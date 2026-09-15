//! 内核级虚拟串口后端：让浏览器的串口选择框与桌面串口列表都能**直接选中**这个口。
//!
//! 为什么必须内核出手：PTY（/dev/pts/N）只是字符设备，内核不把它注册成 udev 的 tty
//! 设备，而 Chromium 的 Web Serial 按 udev tty 枚举，所以 PTY 永远进不了浏览器的选择框。
//!
//! - linux-usbip   ：用户态模拟 USB CDC-ACM（usbip.rs），经 vhci_hcd 挂上总线 -> /dev/ttyACM*
//! - linux-gadget  ：dummy_hcd + g_serial -> /dev/ttyGS0
//! - linux-tty0tty ：成对 /dev/tnt0 <-> /dev/tnt1
//! - windows-com0com：成对 COM 口（CNCA90 <-> COM91）。Windows 上这是唯一路径，
//!   也是最省事的一条：装一次 com0com 驱动，浏览器与桌面程序都能直接选到 COM91
//!
//! 不可用时绝不假装成功：逐项给出原因与"装了什么就能用"。

use std::fs;
use std::io::Write;
use std::path::Path;
use std::process::Command;
use std::sync::Arc;

use serde_json::{json, Value};

#[cfg(target_os = "linux")]
use crate::attachd;
#[cfg(target_os = "linux")]
use crate::usbip;

#[derive(Debug, Clone)]
pub struct Verdict {
    pub id: &'static str,
    pub title: &'static str,
    /// 被测程序（浏览器选择框 / 桌面串口列表）应该选的那个口
    pub selectable: String,
    pub available: bool,
    pub reason: Option<String>,
    pub install: Option<String>,
}

impl Verdict {
    pub fn to_json(&self) -> Value {
        json!({
            "id": self.id,
            "title": self.title,
            "selectable": self.selectable,
            "available": self.available,
            "reason": self.reason,
            "install": self.install
        })
    }
}

/// 我们写入的那一端
pub enum Writer {
    /// 真实设备节点（gadget / tty0tty / com0com）
    Device(fs::File),
    /// 用户态 USB 设备模拟器（仅 Linux 的 USB/IP 路径）
    #[cfg(target_os = "linux")]
    Emulator(usbip::Emulator),
}

impl Writer {
    pub fn write(&mut self, bytes: &[u8]) -> std::io::Result<()> {
        match self {
            Writer::Device(file) => {
                file.write_all(bytes)?;
                file.flush()
            }
            #[cfg(target_os = "linux")]
            Writer::Emulator(emulator) => {
                emulator.write(bytes);
                Ok(())
            }
        }
    }
}

pub struct Started {
    pub id: &'static str,
    pub title: &'static str,
    pub writer: Writer,
    /// 被测程序应选的设备
    pub selectable: String,
    /// 桌面壳串口下拉能看到的别名（它只认 /dev/ttyUSBn）
    pub desktop_alias: Option<String>,
    pub vhci_port: Option<u64>,
}

#[cfg(target_os = "linux")]
fn has_module(name: &str) -> bool {
    Command::new("modinfo")
        .arg(name)
        .output()
        .map(|output| output.status.success())
        .unwrap_or(false)
}

#[cfg(target_os = "linux")]
fn module_loaded(name: &str) -> bool {
    fs::read_to_string("/proc/modules")
        .map(|text| text.lines().any(|line| line.starts_with(&format!("{name} "))))
        .unwrap_or(false)
}

#[cfg(unix)]
fn is_root() -> bool {
    // SAFETY: geteuid 无副作用
    (unsafe { libc::geteuid() }) == 0
}

#[cfg(target_os = "linux")]
fn is_wsl() -> bool {
    fs::read_to_string("/proc/version")
        .map(|text| {
            let lowered = text.to_ascii_lowercase();
            lowered.contains("microsoft") || lowered.contains("wsl")
        })
        .unwrap_or(false)
}

#[cfg(unix)]
fn graphical_session() -> bool {
    std::env::var("DISPLAY").is_ok() || std::env::var("WAYLAND_DISPLAY").is_ok()
}

#[cfg(unix)]
fn have_pkexec() -> bool {
    Command::new("sh")
        .args(["-c", "command -v pkexec"])
        .output()
        .map(|output| output.status.success())
        .unwrap_or(false)
}

