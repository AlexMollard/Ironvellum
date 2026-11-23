use crate::{Directory, Identity, OpenDir};
use crate::{
    Install, LocalError, LockMode, LockedFolder, LocklessOpen, Name, PrivateFolder, Selected, name,
    open_endpoints, selected,
};
use ironvellum_contracts::{Value, loads};
use std::path::Path;

type Result<T> = std::result::Result<T, LocalError>;
const LIMIT: usize = 65_536;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct Settings {
    pub input_directory: Selected,
    pub outbox_directory: Selected,
    pub state_directory: Selected,
    pub selected_dataset_id: Option<String>,
    pub watch_enabled: bool,
}

pub fn valid_dataset_id(id: &str) -> bool {
    id.len() == 36
        && id.bytes().enumerate().all(|(i, b)| match i {
            8 | 13 | 18 | 23 => b == b'-',
            14 => b == b'4',
            19 => matches!(b, b'8' | b'9' | b'a' | b'b'),
            _ => b.is_ascii_digit() || matches!(b, b'a'..=b'f'),
        })
}

impl Settings {
    pub fn new(
        input_directory: Selected,
        outbox_directory: Selected,
        state_directory: Selected,
    ) -> Self {
        Self {
            input_directory,
            outbox_directory,
            state_directory,
            selected_dataset_id: None,
            watch_enabled: false,
        }
    }

    pub fn endpoints(&self) -> [&Selected; 3] {
        [
            &self.input_directory,
            &self.outbox_directory,
            &self.state_directory,
        ]
    }

    pub fn validate(&self, file: &Selected) -> Result<()> {
        let paths = self.endpoints();
        for (index, path) in paths.iter().enumerate() {
            path.as_path().to_str().ok_or(LocalError::InvalidSettings)?;
            if paths[..index]
                .iter()
                .any(|other| path.is_within(other) || other.is_within(path))
            {
                return Err(LocalError::OverlappingPaths);
            }
        }
        if file.is_within(paths[0]) || file.is_within(paths[1]) {
            return Err(LocalError::OverlappingPaths);
        }
        if self
            .selected_dataset_id
            .as_deref()
            .is_some_and(|id| !valid_dataset_id(id))
        {
            return Err(LocalError::InvalidSettings);
        }
        #[cfg(windows)]
        for path in paths {
            crate::windows::validate_volume(path)?;
        }
        Ok(())
    }

    fn decode(file: &Selected, raw: &[u8]) -> Result<Self> {
        let invalid = LocalError::InvalidSettings;
        let data = loads(raw, LIMIT, 16)?;
        let obj = data.as_object().ok_or(invalid)?;
        if obj.len() != 6 || obj.get("schemaVersion").and_then(Value::as_u64) != Some(1) {
            return Err(invalid);
        }
        let path = |key: &str| {
            selected(Path::new(
                obj.get(key).and_then(Value::as_str).ok_or(invalid)?,
            ))
        };
        let value = Self {
            input_directory: path("inputDirectory")?,
            outbox_directory: path("outboxDirectory")?,
            state_directory: path("stateDirectory")?,
            selected_dataset_id: match obj.get("selectedDatasetId") {
                Some(Value::Null) => None,
                Some(Value::String(id)) => Some(id.clone()),
                _ => return Err(invalid),
            },
            watch_enabled: obj
                .get("watchEnabled")
                .and_then(Value::as_bool)
                .ok_or(invalid)?,
        };
        value.validate(file)?;
        Ok(value)
    }

    fn encode(&self) -> Result<Vec<u8>> {
        // Legacy json.dumps uses spaces after commas and colons, including for first creation.
        let quote = |path: &Selected| {
            let text = path.as_path().to_str().ok_or(LocalError::InvalidSettings)?;
            serde_json::to_string(text).map_err(|_| LocalError::InvalidSettings)
        };
        let dataset = serde_json::to_string(&self.selected_dataset_id)
            .map_err(|_| LocalError::InvalidSettings)?;
        let raw = format!(
            concat!(
                "{{\"schemaVersion\": 1, \"inputDirectory\": {}, ",
                "\"outboxDirectory\": {}, \"stateDirectory\": {}, ",
                "\"selectedDatasetId\": {}, \"watchEnabled\": {}}}"
            ),
            quote(&self.input_directory)?,
            quote(&self.outbox_directory)?,
            quote(&self.state_directory)?,
            dataset,
            self.watch_enabled
        )
        .into_bytes();
        if raw.len() > LIMIT {
            return Err(LocalError::FileLimit);
        }
        Ok(raw)
    }
}

fn location(path: &Selected) -> Result<(Selected, Name)> {
    let path = path.as_path();
    Ok((
        selected(path.parent().ok_or(LocalError::InvalidPath)?)?,
        name(path.file_name().ok_or(LocalError::InvalidPath)?)?,
    ))
}

