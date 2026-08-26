package com.nip.service;

import com.nip.dao.PostTickerTapeTrainDao;
import com.nip.dto.vo.param.PostTickerTapeTrainUpdateParam;
import com.nip.entity.PostTickerTapeTrainEntity;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.nip.common.constants.PostTickerTapeTrainStatusEnum.FINISH;
import static com.nip.common.constants.PostTickerTapeTrainStatusEnum.HAS_SCORE;
import static com.nip.common.constants.PostTickerTapeTrainStatusEnum.UNDERWAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task 3.5 P1-08：finish 写 PostTickerTapeTrainStatusEnum.FINISH(2)，
 * 而 checkStatus 拦的是旧枚举 FINISH(3) —— 已结束的训练可被反复 begin/finish 覆盖时间。
 * 现在 checkStatus 统一新枚举并同时拦 FINISH(2) 与 HAS_SCORE(3)。
 */
@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class PostTickerTapeTrainServiceTest {
  @Inject PostTickerTapeTrainService service;
  @Inject PostTickerTapeTrainDao trainDao;

  private PostTickerTapeTrainEntity train(Integer status) {
    PostTickerTapeTrainEntity e = new PostTickerTapeTrainEntity();
    e.setStatus(status);
    e.setStartTime(LocalDateTime.now().minusMinutes(5));
    e.setValidTime("0");
    return trainDao.save(e);
  }

  @Test
  void finishedTrainRejectsSecondFinishAndBegin() {
    PostTickerTapeTrainEntity e = train(UNDERWAY.getCode());
    PostTickerTapeTrainUpdateParam param = new PostTickerTapeTrainUpdateParam();
    param.setId(e.getId());

    service.finish(param);
    assertEquals(FINISH.getCode(), trainDao.findById(e.getId()).getStatus());

    assertThrows(IllegalArgumentException.class, () -> service.finish(param),
        "已结束训练重复 finish 必须被拦截");
    assertThrows(IllegalArgumentException.class, () -> service.begin(e.getId()),
        "已结束训练重复 begin 必须被拦截");
  }

  @Test
  void scoredTrainRejectsBegin() {
    PostTickerTapeTrainEntity e = train(HAS_SCORE.getCode());
    assertThrows(IllegalArgumentException.class, () -> service.begin(e.getId()),
        "已评分训练必须被拦截");
  }
}
