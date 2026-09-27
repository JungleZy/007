use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rust_decimal::Decimal;
use serde_json::{json, Value};
use sqlx::Row;
use std::str::FromStr;

#[derive(serde::Deserialize)]
pub(crate) struct AddRequest {
    name: Option<String>,
    #[serde(rename = "isCable")]
    is_cable: Option<i32>,
    #[serde(rename = "cableId")]
    _cable_id: Option<String>,
    #[serde(rename = "startPage")]
    _start_page: Option<i32>,
    #[serde(rename = "type")]
    train_type: Option<i32>,
    #[serde(rename = "trainType")]
    train_kind: Option<i32>,
    #[serde(rename = "groupNumber")]
    group_number: Option<i32>,
    #[serde(rename = "patType")]
    pat_type: Option<i32>,
    #[serde(rename = "ruleId")]
    rule_id: Option<String>,
    content: Option<String>,
}
#[derive(serde::Deserialize)]
pub(crate) struct ActionRequest {
    id: Option<String>,
    attempt: Option<i32>,
    #[serde(rename = "countdownSeconds")]
    _countdown_seconds: Option<i32>,
}
#[derive(serde::Deserialize)]
pub(crate) struct DeleteQuery {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
}

fn err(e: sqlx::Error, op: &'static str) -> Response {
    tracing::warn!(error=%e,operation=op,"post telex write failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}
fn business(code: i32, message: &'static str) -> Response {
    envelope_response(StatusCode::OK, code, message, "")
}
fn invalid(message: &'static str) -> Response {
    business(202, message)
}
fn number(value: &Value) -> Result<Decimal, &'static str> {
    match value {
        Value::Number(n) => Decimal::from_str(&n.to_string()).map_err(|_| "评分规则字段必须为数字"),
        Value::String(s) => Decimal::from_str(s).map_err(|_| "评分规则字段必须为数字"),
        _ => Err("评分规则字段必须为数字"),
    }
}
fn rule_decimal(rule: &Value, key: &str) -> Result<Decimal, &'static str> {
    number(
        rule.get("other")
            .and_then(|v| v.get(key))
            .ok_or("评分规则字段缺失")?,
    )
}
fn validate_rule(content: Option<&str>) -> Result<Value, &'static str> {
    let rule: Value = serde_json::from_str(content.ok_or("评分规则缺失")?)
        .map_err(|_| "评分规则必须是完整JSON对象")?;
    if !rule.is_object() || rule.get("rateUnit").and_then(Value::as_str).is_none() {
        return Err("评分规则格式无效");
    }
    if rule
        .get("wpm")
        .and_then(|v| v.get("base"))
        .and_then(Value::as_i64)
        .is_none()
    {
        return Err("评分规则wpm.base缺失");
    }
    for key in [
        "errorCode",
        "muchLessCode",
        "muchLessLine",
        "muchLessGroups",
        "correctMistakes",
        "lessPage",
        "lessReturnLine",
        "errorPage",
        "nonStandart",
    ] {
        rule_decimal(&rule, key)?;
    }
    Ok(rule)
}
fn frozen_score(score: Option<String>) -> Result<Decimal, &'static str> {
    let value = Decimal::from_str(&score.ok_or("训练冻结满分必须为非负数")?)
        .map_err(|_| "训练冻结满分必须为非负数")?;
    if value < Decimal::ZERO {
        Err("训练冻结满分必须为非负数")
    } else {
        Ok(value)
    }
}
fn generated(kind: i32, count: usize) -> Vec<String> {
    let alphabet = match kind {
        1 => b"ABCDEFGHIJKLMNOPQRSTUVWXYZ".as_slice(),
        2 => b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        _ => b"0123456789",
    };
    use rand::Rng;
    let mut rng = rand::thread_rng();
    (0..count)
        .map(|_| {
            (0..4)
                .map(|_| alphabet[rng.gen_range(0..alphabet.len())] as char)
                .collect()
        })
        .collect()
}
async fn generate_page(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    id: &str,
    kind: i32,
    page: i32,
    count: usize,
) -> Result<(), sqlx::Error> {
    for (sort, key) in generated(kind, count).into_iter().enumerate() {
        sqlx::query("INSERT INTO t_post_telex_pat_train_page (id,train_id,`key`,page_number,sort) VALUES (?,?,?,?,?)").bind(uuid::Uuid::new_v4().to_string()).bind(id).bind(key).bind(page).bind(sort as i32).execute(&mut **tx).await?;
    }
    Ok(())
}

