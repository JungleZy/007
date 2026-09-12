package com.nip.ws;

import com.nip.common.constants.SimulationRoomTypeEnum;
import com.nip.dao.UserDao;
import com.nip.dao.general.key.GeneralKeyPatDao;
import com.nip.dao.general.telex.GeneralTelexPatDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainDao;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.dto.SimulationRouterRoomUserSimpDto;
import com.nip.dto.general.GeneralPatTrainUserDto;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.entity.UserEntity;
import com.nip.dto.general.GeneralPatTrainRoomUserDto;
import com.nip.dto.general.GeneralPatTrainUserModelDto;
import com.nip.service.general.GeneralKeyPatService;
import com.nip.entity.simulation.key.GeneralKeyPatEntity;
import com.nip.entity.simulation.telex.GeneralTelexPatEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainEntity;
import com.nip.service.general.GeneralTelexPatService;
import com.nip.service.general.GeneralTickerPatService;
import com.nip.testsupport.WebSocketStateReset;
import com.nip.testsupport.Fixtures;

import com.nip.ws.service.RoomLifecycleLocks;
import com.nip.service.simulation.SimulationRouterRoomService;
import com.nip.ws.service.simulation.SimulationGlobal;
import com.nip.ws.model.GeneralTickerPatTrainRoomUserModel;
import com.nip.ws.model.GeneralTickerPatTrainUserModel;
import com.nip.ws.model.SimulationSessionHolder;
import com.nip.ws.model.SimulationUserModel;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest

class WebSocketDeleteOpenAtomicityTest {

  @Inject
  SimulationRouterRoomDao simulationRoomDao;
  @Inject
  SimulationRouterRoomService simulationDeleteService;
  @Inject
  GeneralKeyPatDao keyDao;
  @Inject
  GeneralKeyPatService keyDeleteService;
  @Inject
  GeneralTelexPatDao telexDao;
  @Inject
  GeneralTelexPatService telexDeleteService;
  @Inject
  GeneralTickerPatTrainDao tickerDao;
  @Inject
  GeneralTickerPatService tickerDeleteService;
  @Inject
  UserDao userDao;

  @AfterEach
  void clearRooms() {
    WebSocketStateReset.clearAll();
  }

  @Test
  void simulationDeleteAfterValidationCannotLeaveGhostRoom() throws Exception {
    ValidationBarrier barrier = new ValidationBarrier();
    WebSocketSimulationService endpoint = new WebSocketSimulationService();
    endpoint.roomUserDao = new FixedSimulationUserDao();
    endpoint.roomDao = new BlockingSimulationRoomDao(barrier);
    endpoint.handshake = new FixedHandshake("student");
    SessionProbe probe = session("simulation");

    assertDeleteWinsAfterValidation(
        () -> endpoint.onOpen(probe.session(), "301"),
        barrier,
        RoomLifecycleLocks.simulationRoom(301),
        () -> SimulationGlobal.routerRoom.remove(301),
        () -> SimulationGlobal.routerRoom.containsKey(301),
        probe);
  }

  @Test
  void keyDeleteAfterValidationCannotLeaveGhostRoom() throws Exception {
    ValidationBarrier barrier = new ValidationBarrier();
    WebSocketGeneralKeyPatService endpoint = new WebSocketGeneralKeyPatService();
    endpoint.generalKeyPatService = new BlockingKeyService(barrier);
    endpoint.handshake = new FixedHandshake("student");
    SessionProbe probe = session("key");

    assertDeleteWinsAfterValidation(
        () -> endpoint.onOpen("302", probe.session()),
        barrier,
        RoomLifecycleLocks.generalKeyRoom(302),
        () -> WebSocketGeneralKeyPatService.ROOM.remove(302),
        () -> WebSocketGeneralKeyPatService.ROOM.containsKey(302),
        probe);
  }

