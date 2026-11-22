use ironvellum_contracts::{Refusal, snapshot::Snapshot};

const GOOD: &[u8] =
    include_bytes!("../../../tools/local_connector/contracts/fixtures/v2/context.json");
const BAD: &[u8] =
    include_bytes!("../../../tools/local_connector/contracts/fixtures/v2/invalid-progress.json");

#[test]
fn frozen_metric_totals_and_invalid_progress() {
    let source = Snapshot::from_bytes(GOOD).unwrap();
    let p = source.progress();
    assert_eq!(p["completedSets"], 8);
    assert_eq!(p["completedReps"], 12);
    assert_eq!(p["holdSeconds"], 18);
    assert_eq!(p["activitySeconds"], 105);
    assert_eq!(p["distanceM"].as_f64(), Some(103.5));
    assert_eq!(p["attempts"], 3);
    assert_eq!(Snapshot::from_bytes(BAD).err(), Some(Refusal::InvalidData));
}
#[test]
fn computed_lifts_preserve_original_rendered_number_types_and_order() {
    let raw = std::str::from_utf8(GOOD).unwrap().replacen(
        "\"recordedReps\":8",
        "\"recordedReps\":8.00",
        1,
    );
    let s = Snapshot::from_bytes(raw.as_bytes()).unwrap();
    assert_eq!(encode(&s.progress()["lifts"][3]).unwrap(),br#"{"completedSets":1,"completedReps":8,"externalVolumeKg":20.0,"holdSeconds":0,"activitySeconds":0,"distanceM":0.0,"attempts":0,"exerciseKey":"bench press","maxReps":8,"maxExternalLoadKg":2.5,"maxAssistanceKg":null}"#);
}
#[test]
fn inactive_lift_float_zeros_change_the_last_row_that_fits() {
    use ironvellum_contracts::wire::MAX_RESULT;
    let s = Snapshot::from_bytes(GOOD).unwrap();
    let args =
        json!({"view":"lifts","offset":5,"fields":["distanceM","exerciseKey","externalVolumeKg"]});
    let meta = json!({});
    // The oracle page, including its own escaped text copy, contains both
    // generated zero floats with a decimal point. One byte below this boundary
    // leaves the last row for the continuation.
    let payload = r#"{"snapshot":{},"view":"lifts","items":[{"distanceM":103.5,"exerciseKey":"cycling","externalVolumeKg":0.0},{"distanceM":0.0,"exerciseKey":"local:00000000-0000-4000-8000-00000000001e","externalVolumeKg":0.0}],"total":7,"nextCursor":null}"#;
    let envelope = format!(
        r#"{{"jsonrpc":"2.0","id":"","result":{{"content":[{{"type":"text","text":{}}}],"structuredContent":{},"isError":false,"resultType":"complete"}}}}"#,
        serde_json::to_string(payload).unwrap(),
        payload
    );
    let exact = "i".repeat(MAX_RESULT - envelope.len());
    assert_eq!(
        s.collection("get_progress", &args, &meta, &json!(exact))
            .unwrap()["items"]
            .as_array()
            .unwrap()
            .len(),
        2
    );
    let over = format!("{exact}i");
    assert_eq!(
        s.collection("get_progress", &args, &meta, &json!(over))
            .unwrap()["items"]
            .as_array()
            .unwrap()
            .len(),
        1
    );
}
#[test]
fn subnormal_breaks_a_binary64_midpoint_tie_after_exact_accumulation() {
    let mut d = base();
    let halfway: Value =
        serde_json::from_str("1.00000000000000011102230246251565404236316680908203125").unwrap();
    let tiny: Value = serde_json::from_str("5e-324").unwrap();
    let up = json!(1.0000000000000002);
    merge(
        &mut d["workouts"][0]["sets"][0],
        json!({"recordedReps":1,"weightKg":halfway}),
    );
    merge(
        &mut d["workouts"][0]["sets"][7],
        json!({"recordedReps":1,"done":true,"weightKg":tiny}),
    );
    let p = &mut d["progress"];
    merge(
        p,
        json!({"completedSets":9,"completedReps":6,"externalVolumeKg":up}),
    );
    merge(
        &mut p["lifts"][3],
        json!({"completedSets":2,"completedReps":2,"maxReps":1,"maxExternalLoadKg":halfway,"externalVolumeKg":up}),
    );
    p["lifts"].as_array_mut().unwrap().sort_by_key(|l| {
        (
            std::cmp::Reverse(l["completedSets"].as_u64().unwrap()),
            l["exerciseKey"].as_str().unwrap().to_owned(),
        )
    });
    assert!(parse(&d).is_ok());
    d["progress"]["externalVolumeKg"] = json!(1.0);
    assert_eq!(parse(&d).err(), Some(Refusal::InvalidData));
}
#[test]
fn historical_movements_and_missing_routines_remain_frozen_only() {
    let mut d = base();
    let key = opaque(700);
    let step = &mut d["workouts"][0]["preparations"][0]["steps"][0];
    step["exerciseKey"] = json!(key);
    let metric = step["legacyTarget"]["metric"].clone();
    let name = step["name"].clone();
    d["movements"].as_array_mut().unwrap().push(json!({"key":key,"name":name,"metric":metric,"source":"HISTORICAL","loadSemantics":"UNKNOWN","custom":null}));
    d["workouts"][0]["preparations"][0]["sourceRoutineId"] = json!(opaque(800));
    let s = parse(&d).unwrap();
    assert_eq!(s.search_movements(&key, 1)[0]["draftable"], false);
    let detail = s
        .workout_result(d["workouts"][0]["id"].as_str().unwrap())
        .unwrap();
    assert_eq!(detail["routines"], json!([]));
    let mut repeat = d.clone();
    let steps = repeat["workouts"][0]["preparations"][0]["steps"]
        .as_array_mut()
        .unwrap();
    steps.push(steps[0].clone());
    assert_eq!(parse(&repeat).err(), Some(Refusal::InvalidData));
    d["workouts"][0]["sets"][0]["exerciseKey"] = json!(key);
    assert_eq!(parse(&d).err(), Some(Refusal::InvalidData));
}
#[test]
fn collections_expose_all_views_and_source_bound_references() {
    let s = Snapshot::from_bytes(GOOD).unwrap();
    let d = base();
    let w = &d["workouts"][0];
    let saved = &d["savedWorkouts"][0];
    let prep = &w["preparations"][0];
    let routine = &d["routines"][0];
    let meta = s.metadata(300);
    assert_eq!(
        s.workout_result(w["id"].as_str().unwrap()).unwrap(),
        json!({"workout":w,"routines":d["routines"]})
    );
    for (tool, view, id, expected) in [
        ("get_workout", "sets", &w["id"], w["sets"].clone()),
        (
            "get_workout",
            "preparationSteps",
            &w["id"],
            prep["steps"].clone(),
        ),
        (
            "get_workout",
            "completedUnitKeys",
            &w["id"],
            prep["progress"]["completedUnitKeys"].clone(),
        ),
        (
            "get_workout",
            "skippedUnitKeys",
            &w["id"],
            prep["progress"]["skippedUnitKeys"].clone(),
        ),
        (
            "list_saved_workouts",
            "entries",
            &saved["id"],
            saved["entries"].clone(),
        ),
        (
            "get_workout",
            "routineSteps",
            &w["id"],
            routine["steps"].clone(),
        ),
        (
            "list_saved_workouts",
            "routineSteps",
            &saved["id"],
            routine["steps"].clone(),
        ),
    ] {
        let args = json!({"view":view,"id":id,"kind":prep["kind"],"routineId":routine["id"]});
        let page = s
            .collection(tool, &args, &meta, &json!("escaped\\\"id"))
            .unwrap();
        assert_eq!(page["items"], expected, "{view}");
    }
    for (tool, view, id, field, count) in [
        (
            "get_workout",
            "preparations",
            &w["id"],
            "stepCount",
            prep["steps"].as_array().unwrap().len(),
        ),
        (
            "get_workout",
            "routines",
            &w["id"],
            "stepCount",
            routine["steps"].as_array().unwrap().len(),
        ),
        (
            "list_saved_workouts",
            "headers",
            &Value::Null,
            "entryCount",
            saved["entries"].as_array().unwrap().len(),
        ),
    ] {
        let args = json!({"view":view,"id":id,"fields":[field]});
        assert_eq!(
            s.collection(tool, &args, &meta, &json!(1)).unwrap()["items"][0][field],
            count
        );
    }
    let totals = s
        .collection("get_progress", &json!({"view":"totals"}), &meta, &json!(1))
        .unwrap();
    assert!(totals["items"][0].get("lifts").is_none());
    assert_eq!(totals["items"][0]["completedSets"], 8);
    let p = s
        .collection(
            "get_progress",
            &json!({"view":"lifts","limit":1}),
            &meta,
            &json!(1),
        )
        .unwrap();
    let mut different = GOOD.to_vec();
    different.push(b' ');
    let other = Snapshot::from_bytes(&different).unwrap();
    assert_eq!(
        other.collection(
            "get_progress",
            &json!({"view":"lifts","cursor":p["nextCursor"]}),
            &meta,
            &json!(1)
        ),
        Err(Refusal::InvalidCursor)
    );
}
#[test]
fn exact_movement_precedes_name_matches_and_search_casefolds() {
    let mut d = base();
    let plank = json!({"key":"plank","name":"Plank","metric":"HOLD","custom":false,"source":"CATALOGUE","loadSemantics":"EXTERNAL"});
    let sample = d["savedWorkouts"][0]["entries"][0].clone();
    for i in 0..22 {
        let mut m = plank.clone();
        if i > 0 {
            merge(
                &mut m,
                json!({"key":opaque(20000+i),"name":format!("Plank Straße {i}"),"source":"LOCAL","custom":true}),
            );
        }
        let mut e = sample.clone();
        e["order"] = json!(i + 1);
        e["position"] = json!(i + 1);
        e["exerciseKey"] = m["key"].clone();
        e["target"]["metric"] = json!("HOLD");
        e["target"]["unit"] = json!("SECONDS");
        d["savedWorkouts"][0]["entries"]
            .as_array_mut()
            .unwrap()
            .push(e);
        d["movements"].as_array_mut().unwrap().push(m);
    }
    d["movements"]
        .as_array_mut()
        .unwrap()
        .sort_by(|a, b| a["key"].as_str().cmp(&b["key"].as_str()));
    let s = parse(&d).unwrap();
    for n in [1, 20] {
        assert_eq!(s.search_movements("PLANK", n)[0]["key"], "plank");
    }
    assert_eq!(
        s.search_movements("STRASSE", 100).as_array().unwrap().len(),
        21
    );
}
use ironvellum_contracts::{Value, encode};
use serde_json::json;
fn base() -> Value {
    serde_json::from_slice(GOOD).unwrap()
}
fn opaque(i: usize) -> String {
    format!("local:00000000-0000-4000-8000-{i:012x}")
}
fn parse(v: &Value) -> Result<Snapshot, Refusal> {
    Snapshot::from_bytes(&encode(v)?)
}
fn merge(v: &mut Value, updates: Value) {
    for (k, n) in updates.as_object().unwrap() {
        v[k] = n.clone();
    }
}
#[test]
fn complete_counterpart_keeps_all_observations() {
    let mut d = base();
    let sample = d["workouts"][0]["sets"][0].clone();
    for i in 0..18 {
        let count = if i < 11 { 17 } else { 16 };
        let sets=(0..count).map(|j|{let mut s=sample.clone();merge(&mut s,json!({"order":j,"setIndex":j,"exercisePosition":0,"done":false,"superset":null}));s}).collect::<Vec<_>>();
        d["workouts"].as_array_mut().unwrap().push(json!({"id":opaque(1000+i),"startedAtMs":0,"completedAtMs":199-i,"sets":sets,"preparations":[]}));
    }
    for i in 0..6 {
        let mut s = d["savedWorkouts"][0].clone();
        s["id"] = json!(opaque(2000 + i));
        s["name"] = json!("");
        d["savedWorkouts"].as_array_mut().unwrap().push(s);
    }
    merge(
        &mut d["coverage"],
        json!({"workoutsIncluded":19,"setsIncluded":309,"savedWorkoutsIncluded":7}),
    );
    merge(
        &mut d["progress"],
        json!({"completedWorkouts":19,"firstCompletedAtMs":182}),
    );
    let s = parse(&d).unwrap();
    let page = s.page("workouts", &json!({})).unwrap();
    let all = page["workouts"].as_array().unwrap();
    assert_eq!(all.len(), 19);
    assert_eq!(
        all.iter()
            .map(|h| s.workout(h["id"].as_str().unwrap()).unwrap()["sets"]
                .as_array()
                .unwrap()
                .len())
            .sum::<usize>(),
        309
    );
    assert_eq!(
        s.page("savedWorkouts", &json!({})).unwrap()["savedWorkouts"],
        d["savedWorkouts"]
    );
}
#[test]
fn totals_round_once_after_collecting_all_records() {
    let mut d = base();
    for (i, update) in [
        (0, json!({"recordedReps":1,"weightKg":10000000000000000u64})),
        (6, json!({"recordedReps":1,"weightKg":1,"kind":"WORKING"})),
        (7, json!({"recordedReps":1,"weightKg":1,"done":true})),
    ] {
        merge(&mut d["workouts"][0]["sets"][i], update);
    }
    let p = &mut d["progress"];
    merge(
        p,
        json!({"completedSets":10,"completedReps":7,"externalVolumeKg":10000000000000002u64}),
    );
    merge(
        &mut p["lifts"][3],
        json!({"completedSets":2,"completedReps":2,"maxReps":1,"maxExternalLoadKg":10000000000000000u64,"externalVolumeKg":10000000000000000u64}),
    );
    merge(
        p["lifts"].as_array_mut().unwrap().last_mut().unwrap(),
        json!({"completedSets":1,"completedReps":1,"maxReps":1,"maxExternalLoadKg":1,"externalVolumeKg":1}),
    );
    p["lifts"].as_array_mut().unwrap().sort_by_key(|l| {
        (
            std::cmp::Reverse(l["completedSets"].as_u64().unwrap()),
            l["exerciseKey"].as_str().unwrap().to_owned(),
        )
    });
    assert!(parse(&d).is_ok());
    d["progress"]["externalVolumeKg"] = json!(10000000000000000u64);
    assert_eq!(parse(&d).err(), Some(Refusal::InvalidData));
}
#[test]
fn above_old_limits_keeps_all_referenced_movements() {
    let mut d = base();
    let m = d["movements"].as_array().unwrap().last().unwrap().clone();
    let l = d["progress"]["lifts"]
        .as_array()
        .unwrap()
        .last()
        .unwrap()
        .clone();
    let set = d["workouts"][0]["sets"][0].clone();
    for i in 0..5001 {
        let key = opaque(1000 + i);
        let mut movement = m.clone();
        movement["key"] = json!(key);
        d["movements"].as_array_mut().unwrap().push(movement);
        let mut r = set.clone();
        merge(
            &mut r,
            json!({"order":i+10,"exercisePosition":i+10,"exerciseKey":key,"done":false,"superset":null}),
        );
        d["workouts"][0]["sets"].as_array_mut().unwrap().push(r);
        let mut lift = l.clone();
        lift["exerciseKey"] = json!(key);
        d["progress"]["lifts"].as_array_mut().unwrap().push(lift);
    }
    d["movements"]
        .as_array_mut()
        .unwrap()
        .sort_by(|a, b| a["key"].as_str().cmp(&b["key"].as_str()));
    let mut w = d["workouts"][0].clone();
    w["sets"] = json!([]);
    w["preparations"] = json!([]);
    for i in 0..100 {
        w["id"] = json!(opaque(10000 + i));
        d["workouts"].as_array_mut().unwrap().push(w.clone());
    }
    let mut saved = d["savedWorkouts"][0].clone();
    for i in 0..50 {
        saved["id"] = json!(opaque(11000 + i));
        d["savedWorkouts"]
            .as_array_mut()
            .unwrap()
            .push(saved.clone());
    }
    merge(
        &mut d["coverage"],
        json!({"workoutsIncluded":101,"setsIncluded":5011,"savedWorkoutsIncluded":51}),
    );
    d["progress"]["completedWorkouts"] = json!(101);
    let s = parse(&d).unwrap();
    let p = s.page("workouts", &json!({"limit":1})).unwrap();
    assert_eq!(p["workouts"][0]["setCount"], 5011);
    assert_eq!(
        s.page("workouts", &json!({"cursor":p["nextCursor"]}))
            .unwrap()["workouts"][0]["id"],
        opaque(10000)
    );
    assert_eq!(s.progress()["lifts"].as_array().unwrap().len(), 5008);
    assert_eq!(s.metadata(300)["coverage"], d["coverage"]);
}
#[test]
fn rejects_each_inconsistent_v2_claim() {
    let base = base();
    for (path, wrong) in [
        ("/progress/lifts/3/externalVolumeKg", json!(20.1)),
        ("/progress/lifts/0/holdSeconds", json!(17)),
        ("/progress/lifts/2/maxAssistanceKg", Value::Null),
        ("/progress/lifts/4/attempts", json!(2)),
        ("/progress/lifts/5/distanceM", json!(100.5)),
        ("/coverage/setsIncluded", json!(8)),
        ("/movements/2/loadSemantics", json!("EXTERNAL")),
        ("/movements/6/key", json!("custom rings")),
        ("/movements/6/key", json!(opaque(1))),
        ("/workouts/0/sets/0/exerciseKey", json!("not in registry")),
        ("/workouts/0/sets/0/order", json!(1)),
        ("/workouts/0/sets/0/exercisePosition", json!(10)),
        ("/workouts/0/sets/0/superset", json!(1)),
        ("/workouts/0/preparations/0/legacyStepCount", json!(2)),
        ("/workouts/0/preparations/0/progress/repeatIndex", json!(2)),
        (
            "/workouts/0/preparations/0/progress/completedUnitKeys",
            json!(["0:2:0"]),
        ),
        (
            "/workouts/0/preparations/0/progress/completedUnitKeys",
            json!(["0:0:1"]),
        ),
        (
            "/workouts/0/preparations/0/progress/completedUnitKeys",
            json!(["0:1:0", "0:0:0"]),
        ),
        ("/savedWorkouts/0/warmup/routineId", json!(opaque(999))),
        ("/savedWorkouts/0/warmup/mode", json!("AUTO")),
        ("/savedWorkouts/0/entries/0/target/unit", json!("MINUTES")),
        ("/savedWorkouts/0/entries/0/target/metric", json!("HOLD")),
        ("/savedWorkouts/0/entries/0/target/distanceKm", json!(1.2)),
        ("/savedWorkouts/0/entries/0/scheme/dropCount", json!(2)),
        ("/savedWorkouts/0/entries/0/scheme/topRange/min", json!(5)),
        ("/savedWorkouts/0/entries/0/scheme/warmupRamp", json!(0)),
        ("/routines/0/kind", json!("COOLDOWN")),
        ("/routines/0/id", json!(opaque(1))),
        ("/routines/0/name", json!("\t\n")),
        ("/routines/0/steps/0/legacyTarget/weightKg", json!(1.25)),
    ] {
        let mut d = base.clone();
        *d.pointer_mut(path).unwrap() = wrong;
        assert_eq!(parse(&d).err(), Some(Refusal::InvalidData), "{path}");
    }
}
