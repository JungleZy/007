package com.nip.common.exception;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.ResponseResult;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 校验类异常保持 HTTP 200 + 业务码 CODE_500 + 原提示消息（契约约束，Phase 4 收窄拦截器后恢复精确业务码）。
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
  @Override
  public Response toResponse(IllegalArgumentException e) {
    return Response.ok(
            ResponseResult.error(ResponseCode.CODE_500, e.getMessage(), e.getMessage()))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }
}
