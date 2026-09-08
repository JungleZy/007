package com.nip.dao.general.key;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.simulation.key.GeneralKeyPatUserValueResolverEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class GeneralKeyPatUserValueResolverDao extends BaseRepository<GeneralKeyPatUserValueResolverEntity, Integer> {
  public List<GeneralKeyPatUserValueResolverEntity> findTwoPage(Integer trainId, String userId) {
    return find("trainId =?1 and userId=?2 and (pageNumber = 1 or pageNumber =2) order by pageNumber, sort", trainId, userId).list();
  }

  public List<GeneralKeyPatUserValueResolverEntity> findByTrainIdAndPageNumberAndUserIdOrderBySort(Integer trainId, Integer pageNumber, String userId) {
    return find("trainId = ?1 and pageNumber = ?2 and userId = ?3 order by sort asc", trainId, pageNumber, userId).list();
  }

  @Transactional
  public void deleteByTrainIdAndUserId(Integer trainId, String userId) {
    delete("trainId = ?1 and userId = ?2", trainId, userId);
    flush();
  }
}
