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
    u.setToken(SessionToken.hash(token));
    u.setDeviceId(deviceId);
    UserEntity saved = QuarkusTransaction.requiringNew().call(() -> userDao.save(u));
    saved.setToken(token);
    return saved;
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
