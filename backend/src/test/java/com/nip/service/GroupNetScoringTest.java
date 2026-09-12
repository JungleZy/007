package com.nip.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nip.common.utils.JSONUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class GroupNetScoringTest {
  static final String RULES = """
      [{"paramName":"网路地址","weight":2},{"paramName":"单台地址","weight":3},{"paramName":"信道","weight":0.25}]
      """;
  static final String TOPIC = readTopic();

  private static String readTopic() {
    try (var input = GroupNetScoringTest.class.getResourceAsStream("/group-net-j210-topic.json")) {
      if (input == null) throw new IllegalStateException("Missing J210 fixture");
      return JSONUtils.toJson(JSONUtils.fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8), JsonArray.class));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read J210 fixture", e);
    }
  }

  static String answer() {
    JsonObject answer = new JsonObject();
    answer.add("chananel1", JSONUtils.fromJson("[{\"indexs\":1,\"receptionChananel\":0,\"sendChananel\":\"29.0\"}]", JsonArray.class));
    JsonArray serials = new JsonArray();
    for (int i = 0; i < 20; i++) serials.add(new JsonObject());
    serials.get(0).getAsJsonObject().addProperty("networkdress", "0319");
    serials.get(19).getAsJsonObject().addProperty("dressname", 299);
    answer.add("serialNumbers", serials);
    return JSONUtils.toJson(answer);
  }

  @Test
  void earnsEachCellWeightUsingDecimalValuesAndSerialMappingAtInclusiveBounds() {
    GroupNetScoring.Result result = GroupNetScoring.calculate(7, TOPIC, GroupNetScoring.freeze(7, RULES, TOPIC), answer());
    assertEquals(0, new BigDecimal("5.50").compareTo(result.score()));
    JsonArray cells = JSONUtils.fromJson(result.details(), JsonArray.class);
    assertTrue(cells.get(0).getAsJsonObject().get("correct").getAsBoolean());
    assertEquals(299, cells.get(1).getAsJsonObject().get("actual").getAsInt());
    assertEquals(0, cells.get(2).getAsJsonObject().get("actual").getAsInt());
    assertTrue(cells.get(3).getAsJsonObject().get("correct").getAsBoolean());
  }

  @Test
  void unansweredAndWrongChannelsDoNotBorrowOtherCellValuesOrExpectedZeros() {
    String snapshot = GroupNetScoring.freeze(7, RULES, TOPIC);
    GroupNetScoring.Result unanswered = GroupNetScoring.calculate(7, TOPIC, snapshot, "{}");
    assertEquals(0, unanswered.score().signum());
    for (JsonElement cell : JSONUtils.fromJson(unanswered.details(), JsonArray.class)) assertFalse(cell.getAsJsonObject().get("correct").getAsBoolean());
    JsonObject wrongChannel = JSONUtils.fromJson(answer(), JsonObject.class);
    wrongChannel.getAsJsonArray("chananel1").get(0).getAsJsonObject().addProperty("indexs", 2);
    assertEquals(0, new BigDecimal("5").compareTo(GroupNetScoring.calculate(7, TOPIC, snapshot, JSONUtils.toJson(wrongChannel)).score()));
  }

  @Test
  void rejectsWrongDeviceInvalidRulesAndOutOfRangeQuestions() {
    assertThrows(IllegalArgumentException.class, () -> GroupNetScoring.calculate(8, TOPIC,
        GroupNetScoring.freeze(7, RULES, TOPIC), answer()));
    assertThrows(IllegalArgumentException.class, () -> GroupNetScoring.freeze(7, "[]", TOPIC));
    assertThrows(IllegalArgumentException.class, () -> GroupNetScoring.freeze(7, RULES.replace("2}", "-2}"), TOPIC));
    assertThrows(IllegalArgumentException.class, () -> GroupNetScoring.freeze(7, RULES,
        TOPIC.replace("29.00000", "29.00001")));
    assertThrows(IllegalArgumentException.class, () -> GroupNetScoring.freeze(7, RULES,
        TOPIC.replace("\"299\"", "\"099\"")));
    assertThrows(IllegalArgumentException.class, () -> GroupNetScoring.freeze(7, RULES,
        TOPIC.replace("\"value\":19", "\"value\":20")));
  }

  @Test
  void rejectsPartialNumbersAndDuplicateAnswerMappingsInsteadOfParsingNumericPrefixes() {
    String snapshot = GroupNetScoring.freeze(7, RULES, TOPIC);
    JsonObject malformed = JSONUtils.fromJson(answer(), JsonObject.class);
    malformed.getAsJsonArray("serialNumbers").get(0).getAsJsonObject().addProperty("networkdress", "319garbage");
    assertThrows(IllegalArgumentException.class, () -> GroupNetScoring.calculate(7, TOPIC, snapshot, JSONUtils.toJson(malformed)));
    JsonObject duplicate = JSONUtils.fromJson(answer(), JsonObject.class);
    duplicate.getAsJsonArray("chananel1").add(duplicate.getAsJsonArray("chananel1").get(0));
    assertThrows(IllegalArgumentException.class, () -> GroupNetScoring.calculate(7, TOPIC, snapshot, JSONUtils.toJson(duplicate)));
  }

  @Test
  void completeZeroWeightRulesStillSettleAndDistinguishCorrectFromUnanswered() {
    String rules = RULES.replace("2}", "0}").replace("3}", "0}").replace("0.25}", "0}");
    GroupNetScoring.Result result = GroupNetScoring.calculate(7, TOPIC, GroupNetScoring.freeze(7, rules, TOPIC), answer());
    assertEquals(0, result.score().signum());
    JsonArray details = JSONUtils.fromJson(result.details(), JsonArray.class);
    assertTrue(details.get(0).getAsJsonObject().get("correct").getAsBoolean());
    assertFalse(details.get(4).getAsJsonObject().get("correct").getAsBoolean());
  }

  @Test
  void cannotCloneNetworkAddressesOrMoveScoringCellsOutsideTheActualTemplate() {
    invalidTopic(topic -> {
      JsonObject copied = topic.get(1).getAsJsonObject().deepCopy();
      copied.add("xy", JSONUtils.fromJson("[0,0]", JsonArray.class));
      topic.add(copied);
    });
    invalidTopic(topic -> topic.get(1).getAsJsonObject().add("xy", JSONUtils.fromJson("[100,100]", JsonArray.class)));
    invalidTopic(topic -> topic.get(3).getAsJsonObject().getAsJsonObject("value").addProperty("isParameter", "网路地址"));
  }

  @Test
  void requiresEveryTemplateCellAndItsFixedChannelDirectionAndNumber() {
    invalidTopic(topic -> topic.remove(topic.size() - 1));
    invalidTopic(topic -> topic.add(topic.get(1)));
    invalidTopic(topic -> topic.get(3).getAsJsonObject().getAsJsonObject("value").addProperty("xdValues", 2));
    invalidTopic(topic -> {
      JsonObject cell = topic.get(3).getAsJsonObject().getAsJsonObject("value");
      cell.remove("xdValues");
      cell.addProperty("xdValuef", 1);
    });
  }

  @Test
  void rejectsStructuredAndBooleanNumbersButTreatsExplicitNullAsUnanswered() {
    String snapshot = GroupNetScoring.freeze(7, RULES, TOPIC);
    JsonObject answer = JSONUtils.fromJson(answer(), JsonObject.class);
    JsonObject channel = answer.getAsJsonArray("chananel1").get(0).getAsJsonObject();
    channel.addProperty("receptionChananel", true);
    assertThrows(IllegalArgumentException.class, () -> GroupNetScoring.calculate(7, TOPIC, snapshot, JSONUtils.toJson(answer)));
    channel.add("receptionChananel", new JsonArray());
    assertThrows(IllegalArgumentException.class, () -> GroupNetScoring.calculate(7, TOPIC, snapshot, JSONUtils.toJson(answer)));
    String explicitNull = JSONUtils.toJson(answer).replace("\"receptionChananel\":[]", "\"receptionChananel\":null");
    assertEquals(0, new BigDecimal("5.25").compareTo(GroupNetScoring.calculate(7, TOPIC, snapshot, explicitNull).score()));
  }

  @Test
  void retriesCompareDecimalNumbersExactlyWithoutDependingOnJsonMemberOrder() {
    assertTrue(GroupNetScoring.sameAnswer("{\"a\":1.00,\"b\":[null,\"x\"]}", "{\"b\":[null,\"x\"],\"a\":1}"));
    assertFalse(GroupNetScoring.sameAnswer("{\"a\":[0.10000000000000001]}", "{\"a\":[0.10000000000000002]}"));
    assertFalse(GroupNetScoring.sameAnswer("{\"a\":\"1\"}", "{\"a\":1}"));
  }

  private static void invalidTopic(Consumer<JsonArray> mutate) {
    JsonArray topic = JSONUtils.fromJson(TOPIC, JsonArray.class);
    mutate.accept(topic);
    assertThrows(IllegalArgumentException.class, () -> GroupNetScoring.freeze(7, RULES, JSONUtils.toJson(topic)));
  }
}
