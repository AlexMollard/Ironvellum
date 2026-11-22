#![cfg(any(windows, target_os = "linux", target_os = "macos"))]

use ironvellum_local::{
    Directory, Install, LocalError, LocalFiles, LockMode, LocklessOpen, Name, OpenDir,
    PrivateFolder, candidate_names, name, open_endpoints, selected,
};
use std::{fs, path::PathBuf, process::Command};

struct Fixture(PathBuf);

impl Fixture {
    fn new() -> Self {
        let root = std::env::temp_dir();
        #[cfg(unix)]
        let root = root.canonicalize().unwrap();
        let path = root.join(format!(
            "ironvellum-storage-{}-{}",
            std::process::id(),
            std::thread::current().name().unwrap()
        ));
        fs::create_dir(&path).unwrap();
        Self(path)
    }

    fn outbox(&self) -> PrivateFolder {
        PrivateFolder::open(
            &selected(&self.0.join("out")).unwrap(),
            LocklessOpen::CreateLeaf,
        )
        .unwrap()
    }
}

impl Drop for Fixture {
    fn drop(&mut self) {
        fs::remove_dir_all(&self.0).unwrap();
    }
}

fn n(value: &str) -> Name {
    name(value.as_ref()).unwrap()
}

#[test]
fn bounded_io_installs_original_bytes_and_keeps_occupied_entries() {
    let fixture = Fixture::new();
    let folder = fixture.outbox();
    let guard = folder.serialized(LockMode::Create).unwrap();
    let file = n("draft.json");
    let original = b"{  \"original\" : true }\n";
    assert_eq!(guard.read(&file, 0), Err(LocalError::Missing));
    guard.write(&file, b"", Install::New).unwrap();
    assert_eq!(guard.read(&file, 0).unwrap(), b"");
    guard.write(&file, original, Install::Replace).unwrap();
    assert_eq!(
        guard.read(&file, original.len() - 1),
        Err(LocalError::FileLimit)
    );
    assert_eq!(guard.read(&file, usize::MAX).unwrap(), original);
    assert_eq!(
        guard.write(&file, b"other", Install::New),
        Err(LocalError::Exists)
    );
    guard.publish(&file, original).unwrap();
    assert_eq!(
        guard.publish(&file, b"other"),
        Err(LocalError::OutboxConflict)
    );
    assert_eq!(guard.read(&file, original.len()).unwrap(), original);
    let foreign = fixture.0.join("out").join("foreign");
    let alias = fixture.0.join("out").join("alias");
    fs::write(&foreign, b"foreign bytes").unwrap();
    fs::hard_link(&foreign, &alias).unwrap();
    for mode in [Install::New, Install::Replace] {
        assert_eq!(
            guard.write(&n("alias"), b"other", mode),
            Err(LocalError::RegularFileRequired)
        );
    }
    assert_eq!(
        guard.unlink(&n("alias")),
        Err(LocalError::RegularFileRequired)
    );
    assert_eq!(fs::read(alias).unwrap(), b"foreign bytes");
    assert_eq!(fs::read(foreign).unwrap(), b"foreign bytes");
    let occupied = fixture.0.join("out").join("occupied.json");
    fs::create_dir(&occupied).unwrap();
    assert_eq!(
        guard.write(&n("occupied.json"), b"other", Install::Replace),
        Err(LocalError::RegularFileRequired)
    );
    assert!(occupied.is_dir());
    assert!(!fs::read_dir(fixture.0.join("out")).unwrap().any(|entry| {
        entry
            .unwrap()
            .file_name()
            .as_encoded_bytes()
            .ends_with(b".tmp")
    }));
    guard.unlink(&file).unwrap();
    assert!(!folder.dir().exists_no_follow(&file).unwrap());
}

#[test]
fn private_open_refuses_an_existing_broad_directory_without_repair() {
    let fixture = Fixture::new();
    let path = fixture.0.join("out");
    drop(fixture.outbox());
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&path, fs::Permissions::from_mode(0o755)).unwrap();
    }
    #[cfg(windows)]
    {
        let result = Command::new("icacls.exe")
            .arg(&path)
            .args(["/grant", "*S-1-1-0:R"])
            .output()
            .unwrap();
        assert!(result.status.success());
    }
    for mode in [LocklessOpen::Existing, LocklessOpen::CreateLeaf] {
        assert!(matches!(
            PrivateFolder::open(&selected(&path).unwrap(), mode),
            Err(LocalError::PrivateOutboxRequired)
        ));
    }
    assert_eq!(fs::read_dir(&path).unwrap().count(), 0);
}

