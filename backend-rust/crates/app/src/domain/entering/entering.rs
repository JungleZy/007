use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use rust_decimal::Decimal;
use serde_json::{json, Value};
use sqlx::Row;
use uuid::Uuid;

macro_rules! exercise_query {
    ($suffix:literal) => {
        sqlx::query(concat!(
            "SELECT id,type,create_user_id,accuracy,speed,content,source_content,duration,status,name,correct_num,error_num,protocol_version,elapsed_millis,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') start_time_text,DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') end_time_text,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') create_time_text,CAST(TIMESTAMPDIFF(MICROSECOND,active_started_at,NOW(6)) DIV 1000 AS SIGNED) active_elapsed FROM t_entering_exercise",
            $suffix
        ))
    };
}

#[derive(Debug)]
pub(super) enum ExerciseError {
    Parameter(String),
    Terminal(&'static str),
    Forbidden,
    Database(sqlx::Error),
    Json(serde_json::Error),
    Request(Response),
    Internal(&'static str),
}
impl From<sqlx::Error> for ExerciseError {
    fn from(error: sqlx::Error) -> Self {
        Self::Database(error)
    }
}
impl From<serde_json::Error> for ExerciseError {
    fn from(error: serde_json::Error) -> Self {
        Self::Json(error)
    }
}
impl From<Response> for ExerciseError {
    fn from(response: Response) -> Self {
        Self::Request(response)
    }
}
fn exercise_response(result: Result<Value, ExerciseError>) -> Response {
    match result {
        Ok(value) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Err(ExerciseError::Parameter(message)) => {
            envelope_response(StatusCode::OK, 202, &message, &message)
        }
        Err(ExerciseError::Terminal(message)) => {
            envelope_response(StatusCode::OK, 208, message, message)
        }
        Err(ExerciseError::Forbidden) => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Err(ExerciseError::Request(response)) => response,
        Err(error) => {
            match error {
                ExerciseError::Database(error) => {
                    tracing::error!(%error, "entering lifecycle database failure")
                }
                ExerciseError::Json(error) => {
                    tracing::error!(%error, "entering lifecycle serialization failure")
                }
                ExerciseError::Internal(reason) => {
                    tracing::error!(reason, "entering lifecycle invariant failure")
                }
                _ => unreachable!(),
            }
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}
fn exercise_pool(state: &AppState) -> Result<&sqlx::MySqlPool, ExerciseError> {
    state
        .pool
        .as_ref()
        .ok_or(ExerciseError::Internal("database unavailable"))
}
fn exercise_body(body: &Value) -> Result<(), ExerciseError> {
    if body.is_null() {
        return Err(ExerciseError::Internal("request body is null"));
    }
    if !body.is_object() {
        return Err(ExerciseError::Request(
            StatusCode::BAD_REQUEST.into_response(),
        ));
    }
    Ok(())
}
fn exercise_id(body: &Value) -> Result<std::borrow::Cow<'_, str>, ExerciseError> {
    exercise_body(body)?;
    request_string(body.get("id"))?
        .ok_or_else(|| ExerciseError::Parameter("id to load is required for loading".to_owned()))
}
fn require_exercise_owner(
    row: &sqlx::mysql::MySqlRow,
    user: &AuthUser,
) -> Result<(), ExerciseError> {
    if row.try_get::<Option<&str>, _>("create_user_id")? != Some(user.id.as_str()) {
        return Err(ExerciseError::Forbidden);
    }
    Ok(())
}
fn require_exercise_protocol(row: &sqlx::mysql::MySqlRow) -> Result<(), ExerciseError> {
    if row.try_get::<Option<i32>, _>("protocol_version")? != Some(1) {
        return Err(ExerciseError::Terminal(
            "旧版练习仅可查看已完成记录，请重新创建训练",
        ));
    }
    Ok(())
}
fn exercise_elapsed(row: &sqlx::mysql::MySqlRow) -> Result<i64, ExerciseError> {
    let accumulated = row
        .try_get::<Option<i64>, _>("elapsed_millis")?
        .unwrap_or(0);
    let active = if row.try_get::<Option<i32>, _>("status")? == Some(1) {
        row.try_get::<Option<i64>, _>("active_elapsed")?
            .ok_or_else(|| ExerciseError::Parameter("活动训练缺少计时起点".to_owned()))?
            .max(0)
    } else {
        0
    };
    accumulated
        .checked_add(active)
        .ok_or(ExerciseError::Internal("elapsed time overflow"))
}
fn exercise_json(row: &sqlx::mysql::MySqlRow, live_duration: bool) -> Result<Value, ExerciseError> {
    let duration = if live_duration && row.try_get::<Option<i32>, _>("protocol_version")? == Some(1)
    {
        Some((exercise_elapsed(row)? / 1000).min(i64::from(i32::MAX)) as i32)
    } else {
        row.try_get::<Option<i32>, _>("duration")?
    };
    Ok(json!({
        "id":row.try_get::<&str,_>("id")?,
        "type":row.try_get::<Option<i32>,_>("type")?,
        "startTime":row.try_get::<Option<&str>,_>("start_time_text")?,
        "endTime":row.try_get::<Option<&str>,_>("end_time_text")?,
        "createTime":row.try_get::<Option<&str>,_>("create_time_text")?,
        "accuracy":row.try_get::<Option<f64>,_>("accuracy")?,
        "speed":row.try_get::<Option<i32>,_>("speed")?,
        "content":row.try_get::<Option<&str>,_>("content")?,
        "duration":duration,
        "status":row.try_get::<Option<i32>,_>("status")?,
        "name":row.try_get::<Option<&str>,_>("name")?,
        "correctNum":row.try_get::<Option<i32>,_>("correct_num")?,
        "errorNum":row.try_get::<Option<i32>,_>("error_num")?,
        "protocolVersion":row.try_get::<Option<i32>,_>("protocol_version")?
    }))
}
async fn locked_exercise(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    id: &str,
    user: &AuthUser,
) -> Result<sqlx::mysql::MySqlRow, ExerciseError> {
    // Same owner lock as lazy statistics creation; serialize settlement and totals.
    sqlx::query("SELECT id FROM t_user WHERE id=? FOR UPDATE")
        .bind(&user.id)
        .fetch_one(&mut **tx)
        .await?;
    let row = exercise_query!(" WHERE id=? FOR UPDATE")
        .bind(id)
        .fetch_optional(&mut **tx)
        .await?
        .ok_or_else(|| ExerciseError::Parameter("未查询到该训练".to_owned()))?;
    require_exercise_owner(&row, user)?;
    Ok(row)
}
async fn finish_exercise_transaction(
    tx: sqlx::Transaction<'_, sqlx::MySql>,
    result: Result<Value, ExerciseError>,
) -> Result<Value, ExerciseError> {
    match result {
        Ok(value) => {
            tx.commit().await?;
            Ok(value)
        }
        Err(error) => {
            tx.rollback().await?;
            Err(error)
        }
    }
}

pub(super) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    exercise_response(
        async {
            exercise_body(&body)?;
            let kind = request_integer(body.get("type"))?
                .ok_or(ExerciseError::Internal("list type is null"))?;
            let query = if kind == 0 {
                exercise_query!(" WHERE type<=? AND create_user_id=? ORDER BY create_time DESC")
            } else {
                exercise_query!(" WHERE type>? AND create_user_id=? ORDER BY create_time DESC")
            };
            let rows = query
                .bind(kind)
                .bind(&user.id)
                .fetch_all(exercise_pool(&state)?)
                .await?;
            rows.iter()
                .map(|row| exercise_json(row, false))
                .collect::<Result<Vec<_>, _>>()
                .map(Value::Array)
        }
        .await,
    )
}

pub(super) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    exercise_response(async {
        exercise_body(&body)?;
        let kind = request_integer(body.get("type"))?
            .ok_or_else(|| ExerciseError::Parameter("训练类型不能为空".to_owned()))?;
        let name = request_string(body.get("name"))?;
        let mut tx = exercise_pool(&state)?.begin().await?;
        let result = async {
            let stock = sqlx::query_scalar::<_, Option<String>>("SELECT content FROM t_entering_exercise_word_stock WHERE type=? LIMIT 1")
                .bind(if kind == 9 { 2 } else { kind }).fetch_optional(&mut *tx).await?.flatten();
            let source = crate::domain::entering::entering_codebook::freeze(kind, stock.as_deref()).map_err(ExerciseError::Parameter)?;
            let captured = crate::domain::entering::entering_codebook::empty_capture(&source);
            let frozen = crate::domain::entering::entering_codebook::serialize_source(&source)?;
            let id = Uuid::new_v4().to_string();
            sqlx::query("INSERT INTO t_entering_exercise (id,type,name,create_user_id,status,speed,accuracy,duration,correct_num,error_num,protocol_version,content,source_content,elapsed_millis,create_time) VALUES (?,?,?,?,0,0,0,0,0,0,1,?,?,0,NOW())")
                .bind(&id).bind(kind).bind(name.as_deref()).bind(&user.id).bind(captured.content).bind(frozen)
                .execute(&mut *tx).await?;
            let row = exercise_query!(" WHERE id=?").bind(&id).fetch_one(&mut *tx).await?;
            exercise_json(&row, false)
        }.await;
        finish_exercise_transaction(tx, result).await
    }.await)
}

pub(super) async fn by_id(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    exercise_response(
        async {
            let id = exercise_id(&body)?;
            let row = exercise_query!(" WHERE id=?")
                .bind(id.as_ref())
                .fetch_optional(exercise_pool(&state)?)
                .await?
                .ok_or_else(|| ExerciseError::Parameter("未查询到该训练".to_owned()))?;
            require_exercise_owner(&row, &user)?;
            if row.try_get::<Option<i32>, _>("status")? != Some(2) {
                require_exercise_protocol(&row)?;
            }
            exercise_json(&row, true)
        }
        .await,
    )
}

pub(super) async fn last_train(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    exercise_response(
        async {
            exercise_body(&body)?;
            let kind = request_integer(body.get("type"))?;
            let row = exercise_query!(
                " WHERE create_user_id=? AND type=? ORDER BY create_time DESC LIMIT 1"
            )
            .bind(&user.id)
            .bind(kind)
            .fetch_optional(exercise_pool(&state)?)
            .await?;
            row.as_ref()
                .map(|row| exercise_json(row, false))
                .transpose()
                .map(|value| value.unwrap_or(Value::Null))
        }
        .await,
    )
}

pub(super) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    start_exercise(state, user, body, false).await
}
pub(super) async fn go_to(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    start_exercise(state, user, body, true).await
}
async fn start_exercise(state: AppState, user: AuthUser, body: Value, resume: bool) -> Response {
    exercise_response(async {
        let id = exercise_id(&body)?;
        let mut tx = exercise_pool(&state)?.begin().await?;
        let result = async {
            let row = locked_exercise(&mut tx, &id, &user).await?;
            require_exercise_protocol(&row)?;
            let status = row.try_get::<Option<i32>, _>("status")?;
            if status == Some(2) { return Err(ExerciseError::Terminal("训练已结束")); }
            if status == Some(1) { return Ok(Value::Null); }
            if status != Some(if resume { 3 } else { 0 }) {
                return Err(ExerciseError::Parameter(if resume { "训练不处于暂停状态" } else { "请继续已开始的训练" }.to_owned()));
            }
            sqlx::query("UPDATE t_entering_exercise SET start_time=COALESCE(start_time,NOW()),active_started_at=NOW(6),status=1 WHERE id=?")
                .bind(id.as_ref()).execute(&mut *tx).await?;
            Ok(Value::Null)
        }.await;
        finish_exercise_transaction(tx, result).await
    }.await)
}

