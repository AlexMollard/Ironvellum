use ironvellum_contracts::{encode, loads};

#[test]
fn refuses_malformed_wire_data_with_fixed_codes() {
    for raw in [
        br#"{"a":1,"\u0061":2}"#.as_slice(),
        b"\"\xff\"",
        br#""\ud800""#,
        br#""\udfff""#,
        b"NaN",
        b"Infinity",
        b"1e309",
        b"1e-400",
        b"[[[]]]",
        b"00",
    ] {
        assert_eq!(loads(raw, 512, 2).unwrap_err().code(), "invalid_data");
    }
    assert_eq!(loads(b"null", 3, 0).unwrap_err().code(), "file_limit");
}
#[test]
fn computed_floats_and_parsed_decimals_keep_distinct_wire_spellings() {
    use ironvellum_contracts::wire::binary64;
    for (token, decimal, float) in [
        ("0.00", "0", "0.0"),
        ("-0.0", "0", "-0.0"),
        ("4e0", "4", "4.0"),
        ("1e-4", "0.0001", "0.0001"),
        ("1e-5", "0.00001", "1e-05"),
        ("1e-7", "1E-7", "1e-07"),
        ("1e15", "1E+15", "1000000000000000.0"),
        ("1e16", "1E+16", "1e+16"),
        ("1e20", "1E+20", "1e+20"),
        ("5e-324", "5E-324", "5e-324"),
        (
            "1.7976931348623157e308",
            "1.7976931348623157E+308",
            "1.7976931348623157e+308",
        ),
    ] {
        let parsed = loads(token.as_bytes(), 128, 0).unwrap();
        let generated = binary64(token.parse().unwrap()).unwrap();
        assert_eq!(encode(&parsed).unwrap(), decimal.as_bytes(), "{token}");
        assert_eq!(encode(&generated).unwrap(), float.as_bytes(), "{token}");
        assert_eq!(
            encode(&serde_json::json!([parsed, generated])).unwrap(),
            format!("[{decimal},{float}]").as_bytes()
        );
    }
    assert!(binary64(f64::INFINITY).is_err());
}
#[test]
fn setup_depth_is_supported_and_untrusted_depth_is_capped() {
    for (levels, valid) in [(128, true), (129, false), (10000, false)] {
        let data = format!("{}0{}", "[".repeat(levels), "]".repeat(levels));
        assert_eq!(
            ironvellum_contracts::loads(data.as_bytes(), data.len(), usize::MAX).is_ok(),
            valid
        );
    }
}

#[test]
fn retains_order_decimal_precision_and_numeric_kinds() {
    let raw =
        br#"{"z":-0,"a":123456789012345678901234567890.0001,"b":3.0,"n":0e-1000000,"s":"[{\"}]"}"#;
    let val = loads(raw, raw.len(), 1).unwrap();
    assert_eq!(
        encode(&val).unwrap(),
        br#"{"z":0,"a":123456789012345678901234567890.0001,"b":3.0,"n":0,"s":"[{\"}]"}"#
    );
    assert_eq!(loads(br#""\ud83d\ude00""#, 20, 0).unwrap(), "😀");
}

#[test]
fn bounds_tokens_and_falls_back_to_valid_original_spelling() {
    let token = format!("{}e-128", "1".repeat(123));
    assert_eq!(
        encode(&loads(token.as_bytes(), 200, 0).unwrap()).unwrap(),
        token.as_bytes()
    );
    let over = format!("1{token}");
    assert_eq!(
        loads(over.as_bytes(), 200, 0).unwrap_err().code(),
        "invalid_data"
    );
    assert!(loads(b"5e-324", 8, 0).is_ok());
}

#[test]
fn distinguishes_lexical_integers_and_bounds_zero_arithmetic() {
    for invalid in ["0e1000000000000000000", "0e-1999999999999999998"] {
        assert!(loads(invalid.as_bytes(), 128, 0).is_err(), "{invalid}");
    }
    use ironvellum_contracts::{schema::exact, wire::lexical_integer};
    for (raw, integral_token, stored) in [
        ("-0", true, "0"),
        ("-0.0", false, "0e0"),
        ("0e-1000000", false, "0e0"),
        ("4", true, "4"),
        ("4.0", false, "4.0"),
        ("4e0", false, "4e0"),
    ] {
        let value = loads(raw.as_bytes(), 128, 0).unwrap();
        let number = value.as_number().unwrap();
        assert_eq!(lexical_integer(number), integral_token);
        assert_eq!(number.as_str(), stored);
        assert!(exact(number).unwrap().is_integer());
    }
}

#[test]
fn invalid_delimiters_literals_numbers_and_control_characters() {
    for token in [
        "",
        "null true",
        "[,1]",
        "[1,]",
        "{\"a\":1,}",
        "{ }x",
        "+1",
        "-",
        "01",
        ".2",
        "1.",
        "1e",
        "True",
        "1e309",
        "\"\u{0}\"",
        "\u{feff}1",
    ] {
        assert_eq!(
            loads(token.as_bytes(), 128, 4).unwrap_err().code(),
            "invalid_data",
            "{token:?}"
        );
    }
    for (raw, rendered) in [
        ("0.000001", "0.000001"),
        ("0.0000001200", "1.200E-7"),
        ("1e+20", "1E+20"),
        ("100e-2", "1.00"),
        ("12e0", "12"),
        ("-0.000E+14", "0"),
    ] {
        assert_eq!(
            encode(&loads(raw.as_bytes(), 128, 0).unwrap()).unwrap(),
            rendered.as_bytes()
        );
    }
}
