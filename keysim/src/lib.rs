//! keysim：手键 / 电子键拍发模拟器与虚拟串口台。
//!
//! 一个核心（真实节拍的事件时间轴）+ 四条注入通道（内核级虚拟串口 / PTY 设备 /
//! 18765 桌面桥接 / 浏览器页面注入）+ 后端载荷与 REST 驱动。
//!
//! 口径来源全部是仓内生产代码（前端 useTraffic.js / WebSerial.js / handKeyTrain.js、
//! 后端 GeneralTickerPatService.java / GeneralKeyPatService.java）；
//! tests/ 下的对表测试逐项核对，任何一侧漂移即失败。

pub mod attachd;
pub mod bridge;
pub mod browser;
pub mod console;
pub mod faults;
pub mod install;
pub mod kernel;
pub mod keying;
pub mod morse;
pub mod pty;
pub mod rest;
pub mod serial;
pub mod sinks;
pub mod timeline;
pub mod usbip;
pub mod ws;

/// 桌面桥接端口：被测应用固定连 ws://localhost:18765/echo（MessageWebSocket.js:115）
pub const BRIDGE_PORT: u16 = 18765;
pub const CONSOLE_PORT: u16 = 18700;
