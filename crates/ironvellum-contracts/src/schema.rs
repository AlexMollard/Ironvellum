use crate::{Refusal, Value};
use bigdecimal::BigDecimal;
use fancy_regex::Regex;
use serde_json::{Map, json};
use std::{
    collections::{HashMap, HashSet},
    str::FromStr,
    sync::LazyLock,
};

macro_rules! resources { ($($file:literal),+ $(,)?) => {
    LazyLock::new(|| HashMap::from([$(($file,serde_json::from_slice(include_bytes!(concat!("../../../tools/local_connector/contracts/", $file))).expect("frozen JSON"))),+]))
}}
static RESOURCES: LazyLock<HashMap<&str, Value>> = resources!(
    "context-v1.schema.json",
    "context-v2.schema.json",
    "proposal-v1.schema.json",
    "files-v1.schema.json",
    "tool-types-v1.schema.json",
    "tool-types-v2.schema.json",
    "tools-v1.json",
    "tool-outputs-v2.json",
    "tools-watch-v1.json",
    "portable-workout-v1.schema.json",
    "movements-v1.json",
    "draft-movement-policy-v1.json",
);
pub fn resource(name: &str) -> Option<&'static Value> {
    RESOURCES.get(name)
}
fn frozen(name: &str) -> &'static Value {
    resource(name).expect("known frozen resource")
}

// Only embedded resources enter the resolver. Unknown targets are build/test defects.
fn inline(s: &Value, local: &Value) -> Value {
    match s {
        Value::Array(a) => Value::Array(a.iter().map(|x| inline(x, local)).collect()),
        Value::Object(o) => {
            if let Some(reference) = s["$ref"].as_str() {
                let (file, key) = reference
                    .split_once("#/$defs/")
                    .expect("local frozen definition");
                let defs = if file.is_empty() {
                    local
                } else {
                    &frozen(file)["$defs"]
                };
                return inline(defs.get(key).expect("known frozen definition"), defs);
            }
            Value::Object(
                o.iter()
                    .filter(|(k, _)| k.as_str() != "$defs")
                    .map(|(k, v)| (k.clone(), inline(v, local)))
                    .collect(),
            )
        }
        _ => s.clone(),
    }
}
pub fn resolved_resource(name: &str) -> Option<Value> {
    let v = resource(name)?;
    Some(inline(v, &v["$defs"]))
}
static DOCUMENTS: LazyLock<HashMap<&str, Value>> = LazyLock::new(|| {
    [
        "context-v1",
        "context-v2",
        "proposal-v1",
        "portable-workout-v1",
        "files-v1",
    ]
    .into_iter()
    .map(|n| (n, resolved_resource(&format!("{n}.schema.json")).unwrap()))
    .collect()
});
pub fn validate_document(name: &str, v: &Value) -> Result<(), Refusal> {
    validate(DOCUMENTS.get(name).ok_or(Refusal::InvalidData)?, v)
}

fn closed(properties: Value) -> Value {
    let required: Vec<_> = properties.as_object().unwrap().keys().cloned().collect();
    json!({"type":"object","properties":properties,"additionalProperties":false,"required":required})
}
fn without(s: &Value, field: &str) -> Value {
    let mut p = s["properties"].clone();
    p.as_object_mut().unwrap().shift_remove(field);
    p
}
fn count() -> Value {
    json!({"type":"integer","minimum":0,"maximum":2147483647})
}
fn header(s: &Value, field: &str, count_field: &str) -> Value {
    let mut p = without(s, field);
    p[count_field] = count();
    closed(p)
}

