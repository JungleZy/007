//! 一次性安装。
//!
//! Linux：把自己注册成 systemd 服务（`keysim attachd`）并装一条 udev 规则。
//! 之后网页上点「开启虚拟串口」不再需要密码——特权只留在助手里，
//! 且助手只做 vhci_hcd 的 attach/detach 与自家设备的认领（attachd.rs 说明了攻击面）。
//!
//! Windows：不需要 root 助手。虚拟串口由 com0com 驱动提供，装一次 com0com 即可，
//! keysim 只调它的 setupc.exe 建/删端口对（kernel.rs）。

#[cfg(target_os = "linux")]
use std::process::Command;

use serde_json::{json, Value};

#[cfg(target_os = "linux")]
const UNIT_PATH: &str = "/etc/systemd/system/keysim-attachd.service";
#[cfg(target_os = "linux")]
const UDEV_PATH: &str = "/etc/udev/rules.d/70-keysim-virtual-serial.rules";

/// com0com 官方下载页：Windows 上的"装一次"就是装它
pub const COM0COM_URL: &str = "https://sourceforge.net/projects/com0com/";

fn self_path() -> String {
    std::env::current_exe()
        .map(|path| path.display().to_string())
        .unwrap_or_else(|_| "keysim".into())
}

/// 安装后的稳定路径：单元文件绝不能指向 cargo 的构建目录，
/// 否则一次 `cargo clean`（或换分支重建）就让 root 服务指向不存在的文件。
#[cfg(target_os = "linux")]
const INSTALL_PATH: &str = "/usr/local/bin/keysim";

#[cfg(target_os = "linux")]
fn is_build_artifact(path: &str) -> bool {
    let normalized = path.replace('\\', "/");
    normalized.contains("/target/release/") || normalized.contains("/target/debug/")
}

/// 原子替换可执行文件：先写同目录的临时文件，再 rename 覆盖。
///
/// 不能直接 fs::copy 到目标：升级时目标正被 systemd 跑着，写它会得到
/// ETXTBSY（Text file busy）。rename 只换目录项，老 inode 由正在运行的
/// 进程继续持有，重启服务后才真正释放。
pub fn replace_executable(source: &str, target: &str) -> Result<(), String> {
    if let Some(parent) = std::path::Path::new(target).parent() {
        std::fs::create_dir_all(parent).map_err(|error| format!("创建 {} 失败：{error}", parent.display()))?;
    }
    // 临时文件必须与目标同目录：跨文件系统 rename 会失败
    let staging = format!("{target}.new-{}", std::process::id());
    std::fs::copy(source, &staging).map_err(|error| format!("写 {staging} 失败：{error}"))?;
    // Windows 的 rename 不覆盖已存在的目标，而运行中的 exe 也不能删：
    // 先把旧文件改名让开（这在 Windows 上是允许的），进程退出后再清理。
    #[cfg(windows)]
    if std::path::Path::new(target).exists() {
        let retired = format!("{target}.old-{}", std::process::id());
        let _ = std::fs::remove_file(&retired);
        if let Err(error) = std::fs::rename(target, &retired) {
            let _ = std::fs::remove_file(&staging);
            return Err(format!("让开旧文件 {target} 失败：{error}"));
        }
    }
    // 可执行位只有类 Unix 需要显式设置；Windows 的可执行性由扩展名与 ACL 决定
    #[cfg(unix)]
    let prepared = std::fs::set_permissions(
        &staging,
        <std::fs::Permissions as std::os::unix::fs::PermissionsExt>::from_mode(0o755),
    )
    .map_err(|error| format!("设置 {staging} 权限失败：{error}"));
    #[cfg(not(unix))]
    let prepared: Result<(), String> = Ok(());

    let result = prepared
        .and_then(|()| std::fs::rename(&staging, target).map_err(|error| format!("替换 {target} 失败：{error}")));
    if result.is_err() {
        let _ = std::fs::remove_file(&staging);
    }
    result
}

