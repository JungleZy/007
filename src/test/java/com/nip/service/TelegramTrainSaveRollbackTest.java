package com.nip.service;

import com.nip.dao.TelegramTrainDao;
import com.nip.dao.TelegramTrainStatisticalDao;
import com.nip.dao.UserDao;
import com.nip.dto.TelegramTrainDto;
import com.nip.entity.TelegramTrainEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task 1.2：save 原来先把上一次暂停训练置为已完成并写统计，再在 trainDto.getTrainFloors() 上 NPE，
 * 异常被 catch (Exception) 吞成 error 信封 —— 前半段写入照常提交。
 * 楼层校验必须发生在任何写操作之前。
 */
@QuarkusTest
class TelegramTrainSaveRollbackTest {
  @Inject TelegramTrainService service;
  @Inject TelegramTrainDao telegramTrainDao;
  @Inject TelegramTrainStatisticalDao statisticalDao;
  @Inject UserDao userDao;

  @Test
  void saveWithoutFloorsLeavesPausedTrainAndStatisticalUntouched() {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    TelegramTrainEntity paused = new TelegramTrainEntity();
    paused.setCreateUserId(user.getId());
    paused.setType(0);
    paused.setStatus(2);
    paused.setTitle("上一次暂停训练");
    paused = telegramTrainDao.saveAndFlush(paused);

    TelegramTrainEntity newTrain = new TelegramTrainEntity();
    newTrain.setType(0);
    newTrain.setTitle("缺楼层的新训练");
    TelegramTrainDto dto = new TelegramTrainDto();
    dto.setTrain(newTrain);
    dto.setTrainFloors(null);

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> service.save(token, dto), "缺楼层必须在写库前抛出，而不是半量提交后返回 error");
    assertEquals("训练楼层不能为空", e.getMessage());

    assertEquals(2, telegramTrainDao.findById(paused.getId()).getStatus().intValue(),
        "上一次暂停训练的状态不得被改成已完成");
    assertEquals(1, telegramTrainDao.find("createUserId", user.getId()).count(),
        "不得写入新的训练头");
    assertEquals(0, statisticalDao.find("userId", user.getId()).count(),
        "不得写入统计行");
  }
}
