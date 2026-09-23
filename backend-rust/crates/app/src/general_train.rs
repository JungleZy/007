use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::Row;

#[derive(Clone, Copy)]
enum Domain {
    Key,
    Telex,
    Ticker,
}
#[derive(Debug, Deserialize)]
pub(crate) struct QueryId {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
    #[serde(rename = "attempt")]
    _attempt: Option<i32>,
}
fn db_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error=%error,operation,"general training database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}
fn table(domain: Domain) -> &'static str {
    match domain {
        Domain::Key => "general_key_pat",
        Domain::Telex => "general_telex_pat",
        Domain::Ticker => "general_ticker_pat",
    }
}
fn id_is_int(domain: Domain) -> bool {
    !matches!(domain, Domain::Telex)
}
async fn add(state: AppState, user: AuthUser, body: Value, domain: Domain) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let title = body
        .get("title")
        .or_else(|| body.get("name"))
        .and_then(Value::as_str);
    let is_cable = body.get("isCable").and_then(Value::as_i64).unwrap_or(0);
    let train_type = body.get("trainType").and_then(Value::as_i64).unwrap_or(0);
    let protocol = 1;
    if id_is_int(domain) {
        let sql=format!("INSERT INTO {} ({},is_cable,create_time,create_user,status,train_type,protocol_version) VALUES (?,?,NOW(),?,0,?,?)",table(domain),if matches!(domain,Domain::Ticker){"name"}else{"title"});
        match sqlx::query(&sql)
            .bind(title)
            .bind(is_cable)
            .bind(&user.id)
            .bind(train_type)
            .bind(protocol)
            .execute(pool)
            .await
        {
            Ok(r) => envelope_json(
                StatusCode::OK,
                200,
                Some(json!({"id":r.last_insert_id(),"status":0,"protocolVersion":1})),
                "ok",
                "",
            ),
            Err(e) => db_error(e, "add general train"),
        }
    } else {
        let id = uuid::Uuid::new_v4().to_string();
        let sql="INSERT INTO general_telex_pat (id,title,is_cable,create_time,create_user,status,train_type,protocol_version) VALUES (?,?,?,NOW(),?,0,?,1)";
        match sqlx::query(sql)
            .bind(&id)
            .bind(title)
            .bind(is_cable)
            .bind(&user.id)
            .bind(train_type)
            .execute(pool)
            .await
        {
            Ok(_) => envelope_json(
                StatusCode::OK,
                200,
                Some(json!({"id":id,"status":0,"protocolVersion":1})),
                "ok",
                "",
            ),
            Err(e) => db_error(e, "add general telex"),
        }
    }
}
async fn list(state: AppState, user: AuthUser, body: Value, domain: Domain) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let page = body.get("page").and_then(Value::as_i64).unwrap_or(1).max(1);
    let rows = body
        .get("rows")
        .and_then(Value::as_i64)
        .unwrap_or(20)
        .clamp(1, 200);
    let t = table(domain);
    let count_sql = format!("SELECT COUNT(*) total FROM {t} WHERE create_user=?");
    let total = match sqlx::query(&count_sql).bind(&user.id).fetch_one(pool).await {
        Ok(r) => r.get::<i64, _>("total"),
        Err(e) => return db_error(e, "count general train"),
    };
    let name = if matches!(domain, Domain::Ticker) {
        "name"
    } else {
        "title"
    };
    let sql=format!("SELECT CAST(id AS CHAR) id,{name} title,status,create_user,protocol_version FROM {t} WHERE create_user=? ORDER BY create_time DESC LIMIT ? OFFSET ?");
    match sqlx::query(&sql)
        .bind(&user.id)
        .bind(rows)
        .bind((page - 1) * rows)
        .fetch_all(pool)
        .await
    {
        Ok(data) => envelope_json(
            StatusCode::OK,
            200,
            Some(
                json!({"totalPage":if total==0{0}else{(total+rows-1)/rows},"totalNumber":total,"currentPage":page,"pageSize":rows,"data":data.iter().map(|r|json!({"id":r.get::<String,_>("id"),"title":r.get::<Option<String>,_>("title"),"status":r.get::<Option<i32>,_>("status"),"protocolVersion":r.get::<Option<i32>,_>("protocol_version")})).collect::<Vec<_>>() }),
            ),
            "ok",
            "",
        ),
        Err(e) => db_error(e, "list general train"),
    }
}
async fn detail(state: AppState, user: AuthUser, body: Value, domain: Domain) -> Response {
    let id = body
        .get("trainId")
        .or_else(|| body.get("id"))
        .and_then(|v| {
            if v.is_string() {
                v.as_str().map(str::to_owned)
            } else {
                Some(v.to_string())
            }
        });
    let Some(id) = id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let t = table(domain);
    let name = if matches!(domain, Domain::Ticker) {
        "name"
    } else {
        "title"
    };
    let sql=format!("SELECT CAST(id AS CHAR) id,{name} title,status,create_user,protocol_version FROM {t} WHERE id=? LIMIT 1");
    match sqlx::query(&sql).bind(id).fetch_optional(pool).await {
        Ok(Some(r))
            if r.get::<Option<String>, _>("create_user").as_deref() == Some(user.id.as_str()) =>
        {
            envelope_json(
                StatusCode::OK,
                200,
                Some(
                    json!({"id":r.get::<String,_>("id"),"title":r.get::<Option<String>,_>("title"),"status":r.get::<Option<i32>,_>("status"),"protocolVersion":r.get::<Option<i32>,_>("protocol_version")}),
                ),
                "ok",
                "",
            )
        }
        Ok(Some(_)) => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Ok(None) => envelope_response(StatusCode::OK, 202, "未查询到训练", ""),
        Err(e) => db_error(e, "detail general train"),
    }
}
async fn set_status(
    state: AppState,
    user: AuthUser,
    id: String,
    status: i32,
    domain: Domain,
) -> Response {
    if !(0..=3).contains(&status) {
        return envelope_response(StatusCode::OK, 202, "训练状态无效", "");
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql=format!("UPDATE {} SET status=?,start_time=CASE WHEN ?=1 THEN COALESCE(start_time,NOW(6)) ELSE start_time END,end_time=CASE WHEN ?=3 THEN NOW(6) ELSE end_time END WHERE id=? AND create_user=? AND status<>3",table(domain));
    match sqlx::query(&sql)
        .bind(status)
        .bind(status)
        .bind(status)
        .bind(id)
        .bind(&user.id)
        .execute(pool)
        .await
    {
        Ok(r) if r.rows_affected() == 1 => envelope_response(StatusCode::OK, 200, "ok", ""),
        Ok(_) => envelope_response(StatusCode::OK, 208, "训练状态不可执行", ""),
        Err(e) => db_error(e, "update general status"),
    }
}
async fn delete(state: AppState, user: AuthUser, id: String, domain: Domain) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = format!("DELETE FROM {} WHERE id=? AND create_user=?", table(domain));
    match sqlx::query(&sql)
        .bind(id)
        .bind(&user.id)
        .execute(pool)
        .await
    {
        Ok(_) => envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", ""),
        Err(e) => db_error(e, "delete general train"),
    }
}
async fn statistic(state: AppState, user: AuthUser, body: Value, domain: Domain) -> Response {
    let id = body
        .get("trainId")
        .or_else(|| body.get("id"))
        .map(Value::to_string)
        .unwrap_or_else(|| "0".into())
        .trim_matches('"')
        .to_owned();
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let user_table = match domain {
        Domain::Key => "general_key_pat_user",
        Domain::Telex => "general_telex_pat_user",
        Domain::Ticker => "general_ticker_pat_train_user",
    };
    let sql=format!("SELECT COUNT(*) total,COALESCE(SUM(CASE WHEN status=3 THEN 1 ELSE 0 END),0) finished FROM {user_table} WHERE train_id=?");
    match sqlx::query(&sql).bind(id).fetch_one(pool).await {
        Ok(r) => envelope_json(
            StatusCode::OK,
            200,
            Some(
                json!({"totalCount":r.get::<i64,_>("total"),"finishedCount":r.get::<i64,_>("finished"),"owner":user.id}),
            ),
            "ok",
            "",
        ),
        Err(e) => db_error(e, "general statistics"),
    }
}

