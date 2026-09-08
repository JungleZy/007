package com.nip.dao.general.ticker;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.GeneralTickerPatTrainUserDto;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainUserEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class GeneralTickerPatTrainUserDao extends BaseRepository<GeneralTickerPatTrainUserEntity, Integer> {

  /**
   * 根据用户id查询信息
   *
   * @param id id
   * @return entity
   */
  public List<GeneralTickerPatTrainUserEntity> findByUserId(String id) {
    return find("userId = ?1", id).list();
  }

  /**
   * 根据训练id查询训练信息和用户信息
   *
   * @param id 训练id
   * @return map
   */
  public List<GeneralTickerPatTrainUserDto> findByTrainIdToMap(Integer id, String uid) {
    return entityManager.createNamedQuery("find_general_ticker_pat_train_user_dto", GeneralTickerPatTrainUserDto.class)
        .setParameter(1, id)
        .setParameter(2, uid)
        .getResultList();
  }

  /**
   * 根据训练id和人员角色查询记录
   *
   * @param trainId 训练id
   * @param role    角色 0 参训人 1 组训人
   * @return entity
   */
  public List<GeneralTickerPatTrainUserEntity> findByTrainIdAndRole(Integer trainId, Integer role) {
    return find("trainId = ?1 and role = ?2", trainId, role).list();
  }

  /**
   * 根据用户id 和训练id查询
   *
   * @param userId  用户id
   * @param trainId 训练id
   * @return entity
   */
  public GeneralTickerPatTrainUserEntity findByUserIdAndTrainId(String userId, Integer trainId) {
    return find("userId = ?1 and trainId = ?2", userId, trainId).firstResult();
  }

  /**
   * 查询上一次训练记录
   *
   * @param userId     用户id
   * @param createTime 创建时间
   * @return entity
   */
  public GeneralTickerPatTrainUserEntity findFirstByUserIdAndFinishTimeBeforeOrderByFinishTimeDesc(String userId, LocalDateTime createTime) {
    return find("userId = ?1 and finishTime < ?2 order by finishTime desc", userId, createTime).firstResult();
  }

  public List<GeneralTickerPatTrainUserEntity> findByUseridTop2(LocalDateTime createTime, String id) {
    return find("userId = ?1 and createTime < ?2 and isFinish = 1 ORDER BY createTime DESC LIMIT 2", id, createTime).list();
  }

  public List<BigDecimal> getClassLastTwoResult(String user) {
    return entityManager.createQuery("SELECT ifnull(tu.score,0) " +
            "from general_ticker_pat_train_user tu " +
            "LEFT JOIN general_ticker_pat p on tu.trainId = p.id " +
            "where p.trainType =1 and  tu.userId = ?1 " +
            "ORDER BY p.createTime DESC LIMIT 2", BigDecimal.class)
        .setParameter(1, user)
        .getResultList();
  }
  public List<GeneralTickerPatTrainUserEntity> findRoleAdminByUserId(Integer trainId) {
    return find("trainId =?1 and role= 1", trainId).list();
  }
}
