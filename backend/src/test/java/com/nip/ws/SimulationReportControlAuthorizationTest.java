package com.nip.ws;

import com.google.gson.JsonObject;
import com.nip.common.constants.SimulationRoomTypeEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.UserDao;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.WebSocketSessionProbe;
import com.nip.ws.model.SimulationSessionHolder;
import com.nip.ws.service.simulation.SimulationGlobal;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SimulationReportControlAuthorizationTest {
  @Inject UserDao users;
  @Inject SimulationRouterRoomDao rooms;
  @Inject SimulationRouterRoomUserDao members;
  @Inject WebSocketSimulationService endpoint;

  @ParameterizedTest
  @EnumSource(value = SimulationRoomTypeEnum.class, names = {"REPORT", "RECEPT"})
  void receivingStudentCannotControlRoomButSendingTeacherCan(SimulationRoomTypeEnum roomType) throws Exception {
    UserEntity creator = user();
    UserEntity teacher = user();
    UserEntity student = user();
    Integer roomId = room(creator, roomType, 0, 0);
    member(roomId, teacher, 0);
    member(roomId, student, 1);
    var teacherProbe = new WebSocketSimulationTest.Probe();
    var studentProbe = new WebSocketSimulationTest.Probe();
    var container = ContainerProvider.getWebSocketContainer();

    // Both URL identities are forged; credentials must still determine each actor.
    try (Session studentSession = container.connectToServer(studentProbe, uri(student, teacher.getId(), roomId))) {
      awaitConnections(roomId, 1);
      try (Session teacherSession = container.connectToServer(teacherProbe, uri(teacher, student.getId(), roomId))) {
        awaitConnections(roomId, 2);
        studentSession.getBasicRemote().sendText("{\"type\":\"online\",\"id\":\"" + teacher.getId() + "\"}");
        JsonObject ready = payload(next(teacherProbe));
        assertEquals("online", ready.get("type").getAsString());
        assertEquals(student.getId(), ready.get("id").getAsString());

        rejectControl(studentSession, studentProbe, teacherProbe, teacher, roomId, "1", new State(0, 0, 0));
        control(teacherSession, studentProbe, roomId, 1, new State(1, 1, 0));
        rejectControl(studentSession, studentProbe, teacherProbe, teacher, roomId, "2", new State(1, 1, 0));
        control(teacherSession, studentProbe, roomId, 2, new State(1, 0, 0));
        rejectControl(studentSession, studentProbe, teacherProbe, teacher, roomId, "3", new State(1, 0, 0));
        control(teacherSession, studentProbe, roomId, 3, new State(1, 1, 0));
        rejectControl(studentSession, studentProbe, teacherProbe, teacher, roomId, "4", new State(1, 1, 0));
        for (String malformedType : List.of("04", "4.0", " 4", "unknown")) {
          rejectControl(studentSession, studentProbe, teacherProbe, teacher, roomId,
              JSONUtils.toJson(malformedType), new State(1, 1, 0));
        }
        control(teacherSession, studentProbe, roomId, 4, new State(2, 1, 42));
        assertTrue(teacherSession.isOpen());
        assertTrue(studentSession.isOpen());
      }
    }
  }

  @Test
  void currentDatabaseRoleAndMembershipOverrideConnectedRoleSnapshot() throws Exception {
    UserEntity creator = user();
    UserEntity teacher = user();
    Integer roomId = room(creator, SimulationRoomTypeEnum.REPORT, 1, 1);
    member(roomId, creator, 1);
    member(roomId, teacher, 0);
    WebSocketSessionProbe creatorProbe = probe(creator);
    WebSocketSessionProbe teacherProbe = probe(teacher);
    endpoint.onOpen(creatorProbe.session(), roomId.toString());
    endpoint.onOpen(teacherProbe.session(), roomId.toString());
    try {
      creatorProbe.outbound().clear();
      teacherProbe.outbound().clear();
      endpoint.onMessage(roomId.toString(), "{\"type\":2}", teacherProbe.session());
      assertEquals(new State(1, 0, 0), state(roomId));
      assertEquals("2", payload(creatorProbe.outbound().getLast()).get("type").getAsString());
      creatorProbe.outbound().clear();

      QuarkusTransaction.requiringNew().run(() ->
          members.findByUserIdAndRoomId(teacher.getId(), roomId).setUserType(1));
      endpoint.onMessage(roomId.toString(), "{\"type\":3}", teacherProbe.session());
      assertEquals(-1, envelope(teacherProbe.outbound().getLast()).get("code").getAsInt());
      assertTrue(creatorProbe.outbound().isEmpty(), "Demoted teacher must not broadcast a resume");
      assertEquals(new State(1, 0, 0), state(roomId));
      teacherProbe.outbound().clear();

      // The creator remains a controller even when configured in a receiving seat.
      endpoint.onMessage(roomId.toString(), "{\"type\":3}", creatorProbe.session());
      assertEquals(new State(1, 1, 0), state(roomId));
      assertEquals("3", payload(teacherProbe.outbound().getLast()).get("type").getAsString());
      teacherProbe.outbound().clear();

      QuarkusTransaction.requiringNew().run(() -> members.remove(roomId, creator.getId()));
      endpoint.onMessage(roomId.toString(), "{\"type\":4,\"count\":999}", creatorProbe.session());
      assertEquals(-1, envelope(creatorProbe.outbound().getLast()).get("code").getAsInt());
      assertEquals(new State(1, 1, 0), state(roomId));
      assertTrue(teacherProbe.outbound().isEmpty(), "Removed member must not broadcast a finish");
      assertTrue(creatorProbe.session().isOpen());
    } finally {
      endpoint.onClose(roomId.toString(), creatorProbe.session());
      endpoint.onClose(roomId.toString(), teacherProbe.session());
      creatorProbe.session().close();
      teacherProbe.session().close();
    }
  }

  private void rejectControl(Session student, WebSocketSimulationTest.Probe studentProbe,
      WebSocketSimulationTest.Probe teacherProbe, UserEntity teacher, Integer roomId,
      String typeJson, State expected) throws Exception {
    List<Integer> statuses = SimulationGlobal.reportRoom.get(roomId).stream()
        .map(holder -> holder.userModel().getStatus()).toList();
    student.getBasicRemote().sendText("{\"type\":" + typeJson + ",\"count\":999,\"id\":\""
        + teacher.getId() + "\",\"userId\":\"" + teacher.getId() + "\"}");
    assertEquals(-1, envelope(next(studentProbe)).get("code").getAsInt(), "Student control must be denied");
    assertNull(teacherProbe.received.poll(200, TimeUnit.MILLISECONDS), "Denied control must not reach the teacher");
    assertEquals(expected, state(roomId), "Denied control must preserve persisted lifecycle and duration");
    assertEquals(statuses, SimulationGlobal.reportRoom.get(roomId).stream()
        .map(holder -> holder.userModel().getStatus()).toList(), "Denied control must preserve readiness");
    assertTrue(student.isOpen());
  }

  private void control(Session teacher, WebSocketSimulationTest.Probe studentProbe,
      Integer roomId, int type, State expected) throws Exception {
    teacher.getBasicRemote().sendText("{\"type\":" + type + ",\"count\":42}");
    assertEquals(Integer.toString(type), payload(next(studentProbe)).get("type").getAsString());
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    State actual = state(roomId);
    while (!expected.equals(actual) && System.nanoTime() < deadline) {
      Thread.sleep(20);
      actual = state(roomId);
    }
    assertEquals(expected, actual);
  }

  private record State(Integer stats, Integer playStatus, Integer totalTime) {}

  private State state(Integer roomId) {
    return QuarkusTransaction.requiringNew().call(() -> {
      SimulationRouterRoomEntity room = rooms.findById(roomId);
      return new State(room.getStats(), room.getPlayStatus(), room.getTotalTime());
    });
  }

  private static String next(WebSocketSimulationTest.Probe probe) throws InterruptedException {
    String frame = probe.received.poll(5, TimeUnit.SECONDS);
    assertNotNull(frame, "Expected a WebSocket protocol frame");
    return frame;
  }

  private static JsonObject envelope(String frame) {
    return JSONUtils.fromJson(frame, JsonObject.class);
  }

  private static JsonObject payload(String frame) {
    JsonObject envelope = envelope(frame);
    assertEquals(1, envelope.get("code").getAsInt());
    return JSONUtils.fromJson(envelope.get("data").getAsString(), JsonObject.class);
  }

  private static void awaitConnections(Integer roomId, int size) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      List<SimulationSessionHolder> connected = SimulationGlobal.reportRoom.get(roomId);
      if (connected != null && connected.size() == size) return;
      Thread.sleep(20);
    }
    fail("WebSocket connections were not registered for room " + roomId);
  }

  private UserEntity user() {
    return Fixtures.user(users, UUID.randomUUID().toString(), UUID.randomUUID().toString());
  }

  private Integer room(UserEntity creator, SimulationRoomTypeEnum type, int stats, int playStatus) {
    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
    room.setName("report-control-" + UUID.randomUUID());
    room.setCreateUserId(creator.getId());
    room.setRoomType(type.getType());
    room.setStats(stats);
    room.setPlayStatus(playStatus);
    room.setTotalTime(0);
    return rooms.save(room).getId();
  }

  private void member(Integer roomId, UserEntity user, int userType) {
    SimulationRouterRoomUserEntity member = new SimulationRouterRoomUserEntity();
    member.setRoomId(roomId);
    member.setUserId(user.getId());
    member.setUserType(userType);
    member.setChannel(userType);
    member.setUserStatus(0);
    members.save(member);
  }

  private static WebSocketSessionProbe probe(UserEntity user) {
    return WebSocketSessionProbe.open(UUID.randomUUID().toString(), user.getToken(), user.getDeviceId());
  }

  private static URI uri(UserEntity user, String pathUserId, Integer roomId) {
    return URI.create("ws://localhost:18081/simulation/" + pathUserId + "/" + roomId
        + "?token=" + user.getToken() + "&deviceId=" + user.getDeviceId());
  }
}
