#![cfg(windows)]
use ironvellum_local::windows::Directory;
use ironvellum_local::{
    EntryKind,
    windows::{FileLock, stamp, validate_volume, writable_attribute},
};
use ironvellum_local::{LocalError, OpenDir, OpenFile, name, selected};
use std::{fs, io::Write, path::PathBuf};
use std::{os::windows::io::AsRawHandle, path::Path, process::Command};
use windows_sys::Win32::{
    Foundation::{ERROR_LOCK_VIOLATION, GetLastError},
    Storage::FileSystem::{LOCKFILE_EXCLUSIVE_LOCK, LOCKFILE_FAIL_IMMEDIATELY, LockFileEx},
};

struct Temp(PathBuf);
impl Temp {
    fn new() -> Self {
        let path = std::env::temp_dir().join(format!(
            "ironvellum-win-{}-{}",
            std::process::id(),
            std::thread::current().name().unwrap()
        ));
        fs::create_dir(&path).unwrap();
        Self(path)
    }
}

fn fixture_dir(path: &Path, mode: OpenDir) -> Directory {
    Directory::open(&selected(path).unwrap(), mode).unwrap()
}
fn key(s: &str) -> ironvellum_local::Name {
    name(s.as_ref()).unwrap()
}

#[test]
fn guarded_handles_capture_identity_stamp_attributes_and_entries() {
    let tmp = Temp::new();
    let path = tmp.0.join("leaf");
    validate_volume(&selected(&path).unwrap()).unwrap();
    let slashes = path.to_str().unwrap().replace('\\', "/");
    validate_volume(&selected(Path::new(&slashes)).unwrap()).unwrap();
    let absent = selected(&path.join("missing")).unwrap();
    assert!(Directory::open(&absent, OpenDir::PrivateCreateLeaf).is_err());
    assert!(!path.exists());
    let parent = fixture_dir(&tmp.0, OpenDir::InputExisting);
    let dir = fixture_dir(&path, OpenDir::PrivateCreateLeaf);
    assert_eq!(dir.ancestors().last(), Some(&parent.identity()));
    let file = key("training");
    dir.open_file(&file, OpenFile::CreateNew)
        .unwrap()
        .write_all(b"one")
        .unwrap();
    let f = dir.open_file(&file, OpenFile::Read).unwrap();
    let before = stamp(&f).unwrap();
    let ns = |time: std::time::SystemTime| {
        time.duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos() as i128
    };
    assert_eq!(
        before.modified_ns,
        ns(f.metadata().unwrap().modified().unwrap())
    );
    assert_eq!(
        before.ctime_ns,
        ns(f.metadata().unwrap().created().unwrap())
    );
    assert!(writable_attribute(&f).unwrap());
    assert!(fs::rename(path.join("training"), path.join("moved")).is_err());
    drop(f);
    dir.open_file(&key("new"), OpenFile::CreateNew)
        .unwrap()
        .write_all(b"other")
        .unwrap();
    dir.replace(&key("new"), &file).unwrap();
    let target = path.join("training");
    let original = fs::metadata(&target).unwrap().permissions();
    let mut readonly = original.clone();
    readonly.set_readonly(true);
    fs::set_permissions(&target, readonly).unwrap();
    let f = dir.open_file(&file, OpenFile::Read).unwrap();
    let after = stamp(&f).unwrap();
    assert_ne!(before.identity, after.identity);
    assert_eq!(after.length, 5);
    assert!(!writable_attribute(&f).unwrap());
    drop(f);
    fs::set_permissions(&target, original).unwrap();
    fs::create_dir(path.join("nested")).unwrap();
    assert_eq!(
        dir.open_file(&key("nested"), OpenFile::Read).unwrap_err(),
        LocalError::RegularFileRequired
    );
    assert!(
        dir.entries()
            .unwrap()
            .any(|e| e.is_ok_and(|e| e.name == "nested" && e.kind == EntryKind::Directory))
    );
    dir.publish_new(&file, &key("published")).unwrap();
    dir.sync().unwrap();
    dir.unlink(&key("published")).unwrap();
    drop(dir);
    fs::rename(&path, tmp.0.join("released")).unwrap();
}

