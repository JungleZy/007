use super::dto::{count_characters, err, invalid, CaptureInterval};
use super::rules::{capture_duration, require_no_overlap};
use crate::platform::envelope::envelope_response;
use crate::shared::generated_groups::generated_groups;
use crate::shared::handkey_rules::{validate_standard, HandkeyRule, HandkeyScore};
use crate::shared::handkey_scoring::{compare_page, scoring_result};
use axum::http::StatusCode;
use axum::response::Response;
use rust_decimal::prelude::ToPrimitive;
use serde_json::json;
use serde_json::Value;
use sqlx::Row;

pub(super) const TRAIN_VIEW_SQL: &str = "SELECT t.*,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') AS start_text,DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') AS end_text,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS create_text,CAST(GREATEST(COALESCE(TIMESTAMPDIFF(MICROSECOND,start_time,NOW(6)) DIV 1000,0),0) AS SIGNED) AS elapsed_ms FROM t_post_telegram_train t WHERE id=? LIMIT 1 FOR UPDATE";

pub(super) fn training_view(row: &sqlx::mysql::MySqlRow) -> Value {
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
        "score":row.get::<Option<String>,_>("score"),
        "ruleId":row.get::<Option<String>,_>("rule_id"), "ruleContent":row.get::<Option<String>,_>("rule_content"),
        "statisticInfo":row.get::<Option<String>,_>("statistic_info"), "deductInfo":row.get::<Option<String>,_>("deduct_info"),
        "floorNow":row.get::<Option<i32>,_>("floor_now"), "speedLog":row.get::<Option<String>,_>("speed_log"),
        "messageGroup":null, "pageAnalyzeVOS":null, "finishInfo":null, "messageBody":null,
        "existNumber":null, "standards":null, "resolver":null
    })
}