#[test]
fn existing_lock_is_noncreating_and_refuses_nonempty_lock() {
    let fixture = Fixture::new();
    let folder = fixture.outbox();
    assert!(matches!(
        folder.serialized(LockMode::Existing),
        Err(LocalError::Missing)
    ));
    let lock_name = n(".ironvellum-lock-v1");
    assert!(!folder.dir().exists_no_follow(&lock_name).unwrap());
    drop(folder.serialized(LockMode::Create).unwrap());
    let original = folder.dir().file_stamp(&lock_name).unwrap();
    {
        let guard = folder.serialized(LockMode::Existing).unwrap();
        assert_eq!(
            guard.write(&lock_name, b"replacement", Install::Replace),
            Err(LocalError::InvalidPath)
        );
        assert_eq!(guard.unlink(&lock_name), Err(LocalError::InvalidPath));
        guard
            .write(&n(".ordinary"), b"allowed", Install::New)
            .unwrap();
    }
    assert_eq!(folder.dir().file_stamp(&lock_name).unwrap(), original);
    let path = fixture.0.join("out").join(lock_name.as_os_str());
    fs::write(&path, b"foreign").unwrap();
    for mode in [LockMode::Create, LockMode::Existing] {
        assert!(matches!(
            folder.serialized(mode),
            Err(LocalError::OutboxConflict)
        ));
    }
    assert_eq!(fs::read(&path).unwrap(), b"foreign");
}

#[test]
fn case_variant_is_reserved_only_when_it_resolves_to_the_held_lock() {
    let fixture = Fixture::new();
    let folder = fixture.outbox();
    let literal = n(".ironvellum-lock-v1");
    let variant = n(".IRONVELLUM-LOCK-V1");
    drop(folder.serialized(LockMode::Create).unwrap());
    let original = folder.dir().file_stamp(&literal).unwrap();
    let aliases = folder.dir().exists_no_follow(&variant).unwrap();
    println!("Case variant resolves to the same lock: {aliases}");
    #[cfg(target_os = "linux")]
    assert!(
        !aliases,
        "synthetic Linux fixture must use a case-sensitive directory"
    );
    {
        // Creating mode holds write access and exercises Windows share compatibility.
        let guard = folder.serialized(LockMode::Create).unwrap();
        if aliases {
            for mode in [Install::New, Install::Replace] {
                assert_eq!(
                    guard.write(&variant, b"foreign", mode),
                    Err(LocalError::InvalidPath)
                );
            }
            assert_eq!(guard.publish(&variant, b""), Err(LocalError::InvalidPath));
            assert_eq!(guard.unlink(&variant), Err(LocalError::InvalidPath));
        } else {
            guard.write(&variant, b"first", Install::New).unwrap();
            guard
                .write(&variant, b"distinct", Install::Replace)
                .unwrap();
            guard.publish(&variant, b"distinct").unwrap();
            assert_eq!(guard.read(&variant, 8).unwrap(), b"distinct");
            guard.unlink(&variant).unwrap();
            assert!(!folder.dir().exists_no_follow(&variant).unwrap());
            guard.publish(&variant, b"published").unwrap();
            assert_eq!(guard.read(&variant, 9).unwrap(), b"published");
        }
    }
    assert_eq!(folder.dir().file_stamp(&literal).unwrap(), original);
}

