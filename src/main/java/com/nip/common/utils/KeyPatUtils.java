package com.nip.common.utils;

import com.google.gson.reflect.TypeToken;
import com.nip.dto.KeyPatPageTransferDto;
import com.nip.dto.KeyPatStatisticalDto;
import com.nip.dto.KeyPatValueTransferDto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class KeyPatUtils {
  final int MAX_LOOKAHEAD = 4;
  final int GROUP_SIZE = 10;
  final int LARGE_THRESHOLD = 100;


  public static void handle(String userId,
                            List<KeyPatValueTransferDto> pageValueResult,
                            List<KeyPatPageTransferDto> userPages,
                            List<KeyPatValueTransferDto> userPageValuesOld,
                            KeyPatStatisticalDto ks) {
    List<List<String>> precomputedValues = userPageValuesOld.stream()
        .map(kp -> {
          String s = convert2String(kp.getValue());
          return s.length() >= 8 && !s.contains("?") ? groupValue(s) : null;
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
        cumsum += values.size();
      } else {
        kp.setSort(kp.getSort() + cumsum);
        userPageValues.add(kp);
      }
    }
    // ---- 结束重新组装拍发内容 ----

    if (!userPages.isEmpty()) {
      // 记录已处理的拍发数据下标
      int sSign = 0;
      int lackLineBase = 0;
      //比对每组报文
      for (int j = 0; j < userPages.size(); j++) {
        KeyPatPageTransferDto keyEntity = userPages.get(j);
        String groupKey = convert2String(keyEntity.getKey());

        // ---- 开始多少行判断 ----
        // 判断少行
        if (sSign >= userPageValues.size()) {
          lackLineBase += 1;
          add(pageValueResult, userId, keyEntity, null, null);
          continue;
        }
        // ---- 结束多少行判断 ----

        KeyPatValueTransferDto valueEntity = userPageValues.get(sSign);
        if (StringUtils.isEmpty(valueEntity)) {
          valueEntity = new KeyPatValueTransferDto();
        }
        String groupValue = convert2String(valueEntity.getValue());
        ks.setPat(ks.getPat() + groupValue.length());

        // 累加解析后的时间总和到总时间
        ks.setPatTime(ks.getPatTime() + convert2Int(valueEntity.getTime()));

        // 如果存在？则说明该结果是改错，取最后一个？后的内容
        if (groupValue.contains("?")) {
          groupValue = alterErrorMethod(groupValue);
          //改错++
          ks.setAlterError(ks.getAlterError() + 1);
        }

        // 目标数据与拍发数据不一致
        if (!Objects.equals(groupKey, groupValue)) {
          // 跳过标识符，默认不跳过
          boolean isGoon = true;

          // ---- 开始多少组判断 ----
          boolean isMoreGroup = true;
          // 多组标识符，默认无多组
          if (sSign > 0) {
            List<KeyPatValueTransferDto> moreGroupTemp = new ArrayList<>();
            moreGroupTemp.add(valueEntity);
            // 判断是否多组: 拍发报文后4组 与当前报文对比，某组报文与目标报文相同，则中间的报文判定为多组
            for (int k = 1; k <= 4; k++) {
              int tempIndex = sSign + k;
              if (tempIndex < userPageValues.size()) {
                KeyPatValueTransferDto nextPageValue = userPageValues.get(tempIndex);
                String nextPageValueValue = convert2String(nextPageValue.getValue());
                if (nextPageValueValue.contains("?")) {
                  nextPageValueValue = alterErrorMethod(nextPageValueValue);
                }
                // 当找到时，则多组结束，并把多组数据加入结果中
                if (Objects.equals(nextPageValueValue, groupKey)) {
                  int gs = 0;
                  for (KeyPatValueTransferDto mg : moreGroupTemp) {
                    add(pageValueResult, userId, keyEntity, JSONUtils.toJson(convert2List(mg.getValue(), gs)), mg.getTime());
                    gs++;
                  }
                  add(pageValueResult, userId, keyEntity, JSONUtils.toJson(convert2List(nextPageValue.getValue(), gs)), nextPageValue.getTime());
                  // 已处理的目标数据下标++
                  sSign = tempIndex;
                  // 增加多组个数
                  ks.setMoreGroup(ks.getMoreGroup() + k);
//                  moreGroup += k;
                  // 多组标识符，设为有多组
                  isMoreGroup = false;
                  // 跳过标识符，设为跳过
                  isGoon = false;
                  break;
                }
                moreGroupTemp.add(nextPageValue);
              }
            }
          }
          // 判断是否少组，用目标数据以及之后的所有报文与当前拍发报文进行比对，某组报文与目标报文相同，则中间判定为少组
          if (isMoreGroup) {
            for (int k = j + 1; k < userPages.size(); k++) {
              KeyPatPageTransferDto nextUserPages = userPages.get(k);
              String nextKey = convert2String(nextUserPages.getKey());
              if (Objects.equals(groupValue, nextKey)) {
                boolean isNotBunchGroup = true;
                if (k - j == 10) {
                  boolean isOk = false;
                  for (int l = 1; l < 5; l++) {
                    int sl = sSign + l;
                    int jl = j + l;
                    if (sl < userPageValues.size() && jl < userPages.size()) {
                      KeyPatValueTransferDto key = userPageValues.get(sl);
                      KeyPatPageTransferDto value = userPages.get(jl);
                      if (Objects.equals(key.getValue(), value.getKey())) {
                        isOk = true;
                        break;
                      }
                    }
                  }
                  if (isOk) {
                    add(pageValueResult, userId, keyEntity, encapsulateErrorInformation(valueEntity, "bunchGroup"), valueEntity.getTime());
                    ks.setBunchGroup(ks.getBunchGroup() + 1);
                    isNotBunchGroup = false;
                    isGoon = false;
                  }
                }
                if (isNotBunchGroup) {
                  // 增加漏拍个数
                  int lackNum = k - j;
                  ks.setLackGroup(ks.getLackGroup() + lackNum);
                  for (int i1 = 0; i1 < lackNum; i1++) {
                    add(pageValueResult, userId, userPages.get(j + i1), "[]", "[]", keyEntity.getSort() + i1);
                  }
                  add(pageValueResult, userId, nextUserPages, valueEntity);
                  //索引同步
                  j += lackNum;
                  // 跳过标识符，设为跳过
                  isGoon = false;
                  break;
                }
              }
            }
          }
          // ---- 结束多少组判断 ----

          // ---- 开始多少码判断 ----
          if (isGoon) {
            // 多码
            if (groupValue.length() > 4) {
              if (groupValue.contains(groupKey)) {
                add(pageValueResult, userId, keyEntity, encapsulateErrorInformation(valueEntity, "more"), valueEntity.getTime());
                ks.setMore(ks.getMore() + 1);
                isGoon = false;
              }
            }
            // 少码
            if (groupValue.length() < 4) {
              if (groupKey.contains(groupValue)) {
                add(pageValueResult, userId, keyEntity, encapsulateErrorInformation(valueEntity, "lack"), valueEntity.getTime());
                ks.setLack(ks.getLack() + 1);
                isGoon = false;
              }
            }
          }
          // ---- 结束多少码判断 ----

          // ---- 开始错码判断 ----
          if (isGoon) {
            add(pageValueResult, userId, keyEntity, valueEntity);
            ks.setError(ks.getError() + 1);
          }
          // ---- 结束错码判断 ----

        }
        // 目标数据与拍发数据一致
        else {
          add(pageValueResult, userId, keyEntity, valueEntity);
        }
        // 已处理的目标数据下标++
        sSign++;
        //拍发组数++
        ks.setPatGroup(ks.getPatGroup() + 1);
        // ---- 开始多少行判断 ----
        // 判断多行
        if (j == userPages.size() - 1 && sSign <= userPageValues.size()) {
          ks.setMoreLine(ks.getMoreLine() + ((userPageValues.size() - sSign) / 10 + 1));
          int flag = 1;
          for (int k = sSign; k < userPageValues.size(); k++) {
            add(pageValueResult, userId, userPageValues.get(k), j + flag, null);
            flag++;
          }
        }
        // ---- 结束多少行判断 ----
      }
      ks.setLackLine(lackLineBase / 10);
    } else {
      ks.setMoreLine(ks.getMoreLine() + userPageValues.size() / 10);
      if (userPageValues.size() % 10 > 0) {
        ks.setMoreLine(ks.getMoreLine() + 1);
      }
    }
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
    List<Integer> numbers = Arrays.stream(input.replaceAll("[\\[\\]]", "")  // 移除方括号
            .split(","))                  // 分割元素
        .map(String::trim)                     // 去除空格
        .map(Integer::parseInt)                // 转为整数
        .toList();

    // 2. 分组处理
    // 按每4个元素一组进行分组，不足4个时自动截断
    List<String> result = new ArrayList<>();
    for (int i = 0; i < numbers.size(); i += 4) {
      // 获取当前分组的结束索引
      int end = Math.min(i + 4, numbers.size());

      // 创建分组子列表并格式化为字符串
      String group = numbers.subList(i, end).toString()
          .replace(" ", "");  // 移除数字间的空格
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

  private static void add(List<KeyPatValueTransferDto> pageValueResult, String userId, KeyPatValueTransferDto value, Integer sort, String key) {
    pageValueResult.add(new KeyPatValueTransferDto(
        userId,
        value.getTrainId(),
        value.getPageNumber(),
        key,
        value.getValue(),
        value.getTime(),
        sort
    ));
  }

  private static void add(List<KeyPatValueTransferDto> pageValueResult, String userId, KeyPatValueTransferDto value, Integer sort) {
    pageValueResult.add(new KeyPatValueTransferDto(
        userId,
        value.getTrainId(),
        value.getPageNumber(),
        value.getKey(),
        value.getValue(),
        value.getTime(),
        sort
    ));
  }

  private static void add(List<KeyPatValueTransferDto> pageValueResult, String userId, KeyPatPageTransferDto key, KeyPatValueTransferDto value) {
    pageValueResult.add(new KeyPatValueTransferDto(
        userId,
        key.getTrainId(),
        key.getPageNumber(),
        key.getKey(),
        value.getValue(),
        value.getTime(),
        key.getSort()
    ));
  }


  private static void add(List<KeyPatValueTransferDto> pageValueResult, String userId, KeyPatPageTransferDto key, String value, String time) {
    pageValueResult.add(new KeyPatValueTransferDto(
        userId,
        key.getTrainId(),
        key.getPageNumber(),
        key.getKey(),
        value,
        time,
        key.getSort()
    ));
  }

  private static void add(List<KeyPatValueTransferDto> pageValueResult, String userId, KeyPatPageTransferDto key, String value, String time, Integer sort) {
    pageValueResult.add(new KeyPatValueTransferDto(
        userId,
        key.getTrainId(),
        key.getPageNumber(),
        key.getKey(),
        value,
        time,
        sort
    ));
  }
}
