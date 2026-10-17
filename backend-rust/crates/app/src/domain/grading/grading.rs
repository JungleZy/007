use axum::{extract::State, http::StatusCode, response::Response, Json};
use serde_json::{json, Value};
use sqlx::{pool::PoolConnection, Connection, MySql, Row, Transaction};

use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;

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

pub(super) async fn rule_by_id(
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

pub(super) async fn list_by_type(
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

pub(super) async fn by_id(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
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

// Named-lock sessions keep their pool permit and are closed on every exit,
// including cancellation while GET_LOCK is waiting. Never reuse these sessions.
pub(super) async fn type_connection(
    pool: &sqlx::MySqlPool,
    rule_type: i64,
) -> Result<PoolConnection<MySql>, sqlx::Error> {
    let mut connection = pool.acquire().await?;
    connection.close_on_drop();
    let acquired: Option<i64> = sqlx::query_scalar(
        "SELECT GET_LOCK(CONCAT('grading:', LEFT(SHA2(CONCAT(DATABASE(), ':', ?), 256), 56)), 10)",
    )
    .bind(rule_type)
    .fetch_one(&mut *connection)
    .await?;
    if acquired != Some(1) {
        return Err(sqlx::Error::Protocol(
            "grading type lock unavailable".into(),
        ));
    }
    Ok(connection)
}

async fn lock_rules(
    tx: &mut Transaction<'_, MySql>,
    rule_type: i64,
    target: Option<&str>,
) -> Result<Vec<sqlx::mysql::MySqlRow>, sqlx::Error> {
    let mut ids =
        sqlx::query_scalar::<_, String>("SELECT id FROM t_grading_rule WHERE type=? ORDER BY id")
            .bind(rule_type)
            .fetch_all(&mut **tx)
            .await?;
    if let Some(target) = target {
        ids.push(target.to_owned());
    }
    ids.sort();
    ids.dedup();
    let mut rows = Vec::with_capacity(ids.len());
    for id in ids {
        if let Some(row) =
            sqlx::query("SELECT id,type,is_default FROM t_grading_rule WHERE id=? FOR UPDATE")
                .bind(id)
                .fetch_optional(&mut **tx)
                .await?
        {
            rows.push(row);
        }
    }
    Ok(rows)
}

async fn reset_defaults(
    tx: &mut Transaction<'_, MySql>,
    rows: &[sqlx::mysql::MySqlRow],
    rule_type: i64,
) -> Result<(), sqlx::Error> {
    for row in rows
        .iter()
        .filter(|row| row.get::<Option<i32>, _>("type").map(i64::from) == Some(rule_type))
    {
        sqlx::query("UPDATE t_grading_rule SET is_default=1 WHERE id=?")
            .bind(row.get::<String, _>("id"))
            .execute(&mut **tx)
            .await?;
    }
    Ok(())
}

pub(super) async fn save(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
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
    let result = async {
        let rule_id = if creating {
            rule_id
        } else {
            let Some(row) = rule_by_id(pool, &rule_id).await? else {
                return Ok(None);
            };
            row.get::<String, _>("id")
        };
        let mut connection = type_connection(pool, rule_type).await?;
        let mut tx = connection.begin().await?;
        let rows = lock_rules(&mut tx, rule_type, (!creating).then_some(rule_id.as_str())).await?;
        if !creating && !rows.iter().any(|row| row.get::<String, _>("id") == rule_id) {
            tx.rollback().await?;
            return Ok(None);
        }
        let is_default = if requested_default == 0 {
            reset_defaults(&mut tx, &rows, rule_type).await?;
            0
        } else if rows.iter().any(|row| row.get::<String, _>("id") != rule_id
            && row.get::<Option<i32>, _>("type").map(i64::from) == Some(rule_type)
            && row.get::<Option<i32>, _>("is_default") == Some(0)) { 1 } else { 0 };
        if creating {
            sqlx::query("INSERT INTO t_grading_rule (id,type,title,score,status,is_default,content) VALUES (?,?,?,?,?,?,?)")
                .bind(&rule_id).bind(rule_type).bind(title).bind(score).bind(status).bind(is_default).bind(content)
                .execute(&mut *tx).await?;
        } else {
            sqlx::query("UPDATE t_grading_rule SET type=?,title=?,score=?,status=?,is_default=?,content=? WHERE id=?")
                .bind(rule_type).bind(title).bind(score).bind(status).bind(is_default).bind(content).bind(&rule_id)
                .execute(&mut *tx).await?;
        }
        tx.commit().await?;
        drop(connection);
        rule_by_id(pool, &rule_id).await
    }.await;
    match result {
        Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(rule_json(&row)), "ok", ""),
        Ok(None) => envelope_response(StatusCode::OK, 202, "未查询到评分规则", "未查询到评分规则"),
        Err(error) => {
            tracing::warn!(%error, "grading rule save failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(super) async fn update_status(
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

pub(super) async fn change_default(
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
    let result = async {
        loop {
            let Some(row) = rule_by_id(pool, id).await? else {
                return Ok(false);
            };
            let rule_type = i64::from(row.get::<Option<i32>, _>("type").unwrap_or_default());
            let canonical_id = row.get::<String, _>("id");
            let mut connection = type_connection(pool, rule_type).await?;
            let mut tx = connection.begin().await?;
            let rows = lock_rules(&mut tx, rule_type, Some(&canonical_id)).await?;
            let Some(target) = rows
                .iter()
                .find(|row| row.get::<String, _>("id") == canonical_id)
            else {
                tx.rollback().await?;
                return Ok(false);
            };
            // The current PK read, not the preflight snapshot, controls the reset.
            if i64::from(target.get::<Option<i32>, _>("type").unwrap_or_default()) != rule_type {
                tx.rollback().await?;
                continue;
            }
            reset_defaults(&mut tx, &rows, rule_type).await?;
            sqlx::query("UPDATE t_grading_rule SET is_default=0 WHERE id=?")
                .bind(&canonical_id)
                .execute(&mut *tx)
                .await?;
            tx.commit().await?;
            return Ok::<_, sqlx::Error>(true);
        }
    }
    .await;
    match result {
        Ok(true) => envelope_json(StatusCode::OK, 200, None, "ok", ""),
        Ok(false) => envelope_response(StatusCode::OK, 202, "未查询到评分规则", "未查询到评分规则"),
        Err(error) => {
            tracing::warn!(%error, "grading rule default change failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(super) async fn delete(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
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
