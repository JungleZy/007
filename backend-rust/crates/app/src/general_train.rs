use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::{MySqlConnection, MySqlPool, Row};
use std::collections::HashMap;

#[derive(Clone, Copy)]
enum Domain {
    Key,
    Telex,
    Ticker,
}
#[derive(Debug, Deserialize)]
pub(crate) struct QueryId {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
    #[serde(rename = "attempt")]
    attempt: Option<i32>,
}
fn table(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => "general_key_pat",
        Domain::Telex => "general_telex_pat",
        Domain::Ticker => "general_ticker_pat",
    }
}
fn ws_kind(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => "key",
        Domain::Telex => "telex",
        Domain::Ticker => "ticker",
    }
}

fn id_is_int(domain: Domain) -> bool {
    !matches!(domain, Domain::Telex)
}

fn user_table(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => "general_key_pat_user",
        Domain::Telex => "general_telex_pat_user",
        Domain::Ticker => "general_ticker_pat_train_user",
    }
}
fn page_table(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => "general_key_pat_page",
        Domain::Telex => "general_telex_pat_page",
        Domain::Ticker => "general_ticker_pat_train_page",
    }
}
fn value_table(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => "general_key_pat_user_value",
        Domain::Telex => "general_telex_pat_user_value",
        Domain::Ticker => "general_ticker_pat_train_user_value",
    }
}
#[derive(Debug)]
enum Failure {
    Business(i32, &'static str),
    Database(sqlx::Error),
}
impl From<sqlx::Error> for Failure {
    fn from(error: sqlx::Error) -> Self {
        Self::Database(error)
    }
}
type Outcome<T> = Result<T, Failure>;
fn invalid(message: &'static str) -> Failure {
    Failure::Business(202, message)
}
fn denied() -> Failure {
    Failure::Business(207, "无权限")
}
fn terminal(message: &'static str) -> Failure {
    Failure::Business(208, message)
}
fn response(result: Outcome<Value>) -> Response {
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

fn train_response(mut train: Value, domain: Domain) -> Value {
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

fn detail_member(member: &Value, domain: Domain) -> Value {
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

fn finish_member(member: &Value, domain: Domain) -> Value {
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

fn text(value: &Value) -> Option<String> {
    match value {
        Value::String(value) => Some(value.clone()),
        Value::Number(value) => Some(value.to_string()),
        _ => None,
    }
}
fn train_id(body: &Value) -> Outcome<String> {
    body.get("trainId")
        .or_else(|| body.get("id"))
        .and_then(text)
        .filter(|id| !id.trim().is_empty())
        .ok_or_else(|| invalid("训练ID不能为空"))
}
fn column(field: &str) -> String {
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
fn projection(fields: &str) -> String {
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
const KEY_SYNC: &str = "id protocolVersion title totalNumber ruleId ruleContent createUser createTime startTime endTime status validTime messageType isAverage isRandom";
const KEY_USER: &str = "id attempt captureStartedAt activeMillis capturePages accuracy deductInfo speed errorNumber statisticInfo score isFinish createTime finishTime duration trainId userId role content";
const KEY_PAGE: &str = "id key pageNumber sort time trainId value";
const KEY_VALUE: &str = "id pageNumber sort time key value trainId userId";
const KEY_MORE: &str = "id trainId userId pageNumber moreGroup moreLine";
const USER_SYNC: &str = "id wkno phone email userAccount userImg userName idCard userSex updateFlag status bday eday dday";
fn train_fields(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => "id protocolVersion title isCable totalNumber ruleId ruleContent createUser createTime startTime endTime status validTime messageType isAverage isRandom trainType upId",
        Domain::Telex => "id protocolVersion title isCable totalNumber ruleId ruleContent ruleScore createUser createTime startTime endTime status validTime type patType trainType",
        Domain::Ticker => "id protocolVersion name isCable messageNumber ruleId ruleContent ruleScore createUser createTime startTime endTime status validTime type codeSort isAverage isRandom trainType upId",
    }
}
fn member_fields(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => KEY_USER,
        Domain::Telex => "id attempt captureStartedAt activeMillis accuracy deductInfo speed errorNumber statisticInfo score isFinish createTime finishTime duration trainId userId role speedLog validTime validTimeLog",
        Domain::Ticker => "id attempt captureStartedAt activeMillis accuracy deductInfo speed errorNumber statisticInfo score isFinish createTime finishTime trainId userId role lack speedLog",
    }
}
fn page_fields(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => KEY_PAGE,
        Domain::Telex => "id key pageNumber sort trainId value",
        Domain::Ticker => "id trainId floorNumber sort moresKey moresValue moresTime patKeys",
    }
}
fn value_fields(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => KEY_VALUE,
        Domain::Telex => "id pageNumber sort key value trainId userId attempt captureIntervals receivedAt",
        Domain::Ticker => "id trainId userId floorNumber finishInfo messageBody resolver standard attempt captureIntervals receivedAt",
    }
}
async fn rows(
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
async fn load_train(
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
async fn load_member(
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
const STUDENT_MISSING: &str = "未查询到该用户的学员参训记录";

/// Members of a training with `userName`/`userImg` filled in from `t_user`; members whose user no
/// longer exists are dropped, exactly like the Java `LEFT JOIN ... AND !ISNULL(u.id)` query.
async fn members_with_profiles(
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
async fn readable_member(
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
async fn delete_rows(
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
async fn closing_window_open(
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

async fn manages(
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
async fn require_member(
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
async fn readable_target(
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
async fn insert_fields(
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
/// Jackson's `Boolean` coercion as the add form relies on it: SelectCable.vue posts the cable's
/// integer `codeSort` (1 means long code), which Jackson reads as `true`; any non-zero number is
/// true, and the strings "true"/"True" are true.
fn jackson_flag(value: &Value) -> bool {
    match value {
        Value::Bool(flag) => *flag,
        Value::Number(number) => number.as_f64().is_some_and(|number| number != 0.0),
        Value::String(text) => matches!(text.as_str(), "true" | "True"),
        _ => false,
    }
}
async fn add(state: AppState, actor: AuthUser, mut body: Value, domain: Domain) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        if !body.is_object() { return Err(invalid("请求参数错误")); }
        let total_field = if matches!(domain,Domain::Ticker) { "messageNumber" } else { "totalNumber" };
        let total = body[total_field].as_i64().filter(|total| *total > 0 && *total <= i32::MAX as i64).ok_or_else(|| invalid("训练总组数不合法"))?;
        let cable = if matches!(domain,Domain::Ticker) { body["isCable"].as_i64().unwrap_or(0) } else { body["isCable"].as_i64().ok_or_else(|| invalid("是否使用电缆报底不能为空"))? };
        let participants = body["userId"].as_array().ok_or_else(|| invalid("参训人员不能为空"))?.iter().map(|id| id.as_str().filter(|id| !id.is_empty()).map(str::to_owned).ok_or_else(|| invalid("参训人员ID不合法"))).collect::<Outcome<Vec<_>>>()?;
        if matches!(domain,Domain::Ticker) {
            for field in ["codeSort","isAverage","isRandom"] { body[field] = json!(i32::from(jackson_flag(&body[field]))); }
        }
        let kind_field = if matches!(domain,Domain::Key) { "messageType" } else { "type" };
        if cable == 0 && body[kind_field].as_i64().is_none() { return Err(invalid("报文类型不能为空")); }
        if cable == 0 && matches!(domain,Domain::Key) && (body["isAverage"].as_i64().is_none() || body["isRandom"].as_i64().is_none()) { return Err(invalid("报文生成方式不能为空")); }
        let rule_id = body["ruleId"].as_str().ok_or_else(|| invalid("评分规则ID不能为空"))?.to_owned();
        let mut transaction = pool.begin().await?;
        let rule = rows(&mut transaction,"t_grading_rule","id type title score status isDefault content","WHERE id=? FOR SHARE",&[&rule_id]).await?.pop().ok_or_else(|| invalid("未查询到评分规则"))?;
        body["ruleContent"] = if matches!(domain,Domain::Key) { json!(rule.to_string()) } else { rule["content"].clone() };
        body["ruleScore"] = rule["score"].clone();
        frozen_rule(&body,domain)?;
        let now: String = sqlx::query_scalar("SELECT DATE_FORMAT(NOW(6),'%Y-%m-%d %H:%i:%s.%f')").fetch_one(&mut *transaction).await?;
        body["createUser"] = json!(actor.id); body["createTime"] = json!(now); body["status"] = json!(0); body["validTime"] = json!(0); body["protocolVersion"] = json!(1); body["isCable"] = json!(cable);
        body["startTime"] = Value::Null; body["endTime"] = Value::Null;
        if !id_is_int(domain) { body["id"] = json!(uuid::Uuid::new_v4().to_string()); }
        let fields = train_fields(domain).split_whitespace().filter(|field| *field != "id" || !id_is_int(domain)).collect::<Vec<_>>().join(" ");
        let generated = insert_fields(&mut transaction,table(domain),&fields,&body).await?;
        if id_is_int(domain) { body["id"] = json!(generated); }
        for (target,role) in participants.iter().map(|id| (id,0)).chain(std::iter::once((&actor.id,1))) {
            let creator = role == 1 && target == &actor.id;
            let null_finish = matches!(domain, Domain::Ticker) || (creator && !matches!(domain, Domain::Telex));
            let mut member = json!({"trainId":body["id"],"userId":target,"role":role,"isFinish":if null_finish { Value::Null } else { json!(0) },"score":if creator { Value::Null } else { json!(0) },"accuracy":if creator { Value::Null } else { json!("0.00") },"errorNumber":if creator { Value::Null } else { json!(0) },"attempt":0,"createTime":now});
            let fields = if matches!(domain,Domain::Telex) { member["id"] = json!(uuid::Uuid::new_v4().to_string()); "id trainId userId role isFinish score accuracy errorNumber attempt createTime" } else { "trainId userId role isFinish score accuracy errorNumber attempt createTime" };
            insert_fields(&mut transaction,user_table(domain),fields,&member).await?;
        }
        if cable == 0 {
            let generated = total.min(200) as usize;
            for offset in (0..generated).step_by(100) {
                let count = (generated-offset).min(100);
                let groups = if matches!(domain,Domain::Telex) && body["type"] == 0 {
                    match body["patType"].as_i64() { Some(0) => numeric_pairs(count,false), Some(1) => numeric_pairs(count,true), Some(2) => generate_groups(&body,domain,count)?, _ => return Err(invalid("拍发类型不合法")) }
                } else { generate_groups(&body,domain,count)? };
                for (sort,group) in groups.into_iter().enumerate() {
                    save_generated_group(&mut transaction,&body["id"],domain,offset/100+1,sort,group).await?;
                }
            }
        } else {
            let cable_id = body["cableId"].as_str().ok_or_else(|| invalid("固定报编号不能为空"))?;
            let floors = rows(&mut transaction,"t_cable_floor","floorNumber sort moresKey","WHERE cable_id=? ORDER BY floor_number,sort",&[cable_id]).await?;
            let mut grouped: Vec<Vec<String>> = Vec::new();
            let mut previous = Value::Null;
            for floor in floors {
                if floor["floorNumber"] != previous { grouped.push(Vec::new()); previous = floor["floorNumber"].clone(); }
                let keys = stored_json(floor["moresKey"].as_str().ok_or_else(|| invalid("固定报内容缺失"))?)?;
                let group = keys.as_array().ok_or_else(|| invalid("固定报内容不是字符列表"))?.iter().map(|key| key.as_str().ok_or_else(|| invalid("固定报字符不合法"))).collect::<Outcome<Vec<_>>>()?.concat();
                grouped.last_mut().ok_or_else(|| invalid("固定报楼层缺失"))?.push(group);
            }
            let start = body["startPage"].as_u64().unwrap_or(1).saturating_sub(1) as usize;
            let start = if start < grouped.len() { start } else { 0 };
            let pages = total as usize / 100;
            if pages == 0 || pages > grouped.len().saturating_sub(start) { return Err(invalid("所选电缆可用楼层不足")); }
            for (page,groups) in grouped.into_iter().skip(start).take(pages).enumerate() {
                for (sort,group) in groups.into_iter().enumerate() { save_generated_group(&mut transaction,&body["id"],domain,page+1,sort,group).await?; }
            }
        }
        let id = text(&body["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
        let title = if matches!(domain, Domain::Ticker) {
            text(&body["name"]).unwrap_or_default()
        } else {
            text(&body["title"]).unwrap_or_default()
        };
        let result = load_train(&mut transaction, &id, domain, false).await?;
        transaction.commit().await?;
        super::ws::notify_general_new_train(ws_kind(domain), &id, &title, &participants).await;
        Ok(train_response(result, domain))
    }.await)
}
async fn list(state: AppState, user: AuthUser, body: Value, domain: Domain) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let mut connection = pool.acquire().await?;
        let page = body["page"].as_i64().unwrap_or(1).max(1);
        let size = body["rows"].as_i64().unwrap_or(20).clamp(1, 200);
        let filter = format!("WHERE id IN (SELECT train_id FROM {} WHERE user_id=?)", user_table(domain));
        let count = format!("SELECT COUNT(*) FROM {} {filter}", table(domain));
        let total: i64 = sqlx::query_scalar(&count).bind(&user.id).fetch_one(&mut *connection).await?;
        let filter = format!("{filter} ORDER BY create_time DESC,id DESC LIMIT {size} OFFSET {}", (page - 1).saturating_mul(size));
        let mut data = rows(&mut connection, table(domain), train_fields(domain), &filter, &[&user.id]).await?;
        {
            // Java findAll: every item carries its member list (list.js runs `userInfoList.forEach`),
            // booleans for codeSort/isRandom, and the real page count for fixed (cable) reports.
            // The electronic-key list leaves the total alone and hides the frozen rule.
            let mut items = Vec::with_capacity(data.len());
            for mut train in data {
                let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
                if matches!(domain, Domain::Key | Domain::Telex) {
                    train["ruleContent"] = Value::Null;
                }
                if matches!(domain, Domain::Ticker) && train["isCable"] == 1 {
                    let pages: i64 = sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {} WHERE train_id=?", page_table(domain)))
                        .bind(&id)
                        .fetch_one(&mut *connection)
                        .await?;
                    train["messageNumber"] = json!(pages);
                }
                let mut members = members_with_profiles(&mut connection, &id, domain, "id").await?;
                if matches!(domain, Domain::Key) {
                    // Java's findAll member DTO (`find_general_key_pat_user_dto`) has no active time.
                    for member in &mut members { member["activeMillis"] = Value::Null; }
                }
                let infos = members.iter().map(|member| {
                    if matches!(domain, Domain::Telex) {
                        // Java's findAll member DTO (`find_general_telex_pat_user_simple_dto`)
                        // carries only the user, profile and role.
                        let mut info = detail_member(&Value::Null, domain);
                        for field in ["userId", "userName", "userImg", "role"] { info[field] = member[field].clone(); }
                        info["pageAnalyzeVOS"] = Value::Null;
                        info
                    } else { detail_member(member, domain) }
                }).collect::<Vec<_>>();
                let mut item = train_response(train, domain);
                item["userInfoList"] = json!(infos);
                items.push(item);
            }
            data = items;
        }
        Ok(json!({"totalPage":(total+size-1)/size,"totalNumber":total,"currentPage":page,"pageSize":size,"data":data}))
    }.await)
}
async fn detail(state: AppState, user: AuthUser, body: Value, domain: Domain) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(
        async {
            let id = train_id(&body)?;
            let mut connection = pool.acquire().await?;
            let mut train = load_train(&mut connection, &id, domain, false).await?;
            if train["isCable"] == 1 {
                let page_column = if matches!(domain, Domain::Ticker) {
                    "floor_number"
                } else {
                    "page_number"
                };
                let total_field = if matches!(domain, Domain::Ticker) {
                    "messageNumber"
                } else {
                    "totalNumber"
                };
                let count: i64 = sqlx::query_scalar(&format!(
                    "SELECT COUNT(*) FROM {} WHERE train_id=?",
                    page_table(domain)
                ))
                .bind(&id)
                .fetch_one(&mut *connection)
                .await?;
                let max_page: Option<i64> = sqlx::query_scalar(&format!(
                    "SELECT MAX({page_column}) FROM {} WHERE train_id=?",
                    page_table(domain)
                ))
                .bind(&id)
                .fetch_one(&mut *connection)
                .await?;
                train[total_field] = json!(count);
                train["pageCount"] = max_page.map_or(Value::Null, |value| json!(value));
            }
            // Java `detail` (hand-key): `uid` selects whose record is read. Reading another
            // member needs the creator or a role-1 member (207, administrators not exempt), a
            // non-member target is 202, and the member list is narrowed to that user.
            let requested = if matches!(domain, Domain::Ticker) {
                body.get("uid").and_then(Value::as_str)
            } else {
                None
            };
            if matches!(domain, Domain::Ticker) {
                readable_member(&mut connection, &train, &user, requested, domain).await?;
            } else {
                require_member(&mut connection, &train, &user, domain).await?;
            }
            let mut members = members_with_profiles(&mut connection, &id, domain, "role DESC,id").await?;
            if let Some(uid) = requested.filter(|uid| !uid.is_empty()) {
                members.retain(|member| member["userId"].as_str() == Some(uid));
            }
            let kind = match domain {
                Domain::Key => "key",
                Domain::Telex => "telex",
                Domain::Ticker => "ticker",
            };
            for member in &mut members {
                let target = member["userId"]
                    .as_str()
                    .ok_or_else(|| invalid("参训用户ID缺失"))?
                    .to_owned();
                if matches!(domain, Domain::Key) && train["protocolVersion"] != 1 {
                    member["activeMillis"] = Value::Null;
                }
                if matches!(domain, Domain::Key) && train["protocolVersion"] == 1 {
                    let captures = stored_json(
                        member["capturePages"]
                            .as_str()
                            .ok_or_else(|| invalid("缺少原始采集页索引"))?,
                    )?;
                    let object = captures
                        .as_object()
                        .ok_or_else(|| invalid("缺少原始采集页索引"))?;
                    let mut pages = object
                        .keys()
                        .map(|page| page.parse::<i64>().map_err(|_| invalid("采集页索引无效")))
                        .collect::<Outcome<Vec<_>>>()?;
                    pages.sort_unstable();
                    member["existPageNumber"] = json!(pages.len());
                    member["existNumber"] = json!(pages);
                }
                if matches!(domain, Domain::Ticker) && requested.is_some() {
                    let saved: i64 = sqlx::query_scalar(&format!(
                        "SELECT COUNT(*) FROM {} WHERE train_id=? AND user_id=?",
                        value_table(domain)
                    ))
                    .bind(&id)
                    .bind(&target)
                    .fetch_one(&mut *connection)
                    .await?;
                    let floors: Vec<i32> = sqlx::query_scalar(&format!(
                        "SELECT DISTINCT floor_number FROM {} WHERE train_id=? AND user_id=? ORDER BY floor_number",
                        value_table(domain)
                    ))
                    .bind(&id)
                    .bind(&target)
                    .fetch_all(&mut *connection)
                    .await?;
                    member["existPageNumber"] = json!(saved);
                    member["existNumber"] = json!(floors);
                }
                if matches!(domain, Domain::Ticker) && train["protocolVersion"] == 1 {
                    member["validTime"] = json!(member["activeMillis"].as_i64().unwrap_or(0) / 1000);
                }
                if matches!(domain, Domain::Key) {
                    member["pageAnalyzeVOS"] = page_analyze(&mut connection, &train, member).await?;
                }
                if matches!(domain, Domain::Telex) {
                    // Java's detail member DTO (`find_general_telex_pat_user_dto`) has no
                    // valid_time/speed_log columns; the statistics are computed per page.
                    for field in ["validTime", "speedLog"] { member[field] = Value::Null; }
                    member["pageAnalyzeVOS"] = telex_page_analyze(&mut connection, &train, &target).await?;
                }
                member["userStatus"] =
                    json!(super::ws::general_connection_status(kind, &id, &target)
                        .await
                        .unwrap_or(0));
            }
            let members = members
                .iter()
                .map(|member| detail_member(member, domain))
                .collect::<Vec<_>>();
            train = train_response(train, domain);
            train["userInfoList"] = json!(members);
            Ok(train)
        }
        .await,
    )
}
async fn set_status(
    state: AppState,
    user: AuthUser,
    id: String,
    status: i32,
    domain: Domain,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let ticker = matches!(domain, Domain::Ticker);
        // Java answers the hand-key status endpoint with "ok" and reports a restart as 202.
        let done = || if ticker { json!("ok") } else { json!("") };
        let mut transaction = pool.begin().await?;
        let train = load_train(&mut transaction, &id, domain, true).await?;
        if !manages(&mut transaction, &train, &user, domain).await? { return Err(denied()); }
        let protocol = || invalid("旧训练缺少原始采集协议，请重新创建训练");
        if ticker && train["protocolVersion"] != 1 { return Err(invalid("旧训练缺少原始采集协议，请重新创建训练；历史成绩保持不变")); }
        if !matches!(status, 1 | 2) { return Err(invalid("训练状态不合法")); }
        if !ticker && train["protocolVersion"] != 1 { return Err(protocol()); }
        if train["status"] == status || (!matches!(domain, Domain::Telex) && train["status"] == 3 && status == 2) { transaction.commit().await?; return Ok(done()); }
        if status == 1 {
            if train["status"] != 0 { return Err(if matches!(domain, Domain::Telex) { terminal("训练不能重新开始") } else { invalid("训练不能重新开始") }); }
            frozen_rule(&train, domain)?;
            let sql = format!("UPDATE {} SET status=1,start_time=NOW(6) WHERE id=?", table(domain));
            sqlx::query(&sql).bind(&id).execute(&mut *transaction).await?;
            // The member tables are indexed by primary key only: read the student ids plainly
            // (the training row is locked) and stamp the shared start time row by row.
            let started: String = sqlx::query_scalar(&format!("SELECT DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s.%f') FROM {} WHERE id=?", table(domain)))
                .bind(&id).fetch_one(&mut *transaction).await?;
            let students: Vec<String> = sqlx::query_scalar(&format!("SELECT CAST(id AS CHAR) FROM {} WHERE train_id=? AND role=0 ORDER BY id", user_table(domain)))
                .bind(&id).fetch_all(&mut *transaction).await?;
            let sql = format!("UPDATE {} SET capture_started_at=? WHERE id=?", user_table(domain));
            for student in students {
                sqlx::query(&sql).bind(&started).bind(student).execute(&mut *transaction).await?;
            }
        } else {
            if train["status"] != 1 { return Err(if ticker { invalid("训练不在进行中") } else { terminal("训练不在进行中") }); }
            let closing = closing_status(domain);
            let sql = format!("UPDATE {} SET status=?,end_time=NOW(6),valid_time=GREATEST(0,TIMESTAMPDIFF(SECOND,start_time,NOW(6))) WHERE id=?", table(domain));
            sqlx::query(&sql).bind(closing).bind(&id).execute(&mut *transaction).await?;
            if closing == 3 {
                let unfinished: i64 = sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {} WHERE train_id=? AND role=0 AND COALESCE(is_finish,0)<>1", user_table(domain)))
                    .bind(&id).fetch_one(&mut *transaction).await?;
                if unfinished == 0 {
                    sqlx::query(&format!("UPDATE {} SET status=2 WHERE id=?", table(domain))).bind(&id).execute(&mut *transaction).await?;
                }
            }
        }
        transaction.commit().await?;
        Ok(done())
    }.await)
}
async fn delete(state: AppState, user: AuthUser, id: String, domain: Domain) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(
        async {
            // Excludes a socket opening this room until the room is closed (Java RoomLifecycleLocks).
            let _room = super::ws::general_room_lifecycle(ws_kind(domain), &id).await;
            let mut transaction = pool.begin().await?;
            let train = load_train(&mut transaction, &id, domain, true).await?;
            if !manages(&mut transaction, &train, &user, domain).await? {
                return Err(denied());
            }
            for child in [value_table(domain), page_table(domain), user_table(domain)] {
                delete_rows(&mut transaction, child, "WHERE train_id=?", &[&id]).await?;
            }
            if matches!(domain, Domain::Key) {
                for child in [
                    "general_key_pat_train_more",
                    "general_key_pat_user_value_resolver",
                ] {
                    delete_rows(&mut transaction, child, "WHERE train_id=?", &[&id]).await?;
                }
            }
            sqlx::query(&format!("DELETE FROM {} WHERE id=?", table(domain)))
                .bind(&id)
                .execute(&mut *transaction)
                .await?;
            transaction.commit().await?;
            super::ws::close_general_room(ws_kind(domain), &id).await;
            Ok(json!(true))
        }
        .await,
    )
}
async fn statistic(state: AppState, actor: AuthUser, body: Value, domain: Domain) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let id = train_id(&body)?;
        let mut connection = pool.acquire().await?;
        let train = load_train(&mut connection,&id,domain,false).await?;
        require_member(&mut connection,&train,&actor,domain).await?;
        let members = rows(&mut connection,user_table(domain),member_fields(domain),"WHERE train_id=? AND role=0 ORDER BY id",&[&id]).await?;
        let mut counts = [0i64;3];
        let mut totals = HashMap::<String,i64>::new();
        let mut tendencies = Vec::with_capacity(members.len());
        for member in &members {
            let Some(score) = member["score"].as_f64() else { continue; };
            counts[if score >= 90.0 { 0 } else if score >= 70.0 { 1 } else { 2 }] += 1;
            let source = if matches!(domain,Domain::Ticker) { "statisticInfo" } else { "deductInfo" };
            if let Some(text) = member[source].as_str().filter(|text| !text.trim().is_empty()) {
                let info = stored_json(text)?;
                if let Some(info) = info.as_object() {
                    for (key,value) in info { if let Some(number) = value.as_i64() { *totals.entry(key.clone()).or_default() += number; } }
                }
            }
            let target = member["userId"].as_str().ok_or_else(|| invalid("参训用户ID缺失"))?;
            let Some(profile) = rows(&mut connection,"t_user","id userName userImg","WHERE id=?",&[target]).await?.pop() else { continue; };
            // Java `findByUseridTop2` (hand-key): settled trainings only, newest created first.
            let history_filter = if matches!(domain,Domain::Ticker) { "WHERE user_id=? AND create_time<? AND is_finish=1 ORDER BY create_time DESC LIMIT 2" } else { "WHERE user_id=? AND create_time<? ORDER BY finish_time DESC LIMIT 2" };
            let history = rows(&mut connection,user_table(domain),"score",history_filter,&[target,member["createTime"].as_str().unwrap_or("")]).await?;
            let mut tendency = json!({"userId":target,"userName":profile["userName"],"userImg":profile["userImg"],"thisScore":member["score"],"lastScore":history.first().map(|row| &row["score"]).unwrap_or(&Value::Null),"lastLastScore":history.get(1).map(|row| &row["score"]).unwrap_or(&Value::Null)});
            if !matches!(domain,Domain::Ticker) {
                // Java's statistics builder sets no status for the electronic key.
                tendency["status"] = if matches!(domain, Domain::Key) { Value::Null } else { member["isFinish"].clone() };
                for field in ["lastScore","lastLastScore"] { if tendency[field].is_null() { tendency[field] = json!(0); } }
            }
            tendencies.push(tendency);
        }
        let mut report = json!({});
        for (name,count) in ["good","nice","belowStandard"].into_iter().zip(counts) {
            report[name] = json!({"peopleNumber":count,"rate":percentage(count,members.len() as i64)});
        }
        let mut errors = json!({});
        let mut result = json!({"schoolReport":report,"userTendencyVO":tendencies});
        if matches!(domain,Domain::Ticker) {
            for (source,target) in [("dot","dot"),("line","line"),("code","codeGap"),("word","wordGap"),("group","groupGap")] {
                let total: i64 = ["MinNumber","MaxNumber","PerfectNumber"].iter().map(|suffix| totals.get(&format!("{source}{suffix}")).copied().unwrap_or(0)).sum();
                for direction in ["Min","Max"] { errors[format!("{target}{direction}")] = json!(percentage(totals.get(&format!("{source}{direction}Number")).copied().unwrap_or(0),total)); }
            }
            result["errorInfoVO"] = errors;
        } else {
            let fields = if matches!(domain,Domain::Key) { "errorNumber lackNumber moreNumber lackLineNumber moreLineNumber lackGroupNumber moreGroupNumber" } else { "errorCodeNumber errorPageNumber correctMistakesNumber lessPageNumber lessReturnLineNumber muchLessCodeNumber muchLessGroupsNumber muchLessLineNumber nonStandartNumber" };
            for field in fields.split_whitespace() { errors[field] = json!(totals.get(field).copied().unwrap_or(0)); }
            result["errorCollect"] = errors;
        }
        Ok(result)
    }.await)
}

pub(crate) async fn key_add(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    add(s, u, b, Domain::Key).await
}
pub(crate) async fn key_list(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    list(s, u, b, Domain::Key).await
}
pub(crate) async fn key_detail(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    detail(s, u, b, Domain::Key).await
}
pub(crate) async fn key_update(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    let id = b
        .get("trainId")
        .map(Value::to_string)
        .unwrap_or_default()
        .trim_matches('"')
        .to_owned();
    let status = b
        .get("status")
        .and_then(Value::as_i64)
        .and_then(|status| i32::try_from(status).ok())
        .unwrap_or(-1);
    set_status(s, u, id, status, Domain::Key).await
}
pub(crate) async fn key_finish(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    finish_participant(s, u, b, Domain::Key).await
}
pub(crate) async fn key_reset(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    participant_action(s, u, b, Domain::Key, true).await
}
pub(crate) async fn key_start(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    participant_action(
        s,
        u,
        json!({"trainId":q.train_id,"attempt":q.attempt}),
        Domain::Key,
        false,
    )
    .await
}
pub(crate) async fn key_delete(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    delete(s, u, q.train_id.unwrap_or_default(), Domain::Key).await
}
pub(crate) async fn key_statistics(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    statistic(s, u, b, Domain::Key).await
}

pub(crate) async fn telex_add(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    add(s, u, b, Domain::Telex).await
}
pub(crate) async fn telex_list(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    list(s, u, b, Domain::Telex).await
}
pub(crate) async fn telex_detail(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    detail(s, u, b, Domain::Telex).await
}
pub(crate) async fn telex_update(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    let id = b
        .get("trainId")
        .and_then(Value::as_str)
        .unwrap_or_default()
        .to_owned();
    let status = b
        .get("status")
        .and_then(Value::as_i64)
        .and_then(|status| i32::try_from(status).ok())
        .unwrap_or(-1);
    set_status(s, u, id, status, Domain::Telex).await
}
pub(crate) async fn telex_finish(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    finish_participant(s, u, b, Domain::Telex).await
}
pub(crate) async fn telex_start(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    participant_action(
        s,
        u,
        json!({"trainId":q.train_id,"attempt":q.attempt}),
        Domain::Telex,
        false,
    )
    .await
}
pub(crate) async fn telex_delete(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    delete(s, u, q.train_id.unwrap_or_default(), Domain::Telex).await
}
pub(crate) async fn telex_statistics(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    statistic(s, u, b, Domain::Telex).await
}

pub(crate) async fn ticker_add(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    add(s, u, b, Domain::Ticker).await
}
pub(crate) async fn ticker_list(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    list(s, u, b, Domain::Ticker).await
}
pub(crate) async fn ticker_detail(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    detail(s, u, b, Domain::Ticker).await
}
pub(crate) async fn ticker_finish(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    finish_participant(s, u, b, Domain::Ticker).await
}
pub(crate) async fn ticker_reset(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    participant_action(s, u, b, Domain::Ticker, true).await
}
pub(crate) async fn ticker_start(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    participant_action(
        s,
        u,
        json!({"trainId":q.train_id,"attempt":q.attempt}),
        Domain::Ticker,
        false,
    )
    .await
}
pub(crate) async fn ticker_delete(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    delete(s, u, q.train_id.unwrap_or_default(), Domain::Ticker).await
}
pub(crate) async fn ticker_statistics(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    statistic(s, u, b, Domain::Ticker).await
}
/// Characters of one stored key value (a JSON list of single characters): blanks and `?` do not
/// count, anything else that is not exactly one code point is rejected (Java `countCharacters`).
fn key_value_characters(value: &Value) -> Outcome<i64> {
    let text = value
        .as_str()
        .ok_or_else(|| invalid("拍发字符列表不能为空"))?;
    let characters: Vec<Option<String>> = serde_json::from_str::<Option<Vec<Option<String>>>>(text)
        .map_err(|_| invalid("拍发字符列表不能为空"))?
        .ok_or_else(|| invalid("拍发字符列表不能为空"))?;
    let mut count = 0;
    for character in characters {
        let character = character
            .filter(|character| character.chars().count() == 1)
            .ok_or_else(|| invalid("每个拍发事件必须是一个正文字符"))?;
        if !character.trim().is_empty() && character != "?" {
            count += 1;
        }
    }
    Ok(count)
}

/// Java `generatePageAnalyze`: one `{pageNumber, patNumber, totalTime}` per captured page (raw
/// protocol), or per stored value page for pre-protocol trainings.
async fn page_analyze(
    connection: &mut MySqlConnection,
    train: &Value,
    member: &Value,
) -> Outcome<Value> {
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    let user_id = member["userId"]
        .as_str()
        .ok_or_else(|| invalid("参训用户ID缺失"))?;
    let values = rows(
        connection,
        value_table(Domain::Key),
        value_fields(Domain::Key),
        "WHERE train_id=? AND user_id=? ORDER BY page_number,sort",
        &[&id, user_id],
    )
    .await?;
    let mut analysis = Vec::new();
    if train["protocolVersion"] == 1 {
        let captures = stored_json(
            member["capturePages"]
                .as_str()
                .ok_or_else(|| invalid("缺少原始采集页索引"))?,
        )?;
        let captures = captures
            .as_object()
            .ok_or_else(|| invalid("缺少原始采集页索引"))?;
        let mut pages = captures
            .keys()
            .map(|page| {
                page.parse::<i64>()
                    .map(|number| (number, page))
                    .map_err(|_| invalid("采集页索引无效"))
            })
            .collect::<Outcome<Vec<_>>>()?;
        pages.sort_unstable();
        for (number, key) in pages {
            let mut characters = 0;
            for value in values
                .iter()
                .filter(|value| value["pageNumber"].as_i64() == Some(number))
            {
                characters += key_value_characters(&value["value"])?;
            }
            let intervals = capture_intervals(&captures[key.as_str()]["intervals"])?;
            analysis.push(json!({"pageNumber": number, "patNumber": characters, "totalTime": interval_duration(&intervals, i64::MAX)?}));
        }
        return Ok(json!(analysis));
    }
    let mut by_page: std::collections::BTreeMap<i64, (i64, i64)> =
        std::collections::BTreeMap::new();
    for value in &values {
        let page = value["pageNumber"]
            .as_i64()
            .ok_or_else(|| invalid("拍发页码缺失"))?;
        let entry = by_page.entry(page).or_default();
        if let Some(times) = value["time"]
            .as_str()
            .map(stored_json)
            .transpose()?
            .and_then(|times| times.as_array().cloned())
        {
            for time in times {
                entry.1 += time
                    .as_str()
                    .and_then(|time| time.trim().parse::<i64>().ok())
                    .ok_or_else(|| invalid("拍发时间不合法"))?;
            }
        }
        let pat = value["value"].as_str().unwrap_or("[]");
        entry.0 += stored_json(pat)?
            .as_array()
            .map_or(0, |values| values.len() as i64);
    }
    for (page, (count, time)) in by_page {
        analysis.push(json!({"pageNumber": page, "patNumber": count, "totalTime": time}));
    }
    Ok(json!(analysis))
}

/// Java `generatePageAnalyze` (telex): one `{pageNumber, patNumber, totalTime}` per page that has
/// stored value rows; only the raw row (`sort = -1`) contributes its character count and the
/// duration of its capture intervals.
async fn telex_page_analyze(
    connection: &mut MySqlConnection,
    train: &Value,
    user_id: &str,
) -> Outcome<Value> {
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    let values = rows(
        connection,
        value_table(Domain::Telex),
        value_fields(Domain::Telex),
        "WHERE train_id=? AND user_id=? ORDER BY page_number,sort",
        &[&id, user_id],
    )
    .await?;
    let mut by_page: std::collections::BTreeMap<i64, (i64, i64)> =
        std::collections::BTreeMap::new();
    for value in &values {
        let page = value["pageNumber"]
            .as_i64()
            .ok_or_else(|| invalid("拍发页码缺失"))?;
        let entry = by_page.entry(page).or_default();
        if value["sort"] == -1 {
            entry.0 += super::telex_capture::character_count(
                value["value"].as_str().unwrap_or_default(),
                train["trainType"].as_i64(),
            ) as i64;
            if let Some(intervals) = value["captureIntervals"]
                .as_str()
                .filter(|text| !text.trim().is_empty())
            {
                entry.1 +=
                    interval_duration(&capture_intervals(&stored_json(intervals)?)?, i64::MAX)?;
            }
        }
    }
    Ok(json!(by_page
        .into_iter()
        .map(|(page, (count, time))| json!({"pageNumber": page, "patNumber": count, "totalTime": time}))
        .collect::<Vec<_>>()))
}

/// Java `GeneralTelexPatService.patDetail`: the reader is checked like `requireReadableTarget`
/// (no membership needed to read oneself), then the *requested* user's member row is looked up
/// (a missing `userId` finds nothing, 202). The reply is the typed user VO.
async fn telex_pat_detail(
    connection: &mut MySqlConnection,
    train: &Value,
    user: &AuthUser,
    body: &Value,
) -> Outcome<Value> {
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    let requested = body["userId"].as_str().filter(|value| !value.is_empty());
    let target = requested
        .filter(|value| !value.trim().is_empty())
        .unwrap_or(&user.id);
    if target != user.id && !manages(connection, train, user, Domain::Telex).await? {
        return Err(denied());
    }
    let member = match requested {
        Some(requested) => load_member(connection, &id, requested, Domain::Telex).await?,
        None => None,
    }
    .ok_or_else(|| invalid("未查询到该用户的参训记录"))?;
    let user_id = text(&member["userId"]).unwrap_or_default();
    let pages: Vec<i64> = sqlx::query_scalar(&format!(
        "SELECT DISTINCT page_number FROM {} WHERE train_id=? ORDER BY page_number",
        page_table(Domain::Telex)
    ))
    .bind(&id)
    .fetch_all(&mut *connection)
    .await?;
    let content = if member["isFinish"] == 1 {
        rows(
            connection,
            value_table(Domain::Telex),
            value_fields(Domain::Telex),
            "WHERE train_id=? AND user_id=? AND page_number<=2 AND page_number>=1 ORDER BY page_number,sort,id",
            &[&id, &user_id],
        )
        .await?
    } else {
        rows(
            connection,
            page_table(Domain::Telex),
            page_fields(Domain::Telex),
            "WHERE train_id=? AND page_number<=2 AND page_number>=1 ORDER BY page_number,sort,id",
            &[&id],
        )
        .await?
    };
    let content = content
        .iter()
        .map(|row| {
            json!({"id": row["id"], "trainId": row["trainId"], "pageNumber": row["pageNumber"],
                "key": row["key"], "value": row["value"], "time": Value::Null, "sort": row["sort"]})
        })
        .collect::<Vec<_>>();
    let mut result = detail_member(&member, Domain::Telex);
    result["duration"] = json!(member["validTime"].as_i64().unwrap_or(0));
    result["pageAnalyzeVOS"] = telex_page_analyze(connection, train, &user_id).await?;
    result["existPage"] = json!(pages);
    result["content"] = json!(content);
    result["ruleContent"] = train["ruleContent"].clone();
    result["totalNumber"] = train["totalNumber"].clone();
    result["isCable"] = train["isCable"].clone();
    if train["isCable"] == 1 {
        result["totalNumber"] = json!(
            sqlx::query_scalar::<_, i64>(&format!(
                "SELECT COUNT(*) FROM {} WHERE train_id=?",
                page_table(Domain::Telex)
            ))
            .bind(&id)
            .fetch_one(&mut *connection)
            .await?
        );
        result["pageCount"] = json!(pages.last());
    }
    Ok(result)
}

async fn members(state: AppState, user: AuthUser, body: Value, domain: Domain) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let id = train_id(&body)?;
        let mut connection = pool.acquire().await?;
        let train = load_train(&mut connection, &id, domain, false).await?;
        if matches!(domain, Domain::Telex) {
            return telex_pat_detail(&mut connection, &train, &user, &body).await;
        }
        let key = matches!(domain, Domain::Key);
        // Java `requireReadableTarget` (key) never asks for membership of the caller itself: an
        // outsider reading their own record gets 202 "no such participant", not 207.
        let target = if key {
            let target = body["userId"].as_str().filter(|id| !id.trim().is_empty()).unwrap_or(&user.id);
            if target != user.id && !manages(&mut connection, &train, &user, domain).await? { return Err(denied()); }
            target.to_owned()
        } else {
            readable_target(&mut connection, &train, &user, &body, domain).await?
        };
        let mut member = load_member(&mut connection, &id, &target, domain).await?.ok_or_else(|| invalid(if key { "未查询到该用户的参训记录" } else { "未查询到参训记录" }))?;
        member["protocolVersion"] = train["protocolVersion"].clone();
        member["ruleContent"] = train["ruleContent"].clone();
        member["totalNumber"] = train["totalNumber"].clone();
        member["isCable"] = train["isCable"].clone();
        member["duration"] = if train["protocolVersion"] == 1 { json!(member["activeMillis"].as_i64().unwrap_or(0)/1000) } else { train["validTime"].clone() };
        if !key {
            if let Some(profile) = rows(&mut connection,"t_user","id userName userImg","WHERE id=?",&[&target]).await?.pop() { member["userName"] = profile["userName"].clone(); member["userImg"] = profile["userImg"].clone(); }
        }
        let page_column = if matches!(domain, Domain::Ticker) { "floor_number" } else { "page_number" };
        let sql = format!("SELECT DISTINCT {page_column} FROM {} WHERE train_id=? ORDER BY {page_column}", page_table(domain));
        let pages: Vec<i32> = sqlx::query_scalar(&sql).bind(&id).fetch_all(&mut *connection).await?;
        member["existPage"] = json!(pages);
        member["pageCount"] = json!(pages.last());
        let filter = format!("WHERE train_id=? AND {page_column}<=2 ORDER BY {page_column},sort");
        member["content"] = if member["isFinish"] == 1 {
            json!(rows(&mut connection, value_table(domain), value_fields(domain), &format!("WHERE train_id=? AND user_id=? AND {page_column}<=2 ORDER BY {page_column},id"), &[&id,&target]).await?)
        } else { json!(rows(&mut connection, page_table(domain), page_fields(domain), &filter, &[&id]).await?) };
        if key && train["protocolVersion"] == 1 {
            let captures = stored_json(member["capturePages"].as_str().unwrap_or("{}"))?;
            let object = captures.as_object().ok_or_else(|| invalid("缺少原始采集页索引"))?;
            let mut submitted = object.keys().map(|key| key.parse::<i32>().map_err(|_| invalid("采集页索引无效"))).collect::<Outcome<Vec<_>>>()?;
            submitted.sort_unstable();
            member["existPageNumber"] = json!(submitted.len());
            member["existNumber"] = json!(submitted);
        }
        if key {
            // Java `PojoUtils.convertOne(member, GeneralKeyPatUserInfoVO)`: a typed user VO
            // (no raw columns, no profile), the fixed-report totals only for cable trainings.
            member["pageAnalyzeVOS"] = page_analyze(&mut connection, &train, &member).await?;
            let content = member["content"].as_array().map(|rows| rows.iter().map(|row| page_dto(row, domain, false)).collect::<Vec<_>>()).unwrap_or_default();
            let mut result = detail_member(&member, domain);
            result["protocolVersion"] = member["protocolVersion"].clone();
            result["isCable"] = member["isCable"].clone();
            result["duration"] = member["duration"].clone();
            result["ruleContent"] = member["ruleContent"].clone();
            result["totalNumber"] = member["totalNumber"].clone();
            result["existPage"] = member["existPage"].clone();
            result["content"] = json!(content);
            result["pageCount"] = Value::Null;
            if train["isCable"] == 1 {
                result["totalNumber"] = json!(sqlx::query_scalar::<_, i64>(&format!("SELECT COUNT(*) FROM {} WHERE train_id=?", page_table(domain))).bind(&id).fetch_one(&mut *connection).await?);
                result["pageCount"] = member["pageCount"].clone();
            }
            return Ok(result);
        }
        Ok(member)
    }.await)
}
async fn page(state: AppState, user: AuthUser, body: Value, domain: Domain) -> Response {
    page_response(state, user, body, domain, matches!(domain, Domain::Ticker)).await
}
async fn upload(state: AppState, actor: AuthUser, body: Value, domain: Domain) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let id = train_id(&body)?;
        let page = body.get("pageNumber").or_else(|| body.get("floorNumber")).and_then(Value::as_i64).filter(|page| *page > 0).ok_or_else(|| invalid("页码不正确"))?;
        let mut transaction = pool.begin().await?;
        let train = load_train(&mut transaction, &id, domain, true).await?;
        // Java `student()`: a missing or non-student member is 202 for the hand-key and key
        // domains; the telex domain keeps its 207.
        let not_student = || if matches!(domain, Domain::Telex) { denied() } else { invalid(STUDENT_MISSING) };
        let member = load_member(&mut transaction, &id, &actor.id, domain).await?.ok_or_else(not_student)?;
        if member["role"] != 0 { return Err(not_student()); }
        if matches!(domain, Domain::Key) {
            // Lock order is training -> member -> values everywhere (reset and finish lock the
            // member before touching values); the member row is written at the end of upload.
            sqlx::query("SELECT id FROM general_key_pat_user WHERE id=? FOR UPDATE")
                .bind(text(&member["id"]).ok_or_else(|| invalid("参训记录主键缺失"))?)
                .fetch_one(&mut *transaction).await?;
        }
        if matches!(domain, Domain::Telex) && train["protocolVersion"] != 1 { return Err(terminal("旧训练缺少原始采集协议，请重新创建训练；历史成绩保持不变")); }
        if train["protocolVersion"] != 1 || (matches!(domain, Domain::Telex) && body["protocolVersion"] != 1) { return Err(invalid("客户端采集协议版本不匹配，请刷新后重新进入训练")); }
        if body["attempt"].as_i64().is_none() || body["attempt"] != member["attempt"] { return Err(terminal("训练轮次已变化，请重新读取训练")); }
        let total = train[if matches!(domain, Domain::Ticker) { "messageNumber" } else { "totalNumber" }].as_i64().ok_or_else(|| invalid("训练总组数缺失"))?;
        if page > (total + 99) / 100 { return Err(invalid("页码不正确")); }
        let intervals = capture_intervals(&body["captureIntervals"])?;
        let page_text = page.to_string();
        let page_column = if matches!(domain, Domain::Ticker) { "floor_number" } else { "page_number" };
        let raw_filter = if matches!(domain, Domain::Telex) { " AND sort=-1" } else { "" };
        let filter = format!("WHERE train_id=? AND user_id=? AND {page_column}=?{raw_filter} ORDER BY {}", if matches!(domain,Domain::Ticker) { "id" } else { "sort,id" });
        let saved = rows(&mut transaction, value_table(domain), value_fields(domain), &filter, &[&id,&actor.id,&page_text]).await?;
        let mut captures = if matches!(domain, Domain::Key) { stored_json(member["capturePages"].as_str().ok_or_else(|| invalid("缺少原始采集页索引"))?)? } else { json!({}) };
        let previous = if matches!(domain, Domain::Key) { captures.get(&page_text).map(|capture| capture["intervals"].clone()) }
            else { saved.first().map(|row| row["captureIntervals"].as_str().ok_or_else(|| invalid("已保存页缺少采集时间轴")).and_then(stored_json)).transpose()? };
        if let Some(previous) = previous {
            let old = capture_intervals(&previous)?;
            if old == intervals {
                let same = match domain {
                    Domain::Key => body["pageValue"].as_array().is_some_and(|values| values.len() == saved.len() && values.iter().zip(&saved).all(|(value,saved)| ["sort","key","value","time"].iter().all(|field| value[*field] == saved[*field]))),
                    Domain::Telex => saved.first().is_some_and(|row| row["value"] == body["patValue"]),
                    Domain::Ticker => if let Some(row) = saved.first() { stored_json(row["messageBody"].as_str().ok_or_else(|| invalid("已保存页缺少拍发内容"))?)? == body["messageBody"] && stored_json(row["standard"].as_str().ok_or_else(|| invalid("已保存页缺少校准记录"))?)? == body["standard"] && row["finishInfo"] == body["finishInfo"] } else { false },
                };
                if !same {
                    return Err(invalid(if matches!(domain, Domain::Telex) {
                        "该页采集区间已确认，但正文不一致"
                    } else {
                        "该页采集区间已确认，但拍发内容不一致"
                    }));
                }
                transaction.commit().await?;
                return Ok(if matches!(domain, Domain::Key) { json!("") } else { Value::Null });
            }
            if intervals.len() <= old.len() || !intervals.starts_with(&old) { return Err(invalid("不能覆盖已确认的原始采集区间")); }
        }
        if member["isFinish"] == 1 { return Err(terminal("已结算的训练不能上传")); }
        let bound = capture_bound(&mut transaction,&id,&actor.id,domain).await?;
        let duration = interval_duration(&intervals,bound)?;
        let (characters,raw_millis) = measure_submission(&body,domain,train["trainType"].as_i64())?;
        if (characters > 0 && duration == 0) || raw_millis > duration as f64 + intervals.len() as f64 { return Err(invalid("原始拍发时长与采集区间不一致")); }
        let mut all = intervals.clone();
        if matches!(domain,Domain::Key) {
            for (number,capture) in captures.as_object().ok_or_else(|| invalid("缺少原始采集页索引"))? {
                if number != &page_text { all.extend(capture_intervals(&capture["intervals"])?); }
            }
        } else {
            let filter = format!("WHERE train_id=? AND user_id=? AND {page_column}<>?{raw_filter}");
            for row in rows(&mut transaction,value_table(domain),value_fields(domain),&filter,&[&id,&actor.id,&page_text]).await? {
                let intervals = stored_json(row["captureIntervals"].as_str().ok_or_else(|| invalid("已保存页缺少采集时间轴"))?)?;
                all.extend(capture_intervals(&intervals)?);
            }
        }
        all.sort_unstable();
        interval_duration(&all,i64::MAX)?;
        // `saved` is exactly this page's rows (same filter, read after the training lock): delete
        // them by primary key instead of scanning the unindexed child table.
        for saved_row in &saved {
            let row_id = text(&saved_row["id"]).ok_or_else(|| invalid("已保存页缺少主键"))?;
            sqlx::query(&format!("DELETE FROM {} WHERE id=?",value_table(domain))).bind(row_id).execute(&mut *transaction).await?;
        }
        match domain {
            Domain::Key => {
                for source in body["pageValue"].as_array().ok_or_else(|| invalid("拍发记录不能为空"))? {
                    let mut row = source.clone();
                    row["trainId"] = train["id"].clone(); row["userId"] = json!(actor.id); row["pageNumber"] = json!(page);
                    insert_fields(&mut transaction,value_table(domain),"trainId userId pageNumber sort key value time",&row).await?;
                }
                let received: i64 = sqlx::query_scalar("SELECT CAST(UNIX_TIMESTAMP(NOW(3))*1000 AS SIGNED)").fetch_one(&mut *transaction).await?;
                captures[&page_text] = json!({"attempt":member["attempt"],"intervals":body["captureIntervals"],"receivedAt":received});
                sqlx::query("UPDATE general_key_pat_user SET capture_pages=? WHERE id=?").bind(captures.to_string()).bind(text(&member["id"]).ok_or_else(|| invalid("参训记录主键缺失"))?).execute(&mut *transaction).await?;
            }
            Domain::Telex => {
                sqlx::query("INSERT INTO general_telex_pat_user_value (id,train_id,user_id,page_number,sort,value,attempt,capture_intervals,received_at) VALUES (?,?,?,?,-1,?,?,?,NOW(6))")
                    .bind(uuid::Uuid::new_v4().to_string()).bind(&id).bind(&actor.id).bind(page).bind(body["patValue"].as_str()).bind(member["attempt"].as_i64()).bind(body["captureIntervals"].to_string()).execute(&mut *transaction).await?;
                derive_telex_capture(&mut transaction,&text(&member["id"]).ok_or_else(|| invalid("参训记录主键缺失"))?,&id,&actor.id,train["trainType"].as_i64()).await?;
            }
            Domain::Ticker => {
                sqlx::query("INSERT INTO general_ticker_pat_train_user_value (train_id,user_id,floor_number,message_body,standard,finish_info,attempt,capture_intervals,received_at) VALUES (?,?,?,?,?,?,?,?,NOW(6))")
                    .bind(&id).bind(&actor.id).bind(page).bind(body["messageBody"].to_string()).bind(body["standard"].to_string()).bind(body["finishInfo"].as_str()).bind(member["attempt"].as_i64()).bind(body["captureIntervals"].to_string()).execute(&mut *transaction).await?;
            }
        }
        transaction.commit().await?;
        Ok(if matches!(domain, Domain::Key) { json!("") } else { Value::Null })
    }.await)
}

