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
    let executable = self_path();
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
        "socket": crate::attachd::DEFAULT_SOCKET,
        "user": user,
        "uid": uid
    }))
}

/// 网页/CLI 调用：root 直装；有 pkexec 则弹框装；否则把命令交给用户
pub fn install_helper() -> Value {
    // SAFETY: geteuid 无副作用
    if (unsafe { libc::geteuid() }) == 0 {
        return match write_unit_as_root() {
            Ok(result) => result,
            Err(error) => json!({"ok": false, "error": error}),
        };
    }
    let executable = self_path();
    let manual = format!("sudo {executable} install-helper");
    let has_pkexec = Command::new("sh")
        .args(["-c", "command -v pkexec"])
        .output()
        .map(|output| output.status.success())
        .unwrap_or(false);
    let graphical = std::env::var("DISPLAY").is_ok() || std::env::var("WAYLAND_DISPLAY").is_ok();
    if !(has_pkexec && graphical) {
        return json!({
            "ok": false,
            "error": "没有 root，也没有可弹框的 pkexec",
            "command": manual
        });
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
    /// 非 root 且无 pkexec 时必须把可执行命令交给用户，不能只说失败
    fn without_privilege_it_hands_back_a_command() {
        // SAFETY: geteuid 无副作用
        if (unsafe { libc::geteuid() }) == 0 {
            return;
        }
        std::env::remove_var("DISPLAY");
        std::env::remove_var("WAYLAND_DISPLAY");
        let result = install_helper();
        assert_eq!(result["ok"], false);
        let command = result["command"].as_str().unwrap_or_default();
        assert!(command.starts_with("sudo ") && command.contains("install-helper"), "{command}");
    }
}