/// 从构建目录安装时，先把自己复制到 INSTALL_PATH，再让单元指向它
#[cfg(target_os = "linux")]
fn stable_executable() -> Result<String, String> {
    let current = self_path();
    if !is_build_artifact(&current) {
        return Ok(current);
    }
    replace_executable(&current, INSTALL_PATH)?;
    Ok(INSTALL_PATH.to_string())
}

/// 给用户的可复制命令：一律用绝对路径，别假设 keysim 在 PATH 里
pub fn install_command() -> String {
    if cfg!(target_os = "linux") {
        format!("sudo {} install-helper", self_path())
    } else if cfg!(windows) {
        format!("安装 com0com（{COM0COM_URL}）后无需其他步骤")
    } else {
        format!("{} install-helper", self_path())
    }
}

/// 助手是否已装好（Windows 恒为"不需要"，因此也恒为未安装）
pub fn helper_installed() -> bool {
    #[cfg(target_os = "linux")]
    {
        crate::attachd::ask(json!({"op": "probe"}))["ok"] == true
    }
    #[cfg(not(target_os = "linux"))]
    {
        false
    }
}

#[cfg(target_os = "linux")]
fn target_user() -> (String, u32) {
    let name = std::env::var("SUDO_USER")
        .or_else(|_| std::env::var("PKEXEC_UID").map(|_| String::new()))
        .ok()
        .filter(|value| !value.is_empty())
        .or_else(|| std::env::var("USER").ok())
        .unwrap_or_else(|| "root".into());
    let uid = std::env::var("PKEXEC_UID")
        .ok()
        .and_then(|value| value.parse::<u32>().ok())
        .or_else(|| {
            Command::new("id")
                .args(["-u", &name])
                .output()
                .ok()
                .filter(|output| output.status.success())
                .and_then(|output| String::from_utf8_lossy(&output.stdout).trim().parse().ok())
        })
        // SAFETY: getuid 无副作用
        .unwrap_or_else(|| unsafe { libc::getuid() });
    (name, uid)
}

/// udev 规则只匹配 keysim 自己的虚拟设备（VID:PID = 1209:5253），
/// 把节点属主设成安装者。这样不必把用户塞进 dialout 组，
/// 也不会影响任何真实串口的权限。
#[cfg(target_os = "linux")]
fn udev_text(user: &str) -> String {
    format!(
        "# keysim 虚拟串口（USB/IP + CDC-ACM）：只认自家 VID:PID，交给 {user}\n\
         SUBSYSTEM==\"tty\", ATTRS{{idVendor}}==\"1209\", ATTRS{{idProduct}}==\"5253\", \
         OWNER=\"{user}\", MODE=\"0660\", TAG+=\"uaccess\", SYMLINK+=\"{alias}\"\n",
        alias = crate::attachd::DESKTOP_ALIAS
    )
}

#[cfg(target_os = "linux")]
fn unit_text(executable: &str, uid: u32) -> String {
    format!(
        "[Unit]\n\
         Description=keysim USB/IP attach helper（只做 vhci_hcd attach/detach）\n\
         After=network.target\n\n\
         [Service]\n\
         Type=simple\n\
         ExecStart={executable} attachd --socket {socket} --uid {uid}\n\
         Restart=on-failure\n\
         RuntimeDirectory=keysim\n\n\
         [Install]\n\
         WantedBy=multi-user.target\n",
        socket = crate::attachd::DEFAULT_SOCKET
    )
}

