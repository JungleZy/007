package com.nip.service;

import com.nip.common.utils.JSONUtils;
import com.nip.dao.PostTelegramTrainContentValueDao;
import com.nip.dao.PostTelegramTrainDao;
import com.nip.dto.PostTelegramTrainFinishDto;
import com.nip.dto.PostTelegramTrainFinishInfoDto;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import com.nip.entity.PostTelegramTrainContentFloorValueEntity;
import com.nip.entity.PostTelegramTrainEntity;

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

  // t_grading_rule type=0 的精简规则：rule_content 列为 VARCHAR(255)，此处保留结构与关键 base 值，
  // other 段用空对象压到 255 以内；countScore 在读到损坏 patLogs 前只需 parseContent 成功。
  private static final String RULE_CONTENT = "{\"wpm\":{\"base\":70},\"skew\":51,"
      + "\"code\":{\"dot\":{\"base\":30},\"dash\":{\"base\":50}},"
      + "\"gap\":{\"little\":{\"base\":40},\"middle\":{\"base\":60},\"large\":{\"base\":90}},"
      + "\"other\":{\"errorCode\":{},\"quantoCode\":{},\"quantoGroup\":{},"
      + "\"alterError\":{},\"quantoRow\":{},\"bunchGroup\":{}}}";
  private static String corruptMessageBody() {
    PostTelegramTrainContentAddParam item = new PostTelegramTrainContentAddParam();
    item.setPatKeys(JSONUtils.toJson(List.of("ABCD")));
    item.setPatLogs("[broken");
    item.setMoresTime("[[1,2]]");
    item.setMoresValue("[[1,2]]");
    return JSONUtils.toJson(List.of(item));
  }

  @Test
  void finishRollsBackWhenStoredPatLogsJsonIsCorrupt() {
    PostTelegramTrainEntity train = new PostTelegramTrainEntity();
    train.setMessageNumber(100);
    train.setStatus(1); // 进行中
    train.setScore("88");
    train.setRuleContent(RULE_CONTENT);
    train = trainDao.save(train);
    String trainId = train.getId();

    PostTelegramTrainContentFloorValueEntity floor = new PostTelegramTrainContentFloorValueEntity();
    floor.setTrainId(trainId);
    floor.setFloorNumber(1);
    floor.setMessageBody(corruptMessageBody());
    floor.setStandard("[]");
    floor.setResolver("OLD_RESOLVER");
    floor = contentValueDao.saveAndFlush(floor);
    String floorId = floor.getId();

    PostTelegramTrainFinishDto dto = new PostTelegramTrainFinishDto();
    dto.setId(trainId);
    dto.setValidTime(60);
    dto.setFinishInfo(List.of(new PostTelegramTrainFinishInfoDto()));

    assertThrows(IllegalStateException.class, () -> service.finish(dto));

    assertEquals(Integer.valueOf(1), trainDao.findById(trainId).getStatus(),
        "外层事务须回滚：状态保持进行中");
    assertEquals("88", trainDao.findById(trainId).getScore(),
        "外层事务须回滚：分数不得变更");
    assertEquals("OLD_RESOLVER", contentValueDao.findById(floorId).getResolver(),
        "外层事务须回滚：旧解析结果不得被覆盖");
  }
}
