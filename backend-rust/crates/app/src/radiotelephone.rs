use crate::platform::{
    auth::AuthUser,
    envelope::{envelope_json, envelope_response},
    state::AppState,
};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, MySql, Row, Transaction};

#[derive(Debug, Deserialize)]
pub(crate) struct LifecycleRequest {
    #[serde(rename = "type")]
    train_type: Option<i32>,
    #[serde(rename = "sessionId")]
    session_id: Option<String>,
}

fn database_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error = %error, operation, "radiotelephone database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

fn view(row: &MySqlRow, session_id: Option<&str>) -> Value {
    json!({
        "userId": row.get::<Option<String>, _>("user_id"),
        "type": row.get::<Option<i32>, _>("type"),
        "totalTime": row.get::<Option<String>, _>("total_time"),
        "totalCount": row.get::<Option<i32>, _>("total_count"),
        "sessionId": session_id
    })
}

fn require_type(body: &LifecycleRequest) -> Result<i32, Response> {
    body.train_type
        .ok_or_else(|| envelope_response(StatusCode::OK, 202, "话报训练参数无效", ""))
}

fn require_session(body: &LifecycleRequest) -> Result<&str, Response> {
    body.session_id
        .as_deref()
        .map(str::trim)
        .filter(|session_id| !session_id.is_empty())
        .ok_or_else(|| envelope_response(StatusCode::OK, 202, "话报训练缺少训练会话", ""))
}

fn parse_total_time(value: Option<String>) -> Result<i64, Response> {
    let Some(value) = value.filter(|value| !value.trim().is_empty()) else {
        return Ok(0);
    };
    value
        .trim()
        .parse::<i32>()
        .map(|seconds| i64::from(seconds.max(0)))
        .map_err(|_| envelope_response(StatusCode::OK, 208, "历史累计时长损坏，不能继续结算", ""))
}

async fn lock_user(
    transaction: &mut Transaction<'_, MySql>,
    user_id: &str,
) -> Result<bool, sqlx::Error> {
    sqlx::query("SELECT id FROM t_user WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(user_id)
        .fetch_optional(&mut **transaction)
        .await
        .map(|row| row.is_some())
}

async fn load_row(
    transaction: &mut Transaction<'_, MySql>,
    user_id: &str,
    train_type: i32,
) -> Result<Option<MySqlRow>, sqlx::Error> {
    sqlx::query("SELECT id,user_id,type,total_time,total_count,active_session_id,IF(session_started_at IS NULL,0,1) AS session_running,active_millis,finalized_session_id FROM t_radiotelephone_train WHERE user_id=? AND type=? LIMIT 1 FOR UPDATE")
        .bind(user_id)
        .bind(train_type)
        .fetch_optional(&mut **transaction)
        .await
}

async fn foreign_session(
    transaction: &mut Transaction<'_, MySql>,
    session_id: &str,
    user_id: &str,
) -> Result<bool, sqlx::Error> {
    sqlx::query("SELECT user_id FROM t_radiotelephone_train WHERE active_session_id=? LIMIT 1")
        .bind(session_id)
        .fetch_optional(&mut **transaction)
        .await
        .map(|row| {
            row.and_then(|row| row.get::<Option<String>, _>("user_id"))
                .is_some_and(|owner| owner != user_id)
        })
}

async fn session_error(
    transaction: &mut Transaction<'_, MySql>,
    session_id: &str,
    user_id: &str,
) -> Response {
    match foreign_session(transaction, session_id, user_id).await {
        Ok(true) => envelope_response(StatusCode::OK, 207, "话报训练会话不属于当前用户", ""),
        Ok(false) => envelope_response(StatusCode::OK, 208, "训练会话已失效", ""),
        Err(error) => database_error(error, "check foreign session"),
    }
}

pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    raw: axum::body::Bytes,
) -> Response {
    // Java：空体或 JSON null 与“无过滤”等价（200）；无 Content-Type 的无体请求 Java 为 415，Rust 保留 200（已登记）。
    let body = if raw.iter().all(u8::is_ascii_whitespace) || raw.trim_ascii() == b"null" {
        None
    } else {
        match serde_json::from_slice::<LifecycleRequest>(&raw) {
            Ok(body) => Some(Json(body)),
            Err(error) => {
                return axum::response::IntoResponse::into_response((
                    StatusCode::BAD_REQUEST,
                    format!("Failed to parse the request body as JSON: {error}"),
                ))
            }
        }
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start list transaction"),
    };
    // 只有带 type 的请求会初始化汇总行（写路径），才先按主键锁用户行；纯读取不加锁。
    if let Some(Json(body)) = body {
        match lock_user(&mut transaction, &user.id).await {
            Ok(true) => {}
            Ok(false) => return envelope_response(StatusCode::OK, 202, "用户不存在", ""),
            Err(error) => return database_error(error, "lock user for list"),
        }
        let train_type = match require_type(&body) {
            Ok(train_type) => train_type,
            Err(response) => return response,
        };
        match load_row(&mut transaction, &user.id, train_type).await {
            Ok(Some(_)) => {}
            Ok(None) => {
                if let Err(error) = sqlx::query("INSERT INTO t_radiotelephone_train (id,user_id,type,total_time,total_count) VALUES (?,?,?,'0',0)")
                    .bind(uuid::Uuid::new_v4().to_string())
                    .bind(&user.id)
                    .bind(train_type)
                    .execute(&mut *transaction)
                    .await
                {
                    return database_error(error, "initialize list aggregate");
                }
            }
            Err(error) => return database_error(error, "load aggregate for list"),
        }
    }
    let rows = match sqlx::query("SELECT user_id,type,total_time,total_count,active_session_id FROM t_radiotelephone_train WHERE user_id=? ORDER BY type")
        .bind(&user.id)
        .fetch_all(&mut *transaction)
        .await
    {
        Ok(rows) => rows,
        Err(error) => return database_error(error, "list training totals"),
    };
    let data = Value::Array(
        rows.iter()
            .map(|row| {
                let session_id = row.get::<Option<String>, _>("active_session_id");
                view(row, session_id.as_deref())
            })
            .collect(),
    );
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit list transaction");
    }
    envelope_json(StatusCode::OK, 200, Some(data), "ok", "")
}

