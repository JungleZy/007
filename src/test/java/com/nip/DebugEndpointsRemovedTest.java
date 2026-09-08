package com.nip;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Phase 0 回归测试：守「无鉴权/调试写库端点不得再出现」这一契约。
 * 端点被删除后 JAX-RS 抛 NotFoundException，由 WebApplicationExceptionMapper 原样直通 HTTP 404。
 */
@QuarkusTest

class DebugEndpointsRemovedTest {

  @Test
  void unauthenticatedWriteEndpointsAreGone() {
    // 原 controller/test/TestController：无 @JWT 的写库端点
    given()
        .header("Origin", "http://localhost")
        .when().get("/api/test/start")
        .then().statusCode(404);
    // 原 PostTelegramTrainController.test()：覆盖真实报底的调试端点
    given()
        .header("Origin", "http://localhost")
        .when().get("/api/postTelegramTrain/test")
        .then().statusCode(404);
    // 原 free/UserController.test()：返回含凭据实体
    given()
        .header("Origin", "http://localhost")
        .when().post("/api/user/test")
        .then().statusCode(404);
  }
}
