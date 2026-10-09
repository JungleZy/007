use super::{envelope_json, envelope_response, post_telegraph, AppState, AuthUser};
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rand::{seq::SliceRandom, Rng};
use rust_decimal::{Decimal, RoundingStrategy};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, Row};
use std::{
    collections::{BTreeMap, BTreeSet},
    str::FromStr,
};

#[derive(Deserialize)]
pub(crate) struct AddRequest {
    title: Option<String>,
    #[serde(rename = "isCable")]
    is_cable: Option<i32>,
    #[serde(rename = "cableId")]
    cable_id: Option<String>,
    #[serde(rename = "startPage")]
    start_page: Option<i32>,
    #[serde(rename = "totalNumber")]
    total_number: Option<i32>,
    #[serde(rename = "messageType")]
    message_type: Option<i32>,
    #[serde(rename = "ruleId")]
    rule_id: Option<String>,
}

#[derive(Deserialize)]
pub(crate) struct ActionRequest {
    pub id: Option<String>,
    #[serde(rename = "protocolVersion")]
    pub protocol_version: Option<i32>,
    pub attempt: Option<i32>,
}
#[derive(Deserialize)]
pub(crate) struct DeleteQuery {
    #[serde(rename = "trainId")]
    pub train_id: Option<String>,
}

#[derive(Clone, Deserialize, Serialize, PartialEq)]
pub(crate) struct CaptureInterval {
    #[serde(rename = "startedMs")]
    started_ms: i64,
    #[serde(rename = "endedMs")]
    ended_ms: i64,
}

#[derive(Clone, Deserialize)]
pub(crate) struct PageMessage {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
    #[serde(rename = "pageNumber")]
    page_number: Option<i32>,
    key: Option<String>,
    value: Option<String>,
    time: Option<String>,
    sort: Option<i32>,
}

#[derive(Deserialize)]
pub(crate) struct FinishPageRequest {
    pub id: Option<String>,
    #[serde(rename = "pageNumber")]
    pub page_number: Option<i32>,
    #[serde(rename = "protocolVersion")]
    pub protocol_version: Option<i32>,
    pub attempt: Option<i32>,
    pub value: Option<Vec<PageMessage>>,
    #[serde(rename = "captureIntervals")]
    pub capture_intervals: Option<Vec<CaptureInterval>>,
}

fn err(e: sqlx::Error, op: &'static str) -> Response {
    tracing::warn!(error=%e, operation=op, "post telegraph write failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

fn business(code: i32, message: &'static str) -> Response {
    let status = if code == 500 {
        StatusCode::INTERNAL_SERVER_ERROR
    } else {
        StatusCode::OK
    };
    envelope_response(
        status,
        code,
        message,
        if matches!(code, 202 | 208) {
            message
        } else {
            ""
        },
    )
}
fn generated_groups(message_type: i32, count: usize) -> Vec<String> {
    let alphabet: &[u8] = match message_type {
        0 => b"0123456789",
        1 => b"ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        _ => b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
    };
    let mut rng = rand::thread_rng();
    let mut groups = Vec::with_capacity(count);
    for _ in 0..count {
        let group = loop {
            let candidate = if message_type == 2 {
                let target_digits = match rng.gen_range(0..100) {
                    0..50 => 2,
                    50..75 => 3,
                    _ => 1,
                };
                let mut positions = [0usize, 1, 2, 3];
                positions.shuffle(&mut rng);
                let digit_positions = &positions[..target_digits];
                let mut used = Vec::with_capacity(4);
                for position in 0..4 {
                    let source: &[u8] = if digit_positions.contains(&position) {
                        b"0123456789"
                    } else {
                        b"ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    };
                    loop {
                        let value = source[rng.gen_range(0..source.len())];
                        if !used.contains(&value) {
                            used.push(value);
                            break;
                        }
                    }
                }
                String::from_utf8(used).expect("report alphabet is ASCII")
            } else {
                alphabet
                    .choose_multiple(&mut rng, 4)
                    .map(|value| char::from(*value))
                    .collect()
            };
            if !groups
                .iter()
                .rev()
                .take(10)
                .any(|value| value == &candidate)
            {
                break candidate;
            }
        };
        groups.push(group);
    }
    groups
}

fn parse_array(value: &str) -> Result<Vec<Value>, ()> {
    serde_json::from_str(value).map_err(|_| ())
}

fn canonical_messages(
    dto: &FinishPageRequest,
    train_id: &str,
    page_number: i32,
) -> Result<Vec<Value>, &'static str> {
    let values = dto.value.as_ref().ok_or("原始页内容不能为空")?;
    let mut output = Vec::with_capacity(values.len());
    let mut previous_sort = -1;
    for item in values {
        let sort = item.sort.ok_or("原始组必须有序且属于当前训练页")?;
        if sort <= previous_sort
            || item
                .train_id
                .as_deref()
                .is_some_and(|id| Some(id) != dto.id.as_deref())
            || item.page_number.is_some_and(|page| page != page_number)
        {
            return Err("原始组必须有序且属于当前训练页");
        }
        let key = item.key.as_deref().ok_or("原始组字段不能为空")?;
        let value = item.value.as_deref().ok_or("原始组字段不能为空")?;
        let time = item.time.as_deref().ok_or("原始组字段不能为空")?;
        let body = string_array(value)?;
        let times = string_array(time)?;
        string_array(key)?;
        if body.len() != times.len() {
            return Err("原始字符与事件时长数量不一致");
        }
        for elapsed in times {
            if elapsed.parse::<i64>().map_err(|_| "原始事件时长无效")? < 0 {
                return Err("原始事件时长不能为负");
            }
        }
        output.push(json!({"id": null, "trainId": train_id, "pageNumber": page_number, "key": key, "value": value, "time": time, "sort": sort}));
        previous_sort = sort;
    }
    Ok(output)
}

fn same_raw_messages(
    stored: &str,
    incoming: &[Value],
    train_id: &str,
) -> Result<bool, serde_json::Error> {
    let stored: Vec<Value> = serde_json::from_str(stored)?;
    Ok(stored.len() == incoming.len()
        && stored.iter().zip(incoming).all(|(stored, incoming)| {
            let (Some(stored), Some(incoming)) = (stored.as_object(), incoming.as_object()) else {
                return false;
            };
            stored.len() == incoming.len()
                && stored.iter().all(|(field, value)| {
                    if field == "trainId" {
                        value
                            .as_str()
                            .is_some_and(|id| id.eq_ignore_ascii_case(train_id))
                            && incoming.get(field).and_then(Value::as_str) == Some(train_id)
                    } else {
                        incoming.get(field) == Some(value)
                    }
                })
        }))
}

fn valid_intervals(intervals: &[CaptureInterval], maximum_end: i64) -> Result<i64, &'static str> {
    if maximum_end < 0 {
        return Err("采集时间轴缺失或训练尚未开始");
    }
    let mut previous_end = 0_i64;
    let mut duration = 0_i64;
    for interval in intervals {
        if interval.started_ms < previous_end
            || interval.ended_ms <= interval.started_ms
            || interval.ended_ms > maximum_end
        {
            return Err("采集区间必须有序、非负且位于训练有效时间内");
        }
        duration = duration
            .checked_add(interval.ended_ms - interval.started_ms)
            .ok_or("采集时长超出范围")?;
        previous_end = interval.ended_ms;
    }
    Ok(duration)
}

fn check_no_overlap(mut pages: Vec<Vec<CaptureInterval>>) -> Result<(), &'static str> {
    let mut all = Vec::new();
    for page in pages.drain(..) {
        all.extend(page);
    }
    all.sort_by_key(|interval| interval.started_ms);
    let mut previous_end = 0;
    for interval in all {
        if interval.started_ms < previous_end || interval.ended_ms <= interval.started_ms {
            return Err("采集区间必须有序、非负且位于训练有效时间内");
        }
        previous_end = interval.ended_ms;
    }
    Ok(())
}

