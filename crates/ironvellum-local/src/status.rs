use crate::{Identity, LocalError, Selected};
use ironvellum_contracts::encode;
use serde_json::json;
use sha2::{Digest, Sha256};
use std::path::PathBuf;

fn spelling(path: &Selected) -> PathBuf {
    // Components preserve native spelling and omit redundant separators and dots.
    path.as_path().components().collect()
}

#[cfg(windows)]
fn normalized_bytes(path: PathBuf) -> Result<Vec<u8>, LocalError> {
    use std::{
        ffi::OsString,
        os::windows::ffi::{OsStrExt, OsStringExt},
        ptr::{null, null_mut},
    };
    use windows_sys::Win32::Globalization::{LCMAP_LOWERCASE, LCMapStringEx};
    let wide: Vec<_> = path.as_os_str().encode_wide().collect();
    let len = i32::try_from(wide.len()).map_err(|_| LocalError::InvalidPath)?;
    let map = |dst, count| unsafe {
        LCMapStringEx(
            [0u16].as_ptr(),
            LCMAP_LOWERCASE,
            wide.as_ptr(),
            len,
            dst,
            count,
            null(),
            null(),
            0,
        )
    };
    let size = map(null_mut(), 0);
    if size <= 0 {
        return Err(LocalError::InvalidPath);
    }
    let mut result = vec![0; size as usize];
    if map(result.as_mut_ptr(), size) != size {
        return Err(LocalError::InvalidPath);
    }
    // Rust's Windows OsStr encoded representation is WTF-8, including lone surrogates.
    Ok(OsString::from_wide(&result).as_encoded_bytes().to_vec())
}

pub fn rendezvous_name(path: &Selected) -> Result<String, LocalError> {
    let native = spelling(path);
    #[cfg(windows)]
    let digest = Sha256::digest(normalized_bytes(native)?);
    #[cfg(unix)]
    let digest = Sha256::digest(std::os::unix::ffi::OsStrExt::as_bytes(native.as_os_str()));
    Ok(format!(
        ".ironvellum-sessions-{}",
        &format!("{digest:x}")[..24]
    ))
}

pub fn endpoint_digest(
    paths: [&Selected; 3],
    identities: [Identity; 3],
) -> Result<String, LocalError> {
    let mut rows = Vec::with_capacity(3);
    for (path, id) in paths.into_iter().zip(identities) {
        #[cfg(windows)]
        let native = json!([id.volume, id.file >> 32, id.file & 0xffff_ffff]);
        #[cfg(unix)]
        let native = json!([id.volume, id.file]);
        let text = spelling(path);
        rows.push(json!([
            text.to_str().ok_or(LocalError::InvalidPath)?,
            native
        ]));
    }
    Ok(format!("{:x}", Sha256::digest(encode(&rows.into())?)))
}

use crate::{SourceSummary, valid_dataset_id};
use LocalError::InvalidSession;
use ironvellum_contracts::{
    Value, loads,
    wire::{MAX_DEPTH, MAX_TIME, lexical_integer},
};

pub const SESSION_BYTES: usize = 8192;
fn lower_hex(value: &str, length: usize) -> bool {
    value.len() == length
        && value
            .bytes()
            .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
}
pub fn session_id(name: &str) -> Option<&str> {
    name.strip_suffix(".json").filter(|id| lower_hex(id, 32))
}

fn fields(value: &Value, keys: &[&str]) -> Result<(), LocalError> {
    if value
        .as_object()
        .is_some_and(|map| map.len() == keys.len() && keys.iter().all(|k| map.contains_key(*k)))
    {
        Ok(())
    } else {
        Err(InvalidSession)
    }
}
fn time(value: &Value, min: u64) -> Result<u64, LocalError> {
    value
        .as_u64()
        .filter(|v| (min..=MAX_TIME).contains(v))
        .ok_or(InvalidSession)
}

