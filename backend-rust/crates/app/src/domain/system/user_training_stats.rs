use axum::{
    body::Bytes,
    extract::{Extension, State},
    http::{HeaderMap, StatusCode},
    response::{IntoResponse, Response},
    Json,
};
use chrono::NaiveDateTime;
use serde_json::{json, Number, Value};
use sqlx::{mysql::MySqlRow, MySqlPool, Row};
use std::{io, str::FromStr};

use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;

#[derive(Default)]
pub(super) struct TimeBounds {
    pub(super) start: Option<String>,
    pub(super) end: Option<String>,
}

// Java requires JSON media type but permits an empty/null body for an unbounded query.
// Invalid time strings are ignored after both LocalDateTime parsers fail.
pub(super) fn parse_bounds(body: Option<Json<Value>>) -> Result<TimeBounds, ()> {
    let Some(Json(value)) = body else {
        return Ok(TimeBounds::default());
    };
    let Some(object) = value.as_object() else {
        return if value.is_null() {
            Ok(TimeBounds::default())
        } else {
            Err(())
        };
    };
    Ok(TimeBounds {
        start: parse_time(object.get("startTime"), "startTime"),
        end: parse_time(object.get("endTime"), "endTime"),
    })
}

fn parse_time(value: Option<&Value>, name: &str) -> Option<String> {
    let Some(text) = value.and_then(Value::as_str) else {
        return None;
    };
    if text.is_empty() || text.chars().all(char::is_whitespace) {
        return None;
    }

    let parsed = NaiveDateTime::parse_from_str(text, "%Y-%m-%dT%H:%M:%S")
        .or_else(|_| NaiveDateTime::parse_from_str(text, "%Y-%m-%dT%H:%M:%S%.f"))
        .or_else(|_| NaiveDateTime::parse_from_str(text, "%Y-%m-%dT%H:%M"))
        .or_else(|_| NaiveDateTime::parse_from_str(text, "%Y-%m-%d %H:%M:%S"));
    match parsed {
        Ok(time) => Some(time.format("%Y-%m-%d %H:%M:%S%.6f").to_string()),
        Err(error) => {
            tracing::warn!(field = name, %error, "training statistic time filter could not be parsed; ignoring it");
            None
        }
    }
}

// The four optional binds keep the SQL comparison parameterized while retaining
// Java's "null bound means no filter" behavior.  MySQL performs the same
// DATETIME comparison as Hibernate for these canonical local date-time strings.
const FINISH_RANGE: &str =
    " AND (? IS NULL OR finish_time >= ?) AND (? IS NULL OR finish_time <= ?)";
const END_RANGE: &str = " AND (? IS NULL OR end_time >= ?) AND (? IS NULL OR end_time <= ?)";

fn bind_range<'a>(
    query: sqlx::query::Query<'a, sqlx::MySql, sqlx::mysql::MySqlArguments>,
    bounds: &'a TimeBounds,
) -> sqlx::query::Query<'a, sqlx::MySql, sqlx::mysql::MySqlArguments> {
    query
        .bind(bounds.start.as_deref())
        .bind(bounds.start.as_deref())
        .bind(bounds.end.as_deref())
        .bind(bounds.end.as_deref())
}

fn decode_error(message: impl Into<String>) -> sqlx::Error {
    sqlx::Error::Decode(Box::new(io::Error::new(
        io::ErrorKind::InvalidData,
        message.into(),
    )))
}

fn parse_duration_text(value: Option<String>, field: &str) -> i32 {
    let Some(value) = value else {
        return 0;
    };
    match value.parse::<i32>() {
        Ok(value) => value,
        Err(error) => {
            tracing::warn!(field, %error, "malformed training duration ignored as Java service does");
            0
        }
    }
}

fn add_wrapping(total: &mut i32, value: i32) {
    *total = total.wrapping_add(value);
}

async fn hand_key_duration(
    pool: &MySqlPool,
    user_id: &str,
    bounds: &TimeBounds,
) -> Result<i32, sqlx::Error> {
    let sql = format!(
        "SELECT CAST(COALESCE(SUM(TIMESTAMPDIFF(SECOND, create_time, finish_time)), 0) AS SIGNED) AS total \
         FROM general_ticker_pat_train_user \
         WHERE user_id=? AND role=0 AND is_finish=1 AND create_time IS NOT NULL AND finish_time IS NOT NULL{FINISH_RANGE}"
    );
    let row = bind_range(sqlx::query(&sql).bind(user_id), bounds)
        .fetch_one(pool)
        .await?;
    Ok(row.try_get::<i64, _>("total")? as i32)
}

