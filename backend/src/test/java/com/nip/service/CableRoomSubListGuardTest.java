package com.nip.service;

import com.nip.dao.GradingRuleDao;
import com.nip.dao.UserDao;
import com.nip.dto.PostTelegraphKeyPatTrainDto;
import com.nip.dto.PostTelexPatTrainDto;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainAddParam;
import com.nip.entity.GradingRuleEntity;
import com.nip.service.general.GeneralTickerPatService;
import com.nip.testsupport.Fixtures;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task 6.4 家族残留：电缆报底分支的 {@code cableFloor.subList(0, totalPage)}。
 * 修复前 totalPage = 组数/100 未做下界/上界校验：
 * 组数 &lt; 100 时 totalPage=0，subList 返回空列表，静默建出一个零报底的房间/训练；
 * totalPage &gt; 可用楼层数时 subList 直接 IndexOutOfBoundsException → 500。
 * 两种都必须变成可辨识的业务错误。
 */
@QuarkusTest
class CableRoomSubListGuardTest {

  @Inject GeneralTickerPatService generalTickerPatService;
  @Inject PostTelexPatTrainService postTelexPatTrainService;
  @Inject PostTelegraphKeyPatTrainService postTelegraphKeyPatTrainService;
  @Inject GradingRuleDao gradingRuleDao;
  @Inject UserDao userDao;

  @Test
  void tickerPatRoomWithLessThanOnePageOfGroupsIsRejected() {
    String token = "sublist-ticker-" + UUID.randomUUID();
    Fixtures.user(userDao, token);

    GeneralTickerPatTrainAddParam param = new GeneralTickerPatTrainAddParam();
    param.setName("不足一页电缆房间");
    param.setType(0);
    param.setIsCable(1);
    param.setCableId("sublist-no-such-cable");
    param.setUserId(List.of());
    param.setMessageNumber(50); // 修复前 totalPage=0 → 静默建出零报底房间

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> generalTickerPatService.add(param, token), "不足一页的电缆报底必须被拒绝");
    assertEquals("报文组数不足一页，无法建立房间", ex.getMessage());
  }

  @Test
  void tickerPatRoomWithMorePagesThanCableFloorsIsRejected() {
    String token = "sublist-ticker-over-" + UUID.randomUUID();
    Fixtures.user(userDao, token);

    GeneralTickerPatTrainAddParam param = new GeneralTickerPatTrainAddParam();
    param.setName("楼层不足电缆房间");
    param.setType(0);
    param.setIsCable(1);
    param.setCableId("sublist-no-such-cable");
    param.setUserId(List.of());
    param.setMessageNumber(200); // totalPage=2 > 可用楼层 0，修复前 subList 越界 500

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> generalTickerPatService.add(param, token), "楼层不足必须报业务错误而非越界");
    assertEquals("所选电缆可用楼层不足", ex.getMessage());
  }

  @Test
  void telexPatTrainWithLessThanOnePageOfGroupsIsRejected() {
    String token = "sublist-telex-" + UUID.randomUUID();
    Fixtures.user(userDao, token);
    GradingRuleEntity rule = gradingRuleDao.save(rule());

    PostTelexPatTrainDto dto = new PostTelexPatTrainDto();
    dto.setName("不足一页电缆训练");
    dto.setRuleId(rule.getId());
    dto.setIsCable(1);
    dto.setCableId("sublist-no-such-cable");
    dto.setGroupNumber(50); // 修复前 totalPage=0 → 静默建出零报底训练

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> postTelexPatTrainService.save(dto, token), "不足一页的电缆报底必须被拒绝");
    assertEquals("报文组数不足一页，无法建立训练", ex.getMessage());
  }

  @Test
  void telegraphKeyPatTrainWithLessThanOnePageOfGroupsIsRejected() {
    String token = "sublist-key-" + UUID.randomUUID();
    Fixtures.user(userDao, token);
    GradingRuleEntity rule = gradingRuleDao.save(rule());

    PostTelegraphKeyPatTrainDto dto = new PostTelegraphKeyPatTrainDto();
    dto.setTitle("不足一页电缆训练");
    dto.setRuleId(rule.getId());
    dto.setIsCable(1);
    dto.setCableId("sublist-no-such-cable");
    dto.setTotalNumber(50); // 修复前 totalPage=0 → 静默建出零报底训练

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> postTelegraphKeyPatTrainService.add(dto, token), "不足一页的电缆报底必须被拒绝");
    assertEquals("报文组数不足一页，无法建立训练", ex.getMessage());
  }

  private static GradingRuleEntity rule() {
    GradingRuleEntity entity = new GradingRuleEntity();
    entity.setTitle("subList 守卫规则");
    entity.setType(0);
    entity.setScore(100);
    entity.setContent("{}");
    return entity;
  }
}
