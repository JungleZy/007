package com.nip.service;

import com.nip.dao.GradingRuleDao;
import com.nip.dao.MilitaryTermDataDao;
import com.nip.dao.UserDao;
import com.nip.dto.PostTelexPatTrainDto;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.MilitaryTermDataEntity;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
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
@QuarkusTestResource(MySqlResource.class)
class IntegerUnboxBoundaryTest {

  @Inject GradingRuleService gradingRuleService;
  @Inject GradingRuleDao gradingRuleDao;
  @Inject MilitaryTermDataService militaryTermDataService;
  @Inject MilitaryTermDataDao militaryTermDataDao;
  @Inject PostTelexPatTrainService postTelexPatTrainService;
  @Inject UserDao userDao;

  @Test
  void saveGradingRuleWithNullIsDefaultDoesNotNpe() {
    GradingRuleEntity entity = new GradingRuleEntity();
    entity.setTitle("拆箱边界规则");
    entity.setType(0);
    entity.setScore(100);
    entity.setContent("{}");
    entity.setIsDefault(null); // 修复前 entity.getIsDefault() == 0 直接 NPE
    assertDoesNotThrow(() -> gradingRuleService.saveGradingRule(entity),
        "isDefault 为 null 不得拆箱 NPE");
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
    GradingRuleEntity rule = gradingRuleDao.save(new GradingRuleEntity());

    PostTelexPatTrainDto dto = new PostTelexPatTrainDto();
    dto.setName("拆箱边界训练");
    dto.setRuleId(rule.getId());
    dto.setIsCable(0);
    dto.setType(0);
    dto.setPatType(2);
    dto.setGroupNumber(null); // 修复前 groupNumber < 200 直接 NPE
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> postTelexPatTrainService.save(dto, token), "组数为 null 必须显式报错而非 NPE");
    assertEquals("组数不能为空", ex.getMessage());
  }
}
