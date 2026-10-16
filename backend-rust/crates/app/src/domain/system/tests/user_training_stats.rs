use crate::domain::system::user_training_stats::*;
use axum::Json;
use serde_json::json;

#[test]
fn minute_precision_iso_filter_is_not_silently_dropped() {
    let bounds = parse_bounds(Some(Json(json!({
        "startTime": "2026-09-01T03:00",
        "endTime": "2026-09-01 04:00:00"
    }))))
    .unwrap();
    assert_eq!(bounds.start.as_deref(), Some("2026-09-01 03:00:00.000000"));
    assert_eq!(bounds.end.as_deref(), Some("2026-09-01 04:00:00.000000"));
}
