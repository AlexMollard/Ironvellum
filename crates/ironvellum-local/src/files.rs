use crate::LocalError;
use std::ffi::{OsStr, OsString};
use std::path::{Path, PathBuf};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum OpenDir {
    InputExisting,
    PrivateExisting,
    PrivateCreateLeaf,
}
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum OpenFile {
    Read,
    CreateNew,
    CreateLock,
    ExistingLock,
}
#[derive(Clone, Copy, Debug, Eq, PartialEq, Hash)]
pub struct Identity {
    pub volume: u64,
    pub file: u64,
}
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct Stamp {
    pub identity: Identity,
    pub length: u64,
    pub modified_ns: i128,
    pub ctime_ns: i128,
}
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum EntryKind {
    Regular,
    Directory,
    Special,
}
#[derive(Debug)]
pub struct Entry {
    pub name: OsString,
    pub kind: EntryKind,
}

/// A lexically validated endpoint. Opening it also requires native volume and handle checks.
#[derive(Debug, Clone, Eq, PartialEq)]
pub struct Selected(PathBuf);

impl Selected {
    pub fn as_path(&self) -> &Path {
        &self.0
    }

    /// Includes equality and compares complete path components.
    pub fn is_within(&self, directory: &Self) -> bool {
        #[cfg(not(windows))]
        {
            self.0.starts_with(&directory.0)
        }

        #[cfg(windows)]
        {
            let mut candidate = self.0.components();
            directory.0.components().all(|part| {
                candidate.next().is_some_and(|other| {
                    let (a, b) = (part.as_os_str(), other.as_os_str());
                    match (a.to_str(), b.to_str()) {
                        (Some(a), Some(b)) => a.to_lowercase() == b.to_lowercase(),
                        _ => a
                            .as_encoded_bytes()
                            .eq_ignore_ascii_case(b.as_encoded_bytes()),
                    }
                })
            })
        }
    }
}

#[derive(Debug, Clone, Eq, PartialEq)]
pub struct Name(OsString);

impl Name {
    pub fn as_os_str(&self) -> &OsStr {
        &self.0
    }
}

fn separator(byte: &u8) -> bool {
    *byte == b'/' || (cfg!(windows) && *byte == b'\\')
}

#[cfg(windows)]
fn reserved(component: &[u8]) -> bool {
    if component.ends_with(b".")
        || component.ends_with(b" ")
        || component
            .iter()
            .any(|&b| b < 32 || b"<>:\"|?*".contains(&b))
    {
        return true;
    }
    let stem = component.split(|&b| b == b'.').next().unwrap_or_default();
    // Python's reserved-name comparison uppercases Unicode (including dotless i).
    // An unpaired UTF-16 surrogate can never occur in a reserved device name.
    let Ok(stem) = std::str::from_utf8(stem) else {
        return false;
    };
    let upper = stem.trim_end_matches(' ').to_uppercase();
    if matches!(
        upper.as_str(),
        "CON" | "PRN" | "AUX" | "NUL" | "CONIN$" | "CONOUT$"
    ) {
        return true;
    }
    matches!(
        upper
            .strip_prefix("COM")
            .or_else(|| upper.strip_prefix("LPT")),
        Some("1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" | "¹" | "²" | "³")
    )
}

fn invalid(component: &[u8]) -> bool {
    #[cfg(windows)]
    {
        reserved(component)
    }
    #[cfg(not(windows))]
    {
        component.contains(&0)
    }
}

pub fn name(value: &OsStr) -> Result<Name, LocalError> {
    let raw = value.as_encoded_bytes();
    if raw.is_empty() || raw == b"." || raw == b".." || raw.iter().any(separator) || invalid(raw) {
        return Err(LocalError::InvalidPath);
    }
    Ok(Name(value.to_owned()))
}

// Isolate the anchor before inspecting the tail. Network and device paths
// still undergo the root-only and parent-component checks before refusal.
#[cfg(windows)]
fn tail_after_anchor(raw: &[u8]) -> Result<(&[u8], bool), LocalError> {
    if raw.len() >= 2 && raw[..2].iter().all(separator) {
        let after_slashes = &raw[2..];
        let mut components = after_slashes.split(separator);
        let server = components.next();
        let share = components.next();
        let extended_unc =
            server == Some(b"?") && share.is_some_and(|s| s.eq_ignore_ascii_case(b"UNC"));
        let count = if extended_unc { 4 } else { 2 };
        let tail = after_slashes
            .splitn(count + 1, separator)
            .nth(count)
            .unwrap_or_default();
        return Ok((tail, false));
    }
    // ntpath accepts any single character as a drive designator during its
    // absolute-path check. Only an ASCII drive letter passes the local check.
    let Some(first) = raw.first() else {
        return Err(LocalError::AbsolutePathRequired);
    };
    let colon = first.leading_ones().max(1) as usize;
    if separator(first)
        || raw.get(colon) != Some(&b':')
        || !raw.get(colon + 1).is_some_and(separator)
    {
        return Err(LocalError::AbsolutePathRequired);
    }
    Ok((&raw[colon + 2..], raw[0].is_ascii_alphabetic()))
}

pub fn selected(path: &Path) -> Result<Selected, LocalError> {
    let raw = path.as_os_str().as_encoded_bytes();
    #[cfg(windows)]
    let (tail, local) = tail_after_anchor(raw)?;
    #[cfg(not(windows))]
    let (tail, local) = (
        raw.strip_prefix(b"/")
            .ok_or(LocalError::AbsolutePathRequired)?,
        !raw.starts_with(b"//"),
    );
    let mut components = tail
        .split(separator)
        .filter(|s| !s.is_empty() && *s != b".")
        .peekable();
    if components.peek().is_none() || components.clone().any(|s| s == b"..") {
        return Err(LocalError::AbsolutePathRequired);
    }
    if !local {
        return Err(LocalError::LocalPathRequired);
    }
    if components.any(invalid) {
        return Err(LocalError::InvalidPath);
    }
    Ok(Selected(path.to_owned()))
}

#[cfg(any(target_os = "linux", target_os = "macos"))]
use crate::posix::Directory as NativeDirectory;
#[cfg(windows)]
use crate::windows::Directory as NativeDirectory;

#[cfg(any(windows, target_os = "linux", target_os = "macos"))]
impl Selected {
    /// Only the input-parent walk may receive a volume root.
    pub(crate) fn open_context_parent(&self) -> Result<NativeDirectory, LocalError> {
        let parent = self.as_path().parent().ok_or(LocalError::InvalidPath)?;
        NativeDirectory::open(&Selected(parent.to_owned()), OpenDir::InputExisting)
    }
}

#[cfg(all(test, any(windows, target_os = "linux", target_os = "macos")))]
mod parent_tests {
    use super::*;
    #[test]
    fn validated_context_can_have_a_volume_root_parent() {
        let temp = std::env::temp_dir();
        let root = temp.ancestors().last().unwrap();
        assert!(selected(root).is_err());
        assert!(selected(&root.join("../bad")).is_err());
        let context = selected(&root.join("ironvellum-synthetic-absent-context.json")).unwrap();
        assert!(
            context
                .open_context_parent()
                .unwrap()
                .ancestors()
                .is_empty()
        );
        #[cfg(unix)]
        let temp = temp.canonicalize().unwrap();
        let context = selected(&temp.join("ironvellum-synthetic-absent-context.json")).unwrap();
        let expected =
            NativeDirectory::open(&selected(&temp).unwrap(), OpenDir::InputExisting).unwrap();
        assert_eq!(
            context.open_context_parent().unwrap().identity(),
            expected.identity()
        );
    }
}
