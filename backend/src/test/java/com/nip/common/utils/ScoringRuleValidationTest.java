package com.nip.common.utils;

import com.nip.dto.PostKeyPatTrainRuleDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ScoringRuleValidationTest {
  private static final String HANDKEY = """
      {"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":70},"skew":51,
       "code":{"dot":{"base":30,"l":1,"r":1,"max":1},"dash":{"base":50,"l":1,"r":1,"max":5}},
       "gap":{"little":{"base":40,"l":1,"r":1,"max":1},"middle":{"base":60,"l":1,"r":1,"max":1},"large":{"base":90,"l":1,"r":1,"max":1}},
       "other":{"errorCode":{"l":1,"max":1},"quantoCode":{"l":1,"max":1},"quantoGroup":{"l":1,"max":1},"alterError":{"l":1,"max":1},"quantoRow":{"l":1,"max":1},"bunchGroup":{"l":1,"max":1}}}
      """;

  @Test
  void handkeyRejectsFractionalDeductionBeforeIntegerDeserialization() {
    assertThrows(IllegalArgumentException.class,
        () -> ScoringRuleValidation.handkey(HANDKEY.replace("\"max\":5", "\"max\":5.75")));
    assertThrows(IllegalArgumentException.class,
        () -> ScoringRuleValidation.handkey(HANDKEY.replace("\"base\":70", "\"base\":2147483648")));
    assertEquals(5, ScoringRuleValidation.handkey(HANDKEY).getDash().getMax());
    assertEquals(0, ScoringRuleValidation.handkey(HANDKEY).getWpm().getR());
  }

  @Test
  void electronicPreservesDecimalCoefficientsAndOnlyDefaultsOptionalSpeedTerms() {
    String content = """
        {"rateUnit":"FOUR_CHARACTER_GROUPS_PER_MINUTE","wpm":{"base":4,"r":0.125},
         "other":{"errorCode":0.375,"muchLessCode":0,"muchLessLine":0,"muchLessGroups":0,"alterError":0,"bunchGroup":0,"lessGap":0}}
        """;
    PostKeyPatTrainRuleDto rule = ScoringRuleValidation.electronic(content);
    assertEquals(new BigDecimal("0.375"), rule.getOther().getErrorCode());
    assertEquals(new BigDecimal("0.500"), ScoreMath.wpmScore(4, rule.getWpm().getR(), rule.getWpm().getL(), 8));
    assertEquals(BigDecimal.ZERO, ScoreMath.wpmScore(4, rule.getWpm().getR(), rule.getWpm().getL(), 2));
    assertThrows(IllegalArgumentException.class,
        () -> ScoringRuleValidation.electronic(content.replace(",\"lessGap\":0", "")));
  }

  @Test
  void partialNegativeAndWrongUnitRulesCannotReachScoring() {
    assertThrows(IllegalArgumentException.class, () -> ScoringRuleValidation.handkey(
        "{\"rateUnit\":\"CHARACTERS_PER_MINUTE\",\"wpm\":{\"base\":70},\"skew\":51}"));
    assertThrows(IllegalArgumentException.class,
        () -> ScoringRuleValidation.handkey(HANDKEY.replace("\"base\":70", "\"base\":0")));
    String telex = """
        {"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":40},"other":{
         "errorCode":1,"muchLessCode":2,"muchLessLine":3,"muchLessGroups":4,"correctMistakes":0.25,
         "lessPage":5,"lessReturnLine":6,"errorPage":7,"nonStandart":0.5}}
        """;
    assertEquals(new BigDecimal("0.25"), ScoringRuleValidation.telex(telex).getOther().getCorrectMistakes());
    assertThrows(IllegalArgumentException.class,
        () -> ScoringRuleValidation.telex(telex.replace("\"nonStandart\":0.5", "\"nonStandart\":-0.5")));
    assertThrows(IllegalArgumentException.class,
        () -> ScoringRuleValidation.telex(telex.replace("CHARACTERS_PER_MINUTE", "FOUR_CHARACTER_GROUPS_PER_MINUTE")));
  }
}
