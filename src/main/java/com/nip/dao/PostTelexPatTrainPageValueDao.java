package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostTelexPatTrainPageValueEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.ID;
import static com.nip.common.constants.BaseConstants.TRAIN_ID;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 11:13
 * @Description:
 */
@ApplicationScoped
public class PostTelexPatTrainPageValueDao extends BaseRepository<PostTelexPatTrainPageValueEntity, String> {

  public PostTelexPatTrainPageValueEntity findByTrainIdAndPageNumber(String trainId, Integer pageNumber) {
    List<PostTelexPatTrainPageValueEntity> resultList = entityManager.createQuery("from t_post_telex_pat_train_page_value where trainId = :trainId and pageNumber = :pageNumber", PostTelexPatTrainPageValueEntity.class)
        .setParameter(TRAIN_ID, trainId)
        .setParameter("pageNumber", pageNumber).getResultList();
    return resultList.isEmpty() ? null : resultList.getFirst();
  }
  public PostTelexPatTrainPageValueEntity findByTrainId(String trainId) {
    List<PostTelexPatTrainPageValueEntity> resultList = entityManager.createQuery("from t_post_telex_pat_train_page_value where trainId = :trainId order by pageNumber", PostTelexPatTrainPageValueEntity.class)
        .setParameter(TRAIN_ID, trainId).getResultList();
    return resultList.isEmpty() ? null : resultList.getFirst();
  }
  public List<PostTelexPatTrainPageValueEntity> findAllByTrainId(String trainId) {
    return entityManager.createQuery("from t_post_telex_pat_train_page_value where trainId = :trainId order by pageNumber", PostTelexPatTrainPageValueEntity.class)
        .setParameter(TRAIN_ID, trainId).getResultList();
  }

  public List<PostTelexPatTrainPageValueEntity> findByTrainIdTop2(String id) {
    return entityManager.createQuery(
            "select m from t_post_telex_pat_train_page_value m where m.trainId =:id and (m.pageNumber=1 or m.pageNumber =2)", PostTelexPatTrainPageValueEntity.class)
        .setParameter(ID, id).getResultList();
  }

  public List<Integer> countPageNumber(String id) {
    return entityManager.createQuery(
            "SELECT pageNumber from t_post_telex_pat_train_page_value where trainId = :id GROUP BY pageNumber", Integer.class)
        .setParameter(ID, id).getResultList();
  }
}
