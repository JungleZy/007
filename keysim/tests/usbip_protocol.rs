//! 测试自己扮演内核的 vhci_hcd：走完 USB/IP 握手再逐个下 URB，
//! 不需要 root 就能证明"内核看到的是一台正确的 CDC-ACM 串口"。
//!
//! 判据全部来自 USB 2.0 规范与 Linux cdc-acm 的绑定条件，不是自定义约定。

use std::io::{Read, Write};
use std::net::TcpStream;
use std::sync::Arc;
use std::time::{Duration, Instant};

use keysim::usbip;

const USBIP_VERSION: u16 = 0x0111;
const OP_REQ_IMPORT: u16 = 0x8003;
const OP_REP_IMPORT: u16 = 0x0003;
const CMD_SUBMIT: u32 = 1;
const CMD_UNLINK: u32 = 2;
const RET_SUBMIT: u32 = 3;
const RET_UNLINK: u32 = 4;
const DIR_OUT: u32 = 0;
const DIR_IN: u32 = 1;

struct Kernel {
    stream: TcpStream,
    buffer: Vec<u8>,
    device: Vec<u8>,
    devid: u32,
    emulator: usbip::Emulator,
    received: Arc<parking_lot::Mutex<Vec<String>>>,
}

fn submit(seqnum: u32, devid: u32, direction: u32, ep: u32, length: i32, setup: [u8; 8]) -> Vec<u8> {
    let mut header = vec![0u8; 48];
    header[0..4].copy_from_slice(&CMD_SUBMIT.to_be_bytes());
    header[4..8].copy_from_slice(&seqnum.to_be_bytes());
    header[8..12].copy_from_slice(&devid.to_be_bytes());
    header[12..16].copy_from_slice(&direction.to_be_bytes());
    header[16..20].copy_from_slice(&ep.to_be_bytes());
    header[24..28].copy_from_slice(&length.to_be_bytes());
    header[40..48].copy_from_slice(&setup);
    header
}

fn setup_packet(kind: u8, request: u8, value: u16, index: u16, length: u16) -> [u8; 8] {
    let mut setup = [0u8; 8];
    setup[0] = kind;
    setup[1] = request;
    setup[2..4].copy_from_slice(&value.to_le_bytes());
    setup[4..6].copy_from_slice(&index.to_le_bytes());
    setup[6..8].copy_from_slice(&length.to_le_bytes());
    setup
}

struct Ret {
    command: u32,
    seqnum: u32,
    status: i32,
    data: Vec<u8>,
}

impl Kernel {
    /// 起模拟器并完成 import 握手
    fn attach() -> Kernel {
        let received = Arc::new(parking_lot::Mutex::new(Vec::new()));
        let sink_received = Arc::clone(&received);
        let emulator = usbip::start(
            0,
            Arc::new(move |event| {
                if let usbip::Event::Rx(hex) = event {
                    sink_received.lock().push(hex);
                }
            }),
        )
        .expect("模拟器应能起在随机端口");

        let mut stream = TcpStream::connect(("127.0.0.1", emulator.usbip_port)).expect("应能连上模拟器");
        stream.set_nodelay(true).ok();
        stream.set_read_timeout(Some(Duration::from_millis(200))).ok();

        let mut request = Vec::new();
        request.extend_from_slice(&USBIP_VERSION.to_be_bytes());
        request.extend_from_slice(&OP_REQ_IMPORT.to_be_bytes());
        request.extend_from_slice(&0u32.to_be_bytes());
        let mut busid = [0u8; 32];
        busid[..emulator.busid.len()].copy_from_slice(emulator.busid.as_bytes());
        request.extend_from_slice(&busid);
        stream.write_all(&request).expect("import 请求应能发出");

        let mut kernel = Kernel {
            stream,
            buffer: Vec::new(),
            device: Vec::new(),
            devid: 0,
            emulator,
            received,
        };
        let head = kernel.read_exact(8);
        assert_eq!(u16::from_be_bytes([head[2], head[3]]), OP_REP_IMPORT, "import 回应码不对");
        assert_eq!(u32::from_be_bytes([head[4], head[5], head[6], head[7]]), 0, "import 被拒绝");
        kernel.device = kernel.read_exact(312);
        let busnum = u32::from_be_bytes([kernel.device[288], kernel.device[289], kernel.device[290], kernel.device[291]]);
        let devnum = u32::from_be_bytes([kernel.device[292], kernel.device[293], kernel.device[294], kernel.device[295]]);
        kernel.devid = (busnum << 16) | devnum;
        kernel
    }

