package com.nip.controller;

import com.nip.dao.UserDao;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class SessionLogoutTest {
  @Inject UserDao userDao;

  @Test
  void logoutInvalidatesTheOldTokenWithoutAffectingAnotherSession() {
    String token = "logout-" + UUID.randomUUID();
    String device = "device-" + UUID.randomUUID();
    UserEntity user = Fixtures.user(userDao, token, device);
    String otherToken = "other-" + UUID.randomUUID();
    String otherDevice = "other-device-" + UUID.randomUUID();
    UserEntity other = Fixtures.user(userDao, otherToken, otherDevice);
    try {
      given().header("token", token).header("deviceId", device)
          .post("/api/user/userOut").then().statusCode(200)
          .body("code", is(200)).body("data", is(true));
      assertNull(userDao.findById(user.getId()).getToken());
      assertNull(userDao.findById(user.getId()).getDeviceId());
      given().header("token", token).header("deviceId", device)
          .get("/api/menus/getMenusAll").then().statusCode(200).body("code", is(206));
      given().header("token", otherToken).header("deviceId", otherDevice)
          .get("/api/menus/getMenusAll").then().statusCode(200).body("code", is(200));
    } finally {
      userDao.deleteById(user.getId());
      userDao.deleteById(other.getId());
    }
  }
}