pub(super) async fn pause(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    save_exercise_answers(state, user, body, false).await
}
pub(super) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    save_exercise_answers(state, user, body, true).await
}
fn validate_exercise_previews(body: &Value) -> Result<(), ExerciseError> {
    exercise_body(body)?;
    for field in ["speed", "duration", "correctNum", "errorNum"] {
        request_integer(body.get(field))?;
    }
    request_double(body.get("accuracy"))?;
    Ok(())
}

async fn save_exercise_answers(
    state: AppState,
    user: AuthUser,
    body: Value,
    finish: bool,
) -> Response {
    exercise_response(async {
        validate_exercise_previews(&body)?;
        let id = exercise_id(&body)?;
        let content = request_string(body.get("content"))?;
        let mut tx = exercise_pool(&state)?.begin().await?;
        let result = async {
            let row = locked_exercise(&mut tx, &id, &user).await?;
            require_exercise_protocol(&row)?;
            let status = row.try_get::<Option<i32>, _>("status")?;
            let captured = || {
                let source = crate::domain::entering::entering_codebook::source(row.try_get::<Option<&str>, _>("source_content")?)
                    .map_err(ExerciseError::Parameter)?;
                crate::domain::entering::entering_codebook::capture(&source, content.as_deref()).map_err(ExerciseError::Parameter)
            };
            if status == Some(2) {
                if finish {
                    match captured() {
                        Ok(retry) if Some(retry.content.as_str()) == row.try_get::<Option<&str>, _>("content")? => return exercise_json(&row, false),
                        Ok(_) | Err(ExerciseError::Parameter(_)) => {},
                        Err(error) => return Err(error),
                    }
                }
                return Err(ExerciseError::Terminal("训练已结束，不能修改结果"));
            }
            let captured = captured()?;
            if !matches!(status, Some(1 | 3)) { return Err(ExerciseError::Parameter("训练尚未开始".to_owned())); }
            if status == Some(3) && Some(captured.content.as_str()) != row.try_get::<Option<&str>, _>("content")? {
                return Err(ExerciseError::Parameter("暂停期间不能修改录入内容，请先继续训练".to_owned()));
            }
            let elapsed = exercise_elapsed(&row)?;
            let answered = captured.correct + captured.errors;
            let accuracy = if answered == 0 { 0.0 } else { (f64::from(captured.correct) * 10000.0 / f64::from(answered)).round() / 100.0 };
            let speed = (i64::from(answered) * 60000 / elapsed.max(1)).min(i64::from(i32::MAX)) as i32;
            let duration = (elapsed / 1000).min(i64::from(i32::MAX)) as i32;
            sqlx::query("UPDATE t_entering_exercise SET elapsed_millis=?,active_started_at=NULL,duration=?,correct_num=?,error_num=?,accuracy=?,speed=?,content=?,status=?,end_time=IF(?,NOW(),end_time) WHERE id=?")
                .bind(elapsed).bind(duration).bind(captured.correct).bind(captured.errors).bind(accuracy).bind(speed)
                .bind(captured.content).bind(if finish { 2 } else { 3 }).bind(finish).bind(id.as_ref()).execute(&mut *tx).await?;
            if finish {
                let kind = row.try_get::<Option<i32>, _>("type")?.ok_or(ExerciseError::Internal("finished exercise has no type"))?;
                update_exercise_statistics(&mut tx, &user.id, kind).await?;
            }
            let updated = exercise_query!(" WHERE id=?").bind(id.as_ref()).fetch_one(&mut *tx).await?;
            exercise_json(&updated, false)
        }.await;
        finish_exercise_transaction(tx, result).await
    }.await)
}

