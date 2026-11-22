use ironvellum_local::LocalError;
use ironvellum_local::status::{SessionRecord, session_id};

const RAW: &str = include_str!("legacy-session.json");
#[test]
fn legacy_record_roundtrip() {
    let record = SessionRecord::decode(RAW.as_bytes()).unwrap();
    assert_eq!(record.encode().unwrap(), RAW.as_bytes());
    assert!(!record.verified());
    let mut padded = RAW.as_bytes().to_vec();
    padded.resize(8192, b' ');
    assert!(SessionRecord::decode(&padded).is_ok());
    padded.push(b' ');
    assert_eq!(
        SessionRecord::decode(&padded).unwrap_err(),
        LocalError::Contract(ironvellum_contracts::Refusal::FileLimit)
    );
}

#[test]
fn rejects_invalid_session_fields() {
    for (from, to) in [
        ("253402300799999", "253402300800000"),
        ("253402300799999", "0"),
        ("253402300799999", "1e0"),
        ("253402300799999", "1.0"),
        ("253402300799999", "true"),
        ("\"toolsListed\":false,", ""),
        (
            "\"statusChecked\":true",
            "\"extra\":true,\"statusChecked\":true",
        ),
        ("\"initialized\":true", "\"initialized\":1"),
        ("fedcba", "Fedcba"),
        ("000000", "00000X"),
        ("-4111-", "-5111-"),
        ("-a222-", "-c222-"),
        ("\"exportedAtMs\":0", "\"exportedAtMs\":0.0"),
        ("\"exportedAtMs\":0", "\"exportedAtMs\":253402300800000"),
        ("\"workoutsIncluded\":0", "\"workoutsIncluded\":-1"),
        ("\"workoutsIncluded\":0", "\"workoutsIncluded\":1e0"),
        ("\"workoutsIncluded\":0", "\"workoutsIncluded\":true"),
        (
            "\"workoutsIncluded\":0",
            "\"workoutsIncluded\":0,\"other\":0",
        ),
        ("\"coverage\":", "\"other\":"),
    ] {
        let bad = RAW.replace(from, to);
        assert_ne!(bad, RAW);
        assert_eq!(
            SessionRecord::decode(bad.as_bytes()).unwrap_err(),
            LocalError::InvalidSession,
            "{from} -> {to}"
        );
    }
}
#[test]
fn filename_is_exact_lowercase_hex() {
    let id = "0123456789abcdef0123456789abcdef";
    assert_eq!(session_id(&format!("{id}.json")), Some(id));
    for name in [
        format!("{id}.JSON"),
        format!("{}.json", id.to_uppercase()),
        format!(".{id}.json"),
        format!("{id}0.json"),
    ] {
        assert!(session_id(&name).is_none());
    }
}

#[test]
fn generated_source_and_handshake_roundtrip() {
    use ironvellum_local::{SourceSummary, status::LoadedSource};
    let summary = SourceSummary {
        dataset_id: "11111111-1111-4111-8111-111111111111".into(),
        snapshot_id: "22222222-2222-4222-a222-222222222222".into(),
        exported_at_ms: 123,
        sha256: "b".repeat(64),
        workouts_included: 2,
        sets_included: 3,
        saved_workouts_included: 4,
    };
    let mut record = SessionRecord::decode(RAW.as_bytes()).unwrap();
    for bits in 0..8 {
        record.initialized = bits & 1 != 0;
        record.tools_listed = bits & 2 != 0;
        record.status_checked = bits & 4 != 0;
        record.loaded_source = if record.initialized {
            Some(LoadedSource::from_summary(&summary).unwrap())
        } else {
            None
        };
        assert_eq!(record.verified(), bits == 7);
        assert_eq!(
            SessionRecord::decode(&record.encode().unwrap()).unwrap(),
            record
        );
    }
    record.time_ms = 0;
    assert!(record.encode().is_err());
}

#[test]
fn parser_refusals_survive_session_decode() {
    let duplicate = RAW.replace(
        r#""initialized":true"#,
        r#""initialized":true,"initialized":false"#,
    );
    let nested = RAW.replace(
        r#""workoutsIncluded":0"#,
        r#""workoutsIncluded":0,"workoutsIncluded":0"#,
    );
    for invalid in [duplicate.as_bytes(), nested.as_bytes(), b"{"] {
        assert_eq!(
            SessionRecord::decode(invalid).unwrap_err(),
            LocalError::Contract(ironvellum_contracts::Refusal::InvalidData)
        );
    }
}