pub(crate) async fn key_page(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    page(s, u, b, Domain::Key).await
}
pub(crate) async fn key_members(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    members(s, u, b, Domain::Key).await
}
pub(crate) async fn key_upload(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    upload(s, u, b, Domain::Key).await
}
pub(crate) async fn key_online(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    online(s, u, q.train_id.unwrap_or_default(), Domain::Key).await
}
pub(crate) async fn telex_page(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    page(s, u, b, Domain::Telex).await
}
pub(crate) async fn telex_members(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    members(s, u, b, Domain::Telex).await
}
pub(crate) async fn telex_upload(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    upload(s, u, b, Domain::Telex).await
}
pub(crate) async fn telex_online(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    online(s, u, q.train_id.unwrap_or_default(), Domain::Telex).await
}
pub(crate) async fn ticker_page(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    page(s, u, b, Domain::Ticker).await
}
pub(crate) async fn ticker_upload(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    upload(s, u, b, Domain::Ticker).await
}

async fn export_train(
    connection: &mut MySqlConnection,
    actor: &AuthUser,
    id: &str,
) -> Outcome<Value> {
    // Read-only: plain reads inside the transaction's snapshot, no row lock.
    let train = load_train(connection, id, Domain::Key, false).await?;
    if !manages(connection, &train, actor, Domain::Key).await? {
        return Err(denied());
    }
    let train = rows(
        connection,
        table(Domain::Key),
        KEY_SYNC,
        "WHERE id=?",
        &[id],
    )
    .await?
    .remove(0);
    let pages = rows(
        connection,
        page_table(Domain::Key),
        KEY_PAGE,
        "WHERE train_id=? ORDER BY page_number,sort",
        &[id],
    )
    .await?;
    let members = rows(
        connection,
        user_table(Domain::Key),
        KEY_USER,
        "WHERE train_id=? ORDER BY id",
        &[id],
    )
    .await?;
    let values = rows(
        connection,
        value_table(Domain::Key),
        KEY_VALUE,
        "WHERE train_id=? ORDER BY page_number,sort,id",
        &[id],
    )
    .await?;
    let more = rows(
        connection,
        "general_key_pat_train_more",
        KEY_MORE,
        "WHERE train_id=? ORDER BY page_number,id",
        &[id],
    )
    .await?;
    let users = rows(connection, "t_user", USER_SYNC,
        "WHERE id IN (SELECT create_user FROM general_key_pat WHERE id=? UNION SELECT user_id FROM general_key_pat_user WHERE train_id=? UNION SELECT user_id FROM general_key_pat_user_value WHERE train_id=? UNION SELECT user_id FROM general_key_pat_train_more WHERE train_id=?) ORDER BY id",
        &[id, id, id, id]).await?;
    Ok(
        json!({"trainDto":train,"pageDto":pages,"userDto":members,"userValueDto":values,"moreDto":more,"users":users}),
    )
}
async fn export(pool: &MySqlPool, actor: &AuthUser, id: Option<&str>) -> Outcome<Value> {
    let mut transaction = pool.begin().await?;
    let result = if let Some(id) = id {
        export_train(&mut transaction, actor, id).await
    } else {
        let ids = sqlx::query("SELECT DISTINCT t.id FROM general_key_pat t JOIN general_key_pat_user u ON u.train_id=t.id WHERE u.user_id=? AND (? OR t.create_user=? OR u.role=1) ORDER BY t.id")
            .bind(&actor.id).bind(actor.is_admin).bind(&actor.id).fetch_all(&mut *transaction).await?;
        let mut trains = Vec::with_capacity(ids.len());
        for row in ids {
            trains.push(
                export_train(
                    &mut transaction,
                    actor,
                    &row.get::<i32, _>("id").to_string(),
                )
                .await?,
            );
        }
        Ok(json!(trains))
    };
    match result {
        Ok(value) => {
            transaction.commit().await?;
            Ok(value)
        }
        Err(error) => {
            transaction.rollback().await?;
            Err(error)
        }
    }
}
fn array<'a>(body: &'a Value, field: &str) -> Outcome<&'a [Value]> {
    body[field]
        .as_array()
        .map(Vec::as_slice)
        .ok_or_else(|| invalid("导入数据缺少必需的列表"))
}
fn mapped_user(map: &HashMap<String, String>, source: &Value) -> Outcome<String> {
    source
        .as_str()
        .and_then(|id| map.get(id))
        .cloned()
        .ok_or_else(|| invalid("导入用户映射缺失"))
}
async fn import_train(connection: &mut MySqlConnection, body: &Value) -> Outcome<()> {
    let mut train = body["trainDto"]
        .as_object()
        .cloned()
        .map(Value::Object)
        .ok_or_else(|| invalid("训练信息不能为空"))?;
    let users = array(body, "users")?;
    let mut user_ids = HashMap::with_capacity(users.len());
    for source in users {
        let old_id = source["id"]
            .as_str()
            .filter(|id| !id.is_empty())
            .ok_or_else(|| invalid("导入用户ID不能为空"))?;
        let account = source["userAccount"]
            .as_str()
            .filter(|account| !account.trim().is_empty())
            .ok_or_else(|| invalid("导入账号不能为空"))?;
        if user_ids.contains_key(old_id) {
            return Err(invalid("导入用户ID重复"));
        }
        // t_user is indexed by primary key only: a locking read by account would lock the whole
        // table (and every login token write). Read the account plainly, then take a shared
        // lock on that one row by primary key so the user cannot be deleted under the import.
        let existing =
            sqlx::query("SELECT id FROM t_user WHERE user_account=? ORDER BY id LIMIT 1")
                .bind(account)
                .fetch_optional(&mut *connection)
                .await?;
        if let Some(row) = &existing {
            sqlx::query("SELECT id FROM t_user WHERE id=? FOR SHARE")
                .bind(row.try_get::<String, _>("id")?)
                .fetch_optional(&mut *connection)
                .await?;
        }
        let id =
            if let Some(row) = existing {
                row.try_get::<String, _>("id")?
            } else {
                let id = uuid::Uuid::new_v4().to_string();
                // Offline archives cannot install sessions, passwords, roles or account state.
                sqlx::query(
                "INSERT INTO t_user (id,user_account,user_name,user_img,status) VALUES (?,?,?,?,0)",
            )
            .bind(&id)
            .bind(account)
            .bind(source["userName"].as_str())
            .bind(source["userImg"].as_str().unwrap_or("/userImages/pag-avatar-man.png"))
            .execute(&mut *connection)
            .await?;
                id
            };
        user_ids.insert(old_id.to_owned(), id);
    }
    train["createUser"] = json!(mapped_user(&user_ids, &train["createUser"])?);
    if train["protocolVersion"].is_null() {
        train["protocolVersion"] = json!(0);
    }
    // The sync DTO carries neither isCable nor trainType: Java's entity default is isCable=0.
    train["isCable"] = json!(0);
    let fields = format!("{KEY_SYNC} isCable");
    let fields = fields
        .split_whitespace()
        .filter(|field| *field != "id")
        .collect::<Vec<_>>()
        .join(" ");
    let id = insert_fields(connection, "general_key_pat", &fields, &train).await?;
    for (property, table, fields) in [
        ("pageDto", "general_key_pat_page", KEY_PAGE),
        ("userDto", "general_key_pat_user", KEY_USER),
        ("userValueDto", "general_key_pat_user_value", KEY_VALUE),
        ("moreDto", "general_key_pat_train_more", KEY_MORE),
    ] {
        let fields = fields
            .split_whitespace()
            .filter(|field| *field != "id" || property == "pageDto")
            .collect::<Vec<_>>()
            .join(" ");
        for source in array(body, property)? {
            let mut row = source
                .as_object()
                .cloned()
                .map(Value::Object)
                .ok_or_else(|| invalid("导入行必须是对象"))?;
            row["trainId"] = json!(id);
            if property == "pageDto" {
                row["id"] = json!(uuid::Uuid::new_v4().to_string());
            } else {
                row["userId"] = json!(mapped_user(&user_ids, &row["userId"])?);
            }
            if property == "userDto" {
                if row["attempt"].is_null() {
                    row["attempt"] = json!(0);
                }
                if row["capturePages"].is_null() {
                    row["capturePages"] = json!("{}");
                }
            }
            insert_fields(connection, table, &fields, &row).await?;
        }
    }
    Ok(())
}
async fn import(pool: &MySqlPool, actor: &AuthUser, body: Value, batch: bool) -> Outcome<Value> {
    if !actor.is_admin {
        return Err(denied());
    }
    let archives = if batch {
        body.as_array()
            .ok_or_else(|| invalid("导入数据必须是列表"))?
            .as_slice()
    } else {
        std::slice::from_ref(&body)
    };
    let mut transaction = pool.begin().await?;
    for archive in archives {
        if let Err(error) = import_train(&mut transaction, archive).await {
            transaction.rollback().await?;
            return Err(error);
        }
    }
    transaction.commit().await?;
    Ok(Value::Null)
}
pub(crate) async fn key_export(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Query(query): Query<QueryId>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(id) = query.train_id.filter(|id| !id.is_empty()) else {
        return response(Err(invalid("训练ID不能为空")));
    };
    response(export(pool, &actor, Some(&id)).await)
}
pub(crate) async fn key_export_batch(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(export(pool, &actor, None).await)
}
pub(crate) async fn key_import(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    if !actor.is_admin {
        return response(Err(denied()));
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(import(pool, &actor, body, false).await)
}
pub(crate) async fn key_import_batch(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    if !actor.is_admin {
        return response(Err(denied()));
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(import(pool, &actor, body, true).await)
}

fn stored_json(text: &str) -> Outcome<Value> {
    serde_json::from_str(text).map_err(|error| {
        Failure::Database(sqlx::Error::Protocol(format!(
            "invalid stored training JSON: {error}"
        )))
    })
}
async fn elapsed(
    connection: &mut MySqlConnection,
    id: &str,
    target: &str,
    domain: Domain,
) -> Outcome<i64> {
    let sql = format!("SELECT CAST(COALESCE(GREATEST(0,TIMESTAMPDIFF(MICROSECOND,capture_started_at,NOW(6)) DIV 1000),0) AS SIGNED) FROM {} WHERE train_id=? AND user_id=?", user_table(domain));
    Ok(sqlx::query_scalar(&sql)
        .bind(id)
        .bind(target)
        .fetch_optional(connection)
        .await?
        .unwrap_or(0))
}
fn page_dto(row: &Value, domain: Domain, source: bool) -> Value {
    match domain {
        Domain::Key => {
            let mut output = row.clone();
            for field in ["id", "trainId"] {
                if let Some(value) = output.get(field).and_then(text) {
                    output[field] = json!(value);
                }
            }
            output.as_object_mut().map(|object| {
                object.remove("userId");
            });
            output
        }
        Domain::Telex => json!({
            "id": row["id"], "key": row["key"], "pageNumber": row["pageNumber"],
            "sort": row["sort"], "trainId": row["trainId"],
            "value": if source { Value::Null } else { row["value"].clone() }
        }),
        Domain::Ticker => json!({
            "id": row["id"], "moresKey": row["moresKey"], "moresTime": row["moresTime"],
            "moresValue": row["moresValue"], "patKeys": row["patKeys"], "patLogs": "[]"
        }),
    }
}

async fn page_response(
    state: AppState,
    actor: AuthUser,
    body: Value,
    domain: Domain,
    personal: bool,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let id = train_id(&body)?;
        let number = body.get("pageNumber").or_else(|| body.get("floorNumber")).and_then(Value::as_i64).filter(|number| *number > 0).ok_or_else(|| invalid("页码不正确"))?;
        let mut transaction = pool.begin().await?;
        // Only getPage may generate a missing page; findPage is a plain read and must not lock.
        let train = load_train(&mut transaction, &id, domain, personal).await?;
        // Java `readableMember` (key and hand-key): 207 for reading others without being the
        // creator/organizer, 202 for a target that is not a member. Telex keeps its own check.
        let java_member = personal && !matches!(domain, Domain::Telex);
        if !java_member { require_member(&mut transaction, &train, &actor, domain).await?; }
        let page_column = if matches!(domain, Domain::Ticker) { "floor_number" } else { "page_number" };
        let filter = format!("WHERE train_id=? AND {page_column}=? ORDER BY sort");
        let number_text = number.to_string();
        let mut messages = rows(&mut transaction, page_table(domain), page_fields(domain), &filter, &[&id, &number_text]).await?;
        if !personal {
            transaction.commit().await?;
            if matches!(domain, Domain::Key | Domain::Telex) {
                // Java `GeneralKeyPatPageDetailDto` / `GeneralTelexPatPageDetailDto`: no trainId.
                for message in &mut messages { message.as_object_mut().map(|row| row.remove("trainId")); }
            }
            return Ok(json!({"messageContent":messages}));
        }
        let (target, member) = if java_member {
            readable_member(&mut transaction, &train, &actor, body.get("userId").and_then(Value::as_str), domain).await?
        } else {
            let target = readable_target(&mut transaction, &train, &actor, &body, domain).await?;
            let member = load_member(&mut transaction, &id, &target, domain).await?.ok_or_else(|| invalid("未查询到参训记录"))?;
            (target, member)
        };
        let total = train[if matches!(domain, Domain::Ticker) { "messageNumber" } else { "totalNumber" }].as_i64().ok_or_else(|| invalid("训练总组数缺失"))?;
        if number > (total + 99) / 100 { return Err(invalid("页码不正确")); }
        if messages.is_empty() {
            if train["isCable"] == 1 { return Err(invalid("固定报底页不存在")); }
            let groups = generate_groups(&train, domain, (total - (number - 1) * 100).min(100) as usize)?;
            for (sort, group) in groups.into_iter().enumerate() {
                let key = if matches!(domain, Domain::Telex) { group } else { json!(group.chars().map(|ch| ch.to_string()).collect::<Vec<_>>()).to_string() };
                let row = if matches!(domain, Domain::Ticker) {
                    json!({"id":uuid::Uuid::new_v4().to_string(),"trainId":train["id"],"floorNumber":number,"sort":sort,"moresKey":key,"moresValue":"[]","moresTime":"[]","patKeys":"[]"})
                } else { json!({"id":uuid::Uuid::new_v4().to_string(),"trainId":train["id"],"pageNumber":number,"sort":sort,"key":key,"value":"[]","time":"[]"}) };
                insert_fields(&mut transaction, page_table(domain), page_fields(domain), &row).await?;
                messages.push(row);
            }
        }
        let filter = format!("WHERE train_id=? AND user_id=? AND {page_column}=? ORDER BY {}", if matches!(domain, Domain::Ticker) { "id" } else { "sort" });
        let values = rows(&mut transaction, value_table(domain), value_fields(domain), &filter, &[&id, &target, &number_text]).await?;
        let mut result = json!({"protocolVersion":train["protocolVersion"],"attempt":member["attempt"],"serverElapsedMs":elapsed(&mut transaction,&id,&target,domain).await?,"savedCaptureIntervals":[],"submitted":false});
        match domain {
            Domain::Key => {
                result["resolverMessage"] = Value::Null;
                let captures = stored_json(member["capturePages"].as_str().unwrap_or("{}"))?;
                let captured = captures.get(&number_text);
                let submitted = captured.is_some() || (train["protocolVersion"] != 1 && !values.is_empty());
                result["submitted"] = json!(submitted);
                result["savedCaptureIntervals"] = captured.map(|page| page["intervals"].clone()).unwrap_or(json!([]));
                result["messageVO"] = json!(if submitted { values.iter().map(|row| page_dto(row, domain, false)).collect::<Vec<_>>() } else { messages.iter().map(|row| page_dto(row, domain, true)).collect::<Vec<_>>() });
                let more = rows(&mut transaction,"general_key_pat_train_more",KEY_MORE,"WHERE train_id=? AND user_id=? AND page_number=?",&[&id,&target,&number_text]).await?;
                for key in ["moreGroup", "moreLine"] { result[key] = match more.first().and_then(|row| row[key].as_str()) { Some(text) => stored_json(text)?, None => json!([]) }; }
            }
            Domain::Telex => unreachable!("telex getPage has its own handler"),
            Domain::Ticker => {
                let saved = values.first();
                result["submitted"] = json!(saved.is_some());
                let display_messages = messages.iter().map(|row| page_dto(row, domain, true)).collect::<Vec<_>>();
                result["messageBody"] = saved.map(|row| row["messageBody"].clone()).unwrap_or_else(|| json!(serde_json::to_string(&display_messages).unwrap_or_default()));
                result["messageKey"] = json!(messages.iter().map(|row| page_dto(row, domain, true)).collect::<Vec<_>>());
                for key in ["finishInfo", "standard", "resolver"] { result[key] = saved.map(|row| row[key].clone()).unwrap_or(json!("[]")); }
                if train["protocolVersion"] == 1 {
                    if let Some(intervals) = saved.and_then(|row| row["captureIntervals"].as_str()) { result["savedCaptureIntervals"] = stored_json(intervals)?; }
                }
            }
        }
        transaction.commit().await?;
        Ok(result)
    }.await)
}
async fn pat_value(state: AppState, actor: AuthUser, body: Value, domain: Domain) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(
        async {
            let id = train_id(&body)?;
            let telex = matches!(domain, Domain::Telex);
            // Java telex getPatValue does not validate the page: nothing stored for it is `[]`.
            let page = match body["pageNumber"].as_i64().filter(|page| *page > 0) {
                Some(page) => page.to_string(),
                None if telex => "0".to_owned(),
                None => return Err(invalid("页码不正确")),
            };
            let mut connection = pool.acquire().await?;
            let train = load_train(&mut connection, &id, domain, false).await?;
            // Java key `requireReadableTarget`: no membership check for reading oneself, so an
            // outsider gets an empty list; the VO has no `id`.
            let target = if matches!(domain, Domain::Key | Domain::Telex) {
                let target = body["userId"]
                    .as_str()
                    .filter(|id| !id.trim().is_empty())
                    .unwrap_or(&actor.id);
                if target != actor.id && !manages(&mut connection, &train, &actor, domain).await? {
                    return Err(denied());
                }
                target.to_owned()
            } else {
                readable_target(&mut connection, &train, &actor, &body, domain).await?
            };
            let mut values = rows(
                &mut connection,
                value_table(domain),
                value_fields(domain),
                "WHERE train_id=? AND user_id=? AND page_number=? ORDER BY sort,id",
                &[&id, &target, &page],
            )
            .await?;
            if matches!(domain, Domain::Key) {
                for value in &mut values {
                    value.as_object_mut().map(|row| row.remove("id"));
                }
            }
            if telex {
                // Java `GeneralTelexPatTrainUserValueVO`.
                for value in &mut values {
                    if let Some(row) = value.as_object_mut() {
                        for field in ["id", "attempt", "captureIntervals", "receivedAt"] {
                            row.remove(field);
                        }
                    }
                }
            }
            Ok(json!(values))
        }
        .await,
    )
}
pub(crate) async fn key_get_page(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    page_response(state, actor, body, Domain::Key, true).await
}
/// Java `GeneralTelexPatService.getPage`: the reader is checked like `requireReadableTarget`
/// (no membership needed to read oneself). A missing source page of a random report is generated
/// on first read; only then is the training row locked (plain reads otherwise).
pub(crate) async fn telex_get_page(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let domain = Domain::Telex;
        let id = train_id(&body)?;
        let mut connection = pool.acquire().await?;
        let train = load_train(&mut connection, &id, domain, false).await?;
        let target = body["userId"]
            .as_str()
            .filter(|value| !value.trim().is_empty())
            .unwrap_or(&actor.id)
            .to_owned();
        if target != actor.id && !manages(&mut connection, &train, &actor, domain).await? {
            return Err(denied());
        }
        let number = body["pageNumber"].as_i64().ok_or_else(|| invalid("页码不能为空"))?;
        let mut generate_count = 100_i64;
        if train["isCable"] == 0 {
            let total = train["totalNumber"].as_i64().ok_or_else(|| invalid("训练总组数缺失，无法取页"))?;
            let total_page = total / 100 + i64::from(total % 100 > 0);
            if number > total_page || number < 1 { return Err(invalid("页码不正确")); }
            if number == total_page { generate_count = total - (number - 1) * 100; }
        } else if number < 1 {
            return Err(invalid("页码不正确"));
        }
        let number_text = number.to_string();
        let page_filter = "WHERE train_id=? AND page_number=? ORDER BY sort";
        let mut messages = rows(&mut connection, page_table(domain), page_fields(domain), page_filter, &[&id, &number_text]).await?;
        if messages.is_empty() {
            if train["isCable"] == 1 { return Err(invalid("固定报底页不存在")); }
            let mut transaction = pool.begin().await?;
            load_train(&mut transaction, &id, domain, true).await?;
            messages = rows(&mut transaction, page_table(domain), page_fields(domain), page_filter, &[&id, &number_text]).await?;
            if messages.is_empty() {
                let groups = generate_groups(&train, domain, generate_count.max(0) as usize)?;
                for (sort, group) in groups.into_iter().enumerate() {
                    let row = json!({"id":uuid::Uuid::new_v4().to_string(),"trainId":train["id"],"pageNumber":number,"sort":sort,"key":group,"value":Value::Null});
                    insert_fields(&mut transaction, page_table(domain), page_fields(domain), &row).await?;
                    messages.push(row);
                }
            }
            transaction.commit().await?;
        }
        let values = rows(&mut connection, value_table(domain), value_fields(domain), "WHERE train_id=? AND user_id=? AND page_number=? ORDER BY sort,id", &[&id, &target, &number_text]).await?;
        let member = load_member(&mut connection, &id, &target, domain).await?;
        let raw = values.iter().find(|row| row["sort"] == -1);
        let mut result = json!({
            "protocolVersion": train["protocolVersion"],
            "attempt": member.as_ref().map_or(Value::Null, |member| member["attempt"].clone()),
            "serverElapsedMs": if member.is_some() { elapsed(&mut connection, &id, &target, domain).await? } else { 0 },
            "submitted": raw.is_some(), "savedCaptureIntervals": [],
        });
        if let Some(intervals) = raw.and_then(|row| row["captureIntervals"].as_str()) { result["savedCaptureIntervals"] = stored_json(intervals)?; }
        result["messageVO"] = json!(if values.is_empty() { messages.iter().map(|row| page_dto(row, domain, true)).collect::<Vec<_>>() } else { values.iter().map(|row| page_dto(row, domain, false)).collect::<Vec<_>>() });
        Ok(result)
    }.await)
}
pub(crate) async fn key_pat_value(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    pat_value(state, actor, body, Domain::Key).await
}
pub(crate) async fn telex_pat_value(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    pat_value(state, actor, body, Domain::Telex).await
}

