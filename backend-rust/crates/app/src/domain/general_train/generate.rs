use super::common::*;
use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;
use axum::http::StatusCode;
use axum::response::Response;
use serde_json::json;
use serde_json::Value;
use sqlx::MySqlConnection;

pub(super) fn generate_groups(train: &Value, domain: Domain, count: usize) -> Outcome<Vec<String>> {
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

pub(super) fn frozen_rule(train: &Value, domain: Domain) -> Outcome<Value> {
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
pub(super) async fn participant_action(
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
pub(super) async fn capture_bound(
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

pub(super) async fn online(
    state: AppState,
    actor: AuthUser,
    id: String,
    domain: Domain,
) -> Response {
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
            Ok(json!(
                crate::ws::notify::general_online_users(kind, &id).await
            ))
        }
        .await,
    )
}

pub(super) fn capture_intervals(value: &Value) -> Outcome<Vec<(i64, i64)>> {
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
pub(super) fn interval_duration(intervals: &[(i64, i64)], maximum: i64) -> Outcome<i64> {
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

pub(super) fn measure_submission(
    body: &Value,
    domain: Domain,
    train_type: Option<i64>,
) -> Outcome<(usize, f64)> {
    if matches!(domain, Domain::Telex) {
        let text = body["patValue"]
            .as_str()
            .ok_or_else(|| invalid("页面内容不能为空"))?;
        return Ok((
            crate::shared::telex_capture::character_count(text, train_type),
            0.0,
        ));
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

pub(super) fn percentage(count: i64, total: i64) -> i64 {
    if total <= 0 {
        0
    } else {
        ((count as f64 / total as f64) * 100.0).round() as i64
    }
}

pub(super) async fn save_generated_group(
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
pub(super) fn numeric_pairs(count: usize, opposite: bool) -> Vec<String> {
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
