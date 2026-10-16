use crate::platform::{
    auth::AuthUser,
    envelope::{envelope_json, envelope_response},
    state::AppState,
};
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rand::seq::SliceRandom;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, MySql, Row, Transaction};

fn row_json(row: &MySqlRow) -> Value {
    json!({
        "id":row.get::<String,_>("id"),"name":row.get::<Option<String>,_>("name"),
        "isCable":row.get::<Option<i32>,_>("is_cable"),"type":row.get::<Option<i32>,_>("type"),
        "createUser":row.get::<Option<String>,_>("create_user"),"status":row.get::<Option<i32>,_>("status"),
        "startTime":row.get::<Option<String>,_>("start_time_text"),"endTime":row.get::<Option<String>,_>("end_time_text"),
        "protocolVersion":row.get::<i32,_>("protocol_version"),"attempt":row.get::<i32,_>("attempt"),
        "serverElapsedMs":0,"countdownSeconds":row.get::<Option<i32>,_>("countdown_seconds"),"remainingMs":null,
        "deadline":row.get::<Option<String>,_>("deadline_text"),"pausedAt":row.get::<Option<String>,_>("paused_at_text"),
        "groupNumber":row.get::<Option<i32>,_>("group_number"),"pageNumber":null,
        "errorNumber":row.get::<Option<i32>,_>("error_number"),"accuracy":row.get::<Option<String>,_>("accuracy"),
        "speed":row.get::<Option<String>,_>("speed"),"speedLog":row.get::<Option<String>,_>("speed_log"),
        "validTime":row.get::<Option<i32>,_>("valid_time"),"validTimeLog":row.get::<Option<String>,_>("valid_time_log"),
        "createTime":row.get::<Option<String>,_>("create_time_text"),"content":row.get::<Option<String>,_>("content"),
        "score":row.get::<Option<String>,_>("score"),"change":row.get::<Option<i32>,_>("change"),
        "deductInfo":row.get::<Option<String>,_>("deduct_info"),"existPage":null,"codeAll":null,
        "totalSpeed":row.get::<Option<String>,_>("total_speed")
    })
}
const COLUMNS:&str="id,name,is_cable,type,create_user,status,group_number,error_number,accuracy,speed,valid_time,score,total_speed,attempt,protocol_version,content,`change`,deduct_info,speed_log,valid_time_log,countdown_seconds,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') start_time_text,DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') end_time_text,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') create_time_text,LEFT(DATE_FORMAT(deadline,'%Y-%m-%d %H:%i:%s.%f'),23) deadline_text,LEFT(DATE_FORMAT(paused_at,'%Y-%m-%d %H:%i:%s.%f'),23) paused_at_text";

#[derive(serde::Deserialize)]
pub(crate) struct PageQuery {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
    #[serde(rename = "pageNumber")]
    page_number: Option<i32>,
}

