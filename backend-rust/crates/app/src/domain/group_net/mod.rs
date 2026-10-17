mod general_group_net_rule;
mod group_net;
mod group_net_write;

use self::group_net::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::domain::group_net::general_group_net_rule;
    use crate::domain::group_net::group_net_write;
    use crate::platform::auth::authenticate;
    use crate::platform::auth::require_admin;
    use axum::{
        middleware,
        routing::{get, post},
        Router,
    };

    let mut router = Router::new();
    let train = Router::new()
        .route("/listPage", post(list))
        .route("/details", get(detail))
        .route("/saveTrain", post(group_net_write::save))
        .route("/submitAnswer", post(group_net_write::submit))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let rule_read = Router::new()
        .route("/findAll", post(general_group_net_rule::find_all))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let rule_admin = Router::new()
        .route("/save", post(general_group_net_rule::save))
        .route("/deleteById", post(general_group_net_rule::delete_by_id))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router
        .nest("/api/groupNetTrain", train)
        .nest("/api/generalGroupNetRule", rule_read.merge(rule_admin));
    router
}

#[cfg(test)]
mod tests;
