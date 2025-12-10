package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TelegraphKeyTrainStatisticalEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.USER_ID;

/**
 * @Author: wushilin
 * @Data: 2022-06-09 11:56
 * @Description:
 */
@ApplicationScoped
public class TelegraphKeyTrainStatisticalDao extends BaseRepository<TelegraphKeyTrainStatisticalEntity, String> {

  public TelegraphKeyTrainStatisticalEntity findByUserIdAndType(String userId, Integer type) {
    return find("userId = ?1 and type = ?2", userId, type).firstResult();
  }

  public List<TelegraphKeyTrainStatisticalEntity> findByUserId(String userId) {
    return find(USER_ID, userId).list();
  }
}
