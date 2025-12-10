package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostTelegraphKeyPatTrainPageValueEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TRAIN_ID;

/**
 * @Author: wushilin
 * @Data: 2023-03-16 11:42
 * @Description:
 */
@ApplicationScoped
public class PostTelegraphKeyPatTrainPageValueDao extends BaseRepository<PostTelegraphKeyPatTrainPageValueEntity, String> {

  @Transactional
  public void deleteByTrainIdAndPageNumber(String trainId, Integer pageNumber) {
    delete("trainId = ?1 and pageNumber = ?2", trainId, pageNumber);
    flush();
  }
  @Transactional
  public void deleteByTrainId(String trainId) {
    delete("trainId = ?1", trainId);
    flush();
  }


  public List<PostTelegraphKeyPatTrainPageValueEntity> findByTrainIdAndPageNumberOrderBySort(String trainId,
                                                                                             Integer pageNumber) {
    return find("trainId = ?1 and pageNumber = ?2 order by sort", trainId, pageNumber).list();
  }


  public List<PostTelegraphKeyPatTrainPageValueEntity> findByTrainIdOrderByPageNumberAscSortAsc(String trainId) {
    return find("trainId = ?1 order by pageNumber asc,sort asc", trainId).list();
  }

  public List<Integer> findAllByTrainIdToPageNumber(String trainId) {
    return entityManager.createQuery(
      "select pageNumber from t_post_telegraph_key_pat_train_page_value where trainId =:trainId group by pageNumber ORDER BY pageNumber",
      Integer.class
    ).setParameter(TRAIN_ID, trainId).getResultList();
  }

  public List<PostTelegraphKeyPatTrainPageValueEntity> findTwoPage(String trainId) {
    return find("trainId =?1 and (pageNumber = 1 or pageNumber = 2) order by pageNumber,sort", trainId).list();
  }
}
