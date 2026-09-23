#![recursion_limit = "256"]
use std::collections::{HashMap, HashSet};
use std::{
    env,
    net::SocketAddr,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc,
    },
    time::{Duration, SystemTime, UNIX_EPOCH},
};

use axum::{
    extract::{DefaultBodyLimit, Extension, Request, State},
    http::{HeaderValue, StatusCode},
    middleware::{self, Next},
    response::{IntoResponse, Response},
    routing::{get, post},
    Json, Router,
};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use sqlx::{mysql::MySqlPoolOptions, MySqlConnection, MySqlPool, Row};
use thiserror::Error;
use time::OffsetDateTime;
use tokio::{net::TcpListener, signal, time::timeout};
use tower_http::{cors::CorsLayer, limit::RequestBodyLimitLayer};
use tracing::{error, info, warn};
use uuid::Uuid;
mod admin;
mod cable;
mod device;
mod equipment;
mod equipment_train;
mod exam;
mod grading;
mod password;
mod test_paper;
mod theory;
mod theory_detail;
mod theory_file;
mod theory_question;
mod theory_test;

const LISTEN_ADDR_ENV: &str = "RUST_BACKEND_ADDR";
const DATABASE_URL_ENV: &str = "RUST_BACKEND_DATABASE_URL";
const CABLE_READONLY_ENV: &str = "RUST_PHASE4_CABLE_ENABLED";
const GRADING_READONLY_ENV: &str = "RUST_PHASE4_GRADING_ENABLED";
const DEVICE_READONLY_ENV: &str = "RUST_PHASE4_DEVICE_ENABLED";
const EQUIPMENT_READONLY_ENV: &str = "RUST_PHASE4_EQUIPMENT_ENABLED";
const THEORY_READONLY_ENV: &str = "RUST_PHASE5_THEORY_ENABLED";
const TOOLS_READONLY_ENV: &str = "RUST_PHASE2_TOOLS_ENABLED";
const AUTH_READONLY_ENV: &str = "RUST_PHASE3_AUTH_ENABLED";
const DEFAULT_LISTEN_ADDR: &str = "127.0.0.1:18002";
const DEFAULT_BODY_LIMIT: usize = 11 * 1024 * 1024;

