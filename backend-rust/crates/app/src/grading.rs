use axum::{extract::State, http::StatusCode, response::Response, Json};
use serde_json::{json, Value};
use sqlx::Row;

use super::{envelope_json, envelope_response, AppState};

fn rule_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "type": row.get::<Option<i32>, _>("type"),
        "title": row.get::<Option<String>, _>("title"),
        "score": row.get::<Option<i32>, _>("score"),
        "status": row.get::<Option<i32>, _>("status"),
        "isDefault": row.get::<Option<i32>, _>("is_default"),
        "content": row.get::<Option<String>, _>("content")
    })
}

async fn rule_by_id(
    pool: &sqlx::MySqlPool,
    id: &str,
) -> Result<Option<sqlx::mysql::MySqlRow>, sqlx::Error> {
    sqlx::query("SELECT id, type, title, score, status, is_default, content FROM t_grading_rule WHERE id = ? LIMIT 1")
        .bind(id).fetch_optional(pool).await
}
async fn active_reference_count(
    pool: &sqlx::MySqlPool,
    table: &str,
    id: &str,
) -> Result<i64, sqlx::Error> {
    let exists = sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")
        .bind(table).fetch_one(pool).await?;
    if exists == 0 {
        return Ok(0);
    }
    let query = format!(
        "SELECT COUNT(*) AS count FROM `{table}` WHERE rule_id = ? AND (status = 0 OR status = 1)"
    );
    Ok(sqlx::query_scalar::<_, i64>(&query)
        .bind(id)
        .fetch_one(pool)
        .await?)
}

pub(crate) async fn list_by_type(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(rule_type) = body.get("type").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    match sqlx::query("SELECT id, type, title, score, status, is_default, content FROM t_grading_rule WHERE type = ? ORDER BY id").bind(rule_type).fetch_all(pool).await {
        Ok(rows) => envelope_json(StatusCode::OK, 200, Some(json!(rows.iter().map(rule_json).collect::<Vec<_>>())), "ok", ""),
        Err(error) => { tracing::warn!(error = %error, "grading rule list query failed"); envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "") }
    }
}

pub(crate) async fn by_id(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    match rule_by_id(pool, id).await {
        Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(rule_json(&row)), "ok", ""),
        Ok(None) => envelope_response(StatusCode::OK, 202, "未查询到评分规则", "未查询到评分规则"),
        Err(error) => {
            tracing::warn!(error = %error, "grading rule detail query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(crate) async fn save(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    // Jackson binds numeric JSON ids to String before the create/update decision.
    let supplied_id = body
        .get("id")
        .and_then(|value| match value {
            Value::String(id) => Some(id.clone()),
            Value::Number(id) => Some(id.to_string()),
            _ => None,
        })
        .filter(|id| !id.trim().is_empty());
    let creating = supplied_id.is_none();
    let rule_id = supplied_id.unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    let Some(rule_type) = body.get("type").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let score = body.get("score").and_then(Value::as_i64);
    let title = body.get("title").and_then(Value::as_str);
    let status = body.get("status").and_then(Value::as_i64).unwrap_or(0);
    let requested_default = body.get("isDefault").and_then(Value::as_i64).unwrap_or(1);
    let content = body.get("content").and_then(Value::as_str);
    if [0, 2, 3].contains(&rule_type) && score.is_none_or(|value| value < 0) {
        return envelope_response(StatusCode::OK, 500, "评分规则满分必须为非负整数", "");
    }
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if !creating {
        // Lock only the supplied primary key. A deleted/stale id must never create a rule.
        match sqlx::query_scalar::<_, String>(
            "SELECT id FROM t_grading_rule WHERE id = ? FOR UPDATE",
        )
        .bind(&rule_id)
        .fetch_optional(&mut *tx)
        .await
        {
            Ok(Some(_)) => {}
            Ok(None) => {
                let _ = tx.rollback().await;
                return envelope_response(
                    StatusCode::OK,
                    202,
                    "未查询到评分规则",
                    "未查询到评分规则",
                );
            }
            Err(error) => {
                tracing::warn!(%error, "grading rule update lookup failed");
                let _ = tx.rollback().await;
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        }
    }
    if requested_default == 0
        && sqlx::query("UPDATE t_grading_rule SET is_default = 1 WHERE type = ?")
            .bind(rule_type)
            .execute(&mut *tx)
            .await
            .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    let is_default = if requested_default == 0 {
        0
    } else {
        match sqlx::query("SELECT COUNT(*) AS count FROM t_grading_rule WHERE type = ? AND is_default = 0 AND id <> ?").bind(rule_type).bind(&rule_id).fetch_one(&mut *tx).await { Ok(row) if row.get::<i64, _>("count") == 0 => 0, _ => 1 }
    };
    let write = if creating {
        sqlx::query("INSERT INTO t_grading_rule (id,type,title,score,status,is_default,content) VALUES (?,?,?,?,?,?,?)")
            .bind(&rule_id).bind(rule_type).bind(title).bind(score).bind(status).bind(is_default).bind(content)
            .execute(&mut *tx).await
    } else {
        sqlx::query("UPDATE t_grading_rule SET type=?,title=?,score=?,status=?,is_default=?,content=? WHERE id=?")
            .bind(rule_type).bind(title).bind(score).bind(status).bind(is_default).bind(content).bind(&rule_id)
            .execute(&mut *tx).await
    };
    if let Err(error) = write {
        tracing::warn!(%error, "grading rule save failed");
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    match rule_by_id(pool, &rule_id).await {
        Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(rule_json(&row)), "ok", ""),
        _ => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) async fn update_status(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(status) = body.get("status").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE t_grading_rule SET status = ? WHERE id = ?")
        .bind(status)
        .bind(id)
        .execute(pool)
        .await
    {
        Ok(result) if result.rows_affected() == 1 => match rule_by_id(pool, id).await {
            Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(rule_json(&row)), "ok", ""),
            _ => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
        },
        Ok(_) => envelope_response(StatusCode::OK, 202, "未查询到评分规则", "未查询到评分规则"),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) async fn change_default(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(row) = rule_by_id(pool, id).await.ok().flatten() else {
        return envelope_response(StatusCode::OK, 202, "未查询到评分规则", "未查询到评分规则");
    };
    let rule_type: i32 = row.get::<Option<i32>, _>("type").unwrap_or_default();
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if sqlx::query("UPDATE t_grading_rule SET is_default = 1 WHERE type = ?")
        .bind(rule_type)
        .execute(&mut *tx)
        .await
        .is_err()
        || sqlx::query("UPDATE t_grading_rule SET is_default = 0 WHERE id = ?")
            .bind(id)
            .execute(&mut *tx)
            .await
            .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, None, "ok", "")
}

pub(crate) async fn delete(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let reference_tables = [
        "t_post_telegram_train",
        "t_post_telex_pat_train",
        "t_post_telegraph_key_pat_train",
        "general_telex_pat",
        "general_key_pat",
        "general_ticker_pat",
    ];
    for table in reference_tables {
        match active_reference_count(pool, table, id).await {
            Ok(count) if count > 0 => {
                return envelope_response(
                    StatusCode::OK,
                    500,
                    "存在未开始或进行中的训练引用该评分规则，禁止删除",
                    "",
                )
            }
            Ok(_) => {}
            Err(error) => {
                tracing::warn!(error = %error, table, "grading rule reference query failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        }
    }
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if sqlx::query("DELETE FROM t_grading_rule WHERE id = ?")
        .bind(id)
        .execute(&mut *tx)
        .await
        .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, None, "ok", "")
}
