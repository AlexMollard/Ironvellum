#![cfg(any(windows, target_os = "linux", target_os = "macos"))]
use ironvellum_local::{
    DetachedInventory, EndpointBinding, Journal, LocalError, Settings, WatchedSource,
    load_settings, save_settings, selected,
};
use std::{
    fs,
    path::PathBuf,
    time::{Duration, Instant},
};
const RAW: &[u8] = include_bytes!("../../../tools/local_connector/contracts/fixtures/context.json");
struct Fixture {
    root: PathBuf,
    settings: Settings,
}
impl Fixture {
    fn config(&self) -> ironvellum_local::Selected {
        selected(&self.root.join("state/settings.json")).unwrap()
    }
    fn watcher(&self) -> WatchedSource {
        WatchedSource::bind(
            self.config(),
            self.settings.clone(),
            EndpointBinding::bind(&self.settings).unwrap(),
        )
        .unwrap()
    }
    fn new() -> Self {
        let root = std::env::temp_dir();
        #[cfg(unix)]
        let root = root.canonicalize().unwrap();
        let root = root.join(format!(
            "ironvellum-inventory-{}-{}",
            std::process::id(),
            std::thread::current().name().unwrap()
        ));
        fs::create_dir(&root).unwrap();
        let leaf = |n| selected(&root.join(n)).unwrap();
        let settings = Settings::new(leaf("in"), leaf("out"), leaf("state"));
        fs::create_dir(settings.input_directory.as_path()).unwrap();
        save_settings(&leaf("state/settings.json"), &settings).unwrap();
        Self { root, settings }
    }
    fn put(&self, n: &str, bytes: &[u8]) {
        fs::write(self.root.join("in").join(n), bytes).unwrap();
    }
}
impl Drop for Fixture {
    fn drop(&mut self) {
        fs::remove_dir_all(&self.root).unwrap();
    }
}
fn export(dataset: u8, snapshot: u8, time: u64, length: usize) -> Vec<u8> {
    let mut doc: serde_json::Value = serde_json::from_slice(RAW).unwrap();
    doc["datasetId"] = format!("00000000-0000-4000-8000-{dataset:012x}").into();
    doc["snapshotId"] = format!("00000000-0000-4000-8000-{snapshot:012x}").into();
    doc["exportedAtMs"] = time.into();
    let mut raw = serde_json::to_vec(&doc).unwrap();
    raw.resize(length.max(raw.len()), b' ');
    raw
}
#[test]
fn spends_exact_read_budget_and_continues_past_candidate_that_does_not_fit() {
    let f = Fixture::new();
    let mut reader = DetachedInventory::bind(&f.settings).unwrap();
    const MIB: usize = 1 << 20;
    for (i, size) in [8, 8, 8, 7, 2, 1].into_iter().enumerate() {
        f.put(
            &format!("{i}.json"),
            &export(i as u8, i as u8, 1_700_001_000_000, size * MIB),
        );
    }
    let now = Instant::now();
    assert!(reader.refresh(&f.settings, now).scanning);
    let bounded = reader.refresh(&f.settings, now + Duration::from_secs(2));
    assert!(bounded.scanning);
    assert_eq!(bounded.error_code, None);
    assert_eq!(
        bounded
            .pending
            .iter()
            .map(|s| &s.dataset_id[35..])
            .collect::<Vec<_>>(),
        ["0", "1", "2", "3", "5"]
    );
    let finished = reader.refresh(&f.settings, now + Duration::from_secs(4));
    assert!(!finished.scanning);
    assert_eq!(finished.pending.len(), 6);
}
#[test]
fn changed_batch_member_and_late_arrival_do_not_block_initial_choice() {
    let mut f = Fixture::new();
    f.settings.selected_dataset_id = Some("00000000-0000-4000-8000-000000000001".into());
    let mut reader = DetachedInventory::bind(&f.settings).unwrap();
    let now = Instant::now();
    let poll = |s| now + Duration::from_secs(s);
    f.put("a.json", &export(1, 1, 1_700_001_000_000, 0));
    f.put("b.json", b"{");
    f.put("c.json", b"{");
    assert!(reader.refresh(&f.settings, poll(0)).scanning);
    f.put("b.json", b"{ ");
    fs::remove_file(f.root.join("in/c.json")).unwrap();
    f.put("late.json", &export(1, 2, 1_700_001_000_000, 0));
    let first = reader.refresh(&f.settings, poll(2));
    assert!(first.scanning);
    assert!(first.available.unwrap().snapshot_id.ends_with('1'));
    let next = reader.refresh(&f.settings, poll(4));
    assert_eq!(next.error_code.unwrap().code(), "invalid_data");
    assert!(next.available.unwrap().snapshot_id.ends_with('2'));
    f.put("b.json", &export(2, 3, 1_700_001_000_001, 0));
    assert!(reader.refresh(&f.settings, poll(6)).scanning);
    let recovered = reader.refresh(&f.settings, poll(8));
    assert_eq!(recovered.error_code, None);
    assert_eq!(recovered.pending.len(), 1);
    let mut changed = f.settings.clone();
    changed.input_directory = selected(&f.root.join("absent")).unwrap();
    let reloaded = reader.refresh(&changed, poll(10));
    assert!(reloaded.reload_required);
    assert_eq!(reloaded.available, recovered.available);
    assert!(!changed.input_directory.as_path().exists());
}
#[test]
fn bounds_candidates_entries_and_oversized_export() {
    let f = Fixture::new();
    let mut reader = DetachedInventory::bind(&f.settings).unwrap();
    let now = Instant::now();
    let poll = |s| now + Duration::from_secs(s);
    for i in 0..256 {
        f.put(&format!("{i}.JSON"), b"{");
    }
    fs::create_dir(f.root.join("in/sub.json")).unwrap();
    assert!(reader.refresh(&f.settings, poll(0)).scanning);
    f.put("extra.json", RAW);
    assert_eq!(
        reader.refresh(&f.settings, poll(2)).error_code,
        Some(LocalError::FileCountLimit)
    );
    fs::remove_file(f.root.join("in/extra.json")).unwrap();
    for i in 0..(4096 - 257) {
        f.put(&format!("{i}.txt"), b"");
    }
    assert_eq!(
        reader
            .refresh(&f.settings, poll(4))
            .error_code
            .unwrap()
            .code(),
        "invalid_data"
    );
    f.put("extra.txt", b"");
    assert_eq!(
        reader.refresh(&f.settings, poll(6)).error_code,
        Some(LocalError::FileCountLimit)
    );
    fs::remove_file(f.root.join("in/extra.txt")).unwrap();
    fs::File::create(f.root.join("in/99.JSON"))
        .unwrap()
        .set_len((8 << 20) + 1)
        .unwrap();
    reader.refresh(&f.settings, poll(8));
    assert_eq!(
        reader.refresh(&f.settings, poll(10)).error_code,
        Some(LocalError::FileLimit)
    );
}
#[test]
fn observes_stability_conflicts_and_endpoint_replacement() {
    let f = Fixture::new();
    let mut reader = DetachedInventory::bind(&f.settings).unwrap();
    f.put("first.JSON", RAW);
    let now = Instant::now();
    let poll = |s| now + Duration::from_secs(s);
    assert!(reader.refresh(&f.settings, poll(0)).scanning);
    assert!(reader.refresh(&f.settings, poll(1)).pending.is_empty());
    let ready = reader.refresh(&f.settings, poll(2));
    assert_eq!(ready.pending.len(), 1);
    assert!(ready.available.is_none());
    f.put("second.json", &[RAW, b"\n"].concat());
    assert_eq!(reader.refresh(&f.settings, poll(3)), ready);
    assert!(reader.refresh(&f.settings, poll(4)).scanning);
    let conflict = reader.refresh(&f.settings, poll(6));
    assert_eq!(conflict.error_code, Some(LocalError::SnapshotConflict));
    assert!(conflict.pending.is_empty());
    fs::remove_file(f.root.join("in/second.json")).unwrap();
    let mut configured = f.settings.clone();
    configured.selected_dataset_id = Some(ready.pending[0].dataset_id.clone());
    assert_eq!(
        reader.refresh(&configured, poll(8)).available.as_ref(),
        ready.pending.first()
    );
    fs::rename(f.root.join("out"), f.root.join("old")).unwrap();
    let missing = reader.refresh(&configured, poll(10));
    assert!(missing.reload_required && missing.available.is_none());
    assert!(!f.root.join("out").exists());
    fs::create_dir(f.root.join("out")).unwrap();
    assert_eq!(
        reader.refresh(&configured, poll(12)).error_code,
        Some(LocalError::ReloadRequired)
    );
}
#[test]
fn live_selection_holds_until_watch_changes_and_protects_retired_tokens() {
    let f = Fixture::new();
    let mut live = f.watcher();
    let now = Instant::now();
    let poll = |s| now + Duration::from_secs(s);
    f.put("a.json", &export(1, 1, 1_700_001_000_000, 0));
    assert!(live.refresh(poll(0)).scanning);
    assert_eq!(
        live.current(poll(2)).err(),
        Some(LocalError::SourceUnavailable)
    );
    let chosen = live.last_status().pending[0].dataset_id.clone();
    assert_eq!(
        live.select_dataset("invalid", poll(2)),
        Err(LocalError::DatasetUnavailable)
    );
    live.select_dataset(&chosen, poll(2)).unwrap();
    let held = live.current(poll(2)).unwrap();
    f.put("a.json", &export(1, 2, 1_700_001_000_001, 0));
    live.refresh(poll(4));
    let off = live.refresh(poll(6));
    assert!(off.available.unwrap().snapshot_id.ends_with('2'));
    assert!(std::sync::Arc::ptr_eq(&held, &off.active.unwrap()));
    let mut setting = load_settings(&f.config()).unwrap();
    setting.watch_enabled = true;
    save_settings(&f.config(), &setting).unwrap();
    assert!(live.current(poll(8)).unwrap().snapshot().ends_with('2'));
    for i in 3..=5 {
        f.put("a.json", &export(1, i, 1_700_001_000_000 + i as u64, 0));
        live.refresh(poll(i as u64 * 4));
        assert!(live.resolve(held.sha256(), poll(i as u64 * 4)).is_ok());
        live.refresh(poll(i as u64 * 4 + 2));
    }
    assert_eq!(
        live.resolve(held.sha256(), poll(22)).err(),
        Some(LocalError::SourceExpired)
    );
    assert!(held.snapshot().ends_with('1'));
    fs::remove_file(f.root.join("in/a.json")).unwrap();
    assert!(live.refresh(poll(24)).available.is_none());
    let before = fs::metadata(f.config().as_path())
        .unwrap()
        .modified()
        .unwrap();
    let bytes = fs::read(f.config().as_path()).unwrap();
    live.select_dataset(&chosen, poll(24)).unwrap();
    assert_eq!(fs::read(f.config().as_path()).unwrap(), bytes);
    assert_eq!(
        fs::metadata(f.config().as_path())
            .unwrap()
            .modified()
            .unwrap(),
        before
    );
    assert!(live.current(poll(24)).unwrap().snapshot().ends_with('5'));
}

