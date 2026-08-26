package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.DeviceEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class DeviceDao extends BaseRepository<DeviceEntity, Integer> {

  public List<DeviceEntity> findByDeviceTypeId(Integer id){
    return find("deviceTypeId = ?1",id).list();
  }

  /** 各设备类型下的设备数量（一次分组查询，替代逐类型 count 的 N+1） */
  public Map<Integer, Long> countGroupByDeviceTypeId() {
    return entityManager.createQuery(
            "select d.deviceTypeId, count(d) from DeviceEntity d group by d.deviceTypeId", Object[].class)
        .getResultList().stream()
        .collect(Collectors.toMap(r -> (Integer) r[0], r -> (Long) r[1]));
  }

  @Transactional
  public void  deleteByDeviceTypeId(Integer id){
    delete("deviceTypeId = ?1",id);
  }

}