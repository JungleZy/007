use serde_json::{json, Value};

// PojoUtils copies String -> List<Map> through Hutool unWrap/splitTrim, not JSON.
// Any nonempty token fails Map conversion and the ignoreError copy leaves null.
pub(crate) fn string_to_map_list(stored: Option<&str>) -> Option<Value> {
    let stored = stored?;
    let unwrapped = stored
        .strip_prefix('[')
        .and_then(|text| text.strip_suffix(']'))
        .unwrap_or(stored);
    unwrapped
        .split(',')
        .all(|token| token.trim().is_empty())
        .then(|| json!([]))
}

#[cfg(test)]
mod tests {
    use super::string_to_map_list;
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
}