/// 提权通道：root 直跑；否则 pkexec 弹一次系统授权框（仅类 Unix）
#[cfg(unix)]
pub fn elevation() -> Result<Option<&'static str>, String> {
    if is_root() {
        return Ok(None);
    }
    if have_pkexec() && graphical_session() {
        return Ok(Some("pkexec"));
    }
    Err("需要 root 或可弹框的 pkexec".into())
}

#[cfg(unix)]
fn run_privileged(program: &str, args: &[&str]) -> Result<(), String> {
    let prefix = elevation()?;
    let output = match prefix {
        Some(tool) => {
            let mut command = Command::new(tool);
            command.arg(program).args(args);
            command.output()
        }
        None => Command::new(program).args(args).output(),
    }
    .map_err(|error| format!("执行 {program} 失败：{error}"))?;
    if output.status.success() {
        Ok(())
    } else {
        Err(String::from_utf8_lossy(&output.stderr).trim().to_string())
    }
}

// ------------------------------------------------------------------ USB/IP

#[cfg(target_os = "linux")]
const USBIP_TITLE: &str = "USB/IP + vhci_hcd（用户态模拟 USB 串口，内核当真设备）";

#[cfg(target_os = "linux")]
fn probe_usbip() -> Verdict {
    let selectable = "/dev/ttyACM*".to_string();
    let missing: Vec<&str> = ["vhci-hcd", "cdc-acm"].into_iter().filter(|name| !has_module(name)).collect();
    if !missing.is_empty() {
        return Verdict {
            id: "linux-usbip",
            title: USBIP_TITLE,
            selectable,
            available: false,
            reason: Some(format!("内核未提供 {} 模块", missing.join(" / "))),
            install: Some("安装内核附加模块包后重试".into()),
        };
    }
    let helper = attachd::ask(json!({"op": "probe"}));
    if helper["ok"] != true {
        return Verdict {
            id: "linux-usbip",
            title: USBIP_TITLE,
            selectable,
            available: false,
            reason: helper["error"].as_str().map(str::to_string),
            install: Some(format!("装一次 root 助手：{}（之后网页上点开启即可，不再输密码）", crate::install::install_command())),
        };
    }
    Verdict { id: "linux-usbip", title: USBIP_TITLE, selectable, available: true, reason: None, install: None }
}

/// 被测程序能不能真的打开这个设备节点（不是"存在"，是"可读可写"）。
///
/// 返回 None = 本平台无法在不打开设备的前提下判断：Windows 的 COM 口是独占打开，
/// 试开一次会把正在用它的程序挤掉，所以不猜。
pub fn openable(path: &str) -> Option<bool> {
    #[cfg(unix)]
    {
        // SAFETY: access 只查权限，不打开文件
        unsafe {
            let Ok(c_path) = std::ffi::CString::new(path) else { return Some(false) };
            Some(libc::access(c_path.as_ptr(), libc::R_OK | libc::W_OK) == 0)
        }
    }
    #[cfg(not(unix))]
    {
        let _ = path;
        None
    }
}

#[cfg(target_os = "linux")]
fn existing_acm() -> Vec<String> {
    fs::read_dir("/dev")
        .map(|entries| {
            entries
                .filter_map(Result::ok)
                .map(|entry| entry.file_name().to_string_lossy().to_string())
                .filter(|name| name.starts_with("ttyACM"))
                .collect()
        })
        .unwrap_or_default()
}

