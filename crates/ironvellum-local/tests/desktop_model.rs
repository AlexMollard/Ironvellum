use ironvellum_contracts::snapshot::Snapshot;
use ironvellum_local::{
    EndpointBinding, Journal, LocalError, LocklessOpen, PrivateFolder, RefreshStatus, Selected,
    Settings,
    desktop_model::{
        DesktopObserver, Editor, FolderEdit, Lifecycle, display_literal, resolve_folder,
        resolve_ready_draft,
    },
    load_settings, save_settings, selected,
    status::{Evidence, Heartbeat, rendezvous_name},
    update_settings,
};
use std::{
    fs,
    path::PathBuf,
    sync::Arc,
    time::{Duration, Instant},
};

const RAW: &[u8] = include_bytes!("../../../tools/local_connector/contracts/fixtures/context.json");
const PROPOSAL: &[u8] =
    include_bytes!("../../../tools/local_connector/contracts/fixtures/proposal.json");
const WALL: u64 = 1_700_001_000_000;
const DB: &str = ".ironvellum-proposals-v1.sqlite";

struct Fixture {
    root: PathBuf,
    file: Selected,
    settings: Settings,
}
impl Fixture {
    fn new() -> Self {
        let root = std::env::temp_dir();
        #[cfg(unix)]
        let root = root.canonicalize().unwrap();
        let root = root.join(format!(
            "iv-desktop-{}-{}",
            std::process::id(),
            std::thread::current().name().unwrap()
        ));
        fs::create_dir(&root).unwrap();
        let path = |leaf| selected(&root.join(leaf)).unwrap();
        Self {
            file: path("config/settings.json"),
            settings: Settings::new(path("training"), path("out"), path("state")),
            root,
        }
    }
    fn configure(&self) {
        fs::create_dir(self.settings.input_directory.as_path()).unwrap();
        save_settings(&self.file, &self.settings).unwrap();
    }
    fn export(&self) -> PathBuf {
        let export = self.settings.input_directory.as_path().join("export.json");
        fs::write(&export, RAW).unwrap();
        export
    }
    fn session_dir(&self) -> PathBuf {
        self.file
            .as_path()
            .parent()
            .unwrap()
            .join(rendezvous_name(&self.file).unwrap())
    }
}
impl Drop for Fixture {
    fn drop(&mut self) {
        fs::remove_dir_all(&self.root).unwrap();
    }
}

#[test]
fn detached_reads_do_not_create_configuration_locks_or_folders() {
    let f = Fixture::new();
    let mut reader = DesktopObserver::new(f.file.clone());
    let now = Instant::now();
    assert_eq!(reader.read(now, WALL).lifecycle(), Lifecycle::Unconfigured);
    assert_eq!(fs::read_dir(&f.root).unwrap().count(), 0);
    f.configure();
    let bytes = fs::read(f.file.as_path()).unwrap();
    assert_eq!(
        reader.read(now, WALL).lifecycle(),
        Lifecycle::AwaitingTraining
    );
    assert!(!f.session_dir().exists());
    assert_eq!(
        fs::read_dir(f.settings.outbox_directory.as_path())
            .unwrap()
            .count(),
        0
    );
    assert_eq!(bytes, fs::read(f.file.as_path()).unwrap());
}

#[test]
fn recovered_input_rescans_before_the_next_interval() {
    let mut f = Fixture::new();
    f.settings.selected_dataset_id = Some(Snapshot::from_bytes(RAW).unwrap().dataset().into());
    f.configure();
    let export = f.export();
    let mut reader = DesktopObserver::new(f.file.clone());
    let now = Instant::now();
    reader.read(now, WALL);
    let ready = now + Duration::from_secs(2);
    assert!(reader.read(ready, WALL).source.unwrap().available.is_some());

    let input = f.settings.input_directory.as_path();
    let parked = f.root.join("parked-training");
    fs::rename(input, &parked).unwrap();
    let stale = reader.read(ready + Duration::from_millis(100), WALL);
    assert!(stale.source_stale);
    assert!(stale.source.unwrap().available.is_some());
    fs::remove_file(parked.join(export.file_name().unwrap())).unwrap();
    fs::rename(parked, input).unwrap();
    let recovered = reader.read(ready + Duration::from_millis(200), WALL);
    assert!(!recovered.source_stale);
    assert!(recovered.source_problem.is_none());
    assert!(recovered.source.unwrap().available.is_none());
}

