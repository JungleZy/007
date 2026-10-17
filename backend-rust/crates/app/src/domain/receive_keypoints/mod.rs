mod keypoints;
mod receive_keypoints;

use self::receive_keypoints::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::domain::receive_keypoints::keypoints;
    use crate::platform::auth::authenticate;
    use crate::platform::auth::require_admin;
    use axum::{middleware, routing::post, Router};

    let mut router = Router::new();
    let read = Router::new()
        .route("/getByType", post(get_by_type))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin = Router::new()
        .route("/save", post(save))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let key_read = Router::new()
        .route("/findKeyPointsByType", post(keypoints::key))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let key_admin = Router::new()
        .route("/saveKeyPoints", post(keypoints::save_key_points))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router
        .nest("/api/receiveKeyPoints", read.merge(admin))
        .nest("/api/keyPoints", key_read.merge(key_admin));
    router
}
