## commits
fcfc902 fix(p0-9): 断线身份按连接解析+router房间键泄漏

## stat
 .../com/nip/ws/WebSocketSimulationService.java     |  86 +++++++------
 .../java/com/nip/ws/WebSocketSimulationTest.java   | 140 +++++++++++++++++++++
 2 files changed, 186 insertions(+), 40 deletions(-)

## diff
diff --git a/src/main/java/com/nip/ws/WebSocketSimulationService.java b/src/main/java/com/nip/ws/WebSocketSimulationService.java
index 7188892..5889c03 100644
--- a/src/main/java/com/nip/ws/WebSocketSimulationService.java
+++ b/src/main/java/com/nip/ws/WebSocketSimulationService.java
@@ -45,20 +45,21 @@ import static com.nip.common.constants.SimulationRoomTypeEnum.*;
 @Data
 @Slf4j
 @RegisterForReflection
 public class WebSocketSimulationService {
   @Inject
   SimulationRouterRoomDao roomDao;
   @Inject
   UserDao userDao;
   @Inject
   SimulationRouterRoomUserDao roomUserDao;
+  // 仅 per-connection holder(persistData) 使用；共享 bean 上恒为 null，禁止读取
   private Session session;
   private SimulationUserModel userModel;
 
   /**
    * @param session 会话
    * @param id      用户id
    */
   @OnOpen
   public void onOpen(Session session, @PathParam(ID) String id, @PathParam(ROOM_ID) Integer roomId) throws IOException {
     SimulationRouterRoomUserSimpDto roomUserMap = roomUserDao.findByUserIdAndRoomId2Map(id, roomId);
@@ -70,53 +71,52 @@ public class WebSocketSimulationService {
       return;
     }
     if (roomUserMap == null) {
       UserEntity userEntity = userDao.findById(id);
       roomUserMap = new SimulationRouterRoomUserSimpDto();
       roomUserMap.setId(userEntity.getId());
       roomUserMap.setName(userEntity.getUserAccount());
       roomUserMap.setUserImg(userEntity.getUserImg());
       roomUserMap.setChannel(-1);
     }
-    this.userModel = PojoUtils.convertOne(roomUserMap, SimulationUserModel.class);
+    SimulationUserModel userModel = PojoUtils.convertOne(roomUserMap, SimulationUserModel.class);
     if (userModel != null) {
       userModel.setStatus(1);
     }
-    this.session = session;
     persistData.setSession(session);
     persistData.setUserModel(userModel);
     SimulationRouterRoomEntity roomEntity = optional.get();
     if (Objects.equals(DISTURB.getType(), roomEntity.getRoomType())) {
-      addRoomDisturd(this, roomId, persistData);
+      addRoomDisturd(roomId, persistData);
     } else if (Objects.equals(REPORT.getType(), roomEntity.getRoomType())) {
-      addRoomReport(this, roomId, persistData);
+      addRoomReport(roomId, persistData);
     } else if (Objects.equals(RECEPT.getType(), roomEntity.getRoomType())) {
-      addRoomReport(this, roomId, persistData);
+      addRoomReport(roomId, persistData);
     } else if (Objects.equals(ROUTER.getType(), roomEntity.getRoomType())) {
-      addRoomRouter(this, roomId, persistData);
+      addRoomRouter(roomId, persistData);
     }
   }
 
-  public void addRoomDisturd(WebSocketSimulationService ws, Integer roomId, WebSocketSimulationService persistData) {
+  public void addRoomDisturd(Integer roomId, WebSocketSimulationService persistData) {
     List<WebSocketSimulationService> simulations = Optional.ofNullable(SimulationGlobal.disturbRoom.get(roomId))
         .orElseGet(ArrayList::new);
     //踢出连接
-    kickOutOld(simulations, ws.getUserModel().getId());
-    String id = ws.getUserModel().getId();
+    kickOutOld(simulations, persistData.getUserModel().getId());
+    String id = persistData.getUserModel().getId();
     //发给前端message
     SimulationDisturdWebscoketVO webscoketVO = new SimulationDisturdWebscoketVO();
     webscoketVO.setTopic(ONLINE);
     SimulationDisturdWebscoketBody body = new SimulationDisturdWebscoketBody();
-    body.setId(ws.getUserModel().getId());
-    body.setUserName(ws.getUserModel().getName());
-    body.setUserImg(ws.getUserModel().getUserImg());
-    body.setChannel(ws.getUserModel().getChannel());
+    body.setId(persistData.getUserModel().getId());
+    body.setUserName(persistData.getUserModel().getName());
+    body.setUserImg(persistData.getUserModel().getUserImg());
+    body.setChannel(persistData.getUserModel().getChannel());
     webscoketVO.setBody(body);
     Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
     if (optional.isPresent()) {
       SimulationRouterRoomEntity roomEntity = optional.get();
       if (!Objects.equals(roomEntity.getCreateUserId(), id)) {
         //学员通道选择不为null，给教员发消息该学员上线
         for (WebSocketSimulationService webSocketSimulationService : simulations) {
           if (webSocketSimulationService.getUserModel().getChannel().compareTo(-1) == 0) {
             WebSocketSimulationService.sendMessage(webSocketSimulationService.getSession(), JSONUtils.toJson(webscoketVO), "", "");
             break;
@@ -129,50 +129,50 @@ public class WebSocketSimulationService {
             WebSocketSimulationService.sendMessage(webSocketSimulationService.getSession(), JSONUtils.toJson(webscoketVO), "", "");
 
           }
         }
       }
       simulations.add(persistData);
       SimulationGlobal.disturbRoom.put(roomId, simulations);
     }
   }
 
-  public void addRoomReport(WebSocketSimulationService ws, Integer roomId, WebSocketSimulationService persistData) {
+  public void addRoomReport(Integer roomId, WebSocketSimulationService persistData) {
     //拿到房间信息
     List<WebSocketSimulationService> simulations = Optional.ofNullable(SimulationGlobal.reportRoom.get(roomId))
         .orElseGet(ArrayList::new);
-    kickOutOld(simulations, ws.getUserModel().getId());
-    if (ws.getUserModel().getChannel().compareTo(1) == 0) {
+    kickOutOld(simulations, persistData.getUserModel().getId());
+    if (persistData.getUserModel().getChannel().compareTo(1) == 0) {
       simulations.stream()
           .filter(item -> item.getUserModel().getChannel() == 0)
           .findFirst()
           .ifPresent(wss -> {
             Map<String, String> data = new HashMap<>();
             data.put(TYPE, "1");
-            data.put(ID, ws.getUserModel().getId());
+            data.put(ID, persistData.getUserModel().getId());
             //学员上线给教员发送信息
-            WebSocketSimulationService.sendMessage(wss.getSession(), JSONObject.toJSONString(data), ws.getUserModel().getName(), "");
+            WebSocketSimulationService.sendMessage(wss.getSession(), JSONObject.toJSONString(data), persistData.getUserModel().getName(), "");
           });
     }
-    ws.getUserModel().setStatus(1);
+    persistData.getUserModel().setStatus(1);
     simulations.add(persistData);
     SimulationGlobal.reportRoom.put(roomId, simulations);
   }
 
-  public void addRoomRouter(WebSocketSimulationService ws, Integer roomId, WebSocketSimulationService persistData) {
+  public void addRoomRouter(Integer roomId, WebSocketSimulationService persistData) {
     List<WebSocketSimulationService> simulations = Optional.ofNullable(SimulationGlobal.routerRoom.get(roomId))
         .orElseGet(ArrayList::new);
-    kickOutOld(simulations, ws.getUserModel().getId());
+    kickOutOld(simulations, persistData.getUserModel().getId());
     //发送上线成功的消息
     Map<String, Object> msg = new HashMap<>();
     Map<String, String> body = new HashMap<>();
-    body.put(ID, ws.getUserModel().getId());
+    body.put(ID, persistData.getUserModel().getId());
     msg.put(TOPIC, ONLINE);
     msg.put(BODY, body);
     //添加到socket中
     for (WebSocketSimulationService simulation : simulations) {
       WebSocketSimulationService.sendMessage(simulation.getSession(), JSONObject.toJSONString(msg), "", "");
     }
 
     simulations.add(persistData);
     SimulationGlobal.routerRoom.put(roomId, simulations);
   }
@@ -183,23 +183,23 @@ public class WebSocketSimulationService {
   @OnClose
   public void onClose(@PathParam(ID) String id, @PathParam(ROOM_ID) Integer roomId) {
     Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
     if (optional.isEmpty()) {
       return;
     }
     SimulationRouterRoomEntity roomEntity = optional.get();
     if (Objects.equals(DISTURB.getType(), roomEntity.getRoomType())) {
       quitRoomDisturb(roomId, id);
     } else if (Objects.equals(REPORT.getType(), roomEntity.getRoomType())) {
-      quitRoomReport(this, roomId, id);
+      quitRoomReport(roomId, id);
     } else if (Objects.equals(RECEPT.getType(), roomEntity.getRoomType())) {
-      quitRoomReport(this, roomId, id);
+      quitRoomReport(roomId, id);
     } else if (Objects.equals(ROUTER.getType(), roomEntity.getRoomType())) {
       quitRoomRouter(roomId, id);
     }
   }
 
   @Transactional
   public void quitRoomDisturb(Integer roomId, String userId) {
     List<WebSocketSimulationService> simulations = SimulationGlobal.disturbRoom.get(roomId);
     if (Objects.isNull(simulations)) {
       return;
@@ -247,62 +247,63 @@ public class WebSocketSimulationService {
       message.put(BaseConstants.BODY, body);
       WebSocketSimulationService.sendMessage(item.getSession(), JSONObject.toJSONString(message), "", "");
     });
 
     if (removeIndex != null && simulations.isEmpty()) {
       SimulationGlobal.disturbRoom.remove(roomId);
     }
   }
 
   @Transactional
-  public void quitRoomReport(WebSocketSimulationService ws, Integer roomId, String userId) {
+  public void quitRoomReport(Integer roomId, String userId) {
     List<WebSocketSimulationService> simulations = SimulationGlobal.reportRoom.get(roomId);
     if (Objects.isNull(simulations)) {
       return;
     }
-    if (ws.getUserModel().getChannel().compareTo(1) == 0) {
+    //按 userId 从房间列表解析该连接的 holder，绝不读共享单例字段（P0#9：单例字段是最后连接者身份）
+    WebSocketSimulationService holder = simulations.stream()
+        .filter(item -> item.getUserModel().getId().equals(userId))
+        .findFirst()
+        .orElse(null);
+    if (holder == null) {
+      return;
+    }
+    if (holder.getUserModel().getChannel().compareTo(1) == 0) {
       simulations.stream()
           .filter(item -> item.getUserModel().getChannel() == 0)
           .findFirst()
           .ifPresent(wss -> {
             Map<String, Object> data = new HashMap<>();
             data.put(TYPE, 0);
-            data.put(ID, ws.getUserModel().getId());
-            WebSocketSimulationService.sendMessage(wss.getSession(), JSONObject.toJSONString(data), ws.getUserModel().getName(), "");
+            data.put(ID, holder.getUserModel().getId());
+            WebSocketSimulationService.sendMessage(wss.getSession(), JSONObject.toJSONString(data), holder.getUserModel().getName(), "");
           });
     } else {
       Optional<SimulationRouterRoomEntity> roomEntityOptional = roomDao.findByIdOptional(roomId);
       roomEntityOptional.ifPresent(roomEntity -> {
         roomEntity.setPlayStatus(0);
         roomDao.save(roomEntity);
       });
       for (WebSocketSimulationService simulation : simulations) {
         if (simulation.getUserModel().getUserType().compareTo(1) == 0) {
           Map<String, Integer> jb = new HashMap<>();
           jb.put(TYPE, 2);
           WebSocketSimulationService.sendMessage(simulation.getSession(), JSONObject.toJSONString(jb), "", "");
         }
       }
     }
-    Iterator<WebSocketSimulationService> iterator = simulations.iterator();
-    while (iterator.hasNext()) {
-      WebSocketSimulationService simulation = iterator.next();
-      if (Objects.equals(simulation.getUserModel().getId(), userId)) {
-        iterator.remove(); // 安全地移除元素
-        if (simulations.isEmpty()) {
-          SimulationGlobal.reportRoom.remove(roomId);
-        }
-        break; // 找到并删除一个即可
-      }
+    simulations.remove(holder);
+    if (simulations.isEmpty()) {
+      SimulationGlobal.reportRoom.remove(roomId);
+    } else {
+      SimulationGlobal.reportRoom.put(roomId, simulations);
     }
-    System.out.println(simulations.size());
-    SimulationGlobal.reportRoom.put(roomId, simulations);
   }
 
   public void quitRoomRouter(Integer roomId, String userId) {
     List<WebSocketSimulationService> simulations = SimulationGlobal.routerRoom.get(roomId);
     if (Objects.isNull(simulations)) {
       return;
     }
     Map<String, Object> msg = new HashMap<>();
     Map<String, String> body = new HashMap<>();
     body.put(ID, userId);
@@ -310,20 +311,25 @@ public class WebSocketSimulationService {
     msg.put(BODY, body);
     WebSocketSimulationService removeObj = null;
     for (WebSocketSimulationService webSocketSimulationService : simulations) {
       if (webSocketSimulationService.getUserModel().getId().equals(userId)) {
         removeObj = webSocketSimulationService;
       } else {
         WebSocketSimulationService.sendMessage(webSocketSimulationService.getSession(), JSONObject.toJSONString(msg), "", "");
       }
     }
     simulations.remove(removeObj);
+    if (simulations.isEmpty()) {
+      SimulationGlobal.routerRoom.remove(roomId);
+    } else {
+      SimulationGlobal.routerRoom.put(roomId, simulations);
+    }
   }
 
   /**
    * 消息处理
    *
    * @param
    * @param message 消息（JSON）
    */
   @OnMessage
   public void onMessage(@PathParam(ID) String id, @PathParam(ROOM_ID) Integer roomId, String message) {
diff --git a/src/test/java/com/nip/ws/WebSocketSimulationTest.java b/src/test/java/com/nip/ws/WebSocketSimulationTest.java
new file mode 100644
index 0000000..4f1bbf7
--- /dev/null
+++ b/src/test/java/com/nip/ws/WebSocketSimulationTest.java
@@ -0,0 +1,140 @@
+package com.nip.ws;
+
+import com.nip.dao.UserDao;
+import com.nip.dao.simulation.SimulationRouterRoomDao;
+import com.nip.dao.simulation.SimulationRouterRoomUserDao;
+import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
+import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
+import com.nip.testsupport.Fixtures;
+import com.nip.testsupport.MySqlResource;
+import com.nip.ws.service.simulation.SimulationGlobal;
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
+import java.net.URI;
+import java.util.List;
+import java.util.concurrent.LinkedBlockingQueue;
+import java.util.concurrent.TimeUnit;
+
+import static com.nip.common.constants.SimulationRoomTypeEnum.REPORT;
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.assertNotNull;
+import static org.junit.jupiter.api.Assertions.assertTrue;
+
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class WebSocketSimulationTest {
+
+  @Inject
+  UserDao userDao;
+  @Inject
+  SimulationRouterRoomDao roomDao;
+  @Inject
+  SimulationRouterRoomUserDao roomUserDao;
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
+  // P0#9：学员断线不得按“最后连接者”的身份处理。
+  // 缺陷：onOpen 把身份写进共享单例字段（:80/:84），onClose 把 this 传给 quitRoomReport（:193/:195）。
+  // 先连学员、后连教员 → this.userModel 停在教员身份 → 学员断线走教员分支：
+  // 整房 playStatus 被置 0（暂停）并落库。
+  @Test
+  void studentDisconnectMustNotPauseRoomAsTeacher() throws Exception {
+    String teacherId = Fixtures.user(userDao, "t-sim-teacher").getId();
+    String studentId = Fixtures.user(userDao, "t-sim-student").getId();
+
+    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
+    room.setName("report-room");
+    room.setCreateUserId(teacherId);
+    room.setRoomType(REPORT.getType());
+    room.setStats(1);
+    room.setPlayStatus(1); // 播报中；学员断线不得把它改成 0（暂停）
+    room = roomDao.save(room);
+    Integer roomId = room.getId();
+
+    saveRoomUser(roomId, teacherId, 0, 0); // 教员：userType=0（发报），channel=0
+    saveRoomUser(roomId, studentId, 1, 1); // 学员：userType=1（收报），channel=1
+
+    WebSocketContainer c = ContainerProvider.getWebSocketContainer();
+    Probe studentP = new Probe();
+    Probe teacherP = new Probe();
+    // 连接顺序触发缺陷：先连学员、后连教员，共享字段停在教员身份。
+    // onOpen 各自 get→new list→put 存在并发覆盖（P1-3，Phase 2 修），这里串行等待注册完成再连下一个。
+    Session student = c.connectToServer(studentP, uri(studentId, roomId));
+    awaitRoomSize(roomId, 1);
+    try (Session teacher = c.connectToServer(teacherP, uri(teacherId, roomId))) {
+      awaitRoomSize(roomId, 2);
+
+      student.close(); // 学员断线
+      awaitRemoved(roomId, studentId);
+
+      SimulationRouterRoomEntity after = roomDao.findById(roomId);
+      assertEquals(1, after.getPlayStatus().intValue(),
+          "学员断线不得按教员身份暂停整房：playStatus 必须保持 1");
+      List<WebSocketSimulationService> members = SimulationGlobal.reportRoom.get(roomId);
+      assertNotNull(members, "教员仍在线，房间列表不得消失");
+      assertTrue(members.stream().anyMatch(m -> teacherId.equals(m.getUserModel().getId())),
+          "教员连接必须仍在房间列表");
+      assertTrue(teacher.isOpen(), "教员连接必须仍然打开");
+    } finally {
+      if (student.isOpen()) {
+        student.close();
+      }
+    }
+  }
+
+  private static URI uri(String userId, Integer roomId) {
+    return URI.create("ws://localhost:18081/simulation/" + userId + "/" + roomId);
+  }
+
+  private void saveRoomUser(Integer roomId, String userId, int userType, int channel) {
+    SimulationRouterRoomUserEntity e = new SimulationRouterRoomUserEntity();
+    e.setRoomId(roomId);
+    e.setUserId(userId);
+    e.setUserType(userType);
+    e.setChannel(channel);
+    e.setUserStatus(0);
+    roomUserDao.save(e);
+  }
+
+  /** 服务端 onOpen 完成注册是异步的：轮询房间列表直到到达期望人数。 */
+  private static void awaitRoomSize(Integer roomId, int size) throws InterruptedException {
+    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
+    while (System.nanoTime() < deadline) {
+      List<WebSocketSimulationService> members = SimulationGlobal.reportRoom.get(roomId);
+      if (members != null && members.size() >= size) {
+        return;
+      }
+      Thread.sleep(100);
+    }
+    throw new AssertionError("10s 内房间列表未到达 " + size + " 人");
+  }
+
+  /** 轮询直到该用户被移出房间列表（onClose 异步执行）。 */
+  private static void awaitRemoved(Integer roomId, String userId) throws InterruptedException {
+    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
+    while (System.nanoTime() < deadline) {
+      List<WebSocketSimulationService> members = SimulationGlobal.reportRoom.get(roomId);
+      if (members == null || members.stream().noneMatch(m -> userId.equals(m.getUserModel().getId()))) {
+        return;
+      }
+      Thread.sleep(100);
+    }
+    throw new AssertionError("学员断线后 10s 内未被移出房间列表");
+  }
+}
