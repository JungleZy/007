package com.nip.service;

import com.nip.dao.CableDao;
import com.nip.dao.CableFloorDao;
import com.nip.dao.CableTypeDao;
import com.nip.entity.CableEntity;
import com.nip.entity.CableFloorEntity;
import com.nip.entity.CableTypeEntity;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 1.3：CableService.delete / CableTypeService.delete 原来 catch (RuntimeException) 返回 false，
 * 半删结果照常提交。现在异常直接逸出，由 InnoDB 回滚。
 * 限制：DevServices MySQL 下无法在测试内可靠制造「第一步成功、第二步失败」，
 * 因此退化为断言「删除成功时三张表都被清空」与「删不存在 id 时不产生部分删除」。
 */
@QuarkusTest
class CableDeleteAtomicityTest {
  @Inject CableService cableService;
  @Inject CableTypeService cableTypeService;
  @Inject CableDao cableDao;
  @Inject CableTypeDao cableTypeDao;
  @Inject CableFloorDao cableFloorDao;

  private CableTypeEntity seedType() {
    CableTypeEntity type = new CableTypeEntity();
    type.setTitle("原子性-" + UUID.randomUUID());
    return cableTypeDao.saveAndFlush(type);
  }

  private CableEntity seedCable(String typeId, int floors) {
    CableEntity cable = new CableEntity();
    cable.setTypeId(typeId);
    cable.setTitle("报文-" + UUID.randomUUID());
    cable.setScope(0);
    cable = cableDao.saveAndFlush(cable);
    for (int i = 0; i < floors; i++) {
      CableFloorEntity floor = new CableFloorEntity();
      floor.setCableId(cable.getId());
      floor.setTypeId(typeId);
      floor.setFloorNumber(i);
      floor.setSort(0);
      floor.setMoresKey("[\"A\"]");
      cableFloorDao.saveAndFlush(floor);
    }
    return cable;
  }

  @Test
  void deleteCableClearsHeadAndItsFloorsTogether() {
    CableTypeEntity type = seedType();
    CableEntity cable = seedCable(type.getId(), 2);

    assertTrue(cableService.delete(cable.getId()), "删除已存在报文必须返回 true");

    assertNull(cableDao.findById(cable.getId()), "报文头必须已删除");
    assertEquals(0, cableFloorDao.find("cableId", cable.getId()).count(), "报文楼层必须一并删除");
  }

  @Test
  void deleteMissingCableIdDoesNotTouchOtherCables() {
    CableTypeEntity type = seedType();
    CableEntity kept = seedCable(type.getId(), 3);

    assertFalse(cableService.delete(UUID.randomUUID().toString()), "删除不存在的报文必须返回 false");

    assertNotNull(cableDao.findById(kept.getId()), "无关报文头不得被删除");
    assertEquals(3, cableFloorDao.find("cableId", kept.getId()).count(), "无关报文楼层不得被删除");
  }

  @Test
  void deleteCableTypeClearsTypeCablesAndFloorsTogether() {
    CableTypeEntity type = seedType();
    CableEntity cable = seedCable(type.getId(), 2);

    assertTrue(cableTypeService.delete(type.getId()), "删除已存在报文类型必须返回 true");

    assertNull(cableTypeDao.findById(type.getId()), "报文类型必须已删除");
    assertNull(cableDao.findById(cable.getId()), "该类型下的报文头必须一并删除");
    assertEquals(0, cableFloorDao.find("typeId", type.getId()).count(), "该类型下的报文楼层必须一并删除");
  }
}
