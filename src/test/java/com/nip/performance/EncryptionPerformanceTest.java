package com.nip.performance;

import com.nip.common.utils.MD5Util;
import com.nip.common.utils.PasswordUtil;
import com.nip.common.utils.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("加密工具性能测试")
class EncryptionPerformanceTest {

  private static final int WARMUP_ITERATIONS = 1000;
  private static final int TEST_ITERATIONS = 10000;

  @Test
  @DisplayName("测试MD5加密性能")
  void testMD5Performance() {
    String input = "testPassword123";

    warmup(() -> MD5Util.encrypt(input));

    long startTime = System.currentTimeMillis();
    for (int i = 0; i < TEST_ITERATIONS; i++) {
      MD5Util.encrypt(input);
    }
    long endTime = System.currentTimeMillis();

    long duration = endTime - startTime;
    double avgTime = (double) duration / TEST_ITERATIONS;

    System.out.println("MD5加密性能测试:");
    System.out.println("总耗时: " + duration + " ms");
    System.out.println("平均耗时: " + avgTime + " ms");
    System.out.println("每秒处理次数: " + (TEST_ITERATIONS * 1000 / duration));

    assertTrue(duration < 5000, "MD5加密性能测试失败: 耗时过长");
  }

  @Test
  @DisplayName("测试AES加密性能")
  void testAESPerformance() {
    String input = "testPassword123";
    String phone = "18623090141";

    warmup(() -> PasswordUtil.encryptPassword(phone, input));

    long startTime = System.currentTimeMillis();
    for (int i = 0; i < TEST_ITERATIONS; i++) {
      PasswordUtil.encryptPassword(phone, input);
    }
    long endTime = System.currentTimeMillis();

    long duration = endTime - startTime;
    double avgTime = (double) duration / TEST_ITERATIONS;

    System.out.println("AES加密性能测试:");
    System.out.println("总耗时: " + duration + " ms");
    System.out.println("平均耗时: " + avgTime + " ms");
    System.out.println("每秒处理次数: " + (TEST_ITERATIONS * 1000 / duration));

    assertTrue(duration < 10000, "AES加密性能测试失败: 耗时过长");
  }

  @Test
  @DisplayName("测试字符串工具性能")
  void testStringUtilsPerformance() {
    String input = "  test string  ";

    warmup(() -> StringUtils.hasText(input));

    long startTime = System.currentTimeMillis();
    for (int i = 0; i < TEST_ITERATIONS * 10; i++) {
      StringUtils.isEmpty(input);
      StringUtils.hasText(input);
      StringUtils.hasLength(input);
    }
    long endTime = System.currentTimeMillis();

    long duration = endTime - startTime;
    double avgTime = (double) duration / (TEST_ITERATIONS * 10);

    System.out.println("字符串工具性能测试:");
    System.out.println("总耗时: " + duration + " ms");
    System.out.println("平均耗时: " + avgTime + " ms");
    System.out.println("每秒处理次数: " + (TEST_ITERATIONS * 10 * 1000 / duration));

    assertTrue(duration < 1000, "字符串工具性能测试失败: 耗时过长");
  }

  private void warmup(Runnable runnable) {
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      runnable.run();
    }
  }
}
