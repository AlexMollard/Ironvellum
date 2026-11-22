use super::{
    LoadedSource, SESSION_BYTES, SessionRecord, endpoint_digest, rendezvous_name, session_id,
};
use crate::{
    DetachedInventory, EndpointBinding, Entries, Install, InventoryStatus, LocalError, LockMode,
    LocklessOpen, Name, PrivateFolder, RefreshStatus, Selected, Settings, endpoint_identities,
    load_settings, name, selected, storage::uuid_v4,
};
use serde_json::{Value, json};
use std::time::Instant;

const STALE_MS: u64 = 15_000;
const RECLAIM_BATCH: usize = 16;
const SCAN_LIMIT: usize = 4096;
fn session_directory(path: &Selected) -> Result<Selected, LocalError> {
    let parent = path.as_path().parent().ok_or(LocalError::InvalidPath)?;
    selected(&parent.join(rendezvous_name(path)?))
}

#[derive(Clone, Copy, Debug)]
pub enum Evidence {
    Initialized,
    ToolsListed,
    StatusChecked,
}

pub struct Heartbeat {
    folder: PrivateFolder,
    id: String,
    filename: Name,
    record: SessionRecord,
    last_written: Vec<u8>,
    cursor: Option<Entries>,
}
impl Heartbeat {
    pub fn start(
        path: &Selected,
        binding: &EndpointBinding,
        status: &RefreshStatus,
        wall_ms: u64,
    ) -> Result<Self, LocalError> {
        let endpoints = endpoint_digest(binding.paths(), binding.identities())?;
        let id = uuid_v4()?.replace('-', "");
        let filename = name(format!("{id}.json").as_ref())?;
        let folder = PrivateFolder::open(&session_directory(path)?, LocklessOpen::CreateLeaf)?;
        let mut hb = Self {
            folder,
            id,
            filename,
            record: SessionRecord {
                time_ms: wall_ms,
                endpoints,
                initialized: false,
                tools_listed: false,
                status_checked: false,
                loaded_source: None,
                reload_required: false,
            },
            last_written: Vec::new(),
            cursor: None,
        };
        hb.publish(status, wall_ms)?;
        hb.try_reclaim(wall_ms);
        Ok(hb)
    }
    pub fn id(&self) -> &str {
        &self.id
    }
    fn publish(&mut self, status: &RefreshStatus, wall_ms: u64) -> Result<(), LocalError> {
        self.record.time_ms = wall_ms;
        self.record.reload_required = status.reload_required;
        self.record.loaded_source = status
            .active
            .as_deref()
            .map(LoadedSource::from_snapshot)
            .transpose()?;
        let raw = self.record.encode()?;
        let guard = self.folder.serialized(LockMode::Create)?;
        guard.write(&self.filename, &raw, Install::Replace)?;
        self.last_written = raw;
        Ok(())
    }
    /// Call after the corresponding operation succeeds. A diagnostic write may be retried by tick.
    pub fn evidence(&mut self, event: Evidence, status: &RefreshStatus, wall_ms: u64) {
        match event {
            Evidence::Initialized => self.record.initialized = true,
            Evidence::ToolsListed => self.record.tools_listed = true,
            Evidence::StatusChecked => self.record.status_checked = true,
        }
        let _ = self.publish(status, wall_ms);
    }
    pub fn tick(&mut self, status: &RefreshStatus, wall_ms: u64) {
        let _ = self.publish(status, wall_ms);
        self.try_reclaim(wall_ms);
    }
    fn try_reclaim(&mut self, wall_ms: u64) {
        if self.reclaim(wall_ms).is_err() {
            self.cursor = None;
        }
    }
    fn reclaim(&mut self, wall_ms: u64) -> Result<(), LocalError> {
        if self.cursor.is_none() {
            self.cursor = Some(self.folder.dir().owned_entries()?);
        }
        let cursor = self.cursor.as_mut().expect("opened above");
        let guard = self.folder.serialized(LockMode::Create)?;
        for _ in 0..RECLAIM_BATCH {
            let Some(entry) = cursor.next() else {
                self.cursor = None;
                break;
            };
            let entry = entry?;
            let Some(id) = entry.name.to_str().and_then(session_id) else {
                continue;
            };
            if id == self.id {
                continue;
            }
            let filename = name(&entry.name)?;
            if guard
                .read(&filename, SESSION_BYTES)
                .and_then(|raw| SessionRecord::decode(&raw))
                .is_ok_and(|record| wall_ms.saturating_sub(record.time_ms) > STALE_MS)
            {
                let _ = guard.unlink(&filename);
            }
        }
        Ok(())
    }
    /// Consume after the scheduler stops. A changed or unreadable record remains untouched.
    pub fn close(mut self) {
        self.cursor = None;
        if let Ok(guard) = self.folder.serialized(LockMode::Create)
            && guard
                .read(&self.filename, SESSION_BYTES)
                .is_ok_and(|bytes| bytes == self.last_written)
        {
            let _ = guard.unlink(&self.filename);
        }
    }
}

