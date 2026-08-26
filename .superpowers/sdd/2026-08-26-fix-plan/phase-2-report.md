# Phase 2 报告：WebSocket 并发家族

结论：Task 2.1–2.6 全部完成 + 修复轮 1（ws-7），共 7 次提交，验收命令全绿（Tests run: 5, Failures: 0, Errors: 0）。

## 提交清单

| Task | 提交 | 内容 |
|---|---|---|
| 2.1 | `859ece7` | 三张房间表 `computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>())` 原子建房，删除 addRoom*/quitRoom* 全部尾部 `put` 覆盖；SimulationGlobal 加不变量注释 |
| 2.2 | `50827c2` | KeyPat/TelexPat/TickerPat 三端点补 `@OnError`：`log.error("ws error, session={}", session.getId(), t)` + 复用 onClose 清理并关连接 |
| 2.3 | `94ac2b6` | messageHandleReport(:463)/messageHandleRouter(:531) 取表后判空短路，REST 删房后客户端续发消息不再 NPE |
| 2.4 | `e4a4900` | `WebSocketService.sendInfoAll` 改为委托 `sendInfo(sid, message)` 定向发送（新发现 3；全仓 grep 确认零调用方，改动无涟漪） |
| 2.5 | `273f366` | 剩余 P1/P2 逐条清理（明细见下） |
| 2.6 | `474c616` | WebSocketUnionTest 扩展 50 轮并发进出泄漏测试 + 压出的 RoomModel.users CME 真实缺陷修复 |
| 修复轮1 | `79b251f` | 评审 Important：三处 quitRoom* 的 `if(isEmpty) remove(roomId)` 两步判空移除改 `computeIfPresent(roomId, (k, list) -> list.isEmpty() ? null : list)` 原子化——原写法在 A 读到 empty 后、remove 前，B 经 computeIfAbsent 拿到同一列表 add 自身，A 的 remove 会把 B 孤立（不在全局表、收不到广播）。复跑验收全绿 |

## 验证证据

- 验收命令：`flock /tmp/omp-mvn.lock -c "JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest='WebSocket*Test'"`
  → `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`（WebSocketUnionTest 4 + WebSocketSimulationTest 1，BUILD SUCCESS）。
- 每个实现 Task 提交前均通过 `mvnw compile`（EXIT=0）。
- **Task 2.6 红→绿证据（测试真的抓过缺陷）**：50 轮并发进出首轮运行即失败——第 28 轮 REMOVE_ROOM 广播丢失，Suppressed 栈为 `ConcurrentModificationException at WebSocketUnionService.removeRoom(:321)`。根因：`RoomModel.users` 是裸 ArrayList，EXIT_ROOM 的 `removeIf` 与 REMOVE_ROOM 的 `forEach` 跨连接线程并发（P1-2 同类、Union 家族未收录点）。修复：`RoomModel.users` 与 `addRoom` 建列表处 COW 化。复跑全绿，结束断言 `webSocketClientSet`/`onlineUsers`/`onlineRooms` 三表 size==0 通过。
- 用例B（roomMessageReachesRoomMemberOnly）暴露既有偶发竞态：未等 onOpen 异步注册完成即发消息（服务端注册前丢弃消息），并发负载下失败一次；补 `awaitRegistered` 三连消除（不弱化断言）。

## 静态门禁（Task 2.6）

`grep -n "private Session\|private .*UserModel" src/main/java/com/nip/ws/*.java`（顶层，不含 model/）实际输出：

```
WebSocketSimulationService.java:57:  // 仅 per-connection holder(persistData) 使用；共享 bean 上恒为 null，禁止读取
WebSocketSimulationService.java:58:  private Session session;
WebSocketSimulationService.java:59:  private SimulationUserModel userModel;
WebSocketUnionService.java:51:  private record Client(Session session, UserModel user) {}
WebSocketUnionService.java:55:  private static final ConcurrentHashMap<String, UserModel> onlineUsers = ...
```

判定：**通过**。共享实例字段仅剩 WebSocketSimulationService 的 holder 专用字段（带禁读注释，门禁明示允许）；Union 两行是正则字面误中——`record Client` 是 per-connection holder 类型声明、`onlineUsers` 是 static 并发表，均为修复本身而非共享连接态。WebSocketService/StartWebSocket/StatusWebSocket 的 `private Session` 字段已全部清除（重构为 `ConcurrentMap<String, Session>`）。

## Task 2.5 逐条对照（ws 分片 23 条 + 新发现 3，位置对照 agent://WsAudit）

