use super::handkey_rules::{HandkeyRule, HandkeyScore};
use serde::{Deserialize, Serialize};
use serde_json::json;
use serde_json::Value;

#[derive(Deserialize, Serialize, Clone)]
pub(crate) struct PatLog {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) name: Option<String>,
    pub(crate) key: i32,
    pub(crate) value: f64,
}

pub(crate) fn field_string<'a>(
    object: &'a serde_json::Map<String, Value>,
    name: &str,
) -> Result<&'a str, &'static str> {
    object
        .get(name)
        .and_then(Value::as_str)
        .ok_or("拍发内容字段无效")
}

#[derive(Default)]
struct RawGroup {
    keys: Vec<String>,
    pub(super) logs: Vec<Vec<PatLog>>,
    times: Vec<Value>,
    pub(super) values: Vec<Value>,
}

#[derive(Default)]
pub(crate) struct ResolvedGroup {
    pub(crate) key: String,
    pub(crate) logs: Vec<Vec<PatLog>>,
    pub(crate) times: Vec<Value>,
    pub(crate) values: Vec<Value>,
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

/// A parsed `List<Double>` as Gson prints it again: every element is a floating-point number.
fn java_doubles(value: &Value) -> Value {
    match value {
        Value::Array(items) => Value::Array(
            items
                .iter()
                .map(|item| {
                    item.as_f64()
                        .map_or_else(|| item.clone(), |number| json!(number))
                })
                .collect(),
        ),
        other => other.clone(),
    }
}

fn group_slice(group: &ResolvedGroup, key: String, start: usize, length: usize) -> ResolvedGroup {
    ResolvedGroup {
        key,
        logs: (start..start + length)
            .map(|i| group.logs.get(i).cloned().unwrap_or_default())
            .collect(),
        // Java's resolver stores split/corrected symbol arrays as JSON strings. Durations were
        // parsed as `List<Double>`, so Gson writes whole milliseconds as `300.0`.
        times: (start..start + length)
            .map(|i| {
                Value::String(java_doubles(group.times.get(i).unwrap_or(&Value::Null)).to_string())
            })
            .collect(),
        values: (start..start + length)
            .map(|i| Value::String(group.values.get(i).unwrap_or(&Value::Null).to_string()))
            .collect(),
    }
}

pub(crate) fn resolve_groups(
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

pub(crate) fn score_timing(
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
