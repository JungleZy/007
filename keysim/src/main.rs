//! keysim：手键 / 电子键拍发模拟器与虚拟串口台。
//!
//! 单个 Rust 可执行文件：网页控制台、虚拟串口（内核级 / PTY / 桥接 / 浏览器注入）、
//! 节拍模型、故障注入、后端载荷与 REST 驱动、root 助手，全部内嵌。
//!
//! 口径来源全部是仓内生产代码（前端 useTraffic.js / WebSerial.js / handKeyTrain.js、
//! 后端 GeneralTickerPatService.java / GeneralKeyPatService.java），
//! tests/ 下的对表测试会逐项核对，任何一侧改动导致漂移即测试失败。

use keysim::{attachd, console, faults, install, keying, pty, rest, serial, sinks, BRIDGE_PORT, CONSOLE_PORT};

use std::collections::HashMap;
use std::path::PathBuf;

use serde_json::{json, Value};

const USAGE: &str = "\
keysim —— 手键/电子键拍发模拟器与虚拟串口台（单文件）

  keysim                                          等同 serve：开控制台并自动开虚拟串口
  keysim serve  [--http 18700] [--port 18765]     网页控制台（虚拟串口开关 / 报文 / 拍发）
                [--no-autostart] [--open <url>] [--link /dev/ttyUSB0]
  keysim doctor                                   体检：本机能提供哪种虚拟串口，缺什么装什么
  keysim hand     --text \"ABCD EFGH\" [选项]        手键拍发
  keysim electron --text \"ABCD EFGH\" [选项]        电子键拍发
  keysim bridge   [--port 18765] [--speed 1]      只起桌面桥接并回放
  keysim upload   --user <账号> --password <密码>   打真实后端：取页 → startTrain → uploadResult → finish
                  --train-id <n> [--page 1] [--attempt 1] [--base http://localhost:18001/api]
  keysim install-helper / uninstall-helper        装/卸 root 助手（USB/IP 挂载，装一次即可）
  keysim attachd  --socket <路径> --uid <n>        root 助手本体（由 systemd 拉起）

通用选项
  --key hand|electron   键型（hand/electron 子命令已隐含）
  --text <报文>         组间空格分隔；也可用 --random <组数> 现生成
  --alphabet <名>       letter | short | long | mix（默认 letter）
  --rate <n>            手键 字符/分（默认 70），电子键 组/分（默认 20）
  --jitter <0..1>       节拍抖动比例（默认 0，必须 ≤ 评分规则 skew/100）
  --seed <n>            随机种子（默认 1）
  --skew <n>            评分规则偏移量，手键可行性校验用（默认 51）
  --tail <名>           手键 turn|end|none；电子键 page|end|none
  --no-preamble         不发开始符（默认发）
  --multi-page          非单页训练：按\"翻页后 codeGap 夹到 60ms\"校验可行性
  --fault <列表>        dupDown,missingUp,microPress,unknownByte（逗号分隔）
  --sink <名>           frames（默认）| bytes | bridge | payload
  --chunk <名>          bytes 分包：exact（默认）|split|merge|random
  --window <ms>         分包窗口（默认 20）
  --speed <倍数>        回放倍速（默认 1）
  --train-id / --page / --attempt / --server-elapsed / --scale   payload 与 upload 用
  --out <路径>          输出写文件而非标准输出
";

struct Args {
    command: String,
    flags: HashMap<String, String>,
    switches: Vec<String>,
    links: Vec<PathBuf>,
}

impl Args {
    fn parse() -> Args {
        let mut raw: Vec<String> = std::env::args().skip(1).collect();
        let command = match raw.first() {
            Some(first) if !first.starts_with("--") => raw.remove(0),
            _ => "serve".to_string(),
        };
        let mut flags = HashMap::new();
        let mut switches = Vec::new();
        let mut links = Vec::new();
        let mut index = 0usize;
        while index < raw.len() {
            let token = raw[index].clone();
            index += 1;
            let Some(name) = token.strip_prefix("--") else { continue };
            let (name, inline) = match name.split_once('=') {
                Some((name, value)) => (name.to_string(), Some(value.to_string())),
                None => (name.to_string(), None),
            };
            let next = raw.get(index).filter(|value| !value.starts_with("--")).cloned();
            let value = inline.or_else(|| {
                if matches!(
                    name.as_str(),
                    "no-autostart" | "no-preamble" | "multi-page" | "hold" | "help" | "json"
                ) {
                    None
                } else {
                    next.map(|value| {
                        index += 1;
                        value
                    })
                }
            });
            match value {
                Some(value) if name == "link" => links.push(PathBuf::from(value)),
                Some(value) => {
                    flags.insert(name, value);
                }
                None => switches.push(name),
            }
        }
        Args { command, flags, switches, links }
    }

    fn text(&self, name: &str) -> Option<&str> {
        self.flags.get(name).map(String::as_str)
    }
    fn number(&self, name: &str) -> Option<f64> {
        self.flags.get(name).and_then(|value| value.parse().ok())
    }
    fn has(&self, name: &str) -> bool {
        self.switches.iter().any(|item| item == name)
    }
}

/// CLI 参数 → 与网页 API 完全相同的参数对象（只有一份口径）
fn params_of(args: &Args, key: &str) -> Result<Value, String> {
    let text = match (args.text("text"), args.number("random")) {
        (Some(text), _) => text.to_string(),
        (None, Some(groups)) => keying::random_message(
            args.text("alphabet").unwrap_or("letter"),
            groups as usize,
            args.number("group-size").unwrap_or(4.0) as usize,
            args.number("seed").map(|value| value as u32),
        )?,
        (None, None) => return Err("缺少报文：用 --text \"ABCD EFGH\" 或 --random <组数>".into()),
    };
    let mut params = json!({
        "key": key,
        "text": text,
        "alphabet": args.text("alphabet").unwrap_or("letter"),
        "jitter": args.number("jitter").unwrap_or(0.0),
        "seed": args.number("seed").unwrap_or(1.0),
        "skew": args.number("skew").unwrap_or(51.0),
        "preamble": !args.has("no-preamble"),
        "singlePage": !args.has("multi-page"),
        "faults": args.text("fault").map(|list| list.split(',').filter(|item| !item.is_empty()).collect::<Vec<_>>()).unwrap_or_default()
    });
    if let Some(rate) = args.number("rate") {
        params["rate"] = json!(rate);
    }
    if let Some(tail) = args.text("tail") {
        params["tail"] = json!(tail);
    }
    if let Some(gap) = args.number("stroke-gap") {
        params["strokeGap"] = json!(gap);
    }
    Ok(params)
}

fn emit(args: &Args, text: &str) {
    match args.text("out") {
        Some(path) => match std::fs::write(path, text) {
            Ok(()) => println!("已写入 {path}"),
            Err(error) => eprintln!("写 {path} 失败：{error}"),
        },
        None => println!("{text}"),
    }
}

fn run_key(args: &Args, key: &str) -> Result<(), String> {
    let params = params_of(args, key)?;
    let timeline = console::build_timeline(&params)?;
    let sink = args.text("sink").unwrap_or("frames");
    let chunking = faults::Chunking::parse(args.text("chunk").unwrap_or("exact"))
        .ok_or_else(|| format!("未知分包模式 {}", args.text("chunk").unwrap_or_default()))?;
    let window = args.number("window").unwrap_or(20.0);
    let seed = args.number("seed").unwrap_or(1.0) as u32;

    match sink {
        "frames" => {
            let frames: Vec<Value> = sinks::to_frames(&timeline, 0.0)
                .into_iter()
                .map(|(at, frame)| json!({"at": at, "frame": frame}))
                .collect();
            emit(args, &serde_json::to_string_pretty(&frames).unwrap_or_default());
        }
        "bytes" => {
            let lines: Vec<String> = sinks::to_byte_stream(&timeline, chunking, window, seed)
                .into_iter()
                .map(|chunk| format!("{:>9.3}  {}", chunk.at, sinks::hex(&chunk.bytes)))
                .collect();
            emit(args, &lines.join("\n"));
        }
        "payload" => {
            let train_id = args.number("train-id").unwrap_or(0.0) as i64;
            let page = args.number("page").unwrap_or(1.0) as i64;
            let capture = sinks::Capture {
                attempt: args.number("attempt").unwrap_or(1.0) as i64,
                server_elapsed_ms: args.number("server-elapsed").unwrap_or(0.0),
            };
            let scale = match args.number("scale") {
                Some(scale) => scale,
                None => match args.number("server-elapsed") {
                    Some(elapsed) if elapsed > 0.0 => sinks::fit_scale(&timeline, elapsed)?,
                    _ => 1.0,
                },
            };
            let payload = if key == "hand" {
                sinks::hand_upload_payload(&timeline, train_id, page, &capture, scale, args.number("skew").unwrap_or(51.0) as i64)
            } else {
                sinks::electron_upload_payload(&timeline, train_id, page, &capture, scale)
            };
            emit(args, &serde_json::to_string_pretty(&payload).unwrap_or_default());
        }
        "bridge" => {
            let port = args.number("port").unwrap_or(BRIDGE_PORT as f64) as u16;
            let speed = args.number("speed").unwrap_or(1.0).max(0.05);
            let serial = serial::VirtualSerial::new(
                port,
                args.links.clone(),
                std::sync::Arc::new(|event: Value| {
                    if event["type"] == "log" {
                        println!("[{}] {}", event["level"].as_str().unwrap_or("info"), event["message"].as_str().unwrap_or_default());
                    }
                }),
            );
            serial.open()?;
            println!("等待被测程序接入……（Ctrl-C 退出）");
            for _ in 0..100 {
                if serial.state()["bridgeClients"].as_u64().unwrap_or(0) > 0 {
                    break;
                }
                std::thread::sleep(std::time::Duration::from_millis(100));
            }
            serial.send(&timeline, speed, params["text"].as_str().unwrap_or_default())?;
            while serial.state()["replay"]["running"] == true {
                std::thread::sleep(std::time::Duration::from_millis(100));
            }
            if !args.has("hold") {
                serial.close();
            } else {
                println!("已回放完毕，保持串口开启（Ctrl-C 退出）");
                loop {
                    std::thread::sleep(std::time::Duration::from_secs(3600));
                }
            }
        }
        other => return Err(format!("未知 sink {other}（可选 frames / bytes / bridge / payload）")),
    }
    Ok(())
}

/// 打真实后端：取页 → startTrain → uploadResult → finish
fn run_upload(args: &Args) -> Result<(), String> {
    let key = args.text("key").unwrap_or("hand");
    let base = args.text("base").unwrap_or(rest::DEFAULT_BASE);
    let account = args.text("user").ok_or("缺少 --user")?;
    let password = args.text("password").ok_or("缺少 --password")?;
    let train_id = args.number("train-id").ok_or("缺少 --train-id")? as i64;
    let page = args.number("page").unwrap_or(1.0) as i64;
    let attempt = args.number("attempt").unwrap_or(1.0) as i64;
    let endpoints = rest::endpoints(key);

    let session = rest::Session::login(base, account, password)?;
    println!("已登录：deviceId={}", session.device_id);

    let page_body = if key == "hand" {
        json!({"id": train_id, "floorNumber": page})
    } else {
        json!({"trainId": train_id, "pageNumber": page})
    };
    let page_envelope = session.call(endpoints.page, "POST", Some(&page_body))?;
    let text = args
        .text("text")
        .map(str::to_string)
        .or_else(|| page_envelope["data"]["pageValue"].as_str().map(str::to_string))
        .ok_or("后端没给本页报文，请用 --text 指定")?;
    println!("本页报文：{text}");

    session.call(&format!("{}?trainId={train_id}&attempt={attempt}", endpoints.start), "GET", None)?;
    let started = std::time::Instant::now();

    let mut params = params_of(args, key)?;
    params["text"] = json!(text);
    let timeline = console::build_timeline(&params)?;

    // 服务端只信 captureIntervals，且逐符时长之和不得超过采集区间
    let elapsed = started.elapsed().as_secs_f64() * 1000.0;
    let wait = (timeline.duration() - elapsed).max(0.0);
    std::thread::sleep(std::time::Duration::from_millis(wait as u64));
    let server_elapsed = started.elapsed().as_secs_f64() * 1000.0;
    let scale = sinks::fit_scale(&timeline, server_elapsed)?;
    let capture = sinks::Capture { attempt, server_elapsed_ms: 0.0 };
    let payload = if key == "hand" {
        sinks::hand_upload_payload(&timeline, train_id, page, &capture, scale, args.number("skew").unwrap_or(51.0) as i64)
    } else {
        sinks::electron_upload_payload(&timeline, train_id, page, &capture, scale)
    };
    let uploaded = session.call(endpoints.upload, "POST", Some(&payload))?;
    println!("uploadResult：{}", uploaded["message"].as_str().unwrap_or("ok"));

    let finish_body = if key == "hand" {
        json!({"id": train_id, "attempt": attempt})
    } else {
        json!({"trainId": train_id, "attempt": attempt})
    };
    let finished = session.call(endpoints.finish, "POST", Some(&finish_body))?;
    println!("finish：{}", finished["message"].as_str().unwrap_or("ok"));
    let statistics = session.call(endpoints.statistics, "POST", Some(&json!({"id": train_id, "trainId": train_id})))?;
    println!("statistics：{}", serde_json::to_string(&statistics["data"]).unwrap_or_default());
    Ok(())
}

fn run_serve(args: &Args) -> Result<(), String> {
    let options = console::Options {
        http: args.number("http").unwrap_or(CONSOLE_PORT as f64) as u16,
        bridge: args.number("port").unwrap_or(BRIDGE_PORT as f64) as u16,
        autostart: !args.has("no-autostart"),
        links: if args.links.is_empty() { pty::default_links() } else { args.links.clone() },
    };
    let console = console::serve(options).map_err(|error| format!("控制台端口 {} 无法监听：{error}", args.number("http").unwrap_or(CONSOLE_PORT as f64)))?;
    let origin = format!("http://127.0.0.1:{}", console.port);
    println!("控制台已启动：{origin}");
    let state = console.serial.state();
    if let Some(path) = state["device"]["path"].as_str() {
        println!("虚拟串口设备：{path}");
    }
    if let Some(selectable) = state["kernel"]["selectable"].as_str() {
        println!("被测程序请选：{selectable}（浏览器串口选择框与桌面串口列表都能看到）");
    }
    println!("桌面桥接：{}", state["bridgeUrl"].as_str().unwrap_or_default());
    if let Some(url) = args.text("open") {
        match console.open_page(url) {
            Ok(()) => println!("已打开被测页面并预置虚拟串口：{url}"),
            Err(error) => eprintln!("打开被测页面失败：{error}"),
        }
    }
    println!("Ctrl-C 退出");
    loop {
        std::thread::sleep(std::time::Duration::from_secs(3600));
    }
}

fn main() {
    let args = Args::parse();
    if args.has("help") || args.command == "help" {
        print!("{USAGE}");
        return;
    }
    let result = match args.command.as_str() {
        "serve" => run_serve(&args),
        "doctor" => {
            println!("{}", console::doctor());
            Ok(())
        }
        "hand" => run_key(&args, "hand"),
        "electron" => run_key(&args, "electron"),
        "bridge" => {
            let mut args = Args::parse();
            args.flags.insert("sink".into(), "bridge".into());
            if args.text("text").is_none() && args.number("random").is_none() {
                args.flags.insert("random".into(), "4".into());
            }
            run_key(&args, args.text("key").unwrap_or("hand").to_string().as_str())
        }
        "upload" => run_upload(&args),
        "install-helper" => match install::install_helper() {
            result if result["ok"] == true => {
                println!("root 助手已安装：{}", result["socket"].as_str().unwrap_or_default());
                Ok(())
            }
            result => Err(format!(
                "{}{}",
                result["error"].as_str().unwrap_or("安装失败"),
                result["command"].as_str().map(|command| format!("；请手动执行：{command}")).unwrap_or_default()
            )),
        },
        "uninstall-helper" => install::uninstall_as_root().map(|result| {
            println!("已卸载：{}", result["removed"].as_str().unwrap_or_default());
        }),
        "attachd" => {
            let socket = args.text("socket").unwrap_or(attachd::DEFAULT_SOCKET).to_string();
            let uid = args.number("uid").map(|value| value as u32);
            attachd::serve(&socket, uid).map_err(|error| error.to_string())
        }
        other => Err(format!("未知子命令 {other}\n\n{USAGE}")),
    };
    if let Err(error) = result {
        eprintln!("keysim: {error}");
        std::process::exit(1);
    }
}
