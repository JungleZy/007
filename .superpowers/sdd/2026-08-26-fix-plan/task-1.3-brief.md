### Task 1.3: 菜单按钮权限静默丢失 + 自赋值（P0#3 + 改级#14，MenusService:83-116）

**Files:**
- Modify: `src/main/java/com/nip/service/MenusService.java:83-116`、`src/main/java/com/nip/controller/MenusController.java:47-49`
- Test: `src/test/java/com/nip/service/MenusServiceTest.java`

两个缺陷同方法一并修（改级#14 的自赋值在 :91-98：`menus.setParentId(menus.getParentId())` 全部自赋值，菜单元数据编辑从未生效；新建分支 :88 为 `menusDao.save(entity.getMenus())`，已核实保持原样）：

- [ ] **Step 1: 失败测试**（两个用例）

```java
@Test
void addMenusWithNullPermissionsKeepsButtons() {
  // 先建含 1 个按钮的菜单，再用 permissions=null 编辑
  // 断言：menusButtonDao.findAllByMenusId(menusId) 仍非空
}

@Test
void editMenusUpdatesMetadata() {
  // 编辑 title，断言 menusDao.findById(id).getTitle() 变化
}
```

- [ ] **Step 3: 修复**

```java
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
    menus.setParentId(in.getParentId());   // 原代码为自赋值，编辑从未生效
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
```

同步 `MenusController:47-49`：原 `menusButtonDto == null ? SYSTEM_ERROR : ...` 分支因方法不再返回 null 而成死码，删除该三目、直接包 success。

- [ ] Step 4-5：绿 → `git commit -m "fix(p0-3): 菜单权限先删后写吞异常 + 元数据自赋值"`

