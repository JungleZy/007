mod general_aux;
pub(crate) mod general_train;
pub(crate) mod train_rule;

use self::general_train::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::domain::general_train::general_aux;
    use crate::platform::auth::authenticate;
    use axum::{
        middleware,
        routing::{get, post},
        Router,
    };

    let mut router = Router::new();
    let key = Router::new()
        .route("/add", post(key_add))
        .route("/findAll", post(key_list))
        .route("/findPage", post(key_page))
        .route("/getPage", post(key_get_page))
        .route("/detail", post(key_detail))
        .route("/patDetail", post(key_members))
        .route("/getPatValue", post(key_pat_value))
        .route("/getTrainInfo", get(key_export))
        .route("/getTrainInfoBatch", get(key_export_batch))
        .route("/importTrainInfo", post(key_import))
        .route("/importTrainInfoBatch", post(key_import_batch))
        .route("/uploadResult", post(key_upload))
        .route("/updateTrainStatus", post(key_update))
        .route("/finish", post(key_finish))
        .route("/statistics", post(key_statistics))
        .route("/getOnline", get(key_online))
        .route("/startTrain", get(key_start))
        .route("/reset", post(key_reset))
        .route("/delete", get(key_delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let telex = Router::new()
        .route("/add", post(telex_add))
        .route("/findAll", post(telex_list))
        .route("/findPage", post(telex_page))
        .route("/getPage", post(telex_get_page))
        .route("/detail", post(telex_detail))
        .route("/patDetail", post(telex_members))
        .route("/getPatValue", post(telex_pat_value))
        .route("/uploadResult", post(telex_upload))
        .route("/updateTrainStatus", post(telex_update))
        .route("/finish", post(telex_finish))
        .route("/statistics", post(telex_statistics))
        .route("/getOnline", get(telex_online))
        .route("/startTrain", get(telex_start))
        .route("/delete", get(telex_delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let ticker = Router::new()
        .route("/add", post(ticker_add))
        .route("/findAll", post(ticker_list))
        .route("/findPage", post(ticker_page))
        .route("/getPage", post(ticker_page))
        .route("/detail", post(ticker_detail))
        .route("/uploadResult", post(ticker_upload))
        .route("/updateTrainStatus", post(ticker_update))
        .route("/finish", post(ticker_finish))
        .route("/reset", post(ticker_reset))
        .route("/statistics", post(ticker_statistics))
        .route("/startTrain", get(ticker_start))
        .route("/delete", get(ticker_delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let key_online = Router::new()
        .route("/getOneline/{trainId}", get(general_aux::key_online))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let ticker_open = Router::new()
        .route("/综合训练-手键拍发Api", get(general_aux::ticker_online))
        .route("/findTrainInfo", get(general_aux::ticker_train_info))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let ticker_socket = Router::new()
        .route("/getByTrainIdAndUserId", get(general_aux::ticker_user))
        .route(
            "/updateTrainStatus",
            post(general_aux::ticker_update_status),
        )
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router
        .nest("/api/generalKeyPat", key)
        .nest("/api/generalTelexPat", telex)
        .nest("/api/generalTickerPatTrain", ticker)
        .nest("/api/GeneralKeyPatTrain", key_online)
        .nest("/api/openApi/generalTickerPatTrain", ticker_open)
        .nest("/api/socket/generalTickerPatTrain", ticker_socket);
    router
}

#[cfg(test)]
mod tests;
