//! 端到端：起真控制台，用真 WebSocket 客户端分别冒充
//! ①桌面被测应用（连 18765 桥接）与 ②被注入的网页（连 /ws/serial），
//! 然后走 HTTP API 发一次真实拍发，断言两条通道收到的东西自洽。
//!
//! 这是"模拟器能不能真的驱动被测端"的证明，不是内部实现的镜子。

use std::io::{Read, Write};
use std::net::TcpStream;
use std::time::{Duration, Instant};

use serde_json::{json, Value};

use keysim::{console, ws};

fn http(port: u16, method: &str, path: &str, body: Option<&Value>) -> Value {
    let mut stream = TcpStream::connect(("127.0.0.1", port)).expect("控制台应可连接");
    // 没有读超时的话，handler 一旦回归成 keep-alive，测试不是超时失败而是永久挂死
    stream
        .set_read_timeout(Some(Duration::from_secs(10)))
        .expect("应能设置读超时");
    let payload = body.map(|value| value.to_string()).unwrap_or_default();
    let request = format!(
        "{method} {path} HTTP/1.1\r\nHost: 127.0.0.1\r\ncontent-type: application/json\r\ncontent-length: {}\r\nconnection: close\r\n\r\n{payload}",
        payload.len()
    );
    stream.write_all(request.as_bytes()).expect("请求应能发出");
    let mut response = String::new();
    stream.read_to_string(&mut response).unwrap_or_else(|error| panic!("{method} {path} 响应读取失败：{error}"));
    let (head, body) = response.split_once("\r\n\r\n").expect("响应不完整");
    assert!(head.starts_with("HTTP/1.1 2"), "{method} {path} 返回 {head}");
    serde_json::from_str(body.trim()).unwrap_or_else(|_| json!({"raw": body}))
}

fn http_text(port: u16, path: &str) -> String {
    let mut stream = TcpStream::connect(("127.0.0.1", port)).expect("控制台应可连接");
    stream
        .set_read_timeout(Some(Duration::from_secs(10)))
        .expect("应能设置读超时");
    let request = format!("GET {path} HTTP/1.1\r\nHost: 127.0.0.1\r\nconnection: close\r\n\r\n");
    stream.write_all(request.as_bytes()).expect("请求应能发出");
    let mut response = String::new();
    stream.read_to_string(&mut response).unwrap_or_else(|error| panic!("GET {path} 响应读取失败：{error}"));
    response.split_once("\r\n\r\n").map(|(_, body)| body.to_string()).unwrap_or_default()
}

/// 极简 WS 客户端：只用于测试里扮演被测端
struct Client {
    stream: TcpStream,
    parser: ws::Parser,
    inbox: Vec<ws::Message>,
}

impl Client {
    fn connect(port: u16, path: &str) -> Client {
        let mut stream = TcpStream::connect(("127.0.0.1", port)).expect("端口应在监听");
        stream.set_nodelay(true).ok();
        stream.set_read_timeout(Some(Duration::from_millis(100))).ok();
        let key = ws::base64(b"keysim-test-1234");
        let request = format!(
            "GET {path} HTTP/1.1\r\nHost: 127.0.0.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n"
        );
        stream.write_all(request.as_bytes()).expect("握手请求应能发出");
        let head = ws::read_headers(&mut stream).expect("应收到握手响应");
        assert!(head.starts_with("HTTP/1.1 101"), "{path} 握手失败：{head}");
        assert_eq!(
            ws::header_value(&head, "sec-websocket-accept").as_deref(),
            Some(ws::accept_key(&key).as_str()),
            "Sec-WebSocket-Accept 不符合 RFC6455"
        );
        Client { stream, parser: ws::Parser::default(), inbox: Vec::new() }
    }

    fn pump(&mut self) {
        let mut chunk = [0u8; 8192];
        while let Ok(count) = self.stream.read(&mut chunk) {
            if count == 0 {
                break;
            }
            self.inbox.extend(self.parser.push(&chunk[..count]));
        }
    }

