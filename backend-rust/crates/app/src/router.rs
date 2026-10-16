use axum::{http::HeaderValue, middleware, routing::get, Router};
use tower_http::{catch_panic::CatchPanicLayer, cors::CorsLayer, limit::RequestBodyLimitLayer};

use crate::{
    cable, device, entering, equipment, general_train, grading, group_net,
    platform::{
        auth::{authenticate, require_admin},
        envelope::openapi,
        health::{admin_probe, auth_probe, health, live, ready},
        http::{not_found, panic_response, request_id},
        state::AppState,
    },
    post_telegram, post_telegraph, post_telex, post_ticker, radiotelephone, receive_keypoints,
    simulation, system, telegram, telegraph_key, telex, theory, ticker_tape, tools, train_rule, ws,
};

pub(crate) fn build_router(state: AppState) -> Router {
    let protected = Router::new()
        .route("/auth", get(auth_probe))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin = Router::new()
        .route("/admin", get(admin_probe))
        .route_layer(middleware::from_fn_with_state(state.clone(), require_admin));
    let mut api = Router::new()
        .nest("/api/phase1/contract", protected.merge(admin))
        .route("/q/openapi", get(openapi));
    if state.config.tools_readonly_enabled {
        api = api.merge(tools::routes(&state));
    }
    if state.config.theory_readonly_enabled {
        api = api.merge(theory::routes(&state));
    }
    if state.config.cable_readonly_enabled {
        api = api.merge(cable::routes(&state));
    }
    if state.config.grading_readonly_enabled {
        api = api.merge(grading::routes(&state));
    }
    if state.config.device_readonly_enabled {
        api = api.merge(device::routes(&state));
    }
    if state.config.equipment_readonly_enabled {
        api = api.merge(equipment::routes(&state));
    }
    if state.config.entering_readonly_enabled {
        api = api.merge(entering::routes(&state));
    }
    if state.config.radiotelephone_readonly_enabled {
        api = api.merge(radiotelephone::routes(&state));
    }
    if state.config.telegram_readonly_enabled {
        api = api.merge(telegram::routes(&state));
    }
    if state.config.post_telegram_readonly_enabled {
        api = api.merge(post_telegram::routes(&state));
    }
    if state.config.post_telex_readonly_enabled {
        api = api.merge(post_telex::routes(&state));
    }
    if state.config.post_telegraph_readonly_enabled {
        api = api.merge(post_telegraph::routes(&state));
    }
    if state.config.post_ticker_readonly_enabled {
        api = api.merge(post_ticker::routes(&state));
    }
    if state.config.group_net_readonly_enabled {
        api = api.merge(group_net::routes(&state));
    }
    if state.config.receive_keypoints_readonly_enabled {
        api = api.merge(receive_keypoints::routes(&state));
    }
    if state.config.simulation_readonly_enabled {
        api = api.merge(simulation::routes(&state));
    }
    if state.config.general_train_readonly_enabled {
        api = api.merge(general_train::routes(&state));
    }
    if state.config.telegraph_key_readonly_enabled {
        api = api.merge(telegraph_key::routes(&state));
    }
    if state.config.telex_readonly_enabled {
        api = api.merge(telex::routes(&state));
    }
    if state.config.ticker_tape_readonly_enabled {
        api = api.merge(ticker_tape::routes(&state));
    }
    if state.config.auth_readonly_enabled {
        api = api.merge(system::routes(&state));
    }
    if state.config.ws_readonly_enabled {
        api = api.merge(ws::routes(&state));
    }
    if state.config.general_train_readonly_enabled
        || state.config.radiotelephone_readonly_enabled
        || state.config.entering_readonly_enabled
    {
        api = api.merge(train_rule::routes(&state));
    }
    let cors = if state.config.cors_origin == "*" {
        CorsLayer::permissive()
    } else {
        CorsLayer::new()
            .allow_origin(
                state
                    .config
                    .cors_origin
                    .parse::<HeaderValue>()
                    .expect("validated origin"),
            )
            .allow_methods(tower_http::cors::Any)
            .allow_headers(tower_http::cors::Any)
    };
    Router::new()
        .route("/health", get(health))
        .route("/live", get(live))
        .route("/ready", get(ready))
        .merge(api)
        .fallback(not_found)
        .layer(CatchPanicLayer::custom(panic_response))
        .layer(RequestBodyLimitLayer::new(state.config.body_limit))
        .layer(cors)
        .layer(middleware::from_fn(request_id))
        .with_state(state)
}
