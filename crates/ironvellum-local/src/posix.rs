//! Operations anchored to open POSIX directory descriptors.
use crate::{LocalError, Selected};
use std::path::Path;

#[cfg(any(target_os = "linux", target_os = "macos"))]
mod native;
#[cfg(any(target_os = "linux", target_os = "macos"))]
pub use native::*;

type Result<T> = std::result::Result<T, LocalError>;

/// Reject a known remote ancestor of either endpoint or a remote mount below the outbox.
pub fn check_mounts(context: &Selected, outbox: &Selected) -> Result<()> {
    inspect_mounts(|mount, local| {
        if !local && intersects_endpoints(mount, context.as_path(), outbox.as_path()) {
            Err(LocalError::LocalVolumeRequired)
        } else {
            Ok(())
        }
    })
}

fn intersects_endpoints(mount: &Path, context: &Path, outbox: &Path) -> bool {
    context.starts_with(mount) || outbox.starts_with(mount) || mount.starts_with(outbox)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn mount_boundaries_use_the_exact_context_file_and_outbox_subtree() {
        let context = Path::new("/a/input/context.json");
        let outbox = Path::new("/b/output");
        for path in [
            "/a/input",
            "/a/input/context.json",
            "/b",
            "/b/output",
            "/b/output/nested",
        ] {
            assert!(intersects_endpoints(Path::new(path), context, outbox));
        }
        for path in ["/a/input/other", "/a/input2", "/b/output2"] {
            assert!(!intersects_endpoints(Path::new(path), context, outbox));
        }
    }
}

pub fn validate_volume(path: &Selected) -> Result<()> {
    inspect_mounts(|mount, local| {
        if !local && path.as_path().starts_with(mount) {
            Err(LocalError::LocalVolumeRequired)
        } else {
            Ok(())
        }
    })
}

#[cfg(target_os = "linux")]
mod linux;
#[cfg(target_os = "linux")]
use linux::inspect_mounts;
#[cfg(target_os = "macos")]
mod darwin;
#[cfg(target_os = "macos")]
use darwin::inspect_mounts;

#[cfg(not(any(target_os = "linux", target_os = "macos")))]
fn inspect_mounts(_: impl FnMut(&Path, bool) -> Result<()>) -> Result<()> {
    Err(LocalError::UnsupportedPlatform)
}
