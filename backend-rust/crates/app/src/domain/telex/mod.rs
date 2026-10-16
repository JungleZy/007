pub(crate) mod telex;
pub(crate) mod telex_handle;

use self::telex::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::platform::auth::authenticate;
    use axum::{middleware, routing::post, Router};

    let mut router = Router::new();
    let routes = Router::new()
        .route("/saveTelexPat", post(save_base))
        .route("/findTelexPatById", post(find_base))
        .route("/deleteTexPatByToken", post(delete_base))
        .route("/statisticalPage", post(statistical_page))
        .route("/lastPatTrain", post(last_train))
        .route("/saveTexPatTrain", post(train_save))
        .route("/findTexPatTrainByToken", post(train_list))
        .route("/findTexPatTrainById", post(train_detail))
        .route("/deleteById", post(train_delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router.nest("/api/telexPat", routes);
    router
}

#[cfg(test)]
mod tests;