pub(crate) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<AddRequest>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let is_cable = body.is_cable.unwrap_or(0);
    let Some(groups) = body.group_number else {
        return invalid("组数不能为空");
    };
    if groups <= 0 {
        return invalid("组数不能为空");
    };
    let Some(kind) = body.train_type else {
        return invalid("报文类型不能为空");
    };
    if is_cable == 0 && kind == 0 && body.pat_type.is_none() {
        return invalid("拍发类型不能为空");
    };
    let Some(rule_id) = body.rule_id.filter(|v| !v.trim().is_empty()) else {
        return invalid("未查询到评分规则");
    };
    let mut tx = match pool.begin().await {
        Ok(v) => v,
        Err(e) => return err(e, "add transaction"),
    };
    let rule =
        match sqlx::query("SELECT score,content FROM t_grading_rule WHERE id=? LIMIT 1 FOR UPDATE")
            .bind(&rule_id)
            .fetch_optional(&mut *tx)
            .await
        {
            Ok(Some(v)) => v,
            Ok(None) => return invalid("未查询到评分规则"),
            Err(e) => return err(e, "add rule"),
        };
    let score = match frozen_score(rule.get::<Option<i32>, _>("score").map(|v| v.to_string())) {
        Ok(v) => v,
        Err(m) => return invalid(m),
    };
    if let Err(m) = validate_rule(rule.get::<Option<String>, _>("content").as_deref()) {
        return invalid(m);
    };
    let id = uuid::Uuid::new_v4().to_string();
    let result=sqlx::query("INSERT INTO t_post_telex_pat_train (id,name,is_cable,type,train_type,group_number,pat_type,rule_id,rule_content,create_user,status,protocol_version,attempt,score,content,accuracy,speed,total_speed,valid_time,error_number,pause_intervals) VALUES (?,?,?,?,?,?,?,?,?,?,0,1,0,?,?,?,?,?,?,?,?)").bind(&id).bind(body.name).bind(is_cable).bind(kind).bind(body.train_kind.unwrap_or(0)).bind(groups).bind(body.pat_type).bind(&rule_id).bind(rule.get::<Option<String>,_>("content")).bind(&user.id).bind(score.to_string()).bind(body.content).bind("0.00").bind("0").bind("0").bind(0i32).bind(0i32).bind("[]").execute(&mut *tx).await;
    if let Err(e) = result {
        return err(e, "add insert");
    };
    if is_cable == 0 {
        if let Err(e) = generate_page(&mut tx, &id, kind, 1, groups.min(200) as usize).await {
            return err(e, "add page");
        }
    }
    if let Err(e) = tx.commit().await {
        return err(e, "add commit");
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"id":id,"status":0,"protocolVersion":1,"attempt":0,"score":score.to_string()})),
        "ok",
        "",
    )
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Action {
    Begin,
    Pause,
    Resume,
    Finish,
    Reset,
}
#[derive(serde::Deserialize)]
struct Interval {
    #[serde(rename = "startedMs")]
    started_ms: i64,
    #[serde(rename = "endedMs")]
    ended_ms: i64,
}
fn intervals(raw: Option<String>) -> Result<Vec<Interval>, &'static str> {
    serde_json::from_str(raw.as_deref().ok_or("原始采集时间轴缺失，不能计算成绩")?)
        .map_err(|_| "原始采集时间轴缺失，不能计算成绩")
}
fn duration(values: &[Interval], bound: i64) -> Result<i64, &'static str> {
    let mut end = 0i64;
    let mut total = 0i64;
    for v in values {
        if v.started_ms < end || v.ended_ms <= v.started_ms || v.ended_ms > bound {
            return Err("采集区间必须有序、非负且位于训练有效时间内");
        };
        total = total
            .checked_add(v.ended_ms - v.started_ms)
            .ok_or("采集时长溢出")?;
        end = v.ended_ms
    }
    Ok(total)
}
fn text_groups(text: &str, kind: i32) -> Vec<String> {
    if kind == 0 {
        serde_json::from_str::<Vec<Value>>(text)
            .unwrap_or_default()
            .into_iter()
            .filter_map(|v| v.get("text").and_then(Value::as_str).map(str::to_owned))
            .collect()
    } else {
        text.split_whitespace().map(str::to_owned).collect()
    }
}
async fn derive_capture(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    id: &str,
    attempt: i32,
) -> Result<(), &'static str> {
    let train=sqlx::query("SELECT CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',start_time) DIV 1000 AS SIGNED) start_ms,CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',deadline) DIV 1000 AS SIGNED) deadline_ms,CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',paused_at) DIV 1000 AS SIGNED) paused_ms,pause_intervals FROM t_post_telex_pat_train WHERE id=? FOR UPDATE").bind(id).fetch_one(&mut **tx).await.map_err(|_|"服务器错误")?;
    let start = train
        .get::<Option<i64>, _>("start_ms")
        .ok_or("训练开始时刻缺失")?;
    let now: i64 = sqlx::query_scalar(
        "SELECT CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',NOW(3)) DIV 1000 AS SIGNED)",
    )
    .fetch_one(&mut **tx)
    .await
    .map_err(|_| "服务器错误")?;
    duration(&intervals(train.get("pause_intervals"))?, now - start)?;
    let rows=sqlx::query("SELECT page_number,attempt,capture_intervals,pat_value,CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',received_at) DIV 1000 AS SIGNED) received_ms FROM t_post_telex_pat_train_page_value WHERE train_id=? ORDER BY page_number").bind(id).fetch_all(&mut **tx).await.map_err(|_|"服务器错误")?;
    let mut total = 0i64;
    let mut chars = 0i64;
    let mut times: Vec<Option<i32>> = Vec::new();
    let mut speeds: Vec<Option<String>> = Vec::new();
    for row in rows {
        if row.get::<Option<i32>, _>("attempt") != Some(attempt) {
            return Err("训练轮次已改变");
        };
        let received = row
            .get::<Option<i64>, _>("received_ms")
            .ok_or("采集接收时刻缺失")?;
        let mut bound = received - start;
        if let Some(v) = train.get::<Option<i64>, _>("deadline_ms") {
            bound = bound.min(v - start)
        }
        if let Some(v) = train.get::<Option<i64>, _>("paused_ms") {
            bound = bound.min(v - start)
        }
        let millis = duration(&intervals(row.get("capture_intervals"))?, bound)?;
        let text = row
            .get::<Option<String>, _>("pat_value")
            .unwrap_or_default();
        chars += text.chars().count() as i64;
        total += millis;
        let page = row.get::<Option<i32>, _>("page_number").unwrap_or(1) as usize;
        times.resize(page, None);
        speeds.resize(page, None);
        times[page - 1] = Some((millis / 1000) as i32);
        speeds[page - 1] = Some(if millis == 0 {
            "0".into()
        } else {
            ((text.chars().count() as i64 * 60000 + millis / 2) / millis).to_string()
        })
    }
    let speed = if total == 0 {
        "0".into()
    } else {
        ((chars * 60000 + total / 2) / total).to_string()
    };
    sqlx::query("UPDATE t_post_telex_pat_train SET speed=?,total_speed=?,valid_time=?,speed_log=?,valid_time_log=? WHERE id=?").bind(&speed).bind(&speed).bind((total/1000)as i32).bind(serde_json::to_string(&speeds).unwrap_or_else(|_|"[]".into())).bind(serde_json::to_string(&times).unwrap_or_else(|_|"[]".into())).bind(id).execute(&mut **tx).await.map_err(|_|"服务器错误")?;
    Ok(())
}
async fn ensure_floor(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    id: &str,
    kind: i32,
    total: i32,
) -> Result<(), &'static str> {
    let pages = (total + 99) / 100;
    let existing: Vec<i32> = sqlx::query_scalar(
        "SELECT DISTINCT page_number FROM t_post_telex_pat_train_page WHERE train_id=?",
    )
    .bind(id)
    .fetch_all(&mut **tx)
    .await
    .map_err(|_| "服务器错误")?;
    for page in 1..=pages {
        if !existing.contains(&page) {
            generate_page(
                tx,
                id,
                kind,
                page,
                (total - (page - 1) * 100).min(100) as usize,
            )
            .await
            .map_err(|_| "服务器错误")?
        }
    }
    Ok(())
}
async fn settle(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    id: &str,
    user: &str,
    attempt: i32,
) -> Result<(), &'static str> {
    let train=sqlx::query("SELECT group_number,is_cable,type,train_type,score,rule_content FROM t_post_telex_pat_train WHERE id=? AND create_user=? FOR UPDATE").bind(id).bind(user).fetch_one(&mut **tx).await.map_err(|_|"未查询到训练信息")?;
    let full = frozen_score(train.get("score"))?;
    let rule = validate_rule(train.get::<Option<String>, _>("rule_content").as_deref())?;
    if train.get::<Option<i32>, _>("is_cable") == Some(0) {
        let total = train
            .get::<Option<i32>, _>("group_number")
            .ok_or("训练组数缺失，不能结算")?;
        if total <= 0 {
            return Err("训练组数缺失，不能结算");
        };
        ensure_floor(
            tx,
            id,
            train.get::<Option<i32>, _>("type").ok_or("报文类型缺失")?,
            total,
        )
        .await?
    }
    derive_capture(tx, id, attempt).await?;
    let kind = train
        .get::<Option<i32>, _>("train_type")
        .ok_or("训练类型缺失，无法结算")?;
    let source = sqlx::query(
        "SELECT `key` FROM t_post_telex_pat_train_page WHERE train_id=? ORDER BY page_number,sort",
    )
    .bind(id)
    .fetch_all(&mut **tx)
    .await
    .map_err(|_| "服务器错误")?;
    let values=sqlx::query("SELECT pat_value,attempt FROM t_post_telex_pat_train_page_value WHERE train_id=? ORDER BY page_number").bind(id).fetch_all(&mut **tx).await.map_err(|_|"服务器错误")?;
    let mut submitted = Vec::new();
    for row in values {
        if row.get::<Option<i32>, _>("attempt") != Some(attempt) {
            return Err("训练轮次已改变");
        };
        submitted.extend(text_groups(
            &row.get::<Option<String>, _>("pat_value")
                .unwrap_or_default(),
            kind,
        ))
    }
    let expected: Vec<String> = source
        .into_iter()
        .map(|r| r.get::<Option<String>, _>("key").unwrap_or_default())
        .collect();
    let mut errors = 0i64;
    let mut missing = 0i64;
    for i in 0..expected.len().max(submitted.len()) {
        match (expected.get(i), submitted.get(i)) {
            (Some(a), Some(b)) if a != b => errors += 1,
            (Some(_), None) => missing += 1,
            (None, Some(_)) => errors += 1,
            _ => {}
        }
    }
    let deductions = rule_decimal(&rule, "errorCode")? * Decimal::from(errors)
        + rule_decimal(&rule, "muchLessGroups")? * Decimal::from(missing);
    let score = full - deductions;
    let accuracy = if expected.is_empty() {
        Decimal::ZERO
    } else {
        Decimal::from(expected.len() as i64 - errors - missing)
            / Decimal::from(expected.len() as i64)
            * Decimal::from(100)
    };
    sqlx::query("UPDATE t_post_telex_pat_train SET status=3,end_time=NOW(3),score=?,error_number=?,accuracy=?,change=0 WHERE id=? AND create_user=? AND attempt=? AND status IN (1,2)").bind(score.to_string()).bind(errors as i32).bind(format!("{accuracy:.2}")).bind(id).bind(user).bind(attempt).execute(&mut **tx).await.map_err(|_|"服务器错误")?;
    Ok(())
}