#[test]
fn junction_leaf_and_ancestor_are_refused() {
    let tmp = Temp::new();
    fs::create_dir_all(tmp.0.join("real/child")).unwrap();
    let status = Command::new("cmd.exe")
        .current_dir(&tmp.0)
        .args(["/d", "/c", "mklink", "/J", "junction", "real"])
        .output()
        .unwrap();
    assert!(status.status.success());
    for path in ["junction", "junction/child"] {
        assert!(matches!(
            Directory::open(
                &selected(&tmp.0.join(path)).unwrap(),
                OpenDir::InputExisting
            ),
            Err(LocalError::RegularDirectoryRequired)
        ));
    }
    let dir = fixture_dir(&tmp.0, OpenDir::InputExisting);
    assert_eq!(
        dir.open_file(&key("junction"), OpenFile::Read).unwrap_err(),
        LocalError::RegularFileRequired
    );
    assert!(
        dir.entries()
            .unwrap()
            .any(|e| e.is_ok_and(|e| e.name == "junction" && e.kind == EntryKind::Special))
    );
    fs::rename(tmp.0.join("real"), tmp.0.join("moved-real")).unwrap();
    assert!(dir.exists_no_follow(&key("junction")).unwrap());
    assert_eq!(
        dir.open_file(&key("junction"), OpenFile::Read).unwrap_err(),
        LocalError::RegularFileRequired
    );
    fs::rename(tmp.0.join("moved-real"), tmp.0.join("real")).unwrap();
    fs::remove_dir(tmp.0.join("junction")).unwrap();
    assert!(tmp.0.join("real/child").exists());
}

fn acl(path: &Path, args: &[&str]) -> Vec<u8> {
    let output = Command::new("icacls.exe")
        .arg(path)
        .args(args)
        .output()
        .unwrap();
    assert!(output.status.success());
    output.stdout
}
#[test]
fn broad_file_and_directory_permissions_are_left_intact() {
    let tmp = Temp::new();
    let path = tmp.0.join("protected");
    let dir = fixture_dir(&path, OpenDir::PrivateCreateLeaf);
    let file = path.join("file");
    dir.open_file(&key("file"), OpenFile::CreateNew)
        .unwrap()
        .write_all(b"original")
        .unwrap();
    acl(&file, &["/grant", "*S-1-1-0:R"]);
    let before = acl(&file, &[]);
    for mode in [OpenFile::Read, OpenFile::CreateLock] {
        assert_eq!(
            dir.open_file(&key("file"), mode).unwrap_err(),
            LocalError::PrivateOutboxRequired
        );
    }
    assert_eq!(acl(&file, &[]), before);
    assert_eq!(fs::read(&file).unwrap(), b"original");
    drop(dir);
    acl(&path, &["/grant", "*S-1-1-0:R"]);
    let before = acl(&path, &[]);
    for mode in [OpenDir::PrivateExisting, OpenDir::PrivateCreateLeaf] {
        assert!(matches!(
            Directory::open(&selected(&path).unwrap(), mode),
            Err(LocalError::PrivateOutboxRequired)
        ));
    }
    assert_eq!(acl(&path, &[]), before);
}

#[test]
fn ordinary_harness_leaves_ambient_probe_files_intact() {
    let tmp = Temp::new();
    let path = tmp.0.join("ambient");
    let dir = fixture_dir(&path, OpenDir::PrivateCreateLeaf);
    for source in ["left", "right"] {
        dir.open_file(&key(source), OpenFile::CreateNew)
            .unwrap()
            .write_all(source.as_bytes())
            .unwrap();
    }
    let output = Command::new(std::env::current_exe().unwrap())
        .args(["--exact", "native_child_probe"])
        .env("IRONVELLUM_WIN_TEST_PROBE", &path)
        .env("IRONVELLUM_WIN_TEST_PUBLISH", "left")
        .output()
        .unwrap();
    assert!(output.status.success());
    for source in ["left", "right"] {
        assert_eq!(fs::read(path.join(source)).unwrap(), source.as_bytes());
    }
    assert!(!path.join("published").exists());
}

#[test]
fn lock_test_isolated_from_ambient_publication_mode() {
    let tmp = Temp::new();
    let path = tmp.0.join("unused");
    let output = Command::new(std::env::current_exe().unwrap())
        .args([
            "--exact",
            "lock_excludes_an_independent_process_and_releases",
        ])
        .env("IRONVELLUM_WIN_TEST_PUBLISH", "left")
        .env("IRONVELLUM_WIN_TEST_PROBE", &path)
        .env("IRONVELLUM_WIN_TEST_HELD", "1")
        .output()
        .unwrap();
    assert!(
        output.status.success(),
        "{}",
        String::from_utf8_lossy(&output.stdout)
    );
    assert!(!path.exists());
}

fn native_probe_command(path: &Path) -> Command {
    let mut cmd = Command::new(std::env::current_exe().unwrap());
    cmd.args(["--ignored", "--exact", "native_child_probe"])
        .env("IRONVELLUM_WIN_TEST_PROBE", path)
        .env_remove("IRONVELLUM_WIN_TEST_PUBLISH")
        .env_remove("IRONVELLUM_WIN_TEST_HELD");
    cmd
}