| 编号 | 处置 |
|---|---|
| P0-1 / P0-2 | 已由 Task 1.6/1.7 修复（record Client + holder 解析），本轮核对无回归 |
| P1-1 | **域外跳过**：service/general/GeneralKeyPatService.countScore，非 ws/**，本波无人认领（见 concerns） |
| P1-2 / P1-3 | Task 2.1 修复 |
| P1-4 | 修复：quitRoomDisturb 原 removeIndex 不可达分支删除，改 `removeIf` + 空房 `remove(roomId)` |
| P1-5 | 已在 P0#9 修复中变为条件 put，本轮删除残余 else-put（2.1）；println 已不存在 |
| P1-6 | 修复：PAT_ROOM `computeIfAbsent`；joinUser 改 CopyOnWriteArrayList（ws/model）；清房改 `computeIfPresent` 原子判空删除 |
| P1-7 | 修复：KeyPat/Telex onOpen catch 补 `return`（含注释说明全 null userModel 的 NPE 链） |
| P1-8 | 修复：onClose 先判 `ROOM.get == null → close+return`；学员移除提出教员判空条件（改 removeIf）；空房 `computeIfPresent` 释放条目（原 ROOM 只增不减） |
| P1-9 | 修复：WebSocketService 重构为 `ConcurrentMap<String, Session>`，onlineId/重复删除整体消除；onClose 用 `remove(sid, session)` 条件移除防重连驱逐 |
| P1-10 | 修复：StartWebSocket 同样重构，单例 this 不再入集合，踢旧连接按 map put 返回值判定 |
| P1-11 | 修复：Simulation/KeyPat/Telex/Ticker/Start 广播全部改 `getAsyncRemote()` + `catch (Exception)`（单接收方失败不中断整轮）。例外：sendErrMessage/sendErrorMessage 保留 basic 同步写——仅 onOpen 拒接路径、close 前单线程单次写，async 可能在 close 前丢帧（已注释）。Ticker 无调用方的 sendMessageThrow 删除 |
| P1-12 | Task 2.3 修复；Ticker onMessage 同类 `PAT_ROOM.get` 判空一并补上 |
| P1-13 | **域外跳过**：service/general/GeneralTelexPatService static RANDOM（见 concerns） |
| P2-1 | 已由 Task 1.6 修复（runAsync().join() 已不存在），核对确认 |
| P2-2 | 修复：两处 runAsync 补 `.exceptionally(log.error)`，持久化失败不再静默；ManagedExecutor 替换属"不顺手重构"约束外，未做 |
| P2-3 | 修复：kickOutOld 先关再 `removeIf`，消除索引前移漏踢 |
| P2-4 | **域外跳过**：service/general/GeneralTickerPatService synchronized(this) |
| P2-5 | 修复：Simulation 补 @OnError（2.5），三 General 补 @OnError（2.2）；Union/WebSocketService/Start/Status 原有 |
| P2-6 | 修复：webSocketServerSet 死代码连同 onlineId 在 WebSocketService/StartWebSocket 一并删除 |
| P2-7 | 修复：StatusWebSocket 共享 session 字段删除，onClose/onMessage 改形参；无调用方的 sendMessage 删除 |
| P2-8 | **域外跳过**：七处 REST delete 不关 session（service/simulation、service/general）；ws 侧已用 2.3/Ticker 判空把后果从 NPE 降为丢弃+告警 |
| 新发现1 | 已由 P0#9 提交修复（quitRoomRouter 现有空房 remove），核对确认 |
| 新发现2 | 已由 Task 1.6 修复（sendInfo 统一判空），核对确认 |
| 新发现3 | Task 2.4 修复 |

## Concerns（需 Main 决策/转交）

1. **域外遗留 4 条**（本波各 Phase 域均未覆盖，建议排入后续批次）：P1-1（countScore parallelStream+先删后写，评审认为可升 P0）、P1-13（static ThreadLocalRandom）、P2-4（全局锁粒度）、P2-8（REST delete 不关 session——ws 侧已判空兜底，剩余是"客户端无感知"体验问题）。
2. `dto/general/GeneralPatTrainRoomUserDto.joinUser` 仍是裸 ArrayList（KeyPat/Telex 房间成员表，onOpen add/onClose removeIf/onMessage forEach 跨线程），评审未单列、该文件不在 ws/** 域内，未改；与 P1-6 同类，建议随域外批次 COW 化。
3. 广播 async 化后单帧失败只记日志不重试（与 Union 既有 send() 语义一致）；Ticker sendMessage 的 boolean 现表示"提交成功"而非"送达成功"，调用方清死会话逻辑仍按 isOpen 生效。
