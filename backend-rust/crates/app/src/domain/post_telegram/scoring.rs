use super::parse::{resolve_groups, score_timing, ResolvedGroup};
use super::rules::{HandkeyRule, HandkeyScore};
use serde::Serialize;
use serde_json::json;
use serde_json::Value;

#[derive(Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PageResolver {
    resolver_message: Vec<String>,
    resolver_pat_logs: Vec<String>,
    resolver_mores_value: Vec<String>,
    resolver_mores_time: Vec<String>,
    pub(crate) more_groups: Vec<Value>,
    pub(crate) more_line: Vec<Value>,
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

pub(crate) fn compare_page(
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

pub(crate) fn scoring_result(
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
