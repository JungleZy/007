package com.nip.service;

import com.nip.common.utils.PojoUtils;
import com.nip.dao.DeviceDao;
import com.nip.dao.DeviceDescriptionDao;
import com.nip.dto.vo.DeviceDescriptionVO;
import com.nip.dto.vo.DeviceVO;
import com.nip.dto.vo.param.DeviceDescriptionAddParam;
import com.nip.dto.vo.param.DeviceUpdateParam;
import com.nip.entity.DeviceDescriptionEntity;
import com.nip.entity.DeviceEntity;
import com.nip.entity.UserEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * @Author: wushilin
 * @Data: 2023-07-07 09:35
 * @Description:
 */
@ApplicationScoped
public class DeviceService {
  private final DeviceDao deviceDao;
  private final DeviceDescriptionDao deviceDescriptionDao;
  private final UserService userService;

  @Inject
  public DeviceService(DeviceDao deviceDao, DeviceDescriptionDao deviceDescriptionDao, UserService userService) {
    this.deviceDao = deviceDao;
    this.deviceDescriptionDao = deviceDescriptionDao;
    this.userService = userService;
  }

  @Transactional
  public DeviceVO save(DeviceUpdateParam param, String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    if (param.getId() == null) {
      DeviceEntity deviceEntity = PojoUtils.convertOne(param, DeviceEntity.class);
      deviceEntity.setUserId(userEntity.getId());
      DeviceEntity saved = deviceDao.saveAndFlush(deviceEntity);
      List<DeviceDescriptionEntity> descriptionEntities = PojoUtils.convert(
          param.getDescriptions(), DeviceDescriptionEntity.class, (v, e) -> {
            e.setUserId(userEntity.getId());
            e.setDeviceId(saved.getId());
          });
      List<DeviceDescriptionEntity> save = deviceDescriptionDao.save(descriptionEntities);
      return PojoUtils.convertOne(saved, DeviceVO.class, (e, v) -> v.setDescriptions(PojoUtils.convert(save, DeviceDescriptionVO.class)));
    } else {
      DeviceEntity deviceEntity = Optional.ofNullable(deviceDao.findById(param.getId()))
          .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
      deviceEntity.setDeviceImg(param.getDeviceImg());
      deviceEntity.setDeviceNumber(param.getDeviceNumber());
      deviceEntity.setDeviceTypeId(param.getDeviceTypeId());
      deviceEntity.setDeviceName(param.getDeviceName());
      DeviceEntity saved = deviceDao.saveAndFlush(deviceEntity);
      //删除该设备的所有描述重新新增
      deviceDescriptionDao.deleteByDeviceId(param.getId());
      List<DeviceDescriptionEntity> descriptionEntities = PojoUtils.convert(
          param.getDescriptions(), DeviceDescriptionEntity.class, (v, e) -> {
            e.setId(null);
            e.setUserId(userEntity.getId());
            e.setDeviceId(saved.getId());
          });
      List<DeviceDescriptionEntity> save = deviceDescriptionDao.save(descriptionEntities);
      return PojoUtils.convertOne(saved, DeviceVO.class, (e, v) -> v.setDescriptions(PojoUtils.convert(save, DeviceDescriptionVO.class)));
    }
  }

  public List<DeviceVO> listPage(Integer deviceTypeId) {
    List<DeviceEntity> deviceEntities = deviceDao.find("deviceTypeId = ?1", Sort.by("createTime").ascending(),
        deviceTypeId).list();
    return PojoUtils.convert(deviceEntities, DeviceVO.class, (e, v) -> {
      List<DeviceDescriptionEntity> descriptionEntities = deviceDescriptionDao.findByDeviceIdOrderByCreateTimeAsc(
          e.getId());
      v.setDescriptions(PojoUtils.convert(descriptionEntities, DeviceDescriptionVO.class));
    });
  }

  @Transactional
  public void delete(Integer deviceId) {
    deviceDao.deleteById(deviceId);
    deviceDescriptionDao.deleteByDeviceId(deviceId);
  }

  @Transactional
  public DeviceDescriptionVO addDescription(DeviceDescriptionAddParam param, String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    DeviceDescriptionEntity descriptionEntity = PojoUtils.convertOne(param, DeviceDescriptionEntity.class);
    descriptionEntity.setUserId(userEntity.getId());
    DeviceDescriptionEntity saved = deviceDescriptionDao.saveAndFlush(descriptionEntity);
    return PojoUtils.convertOne(saved, DeviceDescriptionVO.class);
  }

}