#[test]
fn independent_failures_keep_truthful_evidence_and_recover() {
    let mut f = Fixture::new();
    let snapshot = Arc::new(Snapshot::from_bytes(RAW).unwrap());
    f.settings.selected_dataset_id = Some(snapshot.dataset().to_owned());
    f.settings.watch_enabled = false;
    f.configure();
    let export = f.export();
    let mut newer: serde_json::Value = serde_json::from_slice(RAW).unwrap();
    newer["snapshotId"] = serde_json::json!("87654321-0000-4000-8000-123456789012");
    newer["exportedAtMs"] = serde_json::json!(WALL);
    fs::write(&export, serde_json::to_vec(&newer).unwrap()).unwrap();
    let st = RefreshStatus {
        active: Some(snapshot),
        ..Default::default()
    };
    let mut hb = Heartbeat::start(
        &f.file,
        &EndpointBinding::bind(&f.settings).unwrap(),
        &st,
        WALL,
    )
    .unwrap();
    for event in [
        Evidence::Initialized,
        Evidence::ToolsListed,
        Evidence::StatusChecked,
    ] {
        hb.evidence(event, &st, WALL);
    }
    // Keep the exact on-disk record without pinning the directory under the
    // publisher. This models an external process that stopped without cleanup.
    let record = f.session_dir().join(format!("{}.json", hb.id()));
    let bytes = fs::read(&record).unwrap();
    hb.close();
    fs::write(record, bytes).unwrap();
    let mut reader = DesktopObserver::new(f.file.clone());
    let start = Instant::now();
    let mut tick_number = 0;
    let mut poll = |reader: &mut DesktopObserver, wall| {
        let now = start + Duration::from_secs(tick_number * 2);
        tick_number += 1;
        reader.read(now, wall)
    };
    assert!(poll(&mut reader, WALL).source.unwrap().scanning);
    let state = poll(&mut reader, WALL);
    let available = state.source.as_ref().unwrap().available.clone().unwrap();
    assert_eq!(available.snapshot_id, newer["snapshotId"].as_str().unwrap());
    assert_eq!(
        state.sessions[0]
            .record
            .loaded_source
            .as_ref()
            .unwrap()
            .value()["snapshotId"],
        st.active.as_ref().unwrap().snapshot()
    );
    assert_eq!(state.verified_count(), 1);

    let broken = f.settings.outbox_directory.as_path().join(DB);
    fs::write(&broken, b"bad database").unwrap();
    let state = poll(&mut reader, WALL);
    assert!(state.archive_problem.is_some());
    assert_eq!(state.verified_count(), 1);
    assert_eq!(state.source.unwrap().available, Some(available.clone()));
    fs::remove_file(broken).unwrap();
    assert!(poll(&mut reader, WALL).archive_problem.is_none());
    let parked = f.root.join("parked-outbox");
    fs::rename(f.settings.outbox_directory.as_path(), &parked).unwrap();
    let no_outbox = poll(&mut reader, WALL);
    assert!(no_outbox.archive_problem.is_some());
    assert!(no_outbox.reload_required());
    assert_eq!(no_outbox.source.unwrap().available, Some(available.clone()));
    fs::rename(parked, f.settings.outbox_directory.as_path()).unwrap();

    let parked = f.root.join("parked-sessions");
    fs::rename(f.session_dir(), &parked).unwrap();
    fs::write(f.session_dir(), b"broken session directory").unwrap();
    let stale = poll(&mut reader, WALL);
    assert!(stale.session_problem.is_some());
    assert!(stale.sessions_stale);
    assert_eq!(stale.sessions.len(), 1);
    assert_eq!(stale.verified_count(), 0);
    assert_eq!(stale.source.unwrap().available, Some(available.clone()));
    fs::remove_file(f.session_dir()).unwrap();
    fs::rename(parked, f.session_dir()).unwrap();
    assert_eq!(poll(&mut reader, WALL).verified_count(), 1);

    let parked = f.root.join("parked-input");
    fs::rename(f.settings.input_directory.as_path(), &parked).unwrap();
    let stale = poll(&mut reader, WALL);
    assert!(stale.source_stale);
    assert!(stale.source_problem.is_some());
    assert_eq!(stale.verified_count(), 1);
    assert!(stale.reload_required());
    assert_eq!(stale.source.unwrap().available, Some(available));
    fs::rename(parked, f.settings.input_directory.as_path()).unwrap();
    poll(&mut reader, WALL);
    assert!(!poll(&mut reader, WALL).source_stale);

    let bytes = fs::read(f.file.as_path()).unwrap();
    fs::write(f.file.as_path(), b"{").unwrap();
    let bad_settings = poll(&mut reader, WALL);
    assert!(bad_settings.settings_problem.is_some());
    assert!(bad_settings.settings_stale && bad_settings.sessions_stale);
    assert_eq!(bad_settings.verified_count(), 0);
    fs::write(f.file.as_path(), bytes).unwrap();
    let expired = poll(&mut reader, WALL + 15_001);
    assert!(expired.sessions.is_empty());
    assert!(!expired.sessions_stale);
    assert_eq!(expired.lifecycle(), Lifecycle::Configured);
}

