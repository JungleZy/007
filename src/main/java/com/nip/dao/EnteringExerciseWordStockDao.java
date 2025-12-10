package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.EnteringExerciseWordStockEntity;
import jakarta.enterprise.context.ApplicationScoped;

import static com.nip.common.constants.BaseConstants.TYPE;

@ApplicationScoped
public class EnteringExerciseWordStockDao extends BaseRepository<EnteringExerciseWordStockEntity, String> {
  /**
   * 根据type查询
   *
   * @param: type
   */
  public EnteringExerciseWordStockEntity findByType(Integer type) {
    return find(TYPE, type).firstResult();
  }
}
