package com.nip.common.specification.retMapping;

import com.nip.common.specification.SpecificationExecutor;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

public class DefaultMapResultMappingHandler implements ResultMappingHandler {


  /**
   * non javadoc
   * @see ResultMappingHandler#handler(ResultSet, List, Class, Object)
   */
  @Override
  public Object handler(ResultSet executeQuery, List<SpecificationExecutor.ResultType> columNames, Class<?> retClass, Object retObj) {
    //todo...
    return null;
  }

  /**
   * non javadoc
   * @see ResultMappingHandler#getHandlerType(Object) ()
   */
  @Override
  public boolean getHandlerType(Object retObj) {

    return retObj instanceof Map;
  }
}
