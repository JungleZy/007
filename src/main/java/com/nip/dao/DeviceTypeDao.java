package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.DeviceTypeEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class DeviceTypeDao extends BaseRepository<DeviceTypeEntity, Integer> {

  public List<DeviceTypeEntity> findAllByIdIn(Set<Integer> ids) {
    return ids.isEmpty() ? List.of() : list("id in ?1", ids);
  }
}