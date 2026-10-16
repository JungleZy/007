use super::capture::stored_json;
use super::common::*;
use super::generate::participant_action;
use super::generate::{capture_intervals, frozen_rule, interval_duration, measure_submission};
use super::lifecycle::{add, delete, detail, finish_participant, list, set_status, statistic};
use crate::platform::auth::AuthUser;
use crate::platform::state::AppState;
use axum::extract::{Extension, Query, State};
use axum::response::Response;
use axum::Json;
use serde_json::{json, Value};
use sqlx::MySqlConnection;
use std::collections::HashMap;

pub(super) async fn ticker_add(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    add(s, u, b, Domain::Ticker).await
}
pub(super) async fn ticker_list(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    list(s, u, b, Domain::Ticker).await
}
pub(super) async fn ticker_detail(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    detail(s, u, b, Domain::Ticker).await
}
pub(super) async fn ticker_finish(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    finish_participant(s, u, b, Domain::Ticker).await
}
pub(super) async fn ticker_reset(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    participant_action(s, u, b, Domain::Ticker, true).await
}
pub(super) async fn ticker_start(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    participant_action(
        s,
        u,
        json!({"trainId":q.train_id,"attempt":q.attempt}),
        Domain::Ticker,
        false,
    )
    .await
}
pub(super) async fn ticker_delete(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    delete(s, u, q.train_id.unwrap_or_default(), Domain::Ticker).await
}
pub(super) async fn ticker_statistics(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    statistic(s, u, b, Domain::Ticker).await
}
/// Hand-key settlement. Java scores it with the same MessageComparisonService / handkey rule
/// machinery as the post-telegram training, so this reuses that port: `compare_page` yields the
/// page resolver and the score counters, `scoring_result` the deduction and statistics JSON.
pub(super) async fn settle_ticker(
    connection: &mut MySqlConnection,
    train: &Value,
    member: &Value,
    user_id: &str,
) -> Outcome<()> {
    use crate::domain::post_telegram::rules::handkey_rule;
    use crate::domain::post_telegram::rules::HandkeyScore;
    use crate::domain::post_telegram::scoring::compare_page;
    use crate::domain::post_telegram::scoring::scoring_result;
    use rust_decimal::prelude::ToPrimitive;
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    let rule = frozen_rule(train, Domain::Ticker)?;
    let handkey = handkey_rule(Some(&rule.to_string())).map_err(invalid)?;
    let values = rows(
        connection,
        value_table(Domain::Ticker),
        value_fields(Domain::Ticker),
        "WHERE train_id=? AND user_id=? ORDER BY floor_number",
        &[&id, user_id],
    )
    .await?;
    let duration = member_capture_duration(member, Domain::Ticker, &values)?;
    let sources = rows(
        connection,
        page_table(Domain::Ticker),
        page_fields(Domain::Ticker),
        "WHERE train_id=? ORDER BY floor_number,sort",
        &[&id],
    )
    .await?;
    let mut source_by_page: HashMap<i64, Vec<Value>> = HashMap::new();
    for source in sources {
        source_by_page
            .entry(
                source["floorNumber"]
                    .as_i64()
                    .ok_or_else(|| invalid("报底楼层缺失"))?,
            )
            .or_default()
            .push(source);
    }
    let mut score = HandkeyScore::default();
    let mut characters = 0_i64;
    let mut existing_pages = std::collections::HashSet::new();
    let mut page_rates = Vec::with_capacity(values.len());
    let mut resolvers = Vec::with_capacity(values.len());
    for value in &values {
        let page = value["floorNumber"]
            .as_i64()
            .ok_or_else(|| invalid("拍发楼层缺失"))?;
        let body = json_array_text(&value["messageBody"], "拍发记录必须是JSON数组文本")?;
        let standard = json_array_text(&value["standard"], "校准记录必须是JSON数组文本")?;
        let submission = json!({"messageBody": body, "standard": standard});
        let (count, _) = measure_submission(&submission, Domain::Ticker, None)?;
        let page_duration = interval_duration(
            &capture_intervals(&stored_json(
                value["captureIntervals"]
                    .as_str()
                    .ok_or_else(|| invalid("已保存页缺少原始采集时间轴"))?,
            )?)?,
            i64::MAX,
        )?;
        // Java TrainingRateUnit.rate: whole characters per minute, HALF_UP.
        page_rates.push(json!(crate::shared::scoring::rate_per_minute(
            count as i64,
            page_duration
        )
        .to_string()));
        characters += count as i64;
        existing_pages.insert(page);
        let source_groups = source_by_page
            .get(&page)
            .map(Vec::as_slice)
            .unwrap_or_default()
            .iter()
            .map(|source| source_group_text(source, Domain::Ticker))
            .collect::<Outcome<Vec<_>>>()?;
        let resolver = compare_page(
            &source_groups,
            submission["messageBody"]
                .as_array()
                .map(Vec::as_slice)
                .unwrap_or_default(),
            submission["standard"]
                .as_array()
                .map(Vec::as_slice)
                .unwrap_or_default(),
            &handkey,
            &mut score,
        )
        .map_err(invalid)?;
        resolvers.push((
            text(&value["id"]).ok_or_else(|| invalid("已保存页缺少主键"))?,
            serde_json::to_string(&resolver).map_err(|_| invalid("页解析结果序列化失败"))?,
        ));
    }
    // Java calculateLackCount: every page of messageNumber that has no saved value counts as
    // missing, whether or not its base text was ever generated (a fixed report may have fewer
    // pages than messageNumber implies).
    let message_number = train["messageNumber"]
        .as_i64()
        .ok_or_else(|| invalid("训练总组数缺失，无法结算"))?;
    let mut missing = 0_i64;
    for page in 1..=(message_number + 99) / 100 {
        if !existing_pages.contains(&page) {
            missing += 100.min(message_number - (page - 1) * 100);
        }
    }
    score.lack_groups += missing;
    let speed = crate::shared::scoring::rate_per_minute(characters, duration);
    let speed_number = speed
        .to_i64()
        .filter(|speed| *speed <= i64::from(i32::MAX))
        .ok_or_else(|| invalid("训练速率超出有效范围"))?;
    let full_score = frozen_full_score(train, Domain::Ticker)?;
    let (total, statistics, deductions) =
        scoring_result(&score, &handkey, full_score as i32, speed_number, false);
    // Java: no correct group at all is "0.00"; otherwise ScoreMath's two-place ratio times 100.
    let accuracy = if score.correct() == 0 {
        "0.00".to_owned()
    } else {
        format!(
            "{:.2}",
            crate::shared::scoring::accuracy_percent(score.correct(), score.total())
        )
    };
    resolvers.sort_by_key(|(page_id, _)| page_id.parse::<i64>().unwrap_or(i64::MAX));
    for (page_id, resolver) in resolvers {
        sqlx::query("UPDATE general_ticker_pat_train_user_value SET resolver=? WHERE id=?")
            .bind(resolver)
            .bind(page_id)
            .execute(&mut *connection)
            .await?;
    }
    let sql = "UPDATE general_ticker_pat_train_user SET active_millis=?,speed=?,speed_log=?,accuracy=?,error_number=?,lack=?,score=?,deduct_info=?,statistic_info=?,is_finish=1,finish_time=NOW(6) WHERE id=?";
    sqlx::query(sql)
        .bind(duration)
        .bind(speed.to_string())
        .bind(Value::Array(page_rates).to_string())
        .bind(accuracy)
        .bind(i32::try_from(score.errors()).map_err(|_| invalid("错误数量超出范围"))?)
        .bind(i32::try_from(missing).map_err(|_| invalid("缺报数量超出范围"))?)
        .bind(format!("{total}.00"))
        .bind(deductions.to_string())
        .bind(statistics.to_string())
        .bind(text(&member["id"]).ok_or_else(|| invalid("参训记录主键缺失"))?)
        .execute(connection)
        .await?;
    Ok(())
}
/// `/api/socket/generalTickerPatTrain/updateTrainStatus` (teacher.js:299): the same Java
/// `updateStatus` transition as the training's own status endpoint.
pub(super) async fn ticker_socket_update(
    state: AppState,
    actor: AuthUser,
    train_id: i64,
    status: Option<i64>,
) -> Response {
    let status = status
        .and_then(|status| i32::try_from(status).ok())
        .unwrap_or(-1);
    set_status(state, actor, train_id.to_string(), status, Domain::Ticker).await
}

pub(super) async fn ticker_update(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let id = match train_id(&body) {
        Ok(id) => id,
        Err(error) => return response(Err(error)),
    };
    let status = body["status"]
        .as_i64()
        .and_then(|status| i32::try_from(status).ok())
        .unwrap_or(-1);
    set_status(state, actor, id, status, Domain::Ticker).await
}
