package com.nip.service;

import com.nip.dao.TelexPatTrainStatisticalDao;
import com.nip.dao.UserDao;
import com.nip.dto.vo.TelexPatTrainStatisticalVO;
import com.nip.entity.TelexPatTrainStatisticalEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task 3.4 P2-69：statisticalPage 原来补齐缺失类型后重新查库直返、完全不排序。
 * 现在必须补齐 4 种类型并显式按 type 排序。
 */
@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class TelexPatTrainStatisticalServiceTest {
  @Inject TelexPatTrainStatisticalService service;
  @Inject TelexPatTrainStatisticalDao statisticalDao;
  @Inject UserDao userDao;

  @Test
  void statisticalPageFillsMissingTypesAndSortsByType() {
    UserEntity user = Fixtures.user(userDao, "p34-telex-sort");
    // 只有 type 3 和 1，且逆序插入
    for (int type : new int[]{3, 1}) {
      TelexPatTrainStatisticalEntity e = new TelexPatTrainStatisticalEntity();
      e.setUserId(user.getId());
      e.setType(type);
      e.setTotalCount(type * 10);
      e.setAvgSpeed(BigDecimal.ZERO);
      e.setTotalTime("0");
      statisticalDao.save(e);
    }

    List<TelexPatTrainStatisticalVO> page = service.statisticalPage("p34-telex-sort");

    assertEquals(4, page.size(), "缺失类型必须补齐到 4 种");
    assertEquals(List.of(0, 1, 2, 3), page.stream().map(TelexPatTrainStatisticalVO::getType).toList(),
        "必须按 type 升序返回");
  }
}
