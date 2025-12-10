package com.nip.service;

import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.CableDao;
import com.nip.dao.CableFloorDao;
import com.nip.dao.CableTypeDao;
import com.nip.dto.vo.CableVO;
import com.nip.entity.CableEntity;
import com.nip.entity.CableFloorEntity;
import com.nip.entity.CableTypeEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@ApplicationScoped
@Slf4j
public class CableService {
  private final CableDao cableDao;
  private final CableTypeDao cableTypeDao;
  private final CableFloorDao cableFloorDao;

  @Inject
  public CableService(CableDao cableDao, CableTypeDao cableTypeDao, CableFloorDao cableFloorDao) {
    this.cableDao = cableDao;
    this.cableTypeDao = cableTypeDao;
    this.cableFloorDao = cableFloorDao;
  }

  public List<CableEntity> findAll(String typeId, List<Integer> scope) {
    if (StringUtils.isEmpty(typeId) && null == scope) {
      return cableDao.findAll().list();
    }
    if (null != scope && !StringUtils.isEmpty(typeId)) {
      return cableDao.find("typeId = ?1 and scope IN (?2)", typeId, scope).list();
    }
    if (null != scope && StringUtils.isEmpty(typeId)) {
      return cableDao.find("scope IN (?1)", scope).list();
    }
    return cableDao.find("typeId", typeId).list();
  }

  public CableEntity findById(String id) {
    return cableDao.findById(id);
  }

  @Transactional
  public CableEntity save(CableVO vo) {
    CableEntity entity = PojoUtils.convertOne(vo, CableEntity.class);
    CableTypeEntity byId = cableTypeDao.findById(entity.getTypeId());
    entity.setTypeTitle(byId.getTitle());
    CableEntity save = cableDao.save(entity);
    cableFloorDao.deleteByCableId(save.getId());
    List<List<List<String>>> floors = vo.getFloors();
    int groupCount = 0;
    int codeCount = 0;
    for (int i = 0; i < floors.size(); i++) {
      for (int j = 0; j < floors.get(i).size(); j++) {
        groupCount++;
        codeCount += floors.get(i).get(j).size();
        CableFloorEntity floor = new CableFloorEntity();
        floor.setCableId(save.getId());
        floor.setTypeId(save.getTypeId());
        floor.setFloorNumber(i);
        floor.setSort(j);
        floor.setMoresKey(JSONUtils.toJson(floors.get(i).get(j)));
        cableFloorDao.save(floor);
      }
    }
    entity.setFloorCount(floors.size());
    entity.setGroupCount(groupCount);
    entity.setCodeCount(codeCount);
    save = cableDao.saveAndFlush(entity);
    return save;
  }

  @Transactional
  public Boolean delete(String id) {
    try {
      cableFloorDao.deleteByCableId(id);
      return cableDao.deleteById(id);
    } catch (RuntimeException e) {
      log.error("删除报文失败", e);
      return false;
    }
  }
}
