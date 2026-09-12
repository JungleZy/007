package com.nip.controller;

import com.nip.dao.GradingRuleDao;
import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 全局规则与理论主数据写端点的授权契约（SEC-07）：评分规则、设备评分规则、综合组网运用评分规则、
 * 理论教案 / 题库 / 试卷的维护端点落库后影响**全体学员**的成绩基准与考核内容，
 * 因此只允许系统管理员调用，普通人员必须得到 HTTP 200 + {@code code:207}。
 *
 * <p>同时锁住反向契约（本轮最大的回归风险）：
 * <ul>
 *   <li>规则**读**端点不得一起收紧——学员训练页在训练过程中读规则，加门禁会直接打断训练；</li>
 *   <li>学员自己的随堂测试答卷仍可提交——它虽然落库，但写的是调用者本人的数据
 *       （{@code userId} 由 token 推导），属于自操作而非主数据维护，不在 {@code @RequireAdmin} 范围内。</li>
 * </ul>
 */
@QuarkusTest
class RuleAndQuestionBankAuthorizationTest {
  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;
  @Inject GradingRuleDao gradingRuleDao;

  @Test
  void studentCannotMaintainGradingRules() {
    UserEntity student = student("grad-student");
    GradingRuleEntity rule = Fixtures.handkeyRule(gradingRuleDao);

    as(student).body(Map.of("title", "student-made", "type", 0, "score", 100, "content", "{}"))
        .post("/api/gradingRule/saveGradingRule").then().statusCode(200).body("code", is(207));
    as(student).body(Map.of("id", rule.getId(), "status", 1))
        .post("/api/gradingRule/updateGradingRuleStatus").then().statusCode(200).body("code", is(207));
    as(student).body(Map.of("id", rule.getId()))
        .post("/api/gradingRule/changeGradingRuleIsDefault").then().statusCode(200).body("code", is(207));
    as(student).body(Map.of("id", rule.getId()))
        .post("/api/gradingRule/deleteGradingRule").then().statusCode(200).body("code", is(207));

    // 授权拒绝不得产生副作用：规则行、状态都原样留在库中，新规则也没被建出来
    GradingRuleEntity stored = gradingRuleDao.findById(rule.getId());
    assertNotNull(stored);
    assertEquals(0, stored.getStatus().intValue());
    assertEquals(0, gradingRuleDao.count("title", "student-made"));
  }

  @Test
  void studentCannotMaintainDeviceScoringRules() {
    UserEntity student = student("dev-rule-student");

    as(student).body(Map.of("deviceId", 1, "ruleContent", "[]"))
        .post("/api/deviceScoringRule/save").then().statusCode(200).body("code", is(207));
    as(student).queryParam("id", 1)
        .get("/api/deviceScoringRule/delete").then().statusCode(200).body("code", is(207));
  }

  @Test
  void studentCannotMaintainGroupNetRules() {
    UserEntity student = student("net-rule-student");

    as(student).body(Map.of("code", "J210-742", "xyScore", "[]"))
        .post("/api/generalGroupNetRule/save").then().statusCode(200).body("code", is(207));
    as(student).body(Map.of("id", 1))
        .post("/api/generalGroupNetRule/deleteById").then().statusCode(200).body("code", is(207));
  }

  @Test
  void studentCannotMaintainTheoryKnowledge() {
    UserEntity student = student("knowledge-student");

    as(student).body(Map.of("knowledge", Map.of("title", "student-lesson", "type", 0)))
        .post("/api/theoryKnowledge/saveTheoryKnowledge").then().statusCode(200).body("code", is(207));
    as(student).body(Map.of("id", "no-such-knowledge"))
        .post("/api/theoryKnowledge/deleteThroyKnowledgeById").then().statusCode(200).body("code", is(207));
    as(student).body(Map.of("type", 0, "name", "student-classify"))
        .post("/api/theoryKnowledge/addClassify").then().statusCode(200).body("code", is(207));
    as(student).body(Map.of("id", "no-such-classify"))
        .post("/api/theoryKnowledge/removeClassify").then().statusCode(200).body("code", is(207));
  }

  @Test
  void studentCannotMaintainQuestionBank() {
    UserEntity student = student("bank-student");

    as(student).body(Map.of("topic", "student-question", "type", 1, "levelId", "1"))
        .post("/api/theoryKnowledgeQuestion/saveTheoryKnowledgeQuestion").then().statusCode(200).body("code", is(207));
    as(student).body(Map.of("name", "student-level"))
        .post("/api/theoryKnowledgeQuestion/saveTheoryKnowledgeQuestionLevel").then().statusCode(200).body("code", is(207));
    as(student).body(Map.of("id", "no-such-level"))
        .post("/api/theoryKnowledgeQuestion/deleteTheoryKnowledgeQuestionLevelById").then().statusCode(200).body("code", is(207));
    as(student).body(Map.of("id", "no-such-question"))
        .post("/api/theoryKnowledgeQuestion/deleteTheoryKnowledgeQuestion").then().statusCode(200).body("code", is(207));
    as(student).body(List.of(Map.of("topic", "batch-question", "type", 1, "levelId", "1")))
        .post("/api/theoryKnowledgeQuestion/saveBatch").then().statusCode(200).body("code", is(207));
  }

