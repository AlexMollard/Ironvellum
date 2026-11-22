use crate::Value;
use serde_json::{Map, Number};
use std::{fmt, str::FromStr};

pub const MAX_CONTEXT: usize = 8 << 20;
pub const MAX_FRAME: usize = 64 << 10;
pub const MAX_RESULT: usize = 1 << 20;
/// Maximum supported nesting, including client setup documents.
pub const MAX_DEPTH: usize = 128;
pub const MAX_TIME: u64 = 253402300799999;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Refusal {
    InvalidData,
    FileLimit,
    UnsupportedMovement,
    InvalidCursor,
    InvalidOffset,
    NotFound,
    ResultLimit,
}
impl Refusal {
    pub const fn code(self) -> &'static str {
        match self {
            Self::InvalidData => "invalid_data",
            Self::FileLimit => "file_limit",
            Self::UnsupportedMovement => "unsupported_movement",
            Self::InvalidCursor => "invalid_cursor",
            Self::InvalidOffset => "invalid_offset",
            Self::NotFound => "not_found",
            Self::ResultLimit => "result_limit",
        }
    }
}
impl fmt::Display for Refusal {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.code())
    }
}
impl std::error::Error for Refusal {}
use Refusal::InvalidData;

pub fn lexical_integer(n: &Number) -> bool {
    !n.as_str().contains(['.', 'e', 'E'])
}

/// Produce the shortest round-tripping binary64 spelling used by the original
/// renderer. Unlike a parsed Decimal, a computed float retains a decimal point
/// for integral values and uses scientific notation outside [1e-4, 1e16).
pub fn binary64(value: f64) -> Result<Value, Refusal> {
    let shortest = Number::from_f64(value.abs())
        .ok_or(InvalidData)?
        .to_string();
    let sign = if value.is_sign_negative() { "-" } else { "" };
    let rendered = if value == 0.0 {
        format!("{sign}0.0")
    } else {
        let (mantissa, exponent) = shortest.split_once(['e', 'E']).unwrap_or((&shortest, "0"));
        let integer_len = mantissa.find('.').unwrap_or(mantissa.len());
        let all = mantissa.replace('.', "");
        let significant = all.trim_start_matches('0');
        let leading_zeros = all.len() - significant.len();
        let digits = significant.trim_end_matches('0');
        let power = exponent.parse::<i32>().map_err(|_| InvalidData)? + integer_len as i32
            - leading_zeros as i32
            - 1;
        if !(-4..16).contains(&power) {
            let fraction = if digits.len() == 1 {
                String::new()
            } else {
                format!(".{}", &digits[1..])
            };
            format!("{sign}{}{fraction}e{power:+03}", &digits[..1])
        } else if power < 0 {
            format!("{sign}0.{}{digits}", "0".repeat((-power - 1) as usize))
        } else {
            let split = power as usize + 1;
            if split < digits.len() {
                format!("{sign}{}.{}", &digits[..split], &digits[split..])
            } else {
                format!("{sign}{digits}{}.0", "0".repeat(split - digits.len()))
            }
        }
    };
    Ok(Value::Number(Number::from_string_unchecked(rendered)))
}

/// Decimal.__str__ chooses ordinary notation for adjusted exponents >= -6
/// unless the stored exponent is positive. Trailing coefficient zeros matter.
fn spelling(token: &str) -> Result<String, Refusal> {
    if token.len() > 128 {
        return Err(InvalidData);
    }
    Number::from_str(token).map_err(|_| InvalidData)?;
    let magnitude = token.trim_start_matches('-');
    let (mantissa, exponent) = magnitude.split_once(['e', 'E']).unwrap_or((magnitude, "0"));
    let exponent: i64 = exponent.parse().map_err(|_| InvalidData)?;
    let fractional = mantissa.split_once('.').map_or(0, |(_, f)| f.len());
    let scale = exponent.checked_sub(fractional as i64).ok_or(InvalidData)?;
    let digits = mantissa.replace('.', "");
    let digits = digits.trim_start_matches('0');
    if digits.is_empty() {
        // An exact 64-bit CPython Decimal rejects clamping outside MIN_ETINY..MAX_EMAX.
        if !(-1_999_999_999_999_999_997..=999_999_999_999_999_999).contains(&scale) {
            return Err(InvalidData);
        }
        return Ok("0".into());
    }
    let approximate = token.parse::<f64>().map_err(|_| InvalidData)?;
    if !approximate.is_finite() || approximate == 0.0 {
        return Err(InvalidData);
    }
    if !token.contains(['.', 'E', 'e']) {
        return Ok(token.into());
    }
    let adjusted = scale + digits.len() as i64 - 1;
    let sign = if token.starts_with('-') { "-" } else { "" };
    let mut out = sign.to_owned();
    if scale > 0 || adjusted < -6 {
        out.push_str(&digits[..1]);
        if digits.len() > 1 {
            out.push('.');
            out.push_str(&digits[1..]);
        }
        out.push_str(&format!("E{adjusted:+}"));
    } else if adjusted < 0 {
        out.push_str("0.");
        out.push_str(&"0".repeat((-adjusted - 1) as usize));
        out.push_str(digits);
    } else {
        let split = (adjusted + 1) as usize;
        out.push_str(&digits[..split]);
        if split < digits.len() {
            out.push('.');
            out.push_str(&digits[split..]);
        }
    }
    Ok(if out.len() > 128 { token.into() } else { out })
}

