use ironvellum_contracts::Refusal;
use ironvellum_local::{LocalError, name, selected};
use std::ffi::OsStr;
use std::path::Path;

#[test]
fn names_require_one_original_component() {
    for s in ["", ".", "..", "x/", "./x", "x/.", "x/../y", "x\0y"] {
        assert_eq!(
            name(OsStr::new(s)).unwrap_err(),
            LocalError::InvalidPath,
            "{s:?}"
        );
    }
    for s in ["Résumé ⛷.json", ".draft", " two spaces"] {
        assert_eq!(name(OsStr::new(s)).unwrap().as_os_str(), OsStr::new(s));
    }
}

#[test]
fn selected_requires_nonroot_absolute_without_parent_components() {
    for s in ["", ".", "..", "my-file", "/"] {
        assert_eq!(
            selected(Path::new(s)).unwrap_err(),
            LocalError::AbsolutePathRequired
        );
    }
    let root = if cfg!(windows) { "C:/" } else { "/" };
    for suffix in ["", ".", "a/../b", "a/..", "./"] {
        assert_eq!(
            selected(Path::new(&format!("{root}{suffix}"))).unwrap_err(),
            LocalError::AbsolutePathRequired
        );
    }
    assert_eq!(
        selected(Path::new("//server/share/leaf")).unwrap_err(),
        LocalError::LocalPathRequired
    );
    let p = format!("{root}a/./b//résumé/");
    assert_eq!(
        selected(Path::new(&p)).unwrap().as_path().as_os_str(),
        OsStr::new(&p)
    );
}

#[test]
fn missing_path_is_purely_lexical() {
    let path = std::env::temp_dir()
        .join(format!("ironvellum-lexical-test-{}", std::process::id()))
        .join("./missing/child");
    assert!(!path.exists());
    assert_eq!(
        selected(&path).unwrap().as_path().as_os_str(),
        path.as_os_str()
    );
    assert!(!path.exists());
}

#[cfg(windows)]
#[test]
fn windows_component_rules_and_error_order() {
    for s in [
        "a:b",
        "CON",
        "prn.txt",
        "lpt².x",
        "coM¹",
        "CONIN$",
        "conout$.log",
        "Aux .txt",
        "abc.",
        "abc ",
        "x?y",
        "x*y",
        "a<b",
        "a>b",
        "a|b",
        "a\"b",
        "x\x1fy",
        "x\0y",
    ] {
        assert_eq!(name(OsStr::new(s)).unwrap_err(), LocalError::InvalidPath);
        assert_eq!(
            selected(Path::new(&format!("C:/parent/{s}"))).unwrap_err(),
            LocalError::InvalidPath
        );
    }
    for s in [r"a\b", r".\a", r"\a", "C:foo", "D:"] {
        assert_eq!(name(OsStr::new(s)).unwrap_err(), LocalError::InvalidPath);
    }
    for s in [
        r"\\host\share\file",
        r"\\?\C:\test",
        r"\\.\pipe\test",
        "1:/leaf",
        "::/leaf",
        "é:/leaf",
    ] {
        assert_eq!(
            selected(Path::new(s)).unwrap_err(),
            LocalError::LocalPathRequired,
            "{s}"
        );
    }
    for s in [
        r"\\host\share",
        r"\\host\share\a\..\b",
        r"C:partial",
        r"\rooted",
    ] {
        assert_eq!(
            selected(Path::new(s)).unwrap_err(),
            LocalError::AbsolutePathRequired
        );
    }
    for s in ["com0", "lpt10.doc", "COM1other", "auxiliary", "COM⁴"] {
        assert!(name(OsStr::new(s)).is_ok());
    }
    use std::os::windows::ffi::OsStringExt;
    let n = std::ffi::OsString::from_wide(&[0xd800, 65]);
    assert_eq!(name(&n).unwrap().as_os_str(), n);
}

#[cfg(unix)]
#[test]
fn posix_preserves_arbitrary_non_nul_bytes_and_posix_punctuation() {
    use std::os::unix::ffi::OsStrExt;
    for n in [
        b"C:foo".as_slice(),
        b"a\\b",
        b"NUL",
        b"trailing.",
        b" space ",
        b"hi\xff",
    ] {
        let s = OsStr::from_bytes(n);
        assert_eq!(name(s).unwrap().as_os_str(), s);
    }
    let p = Path::new(OsStr::from_bytes(b"/a/\xff"));
    assert_eq!(selected(p).unwrap().as_path().as_os_str(), p.as_os_str());
    assert_eq!(
        selected(Path::new("/a/\0")).unwrap_err(),
        LocalError::InvalidPath
    );
    assert_eq!(
        selected(Path::new("//a/../b")).unwrap_err(),
        LocalError::AbsolutePathRequired
    );
}

#[test]
fn errors_expose_legacy_public_codes() {
    use LocalError::*;
    for (error, code) in [
        (Missing, "storage_unavailable"),
        (Exists, "storage_unavailable"),
        (StorageUnavailable, "storage_unavailable"),
        (AbsolutePathRequired, "absolute_path_required"),
        (LocalPathRequired, "local_path_required"),
        (InvalidPath, "invalid_path"),
        (LocalVolumeRequired, "local_volume_required"),
        (UnsupportedPlatform, "unsupported_platform"),
        (PrivateOutboxRequired, "private_outbox_required"),
        (RegularDirectoryRequired, "regular_directory_required"),
        (RegularFileRequired, "regular_file_required"),
        (OverlappingPaths, "overlapping_paths"),
        (FileLimit, "file_limit"),
        (FileCountLimit, "file_count_limit"),
        (SourceChanged, "source_changed"),
        (OutboxConflict, "outbox_conflict"),
        (InvalidSettings, "invalid_settings"),
        (JournalConflict, "journal_conflict"),
        (IdempotencyConflict, "idempotency_conflict"),
        (Capacity, "capacity"),
        (Clock, "clock"),
        (NotFound, "not_found"),
        (Contract(Refusal::InvalidData), "invalid_data"),
    ] {
        assert_eq!(error.to_string(), code);
        assert_eq!(error.code(), code);
    }
    assert_ne!(Missing, Exists);
}
