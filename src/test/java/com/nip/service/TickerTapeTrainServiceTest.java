package com.nip.service;

import com.nip.dao.TickerTapeTrainStatisticalDao;
import com.nip.dao.UserDao;
import com.nip.dto.vo.TelexPatTrainStatisticalVO;
import com.nip.entity.TickerTapeTrainStatisticalEntity;
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
 * Task 3.4 P2-17：statisticalPage 原来对未排序结果 Collections.swap(0,1)，
 * DB 返回顺序不是 0,1,2 时三个 Tab 数据互串。现在必须显式按 type 排序。
 */
@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class TickerTapeTrainServiceTest {
  @Inject TickerTapeTrainService service;
  @Inject TickerTapeTrainStatisticalDao statisticalDao;
  @Inject UserDao userDao;

  @Test
  void statisticalPageSortsByTypeRegardlessOfDbOrder() {
    UserEntity user = Fixtures.user(userDao, "p34-tickertape-sort");
    // 逆序插入，模拟 DB 返回顺序不是 0,1,2
    for (int type : new int[]{2, 0, 1}) {
      TickerTapeTrainStatisticalEntity e = new TickerTapeTrainStatisticalEntity();
      e.setUserId(user.getId());
      e.setType(type);
      e.setTotalCount(type * 10);
      e.setAvgSpeed(new BigDecimal(0));
      e.setTotalTime("0");
      statisticalDao.save(e);
    }

    List<TelexPatTrainStatisticalVO> page = service.statisticalPage("p34-tickertape-sort");

    assertEquals(3, page.size());
    assertEquals(List.of(0, 1, 2), page.stream().map(TelexPatTrainStatisticalVO::getType).toList(),
        "必须按 type 升序，不依赖 DB 顺序");
  }
}
