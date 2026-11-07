package com.nip.common.utils;

import com.nip.dto.TelexPatPageTransferDto;
import com.nip.dto.TelexPatStatisticalDto;
import com.nip.dto.TelexPatValueTransferDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 数据报/电传结算的分类口径：与手键同名的扣分项（错码、多少码、多少组、多少行）按手键判定，
 * 数据报独有项（少回行、页标、改错、不规）沿用本身规则。报底按生产方式取四位互异数字，且相邻十组不重复。
 */
class TelexPatScoringTest {

  private static final String[] KEYS = {
      "4821", "9073", "1564", "3790", "6258", "0413", "7936", "2185", "5609", "8347",
      "1938", "6450", "3072", "9821", "0567", "4296", "7105", "2739", "5384", "8610",
      "3951", "0284", "6719", "1463", "9502", "4837", "7260", "2096", "5148", "8375",
      "6097", "1328", "4785", "9216", "0639", "3570", "7802", "2461", "5913", "8054",
      "0742", "5389", "2916", "7603", "1257", "8430", "3165", "6094", "9578", "4201",
      "7319", "2840", "9057", "4682", "0195", "6728", "1403", "5967", "3814", "8526",
      "2673", "8105", "5491", "0836", "6214", "3958", "9480", "1729", "4352", "7061",
      "5104", "3627", "8259", "1946", "7380", "0592", "4718", "9832", "2065", "6493",
      "9265", "4708", "0831", "5379", "2614", "8197", "6540", "3082", "7956", "1423",
      "8536", "6912", "3487", "0754", "9128", "5263", "1609", "7845", "4371", "2980"};

