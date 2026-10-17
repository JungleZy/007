pub(crate) mod ticker_tape;

use self::ticker_tape::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::platform::auth::authenticate;
    use crate::platform::auth::require_admin;
    use axum::{middleware, routing::post, Router};

    let mut router = Router::new();
    let train = Router::new()
        .route("/listPage", post(list))
        .route("/getById", post(by_id))
        .route("/add", post(add))
        .route("/begin", post(begin))
        .route("/pause", post(pause))
        .route("/goOn", post(resume))
        .route("/finish", post(finish))
        .route("/baseSession", post(base_session))
        .route("/saveBaseTrain", post(save_base))
        .route("/discardBaseSession", post(discard_base))
        .route("/statisticalPage", post(statistics))
        .route("/lastTrain", post(last_train))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let setting = Router::new()
        .route("/findAll", post(settings))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_setting = Router::new()
        .route("/addOrUpdate", post(setting_add_or_update))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let stage_setting = Router::new()
        .route("/findAll", post(stage_settings))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_stage_setting = Router::new()
        .route("/add", post(stage_setting_add))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router
        .nest("/api/tickerTapeTrain", train)
        .nest("/api/tickerTapeTrainSetting", setting.merge(admin_setting))
        .nest(
            "/api/tickerTapeTrainStageSetting",
            stage_setting.merge(admin_stage_setting),
        );
    router
}

#[cfg(test)]
mod tests;
