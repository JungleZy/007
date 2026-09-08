package com.nip.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nip.dao.MenusButtonDao;
import com.nip.dao.MenusDao;
import com.nip.dao.RoleMenusDao;
import com.nip.dto.MenusButtonDto;
import com.nip.dto.MenusDto;
import com.nip.entity.MenusButtonEntity;
import com.nip.entity.MenusEntity;
import com.nip.entity.RoleMenusEntity;


import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest

class MenusServiceTest {
  @Inject MenusService service;
  @Inject MenusDao menusDao;
  @Inject MenusButtonDao menusButtonDao;
  @Inject RoleMenusDao roleMenusDao;
  @Inject ObjectMapper objectMapper;

  private MenusButtonDto menuWithOneButton(String title) {
    MenusEntity menus = new MenusEntity();
    menus.setTitle(title);
    menus.setKey("k-" + title);
    menus.setPath("/p/" + title);
    menus.setName("n-" + title);
    menus.setComponent("c");
    menus.setSort(1);

    MenusButtonEntity button = new MenusButtonEntity();
    button.setTitle("btn");
    button.setKey("btn-key");

    MenusButtonDto dto = new MenusButtonDto();
    dto.setMenus(menus);
    dto.setPermissions(new ArrayList<>(List.of(button)));
    return dto;
  }

  @Test
  void addMenusWithNullPermissionsKeepsButtons() {
    // 先建含 1 个按钮的菜单
    MenusButtonDto created = service.addMenus(menuWithOneButton("m1"));
    String menusId = created.getMenus().getId();
    assertFalse(menusButtonDao.findAllByMenusId(menusId).isEmpty());

    // 再用 permissions=null 编辑：不得静默删除已有按钮
    MenusButtonDto edit = new MenusButtonDto();
    MenusEntity in = new MenusEntity();
    in.setId(menusId);
    in.setTitle("m1");
    edit.setMenus(in);
    edit.setPermissions(null);

    try {
      service.addMenus(edit);
    } catch (RuntimeException expectedAfterFix) {
      // 修复后应在删除前抛出，拒绝编辑
    }
    assertFalse(menusButtonDao.findAllByMenusId(menusId).isEmpty(), "permissions=null 不得删除已有按钮");
  }

  @Test
  void editMenusUpdatesMetadata() {
    MenusButtonDto created = service.addMenus(menuWithOneButton("m2"));
    String menusId = created.getMenus().getId();

    MenusButtonDto edit = menuWithOneButton("m2-edit");
    edit.getMenus().setId(menusId);
    service.addMenus(edit);

    assertEquals("m2-edit", menusDao.findById(menusId).getTitle(), "编辑 title 必须生效");
  }

  private MenusEntity menu(String parentId, int sort) {
    MenusEntity m = new MenusEntity();
    m.setParentId(parentId);
    m.setSort(sort);
    m.setKey("k-" + UUID.randomUUID());
    m.setName("n");
    m.setPath("/p");
    m.setComponent("c");
    m.setTitle("t");
    return menusDao.save(m);
  }

  private void button(String menusId, String key) {
    MenusButtonEntity b = new MenusButtonEntity();
    b.setMenusId(menusId);
    b.setKey(key);
    b.setTitle("btn-" + key);
    menusButtonDao.save(b);
  }

  @Test
  @TestTransaction
  void roleMenusBatchAncestorClosureAndDecodedPermissions() throws Exception {
    // 三层树：root <- mid <- leaf；角色仅直连 leaf
    MenusEntity root = menu("-1", 1);
    MenusEntity mid = menu(root.getId(), 1);
    MenusEntity leaf = menu(mid.getId(), 1);

    // leaf 两个按钮，角色 per 只放行 "view"
    button(leaf.getId(), "view");
    button(leaf.getId(), "edit");

    String roleId = "role-" + UUID.randomUUID();
    RoleMenusEntity rm = new RoleMenusEntity();
    rm.setRoleId(roleId);
    rm.setMenuId(leaf.getId());
    rm.setPer(objectMapper.writeValueAsString(List.of("view")));
    roleMenusDao.save(rm);

    CountingMenusDao countingMenusDao = new CountingMenusDao();
    CountingRoleMenusDao countingRoleMenusDao = new CountingRoleMenusDao();
    CountingMenusButtonDao countingButtonDao = new CountingMenusButtonDao();
    MenusService svc = new MenusService(countingMenusDao, countingButtonDao, countingRoleMenusDao, objectMapper);

    List<MenusDto> dtos = svc.getMenusDtosById(roleId);

    // 父级闭包：root -> mid -> leaf 的完整链路被重建
    assertEquals(1, dtos.size(), "顶级只有 root");
    MenusDto rootDto = dtos.get(0);
    assertEquals(root.getId(), rootDto.getId());
    assertEquals(1, rootDto.getChildren().size());
    MenusDto midDto = rootDto.getChildren().get(0);
    assertEquals(mid.getId(), midDto.getId());
    assertEquals(1, midDto.getChildren().size());
    MenusDto leafDto = midDto.getChildren().get(0);
    assertEquals(leaf.getId(), leafDto.getId());

    // 权限过滤：仅 per 解码出的 "view" 按钮，"edit" 被过滤
    assertEquals(1, leafDto.getPermissions().size());
    assertEquals("view", leafDto.getPermissions().get(0).getKey());
    // 无角色行的祖先菜单权限为空
    assertTrue(rootDto.getPermissions().isEmpty());
    assertTrue(midDto.getPermissions().isEmpty());

    // N+1 契约：角色行/按钮各一次批量，逐菜单/逐 key 查询为零，祖先不走 findById
    assertEquals(1, countingRoleMenusDao.batchCalls, "role-menu 批量恰好一次");
    assertEquals(0, countingRoleMenusDao.singleCalls, "不得逐菜单 findFirstByRoleIdAndMenuId");
    assertEquals(1, countingButtonDao.batchCalls, "button 批量恰好一次");
    assertEquals(0, countingButtonDao.singleKeyCalls, "不得逐 key findAllByMenusIdAndKey");
    assertEquals(0, countingMenusDao.findByIdCalls, "祖先闭包不得逐级 findById");
  }

  static class CountingMenusDao extends MenusDao {
    int findByIdCalls;

    @Override
    public MenusEntity findById(String id) {
      findByIdCalls++;
      return find("id = ?1", id).firstResult();
    }
  }

  static class CountingRoleMenusDao extends RoleMenusDao {
    int batchCalls;
    int singleCalls;

    @Override
    public List<RoleMenusEntity> findAllByRoleId(String roleId) {
      batchCalls++;
      return super.findAllByRoleId(roleId);
    }

    @Override
    public RoleMenusEntity findFirstByRoleIdAndMenuId(String roleId, String menuId) {
      singleCalls++;
      return super.findFirstByRoleIdAndMenuId(roleId, menuId);
    }
  }

  static class CountingMenusButtonDao extends MenusButtonDao {
    int batchCalls;
    int singleKeyCalls;
    int singleMenuCalls;

    @Override
    public List<MenusButtonEntity> findAllByMenusIdIn(Set<String> menuIds) {
      batchCalls++;
      return super.findAllByMenusIdIn(menuIds);
    }

    @Override
    public List<MenusButtonEntity> findAllByMenusIdAndKey(String menusId, String key) {
      singleKeyCalls++;
      return super.findAllByMenusIdAndKey(menusId, key);
    }

    @Override
    public List<MenusButtonEntity> findAllByMenusId(String menusId) {
      singleMenuCalls++;
      return super.findAllByMenusId(menusId);
    }
  }
}
