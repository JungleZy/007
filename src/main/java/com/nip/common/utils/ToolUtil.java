package com.nip.common.utils;

import jakarta.persistence.Id;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ToolUtil
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-21 18:35
 */
@Slf4j
public class ToolUtil {
  private static final BigDecimal SCALE = new BigDecimal(100);

  public static String handleIdCard(String idCard) {
    if (StringUtils.isNotEmpty(idCard)) {
      if (idCard.length() == 18) {
        String substring = idCard.substring(6, 14);
        return substring.substring(0, 4) + "-" + substring.substring(4, 6) + "-" + substring.substring(6);
      } else if (idCard.length() == 15) {
        String substring = idCard.substring(6, 12);
        return "19" + substring.substring(0, 2) + "-" + substring.substring(2, 4) + "-" + substring.substring(4);
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  public static Map<String, Object> assembleData(List<Object[]> resultList) {
    Map<String, Object> map = new HashMap<>();
    if (!resultList.isEmpty()) {
      map.put("totalTime", resultList.getFirst()[0]);
      map.put("totalCount", resultList.getFirst()[1]);
      map.put("avgSpeed", resultList.getFirst()[2]);
    }
    return map;
  }

  public static List<String> objToList(Object obj) {
    List<String> list = new ArrayList<>();
    if (obj instanceof ArrayList<?>) {
      for (Object o : (List<?>) obj) {
        list.add(String.valueOf(o));
      }
      return list;
    }
    return null;
  }

  /**
   * 检查对象的ID字段是否为空
   * 该方法通过反射遍历对象的类及其超类，寻找带有@Id注解的字段，并检查该字段的值是否为空
   *
   * @param object 要检查的对象
   * @return 如果ID字段为空或对象为null，则返回true；否则返回false
   */
  public static boolean isIdFieldEmpty(Object object) {
    if (object == null) {
      return true;
    }

    Class<?> clazz = object.getClass();
    while (clazz != null) {
      for (Field field : clazz.getDeclaredFields()) {
        if (field.isAnnotationPresent(Id.class)) {
          try {
            String getterName = "get" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
            Method getterMethod = clazz.getMethod(getterName);
            Object value = getterMethod.invoke(object);
            return value == null || value == "";
          } catch (Exception e) {
            log.error("Failed to access field", e);
            throw new IllegalArgumentException("Failed to access field", e);
          }
        }
      }
      clazz = clazz.getSuperclass();
    }
    return false;
  }

  public static BigDecimal calculateRate(int min, int max, int total) {
    return min == 0 ? BigDecimal.ZERO : new BigDecimal(max).divide(new BigDecimal(total), 10, RoundingMode.HALF_UP).multiply(SCALE).setScale(0, RoundingMode.HALF_UP);
  }

  public static int calculateScore(Integer max, int score, int exc) {
    if (max == 0) {
      return score;
    } else if (score > max) {
      return exc;
    } else {
      return score;
    }
  }

  public static int calculateTS(int total, int max, int min, int per) {
    return new BigDecimal(total)
        .divide(new BigDecimal(
            max +
                min +
                per
        ), 0, RoundingMode.HALF_UP).intValue();
  }
}