pub fn load_settings(path: &Selected) -> Result<Settings> {
    let (parent, n) = location(path)?;
    let folder = PrivateFolder::open(&parent, LocklessOpen::Existing)?;
    Settings::decode(path, &folder.dir().read(&n, LIMIT)?)
}

fn check_location(parent: &Directory, endpoint: &Directory) -> Result<()> {
    if crate::storage::below(parent, endpoint) {
        return Err(LocalError::OverlappingPaths);
    }
    Ok(())
}

// An old configuration may refer to missing leaves. Check each existing endpoint
// independently, so a missing input cannot hide an alias of the outbox.
#[derive(Clone, Copy)]
enum LocationCheck {
    Proposed,
    Stored,
    Bound,
}

fn check_existing_location(
    parent: &Directory,
    value: &Settings,
    mode: LocationCheck,
) -> Result<()> {
    for endpoint in [&value.input_directory, &value.outbox_directory] {
        match Directory::open(endpoint, OpenDir::InputExisting) {
            Ok(directory) => check_location(parent, &directory)?,
            Err(LocalError::Missing) => (),
            Err(_) if matches!(mode, LocationCheck::Bound) => {
                return Err(LocalError::ReloadRequired);
            }
            Err(
                error @ (LocalError::RegularDirectoryRequired | LocalError::StorageUnavailable),
            ) if matches!(mode, LocationCheck::Stored) => {
                // A former directory may now be a plain file. Its guarded parent
                // and no-follow leaf metadata distinguish it from a link or an
                // unsafe ancestor without opening or reading the replacement.
                let leaf = name(
                    endpoint
                        .as_path()
                        .file_name()
                        .ok_or(LocalError::InvalidPath)?,
                )?;
                if !endpoint
                    .open_context_parent()?
                    .is_regular_no_follow(&leaf)?
                {
                    return Err(error);
                }
            }
            Err(error) => return Err(error),
        }
    }
    Ok(())
}

fn endpoint_error(error: LocalError, bound: bool) -> LocalError {
    if bound && error == LocalError::LocalVolumeRequired {
        LocalError::ReloadRequired
    } else {
        error
    }
}

fn read_existing(
    folder: &PrivateFolder,
    path: &Selected,
    n: &Name,
    bound: bool,
) -> Result<Option<Settings>> {
    let raw = match folder.dir().read(n, LIMIT) {
        Ok(raw) => raw,
        Err(LocalError::Missing) => return Ok(None),
        Err(err) => return Err(err),
    };
    let value = Settings::decode(path, &raw).map_err(|e| endpoint_error(e, bound))?;
    let mode = if bound {
        LocationCheck::Bound
    } else {
        LocationCheck::Stored
    };
    check_existing_location(folder.dir(), &value, mode)?;
    Ok(Some(value))
}

fn settings_lock<'a>(
    folder: &'a PrivateFolder,
    path: &Selected,
    n: &Name,
    bound: bool,
) -> Result<LockedFolder<'a>> {
    match folder.serialized(LockMode::Existing) {
        Ok(guard) => Ok(guard),
        Err(LocalError::Missing) => {
            // Before introducing the lock file, exclude locations identified by
            // legacy settings. The locked read below remains authoritative.
            read_existing(folder, path, n, bound)?;
            folder.serialized(LockMode::Create)
        }
        Err(err) => Err(err),
    }
}

fn persist(
    guard: &LockedFolder<'_>,
    n: &Name,
    value: &Settings,
    old: Option<&Settings>,
) -> Result<()> {
    let raw = value.encode()?;
    if old.is_some_and(|old| equivalent(old, value)) {
        return Ok(());
    }
    guard.write(
        n,
        &raw,
        if old.is_some() {
            Install::Replace
        } else {
            Install::New
        },
    )
}

pub fn save_settings(path: &Selected, value: &Settings) -> Result<()> {
    save(path, value.clone(), None).map(|_| ())
}

fn equivalent(a: &Settings, b: &Settings) -> bool {
    same_paths(a.endpoints(), b.endpoints())
        && a.watch_enabled == b.watch_enabled
        && a.selected_dataset_id == b.selected_dataset_id
}

/// Configure explicitly selected directories, preserving omitted preferences
/// from the most recent settings read under the directory lock.
pub(crate) fn configure_settings(
    path: &Selected,
    directories: Settings,
    dataset: Option<&str>,
    watch: Option<bool>,
) -> Result<Settings> {
    let dataset = dataset.filter(|id| !id.is_empty());
    if dataset.is_some_and(|id| !valid_dataset_id(id)) {
        return Err(LocalError::InvalidSettings);
    }
    save(path, directories, Some((dataset, watch)))
}

