use ironvellum_local::status::{Evidence, Heartbeat, StatusReader, rendezvous_name};
use ironvellum_local::{
    EndpointBinding, RefreshStatus, Selected, Settings, save_settings, selected,
};
use std::{
    collections::BTreeMap,
    fs,
    path::{Path, PathBuf},
    time::{Duration, Instant},
};
struct Fixture {
    root: PathBuf,
    path: Selected,
    settings: Settings,
}
impl Fixture {
    fn new() -> Self {
        let root = std::env::temp_dir();
        #[cfg(unix)]
        let root = root.canonicalize().unwrap();
        let root = root.join(format!(
            "iv-status-{}-{}",
            std::process::id(),
            std::thread::current().name().unwrap()
        ));
        fs::create_dir(&root).unwrap();
        let leaf = |s| selected(&root.join(s)).unwrap();
        let path = leaf("config/settings.json");
        let settings = Settings::new(leaf("in"), leaf("out"), leaf("state"));
        fs::create_dir(settings.input_directory.as_path()).unwrap();
        save_settings(&path, &settings).unwrap();
        Self {
            root,
            path,
            settings,
        }
    }
    fn dir(&self) -> PathBuf {
        self.path
            .as_path()
            .parent()
            .unwrap()
            .join(rendezvous_name(&self.path).unwrap())
    }
    fn start(&self, status: &RefreshStatus, ms: u64) -> Heartbeat {
        Heartbeat::start(
            &self.path,
            &EndpointBinding::bind(&self.settings).unwrap(),
            status,
            ms,
        )
        .unwrap()
    }
}
impl Drop for Fixture {
    fn drop(&mut self) {
        fs::remove_dir_all(&self.root).unwrap();
    }
}
fn tree(root: &Path) -> BTreeMap<PathBuf, Option<Vec<u8>>> {
    let mut result = BTreeMap::new();
    for entry in fs::read_dir(root).unwrap() {
        let p = entry.unwrap().path();
        if p.is_dir() {
            result.insert(p.clone(), None);
            result.extend(tree(&p));
        } else {
            let bytes = fs::read(&p).unwrap();
            result.insert(p, Some(bytes));
        }
    }
    result
}
#[test]
fn handshake_boundaries_and_readonly_evidence() {
    let f = Fixture::new();
    let mut reader = StatusReader::new(f.path.clone());
    let now = Instant::now();
    let before = tree(&f.root);
    let empty = reader.read(now, 20_000).unwrap().wire().unwrap();
    assert_eq!(
        empty["nextActions"],
        serde_json::json!(["transfer_training", "open_client"])
    );
    assert_eq!(before, tree(&f.root));
    let st = RefreshStatus::default();
    let mut hb = f.start(&st, 20_000);
    let own = f.dir().join(format!("{}.json", hb.id()));
    assert!(own.exists());
    let active = reader.read(now, 20_000).unwrap().wire().unwrap();
    assert_eq!(active["status"], "running");
    assert_eq!(active["clientVerified"], false);
    hb.evidence(Evidence::StatusChecked, &st, 20_000);
    hb.evidence(Evidence::Initialized, &st, 20_000);
    hb.evidence(Evidence::ToolsListed, &st, 20_000);
    let before = tree(&f.root);
    assert!(reader.read(now, 19_999).unwrap().sessions.is_empty());
    assert!(reader.read(now, 35_001).unwrap().sessions.is_empty());
    let report = reader.read(now, 35_000).unwrap().wire().unwrap();
    assert_eq!(report["clientVerified"], true);
    assert_eq!(
        report["sessions"][0]["loadedSource"],
        serde_json::Value::Null
    );
    assert_eq!(before, tree(&f.root));
    hb.close();
    assert!(!own.exists());
}

const RAW: &[u8] = include_bytes!("../../../tools/local_connector/contracts/fixtures/context.json");
const WALL: u64 = 1_700_001_000_000;
fn active(raw: &[u8]) -> RefreshStatus {
    RefreshStatus {
        active: Some(std::sync::Arc::new(
            ironvellum_contracts::snapshot::Snapshot::from_bytes(raw).unwrap(),
        )),
        ..Default::default()
    }
}

