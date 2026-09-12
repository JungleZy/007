package com.nip.ws;

import com.nip.entity.UserEntity;
import com.nip.testsupport.WebSocketStateReset;
import com.nip.ws.model.RoomModel;
import com.nip.ws.model.UserModel;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
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

import static com.nip.testsupport.WebSocketStateReset.unionTable;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest

class WebSocketUnionLifecycleTest {

  @AfterEach
  void clearState() {
    WebSocketStateReset.clearAll();
  }

  @Test
  void replacementWaitsForOldCleanupAndLeavesCompleteCurrentState() throws Exception {
    String sid = "same-user";
    WebSocketUnionService endpoint = endpoint(new SignallingHandshake(0, new CountDownLatch(1)));
    TestSession oldSession = session("old", sid);
    TestSession replacementSession = session("replacement", sid);
    endpoint.onOpen(oldSession.session());

    PauseAfterEmptyObservation members = new PauseAfterEmptyObservation();
    members.add(user(sid));
    RoomModel room = room("room", sid, members);
    unionTable("onlineRooms").put(room.getId(), room);

    FutureTask<Void> cleanup = new FutureTask<>(() -> {
      endpoint.onClose(oldSession.session());
      return null;
    });
    Thread cleanupThread = Thread.ofVirtual().start(cleanup);
    assertTrue(members.emptyObserved.await(5, TimeUnit.SECONDS));

    FutureTask<Void> replacement = new FutureTask<>(() -> {
      endpoint.onOpen(replacementSession.session());
      return null;
    });
    Thread replacementThread = Thread.ofVirtual().start(replacement);
    try {
      // 断言的是连接表这一可观察状态，而不是握手的调用次数：握手鉴权（T3-5）在取锁之前完成，
      // 计数探针会在锁外就被触发，测不到「等待旧连接清理」。真正不能提前发生的是
      // 替代连接把自己发布进 webSocketClientSet —— 清理未完成前，表里必须仍是旧 Session。
      Thread.sleep(250);
      Object duringCleanup = unionTable("webSocketClientSet").get(sid);
      // onClose 先摘掉连接映射、再清理房间成员（暂停点在后者），所以此刻表里是空洞是正常的。
      // 不能发生的是：替代连接抢在清理完成前把自己发布进表 —— 缺锁时正是这个后果。
      if (duringCleanup != null) {
        assertSame(oldSession.session(), clientSession(duringCleanup),
            "替代连接必须等旧连接清理完成后才能发布自己");
      }
    } finally {
      members.resume.countDown();
    }

    cleanup.get(5, TimeUnit.SECONDS);
    replacement.get(5, TimeUnit.SECONDS);
    cleanupThread.join();
    replacementThread.join();

    Object current = unionTable("webSocketClientSet").get(sid);
    assertNotNull(current);
    assertSame(replacementSession.session(), clientSession(current));
    assertTrue(unionTable("onlineUsers").containsKey(sid));
    assertTrue(replacementSession.open.get());
  }

  @Test
  void concurrentSameSidOpensTrackExactlyOneOpenSession() throws Exception {
    String sid = "same-user";
    CountDownLatch firstLookupEntered = new CountDownLatch(1);
    CountDownLatch releaseFirstLookup = new CountDownLatch(1);
    CountDownLatch secondLookupEntered = new CountDownLatch(1);
    WebSocketUnionService endpoint = endpoint(
        new FirstLookupBlockingHandshake(firstLookupEntered, releaseFirstLookup, secondLookupEntered));
    TestSession first = session("first", sid);
    TestSession second = session("second", sid);

    FutureTask<Void> firstOpen = new FutureTask<>(() -> {
      endpoint.onOpen(first.session());
      return null;
    });
    Thread firstThread = Thread.ofVirtual().start(firstOpen);
    assertTrue(firstLookupEntered.await(5, TimeUnit.SECONDS));

    FutureTask<Void> secondOpen = new FutureTask<>(() -> {
      endpoint.onOpen(second.session());
      return null;
    });
    Thread secondThread = Thread.ofVirtual().start(secondOpen);
    secondLookupEntered.await(250, TimeUnit.MILLISECONDS);
    releaseFirstLookup.countDown();

    firstOpen.get(5, TimeUnit.SECONDS);
    secondOpen.get(5, TimeUnit.SECONDS);
    firstThread.join();
    secondThread.join();

    Object current = unionTable("webSocketClientSet").get(sid);
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
        new SignallingHandshake(Integer.MAX_VALUE, new CountDownLatch(0)));
    TestSession oldSession = session("member-old", sid);
    TestSession replacement = session("member-replacement", sid);
    TestSession watcher = session("watcher", "watcher");
    endpoint.onOpen(oldSession.session());
    endpoint.onOpen(watcher.session());
    RoomModel room = room("room-member", sid,
        new CopyOnWriteArrayList<>(List.of(user(sid))));
    unionTable("onlineRooms").put(room.getId(), room);
    watcher.outbound().clear();

    endpoint.onOpen(replacement.session());

