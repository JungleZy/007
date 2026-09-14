//! 虚拟串口总管：一个开关，若干条真实链路，同一份时间轴同时下发。
//!
//! ⓿ 内核级设备（usbip/gadget/tty0tty/com0com）：浏览器选择框与桌面串口列表都能直接选中
//! ① PTY 设备：任何按路径打开串口的程序可用（浏览器选择框看不到 PTY）
//! ② 桌面桥接 18765：桌面模式下被测应用自己连过来，线上跑帧级 JSON
//! ③ 浏览器注入 /ws/serial：页面里的虚拟 navigator.serial 连回来，线上跑串口字节

use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant};

use parking_lot::Mutex;
use serde_json::{json, Value};

use crate::bridge::{self, Bridge};
use crate::kernel::{self, Started};
use crate::pty::{self, Pty};
use crate::sinks;
use crate::timeline::Timeline;

pub type Sink = Arc<dyn Fn(Value) + Send + Sync>;

struct Replay {
    sent: usize,
    total: usize,
    key: String,
    text: String,
    cancel: Arc<AtomicBool>,
}

pub struct VirtualSerial {
    bridge_port: u16,
    device_links: Vec<PathBuf>,
    sink: Sink,
    bridge: Mutex<Option<Arc<Bridge>>>,
    device: Mutex<Option<Pty>>,
    kernel: Mutex<Option<Started>>,
    kernel_reasons: Mutex<Vec<String>>,
    system_link: Mutex<Option<String>>,
    inject: Mutex<Vec<std::net::TcpStream>>,
    replay: Mutex<Option<Replay>>,
}

pub const SYSTEM_LINK: &str = "/dev/ttyUSB0";

impl VirtualSerial {
    pub fn new(bridge_port: u16, device_links: Vec<PathBuf>, sink: Sink) -> Arc<Self> {
        Arc::new(VirtualSerial {
            bridge_port,
            device_links,
            sink,
            bridge: Mutex::new(None),
            device: Mutex::new(None),
            kernel: Mutex::new(None),
            kernel_reasons: Mutex::new(Vec::new()),
            system_link: Mutex::new(None),
            inject: Mutex::new(Vec::new()),
            replay: Mutex::new(None),
        })
    }

    fn log(&self, level: &str, message: impl Into<String>) {
        (self.sink)(json!({"type": "log", "level": level, "message": message.into()}));
    }

    fn announce(&self) {
        (self.sink)(json!({"type": "state", "state": self.state()}));
    }

    pub fn state(&self) -> Value {
        let bridge = self.bridge.lock();
        let device = self.device.lock();
        let kernel = self.kernel.lock();
        let replay = self.replay.lock();
        json!({
            "open": bridge.is_some(),
            "bridgePort": bridge.as_ref().map(|item| item.port).unwrap_or(self.bridge_port),
            "bridgeUrl": bridge.as_ref().map(|item| item.url())
                .unwrap_or_else(|| format!("ws://127.0.0.1:{}/echo", self.bridge_port)),
            "bridgeClients": bridge.as_ref().map(|item| item.client_count()).unwrap_or(0),
            "injectClients": self.inject.lock().len(),
            "kernel": {
                "id": kernel.as_ref().map(|item| item.id),
                "title": kernel.as_ref().map(|item| item.title),
                "selectable": kernel.as_ref().map(|item| item.selectable.clone()),
                "selectableOpenable": kernel.as_ref().map(|item| kernel::readable_and_writable(&item.selectable)),
                "reasons": self.kernel_reasons.lock().clone(),
                "backends": kernel::probe_all().iter().map(|verdict| verdict.to_json()).collect::<Vec<_>>()
            },
            "device": {
                "path": device.as_ref().map(|item| item.path.clone()),
                "links": device.as_ref().map(|item| item.links.iter().map(|link| link.display().to_string()).collect::<Vec<_>>()).unwrap_or_default(),
                "warnings": device.as_ref().map(|item| item.warnings.clone()).unwrap_or_default(),
                "available": pty::probe().is_ok(),
                "reason": pty::probe().err(),
                "systemLink": SYSTEM_LINK,
                "systemLinked": self.system_link.lock().clone(),
                "systemLinkHint": device.as_ref().map(|item| item.system_link_hint(SYSTEM_LINK))
            },
            "replay": match replay.as_ref() {
                Some(state) => json!({"running": true, "sent": state.sent, "total": state.total, "key": state.key, "text": state.text}),
                None => json!({"running": false, "sent": 0, "total": 0})
            }
        })
    }

    pub fn attach_inject(&self, stream: std::net::TcpStream) {
        self.inject.lock().push(stream);
        let count = self.inject.lock().len();
        self.log("info", format!("浏览器虚拟串口已接入（当前 {count} 个）"));
        self.announce();
    }

