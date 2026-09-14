package com.nip.service;

import com.nip.common.constants.PostMilitaryTermTrainStatusEnum;
import com.nip.dao.PostMilitaryTermTrainDao;
import com.nip.dao.PostMilitaryTermTrainTestPaperDao;
import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.entity.PostMilitaryTermTrainEntity;
import com.nip.entity.PostMilitaryTermTrainTestPaperEntity;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class PostMilitaryTermTrainBehaviorTest {
  @Inject PostMilitaryTermTrainDao trainDao;
  @Inject PostMilitaryTermTrainTestPaperDao paperDao;
  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;

  @Test
  void detailsRedactsIncompleteAnswersAndRejectsOtherUsers() {
    UserEntity owner = user("military-details-owner");
    UserEntity other = user("military-details-other");
    UserEntity admin = admin("military-details-admin");
    Seed seed = seedTrain(owner, PostMilitaryTermTrainStatusEnum.UNDERWAY.getStatus(), null, "题面保留");

    details(other, seed.trainId()).then().statusCode(200).body("code", is(207));
    details(admin, seed.trainId()).then().statusCode(200).body("code", is(207));
    details(owner, seed.trainId()).then().statusCode(200).body("code", is(200))
        .body("data.testPaperList[0].correctAnswer", nullValue())
        .body("data.testPaperList[0].title", is("题面保留"))
        .body("data.testPaperList[0].option", is("{\"A\":\"正确\",\"B\":\"错误\"}"));

    PostMilitaryTermTrainTestPaperEntity stored = paperDao.findById(seed.paperId());
    assertNotNull(stored);
    assertEquals("A", stored.getCorrectAnswer(), "脱敏不得改写受管实体的正确答案");
  }

  @Test
  void invalidSubmissionIsRejectedWithoutChangingCommittedRows() {
    UserEntity owner = user("military-invalid-owner");
    UserEntity other = user("military-invalid-other");
    Seed seed = seedTrain(owner, PostMilitaryTermTrainStatusEnum.UNDERWAY.getStatus(), null, "校验题");
    String trainId = seed.trainId();
    Map<String, Object> malformed = Map.of("id", trainId, "testPaperList", List.of(Map.of("userAnswer", "A")));
    finish(owner, malformed).then().statusCode(200).body("code", is(202));
    finish(other, Map.of("id", trainId, "testPaperList", List.of(answer(seed.paperId(), "A"))))
        .then().statusCode(200).body("code", is(207));

    Map<String, Object> duplicate = Map.of("id", trainId, "testPaperList", List.of(
        answer(seed.paperId(), "A"), answer(seed.paperId(), "A")));
    finish(owner, duplicate).then().statusCode(200).body("code", is(202));

    Map<String, Object> outside = Map.of("id", trainId, "testPaperList", List.of(answer("outside", "A")));
    finish(owner, outside).then().statusCode(200).body("code", is(202));

    Map<String, Object> invalidKey = Map.of("id", trainId, "testPaperList", List.of(answer(seed.paperId(), "Z")));
    finish(owner, invalidKey).then().statusCode(200).body("code", is(202));

    details(other, trainId).then().statusCode(200).body("code", is(207));
    PostMilitaryTermTrainEntity train = trainDao.findById(trainId);
    PostMilitaryTermTrainTestPaperEntity paper = paperDao.findById(seed.paperId());
    assertEquals(PostMilitaryTermTrainStatusEnum.UNDERWAY.getStatus(), train.getStatus());
    assertNull(train.getCorrectNumber());
    assertNull(train.getEndTime());
    assertNull(paper.getUserAnswer());
    assertEquals("A", paper.getCorrectAnswer());
  }

  @Test
  void completedBeginReturnsTerminalCodeAndCompletedDetailsKeepReviewFields() {
    UserEntity owner = user("military-complete-owner");
    UserEntity other = user("military-complete-other");
    Seed seed = seedTrain(owner, PostMilitaryTermTrainStatusEnum.FINISH.getStatus(), "A", "已完成题面");
    PostMilitaryTermTrainEntity before = trainDao.findById(seed.trainId());
    LocalDateTime endTime = before.getEndTime();

    begin(owner, seed.trainId()).then().statusCode(200).body("code", is(208));
    details(owner, seed.trainId()).then().statusCode(200).body("code", is(200))
        .body("data.testPaperList[0].correctAnswer", is("A"))
        .body("data.testPaperList[0].userAnswer", is("A"))
        .body("data.testPaperList[0].title", is("已完成题面"));
    details(other, seed.trainId()).then().statusCode(200).body("code", is(207));

    PostMilitaryTermTrainEntity after = trainDao.findById(seed.trainId());
    assertEquals(PostMilitaryTermTrainStatusEnum.FINISH.getStatus(), after.getStatus());
    assertEquals(endTime, after.getEndTime());
    assertEquals("A", paperDao.findById(seed.paperId()).getCorrectAnswer());
  }

  @Test
  void concurrentIdenticalFinishIsIdempotentAndChangedReplayIsTerminal() {
    UserEntity owner = user("military-race-owner");
    Seed seed = seedTrain(owner, PostMilitaryTermTrainStatusEnum.UNDERWAY.getStatus(), null, "并发题");
    Map<String, Object> payload = Map.of("id", seed.trainId(), "testPaperList", List.of(answer(seed.paperId(), "A")));

    CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
        () -> finish(owner, payload).jsonPath().getInt("code"));
    CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
        () -> finish(owner, payload).jsonPath().getInt("code"));
    CompletableFuture.allOf(first, second).orTimeout(10, java.util.concurrent.TimeUnit.SECONDS).join();
    assertEquals(List.of(200, 200), List.of(first.join(), second.join()),
        "相同交卷并发到达时，持锁后的请求必须幂等成功");

    finish(owner, Map.of("id", seed.trainId(), "testPaperList", List.of(answer(seed.paperId(), "B"))))
        .then().statusCode(200).body("code", is(208));
    PostMilitaryTermTrainEntity train = trainDao.findById(seed.trainId());
    PostMilitaryTermTrainTestPaperEntity paper = paperDao.findById(seed.paperId());
    assertEquals(PostMilitaryTermTrainStatusEnum.FINISH.getStatus(), train.getStatus());
    assertEquals(Integer.valueOf(1), train.getCorrectNumber());
    assertEquals("A", paper.getUserAnswer());
    assertEquals("A", paper.getCorrectAnswer());
  }

  @Test
  void noAnswerFinishKeepsLegacyZeroScoreBehavior() {
    UserEntity owner = user("military-no-answer-owner");
    Seed seed = seedTrain(owner, PostMilitaryTermTrainStatusEnum.UNDERWAY.getStatus(), null, "空答案题");

    finish(owner, Map.of("id", seed.trainId(), "testPaperList", List.of()))
        .then().statusCode(200).body("code", is(200));
    finish(owner, Map.of("id", seed.trainId(), "testPaperList", List.of()))
        .then().statusCode(200).body("code", is(200));
    PostMilitaryTermTrainEntity train = trainDao.findById(seed.trainId());
    assertEquals(Integer.valueOf(0), train.getCorrectNumber());
    assertEquals(Integer.valueOf(0), train.getErrorNumber());
    assertEquals(0, train.getAccuracy().compareTo(BigDecimal.ZERO));
    assertEquals(0, train.getScore().compareTo(BigDecimal.ZERO));
  }

  @Test
  void ongoingTrainWithoutStartTimeIsTerminalAndUnchanged() {
    UserEntity owner = user("military-unverifiable-owner");
    Seed seed = seedTrain(owner, PostMilitaryTermTrainStatusEnum.UNDERWAY.getStatus(), null, "缺少开始时间题");
    QuarkusTransaction.requiringNew().run(() -> trainDao.findById(seed.trainId()).setStartTime(null));

    finish(owner, Map.of("id", seed.trainId(), "testPaperList", List.of(answer(seed.paperId(), "A"))))
        .then().statusCode(200).body("code", is(208));
    PostMilitaryTermTrainEntity train = trainDao.findById(seed.trainId());
    PostMilitaryTermTrainTestPaperEntity paper = paperDao.findById(seed.paperId());
    assertEquals(PostMilitaryTermTrainStatusEnum.UNDERWAY.getStatus(), train.getStatus());
    assertNull(train.getStartTime());
    assertNull(train.getEndTime());
    assertNull(paper.getUserAnswer());
    assertEquals("A", paper.getCorrectAnswer());
  }

  private UserEntity user(String prefix) {
    return Fixtures.user(userDao, prefix + "-" + UUID.randomUUID(), "device-" + UUID.randomUUID());
  }

  private UserEntity admin(String prefix) {
    UserEntity user = user(prefix);
    RoleEntity role = new RoleEntity();
    role.setTitle(prefix + "-role");
    role.setIsAdmin(0);
    role.setIsDefault(1);
    role = roleDao.saveAndFlush(role);
    UserRoleEntity link = new UserRoleEntity();
    link.setUserId(user.getId());
    link.setRoleId(role.getId());
    userRoleDao.saveAndFlush(link);
    return user;
  }

  private Seed seedTrain(UserEntity owner, int status, String userAnswer, String title) {
    return QuarkusTransaction.requiringNew().call(() -> {
      PostMilitaryTermTrainEntity train = new PostMilitaryTermTrainEntity();
      train.setUserId(owner.getId());
      train.setStatus(status);
      train.setTypes("[]");
      train.setTotalNumber(1);
      train.setCorrectNumber(status == PostMilitaryTermTrainStatusEnum.FINISH.getStatus() ? 1 : null);
      train.setErrorNumber(status == PostMilitaryTermTrainStatusEnum.FINISH.getStatus() ? 0 : null);
      train.setAccuracy(status == PostMilitaryTermTrainStatusEnum.FINISH.getStatus() ? new BigDecimal("100") : null);
      train.setScore(status == PostMilitaryTermTrainStatusEnum.FINISH.getStatus() ? new BigDecimal("100") : null);
      train.setStartTime(status == PostMilitaryTermTrainStatusEnum.NOT_STARTED.getStatus()
          ? null : LocalDateTime.now().minusSeconds(5));
      train.setEndTime(status == PostMilitaryTermTrainStatusEnum.FINISH.getStatus() ? LocalDateTime.now() : null);
      train = trainDao.saveAndFlush(train);

      PostMilitaryTermTrainTestPaperEntity paper = new PostMilitaryTermTrainTestPaperEntity();
      paper.setTrainId(train.getId());
      paper.setTitle(title);
      paper.setOption("{\"A\":\"正确\",\"B\":\"错误\"}");
      paper.setCorrectAnswer("A");
      paper.setUserAnswer(userAnswer);
      paper = paperDao.saveAndFlush(paper);
      return new Seed(train.getId(), paper.getId());
    });
  }

  private Map<String, Object> answer(String id, String userAnswer) {
    return Map.of("id", id, "userAnswer", userAnswer);
  }

  private Response details(UserEntity user, String trainId) {
    return given().contentType(ContentType.JSON).header("token", user.getToken()).header("deviceId", user.getDeviceId())
        .body(Map.of("id", trainId)).when().post("/api/postMilitaryTermTrain/details").then().extract().response();
  }

  private Response begin(UserEntity user, String trainId) {
    return given().contentType(ContentType.JSON).header("token", user.getToken()).header("deviceId", user.getDeviceId())
        .body(Map.of("id", trainId)).when().post("/api/postMilitaryTermTrain/begin").then().extract().response();
  }

  private Response finish(UserEntity user, Map<String, Object> body) {
    return given().contentType(ContentType.JSON).header("token", user.getToken()).header("deviceId", user.getDeviceId())
        .body(body).when().post("/api/postMilitaryTermTrain/finish").then().extract().response();
  }

  private record Seed(String trainId, String paperId) {
  }
}
