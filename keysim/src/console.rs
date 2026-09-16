//! 控制台：内嵌网页 + HTTP API + SSE + 浏览器注入通道，全在一个进程里。
//!
//! 网页与注入脚本都编进二进制（include_str!），所以交付物就是一个可执行文件。

use std::io::{BufRead, BufReader, Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::Arc;
use std::thread;

use parking_lot::Mutex;
use serde_json::{json, Value};

use crate::browser;
use crate::faults::{self, Chunking, Fault};
use crate::keying::{self, ElectronOptions, HandOptions};
use crate::kernel;
#[cfg(unix)]
use crate::pty;
use crate::serial::{VirtualSerial, SYSTEM_LINK};
use crate::sinks;
use crate::timeline::Timeline;
use crate::ws;

const INDEX_HTML: &str = include_str!("../public/index.html");
const INJECT_JS: &str = include_str!("../public/inject.js");

pub struct Console {
    pub port: u16,
    pub serial: Arc<VirtualSerial>,
    events: Arc<Mutex<Vec<TcpStream>>>,
    browser: Mutex<Option<browser::Browser>>,
}

/// 读数取自**实际生成的时间轴**，不是名义参数：节拍会按实测码率口径反算，
/// 拿名义值显示会和客户端/服务端算出来的对不上。
fn plan_json(timeline: &Timeline) -> Value {
    let plan = timeline.plan;
    if timeline.key == "hand" {
        json!({"dot": plan.dot, "dash": plan.dash, "gap": plan.gap, "word": plan.word, "group": plan.group})
    } else {
        json!({"perChar": plan.word, "word": plan.word, "group": plan.group, "strokeGap": plan.gap})
    }
}

fn number(params: &Value, key: &str, fallback: f64) -> f64 {
    params[key].as_f64().filter(|value| value.is_finite()).unwrap_or(fallback)
}

fn text_of(params: &Value, key: &str, fallback: &str) -> String {
    params[key].as_str().unwrap_or(fallback).to_string()
}

fn hand_options(params: &Value) -> Result<HandOptions, String> {
    let defaults = HandOptions::default();
    Ok(HandOptions {
        text: text_of(params, "text", ""),
        alphabet: text_of(params, "alphabet", &defaults.alphabet),
        rate: number(params, "rate", defaults.rate),
        unit: text_of(params, "unit", &defaults.unit),
        skew: number(params, "skew", defaults.skew),
        jitter: number(params, "jitter", 0.0),
        seed: number(params, "seed", 1.0) as u32,
        preamble: params["preamble"].as_bool().unwrap_or(true),
        tail: text_of(params, "tail", &defaults.tail),
        low_rate: params["lowRate"].as_bool().unwrap_or(false),
        style: text_of(params, "style", &defaults.style),
    })
}

fn electron_options(params: &Value) -> Result<ElectronOptions, String> {
    let defaults = ElectronOptions::default();
    Ok(ElectronOptions {
        text: text_of(params, "text", ""),
        alphabet: text_of(params, "alphabet", &defaults.alphabet),
        rate: number(params, "rate", defaults.rate),
        stroke_gap: number(params, "strokeGap", defaults.stroke_gap),
        group_size: number(params, "groupSize", 4.0) as usize,
        jitter: number(params, "jitter", 0.0),
        seed: number(params, "seed", 1.0) as u32,
        preamble: params["preamble"].as_bool().unwrap_or(true),
        tail: text_of(params, "tail", &defaults.tail),
        style: text_of(params, "style", &defaults.style),
    })
}

fn parse_faults(params: &Value) -> Result<Vec<Fault>, String> {
    let mut parsed = Vec::new();
    for item in params["faults"].as_array().cloned().unwrap_or_default() {
        let name = item.as_str().unwrap_or_default();
        parsed.push(Fault::parse(name).ok_or_else(|| format!("未知故障 {name}（可选：{}）", faults::Fault::all().join(" / ")))?);
    }
    Ok(parsed)
}

/// 按请求参数造时间轴；故障注入在这里统一落地。
/// 返回每种故障实际注入的次数，供预览与日志如实呈现（勾了却是 0 次也要看得见）。
pub fn build_timeline_with_faults(params: &Value) -> Result<(Timeline, Vec<(faults::Fault, usize)>), String> {
    let mut timeline = if params["key"].as_str().unwrap_or("hand") == "hand" {
        keying::hand_timeline(&hand_options(params)?)?
    } else {
        keying::electron_timeline(&electron_options(params)?)?
    };
    let seed = number(params, "seed", 1.0) as u32;
    let report = faults::apply(&mut timeline, &parse_faults(params)?, seed);
    Ok((timeline, report))
}

pub fn build_timeline(params: &Value) -> Result<Timeline, String> {
    build_timeline_with_faults(params).map(|(timeline, _)| timeline)
}

impl Console {
    fn broadcast(&self, event: &Value) {
        sse_broadcast(&self.events, event);
    }

    fn state(&self) -> Value {
        let mut state = self.serial.state();
        let browser = self.browser.lock();
        state["browser"] = match browser.as_ref() {
            Some(session) => json!({
                "open": true,
                "url": session.url.clone(),
                "available": true,
                "profile": browser::profile_dir().display().to_string()
            }),
            None => match browser::probe() {
                Ok(path) => json!({
                    "open": false,
                    "available": true,
                    "executable": path,
                    "profile": browser::profile_dir().display().to_string()
                }),
                Err(reason) => json!({"open": false, "available": false, "reason": reason}),
            },
        };
        state
    }

    fn log(&self, level: &str, message: impl Into<String>) {
        self.broadcast(&json!({"type": "log", "level": level, "message": message.into()}));
    }

    fn handle(self: &Arc<Self>, path: &str, body: &Value) -> Value {
        match path {
            "/api/state" => json!({"ok": true, "state": self.state()}),
            "/api/port" => {
                if body["open"].as_bool().unwrap_or(true) {
                    match self.serial.open() {
                        Ok(_) => json!({"ok": true, "state": self.state()}),
                        Err(error) => json!({"ok": false, "error": error}),
                    }
                } else {
                    self.serial.close();
                    json!({"ok": true, "state": self.state()})
                }
            }
            "/api/message/random" => {
                let alphabet = text_of(body, "alphabet", "letter");
                let groups = number(body, "groups", 4.0) as usize;
                let size = number(body, "groupSize", 4.0) as usize;
                let seed = body["seed"].as_u64().map(|value| value as u32);
                match keying::random_message(&alphabet, groups, size, seed) {
                    Ok(text) => json!({"ok": true, "text": text}),
                    Err(error) => json!({"ok": false, "error": error}),
                }
            }
            "/api/preview" => match build_timeline_with_faults(body) {
                Ok((timeline, injected)) => {
                    let chunks = sinks::to_bytes(&timeline);
                    let head: Vec<String> = chunks.iter().take(12).map(|chunk| sinks::hex(&chunk.bytes)).collect();
                    // 纸带用：按下/抬起的时刻对，页面据此把点划画出来。
                    // 手键是 Down/Up 成对，电子键是单字节码（画成等宽刻点）。
                    // 纸带按 1ms=1px 等比绘制，长报文靠滚动看，所以上限放宽；
                    // 真被截断时告诉页面，让它画出截断标记而不是默默少画。
                    const MARK_LIMIT: usize = 40000;
                    let marks: Vec<Value> = timeline
                        .events
                        .iter()
                        .take(MARK_LIMIT)
                        .map(|event| {
                            let kind = match event.kind {
                                crate::timeline::Kind::Down => 0,
                                crate::timeline::Kind::Up => 1,
                                crate::timeline::Kind::Code(_) => 2,
                            };
                            json!([(event.at * 1000.0).round() / 1000.0, kind])
                        })
                        .collect();
                    // 码率一律按客户端/服务端的实测口径报（字符×60000/采集区间，组/分再除 4）
                    let per_unit = if timeline.key == "hand" { 1.0 } else { 4.0 };
                    json!({
                        "ok": true,
                        "duration": timeline.duration(),
                        "events": timeline.events.len(),
                        "chars": timeline.body_chars().count(),
                        "plan": plan_json(&timeline),
                        "rate": (keying::measured_rate(&timeline, per_unit) * 100.0).round() / 100.0,
                        "rateUnit": if timeline.key == "hand" { "字/分" } else { "组/分" },
                        "captureWindow": (keying::capture_window(&timeline) * 1000.0).round() / 1000.0,
                        "marks": marks,
                        "marksTruncated": timeline.events.len() > MARK_LIMIT,
                        "faults": injected
                            .iter()
                            .map(|(fault, count)| json!({"name": fault.label(), "count": count}))
                            .collect::<Vec<_>>(),
                        "head": head
                    })
                }
                Err(error) => json!({"ok": false, "error": error}),
            },
            "/api/send" => match build_timeline_with_faults(body) {
                Ok((timeline, injected)) => {
                    let speed = number(body, "speed", 1.0).max(0.05);
                    let text = text_of(body, "text", "");
                    if !injected.is_empty() {
                        self.log(
                            "warn",
                            format!(
                                "本次带故障注入：{}",
                                injected
                                    .iter()
                                    .map(|(fault, count)| format!("{} ×{count}", fault.label()))
                                    .collect::<Vec<_>>()
                                    .join(" · ")
                            ),
                        );
                    }
                    match self.serial.send(&timeline, speed, &text) {
                        Ok(state) => json!({"ok": true, "state": state}),
                        Err(error) => json!({"ok": false, "error": error}),
                    }
                }
                Err(error) => json!({"ok": false, "error": error}),
            },
            "/api/stop" => {
                let stopped = self.serial.stop();
                json!({"ok": true, "stopped": stopped, "state": self.state()})
            }
            "/api/device/link" => {
                let target = text_of(body, "target", SYSTEM_LINK);
                let mut result = if body["remove"].as_bool().unwrap_or(false) {
                    self.serial.unlink_system(&target)
                } else {
                    self.serial.link_system(&target)
                };
                result["state"] = self.state();
                result
            }
            "/api/helper/install" => {
                let mut result = crate::install::install_helper();
                if result["ok"] == true {
                    self.log("ok", "root 助手已安装：点「开启虚拟串口」即可得到 /dev/ttyACM*");
                } else if let Some(command) = result["command"].as_str() {
                    self.log("warn", format!("需要手动执行一次：{command}"));
                }
                result["state"] = self.state();
                result
            }
            "/api/browser/open" => {
                let url = text_of(body, "url", "");
                if url.is_empty() {
                    return json!({"ok": false, "error": "缺少页面地址"});
                }
                match self.open_page(&url) {
                    Ok(()) => json!({"ok": true, "state": self.state()}),
                    Err(error) => json!({"ok": false, "error": error}),
                }
            }
            "/api/browser/close" => {
                if let Some(session) = self.browser.lock().take() {
                    session.close();
                    self.log("warn", "被测页面已关闭");
                }
                json!({"ok": true, "state": self.state()})
            }
            other => json!({"ok": false, "error": format!("未知接口 {other}")}),
        }
    }
}

impl Console {
    /// 拉起浏览器打开被测页面，并在页面脚本执行前注入虚拟串口
    pub fn open_page(&self, url: &str) -> Result<(), String> {
        let session = browser::open(url, &inject_script(self.port))?;
        self.log("ok", format!("已打开被测页面并预置虚拟串口：{url}"));
        *self.browser.lock() = Some(session);
        Ok(())
    }
}

/// 注入脚本按实际端口定制 origin（脚本内是占位符，见 public/inject.js）
fn inject_script(port: u16) -> String {
    INJECT_JS.replace("__KEYSIM_ORIGIN__", &format!("http://127.0.0.1:{port}"))
}

/// SSE 广播：Console::broadcast 与 serve 里的 sink 闭包共用这一份
fn sse_broadcast(events: &Mutex<Vec<TcpStream>>, event: &Value) {
    let payload = format!("data: {event}\n\n");
    events.lock().retain_mut(|client| client.write_all(payload.as_bytes()).is_ok());
}

fn respond(stream: &mut TcpStream, status: &str, content_type: &str, body: &[u8]) {
    let head = format!(
        "HTTP/1.1 {status}\r\ncontent-type: {content_type}\r\ncontent-length: {}\r\naccess-control-allow-origin: *\r\nconnection: close\r\n\r\n",
        body.len()
    );
    let _ = stream.write_all(head.as_bytes());
    let _ = stream.write_all(body);
}

/// 控制台启动参数
pub struct Options {
    pub http: u16,
    /// 首屏即可用：起来就开虚拟串口
    pub autostart: bool,
    pub links: Vec<std::path::PathBuf>,
}

impl Default for Options {
    fn default() -> Self {
        Options {
            http: crate::CONSOLE_PORT,
            autostart: true,
            links: crate::default_device_links(),
        }
    }
}

/// 监听控制台端口。
///
/// 重启时旧进程可能还没完全退干净，直接 bind 会拿到 EADDRINUSE 然后整个进程退出
/// （被 systemd/hub 这类管理器重启时就表现为"起来就挂"）。所以对"地址被占用"重试几轮，
/// 其他错误立即上报。
fn listen(port: u16) -> std::io::Result<TcpListener> {
    let mut last = None;
    for attempt in 0..20 {
        match TcpListener::bind(("127.0.0.1", port)) {
            Ok(listener) => return Ok(listener),
            Err(error) if error.kind() == std::io::ErrorKind::AddrInUse => {
                if attempt == 0 {
                    eprintln!("[keysim] 端口 {port} 仍被占用，等待旧进程退出…");
                }
                last = Some(error);
                if attempt + 1 < 20 {
                    thread::sleep(std::time::Duration::from_millis(150));
                }
            }
            Err(error) => return Err(error),
        }
    }
    Err(last.unwrap_or_else(|| std::io::Error::new(std::io::ErrorKind::AddrInUse, "端口一直被占用")))
}

pub fn serve(options: Options) -> std::io::Result<Arc<Console>> {
    let listener = listen(options.http)?;
    let bound = listener.local_addr()?.port();
    let events: Arc<Mutex<Vec<TcpStream>>> = Arc::new(Mutex::new(Vec::new()));

    let sink_events = Arc::clone(&events);
    let serial = VirtualSerial::new(
        options.links.clone(),
        Arc::new(move |event: Value| {
            sse_broadcast(&sink_events, &event);
        }),
    );

    let console = Arc::new(Console {
        port: bound,
        serial: Arc::clone(&serial),
        events: Arc::clone(&events),
        browser: Mutex::new(None),
    });

    if options.autostart {
        if let Err(error) = serial.open() {
            eprintln!("[keysim] 自动开启虚拟串口失败：{error}");
        }
    }

    let accept_console = Arc::clone(&console);
    thread::spawn(move || {
        for incoming in listener.incoming() {
            let Ok(stream) = incoming else { continue };
            let console = Arc::clone(&accept_console);
            thread::spawn(move || serve_one(console, stream));
        }
    });
    Ok(console)
}

fn serve_one(console: Arc<Console>, mut stream: TcpStream) {
    let _ = stream.set_nodelay(true);
    let mut reader = BufReader::new(match stream.try_clone() {
        Ok(clone) => clone,
        Err(_) => return,
    });
    let mut request_line = String::new();
    if reader.read_line(&mut request_line).is_err() {
        return;
    }
    let mut parts = request_line.split_whitespace();
    let method = parts.next().unwrap_or("GET").to_string();
    let target = parts.next().unwrap_or("/").to_string();
    let path = target.split('?').next().unwrap_or("/").to_string();

    let mut headers = String::new();
    loop {
        let mut line = String::new();
        match reader.read_line(&mut line) {
            Ok(0) => break,
            Ok(_) => {
                if line == "\r\n" || line == "\n" {
                    break;
                }
                headers.push_str(&line);
            }
            Err(_) => return,
        }
    }
    let lowered = headers.to_ascii_lowercase();

    // WebSocket：浏览器注入通道
    if lowered.contains("upgrade: websocket") {
        let Some(key) = ws::header_value(&headers, "sec-websocket-key") else { return };
        if path != "/ws/serial" {
            let _ = stream.write_all(b"HTTP/1.1 404 Not Found\r\n\r\n");
            return;
        }
        if ws::write_handshake(&mut stream, &key).is_err() {
            return;
        }
        console.serial.attach_inject(stream);
        return;
    }

    if path == "/api/events" {
        let head = "HTTP/1.1 200 OK\r\ncontent-type: text/event-stream\r\ncache-control: no-cache\r\nconnection: keep-alive\r\naccess-control-allow-origin: *\r\n\r\n";
        if stream.write_all(head.as_bytes()).is_err() {
            return;
        }
        let initial = format!("data: {}\n\n", json!({"type": "state", "state": console.state()}));
        let _ = stream.write_all(initial.as_bytes());
        console.events.lock().push(stream);
        return;
    }

    let length: usize = ws::header_value(&headers, "content-length")
        .and_then(|value| value.parse().ok())
        .unwrap_or(0);
    // content-length 直接决定分配大小，不能无条件信任客户端声明的数字
    const MAX_BODY: usize = 16 * 1024 * 1024;
    if length > MAX_BODY {
        respond(&mut stream, "413 Payload Too Large", "text/plain; charset=utf-8", b"body too large");
        return;
    }
    let mut body = vec![0u8; length];
    if length > 0 && reader.read_exact(&mut body).is_err() {
        return;
    }

    match (method.as_str(), path.as_str()) {
        ("GET", "/") | ("GET", "/index.html") => respond(&mut stream, "200 OK", "text/html; charset=utf-8", INDEX_HTML.as_bytes()),
        ("GET", "/inject.js") => {
            let script = inject_script(console.port);
            respond(&mut stream, "200 OK", "application/javascript; charset=utf-8", script.as_bytes());
        }
        ("OPTIONS", _) => respond(&mut stream, "204 No Content", "text/plain", b""),
        (_, path) if path.starts_with("/api/") => {
            let parsed: Value = if body.is_empty() {
                json!({})
            } else {
                serde_json::from_slice(&body).unwrap_or_else(|_| json!({}))
            };
            let result = console.handle(path, &parsed);
            respond(&mut stream, "200 OK", "application/json; charset=utf-8", result.to_string().as_bytes());
        }
        _ => respond(&mut stream, "404 Not Found", "text/plain; charset=utf-8", b"not found"),
    }
}

/// doctor：把本机能力矩阵打成人读文本（不可用必须给原因与装法）
pub fn doctor() -> String {
    let mut lines = Vec::new();
    lines.push(format!("keysim doctor —— 虚拟串口能力矩阵（平台：{}）", crate::PLATFORM));
    lines.push(String::new());
    lines.push("| 后端 | 可用 | 被测程序应选 | 说明 |".into());
    lines.push("|---|---|---|---|".into());
    for verdict in kernel::probe_all() {
        lines.push(format!(
            "| {} | {} | {} | {} |",
            verdict.id,
            if verdict.available { "可用" } else { "不可用" },
            verdict.selectable,
            verdict
                .reason
                .map(|reason| match verdict.install {
                    Some(install) => format!("{reason}；装法：{install}"),
                    None => reason,
                })
                .unwrap_or_else(|| "就绪".into())
        ));
    }
    #[cfg(unix)]
    {
        let pty_state = match pty::probe() {
            Ok(()) => "可用 | /dev/pts/N | 任何按路径打开串口的程序可用；浏览器选择框看不到 PTY".to_string(),
            Err(reason) => format!("不可用 | /dev/pts/N | {reason}"),
        };
        lines.push(format!("| pty | {pty_state} |"));
    }
    #[cfg(not(unix))]
    lines.push("| pty | 不适用 | - | 本平台没有 PTY；com0com 已提供真实 COM 口 |".to_string());
    lines.push(format!(
        "| browser-inject | {} | 页面内虚拟 navigator.serial | 由 keysim 拉起浏览器并在页面脚本前注入 |",
        match browser::probe() {
            Ok(path) => format!("可用（{path}）"),
            Err(reason) => format!("不可用（{reason}）"),
        }
    ));
    lines.push(String::new());
    lines.push(format!(
        "root 助手：{}",
        if !crate::HELPER_REQUIRED {
            "本平台不需要".to_string()
        } else if crate::install::helper_installed() {
            "已安装".to_string()
        } else {
            format!("未安装 —— 执行 {}", crate::install::install_command())
        }
    ));
    lines.push(format!("故障注入：{}", Fault::all().join(" / ")));
    lines.push(format!(
        "分包模式：{}",
        ["exact", "split", "merge", "random"]
            .into_iter()
            .filter(|name| Chunking::parse(name).is_some())
            .collect::<Vec<_>>()
            .join(" / ")
    ));
    lines.join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    /// 内嵌资源必须真的编进二进制（交付物只有一个文件）
    fn assets_are_embedded() {
        assert!(INDEX_HTML.contains("keysim"), "网页没编进二进制");
        assert!(INJECT_JS.contains("navigator"), "注入脚本没编进二进制");
    }

    #[test]
    /// 未知接口不 panic，按错误返回
    fn unknown_api_is_rejected() {
        let console = serve(Options { http: 0, autostart: false, ..Options::default() }).expect("控制台应能起在随机端口");
        let result = console.handle("/api/nope", &json!({}));
        assert_eq!(result["ok"], false);
    }

    #[test]
    /// 故障名非法要报错并列出可选值，而不是静默忽略
    fn unknown_fault_is_reported() {
        let error = build_timeline(&json!({"key": "hand", "text": "ABCD", "faults": ["nope"]}))
            .expect_err("非法故障名必须报错");
        assert!(error.contains("nope") && error.contains("dupDown"), "{error}");
    }

    #[test]
    /// doctor 必须对每个后端都给结论
    fn doctor_covers_every_backend() {
        let report = doctor();
        if kernel::disabled_by_env() {
            return;
        }
        let expected: &[&str] = if cfg!(target_os = "linux") {
            &["linux-usbip", "linux-gadget", "linux-tty0tty", "pty"]
        } else {
            &["windows-com0com"]
        };
        for id in expected {
            assert!(report.contains(id), "doctor 少了 {id}：\n{report}");
        }
    }
}
