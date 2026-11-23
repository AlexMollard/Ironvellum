use ironvellum_contracts::snapshot::Snapshot;
use ironvellum_local::{
    EndpointBinding, Journal, LocklessOpen, PrivateFolder, RefreshStatus, Selected, Settings,
    desktop_model::{DesktopObserver, DesktopState},
    save_settings, selected,
    status::{Evidence, Heartbeat, rendezvous_name},
};
use serde_json::{Value, json};
use std::{
    collections::BTreeMap,
    fs,
    path::PathBuf,
    sync::Arc,
    time::{Duration, Instant},
};

const RAW: &[u8] =
    include_bytes!("../../../../tools/local_connector/contracts/fixtures/context.json");
const PROPOSAL: &[u8] =
    include_bytes!("../../../../tools/local_connector/contracts/fixtures/proposal.json");
const WALL: u64 = 1_700_001_000_000;
struct Fixture {
    _root: tempfile::TempDir,
    settings: Settings,
    file: Selected,
    observer: DesktopObserver,
    now: Instant,
}
impl Fixture {
    fn new() -> Self {
        let root = tempfile::tempdir().unwrap();
        #[cfg(unix)]
        let base = root.path().canonicalize().unwrap();
        #[cfg(windows)]
        let base = root.path().to_owned();
        let path = |leaf| selected(&base.join(leaf)).unwrap();
        let file = path("private/settings.json");
        let settings = Settings::new(path("Exports"), path("Drafts"), path("state"));
        let observer = DesktopObserver::new(file.clone());
        Self {
            _root: root,
            file,
            settings,
            observer,
            now: Instant::now(),
        }
    }
    fn poll(&mut self) -> DesktopState {
        self.now += Duration::from_secs(4);
        self.observer.read(self.now, WALL)
    }
    fn evidence(
        &self,
        session: u32,
        snapshot: Option<Arc<Snapshot>>,
        initialized: bool,
        verified: bool,
        reload: bool,
    ) -> PathBuf {
        let status = RefreshStatus {
            active: snapshot,
            reload_required: reload,
            ..Default::default()
        };
        let mut hb = Heartbeat::start(
            &self.file,
            &EndpointBinding::bind(&self.settings).unwrap(),
            &status,
            WALL,
        )
        .unwrap();
        if initialized {
            hb.evidence(Evidence::Initialized, &status, WALL);
        }
        if verified {
            for e in [Evidence::ToolsListed, Evidence::StatusChecked] {
                hb.evidence(e, &status, WALL);
            }
        }
        let folder = self
            .file
            .as_path()
            .parent()
            .unwrap()
            .join(rendezvous_name(&self.file).unwrap());
        let raw = fs::read(folder.join(format!("{}.json", hb.id()))).unwrap();
        hb.close();
        // Retain actual encoded evidence with a repeatable synthetic session ID.
        let record = folder.join(format!("{session:032x}.json"));
        fs::write(&record, raw).unwrap();
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&record, fs::Permissions::from_mode(0o600)).unwrap();
        }
        record
    }
    fn publish(&self, snapshot: &Snapshot, title: &str, key: &str) {
        let mut data: Value = serde_json::from_slice(PROPOSAL).unwrap();
        data["draft"]["name"] = json!(title);
        let folder =
            PrivateFolder::open(&self.settings.outbox_directory, LocklessOpen::Existing).unwrap();
        Journal::new(&folder, snapshot)
            .create(key, &data["draft"], WALL)
            .unwrap();
    }
}

