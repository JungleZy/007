package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostTelegraphKeyPatTrainPageEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TRAIN_ID;

/**
 * PostTelegraphKeyPatTrainPageDao
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-26 14:18:02
 */
@ApplicationScoped
public class PostTelegraphKeyPatTrainPageDao extends BaseRepository<PostTelegraphKeyPatTrainPageEntity, String> {

  public List<Integer> countPageNumber(String trainId) {
    return entityManager.createQuery(
        "select pageNumber from t_post_telegraph_key_pat_train_page where trainId =:trainId group by pageNumber",
        Integer.class
    ).setParameter(TRAIN_ID, trainId).getResultList();
  }

  public Integer findMaxPageNumber(String trainId) {
    return entityManager.createQuery(
        "select max(pageNumber) from t_post_telegraph_key_pat_train_page where trainId =:trainId",
        Integer.class
    ).setParameter(TRAIN_ID, trainId).setMaxResults(1).getSingleResult();
  }
  public List<PostTelegraphKeyPatTrainPageEntity> findTwoPage(String trainId) {
    return find("trainId =?1 and (pageNumber = 1 or pageNumber = 2) order by pageNumber,sort", trainId).list();
  }

  public List<PostTelegraphKeyPatTrainPageEntity> findByTrainIdAndPageNumberOrderBySort(String trainId,
                                                                                        Integer pageNumber) {
    return find("trainId = ?1 and pageNumber = ?2 order by sort", trainId, pageNumber).list();
  }
  public List<PostTelegraphKeyPatTrainPageEntity> findByTrainIdAndPageNumberOrderBySort(String trainId,
                                                                                        List<Integer> pageNumber) {
    return find("trainId = ?1 and pageNumber in (?2) order by sort", trainId, pageNumber).list();
  }
}
