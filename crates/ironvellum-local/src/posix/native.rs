#[cfg(target_os = "macos")]
use super::darwin::local_volume;
#[cfg(target_os = "linux")]
use super::linux::local_volume;
use super::{Result, validate_volume};
use crate::{Entry, EntryKind, Identity, LocalError, Name, OpenDir, OpenFile, Selected, Stamp};
use std::{
    ffi::{CStr, CString, OsStr, OsString},
    fs::{File, Metadata},
    io,
    ops::Deref,
    os::{
        fd::{AsRawFd, FromRawFd, IntoRawFd, RawFd},
        unix::{
            ffi::{OsStrExt, OsStringExt},
            fs::MetadataExt,
        },
    },
    path::Component,
    ptr::NonNull,
    sync::Arc,
};

fn map_error(err: io::Error) -> LocalError {
    match err.raw_os_error() {
        Some(libc::ENOENT) => LocalError::Missing,
        Some(libc::EEXIST) => LocalError::Exists,
        _ => LocalError::StorageUnavailable,
    }
}
fn check(rc: libc::c_int) -> Result<()> {
    if rc < 0 {
        Err(map_error(io::Error::last_os_error()))
    } else {
        Ok(())
    }
}
fn component(n: &OsStr) -> Result<CString> {
    CString::new(n.as_bytes()).map_err(|_| LocalError::InvalidPath)
}
fn open_at(parent: RawFd, n: &CStr, flags: i32) -> Result<File> {
    // Always pass the promoted mode argument. macOS mode_t is narrower than int.
    let fd = unsafe {
        libc::openat(
            parent,
            n.as_ptr(),
            flags | libc::O_NOFOLLOW | libc::O_CLOEXEC,
            0o600 as libc::c_uint,
        )
    };
    if fd < 0 {
        return Err(map_error(io::Error::last_os_error()));
    }
    Ok(unsafe { File::from_raw_fd(fd) })
}
fn stat_at(fd: RawFd, n: &CStr) -> Result<libc::stat> {
    let mut st = std::mem::MaybeUninit::uninit();
    check(unsafe { libc::fstatat(fd, n.as_ptr(), st.as_mut_ptr(), libc::AT_SYMLINK_NOFOLLOW) })?;
    Ok(unsafe { st.assume_init() })
}
fn identity(m: &Metadata) -> Identity {
    Identity {
        volume: m.dev(),
        file: m.ino(),
    }
}
fn private_owner(uid: u32, mode: u32, real_uid: u32) -> Result<()> {
    if uid != real_uid || mode & 0o077 != 0 {
        Err(LocalError::PrivateOutboxRequired)
    } else {
        Ok(())
    }
}
const DIR_FLAGS: i32 = libc::O_RDONLY | libc::O_DIRECTORY;

pub struct Directory {
    // Retain every ancestor even when its name is moved. All I/O uses leaf.
    _pins: Vec<File>,
    ancestors: Vec<Identity>,
    leaf: File,
    identity: Identity,
}

