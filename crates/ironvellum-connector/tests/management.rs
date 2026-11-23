use ironvellum_local::{LocklessOpen, PrivateFolder, selected};
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use std::{
    ffi::OsString,
    fs,
    path::{Path, PathBuf},
    process::Command,
};

const BIN: &str = env!("CARGO_BIN_EXE_ironvellum-connector");
const ID: &str = "01234567-89ab-4def-8123-456789abcdef";
struct Fixture(PathBuf);
impl Fixture {
    fn new() -> Self {
        let root = std::env::temp_dir();
        #[cfg(unix)]
        let root = root.canonicalize().unwrap();
        let dir = root.join(format!(
            "ironvellum setup-{}-{}",
            std::process::id(),
            std::thread::current().name().unwrap()
        ));
        PrivateFolder::open(&selected(&dir).unwrap(), LocklessOpen::CreateLeaf).unwrap();
        Self(dir)
    }
    fn path(&self, name: &str) -> PathBuf {
        self.0.join(name)
    }
    fn setup(&self, client: &str) -> Vec<OsString> {
        let mut args: Vec<OsString> = ["setup", "--json", "--client", client]
            .into_iter()
            .map(Into::into)
            .collect();
        for (key, dir) in [
            ("--settings", "settings ü.json"),
            ("--input-directory", "input"),
            ("--outbox-directory", "out"),
            ("--state-directory", "state"),
        ] {
            args.extend([key.into(), self.path(dir).into()]);
        }
        args
    }
    fn named(&self) -> Vec<OsString> {
        let mut args = self.setup("claude-desktop");
        args.extend(["--client-config".into(), self.path("client.json").into()]);
        args
    }
    fn command(&self, command: &str) -> Vec<OsString> {
        vec![
            command.into(),
            "--json".into(),
            "--settings".into(),
            self.path("settings ü.json").into(),
        ]
    }
}
impl Drop for Fixture {
    fn drop(&mut self) {
        fs::remove_dir_all(&self.0).unwrap();
    }
}
fn run(args: Vec<OsString>, status: i32) -> Value {
    let output = Command::new(BIN)
        .args(args)
        .env_remove("APPDATA")
        .output()
        .unwrap();
    assert!(output.stderr.is_empty(), "{:?}", output.stderr);
    assert_eq!(
        output.status.code(),
        Some(status),
        "{}",
        String::from_utf8_lossy(&output.stdout)
    );
    assert_eq!(output.stdout.last(), Some(&b'\n'));
    serde_json::from_slice(&output.stdout).unwrap()
}
fn put(path: &Path, raw: &[u8]) {
    fs::write(path, raw).unwrap();
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(path, fs::Permissions::from_mode(0o600)).unwrap();
    }
}
#[test]
fn headless_configuration_preferences_status_and_refusals() {
    let f = Fixture::new();
    let mut args = f.setup("config-only");
    args.extend(["--watch".into(), "--dataset-id".into(), ID.into()]);
    let result = run(args, 0);
    assert_eq!(result["status"], "awaiting_training");
    assert_eq!(result["sessions"], json!([]));
    assert_eq!(
        result["nextActions"],
        json!(["transfer_training", "open_client"])
    );
    assert_eq!(result["clientVerified"], false);
    assert_eq!(
        result["fragment"]["mcpServers"]["ironvellum-personal"],
        json!({"command":BIN,"args":["serve-folder","--settings",f.path("settings ü.json")]})
    );
    let mut args = f.setup("config-only");
    args.push("--dataset-id=".into());
    run(args, 0);
    let raw = fs::read(f.path("settings ü.json")).unwrap();
    let data: Value = serde_json::from_slice(&raw).unwrap();
    assert_eq!(
        data,
        json!({"schemaVersion":1,"inputDirectory":f.path("input"),"outboxDirectory":f.path("out"),"stateDirectory":f.path("state"),"selectedDatasetId":ID,"watchEnabled":true})
    );
    let mut args = f.setup("config-only");
    args.extend(["--watch".into(), "--no-watch".into()]);
    run(args, 0);
    let data: Value =
        serde_json::from_slice(&fs::read(f.path("settings ü.json")).unwrap()).unwrap();
    assert_eq!(data["watchEnabled"], false);
    let mut args = f.command("select-source");
    args.extend([
        "--dataset-id".into(),
        "11234567-89ab-4def-8123-456789abcdef".into(),
    ]);
    assert_eq!(run(args, 2)["code"], "dataset_unavailable");
    let before = fs::read(f.path("settings ü.json")).unwrap();
    let mut args = f.command("select-source");
    args.extend(["--dataset-id".into(), ID.into()]);
    assert_eq!(
        run(args, 2),
        json!({"schemaVersion":1,"status":"error","code":"dataset_unavailable","selectedDatasetId":null,"sessions":[],"nextActions":["check_settings"]})
    );
    assert_eq!(fs::read(f.path("settings ü.json")).unwrap(), before);
    let mut args = f.command("status");
    args.extend(["--secret".into(), "private-test-canary".into()]);
    assert_eq!(
        run(args, 2),
        json!({"schemaVersion":1,"status":"error","code":"invalid_arguments","selectedDatasetId":null,"sessions":[],"nextActions":["check_command_help"]})
    );
    put(&f.path("input/ironvellum.json"), b"bad");
    assert_eq!(run(f.command("status"), 0)["code"], "invalid_data");
}
#[test]
fn client_preflight_merge_unchanged_backup_and_restore() {
    let f = Fixture::new();
    let path = f.path("client.json");
    for raw in [
        b"[".as_slice(),
        b"[]",
        b"{\"mcpServers\":null}",
        b"{\"a\":1,\"a\":2}",
    ] {
        put(&path, raw);
        assert_eq!(run(f.named(), 2)["code"], "invalid_client_config");
        assert_eq!(fs::read(&path).unwrap(), raw);
        assert!(!f.path("input").exists());
    }
    let original=br#" { "huge": 123456789123456789123456789, "mcpServers":{"other":{"key":true},"ironvellum-personal":null} } "#;
    put(&path, original);
    let result = run(f.named(), 2);
    assert_eq!(result["nextActions"], json!(["review_existing_entry"]));
    assert!(!f.path("input").exists());
    let mut replace = f.named();
    replace.push("--replace-existing".into());
    let result = run(replace.clone(), 0);
    let digest = format!("{:x}", Sha256::digest(original));
    let backup = f.path(&format!("client.json.{}.bak", &digest[..24]));
    assert_eq!(result["backupPath"], json!(backup));
    assert_eq!(fs::read(&backup).unwrap(), original);
    assert_eq!(result["clientConfigChanged"], true);
    assert_eq!(
        result["nextActions"],
        json!(["transfer_training", "open_client", "reload_client"])
    );
    let raw = fs::read(&path).unwrap();
    let data: Value = serde_json::from_slice(&raw).unwrap();
    assert_eq!(data["huge"].to_string(), "123456789123456789123456789");
    assert_eq!(data["mcpServers"]["other"], json!({"key":true}));
    let same = run(f.named(), 0);
    assert_eq!(same["clientConfigChanged"], false);
    assert!(same["backupPath"].is_null());
    assert_eq!(fs::read(&path).unwrap(), raw);
    put(&path, &fs::read(&backup).unwrap());
    assert_eq!(fs::read(&path).unwrap(), original);
    put(&backup, b"conflict");
    assert_eq!(run(replace.clone(), 2)["code"], "client_backup_conflict");
    assert_eq!(fs::read(&path).unwrap(), original);
    put(&backup, original);
    assert_eq!(run(replace, 0)["backupPath"], json!(backup));
}

