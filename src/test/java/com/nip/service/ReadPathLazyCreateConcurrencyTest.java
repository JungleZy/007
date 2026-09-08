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

/**
 * Task 6.3 读路径懒建的并发守卫。
 *
 * <p>两条读路径在查不到记录时会在读请求里补建一行：
 * {@code POST /radiotelephone/listPage}（及同口径的 {@code finish}）懒建
 * {@code t_radiotelephone_train} 的 (user_id, type) 行；{@code GET /comprehensive/getUserInfo}
 * 懒建 {@code t_theory_knowledge_test_fallible} 的 user_id 行。缺唯一约束时两个并发首调各插一行，
 * 之后 DAO 的 {@code firstResult()} 只命中其中一行，另一行成孤儿 → 计数割裂。
 *
 * <p>怎么让「双方都查不到」成为确定事件而不是抢跑概率：每个线程先自己开一个事务并读一次
 * （既断言此刻确实没有行，又固定住 MySQL REPEATABLE READ 的一致性读快照），两个线程都读完
 * 才在栅栏处放行去调被测方法 —— 被测方法的 {@code @Transactional} 是 REQUIRED，会加入线程
 * 自己这个已经固定了快照的事务，于是双方必然都走到懒建分支。
 */
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
    CyclicBarrier bothMissedTheRow = new CyclicBarrier(2);

    List<List<RadiotelephoneVO>> results = race(() -> QuarkusTransaction.requiringNew().call(() -> {
      assertNull(radiotelephoneDao.findByUserIdAndType(user.getId(), TYPE), "用例前置：该用户该类型不得已有统计行");
      bothMissedTheRow.await(30, TimeUnit.SECONDS);
      return radiotelephoneService.listPage(token, dto(TYPE, null));
    }));

    List<RadiotelephoneEntity> rows = radiotelephoneDao.findAllByUserId(user.getId());
    assertEquals(1, rows.size(), "两个并发首调只能懒建出 1 行统计记录，多出来的那行会让统计页重复显示并成为孤儿");
    for (List<RadiotelephoneVO> result : results) {
      assertEquals(1, result.stream().filter(vo -> vo.getType() == TYPE).count(),
          "每个调用都必须拿到该类型恰好一行");
      RadiotelephoneVO vo = result.getFirst();
      assertEquals(0, vo.getTotalCount().intValue(), "懒建出来的统计行训练次数是 0");
      assertEquals("0", vo.getTotalTime(), "懒建出来的统计行累计时长是 0");
    }
  }

  @Test
  void concurrentFinishAccumulatesOntoTheSameRow() throws Exception {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    CyclicBarrier bothMissedTheRow = new CyclicBarrier(2);

    race(() -> QuarkusTransaction.requiringNew().call(() -> {
      assertNull(radiotelephoneDao.findByUserIdAndType(user.getId(), TYPE), "用例前置：该用户该类型不得已有统计行");
      bothMissedTheRow.await(30, TimeUnit.SECONDS);
      return radiotelephoneService.finish(dto(TYPE, 30), token);
    }));

    List<RadiotelephoneEntity> rows = radiotelephoneDao.findAllByUserId(user.getId());
    assertEquals(1, rows.size(), "两个并发首次结算只能落 1 行统计记录");
    RadiotelephoneEntity persisted = rows.getFirst();
    assertEquals(2, persisted.getTotalCount().intValue(), "两次结算必须都累加到同一行上，否则计数割裂");
    assertEquals("60", persisted.getTotalTime(), "两次结算的时长必须都累加到同一行上");
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
      assertNotNull(result.getErrorTopic(), "读接口本身必须正常返回，不得因为幂等改造吞掉结果");
    }
    assertNotNull(testFallibleDao.findByUserId(user.getId()).getContent(), "缓存行的内容不得为空");
  }

  @Test
  void lazyCreateRejectsNullTypeInsteadOfWritingANullKeyRow() {
    String token = UUID.randomUUID().toString();
    Fixtures.user(userDao, token);

    // MySQL 唯一索引允许多个 NULL 行：放过空键等于让唯一约束形同虚设，所以必须挡在写入之前
    assertThrows(IllegalArgumentException.class, () -> radiotelephoneService.finish(dto(null, 30), token),
        "训练类型为空时不得懒建出 type 为 NULL 的统计行");
  }

  private RadiotelephoneDto dto(Integer type, Integer totalTime) {
    RadiotelephoneDto dto = new RadiotelephoneDto();
    dto.setType(type);
    dto.setTotalTime(totalTime);
    return dto;
  }

  /** 两个线程同时跑同一段逻辑，返回两边的结果 */
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

  /** ComprehensiveService 只从请求里取 token 头，用动态代理喂一个即可（先例见 WebSocketDeleteOpenAtomicityTest 的 Session 代理） */
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
