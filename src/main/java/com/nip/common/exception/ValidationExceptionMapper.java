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
 * 校验类异常保持 HTTP 200 + 业务码 500（SYSTEM_ERROR）+ 业务提示消息。
 * 提示消息经 {@link #safeMessage(String)} 收口：只保留首行业务文案，剥掉异常类名与堆栈片段，
 * 并对长度设上限；原始消息与堆栈只进日志（log.warn），不再原文回显给客户端。
 */
@Provider
@Slf4j
public class ValidationExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
  /** 消息为空或只剩类名时的兜底文案 */
  static final String FALLBACK_MESSAGE = "请求参数不合法";
  /** 回显消息长度上限，防止把无界入参原样反射回客户端 */
  static final int MAX_MESSAGE_LENGTH = 200;
  /** 形如 java.lang.IllegalArgumentException: 的前缀（含嵌套包装时的多层前缀） */
  private static final Pattern EXCEPTION_PREFIX =
      Pattern.compile("^(?:[\\w$]+\\.)+[\\w$]*(?:Exception|Error|Throwable):\\s*");

  @Override
  public Response toResponse(IllegalArgumentException e) {
    log.warn("请求参数校验失败: {}", e.getMessage(), e);
    String message = safeMessage(e.getMessage());
    return Response.ok(
            ResponseResult.error(ResponseCode.SYSTEM_ERROR, message, message))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }

  /**
   * 把异常消息收口成可对外展示的单行业务文案。同包的 {@link IllegalStateExceptionMapper}、
   * {@link InvalidTitleExceptionMapper} 共用，避免出现第二套口径。
   */
  static String safeMessage(String raw) {
    if (raw == null || raw.isBlank()) {
      return FALLBACK_MESSAGE;
    }
    // 只取首行：多行消息的后续行通常是包装进来的堆栈或 caused-by 片段
    int lineEnd = raw.length();
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '\n' || c == '\r') {
        lineEnd = i;
        break;
      }
    }
    String message = raw.substring(0, lineEnd).trim();
    // 剥掉逐层包装留下的异常类名前缀
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
