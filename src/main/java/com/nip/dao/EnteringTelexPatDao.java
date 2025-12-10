package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.EnteringTelexPatEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EnteringTelexPatDao extends BaseRepository<EnteringTelexPatEntity, String> {
  /**
   * 根据id和类型查询
   *
   * @param: id
   * @param: type
   */
  public EnteringTelexPatEntity findByIdAndType(String id, Integer type) {
    return find("id =?1 and type = ?2", id, type).firstResult();
  }

  /**
   * 根据创建人id和type查询
   *
   * @param: createUserId
   * @param: type
   */
  public EnteringTelexPatEntity findByCreateUserIdAndType(String createUserId, Integer type) {
    return find("createUserId =?1 and type =?2", createUserId, type).firstResult();
  }
}
