use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rand::Rng;
use rust_decimal::prelude::ToPrimitive;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sqlx::Row;

#[derive(Deserialize)]
pub(crate) struct AddRequest {
    name: Option<String>,
    #[serde(rename = "isCable")]
    is_cable: Option<i32>,
    #[serde(rename = "type")]
    train_type: Option<i32>,
    #[serde(rename = "codeSort")]
    code_sort: Option<bool>,
    #[serde(rename = "isRandom")]
    is_random: Option<bool>,
    #[serde(rename = "messageNumber")]
    message_number: Option<i32>,
    #[serde(rename = "ruleId")]
    rule_id: Option<String>,
    #[serde(rename = "isAverage")]
    is_average: Option<bool>,
    #[serde(rename = "cableId")]
    cable_id: Option<String>,
    #[serde(rename = "startPage")]
    start_page: Option<i32>,
}

#[derive(Deserialize)]
pub(crate) struct ActionRequest {
    id: Option<String>,
    attempt: Option<i32>,
}
#[derive(Deserialize)]
pub(crate) struct DeleteQuery {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
}

#[derive(Deserialize, Serialize, Clone, PartialEq)]
struct CaptureInterval {
    #[serde(rename = "startedMs")]
    started_ms: i64,
    #[serde(rename = "endedMs")]
    ended_ms: i64,
}

#[derive(Deserialize, Serialize, Clone)]
struct PatLog {
    key: i32,
    value: f64,
}

#[derive(Deserialize)]
pub(crate) struct ContentQuery {
    id: Option<String>,
    #[serde(rename = "floorNumber")]
    floor_number: Option<i32>,
}

#[derive(Deserialize)]
pub(crate) struct ContentUpdate {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
    #[serde(rename = "floorNumber")]
    floor_number: Option<i32>,
    #[serde(rename = "messageBody")]
    message_body: Option<Vec<Value>>,
    standard: Option<Vec<Value>>,
    #[serde(rename = "finishInfo")]
    finish_info: Option<String>,
    attempt: Option<i32>,
    #[serde(rename = "captureIntervals")]
    capture_intervals: Option<Vec<CaptureInterval>>,
}

#[derive(Deserialize)]
pub(crate) struct AddContentRequest {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
    #[serde(rename = "messageBody")]
    message_body: Option<Vec<Vec<Value>>>,
}

fn err(e: sqlx::Error, op: &'static str) -> Response {
    tracing::warn!(error=%e,operation=op,"post telegram write failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

fn invalid(message: &'static str) -> Response {
    envelope_response(StatusCode::OK, 202, message, "")
}

fn field_string<'a>(
    object: &'a serde_json::Map<String, Value>,
    name: &str,
) -> Result<&'a str, &'static str> {
    object
        .get(name)
        .and_then(Value::as_str)
        .ok_or("拍发内容字段无效")
}

fn content_item_json(
    object: &serde_json::Map<String, Value>,
) -> Result<(String, String, String, String, String), &'static str> {
    let mores_key = field_string(object, "moresKey")?.to_owned();
    let mores_value = field_string(object, "moresValue")?.to_owned();
    let mores_time = field_string(object, "moresTime")?.to_owned();
    let pat_keys = field_string(object, "patKeys")?.to_owned();
    let pat_logs = field_string(object, "patLogs")?.to_owned();
    serde_json::from_str::<Vec<String>>(&mores_key).map_err(|_| "拍发内容字段无效")?;
    serde_json::from_str::<Vec<Vec<i32>>>(&mores_value).map_err(|_| "拍发内容字段无效")?;
    serde_json::from_str::<Vec<Vec<f64>>>(&mores_time).map_err(|_| "拍发内容字段无效")?;
    serde_json::from_str::<Vec<Vec<PatLog>>>(&pat_logs).map_err(|_| "拍发内容字段无效")?;
    Ok((mores_key, mores_value, mores_time, pat_keys, pat_logs))
}

fn count_characters(body: &[Value]) -> Result<i64, &'static str> {
    let mut count = 0_i64;
    for item in body {
        let Some(object) = item.as_object() else {
            return Err("拍发组无效");
        };
        let (_, mores_value, mores_time, pat_keys, pat_logs) = content_item_json(object)?;
        let keys: Vec<String> = serde_json::from_str(&pat_keys).map_err(|_| "拍发内容字段无效")?;
        let values: Vec<Vec<i32>> =
            serde_json::from_str(&mores_value).map_err(|_| "拍发内容字段无效")?;
        let times: Vec<Vec<f64>> =
            serde_json::from_str(&mores_time).map_err(|_| "拍发内容字段无效")?;
        let logs: Vec<Vec<PatLog>> =
            serde_json::from_str(&pat_logs).map_err(|_| "拍发内容字段无效")?;
        if keys.len() != values.len() || keys.len() != times.len() || keys.len() != logs.len() {
            return Err("拍发字符、码值与时长记录不一致");
        }
        for index in 0..keys.len() {
            if values[index].len() != times[index].len() {
                return Err("拍发字符、码值与时长记录不一致");
            }
            if values[index].iter().any(|value| *value != 0 && *value != 1)
                || times[index]
                    .iter()
                    .any(|value| !value.is_finite() || *value < 0.0)
            {
                return Err("拍发码值或时长记录无效");
            }
            let mut symbol = 0_usize;
            for event in &logs[index] {
                if !(0..=4).contains(&event.key) || !event.value.is_finite() || event.value < 0.0 {
                    return Err("拍发事件类型或时长无效");
                }
                if event.key < 2 {
                    if symbol >= values[index].len()
                        || event.key != values[index][symbol]
                        || event.value != times[index][symbol]
                    {
                        return Err("拍发事件与码值时长不一致");
                    }
                    symbol += 1;
                }
            }
            if symbol != values[index].len() {
                return Err("拍发事件缺少码值记录");
            }
            count = count
                .checked_add(
                    keys[index]
                        .chars()
                        .filter(|character| {
                            !character.is_whitespace()
                                && *character != '?'
                                && *character != '.'
                                && *character != '。'
                        })
                        .count() as i64,
                )
                .ok_or("拍发内容过大")?;
        }
    }
    Ok(count)
}

fn capture_duration(intervals: &[CaptureInterval], maximum_end: i64) -> Result<i64, &'static str> {
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
            .ok_or("采集时长无效")?;
        previous_end = interval.ended_ms;
    }
    Ok(duration)
}
fn require_no_overlap(pages: &[Vec<CaptureInterval>]) -> Result<(), &'static str> {
    let mut intervals = pages
        .iter()
        .flat_map(|page| page.iter().cloned())
        .collect::<Vec<_>>();
    intervals.sort_by_key(|interval| interval.started_ms);
    let mut previous_end = 0_i64;
    for interval in intervals {
        if interval.started_ms < previous_end || interval.ended_ms <= interval.started_ms {
            return Err("已保存页的采集时间轴重叠或无效");
        }
        previous_end = interval.ended_ms;
    }
    Ok(())
}

fn validate_standard(standard: &[Value], has_body: bool) -> Result<(), &'static str> {
    if has_body && standard.is_empty() {
        return Err("点划基准记录不能为空");
    }
    for item in standard {
        let Some(object) = item.as_object() else {
            return Err("点划自校准基准记录无效");
        };
        for name in ["dot", "line", "codeGap", "wordGap", "groupGap"] {
            if object
                .get(name)
                .and_then(Value::as_i64)
                .is_none_or(|value| value <= 0)
            {
                return Err("点划自校准基准记录无效");
            }
        }
    }
    Ok(())
}

#[derive(Clone, Copy)]
struct TimingRule {
    base: i64,
    low: i64,
    high: i64,
    max: i64,
}

struct HandkeyRule {
    timing: [TimingRule; 5],
    other: [(i64, i64); 6],
    speed: TimingRule,
}

fn rule_integer(value: &Value, required: bool) -> Result<i64, &'static str> {
    if value.is_null() && !required {
        return Ok(0);
    }
    let number = value.as_f64().ok_or("评分规则必须包含有效非负整数")?;
    if number < 0.0 || number > i32::MAX as f64 || number.fract() != 0.0 {
        return Err("评分规则必须包含有效非负整数");
    }
    Ok(number as i64)
}

fn timing_rule(value: &Value, speed: bool) -> Result<TimingRule, &'static str> {
    if !value.is_object() || (!value["type"].is_null() && !value["type"].is_boolean()) {
        return Err("评分规则必须是完整对象");
    }
    let rule = TimingRule {
        base: rule_integer(&value["base"], true)?,
        low: rule_integer(&value["l"], !speed)?,
        high: rule_integer(&value["r"], !speed)?,
        max: rule_integer(&value["max"], !speed)?,
    };
    if speed && rule.base == 0 {
        return Err("评分规则速率基准必须大于0");
    }
    Ok(rule)
}

