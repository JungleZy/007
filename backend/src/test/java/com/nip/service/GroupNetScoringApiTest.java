package com.nip.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.DeviceDao;
import com.nip.dao.DeviceScoringRuleDao;
import com.nip.dao.DeviceTypeDao;
import com.nip.dao.GroupNetTrainDao;
import com.nip.dao.UserDao;
import com.nip.dto.DeviceScoringRuleDto;
import com.nip.entity.DeviceEntity;
import com.nip.entity.DeviceScoringRuleEntity;
import com.nip.entity.DeviceTypeEntity;
import com.nip.entity.GroupNetTrainEntity;
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
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GroupNetScoringApiTest {
  @Inject UserDao userDao;
  @Inject DeviceDao deviceDao;
  @Inject DeviceTypeDao typeDao;
  @Inject DeviceScoringRuleDao ruleDao;
  @Inject GroupNetTrainDao trainDao;
  @Inject DeviceScoringRuleService ruleService;
  private UserEntity owner;
  private UserEntity other;
  private DeviceEntity device;
  private DeviceEntity otherDevice;
  private DeviceTypeEntity type;
  private DeviceScoringRuleEntity rule;

  @BeforeEach
  void seed() {
    owner = Fixtures.user(userDao, UUID.randomUUID().toString(), "group-score-owner");
    other = Fixtures.user(userDao, UUID.randomUUID().toString(), "group-score-other");
    QuarkusTransaction.requiringNew().run(() -> {
      type = new DeviceTypeEntity();
      type.setTypeName("group-score-" + UUID.randomUUID());
      typeDao.save(type);
      device = device();
      otherDevice = device();
      rule = rule(device, GroupNetScoringTest.RULES);
      rule(otherDevice, GroupNetScoringTest.RULES.replace("0.25", "99"));
    });
  }

  private DeviceEntity device() {
    DeviceEntity value = new DeviceEntity();
    value.setDeviceTypeId(type.getId());
    value.setDeviceNumber("J210-742");
    value.setDeviceName("score-" + UUID.randomUUID());
    return deviceDao.save(value);
  }

  private DeviceScoringRuleEntity rule(DeviceEntity device, String content) {
    DeviceScoringRuleEntity value = new DeviceScoringRuleEntity();
    value.setDeviceId(device.getId());
    value.setRuleContent(content);
    return ruleDao.save(value);
  }

  @AfterEach
  void cleanup() {
    QuarkusTransaction.requiringNew().run(() -> {
      trainDao.delete("createUser = ?1", owner.getId());
      ruleDao.delete("deviceId = ?1 or deviceId = ?2", device.getId(), otherDevice.getId());
      deviceDao.deleteById(device.getId());
      deviceDao.deleteById(otherDevice.getId());
      typeDao.deleteById(type.getId());
      userDao.deleteById(owner.getId());
      userDao.deleteById(other.getId());
    });
  }

  private RequestSpecification request(UserEntity user) {
    return given().header("token", user.getToken()).header("deviceId", user.getDeviceId())
        .contentType("application/json");
  }

  private int create() {
    return request(owner).body(Map.of("deviceId", device.getId(), "deviceType", type.getId(), "topic", GroupNetScoringTest.TOPIC))
        .post("/api/groupNetTrain/saveTrain").then().statusCode(200).body("code", is(200))
        .extract().path("data.id");
  }

  @Test
  void apiDatabaseAndDetailUseFrozenDeviceRuleAndDoNotAcceptForgedOrRepeatedScores() {
    int id = create();
    QuarkusTransaction.requiringNew().run(() -> ruleDao.findById(rule.getId()).setRuleContent(
        GroupNetScoringTest.RULES.replace("0.25", "99")));
    request(owner).body(Map.of("id", id, "answer", GroupNetScoringTest.answer(), "score", 9999))
        .post("/api/groupNetTrain/submitAnswer").then().statusCode(200).body("code", is(202));
    assertNull(trainDao.findById(id).getScore());
    request(other).body(Map.of("id", id, "answer", GroupNetScoringTest.answer()))
        .post("/api/groupNetTrain/submitAnswer").then().statusCode(200).body("code", is(207));
    request(owner).body(Map.of("id", id, "answer", GroupNetScoringTest.answer()))
        .post("/api/groupNetTrain/submitAnswer").then().statusCode(200).body("code", is(200)).body("data.score", is(5.5f));
    GroupNetTrainEntity stored = trainDao.findById(id);
    assertEquals(0, new BigDecimal("5.50").compareTo(stored.getScore()));
    assertEquals(GroupNetScoringTest.answer(), stored.getAnswer());
    String details = request(owner).queryParam("id", id).get("/api/groupNetTrain/details")
        .then().statusCode(200).body("code", is(200)).body("data.score", is(5.5f)).extract().path("data.content");
    assertEquals(stored.getContent(), details);
    request(owner).body(Map.of("id", id, "answer", GroupNetScoringTest.answer()))
        .post("/api/groupNetTrain/submitAnswer").then().statusCode(200).body("code", is(200)).body("data.score", is(5.5f));
    request(owner).body(Map.of("id", id, "answer", "{}"))
        .post("/api/groupNetTrain/submitAnswer").then().statusCode(200).body("code", is(202));
    request(other).queryParam("id", id).get("/api/groupNetTrain/details")
        .then().statusCode(200).body("code", is(207));
    assertEquals(0, new BigDecimal("5.50").compareTo(trainDao.findById(id).getScore()));
  }

  @Test
  void historicalCompletedScoresStayUnchangedAndUnfrozenUnfinishedTrainingCannotBeScored() {
    GroupNetTrainEntity history = new GroupNetTrainEntity();
    history.setCreateUser(owner.getId());
    history.setDeviceId(device.getId());
    history.setTopic(GroupNetScoringTest.TOPIC);
    history.setAnswer("{}");
    history.setScore(new BigDecimal("77"));
    history.setScoringRuleContent("");
    trainDao.saveAndFlush(history);
    request(owner).body(Map.of("id", history.getId(), "answer", "{}"))
        .post("/api/groupNetTrain/submitAnswer").then().statusCode(200).body("code", is(200)).body("data.score", is(77.0f));
    request(owner).queryParam("id", history.getId()).get("/api/groupNetTrain/details")
        .then().statusCode(200).body("code", is(200)).body("data.score", is(77.0f));
    GroupNetTrainEntity unfinished = new GroupNetTrainEntity();
    unfinished.setCreateUser(owner.getId());
    unfinished.setDeviceId(device.getId());
    unfinished.setTopic(GroupNetScoringTest.TOPIC);
    trainDao.saveAndFlush(unfinished);
    request(owner).body(Map.of("id", unfinished.getId(), "answer", GroupNetScoringTest.answer()))
        .post("/api/groupNetTrain/submitAnswer").then().statusCode(200).body("code", is(202));
    assertNull(trainDao.findById(unfinished.getId()).getScore());
  }

  @Test
  void ruleCannotBeReassignedToAnotherDeviceAndMissingRulesCannotStartTraining() {
    DeviceScoringRuleDto moved = new DeviceScoringRuleDto();
    moved.setId(rule.getId());
    moved.setDeviceId(otherDevice.getId());
    moved.setRuleContent(GroupNetScoringTest.RULES);
    assertThrows(IllegalArgumentException.class, () -> ruleService.save(moved));
    assertEquals(device.getId(), ruleDao.findById(rule.getId()).getDeviceId());
    QuarkusTransaction.requiringNew().run(() -> ruleDao.deleteById(rule.getId()));
    request(owner).body(Map.of("deviceId", device.getId(), "deviceType", type.getId(), "topic", GroupNetScoringTest.TOPIC))
        .post("/api/groupNetTrain/saveTrain").then().statusCode(200).body("code", is(202));
    assertEquals(0, trainDao.count("createUser = ?1", owner.getId()));
  }

  @Test
  void completeZeroRulePersistsACompletedZeroAndSameAnswerRetryRemainsStable() {
    QuarkusTransaction.requiringNew().run(() -> ruleDao.findById(rule.getId()).setRuleContent(
        GroupNetScoringTest.RULES.replace("2}", "0}").replace("3}", "0}").replace("0.25}", "0}")));
    int id = create();
    request(owner).body(Map.of("id", id, "answer", GroupNetScoringTest.answer()))
        .post("/api/groupNetTrain/submitAnswer").then().statusCode(200).body("code", is(200));
    assertEquals(0, trainDao.findById(id).getScore().signum());
    request(owner).body(Map.of("id", id, "answer", GroupNetScoringTest.answer()))
        .post("/api/groupNetTrain/submitAnswer").then().statusCode(200).body("code", is(200));
    request(owner).body(Map.of("id", id, "answer", "{}"))
        .post("/api/groupNetTrain/submitAnswer").then().statusCode(200).body("code", is(202));
    assertEquals(0.0f, request(owner).queryParam("id", id).get("/api/groupNetTrain/details")
        .then().statusCode(200).body("code", is(200)).extract().jsonPath().getFloat("data.score"));
  }

  @Test
  void fabricatedAdditionalNetworkAddressCannotCreateAScorableTraining() {
    JsonArray topic = JSONUtils.fromJson(GroupNetScoringTest.TOPIC, JsonArray.class);
    topic.add(JSONUtils.fromJson("{\"xy\":[0,0],\"value\":{\"isParameter\":\"网路地址\",\"value\":319}}", JsonObject.class));
    request(owner).body(Map.of("deviceId", device.getId(), "deviceType", type.getId(), "topic", JSONUtils.toJson(topic)))
        .post("/api/groupNetTrain/saveTrain").then().statusCode(200).body("code", is(202));
    assertEquals(0, trainDao.count("createUser = ?1", owner.getId()));
  }
}