#[test]
fn client_options_and_path_placement_precede_endpoint_creation() {
    let f = Fixture::new();
    for option in ["--replace-existing", "--client-config=somewhere"] {
        let mut args = f.setup("config-only");
        args.push(option.into());
        assert_eq!(run(args, 2)["code"], "invalid_client_option");
    }
    for (target, expected) in [
        ("state/client.json", "overlapping_paths"),
        ("input/sub/client.json", "overlapping_paths"),
        ("settings ü.json", "overlapping_paths"),
        (".ironvellum-client", "invalid_client_config"),
    ] {
        let mut args = f.setup("claude-desktop");
        args.extend(["--client-config".into(), f.path(target).into()]);
        assert_eq!(run(args, 2)["code"], expected);
    }
    assert!(fs::read_dir(&f.0).unwrap().next().is_none());
    let result = run(f.named(), 0);
    assert!(result["backupPath"].is_null());
    assert_eq!(result["clientConfigChanged"], true);
}

#[test]
fn client_readonly_hardlinks_and_limits_preserve_bytes() {
    let f = Fixture::new();
    let path = f.path("client.json");
    put(&path, b"{}");
    let writable = fs::metadata(&path).unwrap().permissions();
    let mut protected = writable.clone();
    protected.set_readonly(true);
    fs::set_permissions(&path, protected.clone()).unwrap();
    assert_eq!(run(f.named(), 2)["code"], "client_config_read_only");
    fs::set_permissions(&path, writable.clone()).unwrap();
    assert!(!f.path("input").exists());
    let link = f.path("other.json");
    fs::hard_link(&path, &link).unwrap();
    assert_eq!(run(f.named(), 2)["code"], "regular_file_required");
    assert_eq!(fs::read(&link).unwrap(), b"{}");
    fs::remove_file(link).unwrap();
    let mut big = br#"{"other":""#.to_vec();
    big.resize((4 << 20) - 2, b'x');
    big.extend_from_slice(br#""}"#);
    put(&path, &big);
    assert_eq!(run(f.named(), 2)["code"], "file_limit");
    big.push(b' ');
    put(&path, &big);
    assert_eq!(run(f.named(), 2)["code"], "file_limit");
    assert_eq!(fs::read(&path).unwrap(), big);
    put(&path, b"{}");
    run(f.named(), 0);
    let installed = fs::read(&path).unwrap();
    fs::set_permissions(&path, protected).unwrap();
    let same = run(f.named(), 0);
    assert_eq!(same["clientConfigChanged"], false);
    assert_eq!(fs::read(&path).unwrap(), installed);
    fs::set_permissions(&path, writable).unwrap();
}

#[test]
fn platform_default_uses_only_the_synthetic_home() {
    let f = Fixture::new();
    let mut command = Command::new(BIN);
    command
        .args(f.setup("claude-desktop"))
        .env("HOME", &f.0)
        .env("APPDATA", &f.0)
        .env("XDG_CONFIG_HOME", f.path("unused"));
    #[cfg(windows)]
    let base = f.0.clone();
    #[cfg(target_os = "linux")]
    let base = f.path(".config");
    #[cfg(target_os = "macos")]
    let base = f.path("Library/Application Support");
    fs::create_dir_all(&base).unwrap();
    let output = command.output().unwrap();
    assert!(
        output.status.success(),
        "{}",
        String::from_utf8_lossy(&output.stdout)
    );
    assert!(base.join("Claude/claude_desktop_config.json").is_file());
    assert!(!f.path("unused").exists());
    #[cfg(windows)]
    assert_eq!(
        run(f.setup("claude-desktop"), 2)["code"],
        "client_config_required"
    );
}

#[test]
fn selection_settles_the_real_inventory_and_reports_current_source() {
    let f = Fixture::new();
    run(f.setup("config-only"), 0);
    let export = include_bytes!("../../../tools/local_connector/contracts/fixtures/context.json");
    put(&f.path("input/export.json"), export);
    let data: Value = serde_json::from_slice(export).unwrap();
    let id = data["datasetId"].as_str().unwrap();
    let mut args = f.command("select-source");
    args.extend(["--dataset-id".into(), id.into()]);
    let result = run(args, 0);
    assert_eq!(result["selectedDatasetId"], id);
    assert_eq!(result["availableSource"]["datasetId"], id);
    assert_eq!(result["clientVerified"], false);
}
