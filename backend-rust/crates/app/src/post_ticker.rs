use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rand::seq::SliceRandom;
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, MySql, Row, Transaction};

const COLS: &str = "id,name,is_cable,rate,type,message_number,code_short,valid_time,score,status,user_id,result,more_code,lack_code,error_code,more_group,lack_group,total_number,disturb,ratio,is_start_sign,is_low_rate,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') create_time_text,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') start_time_text,DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') end_time_text";

#[derive(Debug)]
enum Error {
    Business(i32, &'static str),
    Database(sqlx::Error),
    Json(serde_json::Error),
}
impl From<sqlx::Error> for Error {
    fn from(e: sqlx::Error) -> Self {
        Self::Database(e)
    }
}
impl From<serde_json::Error> for Error {
    fn from(e: serde_json::Error) -> Self {
        Self::Json(e)
    }
}
fn respond(result: Result<Option<Value>, Error>) -> Response {
    match result {
        Ok(value) => envelope_json(StatusCode::OK, 200, value, "ok", ""),
        Err(Error::Business(code, message)) => {
            let description = if code == 207 { "" } else { message };
            envelope_response(StatusCode::OK, code, message, description)
        }
        Err(error) => {
            match error {
                Error::Database(error) => {
                    tracing::warn!(%error, "post ticker database operation failed")
                }
                Error::Json(error) => tracing::warn!(%error, "post ticker stored JSON invalid"),
                Error::Business(_, _) => unreachable!(),
            }
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}
fn required_id(id: Option<&str>) -> Result<&str, Error> {
    id.filter(|v| !v.is_empty())
        .ok_or(Error::Business(202, "请求参数错误"))
}
async fn owned(
    tx: &mut Transaction<'_, MySql>,
    id: &str,
    user: &AuthUser,
) -> Result<MySqlRow, Error> {
    let row = sqlx::query("SELECT *,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') start_time_text FROM t_post_ticker_tape_train WHERE id=? FOR UPDATE")
        .bind(id).fetch_optional(&mut **tx).await?.ok_or(Error::Business(202, "未查询到训练"))?;
    if row.get::<Option<String>, _>("user_id").as_deref() != Some(user.id.as_str()) {
        return Err(Error::Business(207, "无权限"));
    }
    Ok(row)
}
fn item(row: &MySqlRow, detail: bool) -> Result<Value, Error> {
    let content = if detail {
        row.get::<Option<String>, _>("code_message_body")
            .filter(|value| !value.is_empty())
            .map(|value| serde_json::from_str::<Value>(&value))
            .transpose()?
    } else {
        list_message_body(row.get::<Option<&str>, _>("code_message_body"))
    };
    Ok(json!({
        "id":row.get::<String,_>("id"),"name":row.get::<Option<String>,_>("name"),
        "isCable":row.get::<Option<i32>,_>("is_cable"),"trainId":null,
        "rate":row.get::<Option<i32>,_>("rate"),"type":row.get::<Option<i32>,_>("type"),
        "codeMessageBody":content,"messageNumber":row.get::<Option<i32>,_>("message_number"),
        "codeShort":row.get::<Option<i32>,_>("code_short"),"validTime":row.get::<Option<String>,_>("valid_time"),
        "score":row.get::<Option<String>,_>("score"),"status":row.get::<Option<i32>,_>("status"),
        "result":row.get::<Option<String>,_>("result"),"moreCode":row.get::<Option<i32>,_>("more_code"),
        "lackCode":row.get::<Option<i32>,_>("lack_code"),"errorCode":row.get::<Option<i32>,_>("error_code"),
        "moreGroup":row.get::<Option<i32>,_>("more_group"),"lackGroup":row.get::<Option<i32>,_>("lack_group"),
        "totalNumber":row.get::<Option<i32>,_>("total_number"),"images":null,"isLowRate":row.get::<Option<i32>,_>("is_low_rate"),
        "disturb":row.get::<Option<String>,_>("disturb"),"ratio":row.get::<Option<i32>,_>("ratio"),
        "isStartSign":row.get::<Option<i32>,_>("is_start_sign"),"pageNumber":null,
        "createTime":row.get::<Option<String>,_>("create_time_text"),
        "startTime":row.get::<Option<String>,_>("start_time_text"),
        "endTime":row.get::<Option<String>,_>("end_time_text")
    }))
}

// PojoUtils copies String -> List<Map> through Hutool unWrap/splitTrim, not JSON.
// Any nonempty token fails Map conversion and the ignoreError copy leaves null.
fn list_message_body(stored: Option<&str>) -> Option<Value> {
    let stored = stored?;
    let unwrapped = stored
        .strip_prefix('[')
        .and_then(|text| text.strip_suffix(']'))
        .unwrap_or(stored);
    unwrapped
        .split(',')
        .all(|token| token.trim().is_empty())
        .then(|| json!([]))
}

#[derive(Deserialize)]
pub(crate) struct IdRequest {
    pub id: Option<String>,
}
#[derive(Deserialize)]
pub(crate) struct PageBody {
    page: Option<i64>,
    rows: Option<i64>,
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PostTickerPageQuery {
    train_id: Option<String>,
    page_number: Option<i32>,
}

pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(page): Json<PageBody>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    respond(async {
        let no = page.page.unwrap_or(1).max(1);
        let size = page.rows.unwrap_or(20).clamp(1, 200);
        let total: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_post_ticker_tape_train WHERE user_id=?").bind(&user.id).fetch_one(pool).await?;
        let records = sqlx::query(&format!("SELECT code_message_body,{COLS} FROM t_post_ticker_tape_train WHERE user_id=? ORDER BY create_time DESC LIMIT ? OFFSET ?"))
            .bind(&user.id).bind(size).bind((no - 1) * size).fetch_all(pool).await?;
        let data = records.iter().map(|row| item(row, false)).collect::<Result<Vec<_>, _>>()?;
        Ok(Some(json!({"totalPage":(total+size-1)/size,"totalNumber":total,"currentPage":no-1,"pageSize":size,"data":data})))
    }.await)
}

pub(crate) async fn detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<IdRequest>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    respond(async {
        let id = required_id(body.id.as_deref())?;
        let mut tx = pool.begin().await?;
        owned(&mut tx, id, &user).await?;
        let row = sqlx::query(&format!("SELECT code_message_body,{COLS} FROM t_post_ticker_tape_train WHERE id=?")).bind(id).fetch_one(&mut *tx).await?;
        let mut data = item(&row, true)?;
        let images: Vec<Option<String>> = sqlx::query_scalar("SELECT image FROM t_post_ticker_tape_train_page_value WHERE train_id=? ORDER BY page_number").bind(id).fetch_all(&mut *tx).await?;
        data["images"] = json!(images);
        data["isStartSign"] = json!(row.get::<Option<i32>,_>("is_start_sign").unwrap_or(0));
        if row.get::<Option<i32>,_>("is_cable") == Some(1) {
            let count = sqlx::query("SELECT COUNT(*) total,MAX(page_number) maximum FROM t_post_ticker_tape_train_page WHERE train_id=?").bind(id).fetch_one(&mut *tx).await?;
            data["totalNumber"] = json!(count.get::<i64,_>("total"));
            data["pageNumber"] = json!(count.get::<Option<i32>,_>("maximum"));
        }
        tx.commit().await?;
        Ok(Some(data))
    }.await)
}

// The Java generator carries a ten-group uniqueness window within each generated batch.
fn generate_groups(
    kind: i32,
    count: usize,
    start_page: i32,
    average: bool,
    random: bool,
) -> Result<Vec<(i32, i32, String)>, Error> {
    let alphabet: &[u8] = match kind {
        0 => b"0123456789",
        1 => b"ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        2 => b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        _ => return Err(Error::Business(202, "未知类型")),
    };
    let mut rng = rand::thread_rng();
    let start = if kind == 0 {
        0
    } else {
        ((start_page as usize - 1) * 400) % alphabet.len()
    };
    let mut values: Vec<u8> = (0..count * 4)
        .map(|i| alphabet[(start + i) % alphabet.len()])
        .collect();
    if average {
        values.shuffle(&mut rng);
    }
    let mut recent = std::collections::VecDeque::<String>::with_capacity(10);
    let mut result = Vec::with_capacity(count);
    for i in 0..count {
        let mut group = [0u8; 4];
        if average {
            for j in 0..4 {
                let position = i * 4 + j;
                if group[..j].contains(&values[position]) {
                    if let Some(next) = (position + 1..values.len())
                        .find(|next| !group[..j].contains(&values[*next]))
                    {
                        values.swap(position, next);
                    }
                }
                group[j] = values[position];
            }
            if recent.iter().any(|v| v.as_bytes() == group) {
                if let Some(next) = (i * 4 + 4..values.len()).find(|next| {
                    let mut candidate = group;
                    candidate[3] = values[*next];
                    !recent.iter().any(|v| v.as_bytes() == candidate)
                }) {
                    group[3] = values[next];
                    values.swap(i * 4 + 3, next);
                }
            }
        } else {
            if random {
                for (j, c) in alphabet.choose_multiple(&mut rng, 4).enumerate() {
                    group[j] = *c;
                }
            } else {
                group.copy_from_slice(&values[i * 4..i * 4 + 4]);
            }
            for _ in 0..20 {
                if !recent.iter().any(|v| v.as_bytes() == group) {
                    break;
                }
                for (j, c) in alphabet.choose_multiple(&mut rng, 4).enumerate() {
                    group[j] = *c;
                }
            }
        }
        let key: String = group.into_iter().map(char::from).collect();
        recent.push_back(key.clone());
        if recent.len() > 10 {
            recent.pop_front();
        }
        // Average mode increments its page counter on the 400th character before saving.
        let page = start_page + (i / 100) as i32;
        let sort = if average {
            ((i + 1) % 100) as i32
        } else {
            (i % 100) as i32
        };
        result.push((page, sort, key));
    }
    Ok(result)
}
async fn save_groups(
    tx: &mut Transaction<'_, MySql>,
    id: &str,
    groups: Vec<(i32, i32, String)>,
) -> Result<(), Error> {
    for (page, sort, key) in groups {
        sqlx::query("INSERT INTO t_post_ticker_tape_train_page (id,train_id,page_number,sort,`key`) VALUES (?,?,?,?,?)")
            .bind(uuid::Uuid::new_v4().to_string()).bind(id).bind(page).bind(sort).bind(key).execute(&mut **tx).await?;
    }
    Ok(())
}

pub(crate) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(mut body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    respond(async {
        if !body.is_object() { return Err(Error::Business(202, "请求参数错误")); }
        let cable = match body.get("isCable") {
            Some(value) => value.as_i64().ok_or(Error::Business(202, "是否使用固定报底不能为空"))?,
            None => 0,
        };
        let total = body.get("totalNumber").and_then(Value::as_i64).and_then(|v| i32::try_from(v).ok()).ok_or(Error::Business(202, "报文组数不能为空"))?;
        let kind = body.get("type").and_then(Value::as_i64).and_then(|v| i32::try_from(v).ok());
        if cable == 0 && kind.is_none() { return Err(Error::Business(202, "报文类型不能为空")); }
        if total < 0 { return Err(Error::Business(202, "报文组数无效")); }
        let content = body.get("codeMessageBody").filter(|value| !value.is_null()).map(Value::to_string).unwrap_or_default();
        let id = uuid::Uuid::new_v4().to_string();
        let mut tx = pool.begin().await?;
        sqlx::query("INSERT INTO t_post_ticker_tape_train (id,name,is_cable,rate,type,code_message_body,code_short,valid_time,status,user_id,more_code,lack_code,error_code,more_group,lack_group,total_number,disturb,is_avg,is_random,ratio,is_start_sign,create_time) VALUES (?,?,?,?,?,?,?,'0',0,?,0,0,0,0,0,?,?,?,?,?,?,NOW(6))")
            .bind(&id).bind(body.get("name").and_then(Value::as_str)).bind(cable).bind(body.get("rate").and_then(Value::as_i64)).bind(kind).bind(content)
            .bind(body.get("codeShort").and_then(Value::as_i64)).bind(&user.id).bind(total).bind(body.get("disturb").and_then(Value::as_str))
            .bind(body.get("isAvg").and_then(Value::as_i64)).bind(body.get("isRandom").and_then(Value::as_i64)).bind(body.get("ratio").and_then(Value::as_i64))
            .bind(body.get("isStartSign").and_then(Value::as_i64).unwrap_or(1)).execute(&mut *tx).await?;
        if cable == 0 {
            let groups = generate_groups(kind.ok_or(Error::Business(202, "报文类型不能为空"))?, total.min(200) as usize, 1, body.get("isAvg").and_then(Value::as_i64) == Some(1), body.get("isRandom").and_then(Value::as_i64) == Some(1))?;
            save_groups(&mut tx, &id, groups).await?;
        } else {
            let cable_id = required_id(body.get("cableId").and_then(Value::as_str))?;
            let floors = sqlx::query("SELECT floor_number,mores_key FROM t_cable_floor WHERE cable_id=? ORDER BY floor_number,sort").bind(cable_id).fetch_all(&mut *tx).await?;
            let mut floor_groups = std::collections::BTreeMap::<i32, Vec<String>>::new();
            for row in floors {
                let number = row.get::<Option<i32>,_>("floor_number").ok_or(Error::Business(202, "报底页码缺失"))?;
                let raw = row.get::<Option<String>,_>("mores_key").ok_or(Error::Business(202, "报底内容缺失"))?;
                let keys: Option<Vec<String>> = serde_json::from_str(&raw)?;
                let floor = floor_groups.entry(number).or_default();
                if let Some(keys) = keys { floor.push(keys.concat()); }
            }
            let mut skip = body.get("startPage").and_then(Value::as_i64).unwrap_or(1).saturating_sub(1).max(0) as usize;
            if skip >= floor_groups.len() { skip = 0; }
            let pages = (total / 100) as usize;
            if pages == 0 { return Err(Error::Business(202, "报文组数不足一页，无法建立训练")); }
            if floor_groups.len().saturating_sub(skip) < pages { return Err(Error::Business(202, "所选电缆可用楼层不足")); }
            for (index, (_, floor)) in floor_groups.into_iter().skip(skip).take(pages).enumerate() {
                let groups = floor.into_iter().enumerate().map(|(sort, key)| ((index + 1) as i32, sort as i32, key)).collect();
                save_groups(&mut tx, &id, groups).await?;
            }
        }
        tx.commit().await?;
        let mut response = serde_json::Map::new();
        for field in ["name", "cableId", "startPage", "rate", "type", "codeShort", "codeMessageBody", "isLowRate", "disturb", "isAvg", "isRandom", "totalNumber", "ratio", "isStartSign"] {
            response.insert(field.to_owned(), body.as_object_mut().expect("validated object").remove(field).unwrap_or(Value::Null));
        }
        response.insert("isCable".into(), json!(cable));
        response.insert("id".into(), json!(id));
        Ok(Some(Value::Object(response)))
    }.await)
}

async fn lifecycle(state: AppState, user: AuthUser, id: Option<String>, action: i32) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    respond(async {
        let id = required_id(id.as_deref())?;
        let mut tx = pool.begin().await?;
        let row = owned(&mut tx, id, &user).await?;
        let status = row.get::<Option<i32>,_>("status").ok_or(Error::Business(202, "训练状态缺失"))?;
        if action != 0 && matches!(status, 2 | 3) { return Err(Error::Business(208, "训练已结束")); }
        match action {
            0 => {
                if status == 0 { return Err(Error::Business(202, "训练还未开始，无需重置")); }
                sqlx::query("DELETE FROM t_post_ticker_tape_train_page_value WHERE train_id=?").bind(id).execute(&mut *tx).await?;
                sqlx::query("UPDATE t_post_ticker_tape_train SET status=0,start_time=NULL WHERE id=?").bind(id).execute(&mut *tx).await?;
            }
            1 => {
                sqlx::query("UPDATE t_post_ticker_tape_train SET status=1,start_time=NOW(6) WHERE id=?").bind(id).execute(&mut *tx).await?;
            }
            _ => {
                if status == 0 || row.get::<Option<String>, _>("start_time_text").is_none() {
                    return Err(Error::Business(202, "训练还未开始，无法结算"));
                }
                sqlx::query("UPDATE t_post_ticker_tape_train SET status=2,end_time=NOW(6),valid_time=TIMESTAMPDIFF(SECOND,start_time,NOW(6)) WHERE id=?").bind(id).execute(&mut *tx).await?;
            }
        }
        tx.commit().await?;
        Ok(None)
    }.await)
}
pub(crate) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<IdRequest>,
) -> Response {
    lifecycle(state, user, body.id, 1).await
}
pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<IdRequest>,
) -> Response {
    lifecycle(state, user, body.id, 2).await
}
pub(crate) async fn reset(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<IdRequest>,
) -> Response {
    lifecycle(state, user, body.id, 0).await
}