    fn wait_until(&mut self, wanted: impl Fn(&Vec<ws::Message>) -> bool, seconds: u64, what: &str) {
        let deadline = Instant::now() + Duration::from_secs(seconds);
        while Instant::now() < deadline {
            self.pump();
            if wanted(&self.inbox) {
                return;
            }
            std::thread::sleep(Duration::from_millis(20));
        }
        panic!("等「{what}」超时，已收到 {} 条消息", self.inbox.len());
    }

    fn texts(&self) -> Vec<Value> {
        self.inbox
            .iter()
            .filter(|message| message.opcode == ws::OP_TEXT)
            .filter_map(|message| serde_json::from_slice(&message.payload).ok())
            .collect()
    }

    fn bytes(&self) -> Vec<Vec<u8>> {
        self.inbox
            .iter()
            .filter(|message| message.opcode == ws::OP_BINARY)
            .map(|message| message.payload.clone())
            .collect()
    }

    fn send_text(&mut self, text: &str) {
        let frame = ws::encode_masked(ws::OP_TEXT, text.as_bytes(), [0x37, 0xfa, 0x21, 0x3d]);
        self.stream.write_all(&frame).expect("客户端消息应能发出");
    }
}

fn start_console() -> std::sync::Arc<console::Console> {
    // 测试不碰内核级后端：它会往内核挂 USB 设备、重指 /dev/ttyUSBn 别名，
    // 跑一遍就把开发机上正在用的实例搅乱（实测把常驻控制台搞挂过）。
    std::env::set_var("KEYSIM_NO_KERNEL", "1");
    console::serve(console::Options { http: 0, bridge: 0, autostart: true, links: Vec::new() })
        .expect("控制台应能起在随机端口")
}

#[test]
/// 启动即可用：首屏就该已开虚拟串口，并给出被测端要连/要选的东西
fn autostart_opens_the_port() {
    let console = start_console();
    let state = http(console.port, "GET", "/api/state", None)["state"].clone();
    assert_eq!(state["open"], true, "启动后虚拟串口应已开启");
    let bridge_url = state["bridgeUrl"].as_str().unwrap_or_default();
    assert!(bridge_url.starts_with("ws://127.0.0.1:") && bridge_url.ends_with("/echo"), "{bridge_url}");
    assert!(
        state["device"]["path"].as_str().is_some() || state["device"]["available"] == false,
        "要么给出设备路径，要么说清为什么不可用：{}",
        state["device"]
    );
    // 内嵌资源：网页与注入脚本都从二进制里出，且脚本里的源地址被改写成本实例端口
    assert!(http_text(console.port, "/").contains("keysim"), "首页没从二进制里出");
    let script = http_text(console.port, "/inject.js");
    assert!(script.contains(&format!("127.0.0.1:{}", console.port)), "注入脚本没改写成本实例端口");
    assert!(script.contains("navigator"), "注入脚本内容不对");
}

#[test]
/// 桌面被测端一连上就该收到"设备在线"，并把它回推的频率/音量记下来
fn desktop_bridge_gets_device_online() {
    let console = start_console();
    let port = console.serial.state()["bridgePort"].as_u64().expect("桥接端口") as u16;
    let mut client = Client::connect(port, "/echo");
    client.wait_until(|inbox| !inbox.is_empty(), 3, "设备在线通知");
    let first = client.texts().first().cloned().expect("首条应是 JSON");
    assert_eq!(first["data"]["status"], true, "首条应是设备在线：{first}");

    // 客户端回推的音频控制必须被收下（MessageWebSocket.js:151,156-157）
    client.send_text(&json!({"type": 0, "fre": 1200, "volume": 0.8}).to_string());
    let deadline = Instant::now() + Duration::from_secs(3);
    while Instant::now() < deadline && console.serial.state()["bridgeClients"].as_u64() == Some(0) {
        std::thread::sleep(Duration::from_millis(20));
    }
    assert_eq!(console.serial.state()["bridgeClients"], 1, "桥接客户端数不对");
}

