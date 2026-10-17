use crate::domain::theory::exam::*;
use serde_json::json;
use serde_json::Value;

#[test]
fn numeric_exam_ids_are_looked_up_instead_of_becoming_new_exams() {
    assert_eq!(
        request_text(Some(&json!(987654))).unwrap().as_deref(),
        Some("987654")
    );
    assert!(request_text(Some(&json!({"id": "other"}))).is_err());
}

#[test]
fn exam_timestamps_match_java_utc_plus_eight_for_countdown() {
    let instant = chrono::DateTime::from_timestamp(0, 0).unwrap();
    assert_eq!(exam_time(instant), "1970-01-01 08:00:00");
}

#[test]
fn active_paper_hides_answers_and_preserves_completion_slots() {
    let source = r#"[{"id":"q","topic":"甲________乙(___)丙$_$","answer":"[\"甲\",\"乙\",\"丙\"]","analysis":"解析","score":5}]"#;
    let projected: Value =
        serde_json::from_str(&project_questions(source, "completion").unwrap()).unwrap();
    assert_eq!(projected[0]["answer"], r#"["","",""]"#);
    assert!(projected[0].get("analysis").is_none());
    assert_eq!(completion_slots("________________"), 2);
    assert_eq!(projected[0]["score"], 5);
}

#[test]
fn snapshot_integer_coercion_cannot_silently_zero_or_wrap_scores() {
    let source = json!([{"id":"q", "score":10.0, "sort":"0", "type":1}]);
    let snapshot = question_list_json(source.as_array().unwrap()).unwrap();
    let mut answers = json!({"singleChoice":[{"id":"q", "answer":null}]});
    assert_eq!(
        score_section(&snapshot, &mut answers, "singleChoice").unwrap(),
        10
    );
    assert!(question_list_json(&[json!({"score":2147483648_i64})]).is_err());
    assert!(score_section(
        r#"[{"id":"q","score":2147483648}]"#,
        &mut answers,
        "singleChoice"
    )
    .is_err());
}

#[test]
fn short_answer_scoring_keeps_java_unicode_boundary_whitespace() {
    for (boundary, expected) in [(" \t\r\n\u{001f}", 10), ("\u{3000}", 0), ("\u{00a0}", 0)] {
        let padded = format!("{boundary}标准步骤{boundary}");
        let snapshot = json!([{"id":"q","answer":"标准步骤","score":10}]).to_string();
        let mut answers = json!({"shortAnswer":[{"id":"q","answer":padded}]});
        assert_eq!(
            score_section(&snapshot, &mut answers, "shortAnswer").unwrap(),
            expected
        );
        assert_eq!(answers["shortAnswer"][0]["teacherScore"], expected);
        for stored in [padded.clone(), json!(padded).to_string()] {
            let snapshot = json!([{"id":"q","answer":stored,"score":10}]).to_string();
            let mut answers = json!({"shortAnswer":[{"id":"q","answer":"标准步骤"}]});
            assert_eq!(
                score_section(&snapshot, &mut answers, "shortAnswer").unwrap(),
                expected
            );
        }
    }
}

#[test]
fn completion_scoring_trims_csv_boundaries_not_unicode_or_each_blank() {
    for (boundary, expected) in [(" \t\r\n\u{001f}", 10), ("\u{3000}", 0), ("\u{00a0}", 0)] {
        let padded = json!([format!("{boundary}甲"), format!("乙{boundary}")]);
        let snapshot = json!([{"id":"q","answer":"[\"甲\",\"乙\"]","score":10}]).to_string();
        let mut answers = json!({"completion":[{"id":"q","answer":padded}]});
        assert_eq!(
            score_section(&snapshot, &mut answers, "completion").unwrap(),
            expected
        );
        assert_eq!(answers["completion"][0]["teacherScore"], expected);
        for stored in [format!("{boundary}甲,乙{boundary}"), padded.to_string()] {
            let snapshot = json!([{"id":"q","answer":stored,"score":10}]).to_string();
            let mut answers = json!({"completion":[{"id":"q","answer":["甲","乙"]}]});
            assert_eq!(
                score_section(&snapshot, &mut answers, "completion").unwrap(),
                expected
            );
        }
    }
    assert_eq!(normalize_answer(&json!(["甲 ", " 乙"])).unwrap(), "甲 , 乙");
}

#[test]
fn settlement_uses_first_answer_and_overwrites_client_awards() {
    let snapshot = r#"[{"id":"q","answer":"[\"A\",\"B\"]","score":10}]"#;
    let mut answers = json!({"multipleChoice":[{"id":"q","answer":["A","B"],"teacherScore":999},{"id":"q","answer":["X"],"teacherScore":999}]});
    assert_eq!(
        score_section(snapshot, &mut answers, "multipleChoice").unwrap(),
        10
    );
    assert_eq!(answers["multipleChoice"][0]["teacherScore"], 10);
    let mut wrong = json!({"multipleChoice":[{"id":"q","answer":["X"],"teacherScore":999},{"id":"q","answer":["A","B"]}]});
    assert_eq!(
        score_section(snapshot, &mut wrong, "multipleChoice").unwrap(),
        0
    );
    assert_eq!(wrong["multipleChoice"][0]["teacherScore"], 0);
}

#[test]
fn blank_non_object_and_non_array_answers_match_zero_score_behavior() {
    assert!(parse_answer_content(Some("  ")).unwrap().is_none());
    assert!(parse_answer_content(Some("[]")).unwrap().is_none());
    assert!(parse_answer_content(Some("not json")).is_err());
    let mut answers = json!({"singleChoice": [null, 1, {"id":"unrelated"}], "judge": null});
    let snapshot = r#"[{"id":"q","answer":"\"A\"","score":5}]"#;
    assert_eq!(
        score_section(snapshot, &mut answers, "singleChoice").unwrap(),
        0
    );
    assert_eq!(score_section(snapshot, &mut answers, "judge").unwrap(), 0);
    assert_eq!(normalize_answer(&json!(["", "b"])).unwrap(), ",b");
}

#[test]
fn retry_accepts_java_json_property_order_but_not_answer_changes() {
    let java = r#"{"singleChoice":[{"id":"q","answer":"A","teacherScore":5}]}"#;
    let rust = r#"{"singleChoice":[{"teacherScore":5,"answer":"A","id":"q"}]}"#;
    assert!(same_answer_content(Some(java), Some(rust)));
    assert!(!same_answer_content(
        Some(java),
        Some(r#"{"singleChoice":[{"id":"q","answer":"B","teacherScore":0}]}"#)
    ));
    assert!(!same_answer_content(None, Some("")));
}
