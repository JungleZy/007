package com.nip.controller;

import com.nip.dao.TheoryKnowledgeQuestionDao;
import com.nip.dao.UserDao;
import com.nip.entity.TheoryKnowledgeQuestionEntity;
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
 * Task 5.2 回归：三个「返回 code=200 的空壳」端点已连端点一并删除（poi 依赖在 pom.xml:79-83 被注掉，
 * 模板/题库 Excel 导入导出无法在不新增依赖的前提下真实实现）；仍保留的
 * exportQuestionByLevelId 必须返回真实数据。
 */
@QuarkusTest
class TheoryKnowledgeUploadExportTest {
  private static final String TOKEN = "tk-upload-export-" + UUID.randomUUID();
  private static final String DEVICE = "dev-upload-export-" + UUID.randomUUID();

  @Inject UserDao userDao;
  @Inject TheoryKnowledgeQuestionDao questionDao;

  @BeforeEach
  void seedUser() {
    RestAssured.defaultParser = Parser.JSON;
    if (userDao.findUserEntityByToken(TOKEN) == null) {
      Fixtures.user(userDao, TOKEN, DEVICE);
    }
  }

  private RequestSpecification authed() {
    return given()
        .header("Origin", "http://localhost")
        .header("token", TOKEN)
        .header("deviceId", DEVICE)
        .contentType("application/json");
  }

  @Test
  void emptyShellUploadAndExportEndpointsAreGone() {
    authed().body("{}")
        .when().post("/api/theoryKnowledgeQuestion/upLoadFile")
        .then().statusCode(404);
    authed().body("{}")
        .when().post("/api/theoryKnowledgeQuestion/exportTemplate")
        .then().statusCode(404);
    authed().body("{}")
        .when().post("/api/theoryKnowledge/uploadFileToNip")
        .then().statusCode(404);
  }

  @Test
  void exportQuestionByLevelIdReturnsRealRows() {
    String levelId = "lvl-" + UUID.randomUUID();
    String topic = "export-topic-" + UUID.randomUUID();
    TheoryKnowledgeQuestionEntity question = new TheoryKnowledgeQuestionEntity();
    question.setLevelId(levelId);
    question.setTopic(topic);
    question.setType(1);
    question.setAnswer("A");
    questionDao.save(question);

    authed().body("{\"levelId\":\"" + levelId + "\"}")
        .when().post("/api/theoryKnowledgeQuestion/exportQuestionByLevelId")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.size()", is(1))
        .body("data[0].topic", is(topic));
  }
}
