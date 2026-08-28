package com.nip.service;

import com.nip.common.response.Response;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.KeyPointsDao;
import com.nip.dao.MilitaryTermDataDao;
import com.nip.dao.UserDao;
import com.nip.dto.vo.MilitaryTermDataVO;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.KeyPointsEntity;
import com.nip.entity.MilitaryTermDataEntity;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class FindByIdResidualTest {
  private static final String TOKEN = "findbyid-residual-token";
  private static final String DEVICE = "findbyid-residual-device";

  @Inject GradingRuleService gradingRuleService;
  @Inject GradingRuleDao gradingRuleDao;
  @Inject MilitaryTermDataService militaryTermDataService;
  @Inject MilitaryTermDataDao militaryTermDataDao;
  @Inject KeyPointsDao keyPointsDao;
  @Inject UserDao userDao;

  @BeforeEach
  void seedUser() {
    RestAssured.defaultParser = Parser.JSON;
    if (userDao.findUserEntityByToken(TOKEN) == null) {
      Fixtures.user(userDao, TOKEN, DEVICE);
    }
  }

  @Test
  void getMissingGradingRuleThrowsInsteadOfReturningEmptyEntity() {
    long before = gradingRuleDao.count();

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> gradingRuleService.getGradingRuleById("missing-grading-rule"));

    assertEquals("未查询到评分规则", ex.getMessage());
    assertEquals(before, gradingRuleDao.count(), "缺失规则查询不得创建空规则");
  }

  @Test
  void updateStatusForMissingGradingRuleThrowsWithoutPersisting() {
    long before = gradingRuleDao.count();

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> gradingRuleService.updateGradingRuleStatus("missing-status-rule", 1));

    assertEquals("未查询到评分规则", ex.getMessage());
    assertEquals(before, gradingRuleDao.count(), "缺失规则状态更新不得创建空规则");
  }

  @Test
  void changeDefaultForMissingGradingRuleThrowsWithoutChangingExistingRules() {
    GradingRuleEntity first = gradingRuleDao.save(rule(7101, "默认规则A", 0));
    GradingRuleEntity second = gradingRuleDao.save(rule(7101, "默认规则B", 1));

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> gradingRuleService.changeGradingRuleIsDefault("missing-default-rule"));

    assertEquals("未查询到评分规则", ex.getMessage());
    assertEquals(0, gradingRuleDao.findById(first.getId()).getIsDefault());
    assertEquals(1, gradingRuleDao.findById(second.getId()).getIsDefault());
  }

  @Test
  void existingGradingRuleReadStatusAndDefaultFlowsStillWork() {
    GradingRuleEntity first = gradingRuleDao.save(rule(7102, "有效规则A", 0));
    GradingRuleEntity second = gradingRuleDao.save(rule(7102, "有效规则B", 1));

    Response<GradingRuleEntity> read = gradingRuleService.getGradingRuleById(second.getId());
    Response<GradingRuleEntity> updated = gradingRuleService.updateGradingRuleStatus(second.getId(), 1);
    Response<Void> changed = gradingRuleService.changeGradingRuleIsDefault(second.getId());

    assertEquals(200, read.getCode());
    assertEquals(second.getId(), read.getData().getId());
    assertEquals(200, updated.getCode());
    assertEquals(1, gradingRuleDao.findById(second.getId()).getStatus());
    assertEquals(200, changed.getCode());
    assertEquals(1, gradingRuleDao.findById(first.getId()).getIsDefault());
    assertEquals(0, gradingRuleDao.findById(second.getId()).getIsDefault());
  }

  @Test
  void deleteMissingMilitaryTermThrowsWithoutSideEffects() {
    MilitaryTermDataEntity existing = militaryTermDataDao.save(new MilitaryTermDataEntity()
        .setParentId("findbyid-parent")
        .setKey("保留军语")
        .setSort(1));
    long before = militaryTermDataDao.count();
    MilitaryTermDataVO missing = new MilitaryTermDataVO();
    missing.setId("missing-military-term");

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> militaryTermDataService.delete(missing));

    assertEquals("未查询到该军语", ex.getMessage());
    assertEquals(before, militaryTermDataDao.count());
    assertNotNull(militaryTermDataDao.findById(existing.getId()), "缺失 id 删除不得影响现有军语");
  }

  @Test
  void deleteExistingMilitaryTermAdjustsOnlyFollowingSiblingsInSameParent() {
    MilitaryTermDataEntity before = militaryTermDataDao.save(new MilitaryTermDataEntity()
        .setParentId("findbyid-valid-parent")
        .setKey("删除前项")
        .setSort(1));
    MilitaryTermDataEntity target = militaryTermDataDao.save(new MilitaryTermDataEntity()
        .setParentId("findbyid-valid-parent")
        .setKey("删除中间项")
        .setSort(2));
    MilitaryTermDataEntity after = militaryTermDataDao.save(new MilitaryTermDataEntity()
        .setParentId("findbyid-valid-parent")
        .setKey("删除后项")
        .setSort(3));
    MilitaryTermDataEntity otherParent = militaryTermDataDao.save(new MilitaryTermDataEntity()
        .setParentId("findbyid-other-parent")
        .setKey("其他父级项")
        .setSort(7));
    MilitaryTermDataVO vo = new MilitaryTermDataVO();
    vo.setId(target.getId());

    militaryTermDataService.delete(vo);

    assertFalse(militaryTermDataDao.findByIdOptional(target.getId()).isPresent());
    assertEquals(1, militaryTermDataDao.findById(before.getId()).getSort());
    assertEquals(2, militaryTermDataDao.findById(after.getId()).getSort());
    assertEquals(7, militaryTermDataDao.findById(otherParent.getId()).getSort());
  }

  @Test
  void missingKeyPointsTypeReturnsBusinessFailureEnvelope() {
    given()
        .header("Origin", "http://localhost")
        .header("token", TOKEN)
        .header("deviceId", DEVICE)
        .contentType("application/json")
        .body("{}")
        .when().post("/api/keyPoints/findKeyPointsByType")
        .then().statusCode(200)
        .body("code", is(500))
        .body("message", equalTo("要点讲解类型不能为空"));
  }

  @Test
  void validKeyPointsTypeStillReturnsMatchingEntity() {
    KeyPointsEntity entity = new KeyPointsEntity();
    entity.setType(7103);
    entity.setContent("有效要点内容");
    keyPointsDao.save(entity);

    given()
        .header("Origin", "http://localhost")
        .header("token", TOKEN)
        .header("deviceId", DEVICE)
        .contentType("application/json")
        .body("{\"type\":7103}")
        .when().post("/api/keyPoints/findKeyPointsByType")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.id", equalTo(entity.getId()))
        .body("data.content", equalTo("有效要点内容"));
  }

  private static GradingRuleEntity rule(int type, String title, int isDefault) {
    GradingRuleEntity entity = new GradingRuleEntity();
    entity.setType(type);
    entity.setTitle(title);
    entity.setIsDefault(isDefault);
    entity.setStatus(0);
    entity.setScore(100);
    entity.setContent("{}");
    return entity;
  }
}
