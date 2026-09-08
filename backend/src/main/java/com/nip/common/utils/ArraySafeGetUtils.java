package com.nip.common.utils;

import java.util.List;
import java.util.function.Consumer;

/**
 * @Author: wushilin
 * @Data: 2024-05-20 10:21
 * @Description:
 */
public class ArraySafeGetUtils {

  /**
   * 从集合中获取一个元素，如果索引越界则返回指定的默认值
   * @param list 集合
   * @param index 获取索引
   * @param defaultValue 默认值
   * @param <T> 集合泛型
   * @return 索引下指定的元素/默认值
   */
  public static  <T> T get(List<T> list,int index,T defaultValue){
    if (list.size() > index){
      return list.get(index);
    }
    return defaultValue;
  }

  /**
   * 从集合中获取一个元素，如果索引未越界则进行消费
   * @param list 集合
   * @param index 索引
   * @param consumer 消费
   * @param <T> 元素类型
   */
  public static <T> void get(List<T> list, int index, Consumer<T> consumer){
    if (list.size()>index){
      consumer.accept(list.get(index));
    }
  }
}
