package com.nip.ws;

import com.nip.dao.UserDao;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.WebSocketStateReset;
import com.nip.ws.model.SimulationSessionHolder;
import com.nip.ws.service.simulation.SimulationGlobal;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.nip.common.constants.SimulationRoomTypeEnum.ROUTER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebSocket 握手鉴权（SEC-06）的对外可观测契约。
 *
 * <p>守四件事：
 * <ol>
 *   <li>不带 {@code token}/{@code deviceId} 的连接被服务端关闭；</li>
 *   <li>路径参数只作路由——A 的凭据连 B 的路径时按 A 注册，B 的在线状态与定向推送不受影响；</li>
 *   <li>路由房建房人在 {@code t_simulation_router_room_user} **没有成员行**，仍须能连入并向全房广播
 *       （合成成员保持 {@code userType=null}/{@code channel=-1}，组训人员的识别口径不变）；</li>
 *   <li>既没有成员行、又不是建房人的连接被拒。</li>
 * </ol>
 *
 * <p>全部走真实客户端连接：断言对象是客户端实际看到的关闭与帧，而不是内部调用。
 */
@QuarkusTest
class WebSocketHandshakeAuthorizationTest {

  private static final String HOST = "ws://localhost:18081";

  @Inject
  UserDao userDao;
  @Inject
  SimulationRouterRoomDao roomDao;
  @Inject
  SimulationRouterRoomUserDao roomUserDao;

  @ClientEndpoint
  public static class Probe {
    final LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();

    @OnMessage
    public void on(String m) {
      received.add(m);
    }
  }

  @AfterEach
  void clearRooms() {
    WebSocketStateReset.clearAll();
  }

  @Test
  void connectionWithoutCredentialsIsClosed() throws Exception {
    TestUser target = user("t-ws-anonymous");
    WebSocketContainer container = ContainerProvider.getWebSocketContainer();

    Session anonymous = container.connectToServer(new Probe(),
        URI.create(HOST + "/websocket/" + target.id()));
    try {
      assertTrue(awaitClosed(anonymous), "不带 token/deviceId 的连接必须由服务端关闭");
    } finally {
      if (anonymous.isOpen()) {
        anonymous.close();
      }
    }
  }

  @Test
  void credentialsOverridePathSoImpersonationRegistersAsTheRealUser() throws Exception {
    TestUser attacker = user("t-ws-impersonator");
    TestUser victim = user("t-ws-impersonated");
    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    Probe victimProbe = new Probe();
    Probe attackerProbe = new Probe();

    Session victimSession = container.connectToServer(victimProbe,
        URI.create(HOST + "/websocket/" + victim.id() + credentials(victim)));
    // A 的凭据 + B 的路径参数
    Session forged = container.connectToServer(attackerProbe,
        URI.create(HOST + "/websocket/" + victim.id() + credentials(attacker)));
    try {
      // 定向推送只能命中在线表里的键：收到即证明这条连接是按 A（凭据主人）注册的
      assertTrue(awaitDirectedFrame(attackerProbe, attacker.id(), "attacker-online"),
          "伪造路径的连接必须按凭据主人注册，否则 A 的定向推送到不了它");

      victimProbe.received.clear();
      attackerProbe.received.clear();
      WebSocketService.sendInfo(victim.id(), "victim-only-frame");

      assertEquals("victim-only-frame", victimProbe.received.poll(5, TimeUnit.SECONDS),
          "B 的在线状态不得被伪造连接顶掉：B 必须仍能收到自己的定向推送");
      assertNull(attackerProbe.received.poll(2, TimeUnit.SECONDS),
          "A 不得收到发给 B 的定向推送");
    } finally {
      closeQuiet(forged);
      closeQuiet(victimSession);
    }
  }

