package com.nip.ws;

import com.google.gson.JsonObject;
import com.nip.common.utils.JSONUtils;
import com.nip.dto.general.GeneralPatTrainRoomUserDto;
import com.nip.dto.general.GeneralPatTrainUserModelDto;
import com.nip.testsupport.WebSocketSessionProbe;
import com.nip.testsupport.WebSocketStateReset;
import com.nip.ws.model.GeneralTickerPatTrainRoomUserModel;
import com.nip.ws.model.GeneralTickerPatTrainUserModel;
import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketGeneralSessionLifecycleTest {

  @AfterEach
  void clearRooms() {
    WebSocketStateReset.clearAll();
  }

  @Test
  void keyStaleCloseDoesNotRemoveReplacement() {
    Session oldSession = session("key-old", "user");
    Session currentSession = session("key-current", "user");
    GeneralPatTrainUserModelDto current = generalUser("user", currentSession);
    GeneralPatTrainRoomUserDto room = generalRoom(current);
    WebSocketGeneralKeyPatService.ROOM.put(101, room);

    new WebSocketGeneralKeyPatService().onClose("101", oldSession);

    GeneralPatTrainRoomUserDto actual = WebSocketGeneralKeyPatService.ROOM.get(101);
    assertNotNull(actual, "stale close must not remove the room containing the replacement");
    assertSame(current, actual.getJoinUser().getFirst());
  }

  @Test
  void keyCurrentErrorThenCloseRemovesEmptyRoomIdempotently() {
    Session currentSession = session("key-current", "user");
    WebSocketGeneralKeyPatService.ROOM.put(102, generalRoom(generalUser("user", currentSession)));
    WebSocketGeneralKeyPatService endpoint = new WebSocketGeneralKeyPatService();

    endpoint.onError("102", currentSession, new RuntimeException("expected"));
    assertDoesNotThrow(() -> endpoint.onClose("102", currentSession));

    assertFalse(WebSocketGeneralKeyPatService.ROOM.containsKey(102));
  }

  @Test
  void telexStaleCloseDoesNotRemoveReplacement() {
    Session oldSession = session("telex-old", "user");
    Session currentSession = session("telex-current", "user");
    GeneralPatTrainUserModelDto current = generalUser("user", currentSession);
    GeneralPatTrainRoomUserDto room = generalRoom(current);
    WebSocketGeneralTelexPatService.ROOM.put("train-101", room);

    new WebSocketGeneralTelexPatService().onClose("train-101", oldSession);

    GeneralPatTrainRoomUserDto actual = WebSocketGeneralTelexPatService.ROOM.get("train-101");
    assertNotNull(actual, "stale close must not remove the room containing the replacement");
    assertSame(current, actual.getJoinUser().getFirst());
  }

  @Test
  void telexCurrentErrorThenCloseRemovesEmptyRoomIdempotently() {
    Session currentSession = session("telex-current", "user");
    WebSocketGeneralTelexPatService.ROOM.put("train-102", generalRoom(generalUser("user", currentSession)));
    WebSocketGeneralTelexPatService endpoint = new WebSocketGeneralTelexPatService();

    endpoint.onError("train-102", currentSession, new RuntimeException("expected"));
    assertDoesNotThrow(() -> endpoint.onClose("train-102", currentSession));

    assertFalse(WebSocketGeneralTelexPatService.ROOM.containsKey("train-102"));
  }

  @Test
  void tickerStaleErrorDoesNotRemoveReplacement() {
    Session oldSession = session("ticker-old", "user");
    Session currentSession = session("ticker-current", "user");
    GeneralTickerPatTrainUserModel current = tickerUser("user", currentSession);
    GeneralTickerPatTrainRoomUserModel room = new GeneralTickerPatTrainRoomUserModel();
    room.getJoinUser().add(current);
    WebSocketGeneralTickerPatService.PAT_ROOM.put(101, room);

    new WebSocketGeneralTickerPatService()
        .onError("101", oldSession, new RuntimeException("expected"));

    GeneralTickerPatTrainRoomUserModel actual = WebSocketGeneralTickerPatService.PAT_ROOM.get(101);
    assertNotNull(actual, "stale error must not remove the room containing the replacement");
    assertSame(current, actual.getJoinUser().getFirst());
  }

  @Test
  void tickerCurrentErrorIsIdempotentAndRemovesEmptyRoom() {
    Session currentSession = session("ticker-current", "user");
    GeneralTickerPatTrainRoomUserModel room = new GeneralTickerPatTrainRoomUserModel();
    room.getJoinUser().add(tickerUser("user", currentSession));
    WebSocketGeneralTickerPatService.PAT_ROOM.put(102, room);
    WebSocketGeneralTickerPatService endpoint = new WebSocketGeneralTickerPatService();

    endpoint.onError("102", currentSession, new RuntimeException("expected"));
    assertDoesNotThrow(() -> endpoint.onError("102", currentSession,
        new RuntimeException("duplicate callback")));

    assertFalse(WebSocketGeneralTickerPatService.PAT_ROOM.containsKey(102));
  }

  @Test
  void keyStaleStudentMessageDoesNotMutateReplacementOrNotifyTeacher() {
    Session oldSession = session("key-old-message", "student");
    Session currentSession = session("key-current-message", "student");
    WebSocketSessionProbe teacher = WebSocketSessionProbe.bound("key-teacher", "teacher");
    GeneralPatTrainUserModelDto current = generalUser("student", currentSession);
    GeneralPatTrainRoomUserDto room = generalRoom(current);
    room.setGroupUser(generalUser("teacher", teacher.session()));
    WebSocketGeneralKeyPatService.ROOM.put(201, room);

    new WebSocketGeneralKeyPatService()
        .onMessage("201", "{\"topic\":\"ready\"}", oldSession);

    assertEquals(1, current.getStatus());
    assertTrue(teacher.outbound().isEmpty());
  }

  @Test
  void telexStaleTeacherControlMessageDoesNotBroadcastToStudents() {
    Session oldTeacher = session("telex-old-teacher", "teacher");
    Session currentTeacher = session("telex-current-teacher", "teacher");
    WebSocketSessionProbe student = WebSocketSessionProbe.bound("telex-student", "student");
    GeneralPatTrainRoomUserDto room = generalRoom(generalUser("student", student.session()));
    room.setGroupUser(generalUser("teacher", currentTeacher));
    WebSocketGeneralTelexPatService.ROOM.put("train-201", room);

    new WebSocketGeneralTelexPatService()
        .onMessage("train-201", "{\"topic\":\"begin\"}", oldTeacher);

    assertTrue(student.outbound().isEmpty());
  }

  @Test
  void tickerStaleStudentMessageDoesNotMutateReplacementOrNotifyTeacher() {
    Session oldSession = session("ticker-old-message", "student");
    Session currentSession = session("ticker-current-message", "student");
    WebSocketSessionProbe teacher = WebSocketSessionProbe.bound("ticker-teacher", "teacher");
    GeneralTickerPatTrainUserModel current = tickerUser("student", currentSession);
    GeneralTickerPatTrainRoomUserModel room = new GeneralTickerPatTrainRoomUserModel();
    room.getJoinUser().add(current);
    GeneralTickerPatTrainUserModel teacherUser = tickerUser("teacher", teacher.session());
    teacherUser.setRole(1);
    room.setGroupUser(teacherUser);
    WebSocketGeneralTickerPatService.PAT_ROOM.put(201, room);

    new WebSocketGeneralTickerPatService()
        .onMessage("201", "{\"topic\":\"ready\"}", oldSession);

    assertEquals(1, current.getStatus());
    assertTrue(teacher.outbound().isEmpty());
  }

  @Test
  void keyMalformedFramesReturnErrorsAndReadyHeartbeatRemainUsable() {
    WebSocketSessionProbe student = WebSocketSessionProbe.bound("key-malformed", "student");
    WebSocketSessionProbe teacher = WebSocketSessionProbe.bound("key-teacher", "teacher");
    GeneralPatTrainUserModelDto studentModel = generalUser("student", student.session());
    GeneralPatTrainRoomUserDto room = generalRoom(studentModel);
    room.setGroupUser(generalUser("teacher", teacher.session()));
    WebSocketGeneralKeyPatService.ROOM.put(401, room);
    WebSocketGeneralKeyPatService endpoint = new WebSocketGeneralKeyPatService();

    for (String frame : malformedFrames()) {
      endpoint.onMessage("401", frame, student.session());
      assertFormatError(student, frame);
    }
    assertEquals(1, studentModel.getStatus());
    assertSame(room, WebSocketGeneralKeyPatService.ROOM.get(401));
    assertTrue(teacher.outbound().isEmpty());
    assertTrue(student.session().isOpen());

    endpoint.onMessage("401", "{\"topic\":\"ready\"}", student.session());
    endpoint.onMessage("401", WebSocketHeartbeat.PING, student.session());
    assertEquals(2, studentModel.getStatus());
    assertEquals(List.of(WebSocketHeartbeat.PONG), student.outbound());
    assertReady(teacher);
  }

  @Test
  void telexMalformedFramesReturnErrorsAndReadyHeartbeatRemainUsable() {
    WebSocketSessionProbe student = WebSocketSessionProbe.bound("telex-malformed", "student");
    WebSocketSessionProbe teacher = WebSocketSessionProbe.bound("telex-teacher", "teacher");
    GeneralPatTrainUserModelDto studentModel = generalUser("student", student.session());
    GeneralPatTrainRoomUserDto room = generalRoom(studentModel);
    room.setGroupUser(generalUser("teacher", teacher.session()));
    WebSocketGeneralTelexPatService.ROOM.put("train-401", room);
    WebSocketGeneralTelexPatService endpoint = new WebSocketGeneralTelexPatService();

    for (String frame : malformedFrames()) {
      endpoint.onMessage("train-401", frame, student.session());
      assertFormatError(student, frame);
    }
    assertEquals(1, studentModel.getStatus());
    assertSame(room, WebSocketGeneralTelexPatService.ROOM.get("train-401"));
    assertTrue(teacher.outbound().isEmpty());
    assertTrue(student.session().isOpen());

    endpoint.onMessage("train-401", "{\"topic\":\"ready\"}", student.session());
    endpoint.onMessage("train-401", WebSocketHeartbeat.PING, student.session());
    assertEquals(2, studentModel.getStatus());
    assertEquals(List.of(WebSocketHeartbeat.PONG), student.outbound());
    assertReady(teacher);
  }

  @Test
  void tickerMalformedFramesReturnErrorsAndReadyHeartbeatRemainUsable() {
    WebSocketSessionProbe student = WebSocketSessionProbe.bound("ticker-malformed", "student");
    WebSocketSessionProbe teacher = WebSocketSessionProbe.bound("ticker-teacher", "teacher");
    GeneralTickerPatTrainUserModel studentModel = tickerUser("student", student.session());
    studentModel.setRole(0);
    GeneralTickerPatTrainUserModel teacherModel = tickerUser("teacher", teacher.session());
    teacherModel.setRole(1);
    GeneralTickerPatTrainRoomUserModel room = new GeneralTickerPatTrainRoomUserModel();
    room.getJoinUser().add(studentModel);
    room.setGroupUser(teacherModel);
    WebSocketGeneralTickerPatService.PAT_ROOM.put(402, room);
    WebSocketGeneralTickerPatService endpoint = new WebSocketGeneralTickerPatService();

    for (String frame : malformedFrames()) {
      endpoint.onMessage("402", frame, student.session());
      assertFormatError(student, frame);
    }
    assertEquals(1, studentModel.getStatus());
    assertSame(room, WebSocketGeneralTickerPatService.PAT_ROOM.get(402));
    assertTrue(teacher.outbound().isEmpty());
    assertTrue(student.session().isOpen());
    endpoint.onMessage("402", "{\"topic\":\"ready\"}", student.session());
    endpoint.onMessage("402", WebSocketHeartbeat.PING, student.session());
    assertEquals(2, studentModel.getStatus());
    assertEquals(List.of(WebSocketHeartbeat.PONG), student.outbound());
    assertReady(teacher);
  }

  @Test
  void keyTopicsRequireBoundSenderRoleAndIdentity() {
    WebSocketSessionProbe student = WebSocketSessionProbe.bound("key-role-student", "student");
    WebSocketSessionProbe teacher = WebSocketSessionProbe.bound("key-role-teacher", "teacher");
    GeneralPatTrainUserModelDto member = generalUser("student", student.session());
    GeneralPatTrainUserModelDto group = generalUser("teacher", teacher.session());
    group.setRole(1);
    GeneralPatTrainRoomUserDto room = generalRoom(member);
    room.setGroupUser(group);
    WebSocketGeneralKeyPatService.ROOM.put(501, room);
    WebSocketGeneralKeyPatService endpoint = new WebSocketGeneralKeyPatService();
    assertTopicPermissions((frame, session) -> endpoint.onMessage("501", frame, session),
        student, teacher, member::getStatus, group::getStatus, 1);
    assertSame(room, WebSocketGeneralKeyPatService.ROOM.get(501));
  }

  @Test
  void telexTopicsRequireBoundSenderRoleAndIdentity() {
    WebSocketSessionProbe student = WebSocketSessionProbe.bound("telex-role-student", "student");
    WebSocketSessionProbe teacher = WebSocketSessionProbe.bound("telex-role-teacher", "teacher");
    GeneralPatTrainUserModelDto member = generalUser("student", student.session());
    GeneralPatTrainUserModelDto group = generalUser("teacher", teacher.session());
    group.setRole(1);
    GeneralPatTrainRoomUserDto room = generalRoom(member);
    room.setGroupUser(group);
    WebSocketGeneralTelexPatService.ROOM.put("train-501", room);
    WebSocketGeneralTelexPatService endpoint = new WebSocketGeneralTelexPatService();
    assertTopicPermissions((frame, session) -> endpoint.onMessage("train-501", frame, session),
        student, teacher, member::getStatus, group::getStatus, 1);
    assertSame(room, WebSocketGeneralTelexPatService.ROOM.get("train-501"));
  }

  @Test
  void tickerTopicsRequireBoundSenderRoleAndIdentity() {
    WebSocketSessionProbe student = WebSocketSessionProbe.bound("ticker-role-student", "student");
    WebSocketSessionProbe teacher = WebSocketSessionProbe.bound("ticker-role-teacher", "teacher");
    GeneralTickerPatTrainUserModel member = tickerUser("student", student.session());
    GeneralTickerPatTrainUserModel group = tickerUser("teacher", teacher.session());
    group.setRole(1);
    GeneralTickerPatTrainRoomUserModel room = new GeneralTickerPatTrainRoomUserModel();
    room.getJoinUser().add(member);
    room.setGroupUser(group);
    WebSocketGeneralTickerPatService.PAT_ROOM.put(501, room);
    WebSocketGeneralTickerPatService endpoint = new WebSocketGeneralTickerPatService();
    assertTopicPermissions((frame, session) -> endpoint.onMessage("501", frame, session),
        student, teacher, member::getStatus, group::getStatus, 3);
    assertSame(room, WebSocketGeneralTickerPatService.PAT_ROOM.get(501));
  }

  private static void assertTopicPermissions(BiConsumer<String, Session> receive,
      WebSocketSessionProbe student, WebSocketSessionProbe teacher,
      IntSupplier studentStatus, IntSupplier teacherStatus, int finishStatus) {
    for (String topic : List.of("begin", "end", "unknown")) {
      receive.accept(topicFrame(topic, "teacher", 1), student.session());
      assertRejectedTopic(student);
      assertTrue(teacher.outbound().isEmpty(), "学生控制帧不得通知教员");
      assertEquals(1, studentStatus.getAsInt());
      assertEquals(1, teacherStatus.getAsInt());
    }
    for (String topic : List.of("ready", "pat", "finish", "unknown")) {
      receive.accept(topicFrame(topic, "student", 0), teacher.session());
      assertRejectedTopic(teacher);
      assertTrue(student.outbound().isEmpty(), "教员不得伪造学员进度");
      assertEquals(1, studentStatus.getAsInt());
      assertEquals(1, teacherStatus.getAsInt());
    }
    for (String topic : List.of("begin", "end")) {
      receive.accept(topicFrame(topic, "forged", 0), teacher.session());
      assertDeliveredTopic(student, topic, "teacher");
      assertTrue(teacher.outbound().isEmpty());
    }
    for (String topic : List.of("finish", "ready", "pat")) {
      receive.accept(topicFrame(topic, "teacher", 1), student.session());
      assertDeliveredTopic(teacher, topic, "student");
      assertTrue(student.outbound().isEmpty(), "学员消息只能发给教员，不能广播给学员");
      assertEquals("finish".equals(topic) ? finishStatus : 2, studentStatus.getAsInt());
      assertEquals(1, teacherStatus.getAsInt());
    }
    assertTrue(student.session().isOpen());
    assertTrue(teacher.session().isOpen());
  }

  private static String topicFrame(String topic, String forgedId, int forgedRole) {
    JsonObject body = new JsonObject();
    body.addProperty("topic", topic);
    body.addProperty("id", forgedId);
    body.addProperty("role", forgedRole);
    body.addProperty("log", "payload");
    return JSONUtils.toJson(body);
  }

  private static void assertRejectedTopic(WebSocketSessionProbe sender) {
    assertEquals(1, sender.outbound().size());
    JsonObject envelope = JSONUtils.fromJson(sender.outbound().getFirst(), JsonObject.class);
    assertEquals(-1, envelope.get("code").getAsInt());
    assertTrue(envelope.getAsJsonPrimitive("data").isString());
    assertTrue(sender.session().isOpen());
    sender.outbound().clear();
  }

  private static void assertDeliveredTopic(WebSocketSessionProbe recipient, String topic, String userId) {
    assertEquals(1, recipient.outbound().size());
    JsonObject envelope = JSONUtils.fromJson(recipient.outbound().getFirst(), JsonObject.class);
    assertEquals(1, envelope.get("code").getAsInt());
    JsonObject body = JSONUtils.fromJson(envelope.get("data").getAsString(), JsonObject.class);
    assertEquals(topic, body.get("topic").getAsString());
    assertEquals(userId, body.get("id").getAsString());
    assertEquals("payload", body.get("log").getAsString());
    recipient.outbound().clear();
  }

  private static void assertFormatError(WebSocketSessionProbe probe, String input) {
    assertTrue(probe.session().isOpen(), "坏帧不得关闭连接：" + input);
    assertEquals(1, probe.outbound().size(), "每个坏帧应只返回一个错误：" + input);
    JsonObject envelope = JSONUtils.fromJson(probe.outbound().getFirst(), JsonObject.class);
    assertEquals(-1, envelope.get("code").getAsInt());
    assertTrue(envelope.get("data").isJsonPrimitive());
    assertTrue(envelope.getAsJsonPrimitive("data").isString());
    assertEquals("消息格式错误", envelope.get("data").getAsString());
    probe.outbound().clear();
  }
  private static List<String> malformedFrames() {
    return List.of("not-json", "{", "null", "[]", "[[\"topic\",\"ready\"]]", "42",
        "{}", "{\"topic\":null}", "{\"topic\":123}", "{\"topic\":true}",
        "{\"topic\":{}}", "{\"topic\":[]}", "{\"topic\":\" \"}");
  }

  private static void assertReady(WebSocketSessionProbe teacher) {
    assertEquals(1, teacher.outbound().size(), "只有合法 ready 能通知教员");
    JsonObject envelope = JSONUtils.fromJson(teacher.outbound().getFirst(), JsonObject.class);
    assertEquals(1, envelope.get("code").getAsInt());
    JsonObject body = JSONUtils.fromJson(envelope.get("data").getAsString(), JsonObject.class);
    assertEquals("ready", body.get("topic").getAsString());
  }


  @Test
  void deletedGeneralRoomSnapshotsCloseEverySession() {
    Session keyTeacher = session("key-delete-teacher", "teacher");
    Session keyStudent = session("key-delete-student", "student");
    GeneralPatTrainRoomUserDto keyRoom = generalRoom(generalUser("student", keyStudent));
    keyRoom.setGroupUser(generalUser("teacher", keyTeacher));

    Session telexTeacher = session("telex-delete-teacher", "teacher");
    Session telexStudent = session("telex-delete-student", "student");
    GeneralPatTrainRoomUserDto telexRoom = generalRoom(generalUser("student", telexStudent));
    telexRoom.setGroupUser(generalUser("teacher", telexTeacher));

    Session tickerTeacher = session("ticker-delete-teacher", "teacher");
    Session tickerStudent = session("ticker-delete-student", "student");
    GeneralTickerPatTrainRoomUserModel tickerRoom = new GeneralTickerPatTrainRoomUserModel();
    tickerRoom.setGroupUser(tickerUser("teacher", tickerTeacher));
    tickerRoom.getJoinUser().add(tickerUser("student", tickerStudent));

    WebSocketGeneralKeyPatService.closeRoomSessions(keyRoom);
    WebSocketGeneralTelexPatService.closeRoomSessions(telexRoom);
    WebSocketGeneralTickerPatService.closeRoomSessions(tickerRoom);

    assertAll(
        () -> assertFalse(keyTeacher.isOpen()),
        () -> assertFalse(keyStudent.isOpen()),
        () -> assertFalse(telexTeacher.isOpen()),
        () -> assertFalse(telexStudent.isOpen()),
        () -> assertFalse(tickerTeacher.isOpen()),
        () -> assertFalse(tickerStudent.isOpen()));
  }

  /**
   * 拒接路径的送达契约：错误帧必须在关闭连接之前发出，否则客户端只看到一次无理由的断开。
   *
   * <p>断言「帧已记账 + 连接已关闭」即证明了先后次序：{@code sendErrMessage} 先查
   * {@code session.isOpen()}，而 {@code close()} 把 open 翻成 false 之后不可逆，
   * 所以这一帧只可能记在关闭之前。
   *
   * <p>本域 {@code sendErrMessage} 走 {@code getBasicRemote()}（同步写，见
   * {@code WebSocketGeneralKeyPatService.sendErrMessage} 的方法注释），断言因此落在
   * {@link WebSocketSessionProbe#basicOutbound()}：改成异步写就红。
   */
  @Test
  void keyRejectedHandshakeSendsErrorFrameBeforeClosing() {
    WebSocketSessionProbe rejected = WebSocketSessionProbe.anonymous("key-reject");
    WebSocketGeneralKeyPatService endpoint = new WebSocketGeneralKeyPatService();
    endpoint.handshake = new WebSocketHandshake();

    endpoint.onOpen("301", rejected.session());

    assertAll(
        () -> assertTrue(rejected.asyncOutbound().isEmpty(), "拒接原因不得走异步写"),
        () -> assertEquals(1, rejected.basicOutbound().size(), "拒接必须发且只发一帧错误"),
        () -> assertTrue(rejected.basicOutbound().getFirst().contains("登录凭据无效，拒绝建立连接")),
        () -> assertTrue(rejected.basicOutbound().getFirst().contains("\"code\":-1"), "必须是错误信封"),
        () -> assertFalse(rejected.session().isOpen(), "拒接后必须关闭连接"),
        () -> assertFalse(WebSocketGeneralKeyPatService.ROOM.containsKey(301), "被拒的连接不得入房"));
  }

  /** 同 {@link #keyRejectedHandshakeSendsErrorFrameBeforeClosing}，telex 域同样是同步写。 */
  @Test
  void telexRejectedHandshakeSendsErrorFrameBeforeClosing() {
    WebSocketSessionProbe rejected = WebSocketSessionProbe.anonymous("telex-reject");
    WebSocketGeneralTelexPatService endpoint = new WebSocketGeneralTelexPatService();
    endpoint.handshake = new WebSocketHandshake();

    endpoint.onOpen("train-301", rejected.session());

    assertAll(
        () -> assertTrue(rejected.asyncOutbound().isEmpty(), "拒接原因不得走异步写"),
        () -> assertEquals(1, rejected.basicOutbound().size(), "拒接必须发且只发一帧错误"),
        () -> assertTrue(rejected.basicOutbound().getFirst().contains("登录凭据无效，拒绝建立连接")),
        () -> assertTrue(rejected.basicOutbound().getFirst().contains("\"code\":-1"), "必须是错误信封"),
        () -> assertFalse(rejected.session().isOpen(), "拒接后必须关闭连接"),
        () -> assertFalse(WebSocketGeneralTelexPatService.ROOM.containsKey("train-301"), "被拒的连接不得入房"));
  }

  /**
   * ticker 域的同一条契约。
   *
   * <p>本域 {@code sendErrMessage} 原先走 {@code getAsyncRemote()}，与 key/telex 的同步写不一致 ——
   * 而它的三个拒接调用点都紧跟 {@code close(session)}，异步写只是入队，close 可能抢在刷出前执行。
   * 该偏差已修（见 {@code WebSocketGeneralTickerPatService.sendErrMessage} 的注释），因此这里
   * 与 key/telex 同口径钉死 basic 通道：谁把它改回异步写就红。
   */
  @Test
  void tickerRejectedHandshakeSendsErrorFrameBeforeClosing() {
    WebSocketSessionProbe rejected = WebSocketSessionProbe.anonymous("ticker-reject");
    WebSocketGeneralTickerPatService endpoint = new WebSocketGeneralTickerPatService();
    endpoint.handshake = new WebSocketHandshake();

    endpoint.onOpen("301", "0", rejected.session());

    assertAll(
        () -> assertTrue(rejected.asyncOutbound().isEmpty(), "拒接原因不得走异步写"),
        () -> assertEquals(1, rejected.basicOutbound().size(), "拒接必须发且只发一帧错误"),
        () -> assertTrue(rejected.basicOutbound().getFirst().contains("登录凭据无效，拒绝建立连接")),
        () -> assertTrue(rejected.basicOutbound().getFirst().contains("\"code\":-1"), "必须是错误信封"),
        () -> assertFalse(rejected.session().isOpen(), "拒接后必须关闭连接"),
        () -> assertFalse(WebSocketGeneralTickerPatService.PAT_ROOM.containsKey(301), "被拒的连接不得入房"));
  }

  private static GeneralPatTrainRoomUserDto generalRoom(GeneralPatTrainUserModelDto user) {
    GeneralPatTrainRoomUserDto room = new GeneralPatTrainRoomUserDto();
    room.getJoinUser().add(user);
    return room;
  }

  private static GeneralPatTrainUserModelDto generalUser(String id, Session session) {
    GeneralPatTrainUserModelDto user = new GeneralPatTrainUserModelDto();
    user.setId(id);
    user.setRole(0);
    user.setStatus(1);
    user.setSession(session);
    return user;
  }

  private static GeneralTickerPatTrainUserModel tickerUser(String id, Session session) {
    GeneralTickerPatTrainUserModel user = new GeneralTickerPatTrainUserModel();
    user.setId(id);
    user.setRole(0);
    user.setStatus(1);
    user.setSession(session);
    return user;
  }

  /**
   * 造一条「已完成握手」的连接：握手鉴权（SEC-06）后端点只认 onOpen 绑在会话上的身份，
   * 路径参数不再参与认人，所以这里必须显式绑定该连接的用户 id。
   */
  private static Session session(String id, String userId) {
    return WebSocketSessionProbe.bound(id, userId).session();
  }
}
