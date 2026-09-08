package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.UserRoleEntity;
import jakarta.enterprise.context.ApplicationScoped;

import static com.nip.common.constants.BaseConstants.USER_ID;

@ApplicationScoped
public class UserRoleDao extends BaseRepository<UserRoleEntity, String> {
  public void deleteByUserId(String userId) {
    delete(USER_ID, userId);
  }

  public UserRoleEntity findByUserId(String userId) {
    return find(USER_ID, userId).firstResult();
  }
}
