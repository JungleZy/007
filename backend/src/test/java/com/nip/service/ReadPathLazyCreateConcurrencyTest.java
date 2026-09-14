package com.nip.service;

import com.nip.dao.RadiotelephoneDao;
import com.nip.dao.TheoryKnowledgeTestFallibleDao;
import com.nip.dao.UserDao;
import com.nip.dto.RadiotelephoneDto;
import com.nip.dto.vo.ComprehensiveVO;
import com.nip.dto.vo.RadiotelephoneVO;
import com.nip.entity.RadiotelephoneEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Concurrency guards for read-path lazy creation and persisted radio sessions. */
@QuarkusTest
class ReadPathLazyCreateConcurrencyTest {

  private static final int TYPE = 1;

  @Inject UserDao userDao;
  @Inject RadiotelephoneDao radiotelephoneDao;
  @Inject RadiotelephoneService radiotelephoneService;
  @Inject TheoryKnowledgeTestFallibleDao testFallibleDao;
  @Inject ComprehensiveService comprehensiveService;

  @Test
  void concurrentListPageFirstCallsCreateExactlyOneStatisticsRow() throws Exception {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    CyclicBarrier ready = new CyclicBarrier(2);

    List<List<RadiotelephoneVO>> results = race(() -> {
      ready.await(30, TimeUnit.SECONDS);
      return radiotelephoneService.listPage(token, dto(TYPE));
    });

    List<RadiotelephoneEntity> rows = radiotelephoneDao.findAllByUserId(user.getId());
    assertEquals(1, rows.size(), "两个并发首调只能懒建出 1 行统计记录");
    for (List<RadiotelephoneVO> result : results) {
      assertEquals(1, result.stream().filter(vo -> vo.getType() == TYPE).count(),
          "每个调用都必须拿到该类型恰好一行");
      RadiotelephoneVO vo = result.stream().filter(item -> item.getType() == TYPE).findFirst().orElseThrow();
      assertEquals(0, vo.getTotalCount().intValue(), "懒建出来的统计行训练次数是 0");
      assertEquals("0", vo.getTotalTime(), "懒建出来的统计行累计时长是 0");
    }
  }

  @Test
  void concurrentBeginReturnsOnePersistedSession() throws Exception {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    CyclicBarrier ready = new CyclicBarrier(2);

    List<RadiotelephoneVO> results = race(() -> {
      ready.await(30, TimeUnit.SECONDS);
      return radiotelephoneService.begin(dto(TYPE), token);
    });

    assertNotNull(results.get(0).getSessionId());
    assertEquals(results.get(0).getSessionId(), results.get(1).getSessionId(),
        "重复 begin 必须复用同一服务端会话");
    assertEquals(1, radiotelephoneDao.findAllByUserId(user.getId()).size());
    assertEquals(0, radiotelephoneDao.findByUserIdAndType(user.getId(), TYPE).getTotalCount().intValue());
  }

  @Test
  void concurrentFinishReplayAccumulatesOnce() throws Exception {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    String sessionId = radiotelephoneService.begin(dto(TYPE), token).getSessionId();
    CyclicBarrier ready = new CyclicBarrier(2);

    race(() -> {
      ready.await(30, TimeUnit.SECONDS);
      return radiotelephoneService.finish(dto(TYPE, sessionId), token);
    });

    RadiotelephoneEntity persisted = radiotelephoneDao.findByUserIdAndType(user.getId(), TYPE);
    assertEquals(1, persisted.getTotalCount().intValue(), "同一会话并发结算只能记 1 次");
    assertEquals("0", persisted.getTotalTime(), "测试立即结束时服务端累计时长应为 0 秒");
  }

  @Test
  void concurrentGetUserInfoCachesExactlyOneFallibleRow() throws Exception {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    HttpServerRequest request = requestWithToken(token);
    CyclicBarrier bothMissedTheRow = new CyclicBarrier(2);

    List<ComprehensiveVO> results = race(() -> QuarkusTransaction.requiringNew().call(() -> {
      assertNull(testFallibleDao.findByUserId(user.getId()), "用例前置：该用户不得已有易错题缓存行");
      bothMissedTheRow.await(30, TimeUnit.SECONDS);
      return comprehensiveService.getUserOverallInfo(request);
    }));

    long rows = QuarkusTransaction.requiringNew()
        .call(() -> testFallibleDao.find("userId", user.getId()).count());
    assertEquals(1L, rows, "两个并发 GET 只能缓存出 1 行易错题记录");
    for (ComprehensiveVO result : results) {
      assertNotNull(result.getErrorTopic(), "读接口本身必须正常返回");
    }
    assertNotNull(testFallibleDao.findByUserId(user.getId()).getContent(), "缓存行的内容不得为空");
  }

  @Test
  void lazyCreateRejectsNullTypeInsteadOfWritingANullKeyRow() {
    String token = UUID.randomUUID().toString();
    Fixtures.user(userDao, token);

    assertThrows(IllegalArgumentException.class, () -> radiotelephoneService.finish(dto(null), token),
        "训练类型为空时不得懒建出 type 为 NULL 的统计行");
  }

  private RadiotelephoneDto dto(Integer type, String sessionId) {
    RadiotelephoneDto dto = new RadiotelephoneDto();
    dto.setType(type);
    dto.setSessionId(sessionId);
    return dto;
  }

  private RadiotelephoneDto dto(Integer type) {
    return dto(type, null);
  }

  /** Runs the same operation on two threads and returns both results. */
  private <T> List<T> race(Callable<T> attempt) throws Exception {
    FutureTask<T> first = new FutureTask<>(attempt);
    FutureTask<T> second = new FutureTask<>(attempt);
    Thread firstThread = new Thread(first, "lazy-create-race-1");
    Thread secondThread = new Thread(second, "lazy-create-race-2");
    firstThread.start();
    secondThread.start();
    try {
      return List.of(first.get(60, TimeUnit.SECONDS), second.get(60, TimeUnit.SECONDS));
    } finally {
      firstThread.join(TimeUnit.SECONDS.toMillis(5));
      secondThread.join(TimeUnit.SECONDS.toMillis(5));
    }
  }

  /** ComprehensiveService reads only the token header, so a dynamic proxy is sufficient. */
  private HttpServerRequest requestWithToken(String token) {
    return (HttpServerRequest) Proxy.newProxyInstance(
        HttpServerRequest.class.getClassLoader(),
        new Class<?>[]{HttpServerRequest.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getHeader" -> token;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "HttpServerRequest(token=" + token + ")";
          default -> null;
        });
  }
}
