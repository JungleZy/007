//! root 助手：只干一件特权活——把模拟器的 USB/IP 连接交给内核的 vhci_hcd。
//!
//! 往 /sys/devices/platform/vhci_hcd.0/attach 写 "port sockfd devid speed" 是向内核注册
//! USB 设备，必须 root。设备本身（描述符、端点、CDC-ACM 行为）跑在普通用户权限的
//! 模拟器里，本助手不碰业务数据。
//!
//! 攻击面刻意收窄：
//! - 只监听本机 unix socket，且只接受安装时指定 uid 的调用方（SO_PEERCRED 校验）；
//! - 只允许连回 127.0.0.1 的端口，不接受任意主机；
//! - 只做 attach / detach / probe 三件事，不执行调用方给的任何命令。

use std::fs;
use std::io::{BufRead, BufReader, Read, Write};
use std::os::fd::AsRawFd;
use std::os::unix::net::{UnixListener, UnixStream};
use std::path::Path;

use serde_json::{json, Value};

pub const DEFAULT_SOCKET: &str = "/run/keysim/keysim-attachd.sock";
const VHCI_BASE: &str = "/sys/devices/platform/vhci_hcd.0";
const USBIP_VERSION: u16 = 0x0111;
const OP_REQ_IMPORT: u16 = 0x8003;
const OP_REP_IMPORT: u16 = 0x0003;
/// VDEV_ST_NULL：该 vhci 端口空闲
const FREE_PORT: u32 = 4;

pub fn attach_path() -> String {
    format!("{VHCI_BASE}/attach")
}
pub fn detach_path() -> String {
    format!("{VHCI_BASE}/detach")
}
pub fn status_path() -> String {
    format!("{VHCI_BASE}/status")
}

pub fn socket_path() -> String {
    std::env::var("KEYSIM_ATTACHD_SOCKET").unwrap_or_else(|_| DEFAULT_SOCKET.to_string())
}

#[derive(Debug, Clone, Copy)]
struct PortState {
    hub_super_speed: bool,
    port: u32,
    status: u32,
}

/// status 文件在不同内核版本里有无 hub 列，两种都认
fn read_ports() -> Result<Vec<PortState>, String> {
    let text = fs::read_to_string(status_path()).map_err(|error| error.to_string())?;
    let mut ports = Vec::new();
    for line in text.lines() {
        let mut tokens: Vec<&str> = line.split_whitespace().collect();
        if tokens.is_empty() || tokens[0] == "hub" || tokens[0] == "prt" {
            continue;
        }
        let mut hub_super_speed = false;
        if tokens[0] == "hs" || tokens[0] == "ss" {
            hub_super_speed = tokens[0] == "ss";
            tokens.remove(0);
        }
        let (Ok(port), Ok(status)) = (tokens[0].parse::<u32>(), tokens.get(1).unwrap_or(&"").parse::<u32>()) else {
            continue;
        };
        ports.push(PortState { hub_super_speed, port, status });
    }
    Ok(ports)
}

fn free_port(speed: u32) -> Result<u32, String> {
    let want_super = speed >= 5;
    read_ports()?
        .into_iter()
        .find(|entry| entry.status == FREE_PORT && entry.hub_super_speed == want_super)
        .map(|entry| entry.port)
        .ok_or_else(|| "没有空闲的 vhci 端口".to_string())
}

fn modprobe() -> Result<(), String> {
    if Path::new(&attach_path()).exists() {
        return Ok(());
    }
    let output = std::process::Command::new("modprobe")
        .arg("vhci-hcd")
        .output()
        .map_err(|error| format!("执行 modprobe 失败：{error}"))?;
    if !output.status.success() {
        return Err(format!(
            "modprobe vhci-hcd 失败：{}",
            String::from_utf8_lossy(&output.stderr).trim()
        ));
    }
    if Path::new(&attach_path()).exists() {
        Ok(())
    } else {
        Err("vhci-hcd 已加载但没有 attach 节点".into())
    }
}