#[derive(Deserialize)]
pub(crate) struct UploadRequest {
    id: Option<String>,
    result: Option<Vec<Vec<String>>>,
    images: Option<Vec<Option<String>>>,
}
pub(crate) async fn upload_result(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<UploadRequest>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    respond(async {
        let id = required_id(body.id.as_deref())?;
        let mut tx = pool.begin().await?;
        let train = owned(&mut tx, id, &user).await?;
        let existing: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_post_ticker_tape_train_page_value WHERE train_id=?").bind(id).fetch_one(&mut *tx).await?;
        if train.get::<Option<i32>,_>("status") == Some(3) || existing > 0 { return Err(Error::Business(208, "训练已结束")); }
        let result = body.result.as_ref().filter(|v| !v.is_empty()).ok_or(Error::Business(202, "填报结果不能为空"))?;
        let images = body.images.as_ref().filter(|v| v.len() == result.len()).ok_or(Error::Business(202, "截图数量与填报页数不一致"))?;
        let mut more_group = 0i64; let mut lack_group = 0i64; let mut more_code = 0i64; let mut lack_code = 0i64; let mut error_code = 0i64; let mut total_groups = 0i64;
        for (page_index, groups) in result.iter().enumerate() {
            let pages = sqlx::query("SELECT id,`key` FROM t_post_ticker_tape_train_page WHERE train_id=? AND page_number=? ORDER BY sort").bind(id).bind((page_index + 1) as i32).fetch_all(&mut *tx).await?;
            total_groups += groups.len() as i64;
            for (index, group) in groups.iter().enumerate() {
                if let Some(page) = pages.get(index) {
                    let expected = page.get::<Option<String>,_>("key").ok_or(Error::Business(202, "报底内容缺失"))?;
                    if expected != *group {
                        let delta = group.encode_utf16().count() as i64 - expected.encode_utf16().count() as i64;
                        if group.is_empty() { lack_group += 1; } else if delta > 0 { more_code += delta; } else if delta < 0 { lack_code -= delta; } else { error_code += 1; }
                    }
                    sqlx::query("UPDATE t_post_ticker_tape_train_page SET value=? WHERE id=?").bind(group).bind(page.get::<String,_>("id")).execute(&mut *tx).await?;
                } else if !group.is_empty() { more_group += 1; }
            }
            sqlx::query("INSERT INTO t_post_ticker_tape_train_page_value (id,train_id,page_number,value,image) VALUES (?,?,?,?,?)")
                .bind(uuid::Uuid::new_v4().to_string()).bind(id).bind((page_index + 1) as i32).bind(serde_json::to_string(groups)?).bind(&images[page_index]).execute(&mut *tx).await?;
        }
        let total = train.get::<Option<i32>,_>("total_number").ok_or(Error::Business(202, "训练总组数缺失"))? as i64;
        lack_group += (total - total_groups).max(0);
        let score = (100 - more_group - lack_group - more_code - lack_code - error_code).to_string();
        sqlx::query("UPDATE t_post_ticker_tape_train SET score=?,error_code=?,more_code=?,lack_code=?,more_group=?,lack_group=?,status=3,result=? WHERE id=?")
            .bind(score).bind(error_code).bind(more_code).bind(lack_code).bind(more_group).bind(lack_group).bind(serde_json::to_string(result)?).bind(id).execute(&mut *tx).await?;
        let row = sqlx::query(&format!("SELECT code_message_body,{COLS} FROM t_post_ticker_tape_train WHERE id=?")).bind(id).fetch_one(&mut *tx).await?;
        let mut data = item(&row, true)?;
        // Unlike getById, Java's uploadResult copies the entity into a VO without parsing a nonempty JSON body.
        if data["codeMessageBody"].as_array().is_some_and(|body| !body.is_empty()) {
            data["codeMessageBody"] = Value::Null;
        }
        data["images"] = json!(images);
        tx.commit().await?;
        Ok(Some(data))
    }.await)
}

