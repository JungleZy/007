package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.RoleMenusEntity;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RoleMenusDao extends BaseRepository<RoleMenusEntity, String> {

  public RoleMenusEntity findFirstByRoleIdAndMenuId(String roleId, String menuId) {
    return find(
        "roleId = :roleId and menuId = :menuId", Parameters.with("roleId", roleId).and("menuId", menuId)).firstResult();
  }

  public void deleteAllByRoleId(String roleId) {
    delete("roleId = :roleId", Parameters.with("roleId", roleId));
  }
}
