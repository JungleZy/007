# 结论：WS 并发层本片 P0 0 / P1 0 / P2 8（7 条静态发现 + 1 条运行探针新发现 WS-P2-08，已修复）/ P3 3；上一轮 23 条 + 全项目 P0#8/#9/P1#10 全部已修复或收敛（P2-4 跨分片未修，P1-12/P2-2 部分修复）。

| 项目 | 内容 |
|---|---|
| 审查范围 | `src/main/java/com/nip/ws/**`（8 端点 + service/(RoomLifecycleLocks,RoomDeletionTransaction) + service/simulation/(SimulationGlobal,SimulationRoomLifecycle,SimulationSocketService) + service/general/(2 socket service) + model/ 12 文件）；只读交叉核对 REST delete 门面、GeneralKeyPatService.getTrainUserInfo、GeneralTickerPatService.findMessageBody、GeneralTelexPatService 报文生成 |
| 审查日期 | 2026-09-07 |
| 审查方式 | 静态阅读 + grep 取证（未运行构建/测试）；2026-09-07 补一次 prod jar 运行探针（联合端点三连接），产出 §3.1 的 WS-P2-08 |
| 计数 | P0 0 / P1 0 / P2 8（其中 WS-P2-08 由 2026-09-07 prod jar 运行探针补入，已修复）/ P3 3 |

> 源码相较 2026-08-26 版已大幅重构（端点=单例+静态 map+每连接 holder），上一轮行号已失效，本文行号以 HEAD(b9b9f22) 为准。

## 1. P0
无。上一轮两条 P0（联合/仿真端点实例字段）已从设计消除：Union 用 record Client 入 ConcurrentHashMap<String,Client> 并 resolveClient 按 session 身份反查（WebSocketUnionService.java:54/60/84-85/193-203）；Simulation 用 SimulationSessionHolder + removeCurrent(...,session) 按连接身份退房（WebSocketSimulationService.java:293-323 / SimulationRoomLifecycle.java:41-60）。

## 2. P1
无。上一轮 13 条见第 5 节；P1-12 空指针子项下沉为本轮 P2，P2-4 跨分片未修，余皆修复。

## 3. P2（7 条静态发现 + §3.1 的 1 条运行探针新发现）

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| WS-P2-01 | WebSocketSimulationService.java:447 (messageHandleDisturb,TOPIC_SELECT) | 干扰房含合成成员(未在 room_user 表、openLocked 合成路径 userType 恒 null) 且在册成员发 select | getUserType().compareTo(0) 对 null NPE，逃 @Transactional 后经 @OnError→onClose 撕连接、丢本条广播 | openLocked:107-111 未设 userType；:446-452 遍历全体调 getUserType |
| WS-P2-02 | WebSocketSimulationService.java:456 (messageHandleDisturb,TOPIC_RESULT) | 同上含合成成员且收 result | 同上 NPE→广播中断、连接被清理 | :454-459 无判空遍历 getUserType |
| WS-P2-03 | WebSocketSimulationService.java:531 (messageHandleReport,TOPIC_RESULT) | REPORT/RECEPT 成员 channel 为 null（DB 列空） | getChannel().compareTo(0) NPE→填报结果不下发。待运行验证 channel 列空值 | :531 无判空；openLocked:97-101 只保成员在册 |
| WS-P2-04 | WebSocketSimulationService.java:578-582 (messageHandleRouter,TRAIN_PLAY) | ROUTER 发报者或收报成员 channel 为 null | getChannel().compareTo(channel) NPE→转发中断。待运行验证 channel 列空值 | :578-582 未判空；合成成员 channel=-1 非 null |
| WS-P2-05 | WebSocketGeneralKeyPatService.java:105,109 (openLocked) | role(DB 列)为 null | getRole().compareTo 在 try(:78-83)之外、compute lambda 内 NPE→连接被清理(compute 契约保证 map 不损)。待运行验证 role 列空值 | getTrainUserInfo:503 可传 null role；解引用在 try 外 |
| WS-P2-06 | WebSocketGeneralTelexPatService.java:105,109 (openLocked) | 同 WS-P2-05（数据报端点同构） | 同上 NPE。待运行验证 | 与 Key 逐行同构；Ticker 因 openLocked:76-78 先判等在 try 内故免疫 |
| WS-P2-07 | WebSocketUnionService.java:114-170 (onMessage) 触发点 :338/:481/:508/:556 | 客户端发 data 非数字控制帧或 roomMessage 的 sendUser 指向不存在用户 | seatInspect/roomStatusChange/updateRoomUser/roomMessage 无 try/catch，异常冒泡 @OnError(:178-186)→userExit 把用户移出全部房间并广播 USER_EXIT 却不关 socket→僵尸连接，后续消息被 resolveClient 丢弃 | onError 仅 log+userExit 无 session.close |