fn page_json(row: &MySqlRow) -> Value {
    json!({"id":null,"trainId":row.get::<Option<String>,_>("train_id"),"pageNumber":row.get::<Option<i32>,_>("page_number"),"key":row.get::<Option<String>,_>("key"),"value":row.get::<Option<String>,_>("value"),"sort":row.get::<Option<i32>,_>("sort")})
}
pub(crate) async fn find_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<PostTickerPageQuery>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    respond(async {
        let id = required_id(query.train_id.as_deref())?;
        let mut tx = pool.begin().await?;
        let train = owned(&mut tx, id, &user).await?;
        let number = query.page_number.filter(|v| *v > 0).ok_or(Error::Business(202, "页码不正确"))?;
        let total = train.get::<Option<i32>,_>("total_number").ok_or(Error::Business(202, "训练总组数缺失，无法取页"))?;
        let maximum = (i64::from(total) + 99) / 100;
        let sql = "SELECT train_id,page_number,`key`,value,sort FROM t_post_ticker_tape_train_page WHERE train_id=? AND page_number=? ORDER BY sort";
        let rows = sqlx::query(sql).bind(id).bind(number).fetch_all(&mut *tx).await?;
        let mut pages = rows.iter().map(page_json).collect::<Vec<_>>();
        if pages.is_empty() && i64::from(number) <= maximum {
            let count = if i64::from(number) == maximum { total - (number - 1) * 100 } else { 100 };
            let kind = train.get::<Option<i32>,_>("type").ok_or(Error::Business(202, "报文类型缺失，无法生成报底"))?;
            let groups = generate_groups(kind, count.max(0) as usize, number, train.get::<Option<i32>,_>("is_avg") == Some(1), train.get::<Option<i32>,_>("is_random") == Some(1))?;
            for (page, sort, key) in groups {
                let page_id = uuid::Uuid::new_v4().to_string();
                sqlx::query("INSERT INTO t_post_ticker_tape_train_page (id,train_id,page_number,sort,`key`) VALUES (?,?,?,?,?)")
                    .bind(&page_id).bind(id).bind(page).bind(sort).bind(&key).execute(&mut *tx).await?;
                pages.push(json!({"id":null,"trainId":id,"pageNumber":page,"sort":sort,"key":key,"value":null}));
            }
            pages.sort_by_key(|row| row["sort"].as_i64());
        }
        let raw: Option<Option<String>> = sqlx::query_scalar("SELECT value FROM t_post_ticker_tape_train_page_value WHERE train_id=? AND page_number=? LIMIT 1").bind(id).bind(number).fetch_optional(&mut *tx).await?;
        let value = match raw.flatten() { Some(raw) => serde_json::from_str::<Vec<String>>(&raw)?, None => Vec::new() };
        let data = json!({"messageBody":pages,"value":value});
        tx.commit().await?;
        Ok(Some(data))
    }.await)
}

