package com.nip.service;

import com.nip.common.security.SessionToken;
import com.nip.dao.UserDao;
import com.nip.dao.simulation.SimulationRouterRoomContentDao;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomPageDao;
import com.nip.dao.simulation.SimulationRouterRoomPageValueDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.dto.vo.param.simulation.router.SimulationRoomRouterContentAddParam;
import com.nip.dto.vo.simulation.SimulationRouterRoomPageInfoVO;
import com.nip.dto.vo.simulation.disturd.SimulationDisturdUploadResultVO;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomContentEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomPageValueEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
import com.nip.service.simulation.SimulationRouterRoomContentService;
import com.nip.service.simulation.SimulationRouterRoomService;
import com.nip.testsupport.Fixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SimulationPagePersistenceTest {
  @Inject SimulationRouterRoomService rooms;
  @Inject SimulationRouterRoomContentService content;
  @Inject SimulationRouterRoomDao roomDao;
  @Inject SimulationRouterRoomContentDao contentDao;
  @Inject SimulationRouterRoomPageDao pageDao;
  @Inject SimulationRouterRoomPageValueDao valueDao;
  @Inject SimulationRouterRoomUserDao memberDao;
  @Inject UserDao userDao;
  @Inject DataSource dataSource;
  private final List<Integer> createdRooms = new ArrayList<>();

  @AfterEach
  void removeRooms() {
    // 清场直接走 DAO：delete 端点现在要求「建房人 ∪ 组训位 ∪ 管理员」授权（T2-3），
    // 而 room() 造的房没有 createUserId，用业务方法清场等于让 teardown 依赖授权口径。
    createdRooms.forEach(roomId -> QuarkusTransaction.requiringNew().run(() -> {
      valueDao.delete("roomId", roomId);
      pageDao.delete("roomId", roomId);
      memberDao.delete("roomId", roomId);
      contentDao.delete("roomId", roomId);
      roomDao.deleteById(roomId);
    }));
  }

  @Test
  void concurrentFirstReadsWithOldSnapshotsReturnOneIdenticalCompletePage() throws Exception {
    int roomId = room(350);
    CyclicBarrier missed = new CyclicBarrier(2);
    List<SimulationRouterRoomPageInfoVO> results = race(() -> QuarkusTransaction.requiringNew().call(() -> {
      assertEquals(0, pageDao.count("roomId = ?1 and pageNumber = ?2", roomId, 3));
      // Establish both repeatable-read snapshots before either caller acquires the room row.
      missed.await(30, TimeUnit.SECONDS);
      return rooms.findPage("reader", roomId, 3);
    }));
    assertEquals(100, results.getFirst().getPageVos().size());
    assertEquals(results.getFirst().getPageVos(), results.getLast().getPageVos());
    assertEquals(100, pageDao.count("roomId = ?1 and pageNumber = ?2", roomId, 3));
    assertEquals(50, rooms.findPage("reader", roomId, 4).getPageVos().size());
    assertTrue(rooms.findPage("reader", roomId, 5).getPageVos().isEmpty());
    assertEquals(150, pageDao.count("roomId", roomId));
  }

  @Test
  void averageNumericCreationPersistsTwoDistinctHundredGroupPages() {
    String token = token();
    Fixtures.user(userDao, token);
    SimulationRoomRouterContentAddParam input = new SimulationRoomRouterContentAddParam();
    input.setRoomName("T11 average pages");
    input.setIsCable(0);
    input.setBwCount(350);
    input.setBwType(0);
    input.setBdType(1);
    input.setIsRandom(0);
    int roomId = content.addRoomAndContent(request(token), input);
    createdRooms.add(roomId);
    assertEquals(100, rooms.findPage("reader", roomId, 1).getPageVos().size());
    assertEquals(100, rooms.findPage("reader", roomId, 2).getPageVos().size());
    assertEquals(200, pageDao.count("roomId", roomId));
  }

  @Test
  void completeRetriesReplaceOnlyAuthenticatedUsersPagesAndRemoveOldTail() throws Exception {
    int roomId = room(100);
    String alice = participant(roomId, 1);
    String bob = participant(roomId, 1);
    List<String> three = List.of("[\"AAAA\"]", "[\"BBBB\"]", "[\"CCCC\"]");
    content.uploadResult(request(bob), answer(roomId, List.of("[\"OTHER\"]")));
    CyclicBarrier start = new CyclicBarrier(2);
    race(() -> {
      start.await(30, TimeUnit.SECONDS);
      return content.uploadResult(request(alice), answer(roomId, three));
    });
    assertEquals(three, values(roomId, alice));
    assertEquals(1, status(roomId, alice));

    // Extra answer pages remain visible with an empty standard page, never fabricated keys.
    assertEquals(List.of("CCCC"), rooms.findPage(userId(alice), roomId, 3).getValue());
    assertTrue(rooms.findPage(userId(alice), roomId, 3).getPageVos().isEmpty());
    List<String> two = List.of("[\"NEW\"]", "[]");
    content.uploadResult(request(alice), answer(roomId, two));
    content.uploadResult(request(alice), answer(roomId, two));
    assertEquals(two, values(roomId, alice));
    assertEquals(List.of("[\"OTHER\"]"), values(roomId, bob));
    assertEquals(3, valueDao.count("roomId", roomId));
    assertTrue(rooms.findPage(userId(alice), roomId, 3).getValue().isEmpty());

    content.uploadResult(request(alice), answer(roomId, List.of()));
    assertTrue(values(roomId, alice).isEmpty());
    assertEquals(List.of("[\"OTHER\"]"), values(roomId, bob));
  }

  @Test
  void databaseFailureAfterReplacementStartsRestoresAnswersAndSubmissionState() throws Exception {
    int roomId = room(300);
    String token = participant(roomId, 1);
    List<String> old = List.of("[\"OLD1\"]", "[\"OLD2\"]", "[\"OLD3\"]");
    content.uploadResult(request(token), answer(roomId, old));
    QuarkusTransaction.requiringNew().run(() -> memberDao.update(
        "userStatus = 0 where roomId = ?1 and userId = ?2", roomId, userId(token)));
    String constraint = "t11_reject_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
      statement.execute("ALTER TABLE simulation_router_room_page_value ADD CONSTRAINT " + constraint
          + " CHECK (room_id <> " + roomId + " OR `value` <> '[\"FAIL\"]')");
      try {
        assertThrows(RuntimeException.class, () -> content.uploadResult(request(token),
            answer(roomId, List.of("[\"NEW\"]", "[\"FAIL\"]"))));
      } finally {
        statement.execute("ALTER TABLE simulation_router_room_page_value DROP CHECK " + constraint);
      }
    }
    assertEquals(old, values(roomId, token));
    assertEquals(0, status(roomId, token));
  }

  @Test
  void onlyEnrolledReceiversCanWriteAndMalformedRequestsLeaveSavedAnswersUntouched() {
    int roomId = room(100);
    String receiver = participant(roomId, 1);
    String sender = participant(roomId, 0);
    String outsider = token();
    Fixtures.user(userDao, outsider);
    List<String> saved = List.of("[\"SAVED\"]");
    content.uploadResult(request(receiver), answer(roomId, saved));
    assertThrows(IllegalArgumentException.class,
        () -> content.uploadResult(request(sender), answer(roomId, saved)));
    assertThrows(IllegalArgumentException.class,
        () -> content.uploadResult(request(outsider), answer(roomId, saved)));
    assertThrows(IllegalArgumentException.class,
        () -> content.uploadResult(request(receiver), answer(roomId, null)));
    assertThrows(IllegalArgumentException.class,
        () -> content.uploadResult(request(receiver), answer(roomId, List.of("[1]"))));
    QuarkusTransaction.requiringNew().run(() -> roomDao.update("stats = 0 where id = ?1", roomId));
    assertThrows(IllegalArgumentException.class,
        () -> content.uploadResult(request(receiver), answer(roomId, List.of())));
    assertEquals(saved, values(roomId, receiver));
    assertEquals(1, valueDao.count("roomId", roomId));
  }

  @Test
  void missingCablePageIsNotReplacedWithRandomStandardContent() {
    int roomId = room(100);
    QuarkusTransaction.requiringNew().run(() -> roomDao.update("isCable = 1 where id = ?1", roomId));
    assertTrue(rooms.findPage("reader", roomId, 1).getPageVos().isEmpty());
    assertEquals(0, pageDao.count("roomId", roomId));
  }

  private int room(int groups) {
    int roomId = QuarkusTransaction.requiringNew().call(() -> {
      SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
      room.setName("T11 room");
      room.setIsCable(0);
      room.setRoomType(1);
      room.setStats(2);
      roomDao.save(room);
      SimulationRouterRoomContentEntity body = new SimulationRouterRoomContentEntity();
      body.setRoomId(room.getId());
      body.setBwCount(groups);
      body.setBwType(0);
      body.setBdType(0);
      body.setIsRandom(1);
      contentDao.save(body);
      return room.getId();
    });
    createdRooms.add(roomId);
    return roomId;
  }

  private String participant(int roomId, int type) {
    String token = token();
    UserEntity user = Fixtures.user(userDao, token);
    QuarkusTransaction.requiringNew().run(() -> {
      SimulationRouterRoomUserEntity member = new SimulationRouterRoomUserEntity();
      member.setRoomId(roomId);
      member.setUserId(user.getId());
      member.setUserType(type);
      member.setUserStatus(0);
      memberDao.save(member);
    });
    return token;
  }

  private List<String> values(int roomId, String token) {
    return QuarkusTransaction.requiringNew().call(() -> valueDao
        .find("roomId = ?1 and userId = ?2 order by pageNumber", roomId, userId(token))
        .list().stream().map(SimulationRouterRoomPageValueEntity::getValue).toList());
  }

  private Integer status(int roomId, String token) {
    return QuarkusTransaction.requiringNew().call(() ->
        memberDao.findByUserIdAndRoomId(userId(token), roomId).getUserStatus());
  }

  private String userId(String token) {
    // t_user.token 存的是摘要（T3-1），按明文查不到人。
    return userDao.find("token", SessionToken.hash(token)).singleResult().getId();
  }

  private static String token() {
    return "t11-" + UUID.randomUUID();
  }

  private static SimulationDisturdUploadResultVO answer(int roomId, List<String> pages) {
    SimulationDisturdUploadResultVO input = new SimulationDisturdUploadResultVO();
    input.setRoomId(roomId);
    input.setContentValue(pages);
    return input;
  }

  private static HttpServerRequest request(String token) {
    return (HttpServerRequest) Proxy.newProxyInstance(HttpServerRequest.class.getClassLoader(),
        new Class<?>[]{HttpServerRequest.class}, (proxy, method, args) -> switch (method.getName()) {
          case "getHeader" -> token;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          default -> null;
        });
  }

  private static <T> List<T> race(Callable<T> operation) throws Exception {
    FutureTask<T> first = new FutureTask<>(operation);
    FutureTask<T> second = new FutureTask<>(operation);
    Thread a = new Thread(first, "t11-first");
    Thread b = new Thread(second, "t11-second");
    a.start();
    b.start();
    try {
      return List.of(first.get(60, TimeUnit.SECONDS), second.get(60, TimeUnit.SECONDS));
    } finally {
      a.join(TimeUnit.SECONDS.toMillis(5));
      b.join(TimeUnit.SECONDS.toMillis(5));
    }
  }
}
