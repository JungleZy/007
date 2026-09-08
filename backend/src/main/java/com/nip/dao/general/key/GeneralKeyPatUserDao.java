package com.nip.dao.general.key;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.GeneralKeyPatUserDto;
import com.nip.entity.simulation.key.GeneralKeyPatUserEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class GeneralKeyPatUserDao extends BaseRepository<GeneralKeyPatUserEntity, Integer> {

  public List<GeneralKeyPatUserEntity> findByUserId(String userId) {
    return find("userId =?1", userId).list();
  }

  public List<GeneralKeyPatUserDto> findByTrainIdToMap(Integer id) {
    return entityManager.createNamedQuery("find_general_key_pat_user_dto", GeneralKeyPatUserDto.class)
        .setParameter(1, id)
        .getResultList();
  }

  public List<GeneralKeyPatUserEntity> findByTrainIdAndRole(Integer trainId, Integer role) {
    return find("trainId =?1 and role =?2", trainId, role).list();
  }

  public GeneralKeyPatUserEntity findByUserIdAndTrainId(String userId, Integer trainId) {
    return find("userId =?1 and trainId =?2", userId, trainId).firstResult();
  }
  public List<GeneralKeyPatUserEntity> findRoleAdminByUserId(Integer trainId) {
    return find("trainId =?1 and role= 1", trainId).list();
  }

  /**
   * 查询上一次训练记录
   *
   * @param userId     用户id
   * @param createTime 创建时间
   * @return entity
   */
  public GeneralKeyPatUserEntity findFirstByUserIdAndFinishTimeBeforeOrderByFinishTimeDesc(String userId, LocalDateTime createTime) {
    return find("userId =?1 and finishTime < ?2 ORDER BY finishTime DESC", userId, createTime).firstResult();
  }

  public List<BigDecimal> findByFistTwoScore(String uid, LocalDateTime createTime) {
    return find("userId =?1 and createTime < ?2 ORDER BY finishTime DESC LIMIT 2", uid, createTime).list()
        .stream().map(GeneralKeyPatUserEntity::getScore).toList();
  }

  public List<String> findTrainUserIdsByTrainId(Integer trainId) {
    return entityManager.createQuery("select distinct userId from general_key_pat_user where trainId = ?1 and role=0", String.class)
        .setParameter(1, trainId)
        .getResultList();
  }

  public List<BigDecimal> score() {
    return find("role=0 ORDER BY createTime DESC LIMIT 10").list()
        .stream().map(GeneralKeyPatUserEntity::getScore).toList();
  }

  public List<GeneralKeyPatUserEntity> findByTrainId(Integer trainId) {
    return find("trainId =?1", trainId).list();
  }

  public List<Integer> queryRelatedTrainId(String userId) {
    return entityManager.createQuery("select distinct trainId from general_key_pat_user where userId = ?1", Integer.class)
        .setParameter(1, userId)
        .getResultList();
  }

  public List<BigDecimal> findLastTwoResult(String user) {
    return entityManager.createQuery("SELECT ifnull(pu.score,0) " +
            "FROM general_key_pat_user pu LEFT JOIN general_key_pat p on pu.trainId = p.id " +
            "where p.trainType = 1 and pu.userId = ?1 ORDER BY p.createTime desc", BigDecimal.class)
        .setParameter(1, user)
        .getResultList();
  }
}
