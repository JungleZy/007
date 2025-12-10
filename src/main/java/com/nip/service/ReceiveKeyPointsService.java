package com.nip.service;

import com.nip.dao.ReceiveKeyPointsDao;
import com.nip.entity.ReceiveKeyPointsEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * @Author: wushilin
 * @Data: 2022-04-11 09:21
 * @Description:
 */
@ApplicationScoped
public class ReceiveKeyPointsService {

  private final ReceiveKeyPointsDao keyPointsDao;

  @Inject
  public ReceiveKeyPointsService(ReceiveKeyPointsDao keyPointsDao) {
    this.keyPointsDao = keyPointsDao;
  }

  @Transactional
  public ReceiveKeyPointsEntity save(ReceiveKeyPointsEntity entity) {
    return keyPointsDao.save(entity);
  }

  public ReceiveKeyPointsEntity getByType(Integer type) {
    return keyPointsDao.findByType(type);
  }
}