fn generate_groups(train: &Value, domain: Domain, count: usize) -> Outcome<Vec<String>> {
    use rand::{seq::SliceRandom, Rng};
    let kind = train[if matches!(domain, Domain::Key) {
        "messageType"
    } else {
        "type"
    }]
    .as_i64();
    let alphabet: &[u8] = match kind {
        Some(0) => b"0123456789",
        Some(1) => b"ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        Some(2) => b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        _ => return Err(invalid("未知数据类型")),
    };
    let mut rng = rand::thread_rng();
    if matches!(domain, Domain::Telex) {
        return Ok((0..count)
            .map(|_| {
                (0..4)
                    .map(|_| char::from(alphabet[rng.gen_range(0..alphabet.len())]))
                    .collect()
            })
            .collect());
    }
    if train["isRandom"] != 1 {
        return Ok((0..count)
            .map(|i| {
                (0..4)
                    .map(|j| char::from(alphabet[(i * 4 + j) % alphabet.len()]))
                    .collect()
            })
            .collect());
    }
    let average = train["isAverage"] == 1;
    if average && kind == Some(0) {
        return Ok(numeric_pairs(count, true));
    }
    let mut values: Vec<u8> = (0..count * 4)
        .map(|i| alphabet[i % alphabet.len()])
        .collect();
    if average {
        values.shuffle(&mut rng);
    }
    let mut groups: Vec<String> = Vec::with_capacity(count);
    for index in 0..count {
        let mut group = [0; 4];
        if average {
            for position in 0..4 {
                let offset = index * 4 + position;
                if group[..position].contains(&values[offset]) {
                    if let Some(next) = (offset + 1..values.len())
                        .find(|next| !group[..position].contains(&values[*next]))
                    {
                        values.swap(offset, next);
                    }
                }
                group[position] = values[offset];
            }
        } else {
            loop {
                if kind == Some(2) {
                    let digits = match rng.gen_range(0..100) {
                        0..=49 => 2,
                        50..=74 => 3,
                        _ => 1,
                    };
                    for (position, value) in b"0123456789"
                        .choose_multiple(&mut rng, digits)
                        .chain(b"ABCDEFGHIJKLMNOPQRSTUVWXYZ".choose_multiple(&mut rng, 4 - digits))
                        .enumerate()
                    {
                        group[position] = *value;
                    }
                    group.shuffle(&mut rng);
                } else {
                    for (position, value) in alphabet.choose_multiple(&mut rng, 4).enumerate() {
                        group[position] = *value;
                    }
                }
                if !groups
                    .iter()
                    .rev()
                    .take(10)
                    .any(|previous| previous.as_bytes() == group)
                {
                    break;
                }
            }
        }
        groups.push(group.into_iter().map(char::from).collect());
    }
    Ok(groups)
}

