package com.nip.ws;

import com.nip.dao.UserDao;
import com.nip.entity.UserEntity;
import com.nip.ws.model.RoomModel;
import com.nip.ws.model.UserModel;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Objects;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class WebSocketUnionLifecycleTest {

  @AfterEach
  void clearState() throws Exception {
    unionMap("webSocketClientSet").clear();
    unionMap("onlineUsers").clear();
    unionMap("onlineRooms").clear();
  }

  @Test
  void replacementWaitsForOldCleanupAndLeavesCompleteCurrentState() throws Exception {
    String sid = "same-user";
    CountDownLatch replacementLookup = new CountDownLatch(1);
    WebSocketUnionService endpoint = endpoint(new SignallingUserDao(2, replacementLookup));
    TestSession oldSession = session("old");
    TestSession replacementSession = session("replacement");
    endpoint.onOpen(oldSession.session(), sid);

    PauseAfterEmptyObservation members = new PauseAfterEmptyObservation();
    members.add(user(sid));
    RoomModel room = room("room", sid, members);
    unionMap("onlineRooms").put(room.getId(), room);

    FutureTask<Void> cleanup = new FutureTask<>(() -> {
      endpoint.onClose(oldSession.session());
      return null;
    });
    Thread cleanupThread = Thread.ofVirtual().start(cleanup);
    assertTrue(members.emptyObserved.await(5, TimeUnit.SECONDS));

    FutureTask<Void> replacement = new FutureTask<>(() -> {
      endpoint.onOpen(replacementSession.session(), sid);
      return null;
    });
    Thread replacementThread = Thread.ofVirtual().start(replacement);
    try {
      assertFalse(replacementLookup.await(250, TimeUnit.MILLISECONDS),
          "replacement lookup/publication must wait until old cleanup completes");
    } finally {
      members.resume.countDown();
    }

    cleanup.get(5, TimeUnit.SECONDS);
    replacement.get(5, TimeUnit.SECONDS);
    cleanupThread.join();
    replacementThread.join();

    Object current = unionMap("webSocketClientSet").get(sid);
    assertNotNull(current);
    assertSame(replacementSession.session(), clientSession(current));
    assertTrue(unionMap("onlineUsers").containsKey(sid));
    assertTrue(replacementSession.open.get());
  }

  @Test
  void concurrentSameSidOpensTrackExactlyOneOpenSession() throws Exception {
    String sid = "same-user";
    CountDownLatch firstLookupEntered = new CountDownLatch(1);
    CountDownLatch releaseFirstLookup = new CountDownLatch(1);
    CountDownLatch secondLookupEntered = new CountDownLatch(1);
    WebSocketUnionService endpoint = endpoint(
        new FirstLookupBlockingUserDao(firstLookupEntered, releaseFirstLookup, secondLookupEntered));
    TestSession first = session("first");
    TestSession second = session("second");

    FutureTask<Void> firstOpen = new FutureTask<>(() -> {
      endpoint.onOpen(first.session(), sid);
      return null;
    });
    Thread firstThread = Thread.ofVirtual().start(firstOpen);
    assertTrue(firstLookupEntered.await(5, TimeUnit.SECONDS));

    FutureTask<Void> secondOpen = new FutureTask<>(() -> {
      endpoint.onOpen(second.session(), sid);
      return null;
    });
    Thread secondThread = Thread.ofVirtual().start(secondOpen);
    secondLookupEntered.await(250, TimeUnit.MILLISECONDS);
    releaseFirstLookup.countDown();

    firstOpen.get(5, TimeUnit.SECONDS);
    secondOpen.get(5, TimeUnit.SECONDS);
    firstThread.join();
    secondThread.join();

    Object current = unionMap("webSocketClientSet").get(sid);
    assertNotNull(current);
    Session currentSession = clientSession(current);
    assertTrue(currentSession.isOpen());
    assertEquals(1, List.of(first, second).stream().filter(item -> item.open.get()).count(),
        "the displaced same-sid Session must not remain open and untracked");
  }

  @Test
  void sameSidReplacementPreservesRoomMembershipWithoutUserExit() throws Exception {
    String sid = "member";
    WebSocketUnionService endpoint = endpoint(
        new SignallingUserDao(Integer.MAX_VALUE, new CountDownLatch(0)));
    TestSession oldSession = session("member-old");
    TestSession replacement = session("member-replacement");
    TestSession watcher = session("watcher");
    endpoint.onOpen(oldSession.session(), sid);
    endpoint.onOpen(watcher.session(), "watcher");
    RoomModel room = room("room-member", sid,
        new CopyOnWriteArrayList<>(List.of(user(sid))));
    unionMap("onlineRooms").put(room.getId(), room);
    watcher.outbound().clear();

    endpoint.onOpen(replacement.session(), sid);

    RoomModel mapped = (RoomModel) unionMap("onlineRooms").get(room.getId());
    assertNotNull(mapped);
    assertEquals(List.of(sid), mapped.getUsers().stream().map(UserModel::getId).toList());
    assertFalse(watcher.outbound().stream().anyMatch(message -> message.contains("\"code\":3")),
        "same-sid replacement must not broadcast USER_EXIT");
    assertSame(replacement.session(), clientSession(unionMap("webSocketClientSet").get(sid)));
    assertFalse(oldSession.open().get(), "same-sid replacement must close the displaced Session");
  }

  @Test
  void soleMemberExplicitExitRemovesRoomKey() throws Exception {
    WebSocketUnionService endpoint = endpoint(new SignallingUserDao(Integer.MAX_VALUE, new CountDownLatch(0)));
    TestSession owner = session("owner-session");
    endpoint.onOpen(owner.session(), "owner");
    RoomModel room = room("123", "owner", new CopyOnWriteArrayList<>(List.of(user("owner"))));
    unionMap("onlineRooms").put(room.getId(), room);

    endpoint.onMessage("{\"code\":14,\"data\":123}", owner.session());

    assertFalse(unionMap("onlineRooms").containsKey("123"),
        "explicit EXIT by the sole member must remove the room key");
  }

  @Test
  void disconnectInterleavedWithJoinCannotPublishIntoDetachedRoom() throws Exception {
    WebSocketUnionService endpoint = endpoint(new SignallingUserDao(Integer.MAX_VALUE, new CountDownLatch(0)));
    TestSession owner = session("owner-session");
    TestSession joiner = session("joiner-session");
    endpoint.onOpen(owner.session(), "owner");
    endpoint.onOpen(joiner.session(), "joiner");
    PauseBeforeJoinAdd members = new PauseBeforeJoinAdd("joiner");
    members.addInitial(user("owner"));
    RoomModel room = room("456", "owner", members);
    unionMap("onlineRooms").put(room.getId(), room);

    FutureTask<Void> join = new FutureTask<>(() -> {
      endpoint.onMessage("{\"code\":13,\"data\":456}", joiner.session());
      return null;
    });
    Thread joinThread = Thread.ofVirtual().start(join);
    assertTrue(members.joinAddEntered.await(5, TimeUnit.SECONDS));

    FutureTask<Void> disconnect = new FutureTask<>(() -> {
      endpoint.onClose(owner.session());
      return null;
    });
    Thread disconnectThread = Thread.ofVirtual().start(disconnect);
    boolean cleanupObservedEmpty = members.emptyObserved.await(250, TimeUnit.MILLISECONDS);
    members.resumeJoinAdd.countDown();
    if (cleanupObservedEmpty) {
      members.resumeEmptyCheck.countDown();
    }

    join.get(5, TimeUnit.SECONDS);
    disconnect.get(5, TimeUnit.SECONDS);
    joinThread.join();
    disconnectThread.join();

    RoomModel mapped = (RoomModel) unionMap("onlineRooms").get("456");
    assertNotNull(mapped, "JOIN success must not target a room holder detached from the map");
    assertEquals(List.of("joiner"), mapped.getUsers().stream().map(UserModel::getId).toList());
  }

  private static WebSocketUnionService endpoint(UserDao userDao) throws Exception {
    WebSocketUnionService endpoint = new WebSocketUnionService();
    Field field = WebSocketUnionService.class.getDeclaredField("userDao");
    field.setAccessible(true);
    field.set(endpoint, userDao);
    return endpoint;
  }

  private static UserModel user(String id) {
    UserModel user = new UserModel();
    user.setId(id);
    user.setName("name-" + id);
    return user;
  }

  private static RoomModel room(String id, String admin, List<UserModel> members) {
    RoomModel room = new RoomModel();
    room.setId(id);
    room.setAdmin(admin);
    room.setUsers(members);
    return room;
  }

  private static Session clientSession(Object client) throws Exception {
    var accessor = client.getClass().getDeclaredMethod("session");
    accessor.setAccessible(true);
    return (Session) accessor.invoke(client);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> unionMap(String fieldName) throws Exception {
    Field field = WebSocketUnionService.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (Map<String, Object>) field.get(null);
  }

  private static UserEntity entity(String id) {
    UserEntity entity = new UserEntity();
    entity.setId(id);
    entity.setUserName("name-" + id);
    entity.setUserImg("image-" + id);
    return entity;
  }

  private static final class SignallingUserDao extends UserDao {
    private final int signalCall;
    private final CountDownLatch signal;
    private final AtomicInteger calls = new AtomicInteger();

    private SignallingUserDao(int signalCall, CountDownLatch signal) {
      this.signalCall = signalCall;
      this.signal = signal;
    }

    @Override
    public UserEntity findUserEntityById(String id) {
      if (calls.incrementAndGet() == signalCall) {
        signal.countDown();
      }
      return entity(id);
    }
  }

  private static final class FirstLookupBlockingUserDao extends UserDao {
    private final CountDownLatch firstEntered;
    private final CountDownLatch releaseFirst;
    private final CountDownLatch secondEntered;
    private final AtomicInteger calls = new AtomicInteger();

    private FirstLookupBlockingUserDao(
        CountDownLatch firstEntered,
        CountDownLatch releaseFirst,
        CountDownLatch secondEntered) {
      this.firstEntered = firstEntered;
      this.releaseFirst = releaseFirst;
      this.secondEntered = secondEntered;
    }

    @Override
    public UserEntity findUserEntityById(String id) {
      if (calls.incrementAndGet() == 1) {
        firstEntered.countDown();
        await(releaseFirst);
      } else {
        secondEntered.countDown();
      }
      return entity(id);
    }
  }

  private static final class PauseAfterEmptyObservation extends CopyOnWriteArrayList<UserModel> {
    private final CountDownLatch emptyObserved = new CountDownLatch(1);
    private final CountDownLatch resume = new CountDownLatch(1);
    private final AtomicBoolean pause = new AtomicBoolean(true);

    @Override
    public boolean isEmpty() {
      boolean empty = super.isEmpty();
      if (empty && pause.compareAndSet(true, false)) {
        emptyObserved.countDown();
        await(resume);
      }
      return empty;
    }
  }

  private static final class PauseBeforeJoinAdd extends CopyOnWriteArrayList<UserModel> {
    private final String joinerId;
    private final CountDownLatch joinAddEntered = new CountDownLatch(1);
    private final CountDownLatch resumeJoinAdd = new CountDownLatch(1);
    private final CountDownLatch emptyObserved = new CountDownLatch(1);
    private final CountDownLatch resumeEmptyCheck = new CountDownLatch(1);

    private PauseBeforeJoinAdd(String joinerId) {
      this.joinerId = joinerId;
    }

    private void addInitial(UserModel user) {
      super.add(user);
    }

    @Override
    public boolean add(UserModel user) {
      if (Objects.equals(joinerId, user.getId())) {
        joinAddEntered.countDown();
        await(resumeJoinAdd);
      }
      return super.add(user);
    }

    @Override
    public boolean isEmpty() {
      boolean empty = super.isEmpty();
      if (empty) {
        emptyObserved.countDown();
        await(resumeEmptyCheck);
      }
      return empty;
    }
  }

  private record TestSession(Session session, AtomicBoolean open, List<String> outbound) {
  }

  private static TestSession session(String id) {
    AtomicBoolean open = new AtomicBoolean(true);
    List<String> outbound = new CopyOnWriteArrayList<>();
    Map<String, Object> properties = new java.util.concurrent.ConcurrentHashMap<>();
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
          case "getUserProperties" -> properties;
          case "getAsyncRemote" -> async;
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
    return new TestSession(session, open, outbound);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("barrier timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
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
