package com.nip.common.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

/**
 * @Author: wushilin
 * @Data: 2023-09-13 15:17
 * @Description:
 */
public interface Specification <T>{


  /**
   * 动态sql条件查询 用法与JPA相同
   * @param root root
   * @param criteriaQuery 查询条件
   * @param criteriaBuilder 条件构建器
   * @return 查询条件
   */
  CriteriaQuery<T> toPredicate(Root<T> root, CriteriaQuery<T> criteriaQuery, CriteriaBuilder criteriaBuilder);

}
