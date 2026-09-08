package com.nip.ws;

import com.nip.dto.general.GeneralPatTrainRoomUserDto;
import com.nip.dto.general.GeneralPatTrainUserModelDto;
import com.nip.ws.model.GeneralTickerPatTrainRoomUserModel;
import com.nip.ws.model.GeneralTickerPatTrainUserModel;
import jakarta.websocket.Session;
import jakarta.websocket.RemoteEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketGeneralSessionLifecycleTest {

  @AfterEach
  void clearRooms() {
    WebSocketGeneralKeyPatService.ROOM.clear();
    WebSocketGeneralTelexPatService.ROOM.clear();
    WebSocketGeneralTickerPatService.PAT_ROOM.clear();
  }

  @Test
  void keyStaleCloseDoesNotRemoveReplacement() {
    Session oldSession = session("key-old");
    Session currentSession = session("key-current");
    GeneralPatTrainUserModelDto current = generalUser("user", currentSession);
    GeneralPatTrainRoomUserDto room = generalRoom(current);
    WebSocketGeneralKeyPatService.ROOM.put(101, room);

    new WebSocketGeneralKeyPatService().onClose("user", 101, oldSession);

    GeneralPatTrainRoomUserDto actual = WebSocketGeneralKeyPatService.ROOM.get(101);
    assertNotNull(actual, "stale close must not remove the room containing the replacement");
    assertSame(current, actual.getJoinUser().getFirst());
  }

  @Test
  void keyCurrentErrorThenCloseRemovesEmptyRoomIdempotently() {
    Session currentSession = session("key-current");
    WebSocketGeneralKeyPatService.ROOM.put(102, generalRoom(generalUser("user", currentSession)));
    WebSocketGeneralKeyPatService endpoint = new WebSocketGeneralKeyPatService();

    endpoint.onError("user", 102, currentSession, new RuntimeException("expected"));
    assertDoesNotThrow(() -> endpoint.onClose("user", 102, currentSession));

    assertFalse(WebSocketGeneralKeyPatService.ROOM.containsKey(102));
  }

  @Test
  void telexStaleCloseDoesNotRemoveReplacement() {
    Session oldSession = session("telex-old");
    Session currentSession = session("telex-current");
    GeneralPatTrainUserModelDto current = generalUser("user", currentSession);
    GeneralPatTrainRoomUserDto room = generalRoom(current);
    WebSocketGeneralTelexPatService.ROOM.put("train-101", room);

    new WebSocketGeneralTelexPatService().onClose("user", "train-101", oldSession);

    GeneralPatTrainRoomUserDto actual = WebSocketGeneralTelexPatService.ROOM.get("train-101");
    assertNotNull(actual, "stale close must not remove the room containing the replacement");
    assertSame(current, actual.getJoinUser().getFirst());
  }

  @Test
  void telexCurrentErrorThenCloseRemovesEmptyRoomIdempotently() {
    Session currentSession = session("telex-current");
    WebSocketGeneralTelexPatService.ROOM.put("train-102", generalRoom(generalUser("user", currentSession)));
    WebSocketGeneralTelexPatService endpoint = new WebSocketGeneralTelexPatService();

    endpoint.onError("user", "train-102", currentSession, new RuntimeException("expected"));
    assertDoesNotThrow(() -> endpoint.onClose("user", "train-102", currentSession));

    assertFalse(WebSocketGeneralTelexPatService.ROOM.containsKey("train-102"));
  }

  @Test
  void tickerStaleErrorDoesNotRemoveReplacement() {
    Session oldSession = session("ticker-old");
    Session currentSession = session("ticker-current");
    GeneralTickerPatTrainUserModel current = tickerUser("user", currentSession);
    GeneralTickerPatTrainRoomUserModel room = new GeneralTickerPatTrainRoomUserModel();
    room.getJoinUser().add(current);
    WebSocketGeneralTickerPatService.PAT_ROOM.put(101, room);

    new WebSocketGeneralTickerPatService()
        .onError("user", 101, oldSession, new RuntimeException("expected"));

    GeneralTickerPatTrainRoomUserModel actual = WebSocketGeneralTickerPatService.PAT_ROOM.get(101);
    assertNotNull(actual, "stale error must not remove the room containing the replacement");
    assertSame(current, actual.getJoinUser().getFirst());
  }

  @Test
  void tickerCurrentErrorIsIdempotentAndRemovesEmptyRoom() {
    Session currentSession = session("ticker-current");
    GeneralTickerPatTrainRoomUserModel room = new GeneralTickerPatTrainRoomUserModel();
    room.getJoinUser().add(tickerUser("user", currentSession));
    WebSocketGeneralTickerPatService.PAT_ROOM.put(102, room);
    WebSocketGeneralTickerPatService endpoint = new WebSocketGeneralTickerPatService();

    endpoint.onError("user", 102, currentSession, new RuntimeException("expected"));
    assertDoesNotThrow(() -> endpoint.onError("user", 102, currentSession,
        new RuntimeException("duplicate callback")));

    assertFalse(WebSocketGeneralTickerPatService.PAT_ROOM.containsKey(102));
  }

  @Test
  void keyStaleStudentMessageDoesNotMutateReplacementOrNotifyTeacher() {
    Session oldSession = session("key-old-message");
    Session currentSession = session("key-current-message");
    SessionProbe teacher = recordingSession("key-teacher");
    GeneralPatTrainUserModelDto current = generalUser("student", currentSession);
    GeneralPatTrainRoomUserDto room = generalRoom(current);
    room.setGroupUser(generalUser("teacher", teacher.session()));
    WebSocketGeneralKeyPatService.ROOM.put(201, room);

    new WebSocketGeneralKeyPatService()
        .onMessage("student", 201, "{\"topic\":\"ready\"}", oldSession);

    assertEquals(1, current.getStatus());
    assertTrue(teacher.outbound().isEmpty());
  }

  @Test
  void telexStaleTeacherControlMessageDoesNotBroadcastToStudents() {
    Session oldTeacher = session("telex-old-teacher");
    Session currentTeacher = session("telex-current-teacher");
    SessionProbe student = recordingSession("telex-student");
    GeneralPatTrainRoomUserDto room = generalRoom(generalUser("student", student.session()));
    room.setGroupUser(generalUser("teacher", currentTeacher));
    WebSocketGeneralTelexPatService.ROOM.put("train-201", room);

    new WebSocketGeneralTelexPatService()
        .onMessage("teacher", "train-201", "{\"topic\":\"begin\"}", oldTeacher);

    assertTrue(student.outbound().isEmpty());
  }

  @Test
  void tickerStaleStudentMessageDoesNotMutateReplacementOrNotifyTeacher() {
    Session oldSession = session("ticker-old-message");
    Session currentSession = session("ticker-current-message");
    SessionProbe teacher = recordingSession("ticker-teacher");
    GeneralTickerPatTrainUserModel current = tickerUser("student", currentSession);
    GeneralTickerPatTrainRoomUserModel room = new GeneralTickerPatTrainRoomUserModel();
    room.getJoinUser().add(current);
    GeneralTickerPatTrainUserModel teacherUser = tickerUser("teacher", teacher.session());
    teacherUser.setRole(1);
    room.setGroupUser(teacherUser);
    WebSocketGeneralTickerPatService.PAT_ROOM.put(201, room);

    new WebSocketGeneralTickerPatService()
        .onMessage("student", 201, "{\"topic\":\"ready\"}", oldSession);

    assertEquals(1, current.getStatus());
    assertTrue(teacher.outbound().isEmpty());
  }

  @Test
  void deletedGeneralRoomSnapshotsCloseEverySession() {
    Session keyTeacher = session("key-delete-teacher");
    Session keyStudent = session("key-delete-student");
    GeneralPatTrainRoomUserDto keyRoom = generalRoom(generalUser("student", keyStudent));
    keyRoom.setGroupUser(generalUser("teacher", keyTeacher));

    Session telexTeacher = session("telex-delete-teacher");
    Session telexStudent = session("telex-delete-student");
    GeneralPatTrainRoomUserDto telexRoom = generalRoom(generalUser("student", telexStudent));
    telexRoom.setGroupUser(generalUser("teacher", telexTeacher));

    Session tickerTeacher = session("ticker-delete-teacher");
    Session tickerStudent = session("ticker-delete-student");
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

  private static Session session(String id) {
    return recordingSession(id).session();
  }

  private static SessionProbe recordingSession(String id) {
    AtomicBoolean open = new AtomicBoolean(true);
    List<String> outbound = new CopyOnWriteArrayList<>();
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
          case "close" -> {
            open.set(false);
            yield null;
          }
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "Session[" + id + "]";
          default -> defaultValue(method.getReturnType());
        });
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
