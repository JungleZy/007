package com.nip.service;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.Response;
import com.nip.dao.TelegramTrainFloorContentDao;
import com.nip.dao.TelegramTrainFloorDao;
import com.nip.entity.TelegramTrainFloorContentEntity;
import com.nip.entity.TelegramTrainFloorEntity;
import com.nip.dao.TelegramTrainStatisticalDao;
import com.nip.dao.UserDao;
import com.nip.dto.vo.TelegramTrainStatisticalVO;
import com.nip.entity.TelegramTrainStatisticalEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;


import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
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
  @Inject TelegramTrainFloorDao floorDao;

  private static final String PAT_TOKEN = "telegram-floor-page-" + UUID.randomUUID();
  private static final String PAT_DEVICE = "telegram-floor-dev-" + UUID.randomUUID();

  @BeforeEach
  void seedPatUser() {
    RestAssured.defaultParser = Parser.JSON;
    if (userDao.findUserEntityByToken(PAT_TOKEN) == null) {
      Fixtures.user(userDao, PAT_TOKEN, PAT_DEVICE);
    }
  }

  /**
   * 缺页（trainId+pageNumber 查无报底）必须是 202「目标不存在、可修正后重试」，
   * 而且真因要原样回到调用方——改前 NPE 被宽 catch 吞成 ResponseResult.error()，
   * 前端只看到 code 500 /「服务器错误」，分不清是缺页还是服务挂了。
   */
  @Test
  void missingFloorPageReturns202WithRealCauseInsteadOfGenericFailure() {
    given()
        .contentType(ContentType.JSON)
        .header("token", PAT_TOKEN)
        .header("deviceId", PAT_DEVICE)
        .body(Map.of("trainId", "no-such-train-" + UUID.randomUUID(), "pageNumber", 7))
        .when().post("/api/telegramTrain/getFloorContentByFloor")
        .then().statusCode(200)
        // 明确业务码：缺页 = 参数可修正的「目标不存在」
        .body("code", is(ResponseCode.PARAMS_ERROR.getCode()))
        // 真因不再被吞：文案指向缺的那一页，而不是宽 catch 的通用「服务器错误」
        .body("message", not(is(ResponseCode.SYSTEM_ERROR.getMessage())))
        .body("message", containsString("7"))
        .body("message", containsString("页报底"));
  }

  /** 缺页守卫不得误伤正常路径：报底存在时仍是 200。 */
  @Test
  void existingFloorPageStillSucceeds() {
    String trainId = "telegram-floor-train-" + UUID.randomUUID();
    TelegramTrainFloorEntity floor = new TelegramTrainFloorEntity();
    floor.setTrainId(trainId);
    floor.setSort(3);
    floorDao.saveAndFlush(floor);

    given()
        .contentType(ContentType.JSON)
        .header("token", PAT_TOKEN)
        .header("deviceId", PAT_DEVICE)
        .body(Map.of("trainId", trainId, "pageNumber", 3))
        .when().post("/api/telegramTrain/getFloorContentByFloor")
        .then().statusCode(200)
        .body("code", is(ResponseCode.SUCCESS.getCode()));
  }

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
