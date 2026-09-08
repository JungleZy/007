package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostTickerTapeTrainPageValueEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-03-22 09:57
 * @Description:
 */
@ApplicationScoped
public class PostTickerTapeTrainPageValueDao extends BaseRepository<PostTickerTapeTrainPageValueEntity, String> {

  public PostTickerTapeTrainPageValueEntity findByTrainIdAndPageNumber(String trainId, Integer pageNumber) {
    return find("trainId = ?1 and pageNumber = ?2", trainId, pageNumber).list().stream().findFirst().orElse(null);
  }

  public List<PostTickerTapeTrainPageValueEntity> findByTrainId(String id) {
    return find("trainId =?1 ", id).list();
  }
}
