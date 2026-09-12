package com.nip.common.exception;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.ResponseResult;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * 契约兼容：HTTP 200 + code 208（业务终态，不可重试）。
 *
 * <p>业务文案经 {@link ValidationExceptionMapper#safeMessage(String)} 收口后原样回传，
 * 前端凭 208 这个码判定「不可重试」，凭 message 告诉用户为什么。
 */
@Provider
@Slf4j
public class TerminalStateExceptionMapper implements ExceptionMapper<TerminalStateException> {
  @Override
  public Response toResponse(TerminalStateException e) {
    log.warn("业务终态拒绝: {}", e.getMessage());
    String message = ValidationExceptionMapper.safeMessage(e.getMessage());
    return Response.ok(ResponseResult.error(ResponseCode.CODE_208, message, message))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }
}
