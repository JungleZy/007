package com.nip.common.specification;

import com.nip.common.PageInfo;
import com.nip.common.specification.exception.SpecificationExecutorException;
import jakarta.annotation.Nullable;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-09-13 14:51
 * @Description:
 */

@Data
@Slf4j
public class SpecificationExecutor<T> {

  public EntityManager entityManager;

  public DefaultEntityInformation<T> entityInformation;

  public SpecificationExecutor() {
    this.entityManager = CDI.current().select(EntityManager.class).get();
    Type genericSuperclass = this.getClass().getGenericSuperclass();
    while (genericSuperclass != null && !(genericSuperclass instanceof ParameterizedType)) {
      if (genericSuperclass instanceof Class) {
        genericSuperclass = ((Class<?>) genericSuperclass).getGenericSuperclass();
      } else {
        log.error("构建com.nip.server.common.SpecificationExecutor异常,建议使用DeBug检查{},是否是期望的类。", this.getClass().getName());
        throw new SpecificationExecutorException("com.nip.server.common.SpecificationExecutor 赋值DefaultEntityInformation<T>异常");
      }
    }
    if (genericSuperclass != null) {
      ParameterizedType type = (ParameterizedType) genericSuperclass;
      Class<T> aClass = (Class<T>) type.getActualTypeArguments()[0];
      this.entityInformation = new DefaultEntityInformation<>(aClass);
    }

  }


  /**
   * @param specification 条件构建器
   * @return 返回结果
   */
  public List<T> findAll(@Nullable Specification<T> specification) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<T> query = builder.createQuery(entityInformation.getJavaType());
    Root<T> root = query.from(entityInformation.getJavaType());
    return entityManager.createQuery(specification.toPredicate(root, query, builder)).getResultList();
  }


  /**
   * @param specification 条件构建器
   * @param currentPage   页码
   * @param pageSize      每页大小
   * @return 返回结果
   */
  public PageInfo<T> findPage(@Nullable Specification<T> specification, int currentPage, int pageSize) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<T> query = builder.createQuery(entityInformation.getJavaType());
    Root<T> root = query.from(entityInformation.getJavaType());
    List<T> resultList = entityManager.createQuery(specification.toPredicate(root, query, builder))
        .setFirstResult(currentPage * pageSize)
        .setMaxResults(pageSize)
        .getResultList();
    long total = count(specification, builder);
    PageInfo<T> ret = new PageInfo<>();
    ret.setData(resultList);
    ret.setPageSize(pageSize);
    ret.setTotalNumber(total);
    ret.setCurrentPage(currentPage + 1);
    ret.setTotalPage((int) ((total + pageSize - 1) / pageSize));
    return ret;
  }

  /**
   * 统计满足条件的总条数。
   * <p>
   * 必须另建一条 count 查询：{@link CriteriaQuery} 与 {@link Root} 都不能与数据查询复用，
   * 复用会让 select 列表被 count 覆盖、分页结果一起变形。条件构建器只用来复写 where；
   * 排序对 count 无意义且在 ONLY_FULL_GROUP_BY 下非法，故条件应用完毕后清空 orderBy。
   *
   * @param specification 条件构建器
   * @param builder       条件构建器工厂（与数据查询共用同一个）
   * @return 总条数
   */
  private long count(@Nullable Specification<T> specification, CriteriaBuilder builder) {
    CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
    Root<T> countRoot = countQuery.from(entityInformation.getJavaType());
    // Specification 的签名要求 CriteriaQuery<T>；这里只借它复写 where/orderBy，不触碰 select，故转型安全
    @SuppressWarnings("unchecked")
    CriteriaQuery<T> asEntityQuery = (CriteriaQuery<T>) (CriteriaQuery<?>) countQuery;
    specification.toPredicate(countRoot, asEntityQuery, builder);
    countQuery.select(builder.count(countRoot)).orderBy(List.of());
    return entityManager.createQuery(countQuery).getSingleResult();
  }
}
