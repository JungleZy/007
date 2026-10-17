use axum::{extract::Multipart, http::StatusCode, response::Response};
use serde_json::json;

use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;

const MAX_FILE_BYTES: usize = 10 * 1024 * 1024;

pub(super) async fn upload(mut multipart: Multipart) -> Response {
    while let Ok(Some(field)) = multipart.next_field().await {
        if field.name() != Some("file") {
            continue;
        }
        let file_name = field.file_name().unwrap_or("").to_ascii_lowercase();
        let allowed = [".txt", ".md", ".csv"]
            .iter()
            .any(|suffix| file_name.ends_with(suffix));
        if !allowed {
            return envelope_response(
                StatusCode::OK,
                202,
                "仅支持纯文本文档（txt/md/csv）；Word/PPT 请由前端解析后提交内容",
                "",
            );
        }
        let bytes = match field.bytes().await {
            Ok(bytes) => bytes,
            Err(_) => return envelope_response(StatusCode::OK, 202, "上传文件读取失败", ""),
        };
        if bytes.is_empty() {
            return envelope_response(StatusCode::OK, 202, "文档内容为空", "");
        }
        if bytes.len() > MAX_FILE_BYTES {
            return envelope_response(StatusCode::OK, 202, "上传文件不能超过10MiB", "");
        }
        let content = match String::from_utf8(bytes.to_vec()) {
            Ok(content) if !content.trim().is_empty() => content,
            _ => {
                return envelope_response(
                    StatusCode::OK,
                    202,
                    "文档不是 UTF-8 纯文本，无法读取",
                    "",
                )
            }
        };
        return envelope_json(
            StatusCode::OK,
            200,
            Some(json!({"type":2,"wordContent":content,"imgUrls":[]})),
            "ok",
            "",
        );
    }
    envelope_response(StatusCode::OK, 202, "未收到上传文件", "")
}
