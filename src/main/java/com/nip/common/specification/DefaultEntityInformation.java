package com.nip.common.specification;

import cn.hutool.core.lang.Assert;

/**
 * @Author: wushilin
 * @Data: 2023-09-13 16:42
 * @Description: 实体类元数据
 */
public class DefaultEntityInformation<T> implements EntityInformation<T>{

  private final Class<T> domainType;

  public DefaultEntityInformation(Class<T> domainType) {
    Assert.notNull(domainType, "Domain type must not be null!");
    this.domainType = domainType;
  }

  @Override
  public Class<T> getJavaType() {
    return this.domainType;
  }
}
