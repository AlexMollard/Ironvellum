use ironvellum_contracts::{
    Refusal, Value,
    set_scheme::{check_scheme, trim_kotlin},
};
use serde_json::json;

fn claim(fields: Value) -> Value {
    let mut value = json!({
        "type":"straight", "warmupRamp":false, "workingRange":null,
        "topRange":null, "topRir":null, "backoffSets":null, "backoffRange":null,
        "backoffFixedKg":null, "backoffPercent":null, "restAfterTopSec":null,
        "dropSets":null, "dropCount":null, "dropPercent":null, "customKinds":null
    });
    value
        .as_object_mut()
        .unwrap()
        .extend(fields.as_object().unwrap().clone());
    value
}

fn top(fields: Value) -> Value {
    let mut value = claim(json!({"type":"top_backoff", "topRange":{"min":4,"max":6},
        "topRir":{"min":0,"max":1}, "backoffSets":2, "backoffRange":{"min":6,"max":8},
        "backoffPercent":10, "restAfterTopSec":180}));
    value
        .as_object_mut()
        .unwrap()
        .extend(fields.as_object().unwrap().clone());
    value
}

#[test]
fn historical_vectors() {
    let vectors = [
        (None, claim(json!({}))),
        (
            Some("t=future;rr=٩-15;wr=1"),
            claim(json!({"warmupRamp":true,"workingRange":{"min":9,"max":15}})),
        ),
        (
            Some("t=drops;ds=999;dc=-4;dp=2;rr=7-750"),
            claim(json!({"type":"drops",
            "workingRange":{"min":7,"max":500},"dropSets":30,"dropCount":1,"dropPercent":5})),
        ),
        (
            Some("t=custom;ck=warmup, top ,wrong,drop,;wr=1"),
            claim(json!({"type":"custom",
            "warmupRamp":true,"customKinds":["TOP","WORKING","DROP","WORKING"]})),
        ),
        (
            Some("t=top_backoff;bf=bogus;bp=77;rt=4;bo=0;tr=2;rir=0-30"),
            top(
                json!({"topRange":{"min":2,"max":2},"topRir":{"min":0,"max":10},
                "backoffSets":1,"backoffPercent":50,"restAfterTopSec":30}),
            ),
        ),
        (
            Some("t=top_backoff;bf=0x1.6p2d;rr=1-2"),
            top(json!({"backoffFixedKg":5.5,"backoffPercent":null})),
        ),
    ];
    for (raw, claimed) in vectors {
        let raw = json!(raw);
        let original = raw.clone();
        assert_eq!(check_scheme(&raw, &claimed), Ok(()), "{raw}");
        assert_eq!(raw, original);
        let mut altered = claimed;
        altered["warmupRamp"] = json!(!altered["warmupRamp"].as_bool().unwrap());
        assert_eq!(
            check_scheme(&raw, &altered),
            Err(Refusal::InvalidData),
            "{raw}"
        );
    }
}

#[test]
fn frozen_schema_rejects_coercible_claims() {
    let bytes = include_bytes!("../../../tools/local_connector/contracts/fixtures/v2/context.json");
    let mut fixture = ironvellum_contracts::loads(bytes, 8 << 20, 32).unwrap();
    let raw = json!("t=top_backoff;bf=1");
    let valid = top(json!({
        "backoffFixedKg":1.0, "backoffPercent":null, "backoffSets":2.0
    }));
    let entry = &mut fixture["savedWorkouts"][0]["entries"][0];
    entry["schemeRaw"] = raw.clone();
    entry["scheme"] = valid.clone();
    let validate = ironvellum_contracts::schema::validate_document;
    assert_eq!(validate("context-v2", &fixture), Ok(()));
    assert_eq!(check_scheme(&raw, &valid), Ok(()));

    for (key, value) in [("warmupRamp", json!(0)), ("backoffFixedKg", json!("1"))] {
        let mut malformed = fixture.clone();
        let scheme = &mut malformed["savedWorkouts"][0]["entries"][0]["scheme"];
        scheme[key] = value;
        assert_eq!(
            check_scheme(&raw, scheme),
            Err(Refusal::InvalidData),
            "{key}"
        );
        assert_eq!(
            validate("context-v2", &malformed),
            Err(Refusal::InvalidData),
            "{key}"
        );
    }
}