#[test]
#[ignore = "subprocess fixture explicitly run by the lock and publication tests"]
fn native_child_probe() {
    // A broad harness run with --include-ignored must also ignore ambient fixture variables.
    if !std::env::args_os()
        .skip(1)
        .eq(["--ignored", "--exact", "native_child_probe"])
    {
        return;
    }
    let Some(path) = std::env::var_os("IRONVELLUM_WIN_TEST_PROBE") else {
        return;
    };
    let dir = fixture_dir(Path::new(&path), OpenDir::PrivateExisting);
    if let Ok(source) = std::env::var("IRONVELLUM_WIN_TEST_PUBLISH") {
        assert!(matches!(source.as_str(), "left" | "right"));
        match dir.publish_new(&key(&source), &key("published")) {
            Ok(()) | Err(LocalError::Exists) => return,
            result => panic!("unexpected publication outcome {result:?}"),
        }
    }
    let file = dir.open_file(&key("lock"), OpenFile::ExistingLock).unwrap();
    // Independent Win32 caller, using the same byte range as the existing connector.
    let mut ov = unsafe { std::mem::zeroed() };
    let success = unsafe {
        LockFileEx(
            file.as_raw_handle(),
            LOCKFILE_EXCLUSIVE_LOCK | LOCKFILE_FAIL_IMMEDIATELY,
            0,
            1,
            0,
            &mut ov,
        )
    };
    let error = unsafe { GetLastError() };
    if std::env::var_os("IRONVELLUM_WIN_TEST_HELD").is_some() {
        assert_eq!(success, 0);
        assert_eq!(error, ERROR_LOCK_VIOLATION);
    } else {
        assert_ne!(success, 0);
    }
}

#[test]
fn two_processes_publish_once_without_overwriting() {
    let tmp = Temp::new();
    let path = tmp.0.join("private");
    let dir = fixture_dir(&path, OpenDir::PrivateCreateLeaf);
    let streams = ["left", "right"];
    for source in streams {
        dir.open_file(&key(source), OpenFile::CreateNew)
            .unwrap()
            .write_all(source.as_bytes())
            .unwrap();
    }
    let children = streams.map(|source| {
        native_probe_command(&path)
            .env("IRONVELLUM_WIN_TEST_PUBLISH", source)
            .stdout(std::process::Stdio::null())
            .spawn()
            .unwrap()
    });
    for mut child in children {
        assert!(child.wait().unwrap().success());
    }
    let published = fs::read_to_string(path.join("published")).unwrap();
    assert!(streams.contains(&published.as_str()));
    for source in streams {
        if source == published {
            assert!(!path.join(source).exists());
        } else {
            assert_eq!(fs::read(path.join(source)).unwrap(), source.as_bytes());
        }
    }
}
#[test]
fn lock_excludes_an_independent_process_and_releases() {
    let tmp = Temp::new();
    let path = tmp.0.join("private");
    let dir = fixture_dir(&path, OpenDir::PrivateCreateLeaf);
    let guard =
        FileLock::acquire(dir.open_file(&key("lock"), OpenFile::CreateLock).unwrap()).unwrap();
    let probe = |held| {
        let mut cmd = native_probe_command(&path);
        if held {
            cmd.env("IRONVELLUM_WIN_TEST_HELD", "1");
        }
        let output = cmd.output().unwrap();
        assert!(
            output.status.success(),
            "child result: {}",
            String::from_utf8_lossy(&output.stdout)
        );
    };
    probe(true);
    drop(guard);
    probe(false);
    let guard =
        FileLock::acquire(dir.open_file(&key("lock"), OpenFile::ExistingLock).unwrap()).unwrap();
    assert_eq!(stamp(guard.file()).unwrap().length, 0);
    probe(true);
}
impl Drop for Temp {
    fn drop(&mut self) {
        fs::remove_dir_all(&self.0).unwrap();
    }
}

#[test]
fn private_files_and_pinned_names() {
    let tmp = Temp::new();
    let path = tmp.0.join("private");
    let dir = Directory::open(&selected(&path).unwrap(), OpenDir::PrivateCreateLeaf).unwrap();
    assert!(fs::rename(&tmp.0, tmp.0.with_extension("moved")).is_err());
    assert!(fs::rename(&path, tmp.0.join("moved")).is_err());
    let a = name("a".as_ref()).unwrap();
    let b = name("b".as_ref()).unwrap();
    assert_eq!(
        dir.open_file(&a, OpenFile::ExistingLock).unwrap_err(),
        LocalError::Missing
    );
    dir.open_file(&a, OpenFile::CreateNew)
        .unwrap()
        .write_all(b"first")
        .unwrap();
    assert_eq!(
        dir.open_file(&a, OpenFile::CreateNew).unwrap_err(),
        LocalError::Exists
    );
    dir.open_file(&b, OpenFile::CreateNew)
        .unwrap()
        .write_all(b"second")
        .unwrap();
    assert_eq!(dir.publish_new(&a, &b), Err(LocalError::Exists));
    assert_eq!(fs::read(path.join("b")).unwrap(), b"second");
    dir.replace(&a, &b).unwrap();
    assert_eq!(fs::read(path.join("b")).unwrap(), b"first");
    fs::hard_link(path.join("b"), path.join("alias")).unwrap();
    assert_eq!(
        dir.open_file(&b, OpenFile::Read).unwrap_err(),
        LocalError::RegularFileRequired
    );
}
