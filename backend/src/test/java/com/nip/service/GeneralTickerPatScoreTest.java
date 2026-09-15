package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.UserDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainPageDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainUserDao;
import com.nip.dto.GeneralTickerPatTrainUserDto;
import com.nip.dto.CaptureInterval;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainContentAddParam;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainContentValueVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainFinishInfoVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainFinishVO;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainPageEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainUserEntity;
import com.nip.service.general.GeneralTickerPatService;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class GeneralTickerPatScoreTest {

  private static final String RULE_JSON = """
      {"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":40,"r":2,"l":1},"skew":51,
       "code":{"dot":{"base":30,"l":1,"r":2,"max":1},"dash":{"base":50,"l":1,"r":2,"max":5}},
       "gap":{"little":{"base":40,"l":1,"r":2,"max":4},"middle":{"base":60,"l":1,"r":2,"max":4},
              "large":{"base":90,"l":1,"r":2,"max":4}},
       "other":{"errorCode":{"l":3,"max":10},"quantoCode":{"l":1,"max":4},"quantoGroup":{"l":1,"max":4},
                "alterError":{"l":1,"max":4},"quantoRow":{"l":1,"max":4},"bunchGroup":{"l":1,"max":4}}}
      """;

  private static final String MISSING_GROUP_RULE = """
      {"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":40,"r":0,"l":0},"skew":51,
       "code":{"dot":{"base":30,"l":0,"r":0,"max":0},"dash":{"base":50,"l":0,"r":0,"max":0}},
       "gap":{"little":{"base":40,"l":0,"r":0,"max":0},"middle":{"base":60,"l":0,"r":0,"max":0},
              "large":{"base":90,"l":0,"r":0,"max":0}},
       "other":{"errorCode":{"l":0,"max":0},"quantoCode":{"l":0,"max":0},"quantoGroup":{"l":2,"max":1000},
                "alterError":{"l":0,"max":0},"quantoRow":{"l":0,"max":0},"bunchGroup":{"l":0,"max":0}}}
      """;

  @Inject GeneralTickerPatService service;
  @Inject GeneralTickerPatTrainDao trainDao;
  @Inject GeneralTickerPatTrainPageDao pageDao;
  @Inject GeneralTickerPatTrainUserDao trainUserDao;
  @Inject GradingRuleDao ruleDao;
  @Inject UserDao userDao;

  @Test
  void finishCapsDashIndependentlyAndPreservesOtherDeductionsAndConfiguredTotal() {
    String token = "general-dash-" + UUID.randomUUID();
    UserEntity user = Fixtures.user(userDao, token);
    GradingRuleEntity rule = new GradingRuleEntity();
    rule.setTitle("General dash cap regression");
    rule.setScore(150);
    rule.setContent(RULE_JSON);
    rule = ruleDao.save(rule);

    GeneralTickerPatTrainEntity train = trainDao.save(new GeneralTickerPatTrainEntity()
        .setName("General dash cap regression")
        .setType(0).setTrainType(1).setCodeSort(0).setIsRandom(0).setIsCable(0).setIsAverage(0)
        .setMessageNumber(1).setRuleId(rule.getId()).setCreateUser(user.getId())
        .setRuleContent(RULE_JSON).setRuleScore(150)
        .setStatus(1).setStartTime(LocalDateTime.now().minusMinutes(1)));
    trainUserDao.save(new GeneralTickerPatTrainUserEntity()
        .setTrainId(train.getId()).setUserId(user.getId()).setRole(0).setIsFinish(0).setCaptureStartedAt(train.getStartTime()));
    GeneralTickerPatTrainPageEntity page = pageDao.save(new GeneralTickerPatTrainPageEntity()
        .setTrainId(train.getId()).setFloorNumber(1).setSort(0).setMoresKey("[\"1\",\"1\",\"1\",\"1\"]"));

    // A wrong group activates timing deductions; a matching group only records timing statistics.
    GeneralTickerPatTrainContentAddParam content = new GeneralTickerPatTrainContentAddParam();
    content.setId(page.getId());
    content.setMoresKey(page.getMoresKey());
    content.setPatKeys("[\"9\",\"9\",\"9\",\"9\"]");
    content.setMoresValue("[[1,0],[1,0],[1,0],[1,0]]");
    content.setMoresTime("[[400,140],[400,100],[400,100],[400,100]]");
    List<List<Map<String, Object>>> logs = new ArrayList<>();
    for (int word = 0; word < 4; word++) {
      logs.add(List.of(
          Map.of("name", "间隔", "key", 2, "value", word == 0 ? 700 : 300),
          Map.of("name", "划", "key", 1, "value", 400),
          Map.of("name", "间隔", "key", 2, "value", 100),
          Map.of("name", "点", "key", 0, "value", word == 0 ? 140 : 100)));
    }
    content.setPatLogs(JSONUtils.toJson(logs));

    GeneralTickerPatTrainFinishInfoVO standard = new GeneralTickerPatTrainFinishInfoVO();
    standard.setDot(100);
    standard.setLine(300);
    standard.setCodeGap(100);
    standard.setWordGap(300);
    standard.setGroupGap(700);
    standard.setOffSize(51);
    GeneralTickerPatTrainContentValueVO upload = new GeneralTickerPatTrainContentValueVO();
    upload.setTrainId(train.getId());
    upload.setFloorNumber(1);
    upload.setMessageBody(List.of(content));
    upload.setStandard(List.of(standard));
    upload.setAttempt(0);
    upload.setCaptureIntervals(List.of(new CaptureInterval(0, 6000)));
    service.saveContentValue(upload, token);

    GeneralTickerPatTrainFinishVO finish = new GeneralTickerPatTrainFinishVO();
    finish.setId(train.getId());
    finish.setAttempt(0);
    service.finish(finish, token);

    // Read the persisted projection used by the training report, outside the finish transaction.
    GeneralTickerPatTrainUserDto result = trainUserDao.findByTrainIdToMap(train.getId(), user.getId()).getFirst();
    Map<String, Integer> deductions = JSONUtils.fromJson(result.getDeductInfo(), new TypeToken<>() {});
    assertEquals(8, deductions.get("lineNumber"));
    assertEquals(5, deductions.get("lineScore"), "Four coarse dashes exceed dash.max=5, not dot.max=1");
    assertEquals(1, deductions.get("dotMinScore"), "The independent dot cap remains one");
    assertEquals(3, deductions.get("errorWord"), "The wrong-group deduction remains unchanged");
    for (String key : List.of("codeGapScore", "wordGapScore", "groupGapScore", "alterErrorScore",
        "quantoCode", "quantoGroup", "quantoRow", "bunchGroup", "wpmScore")) {
      assertEquals(0, deductions.get(key), key);
    }
    assertEquals(0, new BigDecimal("141").compareTo(result.getScore()), "150 - 5 dash - 1 dot - 3 wrong group");
    assertEquals(1, result.getIsFinish());
  }

  @Test
  void whollyMissingHundredGroupPageReportsAndDeductsOneHundredGroups() {
    assertMissingGroups(100, List.of(), false, 100, 200);
  }

  @Test
  void missingPartialLastPageDeductsOnlyItsFiftyGroups() {
    assertMissingGroups(150, List.of(1), false, 50, 100);
  }

  @Test
  void missingFirstAndThirdPagesUseTheirOwnSizes() {
    assertMissingGroups(250, List.of(2), false, 150, 300);
  }

  @Test
  void wholeMissingPagesPreservePreviouslyDetectedWithinPageOmissions() {
    assertMissingGroups(150, List.of(1), true, 50, 102);
  }

  @Test
  void correctlySubmittedFullPageHasNoMissingGroupDeduction() {
    assertMissingGroups(100, List.of(1), false, 0, 0);
  }

  private void assertMissingGroups(int groups, List<Integer> submittedPages, boolean omitSecondGroup,
      int expectedLack, int expectedDeduction) {
    String token = "general-missing-" + UUID.randomUUID();
    UserEntity user = Fixtures.user(userDao, token);
    GeneralTickerPatTrainEntity train = trainDao.save(new GeneralTickerPatTrainEntity()
        .setName("Missing group accounting").setType(0).setTrainType(1).setCodeSort(0)
        .setIsRandom(0).setIsCable(0).setIsAverage(0).setMessageNumber(groups)
        .setCreateUser(user.getId()).setRuleContent(MISSING_GROUP_RULE).setRuleScore(1000)
        .setProtocolVersion(1).setStatus(1).setStartTime(LocalDateTime.now().minusMinutes(1)));
    trainUserDao.save(new GeneralTickerPatTrainUserEntity()
        .setTrainId(train.getId()).setUserId(user.getId()).setRole(0).setIsFinish(0)
        .setCaptureStartedAt(train.getStartTime()));

    for (int page = 1; page <= (groups + 99) / 100; page++) {
      List<GeneralTickerPatTrainContentAddParam> body = new ArrayList<>();
      int pageGroups = Math.min(100, groups - (page - 1) * 100);
      for (int index = 0; index < pageGroups; index++) {
        List<String> keys = String.valueOf(1000 + (page - 1) * 100 + index).chars()
            .mapToObj(value -> String.valueOf((char) value)).toList();
        GeneralTickerPatTrainPageEntity source = pageDao.save(new GeneralTickerPatTrainPageEntity()
            .setTrainId(train.getId()).setFloorNumber(page).setSort(index).setMoresKey(JSONUtils.toJson(keys)));
        if (omitSecondGroup && index == 1) continue;
        GeneralTickerPatTrainContentAddParam content = new GeneralTickerPatTrainContentAddParam();
        content.setId(source.getId());
        content.setMoresKey(source.getMoresKey());
        content.setPatKeys(source.getMoresKey());
        content.setMoresValue("[[0],[0],[0],[0]]");
        content.setMoresTime("[[1],[1],[1],[1]]");
        content.setPatLogs(JSONUtils.toJson(keys.stream().map(key -> List.of(
            Map.of("key", 2, "value", 1), Map.of("key", 0, "value", 1))).toList()));
        body.add(content);
      }
      if (!submittedPages.contains(page)) continue;
      GeneralTickerPatTrainFinishInfoVO standard = new GeneralTickerPatTrainFinishInfoVO();
      standard.setDot(1);
      standard.setLine(3);
      standard.setCodeGap(1);
      standard.setWordGap(3);
      standard.setGroupGap(7);
      standard.setOffSize(51);
      GeneralTickerPatTrainContentValueVO upload = new GeneralTickerPatTrainContentValueVO();
      upload.setTrainId(train.getId());
      upload.setFloorNumber(page);
      upload.setAttempt(0);
      upload.setMessageBody(body);
      upload.setStandard(List.of(standard));
      upload.setCaptureIntervals(List.of(new CaptureInterval((page - 1) * 1000L, page * 1000L)));
      service.saveContentValue(upload, token);
    }

    GeneralTickerPatTrainFinishVO finish = new GeneralTickerPatTrainFinishVO();
    finish.setId(train.getId());
    finish.setAttempt(0);
    service.finish(finish, token);

    GeneralTickerPatTrainUserDto result = trainUserDao.findByTrainIdToMap(train.getId(), user.getId()).getFirst();
    Map<String, Integer> deductions = JSONUtils.fromJson(result.getDeductInfo(), new TypeToken<>() {});
    assertEquals(expectedLack, result.getLack());
    assertEquals(expectedDeduction, deductions.get("quantoGroup"));
    assertEquals(0, BigDecimal.valueOf(1000 - expectedDeduction).compareTo(result.getScore()));
  }
}
