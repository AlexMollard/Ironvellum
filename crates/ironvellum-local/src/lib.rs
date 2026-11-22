mod error;
mod files;
#[cfg(unix)]
pub mod posix;
#[cfg(any(windows, target_os = "linux", target_os = "macos"))]
mod scan;
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