#[test]
fn old_cursor_and_exact_draft_survive_refresh_and_journal_reopen() {
    use serde_json::{Value, json};
    let mut f = Fixture::new();
    let mut doc: Value = serde_json::from_slice(RAW).unwrap();
    let mut second = doc["savedWorkouts"][0].clone();
    second["id"] = json!("local:ffffffff-ffff-4fff-8fff-ffffffffffff");
    doc["savedWorkouts"]
        .as_array_mut()
        .unwrap()
        .push(second.clone());
    doc["coverage"]["savedWorkoutsIncluded"] = json!(2);
    f.settings.selected_dataset_id = Some(doc["datasetId"].as_str().unwrap().into());
    f.settings.watch_enabled = true;
    save_settings(&f.config(), &f.settings).unwrap();
    let raw = serde_json::to_vec(&doc).unwrap();
    f.put("source.json", &raw);
    let now = Instant::now();
    let poll = |s| now + Duration::from_secs(s);
    let mut watcher = f.watcher();
    watcher.refresh(poll(0));
    let first = watcher.current(poll(2)).unwrap();
    let cursor = first.page("savedWorkouts", &json!({"limit":1})).unwrap()["nextCursor"].clone();
    let draft: Value = serde_json::from_slice(include_bytes!(
        "../../../tools/local_connector/contracts/fixtures/proposal.json"
    ))
    .unwrap();
    let out = watcher.outbox();
    let j = Journal::new(&out, &first);
    let proposal = j
        .create("same", &draft["draft"], 1_700_001_000_000)
        .unwrap();
    let id = proposal["proposal"]["draftId"].as_str().unwrap();
    let original = j.read_ready(id).unwrap();
    doc["snapshotId"] = json!("ffffffff-ffff-4fff-8fff-ffffffffffff");
    f.put("source.json", &serde_json::to_vec(&doc).unwrap());
    watcher.refresh(poll(4));
    let latest = watcher.current(poll(6)).unwrap();
    assert_ne!(first.sha256(), latest.sha256());
    assert!(
        latest
            .page("savedWorkouts", &json!({"cursor":cursor}))
            .is_err()
    );
    let resolved = watcher.resolve(first.sha256(), poll(6)).unwrap();
    assert_eq!(
        resolved
            .page("savedWorkouts", &json!({"cursor":cursor}))
            .unwrap()["savedWorkouts"][0],
        second
    );
    let old = Journal::new(&out, &resolved);
    assert_eq!(old.create("same", &draft["draft"], 0).unwrap(), proposal);
    assert_eq!(old.get(id).unwrap(), proposal);
    assert_eq!(old.read_ready(id).unwrap(), original);
    drop(watcher);
    f.put("source.json", &raw);
    let mut reopened = f.watcher();
    reopened.refresh(poll(8));
    let original_source = reopened.current(poll(10)).unwrap();
    let fixed = reopened.outbox();
    let reopened_journal = Journal::new(&fixed, &original_source);
    assert_eq!(
        reopened_journal.create("same", &draft["draft"], 0).unwrap(),
        proposal
    );
    assert_eq!(reopened_journal.read_ready(id).unwrap(), original);
}

