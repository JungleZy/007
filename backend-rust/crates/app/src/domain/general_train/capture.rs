use super::common::*;
use super::generate::{
    capture_bound, capture_intervals, generate_groups, interval_duration, measure_submission,
    online,
};
use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::MySqlConnection;

/// Characters of one stored key value (a JSON list of single characters): blanks and `?` do not
/// count, anything else that is not exactly one code point is rejected (Java `countCharacters`).
fn key_value_characters(value: &Value) -> Outcome<i64> {
    let text = value
        .as_str()
        .ok_or_else(|| invalid("拍发字符列表不能为空"))?;
    let characters: Vec<Option<String>> = serde_json::from_str::<Option<Vec<Option<String>>>>(text)
        .map_err(|_| invalid("拍发字符列表不能为空"))?
        .ok_or_else(|| invalid("拍发字符列表不能为空"))?;
    let mut count = 0;
    for character in characters {
        let character = character
            .filter(|character| character.chars().count() == 1)
            .ok_or_else(|| invalid("每个拍发事件必须是一个正文字符"))?;
        if !character.trim().is_empty() && character != "?" {
            count += 1;
        }
    }
    Ok(count)
}

/// Java `generatePageAnalyze`: one `{pageNumber, patNumber, totalTime}` per captured page (raw
/// protocol), or per stored value page for pre-protocol trainings.
pub(super) async fn page_analyze(
    connection: &mut MySqlConnection,
    train: &Value,
    member: &Value,
) -> Outcome<Value> {
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    let user_id = member["userId"]
        .as_str()
        .ok_or_else(|| invalid("参训用户ID缺失"))?;
    let values = rows(
        connection,
        value_table(Domain::Key),
        value_fields(Domain::Key),
        "WHERE train_id=? AND user_id=? ORDER BY page_number,sort",
        &[&id, user_id],
    )
    .await?;
    let mut analysis = Vec::new();
    if train["protocolVersion"] == 1 {
        let captures = stored_json(
            member["capturePages"]
                .as_str()
                .ok_or_else(|| invalid("缺少原始采集页索引"))?,
        )?;
        let captures = captures
            .as_object()
            .ok_or_else(|| invalid("缺少原始采集页索引"))?;
        let mut pages = captures
            .keys()
            .map(|page| {
                page.parse::<i64>()
                    .map(|number| (number, page))
                    .map_err(|_| invalid("采集页索引无效"))
            })
            .collect::<Outcome<Vec<_>>>()?;
        pages.sort_unstable();
        for (number, key) in pages {
            let mut characters = 0;
            for value in values
                .iter()
                .filter(|value| value["pageNumber"].as_i64() == Some(number))
            {
                characters += key_value_characters(&value["value"])?;
            }
            let intervals = capture_intervals(&captures[key.as_str()]["intervals"])?;
            analysis.push(json!({"pageNumber": number, "patNumber": characters, "totalTime": interval_duration(&intervals, i64::MAX)?}));
        }
        return Ok(json!(analysis));
    }
    let mut by_page: std::collections::BTreeMap<i64, (i64, i64)> =
        std::collections::BTreeMap::new();
    for value in &values {
        let page = value["pageNumber"]
            .as_i64()
            .ok_or_else(|| invalid("拍发页码缺失"))?;
        let entry = by_page.entry(page).or_default();
        if let Some(times) = value["time"]
            .as_str()
            .map(stored_json)
            .transpose()?
            .and_then(|times| times.as_array().cloned())
        {
            for time in times {
                entry.1 += time
                    .as_str()
                    .and_then(|time| time.trim().parse::<i64>().ok())
                    .ok_or_else(|| invalid("拍发时间不合法"))?;
            }
        }
        let pat = value["value"].as_str().unwrap_or("[]");
        entry.0 += stored_json(pat)?
            .as_array()
            .map_or(0, |values| values.len() as i64);
    }
    for (page, (count, time)) in by_page {
        analysis.push(json!({"pageNumber": page, "patNumber": count, "totalTime": time}));
    }
    Ok(json!(analysis))
}

/// Java `generatePageAnalyze` (telex): one `{pageNumber, patNumber, totalTime}` per page that has
/// stored value rows; only the raw row (`sort = -1`) contributes its character count and the
/// duration of its capture intervals.
pub(super) async fn telex_page_analyze(
    connection: &mut MySqlConnection,
    train: &Value,
    user_id: &str,
) -> Outcome<Value> {
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    let values = rows(
        connection,
        value_table(Domain::Telex),
        value_fields(Domain::Telex),
        "WHERE train_id=? AND user_id=? ORDER BY page_number,sort",
        &[&id, user_id],
    )
    .await?;
    let mut by_page: std::collections::BTreeMap<i64, (i64, i64)> =
        std::collections::BTreeMap::new();
    for value in &values {
        let page = value["pageNumber"]
            .as_i64()
            .ok_or_else(|| invalid("拍发页码缺失"))?;
        let entry = by_page.entry(page).or_default();
        if value["sort"] == -1 {
            entry.0 += crate::shared::telex_capture::character_count(
                value["value"].as_str().unwrap_or_default(),
                train["trainType"].as_i64(),
            ) as i64;
            if let Some(intervals) = value["captureIntervals"]
                .as_str()
                .filter(|text| !text.trim().is_empty())
            {
                entry.1 +=
                    interval_duration(&capture_intervals(&stored_json(intervals)?)?, i64::MAX)?;
            }
        }
    }
    Ok(json!(by_page
        .into_iter()
        .map(|(page, (count, time))| json!({"pageNumber": page, "patNumber": count, "totalTime": time}))
        .collect::<Vec<_>>()))
}

