package com.nip.ws;

import com.nip.dto.general.GeneralPatTrainRoomUserDto;
import com.nip.dto.general.GeneralPatTrainUserModelDto;
import com.nip.testsupport.WebSocketStateReset;
import com.nip.ws.model.GeneralTickerPatTrainRoomUserModel;
import com.nip.ws.model.GeneralTickerPatTrainUserModel;
import jakarta.websocket.Session;
import jakarta.websocket.RemoteEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

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
    SessionProbe teacher = recordingSession("key-teacher", "teacher");
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
    SessionProbe student = recordingSession("telex-student", "student");
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
    SessionProbe teacher = recordingSession("ticker-teacher", "teacher");
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

  private record SessionProbe(Session session, List<String> outbound) {
  }

  /**
   * 造一条「已完成握手」的连接：握手鉴权（SEC-06）后端点只认 onOpen 绑在会话上的身份，
   * 路径参数不再参与认人，所以这里必须显式绑定该连接的用户 id。
   */
  private static Session session(String id, String userId) {
    return recordingSession(id, userId).session();
  }

  private static SessionProbe recordingSession(String id, String userId) {
    AtomicBoolean open = new AtomicBoolean(true);
    List<String> outbound = new CopyOnWriteArrayList<>();
    Map<String, Object> properties = new ConcurrentHashMap<>();
    RemoteEndpoint.Async async = (RemoteEndpoint.Async) Proxy.newProxyInstance(
        RemoteEndpoint.Async.class.getClassLoader(),
        new Class<?>[]{RemoteEndpoint.Async.class},
        (proxy, method, args) -> {
          if ("sendText".equals(method.getName()) && args != null && args.length > 0) {
            outbound.add(args[0].toString());
          }
          return defaultValue(method.getReturnType());
        });
    Session session = (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(),
        new Class<?>[]{Session.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getId" -> id;
          case "isOpen" -> open.get();
          case "getAsyncRemote" -> async;
          case "getUserProperties" -> properties;
          case "close" -> {
            open.set(false);
            yield null;
          }
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "Session[" + id + "]";
          default -> defaultValue(method.getReturnType());
        });
    WebSocketHandshake.bind(session, userId);
    return new SessionProbe(session, outbound);
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == char.class) {
      return '\0';
    }
    if (type == byte.class) {
      return (byte) 0;
    }
    if (type == short.class) {
      return (short) 0;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == float.class) {
      return 0F;
    }
    return 0D;
  }
}
