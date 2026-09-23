use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;
fn item(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id":row.get::<i32,_>("id"),"deviceType":row.get::<Option<i32>,_>("device_type"),"deviceId":row.get::<Option<i32>,_>("device_id"),"topic":row.get::<Option<String>,_>("topic"),"answer":row.get::<Option<String>,_>("answer"),"scoringRuleContent":row.get::<Option<String>,_>("scoring_rule_content"),"score":row.get::<Option<String>,_>("score"),"content":row.get::<Option<String>,_>("content")})
}
#[derive(serde::Deserialize)]
pub(crate) struct PageBody {
    page: Option<i64>,
    rows: Option<i64>,
}
#[derive(serde::Deserialize)]
pub(crate) struct IdQuery {
    id: Option<i32>,
}
pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<PageBody>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let page = body.page.unwrap_or(1).max(1);
    let rows = body.rows.unwrap_or(20).clamp(1, 200);
    let total =
        match sqlx::query("SELECT COUNT(*) AS total FROM group_net_train WHERE create_user=?")
            .bind(&user.id)
            .fetch_one(pool)
            .await
        {
            Ok(r) => r.get::<i64, _>("total"),
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        };
    match sqlx::query("SELECT id,device_type,device_id,topic,answer,scoring_rule_content,score,content FROM group_net_train WHERE create_user=? ORDER BY create_time DESC LIMIT ? OFFSET ?").bind(&user.id).bind(rows).bind((page-1)*rows).fetch_all(pool).await{Ok(data)=>envelope_json(StatusCode::OK,200,Some(json!({"totalPage":if total==0{0}else{(total+rows-1)/rows},"totalNumber":total,"currentPage":page,"pageSize":rows,"data":data.iter().map(item).collect::<Vec<_>>() })),"ok",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}
pub(crate) async fn detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<IdQuery>,
) -> Response {
    let Some(id) = query.id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,device_type,device_id,topic,answer,scoring_rule_content,score,content,create_user FROM group_net_train WHERE id=? LIMIT 1").bind(id).fetch_optional(pool).await{Ok(Some(row)) if row.get::<Option<String>,_>("create_user").as_deref()==Some(user.id.as_str())=>envelope_json(StatusCode::OK,200,Some(item(&row)),"ok",""),Ok(Some(_))=>envelope_response(StatusCode::OK,207,"无权限",""),Ok(None)=>envelope_response(StatusCode::OK,202,"未查询到训练",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}