#[test]
/// 一次手键拍发：桥接收到帧、网页收到字节，两者一一对应且符合 WebSerial 帧协议
fn one_hand_replay_drives_both_channels() {
    let console = start_console();
    let bridge_port = console.serial.state()["bridgePort"].as_u64().unwrap() as u16;
    let mut desktop = Client::connect(bridge_port, "/echo");
    let mut page = Client::connect(console.port, "/ws/serial");
    desktop.wait_until(|inbox| !inbox.is_empty(), 3, "设备在线通知");

    let params = json!({
        "key": "hand",
        "text": "AB",
        "alphabet": "letter",
        "rate": 70,
        "preamble": false,
        "tail": "none",
        "speed": 20
    });
    let preview = http(console.port, "POST", "/api/preview", Some(&params));
    assert_eq!(preview["ok"], true, "{preview}");
    let expected = preview["events"].as_u64().expect("帧数") as usize;
    assert!(expected > 0, "预览没有帧");

    let sent = http(console.port, "POST", "/api/send", Some(&params));
    assert_eq!(sent["ok"], true, "{sent}");

    desktop.wait_until(move |inbox| inbox.len() >= expected + 1, 20, "桥接收齐所有帧");
    page.wait_until(move |inbox| inbox.len() >= expected, 20, "网页收齐所有字节");

    // 桥接：每帧都是 {data:{t,k,d}}，t=0 手键，k=0 按下 / 1 抬起
    let frames: Vec<Value> = desktop
        .texts()
        .into_iter()
        .filter(|value| value["data"]["status"].is_null())
        .collect();
    assert_eq!(frames.len(), expected, "桥接帧数与预览不一致");
    for frame in &frames {
        assert_eq!(frame["data"]["t"], 0, "手键帧 t 必须为 0：{frame}");
        assert!(
            matches!(frame["data"]["k"].as_i64(), Some(0) | Some(1)),
            "k 只能是 0/1：{frame}"
        );
        assert!(frame["data"]["d"].as_f64().is_some(), "缺时间戳：{frame}");
    }

    // 网页：字节必须是 1,0（按下）或 2,0,0（抬起），与帧序一一对应
    let chunks = page.bytes();
    assert_eq!(chunks.len(), expected, "网页字节帧数与预览不一致");
    for (index, chunk) in chunks.iter().enumerate() {
        let k = frames[index]["data"]["k"].as_i64().unwrap();
        if k == 0 {
            assert_eq!(chunk.as_slice(), &[1, 0], "第 {index} 帧应是按下 2 字节");
        } else {
            assert_eq!(chunk.as_slice(), &[2, 0, 0], "第 {index} 帧应是抬起 3 字节");
        }
    }

    let state = http(console.port, "GET", "/api/state", None)["state"].clone();
    assert_eq!(state["replay"]["running"], false, "回放应已结束");
}

#[test]
/// 拍发中途可以停：stop 之后回放状态清零，且不再有新帧
fn stop_halts_replay() {
    let console = start_console();
    let bridge_port = console.serial.state()["bridgePort"].as_u64().unwrap() as u16;
    let mut desktop = Client::connect(bridge_port, "/echo");
    desktop.wait_until(|inbox| !inbox.is_empty(), 3, "设备在线通知");

    let params = json!({"key": "hand", "text": "ABCD EFGH", "rate": 40, "preamble": true, "tail": "turn"});
    assert_eq!(http(console.port, "POST", "/api/send", Some(&params))["ok"], true);
    desktop.wait_until(|inbox| inbox.len() > 3, 10, "拍发开始");

    let stopped = http(console.port, "POST", "/api/stop", None);
    assert_eq!(stopped["stopped"], true, "应报告已停止");
    assert_eq!(stopped["state"]["replay"]["running"], false);

    desktop.pump();
    let before = desktop.inbox.len();
    std::thread::sleep(Duration::from_millis(600));
    desktop.pump();
    assert_eq!(desktop.inbox.len(), before, "停止后仍在发帧");
}