#[test]
fn independent_legacy_reports() {
    use ironvellum_local::status::{LoadedSource, SessionRecord, endpoint_digest};
    let goldens: serde_json::Value =
        serde_json::from_str(include_str!("legacy-status-results.json")).unwrap();
    let mut f = Fixture::new();
    let mut now = Instant::now();
    let mut reader = StatusReader::new(f.path.clone());
    let data = f.root.join("in/data.json");
    let badname = f.dir().join(format!("{}.json", "2".repeat(32)));
    let goodname = f.dir().join(format!("{}.json", "1".repeat(32)));
    let snap = active(RAW).active.unwrap();
    let binding = EndpointBinding::bind(&f.settings).unwrap();
    let mut record = SessionRecord {
        time_ms: WALL,
        endpoints: endpoint_digest(binding.paths(), binding.identities()).unwrap(),
        initialized: true,
        tools_listed: true,
        status_checked: true,
        loaded_source: Some(LoadedSource::from_snapshot(&snap).unwrap()),
        reload_required: false,
    };
    for case in goldens.as_array().unwrap() {
        let mut ms = WALL;
        match case["name"].as_str().unwrap() {
            "awaiting_training" => (),
            "pending_choice" => {
                fs::write(&data, RAW).unwrap();
            }
            "configured_source" => {
                f.settings.selected_dataset_id = Some(snap.dataset().to_string());
                save_settings(&f.path, &f.settings).unwrap();
            }
            "running_verified" => {
                f.start(&RefreshStatus::default(), WALL).close();
                fs::write(&goodname, record.encode().unwrap()).unwrap();
            }
            "valid_session_with_invalid_neighbor" => fs::write(&badname, b"{").unwrap(),
            "source_error_precedes_session_error" => fs::write(&data, b"{").unwrap(),
            "inclusive_fifteen_seconds" => {
                fs::write(&data, RAW).unwrap();
                fs::remove_file(&badname).unwrap();
                ms += 15_000;
            }
            "expired_session" => ms += 15_001,
            "future_session" => ms -= 1,
            "endpoint_mismatch" => {
                record.endpoints = "a".repeat(64);
                fs::write(&goodname, record.encode().unwrap()).unwrap();
            }
            other => panic!("unexpected case: {other}"),
        }
        now += Duration::from_secs(3);
        reader.read(now, ms).unwrap();
        now += Duration::from_secs(3);
        let before = tree(&f.root);
        assert_eq!(
            reader.read(now, ms).unwrap().wire().unwrap(),
            case["result"],
            "{}",
            case["name"]
        );
        assert_eq!(before, tree(&f.root));
    }
}

#[test]
fn all_handshake_orders_and_failed_publish_recovery() {
    let f = Fixture::new();
    let st = RefreshStatus::default();
    let mut reader = StatusReader::new(f.path.clone());
    let events = [
        Evidence::Initialized,
        Evidence::ToolsListed,
        Evidence::StatusChecked,
    ];
    for order in [
        [0, 1, 2],
        [0, 2, 1],
        [1, 0, 2],
        [1, 2, 0],
        [2, 0, 1],
        [2, 1, 0],
    ] {
        let mut hb = f.start(&st, WALL);
        let lock = f.dir().join(".ironvellum-lock-v1");
        fs::write(&lock, b"broken").unwrap();
        hb.evidence(events[order[0]], &st, WALL);
        let report = reader.read(Instant::now(), WALL).unwrap();
        let r = &report.sessions[0].record;
        assert!(!r.initialized && !r.tools_listed && !r.status_checked);
        fs::write(&lock, []).unwrap();
        hb.tick(&st, WALL);
        for (n, index) in order.into_iter().enumerate() {
            hb.evidence(events[index], &st, WALL);
            assert_eq!(
                reader.read(Instant::now(), WALL).unwrap().sessions[0]
                    .record
                    .verified(),
                n == 2
            );
        }
        hb.close();
    }
}

