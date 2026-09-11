package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.UserDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainPageDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainUserDao;
import com.nip.dto.GeneralTickerPatTrainUserDto;
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
      {"wpm":{"base":70,"r":2,"l":1},"skew":51,
       "code":{"dot":{"base":30,"l":1,"r":2,"max":1},"dash":{"base":50,"l":1,"r":2,"max":5}},
       "gap":{"little":{"base":40,"l":1,"r":2,"max":4},"middle":{"base":60,"l":1,"r":2,"max":4},
              "large":{"base":90,"l":1,"r":2,"max":4}},
       "other":{"errorCode":{"l":3,"max":10},"quantoCode":{"l":1,"max":4},"quantoGroup":{"l":1,"max":4},
                "alterError":{"l":1,"max":4},"quantoRow":{"l":1,"max":4},"bunchGroup":{"l":1,"max":4}}}
      """;

  @Inject GeneralTickerPatService service;
  @Inject GeneralTickerPatTrainDao trainDao;
  @Inject GeneralTickerPatTrainPageDao pageDao;
  @Inject GeneralTickerPatTrainUserDao trainUserDao;
  @Inject GradingRuleDao ruleDao;
  @Inject UserDao userDao;

  @Test
  void finishCapsDashIndependentlyAndPreservesOtherDeductionsAndConfiguredTotal() {
    UserEntity user = Fixtures.user(userDao, "general-dash-" + UUID.randomUUID());
    GradingRuleEntity rule = new GradingRuleEntity();
    rule.setTitle("General dash cap regression");
    rule.setScore(150);
    rule.setContent(RULE_JSON);
    rule = ruleDao.save(rule);

    GeneralTickerPatTrainEntity train = trainDao.save(new GeneralTickerPatTrainEntity()
        .setName("General dash cap regression")
        .setType(0).setTrainType(1).setCodeSort(0).setIsRandom(0).setIsCable(0).setIsAverage(0)
        .setMessageNumber(1).setRuleId(rule.getId()).setCreateUser(user.getId())
        .setStatus(1).setStartTime(LocalDateTime.now().minusMinutes(1)));
    trainUserDao.save(new GeneralTickerPatTrainUserEntity()
        .setTrainId(train.getId()).setUserId(user.getId()).setRole(0).setIsFinish(0));
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
    upload.setUserId(user.getId());
    upload.setFloorNumber(1);
    upload.setMessageBody(List.of(content));
    upload.setStandard(List.of(standard));
    upload.setSpeed("70");
    upload.setErrorNumber(1);
    upload.setAccuracy("0.00");
    service.saveContentValue(upload);

    GeneralTickerPatTrainFinishVO finish = new GeneralTickerPatTrainFinishVO();
    finish.setId(train.getId());
    finish.setUserId(user.getId());
    service.finish(finish);

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
}