fn frozen_rule(train: &Value, domain: Domain) -> Outcome<Value> {
    let mut rule: Value = serde_json::from_str(
        train["ruleContent"]
            .as_str()
            .ok_or_else(|| invalid("训练缺少冻结评分规则"))?,
    )
    .map_err(|_| invalid("冻结评分规则不合法"))?;
    if !matches!(domain, Domain::Key) {
        if let Value::String(text) = &rule {
            rule = serde_json::from_str(text).map_err(|_| invalid("冻结评分规则不合法"))?;
        }
    }
    let full_score = if matches!(domain, Domain::Key) {
        settlement_number(&rule["score"])
    } else {
        settlement_number(&train["ruleScore"])
    };
    if full_score.is_none_or(|score| !score.is_finite() || score <= 0.0) {
        return Err(invalid("训练缺少有效的冻结规则满分"));
    }
    if matches!(domain, Domain::Key) {
        rule = match &rule["content"] {
            Value::String(text) => {
                serde_json::from_str(text).map_err(|_| invalid("冻结评分规则不合法"))?
            }
            value => value.clone(),
        };
    }
    let unit = if matches!(domain, Domain::Key) {
        "FOUR_CHARACTER_GROUPS_PER_MINUTE"
    } else {
        "CHARACTERS_PER_MINUTE"
    };
    if rule["rateUnit"] != unit
        || settlement_number(&rule["wpm"]["base"])
            .is_none_or(|base| !base.is_finite() || base <= 0.0)
        || !rule["other"].is_object()
    {
        return Err(invalid("冻结评分规则不完整或码率单位不正确"));
    }
    Ok(rule)
}
async fn participant_action(
    state: AppState,
    actor: AuthUser,
    body: Value,
    domain: Domain,
    reset: bool,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let id = train_id(&body)?;
        let mut transaction = pool.begin().await?;
        let train = load_train(&mut transaction, &id, domain, true).await?;
        let member = locked_member(&mut transaction, &id, &actor.id, domain).await?;
        if member["role"] != 0 { return Err(if matches!(domain, Domain::Telex) { denied() } else { invalid(STUDENT_MISSING) }); }
        let attempt = || -> Outcome<()> {
            if body["attempt"].as_i64().is_none() || body["attempt"] != member["attempt"] { return Err(terminal("训练轮次已变化，请重新读取训练")); }
            Ok(())
        };
        let protocol = || -> Outcome<()> {
            if train["protocolVersion"] != 1 {
                return Err(if matches!(domain, Domain::Telex) { terminal("旧训练缺少原始采集协议，请重新创建训练；历史成绩保持不变") } else { invalid("旧训练缺少原始采集协议，请重新创建训练") });
            }
            Ok(())
        };
        let underway = || -> Outcome<()> {
            if train["status"] != 1 { return Err(invalid("训练不在进行中")); }
            Ok(())
        };
        // The Java services check these in a different order per domain, and the status
        // errors are plain 202 (IllegalStateException); only the stale attempt and an
        // already-settled start are 208 terminal states. Telex keeps its own order and codes.
        match (domain, reset) {
            (Domain::Telex, _) => {
                attempt()?;
                protocol()?;
                if reset && train["status"] != 1 { return Err(terminal("训练不在进行中")); }
            }
            (Domain::Ticker, true) => { attempt()?; underway()?; protocol()?; }
            (Domain::Key, true) => { underway()?; protocol()?; attempt()?; }
            (Domain::Ticker, false) => {
                attempt()?;
                capture_bound(&mut transaction, &id, &actor.id, domain).await?;
                protocol()?;
            }
            (Domain::Key, false) => {
                capture_bound(&mut transaction, &id, &actor.id, domain).await?;
                protocol()?;
                attempt()?;
            }
        }
        let member_id = text(&member["id"]).ok_or_else(|| invalid("参训记录主键缺失"))?;
        if reset {
            let next = member["attempt"].as_i64().and_then(|attempt| attempt.checked_add(1)).filter(|attempt| *attempt <= i32::MAX as i64).ok_or_else(|| terminal("训练轮次已用尽"))?;
            let score = if matches!(domain, Domain::Ticker) { "NULL" } else { "0" };
            let extras = match domain {
                Domain::Key => ",capture_pages='{}',duration='0',content=NULL",
                Domain::Telex => ",speed_log=NULL,valid_time_log=NULL",
                Domain::Ticker => ",lack=0,speed_log=NULL",
            };
            let sql = format!("UPDATE {} SET attempt=?,capture_started_at=NOW(6),active_millis=NULL,is_finish=0,accuracy='0.00',speed='0',error_number=0,score={score},finish_time=NULL,deduct_info=NULL,statistic_info=NULL{extras} WHERE id=?", user_table(domain));
            sqlx::query(&sql).bind(next).bind(&member_id).execute(&mut *transaction).await?;
            delete_rows(&mut transaction, value_table(domain), "WHERE train_id=? AND user_id=?", &[&id, &actor.id]).await?;
            if matches!(domain, Domain::Key) {
                for child in ["general_key_pat_train_more", "general_key_pat_user_value_resolver"] {
                    delete_rows(&mut transaction, child, "WHERE train_id=? AND user_id=?", &[&id, &actor.id]).await?;
                }
            }
        } else {
            if member["isFinish"] == 1 { return Err(terminal(if matches!(domain, Domain::Telex) { "已结算的训练不能重新开始" } else { "已结算的训练需要先重置" })); }
            // Telex startTrain marks readiness; its capture window is enforced on upload.
            sqlx::query(&format!("UPDATE {} SET is_finish=2 WHERE id=?",user_table(domain))).bind(&member_id).execute(&mut *transaction).await?;
        }
        transaction.commit().await?;
        Ok(if reset && matches!(domain, Domain::Ticker) { json!("ok") } else { Value::Null })
    }.await)
}
async fn capture_bound(
    connection: &mut MySqlConnection,
    id: &str,
    target: &str,
    domain: Domain,
) -> Outcome<i64> {
    let closing = if matches!(domain, Domain::Telex) {
        2
    } else {
        3
    };
    let sql = format!("SELECT CAST(TIMESTAMPDIFF(MICROSECOND,u.capture_started_at,NOW(6)) DIV 1000 AS SIGNED) elapsed FROM {} t JOIN {} u ON u.train_id=t.id WHERE t.id=? AND u.user_id=? AND (u.capture_started_at IS NULL OR t.status=1 OR (t.status={closing} AND NOW(6)<DATE_ADD(t.end_time,INTERVAL 60 SECOND)))",table(domain),user_table(domain));
    match sqlx::query_scalar::<_, Option<i64>>(&sql)
        .bind(id)
        .bind(target)
        .fetch_optional(connection)
        .await?
    {
        Some(Some(elapsed)) => Ok(elapsed.max(0)),
        Some(None) => Err(invalid("采集尚未开始")),
        // Java IllegalStateException is 202 for the hand-key and key domains.
        None if matches!(domain, Domain::Telex) => Err(terminal("训练已停止接收拍发记录")),
        None => Err(invalid("训练已停止接收拍发记录")),
    }
}

