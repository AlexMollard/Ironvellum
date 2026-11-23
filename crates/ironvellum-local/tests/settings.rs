#![cfg(any(windows, target_os = "linux", target_os = "macos"))]
use ironvellum_local::{
    LocalError, LocklessOpen, PrivateFolder, Selected, Settings, load_settings, save_settings,
    selected, update_settings,
};
use std::{fs, path::PathBuf};

struct Fixture(PathBuf);

fn configure_settings(
    path: &Selected,
    dirs: Settings,
    dataset: Option<&str>,
    watch: Option<bool>,
) -> Result<Settings, LocalError> {
    use ironvellum_local::setup::{Client, SetupOptions, configure};
    Ok(configure(SetupOptions {
        settings: path.clone(),
        input: dirs.input_directory,
        outbox: dirs.outbox_directory,
        state: dirs.state_directory,
        dataset: dataset.map(str::to_owned),
        watch,
        client: Client::ConfigOnly,
        executable: selected(&std::env::current_exe().unwrap())?,
    })?
    .status
    .settings)
}

#[cfg(windows)]
#[test]
fn equivalent_endpoint_case_and_separators_keep_settings_bytes() {
    let f = Fixture::new();
    let target = f.target();
    configure_settings(&target, f.settings(), None, None).unwrap();
    let original = f.original();
    f.existing(original.as_bytes());
    let alternate = |s: &Selected| {
        selected(std::path::Path::new(
            &s.as_path()
                .to_str()
                .unwrap()
                .to_uppercase()
                .replace('\\', "/"),
        ))
        .unwrap()
    };
    let [a, b, c] = f.settings().endpoints().map(alternate);
    configure_settings(&target, Settings::new(a, b, c), None, None).unwrap();
    assert_eq!(fs::read(target.as_path()).unwrap(), original.as_bytes());
}

#[test]
fn first_setup_creates_input_and_retains_omitted_preferences() {
    let f = Fixture::new();
    let target = f.target();
    let id = "01234567-89ab-4def-8123-456789abcdef";
    configure_settings(&target, f.settings(), Some(id), Some(true)).unwrap();
    let original = fs::read(target.as_path()).unwrap();
    let repeated = configure_settings(&target, f.settings(), None, None).unwrap();
    assert_eq!(repeated.selected_dataset_id.as_deref(), Some(id));
    assert!(repeated.watch_enabled);
    assert_eq!(fs::read(target.as_path()).unwrap(), original);
    assert!(
        !configure_settings(&target, f.settings(), None, Some(false))
            .unwrap()
            .watch_enabled
    );
}

#[test]
fn ordinary_updates_and_saves_can_repair_a_former_input_which_is_now_a_file() {
    let f = Fixture::new();
    let target = f.target();
    let original = f.original();
    let stale = f.settings().input_directory;
    fs::write(stale.as_path(), b"unrelated file").unwrap();
    f.existing(original.as_bytes());
    let next = f.leaf("new-input");
    fs::create_dir(next.as_path()).unwrap();
    let mut configured = f.settings();
    configured.input_directory = next.clone();
    let result = update_settings(&target, |mut old| {
        assert_eq!(old.input_directory, stale);
        old.input_directory = next;
        Ok(old)
    })
    .unwrap();
    assert_eq!(result, configured);
    assert_eq!(load_settings(&target).unwrap(), configured);
    f.existing(original.as_bytes());
    save_settings(&target, &configured).unwrap();
    let repaired = fs::read(target.as_path()).unwrap();
    assert_eq!(load_settings(&target).unwrap(), configured);
    assert!(save_settings(&target, &f.settings()).is_err());
    assert_eq!(fs::read(target.as_path()).unwrap(), repaired);
    assert_eq!(fs::read(stale.as_path()).unwrap(), b"unrelated file");
    #[cfg(unix)]
    {
        let payload = f.0.join("payload");
        fs::rename(stale.as_path(), &payload).unwrap();
        std::os::unix::fs::symlink(&payload, stale.as_path()).unwrap();
        f.existing(original.as_bytes());
        assert!(save_settings(&target, &configured).is_err());
        assert_eq!(fs::read(target.as_path()).unwrap(), original.as_bytes());
        assert_eq!(fs::read(payload).unwrap(), b"unrelated file");
    }
}

