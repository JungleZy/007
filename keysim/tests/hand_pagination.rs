use keysim::keying::{self, HandOptions};

#[test]
fn no_automatic_turns_at_hundred_groups_or_end_even_with_old_tail_option() {
    for count in [99, 100, 101, 200, 201] {
        for tail in ["none", "turn", "end"] {
            let timeline = keying::hand_timeline(&HandOptions {
                text: vec!["1234"; count].join(" "), alphabet: "short".into(), tail: tail.into(), ..Default::default()
            }).unwrap();
            assert_eq!(timeline.chars.iter().filter(|c| c.value == "翻页").count(), 0);
            assert_eq!(timeline.body_chars().count(), count * 4);
            assert_eq!(timeline.chars.iter().filter(|c| c.value == "结束").count(), usize::from(tail == "end"));
        }
    }
}

#[test]
fn typed_period_alone_controls_page_boundary_and_starts_next_page() {
    for count in [90, 99, 100, 101, 110] {
        for separator in ["。", "."] {
            let timeline = keying::hand_timeline(&HandOptions {
                text: format!("{} 6023 {separator}7025 1234", vec!["4567"; count - 1].join(" ")),
                alphabet: "short".into(), preamble: false, tail: "none".into(), ..Default::default()
            }).unwrap();
            let turns: Vec<_> = timeline.chars.iter().enumerate().filter(|(_, c)| c.value == "翻页").collect();
            assert_eq!(turns.len(), 3);
            assert_eq!(timeline.chars[..turns[0].0].iter().filter(|c| !c.control).count(), count * 4);
            assert_eq!(timeline.chars[turns[2].0 + 1].value, "开始");
            let next: String = timeline.chars[turns[2].0 + 1..].iter().filter(|c| !c.control).take(4).map(|c| c.value.as_str()).collect();
            assert_eq!(next, "7025");
        }
    }
}

#[test]
fn final_period_has_no_extra_start_or_duplicate_turn() {
    let timeline = keying::hand_timeline(&HandOptions {
        text: "1111 2222 。3333 4444/5555 6666 。".into(), alphabet: "short".into(),
        tail: "turn".into(), ..Default::default()
    }).unwrap();
    assert_eq!(timeline.groups, vec!["1111", "2222", "5555", "4444", "6666"]);
    assert_eq!(timeline.chars.iter().filter(|c| c.value == "翻页").count(), 6);
    assert_eq!(timeline.chars.iter().filter(|c| c.value == "开始").count(), 2);
    assert_eq!(timeline.chars.last().unwrap().value, "翻页");
}

#[test]
fn explicit_pages_keep_rate_and_corrections_for_all_styles() {
    for style in ["machine", "human", "fatigue"] {
        let timeline = keying::hand_timeline(&HandOptions {
            text: format!("{} 。{}", vec!["1234"; 99].join(" "), vec!["5678"; 101].join(" ")),
            alphabet: "short".into(), style: style.into(), correct_current: true, correct_previous: true,
            tail: "none".into(), ..Default::default()
        }).unwrap();
        assert_eq!(timeline.chars.iter().filter(|c| c.value == "翻页").count(), 3);
        assert!((keying::measured_rate(&timeline, 1.0) / 120.0 - 1.0).abs() < 0.005);
    }
}
