//! 一键打开被测页面：keysim 自己拉起 Chromium，并在**页面脚本执行之前**
//! 把 navigator.serial 换成虚拟串口（CDP 的 Page.addScriptToEvaluateOnNewDocument）。
//!
//! 这样测试不需要去页面控制台粘任何东西，页面跑的仍是生产代码。
//! 只用 CDP 裸协议（ws.rs 的客户端帧），不引第三方浏览器驱动。

use std::io::{BufRead, BufReader, Read, Write};
use std::net::TcpStream;
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::time::{Duration, Instant};

use serde_json::{json, Value};

use crate::ws;

const CANDIDATES: [&str; 6] = [
    "google-chrome",
    "google-chrome-stable",
    "chromium",
    "chromium-browser",
    "microsoft-edge",
    "/usr/bin/chromium",
];

/// 找浏览器；KEYSIM_BROWSER 可显式指定
pub fn probe() -> Result<String, String> {
    if let Ok(path) = std::env::var("KEYSIM_BROWSER") {
        if !path.is_empty() {
            return Ok(path);
        }
    }
    for candidate in CANDIDATES {
        let found = Command::new("sh")
            .args(["-c", &format!("command -v {candidate}")])
            .output()
            .ok()
            .filter(|output| output.status.success())
            .map(|output| String::from_utf8_lossy(&output.stdout).trim().to_string());
        if let Some(path) = found.filter(|path| !path.is_empty()) {
            return Ok(path);
        }
    }
    Err("未找到 Chromium/Chrome；装一个或用 KEYSIM_BROWSER 指定可执行文件".into())
}

/// 浏览器 profile 固定在一个目录里，**不随进程退出删除**。
///
/// 之前用 `temp_dir()/keysim-profile-<pid>` 并在关闭时删掉，于是每次「打开并预置
/// 虚拟串口」都是全新 profile：被测系统要登录，就得重新登录一次。固定下来之后
/// 登录态、localStorage、已授权的串口都留在里面，第二次打开直接可用。
pub fn profile_dir() -> PathBuf {
    match std::env::var("HOME") {
        Ok(home) if !home.is_empty() => PathBuf::from(home).join(".keysim").join("browser-profile"),
        _ => std::env::temp_dir().join("keysim-browser-profile"),
    }
}

#[derive(Debug)]
pub struct Browser {
    pub url: String,
    pub headless: bool,
    child: Child,
}

