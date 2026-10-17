mod post_telex;
mod post_telex_write;

use self::post_telex::*;

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
        .route("/getPage", get(get_page))
        .route("/finishPage", post(finish_page))
        .route(
            "/save",
            post(crate::domain::post_telex::post_telex_write::add),
        )
        .route(
            "/begin",
            post(crate::domain::post_telex::post_telex_write::begin),
        )
        .route(
            "/pause",
            post(crate::domain::post_telex::post_telex_write::pause),
        )
        .route(
            "/resume",
            post(crate::domain::post_telex::post_telex_write::resume),
        )
        .route(
            "/reset",
            post(crate::domain::post_telex::post_telex_write::reset),
        )
        .route(
            "/finish",
            post(crate::domain::post_telex::post_telex_write::finish),
        )
        .route(
            "/delete",
            get(crate::domain::post_telex::post_telex_write::delete),
        )
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router.nest("/api/postTelexPatTrain", routes);
    router
}

#[cfg(test)]
mod tests;
