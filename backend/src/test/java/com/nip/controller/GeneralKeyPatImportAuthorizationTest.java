package com.nip.controller;

import com.nip.common.utils.MD5Util;
import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.dao.general.key.GeneralKeyPatDao;
import com.nip.dao.general.key.GeneralKeyPatUserDao;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.entity.simulation.key.GeneralKeyPatEntity;
import com.nip.entity.simulation.key.GeneralKeyPatUserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 综合组训离线导入（{@code /api/generalKeyPat/importTrainInfo}）的对外契约（SEC-05）。
 *
 * <p>导入包完全来自客户端，却会在本库**建账号**并重写训练归属，因此三条消费者可见的约束必须成立：
 * <ul>
 *   <li>只有管理员能导入 —— 学员/普通人员拿 {@code code:207}；</li>
 *   <li>导入建出的账号<b>不带任何可用凭证</b>：{@code token}/{@code deviceId}/{@code password} 均为 NULL，
 *       包里夹带的凭据字段一律被丢弃，拿这些值冒充该账号只会拿到 {@code code:206}；</li>
 *   <li>{@code status} 由服务端决定，包内取值无效 —— 客户端不能借导入直接决定账号状态。</li>
 * </ul>
 * 另外锁死一条曾让导入整体失效的缺陷：新建账号的「包内 id → 本库 id」映射必须真正建立，
 * 否则训练创建人与全部参训行的 {@code userId} 会被写成 NULL。
 */
@QuarkusTest
class GeneralKeyPatImportAuthorizationTest {
  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;
  @Inject GeneralKeyPatDao trainDao;
  @Inject GeneralKeyPatUserDao trainUserDao;

  /** 包内夹带的口令明文；落库的话会是它的 MD5 旧格式摘要，而旧格式是 verify 支持的 —— 落库即可登录。 */
  private static final String SMUGGLED_PASSWORD = "smuggled-secret";

  @Test
  void ordinaryUserCannotImportTrain() {
    UserEntity student = user("import-student");
    attachRole(student, 1);

    given()
        .contentType(ContentType.JSON)
        .header("token", student.getToken())
        .header("deviceId", student.getDeviceId())
        .body(Map.of())
        .when()
        .post("/api/generalKeyPat/importTrainInfo")
        .then()
        .statusCode(200)
        .body("code", is(207));
  }

  @Test
  void importedAccountCarriesNoUsableCredential() {
    UserEntity admin = user("import-admin-cred");
    attachRole(admin, 0);
    String sourceId = "pkg-user-" + UUID.randomUUID();
    String account = "imported-" + UUID.randomUUID();
    String smuggledToken = "smuggled-token-" + UUID.randomUUID();
    String smuggledDevice = "smuggled-device-" + UUID.randomUUID();

    importAs(admin, packageOf(sourceId, account, smuggledToken, smuggledDevice, 1));

    UserEntity imported = userDao.findByUserAccount(account);
    assertNotNull(imported, "导入应按账号建号");
    assertNull(imported.getToken(), "导入不得落会话 token");
    assertNull(imported.getDeviceId(), "导入不得落设备号");
    assertNull(imported.getPassword(), "导入建号不设口令，须管理员 resetPassword 后才能登录");

    // 包里夹带的凭据即使原样落库也只能通过这条路被使用 —— 这里必须是「凭证异常」。
    given()
        .header("token", smuggledToken)
        .header("deviceId", smuggledDevice)
        .when()
        .get("/api/generalKeyPat/getTrainInfoBatch")
        .then()
        .statusCode(200)
        .body("code", is(206));
  }

