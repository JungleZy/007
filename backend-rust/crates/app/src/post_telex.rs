use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;
fn row_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id":row.get::<String,_>("id"),"name":row.get::<Option<String>,_>("name"),"isCable":row.get::<Option<i32>,_>("is_cable"),"type":row.get::<Option<i32>,_>("type"),"trainType":row.get::<Option<i32>,_>("train_type"),"status":row.get::<Option<i32>,_>("status"),"groupNumber":row.get::<Option<i32>,_>("group_number"),"errorNumber":row.get::<Option<i32>,_>("error_number"),"accuracy":row.get::<Option<String>,_>("accuracy"),"speed":row.get::<Option<String>,_>("speed"),"validTime":row.get::<Option<i32>,_>("valid_time"),"score":row.get::<Option<String>,_>("score"),"totalSpeed":row.get::<Option<String>,_>("total_speed"),"attempt":row.get::<Option<i32>,_>("attempt"),"protocolVersion":row.get::<Option<i32>,_>("protocol_version")})
}
const COLUMNS:&str="id,name,is_cable,type,train_type,status,group_number,error_number,accuracy,speed,valid_time,score,total_speed,attempt,protocol_version";
pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<TrainQuery>,
    Json(page): Json<PageBody>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let train_type = query.train_type.unwrap_or(0);
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
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
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
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}
#[derive(serde::Deserialize)]
pub(crate) struct TrainQuery {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
    #[serde(rename = "trainType")]
    train_type: Option<i32>,
}
#[derive(serde::Deserialize)]
pub(crate) struct PageBody {
    page: Option<i64>,
    rows: Option<i64>,
}
pub(crate) async fn detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TrainQuery>,
) -> Response {
    let Some(id) = body.train_id.filter(|v| !v.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql =
        format!("SELECT {COLUMNS},create_user FROM t_post_telex_pat_train WHERE id=? LIMIT 1");
    match sqlx::query(&sql).bind(id).fetch_optional(pool).await {
        Ok(Some(row))
            if row.get::<Option<String>, _>("create_user").as_deref() == Some(user.id.as_str()) =>
        {
            envelope_json(StatusCode::OK, 200, Some(row_json(&row)), "ok", "")
        }
        Ok(Some(_)) => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Ok(None) => envelope_response(StatusCode::OK, 202, "未查询到训练", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}
