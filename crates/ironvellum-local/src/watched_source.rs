use crate::{
    Directory, Identity, LocalError, LocklessOpen, PrivateFolder, Selected, Settings, Stamp,
    candidate_names, load_settings, name, open_endpoints, update_bound_settings, valid_dataset_id,
};
use ironvellum_contracts::{Refusal, snapshot::Snapshot, wire::MAX_CONTEXT};
use std::{
    collections::{BTreeMap, BTreeSet, VecDeque},
    ffi::OsString,
    sync::Arc,
    time::{Duration, Instant},
};

pub(crate) const INTERVAL: Duration = Duration::from_secs(2);
const SCAN_BYTES: u64 = 32 << 20;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EndpointBinding {
    paths: [Selected; 3],
    identities: [Identity; 3],
}
impl EndpointBinding {
    pub fn bind(settings: &Settings) -> Result<Self, LocalError> {
        let dirs = open_endpoints(settings.endpoints(), LocklessOpen::Existing)?;
        Ok(Self {
            paths: settings.endpoints().map(Clone::clone),
            identities: dirs.each_ref().map(Directory::identity),
        })
    }
    pub fn paths(&self) -> [&Selected; 3] {
        self.paths.each_ref()
    }
    pub fn identities(&self) -> [Identity; 3] {
        self.identities
    }
    pub(crate) fn matches(&self, settings: &Settings) -> bool {
        self.paths()
            .into_iter()
            .zip(settings.endpoints())
            .all(|(a, b)| a.is_within(b) && b.is_within(a))
    }
    fn reopen(&self) -> Result<[Directory; 3], LocalError> {
        let dirs = open_endpoints(self.paths(), LocklessOpen::Existing)
            .map_err(|_| LocalError::ReloadRequired)?;
        if dirs.each_ref().map(Directory::identity) != self.identities {
            return Err(LocalError::ReloadRequired);
        }
        Ok(dirs)
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SourceSummary {
    pub dataset_id: String,
    pub snapshot_id: String,
    pub exported_at_ms: u64,
    pub sha256: String,
    pub workouts_included: u64,
    pub sets_included: u64,
    pub saved_workouts_included: u64,
}
impl SourceSummary {
    pub(crate) fn new(s: &Snapshot) -> Self {
        let m = s.metadata(0);
        let c = &m["coverage"];
        Self {
            dataset_id: s.dataset().into(),
            snapshot_id: s.snapshot().into(),
            sha256: s.sha256().into(),
            exported_at_ms: m["exportedAtMs"].as_u64().unwrap(),
            workouts_included: c["workoutsIncluded"].as_u64().unwrap(),
            sets_included: c["setsIncluded"].as_u64().unwrap(),
            saved_workouts_included: c["savedWorkoutsIncluded"].as_u64().unwrap(),
        }
    }
    fn order(&self) -> (u64, &str) {
        (self.exported_at_ms, &self.snapshot_id)
    }
}

#[derive(Default, Clone, Debug, Eq, PartialEq)]
pub struct InventoryStatus {
    pub available: Option<SourceSummary>,
    pub pending: Vec<SourceSummary>,
    pub scanning: bool,
    pub error_code: Option<LocalError>,
    pub reload_required: bool,
}
struct Observation {
    stamp: Stamp,
    since: Instant,
    validation: Option<Result<SourceSummary, LocalError>>,
}
#[derive(Default)]
struct Scanner {
    observations: BTreeMap<OsString, Observation>,
    initial_batch: Option<Vec<(OsString, Stamp)>>,
}
struct Tick {
    remaining: u64,
    scanning: bool,
    error: Option<LocalError>,
    best: Option<(SourceSummary, Snapshot)>,
}
impl Tick {
    fn read(
        &mut self,
        dir: &Directory,
        child: &crate::Name,
        stamp: &Stamp,
    ) -> Result<Option<Snapshot>, LocalError> {
        if stamp.length > MAX_CONTEXT as u64 {
            return Err(LocalError::FileLimit);
        }
        if stamp.length > self.remaining {
            self.scanning = true;
            return Ok(None);
        }
        self.remaining -= stamp.length;
        Ok(Some(Snapshot::from_bytes(
            &dir.stable_bytes(child, stamp)?,
        )?))
    }
}
impl Scanner {
    fn observe(
        &mut self,
        dir: &Directory,
        now: Instant,
        selected: Option<&str>,
    ) -> Result<Tick, LocalError> {
        let (names, error) = candidate_names(dir, 256, 4096)?;
        self.observations
            .retain(|n, _| names.binary_search(n).is_ok());
        let mut tick = Tick {
            remaining: SCAN_BYTES,
            scanning: false,
            error,
            best: None,
        };
        for n in names {
            if let Err(error) = self.observe_one(dir, &n, now, selected, &mut tick) {
                tick.error = Some(error);
                if matches!(
                    error,
                    LocalError::Contract(Refusal::InvalidData | Refusal::FileLimit)
                        | LocalError::FileLimit
                ) && let Some(obs) = self.observations.get_mut(&n)
                {
                    obs.validation = Some(Err(error));
                } else {
                    self.observations.remove(&n);
                }
            }
        }
        Ok(tick)
    }
    fn observe_one(
        &mut self,
        dir: &Directory,
        n: &OsString,
        now: Instant,
        selected: Option<&str>,
        tick: &mut Tick,
    ) -> Result<(), LocalError> {
        let child = name(n)?;
        let stamp = dir.file_stamp(&child)?;
        let obs = self
            .observations
            .entry(n.clone())
            .or_insert_with(|| Observation {
                stamp,
                since: now,
                validation: None,
            });
        if obs.stamp != stamp {
            *obs = Observation {
                stamp,
                since: now,
                validation: None,
            };
        }
        if now.duration_since(obs.since) < INTERVAL {
            tick.scanning = true;
            return Ok(());
        }
        if let Some(result) = &obs.validation {
            return result.as_ref().map(|_| ()).map_err(|e| *e);
        }
        let Some(snap) = tick.read(dir, &child, &stamp)? else {
            return Ok(());
        };
        let summary = SourceSummary::new(&snap);
        if Some(summary.dataset_id.as_str()) == selected
            && tick
                .best
                .as_ref()
                .is_none_or(|(s, _)| summary.order() > s.order())
        {
            tick.best = Some((summary.clone(), snap));
        }
        obs.validation = Some(Ok(summary));
        Ok(())
    }
    fn latest<'a>(
        &'a self,
        adopted: &'a VecDeque<Arc<Snapshot>>,
    ) -> (BTreeMap<&'a str, (&'a OsString, &'a SourceSummary)>, bool) {
        let mut ids = BTreeMap::new();
        let mut conflicts = BTreeSet::new();
        let valid = self
            .observations
            .iter()
            .filter_map(|(name, o)| Some((name, o.validation.as_ref()?.as_ref().ok()?)));
        for (id, hash) in adopted.iter().map(|s| (s.snapshot(), s.sha256())).chain(
            valid
                .clone()
                .map(|(_, s)| (s.snapshot_id.as_str(), s.sha256.as_str())),
        ) {
            if ids.insert(id, hash).is_some_and(|old| old != hash) {
                conflicts.insert(id);
            }
        }
        let mut latest = BTreeMap::<&str, (&OsString, &SourceSummary)>::new();
        for (name, s) in valid {
            if !conflicts.contains(s.snapshot_id.as_str())
                && latest
                    .get(s.dataset_id.as_str())
                    .is_none_or(|(_, old)| s.order() > old.order())
            {
                latest.insert(&s.dataset_id, (name, s));
            }
        }
        (latest, !conflicts.is_empty())
    }
    fn classify(
        &self,
        selected: Option<&str>,
        settled: bool,
        tick: &Tick,
        adopted: &VecDeque<Arc<Snapshot>>,
    ) -> (InventoryStatus, Option<(OsString, Stamp)>) {
        let (mut latest, conflicts) = self.latest(adopted);
        let winner = selected
            .and_then(|id| latest.remove(id))
            .filter(|_| settled);
        let version = winner.map(|(n, _)| (n.clone(), self.observations[n].stamp));
        let status = InventoryStatus {
            available: winner.map(|(_, s)| s.clone()),
            pending: latest.into_values().map(|(_, s)| s.clone()).collect(),
            scanning: tick.scanning,
            error_code: if conflicts {
                Some(LocalError::SnapshotConflict)
            } else {
                tick.error
            },
            reload_required: false,
        };
        (status, version)
    }
    fn settled(&mut self, watch: bool) -> bool {
        if watch {
            self.initial_batch = None;
            return true;
        }
        let batch = self.initial_batch.get_or_insert_with(|| {
            self.observations
                .iter()
                .map(|(n, o)| (n.clone(), o.stamp))
                .collect()
        });
        !batch.iter().any(|(n, s)| {
            self.observations
                .get(n)
                .is_some_and(|o| o.stamp == *s && o.validation.is_none())
        })
    }
}

pub struct DetachedInventory {
    binding: EndpointBinding,
    scanner: Scanner,
    selected: Option<String>,
    last_poll: Option<Instant>,
    status: InventoryStatus,
}
impl DetachedInventory {
    pub fn bind(settings: &Settings) -> Result<Self, LocalError> {
        Ok(Self {
            binding: EndpointBinding::bind(settings)?,
            scanner: Scanner::default(),
            selected: settings.selected_dataset_id.clone(),
            last_poll: None,
            status: InventoryStatus::default(),
        })
    }
    pub fn binding(&self) -> &EndpointBinding {
        &self.binding
    }
    /// The caller supplies the settings from its coherent, read-only poll.
    pub fn refresh(&mut self, settings: &Settings, now: Instant) -> InventoryStatus {
        if self
            .last_poll
            .is_some_and(|t| now.saturating_duration_since(t) < INTERVAL)
        {
            return self.status.clone();
        }
        self.last_poll = Some(now);
        let different = !self.binding.matches(settings);
        self.status = match self.read(settings, now) {
            Ok(mut status) => {
                status.reload_required = different;
                status
            }
            Err(e) => InventoryStatus {
                error_code: Some(e),
                reload_required: different || e == LocalError::ReloadRequired,
                ..Default::default()
            },
        };
        self.status.clone()
    }
    fn read(&mut self, settings: &Settings, now: Instant) -> Result<InventoryStatus, LocalError> {
        let dirs = self.binding.reopen()?;
        scan_inventory(
            &mut self.scanner,
            &mut self.selected,
            &dirs[0],
            settings,
            now,
        )
    }
}

fn scan_inventory(
    scanner: &mut Scanner,
    selected: &mut Option<String>,
    input: &Directory,
    settings: &Settings,
    now: Instant,
) -> Result<InventoryStatus, LocalError> {
    if *selected != settings.selected_dataset_id {
        scanner.initial_batch = None;
        selected.clone_from(&settings.selected_dataset_id);
    }
    let tick = scanner.observe(input, now, None)?;
    let settled = scanner.settled(settings.watch_enabled);
    if settled {
        scanner.initial_batch = None;
    }
    Ok(scanner
        .classify(selected.as_deref(), settled, &tick, &VecDeque::new())
        .0)
}

/// The desktop observes the input independently of the journal and live clients.
/// It checks its input identity on every read, including between stability ticks.
#[derive(Default)]
pub(crate) struct InputInventory {
    input: Option<(Selected, Identity)>,
    scanner: Scanner,
    selected: Option<String>,
    watch_enabled: bool,
    last_poll: Option<Instant>,
    status: InventoryStatus,
}
impl InputInventory {
    pub(crate) fn candidate_is_present(&self, input: &Directory, dataset: &str) -> bool {
        self.status
            .pending
            .iter()
            .filter(|s| s.dataset_id == dataset)
            .any(|winner| {
                self.scanner.observations.iter().any(|(file, observation)| {
                    observation
                        .validation
                        .as_ref()
                        .and_then(|value| value.as_ref().ok())
                        == Some(winner)
                        && crate::name(file).and_then(|n| input.file_stamp(&n))
                            == Ok(observation.stamp)
                })
            })
    }