pub fn observe_states() -> BTreeMap<&'static str, Arc<DesktopState>> {
    let mut f = Fixture::new();
    let mut states = BTreeMap::new();
    states.insert("unconfigured", Arc::new(f.poll()));
    fs::create_dir(f.settings.input_directory.as_path()).unwrap();
    let raw = Arc::new(Snapshot::from_bytes(RAW).unwrap());
    f.settings.watch_enabled = true;
    f.settings.selected_dataset_id = Some(raw.dataset().into());
    save_settings(&f.file, &f.settings).unwrap();
    states.insert("awaiting", Arc::new(f.poll()));
    let export = f.settings.input_directory.as_path().join("training.json");
    fs::write(&export, RAW).unwrap();
    f.poll();
    let detached = f.poll();
    assert!(detached.source.as_ref().unwrap().available.is_some() && detached.sessions.is_empty());
    states.insert("detached", Arc::new(detached));
    f.evidence(1, Some(raw.clone()), true, true, false);
    let incomplete = f.settings.input_directory.as_path().join("incoming.json");
    fs::write(&incomplete, b"{").unwrap();
    f.poll();
    let recovery = f.poll();
    assert!(
        recovery.source_problem.is_some() && recovery.sessions[0].record.loaded_source.is_some()
    );
    states.insert("recovery", Arc::new(recovery));
    fs::remove_file(incomplete).unwrap();
    f.publish(&raw, "Upper A", "upper");
    f.publish(&raw, "Lower A", "lower");
    let overview = f.poll();
    assert_eq!(overview.drafts.len(), 2);
    assert_eq!(overview.verified_count(), 1);
    states.insert("overview", Arc::new(overview));
    let mut other: Value = serde_json::from_slice(RAW).unwrap();
    other["datasetId"] = json!("01234567-89ab-4def-8123-456789abcdef");
    other["snapshotId"] = json!("550e8400-e29b-41d4-a716-446655440042");
    other["exportedAtMs"] = json!(WALL + 60_000);
    let other_raw = serde_json::to_vec(&other).unwrap();
    let file = f.settings.input_directory.as_path().join("other.json");
    fs::write(file, &other_raw).unwrap();
    f.poll();
    let choice = f.poll();
    assert!(!choice.source.as_ref().unwrap().pending.is_empty());
    states.insert("choice", Arc::new(choice));
    fs::remove_file(&export).unwrap();
    f.poll();
    let keep = f.poll();
    assert!(keep.source.as_ref().unwrap().available.is_none());
    states.insert("keep", Arc::new(keep));
    let second = Snapshot::from_bytes(&other_raw).unwrap();
    f.evidence(2, Some(Arc::new(second)), true, false, true);
    f.evidence(3, None, false, false, false);
    let mut newer: Value = serde_json::from_slice(RAW).unwrap();
    newer["snapshotId"] = json!("550e8400-e29b-41d4-a716-446655440043");
    newer["exportedAtMs"] = json!(WALL + 120_000);
    fs::write(export, serde_json::to_vec(&newer).unwrap()).unwrap();
    f.publish(
        &raw,
        "{Upper} & 東京 · 서울 · 训练 e\u{301} — long workout name for a narrow screen",
        "mixed",
    );
    f.poll();
    let mixed = f.poll();
    assert_eq!(mixed.sessions.len(), 3);
    assert_eq!(mixed.verified_count(), 1);
    assert!(mixed.reload_required());
    assert_eq!(
        mixed
            .source
            .as_ref()
            .unwrap()
            .available
            .as_ref()
            .unwrap()
            .snapshot_id,
        newer["snapshotId"].as_str().unwrap()
    );
    states.insert("mixed", Arc::new(mixed));
    let db = f
        .settings
        .outbox_directory
        .as_path()
        .join(".ironvellum-proposals-v1.sqlite");
    let original = fs::read(&db).unwrap();
    fs::write(&db, b"invalid synthetic fixture").unwrap();
    let archive = f.poll();
    assert!(archive.archive_problem.is_some() && archive.drafts_stale);
    states.insert("draft-error", Arc::new(archive));
    fs::write(db, original).unwrap();
    f.poll();
    fs::write(f.file.as_path(), b"{").unwrap();
    let stale = f.poll();
    assert!(stale.settings_stale && stale.sessions_stale);
    states.insert("stale", Arc::new(stale));
    states
}