enum SettlementError {
    Business(i32, &'static str),
    Database(sqlx::Error),
}

impl From<sqlx::Error> for SettlementError {
    fn from(error: sqlx::Error) -> Self {
        Self::Database(error)
    }
}

impl From<&'static str> for SettlementError {
    fn from(message: &'static str) -> Self {
        Self::Business(202, message)
    }
}

fn invalid_stored(message: &'static str) -> SettlementError {
    SettlementError::Database(sqlx::Error::Protocol(message.into()))
}

#[derive(Default)]
struct KeyStatistics {
    pat_group: i64,
    error: i64,
    lack: i64,
    more: i64,
    lack_line: i64,
    more_line: i64,
    lack_group: i64,
    more_group: i64,
    alter_error: i64,
    bunch_group: i64,
    lack_gap: i64,
}

struct SourceGroup {
    page: i32,
    sort: i32,
    key: String,
    text: Vec<u16>,
}

struct CapturedGroup {
    value: String,
    time: String,
    text: Vec<u16>,
    tokens: Option<Vec<String>>,
}

struct RebuiltGroup {
    page: i32,
    sort: i32,
    key: Option<String>,
    value: Option<String>,
    time: Option<String>,
}

fn string_array(text: &str) -> Result<Vec<String>, &'static str> {
    parse_array(text)
        .map_err(|_| "原始字段必须是JSON数组")?
        .into_iter()
        .map(|value| match value {
            Value::String(value) => Ok(value),
            Value::Number(value) => Ok(value.to_string()),
            Value::Bool(value) => Ok(value.to_string()),
            _ => Err("原始数组不能包含空值或复合对象"),
        })
        .collect()
}

fn electronic_rule(content: &str) -> Result<Value, &'static str> {
    let rule: Value = serde_json::from_str(content).map_err(|_| "评分规则必须是完整JSON对象")?;
    if rule.get("rateUnit").and_then(Value::as_str) != Some("FOUR_CHARACTER_GROUPS_PER_MINUTE") {
        return Err("评分规则rateUnit必须明确为FOUR_CHARACTER_GROUPS_PER_MINUTE");
    }
    let wpm = rule
        .get("wpm")
        .filter(|value| value.is_object())
        .ok_or("评分规则wpm必须是完整对象")?;
    let base = rule_decimal(wpm, "base", true)?;
    if base <= Decimal::ZERO || !base.fract().is_zero() || base > Decimal::from(i32::MAX) {
        return Err("评分规则速度基准必须为有效范围内的正整数");
    }
    rule_decimal(wpm, "r", false)?;
    rule_decimal(wpm, "l", false)?;
    let other = rule
        .get("other")
        .filter(|value| value.is_object())
        .ok_or("评分规则other必须是完整对象")?;
    for field in [
        "errorCode",
        "muchLessCode",
        "muchLessLine",
        "muchLessGroups",
        "alterError",
        "bunchGroup",
        "lessGap",
    ] {
        rule_decimal(other, field, true)?;
    }
    for field in [
        "correctMistakes",
        "lessPage",
        "lessReturnLine",
        "errorPage",
        "nonStandart",
    ] {
        rule_decimal(other, field, false)?;
    }
    Ok(rule)
}

fn rule_decimal(object: &Value, key: &str, required: bool) -> Result<Decimal, &'static str> {
    match object.get(key) {
        None | Some(Value::Null) if !required => Ok(Decimal::ZERO),
        Some(Value::Number(number)) => {
            let number = number.to_string();
            let value = Decimal::from_str(&number)
                .or_else(|_| Decimal::from_scientific(&number))
                .map_err(|_| "评分规则数值超出有效范围")?;
            if value < Decimal::ZERO {
                return Err("评分规则数值不能为负数");
            }
            Ok(value)
        }
        _ => Err("评分规则缺少有效数值"),
    }
}

fn corrected(text: &[u16]) -> &[u16] {
    text.iter()
        .rposition(|unit| *unit == u16::from(b'?'))
        .map_or(text, |index| &text[index + 1..])
}

fn contains_units(text: &[u16], part: &[u16]) -> bool {
    part.is_empty()
        || (part.len() <= text.len() && text.windows(part.len()).any(|window| window == part))
}

fn rebuilt(source: &SourceGroup, value: Option<String>, time: Option<String>) -> RebuiltGroup {
    RebuiltGroup {
        page: source.page,
        sort: source.sort,
        key: Some(source.key.clone()),
        value,
        time,
    }
}

fn annotated(group: &CapturedGroup, annotation: &str) -> String {
    if let Some(body) = &group.tokens {
        let mut tokens = Vec::with_capacity(body.len() + 1);
        tokens.push(annotation);
        tokens.extend(body.iter().map(String::as_str));
        json!(tokens).to_string()
    } else {
        format!("[{},{}", json!(annotation), &group.value[1..])
    }
}

fn utf16_array(units: &[u16]) -> String {
    let mut json = String::from("[");
    for (index, unit) in units.iter().enumerate() {
        if index > 0 {
            json.push(',');
        }
        if let Some(character) = char::from_u32(u32::from(*unit)) {
            json.push_str(&json!(character).to_string());
        } else {
            // Java groups UTF-16 code units, including individual surrogate halves.
            json.push_str(&format!("\"\\u{unit:04x}\""));
        }
    }
    json.push(']');
    json
}

