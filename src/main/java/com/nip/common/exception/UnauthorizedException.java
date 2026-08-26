package com.nip.common.exception;

/**
 * token 无效或已过期（getUserByToken 查无用户）。
 * 由 {@link UnauthorizedExceptionMapper} 映射为 HTTP 200 + code 203 信封（与 JWTInterceptor 现行契约一致）。
 */
public class UnauthorizedException extends RuntimeException {
  public UnauthorizedException(String message) {
    super(message);
  }
}
