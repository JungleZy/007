package com.nip.dao;

import com.google.gson.reflect.TypeToken;
import com.nip.common.repository.BaseRepository;
import com.nip.common.utils.JSONUtils;
import com.nip.dto.sql.TickerTapeTrainDaoCountBaseTrain;
import com.nip.entity.TickerTapeTrainEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.TYPE;
import static com.nip.common.constants.BaseConstants.USER_ID;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 15:15
 * @Description:
 */
@ApplicationScoped
public class TickerTapeTrainDao extends BaseRepository<TickerTapeTrainEntity, String> {
  /**
   * 开始训练
   *
   * @param id
   */
  @Transactional
  public void begin(
      String id) {
    update("startTime = now(),`status` = 1  where id = ?1", id);
  }

  /**
   * 暂停时训练
   *
   * @param id
   * @param validTime
   */
  @Transactional
  public void pause(
      String id, String validTime, String mark, Integer schedule) {
    update("`status` = 2,validTime = ?2 ,mark=?3 ,schedule=?4  where id = ?1 ", id, validTime, mark, schedule);
  }

  /**
   * 继续训练
   *
   * @param id
   */
  @Transactional
  public void goOn(
      String id) {
    update("`status` = 1  where id = ?1", id);
  }

  @Transactional
  public void finish(
      String id, String validTime, String mark, Integer schedule) {
    update("`status` = 3,validTime = ?2 ,mark=?3 ,endTime = now(),schedule=?4  where id = ?1", id, validTime, mark,
        schedule
    );
  }

  /**
   * 统计科式或基础训练
   *
   * @param userId
   * @param type
   * @return
   */
  public Map<String, Object> countBaseTrain(String userId, Integer type) {
    TickerTapeTrainDaoCountBaseTrain countBaseTrain = entityManager.createNamedQuery(
            "count_base_train", TickerTapeTrainDaoCountBaseTrain.class).setParameter(USER_ID, userId)
        .setParameter(TYPE, type).setMaxResults(1).getSingleResult();
    return JSONUtils.fromJson(JSONUtils.toJson(countBaseTrain), new TypeToken<>() {
    });
  }

  /**
   * 查统计单词训练
   *
   * @param userId
   * @return
   */
  public TickerTapeTrainDaoCountBaseTrain countLetterTrain(String userId) {
    return entityManager.createNamedQuery(
            "count_letter_train", TickerTapeTrainDaoCountBaseTrain.class).setParameter(1, userId).getResultList()
        .stream()
        .findFirst()
        .orElseGet(() -> {
          TickerTapeTrainDaoCountBaseTrain train = new TickerTapeTrainDaoCountBaseTrain();
          train.setTotalCount(0);
          train.setTotalTime(0);
          train.setAvgSpeed(BigDecimal.ZERO);
          return train;
        });
  }

  /**
   * 查询最后一次训练记录
   *
   * @param id
   * @param type
   * @return
   */
  public TickerTapeTrainEntity lastTrain(String id, Integer type) {
    return find("userId = ?1 and type = ?2", Sort.by("createTime").ascending(), id, type).firstResult();
  }
}
