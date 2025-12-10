package com.nip.dao.general.ticker;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainPageEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class GeneralTickerPatTrainPageDao extends BaseRepository<GeneralTickerPatTrainPageEntity, Integer> {

  public List<GeneralTickerPatTrainPageEntity> findByFloorNumberAndTrainIdOrderBySort(Integer floorNumber, Integer trainId) {
    return find("floorNumber = ?1 and trainId = ?2 order by sort", floorNumber, trainId).list();
  }

  public GeneralTickerPatTrainPageEntity findByTrainIdOrderByFloorNumberDescSortDesc(Integer id) {
    return find("trainId = ?1 order by floorNumber desc limit 1", id).firstResult();
  }

  public List<GeneralTickerPatTrainPageEntity> findByTrainIdOrderByFloorNumberSort(Integer id) {
    return find("trainId = ?1 order by floorNumber,sort", id).list();
  }

  public List<Integer> findByTrainIdCountFloor(Integer id) {
    return find("trainId = ?1 group by floorNumber", id).list()
        .stream().map(GeneralTickerPatTrainPageEntity::getFloorNumber).toList();
  }
  public Integer findMaxPageNumber(Integer trainId) {
    return entityManager.createQuery(
        "select max(floorNumber) from general_ticker_pat_train_page where trainId =:trainId",
        Integer.class
    ).setParameter("trainId", trainId).setMaxResults(1).getSingleResult();
  }
}
