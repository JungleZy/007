package com.nip.controller;

import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.common.utils.MD5Util;
import com.nip.entity.UserRoleEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class AdminAuthorizationTest {
  @Inject
  UserDao userDao;

  @Inject
  RoleDao roleDao;

  @Inject
  UserRoleDao userRoleDao;

  @Test
  void ordinaryUserCannotDeleteAnotherUser() {
    UserEntity actor = createUser("ordinary");
    UserEntity victim = createUser("victim");
    attachRole(actor, 1);

    given()
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .queryParam("userId", victim.getId())
        .when()
        .get("/api/user/delete")
        .then()
        .statusCode(200)
        .body("code", is(207));

    assertNotNull(userDao.findById(victim.getId()));
  }

  @Test
  void userWithoutRoleCannotAssignRoles() {
    UserEntity actor = createUser("no-role");
    UserEntity target = createUser("role-target");

    given()
        .contentType(ContentType.JSON)
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .body(Map.of("userId", target.getId(), "roleIds", java.util.List.of()))
        .when()
        .post("/api/user/addUserRole")
        .then()
        .statusCode(200)
        .body("code", is(207));
  }

  @Test
  void changePasswordUsesTokenOwnerInsteadOfBodyUserId() {
    UserEntity actor = createUser("password-actor");
    UserEntity victim = createUser("password-victim");
    actor.setPassword(MD5Util.encrypt("old-actor-password"));
    victim.setPassword(MD5Util.encrypt("victim-password"));
    userDao.saveAndFlush(actor);
    userDao.saveAndFlush(victim);

    given()
        .contentType(ContentType.JSON)
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .body(Map.of(
            "userId", victim.getId(),
            "oldPassword", "old-actor-password",
            "newPassword", "new-actor-password",
            "newPasswordV", "new-actor-password"))
        .when()
        .post("/api/user/changePassword")
        .then()
        .statusCode(200)
        .body("code", is(200))
        .body("data", is(true));

    assertEquals(MD5Util.encrypt("new-actor-password"), userDao.findById(actor.getId()).getPassword());
    assertEquals(MD5Util.encrypt("victim-password"), userDao.findById(victim.getId()).getPassword());
  }

  @Test
  void superAdminRoleAllowsProtectedOperation() {
    UserEntity actor = createUser("admin");
    UserEntity target = createUser("reset-target");
    attachRole(actor, 0);

    given()
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .queryParam("userId", target.getId())
        .when()
        .get("/api/user/resetPassword")
        .then()
        .statusCode(200)
        .body("code", is(200))
        .body("data", containsString("123456"));
  }

  @Test
  void authenticatedDirectoryReturnsOnlySummaryFields() {
    UserEntity actor = createUser("directory");

    given()
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .when()
        .post("/api/user/getUserDirectory")
        .then()
        .statusCode(200)
        .body("code", is(200))
        .body("data.id", org.hamcrest.Matchers.hasItem(actor.getId()))
        .body("data.userAccount", org.hamcrest.Matchers.hasItem(actor.getUserAccount()))
        .body("data.password", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue()))
        .body("data.token", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue()))
        .body("data.deviceId", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue()));
  }

  private UserEntity createUser(String prefix) {
    UserEntity user = new UserEntity();
    user.setUserAccount(prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
    user.setUserName(prefix);
    user.setIdCard("11010119900101" + String.format("%04d", UUID.randomUUID().hashCode() & 0xFFFF));
    user.setPassword(MD5Util.encrypt("password"));
    user.setToken(prefix + "-token-" + UUID.randomUUID());
    user.setDeviceId(prefix + "-device-" + UUID.randomUUID());
    return userDao.saveAndFlush(user);
  }

  private void attachRole(UserEntity user, int isAdmin) {
    RoleEntity role = new RoleEntity();
    role.setTitle("role-" + UUID.randomUUID());
    role.setIsAdmin(isAdmin);
    role.setIsDefault(1);
    role = roleDao.saveAndFlush(role);

    UserRoleEntity link = new UserRoleEntity();
    link.setUserId(user.getId());
    link.setRoleId(role.getId());
    userRoleDao.saveAndFlush(link);
  }
}
