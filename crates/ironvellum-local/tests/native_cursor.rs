#![cfg(any(windows, target_os = "linux", target_os = "macos"))]
#[cfg(unix)]
use ironvellum_local::posix::Directory;
#[cfg(windows)]
use ironvellum_local::windows::Directory;
use ironvellum_local::{OpenDir, name, selected};
use std::{collections::BTreeSet, ffi::OsString, fs, sync::Arc};

#[test]
fn resumes_with_original_pins_after_caller_drops_directory() {
    let temp = std::env::temp_dir();
    #[cfg(unix)]
    let temp = temp.canonicalize().unwrap();
    let fixture = temp.join(format!("ironvellum-owned-cursor-{}", std::process::id()));
    fs::create_dir(&fixture).unwrap();
    let path = fixture.join("original");
    fs::create_dir(&path).unwrap();
    let expected: BTreeSet<OsString> = (0..39).map(|i| format!("file-{i}").into()).collect();
    for n in &expected {
        fs::write(path.join(n), b"original").unwrap();
    }
    let dir = Arc::new(Directory::open(&selected(&path).unwrap(), OpenDir::InputExisting).unwrap());
    assert!(
        dir.exists_no_follow(&name("file-0".as_ref()).unwrap())
            .unwrap()
    );
    assert!(
        !dir.exists_no_follow(&name("absent".as_ref()).unwrap())
            .unwrap()
    );
    let mut entries = dir.owned_entries().unwrap();
    let mut seen: Vec<_> = entries.by_ref().take(16).map(|e| e.unwrap().name).collect();
    assert_eq!(seen.len(), 16);
    #[cfg(windows)]
    assert!(fs::rename(&fixture, fixture.with_extension("renamed")).is_err());
    let moved = fixture.join("moved");
    #[cfg(windows)]
    assert!(fs::rename(&path, &moved).is_err());
    #[cfg(unix)]
    {
        fs::rename(&path, &moved).unwrap();
        fs::create_dir(&path).unwrap();
        fs::write(path.join("replacement"), b"replacement").unwrap();
    }
    let seen = std::thread::spawn(move || {
        seen.extend(entries.by_ref().take(16).map(|e| e.unwrap().name));
        assert_eq!(seen.len(), 32);
        seen.extend(entries.map(|e| e.unwrap().name));
        seen
    })
    .join()
    .unwrap();
    assert_eq!(seen.len(), 39);
    assert_eq!(seen.into_iter().collect::<BTreeSet<_>>(), expected);
    #[cfg(windows)]
    fs::rename(&path, &moved).unwrap();
    fs::remove_dir_all(fixture).unwrap();
}