// Port KeyPatUtils.handle's ordered lookahead and result annotations; raw rows stay immutable.
fn align_page(
    source: &[SourceGroup],
    captured: &[CapturedGroup],
    statistics: &mut KeyStatistics,
    output: &mut Vec<RebuiltGroup>,
) -> Result<(), &'static str> {
    let mut source_index = 0;
    let mut capture_index = 0;
    let mut missing_tail = 0;
    if source.is_empty() {
        statistics.more_line += captured.len().div_ceil(10) as i64;
        return Ok(());
    }
    while source_index < source.len() {
        let target = &source[source_index];
        let Some(group) = captured.get(capture_index) else {
            missing_tail += 1;
            output.push(rebuilt(target, None, None));
            source_index += 1;
            continue;
        };
        let actual = corrected(&group.text);
        if actual.len() != group.text.len() {
            statistics.alter_error += 1;
        }
        let mut handled = false;
        if target.text != actual {
            if capture_index > 0 {
                for ahead in 1..=4 {
                    let Some(next) = captured.get(capture_index + ahead) else {
                        break;
                    };
                    if corrected(&next.text) == target.text {
                        for (index, extra) in captured[capture_index..=capture_index + ahead]
                            .iter()
                            .enumerate()
                        {
                            output.push(rebuilt(
                                target,
                                Some(annotated(extra, &index.to_string())),
                                Some(extra.time.clone()),
                            ));
                        }
                        capture_index += ahead;
                        statistics.more_group += ahead as i64;
                        handled = true;
                        break;
                    }
                }
            }
            if !handled {
                for next_index in source_index + 1..source.len() {
                    if actual != source[next_index].text {
                        continue;
                    }
                    let bunch = next_index - source_index == 10
                        && (1..5).any(|ahead| {
                            captured
                                .get(capture_index + ahead)
                                .zip(source.get(source_index + ahead))
                                .is_some_and(|(value, key)| value.value == key.key)
                        });
                    if bunch {
                        output.push(rebuilt(
                            target,
                            Some(annotated(group, "bunchGroup")),
                            Some(group.time.clone()),
                        ));
                        statistics.bunch_group += 1;
                        handled = true;
                        // Java continues looking for a later matching target after a bunch-group match.
                        continue;
                    }
                    statistics.lack_group += (next_index - source_index) as i64;
                    for (offset, missing) in source[source_index..next_index].iter().enumerate() {
                        let mut row = rebuilt(missing, Some("[]".into()), Some("[]".into()));
                        row.sort = target
                            .sort
                            .checked_add(i32::try_from(offset).map_err(|_| "原始组排序超出范围")?)
                            .ok_or("原始组排序超出范围")?;
                        output.push(row);
                    }
                    output.push(rebuilt(
                        &source[next_index],
                        Some(group.value.clone()),
                        Some(group.time.clone()),
                    ));
                    source_index = next_index;
                    handled = true;
                    break;
                }
            }
            if !handled && actual.len() > 4 && contains_units(actual, &target.text) {
                statistics.more += 1;
                output.push(rebuilt(
                    target,
                    Some(annotated(group, "more")),
                    Some(group.time.clone()),
                ));
                handled = true;
            }
            if !handled && actual.len() < 4 && contains_units(&target.text, actual) {
                statistics.lack += 1;
                output.push(rebuilt(
                    target,
                    Some(annotated(group, "lack")),
                    Some(group.time.clone()),
                ));
                handled = true;
            }
            if !handled {
                statistics.error += 1;
            }
        }
        if !handled {
            output.push(rebuilt(
                target,
                Some(group.value.clone()),
                Some(group.time.clone()),
            ));
        }
        capture_index += 1;
        statistics.pat_group += 1;
        if source_index == source.len() - 1 && capture_index < captured.len() {
            statistics.more_line += (captured.len() - capture_index).div_ceil(10) as i64;
            for (offset, extra) in captured[capture_index..].iter().enumerate() {
                output.push(RebuiltGroup {
                    page: target.page,
                    sort: i32::try_from(source_index + offset + 1)
                        .map_err(|_| "原始组排序超出范围")?,
                    key: None,
                    value: Some(extra.value.clone()),
                    time: Some(extra.time.clone()),
                });
            }
        }
        source_index += 1;
    }
    // This is assignment, not accumulation, in Java's per-page alignment contract.
    statistics.lack_line = missing_tail / 10;
    Ok(())
}

fn captured_groups(
    values: &[PageMessage],
    train_id: &str,
    page: i32,
    statistics: &mut KeyStatistics,
) -> Result<(Vec<CapturedGroup>, i64, bool), &'static str> {
    let mut groups = Vec::with_capacity(values.len());
    let mut characters = 0_i64;
    let mut has_events = false;
    let mut previous_sort = -1;
    for value in values {
        let sort = value.sort.ok_or("原始组必须有序且属于当前训练页")?;
        if sort <= previous_sort
            || value
                .train_id
                .as_deref()
                .is_some_and(|id| !id.eq_ignore_ascii_case(train_id))
            || value.page_number.is_some_and(|number| number != page)
        {
            return Err("原始组必须有序且属于当前训练页");
        }
        previous_sort = sort;
        let body = value.value.as_deref().ok_or("原始组字段不能为空")?;
        let time = value.time.as_deref().ok_or("原始组字段不能为空")?;
        let tokens = string_array(body)?;
        let times = string_array(time)?;
        string_array(value.key.as_deref().ok_or("原始组字段不能为空")?)?;
        if tokens.len() != times.len() {
            return Err("原始字符与事件时长数量不一致");
        }
        let times = times
            .iter()
            .map(|time| {
                time.parse::<i64>()
                    .ok()
                    .filter(|time| *time >= 0)
                    .ok_or("原始事件时长无效")
            })
            .collect::<Result<Vec<_>, _>>()?;
        has_events |= !tokens.is_empty();
        for token in &tokens {
            if !matches!(token.as_str(), "开始" | "句号" | "结束" | "?") {
                let count = token
                    .chars()
                    .filter(|c| !c.is_whitespace() && !c.is_control())
                    .count();
                characters = characters
                    .checked_add(i64::try_from(count).map_err(|_| "原始字符数量超出范围")?)
                    .ok_or("原始字符数量超出范围")?;
            }
        }
        let text = tokens
            .iter()
            .flat_map(|token| token.encode_utf16())
            .collect::<Vec<_>>();
        if text.len() >= 8 && !text.contains(&u16::from(b'?')) {
            let chunks = text.chunks(4);
            statistics.lack_gap += chunks.len() as i64 - 1;
            let mut time_chunks = times.chunks(4);
            for chunk in chunks {
                let chunk_time = time_chunks.next().ok_or("原始字符与分组时长数量不一致")?;
                groups.push(CapturedGroup {
                    value: utf16_array(chunk),
                    time: json!(chunk_time).to_string(),
                    text: chunk.to_vec(),
                    tokens: None,
                });
            }
        } else {
            groups.push(CapturedGroup {
                value: body.to_owned(),
                time: time.to_owned(),
                text,
                tokens: Some(tokens),
            });
        }
    }
    Ok((groups, characters, has_events))
}

