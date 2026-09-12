package com.nip.controller;

import com.nip.common.security.PasswordHasher;
import com.nip.common.security.SessionToken;
import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 会话令牌的不透明性与存储哈希（SEC-04 可反解部分）。
 *
 * <p>旧实现把 {@code AES/ECB(账号-明文口令-设备号)} 当令牌、密钥是源码里的常量，令牌因此
 * 可反解出口令、且同输入恒等；本类锁住新口径：随机签发、库存摘要、摘要本身不是凭据。
 */
@QuarkusTest
class OpaqueSessionTokenTest {

  private static final String PASSWORD = "opaque-token-secret";

  /** 已随 {@code AESUtil} 下线的密钥：仅用于复刻旧格式令牌，证明它不再能通过鉴权。 */
  private static final String RETIRED_AES_KEY = "gdEFPBLx3zu2Y18wrAmtig==";

  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;
  @Inject PasswordHasher passwordHasher;

  @Test
  void repeatedLoginsIssueDifferentTokensAndStoreOnlyTheDigest() {
    UserEntity user = seedUser();

    String first = login(user.getUserAccount(), "device-" + UUID.randomUUID());
    String second = login(user.getUserAccount(), "device-" + UUID.randomUUID());

    // 旧实现同账号同口令同设备恒等，且换设备也只换一段密文；随机签发必须两次都不同
    assertNotEquals(first, second, "两次登录必须签发不同令牌");

    String stored = userDao.findById(user.getId()).getToken();
    assertNotEquals(second, stored, "t_user.token 不得存令牌明文");
    assertEquals(SessionToken.hash(second), stored, "t_user.token 必须是令牌的 SHA-256 hex");
  }

  @Test
  void storedDigestIsNotUsableAsACredential() {
    UserEntity user = seedUser();
    String device = "device-" + UUID.randomUUID();

    String token = login(user.getUserAccount(), device);
    call(token, device).body("code", is(200));

    // 摘要若也能当凭据，一次库导出就等于会话接管 —— 存哈希就白存了
    call(userDao.findById(user.getId()).getToken(), device).body("code", is(206));
  }

  @Test
  void legacyAesTokenIsRejected() {
    UserEntity user = seedUser();
    String device = "device-" + UUID.randomUUID();
    login(user.getUserAccount(), device);

    // 这正是旧实现会签发给这次登录的值：迁移后必须失效（存量会话全部需要重新登录）
    call(legacyAesToken(user.getUserAccount(), PASSWORD, device), device).body("code", is(206));
  }

  @Test
  void logoutInvalidatesTheIssuedToken() {
    UserEntity user = seedUser();
    String device = "device-" + UUID.randomUUID();
    String token = login(user.getUserAccount(), device);

    given().header("token", token).header("deviceId", device)
        .post("/api/user/userOut").then().statusCode(200)
        .body("code", is(200)).body("data", is(true));

    call(token, device).body("code", is(206));
  }

  private ValidatableResponse call(String token, String deviceId) {
    return given().header("token", token).header("deviceId", deviceId)
        .get("/api/menus/getMenusAll").then().statusCode(200);
  }

  private String login(String userAccount, String deviceId) {
    String token = given().contentType(ContentType.JSON)
        .body(Map.of("userAccount", userAccount, "password", PASSWORD, "deviceId", deviceId))
        .post("/api/user/login").then().statusCode(200).body("code", is(200))
        .extract().jsonPath().getString("data.token");
    assertNotNull(token, "登录响应必须把令牌明文回给登录者本人");
    return token;
  }

  /** 复刻已删除的 {@code AESUtil.encrypt(账号-口令-设备号, UKDAI_AES_KEY)}。 */
  private static String legacyAesToken(String userAccount, String password, String deviceId) {
    try {
      Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
      cipher.init(Cipher.ENCRYPT_MODE,
          new SecretKeySpec(Base64.getDecoder().decode(RETIRED_AES_KEY), "AES"));
      byte[] payload = (userAccount + "-" + password + "-" + deviceId).getBytes(StandardCharsets.UTF_8);
      return Base64.getEncoder().encodeToString(cipher.doFinal(payload));
    } catch (Exception failure) {
      throw new IllegalStateException("无法复刻旧格式令牌", failure);
    }
  }

  private UserEntity seedUser() {
    UserEntity user = new UserEntity();
    user.setUserAccount("opaque-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
    user.setUserName("opaque-token");
    user.setIdCard("11010119900101" + String.format("%04d", UUID.randomUUID().hashCode() & 0xFFFF));
    user.setPassword(passwordHasher.hash(PASSWORD));
    user.setStatus(0);
    UserEntity saved = userDao.saveAndFlush(user);

    RoleEntity role = new RoleEntity();
    role.setTitle("opaque-role-" + UUID.randomUUID());
    role.setIsAdmin(1);
    role.setIsDefault(1);
    role = roleDao.saveAndFlush(role);

    UserRoleEntity link = new UserRoleEntity();
    link.setUserId(saved.getId());
    link.setRoleId(role.getId());
    userRoleDao.saveAndFlush(link);
    return saved;
  }
}
