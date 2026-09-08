package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostTelegramTrainContentFloorValueEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TRAIN_ID;

@ApplicationScoped
public class PostTelegramTrainContentValueDao extends BaseRepository<PostTelegramTrainContentFloorValueEntity, String> {

  /**
   * 清除本次训练的内容
   *
   * @param: id
   */
  @Transactional
  public void deleteByTrainId(String id) {
    delete(TRAIN_ID, id);
  }

  /**
   * 根据训练id 和页码删除训练
   *
   * @param: trainId 训练id
   * @param: floorNumber 页码
   */
  @Transactional
  public void deleteByTrainIdAndFloorNumber(String trainId, Integer floorNumber) {
    delete("trainId = ?1 and floorNumber=?2", trainId, floorNumber);
  }

  /**
   * 根据训练id和保底号得到报文内容
   *
   * @param: floorNumber
   * @param: id
   */
  public PostTelegramTrainContentFloorValueEntity findByFloorNumberAndTrainId(Integer floorNumber, String id) {
    return find("floorNumber = ?1 and trainId = ?2", floorNumber, id).firstResult();
  }

  /**
   * 根据id查询所有的手键拍发内容
   *
   * @param: id
   */
  public List<PostTelegramTrainContentFloorValueEntity> findAllByTrainIdOrderByFloorNumber(String id) {
    return find("trainId = ?1 order by floorNumber", id).list();
  }

  public List<Integer> countExistFloorNumber(String trainId) {
    return entityManager.createQuery(
      "select floorNumber from t_post_telegram_train_floor_content_value where trainId =:trainId group by floorNumber",
      Integer.class
    ).setParameter(TRAIN_ID, trainId).getResultList();
  }
}