#[cfg(unix)]
#[test]
fn moved_parent_cannot_become_a_configured_endpoint_during_update() {
    let f = Fixture::new();
    let mut settings = f.settings();
    fs::create_dir(settings.input_directory.as_path()).unwrap();
    settings.state_directory = f.leaf("separate-state");
    let target = f.target();
    save_settings(&target, &settings).unwrap();
    let original = fs::read(target.as_path()).unwrap();
    let moved = f.leaf("another-spelling");
    let before = fs::read_dir(f.leaf("state").as_path()).unwrap().count();
    let result = update_settings(&target, |mut latest| {
        fs::rename(f.leaf("state").as_path(), moved.as_path()).unwrap();
        latest.input_directory = moved.clone();
        Ok(latest)
    });
    assert_eq!(result, Err(LocalError::OverlappingPaths));
    assert_eq!(
        fs::read(moved.as_path().join("config.json")).unwrap(),
        original
    );
    assert_eq!(fs::read_dir(moved.as_path()).unwrap().count(), before);
}

#[test]
fn equivalent_directory_spelling_cannot_receive_settings_or_lock() {
    let f = Fixture::new();
    let value = f.settings();
    fs::create_dir(value.input_directory.as_path()).unwrap();
    let alias = f.leaf("CAFÉ INPUT");
    if !alias.as_path().is_dir() {
        return;
    } // Conditional on filesystem case handling.
    let location = f.leaf("CAFÉ INPUT/inner");
    let target = f.leaf("CAFÉ INPUT/inner/config.json");
    assert_eq!(
        save_settings(&target, &value),
        Err(LocalError::OverlappingPaths)
    );
    assert!(!location.as_path().exists());
    PrivateFolder::open(&location, LocklessOpen::CreateLeaf).unwrap();
    assert_eq!(
        save_settings(&target, &value),
        Err(LocalError::OverlappingPaths)
    );
    assert_eq!(fs::read_dir(location.as_path()).unwrap().count(), 0);
    let original = f.original();
    fs::write(target.as_path(), original.as_bytes()).unwrap();
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(target.as_path(), fs::Permissions::from_mode(0o600)).unwrap();
    }
    assert_eq!(
        update_settings(&target, Ok),
        Err(LocalError::OverlappingPaths)
    );
    assert_eq!(fs::read(target.as_path()).unwrap(), original.as_bytes());
    assert_eq!(fs::read_dir(location.as_path()).unwrap().count(), 1);
}

