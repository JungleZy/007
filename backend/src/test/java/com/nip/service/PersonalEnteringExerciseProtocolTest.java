package com.nip.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nip.dao.EnteringExerciseDao;
import com.nip.dao.EnteringExerciseWordStockDao;
import com.nip.dao.EnteringStatisticalDao;
import com.nip.dao.UserDao;
import com.nip.entity.EnteringExerciseEntity;
import com.nip.entity.EnteringExerciseWordStockEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PersonalEnteringExerciseProtocolTest {
  @Inject UserDao users;
  @Inject EnteringExerciseDao exercises;
  @Inject EnteringExerciseWordStockDao stocks;
  @Inject EnteringStatisticalDao statistics;
  private UserEntity actor;
  private UserEntity outsider;
  private final Map<Integer, String> previousStocks = new HashMap<>();
  private final Set<Integer> newStocks = new HashSet<>();

  @BeforeEach
  void actors() {
    actor = Fixtures.user(users, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    outsider = Fixtures.user(users, UUID.randomUUID().toString(), UUID.randomUUID().toString());
  }

  @AfterEach
  void cleanup() {
    QuarkusTransaction.requiringNew().run(() -> {
      exercises.delete("createUserId", actor.getId());
      statistics.delete("userId", actor.getId());
      previousStocks.forEach((id, content) -> stocks.findById(id).setContent(content));
      newStocks.forEach(stocks::deleteById);
      users.deleteById(actor.getId());
      users.deleteById(outsider.getId());
    });
  }

  @Test
  void realCodeInputControlsGroupsClockOwnershipAndTerminalRetries() {
    stock(0, "[{\"font\":\"为了\",\"pys\":\"le\"},{\"font\":\"了解\",\"pys\":\"liao\"}]");
    JsonObject created = call(actor, "add", Map.of("type", 0, "name", "personal-protocol"), 200).getAsJsonObject("data");
    String id = created.get("id").getAsString();
    JsonArray question = JsonParser.parseString(created.get("content").getAsString()).getAsJsonArray();
    assertEquals("le", code(question, 0));
    assertEquals("liao", code(question, 1));
    stock(0, "[{\"font\":\"改库\",\"pys\":\"changed\"}]");
    call(outsider, "begin", Map.of("id", id), 207);
    call(actor, "begin", Map.of("id", id), 200);
    QuarkusTransaction.requiringNew().run(() -> exercises.findById(id).setActiveStartedAt(LocalDateTime.now().minusSeconds(61)));
    LocalDateTime began = exercises.findById(id).getStartTime();
    call(actor, "begin", Map.of("id", id), 200);
    assertEquals(began, exercises.findById(id).getStartTime());
    String answers = "[{\"font\":\"forged\",\"value\":\"xx\",\"pys\":[{\"py\":\"x\",\"trueOrfalse\":true}],\"trueOrfalse\":true},{\"value\":\"li\",\"trueOrfalse\":true}]";
    JsonObject paused = call(actor, "pause", Map.of("id", id, "content", answers, "duration", 9999, "accuracy", 100, "correctNum", 99), 200).getAsJsonObject("data");
    assertEquals(0, paused.get("correctNum").getAsInt());
    assertEquals(1, paused.get("errorNum").getAsInt());
    assertEquals(0, paused.get("accuracy").getAsInt());
    assertTrue(paused.get("duration").getAsInt() >= 61 && paused.get("duration").getAsInt() < 120);
    JsonArray captured = JsonParser.parseString(paused.get("content").getAsString()).getAsJsonArray();
    assertEquals("为了", captured.get(0).getAsJsonObject().get("font").getAsString());
    assertEquals("le", code(captured, 0));
    assertEquals("li", captured.get(1).getAsJsonObject().get("value").getAsString());
    long elapsed = exercises.findById(id).getElapsedMillis();
    call(actor, "pause", Map.of("id", id, "content", answers), 200);
    assertEquals(elapsed, exercises.findById(id).getElapsedMillis());
    call(actor, "getById", Map.of("id", id), 200);
    call(outsider, "pause", Map.of("id", id, "content", answers), 207);
    call(actor, "goTo", Map.of("id", id), 200);
    QuarkusTransaction.requiringNew().run(() -> exercises.findById(id).setActiveStartedAt(LocalDateTime.now().minusSeconds(2)));
    String finishedAnswers = "[{\"value\":\"le\"},{\"value\":\"li\"}]";
    JsonObject finished = call(actor, "finish", Map.of("id", id, "content", finishedAnswers), 200).getAsJsonObject("data");
    assertEquals(1, finished.get("correctNum").getAsInt());
    assertEquals(0, finished.get("errorNum").getAsInt());
    assertEquals(100, finished.get("accuracy").getAsInt());
    assertTrue(finished.get("duration").getAsInt() >= paused.get("duration").getAsInt() + 2);
    LocalDateTime ended = exercises.findById(id).getEndTime();
    call(actor, "finish", Map.of("id", id, "content", finishedAnswers), 200);
    assertEquals(ended, exercises.findById(id).getEndTime());
    assertEquals(1, statistics.findByUserIdAndTypeAndChildType(actor.getId(), 0, 0).getTotalCount());
    assertEquals(String.valueOf(finished.get("duration").getAsInt()), statistics.findByUserIdAndTypeAndChildType(actor.getId(), 0, 0).getTotalTime());
    call(actor, "finish", Map.of("id", id, "content", answers), 208);
    call(actor, "pause", Map.of("id", id, "content", finishedAnswers), 208);
    call(actor, "goTo", Map.of("id", id), 208);
    call(actor, "begin", Map.of("id", id), 208);
    assertEquals(ended, exercises.findById(id).getEndTime());
  }

  @Test
  void wordAndArticleSourcesFreezeFirstPinyinAndLastWubiCodes() {
    stock(2, "[\"了解\"]");
    JsonObject pinyin = call(actor, "add", Map.of("type", 2, "name", "word"), 200).getAsJsonObject("data");
    assertEquals("lejie", code(JsonParser.parseString(pinyin.get("content").getAsString()).getAsJsonArray(), 0));
    stock(2, "[\"工\"]");
    JsonObject wubi = call(actor, "add", Map.of("type", 9, "name", "wubi"), 200).getAsJsonObject("data");
    assertEquals("aaaa", code(JsonParser.parseString(wubi.get("content").getAsString()).getAsJsonArray(), 0));
    String wubiId = wubi.get("id").getAsString();
    call(actor, "begin", Map.of("id", wubiId), 200);
    JsonObject wubiResult = call(actor, "finish", Map.of("id", wubiId, "content", "[{\"value\":\"aaax\",\"trueOrfalse\":true}]"), 200).getAsJsonObject("data");
    assertEquals(0, wubiResult.get("correctNum").getAsInt());
    assertEquals(1, wubiResult.get("errorNum").getAsInt());
    stock(4, "[\"了A。！\"]");
    JsonObject article = call(actor, "add", Map.of("type", 4, "name", "article"), 200).getAsJsonObject("data");
    JsonArray rows = JsonParser.parseString(article.get("content").getAsString()).getAsJsonArray();
    assertEquals("le", code(rows, 0));
    assertEquals("A", code(rows, 1));
    assertEquals(".", code(rows, 2));
    assertEquals("!", code(rows, 3));
    String id = article.get("id").getAsString();
    call(actor, "begin", Map.of("id", id), 200);
    JsonObject finish = call(actor, "finish", Map.of("id", id, "content", "[{\"value\":\"l\"},{\"value\":\"A\"},{\"value\":\".\"},{\"value\":\"?\"}]"), 200).getAsJsonObject("data");
    assertEquals(2, finish.get("correctNum").getAsInt());
    assertEquals(1, finish.get("errorNum").getAsInt());
    assertEquals(66.67, finish.get("accuracy").getAsDouble());
  }

  @Test
  void defaultStockRareCharactersAndLiteralKeysHaveRealFrozenCodes() {
    stock(2, "[\"地鵏\",\"鞍韂\",\"羈泊\",\"膙子\",\"ǘ驴子\",\"遛跶\",\"啰嗦\"]");
    JsonObject wubi = call(actor, "add", Map.of("type", 9, "name", "default-stock-wubi"), 200).getAsJsonObject("data");
    JsonArray rows = JsonParser.parseString(wubi.get("content").getAsString()).getAsJsonArray();
    List<String> expected = List.of("fbgeho", "afpvafqy", "lafcirg", "exkjbbbb", "vcyntbbbb", "qyvpkhdp", "klqykfpi");
    JsonArray input = new JsonArray();
    for (int i = 0; i < expected.size(); i++) {
      assertEquals(expected.get(i), code(rows, i));
      JsonObject value = new JsonObject();
      value.addProperty("value", expected.get(i));
      input.add(value);
    }
    String id = wubi.get("id").getAsString();
    call(actor, "begin", Map.of("id", id), 200);
    JsonObject finished = call(actor, "finish", Map.of("id", id, "content", input.toString()), 200).getAsJsonObject("data");
    assertEquals(7, finished.get("correctNum").getAsInt());
    assertEquals(0, finished.get("errorNum").getAsInt());
    assertEquals(100, finished.get("accuracy").getAsDouble());

    stock(1, "[\"碩\"]");
    JsonObject single = call(actor, "add", Map.of("type", 1, "name", "default-stock-pinyin"), 200).getAsJsonObject("data");
    assertEquals("shuo", code(JsonParser.parseString(single.get("content").getAsString()).getAsJsonArray(), 0));
    stock(3, "[\"㯲\",\"啰\",\"掟\",\"琯\",\"羈\",\"膙\",\"蚆\",\"跶\",\"韂\",\"鲘\",\"鵏\",\"ǘ\",\"（\"]");
    JsonObject words = call(actor, "add", Map.of("type", 3, "name", "default-stock-literals"), 200).getAsJsonObject("data");
    JsonArray pinyin = JsonParser.parseString(words.get("content").getAsString()).getAsJsonArray();
    List<String> pinyinCodes = List.of("jin", "luo", "zheng", "guan", "ji", "jiang", "ba", "da", "chan", "hou", "bu", "v", "(");
    for (int i = 0; i < pinyinCodes.size(); i++) assertEquals(pinyinCodes.get(i), code(pinyin, i));
  }

  @Test
  void unsupportedCharacterCannotSilentlyDisappearFromAWord() {
    stock(2, "[\"地鿿\"]");
    long before = exercises.count("createUserId", actor.getId());
    call(actor, "add", Map.of("type", 2, "name", "unknown-pinyin"), 202);
    call(actor, "add", Map.of("type", 9, "name", "unknown-wubi"), 202);
    assertEquals(before, exercises.count("createUserId", actor.getId()));
  }

  @Test
  void legacyPauseIsNeverCompletedOrPromotedWhenCreatingReplacement() {
    stock(0, "[{\"font\":\"为了\",\"pys\":\"le\"}]");
    String id = QuarkusTransaction.requiringNew().call(() -> {
      EnteringExerciseEntity old = new EnteringExerciseEntity();
      old.setCreateUserId(actor.getId());
      old.setType(0);
      old.setStatus(3);
      old.setContent("legacy-pause");
      old.setDuration(15);
      return exercises.save(old).getId();
    });
    call(actor, "getById", Map.of("id", id), 208);
    call(actor, "begin", Map.of("id", id), 208);
    call(actor, "goTo", Map.of("id", id), 208);
    call(actor, "finish", Map.of("id", id, "content", "[]"), 208);
    call(actor, "add", Map.of("type", 0, "name", "replacement"), 200);
    EnteringExerciseEntity unchanged = exercises.findById(id);
    assertEquals(3, unchanged.getStatus());
    assertEquals(0, unchanged.getProtocolVersion());
    assertEquals("legacy-pause", unchanged.getContent());
    assertEquals(15, unchanged.getDuration());
    assertNull(statistics.findByUserIdAndTypeAndChildType(actor.getId(), 0, 0));
    QuarkusTransaction.requiringNew().run(() -> exercises.findById(id).setStatus(2));
    assertEquals("legacy-pause", call(actor, "getById", Map.of("id", id), 200).getAsJsonObject("data").get("content").getAsString());
  }

  private void stock(int type, String content) {
    QuarkusTransaction.requiringNew().run(() -> {
      EnteringExerciseWordStockEntity stock = stocks.findByType(type);
      if (stock == null) {
        stock = new EnteringExerciseWordStockEntity();
        stock.setType(type);
        stock.setContent(content);
        newStocks.add(stocks.save(stock).getId());
      } else {
        if (!newStocks.contains(stock.getId())) previousStocks.putIfAbsent(stock.getId(), stock.getContent());
        stock.setContent(content);
      }
    });
  }

  private JsonObject call(UserEntity user, String action, Object body, int code) {
    String response = given().header("token", user.getToken()).header("deviceId", user.getDeviceId())
        .contentType("application/json").body(body).post("/api/enteringExercise/" + action)
        .then().statusCode(200).extract().asString();
    JsonObject result = JsonParser.parseString(response).getAsJsonObject();
    assertEquals(code, result.get("code").getAsInt(), response);
    return result;
  }

  private String code(JsonArray rows, int index) {
    StringBuilder result = new StringBuilder();
    for (var letter : rows.get(index).getAsJsonObject().getAsJsonArray("pys")) result.append(letter.getAsJsonObject().get("py").getAsString());
    return result.toString();
  }
}
