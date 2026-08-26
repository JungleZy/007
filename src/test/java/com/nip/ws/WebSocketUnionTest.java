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

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
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
