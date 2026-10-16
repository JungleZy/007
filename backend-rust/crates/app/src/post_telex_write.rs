use crate::platform::{
    auth::AuthUser,
    envelope::{envelope_json, envelope_response},
    state::AppState,
};
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rust_decimal::prelude::ToPrimitive;
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
    cable_id: Option<String>,
    #[serde(rename = "startPage")]
    start_page: Option<i32>,
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
    countdown_seconds: Option<i32>,
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
    let description = if code == 207 { "" } else { message };
    envelope_response(StatusCode::OK, code, message, description)
}
fn invalid(message: &'static str) -> Response {
    business(202, message)
}
fn number(value: &Value) -> Result<Decimal, &'static str> {
    let Value::Number(number) = value else {
        return Err("评分规则字段必须为非负数字");
    };
    Decimal::from_str(&number.to_string()).map_err(|_| "评分规则字段必须为非负数字")
}
fn rule_decimal(rule: &Value, key: &str) -> Result<Decimal, &'static str> {
    let value = number(
        rule.get("other")
            .and_then(|v| v.get(key))
            .ok_or("评分规则字段缺失")?,
    )?;
    if value < Decimal::ZERO {
        return Err("评分规则字段不能为负数");
    }
    Ok(value)
}
fn optional_rule_decimal(rule: &Value, parent: &str, key: &str) -> Result<(), &'static str> {
    let Some(value) = rule.get(parent).and_then(|v| v.get(key)) else {
        return Ok(());
    };
    if value.is_null() {
        return Ok(());
    }
    let value = number(value)?;
    if value < Decimal::ZERO {
        return Err("评分规则字段不能为负数");
    }
    Ok(())
}
fn validate_rule(content: Option<&str>) -> Result<Value, &'static str> {
    let rule: Value = serde_json::from_str(content.ok_or("评分规则缺失")?)
        .map_err(|_| "评分规则必须是完整JSON对象")?;
    if !rule.is_object()
        || rule.get("rateUnit").and_then(Value::as_str) != Some("CHARACTERS_PER_MINUTE")
    {
        return Err("评分规则rateUnit必须为CHARACTERS_PER_MINUTE");
    }
    let wpm = rule.get("wpm").ok_or("评分规则wpm必须是完整对象")?;
    let base = number(wpm.get("base").ok_or("评分规则wpm.base缺失")?)?;
    if base <= Decimal::ZERO || !base.fract().is_zero() || base > Decimal::from(i32::MAX) {
        return Err("评分规则wpm.base必须为正整数");
    }
    for key in ["r", "l"] {
        optional_rule_decimal(&rule, "wpm", key)?;
    }
    let other = rule.get("other").ok_or("评分规则other必须是完整对象")?;
    if !other.is_object() {
        return Err("评分规则other必须是完整对象");
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
    for key in ["alterError", "bunchGroup", "lessGap"] {
        optional_rule_decimal(&rule, "other", key)?;
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
async fn generate_page(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    id: &str,
    kind: i32,
    pat_type: Option<i32>,
    page: i32,
    count: usize,
) -> Result<(), sqlx::Error> {
    let keys = super::post_telex::telex_groups(kind, pat_type, count)
        .map_err(|_| sqlx::Error::Protocol("invalid telex generation parameters".into()))?;
    for (sort, key) in keys.into_iter().enumerate() {
        sqlx::query("INSERT INTO t_post_telex_pat_train_page (id,train_id,`key`,page_number,sort) VALUES (?,?,?,?,?)").bind(uuid::Uuid::new_v4().to_string()).bind(id).bind(key).bind(page).bind(sort as i32).execute(&mut **tx).await?;
    }
    Ok(())
}

async fn mutate_children(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    id: &str,
    delete_pages: bool,
) -> Result<(), sqlx::Error> {
    // Existing writers (lazy receipt, capture and settlement included) hold the
    // train PK before their first child read; creation owns its uncommitted PK.
    // Discover without scan locks, then mutate in table/PRIMARY order.
    let page_select = if delete_pages {
        "SELECT id FROM t_post_telex_pat_train_page WHERE train_id=? ORDER BY id"
    } else {
        "SELECT id FROM t_post_telex_pat_train_page WHERE train_id=? AND value IS NOT NULL ORDER BY id"
    };
    for (select, mutation) in [
        (
            page_select,
            if delete_pages {
                "DELETE FROM t_post_telex_pat_train_page WHERE id=? AND train_id=?"
            } else {
                "UPDATE t_post_telex_pat_train_page SET value=NULL WHERE id=? AND train_id=?"
            },
        ),
        (
            "SELECT id FROM t_post_telex_pat_train_page_value WHERE train_id=? ORDER BY id",
            "DELETE FROM t_post_telex_pat_train_page_value WHERE id=? AND train_id=?",
        ),
    ] {
        let children: Vec<String> = sqlx::query_scalar(select)
            .bind(id)
            .fetch_all(&mut **tx)
            .await?;
        for child in children {
            let result = sqlx::query(mutation)
                .bind(child)
                .bind(id)
                .execute(&mut **tx)
                .await?;
            if result.rows_affected() != 1 {
                return Err(sqlx::Error::RowNotFound);
            }
        }
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
    let Some(groups) = body.group_number.filter(|n| *n > 0) else {
        return invalid("组数不能为空");
    };
    if is_cable != 0 && groups < 100 {
        return invalid("报文组数不足一页，无法建立训练");
    }
    let kind = body.train_type;
    if is_cable == 0 && kind.is_none() {
        return invalid("报文类型不能为空");
    }
    if is_cable == 0 && !matches!(kind, Some(0 | 1 | 2)) {
        return invalid("未知数据类型");
    }
    if is_cable == 0 && kind == Some(0) && body.pat_type.is_none() {
        return invalid("拍发类型不能为空");
    }
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
    let result = sqlx::query("INSERT INTO t_post_telex_pat_train (id,name,is_cable,type,train_type,group_number,pat_type,rule_id,rule_content,create_user,status,protocol_version,attempt,score,content,accuracy,speed,total_speed,valid_time,error_number,pause_intervals,`change`,create_time) VALUES (?,?,?,?,?,?,?,?,?,?,0,1,0,?,?,?,?,NULL,?,?,?,0,NOW(6))")
        .bind(&id).bind(body.name).bind(is_cable).bind(kind).bind(body.train_kind).bind(groups).bind(body.pat_type)
        .bind(&rule_id).bind(rule.get::<Option<String>,_>("content")).bind(&user.id).bind(score.to_string())
        .bind(body.content).bind("0.00").bind("0").bind(0i32).bind(0i32).bind("[]").execute(&mut *tx).await;
    if let Err(e) = result {
        return err(e, "add insert");
    }
    if is_cable == 0 {
        let initial = groups.min(200) as usize;
        for offset in (0..initial).step_by(100) {
            if let Err(e) = generate_page(
                &mut tx,
                &id,
                kind.expect("checked report type"),
                body.pat_type,
                (offset / 100 + 1) as i32,
                (initial - offset).min(100),
            )
            .await
            {
                return err(e, "add page");
            }
        }
    } else {
        let floors = match sqlx::query("SELECT floor_number,mores_key FROM t_cable_floor WHERE cable_id=? ORDER BY floor_number,sort")
            .bind(body.cable_id.as_deref()).fetch_all(&mut *tx).await { Ok(v) => v, Err(e) => return err(e, "add cable floor") };
        let mut grouped = std::collections::BTreeMap::<i32, Vec<String>>::new();
        for floor in floors {
            let Some(number) = floor.get::<Option<i32>, _>("floor_number") else {
                return invalid("报底页码缺失");
            };
            let Some(raw) = floor.get::<Option<String>, _>("mores_key") else {
                return invalid("报底内容缺失");
            };
            let keys: Vec<String> = match serde_json::from_str(&raw) {
                Ok(v) => v,
                Err(_) => return invalid("报底内容无效"),
            };
            grouped.entry(number).or_default().push(keys.concat());
        }
        let mut skip = body.start_page.unwrap_or(1).saturating_sub(1).max(0) as usize;
        if skip >= grouped.len() {
            skip = 0;
        }
        let pages = (groups / 100) as usize;
        if grouped.len().saturating_sub(skip) < pages {
            return invalid("所选电缆可用楼层不足");
        }
        for (index, (_, groups)) in grouped.into_iter().skip(skip).take(pages).enumerate() {
            for (sort, key) in groups.into_iter().enumerate() {
                if let Err(e) = sqlx::query("INSERT INTO t_post_telex_pat_train_page (id,train_id,`key`,page_number,sort) VALUES (?,?,?,?,?)")
                    .bind(uuid::Uuid::new_v4().to_string()).bind(&id).bind(key).bind((index+1) as i32).bind(sort as i32).execute(&mut *tx).await { return err(e, "add cable page"); }
            }
        }
    }
    let view = match super::post_telex::train_view(&mut tx, &id, false).await {
        Ok(Some(v)) => v,
        Ok(None) => return invalid("未查询到训练信息"),
        Err(e) => return err(e, "add view"),
    };
    if let Err(e) = tx.commit().await {
        return err(e, "add commit");
    }
    envelope_json(StatusCode::OK, 200, Some(view), "ok", "")
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
async fn derive_capture(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    id: &str,
    attempt: i32,
) -> Result<(), &'static str> {
    let train=sqlx::query("SELECT CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',start_time) DIV 1000 AS SIGNED) start_ms,CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',deadline) DIV 1000 AS SIGNED) deadline_ms,CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',paused_at) DIV 1000 AS SIGNED) paused_ms,pause_intervals,train_type FROM t_post_telex_pat_train WHERE id=? FOR UPDATE").bind(id).fetch_one(&mut **tx).await.map_err(|_|"服务器错误")?;
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
        }
        let received = row
            .get::<Option<i64>, _>("received_ms")
            .ok_or("采集接收时刻缺失")?;
        let mut bound = received - start;
        if let Some(v) = train.get::<Option<i64>, _>("deadline_ms") {
            bound = bound.min(v - start);
        }
        if let Some(v) = train.get::<Option<i64>, _>("paused_ms") {
            bound = bound.min(v - start);
        }
        let millis = duration(&intervals(row.get("capture_intervals"))?, bound)?;
        let text = row
            .get::<Option<String>, _>("pat_value")
            .ok_or("已保存正文缺失")?;
        let characters = super::telex_capture::character_count(
            &text,
            train.get::<Option<i32>, _>("train_type").map(i64::from),
        ) as i64;
        if characters > 0 && millis == 0 {
            return Err("非空正文必须有有效采集时长");
        }
        chars = chars.checked_add(characters).ok_or("正文长度溢出")?;
        total = total.checked_add(millis).ok_or("采集时长溢出")?;
        let page = row
            .get::<Option<i32>, _>("page_number")
            .ok_or("页码不正确")?;
        if page < 1 {
            return Err("页码不正确");
        }
        let page = page as usize;
        times.resize(page, None);
        speeds.resize(page, None);
        times[page - 1] = Some((millis / 1000) as i32);
        speeds[page - 1] = Some(if millis == 0 {
            "0".into()
        } else {
            ((characters * 60000 + millis / 2) / millis).to_string()
        });
    }
    let speed = if total == 0 {
        "0".into()
    } else {
        ((chars * 60000 + total / 2) / total).to_string()
    };
    sqlx::query("UPDATE t_post_telex_pat_train SET speed=?,total_speed=?,valid_time=?,speed_log=?,valid_time_log=? WHERE id=?")
        .bind(&speed).bind(&speed).bind((total/1000) as i32).bind(serde_json::to_string(&speeds).map_err(|_| "服务器错误")?).bind(serde_json::to_string(&times).map_err(|_| "服务器错误")?).bind(id).execute(&mut **tx).await.map_err(|_| "服务器错误")?;
    Ok(())
}
async fn ensure_floor(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    id: &str,
    kind: i32,
    pat_type: Option<i32>,
    total: i32,
) -> Result<(), &'static str> {
    let existing: Vec<i32> = sqlx::query_scalar(
        "SELECT DISTINCT page_number FROM t_post_telex_pat_train_page WHERE train_id=?",
    )
    .bind(id)
    .fetch_all(&mut **tx)
    .await
    .map_err(|_| "服务器错误")?;
    let pages = total / 100 + i32::from(total % 100 > 0);
    for page in 1..=pages {
        if !existing.contains(&page) {
            generate_page(
                tx,
                id,
                kind,
                pat_type,
                page,
                (total - (page - 1) * 100).min(100) as usize,
            )
            .await
            .map_err(|_| "服务器错误")?
        }
    }
    Ok(())
}
#[derive(Default)]
struct ScoreStats {
    error_code: i64,
    correct_mistakes: i64,
    non_standard: i64,
    less_line: i64,
    more_line: i64,
    less_groups: i64,
    more_groups: i64,
    less_code: i64,
    less_return_line: i64,
    less_page: i64,
    error_page: i64,
    more_code: i64,
    lack_code: i64,
    pat_groups: i64,
}

