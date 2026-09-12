package com.nip.testsupport;

import com.google.gson.JsonObject;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.GradingRuleDao;
import com.nip.entity.GradingRuleEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.nip.dao.UserDao;
import com.nip.entity.UserEntity;
import java.util.UUID;
import com.nip.common.security.SessionToken;
import io.quarkus.narayana.jta.QuarkusTransaction;

public final class Fixtures {
  private Fixtures() {}

  public static UserEntity user(UserDao userDao, String token) {
    return user(userDao, token, null);
  }

  /**
   * 播种一个可直接用 {@code token} 当请求头凭据的用户。
   *
   * <p>唯一的用户播种入口：{@code t_user.token} 落 {@link SessionToken#hash(String)} 的摘要
   * （与 {@code UserService.login} 同口径），而返回对象的 {@code token} 字段保持**明文**，
   * 供用例直接塞进 {@code token} 请求头或传给 service 方法。用例不得自行
   * {@code new UserEntity().setToken(明文)} 后落库 —— 那样鉴权按摘要查不到，必得 206。
   *
   * <p>播种走独立事务：返回的实例因此与调用方的持久化上下文脱管，把明文写回它不会
   * 被脏检查刷回库里（{@code @TestTransaction} 用例同样成立）。
   */
  public static UserEntity user(UserDao userDao, String token, String deviceId) {
    UserEntity u = new UserEntity();
    u.setUserName("tester");
    // 账号必须唯一：save 独立提交且用例间无清理，共用 "tester" 会让
    // existsUserEntitiesByIdCardOrUserAccount / findUserEntityByUserAccount 等
    // 按账号查询的路径跨用例串数据。
    u.setUserAccount("tester-" + UUID.randomUUID());
    sessionToken(u, token);
    u.setDeviceId(deviceId);
    UserEntity saved = QuarkusTransaction.requiringNew().call(() -> userDao.save(u));
    saved.setToken(token);
    return saved;
  }

  /**
   * 把明文 token 写成**库口径的摘要**，返回同一实例供调用方继续设置领域字段后自行落库。
   *
   * <p>给 {@link #user} 覆盖不到的播种场景用：那些用例要额外落 {@code idCard}/{@code phone}/
   * {@code password}/{@code status}，或者要先返回脱管实体再由调用方保存。它们过去各自写
   * {@code setToken(SessionToken.hash(t))}，于是"入库必须是摘要"这条口径在测试支撑里有 4 份副本，
   * 抄漏一处就是鉴权按摘要查不到人、用例静默拿 206（`UserDirectoryAuthorizationTest` 已踩过一次）。
   * 现在 {@link SessionToken#hash} 在测试侧只剩三类出现：本方法（写）、{@link #userIdByToken}（读），
   * 以及两个**断言存储口径本身**的用例（`OpaqueSessionTokenTest`、`PasswordMigrationTest`）——
   * 后者必须直接引用摘要函数，它就是被断言的契约。
   *
   * <p>落库后记得把明文写回返回值（{@code saved.setToken(明文)}）当请求头凭据用。
   */
  public static UserEntity sessionToken(UserEntity user, String plaintext) {
    user.setToken(SessionToken.hash(plaintext));
    return user;
  }

  /**
   * 按明文 token 反查用户 id。
   *
   * <p>库里存的是摘要，所以不能拿明文直接 {@code find("token", 明文)} —— 那样查不到人，
   * 用例会以 NPE 或空结果的形式莫名失败。三个训练域的 service 测试都要用播种用户的 id
   * 填 {@code createUser}/{@code createUserId}，过去各自写一遍摘要转换。
   */
  public static String userIdByToken(UserDao userDao, String token) {
    UserEntity user = userDao.find("token", SessionToken.hash(token)).firstResult();
    if (user == null) throw new IllegalStateException("未按 token 找到播种用户，检查是否走了 Fixtures 播种");
    return user.getId();
  }

  public static GradingRuleEntity handkeyRule(GradingRuleDao dao) {
    try (var source = Fixtures.class.getResourceAsStream("/scoring/grading-rule-type0.json")) {
      if (source == null) throw new IllegalStateException("Missing handkey rule fixture");
      JsonObject content = JSONUtils.fromJson(new String(source.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
      content.addProperty("rateUnit", "CHARACTERS_PER_MINUTE");
      GradingRuleEntity rule = new GradingRuleEntity();
      rule.setTitle("handkey-fixture-" + UUID.randomUUID());
      rule.setType(0);
      rule.setScore(100);
      rule.setContent(content.toString());
      return dao.saveAndFlush(rule);
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot load handkey rule fixture", failure);
    }
  }
}