pub(super) fn raw_page_analysis(raw: &MySqlRow) -> Result<Value, sqlx::Error> {
    let values: Vec<PageMessage> = serde_json::from_str(&raw.try_get::<String, _>("value")?)
        .map_err(|_| sqlx::Error::Protocol("invalid raw page JSON".into()))?;
    let intervals: Vec<CaptureInterval> =
        serde_json::from_str(&raw.try_get::<String, _>("capture_intervals")?)
            .map_err(|_| sqlx::Error::Protocol("invalid capture timeline".into()))?;
    let elapsed = raw
        .try_get::<Option<i64>, _>("elapsed_ms")?
        .ok_or_else(|| sqlx::Error::Protocol("missing capture receipt timestamp".into()))?;
    let duration = valid_intervals(&intervals, elapsed)
        .map_err(|message| sqlx::Error::Protocol(message.into()))?;
    let mut characters = 0_i64;
    for value in values {
        let body = value
            .value
            .ok_or_else(|| sqlx::Error::Protocol("missing captured value".into()))?;
        for token in string_array(&body).map_err(|message| sqlx::Error::Protocol(message.into()))? {
            if !matches!(token.as_str(), "开始" | "句号" | "结束" | "?") {
                characters = characters
                    .checked_add(
                        token
                            .chars()
                            .filter(|c| !c.is_whitespace() && !c.is_control())
                            .count() as i64,
                    )
                    .ok_or_else(|| {
                        sqlx::Error::Protocol("capture character count overflow".into())
                    })?;
            }
        }
    }
    let characters = i32::try_from(characters)
        .map_err(|_| sqlx::Error::Protocol("capture character count overflow".into()))?;
    Ok(
        json!({"pageNumber": raw.try_get::<i32, _>("page_number")?, "patNumber": characters, "totalTime": duration}),
    )
}

fn score_result(
    rule: &Value,
    full_score: Decimal,
    statistics: &KeyStatistics,
    characters: i64,
    duration: i64,
) -> Result<(Decimal, Decimal, Value), &'static str> {
    let speed = if characters <= 0 || duration <= 0 {
        Decimal::ZERO
    } else {
        (Decimal::from(characters) * Decimal::from(15_000) / Decimal::from(duration))
            .round_dp_with_strategy(0, RoundingStrategy::MidpointAwayFromZero)
    };
    if speed > Decimal::from(i32::MAX) {
        return Err("拍发速度超出有效范围");
    }
    let mut score = full_score;
    let mut deduct = serde_json::Map::new();
    for (name, field, count) in [
        ("error", "errorCode", statistics.error),
        ("lack", "muchLessCode", statistics.lack),
        ("more", "muchLessCode", statistics.more),
        ("lackLine", "muchLessLine", statistics.lack_line),
        ("moreLine", "muchLessLine", statistics.more_line),
        ("lackGroup", "muchLessGroups", statistics.lack_group),
        ("moreGroup", "muchLessGroups", statistics.more_group),
        ("alterError", "alterError", statistics.alter_error),
        ("bunchGroup", "bunchGroup", statistics.bunch_group),
        ("lackGap", "lessGap", statistics.lack_gap),
    ] {
        let deduction = rule_decimal(&rule["other"], field, true)?
            .checked_mul(Decimal::from(count))
            .ok_or("评分结果超出有效范围")?;
        score = score.checked_sub(deduction).ok_or("评分结果超出有效范围")?;
        deduct.insert(format!("{name}Number"), json!(count));
        deduct.insert(format!("{name}Score"), json!(format!("-{deduction}")));
    }
    let difference = speed - rule_decimal(&rule["wpm"], "base", true)?;
    let speed_score = if difference == Decimal::ZERO {
        Decimal::ZERO
    } else {
        rule_decimal(
            &rule["wpm"],
            if difference > Decimal::ZERO { "r" } else { "l" },
            false,
        )?
        .checked_mul(difference)
        .ok_or("评分结果超出有效范围")?
    };
    score = score
        .checked_add(speed_score)
        .ok_or("评分结果超出有效范围")?;
    deduct.insert("speedNumber".into(), json!(speed.to_string()));
    deduct.insert(
        "speedScore".into(),
        json!(if speed_score > Decimal::ZERO {
            format!("+{speed_score}")
        } else {
            speed_score.to_string()
        }),
    );
    Ok((score, speed, Value::Object(deduct)))
}

// Every caller holds the canonical train PRIMARY row before its first consistent read.
// All page writers (including lazy getPage generation) use that same parent lock.
async fn delete_child_rows(
    connection: &mut sqlx::MySqlConnection,
    table: &str,
    train_id: &str,
) -> Result<(), sqlx::Error> {
    let ids = sqlx::query_scalar::<_, String>(&format!(
        "SELECT id FROM {table} WHERE train_id=? ORDER BY id"
    ))
    .bind(train_id)
    .fetch_all(&mut *connection)
    .await?;
    let sql = format!("DELETE FROM {table} WHERE id=?");
    for id in ids {
        sqlx::query(&sql).bind(id).execute(&mut *connection).await?;
    }
    Ok(())
}

