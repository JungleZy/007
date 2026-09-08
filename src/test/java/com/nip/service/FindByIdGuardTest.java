package com.nip.service;

import com.nip.dao.PostMilitaryTermTrainDao;
import com.nip.dao.TheoryKnowledgeExamDao;
import com.nip.entity.PostMilitaryTermTrainEntity;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 7.4 findById 家族边界测试（第二批抽样）：
 * 原先 {@code dao.findById(id)} 的返回值被直接解引用（setStatus/setStartTime），
 * 不存在的 id 会以 NPE 逸出成 500。修复后必须是显式的参数错误。
 */
@QuarkusTest

class FindByIdGuardTest {

  @Inject PostMilitaryTermTrainService postMilitaryTermTrainService;
  @Inject PostMilitaryTermTrainDao postMilitaryTermTrainDao;
  @Inject TheoryKnowledgeExamService theoryKnowledgeExamService;
  @Inject TheoryKnowledgeExamDao theoryKnowledgeExamDao;

  @Test
  void beginMissingMilitaryTermTrainThrowsWithoutPersisting() {
    long before = postMilitaryTermTrainDao.count();

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> postMilitaryTermTrainService.begin("no-such-military-term-train"),
        "不存在的训练 id 必须显式报错而非 NPE");

    assertEquals("未查询到该训练", ex.getMessage());
    assertEquals(before, postMilitaryTermTrainDao.count(), "开始不存在的训练不得落任何新行");
  }

  @Test
  void beginExistingMilitaryTermTrainStillStarts() {
    PostMilitaryTermTrainEntity seeded = postMilitaryTermTrainDao.saveAndFlush(
        new PostMilitaryTermTrainEntity());

    postMilitaryTermTrainService.begin(seeded.getId());

    assertEquals(Integer.valueOf(1), postMilitaryTermTrainDao.findById(seeded.getId()).getStatus(),
        "存在的训练必须照常置为进行中，守卫不得误伤正常路径");
  }

  @Test
  void teacherStartMissingExamThrowsWithoutPersisting() {
    long before = theoryKnowledgeExamDao.count();

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> theoryKnowledgeExamService.teacherStartExam("no-such-exam-id", 2),
        "不存在的考试 id 必须显式报错而非 NPE");

    assertEquals("未查询到考试", ex.getMessage());
    assertEquals(before, theoryKnowledgeExamDao.count(), "开考不存在的考试不得落任何新行");
  }
}