#[test]
fn archived_drafts_survive_their_source_and_resolve_only_exact_ready_files() {
    let f = Fixture::new();
    f.configure();
    let folder = PrivateFolder::open(&f.settings.outbox_directory, LocklessOpen::Existing).unwrap();
    let snapshot = Snapshot::from_bytes(RAW).unwrap();
    let mut proposal: serde_json::Value = serde_json::from_slice(PROPOSAL).unwrap();
    proposal["draft"]["name"] = serde_json::json!("A\u{202E} & [B]");
    Journal::new(&folder, &snapshot)
        .create("ready", &proposal["draft"], WALL)
        .unwrap();
    let now = Instant::now();
    let mut reader = DesktopObserver::new(f.file.clone());
    let state = reader.read(now, WALL + 20_000);
    assert!(state.sessions.is_empty());
    assert!(state.source.unwrap().available.is_none());
    assert_eq!(state.drafts.len(), 1);
    assert_eq!(state.drafts[0].display_name, "A� & [B]");
    let filename = &state.drafts[0].filename;
    assert_eq!(
        resolve_ready_draft(&f.settings.outbox_directory, filename).unwrap(),
        f.settings.outbox_directory.as_path().join(filename)
    );
    let lock = f
        .settings
        .outbox_directory
        .as_path()
        .join(".ironvellum-lock-v1");
    let parked = f.root.join("parked-lock");
    let bytes = fs::read(&lock).unwrap();
    fs::rename(&lock, &parked).unwrap();
    let stale = reader.read(now, WALL);
    assert_eq!(stale.drafts, state.drafts);
    assert!(stale.drafts_stale);
    assert!(stale.archive_problem.is_some());
    assert!(!lock.exists());
    assert!(resolve_ready_draft(&f.settings.outbox_directory, filename).is_err());
    fs::rename(parked, &lock).unwrap();
    assert_eq!(fs::read(&lock).unwrap(), bytes);
    assert!(!reader.read(now, WALL).drafts_stale);
    let dir = selected(&f.root.join("folder & [literal]")).unwrap();
    fs::create_dir(dir.as_path()).unwrap();
    assert_eq!(resolve_folder(&dir).unwrap(), dir.as_path());
    assert!(resolve_ready_draft(&f.settings.outbox_directory, "../state").is_err());
    fs::remove_file(f.settings.outbox_directory.as_path().join(filename)).unwrap();
    assert!(resolve_ready_draft(&f.settings.outbox_directory, filename).is_err());
    assert_eq!(display_literal("a\u{85}\u{200D}\u{E0001}\n&"), "a����&");
}

