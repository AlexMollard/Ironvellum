use super::{LocalError, Path, Result};
use std::{
    ffi::{OsStr, c_void},
    os::{fd::RawFd, unix::ffi::OsStrExt},
    ptr,
};

const REFUSE: LocalError = LocalError::LocalVolumeRequired;

fn field(raw: &[libc::c_char]) -> Result<&[u8]> {
    // statfs fields are fixed arrays. Inspect only bytes inside the array.
    let bytes = unsafe { std::slice::from_raw_parts(raw.as_ptr().cast::<u8>(), raw.len()) };
    let end = bytes.iter().position(|&b| b == 0).ok_or(REFUSE)?;
    Ok(&bytes[..end])
}
pub(super) fn inspect_mounts(mut visit: impl FnMut(&Path, bool) -> Result<()>) -> Result<()> {
    // getmntinfo's buffer is process-global. getfsstat takes caller-owned storage.
    for _ in 0..4 {
        let count = unsafe { libc::getfsstat(ptr::null_mut(), 0, libc::MNT_NOWAIT) };
        if count <= 0 {
            return Err(REFUSE);
        }
        let slots = (count as usize).checked_add(1).ok_or(REFUSE)?;
        let bytes = slots
            .checked_mul(size_of::<libc::statfs>())
            .and_then(|n| i32::try_from(n).ok())
            .ok_or(REFUSE)?;
        let mut mounts = Vec::<libc::statfs>::new();
        mounts.try_reserve_exact(slots).map_err(|_| REFUSE)?;
        let written = unsafe { libc::getfsstat(mounts.as_mut_ptr(), bytes, libc::MNT_NOWAIT) };
        if written <= 0 {
            return Err(REFUSE);
        }
        if written as usize >= slots {
            continue;
        }
        // The kernel filled precisely the returned number of complete structures.
        unsafe {
            mounts.set_len(written as usize);
        }
        for m in &mounts {
            let mount = Path::new(OsStr::from_bytes(field(&m.f_mntonname)?));
            if !mount.is_absolute() {
                return Err(REFUSE);
            }
            visit(
                mount,
                m.f_flags & libc::MNT_LOCAL as u32 != 0 && field(&m.f_fstypename)? != b"autofs",
            )?;
        }
        return Ok(());
    }
    Err(REFUSE)
}
pub(super) fn local_volume(fd: RawFd) -> Result<()> {
    let mut m = std::mem::MaybeUninit::uninit();
    if unsafe { libc::fstatfs(fd, m.as_mut_ptr()) } != 0 {
        return Err(REFUSE);
    }
    if unsafe { m.assume_init() }.f_flags & libc::MNT_LOCAL as u32 == 0 {
        Err(REFUSE)
    } else {
        Ok(())
    }
}
unsafe extern "C" {
    fn acl_get_fd(fd: RawFd) -> *mut c_void;
    fn acl_get_entry(
        acl: *mut c_void,
        entry_id: libc::c_int,
        entry: *mut *mut c_void,
    ) -> libc::c_int;
    fn acl_free(obj: *mut c_void) -> libc::c_int;
}
pub(super) fn private_acl(fd: RawFd) -> Result<()> {
    let denied = LocalError::PrivateOutboxRequired;
    let acl = unsafe { acl_get_fd(fd) };
    if acl.is_null() {
        return match std::io::Error::last_os_error().raw_os_error() {
            Some(libc::ENOENT | libc::ENOTSUP) => Ok(()),
            _ => Err(denied),
        };
    }
    let mut entry = ptr::null_mut();
    let rc = unsafe { acl_get_entry(acl, 0, &mut entry) };
    let empty = rc != 0 && std::io::Error::last_os_error().raw_os_error() == Some(libc::EINVAL);
    unsafe {
        acl_free(acl);
    }
    if empty { Ok(()) } else { Err(denied) }
}
