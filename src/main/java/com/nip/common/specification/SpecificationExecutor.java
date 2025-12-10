package com.nip.common.specification;

import com.nip.common.PageInfo;
import com.nip.common.specification.exception.SpecificationExecutorException;
import com.nip.common.specification.retMapping.ResultMappingHandler;
import com.nip.common.specification.retMapping.ResultMappingHandlerFactory;
import jakarta.annotation.Nullable;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
   * @param pageSize      页码
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
    int total = entityManager.createQuery(specification.toPredicate(root, query, builder)).getResultList().size();
    PageInfo<T> ret = new PageInfo<>();
    ret.setData(resultList);
    ret.setPageSize(pageSize);
    ret.setTotalNumber(total);
    ret.setCurrentPage(currentPage + 1);
    ret.setTotalPage((total + pageSize - 1) / pageSize);
    return ret;
  }

  public <S> S nativeQuery(String sql, Class<S> retClass, Object... param) {
    //只能是查询sql
    if (!sql.startsWith("select") && !sql.startsWith("SELECT")) {
      throw new IllegalArgumentException("非查询sql");
    }
    AtomicReference<S> ret = new AtomicReference<>();
    try {
      SessionFactory sessionFactory = CDI.current().select(SessionFactory.class).get();
      sessionFactory
          .openSession()
          .doWork(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
              //参数赋值
              for (int i = 0; i < param.length; i++) {
                ps.setObject(i + 1, param[i]);
              }
              ResultSet executeQuery = ps.executeQuery();
              ResultSetMetaData resultSet = executeQuery.getMetaData();
              List<ResultType> columNames = new ArrayList<>();
              if (executeQuery.getRow() == 0) {
                return;
              }
              //实例化返回对象
              ret.set(retClass.getDeclaredConstructor().newInstance());
              //封装返回字段类型名字
              for (int i = 0; i < resultSet.getColumnCount(); i++) {
                String columnName = resultSet.getColumnName(i + 1);
                String columnNameHump = columnConvertHump(columnName);
                String columnClassName = resultSet.getColumnClassName(i + 1);
                ResultType resultType = new ResultType(columnName, columnNameHump, columnClassName);
                columNames.add(resultType);
              }
              List<ResultMappingHandler> handlers = ResultMappingHandlerFactory.getHandlers();
              ResultMappingHandler mappingHandler = handlers.stream()
                  .filter(handler -> handler.getHandlerType(ret.get()))
                  .findFirst()
                  .orElseThrow(() -> new RuntimeException("类型未指定"));
              mappingHandler.handler(executeQuery, columNames, retClass, ret.get());
            } catch (Exception e) {
              log.error("sql execute exception:{}", e.getMessage());
            }
          });

      return ret.get();
    } catch (Exception e) {
      log.error("sql execute exception:{}", e.getMessage());
    }
    throw new IllegalArgumentException("sql execute exception");

  }

  /**
   * 将字段转成驼峰
   *
   * @param colum 列名
   * @return 驼峰列名
   */
  public String columnConvertHump(String colum) {
    StringBuilder ret = new StringBuilder();
    char[] chars = colum.toCharArray();
    boolean b = false;
    for (char c : chars) {
      if (c == 95) {
        b = true;
      } else {
        if (b) {
          String upperCase = String.valueOf(c).toUpperCase();
          ret.append(upperCase);
          b = false;
        } else {
          ret.append(c);
        }
      }
    }
    return ret.toString();
  }


  @Data
  @AllArgsConstructor
  public static class ResultType {
    //sql原始字段
    String columName;
    //驼峰
    String columNameHump;
    //类型
    String columClassName;
  }

}

