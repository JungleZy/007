package com.nip.service;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.Response;
import com.nip.dao.TelexPatDao;
import com.nip.dao.TelexPatTrainStatisticalDao;
import com.nip.dao.UserDao;
import com.nip.entity.TelexPatEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Task 1.1：statisticalDao.findByUserIdAndType 查无统计行（用户从未训练过）时，
 * 原实现在 NPE 上被 catch (Exception) 吞掉 —— t_telex_pat 已删除照常提交，却返回 error 信封。
 * 统计行缺失属正常情形，必须跳过清零并返回成功。
 */
@QuarkusTest
class TelexPatDeleteRollbackTest {
  @Inject TelexPatService telexPatService;
  @Inject TelexPatDao telexPatDao;
  @Inject TelexPatTrainStatisticalDao statisticalDao;
  @Inject UserDao userDao;

  @Test
  void deleteSucceedsAndClearsPatWhenStatisticalRowMissing() {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    TelexPatEntity pat = new TelexPatEntity();
    pat.setUserId(user.getId());
    pat.setType(0);
    pat.setCount(10);
    pat.setMistake(1);
    pat.setDuration(60L);
    telexPatDao.saveAndFlush(pat);
    assertNull(statisticalDao.findByUserIdAndType(user.getId(), 0), "前置条件：该用户该类型不得有统计行");

    Response<Void> response = telexPatService.deleteTexPatByToken(token, 0);

    assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode(),
        "统计行缺失属正常情形，必须返回成功而不是被吞成 error");
    assertEquals(0, telexPatDao.find("userId = ?1 and type = ?2", user.getId(), 0).count(),
        "该用户该类型的单字训练记录必须已删除");
  }
}
