//! USB/IP + CDC-ACM 设备模拟：内核会真的多出一个 USB 串口（/dev/ttyACM*，有 udev 记录），
//! 浏览器的串口选择框与桌面串口列表都能直接选中它。
//!
//! 设备逻辑跑在普通用户权限；只有"把它挂上总线"（modprobe + 写 vhci_hcd 的 attach）
//! 是特权操作，交给 attachd 里的 root 助手。任何语言都绕不开这一次 root ——
//! 它是"向内核注册设备"。

use std::collections::VecDeque;
use std::io::{self, Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;

use parking_lot::{Condvar, Mutex};

/// 模拟器对外事件
#[derive(Debug, Clone)]
pub enum Event {
    Attached,
    Detached,
    /// 主机往串口写了数据（十六进制串）
    Rx(String),
    Log(String),
}

pub type Sink = Arc<dyn Fn(Event) + Send + Sync>;

// ---------------------------------------------------------------- USB/IP 常量

const USBIP_VERSION: u16 = 0x0111;
const OP_REQ_DEVLIST: u16 = 0x8005;
const OP_REP_DEVLIST: u16 = 0x0005;
const OP_REQ_IMPORT: u16 = 0x8003;
const OP_REP_IMPORT: u16 = 0x0003;

const USBIP_CMD_SUBMIT: u32 = 1;
const USBIP_CMD_UNLINK: u32 = 2;
const USBIP_RET_SUBMIT: u32 = 3;
const USBIP_RET_UNLINK: u32 = 4;

const USBIP_DIR_OUT: u32 = 0;
const USBIP_DIR_IN: u32 = 1;

/// USB_SPEED_FULL，和内核 enum usb_device_speed 对齐
const USB_SPEED_FULL: u32 = 2;

const EPIPE: i32 = -32;
const ECONNRESET: i32 = -104;

const BUSID: &str = "1-1";
const BUSNUM: u32 = 1;
const DEVNUM: u32 = 2;

// ---------------------------------------------------------------- 设备描述符

const VENDOR_ID: u16 = 0x1209; // pid.codes 的开放 VID
const PRODUCT_ID: u16 = 0x5253; // 'R''S'

const DEVICE_DESCRIPTOR: [u8; 18] = [
    18, 0x01, // bLength, DEVICE
    0x00, 0x02, // bcdUSB 2.00
    0x02, 0x00, 0x00, // CDC 设备类
    64,   // bMaxPacketSize0
    (VENDOR_ID & 0xff) as u8,
    (VENDOR_ID >> 8) as u8,
    (PRODUCT_ID & 0xff) as u8,
    (PRODUCT_ID >> 8) as u8,
    0x00, 0x01, // bcdDevice 1.00
    1, 2, 3, // iManufacturer, iProduct, iSerialNumber
    1,  // bNumConfigurations
];

/// 配置描述符全集：CDC 通信接口（含 CDC 功能描述符 + 中断 IN）+ CDC 数据接口（bulk IN/OUT）
const CONFIG_DESCRIPTOR: [u8; 67] = [
    // Configuration
    9, 0x02, 67, 0x00, 2, 1, 0, 0x80, 50,
    // Interface 0: Communications / ACM / AT-commands
    9, 0x04, 0, 0, 1, 0x02, 0x02, 0x01, 0,
    // CDC Header
    5, 0x24, 0x00, 0x10, 0x01,
    // CDC Call Management
    5, 0x24, 0x01, 0x00, 0x01,
    // CDC ACM（支持 SET_LINE_CODING 等）
    4, 0x24, 0x02, 0x02,
    // CDC Union
    5, 0x24, 0x06, 0x00, 0x01,
    // EP 0x82 中断 IN（串口状态通知）
    7, 0x05, 0x82, 0x03, 0x08, 0x00, 0xff,
    // Interface 1: CDC Data
    9, 0x04, 1, 0, 2, 0x0a, 0x00, 0x00, 0,
    // EP 0x01 bulk OUT
    7, 0x05, 0x01, 0x02, 64, 0x00, 0x00,
    // EP 0x81 bulk IN
    7, 0x05, 0x81, 0x02, 64, 0x00, 0x00,
];

fn string_descriptor(index: u8) -> Option<Vec<u8>> {
    let text = match index {
        0 => {
            // 语言 ID 列表：仅 en-US
            return Some(vec![4, 0x03, 0x09, 0x04]);
        }
        1 => "keysim",
        2 => "keysim virtual serial",
        3 => "KEYSIM0001",
        _ => return None,
    };
    let utf16: Vec<u16> = text.encode_utf16().collect();
    let mut out = Vec::with_capacity(2 + utf16.len() * 2);
    out.push((2 + utf16.len() * 2) as u8);
    out.push(0x03);
    for unit in utf16 {
        out.push((unit & 0xff) as u8);
        out.push((unit >> 8) as u8);
    }
    Some(out)
}

// ---------------------------------------------------------------- 小工具

fn be16(value: u16) -> [u8; 2] { value.to_be_bytes() }
fn be32(value: u32) -> [u8; 4] { value.to_be_bytes() }

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|byte| format!("{byte:02x}")).collect::<Vec<_>>().join(" ")
}

