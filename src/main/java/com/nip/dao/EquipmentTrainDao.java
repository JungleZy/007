package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.EquipmentTrainEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.USER_ID;

@ApplicationScoped
public class EquipmentTrainDao extends BaseRepository<EquipmentTrainEntity, String> {
  public List<EquipmentTrainEntity> findAllByUserId(String uid) {
    return find(USER_ID, Sort.by("trainTime").descending(), uid).list();
  }
}
