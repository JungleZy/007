use crate::domain::telegram::telegram_write::aggregate_time_text;
use crate::domain::telegram::telegram_write::error;
use crate::domain::telegram::telegram_write::train;
use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;
use crate::shared::statistical::lock_user;
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::Value;
use sqlx::{mysql::MySqlRow, MySql, MySqlPool, Row, Transaction};

pub(super) fn invalid(message: &str) -> Response {
    envelope_response(StatusCode::OK, 202, message, message)
}

fn capture_db_error(error: sqlx::Error, operation: &'static str) -> (i32, &'static str) {
    tracing::warn!(%error, operation, "telegram capture database operation failed");
    (500, "服务器错误")
}

fn capture_response((code, message): (i32, &'static str)) -> Response {
    let status = if code == 500 {
        StatusCode::INTERNAL_SERVER_ERROR
    } else {
        StatusCode::OK
    };
    envelope_response(
        status,
        code,
        if code == 207 { "无权限" } else { message },
        if code == 202 { message } else { "" },
    )
}

async fn writable_train(
    tx: &mut Transaction<'_, MySql>,
    id: &str,
    user_id: &str,
) -> Result<MySqlRow, (i32, &'static str)> {
    let row = sqlx::query("SELECT create_user_id,protocol_version,status,active_since,accumulated_active_millis,type FROM t_telegram_train WHERE id=? FOR UPDATE")
        .bind(id).fetch_optional(&mut **tx).await
        .map_err(|e| capture_db_error(e, "lock telegram train"))?
        .ok_or((202, "未查询该训练！"))?;
    if row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user_id) {
        return Err((207, "无权访问他人训练"));
    }
    if row.get::<Option<i32>, _>("protocol_version") != Some(1) {
        return Err((208, "历史训练缺少权威时间轴，请新建训练"));
    }
    if !(0..=2).contains(&row.get::<Option<i32>, _>("status").unwrap_or(0)) {
        return Err((208, "训练已结束"));
    }
    Ok(row)
}

async fn content_owner(
    tx: &mut Transaction<'_, MySql>,
    content_id: &str,
) -> Result<(String, String), (i32, &'static str)> {
    let row = sqlx::query("SELECT f.id AS floor_id,f.train_id FROM t_telegram_train_floor_content c JOIN t_telegram_train_floor f ON f.id=c.floor_id WHERE c.id=?")
        .bind(content_id).fetch_optional(&mut **tx).await
        .map_err(|e| capture_db_error(e, "load telegram content owner"))?
        .ok_or((202, "未查询报文内容"))?;
    Ok((row.get("floor_id"), row.get("train_id")))
}

async fn update_content(
    tx: &mut Transaction<'_, MySql>,
    content: &Value,
) -> Result<(), (i32, &'static str)> {
    sqlx::query("UPDATE t_telegram_train_floor_content SET mores_value=?,mores_time=? WHERE id=?")
        .bind(content.get("moresValue").and_then(Value::as_str))
        .bind(
            content
                .get("moresTime")
                .and_then(Value::as_str)
                .filter(|v| !v.trim().is_empty())
                .unwrap_or("[]"),
        )
        .bind(content.get("id").and_then(Value::as_str))
        .execute(&mut **tx)
        .await
        .map_err(|e| capture_db_error(e, "save telegram raw content"))?;
    Ok(())
}

pub(crate) async fn save_captured_content(
    pool: &MySqlPool,
    actor: &str,
    content: &Value,
) -> Result<(), (i32, &'static str)> {
    let id = content
        .get("id")
        .and_then(Value::as_str)
        .filter(|v| !v.trim().is_empty())
        .ok_or((202, "未查询报文内容"))?;
    let mut tx = pool
        .begin()
        .await
        .map_err(|e| capture_db_error(e, "start telegram capture"))?;
    if !crate::shared::statistical::lock_user(&mut tx, actor)
        .await
        .map_err(|e| capture_db_error(e, "lock telegram capture user"))?
    {
        return Err((207, "未认证的采集会话"));
    }
    let (_, train_id) = content_owner(&mut tx, id).await?;
    writable_train(&mut tx, &train_id, actor).await?;
    update_content(&mut tx, content).await?;
    tx.commit()
        .await
        .map_err(|e| capture_db_error(e, "commit telegram capture"))?;
    Ok(())
}