#[cfg(target_os = "linux")]
fn start_usbip(log: Arc<dyn Fn(String) + Send + Sync>) -> Result<Started, String> {
    let before = existing_acm();
    let sink_log = Arc::clone(&log);
    let emulator = usbip::start(
        0,
        Arc::new(move |event| match event {
            usbip::Event::Attached => sink_log("内核已接管 USB/IP 连接".into()),
            usbip::Event::Detached => sink_log("内核已断开 USB/IP 连接".into()),
            usbip::Event::Rx(hex) => sink_log(format!("主机往串口写入：{hex}")),
            usbip::Event::Log(message) => sink_log(format!("模拟器：{message}")),
        }),
    )
    .map_err(|error| format!("启动 USB/IP 模拟器失败：{error}"))?;

    let attached = attachd::ask(json!({
        "op": "attach",
        "port": emulator.usbip_port,
        "busid": emulator.busid
    }));
    if attached["ok"] != true {
        return Err(attached["error"].as_str().unwrap_or("attach 失败").to_string());
    }
    let vhci_port = attached["vhciPort"].as_u64();

    // 内核枚举 + cdc-acm 绑定要一点时间
    for _ in 0..40 {
        let now = existing_acm();
        if let Some(name) = now.iter().find(|name| !before.contains(name)) {
            let path = format!("/dev/{name}");
            // 内核默认把 /dev/ttyACM* 给 root:dialout 0660。被测程序（浏览器、桌面壳）
            // 通常不在 dialout 组里，不认领就是"设备在但打不开"。
            let granted = attachd::ask(json!({"op": "grant", "device": name}));
            match (granted["ok"] == true, openable(&path) == Some(true)) {
                (_, true) => log(format!("虚拟串口可直接打开：{path}")),
                (true, false) => log(format!("已认领 {path}，但当前进程仍打不开它（属主已改，稍后重试或重开设备）")),
                (false, false) => log(format!(
                    "{path} 已出现但当前用户打不开（内核默认 root:dialout）：{}。重新执行一次 {} 可装上 udev 规则永久解决",
                    granted["error"].as_str().unwrap_or("认领失败"),
                    crate::install::install_command()
                )),
            }
            // 桌面壳只列 /dev/ttyUSBn，给它一个别名，否则下拉里根本看不到
            let aliased = attachd::ask(json!({"op": "symlink", "device": name}));
            let desktop_alias = aliased["alias"].as_str().map(str::to_string);
            match &desktop_alias {
                Some(alias) => log(format!("桌面串口列表可见：{alias} → {path}")),
                None => log(format!(
                    "未能建立桌面别名（桌面壳只认 /dev/ttyUSBn）：{}",
                    aliased["error"].as_str().unwrap_or("未知原因")
                )),
            }
            return Ok(Started {
                id: "linux-usbip",
                title: USBIP_TITLE,
                writer: Writer::Emulator(emulator),
                selectable: path,
                desktop_alias,
                vhci_port,
            });
        }
        std::thread::sleep(std::time::Duration::from_millis(200));
    }
    if let Some(port) = vhci_port {
        let _ = attachd::ask(json!({"op": "detach", "vhciPort": port}));
    }
    Err("已 attach 但没有出现 /dev/ttyACM*（cdc-acm 是否可用？）".into())
}

// ------------------------------------------------------------------ gadget / tty0tty / com0com

#[cfg(target_os = "linux")]
const GADGET_DEVICE: &str = "/dev/ttyGS0";
#[cfg(target_os = "linux")]
const TTY0TTY_OURS: &str = "/dev/tnt0";
#[cfg(target_os = "linux")]
const TTY0TTY_THEIRS: &str = "/dev/tnt1";

#[cfg(target_os = "linux")]
fn probe_gadget() -> Verdict {
    let title = "USB gadget（dummy_hcd + g_serial）";
    let selectable = GADGET_DEVICE.to_string();
    let missing: Vec<&str> = ["dummy_hcd", "g_serial"].into_iter().filter(|name| !has_module(name)).collect();
    if !missing.is_empty() {
        let install = if is_wsl() {
            "WSL2 默认内核没编 USB gadget：需自建内核（CONFIG_USB_DUMMY_HCD=m、CONFIG_USB_G_SERIAL=m）并在 .wslconfig 里 kernel= 指向它"
        } else {
            "安装内核附加模块包，如 Debian/Ubuntu：sudo apt install linux-modules-extra-$(uname -r)"
        };
        return Verdict {
            id: "linux-gadget",
            title,
            selectable,
            available: false,
            reason: Some(format!("内核未提供 {} 模块", missing.join(" / "))),
            install: Some(install.into()),
        };
    }
    match elevation() {
        Ok(_) => Verdict { id: "linux-gadget", title, selectable, available: true, reason: None, install: None },
        Err(reason) => Verdict { id: "linux-gadget", title, selectable, available: false, reason: Some(reason), install: None },
    }
}