### 3.1 本轮运行探针新发现（静态阅读未捕获，已修复）

| 编号 | 位置(file:line) | 触发条件 | 后果 | 状态 |
|---|---|---|---|---|
| WS-P2-08 | WebSocketUnionService.java:72-80 (onOpen)、:509-513 (roomMessage) | 端点无鉴权，`sid` 直接来自路径参数、`sendUser` 直接来自客户端报文，库里没有对应用户行 | 修复前 `userDao.findUserEntityById(...)` 返回 null 后直接 `getUserName()` → `NullPointerException`；onOpen 一路在异常抛出前已进入连接建立流程，未知 sid 可反复触发 | **已修复** |

**为什么静态评审没抓住：** Phase 6.5「WS 可空解引用与消息异常隔离」清的是可空**列**（`userType`/`channel`/`role`）在 `compareTo` 前的判空（WS-P2-01..06），联合端点「查库结果对象本身为 null」这一族不在其视野内，本文 WS-P2-07 也只把 `roomMessage` 的未知 `sendUser` 当作「异常冒泡到 @OnError」的触发源之一，没有单列成空解引用。它是 2026-09-07 对 prod jar（端口 18002）做 `ws://localhost:18002/websocketUnion/{sid}` 三连接探针时才暴露的：A（sid=1）被同 sid 的 B 顶下线，收到 `{"code":1,"data":"关闭连接"}` 后以 `code=1000` 正常关闭；B（sid=1 重连）`readyState=1` 保持在线——同 sid 置换语义正确；C（sid=`no-such-user-9999`）连接被关闭，但服务端日志抛 `NullPointerException: Cannot invoke "com.nip.entity.UserEntity.getUserName()" because "userEntity" is null`。

**修法（取同族既有范式，未发明第二套）：** 参照 `WebSocketGeneralKeyPatService:78-83` 的「查不到用户 → 返回 error transition」与 `WebSocketSimulationService:81-85` 的「发错误消息 + 关闭会话 + return」。`onOpen` 把用户查询提到 `existing` 置换逻辑之前，查不到则 `log.warn` → 向客户端发 `CodeConstants.CLOSE` + 文案「用户不存在，拒绝建立联合训练连接」→ `close(session)` → return，且**不写入** `webSocketClientSet`/`onlineUsers`（`WebSocketUnionService.java:72-80`）；`roomMessage` 查不到发送者则 `log.warn("房间消息投递失败，发送者不存在:{}")` + return（`:509-513`，与紧邻的「房间不存在」守卫 `:503-506` 同形）。

**回归测试：** `WebSocketUnionLifecycleTest.unknownSidIsRejectedWithoutRegisteringOrDereferencingNull`（`:164-178`，替身 `MissingUserDao` 在 `:283-288`）断言四件消费者可见的事：未进 `webSocketClientSet`、未进 `onlineUsers`、会话已关闭、客户端收到含「用户不存在」的消息；该类由 5 用例增至 6 用例。RED 实证：临时删掉 `onOpen` 守卫块后实测 `Tests run: 1, Failures: 0, Errors: 1` —— `java.lang.NullPointerException: Cannot invoke "com.nip.entity.UserEntity.getUserName()" because "userEntity" is null at com.nip.ws.WebSocketUnionLifecycleTest.unknownSidIsRejectedWithoutRegisteringOrDereferencingNull(WebSocketUnionLifecycleTest.java:169)`；恢复守卫后回绿。

## 4. P3（3 个）

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| WS-P3-01 | WebSocketGeneralKeyPatService.java:174-175/184、Telex:166-167/176、Ticker:208-209/218 (onClose) | 每次断线清理 | 在 ROOM.computeIfPresent 重映射函数内 sendMessage(async 投递)，CHM bin 锁内做出站 I/O；与 Union userExitLocked(:236-264 先收集再 compute 外发)不一致，影响轻微 | 对比 Union defer-outside |
| WS-P3-02 | WebSocketService.java:86-93/96-102 (onMessage) | 手键每条 2001/3001 日志 | 仍投 ForkJoinPool.commonPool（仅补 exceptionally，未改 ManagedExecutor）→高频挤占 commonPool（P2-2 部分修复） | :86/96 runAsync；:89-92/99-102 exceptionally |
| WS-P3-03 | WebSocketSimulationService.java:260/292/373/472/546 (@Transactional 由 onClose/onMessage this. 自调用) | 容器回调进入这些 @Transactional 方法 | 端点内自调用能否触发拦截器取决于 ArC；与 delete 刻意抽 RoomDeletionTransaction bean 不一致。若不生效则 roomDao.save/updateStats* 可能抛 TransactionRequiredException 或不提交。待运行验证（v1.0.0 已发布运行，倾向生效；测试均走 CDI 代理未覆盖此路径） | onClose:244-249/onMessage:362-369 均 this. 直调；delete 用 roomDeletionTransaction.run |