pub(crate) async fn key_add(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    add(s, u, b, Domain::Key).await
}
pub(crate) async fn key_list(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    list(s, u, b, Domain::Key).await
}
pub(crate) async fn key_detail(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    detail(s, u, b, Domain::Key).await
}
pub(crate) async fn key_update(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    let id = b
        .get("trainId")
        .map(Value::to_string)
        .unwrap_or_default()
        .trim_matches('"')
        .to_owned();
    let status = b.get("status").and_then(Value::as_i64).unwrap_or(-1) as i32;
    set_status(s, u, id, status, Domain::Key).await
}
pub(crate) async fn key_finish(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    let id = b
        .get("trainId")
        .map(Value::to_string)
        .unwrap_or_default()
        .trim_matches('"')
        .to_owned();
    set_status(s, u, id, 3, Domain::Key).await
}
pub(crate) async fn key_reset(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    let id = b
        .get("trainId")
        .map(Value::to_string)
        .unwrap_or_default()
        .trim_matches('"')
        .to_owned();
    set_status(s, u, id, 0, Domain::Key).await
}
pub(crate) async fn key_start(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    set_status(s, u, q.train_id.unwrap_or_default(), 1, Domain::Key).await
}
pub(crate) async fn key_delete(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    delete(s, u, q.train_id.unwrap_or_default(), Domain::Key).await
}
pub(crate) async fn key_statistics(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    statistic(s, u, b, Domain::Key).await
}

