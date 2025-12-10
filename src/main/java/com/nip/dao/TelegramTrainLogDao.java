package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TelegramTrainLogEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * TelegramTrainLogDao
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-01-13 13:55:38
 */
@ApplicationScoped
public class TelegramTrainLogDao extends BaseRepository<TelegramTrainLogEntity, String> {
  public List<TelegramTrainLogEntity> findAllByTelegramTrainIdOrderByCreatTimeAsc(String telegramTrainId) {
    return find("telegramTrainId = ?1 order by creatTime asc", telegramTrainId).list();
  }
}