async fn online(state: AppState, actor: AuthUser, id: String, domain: Domain) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(
        async {
            let mut connection = pool.acquire().await?;
            let train = load_train(&mut connection, &id, domain, false).await?;
            require_member(&mut connection, &train, &actor, domain).await?;
            let kind = match domain {
                Domain::Key => "key",
                Domain::Telex => "telex",
                Domain::Ticker => "ticker",
            };
            Ok(json!(super::ws::general_online_users(kind, &id).await))
        }
        .await,
    )
}

fn capture_intervals(value: &Value) -> Outcome<Vec<(i64, i64)>> {
    value
        .as_array()
        .ok_or_else(|| invalid("采集时间轴不能为空"))?
        .iter()
        .map(|interval| {
            let start = interval["startedMs"]
                .as_i64()
                .ok_or_else(|| invalid("采集起点必须为整数"))?;
            let end = interval["endedMs"]
                .as_i64()
                .ok_or_else(|| invalid("采集终点必须为整数"))?;
            Ok((start, end))
        })
        .collect()
}
fn interval_duration(intervals: &[(i64, i64)], maximum: i64) -> Outcome<i64> {
    let mut previous = 0;
    let mut duration = 0i64;
    for &(start, end) in intervals {
        if start < previous || end <= start || end > maximum {
            return Err(invalid("采集区间必须有序、非负且位于训练有效时间内"));
        }
        duration = duration
            .checked_add(end - start)
            .ok_or_else(|| invalid("采集时长溢出"))?;
        previous = end;
    }
    Ok(duration)
}
fn submitted_array(value: &Value) -> Outcome<Vec<Value>> {
    let text = value
        .as_str()
        .ok_or_else(|| invalid("原始拍发字段必须是JSON数组文本"))?;
    serde_json::from_str(text).map_err(|_| invalid("原始拍发字段必须是JSON数组文本"))
}

