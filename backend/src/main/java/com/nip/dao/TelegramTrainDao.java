package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TelegramTrainEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

import static com.nip.common.utils.ToolUtil.assembleData;

/**
 * TelegramTrainDao
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-20 11:15
 */
@ApplicationScoped
public class TelegramTrainDao extends BaseRepository<TelegramTrainEntity, String> {

  public List<TelegramTrainEntity> findAllByCreateUserIdOrderByCreateTimeDesc(String userId) {
    return find("createUserId = ?1 order by createTime desc", userId).list();
  }

  public Map<String, Object> findWordTrain(String createUserId) {
    List<Object[]> resultList = entityManager.createNativeQuery(
        "SELECT IFNULL(sum(sustain_time),0) totalTime,count(id) totalCount, IFNULL(avg(speed),0) avgSpeed "
            + "from t_telegram_train where create_user_id = :createUserId and type in(0,1,2) and `status` = 3 ",
        Object[].class
    ).setParameter("createUserId", createUserId).getResultList();

    return assembleData(resultList);
  }

  public Map<String, Object> findGroupTrain(String createUserId) {
    List<Object[]> resultList = entityManager.createNativeQuery(
        "SELECT IFNULL(sum(sustain_time),0) totalTime,count(id) totalCount, IFNULL(avg(speed),0) avgSpeed "
            + "from t_telegram_train where create_user_id = :createUserId and type in(11,12,13,14) and `status` = 3 ",
        Object[].class
    ).setParameter("createUserId", createUserId).getResultList();

    return assembleData(resultList);
  }

  public Map<String, Object> findBaseTrain(String createUserId) {
    List<Object[]> resultList = entityManager.createNativeQuery(
            "SELECT IFNULL(sum(sustain_time),0) totalTime,count(id) totalCount, IFNULL(avg(speed),0) avgSpeed "
                + "from t_telegram_train where create_user_id = :createUserId and type = 21 and `status` = 3 ", Object[].class)
        .setParameter("createUserId", createUserId).getResultList();

    return assembleData(resultList);
  }

  public TelegramTrainEntity lastTrain(String userId, Integer type) {
    return find("createUserId = ?1 and type = ?2 order by createTime desc", userId, type).firstResult();
  }
}