pub(crate) async fn save_captured_log(
    pool: &MySqlPool,
    actor: &str,
    data: &Value,
) -> Result<(), (i32, &'static str)> {
    let id = data
        .get("telegramTrainId")
        .and_then(Value::as_str)
        .filter(|v| !v.trim().is_empty())
        .ok_or((202, "训练编号不能为空"))?;
    let mut tx = pool
        .begin()
        .await
        .map_err(|e| capture_db_error(e, "start telegram captured log"))?;
    if !crate::shared::statistical::lock_user(&mut tx, actor)
        .await
        .map_err(|e| capture_db_error(e, "lock telegram log user"))?
    {
        return Err((207, "未认证的采集会话"));
    }
    writable_train(&mut tx, id, actor).await?;
    let value = data.get("value").and_then(|value| match value {
        Value::String(value) => Some(value.clone()),
        Value::Number(value) => Some(value.to_string()),
        _ => None,
    });
    sqlx::query("INSERT INTO t_telegram_train_log (id,telegram_train_id,type,value,creat_time) VALUES (?,?,?,?,?)")
        .bind(uuid::Uuid::new_v4().to_string()).bind(id).bind(data.get("type").and_then(Value::as_i64))
        .bind(value).bind(chrono::Utc::now().timestamp_millis().to_string())
        .execute(&mut *tx).await.map_err(|e| capture_db_error(e, "save telegram captured log"))?;
    tx.commit()
        .await
        .map_err(|e| capture_db_error(e, "commit telegram captured log"))?;
    Ok(())
}

pub(super) async fn save_floor_content(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match save_captured_content(pool, &user.id, &body).await {
        Ok(()) => envelope_response(StatusCode::OK, 200, "ok", ""),
        Err(error) => capture_response(error),
    }
}

async fn submitted_content(
    tx: &mut Transaction<'_, MySql>,
    train_id: &str,
    body: &Value,
) -> Result<(), Response> {
    let floors = body
        .get("trainFloors")
        .and_then(Value::as_array)
        .ok_or_else(|| invalid("报底不能为空"))?;
    for floor in floors {
        let contents = floor
            .get("floorContents")
            .and_then(Value::as_array)
            .ok_or_else(|| invalid("报底内容不能为空"))?;
        let declared = floor
            .get("floor")
            .and_then(|f| f.get("id"))
            .and_then(Value::as_str);
        for content in contents {
            let id = content
                .get("id")
                .and_then(Value::as_str)
                .ok_or_else(|| invalid("未查询报文内容"))?;
            let (floor_id, owner_train) = content_owner(tx, id).await.map_err(capture_response)?;
            if declared.is_some_and(|declared| declared != floor_id) || owner_train != train_id {
                return Err(envelope_response(StatusCode::OK, 207, "无权限", ""));
            }
        }
    }
    for floor in floors {
        for content in floor["floorContents"]
            .as_array()
            .expect("validated contents")
        {
            update_content(tx, content)
                .await
                .map_err(capture_response)?;
        }
    }
    Ok(())
}

