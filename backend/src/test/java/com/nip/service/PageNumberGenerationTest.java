package com.nip.service;

import com.nip.dao.PostTelegramTrainDao;
import com.nip.dao.PostTelegramTrainFloorContentDao;
import com.nip.dao.PostTelexPatTrainDao;
import com.nip.dao.UserDao;
import com.nip.testsupport.Fixtures;
import com.nip.dto.vo.PostTelegramTrainAddContentValueVO;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import com.nip.dto.vo.param.PostTelegramTrainFloorContentQueryParam;
import com.nip.entity.PostTelegramTrainEntity;
import com.nip.entity.PostTelegramTrainFloorContentEntity;
import com.nip.entity.PostTelexPatTrainEntity;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 4.4：三处页号算术。
 * 1) addContentValue 原来 `floorNumber += i`，i=0 复用已有楼层号、i=2 跳过 base+2 留空洞；
 * 2) findMessageBody 跳页生成时原来按「最后一页 + 1」落库，请求第 4 页会写成第 2 页（遗留 P2-07）；
 * 3) PostTelexPatTrainService.getPage 原来判据是 pageNumber < 0，第 0 页可落库（遗留 P2-06）。
 */
@QuarkusTest
class PageNumberGenerationTest {
  @Inject
  PostTelegramTrainService telegramTrainService;
  @Inject
  PostTelexPatTrainService telexPatTrainService;
  @Inject
  PostTelegramTrainDao telegramTrainDao;
  @Inject
  PostTelegramTrainFloorContentDao floorContentDao;
  @Inject
  PostTelexPatTrainDao telexPatTrainDao;
  @Inject
  UserDao userDao;

  @Test
  void addContentValueAppendsConsecutiveFloorsWithoutOverlapOrGap() {
    String token = "hand-page-" + java.util.UUID.randomUUID();
    String trainId = seedTelegramTrain(token);
    seedFloor(trainId, 1, 0);

    PostTelegramTrainAddContentValueVO vo = new PostTelegramTrainAddContentValueVO();
    vo.setTrainId(trainId);
    vo.setMessageBody(List.of(List.of(contentParam("[\"B\"]")), List.of(contentParam("[\"C\"]"))));

    List<Integer> floors = telegramTrainService.addContentValue(vo, token);

    assertEquals(List.of(1, 2, 3), floors.stream().sorted().toList(),
        "基准页之后必须连续追加 base+1、base+2，不留空洞");
    assertEquals(1, floorContentDao.findByFloorNumberAndTrainIdOrderBySort(1, trainId).size(),
        "基准页不得被追加内容重复写入");
    assertEquals(1, floorContentDao.findByFloorNumberAndTrainIdOrderBySort(2, trainId).size());
    assertEquals(1, floorContentDao.findByFloorNumberAndTrainIdOrderBySort(3, trainId).size());
  }

  @Test
  void jumpPageGenerationStoresRequestedPageNumber() {
    String token = "hand-page-" + java.util.UUID.randomUUID();
    String trainId = seedTelegramTrain(token);
    seedFloor(trainId, 1, 0);

    PostTelegramTrainFloorContentQueryParam param = new PostTelegramTrainFloorContentQueryParam();
    param.setId(trainId);
    param.setFloorNumber(4);

    telegramTrainService.findMessageBody(param, token);

    assertEquals(100, floorContentDao.findByFloorNumberAndTrainIdOrderBySort(4, trainId).size(),
        "跳页生成的报底必须落在请求的页号上");
    assertTrue(floorContentDao.findByFloorNumberAndTrainIdOrderBySort(2, trainId).isEmpty(),
        "不得按「最后一页 + 1」落到第 2 页");
  }

  @Test
  void getPageRejectsPageNumberZero() {
    PostTelexPatTrainEntity entity = new PostTelexPatTrainEntity();
    String token = "page-zero-" + java.util.UUID.randomUUID();
    entity.setCreateUser(Fixtures.user(userDao, token).getId());
    entity.setProtocolVersion(1);
    entity.setIsCable(0);
    entity.setGroupNumber(500);
    entity.setType(0);
    entity.setTrainType(0);
    entity.setStatus(0);
    String trainId = telexPatTrainDao.saveAndFlush(entity).getId();

    assertThrows(IllegalArgumentException.class,
        () -> telexPatTrainService.getPage(trainId, 0, token));
  }

  private String seedTelegramTrain(String token) {
    PostTelegramTrainEntity entity = new PostTelegramTrainEntity();
    entity.setCreateUser(Fixtures.user(userDao, token).getId());
    entity.setProtocolVersion(1).setAttempt(0);
    entity.setMessageNumber(500);
    entity.setType(0);
    entity.setIsCable(1);
    entity.setIsAverage(1);
    entity.setIsRandom(1);
    entity.setStatus(0);
    return telegramTrainDao.saveAndFlush(entity).getId();
  }

  private void seedFloor(String trainId, int floorNumber, int sort) {
    PostTelegramTrainFloorContentEntity floor = new PostTelegramTrainFloorContentEntity();
    floor.setTrainId(trainId);
    floor.setFloorNumber(floorNumber);
    floor.setSort(sort);
    floor.setMoresKey("[\"A\"]");
    floor.setMoresValue("[]");
    floor.setMoresTime("[]");
    floor.setPatKeys("[]");
    floorContentDao.saveAndFlush(floor);
  }

  private static PostTelegramTrainContentAddParam contentParam(String moresKey) {
    PostTelegramTrainContentAddParam param = new PostTelegramTrainContentAddParam();
    param.setMoresKey(moresKey);
    param.setMoresValue("[]");
    param.setMoresTime("[]");
    param.setPatKeys("[]");
    return param;
  }
}
