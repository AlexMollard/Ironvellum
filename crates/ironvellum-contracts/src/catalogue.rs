use crate::{Refusal, Value};
use sha2::{Digest, Sha256};
use std::{collections::HashMap, sync::LazyLock};

pub struct Catalogue {
    pub digest: String,
    rows: HashMap<String, Value>,
}
impl Catalogue {
    pub fn get(&self, key: &str) -> Option<&Value> {
        self.rows.get(key)
    }
    pub fn len(&self) -> usize {
        self.rows.len()
    }
    pub fn is_empty(&self) -> bool {
        self.rows.is_empty()
    }
}

pub static MOVEMENTS: LazyLock<Catalogue> = LazyLock::new(|| {
    let manifest = crate::schema::resource("movements-v1.json").unwrap();
    let policy = crate::schema::resource("draft-movement-policy-v1.json").unwrap();
    let mut digest = Sha256::new();
    digest.update(b"ironvellum-movements-v1\0");
    let mut rows = HashMap::new();
    for row in manifest["movements"].as_array().unwrap() {
        for field in ["key", "name", "metric"] {
            digest.update(row[field].as_str().unwrap().as_bytes());
            digest.update([0]);
        }
        let mut row = row.clone();
        let allowed = row["metric"] == policy["requiredMetric"]
            && !policy["excludedKeys"]
                .as_array()
                .unwrap()
                .contains(&row["key"]);
        row["draftable"] = Value::Bool(allowed);
        assert!(
            rows.insert(row["key"].as_str().unwrap().to_owned(), row)
                .is_none()
        );
    }
    let digest = format!("{:x}", digest.finalize());
    assert_eq!(rows.len(), 584);
    assert_eq!(
        crate::schema::resource("files-v1.schema.json").unwrap()["$defs"]["catalogue"]["properties"]
            ["sha256"]["const"],
        digest
    );
    Catalogue { rows, digest }
});
pub fn draftable(key: &str) -> bool {
    MOVEMENTS.get(key).is_some_and(|r| r["draftable"] == true)
}
pub fn check_entries(entries: &[Value]) -> Result<(), Refusal> {
    if entries
        .iter()
        .all(|e| e["exerciseKey"].as_str().is_some_and(draftable))
    {
        Ok(())
    } else {
        Err(Refusal::UnsupportedMovement)
    }
}
