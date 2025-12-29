package com.nip.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusIntegrationTest
@QuarkusTestResource(value = DatabaseTestResource.class)
@DisplayName("训练管理API集成测试")
class TrainIntegrationTest {

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 18001;
    }

    @Test
    @DisplayName("测试获取训练列表")
    void testGetTrainList() {
        String token = "test-token";
        
        given()
            .header("token", token)
            .header("deviceId", "test-device")
            .queryParam("type", 1)
        .when()
            .get("/api/train/list")
        .then()
            .statusCode(200)
            .body("code", equalTo(200));
    }

    @Test
    @DisplayName("测试创建训练记录")
    void testCreateTrain() {
        String token = "test-token";
        
        given()
            .header("token", token)
            .header("deviceId", "test-device")
            .contentType(ContentType.JSON)
            .body("{\"type\":1,\"status\":0,\"content\":\"测试训练内容\"}")
        .when()
            .post("/api/train/create")
        .then()
            .statusCode(200)
            .body("code", equalTo(200));
    }

    @Test
    @DisplayName("测试更新训练记录")
    void testUpdateTrain() {
        String token = "test-token";
        String trainId = "test-train-id";
        
        given()
            .header("token", token)
            .header("deviceId", "test-device")
            .contentType(ContentType.JSON)
            .pathParam("id", trainId)
            .body("{\"status\":1}")
        .when()
            .put("/api/train/{id}")
        .then()
            .statusCode(200)
            .body("code", equalTo(200));
    }

    @Test
    @DisplayName("测试删除训练记录")
    void testDeleteTrain() {
        String token = "test-token";
        String trainId = "test-train-id";
        
        given()
            .header("token", token)
            .header("deviceId", "test-device")
            .pathParam("id", trainId)
        .when()
            .delete("/api/train/{id}")
        .then()
            .statusCode(200)
            .body("code", equalTo(200));
    }
}
