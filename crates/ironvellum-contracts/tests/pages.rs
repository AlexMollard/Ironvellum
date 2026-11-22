use ironvellum_contracts::{
    Refusal, Value, encode,
    pages::{STRUCTURED_PAGE_TEXT, bounded_page},
    wire::MAX_RESULT,
};
use serde_json::json;

const GOLDEN: &str = "20:1d6609c933638a4c0912fff6a5e8a1889244241aba3c370bb50a0f22b7540272";

fn args() -> Value {
    json!({"id":"workout","kind":"WARMUP","routineId":"routine","view":"sets","fields":["z","a"]})
}

fn page(args: &Value, rows: &[Value], id: &Value) -> Result<Value, Refusal> {
    bounded_page(
        "source",
        "get_workout",
        args,
        &json!({"schemaVersion":2}),
        rows,
        Clone::clone,
        id,
    )
}

#[test]
fn golden_cursor_fields_and_page_boundaries() {
    let rows: Vec<_> = (0..45)
        .map(|n| json!({"z":n,"a":"a","ignored":true}))
        .collect();
    let first = bounded_page(
        "source",
        "get_workout",
        &args(),
        &Value::Null,
        &rows,
        |row| json!({"z":row["z"],"a":"projected"}),
        &json!(1),
    )
    .unwrap();
    assert_eq!(first["nextCursor"], GOLDEN);
    assert_eq!(first["total"], 45);
    assert_eq!(first["items"].as_array().unwrap().len(), 20);
    assert_eq!(
        encode(&first["items"][0]).unwrap(),
        br#"{"a":"projected","z":0}"#
    );
    let mut continuation = args();
    continuation["fields"] = json!(["a", "z"]);
    continuation["cursor"] = json!(GOLDEN);
    let second = page(&continuation, &rows, &json!(1)).unwrap();
    assert_eq!(second["items"][0]["z"], 20);
    assert_eq!(
        second["nextCursor"],
        "40:dfd7f537500e72d1dc11e30d240a7ea618d524b32f8d68fdcdd4299d936a5429"
    );
    continuation["cursor"] = second["nextCursor"].clone();
    let last = page(&continuation, &rows, &json!(1)).unwrap();
    assert_eq!(last["items"].as_array().unwrap().len(), 5);
    assert!(last["nextCursor"].is_null());
}

#[test]
fn cursor_is_bound_to_every_scope_component() {
    let rows = vec![json!({"a":0,"z":0}); 30];
    let mut continuation = args();
    continuation["cursor"] = json!(GOLDEN);
    for (file, tool) in [
        ("changed", "get_workout"),
        ("source", "list_saved_workouts"),
    ] {
        assert_eq!(
            bounded_page(
                file,
                tool,
                &continuation,
                &Value::Null,
                &rows,
                Clone::clone,
                &json!(0)
            ),
            Err(Refusal::InvalidCursor)
        );
    }
    for (key, value) in [
        ("id", json!("x")),
        ("kind", json!("COOLDOWN")),
        ("routineId", json!("x")),
        ("view", json!("entries")),
        ("fields", json!(["a"])),
    ] {
        let mut modified = continuation.clone();
        modified[key] = value;
        assert_eq!(
            page(&modified, &rows, &json!(0)),
            Err(Refusal::InvalidCursor),
            "{key}"
        );
    }
    for cursor in [
        "bad",
        "0:bad",
        "020:1d6609c933638a4c0912fff6a5e8a1889244241aba3c370bb50a0f22b7540272",
        "30:bad",
        "999999999999999999999999:bad",
    ] {
        continuation["cursor"] = json!(cursor);
        assert_eq!(
            page(&continuation, &rows, &json!(0)),
            Err(Refusal::InvalidCursor)
        );
    }
}

