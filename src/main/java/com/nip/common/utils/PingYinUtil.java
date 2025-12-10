package com.nip.common.utils;

import java.util.Random;

/**
 * 得到中文首字母
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2018-09-20 20:18:10
 */
public class PingYinUtil {

  public static void main(String[] args) {
    String str = "这是一个测试";
    System.out.println("中文首字母：" + getPYIndexStr(str, true));
  }

  /**
   * 返回首字母
   */
  public static String getPYIndexStr(String strChinese, boolean bUpCase) {
    try {
      StringBuilder buffer = new StringBuilder();
      // 把中文转化成byte数组
      byte[] b = strChinese.getBytes("GBK");
      for (int i = 0; i < b.length; i++) {
        if ((b[i] & 255) > 128) {
          int char1 = b[i++] & 255;
          // 左移运算符用“<<”表示，是将运算符左边的对象，向左移动运算符右边指定的位数，并且在低位补零。其实，向左移n位，就相当于乘上2的n次方
          char1 <<= 8;
          int chart = char1 + (b[i] & 255);
          buffer.append(getPYIndexChar((char) chart, bUpCase));
          continue;
        }
        char c = (char) b[i];
        // 确定指定字符是否可以是 Java
        if (!Character.isJavaIdentifierPart(c)) {
          // 标识符中首字符以外的部分。
          c = 'A';
        }
        buffer.append(c);
      }
      return buffer.toString();
    } catch (Exception e) {
      System.out.println("取中文拼音有错" + e.getMessage());
    }
    return null;
  }

  /**
   * 得到首字母
   */
  private static char getPYIndexChar(char strChinese, boolean bUpCase) {

    char result;

    if ((int) strChinese >= 45217 && (int) strChinese <= 45252) {
      result = 'A';
    } else if ((int) strChinese >= 45253 && (int) strChinese <= 45760) {
      result = 'B';
    } else if ((int) strChinese >= 45761 && (int) strChinese <= 46317) {
      result = 'C';
    } else if ((int) strChinese >= 46318 && (int) strChinese <= 46825) {
      result = 'D';
    } else if ((int) strChinese >= 46826 && (int) strChinese <= 47009) {
      result = 'E';
    } else if ((int) strChinese >= 47010 && (int) strChinese <= 47296) {
      result = 'F';
    } else if ((int) strChinese >= 47297 && (int) strChinese <= 47613) {
      result = 'G';
    } else if ((int) strChinese >= 47614 && (int) strChinese <= 48118) {
      result = 'H';
    } else if ((int) strChinese >= 48119 && (int) strChinese <= 49061) {
      result = 'J';
    } else if ((int) strChinese >= 49062 && (int) strChinese <= 49323) {
      result = 'K';
    } else if ((int) strChinese >= 49324 && (int) strChinese <= 49895) {
      result = 'L';
    } else if ((int) strChinese >= 49896 && (int) strChinese <= 50370) {
      result = 'M';
    } else if ((int) strChinese >= 50371 && (int) strChinese <= 50613) {
      result = 'N';
    } else if ((int) strChinese >= 50614 && (int) strChinese <= 50621) {
      result = 'O';
    } else if ((int) strChinese >= 50622 && (int) strChinese <= 50905) {
      result = 'P';
    } else if ((int) strChinese >= 50906 && (int) strChinese <= 51386) {
      result = 'Q';
    } else if ((int) strChinese >= 51387 && (int) strChinese <= 51445) {
      result = 'R';
    } else if ((int) strChinese >= 51446 && (int) strChinese <= 52217) {
      result = 'S';
    } else if ((int) strChinese >= 52218 && (int) strChinese <= 52697) {
      result = 'T';
    } else if ((int) strChinese >= 52698 && (int) strChinese <= 52979) {
      result = 'W';
    } else if ((int) strChinese >= 52980 && (int) strChinese <= 53688) {
      result = 'X';
    } else if ((int) strChinese >= 53689 && (int) strChinese <= 54480) {
      result = 'Y';
    } else if ((int) strChinese >= 54481 && (int) strChinese <= 55289) {
      result = 'Z';
    } else {
      result = (char) (65 + (new Random()).nextInt(25));
    }
    if (!bUpCase) {
      result = Character.toLowerCase(result);
    }
    return result;
  }
}