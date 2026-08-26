package com.nip.common.exception;

import com.nip.dao.UserDao;
import com.nip.service.TelexPatTrainService;
import com.nip.service.UserService;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 4 异常边界集成测试：JWTInterceptor 收窄（Task 4.3）后，
 * 端点异常由 common/exception 下的 ExceptionMapper 接管（Task 4.1）。
 */
@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class ExceptionBoundaryTest {
  private static final String TOKEN = "boundary-token";
  private static final String DEVICE = "boundary-device";

  @Inject
  UserDao userDao;
  @Inject
  UserService userService;
  @Inject
  TelexPatTrainService telexPatTrainService;

  @BeforeEach
  void seedUser() {
    // 拦截器 response.send 的裸 JSON 未带 Content-Type，显式指定默认解析器
    RestAssured.defaultParser = Parser.JSON;
    if (userDao.findUserEntityByToken(TOKEN) == null) {
      Fixtures.user(userDao, TOKEN, DEVICE);
    }
  }

  @Test
  void missingTokenOnJwtEndpointReturns203Envelope() {
    // 拦截器自身校验保留：无 token → HTTP 200 + code 203
    given()
        .header("Origin", "http://localhost")
        .header("deviceId", DEVICE)
        .when().get("/api/menus/getMenusAll")
        .then().statusCode(200)
        .body("code", is(203));
  }

  @Test
  void unknownTokenOnNonJwtEndpointReturns203Envelope() {
    // Task 4.2：非 @JWT 的 DeviceController 路径，getUserByToken 抛 UnauthorizedException
    // → UnauthorizedExceptionMapper：HTTP 200 + code 203（原为 NPE→500）
    given()
        .header("Origin", "http://localhost")
        .header("token", "expired-token-nowhere")
        .contentType("application/json")
        .body("{}")
        .when().post("/api/device/save")
        .then().statusCode(200)
        .body("code", is(203));
  }

  @Test
  void getUserByTokenThrowsUnauthorizedForUnknownToken() {
    assertThrows(UnauthorizedException.class, () -> userService.getUserByToken("expired-token-nowhere"));
  }

  @Test
  void unauthorizedRethrownThroughLegacyCatchAll() {
    // 4.2 步骤 2：位于 try/catch(Exception)→error() 内的调用点必须重抛 203，而不是被吞成通用错误
    assertThrows(UnauthorizedException.class,
        () -> telexPatTrainService.findTexPatTrainByToken("expired-token-nowhere"));
  }

  @Test
  void expiredTokenOnJwtEndpointReturns206Envelope() {
    // 库中不存在的（过期）token → 拦截器 HTTP 200 + code 206
    given()
        .header("Origin", "http://localhost")
        .header("token", "expired-token-nowhere")
        .header("deviceId", DEVICE)
        .when().get("/api/menus/getMenusAll")
        .then().statusCode(200)
        .body("code", is(206));
  }

  @Test
  void validationFailureOnJwtEndpointKeeps200WithOriginalMessage() {
    // 校验失败（permissions=null 的 addMenu）→ ValidationExceptionMapper 接管：
    // HTTP 200 + CODE_500 + 原提示消息（不再被拦截器兜成 SYSTEM_ERROR）
    given()
        .header("Origin", "http://localhost")
        .header("token", TOKEN)
        .header("deviceId", DEVICE)
        .contentType("application/json")
        .body("{\"menus\":{\"title\":\"px\"},\"permissions\":null}")
        .when().post("/api/menus/addMenu")
        .then().statusCode(200)
        .body("code", is(500))
        .body("message", equalTo("permissions 缺失，拒绝编辑菜单权限"));
  }

  @Test
  void unknownExceptionOnJwtEndpointReturns500SystemErrorEnvelope() {
    // 未知异常（menus=null → NPE）→ GlobalExceptionMapper：HTTP 500 + SYSTEM_ERROR 信封
    given()
        .header("Origin", "http://localhost")
        .header("token", TOKEN)
        .header("deviceId", DEVICE)
        .contentType("application/json")
        .body("{\"permissions\":[]}")
        .when().post("/api/menus/addMenu")
        .then().statusCode(500)
        .body("code", is(500))
        .body("message", equalTo("服务器错误"));
  }

  @Test
  void unknownPathKeeps404NotHijackedByGlobalMapper() {
    // 锁定：WebApplicationException（未匹配路径的 NotFound）不被 GlobalExceptionMapper 劫持成
    // 500/SYSTEM_ERROR 信封，HTTP 404 原样返回
    given()
        .header("Origin", "http://localhost")
        .when().get("/api/no-such-endpoint-anywhere")
        .then().statusCode(404);
  }
}
