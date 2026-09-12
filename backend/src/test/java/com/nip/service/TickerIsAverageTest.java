package com.nip.service;

import com.nip.common.utils.JSONUtils;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.UserDao;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainAddParam;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainContentAddParam;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainPageParam;
import com.nip.entity.UserEntity;
import com.nip.service.general.GeneralTickerPatService;
import com.nip.testsupport.Fixtures;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 3.3：报底缺页懒生成的 isAverage 口径必须与入库口径一致。
 *
 * 入库侧 GeneralTickerPatService.add:132 存 `setIsAverage(TRUE.equals(param.getIsAverage()) ? 1 : 0)`
 * （1 = 平均报），首 2 页在 add:169/194 里直接用 param 的布尔值生成；缺页懒生成
 * （findMessageBody:313-325）原来把 `entity.getIsAverage() == 0` 当布尔 avg 传下去，方向与入库相反
 * ——同一条训练的第 3 页会按与首 2 页相反的口径生成。本测试锁的就是这个跨页一致性。
 *
 * 可观测判定式取自 GlobalMessageGeneratedUtil.generatedNumber(:28-80)：
 * random=true 且 avg=true 走 assembling(:163-201)，每组 4 位必然是 2 位来自 1-5 与 2 位来自 6-9/0
 * （整页去重 pageCheckDuplicate 只在组内换位，不改数字集合）；avg=false 则是 0-9 里取 4 个互不相同的
 * 数字，单组恰好 2 低 2 高的概率约 47.6%，整页 100 组全命中的概率约 1e-32。
 * 因此「整页 100 组里有多少组是 2 低 + 2 高」在两种口径下是可区分的可观察量：平均 = 100，不平均 < 100。
 *
 * 只测 type=0（数码报）一路：generatedWord/generatedMingle 的 avg 分支同构，但它们的差异落在
 * 字母/混合字符集的配比上，没有同样干净的整页判定式。
 */
@QuarkusTest
class TickerIsAverageTest {

  private static final Set<Character> LOW_DIGITS = Set.of('1', '2', '3', '4', '5');

  @Inject GeneralTickerPatService service;
  @Inject UserDao userDao;
  @Inject GradingRuleDao gradingRuleDao;

  /** 一页里「2 位低码 + 2 位高码」的组数——平均报的拼装特征。 */
  private static long evenSplitGroups(List<GeneralTickerPatTrainContentAddParam> page) {
    return page.stream().filter(group -> {
      String[] chars = JSONUtils.fromJson(group.getMoresKey(), String[].class);
      long low = 0;
      for (String c : chars) {
        if (LOW_DIGITS.contains(c.charAt(0))) {
          low++;
        }
      }
      return chars.length == 4 && low == 2;
    }).count();
  }

  private int createTrain(String token, boolean average) {
    GeneralTickerPatTrainAddParam param = new GeneralTickerPatTrainAddParam();
    param.setName("Task3.3-isAverage-" + average);
    param.setType(0);
    param.setIsCable(0);
    param.setIsRandom(true);
    param.setIsAverage(average);
    // >200：add 只预生成前 2 页，第 3 页留给 findMessageBody 懒生成
    param.setMessageNumber(300);
    param.setUserId(List.of());
    param.setRuleId(Fixtures.handkeyRule(gradingRuleDao).getId());
    return service.add(param, token).getId();
  }

  private List<GeneralTickerPatTrainContentAddParam> readPage(int trainId, String userId, String token, int floor) {
    GeneralTickerPatTrainPageParam param = new GeneralTickerPatTrainPageParam();
    param.setId(trainId);
    param.setUserId(userId);
    param.setFloorNumber(floor);
    return service.findMessageBody(param, token).getMessageKey();
  }

  @Test
  void lazyGeneratedPageOfAverageTrainKeepsTheAverageLayoutOfEagerPages() {
    String token = "t33-avg-" + UUID.randomUUID();
    UserEntity user = Fixtures.user(userDao, token);
    int trainId = createTrain(token, true);

    List<GeneralTickerPatTrainContentAddParam> eager = readPage(trainId, user.getId(), token, 1);
    List<GeneralTickerPatTrainContentAddParam> lazy = readPage(trainId, user.getId(), token, 3);

    assertEquals(100, eager.size(), "第 1 页应为 add 预生成的整页 100 组");
    assertEquals(100, evenSplitGroups(eager), "基准：isAverage=true 的预生成页每组都是 2 低 + 2 高");
    assertEquals(100, lazy.size(), "第 3 页应懒生成 300-200=100 组");
    assertEquals(100, evenSplitGroups(lazy),
        "isAverage=1 的训练，缺页懒生成必须与首 2 页同口径按平均报拼装");
  }

  @Test
  void lazyGeneratedPageOfNonAverageTrainStaysNonAverage() {
    String token = "t33-noavg-" + UUID.randomUUID();
    UserEntity user = Fixtures.user(userDao, token);
    int trainId = createTrain(token, false);

    List<GeneralTickerPatTrainContentAddParam> eager = readPage(trainId, user.getId(), token, 1);
    List<GeneralTickerPatTrainContentAddParam> lazy = readPage(trainId, user.getId(), token, 3);

    assertEquals(100, eager.size(), "第 1 页应为 add 预生成的整页 100 组");
    assertTrue(evenSplitGroups(eager) < 100,
        "基准：isAverage=false 的预生成页不应整页都是平均报的 2 低 + 2 高");
    assertEquals(100, lazy.size(), "第 3 页应懒生成 300-200=100 组");
    assertTrue(evenSplitGroups(lazy) < 100,
        "isAverage=0 的训练，缺页懒生成不得反过来按平均报生成（实际 2低2高组数="
            + evenSplitGroups(lazy) + "/100）");
  }
}
