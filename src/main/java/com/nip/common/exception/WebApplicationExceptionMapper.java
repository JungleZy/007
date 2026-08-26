package com.nip.common.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * WebApplicationException（404/405/415 等 JAX-RS 语义异常）直通：
 * 按异常自带的 Response 原样返回，不落入 GlobalExceptionMapper 被兜成 500/SYSTEM_ERROR。
 */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {
  @Override
  public Response toResponse(WebApplicationException e) {
    return e.getResponse();
  }
}
