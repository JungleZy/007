package com.nip.service;

import com.nip.dao.RadiotelephoneDao;
import com.nip.dao.TelegraphKeyPatSyntheticalDao;
import com.nip.dao.UserDao;
import com.nip.dto.RadiotelephoneDto;
import com.nip.dto.TelegraphKeyPatSyntheticalDto;
import com.nip.dto.vo.RadiotelephoneVO;
import com.nip.entity.TelegraphKeyPatSyntheticalEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class AggregateScoringBoundaryTest {
  @Inject UserDao userDao;
  @Inject TelegraphKeyPatSyntheticalDao keyDao;
  @Inject TelegraphKeyPatSyntheticalService keyService;
  @Inject RadiotelephoneService radioService;
  @Inject RadiotelephoneDao radioDao;

  @Test
  void comprehensiveSaveCannotTakeOverExistingId() {
    UserEntity owner = Fixtures.user(userDao, "key-owner");
    UserEntity attacker = Fixtures.user(userDao, "key-attacker");
    TelegraphKeyPatSyntheticalEntity row = new TelegraphKeyPatSyntheticalEntity();
    row.setTitle("original");
    row.setCreateUserId(owner.getId());
    row.setStatus(0);
    row = keyDao.saveAndFlush(row);
    TelegraphKeyPatSyntheticalDto request = new TelegraphKeyPatSyntheticalDto();
    request.setId(row.getId());
    request.setTitle("stolen");
    assertThrows(com.nip.common.exception.ForbiddenException.class, () -> keyService.save(attacker.getToken(), request));
    assertEquals("original", keyDao.findById(row.getId()).getTitle());
  }

  @Test
  void radiotelephoneUsesServerSessionForFinish() {
    UserEntity user = Fixtures.user(userDao, "radio-boundary");
    RadiotelephoneDto missingSession = new RadiotelephoneDto();
    missingSession.setType(0);

    assertThrows(IllegalArgumentException.class,
        () -> radioService.finish(missingSession, user.getToken()),
        "没有 begin 返回的会话时，finish 必须返回参数错误");
    assertEquals(0, radioDao.findAllByUserId(user.getId()).size(),
        "缺少会话的请求不得改变历史统计");

    RadiotelephoneDto beginRequest = new RadiotelephoneDto();
    beginRequest.setType(0);
    String sessionId = radioService.begin(beginRequest, user.getToken()).getSessionId();

    RadiotelephoneDto finishRequest = new RadiotelephoneDto();
    finishRequest.setType(0);
    finishRequest.setSessionId(sessionId);
    RadiotelephoneVO finished = radioService.finish(finishRequest, user.getToken());

    assertEquals(1, finished.getTotalCount().intValue(), "服务端会话只能结算一次");
    assertEquals("0", finished.getTotalTime(), "立即结束的服务端时钟累计为 0 秒");
  }
}
