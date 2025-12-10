package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostTelexPatTrainPageEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

import static com.nip.common.constants.BaseConstants.ID;
import static com.nip.common.constants.BaseConstants.TRAIN_ID;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 11:13
 * @Description:
 */
@ApplicationScoped
public class PostTelexPatTrainPageDao extends BaseRepository<PostTelexPatTrainPageEntity, String> {

  public List<PostTelexPatTrainPageEntity> findByTrainIdAndPageNumberOrderBySort(String trainId, Integer pageNumber) {
    return entityManager.createQuery("from t_post_telex_pat_train_page where trainId = :trainId and pageNumber = :pageNumber ORDER BY sort", PostTelexPatTrainPageEntity.class)
        .setParameter(TRAIN_ID, trainId)
        .setParameter("pageNumber", pageNumber).getResultList();
  }

  public List<PostTelexPatTrainPageEntity> findByTrainIdOrderBySort(String trainId) {
    return entityManager.createQuery("from t_post_telex_pat_train_page where trainId = :trainId ORDER BY pageNumber,sort", PostTelexPatTrainPageEntity.class)
        .setParameter(TRAIN_ID, trainId).getResultList();
  }

  public List<PostTelexPatTrainPageEntity> findByTrainIdTop2(String id) {
    return entityManager.createQuery(
            "select m from t_post_telex_pat_train_page m where m.trainId =:id and m.pageNumber < 3 order by pageNumber,sort", PostTelexPatTrainPageEntity.class)
        .setParameter(ID, id).getResultList();
  }

  public List<Integer> countPageNumber(String id) {
    return entityManager.createQuery(
            "SELECT pageNumber from t_post_telex_pat_train_page where trainId = :id GROUP BY pageNumber order by pageNumber", Integer.class)
        .setParameter(ID, id).getResultList();
  }

  public Integer findMaxPageNumber(String trainId) {
    return entityManager.createQuery(
        "select max(pageNumber) from t_post_telex_pat_train_page where trainId =:trainId",
        Integer.class
    ).setParameter(TRAIN_ID, trainId).setMaxResults(1).getSingleResult();
  }

  @Transactional
  public void deleteByTrainId(String trainId) {
    entityManager.createQuery("delete from t_post_telex_pat_train_page where trainId = :trainId")
        .setParameter(TRAIN_ID, trainId).executeUpdate();
  }
}
