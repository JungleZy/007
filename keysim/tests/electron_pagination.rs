use keysim::{keying::{self, electron_timeline, ElectronOptions}, morse, timeline::Kind};

#[test]
fn electronic_corrections_send_f1_keys_and_preserve_the_following_group() {
    for (text, alphabet) in [("1234 5678 / 9012 3456 ? 7890", "short"),
                             ("ABCD EFGH / IJKL MNOP ? QRST", "letter")] {
        let timeline = electron_timeline(&ElectronOptions {
            text: text.into(), alphabet: alphabet.into(), ..Default::default()
        }).unwrap();
        let corrections: Vec<_> = timeline.chars.iter().filter(|c| c.value.starts_with("改错")).collect();
        assert_eq!(corrections.len(), 2);
        assert_eq!(corrections[0].keys, vec![43]);
        assert_eq!(corrections[0].group, Some(0));
        assert_eq!(corrections[1].keys, vec![42]);
        assert_eq!(corrections[1].group, Some(2));
        assert!(corrections.iter().all(|c| c.control));
        for correction in corrections {
            let index = timeline.events.iter().position(|e| e.at == correction.started_at && e.kind == Kind::Code(correction.keys[0])).unwrap();
            let mode = timeline.events[..index].iter().rev().find(|e| matches!(e.kind, Kind::Code(morse::FN_F1 | morse::FN_F2))).unwrap();
            assert_eq!(mode.kind, Kind::Code(morse::FN_F1));
        }
    }
}

#[test]
fn electronic_partial_correction_does_not_insert_enter_before_control() {
    let timeline = electron_timeline(&ElectronOptions {
        text: "12?3456。7890".into(), alphabet: "short".into(), ..Default::default()
    }).unwrap();
    let correction = timeline.chars.iter().find(|c| c.value == "改错当前组").unwrap();
    assert!(!timeline.events.iter().any(|e| e.at < correction.started_at && e.kind == Kind::Code(morse::FN_ENTER)));
    assert_eq!(timeline.chars.iter().filter(|c| c.value == "句号").count(), 1);
    assert_eq!(timeline.chars.iter().filter(|c| c.value == "开始").count(), 2);
}

#[test]
fn typed_period_submits_page_and_restarts_with_f1_start_before_next_body() {
    for count in [99, 100, 101] {
        for (alphabet, group) in [("short", "1234"), ("letter", "ABCD")] {
            for separator in ["。", "."] {
                let timeline = electron_timeline(&ElectronOptions {
                    text: format!("{}{separator}{group}", vec![group; count].join(" ")),
                    alphabet: alphabet.into(), preamble: false, ..Default::default()
                }).unwrap();
                let period = timeline.events.iter().position(|e| e.kind == Kind::Code(morse::FN_PERIOD)).unwrap();
                assert_eq!(timeline.events[period + 1].kind, Kind::Code(morse::FN_F1));
                assert_eq!(timeline.events[period + 2].kind, Kind::Code(morse::FN_START));
                assert!(timeline.events[period + 1].at - timeline.events[period].at >= keying::PAUSE_DURATION);
                let starts: Vec<_> = timeline.chars.iter().filter(|c| c.value == "开始").collect();
                assert_eq!(starts.len(), 1);
                let next = timeline.body_chars().find(|c| c.started_at >= starts[0].ended_at).unwrap();
                assert_eq!(next.group, Some(count));
                assert_eq!(timeline.chars.iter().filter(|c| c.value == "句号").count(), 1);
            }
        }
    }
}

#[test]
fn no_period_is_added_for_group_count_or_old_page_tail_option() {
    for count in [100, 101, 200] {
        let timeline = electron_timeline(&ElectronOptions {
            text: vec!["1234"; count].join(" "), alphabet: "short".into(), tail: "page".into(), ..Default::default()
        }).unwrap();
        assert!(!timeline.events.iter().any(|e| e.kind == Kind::Code(morse::FN_PERIOD)));
        assert_eq!(timeline.chars.iter().filter(|c| c.value == "开始").count(), 1);
    }
}

#[test]
fn final_period_has_no_extra_start_and_rate_remains_calibrated() {
    let timeline = electron_timeline(&ElectronOptions {
        text: "1234。5678.".into(), alphabet: "short".into(), ..Default::default()
    }).unwrap();
    assert_eq!(timeline.chars.iter().filter(|c| c.value == "句号").count(), 2);
    assert_eq!(timeline.chars.iter().filter(|c| c.value == "开始").count(), 2);
    assert_eq!(timeline.chars.last().unwrap().value, "句号");
    assert!((keying::measured_rate(&timeline, 4.0) / 30.0 - 1.0).abs() < 0.005);
    for text in ["。1234", "1234。。5678"] {
        assert!(electron_timeline(&ElectronOptions {text: text.into(), alphabet: "short".into(), ..Default::default()}).is_err());
    }
}