async fn action(state: AppState, user: AuthUser, body: ActionRequest, action: Action) -> Response {
    let Some(id) = body.id.filter(|v| !v.trim().is_empty()) else {
        return invalid("请求参数错误");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(v) => v,
        Err(e) => return err(e, "action begin"),
    };
    let row=match sqlx::query("SELECT create_user,status,protocol_version,attempt,group_number,type,rule_content,score,CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',deadline) DIV 1000 AS SIGNED) deadline_ms FROM t_post_telex_pat_train WHERE id=? FOR UPDATE").bind(&id).fetch_optional(&mut *tx).await{Ok(Some(v))=>v,Ok(None)=>return business(202,"未查询到训练信息"),Err(e)=>return err(e,"action select")};
    if row.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        return business(207, "无权限");
    };
    let status = row.get::<Option<i32>, _>("status");
    let attempt = row.get::<Option<i32>, _>("attempt").unwrap_or(0);
    if row.get::<Option<i32>, _>("protocol_version") != Some(1) && status != Some(3) {
        return invalid("旧训练缺少原始采集时序，请终止旧训练并重新创建训练");
    };
    if body.attempt != Some(attempt) {
        return business(208, "训练轮次已改变，请重新读取训练，旧轮次不能提交");
    };
    if status == Some(3) && action != Action::Finish {
        return business(208, "训练已完成，不能修改");
    };
    let result:Result<(),&'static str>=match action{Action::Begin if status==Some(0)=>{if validate_rule(row.get::<Option<String>,_>("rule_content").as_deref()).is_err()||frozen_score(row.get("score")).is_err(){Err("训练评分规则快照无效，请新建训练")}else{sqlx::query("UPDATE t_post_telex_pat_train SET status=1,start_time=COALESCE(start_time,NOW(3)),paused_at=NULL,pause_intervals='[]' WHERE id=? AND attempt=?").bind(&id).bind(attempt).execute(&mut *tx).await.map(|_|()).map_err(|_|"服务器错误")}},Action::Begin if matches!(status,Some(1|2))=>Ok(()),Action::Pause if status==Some(1)=>sqlx::query("UPDATE t_post_telex_pat_train SET status=2,paused_at=NOW(3) WHERE id=? AND attempt=?").bind(&id).bind(attempt).execute(&mut *tx).await.map(|_|()).map_err(|_|"服务器错误"),Action::Pause if status==Some(2)=>Ok(()),Action::Resume if status==Some(1)=>Ok(()),Action::Resume if status==Some(2)=>sqlx::query("UPDATE t_post_telex_pat_train SET status=1,deadline=CASE WHEN deadline IS NULL THEN NULL ELSE DATE_ADD(deadline,INTERVAL TIMESTAMPDIFF(MICROSECOND,paused_at,NOW(3)) MICROSECOND) END,paused_at=NULL,pause_intervals=JSON_ARRAY_APPEND(COALESCE(pause_intervals,'[]'),'$',JSON_OBJECT('startedMs',CAST(TIMESTAMPDIFF(MICROSECOND,start_time,paused_at) DIV 1000 AS SIGNED),'endedMs',CAST(TIMESTAMPDIFF(MICROSECOND,start_time,NOW(3)) DIV 1000 AS SIGNED))) WHERE id=? AND attempt=?").bind(&id).bind(attempt).execute(&mut *tx).await.map(|_|()).map_err(|_|"服务器错误"),Action::Finish if status==Some(3)=>Ok(()),Action::Finish if matches!(status,Some(1|2))=>settle(&mut tx,&id,&user.id,attempt).await,Action::Reset if matches!(status,Some(0|1|2))=>{let first=sqlx::query("DELETE FROM t_post_telex_pat_train_page_value WHERE train_id=?").bind(&id).execute(&mut *tx).await;let second=sqlx::query("UPDATE t_post_telex_pat_train_page SET value=NULL WHERE train_id=?").bind(&id).execute(&mut *tx).await;if first.is_err()||second.is_err(){Err("服务器错误")}else{sqlx::query("UPDATE t_post_telex_pat_train SET status=0,attempt=attempt+1,start_time=NULL,end_time=NULL,countdown_seconds=NULL,deadline=NULL,paused_at=NULL,pause_intervals='[]',speed='0',total_speed='0',valid_time=0,speed_log=NULL,valid_time_log=NULL,accuracy='0.00',error_number=0,change=0,deduct_info=NULL,score=? WHERE id=? AND create_user=? AND attempt=?").bind(row.get::<Option<String>,_>("score")).bind(&id).bind(&user.id).bind(attempt).execute(&mut *tx).await.map(|_|()).map_err(|_|"服务器错误")}},_=>Err("训练状态不可执行")};
    if let Err(m) = result {
        return invalid(m);
    }
    if let Err(e) = tx.commit().await {
        return err(e, "action commit");
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(
            json!({"id":id,"status":if action==Action::Finish{3}else{status.unwrap_or(0)},"attempt":if action==Action::Reset{attempt+1}else{attempt}}),
        ),
        "ok",
        "",
    )
}

