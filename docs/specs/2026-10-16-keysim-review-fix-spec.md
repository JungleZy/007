# keysim 评审整改规格（2026-10-16）

> 状态：草案，§6 取舍待确认，未实施。代码基线 `fb7cfdd4`，`keysim/` 工作区无未提交改动。基线 `cargo test --locked` 85 项全部通过（lib 62、drift 7、其余 11/1/4）；这是本轮实测数，不作为以后的基线。

## 1. 背景

2026-10-16 对 `keysim/`（约 7.8k 行 Rust、`public/` 控制台与注入脚本、e2e 脚本）及仓内所有提到 keysim 的文档做了一轮只读评审，分三块：核心计算（时间轴/摩尔斯/故障注入）、I/O 与服务（串口/USB-IP/控制台/root 助手/浏览器）、文档。

结论：

- **2 条 P1**：控制台可被任意网页跨站调用，能删掉并替换用户的任意文件；「接入系统串口列表」一旦成功，进程就会自己卡死。
- 核心计算没有 P1。节拍公式、实测码率，以及和前端 `handKeyDecoder.js` / `calculateTiming` 的判定标准一致。
- 和后端（路径、`token`/`deviceId` 请求头、`startTrain` 参数）、和前端（`inject.js` ↔ `WebSerial.js`/`WebSerialChannel.js`）的接口约定一致。只有 `keysim upload` 读错了响应字段（K07）。
- 文档：README 还写着已经删掉的「桥接」通道；keysim 没有任何使用说明；09-15 评审的 R13 读起来像个仍未解决的缺口。

本规格中的行号以基线 `fb7cfdd4` 为准，实施时用 grep 现取。

## 2. 范围

| 标识 | 级别 | 交付单元 | 提交类型 | 核实 |
|---|---|---|---|---|
| K01 | P1 | 控制台拒绝跨源写请求；系统串口链接目标固定 | `fix(keysim)` | 已核实 |
| K02 | P1 | `link_system` 自死锁，以及持锁等待授权框 | `fix(keysim)` | 已核实（读码） |
| K03 | P2 | 故障注入按键型生效 | `fix(keysim)` | 已核实（复现） |
| K04 | P2 | 拍发与随机报文的参数边界校验 | `fix(keysim)` | 已核实 |
| K05 | P2 | `state()` 不再持回放锁去做探测 | `fix(keysim)` | 已核实（读码） |
| K06 | P2 | SSE 推送加写超时，不在锁内阻塞写 | `fix(keysim)` | 已核实（读码） |
| K07 | P2 | `keysim upload` 读对页报文字段并沿用 attempt | `fix(keysim)` | 已核实（读码） |
| K08 | P2 | root 助手只接本次 keysim 的设备，且不能被卡死 | `fix(keysim)` | 读码，待复核 |
| K09 | P2 | root 服务不得以用户可写的二进制运行 | `fix(keysim)` | 已核实（读码） |
| K10 | P2 | 电子键 `--style fatigue` 与 `--style human` 的帮助文本如实化 | `fix(keysim)` | 已核实（复现） |
| K11 | P2 | 浏览器会话：句柄回收与注入在跳转后是否保持 | `fix(keysim)` | 疑似，须先复现 |
| K12 | — | 文档：`keysim/README.md`、过时表述、文档地图 | `docs(keysim)` | 已核实 |

各单元互不依赖，可单独回滚；唯一的顺序约束是 K12 最后做，以便引用前面各单元的提交号。

**不在本轮范围**（登记见 §7）：P3 级的资源泄漏与健壮性问题、非 Linux 平台的行为缺陷（取决于 D4）、`tests/drift.rs` 的断言加强、前端 `handKeyDecoder.js` 疑似重复上报间隔。

## 3. 不变约束

