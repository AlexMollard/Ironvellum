use crate::{
    Refusal, Value,
    catalogue::{MOVEMENTS, check_entries},
    complete_context::{checked_progress, integer, references, require, rows, semantics, string},
    pages::bounded_page,
    schema, wire,
};
use bigdecimal::ToPrimitive;
use caseless::default_case_fold_str;
use serde_json::json;
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, BTreeSet, HashSet};

type Result<T> = std::result::Result<T, Refusal>;
pub struct Snapshot {
    doc: Value,
    hash: String,
    version: u8,
    movements: Vec<Value>,
    routines: BTreeMap<String, usize>,
}
fn id(v: &Value) -> &str {
    string(v, "id")
}
fn timestamp(n: &Value) -> bool {
    n.as_number().is_some_and(wire::lexical_integer)
}
fn header(row: &Value, field: &str, count: &str) -> Value {
    let mut v = row
        .as_object()
        .unwrap()
        .iter()
        .filter(|(k, _)| k.as_str() != field)
        .map(|(k, v)| (k.clone(), v.clone()))
        .collect::<serde_json::Map<_, _>>();
    v.insert(count.to_owned(), json!(rows(row, field).len()));
    Value::Object(v)
}
impl Snapshot {
    pub fn from_bytes(raw: &[u8]) -> Result<Self> {
        let mut doc = wire::loads(raw, wire::MAX_CONTEXT, 16)?;
        let version = doc["schemaVersion"]
            .as_u64()
            .filter(|v| matches!(v, 1 | 2))
            .ok_or(Refusal::InvalidData)? as u8;
        schema::validate_document(
            if version == 1 {
                "context-v1"
            } else {
                "context-v2"
            },
            &doc,
        )?;
        let workouts = rows(&doc, "workouts");
        let saved = rows(&doc, "savedWorkouts");
        let cov = &doc["coverage"];
        require(timestamp(&doc["exportedAtMs"]))?;
        let mut ids = HashSet::new();
        for w in workouts.iter().chain(saved) {
            require(ids.insert(id(w)))?;
        }
        for w in workouts {
            require(
                timestamp(&w["startedAtMs"])
                    && timestamp(&w["completedAtMs"])
                    && integer(&w["startedAtMs"]) <= integer(&w["completedAtMs"]),
            )?;
        }
        require(workouts.windows(2).all(|p| {
            (
                std::cmp::Reverse(integer(&p[0]["completedAtMs"])),
                id(&p[0]),
            ) <= (
                std::cmp::Reverse(integer(&p[1]["completedAtMs"])),
                id(&p[1]),
            )
        }))?;
        require(saved.windows(2).all(|p| id(&p[0]) < id(&p[1])))?;
        require(
            integer(&cov["workoutsIncluded"]) == workouts.len() as u64
                && integer(&cov["savedWorkoutsIncluded"]) == saved.len() as u64
                && integer(&cov["setsIncluded"])
                    == workouts
                        .iter()
                        .map(|w| rows(w, "sets").len() as u64)
                        .sum::<u64>(),
        )?;
        for f in ["firstCompletedAtMs", "lastCompletedAtMs"] {
            let n = &doc["progress"][f];
            require(n.is_null() || timestamp(n))?;
        }
        if version == 2 {
            references(&doc)?;
        } else {
            for w in workouts {
                let sets = rows(w, "sets");
                require(
                    sets.iter()
                        .enumerate()
                        .all(|(i, s)| integer(&s["order"]) == i as u64),
                )?;
                check_entries(sets)?;
            }
            for s in saved {
                check_entries(rows(s, "entries"))?;
            }
            require(integer(&cov["workoutsTruncated"]) == 0 || workouts.len() == 100)?;
            require(integer(&cov["savedWorkoutsTruncated"]) == 0 || saved.len() == 50)?;
            require(
                integer(&cov["setsTruncated"]) == 0
                    || workouts.iter().any(|w| rows(w, "sets").len() == 200),
            )?;
            require(!workouts.is_empty() || integer(&cov["setsUnsupported"]) == 0)?;
        }
        doc["progress"] = checked_progress(&doc, version == 2)?;
        let manifest = schema::resource("movements-v1.json").unwrap();
        let mut movements: Vec<_> = rows(manifest, "movements")
            .iter()
            .map(|m| MOVEMENTS.get(string(m, "key")).unwrap().clone())
            .collect();
        let mut routines = BTreeMap::new();
        if version == 2 {
            let mut registry = BTreeMap::new();
            for mut m in movements {
                m["loadSemantics"] = json!(semantics(string(&m, "name")));
                m["custom"] = json!(false);
                m["source"] = json!("MANIFEST");
                registry.insert(string(&m, "key").to_owned(), m);
            }
            for r in rows(&doc, "movements") {
                let mut m = r.clone();
                m["draftable"] = json!(
                    m["source"] == "CATALOGUE" && crate::catalogue::draftable(string(&m, "key"))
                );
                registry.insert(string(&m, "key").to_owned(), m);
            }
            movements = registry.into_values().collect();
            for (i, r) in rows(&doc, "routines").iter().enumerate() {
                routines.insert(id(r).to_owned(), i);
            }
        }
        Ok(Self {
            doc,
            hash: format!("{:x}", Sha256::digest(raw)),
            version,
            movements,
            routines,
        })
    }
    pub fn version(&self) -> u8 {
        self.version
    }
    pub fn sha256(&self) -> &str {
        &self.hash
    }
    pub fn dataset(&self) -> &str {
        string(&self.doc, "datasetId")
    }
    pub fn snapshot(&self) -> &str {
        string(&self.doc, "snapshotId")
    }
    pub fn metadata(&self, now: u64) -> Value {
        let mut v = json!({"datasetId":self.dataset(),"snapshotId":self.snapshot(),"exportedAtMs":self.doc["exportedAtMs"],"coverage":self.doc["coverage"],"ageMs":now.saturating_sub(integer(&self.doc["exportedAtMs"]))});
        if self.version == 2 {
            v["schemaVersion"] = json!(2);
        }
        v
    }
    pub fn progress(&self) -> Value {
        self.doc["progress"].clone()
    }
    fn parent(&self, kind: &str, key: &str) -> Result<&Value> {
        rows(&self.doc, kind)
            .iter()
            .find(|r| id(r) == key)
            .ok_or(Refusal::NotFound)
    }
    pub fn workout(&self, key: &str) -> Result<Value> {
        self.parent("workouts", key).cloned()
    }
    fn referenced<'a>(&self, refs: impl Iterator<Item = &'a str>) -> Vec<Value> {
        refs.collect::<BTreeSet<_>>()
            .iter()
            .filter_map(|k| self.routines.get(*k))
            .map(|i| self.doc["routines"][*i].clone())
            .collect()
    }
    pub fn workout_result(&self, key: &str) -> Result<Value> {
        let w = self.parent("workouts", key)?;
        let mut result = json!({"workout":w});
        if self.version == 2 {
            result["routines"] = json!(
                self.referenced(
                    rows(w, "preparations")
                        .iter()
                        .filter_map(|p| p["sourceRoutineId"].as_str())
                )
            );
        }
        Ok(result)
    }
    pub fn search_movements(&self, query: &str, limit: usize) -> Value {
        let q = default_case_fold_str(query);
        let exact = if self.version == 2 {
            self.movements.iter().position(|r| string(r, "key") == q)
        } else {
            None
        };
        let matches = self
            .movements
            .iter()
            .enumerate()
            .filter(|(i, r)| {
                Some(*i) != exact
                    && ["key", "name"]
                        .iter()
                        .any(|k| default_case_fold_str(string(r, k)).contains(&q))
            })
            .map(|(_, r)| r);
        Value::Array(
            exact
                .iter()
                .map(|i| &self.movements[*i])
                .chain(matches)
                .take(limit)
                .cloned()
                .collect(),
        )
    }
    pub fn page(&self, kind: &str, args: &Value) -> Result<Value> {
        require(matches!(kind, "workouts" | "savedWorkouts"))?;
        let list = rows(&self.doc, kind);
        let cursor = |row: &Value| {
            format!(
                "{:x}",
                Sha256::digest(format!("{}/{}/{}", self.hash, kind, id(row)))
            )
        };
        let start = if let Some(c) = args.get("cursor") {
            list.iter()
                .take(list.len().saturating_sub(1))
                .position(|r| cursor(r) == *c)
                .map(|i| i + 1)
                .ok_or(Refusal::InvalidCursor)?
        } else {
            0
        };
        let limit = if let Some(n) = args.get("limit") {
            schema::exact(n.as_number().ok_or(Refusal::InvalidData)?)?
                .to_usize()
                .ok_or(Refusal::InvalidData)?
        } else {
            20usize
        };
        require(limit > 0)?;
        let end = start.saturating_add(limit).min(list.len());
        let part = &list[start..end];
        let mut out = json!({});
        out[kind] = if kind == "workouts" {
            json!(part.iter().map(|r|json!({"id":r["id"],"startedAtMs":r["startedAtMs"],"completedAtMs":r["completedAtMs"],"setCount":rows(r,"sets").len()})).collect::<Vec<_>>())
        } else {
            json!(part)
        };
        out["nextCursor"] = json!(if end < list.len() {
            Some(cursor(&list[end - 1]))
        } else {
            None
        });
        if kind == "savedWorkouts" && self.version == 2 {
            out["routines"] = json!(self.referenced(part.iter().flat_map(|p| {
                ["warmup", "cooldown"]
                    .into_iter()
                    .filter_map(|f| p[f]["routineId"].as_str())
            })));
        }
        Ok(out)
    }
    pub fn collection(
        &self,
        tool: &str,
        args: &Value,
        metadata: &Value,
        request_id: &Value,
    ) -> Result<Value> {
        require(self.version == 2)?;
        require(matches!(
            tool,
            "get_workout" | "get_progress" | "list_saved_workouts"
        ))?;
        let view = args["view"].as_str().ok_or(Refusal::InvalidData)?;
        let owned;
        let list: &[Value];
        let mut project: fn(&Value) -> Value = Value::clone;
        if tool == "get_progress" {
            if view == "lifts" {
                list = rows(&self.doc["progress"], "lifts");
            } else {
                require(view == "totals")?;
                let v = Value::Object(
                    self.doc["progress"]
                        .as_object()
                        .unwrap()
                        .iter()
                        .filter(|(field, _)| field.as_str() != "lifts")
                        .map(|(field, value)| (field.clone(), value.clone()))
                        .collect(),
                );
                owned = vec![v];
                list = &owned;
            }
        } else if view == "headers" {
            require(tool == "list_saved_workouts")?;
            list = rows(&self.doc, "savedWorkouts");
            project = |r| header(r, "entries", "entryCount");
        } else {
            let saved = tool == "list_saved_workouts";
            let p = self.parent(
                if saved { "savedWorkouts" } else { "workouts" },
                args["id"].as_str().ok_or(Refusal::InvalidData)?,
            )?;
            match view {
                "entries" if saved => list = rows(p, "entries"),
                "sets" if !saved => list = rows(p, "sets"),
                "preparations" if !saved => {
                    list = rows(p, "preparations");
                    project = |r| {
                        let mut h = header(r, "steps", "stepCount");
                        h["progress"] = header(
                            &header(&r["progress"], "completedUnitKeys", "completedUnitCount"),
                            "skippedUnitKeys",
                            "skippedUnitCount",
                        );
                        h
                    };
                }
                "routines" | "routineSteps" => {
                    let refs = if saved {
                        ["warmup", "cooldown"]
                            .iter()
                            .filter_map(|f| p[f]["routineId"].as_str())
                            .collect::<Vec<_>>()
                    } else {
                        rows(p, "preparations")
                            .iter()
                            .filter_map(|v| v["sourceRoutineId"].as_str())
                            .collect()
                    };
                    if view == "routines" {
                        owned = self.referenced(refs.into_iter());
                        list = &owned;
                        project = |r| header(r, "steps", "stepCount");
                    } else {
                        let k = args["routineId"].as_str().ok_or(Refusal::InvalidData)?;
                        let i = self
                            .routines
                            .get(k)
                            .filter(|_| refs.contains(&k))
                            .ok_or(Refusal::NotFound)?;
                        list = rows(&self.doc["routines"][*i], "steps");
                    }
                }
                "preparationSteps" | "completedUnitKeys" | "skippedUnitKeys" if !saved => {
                    let prep = rows(p, "preparations")
                        .iter()
                        .find(|v| v["kind"] == args["kind"])
                        .ok_or(Refusal::NotFound)?;
                    list = if view == "preparationSteps" {
                        rows(prep, "steps")
                    } else {
                        rows(&prep["progress"], view)
                    };
                }
                _ => return Err(Refusal::InvalidData),
            }
        }
        bounded_page(&self.hash, tool, args, metadata, list, project, request_id)
    }
}
