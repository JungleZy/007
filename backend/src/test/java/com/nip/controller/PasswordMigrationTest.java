package com.nip.controller;

import com.nip.common.security.PasswordHasher;
import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PasswordMigrationTest {
  private static final String LEGACY_PASSWORD = "5f4dcc3b5aa765d61d8327deb882cf99";

  @Inject
  UserDao userDao;

  @Inject
  RoleDao roleDao;

  @Inject
  UserRoleDao userRoleDao;

  @Inject
  PasswordHasher passwordHasher;

  private final List<String> accounts = new ArrayList<>();
  private final List<String> roleIds = new ArrayList<>();

  @AfterEach
  void removeFixtures() {
    for (String account : accounts) {
      UserEntity user = userDao.findUserEntityByUserAccount(account);
      if (user != null) {
        userRoleDao.deleteByUserId(user.getId());
        userDao.deleteById(user.getId());
      }
    }
    for (String roleId : roleIds) {
      roleDao.deleteById(roleId);
    }
  }

  @Test
  void legacyLoginOnlyUpgradesAfterSuccessAndDoesNotRewriteCurrentHash() {
    UserEntity user = seedUser("legacy", LEGACY_PASSWORD);
    attachRole(user, createRole(1, 1));
    String oldToken = user.getToken();
    String oldDevice = user.getDeviceId();

    given().contentType(ContentType.JSON)
        .body(Map.of("userAccount", user.getUserAccount(), "password", "wrong-password",
            "deviceId", "failed-device"))
        .post("/api/user/login").then().statusCode(200).body("code", is(500));

    UserEntity unchanged = userDao.findById(user.getId());
    assertEquals(LEGACY_PASSWORD, unchanged.getPassword());
    assertEquals(oldToken, unchanged.getToken());
    assertEquals(oldDevice, unchanged.getDeviceId());

    String device = "login-device-" + UUID.randomUUID();
    loginAndReadProfile(user, "password", device);
    String upgraded = userDao.findById(user.getId()).getPassword();
    assertNewHash(upgraded);

    loginAndReadProfile(user, "password", device);
    assertEquals(upgraded, userDao.findById(user.getId()).getPassword());
  }

  @Test
  void registrationAndAdminImportSaltTheSamePlaintextAndPermitAuthenticatedLogin() {
    if (roleDao.find("isDefault", 0).firstResult() == null) {
      createRole(1, 0);
    }
    String registeredAccount = account("registered");
    given().contentType(ContentType.JSON)
        .body(Map.of("userAccount", registeredAccount, "userName", "registered",
            "idCard", idCard(), "password", "password"))
        .post("/api/user/signin").then().statusCode(200).body("code", is(200));
    UserEntity registered = userDao.findUserEntityByUserAccount(registeredAccount);
    assertNotNull(registered);
    String registeredHash = registered.getPassword();
    assertNewHash(registeredHash);

    UserEntity admin = seedUser("admin", LEGACY_PASSWORD);
    attachRole(admin, createRole(0, 1));
    String importedAccount = account("imported");
    given().contentType(ContentType.JSON)
        .header("token", admin.getToken()).header("deviceId", admin.getDeviceId())
        .body(List.of(Map.of("userAccount", importedAccount, "userName", "imported",
            "idCard", idCard(), "password", "password", "status", 0)))
        .post("/api/user/importUser").then().statusCode(200).body("code", is(200));
    UserEntity imported = userDao.findUserEntityByUserAccount(importedAccount);
    assertNotNull(imported);
    String importedHash = imported.getPassword();
    assertNewHash(importedHash);
    assertNotEquals(registeredHash.split("\\$", -1)[3], importedHash.split("\\$", -1)[3]);

    loginAndReadProfile(registered, "password", "registered-device-" + UUID.randomUUID());
    loginAndReadProfile(imported, "password", "imported-device-" + UUID.randomUUID());
    assertEquals(registeredHash, userDao.findById(registered.getId()).getPassword());
    assertEquals(importedHash, userDao.findById(imported.getId()).getPassword());
  }

  @Test
  void plainTextPasswordVerificationIsScopedToTokenOwner() {
    UserEntity owner = seedUser("owner", passwordHasher.hash("owner-secret"));
    UserEntity other = seedUser("other", LEGACY_PASSWORD);

    verifyPassword(owner, "owner-secret", true);
    verifyPassword(owner, "password", false);
    verifyPassword(other, "password", true);
    verifyPassword(other, "owner-secret", false);
  }

  private UserEntity seedUser(String prefix, String storedPassword) {
    UserEntity user = new UserEntity();
    user.setUserAccount(account(prefix));
    user.setUserName(prefix);
    user.setIdCard(idCard());
    user.setPassword(storedPassword);
    user.setStatus(0);
    user.setToken("password-token-" + UUID.randomUUID());
    user.setDeviceId("password-device-" + UUID.randomUUID());
    return userDao.saveAndFlush(user);
  }

  private RoleEntity createRole(int isAdmin, int isDefault) {
    RoleEntity role = new RoleEntity();
    role.setTitle("password-role-" + UUID.randomUUID());
    role.setIsAdmin(isAdmin);
    role.setIsDefault(isDefault);
    role = roleDao.saveAndFlush(role);
    roleIds.add(role.getId());
    return role;
  }

  private void attachRole(UserEntity user, RoleEntity role) {
    UserRoleEntity link = new UserRoleEntity();
    link.setUserId(user.getId());
    link.setRoleId(role.getId());
    userRoleDao.saveAndFlush(link);
  }

  private void loginAndReadProfile(UserEntity user, String password, String device) {
    String token = given().contentType(ContentType.JSON)
        .body(Map.of("userAccount", user.getUserAccount(), "password", password, "deviceId", device))
        .post("/api/user/login").then().statusCode(200).body("code", is(200))
        .extract().jsonPath().getString("data.token");
    assertNotNull(token);
    given().header("token", token).header("deviceId", device)
        .post("/api/user/getUsersByToken").then().statusCode(200)
        .body("code", is(200)).body("data.id", is(user.getId()));
  }

  private void verifyPassword(UserEntity user, String password, boolean expected) {
    given().contentType(ContentType.TEXT)
        .header("token", user.getToken()).header("deviceId", user.getDeviceId())
        .body(password).post("/api/user/verifyPassword").then().statusCode(200)
        .body("code", is(200)).body("data", is(expected));
  }

  private void assertNewHash(String hash) {
    assertNotNull(hash);
    assertTrue(hash.matches("pbkdf2_sha256\\$1\\$[0-9]+\\$[A-Za-z0-9_-]{22}\\$[A-Za-z0-9_-]{43}"));
  }

  private String account(String prefix) {
    String account = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    accounts.add(account);
    return account;
  }

  private String idCard() {
    long random = Integer.toUnsignedLong(UUID.randomUUID().hashCode());
    return String.format("%06d19900101%04d", 100000 + random % 900000, random % 10000);
  }
}
