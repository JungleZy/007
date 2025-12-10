package com.nip.dao.general.telex;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.simulation.telex.GeneralTelexPatUserValueEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class GeneralTelexPatUserValueDao extends BaseRepository<GeneralTelexPatUserValueEntity, String> {
  public List<GeneralTelexPatUserValueEntity> findByTrainIdAndUserIdOrderByPageNumberAscSortAsc(String trainId, String userId) {
    return find("trainId = ?1 and userId = ?2 order by pageNumber asc, sort asc", trainId, userId).list();
  }
  public List<GeneralTelexPatUserValueEntity> findTwoPage(String id, String userId) {
    return find("trainId = ?1 and userId = ?2 and (pageNumber = 1 or pageNumber = 2) order by pageNumber, sort", id, userId).list();
  }
  @Transactional
  public void deleteByTrainIdAndPageNumberAndUserId(String trainId, Integer pageNumber, String userId) {
    delete("trainId =?1 and pageNumber=?2 and userId=?3", trainId, pageNumber, userId);
  }
  public List<GeneralTelexPatUserValueEntity> findByTrainIdAndPageNumberAndUserIdOrderBySort(String trainId, Integer pageNumber, String userId) {
    return find("trainId = ?1 and pageNumber = ?2 and userId = ?3 order by sort asc", trainId, pageNumber, userId).list();
  }

  public List<GeneralTelexPatUserValueEntity> findByTrainIdAndPageNumberAndUserIdAndSortOrderBySort(String trainId, Integer pageNumber, String userId) {
    return find("trainId = ?1 and pageNumber = ?2 and userId = ?3 and sort=-1 order by sort asc", trainId, pageNumber, userId).list();
  }

  public List<GeneralTelexPatUserValueEntity> findByPageNumberAndTrainIdAndUserId(Integer pageNumber, String trainId, String userId) {
    return find(" trainId = ?1 and pageNumber = ?2 and userId = ?3", trainId, pageNumber, userId).list();
  }
  @Transactional
  public void deleteByTrainIdAndUserId(String trainId, String userId) {
    delete("trainId = ?1 and userId = ?2", trainId, userId);
    flush();
  }
}
