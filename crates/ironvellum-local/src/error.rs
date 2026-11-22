use ironvellum_contracts::Refusal;
use std::fmt;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum LocalError {
    Missing,
    Exists,
    StorageUnavailable,
    AbsolutePathRequired,
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
    OutboxConflict,
    InvalidSettings,
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
            Self::OutboxConflict => "outbox_conflict",
            Self::InvalidSettings => "invalid_settings",
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
