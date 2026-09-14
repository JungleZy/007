package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TelexPatTrainEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static com.nip.common.constants.BaseConstants.TYPE;
import static com.nip.common.constants.BaseConstants.USER_ID;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/29 9:50
 */
@ApplicationScoped
public class TelexPatTrainDao extends BaseRepository<TelexPatTrainEntity, String> {
  /** Acquire the train row lock before validating a lifecycle mutation. */
  @Transactional
  public TelexPatTrainEntity findForUpdate(String id) {
    return entityManager.createQuery("from t_telex_pat_train where id = :id", TelexPatTrainEntity.class)
        .setParameter("id", id)
        .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        .getResultStream().findFirst().orElse(null);
  }

  public List<TelexPatTrainEntity> findAllByCreateUserId(String id) {
    return find("createUserId", id).list();
  }

  public List<TelexPatTrainEntity> findAllByCreateUserIdAndTypeAndStatus(String userId, Integer type, Integer status) {
    return find("createUserId = ?1 and type = ?2 and status = ?3", userId, type, status).list();
  }

  public BigDecimal getAvgSpeed(String userId, Integer type) {
    Object singleResult = entityManager.createNativeQuery(
      "select ifnull(AVG(speed),0) FROM t_telex_pat_train where create_user_id= :userId and type = :type and `status`=3",
      String.class
    ).setParameter(USER_ID, userId).setParameter(TYPE, type).setMaxResults(1).getSingleResult();
    return new BigDecimal((String) singleResult);
  }


  public TelexPatTrainEntity lastTration(String id, Integer type) {
    return find("createUserId = ?1 and type = ?2 order by createTime desc", id, type).firstResult();
  }

  @Transactional
  public void updateStatus(String id, Integer status) {
    update("status = ?2 where id = ?1", id, status);
  }
}