/// Java `GeneralTelexPatService.patDetail`: the reader is checked like `requireReadableTarget`
/// (no membership needed to read oneself), then the *requested* user's member row is looked up
/// (a missing `userId` finds nothing, 202). The reply is the typed user VO.
pub(super) async fn telex_pat_detail(
    connection: &mut MySqlConnection,
    train: &Value,
    user: &AuthUser,
    body: &Value,
) -> Outcome<Value> {
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    let requested = body["userId"].as_str().filter(|value| !value.is_empty());
    let target = requested
        .filter(|value| !value.trim().is_empty())
        .unwrap_or(&user.id);
    if target != user.id && !manages(connection, train, user, Domain::Telex).await? {
        return Err(denied());
    }
    let member = match requested {
        Some(requested) => load_member(connection, &id, requested, Domain::Telex).await?,
        None => None,
    }
    .ok_or_else(|| invalid("未查询到该用户的参训记录"))?;
    let user_id = text(&member["userId"]).unwrap_or_default();
    let pages: Vec<i64> = sqlx::query_scalar(&format!(
        "SELECT DISTINCT page_number FROM {} WHERE train_id=? ORDER BY page_number",
        page_table(Domain::Telex)
    ))
    .bind(&id)
    .fetch_all(&mut *connection)
    .await?;
    let content = if member["isFinish"] == 1 {
        rows(
            connection,
            value_table(Domain::Telex),
            value_fields(Domain::Telex),
            "WHERE train_id=? AND user_id=? AND page_number<=2 AND page_number>=1 ORDER BY page_number,sort,id",
            &[&id, &user_id],
        )
        .await?
    } else {
        rows(
            connection,
            page_table(Domain::Telex),
            page_fields(Domain::Telex),
            "WHERE train_id=? AND page_number<=2 AND page_number>=1 ORDER BY page_number,sort,id",
            &[&id],
        )
        .await?
    };
    let content = content
        .iter()
        .map(|row| {
            json!({"id": row["id"], "trainId": row["trainId"], "pageNumber": row["pageNumber"],
                "key": row["key"], "value": row["value"], "time": Value::Null, "sort": row["sort"]})
        })
        .collect::<Vec<_>>();
    let mut result = detail_member(&member, Domain::Telex);
    result["duration"] = json!(member["validTime"].as_i64().unwrap_or(0));
    result["pageAnalyzeVOS"] = telex_page_analyze(connection, train, &user_id).await?;
    result["existPage"] = json!(pages);
    result["content"] = json!(content);
    result["ruleContent"] = train["ruleContent"].clone();
    result["totalNumber"] = train["totalNumber"].clone();
    result["isCable"] = train["isCable"].clone();
    if train["isCable"] == 1 {
        result["totalNumber"] = json!(
            sqlx::query_scalar::<_, i64>(&format!(
                "SELECT COUNT(*) FROM {} WHERE train_id=?",
                page_table(Domain::Telex)
            ))
            .bind(&id)
            .fetch_one(&mut *connection)
            .await?
        );
        result["pageCount"] = json!(pages.last());
    }
    Ok(result)
}
pub(super) async fn members(
    state: AppState,
    user: AuthUser,
    body: Value,
    domain: Domain,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let id = train_id(&body)?;
        let mut connection = pool.acquire().await?;
        let train = load_train(&mut connection, &id, domain, false).await?;
        if matches!(domain, Domain::Telex) {
            return telex_pat_detail(&mut connection, &train, &user, &body).await;
        }
        let key = matches!(domain, Domain::Key);
        // Java `requireReadableTarget` (key) never asks for membership of the caller itself: an
        // outsider reading their own record gets 202 "no such participant", not 207.
        let target = if key {
            let target = body["userId"].as_str().filter(|id| !id.trim().is_empty()).unwrap_or(&user.id);
            if target != user.id && !manages(&mut connection, &train, &user, domain).await? { return Err(denied()); }
            target.to_owned()
        } else {
            readable_target(&mut connection, &train, &user, &body, domain).await?
        };
        let mut member = load_member(&mut connection, &id, &target, domain).await?.ok_or_else(|| invalid(if key { "未查询到该用户的参训记录" } else { "未查询到参训记录" }))?;
        member["protocolVersion"] = train["protocolVersion"].clone();
        member["ruleContent"] = train["ruleContent"].clone();
        member["totalNumber"] = train["totalNumber"].clone();
        member["isCable"] = train["isCable"].clone();
        member["duration"] = if train["protocolVersion"] == 1 { json!(member["activeMillis"].as_i64().unwrap_or(0)/1000) } else { train["validTime"].clone() };
        if !key {
            if let Some(profile) = rows(&mut connection,"t_user","id userName userImg","WHERE id=?",&[&target]).await?.pop() { member["userName"] = profile["userName"].clone(); member["userImg"] = profile["userImg"].clone(); }
        }
        let page_column = if matches!(domain, Domain::Ticker) { "floor_number" } else { "page_number" };
        let sql = format!("SELECT DISTINCT {page_column} FROM {} WHERE train_id=? ORDER BY {page_column}", page_table(domain));
        let pages: Vec<i32> = sqlx::query_scalar(&sql).bind(&id).fetch_all(&mut *connection).await?;
        member["existPage"] = json!(pages);
        member["pageCount"] = json!(pages.last());
        let filter = format!("WHERE train_id=? AND {page_column}<=2 ORDER BY {page_column},sort");
        member["content"] = if member["isFinish"] == 1 {
            json!(rows(&mut connection, value_table(domain), value_fields(domain), &format!("WHERE train_id=? AND user_id=? AND {page_column}<=2 ORDER BY {page_column},id"), &[&id,&target]).await?)
        } else { json!(rows(&mut connection, page_table(domain), page_fields(domain), &filter, &[&id]).await?) };
        if key && train["protocolVersion"] == 1 {
            let captures = stored_json(member["capturePages"].as_str().unwrap_or("{}"))?;
            let object = captures.as_object().ok_or_else(|| invalid("缺少原始采集页索引"))?;
            let mut submitted = object.keys().map(|key| key.parse::<i32>().map_err(|_| invalid("采集页索引无效"))).collect::<Outcome<Vec<_>>>()?;
            submitted.sort_unstable();
            member["existPageNumber"] = json!(submitted.len());
            member["existNumber"] = json!(submitted);
        }
        if key {
            // Java `PojoUtils.convertOne(member, GeneralKeyPatUserInfoVO)`: a typed user VO
            // (no raw columns, no profile), the fixed-report totals only for cable trainings.
            member["pageAnalyzeVOS"] = page_analyze(&mut connection, &train, &member).await?;
            let content = member["content"].as_array().map(|rows| rows.iter().map(|row| page_dto(row, domain, false)).collect::<Vec<_>>()).unwrap_or_default();
            let mut result = detail_member(&member, domain);
            result["protocolVersion"] = member["protocolVersion"].clone();
            result["isCable"] = member["isCable"].clone();
            result["duration"] = member["duration"].clone();
            result["ruleContent"] = member["ruleContent"].clone();
            result["totalNumber"] = member["totalNumber"].clone();
            result["existPage"] = member["existPage"].clone();
            result["content"] = json!(content);
            result["pageCount"] = Value::Null;
            if train["isCable"] == 1 {
                result["totalNumber"] = json!(sqlx::query_scalar::<_, i64>(&format!("SELECT COUNT(*) FROM {} WHERE train_id=?", page_table(domain))).bind(&id).fetch_one(&mut *connection).await?);
                result["pageCount"] = member["pageCount"].clone();
            }
            return Ok(result);
        }
        Ok(member)
    }.await)
}
pub(super) async fn page(state: AppState, user: AuthUser, body: Value, domain: Domain) -> Response {
    page_response(state, user, body, domain, matches!(domain, Domain::Ticker)).await
}
pub(super) async fn upload(
    state: AppState,
    actor: AuthUser,
    body: Value,
    domain: Domain,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let id = train_id(&body)?;
        let page = body.get("pageNumber").or_else(|| body.get("floorNumber")).and_then(Value::as_i64).filter(|page| *page > 0).ok_or_else(|| invalid("页码不正确"))?;
        let mut transaction = pool.begin().await?;
        let train = load_train(&mut transaction, &id, domain, true).await?;
        // Java `student()`: a missing or non-student member is 202 for the hand-key and key
        // domains; the telex domain keeps its 207.
        let not_student = || if matches!(domain, Domain::Telex) { denied() } else { invalid(STUDENT_MISSING) };
        let member = load_member(&mut transaction, &id, &actor.id, domain).await?.ok_or_else(not_student)?;
        if member["role"] != 0 { return Err(not_student()); }
        if matches!(domain, Domain::Key) {
            // Lock order is training -> member -> values everywhere (reset and finish lock the
            // member before touching values); the member row is written at the end of upload.
            sqlx::query("SELECT id FROM general_key_pat_user WHERE id=? FOR UPDATE")
                .bind(text(&member["id"]).ok_or_else(|| invalid("参训记录主键缺失"))?)
                .fetch_one(&mut *transaction).await?;
        }
        if matches!(domain, Domain::Telex) && train["protocolVersion"] != 1 { return Err(terminal("旧训练缺少原始采集协议，请重新创建训练；历史成绩保持不变")); }
        if train["protocolVersion"] != 1 || (matches!(domain, Domain::Telex) && body["protocolVersion"] != 1) { return Err(invalid("客户端采集协议版本不匹配，请刷新后重新进入训练")); }
        if body["attempt"].as_i64().is_none() || body["attempt"] != member["attempt"] { return Err(terminal("训练轮次已变化，请重新读取训练")); }
        let total = train[if matches!(domain, Domain::Ticker) { "messageNumber" } else { "totalNumber" }].as_i64().ok_or_else(|| invalid("训练总组数缺失"))?;
        if page > (total + 99) / 100 { return Err(invalid("页码不正确")); }
        let intervals = capture_intervals(&body["captureIntervals"])?;
        let page_text = page.to_string();
        let page_column = if matches!(domain, Domain::Ticker) { "floor_number" } else { "page_number" };
        let raw_filter = if matches!(domain, Domain::Telex) { " AND sort=-1" } else { "" };
        let filter = format!("WHERE train_id=? AND user_id=? AND {page_column}=?{raw_filter} ORDER BY {}", if matches!(domain,Domain::Ticker) { "id" } else { "sort,id" });
        let saved = rows(&mut transaction, value_table(domain), value_fields(domain), &filter, &[&id,&actor.id,&page_text]).await?;
        let mut captures = if matches!(domain, Domain::Key) { stored_json(member["capturePages"].as_str().ok_or_else(|| invalid("缺少原始采集页索引"))?)? } else { json!({}) };
        let previous = if matches!(domain, Domain::Key) { captures.get(&page_text).map(|capture| capture["intervals"].clone()) }
            else { saved.first().map(|row| row["captureIntervals"].as_str().ok_or_else(|| invalid("已保存页缺少采集时间轴")).and_then(stored_json)).transpose()? };
        if let Some(previous) = previous {
            let old = capture_intervals(&previous)?;
            if old == intervals {
                let same = match domain {
                    Domain::Key => body["pageValue"].as_array().is_some_and(|values| values.len() == saved.len() && values.iter().zip(&saved).all(|(value,saved)| ["sort","key","value","time"].iter().all(|field| value[*field] == saved[*field]))),
                    Domain::Telex => saved.first().is_some_and(|row| row["value"] == body["patValue"]),
                    Domain::Ticker => if let Some(row) = saved.first() { stored_json(row["messageBody"].as_str().ok_or_else(|| invalid("已保存页缺少拍发内容"))?)? == body["messageBody"] && stored_json(row["standard"].as_str().ok_or_else(|| invalid("已保存页缺少校准记录"))?)? == body["standard"] && row["finishInfo"] == body["finishInfo"] } else { false },
                };
                if !same {
                    return Err(invalid(if matches!(domain, Domain::Telex) {
                        "该页采集区间已确认，但正文不一致"
                    } else {
                        "该页采集区间已确认，但拍发内容不一致"
                    }));
                }
                transaction.commit().await?;
                return Ok(if matches!(domain, Domain::Key) { json!("") } else { Value::Null });
            }
            if intervals.len() <= old.len() || !intervals.starts_with(&old) { return Err(invalid("不能覆盖已确认的原始采集区间")); }
        }
        if member["isFinish"] == 1 { return Err(terminal("已结算的训练不能上传")); }
        let bound = capture_bound(&mut transaction,&id,&actor.id,domain).await?;
        let duration = interval_duration(&intervals,bound)?;
        let (characters,raw_millis) = measure_submission(&body,domain,train["trainType"].as_i64())?;
        if (characters > 0 && duration == 0) || raw_millis > duration as f64 + intervals.len() as f64 { return Err(invalid("原始拍发时长与采集区间不一致")); }
        let mut all = intervals.clone();
        if matches!(domain,Domain::Key) {
            for (number,capture) in captures.as_object().ok_or_else(|| invalid("缺少原始采集页索引"))? {
                if number != &page_text { all.extend(capture_intervals(&capture["intervals"])?); }
            }
        } else {
            let filter = format!("WHERE train_id=? AND user_id=? AND {page_column}<>?{raw_filter}");
            for row in rows(&mut transaction,value_table(domain),value_fields(domain),&filter,&[&id,&actor.id,&page_text]).await? {
                let intervals = stored_json(row["captureIntervals"].as_str().ok_or_else(|| invalid("已保存页缺少采集时间轴"))?)?;
                all.extend(capture_intervals(&intervals)?);
            }
        }
        all.sort_unstable();
        interval_duration(&all,i64::MAX)?;
        // `saved` is exactly this page's rows (same filter, read after the training lock): delete
        // them by primary key instead of scanning the unindexed child table.
        for saved_row in &saved {
            let row_id = text(&saved_row["id"]).ok_or_else(|| invalid("已保存页缺少主键"))?;
            sqlx::query(&format!("DELETE FROM {} WHERE id=?",value_table(domain))).bind(row_id).execute(&mut *transaction).await?;
        }
        match domain {
            Domain::Key => {
                for source in body["pageValue"].as_array().ok_or_else(|| invalid("拍发记录不能为空"))? {
                    let mut row = source.clone();
                    row["trainId"] = train["id"].clone(); row["userId"] = json!(actor.id); row["pageNumber"] = json!(page);
                    insert_fields(&mut transaction,value_table(domain),"trainId userId pageNumber sort key value time",&row).await?;
                }
                let received: i64 = sqlx::query_scalar("SELECT CAST(UNIX_TIMESTAMP(NOW(3))*1000 AS SIGNED)").fetch_one(&mut *transaction).await?;
                captures[&page_text] = json!({"attempt":member["attempt"],"intervals":body["captureIntervals"],"receivedAt":received});
                sqlx::query("UPDATE general_key_pat_user SET capture_pages=? WHERE id=?").bind(captures.to_string()).bind(text(&member["id"]).ok_or_else(|| invalid("参训记录主键缺失"))?).execute(&mut *transaction).await?;
            }
            Domain::Telex => {
                sqlx::query("INSERT INTO general_telex_pat_user_value (id,train_id,user_id,page_number,sort,value,attempt,capture_intervals,received_at) VALUES (?,?,?,?,-1,?,?,?,NOW(6))")
                    .bind(uuid::Uuid::new_v4().to_string()).bind(&id).bind(&actor.id).bind(page).bind(body["patValue"].as_str()).bind(member["attempt"].as_i64()).bind(body["captureIntervals"].to_string()).execute(&mut *transaction).await?;
                derive_telex_capture(&mut transaction,&text(&member["id"]).ok_or_else(|| invalid("参训记录主键缺失"))?,&id,&actor.id,train["trainType"].as_i64()).await?;
            }
            Domain::Ticker => {
                sqlx::query("INSERT INTO general_ticker_pat_train_user_value (train_id,user_id,floor_number,message_body,standard,finish_info,attempt,capture_intervals,received_at) VALUES (?,?,?,?,?,?,?,?,NOW(6))")
                    .bind(&id).bind(&actor.id).bind(page).bind(body["messageBody"].to_string()).bind(body["standard"].to_string()).bind(body["finishInfo"].as_str()).bind(member["attempt"].as_i64()).bind(body["captureIntervals"].to_string()).execute(&mut *transaction).await?;
            }
        }
        transaction.commit().await?;
        Ok(if matches!(domain, Domain::Key) { json!("") } else { Value::Null })
    }.await)
}

