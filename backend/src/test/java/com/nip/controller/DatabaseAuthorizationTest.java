package com.nip.controller;

import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class DatabaseAuthorizationTest {
  @Inject UserDao users;
  @Inject RoleDao roles;
  @Inject UserRoleDao userRoles;

  @Test
  void ordinaryUserCannotAccessAnyDatabaseOperation() {
    var user = actor(1);
    given().header("token", user.getToken()).header("deviceId", user.getDeviceId())
        .get("/api/database/status").then().statusCode(200).body("code", is(207));
    given().header("token", user.getToken()).header("deviceId", user.getDeviceId())
        .post("/api/database/backup").then().statusCode(200).body("code", is(207));
    given().header("token", user.getToken()).header("deviceId", user.getDeviceId())
        .multiPart("file", "test.sql", "SELECT 1;".getBytes(StandardCharsets.UTF_8))
        .multiPart("confirmation", "project006_test")
        .post("/api/database/import").then().statusCode(200).body("code", is(207));
  }

  @Test
  void administratorCanReadStatusButCannotImportInvalidFile() {
    var user = actor(0);
    given().header("token", user.getToken()).header("deviceId", user.getDeviceId())
        .get("/api/database/status").then().statusCode(200).body("code", is(200));
    given().header("token", user.getToken()).header("deviceId", user.getDeviceId())
        .multiPart("file", "test.txt", "SELECT 1;".getBytes(StandardCharsets.UTF_8))
        .multiPart("confirmation", "project006_test")
        .post("/api/database/import").then().statusCode(200).body("code", is(202));
    given().header("token", user.getToken()).header("deviceId", user.getDeviceId())
        .multiPart("file", "test.sql", "SELECT 1;".getBytes(StandardCharsets.UTF_8))
        .multiPart("confirmation", "wrong-database")
        .post("/api/database/import").then().statusCode(200).body("code", is(202));
  }

  private UserEntity actor(int isAdmin) {
    String suffix = UUID.randomUUID().toString();
    UserEntity user = new UserEntity();
    user.setUserAccount("db-" + suffix.substring(0, 20));
    user.setUserName("database-test");
    user.setDeviceId("device-" + suffix);
    String token = "token-" + suffix;
    Fixtures.sessionToken(user, token);
    user = users.saveAndFlush(user);
    user.setToken(token);
    RoleEntity role = new RoleEntity();
    role.setTitle("db-role-" + suffix);
    role.setIsAdmin(isAdmin);
    role.setIsDefault(1);
    role = roles.saveAndFlush(role);
    UserRoleEntity link = new UserRoleEntity();
    link.setUserId(user.getId());
    link.setRoleId(role.getId());
    userRoles.saveAndFlush(link);
    return user;
  }
}
