use crate::domain::group_net::group_net::Error;
use crate::domain::group_net::group_net_write::*;
use rust_decimal::Decimal;
use serde_json::json;
use serde_json::Value;

const RULES: &str = r#"[{"paramName":"网路地址","weight":2},{"paramName":"单台地址","weight":3},{"paramName":"信道","weight":0.25}]"#;
const TOPIC: &str =
    include_str!("../../../../../../../backend/src/test/resources/group-net-j210-topic.json");

fn answer() -> Value {
    let mut serials = vec![json!({}); 20];
    serials[0] = json!({"networkdress": "0319"});
    serials[19] = json!({"dressname": 299});
    json!({
        "chananel1": [{"indexs": 1, "receptionChananel": 0, "sendChananel": "29.0"}],
        "serialNumbers": serials,
    })
}

#[test]
fn scores_each_decimal_cell_using_its_serial_and_channel_mapping() {
    let frozen = freeze(7, Some(RULES), Some(TOPIC)).unwrap();
    let (score, details) =
        calculate(Some(7), Some(TOPIC), &frozen, Some(&answer().to_string())).unwrap();
    assert_eq!(score, Decimal::new(550, 2));
    let details: Vec<Value> = serde_json::from_str(&details).unwrap();
    assert_eq!(details.len(), 149);
    assert_eq!(
        details[0],
        json!({"xy": [4, 2], "actual": "0319", "correct": true, "weight": 2, "score": 2})
    );
    assert_eq!(details[1]["actual"], 299);
    assert_eq!(details[2]["actual"], 0);
    assert_eq!(details[3]["correct"], true);
    assert_eq!(details[4]["actual"], "");
    assert_eq!(details[4]["correct"], false);
    assert!(matches!(
        calculate(Some(8), Some(TOPIC), &frozen, Some("{}")),
        Err(Error::Invalid(_))
    ));
}

#[test]
fn zero_weights_preserve_correctness_without_awarding_unanswered_zero_channels() {
    let rules = RULES
        .replace("2}", "0}")
        .replace("3}", "0}")
        .replace("0.25}", "0}");
    let frozen = freeze(7, Some(&rules), Some(TOPIC)).unwrap();
    let (score, details) =
        calculate(Some(7), Some(TOPIC), &frozen, Some(&answer().to_string())).unwrap();
    assert_eq!(score, Decimal::ZERO);
    let details: Vec<Value> = serde_json::from_str(&details).unwrap();
    assert_eq!(details[2]["correct"], true);
    assert_eq!(details[4]["correct"], false);
    let (score, details) = calculate(Some(7), Some(TOPIC), &frozen, Some("{}")).unwrap();
    assert_eq!(score, Decimal::ZERO);
    let details: Vec<Value> = serde_json::from_str(&details).unwrap();
    assert!(details
        .iter()
        .all(|cell| cell["correct"] == false && cell["actual"] == ""));
}

#[test]
fn invalid_or_incomplete_templates_cannot_freeze_scoring_rules() {
    let mut topic: Value = serde_json::from_str(TOPIC).unwrap();
    topic.as_array_mut().unwrap().pop();
    assert!(matches!(
        freeze(7, Some(RULES), Some(&topic.to_string())),
        Err(Error::Invalid(_))
    ));

    let mut topic: Value = serde_json::from_str(TOPIC).unwrap();
    topic[1]["xy"] = json!([0, 0]);
    assert!(matches!(
        freeze(7, Some(RULES), Some(&topic.to_string())),
        Err(Error::Invalid(_))
    ));

    let mut topic: Value = serde_json::from_str(TOPIC).unwrap();
    topic[3]["value"]["xdValues"] = json!(2);
    assert!(matches!(
        freeze(7, Some(RULES), Some(&topic.to_string())),
        Err(Error::Invalid(_))
    ));
    assert!(matches!(
        freeze(7, Some("[]"), Some(TOPIC)),
        Err(Error::Invalid(_))
    ));
    assert!(matches!(
        freeze(7, Some(&RULES.replace("0.25", "0.251")), Some(TOPIC)),
        Err(Error::Invalid(_))
    ));
}

#[test]
fn rejects_duplicate_channels_and_malformed_decimal_answers() {
    let frozen = freeze(7, Some(RULES), Some(TOPIC)).unwrap();
    let mut duplicate = answer();
    let channel = duplicate["chananel1"][0].clone();
    duplicate["chananel1"].as_array_mut().unwrap().push(channel);
    assert!(matches!(
        calculate(Some(7), Some(TOPIC), &frozen, Some(&duplicate.to_string())),
        Err(Error::Invalid(_))
    ));

    let mut malformed = answer();
    malformed["serialNumbers"][0]["networkdress"] = json!("319garbage");
    assert!(matches!(
        calculate(Some(7), Some(TOPIC), &frozen, Some(&malformed.to_string())),
        Err(Error::Invalid(_))
    ));
    malformed["serialNumbers"][0]["networkdress"] = json!(true);
    assert!(matches!(
        calculate(Some(7), Some(TOPIC), &frozen, Some(&malformed.to_string())),
        Err(Error::Invalid(_))
    ));

    let mut precise = answer();
    precise["chananel1"][0]["sendChananel"] = json!("29.000000000000000000000000000001");
    let (score, _) = calculate(Some(7), Some(TOPIC), &frozen, Some(&precise.to_string())).unwrap();
    assert_eq!(score, Decimal::new(525, 2));
}

#[test]
fn retries_compare_arbitrary_precision_numbers_without_changing_json_types() {
    assert!(same_answer(
        Some(r#"{"a":1.00,"b":[null,"x"]}"#),
        Some(r#"{"b":[null,"x"],"a":1e0}"#)
    )
    .unwrap());
    assert!(!same_answer(
        Some(r#"{"a":[0.10000000000000001]}"#),
        Some(r#"{"a":[0.10000000000000002]}"#)
    )
    .unwrap());
    assert!(!same_answer(Some(r#"{"a":"1"}"#), Some(r#"{"a":1}"#)).unwrap());
    assert!(same_answer(
        Some(r#"{"a":1000.00,"z":-0.0}"#),
        Some(r#"{"a":1e3,"z":0}"#)
    )
    .unwrap());
    assert!(!same_answer(Some(r#"{"a":[1,2]}"#), Some(r#"{"a":[2,1]}"#)).unwrap());
}
