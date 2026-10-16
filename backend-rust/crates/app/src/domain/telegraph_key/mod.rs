pub(crate) mod synthetical;
pub(crate) mod telegraph_key;

use self::telegraph_key::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::domain::telegraph_key::synthetical;
    use crate::platform::auth::authenticate;
    use axum::{middleware, routing::post, Router};

    let mut router = Router::new();
    let key_routes = Router::new()
        .route("/findByType", post(find_by_type))
        .route("/save", post(save))
        .route("/clear", post(clear))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let synthetical_routes = Router::new()
        .route("/findAll", post(synthetical::find_all))
        .route("/save", post(synthetical::save))
        .route("/begin", post(synthetical::begin))
        .route("/stop", post(synthetical::stop))
        .route("/goTo", post(synthetical::go_to))
        .route("/finish", post(synthetical::finish))
        .route("/findById", post(synthetical::find_by_id))
        .route("/lastTrain", post(synthetical::last_train))
        .route("/statisticalPage", post(synthetical::statistical_page))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router
        .nest("/api/telegraphKeyPatTrain", key_routes)
        .nest("/api/telegraphKeyPatTrainSynthetical", synthetical_routes);
    router
}

#[cfg(test)]
mod tests;