fn views(tools: &mut Value, defs: &Value) {
    let progress = header(
        &header(
            &defs["prepProgress"],
            "completedUnitKeys",
            "completedUnitCount",
        ),
        "skippedUnitKeys",
        "skippedUnitCount",
    );
    let mut prep = header(&defs["preparation"], "steps", "stepCount");
    prep["properties"]["progress"] = progress;
    let unit = &defs["prepProgress"]["properties"]["completedUnitKeys"]["items"];
    let rows = json!({
        "sets":defs["set"],"preparations":prep,"preparationSteps":defs["step"],"completedUnitKeys":unit,"skippedUnitKeys":unit,
        "routines":header(&defs["routine"],"steps","stepCount"),"routineSteps":defs["step"],
        "headers":header(&defs["preset"],"entries","entryCount"),"entries":defs["entry"],
        "totals":closed(without(&defs["progress"],"lifts")),"lifts":defs["lift"]
    });
    let cursor = json!({"type":"string","pattern":r"^[0-9]{1,10}:[0-9a-f]{64}(?![\s\S])"});
    let selectors = json!({"id":defs["opaqueId"],"routineId":defs["opaqueId"],"kind":{"enum":["WARMUP","COOLDOWN"]}});
    for (name, view_names) in [
        (
            "get_workout",
            &[
                "sets",
                "preparations",
                "preparationSteps",
                "completedUnitKeys",
                "skippedUnitKeys",
                "routines",
                "routineSteps",
            ][..],
        ),
        (
            "list_saved_workouts",
            &["headers", "entries", "routines", "routineSteps"][..],
        ),
        ("get_progress", &["totals", "lifts"][..]),
    ] {
        let ordinary = &tools[name];
        let mut inputs = vec![ordinary["inputSchema"].clone()];
        let mut outputs = vec![ordinary["outputSchema"].clone()];
        for &view in view_names {
            let parent: &[&str] = match view {
                "preparationSteps" | "completedUnitKeys" | "skippedUnitKeys" => &["id", "kind"],
                "routineSteps" => &["id", "routineId"],
                "headers" | "totals" | "lifts" => &[],
                _ => &["id"],
            };
            let tag = json!({"const":view});
            let mut args = json!({"view":tag});
            for k in parent {
                args[*k] = selectors[*k].clone();
            }
            args["cursor"] = cursor.clone();
            args["offset"] = count();
            args["offset"]["description"] =
                json!("Zero-based row, including after an oversized item.");
            args["limit"] = json!({"type":"integer","minimum":1,"maximum":512});
            let mut row = rows[view].clone();
            if let Some(props) = row["properties"].as_object() {
                args["fields"] = json!({"type":"array","minItems":1,"uniqueItems":true,"items":{"enum":props.keys().collect::<Vec<_>>()},"description":"Return only these fields of each row."});
                row["required"] = json!([]);
                row["minProperties"] = json!(1);
            }
            let mut request = closed(args);
            request["required"] = json!(
                std::iter::once("view")
                    .chain(parent.iter().copied())
                    .collect::<Vec<_>>()
            );
            request["not"] = json!({"required":["cursor","offset"]});
            inputs.push(request);
            outputs.push(closed(json!({"snapshot":ordinary["outputSchema"]["properties"]["snapshot"],"view":tag,
                "items":{"type":"array","items":row,"maxItems":512},"total":count(),"nextCursor":{"anyOf":[cursor,{"type":"null"}]}})));
        }
        tools[name] = json!({"inputSchema":{"type":"object","anyOf":inputs},"outputSchema":{"type":"object","anyOf":outputs}});
    }
}
static V1: LazyLock<Value> = LazyLock::new(|| {
    inline(
        frozen("tools-v1.json"),
        &frozen("files-v1.schema.json")["$defs"],
    )
});
static V2: LazyLock<Value> = LazyLock::new(|| {
    let outputs = resolved_resource("tool-outputs-v2.json").unwrap();
    let mut tools = Value::Object(Map::new());
    for (name, out) in outputs.as_object().unwrap() {
        tools[name] = V1[name].clone();
        tools[name]["outputSchema"] = out.clone();
    }
    let defs = &frozen("context-v2.schema.json")["$defs"];
    views(&mut tools, &inline(defs, defs));
    tools
});
pub fn tool(version: u8, name: &str) -> Option<&'static Value> {
    match version {
        1 => V1.get(name),
        2 => V2.get(name),
        _ => None,
    }
}

