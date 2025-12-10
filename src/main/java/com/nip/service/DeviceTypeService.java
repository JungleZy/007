package com.nip.service;

import com.nip.common.utils.PojoUtils;
import com.nip.dao.DeviceDao;
import com.nip.dao.DeviceDescriptionDao;
import com.nip.dao.DeviceTypeDao;
import com.nip.dao.UserDao;
import com.nip.dto.vo.DeviceTypeVO;
import com.nip.dto.vo.param.DeviceTypeUpdateParam;
import com.nip.entity.DeviceEntity;
import com.nip.entity.DeviceTypeEntity;
import com.nip.entity.UserEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-07-07 09:10
 * @Description:
 */
@ApplicationScoped
public class DeviceTypeService {
  private final DeviceTypeDao typeDao;
  private final UserDao userDao;
  private final DeviceDao deviceDao;
  private final DeviceDescriptionDao deviceDescriptionDao;

  @Inject
  public DeviceTypeService(DeviceTypeDao typeDao, UserDao userDao, DeviceDao deviceDao, DeviceDescriptionDao deviceDescriptionDao) {
    this.typeDao = typeDao;
    this.userDao = userDao;
    this.deviceDao = deviceDao;
    this.deviceDescriptionDao = deviceDescriptionDao;
  }

  @Transactional
  public DeviceTypeVO save(DeviceTypeUpdateParam param, String token) {
    if (param.getId() == null) {
      UserEntity userEntity = userDao.findUserEntityByToken(token);
      DeviceTypeEntity deviceTypeEntity = PojoUtils.convertOne(param, DeviceTypeEntity.class);
      deviceTypeEntity.setUserId(userEntity.getId());
      DeviceTypeEntity save = typeDao.save(deviceTypeEntity);
      return PojoUtils.convertOne(save, DeviceTypeVO.class);
    } else {
      DeviceTypeEntity typeEntity = typeDao.findById(param.getId());
      typeEntity.setTypeName(param.getTypeName());
      return PojoUtils.convertOne(typeEntity, DeviceTypeVO.class);
    }
  }

  public List<DeviceTypeVO> findAll() {
    List<DeviceTypeEntity> entityList = typeDao.findAll(Sort.by("createTime").ascending()).list();
    return PojoUtils.convert(entityList, DeviceTypeVO.class, (e, v) -> v.setExistDevice(deviceDao.findByDeviceTypeId(e.getId()).size()));
  }

  @Transactional
  public void delete(DeviceTypeUpdateParam param) {
    typeDao.deleteById(param.getId());
    //删除该类型下所有设备及操作说明
    List<DeviceEntity> deviceEntities = deviceDao.findByDeviceTypeId(param.getId());
    List<Integer> deviceIdList = deviceEntities.stream().map(DeviceEntity::getId).toList();
    deviceDao.deleteByDeviceTypeId(param.getId());
    deviceDescriptionDao.deleteAllByDeviceIdIn(deviceIdList);
  }
}
