//! Explicit personal connector configuration and client installation.
use crate::settings::{SetupMode, configure_settings};
use crate::status::{StatusReader, StatusReport};
use crate::{
    Directory, EndpointBinding, Install, LocalError, LockMode, LocklessOpen, Name, OpenDir,
    PrivateFolder, Selected, Settings, WatchedSource, load_settings, name, selected,
    valid_dataset_id,
};
use ironvellum_contracts::{Value, encode, loads};
use serde_json::json;
use sha2::{Digest, Sha256};
use std::{
    path::PathBuf,
    time::{Instant, SystemTime, UNIX_EPOCH},
};

type Result<T> = std::result::Result<T, LocalError>;
const ENTRY: &str = "ironvellum-personal";
const LIMIT: usize = 4 << 20;

#[derive(Clone, Debug)]
pub enum Client {
    ConfigOnly,
    ClaudeDesktop {
        config: Option<Selected>,
        replace_existing: bool,
    },
}

pub struct SetupOptions {
    pub settings: Selected,
    pub input: Selected,
    pub outbox: Selected,
    pub state: Selected,
    pub dataset: Option<String>,
    pub watch: Option<bool>,
    pub client: Client,
    pub executable: Selected,
}

pub struct SetupResult {
    pub status: StatusReport,
    pub client: &'static str,
    pub client_config_changed: bool,
    pub backup_path: Option<PathBuf>,
    pub fragment: Value,
}

impl SetupResult {
    pub fn wire(&self) -> Result<Value> {
        let mut result = self.status.wire()?;
        result["client"] = json!(self.client);
        result["clientConfigChanged"] = json!(self.client_config_changed);
        result["backupPath"] = json!(self.backup_path);
        result["fragment"] = self.fragment.clone();
        let actions = result["nextActions"]
            .as_array_mut()
            .ok_or(LocalError::InvalidSettings)?;
        if self.client_config_changed && !actions.iter().any(|action| action == "reload_client") {
            actions.push(json!("reload_client"));
        }
        Ok(result)
    }
}

fn wall_ms() -> Result<u64> {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .ok()
        .and_then(|d| d.as_millis().try_into().ok())
        .ok_or(LocalError::Clock)
}

pub fn status(path: &Selected) -> Result<StatusReport> {
    let mut reader = StatusReader::new(path.clone());
    let first = reader.read(Instant::now(), wall_ms()?)?;
    if !first.inventory.scanning {
        return Ok(first);
    }
    std::thread::sleep(crate::watched_source::INTERVAL);
    reader.read(Instant::now(), wall_ms()?)
}

pub fn select_source(path: &Selected, dataset: &str) -> Result<StatusReport> {
    if !valid_dataset_id(dataset) {
        return Err(LocalError::DatasetUnavailable);
    }
    let settings = load_settings(path)?;
    let binding = EndpointBinding::bind(&settings)?;
    let mut source = WatchedSource::bind(path.clone(), settings, binding)?;
    let mut report = source.refresh(Instant::now());
    for _ in 0..256 {
        if !report.scanning || report.pending.iter().any(|s| s.dataset_id == dataset) {
            break;
        }
        std::thread::sleep(crate::watched_source::INTERVAL);
        report = source.refresh(Instant::now());
    }
    let eligible = report.pending.iter().any(|s| s.dataset_id == dataset)
        || report
            .active
            .as_ref()
            .is_some_and(|s| s.dataset() == dataset);
    if !eligible {
        return Err(LocalError::DatasetUnavailable);
    }
    source.select_dataset(dataset, Instant::now())?;
    status(path)
}

fn default_config() -> Result<Selected> {
    #[cfg(windows)]
    let base = std::env::var_os("APPDATA")
        .filter(|s| !s.is_empty())
        .map(PathBuf::from);
    #[cfg(target_os = "macos")]
    let base = std::env::home_dir().map(|p| p.join("Library/Application Support"));
    #[cfg(target_os = "linux")]
    let base = std::env::home_dir().map(|p| p.join(".config"));
    selected(
        &base
            .ok_or(LocalError::ClientConfigRequired)?
            .join("Claude/claude_desktop_config.json"),
    )
}

fn text(path: &Selected) -> Result<&str> {
    path.as_path().to_str().ok_or(LocalError::InvalidPath)
}

