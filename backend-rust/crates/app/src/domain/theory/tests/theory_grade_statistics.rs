use crate::domain::theory::theory::*;
use serde_json::json;

#[test]
fn grade_modes_use_paper_thresholds_and_sparse_calendar_groups() {
    let records = [
        ("2026-01-02 10:00:00", 69),
        ("2026-01-02 11:00:00", 70),
        ("2026-01-03 10:00:00", 85),
        ("2026-02-04 10:00:00", 80),
    ]
    .map(|(time, score)| (Some(time.to_owned()), Some(score)));
    let thresholds = [
        (Some(69), Some(70), Some(101)),
        (Some(70), Some(70), Some(101)),
        (Some(85), Some(70), Some(101)),
        (Some(80), Some(90), Some(100)),
    ];
    assert_eq!(
        grade_statistics(&records, &thresholds, 0, true).unwrap(),
        json!({"up":{"all":4,"good":2},"down":{"59":2,"60":1,"81":1}})
    );
    assert_eq!(
        grade_statistics(&records, &thresholds, 1, true).unwrap()["down"],
        json!({"1":3,"2":1})
    );
    assert_eq!(
        grade_statistics(&records[..3], &thresholds[..3], 1, false).unwrap()["down"],
        json!({"2":2,"3":1})
    );
    assert_eq!(
        grade_statistics(&records, &thresholds, 2, true).unwrap()["down"],
        json!({"1":{"high":"85","low":"69","avg":"74.67"},"2":{"high":"80","low":"80","avg":"80.00"}})
    );
    assert_eq!(
        grade_statistics(&records[..3], &thresholds[..3], 9, false).unwrap()["down"],
        json!({"2":{"high":"70","low":"69","avg":"69.50"},"3":{"high":"85","low":"85","avg":"85.00"}})
    );
    for kind in [0, 1, 2] {
        assert_eq!(
            grade_statistics(&[], &[], kind, true).unwrap(),
            json!({"up":{"all":0,"good":0},"down":{}})
        );
    }
    assert!(grade_statistics(&records[..1], &[(Some(69), None, None)], 0, true).is_err());
    assert_eq!(
        grade_statistics(&records[..1], &[(Some(69), None, None)], 1, true).unwrap()["up"]["good"],
        0
    );
}

#[test]
fn score_averages_keep_two_digits_and_half_even_ties() {
    for (sum, expected) in [(9, "1.12"), (11, "1.38"), (-9, "-1.12")] {
        let mut records = vec![(Some("2026-01-01 00:00:00".to_owned()), Some(0)); 8];
        records[0].1 = Some(sum);
        assert_eq!(
            grade_statistics(&records, &[], 2, true).unwrap()["down"]["1"]["avg"],
            expected
        );
    }
}
