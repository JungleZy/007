package com.nip.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.nip.common.utils.JSONUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** J210-742: each matching network/address/channel cell earns its configured weight. */
public final class GroupNetScoring {
  private static final Set<String> PARAMETERS = Set.of("网路地址", "单台地址", "信道");
  private static final int VERSION = 1;

  private GroupNetScoring() {}

  public record Result(BigDecimal score, String details) {}

  public static String freeze(Integer deviceId, String rules, String topic) {
    if (deviceId == null) throw new IllegalArgumentException("评分规则设备不能为空");
    JsonArray values = array(rules, "评分规则");
    rules(values);
    validateTopic(topic);
    JsonObject snapshot = new JsonObject();
    snapshot.addProperty("version", VERSION);
    snapshot.addProperty("deviceId", deviceId);
    snapshot.add("rules", values);
    return JSONUtils.toJson(snapshot);
  }

  public static void validateRules(String content) {
    rules(array(content, "评分规则"));
  }

  private static Map<String, BigDecimal> rules(JsonArray values) {
    Map<String, BigDecimal> result = new HashMap<>();
    for (JsonElement value : values) {
      JsonObject rule = object(value, "评分规则项");
      String name = string(rule.get("paramName"), "规则参数名称");
      BigDecimal weight = decimal(rule.get("weight"), "规则权重");
      if (!PARAMETERS.contains(name) || weight.signum() < 0 || weight.compareTo(new BigDecimal("100")) > 0
          || weight.stripTrailingZeros().scale() > 2 || result.putIfAbsent(name, weight) != null) {
        throw new IllegalArgumentException("评分规则参数重复、未知或权重超出0至100（最多两位小数）");
      }
    }
    if (!result.keySet().equals(PARAMETERS)) {
      throw new IllegalArgumentException("必须完整配置网路地址、单台地址和信道规则");
    }
    return result;
  }

  private static JsonArray validateTopic(String content) {
    JsonArray topic = array(content, "训练题目");
    Set<String> coordinates = new HashSet<>();
    Set<Integer> addresses = new HashSet<>();
    for (JsonElement entry : topic) {
      JsonObject item = object(entry, "题目项");
      if (!(item.get("xy") instanceof JsonArray xy) || xy.size() != 2) throw new IllegalArgumentException("题目坐标无效");
      int row = integer(xy.get(0), "题目行", 0, 100);
      int col = integer(xy.get(1), "题目列", 0, 100);
      if (!coordinates.add(row + ":" + col)) throw new IllegalArgumentException("题目坐标重复");
      JsonObject cell = object(item.get("value"), "题目单元格");
      String name = string(cell.get("isParameter"), "题目参数类型");
      if (!templateParameter(row, col).equals(name)) throw new IllegalArgumentException("题目坐标与J210-742参数类型不匹配");
      if (!"信道".equals(name) && (cell.has("xdValues") || cell.has("xdValuef"))) {
        throw new IllegalArgumentException("非信道题目不能配置收发编号");
      }
      switch (name) {
        case "序号" -> integer(cell.get("value"), "序号", 0, 19);
        case "网路地址" -> integer(cell.get("value"), "网路地址", 300, 319);
        case "单台地址" -> {
          int address = integer(cell.get("value"), "单台地址", 0, 299);
          if (address > 19 && address < 100) throw new IllegalArgumentException("单台地址不在0至19或100至299");
          if (!addresses.add(address)) throw new IllegalArgumentException("单台地址重复");
        }
        case "信道" -> {
          BigDecimal expected = decimal(cell.get("value"), "题目值");
          if (expected.signum() < 0 || expected.compareTo(new BigDecimal("29")) > 0
              || expected.stripTrailingZeros().scale() > 5) throw new IllegalArgumentException("信道应在0至29且最多五位小数");
          int offset = col - firstReceiveColumn(row);
          boolean receive = offset % 3 == 0;
          if (!cell.has(receive ? "xdValues" : "xdValuef")
              || cell.has(receive ? "xdValuef" : "xdValues")) throw new IllegalArgumentException("信道收发映射无效");
          int index = integer(cell.get(receive ? "xdValues" : "xdValuef"), "信道编号", 1, 70);
          if (index != (offset / 3) * 10 + row - 3) throw new IllegalArgumentException("信道编号与J210-742模板位置不匹配");
        }
        default -> throw new IllegalArgumentException("不支持的题目参数");
      }
    }
    if (coordinates.size() != 157) throw new IllegalArgumentException("J210-742题目必须包含完整的157个参数单元格");
    return topic;
  }

