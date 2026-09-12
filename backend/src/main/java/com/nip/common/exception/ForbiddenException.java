package com.nip.common.exception;

/**
 * 服务端授权拒绝：调用者身份有效（token 有效），但对目标资源没有操作权限。
 *
 * <p>由 {@link ForbiddenExceptionMapper} 映射为 HTTP 200 + {@code code:207} 信封，
 * 与 {@code RequireAdminInterceptor} 的拒绝形态一致，全仓授权拒绝只有这一套码。
 *
 * <p>与其它两种拒绝的分工（不要混用）：
 * <ul>
 *   <li>{@link UnauthorizedException} → 203：token 无效或已过期，调用者身份都没建立；</li>
 *   <li>{@code IllegalArgumentException} → 202：参数不合法或目标不存在；</li>
 *   <li>本异常 → 207：身份成立但无权限。</li>
 * </ul>
 */
public class ForbiddenException extends RuntimeException {
  public ForbiddenException(String message) {
    super(message);
  }
}
