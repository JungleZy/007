package com.nip.service;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.Response;
import com.nip.dao.TelegramTrainFloorContentDao;
import com.nip.entity.TelegramTrainFloorContentEntity;
import com.nip.dao.TelegramTrainStatisticalDao;
import com.nip.dao.UserDao;
import com.nip.dto.vo.TelegramTrainStatisticalVO;
import com.nip.entity.TelegramTrainStatisticalEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;


import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Task 5.2：saveFloorContent 的 HQL 曾写成蛇形列名 mores_value/morse_time
 * （非实体属性名，且 morse 还是拼错的 mores），更新永远失败并被 catch 吞成 error 响应。
 * 正确参照同类 controlTelegramTrain 内联更新（TelegramTrainService:205 的 moresValue）。
 */
@QuarkusTest

class TelegramTrainServiceTest {
  @Inject TelegramTrainService service;
  @Inject TelegramTrainFloorContentDao contentDao;
  @Inject TelegramTrainStatisticalDao statisticalDao;
  @Inject UserDao userDao;

  @Test
  void statisticalPageFillsMissingTypesAndSortsAscending() {
    UserEntity user = Fixtures.user(userDao, "telegram-order");
    for (int type : new int[]{2, 0}) {
      TelegramTrainStatisticalEntity entity = new TelegramTrainStatisticalEntity();
      entity.setUserId(user.getId());
      entity.setType(type);
      entity.setTotalCount(0);
      entity.setAvgSpeed(BigDecimal.ZERO);
      entity.setTotalTime("0");
      statisticalDao.save(entity);
    }

    List<TelegramTrainStatisticalVO> result = service.statisticalPage("telegram-order");

    assertEquals(3, result.size());
    assertEquals(List.of(0, 1, 2), result.stream()
        .map(TelegramTrainStatisticalVO::getType).toList());
  }

  @Test
  void saveFloorContentUpdatesMoresValueAndTime() {
    TelegramTrainFloorContentEntity e = new TelegramTrainFloorContentEntity();
    e.setFloorId("p52-floor");
    e.setSort(0);
    e.setMoresKey("k");
    e = contentDao.save(e);

    Response<Void> resp = service.saveFloorContent(
        Map.of("id", e.getId(), "moresValue", "[\"A\"]", "moresTime", "[123]"));

    assertEquals(ResponseCode.SUCCESS.getCode(), resp.getCode(), "更新必须成功而不是被吞成 error");
    TelegramTrainFloorContentEntity reloaded = contentDao.findById(e.getId());
    assertNotNull(reloaded);
    assertEquals("[\"A\"]", reloaded.getMoresValue(), "moresValue 必须已更新");
    assertEquals("[123]", reloaded.getMoresTime(), "moresTime 必须已更新");
  }

  @Test
  void saveFloorContentDefaultsEmptyMoresTimeToEmptyJsonArray() {
    TelegramTrainFloorContentEntity e = new TelegramTrainFloorContentEntity();
    e.setFloorId("p52-floor2");
    e.setSort(0);
    e.setMoresKey("k");
    e.setMoresTime("[9]");
    e = contentDao.save(e);

    Response<Void> resp = service.saveFloorContent(
        Map.of("id", e.getId(), "moresValue", "[\"B\"]", "moresTime", ""));

    assertEquals(ResponseCode.SUCCESS.getCode(), resp.getCode());
    TelegramTrainFloorContentEntity reloaded = contentDao.findById(e.getId());
    assertEquals("[]", reloaded.getMoresTime(), "空 moresTime 必须落库为 []");
  }
}
