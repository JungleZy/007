package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.CableEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CableDao extends BaseRepository<CableEntity, String> {
  public List<CableEntity> findAllByTypeId(String typeId) {
    return find("typeId", typeId).list();
  }
}