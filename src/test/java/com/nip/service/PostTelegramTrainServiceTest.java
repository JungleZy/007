package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.PostTelegramTrainDao;
import com.nip.dto.PostTelegramTrainContentValueDto;
import com.nip.entity.PostTelegramTrainEntity;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task 3.5：saveContentValue 的 speedLog 原来无脑 append，同一页重传会追加重复速率，
 * 与 :534 的 deleteByTrainIdAndFloorNumber（按页覆盖）语义不一致。现在按 floorNumber upsert。
 */
@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class PostTelegramTrainServiceTest {
  @Inject PostTelegramTrainService service;
  @Inject PostTelegramTrainDao trainDao;

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
}