  static List<TelexPatPageTransferDto> page(int size) {
    List<TelexPatPageTransferDto> page = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      TelexPatPageTransferDto dto = new TelexPatPageTransferDto();
      dto.setTrainId("t1");
      dto.setPageNumber(1);
      dto.setKey(KEYS[i]);
      dto.setSort(i);
      page.add(dto);
    }
    return page;
  }

  /** 按报底每十组一行生成拍发行，末组带页标；mutation 在此基础上制造差异。 */
  static List<List<String>> lines(List<TelexPatPageTransferDto> page) {
    List<List<String>> lines = new ArrayList<>();
    for (int i = 0; i < page.size(); i++) {
      if (i % 10 == 0) {
        lines.add(new ArrayList<>());
      }
      lines.getLast().add(page.get(i).getKey());
    }
    List<String> last = lines.getLast();
    last.set(last.size() - 1, last.getLast() + "-1");
    return lines;
  }

  static TelexPatStatisticalDto settle(int size, Consumer<List<List<String>>> mutation) {
    return settle(size, mutation, new ArrayList<>());
  }

  static TelexPatStatisticalDto settle(int size, Consumer<List<List<String>>> mutation,
      List<TelexPatValueTransferDto> out) {
    List<TelexPatPageTransferDto> page = page(size);
    List<List<String>> lines = lines(page);
    mutation.accept(lines);
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < lines.size(); i++) {
      if (i > 0) {
        text.append('\n');
      }
      text.append(String.join(" ", lines.get(i)));
    }
    TelexPatStatisticalDto ks = new TelexPatStatisticalDto();
    TelexPatUtils.handle(null, 1, out, page, text.toString(), ks, true);
    return ks;
  }

  @Test
  void lastPageWithPartialLineSettlesWithoutOverflow() {
    TelexPatStatisticalDto clean = settle(55, lines -> { });
    assertEquals(0, clean.getErrorCodeNumber());
    assertEquals(0, clean.getMuchLessGroupsNumber());

    TelexPatStatisticalDto oneError = settle(55, lines -> lines.get(5).set(2, "ZZZZ"));
    assertEquals(1, oneError.getErrorCodeNumber(), "末页不足一行时的错码仍按错码计");
    assertEquals(0, oneError.getMuchLessGroupsNumber());
  }

  @Test
  void capturedGroupCountExcludesLineBreaks() {
    assertEquals(100, settle(100, lines -> { }).getPatGroup(), "十行一百组按一百组计，换行不计组");
    assertEquals(99, settle(100, lines -> lines.get(1).remove(2)).getPatGroup());
    assertEquals(55, settle(55, lines -> { }).getPatGroup());
  }

  @Test
  void missingLineBreakSplitsAtTheReferenceNextLineWhereverItHappens() {
    for (int line = 0; line < 9; line++) {
      int merged = line;
      TelexPatStatisticalDto ks = settle(100, lines -> lines.get(merged).addAll(lines.remove(merged + 1)));
      assertEquals(1, ks.getLessReturnLineNumber(), "第" + (merged + 1) + "、" + (merged + 2) + "行合并只计一次少回行");
      assertEquals(0, ks.getErrorCodeNumber(), "拆行后后续行不应错位");
      assertEquals(0, ks.getMuchLessGroupsNumber());
      assertEquals(0, ks.getMuchLessLineNumber());
      assertEquals((merged + 1) * 10 + 1, ks.getEvidence().getFirst().referenceGroup(), "依据定位到下一行首组");
    }
  }

  @Test
  void threeLinesMergedCountTwoMissingLineBreaks() {
    TelexPatStatisticalDto ks = settle(100, lines -> {
      lines.get(3).addAll(lines.remove(4));
      lines.get(3).addAll(lines.remove(4));
    });
    assertEquals(2, ks.getLessReturnLineNumber());
    assertEquals(0, ks.getErrorCodeNumber());
    assertEquals(0, ks.getMuchLessGroupsNumber());
    assertEquals(0, ks.getMuchLessLineNumber());
  }

  @Test
  void missingLineBreakWithMissingGroupStillSplits() {
    TelexPatStatisticalDto ks = settle(100, lines -> {
      lines.get(2).remove(4);
      lines.get(2).addAll(lines.remove(3));
    });
    assertEquals(1, ks.getLessReturnLineNumber());
    assertEquals(1, ks.getMuchLessGroupsNumber(), "合并行里漏的一组仍按多少组计");
    assertEquals(0, ks.getErrorCodeNumber());
  }

  @Test
  void consecutiveExtraGroupsCountEachExtraGroup() {
    for (int extra = 1; extra <= 4; extra++) {
      int count = extra;
      TelexPatStatisticalDto ks = settle(100, lines -> {
        for (int k = 0; k < count; k++) {
          lines.get(1).add(3, "ZZZ" + k);
        }
      });
      assertEquals(count, ks.getMuchLessGroupsNumber(), "连续多" + count + "组按" + count + "组计（同手键）");
      assertEquals(0, ks.getErrorCodeNumber());
    }
  }

  @Test
  void extraGroupsAreRenderedInTheReferenceSlot() {
    List<TelexPatValueTransferDto> out = new ArrayList<>();
    settle(100, lines -> {
      lines.get(1).add(3, "ZZZ1");
      lines.get(1).add(4, "ZZZ2");
    }, out);
    List<String> slot = out.stream().filter(v -> v.getSort() == 13).map(TelexPatValueTransferDto::getValue).toList();
    assertEquals(List.of("0,ZZZ1", "1,ZZZ2", "2," + KEYS[13]), slot, "多组与对上的报底并入同一报底位置展示");
    assertEquals(100, out.stream().map(TelexPatValueTransferDto::getSort).distinct().count());
  }

  @Test
  void missingGroupsCountEachMissingGroup() {
    TelexPatStatisticalDto ks = settle(100, lines -> {
      lines.get(1).remove(2);
      lines.get(1).remove(2);
    });
    assertEquals(2, ks.getMuchLessGroupsNumber());
    assertEquals(0, ks.getErrorCodeNumber());
  }

  @Test
  void wrongGroupFollowedByInPlaceGroupIsErrorCodeEvenIfItRepeatsALaterKey() {
    // 第13组误拍成本行第16组的报底，但第14组原位对上：不判少组（同手键原位保护）
    TelexPatStatisticalDto ks = settle(100, lines -> lines.get(1).set(2, KEYS[15]));
    assertEquals(1, ks.getErrorCodeNumber());
    assertEquals(0, ks.getMuchLessGroupsNumber());
  }

  @Test
  void moreOrLessCodeAcceptsAMissingOrExtraCharacterAnywhere() {
    String key = KEYS[12];
    TelexPatStatisticalDto lackMiddle = settle(100, lines -> lines.get(1).set(2, key.charAt(0) + key.substring(2)));
    assertEquals(1, lackMiddle.getMuchLessCodeNumber(), "缺中间一码按多少码计（同手键）");
    assertEquals(0, lackMiddle.getErrorCodeNumber());

    TelexPatStatisticalDto extraMiddle = settle(100, lines -> lines.get(1).set(2, key.substring(0, 2) + "9" + key.substring(2)));
    assertEquals(1, extraMiddle.getMuchLessCodeNumber());

    TelexPatStatisticalDto wrongAndShort = settle(100, lines -> lines.get(1).set(2, "ZZZ"));
    assertEquals(1, wrongAndShort.getErrorCodeNumber(), "长度不同且含错码按错码计");
    assertEquals(0, wrongAndShort.getMuchLessCodeNumber());
  }

  @Test
  void swappedAdjacentGroupsHaveNoBunchItemInDatagram() {
    // 数据报评分项没有串组：互换按多少组判定，一多一少
    TelexPatStatisticalDto ks = settle(100, lines -> {
      List<String> row = lines.get(1);
      String first = row.get(2);
      row.set(2, row.get(3));
      row.set(3, first);
    });
    assertEquals(2, ks.getMuchLessGroupsNumber());
    assertEquals(0, ks.getErrorCodeNumber());
  }

  @Test
  void missingLinesInTheMiddleCountOnlyTheLines() {
    TelexPatStatisticalDto one = settle(100, lines -> lines.remove(3));
    assertEquals(1, one.getMuchLessLineNumber(), "中间漏一行只计一次多少行（同手键）");
    assertEquals(0, one.getErrorCodeNumber(), "漏行之后各行不应错位成错码");
    assertEquals(0, one.getMuchLessGroupsNumber());
    assertEquals(31, one.getEvidence().getFirst().referenceGroup());

    TelexPatStatisticalDto two = settle(100, lines -> {
      lines.remove(3);
      lines.remove(3);
    });
    assertEquals(2, two.getMuchLessLineNumber());
    assertEquals(0, two.getErrorCodeNumber());
  }

  @Test
  void missingLineWithLaterErrorsKeepsErrorsAsErrorCodes() {
    // 对应手键训练116：缺一行之后另有两处错码，不应借远处重复组制造多少组
    TelexPatStatisticalDto ks = settle(100, lines -> {
      lines.remove(3);
      lines.get(6).set(8, KEYS[89]);
      lines.get(7).set(8, "ZZZZ");
    });
    assertEquals(1, ks.getMuchLessLineNumber());
    assertEquals(0, ks.getMuchLessGroupsNumber());
    assertEquals(2, ks.getErrorCodeNumber());
  }

  @Test
  void repeatedLineInTheMiddleCountsOneExtraLine() {
    List<TelexPatValueTransferDto> out = new ArrayList<>();
    TelexPatStatisticalDto ks = settle(100, lines -> lines.add(3, new ArrayList<>(lines.get(2))), out);
    assertEquals(1, ks.getMuchLessLineNumber(), "中间重复一行只计一次多少行（同手键）");
    assertEquals(0, ks.getErrorCodeNumber());
    assertEquals(0, ks.getMuchLessGroupsNumber());
    assertEquals(100, out.stream().filter(v -> v.getSort() < 100).map(TelexPatValueTransferDto::getSort)
        .distinct().count(), "报底每组仍各有一个展示位置");
    assertEquals(10, out.stream().filter(v -> v.getSort() >= 100).count(), "多出的一行附加在报底之后");
  }

  @Test
  void extraLinesAfterTheLastLineCountEachLineOnce() {
    TelexPatStatisticalDto one = settle(100, lines -> lines.add(new ArrayList<>(List.of("ZZZZ", "YYYY"))));
    assertEquals(1, one.getMuchLessLineNumber(), "末尾多一行计一次");
    assertEquals(0, one.getErrorCodeNumber());

    TelexPatStatisticalDto two = settle(100, lines -> {
      lines.add(new ArrayList<>(List.of("ZZZZ")));
      lines.add(new ArrayList<>(List.of("YYYY")));
    });
    assertEquals(2, two.getMuchLessLineNumber());
  }

  @Test
  void missingTrailingLineCountsOnce() {
    TelexPatStatisticalDto ks = settle(100, lines -> {
      lines.remove(9);
      List<String> last = lines.getLast();
      last.set(9, last.get(9) + "-1");
    });
    assertEquals(1, ks.getMuchLessLineNumber());
    assertEquals(0, ks.getErrorCodeNumber());
  }
}
