use crate::{
    Refusal, Value,
    catalogue::MOVEMENTS,
    schema,
    set_scheme::{check_scheme, trim_kotlin},
};
use bigdecimal::{BigDecimal, ToPrimitive};
use serde_json::json;
use std::collections::{BTreeMap, HashMap, HashSet};

type Result<T> = std::result::Result<T, Refusal>;
pub(crate) fn require(ok: bool) -> Result<()> {
    ok.then_some(()).ok_or(Refusal::InvalidData)
}
// These accessors operate only on documents that passed the embedded schema.
pub(crate) fn rows<'a>(v: &'a Value, k: &str) -> &'a [Value] {
    v[k].as_array().expect("validated array")
}
pub(crate) fn string<'a>(v: &'a Value, k: &str) -> &'a str {
    v[k].as_str().expect("validated string")
}
fn decimal(v: &Value) -> BigDecimal {
    schema::exact(v.as_number().expect("validated number")).expect("validated decimal")
}
pub(crate) fn integer(v: &Value) -> u64 {
    decimal(v).to_u64().expect("bounded nonnegative integer")
}
fn equal(a: &Value, b: &Value) -> bool {
    match (a, b) {
        (Value::Number(_), Value::Number(_)) => decimal(a) == decimal(b),
        (Value::Object(x), Value::Object(y)) => {
            x.len() == y.len() && x.iter().all(|(k, v)| y.get(k).is_some_and(|w| equal(v, w)))
        }
        (Value::Array(x), Value::Array(y)) => {
            x.len() == y.len() && x.iter().zip(y).all(|(v, w)| equal(v, w))
        }
        _ => a == b,
    }
}
pub(crate) fn semantics(name: &str) -> &'static str {
    match trim_kotlin(name).to_lowercase().as_str() {
        "assisted pull-up" | "assisted chin-up" | "assisted dip" => "ASSISTANCE",
        _ => "EXTERNAL",
    }
}

