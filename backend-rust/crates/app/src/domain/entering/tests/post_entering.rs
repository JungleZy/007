use crate::domain::entering::post_entering::*;

#[test]
fn stock_delimiters_and_word_counts_match_java_create_and_edit() {
    assert_eq!(
        normalize_stock_lines("A@B\r\nC##D", false),
        ["A", "B", "C##D"]
    );
    assert_eq!(
        normalize_stock_lines("A@B\r\nC##D", true),
        ["A@B", "C", "D"]
    );
    for (content, expected) in [("", 1), (" ", 0), ("   ", 0), ("A  B ", 3), (" A ", 2)] {
        assert_eq!(english_word_size(content), expected, "{content:?}");
    }
    assert_eq!(stock_word_size(Some(0), "\u{00a0}甲\u{00a0}"), 3);
}

#[test]
fn terminal_retry_requires_objects_and_preserves_parse_failures() {
    for content in ["", " \r\n", "null", " null "] {
        assert_eq!(content_array(Some(content)), Err("训练内容不能为空"));
    }
    assert_eq!(same_answers(Some("[1]"), Some("[2]")), Ok(false));
    assert_eq!(same_answers(Some("[{}]"), Some("[{}]")), Ok(true));
    assert_eq!(
        same_answers(Some("[]"), Some("bad json")),
        Err("训练内容必须为有效的数组")
    );
    assert_eq!(same_answers(Some("[]"), None), Ok(false));
    assert_eq!(
        same_answers(
            Some(r#"[{"font":"A","value":"A","trueOrfalse":true}]"#),
            Some(r#"[{"font":"A","value":"A","trueOrfalse":false}]"#)
        ),
        Ok(true)
    );
}