pub(crate) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<LifecycleRequest>,
) -> Response {
    let train_type = match require_type(&body) {
        Ok(train_type) => train_type,
        Err(response) => return response,
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start begin transaction"),
    };
    match lock_user(&mut transaction, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 202, "用户不存在", ""),
        Err(error) => return database_error(error, "lock user for begin"),
    }
    let row = match load_row(&mut transaction, &user.id, train_type).await {
        Ok(row) => row,
        Err(error) => return database_error(error, "load training for begin"),
    };
    if let Some(row) = row {
        if row.get::<Option<String>, _>("active_session_id").is_none() {
            let session_id = uuid::Uuid::new_v4().to_string();
            if let Err(error) = sqlx::query("UPDATE t_radiotelephone_train SET active_session_id=?,session_started_at=NOW(6),active_millis=COALESCE(active_millis,0) WHERE id=?")
                .bind(&session_id)
                .bind(row.get::<String, _>("id"))
                .execute(&mut *transaction)
                .await
            {
                return database_error(error, "activate existing training");
            }
        }
    } else {
        let id = uuid::Uuid::new_v4().to_string();
        let session_id = uuid::Uuid::new_v4().to_string();
        if let Err(error) = sqlx::query("INSERT INTO t_radiotelephone_train (id,user_id,type,total_time,total_count,active_session_id,session_started_at,active_millis) VALUES (?,?,?,'0',0,?,NOW(6),0)")
            .bind(id)
            .bind(&user.id)
            .bind(train_type)
            .bind(session_id)
            .execute(&mut *transaction)
            .await
        {
            return database_error(error, "create training aggregate");
        }
    }
    let row = match load_row(&mut transaction, &user.id, train_type).await {
        Ok(Some(row)) => row,
        Ok(None) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
        Err(error) => return database_error(error, "reload training after begin"),
    };
    let session_id = row.get::<Option<String>, _>("active_session_id");
    let data = view(&row, session_id.as_deref());
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit begin transaction");
    }
    envelope_json(StatusCode::OK, 200, Some(data), "ok", "")
}

pub(crate) async fn pause(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<LifecycleRequest>,
) -> Response {
    transition(state, user, body, Transition::Pause).await
}

pub(crate) async fn resume(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<LifecycleRequest>,
) -> Response {
    transition(state, user, body, Transition::Resume).await
}

enum Transition {
    Pause,
    Resume,
}

