# Task 1.6 报告：P0#8 联合训练 WebSocket 串扰修复

**结论：修复完成。用例A 红→绿，用例B 绿，`this.sUser`/`this.session` 零命中。**

## 改动

- `src/main/java/com/nip/ws/WebSocketUnionService.java`（按 brief Step3 全部 7 步）：
  1. 新增 `private record Client(Session session, UserModel user)`；`webSocketClientSet` 改 `ConcurrentHashMap<String, Client>`。
  2. 删除实例字段 `session`/`sUser` 与 onOpen 的 `CompletableFuture.runAsync(...).join()` 包装（改直接同步执行）；onOpen 末尾 `session.getUserProperties().put("sid", sid)` 后放入 `new Client(session, userModel)`。
  3. 身份管道：新增 `resolveClient(Session)`（userProperties 取 sid → `webSocketClientSet.get`），onMessage/onClose/onError 入口统一走它，`me == null` 直接返回；`userJoin/userExit/getUnionInfo/getRoomInfo/addRoom/ur/joinRoom/exitRoom` 改签名接收 `Client me`。
  4. 出站统一静态 `send(Session, ResponseModel)`（asyncRemote + try/catch）；`sendInfo(String sid, ...)` 及 `updateRoomUser/removeRoom/roomMessage/seatInspect/seatInspectReply/roomStatusChange` 的 `get()` 全部判空，缺失时 `log.warn` 丢弃，不再 NPE；删除实例方法 `sendMessage` 与 `sendInfo(Session, ...)`（仓内无外部调用者，已 grep 确认）。
  5. onMessage 第一行赋值修复：`msg.setSendUser(...)`（原来连续两次 `setReceiveUser`，sendUser 被丢弃）。
  6. `onClose(Session)` / `onError(Session, Throwable)`：resolveClient 后 `userExit(me)` 清 map、清房间成员（幂等：条目已清则直接返回）。
- `src/test/java/com/nip/ws/WebSocketUnionTest.java`：新增（用例A + 用例B）。

## TDD 证据

### 用例A（广播可达性）红：

```
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 38.48 s <<< FAILURE! -- in com.nip.ws.WebSocketUnionTest
[ERROR] com.nip.ws.WebSocketUnionTest.firstClientStillReceivesBroadcastAfterSecondJoins -- Time elapsed: 5.459 s <<< FAILURE!
org.opentest4j.AssertionFailedError: u2 加入后 u1 必须收到广播（缺陷下所有发送都走最后连接者的 session，u1 收不到） ==> expected: not <null>
```

（红运行日志同时暴露缺陷副证：两个连接退出时打印的都是同一个 sid `5bf92fe3-...`——实例字段被最后连接者覆写。）

### 修复后全绿（用例A + 用例B）：

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 36.27 s -- in com.nip.ws.WebSocketUnionTest
[INFO] BUILD SUCCESS
```

命令：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=WebSocketUnionTest`

## 用例B 协议说明（源码核实）

- ADD_ROOM(12)：data=RoomModel JSON 对象；建房者收 ADD_ROOM_SUCCESS(120)，data 内含 Snowflake 房间 id。
- JOIN_ROOM(13)：data=房间id。服务端 onMessage 会把 data 经 Gson 再序列化——字符串会带引号导致 `onlineRooms` 键查不到，测试按数字字面量发送（Snowflake id 为 long，Gson LazilyParsedNumber 原样回写）。这是入站管道既有怪癖，不在本任务修复范围。
- ROOM_MESSAGE(20)：sendUser=用户id（本次修复的 setSendUser 管道生效点，服务端要拿它查 UserEntity），receiveUser=房间id。
- 断言：u2 收到 code 20 且 sendUser=u1、data 含 "hello"；房间外 u3 在 2s 窗口内收不到 code 20。全部达成，无降级。

## 静态检查（this.sUser / this.session 清零）

```
$ grep -n "this.sUser\|this.session" src/main/java/com/nip/ws/WebSocketUnionService.java; echo "exit=$?"
exit=1
```

零命中（grep 退出码 1 = 无匹配）。

## Concerns

- onMessage 入站 data 的"字符串二次序列化加引号"怪癖仍在（见上），真实前端若以字符串发 JOIN_ROOM data 会入房失败——属既有行为，未在本任务范围内改动。
- 出站发送统一为 asyncRemote（原代码 basic/async 混用，basicRemote 并发写同一 session 会抛 IllegalStateException）。

## 修复轮 1（评审 Important）：resolveClient 会话身份校验防重连驱逐

**结论：已修复，用例C 红→绿，3 用例全绿。**

- 缺陷：resolveClient 仅按 sid 查共享 map。同 sid 重连后旧 socket 真正关闭时，onClose(旧session) 命中新连接的 Client → userExit 把存活的新连接从 map/房间清掉并广播 USER_EXIT，新连接沦为僵尸。onError 同风险。
- 修复：resolveClient 取出后校验 `client != null && client.session() == session` 才返回，否则 null——旧 session 的 onClose/onError 对已替换条目 no-op。
- 新用例C `reconnectWithSameSidDoesNotEvictNewConnection`：同 sid 先后两连接，旧连接关闭后 watcher 在 4s 窗内轮询 GET_UNION_INFO 的 USER_LIST 断言 id1 恒在，最后 u2 加入断言新连接仍收到广播。
- 测试时序说明：服务端 onOpen 在 executor 上异步完成、connectToServer 返回不代表注册完成（曾致两轮假信号：①驱逐发生在 sleep 窗之后假绿；②查询在注册前到达被丢弃红错断言）。最终用 `awaitRegistered`（反复发 GET_UNION_INFO 直到收到 USER_LIST）探测注册完成后再关旧连接，红落在目标断言上。

### 红（修复前，驱逐被观测到）：

```
org.opentest4j.AssertionFailedError: 旧连接关闭不得驱逐同 sid 的新连接：id1 必须仍在在线用户列表 ==> expected: <true> but was: <false>
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
```

### 绿（修复后，全类）：

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.nip.ws.WebSocketUnionTest
[INFO] BUILD SUCCESS
```
