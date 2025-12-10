package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostEnteringExerciseWordStockEntity;
import jakarta.enterprise.context.ApplicationScoped;

import static com.nip.common.constants.BaseConstants.TYPE;

@ApplicationScoped
public class PostEnteringExerciseWordStockDao
  extends BaseRepository<PostEnteringExerciseWordStockEntity, Integer> {

  /**
   * 根据type查询
   *
   * @param: type
   */
  public PostEnteringExerciseWordStockEntity findByType(Integer type) {
    return find(TYPE, type).firstResult();
  }
}
