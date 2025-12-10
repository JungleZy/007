package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.DeviceDescriptionEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class DeviceDescriptionDao extends BaseRepository<DeviceDescriptionEntity, Integer> {

  @Transactional
  public void  deleteByDeviceId(Integer deviceId){
    delete("deviceId  =?1",deviceId);
  };

  @Transactional
  public void  deleteAllByDeviceIdIn(List<Integer> deviceIdList){
    delete("deviceId in (?1)",deviceIdList);
  };

  public List<DeviceDescriptionEntity> findByDeviceIdOrderByCreateTimeAsc(Integer deviceId){
    return find("deviceId = ?1",Sort.by("createTime").ascending(),deviceId).list();
  };
}