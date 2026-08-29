package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.PostTelegramTrainDao;
import com.nip.dao.PostTelegramTrainFloorContentDao;
import com.nip.dao.PostTelegramTrainContentValueDao;
import com.nip.dto.PostTelegramTrainContentValueDto;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import com.nip.dto.vo.PostTelegramTrainAddContentValueVO;
import com.nip.entity.PostTelegramTrainEntity;
import com.nip.entity.PostTelegramTrainContentFloorValueEntity;


import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 3.5：saveContentValue 的 speedLog 原来无脑 append，同一页重传会追加重复速率，
 * 与 :534 的 deleteByTrainIdAndFloorNumber（按页覆盖）语义不一致。现在按 floorNumber upsert。
 */
@QuarkusTest

class PostTelegramTrainServiceTest {
  @Inject PostTelegramTrainService service;
  @Inject PostTelegramTrainDao trainDao;
  @Inject PostTelegramTrainContentValueDao contentValueDao;
  @Inject PostTelegramTrainFloorContentDao floorContentDao;

  private static PostTelegramTrainContentValueDto dto(String trainId, int floorNumber, String speed) {
    PostTelegramTrainContentValueDto d = new PostTelegramTrainContentValueDto();
    d.setTrainId(trainId);
    d.setFloorNumber(floorNumber);
    d.setSpeed(speed);
    d.setErrorNumber(0);
    d.setAccuracy("0.00");
    d.setMessageBody(new ArrayList<>());
    return d;
  }

  @Test
  void speedLogUpsertsByFloorNumberInsteadOfAppending() {
    PostTelegramTrainEntity e = new PostTelegramTrainEntity();
    e.setMessageNumber(200);
    e = trainDao.save(e);
    String id = e.getId();

    service.saveContentValue(dto(id, 1, "80"));
    service.saveContentValue(dto(id, 1, "90")); // 同一页重传：覆盖而不是追加
    service.saveContentValue(dto(id, 2, "100"));

    List<String> speedLog = JSONUtils.fromJson(trainDao.findById(id).getSpeedLog(), new TypeToken<>() {
    });
    assertEquals(List.of("90", "100"), speedLog, "同页重传必须按 floorNumber 覆盖");
  }
  @Test
  void malformedJsonShapedPatKeysDoesNotOverwriteExistingPage() {
    PostTelegramTrainEntity train = new PostTelegramTrainEntity();
    train.setMessageNumber(200);
    train = trainDao.save(train);

    PostTelegramTrainContentFloorValueEntity existing = new PostTelegramTrainContentFloorValueEntity();
    existing.setTrainId(train.getId());
    existing.setFloorNumber(1);
    existing.setMessageBody("[{\"patKeys\":\"existing-page\"}]");
    existing.setStandard("[]");
    existing.setFinishInfo("existing-finish");
    existing = contentValueDao.saveAndFlush(existing);
    String existingId = existing.getId();

    PostTelegramTrainContentValueDto replacement = dto(train.getId(), 1, "90");
    PostTelegramTrainContentAddParam item = new PostTelegramTrainContentAddParam();
    item.setPatKeys("[\"a\",broken");
    item.setPatLogs("[[]]");
    item.setMoresTime("[[1,2]]");
    item.setMoresValue("[[1,2]]");
    replacement.setMessageBody(List.of(item));

    assertThrows(IllegalStateException.class, () -> service.saveContentValue(replacement));

    PostTelegramTrainContentFloorValueEntity after = contentValueDao
        .findByFloorNumberAndTrainId(1, train.getId());
    assertNotNull(after);
    assertEquals(existingId, after.getId(), "异常提交不得删除旧页后新建");
    assertEquals("[{\"patKeys\":\"existing-page\"}]", after.getMessageBody());
    assertEquals("[]", after.getStandard());
    assertEquals("existing-finish", after.getFinishInfo());
  }

  @Test
  void addContentValueRollsBackEarlierRowsWhenALaterRowFails() {
    PostTelegramTrainEntity train = trainDao.save(new PostTelegramTrainEntity());

    PostTelegramTrainContentAddParam firstRow = new PostTelegramTrainContentAddParam();
    firstRow.setMoresKey("[\"A\"]");
    firstRow.setMoresValue("[]");
    firstRow.setMoresTime("[]");
    firstRow.setPatKeys("[]");

    List<List<PostTelegramTrainContentAddParam>> rows = new ArrayList<>();
    rows.add(List.of(firstRow));
    rows.add(null);

    PostTelegramTrainAddContentValueVO request = new PostTelegramTrainAddContentValueVO();
    request.setTrainId(train.getId());
    request.setMessageBody(rows);

    assertThrows(NullPointerException.class, () -> service.addContentValue(request));
    assertTrue(floorContentDao.findByTrainIdOrderByFloorNumberSort(train.getId()).isEmpty(),
        "后续行失败时，先前已 flush 的追加内容必须回滚");
  }
}
