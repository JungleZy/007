use crate::platform::{
    auth::AuthUser,
    envelope::{envelope_json, envelope_response},
    state::AppState,
};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;
fn err(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error = %error, operation, "post telegram read failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

fn train_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id":row.get::<String,_>("id"),"name":row.get::<Option<String>,_>("name"),"isCable":row.get::<Option<i32>,_>("is_cable"),"type":row.get::<Option<i32>,_>("type"),"codeSort":row.get::<Option<i32>,_>("code_sort")==Some(1),"isRandom":row.get::<Option<i32>,_>("is_random")==Some(1),"messageNumber":row.get::<Option<i32>,_>("message_number"),"validTime":row.get::<Option<i64>,_>("valid_time"),"speed":row.get::<Option<String>,_>("speed"),"status":row.get::<Option<i32>,_>("status"),"createUser":row.get::<Option<String>,_>("create_user"),"lack":row.get::<Option<i32>,_>("lack"),"errorNumber":row.get::<Option<i32>,_>("error_number"),"accuracy":row.get::<Option<String>,_>("accuracy"),"floorNow":row.get::<Option<i32>,_>("floor_now"),"attempt":row.get::<Option<i32>,_>("attempt"),"protocolVersion":row.get::<Option<i32>,_>("protocol_version"),"activeMillis":row.get::<Option<i64>,_>("active_millis"),"ruleId":row.get::<Option<String>,_>("rule_id"),"ruleContent":row.get::<Option<String>,_>("rule_content"),"score":row.get::<Option<String>,_>("score"),"deductInfo":row.get::<Option<String>,_>("deduct_info"),"statisticInfo":row.get::<Option<String>,_>("statistic_info"),"speedLog":row.get::<Option<String>,_>("speed_log"),"startTime":row.get::<Option<String>,_>("start_text"),"endTime":row.get::<Option<String>,_>("end_text"),"createTime":row.get::<Option<String>,_>("create_text"),"serverElapsedMs":0,"messageGroup":null,"pageAnalyzeVOS":null,"finishInfo":null,"messageBody":null,"existNumber":null,"standards":null,"resolver":null})
}

pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,name,is_cable,type,code_sort,is_random,message_number,valid_time,speed,status,create_user,error_number,lack,accuracy,floor_now,attempt,protocol_version,active_millis,rule_id,rule_content,score,deduct_info,statistic_info,speed_log,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') AS start_text,DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') AS end_text,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS create_text FROM t_post_telegram_train WHERE create_user=? ORDER BY create_time DESC").bind(&user.id).fetch_all(pool).await{Ok(rows)=>envelope_json(StatusCode::OK,200,Some(Value::Array(rows.iter().map(train_json).collect())),"ok",""),Err(error)=>err(error,"list")}
}
pub(crate) async fn detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body.get("id").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "请求参数错误");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let row = match sqlx::query("SELECT id,name,is_cable,type,code_sort,is_random,message_number,valid_time,speed,status,create_user,error_number,lack,accuracy,floor_now,attempt,protocol_version,full_score,active_millis,rule_id,rule_content,score,deduct_info,statistic_info,speed_log,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') AS start_text,DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') AS end_text,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS create_text,CAST(COALESCE(GREATEST(0,TIMESTAMPDIFF(MICROSECOND,start_time,NOW(6))/1000),0) AS SIGNED) AS elapsed_ms FROM t_post_telegram_train WHERE id=? LIMIT 1")
        .bind(id).fetch_optional(pool).await {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到训练", "未查询到训练"),
        Err(error) => return err(error, "detail-train"),
    };
    let id = row.get::<String, _>("id");
    if row.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let source = match sqlx::query("SELECT id,floor_number,sort,mores_key,mores_value,mores_time,pat_keys FROM t_post_telegram_train_floor_content WHERE train_id=? ORDER BY floor_number,sort")
        .bind(&id).fetch_all(pool).await {
        Ok(rows) => rows,
        Err(error) => return err(error, "detail-source"),
    };
    let values = match sqlx::query("SELECT v.floor_number,v.message_body,v.standard,v.finish_info,v.resolver,v.capture_intervals,CAST(COALESCE(GREATEST(0,TIMESTAMPDIFF(MICROSECOND,t.start_time,v.received_at)/1000),0) AS SIGNED) AS received_elapsed_ms FROM t_post_telegram_train_floor_content_value v JOIN t_post_telegram_train t ON t.id=v.train_id WHERE v.train_id=? ORDER BY v.floor_number")
        .bind(&id).fetch_all(pool).await {
        Ok(rows) => rows,
        Err(error) => return err(error, "detail-values"),
    };
    let mut floors = Vec::new();
    for source_row in &source {
        if let Some(floor) = source_row.get::<Option<i32>, _>("floor_number") {
            if !floors.contains(&floor) {
                floors.push(floor);
            }
        }
    }
    let preview_floors = floors.iter().copied().take(2).collect::<Vec<_>>();
    let source_floor_count = floors.len();
    let source_group_count = source.len();
    let mut message_body = Vec::new();
    let mut finish_info = Vec::new();
    let mut standards = Vec::new();
    let mut resolver = Vec::new();
    let analyses = if row.get::<Option<i32>, _>("protocol_version") == Some(1) {
        let mut analyses = Vec::with_capacity(values.len());
        for saved in &values {
            match super::post_telegram_write::saved_page_analysis(saved, &id) {
                Ok(analysis) => analyses.push(analysis),
                Err(response) => return response,
            }
        }
        Value::Array(analyses)
    } else {
        Value::Null
    };
    for floor in preview_floors.iter().copied() {
        let rows: Vec<Value> = source.iter().filter(|row| row.get::<Option<i32>, _>("floor_number") == Some(floor)).map(|row| json!({
            "id": row.get::<Option<String>, _>("id"), "moresKey": row.get::<Option<String>, _>("mores_key"),
            "moresValue": row.get::<Option<String>, _>("mores_value").unwrap_or_else(|| "[]".into()),
            "moresTime": row.get::<Option<String>, _>("mores_time").unwrap_or_else(|| "[]".into()),
            "patKeys": row.get::<Option<String>, _>("pat_keys").unwrap_or_else(|| "[]".into()), "patLogs": "[]"
        })).collect();
        if let Some(saved) = values
            .iter()
            .find(|value| value.get::<Option<i32>, _>("floor_number") == Some(floor))
        {
            let body_text = saved
                .get::<Option<String>, _>("message_body")
                .unwrap_or_else(|| "[]".into());
            message_body.push(Value::String(body_text));
            finish_info.push(
                saved
                    .get::<Option<String>, _>("finish_info")
                    .and_then(|value| serde_json::from_str(&value).ok())
                    .unwrap_or(Value::Null),
            );
            standards.push(Value::String(
                saved
                    .get::<Option<String>, _>("standard")
                    .unwrap_or_else(|| "{}".into()),
            ));
            resolver.push(
                saved
                    .get::<Option<String>, _>("resolver")
                    .map(Value::String)
                    .unwrap_or(Value::Null),
            );
        } else {
            message_body.push(Value::String(
                serde_json::to_string(&rows).unwrap_or_else(|_| "[]".into()),
            ));
            finish_info.push(Value::Null);
            standards.push(Value::String("{}".into()));
            resolver.push(Value::Null);
        }
    }
    if row.get::<Option<i32>, _>("protocol_version") != Some(1)
        && row.get::<Option<i32>, _>("status") != Some(2)
    {
        return envelope_response(
            StatusCode::OK,
            202,
            "旧训练缺少原始采集协议，请终止旧训练并新建",
            "旧训练缺少原始采集协议，请终止旧训练并新建",
        );
    }
    let mut item = train_json(&row);
    item["serverElapsedMs"] = json!(row.get::<i64, _>("elapsed_ms"));
    item["existNumber"] = json!(floors);
    if item["floorNow"].is_null() {
        item["floorNow"] = json!(1);
    }
    if row.get::<Option<i32>, _>("is_cable") == Some(1) {
        item["messageNumber"] = json!(source_floor_count);
        item["messageGroup"] = json!(source_group_count);
    }
    item["messageBody"] = Value::Array(message_body);
    item["finishInfo"] = Value::Array(finish_info);
    item["standards"] = Value::Array(standards);
    item["resolver"] = Value::Array(resolver);
    item["pageAnalyzeVOS"] = analyses;
    envelope_json(StatusCode::OK, 200, Some(item), "ok", "")
}