async fn settle_locked(
    connection: &mut sqlx::MySqlConnection,
    id: &str,
    user_id: &str,
    row: &MySqlRow,
) -> Result<(), SettlementError> {
    let rule_content = row
        .try_get::<Option<String>, _>("rule_content")?
        .ok_or("训练评分规则快照缺失")?;
    let rule = electronic_rule(&rule_content)?;
    let full_score = row
        .try_get::<Option<Decimal>, _>("full_score")?
        .ok_or("训练缺少冻结满分")?;
    if full_score < Decimal::ZERO {
        return Err("训练满分快照无效，请新建训练".into());
    }
    let attempt = row.try_get::<i32, _>("attempt")?;
    let sources = sqlx::query("SELECT page_number,sort,`key` FROM t_post_telegraph_key_pat_train_page WHERE train_id=? ORDER BY page_number,sort")
        .bind(id).fetch_all(&mut *connection).await?;
    let raw_pages = sqlx::query("SELECT raw.page_number,raw.attempt,raw.value,raw.capture_intervals,CAST(TIMESTAMPDIFF(MICROSECOND,train.begin_time,raw.received_at) DIV 1000 AS SIGNED) AS elapsed_ms FROM t_post_telegraph_key_pat_train_raw_page raw JOIN t_post_telegraph_key_pat_train train ON train.id=raw.train_id COLLATE utf8mb4_general_ci WHERE raw.train_id=? ORDER BY raw.page_number")
        .bind(id).fetch_all(&mut *connection).await?;
    let total_groups = if row.try_get::<Option<i32>, _>("is_cable")? == Some(1) {
        i32::try_from(sources.len()).map_err(|_| "训练总组数超出范围")?
    } else {
        row.try_get::<Option<i32>, _>("total_number")?
            .ok_or("训练总组数缺失，无法结算")?
    };
    if total_groups < 0 {
        return Err("训练总组数无效".into());
    }
    let total_pages = total_groups / 100 + i32::from(total_groups % 100 > 0);
    let mut source_pages: BTreeMap<i32, Vec<SourceGroup>> = BTreeMap::new();
    for source in sources {
        let page = source
            .try_get::<Option<i32>, _>("page_number")?
            .ok_or_else(|| invalid_stored("source page number is missing"))?;
        let sort = source
            .try_get::<Option<i32>, _>("sort")?
            .ok_or_else(|| invalid_stored("source sort is missing"))?;
        let key = source
            .try_get::<Option<String>, _>("key")?
            .ok_or_else(|| invalid_stored("source key is missing"))?;
        let tokens = string_array(&key).map_err(|_| invalid_stored("source key is invalid"))?;
        let text = tokens
            .iter()
            .flat_map(|token| token.encode_utf16())
            .collect();
        let groups = source_pages.entry(page).or_default();
        if sort < 0 || groups.last().is_some_and(|group| group.sort >= sort) {
            return Err(invalid_stored("source group ordering is invalid"));
        }
        groups.push(SourceGroup {
            page,
            sort,
            key,
            text,
        });
    }
    let mut timelines = Vec::with_capacity(raw_pages.len());
    let mut submitted = BTreeSet::new();
    let mut statistics = KeyStatistics::default();
    let mut output = Vec::new();
    let mut duration = 0_i64;
    let mut characters = 0_i64;
    for raw in raw_pages {
        let page = raw.try_get::<i32, _>("page_number")?;
        if raw.try_get::<i32, _>("attempt")? != attempt {
            return Err("原始记录轮次不匹配".into());
        }
        if page < 1 || page > total_pages {
            return Err("页码超出训练范围".into());
        }
        if !submitted.insert(page) {
            return Err(invalid_stored("duplicate raw page"));
        }
        let intervals: Vec<CaptureInterval> =
            serde_json::from_str(&raw.try_get::<String, _>("capture_intervals")?)
                .map_err(|_| invalid_stored("stored capture timeline is invalid"))?;
        let maximum_end = raw
            .try_get::<Option<i64>, _>("elapsed_ms")?
            .ok_or_else(|| invalid_stored("capture receipt timestamp is missing"))?;
        let page_duration = valid_intervals(&intervals, maximum_end)?;
        let values: Vec<PageMessage> = serde_json::from_str(&raw.try_get::<String, _>("value")?)
            .map_err(|_| invalid_stored("stored raw page JSON is invalid"))?;
        let (captured, count, has_events) = captured_groups(&values, id, page, &mut statistics)?;
        if has_events && page_duration == 0 {
            return Err("非空原始事件必须提供有效采集时长".into());
        }
        duration = duration
            .checked_add(page_duration)
            .ok_or("采集时长超出范围")?;
        characters = characters
            .checked_add(count)
            .ok_or("原始字符数量超出范围")?;
        timelines.push(intervals);
        align_page(
            source_pages
                .get(&page)
                .map(Vec::as_slice)
                .unwrap_or_default(),
            &captured,
            &mut statistics,
            &mut output,
        )?;
    }
    check_no_overlap(timelines)?;
    let rebuilt_pages: BTreeSet<i32> = output.iter().map(|group| group.page).collect();
    if rebuilt_pages != submitted {
        return Err("拍发记录重建结果页号与原记录不一致，拒绝删除已有拍发记录".into());
    }
    let missing_pages = i64::from(total_pages) - submitted.len() as i64;
    if missing_pages > 0 {
        let submitted_groups: i64 = submitted
            .iter()
            .map(|page| {
                if *page == total_pages && total_groups % 100 > 0 {
                    i64::from(total_groups % 100)
                } else {
                    100
                }
            })
            .sum();
        statistics.lack_group += i64::from(total_groups) - submitted_groups;
        statistics.lack_line += missing_pages * 10;
    }
    let (score, speed, deductions) =
        score_result(&rule, full_score, &statistics, characters, duration)?;
    let accuracy = super::scoring::accuracy_percent(
        statistics.pat_group
            - statistics.error
            - statistics.bunch_group
            - statistics.lack
            - statistics.more,
        statistics.pat_group,
    );
    let error_number = i32::try_from(statistics.error).map_err(|_| "错误组数超出范围")?;
    // Validate and calculate everything before replacing the derived values.
    delete_child_rows(connection, "t_post_telegraph_key_pat_train_page_value", id).await?;
    for value in output {
        sqlx::query("INSERT INTO t_post_telegraph_key_pat_train_page_value (id,train_id,page_number,sort,`key`,value,time) VALUES (?,?,?,?,?,?,?)")
            .bind(uuid::Uuid::new_v4().to_string()).bind(id).bind(value.page).bind(value.sort)
            .bind(value.key).bind(value.value).bind(value.time).execute(&mut *connection).await?;
    }
    let updated = sqlx::query("UPDATE t_post_telegraph_key_pat_train SET status=2,end_time=NOW(6),duration=?,speed=?,accuracy=?,error_number=?,score=?,deduct_info=? WHERE id=? AND create_user_id=? AND protocol_version=1 AND attempt=? AND status=1")
        .bind(Decimal::new(duration, 3).normalize().to_string()).bind(speed.to_string()).bind(accuracy.to_string())
        .bind(error_number).bind(score.to_string()).bind(deductions.to_string()).bind(id).bind(user_id).bind(attempt)
        .execute(&mut *connection).await?;
    if updated.rows_affected() != 1 {
        return Err(SettlementError::Business(208, "训练状态不可执行"));
    }
    Ok(())
}

async fn action_locked(
    connection: &mut sqlx::MySqlConnection,
    id: &str,
    user_id: &str,
    protocol_version: Option<i32>,
    attempt: Option<i32>,
    finish: bool,
) -> Result<(), SettlementError> {
    let row = sqlx::query("SELECT id,create_user_id,status,protocol_version,attempt,CAST(begin_time IS NOT NULL AS SIGNED) AS begun,total_number,is_cable,rule_content,full_score FROM t_post_telegraph_key_pat_train WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(id).fetch_optional(&mut *connection).await?.ok_or("未查询到训练")?;
    let canonical_id = row.try_get::<String, _>("id")?;
    let id = canonical_id.as_str();
    if row
        .try_get::<Option<String>, _>("create_user_id")?
        .as_deref()
        != Some(user_id)
    {
        return Err(SettlementError::Business(207, "无权限"));
    }
    let version = row.try_get::<Option<i32>, _>("protocol_version")?;
    let status = row.try_get::<Option<i32>, _>("status")?;
    if finish && status == Some(2) && version == Some(0) {
        return Ok(());
    }
    if version != Some(1) {
        return Err("旧训练缺少采集时间轴，请终止旧训练并新建训练".into());
    }
    if protocol_version != Some(1)
        || attempt.is_none()
        || attempt != row.try_get::<Option<i32>, _>("attempt")?
    {
        return Err(SettlementError::Business(
            208,
            "训练协议或轮次已失效，请重新读取训练",
        ));
    }
    if finish {
        if status == Some(2) {
            return Ok(());
        }
        if status != Some(1) || row.try_get::<i64, _>("begun")? == 0 {
            return Err("训练尚未开始或已经结束".into());
        }
        settle_locked(connection, id, user_id, &row).await
    } else {
        let content = row
            .try_get::<Option<String>, _>("rule_content")?
            .ok_or("训练评分规则快照缺失")?;
        electronic_rule(&content)?;
        let full_score = row
            .try_get::<Option<Decimal>, _>("full_score")?
            .ok_or("训练满分快照无效，请新建训练")?;
        if full_score < Decimal::ZERO {
            return Err("训练满分快照无效，请新建训练".into());
        }
        if status == Some(0) {
            sqlx::query(
                "UPDATE t_post_telegraph_key_pat_train SET status=1,begin_time=NOW(6) WHERE id=?",
            )
            .bind(id)
            .execute(&mut *connection)
            .await?;
        } else if status != Some(1) {
            return Err(SettlementError::Business(
                208,
                "训练已结束，请重置或新建训练",
            ));
        }
        Ok(())
    }
}

async fn action(state: AppState, user: AuthUser, body: ActionRequest, finish: bool) -> Response {
    let Some(id) = body.id.filter(|value| !value.trim().is_empty()) else {
        return business(202, "请求参数错误");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "action-begin"),
    };
    if let Err(error) = action_locked(
        &mut tx,
        &id,
        &user.id,
        body.protocol_version,
        body.attempt,
        finish,
    )
    .await
    {
        if let Err(rollback_error) = tx.rollback().await {
            return err(rollback_error, "action-rollback");
        }
        return match error {
            SettlementError::Business(code, message) => business(code, message),
            SettlementError::Database(error) => err(error, "action-settlement"),
        };
    }
    if let Err(error) = tx.commit().await {
        return err(error, "action-commit");
    }
    match post_telegraph::owned_train_json(pool, &id, &user.id).await {
        Ok(Ok(value)) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Ok(Err(code)) => business(
            code,
            if code == 207 {
                "无权限"
            } else {
                "未查询到训练"
            },
        ),
        Err(error) => err(error, "action-detail"),
    }
}