  @Test
  void importedAccountStatusIsServerDecided() {
    UserEntity admin = user("import-admin-status");
    attachRole(admin, 0);
    String sourceId = "pkg-user-" + UUID.randomUUID();
    String account = "imported-status-" + UUID.randomUUID();

    // 包内 status=-1（停用中）：若服务端照抄，该账号的状态就由客户端说了算。
    importAs(admin, packageOf(sourceId, account, "t-" + UUID.randomUUID(), "d-" + UUID.randomUUID(), -1));

    UserEntity imported = userDao.findByUserAccount(account);
    assertNotNull(imported);
    assertEquals(Integer.valueOf(0), imported.getStatus(), "status 必须由服务端决定，包内取值无效");

    // 账号仍然不可用：口令为 NULL，包里夹带的那份（MD5 旧格式，落库即可登录）必须没有生效。
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("userAccount", account, "password", SMUGGLED_PASSWORD,
            "deviceId", "login-device-" + UUID.randomUUID()))
        .when()
        .post("/api/user/login")
        .then()
        .statusCode(200)
        .body("code", is(500))
        .body("data", nullValue());
  }

  @Test
  void importRemapsNewAccountIdsInsteadOfNulling() {
    UserEntity admin = user("import-admin-remap");
    attachRole(admin, 0);
    String sourceId = "pkg-user-" + UUID.randomUUID();
    String account = "imported-remap-" + UUID.randomUUID();

    importAs(admin, packageOf(sourceId, account, "t-" + UUID.randomUUID(), "d-" + UUID.randomUUID(), 0));

    UserEntity imported = userDao.findByUserAccount(account);
    assertNotNull(imported);
    List<GeneralKeyPatUserEntity> members = trainUserDao.findByUserId(imported.getId());
    assertEquals(1, members.size(), "参训行必须挂到新建账号的本库 id 上");
    GeneralKeyPatEntity train = trainDao.findById(members.get(0).getTrainId());
    assertNotNull(train);
    assertEquals(imported.getId(), train.getCreateUser(), "训练创建人必须重写为新建账号的本库 id");
  }

  /**
   * 一个最小导入包：一名包内用户（本库不存在的新账号）+ 一条它的参训行 + 一个由它创建的训练。
   * 用户行刻意夹带 {@code token}/{@code deviceId}/{@code password}/{@code status} ——
   * {@code UserSyncDto} 没有前三个字段，Jackson 会忽略未知键；这几个键在这里的作用是
   * 一旦有人把凭据字段加回 DTO 并恢复按名整体拷贝，上面的断言立刻转红。
   */
  private Map<String, Object> packageOf(String sourceId, String account, String token, String deviceId, int status) {
    Map<String, Object> userRow = new HashMap<>();
    userRow.put("id", sourceId);
    userRow.put("userAccount", account);
    userRow.put("userName", "imported-name");
    userRow.put("userImg", "/userImages/imported.png");
    userRow.put("status", status);
    userRow.put("token", token);
    userRow.put("deviceId", deviceId);
    userRow.put("password", MD5Util.encrypt(SMUGGLED_PASSWORD));

    Map<String, Object> trainRow = new HashMap<>();
    trainRow.put("title", "imported-train-" + UUID.randomUUID());
    trainRow.put("createUser", sourceId);
    trainRow.put("status", 0);
    trainRow.put("totalNumber", 1);

    Map<String, Object> memberRow = new HashMap<>();
    memberRow.put("userId", sourceId);
    memberRow.put("role", 1);
    memberRow.put("isFinish", 0);

    Map<String, Object> body = new HashMap<>();
    body.put("trainDto", trainRow);
    body.put("pageDto", List.of());
    body.put("userDto", List.of(memberRow));
    body.put("userValueDto", List.of());
    body.put("moreDto", List.of());
    body.put("users", List.of(userRow));
    return body;
  }

  private void importAs(UserEntity actor, Map<String, Object> body) {
    given()
        .contentType(ContentType.JSON)
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .body(body)
        .when()
        .post("/api/generalKeyPat/importTrainInfo")
        .then()
        .statusCode(200)
        .body("code", is(200));
  }

  private UserEntity user(String prefix) {
    return Fixtures.user(userDao, prefix + "-token-" + UUID.randomUUID(),
        prefix + "-device-" + UUID.randomUUID());
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
