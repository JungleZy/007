package com.nip.ws;

import com.nip.dao.UserDao;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
import com.nip.testsupport.Fixtures;

import com.nip.ws.model.SimulationSessionHolder;
import com.nip.ws.model.SimulationUserModel;
import com.nip.ws.service.simulation.SimulationGlobal;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nip.common.constants.SimulationDisturdTopicEnum.TOPIC_RESULT;
import static com.nip.common.constants.SimulationDisturdTopicEnum.TOPIC_SELECT;
import static com.nip.common.constants.SimulationRoomTypeEnum.DISTURB;
import static com.nip.common.constants.SimulationRoomTypeEnum.REPORT;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest

class WebSocketSimulationTest {

  @Inject
  UserDao userDao;
  @Inject
  SimulationRouterRoomDao roomDao;
  @Inject
  SimulationRouterRoomUserDao roomUserDao;
  @Inject
  WebSocketSimulationService service;
  @Inject com.nip.service.simulation.SimulationResultNotifier resultNotifier;

  @ClientEndpoint
  public static class Probe {
    final LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
    final LinkedBlockingQueue<String> closed = new LinkedBlockingQueue<>();

    @OnMessage
    public void on(String m) {
      received.add(m);
    }

    @OnClose
    public void onClose() {
      closed.add("closed");
    }
  }

  // P0#9：学员断线不得按“最后连接者”的身份处理。
  // 缺陷：onOpen 把身份写进共享单例字段（:80/:84），onClose 把 this 传给 quitRoomReport（:193/:195）。
  // 先连学员、后连教员 → this.userModel 停在教员身份 → 学员断线走教员分支：
  // 整房 playStatus 被置 0（暂停）并落库。
  @Test
  void studentDisconnectMustNotPauseRoomAsTeacher() throws Exception {
    String teacherId = Fixtures.user(userDao, "t-sim-teacher").getId();
    String studentId = Fixtures.user(userDao, "t-sim-student").getId();

    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
    room.setName("report-room");
    room.setCreateUserId(teacherId);
    room.setRoomType(REPORT.getType());
    room.setStats(1);
    room.setPlayStatus(1); // 播报中；学员断线不得把它改成 0（暂停）
    room = roomDao.save(room);
    Integer roomId = room.getId();

    saveRoomUser(roomId, teacherId, 0, 0); // 教员：userType=0（发报），channel=0
    saveRoomUser(roomId, studentId, 1, 1); // 学员：userType=1（收报），channel=1

    WebSocketContainer c = ContainerProvider.getWebSocketContainer();
    Probe studentP = new Probe();
    Probe teacherP = new Probe();
    // 连接顺序触发缺陷：先连学员、后连教员，共享字段停在教员身份。
    // onOpen 各自 get→new list→put 存在并发覆盖（P1-3，Phase 2 修），这里串行等待注册完成再连下一个。
    Session student = c.connectToServer(studentP, uri(studentId, roomId));
    awaitRoomSize(roomId, 1);
    try (Session teacher = c.connectToServer(teacherP, uri(teacherId, roomId))) {
      awaitRoomSize(roomId, 2);

      student.close(); // 学员断线
      awaitRemoved(roomId, studentId);

      SimulationRouterRoomEntity after = roomDao.findById(roomId);
      assertEquals(1, after.getPlayStatus().intValue(),
          "学员断线不得按教员身份暂停整房：playStatus 必须保持 1");
      List<SimulationSessionHolder> members = SimulationGlobal.reportRoom.get(roomId);
      assertNotNull(members, "教员仍在线，房间列表不得消失");
      assertTrue(members.stream().anyMatch(m -> teacherId.equals(m.userModel().getId())),
          "教员连接必须仍在房间列表");
      assertTrue(teacher.isOpen(), "教员连接必须仍然打开");
    } finally {
      if (student.isOpen()) {
        student.close();
      }
    }
  }