fn filename(path: &Selected) -> Result<&str> {
    path.as_path()
        .file_name()
        .and_then(|n| n.to_str())
        .ok_or(LocalError::InvalidPath)
}

fn reserved(s: &str) -> bool {
    if cfg!(windows) {
        s.to_ascii_lowercase().starts_with(".ironvellum-")
    } else {
        s.starts_with(".ironvellum-")
    }
}

fn outside(parent: &Directory, endpoints: &[&Selected]) -> Result<()> {
    for path in endpoints {
        match Directory::open(path, OpenDir::InputExisting) {
            Ok(dir) if crate::storage::below(parent, &dir) => {
                return Err(LocalError::OverlappingPaths);
            }
            Ok(_) | Err(LocalError::Missing) => (),
            Err(e) => return Err(e),
        }
    }
    Ok(())
}

fn preflight_directories(
    settings: &Selected,
    dirs: &Settings,
    setup_mode: SetupMode,
) -> Result<()> {
    let paths = dirs.endpoints();
    let mut existing = Vec::new();
    let mut parents = Vec::new();
    for (index, path) in paths.iter().enumerate() {
        let mode = if index == 0 {
            OpenDir::InputExisting
        } else {
            OpenDir::PrivateExisting
        };
        match Directory::open(path, mode) {
            Ok(directory) => existing.push(directory),
            Err(LocalError::Missing) => match Directory::open_parent(path) {
                Err(LocalError::Missing)
                    if setup_mode == SetupMode::FirstSave
                        && index == 2
                        && path.as_path().parent() == settings.as_path().parent() =>
                {
                    // The locked settings operation creates its private parent
                    // before creating the adjacent derived state directory.
                    let parent =
                        selected(settings.as_path().parent().ok_or(LocalError::InvalidPath)?)?;
                    parents.push(Directory::open_parent(&parent)?);
                }
                other => parents.push(other?),
            },
            Err(e) => return Err(e),
        }
    }
    for (i, dir) in existing.iter().enumerate() {
        if existing[..i]
            .iter()
            .any(|other| crate::storage::below(dir, other) || crate::storage::below(other, dir))
            || parents
                .iter()
                .any(|parent| crate::storage::below(parent, dir))
        {
            return Err(LocalError::OverlappingPaths);
        }
    }
    let parent = selected(settings.as_path().parent().ok_or(LocalError::InvalidPath)?)?;
    match PrivateFolder::open(&parent, LocklessOpen::Existing) {
        Ok(folder) => outside(folder.dir(), &paths[..2])?,
        Err(LocalError::Missing) => outside(&Directory::open_parent(&parent)?, &paths[..2])?,
        Err(e) => return Err(e),
    }
    match load_settings(settings) {
        Ok(_) | Err(LocalError::Missing) => Ok(()),
        Err(e) => Err(e),
    }
}

struct ClientFile {
    target: Selected,
    parent: Selected,
    folder: PrivateFolder,
    leaf: Name,
    original: Option<Vec<u8>>,
    updated: Option<Vec<u8>>,
}

fn optional(dir: &Directory, leaf: &Name) -> Result<Option<Vec<u8>>> {
    match dir.read(leaf, LIMIT) {
        Ok(raw) => Ok(Some(raw)),
        Err(LocalError::Missing) => Ok(None),
        Err(e) => Err(e),
    }
}

