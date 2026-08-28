package com.nip.ws;

import com.nip.dto.general.GeneralPatTrainRoomUserDto;
import com.nip.dto.general.GeneralPatTrainUserModelDto;
import com.nip.ws.model.GeneralTickerPatTrainRoomUserModel;
import com.nip.ws.model.GeneralTickerPatTrainUserModel;
import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
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

  private static Session session(String id) {
    AtomicBoolean open = new AtomicBoolean(true);
    return (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(),
        new Class<?>[]{Session.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getId" -> id;
          case "isOpen" -> open.get();
          case "close" -> {
            open.set(false);
            yield null;
          }
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "Session[" + id + "]";
          default -> defaultValue(method.getReturnType());
        });
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
