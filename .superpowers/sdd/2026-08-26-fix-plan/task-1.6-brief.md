### Task 1.6: 联合训练消息串扰（P0#8 + 新发现 2/4，WebSocketUnionService 全文件）

**Files:**
- Modify: `src/main/java/com/nip/ws/WebSocketUnionService.java`
- Test: `src/test/java/com/nip/ws/WebSocketUnionTest.java`

**现行缺陷**：单例端点（:40）实例字段 `session`/`sUser`（:54-59）被全连接覆写；:81 `webSocketClientSet.put(sid, this)` 所有 value 是同一实例 → 发送/身份操作错投最后连接者。:110-111 连续两次 `setReceiveUser`（sendUser 被丢弃；RequestModel.setSendUser 存在:17）。userExit:180/removeRoom:304-308/roomMessage:401 对 `get()` 不判空。**该端点无"用户→用户"定向码**（switch :114-153 已核实：GET_UNION_INFO/GET_ROOM_INFO/ADD_ROOM/UPDATE_ROOM/UPDATE_ROOM_USER/REMOVE_ROOM/JOIN_ROOM/EXIT_ROOM/ROOM_MESSAGE/SEAT_INSPECT/SEAT_INSPECT_REPLY/ROOM_STATUS_CHANGE），定向只有 ROOM_MESSAGE（receiveUser 携带**房间id**，经 `onlineRooms.get(...)` 发给房间成员）。

- [ ] **Step 1: 失败集成测试**（两个用例；连接 sid 一律用 `Fixtures.user(...).getId()` 返回的 UUID——onOpen:74 按主键查用户，硬编码 "u1" 会 NPE）

```java
@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class WebSocketUnionTest {

  @ClientEndpoint
  public static class Probe {
    final LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
    @OnMessage public void on(String m) { received.add(m); }
  }

  // 用例A：广播可达性——共享 session 缺陷下先连者收不到任何广播
  @Test
  void firstClientStillReceivesBroadcastAfterSecondJoins() throws Exception {
    String id1 = Fixtures.user(userDao, "t-ws-1").getId();
    String id2 = Fixtures.user(userDao, "t-ws-2").getId();
    WebSocketContainer c = ContainerProvider.getWebSocketContainer();
    Probe p1 = new Probe(); Probe p2 = new Probe();
    try (Session s1 = c.connectToServer(p1, URI.create("ws://localhost:18081/websocketUnion/" + id1))) {
      p1.received.clear(); // 排掉自己 join 产生的消息
      try (Session s2 = c.connectToServer(p2, URI.create("ws://localhost:18081/websocketUnion/" + id2))) {
        String got = p1.received.poll(5, TimeUnit.SECONDS);
        assertNotNull(got, "u2 加入后 u1 必须收到广播（缺陷下所有发送都走最后连接者的 session，u1 收不到）");
      }
    }
  }

  // 用例B：房间消息路由——写测试前先读 addRoom/joinRoom/roomMessage 源码确认消息 JSON 结构与 UnionConstants 码值，
  // 流程：u1 发 ADD_ROOM 建房 → u2 发 JOIN_ROOM 入房 → u1 发 ROOM_MESSAGE(receiveUser=房间id)
  // 断言：u2 收到消息、房间外第三连接 u3 收不到
}
```

- [ ] **Step 2: 运行确认失败**（用例A在共享 session 缺陷下必红）。
- [ ] **Step 3: 修复**（结构性，按序执行；**身份管道是本修复的核心**）
  1. 新增 `private record Client(Session session, UserModel user) {}`；`webSocketClientSet` 类型改 `ConcurrentHashMap<String, Client>`。
  2. 删除实例字段 `session`/`sUser`（:54-59）与 :73-85 的 `CompletableFuture.runAsync(...).join()` 包装（runAsync+join 本就同步等待，直接同步执行等价）；onOpen 末尾 `session.getUserProperties().put("sid", sid)` 后 `webSocketClientSet.put(sid, new Client(session, userModel))`。
  3. **身份管道**：onMessage 开头 `String sid = (String) session.getUserProperties().get("sid"); Client me = webSocketClientSet.get(sid); if (me == null) return;`——然后把 `me`（或 `me.user()`）作为参数传入所有 handler。需要改签名接收 `(Client me)` 的方法（原 this.sUser/this.session 读取点）：`userJoin`(:161-163)、`userExit`(:173/:178/:185-190)、`addRoom`(:231-235)、`joinRoom`(:327/:333-335)、`exitRoom`(:371)、以及 getUnionInfo 等一切引用 this.session/this.sUser 的方法——逐一 grep `this.sUser`/`sUser`/`this.session` 清零。
  4. 出站发送统一静态 `send(Session s, ResponseModel m)`；所有 `webSocketClientSet.get(x)` 使用点先判空（重点：userExit:180、removeRoom:304-308、roomMessage:401——判空后丢弃并 log.warn，不再 NPE）。
  5. :110 第一行改 `msg.setSendUser(Optional.ofNullable(map.get("sendUser")).map(Object::toString).orElse(""))`。
  6. onClose/onError 增加 `Session` 参数：userProperties 取 sid → 清 map、清房间成员。
  7. 完成后静态检查：`grep -n "this.sUser\|this.session" src/main/java/com/nip/ws/WebSocketUnionService.java` 零命中。
- [ ] Step 4-5：绿 → `git commit -m "fix(p0-8): 联合训练端点连接态从单例字段改为per-connection holder"`

