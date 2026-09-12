package com.nip.service;

import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.ScoringRuleValidation;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.PostTelegramTrainDao;
import com.nip.dao.PostTelexPatTrainDao;
import com.nip.dao.PostTelegraphKeyPatTrainDao;
import com.nip.dao.general.key.GeneralKeyPatDao;
import com.nip.dao.general.telex.GeneralTelexPatDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainDao;
import com.nip.entity.GradingRuleEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
  PostTelegramTrainDao postTelegramTrainDao;
  @Inject
  PostTelexPatTrainDao postTelexPatTrainDao;
  @Inject
  PostTelegraphKeyPatTrainDao postTelegraphKeyPatTrainDao;
  @Inject
  GeneralTelexPatDao generalTelexPatDao;
  @Inject
  GeneralKeyPatDao generalKeyPatDao;
  @Inject
  GeneralTickerPatTrainDao generalTickerPatTrainDao;

  @Inject
  public GradingRuleService(GradingRuleDao gradingRuleDao) {
    this.gradingRuleDao = gradingRuleDao;
  }

  public Response<List<GradingRuleEntity>> getGradingRuleListByType(Integer type) {
    return ResponseResult.success(gradingRuleDao.findByType(type));
  }

  public Response<GradingRuleEntity> getGradingRuleById(String id) {
    GradingRuleEntity entity = gradingRuleDao.findByIdOptional(id)
        .orElseThrow(() -> new IllegalArgumentException("未查询到评分规则"));
    return ResponseResult.success(entity);
  }

  @Transactional
  public Response<GradingRuleEntity> saveGradingRule(GradingRuleEntity entity) {
    if (entity == null) throw new IllegalArgumentException("评分规则不能为空");
    if ((Objects.equals(entity.getType(), 0) || Objects.equals(entity.getType(), 2) || Objects.equals(entity.getType(), 3))
        && (entity.getScore() == null || entity.getScore() < 0)) throw new IllegalArgumentException("评分规则满分必须为非负整数");
    if (Objects.equals(entity.getType(), 0)) ScoringRuleValidation.handkey(entity.getContent());
    else if (Objects.equals(entity.getType(), 2)) ScoringRuleValidation.telex(entity.getContent());
    else if (Objects.equals(entity.getType(), 3)) ScoringRuleValidation.electronic(entity.getContent());
    List<GradingRuleEntity> byType = gradingRuleDao.findByType(entity.getType());
    List<GradingRuleEntity> def = new ArrayList<>();
    boolean flag = true;
    if (Objects.equals(entity.getIsDefault(), 0)) {
      byType.forEach(e -> {
        e.setIsDefault(1);
        def.add(e);
      });
    } else {
      if (StringUtils.isEmpty(entity.getId())) {
        for (GradingRuleEntity e : byType) {
          if (Objects.equals(e.getIsDefault(), 0)) {
            flag = false;
            break;
          }
        }
      } else {
        for (GradingRuleEntity e : byType) {
          if (Objects.equals(e.getIsDefault(), 0) && !e.getId().equals(entity.getId())) {
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
      GradingRuleEntity ruleEntity = gradingRuleDao.findByIdOptional(entity.getId())
          .orElseThrow(() -> new IllegalArgumentException("未查询到评分规则"));
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
    GradingRuleEntity entity = gradingRuleDao.findByIdOptional(id)
        .orElseThrow(() -> new IllegalArgumentException("未查询到评分规则"));
    entity.setStatus(status);
    return ResponseResult.success(entity);
  }

  @Transactional
  public Response<Void> changeGradingRuleIsDefault(String id) {
    GradingRuleEntity entity = gradingRuleDao.findByIdOptional(id)
        .orElseThrow(() -> new IllegalArgumentException("未查询到评分规则"));
    List<GradingRuleEntity> byType = gradingRuleDao.findByType(entity.getType());
    byType.forEach(e -> {
      e.setIsDefault(1);
      if (id.equals(e.getId())) {
        e.setIsDefault(0);
      }
    });
    return ResponseResult.success();
  }

  @Transactional
  public Response<Void> deleteGradingRule(String id) {
    long c1 = postTelegramTrainDao.count("ruleId = ?1 and (status = 0 or status = 1)", id);
    long c2 = postTelexPatTrainDao.count("ruleId = ?1 and (status = 0 or status = 1)", id);
    long c3 = postTelegraphKeyPatTrainDao.count("ruleId = ?1 and (status = 0 or status = 1)", id);
    long c4 = generalTelexPatDao.count("ruleId = ?1 and (status = 0 or status = 1)", id);
    long c5 = generalKeyPatDao.count("ruleId = ?1 and (status = 0 or status = 1)", id);
    long c6 = generalTickerPatTrainDao.count("ruleId = ?1 and (status = 0 or status = 1)", id);
    if (c1 + c2 + c3 + c4 + c5 + c6 > 0) {
      return ResponseResult.error("存在未开始或进行中的训练引用该评分规则，禁止删除");
    }
    gradingRuleDao.deleteById(id);
    return ResponseResult.success();
  }
}
