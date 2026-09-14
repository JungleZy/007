//! 升级路径的回归测试：目标文件**正在被执行**时也必须能替换。
//!
//! 直接 fs::copy 覆盖一个正在运行的可执行文件会得到 ETXTBSY（Text file busy），
//! 实测踩过：`sudo keysim install-helper` 在助手已运行时报
//! 「复制到 /usr/local/bin/keysim 失败：Text file busy」。
//! 这里真的起一个进程占住目标文件，证明 copy 会失败而 replace_executable 不会。

use std::process::{Child, Command, Stdio};

use keysim::install;

struct Running(Child);

impl Drop for Running {
    fn drop(&mut self) {
        let _ = self.0.kill();
        let _ = self.0.wait();
    }
}

/// 把 keysim 复制到临时目录并跑起来，让该文件处于"正在执行"状态
fn occupy(target: &str) -> Running {
    std::fs::copy(env!("CARGO_BIN_EXE_keysim"), target).expect("应能放置初始副本");
    std::fs::set_permissions(
        target,
        <std::fs::Permissions as std::os::unix::fs::PermissionsExt>::from_mode(0o755),
    )
    .expect("应能置可执行位");
    // serve --http 0 会一直跑：端口交系统分配，不占固定端口
    let child = Command::new(target)
        .args(["serve", "--http", "0", "--port", "0", "--no-autostart"])
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .expect("副本应能启动");
    // 等内核真正把它 mmap 进来，ETXTBSY 才会出现
    std::thread::sleep(std::time::Duration::from_millis(400));
    Running(child)
}

#[test]
fn replaces_a_binary_that_is_currently_running() {
    let directory = std::env::temp_dir().join(format!("keysim-replace-{}", std::process::id()));
    std::fs::create_dir_all(&directory).expect("应能建临时目录");
    let target = directory.join("keysim");
    let target_text = target.display().to_string();
    let _running = occupy(&target_text);

    // 先证明问题真实存在：正在执行的文件不能被直接覆盖
    let direct = std::fs::copy(env!("CARGO_BIN_EXE_keysim"), &target);
    assert_eq!(
        direct.as_ref().err().map(|error| error.raw_os_error()),
        Some(Some(26)),
        "目标未处于执行中，本测试失去意义：{direct:?}"
    );

    // 再证明安装路径不受影响
    install::replace_executable(env!("CARGO_BIN_EXE_keysim"), &target_text).expect("原子替换应当成功");

    let mode = <std::fs::Metadata as std::os::unix::fs::MetadataExt>::mode(&std::fs::metadata(&target).expect("替换后应存在"));
    assert_eq!(mode & 0o777, 0o755, "替换后必须仍可执行");
    // 临时文件不留垃圾
    let leftovers: Vec<String> = std::fs::read_dir(&directory)
        .expect("应能读临时目录")
        .filter_map(Result::ok)
        .map(|entry| entry.file_name().to_string_lossy().to_string())
        .filter(|name| name != "keysim")
        .collect();
    assert!(leftovers.is_empty(), "留下了中间文件：{leftovers:?}");

    // 替换后的文件确实可执行（而不是被写坏）
    let version = Command::new(&target).arg("--help").output().expect("替换后应能运行");
    assert!(
        String::from_utf8_lossy(&version.stdout).contains("keysim"),
        "替换后的二进制跑不出帮助信息"
    );

    std::fs::remove_dir_all(&directory).ok();
}
