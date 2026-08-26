package com.nip.service;

import com.nip.dto.PostTelegramTrainFinishDto;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.vo.PostTelegramTrainScoreVO;
import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
import com.nip.entity.PostTelegramTrainEntity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.nip.common.utils.TickerPatUtils.parseContent;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task 3.2：手键拍发评分的确定性缺陷。
 * P1-06：划扣分封顶误用点的 max；P1-02：速率加减分系数用反且超速被扣分。
 */
class PostTelegramTrainScoreTest {

  /** 基于 t_grading_rule type=0 真实结构，dash.max 调成与 dot.max 不同以暴露 P1-06。 */
  private static final String RULE_JSON = """
      {"wpm":{"base":70,"r":2,"l":1,"type":false},"skew":51,
       "code":{"dot":{"base":30,"l":1,"r":10,"max":1},"dash":{"base":50,"l":1,"r":10,"max":5}},
       "gap":{"little":{"base":40,"l":1,"r":10,"max":1},"middle":{"base":60,"l":1,"r":10,"max":1},
              "large":{"base":90,"l":1,"r":10,"max":1}},
       "other":{"errorCode":{"l":1,"max":1},"quantoCode":{"l":1,"max":1},"quantoGroup":{"l":1,"max":1},
                "alterError":{"l":1,"max":1},"quantoRow":{"l":1,"max":1},"bunchGroup":{"l":1,"max":1}}}""";

  @Test
  void dashDeductionIsCappedByDashMaxNotDotMax() {
    PostTelegramTrainRule rule = parseContent(RULE_JSON);
    PostTelegramTrainScoreVO scoreVO = new PostTelegramTrainScoreVO();
    scoreVO.setLineScore(7); // 超过 dash.max=5，封顶后应扣 5 而不是 dot.max=1
    Map<String, Integer> deductMap = new HashMap<>();

    int score = PostTelegramTrainService.applyDeductions(100, scoreVO, rule, deductMap);

    assertEquals(5, deductMap.get("lineScore"), "划扣分封顶值必须取 dash.max");
    assertEquals(95, score);
  }

  @Test
  void speedAboveBaseAddsScoreWithLCoefficient() {
    Map<String, Integer> deductMap = new HashMap<>();
    saveResultWithSpeed("100", deductMap); // base=70，高于 30，l=1 → +30

    assertEquals(30, deductMap.get("wpmScore"), "高于基准必须按 l 加分");
  }

  @Test
  void speedBelowBaseDeductsWithRCoefficient() {
    Map<String, Integer> deductMap = new HashMap<>();
    saveResultWithSpeed("60", deductMap); // base=70，低于 10，r=2 → -20

    assertEquals(-20, deductMap.get("wpmScore"), "低于基准必须按 r 扣分");
  }

  private static void saveResultWithSpeed(String speed, Map<String, Integer> deductMap) {
    PostTelegramTrainRule rule = parseContent(RULE_JSON);
    PostTelegramTrainEntity entity = new PostTelegramTrainEntity();
    PostTelegramTrainFinishDto dto = new PostTelegramTrainFinishDto();
    dto.setSpeed(speed);
    PostTelegramTrainService.saveTrainResult(entity, new PostTelegramTrainScoreVO(), 100,
        new PostTelegramTrainStatisticsVO(), deductMap, rule, dto);
  }
}
