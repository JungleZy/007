package com.nip.service;

import com.nip.dao.GradingRuleDao;
import com.nip.dao.MilitaryTermDataDao;
import com.nip.dao.UserDao;
import com.nip.dto.PostTelexPatTrainDto;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.MilitaryTermDataEntity;
import com.nip.testsupport.Fixtures;


import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 7 Integer 拆箱家族抽样边界测试（P2-31/33/55/56）：
 * Integer 字段 ==/compareTo 裸拆箱统一改为 Objects.equals/前置判空后，
 * null 输入必须走显式分支或显式报错，不得 NPE。
 */
@QuarkusTest

class IntegerUnboxBoundaryTest {

  @Inject GradingRuleService gradingRuleService;
  @Inject GradingRuleDao gradingRuleDao;
  @Inject MilitaryTermDataService militaryTermDataService;
  @Inject MilitaryTermDataDao militaryTermDataDao;
  @Inject PostTelexPatTrainService postTelexPatTrainService;
  @Inject UserDao userDao;

  @Test
  @io.quarkus.test.TestTransaction
  void savingWithoutDefaultChoiceKeepsTheSelectedRule() {
    GradingRuleEntity selected = Fixtures.handkeyRule(gradingRuleDao);
    selected.setIsDefault(0);
    gradingRuleService.saveGradingRule(selected);
    GradingRuleEntity candidate = new GradingRuleEntity();
    candidate.setTitle("optional-default-rule");
    candidate.setType(0);
    candidate.setScore(100);
    candidate.setContent(selected.getContent());
    candidate.setIsDefault(null);
    var saved = gradingRuleService.saveGradingRule(candidate).getData();
    assertEquals(0, gradingRuleDao.findById(selected.getId()).getIsDefault());
    org.junit.jupiter.api.Assertions.assertNotEquals(0, gradingRuleDao.findById(saved.getId()).getIsDefault());
  }

  @Test
  void moveAcrossParentIsRejected() {
    MilitaryTermDataEntity p1 = militaryTermDataDao.save(
        new MilitaryTermDataEntity().setParentId("0").setKey("拆箱父A").setSort(1));
    MilitaryTermDataEntity p2 = militaryTermDataDao.save(
        new MilitaryTermDataEntity().setParentId("0").setKey("拆箱父B").setSort(2));
    MilitaryTermDataEntity c1 = militaryTermDataDao.save(
        new MilitaryTermDataEntity().setParentId(p1.getId()).setKey("拆箱子A1").setSort(1));
    MilitaryTermDataEntity c2 = militaryTermDataDao.save(
        new MilitaryTermDataEntity().setParentId(p2.getId()).setKey("拆箱子B1").setSort(1));

    com.nip.dto.MilitaryTermDataMoveDto dto = new com.nip.dto.MilitaryTermDataMoveDto();
    dto.setSourceId(c1.getId());
    dto.setTargetId(c2.getId());
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> militaryTermDataService.move(dto), "跨父移动必须被拒绝");
    assertEquals("仅支持同级词条间移动", ex.getMessage());
  }

  @Test
  void telexSaveWithNullGroupNumberIsRejectedExplicitly() {
    String token = "unbox-telex-token";
    Fixtures.user(userDao, token);
    GradingRuleEntity capturedRule = new GradingRuleEntity();
    capturedRule.setScore(100);
    capturedRule.setContent("""
        {"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":10},
         "other":{"errorCode":0,"muchLessGroups":0,"correctMistakes":0,"lessPage":0,"lessReturnLine":0,
                  "muchLessLine":0,"muchLessCode":0,"errorPage":0,"nonStandart":0}}
        """);
    GradingRuleEntity rule = gradingRuleDao.save(capturedRule);

    PostTelexPatTrainDto dto = new PostTelexPatTrainDto();
    dto.setName("拆箱边界训练");
    dto.setRuleId(rule.getId());
    dto.setIsCable(0);
    dto.setType(0);
    dto.setPatType(2);
    dto.setGroupNumber(null); // 修复前 groupNumber < 200 直接 NPE
    assertThrows(IllegalArgumentException.class,
        () -> postTelexPatTrainService.save(dto, token), "组数为 null 必须显式报错而非 NPE");
  }
}
