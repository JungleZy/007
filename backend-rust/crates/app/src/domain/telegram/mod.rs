mod telegram;
pub(crate) mod telegram_lifecycle;
mod telegram_write;

use self::telegram::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::domain::telegram::telegram_write;
    use crate::platform::auth::authenticate;
    use crate::platform::auth::require_admin;
    use axum::{middleware, routing::post, Router};

    let mut router = Router::new();
    let read = Router::new()
        .route("/getAll", post(list))
        .route("/getById", post(by_id))
        .route("/getFloorContentByFloorId", post(floor_content_by_floor_id))
        .route(
            "/getFloorContentByFloorIdAsync",
            post(floor_content_by_floor_id_async),
        )
        .route("/getFloorContentByFloor", post(floor_content_by_floor))
        .route("/getTelegramTrainLog", post(get_telegram_train_log))
        .route("/getSetting", post(get_setting))
        .route("/statisticalPage", post(statistical_page))
        .route("/lastTrain", post(last_train))
        .route("/saveTelegramTrain", post(telegram_write::save))
        .route(
            "/saveFloorContent",
            post(telegram_lifecycle::save_floor_content),
        )
        .route("/saveBaseTrain", post(telegram_write::save_base_train))
        .route("/startTelegramTrain", post(telegram_write::start))
        .route("/pauseTelegramTrain", post(telegram_write::pause))
        .route("/endTelegramTrain", post(telegram_write::end))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin = Router::new()
        .route("/saveSetting", post(telegram_write::save_setting))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router.nest("/api/telegramTrain", read.merge(admin));
    router
}

#[cfg(test)]
mod tests;
