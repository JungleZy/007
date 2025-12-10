package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.GradingRuleEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TYPE;

@ApplicationScoped
public class GradingRuleDao extends BaseRepository<GradingRuleEntity, String> {
  public List<GradingRuleEntity> findByType(Integer type) {
    return find(TYPE, type).list();
  }
}
