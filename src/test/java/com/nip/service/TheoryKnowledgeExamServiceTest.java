package com.nip.service;

import com.nip.dao.TheoryKnowledgeExamDao;
import com.nip.dao.TheoryKnowledgeExamTestPaperDao;
import com.nip.dao.TheoryKnowledgeExamUserDao;
import com.nip.dao.UserDao;
import com.nip.dto.TestPaperDto;
import com.nip.dto.TestPaperQuestionDto;
import com.nip.dto.TheoryKnowledgeExamDto;
import com.nip.entity.TheoryKnowledgeExamUserEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class TheoryKnowledgeExamServiceTest {
  @Inject TheoryKnowledgeExamService service;
  @Inject TheoryKnowledgeExamDao examDao;
  @Inject TheoryKnowledgeExamTestPaperDao examTestPaperDao;
  @Inject TheoryKnowledgeExamUserDao examUserDao;
  @Inject UserDao userDao;

  /** 模拟前端建考试时携带的源试卷；sourcePaperId 为 null 表示新建（走 persist）。 */
  private TestPaperDto paper(String sourcePaperId) {
    TestPaperDto paper = new TestPaperDto();
    paper.setId(sourcePaperId);
    paper.setName("试卷");
    paper.setTotal(100);
    paper.setPassMark(60);
    TestPaperQuestionDto q = new TestPaperQuestionDto();
    q.setTopic("1+1=?");
    q.setType(1);
    q.setAnswer("2");
    paper.setSingleChoice(List.of(q));
    paper.setMultipleChoice(List.of());
    paper.setJudge(List.of());
    paper.setCompletion(List.of());
    paper.setShortAnswer(List.of());
    return paper;
  }

  private TheoryKnowledgeExamDto exam(String title, TestPaperDto paper, String stuId) {
    TheoryKnowledgeExamDto dto = new TheoryKnowledgeExamDto();
    dto.setTitle(title);
    dto.setDuration("60");
    dto.setTestPaper(paper);
    dto.setStuId(List.of(stuId));
    return dto;
  }

  private String examIdByTitle(String title) {
    return examDao.find("title", title).firstResult().getId();
  }

  @Test
  void twoExamsOnSamePaperKeepBothSnapshots() {
    UserEntity user = Fixtures.user(userDao, "t-exam-1");
    service.saveTheoryKnowledgeExam("t-exam-1", exam("exam-snap-a", paper("src-paper-1"), user.getId()));
    service.saveTheoryKnowledgeExam("t-exam-1", exam("exam-snap-b", paper("src-paper-1"), user.getId()));

    String examA = examIdByTitle("exam-snap-a");
    String examB = examIdByTitle("exam-snap-b");
    assertEquals(1, examTestPaperDao.count("examId", examA), "考试A的快照不得被同试卷的考试B抹掉");
    assertEquals(1, examTestPaperDao.count("examId", examB), "考试B应有自己的快照");
  }

  @Test
  void editExamWithAnsweredUsersIsRejected() {
    UserEntity user = Fixtures.user(userDao, "t-exam-2");
    service.saveTheoryKnowledgeExam("t-exam-2", exam("exam-guard", paper(null), user.getId()));
    String examId = examIdByTitle("exam-guard");

    TheoryKnowledgeExamUserEntity examUser = examUserDao.findAllByExamId(examId).get(0);
    examUser.setState(2); // 学生考核中
    examUser.setScore(55);
    examUserDao.save(examUser);

    // 前端编辑流：findTheoryKnowledgeExamById 返回快照作为 paper，编辑请求带快照 id
    String snapshotId = examTestPaperDao.findAllByExamId(examId).getId();
    TheoryKnowledgeExamDto edit = exam("exam-guard-edit", paper(snapshotId), user.getId());
    edit.setId(examId);
    assertThrows(IllegalStateException.class,
        () -> service.saveTheoryKnowledgeExam("t-exam-2", edit),
        "已有作答记录的考试编辑必须被拒绝");

    TheoryKnowledgeExamUserEntity after = examUserDao.findById(examUser.getId());
    assertNotNull(after, "已作答考生行不得被删除重建");
    assertEquals(2, after.getState(), "state 不得被重置");
    assertEquals(55, after.getScore(), "score 不得被清零");
  }

  @Test
  void analyseWithMissingTypeListDoesNotNPE() {
    UserEntity user = Fixtures.user(userDao, "t-exam-3");
    TestPaperDto paper = paper(null);
    paper.setShortAnswer(null); // 缺一个题型列表
    service.saveTheoryKnowledgeExam("t-exam-3", exam("exam-analyse", paper, user.getId()));
    String examId = examIdByTitle("exam-analyse");

    assertDoesNotThrow(() -> service.examineAnalyse(examId));
  }
}
