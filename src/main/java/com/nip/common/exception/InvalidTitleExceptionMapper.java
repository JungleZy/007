package com.nip.common.exception;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.ResponseResult;
import com.nip.service.TheoryKnowledgeService;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * 理论知识标题校验异常：与 {@link ValidationExceptionMapper} 同构，
 * HTTP 200 + 业务码 500（SYSTEM_ERROR）+ 经 safeMessage 收口的业务提示消息。
 */
@Provider
@Slf4j
public class InvalidTitleExceptionMapper
    implements ExceptionMapper<TheoryKnowledgeService.InvalidTitleException> {
  @Override
  public Response toResponse(TheoryKnowledgeService.InvalidTitleException e) {
    log.warn("理论知识标题校验失败: {}", e.getMessage(), e);
    String message = ValidationExceptionMapper.safeMessage(e.getMessage());
    return Response.ok(
            ResponseResult.error(ResponseCode.SYSTEM_ERROR, message, message))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }
}
