mod dto;
mod handlers;
mod post_telegram;
mod report;
pub(crate) mod rules;
mod settle;

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
            post(crate::domain::post_telegram::handlers::content),
        )
        .route(
            "/printBottomReport",
            post(crate::domain::post_telegram::report::print_bottom_report),
        )
        .route(
            "/saveContentValue",
            post(crate::domain::post_telegram::handlers::save_content),
        )
        .route(
            "/addContentValue",
            post(crate::domain::post_telegram::report::add_content_value),
        )
        .route("/save", post(crate::domain::post_telegram::handlers::add))
        .route(
            "/begin",
            post(crate::domain::post_telegram::handlers::begin),
        )
        .route(
            "/finish",
            post(crate::domain::post_telegram::handlers::finish),
        )
        .route("/stop", post(crate::domain::post_telegram::handlers::stop))
        .route(
            "/delete",
            get(crate::domain::post_telegram::handlers::delete),
        )
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router.nest("/api/postTelegramTrain", routes);
    router
}

#[cfg(test)]
mod tests;
