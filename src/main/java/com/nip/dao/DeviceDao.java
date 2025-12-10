package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.DeviceEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class DeviceDao extends BaseRepository<DeviceEntity, Integer> {

  public List<DeviceEntity> findByDeviceTypeId(Integer id){
    return find("deviceTypeId = ?1",id).list();
  }

  @Transactional
  public void  deleteByDeviceTypeId(Integer id){
    delete("deviceTypeId = ?1",id);
  }

}