package com.nip.common.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.nip.dto.PostKeyPatTrainRuleDto;
import com.nip.dto.PostTelexPatTrainRuleDto;
import com.nip.dto.score.MessageDeduct;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.score.SpeedDeduct;
import com.nip.dto.score.TrainingRateUnit;

import java.math.BigDecimal;

/** Validates the complete scoring contract before a rule or training snapshot is written. */
public final class ScoringRuleValidation {
  private ScoringRuleValidation() {}

  public static PostTelegramTrainRule handkey(String content) {
    JsonObject root = root(content, TrainingRateUnit.CHARACTERS_PER_MINUTE);
    PostTelegramTrainRule rule = new PostTelegramTrainRule();
    rule.setRateUnit(TrainingRateUnit.CHARACTERS_PER_MINUTE);
    rule.setSkew(integer(root, "skew", "skew", true, false));
    rule.setWpm(handSpeed(object(root, "wpm", "wpm"), "wpm", true));
    JsonObject code = object(root, "code", "code");
    rule.setDot(handSpeed(object(code, "dot", "code.dot"), "code.dot", false));
    rule.setDash(handSpeed(object(code, "dash", "code.dash"), "code.dash", false));
    JsonObject gap = object(root, "gap", "gap");
    rule.setLittle(handSpeed(object(gap, "little", "gap.little"), "gap.little", false));
    rule.setMiddle(handSpeed(object(gap, "middle", "gap.middle"), "gap.middle", false));
    rule.setLarge(handSpeed(object(gap, "large", "gap.large"), "gap.large", false));
    JsonObject other = object(root, "other", "other");
    rule.setErrorCode(handMessage(other, "errorCode"));
    rule.setQuantoCode(handMessage(other, "quantoCode"));
    rule.setQuantoGroup(handMessage(other, "quantoGroup"));
    rule.setAlterError(handMessage(other, "alterError"));
    rule.setQuantoRow(handMessage(other, "quantoRow"));
    rule.setBunchGroup(handMessage(other, "bunchGroup"));
    return rule;
  }

  public static PostKeyPatTrainRuleDto electronic(String content) {
    JsonObject root = root(content, TrainingRateUnit.FOUR_CHARACTER_GROUPS_PER_MINUTE);
    decimalSpeed(root);
    JsonObject other = object(root, "other", "other");
    requiredDecimals(other, "errorCode", "muchLessCode", "muchLessLine", "muchLessGroups", "alterError", "bunchGroup", "lessGap");
    optionalDecimals(other, "correctMistakes", "lessPage", "lessReturnLine", "errorPage", "nonStandart");
    PostKeyPatTrainRuleDto rule = JSONUtils.gson.fromJson(root, PostKeyPatTrainRuleDto.class);
    if (rule.getWpm().getR() == null) rule.getWpm().setR(BigDecimal.ZERO);
    if (rule.getWpm().getL() == null) rule.getWpm().setL(BigDecimal.ZERO);
    return rule;
  }

  public static PostTelexPatTrainRuleDto telex(String content) {
    JsonObject root = root(content, TrainingRateUnit.CHARACTERS_PER_MINUTE);
    decimalSpeed(root);
    JsonObject other = object(root, "other", "other");
    requiredDecimals(other, "errorCode", "muchLessCode", "muchLessLine", "muchLessGroups", "correctMistakes",
        "lessPage", "lessReturnLine", "errorPage", "nonStandart");
    optionalDecimals(other, "alterError", "bunchGroup", "lessGap");
    PostTelexPatTrainRuleDto rule = JSONUtils.gson.fromJson(root, PostTelexPatTrainRuleDto.class);
    if (rule.getWpm().getR() == null) rule.getWpm().setR(BigDecimal.ZERO);
    if (rule.getWpm().getL() == null) rule.getWpm().setL(BigDecimal.ZERO);
    return rule;
  }

