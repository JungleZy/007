package com.nip.common.exception;

/**
 * 业务终态拒绝：目标对象已进入不可逆状态（训练已完成／已提交／轮次已变化／补交窗口已结束），
 * 同一请求重试必然再次失败。
 *
 * <p>由 {@link TerminalStateExceptionMapper} 映射为 HTTP 200 + {@code code:208} 信封，
 * 业务文案原样回传——终态拒因本身就是要给用户看的（「训练已完成，不能修改」），不涉及越权信息泄露。
 *
 * <p>与其它三种拒绝的分工（不要混用）：
 * <ul>
 *   <li>{@link UnauthorizedException} → 203：token 无效或已过期，调用者身份都没建立；</li>
 *   <li>{@code IllegalArgumentException}／{@code IllegalStateException} → 202：参数不合法或目标不存在，
 *       <b>可修正后重试</b>；</li>
 *   <li>{@link ForbiddenException} → 207：身份成立但无权限（也是终态，前端与 208 同归入不可重试）；</li>
 *   <li>本异常 → 208：权限与参数都没问题，但目标状态已终结，<b>重试无意义</b>。</li>
 * </ul>
 *
 * <p>判定口径：只有「重试同一请求永远不会成功、必须由用户重新加载或新建训练」的拒绝才用它。
 * 把可修正的参数错误（页面内容为空、页码越界）归到这里会让用户失去重试机会，属于误用。
 */
public class TerminalStateException extends RuntimeException {
  public TerminalStateException(String message) {
    super(message);
  }
}
