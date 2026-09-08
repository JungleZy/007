package com.nip.dao;


import com.nip.common.repository.BaseRepository;
import com.nip.entity.TelegramTrainFloorContentEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * TelegramTrainFloorContentDao
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-20 11:15
 */
@ApplicationScoped
public class TelegramTrainFloorContentDao extends BaseRepository<TelegramTrainFloorContentEntity, String> {
  public List<TelegramTrainFloorContentEntity> findAllByFloorIdOrderBySort(String floorId) {
    return find("floorId = ?1 order by sort", floorId).list();
  }

  public List<TelegramTrainFloorContentEntity> findAllByFloorId(String floorId) {
    return find("floorId = ?1", floorId).list();
  }

  public List<TelegramTrainFloorContentEntity> findByFloorIdIn(List<String> ids) {
    return find("floorId in(?1)", ids).list();
  }
}
