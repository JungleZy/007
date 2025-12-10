package com.nip.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.reflect.TypeToken;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.MenusButtonDao;
import com.nip.dao.MenusDao;
import com.nip.dao.RoleDao;
import com.nip.dao.RoleMenusDao;
import com.nip.dto.RoleInfoDto;
import com.nip.dto.RoleMenusDto;
import com.nip.dto.SaveRoleDto;
import com.nip.dto.sql.FindMenusByRoleIdDto;
import com.nip.entity.MenusButtonEntity;
import com.nip.entity.RoleEntity;
import com.nip.entity.RoleMenusEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RoleService
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-20 15:38
 */
@ApplicationScoped
public class RoleService {
  private final RoleDao roleDao;
  private final MenusDao menusDao;
  private final RoleMenusDao roleMenusDao;
  private final MenusButtonDao menusButtonDao;
  private final MenusService menusService;

  @Inject
  public RoleService(RoleDao roleDao, MenusDao menusDao, RoleMenusDao roleMenusDao, MenusButtonDao menusButtonDao, MenusService menusService) {
    this.roleDao = roleDao;
    this.menusDao = menusDao;
    this.roleMenusDao = roleMenusDao;
    this.menusButtonDao = menusButtonDao;
    this.menusService = menusService;
  }

  @Transactional
  public RoleEntity addRole(SaveRoleDto entity) {
    if (entity.getRole().getIsAdmin() == 1 && entity.getRole().getIsDefault() == 0) {
      List<RoleEntity> allByIsDefault = roleDao.findAllByIsDefault(0);
      allByIsDefault.forEach(r -> {
        r.setIsDefault(1);
        roleDao.save(r);
      });
    }
    if (StringUtils.isEmpty(entity.getRole().getId())) {
      roleDao.save(entity.getRole());
    }

    if (entity.getRole().getId() != null) {
      roleMenusDao.deleteAllByRoleId(entity.getRole().getId());
    }
    entity.getMenus().forEach(m -> {
      Map<String, Object> map = JSONUtils.fromJson(m, new TypeToken<>() {});
      RoleMenusEntity roleMenusEntity = new RoleMenusEntity();
      roleMenusEntity.setRoleId(entity.getRole().getId());
      roleMenusEntity.setMenuId(map.get("menusId").toString());
      roleMenusEntity.setPer(map.get("per").toString());
      roleMenusDao.save(roleMenusEntity);
    });
    return entity.getRole();
  }

  public List<RoleInfoDto> getRoleAll() {
    List<RoleEntity> all = roleDao.findAll().list();
    List<RoleInfoDto> roleInfoDtoList = new ArrayList<>(all.size());
    all.forEach(roleEntity -> {
      RoleInfoDto roleInfoDto = new RoleInfoDto();
      roleInfoDto.setRole(roleEntity);
      roleInfoDto.setMenus(getMenusEntityByRoleId(roleEntity.getId()));
      roleInfoDtoList.add(roleInfoDto);
    });
    return roleInfoDtoList;
  }

  public RoleInfoDto getRoleById(String id) {
    RoleEntity roleEntity = roleDao.findById(id);
    RoleInfoDto roleInfoDto = new RoleInfoDto();
    roleInfoDto.setRole(roleEntity);
    roleInfoDto.setMenus(getMenusEntityByRoleId(roleEntity.getId()));
    return roleInfoDto;
  }

  public RoleMenusDto getRoleMenusInfo(String id) {
    RoleEntity roleEntity = roleDao.findById(id);
    RoleMenusDto roleInfoDto = new RoleMenusDto();
    roleInfoDto.setRole(roleEntity);
    roleInfoDto.setMenusAll(menusService.getMenusDtos());
    roleInfoDto.setMenusChecked(
        new ObjectMapper().convertValue(getMenusEntityByRoleId(roleEntity == null ? null : roleEntity.getId()),
            new TypeReference<>() {
            }
        ));
    return roleInfoDto;
  }

  public List<Map<String, Object>> getMenusEntityByRoleId(String roleId) {
    List<FindMenusByRoleIdDto> menusByRoleId = menusDao.findMenusByRoleId(roleId);
    List<Map<String, Object>> nm = new ArrayList<>();
    for (FindMenusByRoleIdDto m : menusByRoleId) {
      List<MenusButtonEntity> menusButtonEntityList = menusButtonDao.findAllByMenusId(m.getId());
      Map<String, Object> n = new ObjectMapper().convertValue(m, new TypeReference<>() {
      });
      n.put("permissions", menusButtonEntityList);
      nm.add(n);
    }
    return nm;
  }
}
