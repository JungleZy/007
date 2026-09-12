package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.exception.ForbiddenException;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.*;
import com.nip.dto.*;
import com.nip.dto.vo.*;
import com.nip.dto.vo.param.*;
import com.nip.entity.*;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PostTelegramTrainServiceTest {
  @Inject PostTelegramTrainService service;
  @Inject PostTelegramTrainDao trainDao;
  @Inject PostTelegramTrainContentValueDao contentValueDao;
  @Inject PostTelegramTrainFloorContentDao floorContentDao;
  @Inject GradingRuleDao ruleDao;
  @Inject UserDao userDao;

  static final String RULE = """
      {"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":70,"r":2,"l":1},"skew":51,
       "code":{"dot":{"base":1000,"l":1,"r":1,"max":1},"dash":{"base":1000,"l":1,"r":1,"max":5}},
       "gap":{"little":{"base":1000,"l":1,"r":1,"max":1},"middle":{"base":1000,"l":1,"r":1,"max":1},"large":{"base":1000,"l":1,"r":1,"max":1}},
       "other":{"errorCode":{"l":1,"max":1},"quantoCode":{"l":1,"max":1},"quantoGroup":{"l":1,"max":1},"alterError":{"l":1,"max":1},"quantoRow":{"l":1,"max":1},"bunchGroup":{"l":1,"max":1}}}
      """;

  private String token() {
    String token = "personal-hand-" + UUID.randomUUID();
    Fixtures.user(userDao, token, token);
    return token;
  }

  private PostTelegramTrainEntity train(String token, int groups) {
    PostTelegramTrainEntity entity = new PostTelegramTrainEntity();
    entity.setCreateUser(userDao.find("token", token).firstResult().getId());
    entity.setProtocolVersion(1).setAttempt(0).setFullScore(137).setScore("137")
        .setMessageNumber(groups).setStatus(1).setStartTime(LocalDateTime.now().minusMinutes(5))
        .setRuleContent(RULE).setType(0).setIsCable(0);
    return trainDao.save(entity);
  }

  static PostTelegramTrainContentAddParam group(List<String> keys) {
    PostTelegramTrainContentAddParam group = new PostTelegramTrainContentAddParam();
    group.setPatKeys(JSONUtils.toJson(keys));
    group.setMoresTime(JSONUtils.toJson(keys.stream().map(k -> List.of(100)).toList()));
    group.setMoresValue(JSONUtils.toJson(keys.stream().map(k -> List.of(0)).toList()));
    group.setPatLogs(JSONUtils.toJson(keys.stream().map(k -> List.of(Map.of("key", 2, "value", 100), Map.of("key", 0, "value", 100))).toList()));
    return group;
  }

  private PostTelegramTrainContentValueDto page(PostTelegramTrainEntity train, int page, long start, long end) {
    PostTelegramTrainContentValueDto dto = new PostTelegramTrainContentValueDto();
    dto.setTrainId(train.getId());
    dto.setFloorNumber(page);
    dto.setAttempt(train.getAttempt());
    dto.setMessageBody(List.of(group(List.of("1", "2", "3", "4"))));
    PostTelegramTrainFinishInfoDto standard = new PostTelegramTrainFinishInfoDto();
    standard.setDot(100); standard.setLine(300); standard.setCodeGap(100); standard.setWordGap(300); standard.setGroupGap(700);
    dto.setStandard(List.of(standard));
    dto.setFinishInfo("{}");
    dto.setCaptureIntervals(List.of(new CaptureInterval(start, end)));
    return dto;
  }

  private PostTelegramTrainFinishDto finish(PostTelegramTrainEntity train) {
    PostTelegramTrainFinishDto dto = new PostTelegramTrainFinishDto();
    dto.setId(train.getId()); dto.setAttempt(train.getAttempt());
    return dto;
  }

  @Test
  void duplicateIsIdempotentAndOverlappingPagesAreRejectedRegardlessOfArrivalOrder() {
    String token = token();
    PostTelegramTrainEntity train = train(token, 300);
    PostTelegramTrainContentValueDto first = page(train, 1, 1000, 5000);
    service.saveContentValue(first, token);
    PostTelegramTrainContentFloorValueEntity saved = contentValueDao.findByFloorNumberAndTrainId(1, train.getId());
    String body = saved.getMessageBody();
    service.saveContentValue(first, token);
    assertEquals(saved.getReceivedAt(), contentValueDao.findByFloorNumberAndTrainId(1, train.getId()).getReceivedAt());
    PostTelegramTrainContentValueDto conflict = page(train, 1, 1000, 5000);
    conflict.setMessageBody(List.of(group(List.of("9"))));
    assertThrows(IllegalStateException.class, () -> service.saveContentValue(conflict, token));
    service.saveContentValue(page(train, 3, 5000, 9000), token);
    assertThrows(IllegalArgumentException.class, () -> service.saveContentValue(page(train, 2, 4000, 7000), token));
    service.saveContentValue(page(train, 2, 0, 500), token);
    assertThrows(IllegalArgumentException.class, () -> service.saveContentValue(page(train, 2, 5000, 600000), token));
    assertEquals(body, contentValueDao.findByFloorNumberAndTrainId(1, train.getId()).getMessageBody());
    assertEquals(3, contentValueDao.findAllByTrainIdOrderByFloorNumber(train.getId()).size());
  }

  @Test
  void resetFencesLatePagesAndControlRequestsAndPreservesBeginAnchor() {
    String token = token();
    PostTelegramTrainEntity train = train(token, 100);
    LocalDateTime start = trainDao.findById(train.getId()).getStartTime();
    service.begin(train.getId(), 0, token);
    assertEquals(start, trainDao.findById(train.getId()).getStartTime());
    PostTelegramTrainContentValueDto old = page(train, 1, 0, 4000);
    PostTelegramTrainFinishDto staleFinish = finish(train);
    service.saveContentValue(old, token);
    service.stop(train.getId(), 0, token);
    PostTelegramTrainVO next = service.begin(train.getId(), 1, token);
    assertEquals(1, next.getAttempt());
    assertTrue(contentValueDao.findAllByTrainIdOrderByFloorNumber(train.getId()).isEmpty());
    assertThrows(IllegalArgumentException.class, () -> service.saveContentValue(old, token));
    assertThrows(IllegalArgumentException.class, () -> service.finish(staleFinish, token));
    assertThrows(IllegalArgumentException.class, () -> service.stop(train.getId(), 0, token));
    assertThrows(IllegalArgumentException.class, () -> service.begin(train.getId(), 0, token));
    assertEquals(1, trainDao.findById(train.getId()).getStatus());
    assertEquals("137", trainDao.findById(train.getId()).getScore());
  }

  @Test
  void replacementRetainsCaptureTimeAndFrozenRuleWithoutAccumulatingOldBody() {
    String token = token();
    GradingRuleEntity rule = new GradingRuleEntity();
    rule.setContent(RULE); rule.setScore(137); rule.setType(0);
    rule = ruleDao.save(rule);
    PostTelegramTrainAddParam add = new PostTelegramTrainAddParam();
    add.setName("authority"); add.setRuleId(rule.getId()); add.setIsCable(0); add.setMessageNumber(1); add.setType(0);
    PostTelegramTrainVO created = service.save(add, token);
    service.begin(created.getId(), 0, token);
    PostTelegramTrainEntity train = trainDao.findById(created.getId());
    train.setStartTime(LocalDateTime.now().minusMinutes(2)); trainDao.save(train);
    List<String> keys = JSONUtils.fromJson(floorContentDao.findByFloorNumberAndTrainIdOrderBySort(1, train.getId()).getFirst().getMoresKey(), new TypeToken<List<String>>() {});
    PostTelegramTrainContentValueDto original = page(train, 1, 0, 1000);
    original.setMessageBody(List.of(group(keys.subList(0, 2))));
    service.saveContentValue(original, token);
    String pageId = contentValueDao.findByFloorNumberAndTrainId(1, train.getId()).getId();
    PostTelegramTrainContentValueDto upload = page(train, 1, 0, 1000);
    upload.setCaptureIntervals(List.of(new CaptureInterval(0, 1000), new CaptureInterval(2000, 5000)));
    upload.setMessageBody(List.of(group(keys)));
    service.saveContentValue(upload, token);
    assertThrows(IllegalStateException.class, () -> service.saveContentValue(original, token));
    assertEquals(pageId, contentValueDao.findByFloorNumberAndTrainId(1, train.getId()).getId());
    assertEquals(1, contentValueDao.findAllByTrainIdOrderByFloorNumber(train.getId()).size());
    String raw = contentValueDao.findByFloorNumberAndTrainId(1, train.getId()).getMessageBody();
    rule.setScore(999); rule.setContent("{}"); ruleDao.save(rule);
    io.restassured.RestAssured.given().header("token", token).header("deviceId", token)
        .contentType("application/json")
        .body(Map.of("id", train.getId(), "attempt", 0, "speed", "99999", "validTime", 1, "accuracy", "100", "errorNumber", 0))
        .post("/api/postTelegramTrain/finish").then().statusCode(200).body("code", org.hamcrest.Matchers.is(200));
    PostTelegramTrainVO result = service.finish(finish(train), token);
    assertEquals("60", result.getSpeed());
    assertEquals(4000L, result.getActiveMillis());
    assertEquals(4L, result.getValidTime());
    assertEquals("127", result.getScore());
    assertEquals(RULE, result.getRuleContent());
    PostTelegramTrainQueryParam query = new PostTelegramTrainQueryParam(); query.setId(train.getId());
    PostTelegramTrainVO detail = service.detail(query, token);
    assertEquals(result.getSpeed(), detail.getSpeed());
    assertEquals(result.getScore(), detail.getScore());
    assertEquals(result.getDeductInfo(), detail.getDeductInfo());
    var analysis = detail.getPageAnalyzeVOS().getFirst();
    assertEquals(1, analysis.getPageNumber());
    assertEquals(60.0, analysis.getPatNumber() * 60000.0 / analysis.getTotalTime());
    assertEquals(raw, contentValueDao.findByFloorNumberAndTrainId(1, train.getId()).getMessageBody());
    service.saveContentValue(upload, token);
    assertEquals(result.getScore(), service.finish(finish(train), token).getScore());
  }

  @Test
  void ownerChecksProtectReadsAndMutationsAndLegacyResultIsReadOnly() {
    String owner = token(); String stranger = token();
    PostTelegramTrainEntity train = train(owner, 100);
    PostTelegramTrainQueryParam query = new PostTelegramTrainQueryParam(); query.setId(train.getId());
    PostTelegramTrainFloorContentQueryParam pageQuery = new PostTelegramTrainFloorContentQueryParam(); pageQuery.setId(train.getId()); pageQuery.setFloorNumber(1);
    // 非创建者一律是授权拒绝（ForbiddenException -> code 207），与「参数不合法/训练不存在」的 202 分离；
    // 旧实现把两者折叠成同一个 IllegalArgumentException，前端无法区分「无权限」与「可重试的参数错误」。
    assertThrows(ForbiddenException.class, () -> service.detail(query, stranger));
    assertThrows(ForbiddenException.class, () -> service.findMessageBody(pageQuery, stranger));
    assertThrows(ForbiddenException.class, () -> service.begin(train.getId(), 0, stranger));
    assertThrows(ForbiddenException.class, () -> service.stop(train.getId(), 0, stranger));
    assertThrows(ForbiddenException.class, () -> service.saveContentValue(page(train, 1, 0, 4000), stranger));
    assertThrows(ForbiddenException.class, () -> service.finish(finish(train), stranger));
    assertThrows(ForbiddenException.class, () -> service.delete(train.getId(), stranger));
    train.setProtocolVersion(0); trainDao.save(train);
    assertThrows(IllegalArgumentException.class, () -> service.begin(train.getId(), 0, owner));
    assertThrows(IllegalArgumentException.class, () -> service.finish(finish(train), owner));
    train.setStatus(2).setScore("88"); trainDao.save(train);
    assertEquals("88", service.finish(finish(train), owner).getScore());
    assertEquals("88", service.detail(query, owner).getScore());
  }

  @Test
  void ambiguousLegacyRuleCannotStartANewTraining() {
    String token = token();
    GradingRuleEntity rule = new GradingRuleEntity();
    rule.setScore(137); rule.setType(0);
    rule.setContent(RULE.replace("\"rateUnit\":\"CHARACTERS_PER_MINUTE\",", ""));
    rule = ruleDao.save(rule);
    PostTelegramTrainAddParam add = new PostTelegramTrainAddParam();
    add.setRuleId(rule.getId()); add.setMessageNumber(1); add.setType(0);
    assertThrows(IllegalArgumentException.class, () -> service.save(add, token));
    assertTrue(service.findAll(token).isEmpty());
  }

  @Test
  void appendRollsBackEarlierRowsWhenALaterRowFails() {
    String token = token();
    PostTelegramTrainEntity train = train(token, 100);
    train.setStatus(0); trainDao.save(train);
    PostTelegramTrainContentAddParam first = group(List.of("A"));
    first.setMoresKey("[\"A\"]");
    List<List<PostTelegramTrainContentAddParam>> pages = new ArrayList<>();
    pages.add(List.of(first)); pages.add(null);
    PostTelegramTrainAddContentValueVO request = new PostTelegramTrainAddContentValueVO();
    request.setTrainId(train.getId()); request.setMessageBody(pages);
    assertThrows(NullPointerException.class, () -> service.addContentValue(request, token));
    assertTrue(floorContentDao.findByTrainIdOrderByFloorNumberSort(train.getId()).isEmpty());
  }

  @Test
  void unknownCharactersCountButRecognizedControlsDoNot() {
    assertEquals(2, PostTelegramTrainService.countCharacters(List.of(group(List.of("#", "X", "?", " ", ".")))));
    PostTelegramTrainContentAddParam invalid = group(List.of("X"));
    invalid.setMoresTime("[[-1]]");
    assertThrows(IllegalArgumentException.class, () -> PostTelegramTrainService.countCharacters(List.of(invalid)));
  }
}
