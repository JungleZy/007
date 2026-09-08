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
import java.util.function.Function;
import java.util.stream.Collectors;

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
  private final ObjectMapper objectMapper;

  @Inject
  public MenusService(MenusDao menusDao, MenusButtonDao menusButtonDao, RoleMenusDao roleMenusDao,
      ObjectMapper objectMapper) {
    this.menusDao = menusDao;
    this.menusButtonDao = menusButtonDao;
    this.roleMenusDao = roleMenusDao;
    this.objectMapper = objectMapper;
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
    Map<String, MenusEntity> byId = menusDao.findAllByOrderBySortAsc().stream()
        .collect(Collectors.toMap(MenusEntity::getId, Function.identity()));
    Set<MenusEntity> list = new HashSet<>(menusEntities);
    menusEntities.forEach(a -> getAll(a, list, byId));
    List<MenusEntity> md = new ArrayList<>(list);
    Map<String, RoleMenusEntity> roleMenuByMenuId = roleMenusDao.findAllByRoleId(roleId).stream()
        .collect(Collectors.toMap(RoleMenusEntity::getMenuId, Function.identity(), (a, b) -> a));
    Set<String> menuIds = md.stream().map(MenusEntity::getId).collect(Collectors.toSet());
    Map<String, List<MenusButtonEntity>> buttonsByMenuId = menusButtonDao.findAllByMenusIdIn(menuIds).stream()
        .collect(Collectors.groupingBy(MenusButtonEntity::getMenusId));
    md.forEach(menusEntity -> {
      if (menusEntity.getParentId().equals("-1")) {
        MenusDto menusDto = handleMenusDto(menusEntity, roleMenuByMenuId, buttonsByMenuId);
        menusDto.setChildren(dg2(md, menusEntity, roleMenuByMenuId, buttonsByMenuId));
        menusDtos.add(menusDto);
      }
    });
    Collections.sort(menusDtos);
    return menusDtos;
  }

  public void getAll(MenusEntity a, Set<MenusEntity> list2, Map<String, MenusEntity> byId) {
    if (!a.getParentId().equals("-1")) {
      MenusEntity menusEntity = byId.get(a.getParentId());
      if (menusEntity != null) {
        list2.add(menusEntity);
        getAll(menusEntity, list2, byId);
      }
    }
  }

  @Transactional
  public MenusButtonDto addMenus(MenusButtonDto entity) {
    if (entity.getPermissions() == null) {
      throw new IllegalArgumentException("permissions 缺失，拒绝编辑菜单权限");
    }
    // 注意：permissions 为非 null 空列表 = 有意清空全部按钮，与原语义一致，放行
    MenusEntity menus;
    MenusEntity in = entity.getMenus();
    if (StringUtils.isEmpty(in.getId())) {
      menus = menusDao.save(in);
    } else {
      menus = menusDao.findById(in.getId());
      if (menus == null) {
        throw new IllegalArgumentException("菜单不存在: " + in.getId());
      }
      menus.setParentId(in.getParentId());
      menus.setComponent(in.getComponent());
      menus.setKey(in.getKey());
      menus.setPath(in.getPath());
      menus.setName(in.getName());
      menus.setIcon(in.getIcon());
      menus.setTitle(in.getTitle());
      menus.setSort(in.getSort());
    }
    menusButtonDao.deleteAllByMenusId(menus.getId());
    List<MenusButtonEntity> ps = new ArrayList<>();
    entity.getPermissions().forEach(p -> {
      p.setId(null);
      p.setMenusId(menus.getId());
      ps.add(menusButtonDao.save(p));
    });
    entity.setPermissions(ps);
    entity.setMenus(menus);
    return entity;
  }

  public List<MenusDto> getMenusAll() {
    return getMenusDtos();
  }

  public MenusButtonDto getMenuById(String id) {
    // Phase 7.4：不存在的菜单 id 必须显式报错，避免前端拿到 menus=null 的空壳
    MenusEntity menusEntity = Optional.ofNullable(menusDao.findById(id))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该菜单"));
    MenusButtonDto menusButtonDto = new MenusButtonDto();
    menusButtonDto.setMenus(menusEntity);
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

  private List<MenusDto> dg2(List<MenusEntity> menusEntities, MenusEntity me,
      Map<String, RoleMenusEntity> roleMenuByMenuId, Map<String, List<MenusButtonEntity>> buttonsByMenuId) {
    List<MenusDto> menusDtos = new ArrayList<>();
    menusEntities.forEach(menusEntity -> {
      if (menusEntity.getParentId().equals(me.getId())) {
        MenusDto menusDto = handleMenusDto(menusEntity, roleMenuByMenuId, buttonsByMenuId);
        menusDto.setChildren(dg2(menusEntities, menusEntity, roleMenuByMenuId, buttonsByMenuId));
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
    // Phase 7.4：isMenu/isBread 是可空 Integer，裸 == 拆箱会 NPE
    menusMetaDto.setIsMenu(Objects.equals(menusEntity.getIsMenu(), 0));
    menusMetaDto.setIsBread(Objects.equals(menusEntity.getIsBread(), 0));
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

  private MenusDto handleMenusDto(MenusEntity menusEntity,
      Map<String, RoleMenusEntity> roleMenuByMenuId, Map<String, List<MenusButtonEntity>> buttonsByMenuId) {
    try {
      MenusMetaDto menusMetaDto = new MenusMetaDto();
      menusMetaDto.setIcon(menusEntity.getIcon());
      menusMetaDto.setIconF(menusEntity.getIconF());
      menusMetaDto.setHeight(menusEntity.getHeight());
      menusMetaDto.setTitle(menusEntity.getTitle());
      menusMetaDto.setIsMenu(Objects.equals(menusEntity.getIsMenu(), 0));
      MenusDto menusDto = new MenusDto();
      menusDto.setId(menusEntity.getId());
      menusDto.setParentId(menusEntity.getParentId());
      menusDto.setKey(menusEntity.getKey());
      menusDto.setPath(menusEntity.getPath());
      menusDto.setName(menusEntity.getName());
      menusDto.setMeta(menusMetaDto);
      menusDto.setComponent(menusEntity.getComponent());
      menusDto.setSort(menusEntity.getSort());
      RoleMenusEntity firstByRoleIdAndMenuId = roleMenuByMenuId.get(menusEntity.getId());
      // 获取该角色在该menu下的按钮权限
      if (null != firstByRoleIdAndMenuId) {
        List<String> strings = objectMapper.readValue(firstByRoleIdAndMenuId.getPer(), new TypeReference<>() {
        });
        List<MenusButtonEntity> buttons = buttonsByMenuId.getOrDefault(menusEntity.getId(), List.of());
        List<MenusButtonEntity> menusButtonEntityList = new ArrayList<>();
        strings.forEach(p -> buttons.forEach(b -> {
          if (Objects.equals(b.getKey(), p)) {
            menusButtonEntityList.add(b);
          }
        }));
        menusDto.setPermissions(menusButtonEntityList);
      } else {
        menusDto.setPermissions(new ArrayList<>());
      }
      return menusDto;
    } catch (Exception e) {
      return new MenusDto();
    }
  }
}
