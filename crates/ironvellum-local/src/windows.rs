//! Win32 primitives for the local connector. Directory handles deny delete sharing
//! throughout the walk and stay live while using the selected directory.
use crate::{Entry, EntryKind, Identity, LocalError, Name, OpenDir, OpenFile, Selected, Stamp};
use std::{
    ffi::{OsStr, c_void},
    fs::{self, File},
    io, iter,
    mem::{size_of, zeroed},
    os::windows::{
        ffi::OsStrExt,
        fs::MetadataExt,
        io::{AsRawHandle, FromRawHandle, OwnedHandle},
    },
    path::{Path, PathBuf},
    ptr::{null, null_mut},
};
use windows_sys::Win32::{
    Foundation::*,
    Security::{Authorization::*, *},
    Storage::FileSystem::*,
    System::{
        IO::OVERLAPPED,
        Threading::{GetCurrentProcess, OpenProcessToken},
    },
};
type Result<T> = std::result::Result<T, LocalError>;
fn map_io(e: io::Error) -> LocalError {
    match e.raw_os_error().map(|n| n as u32) {
        Some(ERROR_FILE_NOT_FOUND | ERROR_PATH_NOT_FOUND) => LocalError::Missing,
        Some(ERROR_FILE_EXISTS | ERROR_ALREADY_EXISTS) => LocalError::Exists,
        _ => LocalError::StorageUnavailable,
    }
}
fn last() -> LocalError {
    map_io(io::Error::last_os_error())
}
fn wide(s: &OsStr) -> Vec<u16> {
    s.encode_wide().chain(iter::once(0)).collect()
}
// All users receive a fresh LocalAlloc allocation from a security API.
struct Local(*mut c_void);
impl Drop for Local {
    fn drop(&mut self) {
        unsafe {
            LocalFree(self.0);
        }
    }
}

