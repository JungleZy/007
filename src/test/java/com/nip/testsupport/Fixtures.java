package com.nip.testsupport;

import com.nip.dao.UserDao;
import com.nip.entity.UserEntity;

public final class Fixtures {
  private Fixtures() {}

  public static UserEntity user(UserDao userDao, String token) {
    UserEntity u = new UserEntity();
    u.setUserName("tester");
    u.setUserAccount("tester");
    u.setToken(token);
    return userDao.save(u); // save 自带事务独立提交
  }
}