| 标识 | 必须保持 |
|---|---|
| KI01 | 和后端的接口约定不变：路径、请求头、手键 `{id,floorNumber}`、电子键 `{trainId,pageNumber}`、`startTrain` 的 GET 查询参数。keysim 不能要求后端或前端改动 |
| KI02 | 注入链路不变：任意页面可以 `fetch('http://127.0.0.1:18700/inject.js')` 拿到脚本，并连上 `/ws/serial`。这是设计上的跨源用法（`public/inject.js:4`、`public/index.html:1210`），K01 不能把它挡掉 |
| KI03 | 控制台页面 `public/index.html` 和 e2e 脚本 `keysim/scripts/e2e-electron-handkey.mjs` 现有的调用方式继续可用：两者都是 `POST` + `content-type: application/json`，前者同源，后者是 Node、不带 Origin |
| KI04 | 帧格式和节拍公式不变：串口字节、注入帧 `{data}`、1200/rate、1:3:1:3:5 配比、校准页计数。`tests/drift.rs` 的对表测试不得放宽 |
| KI05 | 控制台与 USB/IP 只监听 127.0.0.1，不变 |
| KI06 | keysim 与产品版本分开，不新增 release 资产；不碰 `backend/`、`bw-frontend/`、`backend-rust/` |
| KI07 | 每个单元带回归测试，测试断言的是可观察的行为（HTTP 响应、时间轴事件、锁不挂死），不断言实现细节 |

## 4. 逐项行为与验收

### K01 控制台拒绝跨源写请求（P1）

**问题**：
- `src/console.rs:306` 给所有响应加了 `access-control-allow-origin: *`，`/api/events` 的 SSE（`:439`）同样。请求处理（`:463-478`）既不校验 Host、Origin、Content-Type，也没有鉴权；请求体解析失败时静默当成 `{}`。
- `/api/device/link` 直接取请求体里的 `target`（`:242`）。`link_system` 先对它 `remove_file`（`src/serial.rs:272`），再建符号链接；`unlink_system` 也直接删它（`:311`）。

**失败场景**：`keysim serve` 运行期间（虚拟串口默认自动开启），开发者打开的任意网页执行

```js
fetch('http://127.0.0.1:18700/api/device/link', {method: 'POST', mode: 'no-cors', headers: {'content-type': 'text/plain'}, body: '{"target":"/home/u/.ssh/authorized_keys"}'})
```

text/plain 的 POST 属于浏览器的「简单请求」，不触发预检，于是该文件被删掉、换成指向 `/dev/pts/N` 的链接；带 `{"remove":true}` 就是直接删除。同样可以被跨站调用的还有 `/api/helper/install`（弹 pkexec 授权框）、`/api/browser/open`（用带 `--ignore-certificate-errors` 的持久 profile 打开任意 URL）、`/api/send`、`/api/port`；`/api/events` 能被跨站读取。由于不校验 Host，DNS rebinding（让恶意域名解析到 127.0.0.1）也能打通。

**行为要求**：
1. `/api/*` 与 `/api/events` 一律校验：
   - Host 必须是 `127.0.0.1:<端口>` 或 `localhost:<端口>`；
   - 带 Origin 时，Origin 必须是 `http://127.0.0.1:<端口>` 或 `http://localhost:<端口>`；不带 Origin 的放行，因为 Node/curl 不发 Origin，而浏览器的跨源 POST 一定会带；
   - `POST` 必须是 `content-type: application/json`；请求体不是合法 JSON 时返回错误，不再当成 `{}`。
   - 校验失败返回 403 + `{"ok":false,"error":...}`，不执行任何动作。
2. `/api/*` 与 `/api/events` 的响应去掉 `access-control-allow-origin`。只有 `GET /inject.js` 保留 `*`（KI02）。
3. `/api/device/link` 不再接受请求体里的 `target`，链接与移除都固定为 `SYSTEM_LINK`（`/dev/ttyUSB0`）。现有 UI（`public/index.html:1169-1175`）本来就不传 `target`，不受影响。
4. `/ws/serial` 是否也要校验 Origin，见 D1。

**验收**：
- 新增控制台集成测试（沿用 `tests/console_e2e.rs` 风格）：
  - 带外站 Origin 的 POST `/api/device/link`、`/api/send` 得 403，且无副作用（目标文件仍在、没有开始拍发）；
  - text/plain 的 POST 得 403；
  - Host 为 `evil.example:18700` 得 403；
  - 同源 Origin + JSON 正常；
  - 不带 Origin 的 JSON 正常；
  - `GET /inject.js` 仍带 `access-control-allow-origin: *`；
  - 请求体带 `target` 时被忽略。
- e2e 脚本 `node keysim/scripts/e2e-electron-handkey.mjs` 在本地全链路仍能跑通（需后端、vite、keysim serve，前置见脚本头注释）；跑不了就如实写「未跑」，不以单测代替。

