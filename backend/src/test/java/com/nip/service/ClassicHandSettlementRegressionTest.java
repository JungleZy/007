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
    request.getTrainFloors().getFirst().getFloorContents().getFirst().setMoresValue("[[0,1],[0]]");

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
          .body(Map.of("id", contentId, "moresValue", "[[0,1]]", "moresTime", "[100]"))
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
      TelegramTrainFloorContentEntity content = new TelegramTrainFloorContentEntity();
      content.setFloorId(floor.getId());
      content.setSort(0);
      content.setMoresKey("[\"A\",\"B\"]");
      content.setMoresValue("[]");
      content.setMoresTime("[]");
      contentDao.saveAndFlush(content);
      return train.getId();
    });
    return details(id);
  }

  private void correctAnswers(TelegramTrainDto request) {
    request.getTrainFloors().getFirst().getFloorContents().getFirst().setMoresValue("[[0,1],[1,0,0,0]]");
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
