use crate::domain::telegraph_key::synthetical::*;
use serde_json::json;

#[test]
fn score_excludes_unanswered_rows_and_caps_overlong_input() {
    let rows = json!([
        {"text":"1234","value":"12345678","isFocus":true},
        {"text":"5678","value":"5678","isFocus":true},
        {"text":"9999","value":"","isFocus":false}
    ]);
    assert_eq!(metrics(&rows, 60_000).unwrap(), (3, 1, 50.0, "8.00".into()));
}

#[test]
fn score_counts_utf16_units_and_handles_zero_elapsed_time() {
    let rows = json!([{"text":"\u{1F600}","value":"\u{1F600}","isFocus":true}]);
    assert_eq!(
        metrics(&rows, 60_000).unwrap(),
        (1, 0, 100.0, "2.00".into())
    );
    assert_eq!(metrics(&rows, 0).unwrap(), (1, 0, 100.0, "0".into()));
    assert!(metrics(&rows, -1).is_err());
}