impl Browser {
    /// 只关浏览器进程，profile 留着——下次打开还是同一个登录态
    pub fn close(mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

struct Cdp {
    stream: TcpStream,
    parser: ws::Parser,
    next_id: u64,
}

impl Cdp {
    fn connect(endpoint: &str) -> Result<Cdp, String> {
        // ws://127.0.0.1:PORT/devtools/browser/UUID
        let rest = endpoint.strip_prefix("ws://").ok_or("CDP 地址不是 ws://")?;
        let (host, path) = rest.split_once('/').ok_or("CDP 地址缺少路径")?;
        let mut stream = TcpStream::connect(host).map_err(|error| format!("连接 CDP 失败：{error}"))?;
        stream.set_nodelay(true).ok();
        let key = ws::base64(&Instant::now().elapsed().as_nanos().to_be_bytes()[..16.min(16)]);
        let request = format!(
            "GET /{path} HTTP/1.1\r\nHost: {host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n"
        );
        stream.write_all(request.as_bytes()).map_err(|error| error.to_string())?;
        let head = ws::read_headers(&mut stream).map_err(|error| format!("CDP 握手失败：{error}"))?;
        if !head.starts_with("HTTP/1.1 101") {
            return Err(format!("CDP 拒绝握手：{}", head.lines().next().unwrap_or_default()));
        }
        Ok(Cdp { stream, parser: ws::Parser::default(), next_id: 1 })
    }

    fn call(&mut self, method: &str, params: Value, session: Option<&str>) -> Result<Value, String> {
        let id = self.next_id;
        self.next_id += 1;
        let mut message = json!({"id": id, "method": method, "params": params});
        if let Some(session) = session {
            message["sessionId"] = json!(session);
        }
        let mask = (id as u32).to_be_bytes();
        let frame = ws::encode_masked(ws::OP_TEXT, message.to_string().as_bytes(), mask);
        self.stream.write_all(&frame).map_err(|error| error.to_string())?;

        let deadline = Instant::now() + Duration::from_secs(20);
        let mut buffer = [0u8; 8192];
        self.stream.set_read_timeout(Some(Duration::from_millis(500))).ok();
        while Instant::now() < deadline {
            let count = match self.stream.read(&mut buffer) {
                Ok(0) => return Err("CDP 连接已关闭".into()),
                Ok(count) => count,
                Err(error) if error.kind() == std::io::ErrorKind::WouldBlock || error.kind() == std::io::ErrorKind::TimedOut => continue,
                Err(error) => return Err(error.to_string()),
            };
            for incoming in self.parser.push(&buffer[..count]) {
                if incoming.opcode != ws::OP_TEXT {
                    continue;
                }
                let Ok(value): Result<Value, _> = serde_json::from_slice(&incoming.payload) else { continue };
                if value["id"].as_u64() != Some(id) {
                    continue;
                }
                if let Some(error) = value["error"].as_object() {
                    return Err(format!("{method} 失败：{}", error.get("message").and_then(Value::as_str).unwrap_or("未知错误")));
                }
                return Ok(value["result"].clone());
            }
        }
        Err(format!("{method} 超时"))
    }
}

fn devtools_endpoint(child: &mut Child) -> Result<String, String> {
    let stderr = child.stderr.take().ok_or("拿不到浏览器 stderr")?;
    let mut reader = BufReader::new(stderr);
    let deadline = Instant::now() + Duration::from_secs(30);
    let mut line = String::new();
    while Instant::now() < deadline {
        line.clear();
        match reader.read_line(&mut line) {
            Ok(0) => break,
            Ok(_) => {
                if let Some(index) = line.find("ws://") {
                    return Ok(line[index..].trim().to_string());
                }
            }
            Err(error) => return Err(error.to_string()),
        }
    }
    Err("浏览器没有报出 DevTools 地址".into())
}

/// 打开页面并预置虚拟串口；无图形会话时自动 headless
pub fn open(url: &str, inject_script: &str) -> Result<Browser, String> {
    launch(&probe()?, url, inject_script)
}

/// 指定浏览器可执行文件的版本：测试与显式指定场景用，不碰进程环境变量
pub fn launch(executable: &str, url: &str, inject_script: &str) -> Result<Browser, String> {
    let headless = std::env::var("DISPLAY").is_err() && std::env::var("WAYLAND_DISPLAY").is_err();
    let profile = profile_dir();
    let _ = std::fs::create_dir_all(&profile);

    let mut command = Command::new(executable);
    command
        .arg("--remote-debugging-port=0")
        .arg(format!("--user-data-dir={}", profile.display()))
        .arg("--no-first-run")
        .arg("--no-default-browser-check")
        .arg("--disable-features=Translate,MediaRouter")
        .arg("--disable-background-networking")
        // 被测页面常挂在自签或 http 源上，别让证书/混合内容挡住自动化
        .arg("--ignore-certificate-errors")
        .arg("--allow-running-insecure-content")
        .stdout(Stdio::null())
        .stderr(Stdio::piped());
    if headless {
        command.arg("--headless=new");
    }
    command.arg("about:blank");

    let mut child = command.spawn().map_err(|error| format!("启动浏览器失败：{error}"))?;
    let endpoint = match devtools_endpoint(&mut child) {
        Ok(endpoint) => endpoint,
        Err(error) => {
            let _ = child.kill();
            return Err(error);
        }
    };

    let result = (|| -> Result<(), String> {
        let mut cdp = Cdp::connect(&endpoint)?;
        let target = cdp.call("Target.createTarget", json!({"url": "about:blank"}), None)?;
        let target_id = target["targetId"].as_str().ok_or("没拿到 targetId")?.to_string();
        let attached = cdp.call("Target.attachToTarget", json!({"targetId": target_id, "flatten": true}), None)?;
        let session = attached["sessionId"].as_str().ok_or("没拿到 sessionId")?.to_string();
        cdp.call("Page.enable", json!({}), Some(&session))?;
        cdp.call(
            "Page.addScriptToEvaluateOnNewDocument",
            json!({"source": inject_script}),
            Some(&session),
        )?;
        cdp.call("Page.navigate", json!({"url": url}), Some(&session))?;
        Ok(())
    })();

    match result {
        Ok(()) => Ok(Browser { url: url.to_string(), headless, child }),
        Err(error) => {
            let _ = child.kill();
            Err(error)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    /// 没有浏览器时必须说清怎么办，而不是静默失败
    fn probe_reports_how_to_fix() {
        match probe() {
            Ok(path) => assert!(!path.is_empty()),
            Err(reason) => assert!(reason.contains("KEYSIM_BROWSER"), "{reason}"),
        }
    }

    #[test]
    /// profile 必须稳定且与进程无关：带 pid 或放在临时目录会让登录态每次丢失
    fn profile_is_stable_and_not_per_process() {
        let first = profile_dir();
        let second = profile_dir();
        assert_eq!(first, second, "两次取到的 profile 目录必须一致");
        let text = first.display().to_string();
        assert!(!text.contains(&std::process::id().to_string()), "profile 路径不能含 pid：{text}");
        assert!(text.contains("keysim"), "profile 应落在 keysim 自己的目录下：{text}");
    }

    #[test]
    /// 指定不存在的可执行文件时，open 必须报错而不是挂住
    fn open_with_bad_executable_fails_fast() {
        let error = launch("/nonexistent/keysim-browser", "http://127.0.0.1:1/", "1")
            .expect_err("不存在的浏览器必须报错");
        assert!(error.contains("启动浏览器失败"), "{error}");
    }
}