### K02 `link_system` 自死锁（P1）

**问题**：
- `src/serial.rs:268` 拿到的 `device` 锁会一直持到函数结束。后面的 `let Some(device) = device.as_ref()` 只是变量重名（遮蔽），并不释放锁。
- 成功分支 `:276`、`:294` 调 `announce()` → `state()` → `device_state()`，在 `:91` 又锁同一把 `device` 锁。parking_lot 的 Mutex 不能在同一线程重复加锁，于是自己把自己卡住。
- 锁序也是反的：`state()` 先锁 kernel 再锁 device，`link_system` 先锁 device 再经 `announce` 锁 kernel。
- 走 pkexec 时，`link_system` 持着 device 锁一直阻塞到用户点完授权框（`:289`）。这段时间里回放线程（`:433`）和所有 `state()` 都会被卡住。

**失败场景**：只有 PTY、没有内核级虚拟口的环境（界面显示 `linkNeeded=true`），点「接入系统串口列表」；只要 symlink 成功或 pkexec 授权通过，这次请求就永久挂住，device 锁永不释放，之后回放线程、`/api/state`、`/api/port` 全部挂死。`tests/` 里没有任何用例覆盖这个端点。

**行为要求**：在锁里只读出 `device.path`（以及组 hint 所需的字段）就释放锁；`remove_file`、`symlink`、pkexec 和 `announce()` 都放在锁外执行。`unlink_system` 按同一规则检查。不改变成功/失败的返回 JSON。

**验收**：新增测试，在 PTY-only 配置下把目标设为临时目录里一个可写路径。这需要给 `link_system` 一个仅测试可用的目标参数，因为 K01 已经把对外的目标固定成了 `SYSTEM_LINK`。断言：
- 调用在限定时间内返回 `ok:true`；
- 随后 `state()` 也能在限定时间内返回。

用 `std::sync::mpsc` 加超时来判定「没挂死」，不靠 sleep。

### K03 故障注入按键型生效（P2）

**问题**：`src/faults.rs:112-124`（`dup_down`）与 `:148-162`（`micro_press`）不看 `timeline.key`，电子键时间轴也会被插进手键的按下/抬起事件。注释（`:177-178`）和测试 `electron_reports_zero_for_inapplicable_faults` 都声称 dupDown 在电子键上应报 0 次，但测试只断言了 `counts[1]` 和 `counts[2]`，漏了 `counts[0]`，所以没拦住。

**失败场景**：`keysim electron --text "ABCD EFGH IJKL MNOP QRST UVWX" --fault microPress --sink bytes --seed 3` 的电子键码流里夹着 `01 00`、`02 00 00` 这样的手键帧；dupDown 同样会插入 `01 00`。

**行为要求**：`dup_down`、`micro_press`，以及任何只对手键有意义的故障，在电子键上一律不注入、报 0 次，和 `missing_up` 现有做法一致。控制台与 CLI 的故障报告如实显示 0。

**验收**：
- 补全 `electron_reports_zero_for_inapplicable_faults`，对每一项手键专属故障断言 0 次；
- 新增断言：电子键时间轴加任意故障组合后，`--sink bytes` 输出里不出现手键帧前缀。

### K04 参数边界校验（P2）

**问题**：
- `src/keying.rs:356-361`：`group_size=0`（控制台 JSON `groupSize:0` 就能传进来）时，`per_char` 是 inf，`inf <= stroke_gap*2` 为假，校验被绕过，实测时间轴长度为 inf。`stroke_gap` 为负也不校验，`-50` 时第一个事件的时间是 -200。
- `src/console.rs:161-162`：`/api/message/random` 的 `groups`、`groupSize` 直接 `as usize`，`keying.rs:474-481` 会照这个数一直往字符串里追加。POST `{"groups":1e12}` 会把内存吃光，进程被系统杀掉，虚拟串口和 USB/IP 设备随之断开。

**行为要求**：
- `group_size` 必须 ≥1；
- `stroke_gap` 必须是有限值且 ≥0；
- 随机报文的 `groups`、`groupSize` 必须在 1 到上限之间，上限见 D2；
- 任何非有限数（NaN/inf）都拒绝；
- 拒绝时返回现有的 `Err(String)` → `{"ok":false,"error":...}`，CLI 打印错误并以非零退出。

