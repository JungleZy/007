package com.nip.controller;

import com.nip.dao.PostTelegramTrainDao;
import com.nip.dao.PostTelegraphKeyPatTrainDao;
import com.nip.dao.PostTelexPatTrainDao;
import com.nip.dao.UserDao;
import com.nip.entity.PostTelegramTrainEntity;
import com.nip.entity.PostTelegraphKeyPatTrainEntity;
import com.nip.entity.PostTelexPatTrainEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 训练属主拒绝的对外契约：身份成立（token 有效）但不是创建者时，必须是
 * HTTP 200 + {@code code:207}，而不是与「参数不合法/目标不存在」同码的 202。
 *
 * <p>这条是消费者可见的：前端按 {@code code} 分支决定「提示无权限」还是「提示参数错误并引导重试」，
 * 两者混码会让越权操作被当成可重试的参数问题。
 *
 * <p>同时断言被拒绝的资源仍在库中——授权拒绝不得产生副作用。
 */
@QuarkusTest
class TrainOwnershipAuthorizationTest {
  @Inject UserDao userDao;
  @Inject PostTelegramTrainDao telegramTrainDao;
  @Inject PostTelegraphKeyPatTrainDao keyPatTrainDao;
  @Inject PostTelexPatTrainDao telexTrainDao;

  @Test
  void nonOwnerGetsForbiddenOnPersonalHandkeyTrain() {
    UserEntity owner = Fixtures.user(userDao, "owner-telegram-" + UUID.randomUUID(), "device-owner");
    UserEntity intruder = Fixtures.user(userDao, "intruder-telegram-" + UUID.randomUUID(), "device-intruder");

    PostTelegramTrainEntity train = new PostTelegramTrainEntity();
    train.setCreateUser(owner.getId());
    train.setName("ownership-telegram");
    // 采集协议版本与轮次是 NOT NULL 列（迁移 2026-09-11-04-personal-handkey-capture.sql:80-81），
    // 实体已声明 @Column(nullable = false)；播种漏设会在 flush 时就被 Hibernate 拦住。
    train.setProtocolVersion(1);
    train.setAttempt(0);
    telegramTrainDao.save(train);

    given()
        .contentType(ContentType.JSON)
        .header("token", intruder.getToken())
        .header("deviceId", intruder.getDeviceId())
        .body(Map.of("id", train.getId()))
        .when()
        .post("/api/postTelegramTrain/detail")
        .then()
        .statusCode(200)
        .body("code", is(207));

    assertNotNull(telegramTrainDao.findById(train.getId()), "授权拒绝不得删除或改动训练行");
  }

  @Test
  void nonOwnerGetsForbiddenOnPersonalElectronicTrain() {
    UserEntity owner = Fixtures.user(userDao, "owner-keypat-" + UUID.randomUUID(), "device-owner");
    UserEntity intruder = Fixtures.user(userDao, "intruder-keypat-" + UUID.randomUUID(), "device-intruder");

    PostTelegraphKeyPatTrainEntity train = new PostTelegraphKeyPatTrainEntity();
    train.setCreateUserId(owner.getId());
    train.setTitle("ownership-keypat");
    keyPatTrainDao.save(train);

    given()
        .contentType(ContentType.JSON)
        .header("token", intruder.getToken())
        .header("deviceId", intruder.getDeviceId())
        .body(Map.of("id", train.getId()))
        .when()
        .post("/api/PostTelegraphKeyPatTrain/details")
        .then()
        .statusCode(200)
        .body("code", is(207));

    assertNotNull(keyPatTrainDao.findById(train.getId()), "授权拒绝不得删除或改动训练行");
  }

  @Test
  void nonOwnerGetsForbiddenOnPersonalTelexTrain() {
    UserEntity owner = Fixtures.user(userDao, "owner-telex-" + UUID.randomUUID(), "device-owner");
    UserEntity intruder = Fixtures.user(userDao, "intruder-telex-" + UUID.randomUUID(), "device-intruder");

    PostTelexPatTrainEntity train = new PostTelexPatTrainEntity();
    train.setCreateUser(owner.getId());
    train.setName("ownership-telex");
    telexTrainDao.save(train);

    given()
        .contentType(ContentType.JSON)
        .header("token", intruder.getToken())
        .header("deviceId", intruder.getDeviceId())
        .body(Map.of("id", train.getId()))
        .when()
        .post("/api/postTelexPatTrain/detail")
        .then()
        .statusCode(200)
        .body("code", is(207));

    assertNotNull(telexTrainDao.findById(train.getId()), "授权拒绝不得删除或改动训练行");
  }

  @Test
  void missingTrainStaysParameterErrorInsteadOfForbidden() {
    UserEntity actor = Fixtures.user(userDao, "missing-telex-" + UUID.randomUUID(), "device-actor");

    given()
        .contentType(ContentType.JSON)
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .body(Map.of("id", "no-such-train-" + UUID.randomUUID()))
        .when()
        .post("/api/postTelexPatTrain/detail")
        .then()
        .statusCode(200)
        .body("code", is(202));
  }
}