pub(super) async fn key_page(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    page(s, u, b, Domain::Key).await
}
pub(super) async fn key_members(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    members(s, u, b, Domain::Key).await
}
pub(super) async fn key_upload(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    upload(s, u, b, Domain::Key).await
}
pub(super) async fn key_online(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    online(s, u, q.train_id.unwrap_or_default(), Domain::Key).await
}
pub(super) async fn telex_page(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    page(s, u, b, Domain::Telex).await
}
pub(super) async fn telex_members(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    members(s, u, b, Domain::Telex).await
}
pub(super) async fn telex_upload(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    upload(s, u, b, Domain::Telex).await
}
pub(super) async fn telex_online(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    online(s, u, q.train_id.unwrap_or_default(), Domain::Telex).await
}
pub(super) async fn ticker_page(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    page(s, u, b, Domain::Ticker).await
}
pub(super) async fn ticker_upload(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    upload(s, u, b, Domain::Ticker).await
}

pub(super) fn stored_json(text: &str) -> Outcome<Value> {
    serde_json::from_str(text).map_err(|error| {
        Failure::Database(sqlx::Error::Protocol(format!(
            "invalid stored training JSON: {error}"
        )))
    })
}
async fn elapsed(
    connection: &mut MySqlConnection,
    id: &str,
    target: &str,
    domain: Domain,
) -> Outcome<i64> {
    let sql = format!("SELECT CAST(COALESCE(GREATEST(0,TIMESTAMPDIFF(MICROSECOND,capture_started_at,NOW(6)) DIV 1000),0) AS SIGNED) FROM {} WHERE train_id=? AND user_id=?", user_table(domain));
    Ok(sqlx::query_scalar(&sql)
        .bind(id)
        .bind(target)
        .fetch_optional(connection)
        .await?
        .unwrap_or(0))
}
pub(super) fn page_dto(row: &Value, domain: Domain, source: bool) -> Value {
    match domain {
        Domain::Key => {
            let mut output = row.clone();
            for field in ["id", "trainId"] {
                if let Some(value) = output.get(field).and_then(text) {
                    output[field] = json!(value);
                }
            }
            output.as_object_mut().map(|object| {
                object.remove("userId");
            });
            output
        }
        Domain::Telex => json!({
            "id": row["id"], "key": row["key"], "pageNumber": row["pageNumber"],
            "sort": row["sort"], "trainId": row["trainId"],
            "value": if source { Value::Null } else { row["value"].clone() }
        }),
        Domain::Ticker => json!({
            "id": row["id"], "moresKey": row["moresKey"], "moresTime": row["moresTime"],
            "moresValue": row["moresValue"], "patKeys": row["patKeys"], "patLogs": "[]"
        }),
    }
}

