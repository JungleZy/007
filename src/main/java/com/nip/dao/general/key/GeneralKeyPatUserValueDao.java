package com.nip.dao.general.key;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.simulation.key.GeneralKeyPatUserValueEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class GeneralKeyPatUserValueDao extends BaseRepository<GeneralKeyPatUserValueEntity, Integer> {
  public List<GeneralKeyPatUserValueEntity> findByPageNumberAndTrainIdAndUserId(Integer pageNumber, Integer trainId, String userId) {
    return find(" trainId = ?1 and pageNumber = ?2 and userId = ?3", trainId, pageNumber, userId).list();
  }

  public long countByTrainIdAndUserId(Integer trainId, String userId) {
    return count(" trainId = ?1 and userId = ?2", trainId, userId);
  }

  public List<Integer> countByTrainIdAndUserIdGroupByPageNumber(Integer trainId, String userId) {
    return find("trainId =?1 and userId=?2 group by page_number", trainId, userId).list()
        .stream().map(GeneralKeyPatUserValueEntity::getPageNumber).toList();
  }

  @Transactional
  public void deleteByTrainIdAndPageNumberAndUserId(Integer trainId, Integer pageNumber, String userId) {
    delete("trainId =?1 and pageNumber=?2 and userId=?3", trainId, pageNumber, userId);
  }

  @Transactional
  public void deleteByUserIdAndTrainId(String userId, Integer trainId) {
    delete("userId =?1 and trainId=?2", userId, trainId);
  }

  public List<Integer> findPageNumberByTrainIdAndUserId(Integer trainId, String userId) {
    return entityManager.createQuery("SELECT distinct pageNumber from general_key_pat_user_value " +
            "where trainId = ?1 and userId = ?2 order by pageNumber", Integer.class)
        .setParameter(1, trainId)
        .setParameter(2, userId).getResultList();
  }

  public List<GeneralKeyPatUserValueEntity> findByTrainIdAndPageNumberAndUserIdOrderBySort(Integer trainId, Integer pageNumber, String userId) {
    return find("trainId = ?1 and pageNumber = ?2 and userId = ?3 order by sort asc", trainId, pageNumber, userId).list();
  }

  public List<GeneralKeyPatUserValueEntity> findByTrainIdAndUserIdOrderByPageNumberAscSortAsc(Integer trainId, String userId) {
    return find("trainId = ?1 and userId = ?2 order by pageNumber asc, sort asc", trainId, userId).list();
  }

  public List<GeneralKeyPatUserValueEntity> findTwoPage(Integer id, String userId) {
    return find("trainId = ?1 and userId = ?2 and (pageNumber = 1 or pageNumber = 2) order by pageNumber, sort", id, userId).list();
  }

  public List<GeneralKeyPatUserValueEntity> findByTrainId(Integer trainId) {
    return find("trainId = ?1", trainId).list();
  }
  @Transactional
  public void deleteByTrainIdAndUserId(Integer trainId, String userId) {
    delete("trainId = ?1 and userId = ?2", trainId, userId);
    flush();
  }
}