  @Test
  void telexDeleteAfterValidationCannotLeaveGhostRoom() throws Exception {
    ValidationBarrier barrier = new ValidationBarrier();
    WebSocketGeneralTelexPatService endpoint = new WebSocketGeneralTelexPatService();
    endpoint.generalTelexPatService = new BlockingTelexService(barrier);
    endpoint.handshake = new FixedHandshake("student");
    SessionProbe probe = session("telex");

    assertDeleteWinsAfterValidation(
        () -> endpoint.onOpen("train-303", probe.session()),
        barrier,
        RoomLifecycleLocks.generalTelexRoom("train-303"),
        () -> WebSocketGeneralTelexPatService.ROOM.remove("train-303"),
        () -> WebSocketGeneralTelexPatService.ROOM.containsKey("train-303"),
        probe);
  }

  @Test
  void tickerDeleteAfterValidationCannotLeaveGhostRoom() throws Exception {
    ValidationBarrier barrier = new ValidationBarrier();
    WebSocketGeneralTickerPatService endpoint = new WebSocketGeneralTickerPatService();
    endpoint.generalTickerPatService = new BlockingTickerService(barrier);
    endpoint.handshake = new FixedHandshake("student");
    SessionProbe probe = session("ticker");

    assertDeleteWinsAfterValidation(
        () -> endpoint.onOpen("304", "0", probe.session()),
        barrier,
        RoomLifecycleLocks.generalTickerRoom(304),
        () -> WebSocketGeneralTickerPatService.PAT_ROOM.remove(304),
        () -> WebSocketGeneralTickerPatService.PAT_ROOM.containsKey(304),
        probe);
  }


  @Test
  void productionDeleteFacadesRemoveCommittedRowsMapsAndSessions() {
    UserEntity owner = Fixtures.user(userDao, "delete-facade-" + UUID.randomUUID());
    SimulationRouterRoomEntity simulation = simulationRoomDao.save(new SimulationRouterRoomEntity()
        .setName("delete-race-simulation")
        .setRoomType(2)
        .setCreateUserId(owner.getId())
        .setStats(0));
    GeneralKeyPatEntity key = keyDao.save(new GeneralKeyPatEntity()
        .setTitle("delete-race-key")
        .setCreateUser(owner.getId()));
    GeneralTelexPatEntity telex = telexDao.save(new GeneralTelexPatEntity().setTitle("delete-race-telex")
        .setCreateUser(owner.getId()));
    GeneralTickerPatTrainEntity ticker = tickerDao.save(
        new GeneralTickerPatTrainEntity()
            .setName("delete-race-ticker")
            .setCreateUser(owner.getId()));

    SessionProbe simulationSession = session("delete-simulation");
    SimulationUserModel simulationUser = new SimulationUserModel();
    simulationUser.setId("student");
    SimulationGlobal.routerRoom.put(simulation.getId(), new CopyOnWriteArrayList<>(List.of(
        new SimulationSessionHolder(simulationSession.session(), simulationUser))));

    SessionProbe keySession = session("delete-key");
    WebSocketGeneralKeyPatService.ROOM.put(key.getId(), generalRoom(keySession.session()));

    SessionProbe telexSession = session("delete-telex");
    WebSocketGeneralTelexPatService.ROOM.put(telex.getId(), generalRoom(telexSession.session()));

    SessionProbe tickerSession = session("delete-ticker");
    GeneralTickerPatTrainRoomUserModel tickerRoom = new GeneralTickerPatTrainRoomUserModel();
    GeneralTickerPatTrainUserModel tickerUser = new GeneralTickerPatTrainUserModel();
    tickerUser.setId("student");
    tickerUser.setSession(tickerSession.session());
    tickerRoom.getJoinUser().add(tickerUser);
    WebSocketGeneralTickerPatService.PAT_ROOM.put(ticker.getId(), tickerRoom);

    assertAll(
        () -> assertTrue(simulationDeleteService.delete(simulation.getId(), owner.getToken())),
        () -> assertTrue(keyDeleteService.delete(key.getId(), owner.getToken())),
        () -> assertTrue(telexDeleteService.delete(telex.getId(), owner.getToken())),
        () -> assertTrue(tickerDeleteService.delete(ticker.getId(), owner.getToken())));

    assertAll(
        () -> assertNull(simulationRoomDao.findById(simulation.getId())),
        () -> assertNull(keyDao.findById(key.getId())),
        () -> assertNull(telexDao.findById(telex.getId())),
        () -> assertNull(tickerDao.findById(ticker.getId())),
        () -> assertFalse(SimulationGlobal.routerRoom.containsKey(simulation.getId())),
        () -> assertFalse(WebSocketGeneralKeyPatService.ROOM.containsKey(key.getId())),
        () -> assertFalse(WebSocketGeneralTelexPatService.ROOM.containsKey(telex.getId())),
        () -> assertFalse(WebSocketGeneralTickerPatService.PAT_ROOM.containsKey(ticker.getId())),
        () -> assertFalse(simulationSession.open().get()),
        () -> assertFalse(keySession.open().get()),
        () -> assertFalse(telexSession.open().get()),
        () -> assertFalse(tickerSession.open().get()));
  }

