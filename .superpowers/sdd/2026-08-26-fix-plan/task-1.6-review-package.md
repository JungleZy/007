## commits
16964f6 fix(p0-8): 联合训练端点连接态从单例字段改为per-connection holder

## stat
 .../sdd/2026-08-26-fix-plan/task-1.6-report.md     |  56 ++++
 .../java/com/nip/ws/WebSocketUnionService.java     | 361 ++++++++++-----------
 src/test/java/com/nip/ws/WebSocketUnionTest.java   | 127 ++++++++
 3 files changed, 362 insertions(+), 182 deletions(-)

## diff
diff --git a/.superpowers/sdd/2026-08-26-fix-plan/task-1.6-report.md b/.superpowers/sdd/2026-08-26-fix-plan/task-1.6-report.md
new file mode 100644
index 0000000..30e7b52
--- /dev/null
+++ b/.superpowers/sdd/2026-08-26-fix-plan/task-1.6-report.md
@@ -0,0 +1,56 @@
+# Task 1.6 报告：P0#8 联合训练 WebSocket 串扰修复
+
+**结论：修复完成。用例A 红→绿，用例B 绿，`this.sUser`/`this.session` 零命中。**
+
+## 改动
+
+- `src/main/java/com/nip/ws/WebSocketUnionService.java`（按 brief Step3 全部 7 步）：
+  1. 新增 `private record Client(Session session, UserModel user)`；`webSocketClientSet` 改 `ConcurrentHashMap<String, Client>`。
+  2. 删除实例字段 `session`/`sUser` 与 onOpen 的 `CompletableFuture.runAsync(...).join()` 包装（改直接同步执行）；onOpen 末尾 `session.getUserProperties().put("sid", sid)` 后放入 `new Client(session, userModel)`。
+  3. 身份管道：新增 `resolveClient(Session)`（userProperties 取 sid → `webSocketClientSet.get`），onMessage/onClose/onError 入口统一走它，`me == null` 直接返回；`userJoin/userExit/getUnionInfo/getRoomInfo/addRoom/ur/joinRoom/exitRoom` 改签名接收 `Client me`。
+  4. 出站统一静态 `send(Session, ResponseModel)`（asyncRemote + try/catch）；`sendInfo(String sid, ...)` 及 `updateRoomUser/removeRoom/roomMessage/seatInspect/seatInspectReply/roomStatusChange` 的 `get()` 全部判空，缺失时 `log.warn` 丢弃，不再 NPE；删除实例方法 `sendMessage` 与 `sendInfo(Session, ...)`（仓内无外部调用者，已 grep 确认）。
+  5. onMessage 第一行赋值修复：`msg.setSendUser(...)`（原来连续两次 `setReceiveUser`，sendUser 被丢弃）。
+  6. `onClose(Session)` / `onError(Session, Throwable)`：resolveClient 后 `userExit(me)` 清 map、清房间成员（幂等：条目已清则直接返回）。
+- `src/test/java/com/nip/ws/WebSocketUnionTest.java`：新增（用例A + 用例B）。
+
+## TDD 证据
+
+### 用例A（广播可达性）红：
+
+```
+[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 38.48 s <<< FAILURE! -- in com.nip.ws.WebSocketUnionTest
+[ERROR] com.nip.ws.WebSocketUnionTest.firstClientStillReceivesBroadcastAfterSecondJoins -- Time elapsed: 5.459 s <<< FAILURE!
+org.opentest4j.AssertionFailedError: u2 加入后 u1 必须收到广播（缺陷下所有发送都走最后连接者的 session，u1 收不到） ==> expected: not <null>
+```
+
+（红运行日志同时暴露缺陷副证：两个连接退出时打印的都是同一个 sid `5bf92fe3-...`——实例字段被最后连接者覆写。）
+
+### 修复后全绿（用例A + 用例B）：
+
+```
+[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 36.27 s -- in com.nip.ws.WebSocketUnionTest
+[INFO] BUILD SUCCESS
+```
+
+命令：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=WebSocketUnionTest`
+
+## 用例B 协议说明（源码核实）
+
+- ADD_ROOM(12)：data=RoomModel JSON 对象；建房者收 ADD_ROOM_SUCCESS(120)，data 内含 Snowflake 房间 id。
+- JOIN_ROOM(13)：data=房间id。服务端 onMessage 会把 data 经 Gson 再序列化——字符串会带引号导致 `onlineRooms` 键查不到，测试按数字字面量发送（Snowflake id 为 long，Gson LazilyParsedNumber 原样回写）。这是入站管道既有怪癖，不在本任务修复范围。
+- ROOM_MESSAGE(20)：sendUser=用户id（本次修复的 setSendUser 管道生效点，服务端要拿它查 UserEntity），receiveUser=房间id。
+- 断言：u2 收到 code 20 且 sendUser=u1、data 含 "hello"；房间外 u3 在 2s 窗口内收不到 code 20。全部达成，无降级。
+
+## 静态检查（this.sUser / this.session 清零）
+
+```
+$ grep -n "this.sUser\|this.session" src/main/java/com/nip/ws/WebSocketUnionService.java; echo "exit=$?"
+exit=1
+```
+
+零命中（grep 退出码 1 = 无匹配）。
+
+## Concerns
+
+- onMessage 入站 data 的"字符串二次序列化加引号"怪癖仍在（见上），真实前端若以字符串发 JOIN_ROOM data 会入房失败——属既有行为，未在本任务范围内改动。
+- 出站发送统一为 asyncRemote（原代码 basic/async 混用，basicRemote 并发写同一 session 会抛 IllegalStateException）。
diff --git a/src/main/java/com/nip/ws/WebSocketUnionService.java b/src/main/java/com/nip/ws/WebSocketUnionService.java
index 22f6ebb..01eb29d 100644
--- a/src/main/java/com/nip/ws/WebSocketUnionService.java
+++ b/src/main/java/com/nip/ws/WebSocketUnionService.java
@@ -15,516 +15,513 @@ import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.inject.Inject;
 import jakarta.websocket.*;
 import jakarta.websocket.server.PathParam;
 import jakarta.websocket.server.ServerEndpoint;
 import lombok.extern.slf4j.Slf4j;
 import org.apache.commons.lang3.StringUtils;
 
 import java.io.IOException;
 import java.math.BigDecimal;
 import java.util.*;
-import java.util.concurrent.CompletableFuture;
 import java.util.concurrent.ConcurrentHashMap;
 import java.util.concurrent.atomic.AtomicReference;
 
 import static com.nip.common.constants.BaseConstants.TYPE;
 import static com.nip.common.constants.BaseConstants.USER_ID;
 
 /**
  * 联合训练
  *
  * @author < a href=" ">ZhangYang</ a>
  * @version v1.0.01
  * @date 2022-05-31 18:03:11
  */
 @ServerEndpoint(value = "/websocketUnion/{sid}")
 @ApplicationScoped
 @Slf4j
 public class WebSocketUnionService {
 
+  private static final String SID = "sid";
+
   @Inject
   private UserDao userDao;
 
-  private static final ConcurrentHashMap<String, WebSocketUnionService> webSocketClientSet = new ConcurrentHashMap<>();
-  private static final ConcurrentHashMap<String, RoomModel> onlineRooms = new ConcurrentHashMap<>();
-  private static final ConcurrentHashMap<String, UserModel> onlineUsers = new ConcurrentHashMap<>();
-
   /**
-   * 与某个客户端的连接会话，需要通过它来给客户端发送数据
+   * 单连接持有者：端点是单例，连接态必须挂在每个连接自己的 holder 上
    */
-  private Session session;
+  private record Client(Session session, UserModel user) {}
 
-  /**
-   * 接收sid
-   */
-  private  UserModel sUser;
+  private static final ConcurrentHashMap<String, Client> webSocketClientSet = new ConcurrentHashMap<>();
+  private static final ConcurrentHashMap<String, RoomModel> onlineRooms = new ConcurrentHashMap<>();
+  private static final ConcurrentHashMap<String, UserModel> onlineUsers = new ConcurrentHashMap<>();
 
   /**
    * 连接建立成功调用的方法
    */
   @OnOpen
   public void onOpen(Session session, @PathParam("sid") String sid) throws IOException {
-    if (webSocketClientSet.get(sid) != null) {
-      webSocketClientSet.get(sid).sendMessage(
+    Client existing = webSocketClientSet.get(sid);
+    if (existing != null) {
+      send(existing.session(),
         new ResponseModel(CodeConstants.CLOSE.getCode(), CodeConstants.CLOSE.getContent()));
       webSocketClientSet.remove(sid);
       onlineUsers.remove(sid);
     }
 
-    CompletableFuture.runAsync(()->{
-      UserEntity userEntity = userDao.findUserEntityById(sid);
-      UserModel userModel = new UserModel();
-      userModel.setId(sid);
-      userModel.setName(userEntity.getUserName());
-      userModel.setUserImg(userEntity.getUserImg());
-      this.session = session;
-      this.sUser = userModel;
-      webSocketClientSet.put(sid, this);
-      onlineUsers.put(sid, userModel);
-      log.info("有新客户端进入联合训练:" + sid + ",当前在线客户端数为:" + webSocketClientSet.size());
-      userJoin();
-    }).join();
-
-
+    UserEntity userEntity = userDao.findUserEntityById(sid);
+    UserModel userModel = new UserModel();
+    userModel.setId(sid);
+    userModel.setName(userEntity.getUserName());
+    userModel.setUserImg(userEntity.getUserImg());
+    session.getUserProperties().put(SID, sid);
+    Client me = new Client(session, userModel);
+    webSocketClientSet.put(sid, me);
+    onlineUsers.put(sid, userModel);
+    log.info("有新客户端进入联合训练:" + sid + ",当前在线客户端数为:" + webSocketClientSet.size());
+    userJoin(me);
   }
 
   /**
    * 连接关闭调用的方法
    */
   @OnClose
-  public void onClose() {
-    userExit();
+  public void onClose(Session session) {
+    Client me = resolveClient(session);
+    if (me == null) {
+      return;
+    }
+    userExit(me);
   }
 
   /**
    * 收到客户端消息后调用的方法
    *
    * @param message 客户端发送过来的消息
    */
   @OnMessage
   public void onMessage(String message, Session session) {
-    log.info("receive message :{}",message);
+    log.info("receive message :{}", message);
+    Client me = resolveClient(session);
+    if (me == null) {
+      log.warn("收到未注册连接的消息，忽略:{}", message);
+      return;
+    }
     Map map = JSONUtils.fromJson(message, Map.class);
     int code = new BigDecimal(map.get("code").toString()).intValue();
     RequestModel msg = new RequestModel();
     msg.setCode(code);
-    msg.setReceiveUser(Optional.ofNullable(map.get("sendUser")).map(Object::toString).orElse(""));
+    msg.setSendUser(Optional.ofNullable(map.get("sendUser")).map(Object::toString).orElse(""));
     msg.setReceiveUser(Optional.ofNullable(map.get("receiveUser")).map(Objects::toString).orElse(""));
     msg.setData(Optional.ofNullable(map.get("data")).map(JSONUtils::toJson).orElse(""));
     UnionConstants byCode = UnionConstants.getByCode(code);
     switch (byCode) {
       case GET_UNION_INFO:
-        getUnionInfo(session);
+        getUnionInfo(me);
         break;
       case GET_ROOM_INFO:
-        getRoomInfo(msg);
+        getRoomInfo(me, msg);
         break;
       case ADD_ROOM:
-        addRoom(msg);
+        addRoom(me, msg);
         break;
       case UPDATE_ROOM:
-        ur(msg);
+        ur(me, msg);
         break;
       case UPDATE_ROOM_USER:
         updateRoomUser(msg);
         break;
       case REMOVE_ROOM:
         removeRoom(msg);
         break;
       case JOIN_ROOM:
-        joinRoom(msg);
+        joinRoom(me, msg);
         break;
       case EXIT_ROOM:
-        exitRoom(msg);
+        exitRoom(me, msg);
         break;
       case ROOM_MESSAGE:
         roomMessage(msg);
         break;
       case SEAT_INSPECT:
         seatInspect(msg);
         break;
       case SEAT_INSPECT_REPLY:
         seatInspectReply(msg);
         break;
       case ROOM_STATUS_CHANGE:
         roomStatusChange(msg);
         break;
       default:
         break;
     }
   }
 
+  /**
+   * 当WebSocket会话中发生错误时调用的方法
+   *
+   * @param session 发生错误的连接会话
+   * @param error 发生的错误对象
+   */
+  @OnError
+  public void onError(Session session, Throwable error) {
+    log.error("联合训练连接发生错误", error);
+    Client me = resolveClient(session);
+    if (me == null) {
+      return;
+    }
+    userExit(me);
+  }
+
+  /**
+   * 身份管道：由连接会话反查该连接的 holder
+   */
+  private static Client resolveClient(Session session) {
+    Object sid = session.getUserProperties().get(SID);
+    if (sid == null) {
+      return null;
+    }
+    return webSocketClientSet.get(sid.toString());
+  }
+
   /**
    * 用户加入
    */
-  private void userJoin() {
-    webSocketClientSet.forEach((s, webSocketUnionService) -> {
-      if (!Objects.equals(s, this.sUser.getId())) {
-        webSocketUnionService.sendMessage(
-          new ResponseModel(UnionConstants.USER_JOIN.getCode(), JSONUtils.toJson(this.sUser)));
+  private void userJoin(Client me) {
+    webSocketClientSet.forEach((s, client) -> {
+      if (!Objects.equals(s, me.user().getId())) {
+        send(client.session(),
+          new ResponseModel(UnionConstants.USER_JOIN.getCode(), JSONUtils.toJson(me.user())));
       }
     });
   }
 
   /**
    * 用户退出
    */
-  private void userExit() {
+  private void userExit(Client me) {
     for (RoomModel roomModel : onlineRooms.values()) {
-      boolean b = roomModel.getUsers().removeIf(userModel -> userModel.getId().equals(this.sUser.getId()));
+      boolean b = roomModel.getUsers().removeIf(userModel -> userModel.getId().equals(me.user().getId()));
       if (b) {
         updateRoom(roomModel);
         Map jsonObject = new HashMap<>();
         jsonObject.put(TYPE, "exit");
-        jsonObject.put("user", this.sUser);
-        roomModel.getUsers().forEach(user -> {
-          webSocketClientSet.get(user.getId()).sendMessage(
-            new ResponseModel(UnionConstants.ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(jsonObject)));
-        });
+        jsonObject.put("user", me.user());
+        roomModel.getUsers().forEach(user -> sendInfo(user.getId(),
+          new ResponseModel(UnionConstants.ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(jsonObject))));
       }
     }
-    webSocketClientSet.remove(this.sUser.getId());
-    onlineUsers.remove(this.sUser.getId());
-    log.info("有客户端退出联合训练:" + this.sUser.getId() + ",当前在线客户端数为：" + onlineUsers.size());
-    webSocketClientSet.forEach((s, webSocketUnionService) -> {
-      webSocketUnionService.sendMessage(
-        new ResponseModel(UnionConstants.USER_EXIT.getCode(), JSONUtils.toJson(this.sUser)));
-    });
+    webSocketClientSet.remove(me.user().getId());
+    onlineUsers.remove(me.user().getId());
+    log.info("有客户端退出联合训练:" + me.user().getId() + ",当前在线客户端数为：" + onlineUsers.size());
+    webSocketClientSet.forEach((s, client) -> send(client.session(),
+      new ResponseModel(UnionConstants.USER_EXIT.getCode(), JSONUtils.toJson(me.user()))));
   }
 
   /**
    * 获取全部用户信息和房间信息
    */
-  private void getUnionInfo(Session session) {
-    try {
-      session.getAsyncRemote().sendText(JSONUtils.toJson(new ResponseModel(UnionConstants.USER_LIST.getCode(),
-              JSONUtils.toJson(new ArrayList<>(onlineUsers.values()))
-      )));
-      session.getAsyncRemote().sendText(JSONUtils.toJson(new ResponseModel(UnionConstants.ROOM_LIST.getCode(),
-              JSONUtils.toJson(
-                      new ArrayList<>(onlineRooms.values()))
-      )));
-    } catch (Exception e) {
-      log.error("WebSocket连接建立失败", e);
-    }
-
-    //切换成工作线程
-    CompletableFuture.runAsync(()->{
-
-    }).join();
+  private void getUnionInfo(Client me) {
+    send(me.session(), new ResponseModel(UnionConstants.USER_LIST.getCode(),
+      JSONUtils.toJson(new ArrayList<>(onlineUsers.values()))));
+    send(me.session(), new ResponseModel(UnionConstants.ROOM_LIST.getCode(),
+      JSONUtils.toJson(new ArrayList<>(onlineRooms.values()))));
   }
 
-  private void getRoomInfo(RequestModel msg) {
+  private void getRoomInfo(Client me, RequestModel msg) {
     RoomModel roomModel = onlineRooms.get(msg.getData());
-    session.getAsyncRemote().sendText(
-            JSONUtils.toJson(new ResponseModel(UnionConstants.GET_ROOM_INFO.getCode(), JSONUtils.toJson(roomModel))));
+    send(me.session(),
+      new ResponseModel(UnionConstants.GET_ROOM_INFO.getCode(), JSONUtils.toJson(roomModel)));
   }
 
   /**
    * 新建房间
    *
    * @param msg
    */
-  private void addRoom(RequestModel msg) {
+  private void addRoom(Client me, RequestModel msg) {
     try {
       RoomModel room = JSONUtils.fromJson(msg.getData(), RoomModel.class);
       room.setId(StrUtil.toString(SnowflakeIdKit.getInstance().nextId()));
-      room.setAdmin(this.sUser.getId());
+      room.setAdmin(me.user().getId());
       UserModel userModel = new UserModel();
-      userModel.setId(this.sUser.getId());
-      userModel.setName(this.sUser.getName());
-      userModel.setUserImg(this.sUser.getUserImg());
+      userModel.setId(me.user().getId());
+      userModel.setName(me.user().getName());
+      userModel.setUserImg(me.user().getUserImg());
       List<UserModel> user = new ArrayList<>();
       user.add(userModel);
       room.setUsers(user);
       onlineRooms.put(room.getId(), room);
-      sendInfo(session, new ResponseModel(UnionConstants.ADD_ROOM_SUCCESS.getCode(), JSONUtils.toJson(room)));
+      send(me.session(), new ResponseModel(UnionConstants.ADD_ROOM_SUCCESS.getCode(), JSONUtils.toJson(room)));
       updateRoom(room);
     } catch (Exception e) {
-      sendInfo(session, new ResponseModel(UnionConstants.ADD_ROOM_FAIL.getCode()));
+      send(me.session(), new ResponseModel(UnionConstants.ADD_ROOM_FAIL.getCode()));
     }
   }
 
-  private void ur(RequestModel msg) {
+  private void ur(Client me, RequestModel msg) {
     try {
       RoomModel newRoom = JSONUtils.fromJson(msg.getData(), RoomModel.class);
       RoomModel roomModel = onlineRooms.get(newRoom.getId());
       roomModel.setName(newRoom.getName());
       roomModel.setNnt(newRoom.getNnt());
       roomModel.setPassword(newRoom.getPassword());
       roomModel.setType(newRoom.getType());
       updateRoom(roomModel);
     } catch (Exception e) {
-      sendInfo(session, new ResponseModel(UnionConstants.ADD_ROOM_FAIL.getCode()));
+      send(me.session(), new ResponseModel(UnionConstants.ADD_ROOM_FAIL.getCode()));
     }
   }
 
   private void updateRoomUser(RequestModel msg) {
     String roomId = msg.getSendUser();
     String usrId = msg.getReceiveUser();
     String type = msg.getData();
     RoomModel room = onlineRooms.get(roomId);
+    if (room == null) {
+      log.warn("更新房间用户信息失败，房间不存在:{}", roomId);
+      return;
+    }
     for (UserModel user : room.getUsers()) {
       if (user.getId().equals(usrId)) {
         user.setType(Integer.parseInt(type));
         break;
       }
     }
-    room.getUsers().forEach(user -> {
-      sendInfo(user.getId(),
-               new ResponseModel(UnionConstants.UPDATE_ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(room))
-      );
-    });
-
+    room.getUsers().forEach(user -> sendInfo(user.getId(),
+      new ResponseModel(UnionConstants.UPDATE_ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(room))));
   }
 
   /**
    * 更新房间信息
    */
   private void updateRoom(RoomModel roomModel) {
-    webSocketClientSet.forEach((s, webSocketUnionService) -> {
-      webSocketUnionService.sendMessage(
-        new ResponseModel(UnionConstants.UPDATE_ROOM_INFO.getCode(), JSONUtils.toJson(roomModel)));
-    });
+    webSocketClientSet.forEach((s, client) -> send(client.session(),
+      new ResponseModel(UnionConstants.UPDATE_ROOM_INFO.getCode(), JSONUtils.toJson(roomModel))));
   }
 
   /**
    * 解散房间
    *
    * 此方法用于解散一个房间，将房间从在线房间列表中移除，并通知房间内的所有用户
-   * 它首先获取房间模型，然后从在线房间字典中移除该房间，接着通知房间内的所有用户，
-   * 最后广播更新在线房间列表
    *
    * @param msg 请求模型，包含要解散的房间的数据
    */
   private void removeRoom(RequestModel msg) {
     RoomModel roomModel = onlineRooms.get(msg.getData());
+    if (roomModel == null) {
+      log.warn("解散房间失败，房间不存在:{}", msg.getData());
+      return;
+    }
     onlineRooms.remove(msg.getData());
-    List<UserModel> users = roomModel.getUsers();
-    users.forEach(user -> {
-      WebSocketUnionService webSocketUnionService = webSocketClientSet.get(user.getId());
-
-      sendInfo(webSocketUnionService.session,
-               new ResponseModel(UnionConstants.REMOVE_ROOM_BROADCAST.getCode(), JSONUtils.toJson(roomModel))
-      );
-
-    });
+    roomModel.getUsers().forEach(user -> sendInfo(user.getId(),
+      new ResponseModel(UnionConstants.REMOVE_ROOM_BROADCAST.getCode(), JSONUtils.toJson(roomModel))));
     sendOnlineRooms();
   }
 
   /**
    * 加入房间
    *
    * 此方法用于处理用户加入房间的请求它首先检查用户是否已经在一个房间中，
    * 如果没有，则创建用户模型并将其添加到房间的用户列表中，然后通知所有房间内的其他用户
    *
    * @param msg 包含加入房间请求信息的模型
    */
-  private void joinRoom(RequestModel msg) {
+  private void joinRoom(Client me, RequestModel msg) {
     try {
       RoomModel roomModel = onlineRooms.get(msg.getData());
       AtomicReference<Integer> isIn = new AtomicReference<>(0);
       roomModel.getUsers().forEach(userModel -> {
-        if (Objects.equals(userModel.getId(), this.sUser.getId())) {
+        if (Objects.equals(userModel.getId(), me.user().getId())) {
           isIn.set(1);
         }
       });
       if (0 == isIn.get()) {
         UserModel um = new UserModel();
-        um.setId(this.sUser.getId());
-        um.setName(this.sUser.getName());
-        um.setUserImg(this.sUser.getUserImg());
+        um.setId(me.user().getId());
+        um.setName(me.user().getName());
+        um.setUserImg(me.user().getUserImg());
         roomModel.getUsers().add(um);
-        sendInfo(session, new ResponseModel(UnionConstants.JOIN_ROOM_SUCCESS.getCode(), JSONUtils.toJson(roomModel)));
+        send(me.session(),
+          new ResponseModel(UnionConstants.JOIN_ROOM_SUCCESS.getCode(), JSONUtils.toJson(roomModel)));
         updateRoom(roomModel);
         Map jsonObject = new HashMap<>();
         jsonObject.put(TYPE, "join");
         jsonObject.put("user", um);
         roomModel.getUsers().forEach(user -> {
           if (!Objects.equals(user.getId(), um.getId())) {
-            webSocketClientSet.get(user.getId()).sendMessage(
+            sendInfo(user.getId(),
               new ResponseModel(UnionConstants.ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(jsonObject)));
           }
         });
       }
     } catch (Exception e) {
       log.error("加入房间失败", e);
-      sendInfo(session,
-               new ResponseModel(UnionConstants.JOIN_ROOM_FAIL.getCode(), UnionConstants.JOIN_ROOM_FAIL.getContent())
-      );
+      send(me.session(),
+        new ResponseModel(UnionConstants.JOIN_ROOM_FAIL.getCode(), UnionConstants.JOIN_ROOM_FAIL.getContent()));
     }
   }
 
   /**
    * 退出房间
    *
    * 此方法允许当前用户退出指定的房间它通过移除房间中的用户列表来实现，
    * 并通知房间内的其他用户该用户已退出
    *
    * @param msg 包含退出房间所需信息的请求模型，包括房间ID等
    */
-  private void exitRoom(RequestModel msg) {
+  private void exitRoom(Client me, RequestModel msg) {
     RoomModel roomModel = onlineRooms.get(msg.getData());
     if (roomModel != null) {
       List<UserModel> users = roomModel.getUsers();
       AtomicReference<UserModel> um = new AtomicReference<>(new UserModel());
       users.removeIf(r -> {
-        if (r.getId().equals(this.sUser.getId())) {
+        if (r.getId().equals(me.user().getId())) {
           um.set(r);
           return true;
         }
         return false;
       });
       updateRoom(roomModel);
       Map jsonObject = new HashMap<>();
       jsonObject.put(TYPE, "exit");
       jsonObject.put("user", um.get());
-      users.forEach(user -> webSocketClientSet.get(user.getId()).sendMessage(
+      users.forEach(user -> sendInfo(user.getId(),
         new ResponseModel(UnionConstants.ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(jsonObject))));
     }
   }
 
   /**
    * 处理房间消息
    * 当接收到消息时，该方法会将消息发送给房间内的所有用户，除了发送者本身
    *
    * @param msg 消息对象，包含发送者ID，接收者ID，以及消息数据
    */
   private void roomMessage(RequestModel msg) {
     if (StringUtils.isNotEmpty(msg.getReceiveUser())) {
-      Map<String,String> dataMap = new HashMap<>();
-      dataMap.put("data",msg.getData());
+      RoomModel roomModel = onlineRooms.get(msg.getReceiveUser());
+      if (roomModel == null) {
+        log.warn("房间消息投递失败，房间不存在:{}", msg.getReceiveUser());
+        return;
+      }
+      Map<String, String> dataMap = new HashMap<>();
+      dataMap.put("data", msg.getData());
       UserEntity userEntity = userDao.findUserEntityById(msg.getSendUser());
       String userName = userEntity.getUserName();
-      dataMap.put("userName",userName);
-      dataMap.put("userImg",userEntity.getUserImg());
+      dataMap.put("userName", userName);
+      dataMap.put("userImg", userEntity.getUserImg());
       String data = JSONUtils.toJson(dataMap);
-      onlineRooms.get(msg.getReceiveUser()).getUsers().forEach(user -> {
+      roomModel.getUsers().forEach(user -> {
         if (!user.getId().equals(msg.getSendUser())) {
-          sendInfo(user.getId(), new ResponseModel(UnionConstants.ROOM_MESSAGE.getCode(),msg.getSendUser(),msg.getReceiveUser(),data));
-
+          sendInfo(user.getId(),
+            new ResponseModel(UnionConstants.ROOM_MESSAGE.getCode(), msg.getSendUser(), msg.getReceiveUser(), data));
         }
       });
     }
   }
 
   /**
    * 发起席位状态检测
    *
    * 本函数主要用于更新房间内用户的席位状态根据接收到的消息数据更新房间状态，
    * 并重置所有用户的状态，最后通过WebSocket通知所有用户更新后的房间信息
    *
    * @param msg 包含更新信息的请求模型，包括发送用户和数据
    */
   private void seatInspect(RequestModel msg) {
     RoomModel roomModel = onlineRooms.get(msg.getSendUser());
+    if (roomModel == null) {
+      log.warn("席位状态检测失败，房间不存在:{}", msg.getSendUser());
+      return;
+    }
     roomModel.setStatus(Integer.parseInt(msg.getData()));
     roomModel.getUsers().forEach(userModel -> userModel.setStatus(0));
     roomModel.getUsers().forEach(userModel -> {
-      webSocketClientSet.get(userModel.getId()).sendMessage(
+      sendInfo(userModel.getId(),
         new ResponseModel(UnionConstants.UPDATE_ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(roomModel)));
-      webSocketClientSet.get(userModel.getId())
-                        .sendMessage(new ResponseModel(UnionConstants.SEAT_INSPECT_ACCEPT.getCode(), msg.getData()));
+      sendInfo(userModel.getId(),
+        new ResponseModel(UnionConstants.SEAT_INSPECT_ACCEPT.getCode(), msg.getData()));
     });
   }
 
   /**
    * 席位状态回执
    * SEAT_INSPECT_REPLY
    * 该方法用于处理席位状态的回执消息，根据消息更新用户状态，并广播给房间内所有用户
    *
    * @param msg 请求模型，包含发送用户、接收用户和状态数据
    */
   private void seatInspectReply(RequestModel msg) {
     RoomModel roomModel = onlineRooms.get(msg.getReceiveUser());
+    if (roomModel == null) {
+      log.warn("席位状态回执失败，房间不存在:{}", msg.getReceiveUser());
+      return;
+    }
     String sendUser = msg.getSendUser();
     roomModel.getUsers().forEach(userModel -> {
       if (userModel.getId().equals(sendUser)) {
         userModel.setStatus(Integer.parseInt(msg.getData()));
       }
-      Map<String,String> jsonObject = new HashMap<>();
+      Map<String, String> jsonObject = new HashMap<>();
       jsonObject.put(USER_ID, sendUser);
       jsonObject.put("status", msg.getData());
-      webSocketClientSet.get(userModel.getId()).sendMessage(
+      sendInfo(userModel.getId(),
         new ResponseModel(UnionConstants.SEAT_INSPECT_BROADCAST.getCode(), JSONUtils.toJson(jsonObject)));
     });
   }
 
   /**
    * 改变房间状态
    * 当收到改变房间状态的请求时，根据请求信息更新房间的状态
    *
    * @param msg 请求模型，包含改变状态所需的信息，如接收用户和新状态数据
    */
   private void roomStatusChange(RequestModel msg) {
     RoomModel roomModel = onlineRooms.get(msg.getReceiveUser());
+    if (roomModel == null) {
+      log.warn("改变房间状态失败，房间不存在:{}", msg.getReceiveUser());
+      return;
+    }
     roomModel.setStatus(Integer.valueOf(msg.getData()));
     updateRoom(roomModel);
   }
 
   /**
-   * 当WebSocket会话中发生错误时调用的方法
-   * 该方法用于处理会话中的异常情况，确保错误被记录，并且可以进一步处理或通知相关人员
-   *
-   * @param session WebSocket会话对象，代表与客户端的连接在发生错误时的状态
-   * @param error 发生的错误对象，提供了错误的详细信息，可用于调试或错误追踪
+   * 向所有在线的WebSocket客户端发送当前在线的房间列表
    */
-  @OnError
-  public void onError(Session session, Throwable error) {
-    log.error("发生错误");
+  private void sendOnlineRooms() {
+    webSocketClientSet.forEach((s, client) -> send(client.session(),
+      new ResponseModel(UnionConstants.ROOM_LIST.getCode(),
+        JSONUtils.toJson(new ArrayList<>(onlineRooms.values())))));
   }
 
   /**
-   * 发送消息的方法
-   * 该方法将给定的消息对象转换为JSON字符串并发送
-   * 主要用途是封装消息的发送过程，使得发送消息更为简洁和一致
+   * 按用户ID发送消息；连接不在线时丢弃并告警，不再 NPE
    *
-   * @param message 要发送的消息对象，包含了需要发送的信息
+   * @param sid 目标用户ID
+   * @param message 要发送的消息
    */
-  public void sendMessage(ResponseModel message) {
-    sendMessage(JSONUtils.toJson(message));
+  public static void sendInfo(String sid, ResponseModel message) {
+    Client client = webSocketClientSet.get(sid);
+    if (client == null) {
+      log.warn("目标连接不在线，消息丢弃:{}", sid);
+      return;
+    }
+    send(client.session(), message);
   }
 
   /**
-   * 发送消息到WebSocket连接的另一端
-   *
-   * 此方法尝试将给定的字符串消息发送到通过当前session建立的WebSocket连接的另一端
-   * 如果在发送过程中发生任何异常，它将捕获并记录错误信息
+   * 出站发送统一入口
    *
-   * @param message 要发送的字符串消息，不应为null
+   * @param session 目标连接会话
+   * @param message 要发送的消息
    */
-  public void sendMessage(String message) {
+  private static void send(Session session, ResponseModel message) {
+    if (session == null) {
+      return;
+    }
     try {
-      this.session.getBasicRemote().sendText(message);
+      session.getAsyncRemote().sendText(JSONUtils.toJson(message));
     } catch (Exception e) {
-      log.error("WebSocketUnionService.sendMessage:{}", e.getMessage());
+      log.error("WebSocketUnionService.send:{}", e.getMessage());
     }
   }
-
-  /**
-   * 向所有在线的WebSocket客户端发送当前在线的房间列表
-   * 此方法遍历所有WebSocket客户端，并发送一个包含当前所有在线房间信息的消息
-   */
-  private void sendOnlineRooms() {
-    webSocketClientSet.forEach((s, webSocketUnionService) -> {
-      webSocketUnionService.sendMessage(new ResponseModel(UnionConstants.ROOM_LIST.getCode(),
-          JSONUtils.toJson(new ArrayList<>(onlineRooms.values()))
-      ));
-    });
-  }
-
-  /**
-   * 群发自定义消息
-   * 此方法用于向特定用户群发自定义消息它通过用户ID（sid）来识别目标用户集，
-   * 并向这些用户发送一条消息
-   *
-   * @param sid 用户ID，用于识别目标用户群
-   * @param message 要发送的消息内容，包含具体的消息信息
-   */
-  public static void sendInfo(@PathParam("sid") String sid, ResponseModel message) {
-    webSocketClientSet.get(sid).sendMessage(message);
-  }
-
-  public static void sendInfo(Session session, ResponseModel message) {
-    session.getAsyncRemote().sendText(JSONUtils.toJson(message));
-  }
 }
diff --git a/src/test/java/com/nip/ws/WebSocketUnionTest.java b/src/test/java/com/nip/ws/WebSocketUnionTest.java
new file mode 100644
index 0000000..537ef8e
--- /dev/null
+++ b/src/test/java/com/nip/ws/WebSocketUnionTest.java
@@ -0,0 +1,127 @@
+package com.nip.ws;
+
+import com.nip.common.utils.JSONUtils;
+import com.nip.dao.UserDao;
+import com.nip.testsupport.Fixtures;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import jakarta.websocket.ClientEndpoint;
+import jakarta.websocket.ContainerProvider;
+import jakarta.websocket.OnMessage;
+import jakarta.websocket.Session;
+import jakarta.websocket.WebSocketContainer;
+import org.junit.jupiter.api.Test;
+
+import java.math.BigDecimal;
+import java.net.URI;
+import java.util.Map;
+import java.util.concurrent.LinkedBlockingQueue;
+import java.util.concurrent.TimeUnit;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.assertNotNull;
+import static org.junit.jupiter.api.Assertions.assertNull;
+import static org.junit.jupiter.api.Assertions.assertTrue;
+
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class WebSocketUnionTest {
+
+  @Inject
+  UserDao userDao;
+
+  @ClientEndpoint
+  public static class Probe {
+    final LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
+
+    @OnMessage
+    public void on(String m) {
+      received.add(m);
+    }
+  }
+
+  // 用例A：广播可达性——共享 session 缺陷下先连者收不到任何广播
+  @Test
+  void firstClientStillReceivesBroadcastAfterSecondJoins() throws Exception {
+    String id1 = Fixtures.user(userDao, "t-ws-1").getId();
+    String id2 = Fixtures.user(userDao, "t-ws-2").getId();
+    WebSocketContainer c = ContainerProvider.getWebSocketContainer();
+    Probe p1 = new Probe();
+    Probe p2 = new Probe();
+    try (Session s1 = c.connectToServer(p1, URI.create("ws://localhost:18081/websocketUnion/" + id1))) {
+      p1.received.clear(); // 排掉自己 join 产生的消息
+      try (Session s2 = c.connectToServer(p2, URI.create("ws://localhost:18081/websocketUnion/" + id2))) {
+        String got = p1.received.poll(5, TimeUnit.SECONDS);
+        assertNotNull(got, "u2 加入后 u1 必须收到广播（缺陷下所有发送都走最后连接者的 session，u1 收不到）");
+      }
+    }
+  }
+
+  // 用例B：房间消息路由——u1 建房，u2 入房，u1 发 ROOM_MESSAGE(receiveUser=房间id)：
+  // u2 收到、房间外 u3 收不到。
+  // 协议依据源码：ADD_ROOM(12) data=RoomModel JSON；JOIN_ROOM(13) data=房间id（服务端 onMessage 会把
+  // data 再经 Gson 序列化，字符串会带引号导致 onlineRooms 查不到键，故以数字字面量发送 Snowflake id）；
+  // ROOM_MESSAGE(20) sendUser=用户id、receiveUser=房间id。
+  @Test
+  void roomMessageReachesRoomMemberOnly() throws Exception {
+    String id1 = Fixtures.user(userDao, "t-ws-b1").getId();
+    String id2 = Fixtures.user(userDao, "t-ws-b2").getId();
+    String id3 = Fixtures.user(userDao, "t-ws-b3").getId();
+    WebSocketContainer c = ContainerProvider.getWebSocketContainer();
+    Probe p1 = new Probe();
+    Probe p2 = new Probe();
+    Probe p3 = new Probe();
+    try (Session s1 = c.connectToServer(p1, URI.create("ws://localhost:18081/websocketUnion/" + id1));
+         Session s2 = c.connectToServer(p2, URI.create("ws://localhost:18081/websocketUnion/" + id2));
+         Session s3 = c.connectToServer(p3, URI.create("ws://localhost:18081/websocketUnion/" + id3))) {
+
+      // u1 建房
+      s1.getBasicRemote().sendText("{\"code\":12,\"data\":{\"name\":\"room-b\"}}");
+      Map added = pollForCode(p1, 120, 5); // ADD_ROOM_SUCCESS
+      assertNotNull(added, "u1 建房后必须收到 ADD_ROOM_SUCCESS(120)");
+      Map room = JSONUtils.fromJson(added.get("data").toString(), Map.class);
+      String roomId = room.get("id").toString();
+
+      // u2 入房（房间id 是 Snowflake 数字，按数字字面量发送）
+      s2.getBasicRemote().sendText("{\"code\":13,\"data\":" + roomId + "}");
+      assertNotNull(pollForCode(p2, 130, 5), "u2 入房后必须收到 JOIN_ROOM_SUCCESS(130)");
+
+      // 排掉建房/入房阶段的广播噪音
+      Thread.sleep(500);
+      p2.received.clear();
+      p3.received.clear();
+
+      // u1 发房间消息
+      s1.getBasicRemote().sendText("{\"code\":20,\"sendUser\":\"" + id1
+          + "\",\"receiveUser\":\"" + roomId + "\",\"data\":\"hello\"}");
+
+      Map got = pollForCode(p2, 20, 5); // ROOM_MESSAGE
+      assertNotNull(got, "房间成员 u2 必须收到 ROOM_MESSAGE(20)");
+      assertEquals(id1, got.get("sendUser"), "消息发送者必须是 u1");
+      assertTrue(got.get("data").toString().contains("hello"), "消息内容必须可达");
+
+      assertNull(pollForCode(p3, 20, 2), "房间外的 u3 不得收到 ROOM_MESSAGE");
+    }
+  }
+
+  /** 轮询直到收到指定业务码的消息；超时返回 null。 */
+  private static Map pollForCode(Probe probe, int code, long timeoutSec) throws InterruptedException {
+    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec);
+    while (true) {
+      long remain = deadline - System.nanoTime();
+      if (remain <= 0) {
+        return null;
+      }
+      String m = probe.received.poll(remain, TimeUnit.NANOSECONDS);
+      if (m == null) {
+        return null;
+      }
+      Map map = JSONUtils.fromJson(m, Map.class);
+      if (new BigDecimal(map.get("code").toString()).intValue() == code) {
+        return map;
+      }
+    }
+  }
+}
