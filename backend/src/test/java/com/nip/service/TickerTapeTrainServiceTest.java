package com.nip.service;

import com.nip.common.constants.TickerTapeTrainStatusEnum;
import com.nip.common.exception.TerminalStateException;
import com.nip.dao.TickerTapeTrainDao;
import com.nip.dao.TickerTapeTrainStatisticalDao;
import com.nip.dao.UserDao;
import com.nip.dto.vo.TelexPatTrainStatisticalVO;
import com.nip.dto.vo.TickerTapeTrainVo;
import com.nip.dto.vo.param.TickerTapeTrainAddParam;
import com.nip.dto.vo.param.TickerTapeTrainUpdateParam;
import com.nip.entity.TickerTapeTrainEntity;
import com.nip.entity.TickerTapeTrainStatisticalEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;


import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task 3.4 P2-17：statisticalPage 原来对未排序结果 Collections.swap(0,1)，
 * DB 返回顺序不是 0,1,2 时三个 Tab 数据互串。现在必须显式按 type 排序。
 * Task 4.1 P2-03：lastTrain 必须取最新一条训练。
 */
@QuarkusTest

class TickerTapeTrainServiceTest {
  @Inject TickerTapeTrainService service;
  @Inject TickerTapeTrainStatisticalDao statisticalDao;
  @Inject UserDao userDao;
  @Inject TickerTapeTrainDao trainDao;

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

  @Test
  void lastTrainReturnsNewestRecordAndAddSettlesOnlyThatOne() {
    // Task 4.1 P2-03：lastTrain 原来按 createTime 升序取 firstResult，拿到的是最早一条，
    // 断点续训回放旧报文、add 的「未开始则删」也删错记录。
    String token = "p41-lasttrain-" + UUID.randomUUID();
    UserEntity user = Fixtures.user(userDao, token, token);
    int type = 1;

    TickerTapeTrainEntity older = seedTrain(user.getId(), type, "older", LocalDateTime.now().minusSeconds(60));
    TickerTapeTrainEntity newer = seedTrain(user.getId(), type, "newer", LocalDateTime.now());

    TickerTapeTrainVo last = service.lastTrain(token, type);
    assertEquals(newer.getId(), last.getId(), "必须返回 createTime 最新的一条");
    assertEquals("newer", last.getName());

    TickerTapeTrainAddParam param = new TickerTapeTrainAddParam();
    param.setType(type);
    param.setName("added");
    param.setRate(60);
    param.setCodeShort(0);
    param.setIsLowRate(1);
    param.setCodeMessageBody(List.of());
    service.add(param, token);

    assertNull(trainDao.findById(newer.getId()), "add 只结算最新那条未开始训练：应被删除");
    assertNotNull(trainDao.findById(older.getId()), "较早的记录不得被误删");
  }

  @Test
  void getByIdForMissingTrainReportsBusinessErrorInsteadOfDereferencingNull() {
    // Task 7.4：getById 原来直接把 findById 的 null 交给 convertOne，
    // 回调里 e.getCodeMessageBody() 解引用 null → 500。
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> service.getById("p74-missing-" + UUID.randomUUID()),
        "训练不存在时必须显式报错，不得裸解引用 findById 的 null");
    assertEquals("未查询到训练", ex.getMessage());
  }

  @Test
  void checkStatusRejectsMissingTrainWithoutWeakeningFinishedSemantics() {
    // 记录不存在：checkStatus 原来裸 entity.getStatus() → NPE 500
    TickerTapeTrainUpdateParam missing = new TickerTapeTrainUpdateParam();
    missing.setId("p74-missing-" + UUID.randomUUID());
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> service.pause(missing), "训练不存在必须显式报错");
    assertEquals("未查询到训练", ex.getMessage());

    // 记录存在且已结束：必须是业务终态拒绝（TerminalStateException → 208），
    // 既不得被判空降级成参数错误（202），也不得落到兜底 Mapper 变成 500。
    UserEntity user = Fixtures.user(userDao, "p74-finished-" + UUID.randomUUID());
    TickerTapeTrainEntity finished = seedTrain(user.getId(), 1, "finished", LocalDateTime.now());
    finished.setStatus(TickerTapeTrainStatusEnum.FINISH.getCode());
    trainDao.saveAndFlush(finished);

    TickerTapeTrainUpdateParam param = new TickerTapeTrainUpdateParam();
    param.setId(finished.getId());
    assertThrows(TerminalStateException.class,
        () -> service.pause(param), "已结束训练必须抛业务终态拒绝，而不是参数错误");
  }

  private TickerTapeTrainEntity seedTrain(String userId, int type, String name, LocalDateTime createTime) {
    TickerTapeTrainEntity e = new TickerTapeTrainEntity();
    e.setUserId(userId);
    e.setType(type);
    e.setName(name);
    e.setStatus(TickerTapeTrainStatusEnum.NOT_STARTED.getCode());
    e.setCodeMessageBody("[]");
    e.setCreateTime(createTime);
    e.setRate(60);
    e.setCodeShort(0);
    e.setValidTime("0");
    e.setMark("0,0");
    e.setSchedule(0);
    return trainDao.saveAndFlush(e);
  }
}
