package com.nip.service;

import com.nip.common.response.Response;
import com.nip.dao.TheoryKnowledgeExamTestPaperDao;
import com.nip.dao.TheoryKnowledgeExamUserDao;
import com.nip.dao.TheoryKnowledgeSwfDao;
import com.nip.dao.UserDao;
import com.nip.dto.TheoryKnowledgesDto;
import com.nip.dto.vo.TheoryKnowledgeSwfVO;
import com.nip.dto.vo.TheoryKnowledgeTestVO;
import com.nip.entity.TheoryKnowledgeEntity;
import com.nip.entity.TheoryKnowledgeExamTestPaperEntity;
import com.nip.entity.TheoryKnowledgeExamUserEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;


import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest

class TheoryKnowledgeServiceTest {
  @Inject TheoryKnowledgeService service;
  @Inject TheoryKnowledgeSwfDao knowledgeSwfDao;
  @Inject UserDao userDao;
  @Inject TheoryKnowledgeExamUserDao examUserDao;
  @Inject TheoryKnowledgeExamTestPaperDao examTestPaperDao;

  private TheoryKnowledgesDto knowledges(String title, String userId) {
    TheoryKnowledgeEntity knowledge = new TheoryKnowledgeEntity();
    knowledge.setTitle(title);
    knowledge.setCreateUserId(userId);
    TheoryKnowledgeSwfVO swf = new TheoryKnowledgeSwfVO();
    swf.setTitle("章节1");
    swf.setTest(List.of());
    TheoryKnowledgesDto dto = new TheoryKnowledgesDto();
    dto.setKnowledge(knowledge);
    dto.setKnowledgeSwfs(List.of(swf));
    return dto;
  }

  @Test
  void editWithNullSwfListKeepsExistingSwfs() {
    UserEntity user = Fixtures.user(userDao, "t-theory");
    TheoryKnowledgesDto create = knowledges("k1", user.getId());
    String knowledgeId = service.saveTheoryKnowledge(create).getData().getId();
    long before = knowledgeSwfDao.count("knowledgeId", knowledgeId);
    assertTrue(before > 0);

    TheoryKnowledgesDto edit = knowledges("k1-edit", user.getId());
    edit.getKnowledge().setId(knowledgeId);
    edit.setKnowledgeSwfs(null); // 缺课件列表的编辑请求

    try {
      service.saveTheoryKnowledge(edit);
    } catch (RuntimeException expectedAfterFix) {
      // 修复后允许抛（课件列表缺失）；关键契约在下一行
    }
    assertTrue(knowledgeSwfDao.count("knowledgeId", knowledgeId) > 0, "原课件不得被静默删除");
  }

  @Test
  void saveWithNullTestContentsDoesNotNpe() {
    UserEntity user = Fixtures.user(userDao, "t-theory-2");
    TheoryKnowledgesDto create = knowledges("k2", user.getId());
    TheoryKnowledgeTestVO test = new TheoryKnowledgeTestVO();
    test.setTitle("测验1");
    test.setVersions(1);
    test.setKnowledgeTestContents(null); // 测验缺 content 列表
    create.getKnowledgeSwfs().get(0).setTest(List.of(test));

    String knowledgeId = service.saveTheoryKnowledge(create).getData().getId();
    assertTrue(knowledgeSwfDao.count("knowledgeId", knowledgeId) > 0, "课件应落库且不抛 NPE");
  }

  // 为一场试卷落库若干考生成绩：新建试卷(passMark/total)，逐条写入 state=4、endTime 命中年份的考试记录
  private void seedExam(String userId, int passMark, int total, int... scores) {
    String examId = UUID.randomUUID().toString();
    TheoryKnowledgeExamTestPaperEntity paper = new TheoryKnowledgeExamTestPaperEntity();
    paper.setExamId(examId);
    paper.setPassMark(passMark);
    paper.setTotal(total);
    examTestPaperDao.save(paper);
    for (int score : scores) {
      TheoryKnowledgeExamUserEntity eu = new TheoryKnowledgeExamUserEntity();
      eu.setUserId(userId);
      eu.setExamId(examId);
      eu.setScore(score);
      eu.setState(4);
      eu.setEndTime("2099-01-01 10:00:00");
      examUserDao.save(eu);
    }
  }

  @Test
  void gradeDistributionUsesPerPaperThresholds() {
    UserEntity user = Fixtures.user(userDao, "t-grade-dist");
    // A卷 passMark=70 total=100 => goodBoundary=85；B卷 passMark=50 total=100 => goodBoundary=75
    // 65(A) 落 "59"(<70) 而非固定档的 "60"；65(B) 落 "60"(>=50) 而非固定档的 "59" —— 固定 60/80 会误分档
    seedExam(user.getId(), 70, 100, 40, 65, 75, 90);
    seedExam(user.getId(), 50, 100, 40, 65, 85);

    Response<Object> response = service.gradeCount("t-grade-dist", "2099", "", 0);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getData();
    @SuppressWarnings("unchecked")
    Map<String, Integer> details = (Map<String, Integer>) data.get("down");
    @SuppressWarnings("unchecked")
    Map<String, Integer> result = (Map<String, Integer>) data.get("up");

    assertEquals(3, details.get("59"));
    assertEquals(2, details.get("60"));
    assertEquals(2, details.get("81"));
    assertEquals(4, result.get("good"));
  }
}