/// usbip_usb_device：312 字节定长结构
fn usb_device_struct() -> Vec<u8> {
    let mut out = Vec::with_capacity(312);
    let mut path = [0u8; 256];
    let text = b"/keysim/1-1";
    path[..text.len()].copy_from_slice(text);
    out.extend_from_slice(&path);
    let mut busid = [0u8; 32];
    busid[..BUSID.len()].copy_from_slice(BUSID.as_bytes());
    out.extend_from_slice(&busid);
    out.extend_from_slice(&be32(BUSNUM));
    out.extend_from_slice(&be32(DEVNUM));
    out.extend_from_slice(&be32(USB_SPEED_FULL));
    out.extend_from_slice(&be16(VENDOR_ID));
    out.extend_from_slice(&be16(PRODUCT_ID));
    out.extend_from_slice(&be16(0x0100));
    out.push(DEVICE_DESCRIPTOR[4]); // bDeviceClass
    out.push(DEVICE_DESCRIPTOR[5]);
    out.push(DEVICE_DESCRIPTOR[6]);
    out.push(1); // bConfigurationValue
    out.push(1); // bNumConfigurations
    out.push(2); // bNumInterfaces
    out
}

fn usb_interfaces() -> Vec<u8> {
    // 通信接口 + 数据接口，各 4 字节
    vec![0x02, 0x02, 0x01, 0x00, 0x0a, 0x00, 0x00, 0x00]
}

// ---------------------------------------------------------------- 设备状态

#[derive(Debug)]
struct PendingUrb {
    seqnum: u32,
    devid: u32,
    ep: u32,
    length: usize,
}

#[derive(Default)]
struct Shared {
    /// 设备 -> 主机（我们要送进串口的拍发字节）
    outbound: VecDeque<u8>,
    /// 等待数据的 bulk IN URB
    pending_in: Vec<PendingUrb>,
    /// 中断 IN URB：挂着不完成，符合"没有状态变化"的真实行为
    pending_notify: Vec<PendingUrb>,
    line_coding: [u8; 7],
}

pub struct Device {
    shared: Mutex<Shared>,
    signal: Condvar,
    attached: AtomicBool,
    sink: Sink,
}

impl Device {
    fn new(sink: Sink) -> Self {
        let mut shared = Shared::default();
        // 9600 8N1，cdc-acm 会先 GET_LINE_CODING
        shared.line_coding = [0x80, 0x25, 0x00, 0x00, 0x00, 0x00, 0x08];
        Device {
            shared: Mutex::new(shared),
            signal: Condvar::new(),
            attached: AtomicBool::new(false),
            sink,
        }
    }

    fn emit(&self, event: Event) {
        (self.sink)(event);
    }

    /// 宿主要发的串口字节：进队列，唤醒补完线程
    pub fn feed(&self, bytes: &[u8]) {
        self.shared.lock().outbound.extend(bytes.iter().copied());
        self.signal.notify_all();
    }
}

