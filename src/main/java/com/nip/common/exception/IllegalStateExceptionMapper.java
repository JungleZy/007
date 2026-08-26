package com.nip.common.exception;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.ResponseResult;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 状态校验类异常（Assert.state / 业务前置校验）：与 {@link ValidationExceptionMapper} 同构，
 * HTTP 200 + CODE_500 + 原提示消息。
 */
@Provider
public class IllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {
  @Override
  public Response toResponse(IllegalStateException e) {
    return Response.ok(
            ResponseResult.error(ResponseCode.CODE_500, e.getMessage(), e.getMessage()))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }
}
