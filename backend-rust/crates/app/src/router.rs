use axum::{
    extract::DefaultBodyLimit,
    http::HeaderValue,
    middleware,
    routing::{get, post},
    Router,
};
use tower_http::{catch_panic::CatchPanicLayer, cors::CorsLayer, limit::RequestBodyLimitLayer};

use crate::{
    admin, cable, comprehensive, device, entering, equipment, equipment_train, exam, general_aux,
    general_group_net_rule, general_train, grading, group_net, group_net_write, keypoints,
    masthead, military, military_term_data,
    platform::{
        auth::{authenticate, require_admin},
        envelope::openapi,
        health::{admin_probe, auth_probe, health, live, ready},
        http::{not_found, panic_response, request_id},
        state::AppState,
    },
    post_entering, post_military, post_radiotelephone, post_telegram, post_telegram_write,
    post_telegraph, post_telegraph_write, post_telex, post_telex_write, post_ticker,
    question_level, radio_term, radiotelephone, receive_keypoints, simulation, synthetical,
    system::{
        change_password, menu_get_all, role_get_all, user_get_by_token, user_login, user_logout,
        user_signin, verify_password,
    },
    telegram, telegram_write, telegraph_key, telex, test_paper, theory, theory_detail, theory_file,
    theory_question, theory_test, ticker_tape,
    tools::{tools_get_now_time, tools_get_twelve_month},
    train_rule, user_training_stats, ws,
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
        api = api
            .route("/api/tools/getNowTime", get(tools_get_now_time))
            .route("/api/tools/getTwelvemonth", get(tools_get_twelve_month));
    }
    if state.config.theory_readonly_enabled {
        let comprehensive_routes = Router::new()
            .route(
                "/getUserOverallInfo",
                get(comprehensive::get_user_overall_info),
            )
            .route("/getTheoryYear", get(comprehensive::get_theory_year))
            .route(
                "/getTheoryTestYear",
                get(comprehensive::get_theory_test_year),
            )
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest("/api/comprehensive", comprehensive_routes);
    }
    if state.config.cable_readonly_enabled {
        let cable_routes = Router::new()
            .route("/find", post(cable::find))
            .route("/findById", post(cable::find_by_id))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_cable_routes = Router::new()
            .route("/save", post(cable::save))
            .route("/delete", post(cable::delete))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let cable_type_routes = Router::new()
            .route("/find", post(cable::find_types))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_cable_type_routes = Router::new()
            .route("/save", post(cable::save_type))
            .route("/delete", post(cable::delete_type))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let cable_floor_routes = Router::new()
            .route("/find", post(cable::find_floor))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api
            .nest("/api/cable", cable_routes.merge(admin_cable_routes))
            .nest(
                "/api/cable/type",
                cable_type_routes.merge(admin_cable_type_routes),
            )
            .nest("/api/cable/floor", cable_floor_routes);
    }
    if state.config.grading_readonly_enabled {
        let grading_routes = Router::new()
            .route("/getGradingRuleListByType", post(grading::list_by_type))
            .route("/getGradingRuleById", post(grading::by_id))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_grading_routes = Router::new()
            .route("/saveGradingRule", post(grading::save))
            .route("/updateGradingRuleStatus", post(grading::update_status))
            .route("/changeGradingRuleIsDefault", post(grading::change_default))
            .route("/deleteGradingRule", post(grading::delete))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest(
            "/api/gradingRule",
            grading_routes.merge(admin_grading_routes),
        );
    }
    if state.config.device_readonly_enabled {
        let device_routes = Router::new()
            .route("/listPage", post(device::list_devices))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_device_routes = Router::new()
            .route("/save", post(device::save_device))
            .route("/delete", post(device::delete_device))
            .route("/addDeviceDescription", post(device::add_description))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let device_type_routes = Router::new()
            .route("/findAll", post(device::list_types))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_device_type_routes = Router::new()
            .route("/save", post(device::save_type))
            .route("/delete", post(device::delete_type))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api
            .nest("/api/device", device_routes.merge(admin_device_routes))
            .nest(
                "/api/deviceType",
                device_type_routes.merge(admin_device_type_routes),
            );
        let device_scoring_read_routes = Router::new()
            .route("/findAllByDeviceId", get(device::find_scoring_rule))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_device_scoring_routes = Router::new()
            .route("/save", post(device::save_scoring_rule))
            .route("/delete", get(device::delete_scoring_rule))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest(
            "/api/deviceScoringRule",
            device_scoring_read_routes.merge(admin_device_scoring_routes),
        );
    }
    if state.config.equipment_readonly_enabled {
        let equipment_routes = Router::new()
            .route("/listPage", post(equipment::list_devices))
            .route("/listKeyPoints", post(equipment::list_key_points))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_equipment_routes = Router::new()
            .route("/add", post(equipment::add))
            .route("/update", post(equipment::update))
            .route("/delete", post(equipment::delete))
            .route("/addKeyPoints", post(equipment::add_key_points))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest(
            "/api/equipmentDevice",
            equipment_routes.merge(admin_equipment_routes),
        );
    }
    if state.config.entering_readonly_enabled {
        let entering_routes = Router::new()
            .route("/listPage", post(entering::list))
            .route("/add", post(entering::add))
            .route("/getById", post(entering::by_id))
            .route("/getWordStock", post(entering::get_word_stock))
            .route("/lastTrain", post(entering::last_train))
            .route("/begin", post(entering::begin))
            .route("/goTo", post(entering::go_to))
            .route("/pause", post(entering::pause))
            .route("/finish", post(entering::finish))
            .route("/statisticalPage", post(entering::statistics))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let telex_routes = Router::new()
            .route("/findByUserIdAndType", post(entering::telex_find))
            .route("/save", post(entering::telex_save))
            .route("/clear", post(entering::telex_clear))
            .route("/statisticalPage", post(entering::telex_statistics))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let keypoint_routes = Router::new()
            .route("/getByType", post(entering::keypoints))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_entering_keypoint_routes = Router::new()
            .route("/save", post(entering::save_key_points))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let post_entering_routes = Router::new()
            .route("/add", post(post_entering::add))
            .route("/listPage", post(post_entering::list_page))
            .route("/begin", post(post_entering::begin))
            .route("/finish", post(post_entering::finish))
            .route("/getWordStock", post(post_entering::get_word_stock))
            .route("/getById", post(post_entering::get_by_id))
            .route("/delete", get(post_entering::delete))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let word_stock_read_routes = Router::new()
            .route(
                "/view",
                post(post_entering::word_stock_view)
                    .layer(DefaultBodyLimit::max(state.config.body_limit)),
            )
            .route("/listPage", post(post_entering::word_stock_list_page))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let word_stock_admin_routes = Router::new()
            .route("/add", post(post_entering::word_stock_add))
            .route("/delete", post(post_entering::word_stock_delete))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api
            .nest("/api/enteringExercise", entering_routes)
            .nest("/api/enteringTelexPat", telex_routes)
            .nest(
                "/api/enteringKeyPoints",
                keypoint_routes.merge(admin_entering_keypoint_routes),
            )
            .nest("/api/postEnteringExercise", post_entering_routes)
            .nest(
                "/api/postEnteringExerciseWordStock",
                word_stock_read_routes.merge(word_stock_admin_routes),
            );
    }
    if state.config.equipment_readonly_enabled {
        let train_routes = Router::new()
            .route("/listPage", post(equipment_train::list))
            .route("/detail", post(equipment_train::detail))
            .route("/add", post(equipment_train::add))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest("/api/equipmentTrain", train_routes);
    }
    if state.config.radiotelephone_readonly_enabled {
        let radio_routes = Router::new()
            .route("/listPage", post(radiotelephone::list))
            .route("/begin", post(radiotelephone::begin))
            .route("/pause", post(radiotelephone::pause))
            .route("/resume", post(radiotelephone::resume))
            .route("/finish", post(radiotelephone::finish))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let military_routes = Router::new()
            .route("/findAll", post(military::list))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let military_term_read_routes = Router::new()
            .route("/findAll", post(military_term_data::find_all))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let military_term_admin_routes = Router::new()
            .route("/saveAll", post(military_term_data::save_all))
            .route("/save", post(military_term_data::save))
            .route("/delete", post(military_term_data::delete))
            .route("/update", post(military_term_data::update))
            .route("/move", post(military_term_data::move_term))
            .route("/saveBatch", post(military_term_data::save_batch))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let radio_term_routes = Router::new()
            .route("/findByType", post(radio_term::find_by_type))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let radio_term_admin_routes = Router::new()
            .route("/save", post(train_rule::term_save))
            .route("/delete", post(train_rule::term_delete))
            .route("/update", post(train_rule::term_update))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let radio_train_routes = Router::new()
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
        let military_train_routes = Router::new()
            .route("/add", post(post_military::add))
            .route("/listPage", post(post_military::list_page))
            .route("/details", post(post_military::details))
            .route("/begin", post(post_military::begin))
            .route("/finish", post(post_military::finish))
            .route("/delete", get(post_military::delete))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api
            .nest("/api/radiotelephone", radio_routes)
            .nest("/api/militaryTermData", military_routes)
            .nest(
                "/api/mtd",
                military_term_read_routes.merge(military_term_admin_routes),
            )
            .nest(
                "/api/postRadiotelephoneTermData",
                radio_term_routes.merge(radio_term_admin_routes),
            )
            .nest("/api/postRadiotelephoneTrain", radio_train_routes)
            .nest("/api/postMilitaryTermTrain", military_train_routes);
    }
    if state.config.telegram_readonly_enabled {
        let telegram_routes = Router::new()
            .route("/getAll", post(telegram::list))
            .route("/getById", post(telegram::by_id))
            .route(
                "/getFloorContentByFloorId",
                post(telegram::floor_content_by_floor_id),
            )
            .route(
                "/getFloorContentByFloorIdAsync",
                post(telegram::floor_content_by_floor_id_async),
            )
            .route(
                "/getFloorContentByFloor",
                post(telegram::floor_content_by_floor),
            )
            .route(
                "/getTelegramTrainLog",
                post(telegram::get_telegram_train_log),
            )
            .route("/getSetting", post(telegram::get_setting))
            .route("/statisticalPage", post(telegram::statistical_page))
            .route("/lastTrain", post(telegram::last_train))
            .route("/saveTelegramTrain", post(telegram_write::save))
            .route(
                "/saveFloorContent",
                post(telegram_write::save_floor_content),
            )
            .route("/saveBaseTrain", post(telegram_write::save_base_train))
            .route("/startTelegramTrain", post(telegram_write::start))
            .route("/pauseTelegramTrain", post(telegram_write::pause))
            .route("/endTelegramTrain", post(telegram_write::end))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let telegram_admin_routes = Router::new()
            .route("/saveSetting", post(telegram_write::save_setting))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest(
            "/api/telegramTrain",
            telegram_routes.merge(telegram_admin_routes),
        );
    }
    if state.config.post_telegram_readonly_enabled {
        let post_telegram_routes = Router::new()
            .route("/findAll", post(post_telegram::list))
            .route("/detail", post(post_telegram::detail))
            .route("/findMessageBody", post(post_telegram_write::content))
            .route(
                "/printBottomReport",
                post(post_telegram_write::print_bottom_report),
            )
            .route("/saveContentValue", post(post_telegram_write::save_content))
            .route(
                "/addContentValue",
                post(post_telegram_write::add_content_value),
            )
            .route("/save", post(post_telegram_write::add))
            .route("/begin", post(post_telegram_write::begin))
            .route("/finish", post(post_telegram_write::finish))
            .route("/stop", post(post_telegram_write::stop))
            .route("/delete", get(post_telegram_write::delete))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest("/api/postTelegramTrain", post_telegram_routes);
    }
    if state.config.post_telex_readonly_enabled {
        let post_telex_routes = Router::new()
            .route("/findAll", post(post_telex::list))
            .route("/detail", post(post_telex::detail))
            .route("/getPage", get(post_telex::get_page))
            .route("/finishPage", post(post_telex::finish_page))
            .route("/save", post(post_telex_write::add))
            .route("/begin", post(post_telex_write::begin))
            .route("/pause", post(post_telex_write::pause))
            .route("/resume", post(post_telex_write::resume))
            .route("/reset", post(post_telex_write::reset))
            .route("/finish", post(post_telex_write::finish))
            .route("/delete", get(post_telex_write::delete))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest("/api/postTelexPatTrain", post_telex_routes);
    }
    if state.config.post_telegraph_readonly_enabled {
        let post_telegraph_routes = Router::new()
            .route("/listPage", post(post_telegraph::list))
            .route("/details", post(post_telegraph::detail))
            .route("/add", post(post_telegraph_write::add))
            .route("/begin", post(post_telegraph_write::begin))
            .route("/finish", post(post_telegraph_write::finish))
            .route("/getPage", get(post_telegraph::get_page))
            .route("/finishPage", post(post_telegraph_write::finish_page))
            .route("/reset", post(post_telegraph_write::reset))
            .route("/delete", get(post_telegraph_write::delete))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest("/api/PostTelegraphKeyPatTrain", post_telegraph_routes);
    }
    if state.config.post_ticker_readonly_enabled {
        let post_ticker_routes = Router::new()
            .route("/listPage", post(post_ticker::list))
            .route("/getById", post(post_ticker::detail))
            .route("/add", post(post_ticker::add))
            .route("/begin", post(post_ticker::begin))
            .route("/finish", post(post_ticker::finish))
            .route("/reset", post(post_ticker::reset))
            .route("/upLoadResult", post(post_ticker::upload_result))
            .route("/findPage", get(post_ticker::find_page))
            .route("/delete", get(post_ticker::delete))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let ticker_setting_read_routes = Router::new()
            .route("/findAll", post(post_ticker::setting_list))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let ticker_setting_admin_routes = Router::new()
            .route("/addOrUpdate", post(post_ticker::setting_add_or_update))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let masthead_routes = Router::new()
            .route("/findByTrainId", get(masthead::find_by_train_id))
            .route("/save", post(masthead::save))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api
            .nest("/api/postTickerTapeTrain", post_ticker_routes)
            .nest(
                "/api/postTickerTapeTrainSetting",
                ticker_setting_read_routes.merge(ticker_setting_admin_routes),
            )
            .nest("/api/masthead", masthead_routes);
    }
    if state.config.group_net_readonly_enabled {
        let group_net_routes = Router::new()
            .route("/listPage", post(group_net::list))
            .route("/details", get(group_net::detail))
            .route("/saveTrain", post(group_net_write::save))
            .route("/submitAnswer", post(group_net_write::submit))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let group_rule_read_routes = Router::new()
            .route("/findAll", post(general_group_net_rule::find_all))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let group_rule_admin_routes = Router::new()
            .route("/save", post(general_group_net_rule::save))
            .route("/deleteById", post(general_group_net_rule::delete_by_id))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest("/api/groupNetTrain", group_net_routes).nest(
            "/api/generalGroupNetRule",
            group_rule_read_routes.merge(group_rule_admin_routes),
        );
    }
    if state.config.general_train_readonly_enabled
        || state.config.radiotelephone_readonly_enabled
        || state.config.entering_readonly_enabled
    {
        let train_rule_read_routes = Router::new()
            .route("/findByType", post(train_rule::find_by_type))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let train_rule_admin_routes = Router::new()
            .route("/addRule", post(train_rule::add_rule))
            .route("/deleteById", post(train_rule::delete_by_id))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest(
            "/api/postTrainGlobalRule",
            train_rule_read_routes.merge(train_rule_admin_routes),
        );
    }
    if state.config.receive_keypoints_readonly_enabled {
        let receive_keypoint_routes = Router::new()
            .route("/getByType", post(receive_keypoints::get_by_type))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_receive_keypoint_routes = Router::new()
            .route("/save", post(receive_keypoints::save))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let keypoint_routes = Router::new()
            .route("/findKeyPointsByType", post(keypoints::key))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_keypoint_routes = Router::new()
            .route("/saveKeyPoints", post(keypoints::save_key_points))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api
            .nest(
                "/api/receiveKeyPoints",
                receive_keypoint_routes.merge(admin_receive_keypoint_routes),
            )
            .nest(
                "/api/keyPoints",
                keypoint_routes.merge(admin_keypoint_routes),
            );
    }
    if state.config.simulation_readonly_enabled {
        let router_routes = Router::new()
            .route("/addRoom", post(simulation::add_router))
            .route("/findRoom", post(simulation::list_router))
            .route("/getRoomUserList/{roomId}", get(simulation::users))
            .route("/changeChannel", post(simulation::change_channel))
            .route("/getRoomDetail", get(simulation::detail))
            .route("/getRoomChannels", get(simulation::channels))
            .route("/findPage", get(simulation::find_page))
            .route("/sendFinish", post(simulation::finish))
            .route("/delete", get(simulation::delete))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let report_routes = Router::new()
            .route("/addRoom", post(simulation::add_report))
            .route("/findRoom", post(simulation::list_report))
            .route("/getRoomDetail", get(simulation::detail))
            .route("/delete", get(simulation::delete))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let recept_routes = Router::new()
            .route("/addRoom", post(simulation::add_recept))
            .route("/findRoom", post(simulation::list_recept))
            .route("/getRoomDetail", get(simulation::detail))
            .route("/delete", get(simulation::delete))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let content_routes = Router::new()
            .route("/addRoomAndContent", post(simulation::add_disturb))
            .route("/addStudent", post(simulation::add_student))
            .route("/findAlls", post(simulation::list_disturb))
            .route("/findById", post(simulation::detail_body))
            .route("/findTrainUser", post(simulation::users_body))
            .route("/uploadResult", post(simulation::upload_result))
            .route("/saveSetting", post(simulation::save_setting))
            .route("/delete", get(simulation::delete))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let socket_routes = Router::new()
            .route("/getAllRoomInfo", get(ws::simulation_all_room_info))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api
            .nest("/api/simulation/router", router_routes)
            .nest("/api/simulation/report", report_routes)
            .nest("/api/simulation/recept", recept_routes)
            .nest("/api/simulation/routerRoomContent", content_routes)
            .nest("/api/simulation/socket", socket_routes);
    }
    if state.config.general_train_readonly_enabled {
        let key_routes = Router::new()
            .route("/add", post(general_train::key_add))
            .route("/findAll", post(general_train::key_list))
            .route("/findPage", post(general_train::key_page))
            .route("/getPage", post(general_train::key_get_page))
            .route("/detail", post(general_train::key_detail))
            .route("/patDetail", post(general_train::key_members))
            .route("/getPatValue", post(general_train::key_pat_value))
            .route("/getTrainInfo", get(general_train::key_export))
            .route("/getTrainInfoBatch", get(general_train::key_export_batch))
            .route("/importTrainInfo", post(general_train::key_import))
            .route(
                "/importTrainInfoBatch",
                post(general_train::key_import_batch),
            )
            .route("/uploadResult", post(general_train::key_upload))
            .route("/updateTrainStatus", post(general_train::key_update))
            .route("/finish", post(general_train::key_finish))
            .route("/statistics", post(general_train::key_statistics))
            .route("/getOnline", get(general_train::key_online))
            .route("/startTrain", get(general_train::key_start))
            .route("/reset", post(general_train::key_reset))
            .route("/delete", get(general_train::key_delete))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let telex_routes = Router::new()
            .route("/add", post(general_train::telex_add))
            .route("/findAll", post(general_train::telex_list))
            .route("/findPage", post(general_train::telex_page))
            .route("/getPage", post(general_train::telex_get_page))
            .route("/detail", post(general_train::telex_detail))
            .route("/patDetail", post(general_train::telex_members))
            .route("/getPatValue", post(general_train::telex_pat_value))
            .route("/uploadResult", post(general_train::telex_upload))
            .route("/updateTrainStatus", post(general_train::telex_update))
            .route("/finish", post(general_train::telex_finish))
            .route("/statistics", post(general_train::telex_statistics))
            .route("/getOnline", get(general_train::telex_online))
            .route("/startTrain", get(general_train::telex_start))
            .route("/delete", get(general_train::telex_delete))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let ticker_routes = Router::new()
            .route("/add", post(general_train::ticker_add))
            .route("/findAll", post(general_train::ticker_list))
            .route("/findPage", post(general_train::ticker_page))
            .route("/getPage", post(general_train::ticker_page))
            .route("/detail", post(general_train::ticker_detail))
            .route("/uploadResult", post(general_train::ticker_upload))
            .route("/updateTrainStatus", post(general_train::ticker_update))
            .route("/finish", post(general_train::ticker_finish))
            .route("/reset", post(general_train::ticker_reset))
            .route("/statistics", post(general_train::ticker_statistics))
            .route("/startTrain", get(general_train::ticker_start))
            .route("/delete", get(general_train::ticker_delete))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let key_online_routes = Router::new()
            .route("/getOneline/{trainId}", get(general_aux::key_online))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let ticker_open_routes = Router::new()
            .route("/综合训练-手键拍发Api", get(general_aux::ticker_online))
            .route("/findTrainInfo", get(general_aux::ticker_train_info))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let ticker_socket_routes = Router::new()
            .route("/getByTrainIdAndUserId", get(general_aux::ticker_user))
            .route(
                "/updateTrainStatus",
                post(general_aux::ticker_update_status),
            )
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api
            .nest("/api/generalKeyPat", key_routes)
            .nest("/api/generalTelexPat", telex_routes)
            .nest("/api/generalTickerPatTrain", ticker_routes)
            .nest("/api/GeneralKeyPatTrain", key_online_routes)
            .nest("/api/openApi/generalTickerPatTrain", ticker_open_routes)
            .nest("/api/socket/generalTickerPatTrain", ticker_socket_routes);
    }
    if state.config.telegraph_key_readonly_enabled {
        let telegraph_key_routes = Router::new()
            .route("/findByType", post(telegraph_key::find_by_type))
            .route("/save", post(telegraph_key::save))
            .route("/clear", post(telegraph_key::clear))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest("/api/telegraphKeyPatTrain", telegraph_key_routes);
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
        api = api.nest("/api/telegraphKeyPatTrainSynthetical", synthetical_routes);
    }
    if state.config.theory_readonly_enabled {
        let theory_routes = Router::new()
            .route("/listPageClassify", post(theory::list_classify))
            .route("/getBasicTheory", post(theory::list_basic))
            .route("/getDutyBusiness", post(theory::duty_business))
            .route("/getChargeEquipment", post(theory::charge_equipment))
            .route("/getBasicTheoryOpen", post(theory::list_open))
            .route("/saveTheoryKnowledgeRecord", post(theory::save_record))
            .route("/recordStatistice", post(theory::record_statistics))
            .route("/gradeCount", post(theory::grade_count))
            .route("/getById", post(theory_detail::by_id))
            .route("/getByIdAndToken", post(theory_detail::by_id_and_token))
            .route("/uploadFileToNip", post(theory_file::upload))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let test_routes = Router::new()
            .route(
                "/saveUserKnowledgeSwfTestContent",
                post(theory_test::save_user),
            )
            .route("/getTestBySwfId", post(theory_test::by_swf))
            .route("/getByKnowledgeSwfIdAndEnable", post(theory_test::enabled))
            .route(
                "/getTestContentByUserIdAndKnowledgeId",
                post(theory_test::by_knowledge),
            )
            .route(
                "/getTestContentByUserIdAndKnowledgeSwfId",
                post(theory_test::by_swf_user),
            )
            .route("/getTestContentById", post(theory_test::by_id))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_test_routes = Router::new()
            .route("/saveTheoryKnowledgeTest", post(theory_test::save))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_theory_routes = Router::new()
            .route("/saveTheoryKnowledge", post(theory::save_knowledge))
            .route("/addClassify", post(theory::save_classify))
            .route("/removeClassify", post(theory::delete_classify))
            .route("/deleteThroyKnowledgeById", post(theory::delete_knowledge))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api
            .nest(
                "/api/theoryKnowledge",
                theory_routes.merge(admin_theory_routes),
            )
            .nest(
                "/api/theoryKnowledgeTest",
                test_routes.merge(admin_test_routes),
            );
    }
    if state.config.telex_readonly_enabled {
        let telex_routes = Router::new()
            .route("/saveTelexPat", post(telex::save_base))
            .route("/findTelexPatById", post(telex::find_base))
            .route("/deleteTexPatByToken", post(telex::delete_base))
            .route("/statisticalPage", post(telex::statistical_page))
            .route("/lastPatTrain", post(telex::last_train))
            .route("/saveTexPatTrain", post(telex::train_save))
            .route("/findTexPatTrainByToken", post(telex::train_list))
            .route("/findTexPatTrainById", post(telex::train_detail))
            .route("/deleteById", post(telex::train_delete))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest("/api/telexPat", telex_routes);
    }
    if state.config.ticker_tape_readonly_enabled {
        let train_routes = Router::new()
            .route("/listPage", post(ticker_tape::list))
            .route("/getById", post(ticker_tape::by_id))
            .route("/add", post(ticker_tape::add))
            .route("/begin", post(ticker_tape::begin))
            .route("/pause", post(ticker_tape::pause))
            .route("/goOn", post(ticker_tape::resume))
            .route("/finish", post(ticker_tape::finish))
            .route("/baseSession", post(ticker_tape::base_session))
            .route("/saveBaseTrain", post(ticker_tape::save_base))
            .route("/discardBaseSession", post(ticker_tape::discard_base))
            .route("/statisticalPage", post(ticker_tape::statistics))
            .route("/lastTrain", post(ticker_tape::last_train))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let setting_routes = Router::new()
            .route("/findAll", post(ticker_tape::settings))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_setting_routes = Router::new()
            .route("/addOrUpdate", post(ticker_tape::setting_add_or_update))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let stage_setting_routes = Router::new()
            .route("/findAll", post(ticker_tape::stage_settings))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_stage_setting_routes = Router::new()
            .route("/add", post(ticker_tape::stage_setting_add))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api
            .nest("/api/tickerTapeTrain", train_routes)
            .nest(
                "/api/tickerTapeTrainSetting",
                setting_routes.merge(admin_setting_routes),
            )
            .nest(
                "/api/tickerTapeTrainStageSetting",
                stage_setting_routes.merge(admin_stage_setting_routes),
            );
    }
    if state.config.theory_readonly_enabled {
        let question_routes = Router::new()
            .route(
                "/findAllTheoryKnowledgeQuestionLevel",
                post(theory_question::list_levels),
            )
            .route(
                "/findAllQuestionByLevelId",
                post(theory_question::list_questions),
            )
            .route("/exportTemplate", post(theory_question::export_template))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_question_routes = Router::new()
            .route(
                "/saveTheoryKnowledgeQuestion",
                post(theory_question::save_question),
            )
            .route(
                "/saveTheoryKnowledgeQuestionLevel",
                post(question_level::save_level),
            )
            .route(
                "/exportQuestionByLevelId",
                post(theory_question::export_questions),
            )
            .route(
                "/deleteTheoryKnowledgeQuestionLevelById",
                post(theory_question::delete_level),
            )
            .route("/saveBatch", post(theory_question::save_batch))
            .route(
                "/deleteTheoryKnowledgeQuestion",
                post(theory_question::delete_question),
            )
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest(
            "/api/theoryKnowledgeQuestion",
            question_routes.merge(admin_question_routes),
        );
    }
    if state.config.theory_readonly_enabled {
        let paper_routes = Router::new()
            .route("/findAllTestPaper", post(test_paper::list))
            .route("/findTestPaperById", post(test_paper::by_id))
            .route(
                "/findTestPaperByLevelIdAndName",
                post(test_paper::by_level_name),
            )
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_paper_routes = Router::new()
            .route("/saveTestPaper", post(test_paper::save))
            .route("/deleteTestPaper", post(test_paper::delete))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest(
            "/api/theoryKnowledgeTestPaper",
            paper_routes.merge(admin_paper_routes),
        );
    }
    if state.config.theory_readonly_enabled {
        let exam_routes = Router::new()
            .route("/findAllTheoryKnowledgeExam", post(exam::list))
            .route("/findTheoryKnowledgeExamById", post(exam::detail))
            .route("/saveTheoryKnowledgeExamSelfTesting", post(exam::self_test))
            .route("/listPageSelfTesting", post(exam::self_list))
            .route("/finishSelfTesting", post(exam::finish_self_test))
            .route(
                "/teacherStartTheoryKnowledgeExam",
                post(exam::teacher_start),
            )
            .route("/studentChangeExamState", post(exam::student_state))
            .route("/studentSaveExamRealtimeContont", post(exam::realtime))
            .route("/examineAnalyse", post(exam::analyse))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_exam_routes = Router::new()
            .route("/savetheoryKnowledgeExam", post(exam::save_formal))
            .route("/deleteTheoryKnowledgeExam", post(exam::delete_exam))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let exam_user_routes = Router::new()
            .route("/findAllTheoryKnowledgeExamUser", post(exam::user_list))
            .route("/findExamUser", post(exam::find_exam_user))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_exam_user_routes = Router::new()
            .route("/teacherUploadScore", post(exam::teacher_upload_score))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api
            .nest(
                "/api/theoryKnowledgeExam",
                exam_routes.merge(admin_exam_routes),
            )
            .nest(
                "/api/theoryKnowledgeExamUser",
                exam_user_routes.merge(admin_exam_user_routes),
            );
    }
    if state.config.auth_readonly_enabled {
        let user_routes = Router::new()
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
        let menu_routes = Router::new()
            .route("/getMenusAll", get(menu_get_all))
            .route("/getMenuById", get(admin::get_menu_by_id))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let role_routes = Router::new()
            .route("/getRoleAll", post(role_get_all))
            .route("/getRoleById", get(admin::get_role_by_id))
            .route("/getRoleMenusInfo", get(admin::get_role_menus_info))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_user_routes = Router::new()
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
        let admin_role_routes = Router::new()
            .route("/addRole", post(admin::add_role))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_menu_routes = Router::new()
            .route("/addMenu", post(admin::add_menu))
            .route_layer(middleware::from_fn(require_admin))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let user_api = Router::new()
            .route("/login", post(user_login))
            .route("/signin", post(user_signin))
            .merge(user_routes)
            .merge(admin_user_routes);
        let menu_api = menu_routes.merge(admin_menu_routes);
        let role_api = role_routes.merge(admin_role_routes);
        api = api
            .nest("/api/user", user_api)
            .nest("/api/menus", menu_api)
            .nest("/api/role", role_api);
    }
    if state.config.ws_readonly_enabled {
        api = api
            .route("/api/ws/status", get(ws::status))
            .route("/api/status", get(ws::status_open))
            .route("/api/websocket/{sid}", get(ws::open).fallback(not_found))
            .route(
                "/api/websocketUnion/{sid}",
                get(ws::open).fallback(not_found),
            )
            .route(
                "/api/startWebsocket/{sid}",
                get(ws::open).fallback(not_found),
            )
            .route(
                "/api/simulation/{id}/{roomId}",
                get(ws::open).fallback(not_found),
            )
            .route(
                "/api/generalKeyPatTrain/{uid}/{trainId}",
                get(ws::open).fallback(not_found),
            )
            .route(
                "/api/generalTelexPatTrain/{uid}/{trainId}",
                get(ws::open).fallback(not_found),
            )
            .route(
                "/api/generalTickerPat/{uid}/{trainId}/{role}",
                get(ws::open).fallback(not_found),
            );
    }
    let ws_root = if state.config.ws_readonly_enabled {
        Router::new()
            .route("/status", get(ws::status_open))
            .route("/websocket/{sid}", get(ws::open).fallback(not_found))
            .route("/websocketUnion/{sid}", get(ws::open).fallback(not_found))
            .route("/startWebsocket/{sid}", get(ws::open).fallback(not_found))
            .route(
                "/simulation/{id}/{roomId}",
                get(ws::open).fallback(not_found),
            )
            .route(
                "/generalKeyPatTrain/{uid}/{trainId}",
                get(ws::open).fallback(not_found),
            )
            .route(
                "/generalTelexPatTrain/{uid}/{trainId}",
                get(ws::open).fallback(not_found),
            )
            .route(
                "/generalTickerPat/{uid}/{trainId}/{role}",
                get(ws::open).fallback(not_found),
            )
    } else {
        Router::new()
    };
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
        .merge(ws_root)
        .merge(api)
        .fallback(not_found)
        .layer(CatchPanicLayer::custom(panic_response))
        .layer(RequestBodyLimitLayer::new(state.config.body_limit))
        .layer(cors)
        .layer(middleware::from_fn(request_id))
        .with_state(state)
}
