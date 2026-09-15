package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.PostTelegramTrainDao;
import com.nip.dao.PostTelegramTrainFloorContentDao;
import com.nip.dao.UserDao;
import com.nip.dto.CaptureInterval;
import com.nip.dto.PostTelegramTrainContentValueDto;
import com.nip.dto.PostTelegramTrainFinishDto;
import com.nip.dto.PostTelegramTrainFinishInfoDto;
import com.nip.dto.vo.PostTelegramTrainResolverVO;
import com.nip.dto.vo.PostTelegramTrainVO;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import com.nip.dto.vo.param.PostTelegramTrainQueryParam;
import com.nip.entity.PostTelegramTrainEntity;
import com.nip.entity.PostTelegramTrainFloorContentEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class PostTelegramReplayAlignmentTest {
  private static final String RULE = """
      {"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":70,"r":0,"l":0},"skew":51,
       "code":{"dot":{"base":1000,"l":1,"r":1,"max":1},"dash":{"base":1000,"l":1,"r":1,"max":5}},
       "gap":{"little":{"base":1000,"l":1,"r":1,"max":1},"middle":{"base":1000,"l":1,"r":1,"max":1},"large":{"base":1000,"l":1,"r":1,"max":1}},
       "other":{"errorCode":{"l":1,"max":1},"quantoCode":{"l":1,"max":1},"quantoGroup":{"l":1,"max":1},"alterError":{"l":1,"max":1},"quantoRow":{"l":1,"max":1},"bunchGroup":{"l":1,"max":1}}}
      """;

  @Inject PostTelegramTrainService service;
  @Inject PostTelegramTrainDao trainDao;
  @Inject PostTelegramTrainFloorContentDao floorContentDao;
  @Inject UserDao userDao;

  @Test
  void missingFirstPageDoesNotReceiveSecondPagesErrorAnalysis() {
    PostTelegramTrainVO replay = finishAndReplay(false);

    assertNull(replay.getResolver().get(0), "An unsubmitted first page must remain unanalysed");
    assertNull(replay.getFinishInfo().get(0));
    assertEquals("{}", replay.getStandards().get(0));
    assertEquals(List.of(), keys(body(replay, 0).getFirst().getPatKeys()));
    assertEquals(List.of("1", "1", "1", "1"), keys(body(replay, 0).getFirst().getMoresKey()));
    assertSecondPageError(replay);
  }

  @Test
  void sequentialSubmissionsRetainTheirOwnCorrectAndIncorrectAnalysis() {
    PostTelegramTrainVO replay = finishAndReplay(true);

    assertEquals("1111", analysis(replay, 0).getResolverMessage().getFirst());
    assertEquals(List.of("1", "1", "1", "1"), keys(body(replay, 0).getFirst().getMoresKey()));
    assertEquals(100, replay.getFinishInfo().get(0).getDot());
    assertEquals(100, standards(replay, 0).getFirst().getDot());
    assertSecondPageError(replay);
  }

  private void assertSecondPageError(PostTelegramTrainVO replay) {
    assertEquals(List.of("2", "2", "2", "2"), keys(body(replay, 1).getFirst().getMoresKey()));
    assertEquals(List.of("9", "9", "9", "9"), keys(body(replay, 1).getFirst().getPatKeys()));
    assertEquals(List.of("9999"), analysis(replay, 1).getResolverMessage(),
        "The second page's wrong code must be shown against its own source, never page one's source");
    assertEquals(200, replay.getFinishInfo().get(1).getDot());
    assertEquals(200, standards(replay, 1).getFirst().getDot());
  }

  private PostTelegramTrainVO finishAndReplay(boolean submitFirstPage) {
    String token = "post-replay-" + UUID.randomUUID();
    String userId = Fixtures.user(userDao, token).getId();
    PostTelegramTrainEntity train = trainDao.save(new PostTelegramTrainEntity()
        .setCreateUser(userId).setProtocolVersion(1).setAttempt(0).setFullScore(100).setScore("100")
        .setMessageNumber(101).setStatus(1).setStartTime(LocalDateTime.now().minusMinutes(10))
        .setRuleContent(RULE).setType(0).setIsCable(0));
    // Persist in reverse order: replay ordering is by page, not insertion order.
    createSource(train, 2, 1, "2");
    createSource(train, 1, 100, "1");
    if (submitFirstPage) submit(train, 1, "1", token);
    submit(train, 2, "9", token);
    PostTelegramTrainFinishDto finish = new PostTelegramTrainFinishDto();
    finish.setId(train.getId());
    finish.setAttempt(0);
    service.finish(finish, token);
    PostTelegramTrainQueryParam query = new PostTelegramTrainQueryParam();
    query.setId(train.getId());
    return service.detail(query, token);
  }

  private void createSource(PostTelegramTrainEntity train, int page, int groups, String digit) {
    List<PostTelegramTrainFloorContentEntity> sources = new ArrayList<>();
    for (int index = 0; index < groups; index++) {
      PostTelegramTrainFloorContentEntity source = new PostTelegramTrainFloorContentEntity();
      source.setTrainId(train.getId());
      source.setFloorNumber(page);
      source.setSort(index);
      source.setMoresKey(JSONUtils.toJson(List.of(digit, digit, digit, digit)));
      source.setPatKeys("[]");
      sources.add(source);
    }
    floorContentDao.saveAndFlush(sources);
  }

  private void submit(PostTelegramTrainEntity train, int page, String digit, String token) {
    List<PostTelegramTrainContentAddParam> body = new ArrayList<>();
    for (PostTelegramTrainFloorContentEntity source : floorContentDao.findByFloorNumberAndTrainIdOrderBySort(page, train.getId())) {
      PostTelegramTrainContentAddParam group = new PostTelegramTrainContentAddParam();
      group.setId(source.getId());
      group.setMoresKey(source.getMoresKey());
      group.setPatKeys(JSONUtils.toJson(List.of(digit, digit, digit, digit)));
      group.setMoresValue("[[0],[0],[0],[0]]");
      group.setMoresTime("[[100],[100],[100],[100]]");
      group.setPatLogs(JSONUtils.toJson(List.of(digit, digit, digit, digit).stream().map(key -> List.of(
          Map.of("key", 2, "value", 100), Map.of("key", 0, "value", 100))).toList()));
      body.add(group);
    }
    PostTelegramTrainFinishInfoDto calibration = new PostTelegramTrainFinishInfoDto();
    calibration.setDot(page * 100);
    calibration.setLine(page * 300);
    calibration.setCodeGap(page * 100);
    calibration.setWordGap(page * 300);
    calibration.setGroupGap(page * 700);
    calibration.setOffSize(51);
    PostTelegramTrainContentValueDto upload = new PostTelegramTrainContentValueDto();
    upload.setTrainId(train.getId());
    upload.setFloorNumber(page);
    upload.setAttempt(0);
    upload.setMessageBody(body);
    upload.setStandard(List.of(calibration));
    upload.setFinishInfo(JSONUtils.toJson(calibration));
    upload.setCaptureIntervals(List.of(new CaptureInterval((page - 1) * 100000L, page * 100000L)));
    service.saveContentValue(upload, token);
  }

  private List<PostTelegramTrainContentAddParam> body(PostTelegramTrainVO replay, int index) {
    return JSONUtils.fromJson(replay.getMessageBody().get(index), new TypeToken<>() {});
  }

  private List<String> keys(String json) {
    return JSONUtils.fromJson(json, new TypeToken<>() {});
  }

  private PostTelegramTrainResolverVO analysis(PostTelegramTrainVO replay, int index) {
    return JSONUtils.fromJson(replay.getResolver().get(index), PostTelegramTrainResolverVO.class);
  }

  private List<PostTelegramTrainFinishInfoDto> standards(PostTelegramTrainVO replay, int index) {
    return JSONUtils.fromJson(replay.getStandards().get(index), new TypeToken<>() {});
  }
}