#[test]
fn bound_updates_refuse_changed_paths_identities_and_missing_endpoints() {
    use ironvellum_local::{endpoint_identities, update_bound_settings};
    let f = Fixture::new();
    let target = f.target();
    let v = f.settings();
    fs::create_dir(v.input_directory.as_path()).unwrap();
    save_settings(&target, &v).unwrap();
    let ids = endpoint_identities(v.endpoints(), LocklessOpen::Existing).unwrap();
    update_bound_settings(&target, v.endpoints(), ids, |mut settings| {
        settings.watch_enabled = true;
        Ok(settings)
    })
    .unwrap();
    let before = fs::read(target.as_path()).unwrap();
    let mut wrong_ids = ids;
    wrong_ids.swap(0, 2);
    assert_eq!(
        update_bound_settings(&target, v.endpoints(), wrong_ids, Ok),
        Err(LocalError::ReloadRequired)
    );
    let mut paths = v.endpoints();
    paths.swap(0, 1);
    assert_eq!(
        update_bound_settings(&target, paths, ids, Ok),
        Err(LocalError::ReloadRequired)
    );
    fs::remove_dir(v.outbox_directory.as_path()).unwrap();
    assert_eq!(
        update_bound_settings(&target, v.endpoints(), ids, Ok),
        Err(LocalError::ReloadRequired)
    );
    assert!(!v.outbox_directory.as_path().exists());
    assert_eq!(fs::read(target.as_path()).unwrap(), before);
}
impl Fixture {
    fn new() -> Self {
        let root = std::env::temp_dir();
        #[cfg(unix)]
        let root = root.canonicalize().unwrap();
        let root = root.join(format!(
            "ironvellum-settings-{}-{}",
            std::process::id(),
            std::thread::current().name().unwrap()
        ));
        fs::create_dir(&root).unwrap();
        Self(root)
    }
    fn leaf(&self, path: &str) -> Selected {
        selected(&self.0.join(path)).unwrap()
    }
    fn settings(&self) -> Settings {
        Settings::new(
            self.leaf("café input"),
            self.leaf("café input-out"),
            self.leaf("state"),
        )
    }
    fn target(&self) -> Selected {
        self.leaf("state/config.json")
    }
    fn existing(&self, bytes: &[u8]) {
        PrivateFolder::open(&self.leaf("state"), LocklessOpen::CreateLeaf).unwrap();
        fs::write(self.target().as_path(), bytes).unwrap();
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(self.target().as_path(), fs::Permissions::from_mode(0o600))
                .unwrap();
        }
    }
    fn original(&self) -> String {
        let quote = |n| {
            serde_json::to_string(self.leaf(n).as_path().to_str().unwrap())
                .unwrap()
                .replace('é', "\\u00e9")
        };
        format!(
            " {{\n\"watchEnabled\": false,\"selectedDatasetId\":null,\"stateDirectory\":{},\"outboxDirectory\":{},\"inputDirectory\":{},\"schemaVersion\":1}}\n",
            quote("state"),
            quote("café input-out"),
            quote("café input")
        )
    }
}
impl Drop for Fixture {
    fn drop(&mut self) {
        fs::remove_dir_all(&self.0).unwrap();
    }
}

