use keysim::{keying::{self, electron_timeline, ElectronOptions}, morse, timeline::Kind};

#[test]
fn each_electron_page_restarts_after_its_preceding_period() {
    for count in [99, 100, 101, 200, 201] {
        for (alphabet, group) in [("short", "1234"), ("letter", "ABCD")] {
            let timeline = electron_timeline(&ElectronOptions {
                text: vec![group; count].join(" "), alphabet: alphabet.into(),
                ..Default::default()
            }).unwrap();
            let starts: Vec<_> = timeline.chars.iter().filter(|item| item.value == "开始").collect();
            assert_eq!(starts.len(), (count + 99) / 100);
            assert_eq!(timeline.events.iter().filter(|event| event.kind == Kind::Code(morse::FN_PERIOD)).count(), starts.len());
            assert_eq!(timeline.body_chars().count(), count * 4);
            for (page, start) in starts.iter().enumerate().skip(1) {
                let previous = timeline.events.iter().rev().find(|event| event.at < start.started_at).unwrap();
                assert_eq!(previous.kind, Kind::Code(morse::FN_PERIOD));
                assert!(start.started_at - previous.at >= keying::PAUSE_DURATION);
                let next = timeline.body_chars().find(|item| item.started_at >= start.ended_at).unwrap();
                assert_eq!(next.group, Some(page * 100));
                assert!(start.control);
            }
            assert!(timeline.events.windows(2).all(|pair| pair[0].at <= pair[1].at));
        }
    }
}

#[test]
fn electron_page_restart_respects_preamble_and_final_tail() {
    for preamble in [false, true] {
        for tail in ["page", "end", "none"] {
            let timeline = electron_timeline(&ElectronOptions {
                text: vec!["1234"; 101].join(" "), alphabet: "short".into(),
                preamble, tail: tail.into(), ..Default::default()
            }).unwrap();
            let count = |code| timeline.events.iter().filter(|event| event.kind == Kind::Code(code)).count();
            assert_eq!(count(morse::FN_START), if preamble { 2 } else { 0 });
            assert_eq!(count(morse::FN_PERIOD), 1 + usize::from(tail == "page"));
            assert_eq!(count(morse::FN_F3), usize::from(tail == "end"));
            assert!((keying::measured_rate(&timeline, 4.0) / 30.0 - 1.0).abs() < 0.005);
        }
    }
}