fn handkey_rule(content: Option<&str>) -> Result<HandkeyRule, &'static str> {
    let root: Value = serde_json::from_str(content.ok_or("训练评分规则快照缺失，请新建训练")?)
        .map_err(|_| "评分规则必须是完整JSON对象")?;
    if root["rateUnit"].as_str() != Some("CHARACTERS_PER_MINUTE") {
        return Err("评分规则rateUnit必须明确为CHARACTERS_PER_MINUTE");
    }
    rule_integer(&root["skew"], true)?;
    let mut other = [(0, 0); 6];
    for (index, name) in [
        "errorCode",
        "quantoCode",
        "quantoGroup",
        "alterError",
        "quantoRow",
        "bunchGroup",
    ]
    .iter()
    .enumerate()
    {
        other[index] = (
            rule_integer(&root["other"][*name]["l"], true)?,
            rule_integer(&root["other"][*name]["max"], true)?,
        );
    }
    Ok(HandkeyRule {
        timing: [
            timing_rule(&root["code"]["dot"], false)?,
            timing_rule(&root["code"]["dash"], false)?,
            timing_rule(&root["gap"]["little"], false)?,
            timing_rule(&root["gap"]["middle"], false)?,
            timing_rule(&root["gap"]["large"], false)?,
        ],
        other,
        speed: timing_rule(&root["wpm"], true)?,
    })
}

#[derive(Default)]
struct HandkeyScore {
    correct: i64,
    errors: i64,
    total: i64,
    more_groups: i64,
    lack_groups: i64,
    words: i64,
    lines: i64,
    bunches: i64,
    alterations: i64,
    timing: [i64; 5],
    durations: [i64; 5],
    statistics: [[i64; 3]; 5],
}

#[derive(Default)]
struct RawGroup {
    keys: Vec<String>,
    logs: Vec<Vec<PatLog>>,
    times: Vec<Value>,
    values: Vec<Value>,
}

#[derive(Default)]
struct ResolvedGroup {
    key: String,
    logs: Vec<Vec<PatLog>>,
    times: Vec<Value>,
    values: Vec<Value>,
}

fn parse_groups(body: &[Value]) -> Result<Vec<RawGroup>, &'static str> {
    let mut groups = Vec::with_capacity(body.len());
    for item in body {
        let object = item.as_object().ok_or("拍发组无效")?;
        groups.push(RawGroup {
            keys: serde_json::from_str(field_string(object, "patKeys")?)
                .map_err(|_| "拍发字符记录无效")?,
            logs: serde_json::from_str(field_string(object, "patLogs")?)
                .map_err(|_| "拍发事件记录无效")?,
            times: serde_json::from_str(field_string(object, "moresTime")?)
                .map_err(|_| "拍发时长记录无效")?,
            values: serde_json::from_str(field_string(object, "moresValue")?)
                .map_err(|_| "拍发码值记录无效")?,
        });
    }
    // TickerPatUtils.handleMessageBody coalesces partial groups before correction parsing.
    for index in 0..groups.len() {
        while groups[index].keys.len() < 4 && index + 1 < groups.len() {
            let current = &groups[index];
            let next = &groups[index + 1];
            let alphanumeric = |keys: &[String]| {
                keys.iter()
                    .filter(|key| {
                        key.chars()
                            .next()
                            .is_some_and(|c| c.is_ascii_alphanumeric())
                    })
                    .count()
            };
            let next_count = alphanumeric(&next.keys);
            if next.logs.is_empty()
                || next.keys.is_empty()
                || next.keys.iter().any(|key| key == "?")
                || (next_count > 0
                    && (current.keys.iter().any(|key| key == "?")
                        || (current.keys.len() >= 2 && current.keys.iter().all(|key| key == "#"))))
                || alphanumeric(&current.keys) + next_count > 4
            {
                break;
            }
            let mut next = groups.remove(index + 1);
            groups[index].keys.append(&mut next.keys);
            groups[index].logs.append(&mut next.logs);
            groups[index].times.append(&mut next.times);
            groups[index].values.append(&mut next.values);
            groups.push(RawGroup::default());
        }
    }
    Ok(groups)
}

fn group_slice(group: &ResolvedGroup, key: String, start: usize, length: usize) -> ResolvedGroup {
    ResolvedGroup {
        key,
        logs: (start..start + length)
            .map(|i| group.logs.get(i).cloned().unwrap_or_default())
            .collect(),
        // Java's resolver stores split/corrected symbol arrays as JSON strings.
        times: (start..start + length)
            .map(|i| Value::String(group.times.get(i).unwrap_or(&Value::Null).to_string()))
            .collect(),
        values: (start..start + length)
            .map(|i| Value::String(group.values.get(i).unwrap_or(&Value::Null).to_string()))
            .collect(),
    }
}

fn resolve_groups(
    body: &[Value],
    rule: &HandkeyRule,
    score: &mut HandkeyScore,
) -> Result<Vec<ResolvedGroup>, &'static str> {
    let groups = parse_groups(body)?;
    let mut result: Vec<ResolvedGroup> = Vec::with_capacity(groups.len());
    let mut groups = groups
        .into_iter()
        .filter_map(|group| {
            let key = group.keys.concat();
            (!key.trim().is_empty()).then_some(ResolvedGroup {
                key,
                logs: group.logs,
                times: group.times,
                values: group.values,
            })
        })
        .enumerate()
        .peekable();
    while let Some((index, group)) = groups.next() {
        let length = group.key.encode_utf16().count();
        if length > 4 && length % 4 == 0 && !group.key.contains('?') {
            let chars: Vec<u16> = group.key.encode_utf16().collect();
            for offset in (0..length).step_by(4) {
                result.push(group_slice(
                    &group,
                    String::from_utf16_lossy(&chars[offset..offset + 4]),
                    offset,
                    4,
                ));
            }
            score.timing[4] += length as i64 / 3 * rule.timing[4].low;
        } else if group.key == "?" && index > 1 && groups.peek().is_some() {
            let (_, replacement) = groups.next().ok_or("改错记录缺少后组")?;
            *result.last_mut().ok_or("改错记录缺少前组")? = replacement;
        } else if group.key != "?" && group.key.contains('?') {
            let chars: Vec<u16> = group.key.encode_utf16().collect();
            let last = chars
                .iter()
                .rposition(|c| *c == b'?' as u16)
                .ok_or("改错记录无效")?
                + 1;
            if group.key.starts_with('?') && index > 0 {
                *result.last_mut().ok_or("改错记录缺少前组")? = group_slice(
                    &group,
                    String::from_utf16_lossy(&chars[last..]),
                    last,
                    length - last,
                );
            } else if group.key.ends_with('?') && groups.peek().is_some() {
                score.alterations += rule.other[3].0;
                let first = chars
                    .iter()
                    .position(|c| *c == b'?' as u16)
                    .ok_or("改错记录无效")?
                    + 1;
                if last > first + 1 {
                    result.push(group_slice(
                        &group,
                        String::from_utf16_lossy(&chars[first..last - 1]),
                        first,
                        last - first - 1,
                    ));
                } else {
                    result.push(group);
                }
            } else if last < length {
                result.push(group_slice(
                    &group,
                    String::from_utf16_lossy(&chars[last..]),
                    last,
                    length - last,
                ));
            } else {
                result.push(group);
            }
            score.alterations += rule.other[3].0;
        } else {
            result.push(group);
        }
    }
    Ok(result)
}

fn score_timing(
    group: &ResolvedGroup,
    index: usize,
    standards: &[Value],
    correct: bool,
    rule: &HandkeyRule,
    score: &mut HandkeyScore,
) {
    if group.key.is_empty() || standards.is_empty() {
        return;
    }
    let standard = &standards[(index / 10).min(standards.len() - 1)];
    for (character, logs) in group
        .logs
        .iter()
        .take(group.key.encode_utf16().count())
        .enumerate()
    {
        let skip = usize::from(index == 0 && character == 0);
        for (event_index, event) in logs.iter().skip(skip).enumerate() {
            let category = match event.key {
                0 => 0,
                1 => 1,
                2 if event_index == 0 && character == 0 => 4,
                2 if event_index == 0 => 3,
                2 => 2,
                _ => continue,
            };
            let value = i64::from(event.value.round() as i64 as i32);
            let baseline = standard[["dot", "line", "codeGap", "wordGap", "groupGap"][category]]
                .as_i64()
                .unwrap_or(0);
            let timing = rule.timing[category];
            let (bucket, deduction) = if value < baseline - timing.base {
                (
                    0,
                    if category == 3 {
                        timing.high
                    } else {
                        timing.low
                    },
                )
            } else if value > baseline + timing.base {
                (
                    1,
                    if category == 3 {
                        timing.low
                    } else {
                        timing.high
                    },
                )
            } else {
                (2, 0)
            };
            score.statistics[category][bucket] += 1;
            score.durations[category] =
                i64::from((score.durations[category] as i32).wrapping_add(value as i32));
            if !correct {
                score.timing[category] += deduction;
            }
        }
    }
}

