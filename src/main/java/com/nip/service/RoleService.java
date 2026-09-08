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
  private final ObjectMapper objectMapper;

  @Inject
  public RoleService(RoleDao roleDao, MenusDao menusDao, RoleMenusDao roleMenusDao, MenusButtonDao menusButtonDao,
      MenusService menusService, ObjectMapper objectMapper) {
    this.roleDao = roleDao;
    this.menusDao = menusDao;
    this.roleMenusDao = roleMenusDao;
    this.menusButtonDao = menusButtonDao;
    this.menusService = menusService;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public RoleEntity addRole(SaveRoleDto entity) {
    RoleEntity role = entity.getRole();
    if (role == null) {
      throw new IllegalArgumentException("角色信息不能为空");
    }
    // isAdmin/isDefault 可能未传，判空后再比较，避免裸拆箱 NPE
    if (Integer.valueOf(1).equals(role.getIsAdmin()) && Integer.valueOf(0).equals(role.getIsDefault())) {
      roleDao.findAllByIsDefault(0).forEach(r -> {
        r.setIsDefault(1);
        roleDao.save(r);
      });
    }
    if (StringUtils.isEmpty(role.getId())) {
      role.setId(null);
    }
    // 新增走 persist、编辑走 merge：编辑分支此前完全不落库，标量字段改动永远丢失
    RoleEntity saved = roleDao.save(role);
    roleMenusDao.deleteAllByRoleId(saved.getId());
    entity.getMenus().forEach(m -> {
      Map<String, Object> map = JSONUtils.fromJson(m, new TypeToken<>() {});
      RoleMenusEntity roleMenusEntity = new RoleMenusEntity();
      roleMenusEntity.setRoleId(saved.getId());
      roleMenusEntity.setMenuId(map.get("menusId").toString());
      roleMenusEntity.setPer(map.get("per").toString());
      roleMenusDao.save(roleMenusEntity);
    });
    return saved;
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
    RoleEntity roleEntity = roleDao.findByIdOptional(id)
        .orElseThrow(() -> new IllegalArgumentException("未查询到该角色"));
    RoleInfoDto roleInfoDto = new RoleInfoDto();
    roleInfoDto.setRole(roleEntity);
    roleInfoDto.setMenus(getMenusEntityByRoleId(roleEntity.getId()));
    return roleInfoDto;
  }

  public RoleMenusDto getRoleMenusInfo(String id) {
    // Phase 7.4：与 getRoleById 口径一致——不存在的角色 id 显式报错，不再返回 role=null 的空壳
    RoleEntity roleEntity = roleDao.findByIdOptional(id)
        .orElseThrow(() -> new IllegalArgumentException("未查询到该角色"));
    RoleMenusDto roleInfoDto = new RoleMenusDto();
    roleInfoDto.setRole(roleEntity);
    roleInfoDto.setMenusAll(menusService.getMenusDtos());
    roleInfoDto.setMenusChecked(
        objectMapper.convertValue(getMenusEntityByRoleId(roleEntity.getId()),
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
      Map<String, Object> n = objectMapper.convertValue(m, new TypeReference<>() {
      });
      n.put("permissions", menusButtonEntityList);
      nm.add(n);
    }
    return nm;
  }
}
