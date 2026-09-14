package com.nip.service;

import com.nip.dao.PostEnteringExerciseDao;
import com.nip.dao.PostRadiotelephoneDao;
import com.nip.dao.UserDao;
import com.nip.entity.PostEnteringExerciseEntity;
import com.nip.entity.PostRadiotelephoneTrainEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PersonalTrainingScoringContractTest {
  private static final String ENTERING = "/api/postEnteringExercise/";
  private static final String RADIO = "/api/postRadiotelephoneTrain/";
  private static final String ENTERING_SOURCE = "[\"前进\",\"停止\"]";
  private static final String ENTERING_ANSWERS = "[{\"font\":\"前进\",\"value\":\"前进\"},{\"font\":\"停止\",\"value\":\"错误\"}]";
  private static final String RADIO_SOURCE = "[{\"key\":\"ALFA\",\"value\":\"前进\"},{\"key\":\"BRAVO\",\"value\":\"停止\"}]";
  private static final String RADIO_ANSWERS = "[{\"key\":\"ALFA\",\"value\":\"前进\",\"answer\":{\"key\":\"ALFA\",\"value\":\"前进\"}},{\"key\":\"BRAVO\",\"value\":\"停止\",\"answer\":{\"key\":\"wrong\",\"value\":\"停止\"}}]";

  @Inject UserDao userDao;
  @Inject PostEnteringExerciseDao enteringDao;
  @Inject PostRadiotelephoneDao radioDao;
  private UserEntity owner;
  private UserEntity other;

  @BeforeEach
  void seedUsers() {
    owner = Fixtures.user(userDao, UUID.randomUUID().toString(), "personal-owner");
    other = Fixtures.user(userDao, UUID.randomUUID().toString(), "personal-other");
  }

  @AfterEach
  void cleanup() {
    QuarkusTransaction.requiringNew().run(() -> {
      enteringDao.delete("createUserId", owner.getId());
      radioDao.delete("userId", owner.getId());
      userDao.deleteById(owner.getId());
      userDao.deleteById(other.getId());
    });
  }

  @Test
  void bothPersonalDomainsRejectOtherUsersBeforeReadingOrMutating() {
    String entering = entering(1, ENTERING_SOURCE);
    String radio = radio(0, 0);
    for (String action : new String[]{"begin", "finish", "getById"}) {
      post(other, ENTERING, action, Map.of("id", entering, "content", ENTERING_ANSWERS), 207);
    }
    for (String action : new String[]{"begin", "finish", "details"}) {
      post(other, RADIO, action, Map.of("id", radio, "content", RADIO_ANSWERS), 207);
    }
    assertNull(enteringDao.findById(entering).getStartTime());
    assertNull(radioDao.findById(radio).getStartTime());
    request(owner).body(Map.of("id", entering)).post(ENTERING + "getById").then()
        .statusCode(200).body("code", is(200)).body("data.content", is(ENTERING_SOURCE)).body("data.status", is(0));
    request(owner).body(Map.of("id", radio)).post(RADIO + "details").then()
        .statusCode(200).body("code", is(200)).body("data.content", is(RADIO_SOURCE)).body("data.status", is(0));
  }

  @Test
  void enteringRejectsForgedMetricsAndQuestionReplacementThenComputesStableResults() {
    String id = entering(1, ENTERING_SOURCE);
    post(owner, ENTERING, "finish", Map.of("id", id, "content", ENTERING_ANSWERS), 202);
    post(owner, ENTERING, "begin", Map.of("id", id), 200);
    ageEntering(id);
    LocalDateTime start = enteringDao.findById(id).getStartTime();
    post(owner, ENTERING, "begin", Map.of("id", id), 200);
    assertEquals(start, enteringDao.findById(id).getStartTime());
    post(owner, ENTERING, "finish", Map.of("id", id, "content", ENTERING_ANSWERS,
        "accuracy", 100, "speed", 9999, "duration", 1, "correctNum", 999), 202);
    post(owner, ENTERING, "finish", Map.of("id", id,
        "content", ENTERING_ANSWERS.replace("停止", "错误")), 202);
    assertEquals(ENTERING_SOURCE, enteringDao.findById(id).getContent());
    assertEquals(1, enteringDao.findById(id).getStatus());
    post(owner, ENTERING, "finish", Map.of("id", id, "content", ENTERING_ANSWERS), 200);
    request(owner).body(Map.of("id", id)).post(ENTERING + "getById").then().statusCode(200)
        .body("code", is(200)).body("data.accuracy", is(50.0f)).body("data.correctNum", is(1))
        .body("data.errorNum", is(1)).body("data.status", is(2));
    PostEnteringExerciseEntity stored = enteringDao.findById(id);
    assertTrue(stored.getDuration() >= 120);
    assertTrue(stored.getSpeed() <= 2);
    LocalDateTime end = stored.getEndTime();
    post(owner, ENTERING, "finish", Map.of("id", id, "content", ENTERING_ANSWERS), 200);
    post(owner, ENTERING, "finish", Map.of("id", id, "content", "[]"), 208);
    post(owner, ENTERING, "begin", Map.of("id", id), 208);
    assertEquals(end, enteringDao.findById(id).getEndTime());
    assertEquals(stored.getContent(), enteringDao.findById(id).getContent());
  }

  @Test
  void articleLineWrappingCannotReplaceServerTextOrInflateSpeedWithExtraInput() {
    String id = entering(0, "[\"甲乙丙丁\"]");
    post(owner, ENTERING, "begin", Map.of("id", id), 200);
    ageEntering(id);
    String answer = "[{\"font\":\"甲乙\",\"value\":\"甲乙\"},{\"font\":\"丙丁\",\"value\":\"丙错额外内容\"}]";
    post(owner, ENTERING, "finish", Map.of("id", id, "content", answer), 200);
    request(owner).body(Map.of("id", id)).post(ENTERING + "getById").then().statusCode(200)
        .body("code", is(200)).body("data.accuracy", is(37.5f))
        .body("data.correctNum", is(3)).body("data.errorNum", is(5));
    assertTrue(enteringDao.findById(id).getSpeed() <= 2);
  }

  @Test
  void englishAcceptsExistingPunctuationDisplayAndUsesCapturedWords() {
    String id = entering(2, "[\"Go, now!\"]");
    post(owner, ENTERING, "begin", Map.of("id", id), 200);
    post(owner, ENTERING, "finish", Map.of("id", id, "content",
        "[{\"font\":\"Go , \",\"value\":\"Go , \"},{\"font\":\"now !\",\"value\":\"no !\"}]"), 200);
    request(owner).body(Map.of("id", id)).post(ENTERING + "getById").then().statusCode(200)
        .body("code", is(200)).body("data.correctNum", is(3)).body("data.errorNum", is(1));
  }

  @Test
  void pinyinArticleKeepsLiteralPunctuationWithoutEnglishSpacing() {
    String id = entering(4, "[\"甲，乙。\"]");
    post(owner, ENTERING, "begin", Map.of("id", id), 200);
    post(owner, ENTERING, "finish", Map.of("id", id, "content",
        "[{\"font\":\"甲，乙。\",\"value\":\"甲，乙。\"}]"), 200);
    request(owner).body(Map.of("id", id)).post(ENTERING + "getById").then()
        .body("code", is(200)).body("data.accuracy", is(100.0f)).body("data.correctNum", is(4));
  }

  @Test
  void exactEnglishPrefixWithExtraWordsDoesNotReceiveFullAccuracy() {
    String id = entering(2, "[\"Go now\"]");
    post(owner, ENTERING, "begin", Map.of("id", id), 200);
    post(owner, ENTERING, "finish", Map.of("id", id, "content",
        "[{\"font\":\"Go now\",\"value\":\"Go now EXTRA\"}]"), 200);
    request(owner).body(Map.of("id", id)).post(ENTERING + "getById").then()
        .body("code", is(200)).body("data.accuracy", is(66.67f))
        .body("data.correctNum", is(2)).body("data.errorNum", is(1));
  }

  @Test
  void radioRejectsForgedScoreAndQuestionReplacementThenScoresListeningAnswers() {
    String id = radio(0, 0);
    post(owner, RADIO, "finish", Map.of("id", id, "content", RADIO_ANSWERS), 202);
    post(owner, RADIO, "begin", Map.of("id", id), 200);
    QuarkusTransaction.requiringNew().run(() -> radioDao.findById(id).setStartTime(LocalDateTime.now().minusSeconds(120)));
    LocalDateTime start = radioDao.findById(id).getStartTime();
    post(owner, RADIO, "begin", Map.of("id", id), 200);
    assertEquals(start, radioDao.findById(id).getStartTime());
    post(owner, RADIO, "finish", Map.of("id", id, "content", RADIO_ANSWERS,
        "score", 100, "accuracy", 100, "passNumber", 2, "duration", 1), 202);
    post(owner, RADIO, "finish", Map.of("id", id, "content", RADIO_ANSWERS.replace("BRAVO", "wrong")), 202);
    assertEquals(RADIO_SOURCE, radioDao.findById(id).getContent());
    post(owner, RADIO, "finish", Map.of("id", id, "content", RADIO_ANSWERS), 200);
    request(owner).body(Map.of("id", id)).post(RADIO + "details").then().statusCode(200)
        .body("code", is(200)).body("data.score", is(50.0f)).body("data.accuracy", is(50.0f))
        .body("data.passNumber", is(1)).body("data.errorNumber", is(1)).body("data.status", is(2));
    PostRadiotelephoneTrainEntity stored = radioDao.findById(id);
    assertTrue(stored.getDuration() >= 120);
    LocalDateTime end = stored.getEndTime();
    post(owner, RADIO, "finish", Map.of("id", id, "content", RADIO_ANSWERS), 200);
    post(owner, RADIO, "finish", Map.of("id", id, "content", "[]"), 208);
    post(owner, RADIO, "begin", Map.of("id", id), 208);
    post(other, RADIO, "finish", Map.of("id", id, "content", RADIO_ANSWERS), 207);
    assertEquals(end, radioDao.findById(id).getEndTime());
    assertEquals(stored.getContent(), radioDao.findById(id).getContent());
  }

  @Test
  void radioMeaningRecallGradesOnlyTheConfiguredAnswerField() {
    String id = radio(1, 1);
    post(owner, RADIO, "begin", Map.of("id", id), 200);
    post(owner, RADIO, "finish", Map.of("id", id, "content", RADIO_ANSWERS), 200);
    request(owner).body(Map.of("id", id)).post(RADIO + "details").then().statusCode(200)
        .body("code", is(200)).body("data.score", is(100.0f)).body("data.passNumber", is(2));
  }

  private String entering(int type, String content) {
    return QuarkusTransaction.requiringNew().call(() -> {
      PostEnteringExerciseEntity entity = new PostEnteringExerciseEntity();
      entity.setCreateUserId(owner.getId());
      entity.setType(type);
      entity.setStatus(0);
      entity.setContent(content);
      return enteringDao.save(entity).getId();
    });
  }

  private String radio(int trainType, int type) {
    return QuarkusTransaction.requiringNew().call(() -> {
      PostRadiotelephoneTrainEntity entity = new PostRadiotelephoneTrainEntity();
      entity.setUserId(owner.getId());
      entity.setStatus(0);
      entity.setTrainType(trainType);
      entity.setType(type);
      entity.setNumber(2);
      entity.setSpeed(BigDecimal.valueOf(60));
      entity.setContent(RADIO_SOURCE);
      return radioDao.save(entity).getId();
    });
  }

  private void ageEntering(String id) {
    QuarkusTransaction.requiringNew().run(() -> enteringDao.findById(id).setStartTime(LocalDateTime.now().minusSeconds(120)));
  }

  private void post(UserEntity user, String base, String action, Map<String, ?> body, int code) {
    request(user).body(body).post(base + action).then().statusCode(200).body("code", is(code));
  }

  private RequestSpecification request(UserEntity user) {
    return given().header("token", user.getToken()).header("deviceId", user.getDeviceId())
        .contentType("application/json");
  }
}
