package com.nip.service;

import com.nip.dao.CableDao;
import com.nip.dao.CableFloorDao;
import com.nip.dao.CableTypeDao;
import com.nip.entity.CableEntity;
import com.nip.entity.CableTypeEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@ApplicationScoped
@Slf4j
public class CableTypeService {
  private final CableTypeDao cableTypeDao;
  private final CableDao cableDao;
  private final CableFloorDao cableFloorDao;

  @Inject
  public CableTypeService(CableTypeDao cableTypeDao, CableDao cableDao, CableFloorDao cableFloorDao) {
    this.cableTypeDao = cableTypeDao;
    this.cableDao = cableDao;
    this.cableFloorDao = cableFloorDao;
  }

  public List<CableTypeEntity> findAll() {
    return cableTypeDao.findAll().list();
  }

  @Transactional
  public List<CableTypeEntity> save(CableTypeEntity entity) {
    CableTypeEntity cableTypeEntity = cableTypeDao.saveAndFlush(entity);
    List<CableEntity> allByTypeId = cableDao.findAllByTypeId(cableTypeEntity.getId());
    if (!allByTypeId.isEmpty()) {
      allByTypeId.forEach(cableEntity -> {
        cableEntity.setTypeId(cableTypeEntity.getId());
        cableEntity.setTypeTitle(cableTypeEntity.getTitle());
        cableDao.saveAndFlush(cableEntity);
      });
    }
    return cableTypeDao.findAll().list();
  }

  @Transactional
  public boolean delete(String id) {
    try {
      cableTypeDao.deleteById(id);
      cableDao.delete("typeId", id);
      cableFloorDao.delete("typeId", id);
      return true;
    } catch (RuntimeException e) {
      log.error("删除报文失败", e);
      return false;
    }

  }
}
