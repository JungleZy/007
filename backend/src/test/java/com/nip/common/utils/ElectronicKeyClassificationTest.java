package com.nip.common.utils;

import com.nip.dto.KeyPatPageTransferDto;
import com.nip.dto.KeyPatStatisticalDto;
import com.nip.dto.KeyPatValueTransferDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class ElectronicKeyClassificationTest {
  @ParameterizedTest
  @CsvSource({"234,1,0,0", "134,1,0,0", "124,1,0,0", "123,1,0,0", "12934,0,1,0",
      "12394,0,1,0", "192394,0,1,0", "12349999,0,1,0", "99999999,0,0,1",
      "129,0,0,1", "12945,0,0,1", "213,0,0,1"})
  void lengthErrorsRequireOnlyInsertionsOrDeletions(String answer, int lack, int more, int error) {
    var score = score(List.of("1234"), List.of(answer));
    assertEquals(lack, score.getLack());assertEquals(more, score.getMore());assertEquals(error, score.getError());
  }

  @Test void failedCorrectionMustNotBeChargedAgainAsCorrection() {
    var failed = score(List.of("1234"), List.of("1111?1294"));
    assertEquals(1, failed.getError());assertEquals(0, failed.getAlterError());
    assertEquals(1, score(List.of("1234"), List.of("1111?1234")).getAlterError());
  }

  @Test void swappedPairCountsOnceWithoutWrongOrMissingGroups() {
    for (int distance : List.of(1, 5, 10, 15)) {
      var sources = sources(20);
      var answers = new ArrayList<>(sources);Collections.swap(answers, 0, distance);
      var score = score(sources, answers);
      assertEquals(1, score.getBunchGroup());assertEquals(0, score.getError());
      assertEquals(0, score.getLackGroup());assertEquals(0, score.getMoreGroup());
    }
  }

  @Test void distantCoincidenceDoesNotShiftCorrectFollowingGroups() {
    var sources = sources(30);var answers = new ArrayList<>(sources);
    answers.set(5, sources.get(20));
    var score = score(sources, answers);
    assertEquals(1, score.getError());assertEquals(0, score.getLackGroup());assertEquals(0, score.getMoreGroup());
  }

  @Test void missingWholeLineAndSingleGroupAreExclusive() {
    var sources = sources(30);
    for (int start : List.of(0, 10, 20)) {
      var answers = new ArrayList<>(sources);answers.subList(start, start + 10).clear();
      var score = score(sources, answers);
      assertEquals(1, score.getLackLine());assertEquals(0, score.getLackGroup());assertEquals(0, score.getError());
    }
    var score = score(sources, sources.subList(0, 29));
    assertEquals(0, score.getLackLine());assertEquals(1, score.getLackGroup());
  }

  @Test void trailingExtraGroupsAreNotRoundedUpToAnEntireLine() {
    var sources = sources(10);var answers = new ArrayList<>(sources);answers.add("9999");
    var score = score(sources, answers);
    assertEquals(1, score.getMoreGroup());assertEquals(0, score.getMoreLine());
  }

  @Test void lineCountsAccumulateAcrossPages() {
    var score = new KeyPatStatisticalDto();
    compare(sources(20), sources(10), score);
    compare(sources(20), sources(20), score);
    assertEquals(1, score.getLackLine());
  }

  @Test void joinedCorrectGroupsCountOnlyMissingGapAndKeepFollowingGroupAligned() {
    var score = score(List.of("1234", "5678", "9012"), List.of("12345678", "9012"));
    assertEquals(1, score.getLackGap());assertEquals(3, score.getCorrectGroup());
    assertEquals(0, score.getMore());assertEquals(0, score.getError());assertEquals(0, score.getLackGroup());
  }

  @Test void leadingAndMiddleExtraLineOrGroupPreserveAllReferencePositions() {
    for (int position : List.of(0, 5)) for (int count : List.of(1, 10, 11)) {
      var sources = sources(30);var answers = new ArrayList<>(sources);
      answers.addAll(position, Collections.nCopies(count, "9999"));
      var score = new KeyPatStatisticalDto();var result = compare(sources, answers, score);
      assertEquals(count / 10, score.getMoreLine());assertEquals(count % 10, score.getMoreGroup());
      assertEquals(0, score.getError());assertEquals(30, score.getCorrectGroup());
      assertTrue(result.stream().allMatch(row -> row.getPageNumber() == 1));
    }
  }

  @Test void missingPageCountsLinesAndOnlyRemainderGroups() {
    var score = new KeyPatStatisticalDto();
    KeyPatUtils.recordMissingPage(score, 1, 100);
    KeyPatUtils.recordMissingPage(score, 3, 55);
    assertEquals(15, score.getLackLine());assertEquals(5, score.getLackGroup());
    assertEquals(1, score.getEvidence().getFirst().pageNumber());
    assertEquals(3, score.getEvidence().getLast().pageNumber());
    assertEquals(51, score.getEvidence().getLast().referenceGroup());
    assertEquals(55, score.getEvidence().getLast().referenceThrough());
  }

  @Test void extraGroupEvidenceKeepsOriginalCorrectionHistory() {
    var score = score(List.of("1234"), List.of("9999?0000", "1234"));
    assertEquals(1, score.getMoreGroup());
    assertEquals("9999?0000", score.getEvidence().getFirst().captured());
  }

  @Test void pairedBunchAfterMissingGroupKeepsBothMembersAndTheirAccuracy() {
    var sources = sources(30);var answers = new ArrayList<>(sources);
    Collections.swap(answers, 5, 15);answers.remove(0);
    var score = new KeyPatStatisticalDto();var result = compare(sources, answers, score);
    assertEquals(1, score.getLackGroup());assertEquals(1, score.getBunchGroup());assertEquals(0, score.getError());
    assertEquals(27, score.getCorrectGroup());
    assertEquals(2, result.stream().filter(row -> row.getValue().contains("bunchGroup")).count());
  }

  @Test void missingLineEvidenceNeverAlsoAppearsAsWrongCodeAtTheSameReferencePosition() {
    var sources = sources(100);var answers = new ArrayList<>(sources);answers.subList(30, 40).clear();
    var score = score(sources, answers);
    assertEquals(1, score.getLackLine());assertEquals(0, score.getError());
    assertEquals(1, score.getEvidence().size());
    var missing = score.getEvidence().getFirst();
    assertEquals("lackLine", missing.kind());assertEquals(31, missing.referenceGroup());assertEquals(40, missing.referenceThrough());
  }

  @Test void earlyPageBreakProducesDifferentPageEvidenceInsteadOfMovingAnswersAcrossPages() {
    var all = sources(200);var score = new KeyPatStatisticalDto();
    compare(all.subList(0, 100), all.subList(0, 30), score, 1);
    compare(all.subList(100, 200), all.subList(30, 100), score, 2);
    assertEquals(10, score.getLackLine());assertEquals(70, score.getError());
    assertTrue(score.getEvidence().stream().filter(e -> e.kind().equals("error"))
        .allMatch(e -> e.pageNumber() == 2 && e.referenceGroup() <= 70));
    var missing = score.getEvidence().stream().filter(e -> e.kind().equals("lackLine")).toList();
    assertEquals(2, missing.size());
    assertEquals(1, missing.get(0).pageNumber());assertEquals(31, missing.get(0).referenceGroup());assertEquals(7, missing.get(0).count());
    assertEquals(2, missing.get(1).pageNumber());assertEquals(71, missing.get(1).referenceGroup());assertEquals(3, missing.get(1).count());
  }

  @Test void mixedLineAndGroupEvidenceRangesDoNotOverlapAndUseFrozenRulePrice() {
    var score = score(sources(30), sources(19));
    var evidence = score.getEvidence();
    assertEquals(2, evidence.size());
    assertEquals(20, evidence.get(0).referenceGroup());assertEquals(29, evidence.get(0).referenceThrough());
    assertEquals(30, evidence.get(1).referenceGroup());assertEquals(30, evidence.get(1).referenceThrough());
    var rule = ScoringRuleValidation.electronic("""
        {"rateUnit":"FOUR_CHARACTER_GROUPS_PER_MINUTE","wpm":{"base":70,"r":0,"l":0},
         "other":{"errorCode":15,"muchLessCode":20,"muchLessGroups":10,"muchLessLine":12,
                  "alterError":1,"bunchGroup":10,"lessGap":10}}
        """);
    assertEquals(new java.math.BigDecimal("12"), evidence.get(0).priced(rule).deduction());
    assertEquals(new java.math.BigDecimal("10"), evidence.get(1).priced(rule).deduction());
  }

  static List<String> sources(int count) {return IntStream.range(0, count).mapToObj(i -> String.format("%04d", i)).toList();}
  static KeyPatStatisticalDto score(List<String> sources, List<String> answers) {
    var score = new KeyPatStatisticalDto();compare(sources, answers, score);return score;
  }
  static List<KeyPatValueTransferDto> compare(List<String> sources, List<String> answers, KeyPatStatisticalDto score) {
    return compare(sources, answers, score, 1);
  }
  static List<KeyPatValueTransferDto> compare(List<String> sources, List<String> answers, KeyPatStatisticalDto score, int page) {
    List<KeyPatPageTransferDto> reference = new ArrayList<>();
    for (int i = 0; i < sources.size(); i++) {
      var item = new KeyPatPageTransferDto();item.setTrainId("test");item.setPageNumber(page);item.setSort(i);
      item.setKey(KeyPatUtils.convert2JsonArray(sources.get(i)));reference.add(item);
    }
    List<KeyPatValueTransferDto> values = new ArrayList<>();
    for (int i = 0; i < answers.size(); i++) values.add(new KeyPatValueTransferDto("student", "test", page, "[]",
        KeyPatUtils.convert2JsonArray(answers.get(i)), JSONUtils.toJson(Collections.nCopies(answers.get(i).length(), 10)), i));
    List<KeyPatValueTransferDto> result = new ArrayList<>();
    KeyPatUtils.handle("student", result, reference, values, score);
    var expected = java.util.Map.of("error", score.getError(), "lack", score.getLack(), "more", score.getMore(),
        "lackGroup", score.getLackGroup(), "moreGroup", score.getMoreGroup(), "lackLine", score.getLackLine(),
        "moreLine", score.getMoreLine(), "alterError", score.getAlterError(), "bunchGroup", score.getBunchGroup(), "lackGap", score.getLackGap());
    var recorded = score.getEvidence().stream().collect(java.util.stream.Collectors.groupingBy(
        com.nip.dto.ElectronicKeyDeductionEvidence::kind, java.util.stream.Collectors.summingInt(com.nip.dto.ElectronicKeyDeductionEvidence::count)));
    expected.forEach((kind, count) -> assertEquals(count, recorded.getOrDefault(kind, 0), "依据数量须与汇总一致: " + kind));
    return result;
  }
}
