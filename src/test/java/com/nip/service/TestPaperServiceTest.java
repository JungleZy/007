package com.nip.service;

import com.nip.dao.*;
import com.nip.dto.TestPaperDto;
import com.nip.dto.TestPaperQuestionDto;
import com.nip.testsupport.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class TestPaperServiceTest {
  @Inject TestPaperService service;
  @Inject TestPaperQuestionDao questionDao;
  @Inject UserDao userDao;

  private TestPaperDto paper(String name) {
    TestPaperDto dto = new TestPaperDto();
    dto.setName(name);
    dto.setTotal(100);
    dto.setPassMark(60);
    TestPaperQuestionDto q = new TestPaperQuestionDto();
    q.setTopic("1+1=?");
    q.setType(1); // 单选；null 会在 findAllTestPaper:130 拆箱 NPE
    dto.setSingleChoice(List.of(q));
    dto.setMultipleChoice(List.of());
    dto.setJudge(List.of());
    dto.setCompletion(List.of());
    dto.setShortAnswer(List.of());
    return dto;
  }

  @Test
  void updateWithNullTypeListKeepsExistingQuestions() {
    Fixtures.user(userDao, "t-paper");
    TestPaperDto dto = paper("p1");
    service.saveTestPaper("t-paper", dto);
    String paperId = service.findAllTestPaper().getData().get(0).getId();
    long before = questionDao.count("testPaperId", paperId);
    assertTrue(before > 0);

    TestPaperDto edit = paper("p1-edit");
    edit.setId(paperId);
    edit.setShortAnswer(null); // 缺一个题型列表

    try {
      service.saveTestPaper("t-paper", edit);
    } catch (RuntimeException expectedAfterFix) {
      // 修复后允许抛（null 归一后实际不抛）；关键契约在下一行
    }
    assertTrue(questionDao.count("testPaperId", paperId) > 0, "原题目不得被静默删除");
  }
}
