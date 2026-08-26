## commits
859ece7
50827c2
94ac2b6
e4a4900
273f366
474c616


---
commit 859ece797b83fd949164e99c75588ab0c73175ff
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 03:37:11 2026 +0800

    fix(ws-1): 房间表 computeIfAbsent 原子建房 + CopyOnWriteArrayList，删除尾部 put 覆盖

diff --git a/src/main/java/com/nip/ws/WebSocketSimulationService.java b/src/main/java/com/nip/ws/WebSocketSimulationService.java
index 5889c03..cf90fe8 100644
--- a/src/main/java/com/nip/ws/WebSocketSimulationService.java
+++ b/src/main/java/com/nip/ws/WebSocketSimulationService.java
@@ -30,16 +30,17 @@ import jakarta.websocket.server.ServerEndpoint;
 import lombok.Data;
 import lombok.extern.slf4j.Slf4j;
 import org.jose4j.json.internal.json_simple.JSONObject;
 
 import java.io.IOException;
 import java.sql.Timestamp;
 import java.time.LocalDateTime;
 import java.util.*;
+import java.util.concurrent.CopyOnWriteArrayList;
 
 import static com.nip.common.constants.BaseConstants.*;
 import static com.nip.common.constants.SimulationDisturdTopicEnum.*;
 import static com.nip.common.constants.SimulationRoomTypeEnum.*;
 
 @ServerEndpoint(value = "/simulation/{id}/{roomId}")
 @ApplicationScoped
 @Data
