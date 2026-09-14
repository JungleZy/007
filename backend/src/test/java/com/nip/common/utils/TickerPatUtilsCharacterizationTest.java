package com.nip.common.utils;

import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonParser;
import com.nip.dto.PostTelegramTrainFinishInfoDto;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.vo.PostTelegramTrainResolverVO;
import com.nip.dto.vo.PostTelegramTrainScoreVO;
import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import com.nip.service.MessageComparisonService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 3.1：TickerPatUtils 评分核心的 characterization 快照。
 * 输入样本与期望快照均从 classpath 读取（源在 src/test/resources/scoring/ 与其 expected/ 子目录，
 * patKeys 与规则取自 backend/database/project006.sql 真实数据），不依赖进程工作目录，
 * 因此在 IDE、仓库根目录或任何 surefire workingDirectory 下结果一致。
 *
 * <p>本类只读快照，不具备回写源码树的能力：评分逻辑发生真实修复时，assertEquals 的失败信息里
 * 已含完整的新输出，须逐项核对 diff 后手工改写 expected/*.json（快照最大 3KB）。
 * 刻意不提供「一键重生成」入口——那会让一次真实修复看起来像回归被静默吸收。
 */
class TickerPatUtilsCharacterizationTest {

  static class ResolverCase {
    List<String> patKeys;
    List<PostTelegramTrainContentAddParam> userContents;
  }

  static class GapCall {
    String patKey;
    int i;
    List<List<PostTelegramTrainFinishInfoDto.PatLogs>> patLogs;
  }

  static class GapCase {
    List<PostTelegramTrainFinishInfoDto> standards;
    List<GapCall> calls;
  }

  static class ComparisonCase {
    List<String> sources;
    List<String> patKeys;
    List<PostTelegramTrainContentAddParam> userContents;
    List<PostTelegramTrainFinishInfoDto> standards;
  }

  private static class ResolverResult {
    final PostTelegramTrainResolverVO resolver;
    final PostTelegramTrainScoreVO score;

    ResolverResult(PostTelegramTrainResolverVO resolver, PostTelegramTrainScoreVO score) {
      this.resolver = resolver;
      this.score = score;
    }
  }

  private static ResolverResult resolve(String caseFile) {
    ResolverCase c = JSONUtils.fromJson(resource(caseFile), new TypeToken<>() {
    });
    PostTelegramTrainScoreVO score = new PostTelegramTrainScoreVO();
    return new ResolverResult(TickerPatUtils.resolverMessage(c.patKeys, score, rule(), c.userContents), score);
  }

  private static List<List<Double>> numericRows(String encoded) {
    List<List<Double>> result = new ArrayList<>();
    for (var element : JsonParser.parseString(encoded).getAsJsonArray()) {
      String row = element.isJsonPrimitive() ? element.getAsString() : element.toString();
      result.add(JSONUtils.fromJson(row, new TypeToken<>() {
      }));
    }
    return result;
  }

  private static List<List<Integer>> valueRows(String encoded) {
    List<List<Integer>> result = new ArrayList<>();
    for (var element : JsonParser.parseString(encoded).getAsJsonArray()) {
      String row = element.isJsonPrimitive() ? element.getAsString() : element.toString();
      result.add(JSONUtils.fromJson(row, new TypeToken<>() {
      }));
    }
    return result;
  }

  private static List<List<PostTelegramTrainFinishInfoDto.PatLogs>> eventRows(String encoded) {
    List<List<PostTelegramTrainFinishInfoDto.PatLogs>> result = new ArrayList<>();
    for (var element : JsonParser.parseString(encoded).getAsJsonArray()) {
      var row = element.isJsonPrimitive() ? JsonParser.parseString(element.getAsString()) : element;
      if (row.getAsJsonArray().size() > 0 && row.getAsJsonArray().get(0).isJsonArray()) {
        result.addAll(JSONUtils.fromJson(row.toString(), new TypeToken<List<List<PostTelegramTrainFinishInfoDto.PatLogs>>>() {
        }));
      } else {
        result.add(JSONUtils.fromJson(row.toString(), new TypeToken<List<PostTelegramTrainFinishInfoDto.PatLogs>>() {
        }));
      }
    }
    return result;
  }

