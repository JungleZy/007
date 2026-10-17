use crate::platform::envelope::Envelope;
use serde_json::Value;

#[test]
fn missing_response_data_serializes_as_json_null() {
    let body = serde_json::to_value(Envelope::<Value> {
        code: 203,
        data: None,
        message: "token不能为空".into(),
        description: String::new(),
    })
    .expect("envelope must serialize");
    assert_eq!(body["data"], Value::Null);
    assert_eq!(body["code"], 203);
}
