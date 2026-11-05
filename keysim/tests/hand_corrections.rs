use keysim::{console, keying, morse, sinks, timeline::Timeline};
use serde_json::{json, Value};

fn script(text: &str) -> Timeline {
    console::build_timeline(&json!({"key":"hand", "text":text, "alphabet":"letter", "tail":"none"})).unwrap()
}

#[test]
fn manual_current_correction_supports_inside_and_after_a_group() {
    for (text, expected, inside) in [
        ("AB?ABCD EFGH", vec!["ABCD", "EFGH"], true),
        ("ABCD ? EFGH IJKL", vec!["EFGH", "IJKL"], false),
        ("ABCD?EFGHIJKL", vec!["EFGH", "IJKL"], false),
    ] {
        let timeline = script(text);
        assert_eq!(timeline.groups, expected);
        let correction = timeline.chars.iter().find(|item| item.value == "改错当前组").unwrap();
        assert!(correction.control);
        assert_eq!(correction.group, Some(0));
        assert_eq!(correction.codes.iter().map(u8::to_string).collect::<String>(), morse::CONTROL_ALTER);
        let threshold = timeline.plan.gap * 3.51;
        assert_eq!(correction.leading_gap.unwrap() < threshold, inside);
        assert!(correction.leading_gap.unwrap() > timeline.plan.gap * 1.51);
    }
}

#[test]
fn previous_correction_after_four_codes_is_identical_with_or_without_space() {
    let joined = script("AAAA BBBB CCCC XXXX EEEE/DDDD FFFF");
    let spaced = script("AAAA BBBB CCCC XXXX EEEE / DDDD FFFF");
    assert_eq!(joined.groups, vec!["AAAA", "BBBB", "CCCC", "DDDD", "EEEE", "FFFF"]);
    assert_eq!(joined.groups, spaced.groups);
    assert_eq!(joined.events.iter().map(|e| (e.at, e.kind)).collect::<Vec<_>>(),
        spaced.events.iter().map(|e| (e.at, e.kind)).collect::<Vec<_>>());
    let groups: Vec<String> = (0..200).map(|i| format!("{i:04}")).collect();
    let mut input = groups.clone();
    input[12] = "001".into();
    input[13] = "0013/0012".into();
    input[99].push('。');
    let timeline = console::build_timeline(&json!({"key":"hand", "alphabet":"short",
        "text":input.join(" "), "tail":"none"})).unwrap();
    assert_eq!(timeline.groups, groups);
    let turn = timeline.chars.iter().position(|c| c.value == "翻页").unwrap();
    assert_eq!(timeline.chars[..turn].iter().filter(|c| !c.control).map(|c| c.group).max(), Some(Some(99)));
    let next = timeline.chars[turn + 3..].iter().find(|c| !c.control).unwrap();
    assert_eq!(next.group, Some(100));
}

#[test]
fn manual_previous_correction_matches_occupied_and_empty_cursor_rules() {
    for (text, expected) in [("ABCD EF/GHIJ KLMN", vec!["GHIJ", "EFKLMN"]),
        ("ABCD EFGH / GHIJ KLMN", vec!["GHIJ", "EFGH", "KLMN"])] {
        let timeline = script(text);
        assert_eq!(timeline.groups, expected);
        let correction = timeline.chars.iter().find(|item| item.value == "改错前一组").unwrap();
        assert_eq!(correction.group, Some(0));
        assert_eq!(correction.codes.iter().map(u8::to_string).collect::<String>(), morse::CONTROL_NEXT);
    }
    let first = script("ABCD / EFGH");
    assert_eq!(first.groups, vec!["EFGH"], "首组回退与客户端一样钳制到本页第0组");
}

#[test]
fn automatic_modes_replay_the_correct_groups() {
    for (current, previous) in [(true, false), (false, true), (true, true)] {
        let timeline = console::build_timeline(&json!({"key":"hand", "alphabet":"letter",
            "text":"ABCD EFGH", "tail":"end", "correctCurrent":current, "correctPrevious":previous})).unwrap();
        assert_eq!(timeline.groups, vec!["ABCD", "EFGH"]);
        let mut counts = (0, 0);
        for (index, item) in timeline.chars.iter().enumerate() {
            if item.value == "改错当前组" {
                counts.0 += 1;
                let target = item.group.unwrap();
                let replay = &timeline.chars[index + 1..index + 5];
                assert!(replay.iter().all(|item| !item.control && item.group == Some(target)));
                assert_eq!(replay.iter().map(|item| item.value.as_str()).collect::<String>(), timeline.groups[target]);
            } else if item.value == "改错前一组" {
                counts.1 += 1;
                assert_eq!(item.group, Some(0));
                let replay = &timeline.chars[index + 1..index + 5];
                assert_eq!(replay.iter().map(|item| item.value.as_str()).collect::<String>(), "ABCD");
            }
        }
        assert_eq!(counts.0 > 0, current);
        assert_eq!(counts.1 > 0, previous);
    }
}

