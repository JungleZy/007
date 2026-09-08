package com.nip.service;

import com.nip.dao.GradingRuleDao;
import com.nip.dao.PostTelegraphKeyPatTrainDao;
import com.nip.dao.PostTelegraphKeyPatTrainPageValueDao;
import com.nip.dto.PostTelegraphKeyPatTrainDto;
import com.nip.dto.vo.PostTelegraphKeyPatTrainVO;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.PostTelegraphKeyPatTrainEntity;
import com.nip.entity.PostTelegraphKeyPatTrainPageValueEntity;


import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.nip.common.constants.PostTelegraphKeyPatTrainEnum.FINISH;
import static com.nip.common.constants.PostTelegraphKeyPatTrainEnum.UNDERWAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 3.5 P1-10：finish 原来没有任何状态守卫，重复 finish 每次 deleteByTrainId+重插、
 * 时长按新 endTime 重算覆盖。已完成训练必须直接返回。
 * （守卫失效时本用例会走 countScore：无规则内容直接抛异常。）
 */
@QuarkusTest

class PostTelegraphKeyPatTrainServiceTest {
  @Inject PostTelegraphKeyPatTrainService service;
  @Inject PostTelegraphKeyPatTrainDao trainDao;
  @Inject PostTelegraphKeyPatTrainPageValueDao valueDao;
  @Inject GradingRuleDao gradingRuleDao;

  @Test
  void finishOnFinishedTrainReturnsWithoutRecount() {
    PostTelegraphKeyPatTrainEntity e = new PostTelegraphKeyPatTrainEntity();
    e.setStatus(FINISH.getStatus());
    e.setScore(new BigDecimal(77));
    e = trainDao.save(e);

    PostTelegraphKeyPatTrainDto dto = new PostTelegraphKeyPatTrainDto();
    dto.setId(e.getId());

    PostTelegraphKeyPatTrainVO vo = service.finish(dto);

    assertEquals(0, new BigDecimal(77).compareTo(trainDao.findById(e.getId()).getScore()),
        "已完成训练的分数不得被重复结算覆盖");
  }

  /**
   * Task 2.3 P2：结算重建走「先构建校验、后删除写入」。报底缺失导致重建集合为空属于构建阶段失败，
   * 必须在 deleteByTrainId 之前抛出，旧拍发记录行数与内容原封不动。
   * （修复前 delete 先执行、再写入空集合，该页拍发记录被整页清空。）
   */
  @Test
  void finishWithEmptyRebuildKeepsOldPageValuesIntact() {
    GradingRuleEntity rule = gradingRuleDao.save(new GradingRuleEntity());

    PostTelegraphKeyPatTrainEntity e = new PostTelegraphKeyPatTrainEntity();
    e.setStatus(UNDERWAY.getStatus());
    e.setBeginTime(LocalDateTime.now().minusMinutes(1));
    e.setRuleId(rule.getId());
    e.setScore(new BigDecimal(100));
    e = trainDao.save(e);
    String trainId = e.getId();

    // 只有拍发记录、没有任何报底行：重建结果必然为空
    seedPageValue(trainId, 0, "[\"1\",\"2\",\"3\",\"4\"]");
    seedPageValue(trainId, 1, "[\"5\",\"6\",\"7\",\"8\"]");
    seedPageValue(trainId, 2, "[\"9\",\"0\",\"1\",\"2\"]");

    PostTelegraphKeyPatTrainDto dto = new PostTelegraphKeyPatTrainDto();
    dto.setId(trainId);

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.finish(dto),
        "重建结果为空必须在删除之前被拒绝");
    assertTrue(ex.getMessage().contains("拍发记录重建结果为空"), ex.getMessage());

    List<PostTelegraphKeyPatTrainPageValueEntity> after =
        valueDao.findByTrainIdOrderByPageNumberAscSortAsc(trainId);
    assertEquals(3, after.size(), "构建阶段失败时旧拍发记录不得被删除");
    assertEquals(List.of("[\"1\",\"2\",\"3\",\"4\"]", "[\"5\",\"6\",\"7\",\"8\"]", "[\"9\",\"0\",\"1\",\"2\"]"),
        after.stream().map(PostTelegraphKeyPatTrainPageValueEntity::getValue).toList(),
        "旧拍发记录内容必须原样保留");
  }

  /** 播种第 1 页的一条拍发记录，key/value/time 均为 JSON 数组字符串 */
  private void seedPageValue(String trainId, int sort, String value) {
    PostTelegraphKeyPatTrainPageValueEntity v = new PostTelegraphKeyPatTrainPageValueEntity();
    v.setTrainId(trainId);
    v.setPageNumber(1);
    v.setSort(sort);
    v.setKey(value);
    v.setValue(value);
    v.setTime("[\"10\",\"10\",\"10\",\"10\"]");
    valueDao.save(v);
  }
}
