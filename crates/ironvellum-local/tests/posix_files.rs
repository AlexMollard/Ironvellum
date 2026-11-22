#![cfg(any(target_os = "linux", target_os = "macos"))]

#[cfg(target_os = "linux")]
use ironvellum_local::posix::check_mounts;
use ironvellum_local::posix::{Directory, FileLock, stamp, writable_attribute};
use ironvellum_local::{EntryKind, LocalError, Name, OpenDir, OpenFile, name, selected};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::{
    ffi::OsStr,
    fs,
    io::Write,
    os::unix::{
        ffi::OsStrExt,
        fs::{DirBuilderExt, MetadataExt, PermissionsExt, symlink},
        io::AsRawFd,
    },
    path::{Path, PathBuf},
    process::Command,
};
static COUNTER: AtomicUsize = AtomicUsize::new(0);
struct Fixture(PathBuf);
impl Fixture {
    fn new() -> Self {
        // Resolve only the system temp directory (on macOS /var is a symlink).
        let p = std::env::temp_dir().canonicalize().unwrap().join(format!(
            "ironvellum-posix-{}-{}",
            std::process::id(),
            COUNTER.fetch_add(1, Ordering::Relaxed)
        ));
        fs::DirBuilder::new().mode(0o700).create(&p).unwrap();
        Self(p)
    }
}
impl Drop for Fixture {
    fn drop(&mut self) {
        fs::remove_dir_all(&self.0).unwrap();
    }
}
fn n(s: &str) -> Name {
    name(OsStr::new(s)).unwrap()
}
fn open(p: &Path, mode: OpenDir) -> Result<Directory, LocalError> {
    Directory::open(&selected(p).unwrap(), mode)
}

#[test]
fn private_leaf_and_pinned_io() {
    let f = Fixture::new();
    let p = f.0.join("out");
    assert!(matches!(
        open(&p.join("missing"), OpenDir::PrivateCreateLeaf),
        Err(LocalError::Missing)
    ));
    let d = open(&p, OpenDir::PrivateCreateLeaf).unwrap();
    assert_eq!(
        fs::metadata(&p).unwrap().permissions().mode() & 0o777,
        0o700
    );
    assert_eq!(
        d.ancestors().last(),
        Some(&open(&f.0, OpenDir::InputExisting).unwrap().identity())
    );
    fs::rename(&p, f.0.join("old")).unwrap();
    fs::create_dir(&p).unwrap();
    let mut file = d.open_file(&n("a"), OpenFile::CreateNew).unwrap();
    file.write_all(b"first").unwrap();
    assert_eq!(file.metadata().unwrap().permissions().mode() & 0o777, 0o600);
    let before = stamp(&file).unwrap();
    let native = file.metadata().unwrap();
    assert_eq!(
        before.modified_ns,
        i128::from(native.mtime()) * 1_000_000_000 + i128::from(native.mtime_nsec())
    );
    assert_eq!(
        before.ctime_ns,
        i128::from(native.ctime()) * 1_000_000_000 + i128::from(native.ctime_nsec())
    );
    assert_eq!(before.length, 5);
    assert!(writable_attribute(&file).unwrap());
    file.set_permissions(fs::Permissions::from_mode(0o400))
        .unwrap();
    assert!(!writable_attribute(&file).unwrap());
    drop(file);
    assert_eq!(
        d.open_file(&n("a"), OpenFile::CreateNew).unwrap_err(),
        LocalError::Exists
    );
    d.publish_new(&n("a"), &n("b")).unwrap();
    d.open_file(&n("a"), OpenFile::CreateNew).unwrap();
    assert_eq!(
        d.publish_new(&n("a"), &n("b")).unwrap_err(),
        LocalError::Exists
    );
    assert_eq!(fs::read(f.0.join("old/b")).unwrap(), b"first");
    d.replace(&n("b"), &n("a")).unwrap();
    assert_eq!(
        stamp(&d.open_file(&n("a"), OpenFile::Read).unwrap())
            .unwrap()
            .identity,
        before.identity
    );
    d.unlink(&n("a")).unwrap();
    d.sync().unwrap();
    assert_eq!(fs::read_dir(&p).unwrap().count(), 0);
    fs::set_permissions(&p, fs::Permissions::from_mode(0o750)).unwrap();
    assert!(matches!(
        open(&p, OpenDir::PrivateExisting),
        Err(LocalError::PrivateOutboxRequired)
    ));
}

#[test]
fn refuse_links_and_special_files() {
    let f = Fixture::new();
    let p = f.0.join("out");
    let d = open(&p, OpenDir::PrivateCreateLeaf).unwrap();
    symlink(&p, f.0.join("link")).unwrap();
    assert!(open(&f.0.join("link"), OpenDir::InputExisting).is_err());
    assert!(open(&f.0.join("link/child"), OpenDir::PrivateCreateLeaf).is_err());
    fs::write(p.join("file"), b"preserve").unwrap();
    symlink("file", p.join("sym")).unwrap();
    fs::hard_link(p.join("file"), p.join("hard")).unwrap();
    let fifo = std::ffi::CString::new(p.join("fifo").as_os_str().as_bytes()).unwrap();
    assert_eq!(unsafe { libc::mkfifo(fifo.as_ptr(), 0o600) }, 0);
    fs::create_dir(p.join("sub")).unwrap();
    let raw = OsStr::from_bytes(b"non-\xff");
    fs::write(p.join(raw), b"raw").unwrap();
    symlink("missing", p.join("dangling")).unwrap();
    for s in ["fifo", "sub", "sym", "hard", "file", "dangling"] {
        assert!(d.exists_no_follow(&n(s)).unwrap());
        assert_eq!(
            d.open_file(&n(s), OpenFile::Read).unwrap_err(),
            LocalError::RegularFileRequired
        );
    }
    let entries: Vec<_> = d.entries().unwrap().collect::<Result<_, _>>().unwrap();
    for (s, k) in [
        (raw, EntryKind::Regular),
        (OsStr::new("sym"), EntryKind::Special),
        (OsStr::new("sub"), EntryKind::Directory),
    ] {
        assert!(entries.iter().any(|e| e.name == s && e.kind == k));
    }
}

