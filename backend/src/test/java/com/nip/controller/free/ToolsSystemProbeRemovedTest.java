package com.nip.controller.free;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * SEC-12 回归测试：匿名侦察端点 {@code /api/tools/system} 已整体删除。
 *
 * <p>它原本不带任何门禁就返回主机名、内网 IP、OS/JVM 版本与 CPU/内存规格——横向移动前的
 * 现成情报，而前端零消费（{@code bw-frontend/frontend/src} 全量 grep 无引用）。端点删除后
 * JAX-RS 抛 NotFoundException，由 {@code WebApplicationExceptionMapper} 原样直通 HTTP 404。
 *
 * <p>同类里另外两个端点（{@code /getNowTime}、{@code /getTwelvemonth}）有真实前端调用者，
 * 一并断言仍可匿名访问，防止「删侦察面」被做成「把整类门禁掉」而打断已上线页面。
 */
@QuarkusTest
class ToolsSystemProbeRemovedTest {

  @Test
  void systemProbeEndpointIsGone() {
    given()
        .header("Origin", "http://localhost")
        .when().get("/api/tools/system")
        .then().statusCode(404);
  }

  @Test
  void remainingToolEndpointsStayAnonymous() {
    // ShortcutMenu.vue 通过 ToolsApi.getNowTime() 拉服务器时间
    given()
        .header("Origin", "http://localhost")
        .when().get("/api/tools/getNowTime")
        .then().statusCode(200);
    // CableApi/ToolsApi.getTwelvemonth() 供线路统计选月份
    given()
        .header("Origin", "http://localhost")
        .when().get("/api/tools/getTwelvemonth")
        .then().statusCode(200);
  }
}
