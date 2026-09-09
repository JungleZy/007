package com.nip.common.exception;

import com.nip.dao.UserDao;
import com.nip.dao.MilitaryTermDataDao;
import com.nip.entity.MilitaryTermDataEntity;
import com.nip.dao.RoleDao;
import com.nip.dao.UserRoleDao;
import com.nip.service.TelexPatTrainService;
import com.nip.service.UserService;
import com.nip.testsupport.Fixtures;


import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 异常边界集成测试：JWTInterceptor 收窄（Task 4.3）后，
 * 端点异常由 common/exception 下的 ExceptionMapper 接管（Task 4.1）。
 */
@QuarkusTest

class ExceptionBoundaryTest {
  private static final String TOKEN = "boundary-token";
  private static final String DEVICE = "boundary-device";

  @Inject
  UserDao userDao;
  @Inject
  UserService userService;
  @Inject
  TelexPatTrainService telexPatTrainService;
  @Inject
  MilitaryTermDataDao militaryTermDataDao;
  @Inject
  RoleDao roleDao;
  @Inject
  UserRoleDao userRoleDao;
  @BeforeEach
  void seedUser() {
    // 其它 @QuarkusTest 类的同款约定；本类的鉴权信封现已带 application/json（Task 7.2）
    RestAssured.defaultParser = Parser.JSON;
    com.nip.entity.UserEntity boundaryUser = userDao.findUserEntityByToken(TOKEN);
    if (boundaryUser == null) {
      boundaryUser = Fixtures.user(userDao, TOKEN, DEVICE);
    }
    if (userRoleDao.findByUserId(boundaryUser.getId()) == null) {
      com.nip.entity.RoleEntity adminRole = new com.nip.entity.RoleEntity();
      adminRole.setTitle("boundary-admin");
      adminRole.setIsAdmin(0);
      adminRole.setIsDefault(1);
      adminRole = roleDao.save(adminRole);
      com.nip.entity.UserRoleEntity link = new com.nip.entity.UserRoleEntity();
      link.setUserId(boundaryUser.getId());
      link.setRoleId(adminRole.getId());
      userRoleDao.save(link);
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
  void missingDeviceIdOnJwtEndpointReturns204Envelope() {
    // 拦截器第三个序列化点：有 token 无 deviceId → HTTP 200 + code 204
    given()
        .header("Origin", "http://localhost")
        .header("token", TOKEN)
        .when().get("/api/menus/getMenusAll")
        .then().statusCode(200)
        .body("code", is(204));
  }

  @Test
  void unknownTokenOnJwtEndpointReturns206Envelope() {
    given()
        .header("Origin", "http://localhost")
        .header("token", "expired-token-nowhere")
        .header("deviceId", "device-nowhere")
        .contentType("application/json")
        .body("{}")
        .when().post("/api/device/save")
        .then().statusCode(200)
        .body("code", is(206));
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
  void businessEmptyLoginParamsUses202InsteadOfAuth204() {
    given()
        .header("Origin", "http://localhost")
        .contentType("application/json")
        .body("{}")
        .when().post("/api/user/login")
        .then().statusCode(200)
        .body("code", is(202));
  }

  @Test
  void businessEmptyChangePasswordParamsUses202WithValidSession() {
    given()
        .header("Origin", "http://localhost")
        .header("token", TOKEN)
        .header("deviceId", DEVICE)
        .contentType("application/json")
        .body("{\"oldPassword\":\"\",\"newPassword\":\"new\",\"newPasswordV\":\"new\"}")
        .when().post("/api/user/changePassword")
        .then().statusCode(200)
        .body("code", is(202));
  }
  @Test
  void validationFailureOnJwtEndpointKeeps200WithOriginalMessage() {
    // 校验失败（permissions=null 的 addMenu）→ ValidationExceptionMapper 接管：
    // HTTP 200 + 业务码 202 + 原业务提示
    given()
        .header("Origin", "http://localhost")
        .header("token", TOKEN)
        .header("deviceId", DEVICE)
        .contentType("application/json")
        .body("{\"menus\":{\"title\":\"px\"},\"permissions\":null}")
        .when().post("/api/menus/addMenu")
        .then().statusCode(200)
        .body("code", is(202))
        .body("message", equalTo("permissions 缺失，拒绝编辑菜单权限"));
  }

  @Test
  void insufficientMilitaryTermOptionsSurfaceAsCode500Envelope() {
    // 终审 I-1：generateTestPaper 的 IAE（有效题目不足4条）必须穿透 add 的 catch(Exception) 包裹，
    // 由 ValidationExceptionMapper 以 HTTP 200 + 业务码 202 + 原提示送达，而非 RuntimeException → HTTP 500
    String parentId = "boundary-term-parent";
    if (militaryTermDataDao.findAllByParentIdIn(List.of(parentId)).isEmpty()) {
      // 4 条同类型但仅 3 个互异 value：通过 add 的 size>=4 类型过滤，命中 generateTestPaper 的 distinct<4 校验
      militaryTermDataDao.save(new MilitaryTermDataEntity().setParentId(parentId).setKey("甲").setValue("甲值"));
      militaryTermDataDao.save(new MilitaryTermDataEntity().setParentId(parentId).setKey("乙").setValue("乙值"));
      militaryTermDataDao.save(new MilitaryTermDataEntity().setParentId(parentId).setKey("丙").setValue("丙值"));
      militaryTermDataDao.save(new MilitaryTermDataEntity().setParentId(parentId).setKey("丁").setValue("甲值"));
    }
    given()
        .header("Origin", "http://localhost")
        .header("token", TOKEN)
        .header("deviceId", DEVICE)
        .contentType("application/json")
        .body("{\"types\":[\"" + parentId + "\"],\"totalNumber\":1,\"name\":\"boundary\"}")
        .when().post("/api/postMilitaryTermTrain/add")
        .then().statusCode(200)
        .body("code", is(202))
        .body("message", equalTo("类型 " + parentId + " 有效题目不足4条，无法生成干扰项"));
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

  @Test
  void rejectedTokenEnvelopeIsJsonAndCarriesNoHandRolledCorsHeaders() {
    // Task 7.2：拦截器不再往 HttpServerResponse 直写裸 JSON + return null（双写），
    // 改抛 WebApplicationException 由 mapper 单写 → 响应带 application/json 与完整信封；
    // 手写 CORS 已删除，预检专用头不得再出现在普通响应上
    io.restassured.response.Response resp = given()
        .header("Origin", "http://localhost")
        .header("deviceId", DEVICE)
        .when().get("/api/menus/getMenusAll");

    assertEquals(200, resp.statusCode());
    assertTrue(resp.contentType().startsWith("application/json"), resp.contentType());
    assertEquals(203, resp.jsonPath().getInt("code"));
    assertEquals("token不能为空", resp.jsonPath().getString("message"));
    assertNull(resp.getHeader("Access-Control-Max-Age"),
        "预检专用头不得出现在普通响应上（JWTInterceptor 手写 CORS 已删除）");
  }

  @Test
  void validationMessageKeepsBusinessTextAndDropsExceptionNoise() {
    // Task 7.1：对外消息只保留首行业务文案——业务提示原样透出（既有契约），
    // 异常类名前缀与堆栈片段剥掉，空消息兜底，超长消息截断
    assertEquals("组数不能为空", ValidationExceptionMapper.safeMessage("组数不能为空"));
    assertEquals("组数不能为空",
        ValidationExceptionMapper.safeMessage("java.lang.IllegalArgumentException: 组数不能为空"));
    assertEquals("页码不正确", ValidationExceptionMapper.safeMessage(
        "java.lang.IllegalStateException: java.lang.IllegalArgumentException: 页码不正确"));
    assertEquals("boom", ValidationExceptionMapper.safeMessage("boom\n\tat com.nip.Foo.bar(Foo.java:1)"));
    assertEquals(ValidationExceptionMapper.FALLBACK_MESSAGE, ValidationExceptionMapper.safeMessage(null));
    assertEquals(ValidationExceptionMapper.FALLBACK_MESSAGE,
        ValidationExceptionMapper.safeMessage("java.lang.NullPointerException: "));
    assertEquals(ValidationExceptionMapper.MAX_MESSAGE_LENGTH + 1,
        ValidationExceptionMapper.safeMessage("x".repeat(300)).length(),
        "无界入参不得原样回显");
  }
}
