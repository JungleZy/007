package com.nip.dao;


import com.nip.common.repository.BaseRepository;
import com.nip.entity.TelegraphKeyPatTrainEntity;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * @Author: wushilin
 * @Data: 2022-06-09 09:18
 * @Description:
 */
@ApplicationScoped
public class TelegraphKeyPatTrainDao extends BaseRepository<TelegraphKeyPatTrainEntity, String> {
  public TelegraphKeyPatTrainEntity findByCreateUserIdAndType(String userId, Integer type) {
    return find("createUserId = ?1 and type = ?2", userId, type).firstResult();
  }
}
