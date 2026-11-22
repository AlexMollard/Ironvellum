//! Bounded filesystem operations on pinned local directories.
#[cfg(unix)]
use crate::posix as native;
#[cfg(windows)]
use crate::windows as native;
use crate::{Entry, Identity, LocalError, Name, OpenDir, OpenFile, Selected, Stamp, name};
use ironvellum_contracts::wire::{MAX_CONTEXT, MAX_FRAME};
use std::{
    fs::File,
    io::{self, Read, Write},
    sync::Arc,
};

type Result<T> = std::result::Result<T, LocalError>;
const LOCK_NAME: &str = ".ironvellum-lock-v1";

pub type Entries = Box<dyn Iterator<Item = Result<Entry>> + Send>;

pub struct Directory(Arc<native::Directory>);

impl Directory {
    pub(crate) fn open_parent(path: &Selected) -> Result<Self> {
        Ok(Self(Arc::new(path.open_context_parent()?)))
    }

    pub fn open(path: &Selected, mode: OpenDir) -> Result<Self> {
        match mode {
            OpenDir::InputExisting => native::validate_volume(path)?,
            _ => preflight(path, path)?,
        }
        Self::open_checked(path, mode)
    }

    fn open_checked(path: &Selected, mode: OpenDir) -> Result<Self> {
        Ok(Self(Arc::new(native::Directory::open(path, mode)?)))
    }

    pub fn identity(&self) -> Identity {
        self.0.identity()
    }

    pub fn ancestors(&self) -> &[Identity] {
        self.0.ancestors()
    }

