use super::align::{align_page, captured_groups};
use super::dto::{business, err, ActionRequest, CaptureInterval, PageMessage};
use super::parse::{
    check_no_overlap, electronic_rule, invalid_stored, rule_decimal, string_array, valid_intervals,
    KeyStatistics, SettlementError, SourceGroup,
};
use crate::domain::post_telegraph::post_telegraph;
use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;
use axum::http::StatusCode;
use axum::response::Response;
use rust_decimal::Decimal;
use rust_decimal::RoundingStrategy;
use serde_json::json;
use serde_json::Value;
use sqlx::mysql::MySqlRow;
use sqlx::Row;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

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
pub(super) async fn delete_child_rows(
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
    let accuracy = crate::shared::scoring::accuracy_percent(
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

pub(super) async fn action(
    state: AppState,
    user: AuthUser,
    body: ActionRequest,
    finish: bool,
) -> Response {
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