#[test]
fn conflict_with_removed_export_and_invalid_replacements_preserve_loaded_data() {
    let mut f = Fixture::new();
    let raw = export(1, 1, 1_700_001_000_000, 0);
    f.settings.selected_dataset_id = Some("00000000-0000-4000-8000-000000000001".into());
    f.settings.watch_enabled = true;
    save_settings(&f.config(), &f.settings).unwrap();
    let mut w = f.watcher();
    let now = Instant::now();
    let poll = |s| now + Duration::from_secs(s);
    f.put("a.json", &raw);
    w.refresh(poll(0));
    let original = w.current(poll(2)).unwrap();
    for (i, (data, code)) in [
        (b"{".as_slice(), "invalid_data"),
        (&[raw.as_slice(), b" "].concat(), "snapshot_conflict"),
    ]
    .into_iter()
    .enumerate()
    {
        f.put("a.json", data);
        w.refresh(poll(4 + i as u64 * 4));
        let status = w.refresh(poll(6 + i as u64 * 4));
        assert_eq!(status.error_code.unwrap().code(), code);
        assert!(status.available.is_none());
        assert!(std::sync::Arc::ptr_eq(&original, &status.active.unwrap()));
    }
    f.put("a.json", &export(1, 2, 1_700_000_900_000, 0));
    w.refresh(poll(12));
    let older = w.refresh(poll(14));
    assert!(older.available.unwrap().snapshot_id.ends_with('2'));
    assert!(std::sync::Arc::ptr_eq(&original, &older.active.unwrap()));
    fs::hard_link(f.root.join("in/a.json"), f.root.join("in/link.json")).unwrap();
    assert!(w.refresh(poll(16)).error_code.is_some());
    assert!(std::sync::Arc::ptr_eq(
        &original,
        &w.current(poll(16)).unwrap()
    ));
}