pub(crate) async fn delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<PostTickerPageQuery>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    respond(
        async {
            let id = required_id(query.train_id.as_deref())?;
            let mut tx = pool.begin().await?;
            owned(&mut tx, id, &user).await?;
            sqlx::query("DELETE FROM t_post_ticker_tape_train_page_value WHERE train_id=?")
                .bind(id)
                .execute(&mut *tx)
                .await?;
            sqlx::query("DELETE FROM t_post_ticker_tape_train_page WHERE train_id=?")
                .bind(id)
                .execute(&mut *tx)
                .await?;
            let deleted = sqlx::query("DELETE FROM t_post_ticker_tape_train WHERE id=?")
                .bind(id)
                .execute(&mut *tx)
                .await?
                .rows_affected()
                == 1;
            tx.commit().await?;
            Ok(Some(json!(deleted)))
        }
        .await,
    )
}

pub(crate) async fn setting_list(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    respond(async {
        let rows = sqlx::query("SELECT id,type,rate,text,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') create_time_text FROM t_post_ticker_tape_train_setting").fetch_all(pool).await?;
        Ok(Some(json!(rows.iter().map(|r| json!({"id":r.get::<String,_>("id"),"type":r.get::<Option<String>,_>("type"),"rate":r.get::<Option<i32>,_>("rate"),"text":r.get::<Option<String>,_>("text"),"createTime":r.get::<Option<String>,_>("create_time_text")})).collect::<Vec<_>>())))
    }.await)
}

