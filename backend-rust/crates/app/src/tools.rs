use axum::{http::StatusCode, response::Response};
use serde_json::{json, Value};
use time::OffsetDateTime;

use crate::platform::{clock::now_time_millis, envelope::envelope_json};

pub(crate) fn twelve_month_data(year: i32, month: u8) -> Vec<Value> {
    let current_index = year * 12 + i32::from(month) - 1;
    (0..12)
        .map(|offset| {
            let index = current_index - offset;
            json!({
                "year": index.div_euclid(12),
                "month": index.rem_euclid(12) + 1,
            })
        })
        .collect()
}

pub(crate) async fn tools_get_now_time() -> Response {
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!(now_time_millis())),
        "ok",
        "",
    )
}

pub(crate) async fn tools_get_twelve_month() -> Response {
    let current = OffsetDateTime::now_utc();
    let data = twelve_month_data(current.year(), u8::from(current.month()));
    envelope_json(StatusCode::OK, 200, Some(json!(data)), "ok", "")
}

pub(crate) fn routes(
    _state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use axum::{routing::get, Router};

    let mut router = Router::new();
    router = router
        .route("/api/tools/getNowTime", get(tools_get_now_time))
        .route("/api/tools/getTwelvemonth", get(tools_get_twelve_month));
    router
}
