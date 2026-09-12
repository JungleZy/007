package com.nip.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.UserDao;
import com.nip.dao.general.key.GeneralKeyPatDao;
import com.nip.dao.general.key.GeneralKeyPatPageDao;
import com.nip.dao.general.key.GeneralKeyPatUserDao;
import com.nip.dao.general.key.GeneralKeyPatUserValueDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainPageDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainUserDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainUserValueDao;
import com.nip.dto.general.GeneralTickerPatTrainUpdateDto;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.key.GeneralKeyPatUserEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainUserEntity;
import com.nip.service.general.GeneralKeyPatService;
import com.nip.service.general.GeneralTickerPatService;
import com.nip.testsupport.Fixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GeneralCaptureContractTest {
  private static final String REFERENCE = "[\"1\",\"1\",\"1\",\"1\"]";
  private static final String TICKER_RULE = """
      {"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":4,"r":2,"l":1},"skew":51,
       "code":{"dot":{"base":30,"l":0,"r":0,"max":0},"dash":{"base":50,"l":0,"r":0,"max":0}},
       "gap":{"little":{"base":40,"l":0,"r":0,"max":0},"middle":{"base":60,"l":0,"r":0,"max":0},
              "large":{"base":90,"l":0,"r":0,"max":0}},
       "other":{"errorCode":{"l":0,"max":0},"quantoCode":{"l":0,"max":0},"quantoGroup":{"l":0,"max":0},
                "alterError":{"l":0,"max":0},"quantoRow":{"l":0,"max":0},"bunchGroup":{"l":0,"max":0}}}
      """;
  private static final String KEY_RULE = """
      {"rateUnit":"FOUR_CHARACTER_GROUPS_PER_MINUTE","wpm":{"base":1,"r":0.25,"l":1},
       "other":{"errorCode":0,"muchLessGroups":0,"correctMistakes":0,"lessPage":0,"lessReturnLine":0,
                "muchLessLine":0,"muchLessCode":0,"errorPage":0,"nonStandart":0,"alterError":0,"bunchGroup":0,"lessGap":0}}
      """;

  enum Mode {
    TICKER("/api/generalTickerPatTrain", "general_ticker_pat"),
    KEY("/api/generalKeyPat", "general_key_pat");

    final String path;
    final String table;

    Mode(String path, String table) {
      this.path = path;
      this.table = table;
    }
  }

  private record Room(Mode mode, int id, String ruleId) {}
  private record Result(Integer attempt, LocalDateTime captureStartedAt, Long activeMillis,
                        Integer finished, BigDecimal score, String speed, String deductions,
                        LocalDateTime finishedAt, List<String> raw) {}

  @Inject GeneralTickerPatService ticker;
  @Inject GeneralKeyPatService key;
  @Inject GeneralTickerPatTrainDao tickerTrains;
  @Inject GeneralTickerPatTrainPageDao tickerPages;
  @Inject GeneralTickerPatTrainUserDao tickerUsers;
  @Inject GeneralTickerPatTrainUserValueDao tickerValues;
  @Inject GeneralKeyPatDao keyTrains;
  @Inject GeneralKeyPatPageDao keyPages;
  @Inject GeneralKeyPatUserDao keyUsers;
  @Inject GeneralKeyPatUserValueDao keyValues;
  @Inject GradingRuleDao rules;
  @Inject UserDao users;
  @Inject DataSource dataSource;
  @Inject ObjectMapper mapper;

  private UserEntity owner;
  private UserEntity alice;
  private UserEntity bob;
  private UserEntity teacher;
  private UserEntity outsider;
  private final List<Room> rooms = new ArrayList<>();
  private final List<String> ruleIds = new ArrayList<>();
  private final List<UserEntity> actors = new ArrayList<>();

  @BeforeEach
  void createActors() {
    owner = actor();
    alice = actor();
    bob = actor();
    teacher = actor();
    outsider = actor();
  }

  @AfterEach
  void removeOnlyThisTestsRoomsRulesAndActors() {
    for (Room room : rooms) {
      if (room.mode == Mode.TICKER) ticker.delete(room.id);
      else key.delete(room.id);
    }
    QuarkusTransaction.requiringNew().run(() -> {
      ruleIds.forEach(rules::deleteById);
      actors.forEach(user -> users.deleteById(user.getId()));
    });
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void tokenIdentitySeparatesStudentsAndRejectsOutsidersAndNonTeachers(Mode mode) throws Exception {
    Room room = create(mode, 1);
    Map<String, Object> upload = upload(room, 1, 0, List.of(interval(0, 4000)));
    upload.put("userId", bob.getId());
    for (UserEntity denied : List.of(outsider, teacher, owner)) {
      rejected(post(room, "uploadResult", denied, upload));
      rejected(post(room, "finish", denied, control(room, 0)));
      rejected(post(room, "reset", denied, control(room, 0)));
    }
    for (UserEntity denied : List.of(alice, outsider)) {
      assertThrows(IllegalArgumentException.class, () -> status(room, 2, denied));
    }
    assertEquals(1, roomStatus(room));
    ok(post(room, "uploadResult", alice, upload));
    assertEquals(1, result(room, alice).raw.size());
    assertTrue(result(room, bob).raw.isEmpty(), "The forged userId cannot create Bob's answer");
    rejected(page(room, 1, alice, bob));
    rejected(page(room, 1, outsider, alice));
    assertTrue(ok(page(room, 1, teacher, alice)).path("data").path("submitted").asBoolean());

    ok(post(room, "uploadResult", bob, upload(room, 1, 0, List.of(interval(0, 8000)))));
    Result bobsAnswer = result(room, bob);
    Map<String, Object> finish = control(room, 0);
    finish.put("userId", bob.getId());
    ok(post(room, "finish", alice, finish));
    assertEquals(1, result(room, alice).finished);
    assertEquals(bobsAnswer, result(room, bob), "Alice's finish must not settle Bob");
    ok(post(room, "reset", alice, finish));
    assertTrue(result(room, alice).raw.isEmpty());
    assertEquals(bobsAnswer, result(room, bob), "Alice's reset must not erase Bob");
    status(room, 2, teacher);
    assertEquals(3, roomStatus(room), "A role-1 teacher can close a room created by someone else");
  }

  @Test
  void tickerRawTimelineFrozenRuleAndReportUseOneAuthoritativeResult() throws Exception {
    Room room = create(Mode.TICKER, 1);
    Map<String, Object> upload = upload(room, 1, 0, List.of(interval(0, 2000), interval(10000, 12000)));
    upload.put("speed", "99999");
    upload.put("accuracy", "0");
    upload.put("errorNumber", 999);
    ok(post(room, "uploadResult", alice, upload));
    JsonNode before = ok(page(room, 1, alice, alice)).path("data");
    freezeProbe(room);
    Map<String, Object> finish = control(room, 0);
    finish.put("speed", "99999");
    finish.put("score", 99999);
    finish.put("validTime", 1);
    ok(post(room, "finish", alice, finish));

    Result result = result(room, alice);
    assertEquals(4000L, result.activeMillis);
    decimal("60", result.speed);
    decimal("262", result.score);
    assertEquals(112, mapper.readTree(result.deductions).path("wpmScore").asInt());
    JsonNode detail = ok(post(room, "detail", alice, Map.of("id", room.id, "uid", alice.getId())))
        .path("data").path("userInfoList").get(0);
    decimal("60", detail.path("speed").asText());
    decimal("262", detail.path("score").asText());
    decimal("100", detail.path("accuracy").asText());
    assertEquals(0, detail.path("errorNumber").asInt());
    assertEquals(4, detail.path("validTime").asLong(), "Report duration is capture time, not room wall time");
    assertEquals(mapper.readTree(result.deductions), mapper.readTree(detail.path("deductInfo").asText()));
    JsonNode after = ok(page(room, 1, alice, alice)).path("data");
    assertEquals(before.path("messageBody"), after.path("messageBody"));
    assertEquals(before.path("standard"), after.path("standard"));
    assertEquals(before.path("finishInfo"), after.path("finishInfo"));
    assertEquals(before.path("savedCaptureIntervals"), after.path("savedCaptureIntervals"));
    ok(post(room, "finish", alice, finish));
    assertEquals(result, result(room, alice), "Repeated finish must preserve score, deductions and finish timestamp");
  }

  @Test
  void electronicRateCountsActualValueIncludingUnknownButNotCorrectionAndPreservesFractionalScore() throws Exception {
    Room room = create(Mode.KEY, 1);
    Map<String, Object> upload = upload(room, 1, 0, List.of(interval(0, 15000)));
    upload.put("pageValue", List.of(Map.of("sort", 0, "key", REFERENCE,
        "value", "[\"1\",\"?\",\"#\",\"2\"]", "time", "[10,10,10,10]")));
    ok(post(room, "uploadResult", alice, upload));
    List<String> raw = result(room, alice).raw;
    freezeProbe(room);
    JsonNode finished = ok(post(room, "finish", alice, control(room, 0))).path("data").get(0);
    Result result = result(room, alice);
    assertEquals(15000L, result.activeMillis);
    decimal("3", result.speed);
    decimal("150.5", result.score);
    assertTrue(finished.path("score").isNumber(), "A fractional score must remain a JSON number");
    decimal("150.5", finished.path("score").asText());
    decimal("3", mapper.readTree(result.deductions).path("speedNumber").asText());
    assertEquals(raw, result.raw, "Correction parsing must not overwrite raw submitted events");
    JsonNode detail = ok(post(room, "patDetail", alice,
        Map.of("trainId", room.id, "pageNumber", 1, "userId", alice.getId()))).path("data");
    assertEquals(15, detail.path("duration").asLong());
    decimal("3", detail.path("speed").asText());
    decimal("150.5", detail.path("score").asText());
    assertEquals(mapper.readTree(result.deductions), mapper.readTree(detail.path("deductInfo").asText()));
    ok(post(room, "finish", alice, control(room, 0)));
    assertEquals(result, result(room, alice));
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void retriesAndOutOfOrderDeliveryDoNotDoubleCountAndInvalidTimelinesDoNotReplaceSavedPages(Mode mode) throws Exception {
    Room room = create(mode, 101);
    Map<String, Object> second = upload(room, 2, 0, List.of(interval(10000, 14000)));
    ok(post(room, "uploadResult", alice, second));
    ok(post(room, "uploadResult", alice, second));
    Result saved = result(room, alice);
    assertEquals(1, saved.raw.size());
    rejected(post(room, "uploadResult", alice, upload(room, 1, 0, List.of(interval(13000, 15000)))));
    rejected(post(room, "uploadResult", alice,
        upload(room, 2, 0, List.of(interval(10000, 12000), interval(9000, 10000)))));
    rejected(post(room, "uploadResult", alice, upload(room, 2, 0, List.of(interval(0, 3600000)))));
    assertEquals(saved, result(room, alice));
    assertFalse(ok(page(room, 1, alice, alice)).path("data").path("submitted").asBoolean());
    ok(post(room, "uploadResult", alice, upload(room, 1, 0, List.of(interval(0, 4000)))));
    ok(post(room, "finish", alice, control(room, 0)));
    Result finished = result(room, alice);
    assertEquals(8000L, finished.activeMillis);
    assertEquals(2, finished.raw.size());
    decimal(mode == Mode.TICKER ? "60" : "15", finished.speed);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void continuingSamePageKeepsCaptureHistoryAndLateSnapshotCannotEraseNewEvents(Mode mode) throws Exception {
    Room room = create(mode, 1);
    Map<String, Object> first = upload(room, 1, 0, List.of(interval(0, 4000)));
    ok(post(room, "uploadResult", alice, first));
    Map<String, Object> extended = upload(room, 1, 0, List.of(interval(0, 4000), interval(10000, 14000)));
    if (mode == Mode.KEY) {
      extended.put("pageValue", List.of(Map.of("sort", 0, "key", REFERENCE,
          "value", "[\"1\",\"1\",\"1\",\"1\",\"2\"]", "time", "[100,100,100,100,100]")));
    } else {
      extended.put("messageBody", List.of(Map.of("moresKey", REFERENCE,
          "patKeys", "[\"1\",\"1\",\"1\",\"1\",\"2\"]",
          "moresValue", "[[1,0],[1,0],[1,0],[1,0],[1,0]]",
          "moresTime", "[[75,25],[75,25],[75,25],[75,25],[75,25]]",
          "patLogs", JSONUtils.toJson(List.of(tickerLogs(), tickerLogs(), tickerLogs(), tickerLogs(), tickerLogs())))));
    }
    ok(post(room, "uploadResult", alice, extended));
    Result current = result(room, alice);
    rejected(post(room, "uploadResult", alice, first));
    Map<String, Object> rewrittenHistory = new HashMap<>(extended);
    rewrittenHistory.put("captureIntervals", List.of(interval(1000, 4000), interval(10000, 14000)));
    rejected(post(room, "uploadResult", alice, rewrittenHistory));
    assertEquals(current, result(room, alice));
    ok(post(room, "finish", alice, control(room, 0)));
    assertEquals(8000L, result(room, alice).activeMillis);
    decimal(mode == Mode.TICKER ? "38" : "9", result(room, alice).speed);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void rawDurationsCannotFitOneMillisecondCaptureAndRejectedPageLeavesSavedDataUntouched(Mode mode) throws Exception {
    Room room = create(mode, 101);
    ok(post(room, "uploadResult", alice, upload(room, 1, 0, List.of(interval(0, 4000)))));
    Result saved = result(room, alice);
    Map<String, Object> impossible = upload(room, 2, 0, List.of(interval(10000, 10001)));
    if (mode == Mode.KEY) {
      impossible.put("pageValue", List.of(Map.of("sort", 0, "key", REFERENCE,
          "value", REFERENCE, "time", "[1000,1000,1000,1000]")));
    } else {
      impossible.put("messageBody", List.of(Map.of("moresKey", REFERENCE, "patKeys", REFERENCE,
          "moresValue", "[[1,0],[1,0],[1,0],[1,0]]",
          "moresTime", "[[750,250],[750,250],[750,250],[750,250]]",
          "patLogs", "[]")));
    }
    rejected(post(room, "uploadResult", alice, impossible));
    assertEquals(saved, result(room, alice));
    assertFalse(ok(page(room, 2, alice, alice)).path("data").path("submitted").asBoolean());
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void resetFencesLateUploadFinishResetAndStartWithoutChangingNewAttempt(Mode mode) throws Exception {
    Room room = create(mode, 1);
    Map<String, Object> stale = upload(room, 1, 0, List.of(interval(0, 4000)));
    ok(post(room, "uploadResult", alice, stale));
    ok(post(room, "finish", alice, control(room, 0)));
    ok(post(room, "reset", alice, control(room, 0)));
    assertEquals(1, result(room, alice).attempt);
    assertTrue(result(room, alice).raw.isEmpty());
    ageCapture(room);
    ok(post(room, "uploadResult", alice, upload(room, 1, 1, List.of(interval(0, 8000)))));
    Result current = result(room, alice);
    rejected(post(room, "uploadResult", alice, stale));
    rejected(post(room, "finish", alice, control(room, 0)));
    rejected(post(room, "reset", alice, control(room, 0)));
    rejected(given().header("token", alice.getToken()).header("deviceId", alice.getDeviceId())
        .queryParam("trainId", room.id).queryParam("attempt", 0).get(room.mode.path + "/startTrain"));
    assertEquals(current, result(room, alice));
    ok(post(room, "finish", alice, control(room, 1)));
    assertEquals(8000L, result(room, alice).activeMillis);
    decimal(mode == Mode.TICKER ? "30" : "8", result(room, alice).speed);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void closingAllowsNotificationDelayedTailThenSettlesOfflineAndAbsentStudents(Mode mode) throws Exception {
    Room room = create(mode, 1);
    status(room, 2, owner);
    QuarkusTransaction.requiringNew().run(() -> {
      LocalDateTime origin = LocalDateTime.now().minusSeconds(120);
      LocalDateTime teacherEndedAt = origin.plusSeconds(100);
      if (mode == Mode.TICKER) {
        tickerTrains.findById(room.id, LockModeType.PESSIMISTIC_WRITE)
            .setStartTime(origin).setEndTime(teacherEndedAt);
        tickerUsers.findByTrainIdAndRole(room.id, 0).forEach(user -> user.setCaptureStartedAt(origin));
      } else {
        keyTrains.findById(room.id, LockModeType.PESSIMISTIC_WRITE)
            .setStartTime(origin).setEndTime(teacherEndedAt);
        keyUsers.findByTrainIdAndRole(room.id, 0).forEach(user -> user.setCaptureStartedAt(origin));
      }
    });
    assertEquals(3, roomStatus(room));
    assertNotEquals(1, result(room, alice).finished);
    // The teacher stopped at 100s; this learner received the notification after capturing 110s–114s.
    // The server is already at 120s, so the tail is real elapsed capture inside the 60-second grace period.
    ok(post(room, "uploadResult", alice, upload(room, 1, 0, List.of(interval(110000, 114000)))));
    Result tail = result(room, alice);
    rejected(post(room, "uploadResult", alice, upload(room, 1, 0, List.of(interval(0, 3600000)))));
    rejected(post(room, "reset", alice, control(room, 0)));
    assertEquals(tail, result(room, alice));
    expireAndSettle(room);
    assertEquals(2, roomStatus(room));
    assertEquals(1, result(room, alice).finished);
    assertEquals(1, result(room, bob).finished);
    assertEquals(4000L, result(room, alice).activeMillis);
    assertEquals(0L, result(room, bob).activeMillis);
    decimal(mode == Mode.TICKER ? "262" : "153.5", result(room, alice).score);
    decimal(mode == Mode.TICKER ? "146" : "149", result(room, bob).score);
    Result settledTail = result(room, alice);
    Result absent = result(room, bob);
    rejected(post(room, "uploadResult", bob, upload(room, 1, 0, List.of(interval(0, 4000)))));
    rejected(post(room, "uploadResult", alice,
        upload(room, 1, 0, List.of(interval(110000, 114000), interval(115000, 119000)))));
    ok(post(room, "finish", alice, control(room, 0)));
    assertEquals(settledTail, result(room, alice), "After grace expiry, neither new capture nor finish retries can change the result");
    ok(post(room, "finish", bob, control(room, 0)));
    assertEquals(absent, result(room, bob));
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void finalRoomWriteFailureRollsBackEveryStudentsSettlementAndRetryClosesRoom(Mode mode) throws Exception {
    Room room = create(mode, 1);
    ok(post(room, "uploadResult", alice, upload(room, 1, 0, List.of(interval(0, 4000)))));
    status(room, 2, owner);
    Result aliceBefore = result(room, alice);
    Result bobBefore = result(room, bob);
    String constraint = "general_capture_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
      statement.execute("ALTER TABLE " + mode.table + " ADD CONSTRAINT " + constraint
          + " CHECK (create_user <> '" + owner.getId() + "' OR status <> 2)");
      try {
        assertThrows(RuntimeException.class, () -> expireAndSettle(room));
        assertEquals(3, roomStatus(room));
        assertEquals(aliceBefore, result(room, alice), "Already-computed uploaded learner must roll back");
        assertEquals(bobBefore, result(room, bob), "No-page learner must roll back in the same transaction");
      } finally {
        statement.execute("ALTER TABLE " + mode.table + " DROP CHECK " + constraint);
      }
    }
    expireAndSettle(room);
    assertEquals(2, roomStatus(room));
    assertEquals(1, result(room, alice).finished);
    assertEquals(1, result(room, bob).finished);
    decimal(mode == Mode.TICKER ? "262" : "153.5", result(room, alice).score);
    decimal(mode == Mode.TICKER ? "146" : "149", result(room, bob).score);
  }

  @Test
  void malformedJsonAttemptAndIntervalCannotSilentlyCoerceIntoValidCapture() throws Exception {
    Room room = create(Mode.KEY, 1);
    Map<String, Object> upload = upload(room, 1, 0, List.of(interval(0, 4000)));
    ok(post(room, "uploadResult", alice, upload));
    Result before = result(room, alice);
    upload.put("attempt", 0.5);
    rejected(post(room, "uploadResult", alice, upload));
    upload.put("attempt", null);
    rejected(post(room, "uploadResult", alice, upload));
    upload.put("attempt", 0);
    upload.put("captureIntervals", List.of(Map.of("startedMs", 0, "endedMs", 4000.5)));
    rejected(post(room, "uploadResult", alice, upload));
    upload.put("captureIntervals", List.of(Map.of("endedMs", 4000)));
    rejected(post(room, "uploadResult", alice, upload));
    assertEquals(before, result(room, alice));
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void graceWindowFollowsRequestArrivalRatherThanParentLockWaitCompletion(Mode mode) throws Exception {
    Room room = create(mode, 1);
    status(room, 2, owner);
    LocalDateTime arrival = LocalDateTime.now();
    CountDownLatch locked = new CountDownLatch(1);
    // Another learner's write holds the training's parent row; the tail below reaches the server
    // inside the grace window but can only be processed after the window has already elapsed.
    Thread holder = new Thread(() -> QuarkusTransaction.requiringNew().run(() -> {
      if (mode == Mode.TICKER) {
        tickerTrains.findById(room.id, LockModeType.PESSIMISTIC_WRITE).setEndTime(arrival.minusSeconds(57));
      } else {
        keyTrains.findById(room.id, LockModeType.PESSIMISTIC_WRITE).setEndTime(arrival.minusSeconds(57));
      }
      locked.countDown();
      while (LocalDateTime.now().isBefore(arrival.plusSeconds(5))) sleep();
    }));
    holder.start();
    assertTrue(locked.await(30, TimeUnit.SECONDS));
    ok(post(room, "uploadResult", alice, upload(room, 1, 0, List.of(interval(0, 4000)))));
    holder.join();
    assertEquals(1, result(room, alice).raw.size(), "A page that arrived inside the window must survive lock waiting");
    rejected(post(room, "uploadResult", alice,
        upload(room, 1, 0, List.of(interval(0, 4000), interval(5000, 9000)))));
    assertEquals(1, result(room, alice).raw.size());
  }

  private void sleep() {
    try {
      Thread.sleep(50);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private UserEntity actor() {
    String token = "general-capture-" + UUID.randomUUID();
    UserEntity actor = Fixtures.user(users, token, token);
    actors.add(actor);
    return actor;
  }

  private Room create(Mode mode, int groups) throws Exception {
    GradingRuleEntity rule = new GradingRuleEntity();
    rule.setTitle("General capture " + UUID.randomUUID());
    rule.setScore(150);
    rule.setContent(mode == Mode.TICKER ? TICKER_RULE : KEY_RULE);
    rule = rules.save(rule);
    ruleIds.add(rule.getId());
    Map<String, Object> body = new HashMap<>();
    body.put(mode == Mode.TICKER ? "name" : "title", "General capture " + UUID.randomUUID());
    body.put(mode == Mode.TICKER ? "messageNumber" : "totalNumber", groups);
    body.put(mode == Mode.TICKER ? "type" : "messageType", 0);
    body.put("isCable", 0);
    body.put("trainType", 1);
    body.put("isAverage", mode == Mode.TICKER ? false : 0);
    body.put("isRandom", mode == Mode.TICKER ? false : 0);
    body.put("codeSort", false);
    body.put("ruleId", rule.getId());
    body.put("userId", List.of(alice.getId(), bob.getId()));
    int id = ok(post(new Room(mode, 0, rule.getId()), "add", owner, body)).path("data").path("id").asInt();
    Room room = new Room(mode, id, rule.getId());
    rooms.add(room);
    QuarkusTransaction.requiringNew().run(() -> {
      if (mode == Mode.TICKER) {
        tickerUsers.save(new GeneralTickerPatTrainUserEntity().setTrainId(id).setUserId(teacher.getId()).setRole(1));
        tickerPages.find("trainId", id).list().forEach(page -> page.setMoresKey(REFERENCE));
      } else {
        keyUsers.save(new GeneralKeyPatUserEntity().setTrainId(id).setUserId(teacher.getId()).setRole(1));
        keyPages.find("trainId", id).list().forEach(page -> page.setKey(REFERENCE));
      }
    });
    status(room, 1, owner);
    ageCapture(room);
    return room;
  }

  private void ageCapture(Room room) {
    QuarkusTransaction.requiringNew().run(() -> {
      LocalDateTime origin = LocalDateTime.now().minusMinutes(2);
      if (room.mode == Mode.TICKER) {
        tickerTrains.findById(room.id, LockModeType.PESSIMISTIC_WRITE).setStartTime(origin);
        tickerUsers.findByTrainIdAndRole(room.id, 0).forEach(user -> user.setCaptureStartedAt(origin));
      } else {
        keyTrains.findById(room.id, LockModeType.PESSIMISTIC_WRITE).setStartTime(origin);
        keyUsers.findByTrainIdAndRole(room.id, 0).forEach(user -> user.setCaptureStartedAt(origin));
      }
    });
  }

  private void freezeProbe(Room room) {
    QuarkusTransaction.requiringNew().run(() -> {
      GradingRuleEntity rule = rules.findById(room.ruleId);
      rule.setScore(999);
      rule.setContent("{}");
    });
  }

  private void status(Room room, int status, UserEntity actor) {
    if (room.mode == Mode.KEY) {
      key.updateStatus(room.id, status, actor.getToken());
    } else {
      GeneralTickerPatTrainUpdateDto dto = new GeneralTickerPatTrainUpdateDto();
      dto.setTrainId(room.id);
      dto.setStatus(status);
      ticker.updateStatus(dto, actor.getToken());
    }
  }

  private void expireAndSettle(Room room) {
    // Keep expiry and settlement under one parent-row lock so the recovery scheduler cannot race the probe.
    QuarkusTransaction.requiringNew().run(() -> {
      LocalDateTime expired = LocalDateTime.now().minusSeconds(61);
      if (room.mode == Mode.TICKER) {
        tickerTrains.findById(room.id, LockModeType.PESSIMISTIC_WRITE).setEndTime(expired);
        ticker.settleExpired(room.id);
      } else {
        keyTrains.findById(room.id, LockModeType.PESSIMISTIC_WRITE).setEndTime(expired);
        key.settleExpired(room.id);
      }
    });
  }

  private int roomStatus(Room room) {
    return QuarkusTransaction.requiringNew().call(() -> room.mode == Mode.TICKER
        ? tickerTrains.findById(room.id).getStatus() : keyTrains.findById(room.id).getStatus());
  }

  private Result result(Room room, UserEntity actor) {
    return QuarkusTransaction.requiringNew().call(() -> {
      if (room.mode == Mode.TICKER) {
        var user = tickerUsers.findByUserIdAndTrainId(actor.getId(), room.id);
        List<String> raw = tickerValues.find("trainId = ?1 and userId = ?2 order by floorNumber", room.id, actor.getId())
            .list().stream().map(value -> JSONUtils.toJson(List.of(value.getMessageBody(), value.getStandard(),
                value.getFinishInfo(), value.getCaptureIntervals()))).toList();
        return new Result(user.getAttempt(), user.getCaptureStartedAt(), user.getActiveMillis(), user.getIsFinish(),
            user.getScore(), user.getSpeed(), user.getDeductInfo(), user.getFinishTime(), raw);
      }
      var user = keyUsers.findByUserIdAndTrainId(actor.getId(), room.id);
      List<String> raw = keyValues.find("trainId = ?1 and userId = ?2 order by pageNumber, sort", room.id, actor.getId())
          .list().stream().map(value -> JSONUtils.toJson(List.of(value.getPageNumber(), value.getSort(),
              value.getKey(), value.getValue(), value.getTime()))).toList();
      return new Result(user.getAttempt(), user.getCaptureStartedAt(), user.getActiveMillis(), user.getIsFinish(),
          user.getScore(), user.getSpeed(), user.getDeductInfo(), user.getFinishTime(), raw);
    });
  }

  private Map<String, Object> upload(Room room, int page, int attempt, List<Map<String, Long>> intervals) {
    Map<String, Object> body = new HashMap<>();
    body.put("trainId", room.id);
    body.put("attempt", attempt);
    body.put("captureIntervals", intervals);
    if (room.mode == Mode.KEY) {
      body.put("pageNumber", page);
      body.put("pageValue", List.of(Map.of("sort", 0, "key", REFERENCE, "value", REFERENCE,
          "time", "[100,100,100,100]")));
    } else {
      body.put("floorNumber", page);
      body.put("messageBody", List.of(Map.of("sort", 0, "moresKey", REFERENCE, "patKeys", REFERENCE,
          "moresValue", "[[1,0],[1,0],[1,0],[1,0]]", "moresTime", "[[75,25],[75,25],[75,25],[75,25]]",
          "patLogs", JSONUtils.toJson(List.of(tickerLogs(), tickerLogs(), tickerLogs(), tickerLogs())))));
      body.put("standard", List.of(Map.of("dot", 25, "line", 75, "codeGap", 25,
          "wordGap", 75, "groupGap", 175, "offSize", 51)));
      body.put("finishInfo", "{\"dot\":25,\"line\":75,\"codeGap\":25,\"wordGap\":75,\"groupGap\":175,\"offSize\":51}");
    }
    return body;
  }

  private List<Map<String, Object>> tickerLogs() {
    return List.of(Map.of("name", "间隔", "key", 2, "value", 75),
        Map.of("name", "划", "key", 1, "value", 75),
        Map.of("name", "间隔", "key", 2, "value", 25),
        Map.of("name", "点", "key", 0, "value", 25));
  }

  private Map<String, Long> interval(long start, long end) {
    return Map.of("startedMs", start, "endedMs", end);
  }

  private Map<String, Object> control(Room room, int attempt) {
    Map<String, Object> body = new HashMap<>();
    body.put(room.mode == Mode.TICKER ? "id" : "trainId", room.id);
    body.put("attempt", attempt);
    return body;
  }

  private Response page(Room room, int page, UserEntity actor, UserEntity target) {
    return post(room, room.mode == Mode.TICKER ? "findPage" : "getPage", actor,
        Map.of(room.mode == Mode.TICKER ? "id" : "trainId", room.id,
            room.mode == Mode.TICKER ? "floorNumber" : "pageNumber", page, "userId", target.getId()));
  }

  private Response post(Room room, String endpoint, UserEntity actor, Object body) {
    return given().header("token", actor.getToken()).header("deviceId", actor.getDeviceId())
        .contentType("application/json").body(body).post(room.mode.path + "/" + endpoint);
  }

  private JsonNode ok(Response response) throws Exception {
    assertEquals(200, response.statusCode(), response.asString());
    JsonNode json = mapper.readTree(response.asString());
    assertEquals(200, json.path("code").asInt(), response.asString());
    return json;
  }

  private void rejected(Response response) throws Exception {
    assertTrue(response.statusCode() == 200 || response.statusCode() == 400, response.asString());
    if (response.statusCode() == 200) {
      int code = mapper.readTree(response.asString()).path("code").asInt();
      assertTrue(code == 202 || code == 500, response.asString());
    }
  }

  private void decimal(String expected, Object actual) {
    assertNotNull(actual);
    assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));
  }
}