fn telex_page_row(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id":row.get::<String,_>("id"),"trainId":row.get::<Option<String>,_>("train_id"),"key":row.get::<Option<String>,_>("key"),"value":row.get::<Option<String>,_>("value"),"pageNumber":row.get::<Option<i32>,_>("page_number"),"sort":row.get::<Option<i32>,_>("sort")})
}
pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<TrainQuery>,
    Json(page): Json<PageBody>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let train_type = query.train_type;
    let page_no = page.page.unwrap_or(1).max(1);
    let rows = page.rows.unwrap_or(20).clamp(1, 200);
    let count = match sqlx::query(
        "SELECT COUNT(*) AS total FROM t_post_telex_pat_train WHERE create_user=? AND train_type=?",
    )
    .bind(&user.id)
    .bind(train_type)
    .fetch_one(pool)
    .await
    {
        Ok(r) => r.get::<i64, _>("total"),
        Err(error) => {
            tracing::warn!(%error, "post telex count query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let sql=format!("SELECT {COLUMNS} FROM t_post_telex_pat_train WHERE create_user=? AND train_type=? ORDER BY create_time DESC LIMIT ? OFFSET ?");
    match sqlx::query(&sql)
        .bind(&user.id)
        .bind(train_type)
        .bind(rows)
        .bind((page_no - 1) * rows)
        .fetch_all(pool)
        .await
    {
        Ok(items) => envelope_json(
            StatusCode::OK,
            200,
            Some(
                json!({"totalPage":if count==0{0}else{(count+rows-1)/rows},"totalNumber":count,"currentPage":page_no,"pageSize":rows,"data":items.iter().map(row_json).collect::<Vec<_>>() }),
            ),
            "ok",
            "",
        ),
        Err(error) => {
            tracing::warn!(%error, "post telex list query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}
#[derive(serde::Deserialize)]
pub(crate) struct TrainQuery {
    #[serde(rename = "trainType")]
    train_type: Option<i32>,
}
#[derive(serde::Deserialize)]
pub(crate) struct PageBody {
    page: Option<i64>,
    rows: Option<i64>,
}
pub(crate) async fn train_view(
    tx: &mut Transaction<'_, MySql>,
    id: &str,
    with_pages: bool,
) -> Result<Option<Value>, sqlx::Error> {
    let sql = format!("SELECT {COLUMNS},CAST(TIMESTAMPDIFF(MICROSECOND,start_time,NOW(3)) DIV 1000 AS SIGNED) elapsed_ms,CAST(TIMESTAMPDIFF(MICROSECOND,IFNULL(paused_at,NOW(3)),deadline) DIV 1000 AS SIGNED) remaining_ms FROM t_post_telex_pat_train WHERE id=? LIMIT 1");
    let Some(row) = sqlx::query(&sql).bind(id).fetch_optional(&mut **tx).await? else {
        return Ok(None);
    };
    let mut view = row_json(&row);
    view["serverElapsedMs"] = json!(row.get::<Option<i64>, _>("elapsed_ms").unwrap_or(0).max(0));
    view["remainingMs"] = if row.get::<Option<i32>, _>("status") == Some(3)
        && row.get::<Option<i32>, _>("countdown_seconds").is_some()
    {
        json!(0)
    } else {
        json!(row.get::<Option<i64>, _>("remaining_ms").map(|v| v.max(0)))
    };
    if with_pages {
        let pages = sqlx::query("SELECT id,train_id,`key`,value,page_number,sort FROM t_post_telex_pat_train_page WHERE train_id=? AND page_number<3 ORDER BY page_number,sort").bind(id).fetch_all(&mut **tx).await?;
        let values = sqlx::query("SELECT train_id,page_number,pat_value,attempt,capture_intervals FROM t_post_telex_pat_train_page_value WHERE train_id=? AND page_number IN (1,2) ORDER BY page_number").bind(id).fetch_all(&mut **tx).await?;
        view["existPage"] = json!(pages.iter().map(telex_page_row).collect::<Vec<_>>());
        view["codeAll"] = json!(values.iter().map(|v| -> Result<Value,sqlx::Error> {
            let intervals = v.get::<Option<String>,_>("capture_intervals")
                .map(|raw| serde_json::from_str::<Value>(&raw).map_err(|e| sqlx::Error::Decode(Box::new(e))))
                .transpose()?;
            Ok(json!({"trainId":v.get::<Option<String>,_>("train_id"),"pageNumber":v.get::<Option<i32>,_>("page_number"),"patValue":v.get::<Option<String>,_>("pat_value"),"attempt":v.get::<Option<i32>,_>("attempt"),"captureIntervals":intervals}))
        }).collect::<Result<Vec<_>,_>>()?);
        view["pageNumber"] = json!(
            sqlx::query_scalar::<_, Option<i32>>(
                "SELECT MAX(page_number) FROM t_post_telex_pat_train_page WHERE train_id=?"
            )
            .bind(id)
            .fetch_one(&mut **tx)
            .await?
        );
    }
    Ok(Some(view))
}

pub(crate) async fn detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|v| !v.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return page_response(Err(e.into())),
    };
    let owner: Result<Option<Option<String>>, sqlx::Error> =
        sqlx::query_scalar("SELECT create_user FROM t_post_telex_pat_train WHERE id=? FOR UPDATE")
            .bind(id)
            .fetch_optional(&mut *tx)
            .await;
    match owner {
        Ok(None) => envelope_response(StatusCode::OK, 202, "未查询到训练信息", ""),
        Ok(Some(owner)) if owner.as_deref() != Some(user.id.as_str()) => {
            envelope_response(StatusCode::OK, 207, "无权限", "")
        }
        Ok(Some(_)) => match train_view(&mut tx, id, true).await {
            Ok(Some(data)) if data["protocolVersion"] == 1 || data["status"] == 3 => {
                match tx.commit().await {
                    Ok(()) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
                    Err(e) => page_response(Err(e.into())),
                }
            }
            Ok(Some(_)) => envelope_response(
                StatusCode::OK,
                202,
                "旧训练缺少原始采集时序，请终止旧训练并重新创建训练",
                "",
            ),
            Ok(None) => envelope_response(StatusCode::OK, 202, "未查询到训练信息", ""),
            Err(e) => page_response(Err(e.into())),
        },
        Err(e) => page_response(Err(e.into())),
    }
}

#[derive(Debug)]
pub(crate) enum PageError {
    Business(i32, &'static str),
    Database(sqlx::Error),
    Json(serde_json::Error),
}
impl From<sqlx::Error> for PageError {
    fn from(error: sqlx::Error) -> Self {
        Self::Database(error)
    }
}
impl From<serde_json::Error> for PageError {
    fn from(error: serde_json::Error) -> Self {
        Self::Json(error)
    }
}
fn page_response(result: Result<Value, PageError>) -> Response {
    match result {
        Ok(value) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Err(PageError::Business(code, message)) => {
            let description = if code == 207 { "" } else { message };
            envelope_response(StatusCode::OK, code, message, description)
        }
        Err(error) => {
            match error {
                PageError::Database(error) => {
                    tracing::warn!(%error, "post telex page database failure")
                }
                PageError::Json(error) => {
                    tracing::warn!(%error, "post telex stored capture JSON invalid")
                }
                PageError::Business(_, _) => unreachable!(),
            }
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}
#[derive(Clone, Debug, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
struct Interval {
    started_ms: i64,
    ended_ms: i64,
}
fn intervals(raw: Option<String>) -> Result<Vec<Interval>, PageError> {
    let raw = raw.ok_or(PageError::Business(202, "原始采集时间轴缺失，不能计算成绩"))?;
    Ok(serde_json::from_str(&raw)?)
}
fn duration(intervals: &[Interval], bound: i64) -> Result<i64, PageError> {
    if bound < 0 {
        return Err(PageError::Business(202, "训练尚未开始"));
    }
    let mut end = 0;
    let mut total = 0i64;
    for interval in intervals {
        if interval.started_ms < end
            || interval.ended_ms <= interval.started_ms
            || interval.ended_ms > bound
        {
            return Err(PageError::Business(
                202,
                "采集区间必须有序、非负且位于训练有效时间内",
            ));
        }
        total = total
            .checked_add(interval.ended_ms - interval.started_ms)
            .ok_or(PageError::Business(202, "采集时长溢出"))?;
        end = interval.ended_ms;
    }
    Ok(total)
}
fn capture_bound(train: &MySqlRow, received: i64) -> Result<i64, PageError> {
    let start = train
        .get::<Option<i64>, _>("start_ms")
        .ok_or(PageError::Business(202, "训练开始时刻缺失"))?;
    let mut end = received;
    if let Some(deadline) = train.get::<Option<i64>, _>("deadline_ms") {
        end = end.min(deadline);
    }
    if let Some(paused) = train.get::<Option<i64>, _>("paused_ms") {
        end = end.min(paused);
    }
    Ok(end - start)
}
fn rate(characters: i64, millis: i64) -> String {
    if characters <= 0 || millis <= 0 {
        return "0".to_owned();
    }
    ((i128::from(characters) * 60_000 + i128::from(millis) / 2) / i128::from(millis)).to_string()
}

async fn owned_page_train(
    tx: &mut Transaction<'_, MySql>,
    id: &str,
    user: &AuthUser,
) -> Result<MySqlRow, PageError> {
    let row = sqlx::query("SELECT *,CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',start_time) DIV 1000 AS SIGNED) start_ms,CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',deadline) DIV 1000 AS SIGNED) deadline_ms,CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',paused_at) DIV 1000 AS SIGNED) paused_ms FROM t_post_telex_pat_train WHERE id=? FOR UPDATE").bind(id).fetch_optional(&mut **tx).await?.ok_or(PageError::Business(202, "未查询到训练信息"))?;
    if row.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        return Err(PageError::Business(207, "无权限"));
    }
    if row.get::<Option<i32>, _>("protocol_version") != Some(1)
        && row.get::<Option<i32>, _>("status") != Some(3)
    {
        return Err(PageError::Business(
            202,
            "旧训练缺少原始采集时序，请终止旧训练并重新创建训练",
        ));
    }
    Ok(row)
}
async fn maximum_page(tx: &mut Transaction<'_, MySql>, train: &MySqlRow) -> Result<i32, PageError> {
    if train.get::<Option<i32>, _>("is_cable") == Some(1) {
        let maximum: Option<i32> = sqlx::query_scalar(
            "SELECT MAX(page_number) FROM t_post_telex_pat_train_page WHERE train_id=?",
        )
        .bind(train.get::<String, _>("id"))
        .fetch_one(&mut **tx)
        .await?;
        Ok(maximum.unwrap_or(0))
    } else {
        let count = train
            .get::<Option<i32>, _>("group_number")
            .ok_or(PageError::Business(202, "训练组数缺失，无法取页"))?;
        Ok(((i64::from(count) + 99) / 100) as i32)
    }
}
fn require_page(number: i32, maximum: i32) -> Result<(), PageError> {
    if number < 1 || number > maximum {
        return Err(PageError::Business(202, "页码不正确"));
    }
    Ok(())
}

async fn receipt(
    tx: &mut Transaction<'_, MySql>,
    train: &MySqlRow,
    number: i32,
    generate: bool,
) -> Result<Value, PageError> {
    let id = train.get::<String, _>("id");
    let maximum = maximum_page(tx, train).await?;
    require_page(number, maximum)?;
    let sql = "SELECT id,train_id,`key`,value,page_number,sort FROM t_post_telex_pat_train_page WHERE train_id=? AND page_number=? ORDER BY sort";
    let mut pages = sqlx::query(sql)
        .bind(&id)
        .bind(number)
        .fetch_all(&mut **tx)
        .await?;
    if generate && pages.is_empty() && train.get::<Option<i32>, _>("status") != Some(3) {
        let count = if train.get::<Option<i32>, _>("is_cable") == Some(0) && number == maximum {
            train
                .get::<Option<i32>, _>("group_number")
                .ok_or(PageError::Business(202, "训练组数缺失"))?
                - (number - 1) * 100
        } else {
            100
        };
        let kind = train
            .get::<Option<i32>, _>("type")
            .ok_or(PageError::Business(202, "报文类型缺失，无法生成报文"))?;
        let groups = telex_groups(
            kind,
            train.get::<Option<i32>, _>("pat_type"),
            count as usize,
        )?;
        for (sort, key) in groups.into_iter().enumerate() {
            sqlx::query("INSERT INTO t_post_telex_pat_train_page (id,train_id,`key`,page_number,sort) VALUES (?,?,?,?,?)")
                .bind(uuid::Uuid::new_v4().to_string()).bind(&id).bind(key).bind(number).bind(sort as i32).execute(&mut **tx).await?;
        }
        pages = sqlx::query(sql)
            .bind(&id)
            .bind(number)
            .fetch_all(&mut **tx)
            .await?;
    }
    let submitted = sqlx::query("SELECT pat_value,capture_intervals,LEFT(DATE_FORMAT(received_at,'%Y-%m-%d %H:%i:%s.%f'),23) received_at_text FROM t_post_telex_pat_train_page_value WHERE train_id=? AND page_number=? LIMIT 1").bind(&id).bind(number).fetch_optional(&mut **tx).await?;
    let protocol = train.get::<Option<i32>, _>("protocol_version").unwrap_or(0);
    let capture = if protocol == 0 {
        Value::Null
    } else if let Some(row) = submitted.as_ref() {
        json!(intervals(row.get("capture_intervals"))?)
    } else {
        json!([])
    };
    let times: Option<Vec<Option<i32>>> = train
        .get::<Option<String>, _>("valid_time_log")
        .map(|raw| serde_json::from_str(&raw))
        .transpose()?
        .flatten();
    let speeds: Option<Vec<Option<String>>> = train
        .get::<Option<String>, _>("speed_log")
        .map(|raw| serde_json::from_str(&raw))
        .transpose()?
        .flatten();
    Ok(json!({
        "submitted":submitted.is_some(),"codeAll":submitted.as_ref().map(|r| r.get::<Option<String>,_>("pat_value")).unwrap_or(Some(String::new())),
        "attempt":train.get::<Option<i32>,_>("attempt"),"captureIntervals":capture,
        "receivedAt":submitted.as_ref().and_then(|r| r.get::<Option<String>,_>("received_at_text")),
        "validTime":if submitted.is_some() { times.as_ref().and_then(|v| v.get(number as usize - 1)) } else { None },
        "speed":if submitted.is_some() { speeds.as_ref().and_then(|v| v.get(number as usize - 1)) } else { None },
        "pageVo":pages.iter().map(telex_page_row).collect::<Vec<_>>()
    }))
}

pub(crate) async fn get_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<PageQuery>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    page_response(
        async {
            let id = query
                .train_id
                .as_deref()
                .filter(|v| !v.is_empty())
                .ok_or(PageError::Business(202, "请求参数错误"))?;
            let number = query
                .page_number
                .ok_or(PageError::Business(202, "页码不正确"))?;
            let mut tx = pool.begin().await?;
            let train = owned_page_train(&mut tx, id, &user).await?;
            let data = receipt(&mut tx, &train, number, true).await?;
            tx.commit().await?;
            Ok(data)
        }
        .await,
    )
}

pub(crate) async fn finish_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    page_response(async {
        let id = body.get("trainId").and_then(Value::as_str).filter(|v| !v.is_empty()).ok_or(PageError::Business(202, "请求参数错误"))?;
        let number = body.get("pageNumber").and_then(Value::as_i64).and_then(|v| i32::try_from(v).ok()).ok_or(PageError::Business(202, "页码不正确"))?;
        let attempt = body.get("attempt").and_then(Value::as_i64).and_then(|v| i32::try_from(v).ok());
        let text = body.get("patValue").and_then(Value::as_str);
        let capture_raw = body.get("captureIntervals").cloned().unwrap_or(Value::Null);
        let mut tx = pool.begin().await?;
        let clock = sqlx::query("SELECT CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',NOW(3)) DIV 1000 AS SIGNED) millis,DATE_FORMAT(NOW(3),'%Y-%m-%d %H:%i:%s.%f') received_text").fetch_one(&mut *tx).await?;
        let received = clock.get::<i64,_>("millis");
        let received_text = clock.get::<String,_>("received_text");
        let train = owned_page_train(&mut tx, id, &user).await?;
        let id = train.get::<String, _>("id");
        if attempt.is_none() || attempt != train.get::<Option<i32>,_>("attempt") { return Err(PageError::Business(208, "训练轮次已改变，请重新读取训练，旧轮次不能提交")); }
        if train.get::<Option<i32>,_>("protocol_version") != Some(1) { return Err(PageError::Business(202, "历史训练不支持逐页采集写入")); }
        let maximum = maximum_page(&mut tx, &train).await?;
        require_page(number, maximum)?;
        let capture: Option<Vec<Interval>> = text
            .map(|_| serde_json::from_value(capture_raw.clone()).ok())
            .flatten();
        let saved = sqlx::query("SELECT id,pat_value,capture_intervals,attempt FROM t_post_telex_pat_train_page_value WHERE train_id=? AND page_number=? LIMIT 1").bind(&id).bind(number).fetch_optional(&mut *tx).await?;
        let previous = if let Some(row) = saved.as_ref() {
            if row.get::<Option<i32>,_>("attempt") != attempt { return Err(PageError::Business(208, "训练轮次已改变")); }
            let previous = intervals(row.get("capture_intervals"))?;
            if let (Some(text), Some(capture)) = (text, capture.as_ref()) {
                if row.get::<Option<String>,_>("pat_value").as_deref() == Some(text) && previous == *capture {
                    let data = receipt(&mut tx, &train, number, false).await?;
                    tx.commit().await?;
                    return Ok(data);
                }
            }
            Some(previous)
        } else { None };
        let status = train.get::<Option<i32>,_>("status");
        if status == Some(3) { return Err(PageError::Business(208, "训练已完成，不能修改")); }
        if !matches!(status, Some(1 | 2)) { return Err(PageError::Business(202, "训练不在进行中，不能提交页面")); }
        if let Some(deadline) = train.get::<Option<i64>,_>("deadline_ms") {
            if train.get::<Option<i64>,_>("paused_ms").is_none() && received >= deadline + 60_000 { return Err(PageError::Business(208, "补交窗口已结束，不能修改页面")); }
        }
        let text = text.ok_or(PageError::Business(202, "页面内容不能为空"))?;
        let capture = capture.ok_or(PageError::Business(202, "采集时序格式错误"))?;
        let millis = duration(&capture, capture_bound(&train, received)?)?;
        if super::telex_capture::character_count(text, train.get::<Option<i32>, _>("train_type").map(i64::from)) as i64 > 0 && millis == 0 { return Err(PageError::Business(202, "非空正文必须有有效采集时长")); }
        if let Some(previous) = previous {
            if capture.len() <= previous.len() || !capture.starts_with(&previous) { return Err(PageError::Business(202, "不能覆盖已确认的原始采集区间")); }
        }
        if let Some(saved) = saved {
            sqlx::query("UPDATE t_post_telex_pat_train_page_value SET pat_value=?,capture_intervals=?,attempt=?,received_at=? WHERE id=?")
                .bind(text).bind(serde_json::to_string(&capture)?).bind(attempt).bind(&received_text).bind(saved.get::<String,_>("id")).execute(&mut *tx).await?;
        } else {
            sqlx::query("INSERT INTO t_post_telex_pat_train_page_value (id,train_id,page_number,pat_value,capture_intervals,attempt,received_at) VALUES (?,?,?,?,?,?,?)")
                .bind(uuid::Uuid::new_v4().to_string()).bind(&id).bind(number).bind(text).bind(serde_json::to_string(&capture)?).bind(attempt).bind(&received_text).execute(&mut *tx).await?;
        }
        derive_capture(&mut tx, &train, received, maximum).await?;
        let updated = owned_page_train(&mut tx, &id, &user).await?;
        let data = receipt(&mut tx, &updated, number, true).await?;
        tx.commit().await?;
        Ok(data)
    }.await)
}

async fn derive_capture(
    tx: &mut Transaction<'_, MySql>,
    train: &MySqlRow,
    now: i64,
    maximum: i32,
) -> Result<(), PageError> {
    let id = train.get::<String, _>("id");
    let start = train
        .get::<Option<i64>, _>("start_ms")
        .ok_or(PageError::Business(202, "训练开始时刻缺失"))?;
    let mut all_intervals = intervals(train.get("pause_intervals"))?;
    duration(&all_intervals, now - start)?;
    let pages = sqlx::query("SELECT page_number,attempt,capture_intervals,CAST(TIMESTAMPDIFF(MICROSECOND,'1970-01-01',received_at) DIV 1000 AS SIGNED) received_ms,pat_value FROM t_post_telex_pat_train_page_value WHERE train_id=? ORDER BY page_number").bind(&id).fetch_all(&mut **tx).await?;
    let mut times: Vec<Option<i32>> = Vec::new();
    let mut speeds: Vec<Option<String>> = Vec::new();
    let mut total_millis = 0i64;
    let mut total_characters = 0i64;
    for page in pages {
        if page.get::<Option<i32>, _>("attempt") != train.get::<Option<i32>, _>("attempt") {
            return Err(PageError::Business(208, "训练轮次已改变"));
        }
        let number = page
            .get::<Option<i32>, _>("page_number")
            .ok_or(PageError::Business(202, "页码不正确"))?;
        require_page(number, maximum)?;
        let captured = intervals(page.get("capture_intervals"))?;
        let received = page
            .get::<Option<i64>, _>("received_ms")
            .ok_or(PageError::Business(202, "采集接收时刻缺失"))?;
        let millis = duration(&captured, capture_bound(train, received)?)?;
        let text = page
            .get::<Option<String>, _>("pat_value")
            .ok_or(PageError::Business(202, "已保存正文缺失"))?;
        let count = super::telex_capture::character_count(
            &text,
            train.get::<Option<i32>, _>("train_type").map(i64::from),
        ) as i64;
        if count > 0 && millis == 0 {
            return Err(PageError::Business(202, "非空正文必须有有效采集时长"));
        }
        total_millis = total_millis
            .checked_add(millis)
            .ok_or(PageError::Business(202, "采集时长溢出"))?;
        total_characters = total_characters
            .checked_add(count)
            .ok_or(PageError::Business(202, "正文长度溢出"))?;
        all_intervals.extend(captured);
        times.resize(number as usize, None);
        speeds.resize(number as usize, None);
        times[number as usize - 1] = Some(
            i32::try_from(millis / 1000).map_err(|_| PageError::Business(202, "采集时长溢出"))?,
        );
        speeds[number as usize - 1] = Some(rate(count, millis));
    }
    all_intervals.sort_by_key(|v| v.started_ms);
    duration(&all_intervals, i64::MAX)?;
    let speed = rate(total_characters, total_millis);
    let seconds =
        i32::try_from(total_millis / 1000).map_err(|_| PageError::Business(202, "采集时长溢出"))?;
    sqlx::query("UPDATE t_post_telex_pat_train SET speed=?,total_speed=?,valid_time=?,speed_log=?,valid_time_log=? WHERE id=?")
        .bind(&speed).bind(&speed).bind(seconds).bind(serde_json::to_string(&speeds)?).bind(serde_json::to_string(&times)?).bind(id).execute(&mut **tx).await?;
    Ok(())
}

pub(crate) fn telex_groups(
    kind: i32,
    pat_type: Option<i32>,
    count: usize,
) -> Result<Vec<String>, PageError> {
    let alphabet: &[u8] = match kind {
        0 => b"0123456789",
        1 => b"ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        2 => b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        _ => return Err(PageError::Business(202, "未知数据类型")),
    };
    let mut rng = rand::thread_rng();
    let mut result = Vec::with_capacity(count);
    if kind == 0 && matches!(pat_type, Some(0 | 1)) {
        let mut left = Vec::with_capacity(count * 2 + 5);
        let mut right = Vec::with_capacity(count * 2 + 5);
        if pat_type == Some(0) {
            while left.len() < count * 2 {
                let mut min = *b"12345";
                let mut max = *b"67890";
                min.shuffle(&mut rng);
                max.shuffle(&mut rng);
                left.extend(min);
                right.extend(max);
            }
        } else {
            for start in (0..count).step_by(10) {
                let n = (count - start).min(10) * 2;
                let mut min: Vec<u8> = (0..n).map(|i| b"12345"[(start * 2 + i) % 5]).collect();
                let mut max: Vec<u8> = (0..n).map(|i| b"67890"[(start * 2 + i) % 5]).collect();
                min.shuffle(&mut rng);
                max.shuffle(&mut rng);
                for i in 0..n.saturating_sub(1) {
                    if min[i] == min[i + 1] {
                        if let Some(next) = (i + 2..n).find(|j| min[*j] != min[i]) {
                            min.swap(i + 1, next);
                        }
                    }
                    if max[i] == max[i + 1] {
                        if let Some(next) = (i + 2..n).find(|j| max[*j] != max[i]) {
                            max.swap(i + 1, next);
                        }
                    }
                }
                left.extend(min);
                right.extend(max);
            }
        }
        for i in 0..count {
            let mut group = [left[i * 2], left[i * 2 + 1], right[i * 2], right[i * 2 + 1]];
            if pat_type == Some(0) {
                if group[0] == group[1] {
                    group[1] = if group[1] == b'5' { b'4' } else { group[1] + 1 };
                }
                if group[2] == group[3] {
                    group[3] = if group[3] == b'0' {
                        b'9'
                    } else if group[3] == b'9' {
                        b'0'
                    } else {
                        group[3] + 1
                    };
                }
            }
            if pat_type == Some(1) {
                group.swap(1, 2);
                if rand::random::<bool>() {
                    group.swap(0, 1);
                    group.swap(2, 3);
                }
                let original = group;
                if result
                    .iter()
                    .any(|previous: &String| previous.as_bytes() == group.as_slice())
                {
                    let permutations = [
                        [original[3], original[2], original[1], original[0]],
                        [original[2], original[3], original[0], original[1]],
                        [original[2], original[1], original[0], original[3]],
                        [original[0], original[3], original[2], original[1]],
                    ];
                    if let Some(candidate) = permutations.into_iter().find(|candidate| {
                        !result
                            .iter()
                            .any(|previous| previous.as_bytes() == candidate)
                    }) {
                        group = candidate;
                    }
                }
            } else if rand::random::<bool>() {
                group.rotate_left(2);
            }
            result.push(group.into_iter().map(char::from).collect());
        }
    } else {
        for _ in 0..count {
            let mut key: String = alphabet
                .choose_multiple(&mut rng, 4)
                .map(|v| char::from(*v))
                .collect();
            for _ in 0..20 {
                if !result
                    .iter()
                    .rev()
                    .take(10)
                    .any(|previous| previous == &key)
                {
                    break;
                }
                key = alphabet
                    .choose_multiple(&mut rng, 4)
                    .map(|v| char::from(*v))
                    .collect();
            }
            result.push(key);
        }
    }
    Ok(result)
}

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::platform::auth::authenticate;
    use axum::{
        middleware,
        routing::{get, post},
        Router,
    };

    let mut router = Router::new();
    let routes = Router::new()
        .route("/findAll", post(list))
        .route("/detail", post(detail))
        .route("/getPage", get(get_page))
        .route("/finishPage", post(finish_page))
        .route("/save", post(crate::post_telex_write::add))
        .route("/begin", post(crate::post_telex_write::begin))
        .route("/pause", post(crate::post_telex_write::pause))
        .route("/resume", post(crate::post_telex_write::resume))
        .route("/reset", post(crate::post_telex_write::reset))
        .route("/finish", post(crate::post_telex_write::finish))
        .route("/delete", get(crate::post_telex_write::delete))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router.nest("/api/postTelexPatTrain", routes);
    router
}