/// Validated legacy metadata. Coverage can exceed the native integer width in existing records.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct LoadedSource(Value);
impl LoadedSource {
    pub fn from_snapshot(
        snapshot: &ironvellum_contracts::snapshot::Snapshot,
    ) -> Result<Self, LocalError> {
        Self::from_summary(&SourceSummary::new(snapshot))
    }
    pub fn from_summary(s: &SourceSummary) -> Result<Self, LocalError> {
        Self::parse(
            json!({"datasetId":s.dataset_id,"snapshotId":s.snapshot_id,"sourceSha256":s.sha256,
          "exportedAtMs":s.exported_at_ms,"coverage":{"workoutsIncluded":s.workouts_included,
          "setsIncluded":s.sets_included,"savedWorkoutsIncluded":s.saved_workouts_included}}),
        )
    }
    pub fn value(&self) -> &Value {
        &self.0
    }
    /// Typed coverage and identity of the export actually loaded by this session.
    pub fn summary(&self) -> Result<SourceSummary, LocalError> {
        let v = &self.0;
        Ok(SourceSummary {
            dataset_id: v["datasetId"].as_str().expect("validated dataset").into(),
            snapshot_id: v["snapshotId"].as_str().expect("validated snapshot").into(),
            sha256: v["sourceSha256"].as_str().expect("validated hash").into(),
            exported_at_ms: v["exportedAtMs"].as_u64().expect("validated export time"),
            workouts_included: v["coverage"]["workoutsIncluded"]
                .as_u64()
                .ok_or(InvalidSession)?,
            sets_included: v["coverage"]["setsIncluded"]
                .as_u64()
                .ok_or(InvalidSession)?,
            saved_workouts_included: v["coverage"]["savedWorkoutsIncluded"]
                .as_u64()
                .ok_or(InvalidSession)?,
        })
    }
    fn parse(v: Value) -> Result<Self, LocalError> {
        fields(
            &v,
            &[
                "datasetId",
                "snapshotId",
                "sourceSha256",
                "exportedAtMs",
                "coverage",
            ],
        )?;
        for k in ["datasetId", "snapshotId"] {
            if !v[k].as_str().is_some_and(valid_dataset_id) {
                return Err(InvalidSession);
            }
        }
        if !v["sourceSha256"].as_str().is_some_and(|s| lower_hex(s, 64)) {
            return Err(InvalidSession);
        }
        time(&v["exportedAtMs"], 0)?;
        let keys = ["workoutsIncluded", "setsIncluded", "savedWorkoutsIncluded"];
        fields(&v["coverage"], &keys)?;
        for k in keys {
            if !v["coverage"][k]
                .as_number()
                .is_some_and(|n| lexical_integer(n) && !n.as_str().starts_with('-'))
            {
                return Err(InvalidSession);
            }
        }
        Ok(Self(v))
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SessionRecord {
    pub time_ms: u64,
    pub endpoints: String,
    pub initialized: bool,
    pub tools_listed: bool,
    pub status_checked: bool,
    pub loaded_source: Option<LoadedSource>,
    pub reload_required: bool,
}
impl SessionRecord {
    pub fn verified(&self) -> bool {
        self.initialized && self.tools_listed && self.status_checked
    }
    pub fn decode(raw: &[u8]) -> Result<Self, LocalError> {
        let mut value = loads(raw, SESSION_BYTES, MAX_DEPTH)?;
        fields(
            &value,
            &[
                "initialized",
                "timeMs",
                "endpoints",
                "toolsListed",
                "statusChecked",
                "loadedSource",
                "reloadRequired",
            ],
        )?;
        let source = value["loadedSource"].take();
        let flag = |k: &str| value[k].as_bool().ok_or(InvalidSession);
        Ok(Self {
            time_ms: time(&value["timeMs"], 1)?,
            endpoints: value["endpoints"]
                .as_str()
                .filter(|s| lower_hex(s, 64))
                .ok_or(InvalidSession)?
                .to_owned(),
            initialized: flag("initialized")?,
            tools_listed: flag("toolsListed")?,
            status_checked: flag("statusChecked")?,
            loaded_source: if source.is_null() {
                None
            } else {
                Some(LoadedSource::parse(source)?)
            },
            reload_required: flag("reloadRequired")?,
        })
    }
    pub fn encode(&self) -> Result<Vec<u8>, LocalError> {
        if !(1..=MAX_TIME).contains(&self.time_ms) || !lower_hex(&self.endpoints, 64) {
            return Err(InvalidSession);
        }
        let bytes = encode(&json!({"initialized":self.initialized,"timeMs":self.time_ms,"endpoints":self.endpoints,
         "toolsListed":self.tools_listed,"statusChecked":self.status_checked,"loadedSource":self.loaded_source.as_ref().map(LoadedSource::value),
         "reloadRequired":self.reload_required})).map_err(|_| InvalidSession)?;
        if bytes.len() > SESSION_BYTES {
            return Err(InvalidSession);
        }
        Ok(bytes)
    }
}

mod runtime;
pub use runtime::{Evidence, Heartbeat, SessionRow, StatusReader, StatusReport};
