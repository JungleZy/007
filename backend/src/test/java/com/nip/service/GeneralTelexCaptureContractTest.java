package com.nip.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.UserDao;
import com.nip.dao.general.telex.GeneralTelexPatDao;
import com.nip.dao.general.telex.GeneralTelexPatUserDao;
import com.nip.dao.general.telex.GeneralTelexPatUserValueDao;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.telex.GeneralTelexPatUserEntity;
import com.nip.entity.simulation.telex.GeneralTelexPatUserValueEntity;
import com.nip.service.general.GeneralTelexPatService;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.WebSocketSessionProbe;
import com.nip.ws.WebSocketService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组训数据报/电传域的采集契约（评审 SCORE-01/02/03、CONTRACT-01）。
 *
 * <p>断言对象一律是<b>消费者可见的东西</b>：HTTP 信封的 {@code code}、落库的成绩行、
 * 落库的原始提交行、以及教员端 WebSocket 实际收到的帧。写法与
 * {@code GeneralCaptureContractTest}（手键/电子键）一致，只是本域的「页」是一整页文本。
 *
 * <p>速率口径：规则单位由 {@code DatagramGardRule.vue} 固定为字符/分钟，
 * 正文字符数走与个人电传域同一份 {@code PostTelexPatTrainService.characterCount}。
 * 用例正文 {@code "1234 5678"} = 8 个正文字符，配 4 秒采集 → 120 字符/分钟，
 * 这是下面所有分数期望值的来源。
 */
@QuarkusTest
class GeneralTelexCaptureContractTest {

  /** 8 个正文字符、无电传命令，字符计数不依赖命令解析分支。 */
  private static final String PAGE = "1234 5678";

  /** 速率基准 = 120，系数为 0：速率项恒为 0，结算分 == 冻结满分，便于锁「基准跟随」。 */
  private static final String FLAT_RULE = """
      {"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":120,"r":0,"l":0},
       "other":{"errorCode":0,"muchLessGroups":0,"correctMistakes":0,"lessPage":0,"lessReturnLine":0,
                "muchLessLine":0,"muchLessCode":0,"errorPage":0,"nonStandart":0,"alterError":0,
                "bunchGroup":0,"lessGap":0}}
      """;

  /** 速率基准 = 60、每高 1 加 0.5 分：120 字符/分钟 → 速率项 +30，用来锁「码率进分」。 */
  private static final String RATE_RULE = """
      {"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":60,"r":0.5,"l":1},
       "other":{"errorCode":0,"muchLessGroups":0,"correctMistakes":0,"lessPage":0,"lessReturnLine":0,
                "muchLessLine":0,"muchLessCode":0,"errorPage":0,"nonStandart":0,"alterError":0,
                "bunchGroup":0,"lessGap":0}}
      """;

  private record Room(String id, String ruleId) {}

  @Inject GeneralTelexPatService telex;
  @Inject GeneralTelexPatDao trains;
  @Inject GeneralTelexPatUserDao members;
  @Inject GeneralTelexPatUserValueDao values;
  @Inject GradingRuleDao rules;
  @Inject UserDao users;
  @Inject WebSocketService webSocket;
  @Inject DataSource dataSource;
  @Inject ObjectMapper mapper;

  private UserEntity owner;
  private UserEntity alice;
  private UserEntity bob;
  private UserEntity outsider;
  private final List<Room> rooms = new ArrayList<>();
  private final List<String> ruleIds = new ArrayList<>();
  private final List<UserEntity> actors = new ArrayList<>();

  @BeforeEach
  void createActors() {
    owner = actor();
    alice = actor();
    bob = actor();
    outsider = actor();
  }

  @AfterEach
  void removeOnlyThisTestsRoomsRulesAndActors() {
    for (Room room : rooms) {
      telex.delete(room.id, owner.getToken());
    }
    QuarkusTransaction.requiringNew().run(() -> {
      ruleIds.forEach(rules::deleteById);
      actors.forEach(user -> users.deleteById(user.getId()));
    });
  }

  /**
   * 契约一：速率与逐页用时由服务端从采集区间重算，请求体里的 speed/validTime 不再参与任何计算。
   *
   * <p>同一份提交额外塞入 {@code speed:"99999"}/{@code validTime:9999}：若它们仍被采信，
   * 速率分会离谱、{@code valid_time} 会变成 9999，这里一条都不会发生。
   */
  @Test
  void serverRecomputesRateFromCaptureIntervalsAndIgnoresClientReportedSpeed() throws Exception {
    Room room = create(RATE_RULE, 150, 100);
    Map<String, Object> upload = upload(room, 1, 0, PAGE, List.of(interval(0, 4000)));
    upload.put("speed", "99999");
    upload.put("validTime", 9999);
    ok(post("uploadResult", alice, upload));
    ok(post("finish", alice, control(room, 0)));

    GeneralTelexPatUserEntity settled = member(room, alice);
    decimal("120", settled.getSpeed());
    assertEquals(4, settled.getValidTime(), "有效时长必须是采集区间之和，不是客户端上报的 9999");
    assertEquals(4000L, settled.getActiveMillis());
    decimal("180", settled.getScore());
  }

