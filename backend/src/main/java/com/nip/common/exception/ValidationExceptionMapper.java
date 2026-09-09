package com.nip.common.exception;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.ResponseResult;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 校验类异常保持 HTTP 200 + 业务码 202（PARAMS_ERROR）+ 业务提示消息。
 * 提示消息经 {@link #safeMessage(String)} 收口：只保留首行业务文案，剥掉异常类名与堆栈片段，并对长度设上限。
 */
@Provider
@Slf4j
public class ValidationExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
  static final String FALLBACK_MESSAGE = "请求参数不合法";
  static final int MAX_MESSAGE_LENGTH = 200;
  private static final Pattern EXCEPTION_PREFIX =
      Pattern.compile("^(?:[\\w$]+\\.)+[\\w$]*(?:Exception|Error|Throwable):\\s*");

  @Override
  public Response toResponse(IllegalArgumentException e) {
    log.warn("请求参数校验失败: {}", e.getMessage(), e);
    String message = safeMessage(e.getMessage());
    return Response.ok(
            ResponseResult.error(ResponseCode.PARAMS_ERROR, message, message))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }

  static String safeMessage(String raw) {
    if (raw == null || raw.isBlank()) {
      return FALLBACK_MESSAGE;
    }
    int lineEnd = raw.length();
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '\n' || c == '\r') {
        lineEnd = i;
        break;
      }
    }
    String message = raw.substring(0, lineEnd).trim();
    Matcher matcher = EXCEPTION_PREFIX.matcher(message);
    while (matcher.lookingAt()) {
      message = message.substring(matcher.end()).trim();
      matcher = EXCEPTION_PREFIX.matcher(message);
    }
    if (message.isEmpty()) {
      return FALLBACK_MESSAGE;
    }
    return message.length() > MAX_MESSAGE_LENGTH
        ? message.substring(0, MAX_MESSAGE_LENGTH) + "…"
        : message;
  }
}
