use super::dto::{CaptureInterval, PageMessage};
use super::parse::{
    annotated, contains_units, corrected, rebuilt, string_array, utf16_array, valid_intervals,
    CapturedGroup, KeyStatistics, RebuiltGroup, SourceGroup,
};
use serde_json::json;
use serde_json::Value;
use sqlx::mysql::MySqlRow;
use sqlx::Row;

// Port KeyPatUtils.handle's ordered lookahead and result annotations; raw rows stay immutable.
pub(super) fn align_page(
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

pub(super) fn captured_groups(
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