    pub(crate) fn read(
        &mut self,
        settings: &Settings,
        now: Instant,
    ) -> Result<(InventoryStatus, Identity), LocalError> {
        let path = &settings.input_directory;
        let dir = Directory::open(path, crate::OpenDir::InputExisting).inspect_err(|_| {
            self.last_poll = None;
        })?;
        let identity = dir.identity();
        if self
            .input
            .as_ref()
            .is_none_or(|(p, id)| p != path || *id != identity)
        {
            *self = Self::default();
            self.input = Some((path.clone(), identity));
        }
        if self.selected != settings.selected_dataset_id
            || self.watch_enabled != settings.watch_enabled
            || self
                .last_poll
                .is_none_or(|last| now.saturating_duration_since(last) >= INTERVAL)
        {
            // Recovery must scan even if the same directory returns before the
            // previous poll would have fallen out of the cache.
            self.last_poll = None;
            self.status =
                scan_inventory(&mut self.scanner, &mut self.selected, &dir, settings, now)?;
            self.last_poll = Some(now);
            self.watch_enabled = settings.watch_enabled;
        }
        Ok((self.status.clone(), identity))
    }
}

#[derive(Default, Clone)]
pub struct RefreshStatus {
    pub active: Option<Arc<Snapshot>>,
    pub pending: Vec<SourceSummary>,
    pub scanning: bool,
    pub error_code: Option<LocalError>,
    pub reload_required: bool,
    pub available: Option<SourceSummary>,
}

pub struct WatchedSource {
    path: Selected,
    binding: EndpointBinding,
    outbox: Arc<PrivateFolder>,
    scanner: Scanner,
    selected: Option<String>,
    adopted: VecDeque<Arc<Snapshot>>,
    last_poll: Option<Instant>,
    status: RefreshStatus,
}

impl WatchedSource {
    pub fn bind(
        path: Selected,
        settings: Settings,
        binding: EndpointBinding,
    ) -> Result<Self, LocalError> {
        if !binding.matches(&settings) {
            return Err(LocalError::ReloadRequired);
        }
        binding.reopen()?;
        let outbox = PrivateFolder::open(&settings.outbox_directory, LocklessOpen::Existing)
            .map_err(|_| LocalError::ReloadRequired)?;
        if outbox.dir().identity() != binding.identities[1] {
            return Err(LocalError::ReloadRequired);
        }
        Ok(Self {
            path,
            binding,
            outbox: Arc::new(outbox),
            scanner: Scanner::default(),
            selected: settings.selected_dataset_id,
            adopted: VecDeque::new(),
            last_poll: None,
            status: RefreshStatus::default(),
        })
    }
    pub fn binding(&self) -> &EndpointBinding {
        &self.binding
    }
    /// Clone this handle before releasing the watcher lock for journal work.
    pub fn outbox(&self) -> Arc<PrivateFolder> {
        Arc::clone(&self.outbox)
    }
    pub fn last_status(&self) -> RefreshStatus {
        self.status.clone()
    }
    pub fn current(&mut self, now: Instant) -> Result<Arc<Snapshot>, LocalError> {
        self.refresh(now)
            .active
            .ok_or(LocalError::SourceUnavailable)
    }
    pub fn resolve(&mut self, token: &str, now: Instant) -> Result<Arc<Snapshot>, LocalError> {
        self.refresh(now);
        self.adopted
            .iter()
            .find(|s| s.sha256() == token)
            .cloned()
            .ok_or(LocalError::SourceExpired)
    }
    pub fn select_dataset(&mut self, id: &str, now: Instant) -> Result<(), LocalError> {
        if !valid_dataset_id(id) {
            return Err(LocalError::DatasetUnavailable);
        }
        let status = self.refresh(now);
        let eligible = status.pending.iter().any(|s| s.dataset_id == id)
            || status.active.as_ref().is_some_and(|s| s.dataset() == id);
        let persisted = load_settings(&self.path)?;
        if !self.binding.matches(&persisted) {
            return Err(LocalError::ReloadRequired);
        }
        if persisted.selected_dataset_id.as_deref() == Some(id) {
            self.binding.reopen()?;
            return Ok(());
        }
        if !eligible {
            return Err(LocalError::DatasetUnavailable);
        }
        update_bound_settings(
            &self.path,
            self.binding.paths(),
            self.binding.identities(),
            |mut latest| {
                latest.selected_dataset_id = Some(id.to_owned());
                Ok(latest)
            },
        )?;
        self.last_poll = None;
        self.refresh(now);
        Ok(())
    }
    pub fn refresh(&mut self, now: Instant) -> RefreshStatus {
        if self
            .last_poll
            .is_some_and(|t| now.saturating_duration_since(t) < INTERVAL)
        {
            return self.last_status();
        }
        self.last_poll = Some(now);
        match self.poll(now) {
            Ok(inventory) => {
                self.status.available = inventory.available;
                self.status.pending = inventory.pending;
                self.status.scanning = inventory.scanning;
                self.status.error_code = inventory.error_code;
            }
            Err(e) => {
                self.status.available = None;
                self.status.pending.clear();
                self.status.scanning = false;
                self.status.error_code = Some(e);
                self.status.reload_required |= e == LocalError::ReloadRequired;
            }
        }
        self.last_status()
    }
    fn poll(&mut self, now: Instant) -> Result<InventoryStatus, LocalError> {
        let dirs = self.binding.reopen()?;
        let config = load_settings(&self.path)?;
        self.status.reload_required = !self.binding.matches(&config);
        if self.selected != config.selected_dataset_id {
            self.scanner.initial_batch = None;
            self.status.active = None;
            self.selected = config.selected_dataset_id;
        }
        let mut tick = self
            .scanner
            .observe(&dirs[0], now, self.selected.as_deref())?;
        let settled = self
            .scanner
            .settled(config.watch_enabled || self.status.active.is_some());
        let (mut inventory, version) =
            self.scanner
                .classify(self.selected.as_deref(), settled, &tick, &self.adopted);
        if let (Some(s), Some((n, stamp))) = (&inventory.available, version)
            && self.status.active.as_ref().is_none_or(|active| {
                config.watch_enabled && s.order() > SourceSummary::new(active).order()
            })
        {
            self.adopt(&dirs[0], n, stamp, s, &mut tick)?;
            inventory.scanning = tick.scanning;
        }
        // A settled candidate may still need the next tick's byte budget.
        if settled && (inventory.available.is_none() || self.status.active.is_some()) {
            self.scanner.initial_batch = None;
        }
        Ok(inventory)
    }
    fn adopt(
        &mut self,
        dir: &Directory,
        n: OsString,
        stamp: Stamp,
        s: &SourceSummary,
        tick: &mut Tick,
    ) -> Result<(), LocalError> {
        let snap = if let Some(index) = self.adopted.iter().position(|a| a.sha256() == s.sha256) {
            self.adopted.remove(index).expect("position exists")
        } else if let Some((_, fresh)) = tick
            .best
            .take()
            .filter(|(candidate, _)| candidate.sha256 == s.sha256)
        {
            Arc::new(fresh)
        } else {
            match tick.read(dir, &name(&n)?, &stamp) {
                Ok(None) => return Ok(()),
                Ok(Some(fresh)) if fresh.sha256() == s.sha256 => Arc::new(fresh),
                result => {
                    self.scanner.observations.remove(&n);
                    return Err(result.err().unwrap_or(LocalError::SourceChanged));
                }
            }
        };
        self.status.active = Some(Arc::clone(&snap));
        self.adopted.push_back(snap);
        if self.adopted.len() > 4 {
            self.adopted.pop_front();
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{save_settings, selected};
    use std::fs;

    #[test]
    fn replacement_between_observation_and_adoption_refuses_and_reobserves() {
        let base = std::env::temp_dir();
        #[cfg(unix)]
        let base = base.canonicalize().unwrap();
        let root = base.join(format!("iv-adoption-reread-{}", std::process::id()));
        fs::create_dir(&root).unwrap();
        {
            let path = |n| selected(&root.join(n)).unwrap();
            let mut settings = Settings::new(path("input"), path("out"), path("state"));
            fs::create_dir(settings.input_directory.as_path()).unwrap();
            let raw =
                include_bytes!("../../../tools/local_connector/contracts/fixtures/context.json");
            let parsed = Snapshot::from_bytes(raw).unwrap();
            settings.selected_dataset_id = Some(parsed.dataset().into());
            save_settings(&path("state/settings.json"), &settings).unwrap();
            let binding = EndpointBinding::bind(&settings).unwrap();
            let dirs = binding.reopen().unwrap();
            let mut w =
                WatchedSource::bind(path("state/settings.json"), settings, binding).unwrap();
            let file = root.join("input/a.json");
            fs::write(&file, raw).unwrap();
            let now = Instant::now();
            w.refresh(now);
            let held = w.current(now + INTERVAL).unwrap();
            let mut replacement: serde_json::Value = serde_json::from_slice(raw).unwrap();
            replacement["snapshotId"] = "ffffffff-ffff-4fff-8fff-ffffffffffff".into();
            let replacement = serde_json::to_vec(&replacement).unwrap();
            fs::write(&file, &replacement).unwrap();
            w.refresh(now + INTERVAL * 2);
            let mut summary = w.refresh(now + INTERVAL * 3).available.unwrap();
            let n = OsString::from("a.json");
            let original = w.scanner.observations[&n].stamp;
            let mut tick = Tick {
                remaining: SCAN_BYTES,
                scanning: false,
                error: None,
                best: None,
            };
            // Replace the validated, unadopted candidate before its adoption read.
            fs::rename(&file, root.join("retired.json")).unwrap();
            fs::write(&file, &replacement).unwrap();
            assert_eq!(
                w.adopt(&dirs[0], n.clone(), original, &summary, &mut tick),
                Err(LocalError::SourceChanged)
            );
            assert!(!w.scanner.observations.contains_key(&n));
            assert!(Arc::ptr_eq(&held, &w.last_status().active.unwrap()));
            // Even an otherwise valid stamp cannot substitute bytes for the advertised digest.
            summary.sha256 = "0".repeat(64);
            let current = dirs[0].file_stamp(&name(&n).unwrap()).unwrap();
            assert_eq!(
                w.adopt(&dirs[0], n, current, &summary, &mut tick),
                Err(LocalError::SourceChanged)
            );
            assert!(Arc::ptr_eq(&held, &w.last_status().active.unwrap()));
        }
        fs::remove_dir_all(root).unwrap();
    }
}
