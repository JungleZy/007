//! 桌面桥接：在 18765 上冒充仓外硬件桥（TrafficService.exe）。
//!
//! 桌面模式下被测应用主动连 ws://localhost:18765/echo（MessageWebSocket.js:115），
//! 只取 parsed.data（:132-133）：带 status 字段的算设备状态，其余当流量/拍发帧
//! 交给 publishTrafficFrame（:148-155）。收到 {status:true} 后客户端会回推一条
//! {type:0,fre,volume}（:151,156-157），这里记下来供断言。

use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;

use parking_lot::Mutex;
use serde_json::{json, Value};

use crate::ws;

pub struct Received {
    pub text: String,
    pub parsed: Option<Value>,
}

pub struct Bridge {
    pub port: u16,
    pub path: String,
    clients: Arc<Mutex<Vec<TcpStream>>>,
    received: Arc<Mutex<Vec<Received>>>,
    stopped: Arc<AtomicBool>,
    announce_status: bool,
}

impl Bridge {
    pub fn url(&self) -> String {
        format!("ws://127.0.0.1:{}{}", self.port, self.path)
    }

    pub fn client_count(&self) -> usize {
        self.clients.lock().iter().filter(|client| client.peer_addr().is_ok()).count()
    }

    pub fn received(&self) -> Vec<String> {
        self.received.lock().iter().map(|item| item.text.clone()).collect()
    }

    /// 设备在线/离线通知：true 会触发客户端回推频率与音量
    pub fn send_status(&self, status: bool) -> usize {
        self.broadcast(&json!({"data": {"status": status}}).to_string())
    }

    pub fn send_frame(&self, frame: &Value) -> usize {
        self.broadcast(&json!({"data": frame}).to_string())
    }

    pub fn broadcast(&self, text: &str) -> usize {
        let frame = ws::encode(ws::OP_TEXT, text.as_bytes());
        let mut clients = self.clients.lock();
        let mut alive = 0usize;
        clients.retain_mut(|client| match client.write_all(&frame) {
            Ok(()) => {
                alive += 1;
                true
            }
            Err(_) => false,
        });
        alive
    }

    pub fn close(&self) {
        self.stopped.store(true, Ordering::SeqCst);
        for client in self.clients.lock().drain(..) {
            let _ = client.shutdown(std::net::Shutdown::Both);
        }
        // 唤醒 accept 线程让它退出
        let _ = TcpStream::connect(("127.0.0.1", self.port));
    }
}

pub type Log = Arc<dyn Fn(String) + Send + Sync>;

/// 起桥接；port 传 0 由系统分配
pub fn start(port: u16, path: &str, announce_status: bool, log: Log) -> std::io::Result<Arc<Bridge>> {
    let listener = TcpListener::bind(("127.0.0.1", port))?;
    let bound = listener.local_addr()?.port();
    let bridge = Arc::new(Bridge {
        port: bound,
        path: path.to_string(),
        clients: Arc::new(Mutex::new(Vec::new())),
        received: Arc::new(Mutex::new(Vec::new())),
        stopped: Arc::new(AtomicBool::new(false)),
        announce_status,
    });

    let accept_bridge = Arc::clone(&bridge);
    thread::spawn(move || {
        for incoming in listener.incoming() {
            if accept_bridge.stopped.load(Ordering::SeqCst) {
                return;
            }
            let Ok(mut stream) = incoming else { continue };
            let _ = stream.set_nodelay(true);
            let head = match ws::read_headers(&mut stream) {
                Ok(head) => head,
                Err(_) => continue,
            };
            let request_path = head
                .lines()
                .next()
                .and_then(|line| line.split_whitespace().nth(1))
                .map(|target| target.split('?').next().unwrap_or(target).to_string())
                .unwrap_or_default();
            let key = ws::header_value(&head, "sec-websocket-key");
            match (request_path == accept_bridge.path, key) {
                (true, Some(key)) => {
                    if ws::write_handshake(&mut stream, &key).is_err() {
                        continue;
                    }
                }
                _ => {
                    let _ = stream.write_all(b"HTTP/1.1 400 Bad Request\r\n\r\n");
                    continue;
                }
            }
            log(format!("桌面桥接已接入：{request_path}"));
            if accept_bridge.announce_status {
                let frame = ws::encode(ws::OP_TEXT, json!({"data": {"status": true}}).to_string().as_bytes());
                let _ = stream.write_all(&frame);
            }
            let reader = match stream.try_clone() {
                Ok(clone) => clone,
                Err(_) => continue,
            };
            accept_bridge.clients.lock().push(stream);

            let received = Arc::clone(&accept_bridge.received);
            let log = Arc::clone(&log);
            thread::spawn(move || {
                let mut reader = reader;
                let mut parser = ws::Parser::default();
                let mut buffer = [0u8; 4096];
                loop {
                    match reader.read(&mut buffer) {
                        Ok(0) | Err(_) => return,
                        Ok(count) => {
                            for message in parser.push(&buffer[..count]) {
                                if message.opcode == ws::OP_CLOSE {
                                    return;
                                }
                                if message.opcode != ws::OP_TEXT {
                                    continue;
                                }
                                let text = String::from_utf8_lossy(&message.payload).to_string();
                                log(format!("桥接收到：{}", text.chars().take(160).collect::<String>()));
                                let parsed = serde_json::from_str(&text).ok();
                                received.lock().push(Received { text, parsed });
                            }
                        }
                    }
                }
            });
        }
    });

    Ok(bridge)
}
