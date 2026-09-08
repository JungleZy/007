package com.nip.service;

import com.nip.dto.general.GeneralTickerPatTrainUpdateDto;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainAddParam;
import com.nip.service.general.GeneralTickerPatService;
import com.nip.dao.UserDao;
import com.nip.testsupport.Fixtures;


import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task 7.4 残留（手键模拟训练）：findById 裸解引用与 Integer/Boolean 裸拆箱。
 * updateStatus 原来对不存在的 trainId 直接 setStatus → NPE 500；
 * add 原来对缺失的 messageNumber 直接 `> 200` 比较 → NPE 500。
 * 两者都必须变成可辨识的业务错误。
 */
@QuarkusTest

class GeneralTickerPatGuardTest {

  @Inject GeneralTickerPatService service;
  @Inject UserDao userDao;

  @Test
  void updateStatusForMissingTrainReportsBusinessErrorInsteadOfNpe() {
    GeneralTickerPatTrainUpdateDto dto = new GeneralTickerPatTrainUpdateDto();
    dto.setTrainId(-987654);
    dto.setStatus(1);

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> service.updateStatus(dto), "训练不存在时必须显式报错，不得裸解引用 findById 的 null");
    assertEquals("未查询到训练", ex.getMessage());
  }

  @Test
  void addWithoutMessageNumberIsRejectedExplicitly() {
    String token = "p74-tickerpat-" + UUID.randomUUID();
    Fixtures.user(userDao, token);

    GeneralTickerPatTrainAddParam param = new GeneralTickerPatTrainAddParam();
    param.setName("拆箱边界训练");
    param.setType(0);
    param.setIsCable(0);
    param.setUserId(List.of());
    param.setMessageNumber(null); // 修复前 messageNumber > 200 直接拆箱 NPE

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> service.add(param, token), "报底数为 null 必须显式报错而非 NPE");
    assertEquals("报底数不能为空", ex.getMessage());
  }
}
