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