  // contactDocuments/table.js: one network address, eight serial/address rows, 70 receive/send pairs.
  private static String templateParameter(int row, int col) {
    if (row == 4 && col == 2) return "网路地址";
    if (row >= 4 && row <= 11) {
      if (col == 1) return "序号";
      if (col == (row == 4 ? 3 : 2)) return "单台地址";
    }
    if (row >= 4 && row <= 13) {
      int offset = col - firstReceiveColumn(row);
      if (offset >= 0 && offset / 3 < 7 && offset % 3 < 2) return "信道";
    }
    throw new IllegalArgumentException("题目坐标不属于J210-742可编辑参数");
  }

  private static int firstReceiveColumn(int row) {
    return row == 4 ? 8 : row == 13 ? 1 : 7;
  }

  public static Result calculate(Integer deviceId, String topicContent, String snapshotContent, String answerContent) {
    JsonObject snapshot = object(parse(snapshotContent, "冻结评分规则"), "冻结评分规则");
    if (deviceId == null || integer(snapshot.get("version"), "冻结规则版本", 1, Integer.MAX_VALUE) != VERSION
        || integer(snapshot.get("deviceId"), "冻结规则设备", 1, Integer.MAX_VALUE) != deviceId) {
      throw new IllegalArgumentException("冻结评分规则版本或设备不匹配，请重新创建训练");
    }
    if (!(snapshot.get("rules") instanceof JsonArray frozenRules)) throw new IllegalArgumentException("冻结评分规则格式无效");
    Map<String, BigDecimal> weights = rules(frozenRules);
    JsonArray topic = validateTopic(topicContent);
    JsonObject answer = object(parse(answerContent, "答案"), "答案");
    Map<Integer, JsonObject> channels = new HashMap<>();
    JsonArray channelAnswers = optionalArray(answer, "chananel1");
    for (JsonElement item : channelAnswers) {
      JsonObject channel = object(item, "信道答案");
      int index = integer(channel.get("indexs"), "答案信道编号", 0, 999);
      if (channels.putIfAbsent(index, channel) != null) throw new IllegalArgumentException("答案信道编号重复");
    }
    JsonArray serialAnswers = optionalArray(answer, "serialNumbers");
    Map<Integer, Integer> serials = new HashMap<>();
    for (JsonElement entry : topic) {
      JsonObject item = entry.getAsJsonObject();
      JsonObject cell = item.getAsJsonObject("value");
      if ("序号".equals(cell.get("isParameter").getAsString())) {
        serials.put(item.getAsJsonArray("xy").get(0).getAsInt(), decimal(cell.get("value"), "序号").intValueExact());
      }
    }
    BigDecimal total = BigDecimal.ZERO;
    JsonArray details = new JsonArray();
    for (JsonElement entry : topic) {
      JsonObject item = entry.getAsJsonObject();
      JsonObject cell = item.getAsJsonObject("value");
      String name = cell.get("isParameter").getAsString();
      if (!weights.containsKey(name)) continue;
      JsonElement actual;
      if ("信道".equals(name)) {
        boolean receive = cell.has("xdValues");
        int index = decimal(cell.get(receive ? "xdValues" : "xdValuef"), "信道编号").intValueExact();
        JsonObject channel = channels.get(index);
        actual = channel == null ? null : channel.get(receive ? "receptionChananel" : "sendChananel");
      } else {
        int index = "网路地址".equals(name) ? 0 : serials.get(item.getAsJsonArray("xy").get(0).getAsInt());
        JsonElement serial = index < serialAnswers.size() ? serialAnswers.get(index) : null;
        actual = serial == null || serial.isJsonNull() ? null : object(serial, "地址答案").get("网路地址".equals(name) ? "networkdress" : "dressname");
      }
      boolean unanswered = actual == null || actual.isJsonNull()
          || (actual.isJsonPrimitive() && actual.getAsJsonPrimitive().isString() && actual.getAsString().isBlank());
      boolean correct = !unanswered && decimal(cell.get("value"), "题目值").compareTo(decimal(actual, "答案值")) == 0;
      BigDecimal earned = correct ? weights.get(name) : BigDecimal.ZERO;
      total = total.add(earned);
      JsonObject detail = new JsonObject();
      detail.add("xy", item.getAsJsonArray("xy"));
      if (unanswered) detail.addProperty("actual", "");
      else detail.add("actual", actual);
      detail.addProperty("correct", correct);
      detail.addProperty("weight", weights.get(name));
      detail.addProperty("score", earned);
      details.add(detail);
    }
    return new Result(total, JSONUtils.toJson(details));
  }

