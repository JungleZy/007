package com.nip.dao;

import com.nip.dao.general.key.GeneralKeyPatPageDao;
import com.nip.dao.general.telex.GeneralTelexPatPageDao;
import com.nip.entity.simulation.key.GeneralKeyPatPageEntity;
import com.nip.entity.simulation.telex.GeneralTelexPatPageEntity;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-2 / P1-1（Task 5.1）：findTwoPage 曾误用页记录主键 id 过滤，
 * 而调用方（GeneralKeyPatService:833 / GeneralTelexPatService:279）传的是 trainId，
 * 导致"拍发详情"报底内容恒为空。
 * 防守契约：findTwoPage(trainId) 只返回目标 train 的第 1、2 页，按页码升序。
 */
@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class PatPageFindTwoPageDaoTest {
  @Inject GeneralKeyPatPageDao keyPageDao;
  @Inject GeneralTelexPatPageDao telexPageDao;

  private static GeneralKeyPatPageEntity keyPage(Integer trainId, Integer pageNumber) {
    return new GeneralKeyPatPageEntity()
        .setTrainId(trainId).setPageNumber(pageNumber).setSort(0)
        .setKey("k" + pageNumber).setValue("v" + pageNumber);
  }

  private static GeneralTelexPatPageEntity telexPage(String trainId, Integer pageNumber) {
    return new GeneralTelexPatPageEntity()
        .setTrainId(trainId).setPageNumber(pageNumber).setSort(0)
        .setKey("k" + pageNumber).setValue("v" + pageNumber);
  }

  @Test
  void keyPatFindTwoPageReturnsOnlyTargetTrainPages() {
    keyPageDao.save(keyPage(9101, 1));
    keyPageDao.save(keyPage(9101, 2));
    keyPageDao.save(keyPage(9101, 3)); // 第 3 页不属于"前两页"
    keyPageDao.save(keyPage(9202, 1));
    keyPageDao.save(keyPage(9202, 2));

    List<GeneralKeyPatPageEntity> pages = keyPageDao.findTwoPage(9101);

    assertEquals(2, pages.size(), "只应返回目标 train 的第 1、2 页");
    assertTrue(pages.stream().allMatch(p -> p.getTrainId().equals(9101)),
        "不得混入其他 train 的页");
    assertEquals(List.of(1, 2),
        pages.stream().map(GeneralKeyPatPageEntity::getPageNumber).toList(),
        "页码必须为升序的 1、2");
  }

  @Test
  void telexPatFindTwoPageReturnsOnlyTargetTrainPages() {
    telexPageDao.save(telexPage("p51-train-a", 1));
    telexPageDao.save(telexPage("p51-train-a", 2));
    telexPageDao.save(telexPage("p51-train-a", 3));
    telexPageDao.save(telexPage("p51-train-b", 1));
    telexPageDao.save(telexPage("p51-train-b", 2));

    List<GeneralTelexPatPageEntity> pages = telexPageDao.findTwoPage("p51-train-a");

    assertEquals(2, pages.size(), "只应返回目标 train 的第 1、2 页");
    assertTrue(pages.stream().allMatch(p -> "p51-train-a".equals(p.getTrainId())),
        "不得混入其他 train 的页");
    assertEquals(List.of(1, 2),
        pages.stream().map(GeneralTelexPatPageEntity::getPageNumber).toList(),
        "页码必须为升序的 1、2");
  }
}
