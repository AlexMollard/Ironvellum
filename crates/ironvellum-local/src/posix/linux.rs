use super::{LocalError, Path, Result};
use std::{ffi::OsStr, fs, os::fd::RawFd, os::unix::ffi::OsStrExt};

fn local_fs(fs: &[u8]) -> bool {
    matches!(
        fs,
        b"ext2"
            | b"ext3"
            | b"ext4"
            | b"xfs"
            | b"btrfs"
            | b"zfs"
            | b"tmpfs"
            | b"overlay"
            | b"f2fs"
            | b"bcachefs"
            | b"ntfs3"
            | b"ufs"
            | b"jfs"
            | b"ramfs"
            | b"erofs"
            | b"squashfs"
    )
}
const REFUSE: LocalError = LocalError::LocalVolumeRequired;
fn unescape(raw: &[u8]) -> Result<Vec<u8>> {
    let mut bytes = Vec::with_capacity(raw.len());
    let mut input = raw.iter().copied();
    while let Some(ch) = input.next() {
        if ch == b'\\' {
            let mut n = 0u16;
            for _ in 0..3 {
                let digit = input
                    .next()
                    .filter(|c| (b'0'..=b'7').contains(c))
                    .ok_or(REFUSE)?;
                n = n * 8 + u16::from(digit - b'0');
            }
            bytes.push(u8::try_from(n).map_err(|_| REFUSE)?);
        } else {
            bytes.push(ch);
        }
    }
    Ok(bytes)
}
struct Mount<'a> {
    id: &'a [u8],
    path: &'a [u8],
    local: bool,
}
fn mount(line: &[u8]) -> Result<Mount<'_>> {
    let sep = line.windows(3).position(|w| w == b" - ").ok_or(REFUSE)?;
    let mut fields = line[..sep].split(|&b| b == b' ');
    let id = fields.next().ok_or(REFUSE)?;
    let path = fields
        .nth(3)
        .filter(|p| p.starts_with(b"/"))
        .ok_or(REFUSE)?;
    let fs = line[sep + 3..]
        .split(|&b| b == b' ')
        .next()
        .filter(|f| !f.is_empty())
        .ok_or(REFUSE)?;
    Ok(Mount {
        id,
        path,
        local: local_fs(fs),
    })
}
fn table() -> Result<Vec<u8>> {
    fs::read("/proc/self/mountinfo").map_err(|_| REFUSE)
}
pub(super) fn inspect_mounts(mut visit: impl FnMut(&Path, bool) -> Result<()>) -> Result<()> {
    let mounts = table()?;
    if mounts.is_empty() {
        return Err(REFUSE);
    }
    for line in mounts.split(|&b| b == b'\n').filter(|l| !l.is_empty()) {
        let m = mount(line)?;
        visit(Path::new(OsStr::from_bytes(&unescape(m.path)?)), m.local)?;
    }
    Ok(())
}
pub(super) fn local_volume(fd: RawFd) -> Result<()> {
    let info = fs::read_to_string(format!("/proc/self/fdinfo/{fd}")).map_err(|_| REFUSE)?;
    let id = info
        .lines()
        .find_map(|l| l.strip_prefix("mnt_id:"))
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .ok_or(REFUSE)?;
    for line in table()?.split(|&b| b == b'\n').filter(|l| !l.is_empty()) {
        let m = mount(line)?;
        if id.as_bytes() == m.id {
            return if m.local { Ok(()) } else { Err(REFUSE) };
        }
    }
    Err(REFUSE)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn escaped_mounts_and_closed_allowlist() {
        let m = mount(br"123 2 8:2 / /remote\040x\134y\011 rw - nfs server:/ rw").unwrap();
        assert_eq!(m.id, b"123");
        assert!(!m.local);
        assert_eq!(unescape(m.path).unwrap(), b"/remote x\\y\t");
        assert!(unescape(br"/mount\777").is_err());
        assert!(local_fs(b"ext4"));
        assert!(!local_fs(b"fuse.sshfs"));
    }
}
