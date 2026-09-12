package com.nip.controller;

import com.nip.testsupport.Fixtures;
import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class CatalogAuthorizationTest {
  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;

  @Test
  void ordinaryUserCannotMutateCableOrDeviceCatalogs() {
    String token = "catalog-token-" + UUID.randomUUID();
    String device = "catalog-device-" + UUID.randomUUID();
    UserEntity actor = userDao.saveAndFlush(user(token, device));
    RoleEntity role = new RoleEntity();
    role.setTitle("catalog-ordinary-" + UUID.randomUUID());
    role.setIsAdmin(1);
    role.setIsDefault(1);
    role = roleDao.saveAndFlush(role);
    UserRoleEntity link = new UserRoleEntity();
    link.setUserId(actor.getId());
    link.setRoleId(role.getId());
    userRoleDao.saveAndFlush(link);

    given().contentType(ContentType.JSON).headers("token", token, "deviceId", device)
        .body("{}").post("/api/cable/save").then().statusCode(200).body("code", is(207));
    given().headers("token", token, "deviceId", device)
        .post("/api/cable/delete?id=missing").then().statusCode(200).body("code", is(207));
    given().contentType(ContentType.JSON).headers("token", token, "deviceId", device)
        .body("{}").post("/api/cable/type/save").then().statusCode(200).body("code", is(207));
    given().headers("token", token, "deviceId", device)
        .post("/api/cable/type/delete?id=missing").then().statusCode(200).body("code", is(207));
    given().contentType(ContentType.JSON).headers("token", token, "deviceId", device)
        .body("{}").post("/api/device/save").then().statusCode(200).body("code", is(207));
    given().contentType(ContentType.JSON).headers("token", token, "deviceId", device)
        .body(Map.of("id", 0)).post("/api/device/delete").then().statusCode(200).body("code", is(207));
    given().contentType(ContentType.JSON).headers("token", token, "deviceId", device)
        .body("{}").post("/api/device/addDeviceDescription").then().statusCode(200).body("code", is(207));
  }

  /** 库里落摘要（与 UserService.login 同口径）；用例用明文 token 当请求头凭据。 */
  private static UserEntity user(String token, String deviceId) {
    UserEntity user = new UserEntity();
    user.setUserName("catalog-ordinary");
    user.setUserAccount("catalog-" + UUID.randomUUID());
    user.setIdCard("11010119900101" + String.format("%04d", UUID.randomUUID().hashCode() & 0xFFFF));
    Fixtures.sessionToken(user, token);
    user.setDeviceId(deviceId);
    return user;
  }
}