#[test]
fn multiple_loaded_sources_and_endpoint_changes() {
    let mut f = Fixture::new();
    let a = active(RAW);
    f.settings.selected_dataset_id = Some(a.active.as_ref().unwrap().dataset().to_owned());
    f.settings.watch_enabled = false;
    save_settings(&f.path, &f.settings).unwrap();
    let first = f.start(&a, WALL);
    let mut doc: serde_json::Value = serde_json::from_slice(RAW).unwrap();
    doc["snapshotId"] = "11111111-1111-4111-8111-111111111111".into();
    doc["exportedAtMs"] = (WALL + 1).into();
    let raw = serde_json::to_vec(&doc).unwrap();
    fs::write(f.root.join("in/new.json"), &raw).unwrap();
    let b = active(&raw);
    let second = f.start(&b, WALL);
    let mut reader = StatusReader::new(f.path.clone());
    let now = Instant::now();
    reader.read(now, WALL).unwrap();
    let report = reader.read(now + Duration::from_secs(3), WALL).unwrap();
    assert_eq!(
        report.inventory.available.unwrap().sha256,
        b.active.as_ref().unwrap().sha256()
    );
    assert!(report.sessions[0].id < report.sessions[1].id);
    for (hb, st) in [(&first, &a), (&second, &b)] {
        let r = report.sessions.iter().find(|r| r.id == hb.id()).unwrap();
        assert_eq!(
            r.record.loaded_source.as_ref().unwrap().value()["sourceSha256"],
            st.active.as_ref().unwrap().sha256()
        );
    }
    fs::rename(f.root.join("in"), f.root.join("old")).unwrap();
    let missing = reader.read(now + Duration::from_secs(6), WALL).unwrap();
    assert!(missing.sessions.iter().all(|s| s.reload_required));
    assert!(!f.root.join("in").exists());
    fs::create_dir(f.root.join("in")).unwrap();
    let replaced = reader.read(now + Duration::from_secs(9), WALL).unwrap();
    assert_eq!(replaced.state(), "client_reload_required");
    assert!(replaced.inventory.error_code.is_none());
    fs::remove_dir(f.root.join("in")).unwrap();
    fs::rename(f.root.join("old"), f.root.join("in")).unwrap();
    f.settings.state_directory = selected(&f.root.join("new-state")).unwrap();
    save_settings(&f.path, &f.settings).unwrap();
    assert!(
        reader
            .read(now + Duration::from_secs(12), WALL)
            .unwrap()
            .sessions
            .iter()
            .all(|s| s.reload_required)
    );
    first.close();
    second.close();
}

#[test]
fn reclaim_progress_bounds_and_protected_files() {
    let f = Fixture::new();
    let st = RefreshStatus::default();
    let mut hb = f.start(&st, WALL);
    let dir = f.dir();
    let own = dir.join(format!("{}.json", hb.id()));
    let mut record =
        ironvellum_local::status::SessionRecord::decode(&fs::read(&own).unwrap()).unwrap();
    let mut valid = Vec::new();
    for i in 0..40 {
        let p = dir.join(format!("{i:032x}.json"));
        fs::write(&p, record.encode().unwrap()).unwrap();
        valid.push(p);
    }
    let mut protected = valid;
    record.time_ms = WALL + 1;
    let future = dir.join(format!("{:032x}.json", 400));
    fs::write(&future, record.encode().unwrap()).unwrap();
    protected.push(future);
    record.time_ms = WALL - 15_000;
    let inclusive = dir.join(format!("{:032x}.json", 401));
    fs::write(&inclusive, record.encode().unwrap()).unwrap();
    protected.push(inclusive);
    record.time_ms -= 1;
    let expired = record.encode().unwrap();
    let mut stale = Vec::new();
    for i in 500..545 {
        let p = dir.join(format!("{i:032x}.json"));
        fs::write(&p, &expired).unwrap();
        stale.push(p);
    }
    let malformed = dir.join(format!("{:032x}.json", 700));
    fs::write(&malformed, b"{").unwrap();
    protected.push(malformed);
    let foreign = dir.join("other.json");
    fs::write(&foreign, &expired).unwrap();
    protected.push(foreign);
    let linked = dir.join(format!("{:032x}.json", 701));
    fs::write(&linked, &expired).unwrap();
    let alias = dir.join("alias");
    fs::hard_link(&linked, &alias).unwrap();
    protected.extend([linked, alias, own]);
    let mut left = stale.len();
    for _ in 0..30 {
        hb.tick(&st, WALL);
        let rem = stale.iter().filter(|p| p.exists()).count();
        assert!(left - rem <= 16);
        left = rem;
        if rem == 0 {
            break;
        }
    }
    assert_eq!(left, 0);
    assert!(protected.iter().all(|p| p.exists()));
    hb.close();
}