pub(crate) async fn telex_add(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    add(s, u, b, Domain::Telex).await
}
pub(crate) async fn telex_list(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    list(s, u, b, Domain::Telex).await
}
pub(crate) async fn telex_detail(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    detail(s, u, b, Domain::Telex).await
}
pub(crate) async fn telex_update(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    let id = b
        .get("trainId")
        .and_then(Value::as_str)
        .unwrap_or_default()
        .to_owned();
    let status = b.get("status").and_then(Value::as_i64).unwrap_or(-1) as i32;
    set_status(s, u, id, status, Domain::Telex).await
}
pub(crate) async fn telex_finish(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    let id = b
        .get("trainId")
        .and_then(Value::as_str)
        .unwrap_or_default()
        .to_owned();
    set_status(s, u, id, 3, Domain::Telex).await
}
pub(crate) async fn telex_start(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    set_status(s, u, q.train_id.unwrap_or_default(), 1, Domain::Telex).await
}
pub(crate) async fn telex_delete(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    delete(s, u, q.train_id.unwrap_or_default(), Domain::Telex).await
}
pub(crate) async fn telex_statistics(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    statistic(s, u, b, Domain::Telex).await
}

pub(crate) async fn ticker_add(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    add(s, u, b, Domain::Ticker).await
}
pub(crate) async fn ticker_list(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    list(s, u, b, Domain::Ticker).await
}
pub(crate) async fn ticker_detail(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    detail(s, u, b, Domain::Ticker).await
}
pub(crate) async fn ticker_finish(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    let id = b
        .get("trainId")
        .map(Value::to_string)
        .unwrap_or_default()
        .trim_matches('"')
        .to_owned();
    set_status(s, u, id, 3, Domain::Ticker).await
}
pub(crate) async fn ticker_reset(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    let id = b
        .get("trainId")
        .map(Value::to_string)
        .unwrap_or_default()
        .trim_matches('"')
        .to_owned();
    set_status(s, u, id, 0, Domain::Ticker).await
}
pub(crate) async fn ticker_start(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    set_status(s, u, q.train_id.unwrap_or_default(), 1, Domain::Ticker).await
}
pub(crate) async fn ticker_delete(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    delete(s, u, q.train_id.unwrap_or_default(), Domain::Ticker).await
}
pub(crate) async fn ticker_statistics(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    statistic(s, u, b, Domain::Ticker).await
}
async fn members(state: AppState, user: AuthUser, body: Value, domain: Domain) -> Response {
    let id = body
        .get("trainId")
        .or_else(|| body.get("id"))
        .map(Value::to_string)
        .unwrap_or_default()
        .trim_matches('"')
        .to_owned();
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let user_table = match domain {
        Domain::Key => "general_key_pat_user",
        Domain::Telex => "general_telex_pat_user",
        Domain::Ticker => "general_ticker_pat_train_user",
    };
    let main = table(domain);
    let allowed_sql=format!("SELECT id FROM {main} t WHERE CAST(t.id AS CHAR)=? AND (t.create_user=? OR EXISTS(SELECT 1 FROM {user_table} u WHERE CAST(u.train_id AS CHAR)=CAST(t.id AS CHAR) AND u.user_id=?))");
    if !matches!(
        sqlx::query(&allowed_sql)
            .bind(&id)
            .bind(&user.id)
            .bind(&user.id)
            .fetch_optional(pool)
            .await,
        Ok(Some(_))
    ) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let sql=format!("SELECT CAST(id AS CHAR) id,CAST(train_id AS CHAR) train_id,user_id,is_finish,role,attempt FROM {user_table} WHERE CAST(train_id AS CHAR)=? ORDER BY id");
    match sqlx::query(&sql).bind(id).fetch_all(pool).await{Ok(rows)=>envelope_json(StatusCode::OK,200,Some(json!(rows.iter().map(|r|json!({"id":r.get::<String,_>("id"),"trainId":r.get::<Option<String>,_>("train_id"),"userId":r.get::<Option<String>,_>("user_id"),"isFinish":r.get::<Option<i32>,_>("is_finish"),"role":r.get::<Option<i32>,_>("role"),"attempt":r.get::<Option<i32>,_>("attempt")})).collect::<Vec<_>>())),"ok",""),Err(e)=>db_error(e,"list general members")}
}
async fn page(state: AppState, user: AuthUser, body: Value, domain: Domain) -> Response {
    let id = body
        .get("trainId")
        .or_else(|| body.get("id"))
        .map(Value::to_string)
        .unwrap_or_default()
        .trim_matches('"')
        .to_owned();
    let number = body
        .get("pageNumber")
        .or_else(|| body.get("floorNumber"))
        .and_then(Value::as_i64)
        .unwrap_or(1);
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let main = table(domain);
    let owner_sql = format!("SELECT id FROM {main} WHERE CAST(id AS CHAR)=? AND create_user=?");
    if !matches!(
        sqlx::query(&owner_sql)
            .bind(&id)
            .bind(&user.id)
            .fetch_optional(pool)
            .await,
        Ok(Some(_))
    ) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let(sql,kind)=match domain{Domain::Key=>("SELECT id,page_number,sort,`key`,value,time FROM general_key_pat_page WHERE CAST(train_id AS CHAR)=? AND page_number=? ORDER BY sort",0),Domain::Telex=>("SELECT id,page_number,sort,`key`,value,NULL AS time FROM general_telex_pat_page WHERE CAST(train_id AS CHAR)=? AND page_number=? ORDER BY sort",0),Domain::Ticker=>("SELECT id,floor_number AS page_number,sort,mores_key AS `key`,mores_value AS value,mores_time AS time FROM general_ticker_pat_train_page WHERE CAST(train_id AS CHAR)=? AND floor_number=? ORDER BY sort",1)};
    match sqlx::query(sql).bind(id).bind(number).fetch_all(pool).await {
        Ok(rows) => envelope_json(
            StatusCode::OK,
            200,
            Some(
                json!({"pageNumber":number,"type":kind,"content":rows.iter().map(|r|json!({"id":r.get::<String,_>("id"),"pageNumber":r.get::<Option<i32>,_>("page_number"),"sort":r.get::<Option<i32>,_>("sort"),"key":r.get::<Option<String>,_>("key"),"value":r.get::<Option<String>,_>("value"),"time":r.get::<Option<String>,_>("time")})).collect::<Vec<_>>() }),
            ),
            "ok",
            "",
        ),
        Err(e) => db_error(e, "general page"),
    }
}
async fn upload(state: AppState, user: AuthUser, body: Value, domain: Domain) -> Response {
    let id = body
        .get("trainId")
        .map(Value::to_string)
        .unwrap_or_default()
        .trim_matches('"')
        .to_owned();
    let page = body
        .get("pageNumber")
        .or_else(|| body.get("floorNumber"))
        .and_then(Value::as_i64)
        .unwrap_or(1);
    let value = body
        .get("value")
        .or_else(|| body.get("content"))
        .cloned()
        .unwrap_or(Value::Null)
        .to_string();
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let user_table = match domain {
        Domain::Key => "general_key_pat_user",
        Domain::Telex => "general_telex_pat_user",
        Domain::Ticker => "general_ticker_pat_train_user",
    };
    let allowed_sql =
        format!("SELECT id FROM {user_table} WHERE CAST(train_id AS CHAR)=? AND user_id=? LIMIT 1");
    if !matches!(
        sqlx::query(&allowed_sql)
            .bind(&id)
            .bind(&user.id)
            .fetch_optional(pool)
            .await,
        Ok(Some(_))
    ) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let result=match domain{Domain::Key=>sqlx::query("INSERT INTO general_key_pat_user_value (train_id,user_id,page_number,sort,value) VALUES (?,?,?,0,?)").bind(id).bind(&user.id).bind(page).bind(value).execute(pool).await,Domain::Telex=>sqlx::query("INSERT INTO general_telex_pat_user_value (train_id,user_id,page_number,sort,value,received_at) VALUES (?,?,?,0,?,NOW(6))").bind(id).bind(&user.id).bind(page).bind(value).execute(pool).await,Domain::Ticker=>sqlx::query("INSERT INTO general_ticker_pat_train_user_value (train_id,user_id,floor_number,sort,mores_value) VALUES (?,?,?,0,?)").bind(id).bind(&user.id).bind(page).bind(value).execute(pool).await};
    match result {
        Ok(_) => envelope_response(StatusCode::OK, 200, "ok", ""),
        Err(e) => db_error(e, "upload general result"),
    }
}

pub(crate) async fn key_page(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    page(s, u, b, Domain::Key).await
}
pub(crate) async fn key_members(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    members(s, u, b, Domain::Key).await
}
pub(crate) async fn key_upload(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    upload(s, u, b, Domain::Key).await
}
pub(crate) async fn key_online(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    members(s, u, json!({"trainId":q.train_id}), Domain::Key).await
}
pub(crate) async fn telex_page(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    page(s, u, b, Domain::Telex).await
}
pub(crate) async fn telex_members(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    members(s, u, b, Domain::Telex).await
}
pub(crate) async fn telex_upload(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    upload(s, u, b, Domain::Telex).await
}
pub(crate) async fn telex_online(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    members(s, u, json!({"trainId":q.train_id}), Domain::Telex).await
}
pub(crate) async fn ticker_page(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    page(s, u, b, Domain::Ticker).await
}
pub(crate) async fn ticker_upload(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    upload(s, u, b, Domain::Ticker).await
}