fn save(
    path: &Selected,
    mut value: Settings,
    setup: Option<(Option<&str>, Option<bool>)>,
) -> Result<Settings> {
    value.validate(path)?;
    let (parent, n) = location(path)?;
    let folder = match PrivateFolder::open(&parent, LocklessOpen::Existing) {
        Ok(folder) => folder,
        Err(LocalError::Missing) => {
            let ancestor = Directory::open_parent(&parent)?;
            check_existing_location(&ancestor, &value, LocationCheck::Proposed)?;
            PrivateFolder::open(&parent, LocklessOpen::CreateLeaf)?
        }
        Err(err) => return Err(err),
    };
    check_existing_location(folder.dir(), &value, LocationCheck::Proposed)?;
    let guard = settings_lock(&folder, path, &n, false)?;
    let old = read_existing(&folder, path, &n, false)?;
    if let Some((dataset, watch)) = setup {
        value.selected_dataset_id = dataset
            .map(str::to_owned)
            .or_else(|| old.as_ref().and_then(|s| s.selected_dataset_id.clone()));
        value.watch_enabled =
            watch.unwrap_or_else(|| old.as_ref().is_some_and(|s| s.watch_enabled));
        match Directory::open(&value.input_directory, OpenDir::InputExisting) {
            Ok(_) => (),
            Err(LocalError::Missing) => {
                Directory::open(&value.input_directory, OpenDir::PrivateCreateLeaf)?;
            }
            Err(err) => return Err(err),
        }
    }
    let pinned = open_endpoints(value.endpoints(), LocklessOpen::CreateLeaf)?;
    for endpoint in &pinned[..2] {
        check_location(folder.dir(), endpoint)?;
    }
    persist(&guard, &n, &value, old.as_ref())?;
    if setup.is_some() {
        let stored = load_settings(path)?;
        if !equivalent(&value, &stored) {
            return Err(LocalError::SettingsVerifyFailed);
        }
        return Ok(stored);
    }
    Ok(value)
}

pub fn update_settings(
    path: &Selected,
    change: impl FnOnce(Settings) -> Result<Settings>,
) -> Result<Settings> {
    update(path, None, LocklessOpen::CreateLeaf, change)
}

pub(crate) fn update_existing_settings(
    path: &Selected,
    change: impl FnOnce(Settings) -> Result<Settings>,
) -> Result<Settings> {
    update(path, None, LocklessOpen::Existing, change)
}

/// Apply a preference change only while the configured paths still identify
/// the three directories that the caller observed. Never create endpoint leaves.
pub fn update_bound_settings(
    path: &Selected,
    expected_paths: [&Selected; 3],
    expected_identities: [Identity; 3],
    change: impl FnOnce(Settings) -> Result<Settings>,
) -> Result<Settings> {
    update(
        path,
        Some((expected_paths, expected_identities)),
        LocklessOpen::Existing,
        change,
    )
}

fn same_paths(a: [&Selected; 3], b: [&Selected; 3]) -> bool {
    a.into_iter()
        .zip(b)
        .all(|(x, y)| x.is_within(y) && y.is_within(x))
}

fn update(
    path: &Selected,
    binding: Option<([&Selected; 3], [Identity; 3])>,
    mode: LocklessOpen,
    change: impl FnOnce(Settings) -> Result<Settings>,
) -> Result<Settings> {
    let (parent, n) = location(path)?;
    let folder = PrivateFolder::open(&parent, LocklessOpen::Existing)?;
    let guard = settings_lock(&folder, path, &n, binding.is_some())?;
    let old = read_existing(&folder, path, &n, binding.is_some())?.ok_or(LocalError::Missing)?;
    if let Some((paths, _)) = binding
        && !same_paths(old.endpoints(), paths)
    {
        return Err(LocalError::ReloadRequired);
    }
    let value = change(old.clone())?;
    value
        .validate(path)
        .map_err(|e| endpoint_error(e, binding.is_some()))?;
    let pinned = if let Some((paths, identities)) = binding {
        if !same_paths(value.endpoints(), paths) {
            return Err(LocalError::ReloadRequired);
        }
        let pins = open_endpoints(value.endpoints(), LocklessOpen::Existing)
            .map_err(|_| LocalError::ReloadRequired)?;
        if pins.each_ref().map(Directory::identity) != identities {
            return Err(LocalError::ReloadRequired);
        }
        pins
    } else {
        open_endpoints(value.endpoints(), mode)?
    };
    for endpoint in &pinned[..2] {
        check_location(folder.dir(), endpoint)?;
    }
    persist(&guard, &n, &value, Some(&old))?;
    Ok(value)
}
