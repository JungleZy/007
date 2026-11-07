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
}
