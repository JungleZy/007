use axum::{extract::State, http::StatusCode, response::Response, Json};
use serde_json::{json, Value};
use sqlx::{Connection, MySql, MySqlConnection, Row, Transaction};

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

// Named locks cover same-type INSERTs as well as existing rows. The detached
// session is never returned to the pool: dropping it releases the lock on every
// return/error/cancellation path, including failed commits and GET_LOCK errors.
async fn type_connection(
    pool: &sqlx::MySqlPool,
    rule_type: i64,
) -> Result<MySqlConnection, sqlx::Error> {
    let mut connection = pool.acquire().await?.detach();
    let acquired: Option<i64> = sqlx::query_scalar(
        "SELECT GET_LOCK(CONCAT('grading:', LEFT(SHA2(CONCAT(DATABASE(), ':', ?), 256), 56)), 10)",
    )
    .bind(rule_type)
    .fetch_one(&mut connection)
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
    let result = async {
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
    let result = async {
        loop {
            let Some(row) = rule_by_id(pool, id).await? else {
                return Ok(false);
            };
            let rule_type = i64::from(row.get::<Option<i32>, _>("type").unwrap_or_default());
            let mut connection = type_connection(pool, rule_type).await?;
            let mut tx = connection.begin().await?;
            let rows = lock_rules(&mut tx, rule_type, Some(id)).await?;
            let Some(target) = rows.iter().find(|row| row.get::<String, _>("id") == id) else {
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
                .bind(id)
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

#[cfg(test)]
mod tests {
    use super::*;
    use std::{sync::Arc, time::Duration};

    async fn response_code(response: Response) -> Value {
        assert_eq!(
            response.status(),
            StatusCode::OK,
            "database errors must not escape"
        );
        let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        serde_json::from_slice::<Value>(&bytes).unwrap()["code"].clone()
    }

    async fn fixture() -> (AppState, sqlx::MySqlPool, Vec<String>) {
        let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("scratch database URL");
        assert!(url.ends_with("/rust_scratch_lockdemo"));
        let pool = sqlx::mysql::MySqlPoolOptions::new()
            .max_connections(8)
            .connect(&url)
            .await
            .unwrap();
        let state = AppState {
            config: Arc::new(crate::AppConfig::from_env().unwrap()),
            pool: Some(pool.clone()),
            database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
        };
        let suffix = uuid::Uuid::new_v4().to_string();
        let ids = ["000", "aaa", "zzz"]
            .map(|prefix| format!("{prefix}-{suffix}"))
            .to_vec();
        for (id, kind, default) in [
            (&ids[0], 12347, 0),
            (&ids[1], 12346, 0),
            (&ids[2], 12346, 1),
        ] {
            sqlx::query("INSERT INTO t_grading_rule (id,type,title,score,status,is_default) VALUES (?,?,'isolation regression',100,0,?)")
                .bind(id).bind(kind).bind(default).execute(&pool).await.unwrap();
        }
        (state, pool, ids)
    }

    async fn wait_on_gate(pool: &sqlx::MySqlPool, connection: u64) -> bool {
        tokio::time::timeout(Duration::from_secs(5), async {
            loop {
                let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME='t_grading_rule'")
                    .bind(connection).fetch_one(pool).await.unwrap();
                if count > 0 { break; }
                tokio::time::sleep(Duration::from_millis(10)).await;
            }
        }).await.is_ok()
    }

    async fn cleanup(pool: &sqlx::MySqlPool, ids: &[String]) {
        for id in ids {
            sqlx::query("DELETE FROM t_grading_rule WHERE id=?")
                .bind(id)
                .execute(pool)
                .await
                .unwrap();
        }
    }

    #[tokio::test]
    #[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema lock tables"]
    async fn unrelated_type_stays_writable_during_save_and_default_change() {
        for changing in [false, true] {
            let (state, pool, ids) = fixture().await;
            let mut gate = pool.begin().await.unwrap();
            let connection: u64 = sqlx::query_scalar("SELECT CONNECTION_ID()")
                .fetch_one(&mut *gate)
                .await
                .unwrap();
            sqlx::query("SELECT id FROM t_grading_rule WHERE id=? FOR UPDATE")
                .bind(&ids[1])
                .fetch_one(&mut *gate)
                .await
                .unwrap();
            let target = ids[2].clone();
            let writer = tokio::spawn(async move {
                if changing {
                    change_default(State(state), Json(json!({"id":target}))).await
                } else {
                    save(
                        State(state),
                        Json(json!({"id":target,"type":12346,"score":100,"isDefault":0})),
                    )
                    .await
                }
            });
            let waiting = wait_on_gate(&pool, connection).await;
            let unrelated = tokio::time::timeout(
                Duration::from_millis(500),
                sqlx::query("UPDATE t_grading_rule SET title='still writable' WHERE id=?")
                    .bind(&ids[0])
                    .execute(&pool),
            )
            .await;
            gate.commit().await.unwrap();
            let response = tokio::time::timeout(Duration::from_secs(5), writer)
                .await
                .unwrap()
                .unwrap();
            cleanup(&pool, &ids).await;
            assert!(waiting);
            assert!(
                unrelated.is_ok(),
                "another type must not be locked by a table scan"
            );
            unrelated.unwrap().unwrap();
            assert_eq!(response_code(response).await, 200);
        }
    }

    #[tokio::test]
    #[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema lock tables"]
    async fn deleted_default_target_returns_202_without_resetting_survivors() {
        let (state, pool, ids) = fixture().await;
        let mut gate = pool.begin().await.unwrap();
        let connection: u64 = sqlx::query_scalar("SELECT CONNECTION_ID()")
            .fetch_one(&mut *gate)
            .await
            .unwrap();
        sqlx::query("SELECT id FROM t_grading_rule WHERE id=? FOR UPDATE")
            .bind(&ids[1])
            .fetch_one(&mut *gate)
            .await
            .unwrap();
        // The preflight and discovery still see the committed target, but the
        // current PK read after the gate commits must see that it was deleted.
        sqlx::query("DELETE FROM t_grading_rule WHERE id=?")
            .bind(&ids[2])
            .execute(&mut *gate)
            .await
            .unwrap();
        let target = ids[2].clone();
        let writer =
            tokio::spawn(
                async move { change_default(State(state), Json(json!({"id":target}))).await },
            );
        let waiting = wait_on_gate(&pool, connection).await;
        gate.commit().await.unwrap();
        let response = writer.await.unwrap();
        let default: i32 = sqlx::query_scalar("SELECT is_default FROM t_grading_rule WHERE id=?")
            .bind(&ids[1])
            .fetch_one(&pool)
            .await
            .unwrap();
        cleanup(&pool, &ids).await;
        assert!(waiting);
        assert_eq!(response_code(response).await, 202);
        assert_eq!(
            default, 0,
            "a vanished target must not reset any surviving default"
        );
    }

    #[tokio::test]
    #[ignore = "requires isolated rust_scratch_lockdemo"]
    async fn concurrent_new_rules_choose_exactly_one_default() {
        let (state, pool, ids) = fixture().await;
        let mut writers = Vec::new();
        for _ in 0..2 {
            let state = state.clone();
            writers.push(tokio::spawn(async move {
                save(
                    State(state),
                    Json(json!({"type":12348,"score":100,"isDefault":1})),
                )
                .await
            }));
        }
        for writer in writers {
            assert_eq!(response_code(writer.await.unwrap()).await, 200);
        }
        let defaults: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM t_grading_rule WHERE type=12348 AND is_default=0",
        )
        .fetch_one(&pool)
        .await
        .unwrap();
        let created =
            sqlx::query_scalar::<_, String>("SELECT id FROM t_grading_rule WHERE type=12348")
                .fetch_all(&pool)
                .await
                .unwrap();
        cleanup(&pool, &created).await;
        cleanup(&pool, &ids).await;
        assert_eq!(defaults, 1);
    }

    #[tokio::test]
    #[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema lock tables"]
    async fn default_change_uses_current_target_type_after_preflight() {
        let (state, pool, ids) = fixture().await;
        let gate = type_connection(&pool, 12346).await.unwrap();
        let target = ids[2].clone();
        let writer =
            tokio::spawn(
                async move { change_default(State(state), Json(json!({"id":target}))).await },
            );
        let waiting = tokio::time::timeout(Duration::from_secs(5), async {
            loop {
                let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM performance_schema.metadata_locks WHERE OBJECT_TYPE='USER LEVEL LOCK' AND LOCK_STATUS='PENDING'")
                    .fetch_one(&pool).await.unwrap();
                if count > 0 { break; }
                tokio::time::sleep(Duration::from_millis(10)).await;
            }
        }).await;
        sqlx::query("UPDATE t_grading_rule SET type=12347 WHERE id=?")
            .bind(&ids[2])
            .execute(&pool)
            .await
            .unwrap();
        drop(gate);
        let response = tokio::time::timeout(Duration::from_secs(5), writer)
            .await
            .unwrap()
            .unwrap();
        let mut defaults = Vec::new();
        for id in &ids {
            defaults.push(
                sqlx::query_scalar::<_, i32>("SELECT is_default FROM t_grading_rule WHERE id=?")
                    .bind(id)
                    .fetch_one(&pool)
                    .await
                    .unwrap(),
            );
        }
        cleanup(&pool, &ids).await;
        assert!(
            waiting.is_ok(),
            "preflight must precede the concurrent type change"
        );
        assert_eq!(response_code(response).await, 200);
        assert_eq!(defaults, [1, 0, 0], "only the current target type is reset");
    }

    #[tokio::test]
    #[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema lock tables"]
    async fn concurrent_default_saves_and_default_change_have_one_lock_order() {
        let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("scratch database URL");
        assert!(url.ends_with("/rust_scratch_lockdemo"));
        let pool = sqlx::mysql::MySqlPoolOptions::new()
            .max_connections(6)
            .connect(&url)
            .await
            .unwrap();
        let state = AppState {
            config: Arc::new(crate::AppConfig::from_env().unwrap()),
            pool: Some(pool.clone()),
            database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
        };
        let suffix = uuid::Uuid::new_v4().to_string();
        let ids = [
            format!("000-{suffix}"),
            format!("aaa-{suffix}"),
            format!("bbb-{suffix}"),
        ];
        for id in &ids {
            sqlx::query("INSERT INTO t_grading_rule (id,type,title,score,status,is_default) VALUES (?,12345,'lock regression',100,0,1)")
                .bind(id).execute(&pool).await.unwrap();
        }
        for change in [false, true] {
            // One writer waits on the first sorted PK; the other waits on the
            // type's named lock without owning any rule record locks.
            let mut gate = pool.begin().await.unwrap();
            let connection: u64 = sqlx::query_scalar("SELECT CONNECTION_ID()")
                .fetch_one(&mut *gate)
                .await
                .unwrap();
            sqlx::query("SELECT id FROM t_grading_rule WHERE id=? FOR UPDATE")
                .bind(&ids[0])
                .fetch_one(&mut *gate)
                .await
                .unwrap();
            let first_state = state.clone();
            let first_id = ids[1].clone();
            let first = tokio::spawn(async move {
                save(
                    State(first_state),
                    Json(json!({"id":first_id,"type":12345,"score":100,"isDefault":0})),
                )
                .await
            });
            let second_state = state.clone();
            let second_id = ids[2].clone();
            let second = tokio::spawn(async move {
                if change {
                    change_default(State(second_state), Json(json!({"id":second_id}))).await
                } else {
                    save(
                        State(second_state),
                        Json(json!({"id":second_id,"type":12345,"score":100,"isDefault":0})),
                    )
                    .await
                }
            });
            let waiting = tokio::time::timeout(Duration::from_secs(5), async {
                loop {
                    let count: i64 = sqlx::query_scalar("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME='t_grading_rule'")
                        .bind(connection).fetch_one(&pool).await.unwrap();
                    if count == 1 { break; }
                    tokio::time::sleep(Duration::from_millis(10)).await;
                }
            }).await;
            gate.commit().await.unwrap();
            let (first, second) = tokio::time::timeout(Duration::from_secs(5), async {
                tokio::join!(first, second)
            })
            .await
            .expect("default operations must not deadlock or time out");
            assert!(
                waiting.is_ok(),
                "one serialized operation must reach the first PK gate"
            );
            assert_eq!(response_code(first.unwrap()).await, 200);
            assert_eq!(response_code(second.unwrap()).await, 200);
            let defaults: i64 = sqlx::query_scalar(
                "SELECT COUNT(*) FROM t_grading_rule WHERE type=12345 AND is_default=0",
            )
            .fetch_one(&pool)
            .await
            .unwrap();
            assert_eq!(
                defaults, 1,
                "concurrent default replacement must retain exactly one default"
            );
        }
        for id in &ids {
            sqlx::query("DELETE FROM t_grading_rule WHERE id=?")
                .bind(id)
                .execute(&pool)
                .await
                .unwrap();
        }
    }
}
