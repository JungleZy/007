//! PTY 虚拟串口设备：系统里真实存在的字符设备（/dev/pts/N）。
//!
//! 任何按路径打开串口的程序都能用它：串口调试助手、pyserial、minicom、cat，
//! 以及桌面模式下真正去读串口的仓外桥接程序。
//!
//! 边界（实测，不是推断）：内核不把 /dev/pts/* 注册成 udev 的 tty 设备，而 Chromium 的
//! Web Serial 按 udev tty 枚举，所以浏览器的串口选择框看不到 PTY —— 那条路要走
//! kernel.rs 里的内核级后端（USB/IP / gadget / tty0tty / com0com）。

use std::ffi::CStr;
use std::fs;
use std::io;
use std::os::fd::RawFd;
use std::path::PathBuf;
use std::sync::Arc;
use std::thread;

use parking_lot::Mutex;

pub struct Pty {
    master: RawFd,
    slave: RawFd,
    pub path: String,
    pub links: Vec<PathBuf>,
    pub warnings: Vec<String>,
    closed: Arc<Mutex<bool>>,
}

fn errno() -> io::Error {
    io::Error::last_os_error()
}

/// 原始模式：不回显、不做换行转换，字节原样进出，才是串口语义
fn set_raw(fd: RawFd) -> io::Result<()> {
    // SAFETY: fd 是本函数调用方刚打开的有效描述符，termios 只读写本地栈变量
    unsafe {
        let mut settings: libc::termios = std::mem::zeroed();
        if libc::tcgetattr(fd, &mut settings) != 0 {
            return Err(errno());
        }
        libc::cfmakeraw(&mut settings);
        if libc::tcsetattr(fd, libc::TCSANOW, &settings) != 0 {
            return Err(errno());
        }
    }
    Ok(())
}

impl Pty {
    /// 开一个 PTY；links 是要创建的符号链接（失败只记告警，不影响设备本身）
    pub fn open(links: &[PathBuf]) -> io::Result<Pty> {
        // SAFETY: 标准 PTY 三步（posix_openpt/grantpt/unlockpt），失败即返回
        let (master, path) = unsafe {
            let master = libc::posix_openpt(libc::O_RDWR | libc::O_NOCTTY);
            if master < 0 {
                return Err(errno());
            }
            if libc::grantpt(master) != 0 || libc::unlockpt(master) != 0 {
                let error = errno();
                libc::close(master);
                return Err(error);
            }
            let mut buffer = [0i8; 128];
            if libc::ptsname_r(master, buffer.as_mut_ptr(), buffer.len()) != 0 {
                let error = errno();
                libc::close(master);
                return Err(error);
            }
            let path = CStr::from_ptr(buffer.as_ptr()).to_string_lossy().into_owned();
            (master, path)
        };

        // 从端要一直开着：否则没有读者时读主端会得到 EIO
        // SAFETY: path 来自 ptsname_r，必为合法 C 字符串路径
        let slave = unsafe {
            let c_path = std::ffi::CString::new(path.clone()).map_err(|_| io::Error::other("设备路径含 NUL"))?;
            let slave = libc::open(c_path.as_ptr(), libc::O_RDWR | libc::O_NOCTTY);
            if slave < 0 {
                let error = errno();
                libc::close(master);
                return Err(error);
            }
            slave
        };
        set_raw(master)?;
        set_raw(slave)?;

        let mut warnings = Vec::new();
        if let Err(error) = fs::set_permissions(&path, <fs::Permissions as std::os::unix::fs::PermissionsExt>::from_mode(0o666)) {
            warnings.push(format!("放开 {path} 权限失败：{error}（同用户仍可打开）"));
        }

        let mut made = Vec::new();
        for link in links {
            if let Some(parent) = link.parent() {
                let _ = fs::create_dir_all(parent);
            }
            let _ = fs::remove_file(link);
            match std::os::unix::fs::symlink(&path, link) {
                Ok(()) => made.push(link.clone()),
                Err(error) => warnings.push(format!("创建设备链接 {} 失败：{error}", link.display())),
            }
        }

        Ok(Pty { master, slave, path, links: made, warnings, closed: Arc::new(Mutex::new(false)) })
    }

