package com.nip.service;

import com.nip.dao.TelegramTrainDao;
import com.nip.dao.TelegramTrainFloorContentDao;
import com.nip.dao.TelegramTrainFloorDao;
import com.nip.dao.UserDao;
import com.nip.dto.TelegramTrainDto;
import com.nip.entity.TelegramTrainEntity;
import com.nip.entity.TelegramTrainFloorContentEntity;
import com.nip.entity.TelegramTrainFloorEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nip.common.constants.BaseConstants.DEVICE_ID;
import static com.nip.common.constants.BaseConstants.TOKEN;
import static com.nip.common.constants.BaseConstants.TRAIN_ID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ClassicHandSettlementRegressionTest {
  @Inject TelegramTrainDao trainDao;
  @Inject TelegramTrainFloorDao floorDao;
  @Inject TelegramTrainFloorContentDao contentDao;
  @Inject UserDao userDao;

  private String token;
  private String device;
  private String ownerId;

  @BeforeEach
  void createOwner() {
    token = "classic-hand-" + UUID.randomUUID();
    device = "classic-device-" + UUID.randomUUID();
    ownerId = Fixtures.user(userDao, token, device).getId();
  }

  @Test
  void firstPauseScoresSubmittedAnswersAndKeepsTheCurrentPageOnFinish() {
    TelegramTrainDto request = training(1, 1, 0, System.currentTimeMillis() - 60_000);
    correctAnswers(request);
    String floorId = request.getTrainFloors().getFirst().getFloor().getId();
    request.getTrain().setNowFloorId(floorId);

    TelegramTrainEntity paused = submit("pauseTelegramTrain", request);
    assertEquals(0, new BigDecimal(paused.getAccuracy()).compareTo(new BigDecimal("100")));
    assertEquals("2", paused.getSpeed());
    assertTrue(paused.getAccumulatedActiveMillis() >= 60_000);
    assertEquals(floorId, paused.getNowFloorId());

    request.getTrain().setNowFloorId(null);
    TelegramTrainEntity finished = submit("endTelegramTrain", request);
    assertEquals(paused.getAccumulatedActiveMillis(), finished.getAccumulatedActiveMillis());
    assertEquals(floorId, finished.getNowFloorId());
    assertEquals(3, finished.getStatus());
  }

  @Test
  void firstFinishUsesCurrentAnswersAndTheBoundedRateContract() {
    TelegramTrainDto request = training(1, 2, 2264, null);
    correctAnswers(request);
    request.getTrain().setSpeed("999999");
    request.getTrain().setAccuracy("0");
    request.getTrain().setSustainTime("999999");

    TelegramTrainEntity finished = submit("endTelegramTrain", request);
    assertEquals(0, new BigDecimal(finished.getAccuracy()).compareTo(new BigDecimal("100")));
    assertEquals("53", finished.getSpeed());
    assertEquals(2, finished.getTotalNumber());
    assertEquals(0, finished.getErrorNumber());
    assertEquals(2264L, finished.getAccumulatedActiveMillis());
  }

  @Test
  void partiallyWrongFirstSubmissionIsNotScoredFromThePreviousBlankAnswer() {
    TelegramTrainDto request = training(1, 2, 60_000, null);
    request.getTrainFloors().getFirst().getFloorContents().getFirst().setMoresValue("[0,1]");
    request.getTrainFloors().getFirst().getFloorContents().get(1).setMoresValue("[0]");

    TelegramTrainEntity finished = submit("endTelegramTrain", request);
    assertEquals(0, new BigDecimal(finished.getAccuracy()).compareTo(new BigDecimal("50")));
    assertEquals(1, finished.getErrorNumber());
    assertEquals(1, finished.getTotalKnockNumber());
  }

  @Test
  void endedAndLegacyTrainingsRejectBothLifecycleAndSingleContentWrites() {
    for (int protocol : new int[]{0, 1}) {
      int status = protocol == 0 ? 0 : 3;
      TelegramTrainDto request = training(protocol, status, 0, null);
      String contentId = request.getTrainFloors().getFirst().getFloorContents().getFirst().getId();
      given().contentType(ContentType.JSON).header(TOKEN, token).header(DEVICE_ID, device)
          .body(Map.of("id", contentId, "moresValue", "[0,1]", "moresTime", "[100]"))
          .post("/api/telegramTrain/saveFloorContent")
          .then().statusCode(200).body("code", is(208));
      given().contentType(ContentType.JSON).header(TOKEN, token).header(DEVICE_ID, device)
          .body(request).post("/api/telegramTrain/startTelegramTrain")
          .then().statusCode(200).body("code", is(208));

      TelegramTrainDto unchanged = details(request.getTrain().getId());
      assertEquals(status, unchanged.getTrain().getStatus());
      assertEquals("[]", unchanged.getTrainFloors().getFirst().getFloorContents().getFirst().getMoresValue());
    }
  }

  @Test
  void foreignCurrentPageRollsBackTheAnswersAndClockTogether() {
    TelegramTrainDto request = training(1, 1, 0, System.currentTimeMillis() - 60_000);
    TelegramTrainDto other = training(1, 0, 0, null);
    correctAnswers(request);
    request.getTrain().setNowFloorId(other.getTrainFloors().getFirst().getFloor().getId());

    given().contentType(ContentType.JSON).header(TOKEN, token).header(DEVICE_ID, device)
        .body(request).post("/api/telegramTrain/pauseTelegramTrain")
        .then().statusCode(200).body("code", is(207));

    TelegramTrainDto unchanged = details(request.getTrain().getId());
    assertEquals(1, unchanged.getTrain().getStatus());
    assertEquals(0L, unchanged.getTrain().getAccumulatedActiveMillis());
    assertEquals("[]", unchanged.getTrainFloors().getFirst().getFloorContents().getFirst().getMoresValue());
  }

  @Test
  void actualSingleLetterCreationAcceptsBareKeyAndFlatPulses() {
    TelegramTrainDto request = createdPayload("A", 0, 0);
    request.getTrainFloors().getFirst().getFloorContents().getFirst().setMoresValue("[0,1]");
    TelegramTrainEntity result = submit("endTelegramTrain", request);
    assertEquals(1, result.getTotalNumber());
    assertEquals(0, result.getErrorNumber());
    assertEquals("1", result.getSpeed());
    assertEquals(0, new BigDecimal(result.getAccuracy()).compareTo(new BigDecimal("100")));
  }

  @Test
  void numericShortAndLongPulsesUseThePersistedAlphabet() {
    TelegramTrainDto shortRequest = createdPayload("1", 1, 1);
    shortRequest.getTrainFloors().getFirst().getFloor().setNumberType(0);
    shortRequest.getTrainFloors().getFirst().getFloorContents().getFirst().setMoresValue("[0,1]");
    assertEquals(0, submit("endTelegramTrain", shortRequest).getErrorNumber());

    TelegramTrainDto longRequest = createdPayload("1", 1, 0);
    longRequest.getTrainFloors().getFirst().getFloorContents().getFirst().setMoresValue("[0,1]");
    assertEquals(1, submit("endTelegramTrain", longRequest).getErrorNumber());
  }

  @Test
  void fourCharacterWordsAreScoredAsOneExerciseUnit() {
    String keys = "[\"1\",\"2\",\"3\",\"8\"]";
    TelegramTrainDto complete = createdPayload(keys, 21, 1);
    complete.getTrainFloors().getFirst().getFloorContents().getFirst()
        .setMoresValue("[[0,1],[0,0,1],[0,0,0,1,1],[1,0,0]]");
    TelegramTrainEntity result = submit("endTelegramTrain", complete);
    assertEquals(1, result.getTotalNumber());
    assertEquals(0, result.getErrorNumber());
    assertEquals("1", result.getSpeed());

    TelegramTrainDto incomplete = createdPayload(keys, 21, 1);
    incomplete.getTrainFloors().getFirst().getFloorContents().getFirst()
        .setMoresValue("[[0,1],[0,0,1],[],[1,0,0]]");
    TelegramTrainEntity incorrect = submit("endTelegramTrain", incomplete);
    assertEquals(1, incorrect.getTotalNumber());
    assertEquals(1, incorrect.getErrorNumber());
    assertEquals(0, new BigDecimal(incorrect.getAccuracy()).compareTo(BigDecimal.ZERO));
  }

  @Test
  void unsentEntriesDoNotInflateTheMeasuredRate() {
    TelegramTrainDto request = training(1, 2, 60_000, null);
    request.getTrainFloors().getFirst().getFloorContents().getFirst().setMoresValue("[0,1]");
    TelegramTrainEntity result = submit("endTelegramTrain", request);
    assertEquals(2, result.getTotalNumber());
    assertEquals(1, result.getErrorNumber());
    assertEquals("1", result.getSpeed());
  }

  private TelegramTrainDto createdPayload(String key, int type, int numberType) {
    String id = given().contentType(ContentType.JSON).header(TOKEN, token).header(DEVICE_ID, device)
        .body(Map.of("train", Map.of("type", type), "trainFloors", List.of(Map.of(
            "floor", Map.of("type", type, "numberType", numberType),
            "floorContents", List.of(Map.of("moresKey", key, "moresValue", "[]", "moresTime", "[]"))))))
        .post("/api/telegramTrain/saveTelegramTrain")
        .then().statusCode(200).body("code", is(200)).extract().path("data.id");
    QuarkusTransaction.requiringNew().run(() -> {
      TelegramTrainEntity train = trainDao.findById(id);
      train.setStatus(2);
      train.setAccumulatedActiveMillis(60_000L);
      train.setSustainTime("60000");
    });
    return details(id);
  }

  private TelegramTrainDto training(int protocol, int status, long elapsed, Long activeSince) {
    String id = QuarkusTransaction.requiringNew().call(() -> {
      TelegramTrainEntity train = new TelegramTrainEntity();
      train.setCreateUserId(ownerId);
      train.setType(0);
      train.setProtocolVersion(protocol);
      train.setStatus(status);
      train.setAccumulatedActiveMillis(elapsed);
      train.setActiveSince(activeSince);
      train.setSustainTime(Long.toString(elapsed));
      trainDao.saveAndFlush(train);
      TelegramTrainFloorEntity floor = new TelegramTrainFloorEntity();
      floor.setTrainId(train.getId());
      floor.setSort(0);
      floorDao.saveAndFlush(floor);
      for (int i = 0; i < 2; i++) {
        TelegramTrainFloorContentEntity content = new TelegramTrainFloorContentEntity();
        content.setFloorId(floor.getId());
        content.setSort(i);
        content.setMoresKey(i == 0 ? "A" : "B");
        content.setMoresValue("[]");
        content.setMoresTime("[]");
        contentDao.saveAndFlush(content);
      }
      return train.getId();
    });
    return details(id);
  }

  private void correctAnswers(TelegramTrainDto request) {
    request.getTrainFloors().getFirst().getFloorContents().getFirst().setMoresValue("[0,1]");
    request.getTrainFloors().getFirst().getFloorContents().get(1).setMoresValue("[1,0,0,0]");
  }

  private TelegramTrainDto details(String id) {
    return given().contentType(ContentType.JSON).header(TOKEN, token).header(DEVICE_ID, device)
        .body(Map.of(TRAIN_ID, id)).post("/api/telegramTrain/getById")
        .then().statusCode(200).body("code", is(200))
        .extract().jsonPath().getObject("data", TelegramTrainDto.class);
  }

  private TelegramTrainEntity submit(String action, TelegramTrainDto request) {
    return given().contentType(ContentType.JSON).header(TOKEN, token).header(DEVICE_ID, device)
        .body(request).post("/api/telegramTrain/" + action)
        .then().statusCode(200).body("code", is(200))
        .extract().jsonPath().getObject("data", TelegramTrainEntity.class);
  }
}