#[derive(Clone)]
struct SourceGroup {
    id: String,
    key: String,
    sort: i32,
}

fn numeric_selector(value: &str, maximum: usize) -> Option<usize> {
    if value.is_empty() || (value.len() > 1 && value.starts_with('0')) {
        return None;
    }
    value
        .parse::<usize>()
        .ok()
        .filter(|number| (1..=maximum).contains(number))
}

fn normalize_submission(text: &str, kind: i32, stats: &mut ScoreStats) -> Vec<Vec<String>> {
    let mut pages = Vec::new();
    for raw_line in text.split_inclusive(['\r', '\n']) {
        let mut tokens: Vec<String> = raw_line.split_whitespace().map(str::to_owned).collect();
        if tokens.is_empty() {
            continue;
        }
        let mut normalized = Vec::with_capacity(tokens.len());
        let mut index = 0;
        while index < tokens.len() {
            if tokens[index].chars().count() >= 8
                && !tokens[index].contains('/')
                && !tokens[index].contains('-')
            {
                let chars: Vec<char> = tokens[index].chars().collect();
                for chunk in chars.chunks(4) {
                    normalized.push(chunk.iter().collect());
                }
                stats.non_standard += chars.len().saturating_sub(1) as i64 / 4;
                index += 1;
                continue;
            }
            if index + 1 < tokens.len()
                && tokens[index].chars().count() == 3
                && tokens[index + 1].chars().count() == 5
            {
                let next = tokens[index + 1].clone();
                let mut chars: Vec<char> = tokens[index].chars().collect();
                chars.push(next.chars().next().unwrap_or_default());
                tokens[index + 1] = next.chars().skip(1).collect();
                normalized.push(chars.into_iter().collect());
                stats.non_standard += 1;
                index += 1;
                continue;
            }
            if index + 1 < tokens.len()
                && tokens[index].chars().count() == 5
                && tokens[index + 1].chars().count() == 3
            {
                let mut current: Vec<char> = tokens[index].chars().collect();
                let last = current.pop().unwrap_or_default();
                tokens[index + 1] = std::iter::once(last)
                    .chain(tokens[index + 1].chars())
                    .collect();
                normalized.push(current.into_iter().collect());
                stats.non_standard += 1;
                index += 1;
                continue;
            }
            normalized.push(tokens[index].clone());
            index += 1;
        }
        stats.pat_groups += normalized.len() as i64;
        if kind != 4 && raw_line.ends_with(['\r', '\n']) {
            stats.pat_groups += 1;
        }
        if kind != 4 {
            let mut corrected = Vec::with_capacity(normalized.len());
            let mut index = 0;
            while index < normalized.len() {
                let token = &normalized[index];
                if token == "QTA" {
                    if let Some(position) = normalized
                        .get(index + 1)
                        .and_then(|v| numeric_selector(v, 100))
                    {
                        if position <= corrected.len() {
                            corrected.remove(position - 1);
                            stats.correct_mistakes += 1;
                            index += 2;
                            continue;
                        }
                    }
                }
                if token == "ADD" {
                    if let (Some(position), Some(value)) = (
                        normalized
                            .get(index + 1)
                            .and_then(|v| numeric_selector(v, 100)),
                        normalized.get(index + 2),
                    ) {
                        let position = position.saturating_sub(1).min(corrected.len());
                        corrected.insert(position, value.clone());
                        stats.correct_mistakes += 1;
                        index += 3;
                        continue;
                    }
                }
                if token.contains('/') {
                    if let Some((_, replacement)) = token.rsplit_once('/') {
                        if !replacement.is_empty() {
                            corrected.push(replacement.to_owned());
                            stats.correct_mistakes += 1;
                            index += 1;
                            continue;
                        }
                    }
                    if (token.chars().all(|c| c == '/') || token.ends_with('/'))
                        && normalized.get(index + 1).is_some()
                    {
                        if !corrected.is_empty() {
                            corrected.pop();
                        }
                        corrected.push(normalized[index + 1].clone());
                        stats.correct_mistakes += 1;
                        index += 2;
                        continue;
                    }
                    stats.non_standard += 1;
                }
                if let Some(position) = numeric_selector(token, 10) {
                    if let Some(value) = normalized.get(index + 1) {
                        if position <= corrected.len() {
                            corrected[position - 1] = value.clone();
                            stats.correct_mistakes += 1;
                            index += 2;
                            continue;
                        }
                        stats.non_standard += 1;
                    }
                }
                corrected.push(token.clone());
                index += 1;
            }
            tokens = corrected;
        } else {
            tokens = normalized;
        }
        pages.push(tokens);
    }
    pages
}

