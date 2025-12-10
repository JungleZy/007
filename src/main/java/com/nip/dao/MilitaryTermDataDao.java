package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.MilitaryTermDataEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class MilitaryTermDataDao extends BaseRepository<MilitaryTermDataEntity, String> {

  public List<MilitaryTermDataEntity> findAllByParentId(String parentId) {
    return find("parentId", parentId).list();
  }

  public List<MilitaryTermDataEntity> findAllByParentIdOrderBySort(String parentId) {
    return find("parentId = ?1 order by sort", parentId).list();
  }

  public List<MilitaryTermDataEntity> findAllByParentIdIn(List<String> ids) {
    return find("parentId in (?1)", ids).list();
  }

  public List<MilitaryTermDataEntity> findAllByIdIn(List<String> ids) {
    return find("id in (?1)", ids).list();
  }

  public Integer findByParentIdMaxSort(String parentId) {
    return entityManager.createQuery("select max(sort) from t_military_term_data where parentId =:parentId",
                                     Integer.class
    ).setParameter("parentId", parentId).setMaxResults(1).getSingleResult();
  }

  @Transactional
  public void updateSort(String parentId, Integer sort) {
    update("sort = sort + 1 where parentId = ?1 and sort>=?2", parentId, sort);
  }

  @Transactional
  public void sortSubtract(String parentId, Integer sort) {
    update("sort = sort + 1 where parentId = ?1 and sort>=?2", parentId, sort);
  }

  @Transactional
  public void downSwapUp(Integer source, Integer target, String parentId) {
    update("sort = sort + 1 where parentId = ?3 and sort >= ?2 and sort < ?1", source, target, parentId);
  }

  @Transactional
  public void upSwapDown(Integer source, Integer target, String parentId) {
    update("sort = sort - 1 where parentId = ?3 and sort > ?1 and sort <= ?2", source, target, parentId);
  }

  public MilitaryTermDataEntity findByParentIdAndKey(String parentId, String key) {
    return find("parentId=?1 and key=?2", parentId, key).firstResult();
  }
  public MilitaryTermDataEntity findByValue(String value){
    return find("value=?1", value).firstResult();
  }
}
