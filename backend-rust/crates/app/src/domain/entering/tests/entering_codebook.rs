use crate::domain::entering::entering_codebook::*;
use serde_json::json;
use serde_json::Value;

#[test]
fn capture_rebuilds_source_and_truth_instead_of_trusting_client_preview() {
    let source = freeze(
        0,
        Some(r#"[{"font":"重","pys":"zhong"},{"font":"重","pys":"chong"}]"#),
    )
    .unwrap();
    let result = capture(&source, Some(r#"[{"font":"伪造","value":"zhong","trueOrfalse":false},{"value":"ch","isFocus":true}]"#)).unwrap();
    let rows: Value = serde_json::from_str(&result.content).unwrap();
    assert_eq!((result.correct, result.errors), (1, 0));
    assert_eq!(rows[0]["font"], "重");
    assert_eq!(rows[0]["trueOrfalse"], true);
    assert_eq!(rows[1]["isFocus"], false);
    assert_eq!(rows[1]["trueOrfalse"], Value::Null);
    assert_eq!(rows[1]["pys"][2], json!({"py":"o","trueOrfalse":null}));
}

#[test]
fn invalid_stock_and_answer_lengths_do_not_create_empty_successes() {
    assert!(freeze(0, None).is_err());
    assert!(freeze(0, Some("[]")).is_err());
    let source = freeze(0, Some(r#"[{"font":"甲","pys":"a"}]"#)).unwrap();
    assert!(capture(&source, Some("[]")).is_err());
    assert!(capture(&source, Some(r#"[{"value":"ab"}]"#)).is_err());
    assert!(capture(&source, Some(r#"[{"value":3}]"#)).is_err());
    let result = capture(&source, Some(r#"[{"value":"x"}]"#)).unwrap();
    assert_eq!((result.correct, result.errors), (0, 1));
}

#[test]
fn article_splits_characters_and_normalizes_keyboard_punctuation() {
    let source = freeze(4, Some(r#"["A，ü"]"#)).unwrap();
    let codes: Vec<_> = source
        .iter()
        .map(|row| (row.font.as_str(), row.code.as_str()))
        .collect();
    assert_eq!(codes, [("A", "A"), ("，", ","), ("ü", "v")]);
    assert!(freeze(5, Some(r#"["A"]"#)).is_err());
}

#[test]
fn same_answers_reconstruct_java_capture_identity_after_cutover() {
    let source = freeze(
        0,
        Some(r#"[{"font":"重","pys":"zhong"},{"font":"重","pys":"chong"}]"#),
    )
    .unwrap();
    let captured = capture(&source, Some(r#"[{"value":"\u2028"},{"value":""}]"#)).unwrap();
    let saved_by_java = r#"[{"font":"重","value":"\u2028","isFocus":false,"trueOrfalse":null,"pys":[{"py":"z","trueOrfalse":false},{"py":"h","trueOrfalse":null},{"py":"o","trueOrfalse":null},{"py":"n","trueOrfalse":null},{"py":"g","trueOrfalse":null}]},{"font":"重","value":"","isFocus":false,"trueOrfalse":null,"pys":[{"py":"c","trueOrfalse":null},{"py":"h","trueOrfalse":null},{"py":"o","trueOrfalse":null},{"py":"n","trueOrfalse":null},{"py":"g","trueOrfalse":null}]}]"#;
    assert_eq!(captured.content, saved_by_java);
}
