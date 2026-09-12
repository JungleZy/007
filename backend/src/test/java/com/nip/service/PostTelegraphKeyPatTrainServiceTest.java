package com.nip.service;

import com.nip.common.exception.ForbiddenException;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.*;
import com.nip.dto.CaptureInterval;
import com.nip.dto.PostTelegraphKeyPatTrainActionDto;
import com.nip.dto.PostTelegraphKeyPatTrainDto;
import com.nip.dto.PostTelegraphKeyPatTrainPageDto;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageMessageVO;
import com.nip.dto.vo.PostTelegraphKeyPatTrainVO;
import com.nip.entity.*;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.nip.common.constants.PostTelegraphKeyPatTrainEnum.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PostTelegraphKeyPatTrainServiceTest {
  private static final String RULE = """
      {"rateUnit":"FOUR_CHARACTER_GROUPS_PER_MINUTE","wpm":{"base":4,"r":2,"l":3},
       "other":{"errorCode":0,"muchLessCode":0,"muchLessLine":0,"muchLessGroups":0,
                "alterError":0,"bunchGroup":0,"lessGap":0}}
      """;

  @Inject PostTelegraphKeyPatTrainService service;
  @Inject PostTelegraphKeyPatTrainDao trainDao;
  @Inject PostTelegraphKeyPatTrainPageDao pageDao;
  @Inject PostTelegraphKeyPatTrainPageValueDao valueDao;
  @Inject PostTelegraphKeyPatTrainRawPageDao rawDao;
  @Inject GradingRuleDao gradingRuleDao;
  @Inject UserDao userDao;

  @Test
  void finishOnFinishedLegacyTrainReturnsWithoutRecountButStillChecksOwner() {
    String token = token();
    PostTelegraphKeyPatTrainEntity train = seed(token, 1);
    train.setProtocolVersion(0).setStatus(FINISH.getStatus()).setScore(new BigDecimal("77"));
    trainDao.save(train);
    assertEquals(0, new BigDecimal("77").compareTo(service.finish(action(train), token).getScore()));
    // 已完成的旧训练对非创建者仍必须是授权拒绝（207），不能因为「反正不重算」就退化成参数错误。
    assertThrows(ForbiddenException.class, () -> service.finish(action(train), token()));
    assertEquals(0, new BigDecimal("77").compareTo(trainDao.findById(train.getId()).getScore()));
  }

  @Test
  void finishWithEmptyRebuildPreservesRawAndPreviouslySavedDerivedValues() {
    String token = token();
    PostTelegraphKeyPatTrainEntity train = seed(token, 1);
    service.finishPage(page(train, 1, 0, 1000, "1", "2", "3", "4"), token);
    PostTelegraphKeyPatTrainPageValueEntity old = new PostTelegraphKeyPatTrainPageValueEntity(
        train.getId(), 1, "[\"1\"]", "[\"1\"]", "[10]", 0);
    valueDao.save(old);
    String raw = rawDao.findPage(train.getId(), 1).getValue();
    pageDao.delete("trainId", train.getId());
    assertThrows(IllegalStateException.class, () -> service.finish(action(train), token));
    assertEquals("[\"1\"]", valueDao.findByTrainIdOrderByPageNumberAscSortAsc(train.getId()).getFirst().getValue());
    assertEquals(raw, rawDao.findPage(train.getId(), 1).getValue());
    assertEquals(UNDERWAY.getStatus(), trainDao.findById(train.getId()).getStatus());
  }

  @Test
  void exactRetryPreservesReceiptAndRawDataEvenAfterSettlement() {
    String token = token();
    PostTelegraphKeyPatTrainEntity train = seed(token, 1);
    PostTelegraphKeyPatTrainPageDto upload = page(train, 1, 0, 1000, "1", "2", "3", "4");
    service.finishPage(upload, token);
    PostTelegraphKeyPatTrainRawPageEntity saved = rawDao.findPage(train.getId(), 1);
    String raw = saved.getValue();
    assertTrue(service.finishPage(upload, token).isSubmitted());
    assertEquals(saved.getReceivedAt(), rawDao.findPage(train.getId(), 1).getReceivedAt());
    BigDecimal score = service.finish(action(train), token).getScore();
    assertTrue(service.finishPage(upload, token).isSubmitted());
    assertEquals(raw, rawDao.findPage(train.getId(), 1).getValue());
    assertEquals(1, rawDao.findPages(train.getId()).size());
    assertEquals(score, service.finish(action(train), token).getScore());
    assertThrows(IllegalStateException.class,
        () -> service.finishPage(page(train, 1, 0, 1000, "9"), token));
    assertEquals(raw, rawDao.findPage(train.getId(), 1).getValue());
  }

  @Test
  void activeReplacementAppendsClosedIntervalsWithoutReopeningAcknowledgedTime() {
    String token = token();
    PostTelegraphKeyPatTrainEntity train = seed(token, 1);
    PostTelegraphKeyPatTrainPageDto original = page(train, 1, 0, 1000, "1");
    service.finishPage(original, token);
    String rowId = rawDao.findPage(train.getId(), 1).getId();
    assertThrows(IllegalStateException.class,
        () -> service.finishPage(page(train, 1, 0, 1000, "2"), token));
    assertThrows(IllegalStateException.class,
        () -> service.finishPage(page(train, 1, 0, 1500, "1", "2"), token));

    PostTelegraphKeyPatTrainPageDto updated = page(train, 1, 0, 1000, "1", "2");
    updated.setCaptureIntervals(List.of(new CaptureInterval(0, 1000), new CaptureInterval(2000, 3000)));
    assertEquals(updated.getCaptureIntervals(), service.finishPage(updated, token).getSavedCaptureIntervals());
    assertEquals(rowId, rawDao.findPage(train.getId(), 1).getId());
    assertEquals(updated.getValue().getFirst().getValue(),
        service.getPage(train.getId(), 1, token).getMessageVO().getFirst().getValue());
    assertThrows(IllegalStateException.class, () -> service.finishPage(original, token));
    PostTelegraphKeyPatTrainPageDto droppedPrefix = page(train, 1, 2000, 3000, "2", "3");
    droppedPrefix.setCaptureIntervals(List.of(new CaptureInterval(2000, 3000), new CaptureInterval(4000, 5000)));
    assertThrows(IllegalStateException.class, () -> service.finishPage(droppedPrefix, token));

    String raw = rawDao.findPage(train.getId(), 1).getValue();
    LocalDateTime receipt = rawDao.findPage(train.getId(), 1).getReceivedAt();
    assertEquals("2", service.finish(action(train), token).getDuration());
    assertTrue(service.finishPage(updated, token).isSubmitted());
    assertEquals(receipt, rawDao.findPage(train.getId(), 1).getReceivedAt());
    PostTelegraphKeyPatTrainPageDto afterFinish = page(train, 1, 0, 1000, "1", "2", "3");
    afterFinish.setCaptureIntervals(List.of(new CaptureInterval(0, 1000), new CaptureInterval(2000, 3000),
        new CaptureInterval(4000, 5000)));
    assertThrows(IllegalStateException.class, () -> service.finishPage(afterFinish, token));
    assertEquals(raw, rawDao.findPage(train.getId(), 1).getValue());
  }

  @Test
  void reorderedNonOverlappingPagesSettleButFutureOverlapAndOldAttemptsCannotWrite() {
    String token = token();
    PostTelegraphKeyPatTrainEntity train = seed(token, 300);
    service.finishPage(page(train, 3, 2000, 3000, "1"), token);
    assertThrows(IllegalArgumentException.class,
        () -> service.finishPage(page(train, 1, 0, Long.MAX_VALUE, "1"), token));
    PostTelegraphKeyPatTrainPageDto reversed = page(train, 1, 0, 1000, "1");
    reversed.setCaptureIntervals(List.of(new CaptureInterval(500, 1000), new CaptureInterval(0, 400)));
    assertThrows(IllegalArgumentException.class, () -> service.finishPage(reversed, token));
    service.finishPage(page(train, 1, 0, 1000, "1"), token);
    PostTelegraphKeyPatTrainPageDto overlap = page(train, 1, 0, 1000, "1", "2");
    overlap.setCaptureIntervals(List.of(new CaptureInterval(0, 1000), new CaptureInterval(2500, 3500)));
    assertThrows(IllegalArgumentException.class, () -> service.finishPage(overlap, token));
    assertEquals("2", service.finish(action(train), token).getDuration());
    assertEquals(List.of(1, 3), service.details(train.getId(), token).getPageAnalyzeVOS().stream()
        .map(page -> page.getPageNumber()).toList());
    PostTelegraphKeyPatTrainPageDto delayed = page(train, 2, 1000, 2000, "1");
    PostTelegraphKeyPatTrainVO reset = service.reset(action(train), token);
    assertEquals(1, reset.getAttempt());
    assertThrows(IllegalArgumentException.class, () -> service.finishPage(delayed, token));
    assertEquals(List.of(), rawDao.findPages(train.getId()));
    assertEquals(List.of(), valueDao.findByTrainIdOrderByPageNumberAscSortAsc(train.getId()));
    assertEquals(NOT_STARTED.getStatus(), trainDao.findById(train.getId()).getStatus());
    assertEquals("[]", service.getPage(train.getId(), 1, token).getMessageVO().getFirst().getValue());
  }

  @Test
  void sparseDeliveryChargesActualMissingFullPageRatherThanSubmittedPartialLastPage() {
    String token = token();
    PostTelegraphKeyPatTrainEntity train = seed(token, 250);
    train.setRuleContent(RULE.replace("\"muchLessGroups\":0", "\"muchLessGroups\":1"));
    trainDao.save(train);
    PostTelegraphKeyPatTrainPageDto lastPage = page(train, 3, 2000, 3000);
    lastPage.setValue(List.of());
    PostTelegraphKeyPatTrainPageDto firstPage = page(train, 1, 0, 1000);
    firstPage.setValue(List.of());
    service.finishPage(lastPage, token);
    service.finishPage(firstPage, token);
    PostTelegraphKeyPatTrainVO result = service.finish(action(train), token);
    assertEquals(100, com.google.gson.JsonParser.parseString(result.getDeductInfo())
        .getAsJsonObject().get("lackGroupNumber").getAsInt());
    assertEquals(0, new BigDecimal("-12").compareTo(result.getScore()));
    assertEquals("2", result.getDuration());
  }

  @Test
  void finishUsesFrozenRuleAndOriginalBodyCaptureDurationWithOneRateRounding() {
    String token = token();
    GradingRuleEntity rule = new GradingRuleEntity();
    rule.setScore(137);
    rule.setContent(RULE);
    rule = gradingRuleDao.save(rule);
    PostTelegraphKeyPatTrainVO created = service.add(new PostTelegraphKeyPatTrainDto()
        .setTitle("frozen").setIsCable(0).setTotalNumber(1).setMessageType(0).setRuleId(rule.getId()), token);
    PostTelegraphKeyPatTrainEntity train = trainDao.findById(created.getId());
    service.begin(action(train), token);
    train = trainDao.findById(train.getId());
    train.setBeginTime(LocalDateTime.now().minusMinutes(2));
    trainDao.save(train);
    rule.setContent(RULE.replace("\"base\":4", "\"base\":100"));
    rule.setScore(999);
    gradingRuleDao.save(rule);
    // Five actual body characters, including unknown '#'; whitespace and '?' are not body.
    PostTelegraphKeyPatTrainPageDto upload = page(train, 1, 0, 52000, "1", "2", "3", "4", "#", " ", "?");
    service.finishPage(upload, token);
    String raw = rawDao.findPage(train.getId(), 1).getValue();
    PostTelegraphKeyPatTrainVO result = service.finish(action(train), token);
    assertEquals("1", result.getSpeed()); // rounding chars/min first would yield 2 groups/min
    assertEquals("52", result.getDuration());
    assertEquals(0, new BigDecimal("128").compareTo(result.getScore()));
    assertEquals(RULE, result.getRuleContent());
    assertEquals(raw, rawDao.findPage(train.getId(), 1).getValue());
    PostTelegraphKeyPatTrainVO details = service.details(train.getId(), token);
    assertEquals(5, details.getPageAnalyzeVOS().getFirst().getPatNumber());
    assertEquals(52000, details.getPageAnalyzeVOS().getFirst().getTotalTime());
    assertEquals(List.of(new CaptureInterval(0, 52000)), details.getSavedPages().getFirst().getSavedCaptureIntervals());
  }

  @Test
  void ownerBoundaryCoversReadsAndAllMutations() {
    String token = token();
    String stranger = token();
    PostTelegraphKeyPatTrainEntity train = seed(token, 1);
    // 非创建者的读与全部写路径都必须是 ForbiddenException -> 207（与目标不存在的 202 分离）。
    assertThrows(ForbiddenException.class, () -> service.details(train.getId(), stranger));
    assertThrows(ForbiddenException.class, () -> service.getPage(train.getId(), 1, stranger));
    assertThrows(ForbiddenException.class, () -> service.begin(action(train), stranger));
    assertThrows(ForbiddenException.class,
        () -> service.finishPage(page(train, 1, 0, 1000, "1"), stranger));
    assertThrows(ForbiddenException.class, () -> service.finish(action(train), stranger));
    assertThrows(ForbiddenException.class, () -> service.reset(action(train), stranger));
    assertThrows(ForbiddenException.class, () -> service.delete(train.getId(), stranger));
    assertEquals(UNDERWAY.getStatus(), trainDao.findById(train.getId()).getStatus());
    assertEquals(List.of(), rawDao.findPages(train.getId()));
  }

  @Test
  void legacyUnfinishedAndAmbiguousRuleCannotEnterNewProtocol() {
    String token = token();
    PostTelegraphKeyPatTrainEntity train = seed(token, 1);
    train.setProtocolVersion(0);
    trainDao.save(train);
    assertThrows(IllegalStateException.class, () -> service.begin(action(train), token));
    assertThrows(IllegalStateException.class, () -> service.finish(action(train), token));
    assertThrows(IllegalStateException.class, () -> service.details(train.getId(), token));
    GradingRuleEntity rule = new GradingRuleEntity();
    rule.setScore(100);
    rule.setContent(RULE.replace("\"rateUnit\":\"FOUR_CHARACTER_GROUPS_PER_MINUTE\",", ""));
    String ruleId = gradingRuleDao.save(rule).getId();
    int before = service.listPage(token).size();
    assertThrows(IllegalArgumentException.class, () -> service.add(new PostTelegraphKeyPatTrainDto()
        .setTotalNumber(1).setMessageType(0).setRuleId(ruleId), token));
    assertEquals(before, service.listPage(token).size());
  }

  @Test
  void beginResumeKeepsOriginalAnchorAndAttempt() {
    String token = token();
    PostTelegraphKeyPatTrainEntity train = seed(token, 1);
    LocalDateTime begin = trainDao.findById(train.getId()).getBeginTime();
    PostTelegraphKeyPatTrainVO resumed = service.begin(action(train), token);
    assertEquals(begin, trainDao.findById(train.getId()).getBeginTime());
    assertEquals(0, resumed.getAttempt());
    assertTrue(resumed.getServerElapsedMs() >= 119000);
  }

  private String token() {
    String token = "personal-electronic-" + UUID.randomUUID();
    Fixtures.user(userDao, token);
    return token;
  }

  private PostTelegraphKeyPatTrainEntity seed(String token, int groups) {
    PostTelegraphKeyPatTrainEntity train = new PostTelegraphKeyPatTrainEntity()
        .setCreateUserId(userDao.find("token", token).firstResult().getId())
        .setProtocolVersion(1).setAttempt(0).setIsCable(0).setTotalNumber(groups).setMessageType(0)
        .setStatus(UNDERWAY.getStatus()).setBeginTime(LocalDateTime.now().minusMinutes(2))
        .setScore(new BigDecimal("100")).setFullScore(new BigDecimal("100")).setRuleContent(RULE);
    return trainDao.save(train);
  }

  private static PostTelegraphKeyPatTrainActionDto action(PostTelegraphKeyPatTrainEntity train) {
    return new PostTelegraphKeyPatTrainActionDto().setId(train.getId()).setProtocolVersion(1).setAttempt(train.getAttempt());
  }

  private static PostTelegraphKeyPatTrainPageDto page(PostTelegraphKeyPatTrainEntity train,
      int pageNumber, long start, long end, String... characters) {
    PostTelegraphKeyPatTrainPageMessageVO group = new PostTelegraphKeyPatTrainPageMessageVO();
    group.setSort(0);
    group.setKey("[\"1\",\"2\",\"3\",\"4\"]");
    group.setValue(JSONUtils.toJson(List.of(characters)));
    group.setTime(JSONUtils.toJson(java.util.Collections.nCopies(characters.length, 10)));
    return new PostTelegraphKeyPatTrainPageDto().setId(train.getId()).setPageNumber(pageNumber)
        .setProtocolVersion(1).setAttempt(train.getAttempt()).setValue(List.of(group))
        .setCaptureIntervals(List.of(new CaptureInterval(start, end)));
  }
}
