package com.nip.service;

import com.nip.common.utils.JSONUtils;
import com.nip.dao.PostTelegramTrainContentValueDao;
import com.nip.dao.PostTelegramTrainDao;
import com.nip.dto.PostTelegramTrainFinishDto;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import com.nip.entity.PostTelegramTrainContentFloorValueEntity;
import com.nip.entity.PostTelegramTrainEntity;
import com.nip.dao.UserDao;
import com.nip.testsupport.Fixtures;
import java.time.LocalDateTime;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task 2：完成/计分路径读取到损坏的 patLogs JSON 时，resolverMessage 抛 IllegalStateException，
 * 外层 @Transactional(rollbackOn=Exception.class) 必须整体回滚，不得留下半成品的状态/分数/解析结果。
 * 断言校验数据库真实状态，而非注解本身。
 */
@QuarkusTest
class PostTelegramFinishCorruptionTest {
  @Inject PostTelegramTrainService service;
  @Inject PostTelegramTrainDao trainDao;
  @Inject PostTelegramTrainContentValueDao contentValueDao;
  @Inject UserDao userDao;

  private static final String RULE_CONTENT = PostTelegramTrainServiceTest.RULE;
  private static String corruptMessageBody() {
    PostTelegramTrainContentAddParam item = PostTelegramTrainServiceTest.group(List.of("A", "B", "C", "D"));
    item.setPatLogs("[broken");
    return JSONUtils.toJson(List.of(item));
  }

  @Test
  void finishRollsBackWhenStoredPatLogsJsonIsCorrupt() {
    PostTelegramTrainEntity train = new PostTelegramTrainEntity();
    String token = "hand-corrupt-" + UUID.randomUUID();
    train.setCreateUser(Fixtures.user(userDao, token).getId());
    train.setProtocolVersion(1).setAttempt(0).setFullScore(88).setStartTime(LocalDateTime.now().minusMinutes(1));
    train.setMessageNumber(100);
    train.setStatus(1); // 进行中
    train.setScore("88");
    train.setRuleContent(RULE_CONTENT);
    train = trainDao.save(train);
    String trainId = train.getId();

    PostTelegramTrainContentFloorValueEntity floor = new PostTelegramTrainContentFloorValueEntity();
    floor.setTrainId(trainId);
    floor.setFloorNumber(1);
    floor.setAttempt(0);
    floor.setCaptureIntervals("[{\"startedMs\":0,\"endedMs\":1000}]");
    floor.setReceivedAt(LocalDateTime.now());
    floor.setMessageBody(corruptMessageBody());
    floor.setStandard("[]");
    floor.setResolver("OLD_RESOLVER");
    floor = contentValueDao.saveAndFlush(floor);
    String floorId = floor.getId();

    PostTelegramTrainFinishDto dto = new PostTelegramTrainFinishDto();
    dto.setId(trainId);
    dto.setAttempt(0);

    assertThrows(com.google.gson.JsonParseException.class, () -> service.finish(dto, token));

    assertEquals(Integer.valueOf(1), trainDao.findById(trainId).getStatus(),
        "外层事务须回滚：状态保持进行中");
    assertEquals("88", trainDao.findById(trainId).getScore(),
        "外层事务须回滚：分数不得变更");
    assertEquals("OLD_RESOLVER", contentValueDao.findById(floorId).getResolver(),
        "外层事务须回滚：旧解析结果不得被覆盖");
  }
}