#[derive(Default, Serialize)]
#[serde(rename_all = "camelCase")]
struct PageResolver {
    resolver_message: Vec<String>,
    resolver_pat_logs: Vec<String>,
    resolver_mores_value: Vec<String>,
    resolver_mores_time: Vec<String>,
    more_groups: Vec<Value>,
    more_line: Vec<Value>,
}

impl PageResolver {
    fn push(&mut self, key: &str, group: &ResolvedGroup) -> Result<(), &'static str> {
        self.resolver_message.push(key.to_owned());
        self.resolver_pat_logs
            .push(serde_json::to_string(&group.logs).map_err(|_| "拍发事件序列化失败")?);
        self.resolver_mores_value
            .push(serde_json::to_string(&group.values).map_err(|_| "拍发码值序列化失败")?);
        self.resolver_mores_time
            .push(serde_json::to_string(&group.times).map_err(|_| "拍发时长序列化失败")?);
        Ok(())
    }

    fn blanks(&mut self, count: usize) -> Result<(), &'static str> {
        for _ in 0..count {
            self.push("", &ResolvedGroup::default())?;
        }
        Ok(())
    }
}

fn compare_page(
    sources: &[String],
    body: &[Value],
    standards: &[Value],
    rule: &HandkeyRule,
    score: &mut HandkeyScore,
) -> Result<PageResolver, &'static str> {
    let groups = resolve_groups(body, rule, score)?;
    let mut output = PageResolver::default();
    let mut source_index = 0_usize;
    let mut index = 0_usize;
    let mut line_mode = false;
    while index < groups.len() {
        let group = &groups[index];
        let source = sources
            .get(source_index)
            .or_else(|| sources.last())
            .map(String::as_str)
            .unwrap_or("");
        if group.key == source {
            score.correct += 1;
            score_timing(group, index, standards, true, rule, score);
            output.push(&group.key, group)?;
            index += 1;
            source_index += 1;
            continue;
        }
        score.errors += 1;
        score_timing(group, index, standards, false, rule, score);
        if source_index % 10 == 0 {
            if source_index >= 10 && sources.get(source_index - 10) == Some(&group.key) {
                let matches = groups
                    .iter()
                    .skip(index + 1)
                    .take(9)
                    .filter(|next| {
                        sources
                            .iter()
                            .skip(source_index - 9)
                            .take(9)
                            .any(|original| original == &next.key)
                    })
                    .count();
                if matches >= 5 {
                    let end = (index + 10).min(groups.len());
                    output.more_line.push(json!({"point":source_index / 10,"message":groups[index..end].iter().map(|g| &g.key).collect::<Vec<_>>()}));
                    output.push(&group.key, group)?;
                    for (offset, next) in groups[index + 1..end].iter().enumerate() {
                        score_timing(next, index + offset + 1, standards, false, rule, score);
                    }
                    score.lines += 1;
                    line_mode = true;
                    index = end;
                    continue;
                }
            }
            let missing = (1..=2).find(|offset| {
                let next_source = source_index + offset * 10;
                sources.get(next_source) == Some(&group.key)
                    && (1..=9)
                        .filter(|step| {
                            groups
                                .get(index + step)
                                .zip(sources.get(next_source + step))
                                .is_some_and(|(next, original)| &next.key == original)
                        })
                        .count()
                        >= 5
            });
            if let Some(missing) = missing {
                output.blanks(missing * 10)?;
                output.push(&group.key, group)?;
                score.lines += missing as i64;
                source_index += missing * 10 + 1;
                index += 1;
                line_mode = true;
                continue;
            }
        }
        if source_index < sources.len() {
            let range = if line_mode { 10 } else { 4 };
            if let Some(more) = (1..=range).find(|offset| {
                groups
                    .get(index + offset)
                    .is_some_and(|next| next.key == source)
            }) {
                output.push(source, group)?;
                output.more_groups.push(json!({"point":source_index,"message":groups[index..index + more].iter().map(|g| &g.key).collect::<Vec<_>>()}));
                for offset in 1..=more {
                    score_timing(
                        &groups[index + offset],
                        index + offset,
                        standards,
                        false,
                        rule,
                        score,
                    );
                }
                score.more_groups += more as i64;
                source_index += 1;
                index += more + 1;
                continue;
            }
            if let Some(missing) =
                (1..=range).find(|offset| sources.get(source_index + offset) == Some(&group.key))
            {
                output.blanks(missing)?;
                output.push(&group.key, group)?;
                score.lack_groups += missing as i64;
                source_index += missing + 1;
                index += 1;
                continue;
            }
        }
        let bunch = sources.len() > 10
            && source_index < sources.len()
            && (source_index.checked_sub(10).and_then(|i| sources.get(i)) == Some(&group.key)
                || sources.get(source_index + 10) == Some(&group.key));
        if bunch {
            score.bunches += 1;
            output.push(&group.key, group)?;
        } else if group.key.encode_utf16().count() == 4 {
            if group.key.contains('#') {
                score.timing[3] += rule.timing[3].high;
            }
            output.push(&group.key, group)?;
        } else if groups.get(index + 1).is_some_and(|next| {
            group.key.encode_utf16().count() + next.key.encode_utf16().count() == 4
                && source == format!("{}{}", group.key, next.key)
        }) {
            let next = &groups[index + 1];
            let mut merged = ResolvedGroup {
                key: format!("{}{}", group.key, next.key),
                logs: group.logs.clone(),
                times: group.times.clone(),
                values: group.values.clone(),
            };
            merged.logs.extend_from_slice(&next.logs);
            merged.times.extend_from_slice(&next.times);
            merged.values.extend_from_slice(&next.values);
            score.timing[3] += rule.timing[3].low;
            output.push(&merged.key, &merged)?;
            index += 1;
        } else {
            score.words += 1;
            output.push(&group.key, group)?;
        }
        source_index += 1;
        index += 1;
    }
    score.total += groups.len() as i64;
    score.lines += (sources
        .len()
        .div_ceil(10)
        .saturating_sub(groups.len().div_ceil(10))) as i64;
    Ok(output)
}

fn scoring_result(
    score: &HandkeyScore,
    rule: &HandkeyRule,
    full_score: i32,
    speed: i64,
    empty: bool,
) -> (i64, Value, Value) {
    let mut statistics = serde_json::Map::new();
    for (index, name) in ["dot", "line", "code", "word", "group"].iter().enumerate() {
        let counts = score.statistics[index];
        for (bucket, suffix) in ["MinNumber", "MaxNumber", "PerfectNumber"]
            .iter()
            .enumerate()
        {
            statistics.insert(format!("{name}{suffix}"), json!(counts[bucket]));
        }
        let count: i64 = counts.iter().sum();
        let average = if count == 0 {
            0
        } else {
            (score.durations[index] + score.durations[index].signum() * (count / 2)) / count
        };
        statistics.insert(format!("{name}Avg"), json!(average));
    }
    let mut deductions = serde_json::Map::new();
    let mut total = i64::from(full_score);
    let cap = |value: i64, max: i64| if max == 0 { value } else { value.min(max) };
    for (index, (field, count)) in [
        ("dotMinScore", "dotMinNumber"),
        ("lineScore", "lineNumber"),
        ("codeGapScore", "codeNumber"),
        ("wordGapScore", "wordNumber"),
        ("groupGapScore", "groupNumber"),
    ]
    .iter()
    .enumerate()
    {
        let deduction = cap(score.timing[index], rule.timing[index].max);
        deductions.insert((*field).into(), json!(deduction));
        if !empty {
            deductions.insert((*count).into(), json!(score.timing[index]));
        }
        total -= deduction;
    }
    for (index, value, number, field, count) in [
        (
            0,
            score.errors * rule.other[0].0,
            score.errors,
            "errorWord",
            "errorWordNumber",
        ),
        (
            1,
            score.words * rule.other[1].0,
            score.words,
            "quantoCode",
            "quantoCodeNumber",
        ),
        (
            2,
            (score.more_groups + score.lack_groups) * rule.other[2].0,
            score.more_groups,
            "quantoGroup",
            "quantoGroupNumber",
        ),
        (
            3,
            score.alterations,
            score.alterations,
            "alterErrorScore",
            "alterErrorNumber",
        ),
        (
            4,
            score.lines * rule.other[4].0,
            score.lines,
            "quantoRow",
            "quantoRowNumber",
        ),
        (
            5,
            score.bunches * rule.other[5].0,
            score.bunches,
            "bunchGroup",
            "bunchGroupNumber",
        ),
    ] {
        let deduction = cap(value, rule.other[index].1);
        deductions.insert(field.into(), json!(deduction));
        if !empty {
            deductions.insert(count.into(), json!(number));
        }
        total -= deduction;
    }
    if empty {
        total = 0;
    } else {
        let difference = speed - rule.speed.base;
        let adjustment = difference
            * if difference > 0 {
                rule.speed.high
            } else {
                rule.speed.low
            };
        deductions.insert("wpmScore".into(), json!(adjustment));
        total += adjustment;
    }
    (total, Value::Object(statistics), Value::Object(deductions))
}

