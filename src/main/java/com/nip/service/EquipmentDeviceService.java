package com.nip.service;

import com.nip.common.utils.PojoUtils;
import com.nip.dao.EquipmentDeviceDao;
import com.nip.dto.EquipmentDeviceDto;
import com.nip.dto.EquipmentDeviceKeyPointsDto;
import com.nip.dto.vo.EquipmentDeviceKeyPointsVo;
import com.nip.dto.vo.EquipmentDeviceVo;
import com.nip.entity.EquipmentDeviceEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class EquipmentDeviceService {
  private final EquipmentDeviceDao deviceDao;

  @Inject
  public EquipmentDeviceService(EquipmentDeviceDao deviceDao) {
    this.deviceDao = deviceDao;
  }

  public void addDevice(EquipmentDeviceDto dto) {
    EquipmentDeviceEntity entity = PojoUtils.convertOne(dto, EquipmentDeviceEntity.class);
    deviceDao.saveAndFlush(entity);
  }

  public void deleteDevice(EquipmentDeviceVo vo) {
    deviceDao.deleteById(vo.getId());
  }

  public List<EquipmentDeviceVo> listPage() {
    List<EquipmentDeviceEntity> entityList = deviceDao.findAll(Sort.by("createTime").ascending()).list();
    return PojoUtils.convert(entityList, EquipmentDeviceVo.class);
  }

  public void update(EquipmentDeviceVo vo) {
    EquipmentDeviceEntity entity = deviceDao.findById(vo.getId());
    entity.setImage(vo.getImage());
    entity.setIsEnable(vo.getIsEnable());
    entity.setName(vo.getName());
    entity.setOption(vo.getOption());
    deviceDao.saveAndFlush(entity);
  }

  public void saveKeyPoints(EquipmentDeviceKeyPointsDto dto) {
    EquipmentDeviceEntity entity = deviceDao.findById(dto.getDeviceId());
    entity.setKeyPoints(dto.getContent());
    deviceDao.saveAndFlush(entity);
  }

  public List<EquipmentDeviceKeyPointsVo> listKeyPoints() {
    List<EquipmentDeviceEntity> entityList = deviceDao.findAll(Sort.by("createTime").ascending()).list();
    return PojoUtils.convert(entityList, EquipmentDeviceKeyPointsVo.class, (e, v) -> {
      v.setDeviceName(e.getName());
      v.setConnect(e.getKeyPoints());
    });
  }
}
