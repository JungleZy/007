package com.nip.service;

import com.nip.common.utils.PojoUtils;
import com.nip.dao.GeneralGroupNetRuleDao;
import com.nip.dto.vo.GeneralGroupNetRuleVO;
import com.nip.entity.GeneralGroupNetRuleEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class GeneralGroupNetRuleService {
  private final GeneralGroupNetRuleDao ruleDao;

  @Inject
  public GeneralGroupNetRuleService(GeneralGroupNetRuleDao ruleDao) {
    this.ruleDao = ruleDao;
  }

  @Transactional
  public GeneralGroupNetRuleVO save(GeneralGroupNetRuleVO vo) {
    GeneralGroupNetRuleEntity generalGroupNetRuleEntity = PojoUtils.convertOne(vo, GeneralGroupNetRuleEntity.class);
    ruleDao.saveAndFlush(generalGroupNetRuleEntity);
    return PojoUtils.convertOne(generalGroupNetRuleEntity, GeneralGroupNetRuleVO.class);
  }

  public List<GeneralGroupNetRuleVO> findAll() {
    List<GeneralGroupNetRuleEntity> all = ruleDao.findAll().list();
    return PojoUtils.convert(all, GeneralGroupNetRuleVO.class);
  }

  public void deleteById(GeneralGroupNetRuleVO vo) {
    ruleDao.deleteById(vo.getId());
  }
}
