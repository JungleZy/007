package com.nip.common.specification;

/**
 * @Author: wushilin
 * @Data: 2023-09-13 16:40
 * @Description: 实体类元数据
 */
public interface EntityInformation<T> {
  /**
   * 实体类 class
   *
   * @return
   */
  Class<T> getJavaType();
}
