package com.nip.service;

import com.nip.dao.PostTelexPatTrainDao;
import com.nip.dao.PostTelexPatTrainPageDao;
import com.nip.dto.vo.PostTelexPatTrainVO;
import com.nip.dto.vo.param.PostTelexPatTrainFinishParam;
import com.nip.entity.PostTelexPatTrainEntity;
import com.nip.entity.PostTelexPatTrainPageEntity;


import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.nip.common.constants.PostTelexPatTrainStatusEnum.FINISH;
import static com.nip.common.constants.PostTelexPatTrainStatusEnum.UNDERWAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 3.5 P1-09：finish 的幂等守卫曾被注释掉，重复 finish 会把「报底+用户值」混合行
 * 再解析一遍并全表删除重写。已完成训练必须直接返回，不重新结算。
 * （守卫失效时本用例会走 countScore：无规则/无页数据直接抛异常。）
 */
@QuarkusTest

class PostTelexPatTrainServiceTest {
  @Inject PostTelexPatTrainService service;
  @Inject PostTelexPatTrainDao trainDao;
  @Inject PostTelexPatTrainPageDao pageDao;

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

  /**
   * Task 2.3 P2：结算重建走「先构建校验、后删除写入」。报底页号不连续属于构建阶段失败，
   * 必须在 deleteByTrainId 之前抛出，旧报底行数与内容原封不动。
   * （修复前 delete 先执行、再用坏集合覆盖，目标表是 MyISAM 时旧报底永久丢失。）
   */
  @Test
  void finishWithNonContiguousPagesKeepsOldPagesIntact() {
    PostTelexPatTrainEntity e = new PostTelexPatTrainEntity();
    e.setStatus(UNDERWAY.getStatus());
    e.setTrainType(0);
    e.setRuleContent("{}");
    e = trainDao.save(e);
    String trainId = e.getId();

    // 报底只有第 1 页与第 3 页，缺第 2 页
    seedPage(trainId, 1);
    seedPage(trainId, 3);

    PostTelexPatTrainFinishParam param = new PostTelexPatTrainFinishParam();
    param.setId(trainId);

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.finish(param),
        "报底页号不连续必须在删除之前被拒绝");
    assertTrue(ex.getMessage().contains("报底页号不连续"), ex.getMessage());

    List<PostTelexPatTrainPageEntity> after = pageDao.findByTrainIdOrderBySort(trainId);
    assertEquals(20, after.size(), "构建阶段失败时旧报底不得被删除");
    assertEquals(
        List.of("0101", "0102", "0103", "0104", "0105", "0106", "0107", "0108", "0109", "0110",
            "0301", "0302", "0303", "0304", "0305", "0306", "0307", "0308", "0309", "0310"),
        after.stream().map(PostTelexPatTrainPageEntity::getKey).sorted().toList(),
        "旧报底内容必须原样保留");
    assertTrue(after.stream().allMatch(p -> p.getValue() == null), "旧报底不得被回写用户拍发值");
  }

  /** 播种一页 10 组报底：key 形如 0101…0110，sort 0..9，value 留空表示尚未回写 */
  private void seedPage(String trainId, int pageNumber) {
    for (int i = 0; i < 10; i++) {
      PostTelexPatTrainPageEntity page = new PostTelexPatTrainPageEntity();
      page.setTrainId(trainId);
      page.setPageNumber(pageNumber);
      page.setSort(i);
      page.setKey(String.format("%02d%02d", pageNumber, i + 1));
      pageDao.save(page);
    }
  }
}
