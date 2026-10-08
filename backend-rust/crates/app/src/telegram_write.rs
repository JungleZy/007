use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::{MySql, Row, Transaction};

#[path = "telegram_lifecycle.rs"]
mod lifecycle;
pub(crate) use lifecycle::{control, save_floor_content};

async fn lock_user(tx: &mut Transaction<'_, MySql>, user_id: &str) -> Result<bool, sqlx::Error> {
    sqlx::query("SELECT id FROM t_user WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(user_id)
        .fetch_optional(&mut **tx)
        .await
        .map(|row| row.is_some())
}
fn train(body: &Value) -> &Value {
    body.get("train").unwrap_or(body)
}
fn error(error: sqlx::Error, op: &'static str) -> Response {
    tracing::warn!(error=%error,operation=op,"telegram database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

pub(crate) async fn save(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let train = train(&body);
    if body
        .get("trainFloors")
        .and_then(Value::as_array)
        .map_or(true, |floors| floors.is_empty())
    {
        return envelope_response(StatusCode::OK, 202, "训练楼层不能为空", "");
    }
    for (name, min_name, max_name) in [
        ("点", "rateDotMinMs", "rateDotMaxMs"),
        ("划", "rateLineMinMs", "rateLineMaxMs"),
        ("字间隔", "rateIntervalMinMs", "rateIntervalMaxMs"),
        ("组间隔", "bigIntervalMinMs", "bigIntervalMaxMs"),
    ] {
        let min = train.get(min_name).and_then(Value::as_i64);
        let max = train.get(max_name).and_then(Value::as_i64);
        if !matches!((min, max), (Some(min), Some(max)) if min >= 0 && max > min) {
            return envelope_response(
                StatusCode::OK,
                202,
                "训练配置须为非负毫秒值，且上界大于下界",
                name,
            );
        }
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return error(e, "start telegram save"),
    };
    match lock_user(&mut tx, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(e) => return error(e, "lock telegram user"),
    };
    let id = uuid::Uuid::new_v4().to_string();
    let created = chrono_like_now();
    let values = [
        train.get("title").and_then(Value::as_str),
        train.get("startTime").and_then(Value::as_str),
        train.get("pauseTime").and_then(Value::as_str),
        train.get("endTime").and_then(Value::as_str),
        train
            .get("sustainTime")
            .map(Value::as_str)
            .unwrap_or(Some("0")),
        train
            .get("createTime")
            .map(Value::as_str)
            .unwrap_or(Some(&created)),
        train.get("nowFloorId").and_then(Value::as_str),
    ];
    let nums = [
        train.get("type").and_then(Value::as_i64).unwrap_or(0) as i32,
        train.get("status").and_then(Value::as_i64).unwrap_or(0) as i32,
        train
            .get("totalKnockNumber")
            .and_then(Value::as_i64)
            .unwrap_or(0) as i32,
        train
            .get("totalNumber")
            .and_then(Value::as_i64)
            .unwrap_or(0) as i32,
        train
            .get("errorNumber")
            .and_then(Value::as_i64)
            .unwrap_or(0) as i32,
    ];
    let result = sqlx::query("INSERT INTO t_telegram_train (id,title,start_time,pause_time,end_time,sustain_time,type,status,total_knock_number,total_number,error_number,accuracy,speed,create_user_id,create_time,now_floor_id,protocol_version,accumulated_active_millis) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
        .bind(&id).bind(values[0]).bind(values[1]).bind(values[2]).bind(values[3]).bind(values[4]).bind(nums[0]).bind(nums[1]).bind(nums[2]).bind(nums[3]).bind(nums[4]).bind(train.get("accuracy").map(Value::as_str).unwrap_or(Some("0"))).bind(train.get("speed").map(Value::as_str).unwrap_or(Some("0"))).bind(&user.id).bind(values[5]).bind(values[6]).bind(1_i32).bind(0_i64).execute(&mut *tx).await;
    if let Err(e) = result {
        return error(e, "save telegram train");
    }
    if let Err(e) = sqlx::query("UPDATE t_telegram_train SET rate_dot_min_ms=?,rate_dot_max_ms=?,rate_line_min_ms=?,rate_line_max_ms=?,rate_interval_min_ms=?,rate_interval_max_ms=?,big_interval_min_ms=?,big_interval_max_ms=? WHERE id=?")
        .bind(train.get("rateDotMinMs").and_then(Value::as_i64).unwrap_or(1))
        .bind(train.get("rateDotMaxMs").and_then(Value::as_i64).unwrap_or(80))
        .bind(train.get("rateLineMinMs").and_then(Value::as_i64).unwrap_or(81))
        .bind(train.get("rateLineMaxMs").and_then(Value::as_i64).unwrap_or(240))
        .bind(train.get("rateIntervalMinMs").and_then(Value::as_i64).unwrap_or(81))
        .bind(train.get("rateIntervalMaxMs").and_then(Value::as_i64).unwrap_or(240))
        .bind(train.get("bigIntervalMinMs").and_then(Value::as_i64).unwrap_or(241))
        .bind(train.get("bigIntervalMaxMs").and_then(Value::as_i64).unwrap_or(400))
        .bind(&id).execute(&mut *tx).await { return error(e, "save telegram timing settings"); }
    if let Some(floors) = body.get("trainFloors").and_then(Value::as_array) {
        for (floor_index, floor_item) in floors.iter().enumerate() {
            let floor = floor_item.get("floor").unwrap_or(floor_item);
            let floor_id = uuid::Uuid::new_v4().to_string();
            if let Err(e) = sqlx::query("INSERT INTO t_telegram_train_floor (id,sort,train_id,type,number_type,content_number) VALUES (?,?,?,?,?,?)")
                .bind(&floor_id).bind(floor_index as i32).bind(&id).bind(floor.get("type").and_then(Value::as_i64).unwrap_or(0)).bind(floor.get("numberType").and_then(Value::as_i64).unwrap_or(0)).bind(floor_item.get("floorContents").and_then(Value::as_array).map(|v| v.len() as i64).unwrap_or(0)).execute(&mut *tx).await { return error(e, "save telegram floor"); }
            if let Some(contents) = floor_item.get("floorContents").and_then(Value::as_array) {
                for (index, content) in contents.iter().enumerate() {
                    let content_id = uuid::Uuid::new_v4().to_string();
                    if let Err(e) = sqlx::query("INSERT INTO t_telegram_train_floor_content (id,floor_id,sort,mores_key,mores_value,mores_time) VALUES (?,?,?,?,?,?)")
                        .bind(content_id).bind(&floor_id).bind(index as i64).bind(content.get("moresKey").and_then(Value::as_str)).bind(content.get("moresValue").and_then(Value::as_str).unwrap_or("[]")).bind(content.get("moresTime").and_then(Value::as_str).filter(|value| !value.is_empty()).unwrap_or("[]")).execute(&mut *tx).await { return error(e, "save telegram floor content"); }
                }
            }
        }
    }
    let saved = match sqlx::query(&format!(
        "SELECT {} FROM t_telegram_train WHERE id=?",
        super::telegram::TRAIN_COLUMNS
    ))
    .bind(&id)
    .fetch_one(&mut *tx)
    .await
    {
        Ok(row) => super::telegram::train_json(&row),
        Err(e) => return error(e, "load saved telegram train"),
    };
    if let Err(e) = tx.commit().await {
        return error(e, "commit telegram save");
    }
    envelope_json(StatusCode::OK, 200, Some(saved), "ok", "")
}

pub(crate) async fn start(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(mut body): Json<Value>,
) -> Response {
    if let Some(object) = body.as_object_mut() {
        object.insert("controlType".to_owned(), json!(0));
    }
    control(State(state), Extension(user), Json(body)).await
}
pub(crate) async fn pause(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(mut body): Json<Value>,
) -> Response {
    if let Some(object) = body.as_object_mut() {
        object.insert("controlType".to_owned(), json!(1));
    }
    control(State(state), Extension(user), Json(body)).await
}
pub(crate) async fn end(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(mut body): Json<Value>,
) -> Response {
    if let Some(object) = body.as_object_mut() {
        object.insert("controlType".to_owned(), json!(2));
    }
    control(State(state), Extension(user), Json(body)).await
}

fn setting_boundary(value: &Value, prefix: &str) -> Result<rust_decimal::Decimal, &'static str> {
    let raw = match value {
        Value::String(value) => value.trim().to_owned(),
        Value::Number(value) => value.to_string(),
        _ => return Err("配置毫秒值必须为有限的非负数"),
    };
    let text = if prefix.is_empty() {
        raw.as_str()
    } else {
        raw.strip_prefix(prefix)
            .map(str::trim)
            .ok_or("基础练习缺少合法异常区间")?
    };
    if text.is_empty() || text.starts_with('-') || text.contains('e') || text.contains('E') {
        return Err("配置毫秒值必须为有限的非负数");
    }
    let number = text
        .parse::<rust_decimal::Decimal>()
        .map_err(|_| "配置毫秒值必须为有限的非负数")?;
    if number < rust_decimal::Decimal::ZERO {
        return Err("配置毫秒值必须为有限的非负数");
    }
    Ok(number)
}

fn validate_settings(values: &[Value]) -> Result<(), &'static str> {
    if values.is_empty() {
        return Err("基础练习配置不能为空");
    }
    let mut groups = [
        std::collections::BTreeMap::<i32, (rust_decimal::Decimal, rust_decimal::Decimal)>::new(),
        std::collections::BTreeMap::new(),
    ];
    for item in values {
        let object = item.as_object().ok_or("基础练习配置类型无效")?;
        if object.get("type").and_then(Value::as_i64) != Some(0) {
            return Err("基础练习配置类型无效");
        }
        let key = object
            .get("key")
            .and_then(Value::as_str)
            .ok_or("基础练习配置类型无效")?;
        let group = match key {
            "0" => 0,
            "1" => 1,
            _ => return Err("基础练习配置类型无效"),
        };
        let value = object
            .get("value")
            .and_then(Value::as_str)
            .ok_or("基础练习配置JSON无效")?;
        let config: Value = serde_json::from_str(value).map_err(|_| "基础练习配置JSON无效")?;
        let config = config
            .as_object()
            .ok_or("基础练习分级名称、文案或类型无效")?;
        let kind = config
            .get("type")
            .and_then(Value::as_i64)
            .filter(|v| *v >= 0)
            .ok_or("基础练习分级名称、文案或类型无效")? as i32;
        if config
            .get("name")
            .and_then(Value::as_str)
            .map_or(true, |value| value.trim().is_empty())
            || config
                .get("msg")
                .and_then(Value::as_str)
                .map_or(true, |value| value.trim().is_empty())
        {
            return Err("基础练习分级名称、文案或类型无效");
        }
        let min = setting_boundary(
            config.get("min").ok_or("配置毫秒值必须为有限的非负数")?,
            if kind == 0 { "<" } else { "" },
        )?;
        let max = setting_boundary(
            config.get("max").ok_or("配置毫秒值必须为有限的非负数")?,
            if kind == 0 { ">" } else { "" },
        )?;
        if max <= min || groups[group].insert(kind, (min, max)).is_some() {
            return Err(if max <= min {
                "基础练习区间上界必须大于下界"
            } else {
                "基础练习存在重复分级"
            });
        }
    }
    if !groups[0].contains_key(&0)
        || !groups[1].contains_key(&0)
        || groups[0].len() < 2
        || groups[1].len() < 2
    {
        return Err("点和划均须包含异常区间及正区间");
    }
    if groups[0].keys().collect::<Vec<_>>() != groups[1].keys().collect::<Vec<_>>() {
        return Err("点和划的分级必须完整对应");
    }
    for group in &groups {
        let normal_min = group
            .iter()
            .filter(|(kind, _)| **kind != 0)
            .map(|(_, (min, _))| *min)
            .min()
            .ok_or("点和划均须包含异常区间及正区间")?;
        let normal_max = group
            .iter()
            .filter(|(kind, _)| **kind != 0)
            .map(|(_, (_, max))| *max)
            .max()
            .ok_or("点和划均须包含异常区间及正区间")?;
        let abnormal = group.get(&0).expect("checked above");
        if abnormal.0 != normal_min || abnormal.1 != normal_max {
            return Err("异常区间必须覆盖正区间的外边界");
        }
    }
    Ok(())
}

pub(crate) async fn save_setting(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    if !user.is_admin {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let Some(values) = body.as_array() else {
        return envelope_response(StatusCode::OK, 202, "基础练习配置不能为空", "");
    };
    if let Err(message) = validate_settings(values) {
        return envelope_response(StatusCode::OK, 202, message, "");
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return error(e, "start telegram settings"),
    };
    if let Err(e) = sqlx::query("DELETE FROM t_telegram_train_setting")
        .execute(&mut *tx)
        .await
    {
        return error(e, "delete telegram settings");
    }
    for value in values {
        let Some(item) = value.as_object() else {
            return envelope_response(StatusCode::OK, 202, "基础练习配置类型无效", "");
        };
        let result = sqlx::query(
            "INSERT INTO t_telegram_train_setting (id,type,`key`,value) VALUES (?,?,?,?)",
        )
        .bind(uuid::Uuid::new_v4().to_string())
        .bind(item.get("type").and_then(Value::as_i64).unwrap_or(0) as i32)
        .bind(item.get("key").and_then(Value::as_str).unwrap_or_default())
        .bind(
            item.get("value")
                .and_then(Value::as_str)
                .unwrap_or_default(),
        )
        .execute(&mut *tx)
        .await;
        if let Err(e) = result {
            return error(e, "insert telegram setting");
        }
    }
    let rows =
        match sqlx::query("SELECT id,type,`key`,value FROM t_telegram_train_setting ORDER BY id")
            .fetch_all(&mut *tx)
            .await
        {
            Ok(rows) => rows,
            Err(e) => return error(e, "load telegram settings"),
        };
    if let Err(e) = tx.commit().await {
        return error(e, "commit telegram settings");
    }
    envelope_json(StatusCode::OK, 200, Some(Value::Array(rows.iter().map(|row| json!({"id":row.get::<String,_>("id"),"type":row.get::<Option<i32>,_>("type"),"key":row.get::<Option<String>,_>("key"),"value":row.get::<Option<String>,_>("value")})).collect())), "ok", "")
}

pub(crate) async fn save_base_train(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return error(e, "start telegram base train"),
    };
    match lock_user(&mut tx, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(e) => return error(e, "lock telegram base user"),
    };
    let id = uuid::Uuid::new_v4().to_string();
    let created = chrono_like_now();
    let total_knock = body
        .get("totalKnockNumber")
        .and_then(Value::as_i64)
        .unwrap_or(0) as i32;
    let sustain = body
        .get("sustainTime")
        .and_then(Value::as_str)
        .unwrap_or("0");
    if let Err(e) = sqlx::query("INSERT INTO t_telegram_train (id,type,status,total_knock_number,total_number,error_number,accuracy,speed,sustain_time,create_user_id,create_time,protocol_version,accumulated_active_millis,rate_dot_min_ms,rate_dot_max_ms,rate_line_min_ms,rate_line_max_ms,rate_interval_min_ms,rate_interval_max_ms,big_interval_min_ms,big_interval_max_ms) VALUES (?,21,3,?,0,0,'0','0',?,?,?,0,0,1,80,81,240,81,240,241,400)")
        .bind(&id).bind(total_knock).bind(sustain).bind(&user.id).bind(&created).execute(&mut *tx).await { return error(e, "save telegram base train"); }
    let aggregate = match sqlx::query("SELECT COALESCE(SUM(sustain_time),0) AS total_time,COUNT(*) AS total_count,COALESCE(AVG(CAST(speed AS DECIMAL(30,6))),0) AS avg_speed FROM t_telegram_train WHERE create_user_id=? AND type=21 AND status=3")
        .bind(&user.id).fetch_one(&mut *tx).await { Ok(row) => row, Err(e) => return error(e, "aggregate telegram base train") };
    let existing = match sqlx::query(
        "SELECT id FROM t_telegram_train_statistical WHERE user_id=? AND type=2 LIMIT 1",
    )
    .bind(&user.id)
    .fetch_optional(&mut *tx)
    .await
    {
        Ok(row) => row,
        Err(e) => return error(e, "load telegram base statistics"),
    };
    let total_time = aggregate_time_text(aggregate.get::<f64, _>("total_time"));
    let total_count = aggregate.get::<i64, _>("total_count") as i32;
    let avg_speed = aggregate.get::<rust_decimal::Decimal, _>("avg_speed");
    let result = if let Some(row) = existing {
        sqlx::query("UPDATE t_telegram_train_statistical SET total_count=?,total_time=?,avg_speed=? WHERE id=?").bind(total_count).bind(total_time).bind(avg_speed).bind(row.get::<String,_>("id")).execute(&mut *tx).await
    } else {
        sqlx::query("INSERT INTO t_telegram_train_statistical (id,type,total_count,total_time,avg_speed,user_id) VALUES (?,2,?,?,?,?)").bind(uuid::Uuid::new_v4().to_string()).bind(total_count).bind(total_time).bind(avg_speed).bind(&user.id).execute(&mut *tx).await
    };
    if let Err(e) = result {
        return error(e, "save telegram base statistics");
    }
    if let Err(e) = tx.commit().await {
        return error(e, "commit telegram base train");
    }
    match sqlx::query(&format!(
        "SELECT {} FROM t_telegram_train WHERE id=? LIMIT 1",
        super::telegram::TRAIN_COLUMNS
    ))
    .bind(&id)
    .fetch_optional(pool)
    .await
    {
        Ok(Some(row)) => envelope_json(
            StatusCode::OK,
            200,
            Some(super::telegram::train_json(&row)),
            "ok",
            "",
        ),
        Ok(None) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
        Err(e) => error(e, "load telegram base train"),
    }
}

// MySQL SUM over Java's string duration column returns a Double. Gson keeps
// its fractional .0 and uses Java's scientific-notation thresholds.
fn aggregate_time_text(value: f64) -> String {
    let decimal = |text: String| {
        if text.contains('.') {
            text
        } else {
            format!("{text}.0")
        }
    };
    if value != 0.0 && !(0.001..10_000_000.0).contains(&value.abs()) {
        let scientific = format!("{value:e}");
        let (mantissa, exponent) = scientific.split_once('e').expect("float exponent");
        format!("{}E{exponent}", decimal(mantissa.to_owned()))
    } else {
        decimal(value.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::aggregate_time_text;

    #[test]
    fn aggregate_strings_preserve_java_double_notation() {
        for (value, expected) in [
            (0.0, "0.0"),
            (12.0, "12.0"),
            (12.5, "12.5"),
            (0.000001, "1.0E-6"),
            (10_000_000.0, "1.0E7"),
        ] {
            assert_eq!(aggregate_time_text(value), expected);
        }
    }
}
fn chrono_like_now() -> String {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|value| value.as_millis().to_string())
        .unwrap_or_else(|_| "0".to_owned())
}
