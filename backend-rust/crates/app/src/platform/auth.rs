use axum::{
    extract::{Request, State},
    http::StatusCode,
    middleware::Next,
    response::Response,
};

use sha2::{Digest, Sha256};
use sqlx::Row;
use tracing::warn;

use crate::platform::envelope::envelope_response;
use crate::platform::extract::header_text;
use crate::platform::state::AppState;

#[derive(Clone, Debug)]
pub(crate) struct AuthUser {
    pub(crate) id: String,
    pub(crate) is_admin: bool,
}

pub(crate) async fn authenticate(
    State(state): State<AppState>,
    mut request: Request,
    next: Next,
) -> Response {
    let token = match header_text(&request, "token") {
        Some(value) => value,
        None => return envelope_response(StatusCode::OK, 203, "token不能为空", ""),
    };
    let device_id = match header_text(&request, "deviceId") {
        Some(value) => value,
        None => return envelope_response(StatusCode::OK, 204, "设备标识不能为空", ""),
    };
    match lookup_user(&state, token, device_id).await {
        Ok(Some(user)) => {
            request.extensions_mut().insert(user);
            next.run(request).await
        }
        Ok(None) => envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(error) => {
            warn!(error = %error, "authentication dependency failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(crate) async fn require_admin(request: Request, next: Next) -> Response {
    let Some(user) = request.extensions().get::<AuthUser>() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    if !user.is_admin {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    next.run(request).await
}

pub(crate) async fn lookup_user(
    state: &AppState,
    token: &str,
    device_id: &str,
) -> Result<Option<AuthUser>, sqlx::Error> {
    let Some(pool) = &state.pool else {
        return Ok(None);
    };
    let digest = token_digest(token);
    let row = sqlx::query(
        "SELECT u.id, EXISTS(SELECT 1 FROM t_user_role ur JOIN t_role r ON r.id = ur.role_id WHERE ur.user_id = u.id AND r.is_admin = 0) AS is_admin FROM t_user u WHERE u.token = ? AND u.device_id = ? LIMIT 1",
    )
    .bind(digest)
    .bind(device_id)
    .fetch_optional(pool)
    .await?;
    Ok(row.map(|row| AuthUser {
        id: row.get::<String, _>("id"),
        is_admin: row.get::<i64, _>("is_admin") != 0,
    }))
}

pub(crate) fn token_digest(token: &str) -> String {
    let mut digest = Sha256::new();
    digest.update(token.as_bytes());
    format!("{:x}", digest.finalize())
}
