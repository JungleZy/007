//! keysim：手键 / 电子键拍发模拟器与虚拟串口台。
//!
//! 一个核心（真实节拍的事件时间轴）+ 四条注入通道（内核级虚拟串口 / PTY 设备 /
//! 浏览器页面注入）+ 后端载荷与 REST 驱动。
//!
//! 口径来源全部是仓内生产代码（前端 useTraffic.js / WebSerial.js / handKeyTrain.js、
//! 后端 GeneralTickerPatService.java / GeneralKeyPatService.java）；
//! tests/ 下的对表测试逐项核对，任何一侧漂移即失败。

/// root 助手用 unix socket + SO_PEERCRED，仅类 Unix 平台
#[cfg(unix)]
pub mod attachd;
pub mod browser;
pub mod console;
pub mod faults;
pub mod install;
pub mod kernel;
pub mod keying;
pub mod morse;
/// PTY 是类 Unix 概念；Windows 走 com0com 的真实 COM 口
#[cfg(unix)]
pub mod pty;
pub mod rest;
pub mod serial;
pub mod sinks;
pub mod timeline;
pub mod usbip;
pub mod ws;

/// 平台名：state.platform 与各处文案分支都取这里，不在业务代码里写 cfg
pub const PLATFORM: &str = if cfg!(windows) { "windows" } else { "linux" };

/// 设备通道（PTY）是否由本平台提供
pub const DEVICE_CHANNEL_SUPPORTED: bool = cfg!(unix);

/// 本平台是否需要 root 助手：Linux 的 USB/IP 挂载要，Windows 装 com0com 即可
pub const HELPER_REQUIRED: bool = cfg!(target_os = "linux");

/// 额外的设备符号链接默认值（Windows 无此概念）
pub fn default_device_links() -> Vec<std::path::PathBuf> {
    #[cfg(unix)]
    {
        pty::default_links()
    }
    #[cfg(not(unix))]
    {
        Vec::new()
    }
}

pub const CONSOLE_PORT: u16 = 18700;