#[test]
fn offset_boundary_and_later_offset_resumption() {
    let rows = vec![json!("x".repeat(MAX_RESULT)), json!("later")];
    assert_eq!(
        page(&json!({"view":"sets"}), &rows, &json!(1)),
        Err(Refusal::ResultLimit)
    );
    let later = page(
        &json!({"view":"sets","offset":1.0,"limit":1.0}),
        &rows,
        &json!(1),
    )
    .unwrap();
    assert_eq!(
        later,
        json!({"view":"sets","items":["later"],"total":2,"nextCursor":null})
    );
    let at_end = page(&json!({"view":"sets","offset":2}), &rows, &json!(1)).unwrap();
    assert_eq!(at_end["items"], json!([]));
    for offset in [json!(-1), json!(3), json!(0.5)] {
        assert_eq!(
            page(&json!({"view":"sets","offset":offset}), &rows, &json!(1)),
            Err(Refusal::InvalidOffset)
        );
    }
}

fn actual_wire_size(page: &Value, id: &Value, structured_only: bool) -> usize {
    let mut payload = json!({"snapshot":{"schemaVersion":2}});
    payload
        .as_object_mut()
        .unwrap()
        .extend(page.as_object().unwrap().clone());
    let text = if structured_only {
        STRUCTURED_PAGE_TEXT.into()
    } else {
        String::from_utf8(encode(&payload).unwrap()).unwrap()
    };
    encode(
        &json!({"jsonrpc":"2.0","id":id,"result":{"content":[{"type":"text","text":text}],
        "structuredContent":payload,"isError":false,"resultType":"complete"}}),
    )
    .unwrap()
    .len()
}

#[test]
fn escaped_text_and_large_ids_count_against_actual_envelope() {
    let rows = vec![
        json!(r#""\"#.repeat(60_000)),
        json!(r#""\"#.repeat(60_000)),
        json!("last"),
    ];
    let id = json!(r#""\"#.repeat(5_000));
    let request = json!({"view":"sets"});
    let first = page(&request, &rows, &id).unwrap();
    assert_eq!(first["items"].as_array().unwrap().len(), 1);
    assert!(actual_wire_size(&first, &id, false) <= MAX_RESULT);
    let mut extra = first.clone();
    extra["items"].as_array_mut().unwrap().push(rows[1].clone());
    assert!(actual_wire_size(&extra, &id, false) > MAX_RESULT);
    let huge_id = json!("x".repeat(MAX_RESULT));
    assert_eq!(page(&request, &rows, &huge_id), Err(Refusal::ResultLimit));
}

#[test]
fn structured_only_fallback_is_one_row() {
    assert_eq!(STRUCTURED_PAGE_TEXT, "Read this page in structuredContent.");
    let rows = vec![json!("x".repeat(MAX_RESULT * 3 / 4)), json!("later")];
    let id = json!(42);
    let first = page(&json!({"view":"sets"}), &rows, &id).unwrap();
    assert_eq!(first["items"].as_array().unwrap().len(), 1);
    assert!(actual_wire_size(&first, &id, false) > MAX_RESULT);
    assert!(actual_wire_size(&first, &id, true) <= MAX_RESULT);
    let next = page(
        &json!({"view":"sets","cursor":first["nextCursor"]}),
        &rows,
        &id,
    )
    .unwrap();
    assert_eq!(next["items"], json!(["later"]));
}

#[test]
fn cursor_overhead_at_the_wire_boundary() {
    let mut id = json!("quoted \"id\" and \\ path");
    let mut candidate = json!({
        "view":"sets","items":["small",""],"total":3,"nextCursor":format!("2:{}", "0".repeat(64))
    });
    let mut overhead = actual_wire_size(&candidate, &id, false);
    if !overhead.is_multiple_of(2) {
        id = json!(format!("{}!", id.as_str().unwrap()));
        overhead += 1;
    }
    let size = (MAX_RESULT - overhead) / 2;
    candidate["items"][1] = json!("x".repeat(size));
    assert_eq!(actual_wire_size(&candidate, &id, false), MAX_RESULT);
    let mut rows = vec![json!("small"), candidate["items"][1].clone(), json!("tail")];
    let view = json!({"view":"sets","limit":2});
    let at_limit = page(&view, &rows, &id).unwrap();
    assert_eq!(at_limit["items"].as_array().unwrap().len(), 2);
    assert_eq!(actual_wire_size(&at_limit, &id, false), MAX_RESULT);
    rows[1] = json!("x".repeat(size + 1));
    let over_limit = page(&view, &rows, &id).unwrap();
    assert_eq!(over_limit["items"], json!(["small"]));
}
