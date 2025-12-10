package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.RoleEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.ID;

@ApplicationScoped
public class RoleDao extends BaseRepository<RoleEntity, String> {

  public RoleEntity findRoleByUserId(String userId) {
    return entityManager.createQuery(
      "select r FROM t_role as r LEFT JOIN t_user_role as ur on ur.roleId = r.id LEFT JOIN t_user as u on u.id = ur.userId "
        + "where u.id=:id", RoleEntity.class).setParameter(ID, userId).setMaxResults(1).getSingleResult();
  }

  public List<RoleEntity> findAllByIsDefault(int isDefault) {
    return find("isDefault", isDefault).list();
  }
}