fn measure_submission(
    body: &Value,
    domain: Domain,
    train_type: Option<i64>,
) -> Outcome<(usize, f64)> {
    if matches!(domain, Domain::Telex) {
        let text = body["patValue"]
            .as_str()
            .ok_or_else(|| invalid("页面内容不能为空"))?;
        return Ok((super::telex_capture::character_count(text, train_type), 0.0));
    }
    let groups = body[if matches!(domain, Domain::Key) {
        "pageValue"
    } else {
        "messageBody"
    }]
    .as_array()
    .ok_or_else(|| invalid("拍发记录不能为空"))?;
    if matches!(domain, Domain::Ticker) && !body["standard"].is_array() {
        return Err(invalid("校准记录不能为空"));
    }
    let mut previous = None;
    let mut count = 0;
    let mut milliseconds = 0.0;
    for group in groups {
        if !group.is_object() {
            return Err(invalid("拍发组不能为空"));
        }
        if matches!(domain, Domain::Key) {
            let sort = group["sort"]
                .as_i64()
                .filter(|sort| *sort >= 0)
                .ok_or_else(|| invalid("拍发组排序不合法"))?;
            if previous.is_some_and(|previous| sort <= previous) {
                return Err(invalid("拍发组必须有序且不能重复"));
            }
            previous = Some(sort);
        }
        let characters = submitted_array(
            &group[if matches!(domain, Domain::Key) {
                "value"
            } else {
                "patKeys"
            }],
        )?;
        for character in &characters {
            let character = character
                .as_str()
                .filter(|ch| ch.chars().count() == 1)
                .ok_or_else(|| invalid("每个拍发事件必须是一个正文字符"))?;
            if !character.trim().is_empty() && character != "?" {
                count += 1;
            }
        }
        let times = submitted_array(
            &group[if matches!(domain, Domain::Key) {
                "time"
            } else {
                "moresTime"
            }],
        )?;
        let durations = if matches!(domain, Domain::Key) {
            times
        } else {
            let signals = submitted_array(&group["moresValue"])?;
            if signals.len() != characters.len() || times.len() != characters.len() {
                return Err(invalid("拍发字符、原始码和时长数量不一致"));
            }
            let mut durations = Vec::new();
            for (signal, time) in signals.iter().zip(&times) {
                let signal = signal
                    .as_array()
                    .filter(|signal| !signal.is_empty())
                    .ok_or_else(|| invalid("原始点划不能为空"))?;
                let time = time
                    .as_array()
                    .ok_or_else(|| invalid("原始时长必须是数组"))?;
                if signal.len() != time.len()
                    || signal.iter().any(|value| {
                        !matches!(value.as_str(), Some("0") | Some("1"))
                            && !matches!(value.as_i64(), Some(0) | Some(1))
                    })
                {
                    return Err(invalid("原始点划与时长数量不一致"));
                }
                durations.extend(time.iter().cloned());
            }
            durations
        };
        for time in durations {
            milliseconds += time
                .as_f64()
                .filter(|time| time.is_finite() && *time >= 0.0)
                .ok_or_else(|| invalid("原始拍发时长必须为有限非负数"))?;
        }
    }
    Ok((count, milliseconds))
}

fn percentage(count: i64, total: i64) -> i64 {
    if total <= 0 {
        0
    } else {
        ((count as f64 / total as f64) * 100.0).round() as i64
    }
}

async fn save_generated_group(
    connection: &mut MySqlConnection,
    id: &Value,
    domain: Domain,
    page: usize,
    sort: usize,
    group: String,
) -> Outcome<()> {
    let key = if matches!(domain, Domain::Telex) {
        group
    } else {
        json!(group.chars().map(|ch| ch.to_string()).collect::<Vec<_>>()).to_string()
    };
    let row = if matches!(domain, Domain::Ticker) {
        json!({"id":uuid::Uuid::new_v4().to_string(),"trainId":id,"floorNumber":page,"sort":sort,"moresKey":key,"moresValue":"[]","moresTime":"[]","patKeys":"[]"})
    } else {
        // Java stores no `value` on a source page of the telex training.
        json!({"id":uuid::Uuid::new_v4().to_string(),"trainId":id,"pageNumber":page,"sort":sort,"key":key,"value":if matches!(domain, Domain::Telex) { Value::Null } else { json!("[]") },"time":"[]"})
    };
    insert_fields(connection, page_table(domain), page_fields(domain), &row).await?;
    Ok(())
}
/// Java `deriveCapture`: per-page and total rate from the raw rows' capture intervals, written to
/// the member row by primary key. Returns the member's total rate (Java `member.speed`).
async fn derive_telex_capture(
    connection: &mut MySqlConnection,
    member_id: &str,
    train_id: &str,
    user_id: &str,
    train_type: Option<i64>,
) -> Outcome<rust_decimal::Decimal> {
    let rows = rows(
        connection,
        value_table(Domain::Telex),
        value_fields(Domain::Telex),
        "WHERE train_id=? AND user_id=? AND sort=-1 ORDER BY page_number",
        &[train_id, user_id],
    )
    .await?;
    let mut total_duration = 0_i64;
    let mut total_characters = 0_usize;
    let mut page_rates = Vec::new();
    let mut page_times = Vec::new();
    for row in rows {
        let page = row["pageNumber"]
            .as_i64()
            .ok_or_else(|| invalid("拍发页码缺失"))?;
        let intervals = stored_json(
            row["captureIntervals"]
                .as_str()
                .ok_or_else(|| invalid("已保存页缺少原始采集时间轴"))?,
        )?;
        let duration = interval_duration(&capture_intervals(&intervals)?, i64::MAX)?;
        let characters = super::telex_capture::character_count(
            row["value"]
                .as_str()
                .ok_or_else(|| invalid("页面内容不能为空"))?,
            train_type,
        );
        total_duration = total_duration
            .checked_add(duration)
            .ok_or_else(|| invalid("采集时长溢出"))?;
        total_characters = total_characters
            .checked_add(characters)
            .ok_or_else(|| invalid("拍发字符数量超出范围"))?;
        while page_rates.len() < page as usize {
            page_rates.push(Value::Null);
            page_times.push(Value::Null);
        }
        page_rates[page as usize - 1] =
            json!(super::scoring::rate_per_minute(characters as i64, duration).to_string());
        page_times[page as usize - 1] = json!(duration / 1000);
    }
    let speed = super::scoring::rate_per_minute(total_characters as i64, total_duration);
    sqlx::query(
        "UPDATE general_telex_pat_user SET active_millis=?,valid_time=?,speed=?,speed_log=?,valid_time_log=? WHERE id=?",
    )
    .bind(total_duration)
    .bind(i32::try_from(total_duration / 1000).map_err(|_| invalid("采集时长超出范围"))?)
    .bind(speed.to_string())
    .bind(Value::Array(page_rates).to_string())
    .bind(Value::Array(page_times).to_string())
    .bind(member_id)
    .execute(connection)
    .await?;
    Ok(speed)
}

