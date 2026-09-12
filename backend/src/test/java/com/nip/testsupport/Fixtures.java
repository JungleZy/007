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

public final class Fixtures {
  private Fixtures() {}

  public static UserEntity user(UserDao userDao, String token) {
    return user(userDao, token, null);
  }

  public static UserEntity user(UserDao userDao, String token, String deviceId) {
    UserEntity u = new UserEntity();
    u.setUserName("tester");
    // 账号必须唯一：save 独立提交且用例间无清理，共用 "tester" 会让
    // existsUserEntitiesByIdCardOrUserAccount / findUserEntityByUserAccount 等
    // 按账号查询的路径跨用例串数据。
    u.setUserAccount("tester-" + UUID.randomUUID());
    u.setToken(token);
    u.setDeviceId(deviceId);
    return userDao.save(u); // save 自带事务独立提交
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