pub(crate) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<AddRequest>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return business(500, "服务器错误");
    };
    let Some(is_cable) = body.is_cable else {
        return business(202, "是否使用固定报底不能为空");
    };
    if !(0..=1).contains(&is_cable) {
        return business(202, "是否使用固定报底参数无效");
    }
    let Some(total_number) = body.total_number.filter(|value| *value >= 0) else {
        return business(202, "训练总组数不能为空");
    };
    if body
        .message_type
        .is_some_and(|message_type| !(0..=2).contains(&message_type))
    {
        return business(202, "报文类型无效");
    }
    let message_type = if is_cable == 0 {
        let Some(message_type) = body.message_type else {
            return business(202, "报文类型不能为空");
        };
        message_type
    } else {
        body.message_type.unwrap_or(0)
    };
    let Some(rule_id) = body
        .rule_id
        .as_deref()
        .filter(|value| !value.trim().is_empty())
        .map(str::to_owned)
    else {
        return business(202, "评分规则ID不能为空");
    };
    let cable_id = if is_cable == 1 {
        let Some(cable_id) = body
            .cable_id
            .as_deref()
            .filter(|value| !value.trim().is_empty())
            .map(str::to_owned)
        else {
            return business(202, "固定报编号不能为空");
        };
        if total_number / 100 == 0 {
            return business(202, "报文组数不足一页，无法建立训练");
        }
        Some(cable_id)
    } else {
        None
    };

    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "add-begin"),
    };
    let rule =
        match sqlx::query("SELECT content,score FROM t_grading_rule WHERE id=? LIMIT 1 FOR UPDATE")
            .bind(&rule_id)
            .fetch_optional(&mut *tx)
            .await
        {
            Ok(Some(row)) => row,
            Ok(None) => return business(202, "未查询到评分规则"),
            Err(error) => return err(error, "add-rule"),
        };
    let Some(rule_content) = rule.get::<Option<String>, _>("content") else {
        return business(202, "评分规则缺失");
    };
    if let Err(message) = electronic_rule(&rule_content) {
        return business(202, message);
    }
    let Some(score) = rule
        .get::<Option<i32>, _>("score")
        .filter(|score| *score >= 0)
    else {
        return business(202, "评分规则满分必须为非负整数");
    };
    let full_score = Decimal::from(score);
    let id = uuid::Uuid::new_v4().to_string();
    if let Err(error) = sqlx::query("INSERT INTO t_post_telegraph_key_pat_train (id,title,is_cable,status,duration,total_number,error_number,accuracy,speed,create_user_id,attempt,protocol_version,content,message_type,rule_id,rule_content,full_score,score,create_time) VALUES (?,?,?,0,'0',?,0,0,NULL,?,0,1,NULL,?,?,?,?,?,NOW())")
        .bind(&id)
        .bind(body.title.as_deref())
        .bind(is_cable)
        .bind(total_number)
        .bind(&user.id)
        .bind(message_type)
        .bind(&rule_id)
        .bind(&rule_content)
        .bind(full_score)
        .bind(full_score)
        .execute(&mut *tx)
        .await
    {
        return err(error, "add-train");
    }

    if is_cable == 0 {
        let generated_total = total_number.min(200) as usize;
        for offset in (0..generated_total).step_by(100) {
            let count = (generated_total - offset).min(100);
            for (sort, group) in generated_groups(message_type, count)
                .into_iter()
                .enumerate()
            {
                let key = serde_json::to_string(
                    &group
                        .chars()
                        .map(|character| character.to_string())
                        .collect::<Vec<_>>(),
                )
                .expect("generated report contains only JSON-safe characters");
                if let Err(error) = sqlx::query("INSERT INTO t_post_telegraph_key_pat_train_page (id,train_id,page_number,sort,`key`,value,time) VALUES (?,?,?,?,?,?,?)")
                    .bind(uuid::Uuid::new_v4().to_string())
                    .bind(&id)
                    .bind((offset / 100 + 1) as i32)
                    .bind(sort as i32)
                    .bind(key)
                    .bind("[]")
                    .bind("[]")
                    .execute(&mut *tx)
                    .await
                {
                    return err(error, "add-page");
                }
            }
        }
    } else {
        // This is the transaction's first consistent read. Only floor content is copied,
        // so one committed MVCC snapshot is sufficient even for child-first cable deletes.
        let floors = match sqlx::query("SELECT floor_number,sort,mores_key FROM t_cable_floor WHERE cable_id=? ORDER BY floor_number,sort")
            .bind(cable_id.as_deref())
            .fetch_all(&mut *tx)
            .await
        {
            Ok(rows) => rows,
            Err(error) => return err(error, "add-cable-pages"),
        };
        let mut grouped = BTreeMap::<i32, Vec<String>>::new();
        for floor in floors {
            let Some(floor_number) = floor.get::<Option<i32>, _>("floor_number") else {
                return business(202, "固定报页码缺失");
            };
            let Some(raw_key) = floor.get::<Option<String>, _>("mores_key") else {
                return business(202, "固定报内容缺失");
            };
            let key = match string_array(&raw_key) {
                Ok(values) => {
                    serde_json::to_string(&values).expect("fixed report characters are JSON-safe")
                }
                Err(_) => return business(202, "固定报内容不合法"),
            };
            grouped.entry(floor_number).or_default().push(key);
        }
        let grouped: Vec<Vec<String>> = grouped.into_iter().map(|(_, groups)| groups).collect();
        let requested_start = body.start_page.unwrap_or(1);
        let requested_start = if requested_start > 1 {
            let index = (requested_start - 1) as usize;
            if index < grouped.len() {
                index
            } else {
                0
            }
        } else {
            0
        };
        let page_count = (total_number / 100) as usize;
        if page_count > grouped.len().saturating_sub(requested_start) {
            return business(202, "所选电缆可用楼层不足");
        }
        for (page, groups) in grouped
            .into_iter()
            .skip(requested_start)
            .take(page_count)
            .enumerate()
        {
            for (sort, key) in groups.into_iter().enumerate() {
                if let Err(error) = sqlx::query("INSERT INTO t_post_telegraph_key_pat_train_page (id,train_id,page_number,sort,`key`,value,time) VALUES (?,?,?,?,?,?,?)")
                    .bind(uuid::Uuid::new_v4().to_string())
                    .bind(&id)
                    .bind(page as i32 + 1)
                    .bind(sort as i32)
                    .bind(key)
                    .bind("[]")
                    .bind("[]")
                    .execute(&mut *tx)
                    .await
                {
                    return err(error, "add-cable-page");
                }
            }
        }
    }
    if let Err(error) = tx.commit().await {
        return err(error, "add-commit");
    }
    match post_telegraph::owned_train_json(pool, &id, &user.id).await {
        Ok(Ok(value)) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Ok(Err(code)) => business(
            code,
            if code == 207 {
                "无权限"
            } else {
                "未查询到训练"
            },
        ),
        Err(error) => err(error, "add-detail"),
    }
}

