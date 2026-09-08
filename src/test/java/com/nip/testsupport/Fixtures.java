package com.nip.testsupport;

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
}
