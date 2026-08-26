package com.nip.testsupport;

import com.nip.dao.UserDao;
import com.nip.entity.UserEntity;

public final class Fixtures {
  private Fixtures() {}

  public static UserEntity user(UserDao userDao, String token) {
    return user(userDao, token, null);
  }

  public static UserEntity user(UserDao userDao, String token, String deviceId) {
    UserEntity u = new UserEntity();
    u.setUserName("tester");
    u.setUserAccount("tester");
    u.setToken(token);
    u.setDeviceId(deviceId);
    return userDao.save(u); // save 自带事务独立提交
  }
}
