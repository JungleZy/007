package com.nip.common.utils;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 14:46
 * @Description: 校验工具
 */
public class CheckUtils {

  /**
   * 状态校验
   * @param expect 期望状态值
   * @param reality 实际状体值
   * @param exceptionMessage 抛出异常消息
   */
  public static void statusCheck(Integer expect,Integer reality,String exceptionMessage){
    if(expect.compareTo(reality) != 0){
      throw new IllegalArgumentException(exceptionMessage);
    }
  }
}
