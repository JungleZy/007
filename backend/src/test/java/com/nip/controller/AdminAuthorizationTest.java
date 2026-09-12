package com.nip.controller;

import com.nip.testsupport.Fixtures;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    actor = resave(actor);
    victim = resave(victim);

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
        .body("data", is("123456"));
    assertTrue(userDao.findById(target.getId()).getPassword().startsWith("pbkdf2_sha256$"));
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

  @Test
  void ordinaryUserCannotSaveOrImportUsers() {
    UserEntity actor = createUser("editor");
    UserEntity victim = createUser("edit-victim");
    attachRole(actor, 1);
    given().contentType(ContentType.JSON)
        .header("token", actor.getToken()).header("deviceId", actor.getDeviceId())
        .body(Map.of("id", victim.getId(), "userAccount", "changedAccount", "userName", "changed",
            "idCard", "110101199001010011"))
        .post("/api/user/saveUser").then().statusCode(200).body("code", is(207));
    assertEquals(victim.getUserName(), userDao.findById(victim.getId()).getUserName());

    String account = "import" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    given().contentType(ContentType.JSON)
        .header("token", actor.getToken()).header("deviceId", actor.getDeviceId())
        .body(java.util.List.of(Map.of("userAccount", account, "userName", "imported", "password", "password")))
        .post("/api/user/importUser").then().statusCode(200).body("code", is(207));
    assertEquals(0, userDao.count("userAccount", account));
  }

  @Test
  void loginSeparatesCredentialsFromUserProfile() {
    UserEntity actor = createUser("login");
    actor.setStatus(0);
    actor = resave(actor);
    attachRole(actor, 0);
    String device = "login-device-" + UUID.randomUUID();
    var session = given().contentType(ContentType.JSON)
        .body(Map.of("userAccount", actor.getUserAccount(), "password", "password", "deviceId", device))
        .post("/api/user/login").then().statusCode(200).body("code", is(200))
        .body("data.user.id", is(actor.getId()))
        .body("data.user", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("password")))
        .body("data.user", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("token")))
        .body("data.user", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("deviceId")))
        .body("data.deviceId", is(device))
        .extract().jsonPath();
    String token = session.getString("data.token");
    assertNotNull(token);
    given().header("token", token).header("deviceId", device)
        .post("/api/user/getUsersByToken").then().statusCode(200).body("code", is(200))
        .body("data.id", is(actor.getId()))
        .body("data", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("password")))
        .body("data", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("token")))
        .body("data", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("deviceId")));
  }

  private UserEntity createUser(String prefix) {
    UserEntity user = new UserEntity();
    user.setUserAccount(prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
    user.setUserName(prefix);
    user.setIdCard("11010119900101" + String.format("%04d", UUID.randomUUID().hashCode() & 0xFFFF));
    user.setPassword(MD5Util.encrypt("password"));
    String token = prefix + "-token-" + UUID.randomUUID();
    // 库里只落摘要（与 UserService.login 同口径），返回对象保留明文供用例当请求头凭据
    Fixtures.sessionToken(user, token);
    user.setDeviceId(prefix + "-device-" + UUID.randomUUID());
    UserEntity saved = userDao.saveAndFlush(user);
    saved.setToken(token);
    return saved;
  }

  /**
   * 回写用户行。
   *
   * <p>{@link #createUser} 返回对象上的 token 是明文，直接 {@code saveAndFlush} 会把明文刷进
   * {@code t_user.token}，之后鉴权按摘要就查不到人；故落库前换回摘要，返回对象仍带明文。
   */
  private UserEntity resave(UserEntity user) {
    String token = user.getToken();
    Fixtures.sessionToken(user, token);
    UserEntity saved = userDao.saveAndFlush(user);
    saved.setToken(token);
    return saved;
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
