package com.nip.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nip.dao.MenusButtonDao;
import com.nip.dao.MenusDao;
import com.nip.dao.RoleMenusDao;
import com.nip.dto.MenusButtonDto;
import com.nip.dto.MenusDto;
import com.nip.dto.MenusMetaDto;
import com.nip.entity.MenusButtonEntity;
import com.nip.entity.MenusEntity;
import com.nip.entity.RoleMenusEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * MenusService
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-20 14:45
 */
@ApplicationScoped
public class MenusService {
  private final MenusDao menusDao;
  private final MenusButtonDao menusButtonDao;
  private final RoleMenusDao roleMenusDao;

  @Inject
  public MenusService(MenusDao menusDao, MenusButtonDao menusButtonDao, RoleMenusDao roleMenusDao) {
    this.menusDao = menusDao;
    this.menusButtonDao = menusButtonDao;
    this.roleMenusDao = roleMenusDao;
  }

  public List<MenusDto> getMenusDtos() {
    List<MenusEntity> all = menusDao.findAllByOrderBySortAsc();
    List<MenusDto> menusDtos = new ArrayList<>();
    all.forEach(menusEntity -> {
      if (menusEntity.getParentId().equals("-1")) {
        MenusDto menusDto = handleMenusDto(menusEntity);
        menusDto.setChildren(dg(all, menusEntity));
        menusDto.setPermissions(menusButtonDao.findAllByMenusId(menusEntity.getId()));
        menusDtos.add(menusDto);
      }
    });
    Collections.sort(menusDtos);
    return menusDtos;
  }

  public List<MenusDto> getMenusDtosById(String roleId) {
    List<MenusDto> menusDtos = new ArrayList<>();
    //组织路由菜单数据（按钮）
    List<MenusEntity> menusEntities = menusDao.getMenusByRoleId(roleId);
    Set<MenusEntity> list = new HashSet<>(menusEntities);
    menusEntities.forEach(a -> getAll(a, list));
    List<MenusEntity> md = new ArrayList<>(list);
    md.forEach(menusEntity -> {
      if (menusEntity.getParentId().equals("-1")) {
        MenusDto menusDto = handleMenusDto(menusEntity, roleId, null);
        menusDto.setChildren(dg2(md, menusEntity, roleId));
        //        menusDto.setPermissions(menusButtonDao.findAllByMenusId(menusEntity.getId()));
        menusDtos.add(menusDto);
      }
    });
    Collections.sort(menusDtos);
    return menusDtos;
  }

  public void getAll(MenusEntity a, Set<MenusEntity> list2) {
    if (!a.getParentId().equals("-1")) {
      MenusEntity menusEntity = menusDao.findById(a.getParentId());
      list2.add(menusEntity);
      getAll(menusEntity, list2);
    }
  }

  @Transactional
  public MenusButtonDto addMenus(MenusButtonDto entity) {
    try {
      MenusEntity menus;
      if (StringUtils.isEmpty(entity.getMenus().getId())) {
        menus = menusDao.save(entity.getMenus());
      } else {
        menus = menusDao.findById(entity.getMenus().getId());
        menus.setParentId(menus.getParentId());
        menus.setComponent(menus.getComponent());
        menus.setKey(menus.getKey());
        menus.setPath(menus.getPath());
        menus.setName(menus.getName());
        menus.setIcon(menus.getIcon());
        menus.setTitle(menus.getTitle());
        menus.setSort(menus.getSort());
      }

      List<MenusButtonEntity> permissions = entity.getPermissions();
      menusButtonDao.deleteAllByMenusId(menus.getId());
      List<MenusButtonEntity> ps = new ArrayList<>();
      permissions.forEach(p -> {
        p.setId(null);
        p.setMenusId(menus.getId());
        MenusButtonEntity save = menusButtonDao.save(p);
        ps.add(save);
      });
      entity.setPermissions(ps);
      entity.setMenus(menus);
      return entity;
    } catch (Exception e) {
      return null;
    }
  }

  public List<MenusDto> getMenusAll() {
    return getMenusDtos();
  }

  public MenusButtonDto getMenuById(String id) {
    MenusButtonDto menusButtonDto = new MenusButtonDto();
    menusButtonDto.setMenus(menusDao.findById(id));
    menusButtonDto.setPermissions(menusButtonDao.findAllByMenusId(id));
    return menusButtonDto;
  }

  private List<MenusDto> dg(List<MenusEntity> menusEntities, MenusEntity me) {
    List<MenusDto> menusDtos = new ArrayList<>();
    menusEntities.forEach(menusEntity -> {
      if (menusEntity.getParentId().equals(me.getId())) {
        MenusDto menusDto = handleMenusDto(menusEntity);
        menusDto.setChildren(dg(menusEntities, menusEntity));
        //        menusDto.setPermissions(menusButtonDao.findAllByMenusId(menusEntity.getId()));
        menusDtos.add(menusDto);
      }
    });
    Collections.sort(menusDtos);
    return menusDtos;
  }

