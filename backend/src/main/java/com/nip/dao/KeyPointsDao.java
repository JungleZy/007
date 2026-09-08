package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.KeyPointsEntity;
import jakarta.enterprise.context.ApplicationScoped;

import static com.nip.common.constants.BaseConstants.TYPE;

@ApplicationScoped
public class KeyPointsDao extends BaseRepository<KeyPointsEntity, String> {
  public KeyPointsEntity findAllByType(int type) {
    return find(TYPE, type).firstResult();
  }
}
