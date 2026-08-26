package com.nip.service;

import com.nip.dao.EnteringExerciseDao;
import com.nip.dto.vo.EquipmentDeviceVo;
import com.nip.dto.vo.param.EnteringExerciseFinishParam;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 7 findById 家族抽样边界测试：裸 findById 解引用统一改为
 * findByIdOptional().orElseThrow(IllegalArgumentException) 后，
 * 不存在的 id 必须显式报错，且不得产生副作用（EnteringExerciseService.finish
 * 修复前会 persist 一条全空新行）。
 */
@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class FindByIdBoundaryTest {

  @Inject EnteringExerciseService enteringExerciseService;
  @Inject EnteringExerciseDao enteringExerciseDao;
  @Inject RoleService roleService;
  @Inject EquipmentDeviceService equipmentDeviceService;

  @Test
  void finishOnMissingExerciseThrowsAndPersistsNothing() {
    long before = enteringExerciseDao.count();
    EnteringExerciseFinishParam param = new EnteringExerciseFinishParam();
    param.setId("no-such-exercise-id");
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> enteringExerciseService.finish(param), "不存在的训练 id 必须显式报错");
    assertEquals("未查询到该训练", ex.getMessage());
    assertEquals(before, enteringExerciseDao.count(), "不存在的 id 不得落任何新行");
  }

  @Test
  void getRoleByMissingIdThrows() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> roleService.getRoleById("no-such-role-id"), "不存在的角色 id 必须显式报错而非 NPE");
    assertEquals("未查询到该角色", ex.getMessage());
  }

  @Test
  void updateMissingEquipmentDeviceThrows() {
    EquipmentDeviceVo vo = new EquipmentDeviceVo();
    vo.setId("no-such-device-id");
    vo.setName("任意名称");
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> equipmentDeviceService.update(vo), "不存在的设备 id 必须显式报错而非 NPE");
    assertEquals("未查询到该设备", ex.getMessage());
  }
}