/// 以 USB/IP 客户端身份完成 import 握手，socket 随后交给内核
fn import(port: u16, busid: &str) -> Result<(std::net::TcpStream, u32, u32), String> {
    let mut stream = std::net::TcpStream::connect(("127.0.0.1", port))
        .map_err(|error| format!("连接模拟器 127.0.0.1:{port} 失败：{error}"))?;
    let mut request = Vec::with_capacity(40);
    request.extend_from_slice(&USBIP_VERSION.to_be_bytes());
    request.extend_from_slice(&OP_REQ_IMPORT.to_be_bytes());
    request.extend_from_slice(&0u32.to_be_bytes());
    let mut id = [0u8; 32];
    id[..busid.len().min(32)].copy_from_slice(&busid.as_bytes()[..busid.len().min(32)]);
    request.extend_from_slice(&id);
    stream.write_all(&request).map_err(|error| error.to_string())?;

    let mut head = [0u8; 8];
    stream.read_exact(&mut head).map_err(|error| format!("模拟器没回应 import：{error}"))?;
    let code = u16::from_be_bytes([head[2], head[3]]);
    let status = u32::from_be_bytes([head[4], head[5], head[6], head[7]]);
    if code != OP_REP_IMPORT || status != 0 {
        return Err(format!("import 被拒绝（code={code:#06x} status={status}）"));
    }
    let mut device = [0u8; 312];
    stream.read_exact(&mut device).map_err(|error| format!("import 响应不完整：{error}"))?;
    let busnum = u32::from_be_bytes([device[288], device[289], device[290], device[291]]);
    let devnum = u32::from_be_bytes([device[292], device[293], device[294], device[295]]);
    let speed = u32::from_be_bytes([device[296], device[297], device[298], device[299]]);
    Ok((stream, (busnum << 16) | devnum, speed))
}

fn do_attach(request: &Value) -> Value {
    let port = request["port"].as_u64().unwrap_or(0);
    if !(1..=65535).contains(&port) {
        return json!({"ok": false, "error": "端口不合法"});
    }
    let busid = request["busid"].as_str().unwrap_or("1-1");
    if let Err(error) = modprobe() {
        return json!({"ok": false, "error": error});
    }
    let (stream, devid, speed) = match import(port as u16, busid) {
        Ok(result) => result,
        Err(error) => return json!({"ok": false, "error": error}),
    };
    let vhci_port = match free_port(speed) {
        Ok(port) => port,
        Err(error) => return json!({"ok": false, "error": error}),
    };
    let payload = format!("{vhci_port} {} {devid} {speed}", stream.as_raw_fd());
    match fs::write(attach_path(), payload) {
        Ok(()) => {
            eprintln!("[keysim-attachd] attach 成功：vhci 端口 {vhci_port}，devid {devid}，speed {speed}");
            json!({"ok": true, "vhciPort": vhci_port, "devid": devid, "speed": speed})
        }
        Err(error) => json!({"ok": false, "error": format!("写 attach 失败：{error}")}),
    }
}

fn do_detach(request: &Value) -> Value {
    let Some(port) = request["vhciPort"].as_u64() else {
        return json!({"ok": false, "error": "缺少 vhciPort"});
    };
    match fs::write(detach_path(), port.to_string()) {
        Ok(()) => {
            eprintln!("[keysim-attachd] detach 成功：vhci 端口 {port}");
            json!({"ok": true, "vhciPort": port})
        }
        Err(error) => json!({"ok": false, "error": format!("写 detach 失败：{error}")}),
    }
}

fn do_probe() -> Value {
    let ports = read_ports().unwrap_or_default();
    json!({
        "ok": true,
        // SAFETY: geteuid 无副作用
        "root": (unsafe { libc::geteuid() }) == 0,
        "moduleLoaded": Path::new(&attach_path()).exists(),
        "attachPath": attach_path(),
        "freePorts": ports.iter().filter(|entry| entry.status == FREE_PORT).count()
    })
}

fn handle(request: &Value) -> Value {
    match request["op"].as_str().unwrap_or("") {
        "attach" => do_attach(request),
        "detach" => do_detach(request),
        "probe" => do_probe(),
        other => json!({"ok": false, "error": format!("未知操作 {other}")}),
    }
}

fn peer_uid(stream: &UnixStream) -> Option<u32> {
    // SAFETY: 对已连接的 unix socket 取 SO_PEERCRED，结构体与长度按 libc 定义
    unsafe {
        let mut credentials: libc::ucred = std::mem::zeroed();
        let mut length = std::mem::size_of::<libc::ucred>() as libc::socklen_t;
        let result = libc::getsockopt(
            stream.as_raw_fd(),
            libc::SOL_SOCKET,
            libc::SO_PEERCRED,
            (&mut credentials as *mut libc::ucred).cast::<libc::c_void>(),
            &mut length,
        );
        if result == 0 {
            Some(credentials.uid)
        } else {
            None
        }
    }
}

