package com.nip.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import jakarta.ws.rs.core.Context;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

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
  Object execute(InvocationContext context) {
    try {
      Map<String, Object> mp = new HashMap<>();
      response.putHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));//HTTP 请求头获取源IP或域名，并配置到跨域源中
      response.putHeader("Access-Control-Allow-Methods", "POST,OPTIONS,PUT,HEAD,DELETE");
      response.putHeader("Access-Control-Max-Age", "3600000");
      response.putHeader("Access-Control-Allow-Credentials", "true");
      response.putHeader("Access-Control-Allow-Headers", "Authentication,Origin, X-Requested-With, Content-Type, Accept,token,deviceId");
      if (request.method().name().equals("OPTIONS")) {
        response.setStatusCode(200);
      }
      String token = request.getHeader(TOKEN);
      String deviceId = request.getHeader(DEVICE_ID);

      if (StringUtils.isEmpty(token)) {
        token = request.getParam(TOKEN);
      }
      if (StringUtils.isEmpty(token)) {
        mp.put(CODE, ResponseCode.CODE_203.getCode());
        mp.put(MESSAGE, ResponseCode.CODE_203.getMessage());
        response.send(new ObjectMapper().writeValueAsString(mp));
        return null;
      }
      if (StringUtils.isEmpty(deviceId)) {
        deviceId = request.getParam(DEVICE_ID);
      }
      if (StringUtils.isEmpty(deviceId)) {
        mp.put(CODE, ResponseCode.CODE_204.getCode());
        mp.put(MESSAGE, ResponseCode.CODE_204.getMessage());
        response.send(new ObjectMapper().writeValueAsString(mp));
        return null;
      }
      if (userDao.existsUserByTokenAndDeviceId(token, deviceId)) {
        return context.proceed();
      }
      mp.put(CODE, ResponseCode.CODE_206.getCode());
      mp.put(MESSAGE, ResponseCode.CODE_206.getMessage());
      response.send(new ObjectMapper().writeValueAsString(mp));
      return null;
    } catch (Exception exception) {
      log.error("method error from {}.{}\n", context.getTarget().getClass().getSimpleName(), context.getMethod().getName());
      return ResponseResult.error(ResponseCode.SYSTEM_ERROR,exception.getMessage(),exception.getMessage());
    }
  }
}
