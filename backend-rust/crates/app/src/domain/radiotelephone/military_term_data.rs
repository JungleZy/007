use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;
use axum::{
    extract::{rejection::JsonRejection, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, MySql, MySqlConnection, Row, Transaction};
use std::collections::HashMap;

#[derive(Debug)]
enum Error {
    Invalid(&'static str),
    Database(sqlx::Error),
    System,
}

impl From<sqlx::Error> for Error {
    fn from(error: sqlx::Error) -> Self {
        Self::Database(error)
    }
}

fn failure(error: Error) -> Response {
    match error {
        Error::Invalid(message) => envelope_response(StatusCode::OK, 202, message, message),
        Error::Database(error) => {
            tracing::error!(%error, "military term database operation failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
        Error::System => {
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

async fn finish(tx: Transaction<'_, MySql>, result: Result<Option<Value>, Error>) -> Response {
    match result {
        Ok(data) => match tx.commit().await {
            Ok(()) => envelope_json(StatusCode::OK, 200, data, "ok", ""),
            Err(error) => failure(error.into()),
        },
        Err(error) => match tx.rollback().await {
            Ok(()) => failure(error),
            Err(rollback_error) => {
                tracing::error!(?error, %rollback_error, "military term rollback failed");
                failure(rollback_error.into())
            }
        },
    }
}

fn default_sort() -> Option<i32> {
    Some(0)
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct SaveBody {
    #[serde(rename = "type")]
    pub(super) kind: Option<i32>,
    pub(super) parent_id: Option<String>,
    pub(super) key: Option<String>,
    pub(super) value: Option<String>,
}

#[derive(Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct Term {
    pub(super) id: Option<String>,
    pub(super) parent_id: Option<String>,
    pub(super) key: Option<String>,
    pub(super) value: Option<String>,
    #[serde(default = "default_sort")]
    pub(super) sort: Option<i32>,
    child: Option<Vec<Term>>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct MoveBody {
    source_id: Option<String>,
    target_id: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct BatchRow {
    parent_name: Option<String>,
    child_name: Option<String>,
    pub(super) content: Option<String>,
}

fn term(row: &MySqlRow) -> Result<Term, sqlx::Error> {
    Ok(Term {
        id: Some(row.try_get("id")?),
        parent_id: row.try_get("parent_id")?,
        key: row.try_get("key")?,
        value: row.try_get("value")?,
        sort: row.try_get("sort")?,
        child: None,
    })
}

async fn tree(conn: &mut MySqlConnection) -> Result<Value, Error> {
    let roots = sqlx::query("SELECT id,parent_id,`key`,value,sort FROM t_military_term_data WHERE parent_id='0' ORDER BY sort")
        .fetch_all(&mut *conn).await?;
    let children = sqlx::query("SELECT c.id,c.parent_id,c.`key`,c.value,c.sort,p.id AS root_id FROM t_military_term_data c JOIN t_military_term_data p ON c.parent_id=p.id WHERE p.parent_id='0' ORDER BY c.sort")
        .fetch_all(&mut *conn).await?;
    let mut grouped: HashMap<String, Vec<Term>> = HashMap::new();
    for child in &children {
        grouped
            .entry(child.try_get("root_id")?)
            .or_default()
            .push(term(child)?);
    }
    let mut result = Vec::with_capacity(roots.len());
    for root in &roots {
        let mut root = term(root)?;
        root.child = Some(
            grouped
                .remove(root.id.as_deref().unwrap_or_default())
                .unwrap_or_default(),
        );
        result.push(root);
    }
    Ok(json!(result))
}

/// 表只有主键索引：按 parent_id/sort 的写入会 next-key 锁住整表。这里先按主键锁父行（根级的 '0'
/// 没有对应行，不锁），再由调用方普通读出兄弟 id，最后逐个按主键改 sort。
async fn lock_parent(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    parent: Option<&str>,
) -> Result<(), sqlx::Error> {
    if let Some(parent) = parent.filter(|parent| *parent != "0") {
        sqlx::query("SELECT id FROM t_military_term_data WHERE id=? FOR UPDATE")
            .bind(parent)
            .fetch_optional(&mut **tx)
            .await?;
    }
    Ok(())
}

async fn shift_sorts(
    tx: &mut sqlx::Transaction<'_, sqlx::MySql>,
    ids: &[String],
    delta: i32,
) -> Result<(), sqlx::Error> {
    for id in ids {
        sqlx::query("UPDATE t_military_term_data SET sort=sort+? WHERE id=?")
            .bind(delta)
            .bind(id)
            .execute(&mut **tx)
            .await?;
    }
    Ok(())
}

pub(super) async fn find_all(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return failure(Error::System);
    };
    let mut conn = match pool.acquire().await {
        Ok(conn) => conn,
        Err(error) => return failure(error.into()),
    };
    match tree(&mut conn).await {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(error) => failure(error),
    }
}

async fn next_sort(conn: &mut MySqlConnection, parent: Option<&str>) -> Result<i32, Error> {
    let row =
        sqlx::query("SELECT MAX(sort) AS max_sort FROM t_military_term_data WHERE parent_id=?")
            .bind(parent)
            .fetch_one(&mut *conn)
            .await?;
    let maximum: Option<i32> = row.try_get("max_sort")?;
    // Java Integer addition wraps at the boundary.
    Ok(maximum.map_or(1, |value| value.wrapping_add(1)))
}

async fn insert(
    conn: &mut MySqlConnection,
    parent: Option<&str>,
    key: Option<&str>,
    value: Option<&str>,
    sort: Option<i32>,
) -> Result<Term, Error> {
    let id = uuid::Uuid::new_v4().to_string();
    sqlx::query(
        "INSERT INTO t_military_term_data (id,parent_id,`key`,value,sort) VALUES (?,?,?,?,?)",
    )
    .bind(&id)
    .bind(parent)
    .bind(key)
    .bind(value)
    .bind(sort)
    .execute(conn)
    .await?;
    Ok(Term {
        id: Some(id),
        parent_id: parent.map(str::to_owned),
        key: key.map(str::to_owned),
        value: value.map(str::to_owned),
        sort,
        child: None,
    })
}

pub(super) async fn save(
    State(state): State<AppState>,
    body: Result<Json<SaveBody>, JsonRejection>,
) -> Response {
    let Ok(Json(body)) = body else {
        return failure(Error::Invalid("请求参数错误"));
    };
    let Some(pool) = state.pool.as_ref() else {
        return failure(Error::System);
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return failure(error.into()),
    };
    let result = async {
        // The Java save contract intentionally compares the incoming key to stored values.
        let duplicate = sqlx::query("SELECT id FROM t_military_term_data WHERE value=? LIMIT 1")
            .bind(&body.key)
            .fetch_optional(&mut *tx)
            .await?;
        if duplicate.is_some() {
            // NIPException is handled by Java's global HTTP 500 mapper.
            return Err(Error::System);
        }
        let parent = if body.kind == Some(0) {
            Some("0")
        } else {
            body.parent_id.as_deref()
        };
        let sort = next_sort(&mut tx, parent).await?;
        let saved = insert(
            &mut tx,
            parent,
            body.key.as_deref(),
            body.value.as_deref(),
            Some(sort),
        )
        .await?;
        Ok(Some(json!(saved)))
    }
    .await;
    finish(tx, result).await
}

pub(super) async fn delete(
    State(state): State<AppState>,
    body: Result<Json<Term>, JsonRejection>,
) -> Response {
    let Ok(Json(body)) = body else {
        return failure(Error::Invalid("未查询到该军语"));
    };
    let Some(id) = body.id.as_deref() else {
        return failure(Error::Invalid("未查询到该军语"));
    };
    let Some(pool) = state.pool.as_ref() else {
        return failure(Error::System);
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return failure(error.into()),
    };
    let result = async {
        let row =
            sqlx::query("SELECT parent_id,sort FROM t_military_term_data WHERE id=? FOR UPDATE")
                .bind(id)
                .fetch_optional(&mut *tx)
                .await?
                .ok_or(Error::Invalid("未查询到该军语"))?;
        let parent: Option<String> = row.try_get("parent_id")?;
        let sort: Option<i32> = row.try_get("sort")?;
        if sqlx::query("SELECT id FROM t_military_term_data WHERE parent_id=? LIMIT 1")
            .bind(id)
            .fetch_optional(&mut *tx)
            .await?
            .is_some()
        {
            return Err(Error::Invalid("存在子级军语不能删除"));
        }
        lock_parent(&mut tx, parent.as_deref()).await?;
        let siblings = sqlx::query_scalar::<_, String>(
            "SELECT id FROM t_military_term_data WHERE parent_id=? AND sort>?",
        )
        .bind(&parent)
        .bind(sort)
        .fetch_all(&mut *tx)
        .await?;
        shift_sorts(&mut tx, &siblings, -1).await?;
        sqlx::query("DELETE FROM t_military_term_data WHERE id=?")
            .bind(id)
            .execute(&mut *tx)
            .await?;
        Ok(None)
    }
    .await;
    finish(tx, result).await
}

pub(super) async fn update(
    State(state): State<AppState>,
    body: Result<Json<Term>, JsonRejection>,
) -> Response {
    let Ok(Json(body)) = body else {
        return failure(Error::Invalid("请求参数错误"));
    };
    let Some(pool) = state.pool.as_ref() else {
        return failure(Error::System);
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return failure(error.into()),
    };
    let result = async {
        let duplicate = sqlx::query("SELECT id FROM t_military_term_data WHERE value=? LIMIT 1")
            .bind(&body.value)
            .fetch_optional(&mut *tx)
            .await?;
        if let Some(duplicate) = duplicate {
            let id: String = duplicate.try_get("id")?;
            if body.id.as_deref() != Some(id.as_str()) {
                return Err(Error::System);
            }
        }
        if let Some(id) = body.id.as_deref().filter(|id| !id.is_empty()) {
            let existing = sqlx::query("SELECT id FROM t_military_term_data WHERE id=? FOR UPDATE")
                .bind(id)
                .fetch_optional(&mut *tx)
                .await?;
            if existing.is_none() {
                // A missing generated UUID is a stale Hibernate merge, not an insert.
                return Err(Error::System);
            }
            sqlx::query(
                "UPDATE t_military_term_data SET parent_id=?,`key`=?,value=?,sort=? WHERE id=?",
            )
            .bind(&body.parent_id)
            .bind(&body.key)
            .bind(&body.value)
            .bind(body.sort)
            .bind(id)
            .execute(&mut *tx)
            .await?;
        } else {
            insert(
                &mut tx,
                body.parent_id.as_deref(),
                body.key.as_deref(),
                body.value.as_deref(),
                body.sort,
            )
            .await?;
        }
        // Java returns the submitted VO, including child and its default sort of zero.
        Ok(Some(json!(body)))
    }
    .await;
    finish(tx, result).await
}

pub(super) async fn move_term(
    State(state): State<AppState>,
    body: Result<Json<MoveBody>, JsonRejection>,
) -> Response {
    let Ok(Json(body)) = body else {
        return failure(Error::Invalid("请求参数错误"));
    };
    let Some(pool) = state.pool.as_ref() else {
        return failure(Error::System);
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return failure(error.into()),
    };
    let result = async {
        let source =
            sqlx::query("SELECT parent_id,sort FROM t_military_term_data WHERE id=? FOR UPDATE")
                .bind(&body.source_id)
                .fetch_optional(&mut *tx)
                .await?
                .ok_or(Error::Invalid("未查询到移动源词条"))?;
        let target =
            sqlx::query("SELECT parent_id,sort FROM t_military_term_data WHERE id=? FOR UPDATE")
                .bind(&body.target_id)
                .fetch_optional(&mut *tx)
                .await?
                .ok_or(Error::Invalid("未查询到移动目标词条"))?;
        let parent: Option<String> = source.try_get("parent_id")?;
        if parent != target.try_get::<Option<String>, _>("parent_id")? {
            return Err(Error::Invalid("仅支持同级词条间移动"));
        }
        let source_sort = source
            .try_get::<Option<i32>, _>("sort")?
            .ok_or(Error::Invalid("词条排序缺失，无法移动"))?;
        let target_sort = target
            .try_get::<Option<i32>, _>("sort")?
            .ok_or(Error::Invalid("词条排序缺失，无法移动"))?;
        lock_parent(&mut tx, parent.as_deref()).await?;
        if source_sort > target_sort {
            let ids = sqlx::query_scalar::<_, String>(
                "SELECT id FROM t_military_term_data WHERE parent_id=? AND sort>=? AND sort<?",
            )
            .bind(&parent)
            .bind(target_sort)
            .bind(source_sort)
            .fetch_all(&mut *tx)
            .await?;
            shift_sorts(&mut tx, &ids, 1).await?;
        } else {
            let ids = sqlx::query_scalar::<_, String>(
                "SELECT id FROM t_military_term_data WHERE parent_id=? AND sort>? AND sort<=?",
            )
            .bind(&parent)
            .bind(source_sort)
            .bind(target_sort)
            .fetch_all(&mut *tx)
            .await?;
            shift_sorts(&mut tx, &ids, -1).await?;
        }
        sqlx::query("UPDATE t_military_term_data SET sort=? WHERE id=?")
            .bind(target_sort)
            .bind(&body.source_id)
            .execute(&mut *tx)
            .await?;
        Ok(None)
    }
    .await;
    finish(tx, result).await
}

pub(super) async fn save_batch(
    State(state): State<AppState>,
    body: Result<Json<Vec<Option<BatchRow>>>, JsonRejection>,
) -> Response {
    let Ok(Json(rows)) = body else {
        return failure(Error::Invalid("导入数据为空或格式不完整"));
    };
    if rows.is_empty() {
        return failure(Error::Invalid("导入数据为空或格式不完整"));
    }
    let Some(pool) = state.pool.as_ref() else {
        return failure(Error::System);
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return failure(error.into()),
    };
    let result = async {
        for row in rows {
            let row = row.ok_or(Error::System)?;
            let parent = sqlx::query(
                "SELECT id FROM t_military_term_data WHERE parent_id='0' AND `key`=? LIMIT 1",
            )
            .bind(&row.parent_name)
            .fetch_optional(&mut *tx)
            .await?;
            let Some(parent) = parent else {
                let sort = next_sort(&mut tx, Some("0")).await?;
                let parent = insert(
                    &mut tx,
                    Some("0"),
                    row.parent_name.as_deref(),
                    None,
                    Some(sort),
                )
                .await?;
                insert(
                    &mut tx,
                    parent.id.as_deref(),
                    row.child_name.as_deref(),
                    row.content.as_deref(),
                    Some(0),
                )
                .await?;
                continue;
            };
            let parent_id: String = parent.try_get("id")?;
            let child = sqlx::query(
                "SELECT id FROM t_military_term_data WHERE parent_id=? AND `key`=? LIMIT 1",
            )
            .bind(&parent_id)
            .bind(&row.child_name)
            .fetch_optional(&mut *tx)
            .await?;
            if let Some(child) = child {
                let child_id: String = child.try_get("id")?;
                sqlx::query("UPDATE t_military_term_data SET value=? WHERE id=?")
                    .bind(&row.content)
                    .bind(child_id)
                    .execute(&mut *tx)
                    .await?;
            } else {
                let sort = next_sort(&mut tx, Some(&parent_id)).await?;
                insert(
                    &mut tx,
                    Some(&parent_id),
                    row.child_name.as_deref(),
                    row.content.as_deref(),
                    Some(sort),
                )
                .await?;
            }
        }
        Ok(Some(tree(&mut tx).await?))
    }
    .await;
    finish(tx, result).await
}

pub(super) async fn save_all(body: String) -> Response {
    // Java serializes its String argument rather than parsing it, so this endpoint
    // fails before any writes. Preserve that contract instead of inventing an importer.
    let primitive = json!(body).to_string();
    let message = format!("Not a JSON Object: {primitive}");
    let mut end = message.len();
    let mut units = 0;
    for (index, ch) in message.char_indices() {
        units += ch.len_utf16();
        if units > 200 {
            end = index;
            break;
        }
    }
    let message = if end < message.len() {
        format!("{}…", &message[..end])
    } else {
        message
    };
    envelope_response(StatusCode::OK, 202, &message, &message)
}
