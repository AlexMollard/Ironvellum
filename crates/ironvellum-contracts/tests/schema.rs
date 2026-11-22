use ironvellum_contracts::{loads, schema::validate_document};
#[test]
fn frozen_fixtures_match_each_version() {
    for (name, good, raw) in [
        (
            "context-v1",
            true,
            include_bytes!("../../../tools/local_connector/contracts/fixtures/context.json")
                .as_slice(),
        ),
        (
            "context-v2",
            true,
            include_bytes!("../../../tools/local_connector/contracts/fixtures/v2/context.json")
                .as_slice(),
        ),
        // Cross-row progress arithmetic is a snapshot responsibility in Task 2.
        (
            "context-v2",
            true,
            include_bytes!(
                "../../../tools/local_connector/contracts/fixtures/v2/invalid-progress.json"
            )
            .as_slice(),
        ),
        (
            "proposal-v1",
            true,
            include_bytes!("../../../tools/local_connector/contracts/fixtures/proposal.json")
                .as_slice(),
        ),
    ] {
        let value = loads(raw, 8 << 20, 32).unwrap();
        assert_eq!(validate_document(name, &value).is_ok(), good, "{name}");
        let mut malformed = value;
        malformed["datasetId"] = serde_json::json!("invalid");
        assert!(validate_document(name, &malformed).is_err());
    }
}
#[test]
fn complete_composed_tools_match_independent_structural_goldens() {
    use ironvellum_contracts::{Value, schema};
    use sha2::{Digest, Sha256};
    let names = schema::resource("tools-v1.json")
        .unwrap()
        .as_object()
        .unwrap();
    // Golden SHA-256 of the complete compact JSON with recursively sorted object
    // keys. Array order (including selectors and required lists) is significant.
    for (version, expected) in [
        (
            1,
            "f1c17a58a09c489df27d4d0e26ffb7832e88322741825a69038b1896e8cdb48c",
        ),
        (
            2,
            "3eccf98e94701572ceac3944c8c973799d9bfcf50f23c4029479d32f483d09f7",
        ),
    ] {
        let mut bundle = Value::Object(
            names
                .keys()
                .map(|name| (name.clone(), schema::tool(version, name).unwrap().clone()))
                .collect(),
        );
        bundle.sort_all_objects();
        assert_eq!(
            format!("{:x}", Sha256::digest(serde_json::to_vec(&bundle).unwrap())),
            expected,
            "v{version}"
        );
    }
}

#[test]
fn views_and_exact_numeric_boundaries() {
    use ironvellum_contracts::schema::{tool, validate};
    let args = &tool(2, "get_workout").unwrap()["inputSchema"];
    let mut v = serde_json::json!({"view":"sets","id":"local:00000000-0000-4000-8000-000000000065","fields":["recordedReps"]});
    assert!(validate(args, &v).is_ok());
    v["limit"] = loads(b"1.000", 32, 1).unwrap();
    assert!(validate(args, &v).is_ok());
    v["limit"] = loads(b"1.0000000000000000000000000000001", 64, 1).unwrap();
    assert!(validate(args, &v).is_err());
    v["limit"] = serde_json::json!(1);
    v["fields"] = serde_json::json!(["recordedReps", "recordedReps"]);
    assert!(validate(args, &v).is_err());
    assert!(!ironvellum_contracts::catalogue::draftable("assisted dip"));
    assert!(ironvellum_contracts::catalogue::draftable("bench press"));
}

#[test]
fn embeds_all_contracts_and_reconstructs_the_catalogue_policy() {
    use ironvellum_contracts::{
        catalogue::{MOVEMENTS, check_entries},
        schema::{resolved_resource, resource, tool, validate},
    };
    for name in [
        "files-v1.schema.json",
        "context-v1.schema.json",
        "context-v2.schema.json",
        "proposal-v1.schema.json",
        "portable-workout-v1.schema.json",
        "tool-types-v1.schema.json",
        "tool-types-v2.schema.json",
        "tools-v1.json",
        "tool-outputs-v2.json",
        "tools-watch-v1.json",
    ] {
        assert!(resource(name).is_some());
        assert!(resolved_resource(name).is_some());
    }
    assert_eq!(MOVEMENTS.len(), 584);
    assert_eq!(
        MOVEMENTS.digest,
        "6b8bea45f8781884088d99fe8fc76c0df4c5755a8c2d1e9914629710bfa18a66"
    );
    for row in resource("movements-v1.json").unwrap()["movements"]
        .as_array()
        .unwrap()
    {
        let key = row["key"].as_str().unwrap();
        assert_eq!(
            MOVEMENTS.get(key).unwrap()["draftable"],
            row["metric"] == "REPS"
                && !["assisted chin-up", "assisted dip", "assisted pull-up"].contains(&key)
        );
    }
    assert_eq!(
        check_entries(&[serde_json::json!({"exerciseKey":"assisted dip"})])
            .unwrap_err()
            .code(),
        "unsupported_movement"
    );
    let portable =
        include_bytes!("../../../tools/local_connector/contracts/portable-workout-v1.example.json");
    validate_document("portable-workout-v1", &loads(portable, 65536, 16).unwrap()).unwrap();
    for (name, count) in [
        ("get_workout", 7),
        ("list_saved_workouts", 4),
        ("get_progress", 2),
    ] {
        let t = tool(2, name).unwrap();
        for key in ["inputSchema", "outputSchema"] {
            assert_eq!(t[key]["anyOf"].as_array().unwrap().len(), count + 1);
        }
    }
    let args = &tool(2, "get_progress").unwrap()["inputSchema"];
    for bad in [
        serde_json::json!({"view":"totals","offset":0,"cursor":format!("0:{}","a".repeat(64))}),
        serde_json::json!({"view":"totals","fields":[]}),
        serde_json::json!({"view":"totals","limit":513}),
        serde_json::json!({"view":"lifts","id":"extra"}),
    ] {
        assert!(validate(args, &bad).is_err());
    }
    assert!(validate(args, &serde_json::json!({"view":"lifts","limit":512})).is_ok());
}
#[test]
fn rejects_unsupported_vocabulary_in_unvisited_schemas() {
    use ironvellum_contracts::schema::validate;
    use serde_json::json;

    for schema in [
        json!({"not":{"unknown":true}}),
        json!({"anyOf":[true,{"unknown":true}]}),
        json!({"oneOf":[true,{"unknown":true}]}),
        json!({"properties":{"optional":{"unknown":true}}}),
        json!({"additionalProperties":{"unknown":true}}),
        json!({"items":{"unknown":true}}),
    ] {
        assert_eq!(
            validate(&schema, &json!({})).unwrap_err().code(),
            "invalid_data",
            "{schema}"
        );
    }
    let literal = json!({"unknown":true});
    for schema in [
        json!({"const":literal}),
        json!({"enum":[literal]}),
        json!({"properties":{"unknown":{"const":true}}}),
    ] {
        assert!(validate(&schema, &literal).is_ok(), "{schema}");
    }
}
