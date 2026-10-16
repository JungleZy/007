mod tools;

use self::tools::*;

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

#[cfg(test)]
mod tests;
