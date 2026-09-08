package com.nip.service;

import com.nip.dao.PostTickerTapeTrainDao;
import com.nip.dao.PostTickerTapeTrainPageValueDao;
import com.nip.dto.vo.param.PostTickerTapeTrainUpdateParam;
import com.nip.dto.vo.param.PostTickerTapeTrainUploadResultParam;
import com.nip.entity.PostTickerTapeTrainEntity;


import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.nip.common.constants.PostTickerTapeTrainStatusEnum.FINISH;
import static com.nip.common.constants.PostTickerTapeTrainStatusEnum.HAS_SCORE;
import static com.nip.common.constants.PostTickerTapeTrainStatusEnum.NOT_STARTED;
import static com.nip.common.constants.PostTickerTapeTrainStatusEnum.UNDERWAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 3.5 P1-08：finish 写 PostTickerTapeTrainStatusEnum.FINISH(2)，
 * 而 checkStatus 拦的是旧枚举 FINISH(3) —— 已结束的训练可被反复 begin/finish 覆盖时间。
 * 现在 checkStatus 统一新枚举并同时拦 FINISH(2) 与 HAS_SCORE(3)。
 */
@QuarkusTest

class PostTickerTapeTrainServiceTest {
  @Inject PostTickerTapeTrainService service;
  @Inject PostTickerTapeTrainDao trainDao;
  @Inject PostTickerTapeTrainPageValueDao valueDao;

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

  // Task 6.2(a)：reset 把 startTime 置 null，checkStatus 只拦 FINISH/HAS_SCORE ——
  // 修复前 finish 里的 Duration.between(null, …) 会 NPE 成 HTTP 500。
  @Test
  void finishAfterResetIsRejectedWithBusinessError() {
    PostTickerTapeTrainEntity e = train(UNDERWAY.getCode());
    PostTickerTapeTrainUpdateParam param = new PostTickerTapeTrainUpdateParam();
    param.setId(e.getId());

    service.reset(e.getId());
    assertEquals(NOT_STARTED.getCode(), trainDao.findById(e.getId()).getStatus(),
        "reset 必须把状态置回未开始");

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.finish(param),
        "未开始的训练结算必须是明确的业务错误而非 NPE");
    assertEquals("训练还未开始，无法结算", ex.getMessage());

    PostTickerTapeTrainEntity after = trainDao.findById(e.getId());
    assertEquals(NOT_STARTED.getCode(), after.getStatus(), "被拒的结算不得改状态");
    assertNull(after.getEndTime(), "被拒的结算不得写结束时间");
  }

  // Task 6.2(b)：填报页数多于截图张数时，param.getImages().get(i) 越界 —— 必须先拒参数。
  @Test
  void uploadResultRejectsFewerImagesThanResultPages() {
    PostTickerTapeTrainEntity e = train(UNDERWAY.getCode());
    PostTickerTapeTrainUploadResultParam param = new PostTickerTapeTrainUploadResultParam();
    param.setId(e.getId());
    param.setResult(List.of(List.of("11111"), List.of("22222")));
    param.setImages(List.of("image-page-1"));

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> service.uploadResult(param), "填报页数多于截图张数必须是参数错误");
    assertEquals("截图数量与填报页数不一致", ex.getMessage());

    assertTrue(valueDao.findByTrainId(e.getId()).isEmpty(), "被拒的上传不得落任何填报页");
    assertNull(trainDao.findById(e.getId()).getScore(), "被拒的上传不得写分数");
  }
}