#[test]
/// 报文不合字母表时必须拒发并说清哪些字符不在表里
fn invalid_message_is_rejected() {
    let console = start_console();
    let result = http(
        console.port,
        "POST",
        "/api/send",
        Some(&json!({"key": "hand", "text": "ABC1", "alphabet": "letter"})),
    );
    assert_eq!(result["ok"], false);
    let error = result["error"].as_str().unwrap_or_default();
    assert!(error.contains('1') && error.contains("letter"), "{error}");
}

/// 极简 SSE 客户端：订阅 /api/events，把 data: 行逐条解析出来
struct Events {
    stream: TcpStream,
    buffer: String,
}

impl Events {
    fn connect(port: u16) -> Events {
        let mut stream = TcpStream::connect(("127.0.0.1", port)).expect("控制台应可连接");
        stream.set_read_timeout(Some(Duration::from_millis(100))).ok();
        stream
            .write_all(b"GET /api/events HTTP/1.1\r\nHost: 127.0.0.1\r\nconnection: close\r\n\r\n")
            .expect("订阅请求应能发出");
        let mut events = Events { stream, buffer: String::new() };
        // 事件流是 keep-alive，先读到响应头结束
        let deadline = Instant::now() + Duration::from_secs(5);
        while !events.buffer.contains("\r\n\r\n") {
            assert!(Instant::now() < deadline, "事件流响应头超时");
            events.pump();
        }
        let (_, rest) = events.buffer.split_once("\r\n\r\n").expect("响应头应完整");
        events.buffer = rest.to_string();
        events
    }

    fn pump(&mut self) {
        let mut chunk = [0u8; 8192];
        if let Ok(count) = self.stream.read(&mut chunk) {
            if count > 0 {
                self.buffer.push_str(&String::from_utf8_lossy(&chunk[..count]));
            }
        }
    }

    /// 收到满足条件的事件为止，返回途中所有事件
    fn collect_until(&mut self, seconds: u64, done: impl Fn(&Value) -> bool) -> Vec<Value> {
        let deadline = Instant::now() + Duration::from_secs(seconds);
        let mut events = Vec::new();
        while Instant::now() < deadline {
            self.pump();
            while let Some((chunk, rest)) = self.buffer.split_once("\n\n").map(|(a, b)| (a.to_string(), b.to_string())) {
                self.buffer = rest;
                for line in chunk.lines() {
                    if let Some(data) = line.strip_prefix("data: ") {
                        if let Ok(event) = serde_json::from_str::<Value>(data) {
                            let finished = done(&event);
                            events.push(event);
                            if finished {
                                return events;
                            }
                        }
                    }
                }
            }
            std::thread::sleep(Duration::from_millis(10));
        }
        panic!("等事件超时（{}s），已收到 {} 条", seconds, events.len());
    }
}

