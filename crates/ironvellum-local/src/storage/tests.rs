use super::*;
use crate::selected;
use std::{fs, path::PathBuf};

struct Fixture(PathBuf);

impl Fixture {
    fn new() -> Self {
        let root = std::env::temp_dir();
        #[cfg(unix)]
        let root = root.canonicalize().unwrap();
        let dir = root.join(temp_name().unwrap().as_os_str());
        fs::create_dir(&dir).unwrap();
        Self(dir)
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

#[test]
fn publish_rereads_after_exclusive_rename_loses_to_another_writer() {
    let fixture = Fixture::new();
    let folder = fixture.outbox();
    let guard = folder.serialized(LockMode::Create).unwrap();
    for (basename, winning_bytes, expected) in [
        ("equal.json", b"exact".as_slice(), Ok(())),
        (
            "different.json",
            b"foreign",
            Err(LocalError::OutboxConflict),
        ),
    ] {
        let target = name(basename.as_ref()).unwrap();
        let publish = guard.publish_with(&target, b"exact", || {
            // Install a real competing file after the absent read and before this rename.
            guard.write(&target, winning_bytes, Install::New).unwrap();
            guard.write(&target, b"exact", Install::New)
        });
        assert_eq!(publish, expected);
        assert_eq!(guard.read(&target, 16).unwrap(), winning_bytes);
    }
    assert_eq!(folder.dir().entries().unwrap().count(), 3);
}

#[test]
fn sync_failure_leaves_installed_bytes_and_unrelated_files_visible() {
    let fixture = Fixture::new();
    let folder = fixture.outbox();
    let guard = folder.serialized(LockMode::Create).unwrap();
    let destination = name("draft.json".as_ref()).unwrap();
    let other = name(".ironvellum-foreign.tmp".as_ref()).unwrap();
    let dir = &folder.dir().0;

    let failure = guard.write_then_sync(&destination, b"new", Install::New, |_| {
        assert_eq!(guard.read(&destination, 3).unwrap(), b"new");
        dir.open_file(&other, OpenFile::CreateNew)
            .unwrap()
            .write_all(b"unrelated")
            .unwrap();
        Err(LocalError::StorageUnavailable)
    });
    assert_eq!(failure, Err(LocalError::StorageUnavailable));
    assert_eq!(guard.read(&destination, 3).unwrap(), b"new");
    assert_eq!(guard.read(&other, 9).unwrap(), b"unrelated");
    guard.publish(&destination, b"new").unwrap();
    assert_eq!(folder.dir().entries().unwrap().count(), 3);
}

#[cfg(unix)]
#[test]
fn pinned_directory_overlap_uses_identity_after_its_path_changes() {
    let fixture = Fixture::new();
    let original = fixture.0.join("old");
    let moved = fixture.0.join("new");
    fs::create_dir(&original).unwrap();
    fs::create_dir(original.join("child")).unwrap();
    let source = selected(&original.join("child")).unwrap();
    let child = Directory::open(&source, OpenDir::InputExisting).unwrap();
    fs::rename(&original, &moved).unwrap();
    let alias = selected(&moved).unwrap();
    assert!(!source.is_within(&alias));
    let parent = Directory::open(&alias, OpenDir::InputExisting).unwrap();
    assert!(below(&child, &parent));
}