async fn electronic_key_duration(
    pool: &MySqlPool,
    user_id: &str,
    bounds: &TimeBounds,
) -> Result<i32, sqlx::Error> {
    let sql = format!(
        "SELECT duration FROM general_key_pat_user \
         WHERE user_id=? AND role=0 AND is_finish=1 AND finish_time IS NOT NULL{FINISH_RANGE}"
    );
    let rows = bind_range(sqlx::query(&sql).bind(user_id), bounds)
        .fetch_all(pool)
        .await?;
    let mut total = 0;
    for row in rows {
        add_wrapping(
            &mut total,
            parse_duration_text(
                row.try_get::<Option<String>, _>("duration")?,
                "general_key_pat_user.duration",
            ),
        );
    }
    Ok(total)
}

async fn receive_duration(
    pool: &MySqlPool,
    user_id: &str,
    bounds: &TimeBounds,
) -> Result<i32, sqlx::Error> {
    let base_sql = format!(
        "SELECT valid_time FROM t_ticker_tape_train \
         WHERE user_id=? AND status=3 AND end_time IS NOT NULL{END_RANGE}"
    );
    let post_sql = format!(
        "SELECT valid_time FROM t_post_ticker_tape_train \
         WHERE user_id=? AND status>=2 AND end_time IS NOT NULL{END_RANGE}"
    );
    let base_rows = bind_range(sqlx::query(&base_sql).bind(user_id), bounds)
        .fetch_all(pool)
        .await?;
    let post_rows = bind_range(sqlx::query(&post_sql).bind(user_id), bounds)
        .fetch_all(pool)
        .await?;

    let mut total = 0;
    for row in base_rows {
        add_wrapping(
            &mut total,
            parse_duration_text(
                row.try_get::<Option<String>, _>("valid_time")?,
                "t_ticker_tape_train.valid_time",
            ),
        );
    }
    for row in post_rows {
        add_wrapping(
            &mut total,
            parse_duration_text(
                row.try_get::<Option<String>, _>("valid_time")?,
                "t_post_ticker_tape_train.valid_time",
            ),
        );
    }
    Ok(total)
}

async fn general_telex_duration(
    pool: &MySqlPool,
    user_id: &str,
    bounds: &TimeBounds,
) -> Result<i32, sqlx::Error> {
    let sql = format!(
        "SELECT valid_time FROM general_telex_pat_user \
         WHERE user_id=? AND role=0 AND is_finish=1 AND finish_time IS NOT NULL{FINISH_RANGE}"
    );
    let rows = bind_range(sqlx::query(&sql).bind(user_id), bounds)
        .fetch_all(pool)
        .await?;
    let mut total = 0;
    for row in rows {
        add_wrapping(
            &mut total,
            row.try_get::<Option<i32>, _>("valid_time")?.unwrap_or(0),
        );
    }
    Ok(total)
}

async fn entering_duration(
    pool: &MySqlPool,
    user_id: &str,
    bounds: &TimeBounds,
    kind: i32,
) -> Result<i32, sqlx::Error> {
    let sql = format!(
        "SELECT duration FROM t_post_entering_exercise \
         WHERE create_user_id=? AND type=? AND status=2 AND end_time IS NOT NULL{END_RANGE}"
    );
    let rows = bind_range(sqlx::query(&sql).bind(user_id).bind(kind), bounds)
        .fetch_all(pool)
        .await?;
    let mut total = 0;
    for row in rows {
        add_wrapping(
            &mut total,
            row.try_get::<Option<i32>, _>("duration")?.unwrap_or(0),
        );
    }
    Ok(total)
}

async fn duration_data(
    pool: &MySqlPool,
    user_id: &str,
    bounds: &TimeBounds,
) -> Result<Value, sqlx::Error> {
    let hand_key = hand_key_duration(pool, user_id, bounds).await?;
    let electronic_key = electronic_key_duration(pool, user_id, bounds).await?;
    let receive = receive_duration(pool, user_id, bounds).await?;
    let datagram = post_telex_duration_for_type(pool, user_id, bounds, 1).await?;
    let telex = post_telex_duration_for_type(pool, user_id, bounds, 0)
        .await?
        .wrapping_add(general_telex_duration(pool, user_id, bounds).await?);
    let pinyin = entering_duration(pool, user_id, bounds, 0).await?;
    let wubi = entering_duration(pool, user_id, bounds, 2)
        .await?
        .wrapping_add(entering_duration(pool, user_id, bounds, 3).await?);
    let english = entering_duration(pool, user_id, bounds, 4).await?;
    Ok(json!({
        "handKeyDuration": hand_key,
        "electronicKeyDuration": electronic_key,
        "receiveDuration": receive,
        "datagramDuration": datagram,
        "telexDuration": telex,
        "pinyinDuration": pinyin,
        "wubiDuration": wubi,
        "englishDuration": english,
    }))
}

