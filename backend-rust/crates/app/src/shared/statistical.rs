//! Per-user statistics rows for the personal telex and ticker-tape pages.
//!
//! Java creates the missing type rows on the first read (UUID id, count 0, time
//! "0", avgSpeed BigDecimal.ZERO) and then returns every row ordered by type with
//! avgSpeed as a JSON number. Telegram, entering and synthetical implement the
//! same contract in their own modules.

use rust_decimal::Decimal;
use serde_json::{json, Value};
use sqlx::{MySql, MySqlPool, Row, Transaction};
use uuid::Uuid;

fn decimal_number(value: Option<Decimal>) -> Value {
    value
        .and_then(|value| value.to_string().parse::<serde_json::Number>().ok())
        .map(Value::Number)
        .unwrap_or(Value::Null)
}

/// Locks the user row to serialize one user's writes that lazily create or recount statistics
/// rows. The training and statistics tables are indexed by primary key only, so a locking read
/// filtered by `user_id` would lock every row it scans and stall other users until commit: take
/// this lock first, read the rest plainly, and lock only the rows acted on by primary key.
/// Always take it before any training row lock so the order is the same everywhere. `false`
/// means the user row does not exist.
pub(crate) async fn lock_user(
    transaction: &mut Transaction<'_, MySql>,
    user_id: &str,
) -> Result<bool, sqlx::Error> {
    sqlx::query("SELECT id FROM t_user WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(user_id)
        .fetch_optional(&mut **transaction)
        .await
        .map(|row| row.is_some())
}

/// Loads the user's rows from `table`, creating any missing type in `0..types`.
///
/// `table` is interpolated into SQL and must be a trusted constant.
pub(crate) async fn load_or_create(
    pool: &MySqlPool,
    table: &'static str,
    user_id: &str,
    types: i32,
) -> Result<Vec<Value>, sqlx::Error> {
    let mut transaction = pool.begin().await?;
    // The rows may not exist yet, so lock the user to serialize lazy creation. The rows are then
    // read plainly: a locking read on the unindexed `user_id` filter would lock every row scanned.
    sqlx::query("SELECT id FROM t_user WHERE id=? FOR UPDATE")
        .bind(user_id)
        .fetch_one(&mut *transaction)
        .await?;
    let rows = sqlx::query(&format!(
        "SELECT id,type,total_count,total_time,avg_speed,user_id FROM {table} WHERE user_id=? ORDER BY type"
    ))
    .bind(user_id)
    .fetch_all(&mut *transaction)
    .await?;

    let mut present = Vec::with_capacity(rows.len());
    let mut result = Vec::with_capacity(rows.len().max(types as usize));
    for row in &rows {
        let kind = row.try_get::<Option<i32>, _>("type")?;
        present.extend(kind);
        result.push(json!({
            "id": row.try_get::<String, _>("id")?,
            "type": kind,
            "totalCount": row.try_get::<Option<i32>, _>("total_count")?,
            "totalTime": row.try_get::<Option<String>, _>("total_time")?,
            "avgSpeed": decimal_number(row.try_get::<Option<Decimal>, _>("avg_speed")?),
            "userId": row.try_get::<Option<String>, _>("user_id")?,
        }));
    }
    for kind in (0..types).filter(|kind| !present.contains(kind)) {
        let id = Uuid::new_v4().to_string();
        sqlx::query(&format!(
            "INSERT INTO {table} (id,type,total_count,total_time,avg_speed,user_id) VALUES (?,?,0,'0',0,?)"
        ))
        .bind(&id)
        .bind(kind)
        .bind(user_id)
        .execute(&mut *transaction)
        .await?;
        result.push(json!({
            "id": id,
            "type": kind,
            "totalCount": 0,
            "totalTime": "0",
            "avgSpeed": 0,
            "userId": user_id,
        }));
    }
    transaction.commit().await?;
    result.sort_by_key(|row| row["type"].as_i64().unwrap_or(i64::MAX));
    Ok(result)
}