#[test]
fn loads_legacy_bytes_without_creating_endpoints_or_lock() {
    let f = Fixture::new();
    assert_eq!(load_settings(&f.target()), Err(LocalError::Missing));
    assert!(!f.leaf("state").as_path().exists());
    let original = f.original();
    f.existing(original.as_bytes());
    assert_eq!(load_settings(&f.target()).unwrap(), f.settings());
    assert_eq!(fs::read(f.target().as_path()).unwrap(), original.as_bytes());
    assert_eq!(fs::read_dir(f.leaf("state").as_path()).unwrap().count(), 1);
    assert!(!f.settings().input_directory.as_path().exists());
    assert!(!f.settings().outbox_directory.as_path().exists());
}
#[test]
fn refused_settings_remain_byte_exact() {
    let f = Fixture::new();
    let original = f.original();
    let mut bad = vec![
        (vec![255], "invalid_data"),
        (vec![b' '; 65537], "file_limit"),
        (b"{}".to_vec(), "invalid_settings"),
    ];
    for (from, to, code) in [
        ("false", "0", "invalid_settings"),
        ("null", "\"ABC\"", "invalid_settings"),
        (
            "null",
            "\"12345678-1234-4123-7123-123456789abc\"",
            "invalid_settings",
        ),
        (
            "null",
            "\"12345678-1234-1123-8123-123456789abc\"",
            "invalid_settings",
        ),
        (":1}", ":1.0}", "invalid_settings"),
        (":1}", ":1e0}", "invalid_settings"),
        (":1}", ":true}", "invalid_settings"),
        (":1}", ":01}", "invalid_data"),
        (":1}", ":1,\"extra\":true}", "invalid_settings"),
        (":1}", ":1,\"schemaVersion\":1}", "invalid_data"),
        ("null", "\"\\ud800\"", "invalid_data"),
        (
            "null",
            "[[[[[[[[[[[[[[[[[[null]]]]]]]]]]]]]]]]]]",
            "invalid_data",
        ),
        ("\"watchEnabled\": false,", "", "invalid_settings"),
    ] {
        bad.push((original.replace(from, to).into_bytes(), code));
    }
    for (bytes, code) in bad {
        f.existing(&bytes);
        assert_eq!(load_settings(&f.target()).unwrap_err().code(), code);
        assert_eq!(
            save_settings(&f.target(), &f.settings())
                .unwrap_err()
                .code(),
            code
        );
        assert_eq!(
            update_settings(&f.target(), |_| panic!())
                .unwrap_err()
                .code(),
            code
        );
        assert_eq!(fs::read(f.target().as_path()).unwrap(), bytes);
    }
    let mut boundary = original.into_bytes();
    boundary.resize(65536, b' ');
    f.existing(&boundary);
    assert_eq!(load_settings(&f.target()).unwrap(), f.settings());
}
#[test]
fn locked_edits_preserve_disjoint_preferences_and_refuse_failed_changes() {
    let f = Fixture::new();
    f.existing(f.original().as_bytes());
    let target = f.target();
    let before = fs::read(target.as_path()).unwrap();
    assert_eq!(
        update_settings(&target, |_| Err(LocalError::Clock)),
        Err(LocalError::Clock)
    );
    assert_eq!(update_settings(&target, Ok), Err(LocalError::Missing));
    assert_eq!(fs::read(target.as_path()).unwrap(), before);
    fs::create_dir(f.settings().input_directory.as_path()).unwrap();
    let id = "12345678-1234-4123-8123-123456789abc";
    let gate = std::sync::Barrier::new(2);
    std::thread::scope(|s| {
        s.spawn(|| {
            gate.wait();
            update_settings(&target, |mut v| {
                v.watch_enabled = true;
                Ok(v)
            })
            .unwrap()
        });
        gate.wait();
        update_settings(&target, |mut v| {
            v.selected_dataset_id = Some(id.into());
            Ok(v)
        })
        .unwrap();
    });
    let result = load_settings(&target).unwrap();
    assert!(result.watch_enabled);
    assert_eq!(result.selected_dataset_id.as_deref(), Some(id));
}

