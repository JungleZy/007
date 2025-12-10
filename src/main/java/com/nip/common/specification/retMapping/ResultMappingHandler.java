package com.nip.common.specification.retMapping;

import com.nip.common.specification.SpecificationExecutor;

import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public interface ResultMappingHandler {

  /**
   * 映射处理器,将数据库查询字段结果映射到实体类中
   * @param executeQuery 执行返回结果
   * @param columNames 列名 类型 sql结果字段名
   * @param retClass 返回结果类型
   * @param retObj 返回结果实例
   * @return T
   */
  Object handler(ResultSet executeQuery, List<SpecificationExecutor.ResultType> columNames, Class<?> retClass, Object retObj) throws IntrospectionException, SQLException, InvocationTargetException, IllegalAccessException;



  /**
   * 返回各自处理器处理的数据类型
   * @param retObj 返回的class对象
   * @return 是否可以用此处理器处理
   */
  boolean getHandlerType(Object retObj);
}
