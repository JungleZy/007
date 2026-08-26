package com.nip.ws;

import com.nip.common.utils.JSONUtils;
import com.nip.dao.UserDao;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class WebSocketUnionTest {

  @Inject
  UserDao userDao;

  @ClientEndpoint
  public static class Probe {
    final LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();

    @OnMessage
    public void on(String m) {
      received.add(m);
    }
  }

  // 用例A：广播可达性——共享 session 缺陷下先连者收不到任何广播
  @Test
  void firstClientStillReceivesBroadcastAfterSecondJoins() throws Exception {
    String id1 = Fixtures.user(userDao, "t-ws-1").getId();
    String id2 = Fixtures.user(userDao, "t-ws-2").getId();
    WebSocketContainer c = ContainerProvider.getWebSocketContainer();
    Probe p1 = new Probe();
    Probe p2 = new Probe();
    try (Session s1 = c.connectToServer(p1, URI.create("ws://localhost:18081/websocketUnion/" + id1))) {
      p1.received.clear(); // 排掉自己 join 产生的消息
      try (Session s2 = c.connectToServer(p2, URI.create("ws://localhost:18081/websocketUnion/" + id2))) {
        String got = p1.received.poll(5, TimeUnit.SECONDS);
        assertNotNull(got, "u2 加入后 u1 必须收到广播（缺陷下所有发送都走最后连接者的 session，u1 收不到）");
      }
    }
  }

  // 用例C：同 sid 重连——旧连接真正关闭时不得驱逐新连接。
  // 缺陷：resolveClient 仅按 sid 查 map，旧 session 的 onClose 会命中重连后的新 Client，
  // userExit 把存活的新连接从 map 清掉并广播 USER_EXIT，新连接沦为僵尸。
  // 服务端 onOpen 在 executor 上异步完成，须先用 GET_UNION_INFO 探测各连接注册完成，
  // 再关旧连接；随后 watcher 在时间窗内轮询 USER_LIST(10)，断言 id1 恒在。
  @Test
  void reconnectWithSameSidDoesNotEvictNewConnection() throws Exception {
    String id1 = Fixtures.user(userDao, "t-ws-c1").getId();
    String id2 = Fixtures.user(userDao, "t-ws-c2").getId();
    String idW = Fixtures.user(userDao, "t-ws-cw").getId();
    WebSocketContainer c = ContainerProvider.getWebSocketContainer();
    Probe oldP = new Probe();
    Probe newP = new Probe();
    Probe p2 = new Probe();
    Probe watcherP = new Probe();
    Session oldS = c.connectToServer(oldP, URI.create("ws://localhost:18081/websocketUnion/" + id1));
    awaitRegistered(oldS, oldP);
    try (Session newS = c.connectToServer(newP, URI.create("ws://localhost:18081/websocketUnion/" + id1));
         Session watcher = c.connectToServer(watcherP, URI.create("ws://localhost:18081/websocketUnion/" + idW))) {
      awaitRegistered(newS, newP);
      awaitRegistered(watcher, watcherP);
      oldS.close(); // 旧连接真正关闭，触发 onClose(旧session)
      // 时间窗内轮询在线用户列表：id1（新连接）必须始终在线
      boolean sawList = false;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
      while (System.nanoTime() < deadline) {
        watcherP.received.clear();
        watcher.getBasicRemote().sendText("{\"code\":0}");
        Map userList = pollForCode(watcherP, 10, 2); // USER_LIST
        if (userList != null) {
          sawList = true;
          assertTrue(userList.get("data").toString().contains(id1),
              "旧连接关闭不得驱逐同 sid 的新连接：id1 必须仍在在线用户列表");
        }
        Thread.sleep(300);
      }
      assertTrue(sawList, "监控窗内 watcher 必须至少收到一次 USER_LIST(10)");
      newP.received.clear();
      try (Session s2 = c.connectToServer(p2, URI.create("ws://localhost:18081/websocketUnion/" + id2))) {
        String got = newP.received.poll(5, TimeUnit.SECONDS);
        assertNotNull(got, "旧连接关闭不得驱逐同 sid 的新连接：u2 加入时新连接必须仍收到广播");
      }
    } finally {
      if (oldS.isOpen()) {
        oldS.close();
      }
    }
  }

  /** 等待连接在服务端注册完成：反复发 GET_UNION_INFO 直到收到 USER_LIST（未注册时服务端丢弃消息）。 */
  private static void awaitRegistered(Session s, Probe p) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      s.getBasicRemote().sendText("{\"code\":0}");
      if (pollForCode(p, 10, 1) != null) {
        return;
      }
    }
    throw new AssertionError("连接在 10s 内未完成服务端注册");
  }

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
    WebSocketContainer c = ContainerProvider.getWebSocketContainer();
    Probe p1 = new Probe();
    Probe p2 = new Probe();
    Probe p3 = new Probe();
    try (Session s1 = c.connectToServer(p1, URI.create("ws://localhost:18081/websocketUnion/" + id1));
         Session s2 = c.connectToServer(p2, URI.create("ws://localhost:18081/websocketUnion/" + id2));
         Session s3 = c.connectToServer(p3, URI.create("ws://localhost:18081/websocketUnion/" + id3))) {

      // onOpen 异步派发：注册完成前服务端会丢弃消息，先等三条连接全部注册
      awaitRegistered(s1, p1);
      awaitRegistered(s2, p2);
      awaitRegistered(s3, p3);

      // u1 建房
      s1.getBasicRemote().sendText("{\"code\":12,\"data\":{\"name\":\"room-b\"}}");
      Map added = pollForCode(p1, 120, 5); // ADD_ROOM_SUCCESS
      assertNotNull(added, "u1 建房后必须收到 ADD_ROOM_SUCCESS(120)");
      Map room = JSONUtils.fromJson(added.get("data").toString(), Map.class);
      String roomId = room.get("id").toString();

      // u2 入房（房间id 是 Snowflake 数字，按数字字面量发送）
      s2.getBasicRemote().sendText("{\"code\":13,\"data\":" + roomId + "}");
      assertNotNull(pollForCode(p2, 130, 5), "u2 入房后必须收到 JOIN_ROOM_SUCCESS(130)");

      // 排掉建房/入房阶段的广播噪音
      Thread.sleep(500);
      p2.received.clear();
      p3.received.clear();

      // u1 发房间消息
      s1.getBasicRemote().sendText("{\"code\":20,\"sendUser\":\"" + id1
          + "\",\"receiveUser\":\"" + roomId + "\",\"data\":\"hello\"}");

      Map got = pollForCode(p2, 20, 5); // ROOM_MESSAGE
      assertNotNull(got, "房间成员 u2 必须收到 ROOM_MESSAGE(20)");
      assertEquals(id1, got.get("sendUser"), "消息发送者必须是 u1");
      assertTrue(got.get("data").toString().contains("hello"), "消息内容必须可达");

      assertNull(pollForCode(p3, 20, 2), "房间外的 u3 不得收到 ROOM_MESSAGE");
    }
  }

  // 用例D（Task 2.6）：50 次并发进出房间循环后，连接表与房间表必须清零。
  // 守护对象：webSocketClientSet 条件移除、userExit 全表清理、removeRoom 解散——任何一处泄漏
  // （P1-4/P1-5 同类缺陷在 Union 家族的表现）都会让全局静态表残留条目。
  @Test
  void concurrentChurnLeavesNoResidualState() throws Exception {
    String idA = Fixtures.user(userDao, "t-ws-d1").getId();
    String idB = Fixtures.user(userDao, "t-ws-d2").getId();
    WebSocketContainer c = ContainerProvider.getWebSocketContainer();
    // 起点清零：其余用例会在全局静态表里留下房间条目，与本用例守护的泄漏无关
    unionMap("webSocketClientSet").clear();
    unionMap("onlineUsers").clear();
    unionMap("onlineRooms").clear();

    for (int i = 0; i < 50; i++) {
      Probe pa = new Probe();
      Probe pb = new Probe();
      // 背靠背发起两条连接：connectToServer 需要测试线程的请求上下文（CDI），
      // 服务端 onOpen 异步派发到 executor，两次注册在服务端天然并发
      Session sa = connect(c, pa, idA);
      Session sb = connect(c, pb, idB);
      awaitRegistered(sa, pa);
      awaitRegistered(sb, pb);

      // 建房 → 入房 → 退房 → 解散（帧序保证解散先于 close 帧被处理）
      sa.getBasicRemote().sendText("{\"code\":12,\"data\":{\"name\":\"churn-" + i + "\"}}");
      Map added = pollForCode(pa, 120, 5);
      assertNotNull(added, "第 " + i + " 轮建房必须收到 ADD_ROOM_SUCCESS(120)");
      Map room = JSONUtils.fromJson(added.get("data").toString(), Map.class);
      String roomId = room.get("id").toString();
      sb.getBasicRemote().sendText("{\"code\":13,\"data\":" + roomId + "}");
      assertNotNull(pollForCode(pb, 130, 5), "第 " + i + " 轮入房必须收到 JOIN_ROOM_SUCCESS(130)");
      sb.getBasicRemote().sendText("{\"code\":14,\"data\":" + roomId + "}"); // EXIT_ROOM
      sa.getBasicRemote().sendText("{\"code\":15,\"data\":" + roomId + "}"); // REMOVE_ROOM
      assertNotNull(pollForCode(pa, 11, 5), "第 " + i + " 轮解散后必须收到 ROOM_LIST(11) 广播");

      // 并发关闭两条连接
      CompletableFuture.allOf(
          CompletableFuture.runAsync(() -> closeQuiet(sa)),
          CompletableFuture.runAsync(() -> closeQuiet(sb))).join();
    }

    awaitEmpty("webSocketClientSet");
    awaitEmpty("onlineUsers");
    awaitEmpty("onlineRooms");
  }

  private static Session connect(WebSocketContainer c, Probe p, String id) {
    try {
      return c.connectToServer(p, URI.create("ws://localhost:18081/websocketUnion/" + id));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void closeQuiet(Session s) {
    try {
      s.close();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** 反射读取 WebSocketUnionService 的全局静态表（字段私有，测试专用通道）。 */
  private static Map<String, ?> unionMap(String field) throws Exception {
    Field f = WebSocketUnionService.class.getDeclaredField(field);
    f.setAccessible(true);
    return (Map<String, ?>) f.get(null);
  }

  /** onClose 在服务端异步执行：时间窗内轮询直到该全局表清零。 */
  private static void awaitEmpty(String field) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline && !unionMap(field).isEmpty()) {
      Thread.sleep(100);
    }
    assertEquals(0, unionMap(field).size(), field + " 必须在 50 次并发进出后清零");
  }

  /** 轮询直到收到指定业务码的消息；超时返回 null。 */
  private static Map pollForCode(Probe probe, int code, long timeoutSec) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec);
    while (true) {
      long remain = deadline - System.nanoTime();
      if (remain <= 0) {
        return null;
      }
      String m = probe.received.poll(remain, TimeUnit.NANOSECONDS);
      if (m == null) {
        return null;
      }
      Map map = JSONUtils.fromJson(m, Map.class);
      if (new BigDecimal(map.get("code").toString()).intValue() == code) {
        return map;
      }
    }
  }
}