async fn transition(
    state: AppState,
    user: AuthUser,
    body: LifecycleRequest,
    transition: Transition,
) -> Response {
    let train_type = match require_type(&body) {
        Ok(train_type) => train_type,
        Err(response) => return response,
    };
    let session_id = match require_session(&body) {
        Ok(session_id) => session_id.to_owned(),
        Err(response) => return response,
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start transition transaction"),
    };
    match lock_user(&mut transaction, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 202, "用户不存在", ""),
        Err(error) => return database_error(error, "lock user for transition"),
    }
    let row = match load_row(&mut transaction, &user.id, train_type).await {
        Ok(Some(row)) => row,
        Ok(None) => return session_error(&mut transaction, &session_id, &user.id).await,
        Err(error) => return database_error(error, "load training for transition"),
    };
    if row.get::<Option<String>, _>("active_session_id").as_deref() != Some(session_id.as_str()) {
        return session_error(&mut transaction, &session_id, &user.id).await;
    }
    let id = row.get::<String, _>("id");
    let result = match transition {
        Transition::Pause if row.get::<i32, _>("session_running") != 0 => {
            sqlx::query("UPDATE t_radiotelephone_train SET active_millis=LEAST(9223372036854775807,GREATEST(0,COALESCE(active_millis,0))+GREATEST(0,TIMESTAMPDIFF(MICROSECOND,session_started_at,NOW(6)) DIV 1000)),session_started_at=NULL WHERE id=?")
                .bind(&id)
                .execute(&mut *transaction)
                .await
        }
        Transition::Resume if row.get::<i32, _>("session_running") == 0 => {
            sqlx::query("UPDATE t_radiotelephone_train SET session_started_at=NOW(6) WHERE id=?")
                .bind(&id)
                .execute(&mut *transaction)
                .await
        }
        _ => Ok(Default::default()),
    };
    if let Err(error) = result {
        return database_error(error, "persist pause or resume");
    }
    let row = match load_row(&mut transaction, &user.id, train_type).await {
        Ok(Some(row)) => row,
        Ok(None) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
        Err(error) => return database_error(error, "reload transition result"),
    };
    let data = view(&row, Some(&session_id));
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit transition transaction");
    }
    envelope_json(StatusCode::OK, 200, Some(data), "ok", "")
}

pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<LifecycleRequest>,
) -> Response {
    let train_type = match require_type(&body) {
        Ok(train_type) => train_type,
        Err(response) => return response,
    };
    let session_id = match require_session(&body) {
        Ok(session_id) => session_id.to_owned(),
        Err(response) => return response,
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start finish transaction"),
    };
    match lock_user(&mut transaction, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 202, "用户不存在", ""),
        Err(error) => return database_error(error, "lock user for finish"),
    }
    let row = match load_row(&mut transaction, &user.id, train_type).await {
        Ok(Some(row)) => row,
        Ok(None) => return session_error(&mut transaction, &session_id, &user.id).await,
        Err(error) => return database_error(error, "load training for finish"),
    };
    if row
        .get::<Option<String>, _>("finalized_session_id")
        .as_deref()
        == Some(session_id.as_str())
    {
        let data = view(&row, Some(&session_id));
        if let Err(error) = transaction.commit().await {
            return database_error(error, "commit idempotent finish");
        }
        return envelope_json(StatusCode::OK, 200, Some(data), "ok", "");
    }
    if row.get::<Option<String>, _>("active_session_id").as_deref() != Some(session_id.as_str()) {
        return session_error(&mut transaction, &session_id, &user.id).await;
    }
    let prior_total = match parse_total_time(row.get::<Option<String>, _>("total_time")) {
        Ok(total) => total,
        Err(response) => return response,
    };
    let prior_count = row.get::<Option<i32>, _>("total_count").unwrap_or(0);
    let Some(total_count) = prior_count.checked_add(1) else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = row.get::<String, _>("id");
    if let Err(error) = sqlx::query("UPDATE t_radiotelephone_train SET active_millis=LEAST(9223372036854775807,GREATEST(0,COALESCE(active_millis,0))+CASE WHEN session_started_at IS NULL THEN 0 ELSE GREATEST(0,TIMESTAMPDIFF(MICROSECOND,session_started_at,NOW(6)) DIV 1000) END),session_started_at=NULL WHERE id=?")
        .bind(&id)
        .execute(&mut *transaction)
        .await
    {
        return database_error(error, "accrue final active interval");
    }
    let elapsed_millis =
        match sqlx::query("SELECT active_millis FROM t_radiotelephone_train WHERE id=? LIMIT 1")
            .bind(&id)
            .fetch_one(&mut *transaction)
            .await
        {
            Ok(row) => row
                .get::<Option<i64>, _>("active_millis")
                .unwrap_or(0)
                .max(0),
            Err(error) => return database_error(error, "read final active duration"),
        };
    let elapsed_seconds = (elapsed_millis / 1_000).min(i64::from(i32::MAX));
    let total_time = prior_total.saturating_add(elapsed_seconds).to_string();
    if let Err(error) = sqlx::query("UPDATE t_radiotelephone_train SET total_count=?,total_time=?,finalized_session_id=?,active_session_id=NULL,session_started_at=NULL,active_millis=NULL WHERE id=?")
        .bind(total_count)
        .bind(total_time)
        .bind(&session_id)
        .bind(&id)
        .execute(&mut *transaction)
        .await
    {
        return database_error(error, "finalize training session");
    }
    let row = match load_row(&mut transaction, &user.id, train_type).await {
        Ok(Some(row)) => row,
        Ok(None) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
        Err(error) => return database_error(error, "reload finalized training"),
    };
    let data = view(&row, Some(&session_id));
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit finish transaction");
    }
    envelope_json(StatusCode::OK, 200, Some(data), "ok", "")
}

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::{
        military, military_term_data,
        platform::auth::{authenticate, require_admin},
        post_military, post_radiotelephone, radio_term, train_rule,
    };
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
