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
import com.nip.service.general.GeneralTelexPatService;
import com.nip.testsupport.Fixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class GeneralTelexPageAnalysisTest {
  private static final String PAGE = "1234 5678";
  private static final String RULE = """
      {"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":120,"r":0,"l":0},
       "other":{"errorCode":0,"muchLessGroups":0,"correctMistakes":0,"lessPage":0,"lessReturnLine":0,
                "muchLessLine":0,"muchLessCode":0,"errorPage":0,"nonStandart":0,"alterError":0,
                "bunchGroup":0,"lessGap":0}}
      """;

  @Inject GeneralTelexPatService telex;
  @Inject GeneralTelexPatDao trains;
  @Inject GeneralTelexPatUserDao members;
  @Inject GeneralTelexPatUserValueDao values;
  @Inject GradingRuleDao rules;
  @Inject UserDao users;
  @Inject ObjectMapper mapper;

  private UserEntity owner;
  private UserEntity alice;
  private UserEntity bob;
  private String trainId;
  private String ruleId;

  @BeforeEach
  void createTraining() throws Exception {
    owner = actor();
    alice = actor();
    bob = actor();
    GradingRuleEntity rule = new GradingRuleEntity();
    rule.setTitle("Page analysis " + UUID.randomUUID());
    rule.setScore(150);
    rule.setContent(RULE);
    ruleId = rules.save(rule).getId();
    Map<String, Object> body = new HashMap<>();
    body.put("title", "Page analysis " + UUID.randomUUID());
    body.put("isCable", 0);
    body.put("totalNumber", 200);
    body.put("trainType", 0);
    body.put("type", 0);
    body.put("patType", 2);
    body.put("ruleId", ruleId);
    body.put("userId", List.of(alice.getId(), bob.getId()));
    trainId = post("add", owner, body).path("id").asText();
    post("updateTrainStatus", owner, Map.of("trainId", trainId, "status", 1));
    QuarkusTransaction.requiringNew().run(() -> {
      LocalDateTime origin = LocalDateTime.now().minusMinutes(2);
      trains.findById(trainId).setStartTime(origin);
      members.findByTrainIdAndRole(trainId, 0).forEach(member -> member.setCaptureStartedAt(origin));
    });
  }

  @AfterEach
  void removeFixtures() {
    if (trainId != null) {
      telex.delete(trainId, owner.getToken());
    }
    QuarkusTransaction.requiringNew().run(() -> {
      if (ruleId != null) {
        rules.deleteById(ruleId);
      }
      for (UserEntity actor : new UserEntity[] {owner, alice, bob}) {
        if (actor != null) {
          users.deleteById(actor.getId());
        }
      }
    });
  }

  @Test
  void confirmedExtensionsRetriesAndOtherUsersKeepTheirOwnPageMillis() throws Exception {
    upload(alice, 2, PAGE, List.of(interval(10000, 13250)));
    JsonNode partial = detail(alice).path("pageAnalyzeVOS");
    assertEquals(1, partial.size(), "未提交的第1页没有采集分析，不能冒充第2页");
    assertPage(partial.get(0), 2, 8, 3250);
    assertEquals(mapper.createArrayNode(), detail(bob).path("pageAnalyzeVOS"));

    upload(alice, 2, PAGE, List.of(interval(10000, 13250), interval(20000, 21250)));
    List<Map<String, Long>> extended = List.of(interval(10000, 13250), interval(20000, 21250),
        interval(30000, 32250));
    upload(alice, 2, PAGE, extended);
    upload(alice, 2, PAGE, extended);
    upload(alice, 1, PAGE, List.of(interval(0, 1250)));
    upload(bob, 2, PAGE, List.of(interval(0, 2500)));

    JsonNode analyses = detail(alice).path("pageAnalyzeVOS");
    assertEquals(2, analyses.size());
    assertPage(analyses.get(0), 1, 8, 1250);
    assertPage(analyses.get(1), 2, 8, 6750);
    assertEquals(384.0, analyses.get(0).path("patNumber").asDouble() * 60000
        / analyses.get(0).path("totalTime").asLong());
    assertPage(detail(bob).path("pageAnalyzeVOS").get(0), 2, 8, 2500);

    JsonNode room = post("detail", owner, Map.of("trainId", trainId));
    JsonNode roomAlice = null;
    for (JsonNode member : room.path("userInfoList")) {
      if (alice.getId().equals(member.path("userId").asText())) {
        roomAlice = member;
      }
    }
    assertTrue(roomAlice != null, "房间详情应包含该参训人");
    assertEquals(analyses, roomAlice.path("pageAnalyzeVOS"), "两个公共详情入口必须使用同一逐页口径");
    var stale = given().header("token", alice.getToken()).header("deviceId", alice.getDeviceId())
        .contentType("application/json")
        .body(Map.of("trainId", trainId, "pageNumber", 2, "patValue", "changed", "protocolVersion", 1,
            "attempt", 1, "captureIntervals", extended))
        .post("/api/generalTelexPat/uploadResult");
    assertEquals(200, stale.statusCode(), stale.asString());
    assertEquals(208, mapper.readTree(stale.asString()).path("code").asInt());
    assertEquals(analyses, detail(alice).path("pageAnalyzeVOS"));

    post("finish", alice, Map.of("trainId", trainId, "attempt", 0));
    JsonNode settled = detail(alice);
    assertEquals(150, settled.path("score").asInt());
    assertEquals(analyses, settled.path("pageAnalyzeVOS"), "结算产生的分析行不能重复计时");
    post("finish", alice, Map.of("trainId", trainId, "attempt", 0));
    assertEquals(settled.path("score"), detail(alice).path("score"));
    JsonNode restored = post("getPage", alice,
        Map.of("trainId", trainId, "userId", alice.getId(), "pageNumber", 2));
    assertEquals(0, restored.path("attempt").asInt(-1));
  }

  @Test
  void emptySubmittedPageHasZeroDurationWhileUnsubmittedPageHasNoAnalysis() throws Exception {
    upload(alice, 2, "", List.of());
    JsonNode analyses = detail(alice).path("pageAnalyzeVOS");
    assertEquals(1, analyses.size());
    assertPage(analyses.get(0), 2, 0, 0);
  }

  @Test
  void commandOperandsAreExcludedFromTheSameCharacterRateAsSettlement() throws Exception {
    upload(alice, 1, "1234 5678\nQTA 1", List.of(interval(0, 1250)));
    JsonNode details = detail(alice);
    JsonNode analysis = details.path("pageAnalyzeVOS").get(0);
    assertPage(analysis, 1, 8, 1250);
    assertEquals(384.0, analysis.path("patNumber").asDouble() * 60000
        / analysis.path("totalTime").asLong());
    assertEquals(384.0, details.path("speed").asDouble());
  }

  @Test
  void historicalPageWithoutCaptureEvidenceDoesNotBorrowAggregateDuration() throws Exception {
    upload(alice, 2, PAGE, List.of(interval(0, 4000)));
    QuarkusTransaction.requiringNew().run(() -> {
      trains.findById(trainId).setProtocolVersion(0);
      values.findRawByTrainIdAndUserId(trainId, alice.getId()).forEach(row -> {
        row.setCaptureIntervals(null);
        row.setReceivedAt(null);
      });
      var member = members.findByUserIdAndTrainId(alice.getId(), trainId);
      member.setCaptureStartedAt(null);
      member.setValidTime(9999);
      member.setValidTimeLog("[9999,9999]");
      member.setActiveMillis(9999000L);
    });
    JsonNode analyses = detail(alice).path("pageAnalyzeVOS");
    assertEquals(1, analyses.size());
    assertPage(analyses.get(0), 2, 8, 0);
  }

  private void assertPage(JsonNode page, int number, int characters, long millis) {
    assertEquals(number, page.path("pageNumber").asInt());
    assertEquals(characters, page.path("patNumber").asInt());
    assertEquals(millis, page.path("totalTime").asLong());
    if (millis > 0) {
      assertTrue(Double.isFinite(page.path("patNumber").asDouble() * 60000
          / page.path("totalTime").asLong()));
    }
  }

  private JsonNode detail(UserEntity actor) throws Exception {
    return post("patDetail", actor, Map.of("trainId", trainId, "userId", actor.getId()));
  }

  private void upload(UserEntity actor, int page, String text, List<Map<String, Long>> intervals) throws Exception {
    post("uploadResult", actor, Map.of("trainId", trainId, "pageNumber", page, "patValue", text,
        "protocolVersion", 1, "attempt", 0, "captureIntervals", intervals));
  }

  private Map<String, Long> interval(long start, long end) {
    return Map.of("startedMs", start, "endedMs", end);
  }

  private UserEntity actor() {
    String token = "telex-page-" + UUID.randomUUID();
    return Fixtures.user(users, token, token);
  }

  private JsonNode post(String endpoint, UserEntity actor, Object body) throws Exception {
    var response = given().header("token", actor.getToken()).header("deviceId", actor.getDeviceId())
        .contentType("application/json").body(body).post("/api/generalTelexPat/" + endpoint);
    assertEquals(200, response.statusCode(), response.asString());
    JsonNode json = mapper.readTree(response.asString());
    assertEquals(200, json.path("code").asInt(), response.asString());
    return json.path("data");
  }
}