    fn pump(&mut self) {
        let mut chunk = [0u8; 4096];
        match self.stream.read(&mut chunk) {
            Ok(count) => self.buffer.extend_from_slice(&chunk[..count]),
            Err(_) => {}
        }
    }

    fn read_exact(&mut self, want: usize) -> Vec<u8> {
        let deadline = Instant::now() + Duration::from_secs(5);
        while self.buffer.len() < want {
            assert!(Instant::now() < deadline, "等 {want} 字节超时，已到 {}", self.buffer.len());
            self.pump();
        }
        self.buffer.drain(..want).collect()
    }

    /// 不阻塞地看看有没有待读字节（用于验证"无数据时 URB 必须挂着"）
    fn buffered(&mut self) -> usize {
        self.pump();
        self.buffer.len()
    }

    fn read_ret(&mut self) -> Ret {
        let header = self.read_exact(48);
        let command = u32::from_be_bytes([header[0], header[1], header[2], header[3]]);
        let seqnum = u32::from_be_bytes([header[4], header[5], header[6], header[7]]);
        let direction = u32::from_be_bytes([header[12], header[13], header[14], header[15]]);
        let status = i32::from_be_bytes([header[20], header[21], header[22], header[23]]);
        let actual = i32::from_be_bytes([header[24], header[25], header[26], header[27]]);
        let data = if command == RET_SUBMIT && direction == DIR_IN && actual > 0 {
            self.read_exact(actual as usize)
        } else {
            Vec::new()
        };
        Ret { command, seqnum, status, data }
    }

    fn send(&mut self, bytes: &[u8]) {
        self.stream.write_all(bytes).expect("URB 应能发给模拟器");
    }
}

#[test]
/// import 后交回的设备必须是一台全速 CDC 串口，参数与设备描述符自洽
fn import_returns_a_full_speed_cdc_device() {
    let mut kernel = Kernel::attach();
    let device = kernel.device.clone();
    assert_eq!(
        String::from_utf8_lossy(&device[256..288]).trim_end_matches('\0'),
        "1-1",
        "busid 不对"
    );
    assert_eq!(u32::from_be_bytes([device[288], device[289], device[290], device[291]]), 1, "busnum 不对");
    assert_eq!(u32::from_be_bytes([device[292], device[293], device[294], device[295]]), 2, "devnum 不对");
    assert_eq!(
        u32::from_be_bytes([device[296], device[297], device[298], device[299]]),
        2,
        "speed 应为 USB_SPEED_FULL(2)"
    );
    assert_eq!(u16::from_be_bytes([device[300], device[301]]), 0x1209, "idVendor 不对");
    assert_eq!(device[306], 0x02, "bDeviceClass 应为 CDC(0x02)");
    assert_eq!(device[310], 1, "bNumConfigurations 应为 1");
    assert_eq!(device[311], 2, "bNumInterfaces 应为 2（通信 + 数据）");

    let request = submit(1, kernel.devid, DIR_IN, 0, 18, setup_packet(0x80, 0x06, 0x0100, 0, 18));
    kernel.send(&request);
    let descriptor = kernel.read_ret();
    assert_eq!(descriptor.status, 0);
    assert_eq!(descriptor.data.len(), 18, "设备描述符长度必须是 18");
    assert_eq!(descriptor.data[4], 0x02, "bDeviceClass 应为 CDC");
    assert_eq!(u16::from_le_bytes([descriptor.data[8], descriptor.data[9]]), 0x1209);
}

