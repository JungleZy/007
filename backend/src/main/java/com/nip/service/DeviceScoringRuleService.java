package com.nip.service;

import com.nip.common.utils.PojoUtils;
import com.nip.dao.DeviceScoringRuleDao;
import com.nip.dao.DeviceDao;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Objects;
import com.nip.dto.DeviceScoringRuleDto;
import com.nip.dto.vo.DeviceScoringRuleVO;
import com.nip.entity.DeviceScoringRuleEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Optional;

/**
 * @Author: wushilin
 * @Data: 2023-08-22 14:05
 * @Description:
 */
@ApplicationScoped
public class DeviceScoringRuleService {
  private final DeviceScoringRuleDao deviceScoringRuleDao;
  private final DeviceDao deviceDao;

  @Inject
  public DeviceScoringRuleService(DeviceScoringRuleDao deviceScoringRuleDao, DeviceDao deviceDao) {
    this.deviceScoringRuleDao = deviceScoringRuleDao;
    this.deviceDao = deviceDao;
  }

  /**
   * 创建评分
   *
   * @param dto dto
   */
  @Transactional(rollbackOn = Exception.class)
  public void save(DeviceScoringRuleDto dto) {
    if (dto == null || dto.getDeviceId() == null || deviceDao.findById(dto.getDeviceId(), LockModeType.PESSIMISTIC_WRITE) == null) {
      throw new IllegalArgumentException("评分规则必须对应实际设备");
    }
    GroupNetScoring.validateRules(dto.getRuleContent());
    List<DeviceScoringRuleEntity> existing = deviceScoringRuleDao.list("deviceId = ?1", dto.getDeviceId());
    if (existing.stream().anyMatch(rule -> !Objects.equals(rule.getId(), dto.getId()))) {
      throw new IllegalArgumentException("该设备已有评分规则，请编辑原规则或删除重复规则");
    }
    //如果id是null则新增
    if (dto.getId() == null) {
      DeviceScoringRuleEntity ruleEntity = PojoUtils.convertOne(dto, DeviceScoringRuleEntity.class);
      deviceScoringRuleDao.saveAndFlush(ruleEntity);
    } else {
      //更新
      DeviceScoringRuleEntity ruleEntity = Optional.ofNullable(deviceScoringRuleDao.findById(dto.getId()))
          .orElseThrow(() -> new IllegalArgumentException("未查询到评分规则"));
      if (!Objects.equals(ruleEntity.getDeviceId(), dto.getDeviceId())) throw new IllegalArgumentException("评分规则与设备不匹配");
      ruleEntity.setRuleContent(dto.getRuleContent());
    }
  }

  /**
   * 根据id删除
   *
   * @param id id
   */
  @Transactional(rollbackOn = Exception.class)
  public void deleteRule(Integer id) {
    deviceScoringRuleDao.deleteById(id);
  }

  /**
   * 获取所有评分
   *
   * @param deviceId id
   * @return
   */
  public DeviceScoringRuleVO findAllByDeviceId(Integer deviceId) {
    List<DeviceScoringRuleEntity> rules = deviceScoringRuleDao.list("deviceId = ?1", deviceId);
    if (rules.size() > 1) throw new IllegalArgumentException("该设备存在重复评分规则，请先清理");
    return rules.isEmpty() ? null : PojoUtils.convertOne(rules.getFirst(), DeviceScoringRuleVO.class);
  }
}
