# WebSocket / 实时通道（前后端联合评审分片）
- 日期：2026-09-08 / 范围：8 个后端 @ServerEndpoint 与全部前端 new WebSocket( 调用点的跨栈契约（URI/参数/信封/消息格式/重连/心跳/单例状态） / 方法：只读取证

## 0. 分片结论与计数
| 定级 | 条数 | 责任分布(FE/BE/双侧) |
|---|---|---|
| J-P0 | 0 | — |
| J-P1 | 0 | — |
| J-P2 | 3 | FE 0 / BE 0 / 双侧 3（WS-J-P2-01、-02、-05）|
| J-P3 | 2 | FE 1（-03）/ BE 1（-04）/ 双侧 0 |
| 合计 | 5 | — |
（WS-J-P2-05 为安全项，标 [已接受风险口径]，按既定口径不计入 J-P0/J-P1。）

## 1. 契约清单与覆盖率
覆盖：后端 8/8 个 @ServerEndpoint 全部逐个静态取证；前端 13 个 new WebSocket( 命中点全部分类（3 个 vendored/demo 排除、1 个外部串口桥、9 个指向本后端）。未整读的 vendored 大文件（paho-mqtt.js / table.js 等）按性能约束仅 grep。

### WS 端点对账表
| # | 后端 URI 模板 | 后端 file:line | 前端调用点 file:line | URL 拼法 | 信封/消息一致性 | 结论 |
|---|---|---|---|---|---|---|
| 1 | /websocket/{sid} | WebSocketService.java:32 | common/ws/Ws.js:18 | `${window.wsUrl}/websocket/${userInfo.id}` | ResponseModel{code,data:String,map:Map}；Ws.js:44-62 读 code/JSON.parse(data.data)/map，入站码 2001/3001 对应 onMessage:83-103 | 一致 |
| 2 | /websocketUnion/{sid} | WebSocketUnionService.java:41 | unionJob/js/UnionWs.js:41 | `ws://${window.httpUrl}/websocketUnion/${userInfo.id}` | ResponseModel；UnionWs.run:66-69 原样 callback(data)；code 枚举 UnionWsCode↔UnionConstants | 一致（Electron 下 ws://+httpUrl == wsUrl）|
| 3 | /simulation/{id}/{roomId} | WebSocketSimulationService.java:48 | useBroadStudent.js:112、useBroadTeacher.js:104、disturbCode/js/train.js:224、lineNotify/components/ListenIn.vue:300、lineNotify/js/Issue.js:66 | 混用 `${window.wsUrl}` 与 `ws://${window.httpUrl}` | SimulationResponseModel{code(-1/1),sendName,receiveName,data:T}；前端双层解析 `JSON.parse(res.data)`→再 `JSON.parse(data.data)`（train.js:234-235 / Issue.js:82-84 / ListenIn.vue:314-315）；topic/body.* 字段对齐（online/offline/select/result/begin/ready/play/over）| 一致 |
| 4 | /generalKeyPatTrain/{uid}/{trainId} | WebSocketGeneralKeyPatService.java:33 | electronKeyZuXun student handKeyTrain.js:495 / teacher teacher.js:112；telexZuXun student datagramTrain.js:27 / teacher teacher.js:88 | `${window.wsUrl}${src}`（PublicSocket.js:10）| SocketResponseModel{code,sendName,receiveName,data:T}；前端双层解析 `JSON.parse(JSON.parse(e.data).data)`；发送 {topic,id}，后端读 msg.get(TOPIC)（BaseConstants.TOPIC="topic"）| 一致（2 段↔2 段）|
| 5 | /generalTelexPatTrain/{uid}/{trainId} | WebSocketGeneralTelexPatService.java:33 | datagramZuXun student datagramTrain.js:33 / teacher teacher.js:137 | 同上 | 同 4 | 一致（2 段↔2 段）|
| 6 | /generalTickerPat/{uid}/{trainId}/{role} | WebSocketGeneralTickerPatService.java:31 | handkeyZuXun student handKeyTrain.js:818（`.../0`）/ teacher teacher.js:114（`.../1`）| 同上 | 后端 openLocked:76-78 校验 user.getRole()==role | 活跃调用一致（3 段↔3 段，role 0/1 正确）；死代码 teacherBack.js:130-131 仅 2 段缺 {role} → 见 WS-J-P3-03 |
| 7 | /startWebsocket/{sid} | StartWebSocket.java:16 | 无（全前端 grep `startWebsocket` 0 命中）| — | — | 无前端调用者（死/外部）→ 见 WS-J-P3-04 |
| 8 | /status | StatusWebSocket.java:8 | 无（全前端 grep `/status` WS 0 命中）| — | onMessage 回 "pong" | 无前端调用者（健康探针/死）→ 见 WS-J-P3-04 |

### 前端 new WebSocket( 命中点归属
| file:line | 目标 | 归入表行 / 排除理由 |
|---|---|---|
| common/ws/Ws.js:35 | /websocket | 行 1 |
| unionJob/js/UnionWs.js:57 | /websocketUnion | 行 2 |
| components/BroadcastTeachTrain/js/useBroadStudent.js:112 | /simulation | 行 3 |
| components/BroadcastTeachTrain/js/useBroadTeacher.js:104 | /simulation | 行 3 |
| unionJob/disturbCode/js/train.js:224 | /simulation | 行 3 |
| unionJob/lineNotify/components/ListenIn.vue:300 | /simulation | 行 3（与 Issue 互斥 v-if/v-else，LineNotifyTrain.vue:2-3）|
| unionJob/lineNotify/js/Issue.js:66 | /simulation | 行 3 |
| common/ws/PublicSocket.js:10 | wsUrl+src | 行 4/5/6（通用封装，被 general-pat 训练 js 复用）|
| common/ws/MessageWebSocket.js:82 | ws://localhost:18765/echo?username= | 外部串口桥（WebSerial），非本仓库后端端点 → 见下 |
| common/mqtt/paho-mqtt.js:1042,1044 | mqtt broker | 排除：vendored MQTT 库，非 WS 端点 |
| views/demo/chil/IMDemo.vue:144 | ws://10.10.0.210:18766/erp-ws | 排除：demo，外部固定 IP |
| views/demo/hanzi/Index.vue:13 | ws://10.10.0.210:3333/ws | 排除：demo，外部固定 IP |
| views/demo/vico/Index.vue:77 | ws://10.10.0.232:8081/webSocket | 排除：demo，外部固定 IP |

MessageWebSocket.js:82 硬编码 `ws://localhost:18765/echo`：本仓库后端 8 个端点无一为 `/echo`/18765，且 index.html 全套注入端口为 18001；判定为**本机串口/流量桥（外部进程），不在本仓库后端**。契约风险：URL 忽略 window.wsUrl，桥不在时 onclose→reconnect（:104-106，3s）永久空转，且 store/composable 在模块导入期建立（FE 单侧 §234 已列）——此项非本后端契约，故不新报。

## 2. 缺陷条目

**WS-J-P2-01 / PublicSocket 无退避、不看关闭原因的 1s 无限重连 × 后端正常业务流里的握手/消息拒绝 → 自伤式重连+DB 查询风暴**
- 前端证据：common/ws/PublicSocket.js:32-37（onclose 只要 flag 就 1000ms 后 ws_connect，无退避、无上限、不读 CloseEvent.code/reason）。
- 后端证据：general-pat onOpen 在**正常**条件下就会 close 连接——WebSocketGeneralKeyPatService.java:65-68 与 WebSocketGeneralTickerPatService.java:57-60 在 openLocked 抛错（getTrainUserInfo 查不到该 uid+trainId，KeyPat:76-83；Ticker role 不匹配 :76-78）时 sendErrMessage+close；onMessage 在房间被 REST 删除后 WebSocketGeneralKeyPatService.java:128-130 / Ticker:146-148 返回“房间不存在”。每次 openLocked 都走一次 DB（generalXxxPatService.getTrainUserInfo）。
- 触发条件 → 后果：训练未就绪/角色配置与前端硬编码 0/1 不符/教员已删房但学员页仍开着 → 后端每次都拒/短路，前端每 1s 重拨一次并触发一次 DB 查询，单个卡住的客户端即制造 1 QPS 的握手+DB 负载与日志刷屏；多个客户端叠加放大。
- 责任归属：双侧协同。最小修复：FE 在 PublicSocket 重连加指数退避+抖动+上限，且在 onclose 里读 CloseEvent（服务端主动 close/错误信封 code=-1 时停止重连或大幅退避）；BE 无需改逻辑，但应保证拒绝路径可被前端区分（当前 err 帧 code=-1 已可用作停连信号）。
- 与单侧关系：原 FE #6 / §209（FE 单侧 HIGH「重连无退避/上限/心跳」）。联合视角改变**触发面与归属**：拒绝发生在后端正常业务流（非仅“后端宕机”），故须结合后端 close 语义修，归属由 FE 单侧升级为双侧协同。

**WS-J-P2-02 / 后端未配 WS idle-timeout + 前端无应用层心跳 → 半开连接在静态会话表堆积**
- 后端证据：backend/src/main/resources/application.yml:37-38 仅 `quarkus.websocket.dispatch-to-worker: true`，全 yml 无 WS max-idle / `quarkus.http.idle-timeout` 覆盖；会话全部挂在静态表 WebSocketService.java:47(CLIENTS)、WebSocketUnionService.java:60-62(webSocketClientSet/onlineRooms/onlineUsers)、GeneralTickerPatService.java:38(PAT_ROOM)。
- 前端证据：common/ws/ 全目录无 setInterval/heartbeat/ping（FE 单侧 §211 已述），Ws.js/UnionWs.js/PublicSocket.js 均无心跳。
- 触发条件 → 后果：NAT/代理静默丢弃 TCP 后形成半开连接，两侧都不主动探活 → 陈旧 holder 永久滞留静态房间表，onClose 永不触发，房间成员数虚高、被顶号判定与广播对象错乱。
- 责任归属：双侧协同。最小修复：BE 配置 WS 最大空闲（Undertow/quarkus WS idle-timeout）主动回收；FE 加应用层心跳看门狗，超时强关并按 WS-J-P2-01 的受控策略重连。
- 与单侧关系：原 FE §211（FE 单侧 HIGH，仅要求前端加心跳）。联合视角补充后端**同样未配** idle-timeout 的 file:line 证据，归属由 FE 单侧升级为双侧协同。

**WS-J-P2-05 / 全部 WS 端点无 token/deviceId 鉴权，身份取自路径参数，前端直传 localStorage id [已接受风险口径]**
- 后端证据：所有端点身份均为 @PathParam，无 token 校验——WebSocketSimulationService.java:71（id 直接来自路径）、WebSocketUnionService.java:68-74（注释明确“端点无鉴权，sid 直接来自路径参数”，仅查库存在性）、WebSocketService.java:53、general-pat 以 uid/trainId 直连。REST 的类级 @JWT 拦截不覆盖 ws/ 包。
- 前端证据：common/ws/Ws.js:16-18、unionJob/js/UnionWs.js:39-41、useBroadStudent.js:112、disturbCode/js/train.js:224、lineNotify/js/Issue.js:42+66 均以 `JSON.parse(localStorage.getItem('userInfo')).id` 作为路径身份，未附 token/deviceId。
- 触发条件 → 后果：篡改路径中的 id/sid 即可以他人身份连入任意房间/联合训练并收发广播（跨用户串号），或以任意在册 id 顶掉他人连接。纯安全缺口，需主动篡改。
- 责任归属：双侧协同。最小修复：BE 在 @OnOpen 握手阶段校验 token（Sec-WebSocket-Protocol 或 query 传 token 后端比对 getUserByToken）；FE 连接串附带 token 并改由后端从 token 解析身份而非信任路径 id。
- 口径：内网部署既定接受风险，除主动篡改外无功能/数据后果，故不计入 J-P0/J-P1；仍记录以备外网化时收口。

**WS-J-P3-03 / 死代码 teacherBack.js 的 /generalTickerPat 少 {role} 段，且 import 不存在的 common/websocket/PublicSocket.js**
- 前端证据：organization/{telexZuXun,datagramZuXun,electronKeyZuXun,handkeyZuXun}/train/teacher/js/teacherBack.js 拼 `/generalTickerPat/${userInfo.id}/${trainId.value}`（如 telexZuXun teacherBack.js:129-131）仅 2 段；其中 handkeyZuXun teacherBack.js:3 还 `import PublicSocket from '../../../../../../common/websocket/PublicSocket.js'`（本仓库实际路径为 common/ws/PublicSocket.js）。
- 后端证据：WebSocketGeneralTickerPatService.java:31 模板 `/generalTickerPat/{uid}/{trainId}/{role}` 需 3 段。
- 触发条件 → 后果：若该视图被启用，2 段 URL 与 3 段模板不匹配 → 握手 404，且坏 import 会直接构建失败。但全前端 grep `teacherBack` **0 命中**（无任何 .vue 引用、无 *Back.vue、config/router 无引用）→ 判定为未被引用的死代码，当前无运行期后果。
- 责任归属：FE。最小修复：删除这批死 teacherBack.js（或若确需复盘教员端，补 `/1` 角色段并修正 import 路径）。
- 与单侧关系：FE 单侧未列此参数个数错配（单侧无法得知后端需 {role}），属联合新发现，但因死代码降级 J-P3。

**WS-J-P3-04 / 后端 /status 与 /startWebsocket/{sid} 无前端调用者**
- 后端证据：StatusWebSocket.java:8-11（/status，onMessage 回 pong）、StartWebSocket.java:16（/startWebsocket/{sid}，含踢旧连接逻辑）。
- 前端证据：全前端 grep `/status`（WS）与 `startWebsocket` 均 0 命中；MessageWebSocket.js 连的是外部 :18765/echo，非这两个端点。
- 触发条件 → 后果：无前端消费者，属死/外部端点，长期存在会误导后续维护（以为有客户端依赖而不敢删/改）。
- 责任归属：BE。最小修复：确认无外部探针依赖后删除，或在端点上注释标明用途/外部调用方。
- 与单侧关系：跨栈可见性结论，单侧后端评审不涉及前端调用面，非重复。

## 3. 已核实为「一致/无问题」的关键契约（防止误改）
- **URI/参数个数（活跃路径）全部对齐**：/websocket、/websocketUnion（Electron 下 `ws://`+httpUrl 与 wsUrl 同解为 `ws://host:18001`）、/simulation（5 个调用点均 2 段）、/generalKeyPatTrain 与 /generalTelexPatTrain（2 段↔2 段）、/generalTickerPat 活跃调用（handkey student `.../0`、teacher `.../1`，3 段↔3 段且 role 值与后端 openLocked:76-78 校验一致）。
- **三套响应信封被前端一致消费**：ResponseModel（/websocket、/websocketUnion，data:String+map:Map，Ws.js:44-62 分别处理）；SimulationResponseModel 与 SocketResponseModel（code -1/1 + data 为**双层 JSON 字符串**，前端一律 `JSON.parse(JSON.parse(e.data).data)` / `JSON.parse(res.data)`→`JSON.parse(data.data)`，见 handKeyTrain.js:825、train.js:234-235、Issue.js:82-84、ListenIn.vue:314-315）。此双层编码是易踩坑的隐式契约，两侧均正确遵守。
- **消息 topic/type 字段名与取值对齐**：后端 BaseConstants.TOPIC="topic"/TYPE="type"（BaseConstants.java:15,19）与 SimulationDisturdTopicEnum（begin/end/select/result/online 等）↔ 前端发送/分支一致；report 房结束帧 useBroadTeacher.js:487-490 发 `{type:4,count:totalTime}` 与后端 messageHandleReport:505-506 `Integer.parseInt(mesg.get("count"))` 对齐（count 确有发送）。
- **单例状态共享根因已关闭（重连风暴 × 单例的联合后果已大幅缓解）**：8 个端点均 @ApplicationScoped 单例，但**无任何共享可变实例字段**——连接态一律挂静态 ConcurrentMap：WebSocketService.java:37-47（仅 @Inject 无状态 DAO + `static final ConcurrentMap CLIENTS`，注释标注为 P1-9/P1-10 根因修复）、WebSocketSimulationService.java:53-58（仅 @Inject DAO，房态在 SimulationGlobal 静态表 + SimulationRoomLifecycle）、WebSocketUnionService.java:48-62（@Inject userDao + 三张 static 表）、GeneralKeyPatService.java:38-40 / GeneralTickerPatService.java:36-38（@Inject service + `static final Map ROOM/PAT_ROOM`）。且 onClose/onError 采用**条件移除/同会话校验**（StartWebSocket:39 `CLIENTS.remove(sid, session)`、WebSocketService:68、SimulationRoomLifecycle.java:79-86 sameConnection、WebSocketUnionService.java:222-232 resolveClient）→ 同 sid 重连后旧连接的 onClose 不会摘掉新连接。故前端无限重连**不会**再导致会话表泄漏/身份串号/顶号（除半开连接场景，见 WS-J-P2-02）。此为本轮要求的“后端实例字段证据”的正向结论：**不存在**可被跨连接共享的实例字段。
- **同用户同房单连接不变式**：SimulationRoomLifecycle.replace:27-34 按 userId 去重并 close 旧连接；lineNotify 的 Issue/ListenIn 为 v-if/v-else 互斥（LineNotifyTrain.vue:2-3），不会在同一客户端对同房建双连接。

## 4. 与单侧评审的定级变化
- FE #6 / §209（HIGH「重连无退避/上限/心跳」，FE 单侧）→ 联合 **WS-J-P2-01**：触发面从“后端宕机”扩大到“后端正常业务拒绝（删房/角色不符/训练未就绪）”，且每次重连触发一次后端 DB 查询；归属由 **FE → 双侧协同**（FE 加退避且读关闭原因）。
- FE §211（HIGH「无心跳→半开」，FE 单侧仅要求前端）→ 联合 **WS-J-P2-02**：补充后端 application.yml:37-38 未配 WS idle-timeout 的证据；归属由 **FE → 双侧协同**。
- FE §214/§216（HIGH「部分 socket 用 httpUrl 拼 URL，Web 部署畸形」）→ **不升级、不重报**：联合确认在**目标 Electron 外壳**下 index.html:67/73 使 `window.httpUrl`=`host:port`（无 scheme）、`window.wsUrl`=`ws://host:port`，故 `ws://${httpUrl}` 与 `${wsUrl}` 同解，UnionWs.js:41 / train.js:224 / ListenIn.vue:300 / Issue.js:66 在生产 Electron 下**可用**；仅非 Electron 浏览器 https 分支（index.html:80-81 httpUrl=`https://…/data`）才畸形，与 FE 单侧结论一致，联合视角未改变其定级。

## 5. 未能验证的部分（缺失前提）
- 纯静态取证，未启动服务（分片约束不跑服务），故握手 404、1s 重连风暴、半开堆积均为基于代码路径的推断，未做运行期抓包/压测复现。[INFERENCE]
- general-pat 的 role/训练在册数据来自 DB（getTrainUserInfo），无法静态断定生产库中 uid+trainId 的 role 是否恰为前端硬编码的 0/1；若不符则 Ticker onOpen 必拒 → 叠加 WS-J-P2-01 成永久重连。此依赖运行期 DB 数据，未证。[INFERENCE]
- 后端向 /websocket 推送的业务码（200/201/1000/1001）由 REST 侧 sendInfo 触发，推送点在 ws/ 之外，未逐一核对其 payload 与 Ws.js:47-58 消费的 data.data/data.map 形态；仅确认信封字段名一致。[INFERENCE]
- teacherBack.js 判定为死代码依据全前端 grep `teacherBack` 0 命中（含 config/router、organization 全树）；若存在 import.meta.glob 之类动态装载则结论需修正，本轮未见此类用法。[INFERENCE]

## 附录 A：结构化缺陷条目（5 条，子代理原始输出）

### [J-P2] PublicSocket 无退避重连 × 后端正常业务拒绝 → 1s 自伤式重连与 DB 查询风暴

- 锚点：`frontend/src/common/ws/PublicSocket.js:32-37`（置信度 0.7）

前端 common/ws/PublicSocket.js:32-37 的 onclose 只要 flag 为真就固定 1000ms 后无条件重拨，无退避、无上限、且不读取 CloseEvent.code/reason。后端 general-pat 端点在正常业务条件下即主动 close：WebSocketGeneralKeyPatService.java:65-68 与 WebSocketGeneralTickerPatService.java:57-60 在 openLocked 抛错（KeyPat:76-83 查不到 uid+trainId；Ticker:76-78 role 与库中角色不符）时 sendErrMessage 后 close；房间经 REST 删除后 onMessage（KeyPat:128-130 / Ticker:146-148）返回“房间不存在”。每次 openLocked 都执行一次 getTrainUserInfo DB 查询。触发条件：训练未就绪 / 角色配置与前端硬编码 0/1 不符 / 教员已删房但学员页仍开着。后果：后端每次拒绝，前端每 1s 重拨一次并触发一次 DB 查询，单个卡住客户端即制造约 1 QPS 的握手+DB 负载与日志刷屏，多客户端叠加放大。责任=双侧协同：FE 在 PublicSocket 重连加指数退避+抖动+上限，并在 onclose 读 CloseEvent（服务端主动 close 或错误信封 code=-1 时停连或大幅退避）；BE 逻辑无需改（err 帧 code=-1 已可作停连信号）。相对 FE 单侧 #6/§209（仅“后端宕机”场景、FE 归属），联合视角把触发面扩大到正常业务拒绝并升级为双侧。

### [J-P2] 后端未配 WS idle-timeout 且前端无心跳 → 半开连接堆积于静态会话表

- 锚点：`backend/src/main/resources/application.yml:37-38`（置信度 0.68）

后端 backend/src/main/resources/application.yml:37-38 仅设 quarkus.websocket.dispatch-to-worker: true，全 yml 无 WS 最大空闲/idle-timeout 覆盖；所有连接态挂在静态表（WebSocketService.java:47 CLIENTS、WebSocketUnionService.java:60-62 webSocketClientSet/onlineRooms/onlineUsers、WebSocketGeneralTickerPatService.java:38 PAT_ROOM）。前端 common/ws/ 全目录无 setInterval/ping/心跳（Ws.js、UnionWs.js、PublicSocket.js 均无）。触发条件：NAT/代理静默丢弃 TCP 形成半开连接，两侧都不探活。后果：陈旧 SessionHolder 永久滞留静态房间表、onClose 永不触发，导致房间成员数虚高、顶号判定与广播对象错乱。责任=双侧协同：BE 配置 WS 最大空闲主动回收半开连接；FE 增加应用层心跳看门狗，超时强关并按受控策略重连。相对 FE 单侧 §211（仅要求前端加心跳），联合补充了后端同样缺配的证据，归属升级为双侧。

### [J-P2] 全部 WS 端点无 token 鉴权，身份取自路径参数（前端直传 localStorage id）

- 锚点：`backend/src/main/java/com/nip/ws/WebSocketUnionService.java:68-74`（置信度 0.66）

[已接受风险口径] 后端所有 @ServerEndpoint 身份均来自 @PathParam 且无 token 校验：WebSocketSimulationService.java:71（id 直接取路径）、WebSocketUnionService.java:68-74（注释即写明“端点无鉴权，sid 直接来自路径参数”，仅查库存在性）、WebSocketService.java:53、general-pat 以 uid 直连；REST 的类级 @JWT 拦截不覆盖 ws/ 包。前端以 JSON.parse(localStorage.getItem('userInfo')).id 作路径身份、未附 token/deviceId（Ws.js:18、UnionWs.js:41、useBroadStudent.js:112、disturbCode/js/train.js:224、lineNotify/js/Issue.js:66）。触发条件：篡改路径 id/sid。后果：可冒任意在册用户连入任意房间收发广播（跨用户串号）或顶掉他人连接。按内网部署既定接受风险，除主动篡改外无功能/数据后果，故不计入 J-P0/J-P1；外网化时须收口。责任=双侧协同：BE 在 @OnOpen 用 token 解析身份（getUserByToken）而非信任路径；FE 连接串携带 token。

### [J-P3] 死代码 teacherBack.js：/generalTickerPat 少 {role} 段且 import 路径错误

- 锚点：`frontend/src/views/manage/organization/telexZuXun/train/teacher/js/teacherBack.js:129-131`（置信度 0.6）

后端 WebSocketGeneralTickerPatService.java:31 模板为 /generalTickerPat/{uid}/{trainId}/{role}（3 段，openLocked:76-78 校验 role）。前端 organization 各训练类型的 teacherBack.js 却拼 /generalTickerPat/${userInfo.id}/${trainId.value}（如 telexZuXun/train/teacher/js/teacherBack.js:129-131）仅 2 段，缺 {role}；handkeyZuXun 版 teacherBack.js:3 还 import 了本仓库不存在的 common/websocket/PublicSocket.js（真实路径 common/ws/PublicSocket.js）。若启用则握手 404 且构建失败。但全前端 grep `teacherBack` 0 命中（无任何 .vue/router 引用，无 *Back.vue），判定为未被引用的死代码，当前无运行期后果，故降级 J-P3。责任=FE：删除这批死文件；若确需复盘教员端，补 `/1` 角色段并修正 import 路径。此参数个数错配为联合新发现（单侧无法得知后端需 {role}）。

### [J-P3] 后端 /status 与 /startWebsocket/{sid} 无前端调用者（死/外部端点）

- 锚点：`backend/src/main/java/com/nip/ws/StatusWebSocket.java:8-11`（置信度 0.62）

后端 StatusWebSocket.java:8-11（/status，onMessage 回 pong）与 StartWebSocket.java:16（/startWebsocket/{sid}，含踢旧连接逻辑）在本仓库前端无任何调用者：全前端 grep `/status`(WS) 与 `startWebsocket` 均 0 命中，MessageWebSocket.js 连的是外部 ws://localhost:18765/echo 而非这两个端点。后果：无消费者的端点长期存在会误导维护（以为有客户端依赖而不敢删改）。责任=BE：确认无外部探针依赖后删除，或在端点注释标明用途/外部调用方。此为跨栈可见性结论，单侧后端评审不涉及前端调用面，非重复报告。