pub(crate) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    action(state, user, body, false).await
}
pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    action(state, user, body, true).await
}

pub(crate) async fn reset(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    let Some(id) = body.id.filter(|value| !value.trim().is_empty()) else {
        return business(202, "请求参数错误");
    };
    let Some(pool) = state.pool.as_ref() else {
        return business(500, "服务器错误");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return err(e, "reset-begin"),
    };
    let row = match sqlx::query("SELECT id,create_user_id,protocol_version,attempt FROM t_post_telegraph_key_pat_train WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(&id).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => row,
        Ok(None) => return business(202, "未查询到训练"),
        Err(e) => return err(e, "reset-select"),
    };
    let id = row.get::<String, _>("id");
    if row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return business(207, "无权限");
    }
    if row.get::<Option<i32>, _>("protocol_version").unwrap_or(0) != 1 {
        return business(202, "旧训练缺少采集时间轴，请终止旧训练并新建训练");
    }
    if row.get::<Option<i32>, _>("protocol_version").unwrap_or(0)
        != body.protocol_version.unwrap_or(-1)
        || row.get::<Option<i32>, _>("attempt").unwrap_or(-1) != body.attempt.unwrap_or(-1)
    {
        return business(208, "训练协议或轮次已失效，请重新读取训练");
    }
    for table in [
        "t_post_telegraph_key_pat_train_raw_page",
        "t_post_telegraph_key_pat_train_page_value",
        "t_post_telegraph_key_pat_train_more",
    ] {
        if let Err(e) = delete_child_rows(&mut tx, table, &id).await {
            return err(e, "reset-delete");
        }
    }
    let result = sqlx::query("UPDATE t_post_telegraph_key_pat_train SET status=0,attempt=attempt+1,begin_time=NULL,end_time=NULL,duration='0',speed='0',accuracy=0,error_number=0,score=full_score,deduct_info=NULL,content=NULL WHERE id=? AND create_user_id=?")
        .bind(&id).bind(&user.id).execute(&mut *tx).await;
    match result {
        Ok(result) if result.rows_affected() == 1 => {}
        Ok(_) => return business(208, "训练状态不可执行"),
        Err(e) => return err(e, "reset-update"),
    }
    if let Err(e) = tx.commit().await {
        return err(e, "reset-commit");
    }
    match post_telegraph::owned_train_json(pool, &id, &user.id).await {
        Ok(Ok(value)) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Ok(Err(code)) => business(code, "未查询到训练"),
        Err(e) => err(e, "reset-detail"),
    }
}