/// 控制传输：返回 (状态, 数据)
fn handle_control(device: &Device, setup: [u8; 8], length: usize) -> (i32, Vec<u8>) {
    let request_type = setup[0];
    let request = setup[1];
    let value = u16::from_le_bytes([setup[2], setup[3]]);
    let standard = request_type & 0x60 == 0x00;
    let class = request_type & 0x60 == 0x20;

    if standard {
        match request {
            0x06 => {
                // GET_DESCRIPTOR
                let descriptor_type = (value >> 8) as u8;
                let index = (value & 0xff) as u8;
                let data: Option<Vec<u8>> = match descriptor_type {
                    0x01 => Some(DEVICE_DESCRIPTOR.to_vec()),
                    0x02 => Some(CONFIG_DESCRIPTOR.to_vec()),
                    0x03 => string_descriptor(index),
                    _ => None, // 含 DEVICE_QUALIFIER：全速设备应当 stall
                };
                match data {
                    Some(mut bytes) => {
                        bytes.truncate(length);
                        (0, bytes)
                    }
                    None => (EPIPE, Vec::new()),
                }
            }
            0x09 => (0, Vec::new()), // SET_CONFIGURATION
            0x08 => (0, vec![1]),    // GET_CONFIGURATION
            0x0b => (0, Vec::new()), // SET_INTERFACE
            0x00 => (0, vec![0, 0]), // GET_STATUS
            _ => (EPIPE, Vec::new()),
        }
    } else if class {
        match request {
            0x20 => (0, Vec::new()), // SET_LINE_CODING（数据在 OUT 阶段，已读走）
            0x21 => {
                let coding = device.shared.lock().line_coding;
                (0, coding.to_vec())
            }
            0x22 => (0, Vec::new()), // SET_CONTROL_LINE_STATE
            0x23 => (0, Vec::new()), // SEND_BREAK
            _ => (EPIPE, Vec::new()),
        }
    } else {
        (EPIPE, Vec::new())
    }
}

fn write_ret_submit(
    socket: &Mutex<TcpStream>,
    seqnum: u32,
    devid: u32,
    direction: u32,
    ep: u32,
    status: i32,
    data: &[u8],
) -> io::Result<()> {
    let mut header = Vec::with_capacity(48 + data.len());
    header.extend_from_slice(&be32(USBIP_RET_SUBMIT));
    header.extend_from_slice(&be32(seqnum));
    header.extend_from_slice(&be32(devid));
    header.extend_from_slice(&be32(direction));
    header.extend_from_slice(&be32(ep));
    header.extend_from_slice(&be32(status as u32));
    header.extend_from_slice(&be32(data.len() as u32));
    header.extend_from_slice(&be32(0)); // start_frame
    header.extend_from_slice(&be32(0)); // number_of_packets
    header.extend_from_slice(&be32(0)); // error_count
    header.extend_from_slice(&[0u8; 8]); // padding
    if direction == USBIP_DIR_IN {
        header.extend_from_slice(data);
    }
    let mut guard = socket.lock();
    guard.write_all(&header)?;
    guard.flush()
}

fn write_ret_unlink(socket: &Mutex<TcpStream>, seqnum: u32, devid: u32, status: i32) -> io::Result<()> {
    let mut header = Vec::with_capacity(48);
    header.extend_from_slice(&be32(USBIP_RET_UNLINK));
    header.extend_from_slice(&be32(seqnum));
    header.extend_from_slice(&be32(devid));
    header.extend_from_slice(&be32(0));
    header.extend_from_slice(&be32(0));
    header.extend_from_slice(&be32(status as u32));
    header.extend_from_slice(&[0u8; 24]);
    let mut guard = socket.lock();
    guard.write_all(&header)?;
    guard.flush()
}