/// `/api/socket/generalTickerPatTrain/updateTrainStatus` (teacher.js:299): the same Java
/// `updateStatus` transition as the training's own status endpoint.
pub(crate) async fn ticker_socket_update(
    state: AppState,
    actor: AuthUser,
    train_id: i64,
    status: Option<i64>,
) -> Response {
    let status = status
        .and_then(|status| i32::try_from(status).ok())
        .unwrap_or(-1);
    set_status(state, actor, train_id.to_string(), status, Domain::Ticker).await
}

pub(crate) async fn ticker_update(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let id = match train_id(&body) {
        Ok(id) => id,
        Err(error) => return response(Err(error)),
    };
    let status = body["status"]
        .as_i64()
        .and_then(|status| i32::try_from(status).ok())
        .unwrap_or(-1);
    set_status(state, actor, id, status, Domain::Ticker).await
}

fn numeric_pairs(count: usize, opposite: bool) -> Vec<String> {
    use rand::seq::SliceRandom;
    let mut rng = rand::thread_rng();
    let mut result = Vec::with_capacity(count);
    for start in (0..count).step_by(10) {
        let size = (count - start).min(10);
        let mut left: Vec<u8> = (0..size * 2).map(|index| b"12345"[index % 5]).collect();
        let mut right: Vec<u8> = (0..size * 2).map(|index| b"67890"[index % 5]).collect();
        left.shuffle(&mut rng);
        right.shuffle(&mut rng);
        for index in 0..size {
            for side in [&mut left, &mut right] {
                let first = index * 2;
                if side[first] == side[first + 1] {
                    if let Some(next) =
                        (first + 2..side.len()).find(|next| side[*next] != side[first])
                    {
                        side.swap(first + 1, next);
                    } else if first > 0 {
                        if let Some(previous) = (0..first).find(|previous| {
                            side[*previous] != side[first] && side[*previous ^ 1] != side[first]
                        }) {
                            side.swap(first + 1, previous);
                        }
                    }
                }
            }
        }
        for index in 0..size {
            let mut group = if opposite {
                [
                    left[index * 2],
                    right[index * 2],
                    left[index * 2 + 1],
                    right[index * 2 + 1],
                ]
            } else {
                [
                    left[index * 2],
                    left[index * 2 + 1],
                    right[index * 2],
                    right[index * 2 + 1],
                ]
            };
            if rand::random::<bool>() {
                if opposite {
                    group.swap(0, 1);
                    group.swap(2, 3);
                } else {
                    group.rotate_left(2);
                }
            }
            result.push(group.into_iter().map(char::from).collect());
        }
    }
    result
}

fn settlement_number(value: &Value) -> Option<f64> {
    value
        .as_f64()
        .or_else(|| value.as_str().and_then(|text| text.parse::<f64>().ok()))
}

