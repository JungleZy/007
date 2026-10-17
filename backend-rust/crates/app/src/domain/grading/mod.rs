mod grading;

use self::grading::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::platform::auth::authenticate;
    use crate::platform::auth::require_admin;
    use axum::{middleware, routing::post, Router};

    let mut router = Router::new();
    let read = Router::new()
        .route("/getGradingRuleListByType", post(list_by_type))
        .route("/getGradingRuleById", post(by_id))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin = Router::new()
        .route("/saveGradingRule", post(save))
        .route("/updateGradingRuleStatus", post(update_status))
        .route("/changeGradingRuleIsDefault", post(change_default))
        .route("/deleteGradingRule", post(delete))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router.nest("/api/gradingRule", read.merge(admin));
    router
}

#[cfg(test)]
mod tests;
