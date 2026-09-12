package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.utils.JSONUtils;
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


import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest

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
  void completeQuestionListsAndSubmittedAnswersSurviveRoundTrip() {
    String token = "exam-payload-" + UUID.randomUUID();
    UserEntity user = Fixtures.user(userDao, token);
    List<List<TestPaperQuestionDto>> groups = new ArrayList<>();
    Map<String, Object> answers = new LinkedHashMap<>();
    List<String> names = List.of("singleChoice", "multipleChoice", "judge", "completion", "shortAnswer");
    for (int type = 1; type <= 5; type++) {
      List<TestPaperQuestionDto> questions = new ArrayList<>();
      List<Map<String, Object>> submitted = new ArrayList<>();
      for (int number = 1; number <= 2; number++) {
        TestPaperQuestionDto question = new TestPaperQuestionDto();
        question.setId(UUID.randomUUID().toString());
        question.setParentId(UUID.randomUUID().toString());
        question.setTopic("通信理论第" + type + "类第" + number + "题，题干与完整选项必须保留");
        question.setType(type);
        question.setScore(10);
        question.setSort(number);
        question.setOptions("[{\"value\":\"0\",\"label\":\"选项甲\"},{\"value\":\"1\",\"label\":\"选项乙\"}]");
        question.setAnswer(type == 3 ? "1" : "0");
        question.setAnalysis("完整解析应随试卷快照保存，不得因默认列长度截断");
        questions.add(question);
        Object answer = type == 1 ? "0" : type == 3 ? "1" : List.of("参考答案");
        submitted.add(Map.of("id", question.getId(), "answer", answer));
      }
      groups.add(questions);
      answers.put(names.get(type - 1), submitted);
    }
    TestPaperDto paper = paper(null);
    paper.setSingleChoice(groups.get(0));
    paper.setMultipleChoice(groups.get(1));
    paper.setJudge(groups.get(2));
    paper.setCompletion(groups.get(3));
    paper.setShortAnswer(groups.get(4));
    TheoryKnowledgeExamDto dto = exam("payload-" + token, paper, user.getId());
    dto.setTeacher(user.getId());
    assertEquals(200, service.saveTheoryKnowledgeExam(token, dto).getCode());
    String examId = examIdByTitle(dto.getTitle());
    var snapshot = examTestPaperDao.findAllByExamId(examId);
    List<String> stored = List.of(snapshot.getSingleChoiceList(), snapshot.getMultipleChoiceList(),
        snapshot.getJudgeList(), snapshot.getCompletionList(), snapshot.getShortAnswer());
    for (int index = 0; index < stored.size(); index++) {
      List<TestPaperQuestionDto> decoded = JSONUtils.fromJson(stored.get(index),
          new TypeToken<List<TestPaperQuestionDto>>() {});
      assertEquals(groups.get(index), decoded);
    }
    service.teacherStartExam(examId, 2);
    service.studentChangeExamState(token, examId, 2, null);
    String content = JSONUtils.toJson(answers);
    service.studentChangeExamState(token, examId, 3, content);
    TheoryKnowledgeExamUserEntity submitted = examUserDao.findAllByExamIdAndUserId(examId, user.getId());
    assertEquals(3, submitted.getState());
    assertEquals(content, submitted.getContent());
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
  void editExamWithContentOnlyAnswerIsRejectedWithoutDeletingRows() {
    UserEntity user = Fixtures.user(userDao, "t-exam-content-only");
    service.saveTheoryKnowledgeExam("t-exam-content-only",
        exam("exam-content-only", paper(null), user.getId()));
    String examId = examIdByTitle("exam-content-only");

    TheoryKnowledgeExamUserEntity examUser = examUserDao.findAllByExamId(examId).get(0);
    String answerContent = "{\"singleChoice\":[{\"id\":\"q1\",\"answer\":\"2\"}]}";
    examUser.setContent(answerContent);
    examUserDao.save(examUser);
    String snapshotId = examTestPaperDao.findAllByExamId(examId).getId();

    TheoryKnowledgeExamDto edit = exam("exam-content-only-edit", paper(snapshotId), user.getId());
    edit.setId(examId);

    assertThrows(IllegalStateException.class,
        () -> service.saveTheoryKnowledgeExam("t-exam-content-only", edit),
        "仅保存答案内容的考试也必须禁止编辑重建");

    TheoryKnowledgeExamUserEntity after = examUserDao.findById(examUser.getId());
    assertNotNull(after, "已有答案内容的考生行不得被删除重建");
    assertEquals(examUser.getId(), after.getId());
    assertEquals(answerContent, after.getContent(), "content 不得丢失");
    assertEquals(1, after.getState(), "state 不得被重置");
    assertEquals(0, after.getScore(), "score 不得被清零");
    assertNotNull(examTestPaperDao.findById(snapshotId), "旧试卷快照不得被删除");
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

  @Test
  void twoSelfTestsOnSamePaperKeepBothSnapshots() throws Exception {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    String sourcePaperId = "self-src-paper-" + UUID.randomUUID();

    String firstId = service
        .saveTheoryKnowledgeExamSelfTesting(token, exam("self-a-" + token, paper(sourcePaperId), user.getId()))
        .getId();
    String secondId = service
        .saveTheoryKnowledgeExamSelfTesting(token, exam("self-b-" + token, paper(sourcePaperId), user.getId()))
        .getId();

    assertNotEquals(firstId, secondId, "两次自测是两场考试");
    assertEquals(1, examTestPaperDao.count("examId", firstId),
        "前一场自测的快照不得被后一场按源试卷主键 merge 覆盖");
    assertEquals(1, examTestPaperDao.count("examId", secondId), "后一场自测应有自己的快照");
    assertDoesNotThrow(() -> service.examineAnalyse(firstId),
        "前一场自测的考核分析不得因快照被抹掉而 NPE");
  }

  @Test
  void selfTestWithMissingTypeListStoresEmptyJsonArray() throws Exception {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    TestPaperDto paper = paper(null);
    paper.setShortAnswer(null); // 缺一个题型列表

    String examId = service
        .saveTheoryKnowledgeExamSelfTesting(token, exam("self-null-" + token, paper, user.getId()))
        .getId();

    assertEquals("[]", examTestPaperDao.findAllByExamId(examId).getShortAnswer(),
        "自测写侧须与主路径一致做 nullToEmpty 归一");
  }
}