impl ClientFile {
    fn prepare(
        target: Selected,
        settings: &Selected,
        dirs: &Settings,
        entry: &Value,
        replace: bool,
    ) -> Result<Self> {
        let basename = filename(&target)?;
        if reserved(basename) {
            return Err(LocalError::InvalidClientConfig);
        }
        if target.is_within(settings)
            || settings.is_within(&target)
            || dirs
                .endpoints()
                .iter()
                .any(|p| target.is_within(p) || p.is_within(&target))
        {
            return Err(LocalError::OverlappingPaths);
        }
        let parent = selected(target.as_path().parent().ok_or(LocalError::InvalidPath)?)?;
        let folder = match PrivateFolder::open(&parent, LocklessOpen::Existing) {
            Ok(f) => f,
            Err(LocalError::Missing) => {
                outside(&Directory::open_parent(&parent)?, &dirs.endpoints())?;
                PrivateFolder::open(&parent, LocklessOpen::CreateLeaf)?
            }
            Err(e) => return Err(e),
        };
        outside(folder.dir(), &dirs.endpoints())?;
        let leaf = name(basename.as_ref())?;
        match Directory::open_parent(settings) {
            Ok(setting_parent) => {
                let other = filename(settings)?;
                if folder.dir().identity() == setting_parent.identity()
                    && (basename == other
                        || cfg!(windows) && basename.to_lowercase() == other.to_lowercase())
                {
                    return Err(LocalError::OverlappingPaths);
                }
                if let Ok(client_stamp) = folder.dir().file_stamp(&leaf)
                    && let Ok(settings_stamp) = setting_parent.file_stamp(&name(other.as_ref())?)
                    && client_stamp.identity == settings_stamp.identity
                {
                    return Err(LocalError::OverlappingPaths);
                }
            }
            Err(LocalError::Missing) => (),
            Err(e) => return Err(e),
        }
        let original = optional(folder.dir(), &leaf)?;
        let mut doc = match &original {
            None => json!({}),
            Some(raw) => loads(raw, LIMIT, 128).map_err(|_| LocalError::InvalidClientConfig)?,
        };
        let servers = doc
            .as_object_mut()
            .ok_or(LocalError::InvalidClientConfig)?
            .entry("mcpServers")
            .or_insert_with(|| json!({}))
            .as_object_mut()
            .ok_or(LocalError::InvalidClientConfig)?;
        let updated = if servers.get(ENTRY) == Some(entry) {
            None
        } else {
            if servers.contains_key(ENTRY) && !replace {
                return Err(LocalError::ClientEntryConflict);
            }
            if original.is_some() && !folder.dir().writable_attribute(&leaf)? {
                return Err(LocalError::ClientConfigReadOnly);
            }
            servers.insert(ENTRY.into(), entry.clone());
            let raw = encode(&doc)?;
            if raw.len() > LIMIT {
                return Err(LocalError::FileLimit);
            }
            Some(raw)
        };
        Ok(Self {
            target,
            parent,
            folder,
            leaf,
            original,
            updated,
        })
    }

    fn install(self, dirs: &Settings) -> Result<Option<PathBuf>> {
        self.install_with(dirs, || Ok(()))
    }

    fn verify_original(&self, dirs: &Settings) -> Result<()> {
        if Directory::open(&self.parent, OpenDir::PrivateExisting)?.identity()
            != self.folder.dir().identity()
        {
            return Err(LocalError::ClientConfigChanged);
        }
        outside(self.folder.dir(), &dirs.endpoints())?;
        if optional(self.folder.dir(), &self.leaf)? != self.original {
            return Err(LocalError::ClientConfigChanged);
        }
        Ok(())
    }

    fn install_with(
        self,
        dirs: &Settings,
        after_backup: impl FnOnce() -> Result<()>,
    ) -> Result<Option<PathBuf>> {
        self.verify_original(dirs)?;
        let guard = self.folder.serialized(LockMode::Create)?;
        self.verify_original(dirs)?;
        let Some(updated) = &self.updated else {
            return Ok(None);
        };
        let backup = if let Some(raw) = &self.original {
            if !self.folder.dir().writable_attribute(&self.leaf)? {
                return Err(LocalError::ClientConfigReadOnly);
            }
            let digest = format!("{:x}", Sha256::digest(raw));
            let basename = format!("{}.{}.bak", filename(&self.target)?, &digest[..24]);
            let n = name(basename.as_ref())?;
            match guard.write(&n, raw, Install::New) {
                Ok(()) => (),
                Err(LocalError::Exists) => match guard.read(&n, LIMIT) {
                    Ok(existing) if existing == *raw => (),
                    Ok(_) | Err(LocalError::FileLimit) => {
                        return Err(LocalError::ClientBackupConflict);
                    }
                    Err(e) => return Err(e),
                },
                Err(e) => return Err(e),
            }
            Some(self.parent.as_path().join(basename))
        } else {
            None
        };
        after_backup()?;
        self.verify_original(dirs)?;
        if self.original.is_some() && !self.folder.dir().writable_attribute(&self.leaf)? {
            return Err(LocalError::ClientConfigReadOnly);
        }
        match guard.write(
            &self.leaf,
            updated,
            if self.original.is_some() {
                Install::Replace
            } else {
                Install::New
            },
        ) {
            Err(LocalError::Exists) => return Err(LocalError::ClientConfigChanged),
            other => other?,
        }
        if &guard.read(&self.leaf, LIMIT)? != updated {
            return Err(LocalError::ClientConfigVerifyFailed);
        }
        Ok(backup)
    }
}