struct Reader<'a> {
    raw: &'a [u8],
    pos: usize,
    limit: usize,
}
impl Reader<'_> {
    fn peek(&self) -> Option<u8> {
        self.raw.get(self.pos).copied()
    }
    fn spaces(&mut self) {
        while matches!(self.peek(), Some(b' ' | b'\n' | b'\r' | b'\t')) {
            self.pos += 1;
        }
    }
    fn eat(&mut self, c: u8) -> bool {
        if self.peek() == Some(c) {
            self.pos += 1;
            true
        } else {
            false
        }
    }
    fn string(&mut self) -> Result<String, Refusal> {
        let start = self.pos;
        if !self.eat(b'"') {
            return Err(InvalidData);
        }
        while let Some(c) = self.peek() {
            self.pos += 1;
            match c {
                b'\\' if self.peek().is_some() => self.pos += 1,
                b'"' => {
                    return serde_json::from_slice(&self.raw[start..self.pos])
                        .map_err(|_| InvalidData);
                }
                _ => {}
            }
        }
        Err(InvalidData)
    }
    fn value(&mut self, depth: usize) -> Result<Value, Refusal> {
        self.spaces();
        if self.peek() == Some(b'"') {
            return self.string().map(Value::String);
        }
        if matches!(self.peek(), Some(b'{' | b'[')) {
            if depth >= self.limit {
                return Err(InvalidData);
            }
            let object = self.eat(b'{');
            if !object {
                self.pos += 1;
            }
            let end = if object { b'}' } else { b']' };
            let mut map = Map::new();
            let mut array = Vec::new();
            self.spaces();
            if !self.eat(end) {
                loop {
                    if object {
                        self.spaces();
                        let key = self.string()?;
                        if map.contains_key(&key) {
                            return Err(InvalidData);
                        }
                        self.spaces();
                        if !self.eat(b':') {
                            return Err(InvalidData);
                        }
                        map.insert(key, self.value(depth + 1)?);
                    } else {
                        array.push(self.value(depth + 1)?);
                    }
                    self.spaces();
                    if self.eat(end) {
                        break;
                    }
                    if !self.eat(b',') {
                        return Err(InvalidData);
                    }
                }
            }
            return Ok(if object {
                Value::Object(map)
            } else {
                Value::Array(array)
            });
        }
        let start = self.pos;
        while self
            .peek()
            .is_some_and(|c| !matches!(c, b' ' | b'\n' | b'\t' | b'\r' | b'}' | b']' | b','))
        {
            self.pos += 1;
        }
        let token = std::str::from_utf8(&self.raw[start..self.pos]).map_err(|_| InvalidData)?;
        match token {
            "null" => Ok(Value::Null),
            "true" => Ok(Value::Bool(true)),
            "false" => Ok(Value::Bool(false)),
            _ => {
                let normalized = spelling(token)?;
                // Keep the distinction between a JSON integer and a parsed
                // Decimal when canonical Decimal text has no point/exponent.
                // Ne0 is valid exact JSON and is emitted as N by encode.
                let stored =
                    if token.contains(['.', 'e', 'E']) && !normalized.contains(['.', 'e', 'E']) {
                        format!("{normalized}e0")
                    } else {
                        normalized
                    };
                Ok(Value::Number(Number::from_string_unchecked(stored)))
            }
        }
    }
}

pub fn loads(raw: &[u8], cap: usize, depth_limit: usize) -> Result<Value, Refusal> {
    if raw.len() > cap {
        return Err(Refusal::FileLimit);
    }
    let mut reader = Reader {
        raw,
        pos: 0,
        limit: depth_limit.min(MAX_DEPTH),
    };
    let value = reader.value(0)?;
    reader.spaces();
    if reader.pos != raw.len() {
        return Err(InvalidData);
    }
    Ok(value)
}

/// Render an authored value. Decode untrusted input with `loads` before
/// embedding it: that step canonicalizes Decimals while preserving integer
/// syntax. Generated binary64 numbers use `binary64`. Every emitted numeric
/// token still passes the original grammar, size and finiteness checks.
pub fn encode(value: &Value) -> Result<Vec<u8>, Refusal> {
    fn write(v: &Value, out: &mut Vec<u8>) -> Result<(), Refusal> {
        match v {
            Value::Number(n) => {
                let token = n.as_str();
                let emitted = token
                    .strip_suffix("e0")
                    .filter(|int| !int.contains(['.', 'e', 'E']))
                    .unwrap_or(token);
                spelling(token)?;
                out.extend_from_slice(emitted.as_bytes());
            }
            Value::Object(map) => {
                out.push(b'{');
                for (i, (k, child)) in map.iter().enumerate() {
                    if i > 0 {
                        out.push(b',');
                    }
                    serde_json::to_writer(&mut *out, k).map_err(|_| InvalidData)?;
                    out.push(b':');
                    write(child, out)?;
                }
                out.push(b'}');
            }
            Value::Array(a) => {
                out.push(b'[');
                for (i, child) in a.iter().enumerate() {
                    if i > 0 {
                        out.push(b',');
                    }
                    write(child, out)?;
                }
                out.push(b']');
            }
            _ => serde_json::to_writer(out, v).map_err(|_| InvalidData)?,
        }
        Ok(())
    }
    let mut out = Vec::new();
    write(value, &mut out)?;
    Ok(out)
}
