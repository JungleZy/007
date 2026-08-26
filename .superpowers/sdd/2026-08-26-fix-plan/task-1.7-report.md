# Task 1.7 报告：模拟房间断线错身份 + router 房间键泄漏（P0#9 + 新发现 3）

**状态：DONE。** 红→绿完成，单例字段读取点清零。

## 红（TDD 失败证据）

测试 `WebSocketSimulationTest.studentDisconnectMustNotPauseRoomAsTeacher`：
教员(userType=0/channel=0) + 学员(userType=1/channel=1) 进 REPORT(roomType=2) 房间，
连接顺序**先学员后教员**（共享字段 `this.userModel` 停在教员身份），再关学员连接。

```
org.opentest4j.AssertionFailedError: 学员断线不得按教员身份暂停整房：playStatus 必须保持 1 ==> expected: <1> but was: <0>
	at com.nip.ws.WebSocketSimulationTest.studentDisconnectMustNotPauseRoomAsTeacher(WebSocketSimulationTest.java:87)
```

红形态与预期完全一致：学员断线走了教员分支，整房 `playStatus` 被置 0（暂停）并落库。

### 红阶段插曲（测试自身修正，非缺陷形态偏差）

首版测试并发连两个客户端，撞上 **P1-3 的 put 覆盖竞态**（两个 onOpen 各自
`get→null→new ArrayList→put`，后者整表覆盖前者，房间列表恒为 1 人）。调试证据
（临时 debug 打印，已删）：两次 `addRoomReport` 均打出 `size=1`，且 classloader/map
identity 相同，排除测试与服务端静态隔离。测试改为**串行等待注册**
（`awaitRoomSize(roomId,1)` 后再连教员）绕开该竞态——P1-3 属 Phase 2 Task 2.1
（CopyOnWriteArrayList/computeIfAbsent），本任务不修。

## 修复内容（WebSocketSimulationService.java）

1. **onOpen**：删除 `this.userModel`/`this.session` 两处共享 bean 写入；改局部
   `SimulationUserModel userModel`，连接态只进 `persistData`。字段保留（holder 是本类
   new 实例，@Data getter 约 10 处被调），字段上加注释
   `// 仅 per-connection holder(persistData) 使用；共享 bean 上恒为 null，禁止读取`。
2. **addRoomDisturd/addRoomReport/addRoomRouter**：删掉第一个 `ws` 参数（原先传 `this`，
   全部读取都是单例字段泄漏面；评审文档 :85 同一建议），身份一律读 `persistData.getUserModel()`。
   仓内无本文件之外的调用点（grep 证据见下）。
3. **onClose :193/:195**：`quitRoomReport(this, roomId, id)` → `quitRoomReport(roomId, id)`。
4. **quitRoomReport**：从 `SimulationGlobal.reportRoom.get(roomId)` 按
   `getUserModel().getId().equals(userId)` 解析该连接的 holder；holder 为 null（已被
   kickOutOld 移除等）直接返回。教员/学员判定用 **holder 的 channel**（与原逻辑同源：
   channel==1 学员、否则教员），绝不读单例字段。移除逻辑改为 `simulations.remove(holder)` +
   `isEmpty ? remove(roomId) : put(...)`——顺带消除了原「先 remove(roomId) 再无条件
   put(roomId, 空表)」的键回插泄漏，并删掉残留 `System.out.println`（评审 P2-8 点名，
   本方法在任务范围内）。
5. **quitRoomRouter**：末尾按 brief 加 `isEmpty ? routerRoom.remove(roomId) : put(...)`，
   修 router 房间键泄漏。

## 单例字段读取点清零证据

- `grep 'this\.userModel|this\.session'` → **0 处**（写入与读取均已删除；字段声明本身保留）。
- `grep 'addRoom(Disturd|Report|Router)\(this|quitRoomReport\(this'` → **0 处**。
- 全文件所有 `getUserModel()/getSession()` 调用点逐一核对（onOpen/addRoom*/quitRoom*/
  messageHandle*/sendTraining/kickOutOld）：接收者全部是房间列表元素（holder）、
  `persistData` 或解析出的 `holder` 局部变量，无一经由共享 bean。
- `addRoomDisturd|addRoomReport|addRoomRouter|quitRoomReport` 全仓 grep：调用点仅在
  本文件内（其余命中为计划/评审文档），签名收窄无外部影响。

## 绿（验证证据）

```
JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=WebSocketSimulationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.nip.ws.WebSocketSimulationTest
[INFO] BUILD SUCCESS
```

断言覆盖：`playStatus` 保持 1（未被暂停）、学员被移出房间列表、教员仍在列表、教员连接仍打开。

## Concerns

1. **测试日志有一条 shutdown 后噪音**：`[Error Occurred After Shutdown] ... Arc.container() is null`
   ——教员连接在 Quarkus 停止后才触发服务端 onClose，Arc 已关。发生在应用关闭之后，
   不影响测试结果（BUILD SUCCESS）；Phase 2 并发改造后可复查。
2. quitRoomReport 现在对 kickOutOld 场景（旧连接 onClose 时 holder 已不在列表）直接返回，
   不再误删新连接——与 Task 1.6 修的同类缺陷在 REPORT 房间的表现，顺带被 holder 解析
   语义覆盖，但**未为该场景单独建测试**（超出本任务最小范围）。
3. P1-3 put 覆盖竞态在 addRoom* 仍在（本任务测试用串行注册绕开），归 Phase 2 Task 2.1。
