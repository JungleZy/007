package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TelegramTrainStatisticalEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.USER_ID;

/**
 * @Author: wushilin
 * @Data: 2022-06-01 16:30
 * @Description:
 */
@ApplicationScoped
public class TelegramTrainStatisticalDao extends BaseRepository<TelegramTrainStatisticalEntity, String> {
  public TelegramTrainStatisticalEntity findByUserIdAndType(String userId, Integer type) {
    return find("userId = ?1 and type = ?2", userId, type).firstResult();
  }

  public List<TelegramTrainStatisticalEntity> findByUserId(String userId) {
    return find(USER_ID, userId).list();
  }
}