  @Test
  void staleSessionErrorAfterReconnectDoesNotRemoveReplacementOrPauseRoom() throws Exception {
    String studentId = Fixtures.user(userDao, "t-sim-reconnect-student").getId();
    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
    room.setName("report-room-reconnect");
    room.setCreateUserId(studentId);
    room.setRoomType(REPORT.getType());
    room.setStats(1);
    room.setPlayStatus(1);
    room = roomDao.save(room);
    Integer roomId = room.getId();
    saveRoomUser(roomId, studentId, 1, 1);

    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    Session oldClient = container.connectToServer(new Probe(), uri(studentId, roomId));
    Session oldServer = awaitServerSession(roomId, studentId, null);
    Session currentClient = container.connectToServer(new Probe(), uri(studentId, roomId));
    Session currentServer = awaitServerSession(roomId, studentId, oldServer);

    try {
      service.onError(studentId, roomId, oldServer, new RuntimeException("stale callback"));

      assertSame(currentServer, awaitServerSession(roomId, studentId, oldServer));
      assertEquals(1, roomDao.findById(roomId).getPlayStatus().intValue(),
          "stale student callback must not pause the room");
    } finally {
      if (oldClient.isOpen()) {
        oldClient.close();
      }
      if (currentClient.isOpen()) {
        currentClient.close();
      }
    }
  }

  @Test
  void reportRoomRejectsUserWithoutMembershipWithoutPausingRoom() throws Exception {
    String userId = Fixtures.user(userDao, "t-sim-unconfigured").getId();
    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
    room.setName("report-room-membership-required");
    room.setCreateUserId(userId);
    room.setRoomType(REPORT.getType());
    room.setStats(1);
    room.setPlayStatus(1);
    room = roomDao.save(room);
    Integer roomId = room.getId();

    Probe probe = new Probe();
    Session unauthorized = ContainerProvider.getWebSocketContainer()
        .connectToServer(probe, uri(userId, roomId));
    boolean enteredHolderList = awaitPresence(roomId, userId, unauthorized);
    String error = probe.received.poll(5, TimeUnit.SECONDS);
    boolean serverClosed = probe.closed.poll(5, TimeUnit.SECONDS) != null && !unauthorized.isOpen();

    if (unauthorized.isOpen()) {
      unauthorized.close();
    }
    awaitRemoved(roomId, userId);

    assertAll(
        () -> assertTrue(error != null && error.contains("\"code\":-1")
            && error.contains("人员或房间信息未找到"), "必须先发送现有结构化错误"),
        () -> assertTrue(serverClosed, "无房间成员配置的连接必须由服务端关闭"),
        () -> assertFalse(enteredHolderList, "未授权用户不得进入房间 holder 列表"),
        () -> assertEquals(1, roomDao.findById(roomId).getPlayStatus().intValue(),
            "拒接/断连不得暂停 REPORT 房间"));
  }

  @Test
  void disconnectWithNullRoleDoesNotPauseReportRoom() {
    String userId = Fixtures.user(userDao, "t-sim-null-role").getId();
    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
    room.setName("report-room-null-role");
    room.setCreateUserId(userId);
    room.setRoomType(REPORT.getType());
    room.setStats(1);
    room.setPlayStatus(1);
    room = roomDao.save(room);
    Integer roomId = room.getId();

    SimulationUserModel unknownRole = new SimulationUserModel();
    unknownRole.setId(userId);
    unknownRole.setChannel(-1);
    Session unknownSession = testSession("unknown-role");
    SimulationSessionHolder holder = new SimulationSessionHolder(unknownSession, unknownRole);
    SimulationGlobal.reportRoom.put(roomId,
        new CopyOnWriteArrayList<>(List.of(holder)));

    try {
      assertDoesNotThrow(() -> service.quitRoomReport(roomId, userId, unknownSession));
      assertEquals(1, roomDao.findById(roomId).getPlayStatus().intValue(),
          "null/unknown role 断连不得被当成教员暂停房间");
      List<SimulationSessionHolder> members = SimulationGlobal.reportRoom.get(roomId);
      assertTrue(members == null || members.stream()
          .noneMatch(member -> userId.equals(member.userModel().getId())),
          "未知角色 holder 必须安全移除");
    } finally {
      SimulationGlobal.reportRoom.remove(roomId);
    }
  }

