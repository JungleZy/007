use super::dto::{CaptureInterval, FinishPageRequest};
use rust_decimal::Decimal;
use serde_json::json;
use serde_json::Value;
use std::str::FromStr;

pub(super) fn parse_array(value: &str) -> Result<Vec<Value>, ()> {
    serde_json::from_str(value).map_err(|_| ())
}

pub(super) fn canonical_messages(
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

pub(super) fn same_raw_messages(
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

pub(super) fn valid_intervals(
    intervals: &[CaptureInterval],
    maximum_end: i64,
) -> Result<i64, &'static str> {
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

pub(super) fn check_no_overlap(mut pages: Vec<Vec<CaptureInterval>>) -> Result<(), &'static str> {
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

pub(super) enum SettlementError {
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

pub(super) fn invalid_stored(message: &'static str) -> SettlementError {
    SettlementError::Database(sqlx::Error::Protocol(message.into()))
}

#[derive(Default)]
pub(super) struct KeyStatistics {
    pub(super) pat_group: i64,
    pub(super) error: i64,
    pub(super) lack: i64,
    pub(super) more: i64,
    pub(super) lack_line: i64,
    pub(super) more_line: i64,
    pub(super) lack_group: i64,
    pub(super) more_group: i64,
    pub(super) alter_error: i64,
    pub(super) bunch_group: i64,
    pub(super) lack_gap: i64,
}

pub(super) struct SourceGroup {
    pub(super) page: i32,
    pub(super) sort: i32,
    pub(super) key: String,
    pub(super) text: Vec<u16>,
}

pub(super) struct CapturedGroup {
    pub(super) value: String,
    pub(super) time: String,
    pub(super) text: Vec<u16>,
    pub(super) tokens: Option<Vec<String>>,
}

pub(super) struct RebuiltGroup {
    pub(super) page: i32,
    pub(super) sort: i32,
    pub(super) key: Option<String>,
    pub(super) value: Option<String>,
    pub(super) time: Option<String>,
}

pub(super) fn string_array(text: &str) -> Result<Vec<String>, &'static str> {
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

pub(super) fn electronic_rule(content: &str) -> Result<Value, &'static str> {
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

pub(super) fn rule_decimal(
    object: &Value,
    key: &str,
    required: bool,
) -> Result<Decimal, &'static str> {
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

pub(super) fn corrected(text: &[u16]) -> &[u16] {
    text.iter()
        .rposition(|unit| *unit == u16::from(b'?'))
        .map_or(text, |index| &text[index + 1..])
}

pub(super) fn contains_units(text: &[u16], part: &[u16]) -> bool {
    part.is_empty()
        || (part.len() <= text.len() && text.windows(part.len()).any(|window| window == part))
}

pub(super) fn rebuilt(
    source: &SourceGroup,
    value: Option<String>,
    time: Option<String>,
) -> RebuiltGroup {
    RebuiltGroup {
        page: source.page,
        sort: source.sort,
        key: Some(source.key.clone()),
        value,
        time,
    }
}

pub(super) fn annotated(group: &CapturedGroup, annotation: &str) -> String {
    if let Some(body) = &group.tokens {
        let mut tokens = Vec::with_capacity(body.len() + 1);
        tokens.push(annotation);
        tokens.extend(body.iter().map(String::as_str));
        json!(tokens).to_string()
    } else {
        format!("[{},{}", json!(annotation), &group.value[1..])
    }
}

pub(super) fn utf16_array(units: &[u16]) -> String {
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
