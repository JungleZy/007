package com.nip.common.interceptor;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.Response;
import com.nip.dao.UserDao;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

import static com.nip.common.constants.BaseConstants.DEVICE_ID;
import static com.nip.common.constants.BaseConstants.TOKEN;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * HTTP 鉴权凭据的来源与失败文案契约（SEC-04 续、SEC-11）。
 *
 * <p>两条可观察契约：
 * <ol>
 *   <li>{@link JWTInterceptor} 只从请求头取 {@code token}/{@code deviceId}。原实现在请求头缺失时
 *       回退 {@code HttpServerRequest.getParam(...)}，于是「?token=…&amp;deviceId=…」也能过关 ——
 *       长期有效的凭据因此进入访问日志、Referer 与浏览器历史，且业务查询参数 {@code deviceId}
 *       （{@code deviceScoringRule/findAllByDeviceId}）会顶替会话凭据。</li>
 *   <li>鉴权期异常走 {@code catch (Exception)} 兜底分支，该分支在鉴权完成前即可达（匿名请求就能
 *       触发），因此只能回固定的 {@code SYSTEM_ERROR} 文案，不得把 {@code exception.getMessage()}
 *       原样回传 —— 那会泄露表名/列名/JDBC 片段。</li>
 * </ol>
 *
 * <p>WebSocket 侧不在本类范围内：浏览器的 WebSocket API 无法给握手设置请求头，WS 端点的凭据
 * 只能走 query，两种传输的约束不同。
 *
 * <p>{@code RequireAdminInterceptor} 的同款 query 回退也一并删除，但它没有独立的可观察行为：
 * {@code @RequireAdmin} 恒与类级 {@code @JWT} 同在，且 {@code JWTInterceptor} 优先级更前，
 * 请求头缺 token 时早已被 203 拦下 —— 故本类不为它写用例，避免造出恒真的假测试。
 */
@QuarkusTest
class InterceptorCredentialSourceTest {

  /** 只挂类级 @JWT、无额外门禁、空数据也返回 code:200 的最小端点。 */
  private static final String ENDPOINT = "/api/user/getRecentHandKeyTrains";

  @Inject
  UserDao userDao;

  @Test
  void queryStringCredentialsAreRejectedWhileHeadersPass() {
    String token = "header-only-" + UUID.randomUUID();
    String deviceId = "header-only-device-" + UUID.randomUUID();
    Fixtures.user(userDao, token, deviceId);

    // 凭据只在 query 里：改前会被回退读到并放行，现在必须是「token 不能为空」
    given().queryParam(TOKEN, token).queryParam(DEVICE_ID, deviceId)
        .when().post(ENDPOINT)
        .then().statusCode(200).body("code", is(203));

    // 请求头有 token、deviceId 只在 query：deviceId 不得从 query 补齐
    given().header(TOKEN, token).queryParam(DEVICE_ID, deviceId)
        .when().post(ENDPOINT)
        .then().statusCode(200).body("code", is(204));

    // 两个请求头齐备：正常放行
    given().header(TOKEN, token).header(DEVICE_ID, deviceId)
        .when().post(ENDPOINT)
        .then().statusCode(200).body("code", is(200)).body("data", hasSize(0));
  }

  @Test
  void authFailureEnvelopeHidesDatabaseDetails() throws Exception {
    String leak = "Table 'project006.t_user' doesn't exist: select token,device_id from t_user where token=?";

    JWTInterceptor interceptor = new JWTInterceptor();
    interceptor.request = headerOnlyRequest("tok-" + UUID.randomUUID(), "dev-" + UUID.randomUUID());
    // response 只在 OPTIONS 预检分支使用，本用例走的是普通 POST
    interceptor.response = null;
    interceptor.userDao = new UserDao() {
      @Override
      public boolean existsUserByTokenAndDeviceId(String token, String deviceId) {
        throw new IllegalStateException(leak);
      }
    };

    Object result = interceptor.execute(proceedForbiddenContext());

    Response<?> envelope = assertInstanceOf(Response.class, result);
    assertEquals(ResponseCode.SYSTEM_ERROR.getCode(), envelope.getCode());
    assertEquals(ResponseCode.SYSTEM_ERROR.getMessage(), envelope.getMessage());
    String payload = envelope.getMessage() + "|" + envelope.getDescription();
    assertFalse(payload.contains("t_user"),
        "鉴权失败信封泄露了表名：" + payload);
    assertFalse(payload.contains("select"),
        "鉴权失败信封泄露了 SQL 片段：" + payload);
  }

  /**
   * 只认请求头的 {@link HttpServerRequest} 桩。
   *
   * <p>{@code getParam} 一旦被调用即断言失败：这正是被删掉的 query 回退。
   * 用 JDK 动态代理而不是 mock 框架，与 {@code testsupport.WebSocketSessionProbe} 同一做法
   * （仓内无 mockito 依赖）。
   */
  private static HttpServerRequest headerOnlyRequest(String token, String deviceId) {
    return (HttpServerRequest) Proxy.newProxyInstance(
        HttpServerRequest.class.getClassLoader(),
        new Class<?>[]{HttpServerRequest.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "method" -> HttpMethod.POST;
          case "getHeader" -> {
            String name = String.valueOf(args[0]);
            yield TOKEN.equals(name) ? token : DEVICE_ID.equals(name) ? deviceId : null;
          }
          case "getParam" -> throw new AssertionError("HTTP 凭据不得回退读 query：" + args[0]);
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "HttpServerRequest[header-only-stub]";
          default -> null;
        });
  }

  /** 只回日志所需的目标与方法；{@code proceed()} 不该被走到（异常发生在 proceed 之前）。 */
  private static InvocationContext proceedForbiddenContext() throws Exception {
    Method logged = Object.class.getMethod("toString");
    return (InvocationContext) Proxy.newProxyInstance(
        InvocationContext.class.getClassLoader(),
        new Class<?>[]{InvocationContext.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getTarget" -> "StubTarget";
          case "getMethod" -> logged;
          case "proceed" -> throw new AssertionError("鉴权失败后不得放行到端点");
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "InvocationContext[stub]";
          default -> null;
        });
  }
}
