package com.nip.controller;

import com.nip.dao.PostTickerTapeTrainSettingDao;
import com.nip.dao.PostTrainGlobalRuleDao;
import com.nip.dao.RoleDao;
import com.nip.dao.TelegraphKeyPatSyntheticalDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.entity.PostTickerTapeTrainSettingEntity;
import com.nip.entity.PostTrainGlobalRuleEntity;
import com.nip.entity.RoleEntity;
import com.nip.entity.TelegraphKeyPatSyntheticalEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 四个原本完全没有类级 {@code @JWT} 的控制器（评分规则 / 收报速率配置 / 电子键综合训练 / 固定报文报底）
 * 的对外授权契约：
 *
 * <ol>
 *   <li>匿名（不带 token 头）一律 {@code code:203}；</li>
 *   <li>全局配置类写端点只有管理员可写，普通学员 {@code code:207} 且库中数据不变；</li>
 *   <li>读端点不加管理员门禁——加了会打断学员正常训练；</li>
 *   <li>综合训练的结算端点按 token 推导身份，非属主 {@code code:207}，学员本人不受影响。</li>
 * </ol>
 */
@QuarkusTest
class ControllerAuthorizationGuardTest {
  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;
  @Inject PostTrainGlobalRuleDao ruleDao;
  @Inject PostTickerTapeTrainSettingDao settingDao;
  @Inject TelegraphKeyPatSyntheticalDao syntheticalDao;

  @Test
  void anonymousRequestsAreRejectedOnAllFourControllers() {
    anonymous("/api/postTrainGlobalRule/findByType", Map.of("type", 0));
    anonymous("/api/postTickerTapeTrainSetting/findAll", Map.of());
    anonymous("/api/telegraphKeyPatTrainSynthetical/findById", Map.of("id", "anonymous-probe"));
    anonymous("/api/cable/floor/find", Map.of("id", "anonymous-probe"));
  }

  @Test
  void ordinaryUserCannotWriteGlobalRuleButStillReadsIt() {
    UserEntity student = student("rule-student");
    int probeType = 900;

    given()
        .contentType(ContentType.JSON)
        .header("token", student.getToken())
        .header("deviceId", student.getDeviceId())
        .body(List.of(Map.of("type", probeType, "level", "甲", "accuracy", "95", "description", "越权写入")))
        .when()
        .post("/api/postTrainGlobalRule/addRule")
        .then()
        .statusCode(200)
        .body("code", is(207));

    assertEquals(0, ruleDao.count("type", probeType), "授权拒绝不得写入规则行");

    // 读端点必须仍然可用：学员训练页要靠它拿评分档位。
    given()
        .contentType(ContentType.JSON)
        .header("token", student.getToken())
        .header("deviceId", student.getDeviceId())
        .body(Map.of("type", probeType))
        .when()
        .post("/api/postTrainGlobalRule/findByType")
        .then()
        .statusCode(200)
        .body("code", is(200));
  }

  @Test
  void ordinaryUserCannotDeleteGlobalRule() {
    UserEntity student = student("rule-deleter");
    PostTrainGlobalRuleEntity rule = new PostTrainGlobalRuleEntity();
    rule.setType(910);
    rule.setLevel("乙");
    rule.setAccuracy("80");
    rule.setDescription("待保护规则");
    ruleDao.saveAndFlush(rule);

    given()
        .contentType(ContentType.JSON)
        .header("token", student.getToken())
        .header("deviceId", student.getDeviceId())
        .body(Map.of("id", rule.getId()))
        .when()
        .post("/api/postTrainGlobalRule/deleteById")
        .then()
        .statusCode(200)
        .body("code", is(207));

    assertEquals(1, ruleDao.count("id", rule.getId()), "授权拒绝不得删除规则行");
  }

  @Test
  void adminCanWriteGlobalRule() {
    UserEntity admin = admin("rule-admin");
    int probeType = 800;

    given()
        .contentType(ContentType.JSON)
        .header("token", admin.getToken())
        .header("deviceId", admin.getDeviceId())
        .body(List.of(Map.of("type", probeType, "level", "甲", "accuracy", "95", "description", "管理员写入")))
        .when()
        .post("/api/postTrainGlobalRule/addRule")
        .then()
        .statusCode(200)
        .body("code", is(200));

    assertEquals(1, ruleDao.count("type", probeType), "管理员写入应当落库");
  }

