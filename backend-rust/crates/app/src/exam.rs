use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;

pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let state_filter = body.get("state").and_then(Value::as_bool).unwrap_or(true);
    let states = if state_filter { vec![1, 2] } else { vec![3, 4] };
    let rows=match sqlx::query("SELECT DISTINCT e.id,e.title,e.start_time,e.end_time,e.duration,e.create_user_id,e.create_time,e.teacher,e.state FROM t_theory_knowledge_exam e JOIN t_theory_knowledge_exam_user u ON u.exam_id=e.id WHERE u.is_self_testing=1").fetch_all(pool).await{Ok(r)=>r,Err(_)=>return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")};
    let admin=sqlx::query_scalar::<_,i64>("SELECT COUNT(*) FROM t_user_role ur JOIN t_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.is_admin=0").bind(&user.id).fetch_one(pool).await.unwrap_or(0)>0;
    let data=rows.into_iter().filter(|row|states.contains(&row.get::<Option<i32>,_>("state").unwrap_or_default())&&(admin||row.get::<Option<String>,_>("create_user_id").as_deref()==Some(user.id.as_str())||row.get::<Option<String>,_>("teacher").as_deref()==Some(user.id.as_str()))).map(|row|json!({"id":row.get::<String,_>("id"),"title":row.get::<Option<String>,_>("title"),"startTime":row.get::<Option<String>,_>("start_time"),"endTime":row.get::<Option<String>,_>("end_time"),"duration":row.get::<Option<String>,_>("duration"),"createUserId":row.get::<Option<String>,_>("create_user_id"),"createTime":row.get::<Option<String>,_>("create_time"),"teacher":row.get::<Option<String>,_>("teacher"),"state":row.get::<Option<i32>,_>("state")})).collect::<Vec<_>>();
    envelope_json(StatusCode::OK, 200, Some(json!(data)), "ok", "")
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
    let exam=match sqlx::query("SELECT id,title,start_time,end_time,duration,create_user_id,create_time,teacher,state FROM t_theory_knowledge_exam WHERE id=? LIMIT 1").bind(id).fetch_optional(pool).await{Ok(Some(r))=>r,Ok(None)=>return envelope_response(StatusCode::OK,500,"未查询到考试",""),Err(_)=>return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")};
    let candidate=sqlx::query("SELECT id,user_id,state,score,content,is_self_testing FROM t_theory_knowledge_exam_user WHERE exam_id=? AND user_id=? LIMIT 1").bind(id).bind(&user.id).fetch_optional(pool).await.ok().flatten();
    let admin=sqlx::query_scalar::<_,i64>("SELECT COUNT(*) FROM t_user_role ur JOIN t_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.is_admin=0").bind(&user.id).fetch_one(pool).await.unwrap_or(0)>0;
    let controller = admin
        || exam.get::<Option<String>, _>("create_user_id").as_deref() == Some(user.id.as_str())
        || exam.get::<Option<String>, _>("teacher").as_deref() == Some(user.id.as_str());
    if !controller && candidate.is_none() {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let users = if controller {
        sqlx::query(
            "SELECT user_id,state,score,content FROM t_theory_knowledge_exam_user WHERE exam_id=?",
        )
        .bind(id)
        .fetch_all(pool)
        .await
        .unwrap_or_default()
    } else {
        Vec::new()
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(
            json!({"exam":{"id":exam.get::<String,_>("id"),"title":exam.get::<Option<String>,_>("title"),"startTime":exam.get::<Option<String>,_>("start_time"),"endTime":exam.get::<Option<String>,_>("end_time"),"duration":exam.get::<Option<String>,_>("duration"),"createUserId":exam.get::<Option<String>,_>("create_user_id"),"teacher":exam.get::<Option<String>,_>("teacher"),"state":exam.get::<Option<i32>,_>("state")},"user":users.iter().map(|row|json!({"userId":row.get::<Option<String>,_>("user_id"),"state":row.get::<Option<i32>,_>("state"),"score":row.get::<Option<i32>,_>("score"),"content":row.get::<Option<String>,_>("content")})).collect::<Vec<_>>(),"paper":Value::Null}),
        ),
        "ok",
        "",
    )
}

pub(crate) async fn self_test(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let exam_id = uuid::Uuid::new_v4().to_string();
    let snap_id = uuid::Uuid::new_v4().to_string();
    let now = crate::now_time_millis().to_string();
    let paper = body.get("testPaper").cloned().unwrap_or_else(|| json!({}));
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if sqlx::query("INSERT INTO t_theory_knowledge_exam (id,title,start_time,duration,create_user_id,create_time,state) VALUES (?,?,?,?,?,?,2)").bind(&exam_id).bind(body.get("title").and_then(Value::as_str)).bind(&now).bind(body.get("duration").and_then(Value::as_str)).bind(&user.id).bind(&now).execute(&mut *tx).await.is_err(){let _=tx.rollback().await;return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");}
    if sqlx::query("INSERT INTO t_theory_knowledge_exam_test_paper (id,name,test_paper_id,total,pass_the_exam_than,exam_id,pass_mark,single_choice_list,multiple_choice_list,judge_list,completion_list,short_answer) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)").bind(&snap_id).bind(paper.get("name").and_then(Value::as_str)).bind(paper.get("id").and_then(Value::as_str)).bind(paper.get("total").and_then(Value::as_i64)).bind(paper.get("passTheExamThan").and_then(Value::as_str)).bind(&exam_id).bind(paper.get("passMark").and_then(Value::as_i64)).bind(paper.get("singleChoice").unwrap_or(&Value::Array(Vec::new())).to_string()).bind(paper.get("multipleChoice").unwrap_or(&Value::Array(Vec::new())).to_string()).bind(paper.get("judge").unwrap_or(&Value::Array(Vec::new())).to_string()).bind(paper.get("completion").unwrap_or(&Value::Array(Vec::new())).to_string()).bind(paper.get("shortAnswer").unwrap_or(&Value::Array(Vec::new())).to_string()).execute(&mut *tx).await.is_err(){let _=tx.rollback().await;return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");}
    if sqlx::query("INSERT INTO t_theory_knowledge_exam_user (id,exam_id,user_id,state,score,is_self_testing,start_time) VALUES (?,?,?,2,0,0,?)").bind(uuid::Uuid::new_v4().to_string()).bind(&exam_id).bind(&user.id).bind(&now).execute(&mut *tx).await.is_err(){let _=tx.rollback().await;return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");}
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, Some(json!({"id":exam_id})), "ok", "")
}
