package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.EnteringExerciseEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.TYPE;
import static com.nip.common.utils.ToolUtil.assembleData;

@ApplicationScoped
public class EnteringExerciseDao extends BaseRepository<EnteringExerciseEntity, String> {

  /**
   * 根据用户id和type统计
   *
   * @param: createUserId
   * @param: type
   */
  public Map<String, Object> finishStatistical(String createUserId, Integer type) {
    List<Object[]> resultList = entityManager.createNativeQuery(
        "SELECT IFNULL(sum(duration),0) totalTime , IFNULL(count(id),0) totalCount , IFNULL(AVG(speed),0.00) avgSpeed " +
            "from t_entering_exercise " + "where create_user_id = :createUserId and type = :type and `status` = 2",
        Object[].class
    ).setParameter("createUserId", createUserId).setParameter(TYPE, type).getResultList();

    return assembleData(resultList);
  }

  public EnteringExerciseEntity lastTrain(String id, Integer type) {
    return find("createUserId = ?1 and type =?2 ORDER BY createTime desc", id, type).firstResult();
  }
}
