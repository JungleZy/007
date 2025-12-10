package com.nip.common.utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ArrayUtils {
  // 缓存类字段映射关系（提升性能）
  private static final Map<ClassPair, Map<String, FieldPair>> fieldCache = new ConcurrentHashMap<>();

  /**
   * 转换List中的对象类型（自动拷贝相同字段）
   *
   * @param sourceList  源数据列表
   * @param targetClass 目标类型Class对象
   * @return 转换后的新列表
   */
  public static <S, T> List<T> convertList(List<S> sourceList, Class<T> targetClass) {
    if (sourceList == null) return null;
    if (sourceList.isEmpty()) return new ArrayList<>();

    // 获取字段映射缓存
    Class<S> sourceClass = (Class<S>) sourceList.get(0).getClass();
    Map<String, FieldPair> fieldMap = getFieldMap(new ClassPair(sourceClass, targetClass));

    List<T> result = new ArrayList<>(sourceList.size());
    for (S source : sourceList) {
      try {
        T target = targetClass.getDeclaredConstructor().newInstance();
        copyFields(source, target, fieldMap);
        result.add(target);
      } catch (Exception e) {
        throw new RuntimeException("Object conversion failed", e);
      }
    }
    return result;
  }

  // 获取类字段映射（带缓存）
  private static Map<String, FieldPair> getFieldMap(ClassPair classPair) {
    return fieldCache.computeIfAbsent(classPair, cp -> {
      Map<String, FieldPair> map = new ConcurrentHashMap<>();

      // 获取源类所有字段（包括父类）
      List<Field> sourceFields = getAllFields(cp.sourceClass());
      // 获取目标类所有字段（包括父类）
      List<Field> targetFields = getAllFields(cp.targetClass());

      // 构建字段映射
      for (Field targetField : targetFields) {
        for (Field sourceField : sourceFields) {
          if (isCompatibleField(sourceField, targetField)) {
            sourceField.setAccessible(true);
            targetField.setAccessible(true);
            map.put(targetField.getName(), new FieldPair(sourceField, targetField));
            break;
          }
        }
      }
      return map;
    });
  }

  // 递归获取类及其父类的所有字段
  private static List<Field> getAllFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    while (clazz != null) {
      Collections.addAll(fields, clazz.getDeclaredFields());
      clazz = clazz.getSuperclass();
    }
    return fields;
  }

  // 判断字段是否兼容（名称相同且类型兼容）
  private static boolean isCompatibleField(Field source, Field target) {
    if (!source.getName().equals(target.getName())) return false;

    Class<?> sourceType = source.getType();
    Class<?> targetType = target.getType();

    // 处理基本类型和包装类型的兼容
    return sourceType == targetType ||
        (sourceType.isPrimitive() && getWrapperType(sourceType) == targetType) ||
        (targetType.isPrimitive() && sourceType == getWrapperType(targetType));
  }

  // 获取基本类型的包装类型
  private static Class<?> getWrapperType(Class<?> primitiveType) {
    if (primitiveType == int.class) return Integer.class;
    if (primitiveType == long.class) return Long.class;
    if (primitiveType == double.class) return Double.class;
    if (primitiveType == float.class) return Float.class;
    if (primitiveType == boolean.class) return Boolean.class;
    if (primitiveType == byte.class) return Byte.class;
    if (primitiveType == char.class) return Character.class;
    if (primitiveType == short.class) return Short.class;
    return primitiveType;
  }

  // 执行字段拷贝
  private static <S, T> void copyFields(S source, T target, Map<String, FieldPair> fieldMap) {
    try {
      for (FieldPair pair : fieldMap.values()) {
        Object value = pair.source().get(source);
        pair.target().set(target, value);
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Field copy failed", e);
    }
  }

  // 辅助记录类（存储类对）
  private record ClassPair(Class<?> sourceClass, Class<?> targetClass) {
  }

  // 辅助记录类（存储字段对）
  private record FieldPair(Field source, Field target) {
  }
}