fn score_pages(
    source: &std::collections::BTreeMap<i32, Vec<SourceGroup>>,
    submitted: &std::collections::BTreeMap<i32, String>,
    kind: i32,
) -> (ScoreStats, Vec<(String, String)>) {
    let mut stats = ScoreStats::default();
    let mut updates = Vec::new();
    for (page, expected) in source {
        let previous_groups = stats.pat_groups;
        let mut actual = submitted
            .get(page)
            .map(|text| normalize_submission(text, kind, &mut stats))
            .unwrap_or_default();
        let attempted_groups = stats.pat_groups - previous_groups;
        let mut row_index = 0;
        while row_index < actual.len() {
            let expected_index = (row_index + 1) * 10;
            if actual[row_index].len() > 10 && expected_index < expected.len() {
                let expected_key = expected[expected_index]
                    .key
                    .split('-')
                    .next()
                    .unwrap_or(&expected[expected_index].key);
                if let Some(split_at) =
                    actual[row_index]
                        .iter()
                        .enumerate()
                        .skip(10)
                        .find_map(|(index, value)| {
                            (value.split('-').next() == Some(expected_key)).then_some(index)
                        })
                {
                    let remainder = actual[row_index].split_off(split_at);
                    actual.insert(row_index + 1, remainder);
                    stats.less_return_line += 1;
                }
            }
            row_index += 1;
        }
        let mut offset = 0usize;
        let expected_rows = expected.len().div_ceil(10);
        for row in 0..expected_rows {
            let end = (offset + 10).min(expected.len());
            let expected_row = &expected[offset..end];
            let Some(actual_row) = actual.get(row) else {
                stats.less_line += 1;
                updates.extend(
                    expected_row
                        .iter()
                        .map(|group| (group.id.clone(), String::new())),
                );
                offset = end;
                continue;
            };
            for (column, group) in expected_row.iter().enumerate() {
                let mut value = actual_row.get(column).cloned().unwrap_or_default();
                if value.is_empty() {
                    stats.less_groups += 1;
                } else {
                    let base = group.key.split('-').next().unwrap_or(&group.key);
                    let expected_value = if end == expected.len()
                        && column + 1 == expected_row.len()
                        && expected.len() >= 100
                    {
                        format!("{base}-{page}")
                    } else {
                        base.to_owned()
                    };
                    if value == expected_value {
                        // exact
                    } else if expected_value.contains('-') && value == base {
                        if kind == 4 {
                            stats.less_page += 1;
                        }
                    } else if expected_value.contains('-') && value.split('-').next() == Some(base)
                    {
                        if kind == 4
                            && value
                                .rsplit('-')
                                .next()
                                .and_then(|marker| marker.parse::<i32>().ok())
                                != Some(*page)
                        {
                            stats.error_page += 1;
                        }
                    } else if value.len() > 4
                        && value.split('-').next().unwrap_or(&value).contains(base)
                    {
                        stats.less_code += 1;
                        stats.more_code += value.len().saturating_sub(4) as i64;
                    } else if value.len() < 4 && base.contains(&value) {
                        stats.less_code += 1;
                        stats.lack_code += 4i64.saturating_sub(value.len() as i64);
                    } else {
                        stats.error_code += 1;
                    }
                }
                if kind != 4 {
                    let base = group.key.split('-').next().unwrap_or(&group.key);
                    if let Some((body, _)) = value.split_once('-') {
                        if body == base {
                            value.truncate(body.len());
                        }
                    }
                }
                updates.push((group.id.clone(), value));
            }
            if actual_row.len() > expected_row.len() {
                stats.more_groups += (actual_row.len() - expected_row.len()) as i64;
            }
            offset = end;
        }
        if actual.len() > expected_rows {
            stats.more_line += (actual.len() - expected_rows) as i64;
        }
        if (kind == 4 && expected.len() >= 100) || (kind != 4 && attempted_groups >= 100) {
            let last = actual.iter().flat_map(|row| row.iter()).last();
            match last.and_then(|value| value.rsplit_once('-')) {
                Some((_, marker)) if marker == page.to_string() => {}
                Some(_) => stats.error_page += 1,
                None => stats.less_page += 1,
            }
        }
    }
    (stats, updates)
}