const TRAIN_VIEW_SQL: &str = "SELECT t.*,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') AS start_text,DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') AS end_text,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS create_text,CAST(GREATEST(COALESCE(TIMESTAMPDIFF(MICROSECOND,start_time,NOW(6)) DIV 1000,0),0) AS SIGNED) AS elapsed_ms FROM t_post_telegram_train t WHERE id=? LIMIT 1 FOR UPDATE";

fn training_view(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id":row.get::<String,_>("id"), "name":row.get::<Option<String>,_>("name"),
        "isCable":row.get::<Option<i32>,_>("is_cable"), "type":row.get::<Option<i32>,_>("type"),
        "codeSort":row.get::<Option<i32>,_>("code_sort") == Some(1),
        "isRandom":row.get::<Option<i32>,_>("is_random") == Some(1),
        "messageNumber":row.get::<Option<i32>,_>("message_number"),
        "startTime":row.get::<Option<String>,_>("start_text"), "endTime":row.get::<Option<String>,_>("end_text"),
        "createTime":row.get::<Option<String>,_>("create_text"), "createUser":row.get::<Option<String>,_>("create_user"),
        "status":row.get::<Option<i32>,_>("status"), "protocolVersion":row.get::<Option<i32>,_>("protocol_version"),
        "attempt":row.get::<Option<i32>,_>("attempt"), "serverElapsedMs":row.get::<i64,_>("elapsed_ms"),
        "activeMillis":row.get::<Option<i64>,_>("active_millis"), "validTime":row.get::<Option<i64>,_>("valid_time"),
        "speed":row.get::<Option<String>,_>("speed"), "accuracy":row.get::<Option<String>,_>("accuracy"),
        "errorNumber":row.get::<Option<i32>,_>("error_number"), "lack":row.get::<Option<i32>,_>("lack"),
        "score":row.get::<Option<String>,_>("score"), "fullScore":row.get::<Option<i32>,_>("full_score"),
        "ruleId":row.get::<Option<String>,_>("rule_id"), "ruleContent":row.get::<Option<String>,_>("rule_content"),
        "statisticInfo":row.get::<Option<String>,_>("statistic_info"), "deductInfo":row.get::<Option<String>,_>("deduct_info"),
        "floorNow":row.get::<Option<i32>,_>("floor_now"), "speedLog":row.get::<Option<String>,_>("speed_log")
    })
}

