package com.nip.service;

import com.nip.common.utils.TickerPatUtils;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.vo.PostTelegramTrainScoreVO;
import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import com.nip.service.general.GeneralTickerPatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HandKeyDeductionClassificationTest {
  private static final String RULE = """
      {"wpm":{"base":70,"r":0,"l":0},"skew":51,
       "code":{"dot":{"base":30,"l":0,"r":0,"max":0},"dash":{"base":50,"l":0,"r":0,"max":0}},
       "gap":{"little":{"base":40,"l":0,"r":0,"max":0},"middle":{"base":60,"l":0,"r":0,"max":0},
              "large":{"base":90,"l":0,"r":0,"max":0}},
       "other":{"errorCode":{"l":7,"max":100},"quantoCode":{"l":3,"max":100},
                "quantoGroup":{"l":5,"max":100},"alterError":{"l":0,"max":0},
                "quantoRow":{"l":9,"max":100},"bunchGroup":{"l":4,"max":100}}}
      """;

  @ParameterizedTest
  @CsvSource({"123,0,1,97", "234,0,1,97", "134,0,1,97", "124,0,1,97", "24,0,1,97",
      "12345,0,1,97", "91234,0,1,97", "19234,0,1,97", "12934,0,1,97", "12394,0,1,97",
      "12349,0,1,97", "192394,0,1,97", "129,1,0,93", "999,1,0,93", "213,1,0,93",
      "12945,1,0,93", "13249,1,0,93", "12#34,0,1,97", "1#5,1,0,93", "1294,1,0,93", "1234,0,0,100"})
  void personalAndGroupHandKeyDeductOnlyTheClassifiedError(String answer, int wrong, int length, int expectedScore)
      throws Exception {
    PostTelegramTrainRule rule = TickerPatUtils.parseContent(RULE);
    PostTelegramTrainScoreVO score = compare(List.of("1234"), List.of(answer), rule);
    assertDeductions(score, rule, wrong, length, expectedScore);
    assertEquals(answer.equals("1234") ? 0 : 1, score.getErrorNumber(), "总错误数继续包含长度错误");
  }

  @ParameterizedTest
  @CsvSource({"123,9999", "9999,56789"})
  void independentLengthAndWrongGroupErrorsAreBothChargedOnce(String first, String second) throws Exception {
    PostTelegramTrainRule rule = TickerPatUtils.parseContent(RULE);
    PostTelegramTrainScoreVO score = compare(List.of("1234", "5678"), List.of(first, second), rule);
    assertDeductions(score, rule, 1, 1, 90);
    assertEquals(2, score.getErrorNumber());
  }

  private PostTelegramTrainScoreVO compare(List<String> sources, List<String> answers, PostTelegramTrainRule rule) {
    List<PostTelegramTrainContentAddParam> contents = answers.stream().map(answer -> {
      PostTelegramTrainContentAddParam content = new PostTelegramTrainContentAddParam();
      content.setPatKeys(answer);
      content.setPatLogs("[]");
      content.setMoresTime("[]");
      content.setMoresValue("[]");
      return content;
    }).toList();
    PostTelegramTrainScoreVO score = new PostTelegramTrainScoreVO();
    new MessageComparisonService().comparison(sources, answers, score, contents, List.of(), rule,
        new PostTelegramTrainStatisticsVO());
    return score;
  }

  @Test
  void missingLineFollowedByIsolatedWrongGroupsDoesNotCreateTwentyFalseGroupErrors() throws Exception {
    var rule = TickerPatUtils.parseContent(RULE);
    List<String> sources = new ArrayList<>(java.util.stream.IntStream.range(0, 100)
        .mapToObj(i -> String.format("%04d", i)).toList());
    sources.set(78, "6419"); sources.set(79, "1897"); sources.set(80, "6893"); sources.set(88, "6047");
    List<String> answers = new ArrayList<>(sources);
    answers.set(78, "6407"); answers.set(88, "6419");
    answers.subList(30, 40).clear();
    var score = compare(sources, answers, rule);
    assertEquals(1, score.getMoreOrLackLine());
    assertEquals(0, score.getMoreGroup());
    assertEquals(0, score.getLackGroup());
    assertEquals(0, score.getBunchGroup());
    assertDeductions(score, rule, 2, 0, 77);
  }

  @Test
  void missingMiddleLineIsNotChargedAgainAsTrailingLine() throws Exception {
    var rule = TickerPatUtils.parseContent(RULE);
    List<String> sources = java.util.stream.IntStream.range(0, 40)
        .mapToObj(i -> String.format("%04d", i)).toList();
    for (int missingLine : List.of(0, 1, 3)) {
      List<String> answers = new ArrayList<>(sources);
      answers.subList(missingLine * 10, missingLine * 10 + 10).clear();
      var score = compare(sources, answers, rule);
      assertEquals(1, score.getMoreOrLackLine(), "只缺第" + (missingLine + 1) + "行");
      assertDeductions(score, rule, 0, 0, 91);
    }
    List<String> answers = new ArrayList<>(sources.subList(0, 10));
    answers.addAll(sources.subList(20, 30));
    var twoMissing = compare(sources, answers, rule);
    assertEquals(2, twoMissing.getMoreOrLackLine(), "中间和末尾各缺一行");
    assertDeductions(twoMissing, rule, 0, 0, 82);
  }

  @Test
  void correctingFourthGroupAfterFifthKeepsThirdAndFifthAtTheirOriginalPositions() throws Exception {
    var rule = TickerPatUtils.parseContent(RULE);
    var sources = List.of("1111", "2222", "3333", "4444", "5555", "6666");
    for (String correction : List.of("9999?4444", "?4444")) {
      var score = compare(sources, List.of("1111", "2222", "3333", correction, "5555", "6666"), rule);
      assertEquals(6, score.getCorrect());
      assertEquals(0, score.getLackGroup());
      assertDeductions(score, rule, 0, 0, 100);
    }
  }

  @Test
  void groupCaptureAcceptsOnlyReplayBoundaryBackedByPreviousGroupCorrection() throws Exception {
    var service = new GeneralTickerPatService(null, null, null, null, null, null, null, null);
    var method = GeneralTickerPatService.class.getDeclaredMethod("measurePage", List.class);
    method.setAccessible(true);
    var first = new com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainContentAddParam();
    first.setPatKeys("[\"?\",\"1\"]");
    first.setMoresValue("[[0,0,1,0,1,1],[0]]");
    first.setMoresTime("[[10,10,30,10,30,30],[10]]");
    var second = new com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainContentAddParam();
    second.setPatKeys("[\"1\",\"?\",\"2\"]");
    second.setMoresValue("[[0],[],[1]]");second.setMoresTime("[[10],[],[30]]");
    org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> method.invoke(service, List.of(first, second)));
    org.junit.jupiter.api.Assertions.assertThrows(java.lang.reflect.InvocationTargetException.class,
        () -> method.invoke(service, List.of(second)));
  }

  @Test
  void previousGroupCorrectionDoesNotSplitReplayIntoExtraGroupOrChargeAnotherCorrection() throws Exception {
    var rule = TickerPatUtils.parseContent(RULE);
    rule.getAlterError().setL(5);
    rule.getAlterError().setMax(100);
    var first = new PostTelegramTrainContentAddParam();
    first.setPatKeys("1111?2465");first.setPatLogs("[]");first.setMoresTime("[]");
    first.setMoresValue("[[0],[0],[0],[0],[0,0,1,0,1,1],[0],[0],[0],[0]]");
    var second = new PostTelegramTrainContentAddParam();
    second.setPatKeys("6794?6794");second.setPatLogs("[]");second.setMoresTime("[]");
    second.setMoresValue("[[0],[0],[0],[0],[],[0],[0],[0],[0]]");
    var score = new PostTelegramTrainScoreVO();
    var result = new MessageComparisonService().comparison(List.of("2465", "6794"),
        List.of(first.getPatKeys(), second.getPatKeys()), score, List.of(first, second), List.of(), rule,
        new PostTelegramTrainStatisticsVO());
    assertEquals(List.of("2465", "6794"), result.getResolverMessage());
    assertEquals(5, score.getAlterErrorScore());
    assertDeductions(score, rule, 0, 0, 95);
  }

  @Test
  void evidenceLocatesLengthErrorsAndPreservesUncappedAmounts() {
    PostTelegramTrainRule rule = TickerPatUtils.parseContent(RULE);
    rule.getQuantoCode().setMax(4);
    var content = new PostTelegramTrainContentAddParam();
    content.setPatLogs("[]"); content.setMoresTime("[]"); content.setMoresValue("[]");
    var score = new PostTelegramTrainScoreVO();
    var result = new MessageComparisonService().comparison(List.of("1234", "5678"), List.of("123", "567"),
        score, List.of(content, content), List.of(), rule, new PostTelegramTrainStatisticsVO());
    assertEquals(2, result.getDeductionEvidence().size());
    assertEquals(1, result.getDeductionEvidence().getFirst().referenceGroup());
    assertEquals("1234", result.getDeductionEvidence().getFirst().reference());
    assertEquals("123", result.getDeductionEvidence().getFirst().captured());
    assertEquals(Map.of("quantoCode", 3), result.getDeductionEvidence().getFirst().deductions());
    assertEquals(2, result.getDeductionEvidence().getLast().referenceGroup());
    Map<String, Integer> summary = new HashMap<>();
    assertEquals(96, PostTelegramTrainService.applyDeductions(100, score, rule, summary));
    assertEquals(4, summary.get("quantoCode"), "明细原始6分，整场按4分封顶");
  }

  @Test
  void timingEvidenceIncludesMeasuredDurationAndAllowedRange() {
    PostTelegramTrainRule rule = TickerPatUtils.parseContent(RULE);
    rule.getDot().setR(2);
    var standard = new com.nip.dto.PostTelegramTrainFinishInfoDto();
    standard.setDot(100); standard.setLine(300); standard.setCodeGap(100);
    standard.setWordGap(300); standard.setGroupGap(500);
    var content = new PostTelegramTrainContentAddParam();
    content.setPatLogs("[[{\"key\":2,\"value\":500},{\"key\":0,\"value\":160}]]");
    content.setMoresTime("[]"); content.setMoresValue("[]");
    var result = new MessageComparisonService().comparison(List.of("1234"), List.of("9999"),
        new PostTelegramTrainScoreVO(), List.of(content), List.of(standard), rule, new PostTelegramTrainStatisticsVO());
    var timing = result.getTimingEvidence().getFirst();
    assertEquals(160, timing.actualMillis());
    assertEquals(70, timing.minimumMillis());
    assertEquals(130, timing.maximumMillis());
    assertEquals(2, timing.deduction());
    assertEquals(1, timing.capturedGroup());
    assertEquals(1, timing.character());
    assertEquals(2, result.getDeductionEvidence().getFirst().deductions().get("dotMinScore"));
  }

  @Test
  void failedCorrectionDeductsOnlyWrongCodeWhileSuccessfulCorrectionKeepsItsOwnDeduction() throws Exception {
    PostTelegramTrainRule rule = TickerPatUtils.parseContent(RULE);
    rule.getAlterError().setL(5);
    rule.getAlterError().setMax(100);
    var failed = compare(List.of("1234"), List.of("1111?1294"), rule);
    assertEquals(0, failed.getAlterErrorScore());
    assertDeductions(failed, rule, 1, 0, 93);
    var successful = compare(List.of("1234"), List.of("1111?1234"), rule);
    assertEquals(5, successful.getAlterErrorScore());
    assertDeductions(successful, rule, 0, 0, 95);
    var mixed = compare(List.of("1234", "5678"), List.of("1111?1234", "5555?5698"), rule);
    assertEquals(5, mixed.getAlterErrorScore(), "只能去掉失败改错所在组的扣分");
    assertDeductions(mixed, rule, 1, 0, 88);
  }

  @Test
  void moreOrMissingGroupsDoNotAlsoDeductWrongCode() throws Exception {
    PostTelegramTrainRule rule = TickerPatUtils.parseContent(RULE);
    var sources = List.of("1111", "2222", "3333");
    var more = compare(sources, List.of("9999", "1111", "2222", "3333"), rule);
    assertEquals(1, more.getMoreGroup());
    assertDeductions(more, rule, 0, 0, 95);
    var missing = compare(sources, List.of("1111", "3333"), rule);
    assertEquals(1, missing.getLackGroup());
    assertDeductions(missing, rule, 0, 0, 95);
  }

  @Test
  void recognizedWordGapAndBunchErrorsDoNotAlsoDeductWrongCode() throws Exception {
    PostTelegramTrainRule rule = TickerPatUtils.parseContent(RULE);
    var separated = compare(List.of("1234"), List.of("12", "34"), rule);
    assertEquals(1, separated.getErrorNumber());
    assertDeductions(separated, rule, 0, 0, 100);
    List<String> sources = java.util.stream.IntStream.range(0, 20).mapToObj(i -> String.format("%04d", i)).toList();
    List<String> answers = new ArrayList<>(sources);
    answers.set(5, sources.get(15));
    answers.set(15, sources.get(5));
    var bunch = compare(sources, answers, rule);
    assertEquals(1, bunch.getBunchGroup());
    assertDeductions(bunch, rule, 0, 0, 96);
  }

  @Test
  void swappedPairIsOneBunchWithoutWrongCodeAndDoesNotHideIndependentError() throws Exception {
    var rule = TickerPatUtils.parseContent(RULE);
    for (int partner : List.of(1, 5, 10, 15)) {
      List<String> sources = java.util.stream.IntStream.range(0, 20).mapToObj(i -> String.format("%04d", i)).toList();
      List<String> answers = new ArrayList<>(sources);
      java.util.Collections.swap(answers, 0, partner);
      var score = compare(sources, answers, rule);
      assertEquals(1, score.getBunchGroup());
      assertEquals(2, score.getErrorNumber());
      assertDeductions(score, rule, 0, 0, 96);
      answers.set(19, "9999");
      assertDeductions(compare(sources, answers, rule), rule, 1, 0, 89);
    }
  }

  @Test
  void pairedBunchAfterMissingGroupUsesAlignedPositionsAndRecordsBothPartners() throws Exception {
    var rule = TickerPatUtils.parseContent(RULE);
    List<String> sources = java.util.stream.IntStream.range(0, 20).mapToObj(i -> String.format("%04d", i)).toList();
    List<String> answers = new ArrayList<>(sources);
    java.util.Collections.swap(answers, 5, 15);
    answers.remove(0);
    var contents = answers.stream().map(answer -> {
      var content = new PostTelegramTrainContentAddParam();
      content.setPatLogs("[]"); content.setMoresTime("[]"); content.setMoresValue("[]");
      return content;
    }).toList();
    var score = new PostTelegramTrainScoreVO();
    var result = new MessageComparisonService().comparison(sources, answers, score, contents, List.of(), rule,
        new PostTelegramTrainStatisticsVO());
    assertEquals(1, score.getBunchGroup());
    assertEquals(1, score.getLackGroup());
    assertDeductions(score, rule, 0, 0, 91);
    var paired = result.getDeductionEvidence().stream().filter(e -> e.partnerReferenceGroup() != null).toList();
    assertEquals(2, paired.size());
    assertEquals(16, paired.getFirst().partnerReferenceGroup());
    assertEquals(6, paired.getLast().partnerReferenceGroup());
    assertEquals(4, paired.getFirst().deductions().get("bunchGroup"));
    assertEquals(Map.of(), paired.getLast().deductions());
  }

  @Test
  void oneSidedCopyIsWrongCodeRatherThanPairedBunch() throws Exception {
    var rule = TickerPatUtils.parseContent(RULE);
    List<String> sources = java.util.stream.IntStream.range(0, 20).mapToObj(i -> String.format("%04d", i)).toList();
    List<String> answers = new ArrayList<>(sources);
    answers.set(5, sources.get(15));
    var score = compare(sources, answers, rule);
    assertEquals(0, score.getBunchGroup());
    assertDeductions(score, rule, 1, 0, 93);
  }

  private void assertDeductions(PostTelegramTrainScoreVO score, PostTelegramTrainRule rule,
      int wrong, int length, int expectedScore) throws Exception {
    Map<String, Integer> personal = new HashMap<>();
    assertEquals(expectedScore, PostTelegramTrainService.applyDeductions(100, score, rule, personal));
    assertEquals(wrong, personal.get("errorWordNumber"));
    assertEquals(wrong * 7, personal.get("errorWord"));
    assertEquals(length, personal.get("quantoCodeNumber"));
    assertEquals(length * 3, personal.get("quantoCode"));
    assertEquals(score.getMoreGroup() + score.getLackGroup(), personal.get("quantoGroupNumber"));
    assertEquals(score.getBunchGroup(), personal.get("bunchGroupNumber"));
    assertEquals(score.getMoreOrLackLine(), personal.get("quantoRowNumber"));

    var groupService = new GeneralTickerPatService(null, null, null, null, null, null, null, null);
    var deductions = GeneralTickerPatService.class.getDeclaredMethod("applyDeductions",
        Integer.class, PostTelegramTrainScoreVO.class, PostTelegramTrainRule.class, Map.class);
    deductions.setAccessible(true);
    Map<String, Integer> group = new HashMap<>();
    assertEquals(expectedScore, deductions.invoke(groupService, 100, score, rule, group));
    assertEquals(personal, group, "个人岗位与组训扣分明细应一致");
  }
}
