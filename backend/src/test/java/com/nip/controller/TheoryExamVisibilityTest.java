package com.nip.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.RoleDao;
import com.nip.dao.TheoryKnowledgeExamDao;
import com.nip.dao.TheoryKnowledgeExamUserDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.dto.TestPaperDto;
import com.nip.dto.TestPaperQuestionDto;
import com.nip.dto.TheoryKnowledgeExamDto;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.service.TheoryKnowledgeExamService;
import com.nip.testsupport.Fixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TheoryExamVisibilityTest {
  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;
  @Inject TheoryKnowledgeExamDao examDao;
  @Inject TheoryKnowledgeExamUserDao candidateDao;
  @Inject TheoryKnowledgeExamService service;

  @Test
  void detailAndEntryRespectCandidateAndControllerBoundaries() {
    UserEntity creator = user();
    UserEntity teacher = user();
    UserEntity first = user();
    UserEntity second = user();
    UserEntity outsider = user();
    UserEntity admin = user();
    makeAdmin(admin);
    String id = ordinaryExam(creator, teacher, first, second);
    for (UserEntity candidate : List.of(first, second)) {
      String content = JSONUtils.toJson(Map.of("shortAnswer",
          List.of(Map.of("id", "q5", "answer", "private-" + candidate.getId()))));
      request(candidate, "studentSaveExamRealtimeContont", Map.of("examId", id, "content", content), 200);
    }
    for (UserEntity candidate : List.of(first, second)) {
      JsonObject detail = request(candidate, "findTheoryKnowledgeExamById", Map.of("id", id), 200);
      var users = detail.getAsJsonArray("user");
      assertEquals(1, users.size());
      assertEquals(candidate.getId(), users.get(0).getAsJsonObject().get("user_id").getAsString());
      String otherId = candidate == first ? second.getId() : first.getId();
      assertFalse(detail.toString().contains(otherId));
      assertTrue(users.get(0).getAsJsonObject().get("content").getAsString().contains("private-" + candidate.getId()));
      assertHiddenPaper(detail.getAsJsonObject("paper"));
      JsonObject entry = request(candidate, "studentChangeExamState", Map.of("examId", id, "type", "2"), 200);
      assertEquals(candidate.getId(), entry.getAsJsonObject("student").get("userId").getAsString());
      assertFalse(entry.has("user"));
      assertHiddenPaper(entry.getAsJsonObject("paper"));
      assertFalse(entry.toString().contains(otherId));
    }
    request(outsider, "findTheoryKnowledgeExamById", Map.of("id", id), 207);
    request(outsider, "studentChangeExamState", Map.of("examId", id, "type", "2"), 207);
    for (UserEntity controller : List.of(creator, teacher, admin)) {
      JsonObject detail = request(controller, "findTheoryKnowledgeExamById", Map.of("id", id), 200);
      assertEquals(2, detail.getAsJsonArray("user").size());
      assertEquals("[\"alpha\",\"beta\",\"gamma\"]", question(detail.getAsJsonObject("paper"), "completionList").get("answer").getAsString());
      assertEquals("解释秘密", question(detail.getAsJsonObject("paper"), "singleChoiceList").get("analysis").getAsString());
      request(controller, "studentChangeExamState", Map.of("examId", id, "type", "2"), 207);
      JsonObject analysis = request(controller, "examineAnalyse", Map.of("examId", id), 200);
      assertEquals(2, analysis.getAsJsonArray("scoreList").size());
    }
    request(first, "examineAnalyse", Map.of("examId", id), 207);
    request(outsider, "examineAnalyse", Map.of("examId", id), 207);
    for (UserEntity actor : List.of(creator, teacher, admin, first, outsider)) {
      var response = given().contentType(ContentType.JSON).header("token", actor.getToken())
          .header("deviceId", actor.getDeviceId()).body(Map.of("state", true))
          .post("/api/theoryKnowledgeExam/findAllTheoryKnowledgeExam").then().statusCode(200).extract().jsonPath();
      assertEquals(200, response.getInt("code"));
      List<String> ids = response.getList("data.id");
      assertEquals(List.of(creator, teacher, admin).contains(actor), ids.contains(id));
    }
  }

  @Test
  void ordinaryReviewRequiresBothExamCompletionAndOwnGrading() {
    UserEntity creator = user();
    UserEntity first = user();
    UserEntity second = user();
    String id = ordinaryExam(creator, creator, first, second);
    setStates(id, first, 3, 4);
    assertHiddenPaper(request(first, "findTheoryKnowledgeExamById", Map.of("id", id), 200).getAsJsonObject("paper"));
    setStates(id, second, 4, 3);
    assertHiddenPaper(request(second, "findTheoryKnowledgeExamById", Map.of("id", id), 200).getAsJsonObject("paper"));
    JsonObject reviewed = request(first, "findTheoryKnowledgeExamById", Map.of("id", id), 200);
    assertEquals("标准论述", question(reviewed.getAsJsonObject("paper"), "shortAnswer").get("answer").getAsString());
    assertEquals(1, reviewed.getAsJsonArray("user").size());
    request(first, "studentChangeExamState", Map.of("examId", id, "type", "2"), 208);
  }

  @Test
  void administratorSelfTestCreatorCannotSeeAnswersUntilFinishingAllQuestionTypes() throws Exception {
    UserEntity candidate = user();
    makeAdmin(candidate);
    TheoryKnowledgeExamDto dto = examDto(candidate, candidate, candidate);
    String id = request(candidate, "saveTheoryKnowledgeExamSelfTesting", Map.of(
        "title", dto.getTitle(), "duration", dto.getDuration(), "testPaper", dto.getTestPaper()), 200)
        .get("id").getAsString();
    request(user(), "findTheoryKnowledgeExamById", Map.of("id", id), 207);
    request(candidate, "examineAnalyse", Map.of("examId", id), 207);
    assertHiddenPaper(request(candidate, "findTheoryKnowledgeExamById", Map.of("id", id), 200).getAsJsonObject("paper"));
    assertHiddenPaper(request(candidate, "studentChangeExamState", Map.of("examId", id, "type", "2"), 200).getAsJsonObject("paper"));
    String content = JSONUtils.toJson(Map.of(
        "singleChoice", List.of(Map.of("id", "q1", "answer", "0")),
        "multipleChoice", List.of(Map.of("id", "q2", "answer", List.of("0", "1"))),
        "judge", List.of(Map.of("id", "q3", "answer", "1")),
        "completion", List.of(Map.of("id", "q4", "answer", List.of("alpha", "beta", "gamma"))),
        "shortAnswer", List.of(Map.of("id", "q5", "answer", "标准论述"))));
    request(candidate, "studentSaveExamRealtimeContont", Map.of("examId", id, "content", content), 200);
    request(candidate, "finishSelfTesting", Map.of("examId", id, "content", content), 200);
    JsonObject review = request(candidate, "findTheoryKnowledgeExamById", Map.of("id", id), 200);
    assertEquals(50, review.getAsJsonArray("user").get(0).getAsJsonObject().get("score").getAsInt());
    assertEquals("标准论述", question(review.getAsJsonObject("paper"), "shortAnswer").get("answer").getAsString());
    assertEquals("解释秘密", question(review.getAsJsonObject("paper"), "shortAnswer").get("analysis").getAsString());
  }

  private void assertHiddenPaper(JsonObject paper) {
    assertEquals("", JsonParser.parseString(question(paper, "singleChoiceList").get("answer").getAsString()).getAsString());
    assertTrue(JsonParser.parseString(question(paper, "multipleChoiceList").get("answer").getAsString()).getAsJsonArray().isEmpty());
    assertEquals("", JsonParser.parseString(question(paper, "judgeList").get("answer").getAsString()).getAsString());
    assertEquals(JsonParser.parseString("[\"\",\"\",\"\"]"), JsonParser.parseString(question(paper, "completionList").get("answer").getAsString()));
    assertEquals("", question(paper, "shortAnswer").get("answer").getAsString());
    assertEquals(2, JsonParser.parseString(question(paper, "singleChoiceList").get("options").getAsString()).getAsJsonArray().size());
    assertEquals("$_$(___)尾________", question(paper, "completionList").get("topic").getAsString());
    assertFalse(paper.toString().contains("解释秘密"));
    assertFalse(paper.toString().contains("标准论述"));
    assertFalse(paper.toString().contains("alpha"));
  }

  private JsonObject question(JsonObject paper, String section) {
    return JsonParser.parseString(paper.get(section).getAsString()).getAsJsonArray().get(0).getAsJsonObject();
  }

  private JsonObject request(UserEntity actor, String endpoint, Object body, int code) {
    String json = given().contentType(ContentType.JSON).header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId()).body(body).post("/api/theoryKnowledgeExam/" + endpoint)
        .then().statusCode(200).extract().asString();
    JsonObject response = JsonParser.parseString(json).getAsJsonObject();
    assertEquals(code, response.get("code").getAsInt(), json);
    return code == 200 ? response.getAsJsonObject("data") : null;
  }

  private UserEntity user() {
    return Fixtures.user(userDao, "visibility-" + UUID.randomUUID(), UUID.randomUUID().toString());
  }

  private void makeAdmin(UserEntity user) {
    RoleEntity role = new RoleEntity();
    role.setTitle("visibility-" + UUID.randomUUID());
    role.setIsAdmin(0);
    role.setIsDefault(1);
    role = roleDao.saveAndFlush(role);
    UserRoleEntity link = new UserRoleEntity();
    link.setUserId(user.getId());
    link.setRoleId(role.getId());
    userRoleDao.saveAndFlush(link);
  }

  private String ordinaryExam(UserEntity creator, UserEntity teacher, UserEntity... candidates) {
    TheoryKnowledgeExamDto dto = examDto(creator, teacher, candidates);
    service.saveTheoryKnowledgeExam(creator.getToken(), dto);
    String id = examDao.find("title", dto.getTitle()).firstResult().getId();
    QuarkusTransaction.requiringNew().run(() -> examDao.findById(id).setState(2));
    return id;
  }

  private void setStates(String id, UserEntity candidate, int examState, int candidateState) {
    QuarkusTransaction.requiringNew().run(() -> {
      examDao.findById(id).setState(examState);
      candidateDao.findAllByExamIdAndUserId(id, candidate.getId()).setState(candidateState);
    });
  }

  private TheoryKnowledgeExamDto examDto(UserEntity creator, UserEntity teacher, UserEntity... candidates) {
    TestPaperDto paper = new TestPaperDto();
    paper.setName("visibility-paper");
    paper.setTotal(50);
    paper.setPassMark(30);
    paper.setSingleChoice(List.of(question(1, "\"0\"")));
    paper.setMultipleChoice(List.of(question(2, "[\"0\",\"1\"]")));
    paper.setJudge(List.of(question(3, "\"1\"")));
    paper.setCompletion(List.of(question(4, "[\"alpha\",\"beta\",\"gamma\"]")));
    paper.setShortAnswer(List.of(question(5, "标准论述")));
    TheoryKnowledgeExamDto dto = new TheoryKnowledgeExamDto();
    dto.setTitle("visibility-" + UUID.randomUUID());
    dto.setCreateUserId(creator.getId());
    dto.setTeacher(teacher.getId());
    dto.setDuration("60");
    dto.setTestPaper(paper);
    dto.setStuId(java.util.Arrays.stream(candidates).map(UserEntity::getId).toList());
    return dto;
  }

  private TestPaperQuestionDto question(int type, String answer) {
    TestPaperQuestionDto question = new TestPaperQuestionDto();
    question.setId("q" + type);
    question.setType(type);
    question.setTopic(type == 4 ? "$_$(___)尾________" : "题干");
    question.setScore(10);
    question.setAnswer(answer);
    question.setAnalysis("解释秘密");
    question.setOptions("[{\"value\":\"0\",\"label\":\"甲\"},{\"value\":\"1\",\"label\":\"乙\"}]");
    return question;
  }
}