fn corrupt_saved(message: &str, train_id: &str) -> Response {
    tracing::warn!(
        train_id,
        reason = message,
        "saved telegram scoring data is invalid"
    );
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

async fn settle_train(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    train: &sqlx::mysql::MySqlRow,
    rule: &HandkeyRule,
    full_score: i32,
) -> Result<(), Response> {
    let id = train.get::<String, _>("id");
    let attempt = train.get::<Option<i32>, _>("attempt");
    let pages = sqlx::query("SELECT p.id,p.floor_number,p.attempt,p.message_body,p.standard,p.capture_intervals,CAST(TIMESTAMPDIFF(MICROSECOND,t.start_time,p.received_at) DIV 1000 AS SIGNED) AS received_elapsed_ms FROM t_post_telegram_train_floor_content_value p JOIN t_post_telegram_train t ON t.id=p.train_id WHERE p.train_id=? ORDER BY p.floor_number FOR UPDATE")
        .bind(&id).fetch_all(&mut **tx).await.map_err(|error| err(error, "finish-lock-pages"))?;
    let sources = sqlx::query("SELECT floor_number,mores_key FROM t_post_telegram_train_floor_content WHERE train_id=? ORDER BY floor_number,sort FOR UPDATE")
        .bind(&id).fetch_all(&mut **tx).await.map_err(|error| err(error, "finish-lock-sources"))?;
    let mut timelines = Vec::with_capacity(pages.len());
    let mut active_millis = 0_i64;
    let mut characters = 0_i64;
    let mut score = HandkeyScore::default();
    let mut resolvers = Vec::with_capacity(pages.len());
    for page in &pages {
        let received = page.get::<Option<i64>, _>("received_elapsed_ms");
        if page.get::<Option<i32>, _>("attempt") != attempt || received.is_none() {
            return Err(corrupt_saved("已保存页轮次或接收时间缺失", &id));
        }
        let timeline: Vec<CaptureInterval> = page
            .get::<Option<String>, _>("capture_intervals")
            .ok_or_else(|| corrupt_saved("已保存采集时间轴缺失", &id))
            .and_then(|value| {
                serde_json::from_str(&value).map_err(|error| corrupt_saved(&error.to_string(), &id))
            })?;
        let duration =
            capture_duration(&timeline, received.unwrap_or(0).max(0)).map_err(invalid)?;
        active_millis = active_millis
            .checked_add(duration)
            .ok_or_else(|| corrupt_saved("采集时长溢出", &id))?;
        let body: Vec<Value> = page
            .get::<Option<String>, _>("message_body")
            .ok_or_else(|| corrupt_saved("已保存拍发正文缺失", &id))
            .and_then(|value| {
                serde_json::from_str(&value).map_err(|error| corrupt_saved(&error.to_string(), &id))
            })?;
        let count = count_characters(&body).map_err(invalid)?;
        characters = characters
            .checked_add(count)
            .ok_or_else(|| corrupt_saved("拍发字数溢出", &id))?;
        if count > 0 && duration == 0 {
            return Err(invalid("拍发正文缺少有效采集区间"));
        }
        let standards: Vec<Value> = page
            .get::<Option<String>, _>("standard")
            .ok_or_else(|| corrupt_saved("已保存点划基准缺失", &id))
            .and_then(|value| {
                serde_json::from_str(&value).map_err(|error| corrupt_saved(&error.to_string(), &id))
            })?;
        validate_standard(&standards, count > 0).map_err(invalid)?;
        let floor = page
            .get::<Option<i32>, _>("floor_number")
            .ok_or_else(|| corrupt_saved("已保存页页码缺失", &id))?;
        let mut source_groups = Vec::new();
        for source in sources
            .iter()
            .filter(|source| source.get::<Option<i32>, _>("floor_number") == Some(floor))
        {
            let keys: Vec<String> = source
                .get::<Option<String>, _>("mores_key")
                .ok_or_else(|| corrupt_saved("已保存报底缺失", &id))
                .and_then(|value| {
                    serde_json::from_str(&value)
                        .map_err(|error| corrupt_saved(&error.to_string(), &id))
                })?;
            source_groups.push(keys.concat());
        }
        let resolver = compare_page(&source_groups, &body, &standards, rule, &mut score)
            .map_err(|message| corrupt_saved(message, &id))?;
        resolvers.push((
            page.get::<String, _>("id"),
            serde_json::to_string(&resolver)
                .map_err(|error| corrupt_saved(&error.to_string(), &id))?,
        ));
        timelines.push(timeline);
    }
    require_no_overlap(&timelines).map_err(invalid)?;
    let speed = super::scoring::rate_per_minute(characters, active_millis);
    let speed_number = speed
        .to_i64()
        .ok_or_else(|| corrupt_saved("训练速率超出有效范围", &id))?;
    if speed_number > i32::MAX as i64 {
        return Err(corrupt_saved("训练速率超出有效范围", &id));
    }
    if !pages.is_empty() {
        let message_number = train
            .get::<Option<i32>, _>("message_number")
            .ok_or_else(|| corrupt_saved("训练报文数量缺失", &id))?;
        for floor in 1..=(i64::from(message_number) + 99) / 100 {
            if !pages
                .iter()
                .any(|page| page.get::<Option<i32>, _>("floor_number") == Some(floor as i32))
            {
                score.lack_groups += 100.min(i64::from(message_number) - (floor - 1) * 100);
            }
        }
    }
    let (total, statistics, deductions) =
        scoring_result(&score, rule, full_score, speed_number, pages.is_empty());
    let errors = i32::try_from(score.errors).map_err(|_| corrupt_saved("错误组数溢出", &id))?;
    let lack = i32::try_from(score.lack_groups).map_err(|_| corrupt_saved("缺少组数溢出", &id))?;
    let accuracy = if score.correct == 0 {
        "0.00".into()
    } else {
        format!(
            "{:.2}",
            super::scoring::accuracy_percent(score.correct, score.total)
        )
    };
    let speed = if pages.is_empty() {
        "0.00".into()
    } else {
        speed.to_string()
    };
    for (page_id, resolver) in resolvers {
        sqlx::query("UPDATE t_post_telegram_train_floor_content_value SET resolver=? WHERE id=?")
            .bind(resolver)
            .bind(page_id)
            .execute(&mut **tx)
            .await
            .map_err(|error| err(error, "finish-save-resolver"))?;
    }
    sqlx::query("UPDATE t_post_telegram_train SET status=3,end_time=NOW(6),active_millis=?,valid_time=?,speed=?,error_number=?,accuracy=?,score=?,deduct_info=?,statistic_info=?,lack=? WHERE id=?")
        .bind(active_millis).bind(active_millis / 1000).bind(speed).bind(errors).bind(accuracy).bind(total.to_string())
        .bind(deductions.to_string()).bind(statistics.to_string()).bind(lack).bind(&id)
        .execute(&mut **tx).await.map_err(|error| err(error, "finish-save-result"))?;
    Ok(())
}

pub(crate) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<AddRequest>,
) -> Response {
    let Some(is_cable) = body.is_cable else {
        return invalid("是否使用固定报底不能为空");
    };
    let Some(message_number) = body.message_number.filter(|value| *value > 0) else {
        return invalid("报文数量不能为空");
    };
    if is_cable == 0 && body.train_type.is_none() {
        return invalid("报文类型不能为空");
    }
    let Some(rule_id) = body.rule_id.as_deref().filter(|id| !id.is_empty()) else {
        return invalid("评分规则不存在");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "add-begin"),
    };
    let grading =
        match sqlx::query("SELECT content,score FROM t_grading_rule WHERE id=? LIMIT 1 FOR UPDATE")
            .bind(rule_id)
            .fetch_optional(&mut *tx)
            .await
        {
            Ok(Some(row)) => row,
            Ok(None) => return invalid("评分规则不存在"),
            Err(error) => return err(error, "add-rule"),
        };
    let rule_content = grading.get::<Option<String>, _>("content");
    if let Err(message) = handkey_rule(rule_content.as_deref()) {
        return invalid(message);
    }
    let Some(full_score) = grading
        .get::<Option<i32>, _>("score")
        .filter(|score| *score >= 0)
    else {
        return invalid("评分规则满分必须为非负整数");
    };
    let mut pages: Vec<Vec<String>> = Vec::new();
    if is_cable == 0 {
        for (index, group) in generated_groups(
            message_number.min(200) as usize,
            body.train_type.unwrap_or(0),
            body.is_random == Some(true),
        )
        .into_iter()
        .enumerate()
        {
            if index % 100 == 0 {
                pages.push(Vec::new());
            }
            let keys = group
                .chars()
                .map(|character| character.to_string())
                .collect::<Vec<_>>();
            let key = match serde_json::to_string(&keys) {
                Ok(key) => key,
                Err(error) => return corrupt_saved(&error.to_string(), "new-train"),
            };
            pages[index / 100].push(key);
        }
    } else {
        let total_pages = (message_number / 100) as usize;
        if total_pages == 0 {
            return invalid("报文组数不足一页，无法建立训练");
        }
        let rows = match sqlx::query("SELECT floor_number,mores_key FROM t_cable_floor WHERE cable_id=? ORDER BY floor_number,sort FOR UPDATE")
            .bind(body.cable_id.as_deref()).fetch_all(&mut *tx).await {
            Ok(rows) => rows,
            Err(error) => return err(error, "add-cable-pages"),
        };
        let mut previous = None;
        for row in rows {
            let floor = row.get::<Option<i32>, _>("floor_number");
            if floor.is_none() {
                return corrupt_saved("固定报底页码缺失", "new-train");
            }
            if floor != previous {
                pages.push(Vec::new());
                previous = floor;
            }
            let key = row.get::<Option<String>, _>("mores_key");
            let Some(key) = key else {
                continue;
            };
            match serde_json::from_str::<Option<Vec<String>>>(&key) {
                Ok(Some(_)) => pages.last_mut().expect("cable page was inserted").push(key),
                Ok(None) => {}
                Err(error) => return corrupt_saved(&error.to_string(), "new-train"),
            }
        }
        let start = body.start_page.unwrap_or(1).saturating_sub(1).max(0) as usize;
        if start > 0 && start < pages.len() {
            pages.drain(..start);
        }
        if pages.len() < total_pages {
            return invalid("所选电缆可用楼层不足");
        }
        pages.truncate(total_pages);
    }
    let id = uuid::Uuid::new_v4().to_string();
    if let Err(error) = sqlx::query("INSERT INTO t_post_telegram_train (id,name,is_cable,type,code_sort,is_random,is_average,message_number,status,create_user,create_time,error_number,accuracy,protocol_version,attempt,full_score,active_millis,valid_time,speed,rule_id,rule_content,score) VALUES (?,?,?,?,?,?,?,?,0,?,NOW(6),0,'0.00',1,0,?,0,0,'0',?,?,?)")
        .bind(&id).bind(body.name.as_deref()).bind(is_cable).bind(body.train_type)
        .bind(i32::from(body.code_sort == Some(true))).bind(i32::from(body.is_random == Some(true)))
        .bind(i32::from(body.is_average == Some(true))).bind(message_number).bind(&user.id)
        .bind(full_score).bind(rule_id).bind(rule_content).bind(full_score.to_string()).execute(&mut *tx).await {
        return err(error, "add-train");
    }
    for (floor, groups) in pages.into_iter().enumerate() {
        for (sort, key) in groups.into_iter().enumerate() {
            if let Err(error) = sqlx::query("INSERT INTO t_post_telegram_train_floor_content (id,train_id,floor_number,sort,mores_key,mores_value,mores_time,pat_keys) VALUES (?,?,?,?,?,'[]','[]','[]')")
                .bind(uuid::Uuid::new_v4().to_string()).bind(&id).bind((floor + 1) as i32).bind(sort as i32).bind(key).execute(&mut *tx).await {
                return err(error, "add-source-content");
            }
        }
    }
    let data = match sqlx::query(TRAIN_VIEW_SQL)
        .bind(&id)
        .fetch_one(&mut *tx)
        .await
    {
        Ok(row) => training_view(&row),
        Err(error) => return err(error, "add-result"),
    };
    match tx.commit().await {
        Ok(()) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(error) => err(error, "add-commit"),
    }
}

pub(crate) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    action(state, user, body, 1).await
}

pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    action(state, user, body, 3).await
}

pub(crate) async fn stop(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    let Some(id) = body.id else {
        return invalid("请求参数错误");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "stop-begin"),
    };
    let row = match sqlx::query("SELECT create_user,status,protocol_version,attempt FROM t_post_telegram_train WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(&id)
        .fetch_optional(&mut *tx)
        .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return invalid("未查询到训练"),
        Err(error) => return err(error, "stop-lock"),
    };
    if row.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    if row.get::<Option<i32>, _>("protocol_version") != Some(1)
        || body.attempt != row.get::<Option<i32>, _>("attempt")
    {
        return invalid("训练轮次已变化，请重新加载");
    }
    if row.get::<Option<i32>, _>("status") != Some(1) {
        return invalid("训练状态不是进行中");
    }
    for sql in [
        "DELETE FROM t_post_telegram_train_floor_content_value WHERE train_id=?",
        "UPDATE t_post_telegram_train_floor_content SET mores_value='[]',mores_time='[]',pat_keys='[]' WHERE train_id=?",
    ] {
        if let Err(error) = sqlx::query(sql).bind(&id).execute(&mut *tx).await {
            return err(error, "stop-clear-content");
        }
    }
    if let Err(error) = sqlx::query("UPDATE t_post_telegram_train SET status=0,floor_now=1,speed_log=NULL,error_number=0,accuracy='0.00',attempt=attempt+1,start_time=NULL,end_time=NULL,valid_time=0,active_millis=0,speed='0.00',score=COALESCE(full_score,'0'),finish_info=NULL,statistic_info=NULL,deduct_info=NULL,lack=0 WHERE id=?")
        .bind(&id)
        .execute(&mut *tx)
        .await
    {
        return err(error, "stop-update");
    }
    if let Err(error) = tx.commit().await {
        return err(error, "stop-commit");
    }
    envelope_response(StatusCode::OK, 200, "ok", "")
}

