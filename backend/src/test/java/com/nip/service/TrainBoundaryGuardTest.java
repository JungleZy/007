package com.nip.service;

import com.nip.dao.RadiotelephoneDao;
import com.nip.dao.TelegraphKeyPatTrainDao;
import com.nip.dao.TelegraphKeyTrainStatisticalDao;
import com.nip.dao.UserDao;
import com.nip.dto.RadiotelephoneDto;
import com.nip.dto.vo.RadiotelephoneVO;
import com.nip.dto.vo.TelegraphKeyPatTrainVO;
import com.nip.entity.RadiotelephoneEntity;
import com.nip.entity.TelegraphKeyPatTrainEntity;
import com.nip.entity.TelegraphKeyTrainStatisticalEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;


import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 6 边界守卫：
 * - Task 6.1 电子键清空对无统计行的新用户可用（statisticalDao.save(null) 会触发
 *   cn.hutool.core.lang.Assert.notNull 抛 IllegalArgumentException 并回滚整个清空事务）；
 * - Task 6.3 话报结算在记录缺失时懒建，累计时长脏数据不再把结算打成 500。
 */
@QuarkusTest

class TrainBoundaryGuardTest {

  @Inject UserDao userDao;
  @Inject TelegraphKeyPatTrainService telegraphKeyPatTrainService;
  @Inject TelegraphKeyPatTrainDao telegraphKeyPatTrainDao;
  @Inject TelegraphKeyTrainStatisticalDao statisticalDao;
  @Inject RadiotelephoneService radiotelephoneService;
  @Inject RadiotelephoneDao radiotelephoneDao;

  @Test
  void clearIsAvailableForUserWithoutStatisticsRow() {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    assertNull(statisticalDao.findByUserIdAndType(user.getId(), 0),
        "前置条件：新用户必须没有统计行");

    TelegraphKeyPatTrainVO vo = telegraphKeyPatTrainService.clear(token, 0);

    assertEquals(0, vo.getTotalNum().intValue(), "清空后拍发次数必须归零");
    assertEquals(0, vo.getTotalError().intValue(), "清空后错误次数必须归零");
    assertEquals(0, vo.getTotalTime().intValue(), "清空后训练时长必须归零");
    TelegraphKeyPatTrainEntity persisted =
        telegraphKeyPatTrainDao.findByCreateUserIdAndType(user.getId(), 0);
    assertNotNull(persisted, "清空必须落库到该用户该类型，而不是整个事务被回滚");
    assertEquals(0, persisted.getTotalNum().intValue());
  }

  @Test
  void clearStillZeroesExistingStatisticsRow() {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    statisticalDao.save(new TelegraphKeyTrainStatisticalEntity()
        .setUserId(user.getId())
        .setType(1)
        .setTotalCount(7)
        .setTotalTime("120"));

    telegraphKeyPatTrainService.clear(token, 1);

    assertEquals(0, statisticalDao.findByUserIdAndType(user.getId(), 1).getTotalCount().intValue(),
        "已有统计行的训练次数必须被清零并落库");
  }

  @Test
  void radiotelephoneBeginLazilyCreatesRecord() {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    RadiotelephoneDto request = new RadiotelephoneDto();
    request.setType(1);

    RadiotelephoneVO begun = radiotelephoneService.begin(request, token);

    assertNotNull(begun.getSessionId(), "开始训练必须返回服务端会话标识");
    RadiotelephoneEntity persisted = radiotelephoneDao.findByUserIdAndType(user.getId(), 1);
    assertNotNull(persisted, "开始训练必须懒建该用户该类型的统计行");
    assertEquals(0, persisted.getTotalCount().intValue());
    assertEquals("0", persisted.getTotalTime());
  }

  @Test
  void radiotelephoneFinishWithoutBeginRejectsMissingSession() {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    RadiotelephoneDto request = new RadiotelephoneDto();
    request.setType(1);

    assertThrows(IllegalArgumentException.class,
        () -> radiotelephoneService.finish(request, token),
        "未开始训练不得结算");
    assertNull(radiotelephoneDao.findByUserIdAndType(user.getId(), 1),
        "缺少会话的结算请求不得创建统计行");
  }

  @Test
  void radiotelephoneFinishRejectsMalformedHistoricalTotalWithoutDiscardingIt() {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    RadiotelephoneEntity dirty = new RadiotelephoneEntity();
    dirty.setUserId(user.getId());
    dirty.setType(0);
    dirty.setTotalCount(2);
    dirty.setTotalTime("--");
    radiotelephoneDao.saveAndFlush(dirty);

    RadiotelephoneDto beginRequest = new RadiotelephoneDto();
    beginRequest.setType(0);
    RadiotelephoneVO begun = radiotelephoneService.begin(beginRequest, token);
    RadiotelephoneDto finishRequest = new RadiotelephoneDto();
    finishRequest.setType(0);
    finishRequest.setSessionId(begun.getSessionId());

    assertThrows(com.nip.common.exception.TerminalStateException.class,
        () -> radiotelephoneService.finish(finishRequest, token));
    RadiotelephoneEntity persisted = radiotelephoneDao.findByUserIdAndType(user.getId(), 0);
    assertEquals("--", persisted.getTotalTime());
    assertEquals(2, persisted.getTotalCount());
  }
}