@@ -92,18 +93,18 @@ public class WebSocketSimulationService {
     } else if (Objects.equals(RECEPT.getType(), roomEntity.getRoomType())) {
       addRoomReport(roomId, persistData);
     } else if (Objects.equals(ROUTER.getType(), roomEntity.getRoomType())) {
       addRoomRouter(roomId, persistData);
     }
   }
 
   public void addRoomDisturd(Integer roomId, WebSocketSimulationService persistData) {
-    List<WebSocketSimulationService> simulations = Optional.ofNullable(SimulationGlobal.disturbRoom.get(roomId))
-        .orElseGet(ArrayList::new);
+    List<WebSocketSimulationService> simulations =
+        SimulationGlobal.disturbRoom.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
     //踢出连接
     kickOutOld(simulations, persistData.getUserModel().getId());
     String id = persistData.getUserModel().getId();
     //发给前端message
     SimulationDisturdWebscoketVO webscoketVO = new SimulationDisturdWebscoketVO();
     webscoketVO.setTopic(ONLINE);
     SimulationDisturdWebscoketBody body = new SimulationDisturdWebscoketBody();
     body.setId(persistData.getUserModel().getId());
@@ -127,59 +128,56 @@ public class WebSocketSimulationService {
         for (WebSocketSimulationService webSocketSimulationService : simulations) {
           if (webSocketSimulationService.getUserModel().getUserType().compareTo(1) == 0) {
             WebSocketSimulationService.sendMessage(webSocketSimulationService.getSession(), JSONUtils.toJson(webscoketVO), "", "");
 
           }
         }
       }
       simulations.add(persistData);
-      SimulationGlobal.disturbRoom.put(roomId, simulations);
     }
   }
 
   public void addRoomReport(Integer roomId, WebSocketSimulationService persistData) {
     //拿到房间信息
-    List<WebSocketSimulationService> simulations = Optional.ofNullable(SimulationGlobal.reportRoom.get(roomId))
-        .orElseGet(ArrayList::new);
+    List<WebSocketSimulationService> simulations =
+        SimulationGlobal.reportRoom.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
     kickOutOld(simulations, persistData.getUserModel().getId());
     if (persistData.getUserModel().getChannel().compareTo(1) == 0) {
       simulations.stream()
           .filter(item -> item.getUserModel().getChannel() == 0)
           .findFirst()
           .ifPresent(wss -> {
             Map<String, String> data = new HashMap<>();
             data.put(TYPE, "1");
             data.put(ID, persistData.getUserModel().getId());
             //学员上线给教员发送信息
             WebSocketSimulationService.sendMessage(wss.getSession(), JSONObject.toJSONString(data), persistData.getUserModel().getName(), "");
           });
     }
     persistData.getUserModel().setStatus(1);
     simulations.add(persistData);
-    SimulationGlobal.reportRoom.put(roomId, simulations);
   }
 
   public void addRoomRouter(Integer roomId, WebSocketSimulationService persistData) {
-    List<WebSocketSimulationService> simulations = Optional.ofNullable(SimulationGlobal.routerRoom.get(roomId))
-        .orElseGet(ArrayList::new);
+    List<WebSocketSimulationService> simulations =
+        SimulationGlobal.routerRoom.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
     kickOutOld(simulations, persistData.getUserModel().getId());
     //发送上线成功的消息
     Map<String, Object> msg = new HashMap<>();
     Map<String, String> body = new HashMap<>();
     body.put(ID, persistData.getUserModel().getId());
     msg.put(TOPIC, ONLINE);
     msg.put(BODY, body);
     //添加到socket中
     for (WebSocketSimulationService simulation : simulations) {
       WebSocketSimulationService.sendMessage(simulation.getSession(), JSONObject.toJSONString(msg), "", "");
     }
 
     simulations.add(persistData);
-    SimulationGlobal.routerRoom.put(roomId, simulations);
   }
 
   /**
    * 关闭
    */
   @OnClose
   public void onClose(@PathParam(ID) String id, @PathParam(ROOM_ID) Integer roomId) {
     Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
@@ -289,18 +287,16 @@ public class WebSocketSimulationService {
           jb.put(TYPE, 2);
           WebSocketSimulationService.sendMessage(simulation.getSession(), JSONObject.toJSONString(jb), "", "");
         }
       }
     }
     simulations.remove(holder);
     if (simulations.isEmpty()) {
       SimulationGlobal.reportRoom.remove(roomId);
-    } else {
-      SimulationGlobal.reportRoom.put(roomId, simulations);
     }
   }
 
   public void quitRoomRouter(Integer roomId, String userId) {
     List<WebSocketSimulationService> simulations = SimulationGlobal.routerRoom.get(roomId);
     if (Objects.isNull(simulations)) {
       return;
     }
@@ -315,18 +311,16 @@ public class WebSocketSimulationService {
         removeObj = webSocketSimulationService;
       } else {
         WebSocketSimulationService.sendMessage(webSocketSimulationService.getSession(), JSONObject.toJSONString(msg), "", "");
       }
     }
     simulations.remove(removeObj);
     if (simulations.isEmpty()) {
       SimulationGlobal.routerRoom.remove(roomId);
-    } else {
-      SimulationGlobal.routerRoom.put(roomId, simulations);
     }
   }
 
   /**
    * 消息处理
    *
    * @param
    * @param message 消息（JSON）
diff --git a/src/main/java/com/nip/ws/service/simulation/SimulationGlobal.java b/src/main/java/com/nip/ws/service/simulation/SimulationGlobal.java
index 258e2f0..e871cdc 100644
--- a/src/main/java/com/nip/ws/service/simulation/SimulationGlobal.java
+++ b/src/main/java/com/nip/ws/service/simulation/SimulationGlobal.java
@@ -1,16 +1,21 @@
 package com.nip.ws.service.simulation;
 
 import com.nip.ws.WebSocketSimulationService;
 
 import java.util.List;
 import java.util.concurrent.ConcurrentHashMap;
 import java.util.concurrent.ConcurrentMap;
 
+/**
+ * 全局房间表。value 一律是 {@link java.util.concurrent.CopyOnWriteArrayList}，
+ * 且只能通过 {@code computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>())} 创建——
+ * 禁止 get→new ArrayList→put 的 check-then-act 写法（并发 onOpen 会互相覆盖）。
+ */
 public class SimulationGlobal {
   public static final ConcurrentMap<Integer, List<WebSocketSimulationService>> routerRoom = new ConcurrentHashMap<>();
   /**
    * 快速干扰房间
    */
   public static final ConcurrentMap<Integer, List<WebSocketSimulationService>> disturbRoom = new ConcurrentHashMap<>();
   /**
    * 通报教学房间


---
commit 50827c2999aa7f27453a9d73a435237bc74ed01c
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 03:37:58 2026 +0800

    fix(ws-2): 三个 General 端点补 @OnError（日志+复用 onClose 清理并关连接）

diff --git a/src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java b/src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java
index 0d3dc16..f1c2f89 100644
--- a/src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java
+++ b/src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java
@@ -7,16 +7,17 @@ import com.nip.common.utils.PojoUtils;
 import com.nip.dto.general.GeneralPatTrainRoomUserDto;
 import com.nip.dto.general.GeneralPatTrainUserDto;
 import com.nip.dto.general.GeneralPatTrainUserModelDto;
 import com.nip.service.general.GeneralKeyPatService;
 import com.nip.ws.model.SocketResponseModel;
 import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.inject.Inject;
 import jakarta.websocket.OnClose;
+import jakarta.websocket.OnError;
 import jakarta.websocket.OnMessage;
 import jakarta.websocket.OnOpen;
 import jakarta.websocket.Session;
 import jakarta.websocket.server.PathParam;
 import jakarta.websocket.server.ServerEndpoint;
 import lombok.extern.slf4j.Slf4j;
 import org.eclipse.microprofile.openapi.annotations.tags.Tag;
 import org.jose4j.json.internal.json_simple.JSONObject;
@@ -148,16 +149,23 @@ public class WebSocketGeneralKeyPatService {
           sendMessage(keyPatTrainRoomUser.getGroupUser().getSession(), JSONObject.toJSONString(data), "", "");
         }
         joinUser.remove(removeModel);
       }
     }
     close(session);
   }
 
+  @OnError
+  public void onError(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId, Session session, Throwable t) {
+    log.error("ws error, session={}", session.getId(), t);
+    //复用 onClose 清理该 session 对应的房间状态并关闭连接
+    onClose(uid, trainId, session);
+  }
+
   public static void sendMessage(Session session, String message, String sendName, String receiveName) {
     try {
       if (session.isOpen()) {
         session.getBasicRemote().sendText(JSONUtils.toJson(SocketResponseModel.success(message, sendName, receiveName)));
       }
     } catch (IOException e) {
       log.error("WebSocketGeneralKeyPatService.sendMessage: 发送消息失败");
     }
diff --git a/src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java b/src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java
index 94bd0bf..b15d7ef 100644
--- a/src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java
+++ b/src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java
@@ -7,16 +7,17 @@ import com.nip.common.utils.PojoUtils;
 import com.nip.dto.general.GeneralPatTrainRoomUserDto;
 import com.nip.dto.general.GeneralPatTrainUserDto;
 import com.nip.dto.general.GeneralPatTrainUserModelDto;
 import com.nip.service.general.GeneralTelexPatService;
 import com.nip.ws.model.SocketResponseModel;
 import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.inject.Inject;
 import jakarta.websocket.OnClose;
+import jakarta.websocket.OnError;
 import jakarta.websocket.OnMessage;
 import jakarta.websocket.OnOpen;
 import jakarta.websocket.Session;
 import jakarta.websocket.server.PathParam;
 import jakarta.websocket.server.ServerEndpoint;
 import lombok.extern.slf4j.Slf4j;
 import org.eclipse.microprofile.openapi.annotations.tags.Tag;
 import org.jose4j.json.internal.json_simple.JSONObject;
@@ -140,16 +141,23 @@ public class WebSocketGeneralTelexPatService {
           sendMessage(keyPatTrainRoomUser.getGroupUser().getSession(), JSONObject.toJSONString(data), "", "");
         }
         joinUser.remove(removeModel);
       }
     }
     close(session);
   }
 
+  @OnError
+  public void onError(@PathParam("uid") String uid, @PathParam(TRAIN_ID) String trainId, Session session, Throwable t) {
+    log.error("ws error, session={}", session.getId(), t);
+    //复用 onClose 清理该 session 对应的房间状态并关闭连接
+    onClose(uid, trainId, session);
+  }
+
   public static void sendMessage(Session session, String message, String sendName, String receiveName) {
     try {
       if (session.isOpen()) {
         session.getBasicRemote().sendText(JSONUtils.toJson(SocketResponseModel.success(message, sendName, receiveName)));
       }
     } catch (IOException e) {
       log.error("WebSocketGeneralKeyPatService.sendMessage: 发送消息失败");
     }
diff --git a/src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java b/src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java
index cb5232e..76ffa36 100644
--- a/src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java
+++ b/src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java
@@ -3,16 +3,17 @@ package com.nip.ws;
 import com.google.gson.reflect.TypeToken;
 import com.nip.common.constants.BaseConstants;
 import com.nip.common.utils.JSONUtils;
 import com.nip.ws.model.GeneralTickerPatTrainRoomUserModel;
 import com.nip.ws.model.GeneralTickerPatTrainUserModel;
 import com.nip.ws.model.SocketResponseModel;
 import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.websocket.OnClose;
+import jakarta.websocket.OnError;
 import jakarta.websocket.OnMessage;
 import jakarta.websocket.OnOpen;
 import jakarta.websocket.Session;
 import jakarta.websocket.server.PathParam;
 import jakarta.websocket.server.ServerEndpoint;
 import lombok.extern.slf4j.Slf4j;
 import org.eclipse.microprofile.openapi.annotations.tags.Tag;
 
@@ -167,16 +168,28 @@ public class WebSocketGeneralTickerPatService {
     }
     if (roomUser.getJoinUser().isEmpty() && roomUser.getGroupUser() == null) {
 //      log.info("房间：{}，所有人已离开", trainId);
       PAT_ROOM.remove(trainId);
     }
 //    log.info("房间信息：{}", PAT_ROOM);
   }
 
+  @OnError
+  public void onError(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId, Session session, Throwable t) {
+    log.error("ws error, session={}", session.getId(), t);
+    //复用 onClose 清理该 session 对应的房间状态，并关闭连接
+    onClose(uid, trainId);
+    try {
+      session.close();
+    } catch (IOException e) {
+      log.error("关闭socket出错:{}", e.getMessage());
+    }
+  }
+
   public static boolean sendMessage(Session session, String message, String sendName, String receiveName) {
     try {
       if (session.isOpen()) {
         session.getBasicRemote().sendText(JSONUtils.toJson(SocketResponseModel.success(message, sendName, receiveName)));
         return true;
       } else {
         return false;
       }


---
commit 94ac2b64fe8092e303457f4ae8830744bdd2a76b
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 03:38:36 2026 +0800

    fix(ws-3): messageHandleReport/Router 房间列表判空短路，REST 删房后不再 NPE

diff --git a/src/main/java/com/nip/ws/WebSocketSimulationService.java b/src/main/java/com/nip/ws/WebSocketSimulationService.java
index cf90fe8..014db0b 100644
--- a/src/main/java/com/nip/ws/WebSocketSimulationService.java
+++ b/src/main/java/com/nip/ws/WebSocketSimulationService.java
@@ -456,16 +456,20 @@ public class WebSocketSimulationService {
     }));
     WebSocketSimulationService.sendMessage(simulation.getSession(), JSONObject.toJSONString(msg), "", "");
   }
 
   @Transactional
   public void messageHandleReport(String message, Integer roomId, String userId) {
     //通过人员id获取消息管道号
     List<WebSocketSimulationService> socketSimulations = SimulationGlobal.reportRoom.get(roomId);
+    //REST 删房只清 map 不关 session：客户端续发消息时房间列表可能已不存在，判空短路
+    if (socketSimulations == null) {
+      return;
+    }
     Map<String, String> mesg = JSONUtils.fromJson(message, new TypeToken<>() {
     });
     String type = mesg.get(TYPE);
     // 教员开始训练
     if (TOPIC_TRAIN_START.getType().equals(type)) {
       roomDao.updateStatsToGoing(roomId);
     }
     // 暂停训练
@@ -524,16 +528,20 @@ public class WebSocketSimulationService {
       }
     }
   }
 
   @Transactional
   public void messageHandleRouter(String message, Integer roomId, String userId) {
     //通过人员id获取消息管道号
     List<WebSocketSimulationService> socketSimulations = SimulationGlobal.routerRoom.get(roomId);
+    //REST 删房只清 map 不关 session：客户端续发消息时房间列表可能已不存在，判空短路
+    if (socketSimulations == null) {
+      return;
+    }
     Map<String, Object> msg = JSONUtils.fromJson(message, new TypeToken<>() {
     });
     String topic = msg.get(TOPIC).toString();
     switch (topic) {
       case TRAIN_READY -> //状态修改为准备，且给所有人发送消息
           socketSimulations.forEach(webSocketSimulation -> {
             if (Objects.equals(webSocketSimulation.getUserModel().getId(), userId)) {
               webSocketSimulation.getUserModel().setStatus(2);


---
commit e4a4900b57b9b94cb689242e560b290c519096a8
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 03:39:23 2026 +0800

    fix(ws-4): sendInfoAll 按 sid 定向，消除忽略入参的全员越界广播

diff --git a/src/main/java/com/nip/ws/WebSocketService.java b/src/main/java/com/nip/ws/WebSocketService.java
index b7539d7..fbaef90 100644
--- a/src/main/java/com/nip/ws/WebSocketService.java
+++ b/src/main/java/com/nip/ws/WebSocketService.java
@@ -177,20 +177,21 @@ public class WebSocketService {
           item.session.getAsyncRemote().sendText(msg);
         }
       } catch (Exception e) {
         log.error("WebSocketService sendInfo:{}", e.getMessage());
       }
     }
   }
 
+  /**
+   * 按 sid 定向发送（历史上忽略 sid 对全体广播，属越界推送，已收敛为定向）
+   */
   public static void sendInfoAll(@PathParam("sid") String sid, ResponseModel message) {
-    for (WebSocketService item : webSocketClientSet) {
-      item.sendMessage(message);
-    }
+    sendInfo(sid, message);
   }
 
   public static void sendInfo(@PathParam("sid") String sid, String message) {
     for (WebSocketService item : webSocketClientSet) {
       if (item.sid.equals(sid)) {
         item.sendMessage(message);
       }
     }


---
commit 273f3666c42fe18b6a38dcb43e744f9e36462a0f
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 03:53:54 2026 +0800

    fix(ws-5): 清剩余 P1/P2——成员泄漏、onClose 判空与身份、单例字段清除、广播 async 化

diff --git a/src/main/java/com/nip/ws/StartWebSocket.java b/src/main/java/com/nip/ws/StartWebSocket.java
index 8dbe08f..e699fdd 100644
--- a/src/main/java/com/nip/ws/StartWebSocket.java
+++ b/src/main/java/com/nip/ws/StartWebSocket.java
@@ -4,79 +4,59 @@ import com.nip.common.constants.WsCode;
 import com.nip.common.utils.JSONUtils;
 import com.nip.ws.model.ResponseModel;
 import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.websocket.*;
 import jakarta.websocket.server.PathParam;
 import jakarta.websocket.server.ServerEndpoint;
 import lombok.extern.slf4j.Slf4j;
 
-import java.io.IOException;
-import java.util.ArrayList;
-import java.util.List;
-import java.util.concurrent.CopyOnWriteArraySet;
+import java.util.concurrent.ConcurrentHashMap;
+import java.util.concurrent.ConcurrentMap;
 
 @Slf4j
 @ServerEndpoint("/startWebsocket/{sid}")
 @ApplicationScoped
 public class StartWebSocket {
-  //静态变量，用来记录当前在线连接数。
-  private static final List<String> onlineId = new ArrayList<>();
   /**
-   * concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
+   * 在线连接表：sid -> 该连接的 Session。
+   * 端点是 @ApplicationScoped 单例，连接态一律挂在本表上；
+   * 禁止实例字段（单例 this 进 Set 导致集合恒 1 元素，P1-10 根因）。
    */
-  private static StartWebSocket webSocketServerSet;
-  private static final CopyOnWriteArraySet<StartWebSocket> webSocketClientSet = new CopyOnWriteArraySet<>();
-  /**
-   * 与某个客户端的连接会话，需要通过它来给客户端发送数据
-   */
-  private Session session;
-
-  /**
-   * 接收sid
-   */
-  private String sid = "";
+  private static final ConcurrentMap<String, Session> CLIENTS = new ConcurrentHashMap<>();
 
   @OnOpen
-  public void onOpen(Session session, @PathParam("sid") String sid) throws IOException {
-    for (StartWebSocket item : webSocketClientSet) {
-      if (sid.equals(item.sid)) {
-        item.sendMessage(new ResponseModel(WsCode.CLOSE.getCode(), WsCode.CLOSE.getContent()));
-        webSocketClientSet.remove(item);
-        onlineId.remove(item.sid);
-      }
+  public void onOpen(Session session, @PathParam("sid") String sid) {
+    Session old = CLIENTS.put(sid, session);
+    if (old != null && old != session) {
+      //踢掉同 sid 的旧连接：通知后由客户端自行断开
+      send(old, new ResponseModel(WsCode.CLOSE.getCode(), WsCode.CLOSE.getContent()));
     }
-    this.session = session;
-    webSocketClientSet.add(this);
-    this.sid = sid;
-    onlineId.add(sid);
-    log.info("Client Join: {},Online Clients: {}", sid, webSocketClientSet.size());
+    log.info("Client Join: {},Online Clients: {}", sid, CLIENTS.size());
   }
 
   @OnClose
-  public void onClose() {
-    webSocketClientSet.remove(this);
-    onlineId.remove(this.sid);
-    try {
-      if (null != webSocketServerSet) {
-        webSocketServerSet.sendMessage(
-            new ResponseModel(WsCode.USER_LIST.getCode(), JSONUtils.toJson(onlineId)));
-      }
-    } catch (IOException e) {
-      log.error("send message error", e);
-    }
-    log.info("Client Leave: {}; Online Clients: {}", this.sid, onlineId.size());
+  public void onClose(@PathParam("sid") String sid, Session session) {
+    //条件移除：同 sid 重连后，旧 session 的 onClose 不得摘掉新连接
+    CLIENTS.remove(sid, session);
+    log.info("Client Leave: {}; Online Clients: {}", sid, CLIENTS.size());
   }
 
   @OnError
   public void onError(Session session, @PathParam("sid") String sid, Throwable throwable) {
     log.info("onError: {}, {}", sid, throwable);
   }
 
   @OnMessage
   public void onMessage(String message, @PathParam("sid") String sid) {
     log.info("onMessage: {}, {}", sid, message);
   }
 
-  public void sendMessage(ResponseModel message) throws IOException {
-    this.session.getBasicRemote().sendText(JSONUtils.toJson(message));
+  private static void send(Session session, ResponseModel message) {
+    try {
+      if (session.isOpen()) {
+        session.getAsyncRemote().sendText(JSONUtils.toJson(message));
+      }
+    } catch (Exception e) {
+      log.error("StartWebSocket send:{}", e.getMessage());
+    }
   }
 }
diff --git a/src/main/java/com/nip/ws/StatusWebSocket.java b/src/main/java/com/nip/ws/StatusWebSocket.java
index 9c9bbee..4056fc2 100644
--- a/src/main/java/com/nip/ws/StatusWebSocket.java
+++ b/src/main/java/com/nip/ws/StatusWebSocket.java
@@ -1,46 +1,33 @@
 package com.nip.ws;
 
-import com.nip.common.utils.JSONUtils;
-import com.nip.ws.model.ResponseModel;
 import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.websocket.*;
 import jakarta.websocket.server.ServerEndpoint;
 import lombok.extern.slf4j.Slf4j;
 
-import java.io.IOException;
-
 @ServerEndpoint("/status")
 @ApplicationScoped
 @Slf4j
 public class StatusWebSocket {
-  /**
-   * 与某个客户端的连接会话，需要通过它来给客户端发送数据
-   */
-  private Session session;
 
   @OnOpen
   public void onOpen(Session session) {
-    this.session = session;
     log.info("Client Join: {}", session.getId());
   }
 
   @OnClose
-  public void onClose() {
+  public void onClose(Session session) {
     log.info("Client Leave: {}", session.getId());
   }
 
   @OnError
   public void onError(Session session, Throwable throwable) {
     log.info("onError> : {}", String.valueOf(throwable));
   }
 
   @OnMessage
-  public void onMessage(String message) {
+  public void onMessage(String message, Session session) {
     log.info("onMessage> : {}", message);
     session.getAsyncRemote().sendText("pong");
   }
-
-  public void sendMessage(ResponseModel message) throws IOException {
-    this.session.getBasicRemote().sendText(JSONUtils.toJson(message));
-  }
 }
diff --git a/src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java b/src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java
index f1c2f89..5988167 100644
--- a/src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java
+++ b/src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java
@@ -49,26 +49,23 @@ public class WebSocketGeneralKeyPatService {
     //查询人员是否是组训人员
     GeneralPatTrainUserDto userDto = null;
     try {
       userDto = generalKeyPatService.getTrainUserInfo(uid, trainId);
     } catch (Exception e) {
       log.error("WebSocketGeneralKeyPatService.onOpen: 用户不存在");
       sendErrMessage(session, e.getMessage(), "", "");
       close(session);
+      //P1-7：不 return 会带着全 null 的 userModel 继续执行到 getRole() NPE
+      return;
     }
     GeneralPatTrainUserModelDto userModel = PojoUtils.convertOne(userDto, GeneralPatTrainUserModelDto.class);
     userModel.setSession(session);
     userModel.setStatus(1);
-    GeneralPatTrainRoomUserDto roomUser = Optional.ofNullable(ROOM.get(trainId))
-        .orElseGet(() -> {
-          GeneralPatTrainRoomUserDto room = new GeneralPatTrainRoomUserDto();
-          ROOM.put(trainId, room);
-          return room;
-        });
+    GeneralPatTrainRoomUserDto roomUser = ROOM.computeIfAbsent(trainId, k -> new GeneralPatTrainRoomUserDto());
     Map<String, String> data = new HashMap<>();
     data.put(ID, uid);
     data.put(TOPIC, BaseConstants.ONLINE);
     if (userModel.getRole().compareTo(0) == 0) {
       roomUser.getJoinUser().add(userModel);
       //给教员推送消息
       if (roomUser.getGroupUser() != null) {
         sendMessage(roomUser.getGroupUser().getSession(), JSONObject.toJSONString(data), "", "");
@@ -122,66 +119,72 @@ public class WebSocketGeneralKeyPatService {
         }*/
     //教员的消息需要给所有学员发送消息
     trainRoomUser.getJoinUser().forEach(userModel -> sendMessage(userModel.getSession(), message, "", ""));
   }
 
   @OnClose
   public void onClose(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId, Session session) {
     GeneralPatTrainRoomUserDto keyPatTrainRoomUser = ROOM.get(trainId);
-    if (keyPatTrainRoomUser.getGroupUser() != null) {
-      GeneralPatTrainUserModelDto user = keyPatTrainRoomUser.getGroupUser();
-      List<GeneralPatTrainUserModelDto> joinUser = keyPatTrainRoomUser.getJoinUser();
-      Map<String, String> data = new HashMap<>();
-      data.put(TOPIC, OFFLINE);
-      data.put(ID, uid);
-      //判断是否是组训人退出
-      if (Objects.equals(user.getId(), uid)) {
-        //给所有人发送退出消息
-        keyPatTrainRoomUser.getJoinUser().forEach(item -> sendMessage(item.getSession(), JSONObject.toJSONString(data), "", ""));
-        keyPatTrainRoomUser.setGroupUser(null);
-      } else {
-        GeneralPatTrainUserModelDto removeModel = null;
-        for (GeneralPatTrainUserModelDto userModel : joinUser) {
-          if (Objects.equals(userModel.getId(), uid)) {
-            removeModel = userModel;
-          }
-        }
-        if (keyPatTrainRoomUser.getGroupUser() != null) {
-          sendMessage(keyPatTrainRoomUser.getGroupUser().getSession(), JSONObject.toJSONString(data), "", "");
-        }
-        joinUser.remove(removeModel);
+    if (keyPatTrainRoomUser == null) {
+      close(session);
+      return;
+    }
+    List<GeneralPatTrainUserModelDto> joinUser = keyPatTrainRoomUser.getJoinUser();
+    Map<String, String> data = new HashMap<>();
+    data.put(TOPIC, OFFLINE);
+    data.put(ID, uid);
+    GeneralPatTrainUserModelDto groupUser = keyPatTrainRoomUser.getGroupUser();
+    //判断是否是组训人退出
+    if (groupUser != null && Objects.equals(groupUser.getId(), uid)) {
+      //给所有人发送退出消息
+      joinUser.forEach(item -> sendMessage(item.getSession(), JSONObject.toJSONString(data), "", ""));
+      keyPatTrainRoomUser.setGroupUser(null);
+    } else {
+      //学员退出：教员在线时通知教员；无论教员是否在线都必须移除（P1-8：原逻辑教员缺席时学员永不移除）
+      if (groupUser != null) {
+        sendMessage(groupUser.getSession(), JSONObject.toJSONString(data), "", "");
       }
+      joinUser.removeIf(userModel -> Objects.equals(userModel.getId(), uid));
     }
+    //房间清空后释放条目（原 ROOM 只增不减）
+    ROOM.computeIfPresent(trainId, (k, v) -> (v.getJoinUser().isEmpty() && v.getGroupUser() == null) ? null : v);
     close(session);
   }
 
   @OnError
   public void onError(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId, Session session, Throwable t) {
     log.error("ws error, session={}", session.getId(), t);
     //复用 onClose 清理该 session 对应的房间状态并关闭连接
     onClose(uid, trainId, session);
   }
 
+  /**
+   * 广播发送统一入口：async remote 避免并发 basic 写抛 IllegalStateException；
+   * catch Exception，单个接收方失败不中断循环
+   */
   public static void sendMessage(Session session, String message, String sendName, String receiveName) {
     try {
       if (session.isOpen()) {
-        session.getBasicRemote().sendText(JSONUtils.toJson(SocketResponseModel.success(message, sendName, receiveName)));
+        session.getAsyncRemote().sendText(JSONUtils.toJson(SocketResponseModel.success(message, sendName, receiveName)));
       }
-    } catch (IOException e) {
+    } catch (Exception e) {
       log.error("WebSocketGeneralKeyPatService.sendMessage: 发送消息失败");
     }
   }
 
+  /**
+   * onOpen 拒接路径在 close 前调用：保持同步写确保错误帧先于关闭发出
+   */
   public static void sendErrMessage(Session session, String message, String sendName, String receiveName) {
     try {
       if (session.isOpen()) {
         session.getBasicRemote().sendText(JSONUtils.toJson(SocketResponseModel.err(message, sendName, receiveName)));
       }
-    } catch (IOException e) {
+    } catch (Exception e) {
       log.error("WebSocketGeneralKeyPatService.sendErrMessage: 发送消息失败");
     }
   }
 
   private void close(Session session) {
     try {
       session.close();
     } catch (IOException e) {
diff --git a/src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java b/src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java
index b15d7ef..9c13a21 100644
--- a/src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java
+++ b/src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java
@@ -46,29 +46,26 @@ public class WebSocketGeneralTelexPatService {
    */
   @OnOpen
   public void onOpen(@PathParam("uid") String uid, @PathParam(TRAIN_ID) String trainId, Session session) {
     //查询人员是否是组训人员
     GeneralPatTrainUserDto userDto = null;
     try {
       userDto = generalTelexPatService.getTrainUserInfo(uid, trainId);
     } catch (Exception e) {
-      log.error("WebSocketGeneralKeyPatService.onOpen: 用户不存在");
+      log.error("WebSocketGeneralTelexPatService.onOpen: 用户不存在");
       sendErrMessage(session, e.getMessage(), "", "");
       close(session);
+      //P1-7：不 return 会带着全 null 的 userModel 继续执行到 getRole() NPE
+      return;
     }
     GeneralPatTrainUserModelDto userModel = PojoUtils.convertOne(userDto, GeneralPatTrainUserModelDto.class);
     userModel.setSession(session);
     userModel.setStatus(1);
-    GeneralPatTrainRoomUserDto roomUser = Optional.ofNullable(ROOM.get(trainId))
-        .orElseGet(() -> {
-          GeneralPatTrainRoomUserDto room = new GeneralPatTrainRoomUserDto();
-          ROOM.put(trainId, room);
-          return room;
-        });
+    GeneralPatTrainRoomUserDto roomUser = ROOM.computeIfAbsent(trainId, k -> new GeneralPatTrainRoomUserDto());
     Map<String, String> data = new HashMap<>();
     data.put(ID, uid);
     data.put(TOPIC, BaseConstants.ONLINE);
     if (userModel.getRole().compareTo(0) == 0) {
       roomUser.getJoinUser().add(userModel);
       //给教员推送消息
       if (roomUser.getGroupUser() != null) {
         sendMessage(roomUser.getGroupUser().getSession(), JSONObject.toJSONString(data), "", "");
@@ -114,67 +111,73 @@ public class WebSocketGeneralTelexPatService {
     }
     //教员的消息需要给所有学员发送消息
     trainRoomUser.getJoinUser().forEach(userModel -> sendMessage(userModel.getSession(), message, "", ""));
   }
 
   @OnClose
   public void onClose(@PathParam("uid") String uid, @PathParam(TRAIN_ID) String trainId, Session session) {
     GeneralPatTrainRoomUserDto keyPatTrainRoomUser = ROOM.get(trainId);
-    if (keyPatTrainRoomUser.getGroupUser() != null) {
-      GeneralPatTrainUserModelDto user = keyPatTrainRoomUser.getGroupUser();
-      List<GeneralPatTrainUserModelDto> joinUser = keyPatTrainRoomUser.getJoinUser();
-      Map<String, String> data = new HashMap<>();
-      data.put(TOPIC, OFFLINE);
-      data.put(ID, uid);
-      //判断是否是组训人退出
-      if (Objects.equals(user.getId(), uid)) {
-        //给所有人发送退出消息
-        keyPatTrainRoomUser.getJoinUser().forEach(item -> sendMessage(item.getSession(), JSONObject.toJSONString(data), "", ""));
-        keyPatTrainRoomUser.setGroupUser(null);
-      } else {
-        GeneralPatTrainUserModelDto removeModel = null;
-        for (GeneralPatTrainUserModelDto userModel : joinUser) {
-          if (Objects.equals(userModel.getId(), uid)) {
-            removeModel = userModel;
-          }
-        }
-        if (keyPatTrainRoomUser.getGroupUser() != null) {
-          sendMessage(keyPatTrainRoomUser.getGroupUser().getSession(), JSONObject.toJSONString(data), "", "");
-        }
-        joinUser.remove(removeModel);
+    if (keyPatTrainRoomUser == null) {
+      close(session);
+      return;
+    }
+    List<GeneralPatTrainUserModelDto> joinUser = keyPatTrainRoomUser.getJoinUser();
+    Map<String, String> data = new HashMap<>();
+    data.put(TOPIC, OFFLINE);
+    data.put(ID, uid);
+    GeneralPatTrainUserModelDto groupUser = keyPatTrainRoomUser.getGroupUser();
+    //判断是否是组训人退出
+    if (groupUser != null && Objects.equals(groupUser.getId(), uid)) {
+      //给所有人发送退出消息
+      joinUser.forEach(item -> sendMessage(item.getSession(), JSONObject.toJSONString(data), "", ""));
+      keyPatTrainRoomUser.setGroupUser(null);
+    } else {
+      //学员退出：教员在线时通知教员；无论教员是否在线都必须移除（P1-8：原逻辑教员缺席时学员永不移除）
+      if (groupUser != null) {
+        sendMessage(groupUser.getSession(), JSONObject.toJSONString(data), "", "");
       }
+      joinUser.removeIf(userModel -> Objects.equals(userModel.getId(), uid));
     }
+    //房间清空后释放条目（原 ROOM 只增不减）
+    ROOM.computeIfPresent(trainId, (k, v) -> (v.getJoinUser().isEmpty() && v.getGroupUser() == null) ? null : v);
     close(session);
   }
 
   @OnError
   public void onError(@PathParam("uid") String uid, @PathParam(TRAIN_ID) String trainId, Session session, Throwable t) {
     log.error("ws error, session={}", session.getId(), t);
     //复用 onClose 清理该 session 对应的房间状态并关闭连接
     onClose(uid, trainId, session);
   }
 
+  /**
+   * 广播发送统一入口：async remote 避免并发 basic 写抛 IllegalStateException；
+   * catch Exception，单个接收方失败不中断循环
+   */
   public static void sendMessage(Session session, String message, String sendName, String receiveName) {
     try {
       if (session.isOpen()) {
-        session.getBasicRemote().sendText(JSONUtils.toJson(SocketResponseModel.success(message, sendName, receiveName)));
+        session.getAsyncRemote().sendText(JSONUtils.toJson(SocketResponseModel.success(message, sendName, receiveName)));
       }
-    } catch (IOException e) {
-      log.error("WebSocketGeneralKeyPatService.sendMessage: 发送消息失败");
+    } catch (Exception e) {
+      log.error("WebSocketGeneralTelexPatService.sendMessage: 发送消息失败");
     }
   }
 
+  /**
+   * onOpen 拒接路径在 close 前调用：保持同步写确保错误帧先于关闭发出
+   */
   public static void sendErrMessage(Session session, String message, String sendName, String receiveName) {
     try {
       if (session.isOpen()) {
         session.getBasicRemote().sendText(JSONUtils.toJson(SocketResponseModel.err(message, sendName, receiveName)));
       }
-    } catch (IOException e) {
-      log.error("WebSocketGeneralKeyPatService.sendErrMessage: 发送消息失败");
+    } catch (Exception e) {
+      log.error("WebSocketGeneralTelexPatService.sendErrMessage: 发送消息失败");
     }
   }
 
   private void close(Session session) {
     try {
       session.close();
     } catch (IOException e) {
       log.error("关闭socket出错:{}", e.getMessage());
diff --git a/src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java b/src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java
index 76ffa36..35985a4 100644
--- a/src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java
+++ b/src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java
@@ -33,21 +33,18 @@ public class WebSocketGeneralTickerPatService {
   @OnOpen
   public void onOpen(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId, @PathParam("role") Integer role, Session session) {
     log.info("用户：{}，进入房间", uid);
     GeneralTickerPatTrainUserModel userModel = new GeneralTickerPatTrainUserModel();
     userModel.setSession(session);
     userModel.setStatus(1);
     userModel.setId(uid);
     userModel.setRole(role);
-    GeneralTickerPatTrainRoomUserModel roomUser = PAT_ROOM.get(trainId);
-    if (roomUser == null) {
-      roomUser = new GeneralTickerPatTrainRoomUserModel();
-      PAT_ROOM.put(trainId, roomUser);
-    }
+    GeneralTickerPatTrainRoomUserModel roomUser =
+        PAT_ROOM.computeIfAbsent(trainId, k -> new GeneralTickerPatTrainRoomUserModel());
     Map<String, Object> msg = new HashMap<>();
     msg.put(TOPIC, ONLINE);
     msg.put(ID, uid);
     if (userModel.getRole().compareTo(0) == 0) { //学员
       roomUser.getJoinUser().add(userModel);
       //通知教员
       if (roomUser.getGroupUser() != null) {
         boolean b = sendMessage(roomUser.getGroupUser().getSession(), JSONUtils.toJson(msg), "", "");
@@ -73,16 +70,21 @@ public class WebSocketGeneralTickerPatService {
       }
     }
   }
 
   @OnMessage
   public void onMessage(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId, String message, Session session) {
 //    log.info("收到{}训练：{}的消息：{}", trainId, uid, message);
     GeneralTickerPatTrainRoomUserModel roomUser = PAT_ROOM.get(trainId);
+    //房间可能已被 REST 删除（delete 只清 map 不关 session），判空短路
+    if (roomUser == null) {
+      sendErrMessage(session, "房间不存在", "", "");
+      return;
+    }
     Map<String, Object> msg = JSONUtils.fromJson(message, new TypeToken<>() {
     });
     String topic = msg.get(BaseConstants.TOPIC).toString();
     switch (topic) {
       case TRAIN_READY -> {
         //给老师推送
         for (GeneralTickerPatTrainUserModel userModel : roomUser.getJoinUser()) {
           if (Objects.equals(userModel.getId(), uid)) {
@@ -161,57 +163,52 @@ public class WebSocketGeneralTickerPatService {
           break;
         }
       }
       if (roomUser.getGroupUser() != null) {
         sendMessage(roomUser.getGroupUser().getSession(), JSONUtils.toJson(msg), "", "");
       }
       roomUser.getJoinUser().remove(remove);
     }
-    if (roomUser.getJoinUser().isEmpty() && roomUser.getGroupUser() == null) {
-//      log.info("房间：{}，所有人已离开", trainId);
-      PAT_ROOM.remove(trainId);
-    }
+    //原子清房：与并发 onOpen 的 computeIfAbsent 互斥，避免删掉刚建的房间（P1-6）
+    PAT_ROOM.computeIfPresent(trainId, (k, v) -> (v.getJoinUser().isEmpty() && v.getGroupUser() == null) ? null : v);
 //    log.info("房间信息：{}", PAT_ROOM);
   }
 
   @OnError
   public void onError(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId, Session session, Throwable t) {
     log.error("ws error, session={}", session.getId(), t);
     //复用 onClose 清理该 session 对应的房间状态，并关闭连接
     onClose(uid, trainId);
     try {
       session.close();
     } catch (IOException e) {
       log.error("关闭socket出错:{}", e.getMessage());
     }
   }
 
+  /**
+   * 广播发送统一入口：async remote 避免并发 basic 写抛 IllegalStateException；
+   * 返回 false 表示连接已关闭或提交失败，调用方据此清理死会话
+   */
   public static boolean sendMessage(Session session, String message, String sendName, String receiveName) {
     try {
       if (session.isOpen()) {
-        session.getBasicRemote().sendText(JSONUtils.toJson(SocketResponseModel.success(message, sendName, receiveName)));
+        session.getAsyncRemote().sendText(JSONUtils.toJson(SocketResponseModel.success(message, sendName, receiveName)));
         return true;
       } else {
         return false;
       }
-    } catch (IOException e) {
-//      log.error("发送消息失败：{}", e.getMessage());
+    } catch (Exception e) {
       return false;
     }
   }
 
-  public static void sendMessageThrow(Session session, String message, String sendName, String receiveName) throws IOException {
-    if (session.isOpen()) {
-      session.getBasicRemote().sendText(JSONUtils.toJson(SocketResponseModel.success(message, sendName, receiveName)));
-    }
-  }
-
   public static void sendErrMessage(Session session, String message, String sendName, String receiveName) {
     try {
       if (session.isOpen()) {
-        session.getBasicRemote().sendText(JSONUtils.toJson(SocketResponseModel.err(message, sendName, receiveName)));
+        session.getAsyncRemote().sendText(JSONUtils.toJson(SocketResponseModel.err(message, sendName, receiveName)));
       }
-    } catch (IOException e) {
+    } catch (Exception e) {
       log.error("WebSocketGeneralTickerPatService.sendErrMessage: 发送消息失败");
     }
   }
 }
diff --git a/src/main/java/com/nip/ws/WebSocketService.java b/src/main/java/com/nip/ws/WebSocketService.java
index fbaef90..ef494d6 100644
--- a/src/main/java/com/nip/ws/WebSocketService.java
+++ b/src/main/java/com/nip/ws/WebSocketService.java
@@ -9,99 +9,69 @@ import com.nip.entity.TelegramTrainLogEntity;
 import com.nip.service.event.WebSocketEventService;
 import com.nip.service.simulation.SimulationRouterRoomUserService;
 import com.nip.ws.model.ResponseModel;
 import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.inject.Inject;
 import jakarta.websocket.*;
 import jakarta.websocket.server.PathParam;
 import jakarta.websocket.server.ServerEndpoint;
-import lombok.Data;
 import lombok.extern.slf4j.Slf4j;
 
-import java.io.IOException;
 import java.math.BigDecimal;
-import java.util.ArrayList;
-import java.util.List;
 import java.util.Map;
-import java.util.Objects;
 import java.util.concurrent.CompletableFuture;
-import java.util.concurrent.CopyOnWriteArraySet;
+import java.util.concurrent.ConcurrentHashMap;
+import java.util.concurrent.ConcurrentMap;
 
 /**
  * WebSocketService
  *
  * @author < a href=" ">ZhangYang</ a>
  * @version v1.0.01
  * @date 2018-12-14 11:24
  */
 @ServerEndpoint(value = "/websocket/{sid}")
 @ApplicationScoped
-@Data
 @Slf4j
 public class WebSocketService {
 
   @Inject
   private WebSocketEventService webSocketEventService;
   @Inject
   private SimulationRouterRoomUserService roomUserService;
 
-  //静态变量，用来记录当前在线连接数
-  private static List<String> onlineId = new ArrayList<>();
   /**
-   * concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
+   * 在线连接表：sid -> 该连接的 Session。
+   * 端点是 @ApplicationScoped 单例，连接态一律挂在本表上；
+   * 禁止实例字段（共享字段恒为最后连接者身份，P1-9/P1-10 根因）。
    */
-  private static WebSocketService webSocketServerSet;
-  private static CopyOnWriteArraySet<WebSocketService> webSocketClientSet = new CopyOnWriteArraySet<>();
-
-  /**
-   * 与某个客户端的连接会话，需要通过它来给客户端发送数据
-   */
-  private Session session;
-
-  /**
-   * 接收sid
-   */
-  private String sid = "";
+  private static final ConcurrentMap<String, Session> CLIENTS = new ConcurrentHashMap<>();
 
   /**
    * 连接建立成功调用的方法
    */
   @OnOpen
-  public void onOpen(Session session, @PathParam("sid") String sid) throws IOException {
-    for (WebSocketService item : webSocketClientSet) {
-      if (sid.equals(item.sid)) {
-        item.sendMessage(new ResponseModel(CodeConstants.CLOSE.getCode(), CodeConstants.CLOSE.getContent()));
-        webSocketClientSet.remove(item);
-        onlineId.remove(item.sid);
-      }
+  public void onOpen(Session session, @PathParam("sid") String sid) {
+    Session old = CLIENTS.put(sid, session);
+    if (old != null && old != session) {
+      //踢掉同 sid 的旧连接：通知后由客户端自行断开
+      send(old, JSONUtils.toJson(new ResponseModel(CodeConstants.CLOSE.getCode(), CodeConstants.CLOSE.getContent())));
     }
-    this.session = session;
-    this.sid = sid;
-    WebSocketService service = new WebSocketService();
-    service.setSession(session);
-    service.setSid(sid);
-    webSocketClientSet.add(service);
-    onlineId.add(sid);
-    log.info("Client Join: {},Online Clients: {}", sid, webSocketClientSet.size());
+    log.info("Client Join: {},Online Clients: {}", sid, CLIENTS.size());
   }
 
   /**
    * 连接关闭调用的方法
    */
   @OnClose
-  public void onClose(@PathParam(value = "sid") String sid) {
-    removeClient(sid);
-    webSocketClientSet.remove(this);
-    onlineId.remove(this.sid);
-    if (null != webSocketServerSet) {
-      webSocketServerSet.sendMessage(
-          new ResponseModel(CodeConstants.USER_LIST.getCode(), JSONUtils.toJson(onlineId)));
-    }
-    log.info("Client Leave: {}; Online Clients: {}", this.sid, onlineId.size());
+  public void onClose(@PathParam(value = "sid") String sid, Session session) {
+    //条件移除：同 sid 重连后，旧 session 的 onClose 不得摘掉新连接
+    CLIENTS.remove(sid, session);
+    log.info("Client Leave: {}; Online Clients: {}", sid, CLIENTS.size());
   }
 
   /**
    * 收到客户端消息后调用的方法
    *
    * @param message 客户端发送过来的消息
    */
   @OnMessage
@@ -111,23 +81,29 @@ public class WebSocketService {
 
     if (model != null) {
       switch (new BigDecimal(model.get("code").toString()).intValue()) {
         case 2001:
           TelegramTrainLogEntity telegramTrainLogEntity = PojoUtils.convertOne(model.get("data"), TelegramTrainLogEntity.class);
           CompletableFuture.runAsync(() -> {
             webSocketEventService.saveTelegramTrainLog(telegramTrainLogEntity);
             log.info("更新手键日志");
+          }).exceptionally(t -> {
+            log.error("保存手键日志失败", t);
+            return null;
           });
           break;
         case 3001:
           TelegramTrainFloorContentEntity contentEntity = PojoUtils.convertOne(model.get("data"), TelegramTrainFloorContentEntity.class);
           CompletableFuture.runAsync(() -> {
             webSocketEventService.saveTelegramTrainFloorContentEntity(contentEntity);
             log.info("更新key and time ");
+          }).exceptionally(t -> {
+            log.error("保存楼层内容失败", t);
+            return null;
           });
           break;
         default:
           break;
       }
     }
   }
 
@@ -136,64 +112,45 @@ public class WebSocketService {
    * @param error
    */
   @OnError
   public void onError(Session session, Throwable error) {
     log.error("WebSocketService onError:{}", error.getMessage());
   }
 
   /**
-   * 迭代删除某个用户
-   *
-   * @param sid 用户id
-   */
-  public void removeClient(String sid) {
-    for (WebSocketService next : webSocketClientSet) {
-      if (Objects.equals(sid, next.getSid())) {
-        webSocketClientSet.remove(next);
-        onlineId.remove(sid);
-      }
-    }
-  }
-
-  /**
-   * 实现服务器主动推送
-   */
-  public void sendMessage(ResponseModel message) {
-    this.session.getAsyncRemote().sendText(JSONUtils.toJson(message));
-  }
-
-  public void sendMessage(String message) {
-    this.session.getAsyncRemote().sendText(message);
-  }
-
-
-  /**
-   * 群发自定义消息
+   * 按 sid 定向发送
    */
   public static void sendInfo(@PathParam("sid") String sid, ResponseModel message) {
-    for (WebSocketService item : webSocketClientSet) {
-      try {
-        if (item.sid.equals(sid)) {
-          String msg = JSONUtils.toJson(message);
-          item.session.getAsyncRemote().sendText(msg);
-        }
-      } catch (Exception e) {
-        log.error("WebSocketService sendInfo:{}", e.getMessage());
-      }
+    Session session = CLIENTS.get(sid);
+    if (session != null) {
+      send(session, JSONUtils.toJson(message));
     }
   }
 
   /**
    * 按 sid 定向发送（历史上忽略 sid 对全体广播，属越界推送，已收敛为定向）
    */
   public static void sendInfoAll(@PathParam("sid") String sid, ResponseModel message) {
     sendInfo(sid, message);
   }
 
   public static void sendInfo(@PathParam("sid") String sid, String message) {
-    for (WebSocketService item : webSocketClientSet) {
-      if (item.sid.equals(sid)) {
-        item.sendMessage(message);
+    Session session = CLIENTS.get(sid);
+    if (session != null) {
+      send(session, message);
+    }
+  }
+
+  /**
+   * 出站发送统一入口：async remote（Undertow 内部排队，避免并发 basic 写抛
+   * IllegalStateException）；catch Exception，单个接收方失败不得中断调用方
+   */
+  private static void send(Session session, String message) {
+    try {
+      if (session.isOpen()) {
+        session.getAsyncRemote().sendText(message);
       }
+    } catch (Exception e) {
+      log.error("WebSocketService send:{}", e.getMessage());
     }
   }
 }
diff --git a/src/main/java/com/nip/ws/WebSocketSimulationService.java b/src/main/java/com/nip/ws/WebSocketSimulationService.java
index 014db0b..74a7015 100644
--- a/src/main/java/com/nip/ws/WebSocketSimulationService.java
+++ b/src/main/java/com/nip/ws/WebSocketSimulationService.java
@@ -17,16 +17,17 @@ import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
 import com.nip.ws.model.SimulationResponseModel;
 import com.nip.ws.model.SimulationUserModel;
 import com.nip.ws.service.simulation.SimulationGlobal;
 import io.quarkus.runtime.annotations.RegisterForReflection;
 import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.inject.Inject;
 import jakarta.transaction.Transactional;
 import jakarta.websocket.OnClose;
+import jakarta.websocket.OnError;
 import jakarta.websocket.OnMessage;
 import jakarta.websocket.OnOpen;
 import jakarta.websocket.Session;
 import jakarta.websocket.server.PathParam;
 import jakarta.websocket.server.ServerEndpoint;
 import lombok.Data;
 import lombok.extern.slf4j.Slf4j;
 import org.jose4j.json.internal.json_simple.JSONObject;
@@ -191,67 +192,67 @@ public class WebSocketSimulationService {
       quitRoomReport(roomId, id);
     } else if (Objects.equals(RECEPT.getType(), roomEntity.getRoomType())) {
       quitRoomReport(roomId, id);
     } else if (Objects.equals(ROUTER.getType(), roomEntity.getRoomType())) {
       quitRoomRouter(roomId, id);
     }
   }
 
+  @OnError
+  public void onError(@PathParam(ID) String id, @PathParam(ROOM_ID) Integer roomId, Session session, Throwable t) {
+    log.error("ws error, session={}", session.getId(), t);
+    //复用 onClose 清理该 session 对应的房间状态
+    onClose(id, roomId);
+  }
+
   @Transactional
   public void quitRoomDisturb(Integer roomId, String userId) {
     List<WebSocketSimulationService> simulations = SimulationGlobal.disturbRoom.get(roomId);
     if (Objects.isNull(simulations)) {
       return;
     }
     //查询用户角色
     SimulationRouterRoomUserEntity roomUserEntity = roomUserDao.findByUserIdAndRoomId(userId, roomId);
     List<WebSocketSimulationService> collect = new ArrayList<>();
-    Integer removeIndex = null;
     //学员退出
     if (roomUserEntity.getUserType().compareTo(1) == 0) {
-      for (int i = 0; i < simulations.size(); i++) {
-        WebSocketSimulationService socketSimulation = simulations.get(i);
+      for (WebSocketSimulationService socketSimulation : simulations) {
         if (socketSimulation.getUserModel().getUserType().compareTo(0) == 0) {
           collect.add(socketSimulation);
         }
-        if (Objects.equals(socketSimulation.getUserModel().getId(), userId)) {
-          removeIndex = i;
-        }
       }
       Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
       if (optional.isPresent()) {
         SimulationRouterRoomEntity roomEntity = optional.get();
         if (roomEntity.getStats().compareTo(0) == 0) {
           roomUserDao.remove(roomId, userId);
         }
       }
     }
     //教员退出
     else {
-      for (int i = 0; i < simulations.size(); i++) {
-        WebSocketSimulationService webSocketSimulationService = simulations.get(i);
+      for (WebSocketSimulationService webSocketSimulationService : simulations) {
         if (webSocketSimulationService.getUserModel().getUserType().compareTo(1) == 0) {
           collect.add(webSocketSimulationService);
         }
-        if (Objects.equals(webSocketSimulationService.getUserModel().getId(), userId)) {
-          removeIndex = i;
-        }
       }
     }
     collect.forEach(item -> {
       Map<String, Object> message = new HashMap<>();
       Map<String, String> body = new HashMap<>();
       body.put(ID, userId);
       message.put(BaseConstants.TOPIC, BaseConstants.OFFLINE);
       message.put(BaseConstants.BODY, body);
       WebSocketSimulationService.sendMessage(item.getSession(), JSONObject.toJSONString(message), "", "");
     });
 
-    if (removeIndex != null && simulations.isEmpty()) {
+    //移除退出者会话；房间清空后释放房间条目（P1-4：原 removeIndex 逻辑从不移除，永久泄漏）
+    simulations.removeIf(s -> Objects.equals(s.getUserModel().getId(), userId));
+    if (simulations.isEmpty()) {
       SimulationGlobal.disturbRoom.remove(roomId);
     }
   }
 
   @Transactional
   public void quitRoomReport(Integer roomId, String userId) {
     List<WebSocketSimulationService> simulations = SimulationGlobal.reportRoom.get(roomId);
     if (Objects.isNull(simulations)) {
@@ -623,47 +624,56 @@ public class WebSocketSimulationService {
           socketSimulations.forEach(item -> WebSocketSimulationService.sendMessage(item.getSession(), message, "", ""));
       // 切换频道
       case TRAIN_CHANGE -> //给所有人推送消息组训人
           socketSimulations.forEach(item -> WebSocketSimulationService.sendMessage(item.getSession(), message, "", ""));
       case null, default -> log.error("未知主题：{}", msg);
     }
   }
 
+  /**
+   * 仅 onOpen 拒接路径使用：此刻 session 尚未入房、无并发写者，保持同步写，
+   * 确保紧随其后的 session.close() 前错误帧已发出
+   */
   private void sendErrorMessage(Session session, String errorMsg, String sendName, String receiveName) {
     try {
-      session.getBasicRemote().sendText(JSONUtils.toJson(SimulationResponseModel.err(errorMsg, sendName, receiveName)));
-    } catch (IOException e) {
+      if (session.isOpen()) {
+        session.getBasicRemote().sendText(JSONUtils.toJson(SimulationResponseModel.err(errorMsg, sendName, receiveName)));
+      }
+    } catch (Exception e) {
       log.error("WebSocketSimulationService.sendErrorMessage:{}", e.getMessage());
     }
   }
 
+  /**
+   * 广播发送统一入口：async remote（Undertow 内部排队，避免多线程并发 basic 写抛
+   * IllegalStateException 打断整轮广播）；catch Exception，单个接收方失败不中断循环
+   */
   public static void sendMessage(Session session, String message, String sendName, String receiveName) {
     try {
       if (session.isOpen()) {
-        session.getBasicRemote().sendText(JSONUtils.toJson(SimulationResponseModel.success(message, sendName, receiveName)));
+        session.getAsyncRemote().sendText(JSONUtils.toJson(SimulationResponseModel.success(message, sendName, receiveName)));
       }
-    } catch (IOException e) {
+    } catch (Exception e) {
       log.error("WebSocketSimulationService.sendMessage:{}", e.getMessage());
     }
   }
 
   /**
    * 踢出旧连接
    *
    * @param simulations 连接
    * @param userId      用户id
    */
   public void kickOutOld(List<WebSocketSimulationService> simulations, String userId) {
-    for (int i = 0; i < simulations.size(); i++) {
-      WebSocketSimulationService webSocketSimulationService = simulations.get(i);
-      if (Objects.equals(webSocketSimulationService.getUserModel().getId(), userId)) {
+    //先关旧连接再整体移除：索引 for 内 remove 会因元素前移漏踢（P2-3）
+    for (WebSocketSimulationService item : simulations) {
+      if (Objects.equals(item.getUserModel().getId(), userId)) {
         try {
-          webSocketSimulationService.getSession().close();
+          item.getSession().close();
         } catch (IOException e) {
           log.error("WebSocketSimulationService.kickOutOld:{}", e.getMessage());
-        } finally {
-          simulations.remove(webSocketSimulationService);
         }
       }
     }
+    simulations.removeIf(item -> Objects.equals(item.getUserModel().getId(), userId));
   }
 }
diff --git a/src/main/java/com/nip/ws/model/GeneralTickerPatTrainRoomUserModel.java b/src/main/java/com/nip/ws/model/GeneralTickerPatTrainRoomUserModel.java
index 9123be1..060be22 100644
--- a/src/main/java/com/nip/ws/model/GeneralTickerPatTrainRoomUserModel.java
+++ b/src/main/java/com/nip/ws/model/GeneralTickerPatTrainRoomUserModel.java
@@ -1,21 +1,22 @@
 package com.nip.ws.model;
 
 import io.quarkus.runtime.annotations.RegisterForReflection;
 import lombok.Data;
 
-import java.util.ArrayList;
 import java.util.List;
+import java.util.concurrent.CopyOnWriteArrayList;
 
 @Data
 @RegisterForReflection
 public class GeneralTickerPatTrainRoomUserModel {
   /**
    * 组训人员
    */
   private GeneralTickerPatTrainUserModel groupUser;
 
   /**
-   * 参训人员
+   * 参训人员。WS onOpen/onClose 增删、onMessage 广播遍历、HTTP 线程拷贝读取
+   * 三方向并发，必须是 CopyOnWriteArrayList（P1-6）
    */
-  private List<GeneralTickerPatTrainUserModel> joinUser = new ArrayList<>();
+  private List<GeneralTickerPatTrainUserModel> joinUser = new CopyOnWriteArrayList<>();
 }


---
commit 474c61679a8e284e01735e92b90639ed39a398bf
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 04:04:42 2026 +0800

    fix(ws-6): 50 轮并发进出泄漏测试+压出的 RoomModel.users CME 修复（COW 化）

diff --git a/src/main/java/com/nip/ws/WebSocketUnionService.java b/src/main/java/com/nip/ws/WebSocketUnionService.java
index fd3b0c4..3e7c934 100644
--- a/src/main/java/com/nip/ws/WebSocketUnionService.java
+++ b/src/main/java/com/nip/ws/WebSocketUnionService.java
@@ -18,16 +18,17 @@ import jakarta.websocket.server.PathParam;
 import jakarta.websocket.server.ServerEndpoint;
 import lombok.extern.slf4j.Slf4j;
 import org.apache.commons.lang3.StringUtils;
 
 import java.io.IOException;
 import java.math.BigDecimal;
 import java.util.*;
 import java.util.concurrent.ConcurrentHashMap;
+import java.util.concurrent.CopyOnWriteArrayList;
 import java.util.concurrent.atomic.AtomicReference;
 
 import static com.nip.common.constants.BaseConstants.TYPE;
 import static com.nip.common.constants.BaseConstants.USER_ID;
 
 /**
  * 联合训练
  *
@@ -247,17 +248,18 @@ public class WebSocketUnionService {
     try {
       RoomModel room = JSONUtils.fromJson(msg.getData(), RoomModel.class);
       room.setId(StrUtil.toString(SnowflakeIdKit.getInstance().nextId()));
       room.setAdmin(me.user().getId());
       UserModel userModel = new UserModel();
       userModel.setId(me.user().getId());
       userModel.setName(me.user().getName());
       userModel.setUserImg(me.user().getUserImg());
-      List<UserModel> user = new ArrayList<>();
+      //成员表被 EXIT_ROOM 的 removeIf 与 REMOVE_ROOM/广播的 forEach 跨连接线程并发操作，必须 COW
+      List<UserModel> user = new CopyOnWriteArrayList<>();
       user.add(userModel);
       room.setUsers(user);
       onlineRooms.put(room.getId(), room);
       send(me.session(), new ResponseModel(UnionConstants.ADD_ROOM_SUCCESS.getCode(), JSONUtils.toJson(room)));
       updateRoom(room);
     } catch (Exception e) {
       send(me.session(), new ResponseModel(UnionConstants.ADD_ROOM_FAIL.getCode()));
     }
diff --git a/src/main/java/com/nip/ws/model/RoomModel.java b/src/main/java/com/nip/ws/model/RoomModel.java
index 8ff1c87..19c807a 100644
--- a/src/main/java/com/nip/ws/model/RoomModel.java
+++ b/src/main/java/com/nip/ws/model/RoomModel.java
@@ -1,14 +1,15 @@
 package com.nip.ws.model;
 
 import io.quarkus.runtime.annotations.RegisterForReflection;
 import lombok.Data;
 
 import java.util.List;
+import java.util.concurrent.CopyOnWriteArrayList;
 
 /**
  * RoomModel
  *
  * @author < a href=" ">ZhangYang</ a>
  * @version v1.0.01
  * @date 2022-05-25 9:33
  */
@@ -17,10 +18,13 @@ import java.util.List;
 public class RoomModel {
   private String id;
   private String name;
   private Integer type = 0; // 0，一发多收；1，多发多收；2，抄收竞速；3，发报竞速
   private Integer status = 0; // 0,静默阶段；1，检测阶段；2，进行阶段
   private String password;
   private Integer nnt = 8;
   private String admin;
-  private List<UserModel> users;
+  /**
+   * 房间成员。跨连接线程并发增删与遍历，必须保持 CopyOnWriteArrayList
+   */
+  private List<UserModel> users = new CopyOnWriteArrayList<>();
 }
diff --git a/src/test/java/com/nip/ws/WebSocketUnionTest.java b/src/test/java/com/nip/ws/WebSocketUnionTest.java
index 202a09d..12f2a03 100644
--- a/src/test/java/com/nip/ws/WebSocketUnionTest.java
+++ b/src/test/java/com/nip/ws/WebSocketUnionTest.java
@@ -9,20 +9,22 @@ import io.quarkus.test.junit.QuarkusTest;
 import jakarta.inject.Inject;
 import jakarta.websocket.ClientEndpoint;
 import jakarta.websocket.ContainerProvider;
 import jakarta.websocket.OnMessage;
 import jakarta.websocket.Session;
 import jakarta.websocket.WebSocketContainer;
 import org.junit.jupiter.api.Test;
 
+import java.lang.reflect.Field;
 import java.math.BigDecimal;
 import java.net.URI;
 import java.util.Map;
 import java.util.concurrent.LinkedBlockingQueue;
+import java.util.concurrent.CompletableFuture;
 import java.util.concurrent.TimeUnit;
 
 import static org.junit.jupiter.api.Assertions.assertEquals;
 import static org.junit.jupiter.api.Assertions.assertNotNull;
 import static org.junit.jupiter.api.Assertions.assertNull;
 import static org.junit.jupiter.api.Assertions.assertTrue;
 
 @QuarkusTest
@@ -133,16 +135,21 @@ class WebSocketUnionTest {
     WebSocketContainer c = ContainerProvider.getWebSocketContainer();
     Probe p1 = new Probe();
     Probe p2 = new Probe();
     Probe p3 = new Probe();
     try (Session s1 = c.connectToServer(p1, URI.create("ws://localhost:18081/websocketUnion/" + id1));
          Session s2 = c.connectToServer(p2, URI.create("ws://localhost:18081/websocketUnion/" + id2));
          Session s3 = c.connectToServer(p3, URI.create("ws://localhost:18081/websocketUnion/" + id3))) {
 
+      // onOpen 异步派发：注册完成前服务端会丢弃消息，先等三条连接全部注册
+      awaitRegistered(s1, p1);
+      awaitRegistered(s2, p2);
+      awaitRegistered(s3, p3);
+
       // u1 建房
       s1.getBasicRemote().sendText("{\"code\":12,\"data\":{\"name\":\"room-b\"}}");
       Map added = pollForCode(p1, 120, 5); // ADD_ROOM_SUCCESS
       assertNotNull(added, "u1 建房后必须收到 ADD_ROOM_SUCCESS(120)");
       Map room = JSONUtils.fromJson(added.get("data").toString(), Map.class);
       String roomId = room.get("id").toString();
 
       // u2 入房（房间id 是 Snowflake 数字，按数字字面量发送）
@@ -162,16 +169,94 @@ class WebSocketUnionTest {
       assertNotNull(got, "房间成员 u2 必须收到 ROOM_MESSAGE(20)");
       assertEquals(id1, got.get("sendUser"), "消息发送者必须是 u1");
       assertTrue(got.get("data").toString().contains("hello"), "消息内容必须可达");
 
       assertNull(pollForCode(p3, 20, 2), "房间外的 u3 不得收到 ROOM_MESSAGE");
     }
   }
 
+  // 用例D（Task 2.6）：50 次并发进出房间循环后，连接表与房间表必须清零。
+  // 守护对象：webSocketClientSet 条件移除、userExit 全表清理、removeRoom 解散——任何一处泄漏
+  // （P1-4/P1-5 同类缺陷在 Union 家族的表现）都会让全局静态表残留条目。
+  @Test
+  void concurrentChurnLeavesNoResidualState() throws Exception {
+    String idA = Fixtures.user(userDao, "t-ws-d1").getId();
+    String idB = Fixtures.user(userDao, "t-ws-d2").getId();
+    WebSocketContainer c = ContainerProvider.getWebSocketContainer();
+    // 起点清零：其余用例会在全局静态表里留下房间条目，与本用例守护的泄漏无关
+    unionMap("webSocketClientSet").clear();
+    unionMap("onlineUsers").clear();
+    unionMap("onlineRooms").clear();
+
+    for (int i = 0; i < 50; i++) {
+      Probe pa = new Probe();
+      Probe pb = new Probe();
+      // 背靠背发起两条连接：connectToServer 需要测试线程的请求上下文（CDI），
+      // 服务端 onOpen 异步派发到 executor，两次注册在服务端天然并发
+      Session sa = connect(c, pa, idA);
+      Session sb = connect(c, pb, idB);
+      awaitRegistered(sa, pa);
+      awaitRegistered(sb, pb);
+
+      // 建房 → 入房 → 退房 → 解散（帧序保证解散先于 close 帧被处理）
+      sa.getBasicRemote().sendText("{\"code\":12,\"data\":{\"name\":\"churn-" + i + "\"}}");
+      Map added = pollForCode(pa, 120, 5);
+      assertNotNull(added, "第 " + i + " 轮建房必须收到 ADD_ROOM_SUCCESS(120)");
+      Map room = JSONUtils.fromJson(added.get("data").toString(), Map.class);
+      String roomId = room.get("id").toString();
+      sb.getBasicRemote().sendText("{\"code\":13,\"data\":" + roomId + "}");
+      assertNotNull(pollForCode(pb, 130, 5), "第 " + i + " 轮入房必须收到 JOIN_ROOM_SUCCESS(130)");
+      sb.getBasicRemote().sendText("{\"code\":14,\"data\":" + roomId + "}"); // EXIT_ROOM
+      sa.getBasicRemote().sendText("{\"code\":15,\"data\":" + roomId + "}"); // REMOVE_ROOM
+      assertNotNull(pollForCode(pa, 11, 5), "第 " + i + " 轮解散后必须收到 ROOM_LIST(11) 广播");
+
+      // 并发关闭两条连接
+      CompletableFuture.allOf(
+          CompletableFuture.runAsync(() -> closeQuiet(sa)),
+          CompletableFuture.runAsync(() -> closeQuiet(sb))).join();
+    }
+
+    awaitEmpty("webSocketClientSet");
+    awaitEmpty("onlineUsers");
+    awaitEmpty("onlineRooms");
+  }
+
+  private static Session connect(WebSocketContainer c, Probe p, String id) {
+    try {
+      return c.connectToServer(p, URI.create("ws://localhost:18081/websocketUnion/" + id));
+    } catch (Exception e) {
+      throw new IllegalStateException(e);
+    }
+  }
+
+  private static void closeQuiet(Session s) {
+    try {
+      s.close();
+    } catch (Exception e) {
+      throw new IllegalStateException(e);
+    }
+  }
+
+  /** 反射读取 WebSocketUnionService 的全局静态表（字段私有，测试专用通道）。 */
+  private static Map<String, ?> unionMap(String field) throws Exception {
+    Field f = WebSocketUnionService.class.getDeclaredField(field);
+    f.setAccessible(true);
+    return (Map<String, ?>) f.get(null);
+  }
+
+  /** onClose 在服务端异步执行：时间窗内轮询直到该全局表清零。 */
+  private static void awaitEmpty(String field) throws Exception {
+    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
+    while (System.nanoTime() < deadline && !unionMap(field).isEmpty()) {
+      Thread.sleep(100);
+    }
+    assertEquals(0, unionMap(field).size(), field + " 必须在 50 次并发进出后清零");
+  }
+
   /** 轮询直到收到指定业务码的消息；超时返回 null。 */
   private static Map pollForCode(Probe probe, int code, long timeoutSec) throws InterruptedException {
     long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec);
     while (true) {
       long remain = deadline - System.nanoTime();
       if (remain <= 0) {
         return null;
       }