  @Test
  void onlyAdminCanReplaceTickerTapeRateSetting() {
    UserEntity student = student("rate-student");
    UserEntity admin = admin("rate-admin");
    Map<String, Object> payload = Map.of("paramList",
        List.of(Map.of("type", "1", "rate", 120, "text", "码速档位")));
    // 先落一行既有配置：越权写入必须既不新增也不清空它。
    PostTickerTapeTrainSettingEntity existing = new PostTickerTapeTrainSettingEntity();
    existing.setType("0");
    existing.setRate(60);
    existing.setText("既有档位");
    settingDao.saveAndFlush(existing);
    long before = settingDao.count();

    given()
        .contentType(ContentType.JSON)
        .header("token", student.getToken())
        .header("deviceId", student.getDeviceId())
        .body(payload)
        .when()
        .post("/api/postTickerTapeTrainSetting/addOrUpdate")
        .then()
        .statusCode(200)
        .body("code", is(207));

    // addOrUpdate 的实现是「先 deleteAll 再整表重写」，越权调用一旦漏过会清空全局码速配置。
    assertEquals(before, settingDao.count(), "授权拒绝不得清空码速配置");

    // 学员仍要能读码速档位，否则收报训练页开不起来。
    given()
        .contentType(ContentType.JSON)
        .header("token", student.getToken())
        .header("deviceId", student.getDeviceId())
        .when()
        .post("/api/postTickerTapeTrainSetting/findAll")
        .then()
        .statusCode(200)
        .body("code", is(200));

    given()
        .contentType(ContentType.JSON)
        .header("token", admin.getToken())
        .header("deviceId", admin.getDeviceId())
        .body(payload)
        .when()
        .post("/api/postTickerTapeTrainSetting/addOrUpdate")
        .then()
        .statusCode(200)
        .body("code", is(200));

    assertEquals(1, settingDao.count(), "管理员写入应当落库");
  }

  @Test
  void nonOwnerCannotSettleSyntheticalTrain() {
    UserEntity owner = student("synth-owner");
    UserEntity intruder = student("synth-intruder");

    TelegraphKeyPatSyntheticalEntity train = new TelegraphKeyPatSyntheticalEntity()
        .setCreateUserId(owner.getId())
        .setTitle("synthetical-ownership")
        .setStatus(1)
        .setSpeed("0")
        .setDuration("0")
        .setAccuracy(0.0)
        .setErrorNumber(0);
    syntheticalDao.saveAndFlush(train);

    given()
        .contentType(ContentType.JSON)
        .header("token", intruder.getToken())
        .header("deviceId", intruder.getDeviceId())
        .body(Map.of("id", train.getId(), "totalNumber", 100, "speed", "999", "duration", "1"))
        .when()
        .post("/api/telegraphKeyPatTrainSynthetical/finish")
        .then()
        .statusCode(200)
        .body("code", is(207));

    assertEquals(1, syntheticalDao.findById(train.getId()).getStatus(),
        "授权拒绝不得把别人的训练改成已完成");

    // 属主本人不受影响：这条端点是学员自己的结算路径，不能退化成管理员专属。
    given()
        .contentType(ContentType.JSON)
        .header("token", owner.getToken())
        .header("deviceId", owner.getDeviceId())
        .body(Map.of("id", train.getId(), "totalNumber", 100, "speed", "30", "duration", "60"))
        .when()
        .post("/api/telegraphKeyPatTrainSynthetical/finish")
        .then()
        .statusCode(200)
        .body("code", is(200));

    assertEquals(3, syntheticalDao.findById(train.getId()).getStatus(), "属主结算应当写入完成态");
  }

  private static void anonymous(String path, Map<String, ?> body) {
    given()
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post(path)
        .then()
        .statusCode(200)
        .body("code", is(203));
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