## 5. 上一轮遗留核销

### 5.1 2026-08-26-ws-concurrency-review.md（2×P0+13×P1+8×P2）

| 上轮编号 | 上轮结论 | 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| P0-1 | 联合端点实例字段致广播恒发最后连接者/串号/误退 | 已修复 | Client holder 入 ConcurrentHashMap；resolveClient 校验 client.session()==session（Union:54/60/84-85/199/575-582） |
| P0-2 | 仿真 onClose 按最后连接者判角色暂停整房 | 已修复 | SimulationSessionHolder + removeCurrent(...,session)（Sim:293-329/SimulationRoomLifecycle:41-60）；测试 studentDisconnectMustNotPauseRoomAsTeacher 锁定 |
| P1-1 | 多页评分 parallelStream 并发写+先删原始记录 | 已修复（跨分片） | GeneralKeyPatService:668、PostTelegraphKeyPatTrainService:357 改 .stream()；全仓无 parallelStream |
| P1-2 | SimulationGlobal 值为裸 ArrayList | 已修复 | ConcurrentMap<..,List<SimulationSessionHolder>>（SimulationGlobal:14/18/22）；值由 replace 建为 CopyOnWriteArrayList（:24-25） |
| P1-3 | addRoom* get-改-put 非原子 | 已修复 | 单次 rooms.compute（SimulationRoomLifecycle:23-37），onOpen 在条带锁内（Sim:74-80） |
| P1-4 | 干扰房 removeIndex 从不 remove 泄漏 | 已修复 | removeCurrent 用 members.remove 并空时返回 null 删 key（:55-57） |
| P1-5 | 通报房 remove 后无条件 put 回填空表 | 已修复 | 无 put 回填；computeIfPresent 空返 null（:57）；System.out.println 已删 |
| P1-6 | 手键 PAT_ROOM check-then-put + 裸 ArrayList | 已修复 | PAT_ROOM.compute（Ticker:96）；joinUser=CopyOnWriteArrayList（GeneralTickerPatTrainRoomUserModel:21） |
| P1-7 | 键/报 onOpen catch 后不 return | 已修复 | openLocked 返回 error transition（Key/Telex:80-83）；onOpen error 则 return（:65-69） |
| P1-8 | 键/报 onClose ROOM.get NPE + 清理裹在有教员里 + 无 remove | 已修复 | computeIfPresent 处理缺键；学员移除不依赖 groupUser（Key:178-187）；空房返 null；close 无条件（:191）；测试锁定 |
| P1-9 | onlineId 裸 ArrayList + 重复删他人 sid | 已修复 | onlineId 已删；CLIENTS ConcurrentMap（WebSocketService:47）+ remove(sid,session)（:68） |
| P1-10 | StartWebSocket this 入 Set 集合恒 1 | 已修复 | CLIENTS ConcurrentHashMap（StartWebSocket:24）put/remove(sid,session)（:28/39） |
| P1-11 | getBasicRemote 并发写抛 IllegalStateException 断广播 | 已修复 | 广播全改 getAsyncRemote（Sim:666/Key:235/Telex:227/Ticker:298/Union:595/WebSocketService:150/Start:56）；catch 放宽 Exception；getBasicRemote 仅存拒接前 sendErr |
| P1-12 | 仿真 messageHandleReport/Router 未判空遍历 + 可空 channel 解引用 | 部分修复 | 空列表判空已补(:376/477/551)；可空 channel/userType 的 compareTo 解引用仍在→WS-P2-01..04 |
| P1-13 | ThreadLocalRandom.current() 缓存 static 致同序列 | 已修复 | 无 static 字段；使用处直调 current().nextInt（GeneralTelexPatService:609/615/622） |
| P2-1 | 联合 onOpen runAsync().join() 假异步 + 空 join 死代码 | 已修复 | Union 全文件无 CompletableFuture/runAsync/join；onOpen 同步（:78），getUnionInfo 直读 map（:273-278） |
| P2-2 | WebSocketService.onMessage 阻塞 JDBC 入 commonPool 吞异常 | 部分修复 | 已补 exceptionally（:89-92/99-102）；仍用 commonPool→WS-P3-02 |
| P2-3 | kickOutOld 索引删元素漏踢 | 已修复 | kickOutOld 已移除；旧连接替换改 replace 的 removeIf（:27-33）+ closeReplaced |
| P2-4 | GeneralTickerPatService synchronized(this) 全局串行报文生成 | 未修复（跨分片 service/general） | 仍 synchronized(this) 裹 DB+生成+落库（GeneralTickerPatService:265-329），单例=全局锁，多实例失效 |
| P2-5 | 多 WS 端点缺 @OnError | 已修复 | 四端点均补（Sim:253/Key:194/Telex:186/Ticker:227）+ Union:178/WebSocketService:114/Start:43/Status:23 |
| P2-6 | webSocketServerSet 恒 null 死代码 | 已修复 | 字段已删（连同 onlineId） |
| P2-7 | StatusWebSocket session 实例字段共享 | 已修复 | 无实例字段，全形参接收 Session（StatusWebSocket:13-32） |
| P2-8 | delete 只清 map 不关 session；残留 System.out.println | 已修复 | delete 门面 lock→RoomDeletionTransaction.run(删库)→map.remove→closeRoomSessions（SimRouter:328-347/Report:210-224/Recept:214-228/RouterContent:216-229/Key:234-254/Telex:415-428/Ticker:242-255）；closeRoomSessions 逐个 close(CloseReason)；println 已删；测试 productionDeleteFacades... 锁定 |

