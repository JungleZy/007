package com.nip.service;

import com.nip.common.utils.PojoUtils;
import com.nip.dao.DeviceScoringRuleDao;
import com.nip.dto.DeviceScoringRuleDto;
import com.nip.dto.vo.DeviceScoringRuleVO;
import com.nip.entity.DeviceScoringRuleEntity;
import io.quarkus.panache.common.Sort;
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

  @Inject
  public DeviceScoringRuleService(DeviceScoringRuleDao deviceScoringRuleDao) {
    this.deviceScoringRuleDao = deviceScoringRuleDao;
  }

  /**
   * 创建评分
   *
   * @param dto dto
   */
  @Transactional(rollbackOn = Exception.class)
  public void save(DeviceScoringRuleDto dto) {
    //如果id是null则新增
    if (dto.getId() == null) {
      DeviceScoringRuleEntity ruleEntity = PojoUtils.convertOne(dto, DeviceScoringRuleEntity.class);
      deviceScoringRuleDao.saveAndFlush(ruleEntity);
    } else {
      //更新
      DeviceScoringRuleEntity ruleEntity = Optional.ofNullable(deviceScoringRuleDao.findById(dto.getId()))
          .orElseThrow(() -> new RuntimeException("未查询到评分规则"));
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
    DeviceScoringRuleEntity data = deviceScoringRuleDao.find("deviceId = ?1", Sort.by("createTime"), deviceId)
        .list().stream().findFirst()
        .orElse(null);
    return PojoUtils.convertOne(data, DeviceScoringRuleVO.class);
  }
}