impl Directory {
    pub fn open(path: &Selected, mode: OpenDir) -> Result<Self> {
        validate_volume(path)?;
        let mut leaf = open_at(libc::AT_FDCWD, c"/", DIR_FLAGS)?;
        let mut pins = Vec::new();
        let mut ancestors = Vec::new();
        let mut parts = path
            .as_path()
            .components()
            .filter_map(|c| match c {
                Component::Normal(n) => Some(n),
                _ => None,
            })
            .peekable();
        while let Some(part) = parts.next() {
            local_volume(leaf.as_raw_fd())?;
            let n = component(part)?;
            ancestors.push(identity(&leaf.metadata().map_err(map_error)?));
            if parts.peek().is_none() && mode == OpenDir::PrivateCreateLeaf {
                match check(unsafe { libc::mkdirat(leaf.as_raw_fd(), n.as_ptr(), 0o700) }) {
                    Ok(()) => leaf.sync_all().map_err(map_error)?,
                    Err(LocalError::Exists) => (),
                    Err(e) => return Err(e),
                }
            }
            let next = open_at(leaf.as_raw_fd(), &n, DIR_FLAGS)?;
            pins.push(leaf);
            leaf = next;
        }
        local_volume(leaf.as_raw_fd())?;
        let metadata = leaf.metadata().map_err(map_error)?;
        if mode != OpenDir::InputExisting {
            private_owner(metadata.uid(), metadata.mode(), unsafe { libc::getuid() })?;
            #[cfg(target_os = "macos")]
            super::darwin::private_acl(leaf.as_raw_fd())?;
        }
        Ok(Self {
            _pins: pins,
            ancestors,
            identity: identity(&metadata),
            leaf,
        })
    }
    pub fn identity(&self) -> Identity {
        self.identity
    }
    pub fn ancestors(&self) -> &[Identity] {
        &self.ancestors
    }
    fn fd(&self) -> RawFd {
        self.leaf.as_raw_fd()
    }
    pub fn exists_no_follow(&self, n: &Name) -> Result<bool> {
        match stat_at(self.fd(), &component(n.as_os_str())?) {
            Ok(_) => Ok(true),
            Err(LocalError::Missing) => Ok(false),
            Err(e) => Err(e),
        }
    }
    pub(crate) fn is_regular_no_follow(&self, n: &Name) -> Result<bool> {
        let st = stat_at(self.fd(), &component(n.as_os_str())?)?;
        Ok(st.st_mode & libc::S_IFMT == libc::S_IFREG)
    }
    pub fn open_file(&self, n: &Name, mode: OpenFile) -> Result<File> {
        let c = component(n.as_os_str())?;
        if mode != OpenFile::CreateNew {
            match stat_at(self.fd(), &c) {
                Ok(m) if m.st_mode & libc::S_IFMT == libc::S_IFREG && m.st_nlink == 1 => (),
                Ok(_) => return Err(LocalError::RegularFileRequired),
                Err(LocalError::Missing) if mode == OpenFile::CreateLock => (),
                Err(e) => return Err(e),
            }
        }
        let flags = match mode {
            OpenFile::Read | OpenFile::ExistingLock => libc::O_RDONLY,
            OpenFile::CreateNew => libc::O_WRONLY | libc::O_CREAT | libc::O_EXCL,
            OpenFile::CreateLock => libc::O_RDWR | libc::O_CREAT,
        };
        let file = open_at(self.fd(), &c, flags | libc::O_NONBLOCK)?;
        let m = file.metadata().map_err(map_error)?;
        if !m.is_file() || m.nlink() != 1 {
            return Err(LocalError::RegularFileRequired);
        }
        local_volume(file.as_raw_fd())?;
        Ok(file)
    }
    pub fn publish_new(&self, source: &Name, target: &Name) -> Result<()> {
        let a = component(source.as_os_str())?;
        let b = component(target.as_os_str())?;
        #[cfg(target_os = "linux")]
        let rc = unsafe {
            libc::syscall(
                libc::SYS_renameat2,
                self.fd(),
                a.as_ptr(),
                self.fd(),
                b.as_ptr(),
                libc::RENAME_NOREPLACE,
            )
        } as libc::c_int;
        #[cfg(target_os = "macos")]
        let rc = unsafe {
            libc::renameatx_np(
                self.fd(),
                a.as_ptr(),
                self.fd(),
                b.as_ptr(),
                libc::RENAME_EXCL,
            )
        };
        check(rc)
    }
    pub fn replace(&self, source: &Name, target: &Name) -> Result<()> {
        let a = component(source.as_os_str())?;
        let b = component(target.as_os_str())?;
        check(unsafe { libc::renameat(self.fd(), a.as_ptr(), self.fd(), b.as_ptr()) })
    }
    pub fn unlink(&self, n: &Name) -> Result<()> {
        let c = component(n.as_os_str())?;
        check(unsafe { libc::unlinkat(self.fd(), c.as_ptr(), 0) })
    }
    pub fn sync(&self) -> Result<()> {
        self.leaf.sync_all().map_err(map_error)
    }
    pub fn entries(&self) -> Result<impl Iterator<Item = Result<Entry>> + '_> {
        Entries::open(self)
    }
    pub fn owned_entries(
        self: Arc<Self>,
    ) -> Result<impl Iterator<Item = Result<Entry>> + Send + 'static> {
        Entries::open(self)
    }
}

