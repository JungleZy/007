package com.nip.service;

import com.nip.dao.KeyPointsDao;
import com.nip.entity.KeyPointsEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/23 15:48
 */
@ApplicationScoped
public class KeyPointsService {
  private final KeyPointsDao keyPointsDao;

  @Inject
  public KeyPointsService(KeyPointsDao keyPointsDao) {
    this.keyPointsDao = keyPointsDao;
  }

  @Transactional
  public KeyPointsEntity saveKeyPoints(KeyPointsEntity entity) {
    return keyPointsDao.save(entity);
  }

  public KeyPointsEntity findById(int type) {
    return keyPointsDao.findAllByType(type);
  }
}
