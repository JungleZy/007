package com.nip.common.utils;

import com.google.gson.reflect.TypeToken;
import com.nip.dto.KeyPatPageTransferDto;
import com.nip.dto.KeyPatStatisticalDto;
import com.nip.dto.KeyPatValueTransferDto;
import com.nip.dto.ElectronicKeyDeductionEvidence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class KeyPatUtils {

  public static void handle(String userId,
      List<KeyPatValueTransferDto> pageValueResult,
      List<KeyPatPageTransferDto> userPages,
      List<KeyPatValueTransferDto> userPageValuesOld,
      KeyPatStatisticalDto ks) {
    if (userPages.isEmpty() && !userPageValuesOld.isEmpty()) {
      throw new IllegalStateException("该页报底缺失，无法结算拍发记录");
    }
    List<List<String>> precomputedValues = userPageValuesOld.stream()
        .map(kp -> {
          String s = convert2String(kp.getValue());
          return !s.contains("?") && matchesJoinedReference(s, userPages) ? groupValue(s) : null;
        })
        .toList();

    List<KeyPatValueTransferDto> userPageValues = new ArrayList<>();
    // ---- 开始重新组装拍发内容，在这过程中进行少间隔判断 ----
    int cumsum = 0;
    for (int i = 0; i < userPageValuesOld.size(); i++) {
      KeyPatValueTransferDto kp = userPageValuesOld.get(i);
      if (precomputedValues.get(i) != null) {
        List<String> values = precomputedValues.get(i);
        List<String> times = groupTime(kp.getTime());
        for (int j = 0; j < values.size(); j++) {
          userPageValues.add(new KeyPatValueTransferDto(
              kp.getUserId(),
              kp.getTrainId(),
              kp.getPageNumber(),
              JSONUtils.toJson(values),
              convert2JsonArray(values.get(j)),
              times.get(j),
              kp.getSort() + j));
          ks.setLackGap(ks.getLackGap() + 1);
        }
        ks.setLackGap(ks.getLackGap() - 1);
        evidence(ks, kp.getPageNumber(), null, null, kp.getSort() + 1, kp.getSort() + 1,
            String.join(" ", values), convert2String(kp.getValue()), "lackGap", values.size() - 1, null);
        cumsum += values.size() - 1;
      } else {
        if (!convert2String(kp.getValue()).isEmpty()) userPageValues.add(new KeyPatValueTransferDto(
            kp.getUserId(), kp.getTrainId(), kp.getPageNumber(), kp.getKey(), kp.getValue(), kp.getTime(), kp.getSort() + cumsum));
      }
    }
    // ---- 结束重新组装拍发内容 ----

    List<String> sources = userPages.stream().map(p -> convert2String(p.getKey())).toList();
    List<String> answers = userPageValues.stream().map(p -> alterErrorMethod(convert2String(p.getValue()))).toList();
    java.util.Map<Integer, Integer> paired = new java.util.HashMap<>();
    ks.setPatGroup(ks.getPatGroup() + userPageValues.size());
    for (var value : userPageValues) {
      ks.setPat(ks.getPat() + convert2String(value.getValue()).length());
      ks.setPatTime(ks.getPatTime() + convert2Int(value.getTime()));
    }
    int source = 0, captured = 0;
    while (source < sources.size() && captured < answers.size()) {
      var reference = userPages.get(source);
      var value = userPageValues.get(captured);
      String expected = sources.get(source), actual = answers.get(captured);
      if (expected.equals(actual)) {
        ks.setCorrectGroup(ks.getCorrectGroup() + 1);
        if (convert2String(value.getValue()).contains("?")) {
          ks.setAlterError(ks.getAlterError() + 1);
          evidence(ks, reference.getPageNumber(), source + 1, source + 1, captured + 1, captured + 1,
              expected, convert2String(value.getValue()), "alterError", 1, null);
        }
        add(pageValueResult, userId, reference, value);
        source++; captured++; continue;
      }
      boolean bunch = paired.containsKey(captured) && actual.equals(sources.get(paired.get(captured)));
      int bunchCount = 0;
      Integer partner = bunch ? paired.get(captured) + 1 : null;
      if (!bunch) {
        for (int offset = 1; source + offset < sources.size() && captured + offset < answers.size(); offset++) {
          if (!paired.containsKey(captured + offset) && actual.equals(sources.get(source + offset))
              && expected.equals(answers.get(captured + offset))) {
            paired.put(captured + offset, source);
            ks.setBunchGroup(ks.getBunchGroup() + 1);bunch = true;bunchCount = 1;
            partner = source + offset + 1;break;
          }
        }
      }
      if (bunch) {
        evidence(ks, reference.getPageNumber(), source + 1, source + 1, captured + 1, captured + 1,
            expected, convert2String(value.getValue()), "bunchGroup", bunchCount, partner);
        add(pageValueResult, userId, reference, encapsulateErrorInformation(value, "bunchGroup"), value.getTime());
        source++; captured++; continue;
      }
      boolean nextAligned = source + 1 < sources.size() && captured + 1 < answers.size()
          && sources.get(source + 1).equals(answers.get(captured + 1));
      int extra = -1, missing = -1;
      if (!nextAligned) {
        extra = findAlignment(answers, captured, sources, source);
        missing = findAlignment(sources, source, answers, captured);
      }
      if (extra > 0 && (missing < 0 || extra <= missing)) {
        recordExtraGroups(ks, extra);
        rangeEvidence(ks, reference.getPageNumber(), "more", source + 1, captured + 1, extra,
            expected, rawText(userPageValues, captured, captured + extra));
        for (int index = 0; index < extra; index++) {
          var inserted = userPageValues.get(captured + index);
          add(pageValueResult, userId, reference, JSONUtils.toJson(convert2List(inserted.getValue(), index)), inserted.getTime());
        }
        var matched = userPageValues.get(captured + extra);
        add(pageValueResult, userId, reference, JSONUtils.toJson(convert2List(matched.getValue(), extra)), matched.getTime());
        ks.setCorrectGroup(ks.getCorrectGroup() + 1);
        if (convert2String(matched.getValue()).contains("?")) {
          ks.setAlterError(ks.getAlterError() + 1);
          evidence(ks, reference.getPageNumber(), source + 1, source + 1, captured + extra + 1, captured + extra + 1,
              expected, convert2String(matched.getValue()), "alterError", 1, null);
        }
        captured += extra + 1;source++;continue;
      }
      if (missing > 0) {
        recordMissingGroups(ks, missing);
        rangeEvidence(ks, reference.getPageNumber(), "lack", source + 1, null, missing,
            String.join(" ", sources.subList(source, source + missing)), "");
        for (int index = 0; index < missing; index++) add(pageValueResult, userId, userPages.get(source + index), "[]", "[]");
        source += missing;continue;
      }
      String marker = null;
      if (!actual.isEmpty() && actual.length() < expected.length() && isSubsequence(actual, expected)) {
        ks.setLack(ks.getLack() + 1); marker = "lack";
      } else if (actual.length() > expected.length() && isSubsequence(expected, actual)) {
        ks.setMore(ks.getMore() + 1); marker = "more";
      } else ks.setError(ks.getError() + 1);
      evidence(ks, reference.getPageNumber(), source + 1, source + 1, captured + 1, captured + 1,
          expected, convert2String(value.getValue()), marker == null ? "error" : marker, 1, null);
      if (marker == null) add(pageValueResult, userId, reference, value);
      else add(pageValueResult, userId, reference, encapsulateErrorInformation(value, marker), value.getTime());
      source++; captured++;
    }
    recordMissingGroups(ks, sources.size() - source);
    if (source < sources.size()) rangeEvidence(ks, userPages.get(source).getPageNumber(), "lack", source + 1, null,
        sources.size() - source, String.join(" ", sources.subList(source, sources.size())), "");
    while (source < sources.size()) add(pageValueResult, userId, userPages.get(source++), "[]", "[]");
    recordExtraGroups(ks, answers.size() - captured);
    if (captured < answers.size()) rangeEvidence(ks, userPageValues.get(captured).getPageNumber(), "more", null,
        captured + 1, answers.size() - captured, "", rawText(userPageValues, captured, answers.size()));
    int extraSort = sources.size();
    while (captured < answers.size()) add(pageValueResult, userId, userPageValues.get(captured++), extraSort++, null);
  }

  /** 连续缺失按完整10组计行，余数计组，二者互斥并跨页累计。 */
  public static void recordMissingGroups(KeyPatStatisticalDto score, int groups) {
    score.setLackLine(score.getLackLine() + groups / 10);
    score.setLackGroup(score.getLackGroup() + groups % 10);
  }

  public static void recordMissingPage(KeyPatStatisticalDto score, int page, int groups) {
    recordMissingGroups(score, groups);
    rangeEvidence(score, page, "lack", 1, null, groups, "本页报底 " + groups + " 组", "整页未提交");
  }

  private static void rangeEvidence(KeyPatStatisticalDto score, int page, String prefix,
      Integer reference, Integer captured, int groups, String expected, String actual) {
    int complete = groups / 10 * 10;
    if (complete > 0) evidence(score, page, reference,
        reference == null ? null : reference + (prefix.equals("lack") ? complete - 1 : 0),
        captured, captured == null ? null : captured + complete - 1,
        segment(expected, 0, complete, groups), segment(actual, 0, complete, groups), prefix + "Line", groups / 10, null);
    if (groups % 10 > 0) evidence(score, page,
        reference == null ? null : reference + (prefix.equals("lack") ? complete : 0),
        reference == null ? null : reference + (prefix.equals("lack") ? groups - 1 : 0),
        captured == null ? null : captured + complete, captured == null ? null : captured + groups - 1,
        segment(expected, complete, groups % 10, groups), segment(actual, complete, groups % 10, groups), prefix + "Group", groups % 10, null);
  }

  private static String segment(String text, int start, int length, int groups) {
    String[] words = text.split(" ");
    return words.length == groups ? String.join(" ", java.util.Arrays.copyOfRange(words, start, start + length)) : text;
  }

  private static String rawText(List<KeyPatValueTransferDto> values, int start, int end) {
    return values.subList(start, end).stream().map(value -> convert2String(value.getValue())).collect(Collectors.joining(" "));
  }

  private static void evidence(KeyPatStatisticalDto score, int page, Integer reference, Integer referenceEnd,
      Integer captured, Integer capturedEnd, String expected, String actual, String kind, int count, Integer partner) {
    score.getEvidence().add(new ElectronicKeyDeductionEvidence(page, reference, referenceEnd, captured, capturedEnd,
        expected, actual, kind, count, partner, null, null));
  }

  private static void recordExtraGroups(KeyPatStatisticalDto score, int groups) {
    score.setMoreLine(score.getMoreLine() + groups / 10);
    score.setMoreGroup(score.getMoreGroup() + groups % 10);
  }

  private static int findAlignment(List<String> shifted, int start, List<String> fixed, int anchor) {
    for (int offset = 1; start + offset < shifted.size(); offset++) {
      if (!shifted.get(start + offset).equals(fixed.get(anchor))) continue;
      if (start + offset + 1 < shifted.size() && anchor + 1 < fixed.size()
          && !shifted.get(start + offset + 1).equals(fixed.get(anchor + 1))) continue;
      return offset;
    }
    return -1;
  }

  private static boolean isSubsequence(String shorter, String longer) {
    int matched = 0;
    for (int index = 0; index < longer.length() && matched < shorter.length(); index++) {
      if (shorter.charAt(matched) == longer.charAt(index)) matched++;
    }
    return matched == shorter.length();
  }

  private static boolean matchesJoinedReference(String value, List<KeyPatPageTransferDto> references) {
    if (value.length() < 8 || value.length() % 4 != 0) return false;
    List<String> parts = groupValue(value);
    for (int start = 0; start + parts.size() <= references.size(); start++) {
      boolean matches = true;
      for (int index = 0; index < parts.size(); index++) {
        if (!parts.get(index).equals(convert2String(references.get(start + index).getKey()))) {
          matches = false;break;
        }
      }
      if (matches) return true;
    }
    return false;
  }

  /**
   * 将JSON字符串转换为单个字符串
   * 这个方法用于将包含字符串列表的JSON字符串解析为一个拼接的字符串
   *
   * @param jsonStr JSON字符串，包含字符串列表
   * @return 拼接后的字符串
   */
  public static String convert2String(String jsonStr) {
    return String.join("", Objects.requireNonNull(JSONUtils.fromJson(jsonStr, new TypeToken<List<String>>() {
    })));
  }

  public static String convert2JsonArray(String str) {
    Objects.requireNonNull(str, "Input string must not be null");
    return "[" +
        str.chars()
            .mapToObj(c -> "\"" + (char) c + "\"")
            .collect(Collectors.joining(","))
        + "]";
  }

  public static int convert2Int(String timeString) {
    List<Integer> times = JSONUtils.fromJson(timeString, new TypeToken<>() {
    });
    int reduce = 0;
    if (times != null) {
      reduce = times.stream().reduce(0, Integer::sum);
    }
    return reduce;
  }

  /**
   * 改错
   *
   * @param value 原本的值
   * @return
   */
  public static String alterErrorMethod(String value) {
    int index = value.lastIndexOf("?") + 1;
    value = value.substring(index);
    return value;
  }

  /**
   * 将输入字符串按每4个字符分组，返回包含各分组的字符串列表。
   * 例如，输入"abcdefgh"，返回["abcd", "efgh"]；输入"abcde"，返回["abcd", "e"]。
   *
   * @param input 需要分组的字符串，不能为空且长度需大于等于0
   * @return 分组后的字符串列表，每个元素为长度不超过4的子字符串
   */
  public static List<String> groupValue(String input) {
    return IntStream.iterate(0, i -> i < input.length(), i -> i + 4)
        .mapToObj(i -> input.substring(i, Math.min(i + 4, input.length())))
        .collect(Collectors.toList());
  }

  /**
   * 将输入的整数字符串按每4个元素一组进行分组格式化处理
   *
   * @param input 包含整数的字符串，格式类似"[1,2,3,4,5]"（可含空格和方括号）
   * @return 分组后的字符串列表，每个元素为类似"[1,2,3,4]"的格式（元素间无空格）
   */
  public static List<String> groupTime(String input) {
    // 1. 解析字符串为整数列表
    // 移除方括号后按逗号分割，去除元素空格并转换为整数列表
    List<Integer> numbers = Arrays.stream(input.replaceAll("[\\[\\]]", "") // 移除方括号
        .split(",")) // 分割元素
        .map(String::trim) // 去除空格
        .map(Integer::parseInt) // 转为整数
        .toList();

    // 2. 分组处理
    // 按每4个元素一组进行分组，不足4个时自动截断
    List<String> result = new ArrayList<>();
    for (int i = 0; i < numbers.size(); i += 4) {
      // 获取当前分组的结束索引
      int end = Math.min(i + 4, numbers.size());

      // 创建分组子列表并格式化为字符串
      String group = numbers.subList(i, end).toString()
          .replace(" ", ""); // 移除数字间的空格
      result.add(group);
    }
    return result;
  }

  /**
   * 将JSON字符串转换为字符串列表，并在开头插入指定索引值
   *
   * @param value JSON格式的字符串，应为字符串数组序列化结果
   * @param index 要插入到列表开头的索引值（将被转为字符串）
   * @return 处理后的字符串列表，包含插入的索引值作为首元素
   */
  public static List<String> convert2List(String value, Object index) {
    // 从JSON字符串解析字符串列表，使用TypeToken处理泛型类型
    List<String> strings = JSONUtils.fromJson(value, new TypeToken<>() {
    });
    // 空值保护：若解析结果为空则初始化空列表
    if (StringUtils.isEmpty(strings)) {
      strings = new ArrayList<>();
    }
    // 将索引值转为字符串并插入到列表开头
    strings.addFirst(String.valueOf(index));
    return strings;
  }

  private static String encapsulateErrorInformation(KeyPatValueTransferDto valueEntity, String message) {
    List<String> strings = JSONUtils.fromJson(valueEntity.getValue(), new TypeToken<>() {
    });
    if (strings != null) {
      strings.addFirst(message);
    }
    return JSONUtils.toJson(strings);
  }

  private static void add(List<KeyPatValueTransferDto> pageValueResult, String userId, KeyPatValueTransferDto value,
      Integer sort, String key) {
    pageValueResult.add(new KeyPatValueTransferDto(
        userId,
        value.getTrainId(),
        value.getPageNumber(),
        key,
        value.getValue(),
        value.getTime(),
        sort));
  }

  private static void add(List<KeyPatValueTransferDto> pageValueResult, String userId, KeyPatValueTransferDto value,
      Integer sort) {
    pageValueResult.add(new KeyPatValueTransferDto(
        userId,
        value.getTrainId(),
        value.getPageNumber(),
        value.getKey(),
        value.getValue(),
        value.getTime(),
        sort));
  }

  private static void add(List<KeyPatValueTransferDto> pageValueResult, String userId, KeyPatPageTransferDto key,
      KeyPatValueTransferDto value) {
    pageValueResult.add(new KeyPatValueTransferDto(
        userId,
        key.getTrainId(),
        key.getPageNumber(),
        key.getKey(),
        value.getValue(),
        value.getTime(),
        key.getSort()));
  }

  private static void add(List<KeyPatValueTransferDto> pageValueResult, String userId, KeyPatPageTransferDto key,
      String value, String time) {
    pageValueResult.add(new KeyPatValueTransferDto(
        userId,
        key.getTrainId(),
        key.getPageNumber(),
        key.getKey(),
        value,
        time,
        key.getSort()));
  }

  private static void add(List<KeyPatValueTransferDto> pageValueResult, String userId, KeyPatPageTransferDto key,
      String value, String time, Integer sort) {
    pageValueResult.add(new KeyPatValueTransferDto(
        userId,
        key.getTrainId(),
        key.getPageNumber(),
        key.getKey(),
        value,
        time,
        sort));
  }
}
