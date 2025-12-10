package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.ReceiveKeyPointsEntity;
import jakarta.enterprise.context.ApplicationScoped;

import static com.nip.common.constants.BaseConstants.TYPE;

/**
 * @Author: wushilin
 * @Data: 2022-04-11 09:21
 * @Description:
 */
@ApplicationScoped
public class ReceiveKeyPointsDao extends BaseRepository<ReceiveKeyPointsEntity, String> {

  public ReceiveKeyPointsEntity findByType(Integer type) {
    return find(TYPE, type).firstResult();
  }
}