  private static GeneralPatTrainRoomUserDto generalRoom(Session session) {
    GeneralPatTrainUserModelDto user = new GeneralPatTrainUserModelDto();
    user.setId("student");
    user.setSession(session);
    GeneralPatTrainRoomUserDto room = new GeneralPatTrainRoomUserDto();
    room.getJoinUser().add(user);
    return room;
  }
  private static void assertDeleteWinsAfterValidation(
      ThrowingRunnable open,
      ValidationBarrier barrier,
      Lock roomLock,
      Runnable removeRoom,
      BooleanSupplier roomPresent,
      SessionProbe probe) throws Exception {
    FutureTask<Void> opening = new FutureTask<>(() -> {
      open.run();
      return null;
    });
    Thread openThread = Thread.ofVirtual().start(opening);
    assertTrue(barrier.validated.await(5, TimeUnit.SECONDS));

    // delete 线程到达「获取房间锁」这一点的确定性同步点：countDown 紧接 lock()，
    // 而 onOpen 此刻仍停在校验屏障上并持有同一把锁，delete 必然阻塞于此，
    // 无需依赖任何超时窗口。
    CountDownLatch deleteAtLock = new CountDownLatch(1);
    AtomicBoolean roomPresentAtRemoval = new AtomicBoolean();
    FutureTask<Void> deleting = new FutureTask<>(() -> {
      deleteAtLock.countDown();
      roomLock.lock();
      try {
        // 因果观测点：拿到锁时房间必须已被 onOpen 注册。若 delete 抢在注册之前
        // 拿到锁，它就是空跑，注册后的房间会永久残留（幽灵房间）。
        roomPresentAtRemoval.set(roomPresent.getAsBoolean());
        removeRoom.run();
        probe.close();
      } finally {
        roomLock.unlock();
      }
      return null;
    });
    Thread deleteThread = Thread.ofVirtual().start(deleting);
    assertTrue(deleteAtLock.await(5, TimeUnit.SECONDS), "delete 线程必须先到达房间锁获取点");
    barrier.resumeRegistration.countDown();

    opening.get(5, TimeUnit.SECONDS);
    deleting.get(5, TimeUnit.SECONDS);
    openThread.join();
    deleteThread.join();

    assertTrue(roomPresentAtRemoval.get(),
        "delete must wait for validation plus map registration under the shared lock");
    assertFalse(roomPresent.getAsBoolean(), "successful delete must leave no room map key");
    assertFalse(probe.open().get(), "successful delete must leave no live Session");
  }


  private static GeneralPatTrainUserDto user() {
    GeneralPatTrainUserDto user = new GeneralPatTrainUserDto();
    user.setId("student");
    user.setUserName("student");
    user.setUserImg("image");
    user.setRole(0);
    return user;
  }

  private static final class ValidationBarrier {
    private final CountDownLatch validated = new CountDownLatch(1);
    private final CountDownLatch resumeRegistration = new CountDownLatch(1);

    private void pause() {
      validated.countDown();
      await(resumeRegistration);
    }
  }

  private static final class BlockingSimulationRoomDao extends SimulationRouterRoomDao {
    private final ValidationBarrier barrier;

    private BlockingSimulationRoomDao(ValidationBarrier barrier) {
      this.barrier = barrier;
    }

