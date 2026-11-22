use crate::{Refusal, Value, encode, schema::exact, wire::MAX_RESULT};
use bigdecimal::ToPrimitive;
use serde_json::{Map, json};
use sha2::{Digest, Sha256};

pub const STRUCTURED_PAGE_TEXT: &str = "Read this page in structuredContent.";

fn token(scope: &[Value; 7], offset: usize) -> Result<String, Refusal> {
    let mut binding = scope.to_vec();
    binding.push(json!(offset));
    let digest = Sha256::digest(encode(&Value::Array(binding))?);
    Ok(format!("{offset}:{digest:x}"))
}

fn count(value: &Value) -> Option<usize> {
    let number = value.as_number()?;
    if let Some(integer) = number.as_u64() {
        return usize::try_from(integer).ok();
    }
    let decimal = exact(number).ok()?;
    decimal.is_integer().then(|| decimal.to_usize()).flatten()
}

fn wire_size(request_id: &Value, payload: &Value, text: &str) -> Result<usize, Refusal> {
    Ok(encode(&json!({
        "jsonrpc":"2.0","id":request_id,"result":{
            "content":[{"type":"text","text":text}],"structuredContent":payload,
            "isError":false,"resultType":"complete"
        }
    }))?
    .len())
}

fn encoded_text(raw: Vec<u8>) -> Result<String, Refusal> {
    String::from_utf8(raw).map_err(|_| Refusal::InvalidData)
}

/// Return the bounded collection page. The caller attaches the same snapshot
/// metadata and uses the structured-only text when a full response exceeds the budget.
///
/// Supply validated tool arguments, including unique field names when `fields`
/// is present, and rows valid for the selected view after projection.
pub fn bounded_page<F>(
    file_hash: &str,
    tool: &str,
    args: &Value,
    metadata: &Value,
    rows: &[Value],
    project: F,
    request_id: &Value,
) -> Result<Value, Refusal>
where
    F: Fn(&Value) -> Value,
{
    let view = args
        .get("view")
        .and_then(Value::as_str)
        .ok_or(Refusal::InvalidData)?;
    let mut fields = Vec::new();
    if let Some(keys) = args.get("fields") {
        for key in keys.as_array().ok_or(Refusal::InvalidData)? {
            fields.push(key.as_str().ok_or(Refusal::InvalidData)?);
        }
        fields.sort_unstable();
    }
    let scope = [
        json!(file_hash),
        json!(tool),
        args["id"].clone(),
        args["kind"].clone(),
        args["routineId"].clone(),
        json!(view),
        json!(fields),
    ];
    let total = rows.len();
    let start = if let Some(cursor) = args.get("cursor") {
        let cursor = cursor.as_str().ok_or(Refusal::InvalidCursor)?;
        let offset = cursor
            .split_once(':')
            .and_then(|(offset, _)| offset.parse::<usize>().ok())
            .ok_or(Refusal::InvalidCursor)?;
        if offset == 0 || offset >= total || token(&scope, offset)? != cursor {
            return Err(Refusal::InvalidCursor);
        }
        offset
    } else {
        let offset = args
            .get("offset")
            .map_or(Some(0), count)
            .ok_or(Refusal::InvalidOffset)?;
        if offset > total {
            return Err(Refusal::InvalidOffset);
        }
        offset
    };
    let limit = args
        .get("limit")
        .map_or(Some(20), count)
        .filter(|limit| (1..=512).contains(limit))
        .ok_or(Refusal::InvalidData)?;

    let empty = json!({"snapshot":metadata,"view":view,"items":[],"total":total,"nextCursor":null});
    let mut full_size = wire_size(request_id, &empty, &encoded_text(encode(&empty)?)?)?;
    let structured_size = wire_size(request_id, &empty, STRUCTURED_PAGE_TEXT)?;
    let mut items = Vec::new();
    for (index, row) in rows.iter().enumerate().skip(start).take(limit) {
        let mut row = project(row);
        if !fields.is_empty() {
            let object = row.as_object_mut().ok_or(Refusal::InvalidData)?;
            let selected = fields
                .iter()
                .map(|&key| {
                    let value = object.swap_remove(key).ok_or(Refusal::InvalidData)?;
                    Ok((key.to_owned(), value))
                })
                .collect::<Result<Map<_, _>, Refusal>>()?;
            row = Value::Object(selected);
        }
        let raw = encode(&row)?;
        let comma = usize::from(index != start);
        let added = raw.len() + comma;
        // The text copy escapes every JSON string delimiter and backslash a
        // second time. Commas are emitted literally in both copies.
        let text_added = encode(&json!(encoded_text(raw)?))?.len() - 2 + comma;
        let end = index + 1;
        let cursor_bytes = if end < total {
            end.to_string().len() + 1 + 64 - 2
        } else {
            0
        };
        full_size += added + text_added;
        if full_size + 2 * cursor_bytes + 2 * usize::from(end < total) > MAX_RESULT {
            if items.is_empty() {
                if structured_size + added + cursor_bytes > MAX_RESULT {
                    return Err(Refusal::ResultLimit);
                }
                items.push(row);
            }
            break;
        }
        items.push(row);
    }

    let end = start + items.len();
    let cursor = (end < total).then(|| token(&scope, end)).transpose()?;
    Ok(json!({"view":view,"items":items,"total":total,"nextCursor":cursor}))
}
