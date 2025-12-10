package com.nip.common.utils;

import com.nip.dto.TelexPatPageTransferDto;
import com.nip.dto.TelexPatStatisticalDto;
import com.nip.dto.TelexPatValueTransferDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class TelexPatUtils {
  private static final Pattern PAGE_PATTERN = Pattern.compile("(.+?)-(\\d{1,3})/(\\d{1,3})");
  private static final Pattern PAGE_PATTERN1 = Pattern.compile("[^/]+/[^/]+");

  public static void handle(String userId,
                            Integer pageNumber,
                            List<TelexPatValueTransferDto> pageValueResult,
                            List<TelexPatPageTransferDto> userPages,
                            String userPageValues,
                            TelexPatStatisticalDto ks,
                            boolean isLastPage) {
    // 添加空值检查
    if (pageValueResult == null) pageValueResult = new ArrayList<>();
    if (userPages == null) userPages = new ArrayList<>();
    List<List<String>> result = new ArrayList<>();
    int groupNumber = 0;
    if (userPageValues == null || userPageValues.isEmpty()) {
      result = new ArrayList<>();
    }

    int length = 0;
    if (userPageValues != null) {
      length = userPageValues.length();
    }
    int start = 0;  // 当前单词的起始位置
    int pos = 0;    // 当前扫描位置
    List<String> currentLine = new ArrayList<>();

    while (pos < length) {
      char c = userPageValues.charAt(pos);

      // 遇到空格或制表符结束当前单词
      if (c == ' ' || c == '\t') {
        if (start < pos) {
          String substring = userPageValues.substring(start, pos);
          if (substring.length() >= 8 && !substring.contains("/") && !substring.contains("-")) {
            List<String> strings = splitByFour(substring);
            for (String string : strings) {
              currentLine.add(string);
              groupNumber++;
              ks.setPatGroup(ks.getPatGroup() + 1);
            }
            ks.setNonStandartNumber(ks.getNonStandartNumber() + (strings.size() - 1));
          } else {
            currentLine.add(userPageValues.substring(start, pos));
            groupNumber++;
            ks.setPatGroup(ks.getPatGroup() + 1);
          }
        }
        start = pos + 1;  // 跳过空格
      }
      // 遇到换行符结束当前行
      else if (c == '\n' || c == '\r') {
        // 添加当前行最后一个单词
        if (start < pos) {
          String substring = userPageValues.substring(start, pos);
          if (substring.length() >= 8 && !substring.contains("/") && !substring.contains("-")) {
            List<String> strings = splitByFour(substring);
            for (String string : strings) {
              currentLine.add(string);
              groupNumber++;
              ks.setPatGroup(ks.getPatGroup() + 1);
            }
            ks.setNonStandartNumber(ks.getNonStandartNumber() + (strings.size() - 1));
          } else {
            currentLine.add(userPageValues.substring(start, pos));
            groupNumber++;
            ks.setPatGroup(ks.getPatGroup() + 1);
          }
        }

        // 添加非空行到结果
        if (!currentLine.isEmpty()) {
          result.add(currentLine);
          groupNumber++;
          ks.setPatGroup(ks.getPatGroup() + 1);
          currentLine = new ArrayList<>();
        }

        // 处理Windows换行符(\r\n)
        if (c == '\r' && pos + 1 < length && userPageValues.charAt(pos + 1) == '\n') {
          pos++;  // 跳过额外的'\n'
        }
        start = pos + 1;  // 重置单词起始位置
      }
      pos++;
    }

    // 处理最后一行
    if (start < pos) {
      String substring = userPageValues.substring(start, pos);
      if (substring.length() >= 8 && !substring.contains("/") && !substring.contains("-")) {
        List<String> strings = splitByFour(substring);
        for (String string : strings) {
          currentLine.add(string);
          groupNumber++;
          ks.setPatGroup(ks.getPatGroup() + 1);
        }
        ks.setNonStandartNumber(ks.getNonStandartNumber() + (strings.size() - 1));
      } else {
        currentLine.add(userPageValues.substring(start, pos));
        groupNumber++;
        ks.setPatGroup(ks.getPatGroup() + 1);
      }
    }
    if (!currentLine.isEmpty()) {
      result.add(currentLine);
    }

    // 页标判断
    if (groupNumber >= 100) {
      String lastAfterDash = removeAfterDigits(getLastAfterDash(userPageValues));
      if (StringUtils.isEmpty(lastAfterDash)) {
        ks.setLessPageNumber(ks.getLessPageNumber() + 1);
      } else if (!lastAfterDash.equals(pageNumber + "")) {
        ks.setErrorPageNumber(ks.getErrorPageNumber() + 1);
      }
    }

    List<List<String>> neatenResult = new ArrayList<>();
    for (int i = 0; i < result.size(); i++) {
      List<String> row = new ArrayList<>();
      if (result.get(i).size() == 2 && isBetweenOneAndHundred(result.get(i).getFirst())) {
        int i1 = Integer.parseInt(result.get(i).getFirst());
        List<Integer> calculate = calculate(i1);
        if (null != calculate) {
          List<String> strings = replaceElement(neatenResult.get(calculate.getFirst()), calculate.getLast(), result.get(i).getLast());
          neatenResult.set(i1 / 10, strings);
        }
      } else {
        for (int j = 0; j < result.get(i).size(); j++) {
          String group = result.get(i).get(j);
          // 改错
          if (group.contains("/")) {
            // 改页标
            if (containsPattern(group)) {
              String s = transformString(group);
              row.add(s);
              ks.setCorrectMistakesNumber(ks.getCorrectMistakesNumber() + 1);
            } else if (isOnlySlashes(group) && j > 0) {
              if (group.length() != 4) {
                ks.setNonStandartNumber(ks.getNonStandartNumber() + 1);
              }
              row.removeLast();
              row.add(result.get(i).get(j + 1));
              j++;
              ks.setCorrectMistakesNumber(ks.getCorrectMistakesNumber() + 1);
            } else if (isValidFormat(group)) {
              if (!isValid(group)) {
                ks.setNonStandartNumber(ks.getNonStandartNumber() + 1);
              }
              row.add(result.get(i).get(j + 1));
              j++;
              ks.setCorrectMistakesNumber(ks.getCorrectMistakesNumber() + 1);
            } else if (hasMiddleSlash(group)) {
              String value = result.get(i).get(j);
              int index = value.lastIndexOf("/") + 1;
              value = value.substring(index);
              row.add(value);
              ks.setCorrectMistakesNumber(ks.getCorrectMistakesNumber() + 1);
            } else {
              row.add(group);
              ks.setNonStandartNumber(ks.getNonStandartNumber() + 1);
            }
          }
          // 隔页修改
          else if (isPageModification(group)) {
            if (j + 2 < result.get(i).size()) {
              if (!neatenResult.get(i - 1).contains("-")) {
                ks.setNonStandartNumber(ks.getNonStandartNumber() + 1);
              }
              int alterPageNumber = extractPageNumber(group);
              int alterPageGroup = Integer.parseInt(result.get(i).get(j + 1));
              String alterPageValue = result.get(i).get(j + 2);
              AtomicBoolean isOk = new AtomicBoolean(false);
              pageValueResult.forEach(t -> {
                if (t.getPageNumber().equals(alterPageNumber) && t.getSort().equals(alterPageGroup - 1)) {
                  t.setValue(alterPageValue);
                  ks.setCorrectMistakesNumber(ks.getCorrectMistakesNumber() + 1);
                  isOk.set(true);
                }
              });
              if (!isOk.get()) {
                ks.setNonStandartNumber(ks.getNonStandartNumber() + 1);
              }
              j = j + 2;
            }
          }
          // 多组处理,删除指定组
          else if (group.equals("QTA")) {
            if (j + 1 < result.get(i).size()) {
              String nextValue = result.get(i).get(j + 1);
              int nextValueType = checkHyphenPattern(nextValue);
              if (nextValueType == 1) {
                List<Integer> integers = extractNumbersAroundHyphens(nextValue);
                for (int k = integers.getFirst(); k < integers.getLast() + 1; k++) {
                  if (deleteElement(neatenResult, (k - 1) / 10, integers.getFirst() % 10 - 1)) {
                    ks.setCorrectMistakesNumber(ks.getCorrectMistakesNumber() + 1);
                  } else {
                    ks.setNonStandartNumber(ks.getNonStandartNumber() + 1);
                  }
                }
                j = j + integers.size();
              } else if (nextValueType == 4) {
                int i1 = Integer.parseInt(nextValue);
                if (i < 10 && isBetweenOneAndTen(nextValue)) {
                  row.remove(i1 - 1);
                  ks.setCorrectMistakesNumber(ks.getCorrectMistakesNumber() + 1);
                  j++;
                } else if (i >= 10 && isBetweenOneAndHundred(nextValue)) {
                  deleteElement(neatenResult, (i1 - 1) / 10, i1 % 10 - 1);
                  ks.setCorrectMistakesNumber(ks.getCorrectMistakesNumber() + 1);
                  j++;
                } else {
                  j++;
                  ks.setNonStandartNumber(ks.getNonStandartNumber() + 1);
                }
              } else {
                j++;
                ks.setNonStandartNumber(ks.getNonStandartNumber() + 1);
              }
            } else {
              row.add(group);
            }
          }
          // 少组处理，增加指定组
          else if (group.equals("ADD")) {
            if (j + 2 < result.get(i).size()) {
              String nextValue = result.get(i).get(j + 1);
              int nextValueType = checkHyphenPattern(nextValue);
              if (nextValueType == 1) {
                List<Integer> integers = extractNumbersAroundHyphens(nextValue);
                int flag = 2;
                List<String> addRow = new ArrayList<>();
                for (int k = integers.getFirst(); k < integers.getLast() + 1; k++) {
                  insertElement(addRow, (k - 1) % 10, result.get(i).get(j + flag));
                  ks.setCorrectMistakesNumber(ks.getCorrectMistakesNumber() + 1);
                  flag++;
                }
                insertList(neatenResult, (integers.getFirst() - 1) / 10, addRow);
                j = j + flag;
              } else if (nextValueType == 4) {
                int i1 = Integer.parseInt(nextValue);
                if (i < 10 && isBetweenOneAndTen(nextValue)) {
                  insertElement(row, i1 - 1, result.get(i).get(j + 2));
                  ks.setCorrectMistakesNumber(ks.getCorrectMistakesNumber() + 1);
                  j = j + 2;
                } else if (i >= 10 && isBetweenOneAndHundred(nextValue)) {
                  insertElement(neatenResult, i1 / 10, i1 % 10 - 1, result.get(i).get(j + 2));
                  ks.setCorrectMistakesNumber(ks.getCorrectMistakesNumber() + 1);
                  j = j + 2;
                } else {
                  ks.setNonStandartNumber(ks.getNonStandartNumber() + 1);
                }
              } else {
                ks.setNonStandartNumber(ks.getNonStandartNumber() + 1);
              }

            } else {
              row.add(group);
            }
          }
          // 改组处理
          else if (isBetweenOneAndTen(group)) {
            if (j + 1 < result.get(i).size()) {
              if (row.size() < 10) {
                ks.setNonStandartNumber(ks.getNonStandartNumber() + 1);
              }
              row = replaceElement(row, Integer.parseInt(group) - 1, result.get(i).get(j + 1));
              ks.setCorrectMistakesNumber(ks.getCorrectMistakesNumber() + 1);
              j++;
            }
          } else {
            row.add(group);
          }
        }
        if (!row.isEmpty()) {
          neatenResult.add(row);
        }
      }
    }

    // 少回行
    List<List<String>> newResult = new ArrayList<>();
    for (int i = 0; i < neatenResult.size(); i++) {
      if (neatenResult.get(i).size() > 10) {
        int flag = 0;
        for (int j = 10; j < neatenResult.get(i).size(); j++) {
          if (Objects.equals(userPages.get(i + 10).getKey(), neatenResult.get(i).get(j))) {
            flag = j;
            break;
          }
        }
        if (flag > 0) {
          List<String> s = neatenResult.get(i).subList(0, flag);
          newResult.add(s);
          List<String> s1 = neatenResult.get(i).subList(flag, neatenResult.get(i).size());
          newResult.add(s1);
          ks.setLessReturnLineNumber(ks.getLessReturnLineNumber() + 1);
        } else {
          newResult.add(neatenResult.get(i));
        }
      } else {
        newResult.add(neatenResult.get(i));
      }
    }
    neatenResult.clear();
    neatenResult.addAll(newResult);

    removeEmptyLists(neatenResult);
    int rowNum = 0;
    int colNum = 0;
    for (int j = 0; j < userPages.size(); j++) {
      TelexPatPageTransferDto groupKeyEntity = userPages.get(j);
      String groupKey = groupKeyEntity.getKey();
      if (rowNum < neatenResult.size()) {
        List<String> row = neatenResult.get(rowNum);
        if (colNum < row.size()) {
          String groupValue = row.get(colNum);
          if (groupValue.equals(groupKey)) {
            add(userId, pageValueResult, groupKeyEntity, groupValue);
          } else {
            // 跳过标识符，默认不跳过
            boolean isGoon = true;
            // 多组
            boolean isMoreGroup = true;
            List<String> moreGroupTemp = new ArrayList<>();
            moreGroupTemp.add(groupValue);
            // 判断是否多组: 拍发报文后4组 与当前报文对比，某组报文与目标报文相同，则中间的报文判定为多组
            for (int k = 1; k <= 4; k++) {
              int tempIndex = colNum + k;
              if (tempIndex < row.size()) {
                String nextGroupValue = row.get(tempIndex);
                if (Objects.equals(nextGroupValue, groupKey)) {
                  int gs = 0;
                  for (String mg : moreGroupTemp) {
                    add(userId, pageValueResult, groupKeyEntity, gs + "," + mg);
                    gs++;
                  }
                  add(userId, pageValueResult, groupKeyEntity, gs + "," + nextGroupValue);
                  colNum = tempIndex;
                  ks.setMuchLessGroupsNumber(ks.getMuchLessGroupsNumber() + 1);
                  isMoreGroup = false;
                  isGoon = false;
                  break;
                }
                moreGroupTemp.add(nextGroupValue);
              }
            }
            // 少组
            if (isMoreGroup) {
              for (int k = j + 1; k < j / 10 * 10 + 10; k++) {
                TelexPatPageTransferDto nextUserPages = userPages.get(k);
                if (nextUserPages.getKey().equals(groupValue)) {
                  int lackNum = k - j;
                  ks.setMuchLessGroupsNumber(ks.getMuchLessGroupsNumber() + lackNum);
                  for (int i1 = 0; i1 < lackNum; i1++) {
                    add(userId, pageValueResult, userPages.get(j + i1), "", groupKeyEntity.getSort() + i1);
                  }
                  add(userId, pageValueResult, nextUserPages, groupValue);
                  j += lackNum;
                  // 跳过标识符，设为跳过
                  isGoon = false;
                  break;
                }
              }
            }

            // 多少码
            if (isGoon) {
              // 多码
              if (groupValue.length() > 4) {
                if (groupValue.contains(groupKey)) {
                  add(userId, pageValueResult, groupKeyEntity, groupValue);
                  ks.setMuchLessCodeNumber(ks.getMuchLessCodeNumber() + 1);
                  isGoon = false;
                }
              }
              // 少码
              if (groupValue.length() < 4) {
                if (groupKey.contains(groupValue)) {
                  add(userId, pageValueResult, groupKeyEntity, groupValue);
                  ks.setMuchLessCodeNumber(ks.getMuchLessCodeNumber() + 1);
                  isGoon = false;
                }
              }
            }

            // 错码
            if (isGoon) {
              add(userId, pageValueResult, groupKeyEntity, groupValue);
              ks.setErrorCodeNumber(ks.getErrorCodeNumber() + 1);
            }
          }
        } else {
          // 少行
          add(userId, pageValueResult, groupKeyEntity, "");
          ks.setMuchLessGroupsNumber(ks.getMuchLessGroupsNumber() + 1);
        }
      }
      // 少行
      else {
        add(userId, pageValueResult, groupKeyEntity, "");
        if (colNum == 0) {
          ks.setMuchLessLineNumber(ks.getMuchLessLineNumber() + 1);
        }
      }
      // 多行
      if (j == userPages.size() - 1) {
        if (rowNum < neatenResult.size() - 1) {
          ks.setMuchLessLineNumber(ks.getMuchLessLineNumber() + neatenResult.size() - rowNum);
          int flag = 1;
          for (int k = rowNum; k < neatenResult.size(); k++) {
            for (int l = 0; l < neatenResult.get(k).size(); l++) {
              add(userId, pageValueResult, groupKeyEntity, "", neatenResult.get(k).get(l), j + flag);
              flag++;
            }
          }
        }
      }
      // 行尾多组
      if (9 == groupKeyEntity.getSort() % 10) {
        int flag = 1;
        if (rowNum < neatenResult.size()) {
          for (int k = colNum + 1; k < neatenResult.get(rowNum).size(); k++) {
            System.out.println("行尾多组" + neatenResult.get(rowNum).get(k));
            ks.setMuchLessGroupsNumber(ks.getMuchLessGroupsNumber() + 1);
            flag++;
          }
        }
        colNum += flag;
      } else {
        colNum++;
      }
      if (rowNum != (j + 1) / 10) {
        colNum = 0;
        rowNum++;
      }
    }
  }

  public static List<Integer> calculate(int n) {
    if (n < 1) {
      return null;
    }
    int m = n - 1;
    int first = m / 10; // 十位及以上的部分
    int second = m % 10; // 个位部分
    return List.of(first, second);
  }

  /**
   * 移除列表中所有空列表或null元素
   *
   * @param listOfLists 包含字符串列表的列表（会被修改）
   */
  public static void removeEmptyLists(List<List<String>> listOfLists) {
    listOfLists.removeIf(list -> list == null || list.isEmpty());
  }

  /**
   * 在指定位置插入新列表，必要时填充空列表以达到目标索引
   *
   * @param target  目标列表（会被修改）
   * @param index   插入位置索引（非负）
   * @param newList 要插入的新列表
   * @throws IllegalArgumentException 如果index为负数
   */
  public static void insertList(List<List<String>> target, int index, List<String> newList) {
    // 处理非法下标
    if (index < 0) {
      throw new IllegalArgumentException("Index cannot be negative");
    }

    // 当目标列表大小不足时，填充空列表直到达到所需大小
    while (target.size() < index) {
      target.add(new ArrayList<>());
    }

    // 在指定位置插入新列表
    target.add(index, newList);
  }

  /**
   * 提取字符串开头的连续数字部分
   *
   * @param input 输入字符串
   * @return 包含开头连续数字的子字符串，若开头无数字则返回空字符串
   */
  public static String removeAfterDigits(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }

    // 查找第一个非数字字符的位置
    int endIndex = 0;
    while (endIndex < input.length() && Character.isDigit(input.charAt(endIndex))) {
      endIndex++;
    }

    // 如果开头没有数字，返回空字符串
    if (endIndex == 0) {
      return "";
    }

    // 返回开头的连续数字部分
    return input.substring(0, endIndex);
  }

  /**
   * 从字符串中提取页码编号（要求以P/p结尾）
   *
   * @param str 输入字符串
   * @return 有效页码（1-999之间的整数）或-1表示无效输入
   */
  public static int extractPageNumber(String str) {
    if (str == null || str.length() < 2) {
      return -1; // 无效输入
    }

    // 检查最后一个字符是否为P/p
    char lastChar = str.charAt(str.length() - 1);
    if (lastChar != 'P' && lastChar != 'p') {
      return -1;
    }

    // 提取开头的连续数字部分
    int endIndex = 0;
    while (endIndex < str.length() - 1 && // 保留最后一位P/p
        Character.isDigit(str.charAt(endIndex))) {
      endIndex++;
    }

    String numStr = str.substring(0, endIndex);

    // 验证数字部分
    if (numStr.isEmpty() || numStr.length() > 3) {
      return -1;
    }
    if (numStr.length() > 1 && numStr.startsWith("0")) {
      return -1; // 前导零检查
    }

    try {
      int num = Integer.parseInt(numStr);
      if (num >= 1 && num <= 999) {
        return num;
      }
    } catch (NumberFormatException e) {
      // 忽略异常，返回-1
    }
    return -1;
  }

  /**
   * 判断字符串是否符合页码修改格式
   *
   * @param str 输入字符串
   * @return 布尔值表示是否符合格式要求
   */
  public static boolean isPageModification(String str) {
    if (str == null || str.length() < 2) {
      return false;
    }

    // 检查最后一个字符是否为P/p
    char lastChar = str.charAt(str.length() - 1);
    if (lastChar != 'P' && lastChar != 'p') {
      return false;
    }

    // 提取开头的连续数字部分
    int endIndex = 0;
    while (endIndex < str.length() && Character.isDigit(str.charAt(endIndex))) {
      endIndex++;
    }
    String numStr = str.substring(0, endIndex);

    // 验证数字部分
    if (numStr.isEmpty() || numStr.length() > 3) {
      return false; // 无数字或超过3位数
    }
    return numStr.length() == 1 || numStr.charAt(0) != '0'; // 含前导零（如"01"）
  }

  public static String transformString(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }

    // 找到最后一个斜杠的位置
    int lastSlashIndex = input.lastIndexOf('/');

    if (lastSlashIndex == -1) {
      // 没有斜杠，返回原字符串
      return input;
    } else if (lastSlashIndex == input.length() - 1) {
      // 斜杠在末尾，返回空字符串
      return "";
    } else {
      // 返回最后一个斜杠之后的内容
      return input.substring(lastSlashIndex + 1);
    }
  }

  public static boolean containsPattern(String input) {
    if (input == null) return false;
    return PAGE_PATTERN1.matcher(input).find();
  }

  public static void main(String[] args) {
    String[] testCases = {
        "09*22/0922",  // true - 有效模式
        "aa*aa/0922",  // true - 有效模式
        "////",        // false - 无有效序列
        "a///",        // false - 右侧序列为空
        "1///",        // false - 右侧序列为空
        "////1",       // false - 左侧序列为空
        "////a",       // false - 左侧序列为空
        "a/b",         // true - 最小有效模式
        "a//b",        // false - 斜杠之间无有效序列
        "test/page",   // true - 常规有效模式
        "/alone",      // false - 缺少左侧序列
        "only/",       // false - 缺少右侧序列
        "no_slash",    // false - 无斜杠
        "multiple/a/b/c" // true - 包含多个有效模式
    };

    for (String test : testCases) {
      boolean result = containsPattern(test);
      System.out.printf("输入: %-20s 结果: %-5b %s\n",
          "\"" + test + "\"",
          result,
          result ? "✓" : "✗");
    }
  }

  /**
   * 验证数字字符串是否合法（不允许前导零）
   *
   * @param numStr 数字字符串
   * @return 布尔值表示是否有效
   */
  private static boolean isValidNumber(String numStr) {
    // 检查数字字符串是否合法：不允许前导零（除非是单个0）
    return numStr.length() <= 1 || !numStr.startsWith("0");
  }

  /**
   * 提取三个连续连字符前后的数字
   *
   * @param input 输入字符串
   * @return 包含两个数字的列表（前一个和后一个），若未找到有效格式则返回空列表
   */
  public static List<Integer> extractNumbersAroundHyphens(String input) {
    List<Integer> result = new ArrayList<>();

    // 检查输入有效性
    if (input == null || input.isEmpty()) {
      return result;
    }

    // 查找第一个"---"的位置
    int hyphenStart = input.indexOf("---");
    if (hyphenStart == -1) {
      return result; // 没有找到三个连续连字符
    }

    // 提取前面的数字
    Integer beforeNum = extractNumberBefore(input, hyphenStart);
    if (beforeNum == null) {
      return result;
    }

    // 提取后面的数字
    int afterStart = hyphenStart + 3; // 跳过三个连字符
    Integer afterNum = extractNumberAfter(input, afterStart);
    if (afterNum == null) {
      return result;
    }

    // 添加有效数字到结果列表
    result.add(beforeNum);
    result.add(afterNum);
    return result;
  }

  // 提取连字符前的数字
  private static Integer extractNumberBefore(String input, int hyphenStart) {
    if (hyphenStart == 0) {
      return null; // 连字符前面没有字符
    }

    // 向前查找数字的起始位置
    int numEnd = hyphenStart - 1;
    int numStart = numEnd;

    // 找到数字的开始位置
    while (numStart >= 0 && Character.isDigit(input.charAt(numStart))) {
      numStart--;
    }
    numStart++; // 调整到数字实际开始位置

    // 提取数字字符串
    String numStr = input.substring(numStart, numEnd + 1);
    return parseValidNumber(numStr);
  }

  // 提取连字符后的数字
  private static Integer extractNumberAfter(String input, int afterStart) {
    if (afterStart >= input.length()) {
      return null; // 连字符后面没有字符
    }

    // 向后查找数字的结束位置
    int numEnd = afterStart;

    // 找到数字的结束位置
    while (numEnd < input.length() && Character.isDigit(input.charAt(numEnd))) {
      numEnd++;
    }

    // 提取数字字符串
    String numStr = input.substring(afterStart, numEnd);
    return parseValidNumber(numStr);
  }

  // 解析并验证数字是否在1-999范围内
  private static Integer parseValidNumber(String numStr) {
    if (numStr == null || numStr.isEmpty()) {
      return null;
    }

    try {
      // 尝试解析为整数
      int number = Integer.parseInt(numStr);

      // 检查范围
      if (number >= 1 && number <= 999) {
        return number;
      }
    } catch (NumberFormatException e) {
      // 解析失败，不是有效数字
    }
    return null;
  }

  public static int checkHyphenPattern(String input) {
    // 如果不包含连字符，返回4
    if (input == null || !input.contains("-")) {
      return 4;
    }

    int hyphenCount = 0;
    int startIndex = -1;
    int endIndex = -1;
    boolean foundValidPattern = false;
    int firstNum = -1, secondNum = -1;

    // 遍历字符串，查找连续的连字符序列
    for (int i = 0; i < input.length(); i++) {
      if (input.charAt(i) == '-') {
        if (hyphenCount == 0) {
          startIndex = i; // 记录连字符序列的开始位置
        }
        hyphenCount++;
        endIndex = i; // 更新连字符序列的结束位置
      } else if (hyphenCount > 0) {
        // 检查是否满足条件：3个连字符且前后都有有效数字
        if (hyphenCount == 3) {
          firstNum = getNumberBefore(input, startIndex);
          secondNum = getNumberAfter(input, endIndex);

          if (firstNum > 0 && secondNum > 0) {
            foundValidPattern = true;
            break; // 找到有效模式，停止搜索
          }
        }
        // 重置计数器
        hyphenCount = 0;
      }
    }

    // 处理以连字符结尾的情况
    if (!foundValidPattern && hyphenCount == 3) {
      firstNum = getNumberBefore(input, startIndex);
      secondNum = getNumberAfter(input, endIndex);
      if (firstNum > 0 && secondNum > 0) {
        foundValidPattern = true;
      }
    }

    // 根据找到的模式和数字比较结果返回
    if (foundValidPattern) {
      // 第一个数字比第二个大，返回3；否则返回1
      return firstNum > secondNum ? 3 : 1;
    } else {
      // 尝试查找任何连字符序列前后的数字
      firstNum = -1;
      secondNum = -1;

      // 重新查找连字符序列
      hyphenCount = 0;
      startIndex = -1;
      for (int i = 0; i < input.length(); i++) {
        if (input.charAt(i) == '-') {
          if (hyphenCount == 0) {
            startIndex = i;
          }
          hyphenCount++;
          endIndex = i;
        } else if (hyphenCount > 0) {
          firstNum = getNumberBefore(input, startIndex);
          secondNum = getNumberAfter(input, endIndex);
          break;
        }
      }

      // 处理以连字符结尾的情况
      if (hyphenCount > 0 && firstNum < 0) {
        firstNum = getNumberBefore(input, startIndex);
        secondNum = getNumberAfter(input, endIndex);
      }

      // 如果找到两个有效数字，比较它们
      if (firstNum > 0 && secondNum > 0) {
        return firstNum > secondNum ? 3 : 2;
      }

      // 其他情况返回2
      return 2;
    }
  }

  // 获取连字符前的数字
  private static int getNumberBefore(String input, int startIndex) {
    if (startIndex <= 0) return -1;

    // 向前查找数字的起始位置
    int numStart = startIndex - 1;
    while (numStart >= 0 && Character.isDigit(input.charAt(numStart))) {
      numStart--;
    }
    numStart++; // 调整到数字实际开始位置

    String numberStr = input.substring(numStart, startIndex);
    return parseNumber(numberStr);
  }

  // 获取连字符后的数字
  private static int getNumberAfter(String input, int endIndex) {
    if (endIndex >= input.length() - 1) return -1;

    // 向后查找数字的结束位置
    int numEnd = endIndex + 1;
    while (numEnd < input.length() && Character.isDigit(input.charAt(numEnd))) {
      numEnd++;
    }

    String numberStr = input.substring(endIndex + 1, numEnd);
    return parseNumber(numberStr);
  }

  // 解析数字字符串，返回有效数字或-1
  private static int parseNumber(String numberStr) {
    if (numberStr == null || numberStr.isEmpty()) return -1;

    try {
      // 去除前导零
      numberStr = numberStr.replaceFirst("^0+(?!$)", "");
      int number = Integer.parseInt(numberStr);
      return (number >= 1 && number <= 999) ? number : -1;
    } catch (NumberFormatException e) {
      return -1; // 转换失败说明不是有效数字
    }
  }


  /**
   * 在指定下标插入元素，长度不足时用空字符串占位
   *
   * @param list    目标列表
   * @param index   要插入的下标位置
   * @param element 要插入的元素
   */
  public static void insertElement(List<String> list, int index, String element) {
    // 处理负数索引
    if (index < 0) {
      System.out.println("错误: 索引不能为负数");
      return;
    }

    // 计算需要填充的空位数
    int requiredPlaceholders = index - list.size();

    // 添加空字符串占位
    for (int i = 0; i < requiredPlaceholders; i++) {
      list.add(""); // 添加空字符串占位
    }

    // 插入元素
    if (index < list.size()) {
      // 插入到现有位置（会移动后续元素）
      list.add(index, element);
    } else {
      // 添加到末尾（index == list.size() 或 刚刚填充到index）
      list.add(element);
    }
  }

  /**
   * 在指定位置插入元素，长度不足时用空字符串占位
   *
   * @param nestedList 嵌套列表
   * @param outerIndex 外层索引
   * @param innerIndex 内层索引
   * @param value      要插入的值
   */
  public static void insertElement(List<List<String>> nestedList, int outerIndex, int innerIndex, String value) {
    innerIndex = Math.max(innerIndex, 0);
    // 确保外层列表足够大
    while (outerIndex >= nestedList.size()) {
      nestedList.add(new ArrayList<>());
    }

    List<String> innerList = nestedList.get(outerIndex);

    // 确保内层列表足够长以支持插入位置
    while (innerIndex > innerList.size()) {
      innerList.add(""); // 用空字符串占位直到插入位置
    }

    // 在指定位置插入元素
    innerList.add(innerIndex, value);
  }


  /**
   * 删除指定位置的元素
   *
   * @param nestedList 嵌套列表
   * @param outerIndex 外层索引
   * @param innerIndex 内层索引
   * @return true 删除成功, false 索引无效
   */
  public static boolean deleteElement(List<List<String>> nestedList, int outerIndex, int innerIndex) {
    // 检查外层索引
    if (outerIndex < 0 || outerIndex >= nestedList.size()) {
      return false;
    }

    List<String> innerList = nestedList.get(outerIndex);

    // 检查内层索引
    if (innerIndex < 0 || innerIndex >= innerList.size()) {
      return false;
    }

    // 执行删除
    innerList.remove(innerIndex);
    return true;
  }

  public static boolean isValid(String str) {
    // 检查长度是否为4
    if (str == null || str.length() != 4) {
      return false;
    }

    // 检查第一个字符是否是字母或数字
    if (!Character.isLetterOrDigit(str.charAt(0))) {
      return false;
    }

    // 检查最后一个字符是否是斜杠
    if (str.charAt(3) != '/') {
      return false;
    }

    // 从后往前找到第一个非斜杠的位置
    int lastNonSlashIndex = -1;
    for (int i = 3; i >= 0; i--) {
      if (str.charAt(i) != '/') {
        lastNonSlashIndex = i;
        break;
      }
    }

    // 检查前缀部分（0到lastNonSlashIndex）是否全是字母/数字
    for (int i = 0; i <= lastNonSlashIndex; i++) {
      if (!Character.isLetterOrDigit(str.charAt(i))) {
        return false;
      }
    }

    return true;
  }

  public static List<String> replaceElement(List<String> originalList, int index, String newValue) {
    if (originalList == null || index < 0 || index >= originalList.size()) {
      return originalList; // 无效索引直接返回原列表
    }
    // 创建新列表（避免修改原始列表）
    List<String> newList = new ArrayList<>(originalList);

    // 验证下标有效性
    if (index >= newList.size()) {
      return originalList;
    }

    // 替换指定位置的元素
    newList.set(index, newValue);
    return newList;
  }

  public static boolean isBetweenOneAndTen(String str) {
    if (str == null || str.isEmpty()) {
      return false;
    }

    // 添加前导零检查：长度>1且以'0'开头则无效
    if (str.length() > 1 && str.startsWith("0")) {
      return false;
    }

    try {
      int num = Integer.parseInt(str);
      return num >= 1 && num <= 10;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  public static boolean isBetweenOneAndHundred(String str) {
    if (str == null || str.isEmpty()) {
      return false;
    }

    // 尝试将字符串解析为整数
    try {
      int num = Integer.parseInt(str);
      return num >= 1 && num <= 100;
    } catch (NumberFormatException e) {
      // 如果解析失败，返回false
      return false;
    }
  }

  public static boolean isOnlySlashes(String str) {
    // 处理空字符串和null
    if (str == null || str.isEmpty()) {
      return false;
    }

    // 检查每个字符是否都是斜杠
    for (int i = 0; i < str.length(); i++) {
      if (str.charAt(i) != '/') {
        return false;
      }
    }

    return true;
  }

  public static boolean hasMiddleSlash(String str) {
    if (str == null || str.length() < 3) {
      return false;
    }

    int slashIndex = -1;
    int slashCount = 0;

    // 检查每个字符并统计斜杠
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);

      // 检查是否只包含字母、数字或斜杠
      if (!Character.isLetterOrDigit(c) && c != '/') {
        return false;
      }

      // 统计斜杠位置和数量
      if (c == '/') {
        slashCount++;
        slashIndex = i;
      }
    }

    // 必须恰好有一个斜杠
    if (slashCount != 1) {
      return false;
    }

    // 斜杠不能在开头或结尾
    return slashIndex > 0 && slashIndex < str.length() - 1;
  }

  public static boolean isValidFormat(String str) {
    if (str == null || str.length() < 2) {
      return false;
    }

    // 检查第一个字符必须是字母或数字
    char firstChar = str.charAt(0);
    if (!Character.isLetterOrDigit(firstChar)) {
      return false;
    }

    // 检查最后一个字符必须是斜杠
    return str.charAt(str.length() - 1) == '/';
  }

  public static List<String> splitByFour(String input) {
    List<String> result = new ArrayList<>();
    if (input == null || input.isEmpty()) {
      return result;
    }

    for (int i = 0; i < input.length(); i += 4) {
      // 计算当前分组的结束位置
      int end = Math.min(i + 4, input.length());
      result.add(input.substring(i, end));
    }
    return result;
  }

  public static String getLastAfterDash(String input) {
    if (input == null) return null;

    int lastDashIndex = input.lastIndexOf('-');

    if (lastDashIndex == -1) {
      // 没有找到破折号，返回整个字符串
      return null;
    } else if (lastDashIndex == input.length() - 1) {
      // 破折号在末尾，返回空字符串
      return null;
    } else {
      // 返回破折号后的子字符串
      return input.substring(lastDashIndex + 1);
    }
  }

  public static Integer groupNumber(String input) {
    if (input == null || input.isEmpty()) {
      return 0;
    }

    int length = input.length();
    int start = 0;  // 当前单词的起始位置
    int pos = 0;    // 当前扫描位置
    List<String> currentLine = new ArrayList<>();
    Integer gn = 0;

    while (pos < length) {
      char c = input.charAt(pos);

      // 遇到空格或制表符结束当前单词
      if (c == ' ' || c == '\t') {
        if (start < pos) {
          currentLine.add(input.substring(start, pos));
          gn++;
        }
        start = pos + 1;  // 跳过空格
      }
      // 遇到换行符结束当前行
      else if (c == '\n' || c == '\r') {
        // 添加当前行最后一个单词
        if (start < pos) {
          currentLine.add(input.substring(start, pos));
          gn++;
        }

        // 添加非空行到结果
        if (!currentLine.isEmpty()) {
          currentLine = new ArrayList<>();
        }

        // 处理Windows换行符(\r\n)
        if (c == '\r' && pos + 1 < length && input.charAt(pos + 1) == '\n') {
          pos++;  // 跳过额外的'\n'
        }
        start = pos + 1;  // 重置单词起始位置
      }
      pos++;
    }

    // 处理最后一行
    if (start < pos) {
      currentLine.add(input.substring(start, pos));
      gn++;
    }

    return gn;
  }

  /**
   * 计算BigDecimal列表的平均值
   *
   * @param numbers      输入列表
   * @param scale        结果精度（小数位数）
   * @param roundingMode 舍入模式
   * @return Optional包含平均值，空列表返回Optional.empty()
   */
  public static Optional<BigDecimal> calculateAverage(
      List<BigDecimal> numbers,
      int scale,
      RoundingMode roundingMode
  ) {
    // 处理空列表和空值
    if (numbers == null || numbers.isEmpty()) {
      return Optional.empty();
    }

    // 使用BigDecimal累加，避免精度损失
    BigDecimal sum = BigDecimal.ZERO;
    int count = 0;

    for (BigDecimal num : numbers) {
      if (num != null) {
        sum = sum.add(num);
        count++;
      }
    }

    // 全空列表处理
    if (count == 0) {
      return Optional.empty();
    }

    // 计算平均值 = 总和 / 元素数量
    return Optional.of(
        sum.divide(BigDecimal.valueOf(count), scale, roundingMode)
    );
  }

  /**
   * 默认精度版本（0位小数，四舍五入）
   */
  public static Optional<BigDecimal> calculateAverage(List<BigDecimal> numbers) {
    return calculateAverage(numbers, 0, RoundingMode.HALF_UP);
  }

  private static void add(String userId, List<TelexPatValueTransferDto> pageValueResult, TelexPatPageTransferDto key, String value) {
    pageValueResult.add(new TelexPatValueTransferDto(
        userId,
        key.getTrainId(),
        key.getPageNumber(),
        key.getKey(),
        value,
        key.getSort()
    ));
  }

  private static void add(String userId, List<TelexPatValueTransferDto> pageValueResult, TelexPatPageTransferDto key, String value, Integer sort) {
    pageValueResult.add(new TelexPatValueTransferDto(
        userId,
        key.getTrainId(),
        key.getPageNumber(),
        key.getKey(),
        value,
        sort
    ));
  }

  private static void add(String userId, List<TelexPatValueTransferDto> pageValueResult, TelexPatPageTransferDto dto, String key, String value, Integer sort) {
    pageValueResult.add(new TelexPatValueTransferDto(
        userId,
        dto.getTrainId(),
        dto.getPageNumber(),
        key,
        value,
        sort
    ));
  }
}