    @Override
    public Optional<SimulationRouterRoomEntity> findByIdOptional(Integer id) {
      SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
      room.setId(id);
      // roomType 必须与断言的房表一致：ROUTER(0) → SimulationGlobal.routerRoom（本用例断言的那张表）。
      // 若用 REPORT(2)，openLocked 会注册到 reportRoom，routerRoom 恒空，因果断言必然失败。
      room.setRoomType(SimulationRoomTypeEnum.ROUTER.getType());
      room.setCreateUserId("teacher");
      barrier.pause();
      return Optional.of(room);
    }
  }

  private static final class FixedSimulationUserDao extends SimulationRouterRoomUserDao {
    @Override
    public SimulationRouterRoomUserSimpDto findByUserIdAndRoomId2Map(String userId, Integer roomId) {
      SimulationRouterRoomUserSimpDto user = new SimulationRouterRoomUserSimpDto();
      user.setId(userId);
      user.setName(userId);
      user.setChannel(1);
      return user;
    }
  }

  private static final class BlockingKeyService extends GeneralKeyPatService {
    private final ValidationBarrier barrier;

    private BlockingKeyService(ValidationBarrier barrier) {
      super(null, null, null, null, null, null, null, null, null, null);
      this.barrier = barrier;
    }

    @Override
    public GeneralPatTrainUserDto getTrainUserInfo(String uid, Integer trainId) {
      barrier.pause();
      return user();
    }
  }

  private static final class BlockingTelexService extends GeneralTelexPatService {
    private final ValidationBarrier barrier;

    private BlockingTelexService(ValidationBarrier barrier) {
      super(null, null, null, null, null, null, null);
      this.barrier = barrier;
    }

    @Override
    public GeneralPatTrainUserDto getTrainUserInfo(String uid, String trainId) {
      barrier.pause();
      return user();
    }
  }

  private static final class BlockingTickerService extends GeneralTickerPatService {
    private final ValidationBarrier barrier;

    private BlockingTickerService(ValidationBarrier barrier) {
      super(null, null, null, null, null, null, null, null);
      this.barrier = barrier;
    }

    @Override
    public GeneralPatTrainUserDto getTrainUserInfo(String uid, Integer trainId) {
      barrier.pause();
      return user();
    }
  }

  /**
   * 固定身份的握手桩：本用例守的是「校验通过之后」的注册/删除因果，
   * 凭据解析本身不在其射程内，所以直接把握手结果钉成同一个用户。
   */
  private static final class FixedHandshake extends WebSocketHandshake {
    private final String userId;

    private FixedHandshake(String userId) {
      this.userId = userId;
    }

    @Override
    public UserEntity authenticate(Session session) {
      UserEntity user = new UserEntity();
      user.setId(userId);
      return user;
    }
  }

  private record SessionProbe(Session session, AtomicBoolean open) {
    private void close() throws Exception {
      session.close();
    }
  }

  private static SessionProbe session(String id) {
    AtomicBoolean open = new AtomicBoolean(true);
    Map<String, Object> properties = new ConcurrentHashMap<>();
    RemoteEndpoint.Async async = remote(RemoteEndpoint.Async.class);
    RemoteEndpoint.Basic basic = remote(RemoteEndpoint.Basic.class);
    Session session = (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(),
        new Class<?>[]{Session.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getId" -> id;
          case "isOpen" -> open.get();
          case "getAsyncRemote" -> async;
          case "getBasicRemote" -> basic;
          case "getUserProperties" -> properties;
          case "close" -> {
            open.set(false);
            yield null;
          }
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          default -> defaultValue(method.getReturnType());
        });
    return new SessionProbe(session, open);
  }

  private static <T> T remote(Class<T> type) {
    return type.cast(Proxy.newProxyInstance(
        type.getClassLoader(),
        new Class<?>[]{type},
        (proxy, method, args) -> defaultValue(method.getReturnType())));
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
    if (!type.isPrimitive()) return null;
    if (type == boolean.class) return false;
    if (type == char.class) return '\0';
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0F;
    return 0D;
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface BooleanSupplier {
    boolean getAsBoolean();
  }
}
