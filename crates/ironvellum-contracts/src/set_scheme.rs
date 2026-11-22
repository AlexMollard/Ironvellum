use crate::{Refusal, Value, schema::exact};
use serde_json::json;

pub fn trim_kotlin(input: &str) -> &str {
    input.trim_matches(|ch| {
        matches!(ch, '\t'..='\r' | '\u{1c}'..=' ' | '\u{a0}' | '\u{1680}'
            | '\u{2000}'..='\u{200a}' | '\u{2028}' | '\u{2029}' | '\u{202f}'
            | '\u{205f}' | '\u{3000}')
    })
}

fn decimal_digit(ch: char) -> Option<u32> {
    // Java/Kotlin tests UTF-16 code units. Each BMP Nd block has ten digits.
    const ZEROES: [u32; 37] = [
        0x0030, 0x0660, 0x06f0, 0x07c0, 0x0966, 0x09e6, 0x0a66, 0x0ae6, 0x0b66, 0x0be6, 0x0c66,
        0x0ce6, 0x0d66, 0x0de6, 0x0e50, 0x0ed0, 0x0f20, 0x1040, 0x1090, 0x17e0, 0x1810, 0x1946,
        0x19d0, 0x1a80, 0x1a90, 0x1b50, 0x1bb0, 0x1c40, 0x1c50, 0xa620, 0xa8d0, 0xa900, 0xa9d0,
        0xa9f0, 0xaa50, 0xabf0, 0xff10,
    ];
    let code = ch as u32;
    let index = ZEROES
        .partition_point(|&zero| zero <= code)
        .checked_sub(1)?;
    let digit = code - ZEROES[index];
    (digit < 10).then_some(digit)
}

fn parse_integer(text: &str) -> Option<i32> {
    let negative = text.starts_with('-');
    let digits = text.strip_prefix(['+', '-']).unwrap_or(text);
    if digits.is_empty() {
        return None;
    }
    // Accumulating the signed value directly also admits i32::MIN.
    digits.chars().try_fold(0_i32, |value, ch| {
        let digit = decimal_digit(ch)? as i32;
        value
            .checked_mul(10)?
            .checked_add(if negative { -digit } else { digit })
    })
}

fn integer(text: &str, default: i32, low: i32, high: i32) -> i32 {
    parse_integer(text).map_or(default, |value| value.clamp(low, high))
}

fn range(text: &str, default: (i32, i32), low: i32, high: i32) -> Value {
    let mut parts = text.split('-');
    let (min, max) = match parts.next().and_then(parse_integer) {
        Some(start) => {
            let start = start.clamp(low, high);
            (
                start,
                integer(parts.next().unwrap_or_default(), start, low, high).max(start),
            )
        }
        None => default,
    };
    json!({"min":min,"max":max})
}

fn hex_exponent(text: &str) -> Option<i64> {
    let negative = text.starts_with('-');
    let digits = text.strip_prefix(['+', '-']).unwrap_or(text);
    if digits.is_empty() {
        return None;
    }
    let exponent = digits.chars().try_fold(0_i64, |value, ch| {
        Some(
            value
                .saturating_mul(10)
                .saturating_add(i64::from(ch.to_digit(10)?)),
        )
    })?;
    Some(if negative { -exponent } else { exponent })
}

fn hex_double(text: &str) -> Option<f64> {
    let (mantissa, exponent) = text.split_once(['p', 'P'])?;
    let exponent = hex_exponent(exponent)?;
    let (whole, fraction) = mantissa.split_once('.').unwrap_or((mantissa, ""));
    if whole.is_empty() && fraction.is_empty() {
        return None;
    }
    // Keep the first 54 significant bits (53 plus the rounding bit) and
    // whether any discarded bit was set. The full length fixes the exponent.
    let mut significant = 0_i64;
    let mut head = 0_u64;
    let mut sticky = false;
    for ch in whole.chars().chain(fraction.chars()) {
        let digit = ch.to_digit(16)?;
        for shift in (0..4).rev() {
            let bit = digit >> shift & 1;
            if significant != 0 || bit != 0 {
                significant += 1;
                if significant <= 54 {
                    head = (head << 1) | u64::from(bit);
                } else {
                    sticky |= bit != 0;
                }
            }
        }
    }
    if significant == 0 {
        return Some(0.0);
    }
    let exponent = exponent.saturating_add(significant - 1 - 4 * fraction.len() as i64);
    if exponent < -1075 {
        return Some(0.0);
    }
    if exponent > 1023 {
        return None;
    }

    let kept = (exponent + 1075).min(53);
    let discarded = significant.min(54) - kept;
    let rounded = if discarded > 0 {
        let guard = 1 << (discarded - 1);
        let retained = head >> discarded;
        let round_up =
            head & guard != 0 && (sticky || head & (guard - 1) != 0 || retained & 1 != 0);
        retained + u64::from(round_up)
    } else {
        head << -discarded
    };
    // In the normal range rounded includes the implicit leading bit, so
    // use exponent - 1. A rounding carry naturally increments the exponent.
    let exponent_bits = (exponent + 1022).max(0) as u64;
    Some(f64::from_bits((exponent_bits << 52) + rounded))
}