#[derive(Debug, Error)]
enum AppError {
    #[error("configuration error: {0}")]
    Config(String),
    #[error("failed to bind HTTP listener: {0}")]
    Bind(#[source] std::io::Error),
    #[error("invalid database configuration: {0}")]
    Database(#[from] sqlx::Error),
}

#[derive(Clone, Debug)]
struct AppConfig {
    listen_addr: SocketAddr,
    database_url: Option<String>,
    max_connections: u32,
    db_timeout: Duration,
    body_limit: usize,
    cors_origin: String,
    cable_readonly_enabled: bool,
    tools_readonly_enabled: bool,
    device_readonly_enabled: bool,
    equipment_readonly_enabled: bool,
    theory_readonly_enabled: bool,
    grading_readonly_enabled: bool,
    auth_readonly_enabled: bool,
}

#[derive(Clone)]
struct AppState {
    config: Arc<AppConfig>,
    pool: Option<MySqlPool>,
    database_ready: Arc<AtomicBool>,
}
#[derive(Clone, Debug)]
struct AuthUser {
    id: String,
    is_admin: bool,
}
#[derive(Clone)]
struct MenuRow {
    id: String,
    parent_id: String,
    key: Option<String>,
    path: Option<String>,
    name: Option<String>,
    icon: Option<String>,
    icon_f: Option<String>,
    height: Option<String>,
    title: Option<String>,
    is_menu: Option<i32>,
    is_bread: Option<i32>,
    component: Option<String>,
    sort: Option<i32>,
}

#[derive(Serialize)]
struct HealthResponse {
    status: &'static str,
}

#[derive(Serialize)]
struct ReadinessResponse {
    status: &'static str,
    readiness: &'static str,
    dependencies: bool,
}

#[derive(Serialize)]
struct Envelope<T> {
    code: i32,
    data: Option<T>,
    message: String,
    description: String,
}

#[derive(Serialize)]
struct AuthProbeResponse {
    user_id: String,
    is_admin: bool,
}

#[derive(Debug, Deserialize)]
struct LoginRequest {
    #[serde(rename = "userAccount")]
    user_account: Option<String>,
    password: Option<String>,
    #[serde(rename = "deviceId")]
    device_id: Option<String>,
}

#[derive(Debug, Deserialize)]
struct PasswordChangeRequest {
    #[serde(rename = "oldPassword")]
    old_password: Option<String>,
    #[serde(rename = "newPassword")]
    new_password: Option<String>,
    #[serde(rename = "newPasswordV")]
    new_password_verification: Option<String>,
}

#[derive(Debug, Deserialize)]
struct RegistrationRequest {
    id: Option<String>,
    #[serde(rename = "userAccount")]
    user_account: Option<String>,
    #[serde(rename = "userName")]
    user_name: Option<String>,
    #[serde(rename = "idCard")]
    id_card: Option<String>,
    #[serde(rename = "userSex")]
    user_sex: Option<i32>,
    password: Option<String>,
    #[serde(rename = "userImg")]
    user_img: Option<String>,
    eday: Option<String>,
}

#[tokio::main]
async fn main() {
    init_tracing();
    if let Err(error) = run().await {
        error!(error = %error, "rust backend stopped with an error");
        std::process::exit(1);
    }
}

async fn run() -> Result<(), AppError> {
    let config = Arc::new(AppConfig::from_env()?);
    let state = build_state(config.clone()).await?;
    let listener = TcpListener::bind(config.listen_addr)
        .await
        .map_err(AppError::Bind)?;
    let app = build_router(state.clone());

    info!(
        database_configured = config.database_url.is_some(),
        tools_readonly_enabled = config.tools_readonly_enabled,
        auth_readonly_enabled = config.auth_readonly_enabled,
        max_connections = config.max_connections,
        body_limit = config.body_limit,
        "rust backend listening"
    );
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await
        .map_err(|error| AppError::Bind(std::io::Error::other(error)))?;
    info!("rust backend stopped gracefully");
    Ok(())
}

impl AppConfig {
    fn from_env() -> Result<Self, AppError> {
        let listen_addr = parse_socket_addr(LISTEN_ADDR_ENV, DEFAULT_LISTEN_ADDR)?;
        let database_url = env::var(DATABASE_URL_ENV)
            .ok()
            .filter(|value| !value.trim().is_empty());
        let max_connections = parse_u32("RUST_BACKEND_MAX_CONNECTIONS", 10)?;
        if max_connections == 0 {
            return Err(AppError::Config(
                "RUST_BACKEND_MAX_CONNECTIONS must be positive".into(),
            ));
        }
        let timeout_seconds = parse_u64("RUST_BACKEND_DB_TIMEOUT_SECS", 5)?;
        if timeout_seconds == 0 {
            return Err(AppError::Config(
                "RUST_BACKEND_DB_TIMEOUT_SECS must be positive".into(),
            ));
        }
        let body_limit = parse_usize("RUST_BACKEND_BODY_LIMIT_BYTES", DEFAULT_BODY_LIMIT)?;
        if body_limit == 0 {
            return Err(AppError::Config(
                "RUST_BACKEND_BODY_LIMIT_BYTES must be positive".into(),
            ));
        }
        let cors_origin = env::var("RUST_BACKEND_CORS_ORIGIN").unwrap_or_else(|_| "*".into());
        if cors_origin != "*" {
            cors_origin.parse::<HeaderValue>().map_err(|_| {
                AppError::Config("RUST_BACKEND_CORS_ORIGIN must be '*' or one valid origin".into())
            })?;
        }
        let tools_readonly_enabled = parse_bool(TOOLS_READONLY_ENV, false)?;
        let cable_readonly_enabled = parse_bool(CABLE_READONLY_ENV, false)?;
        let device_readonly_enabled = parse_bool(DEVICE_READONLY_ENV, false)?;
        let equipment_readonly_enabled = parse_bool(EQUIPMENT_READONLY_ENV, false)?;
        let theory_readonly_enabled = parse_bool(THEORY_READONLY_ENV, false)?;
        let grading_readonly_enabled = parse_bool(GRADING_READONLY_ENV, false)?;
        let auth_readonly_enabled = parse_bool(AUTH_READONLY_ENV, false)?;
        Ok(Self {
            listen_addr,
            database_url,
            max_connections,
            db_timeout: Duration::from_secs(timeout_seconds),
            body_limit,
            cors_origin,
            cable_readonly_enabled,
            tools_readonly_enabled,
            device_readonly_enabled,
            equipment_readonly_enabled,
            theory_readonly_enabled,
            grading_readonly_enabled,
            auth_readonly_enabled,
        })
    }
}

async fn build_state(config: Arc<AppConfig>) -> Result<AppState, AppError> {
    let pool = match config.database_url.as_deref() {
        Some(url) => Some(
            MySqlPoolOptions::new()
                .max_connections(config.max_connections)
                .acquire_timeout(config.db_timeout)
                .connect_lazy(url)?,
        ),
        None => None,
    };
    let state = AppState {
        config,
        pool,
        database_ready: Arc::new(AtomicBool::new(false)),
    };
    refresh_database_readiness(&state).await;
    Ok(state)
}

fn build_router(state: AppState) -> Router {
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
    if state.config.equipment_readonly_enabled {
        let train_routes = Router::new()
            .route("/listPage", post(equipment_train::list))
            .route("/detail", post(equipment_train::detail))
            .route("/add", post(equipment_train::add))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        api = api.nest("/api/equipmentTrain", train_routes);
    }
    if state.config.theory_readonly_enabled {
        let theory_routes = Router::new()
            .route("/listPageClassify", post(theory::list_classify))
            .route("/getBasicTheory", post(theory::list_basic))
            .route("/recordStatistice", post(theory::record_statistics))
            .route("/gradeCount", post(theory::grade_count))
            .route("/getById", post(theory_detail::by_id))
            .route("/getByIdAndToken", post(theory_detail::by_id_and_token))
            .route("/uploadFileToNip", post(theory_file::upload))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let test_routes = Router::new()
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
        let admin_theory_routes = Router::new()
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
            .nest("/api/theoryKnowledgeTest", test_routes);
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
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let menu_routes = Router::new()
            .route("/getMenusAll", get(menu_get_all))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let role_routes = Router::new()
            .route("/getRoleAll", post(role_get_all))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
        let admin_user_routes = Router::new()
            .route("/saveUser", post(admin::save_user))
            .route("/importUser", post(admin::import_user))
            .route("/addUserRole", post(admin::add_user_role))
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
        .layer(DefaultBodyLimit::max(state.config.body_limit))
        .layer(RequestBodyLimitLayer::new(state.config.body_limit))
        .layer(cors)
        .layer(middleware::from_fn(request_id))
        .with_state(state)
}

async fn request_id(request: Request, next: Next) -> Response {
    let id = request
        .headers()
        .get("x-request-id")
        .and_then(|value| value.to_str().ok())
        .filter(|value| !value.is_empty() && value.len() <= 128)
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| Uuid::new_v4().to_string());
    let method = request.method().clone();
    let uri = request.uri().path().to_owned();
    let mut response = next.run(request).await;
    if let Ok(header_value) = HeaderValue::from_str(&id) {
        response.headers_mut().insert("x-request-id", header_value);
    }
    info!(request_id = %id, method = %method, path = %uri, status = response.status().as_u16(), "request completed");
    response
}

async fn authenticate(State(state): State<AppState>, mut request: Request, next: Next) -> Response {
    let token = match header_text(&request, "token") {
        Some(value) => value,
        None => return envelope_response(StatusCode::OK, 203, "token不能为空", ""),
    };
    let device_id = match header_text(&request, "deviceId") {
        Some(value) => value,
        None => return envelope_response(StatusCode::OK, 204, "设备标识不能为空", ""),
    };
    match lookup_user(&state, token, device_id).await {
        Ok(Some(user)) => {
            request.extensions_mut().insert(user);
            next.run(request).await
        }
        Ok(None) => envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(error) => {
            warn!(error = %error, "authentication dependency failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

async fn require_admin(request: Request, next: Next) -> Response {
    let Some(user) = request.extensions().get::<AuthUser>() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    if !user.is_admin {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    next.run(request).await
}

async fn lookup_user(
    state: &AppState,
    token: &str,
    device_id: &str,
) -> Result<Option<AuthUser>, sqlx::Error> {
    let Some(pool) = &state.pool else {
        return Ok(None);
    };
    let digest = token_digest(token);
    let row = sqlx::query(
        "SELECT u.id, EXISTS(SELECT 1 FROM t_user_role ur JOIN t_role r ON r.id = ur.role_id WHERE ur.user_id = u.id AND r.is_admin = 0) AS is_admin FROM t_user u WHERE u.token = ? AND u.device_id = ? LIMIT 1",
    )
    .bind(digest)
    .bind(device_id)
    .fetch_optional(pool)
    .await?;
    Ok(row.map(|row| AuthUser {
        id: row.get::<String, _>("id"),
        is_admin: row.get::<i64, _>("is_admin") != 0,
    }))
}

async fn refresh_database_readiness(state: &AppState) {
    let ready = match &state.pool {
        Some(pool) => timeout(
            state.config.db_timeout,
            sqlx::query("SELECT 1").execute(pool),
        )
        .await
        .map_or(false, |result| result.is_ok()),
        None => false,
    };
    state.database_ready.store(ready, Ordering::Release);
}

async fn health() -> (StatusCode, Json<HealthResponse>) {
    (StatusCode::OK, Json(HealthResponse { status: "ok" }))
}

async fn live() -> (StatusCode, Json<HealthResponse>) {
    (StatusCode::OK, Json(HealthResponse { status: "ok" }))
}

async fn ready(State(state): State<AppState>) -> (StatusCode, Json<ReadinessResponse>) {
    refresh_database_readiness(&state).await;
    if state.pool.is_none() {
        return (
            StatusCode::OK,
            Json(ReadinessResponse {
                status: "ready",
                readiness: "process-only",
                dependencies: false,
            }),
        );
    }
    if state.database_ready.load(Ordering::Acquire) {
        (
            StatusCode::OK,
            Json(ReadinessResponse {
                status: "ready",
                readiness: "dependencies-ready",
                dependencies: true,
            }),
        )
    } else {
        (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(ReadinessResponse {
                status: "not-ready",
                readiness: "database-unavailable",
                dependencies: false,
            }),
        )
    }
}

async fn auth_probe(Extension(user): Extension<AuthUser>) -> Response {
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!(AuthProbeResponse {
            user_id: user.id,
            is_admin: user.is_admin
        })),
        "ok",
        "",
    )
}

async fn admin_probe(Extension(user): Extension<AuthUser>) -> Response {
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!(AuthProbeResponse {
            user_id: user.id,
            is_admin: user.is_admin
        })),
        "ok",
        "",
    )
}

async fn user_logout(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE t_user SET token = NULL, device_id = NULL WHERE id = ?")
        .bind(&user.id)
        .execute(pool)
        .await
    {
        Ok(result) if result.rows_affected() == 1 => {
            envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", "")
        }
        Ok(_) => envelope_json(StatusCode::OK, 200, Some(json!(false)), "ok", ""),
        Err(error) => {
            warn!(error = %error, user_id = %user.id, "logout update failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

async fn change_password(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(request): Json<PasswordChangeRequest>,
) -> Response {
    let Some(old_password) = request.old_password.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(new_password) = request.new_password.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(new_password_verification) = request
        .new_password_verification
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => {
            warn!(error = %error, "password change transaction could not start");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let row = match sqlx::query("SELECT password FROM t_user WHERE id = ? LIMIT 1 FOR UPDATE")
        .bind(&user.id)
        .fetch_optional(&mut *transaction)
        .await
    {
        Ok(Some(row)) => row,
        Ok(None) => {
            let _ = transaction.rollback().await;
            return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", "");
        }
        Err(error) => {
            let _ = transaction.rollback().await;
            warn!(error = %error, user_id = %user.id, "password query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    if !password::verify(
        &old_password,
        row.get::<Option<String>, _>("password").as_deref(),
    )
    .matches
    {
        let _ = transaction.rollback().await;
        return envelope_json(StatusCode::OK, 200, Some(json!(false)), "当前密码错误", "");
    }
    if new_password != new_password_verification {
        let _ = transaction.rollback().await;
        return envelope_json(StatusCode::OK, 200, Some(json!(false)), "密码不一致", "");
    }
    let new_hash = match password::hash(&new_password) {
        Ok(hash) => hash,
        Err(error) => {
            let _ = transaction.rollback().await;
            warn!(error, "password hash generation failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    if let Err(error) = sqlx::query("UPDATE t_user SET password = ? WHERE id = ?")
        .bind(new_hash)
        .bind(&user.id)
        .execute(&mut *transaction)
        .await
    {
        let _ = transaction.rollback().await;
        warn!(error = %error, user_id = %user.id, "password update failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if let Err(error) = transaction.commit().await {
        warn!(error = %error, user_id = %user.id, "password change commit failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(true)), "数据正常", "")
}

async fn user_signin(
    State(state): State<AppState>,
    Json(request): Json<RegistrationRequest>,
) -> Response {
    if request.id.as_deref().is_some_and(|id| !id.is_empty()) {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    }
    let Some(user_account) = request.user_account.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    if !valid_account(&user_account) {
        return envelope_response(
            StatusCode::OK,
            500,
            "账户格式不正确，最少4位最多32位，只能是数字或字母或汉字",
            "",
        );
    }
    let Some(id_card) = request.id_card.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 500, "身份证格式错误", "");
    };
    let Some(bday) = registration_birthday(&id_card) else {
        return envelope_response(StatusCode::OK, 500, "身份证格式错误", "");
    };
    let Some(plaintext) = request.password.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => {
            warn!(error = %error, "registration transaction could not start");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let account_exists = sqlx::query("SELECT 1 FROM t_user WHERE user_account = ? LIMIT 1")
        .bind(&user_account)
        .fetch_optional(&mut *transaction)
        .await;
    match account_exists {
        Ok(Some(_)) => {
            let _ = transaction.rollback().await;
            return envelope_response(StatusCode::OK, 500, "用户名已被注册", "");
        }
        Err(error) => {
            let _ = transaction.rollback().await;
            warn!(error = %error, "registration duplicate query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
        Ok(None) => {}
    }
    let default_role =
        match sqlx::query("SELECT id FROM t_role WHERE is_default = 0 ORDER BY id LIMIT 1")
            .fetch_optional(&mut *transaction)
            .await
        {
            Ok(Some(row)) => row.get::<String, _>("id"),
            Ok(None) => {
                let _ = transaction.rollback().await;
                warn!("registration cannot proceed without a default role");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
            Err(error) => {
                let _ = transaction.rollback().await;
                warn!(error = %error, "registration default role query failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
    let password_hash = match password::hash(&plaintext) {
        Ok(hash) => hash,
        Err(error) => {
            let _ = transaction.rollback().await;
            warn!(error, "registration password hash failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let user_id = Uuid::new_v4().to_string();
    let user_img = request
        .user_img
        .unwrap_or_else(|| "/userImages/pag-avatar-man.png".into());
    if let Err(error) = sqlx::query(
        "INSERT INTO t_user (id, user_account, user_img, user_name, id_card, user_sex, password, status, bday, eday) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
    )
    .bind(&user_id)
    .bind(&user_account)
    .bind(&user_img)
    .bind(&request.user_name)
    .bind(&id_card)
    .bind(request.user_sex)
    .bind(password_hash)
    .bind(&bday)
    .bind(&request.eday)
    .execute(&mut *transaction)
    .await
    {
        let _ = transaction.rollback().await;
        warn!(error = %error, "registration user insert failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if let Err(error) =
        sqlx::query("INSERT INTO t_user_role (id, user_id, role_id) VALUES (?, ?, ?)")
            .bind(Uuid::new_v4().to_string())
            .bind(&user_id)
            .bind(&default_role)
            .execute(&mut *transaction)
            .await
    {
        let _ = transaction.rollback().await;
        warn!(error = %error, "registration default role insert failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if let Err(error) = transaction.commit().await {
        warn!(error = %error, "registration transaction commit failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(
            json!({"id": user_id, "userName": request.user_name, "userAccount": user_account, "userImg": user_img}),
        ),
        "ok",
        "",
    )
}

async fn verify_password(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    body: String,
) -> Response {
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT password FROM t_user WHERE id = ? LIMIT 1")
        .bind(&user.id)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(
            StatusCode::OK,
            200,
            Some(json!(
                password::verify(&body, row.get::<Option<String>, _>("password").as_deref())
                    .matches
            )),
            "数据正常",
            "",
        ),
        Ok(None) => envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(error) => {
            warn!(error = %error, user_id = %user.id, "verify password query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

async fn user_login(State(state): State<AppState>, Json(request): Json<LoginRequest>) -> Response {
    let Some(user_account) = request.user_account.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(plaintext) = request.password.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(device_id) = request.device_id.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => {
            warn!(error = %error, "login transaction could not start");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let row = match sqlx::query(
        "SELECT id, wkno, phone, email, user_account, user_img, user_name, id_card, user_sex, password, update_flag, status, bday, eday, dday FROM t_user WHERE user_account = ? LIMIT 1 FOR UPDATE",
    )
    .bind(&user_account)
    .fetch_optional(&mut *transaction)
    .await
    {
        Ok(row) => row,
        Err(error) => {
            let _ = transaction.rollback().await;
            warn!(error = %error, "login user query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let Some(row) = row else {
        let _ = transaction.rollback().await;
        return envelope_response(StatusCode::OK, 500, "系统中不存在该账号", "");
    };
    let verification = password::verify(
        &plaintext,
        row.get::<Option<String>, _>("password").as_deref(),
    );
    if !verification.matches {
        let _ = transaction.rollback().await;
        return envelope_response(StatusCode::OK, 500, "密码错误", "");
    }
    match row.get::<Option<i32>, _>("status") {
        Some(1) => {
            let _ = transaction.rollback().await;
            return envelope_response(StatusCode::OK, 500, "该账号正在审核中", "");
        }
        Some(-1) => {
            let _ = transaction.rollback().await;
            return envelope_response(StatusCode::OK, 500, "该账号已停用", "");
        }
        Some(0) => {}
        _ => {
            let _ = transaction.rollback().await;
            return envelope_response(StatusCode::OK, 500, "数据异常", "");
        }
    }
    let upgraded_password = if verification.needs_upgrade {
        match password::hash(&plaintext) {
            Ok(hash) => Some(hash),
            Err(error) => {
                let _ = transaction.rollback().await;
                warn!(error, "login password upgrade failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        }
    } else {
        None
    };
    let role = match sqlx::query(
        "SELECT r.id, r.title, r.remark, r.is_admin, r.is_default FROM t_role r JOIN t_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ? LIMIT 1",
    )
    .bind(row.get::<String, _>("id"))
    .fetch_optional(&mut *transaction)
    .await
    {
        Ok(Some(role)) => role,
        Ok(None) => {
            let _ = transaction.rollback().await;
            return envelope_response(StatusCode::OK, 500, "数据异常", "");
        }
        Err(error) => {
            let _ = transaction.rollback().await;
            warn!(error = %error, "login role query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let role_id: String = role.get("id");
    let is_admin = role.get::<Option<i32>, _>("is_admin") == Some(0);
    let menus = match load_menu_tree(
        &mut *transaction,
        if is_admin { None } else { Some(&role_id) },
    )
    .await
    {
        Ok(menus) => menus,
        Err(error) => {
            let _ = transaction.rollback().await;
            warn!(error = %error, "login menu query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let token = password::issue_token();
    let token_digest = token_digest(&token);
    let update = if let Some(upgraded_password) = upgraded_password {
        sqlx::query("UPDATE t_user SET password = ?, token = ?, device_id = ? WHERE id = ?")
            .bind(upgraded_password)
            .bind(&token_digest)
            .bind(&device_id)
            .bind(row.get::<String, _>("id"))
            .execute(&mut *transaction)
            .await
    } else {
        sqlx::query("UPDATE t_user SET token = ?, device_id = ? WHERE id = ?")
            .bind(&token_digest)
            .bind(&device_id)
            .bind(row.get::<String, _>("id"))
            .execute(&mut *transaction)
            .await
    };
    if let Err(error) = update {
        let _ = transaction.rollback().await;
        warn!(error = %error, "login session update failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    let data = json!({
        "user": {
            "id": row.get::<String, _>("id"),
            "wkno": row.get::<Option<String>, _>("wkno"),
            "phone": row.get::<Option<String>, _>("phone"),
            "email": row.get::<Option<String>, _>("email"),
            "userAccount": row.get::<Option<String>, _>("user_account"),
            "userImg": row.get::<Option<String>, _>("user_img"),
            "userName": row.get::<Option<String>, _>("user_name"),
            "idCard": row.get::<Option<String>, _>("id_card"),
            "userSex": row.get::<Option<i32>, _>("user_sex"),
            "updateFlag": row.get::<Option<String>, _>("update_flag"),
            "status": row.get::<Option<i32>, _>("status"),
            "bday": row.get::<Option<String>, _>("bday"),
            "eday": row.get::<Option<String>, _>("eday"),
            "dday": row.get::<Option<String>, _>("dday")
        },
        "role": {
            "id": role_id,
            "title": role.get::<Option<String>, _>("title"),
            "remark": role.get::<Option<String>, _>("remark"),
            "isAdmin": role.get::<Option<i32>, _>("is_admin"),
            "isDefault": role.get::<Option<i32>, _>("is_default")
        },
        "menus": menus,
        "token": token,
        "deviceId": device_id
    });
    if let Err(error) = transaction.commit().await {
        warn!(error = %error, "login transaction commit failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, Some(data), "登陆成功", "")
}

async fn load_menu_tree(
    connection: &mut MySqlConnection,
    role_id: Option<&str>,
) -> Result<Vec<Value>, sqlx::Error> {
    let menu_rows = sqlx::query(
        "SELECT id, parent_id, `key`, path, name, icon, icon_f, height, title, is_menu, is_bread, component, sort FROM t_menus ORDER BY sort",
    )
    .fetch_all(&mut *connection)
    .await?;
    let button_rows = sqlx::query("SELECT id, menus_id, title, `key` FROM t_menus_button")
        .fetch_all(&mut *connection)
        .await?;
    let menus: Vec<MenuRow> = menu_rows
        .into_iter()
        .map(|row| MenuRow {
            id: row.get("id"),
            parent_id: row
                .get::<Option<String>, _>("parent_id")
                .unwrap_or_else(|| "-1".into()),
            key: row.get("key"),
            path: row.get("path"),
            name: row.get("name"),
            icon: row.get("icon"),
            icon_f: row.get("icon_f"),
            height: row.get("height"),
            title: row.get("title"),
            is_menu: row.get("is_menu"),
            is_bread: row.get("is_bread"),
            component: row.get("component"),
            sort: row.get("sort"),
        })
        .collect();
    let mut selected = role_id.map(|_| HashSet::<String>::new());
    let mut role_permissions: HashMap<String, HashSet<String>> = HashMap::new();
    if let Some(role_id) = role_id {
        for row in sqlx::query("SELECT menu_id, per FROM t_role_menus WHERE role_id = ?")
            .bind(role_id)
            .fetch_all(&mut *connection)
            .await?
        {
            let menu_id: String = row.get("menu_id");
            selected
                .as_mut()
                .expect("role selection initialized")
                .insert(menu_id.clone());
            let permissions = row
                .get::<Option<String>, _>("per")
                .and_then(|value| serde_json::from_str::<Vec<String>>(&value).ok())
                .unwrap_or_default()
                .into_iter()
                .collect();
            role_permissions.insert(menu_id, permissions);
        }
        let selected = selected.as_mut().expect("role selection initialized");
        let mut changed = true;
        while changed {
            changed = false;
            for menu in &menus {
                if selected.contains(&menu.id)
                    && menu.parent_id != "-1"
                    && selected.insert(menu.parent_id.clone())
                {
                    changed = true;
                }
            }
        }
    }
    let selected_ids = selected.as_ref();
    let mut by_parent: HashMap<String, Vec<MenuRow>> = HashMap::new();
    for menu in menus {
        if selected_ids.is_some_and(|ids| !ids.contains(&menu.id)) {
            continue;
        }
        by_parent
            .entry(menu.parent_id.clone())
            .or_default()
            .push(menu);
    }
    let mut permissions: HashMap<String, Vec<Value>> = HashMap::new();
    for row in button_rows {
        let menu_id: String = row.get("menus_id");
        let button_key = row.get::<Option<String>, _>("key");
        let allowed = match role_id {
            None => true,
            Some(_) => button_key.as_ref().is_some_and(|key| {
                role_permissions
                    .get(&menu_id)
                    .is_some_and(|keys| keys.contains(key))
            }),
        };
        if allowed {
            permissions.entry(menu_id.clone()).or_default().push(json!({
                "id": row.get::<String, _>("id"),
                "menusId": menu_id,
                "title": row.get::<Option<String>, _>("title"),
                "key": button_key
            }));
        }
    }
    Ok(menu_tree("-1", &by_parent, &permissions))
}

async fn user_get_by_token(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let row = sqlx::query(
        "SELECT id, wkno, phone, email, user_account, user_img, user_name, id_card, user_sex, update_flag, status, bday, eday, dday FROM t_user WHERE id = ? LIMIT 1",
    )
    .bind(&user.id)
    .fetch_optional(pool)
    .await;
    match row {
        Ok(Some(row)) => {
            let data = json!({
                "id": row.get::<String, _>("id"),
                "wkno": row.get::<Option<String>, _>("wkno"),
                "phone": row.get::<Option<String>, _>("phone"),
                "email": row.get::<Option<String>, _>("email"),
                "userAccount": row.get::<Option<String>, _>("user_account"),
                "userImg": row.get::<Option<String>, _>("user_img"),
                "userName": row.get::<Option<String>, _>("user_name"),
                "idCard": row.get::<Option<String>, _>("id_card"),
                "userSex": row.get::<Option<i32>, _>("user_sex"),
                "updateFlag": row.get::<Option<String>, _>("update_flag"),
                "status": row.get::<Option<i32>, _>("status"),
                "bday": row.get::<Option<String>, _>("bday"),
                "eday": row.get::<Option<String>, _>("eday"),
                "dday": row.get::<Option<String>, _>("dday")
            });
            envelope_json(StatusCode::OK, 200, Some(data), "ok", "")
        }
        Ok(None) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
        Err(error) => {
            warn!(error = %error, "user profile query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}
fn menu_tree(
    parent_id: &str,
    by_parent: &HashMap<String, Vec<MenuRow>>,
    permissions: &HashMap<String, Vec<Value>>,
) -> Vec<Value> {
    let mut rows = by_parent.get(parent_id).cloned().unwrap_or_default();
    rows.sort_by_key(|row| row.sort.unwrap_or(0));
    rows.into_iter()
        .map(|row| {
            let children = menu_tree(&row.id, by_parent, permissions);
            let row_permissions = permissions.get(&row.id).cloned().unwrap_or_default();
            json!({
                "id": row.id,
                "parentId": row.parent_id,
                "key": row.key,
                "path": row.path,
                "name": row.name,
                "meta": {
                    "icon": row.icon,
                    "iconF": row.icon_f,
                    "height": row.height,
                    "title": row.title,
                    "isMenu": row.is_menu == Some(0),
                    "isBread": row.is_bread == Some(0)
                },
                "component": row.component,
                "sort": row.sort.unwrap_or(0),
                "permissions": row_permissions,
                "children": children
            })
        })
        .collect()
}

async fn menu_get_all(State(state): State<AppState>, _user: Extension<AuthUser>) -> Response {
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let menu_rows = sqlx::query(
        "SELECT id, parent_id, `key`, path, name, icon, icon_f, height, title, is_menu, is_bread, component, sort FROM t_menus ORDER BY sort",
    )
    .fetch_all(pool)
    .await;
    let button_rows = sqlx::query("SELECT id, menus_id, title, `key` FROM t_menus_button")
        .fetch_all(pool)
        .await;
    match (menu_rows, button_rows) {
        (Ok(menu_rows), Ok(button_rows)) => {
            let mut by_parent: HashMap<String, Vec<MenuRow>> = HashMap::new();
            for row in menu_rows {
                let menu = MenuRow {
                    id: row.get("id"),
                    parent_id: row
                        .get::<Option<String>, _>("parent_id")
                        .unwrap_or_else(|| "-1".into()),
                    key: row.get("key"),
                    path: row.get("path"),
                    name: row.get("name"),
                    icon: row.get("icon"),
                    icon_f: row.get("icon_f"),
                    height: row.get("height"),
                    title: row.get("title"),
                    is_menu: row.get("is_menu"),
                    is_bread: row.get("is_bread"),
                    component: row.get("component"),
                    sort: row.get("sort"),
                };
                by_parent
                    .entry(menu.parent_id.clone())
                    .or_default()
                    .push(menu);
            }
            let mut permissions: HashMap<String, Vec<Value>> = HashMap::new();
            for row in button_rows {
                let menus_id: String = row.get("menus_id");
                permissions
                    .entry(menus_id.clone())
                    .or_default()
                    .push(json!({
                        "id": row.get::<String, _>("id"),
                        "menusId": menus_id,
                        "title": row.get::<Option<String>, _>("title"),
                        "key": row.get::<Option<String>, _>("key")
                    }));
            }
            envelope_json(
                StatusCode::OK,
                200,
                Some(json!(menu_tree("-1", &by_parent, &permissions))),
                "ok",
                "",
            )
        }
        (Err(error), _) | (_, Err(error)) => {
            warn!(error = %error, "menu tree query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

async fn role_get_all(State(state): State<AppState>, _user: Extension<AuthUser>) -> Response {
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let roles =
        match sqlx::query("SELECT id, title, remark, is_admin, is_default FROM t_role ORDER BY id")
            .fetch_all(pool)
            .await
        {
            Ok(rows) => rows,
            Err(error) => {
                warn!(error = %error, "role query failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
    let mut result = Vec::with_capacity(roles.len());
    for role in roles {
        let role_id: String = role.get("id");
        let menus = match sqlx::query(
            "SELECT m.id, m.parent_id, m.`key`, m.path, m.name, m.icon, m.icon_f, m.height, m.title, m.is_menu, m.is_bread, m.component, m.sort, rm.per FROM t_menus m JOIN t_role_menus rm ON rm.menu_id = m.id WHERE rm.role_id = ? ORDER BY m.sort",
        )
        .bind(&role_id)
        .fetch_all(pool)
        .await
        {
            Ok(rows) => rows
                .into_iter()
                .map(|menu| {
                    json!({
                        "id": menu.get::<String, _>("id"),
                        "parentId": menu.get::<Option<String>, _>("parent_id"),
                        "key": menu.get::<Option<String>, _>("key"),
                        "path": menu.get::<Option<String>, _>("path"),
                        "name": menu.get::<Option<String>, _>("name"),
                        "icon": menu.get::<Option<String>, _>("icon"),
                        "iconF": menu.get::<Option<String>, _>("icon_f"),
                        "height": menu.get::<Option<String>, _>("height"),
                        "title": menu.get::<Option<String>, _>("title"),
                        "isMenu": menu.get::<Option<i32>, _>("is_menu"),
                        "isBread": menu.get::<Option<i32>, _>("is_bread"),
                        "component": menu.get::<Option<String>, _>("component"),
                        "sort": menu.get::<Option<i32>, _>("sort"),
                        "per": menu.get::<Option<String>, _>("per")
                    })
                })
                .collect::<Vec<_>>(),
            Err(error) => {
                warn!(error = %error, role_id = %role_id, "role menu query failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
        result.push(json!({
            "role": {
                "id": role_id,
                "title": role.get::<Option<String>, _>("title"),
                "remark": role.get::<Option<String>, _>("remark"),
                "isAdmin": role.get::<Option<i32>, _>("is_admin"),
                "isDefault": role.get::<Option<i32>, _>("is_default")
            },
            "menus": menus
        }));
    }
    envelope_json(StatusCode::OK, 200, Some(json!(result)), "ok", "")
}

fn valid_account(value: &str) -> bool {
    let length = value.chars().count();
    (4..=32).contains(&length)
        && value.chars().all(|character| {
            character.is_ascii_alphanumeric() || (('\u{4300}'..='\u{9fa5}').contains(&character))
        })
}

fn registration_birthday(id_card: &str) -> Option<String> {
    match id_card.chars().count() {
        18 => Some(format!(
            "{}-{}-{}",
            id_card.get(6..10)?,
            id_card.get(10..12)?,
            id_card.get(12..14)?
        )),
        15 => Some(format!(
            "19{}-{}-{}",
            id_card.get(6..8)?,
            id_card.get(8..10)?,
            id_card.get(10..12)?
        )),
        _ => None,
    }
}

fn twelve_month_data(year: i32, month: u8) -> Vec<Value> {
    let current_index = year * 12 + i32::from(month) - 1;
    (0..12)
        .map(|offset| {
            let index = current_index - offset;
            json!({
                "year": index.div_euclid(12),
                "month": index.rem_euclid(12) + 1,
            })
        })
        .collect()
}

fn now_time_millis() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("system clock must be after Unix epoch")
        .as_millis() as u64
}

async fn tools_get_now_time() -> Response {
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!(now_time_millis())),
        "ok",
        "",
    )
}

async fn tools_get_twelve_month() -> Response {
    let current = OffsetDateTime::now_utc();
    let data = twelve_month_data(current.year(), u8::from(current.month()));
    envelope_json(StatusCode::OK, 200, Some(json!(data)), "ok", "")
}

async fn openapi() -> Json<Value> {
    Json(json!({
        "openapi": "3.1.0",
        "info": {"title": "Rust backend Phase 1/2 infrastructure", "version": "0.1.0"},
        "paths": {
            "/api/phase1/contract/auth": {"get": {"responses": {"200": {"description": "Response envelope"}}}},
            "/api/phase1/contract/admin": {"get": {"responses": {"200": {"description": "Response envelope"}}}},
            "/api/tools/getNowTime": {"get": {"responses": {"200": {"description": "Response envelope; enabled only by RUST_PHASE2_TOOLS_ENABLED"}}}},
            "/api/tools/getTwelvemonth": {"get": {"responses": {"200": {"description": "Response envelope; enabled only by RUST_PHASE2_TOOLS_ENABLED"}}}},
            "/api/cable/find": {"post": {"responses": {"200": {"description": "Cable list envelope; enabled only by RUST_PHASE4_CABLE_ENABLED"}}}},
            "/api/cable/findById": {"post": {"responses": {"200": {"description": "Cable envelope; enabled only by RUST_PHASE4_CABLE_ENABLED"}}}},
            "/api/cable/save": {"post": {"responses": {"200": {"description": "Cable envelope; admin only"}}}},
            "/api/cable/delete": {"post": {"responses": {"200": {"description": "Boolean envelope; admin only"}}}},
            "/api/cable/type/find": {"post": {"responses": {"200": {"description": "Cable type list envelope; enabled only by RUST_PHASE4_CABLE_ENABLED"}}}},
            "/api/cable/type/save": {"post": {"responses": {"200": {"description": "Cable type list envelope; admin only"}}}},
            "/api/cable/type/delete": {"post": {"responses": {"200": {"description": "Boolean envelope; admin only"}}}},
            "/api/cable/floor/find": {"post": {"responses": {"200": {"description": "Cable floor envelope; enabled only by RUST_PHASE4_CABLE_ENABLED"}}}},
            "/api/gradingRule/getGradingRuleListByType": {"post": {"responses": {"200": {"description": "Grading rule list envelope; enabled only by RUST_PHASE4_GRADING_ENABLED"}}}},
            "/api/gradingRule/getGradingRuleById": {"post": {"responses": {"200": {"description": "Grading rule envelope; enabled only by RUST_PHASE4_GRADING_ENABLED"}}}},
            "/api/gradingRule/saveGradingRule": {"post": {"responses": {"200": {"description": "Grading rule envelope; admin only"}}}},
            "/api/gradingRule/updateGradingRuleStatus": {"post": {"responses": {"200": {"description": "Grading rule envelope; admin only"}}}},
            "/api/gradingRule/changeGradingRuleIsDefault": {"post": {"responses": {"200": {"description": "Envelope; admin only"}}}},
            "/api/gradingRule/deleteGradingRule": {"post": {"responses": {"200": {"description": "Envelope; admin only"}}}},
            "/api/device/listPage": {"post": {"responses": {"200": {"description": "Device list envelope; enabled only by RUST_PHASE4_DEVICE_ENABLED"}}}},
            "/api/deviceType/findAll": {"post": {"responses": {"200": {"description": "Device type list envelope; enabled only by RUST_PHASE4_DEVICE_ENABLED"}}}},
            "/api/deviceType/save": {"post": {"responses": {"200": {"description": "Device type envelope; admin only"}}}},
            "/api/deviceType/delete": {"post": {"responses": {"200": {"description": "Envelope; admin only"}}}},
            "/api/device/save": {"post": {"responses": {"200": {"description": "Device envelope; admin only"}}}},
            "/api/device/delete": {"post": {"responses": {"200": {"description": "Envelope; admin only"}}}},
            "/api/device/addDeviceDescription": {"post": {"responses": {"200": {"description": "Device description envelope; admin only"}}}},
            "/api/equipmentDevice/listPage": {"post": {"responses": {"200": {"description": "Equipment device list envelope; enabled only by RUST_PHASE4_EQUIPMENT_ENABLED"}}}},
            "/api/equipmentDevice/listKeyPoints": {"post": {"responses": {"200": {"description": "Equipment key-points list envelope; enabled only by RUST_PHASE4_EQUIPMENT_ENABLED"}}}},
            "/api/equipmentDevice/add": {"post": {"responses": {"200": {"description": "Envelope; admin only"}}}},
            "/api/equipmentDevice/update": {"post": {"responses": {"200": {"description": "Envelope; admin only"}}}},
            "/api/equipmentDevice/delete": {"post": {"responses": {"200": {"description": "Envelope; admin only"}}}},
            "/api/equipmentDevice/addKeyPoints": {"post": {"responses": {"200": {"description": "Envelope; admin only"}}}},
            "/api/equipmentTrain/listPage": {"post": {"responses": {"200": {"description": "Equipment train list envelope; enabled only by RUST_PHASE4_EQUIPMENT_ENABLED"}}}},
            "/api/equipmentTrain/detail": {"post": {"responses": {"200": {"description": "Equipment train envelope; owner only"}}}},
            "/api/equipmentTrain/add": {"post": {"responses": {"200": {"description": "Equipment train envelope; owner-scoped write"}}}},
            "/api/theoryKnowledge/listPageClassify": {"post": {"responses": {"200": {"description": "Theory classification envelope; enabled only by RUST_PHASE5_THEORY_ENABLED"}}}},
            "/api/theoryKnowledge/getBasicTheory": {"post": {"responses": {"200": {"description": "Theory knowledge list envelope; enabled only by RUST_PHASE5_THEORY_ENABLED"}}}},
            "/api/theoryKnowledge/getById": {"post": {"responses": {"200": {"description": "Theory knowledge detail envelope; enabled only by RUST_PHASE5_THEORY_ENABLED"}}}},
            "/api/theoryKnowledge/getByIdAndToken": {"post": {"responses": {"200": {"description": "Theory knowledge detail with user progress; enabled only by RUST_PHASE5_THEORY_ENABLED"}}}},
            "/api/theoryKnowledgeTest/getTestBySwfId": {"post": {"responses": {"200": {"description": "Theory test list envelope; enabled only by RUST_PHASE5_THEORY_ENABLED"}}}},
            "/api/theoryKnowledgeTest/getByKnowledgeSwfIdAndEnable": {"post": {"responses": {"200": {"description": "Enabled test content envelope; enabled only by RUST_PHASE5_THEORY_ENABLED"}}}},
            "/api/theoryKnowledgeTest/getTestContentByUserIdAndKnowledgeId": {"post": {"responses": {"200": {"description": "User test result list; token-scoped"}}}},
            "/api/theoryKnowledgeTest/getTestContentByUserIdAndKnowledgeSwfId": {"post": {"responses": {"200": {"description": "User test result; token-scoped"}}}},
            "/api/theoryKnowledgeTest/getTestContentById": {"post": {"responses": {"200": {"description": "User test result; token-scoped"}}}},
            "/api/theoryKnowledgeTestPaper/findAllTestPaper": {"post": {"responses": {"200": {"description": "Test paper list; enabled only by RUST_PHASE5_THEORY_ENABLED"}}}},
            "/api/theoryKnowledgeTestPaper/findTestPaperById": {"post": {"responses": {"200": {"description": "Test paper detail; enabled only by RUST_PHASE5_THEORY_ENABLED"}}}},
            "/api/theoryKnowledgeTestPaper/findTestPaperByLevelIdAndName": {"post": {"responses": {"200": {"description": "Filtered test paper list; enabled only by RUST_PHASE5_THEORY_ENABLED"}}}},
            "/api/theoryKnowledgeTestPaper/saveTestPaper": {"post": {"responses": {"200": {"description": "Envelope; admin only"}}}},
            "/api/theoryKnowledgeTestPaper/deleteTestPaper": {"post": {"responses": {"200": {"description": "Envelope; admin only"}}}},
            "/api/theoryKnowledgeExam/findTheoryKnowledgeExamById": {"post": {"responses": {"200": {"description": "Exam detail envelope; owner/controller-scoped"}}}},
            "/api/theoryKnowledgeExam/saveTheoryKnowledgeExamSelfTesting": {"post": {"responses": {"200": {"description": "Self-test creation envelope; token-scoped"}}}},
            "/api/theoryKnowledgeExam/finishSelfTesting": {"post": {"responses": {"200": {"description": "Self-test scored result envelope; token-scoped"}}}},
            "/api/theoryKnowledgeExam/teacherStartTheoryKnowledgeExam": {"post": {"responses": {"200": {"description": "Exam state envelope; admin/controller-scoped"}}}},
            "/api/theoryKnowledgeExam/studentChangeExamState": {"post": {"responses": {"200": {"description": "Student exam state envelope; owner-scoped"}}}},
            "/api/theoryKnowledgeExam/studentSaveExamRealtimeContont": {"post": {"responses": {"200": {"description": "Realtime answer envelope; owner-scoped"}}}},
            "/api/theoryKnowledgeExamUser/teacherUploadScore": {"post": {"responses": {"200": {"description": "Envelope; admin only"}}}},
            "/api/theoryKnowledgeQuestion/findAllTheoryKnowledgeQuestionLevel": {"post": {"responses": {"200": {"description": "Question levels envelope; enabled only by RUST_PHASE5_THEORY_ENABLED"}}}},
            "/api/theoryKnowledgeQuestion/findAllQuestionByLevelId": {"post": {"responses": {"200": {"description": "Question list envelope; enabled only by RUST_PHASE5_THEORY_ENABLED"}}}},
            "/api/theoryKnowledgeQuestion/exportTemplate": {"post": {"responses": {"200": {"description": "Question template columns; enabled only by RUST_PHASE5_THEORY_ENABLED"}}}},
            "/api/theoryKnowledgeQuestion/saveBatch": {"post": {"responses": {"200": {"description": "Question list envelope; admin only"}}}},
            "/api/theoryKnowledgeQuestion/deleteTheoryKnowledgeQuestion": {"post": {"responses": {"200": {"description": "Envelope; admin only"}}}},
            "/api/user/login": {"post": {"responses": {"200": {"description": "LoginSessionDto envelope; enabled only by RUST_PHASE3_AUTH_ENABLED"}}}},
            "/api/user/signin": {"post": {"responses": {"200": {"description": "UserSummary envelope; enabled only by RUST_PHASE3_AUTH_ENABLED"}}}},
            "/api/user/getUsersByToken": {"post": {"responses": {"200": {"description": "UserProfile envelope; enabled only by RUST_PHASE3_AUTH_ENABLED"}}}},
            "/api/user/userOut": {"post": {"responses": {"200": {"description": "Boolean envelope; enabled only by RUST_PHASE3_AUTH_ENABLED"}}}},
            "/api/user/changePassword": {"post": {"responses": {"200": {"description": "Boolean envelope; enabled only by RUST_PHASE3_AUTH_ENABLED"}}}},
            "/api/user/verifyPassword": {"post": {"responses": {"200": {"description": "Boolean envelope; enabled only by RUST_PHASE3_AUTH_ENABLED"}}}},
            "/api/user/saveUser": {"post": {"responses": {"200": {"description": "UserProfile envelope; admin only"}}}},
            "/api/user/importUser": {"post": {"responses": {"200": {"description": "UserProfile list envelope; admin only"}}}},
            "/api/user/addUserRole": {"post": {"responses": {"200": {"description": "Message envelope; admin only"}}}},
            "/api/user/delete": {"get": {"responses": {"200": {"description": "Boolean envelope; admin only"}}}},
            "/api/user/resetPassword": {"get": {"responses": {"200": {"description": "Temporary password envelope; admin only"}}}},
            "/api/menus/getMenusAll": {"get": {"responses": {"200": {"description": "Menus tree envelope; enabled only by RUST_PHASE3_AUTH_ENABLED"}}}},
            "/api/menus/addMenu": {"post": {"responses": {"200": {"description": "Menu envelope; admin only"}}}},
            "/api/role/getRoleAll": {"post": {"responses": {"200": {"description": "RoleInfoDto list envelope; enabled only by RUST_PHASE3_AUTH_ENABLED"}}}},
            "/api/role/addRole": {"post": {"responses": {"200": {"description": "Role envelope; admin only"}}}}
        }
    }))
}

fn envelope_response(status: StatusCode, code: i32, message: &str, description: &str) -> Response {
    envelope_json(status, code, None, message, description)
}

fn envelope_json(
    status: StatusCode,
    code: i32,
    data: Option<Value>,
    message: &str,
    description: &str,
) -> Response {
    (
        status,
        Json(Envelope {
            code,
            data,
            message: message.to_owned(),
            description: description.to_owned(),
        }),
    )
        .into_response()
}

fn parse_bool(name: &str, default: bool) -> Result<bool, AppError> {
    match env::var(name) {
        Ok(value) if value.eq_ignore_ascii_case("true") => Ok(true),
        Ok(value) if value.eq_ignore_ascii_case("false") => Ok(false),
        Ok(_) => Err(AppError::Config(format!("{name} must be true or false"))),
        Err(_) => Ok(default),
    }
}
fn header_text<'a>(request: &'a Request, name: &str) -> Option<&'a str> {
    request
        .headers()
        .get(name)
        .and_then(|value| value.to_str().ok())
        .filter(|value| !value.is_empty())
}

fn token_digest(token: &str) -> String {
    let mut digest = Sha256::new();
    digest.update(token.as_bytes());
    format!("{:x}", digest.finalize())
}

fn parse_socket_addr(name: &str, default: &str) -> Result<SocketAddr, AppError> {
    let value = env::var(name).unwrap_or_else(|_| default.to_owned());
    value
        .parse()
        .map_err(|error| AppError::Config(format!("invalid {name} value {value:?}: {error}")))
}

fn parse_u32(name: &str, default: u32) -> Result<u32, AppError> {
    env::var(name).map_or(Ok(default), |value| {
        value
            .parse()
            .map_err(|_| AppError::Config(format!("{name} must be an integer")))
    })
}

fn parse_u64(name: &str, default: u64) -> Result<u64, AppError> {
    env::var(name).map_or(Ok(default), |value| {
        value
            .parse()
            .map_err(|_| AppError::Config(format!("{name} must be an integer")))
    })
}

fn parse_usize(name: &str, default: usize) -> Result<usize, AppError> {
    env::var(name).map_or(Ok(default), |value| {
        value
            .parse()
            .map_err(|_| AppError::Config(format!("{name} must be an integer")))
    })
}

fn init_tracing() {
    let filter = tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| {
        tracing_subscriber::EnvFilter::new("rust_backend=info,tower_http=info")
    });
    tracing_subscriber::fmt()
        .with_env_filter(filter)
        .compact()
        .init();
}

async fn shutdown_signal() {
    let ctrl_c = async {
        signal::ctrl_c()
            .await
            .expect("failed to install Ctrl+C handler");
    };
    #[cfg(unix)]
    let terminate = async {
        let mut signal = signal::unix::signal(signal::unix::SignalKind::terminate())
            .expect("failed to install SIGTERM handler");
        signal.recv().await;
    };
    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();
    tokio::select! { _ = ctrl_c => {}, _ = terminate => {} }
    info!("shutdown signal received");
}
#[cfg(test)]
mod tests {
    use super::*;
    use axum::{body::Body, middleware, routing::get, Router};
    use http_body_util::BodyExt;
    use tower::ServiceExt;

    #[tokio::test]
    async fn admin_middleware_returns_207_for_non_admin_and_200_for_admin() {
        let app = Router::new()
            .route("/", get(admin_probe))
            .layer(middleware::from_fn(require_admin));

        let ordinary_request = Request::builder()
            .uri("/")
            .extension(AuthUser {
                id: "ordinary".into(),
                is_admin: false,
            })
            .body(Body::empty())
            .expect("ordinary request");
        let ordinary_response = app
            .clone()
            .oneshot(ordinary_request)
            .await
            .expect("ordinary response");
        assert_eq!(ordinary_response.status(), StatusCode::OK);
        let ordinary_body = ordinary_response
            .into_body()
            .collect()
            .await
            .expect("ordinary body")
            .to_bytes();
        let ordinary_json: Value =
            serde_json::from_slice(&ordinary_body).expect("ordinary envelope JSON");
        assert_eq!(ordinary_json["code"], 207);

        let admin_request = Request::builder()
            .uri("/")
            .extension(AuthUser {
                id: "admin".into(),
                is_admin: true,
            })
            .body(Body::empty())
            .expect("admin request");
        let admin_response = app.oneshot(admin_request).await.expect("admin response");
        assert_eq!(admin_response.status(), StatusCode::OK);
        let admin_body = admin_response
            .into_body()
            .collect()
            .await
            .expect("admin body")
            .to_bytes();
        let admin_json: Value = serde_json::from_slice(&admin_body).expect("admin envelope JSON");
        assert_eq!(admin_json["code"], 200);
    }

    #[test]
    fn token_digest_matches_java_sha256_contract() {
        assert_eq!(
            token_digest("password"),
            "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"
        );
    }

    #[test]
    fn twelve_month_data_matches_java_descending_year_month_order() {
        let data = twelve_month_data(2026, 1);
        assert_eq!(data.first().expect("first month")["year"], 2026);
        assert_eq!(data.first().expect("first month")["month"], 1);
        assert_eq!(data.last().expect("last month")["year"], 2025);
        assert_eq!(data.last().expect("last month")["month"], 2);
        assert_eq!(data.len(), 12);
    }

    #[test]
    fn missing_response_data_serializes_as_json_null() {
        let body = serde_json::to_value(Envelope::<Value> {
            code: 203,
            data: None,
            message: "token不能为空".into(),
            description: String::new(),
        })
        .expect("envelope must serialize");
        assert_eq!(body["data"], Value::Null);
        assert_eq!(body["code"], 203);
    }
}