  /**
   * 契约二：结算基准是建训时冻结的规则满分，不是写死的 100，也不是结算时刻的规则。
   */
  @Test
  void settlementBaselineFollowsTheFrozenRuleFullScoreRatherThanAHardcodedHundred() throws Exception {
    Room hundredFifty = create(FLAT_RULE, 150, 100);
    Room eightyEight = create(FLAT_RULE, 88, 100);
    for (Room room : List.of(hundredFifty, eightyEight)) {
      ok(post("uploadResult", alice, upload(room, 1, 0, PAGE, List.of(interval(0, 4000)))));
    }
    // 结算前把两条规则都改成 999：冻结口径成立时，成绩不得跟着变
    QuarkusTransaction.requiringNew().run(() -> ruleIds.forEach(id -> rules.findById(id).setScore(999)));
    for (Room room : List.of(hundredFifty, eightyEight)) {
      ok(post("finish", alice, control(room, 0)));
    }

    decimal("150", member(hundredFifty, alice).getScore());
    decimal("88", member(eightyEight, alice).getScore());
  }

  /**
   * 契约三（致命路径回归）：结算只重建分析行，原始提交行必须原地保留。
   *
   * <p>旧实现在 {@code countScore} 里把该学员全部 value 行删掉后用不含采集字段的 DTO 重建，
   * {@code finish} 跑过一次 {@code capture_intervals} 就永久为空，之后任何重算都只能失败。
   * 因此这里直接断言原始行的 {@code capture_intervals}/{@code received_at}/{@code attempt} 仍在，
   * 并且重复结算后仍在、成绩不变。
   */
  @Test
  void settlementKeepsTheRawCaptureRowsSoScoresRemainRecomputable() throws Exception {
    Room room = create(FLAT_RULE, 150, 200);
    ok(post("uploadResult", alice, upload(room, 1, 0, PAGE, List.of(interval(0, 4000)))));
    ok(post("uploadResult", alice, upload(room, 2, 0, PAGE, List.of(interval(10000, 14000)))));
    ok(post("finish", alice, control(room, 0)));

    List<GeneralTelexPatUserValueEntity> raw = rawRows(room, alice);
    assertEquals(2, raw.size(), "两页原始提交行都必须还在");
    for (GeneralTelexPatUserValueEntity row : raw) {
      assertEquals(PAGE, row.getValue(), "原始整页文本不得被分析行覆盖");
      assertNotNull(row.getCaptureIntervals(), "原始采集时间轴不得被结算抹掉");
      assertFalse(row.getCaptureIntervals().isBlank());
      assertNotNull(row.getReceivedAt());
      assertEquals(0, row.getAttempt());
    }
    assertTrue(analysisRows(room, alice) > 0, "结算必须产出分析行");

    GeneralTelexPatUserEntity settled = member(room, alice);
    assertEquals(8000L, settled.getActiveMillis(), "两页采集时长相加");
    decimal("120", settled.getSpeed());
    ok(post("finish", alice, control(room, 0)));
    assertEquals(2, rawRows(room, alice).size(), "重复结算同样不得动原始行");
    assertEquals(settled.getScore(), member(room, alice).getScore());
    assertEquals(settled.getFinishTime(), member(room, alice).getFinishTime());
  }

  /**
   * 契约四：跨页重叠、页内乱序、越过采集边界的区间一律拒绝，且不得覆盖已保存页。
   */
  @Test
  void overlappingOutOfOrderAndOutOfBoundIntervalsAreRejectedWithoutTouchingSavedPages() throws Exception {
    Room room = create(FLAT_RULE, 150, 200);
    ok(post("uploadResult", alice, upload(room, 1, 0, PAGE, List.of(interval(0, 4000)))));
    String saved = rawRows(room, alice).getFirst().getCaptureIntervals();

    rejected(post("uploadResult", alice, upload(room, 2, 0, PAGE, List.of(interval(3000, 5000)))));
    rejected(post("uploadResult", alice,
        upload(room, 2, 0, PAGE, List.of(interval(10000, 12000), interval(9000, 10000)))));
    rejected(post("uploadResult", alice, upload(room, 2, 0, PAGE, List.of(interval(0, 3600000)))));
    // 改写已确认的历史区间同样要被拒，否则学员可以把慢速改成快速
    rejected(post("uploadResult", alice,
        upload(room, 1, 0, PAGE, List.of(interval(1000, 4000), interval(10000, 14000)))));

    List<GeneralTelexPatUserValueEntity> raw = rawRows(room, alice);
    assertEquals(1, raw.size(), "被拒的页不得落库");
    assertEquals(saved, raw.getFirst().getCaptureIntervals(), "已保存页的采集时间轴必须原样保留");
  }