pub(crate) async fn setting_add_or_update(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    respond(async {
        let values = body.get("paramList").and_then(Value::as_array).ok_or(Error::Business(202, "配置不能为空"))?;
        let mut tx = pool.begin().await?;
        sqlx::query("DELETE FROM t_post_ticker_tape_train_setting").execute(&mut *tx).await?;
        for value in values {
            sqlx::query("INSERT INTO t_post_ticker_tape_train_setting (id,type,rate,text,create_time) VALUES (?,?,?,?,COALESCE(?,NOW(6)))")
                .bind(value.get("id").and_then(Value::as_str).filter(|v| !v.is_empty()).map(str::to_owned).unwrap_or_else(|| uuid::Uuid::new_v4().to_string()))
                .bind(value.get("type").and_then(Value::as_str)).bind(value.get("rate").and_then(Value::as_i64)).bind(value.get("text").and_then(Value::as_str))
                .bind(value.get("createTime").and_then(Value::as_str)).execute(&mut *tx).await?;
        }
        tx.commit().await?;
        Ok(Some(body))
    }.await)
}

#[cfg(test)]
mod tests {
    use super::list_message_body;
    use serde_json::json;

    #[test]
    fn list_body_keeps_hutool_empty_and_failed_map_conversion_shapes() {
        for stored in ["", "   ", "[]", "[ ]", ",", "[,]"] {
            assert_eq!(
                list_message_body(Some(stored)),
                Some(json!([])),
                "{stored:?}"
            );
        }
        for stored in [
            "null",
            "not json",
            " [ ] ",
            "[null]",
            "[{}]",
            "[\"a\"]",
            "[{\"codeMessage\":\"1234\",\"sort\":1}]",
            "{}",
        ] {
            assert_eq!(list_message_body(Some(stored)), None, "{stored:?}");
        }
        assert_eq!(list_message_body(None), None);
    }
}