  @Test
  void routerRoomCreatorWithoutMemberRowStillJoinsAndBroadcasts() throws Exception {
    TestUser creator = user("t-ws-router-creator");
    TestUser participant = user("t-ws-router-participant");
    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
    room.setName("router-room-handshake");
    room.setCreateUserId(creator.id());
    room.setRoomType(ROUTER.getType());
    room.setStats(1);
    Integer roomId = roomDao.save(room).getId();
    // 建房人刻意不建成员行：SimulationRouterRoomService 只为 send/receive 列表建行
    saveRoomUser(roomId, participant.id(), 1, 3);

    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    Probe creatorProbe = new Probe();
    Probe participantProbe = new Probe();
    Session creatorSession = container.connectToServer(creatorProbe,
        URI.create(HOST + "/simulation/" + creator.id() + "/" + roomId + credentials(creator)));
    try {
      SimulationSessionHolder synthetic = awaitHolder(roomId, creator.id());
      assertNotNull(synthetic, "路由房建房人（无成员行）必须能连入房间");
      assertNull(synthetic.userModel().getUserType(),
          "合成成员必须保持 userType=null：messageHandleRouter 正是以它识别组训人员");
      assertEquals(-1, synthetic.userModel().getChannel().intValue(),
          "合成成员必须保持 channel=-1，不落在任何真实频道上");

      try (Session participantSession = container.connectToServer(participantProbe,
          URI.create(HOST + "/simulation/" + participant.id() + "/" + roomId
              + credentials(participant)))) {
        assertNotNull(awaitHolder(roomId, participant.id()), "在册参训人员必须能连入房间");
        // 排掉入房阶段的 online 广播
        participantProbe.received.clear();

        creatorSession.getBasicRemote()
            .sendText("{\"topic\":\"play\",\"body\":\"creator-beacon\"}");

        assertNotNull(awaitContaining(participantProbe, "creator-beacon"),
            "组训人员（userType=null）的 play 帧必须广播到全房");
      }
    } finally {
      closeQuiet(creatorSession);
    }
  }

  @Test
  void routerRoomOutsiderWithoutMemberRowIsRejected() throws Exception {
    TestUser creator = user("t-ws-router-owner");
    TestUser outsider = user("t-ws-router-outsider");
    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
    room.setName("router-room-outsider");
    room.setCreateUserId(creator.id());
    room.setRoomType(ROUTER.getType());
    room.setStats(1);
    Integer roomId = roomDao.save(room).getId();

    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    Probe probe = new Probe();
    Session rejected = container.connectToServer(probe,
        URI.create(HOST + "/simulation/" + outsider.id() + "/" + roomId + credentials(outsider)));
    try {
      assertNotNull(awaitContaining(probe, "人员或房间信息未找到"),
          "无成员行且非建房人必须先收到既有结构化错误帧");
      assertTrue(awaitClosed(rejected), "无成员行且非建房人的连接必须由服务端关闭");
      assertFalse(hasHolder(roomId, outsider.id()), "被拒的连接不得进入房间 holder 列表");
    } finally {
      closeQuiet(rejected);
    }
  }

  /** 一个可用于握手的夹具用户：token/deviceId 随 query 送出，服务端据此认人。 */
  private record TestUser(String id, String token, String deviceId) {}

  private TestUser user(String label) {
    String token = label + "-" + UUID.randomUUID();
    String deviceId = "device-" + UUID.randomUUID();
    UserEntity entity = Fixtures.user(userDao, token, deviceId);
    return new TestUser(entity.getId(), entity.getToken(), deviceId);
  }

  private static String credentials(TestUser user) {
    return "?token=" + user.token() + "&deviceId=" + user.deviceId();
  }

  private void saveRoomUser(Integer roomId, String userId, int userType, int channel) {
    SimulationRouterRoomUserEntity member = new SimulationRouterRoomUserEntity();
    member.setRoomId(roomId);
    member.setUserId(userId);
    member.setUserType(userType);
    member.setChannel(channel);
    member.setUserStatus(0);
    roomUserDao.save(member);
  }

  /** 服务端 onOpen/关闭都是异步派发：以下几个 await 都在时间窗内轮询。 */
  private static boolean awaitClosed(Session session) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      if (!session.isOpen()) {
        return true;
      }
      Thread.sleep(50);
    }
    return false;
  }

  private static boolean awaitDirectedFrame(Probe probe, String userId, String frame)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      WebSocketService.sendInfo(userId, frame);
      if (probe.received.poll(200, TimeUnit.MILLISECONDS) != null) {
        return true;
      }
    }
    return false;
  }

  private static String awaitContaining(Probe probe, String text) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      String message = probe.received.poll(100, TimeUnit.MILLISECONDS);
      if (message != null && message.contains(text)) {
        return message;
      }
    }
    return null;
  }

  private static SimulationSessionHolder awaitHolder(Integer roomId, String userId)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      SimulationSessionHolder holder = holder(roomId, userId);
      if (holder != null) {
        return holder;
      }
      Thread.sleep(50);
    }
    return null;
  }

  private static boolean hasHolder(Integer roomId, String userId) {
    return holder(roomId, userId) != null;
  }

  private static SimulationSessionHolder holder(Integer roomId, String userId) {
    List<SimulationSessionHolder> members = SimulationGlobal.routerRoom.get(roomId);
    if (members == null) {
      return null;
    }
    return members.stream()
        .filter(member -> userId.equals(member.userModel().getId()))
        .findFirst()
        .orElse(null);
  }

  private static void closeQuiet(Session session) {
    try {
      if (session.isOpen()) {
        session.close();
      }
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }
}