pub(super) async fn update_exercise_statistics(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    user_id: &str,
    kind: i32,
) -> Result<(), ExerciseError> {
    let aggregate = sqlx::query("SELECT CAST(COALESCE(SUM(duration),0) AS CHAR) total_time,COUNT(id) total_count,COALESCE(AVG(speed),0.00) avg_speed FROM t_entering_exercise WHERE create_user_id=? AND type=? AND status=2")
        .bind(user_id).bind(kind).fetch_one(&mut **tx).await?;
    let total_time = aggregate.try_get::<String, _>("total_time")?;
    let total_count = i32::try_from(aggregate.try_get::<i64, _>("total_count")?)
        .map_err(|_| ExerciseError::Internal("statistics count overflow"))?;
    let avg_speed = aggregate.try_get::<Decimal, _>("avg_speed")?;
    let (group, child) = if kind == 9 { (1, 3) } else { (0, kind) };
    // The caller holds the user row lock; a plain read is enough and a non-PK `FOR UPDATE` would
    // lock every scanned row of the table (no index on user_id).
    let existing = sqlx::query_scalar::<_, String>(
        "SELECT id FROM t_entering_statistical WHERE user_id=? AND type=? AND child_type=? LIMIT 1",
    )
    .bind(user_id)
    .bind(group)
    .bind(child)
    .fetch_optional(&mut **tx)
    .await?;
    if let Some(id) = existing {
        sqlx::query(
            "UPDATE t_entering_statistical SET total_count=?,total_time=?,avg_speed=? WHERE id=?",
        )
        .bind(total_count)
        .bind(total_time)
        .bind(avg_speed)
        .bind(id)
        .execute(&mut **tx)
        .await?;
    } else {
        sqlx::query("INSERT INTO t_entering_statistical(id,user_id,type,child_type,total_count,total_time,avg_speed) VALUES(?,?,?,?,?,?,?)")
            .bind(Uuid::new_v4().to_string()).bind(user_id).bind(group).bind(child).bind(total_count).bind(total_time).bind(avg_speed)
            .execute(&mut **tx).await?;
    }
    Ok(())
}
/// The `{type}` property of EnteringTelexPatQueryParam / EnteringTelexPatSaveParam: Jackson
/// coerces numeric strings and floats, an absent or null type stays null, anything else is a 400.
pub(super) fn telex_type(body: &Value) -> Result<Option<i32>, Response> {
    if !body.is_object() {
        return Err(StatusCode::BAD_REQUEST.into_response());
    }
    request_integer(body.get("type"))
}

