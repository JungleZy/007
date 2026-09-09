package com.nip.controller;

import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P0-01 回归：公开注册不得把客户端提供的用户 ID 当作更新目标。
 */
@QuarkusTest
class AnonymousSigninTest {
  @Inject
  UserDao userDao;

  @Inject
  RoleDao roleDao;

  @Test
  void existingIdOnAnonymousSigninIsRejectedAndVictimRemainsUnchanged() {
    UserEntity victim = saveVictim();
    String originalAccount = victim.getUserAccount();
    String originalName = victim.getUserName();
    String originalIdCard = victim.getIdCard();

    given()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "id", victim.getId(),
            "userAccount", uniqueAccount("attacker"),
            "userName", "attacker-name",
            "idCard", "110101199101010012",
            "password", "attacker-password"))
        .when()
        .post("/api/user/signin")
        .then()
        .statusCode(200)
        .body("code", is(202));

    UserEntity persistedVictim = userDao.findById(victim.getId());
    assertNotNull(persistedVictim);
    assertEquals(originalAccount, persistedVictim.getUserAccount());
    assertEquals(originalName, persistedVictim.getUserName());
    assertEquals(originalIdCard, persistedVictim.getIdCard());
  }

  @Test
  void anonymousSigninWithoutIdCreatesANewUser() {
    ensureDefaultRole();
    String account = uniqueAccount("newuser");
    String idCard = uniqueIdCard();

    given()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "userAccount", account,
            "userName", "new-user",
            "idCard", idCard,
            "password", "new-password"))
        .when()
        .post("/api/user/signin")
        .then()
        .statusCode(200)
        .body("code", is(200));

    UserEntity created = userDao.findUserEntityByUserAccount(account);
    assertNotNull(created);
    assertEquals(idCard, created.getIdCard());
  }

  private UserEntity saveVictim() {
    UserEntity victim = new UserEntity();
    victim.setUserAccount(uniqueAccount("victim"));
    victim.setUserName("victim-name");
    victim.setIdCard(uniqueIdCard());
    victim.setPassword("victim-password");
    victim.setUserSex(1);
    return userDao.saveAndFlush(victim);
  }

  private void ensureDefaultRole() {
    if (roleDao.find("isDefault", 0).firstResult() != null) {
      return;
    }
    RoleEntity role = new RoleEntity();
    role.setTitle("default-" + UUID.randomUUID());
    role.setIsAdmin(1);
    role.setIsDefault(0);
    roleDao.saveAndFlush(role);
  }

  private String uniqueAccount(String prefix) {
    return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
  }
  private String uniqueIdCard() {
    return "11010119900101" + String.format("%04d", (UUID.randomUUID().hashCode() & 0xFFFF) % 10000);
  }
}
