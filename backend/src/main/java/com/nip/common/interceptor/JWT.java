package com.nip.common.interceptor;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * JWT
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-21 15:55
 */
@InterceptorBinding
@Target({TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface JWT {
}
