use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{
        rejection::{JsonRejection, QueryRejection},
        Extension, Query, State,
    },
    http::StatusCode,
    response::Response,
    Json,
};
use serde::{Deserialize, Deserializer};
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, MySql, Row, Transaction};

pub(super) const DETAIL_SELECT: &str = "SELECT id,device_type,device_id,topic,answer,scoring_rule_content,CAST(score AS CHAR) AS score,content,create_user FROM group_net_train WHERE id=?";

#[derive(Debug)]
pub(super) enum Error {
    Invalid(String),
    Denied,
    Terminal,
    Database(sqlx::Error),
    Json(serde_json::Error),
}

impl From<sqlx::Error> for Error {
    fn from(error: sqlx::Error) -> Self {
        Self::Database(error)
    }
}

impl From<serde_json::Error> for Error {
    fn from(error: serde_json::Error) -> Self {
        Self::Json(error)
    }
}

pub(super) fn invalid(message: impl Into<String>) -> Error {
    Error::Invalid(message.into())
}

pub(super) fn failure(error: Error) -> Response {
    match error {
        Error::Invalid(message) => envelope_response(StatusCode::OK, 202, &message, &message),
        Error::Denied => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Error::Terminal => envelope_response(
            StatusCode::OK,
            208,
            "训练已提交，不能修改答案或成绩",
            "训练已提交，不能修改答案或成绩",
        ),
        Error::Database(error) => {
            tracing::error!(%error, "group net database operation failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
        Error::Json(error) => {
            tracing::error!(%error, "group net response serialization failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(super) async fn finish(tx: Transaction<'_, MySql>, result: Result<Value, Error>) -> Response {
    match result {
        Ok(data) => match tx.commit().await {
            Ok(()) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
            Err(error) => failure(error.into()),
        },
        Err(error) => match tx.rollback().await {
            Ok(()) => failure(error),
            Err(rollback_error) => {
                tracing::error!(?error, %rollback_error, "group net rollback failed");
                failure(rollback_error.into())
            }
        },
    }
}

pub(super) fn score_json(score: Option<String>) -> Result<Value, Error> {
    match score {
        Some(score) => Ok(Value::Number(score.parse()?)),
        None => Ok(Value::Null),
    }
}

pub(super) fn detail_json(row: &MySqlRow) -> Result<Value, Error> {
    Ok(json!({
        "id": row.try_get::<i32, _>("id")?,
        "deviceType": row.try_get::<Option<i32>, _>("device_type")?,
        "deviceId": row.try_get::<Option<i32>, _>("device_id")?,
        "topic": row.try_get::<Option<String>, _>("topic")?,
        "answer": row.try_get::<Option<String>, _>("answer")?,
        "scoringRuleContent": row.try_get::<Option<String>, _>("scoring_rule_content")?,
        "score": score_json(row.try_get("score")?)?,
        "content": row.try_get::<Option<String>, _>("content")?,
    }))
}

pub(super) fn require_owner(row: &MySqlRow, user: &AuthUser) -> Result<(), Error> {
    if row.try_get::<Option<String>, _>("create_user")?.as_deref() != Some(user.id.as_str()) {
        return Err(Error::Denied);
    }
    Ok(())
}

// Route query IDs are strings in the Vue submit payload; Java also accepts them.
pub(super) fn deserialize_id<'de, D>(deserializer: D) -> Result<Option<i32>, D::Error>
where
    D: Deserializer<'de>,
{
    #[derive(Deserialize)]
    #[serde(untagged)]
    enum Id {
        Number(i32),
        Text(String),
    }
    match Option::<Id>::deserialize(deserializer)? {
        None => Ok(None),
        Some(Id::Number(id)) => Ok(Some(id)),
        Some(Id::Text(id)) if id.trim().is_empty() => Ok(None),
        Some(Id::Text(id)) => id
            .trim()
            .parse()
            .map(Some)
            .map_err(serde::de::Error::custom),
    }
}

#[derive(Deserialize)]
pub(crate) struct PageBody {
    #[serde(default, deserialize_with = "deserialize_id")]
    page: Option<i32>,
    #[serde(default, deserialize_with = "deserialize_id")]
    rows: Option<i32>,
}

#[derive(Deserialize)]
pub(crate) struct IdQuery {
    id: Option<i32>,
}

pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    body: Result<Json<PageBody>, JsonRejection>,
) -> Response {
    let Ok(Json(body)) = body else {
        return failure(invalid("请求参数错误"));
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let page = i64::from(body.page.unwrap_or(1).max(1));
    let rows = i64::from(body.rows.unwrap_or(20).clamp(1, 200));
    let result = async {
        let total = sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM group_net_train WHERE create_user=?")
            .bind(&user.id)
            .fetch_one(pool)
            .await?;
        let records = sqlx::query("SELECT g.id,CAST(g.score AS CHAR) AS score,DATE_FORMAT(g.create_time,'%Y-%m-%d %H:%i:%s') AS create_time,d.device_name,t.type_name FROM group_net_train g LEFT JOIN t_device d ON d.id=g.device_id LEFT JOIN t_device_type t ON t.id=g.device_type WHERE g.create_user=? ORDER BY g.create_time DESC LIMIT ? OFFSET ?")
            .bind(&user.id)
            .bind(rows)
            .bind((page - 1) * rows)
            .fetch_all(pool)
            .await?;
        let data = records.iter().map(|row| -> Result<Value, Error> {
            Ok(json!({
                "id": row.try_get::<i32, _>("id")?,
                "trainName": Value::Null,
                "deviceName": row.try_get::<Option<String>, _>("device_name")?,
                "deviceTypeName": row.try_get::<Option<String>, _>("type_name")?,
                "score": score_json(row.try_get("score")?)?,
                "createTime": row.try_get::<Option<String>, _>("create_time")?,
            }))
        }).collect::<Result<Vec<_>, _>>()?;
        Ok::<_, Error>(json!({
            "totalPage": (total + rows - 1) / rows,
            "totalNumber": total,
            "currentPage": page - 1,
            "pageSize": rows,
            "data": data,
        }))
    }.await;
    match result {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(error) => failure(error),
    }
}

pub(crate) async fn detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    query: Result<Query<IdQuery>, QueryRejection>,
) -> Response {
    let Ok(Query(IdQuery { id: Some(id) })) = query else {
        return failure(invalid("请求参数错误"));
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result = async {
        let row = sqlx::query(DETAIL_SELECT)
            .bind(id)
            .fetch_optional(pool)
            .await?
            .ok_or_else(|| invalid("未查询到该训练"))?;
        require_owner(&row, &user)?;
        detail_json(&row)
    }
    .await;
    match result {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(error) => failure(error),
    }
}
