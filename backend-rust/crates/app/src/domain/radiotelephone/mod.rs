mod military;
mod military_term_data;
mod post_military;
mod post_radiotelephone;
mod radio_term;
mod radiotelephone;

use self::radiotelephone::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::domain::general_train::train_rule;
    use crate::domain::radiotelephone::military;
    use crate::domain::radiotelephone::military_term_data;
    use crate::domain::radiotelephone::post_military;
    use crate::domain::radiotelephone::post_radiotelephone;
    use crate::domain::radiotelephone::radio_term;
    use crate::platform::auth::authenticate;
    use crate::platform::auth::require_admin;
    use axum::{
        middleware,
        routing::{get, post},
        Router,
    };

    let mut router = Router::new();
    let radio = Router::new()
        .route("/listPage", post(list))
        .route("/begin", post(begin))
        .route("/pause", post(pause))
        .route("/resume", post(resume))
        .route("/finish", post(finish))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let military_routes = Router::new()
        .route("/findAll", post(military::list))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let term_read = Router::new()
        .route("/findAll", post(military_term_data::find_all))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let term_admin = Router::new()
        .route("/saveAll", post(military_term_data::save_all))
        .route("/save", post(military_term_data::save))
        .route("/delete", post(military_term_data::delete))
        .route("/update", post(military_term_data::update))
        .route("/move", post(military_term_data::move_term))
        .route("/saveBatch", post(military_term_data::save_batch))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let radio_term_read = Router::new()
        .route("/findByType", post(radio_term::find_by_type))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let radio_term_admin = Router::new()
        .route("/save", post(train_rule::term_save))
        .route("/delete", post(train_rule::term_delete))
        .route("/update", post(train_rule::term_update))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let radio_train = Router::new()
        .route("/findByType", post(post_radiotelephone::find_by_type))
        .route(
            "/findByTypeAndRandom",
            post(post_radiotelephone::find_by_type_and_random),
        )
        .route("/add", post(post_radiotelephone::add))
        .route("/listPge", post(post_radiotelephone::list_page))
        .route("/begin", post(post_radiotelephone::begin))
        .route("/finish", post(post_radiotelephone::finish))
        .route("/details", post(post_radiotelephone::details))
        .route("/delete", get(post_radiotelephone::delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let military_train = Router::new()
        .route("/add", post(post_military::add))
        .route("/listPage", post(post_military::list_page))
        .route("/details", post(post_military::details))
        .route("/begin", post(post_military::begin))
        .route("/finish", post(post_military::finish))
        .route("/delete", get(post_military::delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router
        .nest("/api/radiotelephone", radio)
        .nest("/api/militaryTermData", military_routes)
        .nest("/api/mtd", term_read.merge(term_admin))
        .nest(
            "/api/postRadiotelephoneTermData",
            radio_term_read.merge(radio_term_admin),
        )
        .nest("/api/postRadiotelephoneTrain", radio_train)
        .nest("/api/postMilitaryTermTrain", military_train);
    router
}

#[cfg(test)]
mod tests;