/// 常驻服务：由 systemd 以 root 启动
pub fn serve(path: &str, allow_uid: Option<u32>) -> std::io::Result<()> {
    // SAFETY: geteuid 无副作用
    if (unsafe { libc::geteuid() }) != 0 {
        eprintln!("[keysim-attachd] 必须以 root 运行（systemd 服务或 sudo）");
        std::process::exit(1);
    }
    if let Some(parent) = Path::new(path).parent() {
        fs::create_dir_all(parent)?;
    }
    let _ = fs::remove_file(path);
    let listener = UnixListener::bind(path)?;
    fs::set_permissions(path, <fs::Permissions as std::os::unix::fs::PermissionsExt>::from_mode(0o660))?;
    if let Some(uid) = allow_uid {
        // SAFETY: path 是刚创建的 socket 文件，chown 只改属主
        unsafe {
            let c_path = std::ffi::CString::new(path).unwrap_or_default();
            libc::chown(c_path.as_ptr(), uid, u32::MAX);
        }
    }
    eprintln!(
        "[keysim-attachd] 就绪：{path}（只接受 uid={} 的调用方）",
        allow_uid.map(|uid| uid.to_string()).unwrap_or_else(|| "任意".into())
    );

    for incoming in listener.incoming() {
        let Ok(mut stream) = incoming else { continue };
        let uid = peer_uid(&stream);
        let response = match (allow_uid, uid) {
            (Some(expected), Some(actual)) if actual != expected && actual != 0 => {
                json!({"ok": false, "error": format!("拒绝 uid={actual}")})
            }
            _ => {
                let mut line = String::new();
                match BufReader::new(stream.try_clone()?).read_line(&mut line) {
                    Ok(_) => match serde_json::from_str::<Value>(line.trim()) {
                        Ok(request) => handle(&request),
                        Err(error) => json!({"ok": false, "error": format!("请求不是合法 JSON：{error}")}),
                    },
                    Err(error) => json!({"ok": false, "error": format!("读取请求失败：{error}")}),
                }
            }
        };
        let _ = stream.write_all(format!("{response}\n").as_bytes());
    }
    Ok(())
}

/// 客户端：向助手要一次操作
pub fn ask(request: Value) -> Value {
    ask_at(&socket_path(), request)
}

/// 指定 socket 路径的版本：测试与多实例场景用，避免改进程环境变量
pub fn ask_at(path: &str, request: Value) -> Value {
    let mut stream = match UnixStream::connect(path) {
        Ok(stream) => stream,
        Err(error) => {
            let reason = if error.kind() == std::io::ErrorKind::NotFound {
                format!("root 助手未安装（{path}）：先执行一次 {}", crate::install::install_command())
            } else {
                format!("root 助手连接失败：{error}")
            };
            return json!({"ok": false, "error": reason});
        }
    };
    let _ = stream.set_read_timeout(Some(std::time::Duration::from_secs(30)));
    if let Err(error) = stream.write_all(format!("{request}\n").as_bytes()) {
        return json!({"ok": false, "error": format!("发送失败：{error}")});
    }
    let mut line = String::new();
    match BufReader::new(stream).read_line(&mut line) {
        Ok(0) => json!({"ok": false, "error": "root 助手提前关闭了连接"}),
        Ok(_) => serde_json::from_str(line.trim())
            .unwrap_or_else(|error| json!({"ok": false, "error": format!("助手响应不是 JSON：{error}")})),
        Err(error) => json!({"ok": false, "error": format!("读取响应失败：{error}")}),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    /// 未装助手时必须给出明确原因与装法，而不是静默失败
    fn missing_helper_reports_how_to_install() {
        let response = ask_at("/tmp/keysim-nonexistent-helper.sock", json!({"op": "probe"}));
        assert_eq!(response["ok"], false);
        let error = response["error"].as_str().unwrap_or_default();
        assert!(error.contains("未安装") && error.contains("install-helper"), "{error}");
    }

    #[test]
    /// 未知操作不panic，按错误返回
    fn unknown_operation_is_rejected() {
        let response = handle(&json!({"op": "nope"}));
        assert_eq!(response["ok"], false);
        assert!(response["error"].as_str().unwrap().contains("未知操作"));
    }
}