pub fn exact(n: &serde_json::Number) -> Result<BigDecimal, Refusal> {
    BigDecimal::from_str(n.as_str()).map_err(|_| Refusal::InvalidData)
}
pub fn equal(a: &Value, b: &Value) -> bool {
    match (a, b) {
        (Value::Number(x), Value::Number(y)) => {
            exact(x).is_ok_and(|x| exact(y).is_ok_and(|y| x == y))
        }
        (Value::Array(x), Value::Array(y)) => {
            x.len() == y.len() && x.iter().zip(y).all(|(x, y)| equal(x, y))
        }
        (Value::Object(x), Value::Object(y)) => {
            x.len() == y.len() && x.iter().all(|(k, x)| y.get(k).is_some_and(|y| equal(x, y)))
        }
        _ => a == b,
    }
}
fn kind(t: &str, v: &Value) -> bool {
    match t {
        "number" => v.is_number(),
        "integer" => v
            .as_number()
            .is_some_and(|n| exact(n).is_ok_and(|n| n.is_integer())),
        "null" => v.is_null(),
        "object" => v.is_object(),
        "array" => v.is_array(),
        "string" => v.is_string(),
        "boolean" => v.is_boolean(),
        _ => false,
    }
}
static PATTERNS: LazyLock<HashMap<String, Regex>> = LazyLock::new(|| {
    fn collect(v: &Value, patterns: &mut HashMap<String, Regex>) {
        if let Some(p) = v.get("pattern").and_then(Value::as_str) {
            patterns
                .entry(p.into())
                .or_insert_with(|| Regex::new(p).expect("valid frozen regex"));
        }
        match v {
            Value::Array(a) => a.iter().for_each(|v| collect(v, patterns)),
            Value::Object(o) => o.values().for_each(|v| collect(v, patterns)),
            _ => {}
        }
    }
    let mut patterns = HashMap::new();
    for r in RESOURCES.values() {
        collect(r, &mut patterns);
    }
    collect(&V2, &mut patterns);
    patterns
});
fn size(limit: &Value, len: usize, minimum: bool) -> bool {
    limit.as_u64().is_some_and(|n| {
        if minimum {
            len as u64 >= n
        } else {
            len as u64 <= n
        }
    })
}
/// Check every schema position, independently of which branches match an instance.
/// Property names and literal values in const/enum are instance data.
fn supported_schema(s: &Value) -> bool {
    if s.is_boolean() {
        return true;
    }
    s.as_object().is_some_and(|schema| {
        schema.iter().all(|(key, value)| match key.as_str() {
            "not" | "items" | "additionalProperties" => supported_schema(value),
            "oneOf" | "anyOf" => value
                .as_array()
                .is_some_and(|branches| branches.iter().all(supported_schema)),
            "properties" => value
                .as_object()
                .is_some_and(|properties| properties.values().all(supported_schema)),
            "pattern" => value
                .as_str()
                .is_some_and(|pattern| PATTERNS.contains_key(pattern)),
            "$schema" | "title" | "description" | "const" | "enum" | "type" | "minimum"
            | "maximum" | "minLength" | "maxLength" | "minItems" | "maxItems" | "uniqueItems"
            | "minProperties" | "required" => true,
            _ => false,
        })
    })
}
fn accepts(s: &Value, v: &Value) -> bool {
    if let Some(ok) = s.as_bool() {
        return ok;
    }
    let Some(schema) = s.as_object() else {
        return false;
    };
    schema.iter().all(|(key, c)| match key.as_str() {
        "title" | "description" | "$schema" => true,
        "type" => match c {
            Value::String(t) => kind(t, v),
            Value::Array(a) => a.iter().any(|t| t.as_str().is_some_and(|t| kind(t, v))),
            _ => false,
        },
        "const" => equal(c, v),
        "enum" => c.as_array().is_some_and(|a| a.iter().any(|x| equal(x, v))),
        "anyOf" => c
            .as_array()
            .is_some_and(|a| a.iter().any(|sub| accepts(sub, v))),
        "oneOf" => c
            .as_array()
            .is_some_and(|a| a.iter().filter(|sub| accepts(sub, v)).take(2).count() == 1),
        "not" => !accepts(c, v),
        "minimum" | "maximum" => v.as_number().is_none_or(|n| {
            exact(n).is_ok_and(|n| {
                c.as_number()
                    .and_then(|x| exact(x).ok())
                    .is_some_and(|bound| {
                        if key == "minimum" {
                            n >= bound
                        } else {
                            n <= bound
                        }
                    })
            })
        }),
        "minLength" | "maxLength" => v
            .as_str()
            .is_none_or(|text| size(c, text.chars().count(), key == "minLength")),
        "pattern" => v.as_str().is_none_or(|text| {
            c.as_str()
                .and_then(|p| PATTERNS.get(p))
                .is_some_and(|r| r.is_match(text).unwrap_or(false))
        }),
        "minItems" | "maxItems" => v
            .as_array()
            .is_none_or(|a| size(c, a.len(), key == "minItems")),
        "items" => v
            .as_array()
            .is_none_or(|a| a.iter().all(|item| accepts(c, item))),
        "uniqueItems" => v.as_array().is_none_or(|a| {
            if c == false {
                return true;
            }
            // Frozen uniqueItems arrays contain scalar strings. Handle other JSON types exactly too.
            let mut strings = HashSet::new();
            a.iter().enumerate().all(|(i, x)| {
                if let Some(t) = x.as_str() {
                    strings.insert(t)
                } else {
                    !a[..i].iter().any(|other| equal(x, other))
                }
            })
        }),
        "minProperties" => v.as_object().is_none_or(|o| size(c, o.len(), true)),
        "required" => v.as_object().is_none_or(|o| {
            c.as_array().is_some_and(|keys| {
                keys.iter()
                    .all(|k| k.as_str().is_some_and(|k| o.contains_key(k)))
            })
        }),
        "properties" => v.as_object().is_none_or(|o| {
            c.as_object().is_some_and(|props| {
                props
                    .iter()
                    .all(|(k, sub)| o.get(k).is_none_or(|val| accepts(sub, val)))
            })
        }),
        "additionalProperties" => v.as_object().is_none_or(|o| {
            o.iter()
                .all(|(k, val)| s["properties"].get(k).is_some() || accepts(c, val))
        }),
        _ => false,
    })
}
/// Validates the keyword and pattern vocabulary of resolved, embedded Ironvellum schemas.
/// Unrecognized assertion keywords fail closed.
pub fn validate(s: &Value, v: &Value) -> Result<(), Refusal> {
    if supported_schema(s) && accepts(s, v) {
        Ok(())
    } else {
        Err(Refusal::InvalidData)
    }
}
