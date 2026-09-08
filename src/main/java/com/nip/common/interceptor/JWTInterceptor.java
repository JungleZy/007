package com.nip.common.interceptor;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.ResponseResult;
import com.nip.dao.UserDao;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import static com.nip.common.constants.BaseConstants.*;

/**
 * JWTInterceptor
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-21 8:34
 */
@JWT
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE)
@Slf4j
public class JWTInterceptor {
  @Context
  HttpServerRequest request;
  @Context
  HttpServerResponse response;
  @Inject
  UserDao userDao;

  @AroundInvoke
  Object execute(InvocationContext context) throws Exception {
    // 拦截器只兜自身 token 解析/校验逻辑的异常；context.proceed() 在 try 之外，
    // 端点业务异常直达 common/exception 下的 ExceptionMapper（Phase 4 Task 4.3）
    try {
      // CORS 响应头一律由 quarkus.http.cors 配置承担（application.yml），此处不再手写：
      // 手写版缺 GET/PATCH 且与配置双写，已删除（Phase 7 Task 7.2）
      if (request.method().name().equals("OPTIONS")) {
        // 预检请求不做 token 校验：CORS 过滤器已应答，此处必须直接返回而不是继续往下校验
        response.setStatusCode(200);
        return null;
      }
      String token = request.getHeader(TOKEN);
      String deviceId = request.getHeader(DEVICE_ID);

      if (StringUtils.isEmpty(token)) {
        token = request.getParam(TOKEN);
      }
      if (StringUtils.isEmpty(token)) {
        throw rejected(ResponseCode.CODE_203);
      }
      if (StringUtils.isEmpty(deviceId)) {
        deviceId = request.getParam(DEVICE_ID);
      }
      if (StringUtils.isEmpty(deviceId)) {
        throw rejected(ResponseCode.CODE_204);
      }
      if (!userDao.existsUserByTokenAndDeviceId(token, deviceId)) {
        throw rejected(ResponseCode.CODE_206);
      }
    } catch (WebApplicationException rejected) {
      // 鉴权拒绝信封由 WebApplicationExceptionMapper 原样送达，不得被下面的兜底 catch 降级
      throw rejected;
    } catch (Exception exception) {
      log.error("jwt fail from {}.{}", context.getTarget().getClass().getSimpleName(), context.getMethod().getName(), exception);
      return ResponseResult.error(ResponseCode.SYSTEM_ERROR, exception.getMessage(), exception.getMessage());
    }
    return context.proceed();
  }

  /**
   * 鉴权拒绝：抛异常而不是直接往 HttpServerResponse 写字节 + return null（原实现的双写来源），
   * 由 {@code WebApplicationExceptionMapper} 原样返回 HTTP 200 + 既有业务码信封。
   * 抛异常的另一个必要性：@JWT 是类级注解，存在返回 void/非 Response 的端点，
   * 从拦截器 return 一个 Response 信封会在这些端点上类型不兼容。
   */
  private static WebApplicationException rejected(ResponseCode code) {
    return new WebApplicationException(
        jakarta.ws.rs.core.Response.ok(ResponseResult.error(code))
            .type(MediaType.APPLICATION_JSON)
            .build());
  }
}
