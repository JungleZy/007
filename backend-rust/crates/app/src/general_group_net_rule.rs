use axum::{extract::State, http::StatusCode, response::Response, Json};
use serde::{Deserialize, Deserializer};
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, MySqlPool, Row};

use super::{envelope_json, envelope_response, AppState};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct RuleRequest {
    #[serde(default, deserialize_with = "deserialize_id")]
    id: Option<i32>,
    xy_score: Option<String>,
    device: Option<String>,
    code: Option<String>,
    create_time: Option<String>,
}

fn deserialize_id<'de, D>(deserializer: D) -> Result<Option<i32>, D::Error>
where
    D: Deserializer<'de>,
{
    #[derive(Deserialize)]
    #[serde(untagged)]
    enum Id {
        Number(i32),
        Text(String),
    }

    match Option::<Id>::deserialize(deserializer)? {
        None => Ok(None),
        Some(Id::Number(id)) => Ok(Some(id)),
        // The frontend submits an empty string when creating a new rule.
        Some(Id::Text(id)) if id.trim().is_empty() => Ok(None),
        Some(Id::Text(id)) => id
            .trim()
            .parse()
            .map(Some)
            .map_err(serde::de::Error::custom),
    }
}

fn rule_json(row: &MySqlRow) -> Result<Value, sqlx::Error> {
    Ok(json!({
        "id": row.try_get::<i32, _>("id")?,
        "xyScore": row.try_get::<Option<String>, _>("xy_score")?,
        "device": row.try_get::<Option<String>, _>("device")?,
        "code": row.try_get::<Option<String>, _>("code")?,
        "createTime": row.try_get::<Option<String>, _>("create_time_text")?,
    }))
}

fn database_error(error: sqlx::Error, operation: &str) -> Response {
    tracing::warn!(error = %error, operation, "general group net rule operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

pub(crate) async fn find_all(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result = async {
        let rows = sqlx::query("SELECT id,xy_score,device,code,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS create_time_text FROM t_general_group_net_rule")
            .fetch_all(pool)
            .await?;
        rows.iter().map(rule_json).collect::<Result<Vec<_>, sqlx::Error>>()
    }
    .await;
    match result {
        Ok(rules) => envelope_json(StatusCode::OK, 200, Some(json!(rules)), "ok", ""),
        Err(error) => database_error(error, "findAll"),
    }
}

async fn save_rule(pool: &MySqlPool, body: RuleRequest) -> Result<Value, sqlx::Error> {
    let mut tx = pool.begin().await?;
    let id = if let Some(id) = body.id {
        sqlx::query("SELECT id FROM t_general_group_net_rule WHERE id=? FOR UPDATE")
            .bind(id)
            .fetch_one(&mut *tx)
            .await?;
        sqlx::query("UPDATE t_general_group_net_rule SET xy_score=?,device=?,code=?,create_time=? WHERE id=?")
            .bind(&body.xy_score)
            .bind(&body.device)
            .bind(&body.code)
            .bind(&body.create_time)
            .bind(id)
            .execute(&mut *tx)
            .await?;
        i64::from(id)
    } else {
        let result = sqlx::query("INSERT INTO t_general_group_net_rule (xy_score,device,code,create_time) VALUES (?,?,?,?)")
            .bind(&body.xy_score)
            .bind(&body.device)
            .bind(&body.code)
            .bind(&body.create_time)
            .execute(&mut *tx)
            .await?;
        // The table's generated key is a signed INT.
        result.last_insert_id() as i64
    };
    let row = sqlx::query("SELECT id,xy_score,device,code,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS create_time_text FROM t_general_group_net_rule WHERE id=?")
        .bind(id)
        .fetch_one(&mut *tx)
        .await?;
    let saved = rule_json(&row)?;
    tx.commit().await?;
    Ok(saved)
}

pub(crate) async fn save(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let body = match serde_json::from_value::<RuleRequest>(body) {
        Ok(body) => body,
        Err(_) => return envelope_response(StatusCode::OK, 202, "请求参数错误", ""),
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match save_rule(pool, body).await {
        Ok(rule) => envelope_json(StatusCode::OK, 200, Some(rule), "ok", ""),
        Err(error) => database_error(error, "save"),
    }
}

pub(crate) async fn delete_by_id(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let id = match serde_json::from_value::<RuleRequest>(body) {
        Ok(RuleRequest { id: Some(id), .. }) => id,
        _ => return envelope_response(StatusCode::OK, 202, "请求参数错误", ""),
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result: Result<(), sqlx::Error> = async {
        let mut tx = pool.begin().await?;
        sqlx::query("DELETE FROM t_general_group_net_rule WHERE id=?")
            .bind(id)
            .execute(&mut *tx)
            .await?;
        tx.commit().await
    }
    .await;
    match result {
        Ok(()) => envelope_response(StatusCode::OK, 200, "ok", ""),
        Err(error) => database_error(error, "deleteById"),
    }
}
