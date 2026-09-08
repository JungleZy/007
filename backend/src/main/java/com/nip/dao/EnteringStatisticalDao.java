package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.EnteringStatisticalEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class EnteringStatisticalDao extends BaseRepository<EnteringStatisticalEntity, String> {

  public EnteringStatisticalEntity findByUserIdAndTypeAndChildType(String userId, Integer type, Integer childType) {
    return find("userId = ?1 and type = ?2 and childType = ?3", userId, type, childType).firstResult();
  }

  public List<EnteringStatisticalEntity> findByUserIdAndType(String userId, Integer type) {
    return find("userId = ?1 and type = ?2", userId, type).list();
  }
}