fn indexed<'a>(values: &'a [Value], key: &str) -> Result<BTreeMap<&'a str, &'a Value>> {
    require(
        values
            .windows(2)
            .all(|w| string(&w[0], key) < string(&w[1], key)),
    )?;
    Ok(values.iter().map(|v| (string(v, key), v)).collect())
}
fn target(v: &Value) -> Result<()> {
    let (unit, empty) = match string(v, "metric") {
        "REPS" => ("REPS", "distanceKm"),
        "HOLD" => ("SECONDS", "distanceKm"),
        "DURATION" => ("MINUTES", "distanceKm"),
        "DISTANCE_TIME" => ("UNUSED", "weightKg"),
        _ => ("ATTEMPTS", "distanceKm"),
    };
    require(v["unit"] == unit && v[empty].is_null())
}
fn ordered(items: &[Value], primary: &str, secondary: Option<&str>) -> Result<()> {
    let mut previous = None;
    let mut groups = HashSet::new();
    for (index, item) in items.iter().enumerate() {
        let order = (
            integer(&item[primary]),
            secondary.map(|k| integer(&item[k])),
        );
        require(integer(&item["order"]) == index as u64 && previous.is_none_or(|p| p <= order))?;
        previous = Some(order);
        if !item["superset"].is_null() {
            let group = integer(&item["superset"]);
            let n = groups.len();
            require(!groups.insert(group) || group == n as u64)?;
        }
    }
    Ok(())
}
fn prep_progress(prep: &Value) -> Result<()> {
    let steps = rows(prep, "steps");
    let p = &prep["progress"];
    let prefix = integer(&prep["legacyStepCount"]) as usize;
    require(prefix <= steps.len())?;
    let active = if p["legacyOnly"] == true {
        prefix
    } else {
        steps.len()
    };
    let valid = |s: u64, r: u64, i: u64| {
        s < (active as u64)
            && r < integer(&steps[s as usize]["rounds"])
            && i < if steps[s as usize]["repeat"] == "ONCE" {
                1
            } else {
                2
            }
    };
    let (s, r, i) = (
        integer(&p["stepIndex"]),
        integer(&p["roundIndex"]),
        integer(&p["repeatIndex"]),
    );
    require((s == active as u64 && r == 0 && i == 0) || valid(s, r, i))?;
    let mut seen = HashSet::new();
    for field in ["completedUnitKeys", "skippedUnitKeys"] {
        let mut prev = None;
        for k in rows(p, field) {
            let k = k.as_str().unwrap();
            require(k.len() <= 23 && prev.is_none_or(|v| v <= k) && seen.insert(k))?;
            prev = Some(k);
            let parts = k
                .split(':')
                .map(str::parse::<u64>)
                .collect::<std::result::Result<Vec<_>, _>>()
                .map_err(|_| Refusal::InvalidData)?;
            require(matches!(parts.as_slice(),[s,r,i] if valid(*s,*r,*i)))?;
        }
    }
    Ok(())
}
pub(crate) fn references(doc: &Value) -> Result<()> {
    let moves = indexed(rows(doc, "movements"), "key")?;
    let routines = indexed(rows(doc, "routines"), "id")?;
    let opaque = &schema::resource("context-v2.schema.json").unwrap()["$defs"]["opaqueId"];
    let mut names = HashMap::new();
    for m in moves.values().filter(|m| m["source"] != "HISTORICAL") {
        *names
            .entry(trim_kotlin(string(m, "name")).to_lowercase())
            .or_insert(0) += 1;
    }
    let mut ids = HashSet::new();
    for row in rows(doc, "workouts")
        .iter()
        .chain(rows(doc, "savedWorkouts"))
    {
        require(ids.insert(string(row, "id")))?;
    }
    for key in routines.keys() {
        require(ids.insert(key))?;
    }
    for (&key, &m) in &moves {
        if m["source"] != "CATALOGUE" {
            schema::validate(opaque, &m["key"])?;
            require(ids.insert(key))?;
        }
        if m["source"] == "HISTORICAL" {
            require(m["custom"].is_null() && m["loadSemantics"] == "UNKNOWN")?;
            continue;
        }
        require(
            !m["metric"].is_null()
                && !m["custom"].is_null()
                && m["loadSemantics"] == semantics(string(m, "name")),
        )?;
        if m["source"] == "CATALOGUE" {
            require(
                MOVEMENTS
                    .get(key)
                    .is_some_and(|v| v["metric"] == m["metric"])
                    && m["custom"] == false
                    && names.get(key) == Some(&1)
                    && key == trim_kotlin(string(m, "name")).to_lowercase(),
            )?;
        }
    }
    let mut used_moves = HashSet::new();
    let mut used_routines = HashSet::new();
    let mut missing = HashMap::new();
    let mut movement = |key: &str, frozen: bool| -> Result<&Value> {
        let (&key, &m) = moves.get_key_value(key).ok_or(Refusal::InvalidData)?;
        let first = used_moves.insert(key);
        require(m["source"] != "HISTORICAL" || (frozen && first))?;
        Ok(m)
    };
    let mut routine = |key: &str, kind: &str, frozen: bool| -> Result<()> {
        if let Some((&key, &r)) = routines.get_key_value(key) {
            used_routines.insert(key);
            require(r["kind"] == kind)
        } else {
            require(
                frozen
                    && !ids.contains(key)
                    && missing
                        .entry(key.to_owned())
                        .or_insert_with(|| kind.to_owned())
                        == kind,
            )
        }
    };
    fn steps<'a>(
        items: &[Value],
        frozen: bool,
        movement: &mut impl FnMut(&str, bool) -> Result<&'a Value>,
    ) -> Result<()> {
        for s in items {
            require(!trim_kotlin(string(s, "name")).is_empty())?;
            let m = movement(string(s, "exerciseKey"), frozen)?;
            let legacy = &s["legacyTarget"];
            if !legacy.is_null() {
                target(legacy)?;
            }
            require(
                m["source"] != "HISTORICAL"
                    || (m["name"] == s["name"] && m["metric"] == legacy["metric"]),
            )?;
        }
        Ok(())
    }
    for w in rows(doc, "workouts") {
        ordered(rows(w, "sets"), "exercisePosition", Some("setIndex"))?;
        for s in rows(w, "sets") {
            movement(string(s, "exerciseKey"), false)?;
        }
        let mut kinds = HashSet::new();
        for prep in rows(w, "preparations") {
            let kind = string(prep, "kind");
            require(kinds.insert(kind))?;
            if let Some(key) = prep["sourceRoutineId"].as_str() {
                routine(key, kind, true)?;
            }
            steps(rows(prep, "steps"), true, &mut movement)?;
            prep_progress(prep)?;
        }
    }
    for saved in rows(doc, "savedWorkouts") {
        for (field, kind) in [("warmup", "WARMUP"), ("cooldown", "COOLDOWN")] {
            let c = &saved[field];
            let id = c["routineId"].as_str();
            require((c["mode"] == "SAVED") == id.is_some())?;
            if let Some(id) = id {
                routine(id, kind, false)?;
            }
        }
        ordered(rows(saved, "entries"), "position", None)?;
        for e in rows(saved, "entries") {
            let m = movement(string(e, "exerciseKey"), false)?;
            require(m["metric"] == e["target"]["metric"])?;
            target(&e["target"])?;
            check_scheme(&e["schemeRaw"], &e["scheme"])?;
        }
    }
    require(used_routines.len() == routines.len())?;
    for r in routines.values() {
        require(!trim_kotlin(string(r, "name")).is_empty())?;
        steps(rows(r, "steps"), false, &mut movement)?;
    }
    require(used_moves.len() == moves.len())
}
#[derive(Default)]
struct Totals {
    sets: u64,
    reps: u64,
    volume: BigDecimal,
    hold: u64,
    activity: u64,
    distance: BigDecimal,
    attempts: u64,
}
fn rounded(d: &BigDecimal) -> Result<Value> {
    // Parsing the entire decimal rounds once to nearest-even, including subnormals.
    let n = d
        .to_string()
        .parse::<f64>()
        .map_err(|_| Refusal::InvalidData)?;
    require(n.is_finite())?;
    crate::wire::binary64(n)
}
impl Totals {
    fn record(&mut self, r: &Value, m: &Value) {
        self.sets += 1;
        let n = integer(&r["recordedReps"]);
        let duration = || {
            if r["durationSec"].is_null() {
                0
            } else {
                integer(&r["durationSec"])
            }
        };
        match string(m, "metric") {
            "REPS" => {
                self.reps += n;
                if m["loadSemantics"] == "EXTERNAL" && !r["weightKg"].is_null() {
                    self.volume += decimal(&r["weightKg"]) * BigDecimal::from(n);
                }
            }
            "HOLD" => {
                self.hold += if r["durationSec"].is_null() {
                    n
                } else {
                    duration()
                }
            }
            "ATTEMPTS_GRADE" => self.attempts += n,
            _ => {
                self.activity += duration();
                if m["metric"] == "DISTANCE_TIME" && !r["distanceM"].is_null() {
                    self.distance += decimal(&r["distanceM"]);
                }
            }
        }
    }
    fn value(&self) -> Result<Value> {
        Ok(
            json!({"completedSets":self.sets,"completedReps":self.reps,"externalVolumeKg":rounded(&self.volume)?,"holdSeconds":self.hold,"activitySeconds":self.activity,"distanceM":rounded(&self.distance)?,"attempts":self.attempts}),
        )
    }
}
fn maximum(current: &mut Value, next: &Value) {
    if !next.is_null() && (current.is_null() || decimal(next) > decimal(current)) {
        *current = next.clone();
    }
}
#[derive(Default)]
struct Lift {
    t: Totals,
    reps: Value,
    external: Value,
    assistance: Value,
}
pub(crate) fn checked_progress(doc: &Value, v2: bool) -> Result<Value> {
    let workouts = rows(doc, "workouts");
    let mut total = Totals::default();
    let mut lifts = BTreeMap::<&str, Lift>::new();
    let moves = if v2 {
        indexed(rows(doc, "movements"), "key")?
    } else {
        BTreeMap::new()
    };
    for w in workouts {
        for r in rows(w, "sets") {
            let key = string(r, "exerciseKey");
            let l = lifts.entry(key).or_default();
            if v2 {
                if r["done"] != true || r["kind"] == "WARMUP" {
                    continue;
                }
                let m = moves[key];
                total.record(r, m);
                l.t.record(r, m);
                if m["metric"] == "REPS" {
                    maximum(&mut l.reps, &json!(integer(&r["recordedReps"])));
                }
                maximum(
                    if m["loadSemantics"] == "ASSISTANCE" {
                        &mut l.assistance
                    } else {
                        &mut l.external
                    },
                    &r["weightKg"],
                );
            } else {
                let n = integer(&r["reps"]);
                total.reps += n;
                total.sets += 1;
                l.t.sets += 1;
                if !r["loadKg"].is_null() {
                    total.volume += decimal(&r["loadKg"]) * BigDecimal::from(n);
                }
                maximum(&mut l.reps, &r["reps"]);
                maximum(&mut l.external, &r["loadKg"]);
            }
        }
    }
    let mut ranked: Vec<_> = lifts.into_iter().collect();
    ranked.sort_by_key(|(_, l)| std::cmp::Reverse(l.t.sets));
    let truncated = ranked.len().saturating_sub(20);
    if !v2 {
        ranked.truncate(20);
    }
    let mut items = Vec::new();
    for (k, l) in ranked {
        let v = if v2 {
            let mut v = l.t.value()?;
            v["exerciseKey"] = json!(k);
            v["maxReps"] = l.reps;
            v["maxExternalLoadKg"] = l.external;
            v["maxAssistanceKg"] = l.assistance;
            v
        } else {
            json!({"exerciseKey":k,"completedSets":l.t.sets,"maxLoadKg":l.external,"maxReps":l.reps})
        };
        items.push(v);
    }
    let first = workouts
        .last()
        .map(|x| x["completedAtMs"].clone())
        .unwrap_or_default();
    let last = workouts
        .first()
        .map(|x| x["completedAtMs"].clone())
        .unwrap_or_default();
    // The MCP text result preserves the original layout of computed progress.
    let val = if v2 {
        let mut val = total.value()?;
        val["version"] = json!(2);
        val["scope"] = json!("completed-workouts");
        val["completedWorkouts"] = json!(workouts.len());
        val["firstCompletedAtMs"] = first;
        val["lastCompletedAtMs"] = last;
        val["lifts"] = Value::Array(items);
        val
    } else {
        json!({"version":1,"scope":"included-workouts","completedWorkouts":workouts.len(),
            "completedSets":total.sets,"completedReps":total.reps,"externalVolumeKg":rounded(&total.volume)?,
            "lifts":items,"liftsTruncated":truncated,"firstCompletedAtMs":first,"lastCompletedAtMs":last})
    };
    let mut claim = doc["progress"].clone();
    let round_claim = |c: &mut Value| -> Result<()> {
        for field in ["externalVolumeKg", "distanceM"] {
            if let Some(n) = c.get_mut(field) {
                *n = rounded(&decimal(n))?;
            }
        }
        Ok(())
    };
    round_claim(&mut claim)?;
    if v2 {
        for l in claim["lifts"].as_array_mut().unwrap() {
            round_claim(l)?;
        }
    }
    require(equal(&claim, &val))?;
    Ok(val)
}