  private static JsonObject root(String content, TrainingRateUnit expected) {
    JsonElement parsed;
    try {
      parsed = JSONUtils.fromJson(content, JsonElement.class);
    } catch (JsonParseException e) {
      throw new IllegalArgumentException("评分规则必须是完整JSON对象，请核对后重新保存", e);
    }
    if (parsed == null || !parsed.isJsonObject()) throw invalid("规则", "必须是完整JSON对象");
    JsonObject root = parsed.getAsJsonObject();
    JsonElement unit = root.get("rateUnit");
    if (unit == null || !unit.isJsonPrimitive() || !unit.getAsJsonPrimitive().isString()
        || !expected.name().equals(unit.getAsString())) {
      throw invalid("rateUnit", "必须明确为" + expected.name() + "，请核对单位后重新保存");
    }
    return root;
  }

  private static JsonObject object(JsonObject parent, String key, String path) {
    JsonElement value = parent.get(key);
    if (value == null || !value.isJsonObject()) throw invalid(path, "必须是完整对象");
    return value.getAsJsonObject();
  }

  private static SpeedDeduct handSpeed(JsonObject value, String path, boolean wpm) {
    SpeedDeduct rule = new SpeedDeduct();
    rule.setBase(integer(value, "base", path + ".base", true, wpm));
    Integer r = integer(value, "r", path + ".r", !wpm, false);
    Integer l = integer(value, "l", path + ".l", !wpm, false);
    rule.setR(wpm && r == null ? 0 : r);
    rule.setL(wpm && l == null ? 0 : l);
    rule.setMax(integer(value, "max", path + ".max", !wpm, false));
    JsonElement type = value.get("type");
    if (type != null && !type.isJsonNull()) {
      if (!type.isJsonPrimitive() || !type.getAsJsonPrimitive().isBoolean()) throw invalid(path + ".type", "必须是布尔值");
      rule.setType(type.getAsBoolean());
    }
    return rule;
  }

  private static MessageDeduct handMessage(JsonObject other, String key) {
    String path = "other." + key;
    JsonObject value = object(other, key, path);
    MessageDeduct rule = new MessageDeduct();
    rule.setL(integer(value, "l", path + ".l", true, false));
    rule.setMax(integer(value, "max", path + ".max", true, false));
    return rule;
  }

  private static void decimalSpeed(JsonObject root) {
    JsonObject wpm = object(root, "wpm", "wpm");
    integer(wpm, "base", "wpm.base", true, true);
    decimal(wpm, "r", "wpm.r", false);
    decimal(wpm, "l", "wpm.l", false);
  }

  private static void requiredDecimals(JsonObject other, String... fields) {
    for (String field : fields) decimal(other, field, "other." + field, true);
  }

  private static void optionalDecimals(JsonObject other, String... fields) {
    for (String field : fields) decimal(other, field, "other." + field, false);
  }

  private static Integer integer(JsonObject object, String key, String path, boolean required, boolean positive) {
    BigDecimal value = decimal(object, key, path, required);
    if (value == null) return null;
    try {
      int result = value.intValueExact();
      if (positive && result == 0) throw invalid(path, "必须大于0");
      return result;
    } catch (ArithmeticException e) {
      throw invalid(path, "必须是有效范围内的整数，不能截断小数");
    }
  }

  private static BigDecimal decimal(JsonObject object, String key, String path, boolean required) {
    JsonElement value = object.get(key);
    if (value == null || value.isJsonNull()) {
      if (required) throw invalid(path, "不能为空");
      return null;
    }
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid(path, "必须是数值");
    try {
      BigDecimal result = value.getAsBigDecimal();
      if (result.signum() < 0) throw invalid(path, "不能为负数");
      return result;
    } catch (NumberFormatException e) {
      throw invalid(path, "必须是有限数值");
    }
  }

  private static IllegalArgumentException invalid(String path, String reason) {
    return new IllegalArgumentException("评分规则" + path + reason + "，请核对后重新保存");
  }
}
