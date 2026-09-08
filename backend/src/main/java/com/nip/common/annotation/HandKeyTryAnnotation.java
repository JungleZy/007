package com.nip.common.annotation;

import java.lang.annotation.*;

/**
 * @Author: wushilin
 * @Data: 2023-04-03 15:28
 * @Description: 请求放行
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HandKeyTryAnnotation {
}
