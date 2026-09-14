package com.nip.service;

import com.nip.common.exception.ForbiddenException;
import com.nip.common.exception.TerminalStateException;
import com.nip.dao.RadiotelephoneDao;
import com.nip.dao.UserDao;
import com.nip.dto.RadiotelephoneDto;
import com.nip.dto.vo.RadiotelephoneVO;
import com.nip.entity.RadiotelephoneEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for the persisted, server-authoritative radio study clock. */
@QuarkusTest
class RadiotelephoneClockTest {

  @Inject UserDao userDao;
  @Inject RadiotelephoneDao radioDao;
  @Inject RadiotelephoneService radioService;

  @Test
  void ordinaryBeginAndFinishPersistsOneCompletedSession() {
    UserEntity user = Fixtures.user(userDao, UUID.randomUUID().toString());

    RadiotelephoneVO begun = radioService.begin(request(0), user.getToken());
    RadiotelephoneVO finished = radioService.finish(request(0, begun.getSessionId()), user.getToken());

    assertNotNull(begun.getSessionId());
    assertEquals(1, finished.getTotalCount().intValue());
    assertEquals("0", finished.getTotalTime());
    RadiotelephoneEntity row = radioDao.findByUserIdAndType(user.getId(), 0);
    assertNull(row.getActiveSessionId());
    assertEquals(begun.getSessionId(), row.getFinalizedSessionId());
  }

  @Test
  void finishUsesServerElapsedClockAndIgnoresClientOnlyFields() {
    UserEntity user = Fixtures.user(userDao, UUID.randomUUID().toString());
    RadiotelephoneVO begun = radioService.begin(request(0), user.getToken());
    RadiotelephoneEntity row = radioDao.findByUserIdAndType(user.getId(), 0);
    row.setSessionStartedAt(Instant.now().minusMillis(2_200));
    row.setActiveMillis(900L);
    row.setTotalTime("41");
    radioDao.saveAndFlush(row);

    RadiotelephoneVO finished = radioService.finish(request(0, begun.getSessionId()), user.getToken());

    long seconds = Long.parseLong(finished.getTotalTime()) - 41L;
    assertTrue(seconds >= 3 && seconds <= 4,
        "累计时长必须由服务端 activeMillis + start time 计算");
  }

  @Test
  void pausedIntervalIsExcludedFromFinalDuration() {
    UserEntity user = Fixtures.user(userDao, UUID.randomUUID().toString());
    RadiotelephoneVO begun = radioService.begin(request(1), user.getToken());
    RadiotelephoneEntity running = radioDao.findByUserIdAndType(user.getId(), 1);
    running.setSessionStartedAt(Instant.now().minusMillis(2_200));
    running.setActiveMillis(0L);
    radioDao.saveAndFlush(running);

    RadiotelephoneVO paused = radioService.pause(request(1, begun.getSessionId()), user.getToken());
    RadiotelephoneEntity pausedRow = radioDao.findByUserIdAndType(user.getId(), 1);
    long capturedMillis = pausedRow.getActiveMillis();
    assertNull(pausedRow.getSessionStartedAt(), "暂停后不得继续累加当前活动区间");
    assertEquals(begun.getSessionId(), paused.getSessionId());

    RadiotelephoneVO finished = radioService.finish(request(1, begun.getSessionId()), user.getToken());

    assertEquals(capturedMillis / 1_000L, Long.parseLong(finished.getTotalTime()),
        "暂停后的等待时间不得进入服务端累计时长");
  }

  @Test
  void replayedOldSessionCannotIncrementNewSession() {
    UserEntity user = Fixtures.user(userDao, UUID.randomUUID().toString());
    RadiotelephoneVO old = radioService.begin(request(0), user.getToken());
    radioService.finish(request(0, old.getSessionId()), user.getToken());
    RadiotelephoneVO current = radioService.begin(request(0), user.getToken());
    assertNotEquals(old.getSessionId(), current.getSessionId());

    RadiotelephoneVO replay = radioService.finish(request(0, old.getSessionId()), user.getToken());
    assertEquals(1, replay.getTotalCount().intValue(), "旧会话重放不得抢占当前会话");

    RadiotelephoneVO finishedCurrent = radioService.finish(request(0, current.getSessionId()), user.getToken());
    assertEquals(2, finishedCurrent.getTotalCount().intValue());
  }

  @Test
  void foreignSessionIsForbiddenWithoutChangingEitherUser() {
    UserEntity owner = Fixtures.user(userDao, UUID.randomUUID().toString());
    UserEntity attacker = Fixtures.user(userDao, UUID.randomUUID().toString());
    RadiotelephoneVO ownerSession = radioService.begin(request(0), owner.getToken());

    assertThrows(ForbiddenException.class,
        () -> radioService.finish(request(0, ownerSession.getSessionId()), attacker.getToken()));

    assertEquals(0, radioDao.findAllByUserId(attacker.getId()).size(),
        "越权会话不得为攻击者创建统计行");
    assertEquals(0, radioDao.findByUserIdAndType(owner.getId(), 0).getTotalCount().intValue(),
        "越权请求不得改变会话所有者统计");
  }

  @Test
  void unknownSessionIsTerminalAndDoesNotCreateRow() {
    UserEntity user = Fixtures.user(userDao, UUID.randomUUID().toString());

    assertThrows(TerminalStateException.class,
        () -> radioService.finish(request(1, UUID.randomUUID().toString()), user.getToken()));
    assertEquals(0, radioDao.findAllByUserId(user.getId()).size());
  }

  private RadiotelephoneDto request(Integer type) {
    return request(type, null);
  }

  private RadiotelephoneDto request(Integer type, String sessionId) {
    RadiotelephoneDto request = new RadiotelephoneDto();
    request.setType(type);
    request.setSessionId(sessionId);
    return request;
  }
}
