pub(crate) mod entering;
mod entering_codebook;
mod post_entering;

use self::entering::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::domain::entering::post_entering;
    use crate::platform::auth::authenticate;
    use crate::platform::auth::require_admin;
    use axum::{
        extract::DefaultBodyLimit,
        middleware,
        routing::{get, post},
        Router,
    };

    let mut router = Router::new();
    let entering_routes = Router::new()
        .route("/listPage", post(list))
        .route("/add", post(add))
        .route("/getById", post(by_id))
        .route("/getWordStock", post(get_word_stock))
        .route("/lastTrain", post(last_train))
        .route("/begin", post(begin))
        .route("/goTo", post(go_to))
        .route("/pause", post(pause))
        .route("/finish", post(finish))
        .route("/statisticalPage", post(statistics))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let telex_routes = Router::new()
        .route("/findByUserIdAndType", post(telex_find))
        .route("/save", post(telex_save))
        .route("/clear", post(telex_clear))
        .route("/statisticalPage", post(telex_statistics))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let keypoint_routes = Router::new()
        .route("/getByType", post(keypoints))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_keypoint_routes = Router::new()
        .route("/save", post(save_key_points))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let post_routes = Router::new()
        .route("/add", post(post_entering::add))
        .route("/listPage", post(post_entering::list_page))
        .route("/begin", post(post_entering::begin))
        .route("/finish", post(post_entering::finish))
        .route("/getWordStock", post(post_entering::get_word_stock))
        .route("/getById", post(post_entering::get_by_id))
        .route("/delete", get(post_entering::delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let stock_read = Router::new()
        .route(
            "/view",
            post(post_entering::word_stock_view)
                .layer(DefaultBodyLimit::max(state.config.body_limit)),
        )
        .route("/listPage", post(post_entering::word_stock_list_page))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let stock_admin = Router::new()
        .route("/add", post(post_entering::word_stock_add))
        .route("/delete", post(post_entering::word_stock_delete))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router
        .nest("/api/enteringExercise", entering_routes)
        .nest("/api/enteringTelexPat", telex_routes)
        .nest(
            "/api/enteringKeyPoints",
            keypoint_routes.merge(admin_keypoint_routes),
        )
        .nest("/api/postEnteringExercise", post_routes)
        .nest(
            "/api/postEnteringExerciseWordStock",
            stock_read.merge(stock_admin),
        );
    router
}

#[cfg(test)]
mod tests;
