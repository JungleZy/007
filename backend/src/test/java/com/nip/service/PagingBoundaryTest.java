package com.nip.service;

import com.nip.dao.UserDao;
import com.nip.testsupport.Fixtures;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Task 4.2：page/rows 原来直接透传，前端漏传或传 0 会算出负偏移 / 除零 → HTTP 500。
 * 现在 common.utils.Page 的 getter 统一钳制（page>=1、rows∈[1,200]），
 * 一处改覆盖两类分页机制：Panache Page.of 与自建 SpecificationExecutor.findPage。
 */
@QuarkusTest
class PagingBoundaryTest {
  private static final String TOKEN = "p42-paging-" + UUID.randomUUID();
  private static final String DEVICE = "p42-device-" + UUID.randomUUID();

  /** Panache Page.of 机制：TickerTapeTrainService.listPage */
  private static final String PANACHE_ENDPOINT = "/api/tickerTapeTrain/listPage";
  /** SpecificationExecutor.findPage 机制：PostTelexPatTrainService.findAll */
  private static final String SPECIFICATION_ENDPOINT = "/api/postTelexPatTrain/findAll?trainType=0";

  @Inject
  UserDao userDao;

  @BeforeEach
  void seedUser() {
    RestAssured.defaultParser = Parser.JSON;
    if (userDao.findUserEntityByToken(TOKEN) == null) {
      Fixtures.user(userDao, TOKEN, DEVICE);
    }
  }

  @Test
  void panachePagingClampsMissingAndOutOfRangeBounds() {
    // 空 body：page 默认 0 → 原来 Page.of(-1, 20) 抛 IllegalArgumentException
    post(PANACHE_ENDPOINT, "{}")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.currentPage", is(1))
        .body("data.pageSize", is(20));
    // rows=0 → 原来 Page.ofSize(0) 抛异常
    post(PANACHE_ENDPOINT, "{\"page\":1,\"rows\":0}")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.pageSize", is(1));
    // rows 超大 → 钳到 200，不再让 DB 拉全表
    post(PANACHE_ENDPOINT, "{\"page\":1,\"rows\":1000000}")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.pageSize", is(200));
  }

  @Test
  void specificationPagingClampsMissingAndOutOfRangeBounds() {
    // 空 body：原来 setFirstResult(-20) 抛 IllegalArgumentException
    post(SPECIFICATION_ENDPOINT, "{}")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.currentPage", is(1))
        .body("data.pageSize", is(20));
    // rows=0 → 原来 (total + pageSize - 1) / pageSize 除零
    post(SPECIFICATION_ENDPOINT, "{\"page\":1,\"rows\":0}")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.currentPage", is(1))
        .body("data.pageSize", is(1));
    post(SPECIFICATION_ENDPOINT, "{\"page\":1,\"rows\":1000000}")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.pageSize", is(200));
  }

  private static io.restassured.response.Response post(String path, String body) {
    return request().body(body).when().post(path);
  }

  private static RequestSpecification request() {
    return given()
        .header("Origin", "http://localhost")
        .header("token", TOKEN)
        .header("deviceId", DEVICE)
        .contentType("application/json");
  }
}
