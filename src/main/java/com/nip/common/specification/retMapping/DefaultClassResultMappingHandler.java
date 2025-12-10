package com.nip.common.specification.retMapping;

import com.nip.common.specification.SpecificationExecutor;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 使用 Introspector和PropertyDescriptor
 * 两个工具调用返回对象的 set方法给属性赋值
 * 目前只要是字段名称相同即可赋值
 *
 * @author ：wsl
 * @date ：2023/9/16 10:22
 * @description： 默认对象返回
 */
public class DefaultClassResultMappingHandler implements ResultMappingHandler {

  /**
   * non javadoc
   * @see ResultMappingHandler#handler(ResultSet, List, Class, Object)
   */
  @Override
  public Object handler(ResultSet executeQuery, List<SpecificationExecutor.ResultType> columNames, Class<?> retClass, Object retObj) throws IntrospectionException, SQLException, InvocationTargetException, IllegalAccessException {
    BeanInfo beanInfo = Introspector.getBeanInfo(retClass);
    // 字段名称分组
    Map<String, List<PropertyDescriptor>> propertyDescriptorMap = Arrays.stream(beanInfo.getPropertyDescriptors())
        .collect(Collectors.groupingBy(PropertyDescriptor::getName));
    while (executeQuery.next()) {
      for (SpecificationExecutor.ResultType resultType : columNames) {
        List<PropertyDescriptor> descriptorList = propertyDescriptorMap.get(resultType.getColumNameHump());
        if (descriptorList != null) {
          PropertyDescriptor descriptor = descriptorList.getFirst();
          Method method = descriptor.getWriteMethod();
          Object object = executeQuery.getObject(resultType.getColumName());
          //赋值
          method.invoke(retObj, object);
        }
      }
    }
    return retObj;
  }

  /**
   * non javadoc
   * @see ResultMappingHandler#getHandlerType(Object)
   */
  @Override
  public boolean getHandlerType(Object ret) {
    return true;
  }


}
