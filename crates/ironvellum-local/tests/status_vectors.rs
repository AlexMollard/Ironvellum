use ironvellum_local::{Identity, endpoint_digest, rendezvous_name, selected};
use serde_json::Value;
use std::path::Path;

#[test]
fn independent_legacy_vectors() {
    let vectors: Value = serde_json::from_str(include_str!("legacy-status-vectors.json")).unwrap();
    for row in vectors
        .as_array()
        .unwrap()
        .iter()
        .filter(|r| r["platform"] == if cfg!(windows) { "windows" } else { "posix" })
    {
        let path = |p: &Value| selected(Path::new(p.as_str().unwrap())).unwrap();
        let file = path(&row["rawSettingsPath"]);
        assert_eq!(rendezvous_name(&file).unwrap(), row["rendezvousName"]);
        let paths: [_; 3] = std::array::from_fn(|i| path(&row["rawPaths"][i]));
        let ids = std::array::from_fn(|i| {
            let id = row["identities"][i].as_array().unwrap();
            Identity {
                volume: id[0].as_u64().unwrap(),
                file: if id.len() == 2 {
                    id[1].as_u64().unwrap()
                } else {
                    (id[1].as_u64().unwrap() << 32) | id[2].as_u64().unwrap()
                },
            }
        });
        assert_eq!(
            endpoint_digest(paths.each_ref(), ids).unwrap(),
            row["endpointDigest"]
        );
    }
}

#[test]
fn redundant_components_preserve_the_legacy_hashes() {
    let vectors: Value = serde_json::from_str(include_str!("legacy-status-vectors.json")).unwrap();
    for row in vectors
        .as_array()
        .unwrap()
        .iter()
        .filter(|r| r["platform"] == if cfg!(windows) { "windows" } else { "posix" })
    {
        let raw = row["rawSettingsPath"].as_str().unwrap().replace('\\', "/");
        let (head, tail) = raw.rsplit_once('/').unwrap();
        let redundant = format!("{head}/.//{tail}/.");
        assert_eq!(
            rendezvous_name(&selected(Path::new(&redundant)).unwrap()).unwrap(),
            row["rendezvousName"]
        );
    }
}
#[cfg(unix)]
#[test]
fn posix_rendezvous_preserves_non_utf8_settings_bytes() {
    use sha2::{Digest, Sha256};
    use std::{ffi::OsStr, os::unix::ffi::OsStrExt};
    let file = selected(Path::new(OsStr::from_bytes(b"/status/.//x\xff"))).unwrap();
    let hash = format!("{:x}", Sha256::digest(b"/status/x\xff"));
    assert_eq!(
        rendezvous_name(&file).unwrap(),
        format!(".ironvellum-sessions-{}", &hash[..24])
    );
    assert_eq!(
        endpoint_digest([&file; 3], [Identity { volume: 1, file: 2 }; 3]).unwrap_err(),
        ironvellum_local::LocalError::InvalidPath
    );
}
