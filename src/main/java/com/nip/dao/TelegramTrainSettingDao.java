package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TelegramTrainSettingEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import static com.nip.common.constants.BaseConstants.TYPE;

/**
 * TelegramTrainSettingDao
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-02-08 10:10:34
 */
@ApplicationScoped
public class TelegramTrainSettingDao extends BaseRepository<TelegramTrainSettingEntity, String> {
  @Transactional
  public void deleteAllByType(int type) {
    delete(TYPE, type);
  }
}