pub(crate) async fn begin(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<ActionRequest>,
) -> Response {
    action(s, u, b, Action::Begin).await
}
pub(crate) async fn pause(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<ActionRequest>,
) -> Response {
    action(s, u, b, Action::Pause).await
}
pub(crate) async fn resume(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<ActionRequest>,
) -> Response {
    action(s, u, b, Action::Resume).await
}
pub(crate) async fn finish(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<ActionRequest>,
) -> Response {
    action(s, u, b, Action::Finish).await
}
pub(crate) async fn reset(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<ActionRequest>,
) -> Response {
    action(s, u, b, Action::Reset).await
}
pub(crate) async fn delete(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<DeleteQuery>,
) -> Response {
    let Some(id) = q.train_id.filter(|v| !v.is_empty()) else {
        return invalid("训练ID不能为空");
    };
    let Some(pool) = s.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let row = match sqlx::query("SELECT create_user FROM t_post_telex_pat_train WHERE id=? LIMIT 1")
        .bind(&id)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return invalid("未查询到训练"),
        Err(e) => return err(e, "delete-select"),
    };
    if row.get::<Option<String>, _>("create_user").as_deref() != Some(u.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    match sqlx::query("DELETE FROM t_post_telex_pat_train WHERE id=? AND create_user=?")
        .bind(&id)
        .bind(&u.id)
        .execute(pool)
        .await
    {
        Ok(result) if result.rows_affected() == 1 => {
            envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", "")
        }
        Ok(_) => invalid("未查询到训练"),
        Err(e) => err(e, "delete"),
    }
}