#[test]
fn corrections_do_not_count_as_new_groups_or_cross_a_committed_page() {
    let original: Vec<String> = (0..100).map(|index| format!("{index:04}")).collect();
    for ending in ["? 9999 。0100", "/ 9998 。0100", "。0100 / 7777"] {
        let timeline = console::build_timeline(&json!({"key":"hand", "alphabet":"short",
            "text":format!("{} {ending}", original.join(" ")), "tail":"end"})).unwrap();
        assert_eq!(timeline.groups.len(), 101);
        let turns: Vec<_> = timeline.chars.iter().enumerate().filter(|(_, item)| item.value == "翻页").collect();
        assert_eq!(turns.len(), 3);
        let correction = timeline.chars.iter().enumerate().find(|(_, item)| item.value.starts_with("改错")).unwrap();
        if ending.starts_with("。0100") {
            assert_eq!(correction.1.group, Some(100));
            assert!(correction.0 > turns[2].0);
        } else {
            assert!(correction.0 < turns[0].0, "第100组之后的改错应在分页前完成");
        }
    }
    let multi = console::build_timeline(&json!({"key":"hand", "alphabet":"short",
        "text":format!("{} 。{} 。1234",vec!["1234";100].join(" "),vec!["1234";100].join(" ")), "tail":"end", "correctCurrent":true, "correctPrevious":true})).unwrap();
    assert_eq!(multi.groups.len(), 201);
    assert_eq!(multi.chars.iter().filter(|item| item.value == "翻页").count(), 6);
    assert!(multi.body_chars().count() > 804);
}

#[test]
fn correction_payload_keeps_the_same_history_markers_as_the_client() {
    let timeline = script("AB?ABCD EFGH");
    let payload = sinks::hand_upload_payload(&timeline, 1, 1,
        &sinks::Capture { attempt: 1, server_elapsed_ms: 0.0 }, 1.0, 51);
    let keys: Value = serde_json::from_str(payload["messageBody"][0]["patKeys"].as_str().unwrap()).unwrap();
    assert_eq!(keys, json!(["A", "B", "?", "A", "B", "C", "D"]));
    let codes: Value = serde_json::from_str(payload["messageBody"][0]["moresValue"].as_str().unwrap()).unwrap();
    assert_eq!(codes[2], json!([0, 0, 1, 1, 0, 0]));
}

#[test]
fn automatic_corrections_are_reproducible_and_keep_the_measured_rate() {
    for style in ["machine", "human", "fatigue"] {
        let options = keying::HandOptions {text: vec!["ABCD"; 101].join(" "), correct_current: true,
            correct_previous: true, style: style.into(), tail: "end".into(), ..Default::default()};
        let first = keying::hand_timeline(&options).unwrap();
        let again = keying::hand_timeline(&options).unwrap();
        assert_eq!(first.events.iter().map(|event| (event.at, event.kind)).collect::<Vec<_>>(),
            again.events.iter().map(|event| (event.at, event.kind)).collect::<Vec<_>>());
        assert!((keying::measured_rate(&first, 1.0) / options.rate - 1.0).abs() < 0.005);
    }
}

#[test]
fn invalid_manual_scripts_are_rejected_and_electronic_keys_keep_their_alphabet() {
    for text in ["? ABCD", "/ ABCD", "ABCD ?", "ABCD /", "?"] {
        assert!(console::build_timeline(&json!({"key":"hand", "text":text})).is_err(), "{text}");
    }
    assert!(console::build_timeline(&json!({"key":"electron", "text":"ABCD ? EFGH"})).is_err());
    let short = console::build_timeline(&json!({"key":"hand", "text":"ABCD", "correctPrevious":true})).unwrap();
    assert!(!short.chars.iter().any(|item| item.value == "改错前一组"));
}