#[test]
fn guarded_selection_preserves_new_preferences_and_rejects_replaced_endpoints() {
    let f = Fixture::new();
    let mut w = f.watcher();
    let now = Instant::now();
    let poll = |s| now + Duration::from_secs(s);
    for i in 1..=2 {
        f.put(&format!("{i}.json"), &export(i, i, 1_700_001_000_000, 0));
    }
    w.refresh(poll(0));
    let status = w.refresh(poll(2));
    let a = &status.pending[0].dataset_id;
    let b = &status.pending[1].dataset_id;
    let mut other = f.settings.clone();
    other.watch_enabled = true;
    save_settings(&f.config(), &other).unwrap();
    w.select_dataset(a, poll(2)).unwrap();
    assert!(load_settings(&f.config()).unwrap().watch_enabled);
    let first = w.current(poll(2)).unwrap();
    fs::rename(f.root.join("in"), f.root.join("retired")).unwrap();
    let before = fs::read(f.config().as_path()).unwrap();
    assert_eq!(
        w.select_dataset(a, poll(2)),
        Err(LocalError::ReloadRequired)
    );
    assert_eq!(
        w.select_dataset(b, poll(2)),
        Err(LocalError::ReloadRequired)
    );
    assert!(!f.root.join("in").exists());
    fs::create_dir(f.root.join("in")).unwrap();
    assert_eq!(
        w.select_dataset(a, poll(2)),
        Err(LocalError::ReloadRequired)
    );
    assert_eq!(
        w.select_dataset(b, poll(2)),
        Err(LocalError::ReloadRequired)
    );
    assert_eq!(fs::read(f.config().as_path()).unwrap(), before);
    let unavailable = w.refresh(poll(4));
    assert!(unavailable.reload_required);
    assert!(std::sync::Arc::ptr_eq(&unavailable.active.unwrap(), &first));
    assert_eq!(
        w.resolve(first.sha256(), poll(4)).unwrap().sha256(),
        first.sha256()
    );
}