fn expected_morse(key: &str, short: bool) -> Result<&'static str, &'static str> {
    if key.len() != 1 {
        return Err("报文字符无效");
    }
    let key = key.as_bytes()[0].to_ascii_uppercase();
    if short {
        match key {
            b'0' => return Ok("1"),
            b'1' => return Ok("01"),
            b'2' => return Ok("001"),
            b'8' => return Ok("100"),
            b'9' => return Ok("10"),
            _ => {}
        }
    }
    Ok(match key {
        b'A' => "01",
        b'B' => "1000",
        b'C' => "1010",
        b'D' => "100",
        b'E' => "0",
        b'F' => "0010",
        b'G' => "110",
        b'H' => "0000",
        b'I' => "00",
        b'J' => "0111",
        b'K' => "101",
        b'L' => "0100",
        b'M' => "11",
        b'N' => "10",
        b'O' => "111",
        b'P' => "0110",
        b'Q' => "1101",
        b'R' => "010",
        b'S' => "000",
        b'T' => "1",
        b'U' => "001",
        b'V' => "0001",
        b'W' => "011",
        b'X' => "1001",
        b'Y' => "1011",
        b'Z' => "1100",
        b'0' => "11111",
        b'1' => "01111",
        b'2' => "00111",
        b'3' => "00011",
        b'4' => "00001",
        b'5' => "00000",
        b'6' => "10000",
        b'7' => "11000",
        b'8' => "11100",
        b'9' => "11110",
        _ => return Err("报文字符无效"),
    })
}

fn matches_morse(actual: Option<&Value>, expected: &str) -> bool {
    actual.and_then(Value::as_array).is_some_and(|values| {
        values.len() == expected.len()
            && values
                .iter()
                .zip(expected.bytes())
                .all(|(value, bit)| value.as_i64() == Some(i64::from(bit - b'0')))
    })
}

pub(super) fn raw_result(
    key: &str,
    raw: Option<&str>,
    grouped: bool,
    short: bool,
) -> Result<(bool, bool), &'static str> {
    let actual: Option<Value> = raw
        .filter(|v| !v.trim().is_empty())
        .map(serde_json::from_str)
        .transpose()
        .map_err(|_| "报文原始内容无效")?;
    if actual.as_ref().is_some_and(|v| !v.is_array()) {
        return Err("拍发值必须为数组");
    }
    let values = actual.as_ref().and_then(Value::as_array);
    if grouped {
        let keys: Vec<String> = serde_json::from_str(key).map_err(|_| "词组报文必须为字符数组")?;
        if keys.is_empty() {
            return Err("词组报文必须为字符数组");
        }
        let mut correct = values.is_some_and(|values| values.len() == keys.len());
        for (index, key) in keys.iter().enumerate() {
            correct &= matches_morse(
                values.and_then(|values| values.get(index)),
                expected_morse(key, short)?,
            );
        }
        Ok((
            correct,
            values.is_some_and(|values| {
                values
                    .iter()
                    .any(|v| v.as_array().is_some_and(|v| !v.is_empty()))
            }),
        ))
    } else {
        Ok((
            matches_morse(actual.as_ref(), expected_morse(key, short)?),
            values.is_some_and(|v| !v.is_empty()),
        ))
    }
}

async fn recalculate(
    tx: &mut Transaction<'_, MySql>,
    id: &str,
    millis: i64,
) -> Result<(), Response> {
    let rows = sqlx::query("SELECT c.mores_key,c.mores_value,f.type,f.number_type FROM t_telegram_train_floor f JOIN t_telegram_train_floor_content c ON c.floor_id=f.id WHERE f.train_id=? ORDER BY f.sort,c.sort")
        .bind(id).fetch_all(&mut **tx).await.map_err(|e| error(e, "load telegram scoring content"))?;
    let mut correct = 0;
    let mut attempted = 0;
    for row in &rows {
        let key = row.get::<Option<String>, _>("mores_key");
        let raw = row.get::<Option<String>, _>("mores_value");
        let (matched, captured) = raw_result(
            key.as_deref().ok_or_else(|| invalid("报文字符无效"))?,
            raw.as_deref(),
            row.get::<Option<i32>, _>("type").unwrap_or(0) > 10,
            row.get::<Option<i32>, _>("number_type") == Some(1),
        )
        .map_err(invalid)?;
        correct += i64::from(matched);
        attempted += i64::from(captured);
    }
    let total = rows.len() as i64;
    let accuracy = if total == 0 {
        "0".to_owned()
    } else {
        format!(
            "{:.2}",
            crate::shared::scoring::accuracy_percent(correct, total)
        )
    };
    let speed = crate::shared::scoring::rate_per_minute(attempted, millis).to_string();
    sqlx::query("UPDATE t_telegram_train SET total_number=?,error_number=?,total_knock_number=?,accuracy=?,speed=? WHERE id=?")
        .bind(total).bind(total-correct).bind(correct).bind(accuracy).bind(speed).bind(id).execute(&mut **tx).await.map_err(|e| error(e, "save telegram metrics"))?;
    Ok(())
}

