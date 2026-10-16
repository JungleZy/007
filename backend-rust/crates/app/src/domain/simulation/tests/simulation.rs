use crate::domain::simulation::simulation::*;
use serde_json::json;

#[test]
fn channel_room_id_accepts_jackson_integer_inputs() {
    for (body, expected) in [
        (json!({}), None),
        (json!({"roomId": null}), None),
        (json!({"roomId": ""}), None),
        (json!({"roomId": " 42 "}), Some(42)),
        (json!({"roomId": 42}), Some(42)),
        (json!({"roomId": 42.9}), Some(42)),
        (json!({"roomId": -1.9}), Some(-1)),
    ] {
        assert_eq!(
            serde_json::from_value::<ChannelRequest>(body)
                .unwrap()
                .room_id,
            expected
        );
    }
    for value in [json!("bad"), json!(true), json!([]), json!(2147483648_i64)] {
        assert!(serde_json::from_value::<ChannelRequest>(json!({"roomId": value})).is_err());
    }
}
