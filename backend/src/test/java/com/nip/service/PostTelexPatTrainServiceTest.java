package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.PostTelexPatTrainDao;
import com.nip.dao.PostTelexPatTrainPageDao;
import com.nip.dao.UserDao;
import com.nip.dto.CaptureInterval;
import com.nip.dto.PostTelexPatTrainDto;
import com.nip.dto.vo.PostTelexPatTrainPageValueVO;
import com.nip.dto.vo.PostTelexPatTrainVO;
import com.nip.dto.vo.param.PostTelexPatTrainFinishParam;
import com.nip.dto.vo.param.PostTelexPatTrainParam;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.PostTelexPatTrainEntity;
import com.nip.entity.PostTelexPatTrainPageEntity;
import com.nip.testsupport.Fixtures;


import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nip.common.constants.PostTelexPatTrainStatusEnum.FINISH;
import static com.nip.common.constants.PostTelexPatTrainStatusEnum.UNDERWAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 3.5 P1-09：finish 的幂等守卫曾被注释掉，重复 finish 会把「报底+用户值」混合行
 * 再解析一遍并全表删除重写。已完成训练必须直接返回，不重新结算。
 * （守卫失效时本用例会走 countScore：无规则/无页数据直接抛异常。）
 */
@QuarkusTest

class PostTelexPatTrainServiceTest {
  @Inject PostTelexPatTrainService service;
  @Inject PostTelexPatTrainDao trainDao;
  @Inject PostTelexPatTrainPageDao pageDao;
  @Inject GradingRuleDao gradingRuleDao;
  @Inject UserDao userDao;