    RoomModel mapped = (RoomModel) unionTable("onlineRooms").get(room.getId());
    assertNotNull(mapped);
    assertEquals(List.of(sid), mapped.getUsers().stream().map(UserModel::getId).toList());
    assertFalse(watcher.outbound().stream().anyMatch(message -> message.contains("\"code\":3")),
        "same-sid replacement must not broadcast USER_EXIT");
    assertSame(replacement.session(), clientSession(unionTable("webSocketClientSet").get(sid)));
    assertFalse(oldSession.open().get(), "same-sid replacement must close the displaced Session");
  }

  @Test
  void soleMemberExplicitExitRemovesRoomKey() throws Exception {
    WebSocketUnionService endpoint = endpoint(
        new SignallingHandshake(Integer.MAX_VALUE, new CountDownLatch(0)));
    TestSession owner = session("owner-session", "owner");
    endpoint.onOpen(owner.session());
    RoomModel room = room("123", "owner", new CopyOnWriteArrayList<>(List.of(user("owner"))));
    unionTable("onlineRooms").put(room.getId(), room);

    endpoint.onMessage("{\"code\":14,\"data\":123}", owner.session());

    assertFalse(unionTable("onlineRooms").containsKey("123"),
        "explicit EXIT by the sole member must remove the room key");
  }

  @Test
  void invalidCredentialsAreRejectedWithoutRegisteringOrDereferencingNull() throws Exception {
    WebSocketUnionService endpoint = endpoint(new RejectingHandshake());
    TestSession ghost = session("ghost-session", "no-such-user");

    endpoint.onOpen(ghost.session());

    assertFalse(unionTable("webSocketClientSet").containsKey("no-such-user"),
        "握手未通过的连接必须永不进入连接表");
    assertFalse(unionTable("onlineUsers").containsKey("no-such-user"),
        "握手未通过的连接必须永不出现在在线用户列表");
    assertFalse(ghost.open().get(), "the rejected connection must be closed, not left open");
    assertTrue(ghost.outbound().stream().anyMatch(message -> message.contains("登录凭据无效")),
        "the client must be told why it was rejected; outbound was " + ghost.outbound());
  }

  @Test
  void disconnectInterleavedWithJoinCannotPublishIntoDetachedRoom() throws Exception {
    WebSocketUnionService endpoint = endpoint(
        new SignallingHandshake(Integer.MAX_VALUE, new CountDownLatch(0)));
    TestSession owner = session("owner-session", "owner");
    TestSession joiner = session("joiner-session", "joiner");
    endpoint.onOpen(owner.session());
    endpoint.onOpen(joiner.session());
    PauseBeforeJoinAdd members = new PauseBeforeJoinAdd("joiner");
    members.addInitial(user("owner"));
    RoomModel room = room("456", "owner", members);
    unionTable("onlineRooms").put(room.getId(), room);

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

    RoomModel mapped = (RoomModel) unionTable("onlineRooms").get("456");
    assertNotNull(mapped, "JOIN success must not target a room holder detached from the map");
    assertEquals(List.of("joiner"), mapped.getUsers().stream().map(UserModel::getId).toList());
  }

  private static WebSocketUnionService endpoint(WebSocketHandshake handshake) {
    WebSocketUnionService endpoint = new WebSocketUnionService();
    endpoint.handshake = handshake;
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

  private static UserEntity entity(String id) {
    UserEntity entity = new UserEntity();
    entity.setId(id);
    entity.setUserName("name-" + id);
    entity.setUserImg("image-" + id);
    return entity;
  }

  /** 按握手次数发信号的桩：原用 UserDao.findUserEntityById 的次数，握手改造后计数点搬到这里。 */
  private static final class SignallingHandshake extends WebSocketHandshake {
    private final int signalCall;
    private final CountDownLatch signal;
    private final AtomicInteger calls = new AtomicInteger();

    private SignallingHandshake(int signalCall, CountDownLatch signal) {
      this.signalCall = signalCall;
      this.signal = signal;
    }

    @Override
    public UserEntity authenticate(Session session) {
      if (calls.incrementAndGet() == signalCall) {
        signal.countDown();
      }
      return entity(credentialUserId(session));
    }
  }

  /** 凭据无效：握手一律失败。 */
  private static final class RejectingHandshake extends WebSocketHandshake {
    @Override
    public UserEntity authenticate(Session session) {
      return null;
    }
  }

  private static final class FirstLookupBlockingHandshake extends WebSocketHandshake {
    private final CountDownLatch firstEntered;
    private final CountDownLatch releaseFirst;
    private final CountDownLatch secondEntered;
    private final AtomicInteger calls = new AtomicInteger();

    private FirstLookupBlockingHandshake(
        CountDownLatch firstEntered,
        CountDownLatch releaseFirst,
        CountDownLatch secondEntered) {
      this.firstEntered = firstEntered;
      this.releaseFirst = releaseFirst;
      this.secondEntered = secondEntered;
    }

    @Override
    public UserEntity authenticate(Session session) {
      if (calls.incrementAndGet() == 1) {
        firstEntered.countDown();
        await(releaseFirst);
      } else {
        secondEntered.countDown();
      }
      return entity(credentialUserId(session));
    }
  }

  /** 桩连接的 query 凭据里 token 就是用户 id，握手桩据此把身份还原出来。 */
  private static String credentialUserId(Session session) {
    return session.getRequestParameterMap().get("token").getFirst();
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

  /** 桩连接：query 凭据的 token 直接写用户 id，握手桩据此还原身份。 */
  private static TestSession session(String id, String userId) {
    AtomicBoolean open = new AtomicBoolean(true);
    List<String> outbound = new CopyOnWriteArrayList<>();
    Map<String, Object> properties = new ConcurrentHashMap<>();
    Map<String, List<String>> credentials =
        Map.of("token", List.of(userId), "deviceId", List.of("device-" + userId));
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
          case "getRequestParameterMap" -> credentials;
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