fn score_accuracy(groups: i64, errors: i64, kind: i32) -> Decimal {
    if groups <= 0 {
        return Decimal::ZERO;
    }
    let ratio = Decimal::from((groups - errors).max(0)) / Decimal::from(groups);
    let ratio = if kind == 4 {
        ratio
    } else {
        ratio.round_dp_with_strategy(2, rust_decimal::RoundingStrategy::MidpointAwayFromZero)
    };
    ratio * Decimal::from(100)
}

fn decimal_text(value: Decimal) -> String {
    value.normalize().to_string()
}

fn deduction_text(value: Decimal) -> String {
    format!("-{}", decimal_text(value))
}

async fn settle(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    id: &str,
    user: &str,
    attempt: i32,
) -> Result<(), &'static str> {
    let train=sqlx::query("SELECT group_number,is_cable,type,train_type,pat_type,score,rule_content FROM t_post_telex_pat_train WHERE id=? AND create_user=? FOR UPDATE").bind(id).bind(user).fetch_one(&mut **tx).await.map_err(|_|"未查询到训练信息")?;
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
            train.get::<Option<i32>, _>("pat_type"),
            total,
        )
        .await?
    }
    derive_capture(tx, id, attempt).await?;
    let speed_text: String =
        sqlx::query_scalar("SELECT COALESCE(speed,'0') FROM t_post_telex_pat_train WHERE id=?")
            .bind(id)
            .fetch_one(&mut **tx)
            .await
            .map_err(|_| "服务器错误")?;
    let speed = Decimal::from_str(&speed_text).map_err(|_| "速率无效")?;
    let kind = train
        .get::<Option<i32>, _>("train_type")
        .ok_or("训练类型缺失，无法结算")?;
    let source_rows = sqlx::query(
        "SELECT id,page_number,sort,`key` FROM t_post_telex_pat_train_page WHERE train_id=? ORDER BY page_number,sort",
    )
    .bind(id)
    .fetch_all(&mut **tx)
    .await
    .map_err(|_| "服务器错误")?;
    let mut source = std::collections::BTreeMap::<i32, Vec<SourceGroup>>::new();
    for row in source_rows {
        let page = row
            .get::<Option<i32>, _>("page_number")
            .ok_or("报底页码缺失")?;
        let key = row.get::<Option<String>, _>("key").ok_or("报底内容缺失")?;
        source.entry(page).or_default().push(SourceGroup {
            id: row.get::<String, _>("id"),
            key,
            sort: row.get::<Option<i32>, _>("sort").ok_or("报底排序缺失")?,
        });
    }
    let value_rows = sqlx::query(
        "SELECT page_number,pat_value,attempt FROM t_post_telex_pat_train_page_value WHERE train_id=? ORDER BY page_number",
    )
    .bind(id)
    .fetch_all(&mut **tx)
    .await
    .map_err(|_| "服务器错误")?;
    let mut submitted = std::collections::BTreeMap::<i32, String>::new();
    for row in value_rows {
        if row.get::<Option<i32>, _>("attempt") != Some(attempt) {
            return Err("训练轮次已改变");
        }
        let page = row
            .get::<Option<i32>, _>("page_number")
            .ok_or("页码不正确")?;
        // Java's page DAO consumes the first page value; duplicate rows are
        // malformed data, but keeping the first is deterministic and avoids
        // scoring the same page twice.
        submitted.entry(page).or_insert_with(|| {
            row.get::<Option<String>, _>("pat_value")
                .unwrap_or_default()
        });
    }
    let (stats, mut page_updates) = score_pages(&source, &submitted, kind);
    page_updates.sort_unstable_by(|left, right| left.0.cmp(&right.0));
    if kind == 4 {
        for (page_id, value) in page_updates {
            sqlx::query("UPDATE t_post_telex_pat_train_page SET value=? WHERE id=? AND train_id=?")
                .bind(value)
                .bind(page_id)
                .bind(id)
                .execute(&mut **tx)
                .await
                .map_err(|_| "服务器错误")?;
        }
    } else {
        // Java rebuilds telex source rows with fresh identities, unlike type 4.
        // The train PK protects replacement, lazy generation and exact retries.
        for (page_id, _) in &page_updates {
            let removed =
                sqlx::query("DELETE FROM t_post_telex_pat_train_page WHERE id=? AND train_id=?")
                    .bind(page_id)
                    .bind(id)
                    .execute(&mut **tx)
                    .await
                    .map_err(|_| "服务器错误")?;
            if removed.rows_affected() != 1 {
                return Err("服务器错误");
            }
        }
        for (page, groups) in source {
            for group in groups {
                let index = page_updates
                    .binary_search_by(|(page_id, _)| page_id.cmp(&group.id))
                    .map_err(|_| "服务器错误")?;
                sqlx::query("INSERT INTO t_post_telex_pat_train_page (id,train_id,`key`,value,page_number,sort) VALUES (?,?,?,?,?,?)")
                    .bind(uuid::Uuid::new_v4().to_string())
                    .bind(id)
                    .bind(group.key)
                    .bind(&page_updates[index].1)
                    .bind(page)
                    .bind(group.sort)
                    .execute(&mut **tx)
                    .await
                    .map_err(|_| "服务器错误")?;
            }
        }
    }
    let base = number(
        rule.get("wpm")
            .and_then(|value| value.get("base"))
            .ok_or("评分规则wpm.base缺失")?,
    )?
    .to_i64()
    .ok_or("评分规则wpm.base无效")?;
    let high = rule
        .get("wpm")
        .and_then(|value| value.get("r"))
        .filter(|value| !value.is_null())
        .map(number)
        .transpose()?
        .unwrap_or(Decimal::ZERO);
    let low = rule
        .get("wpm")
        .and_then(|value| value.get("l"))
        .filter(|value| !value.is_null())
        .map(number)
        .transpose()?
        .unwrap_or(Decimal::ZERO);
    let speed_adjustment = if speed > Decimal::from(base) {
        high * (speed - Decimal::from(base))
    } else if speed < Decimal::from(base) {
        low * (speed - Decimal::from(base))
    } else {
        Decimal::ZERO
    };
    let penalty = |name: &str, count: i64| -> Result<Decimal, &'static str> {
        Ok(rule_decimal(&rule, name)? * Decimal::from(count))
    };
    let error_penalty = penalty("errorCode", stats.error_code)?;
    let less_line_penalty = penalty("muchLessLine", stats.less_line + stats.more_line)?;
    let less_groups_penalty = penalty("muchLessGroups", stats.less_groups + stats.more_groups)?;
    let less_code_penalty = penalty("muchLessCode", stats.less_code)?;
    let return_line_penalty = penalty("lessReturnLine", stats.less_return_line)?;
    let less_page_penalty = penalty("lessPage", stats.less_page)?;
    let error_page_penalty = penalty("errorPage", stats.error_page)?;
    let non_standard_penalty = penalty("nonStandart", stats.non_standard)?;
    let correction_rate = if kind != 4 {
        rule.get("other")
            .and_then(|other| other.get("alterError"))
            .filter(|value| !value.is_null())
            .map(number)
            .transpose()?
            .unwrap_or(rule_decimal(&rule, "correctMistakes")?)
    } else {
        rule_decimal(&rule, "correctMistakes")?
    };
    let correction_penalty = correction_rate * Decimal::from(stats.correct_mistakes);
    let score = full
        - error_penalty
        - less_line_penalty
        - less_groups_penalty
        - less_code_penalty
        - return_line_penalty
        - less_page_penalty
        - error_page_penalty
        - non_standard_penalty
        - correction_penalty
        + speed_adjustment;
    let error_number = if kind != 4 {
        stats.error_code + stats.less_code
    } else {
        stats.error_code
    };
    let accuracy = score_accuracy(stats.pat_groups, error_number, kind);
    let mut deduct = serde_json::Map::new();
    let mut add_deduction = |number_key: &str, score_key: &str, count: i64, value: Decimal| {
        deduct.insert(number_key.to_owned(), json!(count));
        deduct.insert(score_key.to_owned(), Value::String(deduction_text(value)));
    };
    if kind != 4 {
        add_deduction(
            "errorCodeNumber",
            "errorCodeScore",
            stats.error_code,
            error_penalty,
        );
        add_deduction(
            "muchLessLineNumber",
            "muchLessLineScore",
            stats.less_line + stats.more_line,
            less_line_penalty,
        );
        add_deduction(
            "muchLessGroupsNumber",
            "muchLessGroupsScore",
            stats.less_groups + stats.more_groups,
            less_groups_penalty,
        );
        add_deduction(
            "muchLessCodeNumber",
            "muchLessCodeScore",
            stats.less_code,
            less_code_penalty,
        );
        add_deduction(
            "lessReturnLineNumber",
            "lessReturnLineScore",
            stats.less_return_line,
            return_line_penalty,
        );
        add_deduction(
            "lessPageNumber",
            "lessPageScore",
            stats.less_page,
            less_page_penalty,
        );
        add_deduction(
            "errorPageNumber",
            "errorPageScore",
            stats.error_page,
            error_page_penalty,
        );
        add_deduction(
            "nonStandartNumber",
            "nonStandartScore",
            stats.non_standard,
            non_standard_penalty,
        );
        add_deduction(
            "correctMistakesNumber",
            "correctMistakesScore",
            stats.correct_mistakes,
            correction_penalty,
        );
        if speed > Decimal::from(base) {
            deduct.insert(
                "speedScore".into(),
                Value::String(format!("+{}", decimal_text(speed_adjustment))),
            );
        } else if speed < Decimal::from(base) {
            deduct.insert(
                "speedScore".into(),
                Value::String(format!("-{}", decimal_text(speed_adjustment.abs()))),
            );
        }
    } else {
        add_deduction(
            "lackLineNumber",
            "lackLineScore",
            stats.less_line,
            penalty("muchLessLine", stats.less_line)?,
        );
        add_deduction(
            "moreLineNumber",
            "moreLineScore",
            stats.more_line,
            penalty("muchLessLine", stats.more_line)?,
        );
        add_deduction(
            "moreGroupNumber",
            "moreGroupScore",
            stats.more_groups,
            penalty("muchLessGroups", stats.more_groups)?,
        );
        add_deduction(
            "lackReturnLineNumber",
            "lackReturnLineScore",
            stats.less_return_line,
            return_line_penalty,
        );
        add_deduction(
            "lackPageMarkNumber",
            "lackPageMarkScore",
            stats.less_page,
            less_page_penalty,
        );
        add_deduction(
            "pageMarkErrorNumber",
            "pageMarkErrorScore",
            stats.error_page,
            error_page_penalty,
        );
        add_deduction(
            "errorGroupNumber",
            "errorGroupScore",
            stats.error_code,
            error_penalty,
        );
        add_deduction(
            "moreCodeNumber",
            "moreCodeScore",
            stats.more_code,
            penalty("muchLessCode", stats.more_code)?,
        );
        add_deduction(
            "lackCodeNumber",
            "lackCodeScore",
            stats.lack_code,
            penalty("muchLessCode", stats.lack_code)?,
        );
        add_deduction(
            "lackGroupNumber",
            "lackGroupScore",
            stats.less_groups,
            penalty("muchLessGroups", stats.less_groups)?,
        );
        deduct.insert("nonStandartNumber".into(), json!(stats.non_standard));
        deduct.insert(
            "nonStandartScor".into(),
            Value::String(deduction_text(non_standard_penalty)),
        );
        deduct.insert("updateErrorNumber".into(), json!(stats.correct_mistakes));
        deduct.insert(
            "updateErrorScore".into(),
            Value::String(deduction_text(correction_penalty)),
        );
        let difference = Decimal::from(base) - speed;
        if difference > Decimal::ZERO {
            deduct.insert(
                "speedLowNumber".into(),
                Value::String(decimal_text(difference.abs())),
            );
            deduct.insert(
                "speedLowScore".into(),
                Value::String(decimal_text(speed_adjustment.abs())),
            );
            deduct.insert("speedOverTopNumber".into(), Value::String("0".into()));
            deduct.insert("speedOverTopScore".into(), Value::String("0".into()));
        } else {
            deduct.insert(
                "speedOverTopNumber".into(),
                Value::String(decimal_text(difference.abs())),
            );
            deduct.insert(
                "speedOverTopScore".into(),
                Value::String(decimal_text(speed_adjustment)),
            );
            deduct.insert("speedLowNumber".into(), Value::String("0".into()));
            deduct.insert("speedLowScore".into(), Value::String("0".into()));
        }
    }
    let deduct_info = Value::Object(deduct).to_string();
    sqlx::query("UPDATE t_post_telex_pat_train SET status=3,end_time=NOW(3),score=?,error_number=?,accuracy=?,`change`=?,deduct_info=? WHERE id=? AND create_user=? AND attempt=? AND status IN (1,2)")
        .bind(decimal_text(score))
        .bind(error_number as i32)
        .bind(format!("{accuracy:.2}"))
        .bind((stats.correct_mistakes + stats.non_standard) as i32)
        .bind(deduct_info)
        .bind(id)
        .bind(user)
        .bind(attempt)
        .execute(&mut **tx)
        .await
        .map_err(|error| {
            tracing::warn!(%error, operation = "settle post telex", train_id = id, "post telex settlement failed");
            "服务器错误"
        })?;
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
    let row=match sqlx::query("SELECT id,create_user,status,protocol_version,attempt,group_number,type,rule_content,score,countdown_seconds,pause_intervals,CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',start_time) DIV 1000 AS SIGNED) start_ms,CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',deadline) DIV 1000 AS SIGNED) deadline_ms,CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',paused_at) DIV 1000 AS SIGNED) paused_ms FROM t_post_telex_pat_train WHERE id=? FOR UPDATE").bind(&id).fetch_optional(&mut *tx).await{Ok(Some(v))=>v,Ok(None)=>return business(202,"未查询到训练信息"),Err(e)=>return err(e,"action select")};
    if row.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        return business(207, "无权限");
    };
    let id = row.get::<String, _>("id");
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
    if action == Action::Begin
        && body
            .countdown_seconds
            .is_some_and(|seconds| !(1..=86_400).contains(&seconds))
    {
        return invalid("倒计时须为1至86400秒或不启用");
    }
    if action == Action::Begin
        && status != Some(0)
        && body.countdown_seconds != row.get::<Option<i32>, _>("countdown_seconds")
    {
        return business(208, "训练开始后不能修改倒计时");
    }
    if action == Action::Pause && status == Some(1) {
        let expired: i32 = match sqlx::query_scalar("SELECT IF(deadline IS NOT NULL AND deadline<=NOW(3),1,0) FROM t_post_telex_pat_train WHERE id=?")
            .bind(&id).fetch_one(&mut *tx).await { Ok(v) => v, Err(e) => return err(e, "pause clock") };
        if expired == 1 {
            return business(208, "倒计时已结束，仅可补交截止前的内容");
        }
    }
    let result: Result<(), &'static str> = match action {
        Action::Begin if status == Some(0) => {
            if validate_rule(row.get::<Option<String>, _>("rule_content").as_deref()).is_err()
                || frozen_score(row.get("score")).is_err()
            {
                Err("训练评分规则快照无效，请新建训练")
            } else if let Some(seconds) = body.countdown_seconds {
                if !(1..=86_400).contains(&seconds) {
                    Err("倒计时须为1至86400秒或不启用")
                } else {
                    sqlx::query("UPDATE t_post_telex_pat_train SET status=1,start_time=NOW(3),countdown_seconds=?,deadline=DATE_ADD(NOW(3),INTERVAL ? SECOND),paused_at=NULL,pause_intervals='[]' WHERE id=? AND attempt=?").bind(seconds).bind(seconds).bind(&id).bind(attempt).execute(&mut *tx).await.map(|_|()).map_err(|_|"服务器错误")
                }
            } else {
                sqlx::query("UPDATE t_post_telex_pat_train SET status=1,start_time=NOW(3),countdown_seconds=NULL,deadline=NULL,paused_at=NULL,pause_intervals='[]' WHERE id=? AND attempt=?").bind(&id).bind(attempt).execute(&mut *tx).await.map(|_|()).map_err(|_|"服务器错误")
            }
        }
        Action::Begin if matches!(status, Some(1 | 2)) => Ok(()),
        Action::Pause if status == Some(1) => sqlx::query(
            "UPDATE t_post_telex_pat_train SET status=2,paused_at=NOW(3) WHERE id=? AND attempt=?",
        )
        .bind(&id)
        .bind(attempt)
        .execute(&mut *tx)
        .await
        .map(|_| ())
        .map_err(|_| "服务器错误"),
        Action::Pause if status == Some(2) => Ok(()),
        Action::Resume if status == Some(1) => Ok(()),
        Action::Resume if status == Some(2) => {
            if row.get::<Option<i64>, _>("paused_ms").is_none() {
                Err("暂停时刻缺失")
            } else if row.get::<Option<i64>, _>("start_ms").is_none() {
                Err("训练开始时刻缺失")
            } else if intervals(row.get("pause_intervals")).is_err() {
                Err("暂停时间轴无效")
            } else {
                sqlx::query("UPDATE t_post_telex_pat_train SET status=1,deadline=CASE WHEN deadline IS NULL THEN NULL ELSE DATE_ADD(deadline,INTERVAL TIMESTAMPDIFF(MICROSECOND,paused_at,NOW(3)) MICROSECOND) END,paused_at=NULL,pause_intervals=CASE WHEN TIMESTAMPDIFF(MICROSECOND,paused_at,NOW(3))>0 THEN JSON_ARRAY_APPEND(COALESCE(pause_intervals,'[]'),'$',JSON_OBJECT('startedMs',CAST(TIMESTAMPDIFF(MICROSECOND,start_time,paused_at) DIV 1000 AS SIGNED),'endedMs',CAST(TIMESTAMPDIFF(MICROSECOND,start_time,NOW(3)) DIV 1000 AS SIGNED))) ELSE COALESCE(pause_intervals,'[]') END WHERE id=? AND attempt=?").bind(&id).bind(attempt).execute(&mut *tx).await.map(|_|()).map_err(|_|"服务器错误")
            }
        }
        Action::Finish if status == Some(3) => Ok(()),
        Action::Finish if matches!(status, Some(1 | 2)) => {
            settle(&mut tx, &id, &user.id, attempt).await
        }
        Action::Reset if matches!(status, Some(0 | 1 | 2)) => {
            match mutate_children(&mut tx, &id, false).await {
                Err(e) => { tracing::warn!(error=%e, operation="reset telex children", train_id=%id, "post telex reset failed"); Err("服务器错误") }
                Ok(()) => sqlx::query("UPDATE t_post_telex_pat_train SET status=0,attempt=attempt+1,start_time=NULL,end_time=NULL,countdown_seconds=NULL,deadline=NULL,paused_at=NULL,pause_intervals='[]',speed='0',total_speed='0',valid_time=0,speed_log=NULL,valid_time_log=NULL,accuracy='0.00',error_number=0,`change`=0,deduct_info=NULL,score=? WHERE id=? AND create_user=? AND attempt=?").bind(row.get::<Option<String>,_>("score")).bind(&id).bind(&user.id).bind(attempt).execute(&mut *tx).await.map(|_|()).map_err(|e| { tracing::warn!(error=%e, operation="reset telex train", train_id=%id, "post telex reset failed"); "服务器错误" }),
            }
        }
        _ => Err("训练状态不可执行"),
    };
    if let Err(m) = result {
        if m == "服务器错误" {
            tracing::warn!(operation = "post telex action database operation", train_id = %id, "post telex action failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
        return invalid(m);
    }
    let view = match super::post_telex::train_view(&mut tx, &id, false).await {
        Ok(Some(view)) => view,
        Ok(None) => return business(202, "未查询到训练信息"),
        Err(e) => return err(e, "action view"),
    };
    if let Err(e) = tx.commit().await {
        return err(e, "action commit");
    }
    envelope_json(StatusCode::OK, 200, Some(view), "ok", "")
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
        return invalid("未查询到训练信息");
    };
    let Some(pool) = s.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return err(e, "delete-begin"),
    };
    let row = match sqlx::query(
        "SELECT id,create_user FROM t_post_telex_pat_train WHERE id=? LIMIT 1 FOR UPDATE",
    )
    .bind(&id)
    .fetch_optional(&mut *tx)
    .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return invalid("未查询到训练信息"),
        Err(e) => return err(e, "delete-select"),
    };
    if row.get::<Option<String>, _>("create_user").as_deref() != Some(u.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let id = row.get::<String, _>("id");
    if let Err(e) = mutate_children(&mut tx, &id, true).await {
        return err(e, "delete-children");
    }
    let result =
        match sqlx::query("DELETE FROM t_post_telex_pat_train WHERE id=? AND create_user=?")
            .bind(&id)
            .bind(&u.id)
            .execute(&mut *tx)
            .await
        {
            Ok(result) => result,
            Err(e) => return err(e, "delete"),
        };
    if result.rows_affected() != 1 {
        return invalid("未查询到训练信息");
    }
    if let Err(e) = tx.commit().await {
        return err(e, "delete-commit");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", "")
}

#[cfg(test)]
#[path = "post_telex_writer_tests.rs"]
mod writer_tests;