#[test]
fn local_and_endpoint_opening_preserves_creation_boundaries() {
    let fixture = Fixture::new();
    let input = fixture.0.join("input");
    fs::create_dir(&input).unwrap();
    let context = input.join("context.json");
    fs::write(&context, b"original bytes").unwrap();
    let source = selected(&context).unwrap();
    let input = selected(&input).unwrap();
    let missing_parent = selected(&fixture.0.join("missing/out")).unwrap();
    assert!(matches!(
        PrivateFolder::open(&missing_parent, LocklessOpen::CreateLeaf),
        Err(LocalError::Missing)
    ));
    assert!(!fixture.0.join("missing").exists());
    let outbox = selected(&fixture.0.join("out")).unwrap();
    let state = selected(&fixture.0.join("state")).unwrap();
    assert!(matches!(
        LocalFiles::open(&source, &input),
        Err(LocalError::OverlappingPaths)
    ));
    assert!(matches!(
        LocalFiles::open_with_mode(&source, &outbox, LocklessOpen::Existing),
        Err(LocalError::Missing)
    ));
    let absent_context = selected(&fixture.0.join("input/absent.json")).unwrap();
    assert!(matches!(
        LocalFiles::open(&absent_context, &outbox),
        Err(LocalError::Missing)
    ));
    assert!(!outbox.as_path().exists());
    let local = LocalFiles::open(&source, &outbox).unwrap();
    assert!(outbox.as_path().is_dir());
    let endpoints = open_endpoints([&input, &outbox, &state], LocklessOpen::CreateLeaf).unwrap();
    assert_eq!(local.context(), b"original bytes");
    assert_eq!(local.outbox().dir().identity(), endpoints[1].identity());
    assert!(matches!(
        open_endpoints([&input, &outbox, &outbox], LocklessOpen::Existing),
        Err(LocalError::OverlappingPaths)
    ));
    fs::write(context, vec![0; (8 << 20) + 1]).unwrap();
    assert!(matches!(
        LocalFiles::open(&source, &outbox),
        Err(LocalError::FileLimit)
    ));
}

#[test]
fn stable_bytes_and_scan_budgets() {
    let fixture = Fixture::new();
    let dir = Directory::open(&selected(&fixture.0).unwrap(), OpenDir::InputExisting).unwrap();
    fs::write(fixture.0.join("b.JSON"), b"raw").unwrap();
    fs::write(fixture.0.join("a.json"), b"raw").unwrap();
    fs::write(fixture.0.join("ignored"), b"raw").unwrap();
    fs::create_dir(fixture.0.join("directory.json")).unwrap();
    let file = n("b.JSON");
    let stamp = dir.file_stamp(&file).unwrap();
    assert_eq!(dir.stable_bytes(&file, &stamp).unwrap(), b"raw");
    fs::write(fixture.0.join("b.JSON"), b"changed").unwrap();
    assert_eq!(
        dir.stable_bytes(&file, &stamp),
        Err(LocalError::SourceChanged)
    );
    let stamp = dir.file_stamp(&file).unwrap();
    fs::rename(fixture.0.join("b.JSON"), fixture.0.join("old")).unwrap();
    fs::write(fixture.0.join("b.JSON"), b"changed").unwrap();
    assert_eq!(
        dir.stable_bytes(&file, &stamp),
        Err(LocalError::SourceChanged)
    );
    #[cfg(unix)]
    std::os::unix::fs::symlink("absent", fixture.0.join("broken.JsOn")).unwrap();
    #[cfg(windows)]
    {
        let result = Command::new("cmd.exe")
            .current_dir(&fixture.0)
            .args(["/d", "/c", "mklink", "/J", "linked.JsOn", "directory.json"])
            .output()
            .unwrap();
        assert!(result.status.success());
    }
    let (names, deferred) = candidate_names(&dir, 2, 6).unwrap();
    assert_eq!(names, ["a.json", "b.JSON"]);
    assert_eq!(deferred, Some(LocalError::RegularFileRequired));
    assert_eq!(candidate_names(&dir, 1, 6), Err(LocalError::FileCountLimit));
    assert_eq!(candidate_names(&dir, 2, 4), Err(LocalError::FileCountLimit));
    let oversized = n("oversized");
    fs::File::create(fixture.0.join(oversized.as_os_str()))
        .unwrap()
        .set_len((8 << 20) + 1)
        .unwrap();
    let stamp = dir.file_stamp(&oversized).unwrap();
    assert_eq!(
        dir.stable_bytes(&oversized, &stamp),
        Err(LocalError::FileLimit)
    );
}

