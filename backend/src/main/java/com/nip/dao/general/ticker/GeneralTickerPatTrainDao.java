package com.nip.dao.general.ticker;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.GeneralKeyPatTrainScoreDto;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class GeneralTickerPatTrainDao extends BaseRepository<GeneralTickerPatTrainEntity, Integer> {

  public String findByUserLastScore(String userId, String year) {
    List<BigDecimal> resultList = entityManager.createQuery("select tu.score " +
            "from general_ticker_pat_train_user tu " +
            "LEFT JOIN general_ticker_pat p on tu.trainId = p.id " +
            "WHERE p.createTime LIKE concat(?2,'%') and tu.userId = ?1 and tu.isFinish =1 and p.trainType =1 " +
            "ORDER BY tu.createTime desc limit 1", BigDecimal.class)
        .setParameter(1, userId)
        .setParameter(2, year)
        .getResultList();
    if (resultList == null || resultList.isEmpty()) {
      return "";
    } else {
      BigDecimal firstElement = resultList.getFirst();
      return firstElement != null ? firstElement.toString() : "";
    }
  }

  public String countByUserTrainTime(String id, String year) {
    List<Long> resultList = entityManager.createQuery("select sum(p.validTime) " +
            "from general_ticker_pat_train_user tu " +
            "LEFT JOIN general_ticker_pat p on p.id =tu.trainId " +
            "where p.trainType = 0 and p.createTime like concat(?2,'%') " +
            "and tu.userId = ?1 and tu.isFinish =1", Long.class)
        .setParameter(1, id)
        .setParameter(2, year)
        .getResultList();
    if (resultList == null || resultList.isEmpty()) {
      return "";
    } else {
      Long firstElement = resultList.getFirst();
      return firstElement != null ? firstElement.toString() : "";
    }
  }

  public List<String> countByUserTrainYearScore(String id, String year) {
    return entityManager.createQuery("SELECT tu.score " +
            "from general_ticker_pat_train_user tu " +
            "LEFT JOIN general_ticker_pat p on p.id = tu.trainId " +
            "where p.createTime like concat(?2,'%') and  tu.userId =?1 " +
            "and p.trainType = 0 and tu.isFinish =1 and p.status =2", String.class)
        .setParameter(1, id)
        .setParameter(2, year)
        .getResultList();
  }

  public List<GeneralKeyPatTrainScoreDto> countClassResultRage(List<String> uidList, String startTime, String endTime) {
    return entityManager.createNamedQuery("find_general_ticker_pat_train_score_dto", GeneralKeyPatTrainScoreDto.class)
        .setParameter(1, uidList)
        .setParameter(2, startTime)
        .setParameter(3, endTime)
        .getResultList();
  }
}