#[test]
fn scan_limit_and_recovery_keep_valid_rows() {
    let f = Fixture::new();
    let hb = f.start(&RefreshStatus::default(), WALL);
    let dir = f.dir();
    let mut reader = StatusReader::new(f.path.clone());
    let malformed = dir.join(format!("{:032x}.json", 1));
    fs::write(&malformed, b"{").unwrap();
    // Lock, own row and malformed row count towards the cap.
    for i in 0..4093 {
        fs::write(dir.join(format!("foreign{i}")), []).unwrap();
    }
    let read = |reader: &mut StatusReader| reader.read(Instant::now(), WALL).unwrap();
    assert_eq!(read(&mut reader).session_problem, Some("invalid_session"));
    let extra = dir.join("extra");
    fs::write(&extra, []).unwrap();
    assert_eq!(
        read(&mut reader).session_problem,
        Some("session_scan_limit")
    );
    fs::remove_file(extra).unwrap();
    fs::remove_file(malformed).unwrap();
    let clean = read(&mut reader);
    assert_eq!(clean.session_problem, None);
    assert_eq!(clean.sessions.len(), 1);
    hb.close();
}

#[test]
#[ignore = "invoked by ownership_safe_close"]
fn replace_owned_record_child() {
    use ironvellum_local::{Install, LockMode, LocklessOpen, PrivateFolder, name};
    let dir = std::env::var_os("IV_STATUS_TEST_DIR").unwrap();
    let child = std::env::var_os("IV_STATUS_TEST_FILE").unwrap();
    let folder =
        PrivateFolder::open(&selected(Path::new(&dir)).unwrap(), LocklessOpen::Existing).unwrap();
    let guard = folder.serialized(LockMode::Existing).unwrap();
    let filename = name(&child).unwrap();
    let mut bytes = guard.read(&filename, 8192).unwrap();
    bytes.push(b' ');
    guard.write(&filename, &bytes, Install::Replace).unwrap();
    println!("status replacement committed");
    // Remain under the publication lock until the parent has observed the committed write.
    let mut signal = String::new();
    std::io::stdin().read_line(&mut signal).unwrap();
}

#[test]
fn ownership_safe_close() {
    use std::{
        io::{BufRead, BufReader, Write},
        process::{Command, Stdio},
    };
    let f = Fixture::new();
    let st = RefreshStatus::default();
    let mut hb = f.start(&st, WALL);
    let filename = format!("{}.json", hb.id());
    let path = f.dir().join(&filename);
    let original = fs::read(&path).unwrap();
    // A failed publish must leave last-written ownership bytes intact.
    hb.tick(&st, 0);
    hb.close();
    assert!(!path.exists());
    let hb = f.start(&st, WALL);
    let filename = format!("{}.json", hb.id());
    let path = f.dir().join(&filename);
    let mut child = Command::new(std::env::current_exe().unwrap())
        .args([
            "--ignored",
            "--exact",
            "replace_owned_record_child",
            "--nocapture",
        ])
        .env("IV_STATUS_TEST_DIR", f.dir())
        .env("IV_STATUS_TEST_FILE", filename)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .spawn()
        .unwrap();
    let mut out = BufReader::new(child.stdout.take().unwrap());
    assert!(
        (&mut out)
            .lines()
            .map_while(Result::ok)
            .any(|line| line.contains("status replacement committed"))
    );
    let (done, closed) = std::sync::mpsc::channel();
    let closing = std::thread::spawn(move || {
        hb.close();
        done.send(()).unwrap();
    });
    assert_eq!(
        closed.recv_timeout(Duration::from_millis(60)),
        Err(std::sync::mpsc::RecvTimeoutError::Timeout)
    );
    writeln!(child.stdin.take().unwrap(), "release").unwrap();
    assert!(child.wait().unwrap().success());
    closing.join().unwrap();
    let mut changed = original;
    changed.push(b' ');
    assert_eq!(fs::read(path).unwrap(), changed);
}