/// root 下真正落地安装
#[cfg(target_os = "linux")]
pub fn write_unit_as_root() -> Result<Value, String> {
    let executable = stable_executable()?;
    let (user, uid) = target_user();
    std::fs::write(UNIT_PATH, unit_text(&executable, uid)).map_err(|error| format!("写 {UNIT_PATH} 失败：{error}"))?;
    // restart 而非 enable --now：重装时服务往往已在跑，enable --now 不会换掉旧进程，
    // 那样新装的 helper 代码（如 grant 操作）根本不会生效。
    for args in [
        vec!["daemon-reload"],
        vec!["enable", "keysim-attachd.service"],
        vec!["restart", "keysim-attachd.service"],
    ] {
        let output = Command::new("systemctl")
            .args(&args)
            .output()
            .map_err(|error| format!("执行 systemctl {} 失败：{error}", args.join(" ")))?;
        if !output.status.success() {
            return Err(format!(
                "systemctl {} 失败：{}",
                args.join(" "),
                String::from_utf8_lossy(&output.stderr).trim()
            ));
        }
    }
    // udev 规则：让以后每次挂载出来的节点直接归安装者，不用再认领
    let udev = std::fs::write(UDEV_PATH, udev_text(&user))
        .map_err(|error| format!("写 {UDEV_PATH} 失败：{error}"));
    if udev.is_ok() {
        let _ = Command::new("udevadm").args(["control", "--reload-rules"]).output();
        let _ = Command::new("udevadm")
            .args(["trigger", "--subsystem-match=tty", "--action=change"])
            .output();
    }
    // 未装 vhci-hcd 时这一步失败不致命：attach 时还会再试一次
    let _ = Command::new("modprobe").arg("vhci-hcd").output();
    Ok(json!({
        "ok": true,
        "unit": UNIT_PATH,
        "udev": if udev.is_ok() { Value::from(UDEV_PATH) } else { Value::Null },
        "binary": executable,
        "socket": crate::attachd::DEFAULT_SOCKET,
        "user": user,
        "uid": uid
    }))
}

/// 提权路线：只看三个事实，便于单独验证（避免测试去改进程环境变量）
#[derive(Debug, PartialEq, Eq)]
pub enum Route {
    /// 已是 root，直接落地
    Direct,
    /// 非 root，但能弹系统授权框
    Pkexec,
    /// 都不行，把命令交给用户
    Manual,
}

pub fn route(is_root: bool, has_pkexec: bool, graphical: bool) -> Route {
    match (is_root, has_pkexec && graphical) {
        (true, _) => Route::Direct,
        (false, true) => Route::Pkexec,
        (false, false) => Route::Manual,
    }
}

/// 网页/CLI 调用：Linux 上装 systemd 服务 + udev 规则
#[cfg(target_os = "linux")]
pub fn install_helper() -> Value {
    // SAFETY: geteuid 无副作用
    let is_root = (unsafe { libc::geteuid() }) == 0;
    let has_pkexec = Command::new("sh")
        .args(["-c", "command -v pkexec"])
        .output()
        .map(|output| output.status.success())
        .unwrap_or(false);
    let graphical = std::env::var("DISPLAY").is_ok() || std::env::var("WAYLAND_DISPLAY").is_ok();
    let executable = self_path();
    let manual = install_command();
    match route(is_root, has_pkexec, graphical) {
        Route::Direct => {
            return match write_unit_as_root() {
                Ok(result) => result,
                Err(error) => json!({"ok": false, "error": error}),
            }
        }
        Route::Manual => {
            return json!({
                "ok": false,
                "error": "没有 root，也没有可弹框的 pkexec",
                "command": manual
            })
        }
        Route::Pkexec => {}
    }
    match Command::new("pkexec").arg(&executable).arg("install-helper").output() {
        Ok(output) if output.status.success() => {
            json!({"ok": true, "elevated": true, "unit": UNIT_PATH, "socket": crate::attachd::DEFAULT_SOCKET})
        }
        Ok(output) => json!({
            "ok": false,
            "error": format!("系统授权未通过：{}", String::from_utf8_lossy(&output.stderr).trim()),
            "command": manual
        }),
        Err(error) => json!({"ok": false, "error": error.to_string(), "command": manual}),
    }
}

/// Windows 与其他平台：没有 root 助手这一层，虚拟串口由 com0com 驱动提供
#[cfg(not(target_os = "linux"))]
pub fn install_helper() -> Value {
    json!({
        "ok": false,
        "required": false,
        "error": format!("本平台不需要 root 助手：安装 com0com（{COM0COM_URL}）即可获得可被浏览器与桌面程序直接选中的 COM 口"),
        "command": install_command()
    })
}

