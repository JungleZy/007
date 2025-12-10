package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.EnteringKeyPointsEntity;
import jakarta.enterprise.context.ApplicationScoped;

import static com.nip.common.constants.BaseConstants.TYPE;

@ApplicationScoped
public class EnteringKeyPointsDao extends BaseRepository<EnteringKeyPointsEntity, String> {
  /**
   * 根据类型查询
   *
   * @param: type
   */
  public EnteringKeyPointsEntity findByType(Integer type) {
    return find(TYPE, type).firstResult();
  }
}
