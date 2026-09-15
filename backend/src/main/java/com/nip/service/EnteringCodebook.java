package com.nip.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** The same ordered codebook used by Homophone.js and fontBank.js. */
final class EnteringCodebook {
  private static final Map<Character, String> PINYIN = new HashMap<>();
  private static final Map<Character, String> WUBI = new HashMap<>();
  private static final Map<Character, String> INPUT_KEYS = new HashMap<>();

  static {
    try (var stream = EnteringCodebook.class.getResourceAsStream("/entering-codebook.json")) {
      if (stream == null) throw new IllegalStateException("Missing entering-codebook.json");
      JsonObject book = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
      book.getAsJsonObject("keyboard").getAsJsonObject("inputKeys").entrySet().forEach(entry ->
          INPUT_KEYS.put(entry.getKey().charAt(0), entry.getValue().getAsString()));
      JsonObject pinyin = book.getAsJsonObject("pinyin");
      JsonArray syllables = pinyin.getAsJsonArray("syllables");
      JsonArray characters = pinyin.getAsJsonArray("characters");
      for (int i = 0; i < characters.size(); i++) {
        for (char font : characters.get(i).getAsString().toCharArray()) {
          PINYIN.putIfAbsent(font, syllables.get(i).getAsString());
        }
      }
      JsonObject wubi = book.getAsJsonObject("wubi");
      String[] fonts = wubi.get("font").getAsString().split(";", -1);
      String[] codes = wubi.get("code").getAsString().split(";", -1);
      for (int i = 0; i < fonts.length; i++) {
        if (fonts[i].length() == 1) WUBI.put(fonts[i].charAt(0), codes[i]);
      }
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private EnteringCodebook() {}

  static JsonArray freeze(Integer type, String stock) {
    if (type == null || !(type >= 0 && type <= 4 || type == 9)) {
      throw new IllegalArgumentException("个人录入训练类型无效");
    }
    JsonArray source = array(stock);
    JsonArray result = new JsonArray();
    for (JsonElement item : source) {
      if (type == 0) {
        if (!item.isJsonObject()) throw new IllegalArgumentException("同字异音词库无效");
        JsonObject row = item.getAsJsonObject();
        add(result, text(row.get("font")), text(row.get("pys")));
      } else {
        String font = text(item);
        if (type == 4) {
          for (char character : font.toCharArray()) {
            add(result, String.valueOf(character), codeFor(character, false));
          }
        } else {
          StringBuilder code = new StringBuilder();
          for (char character : font.toCharArray()) {
            code.append(codeFor(character, type == 9));
          }
          add(result, font, code.toString());
        }
      }
    }
    if (result.isEmpty()) throw new IllegalArgumentException("未配置可用录入词库");
    return result;
  }

  private static String codeFor(char character, boolean wubi) {
    String literal = INPUT_KEYS.get(character);
    if (literal != null) return literal;
    if (character <= 255) return String.valueOf(character);
    String code = (wubi ? WUBI : PINYIN).get(character);
    if (code == null) throw new IllegalArgumentException((wubi ? "五笔" : "拼音") + "词库包含无码字符：" + character);
    return code;
  }

  private static void add(JsonArray rows, String font, String code) {
    if (font.isEmpty() || code.isEmpty()) throw new IllegalArgumentException("词库条目缺少文字或码串");
    JsonObject row = new JsonObject();
    row.addProperty("font", font);
    row.addProperty("code", code);
    rows.add(row);
  }

  static JsonArray array(String content) {
    try {
      return JsonParser.parseString(content).getAsJsonArray();
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("录入内容必须为数组", e);
    }
  }

  static String text(JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("录入内容必须为字符串");
    }
    return value.getAsString();
  }

  static JsonArray capture(JsonArray source, JsonArray answers) {
    if (source.size() != answers.size()) throw new IllegalArgumentException("录入条目数量与题面不一致");
    JsonArray result = new JsonArray();
    for (int i = 0; i < source.size(); i++) {
      JsonObject target = source.get(i).getAsJsonObject();
      if (!answers.get(i).isJsonObject()) throw new IllegalArgumentException("录入条目无效");
      String value = text(answers.get(i).getAsJsonObject().get("value"));
      String code = text(target.get("code"));
      if (value.length() > code.length()) throw new IllegalArgumentException("录入码串超出条目长度");
      JsonObject row = new JsonObject();
      row.add("font", target.get("font"));
      row.addProperty("value", value);
      boolean complete = value.length() == code.length();
      row.addProperty("isFocus", complete);
      row.add("trueOrfalse", complete ? new com.google.gson.JsonPrimitive(code.equals(value)) : com.google.gson.JsonNull.INSTANCE);
      JsonArray pys = new JsonArray();
      for (int j = 0; j < code.length(); j++) {
        JsonObject letter = new JsonObject();
        letter.addProperty("py", String.valueOf(code.charAt(j)));
        letter.add("trueOrfalse", j < value.length()
            ? new com.google.gson.JsonPrimitive(code.charAt(j) == value.charAt(j)) : com.google.gson.JsonNull.INSTANCE);
        pys.add(letter);
      }
      row.add("pys", pys);
      result.add(row);
    }
    return result;
  }
}
