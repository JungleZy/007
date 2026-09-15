//! 虚拟串口总管：一个开关，若干条真实链路，同一份时间轴同时下发。
//!
//! ⓿ 内核级设备（usbip/gadget/tty0tty/com0com）：浏览器选择框与桌面串口列表都能直接选中
//! ① PTY 设备（仅类 Unix）：任何按路径打开串口的程序可用（浏览器选择框看不到 PTY）；
//!    Windows 上没有这条，那边的 ⓿ 就是 com0com 给的真实 COM 口
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
use crate::keying;
#[cfg(unix)]
use crate::pty::{self, Pty};
use crate::sinks;
use crate::timeline::Timeline;

pub type Sink = Arc<dyn Fn(Value) + Send + Sync>;

struct Replay {
    sent: usize,
    total: usize,
    /// 回放倍速：页面用它在两次上报之间本地插值，播放头才能 60fps 地走
    speed: f64,
    /// 已发出的最后一帧在时间轴上的毫秒数。
    /// 播放头必须用它定位：帧在时间上并不等距（按压几十毫秒、组间隔几百毫秒），
    /// 按 sent/total 线性插值画出来的进度条只会等步长走，和报文对不上。
    at: f64,
    key: String,
    text: String,
    cancel: Arc<AtomicBool>,
}

pub struct VirtualSerial {
    bridge_port: u16,
    #[cfg(unix)]
    device_links: Vec<PathBuf>,
    sink: Sink,
    bridge: Mutex<Option<Arc<Bridge>>>,
    #[cfg(unix)]
    device: Mutex<Option<Pty>>,
    kernel: Mutex<Option<Started>>,
    kernel_reasons: Mutex<Vec<String>>,
    system_link: Mutex<Option<String>>,
    inject: Mutex<Vec<std::net::TcpStream>>,
    replay: Mutex<Option<Replay>>,
}

/// 桌面壳只认 /dev/ttyUSBn（nativeSerialPort.js:45），这是手动接入时用的名字
pub const SYSTEM_LINK: &str = "/dev/ttyUSB0";

