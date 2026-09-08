package com.nip.service;

import com.nip.common.utils.Assert;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.EquipmentTrainDao;
import com.nip.dto.EquipmentTrainDto;
import com.nip.dto.vo.EquipmentTrainVO;
import com.nip.entity.EquipmentTrainEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class EquipmentTrainService {
  private final EquipmentTrainDao dao;
  private final UserService userService;

  @Inject
  public EquipmentTrainService(EquipmentTrainDao dao, UserService userService) {
    this.dao = dao;
    this.userService = userService;
  }

  @Transactional
  public String addTrain(EquipmentTrainDto dto, String token) {
    UserEntity entity = userService.getUserByToken(token);
    if (dto.getId() == null) {
      if (dto.getTrainType() == null) {
        throw new IllegalArgumentException("请传入训练类型");
      }
      EquipmentTrainEntity trainEntity = PojoUtils.convertOne(dto, EquipmentTrainEntity.class);
      trainEntity.setUserId(entity.getId());
      trainEntity.setTrainStatus(0);
      dao.saveAndFlush(trainEntity);
      return trainEntity.getId();
    } else {
      EquipmentTrainEntity trainEntity = dao.findByIdOptional(dto.getId())
          .orElseThrow(() -> new IllegalArgumentException("未查询到训练计划"));
      trainEntity.setTrainStatus(dto.getTrainStatus());
      trainEntity.setContent(dto.getContent());
      return trainEntity.getId();
    }
  }

  public List<EquipmentTrainVO> listPage(String token) {
    UserEntity entity = userService.getUserByToken(token);
    List<EquipmentTrainEntity> allByUserId = dao.findAllByUserId(entity.getId());
    return PojoUtils.convert(allByUserId, EquipmentTrainVO.class);
  }

  public EquipmentTrainVO detail(Map<String, String> param) {
    String id = param.get("id");
    Assert.notNull(id, "请传入ID");
    EquipmentTrainEntity entity = dao.findByIdOptional(id).orElse(new EquipmentTrainEntity());
    return PojoUtils.convertOne(entity, EquipmentTrainVO.class);
  }
}
