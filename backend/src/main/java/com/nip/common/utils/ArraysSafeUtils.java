package com.nip.common.utils;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @Author: wushilin
 * @Data: 2024-06-05 10:58
 * @Description:
 */
public class ArraysSafeUtils {

  /**
   * 从集合中获取元素，如果下表不存在返回默认值
   * @param list 集合
   * @param index 索引
   * @param defaultElement 默认值
   * @param <T> 类型
   * @return 从集合中获取元素，如果下表不存在返回默认值
   */
  public static <T> T getElement(List<T> list,int index,T defaultElement){
    if (list==null || list.isEmpty()){
      return defaultElement;
    }
    if (index<list.size()){
      return list.get(index);
    }
    return defaultElement;
  }


  /**
   * 从集合中获取元素，如果下表不存在返回默认值
   * @param list 集合
   * @param index 索引
   * @param defaultSupplier 默认值
   * @param function 转换
   * @param <T> 集合类型
   * @param <R> 返回值类型
   * @return 从集合中获取元素，如果下表不存在返回默认值
   */
  public static <T,R> R getElement(List<T> list, int index, Supplier<R> defaultSupplier, Function<T,R> function){
    if (list==null || list.isEmpty()){
      return defaultSupplier.get();
    }
    if (index<list.size()){
      return function.apply(list.get(index));
    }
    return defaultSupplier.get();
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
