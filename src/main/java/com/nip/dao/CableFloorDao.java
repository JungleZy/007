package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.CableFloorEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CableFloorDao extends BaseRepository<CableFloorEntity, String> {
  public List<CableFloorEntity> findAllByCableId(String cableId) {
    return find("cableId = ?1 order by floorNumber,sort", cableId).list();
  }
  public List<CableFloorEntity> findAllByCableIdAndFloorNumber(String cableId, Integer floorNumber) {
    return find("cableId = ?1 and floorNumber = ?2 order by sort", cableId, floorNumber).list();
  }
  public void deleteByCableId(String cableId) {
    delete("cableId = ?1", cableId);
  }
}