/// 有数据就把挂起的 bulk IN URB 补完
fn drain_pending(device: &Arc<Device>, socket: &Arc<Mutex<TcpStream>>) {
    loop {
        let completion = {
            let mut shared = device.shared.lock();
            if shared.outbound.is_empty() || shared.pending_in.is_empty() {
                None
            } else {
                let urb = shared.pending_in.remove(0);
                let take = urb.length.min(shared.outbound.len());
                let data: Vec<u8> = shared.outbound.drain(..take).collect();
                Some((urb, data))
            }
        };
        match completion {
            Some((urb, data)) => {
                if write_ret_submit(socket, urb.seqnum, urb.devid, USBIP_DIR_IN, urb.ep, 0, &data).is_err() {
                    return;
                }
            }
            None => return,
        }
    }
}

fn serve_urbs(device: Arc<Device>, stream: TcpStream) -> io::Result<()> {
    let reader_stream = stream.try_clone()?;
    let socket = Arc::new(Mutex::new(stream));
    device.attached.store(true, Ordering::SeqCst);
    device.emit(Event::Attached);

    // 有新字节就唤醒补完线程
    {
        let device = Arc::clone(&device);
        let socket = Arc::clone(&socket);
        thread::spawn(move || {
            while device.attached.load(Ordering::SeqCst) {
                {
                    let mut guard = device.shared.lock();
                    // 等新字节到达；超时也照样去看一眼，避免漏掉唤醒
                    device
                        .signal
                        .wait_for(&mut guard, std::time::Duration::from_millis(200));
                }
                drain_pending(&device, &socket);
            }
        });
    }

    let mut reader = reader_stream;
    let mut header = [0u8; 48];
    loop {
        if let Err(error) = reader.read_exact(&mut header) {
            device.attached.store(false, Ordering::SeqCst);
            device.emit(Event::Detached);
            return Err(error);
        }
        let command = u32::from_be_bytes([header[0], header[1], header[2], header[3]]);
        let seqnum = u32::from_be_bytes([header[4], header[5], header[6], header[7]]);
        let devid = u32::from_be_bytes([header[8], header[9], header[10], header[11]]);
        let direction = u32::from_be_bytes([header[12], header[13], header[14], header[15]]);
        let ep = u32::from_be_bytes([header[16], header[17], header[18], header[19]]);

        match command {
            USBIP_CMD_SUBMIT => {
                let length = i32::from_be_bytes([header[24], header[25], header[26], header[27]]).max(0) as usize;
                let mut setup = [0u8; 8];
                setup.copy_from_slice(&header[40..48]);
                let mut payload = vec![0u8; if direction == USBIP_DIR_OUT { length } else { 0 }];
                if !payload.is_empty() {
                    reader.read_exact(&mut payload)?;
                }

                if ep == 0 {
                    if direction == USBIP_DIR_OUT && setup[1] == 0x20 && !payload.is_empty() {
                        let mut shared = device.shared.lock();
                        let take = payload.len().min(7);
                        shared.line_coding[..take].copy_from_slice(&payload[..take]);
                    }
                    let (status, data) = handle_control(&device, setup, length);
                    write_ret_submit(&socket, seqnum, devid, direction, ep, status, &data)?;
                } else if direction == USBIP_DIR_OUT {
                    // 主机往串口写：交给宿主记录
                    device.emit(Event::Rx(hex(&payload)));
                    write_ret_submit(&socket, seqnum, devid, direction, ep, 0, &payload)?;
                } else {
                    // IN：有数据立刻给，没数据就挂起（真实设备就是这样）
                    let immediate = {
                        let mut shared = device.shared.lock();
                        if ep == 2 {
                            shared.pending_notify.push(PendingUrb { seqnum, devid, ep, length });
                            None
                        } else if shared.outbound.is_empty() {
                            shared.pending_in.push(PendingUrb { seqnum, devid, ep, length });
                            None
                        } else {
                            let take = length.min(shared.outbound.len());
                            Some(shared.outbound.drain(..take).collect::<Vec<u8>>())
                        }
                    };
                    if let Some(data) = immediate {
                        write_ret_submit(&socket, seqnum, devid, direction, ep, 0, &data)?;
                    }
                }
            }
            USBIP_CMD_UNLINK => {
                let target = u32::from_be_bytes([header[20], header[21], header[22], header[23]]);
                let removed = {
                    let mut shared = device.shared.lock();
                    let before = shared.pending_in.len() + shared.pending_notify.len();
                    shared.pending_in.retain(|urb| urb.seqnum != target);
                    shared.pending_notify.retain(|urb| urb.seqnum != target);
                    before != shared.pending_in.len() + shared.pending_notify.len()
                };
                write_ret_unlink(&socket, seqnum, devid, if removed { ECONNRESET } else { 0 })?;
            }
            other => device.emit(Event::Log(format!("未知 USB/IP 命令 {other}"))),
        }
    }
}