#[test]
fn explicit_actions_use_latest_settings_and_cancel_ignores_late_results() {
    let f = Fixture::new();
    f.configure();
    let export = f.export();
    let now = Instant::now();
    let mut reader = DesktopObserver::new(f.file.clone());
    reader.read(now, WALL);
    let state = reader.read(now + Duration::from_secs(2), WALL);
    let dataset = state.source.unwrap().pending[0].dataset_id.clone();
    update_settings(&f.file, |mut s| {
        s.watch_enabled = false;
        Ok(s)
    })
    .unwrap();
    reader.select(&dataset).unwrap();
    assert!(!load_settings(&f.file).unwrap().watch_enabled);
    fs::remove_file(export).unwrap();
    reader.select(&dataset).unwrap();
    assert_eq!(
        reader.select("not-a-dataset"),
        Err(LocalError::DatasetUnavailable)
    );

    let new_input = selected(&f.root.join("new input")).unwrap();
    fs::create_dir(new_input.as_path()).unwrap();
    let mut editor = Editor::default();
    let edit = FolderEdit {
        input: Some(new_input.clone()),
        outbox: Some(f.settings.outbox_directory.clone()),
        watch_enabled: true,
    };
    editor.stage(edit.clone());
    let old = editor.dispatch().unwrap();
    editor.cancel();
    let newer_edit = FolderEdit {
        input: None,
        ..edit.clone()
    };
    editor.stage(newer_edit.clone());
    let new = editor.dispatch().unwrap();
    let newer_state = selected(&f.root.join("state-2")).unwrap();
    update_settings(&f.file, |mut s| {
        s.state_directory = newer_state.clone();
        Ok(s)
    })
    .unwrap();
    let result = reader.save_existing(&old.edit);
    assert!(result.is_ok());
    let committed = reader.complete(&mut editor, old, result, now, WALL);
    assert_eq!(editor.draft(), Some(&newer_edit));
    assert_eq!(editor.outcome(), None);
    let persisted = committed.settings.unwrap();
    assert_eq!(persisted.input_directory, new_input);
    assert_eq!(persisted.state_directory, newer_state);
    assert_eq!(
        persisted.selected_dataset_id.as_deref(),
        Some(dataset.as_str())
    );
    let result = reader.save_existing(&new.edit);
    assert!(result.is_err());
    let _ = reader.complete(&mut editor, new, result, now, WALL);
    assert_eq!(editor.draft(), Some(&newer_edit));
    assert_eq!(editor.outcome(), Some(result));
    let missing = selected(&f.root.join("missing out")).unwrap();
    assert!(
        reader
            .save_existing(&FolderEdit {
                outbox: Some(missing.clone()),
                ..edit
            })
            .is_err()
    );
    assert!(!missing.as_path().exists());
}

#[test]
fn post_commit_error_rereads_the_new_settings_and_keeps_the_failed_edit() {
    let f = Fixture::new();
    f.configure();
    let mut reader = DesktopObserver::new(f.file.clone());
    let mut editor = Editor::default();
    let edit = FolderEdit {
        input: Some(f.settings.input_directory.clone()),
        outbox: Some(f.settings.outbox_directory.clone()),
        watch_enabled: true,
    };
    editor.stage(edit.clone());
    let action = editor.dispatch().unwrap();
    assert!(editor.dispatch().is_none());
    reader.save_existing(&action.edit).unwrap();
    // Simulate a directory sync reporting an error after the replacement.
    let failure = Err(LocalError::StorageUnavailable);
    let state = reader.complete(&mut editor, action, failure, Instant::now(), WALL);
    assert!(state.settings.unwrap().watch_enabled);
    assert_eq!(editor.draft(), Some(&edit));
    assert_eq!(editor.outcome(), Some(failure));
    let retry = editor.dispatch().unwrap();
    let result = reader.save_existing(&retry.edit);
    reader.complete(&mut editor, retry, result, Instant::now(), WALL);
    assert_eq!(editor.outcome(), Some(Ok(())));
    assert!(editor.draft().is_none());
}