async fn finish_statistics(
    tx: &mut Transaction<'_, MySql>,
    user_id: &str,
    kind: i32,
) -> Result<(), Response> {
    let (category, types) = if kind < 10 {
        (0, "0,1,2")
    } else {
        (1, "11,12,13,14")
    };
    let aggregate = sqlx::query(&format!("SELECT COALESCE(SUM(sustain_time),0) AS total_time,COUNT(id) AS total_count,COALESCE(AVG(speed),0) AS avg_speed FROM t_telegram_train WHERE create_user_id=? AND type IN ({types}) AND status=3"))
        .bind(user_id).fetch_one(&mut **tx).await.map_err(|e| error(e, "aggregate telegram metrics"))?;
    let existing = sqlx::query_scalar::<_, String>(
        "SELECT id FROM t_telegram_train_statistical WHERE user_id=? AND type=? LIMIT 1",
    )
    .bind(user_id)
    .bind(category)
    .fetch_optional(&mut **tx)
    .await
    .map_err(|e| error(e, "load telegram statistics"))?;
    let total_time = aggregate_time_text(aggregate.get("total_time"));
    let count = aggregate.get::<i64, _>("total_count");
    let speed = aggregate.get::<f64, _>("avg_speed");
    if let Some(id) = existing {
        sqlx::query("UPDATE t_telegram_train_statistical SET total_time=?,total_count=?,avg_speed=? WHERE id=?")
            .bind(total_time).bind(count).bind(speed).bind(id).execute(&mut **tx).await.map_err(|e| error(e, "update telegram statistics"))?;
    } else {
        sqlx::query("INSERT INTO t_telegram_train_statistical (id,type,total_time,total_count,avg_speed,user_id) VALUES (?,?,?,?,?,?)")
            .bind(uuid::Uuid::new_v4().to_string()).bind(category).bind(total_time).bind(count).bind(speed).bind(user_id).execute(&mut **tx).await.map_err(|e| error(e, "insert telegram statistics"))?;
    }
    Ok(())
}

