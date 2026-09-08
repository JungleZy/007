package com.nip.dao;


import com.nip.common.repository.BaseRepository;
import com.nip.entity.TelegramTrainFloorEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * TelegramTrainFloorDao
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-20 11:15
 */
@ApplicationScoped
public class TelegramTrainFloorDao extends BaseRepository<TelegramTrainFloorEntity, String> {

  public List<TelegramTrainFloorEntity> findAllByTrainIdOrderBySort(String trainId) {
    return find("trainId = ?1 order by sort", trainId).list();
  }

  public TelegramTrainFloorEntity findAllByTrainIdAndPageNumber(String trainId, Integer sort) {
    return find("trainId = ?1 and sort = ?2", trainId, sort).firstResult();
  }
}
