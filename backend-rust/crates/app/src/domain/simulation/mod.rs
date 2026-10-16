mod simulation;

use self::simulation::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::platform::auth::authenticate;
    use crate::platform::auth::require_admin;
    use crate::ws;
    use axum::{
        middleware,
        routing::{get, post},
        Router,
    };

    let mut router = Router::new();
    let room = Router::new()
        .route("/addRoom", post(add_router))
        .route("/findRoom", post(list_router))
        .route("/getRoomUserList/{roomId}", get(users))
        .route("/changeChannel", post(change_channel))
        .route("/getRoomDetail", get(detail))
        .route("/getRoomChannels", get(channels))
        .route("/findPage", get(find_page))
        .route("/sendFinish", post(finish))
        .route("/delete", get(delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let report = Router::new()
        .route("/addRoom", post(add_report))
        .route("/findRoom", post(list_report))
        .route("/getRoomDetail", get(detail))
        .route("/delete", get(delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let recept = Router::new()
        .route("/addRoom", post(add_recept))
        .route("/findRoom", post(list_recept))
        .route("/getRoomDetail", get(detail))
        .route("/delete", get(delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let content = Router::new()
        .route("/addRoomAndContent", post(add_disturb))
        .route("/addStudent", post(add_student))
        .route("/findAlls", post(list_disturb))
        .route("/findById", post(detail_body))
        .route("/findTrainUser", post(users_body))
        .route("/uploadResult", post(upload_result))
        .route("/saveSetting", post(save_setting))
        .route("/delete", get(delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let socket = Router::new()
        .route("/getAllRoomInfo", get(ws::notify::simulation_all_room_info))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router
        .nest("/api/simulation/router", room)
        .nest("/api/simulation/report", report)
        .nest("/api/simulation/recept", recept)
        .nest("/api/simulation/routerRoomContent", content)
        .nest("/api/simulation/socket", socket);
    router
}

#[cfg(test)]
mod tests;