  private List<MenusDto> dg2(List<MenusEntity> menusEntities, MenusEntity me, String role) {
    List<MenusDto> menusDtos = new ArrayList<>();
    menusEntities.forEach(menusEntity -> {
      if (menusEntity.getParentId().equals(me.getId())) {
        MenusDto menusDto = handleMenusDto(menusEntity, role, null);
        menusDto.setChildren(dg2(menusEntities, menusEntity, role));
        //        menusDto.setPermissions(menusButtonDao.findAllByMenusId(menusEntity.getId()));
        menusDtos.add(menusDto);
      }
    });
    Collections.sort(menusDtos);
    return menusDtos;
  }

  public MenusDto handleMenusDto(MenusEntity menusEntity) {
    MenusMetaDto menusMetaDto = new MenusMetaDto();
    menusMetaDto.setIcon(menusEntity.getIcon());
    menusMetaDto.setIconF(menusEntity.getIconF());
    menusMetaDto.setHeight(menusEntity.getHeight());
    menusMetaDto.setTitle(menusEntity.getTitle());
    menusMetaDto.setIsMenu(menusEntity.getIsMenu() == 0);
    menusMetaDto.setIsBread(menusEntity.getIsBread() == 0);
    MenusDto menusDto = new MenusDto();
    menusDto.setId(menusEntity.getId());
    menusDto.setKey(menusEntity.getKey());
    menusDto.setPath(menusEntity.getPath());
    menusDto.setName(menusEntity.getName());
    menusDto.setMeta(menusMetaDto);
    menusDto.setComponent(menusEntity.getComponent());
    menusDto.setSort(menusEntity.getSort());
    menusDto.setPermissions(menusButtonDao.findAllByMenusId(menusEntity.getId()));
    return menusDto;
  }

  public MenusDto handleMenusDto(MenusEntity menusEntity, String roleId, List<MenusDto> menusDtoList) {
    try {
      MenusMetaDto menusMetaDto = new MenusMetaDto();
      menusMetaDto.setIcon(menusEntity.getIcon());
      menusMetaDto.setIconF(menusEntity.getIconF());
      menusMetaDto.setHeight(menusEntity.getHeight());
      menusMetaDto.setTitle(menusEntity.getTitle());
      menusMetaDto.setIsMenu(menusEntity.getIsMenu() == 0);
      MenusDto menusDto = new MenusDto();
      menusDto.setId(menusEntity.getId());
      menusDto.setParentId(menusEntity.getParentId());
      menusDto.setKey(menusEntity.getKey());
      menusDto.setPath(menusEntity.getPath());
      menusDto.setName(menusEntity.getName());
      menusDto.setMeta(menusMetaDto);
      menusDto.setComponent(menusEntity.getComponent());
      menusDto.setSort(menusEntity.getSort());
      RoleMenusEntity firstByRoleIdAndMenuId = roleMenusDao.findFirstByRoleIdAndMenuId(roleId, menusEntity.getId());
      // 获取该角色在该menu下的按钮权限
      if (null != firstByRoleIdAndMenuId) {
        ObjectMapper om = new ObjectMapper();
        List<String> strings = om.readValue(firstByRoleIdAndMenuId.getPer(), new TypeReference<>() {
        });
        List<MenusButtonEntity> menusButtonEntityList = new ArrayList<>();
        strings.forEach(
            p -> menusButtonEntityList.addAll(menusButtonDao.findAllByMenusIdAndKey(menusEntity.getId(), p)));
        menusDto.setPermissions(menusButtonEntityList);
      } else {
        menusDto.setPermissions(new ArrayList<>());
      }
      if (null != menusDtoList) {
        // 如果当前menusDto已经被添加进menusDtoList中，那么就把这两个menusDto的按钮进行合并去重
        boolean flag = true;
        for (MenusDto m : menusDtoList) {
          if (Objects.equals(m.getId(), menusDto.getId())) {
            Set<MenusButtonEntity> hashSet = new HashSet<>();
            hashSet.addAll(m.getPermissions());
            hashSet.addAll(menusDto.getPermissions());
            List<MenusButtonEntity> entities = new ArrayList<>(hashSet);
            m.setPermissions(entities);
            flag = false;
            break;
          }
        }
        if (flag) {
          menusDtoList.add(menusDto);
        }
      }
      return menusDto;
    } catch (Exception e) {
      return new MenusDto();
    }
  }
}