#[test]
/// 进度事件是前端播放头的唯一数据源（评审 H2）：
/// 必须带 at/speed、at 单调、末帧落在时间轴后半段，且 speed 真的影响墙上耗时
fn progress_events_drive_the_playhead() {
    let console = start_console();
    let port = console.port;
    let preview = http(port, "POST", "/api/preview", Some(&json!({"key": "hand", "text": "ABCD EFGH IJKL", "rate": 120})));
    assert_eq!(preview["ok"], true, "预览应成功：{preview}");
    let duration = preview["duration"].as_f64().expect("预览应有时间轴总长");
    let total = preview["events"].as_u64().expect("预览应有帧数");

    // 一次拍发：订阅事件流 -> 发 -> 收到末帧进度。返回（进度事件序列, 墙上耗时）
    let run = |speed: f64| {
        let mut events = Events::connect(port);
        let started = Instant::now();
        let sent = http(
            port,
            "POST",
            "/api/send",
            Some(&json!({"key": "hand", "text": "ABCD EFGH IJKL", "rate": 120, "speed": speed})),
        );
        assert_eq!(sent["ok"], true, "拍发应能开始：{sent}");
        let progress: Vec<Value> = events
            .collect_until(30, |event| {
                event["type"] == "progress" && event["sent"].as_u64() == Some(total)
            })
            .into_iter()
            .filter(|event| event["type"] == "progress")
            .collect();
        (progress, started.elapsed())
    };

    let (slow, wall_slow) = run(1.0);
    let (fast, wall_fast) = run(16.0);

    assert!(slow.len() >= 2, "一秒 7 次节流上报，一次拍发不该只有 {} 条", slow.len());
    let mut last = -1.0f64;
    for event in &slow {
        let at = event["at"].as_f64().unwrap_or_else(|| panic!("progress 缺 at：{event}"));
        assert_eq!(event["speed"].as_f64(), Some(1.0), "speed 应如实回传：{event}");
        assert!(at >= last, "at 必须单调不减：{last} -> {at}");
        last = at;
    }
    assert!(
        last > duration * 0.5 && last <= duration * 1.05,
        "末帧进度应落在时间轴后半段：at={last} duration={duration}"
    );
    assert!(
        wall_slow.as_secs_f64() > wall_fast.as_secs_f64() * 4.0,
        "16× 倍速应显著更快：1×={:.2}s 16×={:.2}s",
        wall_slow.as_secs_f64(),
        wall_fast.as_secs_f64()
    );
    assert_eq!(fast.last().and_then(|event| event["speed"].as_f64()), Some(16.0));
}

#[test]
/// 重启竞态（评审 H1）：旧进程还占着控制台端口时，serve 要等它退出重试成功，而不是起来就挂
fn serve_retries_when_http_port_is_taken() {
    let blocker = std::net::TcpListener::bind(("127.0.0.1", 0)).expect("应能占一个空闲端口");
    let port = blocker.local_addr().expect("应能读到占用端口").port();
    // 300ms 后放手，模拟旧进程退出
    std::thread::spawn(move || {
        std::thread::sleep(Duration::from_millis(300));
        drop(blocker);
    });
    let console = console::serve(console::Options { http: port, bridge: 0, autostart: false, links: Vec::new() })
        .expect("占用解除后重试应成功");
    assert_eq!(console.port, port, "应绑到原本被占的那个端口");
}

#[test]
/// 端口一直被占着就要如实失败：重试窗口（约 3s）耗尽后返回 Err，不能假装起来了
fn serve_fails_when_port_stays_taken() {
    let blocker = std::net::TcpListener::bind(("127.0.0.1", 0)).expect("应能占一个空闲端口");
    let port = blocker.local_addr().expect("应能读到占用端口").port();
    let started = Instant::now();
    let result = console::serve(console::Options { http: port, bridge: 0, autostart: false, links: Vec::new() });
    assert!(result.is_err(), "端口一直被占必须失败");
    assert!(started.elapsed() >= Duration::from_secs(2), "返回太快说明根本没重试");
    drop(blocker);
}

#[test]
/// 桥接端口是同一个竞态（评审 H1）：18765 被占时 bridge::start 也要重试
fn bridge_retries_when_port_is_taken() {
    let blocker = std::net::TcpListener::bind(("127.0.0.1", 0)).expect("应能占一个空闲端口");
    let port = blocker.local_addr().expect("应能读到占用端口").port();
    std::thread::spawn(move || {
        std::thread::sleep(Duration::from_millis(300));
        drop(blocker);
    });
    let log: keysim::bridge::Log = std::sync::Arc::new(|_| {});
    let bridge = keysim::bridge::start(port, "/echo", false, log).expect("占用解除后重试应成功");
    assert_eq!(bridge.port, port);
    bridge.close();
}