  private static void assertAligned(ResolverResult result) {
    PostTelegramTrainResolverVO vo = result.resolver;
    assertEquals(vo.getResolverMessage().size(), vo.getResolverPatLogs().size());
    assertEquals(vo.getResolverMessage().size(), vo.getResolverMoresTime().size());
    assertEquals(vo.getResolverMessage().size(), vo.getResolverMoresValue().size());
    for (int i = 0; i < vo.getResolverMessage().size(); i++) {
      List<List<PostTelegramTrainFinishInfoDto.PatLogs>> logs = eventRows(vo.getResolverPatLogs().get(i));
      List<List<Double>> times = numericRows(vo.getResolverMoresTime().get(i));
      List<List<Integer>> values = valueRows(vo.getResolverMoresValue().get(i));
      assertEquals(logs.size(), times.size(), "log/time alignment at " + i);
      assertEquals(logs.size(), values.size(), "log/value alignment at " + i);
    }
  }

  private static String resource(String name) {
    try (InputStream in = TickerPatUtilsCharacterizationTest.class.getResourceAsStream("/scoring/" + name)) {
      if (in == null) {
        throw new IllegalStateException("缺少测试资源 scoring/" + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static PostTelegramTrainRule rule() {
    return TickerPatUtils.parseContent(resource("grading-rule-type0.json"));
  }

  private static List<PostTelegramTrainFinishInfoDto> standards() {
    GapCase c = JSONUtils.fromJson(resource("gap-case-two-groups.json"), new TypeToken<>() {
    });
    return c.standards;
  }

  private static PostTelegramTrainContentAddParam validContent(String patKey) {
    PostTelegramTrainContentAddParam item = new PostTelegramTrainContentAddParam();
    item.setPatKeys(JSONUtils.toJson(List.of(patKey)));
    item.setPatLogs("[[{\"key\":0,\"value\":100}]]");
    item.setMoresTime("[[11,12]]");
    item.setMoresValue("[[1,0]]");
    return item;
  }

  private static void assertSnapshot(String name, Object actualPayload) {
    String actual = JSONUtils.gson.newBuilder().setPrettyPrinting().create().toJson(actualPayload);
    assertEquals(resource("expected/" + name + ".json").stripTrailing(), actual, name);
  }

  private static Map<String, Object> runResolver(String caseFile) {
    ResolverCase c = JSONUtils.fromJson(resource(caseFile), new TypeToken<>() {
    });
    PostTelegramTrainScoreVO scoreVO = new PostTelegramTrainScoreVO();
    PostTelegramTrainResolverVO vo = TickerPatUtils.resolverMessage(c.patKeys, scoreVO, rule(), c.userContents);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("resolverMessage", vo.getResolverMessage());
    payload.put("resolverPatLogs", vo.getResolverPatLogs());
    payload.put("resolverMoresTime", vo.getResolverMoresTime());
    payload.put("resolverMoresValue", vo.getResolverMoresValue());
    payload.put("scoreVO", scoreVO);
    return payload;
  }

  private static Map<String, Object> runComparison(String caseFile) {
    ComparisonCase c = JSONUtils.fromJson(resource(caseFile), new TypeToken<>() {
    });
    PostTelegramTrainScoreVO scoreVO = new PostTelegramTrainScoreVO();
    PostTelegramTrainStatisticsVO statisticsVO = new PostTelegramTrainStatisticsVO();
    PostTelegramTrainResolverVO vo = new MessageComparisonService().comparison(
        c.sources, c.patKeys, scoreVO, c.userContents, c.standards, rule(), statisticsVO);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("resolverMessage", vo.getResolverMessage());
    payload.put("resolverPatLogs", vo.getResolverPatLogs());
    payload.put("resolverMoresTime", vo.getResolverMoresTime());
    payload.put("resolverMoresValue", vo.getResolverMoresValue());
    payload.put("moreGroups", vo.getMoreGroups());
    payload.put("moreLine", vo.getMoreLine());
    payload.put("scoreVO", scoreVO);
    payload.put("statisticsVO", statisticsVO);
    return payload;
  }

  @Test
  void resolverMessageNormalGroupsWithBlank() {
    assertSnapshot("resolver-normal-with-blank", runResolver("resolver-case-normal-with-blank.json"));
  }

  @Test
  void resolverMessageGluedGroups() {
    ResolverResult result = resolve("resolver-case-glued.json");
    PostTelegramTrainResolverVO vo = result.resolver;
    assertIterableEquals(List.of("3729", "7201", "U3YU", "U3YU"), vo.getResolverMessage());
    assertEquals(4, result.score.getGroupScore());
    assertEquals(List.of(List.of(1d), List.of(2d), List.of(3d), List.of(4d),
        List.of(5d), List.of(6d), List.of(7d), List.of(8d), List.of(11d), List.of(12d),
        List.of(13d), List.of(14d), List.of(15d), List.of(16d), List.of(17d), List.of(18d)),
        vo.getResolverMoresTime().stream().flatMap(row -> numericRows(row).stream()).toList());
    assertAligned(result);
  }

  @Test
  void resolverMessageQuestionMarkCorrections() {
    ResolverResult result = resolve("resolver-case-question-marks.json");
    PostTelegramTrainResolverVO vo = result.resolver;
    assertIterableEquals(List.of("3729", "?", "7201", "201"), vo.getResolverMessage());
    assertEquals(3, result.score.getAlterErrorScore());
    assertEquals(List.of(List.of(10d), List.of(91d), List.of(20d), List.of(42d), List.of(43d), List.of(44d)),
        vo.getResolverMoresTime().stream().flatMap(row -> numericRows(row).stream()).toList());
    assertAligned(result);
  }

  @Test
  void checkDotLineGapTwoGroups() {
    GapCase c = JSONUtils.fromJson(resource("gap-case-two-groups.json"), new TypeToken<>() {
    });
    PostTelegramTrainScoreVO scoreVO = new PostTelegramTrainScoreVO();
    PostTelegramTrainStatisticsVO statisticsVO = new PostTelegramTrainStatisticsVO();
    PostTelegramTrainRule rule = rule();
    for (GapCall call : c.calls) {
      TickerPatUtils.checkDotLineGap(call.patKey, call.i, JSONUtils.toJson(call.patLogs),
          c.standards, rule, false, statisticsVO, scoreVO);
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("scoreVO", scoreVO);
    payload.put("statisticsVO", statisticsVO);
    assertSnapshot("gap-two-groups", payload);
  }

  @Test
  void resolverMessageRejectsCorruptMoresTimeWithoutErasingParsedLogs() {
    PostTelegramTrainContentAddParam item = validContent("ABCD");
    item.setPatLogs("[[{\"key\":\"A\"}]]");
    item.setMoresTime("[broken");

    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> TickerPatUtils.resolverMessage(
            List.of("ABCD"), new PostTelegramTrainScoreVO(), rule(), List.of(item)));

    assertTrue(error.getMessage().contains("moresTime"));
    assertTrue(error.getMessage().contains("index=0"));
  }

  @Test
  void checkDotLineGapRejectsCorruptPatLogs() {
    assertThrows(IllegalStateException.class, () -> TickerPatUtils.checkDotLineGap(
        "A", 0, "[broken", standards(), rule(), false,
        new PostTelegramTrainStatisticsVO(), new PostTelegramTrainScoreVO()));
  }

  @Test
  void comparisonMoreGroupDetected() {
    assertSnapshot("comparison-more-group", runComparison("comparison-case-more-group.json"));
  }

  @Test
  void comparisonMoreLineDetected() {
    assertSnapshot("comparison-more-line", runComparison("comparison-case-more-line.json"));
  }

  @Test
  void comparisonLessLineDetected() {
    assertSnapshot("comparison-less-line", runComparison("comparison-case-less-line.json"));
  }
}
