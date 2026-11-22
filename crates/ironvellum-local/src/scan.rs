use crate::{Directory, EntryKind, LocalError};
use std::ffi::OsString;

pub type CandidateNames = (Vec<OsString>, Option<LocalError>);

/// Count every entry, retaining valid candidates even if a special JSON entry is present.
pub fn candidate_names(
    directory: &Directory,
    candidate_limit: usize,
    entry_limit: usize,
) -> Result<CandidateNames, LocalError> {
    let mut names = Vec::new();
    let mut deferred = None;
    for (index, entry) in directory.entries()?.enumerate() {
        if index >= entry_limit {
            return Err(LocalError::FileCountLimit);
        }
        let entry = entry?;
        if !entry
            .name
            .as_encoded_bytes()
            .last_chunk::<5>()
            .is_some_and(|suffix| suffix.eq_ignore_ascii_case(b".json"))
        {
            continue;
        }
        match entry.kind {
            EntryKind::Directory => (),
            EntryKind::Special => deferred = Some(LocalError::RegularFileRequired),
            EntryKind::Regular => {
                if names.len() == candidate_limit {
                    return Err(LocalError::FileCountLimit);
                }
                names.push(entry.name);
            }
        }
    }
    names.sort_unstable();
    Ok((names, deferred))
}
