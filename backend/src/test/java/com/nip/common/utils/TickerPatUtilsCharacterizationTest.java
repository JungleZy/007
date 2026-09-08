package com.nip.common.utils;

import com.google.gson.reflect.TypeToken;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Task 3.1：TickerPatUtils 评分核心的 characterization 快照。
 * 输入样本在 src/test/resources/scoring/（patKeys 与规则取自 backend/database/project006.sql 真实数据），
 * 期望输出在 src/test/resources/scoring/expected/。
 * 重新生成快照：SCORING_UPDATE=1 运行本测试后人工核对 diff。
 */
class TickerPatUtilsCharacterizationTest {

  private static final Path EXPECTED_DIR = Path.of("src/test/resources/scoring/expected");

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
    Path expectedFile = EXPECTED_DIR.resolve(name + ".json");
    if ("1".equals(System.getenv("SCORING_UPDATE"))) {
      try {
        Files.createDirectories(EXPECTED_DIR);
        Files.writeString(expectedFile, actual + "\n");
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return;
    }
    if (!Files.exists(expectedFile)) {
      fail("缺少快照 " + expectedFile + "，先用 SCORING_UPDATE=1 生成并人工核对");
    }
    try {
      assertEquals(Files.readString(expectedFile).stripTrailing(), actual, name);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
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
    assertSnapshot("resolver-glued", runResolver("resolver-case-glued.json"));
  }

  @Test
  void resolverMessageQuestionMarkCorrections() {
    assertSnapshot("resolver-question-marks", runResolver("resolver-case-question-marks.json"));
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
