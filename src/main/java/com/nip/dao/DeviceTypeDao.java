package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.DeviceTypeEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DeviceTypeDao extends BaseRepository<DeviceTypeEntity, Integer> {

}