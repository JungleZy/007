package com.nip.service;

import com.nip.common.PageInfo;
import com.nip.common.utils.Page;
import com.nip.dao.DeviceDao;
import com.nip.dao.DeviceTypeDao;
import com.nip.dao.GroupNetTrainDao;
import com.nip.dao.UserDao;
import com.nip.dto.vo.GroupNetTrainListPageVO;
import com.nip.entity.DeviceEntity;
import com.nip.entity.DeviceTypeEntity;
import com.nip.entity.GroupNetTrainEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class GroupNetTrainServiceTest {
  @Inject GroupNetTrainDao trainDao;
  @Inject DeviceDao deviceDao;
  @Inject DeviceTypeDao deviceTypeDao;
  @Inject UserDao userDao;
  @Inject UserService userService;

  private DeviceTypeEntity deviceType(String typeName) {
    DeviceTypeEntity t = new DeviceTypeEntity();
    t.setTypeName(typeName);
    return deviceTypeDao.save(t);
  }

  private DeviceEntity device(String name, Integer typeId) {
    DeviceEntity d = new DeviceEntity();
    d.setDeviceName(name);
    d.setDeviceTypeId(typeId);
    return deviceDao.save(d);
  }

  private void train(String createUser, Integer deviceId, Integer deviceType) {
    GroupNetTrainEntity e = new GroupNetTrainEntity();
    e.setCreateUser(createUser);
    e.setDeviceId(deviceId);
    e.setDeviceType(deviceType);
    trainDao.save(e);
  }

  @Test
  @TestTransaction
  void listPageBatchesDeviceAndTypeLookupsPreservingPageInfo() {
    UserEntity user = Fixtures.user(userDao, "gnt-" + UUID.randomUUID());
    DeviceTypeEntity typeA = deviceType("type-A");
    DeviceTypeEntity typeB = deviceType("type-B");
    DeviceEntity devOne = device("dev-1", typeA.getId());
    DeviceEntity devTwo = device("dev-2", typeB.getId());

    train(user.getId(), devOne.getId(), typeA.getId());
    train(user.getId(), devTwo.getId(), typeB.getId());

    CountingDeviceDao countingDeviceDao = new CountingDeviceDao();
    CountingDeviceTypeDao countingDeviceTypeDao = new CountingDeviceTypeDao();
    GroupNetTrainService svc =
        new GroupNetTrainService(trainDao, userService, countingDeviceDao, countingDeviceTypeDao);

    Page page = new Page();
    page.setPage(1);
    page.setRows(20);

    PageInfo<GroupNetTrainListPageVO> result = svc.listPage(page, user.getToken());

    // 完整 DTO 内容：每行的设备名/类型名按 id 正确装配
    List<GroupNetTrainListPageVO> data = result.getData();
    assertEquals(2, data.size());
    Map<String, String> typeByDevice = new HashMap<>();
    for (GroupNetTrainListPageVO vo : data) {
      typeByDevice.put(vo.getDeviceName(), vo.getDeviceTypeName());
    }
    assertEquals("type-A", typeByDevice.get("dev-1"));
    assertEquals("type-B", typeByDevice.get("dev-2"));

    // PageInfo 语义与 PojoUtils.convertPage 完全一致
    assertEquals(20, result.getPageSize());
    assertEquals(0, result.getCurrentPage());
    assertEquals(2L, result.getTotalNumber());
    assertEquals(1, result.getTotalPage());

    // N+1 契约：批量各一次，循环内单 id 查询为零
    assertEquals(1, countingDeviceDao.batchCalls);
    assertEquals(1, countingDeviceTypeDao.batchCalls);
    assertEquals(0, countingDeviceDao.findByIdCalls);
    assertEquals(0, countingDeviceTypeDao.findByIdCalls);
  }

  static class CountingDeviceDao extends DeviceDao {
    int batchCalls;
    int findByIdCalls;

    public List<DeviceEntity> list(String query, Object... params) {
      batchCalls++;
      return super.list(query, params);
    }

    @Override
    public DeviceEntity findById(Integer id) {
      findByIdCalls++;
      return find("id = ?1", id).firstResult();
    }
  }

  static class CountingDeviceTypeDao extends DeviceTypeDao {
    int batchCalls;
    int findByIdCalls;

    @Override
    public List<DeviceTypeEntity> findAllByIdIn(Set<Integer> ids) {
      batchCalls++;
      return super.findAllByIdIn(ids);
    }

    @Override
    public DeviceTypeEntity findById(Integer id) {
      findByIdCalls++;
      return find("id = ?1", id).firstResult();
    }
  }
}
