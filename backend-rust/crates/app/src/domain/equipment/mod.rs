mod equipment;
mod equipment_train;

use self::equipment::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::domain::equipment::equipment_train;
    use crate::platform::auth::authenticate;
    use axum::{middleware, routing::post, Router};

    let mut router = Router::new();
    let read = Router::new()
        .route("/listPage", post(list_devices))
        .route("/listKeyPoints", post(list_key_points))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin = Router::new()
        .route("/add", post(add))
        .route("/update", post(update))
        .route("/delete", post(delete))
        .route("/addKeyPoints", post(add_key_points))
        .route_layer(middleware::from_fn(crate::platform::auth::require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let train = Router::new()
        .route("/listPage", post(equipment_train::list))
        .route("/detail", post(equipment_train::detail))
        .route("/add", post(equipment_train::add))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router
        .nest("/api/equipmentDevice", read.merge(admin))
        .nest("/api/equipmentTrain", train);
    router
}
