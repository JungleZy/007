package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TelegraphKeyPatSyntheticalEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

import static com.nip.common.utils.ToolUtil.assembleData;

/**
 * @Author: wushilin
 * @Data: 2022-06-09 09:59
 * @Description:
 */
@ApplicationScoped
public class TelegraphKeyPatSyntheticalDao extends BaseRepository<TelegraphKeyPatSyntheticalEntity, String> {

  public List<TelegraphKeyPatSyntheticalEntity> findAllByCreateUserIdOrderByCreateTimeDesc(String userId) {
    return find("createUserId = ?1 order by createTime desc", userId).list();
  }

  public Map<String, Object> finishStatistical(String userId) {
    List<Object[]> resultList = entityManager.createNativeQuery(
      "SELECT IFNULL(sum(duration),0) totalTime, count(id) totalCount ,IFNULL(AVG(speed),0) avgSpeed "
        + "from t_telegraph_key_pat_synthetical_train where create_user_id = :createUserId and `status` = 3 ",
      Object[].class
    ).setParameter("createUserId", userId).getResultList();

    return assembleData(resultList);
  }

  public TelegraphKeyPatSyntheticalEntity findLastTrain(String id) {
    return find("createUserId = ?1 order by createTime desc", id).firstResult();
  }
}
