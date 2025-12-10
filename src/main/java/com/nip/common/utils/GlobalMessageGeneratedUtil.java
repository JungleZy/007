package com.nip.common.utils;


import com.nip.dto.vo.PostTelegramGenerateCheck;

import java.util.*;

/**
 * 报文生成器
 */
public class GlobalMessageGeneratedUtil {
  private static final List<String> MIN_DIGITS = List.of("1", "2", "3", "4", "5");
  private static final List<String> MAX_DIGITS = List.of("6", "7", "8", "9", "0");
  private static final Random RANDOM = new Random();

  /**
   * 数码报（对手报）
   *
   * @param groupNumber 生成数量
   * @param avg         是否平均
   * @param random      是否随机
   */
  //10组一平均
  public static List<String> generatedNumber(Integer groupNumber, boolean avg, boolean random) {
    List<String> ret = new ArrayList<>();
    Random r = new Random();
    List<Integer> intArray = new ArrayList<>();
    int item = 0;
    //随机
    if (random) {
      //平均
      if (avg) {
        List<String> leftList = new ArrayList<>();
        List<String> rightList = new ArrayList<>();
        int leftNum = 1;
        int rightNum = 6;
        for (int i = 0; i < groupNumber; i++) {
          for (int j = 0; j < 2; j++) {
            if (leftNum > 5) {
              leftNum = 1;
            }
            leftList.add(String.valueOf(leftNum));

            if (rightNum == 10) {
              rightNum = 0;
            } else if (rightNum == 1) {
              rightNum = 6;
            }
            rightList.add(String.valueOf(rightNum));

            leftNum++;
            rightNum++;
          }

          if ((leftList.size() % 20 == 0) || i == groupNumber - 1) {
            //将连续的进行错乱
            randomExchange(leftList);
            randomExchange(rightList);
            //将两个数组进行组合的到对手报文
            assembling(leftList, rightList, ret);
            leftList.clear();
            rightList.clear();
          }
        }

      } else { //不平均
        for (int i = 0; i < groupNumber; i++) {
          StringBuilder sb = new StringBuilder();
          while (true) {
            int ri = r.nextInt(10);
            boolean b = false;
            if (sb.length() > 0) {
              //获取上次内容
              int lastNum = Integer.valueOf(String.valueOf(sb.charAt(sb.length() - 1)));
              if (lastNum >= 1 && lastNum <= 5) {
                //本次元素必须是 6-7-8-9-0
                b = ri >= 6 || ri == 0;
              } else {
                //本次元素必须是 1-2-3-4-5
                b = ri >= 1 && ri <= 5;
              }
            } else {
              b = true;
            }
            if (sb.indexOf(String.valueOf(ri)) == -1 && b) {
              sb.append(ri);
              if (sb.length() == 4) {
                break;
              }
            }
          }
          ret.add(sb.toString());
        }
      }
    } else { // 不随机 0-9
      int element = 0;
      for (int i = 0; i < groupNumber; i++) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < 4; j++) {
          if (element == 10) {
            element = 0;
          }
          sb.append(element);
          element++;
        }
        ret.add(sb.toString());
      }
    }

    //校验一下
    //check(ret);
    return ret;
  }

  /**
   * 随机交换
   *
   * @param rows
   */
  private static void randomExchange(List<String> rows) {
    Random r = new Random();
    Collections.shuffle(rows, r);
    for (int i = 0; i < rows.size(); i++) {
      String row = rows.get(i);
      if (i != rows.size() - 1) {
        //获取下一组与当前组比对，是否相同，如果相同则再与下组交换判断，直至不相同
        String nextRow = rows.get(i + 1);
        if (Objects.equals(nextRow, row)) {
          while (true) {
            int index = r.nextInt(rows.size());
            String proRRow = "";
            if (i - 1 > 0) {
              proRRow = rows.get(i - 1);
            }
            String rRow = rows.get(index);
            if (index != i && !Objects.equals(rRow, row) && !Objects.equals(proRRow, rRow)) {
              if (index == 0) {
                //需要判断后面一个不能与当前值相同
                String nextRRow = rows.get(index + 1);

                if (!Objects.equals(row, nextRRow)) {
                  rows.set(i, rRow);
                  rows.set(index, row);
                  break;
                }

              } else if (index == rows.size() - 1) {
                //需要判断前面一个不能与当前值相同
                String nextRRow = rows.get(index - 1);
                if (!Objects.equals(row, nextRRow)) {
                  rows.set(i, rRow);
                  rows.set(index, row);
                  break;
                }
              } else {
                //前后都不相同
                String nextRRow = rows.get(index + 1);
                String preRRow = rows.get(index - 1);
                if (!Objects.equals(row, nextRRow) && !Objects.equals(row, preRRow)) {
                  rows.set(i, rRow);
                  rows.set(index, row);
                  break;
                }

              }
            }
          }

        }
      }


    }
  }

  /**
   * 合成对手报
   *
   * @param leftList
   * @param rightList
   * @return
   */
  private static void assembling(List<String> leftList, List<String> rightList, List<String> ret) {
    Random r = new Random();
    int size = leftList.size();
    for (int i = 0; i < size / 2; i++) {
      StringBuilder sb = new StringBuilder();
      // 0 左边 1 右边
      int lOrR = r.nextInt(2);
      for (int j = 0; j < 2; j++) {
        String left = leftList.getFirst();
        String right = rightList.getFirst();
        if (lOrR == 0) {
          sb.append(left);
          sb.append(right);
        } else {
          sb.append(right);
          sb.append(left);
        }
        leftList.remove(0);
        rightList.remove(0);
      }
      //整页检查去重
      PostTelegramGenerateCheck newGorup = pageCheckDuplicate(ret, sb.toString());
      ret.add(newGorup.getGroup());

    }
  }


  /**
   * 字码报
   *
   * @param groupNumber 生成组数
   * @param avg         是否平均
   * @param random      是否随机
   */
  public static List<String> generatedWord(Integer groupNumber, boolean avg, boolean random) {
    Random r = new Random();
    List<String> strArray = new ArrayList<>();
    List<String> ret = new ArrayList<>();
    //找出页
    int pageNumber = groupNumber / 100;
    pageNumber = groupNumber % 100 > 0 ? pageNumber + 1 : pageNumber;

    //是否随机
    if (random) {
      if (avg) {
        //是否平均
        for (int i = 0; i < pageNumber; i++) {
          int sign = 65;
          int number = i == pageNumber - 1 ? groupNumber - (i * 100) : 100;
          for (int j = 0; j < number * 4; j++) {
            char c = (char) sign;
            strArray.add(String.valueOf(c));
            sign = sign == 90 ? 64 : sign;
            sign++;
          }
        }
        int size = strArray.size() / 4;
        Collections.shuffle(strArray);
        for (int i = 0; i < size; i++) {
          StringBuilder sb = new StringBuilder();
          for (int j = 0; j < 4; j++) {
            while (true) {
              int index = r.nextInt(strArray.size());
              String element = strArray.get(index);
              if (sb.indexOf(element) == -1) {
                sb.append(element);
                strArray.remove(index);
                break;
              } else if (j == 3) {
                //替换前面的报文
                for (int k = 0; k < ret.size(); k++) {
                  String lastGroup = ret.get(k);
                  if (!lastGroup.contains(element)) {
                    for (int z = 0; z < lastGroup.length(); z++) {
                      String e = String.valueOf(lastGroup.charAt(z));
                      if (sb.indexOf(e) == -1) {
                        ret.set(k, lastGroup.replaceFirst(e, element));
                        sb.append(e);
                        break;
                      }
                    }
                  }
                  if (sb.length() == 4) {
                    break;
                  }
                }
                if (sb.length() == 4) {
                  break;
                }
              }
            }
          }
          ret.add(sb.toString());
        }
      } else {
        //不平均
        for (int i = 0; i < groupNumber; i++) {
          StringBuilder sb = new StringBuilder();
          for (int j = 0; j < 4; j++) {
            while (true) {
              int ri = r.nextInt(26) + 65;
              char c = (char) ri;
              String element = String.valueOf(c);
              if (sb.indexOf(element) == -1) {
                sb.append(c);
                break;
              }
            }

          }
          ret.add(sb.toString());
        }

      }
    } else {
      // 不随机
      int sign = 65;
      for (int i = 0; i < groupNumber; i++) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < 4; j++) {
          char c = (char) sign;
          String element = String.valueOf(c);
          sb.append(element);
          if (sign == 90) {
            sign = 65;
          } else {
            sign++;
          }
        }
        ret.add(sb.toString());
      }
    }
    return ret;
  }

  /**
   * 混合报
   *
   * @param groupNumber 生成组数
   * @param avg         是否平均
   * @param random      是否随机
   * @return 生成的混合报
   */
  public static List<String> generatedMingle(Integer groupNumber, boolean avg, boolean random) {
    Random r = new Random();
    List<String> ret = new ArrayList<>();
    List<String> strArray = new ArrayList<>();
    //找出页
    int pageNumber = groupNumber / 100;
    pageNumber = groupNumber % 100 > 0 ? pageNumber + 1 : pageNumber;
    if (random) {
      if (avg) {
        int sign = 0;
        for (int i = 0; i < pageNumber; i++) {
          int number = i == pageNumber - 1 ? groupNumber - (i * 100) : 100;
          for (int j = 0; j < number * 4; j++) {
            char c;
            if (sign < 10) {
              c = (char) (sign + 48);
            } else {
              c = (char) (sign + 55);
            }
            strArray.add(String.valueOf(c));
            sign = sign == 35 ? -1 : sign;
            sign++;
          }
        }
        int size = strArray.size() / 4;
        for (int i = 0; i < size; i++) {
          StringBuilder sb = new StringBuilder();
          for (int j = 0; j < 4; j++) {
            while (true) {
              int ri = r.nextInt(strArray.size());
              String element = strArray.get(ri);
              if (j == 3 && sb.charAt(0) < 10 && sb.charAt(1) < 10 && sb.charAt(2) < 10 && element.charAt(0) < 10) {
                //如果前三个码都是数字，最后一码则，必须是字码
                continue;
              }
              if (sb.indexOf(element) == -1) {
                sb.append(element);
                strArray.remove(element);
                break;
              }
            }
          }
          ret.add(sb.toString());
        }
      } else {
        for (int i = 0; i < groupNumber; i++) {
          StringBuilder sb = new StringBuilder();
          for (int j = 0; j < 4; j++) {
            while (true) {

              int ri = r.nextInt(36);
              char c;
              if (ri < 10) {
                c = (char) (ri + 48);
              } else {
                c = (char) (ri + 55);
              }
              String element = String.valueOf(c);
              if (j == 3 && sb.charAt(0) < 10 && sb.charAt(1) < 10 && sb.charAt(2) < 10 && element.charAt(0) < 10) {
                //如果前三个码都是数字，最后一码则，必须是字码
                continue;
              }

              if (sb.indexOf(element) == -1) {
                sb.append(element);
                break;
              }
            }

          }

          ret.add(sb.toString());
        }
      }
    } else {
      int sign = 0;
      for (int i = 0; i < groupNumber; i++) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < 4; j++) {
          char c;
          if (sign < 10) {
            c = (char) (sign + 48);
          } else {
            c = (char) (sign + 55);
          }
          sb.append(c);
          if (sign == 35) {
            sign = 0;
            continue;
          }
          sign++;
        }
        ret.add(sb.toString());
      }
    }
    return ret;
  }

  /**
   * 收报数码报(未完成)
   *
   * @param groupNumber 生成数量
   * @param avg         是否平均
   * @param random      是否随机
   * @return
   */
  public static List<String> generateReceiveNumber(Integer groupNumber, boolean avg, boolean random) {
    List<String> ret = new ArrayList<>();
    List<String> intArray = new ArrayList<>();
    if (random) {
      if (avg) {
        for (int i = 0; i < groupNumber; i++) {
          //todo ...
        }
      } else {

      }
    } else {

    }
    return ret;
  }


  /**
   * 数据综合报
   *
   * @param groupNumber 生成页码
   * @return 返回数据综合报
   */
  public static List<String> generatedNumberGeneral(Integer groupNumber) {
    List<String> ret = new ArrayList<>();
    List<String> intArray = new ArrayList<>();
    Random r = new Random();
    int sign = 0;
    for (int i = 0; i < groupNumber; i++) {
      for (int j = 0; j < 4; j++) {
        if (sign > 9) {
          sign = 0;
        }
        intArray.add(String.valueOf(sign));
        sign++;
      }
      if ((intArray.size() % 40 == 0 && i != 0) || i == groupNumber - 1) {
        int size = intArray.size() / 4;
        for (int j = 0; j < size; j++) {
          StringBuilder sb = new StringBuilder();
          for (int k = 0; k < 4; k++) {
            int count = 0;
            while (true) {
              int index = r.nextInt(intArray.size());
              String element = intArray.get(index);
              if (sb.indexOf(element) == -1) {
                sb.append(element);
                intArray.remove(index);
                break;
              }
              count++;
              if (j == size - 1 && intArray.size() <= 2 && count > 10) {
                for (int z = 0; z < intArray.size(); z++) {
                  //出现死循环了 找出之前的组
                  String e = intArray.get(z); //死循环的元素
                  for (int l = 0; l < size - 1; l++) {
                    //拿到之前的组，筛选出重复的内容进行替换
                    String lastGroup = ret.get(ret.size() - (l + 1));
                    if (!lastGroup.contains(e)
                        && !lastGroup.contains(String.valueOf(sb.charAt(0)))
                        && !lastGroup.contains(String.valueOf(sb.charAt(1)))
                        && !lastGroup.contains(String.valueOf(sb.charAt(2)))) {
                      String s = String.valueOf(lastGroup.charAt(0));
                      String replace = lastGroup.replace(s, e);
                      ret.set(ret.size() - (l + 1), replace);
                      intArray.set(0, s);
                    }
                  }
                }

              }

            }
          }
          ret.add(sb.toString());
        }


      }


    }
    return ret;
  }

  /**
   * 挨指报
   * 1-5为左手 6-0为右手  挨着的两码都为同一手，且一组四码里面左右手的码都有，一行四十个码，平均每个码都会出现四次，同一组码里面不会出现同一码
   * 例如：9624 1378 6035 2187 5407 6912 3160 9845 9843 2507
   *
   * @param count 生成数量
   * @return 挨指报
   */
  public static List<String> bePointed(int count) {
    List<String> min = new ArrayList<>();
    min.add("1");
    min.add("2");
    min.add("3");
    min.add("4");
    min.add("5");
    List<String> max = new ArrayList<>();
    max.add("6");
    max.add("7");
    max.add("8");
    max.add("9");
    max.add("0");
    List<String> left = new ArrayList<>();
    List<String> right = new ArrayList<>();
    List<String> pat = new ArrayList<>();
    for (int i = 0; i < count * 2 / 5; i++) {
      Collections.shuffle(min);
      left.addAll(min);
      Collections.shuffle(max);
      right.addAll(max);
    }
    Random rng = new Random();
    for (int j = 0; j < count * 2; j += 2) {
      String s = left.get(j);
      String s1 = left.get(j + 1);
      if (s.equals(s1)) {
        if (1 == Integer.parseInt(s1)) {
          s1 = String.valueOf(2);
        } else if (5 == Integer.parseInt(s1)) {
          s1 = String.valueOf(4);
        } else {
          s1 = String.valueOf(Integer.parseInt(s1) + 1);
        }
      }
      String s2 = right.get(j);
      String s3 = right.get(j + 1);
      if (s2.equals(s3)) {
        if (6 == Integer.parseInt(s3)) {
          s3 = String.valueOf(7);
        } else if (0 == Integer.parseInt(s3)) {
          s3 = String.valueOf(9);
        } else {
          s3 = String.valueOf(Integer.parseInt(s3) + 1);
        }
      }
      if (rng.nextBoolean()) {
        pat.add(s2 + s3 + s + s1);
      } else {
        pat.add(s + s1 + s2 + s3);
      }

    }
    return pat;
  }

  public static void check(List<String> data) {
    StringBuilder sb = new StringBuilder();
    List<String> strings = new ArrayList<>();
    for (int i = 0; i < data.size(); i++) {
      String s = data.get(i);
      strings.add(s);
      if (strings.size() % 10 == 0 || i == data.size() - 1) {
        strings.forEach(sb::append);
        String s1 = sb.toString();
        Map<String, Integer> count = new HashMap<>();
        for (int j = 0; j < s1.length(); j++) {
          String e = String.valueOf(s1.charAt(j));
          count.merge(e, 1, Integer::sum);
          if (Optional.ofNullable(count.get(e)).orElse(0).compareTo(4) > 0) {
            System.out.println(strings);
          }

        }
        //System.out.println(count);
        sb = new StringBuilder();
        strings.clear();
      }
    }
  }


  /**
   * 整页去重
   *
   * @param dataList xx
   * @param group    xx
   */
  private static PostTelegramGenerateCheck pageCheckDuplicate(List<String> dataList, String group) {
    PostTelegramGenerateCheck ret = new PostTelegramGenerateCheck();
    for (int i = 0; i < dataList.size(); i++) {
      String pageGroup = dataList.get(i);
      //如果碰到相同的组，则本组需要进行顺序交换
      if (Objects.equals(pageGroup, group)) {
        //1.字符串反转
        String reverseStr = new StringBuilder(group).reverse().toString();
        Boolean reverseBoolean = retCheck(dataList, new StringBuilder(group).reverse().toString());
        if (!reverseBoolean) {
          ret.setSign(true);
          ret.setGroup(reverseStr);
          return ret;
        }
        //2.前2个和后2个进行交换
        String newGroup = frontBackExchange(group);
        Boolean fbExchange = retCheck(dataList, newGroup);
        if (!fbExchange) {
          ret.setSign(true);
          ret.setGroup(newGroup);
          return ret;
        }
        //3.第一个和第三个进行交换
        String newsGroup = singularityExchange(group);
        Boolean sExchange = retCheck(dataList, newsGroup);
        if (!sExchange) {
          ret.setSign(true);
          ret.setGroup(newsGroup);
          return ret;
        }
        //4.第二个和第四个进行交换
        String azoExchangeGroup = azoExchange(group);
        Boolean retCheck = retCheck(dataList, azoExchangeGroup);
        if (!retCheck) {
          ret.setSign(true);
          ret.setGroup(azoExchangeGroup);
          return ret;
        }

        //再次调用排序
        ret.setSign(false);
        ret.setGroup(group);
        return ret;
      }
    }
    ret.setSign(true);
    ret.setGroup(group);
    return ret;
  }

  /**
   * 偶数交换
   *
   * @param group
   * @return
   */
  private static String azoExchange(String group) {
    StringBuilder sb = new StringBuilder(group);
    sb.setCharAt(1, group.charAt(3));
    sb.setCharAt(3, group.charAt(1));
    return sb.toString();
  }


  /**
   * 第一个和第三个进行交换
   *
   * @param group
   * @return
   */
  private static String singularityExchange(String group) {
    StringBuilder sb = new StringBuilder(group);
    char temp1 = group.charAt(0);
    char temp2 = group.charAt(2);
    sb.setCharAt(0, temp2);
    sb.setCharAt(2, temp1);
    return sb.toString();
  }

  /**
   * 前后交换
   *
   * @param group
   * @return
   */
  private static String frontBackExchange(String group) {
    StringBuilder ret = new StringBuilder();
    ret.append(group.substring(2));
    ret.append(group, 0, 2);
    return ret.toString();
  }


  /**
   * 重复检查
   *
   * @param dataList
   * @param group
   * @return
   */
  private static Boolean retCheck(List<String> dataList, String group) {
    for (int i = 0; i < dataList.size(); i++) {
      String pageGroup = dataList.get(i);
      if (Objects.equals(pageGroup, group)) {
        return true;
      }
    }
    return false;
  }


}
