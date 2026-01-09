package com.nip.common.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateTimeUtil {
  // UTC+8 时区常量
  public static final ZoneId UTC_PLUS_8 = ZoneId.of("UTC+8");
  public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai"); // 同样表示东八区

  // 常用格式器
  public static final DateTimeFormatter DEFAULT_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  public static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");
  public static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm:ss");

  /**
   * 获取当前UTC+8时间字符串
   * 格式：2026-01-01 11:14:29
   */
  public static String now() {
    return LocalDateTime.now(UTC_PLUS_8).format(DEFAULT_FORMATTER);
  }

  /**
   * 获取当前UTC+8日期字符串
   * 格式：2026-01-01
   */
  public static String today() {
    return LocalDate.now(UTC_PLUS_8).format(DATE_FORMATTER);
  }

  /**
   * 获取当前UTC+8时间字符串
   * 格式：11:14:29
   */
  public static String currentTime() {
    return LocalTime.now(UTC_PLUS_8).format(TIME_FORMATTER);
  }

  /**
   * 获取当前UTC+8的LocalDateTime对象
   */
  public static LocalDateTime localNow() {
    return LocalDateTime.now(UTC_PLUS_8);
  }

  /**
   * 获取当前UTC+8的LocalDate对象
   */
  public static LocalDate localToday() {
    return LocalDate.now(UTC_PLUS_8);
  }

  /**
   * 将时间戳转换为UTC+8时间字符串
   * @param timestamp 毫秒时间戳
   */
  public static String fromTimestamp(long timestamp) {
    return Instant.ofEpochMilli(timestamp)
        .atZone(UTC_PLUS_8)
        .format(DEFAULT_FORMATTER);
  }

  /**
   * 将Date对象转换为UTC+8时间字符串
   */
  public static String fromDate(Date date) {
    return date.toInstant()
        .atZone(UTC_PLUS_8)
        .format(DEFAULT_FORMATTER);
  }

  /**
   * 获取当前UTC+8时间的时间戳（毫秒）
   */
  public static long currentTimeMillis() {
    return Instant.now().atZone(UTC_PLUS_8).toInstant().toEpochMilli();
  }
}