**验收**：对每个边界各写一条单测（0、负数、超上限、NaN），断言返回错误而不是生成时间轴；再加一条控制台测试，`{"groups":1e12}` 立即得到错误响应。

### K05 `state()` 不再持回放锁去做探测（P2）

**问题**：`src/serial.rs:125-146` 在持有 `kernel` 和 `replay` 锁的同时调用：
- `install::helper_installed()`：unix socket 往返，读超时 30s，见 `attachd.rs:403`；
- `kernel::probe_all()`：会派生 modinfo、`sh -c command -v pkexec` 等子进程；
- `pty::probe()`。

回放线程每一帧都要这两把锁（`:424`、`:451`）。另外 `console.rs:120` 持着 browser 锁调 `browser::probe()`，一次最多派生 6 个 sh。

**失败场景**：拍发进行中，另开一个控制台标签页（SSE 一连上就会调 `state()`，见 `console.rs:443`），或者调一次 `/api/port`，回放线程就会被卡几十到几百毫秒；root 助手无响应时最长卡 30s。手键判定的容差是节拍的 ±51%（`keying.rs:45` 的 `skew`），点长只有几十毫秒时，被卡几十毫秒就会越界，污染成绩。

**行为要求**：`state()` 先在锁外做完所有探测（子进程、socket、文件系统），再短暂加锁、只拷贝内存里的字段。回放线程持锁期间不得发生子进程派生或 socket 往返。

**验收**：新增测试，用一个故意变慢的探测替身（注入点由实现决定，只限测试可见），在回放进行中并发调 `state()`。断言回放的相邻帧间隔与时间轴计划值的偏差，不超过 `skew` 规定的相对容差（`keying.rs:45`，不另设数字）。

### K06 SSE 推送加写超时（P2）

**问题**：`src/console.rs:299-302` 持着 events 锁对每个 SSE 连接 `write_all`，而这些连接都没有设写超时。对照注入通道 `src/serial.rs:161` 是设了 2s 写超时的。

**失败场景**：一个浏览器标签页被冻结（或进程被 `kill -STOP`），TCP 发送缓冲区几分钟就会被 progress 事件（约每秒 7 条）填满。之后所有广播（包括回放线程发出的 progress/log，`serial.rs:458`）都阻塞在 events 锁上，新的 SSE 连接也进不来（`console.rs:445`）。

**行为要求**：SSE 连接建立时设写超时，和注入通道同为 2s。写超时或写失败的连接从列表里移除。

**验收**：测试里起一个 SSE 客户端但不读数据，持续广播直到超过发送缓冲区容量；断言广播调用在「写超时 + 余量」内返回，且该客户端被移除。

### K07 `keysim upload` 读对页报文字段（P2）

**问题**：
- `src/main.rs:232-236` 从 `data.pageValue` 取本页报文。后端手键 `findPage` 返回的是 `GeneralTickerPatTrainContentVO`（`messageBody`/`messageKey`/…），电子键 `getPage` 返回的是 `PostTelegraphKeyPatTrainPageVO`（`messageVO`/`resolverMessage`/…），后端源码里没有任何 `pageValue` 字段。e2e 脚本取的是 `data.messageKey[].moresKey`（`e2e-electron-handkey.mjs:148`）。
- `attempt` 用的是 CLI 默认值 1（`main.rs:220`），没有用 `findPage` 返回的 attempt。训练被重置过（attempt≥2）后，`startTrain`/`uploadResult` 会拿到 208。

**失败场景**：
- `keysim upload --user … --train-id N` 不带 `--text`，必然报「后端没给本页报文」；
- 训练重置后上传，得到 208。

**行为要求**：手键从 `messageKey[].moresKey` 拼报文，与 e2e 脚本一致；电子键的取法实施前先对照前端电子键页的实际消费字段取证，结论写进提交正文。没有显式传 `--attempt` 时，沿用页响应里的 attempt。不改后端（KI01）。

**验收**：
- 用后端真实返回形状的 JSON 夹具（从后端 VO 字段名构造）做单测，断言手键、电子键都能取出报文，attempt 取自响应；
- 有条件时本地对真后端跑一次 `keysim upload`，结果如实记录。

### K08 root 助手加固（P2，待复核）