  @Test
  void studentCannotMaintainTestPapers() {
    UserEntity student = student("paper-student");

    as(student).body(Map.of("name", "student-paper"))
        .post("/api/theoryKnowledgeTestPaper/saveTestPaper").then().statusCode(200).body("code", is(207));
    as(student).body(Map.of("id", "no-such-paper"))
        .post("/api/theoryKnowledgeTestPaper/deleteTestPaper").then().statusCode(200).body("code", is(207));
  }

  @Test
  void studentCannotMaintainLessonTestQuestions() {
    UserEntity student = student("lesson-test-student");

    as(student).body(Map.of("knowledgeTest", Map.of("knowledgeId", "k", "knowledgeSwfId", "s")))
        .post("/api/theoryKnowledgeTest/saveTheoryKnowledgeTest").then().statusCode(200).body("code", is(207));
  }

  @Test
  void studentStillReadsRulesAndQuestionLevels() {
    UserEntity student = student("reader-student");
    GradingRuleEntity rule = Fixtures.handkeyRule(gradingRuleDao);

    as(student).body(Map.of("type", 0))
        .post("/api/gradingRule/getGradingRuleListByType").then().statusCode(200)
        .body("code", is(200)).body("data.title", hasItem(rule.getTitle()));
    as(student).body(Map.of("id", rule.getId()))
        .post("/api/gradingRule/getGradingRuleById").then().statusCode(200)
        .body("code", is(200)).body("data.title", is(rule.getTitle()));
    // 组网评分规则被学员训练评分页读取（views/manage/equipment/trainScore、equipmentOperate/trainScore）
    as(student).post("/api/generalGroupNetRule/findAll").then().statusCode(200).body("code", is(200));
    as(student).queryParam("deviceId", 1)
        .get("/api/deviceScoringRule/findAllByDeviceId").then().statusCode(200).body("code", is(200));
    as(student).post("/api/theoryKnowledgeQuestion/findAllTheoryKnowledgeQuestionLevel")
        .then().statusCode(200).body("code", is(200));
  }

  @Test
  void studentStillSubmitsOwnSelfTestAnswers() {
    UserEntity student = student("self-test-student");

    as(student).body(Map.of("knowledgeId", "k-" + UUID.randomUUID(), "knowledgeSwfId", "s",
            "content", "[]", "score", 60))
        .post("/api/theoryKnowledgeTest/saveUserKnowledgeSwfTestContent").then().statusCode(200)
        .body("code", is(200)).body("data.userId", is(student.getId()));
  }

  @Test
  void adminMaintainsGradingRuleStatus() {
    UserEntity admin = admin("rule-admin");
    GradingRuleEntity rule = Fixtures.handkeyRule(gradingRuleDao);

    as(admin).body(Map.of("id", rule.getId(), "status", 1))
        .post("/api/gradingRule/updateGradingRuleStatus").then().statusCode(200).body("code", is(200));
    assertEquals(1, gradingRuleDao.findById(rule.getId()).getStatus().intValue());
  }

  private RequestSpecification as(UserEntity user) {
    return given()
        .contentType(ContentType.JSON)
        .header("token", user.getToken())
        .header("deviceId", user.getDeviceId())
        .when();
  }

  /** 普通人员（{@code is_admin=1}）—— {@code t_role} 只有它和系统管理员两种，没有教员角色。 */
  private UserEntity student(String prefix) {
    return withRole(prefix, 1);
  }

  /** 系统管理员（{@code is_admin=0}）。 */
  private UserEntity admin(String prefix) {
    return withRole(prefix, 0);
  }

  private UserEntity withRole(String prefix, int isAdmin) {
    UserEntity user = Fixtures.user(userDao, prefix + "-token-" + UUID.randomUUID(),
        prefix + "-device-" + UUID.randomUUID());

    RoleEntity role = new RoleEntity();
    role.setTitle("role-" + UUID.randomUUID());
    role.setIsAdmin(isAdmin);
    role.setIsDefault(1);
    role = roleDao.saveAndFlush(role);

    UserRoleEntity link = new UserRoleEntity();
    link.setUserId(user.getId());
    link.setRoleId(role.getId());
    userRoleDao.saveAndFlush(link);
    return user;
  }
}