async fn action(state: AppState, user: AuthUser, body: ActionRequest, status: i32) -> Response {
    let Some(id) = body.id.filter(|id| !id.is_empty()) else {
        return invalid("请求参数错误");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "action-begin"),
    };
    let train = match sqlx::query(TRAIN_VIEW_SQL)
        .bind(&id)
        .fetch_optional(&mut *tx)
        .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return invalid("未查询到训练"),
        Err(error) => return err(error, "action-lock"),
    };
    if train.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let protocol = train.get::<Option<i32>, _>("protocol_version");
    let terminal = train.get::<Option<i32>, _>("status") == Some(3);
    if (status == 1 || !terminal) && protocol != Some(1) {
        return invalid("旧训练缺少原始采集协议，请终止旧训练并新建");
    }
    if protocol == Some(1)
        && (body.attempt.is_none() || body.attempt != train.get::<Option<i32>, _>("attempt"))
    {
        return envelope_response(StatusCode::OK, 208, "训练轮次已变化，请重新加载训练", "");
    }
    if terminal {
        if status == 1 {
            return envelope_response(StatusCode::OK, 208, "已完成训练不可重新开始", "");
        }
        let data = training_view(&train);
        if let Err(error) = tx.commit().await {
            return err(error, "finish-idempotent-commit");
        }
        return envelope_json(StatusCode::OK, 200, Some(data), "ok", "");
    }
    if status == 3
        && (train.get::<Option<i32>, _>("status") != Some(1)
            || train.get::<Option<String>, _>("start_text").is_none())
    {
        return invalid("训练不在进行中");
    }
    let rule = match handkey_rule(train.get::<Option<String>, _>("rule_content").as_deref()) {
        Ok(rule) => rule,
        Err(message) => return invalid(message),
    };
    let Some(full_score) = train
        .get::<Option<i32>, _>("full_score")
        .filter(|score| *score >= 0)
    else {
        return invalid("训练满分快照无效，请新建训练");
    };
    if status == 1 {
        if train.get::<Option<i32>, _>("status") == Some(0) {
            if let Err(error) = sqlx::query(
                "UPDATE t_post_telegram_train SET status=1,start_time=NOW(6) WHERE id=?",
            )
            .bind(&id)
            .execute(&mut *tx)
            .await
            {
                return err(error, "begin-update");
            }
        }
    } else if let Err(response) = settle_train(&mut tx, &train, &rule, full_score).await {
        return response;
    }
    let result = match sqlx::query(TRAIN_VIEW_SQL)
        .bind(&id)
        .fetch_one(&mut *tx)
        .await
    {
        Ok(row) => training_view(&row),
        Err(error) => return err(error, "action-result"),
    };
    match tx.commit().await {
        Ok(()) => envelope_json(StatusCode::OK, 200, Some(result), "ok", ""),
        Err(error) => err(error, "action-commit"),
    }
}

pub(crate) async fn delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<DeleteQuery>,
) -> Response {
    let Some(id) = query.train_id.filter(|value| !value.is_empty()) else {
        return invalid("训练ID不能为空");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "delete-begin"),
    };
    let row = match sqlx::query(
        "SELECT create_user FROM t_post_telegram_train WHERE id=? LIMIT 1 FOR UPDATE",
    )
    .bind(&id)
    .fetch_optional(&mut *tx)
    .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return invalid("未查询到训练"),
        Err(error) => return err(error, "delete-lock"),
    };
    if row.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    for sql in [
        "DELETE FROM t_post_telegram_train_floor_content_value WHERE train_id=?",
        "DELETE FROM t_post_telegram_train_floor_content WHERE train_id=?",
    ] {
        if let Err(error) = sqlx::query(sql).bind(&id).execute(&mut *tx).await {
            return err(error, "delete-content");
        }
    }
    let deleted =
        match sqlx::query("DELETE FROM t_post_telegram_train WHERE id=? AND create_user=?")
            .bind(&id)
            .bind(&user.id)
            .execute(&mut *tx)
            .await
        {
            Ok(result) => result.rows_affected(),
            Err(error) => return err(error, "delete-train"),
        };
    if deleted != 1 {
        return err(sqlx::Error::RowNotFound, "delete-train-missing");
    }
    if let Err(error) = tx.commit().await {
        return err(error, "delete-commit");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", "")
}

pub(crate) async fn content(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ContentQuery>,
) -> Response {
    let Some(train_id) = body.id.filter(|value| !value.is_empty()) else {
        return invalid("请求参数错误");
    };
    let Some(floor_number) = body.floor_number.filter(|value| *value >= 1) else {
        return invalid("页码不能为空");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let train = match sqlx::query("SELECT protocol_version,attempt,status,message_number,create_user,TIMESTAMPDIFF(MICROSECOND,start_time,NOW(6)) DIV 1000 AS elapsed_ms FROM t_post_telegram_train WHERE id=? LIMIT 1")
        .bind(&train_id).fetch_optional(pool).await {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到训练", ""),
        Err(error) => return err(error, "content train"),
    };
    if train.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let message_number = train.get::<Option<i32>, _>("message_number").unwrap_or(0);
    if floor_number > (message_number + 99) / 100 {
        return invalid("页码超出训练范围");
    }
    if train.get::<Option<i32>, _>("status") != Some(3)
        && train.get::<Option<i32>, _>("protocol_version") != Some(1)
    {
        return invalid("旧训练缺少原始采集协议，请终止旧训练并新建");
    }
    let rows = match sqlx::query("SELECT id,mores_key,mores_value,mores_time,pat_keys FROM t_post_telegram_train_floor_content WHERE train_id=? AND floor_number=? ORDER BY sort")
        .bind(&train_id).bind(floor_number).fetch_all(pool).await {
        Ok(rows) => rows,
        Err(error) => return err(error, "content rows"),
    };
    let value = match sqlx::query("SELECT message_body,standard,finish_info,resolver,capture_intervals FROM t_post_telegram_train_floor_content_value WHERE train_id=? AND floor_number=? LIMIT 1")
        .bind(&train_id).bind(floor_number).fetch_optional(pool).await {
        Ok(value) => value,
        Err(error) => return err(error, "content value"),
    };
    let message_key: Vec<Value> = rows.iter().map(|row| json!({
        "id": row.get::<Option<String>, _>("id"),
        "moresKey": row.get::<Option<String>, _>("mores_key"),
        "moresValue": row.get::<Option<String>, _>("mores_value").unwrap_or_else(|| "[]".into()),
        "moresTime": row.get::<Option<String>, _>("mores_time").unwrap_or_else(|| "[]".into()),
        "patKeys": row.get::<Option<String>, _>("pat_keys").unwrap_or_else(|| "[]".into()),
        "patLogs": "[]"
    })).collect();
    let generated_body = serde_json::to_string(&message_key).unwrap_or_else(|_| "[]".into());
    let (message_body, standard, finish_info, resolver, capture_intervals, submitted) = match value
    {
        Some(row) => (
            row.get::<Option<String>, _>("message_body")
                .unwrap_or_else(|| "[]".into()),
            row.get::<Option<String>, _>("standard")
                .unwrap_or_else(|| "[]".into()),
            row.get::<Option<String>, _>("finish_info")
                .unwrap_or_else(|| "[]".into()),
            row.get::<Option<String>, _>("resolver").unwrap_or_default(),
            row.get::<Option<String>, _>("capture_intervals")
                .unwrap_or_else(|| "[]".into()),
            true,
        ),
        None => (
            generated_body,
            "[]".into(),
            "[]".into(),
            String::new(),
            "[]".into(),
            false,
        ),
    };
    let saved_capture_intervals = match serde_json::from_str::<Value>(&capture_intervals) {
        Ok(value) => value,
        Err(_) => return invalid("已保存采集时间轴无效"),
    };
    let server_elapsed_ms = train
        .get::<Option<i64>, _>("elapsed_ms")
        .unwrap_or(0)
        .max(0);
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({
            "protocolVersion": train.get::<Option<i32>, _>("protocol_version").unwrap_or(0),
            "attempt": train.get::<Option<i32>, _>("attempt").unwrap_or(0),
            "serverElapsedMs": server_elapsed_ms,
            "submitted": submitted,
            "savedCaptureIntervals": saved_capture_intervals,
            "messageBody": message_body,
            "messageKey": message_key,
            "finishInfo": finish_info,
            "standard": standard,
            "resolver": resolver
        })),
        "ok",
        "",
    )
}

