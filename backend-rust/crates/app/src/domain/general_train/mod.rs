mod capture;
mod common;
mod export;
mod general_aux;
mod generate;
mod key;
mod lifecycle;
pub(crate) mod recovery;
mod telex;
mod ticker;
pub(crate) mod train_rule;

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
        .route("/add", post(key::key_add))
        .route("/findAll", post(key::key_list))
        .route("/findPage", post(capture::key_page))
        .route("/getPage", post(capture::key_get_page))
        .route("/detail", post(key::key_detail))
        .route("/patDetail", post(capture::key_members))
        .route("/getPatValue", post(capture::key_pat_value))
        .route("/getTrainInfo", get(export::key_export))
        .route("/getTrainInfoBatch", get(export::key_export_batch))
        .route("/importTrainInfo", post(export::key_import))
        .route("/importTrainInfoBatch", post(export::key_import_batch))
        .route("/uploadResult", post(capture::key_upload))
        .route("/updateTrainStatus", post(key::key_update))
        .route("/finish", post(key::key_finish))
        .route("/statistics", post(key::key_statistics))
        .route("/getOnline", get(capture::key_online))
        .route("/startTrain", get(key::key_start))
        .route("/reset", post(key::key_reset))
        .route("/delete", get(key::key_delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let telex = Router::new()
        .route("/add", post(telex::telex_add))
        .route("/findAll", post(telex::telex_list))
        .route("/findPage", post(capture::telex_page))
        .route("/getPage", post(capture::telex_get_page))
        .route("/detail", post(telex::telex_detail))
        .route("/patDetail", post(capture::telex_members))
        .route("/getPatValue", post(capture::telex_pat_value))
        .route("/uploadResult", post(capture::telex_upload))
        .route("/updateTrainStatus", post(telex::telex_update))
        .route("/finish", post(telex::telex_finish))
        .route("/statistics", post(telex::telex_statistics))
        .route("/getOnline", get(capture::telex_online))
        .route("/startTrain", get(telex::telex_start))
        .route("/delete", get(telex::telex_delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let ticker = Router::new()
        .route("/add", post(ticker::ticker_add))
        .route("/findAll", post(ticker::ticker_list))
        .route("/findPage", post(capture::ticker_page))
        .route("/getPage", post(capture::ticker_page))
        .route("/detail", post(ticker::ticker_detail))
        .route("/uploadResult", post(capture::ticker_upload))
        .route("/updateTrainStatus", post(ticker::ticker_update))
        .route("/finish", post(ticker::ticker_finish))
        .route("/reset", post(ticker::ticker_reset))
        .route("/statistics", post(ticker::ticker_statistics))
        .route("/startTrain", get(ticker::ticker_start))
        .route("/delete", get(ticker::ticker_delete))
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