    /// 开启：内核级优先（浏览器/桌面都能直接选），同时开 PTY 与桥接
    pub fn open(self: &Arc<Self>) -> Result<Value, String> {
        if self.bridge.lock().is_some() {
            return Ok(self.state());
        }

        let sink = Arc::clone(&self.sink);
        let log: Arc<dyn Fn(String) + Send + Sync> = Arc::new(move |message: String| {
            sink(json!({"type": "log", "level": "info", "message": message}));
        });

        match kernel::start(Arc::clone(&log)) {
            Ok(started) => {
                self.log(
                    "ok",
                    format!("内核级虚拟串口已就绪（{}）：被测程序请选 {}", started.title, started.selectable),
                );
                *self.kernel_reasons.lock() = Vec::new();
                *self.kernel.lock() = Some(started);
            }
            Err(reasons) => {
                self.log(
                    "info",
                    format!(
                        "没有可用的内核级后端（{}），使用 PTY 设备；浏览器选择框看不到 PTY，Web 模式请用「打开并预置虚拟串口」",
                        reasons.join("；")
                    ),
                );
                *self.kernel_reasons.lock() = reasons;
            }
        }

        match Pty::open(&self.device_links) {
            Ok(device) => {
                let sink = Arc::clone(&self.sink);
                device.watch(move |hex| {
                    sink(json!({"type": "log", "level": "recv", "message": format!("设备对端写入：{hex}")}));
                });
                self.log(
                    "ok",
                    format!(
                        "虚拟串口设备已就绪：{}{}",
                        device.path,
                        if device.links.is_empty() {
                            String::new()
                        } else {
                            format!(" → {}", device.links.iter().map(|link| link.display().to_string()).collect::<Vec<_>>().join(" , "))
                        }
                    ),
                );
                for warning in &device.warnings {
                    self.log("warn", warning.clone());
                }
                *self.device.lock() = Some(device);
            }
            Err(error) => self.log("warn", format!("PTY 设备创建失败，仅启用其余通道：{error}")),
        }

        let sink = Arc::clone(&self.sink);
        let bridge_log: bridge::Log = Arc::new(move |message: String| {
            sink(json!({"type": "log", "level": "info", "message": message}));
        });
        let bridge = bridge::start(self.bridge_port, "/echo", true, bridge_log)
            .map_err(|error| format!("桥接端口 {} 无法监听：{error}", self.bridge_port))?;
        self.log("ok", format!("虚拟串口已开启：{}", bridge.url()));
        *self.bridge.lock() = Some(bridge);
        self.announce();
        Ok(self.state())
    }

    pub fn close(self: &Arc<Self>) -> Value {
        self.stop();
        if let Some(link) = self.system_link.lock().take() {
            let _ = std::fs::remove_file(&link);
        }
        if let Some(started) = self.kernel.lock().take() {
            kernel::stop(&started);
            self.log("warn", "内核级虚拟串口已移除");
        }
        if self.device.lock().take().is_some() {
            self.log("warn", "虚拟串口设备已移除");
        }
        if let Some(bridge) = self.bridge.lock().take() {
            bridge.close();
            self.log("warn", "虚拟串口已关闭");
        }
        for stream in self.inject.lock().drain(..) {
            let _ = stream.shutdown(std::net::Shutdown::Both);
        }
        self.announce();
        self.state()
    }

    /// 把设备接进系统串口列表（桌面壳只认 /dev/ttyUSBn）
    pub fn link_system(&self, target: &str) -> Value {
        let device = self.device.lock();
        let Some(device) = device.as_ref() else {
            return json!({"ok": false, "error": "虚拟串口未开启"});
        };
        let _ = std::fs::remove_file(target);
        if std::os::unix::fs::symlink(&device.path, target).is_ok() {
            *self.system_link.lock() = Some(target.to_string());
            self.log("ok", format!("已接入系统串口列表：{target} → {}", device.path));
            self.announce();
            return json!({"ok": true, "target": target, "elevated": false});
        }
        match kernel::elevation() {
            Ok(prefix) => {
                let mut command = match prefix {
                    Some(tool) => {
                        let mut command = std::process::Command::new(tool);
                        command.arg("ln");
                        command
                    }
                    None => std::process::Command::new("ln"),
                };
                let output = command.args(["-sfn", &device.path, target]).output();
                match output {
                    Ok(result) if result.status.success() => {
                        *self.system_link.lock() = Some(target.to_string());
                        self.log("ok", format!("已接入系统串口列表：{target} → {}（经系统授权）", device.path));
                        self.announce();
                        json!({"ok": true, "target": target, "elevated": true})
                    }
                    Ok(result) => json!({
                        "ok": false,
                        "error": format!("系统授权未通过：{}", String::from_utf8_lossy(&result.stderr).trim()),
                        "hint": device.system_link_hint(target)
                    }),
                    Err(error) => json!({"ok": false, "error": error.to_string(), "hint": device.system_link_hint(target)}),
                }
            }
            Err(reason) => json!({"ok": false, "error": reason, "hint": device.system_link_hint(target)}),
        }
    }

