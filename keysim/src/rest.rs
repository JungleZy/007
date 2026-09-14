//! REST 驱动：把载荷打到真实后端，走完 取页 → startTrain → uploadResult → finish。
//!
//! 后端响应恒为 HTTP 200，业务状态在 JSON code 字段（AGENTS.md 运行时关键事实）；
//! 鉴权只从请求头读 token + deviceId。端点取自前端 api 模块
//! （handkeyZuXun.js、electronKeyZuXun.js），不另造路径。
//!
//! 自带极简 HTTP/1.1 客户端：只发一次请求、只读一次响应，connection: close。

use std::io::{Read, Write};
use std::net::TcpStream;
use std::time::Duration;

use serde_json::{json, Value};

pub const DEFAULT_BASE: &str = "http://localhost:18001/api";

pub struct Session {
    pub base: String,
    pub token: String,
    pub device_id: String,
}

fn split_base(base: &str) -> Result<(String, String), String> {
    let rest = base.strip_prefix("http://").ok_or("只支持 http:// 的后端地址")?;
    let (host, path) = match rest.split_once('/') {
        Some((host, path)) => (host.to_string(), format!("/{path}")),
        None => (rest.to_string(), String::new()),
    };
    let host = if host.contains(':') { host } else { format!("{host}:80") };
    Ok((host, path))
}

/// 发一次 JSON 请求，返回响应体解析出的信封
pub fn request(base: &str, path: &str, method: &str, body: Option<&Value>, headers: &[(&str, &str)]) -> Result<Value, String> {
    let (host, prefix) = split_base(base)?;
    let target = format!("{prefix}{path}");
    let mut stream = TcpStream::connect(&host).map_err(|error| format!("连接后端 {host} 失败：{error}"))?;
    stream.set_read_timeout(Some(Duration::from_secs(30))).ok();
    let payload = body.map(|value| value.to_string()).unwrap_or_default();
    let mut request = format!(
        "{method} {target} HTTP/1.1\r\nHost: {host}\r\ncontent-type: application/json\r\ncontent-length: {}\r\nconnection: close\r\n",
        payload.len()
    );
    for (name, value) in headers {
        request.push_str(&format!("{name}: {value}\r\n"));
    }
    request.push_str("\r\n");
    request.push_str(&payload);
    stream.write_all(request.as_bytes()).map_err(|error| error.to_string())?;

    let mut response = Vec::new();
    stream.read_to_end(&mut response).map_err(|error| error.to_string())?;
    let text = String::from_utf8_lossy(&response);
    let body = text
        .split_once("\r\n\r\n")
        .map(|(_, body)| body.to_string())
        .ok_or("后端响应不完整")?;
    // 后端固定返回 content-length，不走 chunked
    serde_json::from_str(body.trim())
        .map_err(|error| format!("{path} 响应不是 JSON：{error}；原文 {}", body.chars().take(200).collect::<String>()))
}

impl Session {
    /// 登录取会话；deviceId 由客户端生成（后端只校验 token+deviceId 配对）
    pub fn login(base: &str, account: &str, password: &str) -> Result<Session, String> {
        let device_id = format!("keysim-{}", std::process::id());
        let envelope = request(
            base,
            "/user/login",
            "POST",
            Some(&json!({"userAccount": account, "password": password, "deviceId": device_id})),
            &[],
        )?;
        if envelope["code"].as_i64() != Some(200) {
            return Err(format!("/user/login 返回业务码 {}：{}", envelope["code"], envelope["message"]));
        }
        Ok(Session {
            base: base.to_string(),
            token: envelope["data"]["token"].as_str().unwrap_or_default().to_string(),
            device_id: envelope["data"]["deviceId"].as_str().unwrap_or(&device_id).to_string(),
        })
    }

    pub fn call(&self, path: &str, method: &str, body: Option<&Value>) -> Result<Value, String> {
        let envelope = request(
            &self.base,
            path,
            method,
            body,
            &[("token", &self.token), ("deviceId", &self.device_id)],
        )?;
        match envelope["code"].as_i64() {
            Some(200) => Ok(envelope),
            _ => Err(format!("{path} 返回业务码 {}：{}", envelope["code"], envelope["message"])),
        }
    }
}

/// 组训端点表：手键走 generalTickerPatTrain，电子键走 generalKeyPat
pub struct Endpoints {
    pub detail: &'static str,
    pub page: &'static str,
    pub start: &'static str,
    pub upload: &'static str,
    pub finish: &'static str,
    pub statistics: &'static str,
}

pub fn endpoints(key: &str) -> Endpoints {
    if key == "hand" {
        Endpoints {
            detail: "/generalTickerPatTrain/detail",
            page: "/generalTickerPatTrain/findPage",
            start: "/generalTickerPatTrain/startTrain",
            upload: "/generalTickerPatTrain/uploadResult",
            finish: "/generalTickerPatTrain/finish",
            statistics: "/generalTickerPatTrain/statistics",
        }
    } else {
        Endpoints {
            detail: "/generalKeyPat/detail",
            page: "/generalKeyPat/getPage",
            start: "/generalKeyPat/startTrain",
            upload: "/generalKeyPat/uploadResult",
            finish: "/generalKeyPat/finish",
            statistics: "/generalKeyPat/statistics",
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    /// 后端地址必须是 http://host[:port][/prefix]，解析结果直接决定请求行
    fn base_is_split_into_host_and_prefix() {
        assert_eq!(split_base("http://localhost:18001/api").unwrap(), ("localhost:18001".into(), "/api".into()));
        assert_eq!(split_base("http://127.0.0.1/api/v2").unwrap(), ("127.0.0.1:80".into(), "/api/v2".into()));
        assert!(split_base("https://x/api").is_err(), "https 未实现就必须报错");
    }

    #[test]
    /// 两种键型的端点前缀不能混（跨栈契约，改名即断前端）
    fn endpoints_match_frontend_api_modules() {
        assert!(endpoints("hand").upload.starts_with("/generalTickerPatTrain/"));
        assert!(endpoints("electron").upload.starts_with("/generalKeyPat/"));
        assert_eq!(endpoints("hand").page, "/generalTickerPatTrain/findPage");
        assert_eq!(endpoints("electron").page, "/generalKeyPat/getPage");
    }

    #[test]
    /// 后端不可达时报错要点明地址，便于定位
    fn unreachable_backend_names_the_address() {
        let error = request("http://127.0.0.1:1/api", "/user/login", "POST", None, &[]).expect_err("端口 1 必须连不上");
        assert!(error.contains("127.0.0.1:1"), "{error}");
    }
}
