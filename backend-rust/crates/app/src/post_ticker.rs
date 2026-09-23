use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;
const COLS:&str="id,name,is_cable,rate,type,code_message_body,message_number,code_short,valid_time,score,status,user_id,result,more_code,lack_code,error_code,more_group,lack_group,total_number";
fn item(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id":row.get::<String,_>("id"),"name":row.get::<Option<String>,_>("name"),"isCable":row.get::<Option<i32>,_>("is_cable"),"rate":row.get::<Option<i32>,_>("rate"),"type":row.get::<Option<i32>,_>("type"),"codeMessageBody":row.get::<Option<String>,_>("code_message_body"),"messageNumber":row.get::<Option<i32>,_>("message_number"),"codeShort":row.get::<Option<i32>,_>("code_short"),"validTime":row.get::<Option<String>,_>("valid_time"),"score":row.get::<Option<String>,_>("score"),"status":row.get::<Option<i32>,_>("status"),"result":row.get::<Option<String>,_>("result"),"moreCode":row.get::<Option<i32>,_>("more_code"),"lackCode":row.get::<Option<i32>,_>("lack_code"),"errorCode":row.get::<Option<i32>,_>("error_code"),"moreGroup":row.get::<Option<i32>,_>("more_group"),"lackGroup":row.get::<Option<i32>,_>("lack_group"),"totalNumber":row.get::<Option<i32>,_>("total_number")})
}
#[derive(serde::Deserialize)]
pub(crate) struct IdRequest {
    pub id: Option<String>,
}
pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(page): Json<PageBody>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let no = page.page.unwrap_or(1).max(1);
    let rows = page.rows.unwrap_or(20).clamp(1, 200);
    let total =
        match sqlx::query("SELECT COUNT(*) AS total FROM t_post_ticker_tape_train WHERE user_id=?")
            .bind(&user.id)
            .fetch_one(pool)
            .await
        {
            Ok(r) => r.get::<i64, _>("total"),
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        };
    let sql=format!("SELECT {COLS} FROM t_post_ticker_tape_train WHERE user_id=? ORDER BY create_time DESC LIMIT ? OFFSET ?");
    match sqlx::query(&sql)
        .bind(&user.id)
        .bind(rows)
        .bind((no - 1) * rows)
        .fetch_all(pool)
        .await
    {
        Ok(items) => envelope_json(
            StatusCode::OK,
            200,
            Some(
                json!({"totalPage":if total==0{0}else{(total+rows-1)/rows},"totalNumber":total,"currentPage":no,"pageSize":rows,"data":items.iter().map(item).collect::<Vec<_>>() }),
            ),
            "ok",
            "",
        ),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}
#[derive(serde::Deserialize)]
pub(crate) struct PageBody {
    page: Option<i64>,
    rows: Option<i64>,
}
pub(crate) async fn detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<IdRequest>,
) -> Response {
    let Some(id) = body.id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = format!("SELECT {COLS} FROM t_post_ticker_tape_train WHERE id=? LIMIT 1");
    match sqlx::query(&sql).bind(id).fetch_optional(pool).await {
        Ok(Some(row))
            if row.get::<Option<String>, _>("user_id").as_deref() == Some(user.id.as_str()) =>
        {
            envelope_json(StatusCode::OK, 200, Some(item(&row)), "ok", "")
        }
        Ok(Some(_)) => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Ok(None) => envelope_response(StatusCode::OK, 202, "未查询到训练", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}