struct Entries<D> {
    directory: D,
    stream: NonNull<libc::DIR>,
    finished: bool,
}
// Each stream has a separate open description and a single owner. readdir requires
// exclusive access through next(&mut self), and its borrowed name never escapes.
unsafe impl<D: Send> Send for Entries<D> {}
impl<D: Deref<Target = Directory>> Entries<D> {
    fn open(directory: D) -> Result<Self> {
        // A fresh open description gives each iterator an independent directory offset.
        let file = open_at(directory.fd(), c".", DIR_FLAGS)?;
        let stream = NonNull::new(unsafe { libc::fdopendir(file.as_raw_fd()) })
            .ok_or_else(|| map_error(io::Error::last_os_error()))?;
        // fdopendir owns the descriptor on success.
        let _ = file.into_raw_fd();
        Ok(Entries {
            directory,
            stream,
            finished: false,
        })
    }
}
impl<D> Drop for Entries<D> {
    fn drop(&mut self) {
        unsafe {
            libc::closedir(self.stream.as_ptr());
        }
    }
}
impl<D: Deref<Target = Directory>> Iterator for Entries<D> {
    type Item = Result<Entry>;
    fn next(&mut self) -> Option<Self::Item> {
        while !self.finished {
            // readdir returns null on both EOF and failure. Clear thread-local errno first.
            #[cfg(target_os = "linux")]
            unsafe {
                *libc::__errno_location() = 0;
            }
            #[cfg(target_os = "macos")]
            unsafe {
                *libc::__error() = 0;
            }
            let entry = unsafe { libc::readdir(self.stream.as_ptr()) };
            if entry.is_null() {
                let err = io::Error::last_os_error();
                self.finished = true;
                return (err.raw_os_error() != Some(0)).then(|| Err(map_error(err)));
            }
            // readdir's name remains valid until the next call on this owned stream.
            let n = unsafe { CStr::from_ptr((*entry).d_name.as_ptr()) };
            if n == c"." || n == c".." {
                continue;
            }
            let ty = unsafe { (*entry).d_type };
            let kind = match ty {
                libc::DT_REG => EntryKind::Regular,
                libc::DT_DIR => EntryKind::Directory,
                libc::DT_UNKNOWN => match stat_at(self.directory.fd(), n) {
                    Ok(st) => match st.st_mode & libc::S_IFMT {
                        libc::S_IFREG => EntryKind::Regular,
                        libc::S_IFDIR => EntryKind::Directory,
                        _ => EntryKind::Special,
                    },
                    Err(e) => return Some(Err(e)),
                },
                _ => EntryKind::Special,
            };
            return Some(Ok(Entry {
                name: OsString::from_vec(n.to_bytes().to_vec()),
                kind,
            }));
        }
        None
    }
}
pub fn stamp(file: &File) -> Result<Stamp> {
    let m = file.metadata().map_err(map_error)?;
    let nanos = |s: i64, n: i64| i128::from(s) * 1_000_000_000 + i128::from(n);
    Ok(Stamp {
        identity: identity(&m),
        length: m.len(),
        modified_ns: nanos(m.mtime(), m.mtime_nsec()),
        ctime_ns: nanos(m.ctime(), m.ctime_nsec()),
    })
}
pub fn writable_attribute(file: &File) -> Result<bool> {
    Ok(file.metadata().map_err(map_error)?.mode() & 0o222 != 0)
}
pub struct FileLock(File);
impl FileLock {
    pub fn acquire(file: File) -> Result<Self> {
        loop {
            if unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX) } == 0 {
                return Ok(Self(file));
            }
            let err = io::Error::last_os_error();
            if err.kind() != io::ErrorKind::Interrupted {
                return Err(map_error(err));
            }
        }
    }
    pub fn file(&self) -> &File {
        &self.0
    }
}
impl Drop for FileLock {
    fn drop(&mut self) {
        unsafe {
            libc::flock(self.0.as_raw_fd(), libc::LOCK_UN);
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn private_directory_requires_real_owner_even_with_restrictive_mode() {
        assert_eq!(
            private_owner(2, 0o700, 1),
            Err(LocalError::PrivateOutboxRequired)
        );
    }
}