#[derive(Debug, Clone)]
pub struct SessionRow {
    pub id: String,
    pub record: SessionRecord,
    pub reload_required: bool,
}
impl SessionRow {
    pub fn wire(&self) -> Value {
        let r = &self.record;
        json!({
            "sessionId": self.id,
            "lastSeenMs": r.time_ms,
            "toolsListed": r.tools_listed,
            "statusChecked": r.status_checked,
            "loadedSource": r.loaded_source.as_ref().map(LoadedSource::value),
            "initialized": r.initialized,
            "clientVerified": r.verified(),
            "reloadRequired": self.reload_required
        })
    }
}
#[derive(Debug, Clone)]
pub struct StatusReport {
    pub settings: Settings,
    pub inventory: InventoryStatus,
    pub sessions: Vec<SessionRow>,
    pub session_problem: Option<&'static str>,
}
impl StatusReport {
    pub fn state(&self) -> &'static str {
        if self.sessions.iter().any(|s| s.reload_required) {
            "client_reload_required"
        } else if !self.sessions.is_empty() {
            "running"
        } else if self.inventory.available.is_some() || !self.inventory.pending.is_empty() {
            "configured"
        } else {
            "awaiting_training"
        }
    }
    pub fn wire(&self) -> Result<Value, LocalError> {
        let state = self.state();
        let inventory = &self.inventory;
        let mut actions = Vec::new();
        if state == "client_reload_required" {
            actions.push("reload_client");
        }
        if self.session_problem.is_some() {
            actions.push("check_session_state");
        }
        if inventory.error_code.is_some() {
            actions.push("check_folders_or_export");
        }
        if inventory.available.is_none() {
            actions.push(if inventory.pending.is_empty() {
                "transfer_training"
            } else {
                "select_source"
            });
        }
        if self.sessions.is_empty() {
            actions.push("open_client");
        }
        let available = inventory
            .available
            .as_ref()
            .map(LoadedSource::from_summary)
            .transpose()?;
        let pending = inventory
            .pending
            .iter()
            .map(|s| LoadedSource::from_summary(s).map(|s| s.0))
            .collect::<Result<Vec<_>, _>>()?;
        Ok(json!({
            "schemaVersion": 1,
            "status": state,
            "code": inventory.error_code.map(LocalError::code).or(self.session_problem).unwrap_or("ok"),
            "sessionProblem": self.session_problem,
            "configured": true,
            "running": !self.sessions.is_empty(),
            "clientVerified": self.sessions.iter().any(|s| s.record.verified()),
            "sessions": self.sessions.iter().map(SessionRow::wire).collect::<Vec<_>>(),
            "selectedDatasetId": self.settings.selected_dataset_id,
            "availableSource": available.as_ref().map(LoadedSource::value),
            "pending": pending,
            "scanning": inventory.scanning,
            "nextActions": actions
        }))
    }
}
pub struct StatusReader {
    path: Selected,
    inventory: Option<DetachedInventory>,
}
impl StatusReader {
    pub fn new(path: Selected) -> Self {
        Self {
            path,
            inventory: None,
        }
    }
    pub fn read(&mut self, now: Instant, wall_ms: u64) -> Result<StatusReport, LocalError> {
        let settings = load_settings(&self.path)?;
        if self
            .inventory
            .as_ref()
            .is_some_and(|i| !i.binding().matches(&settings))
        {
            self.inventory = None;
        }
        let inventory = self
            .refresh(&settings, now)
            .unwrap_or_else(|e| InventoryStatus {
                error_code: Some(e),
                ..Default::default()
            });
        if inventory.reload_required {
            self.inventory = None;
        }
        let (sessions, session_problem) = self.sessions(&settings, wall_ms)?;
        Ok(StatusReport {
            settings,
            inventory,
            sessions,
            session_problem,
        })
    }
    fn refresh(
        &mut self,
        settings: &Settings,
        now: Instant,
    ) -> Result<InventoryStatus, LocalError> {
        if self.inventory.is_none() {
            self.inventory = Some(DetachedInventory::bind(settings)?);
        }
        Ok(self
            .inventory
            .as_mut()
            .expect("bound above")
            .refresh(settings, now))
    }
    pub(crate) fn sessions(
        &self,
        settings: &Settings,
        wall_ms: u64,
    ) -> Result<(Vec<SessionRow>, Option<&'static str>), LocalError> {
        let folder =
            match PrivateFolder::open(&session_directory(&self.path)?, LocklessOpen::Existing) {
                Ok(folder) => folder,
                Err(LocalError::Missing) => return Ok((Vec::new(), None)),
                Err(e) => return Err(e),
            };
        let endpoints = endpoint_identities(settings.endpoints(), LocklessOpen::Existing)
            .and_then(|ids| endpoint_digest(settings.endpoints(), ids))
            .ok();
        let mut rows = Vec::new();
        let mut problem = None;
        for (i, entry) in folder.dir().entries()?.enumerate() {
            if i == SCAN_LIMIT {
                problem = Some("session_scan_limit");
                break;
            }
            let entry = match entry {
                Err(LocalError::Missing) => continue,
                other => other?,
            };
            let Some(id) = entry.name.to_str().and_then(session_id) else {
                continue;
            };
            let filename = name(&entry.name)?;
            match folder
                .dir()
                .read(&filename, SESSION_BYTES)
                .and_then(|raw| SessionRecord::decode(&raw))
            {
                Ok(record)
                    if wall_ms
                        .checked_sub(record.time_ms)
                        .is_some_and(|age| age <= STALE_MS) =>
                {
                    let reload_required =
                        record.reload_required || endpoints.as_deref() != Some(&record.endpoints);
                    rows.push(SessionRow {
                        id: id.to_owned(),
                        record,
                        reload_required,
                    });
                }
                Ok(_) | Err(LocalError::Missing) => (),
                Err(_) => problem = Some("invalid_session"),
            }
        }
        rows.sort_unstable_by(|a, b| a.id.cmp(&b.id));
        Ok((rows, problem))
    }
}
