## commits
801b193 fix(p0-8): resolveClient 会话身份校验防重连驱逐

## diff
diff --git a/.superpowers/sdd/2026-08-26-fix-plan/task-1.6-report.md b/.superpowers/sdd/2026-08-26-fix-plan/task-1.6-report.md
index 30e7b52..de96106 100644
--- a/.superpowers/sdd/2026-08-26-fix-plan/task-1.6-report.md
+++ b/.superpowers/sdd/2026-08-26-fix-plan/task-1.6-report.md
@@ -47,10 +47,33 @@ org.opentest4j.AssertionFailedError: u2 加入后 u1 必须收到广播（缺陷
 $ grep -n "this.sUser\|this.session" src/main/java/com/nip/ws/WebSocketUnionService.java; echo "exit=$?"
 exit=1
 ```
 
 零命中（grep 退出码 1 = 无匹配）。
 
 ## Concerns
 
 - onMessage 入站 data 的"字符串二次序列化加引号"怪癖仍在（见上），真实前端若以字符串发 JOIN_ROOM data 会入房失败——属既有行为，未在本任务范围内改动。
 - 出站发送统一为 asyncRemote（原代码 basic/async 混用，basicRemote 并发写同一 session 会抛 IllegalStateException）。
+
+## 修复轮 1（评审 Important）：resolveClient 会话身份校验防重连驱逐
+
+**结论：已修复，用例C 红→绿，3 用例全绿。**
+
+- 缺陷：resolveClient 仅按 sid 查共享 map。同 sid 重连后旧 socket 真正关闭时，onClose(旧session) 命中新连接的 Client → userExit 把存活的新连接从 map/房间清掉并广播 USER_EXIT，新连接沦为僵尸。onError 同风险。
+- 修复：resolveClient 取出后校验 `client != null && client.session() == session` 才返回，否则 null——旧 session 的 onClose/onError 对已替换条目 no-op。
+- 新用例C `reconnectWithSameSidDoesNotEvictNewConnection`：同 sid 先后两连接，旧连接关闭后 watcher 在 4s 窗内轮询 GET_UNION_INFO 的 USER_LIST 断言 id1 恒在，最后 u2 加入断言新连接仍收到广播。
+- 测试时序说明：服务端 onOpen 在 executor 上异步完成、connectToServer 返回不代表注册完成（曾致两轮假信号：①驱逐发生在 sleep 窗之后假绿；②查询在注册前到达被丢弃红错断言）。最终用 `awaitRegistered`（反复发 GET_UNION_INFO 直到收到 USER_LIST）探测注册完成后再关旧连接，红落在目标断言上。
+
+### 红（修复前，驱逐被观测到）：
+
+```
+org.opentest4j.AssertionFailedError: 旧连接关闭不得驱逐同 sid 的新连接：id1 必须仍在在线用户列表 ==> expected: <true> but was: <false>
+[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
+```
+
+### 绿（修复后，全类）：
+
+```
+[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.nip.ws.WebSocketUnionTest
+[INFO] BUILD SUCCESS
+```
diff --git a/src/main/java/com/nip/ws/WebSocketUnionService.java b/src/main/java/com/nip/ws/WebSocketUnionService.java
index 01eb29d..fd3b0c4 100644
--- a/src/main/java/com/nip/ws/WebSocketUnionService.java
+++ b/src/main/java/com/nip/ws/WebSocketUnionService.java
@@ -165,28 +165,34 @@ public class WebSocketUnionService {
   public void onError(Session session, Throwable error) {
     log.error("联合训练连接发生错误", error);
     Client me = resolveClient(session);
     if (me == null) {
       return;
     }
     userExit(me);
   }
 
   /**
-   * 身份管道：由连接会话反查该连接的 holder
+   * 身份管道：由连接会话反查该连接的 holder；
+   * 校验 holder 持有的就是本会话——同 sid 重连后，旧 session 的 onClose/onError
+   * 不得命中新连接的条目（否则会把存活的新连接驱逐）
    */
   private static Client resolveClient(Session session) {
     Object sid = session.getUserProperties().get(SID);
     if (sid == null) {
       return null;
     }
-    return webSocketClientSet.get(sid.toString());
+    Client client = webSocketClientSet.get(sid.toString());
+    if (client == null || client.session() != session) {
+      return null;
+    }
+    return client;
   }
 
   /**
    * 用户加入
    */
   private void userJoin(Client me) {
     webSocketClientSet.forEach((s, client) -> {
       if (!Objects.equals(s, me.user().getId())) {
         send(client.session(),
           new ResponseModel(UnionConstants.USER_JOIN.getCode(), JSONUtils.toJson(me.user())));
diff --git a/src/test/java/com/nip/ws/WebSocketUnionTest.java b/src/test/java/com/nip/ws/WebSocketUnionTest.java
index 537ef8e..202a09d 100644
--- a/src/test/java/com/nip/ws/WebSocketUnionTest.java
+++ b/src/test/java/com/nip/ws/WebSocketUnionTest.java
@@ -52,20 +52,81 @@ class WebSocketUnionTest {
     Probe p2 = new Probe();
     try (Session s1 = c.connectToServer(p1, URI.create("ws://localhost:18081/websocketUnion/" + id1))) {
       p1.received.clear(); // 排掉自己 join 产生的消息
       try (Session s2 = c.connectToServer(p2, URI.create("ws://localhost:18081/websocketUnion/" + id2))) {
         String got = p1.received.poll(5, TimeUnit.SECONDS);
         assertNotNull(got, "u2 加入后 u1 必须收到广播（缺陷下所有发送都走最后连接者的 session，u1 收不到）");
       }
     }
   }
 
+  // 用例C：同 sid 重连——旧连接真正关闭时不得驱逐新连接。
+  // 缺陷：resolveClient 仅按 sid 查 map，旧 session 的 onClose 会命中重连后的新 Client，
+  // userExit 把存活的新连接从 map 清掉并广播 USER_EXIT，新连接沦为僵尸。
+  // 服务端 onOpen 在 executor 上异步完成，须先用 GET_UNION_INFO 探测各连接注册完成，
+  // 再关旧连接；随后 watcher 在时间窗内轮询 USER_LIST(10)，断言 id1 恒在。
+  @Test
+  void reconnectWithSameSidDoesNotEvictNewConnection() throws Exception {
+    String id1 = Fixtures.user(userDao, "t-ws-c1").getId();
+    String id2 = Fixtures.user(userDao, "t-ws-c2").getId();
+    String idW = Fixtures.user(userDao, "t-ws-cw").getId();
+    WebSocketContainer c = ContainerProvider.getWebSocketContainer();
+    Probe oldP = new Probe();
+    Probe newP = new Probe();
+    Probe p2 = new Probe();
+    Probe watcherP = new Probe();
+    Session oldS = c.connectToServer(oldP, URI.create("ws://localhost:18081/websocketUnion/" + id1));
+    awaitRegistered(oldS, oldP);
+    try (Session newS = c.connectToServer(newP, URI.create("ws://localhost:18081/websocketUnion/" + id1));
+         Session watcher = c.connectToServer(watcherP, URI.create("ws://localhost:18081/websocketUnion/" + idW))) {
+      awaitRegistered(newS, newP);
+      awaitRegistered(watcher, watcherP);
+      oldS.close(); // 旧连接真正关闭，触发 onClose(旧session)
+      // 时间窗内轮询在线用户列表：id1（新连接）必须始终在线
+      boolean sawList = false;
+      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
+      while (System.nanoTime() < deadline) {
+        watcherP.received.clear();
+        watcher.getBasicRemote().sendText("{\"code\":0}");
+        Map userList = pollForCode(watcherP, 10, 2); // USER_LIST
+        if (userList != null) {
+          sawList = true;
+          assertTrue(userList.get("data").toString().contains(id1),
+              "旧连接关闭不得驱逐同 sid 的新连接：id1 必须仍在在线用户列表");
+        }
+        Thread.sleep(300);
+      }
+      assertTrue(sawList, "监控窗内 watcher 必须至少收到一次 USER_LIST(10)");
+      newP.received.clear();
+      try (Session s2 = c.connectToServer(p2, URI.create("ws://localhost:18081/websocketUnion/" + id2))) {
+        String got = newP.received.poll(5, TimeUnit.SECONDS);
+        assertNotNull(got, "旧连接关闭不得驱逐同 sid 的新连接：u2 加入时新连接必须仍收到广播");
+      }
+    } finally {
+      if (oldS.isOpen()) {
+        oldS.close();
+      }
+    }
+  }
+
+  /** 等待连接在服务端注册完成：反复发 GET_UNION_INFO 直到收到 USER_LIST（未注册时服务端丢弃消息）。 */
+  private static void awaitRegistered(Session s, Probe p) throws Exception {
+    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
+    while (System.nanoTime() < deadline) {
+      s.getBasicRemote().sendText("{\"code\":0}");
+      if (pollForCode(p, 10, 1) != null) {
+        return;
+      }
+    }
+    throw new AssertionError("连接在 10s 内未完成服务端注册");
+  }
+
   // 用例B：房间消息路由——u1 建房，u2 入房，u1 发 ROOM_MESSAGE(receiveUser=房间id)：
   // u2 收到、房间外 u3 收不到。
   // 协议依据源码：ADD_ROOM(12) data=RoomModel JSON；JOIN_ROOM(13) data=房间id（服务端 onMessage 会把
   // data 再经 Gson 序列化，字符串会带引号导致 onlineRooms 查不到键，故以数字字面量发送 Snowflake id）；
   // ROOM_MESSAGE(20) sendUser=用户id、receiveUser=房间id。
   @Test
   void roomMessageReachesRoomMemberOnly() throws Exception {
     String id1 = Fixtures.user(userDao, "t-ws-b1").getId();
     String id2 = Fixtures.user(userDao, "t-ws-b2").getId();
     String id3 = Fixtures.user(userDao, "t-ws-b3").getId();
