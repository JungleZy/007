use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use axum::http::StatusCode;
use axum::response::Response;
use serde::Deserialize;
use serde_json::Value;

use super::capture::stored_json;
use super::generate::{capture_intervals, interval_duration};
#[derive(Clone, Copy)]
pub(super) enum Domain {
    Key,
    Telex,
    Ticker,
}
#[derive(Debug, Deserialize)]
pub(super) struct QueryId {
    #[serde(rename = "trainId")]
    pub(super) train_id: Option<String>,
    #[serde(rename = "attempt")]
    pub(super) attempt: Option<i32>,
}
pub(super) fn table(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => "general_key_pat",
        Domain::Telex => "general_telex_pat",
        Domain::Ticker => "general_ticker_pat",
    }
}
pub(super) fn ws_kind(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => "key",
        Domain::Telex => "telex",
        Domain::Ticker => "ticker",
    }
}

pub(super) fn id_is_int(domain: Domain) -> bool {
    !matches!(domain, Domain::Telex)
}

pub(super) fn user_table(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => "general_key_pat_user",
        Domain::Telex => "general_telex_pat_user",
        Domain::Ticker => "general_ticker_pat_train_user",
    }
}
pub(super) fn page_table(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => "general_key_pat_page",
        Domain::Telex => "general_telex_pat_page",
        Domain::Ticker => "general_ticker_pat_train_page",
    }
}
pub(super) fn value_table(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => "general_key_pat_user_value",
        Domain::Telex => "general_telex_pat_user_value",
        Domain::Ticker => "general_ticker_pat_train_user_value",
    }
}
#[derive(Debug)]
pub(super) enum Failure {
    Business(i32, &'static str),
    Database(sqlx::Error),
}
impl From<sqlx::Error> for Failure {
    fn from(error: sqlx::Error) -> Self {
        Self::Database(error)
    }
}
pub(super) type Outcome<T> = Result<T, Failure>;
pub(super) fn invalid(message: &'static str) -> Failure {
    Failure::Business(202, message)
}
pub(super) fn denied() -> Failure {
    Failure::Business(207, "无权限")
}
pub(super) fn terminal(message: &'static str) -> Failure {
    Failure::Business(208, message)
}
pub(super) fn response(result: Outcome<Value>) -> Response {
    match result {
        Ok(value) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Err(Failure::Business(code, message)) => {
            let description = if matches!(code, 202 | 208) {
                message
            } else {
                ""
            };
            envelope_response(StatusCode::OK, code, message, description)
        }
        Err(Failure::Database(error)) => {
            tracing::warn!(%error, operation = "general training service", "general training database operation failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}
use serde_json::json;

pub(super) fn train_response(mut train: Value, domain: Domain) -> Value {
    train["contentValue"] = Value::Null;
    if train.get("pageCount").is_none() {
        train["pageCount"] = Value::Null;
    }
    train["userInfoList"] = Value::Null;
    train.as_object_mut().map(|object| {
        for field in ["createTime", "startTime", "endTime"] {
            if let Some(Value::String(value)) = object.get(field).cloned() {
                let normalized = value
                    .split_once('T')
                    .map(|(date, time)| {
                        format!("{date} {}", time.split('.').next().unwrap_or(time))
                    })
                    .unwrap_or(value);
                object.insert(field.to_owned(), Value::String(normalized));
            }
        }
        object.remove("upId");
        if matches!(domain, Domain::Telex | Domain::Ticker) {
            object.remove("ruleScore");
        }
        if matches!(domain, Domain::Telex) {
            object.remove("protocolVersion");
        }
        if matches!(domain, Domain::Ticker) {
            if let Some(value) = object.get("codeSort").and_then(Value::as_i64) {
                object.insert("codeSort".into(), json!(value != 0));
            }
            if let Some(value) = object.get("isRandom").and_then(Value::as_i64) {
                object.insert("isRandom".into(), json!(value != 0));
            }
        }
    });
    train
}

pub(super) fn detail_member(member: &Value, domain: Domain) -> Value {
    let common = |field: &str| member.get(field).cloned().unwrap_or(Value::Null);
    let scalar_text = |field: &str| {
        text(&member[field])
            .map(Value::String)
            .unwrap_or(Value::Null)
    };
    match domain {
        Domain::Key => {
            let duration = if member["isFinish"] == 1 {
                member["duration"]
                    .as_str()
                    .and_then(|value| value.parse::<i64>().ok())
                    .map_or(Value::Null, |value| json!(value))
            } else {
                Value::Null
            };
            json!({
                "protocolVersion": Value::Null, "isCable": Value::Null,
                "userId": common("userId"), "userName": common("userName"),
                "userImg": common("userImg"), "userStatus": common("userStatus"),
                "role": common("role"), "score": common("score"),
                "deductInfo": common("deductInfo"), "errorNumber": common("errorNumber"),
                "accuracy": common("accuracy"), "speed": common("speed"),
                "existPageNumber": common("existPageNumber"), "existNumber": common("existNumber"),
                "isFinish": common("isFinish"), "duration": duration,
                "activeMillis": common("activeMillis"), "totalNumber": Value::Null,
                "pageCount": common("pageCount"), "ruleContent": Value::Null,
                "finishTime": common("finishTime"), "patValue": Value::Null,
                "pageAnalyzeVOS": common("pageAnalyzeVOS"), "existPage": Value::Null,
                "content": Value::Null
            })
        }
        Domain::Ticker => json!({
            "accuracy": common("accuracy"), "activeMillis": common("activeMillis"),
            "deductInfo": common("deductInfo"), "errorNumber": common("errorNumber"),
            "existNumber": common("existNumber"), "existPageNumber": common("existPageNumber"),
            "finishTime": common("finishTime"), "isFinish": common("isFinish"),
            "lack": common("lack"), "patValue": Value::Null, "role": common("role"),
            "score": common("score"), "speed": common("speed"), "speedLog": common("speedLog"),
            "statisticInfo": common("statisticInfo"), "userId": common("userId"),
            "userImg": common("userImg"), "userName": common("userName"),
            "userStatus": common("userStatus"), "validTime": common("validTime")
        }),
        Domain::Telex => json!({
            "isCable": Value::Null, "userId": common("userId"),
            "userName": common("userName"), "userImg": common("userImg"),
            "userStatus": common("userStatus"), "role": common("role"),
            "score": common("score"), "deductInfo": common("deductInfo"),
            "errorNumber": common("errorNumber"), "accuracy": scalar_text("accuracy"),
            "speed": scalar_text("speed"), "speedLog": common("speedLog"),
            "existPageNumber": common("existPageNumber"), "existNumber": common("existNumber"),
            "isFinish": common("isFinish"), "duration": common("validTime"),
            "validTime": common("validTime"), "totalNumber": Value::Null,
            "pageCount": Value::Null, "ruleContent": Value::Null,
            "finishTime": common("finishTime"), "patValue": Value::Null,
            "pageAnalyzeVOS": common("pageAnalyzeVOS"), "existPage": Value::Null,
            "content": Value::Null
        }),
    }
}

pub(super) fn finish_member(member: &Value, domain: Domain) -> Value {
    let mut result = detail_member(member, domain);
    result["pageAnalyzeVOS"] = Value::Null;
    if matches!(domain, Domain::Key) {
        result["existPageNumber"] = Value::Null;
        result["existNumber"] = Value::Null;
        result["duration"] = member["duration"]
            .as_str()
            .and_then(|value| value.parse::<i64>().ok())
            .map_or(Value::Null, |value| json!(value));
    } else if matches!(domain, Domain::Telex) {
        result["duration"] = Value::Null;
    }
    result
}

pub(super) fn text(value: &Value) -> Option<String> {
    match value {
        Value::String(value) => Some(value.clone()),
        Value::Number(value) => Some(value.to_string()),
        _ => None,
    }
}
pub(super) fn train_id(body: &Value) -> Outcome<String> {
    body.get("trainId")
        .or_else(|| body.get("id"))
        .and_then(text)
        .filter(|id| !id.trim().is_empty())
        .ok_or_else(|| invalid("训练ID不能为空"))
}
pub(super) fn column(field: &str) -> String {
    let mut name = String::with_capacity(field.len() + 4);
    for ch in field.chars() {
        if ch.is_ascii_uppercase() {
            name.push('_');
            name.push(ch.to_ascii_lowercase());
        } else {
            name.push(ch);
        }
    }
    name
}
// All field lists are server-owned; request keys never become SQL identifiers.
pub(super) fn projection(fields: &str) -> String {
    let pairs = fields
        .split_whitespace()
        .map(|field| {
            let col = column(field);
            if matches!(
                field,
                "createTime"
                    | "startTime"
                    | "endTime"
                    | "finishTime"
                    | "captureStartedAt"
                    | "receivedAt"
            ) {
                format!("'{field}',DATE_FORMAT(`{col}`,'%Y-%m-%dT%H:%i:%s.%f')")
            } else {
                format!("'{field}',`{col}`")
            }
        })
        .collect::<Vec<_>>()
        .join(",");
    format!("CAST(JSON_OBJECT({pairs}) AS CHAR)")
}
pub(super) const KEY_SYNC: &str = "id protocolVersion title totalNumber ruleId ruleContent createUser createTime startTime endTime status validTime messageType isAverage isRandom";
pub(super) const KEY_USER: &str = "id attempt captureStartedAt activeMillis capturePages accuracy deductInfo speed errorNumber statisticInfo score isFinish createTime finishTime duration trainId userId role content";
pub(super) const KEY_PAGE: &str = "id key pageNumber sort time trainId value";
pub(super) const KEY_VALUE: &str = "id pageNumber sort time key value trainId userId";
pub(super) const KEY_MORE: &str = "id trainId userId pageNumber moreGroup moreLine";
pub(super) const USER_SYNC: &str = "id wkno phone email userAccount userImg userName idCard userSex updateFlag status bday eday dday";
pub(super) fn train_fields(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => "id protocolVersion title isCable totalNumber ruleId ruleContent createUser createTime startTime endTime status validTime messageType isAverage isRandom trainType upId",
        Domain::Telex => "id protocolVersion title isCable totalNumber ruleId ruleContent ruleScore createUser createTime startTime endTime status validTime type patType trainType",
        Domain::Ticker => "id protocolVersion name isCable messageNumber ruleId ruleContent ruleScore createUser createTime startTime endTime status validTime type codeSort isAverage isRandom trainType upId",
    }
}
pub(super) fn member_fields(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => KEY_USER,
        Domain::Telex => "id attempt captureStartedAt activeMillis accuracy deductInfo speed errorNumber statisticInfo score isFinish createTime finishTime duration trainId userId role speedLog validTime validTimeLog",
        Domain::Ticker => "id attempt captureStartedAt activeMillis accuracy deductInfo speed errorNumber statisticInfo score isFinish createTime finishTime trainId userId role lack speedLog",
    }
}
pub(super) fn page_fields(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => KEY_PAGE,
        Domain::Telex => "id key pageNumber sort trainId value",
        Domain::Ticker => "id trainId floorNumber sort moresKey moresValue moresTime patKeys",
    }
}
pub(super) fn value_fields(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => KEY_VALUE,
        Domain::Telex => "id pageNumber sort key value trainId userId attempt captureIntervals receivedAt",
        Domain::Ticker => "id trainId userId floorNumber finishInfo messageBody resolver standard attempt captureIntervals receivedAt",
    }
}
use crate::platform::auth::AuthUser;
use sqlx::MySqlConnection;
use sqlx::Row;
use std::collections::HashMap;

pub(super) async fn rows(
    connection: &mut MySqlConnection,
    table: &str,
    fields: &str,
    filter: &str,
    binds: &[&str],
) -> Outcome<Vec<Value>> {
    let sql = format!(
        "SELECT {} AS data FROM `{table}` {filter}",
        projection(fields)
    );
    let mut query = sqlx::query(&sql);
    for value in binds {
        query = query.bind(*value);
    }
    query
        .fetch_all(connection)
        .await?
        .into_iter()
        .map(|row| {
            let raw = row.try_get::<String, _>("data").map_err(Failure::from)?;
            serde_json::from_str(&raw)
                .map_err(|error| Failure::Database(sqlx::Error::Decode(Box::new(error))))
        })
        .collect()
}
pub(super) async fn load_train(
    connection: &mut MySqlConnection,
    id: &str,
    domain: Domain,
    lock: bool,
) -> Outcome<Value> {
    rows(
        connection,
        table(domain),
        train_fields(domain),
        if lock {
            "WHERE id=? FOR UPDATE"
        } else {
            "WHERE id=?"
        },
        &[id],
    )
    .await?
    .pop()
    .ok_or_else(|| invalid("未查询到训练"))
}
pub(super) async fn load_member(
    connection: &mut MySqlConnection,
    id: &str,
    target: &str,
    domain: Domain,
) -> Outcome<Option<Value>> {
    Ok(rows(
        connection,
        user_table(domain),
        member_fields(domain),
        "WHERE train_id=? AND user_id=? ORDER BY id LIMIT 1",
        &[id, target],
    )
    .await?
    .pop())
}
pub(super) const STUDENT_MISSING: &str = "未查询到该用户的学员参训记录";

/// Members of a training with `userName`/`userImg` filled in from `t_user`; members whose user no
/// longer exists are dropped, exactly like the Java `LEFT JOIN ... AND !ISNULL(u.id)` query.
pub(super) async fn members_with_profiles(
    connection: &mut MySqlConnection,
    id: &str,
    domain: Domain,
    order: &str,
) -> Outcome<Vec<Value>> {
    let mut members = rows(
        connection,
        user_table(domain),
        member_fields(domain),
        &format!("WHERE train_id=? ORDER BY {order}"),
        &[id],
    )
    .await?;
    let profiles = rows(
        connection,
        "t_user",
        "id userName userImg",
        &format!(
            "WHERE id IN (SELECT CONVERT(user_id USING utf8mb4) COLLATE utf8mb4_general_ci FROM {} WHERE train_id=?)",
            user_table(domain)
        ),
        &[id],
    )
    .await?;
    let profiles: HashMap<_, _> = profiles
        .iter()
        .filter_map(|profile| profile["id"].as_str().map(|id| (id, profile)))
        .collect();
    members.retain(|member| {
        member["userId"]
            .as_str()
            .is_some_and(|id| profiles.contains_key(id))
    });
    for member in &mut members {
        let profile = member["userId"]
            .as_str()
            .and_then(|id| profiles.get(id))
            .map(|profile| (profile["userName"].clone(), profile["userImg"].clone()));
        if let Some((name, image)) = profile {
            member["userName"] = name;
            member["userImg"] = image;
        }
    }
    Ok(members)
}

/// Java `readableMember`: reading someone else's record needs the creator or a role-1 member of
/// the training (administrators are not exempt, 207); the target must be a member (else 202).
pub(super) async fn readable_member(
    connection: &mut MySqlConnection,
    train: &Value,
    actor: &AuthUser,
    requested: Option<&str>,
    domain: Domain,
) -> Outcome<(String, Value)> {
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    let target = requested.unwrap_or(actor.id.as_str());
    if target != actor.id && train["createUser"].as_str() != Some(actor.id.as_str()) {
        let organizer = load_member(connection, &id, &actor.id, domain)
            .await?
            .is_some_and(|member| member["role"] == 1);
        if !organizer {
            return Err(denied());
        }
    }
    let member = load_member(connection, &id, target, domain)
        .await?
        .ok_or_else(|| invalid("未查询到参训记录"))?;
    Ok((target.to_owned(), member))
}

/// Deletes the rows of `table` selected by `filter` one primary key at a time, in key order.
/// The child tables are indexed by primary key only, so a `DELETE ... WHERE train_id=?` would
/// lock every row it scans, including other trainings' rows. The caller holds the training row
/// lock, so the plainly read id list cannot change underneath it.
pub(super) async fn delete_rows(
    connection: &mut MySqlConnection,
    table: &str,
    filter: &str,
    binds: &[&str],
) -> Outcome<()> {
    let sql = format!("SELECT CAST(id AS CHAR) FROM `{table}` {filter} ORDER BY id");
    let mut query = sqlx::query_scalar::<_, String>(&sql);
    for value in binds {
        query = query.bind(*value);
    }
    let ids = query.fetch_all(&mut *connection).await?;
    let delete = format!("DELETE FROM `{table}` WHERE id=?");
    for id in ids {
        sqlx::query(&delete)
            .bind(id)
            .execute(&mut *connection)
            .await?;
    }
    Ok(())
}

/// True while `now < end_time + 60s` of a training in its closing status.
pub(super) async fn closing_window_open(
    connection: &mut MySqlConnection,
    id: &str,
    domain: Domain,
) -> Outcome<bool> {
    let sql = format!(
        "SELECT NOW(6) < DATE_ADD(end_time,INTERVAL 60 SECOND) FROM {} WHERE id=?",
        table(domain)
    );
    let open: Option<Option<i64>> = sqlx::query_scalar(&sql)
        .bind(id)
        .fetch_optional(connection)
        .await?;
    Ok(open.flatten() == Some(1))
}

pub(super) async fn manages(
    connection: &mut MySqlConnection,
    train: &Value,
    actor: &AuthUser,
    domain: Domain,
) -> Outcome<bool> {
    if actor.is_admin || train["createUser"].as_str() == Some(actor.id.as_str()) {
        return Ok(true);
    }
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    let sql = format!(
        "SELECT EXISTS(SELECT 1 FROM {} WHERE train_id=? AND user_id=? AND role=1)",
        user_table(domain)
    );
    Ok(sqlx::query_scalar::<_, i64>(&sql)
        .bind(&id)
        .bind(&actor.id)
        .fetch_one(connection)
        .await?
        != 0)
}
pub(super) async fn require_member(
    connection: &mut MySqlConnection,
    train: &Value,
    actor: &AuthUser,
    domain: Domain,
) -> Outcome<()> {
    if actor.is_admin || train["createUser"].as_str() == Some(actor.id.as_str()) {
        return Ok(());
    }
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    if load_member(connection, &id, &actor.id, domain)
        .await?
        .is_none()
    {
        return Err(denied());
    }
    Ok(())
}
pub(super) async fn readable_target(
    connection: &mut MySqlConnection,
    train: &Value,
    actor: &AuthUser,
    body: &Value,
    domain: Domain,
) -> Outcome<String> {
    require_member(connection, train, actor, domain).await?;
    let target = body["userId"]
        .as_str()
        .filter(|id| !id.is_empty())
        .unwrap_or(&actor.id);
    if target != actor.id && !manages(connection, train, actor, domain).await? {
        return Err(denied());
    }
    Ok(target.to_owned())
}
pub(super) async fn insert_fields(
    connection: &mut MySqlConnection,
    table: &str,
    fields: &str,
    data: &Value,
) -> Outcome<u64> {
    let fields: Vec<_> = fields.split_whitespace().collect();
    let columns = fields
        .iter()
        .map(|field| format!("`{}`", column(field)))
        .collect::<Vec<_>>()
        .join(",");
    let markers = vec!["?"; fields.len()].join(",");
    let sql = format!("INSERT INTO `{table}` ({columns}) VALUES ({markers})");
    let mut query = sqlx::query(&sql);
    for field in fields {
        query = query.bind(match &data[field] {
            Value::Null => None,
            Value::String(value) => Some(value.clone()),
            value => Some(value.to_string()),
        });
    }
    Ok(query.execute(connection).await?.last_insert_id())
}

/// Locks one member row. The member tables are indexed by primary key only, so a locking read
/// filtered by `train_id`/`user_id` would next-key lock every row it scans (including other
/// trainings' members). Callers hold the training row lock first; the id is read plainly and the
/// row is then locked by primary key.
pub(super) async fn locked_member(
    connection: &mut MySqlConnection,
    id: &str,
    target: &str,
    domain: Domain,
) -> Outcome<Value> {
    // Java telex `student()`/`lockedStudent()`: a user without a member row is 207, not 202.
    let missing = || {
        if matches!(domain, Domain::Telex) {
            denied()
        } else {
            invalid(STUDENT_MISSING)
        }
    };
    let member_id: Option<String> = sqlx::query_scalar(&format!(
        "SELECT CAST(id AS CHAR) FROM {} WHERE train_id=? AND user_id=? ORDER BY id LIMIT 1",
        user_table(domain)
    ))
    .bind(id)
    .bind(target)
    .fetch_optional(&mut *connection)
    .await?;
    let member_id = member_id.ok_or_else(missing)?;
    rows(
        connection,
        user_table(domain),
        member_fields(domain),
        "WHERE id=? FOR UPDATE",
        &[&member_id],
    )
    .await?
    .pop()
    .ok_or_else(missing)
}

fn validate_capture_timelines(timelines: &[Vec<(i64, i64)>]) -> Outcome<()> {
    let mut flattened = timelines.iter().flatten().copied().collect::<Vec<_>>();
    flattened.sort_unstable_by_key(|(start, _)| *start);
    let mut previous = 0_i64;
    for (start, end) in flattened {
        if start < previous {
            return Err(invalid("采集区间存在重叠"));
        }
        previous = end;
    }
    Ok(())
}
pub(super) fn member_capture_duration(
    member: &Value,
    domain: Domain,
    value_rows: &[Value],
) -> Outcome<i64> {
    if matches!(domain, Domain::Key) {
        let captures = stored_json(
            member["capturePages"]
                .as_str()
                .ok_or_else(|| invalid("缺少原始采集页索引"))?,
        )?;
        let object = captures
            .as_object()
            .ok_or_else(|| invalid("缺少原始采集页索引"))?;
        let mut all = Vec::with_capacity(object.len());
        let mut total = 0_i64;
        let expected_attempt = member["attempt"]
            .as_i64()
            .ok_or_else(|| terminal("训练轮次已变化，请重新读取训练"))?;
        for capture in object.values() {
            if capture["attempt"].as_i64() != Some(expected_attempt) {
                return Err(terminal("训练轮次已变化，请重新读取训练"));
            }
            let intervals = capture_intervals(&capture["intervals"])?;
            total = total
                .checked_add(interval_duration(&intervals, i64::MAX)?)
                .ok_or_else(|| invalid("采集时长溢出"))?;
            all.push(intervals);
        }
        validate_capture_timelines(&all)?;
        return Ok(total);
    }
    let mut total = 0_i64;
    let mut all = Vec::new();
    let expected_attempt = member["attempt"]
        .as_i64()
        .ok_or_else(|| terminal("训练轮次已变化，请重新读取训练"))?;
    for row in value_rows {
        if !matches!(domain, Domain::Telex) || row["sort"] == -1 {
            if row["attempt"].as_i64() != Some(expected_attempt) {
                return Err(terminal("训练轮次已变化，请重新读取训练"));
            }
            let capture = stored_json(
                row["captureIntervals"]
                    .as_str()
                    .ok_or_else(|| invalid("已保存页缺少原始采集时间轴"))?,
            )?;
            let intervals = capture_intervals(&capture)?;
            total = total
                .checked_add(interval_duration(&intervals, i64::MAX)?)
                .ok_or_else(|| invalid("采集时长溢出"))?;
            all.push(intervals);
        }
    }
    validate_capture_timelines(&all)?;
    Ok(total)
}

pub(super) fn settlement_number(value: &Value) -> Option<f64> {
    value
        .as_f64()
        .or_else(|| value.as_str().and_then(|text| text.parse::<f64>().ok()))
}

pub(super) fn json_array_text(value: &Value, message: &'static str) -> Outcome<Vec<Value>> {
    let text = value.as_str().ok_or_else(|| invalid(message))?;
    serde_json::from_str(text).map_err(|_| invalid(message))
}

fn string_array_text(value: &Value, message: &'static str) -> Outcome<Vec<String>> {
    json_array_text(value, message)?
        .into_iter()
        .map(|item| match item {
            Value::String(value) => Ok(value),
            _ => Err(invalid(message)),
        })
        .collect()
}

pub(super) fn source_group_text(row: &Value, domain: Domain) -> Outcome<String> {
    if matches!(domain, Domain::Telex) {
        return row["key"]
            .as_str()
            .map(str::to_owned)
            .ok_or_else(|| invalid("报底内容缺失"));
    }
    let field = if matches!(domain, Domain::Ticker) {
        "moresKey"
    } else {
        "key"
    };
    Ok(string_array_text(&row[field], "报底内容不是字符列表")?.concat())
}

fn submitted_group_text(row: &Value, domain: Domain) -> Outcome<String> {
    let field = if matches!(domain, Domain::Key) {
        "value"
    } else {
        "patKeys"
    };
    Ok(string_array_text(&row[field], "拍发字符记录无效")?.concat())
}

fn text_characters(text: &str) -> i64 {
    text.chars()
        .filter(|character| !character.is_whitespace() && *character != '?')
        .count() as i64
}

pub(super) fn frozen_full_score(train: &Value, domain: Domain) -> Outcome<f64> {
    let snapshot: Value = serde_json::from_str(
        train["ruleContent"]
            .as_str()
            .ok_or_else(|| invalid("训练缺少冻结评分规则"))?,
    )
    .map_err(|_| invalid("冻结评分规则不合法"))?;
    let score = if matches!(domain, Domain::Key) {
        settlement_number(&snapshot["score"])
    } else {
        settlement_number(&train["ruleScore"])
    };
    score
        .filter(|value| value.is_finite() && *value > 0.0)
        .ok_or_else(|| invalid("训练缺少有效的冻结规则满分"))
}

#[derive(Default)]
pub(super) struct KeySettlementStatistics {
    pub(super) pat_group: i64,
    pub(super) lack: i64,
    pub(super) more: i64,
    pub(super) lack_group: i64,
    pub(super) more_group: i64,
    pub(super) lack_line: i64,
    more_line: i64,
    alter_error: i64,
    pub(super) error: i64,
    pub(super) bunch_group: i64,
    lack_gap: i64,
}

#[derive(Clone)]
pub(super) struct KeyCapturedGroup {
    pub(super) raw: String,
    pub(super) value: String,
}

fn corrected_key_group(value: &str) -> String {
    value
        .rfind('?')
        .map(|index| value[index + 1..].to_owned())
        .unwrap_or_else(|| value.to_owned())
}

pub(super) fn key_captured_groups(
    values: &[Value],
    statistics: &mut KeySettlementStatistics,
) -> Outcome<(Vec<KeyCapturedGroup>, i64)> {
    let mut groups = Vec::new();
    let mut characters = 0_i64;
    for row in values {
        let text = submitted_group_text(row, Domain::Key)?;
        characters += text_characters(&text);
        let chars = text.chars().collect::<Vec<_>>();
        if chars.len() >= 8 && !text.contains('?') {
            let chunks = chars
                .chunks(4)
                .map(|chunk| chunk.iter().collect::<String>())
                .collect::<Vec<_>>();
            statistics.lack_gap += chunks.len().saturating_sub(1) as i64;
            for chunk in chunks {
                groups.push(KeyCapturedGroup {
                    raw: chunk.clone(),
                    value: chunk,
                });
            }
        } else {
            groups.push(KeyCapturedGroup {
                raw: text.clone(),
                value: text,
            });
        }
    }
    Ok((groups, characters))
}

pub(super) fn align_key_groups(
    sources: &[Value],
    captured: &[KeyCapturedGroup],
    statistics: &mut KeySettlementStatistics,
) -> Outcome<()> {
    if sources.is_empty() {
        statistics.more_line += captured.len().div_ceil(10) as i64;
        return Ok(());
    }
    let mut source_index = 0_usize;
    let mut capture_index = 0_usize;
    let mut missing_tail = 0_i64;
    while source_index < sources.len() {
        let expected = source_group_text(&sources[source_index], Domain::Key)?;
        let Some(group) = captured.get(capture_index) else {
            missing_tail += 1;
            source_index += 1;
            continue;
        };
        let actual = corrected_key_group(&group.value);
        if group.raw.contains('?') {
            statistics.alter_error += 1;
        }
        let mut handled = false;
        if expected != actual {
            // KeyPatUtils only attempts the four-group lookahead after the first group.
            if capture_index > 0 {
                for ahead in 1..=4 {
                    if captured
                        .get(capture_index + ahead)
                        .is_some_and(|next| corrected_key_group(&next.value) == expected)
                    {
                        statistics.more_group += ahead as i64;
                        capture_index += ahead;
                        handled = true;
                        break;
                    }
                }
            }
            if !handled {
                for next_source in source_index + 1..sources.len() {
                    if source_group_text(&sources[next_source], Domain::Key)? != actual {
                        continue;
                    }
                    let gap = next_source - source_index;
                    let mut bunch = false;
                    if gap == 10 {
                        for ahead in 1..5 {
                            if let (Some(value), Some(source)) = (
                                captured.get(capture_index + ahead),
                                sources.get(source_index + ahead),
                            ) {
                                if corrected_key_group(&value.value)
                                    == source_group_text(source, Domain::Key)?
                                {
                                    bunch = true;
                                    break;
                                }
                            }
                        }
                    }
                    if bunch {
                        statistics.bunch_group += 1;
                    } else {
                        statistics.lack_group += gap as i64;
                        source_index = next_source;
                    }
                    handled = true;
                    break;
                }
            }
            if !handled && actual.chars().count() > 4 && actual.contains(expected.as_str()) {
                statistics.more += 1;
                handled = true;
            }
            if !handled && actual.chars().count() < 4 && expected.contains(actual.as_str()) {
                statistics.lack += 1;
                handled = true;
            }
            if !handled {
                statistics.error += 1;
            }
        }
        statistics.pat_group += 1;
        capture_index += 1;
        if source_index == sources.len() - 1 && capture_index < captured.len() {
            let remaining = captured.len() - capture_index;
            statistics.more_line += remaining.div_ceil(10) as i64;
        }
        source_index += 1;
    }
    statistics.lack_line = missing_tail / 10;
    Ok(())
}

fn other_rate(rule: &Value, field: &str) -> f64 {
    settlement_number(&rule["other"][field])
        .or_else(|| settlement_number(&rule["other"][field]["l"]))
        .unwrap_or(0.0)
}

fn speed_adjustment(rule: &Value, speed: f64) -> f64 {
    let base = settlement_number(&rule["wpm"]["base"]).unwrap_or(0.0);
    if speed > base {
        (speed - base) * settlement_number(&rule["wpm"]["r"]).unwrap_or(0.0)
    } else if speed < base {
        (speed - base) * settlement_number(&rule["wpm"]["l"]).unwrap_or(0.0)
    } else {
        0.0
    }
}

pub(super) fn score_text(value: f64) -> String {
    if value.fract() == 0.0 {
        format!("{value:.0}")
    } else {
        format!("{value:.2}")
    }
}

fn decimal_scale(value: &Value) -> usize {
    text(value)
        .and_then(|value| value.split_once('.').map(|(_, fraction)| fraction.len()))
        .unwrap_or(0)
}

fn speed_score_text(rule: &Value, speed: f64, score: f64) -> String {
    if score == 0.0 {
        return "0".to_owned();
    }
    let field = if speed > settlement_number(&rule["wpm"]["base"]).unwrap_or(0.0) {
        "r"
    } else {
        "l"
    };
    let value = format!("{:.*}", decimal_scale(&rule["wpm"][field]), score);
    if score > 0.0 {
        format!("+{value}")
    } else {
        value
    }
}

fn deduction_text(value: f64, scale: usize) -> String {
    format!("-{:.*}", scale, if value == 0.0 { 0.0 } else { value })
}

fn other_rate_scale(rule: &Value, field: &str) -> usize {
    let value = &rule["other"][field];
    if value.is_object() {
        decimal_scale(&value["l"])
    } else {
        decimal_scale(value)
    }
}

pub(super) fn score_key_categories(
    train: &Value,
    rule: &Value,
    stats: &KeySettlementStatistics,
    speed: f64,
) -> Outcome<(f64, Value)> {
    let mut score = frozen_full_score(train, Domain::Key)?;
    let mut deduct = serde_json::Map::new();
    for (name, field, count) in [
        ("error", "errorCode", stats.error),
        ("lack", "muchLessCode", stats.lack),
        ("more", "muchLessCode", stats.more),
        ("lackLine", "muchLessLine", stats.lack_line),
        ("moreLine", "muchLessLine", stats.more_line),
        ("lackGroup", "muchLessGroups", stats.lack_group),
        ("moreGroup", "muchLessGroups", stats.more_group),
        ("alterError", "alterError", stats.alter_error),
        ("bunchGroup", "bunchGroup", stats.bunch_group),
        ("lackGap", "lessGap", stats.lack_gap),
    ] {
        let deduction = other_rate(rule, field) * count as f64;
        score -= deduction;
        deduct.insert(format!("{name}Number"), json!(count));
        deduct.insert(
            format!("{name}Score"),
            json!(deduction_text(deduction, other_rate_scale(rule, field))),
        );
    }
    let speed_score = speed_adjustment(rule, speed);
    score += speed_score;
    deduct.insert("speedNumber".into(), json!(score_text(speed)));
    deduct.insert(
        "speedScore".into(),
        json!(speed_score_text(rule, speed, speed_score)),
    );
    Ok((score, Value::Object(deduct)))
}

pub(super) fn score_telex_categories(
    train: &Value,
    rule: &Value,
    stats: &crate::shared::telex_handle::Stats,
    speed: i64,
) -> Outcome<(f64, Value)> {
    let mut score = frozen_full_score(train, Domain::Telex)?;
    let mut deduct = serde_json::Map::new();
    // Java reads `alterError`; a rule without it (the UI never writes one) is a Java NPE.
    // Rust deliberately falls back to `correctMistakes` like the personal telex service does.
    let correct_field = if rule["other"]["alterError"].is_null() {
        "correctMistakes"
    } else {
        "alterError"
    };
    for (name, field, count) in [
        ("errorCode", "errorCode", stats.error_code),
        ("muchLessLine", "muchLessLine", stats.much_less_line),
        ("muchLessGroups", "muchLessGroups", stats.much_less_groups),
        ("muchLessCode", "muchLessCode", stats.much_less_code),
        ("lessReturnLine", "lessReturnLine", stats.less_return_line),
        ("lessPage", "lessPage", stats.less_page),
        ("errorPage", "errorPage", stats.error_page),
        ("nonStandart", "nonStandart", stats.non_standard),
        ("correctMistakes", correct_field, stats.correct_mistakes),
    ] {
        let deduction = other_rate(rule, field) * count as f64;
        score -= deduction;
        deduct.insert(format!("{name}Number"), json!(count));
        deduct.insert(
            format!("{name}Score"),
            json!(deduction_text(deduction, other_rate_scale(rule, field))),
        );
    }
    // Java ScoreMath.wpmScore on the stored integer speed; the text follows the direction (not the
    // sign of the result) so a zero coefficient still prints "+0"/"-0". Equal speed adds no key.
    let base = settlement_number(&rule["wpm"]["base"]).unwrap_or(0.0) as i64;
    let speed_score = if speed > base {
        let rate = settlement_number(&rule["wpm"]["r"]).unwrap_or(0.0);
        let value = rate * (speed - base) as f64;
        deduct.insert(
            "speedScore".into(),
            json!(format!("+{:.*}", decimal_scale(&rule["wpm"]["r"]), value)),
        );
        value
    } else if speed < base {
        let rate = settlement_number(&rule["wpm"]["l"]).unwrap_or(0.0);
        let value = rate * (base - speed) as f64;
        deduct.insert(
            "speedScore".into(),
            json!(format!("-{:.*}", decimal_scale(&rule["wpm"]["l"]), value)),
        );
        -value
    } else {
        0.0
    };
    score += speed_score;
    Ok((score, Value::Object(deduct)))
}
