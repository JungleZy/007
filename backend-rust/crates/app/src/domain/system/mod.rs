mod admin;
mod password;
mod system;
mod user_training_stats;

use self::system::*;

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::domain::system::admin;
    use crate::domain::system::user_training_stats;
    use crate::platform::auth::authenticate;
    use crate::platform::auth::require_admin;
    use axum::{
        middleware,
        routing::{get, post},
        Router,
    };

    let mut router = Router::new();
    let user = Router::new()
        .route("/getUsersByToken", post(user_get_by_token))
        .route("/userOut", post(user_logout))
        .route("/changePassword", post(change_password))
        .route("/verifyPassword", post(verify_password))
        .route("/getUserDirectory", post(admin::get_user_directory))
        .route("/findAllTeacher", post(admin::find_all_teacher))
        .route("/findAllStu", post(admin::find_all_stu))
        .route(
            "/getUsersByUserNameStartingWith",
            post(admin::get_users_by_user_name_starting_with),
        )
        .route("/getUsersByIds", post(admin::get_users_by_ids))
        .route(
            "/getUserTrainDurationStat",
            post(user_training_stats::get_user_train_duration_stat),
        )
        .route(
            "/getRecentHandKeyTrains",
            post(user_training_stats::get_recent_hand_key_trains),
        )
        .route(
            "/getRecentElectronicKeyTrains",
            post(user_training_stats::get_recent_electronic_key_trains),
        )
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let menu = Router::new()
        .route("/getMenusAll", get(menu_get_all))
        .route("/getMenuById", get(admin::get_menu_by_id))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let role = Router::new()
        .route("/getRoleAll", post(role_get_all))
        .route("/getRoleById", get(admin::get_role_by_id))
        .route("/getRoleMenusInfo", get(admin::get_role_menus_info))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_user = Router::new()
        .route("/saveUser", post(admin::save_user))
        .route("/importUser", post(admin::import_user))
        .route("/addUserRole", post(admin::add_user_role))
        .route("/getAllUser", post(admin::get_all_user))
        .route("/getUserAndRoleById", post(admin::get_user_and_role_by_id))
        .route("/getUserById", post(admin::get_user_by_id))
        .route(
            "/getUserInfoAllByStatusDesc",
            post(admin::get_user_info_all_by_status_desc),
        )
        .route("/delete", get(admin::delete_user))
        .route("/resetPassword", get(admin::reset_password))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_role = Router::new()
        .route("/addRole", post(admin::add_role))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_menu = Router::new()
        .route("/addMenu", post(admin::add_menu))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let user_api = Router::new()
        .route("/login", post(user_login))
        .route("/signin", post(user_signin))
        .merge(user)
        .merge(admin_user);
    router = router
        .nest("/api/user", user_api)
        .nest("/api/menus", menu.merge(admin_menu))
        .nest("/api/role", role.merge(admin_role));
    router
}

#[cfg(test)]
mod tests;
