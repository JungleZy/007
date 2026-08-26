package com.nip.service;

import com.nip.dao.PostTelexPatTrainDao;
import com.nip.dto.vo.PostTelexPatTrainVO;
import com.nip.dto.vo.param.PostTelexPatTrainFinishParam;
import com.nip.entity.PostTelexPatTrainEntity;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.nip.common.constants.PostTelexPatTrainStatusEnum.FINISH;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task 3.5 P1-09：finish 的幂等守卫曾被注释掉，重复 finish 会把「报底+用户值」混合行
 * 再解析一遍并全表删除重写。已完成训练必须直接返回，不重新结算。
 * （守卫失效时本用例会走 countScore：无规则/无页数据直接抛异常。）
 */
@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class PostTelexPatTrainServiceTest {
  @Inject PostTelexPatTrainService service;
  @Inject PostTelexPatTrainDao trainDao;

  @Test
  void finishOnFinishedTrainReturnsWithoutRecount() {
    PostTelexPatTrainEntity e = new PostTelexPatTrainEntity();
    e.setStatus(FINISH.getStatus());
    e.setTrainType(4);
    e.setScore("88");
    e = trainDao.save(e);

    PostTelexPatTrainFinishParam param = new PostTelexPatTrainFinishParam();
    param.setId(e.getId());

    PostTelexPatTrainVO vo = service.finish(param);

    assertEquals("88", vo.getScore(), "已完成训练的分数不得被重复结算覆盖");
    assertEquals("88", trainDao.findById(e.getId()).getScore());
  }
}
