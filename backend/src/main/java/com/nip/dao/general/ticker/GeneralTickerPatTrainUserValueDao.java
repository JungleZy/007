package com.nip.dao.general.ticker;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainUserValueEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class GeneralTickerPatTrainUserValueDao extends BaseRepository<GeneralTickerPatTrainUserValueEntity, Integer> {

  public GeneralTickerPatTrainUserValueEntity findByFloorNumberAndTrainIdAndUserId(Integer floorNumber, Integer id, String userId) {
    return find("floorNumber =?1 and trainId=?2 and userId=?3", floorNumber, id, userId).firstResult();
  }

  public Integer countByTrainIdAndUserId(Integer trainId, String userId) {
    List<Long> resultList = entityManager.createQuery("select count(id) " +
            "from general_ticker_pat_train_user_value " +
            "where trainId =?1 and userId=?2", Long.class)
        .setParameter(1, trainId)
        .setParameter(2, userId)
        .getResultList();
    if (resultList == null || resultList.isEmpty()) {
      return 0;
    } else {
      Long firstElement = resultList.getFirst();
      return firstElement != null ? firstElement.intValue() : 0;
    }
  }

  public List<Integer> countByTrainIdAndUserIdGroupByPageNumber(Integer trainId, String userId) {
    return entityManager.createQuery("select floorNumber " +
            "from general_ticker_pat_train_user_value " +
            "where trainId =?1 and userId=?2 group by floorNumber", Integer.class)
        .setParameter(1, trainId)
        .setParameter(2, userId)
        .getResultList();
  }

  @Transactional
  public void deleteByTrainIdAndFloorNumberAndUserId(Integer trainId, Integer floorNumber, String userId) {
    delete("trainId =?1 and floorNumber=?2 and userId=?3", trainId, floorNumber, userId);
  }

  @Transactional
  public void deleteByUserIdAndTrainId(String userId, Integer trainId) {
    delete("userId =?1 and trainId=?2", userId, trainId);
  }
}
