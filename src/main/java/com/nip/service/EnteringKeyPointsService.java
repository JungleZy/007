package com.nip.service;

import com.nip.dao.EnteringKeyPointsDao;
import com.nip.entity.EnteringKeyPointsEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * @Author: wushilin
 * @Data: 2022-04-21 09:16
 * @Description:
 */
@ApplicationScoped
public class EnteringKeyPointsService {

  private final EnteringKeyPointsDao keyPointsDao;

  @Inject
  public EnteringKeyPointsService(EnteringKeyPointsDao keyPointsDao) {
    this.keyPointsDao = keyPointsDao;
  }

  @Transactional
  public EnteringKeyPointsEntity save(EnteringKeyPointsEntity entity) {
    return keyPointsDao.save(entity);
  }

  public EnteringKeyPointsEntity getByType(Integer type) {
    return keyPointsDao.findByType(type);
  }
}
