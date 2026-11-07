package com.nip.common.utils;

import com.nip.dto.PostTelexPatTrainRuleDto;
import com.nip.dto.TelexDeductionEvidence;
import com.nip.dto.TelexPatPageTransferDto;
import com.nip.dto.TelexPatStatisticalDto;
import com.nip.dto.TelexPatValueTransferDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 数据报/电传结算的逐项扣分依据：TelexPatUtils.handle 每累计一类扣分必须同步落一条证据，
 * 证据数量合计与聚合计数一致；priced 用冻结规则定价，前端不得重算。
 */
class TelexPatDeductionEvidenceTest {

  private static List<TelexPatPageTransferDto> referencePage() {
    List<TelexPatPageTransferDto> page = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      TelexPatPageTransferDto dto = new TelexPatPageTransferDto();
      dto.setTrainId("t1");
      dto.setPageNumber(1);
      dto.setKey(String.format("%04d", i));
      dto.setSort(i);
      page.add(dto);
    }
    return page;
  }

  private static String capturedText(List<TelexPatPageTransferDto> page, Function<Integer, String> override) {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < page.size(); i++) {
      if (i > 0) {
        text.append(i % 10 == 0 ? '\n' : ' ');
      }
      text.append(override.apply(i));
    }
    return text.toString();
  }

  private static TelexPatStatisticalDto settle(List<TelexPatPageTransferDto> page, String values,
      List<TelexPatValueTransferDto> out) {
    TelexPatStatisticalDto ks = new TelexPatStatisticalDto();
    TelexPatUtils.handle(null, 1, out, page, values, ks, true);
    return ks;
  }

  private static Map<String, Integer> evidenceSums(TelexPatStatisticalDto ks) {
    return ks.getEvidence().stream().collect(Collectors.toMap(
        TelexDeductionEvidence::kind, TelexDeductionEvidence::count, Integer::sum));
  }

  private static void assertEvidenceMatchesCounters(TelexPatStatisticalDto ks) {
    Map<String, Integer> sums = evidenceSums(ks);
    assertEquals(ks.getErrorCodeNumber(), sums.getOrDefault("errorCode", 0), "错码证据合计必须等于聚合计数");
    assertEquals(ks.getCorrectMistakesNumber(), sums.getOrDefault("correctMistakes", 0), "改错证据合计必须等于聚合计数");
    assertEquals(ks.getNonStandartNumber(), sums.getOrDefault("nonStandart", 0), "不规证据合计必须等于聚合计数");
    assertEquals(ks.getMuchLessLineNumber(), sums.getOrDefault("muchLessLine", 0), "多少行证据合计必须等于聚合计数");
    assertEquals(ks.getMuchLessGroupsNumber(), sums.getOrDefault("muchLessGroups", 0), "多少组证据合计必须等于聚合计数");
    assertEquals(ks.getMuchLessCodeNumber(), sums.getOrDefault("muchLessCode", 0), "多少码证据合计必须等于聚合计数");
    assertEquals(ks.getLessReturnLineNumber(), sums.getOrDefault("lessReturnLine", 0), "少回行证据合计必须等于聚合计数");
    assertEquals(ks.getLessPageNumber(), sums.getOrDefault("lessPage", 0), "少页标证据合计必须等于聚合计数");
    assertEquals(ks.getErrorPageNumber(), sums.getOrDefault("errorPage", 0), "页标错证据合计必须等于聚合计数");
  }

  @Test
  void errorGroupProducesLocatedEvidence() {
    List<TelexPatPageTransferDto> page = referencePage();
    String values = capturedText(page, i -> i == 5 ? "AAAA" : i == 99 ? "0099-1" : page.get(i).getKey());
    TelexPatStatisticalDto ks = settle(page, values, new ArrayList<>());

    assertEquals(1, ks.getErrorCodeNumber());
    TelexDeductionEvidence evidence = ks.getEvidence().stream()
        .filter(item -> item.kind().equals("errorCode")).findFirst().orElseThrow();
    assertEquals(1, evidence.pageNumber());
    assertEquals(6, evidence.referenceGroup(), "组号按 1 起始展示");
    assertEquals("0005", evidence.reference());
    assertEquals("AAAA", evidence.captured());
    assertEquals(1, evidence.count());
    assertEvidenceMatchesCounters(ks);
  }

  @Test
  void missingGroupAtRowTailProducesEvidenceWithEmptyCaptured() {
    List<TelexPatPageTransferDto> page = referencePage();
    // 第 2 行只拍 0010..0018，漏掉行尾 0019；其余行完整
    List<String> tokens = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      if (i == 19) {
        continue;
      }
      tokens.add(i == 99 ? "0099-1" : page.get(i).getKey());
    }
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < tokens.size(); i++) {
      if (i > 0) {
        text.append(i == 10 || i == 19 || (i > 19 && (i - 19) % 10 == 0) ? '\n' : ' ');
      }
      text.append(tokens.get(i));
    }
    TelexPatStatisticalDto ks = settle(page, text.toString(), new ArrayList<>());

    assertEquals(1, ks.getMuchLessGroupsNumber(), "漏拍行尾一组按多少组计一次");
    TelexDeductionEvidence evidence = ks.getEvidence().stream()
        .filter(item -> item.kind().equals("muchLessGroups")).findFirst().orElseThrow();
    assertEquals(20, evidence.referenceGroup());
    assertEquals("0019", evidence.reference());
    assertEquals("", evidence.captured());
    assertEvidenceMatchesCounters(ks);
  }

  @Test
  void emptySubmissionCountsAllLinesWithEvidence() {
    List<TelexPatPageTransferDto> page = referencePage();
    TelexPatStatisticalDto ks = settle(page, "", new ArrayList<>());

    assertEquals(10, ks.getMuchLessLineNumber());
    assertEquals(0, ks.getErrorCodeNumber());
    assertEvidenceMatchesCounters(ks);
  }

  @Test
  void pricedUsesFrozenRuleAndAlterErrorFallback() {
    PostTelexPatTrainRuleDto.Other other = new PostTelexPatTrainRuleDto.Other();
    other.setErrorCode(new BigDecimal("7"));
    other.setMuchLessGroups(new BigDecimal("3"));
    other.setCorrectMistakes(new BigDecimal("2"));
    other.setLessPage(new BigDecimal("4"));
    other.setLessReturnLine(new BigDecimal("5"));
    other.setMuchLessLine(new BigDecimal("6"));
    other.setMuchLessCode(new BigDecimal("8"));
    other.setErrorPage(new BigDecimal("9"));
    other.setNonStandart(new BigDecimal("11"));
    PostTelexPatTrainRuleDto rule = new PostTelexPatTrainRuleDto();
    rule.setOther(other);

    TelexDeductionEvidence raw = new TelexDeductionEvidence(1, "errorCode", 3, "0002", "AAAA", 2, null, null);
    TelexDeductionEvidence priced = raw.priced(rule);
    assertEquals(0, new BigDecimal("7").compareTo(priced.unitDeduction()));
    assertEquals(0, new BigDecimal("14").compareTo(priced.deduction()), "扣分 = 单价 × 数量");

    TelexDeductionEvidence mistakes = new TelexDeductionEvidence(1, "correctMistakes", null, "1234", "123/", 1, null, null);
    assertEquals(0, new BigDecimal("2").compareTo(mistakes.priced(rule).unitDeduction()),
        "alterError 为空时回退 correctMistakes 单价");
    other.setAlterError(new BigDecimal("13"));
    assertEquals(0, new BigDecimal("13").compareTo(mistakes.priced(rule).unitDeduction()),
        "alterError 存在时优先使用，与结算分支同口径");

    assertEquals(0, new BigDecimal("6").compareTo(
        new TelexDeductionEvidence(1, "muchLessLine", 1, "0000", "", 1, null, null).priced(rule).unitDeduction()));
    assertEquals(0, new BigDecimal("4").compareTo(
        new TelexDeductionEvidence(1, "lessPage", null, "1", "", 1, null, null).priced(rule).unitDeduction()));
    assertEquals(0, new BigDecimal("9").compareTo(
        new TelexDeductionEvidence(1, "errorPage", null, "1", "2", 1, null, null).priced(rule).unitDeduction()));
    assertEquals(0, new BigDecimal("11").compareTo(
        new TelexDeductionEvidence(1, "nonStandart", null, null, "ab", 1, null, null).priced(rule).unitDeduction()));
    assertEquals(0, new BigDecimal("3").compareTo(
        new TelexDeductionEvidence(1, "muchLessGroups", 1, "0000", "", 1, null, null).priced(rule).unitDeduction()));
    assertEquals(0, new BigDecimal("8").compareTo(
        new TelexDeductionEvidence(1, "muchLessCode", 1, "0000", "00001", 1, null, null).priced(rule).unitDeduction()));
    assertEquals(0, new BigDecimal("5").compareTo(
        new TelexDeductionEvidence(1, "lessReturnLine", 11, null, "0000 0001", 1, null, null).priced(rule).unitDeduction()));
  }
}
