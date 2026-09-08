package com.nip.dao.general.key;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.simulation.key.GeneralKeyPatTrainMoreEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TRAIN_ID;
import static com.nip.common.constants.BaseConstants.USER_ID;

@ApplicationScoped
public class GeneralKeyPatTrainMoreDao extends BaseRepository<GeneralKeyPatTrainMoreEntity, Integer> {

  public List<GeneralKeyPatTrainMoreEntity> getTop2ByTrainIdAndUserId(Integer trainId, String userId) {
    return entityManager.createQuery("from general_key_pat_train_more where trainId = :trainId and userId = :userId and (pageNumber = 1 or pageNumber =2) ORDER BY pageNumber", GeneralKeyPatTrainMoreEntity.class)
        .setParameter(TRAIN_ID, trainId)
        .setParameter(USER_ID, userId).getResultList();
  }

  public GeneralKeyPatTrainMoreEntity findByTrainIdAndPageNumberAndUserId(Integer trainId, Integer pageNumber, String userId) {
    return find("trainId = ?1 and pageNumber = ?2 and userId = ?3", trainId, pageNumber, userId).firstResult();
  }

  public List<GeneralKeyPatTrainMoreEntity> findByTrainId(Integer trainId) {
    return find("trainId = ?1", trainId).list();
  }
}
