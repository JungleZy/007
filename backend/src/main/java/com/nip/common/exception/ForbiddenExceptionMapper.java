package com.nip.common.exception;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.ResponseResult;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * 契约兼容：HTTP 200 + code 207（与 {@code RequireAdminInterceptor} 的授权拒绝形态一致）。
 *
 * <p>拒绝原因只进日志，不回传给客户端——避免把资源归属、创建者 id 等内部信息泄露给越权调用方。
 */
@Provider
@Slf4j
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {
  @Override
  public Response toResponse(ForbiddenException e) {
    log.warn("授权拒绝: {}", e.getMessage());
    return Response.ok(ResponseResult.error(ResponseCode.CODE_207))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }
}
