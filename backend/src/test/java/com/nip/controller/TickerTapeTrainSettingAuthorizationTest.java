package com.nip.controller;

import com.nip.dao.RoleDao;
import com.nip.dao.TickerTapeTrainSettingDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.entity.RoleEntity;
import com.nip.entity.TickerTapeTrainSettingEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TickerTapeTrainSettingAuthorizationTest {
  @Inject UserDao userDao; @Inject RoleDao roleDao; @Inject UserRoleDao userRoleDao;
  @Inject TickerTapeTrainSettingDao settingDao;

  @Test
  void studentCannotReplaceSettingsButCanReadAndAdminCanWrite() {
    UserEntity student = user("setting-student"); UserEntity admin = user("setting-admin"); attachAdmin(admin);
    TickerTapeTrainSettingEntity existing = new TickerTapeTrainSettingEntity(); existing.setType("0"); existing.setRate(60); existing.setText("existing"); settingDao.saveAndFlush(existing);
    long before = settingDao.count();
    Map<String, Object> payload = Map.of("paramList", List.of(Map.of("type", "1", "rate", 120, "text", "admin")));
    given().contentType(ContentType.JSON).headers("token", student.getToken(), "deviceId", student.getDeviceId()).body(payload)
        .post("/api/tickerTapeTrainSetting/addOrUpdate").then().statusCode(200).body("code", is(207));
    assertEquals(before, settingDao.count());
    assertEquals(60, settingDao.findById(existing.getId()).getRate());
    given().headers("token", student.getToken(), "deviceId", student.getDeviceId()).post("/api/tickerTapeTrainSetting/findAll")
        .then().statusCode(200).body("code", is(200));
    given().contentType(ContentType.JSON).headers("token", admin.getToken(), "deviceId", admin.getDeviceId()).body(payload)
        .post("/api/tickerTapeTrainSetting/addOrUpdate").then().statusCode(200).body("code", is(200));
    assertEquals(1, settingDao.count());
  }

  private UserEntity user(String prefix) {
    return Fixtures.user(userDao, prefix + "-token-" + UUID.randomUUID(), "device-" + UUID.randomUUID());
  }
  private void attachAdmin(UserEntity user) { RoleEntity r = new RoleEntity(); r.setTitle("admin-" + UUID.randomUUID()); r.setIsAdmin(0); r.setIsDefault(1); r = roleDao.saveAndFlush(r); UserRoleEntity ur = new UserRoleEntity(); ur.setUserId(user.getId()); ur.setRoleId(r.getId()); userRoleDao.saveAndFlush(ur); }
}
