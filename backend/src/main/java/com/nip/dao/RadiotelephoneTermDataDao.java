package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostRadiotelephoneTermDataEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 14:47
 * @Description:
 */
@ApplicationScoped
public class RadiotelephoneTermDataDao extends BaseRepository<PostRadiotelephoneTermDataEntity, Integer> {

  /**
   * 根据type查询所有的数据
   *
   * @param: type
   */
  public List<PostRadiotelephoneTermDataEntity> findByTypeOrderByKey(Integer type) {
    return find("type = ?1 order by key", type).list();
  }

}