  /**
   * 契约五：并发两次 finish 只结算一次，教员端只收一帧；结算失败回滚时一帧也不发。
   */
  @Test
  void concurrentFinishSettlesOnceAndFailedSettlementSendsNoPhantomFrame() throws Exception {
    Room room = create(FLAT_RULE, 150, 100);
    ok(post("uploadResult", alice, upload(room, 1, 0, PAGE, List.of(interval(0, 4000)))));
    WebSocketSessionProbe teacher = WebSocketSessionProbe.open(owner.getId(), owner.getToken(), owner.getDeviceId());
    webSocket.onOpen(teacher.session());
    try {
      String constraint = "telex_capture_" + UUID.randomUUID().toString().replace("-", "");
      try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
        statement.execute("ALTER TABLE general_telex_pat_user ADD CONSTRAINT " + constraint
            + " CHECK (user_id <> '" + alice.getId() + "' OR is_finish <> 1)");
        try {
          Response failed = post("finish", alice, control(room, 0));
          // 约束违例可能在 flush 处抛、也可能在提交处抛：两条路径都必须是「失败」，不能是 code:200
          assertTrue(failed.statusCode() != 200
                  || mapper.readTree(failed.asString()).path("code").asInt() != 200,
              failed.asString());
          assertTrue(teacher.outbound().isEmpty(), "结算回滚后不得留下幻影通知：" + teacher.outbound());
          assertNull(member(room, alice).getFinishTime(), "回滚后成绩行不得被写入");
        } finally {
          statement.execute("ALTER TABLE general_telex_pat_user DROP CHECK " + constraint);
        }
      }

      CountDownLatch start = new CountDownLatch(1);
      List<Response> responses = new ArrayList<>();
      List<Thread> racers = new ArrayList<>();
      for (int index = 0; index < 2; index++) {
        Thread racer = new Thread(() -> {
          await(start);
          Response response = post("finish", alice, control(room, 0));
          synchronized (responses) {
            responses.add(response);
          }
        });
        racers.add(racer);
        racer.start();
      }
      start.countDown();
      for (Thread racer : racers) {
        racer.join();
      }
      assertEquals(2, responses.size(), "两个并发请求都必须拿到应答");
      for (Response response : responses) {
        ok(response);
      }
      assertEquals(1, member(room, alice).getIsFinish());
      assertEquals(1, teacher.outbound().size(),
          "并发两次 finish 只应结算一次，教员端只收一帧：" + teacher.outbound());
    } finally {
      webSocket.onClose(teacher.session());
    }
  }

  /**
   * 契约六：授权拒绝统一 207。非参训人上传/结算、非授权者删训练都被拒，且目标数据仍在。
   */
  @Test
  void unauthorizedWritesAreRejectedWith207AndLeaveTheTrainingIntact() throws Exception {
    Room room = create(FLAT_RULE, 150, 100);
    ok(post("uploadResult", alice, upload(room, 1, 0, PAGE, List.of(interval(0, 4000)))));

    for (UserEntity denied : List.of(outsider, owner)) {
      assertEquals(207, code(post("finish", denied, control(room, 0))), "非参训学员不得结算");
      assertEquals(207, code(post("uploadResult", denied, upload(room, 1, 0, PAGE, List.of(interval(0, 4000))))),
          "非参训学员不得上传");
    }
    for (UserEntity denied : List.of(outsider, alice, bob)) {
      assertEquals(207, code(delete(room, denied)), "非创建者、非组训人、非管理员不得删训练");
      assertEquals(207, code(post("updateTrainStatus", denied,
          Map.of("trainId", room.id, "status", 2))), "同上，改状态也是写口径");
    }
    assertEquals(207, code(post("patDetail", outsider,
        Map.of("trainId", room.id, "userId", alice.getId()))), "非参训人不得读他人成绩");
    assertEquals(207, code(post("patDetail", bob,
        Map.of("trainId", room.id, "userId", alice.getId()))), "同班学员不得读他人成绩");
    // 学员读自己的、教员读全班的都必须照旧可用，授权收口不得把参训人挡在训练页外面
    ok(post("patDetail", alice, Map.of("trainId", room.id, "userId", alice.getId())));
    ok(post("patDetail", owner, Map.of("trainId", room.id, "userId", alice.getId())));
    ok(post("detail", alice, Map.of("trainId", room.id)));

    assertNotNull(QuarkusTransaction.requiringNew().call(() -> trains.findById(room.id)), "被拒的删除不得动库");
    assertEquals(1, rawRows(room, alice).size());
  }

  private void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(30, TimeUnit.SECONDS));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private UserEntity actor() {
    String token = "telex-capture-" + UUID.randomUUID();
    UserEntity actor = Fixtures.user(users, token, token);
    actors.add(actor);
    return actor;
  }

  private Room create(String ruleContent, int fullScore, int groups) throws Exception {
    GradingRuleEntity rule = new GradingRuleEntity();
    rule.setTitle("Telex capture " + UUID.randomUUID());
    rule.setScore(fullScore);
    rule.setContent(ruleContent);
    rule = rules.save(rule);
    ruleIds.add(rule.getId());
    Map<String, Object> body = new HashMap<>();
    body.put("title", "Telex capture " + UUID.randomUUID());
    body.put("isCable", 0);
    body.put("totalNumber", groups);
    body.put("trainType", 0);
    body.put("type", 0);
    body.put("patType", 2);
    body.put("ruleId", rule.getId());
    body.put("userId", List.of(alice.getId(), bob.getId()));
    String id = ok(post("add", owner, body)).path("data").path("id").asText();
    Room room = new Room(id, rule.getId());
    rooms.add(room);
    ok(post("updateTrainStatus", owner, Map.of("trainId", id, "status", 1)));
    ageCapture(room);
    return room;
  }

  /** 把采集起点推到两分钟前，让用例里的毫秒区间落在采集边界内。 */
  private void ageCapture(Room room) {
    QuarkusTransaction.requiringNew().run(() -> {
      LocalDateTime origin = LocalDateTime.now().minusMinutes(2);
      trains.findById(room.id, LockModeType.PESSIMISTIC_WRITE).setStartTime(origin);
      members.findByTrainIdAndRole(room.id, 0).forEach(user -> user.setCaptureStartedAt(origin));
    });
  }

  private GeneralTelexPatUserEntity member(Room room, UserEntity actor) {
    return QuarkusTransaction.requiringNew()
        .call(() -> members.findByUserIdAndTrainId(actor.getId(), room.id));
  }

  private List<GeneralTelexPatUserValueEntity> rawRows(Room room, UserEntity actor) {
    return QuarkusTransaction.requiringNew()
        .call(() -> values.findRawByTrainIdAndUserId(room.id, actor.getId()));
  }

  private long analysisRows(Room room, UserEntity actor) {
    return QuarkusTransaction.requiringNew().call(() -> values
        .findByTrainIdAndUserIdOrderByPageNumberAscSortAsc(room.id, actor.getId())
        .stream().filter(row -> row.getSort() > -1).count());
  }

  private Map<String, Object> upload(Room room, int page, int attempt, String patValue,
      List<Map<String, Long>> intervals) {
    Map<String, Object> body = new HashMap<>();
    body.put("trainId", room.id);
    body.put("pageNumber", page);
    body.put("patValue", patValue);
    body.put("protocolVersion", 1);
    body.put("attempt", attempt);
    body.put("captureIntervals", intervals);
    return body;
  }

  private Map<String, Long> interval(long start, long end) {
    return Map.of("startedMs", start, "endedMs", end);
  }

  private Map<String, Object> control(Room room, int attempt) {
    Map<String, Object> body = new HashMap<>();
    body.put("trainId", room.id);
    body.put("attempt", attempt);
    return body;
  }

  private Response post(String endpoint, UserEntity actor, Object body) {
    return given().header("token", actor.getToken()).header("deviceId", actor.getDeviceId())
        .contentType("application/json").body(body).post("/api/generalTelexPat/" + endpoint);
  }

  private Response delete(Room room, UserEntity actor) {
    return given().header("token", actor.getToken()).header("deviceId", actor.getDeviceId())
        .queryParam("trainId", room.id).get("/api/generalTelexPat/delete");
  }

  private JsonNode ok(Response response) throws Exception {
    assertEquals(200, response.statusCode(), response.asString());
    JsonNode json = mapper.readTree(response.asString());
    assertEquals(200, json.path("code").asInt(), response.asString());
    return json;
  }

  private int code(Response response) throws Exception {
    assertEquals(200, response.statusCode(), response.asString());
    return mapper.readTree(response.asString()).path("code").asInt();
  }

  /** 参数类拒绝：202（可修正）或 208（终态），都不是 200，也都不得改动已落库数据。 */
  private void rejected(Response response) throws Exception {
    int code = code(response);
    assertTrue(code == 202 || code == 208, response.asString());
  }

  private void decimal(String expected, Object actual) {
    assertNotNull(actual);
    assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())),
        "期望 " + expected + "，实际 " + actual);
  }
}