#[test]
fn private_directory_rejects_foreign_owner_when_chown_available() {
    if unsafe { libc::getuid() } != 0 {
        return;
    }
    let f = Fixture::new();
    let child = f.0.join("private");
    open(&child, OpenDir::PrivateCreateLeaf).unwrap();
    let path = std::ffi::CString::new(child.as_os_str().as_bytes()).unwrap();
    if unsafe { libc::chown(path.as_ptr(), 42, !0) } != 0 {
        // A root user in a restricted user namespace can lack CAP_CHOWN.
        assert_eq!(
            std::io::Error::last_os_error().raw_os_error(),
            Some(libc::EPERM)
        );
        return;
    }
    assert!(matches!(
        open(&child, OpenDir::PrivateExisting),
        Err(LocalError::PrivateOutboxRequired)
    ));
    assert_eq!(fs::metadata(&child).unwrap().uid(), 42);
}

#[cfg(target_os = "macos")]
#[test]
fn extended_acl_is_refused_even_with_private_mode_bits() {
    let f = Fixture::new();
    let path = f.0.join("private");
    open(&path, OpenDir::PrivateCreateLeaf).unwrap();
    assert!(
        Command::new("/bin/chmod")
            .args(["+a", "everyone allow list"])
            .arg(&path)
            .status()
            .unwrap()
            .success()
    );
    assert_eq!(fs::metadata(&path).unwrap().mode() & 0o077, 0);
    assert!(matches!(
        open(&path, OpenDir::PrivateExisting),
        Err(LocalError::PrivateOutboxRequired)
    ));
}

#[test]
fn process_lock_and_exclusive_publication() {
    let f = Fixture::new();
    let d = open(&f.0, OpenDir::PrivateExisting).unwrap();
    assert_eq!(
        d.open_file(&n("lock"), OpenFile::ExistingLock).unwrap_err(),
        LocalError::Missing
    );
    let run = |op: &str| {
        let mut cmd = Command::new(std::env::current_exe().unwrap());
        cmd.args(["--ignored", "--exact", "child_probe"])
            .env_clear()
            .env("FIXTURE", &f.0)
            .env("OP", op);
        cmd.spawn().unwrap()
    };
    let lock = FileLock::acquire(d.open_file(&n("lock"), OpenFile::CreateLock).unwrap()).unwrap();
    assert_eq!(stamp(lock.file()).unwrap().length, 0);
    assert!(run("busy").wait().unwrap().success());
    drop(lock);
    assert!(run("free").wait().unwrap().success());
    let existing = d.open_file(&n("lock"), OpenFile::ExistingLock).unwrap();
    let flags = unsafe { libc::fcntl(existing.as_raw_fd(), libc::F_GETFL) };
    assert_ne!(flags, -1);
    assert_eq!(flags & libc::O_ACCMODE, libc::O_RDONLY);
    let _lock = FileLock::acquire(existing).unwrap();
    assert!(run("busy").wait().unwrap().success());
    for s in ["one", "two"] {
        fs::write(f.0.join(s), s).unwrap();
    }
    let mut a = run("one");
    let mut b = run("two");
    assert!(a.wait().unwrap().success() && b.wait().unwrap().success());
    let winner = fs::read_to_string(f.0.join("target")).unwrap();
    assert!(["one", "two"].contains(&winner.as_str()));
    assert!(!f.0.join(&winner).exists());
    let loser = if winner == "one" { "two" } else { "one" };
    assert_eq!(fs::read_to_string(f.0.join(loser)).unwrap(), loser);
}

#[test]
#[ignore = "selected explicitly by isolated parent"]
fn child_probe() {
    assert_eq!(
        std::env::args().skip(1).collect::<Vec<_>>(),
        ["--ignored", "--exact", "child_probe"]
    );
    let op = std::env::var("OP").unwrap();
    let p = PathBuf::from(std::env::var_os("FIXTURE").unwrap());
    match op.as_str() {
        "busy" | "free" => {
            let f = fs::File::open(p.join("lock")).unwrap();
            let rc = unsafe { libc::flock(f.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) };
            if op == "busy" {
                assert_eq!(rc, -1);
                assert_eq!(
                    std::io::Error::last_os_error().raw_os_error(),
                    Some(libc::EWOULDBLOCK)
                );
            } else {
                assert_eq!(rc, 0);
            }
        }
        "one" | "two" => {
            let d = open(&p, OpenDir::PrivateExisting).unwrap();
            assert!(matches!(
                d.publish_new(&n(&op), &n("target")),
                Ok(()) | Err(LocalError::Exists)
            ));
        }
        _ => panic!("unknown operation"),
    }
}

#[cfg(target_os = "linux")]
#[test]
fn known_nonlocal_mount_is_refused_before_open() {
    let f = Fixture::new();
    let local = selected(&f.0).unwrap();
    let remote = selected(Path::new("/proc/ironvellum-missing")).unwrap();
    assert_eq!(
        check_mounts(&remote, &local),
        Err(LocalError::LocalVolumeRequired)
    );
    assert_eq!(
        check_mounts(&local, &remote),
        Err(LocalError::LocalVolumeRequired)
    );
    assert!(matches!(
        Directory::open(&remote, OpenDir::InputExisting),
        Err(LocalError::LocalVolumeRequired)
    ));
    assert_eq!(check_mounts(&local, &local), Ok(()));
}