#[cfg(target_os = "linux")]
fn start_gadget() -> Result<Started, String> {
    for module in ["dummy_hcd", "g_serial"] {
        if module_loaded(module) {
            continue;
        }
        run_privileged("modprobe", &[module]).map_err(|error| format!("加载 {module} 失败：{error}"))?;
    }
    for _ in 0..20 {
        if Path::new(GADGET_DEVICE).exists() {
            let file = fs::OpenOptions::new()
                .read(true)
                .write(true)
                .open(GADGET_DEVICE)
                .map_err(|error| format!("打开 {GADGET_DEVICE} 失败：{error}"))?;
            return Ok(Started {
                id: "linux-gadget",
                title: "USB gadget（dummy_hcd + g_serial）",
                writer: Writer::Device(file),
                selectable: GADGET_DEVICE.into(),
                desktop_alias: None,
                vhci_port: None,
            });
        }
        std::thread::sleep(std::time::Duration::from_millis(100));
    }
    Err(format!("模块已加载但没出现 {GADGET_DEVICE}"))
}

#[cfg(target_os = "linux")]
fn probe_tty0tty() -> Verdict {
    let title = "tty0tty（成对虚拟串口 /dev/tnt0 <-> /dev/tnt1）";
    let selectable = TTY0TTY_THEIRS.to_string();
    if !has_module("tty0tty") {
        return Verdict {
            id: "linux-tty0tty",
            title,
            selectable,
            available: false,
            reason: Some("未安装 tty0tty 内核模块".into()),
            install: Some("需要内核头文件与 dkms：sudo apt install dkms linux-headers-$(uname -r)，再按 tty0tty 项目说明 dkms install".into()),
        };
    }
    if Path::new(TTY0TTY_OURS).exists() {
        return Verdict { id: "linux-tty0tty", title, selectable, available: true, reason: None, install: None };
    }
    match elevation() {
        Ok(_) => Verdict { id: "linux-tty0tty", title, selectable, available: true, reason: None, install: None },
        Err(reason) => Verdict { id: "linux-tty0tty", title, selectable, available: false, reason: Some(reason), install: None },
    }
}

#[cfg(target_os = "linux")]
fn start_tty0tty() -> Result<Started, String> {
    if !Path::new(TTY0TTY_OURS).exists() {
        run_privileged("modprobe", &["tty0tty"]).map_err(|error| format!("加载 tty0tty 失败：{error}"))?;
    }
    let file = fs::OpenOptions::new()
        .read(true)
        .write(true)
        .open(TTY0TTY_OURS)
        .map_err(|error| format!("打开 {TTY0TTY_OURS} 失败：{error}"))?;
    Ok(Started {
        id: "linux-tty0tty",
        title: "tty0tty（成对虚拟串口 /dev/tnt0 <-> /dev/tnt1）",
        writer: Writer::Device(file),
        selectable: TTY0TTY_THEIRS.into(),
        desktop_alias: None,
        vhci_port: None,
    })
}

#[cfg(windows)]
fn com0com_setupc() -> Option<String> {
    for variable in ["ProgramFiles(x86)", "ProgramFiles"] {
        if let Ok(base) = std::env::var(variable) {
            let candidate = format!("{base}\\com0com\\setupc.exe");
            if Path::new(&candidate).exists() {
                return Some(candidate);
            }
        }
    }
    None
}

#[cfg(windows)]
fn probe_com0com() -> Verdict {
    let title = "com0com（成对虚拟 COM 口）";
    let selectable = "COM91".to_string();
    match com0com_setupc() {
        Some(_) => Verdict { id: "windows-com0com", title, selectable, available: true, reason: None, install: None },
        None => Verdict {
            id: "windows-com0com",
            title,
            selectable,
            available: false,
            reason: Some("未检测到 com0com".into()),
            install: Some("安装 com0com（https://sourceforge.net/projects/com0com/）后即可；浏览器与桌面程序都能直接选中".into()),
        },
    }
}

#[cfg(windows)]
fn start_com0com() -> Result<Started, String> {
    let tool = com0com_setupc().ok_or("未检测到 com0com")?;
    let output = Command::new(&tool)
        .args(["install", "PortName=CNCA90", "PortName=COM91"])
        .output()
        .map_err(|error| format!("执行 setupc 失败：{error}"))?;
    if !output.status.success() {
        return Err(String::from_utf8_lossy(&output.stderr).trim().to_string());
    }
    let file = fs::OpenOptions::new()
        .read(true)
        .write(true)
        .open("\\\\.\\CNCA90")
        .map_err(|error| format!("打开 CNCA90 失败：{error}"))?;
    Ok(Started {
        id: "windows-com0com",
        title: "com0com（成对虚拟 COM 口）",
        writer: Writer::Device(file),
        selectable: "COM91".into(),
        desktop_alias: None,
        vhci_port: None,
    })
}

