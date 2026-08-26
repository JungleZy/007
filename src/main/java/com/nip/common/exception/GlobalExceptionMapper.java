package com.nip.common.exception;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.ResponseResult;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 兜底异常映射：未被专用 Mapper 接管的异常 → HTTP 500 + SYSTEM_ERROR 信封，日志保留完整堆栈。
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionMapper.class);

  @Override
  public Response toResponse(Throwable e) {
    log.error("unhandled", e);
    return Response.serverError()
        .type(MediaType.APPLICATION_JSON)
        .entity(ResponseResult.error(ResponseCode.SYSTEM_ERROR))
        .build();
  }
}
