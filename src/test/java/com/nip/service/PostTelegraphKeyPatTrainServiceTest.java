package com.nip.service;

import com.nip.dao.PostTelegraphKeyPatTrainDao;
import com.nip.dto.PostTelegraphKeyPatTrainDto;
import com.nip.dto.vo.PostTelegraphKeyPatTrainVO;
import com.nip.entity.PostTelegraphKeyPatTrainEntity;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.nip.common.constants.PostTelegraphKeyPatTrainEnum.FINISH;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task 3.5 P1-10：finish 原来没有任何状态守卫，重复 finish 每次 deleteByTrainId+重插、
 * 时长按新 endTime 重算覆盖。已完成训练必须直接返回。
 * （守卫失效时本用例会走 countScore：无规则内容直接抛异常。）
 */
@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class PostTelegraphKeyPatTrainServiceTest {
  @Inject PostTelegraphKeyPatTrainService service;
  @Inject PostTelegraphKeyPatTrainDao trainDao;

  @Test
  void finishOnFinishedTrainReturnsWithoutRecount() {
    PostTelegraphKeyPatTrainEntity e = new PostTelegraphKeyPatTrainEntity();
    e.setStatus(FINISH.getStatus());
    e.setScore(new BigDecimal(77));
    e = trainDao.save(e);

    PostTelegraphKeyPatTrainDto dto = new PostTelegraphKeyPatTrainDto();
    dto.setId(e.getId());

    PostTelegraphKeyPatTrainVO vo = service.finish(dto);

    assertEquals(0, new BigDecimal(77).compareTo(trainDao.findById(e.getId()).getScore()),
        "已完成训练的分数不得被重复结算覆盖");
  }
}
