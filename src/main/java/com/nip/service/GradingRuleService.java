package com.nip.service;


import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dao.GradingRuleDao;
import com.nip.entity.GradingRuleEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * LayersService
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-07-15 15:41
 */
@ApplicationScoped
public class GradingRuleService {
  private final GradingRuleDao gradingRuleDao;

  @Inject
  public GradingRuleService(GradingRuleDao gradingRuleDao) {
    this.gradingRuleDao = gradingRuleDao;
  }

  public Response<List<GradingRuleEntity>> getGradingRuleListByType(Integer type) {
    return ResponseResult.success(gradingRuleDao.findByType(type));
  }

  public Response<GradingRuleEntity> getGradingRuleById(String id) {
    return ResponseResult.success(Optional.ofNullable(gradingRuleDao.findById(id)).orElse(new GradingRuleEntity()));
  }

  @Transactional
  public Response<GradingRuleEntity> saveGradingRule(GradingRuleEntity entity) {
    List<GradingRuleEntity> byType = gradingRuleDao.findByType(entity.getType());
    List<GradingRuleEntity> def = new ArrayList<>();
    boolean flag = true;
    if (entity.getIsDefault() == 0) {
      byType.forEach(e -> {
        e.setIsDefault(1);
        def.add(e);
      });
    } else {
      if (StringUtils.isEmpty(entity.getId())) {
        for (GradingRuleEntity e : byType) {
          if (e.getIsDefault() == 0) {
            flag = false;
            break;
          }
        }
      } else {
        for (GradingRuleEntity e : byType) {
          if (e.getIsDefault() == 0 && !e.getId().equals(entity.getId())) {
            flag = false;
            break;
          }
        }
      }
    }
    gradingRuleDao.save(def);
    if (flag) {
      entity.setIsDefault(0);
    }
    if (StringUtils.isNotBlank(entity.getId())) {
      GradingRuleEntity ruleEntity = gradingRuleDao.findById(entity.getId());
      ruleEntity.setStatus(entity.getStatus());
      ruleEntity.setIsDefault(entity.getIsDefault());
      ruleEntity.setContent(entity.getContent());
      ruleEntity.setScore(entity.getScore());
      ruleEntity.setTitle(entity.getTitle());
      ruleEntity.setType(entity.getType());
    } else {
      gradingRuleDao.saveAndFlush(entity);
    }
    return ResponseResult.success(entity);
  }

  @Transactional
  public Response<GradingRuleEntity> updateGradingRuleStatus(String id, Integer status) {
    GradingRuleEntity entity = Optional.ofNullable(gradingRuleDao.findById(id)).orElse(new GradingRuleEntity());
    entity.setStatus(status);
    return ResponseResult.success(entity);
  }

  @Transactional
  public Response<Void> changeGradingRuleIsDefault(String id) {
    try {
      GradingRuleEntity entity = Optional.ofNullable(gradingRuleDao.findById(id)).orElse(new GradingRuleEntity());
      List<GradingRuleEntity> byType = gradingRuleDao.findByType(entity.getType());
      byType.forEach(e -> {
        e.setIsDefault(1);
        if (id.equals(e.getId())) {
          e.setIsDefault(0);
        }
      });
      return ResponseResult.success();
    } catch (Exception e) {
      return ResponseResult.error();
    }
  }
}
