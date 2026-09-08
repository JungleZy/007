package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.MastheadEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MastheadDao extends BaseRepository<MastheadEntity, String> {
  public MastheadEntity findByTrainId(String typeId) {
    return find("trainId", typeId).firstResult();
  }
}