**问题**：
- `src/attachd.rs:130-146`：import 用的端口和 busid 都由调用方提供，root 助手照单连 `127.0.0.1:<端口>`，握手后把 socket 交给内核的 vhci。同一 uid 的任何进程都可以自己起一个 USB/IP 服务端，伪装成 HID 键盘或 U 盘让内核当真设备枚举。文件头注释称「只允许连回 127.0.0.1」，但这挡不住这种用法。
- `do_detach`（`:156-167`）可以拆掉任意 vhci 端口，包括其他真实的 USB/IP 设备。
- `:363-382` 的 accept 循环是单线程，`read_line`（`:372`）没有读超时，也没有长度上限。一个连上来却不发数据的客户端就能让 root 助手永久停摆，systemd 的 `Restart=on-failure` 对「挂住」无效。
- `peer_uid` 取失败时（`(Some(expected), None)`）落进 `_` 分支被放行，即取不到身份时默认允许。
- `stream.try_clone()?` 一失败就让整个 serve 返回，root 助手进程直接退出。

**行为要求**：
1. import 只接受描述符 VID:PID 为 keysim 自己的设备（`1209:5253`，实施时以 `usbip.rs` 常量为准）；detach 只拆由本助手 attach 过的端口。
2. 每个连接设读超时和单行长度上限；单个连接出错只断开这个连接，不让 serve 退出。
3. 取不到 `peer_uid` 时拒绝。

**验收**：
- 单测覆盖：VID/PID 不符被拒、未登记的端口拒绝 detach、`peer_uid` 为 None 被拒、超长行被拒、空闲连接超时后下一个请求仍能处理；
- 实施前先复核 attachd 是否另有限制入口，若已有限制则缩减本项并记录。

### K09 root 服务不得以用户可写的二进制运行（P2）

**问题**：`src/install.rs:35-38` 只有路径里含 `/target/release/` 或 `/target/debug/` 时，才把二进制复制到 `/usr/local/bin`。从 `~/bin`、`~/Downloads` 之类的地方运行 `install-helper` 时，`:160` 把这个用户可写的路径原样写进 systemd 单元，以 root 身份运行。之后同一 uid 的任何进程改写这个文件，下次重启服务或开机就拿到 root。另外 ExecStart 路径没有加引号，路径含空格时单元直接失效。

**行为要求**：见 D3。无论选哪种方案，ExecStart 都必须指向 root 拥有、普通用户不可写的路径，并按 systemd 语法加引号。

**验收**：
- 单测：给定不同源路径时，生成的单元文本里的 ExecStart 都在 root 目录下，且已加引号；
- 现有 `tests/install_replace.rs` 继续通过。

### K10 电子键疲劳模式与帮助文本（P2）

**问题**：
- 休息和犹豫只在组间隔（`Slot::Group`）触发，见 `src/timeline.rs:352-364`；电子键只用 `Slot::Gap`。电子键的字间节拍又由绝对的 target 时刻固定（`keying.rs:394-424`），所以 `--style fatigue` 在电子键上既不会变慢也不会休息。
- `main.rs` 的 help 说 `--style human` 带「疲劳-休息-恢复循环」，实际上休息只在 `fatigue` 里有，`human` 只有正弦慢漂移。

**行为要求**：电子键处理方式见 D5。help 文本改成如实描述：`human` 只有慢漂移，`fatigue` 有疲劳-休息-恢复（并注明是否适用电子键）。

**验收**：
- 按 D5 的选择加单测：要么断言电子键 + fatigue 返回明确错误，要么断言电子键 + fatigue 的时间轴里出现休息段、后段平均节拍慢于前段；
- help 文本由测试或人工核对，二者选一，在提交正文写明。

### K11 浏览器会话（P2，疑似，须先复现）

**问题**：
1. `src/browser.rs:196-211`：`Page.addScriptToEvaluateOnNewDocument` 登记在一个临时建立的 CDP 会话上，`open` 一返回这个会话就关闭了。疑似登记的预置脚本随会话一起失效，页面 F5 或整页跳转后，`navigator.serial` 又变回真实实现。e2e 脚本能通过，是因为它自己全程保持着 CDP 连接（`e2e-electron-handkey.mjs:222-246`），所以测不出这个问题。
2. `src/console.rs:288` 直接覆盖旧的 `Browser`，而 `Browser` 没有 Drop（`browser.rs:59-72` 只有手动 close）。旧的 Chromium 进程成了孤儿，还占着 `~/.keysim/browser-profile`，第二次启动必然报「没报出 DevTools 地址」。
3. `devtools_endpoint` 的 30s deadline 只在两次 `read_line` 之间检查，浏览器卡住不输出时会永久阻塞（`browser.rs:145`）。