fn fixed_kg(text: &str) -> Option<f64> {
    let text = text.trim_matches(|ch| ch <= ' ');
    let text = text.strip_suffix(['f', 'F', 'd', 'D']).unwrap_or(text);
    let unsigned = text.strip_prefix(['+', '-']).unwrap_or(text);
    let value = if let Some(hex) = unsigned
        .strip_prefix("0x")
        .or_else(|| unsigned.strip_prefix("0X"))
    {
        let magnitude = hex_double(hex)?;
        if text.starts_with('-') {
            -magnitude
        } else {
            magnitude
        }
    } else {
        text.parse().ok()?
    };
    (value.is_finite() && value >= 0.0).then_some(value)
}

fn custom_kinds(text: &str) -> Value {
    Value::Array(
        text.split(',')
            .filter_map(|raw| {
                let kind = match trim_kotlin(raw).to_lowercase().as_str() {
                    "warmup" => return None,
                    "top" => "TOP",
                    "backoff" => "BACKOFF",
                    "drop" => "DROP",
                    "failure" => "FAILURE",
                    "rest_pause" => "REST_PAUSE",
                    _ => "WORKING",
                };
                Some(json!(kind))
            })
            .take(30)
            .collect(),
    )
}

fn equal(expected: &Value, claimed: &Value) -> bool {
    match (expected, claimed) {
        (Value::Number(left), Value::Number(right)) => match (exact(left), exact(right)) {
            (Ok(left), Ok(right)) => left == right,
            _ => false,
        },
        (Value::Object(left), Value::Object(right)) => {
            left.len() == right.len()
                && left
                    .iter()
                    .all(|(key, value)| right.get(key).is_some_and(|other| equal(value, other)))
        }
        _ => expected == claimed,
    }
}

/// Compare the historical raw scheme with its claimed interpretation.
///
/// `raw` is null or a string. The caller must already have validated
/// `claimed` against the frozen v2 scheme schema.
pub fn check_scheme(raw: &Value, claimed: &Value) -> Result<(), Refusal> {
    let raw = match raw {
        Value::Null => "",
        Value::String(raw) => raw,
        _ => return Err(Refusal::InvalidData),
    };
    let fields: std::collections::HashMap<_, _> = raw
        .split(';')
        .filter_map(|entry| entry.split_once('='))
        .map(|(key, value)| (trim_kotlin(key), trim_kotlin(value)))
        .filter(|&(key, _)| {
            matches!(
                key,
                "t" | "wr"
                    | "rr"
                    | "tr"
                    | "rir"
                    | "bo"
                    | "br"
                    | "bf"
                    | "bp"
                    | "rt"
                    | "ds"
                    | "dc"
                    | "dp"
                    | "ck"
            )
        })
        .collect();
    let field = |key| fields.get(key).copied().unwrap_or_default();
    let kind = match field("t") {
        kind @ ("top_backoff" | "drops" | "custom") => kind,
        _ => "straight",
    };
    let working = if matches!(kind, "straight" | "drops") {
        let working = range(field("rr"), (1, 1), 1, 500);
        if working["min"] != working["max"] {
            working
        } else {
            Value::Null
        }
    } else {
        Value::Null
    };
    let mut expected = json!({
        "type":kind,"warmupRamp":field("wr") == "1","workingRange":working,"topRange":null,
        "topRir":null,"backoffSets":null,"backoffRange":null,"backoffFixedKg":null,
        "backoffPercent":null,"restAfterTopSec":null,"dropSets":null,"dropCount":null,
        "dropPercent":null,"customKinds":null
    });
    let fixed = match kind {
        "top_backoff" => {
            let fixed = fixed_kg(field("bf"));
            expected["topRange"] = range(field("tr"), (4, 6), 1, 100);
            expected["topRir"] = range(field("rir"), (0, 1), 0, 10);
            expected["backoffSets"] = json!(integer(field("bo"), 2, 1, 10));
            expected["backoffRange"] = range(field("br"), (6, 8), 1, 100);
            if fixed.is_none() {
                expected["backoffPercent"] = json!(integer(field("bp"), 10, 5, 50));
            }
            expected["restAfterTopSec"] = json!(integer(field("rt"), 180, 30, 600));
            fixed
        }
        "drops" => {
            expected["dropSets"] = json!(integer(field("ds"), 1, 1, 30));
            expected["dropCount"] = json!(integer(field("dc"), 1, 1, 4));
            expected["dropPercent"] = json!(integer(field("dp"), 20, 5, 50));
            None
        }
        "custom" => {
            expected["customKinds"] = fields
                .get("ck")
                .map_or_else(|| json!([]), |value| custom_kinds(value));
            None
        }
        _ => None,
    };
    let claimed = claimed.as_object().ok_or(Refusal::InvalidData)?;
    let expected = expected.as_object().ok_or(Refusal::InvalidData)?;
    if claimed.len() == expected.len()
        && expected.iter().all(|(key, value)| {
            claimed
                .get(key)
                .is_some_and(|other| match (key.as_str(), fixed) {
                    ("backoffFixedKg", Some(fixed)) => other.as_f64() == Some(fixed),
                    _ => equal(value, other),
                })
        })
    {
        Ok(())
    } else {
        Err(Refusal::InvalidData)
    }
}
