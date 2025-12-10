package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TelexPatTrainStatisticalEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.USER_ID;

/**
 * @Author: wushilin
 * @Data: 2022-06-01 16:30
 * @Description:
 */
@ApplicationScoped
public class TelexPatTrainStatisticalDao extends BaseRepository<TelexPatTrainStatisticalEntity, String> {

  public TelexPatTrainStatisticalEntity getByUserIdAndType(String userId, Integer type) {
    return find("userId = ?1 and type = ?2", userId, type).firstResult();
  }

  /**
   * 根据用ID查询统计信息
   *
   * @param: userId
   */
  public List<TelexPatTrainStatisticalEntity> findAllByUserId(String userId) {
    return find(USER_ID, userId).list();
  }

  /**
   * 根据用户id和类型查询统计记录
   *
   * @param: userId
   * @param: type
   */
  public TelexPatTrainStatisticalEntity findByUserIdAndType(String userId, Integer type) {
    return find("userId = ?1 and type =?2", userId, type).firstResult();
  }

}