    pub fn unlink_system(&self, target: &str) -> Value {
        match std::fs::remove_file(target) {
            Ok(()) => {
                *self.system_link.lock() = None;
                self.log("ok", format!("已移出系统串口列表：{target}"));
                self.announce();
                json!({"ok": true, "target": target})
            }
            Err(error) => json!({"ok": false, "error": error.to_string()}),
        }
    }

    /// 按时间轴向所有通道同时回放；speed>1 整体加速
    /// （注意客户端的 800ms/静默定时器不随之缩放）
    pub fn send(self: &Arc<Self>, timeline: &Timeline, speed: f64, text: &str) -> Result<Value, String> {
        if self.bridge.lock().is_none() {
            return Err("虚拟串口未开启".into());
        }
        if self.replay.lock().is_some() {
            return Err("上一次拍发还在进行".into());
        }
        let frames = sinks::to_bridge_messages(timeline, 0.0);
        let chunks = sinks::to_bytes(timeline);
        if frames.len() != chunks.len() {
            return Err("帧与字节序列长度不一致，时间轴异常".into());
        }
        let cancel = Arc::new(AtomicBool::new(false));
        *self.replay.lock() = Some(Replay {
            sent: 0,
            total: frames.len(),
            key: timeline.key.to_string(),
            text: text.to_string(),
            cancel: Arc::clone(&cancel),
        });
        self.log(
            "ok",
            format!(
                "开始拍发：{} {} 帧，约 {:.1}s",
                if timeline.key == "hand" { "手键" } else { "电子键" },
                frames.len(),
                timeline.duration() / 1000.0 / speed
            ),
        );
        self.announce();

        let manager = Arc::clone(self);
        let items: Vec<(f64, String, Vec<u8>)> = frames
            .into_iter()
            .zip(chunks)
            .map(|((at, text), chunk)| (at, text, chunk.bytes))
            .collect();
        thread::spawn(move || {
            let started = Instant::now();
            let mut index = 0usize;
            let total = items.len();
            while index < total {
                if cancel.load(Ordering::SeqCst) {
                    break;
                }
                let elapsed = started.elapsed().as_secs_f64() * 1000.0;
                let due = items[index].0 / speed;
                if due > elapsed + 1.0 {
                    thread::sleep(Duration::from_micros(((due - elapsed) * 1000.0).min(50_000.0) as u64));
                    continue;
                }
                let (_, text, bytes) = &items[index];
                if let Some(bridge) = manager.bridge.lock().as_ref() {
                    bridge.broadcast(text);
                }
                if let Some(started) = manager.kernel.lock().as_mut() {
                    let _ = started.writer.write(bytes);
                }
                if let Some(device) = manager.device.lock().as_ref() {
                    let _ = device.write(bytes);
                }
                {
                    let frame = crate::ws::encode(crate::ws::OP_BINARY, bytes);
                    let mut clients = manager.inject.lock();
                    clients.retain_mut(|client| std::io::Write::write_all(client, &frame).is_ok());
                }
                index += 1;
                if let Some(state) = manager.replay.lock().as_mut() {
                    state.sent = index;
                }
                if index % 20 == 0 || index == total {
                    (manager.sink)(json!({"type": "progress", "sent": index, "total": total}));
                }
            }
            let aborted = index < total;
            *manager.replay.lock() = None;
            manager.log(
                if aborted { "warn" } else { "ok" },
                if aborted {
                    format!("拍发已中止，已发 {index} 帧")
                } else {
                    format!("拍发完成，共 {index} 帧")
                },
            );
            (manager.sink)(json!({"type": "progress", "sent": index, "total": total}));
            manager.announce();
        });
        Ok(self.state())
    }

    /// 立刻停：置取消位并等回放线程收尾
    pub fn stop(&self) -> bool {
        let cancel = self.replay.lock().as_ref().map(|state| Arc::clone(&state.cancel));
        match cancel {
            Some(flag) => {
                flag.store(true, Ordering::SeqCst);
                for _ in 0..100 {
                    if self.replay.lock().is_none() {
                        break;
                    }
                    thread::sleep(Duration::from_millis(10));
                }
                true
            }
            None => false,
        }
    }
}
