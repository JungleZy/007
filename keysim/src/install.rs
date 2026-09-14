//! 装一次 root 助手：把自己注册成 systemd 服务（`keysim attachd`）。
//!
//! 之后网页上点「开启虚拟串口」不再需要密码——特权只留在助手里，
//! 且助手只做 vhci_hcd 的 attach/detach（attachd.rs 说明了攻击面）。

use std::process::Command;

use serde_json::{json, Value};

const UNIT_PATH: &str = "/etc/systemd/system/keysim-attachd.service";

fn self_path() -> String {
    std::env::current_exe()
        .map(|path| path.display().to_string())
        .unwrap_or_else(|_| "keysim".into())
}

/// 安装后的稳定路径：单元文件绝不能指向 cargo 的构建目录，
/// 否则一次 `cargo clean`（或换分支重建）就让 root 服务指向不存在的文件。
const INSTALL_PATH: &str = "/usr/local/bin/keysim";

fn is_build_artifact(path: &str) -> bool {
    path.contains("/target/release/") || path.contains("/target/debug/")
}

/// 从构建目录安装时，先把自己复制到 INSTALL_PATH，再让单元指向它
fn stable_executable() -> Result<String, String> {
    let current = self_path();
    if !is_build_artifact(&current) {
        return Ok(current);
    }
    if let Some(parent) = std::path::Path::new(INSTALL_PATH).parent() {
        std::fs::create_dir_all(parent).map_err(|error| format!("创建 {} 失败：{error}", parent.display()))?;
    }
    std::fs::copy(&current, INSTALL_PATH).map_err(|error| format!("复制到 {INSTALL_PATH} 失败：{error}"))?;
    std::fs::set_permissions(
        INSTALL_PATH,
        <std::fs::Permissions as std::os::unix::fs::PermissionsExt>::from_mode(0o755),
    )
    .map_err(|error| format!("设置 {INSTALL_PATH} 权限失败：{error}"))?;
    Ok(INSTALL_PATH.to_string())
}

/// 给用户的可复制命令：一律用绝对路径，别假设 keysim 在 PATH 里
pub fn install_command() -> String {
    format!("sudo {} install-helper", self_path())
}

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
pub fn write_unit_as_root() -> Result<Value, String> {
    let executable = stable_executable()?;
    let (user, uid) = target_user();
    std::fs::write(UNIT_PATH, unit_text(&executable, uid)).map_err(|error| format!("写 {UNIT_PATH} 失败：{error}"))?;
    for args in [vec!["daemon-reload"], vec!["enable", "--now", "keysim-attachd.service"]] {
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
    // 未装 vhci-hcd 时这一步失败不致命：attach 时还会再试一次
    let _ = Command::new("modprobe").arg("vhci-hcd").output();
    Ok(json!({
        "ok": true,
        "unit": UNIT_PATH,
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

/// 网页/CLI 调用：root 直装；有 pkexec 则弹框装；否则把命令交给用户
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

/// 卸掉助手（root）
pub fn uninstall_as_root() -> Result<Value, String> {
    let _ = Command::new("systemctl").args(["disable", "--now", "keysim-attachd.service"]).output();
    std::fs::remove_file(UNIT_PATH).map_err(|error| format!("删除 {UNIT_PATH} 失败：{error}"))?;
    let _ = Command::new("systemctl").arg("daemon-reload").output();
    Ok(json!({"ok": true, "removed": UNIT_PATH}))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    /// 单元文件必须指向当前可执行文件的 attachd 子命令与固定 socket，否则装完连不上
    fn unit_points_at_this_binary() {
        let text = unit_text("/opt/keysim", 1000);
        assert!(text.contains("ExecStart=/opt/keysim attachd --socket /run/keysim/keysim-attachd.sock --uid 1000"), "{text}");
        assert!(text.contains("RuntimeDirectory=keysim"), "缺 RuntimeDirectory，socket 目录不会被创建");
    }

    #[test]
    /// 从 cargo 构建目录安装时必须改指向 /usr/local/bin/keysim：
    /// 指向 target/ 的单元会在 cargo clean 后失效
    fn build_artifacts_are_not_referenced_by_the_unit() {
        assert!(is_build_artifact("/home/u/007/keysim/target/release/keysim"));
        assert!(is_build_artifact("/home/u/007/keysim/target/debug/keysim"));
        assert!(!is_build_artifact(INSTALL_PATH));
        assert!(!is_build_artifact("/opt/keysim/keysim"));
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