fn telex_database_error(error: sqlx::Error) -> Response {
    tracing::error!(%error, "personal entering telex failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

/// Java answers ResponseResult.error(message), i.e. HTTP 200 with code 500 (SYSTEM_ERROR).
fn telex_rejected(message: &str) -> Response {
    envelope_response(StatusCode::OK, 500, message, "")
}

/// EnteringTelexPatVO of a stored record.
fn telex_row_value(row: &sqlx::mysql::MySqlRow) -> Result<Value, sqlx::Error> {
    Ok(json!({
        "id": row.try_get::<String, _>("id")?,
        "createUserId": row.try_get::<Option<String>, _>("create_user_id")?,
        "type": row.try_get::<Option<i32>, _>("type")?,
        "totalTime": row.try_get::<Option<i32>, _>("total_time")?,
        "totalNum": row.try_get::<Option<i32>, _>("total_num")?,
        "totalError": row.try_get::<Option<i32>, _>("total_error")?,
        "messageName": row.try_get::<Option<String>, _>("message_name")?,
    }))
}

/// What Java returns for a user without a record of this type: zero totals, no id.
fn telex_empty_value(kind: Option<i32>) -> Value {
    json!({
        "id": null,
        "createUserId": null,
        "type": kind,
        "totalTime": 0,
        "totalNum": 0,
        "totalError": 0,
        "messageName": null,
    })
}

const TELEX_COLUMNS: &str =
    "SELECT id,create_user_id,type,total_time,total_num,total_error,message_name FROM t_entering_telex_pat";

pub(super) async fn telex_find(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let kind = match telex_type(&body) {
        Ok(kind) => kind,
        Err(response) => return response,
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let value = async {
        // `type = NULL` matches nothing in Java either.
        let Some(kind) = kind else {
            return Ok(telex_empty_value(None));
        };
        let sql = format!("{TELEX_COLUMNS} WHERE create_user_id=? AND type=? LIMIT 1");
        match sqlx::query(&sql)
            .bind(&user.id)
            .bind(kind)
            .fetch_optional(pool)
            .await?
        {
            Some(row) => telex_row_value(&row),
            None => Ok(telex_empty_value(Some(kind))),
        }
    }
    .await;
    match value {
        Ok(value) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Err(error) => telex_database_error(error),
    }
}
pub(super) async fn statistics(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    statistics_for(&state, &user, 0).await
}

pub(super) async fn telex_statistics(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    statistics_for(&state, &user, 1).await
}

pub(super) async fn statistics_for(state: &AppState, user: &AuthUser, kind: i32) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result: Result<Value, sqlx::Error> = async {
        let mut transaction = pool.begin().await?;
        // Lock the user even when no statistics exist yet, serializing lazy creation. The user
        // row lock is the only lock needed: every statistics writer takes it first, and a
        // non-PK `FOR UPDATE` on this index-less table would lock every scanned row.
        if !crate::shared::statistical::lock_user(&mut transaction, &user.id).await? {
            return Err(sqlx::Error::RowNotFound);
        }
        let existing = sqlx::query("SELECT child_type FROM t_entering_statistical WHERE user_id=? AND type=?")
            .bind(&user.id)
            .bind(kind)
            .fetch_all(&mut *transaction)
            .await?;
        let child_types = existing.iter()
            .map(|row| row.try_get::<Option<i32>, _>("child_type"))
            .collect::<Result<Vec<_>, _>>()?;
        for child_type in 0..if kind == 0 { 3 } else { 4 } {
            if !child_types.contains(&Some(child_type)) {
                sqlx::query("INSERT INTO t_entering_statistical (id,type,child_type,total_count,total_time,avg_speed,user_id) VALUES (?,?,?,0,'0',0,?)")
                    .bind(Uuid::new_v4().to_string())
                    .bind(kind)
                    .bind(child_type)
                    .bind(&user.id)
                    .execute(&mut *transaction)
                    .await?;
            }
        }
        let rows = sqlx::query("SELECT id,child_type,total_count,total_time,avg_speed,user_id FROM t_entering_statistical WHERE user_id=? AND type=? ORDER BY child_type")
            .bind(&user.id)
            .bind(kind)
            .fetch_all(&mut *transaction)
            .await?;
        let mut values = Vec::with_capacity(rows.len());
        for row in rows {
            // Older Java saves could persist an untyped row. It is not a display category.
            let Some(child_type) = row.try_get::<Option<i32>, _>("child_type")? else {
                continue;
            };
            let avg_speed = row.try_get::<Option<Decimal>, _>("avg_speed")?
                .map(|value| value.to_string().parse::<serde_json::Number>())
                .transpose()
                .map_err(|error| sqlx::Error::Decode(Box::new(error)))?;
            values.push(json!({
                "id": row.try_get::<String, _>("id")?,
                "type": child_type,
                "totalCount": row.try_get::<Option<i32>, _>("total_count")?,
                "totalTime": row.try_get::<Option<String>, _>("total_time")?,
                "avgSpeed": avg_speed,
                "userId": row.try_get::<Option<String>, _>("user_id")?,
            }));
        }
        transaction.commit().await?;
        Ok(Value::Array(values))
    }.await;
    match result {
        Ok(value) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Err(error) => {
            tracing::error!(%error, kind, "entering statistics failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}
pub(super) async fn keypoints(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    if body.is_null() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if !body.is_object() {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let kind = match request_integer(body.get("type")) {
        Ok(Some(kind)) => kind,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "请求参数错误", ""),
        Err(response) => return response,
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,content FROM t_entering_key_points WHERE type=? LIMIT 1")
        .bind(kind)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(
            StatusCode::OK,
            200,
            Some(
                json!({"id":row.get::<String,_>("id"),"type":row.get::<Option<i32>,_>("type"),"content":row.get::<Option<String>,_>("content")}),
            ),
            "ok",
            "",
        ),
        Ok(None) => envelope_json(StatusCode::OK, 200, None, "ok", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}
/// Statistics figures a telex save records: total time as text and the rate per minute.
/// Totals are nullable Integers in Java and count as 0 here (ScoreMath.rate, HALF_UP, scale 0).
pub(super) fn telex_statistics_figures(
    total_time: Option<i32>,
    total_num: Option<i32>,
) -> (String, Decimal) {
    let seconds = i64::from(total_time.unwrap_or(0));
    let count = i64::from(total_num.unwrap_or(0));
    let millis = seconds * 1000;
    let rate = if count <= 0 || millis <= 0 {
        0
    } else {
        (count * 60_000 * 2 + millis) / (millis * 2)
    };
    (seconds.to_string(), Decimal::from(rate))
}

pub(super) struct TelexSave<'a> {
    pub(super) id: Option<std::borrow::Cow<'a, str>>,
    pub(super) kind: i32,
    pub(super) total_time: Option<i32>,
    pub(super) total_num: Option<i32>,
    pub(super) total_error: Option<i32>,
    pub(super) message_name: Option<std::borrow::Cow<'a, str>>,
}

pub(super) async fn record_telex_statistics(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    user_id: &str,
    save: &TelexSave<'_>,
) -> Result<(), sqlx::Error> {
    let existing = sqlx::query("SELECT id,total_count FROM t_entering_statistical WHERE user_id=? AND type=1 AND child_type=? LIMIT 1")
        .bind(user_id)
        .bind(save.kind)
        .fetch_optional(&mut **tx)
        .await?;
    let (total_time, avg_speed) = telex_statistics_figures(save.total_time, save.total_num);
    match existing {
        Some(row) => {
            let count = row.try_get::<Option<i32>, _>("total_count")?.unwrap_or(0);
            sqlx::query("UPDATE t_entering_statistical SET total_count=?,total_time=?,avg_speed=? WHERE id=?")
                .bind(count.saturating_add(1))
                .bind(total_time)
                .bind(avg_speed)
                .bind(row.try_get::<String, _>("id")?)
                .execute(&mut **tx)
                .await?;
        }
        None => {
            sqlx::query("INSERT INTO t_entering_statistical (id,type,child_type,total_count,total_time,avg_speed,user_id) VALUES (?,1,?,1,?,?,?)")
                .bind(Uuid::new_v4().to_string())
                .bind(save.kind)
                .bind(total_time)
                .bind(avg_speed)
                .bind(user_id)
                .execute(&mut **tx)
                .await?;
        }
    }
    Ok(())
}

async fn save_telex(
    pool: &sqlx::MySqlPool,
    user: &AuthUser,
    save: &TelexSave<'_>,
) -> Result<Value, Response> {
    let mut tx = pool.begin().await.map_err(telex_database_error)?;
    // User row first, then only rows read by primary key: the table has no secondary index, so a
    // `FOR UPDATE` on user_id/type would lock every row of every user.
    if !crate::shared::statistical::lock_user(&mut tx, &user.id)
        .await
        .map_err(telex_database_error)?
    {
        return Err(envelope_response(
            StatusCode::OK,
            206,
            "账号登录凭证异常",
            "",
        ));
    }
    let id = match save.id.as_deref() {
        Some(id) => {
            let row = sqlx::query(
                "SELECT create_user_id,type FROM t_entering_telex_pat WHERE id=? FOR UPDATE",
            )
            .bind(id)
            .fetch_optional(&mut *tx)
            .await
            .map_err(telex_database_error)?;
            let Some(row) = row else {
                return Err(telex_rejected("训练记录不存在或类型不符"));
            };
            // Java hands the record over to the caller; here a foreign record stays untouched.
            let owner = row
                .try_get::<Option<String>, _>("create_user_id")
                .map_err(telex_database_error)?;
            if owner.as_deref() != Some(user.id.as_str()) {
                return Err(envelope_response(StatusCode::OK, 207, "无权限", ""));
            }
            if row
                .try_get::<Option<i32>, _>("type")
                .map_err(telex_database_error)?
                != Some(save.kind)
            {
                return Err(telex_rejected("训练记录不存在或类型不符"));
            }
            sqlx::query("UPDATE t_entering_telex_pat SET total_time=?,total_num=?,total_error=?,message_name=? WHERE id=?")
                .bind(save.total_time)
                .bind(save.total_num)
                .bind(save.total_error)
                .bind(save.message_name.as_deref())
                .bind(id)
                .execute(&mut *tx)
                .await
                .map_err(telex_database_error)?;
            id.to_owned()
        }
        None => {
            let duplicate = sqlx::query(
                "SELECT id FROM t_entering_telex_pat WHERE create_user_id=? AND type=? LIMIT 1",
            )
            .bind(&user.id)
            .bind(save.kind)
            .fetch_optional(&mut *tx)
            .await
            .map_err(telex_database_error)?;
            if duplicate.is_some() {
                return Err(telex_rejected(
                    "您已存在相同类型的训练，不能再添加同类的训练！",
                ));
            }
            let id = Uuid::new_v4().to_string();
            sqlx::query("INSERT INTO t_entering_telex_pat (id,create_user_id,type,total_time,total_num,total_error,message_name,create_time) VALUES (?,?,?,?,?,?,?,NOW())")
                .bind(&id)
                .bind(&user.id)
                .bind(save.kind)
                .bind(save.total_time)
                .bind(save.total_num)
                .bind(save.total_error)
                .bind(save.message_name.as_deref())
                .execute(&mut *tx)
                .await
                .map_err(telex_database_error)?;
            id
        }
    };
    record_telex_statistics(&mut tx, &user.id, save)
        .await
        .map_err(telex_database_error)?;
    tx.commit().await.map_err(telex_database_error)?;
    Ok(json!({
        "id": id,
        "createUserId": user.id,
        "type": save.kind,
        "totalTime": save.total_time,
        "totalNum": save.total_num,
        "totalError": save.total_error,
        "messageName": save.message_name,
    }))
}

/// Java answers with the whole saved record; the Vue page keeps it as its running state.
pub(super) async fn telex_save(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let kind = match telex_type(&body) {
        Ok(kind) => kind,
        Err(response) => return response,
    };
    let parsed = (|| {
        Ok::<_, Response>((
            request_string(body.get("id"))?,
            request_integer(body.get("totalTime"))?,
            request_integer(body.get("totalNum"))?,
            request_integer(body.get("totalError"))?,
            request_string(body.get("messageName"))?,
        ))
    })();
    let (id, total_time, total_num, total_error, message_name) = match parsed {
        Ok(parsed) => parsed,
        Err(response) => return response,
    };
    // Java persists an orphan record with type NULL (and a statistics row that later breaks the
    // statistics page); a record without a type is refused instead.
    let Some(kind) = kind else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let save = TelexSave {
        id,
        kind,
        total_time,
        total_num,
        total_error,
        message_name,
    };
    match save_telex(pool, &user, &save).await {
        Ok(value) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Err(response) => response,
    }
}

pub(super) async fn telex_clear(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let kind = match telex_type(&body) {
        Ok(kind) => kind,
        Err(response) => return response,
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(kind) = kind else {
        return envelope_json(StatusCode::OK, 200, Some(telex_empty_value(None)), "ok", "");
    };
    let result = async {
        let mut tx = pool.begin().await?;
        if !crate::shared::statistical::lock_user(&mut tx, &user.id).await? {
            return Ok(None);
        }
        let existing = sqlx::query("SELECT id FROM t_entering_telex_pat WHERE create_user_id=? AND type=? LIMIT 1")
            .bind(&user.id)
            .bind(kind)
            .fetch_optional(&mut *tx)
            .await?;
        let value = match existing {
            Some(row) => {
                let id = row.try_get::<String, _>("id")?;
                sqlx::query("UPDATE t_entering_telex_pat SET total_time=0,total_num=0,total_error=0 WHERE id=?")
                    .bind(&id)
                    .execute(&mut *tx)
                    .await?;
                let sql = format!("{TELEX_COLUMNS} WHERE id=?");
                let row = sqlx::query(&sql).bind(&id).fetch_one(&mut *tx).await?;
                telex_row_value(&row)?
            }
            None => telex_empty_value(Some(kind)),
        };
        tx.commit().await?;
        Ok::<_, sqlx::Error>(Some(value))
    }
    .await;
    match result {
        Ok(Some(value)) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Ok(None) => envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(error) => telex_database_error(error),
    }
}

pub(crate) fn request_integer(value: Option<&Value>) -> Result<Option<i32>, Response> {
    match value {
        None | Some(Value::Null) => Ok(None),
        Some(Value::Number(value)) => {
            let integer = value
                .as_i64()
                .and_then(|value| i32::try_from(value).ok())
                .or_else(|| {
                    value
                        .as_f64()
                        .filter(|value| *value >= i32::MIN as f64 && *value <= i32::MAX as f64)
                        .map(|value| value as i32)
                });
            integer
                .map(Some)
                .ok_or_else(|| StatusCode::BAD_REQUEST.into_response())
        }
        Some(Value::String(value)) => {
            let value = value.trim_matches(|character| character <= '\u{20}');
            if value.is_empty() || value == "null" {
                Ok(None)
            } else {
                value
                    .parse()
                    .map(Some)
                    .map_err(|_| StatusCode::BAD_REQUEST.into_response())
            }
        }
        Some(_) => Err(StatusCode::BAD_REQUEST.into_response()),
    }
}

pub(super) fn request_double(value: Option<&Value>) -> Result<Option<f64>, Response> {
    match value {
        None | Some(Value::Null) => Ok(None),
        Some(Value::Number(value)) => value
            .as_f64()
            .map(Some)
            .ok_or_else(|| StatusCode::BAD_REQUEST.into_response()),
        Some(Value::String(value)) => {
            let value = value.trim_matches(|character| character <= '\u{20}');
            match value {
                "" | "null" => Ok(None),
                "NaN" => Ok(Some(f64::NAN)),
                "INF" | "Infinity" => Ok(Some(f64::INFINITY)),
                "-INF" | "-Infinity" => Ok(Some(f64::NEG_INFINITY)),
                _ if value
                    .bytes()
                    .all(|byte| matches!(byte, b'0'..=b'9' | b'+' | b'-' | b'.' | b'e' | b'E')) =>
                {
                    value
                        .parse()
                        .map(Some)
                        .map_err(|_| StatusCode::BAD_REQUEST.into_response())
                }
                _ => Err(StatusCode::BAD_REQUEST.into_response()),
            }
        }
        Some(_) => Err(StatusCode::BAD_REQUEST.into_response()),
    }
}

pub(super) async fn get_word_stock(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    if !body.is_object() && !body.is_null() {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let kind = match request_integer(body.get("type")) {
        Ok(Some(9)) => 2,
        Ok(Some(kind)) => kind,
        // Java compares the nullable DTO type before querying the stock table.
        Ok(None) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
        Err(response) => return response,
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result: Result<Value, sqlx::Error> = async {
        let row = sqlx::query(
            "SELECT id,type,content FROM t_entering_exercise_word_stock WHERE type=? LIMIT 1",
        )
        .bind(kind)
        .fetch_optional(pool)
        .await?;
        match row {
            Some(row) => Ok(json!({
                "id": row.try_get::<i32, _>("id")?,
                "type": row.try_get::<Option<i32>, _>("type")?,
                "content": row.try_get::<Option<String>, _>("content")?,
            })),
            None => Ok(json!({"id": null, "type": null, "content": null})),
        }
    }
    .await;
    match result {
        Ok(value) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Err(error) => {
            tracing::error!(%error, "entering word stock query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(super) fn request_string(
    value: Option<&Value>,
) -> Result<Option<std::borrow::Cow<'_, str>>, Response> {
    match value {
        None | Some(Value::Null) => Ok(None),
        Some(Value::String(value)) => Ok(Some(std::borrow::Cow::Borrowed(value.as_str()))),
        Some(Value::Number(value)) => Ok(Some(std::borrow::Cow::Owned(value.to_string()))),
        Some(Value::Bool(value)) => Ok(Some(std::borrow::Cow::Borrowed(if *value {
            "true"
        } else {
            "false"
        }))),
        Some(_) => Err(StatusCode::BAD_REQUEST.into_response()),
    }
}

pub(super) async fn save_key_points(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    if body.is_null() {
        return envelope_response(
            StatusCode::OK,
            202,
            "Entity must not be null.",
            "Entity must not be null.",
        );
    }
    if !body.is_object() {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let kind = match request_integer(body.get("type")) {
        Ok(kind) => kind,
        Err(response) => return response,
    };
    let id = match request_string(body.get("id")) {
        Ok(id) => id,
        Err(response) => return response,
    };
    let content = match request_string(body.get("content")) {
        Ok(content) => content,
        Err(response) => return response,
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result: Result<Value, sqlx::Error> = async {
        let mut transaction = pool.begin().await?;
        let id = match id {
            Some(id) => {
                // Generated identifiers merge only existing entities; an unknown ID is not an insert.
                sqlx::query("SELECT id FROM t_entering_key_points WHERE id=? FOR UPDATE")
                    .bind(id.as_ref())
                    .fetch_one(&mut *transaction)
                    .await?;
                sqlx::query("UPDATE t_entering_key_points SET type=?,content=? WHERE id=?")
                    .bind(kind)
                    .bind(content.as_deref())
                    .bind(id.as_ref())
                    .execute(&mut *transaction)
                    .await?;
                id
            }
            None => {
                let id = Uuid::new_v4().to_string();
                sqlx::query("INSERT INTO t_entering_key_points (id,type,content) VALUES (?,?,?)")
                    .bind(&id)
                    .bind(kind)
                    .bind(content.as_deref())
                    .execute(&mut *transaction)
                    .await?;
                std::borrow::Cow::Owned(id)
            }
        };
        transaction.commit().await?;
        Ok(json!({"id": id, "type": kind, "content": content}))
    }
    .await;
    match result {
        Ok(value) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Err(error) => {
            tracing::error!(%error, "entering key points save failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}
