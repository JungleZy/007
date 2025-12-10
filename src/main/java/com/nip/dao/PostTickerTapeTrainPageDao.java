package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostTickerTapeTrainPageEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TRAIN_ID;

/**
 * @Author: wushilin
 * @Data: 2023-03-20 10:44
 * @Description:
 */
@ApplicationScoped
public class PostTickerTapeTrainPageDao extends BaseRepository<PostTickerTapeTrainPageEntity, String> {

  public List<PostTickerTapeTrainPageEntity> findByTrainIdAndPageNumberOrderBySort(String trainId, Integer pageNumber) {
    return find("trainId = ?1 and pageNumber = ?2", trainId, pageNumber).list();
  }

  public Integer findMaxPageNumber(String trainId) {
    return entityManager.createQuery(
        "select max(pageNumber) from t_post_ticker_tape_train_page where trainId =:trainId",
        Integer.class
    ).setParameter(TRAIN_ID, trainId).setMaxResults(1).getSingleResult();
  }
}
