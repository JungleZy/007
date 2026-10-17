pub(crate) mod cable;

use self::cable::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::platform::auth::authenticate;
    use crate::platform::auth::require_admin;
    use axum::{middleware, routing::post, Router};

    let mut router = Router::new();
    let read = Router::new()
        .route("/find", post(find))
        .route("/findById", post(find_by_id))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin = Router::new()
        .route("/save", post(save))
        .route("/delete", post(delete))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let type_read = Router::new()
        .route("/find", post(find_types))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let type_admin = Router::new()
        .route("/save", post(save_type))
        .route("/delete", post(delete_type))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let floor = Router::new()
        .route("/find", post(find_floor))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router
        .nest("/api/cable", read.merge(admin))
        .nest("/api/cable/type", type_read.merge(type_admin))
        .nest("/api/cable/floor", floor);
    router
}