#[test]
fn kotlin_trim_integers_and_field_handling() {
    assert_eq!(trim_kotlin("\u{1c}\u{a0}\u{202f}x\u{3000}"), "x");
    assert_eq!(trim_kotlin("\u{85}x\u{85}"), "\u{85}x\u{85}");
    let raw = json!(
        ";bad;=;unknown=drop;t=drops;\u{a0}t\u{2007}=\u{202f}top_backoff;bo=+４;tr=8-3-30;rir=𝟜-8;bp=2147483648;rt=-2147483648;wr=1=0"
    );
    assert_eq!(
        check_scheme(
            &raw,
            &top(json!({"backoffSets":4,"topRange":{"min":8,"max":8},
        "restAfterTopSec":30}))
        ),
        Ok(())
    );
    assert_eq!(
        check_scheme(
            &json!("t=top_backoff;rt=-2147483649;bo=2147483647"),
            &top(json!({"backoffSets":10}))
        ),
        Ok(())
    );
    assert_eq!(
        check_scheme(&json!(9), &claim(json!({}))),
        Err(Refusal::InvalidData)
    );
    assert_eq!(
        check_scheme(&Value::Null, &json!({})),
        Err(Refusal::InvalidData)
    );
    assert_eq!(
        check_scheme(&Value::Null, &claim(json!({"extra":null}))),
        Err(Refusal::InvalidData)
    );
}

#[test]
fn java_doubles_round_to_even_and_apply_java_trim() {
    let cases = [
        ("\u{0}\u{20}+.5F\u{1}", Some(0.5)),
        ("12.D", Some(12.0)),
        ("+0X.8P+0F", Some(0.5)),
        ("0x1.00000000000008p0", Some(1.0)),
        (
            "0x1.00000000000008001p0",
            Some(f64::from_bits(1.0_f64.to_bits() + 1)),
        ),
        ("0x0.00000000000008p-1022", Some(0.0)),
        ("0x0.00000000000008001p-1022", Some(f64::from_bits(1))),
        ("0x1.fffffffffffff7p1023", Some(f64::MAX)),
        ("-0x1p-999999999999999999999", Some(0.0)),
        ("0x1.fffffffffffff8p1023", None),
        ("0x1p1024", None),
        ("0x12", None),
        ("1e+F", None),
        ("1dF", None),
        ("１２", None),
        ("-1", None),
        ("+Infinity", None),
        ("NaN", None),
        ("1e999999999999999999999", None),
    ];
    for (literal, fixed) in cases {
        let expected = match fixed {
            Some(fixed) => top(json!({"backoffFixedKg":fixed, "backoffPercent":null})),
            None => top(json!({})),
        };
        assert_eq!(
            check_scheme(&json!(format!("t=top_backoff;bf={literal}")), &expected),
            Ok(()),
            "{literal:?}"
        );
    }
    let precise = ironvellum_contracts::loads(b"1.00000000000000000003", 128, 16).unwrap();
    assert_eq!(
        check_scheme(
            &json!("t=top_backoff;bf=1"),
            &top(json!({
        "backoffFixedKg":precise, "backoffPercent":null, "backoffSets":2.0}))
        ),
        Ok(())
    );
}

#[test]
fn custom_kind_empty_segments_repetition_and_cap() {
    for (raw, kinds) in [
        ("t=custom", vec![]),
        ("t=custom;ck=", vec!["WORKING"]),
        (
            "t=custom;ck=TOP;ck=, DrOP, warmup, drop,,",
            vec!["WORKING", "DROP", "DROP", "WORKING", "WORKING"],
        ),
        ("t=custom;ck=WARMUP", vec![]),
    ] {
        assert_eq!(
            check_scheme(
                &json!(raw),
                &claim(json!({"type":"custom","customKinds":kinds}))
            ),
            Ok(())
        );
    }
    let raw = format!("t=custom;ck={}failure", "TOP,warmup,".repeat(30));
    assert_eq!(
        check_scheme(
            &json!(raw),
            &claim(json!({"type":"custom","customKinds":vec!["TOP";30]}))
        ),
        Ok(())
    );
}