  @ParameterizedTest
  @CsvSource({"80,73", "100,93"})
  void finishDeductsFromCapturedRuleScoreOnlyOnce(int fullScore, int expectedScore) {
    String token = "telex-score-" + UUID.randomUUID();
    Fixtures.user(userDao, token);
    GradingRuleEntity rule = new GradingRuleEntity();
    rule.setType(2);
    rule.setTitle("telex-score-" + fullScore);
    rule.setScore(fullScore);
    rule.setContent("{\"rateUnit\":\"CHARACTERS_PER_MINUTE\",\"wpm\":{\"base\":10,\"r\":1,\"l\":2},\"other\":{\"errorCode\":7,"
        + "\"muchLessGroups\":3,\"correctMistakes\":2,\"lessPage\":4,\"lessReturnLine\":5,"
        + "\"muchLessLine\":6,\"muchLessCode\":3,\"errorPage\":4,\"nonStandart\":2}}");
    rule = gradingRuleDao.save(rule);

    PostTelexPatTrainDto dto = new PostTelexPatTrainDto();
    dto.setName("telex-score-" + fullScore);
    dto.setRuleId(rule.getId());
    dto.setTrainType(0);
    dto.setType(0);
    dto.setPatType(0);
    dto.setGroupNumber(10);
    String trainId = service.save(dto, token).getId();
    PostTelexPatTrainParam trainParam = new PostTelexPatTrainParam();
    trainParam.setId(trainId);
    trainParam.setAttempt(0);
    service.begin(trainParam, token);
    PostTelexPatTrainEntity started = trainDao.findById(trainId);
    started.setStartTime(LocalDateTime.now().minusSeconds(241));
    trainDao.save(started);

    // 读取真实生成的数字报底，仅替换首组；四位字母不可能匹配其它数字组。
    List<String> groups = new ArrayList<>(pageDao.findByTrainIdOrderBySort(trainId).stream()
        .map(PostTelexPatTrainPageEntity::getKey).toList());
    groups.set(0, "AAAA");
    PostTelexPatTrainPageValueVO page = new PostTelexPatTrainPageValueVO();
    page.setTrainId(trainId);
    page.setPageNumber(1);
    page.setPatValue(String.join(" ", groups));
    page.setAttempt(0);
    page.setCaptureIntervals(List.of(new CaptureInterval(0, 240000)));
    service.finishPage(page, token);

    // 后续编辑规则不应改变本次训练创建时捕获的满分。
    rule.setScore(fullScore + 20);
    gradingRuleDao.save(rule);
    PostTelexPatTrainFinishParam finishParam = new PostTelexPatTrainFinishParam();
    finishParam.setId(trainId);
    finishParam.setAttempt(0);
    PostTelexPatTrainVO result = service.finish(finishParam, token);

    assertEquals(0, BigDecimal.valueOf(expectedScore).compareTo(new BigDecimal(result.getScore())));
    assertEquals(1, result.getErrorNumber());
    assertEquals(FINISH.getStatus(), result.getStatus());
    assertEquals("10", result.getSpeed());
    assertEquals(result.getSpeed(), result.getTotalSpeed());
    assertEquals(240, result.getValidTime());
    Map<String, String> deductions = JSONUtils.fromJson(result.getDeductInfo(),
        new TypeToken<Map<String, String>>() {});
    assertEquals("1", deductions.get("errorCodeNumber"));
    assertEquals("-7", deductions.get("errorCodeScore"));
    BigDecimal adjustment = deductions.entrySet().stream()
        .filter(entry -> entry.getKey().endsWith("Score"))
        .map(entry -> new BigDecimal(entry.getValue()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertEquals(0, BigDecimal.valueOf(fullScore).add(adjustment)
        .compareTo(new BigDecimal(result.getScore())), "最终分必须与返回的扣分明细一致");

    PostTelexPatTrainVO repeated = service.finish(finishParam, token);
    PostTelexPatTrainVO persisted = service.detail(trainParam, token);
    assertEquals(result.getScore(), repeated.getScore(), "重复完成不得从已扣分结果再次扣分");
    assertEquals(result.getDeductInfo(), repeated.getDeductInfo());
    assertEquals(result.getScore(), persisted.getScore());
    assertEquals(result.getDeductInfo(), persisted.getDeductInfo());
  }

  @ParameterizedTest
  @CsvSource({"0,5", "0,10", "0,20", "4,5", "4,10", "4,20"})
  void omittedWpmCoefficientsSettleBothScoringBranches(int trainType, int base) {
    String token = "telex-missing-wpm-" + UUID.randomUUID();
    PostTelexPatTrainPageValueVO page = startCapturedTraining(token, trainType, base);
    service.finishPage(page, token);
    PostTelexPatTrainFinishParam finish = new PostTelexPatTrainFinishParam();
    finish.setId(page.getTrainId());
    finish.setAttempt(0);

    PostTelexPatTrainVO result = service.finish(finish, token);

    assertEquals(FINISH.getStatus(), result.getStatus());
    assertEquals(0, new BigDecimal("100").compareTo(new BigDecimal(result.getScore())));
    assertEquals(0, BigDecimal.TEN.compareTo(new BigDecimal(result.getSpeed())));
    Map<String, String> deductions = JSONUtils.fromJson(result.getDeductInfo(), new TypeToken<Map<String, String>>() {});
    if (trainType == 4) {
      assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(deductions.get("speedLowScore"))));
      assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(deductions.get("speedOverTopScore"))));
    } else if (base != 10) {
      assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(deductions.get("speedScore"))));
    }
  }

  @Test
  void exactPageRetryAfterSettlementReturnsOriginalReceiptWithoutChangingResult() {
    String token = "telex-page-retry-" + UUID.randomUUID();
    PostTelexPatTrainPageValueVO page = startCapturedTraining(token, 0, 10);
    var submitted = service.finishPage(page, token);
    PostTelexPatTrainFinishParam finish = new PostTelexPatTrainFinishParam();
    finish.setId(page.getTrainId());
    finish.setAttempt(0);
    service.finish(finish, token);
    PostTelexPatTrainParam detail = new PostTelexPatTrainParam();
    detail.setId(page.getTrainId());
    PostTelexPatTrainVO before = service.detail(detail, token);

    var repeated = service.finishPage(page, token);
    PostTelexPatTrainVO after = service.detail(detail, token);

    assertEquals(submitted.getReceivedAt(), repeated.getReceivedAt());
    assertEquals(submitted.getCaptureIntervals(), repeated.getCaptureIntervals());
    assertEquals(submitted.getCodeAll(), repeated.getCodeAll());
    assertEquals(before.getScore(), after.getScore());
    assertEquals(before.getDeductInfo(), after.getDeductInfo());
    assertEquals(before.getEndTime(), after.getEndTime());
    page.setPatValue(page.getPatValue() + " X");
    assertThrows(IllegalArgumentException.class, () -> service.finishPage(page, token));
    assertEquals(before.getScore(), service.detail(detail, token).getScore());
  }

  private static String zeroPenaltyRule(int base) {
    return "{\"rateUnit\":\"CHARACTERS_PER_MINUTE\",\"wpm\":{\"base\":" + base + "},\"other\":{" 
        + "\"errorCode\":0,\"muchLessGroups\":0,\"correctMistakes\":0,\"lessPage\":0,\"lessReturnLine\":0,"
        + "\"muchLessLine\":0,\"muchLessCode\":0,\"errorPage\":0,\"nonStandart\":0}}";
  }

  private PostTelexPatTrainPageValueVO startCapturedTraining(String token, int trainType, int base) {
    Fixtures.user(userDao, token);
    GradingRuleEntity rule = new GradingRuleEntity();
    rule.setType(2);
    rule.setTitle("telex-capture-" + UUID.randomUUID());
    rule.setScore(100);
    rule.setContent(zeroPenaltyRule(base));
    rule = gradingRuleDao.save(rule);
    PostTelexPatTrainDto dto = new PostTelexPatTrainDto();
    dto.setName("telex-capture");
    dto.setRuleId(rule.getId());
    dto.setTrainType(trainType);
    dto.setType(0);
    dto.setPatType(0);
    dto.setGroupNumber(10);
    String trainId = service.save(dto, token).getId();
    PostTelexPatTrainParam begin = new PostTelexPatTrainParam();
    begin.setId(trainId);
    begin.setAttempt(0);
    service.begin(begin, token);
    PostTelexPatTrainEntity train = trainDao.findById(trainId);
    train.setStartTime(LocalDateTime.now().minusSeconds(241));
    trainDao.save(train);
    PostTelexPatTrainPageValueVO page = new PostTelexPatTrainPageValueVO();
    page.setTrainId(trainId);
    page.setPageNumber(1);
    page.setAttempt(0);
    page.setPatValue(String.join(" ", pageDao.findByTrainIdOrderBySort(trainId).stream()
        .map(PostTelexPatTrainPageEntity::getKey).toList()));
    page.setCaptureIntervals(List.of(new CaptureInterval(0, 240000)));
    return page;
  }

  @Test
  void finishOnFinishedTrainReturnsWithoutRecount() {
    PostTelexPatTrainEntity e = new PostTelexPatTrainEntity();
    String token = "telex-finished-" + UUID.randomUUID();
    e.setCreateUser(Fixtures.user(userDao, token).getId());
    e.setStatus(FINISH.getStatus());
    e.setTrainType(4);
    e.setScore("88");
    e = trainDao.save(e);

    PostTelexPatTrainFinishParam param = new PostTelexPatTrainFinishParam();
    param.setId(e.getId());
    param.setAttempt(0);

    PostTelexPatTrainVO vo = service.finish(param, token);

    assertEquals("88", vo.getScore(), "已完成训练的分数不得被重复结算覆盖");
    assertEquals("88", trainDao.findById(e.getId()).getScore());
  }

  /**
   * Task 2.3 P2：结算重建走「先构建校验、后删除写入」。报底页号不连续属于构建阶段失败，
   * 必须在 deleteByTrainId 之前抛出，旧报底行数与内容原封不动。
   * （修复前 delete 先执行、再用坏集合覆盖，目标表是 MyISAM 时旧报底永久丢失。）
   */
  @Test
  void finishWithNonContiguousPagesKeepsOldPagesIntact() {
    PostTelexPatTrainEntity e = new PostTelexPatTrainEntity();
    String token = "telex-gap-" + UUID.randomUUID();
    e.setCreateUser(Fixtures.user(userDao, token).getId());
    e.setProtocolVersion(1);
    e.setStartTime(LocalDateTime.now().minusSeconds(1));
    e.setPauseIntervals("[]");
    e.setIsCable(1);
    e.setStatus(UNDERWAY.getStatus());
    e.setTrainType(0);
    e.setRuleContent(zeroPenaltyRule(10));
    e.setScore("100");
    e = trainDao.save(e);
    String trainId = e.getId();

    // 报底只有第 1 页与第 3 页，缺第 2 页
    seedPage(trainId, 1);
    seedPage(trainId, 3);

    PostTelexPatTrainFinishParam param = new PostTelexPatTrainFinishParam();
    param.setId(trainId);
    param.setAttempt(0);

    assertThrows(IllegalStateException.class, () -> service.finish(param, token),
        "报底页号不连续必须在删除之前被拒绝");

    List<PostTelexPatTrainPageEntity> after = pageDao.findByTrainIdOrderBySort(trainId);
    assertEquals(20, after.size(), "构建阶段失败时旧报底不得被删除");
    assertEquals(
        List.of("0101", "0102", "0103", "0104", "0105", "0106", "0107", "0108", "0109", "0110",
            "0301", "0302", "0303", "0304", "0305", "0306", "0307", "0308", "0309", "0310"),
        after.stream().map(PostTelexPatTrainPageEntity::getKey).sorted().toList(),
        "旧报底内容必须原样保留");
    assertTrue(after.stream().allMatch(p -> p.getValue() == null), "旧报底不得被回写用户拍发值");
  }

  /** 播种一页 10 组报底：key 形如 0101…0110，sort 0..9，value 留空表示尚未回写 */
  private void seedPage(String trainId, int pageNumber) {
    for (int i = 0; i < 10; i++) {
      PostTelexPatTrainPageEntity page = new PostTelexPatTrainPageEntity();
      page.setTrainId(trainId);
      page.setPageNumber(pageNumber);
      page.setSort(i);
      page.setKey(String.format("%02d%02d", pageNumber, i + 1));
      pageDao.save(page);
    }
  }
}
