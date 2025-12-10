package com.nip.common.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * 数字格式化
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2018-09-20 20:17:53
 */
public class NumUtil {

  /**
   * @Description 保留指定位数的小数(少的位数不补零)
   */
  public static String keepRandomPoint(Double value, int n) {
    if (value == null) {
      value = 0.00;
    }
    return new BigDecimal(value).setScale(n, RoundingMode.HALF_UP).toString();
  }

  /**
   * @Description 浮点保留两位小数(少的位数不补零)
   */
  public static String keep2Point(double value) {
    return keepRandomPoint(value, 2);
  }

  /**
   * @Description 浮点保留1位小数(少的位数不补零)
   */
  public static String keep1Point(double value) {
    return keepRandomPoint(value, 1);
  }

  /**
   * @Description 浮点保留任意位小数(少位补零)
   */
  public static String keepRandomPointZero(double value, int n) {
    DecimalFormat df = new DecimalFormat("#0.00");
    return df.format(Double.valueOf(keepRandomPoint(value, n)));
  }

  /**
   * @Description 浮点保留两位小数(少位补零)
   */
  public static String keep2PointZero(double value) {
    return keepRandomPointZero(value, 2);
  }

  /**
   * @Description 获取任意小数点位的百分比表示
   */
  public static String percentRandomPoint(double value, int n) {
    NumberFormat percent = NumberFormat.getPercentInstance();
    percent.setGroupingUsed(false);
    percent.setMaximumFractionDigits(n);
    return percent.format(value);
  }

  /**
   * @Description 百分比保留两位小数
   */
  public static String percent2Point(double value) {
    return percentRandomPoint(value, 2);
  }

  /**
   * @Description 获取格式化经纬度后的小数(保留3位)
   */
  public static String latLngPoint(double value) {
    return keepRandomPoint(value, 3);
  }

  /**
   * @Description 判断是不是数字
   */
  public static boolean isInteger(String str) {
    Pattern pattern = Pattern.compile("^[-\\+]?[\\d]*$");
    return pattern.matcher(str).matches();
  }

  /**
   * @Description 区间值
   */
  public static int betweenNum(int min, int max) {
    Random random = new Random();
    return random.nextInt(max) % (max - min + 1) + min;
  }

}
