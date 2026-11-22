mod error;
mod files;
#[cfg(any(windows, target_os = "linux", target_os = "macos"))]
mod journal;
#[cfg(any(windows, target_os = "linux", target_os = "macos"))]
pub use journal::{ArchiveItem, Journal, ReadyArchive, ReadyPage};
#[cfg(unix)]
pub mod posix;
#[cfg(any(windows, target_os = "linux", target_os = "macos"))]
mod scan;
#[cfg(any(windows, target_os = "linux", target_os = "macos"))]
mod settings;
#[cfg(any(windows, target_os = "linux", target_os = "macos"))]
pub use settings::{
    Settings, load_settings, save_settings, update_bound_settings, update_settings,
    valid_dataset_id,
};
#[cfg(any(windows, target_os = "linux", target_os = "macos"))]
mod storage;
#[cfg(windows)]
pub mod windows;

pub use error::LocalError;
pub use files::{
    Entry, EntryKind, Identity, Name, OpenDir, OpenFile, Selected, Stamp, name, selected,
};
#[cfg(any(windows, target_os = "linux", target_os = "macos"))]
pub use scan::{CandidateNames, candidate_names};
#[cfg(any(windows, target_os = "linux", target_os = "macos"))]
pub use storage::{
    Directory, Entries, Install, LocalFiles, LockMode, LockedFolder, LocklessOpen, PrivateFolder,
    endpoint_identities, open_endpoints,
};
