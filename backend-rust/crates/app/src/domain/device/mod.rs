mod device;

use self::device::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::platform::auth::authenticate;
    use crate::platform::auth::require_admin;
    use axum::{
        middleware,
        routing::{get, post},
        Router,
    };

    let mut router = Router::new();
    let read = Router::new()
        .route("/listPage", post(list_devices))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin = Router::new()
        .route("/save", post(save_device))
        .route("/delete", post(delete_device))
        .route("/addDeviceDescription", post(add_description))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let type_read = Router::new()
        .route("/findAll", post(list_types))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let type_admin = Router::new()
        .route("/save", post(save_type))
        .route("/delete", post(delete_type))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let scoring_read = Router::new()
        .route("/findAllByDeviceId", get(find_scoring_rule))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let scoring_admin = Router::new()
        .route("/save", post(save_scoring_rule))
        .route("/delete", get(delete_scoring_rule))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router
        .nest("/api/device", read.merge(admin))
        .nest("/api/deviceType", type_read.merge(type_admin))
        .nest("/api/deviceScoringRule", scoring_read.merge(scoring_admin));
    router
}