    pub fn entries(&self) -> Result<impl Iterator<Item = Result<Entry>> + '_> {
        self.0.entries()
    }

    /// The cursor keeps the original native pins and can move to another thread.
    pub fn owned_entries(&self) -> Result<Entries> {
        Ok(Box::new(Arc::clone(&self.0).owned_entries()?))
    }

    pub fn exists_no_follow(&self, n: &Name) -> Result<bool> {
        self.0.exists_no_follow(n)
    }

    pub fn read(&self, n: &Name, limit: usize) -> Result<Vec<u8>> {
        let file = self.0.open_file(n, OpenFile::Read)?;
        let mut bytes = Vec::new();
        file.take(u64::try_from(limit).unwrap_or(u64::MAX).saturating_add(1))
            .read_to_end(&mut bytes)
            .map_err(storage_io)?;
        if bytes.len() > limit {
            return Err(LocalError::FileLimit);
        }
        Ok(bytes)
    }

    pub fn writable_attribute(&self, n: &Name) -> Result<bool> {
        native::writable_attribute(&self.0.open_file(n, OpenFile::Read)?)
    }

    pub fn file_stamp(&self, n: &Name) -> Result<Stamp> {
        native::stamp(&self.0.open_file(n, OpenFile::Read)?)
    }

    pub fn stable_bytes(&self, n: &Name, observed: &Stamp) -> Result<Vec<u8>> {
        let length = usize::try_from(observed.length)
            .ok()
            .filter(|&length| length <= MAX_CONTEXT)
            .ok_or(LocalError::FileLimit)?;
        let mut file = self.0.open_file(n, OpenFile::Read)?;
        if native::stamp(&file)? != *observed {
            return Err(LocalError::SourceChanged);
        }
        let mut bytes = vec![0; length];
        file.read_exact(&mut bytes).map_err(|error| {
            if error.kind() == io::ErrorKind::UnexpectedEof {
                LocalError::SourceChanged
            } else {
                storage_io(error)
            }
        })?;
        if native::stamp(&file)? != *observed || self.file_stamp(n)? != *observed {
            return Err(LocalError::SourceChanged);
        }
        Ok(bytes)
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum LocklessOpen {
    Existing,
    CreateLeaf,
}

impl LocklessOpen {
    fn private(self) -> OpenDir {
        match self {
            Self::Existing => OpenDir::PrivateExisting,
            Self::CreateLeaf => OpenDir::PrivateCreateLeaf,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum LockMode {
    Existing,
    Create,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Install {
    New,
    Replace,
}

pub struct PrivateFolder {
    directory: Directory,
}

impl PrivateFolder {
    pub fn open(path: &Selected, mode: LocklessOpen) -> Result<Self> {
        Ok(Self {
            directory: Directory::open(path, mode.private())?,
        })
    }

    pub fn dir(&self) -> &Directory {
        &self.directory
    }

    pub fn serialized(&self, mode: LockMode) -> Result<LockedFolder<'_>> {
        let filename = name(LOCK_NAME.as_ref())?;
        let open = match mode {
            LockMode::Existing => OpenFile::ExistingLock,
            LockMode::Create => OpenFile::CreateLock,
        };
        let lock = native::FileLock::acquire(self.directory.0.open_file(&filename, open)?)?;
        let stamp = native::stamp(lock.file())?;
        if stamp.length != 0 {
            return Err(LocalError::OutboxConflict);
        }
        Ok(LockedFolder {
            dir: &self.directory,
            _lock: lock,
            lock_identity: stamp.identity,
        })
    }
}

pub struct LockedFolder<'a> {
    dir: &'a Directory,
    _lock: native::FileLock,
    lock_identity: Identity,
}

impl LockedFolder<'_> {
    pub fn read(&self, n: &Name, limit: usize) -> Result<Vec<u8>> {
        self.dir.read(n, limit)
    }

    pub fn write(&self, n: &Name, bytes: &[u8], mode: Install) -> Result<()> {
        self.write_then_sync(n, bytes, mode, native::Directory::sync)
    }

    fn write_then_sync(
        &self,
        n: &Name,
        bytes: &[u8],
        mode: Install,
        sync: impl FnOnce(&native::Directory) -> Result<()>,
    ) -> Result<()> {
        self.check_target(n)?;
        let directory = &self.dir.0;
        let temp = temp_name()?;
        let mut file = directory.open_file(&temp, OpenFile::CreateNew)?;
        let written = write_sync(&mut file, bytes);
        drop(file);
        let installed = written.and_then(|()| match mode {
            Install::New => directory.publish_new(&temp, n),
            Install::Replace => directory.replace(&temp, n),
        });
        if let Err(error) = installed {
            // This name was created exclusively above. Never clean up the target.
            return match directory.unlink(&temp) {
                Ok(()) | Err(LocalError::Missing) => Err(error),
                Err(cleanup_error) => Err(cleanup_error),
            };
        }
        // After rename succeeds its former name is no longer ours to remove.
        sync(directory)
    }

    /// Replays the exact original bytes. A conflicting file is left untouched.
    pub fn publish(&self, n: &Name, original: &[u8]) -> Result<()> {
        self.publish_with(n, original, || self.write(n, original, Install::New))
    }

    fn publish_with(
        &self,
        n: &Name,
        original: &[u8],
        install_new: impl FnOnce() -> Result<()>,
    ) -> Result<()> {
        self.check_target(n)?;
        let matches = |bytes: Vec<u8>| {
            if bytes == original {
                Ok(())
            } else {
                Err(LocalError::OutboxConflict)
            }
        };
        match self.read(n, MAX_FRAME) {
            Ok(bytes) => matches(bytes),
            Err(LocalError::Missing) => match install_new() {
                Err(LocalError::Exists) => matches(self.read(n, MAX_FRAME)?),
                result => result,
            },
            Err(error) => Err(error),
        }
    }

    pub fn unlink(&self, n: &Name) -> Result<()> {
        if !self.check_target(n)? {
            return Err(LocalError::Missing);
        }
        self.dir.0.unlink(n)
    }

    // ExistingLock validates the file and shares write access with the held lock on Windows.
    // Its identity detects aliases according to the actual volume's naming rules.
    fn check_target(&self, n: &Name) -> Result<bool> {
        if n.as_os_str() == LOCK_NAME {
            return Err(LocalError::InvalidPath);
        }
        match self.dir.0.open_file(n, OpenFile::ExistingLock) {
            Ok(file) if native::stamp(&file)?.identity == self.lock_identity => {
                Err(LocalError::InvalidPath)
            }
            Ok(_) => Ok(true),
            Err(LocalError::Missing) => Ok(false),
            Err(error) => Err(error),
        }
    }
}

fn write_sync(file: &mut File, bytes: &[u8]) -> Result<()> {
    file.write_all(bytes).map_err(storage_io)?;
    file.sync_all().map_err(storage_io)
}

fn storage_io(_: io::Error) -> LocalError {
    LocalError::StorageUnavailable
}

pub(crate) fn uuid_v4() -> Result<String> {
    let mut bytes = [0; 16];
    getrandom::fill(&mut bytes).map_err(|_| LocalError::StorageUnavailable)?;
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    let hex = format!("{:032x}", u128::from_be_bytes(bytes));
    Ok(format!(
        "{}-{}-{}-{}-{}",
        &hex[..8],
        &hex[8..12],
        &hex[12..16],
        &hex[16..20],
        &hex[20..],
    ))
}

fn temp_name() -> Result<Name> {
    name(format!(".ironvellum-{}.tmp", uuid_v4()?).as_ref())
}

pub struct LocalFiles {
    context: Vec<u8>,
    outbox: PrivateFolder,
}

impl LocalFiles {
    pub fn open(context: &Selected, outbox: &Selected) -> Result<Self> {
        Self::open_with_mode(context, outbox, LocklessOpen::CreateLeaf)
    }

    pub fn open_with_mode(
        context: &Selected,
        outbox: &Selected,
        mode: LocklessOpen,
    ) -> Result<Self> {
        preflight(context, outbox)?;
        if context.is_within(outbox) {
            return Err(LocalError::OverlappingPaths);
        }
        let parent = Directory::open_parent(context)?;
        let basename = name(
            context
                .as_path()
                .file_name()
                .ok_or(LocalError::InvalidPath)?,
        )?;
        let context = parent.read(&basename, MAX_CONTEXT)?;
        let outbox = Directory::open_checked(outbox, mode.private())?;
        if below(&parent, &outbox) {
            return Err(LocalError::OverlappingPaths);
        }
        Ok(Self {
            context,
            outbox: PrivateFolder { directory: outbox },
        })
    }

    pub fn context(&self) -> &[u8] {
        &self.context
    }

    pub fn outbox(&self) -> &PrivateFolder {
        &self.outbox
    }
}

fn preflight(context: &Selected, outbox: &Selected) -> Result<()> {
    #[cfg(unix)]
    {
        native::check_mounts(context, outbox)
    }

    #[cfg(windows)]
    {
        native::validate_volume(context)?;
        native::validate_volume(outbox)
    }
}

/// Order is input, outbox, state. Only the private endpoint leaves can be created.
pub fn open_endpoints(paths: [&Selected; 3], mode: LocklessOpen) -> Result<[Directory; 3]> {
    for (index, a) in paths.iter().enumerate() {
        for b in &paths[index + 1..] {
            if a.is_within(b) || b.is_within(a) {
                return Err(LocalError::OverlappingPaths);
            }
        }
    }
    for path in paths {
        preflight(path, path)?;
    }
    let [input, outbox, state] = paths;
    let directories = [
        Directory::open_checked(input, OpenDir::InputExisting)?,
        Directory::open_checked(outbox, mode.private())?,
        Directory::open_checked(state, mode.private())?,
    ];
    for (index, a) in directories.iter().enumerate() {
        for b in &directories[index + 1..] {
            if below(a, b) || below(b, a) {
                return Err(LocalError::OverlappingPaths);
            }
        }
    }
    Ok(directories)
}

pub fn endpoint_identities(paths: [&Selected; 3], mode: LocklessOpen) -> Result<[Identity; 3]> {
    Ok(open_endpoints(paths, mode)?
        .each_ref()
        .map(Directory::identity))
}

pub(crate) fn below(path: &Directory, parent: &Directory) -> bool {
    path.identity() == parent.identity() || path.ancestors().contains(&parent.identity())
}

#[cfg(test)]
mod tests;
