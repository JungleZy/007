mod align;
mod delete;
mod dto;
mod handlers;
mod parse;
mod post_telegraph;
mod settle;

use self::post_telegraph::*;

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
        .route("/listPage", post(list))
        .route("/details", post(detail))
        .route("/add", post(crate::domain::post_telegraph::handlers::add))
        .route(
            "/begin",
            post(crate::domain::post_telegraph::handlers::begin),
        )
        .route(
            "/finish",
            post(crate::domain::post_telegraph::handlers::finish),
        )
        .route("/getPage", get(get_page))
        .route(
            "/finishPage",
            post(crate::domain::post_telegraph::handlers::finish_page),
        )
        .route(
            "/reset",
            post(crate::domain::post_telegraph::handlers::reset),
        )
        .route(
            "/delete",
            get(crate::domain::post_telegraph::delete::delete),
        )
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router.nest("/api/PostTelegraphKeyPatTrain", routes);
    router
}

#[cfg(test)]
mod tests;
