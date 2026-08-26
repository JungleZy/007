package com.nip.dao;

import com.nip.entity.PostTelegramTrainFloorContentEntity;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Task 5.2（persistence 分片 P2-11 / P2-12）：
 * - clearByTranId 曾用双引号包 HQL 字符串字面量（HQL 双引号是"带引号的标识符"，不是字符串）。
 * - findCountByTrainIdOrderByFloorNumberAscSortAsc 曾在无 GROUP BY 的聚合查询上带非聚合 ORDER BY，
 *   MySQL 8 默认 sql_mode=ONLY_FULL_GROUP_BY 直接拒绝。
 */
@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class PostTelegramTrainFloorContentDaoTest {
  @Inject PostTelegramTrainFloorContentDao dao;

  private static PostTelegramTrainFloorContentEntity content(String trainId, int floor, int sort) {
    PostTelegramTrainFloorContentEntity e = new PostTelegramTrainFloorContentEntity();
    e.setTrainId(trainId);
    e.setFloorNumber(floor);
    e.setSort(sort);
    e.setMoresKey("key-" + floor + "-" + sort);
    e.setMoresValue("[\"x\"]");
    e.setMoresTime("[100]");
    e.setPatKeys("[\"p\"]");
    return e;
  }

  @Test
  void clearByTranIdResetsContentToEmptyJsonArrays() {
    dao.save(content("p52-clear", 1, 0));
    dao.save(content("p52-clear", 1, 1));
    dao.save(content("p52-keep", 1, 0)); // 其他 train 不受影响

    dao.clearByTranId("p52-clear");

    List<PostTelegramTrainFloorContentEntity> cleared =
        dao.findByTrainIdOrderByFloorNumberSort("p52-clear");
    assertEquals(2, cleared.size());
    for (PostTelegramTrainFloorContentEntity e : cleared) {
      assertEquals("[]", e.getMoresValue(), "moresValue 必须被清为 []");
      assertEquals("[]", e.getMoresTime(), "moresTime 必须被清为 []");
      assertEquals("[]", e.getPatKeys(), "patKeys 必须被清为 []");
    }

    List<PostTelegramTrainFloorContentEntity> kept =
        dao.findByTrainIdOrderByFloorNumberSort("p52-keep");
    assertEquals(1, kept.size());
    assertFalse("[]".equals(kept.getFirst().getMoresValue()), "其他 train 的内容不得被清除");
  }

  @Test
  void countByTrainIdReturnsRowCount() {
    dao.save(content("p52-count", 1, 0));
    dao.save(content("p52-count", 1, 1));
    dao.save(content("p52-count", 2, 0));

    assertEquals(3, dao.findCountByTrainIdOrderByFloorNumberAscSortAsc("p52-count"));
    assertEquals(0, dao.findCountByTrainIdOrderByFloorNumberAscSortAsc("p52-count-none"));
  }
}