pub(crate) async fn save_content(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ContentUpdate>,
) -> Response {
    let Some(train_id) = body.train_id.filter(|value| !value.is_empty()) else {
        return invalid("训练ID不能为空");
    };
    let Some(floor_number) = body.floor_number.filter(|value| *value >= 1) else {
        return invalid("页码不能为空");
    };
    let Some(attempt) = body.attempt else {
        return invalid("训练轮次不能为空");
    };
    let Some(message_body) = body.message_body else {
        return invalid("拍发原始记录不能为空");
    };
    let Some(standard) = body.standard else {
        return invalid("点划基准记录不能为空");
    };
    let Some(capture_intervals) = body.capture_intervals else {
        return invalid("采集时间轴缺失");
    };
    let body_json = match serde_json::to_string(&message_body) {
        Ok(value) => value,
        Err(_) => return invalid("拍发原始记录无效"),
    };
    let standard_json = match serde_json::to_string(&standard) {
        Ok(value) => value,
        Err(_) => return invalid("点划基准记录无效"),
    };
    let capture_json = match serde_json::to_string(&capture_intervals) {
        Ok(value) => value,
        Err(_) => return invalid("采集时间轴无效"),
    };
    let characters = match count_characters(&message_body) {
        Ok(value) => value,
        Err(message) => return invalid(message),
    };
    if let Err(message) = validate_standard(&standard, characters > 0) {
        return invalid(message);
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "start save content"),
    };
    let train = match sqlx::query("SELECT protocol_version,attempt,status,message_number,start_time,TIMESTAMPDIFF(MICROSECOND,start_time,NOW(6)) DIV 1000 AS elapsed_ms,create_user FROM t_post_telegram_train WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(&train_id).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => row,
        Ok(None) => { let _ = tx.rollback().await; return envelope_response(StatusCode::OK, 202, "未查询到训练", ""); }
        Err(error) => { let _ = tx.rollback().await; return err(error, "lock save train"); }
    };
    if train.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    if train.get::<Option<i32>, _>("protocol_version") != Some(1) {
        let _ = tx.rollback().await;
        return invalid("旧训练缺少原始采集协议，请终止旧训练并新建");
    }
    if train.get::<Option<i32>, _>("attempt") != Some(attempt) {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::OK, 208, "训练轮次已变化，请重新加载训练", "");
    }
    if train.get::<Option<i32>, _>("status") != Some(1)
        || train.get::<Option<i64>, _>("elapsed_ms").is_none()
    {
        let _ = tx.rollback().await;
        return invalid("训练不在进行中");
    }
    let elapsed_ms = train
        .get::<Option<i64>, _>("elapsed_ms")
        .unwrap_or(0)
        .max(0);
    let duration = match capture_duration(&capture_intervals, elapsed_ms) {
        Ok(value) => value,
        Err(message) => {
            let _ = tx.rollback().await;
            return invalid(message);
        }
    };
    if characters > 0 && duration == 0 {
        let _ = tx.rollback().await;
        return invalid("拍发正文缺少有效采集区间");
    }
    let existing = match sqlx::query("SELECT id,message_body,standard,capture_intervals,finish_info FROM t_post_telegram_train_floor_content_value WHERE train_id=? AND floor_number=? LIMIT 1 FOR UPDATE")
        .bind(&train_id).bind(floor_number).fetch_optional(&mut *tx).await {
        Ok(value) => value,
        Err(error) => { let _ = tx.rollback().await; return err(error, "lock save content value"); }
    };
    let finish_info = body.finish_info.unwrap_or_else(|| "[]".into());
    if let Some(row) = &existing {
        let same = row.get::<Option<String>, _>("message_body").as_deref()
            == Some(body_json.as_str())
            && row.get::<Option<String>, _>("standard").as_deref() == Some(standard_json.as_str())
            && row.get::<Option<String>, _>("capture_intervals").as_deref()
                == Some(capture_json.as_str())
            && row.get::<Option<String>, _>("finish_info").as_deref() == Some(finish_info.as_str());
        if same {
            if let Err(error) = tx.commit().await {
                return err(error, "commit idempotent content");
            }
            return envelope_response(StatusCode::OK, 200, "ok", "");
        }
        let previous: Vec<CaptureInterval> = match row.get::<Option<String>, _>("capture_intervals")
        {
            Some(value) => match serde_json::from_str(&value) {
                Ok(previous) => previous,
                Err(error) => {
                    tracing::warn!(%error, train_id = %train_id, floor_number, "stored capture intervals are invalid");
                    let _ = tx.rollback().await;
                    return envelope_response(
                        StatusCode::INTERNAL_SERVER_ERROR,
                        500,
                        "服务器错误",
                        "",
                    );
                }
            },
            None => {
                tracing::warn!(train_id = %train_id, floor_number, "stored capture intervals are missing");
                let _ = tx.rollback().await;
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
        if capture_intervals.len() <= previous.len()
            || previous
                .iter()
                .zip(&capture_intervals)
                .any(|(left, right)| left != right)
        {
            let _ = tx.rollback().await;
            return invalid("页内容已变化，必须读取已确认记录后继续采集");
        }
    }
    let other_values = match sqlx::query("SELECT capture_intervals FROM t_post_telegram_train_floor_content_value WHERE train_id=? AND floor_number<>? FOR UPDATE")
        .bind(&train_id).bind(floor_number).fetch_all(&mut *tx).await {
        Ok(rows) => rows,
        Err(error) => { let _ = tx.rollback().await; return err(error, "lock other capture values"); }
    };
    let mut pages = vec![capture_intervals.clone()];
    for row in other_values {
        let Some(value) = row.get::<Option<String>, _>("capture_intervals") else {
            tracing::warn!(train_id = %train_id, "stored page capture intervals are missing");
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        };
        let page = match serde_json::from_str::<Vec<CaptureInterval>>(&value) {
            Ok(page) => page,
            Err(error) => {
                tracing::warn!(%error, train_id = %train_id, "stored page capture intervals are invalid");
                let _ = tx.rollback().await;
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
        pages.push(page);
    }
    if let Err(message) = require_no_overlap(&pages) {
        let _ = tx.rollback().await;
        return invalid(message);
    }
    let source = match sqlx::query("SELECT id FROM t_post_telegram_train_floor_content WHERE train_id=? AND floor_number=? LIMIT 1")
        .bind(&train_id).bind(floor_number).fetch_optional(&mut *tx).await {
        Ok(source) => source,
        Err(error) => { let _ = tx.rollback().await; return err(error, "find source content"); }
    };
    if source.is_none() {
        let _ = tx.rollback().await;
        return invalid("训练报底不存在");
    }
    let write_result = if let Some(row) = existing {
        sqlx::query("UPDATE t_post_telegram_train_floor_content_value SET attempt=?,message_body=?,standard=?,finish_info=?,capture_intervals=?,received_at=NOW(6),resolver=NULL WHERE id=?")
            .bind(attempt).bind(&body_json).bind(&standard_json).bind(&finish_info).bind(&capture_json).bind(row.get::<String, _>("id")).execute(&mut *tx).await
    } else {
        sqlx::query("INSERT INTO t_post_telegram_train_floor_content_value (id,train_id,floor_number,attempt,message_body,standard,finish_info,capture_intervals,received_at,resolver) VALUES (?,?,?,?,?,?,?, ?,NOW(6),NULL)")
            .bind(uuid::Uuid::new_v4().to_string()).bind(&train_id).bind(floor_number).bind(attempt).bind(&body_json).bind(&standard_json).bind(&finish_info).bind(&capture_json).execute(&mut *tx).await
    };
    if let Err(error) = write_result {
        let _ = tx.rollback().await;
        return err(error, "write content value");
    }
    if let Err(error) = sqlx::query("UPDATE t_post_telegram_train SET floor_now=GREATEST(COALESCE(floor_now,1),LEAST(?,CEIL(message_number/100))) WHERE id=? AND create_user=?")
        .bind(floor_number + 1).bind(&train_id).bind(&user.id).execute(&mut *tx).await {
        let _ = tx.rollback().await;
        return err(error, "update content floor");
    }
    match tx.commit().await {
        Ok(()) => envelope_response(StatusCode::OK, 200, "ok", ""),
        Err(error) => err(error, "commit save content"),
    }
}

fn generated_groups(count: usize, train_type: i32, random: bool) -> Vec<String> {
    let alphabet: Vec<char> = match train_type {
        0 => ('0'..='9').collect(),
        1 => ('A'..='Z').collect(),
        _ => ('0'..='9').chain('A'..='Z').collect(),
    };
    let mut rng = rand::thread_rng();
    (0..count)
        .map(|index| {
            let mut output = String::with_capacity(4);
            if random {
                while output.chars().count() < 4 {
                    let character = alphabet[rng.gen_range(0..alphabet.len())];
                    if !output.contains(character) {
                        output.push(character);
                    }
                }
            } else {
                for offset in 0..4 {
                    output.push(alphabet[(index * 4 + offset) % alphabet.len()]);
                }
            }
            output
        })
        .collect()
}

pub(crate) async fn print_bottom_report(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ContentQuery>,
) -> Response {
    let Some(train_id) = body.id.filter(|value| !value.is_empty()) else {
        return invalid("请求参数错误");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "start print report"),
    };
    let train = match sqlx::query("SELECT message_number,type,is_random,status,protocol_version,create_user FROM t_post_telegram_train WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(&train_id).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => row,
        Ok(None) => { let _ = tx.rollback().await; return envelope_response(StatusCode::OK, 202, "未查询到训练", ""); }
        Err(error) => { let _ = tx.rollback().await; return err(error, "lock print train"); }
    };
    if train.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    if train.get::<Option<i32>, _>("status") != Some(3)
        && train.get::<Option<i32>, _>("protocol_version") != Some(1)
    {
        let _ = tx.rollback().await;
        return invalid("旧训练缺少原始采集协议，请终止旧训练并新建");
    }
    let Some(message_number) = train
        .get::<Option<i32>, _>("message_number")
        .filter(|value| *value > 0)
    else {
        let _ = tx.rollback().await;
        return invalid("训练报文数量缺失，无法打印报底");
    };
    let Some(train_type) = train.get::<Option<i32>, _>("type") else {
        let _ = tx.rollback().await;
        return invalid("报文类型缺失，无法打印报底");
    };
    let rows = match sqlx::query("SELECT id,floor_number,sort,mores_key FROM t_post_telegram_train_floor_content WHERE train_id=? ORDER BY floor_number,sort")
        .bind(&train_id).fetch_all(&mut *tx).await {
        Ok(rows) => rows,
        Err(error) => { let _ = tx.rollback().await; return err(error, "read print content"); }
    };
    let total_page = (message_number + 99) / 100;
    let latest_floor = rows
        .iter()
        .filter_map(|row| row.get::<Option<i32>, _>("floor_number"))
        .max()
        .unwrap_or(0);
    if train.get::<Option<i32>, _>("status") == Some(3) || latest_floor >= total_page {
        let result: Vec<String> = rows
            .iter()
            .filter_map(|row| row.get::<Option<String>, _>("mores_key"))
            .collect();
        if let Err(error) = tx.commit().await {
            return err(error, "commit print report");
        }
        return envelope_json(StatusCode::OK, 200, Some(json!(result)), "ok", "");
    }
    let existing_groups = rows.len() as i32;
    let remaining = (message_number - existing_groups).max(0) as usize;
    let groups = generated_groups(
        remaining,
        train_type,
        train.get::<Option<i32>, _>("is_random") == Some(1),
    );
    for (offset, group) in groups.iter().enumerate() {
        let absolute = existing_groups as usize + offset;
        let floor_number = (absolute / 100) as i32 + 1;
        let sort = (absolute % 100) as i32;
        let key = serde_json::to_string(
            &group
                .chars()
                .map(|character| character.to_string())
                .collect::<Vec<_>>(),
        )
        .unwrap_or_else(|_| "[]".into());
        if let Err(error) = sqlx::query("INSERT INTO t_post_telegram_train_floor_content (id,train_id,floor_number,sort,mores_key,mores_value,mores_time,pat_keys) VALUES (?,?,?,? ,?,'[]','[]','[]')")
            .bind(uuid::Uuid::new_v4().to_string()).bind(&train_id).bind(floor_number).bind(sort).bind(key).execute(&mut *tx).await {
            let _ = tx.rollback().await;
            return err(error, "write print content");
        }
    }
    let result_rows = match sqlx::query("SELECT mores_key FROM t_post_telegram_train_floor_content WHERE train_id=? ORDER BY floor_number,sort")
        .bind(&train_id).fetch_all(&mut *tx).await {
        Ok(rows) => rows,
        Err(error) => { let _ = tx.rollback().await; return err(error, "read generated report"); }
    };
    let result: Vec<String> = result_rows
        .iter()
        .filter_map(|row| row.get::<Option<String>, _>("mores_key"))
        .collect();
    match tx.commit().await {
        Ok(()) => envelope_json(StatusCode::OK, 200, Some(json!(result)), "ok", ""),
        Err(error) => err(error, "commit print report"),
    }
}

pub(crate) async fn add_content_value(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<AddContentRequest>,
) -> Response {
    let Some(train_id) = body.train_id.filter(|value| !value.is_empty()) else {
        return invalid("训练ID不能为空");
    };
    let Some(pages) = body.message_body else {
        return invalid("追加内容不能为空");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "start add content"),
    };
    let train = match sqlx::query("SELECT protocol_version,status,create_user FROM t_post_telegram_train WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(&train_id).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => row,
        Ok(None) => { let _ = tx.rollback().await; return envelope_response(StatusCode::OK, 202, "未查询到训练", ""); }
        Err(error) => { let _ = tx.rollback().await; return err(error, "lock add content train"); }
    };
    if train.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    if train.get::<Option<i32>, _>("protocol_version") != Some(1) {
        let _ = tx.rollback().await;
        return invalid("旧训练缺少原始采集协议，请终止旧训练并新建");
    }
    if train.get::<Option<i32>, _>("status") != Some(0) {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::OK, 208, "训练开始后不可追加报底", "");
    }
    let base_floor = match sqlx::query("SELECT floor_number FROM t_post_telegram_train_floor_content WHERE train_id=? ORDER BY floor_number DESC LIMIT 1")
        .bind(&train_id).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => row.get::<Option<i32>, _>("floor_number").unwrap_or(0),
        Ok(None) => 0,
        Err(error) => { let _ = tx.rollback().await; return err(error, "find add content floor"); }
    };
    for (page_offset, page) in pages.iter().enumerate() {
        if page.is_empty() {
            let _ = tx.rollback().await;
            return invalid("追加内容不能为空");
        }
        let floor_number = base_floor + page_offset as i32 + 1;
        for (sort, item) in page.iter().enumerate() {
            let Some(object) = item.as_object() else {
                let _ = tx.rollback().await;
                return invalid("追加内容无效");
            };
            let (mores_key, mores_value, _mores_time, pat_keys, _pat_logs) =
                match content_item_json(object) {
                    Ok(value) => value,
                    Err(message) => {
                        let _ = tx.rollback().await;
                        return invalid(message);
                    }
                };
            if let Err(error) = sqlx::query("INSERT INTO t_post_telegram_train_floor_content (id,train_id,floor_number,sort,mores_key,mores_value,mores_time,pat_keys) VALUES (?,?,?,?,?,?,?,?)")
                .bind(uuid::Uuid::new_v4().to_string()).bind(&train_id).bind(floor_number).bind(sort as i32).bind(mores_key).bind(mores_value).bind("[]").bind(pat_keys).execute(&mut *tx).await {
                let _ = tx.rollback().await;
                return err(error, "write add content");
            }
        }
    }
    let floors = match sqlx::query("SELECT floor_number FROM t_post_telegram_train_floor_content WHERE train_id=? GROUP BY floor_number ORDER BY floor_number")
        .bind(&train_id).fetch_all(&mut *tx).await {
        Ok(rows) => rows.iter().filter_map(|row| row.get::<Option<i32>, _>("floor_number")).collect::<Vec<_>>(),
        Err(error) => { let _ = tx.rollback().await; return err(error, "read add content result"); }
    };
    match tx.commit().await {
        Ok(()) => envelope_json(StatusCode::OK, 200, Some(json!(floors)), "ok", ""),
        Err(error) => err(error, "commit add content"),
    }
}
