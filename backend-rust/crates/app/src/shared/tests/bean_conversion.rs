use crate::shared::bean_conversion::string_to_map_list;
use serde_json::json;

#[test]
fn hutool_empty_and_failed_map_conversion_shapes() {
    for stored in ["", "   ", "[]", "[ ]", ",", "[,]"] {
        assert_eq!(
            string_to_map_list(Some(stored)),
            Some(json!([])),
            "{stored:?}"
        );
    }
    for stored in [
        "null",
        "not json",
        " [ ] ",
        "[null]",
        "[{}]",
        "[\"a\"]",
        "[{\"codeMessage\":\"1234\",\"sort\":1}]",
        "{}",
    ] {
        assert_eq!(string_to_map_list(Some(stored)), None, "{stored:?}");
    }
    assert_eq!(string_to_map_list(None), None);
}
