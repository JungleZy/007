package com.nip.service;

import com.nip.common.constants.PostEnteringExerciseTypeEnum;
import com.nip.dao.PostEnteringExerciseDao;
import com.nip.dao.PostEnteringExerciseWordStockDao;
import com.nip.dao.UserDao;
import com.nip.entity.PostEnteringExerciseWordStockEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 军语类训练（JYCZ / TZYY）取默认文章时走 {@code wordStockDao.findByType}，它是
 * {@code firstResult()}，词库里没有该 type 的行就返回 null。原先立即 {@code .getContent()}，
 * 主数据没铺好的部署会以 NPE 逸出成 {@code code=500 "服务器错误"}，调用方看不出缺了什么。
 *
 * <p>本用例断言的可观察契约：缺该 type 的词库时，HTTP 仍是 200，业务码是参数错误 202，
 * 且 message 点名是哪个类型的词库缺失；同时被拒的请求一行训练都不落库。
 */
@QuarkusTest
class PostEnteringExerciseWordStockGuardTest {

  @Inject UserDao userDao;
  @Inject PostEnteringExerciseDao exerciseDao;
  @Inject PostEnteringExerciseWordStockDao wordStockDao;

  private UserEntity trainee;

  @BeforeEach
  void seed() {
    trainee = Fixtures.user(userDao, UUID.randomUUID().toString(), "entering-word-stock-" + UUID.randomUUID());
  }

  @AfterEach
  void cleanup() {
    QuarkusTransaction.requiringNew().run(() -> {
      exerciseDao.delete("createUserId = ?1", trainee.getId());
      userDao.deleteById(trainee.getId());
    });
  }

  @Test
  void addPinyinMilitaryTermWithoutWordStockIsRejectedAsNamedParameterError() {
    assertMissingWordStockIsNamed(PostEnteringExerciseTypeEnum.JYCZ);
  }

  @Test
  void addWubiMilitaryTermWithoutWordStockIsRejectedAsNamedParameterError() {
    assertMissingWordStockIsNamed(PostEnteringExerciseTypeEnum.TZYY);
  }

  @Test
  void addMilitaryTermWithConfiguredWordStockStillTakesTheDefaultArticle() {
    String content = "默认拼音军语正文-" + UUID.randomUUID();
    QuarkusTransaction.requiringNew().run(() -> {
      PostEnteringExerciseWordStockEntity stock = new PostEnteringExerciseWordStockEntity();
      stock.setType(PostEnteringExerciseTypeEnum.JYCZ.getCode());
      stock.setName("默认军语词库-" + UUID.randomUUID());
      stock.setContent(content);
      wordStockDao.save(stock);
    });

    request().body(body(PostEnteringExerciseTypeEnum.JYCZ))
        .post("/api/postEnteringExercise/add")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.content", is(content));
  }

  /**
   * 词库表在别处没有播种入口（全仓仅本用例触及），但同类用例之间无清理，
   * 正常路径那条会留下一行 JYCZ 词库，所以这里先按 type 清场再断言，用例顺序无关。
   */
  private void assertMissingWordStockIsNamed(PostEnteringExerciseTypeEnum type) {
    QuarkusTransaction.requiringNew().run(() -> wordStockDao.delete("type = ?1", type.getCode()));

    request().body(body(type))
        .post("/api/postEnteringExercise/add")
        .then().statusCode(200)
        .body("code", is(202))
        .body("message", containsString(type.getName()))
        .body("message", containsString("type=" + type.getCode()));

    assertEquals(0, exerciseDao.count("createUserId = ?1", trainee.getId()),
        "词库缺失被拒时不得落下训练行");
  }

  private Map<String, Object> body(PostEnteringExerciseTypeEnum type) {
    return Map.of("type", type.getCode(), "name", "录入练习-" + UUID.randomUUID());
  }

  private RequestSpecification request() {
    return given().header("token", trainee.getToken()).header("deviceId", trainee.getDeviceId())
        .contentType("application/json");
  }
}