/// USB/IP 握手：devlist / import
fn serve_connection(device: Arc<Device>, mut stream: TcpStream) -> io::Result<()> {
    let mut head = [0u8; 8];
    stream.read_exact(&mut head)?;
    let code = u16::from_be_bytes([head[2], head[3]]);
    match code {
        OP_REQ_DEVLIST => {
            let mut reply = Vec::new();
            reply.extend_from_slice(&be16(USBIP_VERSION));
            reply.extend_from_slice(&be16(OP_REP_DEVLIST));
            reply.extend_from_slice(&be32(0));
            reply.extend_from_slice(&be32(1));
            reply.extend_from_slice(&usb_device_struct());
            reply.extend_from_slice(&usb_interfaces());
            stream.write_all(&reply)?;
            stream.flush()?;
            Ok(())
        }
        OP_REQ_IMPORT => {
            let mut busid = [0u8; 32];
            stream.read_exact(&mut busid)?;
            let requested = String::from_utf8_lossy(&busid)
                .trim_end_matches('\0')
                .to_string();
            let ok = requested == BUSID;
            let mut reply = Vec::new();
            reply.extend_from_slice(&be16(USBIP_VERSION));
            reply.extend_from_slice(&be16(OP_REP_IMPORT));
            reply.extend_from_slice(&be32(if ok { 0 } else { 1 }));
            if ok {
                reply.extend_from_slice(&usb_device_struct());
            }
            stream.write_all(&reply)?;
            stream.flush()?;
            if !ok {
                device.emit(Event::Log(format!("拒绝导入未知 busid {requested}")));
                return Ok(());
            }
            serve_urbs(device, stream)
        }
        other => {
            device.emit(Event::Log(format!("未知握手码 {other:#06x}")));
            Ok(())
        }
    }
}


/// 已启动的模拟器
pub struct Emulator {
    device: Arc<Device>,
    pub usbip_port: u16,
    pub busid: &'static str,
    pub devid: u32,
    pub speed: u32,
}

impl Emulator {
    /// 设备"发给主机"的串口字节
    pub fn write(&self, bytes: &[u8]) {
        self.device.feed(bytes);
    }

    pub fn attached(&self) -> bool {
        self.device.attached.load(Ordering::SeqCst)
    }
}

/// 起一个模拟器：在 127.0.0.1 上说 USB/IP，等 root 助手把连接交给内核。
/// port 传 0 由系统分配。
pub fn start(port: u16, sink: Sink) -> io::Result<Emulator> {
    let listener = TcpListener::bind(("127.0.0.1", port))?;
    let bound = listener.local_addr()?.port();
    let device = Arc::new(Device::new(sink));
    let accept_device = Arc::clone(&device);
    thread::spawn(move || {
        for incoming in listener.incoming() {
            match incoming {
                Ok(stream) => {
                    let _ = stream.set_nodelay(true);
                    let device = Arc::clone(&accept_device);
                    thread::spawn(move || {
                        if let Err(error) = serve_connection(Arc::clone(&device), stream) {
                            if error.kind() != io::ErrorKind::UnexpectedEof {
                                device.emit(Event::Log(format!("连接结束：{error}")));
                            }
                        }
                    });
                }
                Err(error) => accept_device.emit(Event::Log(format!("accept 失败：{error}"))),
            }
        }
    });
    Ok(Emulator {
        device,
        usbip_port: bound,
        busid: BUSID,
        devid: (BUSNUM << 16) | DEVNUM,
        speed: USB_SPEED_FULL,
    })
}
