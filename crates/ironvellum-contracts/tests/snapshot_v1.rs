use ironvellum_contracts::{Refusal, Value, encode, snapshot::Snapshot};
use serde_json::json;
const RAW: &[u8] = include_bytes!("../../../tools/local_connector/contracts/fixtures/context.json");
#[test]
fn original_bytes_metadata_detachment_and_manifest_order() {
    let s = Snapshot::from_bytes(RAW).unwrap();
    assert_eq!(
        s.sha256(),
        "e6de41c8ac4a11c1d25b21ece9eac40e761b8e430944887c2c98f276eab6cd3e"
    );
    let mut doc: Value = serde_json::from_slice(RAW).unwrap();
    assert_eq!(s.metadata(0)["ageMs"], 0);
    let key = doc["workouts"][0]["id"].as_str().unwrap();
    let mut w = s.workout(key).unwrap();
    w["sets"] = json!([]);
    assert_ne!(s.workout(key).unwrap(), w);
    assert_eq!(s.search_movements("", 584).as_array().unwrap().len(), 584);
    let manifest = ironvellum_contracts::schema::resource("movements-v1.json").unwrap();
    for (r, m) in s
        .search_movements("", 584)
        .as_array()
        .unwrap()
        .iter()
        .zip(manifest["movements"].as_array().unwrap())
    {
        assert_eq!(r["key"], m["key"]);
    }
    doc["workouts"][0]["sets"][0]["order"] = json!(0.0);
    assert!(Snapshot::from_bytes(&encode(&doc).unwrap()).is_ok());
}
#[test]
fn maximum_history_and_decimal_volume() {
    let mut d: Value = serde_json::from_slice(RAW).unwrap();
    d["workouts"][0]["sets"][0]["reps"] = json!(3);
    d["workouts"][0]["sets"][0]["loadKg"] = json!(0.1);
    d["progress"]["lifts"][0]["maxReps"] = json!(3);
    d["progress"]["lifts"][0]["maxLoadKg"] = json!(0.1);
    d["progress"]["completedReps"] = json!(8);
    d["progress"]["externalVolumeKg"] = json!(0.3);
    assert!(Snapshot::from_bytes(&encode(&d).unwrap()).is_ok());
    d["progress"]["externalVolumeKg"] = json!(0.1 * 3.0);
    assert!(Snapshot::from_bytes(&encode(&d).unwrap()).is_err());
    let mut w = d["workouts"][0].clone();
    w["sets"] = json!(
        (0..200)
            .map(
                |order| json!({"order":order,"exerciseKey":"bench press","reps":1000,"loadKg":1000})
            )
            .collect::<Vec<_>>()
    );
    d["workouts"] = json!(
        (0..100)
            .map(|i| {
                let mut r = w.clone();
                r["id"] = json!(format!("local:00000000-0000-4000-8000-{i:012x}"));
                r
            })
            .collect::<Vec<_>>()
    );
    for (k, n) in [
        ("workoutsIncluded", 100),
        ("workoutsTruncated", 1),
        ("setsIncluded", 20000),
        ("setsTruncated", 30),
    ] {
        d["coverage"][k] = json!(n);
    }
    for (k, n) in [
        ("completedWorkouts", 100),
        ("completedSets", 20000),
        ("completedReps", 20000000),
        ("externalVolumeKg", 20000000000u64),
    ] {
        d["progress"][k] = json!(n);
    }
    d["progress"]["lifts"] = json!([{"exerciseKey":"bench press","completedSets":20000,"maxLoadKg":1000,"maxReps":1000}]);
    let s = Snapshot::from_bytes(&encode(&d).unwrap()).unwrap();
    let p = s.page("workouts", &json!({"limit":50})).unwrap();
    assert_eq!(p["workouts"].as_array().unwrap().len(), 50);
    let last = s
        .page("workouts", &json!({"limit":50,"cursor":p["nextCursor"]}))
        .unwrap();
    assert_eq!(last["workouts"].as_array().unwrap().len(), 50);
    assert!(last["nextCursor"].is_null());
}
#[test]
fn rejects_inconsistent_legacy_files() {
    let base: Value = serde_json::from_slice(RAW).unwrap();
    for (path, value) in [
        ("/schemaVersion", json!(true)),
        ("/schemaVersion", json!(1.0)),
        ("/exportedAtMs", json!(1700001000000.0)),
        ("/coverage/setsIncluded", json!(1)),
        ("/coverage/workoutsTruncated", json!(2)),
        ("/coverage/setsTruncated", json!(1)),
        ("/workouts/0/sets/0/order", json!(2)),
        ("/workouts/0/sets/0/exerciseKey", json!("assisted dip")),
        (
            "/savedWorkouts/0/entries/0/exerciseKey",
            json!("assisted chin-up"),
        ),
        ("/savedWorkouts/0/id", base["workouts"][0]["id"].clone()),
        ("/savedWorkouts/0/name", json!("invalid ")),
        ("/progress/externalVolumeKg", json!(98.76543)),
        ("/catalogue/sha256", json!("a".repeat(64))),
        ("/workouts/0/startedAtMs", base["exportedAtMs"].clone()),
        ("/savedWorkouts/0/entries/0/loadKg", json!(true)),
    ] {
        let mut d = base.clone();
        *d.pointer_mut(path).unwrap() = value;
        assert!(
            Snapshot::from_bytes(&serde_json::to_vec(&d).unwrap()).is_err(),
            "{path}"
        );
    }
    assert_eq!(
        Snapshot::from_bytes(RAW).unwrap().workout("missing"),
        Err(Refusal::NotFound)
    );
}
