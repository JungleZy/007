package com.nip.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusIntegrationTest
@QuarkusTestResource(value = DatabaseTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("用户管理API集成测试")
class UserIntegrationTest {

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 18001;
    }

    @Test
    @Order(1)
    @DisplayName("测试用户登录API")
    void testUserLogin() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"userAccount\":\"admin\",\"password\":\"123456\"}")
        .when()
            .post("/api/user/login")
        .then()
            .statusCode(200)
            .body("code", equalTo(200))
            .body("data.token", notNullValue());
    }

    @Test
    @Order(2)
    @DisplayName("测试获取用户信息API")
    void testGetUserInfo() {
        String token = "test-token";
        
        given()
            .header("token", token)
            .header("deviceId", "test-device")
        .when()
            .get("/api/user/info")
        .then()
            .statusCode(200)
            .body("code", equalTo(200));
    }

    @Test
    @Order(3)
    @DisplayName("测试获取用户列表API")
    void testGetUserList() {
        String token = "test-token";
        
        given()
            .header("token", token)
            .header("deviceId", "test-device")
            .queryParam("page", 1)
            .queryParam("size", 10)
        .when()
            .get("/api/user/list")
        .then()
            .statusCode(200)
            .body("code", equalTo(200))
            .body("data", notNullValue());
    }

    @Test
    @Order(4)
    @DisplayName("测试未授权访问")
    void testUnauthorizedAccess() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/user/info")
        .then()
            .statusCode(401);
    }
}