    /// 设备"收到"字节：写进主端，对端（真串口客户端）就读到它
    pub fn write(&self, bytes: &[u8]) -> io::Result<()> {
        let mut written = 0usize;
        while written < bytes.len() {
            // SAFETY: self.master 在 Pty 生命周期内有效，切片边界由 written 保证
            let count = unsafe {
                libc::write(
                    self.master,
                    bytes[written..].as_ptr().cast::<libc::c_void>(),
                    bytes.len() - written,
                )
            };
            if count <= 0 {
                let error = errno();
                if error.kind() == io::ErrorKind::Interrupted {
                    continue;
                }
                return Err(error);
            }
            written += count as usize;
        }
        Ok(())
    }

    /// 对端往串口写的数据：起一个线程读主端并回调（十六进制串）
    pub fn watch<F: Fn(String) + Send + 'static>(&self, on_rx: F) {
        let master = self.master;
        let closed = Arc::clone(&self.closed);
        thread::spawn(move || {
            let mut buffer = [0u8; 4096];
            loop {
                if *closed.lock() {
                    return;
                }
                // SAFETY: master 直到 close() 之前都有效；closed 标记先于关闭置位
                let count = unsafe { libc::read(master, buffer.as_mut_ptr().cast::<libc::c_void>(), buffer.len()) };
                if count > 0 {
                    let slice = &buffer[..count as usize];
                    on_rx(slice.iter().map(|byte| format!("{byte:02x}")).collect::<Vec<_>>().join(" "));
                } else {
                    thread::sleep(std::time::Duration::from_millis(120));
                }
            }
        });
    }

    /// 没有 root 时给出的一次性命令；桌面壳的串口列表只认 /dev/ttyUSBn
    pub fn system_link_hint(&self, target: &str) -> String {
        format!("sudo ln -sfn {} {target}", self.path)
    }
}

impl Drop for Pty {
    fn drop(&mut self) {
        *self.closed.lock() = true;
        for link in &self.links {
            let _ = fs::remove_file(link);
        }
        // SAFETY: 两个描述符由本结构独占，drop 时只关一次
        unsafe {
            libc::close(self.slave);
            libc::close(self.master);
        }
    }
}

/// 本机能否创建 PTY 虚拟串口设备
pub fn probe() -> Result<(), String> {
    if cfg!(target_os = "windows") {
        return Err("Windows 需要 com0com 之类的虚拟串口驱动，PTY 方案不适用".into());
    }
    fs::metadata("/dev/ptmx").map_err(|error| format!("/dev/ptmx 不可用：{error}"))?;
    Ok(())
}

pub fn default_links() -> Vec<PathBuf> {
    let home = std::env::var("HOME").unwrap_or_else(|_| "/tmp".into());
    vec![PathBuf::from(home).join(".keysim").join("ttyKEYSIM0")]
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Read;

    #[test]
    /// 开出来的必须是真设备：按路径打开就能读到写进去的字节
    fn pty_is_a_real_readable_device() {
        if probe().is_err() {
            return;
        }
        let link = PathBuf::from("/tmp/keysim-unit-tty");
        let pty = Pty::open(std::slice::from_ref(&link)).expect("开 PTY");
        assert!(pty.path.starts_with("/dev/"), "设备节点异常：{}", pty.path);
        assert_eq!(pty.links, vec![link.clone()]);
        assert!(pty.system_link_hint("/dev/ttyUSB0").starts_with("sudo ln -sfn /dev/"));

        let mut reader = fs::File::open(&link).expect("按路径打开设备");
        pty.write(&[1, 0, 2, 0, 0]).expect("写设备");
        let mut buffer = [0u8; 5];
        reader.read_exact(&mut buffer).expect("从设备读回");
        assert_eq!(buffer, [1, 0, 2, 0, 0]);

        let path = pty.path.clone();
        drop(pty);
        assert!(fs::metadata(&link).is_err(), "关闭后符号链接应消失");
        assert!(fs::File::open(&path).is_err(), "关闭后设备节点应回收");
    }
}