fn json_array_text(value: &Value, message: &'static str) -> Outcome<Vec<Value>> {
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

fn source_group_text(row: &Value, domain: Domain) -> Outcome<String> {
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

fn frozen_full_score(train: &Value, domain: Domain) -> Outcome<f64> {
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
struct KeySettlementStatistics {
    pat_group: i64,
    lack: i64,
    more: i64,
    lack_group: i64,
    more_group: i64,
    lack_line: i64,
    more_line: i64,
    alter_error: i64,
    error: i64,
    bunch_group: i64,
    lack_gap: i64,
}

#[derive(Clone)]
struct KeyCapturedGroup {
    raw: String,
    value: String,
}

fn corrected_key_group(value: &str) -> String {
    value
        .rfind('?')
        .map(|index| value[index + 1..].to_owned())
        .unwrap_or_else(|| value.to_owned())
}

fn key_captured_groups(
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

fn align_key_groups(
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

fn score_text(value: f64) -> String {
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

fn score_key_categories(
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

fn score_telex_categories(
    train: &Value,
    rule: &Value,
    stats: &super::telex_handle::Stats,
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

/// Locks one member row. The member tables are indexed by primary key only, so a locking read
/// filtered by `train_id`/`user_id` would next-key lock every row it scans (including other
/// trainings' members). Callers hold the training row lock first; the id is read plainly and the
/// row is then locked by primary key.
async fn locked_member(
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
fn member_capture_duration(member: &Value, domain: Domain, value_rows: &[Value]) -> Outcome<i64> {
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

async fn settle_key(
    connection: &mut MySqlConnection,
    train: &Value,
    member: &Value,
    user_id: &str,
) -> Outcome<()> {
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    let rule = frozen_rule(train, Domain::Key)?;
    let values = rows(
        connection,
        value_table(Domain::Key),
        value_fields(Domain::Key),
        "WHERE train_id=? AND user_id=? ORDER BY page_number,sort,id",
        &[&id, user_id],
    )
    .await?;
    let duration = member_capture_duration(member, Domain::Key, &values)?;
    let captures = stored_json(
        member["capturePages"]
            .as_str()
            .ok_or_else(|| invalid("缺少原始采集页索引"))?,
    )?;
    let captured_pages = captures
        .as_object()
        .ok_or_else(|| invalid("缺少原始采集页索引"))?;
    let sources = rows(
        connection,
        page_table(Domain::Key),
        page_fields(Domain::Key),
        "WHERE train_id=? ORDER BY page_number,sort",
        &[&id],
    )
    .await?;
    let mut source_by_page: HashMap<i64, Vec<Value>> = HashMap::new();
    for source in sources {
        source_by_page
            .entry(
                source["pageNumber"]
                    .as_i64()
                    .ok_or_else(|| invalid("报底页码缺失"))?,
            )
            .or_default()
            .push(source);
    }
    let mut values_by_page: HashMap<i64, Vec<Value>> = HashMap::new();
    for value in values {
        values_by_page
            .entry(
                value["pageNumber"]
                    .as_i64()
                    .ok_or_else(|| invalid("拍发页码缺失"))?,
            )
            .or_default()
            .push(value);
    }
    let mut statistics = KeySettlementStatistics::default();
    let mut characters = 0_i64;
    let mut pages = source_by_page.keys().copied().collect::<Vec<_>>();
    pages.sort_unstable();
    for page in pages {
        let source_rows = source_by_page
            .get(&page)
            .map(Vec::as_slice)
            .unwrap_or_default();
        if !captured_pages.contains_key(&page.to_string()) {
            continue;
        }
        let page_values = values_by_page
            .get(&page)
            .map(Vec::as_slice)
            .unwrap_or_default();
        let (captured, page_characters) = key_captured_groups(page_values, &mut statistics)?;
        characters += page_characters;
        align_key_groups(source_rows, &captured, &mut statistics)?;
    }
    let total_groups = if train["isCable"] == 1 {
        source_by_page.values().map(|rows| rows.len() as i64).sum()
    } else {
        train["totalNumber"]
            .as_i64()
            .ok_or_else(|| invalid("训练总组数缺失，无法结算"))?
    };
    let total_pages = (total_groups + 99) / 100;
    let remaining = total_groups % 100;
    let captured_count = captured_pages.len() as i64;
    if captured_count < total_pages {
        // Registered Java defect (GeneralKeyPatService.countScore, the missing-page block):
        // the per-missing-page loop and the closed formula below both add to the same lack
        // counters, so a missing page is charged twice. Replicated exactly until both stacks
        // are fixed together (decided 2026-10-11). `missing` is Java's `totalPageNumberList`
        // after `removeAll(capturedPages)`; its last entry takes the remainder branch.
        let captured_numbers = captured_pages
            .keys()
            .filter_map(|page| page.parse::<i64>().ok())
            .collect::<Vec<_>>();
        let missing = (1..=total_pages)
            .filter(|page| !captured_numbers.contains(page))
            .count() as i64;
        for index in 0..missing {
            if index == missing - 1 {
                if total_groups % 100 == 0 {
                    statistics.lack_line += 10;
                } else {
                    statistics.lack_line += remaining / 10;
                    if remaining % 10 > 0 {
                        statistics.more_group += remaining % 10;
                    }
                }
            } else {
                statistics.lack_line += 10;
            }
        }
        if remaining > 0 {
            statistics.lack_group += (total_pages - 1 - captured_count) * 100 + remaining;
            statistics.lack_line += (total_pages - 1 - captured_count) * 10 + remaining / 10
                - (statistics.pat_group / 10 + 1);
        } else {
            statistics.lack_group += (total_pages - captured_count) * 100;
            statistics.lack_line +=
                (total_pages - captured_count) * 10 - (statistics.pat_group / 10 + 1);
        }
    }
    let speed = if duration > 0 {
        (characters as f64 * 15_000.0 / duration as f64).round()
    } else {
        0.0
    };
    let score = score_key_categories(train, &rule, &statistics, speed)?;
    let accuracy_numerator = statistics.pat_group
        - statistics.error
        - statistics.bunch_group
        - statistics.lack
        - statistics.more;
    // Java ScoreMath.accuracy: the ratio is rounded to two places first, then scaled by 100.
    let accuracy_text = if statistics.pat_group <= 0 {
        "0".to_owned()
    } else {
        format!(
            "{:.2}",
            super::scoring::accuracy_percent(accuracy_numerator, statistics.pat_group)
        )
    };
    let sql = "UPDATE general_key_pat_user SET active_millis=?,duration=?,speed=?,accuracy=?,error_number=?,score=?,deduct_info=?,is_finish=1,finish_time=NOW(6) WHERE id=?";
    sqlx::query(sql)
        .bind(duration)
        .bind((duration / 1000).to_string())
        .bind(score_text(speed))
        .bind(accuracy_text)
        .bind(i32::try_from(statistics.error).map_err(|_| invalid("错误数量超出范围"))?)
        .bind(format!("{:.2}", score.0))
        .bind(score.1.to_string())
        .bind(text(&member["id"]).ok_or_else(|| invalid("参训记录主键缺失"))?)
        .execute(connection)
        .await?;
    Ok(())
}

/// Result of settling one telex member as Java returns it from memory: the response carries the
/// freshly computed BigDecimals ("92.00" / "0" accuracy, integer speed), not the stored columns.
struct TelexSettled {
    accuracy: String,
    speed: String,
}

/// Java `countScore` + `settleMember` for the group telex training. Per page that has a raw
/// submission the Java comparison (`telex_handle::handle`) yields counters and the analysis
/// rows (`sort >= 0`); the old analysis rows of the member are replaced, the raw rows
/// (`sort = -1`) stay. Every write is by primary key (the child tables have no other index).
async fn settle_telex(
    connection: &mut MySqlConnection,
    train: &Value,
    member: &Value,
    user_id: &str,
) -> Outcome<TelexSettled> {
    use super::telex_handle::{handle, PageKey, Stats, ValueRow};
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    let member_id = text(&member["id"]).ok_or_else(|| invalid("参训记录主键缺失"))?;
    let rule = frozen_rule(train, Domain::Telex)?;
    let speed = derive_telex_capture(
        connection,
        &member_id,
        &id,
        user_id,
        train["trainType"].as_i64(),
    )
    .await?;
    let values = rows(
        connection,
        value_table(Domain::Telex),
        value_fields(Domain::Telex),
        "WHERE train_id=? AND user_id=? AND sort=-1 ORDER BY page_number",
        &[&id, user_id],
    )
    .await?;
    member_capture_duration(member, Domain::Telex, &values)?;
    let sources = rows(
        connection,
        page_table(Domain::Telex),
        page_fields(Domain::Telex),
        "WHERE train_id=? ORDER BY page_number,sort",
        &[&id],
    )
    .await?;
    let mut source_by_page: std::collections::BTreeMap<i64, Vec<PageKey>> =
        std::collections::BTreeMap::new();
    for source in sources {
        source_by_page
            .entry(
                source["pageNumber"]
                    .as_i64()
                    .ok_or_else(|| invalid("报底页码缺失"))?,
            )
            .or_default()
            .push(PageKey {
                key: source["key"].as_str().unwrap_or_default().to_owned(),
                sort: source["sort"].as_i64().unwrap_or_default(),
            });
    }
    let mut submitted_by_page = HashMap::new();
    for value in &values {
        let page = value["pageNumber"]
            .as_i64()
            .ok_or_else(|| invalid("拍发页码缺失"))?;
        // Java takes the first raw row of the page.
        submitted_by_page
            .entry(page)
            .or_insert_with(|| value["value"].as_str().map(str::to_owned));
    }
    let mut stats = Stats::default();
    let mut analysis: Vec<ValueRow> = Vec::new();
    for (page, keys) in &source_by_page {
        if let Some(submitted) = submitted_by_page.get(page) {
            // A raw row without text is a Java NullPointerException inside `handle`.
            let submitted = submitted.as_deref().unwrap_or_default();
            handle(*page, keys, submitted, &mut stats, &mut analysis)
                .map_err(|_| java_exception())?;
        }
    }
    // Replace the member's analysis rows: plain-read the ids, delete and insert by primary key.
    let old: Vec<String> = sqlx::query_scalar(&format!(
        "SELECT CAST(id AS CHAR) FROM {} WHERE train_id=? AND user_id=? AND sort>-1 ORDER BY id",
        value_table(Domain::Telex)
    ))
    .bind(&id)
    .bind(user_id)
    .fetch_all(&mut *connection)
    .await?;
    for old_id in old {
        sqlx::query(&format!(
            "DELETE FROM {} WHERE id=?",
            value_table(Domain::Telex)
        ))
        .bind(old_id)
        .execute(&mut *connection)
        .await?;
    }
    for row in &analysis {
        sqlx::query(&format!(
            "INSERT INTO {} (id,train_id,user_id,page_number,sort,`key`,value) VALUES (?,?,?,?,?,?,?)",
            value_table(Domain::Telex)
        ))
        .bind(uuid::Uuid::new_v4().to_string())
        .bind(&id)
        .bind(user_id)
        .bind(row.page_number)
        .bind(row.sort)
        .bind(&row.key)
        .bind(&row.value)
        .execute(&mut *connection)
        .await?;
    }
    use rust_decimal::prelude::ToPrimitive;
    let speed_value = speed.to_i64().unwrap_or(0);
    let score = score_telex_categories(train, &rule, &stats, speed_value)?;
    // Java: no accuracy when the correct total is zero ("0"), otherwise the two-decimal ratio
    // times 100 ("92.00"); the correct total may be negative and is not clamped.
    let correct_total = stats.pat_group - stats.error_code - stats.much_less_code;
    let accuracy = if correct_total == 0 {
        rust_decimal::Decimal::ZERO
    } else if stats.pat_group == 0 {
        return Err(java_exception());
    } else {
        (rust_decimal::Decimal::from(correct_total) / rust_decimal::Decimal::from(stats.pat_group))
            .round_dp_with_strategy(2, rust_decimal::RoundingStrategy::MidpointAwayFromZero)
            * rust_decimal::Decimal::from(100)
    };
    let accuracy_text = if correct_total == 0 {
        "0".to_owned()
    } else {
        format!("{accuracy:.2}")
    };
    // `error_number` is not written: Java leaves the column at its creation value.
    let sql = "UPDATE general_telex_pat_user SET speed=?,accuracy=?,score=?,deduct_info=?,is_finish=1,finish_time=NOW(6) WHERE id=?";
    sqlx::query(sql)
        .bind(speed.to_string())
        .bind(format!("{accuracy:.2}"))
        .bind(format!("{:.2}", score.0))
        .bind(score.1.to_string())
        .bind(&member_id)
        .execute(&mut *connection)
        .await?;
    Ok(TelexSettled {
        accuracy: accuracy_text,
        speed: speed.to_string(),
    })
}

/// A Java runtime exception on the settlement path: HTTP 500 and a rolled-back transaction.
fn java_exception() -> Failure {
    Failure::Database(sqlx::Error::Protocol(
        "java-equivalent runtime exception while settling a telex training".into(),
    ))
}

/// Hand-key settlement. Java scores it with the same MessageComparisonService / handkey rule
/// machinery as the post-telegram training, so this reuses that port: `compare_page` yields the
/// page resolver and the score counters, `scoring_result` the deduction and statistics JSON.
async fn settle_ticker(
    connection: &mut MySqlConnection,
    train: &Value,
    member: &Value,
    user_id: &str,
) -> Outcome<()> {
    use super::post_telegram_write::{compare_page, handkey_rule, scoring_result, HandkeyScore};
    use rust_decimal::prelude::ToPrimitive;
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    let rule = frozen_rule(train, Domain::Ticker)?;
    let handkey = handkey_rule(Some(&rule.to_string())).map_err(invalid)?;
    let values = rows(
        connection,
        value_table(Domain::Ticker),
        value_fields(Domain::Ticker),
        "WHERE train_id=? AND user_id=? ORDER BY floor_number",
        &[&id, user_id],
    )
    .await?;
    let duration = member_capture_duration(member, Domain::Ticker, &values)?;
    let sources = rows(
        connection,
        page_table(Domain::Ticker),
        page_fields(Domain::Ticker),
        "WHERE train_id=? ORDER BY floor_number,sort",
        &[&id],
    )
    .await?;
    let mut source_by_page: HashMap<i64, Vec<Value>> = HashMap::new();
    for source in sources {
        source_by_page
            .entry(
                source["floorNumber"]
                    .as_i64()
                    .ok_or_else(|| invalid("报底楼层缺失"))?,
            )
            .or_default()
            .push(source);
    }
    let mut score = HandkeyScore::default();
    let mut characters = 0_i64;
    let mut existing_pages = std::collections::HashSet::new();
    let mut page_rates = Vec::with_capacity(values.len());
    let mut resolvers = Vec::with_capacity(values.len());
    for value in &values {
        let page = value["floorNumber"]
            .as_i64()
            .ok_or_else(|| invalid("拍发楼层缺失"))?;
        let body = json_array_text(&value["messageBody"], "拍发记录必须是JSON数组文本")?;
        let standard = json_array_text(&value["standard"], "校准记录必须是JSON数组文本")?;
        let submission = json!({"messageBody": body, "standard": standard});
        let (count, _) = measure_submission(&submission, Domain::Ticker, None)?;
        let page_duration = interval_duration(
            &capture_intervals(&stored_json(
                value["captureIntervals"]
                    .as_str()
                    .ok_or_else(|| invalid("已保存页缺少原始采集时间轴"))?,
            )?)?,
            i64::MAX,
        )?;
        // Java TrainingRateUnit.rate: whole characters per minute, HALF_UP.
        page_rates.push(json!(super::scoring::rate_per_minute(
            count as i64,
            page_duration
        )
        .to_string()));
        characters += count as i64;
        existing_pages.insert(page);
        let source_groups = source_by_page
            .get(&page)
            .map(Vec::as_slice)
            .unwrap_or_default()
            .iter()
            .map(|source| source_group_text(source, Domain::Ticker))
            .collect::<Outcome<Vec<_>>>()?;
        let resolver = compare_page(
            &source_groups,
            submission["messageBody"]
                .as_array()
                .map(Vec::as_slice)
                .unwrap_or_default(),
            submission["standard"]
                .as_array()
                .map(Vec::as_slice)
                .unwrap_or_default(),
            &handkey,
            &mut score,
        )
        .map_err(invalid)?;
        resolvers.push((
            text(&value["id"]).ok_or_else(|| invalid("已保存页缺少主键"))?,
            serde_json::to_string(&resolver).map_err(|_| invalid("页解析结果序列化失败"))?,
        ));
    }
    // Java calculateLackCount: every page of messageNumber that has no saved value counts as
    // missing, whether or not its base text was ever generated (a fixed report may have fewer
    // pages than messageNumber implies).
    let message_number = train["messageNumber"]
        .as_i64()
        .ok_or_else(|| invalid("训练总组数缺失，无法结算"))?;
    let mut missing = 0_i64;
    for page in 1..=(message_number + 99) / 100 {
        if !existing_pages.contains(&page) {
            missing += 100.min(message_number - (page - 1) * 100);
        }
    }
    score.lack_groups += missing;
    let speed = super::scoring::rate_per_minute(characters, duration);
    let speed_number = speed
        .to_i64()
        .filter(|speed| *speed <= i64::from(i32::MAX))
        .ok_or_else(|| invalid("训练速率超出有效范围"))?;
    let full_score = frozen_full_score(train, Domain::Ticker)?;
    let (total, statistics, deductions) =
        scoring_result(&score, &handkey, full_score as i32, speed_number, false);
    // Java: no correct group at all is "0.00"; otherwise ScoreMath's two-place ratio times 100.
    let accuracy = if score.correct() == 0 {
        "0.00".to_owned()
    } else {
        format!(
            "{:.2}",
            super::scoring::accuracy_percent(score.correct(), score.total())
        )
    };
    resolvers.sort_by_key(|(page_id, _)| page_id.parse::<i64>().unwrap_or(i64::MAX));
    for (page_id, resolver) in resolvers {
        sqlx::query("UPDATE general_ticker_pat_train_user_value SET resolver=? WHERE id=?")
            .bind(resolver)
            .bind(page_id)
            .execute(&mut *connection)
            .await?;
    }
    let sql = "UPDATE general_ticker_pat_train_user SET active_millis=?,speed=?,speed_log=?,accuracy=?,error_number=?,lack=?,score=?,deduct_info=?,statistic_info=?,is_finish=1,finish_time=NOW(6) WHERE id=?";
    sqlx::query(sql)
        .bind(duration)
        .bind(speed.to_string())
        .bind(Value::Array(page_rates).to_string())
        .bind(accuracy)
        .bind(i32::try_from(score.errors()).map_err(|_| invalid("错误数量超出范围"))?)
        .bind(i32::try_from(missing).map_err(|_| invalid("缺报数量超出范围"))?)
        .bind(format!("{total}.00"))
        .bind(deductions.to_string())
        .bind(statistics.to_string())
        .bind(text(&member["id"]).ok_or_else(|| invalid("参训记录主键缺失"))?)
        .execute(connection)
        .await?;
    Ok(())
}

async fn finish_participant(
    state: AppState,
    actor: AuthUser,
    body: Value,
    domain: Domain,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let id = train_id(&body)?;
        // Java order: lockedTrain -> student -> requireAttempt, so a missing train is 202 before any 208.
        let attempt = body["attempt"].as_i64();
        let mut transaction = pool.begin().await?;
        let train = load_train(&mut transaction, &id, domain, true).await?;
        let member = locked_member(&mut transaction, &id, &actor.id, domain).await?;
        if member["role"] != 0 { return Err(if matches!(domain, Domain::Telex) { denied() } else { invalid(STUDENT_MISSING) }); }
        let attempt = attempt.ok_or_else(|| terminal("训练轮次已变化，请重新读取训练"))?;
        if member["attempt"] != attempt { return Err(terminal("训练轮次已变化，请重新读取训练")); }
        if member["isFinish"] == 1 {
            let result = finish_member(&member, domain);
            transaction.commit().await?;
            return Ok(if matches!(domain, Domain::Ticker) { train_response(train, domain) } else { json!([result]) });
        }
        if train["protocolVersion"] != 1 {
            return Err(if matches!(domain, Domain::Telex) { terminal("旧训练缺少原始采集协议，请重新创建训练；历史成绩保持不变") } else { invalid("旧训练缺少原始采集协议，请重新创建训练") });
        }
        let status = train["status"].as_i64().unwrap_or(-1);
        frozen_rule(&train, domain)?;
        // Java settles with whatever pages exist: a missing page is counted as lacking groups,
        // never generated at finish.
        let mut telex_settled = None;
        let mut settled_all = false;
        let mut settled_users = vec![actor.id.clone()];
        if !matches!(domain, Domain::Telex) {
            if status == i64::from(closing_status(domain)) {
                // Java accepts an in-flight finish during the 60-second grace
                // window; after it expires it settles every remaining student.
                if closing_window_open(&mut transaction, &id, domain).await? {
                    capture_bound(&mut transaction, &id, &actor.id, domain).await?;
                } else {
                    settled_all = true;
                    settled_users.clear();
                    let students: Vec<String> = sqlx::query_scalar(&format!(
                        "SELECT user_id FROM {} WHERE train_id=? AND role=0 ORDER BY id",
                        user_table(domain)
                    ))
                    .bind(&id)
                    .fetch_all(&mut *transaction)
                    .await?;
                    for user_id in students {
                        let candidate = locked_member(&mut transaction, &id, &user_id, domain).await?;
                        if candidate["isFinish"] == 1 { continue; }
                        match domain {
                            Domain::Key => settle_key(&mut transaction, &train, &candidate, &user_id).await?,
                            Domain::Ticker => settle_ticker(&mut transaction, &train, &candidate, &user_id).await?,
                            Domain::Telex => unreachable!(),
                        }
                        settled_users.push(user_id);
                    }
                    sqlx::query(&format!("UPDATE {} SET status=2 WHERE id=?", table(domain)))
                        .bind(&id)
                        .execute(&mut *transaction)
                        .await?;
                }
            } else if status != 1 {
                return Err(invalid("训练不在进行中"));
            }
        }
        // An expired closing room was fully settled above.  Otherwise settle
        // only the caller, matching Java's idempotent participant finish.
        if !settled_all {
            match domain {
                Domain::Key => settle_key(&mut transaction, &train, &member, &actor.id).await?,
                Domain::Telex => telex_settled = Some(settle_telex(&mut transaction, &train, &member, &actor.id).await?),
                Domain::Ticker => settle_ticker(&mut transaction, &train, &member, &actor.id).await?,
            }
        }
        let recipients: Vec<String> = sqlx::query_scalar(&format!(
            "SELECT user_id FROM {} WHERE train_id=? AND role=1",
            user_table(domain)
        ))
        .bind(&id)
        .fetch_all(&mut *transaction)
        .await?;
        if matches!(domain, Domain::Key | Domain::Ticker) && status == 3 {
            let unfinished: i64 = sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {} WHERE train_id=? AND role=0 AND COALESCE(is_finish,0)<>1", user_table(domain))).bind(&id).fetch_one(&mut *transaction).await?;
            if unfinished == 0 { sqlx::query(&format!("UPDATE {} SET status=2 WHERE id=?", table(domain))).bind(&id).execute(&mut *transaction).await?; }
        }
        let result = if matches!(domain, Domain::Ticker) {
            train_response(load_train(&mut transaction, &id, domain, false).await?, domain)
        } else {
            let member = load_member(&mut transaction, &id, &actor.id, domain)
                .await?
                .ok_or_else(|| invalid("未查询到参训记录"))?;
            let mut result = finish_member(&member, domain);
            if let Some(settled) = telex_settled {
                // Java answers with the in-memory entity, not the stored decimal columns.
                result["accuracy"] = json!(settled.accuracy);
                result["speed"] = json!(settled.speed);
            }
            json!([result])
        };
        transaction.commit().await?;
        for user_id in settled_users {
            super::ws::notify_general_result(ws_kind(domain), &id, &user_id, &recipients).await;
        }
        Ok(result)
    }.await)
}

fn closing_status(domain: Domain) -> i32 {
    match domain {
        Domain::Telex => 2,
        Domain::Key | Domain::Ticker => 3,
    }
}

async fn recovery_train_ids(pool: &MySqlPool, domain: Domain) -> Result<Vec<String>, sqlx::Error> {
    let sql = format!(
        "SELECT CAST(t.id AS CHAR) FROM {} t WHERE t.protocol_version=1 AND t.status=? AND t.end_time IS NOT NULL AND t.end_time <= DATE_SUB(NOW(6), INTERVAL 60 SECOND) AND EXISTS (SELECT 1 FROM {} u WHERE u.train_id=t.id AND u.role=0 AND COALESCE(u.is_finish,0)<>1)",
        table(domain),
        user_table(domain),
    );
    sqlx::query_scalar(&sql)
        .bind(closing_status(domain))
        .fetch_all(pool)
        .await
}

async fn recover_one(pool: &MySqlPool, domain: Domain, id: &str) -> Outcome<()> {
    let mut transaction = pool.begin().await?;
    let train = load_train(&mut transaction, id, domain, true).await?;
    let eligible: i64 = sqlx::query_scalar(&format!(
        "SELECT EXISTS(SELECT 1 FROM {} WHERE id=? AND protocol_version=1 AND status=? AND end_time IS NOT NULL AND end_time <= DATE_SUB(NOW(6), INTERVAL 60 SECOND))",
        table(domain),
    ))
    .bind(id)
    .bind(closing_status(domain))
    .fetch_one(&mut *transaction)
    .await?;
    if eligible == 0 {
        transaction.commit().await?;
        return Ok(());
    }
    frozen_rule(&train, domain)?;
    // Plain read: each member is then locked by primary key (locked_member), in id order.
    let member_ids: Vec<String> = sqlx::query_scalar(&format!(
        "SELECT user_id FROM {} WHERE train_id=? AND role=0 AND COALESCE(is_finish,0)<>1 ORDER BY id",
        user_table(domain),
    ))
    .bind(id)
    .fetch_all(&mut *transaction)
    .await?;
    let recipients: Vec<String> = sqlx::query_scalar(&format!(
        "SELECT user_id FROM {} WHERE train_id=? AND role=1",
        user_table(domain),
    ))
    .bind(id)
    .fetch_all(&mut *transaction)
    .await?;
    let mut settled = Vec::new();
    for user_id in member_ids {
        let member = locked_member(&mut transaction, id, &user_id, domain).await?;
        if member["role"] != 0 || member["isFinish"] == 1 {
            continue;
        }
        match domain {
            Domain::Key => settle_key(&mut transaction, &train, &member, &user_id).await?,
            Domain::Telex => {
                settle_telex(&mut transaction, &train, &member, &user_id).await?;
            }
            Domain::Ticker => settle_ticker(&mut transaction, &train, &member, &user_id).await?,
        }
        settled.push(user_id);
    }
    if matches!(domain, Domain::Key | Domain::Ticker) {
        // A NOT EXISTS subquery inside the UPDATE would S-lock every member row it scans.
        let unfinished: i64 = sqlx::query_scalar(&format!(
            "SELECT COUNT(*) FROM {} WHERE train_id=? AND role=0 AND COALESCE(is_finish,0)<>1",
            user_table(domain),
        ))
        .bind(id)
        .fetch_one(&mut *transaction)
        .await?;
        if unfinished == 0 {
            sqlx::query(&format!("UPDATE {} SET status=2 WHERE id=?", table(domain)))
                .bind(id)
                .execute(&mut *transaction)
                .await?;
        }
    }
    transaction.commit().await?;
    for user_id in &settled {
        super::ws::notify_general_result(ws_kind(domain), id, user_id, &recipients).await;
    }
    Ok(())
}

/// Sweep completed rooms whose 60-second submission grace period has elapsed.
/// Every candidate is rechecked under a transaction lock before frozen-rule
/// settlement, and failures remain retryable.
pub(crate) async fn recover_general_trainings(state: &AppState) {
    let Some(pool) = state.pool.as_ref() else {
        tracing::warn!("general training recovery skipped: database unavailable");
        return;
    };
    for domain in [Domain::Key, Domain::Ticker, Domain::Telex] {
        let ids = match recovery_train_ids(pool, domain).await {
            Ok(ids) => ids,
            Err(error) => {
                tracing::error!(domain = ws_kind(domain), error = %error, "general training recovery candidate query failed");
                continue;
            }
        };
        for id in ids {
            if let Err(error) = recover_one(pool, domain, &id).await {
                tracing::error!(domain = ws_kind(domain), train_id = %id, error = ?error, "general training recovery failed; candidate remains retryable");
            }
        }
    }
}

#[cfg(test)]
#[path = "general_ticker_writer_tests.rs"]
mod ticker_writer_tests;

#[cfg(test)]
#[path = "general_key_writer_tests.rs"]
mod key_writer_tests;

#[cfg(test)]
#[path = "general_telex_writer_tests.rs"]
mod telex_writer_tests;
