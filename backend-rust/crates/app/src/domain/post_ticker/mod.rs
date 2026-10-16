pub(crate) mod masthead;
mod post_ticker;

use self::post_ticker::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::domain::post_ticker::masthead;
    use crate::platform::auth::authenticate;
    use crate::platform::auth::require_admin;
    use axum::{
        middleware,
        routing::{get, post},
        Router,
    };

    let mut router = Router::new();
    let routes = Router::new()
        .route("/listPage", post(list))
        .route("/getById", post(detail))
        .route("/add", post(add))
        .route("/begin", post(begin))
        .route("/finish", post(finish))
        .route("/reset", post(reset))
        .route("/upLoadResult", post(upload_result))
        .route("/findPage", get(find_page))
        .route("/delete", get(delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let setting_read = Router::new()
        .route("/findAll", post(setting_list))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let setting_admin = Router::new()
        .route("/addOrUpdate", post(setting_add_or_update))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let masthead_routes = Router::new()
        .route("/findByTrainId", get(masthead::find_by_train_id))
        .route("/save", post(masthead::save))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router
        .nest("/api/postTickerTapeTrain", routes)
        .nest(
            "/api/postTickerTapeTrainSetting",
            setting_read.merge(setting_admin),
        )
        .nest("/api/masthead", masthead_routes);
    router
}

#[cfg(test)]
mod tests;
