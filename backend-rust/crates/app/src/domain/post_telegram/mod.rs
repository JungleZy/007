mod post_telegram;
pub(crate) mod post_telegram_write;

use self::post_telegram::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::platform::auth::authenticate;
    use axum::{
        middleware,
        routing::{get, post},
        Router,
    };

    let mut router = Router::new();
    let routes = Router::new()
        .route("/findAll", post(list))
        .route("/detail", post(detail))
        .route(
            "/findMessageBody",
            post(crate::domain::post_telegram::post_telegram_write::content),
        )
        .route(
            "/printBottomReport",
            post(crate::domain::post_telegram::post_telegram_write::print_bottom_report),
        )
        .route(
            "/saveContentValue",
            post(crate::domain::post_telegram::post_telegram_write::save_content),
        )
        .route(
            "/addContentValue",
            post(crate::domain::post_telegram::post_telegram_write::add_content_value),
        )
        .route(
            "/save",
            post(crate::domain::post_telegram::post_telegram_write::add),
        )
        .route(
            "/begin",
            post(crate::domain::post_telegram::post_telegram_write::begin),
        )
        .route(
            "/finish",
            post(crate::domain::post_telegram::post_telegram_write::finish),
        )
        .route(
            "/stop",
            post(crate::domain::post_telegram::post_telegram_write::stop),
        )
        .route(
            "/delete",
            get(crate::domain::post_telegram::post_telegram_write::delete),
        )
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router.nest("/api/postTelegramTrain", routes);
    router
}

#[cfg(test)]
mod tests;