pub(crate) async fn finish_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(dto): Json<FinishPageRequest>,
) -> Response {
    let Some(id) = dto.id.clone().filter(|value| !value.trim().is_empty()) else {
        return business(202, "请求参数错误");
    };
    let Some(page_number) = dto.page_number else {
        return business(202, "页码不能为空");
    };
    let Some(intervals) = dto.capture_intervals.clone() else {
        return business(202, "采集时间轴不能为空");
    };
    let Some(pool) = state.pool.as_ref() else {
        return business(500, "服务器错误");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return err(e, "finish-page-begin"),
    };
    let row = match sqlx::query("SELECT id,create_user_id,status,protocol_version,attempt,total_number,is_cable,message_type,CAST(begin_time IS NOT NULL AS SIGNED) AS begun,CAST(COALESCE(GREATEST(0,TIMESTAMPDIFF(MICROSECOND,begin_time,NOW(6)) DIV 1000),0) AS SIGNED) AS elapsed_ms FROM t_post_telegraph_key_pat_train WHERE id=? LIMIT 1 FOR UPDATE").bind(&id).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => row,
        Ok(None) => return business(202, "未查询到训练"),
        Err(e) => return err(e, "finish-page-select"),
    };
    let id = row.get::<String, _>("id");
    if row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return business(207, "无权限");
    }
    if row.get::<Option<i32>, _>("protocol_version").unwrap_or(0) != 1 {
        return business(202, "旧训练缺少采集时间轴，请终止旧训练并新建训练");
    }
    if dto.protocol_version != Some(1) || dto.attempt != row.get::<Option<i32>, _>("attempt") {
        return business(208, "训练协议或轮次已失效，请重新读取训练");
    }
    let groups = if row.get::<Option<i32>, _>("is_cable").unwrap_or(0) == 1 {
        match sqlx::query(
            "SELECT COUNT(*) AS total FROM t_post_telegraph_key_pat_train_page WHERE train_id=?",
        )
        .bind(&id)
        .fetch_one(&mut *tx)
        .await
        {
            Ok(value) => value.get::<i64, _>("total") as i32,
            Err(e) => return err(e, "finish-page-source-count"),
        }
    } else {
        row.get::<Option<i32>, _>("total_number").unwrap_or(0)
    };
    let maximum_page = groups / 100 + i32::from(groups % 100 > 0);
    if page_number < 1 || maximum_page < 1 || page_number > maximum_page {
        return business(202, "页码不正确");
    }
    let values = match canonical_messages(&dto, &id, page_number) {
        Ok(values) => values,
        Err(message) => return business(202, message),
    };
    let raw_value = match serde_json::to_string(&values) {
        Ok(value) => value,
        Err(_) => return business(500, "服务器错误"),
    };
    let interval_json = match serde_json::to_string(&intervals) {
        Ok(value) => value,
        Err(_) => return business(202, "采集时间轴无效"),
    };
    let existing = match sqlx::query("SELECT id,attempt,value,capture_intervals FROM t_post_telegraph_key_pat_train_raw_page WHERE train_id=? AND page_number=? LIMIT 1").bind(&id).bind(page_number).fetch_optional(&mut *tx).await { Ok(row) => row, Err(e) => return err(e, "finish-page-existing") };
    if let Some(existing) = existing.as_ref() {
        if existing.get::<i32, _>("attempt") != dto.attempt.unwrap_or(-1) {
            return business(208, "已保存页轮次不匹配，请重新读取训练");
        }
        if existing.get::<String, _>("capture_intervals") == interval_json {
            let saved_value = existing.get::<String, _>("value");
            let same_content = if saved_value == raw_value {
                true
            } else {
                match same_raw_messages(&saved_value, &values, &id) {
                    Ok(same) => same,
                    Err(_) => return business(500, "已保存原始页内容无效"),
                }
            };
            if same_content {
                if let Err(e) = tx.rollback().await {
                    return err(e, "finish-page-rollback");
                }
                return post_telegraph::get_page(
                    State(state),
                    Extension(user),
                    Query(post_telegraph::PageQuery {
                        train_id: Some(id),
                        page_number: Some(page_number),
                    }),
                )
                .await;
            }
            return business(202, "同一采集时间轴内容冲突，请重新读取已保存页");
        }
        let old: Vec<CaptureInterval> =
            match serde_json::from_str(existing.get::<String, _>("capture_intervals").as_str()) {
                Ok(value) => value,
                Err(_) => return business(500, "已保存采集时间轴无效"),
            };
        if intervals.len() <= old.len()
            || intervals
                .iter()
                .zip(&old)
                .any(|(left, right)| left != right)
        {
            return business(208, "不能覆盖已确认的原始采集区间");
        }
    }
    if row.get::<Option<i32>, _>("status").unwrap_or(0) != 1
        || row.get::<Option<i64>, _>("begun").unwrap_or(0) == 0
    {
        return business(208, "训练尚未开始或已经结束");
    }
    let duration = match valid_intervals(
        &intervals,
        row.get::<Option<i64>, _>("elapsed_ms").unwrap_or(0),
    ) {
        Ok(duration) => duration,
        Err(message) => return business(202, message),
    };
    if duration == 0
        && values.iter().any(|value| {
            value
                .get("value")
                .and_then(Value::as_str)
                .is_some_and(|text| parse_array(text).is_ok_and(|array| !array.is_empty()))
        })
    {
        return business(202, "非空原始事件必须提供有效采集时长");
    }
    let all_rows = match sqlx::query("SELECT capture_intervals FROM t_post_telegraph_key_pat_train_raw_page WHERE train_id=? AND page_number<>?").bind(&id).bind(page_number).fetch_all(&mut *tx).await { Ok(rows) => rows, Err(e) => return err(e, "finish-page-overlap") };
    let mut timelines = Vec::with_capacity(all_rows.len() + 1);
    for raw in all_rows {
        match serde_json::from_str(raw.get::<String, _>("capture_intervals").as_str()) {
            Ok(value) => timelines.push(value),
            Err(_) => return business(500, "已保存采集时间轴无效"),
        }
    }
    timelines.push(intervals);
    if let Err(message) = check_no_overlap(timelines) {
        return business(202, message);
    }
    if row.get::<Option<i32>, _>("is_cable") == Some(0) {
        let generated = match sqlx::query_scalar::<_, i64>("SELECT EXISTS(SELECT 1 FROM t_post_telegraph_key_pat_train_page WHERE train_id=? AND page_number=?)")
            .bind(&id).bind(page_number).fetch_one(&mut *tx).await {
            Ok(generated) => generated != 0,
            Err(e) => return err(e, "finish-page-source-exists"),
        };
        if !generated {
            if let Err(e) =
                post_telegraph::generate_source_page(&mut tx, &row, &id, page_number).await
            {
                return err(e, "finish-page-source");
            }
        }
    }
    let result = if let Some(existing) = existing {
        sqlx::query("UPDATE t_post_telegraph_key_pat_train_raw_page SET value=?,capture_intervals=?,received_at=NOW(6) WHERE id=?").bind(raw_value).bind(interval_json).bind(existing.get::<String, _>("id")).execute(&mut *tx).await
    } else {
        sqlx::query("INSERT INTO t_post_telegraph_key_pat_train_raw_page (id,train_id,page_number,attempt,value,capture_intervals,received_at) VALUES (?,?,?,?,?,?,NOW(6))").bind(uuid::Uuid::new_v4().to_string()).bind(&id).bind(page_number).bind(dto.attempt.unwrap_or(0)).bind(raw_value).bind(interval_json).execute(&mut *tx).await
    };
    match result {
        Ok(_) => {}
        Err(e) => return err(e, "finish-page-save"),
    }
    if let Err(e) = tx.commit().await {
        return err(e, "finish-page-commit");
    }
    post_telegraph::get_page(
        State(state),
        Extension(user),
        Query(post_telegraph::PageQuery {
            train_id: Some(id),
            page_number: Some(page_number),
        }),
    )
    .await
}

pub(crate) async fn delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<DeleteQuery>,
) -> Response {
    let Some(id) = query.train_id.filter(|value| !value.is_empty()) else {
        return business(202, "训练ID不能为空");
    };
    let Some(pool) = state.pool.as_ref() else {
        return business(500, "服务器错误");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return err(e, "delete-begin"),
    };
    let row = match sqlx::query(
        "SELECT id,create_user_id FROM t_post_telegraph_key_pat_train WHERE id=? LIMIT 1 FOR UPDATE",
    )
    .bind(&id)
    .fetch_optional(&mut *tx)
    .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return business(202, "未查询到训练"),
        Err(e) => return err(e, "delete-select"),
    };
    let id = row.get::<String, _>("id");
    if row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return business(207, "无权限");
    }
    for table in [
        "t_post_telegraph_key_pat_train_raw_page",
        "t_post_telegraph_key_pat_train_page_value",
        "t_post_telegraph_key_pat_train_more",
        "t_post_telegraph_key_pat_train_page",
    ] {
        if let Err(e) = delete_child_rows(&mut tx, table, &id).await {
            return err(e, "delete-child");
        }
    }
    if let Err(e) =
        sqlx::query("DELETE FROM t_post_telegraph_key_pat_train WHERE id=? AND create_user_id=?")
            .bind(&id)
            .bind(&user.id)
            .execute(&mut *tx)
            .await
    {
        return err(e, "delete-parent");
    }
    if let Err(e) = tx.commit().await {
        return err(e, "delete-commit");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", "")
}

#[cfg(test)]
#[path = "post_telegraph_writer_tests.rs"]
mod writer_tests;