#[test]
fn first_save_creates_private_leaves_and_writes_legacy_format() {
    let f = Fixture::new();
    let value = f.settings();
    fs::create_dir(value.input_directory.as_path()).unwrap();
    save_settings(&f.target(), &value).unwrap();
    let paths = value
        .endpoints()
        .map(|p| serde_json::to_string(p.as_path().to_str().unwrap()).unwrap());
    let expected = format!(
        "{{\"schemaVersion\": 1, \"inputDirectory\": {}, \"outboxDirectory\": {}, \"stateDirectory\": {}, \"selectedDatasetId\": null, \"watchEnabled\": false}}",
        paths[0], paths[1], paths[2]
    );
    assert_eq!(fs::read(f.target().as_path()).unwrap(), expected.as_bytes());
    assert!(value.outbox_directory.as_path().is_dir());
    assert_eq!(
        fs::read_dir(value.outbox_directory.as_path())
            .unwrap()
            .count(),
        0
    );
    let original = f.original();
    f.existing(original.as_bytes());
    save_settings(&f.target(), &value).unwrap();
    assert_eq!(fs::read(f.target().as_path()).unwrap(), original.as_bytes());
}
#[test]
fn refuses_unsafe_files_and_overlaps_before_creation() {
    let f = Fixture::new();
    let mut value = f.settings();
    for target in [
        f.leaf("café input/config.json"),
        f.leaf("café input-out/config.json"),
    ] {
        assert_eq!(
            save_settings(&target, &value),
            Err(LocalError::OverlappingPaths)
        );
    }
    for (a, b) in [
        ("input", "input"),
        ("input", "input/sub"),
        ("input/sub", "input"),
    ] {
        value.input_directory = f.leaf(a);
        value.outbox_directory = f.leaf(b);
        assert_eq!(
            save_settings(&f.target(), &value),
            Err(LocalError::OverlappingPaths)
        );
    }
    assert!(!f.leaf("state").as_path().exists());
    f.existing(f.original().as_bytes());
    let alias = f.0.join("alias");
    fs::hard_link(f.target().as_path(), &alias).unwrap();
    assert_eq!(
        save_settings(&f.target(), &f.settings()),
        Err(LocalError::RegularFileRequired)
    );
    assert_eq!(
        update_settings(&f.target(), Ok),
        Err(LocalError::RegularFileRequired)
    );
    assert_eq!(fs::read(alias).unwrap(), f.original().as_bytes());
}
#[test]
fn validates_raw_endpoint_spelling_on_read() {
    let f = Fixture::new();
    let placeholder = serde_json::to_string(f.leaf("state").as_path().to_str().unwrap()).unwrap();
    let check = |path: &str| {
        let raw = f
            .original()
            .replace(&placeholder, &serde_json::to_string(path).unwrap());
        f.existing(raw.as_bytes());
        load_settings(&f.target()).unwrap_err()
    };
    assert_eq!(check("relative"), LocalError::AbsolutePathRequired);
    assert!(
        check(f.0.ancestors().last().unwrap().to_str().unwrap())
            == LocalError::AbsolutePathRequired
    );
    assert_eq!(
        check(f.0.join("../state").to_str().unwrap()),
        LocalError::AbsolutePathRequired
    );
    assert!(matches!(
        check("//server/share/state"),
        LocalError::LocalPathRequired | LocalError::AbsolutePathRequired
    ));
    #[cfg(windows)]
    {
        assert_eq!(check(r"C:\NUL"), LocalError::InvalidPath);
        // Choose a genuinely unavailable/non-local drive without opening any directory.
        let unavailable = (b'A'..=b'Z')
            .map(|c| format!(r"{}:\synthetic-state", char::from(c)))
            .find(|p| {
                ironvellum_local::windows::validate_volume(&selected(p.as_ref()).unwrap())
                    == Err(LocalError::LocalVolumeRequired)
            });
        if let Some(path) = unavailable {
            assert_eq!(check(&path), LocalError::LocalVolumeRequired);
            let original = fs::read(f.target().as_path()).unwrap();
            let ids = [ironvellum_local::Identity { volume: 0, file: 0 }; 3];
            assert_eq!(
                ironvellum_local::update_bound_settings(
                    &f.target(),
                    f.settings().endpoints(),
                    ids,
                    |_| panic!()
                ),
                Err(LocalError::ReloadRequired)
            );
            assert_eq!(fs::read(f.target().as_path()).unwrap(), original);
            let malformed = String::from_utf8(original)
                .unwrap()
                .replace("null", "\"bad-id\"");
            f.existing(malformed.as_bytes());
            assert_eq!(
                ironvellum_local::update_bound_settings(
                    &f.target(),
                    f.settings().endpoints(),
                    ids,
                    |_| panic!()
                ),
                Err(LocalError::InvalidSettings)
            );
            assert_eq!(
                fs::read(f.target().as_path()).unwrap(),
                malformed.as_bytes()
            );
            let unavailable_parent =
                selected(&std::path::Path::new(&path).join("settings.json")).unwrap();
            assert_eq!(
                ironvellum_local::update_bound_settings(
                    &unavailable_parent,
                    f.settings().endpoints(),
                    ids,
                    |_| panic!()
                ),
                Err(LocalError::LocalVolumeRequired)
            );
        }
    }
}
#[cfg(unix)]
#[test]
fn refuses_unrepresentable_paths_without_changing_settings() {
    use std::{ffi::OsStr, os::unix::ffi::OsStrExt};
    let f = Fixture::new();
    f.existing(f.original().as_bytes());
    let mut value = f.settings();
    value.input_directory = selected(&f.0.join(OsStr::from_bytes(b"\xff"))).unwrap();
    assert_eq!(
        save_settings(&f.target(), &value),
        Err(LocalError::InvalidSettings)
    );
    assert_eq!(
        fs::read(f.target().as_path()).unwrap(),
        f.original().as_bytes()
    );
}