#[test]
fn readoption_moves_cached_source_to_newest() {
    let f = Fixture::new();
    let mut w = f.watcher();
    let now = Instant::now();
    let settled = now + Duration::from_secs(2);
    for i in 1..=5 {
        f.put(&format!("{i}.json"), &export(i, i, 1_700_001_000_000, 0));
    }
    w.refresh(now);
    let datasets = w.refresh(settled).pending;
    let mut adopted = Vec::new();
    for s in &datasets[..4] {
        w.select_dataset(&s.dataset_id, settled).unwrap();
        adopted.push(w.current(settled).unwrap());
    }
    w.select_dataset(&datasets[0].dataset_id, settled).unwrap();
    assert!(std::sync::Arc::ptr_eq(
        &w.current(settled).unwrap(),
        &adopted[0]
    ));
    w.select_dataset(&datasets[4].dataset_id, settled).unwrap();
    assert!(w.resolve(adopted[0].sha256(), settled).is_ok());
    assert_eq!(
        w.resolve(adopted[1].sha256(), settled).err(),
        Some(LocalError::SourceExpired)
    );
}

#[test]
fn cached_winner_reread_obeys_budget_and_fresh_winner_needs_no_reread() {
    let mut f = Fixture::new();
    let mut w = f.watcher();
    let now = Instant::now();
    let poll = |s| now + Duration::from_secs(s);
    f.put("a.json", &export(1, 1, 1_700_001_000_000, 0));
    w.refresh(poll(0));
    let id = w.refresh(poll(2)).pending[0].dataset_id.clone();
    for i in 2..=5 {
        f.put(
            &format!("{i}.json"),
            &export(i, i, 1_700_001_000_000, 8 << 20),
        );
    }
    w.refresh(poll(4));
    f.settings.selected_dataset_id = Some(id);
    save_settings(&f.config(), &f.settings).unwrap();
    let spent = w.refresh(poll(6));
    assert!(spent.available.is_some() && spent.scanning && spent.active.is_none());
    f.put("late.json", &export(6, 6, 1_700_001_000_001, 0));
    let resumed = w.refresh(poll(8));
    assert!(resumed.scanning && resumed.active.unwrap().snapshot().ends_with('1'));
    f.settings.selected_dataset_id = Some("00000000-0000-4000-8000-000000000005".into());
    save_settings(&f.config(), &f.settings).unwrap();
    let mut fresh = f.watcher();
    fresh.refresh(poll(10));
    // Four sorted 8 MiB files exhaust this tick. The 5th dataset is the last one read.
    // The remaining original small input would block a watch-off initial batch.
    let pending = fresh.refresh(poll(12));
    assert!(pending.active.is_none() && pending.scanning);
    let stable = fresh.refresh(poll(14));
    assert!(stable.active.unwrap().snapshot().ends_with('5'));
    f.settings.watch_enabled = true;
    save_settings(&f.config(), &f.settings).unwrap();
    let mut fresh = f.watcher();
    fresh.refresh(poll(16));
    let spent = fresh.refresh(poll(18));
    assert!(spent.scanning && spent.active.unwrap().snapshot().ends_with('5'));
}