/// 卸掉助手（root）
#[cfg(target_os = "linux")]
pub fn uninstall_as_root() -> Result<Value, String> {
    let _ = Command::new("systemctl").args(["disable", "--now", "keysim-attachd.service"]).output();
    std::fs::remove_file(UNIT_PATH).map_err(|error| format!("删除 {UNIT_PATH} 失败：{error}"))?;
    let _ = Command::new("systemctl").arg("daemon-reload").output();
    let udev_removed = std::fs::remove_file(UDEV_PATH).is_ok();
    if udev_removed {
        let _ = Command::new("udevadm").args(["control", "--reload-rules"]).output();
    }
    Ok(json!({"ok": true, "removed": UNIT_PATH, "udevRemoved": udev_removed}))
}

#[cfg(not(target_os = "linux"))]
pub fn uninstall_as_root() -> Result<Value, String> {
    Err(format!("本平台没有 root 助手可卸：虚拟串口由 com0com 提供（{COM0COM_URL}）"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    /// 单元文件必须指向当前可执行文件的 attachd 子命令与固定 socket，否则装完连不上
    #[cfg(target_os = "linux")]
    fn unit_points_at_this_binary() {
        let text = unit_text("/opt/keysim", 1000);
        assert!(text.contains("ExecStart=/opt/keysim attachd --socket /run/keysim/keysim-attachd.sock --uid 1000"), "{text}");
        assert!(text.contains("RuntimeDirectory=keysim"), "缺 RuntimeDirectory，socket 目录不会被创建");
    }

    #[test]
    /// 从 cargo 构建目录安装时必须改指向 /usr/local/bin/keysim：
    /// 指向 target/ 的单元会在 cargo clean 后失效
    #[cfg(target_os = "linux")]
    fn build_artifacts_are_not_referenced_by_the_unit() {
        assert!(is_build_artifact("/home/u/007/keysim/target/release/keysim"));
        assert!(is_build_artifact("/home/u/007/keysim/target/debug/keysim"));
        assert!(!is_build_artifact(INSTALL_PATH));
        assert!(!is_build_artifact("/opt/keysim/keysim"));
    }

    #[test]
    /// udev 规则必须只匹配 keysim 自家虚拟设备，且不碰真实串口的权限
    #[cfg(target_os = "linux")]
    fn udev_rule_is_scoped_to_our_virtual_device() {
        let rule = udev_text("zhang");
        assert!(rule.contains(r#"ATTRS{idVendor}=="1209""#), "{rule}");
        assert!(rule.contains(r#"ATTRS{idProduct}=="5253""#), "{rule}");
        assert!(rule.contains(r#"OWNER="zhang""#), "{rule}");
        assert!(rule.contains(r#"SUBSYSTEM=="tty""#), "{rule}");
        // 桌面壳只认 /dev/ttyUSBn（nativeSerialPort.js:45），规则必须顺手建别名
        assert!(rule.contains(r#"SYMLINK+="ttyUSB90""#), "{rule}");
        // 不允许出现无条件放权：没有 idVendor 限定的 MODE=0666 之类
        assert!(!rule.contains("0666"), "不该把设备开成全局可写：{rule}");
    }

    #[test]
    /// 三个事实决定三条路线：无 root 且弹不出授权框时只能把命令交给用户
    fn privilege_route_is_decided_by_three_facts() {
        assert_eq!(route(true, false, false), Route::Direct, "root 应直接落地");
        assert_eq!(route(false, true, true), Route::Pkexec, "有 pkexec 且有图形会话应弹框");
        assert_eq!(route(false, true, false), Route::Manual, "无图形会话时 pkexec 弹不出框");
        assert_eq!(route(false, false, true), Route::Manual, "没有 pkexec 只能手动");
    }

    #[test]
    /// 交给用户的命令必须是绝对路径可复制粘贴的（别假设 keysim 在 PATH 里）
    fn manual_command_is_absolute_and_runnable() {
        let command = install_command();
        assert!(command.starts_with("sudo /"), "{command}");
        assert!(command.ends_with(" install-helper"), "{command}");
    }
}