async fn post_telex_duration_for_type(
    pool: &MySqlPool,
    user_id: &str,
    bounds: &TimeBounds,
    train_type: i32,
) -> Result<i32, sqlx::Error> {
    let sql = format!(
        "SELECT valid_time FROM t_post_telex_pat_train \
         WHERE create_user=? AND train_type=? AND status=3 AND end_time IS NOT NULL{END_RANGE}"
    );
    let rows = bind_range(sqlx::query(&sql).bind(user_id).bind(train_type), bounds)
        .fetch_all(pool)
        .await?;
    let mut total = 0;
    for row in rows {
        add_wrapping(
            &mut total,
            row.try_get::<Option<i32>, _>("valid_time")?.unwrap_or(0),
        );
    }
    Ok(total)
}

fn score_value(row: &MySqlRow) -> Result<Value, sqlx::Error> {
    let score = row.try_get::<Option<String>, _>("score")?;
    match score {
        None => Ok(Value::Null),
        Some(value) => Number::from_str(&value)
            .map(Value::Number)
            .map_err(|error| decode_error(format!("score is not a valid decimal: {error}"))),
    }
}

fn recent_value(row: &MySqlRow, require_start_time: bool) -> Result<Value, sqlx::Error> {
    let start_time = row.try_get::<Option<String>, _>("start_time_text")?;
    if require_start_time && start_time.is_none() {
        return Err(decode_error(
            "hand-key result has neither finish nor create time",
        ));
    }
    let train_time = row.try_get::<i64, _>("train_time")? as i32;
    Ok(json!({
        "startTime": start_time,
        "trainTime": train_time,
        "score": score_value(row)?,
        "speed": row.try_get::<Option<String>, _>("speed")?,
    }))
}

async fn recent_hand_key_data(pool: &MySqlPool, user_id: &str) -> Result<Value, sqlx::Error> {
    let rows = sqlx::query(
        "SELECT DATE_FORMAT(COALESCE(finish_time, create_time), '%Y-%m-%d %H:%i:%s') AS start_time_text, \
                CASE WHEN create_time IS NOT NULL AND finish_time IS NOT NULL \
                     THEN CAST(TIMESTAMPDIFF(SECOND, create_time, finish_time) AS SIGNED) ELSE 0 END AS train_time, \
                CAST(score AS CHAR) AS score, speed \
         FROM general_ticker_pat_train_user \
         WHERE user_id=? AND role=0 AND is_finish=1 \
         ORDER BY finish_time DESC LIMIT 10",
    )
    .bind(user_id)
    .fetch_all(pool)
    .await?;
    rows.iter()
        .map(|row| recent_value(row, true))
        .collect::<Result<Vec<_>, _>>()
        .map(Value::Array)
}

async fn recent_electronic_key_data(pool: &MySqlPool, user_id: &str) -> Result<Value, sqlx::Error> {
    let rows = sqlx::query(
        "SELECT DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS start_time_text, \
                CASE WHEN create_time IS NOT NULL AND finish_time IS NOT NULL \
                     THEN CAST(TIMESTAMPDIFF(SECOND, create_time, finish_time) AS SIGNED) ELSE 0 END AS train_time, \
                CAST(score AS CHAR) AS score, speed \
         FROM general_key_pat_user \
         WHERE user_id=? AND role=0 AND is_finish=1 \
         ORDER BY finish_time DESC LIMIT 10",
    )
    .bind(user_id)
    .fetch_all(pool)
    .await?;
    rows.iter()
        .map(|row| recent_value(row, false))
        .collect::<Result<Vec<_>, _>>()
        .map(Value::Array)
}

fn database_error(error: sqlx::Error, operation: &str) -> Response {
    tracing::warn!(%error, operation, "user training statistics database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

pub(super) async fn get_user_train_duration_stat(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    headers: HeaderMap,
    bytes: Bytes,
) -> Response {
    let json_media_type = headers
        .get("content-type")
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.split(';').next())
        .is_some_and(|value| {
            let value = value.trim();
            value.eq_ignore_ascii_case("application/json")
                || (value.starts_with("application/") && value.ends_with("+json"))
        });
    if !json_media_type {
        return StatusCode::UNSUPPORTED_MEDIA_TYPE.into_response();
    }
    let body = if bytes.is_empty() {
        None
    } else {
        match serde_json::from_slice::<Value>(&bytes) {
            Ok(value) => Some(Json(value)),
            Err(_) => return StatusCode::BAD_REQUEST.into_response(),
        }
    };
    let bounds = match parse_bounds(body) {
        Ok(bounds) => bounds,
        Err(()) => return StatusCode::BAD_REQUEST.into_response(),
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match duration_data(pool, &user.id, &bounds).await {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(error) => database_error(error, "user training duration statistics"),
    }
}

pub(super) async fn get_recent_hand_key_trains(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match recent_hand_key_data(pool, &user.id).await {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(error) => database_error(error, "recent hand-key training statistics"),
    }
}

pub(super) async fn get_recent_electronic_key_trains(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match recent_electronic_key_data(pool, &user.id).await {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(error) => database_error(error, "recent electronic-key training statistics"),
    }
}
