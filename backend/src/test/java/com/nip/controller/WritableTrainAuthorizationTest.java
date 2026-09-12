package com.nip.controller;

import com.nip.dao.PostTickerTapeTrainDao;
import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.dao.general.key.GeneralKeyPatDao;
import com.nip.dao.general.key.GeneralKeyPatUserDao;
import com.nip.entity.PostTickerTapeTrainEntity;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.entity.simulation.key.GeneralKeyPatEntity;
import com.nip.entity.simulation.key.GeneralKeyPatUserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 训练写端点（删除 / 改状态）的授权契约。全仓只有两个口径，本用例把两者的边界都钉住：
 *
 * <ul>
 *   <li><b>组训 / 房间域</b>：创建者 ∪ 该训练内 {@code role=1} 组训人 ∪ 管理员；</li>
 *   <li><b>个人训练域</b>：仅创建者——管理员也不放行，个人训练不存在「代管」场景。</li>
 * </ul>
 *
 * <p>拒绝形态必须是 HTTP 200 + {@code code:207}（身份成立但无权限），与「参数不合法/目标不存在」
 * 的 202 分开：前端按 code 决定是提示无权限还是引导重试。同时断言被拒绝的行仍在库中——
 * 授权拒绝不得留下副作用。
 *
 * <p>{@code organizerCanStopTrainCreatedBySomeoneElse} 是<b>防收窄</b>用例：组训人常常不是建训人，
 * 把写口径压成「仅创建者」会让他停不了自己带的训练，直接打断组训。
 */
@QuarkusTest
class WritableTrainAuthorizationTest {
  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;
  @Inject GeneralKeyPatDao keyTrainDao;
  @Inject GeneralKeyPatUserDao keyTrainUserDao;
  @Inject PostTickerTapeTrainDao tickerTapeTrainDao;

  @Test
  void nonOwnerDeletingGroupTrainIsForbiddenAndRowSurvives() {
    UserEntity owner = user("writable-owner");
    UserEntity intruder = user("writable-intruder");
    GeneralKeyPatEntity train = groupTrain(owner);

    deleteGroupTrain(train, intruder).body("code", is(207));

    assertNotNull(keyTrainDao.findById(train.getId()), "授权拒绝不得删除训练行");
  }

  @Test
  void creatorDeletesOwnGroupTrain() {
    UserEntity owner = user("writable-creator");
    GeneralKeyPatEntity train = groupTrain(owner);

    deleteGroupTrain(train, owner).body("code", is(200)).body("data", is(true));

    assertNull(keyTrainDao.findById(train.getId()), "创建者删除必须真的落库");
  }

  @Test
  void administratorDeletesOthersGroupTrain() {
    UserEntity owner = user("writable-admin-owner");
    UserEntity admin = user("writable-admin");
    attachAdminRole(admin);
    GeneralKeyPatEntity train = groupTrain(owner);

    deleteGroupTrain(train, admin).body("code", is(200)).body("data", is(true));

    assertNull(keyTrainDao.findById(train.getId()), "管理员在组训域属于授权者");
  }

  /**
   * 防收窄：房间内 {@code role=1} 的组训人不是创建者，但必须仍能改训练状态。
   */
  @Test
  void organizerCanStopTrainCreatedBySomeoneElse() {
    UserEntity owner = user("writable-organizer-owner");
    UserEntity organizer = user("writable-organizer");
    GeneralKeyPatEntity train = groupTrain(owner);
    train.setStatus(1).setStartTime(LocalDateTime.now().minusMinutes(1));
    keyTrainDao.saveAndFlush(train);
    keyTrainUserDao.saveAndFlush(new GeneralKeyPatUserEntity()
        .setTrainId(train.getId())
        .setUserId(organizer.getId())
        .setRole(1));

    given()
        .contentType(ContentType.JSON)
        .header("token", organizer.getToken())
        .header("deviceId", organizer.getDeviceId())
        .body(Map.of("trainId", train.getId(), "status", 2))
        .when()
        .post("/api/generalKeyPat/updateTrainStatus")
        .then()
        .statusCode(200)
        .body("code", is(200));

    // 该训练没有未完成的参训人，停训直接结算成 2（已完成）而不是 3（收尾补交）
    assertEquals(2, keyTrainDao.findById(train.getId()).getStatus(), "组训人的停训必须真的改了状态");
  }

  @Test
  void nonOwnerDeletingPersonalTrainIsForbiddenAndRowSurvives() {
    UserEntity owner = user("writable-personal-owner");
    UserEntity intruder = user("writable-personal-intruder");
    PostTickerTapeTrainEntity train = personalTrain(owner);

    deletePersonalTrain(train, intruder).body("code", is(207));

    assertNotNull(tickerTapeTrainDao.findById(train.getId()), "授权拒绝不得删除训练行");
  }

  /**
   * 个人训练域到创建者为止：管理员在这里也是越权方，这是两个口径的分界线。
   */
  @Test
  void administratorCannotDeleteOthersPersonalTrain() {
    UserEntity owner = user("writable-personal-admin-owner");
    UserEntity admin = user("writable-personal-admin");
    attachAdminRole(admin);
    PostTickerTapeTrainEntity train = personalTrain(owner);

    deletePersonalTrain(train, admin).body("code", is(207));

    assertNotNull(tickerTapeTrainDao.findById(train.getId()), "个人训练不对管理员开放写权限");
  }

  private ValidatableResponse deleteGroupTrain(GeneralKeyPatEntity train, UserEntity actor) {
    return given()
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .queryParam("trainId", train.getId())
        .when()
        .get("/api/generalKeyPat/delete")
        .then()
        .statusCode(200);
  }

  private ValidatableResponse deletePersonalTrain(PostTickerTapeTrainEntity train, UserEntity actor) {
    return given()
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .queryParam("trainId", train.getId())
        .when()
        .get("/api/postTickerTapeTrain/delete")
        .then()
        .statusCode(200);
  }

  private UserEntity user(String prefix) {
    return Fixtures.user(userDao, prefix + "-" + UUID.randomUUID(), prefix + "-device-" + UUID.randomUUID());
  }

  private GeneralKeyPatEntity groupTrain(UserEntity owner) {
    return keyTrainDao.saveAndFlush(new GeneralKeyPatEntity()
        .setTitle("writable-group-" + UUID.randomUUID())
        .setCreateUser(owner.getId())
        .setStatus(0));
  }

  private PostTickerTapeTrainEntity personalTrain(UserEntity owner) {
    PostTickerTapeTrainEntity train = new PostTickerTapeTrainEntity();
    train.setName("writable-personal-" + UUID.randomUUID());
    train.setUserId(owner.getId());
    train.setStatus(0);
    return tickerTapeTrainDao.saveAndFlush(train);
  }

  private void attachAdminRole(UserEntity user) {
    RoleEntity role = new RoleEntity();
    role.setTitle("writable-admin-role-" + UUID.randomUUID());
    role.setIsAdmin(0);
    role.setIsDefault(1);
    role = roleDao.saveAndFlush(role);

    UserRoleEntity link = new UserRoleEntity();
    link.setUserId(user.getId());
    link.setRoleId(role.getId());
    userRoleDao.saveAndFlush(link);
  }
}
