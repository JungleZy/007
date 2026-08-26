## diff
commit 79b251f41379de7676eb780459ed067528b8f758
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 04:27:39 2026 +0800

    fix(ws-7): quitRoom* 原子判空移除防并发建房孤立

diff --git a/src/main/java/com/nip/ws/WebSocketSimulationService.java b/src/main/java/com/nip/ws/WebSocketSimulationService.java
index 74a7015..f65b052 100644
--- a/src/main/java/com/nip/ws/WebSocketSimulationService.java
+++ b/src/main/java/com/nip/ws/WebSocketSimulationService.java
@@ -242,19 +242,18 @@ public class WebSocketSimulationService {
       body.put(ID, userId);
       message.put(BaseConstants.TOPIC, BaseConstants.OFFLINE);
       message.put(BaseConstants.BODY, body);
       WebSocketSimulationService.sendMessage(item.getSession(), JSONObject.toJSONString(message), "", "");
     });
 
     //移除退出者会话；房间清空后释放房间条目（P1-4：原 removeIndex 逻辑从不移除，永久泄漏）
     simulations.removeIf(s -> Objects.equals(s.getUserModel().getId(), userId));
-    if (simulations.isEmpty()) {
-      SimulationGlobal.disturbRoom.remove(roomId);
-    }
+    //原子判空移除：与并发 onOpen 的 computeIfAbsent 互斥，避免 remove 孤立刚 add 进来的会话
+    SimulationGlobal.disturbRoom.computeIfPresent(roomId, (k, list) -> list.isEmpty() ? null : list);
   }
 
   @Transactional
   public void quitRoomReport(Integer roomId, String userId) {
     List<WebSocketSimulationService> simulations = SimulationGlobal.reportRoom.get(roomId);
     if (Objects.isNull(simulations)) {
       return;
     }
@@ -286,19 +285,18 @@ public class WebSocketSimulationService {
         if (simulation.getUserModel().getUserType().compareTo(1) == 0) {
           Map<String, Integer> jb = new HashMap<>();
           jb.put(TYPE, 2);
           WebSocketSimulationService.sendMessage(simulation.getSession(), JSONObject.toJSONString(jb), "", "");
         }
       }
     }
     simulations.remove(holder);
-    if (simulations.isEmpty()) {
-      SimulationGlobal.reportRoom.remove(roomId);
-    }
+    //原子判空移除：与并发 onOpen 的 computeIfAbsent 互斥，避免 remove 孤立刚 add 进来的会话
+    SimulationGlobal.reportRoom.computeIfPresent(roomId, (k, list) -> list.isEmpty() ? null : list);
   }
 
   public void quitRoomRouter(Integer roomId, String userId) {
     List<WebSocketSimulationService> simulations = SimulationGlobal.routerRoom.get(roomId);
     if (Objects.isNull(simulations)) {
       return;
     }
     Map<String, Object> msg = new HashMap<>();
@@ -310,19 +308,18 @@ public class WebSocketSimulationService {
     for (WebSocketSimulationService webSocketSimulationService : simulations) {
       if (webSocketSimulationService.getUserModel().getId().equals(userId)) {
         removeObj = webSocketSimulationService;
       } else {
         WebSocketSimulationService.sendMessage(webSocketSimulationService.getSession(), JSONObject.toJSONString(msg), "", "");
       }
     }
     simulations.remove(removeObj);
-    if (simulations.isEmpty()) {
-      SimulationGlobal.routerRoom.remove(roomId);
-    }
+    //原子判空移除：与并发 onOpen 的 computeIfAbsent 互斥，避免 remove 孤立刚 add 进来的会话
+    SimulationGlobal.routerRoom.computeIfPresent(roomId, (k, list) -> list.isEmpty() ? null : list);
   }
 
   /**
    * 消息处理
    *
    * @param
    * @param message 消息（JSON）
    */