### 5.2 2026-08-26-full-project-review.md P0#8/#9/P1#10

| 上轮编号 | 上轮结论 | 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| 全项目 P0#8 | WS 端点实例字段（=本片 P0-1 联合） | 已修复 | 同 P0-1 |
| 全项目 P0#9 | 同上（=本片 P0-2 仿真） | 已修复 | 同 P0-2 |
| 全项目 P1#10 | 两处 parallelStream + 房间裸集合 | 已修复 | parallelStream 改串行（GeneralKeyPatService:668/PostTelegraphKeyPatTrainService:357）；集合改 COW（SimulationRoomLifecycle:24-25/GeneralTickerPatTrainRoomUserModel:21/GeneralPatTrainRoomUserDto:21/RoomModel:29） |

### 5.3 测试锁定情况
- 已有回归保护：P0-1（WebSocketUnionTest 5 用例）、P0-2 及角色判定（WebSocketSimulationTest 4 用例）、P1-3/4/5 入退房原子性与零残留（WebSocketUnionLifecycleTest 全部 + WebSocketDeleteOpenAtomicityTest *CannotLeaveGhostRoom）、P1-8 幂等清理（WebSocketGeneralSessionLifecycleTest）、P2-8 删房关连接（productionDeleteFacades... / deletedGeneralRoomSnapshotsCloseEverySession）、WS-P2-08 未知 sid 拒连（WebSocketUnionLifecycleTest.unknownSidIsRejectedWithoutRegisteringOrDereferencingNull:164-178，该类由 5 用例增至 6 用例）。
- 仍无保护：WS-P2-01..06（无用例构造 null 字段成员）、WS-P2-07（无用例发畸形控制帧验证不误剔除）、WS-P3-03（无用例经容器真实路径验证 @Transactional 自调用落库）。

## 6. 待运行验证清单
1. WS-P2-03/04：确认 simulation_router_room_user.channel 是否可空且生产出现 null。
2. WS-P2-05/06：确认组训 role 列是否可空且出现 null。
3. WS-P3-03（最关键）：容器真实路径下发会写库的仿真消息（report TOPIC_TRAIN_PAUSE / router TRAIN_BEGIN|END），确认 roomDao.save/updateStats* 是否提交——判定端点内 this. 自调用 @Transactional 是否生效；若否，训练开始/暂停/结束/继续状态落库会失败，应比照 delete 抽独立 @Transactional bean。
4. WS-P2-01/02：确认未登记用户是否作为合成成员(userType=null)进入 DISTURB 房（openLocked:102-112 允许）并被在册成员的 select/result 遍历触发 NPE。

## 附录：已接受安全风险（不计入计数）
- 全部 WS 端点无鉴权：/websocket/{sid}、/websocketUnion/{sid}、/simulation/{id}/{roomId}、/generalKeyPatTrain/{uid}/{trainId}、/generalTelexPatTrain/{uid}/{trainId}、/generalTickerPat/{uid}/{trainId}/{role}、/startWebsocket/{sid}、/status 均不校验 token/deviceId，路径参数可任意伪造（可冒充他人/以 role=1 冒充教员）。内网部署已接受，沿用历史结论，不计入问题数。