impl VirtualSerial {
    pub fn new(bridge_port: u16, device_links: Vec<PathBuf>, sink: Sink) -> Arc<Self> {
        #[cfg(not(unix))]
        let _ = device_links;
        Arc::new(VirtualSerial {
            bridge_port,
            #[cfg(unix)]
            device_links,
            sink,
            bridge: Mutex::new(None),
            #[cfg(unix)]
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

    /// 设备通道（PTY）在 state 里的形态；Windows 上如实报告"本平台不提供"。
    /// `link_needed`：内核后端已经给出桌面别名时，"接入系统串口列表"这一步就是多余的。
    fn device_state(&self, link_needed: bool) -> Value {
        #[cfg(unix)]
        {
            let device = self.device.lock();
            let probe = pty::probe();
            json!({
                "supported": true,
                "linkNeeded": link_needed && device.is_some(),
                "path": device.as_ref().map(|item| item.path.clone()),
                "links": device.as_ref().map(|item| item.links.iter().map(|link| link.display().to_string()).collect::<Vec<_>>()).unwrap_or_default(),
                "warnings": device.as_ref().map(|item| item.warnings.clone()).unwrap_or_default(),
                "available": probe.is_ok(),
                "reason": probe.err(),
                "systemLink": SYSTEM_LINK,
                "systemLinked": self.system_link.lock().clone(),
                "systemLinkHint": device.as_ref().map(|item| item.system_link_hint(SYSTEM_LINK))
            })
        }
        #[cfg(not(unix))]
        {
            let _ = link_needed;
            json!({
                "supported": false,
                "linkNeeded": false,
                "path": Value::Null,
                "links": [],
                "warnings": [],
                "available": false,
                "reason": "本平台不提供 PTY 设备通道：com0com 已经给出可直接选中的 COM 口",
                "systemLink": Value::Null,
                "systemLinked": Value::Null,
                "systemLinkHint": Value::Null
            })
        }
    }

    pub fn state(&self) -> Value {
        let bridge = self.bridge.lock();
        let kernel = self.kernel.lock();
        let replay = self.replay.lock();
        json!({
            "platform": crate::PLATFORM,
            "helper": {
                "required": crate::HELPER_REQUIRED,
                "installed": crate::install::helper_installed(),
                "command": crate::install::install_command()
            },
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
                "selectableOpenable": kernel.as_ref().and_then(|item| kernel::openable(&item.selectable)),
                "desktopAlias": kernel.as_ref().and_then(|item| item.desktop_alias.clone()),
                "reasons": self.kernel_reasons.lock().clone(),
                "backends": kernel::probe_all().iter().map(|verdict| verdict.to_json()).collect::<Vec<_>>()
            },
            "device": self.device_state(kernel.as_ref().and_then(|item| item.desktop_alias.as_ref()).is_none()),
            "replay": match replay.as_ref() {
                Some(state) => json!({
                    "running": true, "sent": state.sent, "total": state.total,
                    "at": state.at, "speed": state.speed, "key": state.key, "text": state.text
                }),
                None => json!({"running": false, "sent": 0, "total": 0, "at": 0.0, "speed": 1.0})
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
                        "没有可用的内核级后端（{}）；{}",
                        reasons.join("；"),
                        if crate::DEVICE_CHANNEL_SUPPORTED {
                            "改用 PTY 设备：浏览器选择框看不到 PTY，Web 模式请用「打开并预置虚拟串口」"
                        } else {
                            "本平台请先安装 com0com；桥接与浏览器注入两条通道仍可用"
                        }
                    ),
                );
                *self.kernel_reasons.lock() = reasons;
            }
        }

        #[cfg(unix)]
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
        #[cfg(unix)]
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
    #[cfg(unix)]
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

    #[cfg(unix)]
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

    /// Windows 上没有"接进列表"这一步：com0com 直接给出 COM 口名
    #[cfg(not(unix))]
    pub fn link_system(&self, _target: &str) -> Value {
        json!({"ok": false, "error": "本平台无需接入：com0com 的 COM 口本身就在系统串口列表里"})
    }

    #[cfg(not(unix))]
    pub fn unlink_system(&self, _target: &str) -> Value {
        json!({"ok": false, "error": "本平台无需接入，也就无需移出"})
    }

    /// 按时间轴向所有通道同时回放；speed≠1 整体缩放时间轴
    /// （注意被测程序侧的 800ms/静默定时器不随之缩放）
    pub fn send(self: &Arc<Self>, timeline: &Timeline, speed: f64, text: &str) -> Result<Value, String> {
        if self.bridge.lock().is_none() {
            return Err("虚拟串口未开启".into());
        }
        // speed 必须有限且为正：0 会让 due=x/speed=+inf，回放线程永远等不到发帧而空转
        if !speed.is_finite() || speed <= 0.0 {
            return Err(format!("倍速必须是正的有限数，收到 {speed}"));
        }
        let frames = sinks::to_bridge_messages(timeline, 0.0);
        let chunks = sinks::to_bytes(timeline);
        if frames.len() != chunks.len() {
            return Err("帧与字节序列长度不一致，时间轴异常".into());
        }
        let cancel = Arc::new(AtomicBool::new(false));
        // 检查与赋值必须在同一次锁持有内完成，否则并发 /api/send 会起两个回放线程，
        // 双倍写入全部通道，且 stop() 只能取消其中一个
        {
            let mut replay = self.replay.lock();
            if replay.is_some() {
                return Err("上一次拍发还在进行".into());
            }
            *replay = Some(Replay {
                sent: 0,
                total: frames.len(),
                speed,
                at: 0.0,
                key: timeline.key.to_string(),
                text: text.to_string(),
                cancel: Arc::clone(&cancel),
            });
        }
        // 组边界：用来在拍发过程中按组报进度。客户端/服务端的码率口径是
        // 字符数×60000/采集区间，所以这里顺便报"到这一组为止的实测码率"，
        // 跑长页时不用等结束就能看出快了还是慢了。
        let per_unit = if timeline.key == "hand" { 1.0 } else { 4.0 };
        let unit_name = if timeline.key == "hand" { "字/分" } else { "组/分" };
        let origin = timeline.body_chars().next().map(|item| item.started_at).unwrap_or(0.0);
        let mut milestones: Vec<(f64, usize, String, usize)> = Vec::new();
        let mut counted = 0usize;
        for (group_index, group) in timeline.groups.iter().enumerate() {
            let chars: Vec<&crate::timeline::Char> =
                timeline.body_chars().filter(|item| item.group == Some(group_index)).collect();
            let Some(last) = chars.last() else { continue };
            counted += chars.len();
            milestones.push((last.ended_at, group_index + 1, group.clone(), counted));
        }
        let groups_total = milestones.len();
        let target_rate = keying::measured_rate(timeline, per_unit);

        self.log(
            "ok",
            format!(
                "开始拍发：{}·{} · {} 组 {} 字 · {:.1} {} · {} 帧 · 采集区间 {:.2}s{}",
                if timeline.key == "hand" { "手键" } else { "电子键" },
                if timeline.jitter > 0.0 { format!("真人手感 ±{:.0}%", timeline.jitter * 100.0) } else { "机械等长".to_string() },
                groups_total,
                counted,
                target_rate,
                unit_name,
                frames.len(),
                (timeline.duration() - origin) / 1000.0,
                if speed == 1.0 { String::new() } else { format!(" · {speed}× 倍速") }
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
            let mut last_report = Instant::now();
            let mut index = 0usize;
            let mut next_milestone = 0usize;
            // 通道写失败只报一次，避免一页刷几千条
            let mut kernel_failed = false;
            #[cfg(unix)]
            let mut device_failed = false;
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
                let (at, text, bytes) = &items[index];
                if let Some(bridge) = manager.bridge.lock().as_ref() {
                    bridge.broadcast(text);
                }
                if let Some(started) = manager.kernel.lock().as_mut() {
                    if let Err(error) = started.writer.write(bytes) {
                        if !kernel_failed {
                            kernel_failed = true;
                            manager.log("error", format!("内核级设备写入失败，该通道本次拍发已失联：{error}"));
                        }
                    }
                }
                #[cfg(unix)]
                if let Some(device) = manager.device.lock().as_ref() {
                    if let Err(error) = device.write(bytes) {
                        if !device_failed {
                            device_failed = true;
                            manager.log("error", format!("PTY 设备写入失败，该通道本次拍发已失联：{error}"));
                        }
                    }
                }
                {
                    let frame = crate::ws::encode(crate::ws::OP_BINARY, bytes);
                    let mut clients = manager.inject.lock();
                    let before = clients.len();
                    clients.retain_mut(|client| std::io::Write::write_all(client, &frame).is_ok());
                    if clients.len() < before {
                        manager.log("warn", format!("{} 个注入页面在拍发中断开", before - clients.len()));
                    }
                }
                index += 1;
                if let Some(state) = manager.replay.lock().as_mut() {
                    state.sent = index;
                    state.at = *at;
                }
                // 按时间节流上报（约 7 次/秒）：既让播放头走得顺，又不会一帧一条
                if index == total || last_report.elapsed() >= Duration::from_millis(140) {
                    last_report = Instant::now();
                    (manager.sink)(json!({"type": "progress", "sent": index, "total": total, "at": at, "speed": speed}));
                }
                // 走过一组就报一行：组号、这组的报文、到此为止的实测码率
                while next_milestone < milestones.len() && *at >= milestones[next_milestone].0 {
                    let (mark_at, number, group, chars_done) = &milestones[next_milestone];
                    let window = (mark_at - origin).max(1.0);
                    let rate = *chars_done as f64 * 60000.0 / (window * per_unit);
                    manager.log(
                        "info",
                        format!(
                            // 叫"至此均速"而不是"实测码率"：结算口径的采集区间含收尾符与末字静默，
                            // 中途这个数必然偏高，最后一行才是服务端会算出来的那个值
                            "第 {number}/{groups_total} 组 {group} 已发 · {chars_done} 字 · {:.1}s · 至此均速 {rate:.1} {unit_name}",
                            window / 1000.0
                        ),
                    );
                    next_milestone += 1;
                }
            }
            let aborted = index < total;
            let wall = started.elapsed().as_secs_f64();
            *manager.replay.lock() = None;
            if aborted {
                manager.log(
                    "warn",
                    format!(
                        "拍发已中止：第 {}/{groups_total} 组，{index}/{total} 帧，已过 {wall:.1}s",
                        next_milestone.max(1).min(groups_total.max(1)),
                    ),
                );
                // 中止时循环内的节流上报可能没覆盖到最后一帧，补一条终态；
                // 正常完成时 index==total 已在循环内报过，不再重复
                let ended_at = items.get(index.saturating_sub(1)).map(|item| item.0).unwrap_or(0.0);
                (manager.sink)(json!({"type": "progress", "sent": index, "total": total, "at": ended_at, "speed": speed}));
            } else {
                let sent_chars = milestones.last().map(|item| item.3).unwrap_or(0);
                let window = (items.last().map(|item| item.0).unwrap_or(0.0) - origin).max(1.0);
                let rate = sent_chars as f64 * 60000.0 / (window * per_unit);
                manager.log(
                    "ok",
                    format!(
                        "拍发完成：{groups_total} 组 {sent_chars} 字 · {index} 帧 · 墙上时间 {wall:.1}s · 实测 {rate:.1} {unit_name}（目标 {target_rate:.1}，偏 {:+.2}%）",
                        (rate / target_rate.max(0.001) - 1.0) * 100.0
                    ),
                );
            }
            manager.announce();
        });
        Ok(self.state())
    }

    /// 立刻停：置取消位并等回放线程收尾；返回是否真正停下来了
    pub fn stop(&self) -> bool {
        let cancel = self.replay.lock().as_ref().map(|state| Arc::clone(&state.cancel));
        match cancel {
            Some(flag) => {
                flag.store(true, Ordering::SeqCst);
                for _ in 0..100 {
                    if self.replay.lock().is_none() {
                        return true;
                    }
                    thread::sleep(Duration::from_millis(10));
                }
                // 取消位已置但 1s 内回放线程没收尾：如实报告，不让 /api/stop 误报 stopped
                self.replay.lock().is_none()
            }
            None => false,
        }
    }
}