  public static boolean sameAnswer(String saved, String submitted) {
    return sameJson(object(parse(saved, "已提交答案"), "已提交答案"), object(parse(submitted, "答案"), "答案"));
  }

  // Gson's numeric JsonPrimitive.equals can compare doubles; retries must not erase decimal differences.
  private static boolean sameJson(JsonElement left, JsonElement right) {
    if (left.isJsonObject() && right.isJsonObject()) {
      JsonObject a = left.getAsJsonObject();
      JsonObject b = right.getAsJsonObject();
      if (!a.keySet().equals(b.keySet())) return false;
      for (var entry : a.entrySet()) if (!sameJson(entry.getValue(), b.get(entry.getKey()))) return false;
      return true;
    }
    if (left.isJsonArray() && right.isJsonArray()) {
      JsonArray a = left.getAsJsonArray();
      JsonArray b = right.getAsJsonArray();
      if (a.size() != b.size()) return false;
      for (int i = 0; i < a.size(); i++) if (!sameJson(a.get(i), b.get(i))) return false;
      return true;
    }
    if (left.isJsonPrimitive() && right.isJsonPrimitive()
        && left.getAsJsonPrimitive().isNumber() && right.getAsJsonPrimitive().isNumber()) {
      return new BigDecimal(left.getAsString()).compareTo(new BigDecimal(right.getAsString())) == 0;
    }
    return left.equals(right);
  }

  private static JsonArray optionalArray(JsonObject object, String name) {
    JsonElement value = object.get(name);
    if (value == null || value.isJsonNull()) return new JsonArray();
    if (!(value instanceof JsonArray result)) throw new IllegalArgumentException(name + "必须是数组");
    return result;
  }

  private static JsonArray array(String content, String name) {
    JsonElement value = parse(content, name);
    if (!(value instanceof JsonArray result)) throw new IllegalArgumentException(name + "必须是数组");
    return result;
  }

  private static JsonElement parse(String content, String name) {
    if (content == null || content.isBlank()) throw new IllegalArgumentException(name + "不能为空");
    try {
      return JSONUtils.fromJson(content, JsonElement.class);
    } catch (JsonParseException e) {
      throw new IllegalArgumentException(name + "格式无效", e);
    }
  }

  private static JsonObject object(JsonElement value, String name) {
    if (!(value instanceof JsonObject result)) throw new IllegalArgumentException(name + "必须是对象");
    return result;
  }

  private static String string(JsonElement value, String name) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException(name + "必须是字符串");
    }
    return value.getAsString();
  }

  private static BigDecimal decimal(JsonElement value, String name) {
    if (value == null || !value.isJsonPrimitive() || value.getAsJsonPrimitive().isBoolean()
        || !value.getAsString().matches("[+-]?[0-9]+(?:\\.[0-9]+)?")) {
      throw new IllegalArgumentException(name + "必须是完整十进制数");
    }
    return new BigDecimal(value.getAsString());
  }

  private static int integer(JsonElement value, String name, int min, int max) {
    BigDecimal number = decimal(value, name);
    if (number.stripTrailingZeros().scale() > 0 || number.compareTo(BigDecimal.valueOf(min)) < 0
        || number.compareTo(BigDecimal.valueOf(max)) > 0) throw new IllegalArgumentException(name + "超出范围");
    return number.intValueExact();
  }
}
