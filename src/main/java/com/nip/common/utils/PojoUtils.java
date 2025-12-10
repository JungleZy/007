package com.nip.common.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import com.nip.common.Page;
import com.nip.common.PageInfo;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * @author wushilin
 */
@Slf4j
public class PojoUtils {
  private PojoUtils() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * @param vs    被复制的对象
   * @param clazz 要复制成的类型
   * @param <T>   复制成的对象的类型
   * @param <V>   被复制的对象的类型
   * @return 包含了复制的结果的list
   */
  public static <T, V> List<T> convert(List<V> vs, Class<T> clazz, String... ignoreProperties) {
    if (CollUtil.isNotEmpty(vs)) {
      return new ArrayList<>(vs.stream()
          .map(v -> convertOne(v, clazz, ignoreProperties))
          .toList());
    } else {
      return new ArrayList<>();
    }
  }

  public static <T, V> List<T> convert(List<V> vs, Class<T> clazz, BiConsumer<V, T> func, String... ignoreProperties) {
    if (CollUtil.isNotEmpty(vs)) {
      return new ArrayList<>(vs.stream()
          .map(v -> convertOne(v, clazz, func, ignoreProperties))
          .toList());
    } else {
      return new ArrayList<>();
    }
  }

  /**
   * 复制对象的属性
   *
   * @param v     被复制的对象
   * @param clazz 要将该对象复制成什么类型
   * @param <T>   复制成的对象的类型
   * @param <V>   被复制的对象的类型
   * @return 一个新的 T 类型的对象，这个对象和 v 有相同的属性值
   */
  public static <T, V> T convertOne(V v, Class<T> clazz, BiConsumer<V, T> func, String... ignoreProperties) {
    try {

      T t = clazz.getDeclaredConstructor().newInstance();
      BeanUtil.copyProperties(v, t, CopyOptions.create().setIgnoreError(true));
      func.accept(v, t);
      return t;
    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      log.error("convertOne error:{}", e.getMessage());
      throw new NullPointerException();
    }
  }

  public static <T, V> T convertOne(V v, Class<T> clazz, String... ignoreProperties) {
    try {
      T entity = clazz.getDeclaredConstructor().newInstance();
      BeanUtil.copyProperties(v, entity, CopyOptions.create().setIgnoreError(true));
      return entity;
    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      log.error("convertOne error:{}", e.getMessage());
      throw new NullPointerException();
    }
  }

  public static <T, V> PageInfo<T> convertPage(PanacheQuery<V> page, Class<T> clazz, BiConsumer<V, T> func, String... ignoreProperties) {
    List<V> content = page.list();
    List<T> convert = convert(content, clazz, func, ignoreProperties);
    PageInfo<T> ret = new PageInfo<>();
    ret.setData(convert);
    ret.setPageSize(page.page().size);
    ret.setTotalNumber(page.count());
    ret.setCurrentPage(page.page().index);
    ret.setTotalPage(page.pageCount());
    return ret;
  }

  public static <T, V> PageInfo<T> convertPage(PanacheQuery<V> page, Class<T> clazz, String... ignoreProperties) {
    List<V> content = page.list();
    List<T> convert = convert(content, clazz, ignoreProperties);
    PageInfo<T> ret = new PageInfo<>();
    ret.setData(convert);
    ret.setPageSize(page.page().size);
    ret.setTotalNumber(page.count());
    ret.setCurrentPage(page.page().index);
    ret.setTotalPage(page.pageCount());
    return ret;
  }

  public static <T, V> PageInfo<T> convertPage(Page<V> page, Class<T> clazz, String... ignoreProperties) {
    List<V> content = page.getContent();
    List<T> convert = convert(content, clazz, ignoreProperties);
    PageInfo<T> ret = new PageInfo<>();
    ret.setData(convert);
    ret.setCurrentPage(page.getNumber() + 1);
    ret.setPageSize(page.getSize());
    ret.setTotalPage(page.getTotalPages());
    ret.setTotalNumber(page.getTotalElements());
    return ret;
  }

  public static <T> List<List<T>> averageAssign(List<T> source, int splitItemNnm) {
    List<List<T>> result = new ArrayList<>();
    if (source == null || source.isEmpty() || splitItemNnm <= 0) {
      return result;
    }

    int totalSize = source.size();
    int splitNum = calculateSplitNum(totalSize, splitItemNnm);

    for (int i = 0; i < splitNum; i++) {
      int start = i * splitItemNnm;
      int end = Math.min(start + splitItemNnm, totalSize);
      result.add(source.subList(start, end));
    }

    return result;
  }

  private static int calculateSplitNum(int totalSize, int splitItemNnm) {
    return totalSize % splitItemNnm == 0 ? totalSize / splitItemNnm : totalSize / splitItemNnm + 1;
  }

  public static <T> void merge(T user1, T user2) {
    try {
      BeanUtils.copyProperties(user1, user2);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to merge users", e);
    }
  }
}