#[test]
/// 配置描述符必须满足 cdc-acm 的绑定条件，否则内核不会给出 /dev/ttyACM*
fn configuration_satisfies_cdc_acm_binding() {
    let mut kernel = Kernel::attach();
    let request = submit(2, kernel.devid, DIR_IN, 0, 128, setup_packet(0x80, 0x06, 0x0200, 0, 128));
    kernel.send(&request);
    let Ret { status, data, .. } = kernel.read_ret();
    assert_eq!(status, 0);
    assert_eq!(u16::from_le_bytes([data[2], data[3]]) as usize, data.len(), "wTotalLength 与实际长度不一致");
    assert_eq!(data[4], 2, "bNumInterfaces 应为 2");

    let mut interfaces = Vec::new();
    let mut endpoints = Vec::new();
    let mut functional = Vec::new();
    let mut offset = 0usize;
    while offset < data.len() {
        let length = data[offset] as usize;
        assert!(length > 0, "出现零长度描述符，配置集损坏");
        match data[offset + 1] {
            0x04 => interfaces.push([data[offset + 5], data[offset + 6], data[offset + 7]]),
            0x05 => endpoints.push([data[offset + 2], data[offset + 3]]),
            0x24 => functional.push(data[offset + 2]),
            _ => {}
        }
        offset += length;
    }
    assert_eq!(
        interfaces,
        vec![[0x02, 0x02, 0x01], [0x0a, 0x00, 0x00]],
        "接口类不符合 CDC-ACM（通信类 ACM + 数据类）"
    );
    assert_eq!(
        endpoints,
        vec![[0x82, 0x03], [0x01, 0x02], [0x81, 0x02]],
        "端点不符合“中断 IN + bulk OUT + bulk IN”"
    );
    for code in [0x00u8, 0x02, 0x06] {
        assert!(functional.contains(&code), "缺少 CDC 功能描述符 {code:#02x}，实际 {functional:?}");
    }
}

#[test]
/// bulk IN 无数据时必须挂起（真实设备行为），宿主一喂字节就补完同一个 URB，且内容逐字节不变
fn bulk_in_stays_pending_until_bytes_arrive() {
    let mut kernel = Kernel::attach();
    let request = submit(10, kernel.devid, DIR_IN, 1, 64, [0u8; 8]);
    kernel.send(&request);
    std::thread::sleep(Duration::from_millis(400));
    assert_eq!(kernel.buffered(), 0, "无数据时不该立刻回应 bulk IN");

    let bytes = [1u8, 0, 2, 0, 0, 1, 0, 2, 0, 0];
    kernel.emulator.write(&bytes);
    let completion = kernel.read_ret();
    assert_eq!(completion.command, RET_SUBMIT);
    assert_eq!(completion.seqnum, 10, "补完的必须是同一个 URB");
    assert_eq!(completion.status, 0);
    assert_eq!(completion.data, bytes.to_vec(), "串口字节被改动了");
}

#[test]
/// 主机往串口写的数据要回报给宿主；挂起的 URB 必须能被 UNLINK 撤销
fn host_writes_are_reported_and_urbs_can_be_unlinked() {
    let mut kernel = Kernel::attach();
    let payload = [0x41u8, 0x54, 0x0d];
    let mut request = submit(20, kernel.devid, DIR_OUT, 1, payload.len() as i32, [0u8; 8]);
    request.extend_from_slice(&payload);
    kernel.send(&request);
    let written = kernel.read_ret();
    assert_eq!(written.status, 0);

    let deadline = Instant::now() + Duration::from_secs(3);
    let mut seen = None;
    while Instant::now() < deadline && seen.is_none() {
        seen = kernel.received.lock().first().cloned();
        if seen.is_none() {
            std::thread::sleep(Duration::from_millis(50));
        }
    }
    assert_eq!(seen.as_deref(), Some("41 54 0d"), "宿主没收到主机写入");

    // 中断 IN 一直挂着（没有串口状态变化），UNLINK 必须能撤销它
    let pending = submit(30, kernel.devid, DIR_IN, 2, 8, [0u8; 8]);
    kernel.send(&pending);
    std::thread::sleep(Duration::from_millis(200));
    let mut unlink = vec![0u8; 48];
    unlink[0..4].copy_from_slice(&CMD_UNLINK.to_be_bytes());
    unlink[4..8].copy_from_slice(&31u32.to_be_bytes());
    unlink[8..12].copy_from_slice(&kernel.devid.to_be_bytes());
    unlink[20..24].copy_from_slice(&30u32.to_be_bytes());
    kernel.send(&unlink);
    let result = kernel.read_ret();
    assert_eq!(result.command, RET_UNLINK);
    assert_eq!(result.status, -104, "UNLINK 掉已挂起的 URB 应回 -ECONNRESET");
}
