package com.nip.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 会话令牌的签发与摘要。
 *
 * <p>令牌是不透明随机串：服务端从不反解它，只做等值查找。明文只在登录响应里回给登录者本人
 * （{@code dto/LoginSessionDto#token}），{@code t_user.token} 落的是 {@link #hash(String)}
 * 的 SHA-256 十六进制摘要（64 字符，{@code varchar(255)} 足够，无需迁移）。
 *
 * <p>摘要逻辑只此一处：签发面（{@code UserService.login}）与查找面
 * （{@code UserDao.findUserEntityByToken}/{@code existsUserByTokenAndDeviceId}）都调这里，
 * 避免两侧算法漂移导致「登录成功但鉴权恒失败」。
 *
 * <p>令牌熵足够（256 bit）且一次性随机，不可枚举，因此摘要不加盐、不做慢哈希 —— 单次 SHA-256
 * 即可；加盐会破坏「按摘要等值查找」这一唯一用法。
 */
public final class SessionToken {

  /** 随机字节数：32 字节 = 256 bit 熵。 */
  private static final int TOKEN_BYTES = 32;

  private static final SecureRandom RANDOM = new SecureRandom();

  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  private SessionToken() {
  }

  /**
   * 签发一枚新令牌。
   *
   * @return Base64URL 无填充的 43 字符串，仅回给登录者本人
   */
  public static String issue() {
    byte[] raw = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(raw);
    return BASE64_URL.encodeToString(raw);
  }

  /**
   * 计算令牌摘要。
   *
   * @param token 令牌明文；{@code null} 原样返回 {@code null}，保持「无凭据即查不到用户」的既有语义
   * @return SHA-256 十六进制小写摘要（64 字符）
   */
  public static String hash(String token) {
    if (token == null) {
      return null;
    }
    return HexFormat.of().formatHex(digest().digest(token.getBytes(StandardCharsets.UTF_8)));
  }

  private static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException absent) {
      // SHA-256 是 JLS 强制实现的算法，取不到说明 JVM 被裁剪，不能静默降级为明文存储
      throw new IllegalStateException("当前 JVM 缺少 SHA-256 实现，无法计算会话令牌摘要", absent);
    }
  }
}