#[test]
fn pinned_outbox_survives_config_change_and_writes_only_to_original() {
    let mut f = Fixture::new();
    let doc: serde_json::Value = serde_json::from_slice(RAW).unwrap();
    f.settings.selected_dataset_id = Some(doc["datasetId"].as_str().unwrap().into());
    save_settings(&f.config(), &f.settings).unwrap();
    f.put("a.json", RAW);
    let mut w = f.watcher();
    let now = Instant::now();
    let poll = |s| now + Duration::from_secs(s);
    w.refresh(poll(0));
    let source = w.current(poll(2)).unwrap();
    let pinned = w.outbox();
    let mut updated = f.settings.clone();
    updated.outbox_directory = selected(&f.root.join("new-out")).unwrap();
    save_settings(&f.config(), &updated).unwrap();
    assert!(w.refresh(poll(4)).reload_required);
    #[cfg(unix)]
    {
        fs::rename(f.root.join("out"), f.root.join("retired-out")).unwrap();
        fs::create_dir(f.root.join("out")).unwrap();
        assert!(w.refresh(poll(6)).reload_required);
    }
    let p: serde_json::Value = serde_json::from_slice(include_bytes!(
        "../../../tools/local_connector/contracts/fixtures/proposal.json"
    ))
    .unwrap();
    let journal = Journal::new(&pinned, &source);
    let created = journal
        .create("fixed", &p["draft"], 1_700_001_000_000)
        .unwrap();
    assert!(
        !journal
            .read_ready(created["proposal"]["draftId"].as_str().unwrap())
            .unwrap()
            .is_empty()
    );
    assert_eq!(fs::read_dir(f.root.join("new-out")).unwrap().count(), 0);
    #[cfg(unix)]
    assert_eq!(fs::read_dir(f.root.join("out")).unwrap().count(), 0);
}
#[cfg(unix)]
#[test]
fn unsafe_entry_does_not_take_a_candidate_slot() {
    use std::os::unix::fs::symlink;
    let f = Fixture::new();
    let mut reader = DetachedInventory::bind(&f.settings).unwrap();
    for i in 0..256 {
        f.put(&format!("{i}.json"), RAW);
    }
    symlink(f.root.join("in/0.json"), f.root.join("in/danger.json")).unwrap();
    let now = Instant::now();
    let initial = reader.refresh(&f.settings, now);
    assert_eq!(initial.error_code, Some(LocalError::RegularFileRequired));
    assert!(initial.scanning);
    let settled = reader.refresh(&f.settings, now + Duration::from_secs(2));
    assert_eq!(settled.error_code, Some(LocalError::RegularFileRequired));
    assert_eq!(settled.pending.len(), 1);
}