pub(super) async fn control(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let requested = train(&body);
    let Some(id) = requested.get("id").and_then(Value::as_str) else {
        return invalid("训练信息不能为空");
    };
    let kind = body.get("controlType").and_then(Value::as_i64).unwrap_or(0);
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return error(e, "start telegram control"),
    };
    match lock_user(&mut tx, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(e) => return error(e, "lock telegram control user"),
    }
    let row = match writable_train(&mut tx, id, &user.id).await {
        Ok(row) => row,
        Err(error) => return capture_response(error),
    };
    let rates = match crate::domain::telegram::telegram_write::timing_rates(requested) {
        Ok(rates) => rates,
        Err(_) => return invalid("训练配置须为非负毫秒值，且上界大于下界"),
    };
    if let Err(e) = sqlx::query("UPDATE t_telegram_train SET rate_dot_min_ms=?,rate_dot_max_ms=?,rate_line_min_ms=?,rate_line_max_ms=?,rate_interval_min_ms=?,rate_interval_max_ms=?,big_interval_min_ms=?,big_interval_max_ms=? WHERE id=?")
        .bind(rates[0]).bind(rates[1]).bind(rates[2]).bind(rates[3]).bind(rates[4]).bind(rates[5]).bind(rates[6]).bind(rates[7]).bind(id).execute(&mut *tx).await { return error(e, "save telegram rates"); }
    let current = row.get::<Option<i32>, _>("status").unwrap_or(0);
    let now = chrono::Utc::now().timestamp_millis();
    if kind == 0 {
        if current != 1 {
            if current != 0 && current != 2 {
                return invalid("训练状态不可开始");
            }
            if let Err(e) = sqlx::query("UPDATE t_telegram_train SET status=1,start_time=IF(start_time IS NULL OR start_time='',?,start_time),active_since=?,accumulated_active_millis=COALESCE(accumulated_active_millis,0) WHERE id=?")
                .bind(now.to_string()).bind(now).bind(id).execute(&mut *tx).await { return error(e, "start telegram active segment"); }
        }
    } else if kind == 1 || kind == 2 {
        if !(kind == 1 && current == 2) {
            if current != 1 && !(kind == 2 && current == 2) {
                return invalid("训练未开始");
            }
            if let Err(response) = submitted_content(&mut tx, id, &body).await {
                return response;
            }
            let active = row.get::<Option<i64>, _>("active_since");
            let accumulated = row
                .get::<Option<i64>, _>("accumulated_active_millis")
                .unwrap_or(0);
            let millis = accumulated
                + if current == 1 {
                    active.map(|start| (now - start).max(0)).unwrap_or(0)
                } else {
                    0
                };
            if current == 1 && active.is_some() {
                if let Err(e) = sqlx::query("UPDATE t_telegram_train SET accumulated_active_millis=?,sustain_time=?,active_since=NULL WHERE id=?")
                    .bind(millis).bind(millis.to_string()).bind(id).execute(&mut *tx).await { return error(e, "close telegram active segment"); }
            }
            if let Err(response) = recalculate(&mut tx, id, millis).await {
                return response;
            }
            if let Some(floor_id) = requested.get("nowFloorId").and_then(Value::as_str) {
                let owner = match sqlx::query_scalar::<_, String>(
                    "SELECT train_id FROM t_telegram_train_floor WHERE id=?",
                )
                .bind(floor_id)
                .fetch_optional(&mut *tx)
                .await
                {
                    Ok(owner) => owner,
                    Err(e) => return error(e, "load current telegram floor"),
                };
                match owner {
                    None => return invalid("未查询当前报底"),
                    Some(owner) if owner != id => {
                        return envelope_response(StatusCode::OK, 207, "无权限", "")
                    }
                    _ => {}
                }
                if let Err(e) = sqlx::query("UPDATE t_telegram_train SET now_floor_id=? WHERE id=?")
                    .bind(floor_id)
                    .bind(id)
                    .execute(&mut *tx)
                    .await
                {
                    return error(e, "save current telegram floor");
                }
            }
            let query = if kind == 1 {
                "UPDATE t_telegram_train SET status=2,pause_time=? WHERE id=?"
            } else {
                "UPDATE t_telegram_train SET status=3,end_time=? WHERE id=?"
            };
            if let Err(e) = sqlx::query(query)
                .bind(now.to_string())
                .bind(id)
                .execute(&mut *tx)
                .await
            {
                return error(e, "save telegram lifecycle");
            }
            if kind == 2 {
                if let Err(response) = finish_statistics(
                    &mut tx,
                    &user.id,
                    row.get::<Option<i32>, _>("type").unwrap_or(0),
                )
                .await
                {
                    return response;
                }
            }
        }
    } else {
        return invalid(&format!("Unexpected value: {kind}"));
    }
    let saved = match sqlx::query(&format!(
        "SELECT {} FROM t_telegram_train WHERE id=?",
        crate::domain::telegram::telegram::TRAIN_COLUMNS
    ))
    .bind(id)
    .fetch_one(&mut *tx)
    .await
    {
        Ok(row) => crate::domain::telegram::telegram::train_json(&row),
        Err(e) => return error(e, "load controlled telegram train"),
    };
    if let Err(e) = tx.commit().await {
        return error(e, "commit telegram control");
    }
    envelope_json(StatusCode::OK, 200, Some(saved), "ok", "")
}
