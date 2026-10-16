use super::capture::{page_analyze, stored_json, telex_page_analyze};
use super::common::*;
use super::generate::{
    capture_bound, frozen_rule, generate_groups, numeric_pairs, percentage, save_generated_group,
};
use super::key::settle_key;
use super::telex::settle_telex;
use super::ticker::settle_ticker;
use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;
use axum::http::StatusCode;
use axum::response::Response;
use serde_json::json;
use serde_json::Value;
use std::collections::HashMap;

/// Jackson's `Boolean` coercion as the add form relies on it: SelectCable.vue posts the cable's
/// integer `codeSort` (1 means long code), which Jackson reads as `true`; any non-zero number is
/// true, and the strings "true"/"True" are true.
fn jackson_flag(value: &Value) -> bool {
    match value {
        Value::Bool(flag) => *flag,
        Value::Number(number) => number.as_f64().is_some_and(|number| number != 0.0),
        Value::String(text) => matches!(text.as_str(), "true" | "True"),
        _ => false,
    }
}
pub(super) async fn add(
    state: AppState,
    actor: AuthUser,
    mut body: Value,
    domain: Domain,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        if !body.is_object() { return Err(invalid("请求参数错误")); }
        let total_field = if matches!(domain,Domain::Ticker) { "messageNumber" } else { "totalNumber" };
        let total = body[total_field].as_i64().filter(|total| *total > 0 && *total <= i32::MAX as i64).ok_or_else(|| invalid("训练总组数不合法"))?;
        let cable = if matches!(domain,Domain::Ticker) { body["isCable"].as_i64().unwrap_or(0) } else { body["isCable"].as_i64().ok_or_else(|| invalid("是否使用电缆报底不能为空"))? };
        let participants = body["userId"].as_array().ok_or_else(|| invalid("参训人员不能为空"))?.iter().map(|id| id.as_str().filter(|id| !id.is_empty()).map(str::to_owned).ok_or_else(|| invalid("参训人员ID不合法"))).collect::<Outcome<Vec<_>>>()?;
        if matches!(domain,Domain::Ticker) {
            for field in ["codeSort","isAverage","isRandom"] { body[field] = json!(i32::from(jackson_flag(&body[field]))); }
        }
        let kind_field = if matches!(domain,Domain::Key) { "messageType" } else { "type" };
        if cable == 0 && body[kind_field].as_i64().is_none() { return Err(invalid("报文类型不能为空")); }
        if cable == 0 && matches!(domain,Domain::Key) && (body["isAverage"].as_i64().is_none() || body["isRandom"].as_i64().is_none()) { return Err(invalid("报文生成方式不能为空")); }
        let rule_id = body["ruleId"].as_str().ok_or_else(|| invalid("评分规则ID不能为空"))?.to_owned();
        let mut transaction = pool.begin().await?;
        let rule = rows(&mut transaction,"t_grading_rule","id type title score status isDefault content","WHERE id=? FOR SHARE",&[&rule_id]).await?.pop().ok_or_else(|| invalid("未查询到评分规则"))?;
        body["ruleContent"] = if matches!(domain,Domain::Key) { json!(rule.to_string()) } else { rule["content"].clone() };
        body["ruleScore"] = rule["score"].clone();
        frozen_rule(&body,domain)?;
        let now: String = sqlx::query_scalar("SELECT DATE_FORMAT(NOW(6),'%Y-%m-%d %H:%i:%s.%f')").fetch_one(&mut *transaction).await?;
        body["createUser"] = json!(actor.id); body["createTime"] = json!(now); body["status"] = json!(0); body["validTime"] = json!(0); body["protocolVersion"] = json!(1); body["isCable"] = json!(cable);
        body["startTime"] = Value::Null; body["endTime"] = Value::Null;
        if !id_is_int(domain) { body["id"] = json!(uuid::Uuid::new_v4().to_string()); }
        let fields = train_fields(domain).split_whitespace().filter(|field| *field != "id" || !id_is_int(domain)).collect::<Vec<_>>().join(" ");
        let generated = insert_fields(&mut transaction,table(domain),&fields,&body).await?;
        if id_is_int(domain) { body["id"] = json!(generated); }
        for (target,role) in participants.iter().map(|id| (id,0)).chain(std::iter::once((&actor.id,1))) {
            let creator = role == 1 && target == &actor.id;
            let null_finish = matches!(domain, Domain::Ticker) || (creator && !matches!(domain, Domain::Telex));
            let mut member = json!({"trainId":body["id"],"userId":target,"role":role,"isFinish":if null_finish { Value::Null } else { json!(0) },"score":if creator { Value::Null } else { json!(0) },"accuracy":if creator { Value::Null } else { json!("0.00") },"errorNumber":if creator { Value::Null } else { json!(0) },"attempt":0,"createTime":now});
            let fields = if matches!(domain,Domain::Telex) { member["id"] = json!(uuid::Uuid::new_v4().to_string()); "id trainId userId role isFinish score accuracy errorNumber attempt createTime" } else { "trainId userId role isFinish score accuracy errorNumber attempt createTime" };
            insert_fields(&mut transaction,user_table(domain),fields,&member).await?;
        }
        if cable == 0 {
            let generated = total.min(200) as usize;
            for offset in (0..generated).step_by(100) {
                let count = (generated-offset).min(100);
                let groups = if matches!(domain,Domain::Telex) && body["type"] == 0 {
                    match body["patType"].as_i64() { Some(0) => numeric_pairs(count,false), Some(1) => numeric_pairs(count,true), Some(2) => generate_groups(&body,domain,count)?, _ => return Err(invalid("拍发类型不合法")) }
                } else { generate_groups(&body,domain,count)? };
                for (sort,group) in groups.into_iter().enumerate() {
                    save_generated_group(&mut transaction,&body["id"],domain,offset/100+1,sort,group).await?;
                }
            }
        } else {
            let cable_id = body["cableId"].as_str().ok_or_else(|| invalid("固定报编号不能为空"))?;
            let floors = rows(&mut transaction,"t_cable_floor","floorNumber sort moresKey","WHERE cable_id=? ORDER BY floor_number,sort",&[cable_id]).await?;
            let mut grouped: Vec<Vec<String>> = Vec::new();
            let mut previous = Value::Null;
            for floor in floors {
                if floor["floorNumber"] != previous { grouped.push(Vec::new()); previous = floor["floorNumber"].clone(); }
                let keys = stored_json(floor["moresKey"].as_str().ok_or_else(|| invalid("固定报内容缺失"))?)?;
                let group = keys.as_array().ok_or_else(|| invalid("固定报内容不是字符列表"))?.iter().map(|key| key.as_str().ok_or_else(|| invalid("固定报字符不合法"))).collect::<Outcome<Vec<_>>>()?.concat();
                grouped.last_mut().ok_or_else(|| invalid("固定报楼层缺失"))?.push(group);
            }
            let start = body["startPage"].as_u64().unwrap_or(1).saturating_sub(1) as usize;
            let start = if start < grouped.len() { start } else { 0 };
            let pages = total as usize / 100;
            if pages == 0 || pages > grouped.len().saturating_sub(start) { return Err(invalid("所选电缆可用楼层不足")); }
            for (page,groups) in grouped.into_iter().skip(start).take(pages).enumerate() {
                for (sort,group) in groups.into_iter().enumerate() { save_generated_group(&mut transaction,&body["id"],domain,page+1,sort,group).await?; }
            }
        }
        let id = text(&body["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
        let title = if matches!(domain, Domain::Ticker) {
            text(&body["name"]).unwrap_or_default()
        } else {
            text(&body["title"]).unwrap_or_default()
        };
        let result = load_train(&mut transaction, &id, domain, false).await?;
        transaction.commit().await?;
        crate::ws::notify::notify_general_new_train(ws_kind(domain), &id, &title, &participants).await;
        Ok(train_response(result, domain))
    }.await)
}
pub(super) async fn list(state: AppState, user: AuthUser, body: Value, domain: Domain) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let mut connection = pool.acquire().await?;
        let page = body["page"].as_i64().unwrap_or(1).max(1);
        let size = body["rows"].as_i64().unwrap_or(20).clamp(1, 200);
        let filter = format!("WHERE id IN (SELECT train_id FROM {} WHERE user_id=?)", user_table(domain));
        let count = format!("SELECT COUNT(*) FROM {} {filter}", table(domain));
        let total: i64 = sqlx::query_scalar(&count).bind(&user.id).fetch_one(&mut *connection).await?;
        let filter = format!("{filter} ORDER BY create_time DESC,id DESC LIMIT {size} OFFSET {}", (page - 1).saturating_mul(size));
        let mut data = rows(&mut connection, table(domain), train_fields(domain), &filter, &[&user.id]).await?;
        {
            // Java findAll: every item carries its member list (list.js runs `userInfoList.forEach`),
            // booleans for codeSort/isRandom, and the real page count for fixed (cable) reports.
            // The electronic-key list leaves the total alone and hides the frozen rule.
            let mut items = Vec::with_capacity(data.len());
            for mut train in data {
                let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
                if matches!(domain, Domain::Key | Domain::Telex) {
                    train["ruleContent"] = Value::Null;
                }
                if matches!(domain, Domain::Ticker) && train["isCable"] == 1 {
                    let pages: i64 = sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {} WHERE train_id=?", page_table(domain)))
                        .bind(&id)
                        .fetch_one(&mut *connection)
                        .await?;
                    train["messageNumber"] = json!(pages);
                }
                let mut members = members_with_profiles(&mut connection, &id, domain, "id").await?;
                if matches!(domain, Domain::Key) {
                    // Java's findAll member DTO (`find_general_key_pat_user_dto`) has no active time.
                    for member in &mut members { member["activeMillis"] = Value::Null; }
                }
                let infos = members.iter().map(|member| {
                    if matches!(domain, Domain::Telex) {
                        // Java's findAll member DTO (`find_general_telex_pat_user_simple_dto`)
                        // carries only the user, profile and role.
                        let mut info = detail_member(&Value::Null, domain);
                        for field in ["userId", "userName", "userImg", "role"] { info[field] = member[field].clone(); }
                        info["pageAnalyzeVOS"] = Value::Null;
                        info
                    } else { detail_member(member, domain) }
                }).collect::<Vec<_>>();
                let mut item = train_response(train, domain);
                item["userInfoList"] = json!(infos);
                items.push(item);
            }
            data = items;
        }
        Ok(json!({"totalPage":(total+size-1)/size,"totalNumber":total,"currentPage":page,"pageSize":size,"data":data}))
    }.await)
}
pub(super) async fn detail(
    state: AppState,
    user: AuthUser,
    body: Value,
    domain: Domain,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(
        async {
            let id = train_id(&body)?;
            let mut connection = pool.acquire().await?;
            let mut train = load_train(&mut connection, &id, domain, false).await?;
            if train["isCable"] == 1 {
                let page_column = if matches!(domain, Domain::Ticker) {
                    "floor_number"
                } else {
                    "page_number"
                };
                let total_field = if matches!(domain, Domain::Ticker) {
                    "messageNumber"
                } else {
                    "totalNumber"
                };
                let count: i64 = sqlx::query_scalar(&format!(
                    "SELECT COUNT(*) FROM {} WHERE train_id=?",
                    page_table(domain)
                ))
                .bind(&id)
                .fetch_one(&mut *connection)
                .await?;
                let max_page: Option<i64> = sqlx::query_scalar(&format!(
                    "SELECT MAX({page_column}) FROM {} WHERE train_id=?",
                    page_table(domain)
                ))
                .bind(&id)
                .fetch_one(&mut *connection)
                .await?;
                train[total_field] = json!(count);
                train["pageCount"] = max_page.map_or(Value::Null, |value| json!(value));
            }
            // Java `detail` (hand-key): `uid` selects whose record is read. Reading another
            // member needs the creator or a role-1 member (207, administrators not exempt), a
            // non-member target is 202, and the member list is narrowed to that user.
            let requested = if matches!(domain, Domain::Ticker) {
                body.get("uid").and_then(Value::as_str)
            } else {
                None
            };
            if matches!(domain, Domain::Ticker) {
                readable_member(&mut connection, &train, &user, requested, domain).await?;
            } else {
                require_member(&mut connection, &train, &user, domain).await?;
            }
            let mut members = members_with_profiles(&mut connection, &id, domain, "role DESC,id").await?;
            if let Some(uid) = requested.filter(|uid| !uid.is_empty()) {
                members.retain(|member| member["userId"].as_str() == Some(uid));
            }
            let kind = match domain {
                Domain::Key => "key",
                Domain::Telex => "telex",
                Domain::Ticker => "ticker",
            };
            for member in &mut members {
                let target = member["userId"]
                    .as_str()
                    .ok_or_else(|| invalid("参训用户ID缺失"))?
                    .to_owned();
                if matches!(domain, Domain::Key) && train["protocolVersion"] != 1 {
                    member["activeMillis"] = Value::Null;
                }
                if matches!(domain, Domain::Key) && train["protocolVersion"] == 1 {
                    let captures = stored_json(
                        member["capturePages"]
                            .as_str()
                            .ok_or_else(|| invalid("缺少原始采集页索引"))?,
                    )?;
                    let object = captures
                        .as_object()
                        .ok_or_else(|| invalid("缺少原始采集页索引"))?;
                    let mut pages = object
                        .keys()
                        .map(|page| page.parse::<i64>().map_err(|_| invalid("采集页索引无效")))
                        .collect::<Outcome<Vec<_>>>()?;
                    pages.sort_unstable();
                    member["existPageNumber"] = json!(pages.len());
                    member["existNumber"] = json!(pages);
                }
                if matches!(domain, Domain::Ticker) && requested.is_some() {
                    let saved: i64 = sqlx::query_scalar(&format!(
                        "SELECT COUNT(*) FROM {} WHERE train_id=? AND user_id=?",
                        value_table(domain)
                    ))
                    .bind(&id)
                    .bind(&target)
                    .fetch_one(&mut *connection)
                    .await?;
                    let floors: Vec<i32> = sqlx::query_scalar(&format!(
                        "SELECT DISTINCT floor_number FROM {} WHERE train_id=? AND user_id=? ORDER BY floor_number",
                        value_table(domain)
                    ))
                    .bind(&id)
                    .bind(&target)
                    .fetch_all(&mut *connection)
                    .await?;
                    member["existPageNumber"] = json!(saved);
                    member["existNumber"] = json!(floors);
                }
                if matches!(domain, Domain::Ticker) && train["protocolVersion"] == 1 {
                    member["validTime"] = json!(member["activeMillis"].as_i64().unwrap_or(0) / 1000);
                }
                if matches!(domain, Domain::Key) {
                    member["pageAnalyzeVOS"] = page_analyze(&mut connection, &train, member).await?;
                }
                if matches!(domain, Domain::Telex) {
                    // Java's detail member DTO (`find_general_telex_pat_user_dto`) has no
                    // valid_time/speed_log columns; the statistics are computed per page.
                    for field in ["validTime", "speedLog"] { member[field] = Value::Null; }
                    member["pageAnalyzeVOS"] = telex_page_analyze(&mut connection, &train, &target).await?;
                }
                member["userStatus"] =
                    json!(crate::ws::notify::general_connection_status(kind, &id, &target)
                        .await
                        .unwrap_or(0));
            }
            let members = members
                .iter()
                .map(|member| detail_member(member, domain))
                .collect::<Vec<_>>();
            train = train_response(train, domain);
            train["userInfoList"] = json!(members);
            Ok(train)
        }
        .await,
    )
}
pub(super) async fn set_status(
    state: AppState,
    user: AuthUser,
    id: String,
    status: i32,
    domain: Domain,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(async {
        let ticker = matches!(domain, Domain::Ticker);
        // Java answers the hand-key status endpoint with "ok" and reports a restart as 202.
        let done = || if ticker { json!("ok") } else { json!("") };
        let mut transaction = pool.begin().await?;
        let train = load_train(&mut transaction, &id, domain, true).await?;
        if !manages(&mut transaction, &train, &user, domain).await? { return Err(denied()); }
        let protocol = || invalid("旧训练缺少原始采集协议，请重新创建训练");
        if ticker && train["protocolVersion"] != 1 { return Err(invalid("旧训练缺少原始采集协议，请重新创建训练；历史成绩保持不变")); }
        if !matches!(status, 1 | 2) { return Err(invalid("训练状态不合法")); }
        if !ticker && train["protocolVersion"] != 1 { return Err(protocol()); }
        if train["status"] == status || (!matches!(domain, Domain::Telex) && train["status"] == 3 && status == 2) { transaction.commit().await?; return Ok(done()); }
        if status == 1 {
            if train["status"] != 0 { return Err(if matches!(domain, Domain::Telex) { terminal("训练不能重新开始") } else { invalid("训练不能重新开始") }); }
            frozen_rule(&train, domain)?;
            let sql = format!("UPDATE {} SET status=1,start_time=NOW(6) WHERE id=?", table(domain));
            sqlx::query(&sql).bind(&id).execute(&mut *transaction).await?;
            // The member tables are indexed by primary key only: read the student ids plainly
            // (the training row is locked) and stamp the shared start time row by row.
            let started: String = sqlx::query_scalar(&format!("SELECT DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s.%f') FROM {} WHERE id=?", table(domain)))
                .bind(&id).fetch_one(&mut *transaction).await?;
            let students: Vec<String> = sqlx::query_scalar(&format!("SELECT CAST(id AS CHAR) FROM {} WHERE train_id=? AND role=0 ORDER BY id", user_table(domain)))
                .bind(&id).fetch_all(&mut *transaction).await?;
            let sql = format!("UPDATE {} SET capture_started_at=? WHERE id=?", user_table(domain));
            for student in students {
                sqlx::query(&sql).bind(&started).bind(student).execute(&mut *transaction).await?;
            }
        } else {
            if train["status"] != 1 { return Err(if ticker { invalid("训练不在进行中") } else { terminal("训练不在进行中") }); }
            let closing = closing_status(domain);
            let sql = format!("UPDATE {} SET status=?,end_time=NOW(6),valid_time=GREATEST(0,TIMESTAMPDIFF(SECOND,start_time,NOW(6))) WHERE id=?", table(domain));
            sqlx::query(&sql).bind(closing).bind(&id).execute(&mut *transaction).await?;
            if closing == 3 {
                let unfinished: i64 = sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {} WHERE train_id=? AND role=0 AND COALESCE(is_finish,0)<>1", user_table(domain)))
                    .bind(&id).fetch_one(&mut *transaction).await?;
                if unfinished == 0 {
                    sqlx::query(&format!("UPDATE {} SET status=2 WHERE id=?", table(domain))).bind(&id).execute(&mut *transaction).await?;
                }
            }
        }
        transaction.commit().await?;
        Ok(done())
    }.await)
}
pub(super) async fn delete(
    state: AppState,
    user: AuthUser,
    id: String,
    domain: Domain,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(
        async {
            // Excludes a socket opening this room until the room is closed (Java RoomLifecycleLocks).
            let _room = crate::ws::manager::general_room_lifecycle(ws_kind(domain), &id).await;
            let mut transaction = pool.begin().await?;
            let train = load_train(&mut transaction, &id, domain, true).await?;
            if !manages(&mut transaction, &train, &user, domain).await? {
                return Err(denied());
            }
            for child in [value_table(domain), page_table(domain), user_table(domain)] {
                delete_rows(&mut transaction, child, "WHERE train_id=?", &[&id]).await?;
            }
            if matches!(domain, Domain::Key) {
                for child in [
                    "general_key_pat_train_more",
                    "general_key_pat_user_value_resolver",
                ] {
                    delete_rows(&mut transaction, child, "WHERE train_id=?", &[&id]).await?;
                }
            }
            sqlx::query(&format!("DELETE FROM {} WHERE id=?", table(domain)))
                .bind(&id)
                .execute(&mut *transaction)
                .await?;
            transaction.commit().await?;
            crate::ws::notify::close_general_room(ws_kind(domain), &id).await;
            Ok(json!(true))
        }
        .await,
    )
}
pub(super) async fn statistic(
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
        let mut connection = pool.acquire().await?;
        let train = load_train(&mut connection,&id,domain,false).await?;
        require_member(&mut connection,&train,&actor,domain).await?;
        let members = rows(&mut connection,user_table(domain),member_fields(domain),"WHERE train_id=? AND role=0 ORDER BY id",&[&id]).await?;
        let mut counts = [0i64;3];
        let mut totals = HashMap::<String,i64>::new();
        let mut tendencies = Vec::with_capacity(members.len());
        for member in &members {
            let Some(score) = member["score"].as_f64() else { continue; };
            counts[if score >= 90.0 { 0 } else if score >= 70.0 { 1 } else { 2 }] += 1;
            let source = if matches!(domain,Domain::Ticker) { "statisticInfo" } else { "deductInfo" };
            if let Some(text) = member[source].as_str().filter(|text| !text.trim().is_empty()) {
                let info = stored_json(text)?;
                if let Some(info) = info.as_object() {
                    for (key,value) in info { if let Some(number) = value.as_i64() { *totals.entry(key.clone()).or_default() += number; } }
                }
            }
            let target = member["userId"].as_str().ok_or_else(|| invalid("参训用户ID缺失"))?;
            let Some(profile) = rows(&mut connection,"t_user","id userName userImg","WHERE id=?",&[target]).await?.pop() else { continue; };
            // Java `findByUseridTop2` (hand-key): settled trainings only, newest created first.
            let history_filter = if matches!(domain,Domain::Ticker) { "WHERE user_id=? AND create_time<? AND is_finish=1 ORDER BY create_time DESC LIMIT 2" } else { "WHERE user_id=? AND create_time<? ORDER BY finish_time DESC LIMIT 2" };
            let history = rows(&mut connection,user_table(domain),"score",history_filter,&[target,member["createTime"].as_str().unwrap_or("")]).await?;
            let mut tendency = json!({"userId":target,"userName":profile["userName"],"userImg":profile["userImg"],"thisScore":member["score"],"lastScore":history.first().map(|row| &row["score"]).unwrap_or(&Value::Null),"lastLastScore":history.get(1).map(|row| &row["score"]).unwrap_or(&Value::Null)});
            if !matches!(domain,Domain::Ticker) {
                // Java's statistics builder sets no status for the electronic key.
                tendency["status"] = if matches!(domain, Domain::Key) { Value::Null } else { member["isFinish"].clone() };
                for field in ["lastScore","lastLastScore"] { if tendency[field].is_null() { tendency[field] = json!(0); } }
            }
            tendencies.push(tendency);
        }
        let mut report = json!({});
        for (name,count) in ["good","nice","belowStandard"].into_iter().zip(counts) {
            report[name] = json!({"peopleNumber":count,"rate":percentage(count,members.len() as i64)});
        }
        let mut errors = json!({});
        let mut result = json!({"schoolReport":report,"userTendencyVO":tendencies});
        if matches!(domain,Domain::Ticker) {
            for (source,target) in [("dot","dot"),("line","line"),("code","codeGap"),("word","wordGap"),("group","groupGap")] {
                let total: i64 = ["MinNumber","MaxNumber","PerfectNumber"].iter().map(|suffix| totals.get(&format!("{source}{suffix}")).copied().unwrap_or(0)).sum();
                for direction in ["Min","Max"] { errors[format!("{target}{direction}")] = json!(percentage(totals.get(&format!("{source}{direction}Number")).copied().unwrap_or(0),total)); }
            }
            result["errorInfoVO"] = errors;
        } else {
            let fields = if matches!(domain,Domain::Key) { "errorNumber lackNumber moreNumber lackLineNumber moreLineNumber lackGroupNumber moreGroupNumber" } else { "errorCodeNumber errorPageNumber correctMistakesNumber lessPageNumber lessReturnLineNumber muchLessCodeNumber muchLessGroupsNumber muchLessLineNumber nonStandartNumber" };
            for field in fields.split_whitespace() { errors[field] = json!(totals.get(field).copied().unwrap_or(0)); }
            result["errorCollect"] = errors;
        }
        Ok(result)
    }.await)
}
pub(super) async fn finish_participant(
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
        // Java order: lockedTrain -> student -> requireAttempt, so a missing train is 202 before any 208.
        let attempt = body["attempt"].as_i64();
        let mut transaction = pool.begin().await?;
        let train = load_train(&mut transaction, &id, domain, true).await?;
        let member = locked_member(&mut transaction, &id, &actor.id, domain).await?;
        if member["role"] != 0 { return Err(if matches!(domain, Domain::Telex) { denied() } else { invalid(STUDENT_MISSING) }); }
        let attempt = attempt.ok_or_else(|| terminal("训练轮次已变化，请重新读取训练"))?;
        if member["attempt"] != attempt { return Err(terminal("训练轮次已变化，请重新读取训练")); }
        if member["isFinish"] == 1 {
            let result = finish_member(&member, domain);
            transaction.commit().await?;
            return Ok(if matches!(domain, Domain::Ticker) { train_response(train, domain) } else { json!([result]) });
        }
        if train["protocolVersion"] != 1 {
            return Err(if matches!(domain, Domain::Telex) { terminal("旧训练缺少原始采集协议，请重新创建训练；历史成绩保持不变") } else { invalid("旧训练缺少原始采集协议，请重新创建训练") });
        }
        let status = train["status"].as_i64().unwrap_or(-1);
        frozen_rule(&train, domain)?;
        // Java settles with whatever pages exist: a missing page is counted as lacking groups,
        // never generated at finish.
        let mut telex_settled = None;
        let mut settled_all = false;
        let mut settled_users = vec![actor.id.clone()];
        if !matches!(domain, Domain::Telex) {
            if status == i64::from(closing_status(domain)) {
                // Java accepts an in-flight finish during the 60-second grace
                // window; after it expires it settles every remaining student.
                if closing_window_open(&mut transaction, &id, domain).await? {
                    capture_bound(&mut transaction, &id, &actor.id, domain).await?;
                } else {
                    settled_all = true;
                    settled_users.clear();
                    let students: Vec<String> = sqlx::query_scalar(&format!(
                        "SELECT user_id FROM {} WHERE train_id=? AND role=0 ORDER BY id",
                        user_table(domain)
                    ))
                    .bind(&id)
                    .fetch_all(&mut *transaction)
                    .await?;
                    for user_id in students {
                        let candidate = locked_member(&mut transaction, &id, &user_id, domain).await?;
                        if candidate["isFinish"] == 1 { continue; }
                        match domain {
                            Domain::Key => settle_key(&mut transaction, &train, &candidate, &user_id).await?,
                            Domain::Ticker => settle_ticker(&mut transaction, &train, &candidate, &user_id).await?,
                            Domain::Telex => unreachable!(),
                        }
                        settled_users.push(user_id);
                    }
                    sqlx::query(&format!("UPDATE {} SET status=2 WHERE id=?", table(domain)))
                        .bind(&id)
                        .execute(&mut *transaction)
                        .await?;
                }
            } else if status != 1 {
                return Err(invalid("训练不在进行中"));
            }
        }
        // An expired closing room was fully settled above.  Otherwise settle
        // only the caller, matching Java's idempotent participant finish.
        if !settled_all {
            match domain {
                Domain::Key => settle_key(&mut transaction, &train, &member, &actor.id).await?,
                Domain::Telex => telex_settled = Some(settle_telex(&mut transaction, &train, &member, &actor.id).await?),
                Domain::Ticker => settle_ticker(&mut transaction, &train, &member, &actor.id).await?,
            }
        }
        let recipients: Vec<String> = sqlx::query_scalar(&format!(
            "SELECT user_id FROM {} WHERE train_id=? AND role=1",
            user_table(domain)
        ))
        .bind(&id)
        .fetch_all(&mut *transaction)
        .await?;
        if matches!(domain, Domain::Key | Domain::Ticker) && status == 3 {
            let unfinished: i64 = sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {} WHERE train_id=? AND role=0 AND COALESCE(is_finish,0)<>1", user_table(domain))).bind(&id).fetch_one(&mut *transaction).await?;
            if unfinished == 0 { sqlx::query(&format!("UPDATE {} SET status=2 WHERE id=?", table(domain))).bind(&id).execute(&mut *transaction).await?; }
        }
        let result = if matches!(domain, Domain::Ticker) {
            train_response(load_train(&mut transaction, &id, domain, false).await?, domain)
        } else {
            let member = load_member(&mut transaction, &id, &actor.id, domain)
                .await?
                .ok_or_else(|| invalid("未查询到参训记录"))?;
            let mut result = finish_member(&member, domain);
            if let Some(settled) = telex_settled {
                // Java answers with the in-memory entity, not the stored decimal columns.
                result["accuracy"] = json!(settled.accuracy);
                result["speed"] = json!(settled.speed);
            }
            json!([result])
        };
        transaction.commit().await?;
        for user_id in settled_users {
            crate::ws::notify::notify_general_result(ws_kind(domain), &id, &user_id, &recipients).await;
        }
        Ok(result)
    }.await)
}
pub(super) fn closing_status(domain: Domain) -> i32 {
    match domain {
        Domain::Telex => 2,
        Domain::Key | Domain::Ticker => 3,
    }
}
