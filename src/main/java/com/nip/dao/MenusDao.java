package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.sql.FindMenusByRoleIdDto;
import com.nip.entity.MenusEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.ID;

@ApplicationScoped
public class MenusDao extends BaseRepository<MenusEntity, String> {

  public List<MenusEntity> findAllByOrderBySortAsc() {
    return findAll(Sort.by("sort").ascending()).list();
  }

  public List<FindMenusByRoleIdDto> findMenusByRoleId(String roleId) {
    try {
      return entityManager.createNamedQuery("find_menus_by_role_id", FindMenusByRoleIdDto.class)
                          .setParameter("rid", roleId).getResultList();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public List<MenusEntity> getMenusByRoleId(String roleId) {
    return entityManager.createQuery(
                          "select m from t_menus m left join t_role_menus rm on rm.menuId = m.id where rm.roleId=:id", MenusEntity.class)
                        .setParameter(ID, roleId).getResultList();
  }
}