pub(super) fn corrupt_saved(message: &str, train_id: &str) -> Response {
    tracing::warn!(
        train_id,
        reason = message,
        "saved telegram scoring data is invalid"
    );
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

pub(super) fn saved_page_analysis(
    page: &sqlx::mysql::MySqlRow,
    train_id: &str,
) -> Result<Value, Response> {
    let body: Vec<Value> = serde_json::from_str(
        &page
            .get::<Option<String>, _>("message_body")
            .ok_or_else(|| corrupt_saved("已保存拍发正文缺失", train_id))?,
    )
    .map_err(|error| corrupt_saved(&error.to_string(), train_id))?;
    let intervals: Vec<CaptureInterval> = serde_json::from_str(
        &page
            .get::<Option<String>, _>("capture_intervals")
            .ok_or_else(|| corrupt_saved("已保存采集时间轴缺失", train_id))?,
    )
    .map_err(|error| corrupt_saved(&error.to_string(), train_id))?;
    let received = page
        .get::<Option<i64>, _>("received_elapsed_ms")
        .ok_or_else(|| corrupt_saved("已保存接收时间缺失", train_id))?;
    Ok(json!({
        "pageNumber": page.get::<Option<i32>, _>("floor_number"),
        "patNumber": count_characters(&body).map_err(invalid)?,
        "totalTime": capture_duration(&intervals, received.max(0)).map_err(invalid)?,
    }))
}

// Every existing-train writer locks the canonical train PK before its first
// consistent child read, including lazy generation, reset, settlement and delete.
pub(super) async fn clear_content(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    train_id: &str,
    delete_sources: bool,
) -> Result<(), sqlx::Error> {
    let values: Vec<String> = sqlx::query_scalar(
        "SELECT id FROM t_post_telegram_train_floor_content_value WHERE train_id=? ORDER BY id",
    )
    .bind(train_id)
    .fetch_all(&mut **tx)
    .await?;
    for id in values {
        sqlx::query("DELETE FROM t_post_telegram_train_floor_content_value WHERE id=?")
            .bind(id)
            .execute(&mut **tx)
            .await?;
    }
    let sources: Vec<String> = sqlx::query_scalar(
        "SELECT id FROM t_post_telegram_train_floor_content WHERE train_id=? ORDER BY id",
    )
    .bind(train_id)
    .fetch_all(&mut **tx)
    .await?;
    let source_sql = if delete_sources {
        "DELETE FROM t_post_telegram_train_floor_content WHERE id=?"
    } else {
        "UPDATE t_post_telegram_train_floor_content SET mores_value='[]',mores_time='[]',pat_keys='[]' WHERE id=?"
    };
    for id in sources {
        sqlx::query(source_sql).bind(id).execute(&mut **tx).await?;
    }
    Ok(())
}

pub(super) async fn generate_source_page(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    train: &sqlx::mysql::MySqlRow,
    train_id: &str,
    floor_number: i32,
) -> Result<(), Response> {
    let message_number = train
        .get::<Option<i32>, _>("message_number")
        .ok_or_else(|| invalid("训练报文数量缺失，无法生成报底"))?;
    let train_type = train
        .get::<Option<i32>, _>("type")
        .ok_or_else(|| invalid("报文类型缺失，无法生成报底"))?;
    let remaining =
        (i64::from(message_number) - (i64::from(floor_number) - 1) * 100).min(100) as usize;
    for (sort, group) in generated_groups(
        remaining,
        train_type,
        train.get::<Option<i32>, _>("is_random") == Some(1),
        train.get::<Option<i32>, _>("is_average") == Some(1),
    )
    .into_iter()
    .enumerate()
    {
        let keys: Vec<String> = group
            .chars()
            .map(|character| character.to_string())
            .collect();
        let key = serde_json::to_string(&keys)
            .map_err(|error| corrupt_saved(&error.to_string(), train_id))?;
        sqlx::query("INSERT INTO t_post_telegram_train_floor_content (id,train_id,floor_number,sort,mores_key,mores_value,mores_time,pat_keys) VALUES (?,?,?,?,?,'[]','[]','[]')")
            .bind(uuid::Uuid::new_v4().to_string()).bind(train_id).bind(floor_number).bind(sort as i32).bind(key)
            .execute(&mut **tx).await.map_err(|error| err(error, "generate content page"))?;
    }
    Ok(())
}

pub(super) async fn settle_train(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    train: &sqlx::mysql::MySqlRow,
    rule: &HandkeyRule,
    full_score: i32,
) -> Result<(), Response> {
    let id = train.get::<String, _>("id");
    let attempt = train.get::<Option<i32>, _>("attempt");
    let pages = sqlx::query("SELECT p.id,p.floor_number,p.attempt,p.message_body,p.standard,p.capture_intervals,CAST(TIMESTAMPDIFF(MICROSECOND,t.start_time,p.received_at) DIV 1000 AS SIGNED) AS received_elapsed_ms FROM t_post_telegram_train_floor_content_value p JOIN t_post_telegram_train t ON t.id=p.train_id WHERE p.train_id=? ORDER BY p.floor_number")
        .bind(&id).fetch_all(&mut **tx).await.map_err(|error| err(error, "finish-read-pages"))?;
    let sources = sqlx::query("SELECT floor_number,mores_key FROM t_post_telegram_train_floor_content WHERE train_id=? ORDER BY floor_number,sort")
        .bind(&id).fetch_all(&mut **tx).await.map_err(|error| err(error, "finish-read-sources"))?;
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
    let speed = crate::shared::scoring::rate_per_minute(characters, active_millis);
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
            crate::shared::scoring::accuracy_percent(score.correct, score.total)
        )
    };
    let speed = if pages.is_empty() {
        "0.00".into()
    } else {
        speed.to_string()
    };
    resolvers.sort_unstable_by(|left, right| left.0.cmp(&right.0));
    for (page_id, resolver) in resolvers {
        sqlx::query("UPDATE t_post_telegram_train_floor_content_value SET resolver=? WHERE id=?")
            .bind(resolver)
            .bind(page_id)
            .execute(&mut **tx)
            .await
            .map_err(|error| err(error, "finish-save-resolver"))?;
    }
    sqlx::query("UPDATE t_post_telegram_train SET status=2,end_time=NOW(6),active_millis=?,valid_time=?,speed=?,error_number=?,accuracy=?,score=?,deduct_info=?,statistic_info=?,lack=? WHERE id=?")
        .bind(active_millis).bind(active_millis / 1000).bind(speed).bind(errors).bind(accuracy).bind(total.to_string())
        .bind(deductions.to_string()).bind(statistics.to_string()).bind(lack).bind(&id)
        .execute(&mut **tx).await.map_err(|error| err(error, "finish-save-result"))?;
    Ok(())
}
