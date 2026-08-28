package com.nip.ws.service.simulation;

import com.nip.ws.model.SimulationSessionHolder;
import com.nip.ws.model.SimulationUserModel;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.*;

class SimulationRoomLifecycleTest {

  @Test
  void concurrentOldCloseAndReplacementOpenNeverOrphansLiveHolder() throws Exception {
    for (int attempt = 0; attempt < 1_000; attempt++) {
      ConcurrentMap<Integer, List<SimulationSessionHolder>> rooms = new ConcurrentHashMap<>();
      SimulationSessionHolder oldHolder = holder("user", session("old-" + attempt));
      SimulationSessionHolder currentHolder = holder("user", session("current-" + attempt));
      SimulationRoomLifecycle.replace(rooms, 1, oldHolder);

      CountDownLatch start = new CountDownLatch(1);
      FutureTask<SimulationRoomLifecycle.Removal> close = new FutureTask<>(() -> {
        start.await();
        return SimulationRoomLifecycle.removeCurrent(
            rooms, 1, oldHolder.userModel().getId(), oldHolder.session());
      });
      FutureTask<SimulationRoomLifecycle.Replacement> open = new FutureTask<>(() -> {
        start.await();
        return SimulationRoomLifecycle.replace(rooms, 1, currentHolder);
      });
      Thread closeThread = Thread.ofVirtual().start(close);
      Thread openThread = Thread.ofVirtual().start(open);
      start.countDown();

      close.get();
      List<SimulationSessionHolder> openedList = open.get().members();
      closeThread.join();
      openThread.join();

      List<SimulationSessionHolder> mappedList = rooms.get(1);
      assertSame(openedList, mappedList, "live holder list must remain attached to the room map");
      assertEquals(List.of(currentHolder), mappedList);
    }
  }

  @Test
  void currentRemovalDeletesEmptyKeyAndDuplicateCallbackIsNoOp() {
    ConcurrentMap<Integer, List<SimulationSessionHolder>> rooms = new ConcurrentHashMap<>();
    SimulationSessionHolder current = holder("user", session("current"));
    SimulationRoomLifecycle.replace(rooms, 1, current);

    SimulationRoomLifecycle.Removal first = SimulationRoomLifecycle.removeCurrent(
        rooms, 1, current.userModel().getId(), current.session());
    SimulationRoomLifecycle.Removal duplicate = SimulationRoomLifecycle.removeCurrent(
        rooms, 1, current.userModel().getId(), current.session());

    assertSame(current, first.holder());
    assertFalse(rooms.containsKey(1));
    assertNull(duplicate);
  }

  private static SimulationSessionHolder holder(String userId, Session session) {
    SimulationUserModel user = new SimulationUserModel();
    user.setId(userId);
    return new SimulationSessionHolder(session, user);
  }

  private static Session session(String id) {
    return (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(),
        new Class<?>[]{Session.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getId" -> id;
          case "isOpen" -> true;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "Session[" + id + "]";
          default -> null;
        });
  }
}
