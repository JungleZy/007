package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostTickerTapeTrainEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 15:15
 * @Description:
 */
@ApplicationScoped
public class PostTickerTapeTrainDao extends BaseRepository<PostTickerTapeTrainEntity, String> {
  /**
   * 开始训练
   *
   * @param: id
   */
  @Transactional
  public void begin(String id) {
    update("startTime = now(),status=1 where id =?1", id);
  }

  /**
   * 完成训练
   *
   * @param: id
   * @param: validTime
   */
  @Transactional
  public void finish(String id, String validTime, int status) {
    update("status =?3,validTime = ?2,endTime=now() where id =?1", id, validTime, status);
  }
}
