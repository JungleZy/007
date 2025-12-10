package com.nip.common.specification.retMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ：wsl
 * @date ：2023/9/16 10:57
 * @description： 映射工厂类
 */
public class ResultMappingHandlerFactory {

  @SuppressWarnings(value = "rawtypes")
  private static final List<ResultMappingHandler> HANDLERS = new ArrayList<>();


  /**
   * 获取所有映射处理器
   * 想通过接口的class对象自动获取接口的实现类，
   * 但是查阅资料后发现java的class对象，不能直接获取到实现类
   * 网上采用的方法都是扫描包获取类，来判断是否是此接口的实现类
   * 但是会有性能的影响，所以在手动创建实现类，放入到集合中
   *
   * @return 映射处理器
   */
  @SuppressWarnings(value = "rawtypes")
  public synchronized static List<ResultMappingHandler> getHandlers() {
    if (!HANDLERS.isEmpty()) {
      return HANDLERS;
    }
    HANDLERS.add(new DefaultClassResultMappingHandler());
    HANDLERS.add(new DefaultMapResultMappingHandler());
    return HANDLERS;
  }
}