/// 停用内核级后端的开关。
///
/// 内核级后端会真的往内核挂 USB 设备、改 /dev 下的别名，是**机器级副作用**：
/// 自动化测试里跑一遍，就会把开发机上正在用的那个实例的设备与别名搅乱。
/// e2e 测试进程会设 KEYSIM_NO_KERNEL=1（见 tests/console_e2e.rs），只用
/// PTY + 桥接 + 注入三条通道；单元测试如需同样隔离，也要自行设置。
pub fn disabled_by_env() -> bool {
    std::env::var("KEYSIM_NO_KERNEL").map(|value| value != "0").unwrap_or(false)
}

/// 逐个探测，顺序即优先级；只列本平台真的可能提供的后端，
/// 不再把三条 Linux 路径摆在 Windows 面前说"仅 Linux"
pub fn probe_all() -> Vec<Verdict> {
    if disabled_by_env() {
        return vec![Verdict {
            id: "disabled",
            title: "内核级后端已停用（KEYSIM_NO_KERNEL）",
            selectable: "-".into(),
            available: false,
            reason: Some("已按 KEYSIM_NO_KERNEL 停用：避免测试往内核挂设备、动 /dev 别名".into()),
            install: None,
        }];
    }
    #[cfg(target_os = "linux")]
    {
        vec![probe_usbip(), probe_gadget(), probe_tty0tty()]
    }
    #[cfg(windows)]
    {
        vec![probe_com0com()]
    }
    #[cfg(not(any(target_os = "linux", windows)))]
    {
        Vec::new()
    }
}

/// 启动第一个可用的后端；都不可用则返回每条的原因
pub fn start(log: Arc<dyn Fn(String) + Send + Sync>) -> Result<Started, Vec<String>> {
    if disabled_by_env() {
        return Err(vec!["内核级后端已按 KEYSIM_NO_KERNEL 停用".to_string()]);
    }
    // log 只有 USB/IP 后端会用到（它要把模拟器事件转出来）
    #[cfg(not(target_os = "linux"))]
    let _ = &log;
    let mut reasons = Vec::new();
    for verdict in probe_all() {
        if !verdict.available {
            reasons.push(format!("{}: {}", verdict.id, verdict.reason.unwrap_or_default()));
            continue;
        }
        let started = match verdict.id {
            #[cfg(target_os = "linux")]
            "linux-usbip" => start_usbip(Arc::clone(&log)),
            #[cfg(target_os = "linux")]
            "linux-gadget" => start_gadget(),
            #[cfg(target_os = "linux")]
            "linux-tty0tty" => start_tty0tty(),
            #[cfg(windows)]
            "windows-com0com" => start_com0com(),
            other => Err(format!("未知后端 {other}")),
        };
        match started {
            Ok(started) => return Ok(started),
            Err(error) => reasons.push(format!("{}: {error}", verdict.id)),
        }
    }
    Err(reasons)
}

pub fn stop(started: &Started) {
    #[cfg(target_os = "linux")]
    {
        if started.desktop_alias.is_some() {
            let _ = attachd::ask(json!({"op": "symlink", "remove": true}));
        }
        if let Some(port) = started.vhci_port {
            let _ = attachd::ask(json!({"op": "detach", "vhciPort": port}));
        }
    }
    #[cfg(windows)]
    if started.id == "windows-com0com" {
        if let Some(tool) = com0com_setupc() {
            let _ = Command::new(tool).args(["remove", "0"]).output();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    /// 四个后端都必须给出明确结论；不可用要有原因，缺前置要给可执行装法
    fn every_backend_reports_a_verdict() {
        let verdicts = probe_all();
        let expected = if disabled_by_env() { 1 } else if cfg!(target_os = "linux") { 3 } else { 1 };
        assert_eq!(verdicts.len(), expected, "本平台的后端清单数量不对");
        for verdict in verdicts {
            assert!(!verdict.selectable.is_empty(), "{} 没说被测程序该选哪个口", verdict.id);
            if !verdict.available {
                let reason = verdict.reason.clone().unwrap_or_default();
                assert!(!reason.is_empty(), "{} 不可用却没说原因", verdict.id);
                if reason.contains("模块") || reason.contains("未安装") || reason.contains("未检测到") {
                    assert!(verdict.install.is_some(), "{} 缺前置却没给装法", verdict.id);
                }
            }
        }
    }
}
