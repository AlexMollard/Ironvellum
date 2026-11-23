use ironvellum_contracts::Refusal;
use std::fmt;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum LocalError {
    Missing,
    Exists,
    StorageUnavailable,
    AbsolutePathRequired,
    MissingFolders,
    LocalPathRequired,
    InvalidPath,
    LocalVolumeRequired,
    UnsupportedPlatform,
    PrivateOutboxRequired,
    RegularDirectoryRequired,
    RegularFileRequired,
    OverlappingPaths,
    FileLimit,
    FileCountLimit,
    SourceChanged,
    SnapshotConflict,
    SourceUnavailable,
    SourceExpired,
    DatasetUnavailable,
    OutboxConflict,
    InvalidSettings,
    SettingsVerifyFailed,
    InvalidClientOption,
    InvalidClientConfig,
    ClientConfigRequired,
    ClientEntryConflict,
    ClientConfigReadOnly,
    ClientConfigChanged,
    ClientBackupConflict,
    ClientConfigVerifyFailed,
    InvalidSession,
    ReloadRequired,
    JournalConflict,
    IdempotencyConflict,
    Capacity,
    Clock,
    NotFound,
    Contract(Refusal),
}

impl LocalError {
    pub const fn code(self) -> &'static str {
        match self {
            Self::Missing | Self::Exists | Self::StorageUnavailable => "storage_unavailable",
            Self::AbsolutePathRequired => "absolute_path_required",
            Self::MissingFolders => "missing_folders",
            Self::LocalPathRequired => "local_path_required",
            Self::InvalidPath => "invalid_path",
            Self::LocalVolumeRequired => "local_volume_required",
            Self::UnsupportedPlatform => "unsupported_platform",
            Self::PrivateOutboxRequired => "private_outbox_required",
            Self::RegularDirectoryRequired => "regular_directory_required",
            Self::RegularFileRequired => "regular_file_required",
            Self::OverlappingPaths => "overlapping_paths",
            Self::FileLimit => "file_limit",
            Self::FileCountLimit => "file_count_limit",
            Self::SourceChanged => "source_changed",
            Self::SnapshotConflict => "snapshot_conflict",
            Self::SourceUnavailable => "source_unavailable",
            Self::SourceExpired => "source_expired",
            Self::DatasetUnavailable => "dataset_unavailable",
            Self::OutboxConflict => "outbox_conflict",
            Self::InvalidSession => "invalid_session",
            Self::InvalidSettings => "invalid_settings",
            Self::SettingsVerifyFailed => "settings_verify_failed",
            Self::InvalidClientOption => "invalid_client_option",
            Self::InvalidClientConfig => "invalid_client_config",
            Self::ClientConfigRequired => "client_config_required",
            Self::ClientEntryConflict => "client_entry_conflict",
            Self::ClientConfigReadOnly => "client_config_read_only",
            Self::ClientConfigChanged => "client_config_changed",
            Self::ClientBackupConflict => "client_backup_conflict",
            Self::ClientConfigVerifyFailed => "client_config_verify_failed",
            Self::ReloadRequired => "reload_required",
            Self::JournalConflict => "journal_conflict",
            Self::IdempotencyConflict => "idempotency_conflict",
            Self::Capacity => "capacity",
            Self::Clock => "clock",
            Self::NotFound => "not_found",
            Self::Contract(error) => error.code(),
        }
    }
}

impl From<Refusal> for LocalError {
    fn from(error: Refusal) -> Self {
        Self::Contract(error)
    }
}

impl fmt::Display for LocalError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(self.code())
    }
}

impl std::error::Error for LocalError {}
