package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TickerTapeTrainStatisticalEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-06-01 16:30
 * @Description:
 */
@ApplicationScoped
public class TickerTapeTrainStatisticalDao extends BaseRepository<TickerTapeTrainStatisticalEntity, String> {
  public TickerTapeTrainStatisticalEntity findByUserIdAndType(String userId,Integer type){
    return find("userId = ?1 and type =?2",userId,type).firstResult();
  }

  public List<TickerTapeTrainStatisticalEntity> findByUserId(String userId){
    return find("userId = ?1",userId).list();
  }

}