  @Test
  void malformedMessageReturnsProtocolErrorAndKeepsParticipantConnected() throws Exception {
    String userId = Fixtures.user(userDao, "t-sim-malformed").getId();
    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
    room.setName("report-room-malformed");
    room.setCreateUserId(userId);
    room.setRoomType(REPORT.getType());
    room.setStats(1);
    room.setPlayStatus(1);
    room = roomDao.save(room);
    Integer roomId = room.getId();
    saveRoomUser(roomId, userId, 1, 1);

    Probe probe = new Probe();
    Session client = ContainerProvider.getWebSocketContainer().connectToServer(probe, uri(userId, roomId));
    try {
      assertTrue(awaitPresence(roomId, userId, client));
      client.getBasicRemote().sendText("{");
      String error = awaitMessageContaining(probe, "消息格式错误");
      assertNotNull(error, "坏消息必须返回结构化协议错误");
      assertTrue(error.contains("\"code\":-1"), "协议错误必须使用错误响应码: " + error);
      assertTrue(client.isOpen(), "单条坏消息不得清理正常参与者连接");
      assertEquals(1, roomDao.findById(roomId).getPlayStatus().intValue(),
          "坏消息不得触发房间状态变更");
    } finally {
      if (client.isOpen()) client.close();
    }
  }
  private static String awaitMessageContaining(Probe probe, String text) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      String message = probe.received.poll(100, TimeUnit.MILLISECONDS);
      if (message != null && message.contains(text)) return message;
    }
    return null;
  }


  private static URI uri(String userId, Integer roomId) {
    return URI.create("ws://localhost:18081/simulation/" + userId + "/" + roomId);
  }

  private void saveRoomUser(Integer roomId, String userId, int userType, int channel) {
    SimulationRouterRoomUserEntity e = new SimulationRouterRoomUserEntity();
    e.setRoomId(roomId);
    e.setUserId(userId);
    e.setUserType(userType);
    e.setChannel(channel);
    e.setUserStatus(0);
    roomUserDao.save(e);
  }

  private static Session testSession(String id) {
    return (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(),
        new Class<?>[]{Session.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getId" -> id;
          case "isOpen" -> true;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          default -> null;
        });
  }

  /** 服务端 onOpen 完成注册是异步的：轮询房间列表直到到达期望人数。 */
  private static void awaitRoomSize(Integer roomId, int size) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      List<SimulationSessionHolder> members = SimulationGlobal.reportRoom.get(roomId);
      if (members != null && members.size() >= size) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("10s 内房间列表未到达 " + size + " 人");
  }

  private static boolean awaitPresence(Integer roomId, String userId, Session session)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (System.nanoTime() < deadline) {
      List<SimulationSessionHolder> members = SimulationGlobal.reportRoom.get(roomId);
      if (members != null && members.stream().anyMatch(m -> userId.equals(m.userModel().getId()))) {
        return true;
      }
      if (!session.isOpen()) {
        return false;
      }
      Thread.sleep(50);
    }
    return false;
  }

  private static Session awaitServerSession(Integer roomId, String userId, Session previous)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      List<SimulationSessionHolder> members = SimulationGlobal.reportRoom.get(roomId);
      if (members != null) {
        Session current = members.stream()
            .filter(member -> userId.equals(member.userModel().getId()))
            .map(SimulationSessionHolder::session)
            .filter(session -> session != previous)
            .findFirst()
            .orElse(null);
        if (current != null) {
          return current;
        }
      }
      Thread.sleep(50);
    }
    throw new AssertionError("10s 内 replacement Session 未成为当前 holder");
  }

  /** 轮询直到该用户被移出房间列表（onClose 异步执行）。 */
  private static void awaitRemoved(Integer roomId, String userId) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      List<SimulationSessionHolder> members = SimulationGlobal.reportRoom.get(roomId);
      if (members == null || members.stream().noneMatch(m -> userId.equals(m.userModel().getId()))) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("学员断线后 10s 内未被移出房间列表");
  }

  // Task 6.5(b)：干扰房里既有在册成员，也有无 roomUser 行的合成成员（channel=-1、userType 为 null）。
  // 修复前 messageHandleDisturb 的 getUserType().compareTo(0) 在合成成员上 NPE，
  // 整条 select 广播被打断，排在后面的在册成员一个字也收不到。
  @Test
  void disturbRoomBroadcastReachesMembersDespiteSyntheticMember() {
    String senderId = Fixtures.user(userDao, UUID.randomUUID().toString()).getId();
    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
    room.setName("disturb-room-synthetic");
    room.setCreateUserId(senderId);
    room.setRoomType(DISTURB.getType());
    room.setStats(1);
    room = roomDao.save(room);
    Integer roomId = room.getId();
    saveRoomUser(roomId, senderId, 1, 1); // 在册参训人员：切换频道需要 roomUser 行

    // 合成成员排在最前：修复前它会先把整个循环 NPE 掉
    RecordingSession synthetic = recordingSession("synthetic");
    SimulationUserModel syntheticModel = new SimulationUserModel();
    syntheticModel.setId("synthetic-observer");
    syntheticModel.setChannel(-1); // openLocked 合成成员：userType 保持 null
    RecordingSession organizer = recordingSession("organizer");
    SimulationUserModel organizerModel = new SimulationUserModel();
    organizerModel.setId("organizer-user");
    organizerModel.setChannel(0);
    organizerModel.setUserType(0);
    SimulationGlobal.disturbRoom.put(roomId, new CopyOnWriteArrayList<>(List.of(
        new SimulationSessionHolder(synthetic.session(), syntheticModel),
        new SimulationSessionHolder(organizer.session(), organizerModel))));

    try {
      String message = "{\"topic\":\"" + TOPIC_SELECT.getType() + "\",\"body\":{\"road\":2}}";
      assertDoesNotThrow(() -> service.messageHandleDisturb(message, roomId, senderId),
          "合成成员不得让在册成员的消息处理抛异常");

      assertFalse(organizer.outbound().isEmpty(),
          "排在合成成员之后的在册成员必须收到 select 广播");
      // data 字段里嵌的是原始报文字符串（引号被转义），按裸词匹配
      assertTrue(organizer.outbound().stream()
              .anyMatch(m -> m.contains(TOPIC_SELECT.getType()) && m.contains("road")),
          "广播内容必须是发送者的原始 select 帧");
      assertEquals(2, roomUserDao.findByUserIdAndRoomId(senderId, roomId).getChannel().intValue(),
          "切换频道必须落库");
    } finally {
      SimulationGlobal.disturbRoom.remove(roomId);
    }
  }

  @Test
  void clientResultFrameCannotSubmitOrNotifyTeachers() {
    String senderId = Fixtures.user(userDao, UUID.randomUUID().toString()).getId();
    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
    room.setName("report-room-null-channel");
    room.setCreateUserId(senderId);
    room.setRoomType(REPORT.getType());
    room.setStats(1);
    room.setPlayStatus(1);
    room = roomDao.save(room);
    Integer roomId = room.getId();
    saveRoomUser(roomId, senderId, 1, 1);

    RecordingSession nullChannel = recordingSession("null-channel");
    SimulationUserModel nullChannelModel = new SimulationUserModel();
    nullChannelModel.setId("null-channel-user");
    nullChannelModel.setUserType(1); // channel 未配置
    RecordingSession teacher = recordingSession("teacher");
    SimulationUserModel teacherModel = new SimulationUserModel();
    teacherModel.setId("teacher-user");
    teacherModel.setChannel(0);
    teacherModel.setUserType(0);
    SimulationGlobal.reportRoom.put(roomId, new CopyOnWriteArrayList<>(List.of(
        new SimulationSessionHolder(nullChannel.session(), nullChannelModel),
        new SimulationSessionHolder(teacher.session(), teacherModel))));

    try {
      String message = "{\"type\":\"" + TOPIC_RESULT.getType() + "\"}";
      service.messageHandleReport(message, roomId, senderId);
      assertTrue(teacher.outbound().isEmpty(), "客户端结果帧不能冒充已提交通知");
      assertEquals(0, roomUserDao.findByUserIdAndRoomId(senderId, roomId).getUserStatus().intValue(),
          "只有成功的REST提交可以更新填报状态");
    } finally {
      SimulationGlobal.reportRoom.remove(roomId);
    }
  }

  @Test
  void committedResultReachesBothTeachersButRollbackDoesNotNotify() {
    String firstId = Fixtures.user(userDao, UUID.randomUUID().toString()).getId();
    String secondId = Fixtures.user(userDao, UUID.randomUUID().toString()).getId();
    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
    room.setName("committed-result");
    room.setCreateUserId(firstId);
    room.setRoomType(REPORT.getType());
    room.setStats(2);
    Integer roomId = roomDao.save(room).getId();
    saveRoomUser(roomId, firstId, 0, 0);
    saveRoomUser(roomId, secondId, 0, 0);
    RecordingSession first = recordingSession("result-first");
    RecordingSession second = recordingSession("result-second");
    SimulationUserModel firstModel = new SimulationUserModel();
    firstModel.setId(firstId);
    SimulationUserModel secondModel = new SimulationUserModel();
    secondModel.setId(secondId);
    SimulationGlobal.reportRoom.put(roomId, List.of(
        new SimulationSessionHolder(first.session(), firstModel),
        new SimulationSessionHolder(second.session(), secondModel)));
    try {
      assertThrows(IllegalStateException.class, () -> io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
        resultNotifier.publish(roomId, "answer-user", REPORT.getType());
        assertTrue(first.outbound().isEmpty());
        assertTrue(second.outbound().isEmpty());
        throw new IllegalStateException("rollback");
      }));
      assertTrue(first.outbound().isEmpty());
      assertTrue(second.outbound().isEmpty());
      io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
        resultNotifier.publish(roomId, "answer-user", REPORT.getType());
        assertTrue(first.outbound().isEmpty());
        assertTrue(second.outbound().isEmpty());
      });
      for (RecordingSession recipient : List.of(first, second)) {
        assertEquals(1, recipient.outbound().size());
        assertTrue(recipient.outbound().getFirst().contains("answer-user"));
        assertTrue(recipient.outbound().getFirst().contains("roomId"));
      }
    } finally {
      SimulationGlobal.reportRoom.remove(roomId);
    }
  }

  private record RecordingSession(Session session, List<String> outbound) {}

  /** 记录出站帧的 Session 桩：service 只用 isOpen()/getAsyncRemote()/close()。 */
  private static RecordingSession recordingSession(String id) {
    AtomicBoolean open = new AtomicBoolean(true);
    List<String> outbound = new CopyOnWriteArrayList<>();
    RemoteEndpoint.Async async = (RemoteEndpoint.Async) Proxy.newProxyInstance(
        RemoteEndpoint.Async.class.getClassLoader(),
        new Class<?>[]{RemoteEndpoint.Async.class},
        (proxy, method, args) -> {
          if ("sendText".equals(method.getName()) && args != null && args.length > 0) {
            outbound.add(String.valueOf(args[0]));
          }
          return null;
        });
    Session session = (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(),
        new Class<?>[]{Session.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getId" -> id;
          case "isOpen" -> open.get();
          case "getAsyncRemote" -> async;
          case "close" -> {
            open.set(false);
            yield null;
          }
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "Session[" + id + "]";
          default -> null;
        });
    return new RecordingSession(session, outbound);
  }
}
