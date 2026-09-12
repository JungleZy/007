package com.nip.controller;

import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * 用户目录端点（SEC-08）的对外契约：
 *
 * <ol>
 *   <li>返回完整档案（含 idCard/phone/email）的端点一律只对管理员开放，学员得 {@code code:207}；</li>
 *   <li>学员可达的目录端点只回 {@code id/userName/userAccount/userImg}，不得泄露身份证与电话；</li>
 *   <li>管理员走管理端点仍能拿到完整档案；</li>
 *   <li>批量按 id 取用户时，正则元字符不再参与匹配（旧实现把 id 拼成 {@code REGEXP} 模式），
 *       空 id 列表返回空集而非全表。</li>
 * </ol>
 */
@QuarkusTest
class UserDirectoryAuthorizationTest {
  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;

  @Test
  void studentCannotReachDirectoryEndpointsExposingFullProfile() {
    UserEntity actor = student("dir-student");
    UserEntity victim = student("dir-victim");

    as(actor).when().post("/api/user/getAllUser")
        .then().statusCode(200).body("code", is(207));
    as(actor).when().post("/api/user/getUserInfoAllByStatusDesc")
        .then().statusCode(200).body("code", is(207));
    as(actor).body(Map.of("userId", victim.getId())).when().post("/api/user/getUserById")
        .then().statusCode(200).body("code", is(207));
    as(actor).body(Map.of("userId", victim.getId())).when().post("/api/user/getUserAndRoleById")
        .then().statusCode(200).body("code", is(207));
  }

  @Test
  void studentReachableDirectoryEndpointsHideIdCardAndPhone() {
    UserEntity actor = withContactDetails(student("dir-open"));

    as(actor).when().post("/api/user/getUserDirectory")
        .then().statusCode(200).body("code", is(200))
        .body("data.id", hasItem(actor.getId()))
        .body("data.idCard", everyItem(nullValue()))
        .body("data.phone", everyItem(nullValue()))
        .body("data.email", everyItem(nullValue()));

    as(actor).body(Map.of("userName", actor.getUserName())).when()
        .post("/api/user/getUsersByUserNameStartingWith")
        .then().statusCode(200).body("code", is(200))
        .body("data.id", hasItem(actor.getId()))
        .body("data.idCard", everyItem(nullValue()))
        .body("data.phone", everyItem(nullValue()))
        .body("data.email", everyItem(nullValue()));

    as(actor).body(List.of(actor.getId())).when().post("/api/user/getUsersByIds")
        .then().statusCode(200).body("code", is(200))
        .body("data.id", contains(actor.getId()))
        .body("data.userAccount", contains(actor.getUserAccount()))
        .body("data.idCard", everyItem(nullValue()))
        .body("data.phone", everyItem(nullValue()))
        .body("data.email", everyItem(nullValue()));
  }

  @Test
  void adminStillReadsFullProfileThroughManagementEndpoints() {
    UserEntity admin = admin("dir-admin");
    UserEntity target = withContactDetails(student("dir-target"));

    as(admin).body(Map.of("userId", target.getId())).when().post("/api/user/getUserById")
        .then().statusCode(200).body("code", is(200))
        .body("data.idCard", is(target.getIdCard()))
        .body("data.phone", is(target.getPhone()))
        .body("data.email", is(target.getEmail()));

    as(admin).when().post("/api/user/getUserInfoAllByStatusDesc")
        .then().statusCode(200).body("code", is(200))
        .body("data.findAll { it.id == '" + target.getId() + "' }.idCard", contains(target.getIdCard()));
  }

  @Test
  void batchLookupTreatsRegexpMetacharactersAsPlainIds() {
    UserEntity actor = student("dir-regexp");
    UserEntity other = student("dir-regexp-other");

    // ".*" 在旧的 REGEXP 拼接下命中全表
    as(actor).body(List.of(".*")).when().post("/api/user/getUsersByIds")
        .then().statusCode(200).body("code", is(200)).body("data", hasSize(0));

    // "a|b" 在旧实现里是「或」，参数化后只是一个不存在的 id
    as(actor).body(List.of(actor.getId() + "|" + other.getId())).when().post("/api/user/getUsersByIds")
        .then().statusCode(200).body("code", is(200)).body("data", hasSize(0));

    // 掺入元字符不得扩大结果集：返回集恰为传入的真实 id
    as(actor).body(List.of(actor.getId(), ".*")).when().post("/api/user/getUsersByIds")
        .then().statusCode(200).body("code", is(200))
        .body("data.id", contains(actor.getId()));

    as(actor).body(List.of(actor.getId(), other.getId())).when().post("/api/user/getUsersByIds")
        .then().statusCode(200).body("code", is(200))
        .body("data.id", containsInAnyOrder(actor.getId(), other.getId()));
  }

  @Test
  void batchLookupWithEmptyIdListReturnsEmptySetInsteadOfEveryUser() {
    UserEntity actor = student("dir-empty");

    as(actor).body(List.of()).when().post("/api/user/getUsersByIds")
        .then().statusCode(200).body("code", is(200)).body("data", hasSize(0));
  }

  private RequestSpecification as(UserEntity user) {
    return given()
        .contentType(ContentType.JSON)
        .header("token", user.getToken())
        .header("deviceId", user.getDeviceId());
  }

  private UserEntity withContactDetails(UserEntity user) {
    // Fixtures 返回的 token 是明文，回写这一行前必须换回摘要，否则会把明文刷进 t_user.token
    // 让后续鉴权按摘要查不到人。
    String token = user.getToken();
    user.setIdCard("11010119900101" + String.format("%04d", (UUID.randomUUID().hashCode() & 0x7FFFFFFF) % 10000));
    user.setPhone("139" + String.format("%08d", (UUID.randomUUID().hashCode() & 0x7FFFFFFF) % 100000000));
    user.setEmail(user.getUserAccount() + "@example.com");
    Fixtures.sessionToken(user, token);
    UserEntity saved = userDao.saveAndFlush(user);
    saved.setToken(token);
    return saved;
  }

  private UserEntity student(String prefix) {
    return Fixtures.user(userDao, prefix + "-token-" + UUID.randomUUID(),
        prefix + "-device-" + UUID.randomUUID());
  }

  private UserEntity admin(String prefix) {
    UserEntity user = student(prefix);
    RoleEntity role = new RoleEntity();
    role.setTitle("role-" + UUID.randomUUID());
    role.setIsAdmin(0);
    role.setIsDefault(1);
    role = roleDao.saveAndFlush(role);

    UserRoleEntity link = new UserRoleEntity();
    link.setUserId(user.getId());
    link.setRoleId(role.getId());
    userRoleDao.saveAndFlush(link);
    return user;
  }
}
