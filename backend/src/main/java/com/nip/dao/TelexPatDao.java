package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TelexPatEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TYPE;
import static com.nip.common.constants.BaseConstants.USER_ID;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/21 17:47
 */
@ApplicationScoped
public class TelexPatDao extends BaseRepository<TelexPatEntity, String> {

  public TelexPatEntity findAllByUserIdAndType(String userId, int type) {
    return find("userId = ?1 and type = ?2", userId, type).firstResult();
  }

  @Transactional
  public void deleteByUserIdAndType(String userId, Integer type) {
    delete("userId = ?1 and type = ?2", userId, type);
  }

  public Long sumDurationByUserId(String userId, Integer type) {
    List<Long> resultList = entityManager.createQuery(
            "select SUM(duration) FROM t_telex_pat where userId= :userId and type = :type", Long.class)
        .setParameter(USER_ID, userId).setParameter(TYPE, type).getResultList();
    if (resultList == null || resultList.isEmpty()) {
      return 0L;
    } else {
      Long firstElement = resultList.getFirst();
      return firstElement != null ? firstElement : 0L;
    }
  }
}
