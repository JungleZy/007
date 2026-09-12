package com.nip.controller;

import com.nip.dao.UserDao;
import com.nip.dao.general.key.GeneralKeyPatDao;
import com.nip.dao.general.key.GeneralKeyPatUserDao;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.key.GeneralKeyPatEntity;
import com.nip.entity.simulation.key.GeneralKeyPatUserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 综合组训离线导出（{@code /api/generalKeyPat/getTrainInfo(Batch)}）的对外契约。
 *
 * <p>这个读面把训练下**全部**参训者与创建者的用户行序列化给客户端，而 {@code trainId} 是自增可枚举的，
 * 因此两条消费者可见的约束必须成立：
 * <ul>
 *   <li>导出体里不得出现凭据键（{@code password}/{@code token}/{@code deviceId}）——
 *       否则任意已登录账号枚举 {@code trainId} 即可收割他人活动会话；</li>
 *   <li>单点导出对无权者是 {@code code:207}（无权限），批量导出对学员是**空列表**而不是 207 ——
 *       批量按「该用户全部参训行」取 id，含 role=0 学员行，抛异常会让学员的整次导出失败。</li>
 * </ul>
 */
@QuarkusTest
class GeneralKeyPatExportAuthorizationTest {
  @Inject UserDao userDao;
  @Inject GeneralKeyPatDao trainDao;
  @Inject GeneralKeyPatUserDao trainUserDao;

  @Test
  void outsiderCannotExportTrain() {
    UserEntity owner = user("export-owner");
    UserEntity outsider = user("export-outsider");
    GeneralKeyPatEntity train = train(owner);

    given()
        .header("token", outsider.getToken())
        .header("deviceId", outsider.getDeviceId())
        .queryParam("trainId", train.getId())
        .when()
        .get("/api/generalKeyPat/getTrainInfo")
        .then()
        .statusCode(200)
        .body("code", is(207))
        .body("data", org.hamcrest.Matchers.nullValue());
  }

  @Test
  void creatorExportCarriesNoCredentialFields() {
    UserEntity owner = user("export-creator");
    GeneralKeyPatEntity train = train(owner);

    List<Map<String, Object>> exported = given()
        .header("token", owner.getToken())
        .header("deviceId", owner.getDeviceId())
        .queryParam("trainId", train.getId())
        .when()
        .get("/api/generalKeyPat/getTrainInfo")
        .then()
        .statusCode(200)
        .body("code", is(200))
        .body("data.trainDto.id", is(train.getId()))
        // 创建者本人的用户行确实在导出体里 —— 否则下面的「键缺失」断言是空转
        .body("data.users.userAccount", hasItem(owner.getUserAccount()))
        .extract()
        .path("data.users");

    for (Map<String, Object> row : exported) {
      assertFalse(row.containsKey("password"), "导出用户行不得带口令键");
      assertFalse(row.containsKey("token"), "导出用户行不得带会话 token 键");
      assertFalse(row.containsKey("deviceId"), "导出用户行不得带设备号键");
    }
  }

  @Test
  void groupTrainerCanExportTrain() {
    UserEntity owner = user("export-owner-of-trainer");
    UserEntity trainer = user("export-trainer");
    GeneralKeyPatEntity train = train(owner);
    member(train, trainer, 1);

    given()
        .header("token", trainer.getToken())
        .header("deviceId", trainer.getDeviceId())
        .queryParam("trainId", train.getId())
        .when()
        .get("/api/generalKeyPat/getTrainInfo")
        .then()
        .statusCode(200)
        .body("code", is(200))
        .body("data.trainDto.id", is(train.getId()));
  }

  @Test
  void studentBatchExportIsEmptyInsteadOfForbidden() {
    UserEntity owner = user("export-owner-of-student");
    UserEntity student = user("export-student");
    GeneralKeyPatEntity train = train(owner);
    member(train, student, 0);

    given()
        .header("token", student.getToken())
        .header("deviceId", student.getDeviceId())
        .when()
        .get("/api/generalKeyPat/getTrainInfoBatch")
        .then()
        .statusCode(200)
        .body("code", is(200))
        .body("data", hasSize(0));
  }

  private UserEntity user(String prefix) {
    return Fixtures.user(userDao, prefix + "-token-" + UUID.randomUUID(),
        prefix + "-device-" + UUID.randomUUID());
  }

  private GeneralKeyPatEntity train(UserEntity creator) {
    GeneralKeyPatEntity train = new GeneralKeyPatEntity();
    train.setTitle("export-" + UUID.randomUUID());
    train.setCreateUser(creator.getId());
    train.setStatus(0);
    train.setTrainType(1);
    return trainDao.saveAndFlush(train);
  }

  private void member(GeneralKeyPatEntity train, UserEntity user, int role) {
    GeneralKeyPatUserEntity member = new GeneralKeyPatUserEntity();
    member.setTrainId(train.getId());
    member.setUserId(user.getId());
    member.setRole(role);
    member.setIsFinish(0);
    trainUserDao.saveAndFlush(member);
  }
}
