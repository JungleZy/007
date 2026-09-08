package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.RadiotelephoneEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.USER_ID;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 09:32
 * @Description:
 */
@ApplicationScoped
public class RadiotelephoneDao extends BaseRepository<RadiotelephoneEntity, String> {

  public List<RadiotelephoneEntity> findAllByUserId(String userId) {
    return find(USER_ID, userId).list();
  }

  public RadiotelephoneEntity findByUserIdAndType(String userId, Integer type) {
    return find("userId = ?1 and type = ?2", userId, type).firstResult();
  }
}
