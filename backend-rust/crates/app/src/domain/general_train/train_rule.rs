use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;
use axum::{
    extract::{rejection::JsonRejection, Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, MySqlPool, Row};

#[derive(Deserialize)]
struct RuleInput {
    pub(super) id: Option<i32>,
    #[serde(rename = "type")]
    pub(super) kind: i32,
    level: Option<String>,
    pub(super) accuracy: Option<String>,
    pub(super) description: Option<String>,
}

#[derive(Deserialize)]
struct TypeInput {
    #[serde(rename = "type")]
    pub(super) kind: i32,
}

#[derive(Deserialize)]
pub(crate) struct IdInput {
    pub(crate) id: i32,
}

#[derive(Deserialize)]
pub(crate) struct TermInput {
    pub(crate) id: Option<i32>,
    #[serde(rename = "type")]
    pub(crate) kind: i32,
    pub(crate) key: Option<String>,
    pub(crate) value: Option<String>,
    pub(crate) sort: Option<i32>,
}

fn invalid_request() -> Response {
    envelope_response(StatusCode::OK, 202, "请求参数错误", "")
}

fn system_error() -> Response {
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

fn database_error(error: sqlx::Error) -> Response {
    tracing::warn!(error = %error, "training rule or radio term database operation failed");
    system_error()
}

fn rule_json(row: &MySqlRow) -> Result<Value, sqlx::Error> {
    Ok(json!({
        "id": row.try_get::<i32, _>("id")?,
        "type": row.try_get::<Option<i32>, _>("type")?,
        "level": row.try_get::<Option<String>, _>("level")?,
        "accuracy": row.try_get::<Option<String>, _>("accuracy")?,
        "description": row.try_get::<Option<String>, _>("description")?,
        "createTime": row.try_get::<Option<String>, _>("create_time_text")?,
    }))
}

async fn save_rules(pool: &MySqlPool, rules: &[RuleInput]) -> Result<Value, sqlx::Error> {
    let mut tx = pool.begin().await?;
    let mut result = Vec::with_capacity(rules.len());
    for rule in rules {
        let id = if let Some(id) = rule.id {
            sqlx::query(
                "UPDATE t_post_train_global_rule SET type=?,level=?,accuracy=?,description=? WHERE id=?",
            )
            .bind(rule.kind)
            .bind(rule.level.as_deref())
            .bind(rule.accuracy.as_deref())
            .bind(rule.description.as_deref())
            .bind(id)
            .execute(&mut *tx)
            .await?;
            i64::from(id)
        } else {
            let inserted = sqlx::query(
                "INSERT INTO t_post_train_global_rule (type,level,accuracy,description,create_time) VALUES (?,?,?,?,NOW())",
            )
            .bind(rule.kind)
            .bind(rule.level.as_deref())
            .bind(rule.accuracy.as_deref())
            .bind(rule.description.as_deref())
            .execute(&mut *tx)
            .await?;
            i64::try_from(inserted.last_insert_id())
                .map_err(|error| sqlx::Error::Decode(Box::new(error)))?
        };
        let row = sqlx::query(
            "SELECT id,type,level,accuracy,description,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS create_time_text FROM t_post_train_global_rule WHERE id=?",
        )
        .bind(id)
        .fetch_one(&mut *tx)
        .await?;
        result.push(rule_json(&row)?);
    }
    tx.commit().await?;
    Ok(Value::Array(result))
}

async fn add_rule(
    State(state): State<AppState>,
    Extension(_user): Extension<AuthUser>,
    body: Result<Json<Vec<RuleInput>>, JsonRejection>,
) -> Response {
    let Ok(Json(rules)) = body else {
        return invalid_request();
    };
    if rules.iter().any(|rule| rule.id.is_some_and(|id| id <= 0)) {
        return invalid_request();
    }
    let Some(pool) = state.pool.as_ref() else {
        return system_error();
    };
    match save_rules(pool, &rules).await {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(error) => database_error(error),
    }
}

async fn find_by_type(
    State(state): State<AppState>,
    Extension(_user): Extension<AuthUser>,
    body: Result<Json<TypeInput>, JsonRejection>,
) -> Response {
    let Ok(Json(body)) = body else {
        return invalid_request();
    };
    let Some(pool) = state.pool.as_ref() else {
        return system_error();
    };
    let rows = match sqlx::query(
        "SELECT id,type,level,accuracy,description,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS create_time_text FROM t_post_train_global_rule WHERE type=? ORDER BY create_time ASC",
    )
    .bind(body.kind)
    .fetch_all(pool)
    .await
    {
        Ok(rows) => rows,
        Err(error) => return database_error(error),
    };
    match rows.iter().map(rule_json).collect::<Result<Vec<_>, _>>() {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(Value::Array(data)), "ok", ""),
        Err(error) => database_error(error),
    }
}

async fn delete_rule(pool: &MySqlPool, id: i32) -> Result<(), sqlx::Error> {
    let mut tx = pool.begin().await?;
    sqlx::query("DELETE FROM t_post_train_global_rule WHERE id=?")
        .bind(id)
        .execute(&mut *tx)
        .await?;
    tx.commit().await
}

async fn delete_by_id(
    State(state): State<AppState>,
    Extension(_user): Extension<AuthUser>,
    body: Result<Json<IdInput>, JsonRejection>,
) -> Response {
    let Ok(Json(body)) = body else {
        return invalid_request();
    };
    if body.id <= 0 {
        return invalid_request();
    }
    let Some(pool) = state.pool.as_ref() else {
        return system_error();
    };
    match delete_rule(pool, body.id).await {
        Ok(()) => envelope_response(StatusCode::OK, 200, "ok", ""),
        Err(error) => database_error(error),
    }
}

async fn save_term_data(pool: &MySqlPool, term: &TermInput) -> Result<Option<Value>, sqlx::Error> {
    let mut tx = pool.begin().await?;
    let id = if let Some(id) = term.id {
        let existing = sqlx::query_scalar::<_, i32>(
            "SELECT id FROM t_post_radiotelephone_term_data WHERE id=? FOR UPDATE",
        )
        .bind(id)
        .fetch_optional(&mut *tx)
        .await?;
        if existing.is_none() {
            tx.rollback().await?;
            return Ok(None);
        }
        sqlx::query(
            "UPDATE t_post_radiotelephone_term_data SET type=?,`key`=?,value=?,sort=? WHERE id=?",
        )
        .bind(term.kind)
        .bind(term.key.as_deref())
        .bind(term.value.as_deref())
        .bind(term.sort)
        .bind(id)
        .execute(&mut *tx)
        .await?;
        id
    } else {
        let inserted = sqlx::query(
            "INSERT INTO t_post_radiotelephone_term_data (type,`key`,value,sort) VALUES (?,?,?,?)",
        )
        .bind(term.kind)
        .bind(term.key.as_deref())
        .bind(term.value.as_deref())
        .bind(term.sort)
        .execute(&mut *tx)
        .await?;
        i32::try_from(inserted.last_insert_id())
            .map_err(|error| sqlx::Error::Decode(Box::new(error)))?
    };
    tx.commit().await?;
    Ok(Some(json!({
        "id": id,
        "type": term.kind,
        "key": term.key,
        "value": term.value,
        "sort": term.sort,
    })))
}

pub(crate) async fn term_save(
    State(state): State<AppState>,
    Extension(_user): Extension<AuthUser>,
    body: Result<Json<TermInput>, JsonRejection>,
) -> Response {
    let Ok(Json(term)) = body else {
        return invalid_request();
    };
    if term.id.is_some_and(|id| id <= 0) {
        return invalid_request();
    }
    let Some(pool) = state.pool.as_ref() else {
        return system_error();
    };
    match save_term_data(pool, &term).await {
        Ok(Some(data)) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Ok(None) => system_error(),
        Err(error) => database_error(error),
    }
}

pub(crate) async fn term_update(
    State(state): State<AppState>,
    Extension(_user): Extension<AuthUser>,
    body: Result<Json<TermInput>, JsonRejection>,
) -> Response {
    let Ok(Json(term)) = body else {
        return invalid_request();
    };
    if term.id.is_none_or(|id| id <= 0) {
        return invalid_request();
    }
    let Some(pool) = state.pool.as_ref() else {
        return system_error();
    };
    match save_term_data(pool, &term).await {
        Ok(Some(data)) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Ok(None) => envelope_response(StatusCode::OK, 202, "未查询到该词条", "未查询到该词条"),
        Err(error) => database_error(error),
    }
}

async fn delete_term_data(pool: &MySqlPool, id: i32) -> Result<(), sqlx::Error> {
    let mut tx = pool.begin().await?;
    sqlx::query("DELETE FROM t_post_radiotelephone_term_data WHERE id=?")
        .bind(id)
        .execute(&mut *tx)
        .await?;
    tx.commit().await
}

pub(crate) async fn term_delete(
    State(state): State<AppState>,
    Extension(_user): Extension<AuthUser>,
    body: Result<Json<IdInput>, JsonRejection>,
) -> Response {
    let Ok(Json(body)) = body else {
        return invalid_request();
    };
    if body.id <= 0 {
        return invalid_request();
    }
    let Some(pool) = state.pool.as_ref() else {
        return system_error();
    };
    match delete_term_data(pool, body.id).await {
        Ok(()) => envelope_response(StatusCode::OK, 200, "ok", ""),
        Err(error) => database_error(error),
    }
}

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::platform::auth::authenticate;
    use crate::platform::auth::require_admin;
    use axum::{middleware, routing::post, Router};

    Router::new().nest(
        "/api/postTrainGlobalRule",
        Router::new()
            .route("/findByType", post(find_by_type))
            .route_layer(middleware::from_fn_with_state(state.clone(), authenticate))
            .merge(
                Router::new()
                    .route("/addRule", post(add_rule))
                    .route("/deleteById", post(delete_by_id))
                    .route_layer(middleware::from_fn(require_admin))
                    .route_layer(middleware::from_fn_with_state(state.clone(), authenticate)),
            ),
    )
}