**行为要求**：
- 第 1 点先手工复现：控制台 `/api/browser/open` 打开一个页面，F5 后在页面里检查 `navigator.serial` 是否仍是注入实现。复现成立就把 CDP 会话常驻在 `Browser` 里；不成立就删掉这一点并在提交正文记录。
- 第 2、3 点直接修：再次 open 前先关闭旧浏览器；`Browser` 实现 Drop 杀掉子进程；读 DevTools 地址时设真实的超时。

**验收**：
- 第 2 点：单测断言连续两次 open 都成功；
- 第 3 点：用一个不输出的假浏览器可执行文件，断言超时内返回错误；
- 第 1 点：手工复现结果写进提交正文，不为它写依赖真实 Chromium 的套件测试。

### K12 文档（最后做）

1. 新增 `keysim/README.md`，内容包括：
   - 定位：开发与测试用的模拟器，不是产品组件；
   - 子命令：`serve`/`doctor`/`hand`/`electron`/`upload`/`install-helper`，以 `src/main.rs` 的 USAGE 文本为准，链接过去，不复制整段；
   - 控制台端口 18700；
   - 各平台接虚拟串口的方式：Linux 用内核级/PTY 加 root 助手，Windows 用 com0com，支持范围按 D4；
   - `--force`、`--style human|fatigue` 的语义（K10 之后的口径）；
   - e2e 脚本的前置条件（MySQL + 后端 18001、`keysim serve` 18700、vite 18000、测试账号种子）与参数（`--domain zuxun|postJob`、`--matrix`、`--packaged`）；
   - 控制台的安全边界：只监听 127.0.0.1、拒绝跨源写请求（K01）。
2. `README.md:11` 的「桥接」改为「浏览器注入」，keysim 小节链接 `keysim/README.md`。
3. 清理已删除的桥接通道在注释里的残留：`src/main.rs:3`、`src/sinks.rs:1`、`:40`、`:42`、`src/kernel.rs:472`。`src/pty.rs:4` 指的是仓外的桥接程序，不属于 keysim 的通道，保留。
4. `src/morse.rs:3` 引用的 `tests/morse_drift.rs` 改为 `tests/drift.rs`。
5. `docs/reviews/2026-09-15-full-project-review.md` 的 R13 条目末尾补一句「已由独立 keysim CI job 闭合（`.github/workflows/build-quarkus-native.yml` 的 `keysim` job）」，不改原文。
6. `docs/README.md` 文档地图收录 `keysim/README.md` 与本规格；本规格实施完成后，在状态行里标注各单元的提交号。

**验收**：
- README 中的每条命令都在本地实际执行过一次（`--help` 级别即可），无法执行的标注原因；
- `grep -rn '桥接' keysim/src README.md` 只剩 `pty.rs:4`。

## 5. 验证命令

```bash
cd keysim
cargo test --locked      # 每个单元提交前跑受影响的测试，全部单元完成后跑一次全量
cargo build --locked
```

- CI 现有的 `keysim` job 只跑上面两条命令，只在 ubuntu-22.04 上跑，本轮不改 CI。
- 测试数量以实施时实际运行结果为准，写进提交正文；不沿用本规格的 85。
- K01 的 e2e 全链路、K07 的真后端上传、K11 的 F5 复现都属于本地手工验证，结果单独陈述，不能用单测通过代替。

## 6. 待确认取舍

