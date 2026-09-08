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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 5.2 回归（2026-09-08 业务决策：功能需要，但不引 poi）。
 *
 * <p>三个原「返回 code=200 的空壳」端点按本仓既定分工恢复：Excel 由前端解析，
 * 后端只收 JSON 行 / 只给列规格 —— 与 {@code MilitaryTermDataController.saveBatch}
 * （「代替之前文件导入」）和 {@code exportQuestionByLevelId}（「后端只提供数据由前端生成文件导出」）同一路线。
 *
 * <p>{@code uploadFileToNip} 只支持纯文本；Office 格式必须被明确拒绝而非静默返回空 VO。
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
  void batchImportPersistsRowsAndExportReadsThemBack() {
    String levelId = "lvl-" + UUID.randomUUID();
    String first = "batch-topic-a-" + UUID.randomUUID();
    String second = "batch-topic-b-" + UUID.randomUUID();

    authed().body("[{\"type\":1,\"topic\":\"" + first + "\",\"options\":\"[\\\"A\\\"]\","
            + "\"answer\":\"A\",\"levelId\":\"" + levelId + "\"},"
            + "{\"type\":3,\"topic\":\"" + second + "\",\"answer\":\"对\",\"levelId\":\"" + levelId + "\"}]")
        .when().post("/api/theoryKnowledgeQuestion/saveBatch")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.size()", is(2));

    authed().body("{\"levelId\":\"" + levelId + "\"}")
        .when().post("/api/theoryKnowledgeQuestion/exportQuestionByLevelId")
        .then().statusCode(200)
        .body("data.size()", is(2))
        .body("data.topic", hasItems(first, second));
  }

  @Test
  void batchImportRejectsEmptyPayloadAndRowsMissingRequiredFields() {
    authed().body("[]")
        .when().post("/api/theoryKnowledgeQuestion/saveBatch")
        .then().statusCode(200)
        .body("message", is("导入数据为空或格式不完整"));

    authed().body("[{\"type\":1,\"topic\":\"has-topic\",\"levelId\":\"lvl-x\"},"
            + "{\"type\":1,\"levelId\":\"lvl-x\"}]")
        .when().post("/api/theoryKnowledgeQuestion/saveBatch")
        .then().statusCode(200)
        .body("message", is("第 2 行缺少题目"));
  }

  @Test
  void batchImportRollsBackEveryRowWhenOneRowIsInvalid() {
    String levelId = "lvl-rollback-" + UUID.randomUUID();
    String good = "rollback-topic-" + UUID.randomUUID();

    authed().body("[{\"type\":1,\"topic\":\"" + good + "\",\"answer\":\"A\",\"levelId\":\"" + levelId + "\"},"
            + "{\"type\":1,\"topic\":\"second-row\",\"answer\":\"B\"}]")
        .when().post("/api/theoryKnowledgeQuestion/saveBatch")
        .then().statusCode(200)
        .body("message", is("第 2 行缺少所属题库"));

    assertTrue(questionDao.findAllByLevelId(levelId).isEmpty(),
        "一行失败必须整批回滚，第一行不得留在库里");
  }

  @Test
  void templateColumnsMatchTheBatchImportContract() {
    authed().body("{}")
        .when().post("/api/theoryKnowledgeQuestion/exportTemplate")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.field", contains("type", "topic", "options", "answer", "analysis", "levelId"))
        .body("data.findAll { it.required == true }.field", contains("type", "topic", "levelId"));
  }

  @Test
  void plainTextUploadReturnsItsContentAndOfficeFormatsAreRejected() {
    String content = "第一行\n第二行 " + UUID.randomUUID();

    given().header("Origin", "http://localhost").header("token", TOKEN).header("deviceId", DEVICE)
        .multiPart("file", "note.txt", content.getBytes(StandardCharsets.UTF_8), "text/plain")
        .when().post("/api/theoryKnowledge/uploadFileToNip")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.type", is(2))
        .body("data.wordContent", is(content));

    given().header("Origin", "http://localhost").header("token", TOKEN).header("deviceId", DEVICE)
        .multiPart("file", "course.docx", new byte[]{0x50, 0x4b, 0x03, 0x04},
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        .when().post("/api/theoryKnowledge/uploadFileToNip")
        .then().statusCode(200)
        .body("message", containsString("仅支持纯文本文档"));

    given().header("Origin", "http://localhost").header("token", TOKEN).header("deviceId", DEVICE)
        .multiPart("file", "blank.txt", new byte[]{' ', '\n'}, "text/plain")
        .when().post("/api/theoryKnowledge/uploadFileToNip")
        .then().statusCode(200)
        .body("message", containsString("文档内容为空"));
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
