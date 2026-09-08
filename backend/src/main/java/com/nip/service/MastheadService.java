package com.nip.service;

import com.nip.dao.MastheadDao;
import com.nip.entity.MastheadEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class MastheadService {
  private final MastheadDao mastheadDao;

  @Inject
  public MastheadService(MastheadDao mastheadDao) {
    this.mastheadDao = mastheadDao;
  }

  @Transactional
  public MastheadEntity save(MastheadEntity entity) {
    return mastheadDao.save(entity);
  }

  public MastheadEntity findByTrainId(String trainId) {
    return mastheadDao.findByTrainId(trainId);
  }
}
