use crate::{
    ArchiveItem, Directory, EndpointBinding, Identity, InventoryStatus, LocalError, LocklessOpen,
    OpenDir, PrivateFolder, ReadyArchive, Selected, Settings, load_settings, name,
    settings::update_existing_settings,
    status::{SessionRow, StatusReader},
    update_bound_settings, valid_dataset_id,
    watched_source::InputInventory,
};
use std::{path::PathBuf, time::Instant};

type Result<T> = std::result::Result<T, LocalError>;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Lifecycle {
    Unconfigured,
    AwaitingTraining,
    Configured,
    Running,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ReadyDraft {
    pub draft_id: String,
    pub display_name: String,
    pub display_filename: String,
    pub filename: String,
    pub dataset_id: String,
    pub snapshot_id: String,
    pub source_sha256: String,
    outbox: Selected,
    outbox_identity: Identity,
    publication_sha256: [u8; 32],
}

#[derive(Clone, Debug, Default)]
pub struct DesktopState {
    pub settings: Option<Settings>,
    pub settings_stale: bool,
    pub settings_problem: Option<LocalError>,
    pub source: Option<InventoryStatus>,
    pub source_stale: bool,
    pub source_problem: Option<LocalError>,
    pub sessions: Vec<SessionRow>,
    pub sessions_stale: bool,
    pub session_problem: Option<&'static str>,
    pub drafts: Vec<ReadyDraft>,
    pub drafts_stale: bool,
    pub archive_problem: Option<LocalError>,
}

impl DesktopState {
    pub fn lifecycle(&self) -> Lifecycle {
        if self.settings.is_none() {
            Lifecycle::Unconfigured
        } else if !self.sessions_stale && !self.sessions.is_empty() {
            Lifecycle::Running
        } else if !self.source_stale
            && self
                .source
                .as_ref()
                .is_some_and(|source| source.available.is_some() || !source.pending.is_empty())
        {
            Lifecycle::Configured
        } else {
            Lifecycle::AwaitingTraining
        }
    }

    pub fn verified_count(&self) -> usize {
        if self.sessions_stale {
            0
        } else {
            self.sessions.iter().filter(|s| s.record.verified()).count()
        }
    }

    pub fn reload_required(&self) -> bool {
        (!self.source_stale
            && self
                .source
                .as_ref()
                .is_some_and(|source| source.reload_required))
            || (!self.sessions_stale && self.sessions.iter().any(|s| s.reload_required))
    }
}

pub struct DesktopObserver {
    path: Selected,
    inventory: InputInventory,
    sessions: StatusReader,
    selection_binding: Option<EndpointBinding>,
    last: DesktopState,
}

impl DesktopObserver {
    pub fn new(path: Selected) -> Self {
        Self {
            sessions: StatusReader::new(path.clone()),
            path,
            inventory: InputInventory::default(),
            selection_binding: None,
            last: DesktopState::default(),
        }
    }

    pub fn read(&mut self, now: Instant, wall_ms: u64) -> DesktopState {
        self.selection_binding = None;
        let settings = match load_settings(&self.path) {
            Ok(settings) => settings,
            Err(LocalError::Missing) => {
                self.last = DesktopState::default();
                self.inventory = InputInventory::default();
                return self.last.clone();
            }
            Err(problem) => {
                self.last.settings_problem = Some(problem);
                self.last.settings_stale = self.last.settings.is_some();
                self.last.source_stale = self.last.source.is_some();
                self.last.sessions_stale = !self.last.sessions.is_empty();
                self.last.drafts_stale = !self.last.drafts.is_empty();
                return self.last.clone();
            }
        };

        if let Some(previous) = &self.last.settings {
            if previous.input_directory != settings.input_directory {
                self.last.source = None;
            }
            if previous.endpoints() != settings.endpoints() {
                self.last.sessions.clear();
            }
            if previous.outbox_directory != settings.outbox_directory {
                self.last.drafts.clear();
            }
        }

        self.last.settings_problem = None;
        self.last.settings_stale = false;
        match self.inventory.read(&settings, now) {
            Ok((status, identity)) => {
                self.selection_binding = EndpointBinding::bind(&settings)
                    .ok()
                    .filter(|binding| binding.identities()[0] == identity);
                self.last.source_problem = status.error_code;
                self.last.source = Some(status);
                self.last.source_stale = false;
            }
            Err(error) => {
                self.last.source_problem = Some(error);
                self.last.source_stale = self.last.source.is_some();
            }
        }
        match self.sessions.sessions(&settings, wall_ms) {
            Ok((sessions, problem)) => {
                self.last.sessions = sessions;
                self.last.session_problem = problem;
                self.last.sessions_stale = false;
            }
            Err(error) => {
                self.last.session_problem = Some(error.code());
                self.last.sessions_stale = !self.last.sessions.is_empty();
            }
        }
        match read_archive(&settings.outbox_directory) {
            Ok(drafts) => {
                self.last.drafts = drafts;
                self.last.archive_problem = None;
                self.last.drafts_stale = false;
            }
            Err(error) => {
                self.last.archive_problem = Some(error);
                self.last.drafts_stale = !self.last.drafts.is_empty();
            }
        }
        self.last.settings = Some(settings);
        self.last.clone()
    }

    /// Select from the latest successful inventory. The saved choice is a no-op
    /// even when its export is gone.
    pub fn select(&mut self, dataset: &str) -> Result<()> {
        if !valid_dataset_id(dataset) {
            return Err(LocalError::DatasetUnavailable);
        }
        if load_settings(&self.path)?.selected_dataset_id.as_deref() == Some(dataset) {
            return Ok(());
        }
        let binding = self
            .selection_binding
            .as_ref()
            .ok_or(LocalError::DatasetUnavailable)?;
        update_bound_settings(
            &self.path,
            binding.paths(),
            binding.identities(),
            |mut latest| {
                let input = Directory::open(&latest.input_directory, OpenDir::InputExisting)
                    .map_err(|_| LocalError::ReloadRequired)?;
                if input.identity() != binding.identities()[0] {
                    return Err(LocalError::ReloadRequired);
                }
                if !self.inventory.candidate_is_present(&input, dataset) {
                    return Err(LocalError::DatasetUnavailable);
                }
                latest.selected_dataset_id = Some(dataset.to_owned());
                Ok(latest)
            },
        )?;
        self.selection_binding = None;
        Ok(())
    }

    /// Update the configured folders and watch preference from staged edits.
    pub fn save_existing(&self, edit: &FolderEdit) -> Result<()> {
        let input = edit.input.as_ref().ok_or(LocalError::MissingFolders)?;
        let outbox = edit.outbox.as_ref().ok_or(LocalError::MissingFolders)?;
        update_existing_settings(&self.path, |mut latest| {
            latest.input_directory = input.clone();
            latest.outbox_directory = outbox.clone();
            latest.watch_enabled = edit.watch_enabled;
            Ok(latest)
        })?;
        Ok(())
    }

    /// Always reread persisted truth, including after canceled actions and sync failures.
    pub fn complete(
        &mut self,
        editor: &mut Editor,
        action: SaveAction,
        result: Result<()>,
        now: Instant,
        wall_ms: u64,
    ) -> DesktopState {
        let state = self.read(now, wall_ms);
        editor.complete(action, result);
        state
    }
}

fn read_archive(outbox: &Selected) -> Result<Vec<ReadyDraft>> {
    let folder = PrivateFolder::open(outbox, LocklessOpen::Existing)?;
    Ok(ReadyArchive::new(&folder)
        .list_ready()?
        .into_iter()
        .map(|item| ReadyDraft::observed(item, outbox, folder.dir().identity()))
        .collect())
}

impl ReadyDraft {
    fn observed(item: ArchiveItem, outbox: &Selected, outbox_identity: Identity) -> Self {
        Self {
            draft_id: item.draft_id,
            display_name: display_literal(&item.name),
            display_filename: display_literal(&item.filename),
            filename: item.filename,
            dataset_id: item.dataset_id,
            snapshot_id: item.snapshot_id,
            source_sha256: item.source_sha256,
            outbox: outbox.clone(),
            outbox_identity,
            publication_sha256: item.publication_sha256,
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FolderEdit {
    pub input: Option<Selected>,
    pub outbox: Option<Selected>,
    pub watch_enabled: bool,
}

/// A single editor owns its staged edit and the result of its latest dispatch.
#[derive(Default)]
pub struct Editor {
    draft: Option<FolderEdit>,
    active: Option<std::sync::Arc<()>>,
    outcome: Option<Result<()>>,
}

pub struct SaveAction {
    generation: std::sync::Arc<()>,
    pub edit: FolderEdit,
}

impl Editor {
    pub fn draft(&self) -> Option<&FolderEdit> {
        self.draft.as_ref()
    }

    pub fn outcome(&self) -> Option<Result<()>> {
        self.outcome
    }

    pub fn stage(&mut self, edit: FolderEdit) {
        self.cancel();
        self.draft = Some(edit);
    }

    pub fn cancel(&mut self) {
        *self = Self::default();
    }

    pub fn dispatch(&mut self) -> Option<SaveAction> {
        if self.active.is_some() {
            return None;
        }
        let edit = self.draft.clone()?;
        let generation = std::sync::Arc::new(());
        self.active = Some(generation.clone());
        self.outcome = None;
        Some(SaveAction { generation, edit })
    }

    /// Accept a serialized worker result on the thread which owns the editor.
    pub fn complete(&mut self, action: SaveAction, result: Result<()>) {
        if self
            .active
            .as_ref()
            .is_some_and(|id| std::sync::Arc::ptr_eq(id, &action.generation))
        {
            self.active = None;
            self.outcome = Some(result);
            if result.is_ok() {
                self.draft = None;
            }
        }
    }
}

/// Replace Unicode general categories Cc and Cf for display. Rust chars
/// are Unicode scalar values, so category Cs cannot occur.
pub fn display_literal(value: &str) -> String {
    value
        .chars()
        .map(|c| {
            if c.is_control()
                || matches!(c,
                    '\u{00ad}' | '\u{0600}'..='\u{0605}' | '\u{061c}' | '\u{06dd}' | '\u{070f}'
                    | '\u{0890}'..='\u{0891}' | '\u{08e2}' | '\u{180e}' | '\u{200b}'..='\u{200f}'
                    | '\u{202a}'..='\u{202e}' | '\u{2060}'..='\u{2064}' | '\u{2066}'..='\u{206f}'
                    | '\u{feff}' | '\u{fff9}'..='\u{fffb}' | '\u{110bd}' | '\u{110cd}'
                    | '\u{13430}'..='\u{1343f}' | '\u{1bca0}'..='\u{1bca3}'
                    | '\u{1d173}'..='\u{1d17a}' | '\u{e0001}' | '\u{e0020}'..='\u{e007f}'
                )
            {
                '\u{fffd}'
            } else {
                c
            }
        })
        .collect()
}

pub fn resolve_folder(folder: &Selected) -> Result<PathBuf> {
    Directory::open(folder, OpenDir::InputExisting)?;
    Ok(folder.as_path().to_owned())
}

pub fn resolve_ready_draft(outbox: &Selected, filename: &str) -> Result<PathBuf> {
    resolve_archive_file(outbox, filename, |folder| {
        Ok(ReadyArchive::new(folder)
            .list_ready()?
            .iter()
            .any(|row| row.filename == filename))
    })
}

/// Reveal only the exact ready publication represented by the observed row.
pub fn resolve_observed_draft(observed: &ReadyDraft) -> Result<PathBuf> {
    resolve_archive_file(&observed.outbox, &observed.filename, |folder| {
        let identity = folder.dir().identity();
        if identity != observed.outbox_identity {
            return Err(LocalError::SourceChanged);
        }
        let current = ReadyArchive::new(folder)
            .list_ready()?
            .into_iter()
            .find(|item| item.filename == observed.filename)
            .ok_or(LocalError::NotFound)?;
        if ReadyDraft::observed(current, &observed.outbox, identity) != *observed {
            return Err(LocalError::SourceChanged);
        }
        Ok(true)
    })
}

fn resolve_archive_file(
    outbox: &Selected,
    filename: &str,
    is_ready: impl FnOnce(&PrivateFolder) -> Result<bool>,
) -> Result<PathBuf> {
    let file = name(filename.as_ref())?;
    let folder = PrivateFolder::open(outbox, LocklessOpen::Existing)?;
    let before = folder
        .dir()
        .file_stamp(&file)
        .map_err(|error| match error {
            LocalError::Missing => LocalError::NotFound,
            other => other,
        })?;
    if !is_ready(&folder)? {
        return Err(LocalError::NotFound);
    }
    if folder.dir().file_stamp(&file)? != before {
        return Err(LocalError::SourceChanged);
    }
    Ok(outbox.as_path().join(file.as_os_str()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{Journal, selected};
    use ironvellum_contracts::snapshot::Snapshot;
    use std::fs;

    #[test]
    fn refuses_archive_file_replaced_after_validation() {
        let root = std::env::temp_dir();
        #[cfg(unix)]
        let root = root.canonicalize().unwrap();
        let root = root.join(format!("iv-archive-replacement-{}", std::process::id()));
        fs::create_dir(&root).unwrap();
        let outbox = selected(&root.join("out")).unwrap();
        let folder = PrivateFolder::open(&outbox, LocklessOpen::CreateLeaf).unwrap();
        let snapshot = Snapshot::from_bytes(include_bytes!(
            "../../../tools/local_connector/contracts/fixtures/context.json"
        ))
        .unwrap();
        let proposal: serde_json::Value = serde_json::from_slice(include_bytes!(
            "../../../tools/local_connector/contracts/fixtures/proposal.json"
        ))
        .unwrap();
        Journal::new(&folder, &snapshot)
            .create("ready", &proposal["draft"], 1_700_001_000_000)
            .unwrap();
        let filename = ReadyArchive::new(&folder)
            .list_ready()
            .unwrap()
            .remove(0)
            .filename;
        drop(folder);
        let path = outbox.as_path().join(&filename);
        let replacement = outbox.as_path().join("replacement");
        fs::write(&replacement, b"replacement bytes").unwrap();
        let result = resolve_archive_file(&outbox, &filename, |folder| {
            let ready = ReadyArchive::new(folder).list_ready()?;
            assert_eq!(ready[0].filename, filename);
            fs::rename(&replacement, &path).unwrap();
            Ok(true)
        });
        assert_eq!(fs::read(&path).unwrap(), b"replacement bytes");
        fs::remove_dir_all(&root).unwrap();
        assert_eq!(result, Err(LocalError::SourceChanged));
    }
}
