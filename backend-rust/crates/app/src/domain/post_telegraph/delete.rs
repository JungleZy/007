use super::dto::{business, err, DeleteQuery};
use super::settle::delete_child_rows;
use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::state::AppState;
use axum::extract::Extension;
use axum::extract::Query;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::Response;
use serde_json::json;
use sqlx::Row;

pub(super) async fn delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<DeleteQuery>,
) -> Response {
    let Some(id) = query.train_id.filter(|value| !value.is_empty()) else {
        return business(202, "训练ID不能为空");
    };
    let Some(pool) = state.pool.as_ref() else {
        return business(500, "服务器错误");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return err(e, "delete-begin"),
    };
    let row = match sqlx::query(
        "SELECT id,create_user_id FROM t_post_telegraph_key_pat_train WHERE id=? LIMIT 1 FOR UPDATE",
    )
    .bind(&id)
    .fetch_optional(&mut *tx)
    .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return business(202, "未查询到训练"),
        Err(e) => return err(e, "delete-select"),
    };
    let id = row.get::<String, _>("id");
    if row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return business(207, "无权限");
    }
    for table in [
        "t_post_telegraph_key_pat_train_raw_page",
        "t_post_telegraph_key_pat_train_page_value",
        "t_post_telegraph_key_pat_train_more",
        "t_post_telegraph_key_pat_train_page",
    ] {
        if let Err(e) = delete_child_rows(&mut tx, table, &id).await {
            return err(e, "delete-child");
        }
    }
    if let Err(e) =
        sqlx::query("DELETE FROM t_post_telegraph_key_pat_train WHERE id=? AND create_user_id=?")
            .bind(&id)
            .bind(&user.id)
            .execute(&mut *tx)
            .await
    {
        return err(e, "delete-parent");
    }
    if let Err(e) = tx.commit().await {
        return err(e, "delete-commit");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", "")
}