pub(super) async fn page_response(
    state: AppState,
    actor: AuthUser,
    body: Value,
    domain: Domain,
    personal: bool,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let id = train_id(&body)?;
        let number = body.get("pageNumber").or_else(|| body.get("floorNumber")).and_then(Value::as_i64).filter(|number| *number > 0).ok_or_else(|| invalid("页码不正确"))?;
        let mut transaction = pool.begin().await?;
        // Only getPage may generate a missing page; findPage is a plain read and must not lock.
        let train = load_train(&mut transaction, &id, domain, personal).await?;
        // Java `readableMember` (key and hand-key): 207 for reading others without being the
        // creator/organizer, 202 for a target that is not a member. Telex keeps its own check.
        let java_member = personal && !matches!(domain, Domain::Telex);
        if !java_member { require_member(&mut transaction, &train, &actor, domain).await?; }
        let page_column = if matches!(domain, Domain::Ticker) { "floor_number" } else { "page_number" };
        let filter = format!("WHERE train_id=? AND {page_column}=? ORDER BY sort");
        let number_text = number.to_string();
        let mut messages = rows(&mut transaction, page_table(domain), page_fields(domain), &filter, &[&id, &number_text]).await?;
        if !personal {
            transaction.commit().await?;
            if matches!(domain, Domain::Key | Domain::Telex) {
                // Java `GeneralKeyPatPageDetailDto` / `GeneralTelexPatPageDetailDto`: no trainId.
                for message in &mut messages { message.as_object_mut().map(|row| row.remove("trainId")); }
            }
            return Ok(json!({"messageContent":messages}));
        }
        let (target, member) = if java_member {
            readable_member(&mut transaction, &train, &actor, body.get("userId").and_then(Value::as_str), domain).await?
        } else {
            let target = readable_target(&mut transaction, &train, &actor, &body, domain).await?;
            let member = load_member(&mut transaction, &id, &target, domain).await?.ok_or_else(|| invalid("未查询到参训记录"))?;
            (target, member)
        };
        let total = train[if matches!(domain, Domain::Ticker) { "messageNumber" } else { "totalNumber" }].as_i64().ok_or_else(|| invalid("训练总组数缺失"))?;
        if number > (total + 99) / 100 { return Err(invalid("页码不正确")); }
        if messages.is_empty() {
            if train["isCable"] == 1 { return Err(invalid("固定报底页不存在")); }
            let groups = generate_groups(&train, domain, (total - (number - 1) * 100).min(100) as usize)?;
            for (sort, group) in groups.into_iter().enumerate() {
                let key = if matches!(domain, Domain::Telex) { group } else { json!(group.chars().map(|ch| ch.to_string()).collect::<Vec<_>>()).to_string() };
                let row = if matches!(domain, Domain::Ticker) {
                    json!({"id":uuid::Uuid::new_v4().to_string(),"trainId":train["id"],"floorNumber":number,"sort":sort,"moresKey":key,"moresValue":"[]","moresTime":"[]","patKeys":"[]"})
                } else { json!({"id":uuid::Uuid::new_v4().to_string(),"trainId":train["id"],"pageNumber":number,"sort":sort,"key":key,"value":"[]","time":"[]"}) };
                insert_fields(&mut transaction, page_table(domain), page_fields(domain), &row).await?;
                messages.push(row);
            }
        }
        let filter = format!("WHERE train_id=? AND user_id=? AND {page_column}=? ORDER BY {}", if matches!(domain, Domain::Ticker) { "id" } else { "sort" });
        let values = rows(&mut transaction, value_table(domain), value_fields(domain), &filter, &[&id, &target, &number_text]).await?;
        let mut result = json!({"protocolVersion":train["protocolVersion"],"attempt":member["attempt"],"serverElapsedMs":elapsed(&mut transaction,&id,&target,domain).await?,"savedCaptureIntervals":[],"submitted":false});
        match domain {
            Domain::Key => {
                result["resolverMessage"] = Value::Null;
                let captures = stored_json(member["capturePages"].as_str().unwrap_or("{}"))?;
                let captured = captures.get(&number_text);
                let submitted = captured.is_some() || (train["protocolVersion"] != 1 && !values.is_empty());
                result["submitted"] = json!(submitted);
                result["savedCaptureIntervals"] = captured.map(|page| page["intervals"].clone()).unwrap_or(json!([]));
                result["messageVO"] = json!(if submitted { values.iter().map(|row| page_dto(row, domain, false)).collect::<Vec<_>>() } else { messages.iter().map(|row| page_dto(row, domain, true)).collect::<Vec<_>>() });
                let more = rows(&mut transaction,"general_key_pat_train_more",KEY_MORE,"WHERE train_id=? AND user_id=? AND page_number=?",&[&id,&target,&number_text]).await?;
                for key in ["moreGroup", "moreLine"] { result[key] = match more.first().and_then(|row| row[key].as_str()) { Some(text) => stored_json(text)?, None => json!([]) }; }
            }
            Domain::Telex => unreachable!("telex getPage has its own handler"),
            Domain::Ticker => {
                let saved = values.first();
                result["submitted"] = json!(saved.is_some());
                let display_messages = messages.iter().map(|row| page_dto(row, domain, true)).collect::<Vec<_>>();
                result["messageBody"] = saved.map(|row| row["messageBody"].clone()).unwrap_or_else(|| json!(serde_json::to_string(&display_messages).unwrap_or_default()));
                result["messageKey"] = json!(messages.iter().map(|row| page_dto(row, domain, true)).collect::<Vec<_>>());
                for key in ["finishInfo", "standard", "resolver"] { result[key] = saved.map(|row| row[key].clone()).unwrap_or(json!("[]")); }
                if train["protocolVersion"] == 1 {
                    if let Some(intervals) = saved.and_then(|row| row["captureIntervals"].as_str()) { result["savedCaptureIntervals"] = stored_json(intervals)?; }
                }
            }
        }
        transaction.commit().await?;
        Ok(result)
    }.await)
}
async fn pat_value(state: AppState, actor: AuthUser, body: Value, domain: Domain) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(
        async {
            let id = train_id(&body)?;
            let telex = matches!(domain, Domain::Telex);
            // Java telex getPatValue does not validate the page: nothing stored for it is `[]`.
            let page = match body["pageNumber"].as_i64().filter(|page| *page > 0) {
                Some(page) => page.to_string(),
                None if telex => "0".to_owned(),
                None => return Err(invalid("页码不正确")),
            };
            let mut connection = pool.acquire().await?;
            let train = load_train(&mut connection, &id, domain, false).await?;
            // Java key `requireReadableTarget`: no membership check for reading oneself, so an
            // outsider gets an empty list; the VO has no `id`.
            let target = if matches!(domain, Domain::Key | Domain::Telex) {
                let target = body["userId"]
                    .as_str()
                    .filter(|id| !id.trim().is_empty())
                    .unwrap_or(&actor.id);
                if target != actor.id && !manages(&mut connection, &train, &actor, domain).await? {
                    return Err(denied());
                }
                target.to_owned()
            } else {
                readable_target(&mut connection, &train, &actor, &body, domain).await?
            };
            let mut values = rows(
                &mut connection,
                value_table(domain),
                value_fields(domain),
                "WHERE train_id=? AND user_id=? AND page_number=? ORDER BY sort,id",
                &[&id, &target, &page],
            )
            .await?;
            if matches!(domain, Domain::Key) {
                for value in &mut values {
                    value.as_object_mut().map(|row| row.remove("id"));
                }
            }
            if telex {
                // Java `GeneralTelexPatTrainUserValueVO`.
                for value in &mut values {
                    if let Some(row) = value.as_object_mut() {
                        for field in ["id", "attempt", "captureIntervals", "receivedAt"] {
                            row.remove(field);
                        }
                    }
                }
            }
            Ok(json!(values))
        }
        .await,
    )
}
pub(super) async fn key_get_page(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    page_response(state, actor, body, Domain::Key, true).await
}
/// Java `GeneralTelexPatService.getPage`: the reader is checked like `requireReadableTarget`
/// (no membership needed to read oneself). A missing source page of a random report is generated
/// on first read by a member; only then is the training row locked (plain reads otherwise).
/// A non-member read gets the same response but writes nothing.
pub(super) async fn telex_get_page(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let domain = Domain::Telex;
        let id = train_id(&body)?;
        let mut connection = pool.acquire().await?;
        let train = load_train(&mut connection, &id, domain, false).await?;
        let target = body["userId"]
            .as_str()
            .filter(|value| !value.trim().is_empty())
            .unwrap_or(&actor.id)
            .to_owned();
        if target != actor.id && !manages(&mut connection, &train, &actor, domain).await? {
            return Err(denied());
        }
        let number = body["pageNumber"].as_i64().ok_or_else(|| invalid("页码不能为空"))?;
        let mut generate_count = 100_i64;
        if train["isCable"] == 0 {
            let total = train["totalNumber"].as_i64().ok_or_else(|| invalid("训练总组数缺失，无法取页"))?;
            let total_page = total / 100 + i64::from(total % 100 > 0);
            if number > total_page || number < 1 { return Err(invalid("页码不正确")); }
            if number == total_page { generate_count = total - (number - 1) * 100; }
        } else if number < 1 {
            return Err(invalid("页码不正确"));
        }
        let number_text = number.to_string();
        let page_filter = "WHERE train_id=? AND page_number=? ORDER BY sort";
        let member = load_member(&mut connection, &id, &target, domain).await?;
        let mut messages = rows(&mut connection, page_table(domain), page_fields(domain), page_filter, &[&id, &number_text]).await?;
        if messages.is_empty() {
            if train["isCable"] == 1 { return Err(invalid("固定报底页不存在")); }
            if member.is_none() {
                // Deliberate difference from Java: a non-member read gets the same response
                // (freshly generated groups) but nothing is persisted and nothing is locked.
                let groups = generate_groups(&train, domain, generate_count.max(0) as usize)?;
                for (sort, group) in groups.into_iter().enumerate() {
                    messages.push(json!({"id":uuid::Uuid::new_v4().to_string(),"trainId":train["id"],"pageNumber":number,"sort":sort,"key":group,"value":Value::Null}));
                }
            } else {
                let mut transaction = pool.begin().await?;
                load_train(&mut transaction, &id, domain, true).await?;
                messages = rows(&mut transaction, page_table(domain), page_fields(domain), page_filter, &[&id, &number_text]).await?;
                if messages.is_empty() {
                    let groups = generate_groups(&train, domain, generate_count.max(0) as usize)?;
                    for (sort, group) in groups.into_iter().enumerate() {
                        let row = json!({"id":uuid::Uuid::new_v4().to_string(),"trainId":train["id"],"pageNumber":number,"sort":sort,"key":group,"value":Value::Null});
                        insert_fields(&mut transaction, page_table(domain), page_fields(domain), &row).await?;
                        messages.push(row);
                    }
                }
                transaction.commit().await?;
            }
        }
        let values = rows(&mut connection, value_table(domain), value_fields(domain), "WHERE train_id=? AND user_id=? AND page_number=? ORDER BY sort,id", &[&id, &target, &number_text]).await?;
        let raw = values.iter().find(|row| row["sort"] == -1);
        let mut result = json!({
            "protocolVersion": train["protocolVersion"],
            "attempt": member.as_ref().map_or(Value::Null, |member| member["attempt"].clone()),
            "serverElapsedMs": if member.is_some() { elapsed(&mut connection, &id, &target, domain).await? } else { 0 },
            "submitted": raw.is_some(), "savedCaptureIntervals": [],
        });
        if let Some(intervals) = raw.and_then(|row| row["captureIntervals"].as_str()) { result["savedCaptureIntervals"] = stored_json(intervals)?; }
        result["messageVO"] = json!(if values.is_empty() { messages.iter().map(|row| page_dto(row, domain, true)).collect::<Vec<_>>() } else { values.iter().map(|row| page_dto(row, domain, false)).collect::<Vec<_>>() });
        Ok(result)
    }.await)
}
pub(super) async fn key_pat_value(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    pat_value(state, actor, body, Domain::Key).await
}
pub(super) async fn telex_pat_value(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    pat_value(state, actor, body, Domain::Telex).await
}
/// Java `deriveCapture`: per-page and total rate from the raw rows' capture intervals, written to
/// the member row by primary key. Returns the member's total rate (Java `member.speed`).
pub(super) async fn derive_telex_capture(
    connection: &mut MySqlConnection,
    member_id: &str,
    train_id: &str,
    user_id: &str,
    train_type: Option<i64>,
) -> Outcome<rust_decimal::Decimal> {
    let rows = rows(
        connection,
        value_table(Domain::Telex),
        value_fields(Domain::Telex),
        "WHERE train_id=? AND user_id=? AND sort=-1 ORDER BY page_number",
        &[train_id, user_id],
    )
    .await?;
    let mut total_duration = 0_i64;
    let mut total_characters = 0_usize;
    let mut page_rates = Vec::new();
    let mut page_times = Vec::new();
    for row in rows {
        let page = row["pageNumber"]
            .as_i64()
            .ok_or_else(|| invalid("拍发页码缺失"))?;
        let intervals = stored_json(
            row["captureIntervals"]
                .as_str()
                .ok_or_else(|| invalid("已保存页缺少原始采集时间轴"))?,
        )?;
        let duration = interval_duration(&capture_intervals(&intervals)?, i64::MAX)?;
        let characters = crate::shared::telex_capture::character_count(
            row["value"]
                .as_str()
                .ok_or_else(|| invalid("页面内容不能为空"))?,
            train_type,
        );
        total_duration = total_duration
            .checked_add(duration)
            .ok_or_else(|| invalid("采集时长溢出"))?;
        total_characters = total_characters
            .checked_add(characters)
            .ok_or_else(|| invalid("拍发字符数量超出范围"))?;
        while page_rates.len() < page as usize {
            page_rates.push(Value::Null);
            page_times.push(Value::Null);
        }
        page_rates[page as usize - 1] =
            json!(crate::shared::scoring::rate_per_minute(characters as i64, duration).to_string());
        page_times[page as usize - 1] = json!(duration / 1000);
    }
    let speed = crate::shared::scoring::rate_per_minute(total_characters as i64, total_duration);
    sqlx::query(
        "UPDATE general_telex_pat_user SET active_millis=?,valid_time=?,speed=?,speed_log=?,valid_time_log=? WHERE id=?",
    )
    .bind(total_duration)
    .bind(i32::try_from(total_duration / 1000).map_err(|_| invalid("采集时长超出范围"))?)
    .bind(speed.to_string())
    .bind(Value::Array(page_rates).to_string())
    .bind(Value::Array(page_times).to_string())
    .bind(member_id)
    .execute(connection)
    .await?;
    Ok(speed)
}
