use crate::domain::telegram::telegram_write::aggregate_time_text;
use crate::domain::telegram::telegram_write::timing_rates;
use serde_json::json;

#[test]
fn timing_properties_follow_jackson_integer_binding() {
    assert_eq!(
        timing_rates(&json!({})),
        Ok([1, 80, 81, 240, 81, 240, 241, 400])
    );
    assert_eq!(
        timing_rates(&json!({
            "rateDotMinMs":" 40 ", "rateDotMaxMs":80.9,
            "rateLineMinMs":81.9, "rateLineMaxMs":"240",
            "rateIntervalMinMs":" 81", "rateIntervalMaxMs":240.1,
            "bigIntervalMinMs":241, "bigIntervalMaxMs":"400 "
        })),
        Ok([40, 80, 81, 240, 81, 240, 241, 400])
    );
    assert_eq!(timing_rates(&json!({"rateDotMinMs":-0.9})).unwrap()[0], 0);
    for field in [
        "rateDotMinMs",
        "rateDotMaxMs",
        "rateLineMinMs",
        "rateLineMaxMs",
        "rateIntervalMinMs",
        "rateIntervalMaxMs",
        "bigIntervalMinMs",
        "bigIntervalMaxMs",
    ] {
        for invalid in [
            json!(null),
            json!(true),
            json!([]),
            json!({}),
            json!(""),
            json!("40.5"),
            json!("2147483648"),
            json!(2147483648_i64),
            json!(-2147483649_i64),
            json!(2147483647.9),
        ] {
            assert!(timing_rates(&json!({field:invalid})).is_err(), "{field}");
        }
    }
    assert_eq!(
        timing_rates(&json!({"bigIntervalMaxMs":i32::MAX})).unwrap()[7],
        i32::MAX
    );
    assert_eq!(
        timing_rates(&json!({"bigIntervalMaxMs":"2147483647"})).unwrap()[7],
        i32::MAX
    );
    for body in [
        json!({"rateDotMinMs":-1}),
        json!({"rateDotMaxMs":1}),
        json!({"rateDotMinMs":80.9}),
        json!({"bigIntervalMaxMs":240.9}),
    ] {
        assert!(timing_rates(&body).is_err());
    }
}

#[test]
fn aggregate_strings_preserve_java_double_notation() {
    for (value, expected) in [
        (0.0, "0.0"),
        (12.0, "12.0"),
        (12.5, "12.5"),
        (0.000001, "1.0E-6"),
        (10_000_000.0, "1.0E7"),
    ] {
        assert_eq!(aggregate_time_text(value), expected);
    }
}
