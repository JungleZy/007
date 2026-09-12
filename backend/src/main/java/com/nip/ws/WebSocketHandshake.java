package com.nip.ws;

import com.nip.dao.UserDao;
import com.nip.entity.UserEntity;
import com.nip.service.UserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.DEVICE_ID;
import static com.nip.common.constants.BaseConstants.TOKEN;

/**
 * WebSocket 握手鉴权（SEC-06）。
 *
 * <p>浏览器的 {@code WebSocket} 构造器无法设置请求头，凭据只能随 query 传递，
 * 因此这里从 {@link Session#getRequestParameterMap()} 取 {@code token}+{@code deviceId}，
 * 口径与 {@code JWTInterceptor} 完全一致：缺 token、缺 deviceId、或两者组合查无此人一律拒绝。
 *
 * <p>校验通过的身份由 {@link #bind(Session, String)} 绑到连接上，之后所有回调
 * （onMessage/onClose/onError）只认 {@link #authenticatedId(Session)}：
 * 端点路径里的 {@code uid}/{@code sid}/{@code id} 只作路由，不作身份。
 */
@ApplicationScoped
@Slf4j
public class WebSocketHandshake {

  /** 已鉴权用户 id 在会话属性里的键。 */
  private static final String AUTHENTICATED_ID = "nip.ws.authenticatedUserId";

  @Inject
  UserDao userDao;
  @Inject
  UserService userService;

  /**
   * 握手校验。
   *
   * @param session 待校验的连接
   * @return 已鉴权用户；凭据缺失/失效返回 {@code null}，调用方负责发拒因帧并关闭连接
   */
  public UserEntity authenticate(Session session) {
    String token = parameter(session, TOKEN);
    String deviceId = parameter(session, DEVICE_ID);
    if (StringUtils.isEmpty(token) || StringUtils.isEmpty(deviceId)) {
      return null;
    }
    if (!userDao.existsUserByTokenAndDeviceId(token, deviceId)) {
      return null;
    }
    try {
      return userService.getUserByToken(token);
    } catch (RuntimeException invalid) {
      // getUserByToken 查无用户时抛 UnauthorizedException：WebSocket 侧没有信封可返回，
      // 统一收敛成「校验失败」由调用方关闭连接
      log.warn("WebSocket 握手校验失败", invalid);
      return null;
    }
  }

  /** 把已鉴权身份绑到连接上；onOpen 注册房间之前必须先调用。 */
  public static void bind(Session session, String userId) {
    session.getUserProperties().put(AUTHENTICATED_ID, userId);
  }

  /**
   * 取 onOpen 绑定的已鉴权身份。
   *
   * @return 该连接的用户 id；未经握手（被拒后仍收到回调）的连接返回 {@code null}
   */
  public static String authenticatedId(Session session) {
    Map<String, Object> properties = session.getUserProperties();
    if (properties == null) {
      return null;
    }
    return (String) properties.get(AUTHENTICATED_ID);
  }

  private static String parameter(Session session, String name) {
    Map<String, List<String>> parameters = session.getRequestParameterMap();
    if (parameters == null) {
      return null;
    }
    List<String> values = parameters.get(name);
    return values == null || values.isEmpty() ? null : values.getFirst();
  }
}