| 编号 | 问题 | 推荐 | 备选 |
|---|---|---|---|
| D1 | `/ws/serial` 要不要校验 Origin？现在任意网页都能作为注入客户端接入，收到全部拍发字节 | **不校验**。注入本来就是要给任意被测页面用（KI02），拍发字节是模拟数据，不敏感；在 README 写明这条边界 | 加一个 Origin 白名单，可通过 `--allow-origin` 配置；代价是每换一个被测页面都要配置 |
| D2 | 随机报文的上限定多少 | `groups ≤ 1000`、`groupSize ≤ 20`。训练报文实际是百组级，留出余量 | 只限总字符数 ≤ 100k |
| D3 | root 服务的可执行文件放哪 | **一律复制到 `/usr/local/bin/keysim`**（root 拥有，权限 0755）再写进单元，不再区分是否构建产物 | 源路径不是 root 拥有就拒绝安装，提示用户先自己复制；更保守，但多一步手工操作 |
| D4 | 支持哪些平台 | **本轮只保证 Linux**：CI 只跑 ubuntu，root 助手和内核级虚拟口都是 Linux 专属。README 明确写 Windows 只支持 com0com + 控制台；macOS 不支持。`lib.rs` 里 attachd 的 `cfg(unix)` 收窄为 `cfg(target_os = "linux")`，让 macOS 至少能编译。其余非 Linux 问题（浏览器永远无界面、没有 sh、没有 HOME）登记不修 | 本轮一并修 Windows/macOS 的浏览器与 profile 路径；但没有 CI 和实机，修了也无法验收 |
| D5 | 电子键 + `--style fatigue` 怎么处理 | **明确报错**：「疲劳模式只适用于手键」。显式的边界，比静默无效好，改动小 | 给电子键实现疲劳：组间休息加节拍渐慢。要另定电子键疲劳的参数，并对表前端判定，工作量大 |
| D6 | K08/K11 是否放在本轮 | **K08 放本轮**（root 权限面，优先级高）；**K11 放本轮但先复现**，第 1 点不成立就只修第 2、3 点 | 两项都推到下一轮，本轮只做 K01–K07、K09、K10、K12 |

## 7. 登记不修（本轮之外）

只登记，不在本轮实施：

- **USB/IP**：
  - `usbip.rs:369-374` 按对端声明的长度直接 `vec![0; length]`，最多约 2GiB；
  - 读/写失败时不发 Detached，补完线程不退出；
  - 每次开关模拟器都泄漏一个监听线程和端口（`kernel.rs:537` 的 stop 不关闭模拟器）。
- **PTY**：`set_raw` 失败时两个 fd 泄漏；从端权限 0666，本机其他用户可读写（`pty.rs:85-89`）。
- **串口管理**：`open()` 不是原子的，并发两个 `/api/port` 会各起一套设备（`serial.rs:170-236`）。
- **控制台 HTTP**：
  - 没有读超时，请求头没有长度上限，线程数没有上限（`console.rs:393-421`）；
  - WebSocket 升级缺 `sec-websocket-key` 时不回响应（`:426-429`）。
- **CLI**：未知开关和非法数值被静默忽略（`main.rs:75-101`）。
- **安装**：
  - Windows 下 `replace_executable` 失败后不还原旧文件（`install.rs:54-74`）；
  - 经 pkexec 安装时，udev 规则的 OWNER 可能被写成 root（疑似，`:117-122`）；
  - `tests/install_replace.rs` 缺 `cfg(unix)`，Windows 上编译不过。
- **REST**：`rest.rs:328-355` 没有连接超时，并假设响应带 content-length；遇到 chunked 响应会解析失败（疑似）。
- **注入脚本**：`inject.js` 的 `port.writable` 恒为 null，没有实现 `setSignals`/`forget`；当前前端不用这些。
- **退出清理**：进程退出时不清理串口别名和系统链接（`main.rs:302` 没有信号处理）。
- **核心计算**：
  - `calibrate` 8 轮不收敛也静默返回，报错用的是内部缩放后的码率（`keying.rs:172-190`）；
  - `hand_plan` 先用未定标的节拍做可行性校验（疑似，`:195`、`:210`）；
  - seed 0 与 seed 1 的随机序列完全相同（`timeline.rs:86`）；
  - 小写字母直接被拒，不做大小写归一；
  - `micro_press` 在极高速时可能早于上一字抬起（疑似，`faults.rs:153-155`）；
  - `faults.rs:126` 关于缺抬起「下一次按下自恢复」的注释与前端 2000ms 窗口不符。
- **对表测试 `tests/drift.rs` 断言偏弱**：
  - `:296-304` 只检查源码里有 `CONTROL_TURN` 字样；
  - `:225-238` 没比对翻页/更正/下一组三个控制符的值；
  - `:203-217` 键位表测试不绑定键码与点划串的对应；
  - 没有测试驱动前端解码器去验证故障注入后的解码结果。
- **前端（不在 keysim 范围）**：`handKeyDecoder.js:41-44` 疑似在微按压时把间隔上报两次，需在前端另行取证。