#[test]
fn two_processes_install_one_exact_publication() {
    let fixture = Fixture::new();
    let folder = fixture.outbox();
    let spawn = |data: &str| {
        Command::new(std::env::current_exe().unwrap())
            .args(["--ignored", "--exact", "publication_child", "--nocapture"])
            .env_clear()
            .env("IRONVELLUM_STORAGE_TEST_OUTBOX", fixture.0.join("out"))
            .env("IRONVELLUM_STORAGE_TEST_BYTES", data)
            .stdout(std::process::Stdio::piped())
            .spawn()
            .unwrap()
    };
    let first = spawn("First Exact Bytes");
    let second = spawn("Second Exact Bytes");
    let results = [first, second].map(|child| {
        let output = child.wait_with_output().unwrap();
        assert!(output.status.success(), "{output:?}");
        String::from_utf8(output.stdout).unwrap()
    });
    assert_eq!(
        results.iter().filter(|s| s.contains("installed")).count(),
        1
    );
    assert_eq!(results.iter().filter(|s| s.contains("conflict")).count(), 1);
    let bytes = folder.dir().read(&n("published.json"), 50).unwrap();
    let expected = if results[0].contains("installed") {
        b"First Exact Bytes".as_slice()
    } else {
        b"Second Exact Bytes"
    };
    assert_eq!(bytes, expected);
}

#[test]
fn broad_harness_does_not_use_ambient_child_fixture_parameters() {
    let fixture = Fixture::new();
    let folder = fixture.outbox();
    let output = Command::new(std::env::current_exe().unwrap())
        .args(["--include-ignored", "--exact", "publication_child"])
        .env("IRONVELLUM_STORAGE_TEST_OUTBOX", fixture.0.join("out"))
        .env("IRONVELLUM_STORAGE_TEST_BYTES", "ambient")
        .output()
        .unwrap();
    assert!(output.status.success());
    assert_eq!(folder.dir().entries().unwrap().count(), 0);
}

#[test]
#[ignore = "parent supplies a synthetic outbox and bytes"]
fn publication_child() {
    if !std::env::args_os()
        .skip(1)
        .eq(["--ignored", "--exact", "publication_child", "--nocapture"])
    {
        return;
    }
    let Some(out) = std::env::var_os("IRONVELLUM_STORAGE_TEST_OUTBOX") else {
        return;
    };
    let raw = std::env::var("IRONVELLUM_STORAGE_TEST_BYTES").unwrap();
    let folder = PrivateFolder::open(
        &selected(&PathBuf::from(out)).unwrap(),
        LocklessOpen::Existing,
    )
    .unwrap();
    let guard = folder.serialized(LockMode::Create).unwrap();
    match guard.publish(&n("published.json"), raw.as_bytes()) {
        Ok(()) => println!("installed"),
        Err(LocalError::OutboxConflict) => println!("conflict"),
        other => panic!("{other:?}"),
    }
}

#[test]
fn portable_cursor_resumes_across_threads_after_dropping_the_facade() {
    let fixture = Fixture::new();
    let path = fixture.0.join("original");
    fs::create_dir(&path).unwrap();
    let expected: std::collections::BTreeSet<_> =
        (0..37).map(|index| format!("item-{index:02}")).collect();
    for item in &expected {
        fs::write(path.join(item), []).unwrap();
    }
    let dir = Directory::open(&selected(&path).unwrap(), OpenDir::InputExisting).unwrap();
    let mut cursor = dir.owned_entries().unwrap();
    let entry_name = |entry: Result<ironvellum_local::Entry, LocalError>| {
        entry.unwrap().name.into_string().unwrap()
    };
    let mut seen: Vec<_> = cursor.by_ref().take(16).map(entry_name).collect();
    assert_eq!(seen.len(), 16);
    drop(dir);
    let moved = fixture.0.join("moved");
    #[cfg(windows)]
    assert!(fs::rename(&path, &moved).is_err());
    #[cfg(unix)]
    {
        fs::rename(&path, &moved).unwrap();
        fs::create_dir(&path).unwrap();
        fs::write(path.join("replacement"), []).unwrap();
    }
    let seen = std::thread::spawn(move || {
        seen.extend(cursor.by_ref().take(16).map(entry_name));
        assert_eq!(seen.len(), 32);
        seen.extend(cursor.map(entry_name));
        seen
    })
    .join()
    .unwrap();
    assert_eq!(seen.len(), 37);
    assert_eq!(
        seen.into_iter().collect::<std::collections::BTreeSet<_>>(),
        expected
    );
    #[cfg(windows)]
    fs::rename(&path, &moved).unwrap();
}
