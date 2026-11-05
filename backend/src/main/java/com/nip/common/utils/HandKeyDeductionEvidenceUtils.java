package com.nip.common.utils;

import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.vo.PostTelegramTrainScoreVO;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HandKeyDeductionEvidenceUtils {
  private HandKeyDeductionEvidenceUtils() {}

  public static Map<String, Integer> amounts(PostTelegramTrainScoreVO score, PostTelegramTrainRule rule) {
    Map<String, Integer> values = new LinkedHashMap<>();
    values.put("errorWord", score.getErrorCodeNumber() * rule.getErrorCode().getL());
    values.put("quantoCode", score.getMoreOrLackWord() * rule.getQuantoCode().getL());
    values.put("quantoGroup", (score.getMoreGroup() + score.getLackGroup()) * rule.getQuantoGroup().getL());
    values.put("quantoRow", score.getMoreOrLackLine() * rule.getQuantoRow().getL());
    values.put("bunchGroup", score.getBunchGroup() * rule.getBunchGroup().getL());
    values.put("dotMinScore", score.getDotScore());
    values.put("lineScore", score.getLineScore());
    values.put("codeGapScore", score.getCodeScore());
    values.put("wordGapScore", score.getWordScore());
    values.put("groupGapScore", score.getGroupScore());
    return values;
  }

  public static Map<String, Integer> changes(Map<String, Integer> before, Map<String, Integer> after) {
    Map<String, Integer> result = new LinkedHashMap<>();
    after.forEach((key, amount) -> {
      int delta = amount - before.getOrDefault(key, 0);
      if (delta > 0) result.put(key, delta);
    });
    return result;
  }
}
