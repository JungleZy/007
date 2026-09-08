package com.nip.service;

import com.nip.common.response.Response;
import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.dto.SaveRoleDto;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task 5.5 回归：
 * (a) 编辑已有角色时 roleDao.save 从未执行 → 标量字段永不落库；
 * (b) 库中无 isDefault=0 的角色时新建用户静默无角色。
 */
@QuarkusTest
class RoleEditPersistenceTest {
  @Inject RoleService roleService;
  @Inject UserService userService;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;
  @Inject UserDao userDao;

  private SaveRoleDto saveDto(RoleEntity role) {
    SaveRoleDto dto = new SaveRoleDto();
    dto.setRole(role);
    dto.setMenus(List.of());
    return dto;
  }

  /** 角色表由 drop-and-create 起始为空，但同批次其它用例可能留下默认角色，逐场清空以自隔离。 */
  private void clearDefaultRoles() {
    roleDao.findAllByIsDefault(0).forEach(r -> {
      r.setIsDefault(1);
      roleDao.save(r);
    });
  }

  private UserEntity newUser() {
    UserEntity user = new UserEntity();
    user.setUserAccount("acct" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    user.setUserName("role-fixture");
    user.setPassword("pw123456");
    user.setIdCard("110101199001010011");
    return user;
  }

  @Test
  void editingExistingRolePersistsScalarFields() {
    RoleEntity role = new RoleEntity();
    role.setTitle("role-" + UUID.randomUUID());
    role.setRemark("before");
    role.setIsAdmin(0);
    role.setIsDefault(1);
    String roleId = roleService.addRole(saveDto(role)).getId();
    assertNotNull(roleId, "新增角色必须落库并拿到主键");

    RoleEntity edit = new RoleEntity();
    edit.setId(roleId);
    String newTitle = "role-edited-" + UUID.randomUUID();
    edit.setTitle(newTitle);
    edit.setRemark("after");
    edit.setIsAdmin(1);
    edit.setIsDefault(0);
    roleService.addRole(saveDto(edit));

    RoleEntity persisted = roleDao.findById(roleId);
    assertNotNull(persisted, "编辑不得丢行");
    assertEquals(newTitle, persisted.getTitle(), "编辑后的 title 必须落库");
    assertEquals("after", persisted.getRemark(), "编辑后的 remark 必须落库");
    assertEquals(1, persisted.getIsAdmin(), "编辑后的 isAdmin 必须落库");
    assertEquals(0, persisted.getIsDefault(), "编辑后的 isDefault 必须落库");
  }

  @Test
  void addRoleWithoutFlagsDoesNotUnboxNpe() {
    RoleEntity role = new RoleEntity();
    role.setTitle("role-null-flags-" + UUID.randomUUID());
    assertDoesNotThrow(() -> roleService.addRole(saveDto(role)),
        "isAdmin/isDefault 未传不得裸拆箱 NPE");
  }

  @Test
  void newUserWithoutDefaultRoleFailsInsteadOfSilentlyHavingNoRole() {
    clearDefaultRoles();

    UserEntity fresh = newUser();
    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> userService.addUser(fresh, true),
        "缺默认角色必须显式失败，而不是创建一个无角色用户");
    assertEquals("系统未配置默认角色，无法创建用户", ex.getMessage());
    assertNull(userDao.findUserEntityByUserAccount(fresh.getUserAccount()),
        "失败必须整体回滚，不得留下无角色用户");
  }

  @Test
  void newUserGetsDefaultRoleWhenConfigured() {
    clearDefaultRoles();
    RoleEntity defaultRole = new RoleEntity();
    defaultRole.setTitle("role-default-" + UUID.randomUUID());
    defaultRole.setIsAdmin(0);
    defaultRole.setIsDefault(0);
    String defaultRoleId = roleDao.save(defaultRole).getId();

    UserEntity fresh = newUser();
    Response<Object> response = userService.addUser(fresh, true);
    assertEquals(200, response.getCode(), "配置了默认角色时新建用户须成功");

    UserEntity created = userDao.findUserEntityByUserAccount(fresh.getUserAccount());
    assertNotNull(created, "用户必须落库");
    assertEquals(1, userRoleDao.count("userId = ?1 and roleId = ?2", created.getId(), defaultRoleId),
        "新用户必须拿到默认角色");
  }
}
