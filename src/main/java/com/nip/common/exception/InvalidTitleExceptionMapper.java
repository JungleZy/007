package com.nip.common.exception;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.ResponseResult;
import com.nip.service.TheoryKnowledgeService;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 理论知识标题校验异常：与 {@link ValidationExceptionMapper} 同构，HTTP 200 + CODE_500 + 原提示消息。
 */
@Provider
public class InvalidTitleExceptionMapper
    implements ExceptionMapper<TheoryKnowledgeService.InvalidTitleException> {
  @Override
  public Response toResponse(TheoryKnowledgeService.InvalidTitleException e) {
    return Response.ok(
            ResponseResult.error(ResponseCode.CODE_500, e.getMessage(), e.getMessage()))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }
}
