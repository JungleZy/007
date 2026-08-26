package com.nip.common.exception;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.ResponseResult;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 契约兼容：HTTP 200 + code 203（与 JWTInterceptor 对空 token 的现行为一致）。
 */
@Provider
public class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {
  @Override
  public Response toResponse(UnauthorizedException e) {
    return Response.ok(
            ResponseResult.error(ResponseCode.CODE_203))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }
}
