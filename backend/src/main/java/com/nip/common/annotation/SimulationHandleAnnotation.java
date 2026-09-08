package com.nip.common.annotation;

import com.nip.common.constants.SimulationRoomTypeEnum;

import java.lang.annotation.*;

/**
 * @Author: wushilin
 * @Data: 2023-03-02 10:16
 * @Description:
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SimulationHandleAnnotation {
  String description() default "";

  /**
   * 谁的处理器
   * @return
   */
  SimulationRoomTypeEnum roomType() ;

}
