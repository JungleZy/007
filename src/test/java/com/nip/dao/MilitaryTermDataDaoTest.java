package com.nip.dao;

import com.nip.entity.MilitaryTermDataEntity;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 5.2（persistence 分片 P2-7）：in (?1) 对空/null 集合无判空防护。
 * 契约：空集合与 null 一律安全返回空列表，非空集合正常命中。
 */
@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class MilitaryTermDataDaoTest {
  @Inject MilitaryTermDataDao dao;

  @Test
  void inQueriesReturnEmptyListForEmptyOrNullInput() {
    assertTrue(dao.findAllByIdIn(List.of()).isEmpty(), "空 id 集合必须安全返回空列表");
    assertTrue(dao.findAllByParentIdIn(List.of()).isEmpty(), "空 parentId 集合必须安全返回空列表");
    assertTrue(dao.findAllByIdIn(null).isEmpty(), "null id 集合必须安全返回空列表");
    assertTrue(dao.findAllByParentIdIn(null).isEmpty(), "null parentId 集合必须安全返回空列表");
  }

  @Test
  void inQueriesStillMatchWithNonEmptyInput() {
    MilitaryTermDataEntity a = dao.save(new MilitaryTermDataEntity()
        .setParentId("p52-in-parent").setKey("p52-in-a").setValue("va").setSort(0));
    dao.save(new MilitaryTermDataEntity()
        .setParentId("p52-in-other").setKey("p52-in-b").setValue("vb").setSort(0));

    List<MilitaryTermDataEntity> byId = dao.findAllByIdIn(List.of(a.getId()));
    assertEquals(1, byId.size());
    assertEquals(a.getId(), byId.getFirst().getId());

    List<MilitaryTermDataEntity> byParent = dao.findAllByParentIdIn(List.of("p52-in-parent"));
    assertEquals(1, byParent.size());
    assertEquals("p52-in-a", byParent.getFirst().getKey());
  }
}