pub fn configure(options: SetupOptions) -> Result<SetupResult> {
    configure_with(options, SetupMode::Configure)
}

/// Save manual folder choices only if the settings file is still absent.
/// Installing a named client is a separate, explicitly requested operation.
pub fn configure_initial(options: SetupOptions) -> Result<SetupResult> {
    if !matches!(options.client, Client::ConfigOnly) {
        return Err(LocalError::InvalidClientOption);
    }
    configure_with(options, SetupMode::FirstSave)
}

fn configure_with(options: SetupOptions, mode: SetupMode) -> Result<SetupResult> {
    let dirs = Settings::new(options.input, options.outbox, options.state);
    dirs.validate(&options.settings)?;
    if dirs
        .endpoints()
        .iter()
        .any(|p| p.is_within(&options.settings))
    {
        return Err(LocalError::OverlappingPaths);
    }
    if reserved(filename(&options.settings)?)
        || options
            .dataset
            .as_deref()
            .is_some_and(|id| !id.is_empty() && !valid_dataset_id(id))
    {
        return Err(LocalError::InvalidSettings);
    }
    let entry = json!({"command":text(&options.executable)?,"args":["serve-folder","--settings",text(&options.settings)?]});
    preflight_directories(&options.settings, &dirs, mode)?;
    let (client, prepared) = match options.client {
        Client::ConfigOnly => ("config-only", None),
        Client::ClaudeDesktop {
            config,
            replace_existing,
        } => {
            let target = match config {
                Some(p) => p,
                None => default_config()?,
            };
            text(&target)?;
            (
                "claude-desktop",
                Some(ClientFile::prepare(
                    target,
                    &options.settings,
                    &dirs,
                    &entry,
                    replace_existing,
                )?),
            )
        }
    };
    configure_settings(
        &options.settings,
        dirs.clone(),
        options.dataset.as_deref(),
        options.watch,
        mode,
    )?;
    let changed = prepared.as_ref().is_some_and(|file| file.updated.is_some());
    let backup_path = match prepared {
        Some(f) => f.install(&dirs)?,
        None => None,
    };
    Ok(SetupResult {
        status: status(&options.settings)?,
        client,
        client_config_changed: changed,
        backup_path,
        fragment: json!({"mcpServers":{ENTRY:entry}}),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn concurrent_edits_and_interrupted_install_retain_originals() {
        let tmp = std::env::temp_dir();
        #[cfg(unix)]
        let tmp = tmp.canonicalize().unwrap();
        let root = tmp.join(format!("ironvellum-client-retry-{}", std::process::id()));
        let dir = PrivateFolder::open(&selected(&root).unwrap(), LocklessOpen::CreateLeaf).unwrap();
        let path = |leaf: &str| selected(&root.join(leaf)).unwrap();
        let target = path("client.json");
        let settings = path("settings.json");
        let dirs = Settings::new(path("input"), path("out"), path("state"));
        let leaf = name("client.json".as_ref()).unwrap();
        let write = |raw: &[u8], mode| {
            dir.serialized(LockMode::Create)
                .unwrap()
                .write(&leaf, raw, mode)
                .unwrap();
        };
        let prepare = || {
            ClientFile::prepare(
                target.clone(),
                &settings,
                &dirs,
                &json!({"command":"test"}),
                false,
            )
            .unwrap()
        };
        let pending = prepare();
        let original = b" { \"old\" : 1 } ";
        write(original, Install::New);
        assert_eq!(pending.install(&dirs), Err(LocalError::ClientConfigChanged));
        let pending = prepare();
        write(b"{}", Install::Replace);
        assert_eq!(pending.install(&dirs), Err(LocalError::ClientConfigChanged));
        write(original, Install::Replace);
        assert_eq!(
            prepare().install_with(&dirs, || Err(LocalError::StorageUnavailable)),
            Err(LocalError::StorageUnavailable)
        );
        let digest = format!("{:x}", Sha256::digest(original));
        let backup = root.join(format!("client.json.{}.bak", &digest[..24]));
        assert_eq!(fs::read(target.as_path()).unwrap(), original);
        assert_eq!(fs::read(&backup).unwrap(), original);
        assert_eq!(prepare().install(&dirs).unwrap(), Some(backup));
        drop(dir);
        fs::remove_dir_all(root).unwrap();
    }
}