fn user_sid() -> Result<String> {
    // The token handle owns the queried token; the aligned buffer owns the embedded SID.
    unsafe {
        let mut token = null_mut();
        if OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &mut token) == 0 {
            return Err(last());
        }
        let token = OwnedHandle::from_raw_handle(token);
        let mut bytes = 0;
        GetTokenInformation(token.as_raw_handle(), TokenUser, null_mut(), 0, &mut bytes);
        if GetLastError() != ERROR_INSUFFICIENT_BUFFER || (bytes as usize) < size_of::<TOKEN_USER>()
        {
            return Err(LocalError::StorageUnavailable);
        }
        let mut buf = vec![0usize; (bytes as usize).div_ceil(size_of::<usize>())];
        if GetTokenInformation(
            token.as_raw_handle(),
            TokenUser,
            buf.as_mut_ptr().cast(),
            bytes,
            &mut bytes,
        ) == 0
        {
            return Err(last());
        }
        sid_string((*buf.as_ptr().cast::<TOKEN_USER>()).User.Sid)
    }
}
// sid points into a live OS-validated token or security descriptor.
unsafe fn sid_string(sid: PSID) -> Result<String> {
    let mut p = null_mut();
    unsafe {
        if sid.is_null() || ConvertSidToStringSidW(sid, &mut p) == 0 {
            return Err(LocalError::PrivateOutboxRequired);
        }
        let _allocation = Local(p.cast());
        let mut len = 0;
        while *p.add(len) != 0 {
            len += 1;
        }
        String::from_utf16(std::slice::from_raw_parts(p, len))
            .map_err(|_| LocalError::StorageUnavailable)
    }
}
fn descriptor() -> Result<Local> {
    let user = user_sid()?;
    let sddl = wide(format!("D:P(A;OICI;FA;;;{user})(A;OICI;FA;;;SY)(A;OICI;FA;;;BA)").as_ref());
    let mut p = null_mut();
    // The converter allocates a self-relative descriptor freed with LocalFree.
    if unsafe {
        ConvertStringSecurityDescriptorToSecurityDescriptorW(
            sddl.as_ptr(),
            SDDL_REVISION_1,
            &mut p,
            null_mut(),
        )
    } == 0
    {
        return Err(last());
    }
    Ok(Local(p))
}
fn attrs(sd: &Local) -> SECURITY_ATTRIBUTES {
    SECURITY_ATTRIBUTES {
        nLength: size_of::<SECURITY_ATTRIBUTES>() as u32,
        lpSecurityDescriptor: sd.0,
        bInheritHandle: 0,
    }
}
fn private(handle: &File) -> Result<()> {
    let user = user_sid()?;
    let trusted = |s: &str| s == user || matches!(s, "S-1-5-18" | "S-1-5-32-544");
    // Owner, ACL, and ACE pointers remain within the OS allocated descriptor.
    unsafe {
        let (mut owner, mut acl, mut sd) = (null_mut(), null_mut(), null_mut());
        if GetSecurityInfo(
            handle.as_raw_handle(),
            SE_FILE_OBJECT,
            OWNER_SECURITY_INFORMATION | DACL_SECURITY_INFORMATION,
            &mut owner,
            null_mut(),
            &mut acl,
            null_mut(),
            &mut sd,
        ) != ERROR_SUCCESS
        {
            return Err(LocalError::StorageUnavailable);
        }
        let _descriptor = Local(sd);
        if acl.is_null() || !trusted(&sid_string(owner)?) {
            return Err(LocalError::PrivateOutboxRequired);
        }
        for index in 0..(*acl).AceCount {
            let mut ace = null_mut();
            if GetAce(acl, index.into(), &mut ace) == 0 {
                return Err(last());
            }
            const ALLOW: u8 = 0;
            const DENY: u8 = 1;
            match (*ace.cast::<ACE_HEADER>()).AceType {
                DENY => continue,
                ALLOW => {
                    let sid = (&raw mut (*ace.cast::<ACCESS_ALLOWED_ACE>()).SidStart).cast();
                    if !trusted(&sid_string(sid)?) {
                        return Err(LocalError::PrivateOutboxRequired);
                    }
                }
                _ => return Err(LocalError::PrivateOutboxRequired),
            }
        }
    }
    Ok(())
}
fn open(
    path: &Path,
    access: u32,
    sharing: u32,
    disposition: u32,
    sd: Option<&Local>,
) -> Result<File> {
    let path = wide(path.as_os_str());
    let security = sd.map(attrs);
    // Transfer the newly opened handle to File exactly once.
    unsafe {
        let h = CreateFileW(
            path.as_ptr(),
            access,
            sharing,
            security.as_ref().map_or(null(), std::ptr::from_ref),
            disposition,
            FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OPEN_REPARSE_POINT,
            null_mut(),
        );
        if h == INVALID_HANDLE_VALUE {
            Err(last())
        } else {
            Ok(File::from_raw_handle(h))
        }
    }
}
fn info(f: &File) -> Result<BY_HANDLE_FILE_INFORMATION> {
    unsafe {
        let mut i = zeroed();
        if GetFileInformationByHandle(f.as_raw_handle(), &mut i) == 0 {
            Err(last())
        } else {
            Ok(i)
        }
    }
}
fn both(high: u32, low: u32) -> u64 {
    ((high as u64) << 32) | low as u64
}
fn identity(i: &BY_HANDLE_FILE_INFORMATION) -> Identity {
    Identity {
        volume: i.dwVolumeSerialNumber.into(),
        file: both(i.nFileIndexHigh, i.nFileIndexLow),
    }
}
pub struct Directory {
    path: PathBuf,
    _pins: Vec<File>,
    ancestors: Vec<Identity>,
    identity: Identity,
    private: bool,
}
pub fn validate_volume(path: &Selected) -> Result<()> {
    let root = path
        .as_path()
        .ancestors()
        .last()
        .ok_or(LocalError::InvalidPath)?;
    let root = wide(root.as_os_str());
    if matches!(unsafe { GetDriveTypeW(root.as_ptr()) }, 2 | 3 | 6) {
        Ok(())
    } else {
        Err(LocalError::LocalVolumeRequired)
    }
}
impl Directory {
    pub fn open(path: &Selected, mode: OpenDir) -> Result<Self> {
        validate_volume(path)?;
        let path: PathBuf = path.as_path().components().collect();
        let mut chain: Vec<_> = path.ancestors().collect();
        chain.reverse();
        let mut pins = Vec::new();
        let mut ids = Vec::new();
        let restricted = mode != OpenDir::InputExisting;
        for part in chain {
            let leaf = part == path;
            if leaf && mode == OpenDir::PrivateCreateLeaf {
                let sd = descriptor()?;
                let path = wide(part.as_os_str());
                if unsafe { CreateDirectoryW(path.as_ptr(), &attrs(&sd)) } == 0
                    && unsafe { GetLastError() } != ERROR_ALREADY_EXISTS
                {
                    return Err(LocalError::StorageUnavailable);
                }
            }
            let access = FILE_TRAVERSE
                | FILE_READ_ATTRIBUTES
                | if restricted && leaf { READ_CONTROL } else { 0 };
            let h = open(
                part,
                access,
                FILE_SHARE_READ | FILE_SHARE_WRITE,
                OPEN_EXISTING,
                None,
            )?;
            let i = info(&h)?;
            if i.dwFileAttributes & (FILE_ATTRIBUTE_DIRECTORY | FILE_ATTRIBUTE_REPARSE_POINT)
                != FILE_ATTRIBUTE_DIRECTORY
            {
                return Err(LocalError::RegularDirectoryRequired);
            }
            if restricted && leaf {
                private(&h)?;
            }
            ids.push(identity(&i));
            pins.push(h);
        }
        Ok(Self {
            path,
            _pins: pins,
            identity: ids.pop().ok_or(LocalError::InvalidPath)?,
            ancestors: ids,
            private: restricted,
        })
    }
    pub fn identity(&self) -> Identity {
        self.identity
    }
    pub fn ancestors(&self) -> &[Identity] {
        &self.ancestors
    }
    pub fn open_file(&self, n: &Name, mode: OpenFile) -> Result<File> {
        let (access, share, disposition) = match mode {
            OpenFile::Read => (GENERIC_READ, 0, OPEN_EXISTING),
            OpenFile::ExistingLock => (GENERIC_READ, FILE_SHARE_WRITE, OPEN_EXISTING),
            OpenFile::CreateLock => (GENERIC_READ | GENERIC_WRITE, FILE_SHARE_WRITE, OPEN_ALWAYS),
            OpenFile::CreateNew => (GENERIC_WRITE, 0, CREATE_NEW),
        };
        let sd = if self.private && matches!(mode, OpenFile::CreateLock | OpenFile::CreateNew) {
            Some(descriptor()?)
        } else {
            None
        };
        let f = open(
            &self.path.join(n.as_os_str()),
            access | if self.private { READ_CONTROL } else { 0 },
            FILE_SHARE_READ | share,
            disposition,
            sd.as_ref(),
        )?;
        let i = info(&f)?;
        if self.private {
            private(&f)?;
        }
        if unsafe { GetFileType(f.as_raw_handle()) } != FILE_TYPE_DISK
            || i.dwFileAttributes & (FILE_ATTRIBUTE_DIRECTORY | FILE_ATTRIBUTE_REPARSE_POINT) != 0
            || i.nNumberOfLinks != 1
        {
            return Err(LocalError::RegularFileRequired);
        }
        Ok(f)
    }
    fn move_file(&self, a: &Name, b: &Name, replace: u32) -> Result<()> {
        let a = wide(self.path.join(a.as_os_str()).as_os_str());
        let b = wide(self.path.join(b.as_os_str()).as_os_str());
        if unsafe { MoveFileExW(a.as_ptr(), b.as_ptr(), MOVEFILE_WRITE_THROUGH | replace) } != 0 {
            return Ok(());
        }
        match last() {
            LocalError::Exists if replace == 0 => Err(LocalError::Exists),
            _ => Err(LocalError::StorageUnavailable),
        }
    }
    pub fn publish_new(&self, a: &Name, b: &Name) -> Result<()> {
        self.move_file(a, b, 0)
    }
    pub fn replace(&self, a: &Name, b: &Name) -> Result<()> {
        self.move_file(a, b, MOVEFILE_REPLACE_EXISTING)
    }
    pub fn unlink(&self, n: &Name) -> Result<()> {
        fs::remove_file(self.path.join(n.as_os_str())).map_err(map_io)
    }
    /// Both move modes request write-through from the filesystem.
    pub fn sync(&self) -> Result<()> {
        Ok(())
    }
    pub fn exists_no_follow(&self, n: &Name) -> Result<bool> {
        match fs::symlink_metadata(self.path.join(n.as_os_str())).map_err(map_io) {
            Ok(_) => Ok(true),
            Err(LocalError::Missing) => Ok(false),
            Err(e) => Err(e),
        }
    }
    pub fn owned_entries(
        self: std::sync::Arc<Self>,
    ) -> Result<impl Iterator<Item = Result<Entry>> + Send + 'static> {
        let entries = fs::read_dir(&self.path).map_err(map_io)?;
        Ok(entries.map(move |entry| {
            let _pins = &self;
            map_entry(entry)
        }))
    }
    pub fn entries(&self) -> Result<impl Iterator<Item = Result<Entry>> + '_> {
        let entries = fs::read_dir(&self.path).map_err(map_io)?;
        Ok(entries.map(map_entry))
    }
}
fn map_entry(e: io::Result<fs::DirEntry>) -> Result<Entry> {
    let e = e.map_err(map_io)?;
    let a = e.metadata().map_err(map_io)?.file_attributes();
    let kind = if a & (FILE_ATTRIBUTE_REPARSE_POINT | FILE_ATTRIBUTE_DEVICE) != 0 {
        EntryKind::Special
    } else if a & FILE_ATTRIBUTE_DIRECTORY != 0 {
        EntryKind::Directory
    } else {
        EntryKind::Regular
    };
    Ok(Entry {
        name: e.file_name(),
        kind,
    })
}
pub fn stamp(file: &File) -> Result<Stamp> {
    let i = info(file)?;
    let nanos = |t: FILETIME| {
        (i128::from(both(t.dwHighDateTime, t.dwLowDateTime)) - 116444736000000000) * 100
    };
    Ok(Stamp {
        identity: identity(&i),
        length: both(i.nFileSizeHigh, i.nFileSizeLow),
        modified_ns: nanos(i.ftLastWriteTime),
        ctime_ns: nanos(i.ftCreationTime),
    })
}
pub fn writable_attribute(file: &File) -> Result<bool> {
    Ok(info(file)?.dwFileAttributes & FILE_ATTRIBUTE_READONLY == 0)
}
/// Owns the handle used to acquire the synchronous exclusive byte-range lock.
pub struct FileLock(File);
impl FileLock {
    pub fn acquire(file: File) -> Result<Self> {
        // Zeroed OVERLAPPED selects offset zero. The handle is synchronous.
        let mut overlapped: OVERLAPPED = unsafe { zeroed() };
        if unsafe {
            LockFileEx(
                file.as_raw_handle(),
                LOCKFILE_EXCLUSIVE_LOCK,
                0,
                1,
                0,
                &mut overlapped,
            )
        } == 0
        {
            return Err(last());
        }
        Ok(Self(file))
    }
    pub fn file(&self) -> &File {
        &self.0
    }
}
impl Drop for FileLock {
    fn drop(&mut self) {
        // Release the same first byte before File closes the owned handle.
        let mut overlapped: OVERLAPPED = unsafe { zeroed() };
        unsafe {
            UnlockFileEx(self.0.as_raw_handle(), 0, 1, 0, &mut overlapped);
        }
    }
}
