## commits
7a7c126 fix(p0-3): 菜单权限先删后写吞异常 + 元数据自赋值

## stat
 .../sdd/2026-08-26-fix-plan/task-1.3-report.md     | 38 +++++++++++
 .../java/com/nip/controller/MenusController.java   |  6 +-
 src/main/java/com/nip/service/MenusService.java    | 58 ++++++++--------
 .../java/com/nip/service/MenusServiceTest.java     | 79 ++++++++++++++++++++++
 4 files changed, 146 insertions(+), 35 deletions(-)

## diff
diff --git a/.superpowers/sdd/2026-08-26-fix-plan/task-1.3-report.md b/.superpowers/sdd/2026-08-26-fix-plan/task-1.3-report.md
new file mode 100644
index 0000000..0261e86
--- /dev/null
+++ b/.superpowers/sdd/2026-08-26-fix-plan/task-1.3-report.md
@@ -0,0 +1,38 @@
+# Task 1.3 报告：菜单按钮权限静默丢失（P0#3）+ 元数据自赋值（改级#14）
+
+**状态：DONE**
+
+## 修改
+
+- `src/main/java/com/nip/service/MenusService.java` addMenus：
+  - 删除 try/catch(Exception)→return null 吞异常（先删后写在 @Transactional 内吞 NPE 导致按钮被删且事务提交）。
+  - permissions==null → 抛 IllegalArgumentException（删除动作之前），拒绝编辑；非 null 空列表保持原"清空全部按钮"语义。
+  - 编辑分支自赋值（`menus.setXxx(menus.getXxx())`）改为从入参 `in` 复制，元数据编辑真实生效。
+  - 新建分支 `menusDao.save(entity.getMenus())` 保持原样（按 brief 已核实）。
+- `src/main/java/com/nip/controller/MenusController.java:47-49`：方法不再返回 null，删除 `== null ? SYSTEM_ERROR : ...` 死三目，直接 `ResponseResult.success(...)`；顺带移除因此失效的 `ResponseCode` import。
+
+## 测试（src/test/java/com/nip/service/MenusServiceTest.java）
+
+1. `addMenusWithNullPermissionsKeepsButtons`：建含 1 按钮菜单 → permissions=null 编辑 → 断言按钮仍在。
+2. `editMenusUpdatesMetadata`：建菜单 → 改 title 编辑 → 断言 `menusDao.findById(id).getTitle()` 已变。
+
+## 红阶段证据（修复前，两用例各自失败于目标缺陷）
+
+```
+[ERROR]   MenusServiceTest.addMenusWithNullPermissionsKeepsButtons:65 permissions=null 不得删除已有按钮 ==> expected: <false> but was: <true>
+[ERROR]   MenusServiceTest.editMenusUpdatesMetadata:77 编辑 title 必须生效 ==> expected: <m2-edit> but was: <m2>
+[ERROR] Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
+```
+
+## 绿阶段证据（修复后）
+
+```
+[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
+[INFO] BUILD SUCCESS
+```
+
+命令：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=MenusServiceTest`
+
+## Concerns
+
+- 契约变化：permissions 缺失时接口从「HTTP 200 + SYSTEM_ERROR（原为 null 分支）」变为抛 IllegalArgumentException，由 JWTInterceptor 兜为 HTTP 200 + SYSTEM_ERROR + 消息——响应码不变，消息更明确，符合 global-constraints Phase 1 约定。
diff --git a/src/main/java/com/nip/controller/MenusController.java b/src/main/java/com/nip/controller/MenusController.java
index 2eaeb0b..007e8af 100644
--- a/src/main/java/com/nip/controller/MenusController.java
+++ b/src/main/java/com/nip/controller/MenusController.java
@@ -1,13 +1,12 @@
 package com.nip.controller;
 
-import com.nip.common.constants.ResponseCode;
 import com.nip.common.interceptor.JWT;
 import com.nip.common.response.Response;
 import com.nip.common.response.ResponseResult;
 import com.nip.dto.MenusButtonDto;
 import com.nip.dto.MenusDto;
 import com.nip.service.MenusService;
 import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.inject.Inject;
 import jakarta.ws.rs.GET;
 import jakarta.ws.rs.POST;
@@ -36,24 +35,21 @@ public class MenusController {
 
   @Inject
   public MenusController(MenusService menusService) {
     this.menusService = menusService;
   }
 
   @POST
   @Path("/addMenu")
   @Operation(summary = "添加或修改 权限")
   public Response<MenusButtonDto> addMenu(MenusButtonDto entity) {
-    MenusButtonDto menusButtonDto = menusService.addMenus(entity);
-    return menusButtonDto == null
-        ? ResponseResult.error(ResponseCode.SYSTEM_ERROR)
-        : ResponseResult.success(menusButtonDto);
+    return ResponseResult.success(menusService.addMenus(entity));
   }
 
   @GET
   @Path("/getMenusAll")
   @Operation(summary = "获取 所有权限")
   public Response<List<MenusDto>> getMenusAll() {
     return ResponseResult.success(menusService.getMenusAll());
   }
 
   @GET
diff --git a/src/main/java/com/nip/service/MenusService.java b/src/main/java/com/nip/service/MenusService.java
index a654342..6e3c25c 100644
--- a/src/main/java/com/nip/service/MenusService.java
+++ b/src/main/java/com/nip/service/MenusService.java
@@ -75,51 +75,49 @@ public class MenusService {
   public void getAll(MenusEntity a, Set<MenusEntity> list2) {
     if (!a.getParentId().equals("-1")) {
       MenusEntity menusEntity = menusDao.findById(a.getParentId());
       list2.add(menusEntity);
       getAll(menusEntity, list2);
     }
   }
 
   @Transactional
   public MenusButtonDto addMenus(MenusButtonDto entity) {
-    try {
-      MenusEntity menus;
-      if (StringUtils.isEmpty(entity.getMenus().getId())) {
-        menus = menusDao.save(entity.getMenus());
-      } else {
-        menus = menusDao.findById(entity.getMenus().getId());
-        menus.setParentId(menus.getParentId());
-        menus.setComponent(menus.getComponent());
-        menus.setKey(menus.getKey());
-        menus.setPath(menus.getPath());
-        menus.setName(menus.getName());
-        menus.setIcon(menus.getIcon());
-        menus.setTitle(menus.getTitle());
-        menus.setSort(menus.getSort());
-      }
-
-      List<MenusButtonEntity> permissions = entity.getPermissions();
-      menusButtonDao.deleteAllByMenusId(menus.getId());
-      List<MenusButtonEntity> ps = new ArrayList<>();
-      permissions.forEach(p -> {
-        p.setId(null);
-        p.setMenusId(menus.getId());
-        MenusButtonEntity save = menusButtonDao.save(p);
-        ps.add(save);
-      });
-      entity.setPermissions(ps);
-      entity.setMenus(menus);
-      return entity;
-    } catch (Exception e) {
-      return null;
+    if (entity.getPermissions() == null) {
+      throw new IllegalArgumentException("permissions 缺失，拒绝编辑菜单权限");
+    }
+    // 注意：permissions 为非 null 空列表 = 有意清空全部按钮，与原语义一致，放行
+    MenusEntity menus;
+    MenusEntity in = entity.getMenus();
+    if (StringUtils.isEmpty(in.getId())) {
+      menus = menusDao.save(in);
+    } else {
+      menus = menusDao.findById(in.getId());
+      menus.setParentId(in.getParentId());
+      menus.setComponent(in.getComponent());
+      menus.setKey(in.getKey());
+      menus.setPath(in.getPath());
+      menus.setName(in.getName());
+      menus.setIcon(in.getIcon());
+      menus.setTitle(in.getTitle());
+      menus.setSort(in.getSort());
     }
+    menusButtonDao.deleteAllByMenusId(menus.getId());
+    List<MenusButtonEntity> ps = new ArrayList<>();
+    entity.getPermissions().forEach(p -> {
+      p.setId(null);
+      p.setMenusId(menus.getId());
+      ps.add(menusButtonDao.save(p));
+    });
+    entity.setPermissions(ps);
+    entity.setMenus(menus);
+    return entity;
   }
 
   public List<MenusDto> getMenusAll() {
     return getMenusDtos();
   }
 
   public MenusButtonDto getMenuById(String id) {
     MenusButtonDto menusButtonDto = new MenusButtonDto();
     menusButtonDto.setMenus(menusDao.findById(id));
     menusButtonDto.setPermissions(menusButtonDao.findAllByMenusId(id));
diff --git a/src/test/java/com/nip/service/MenusServiceTest.java b/src/test/java/com/nip/service/MenusServiceTest.java
new file mode 100644
index 0000000..3a9578d
--- /dev/null
+++ b/src/test/java/com/nip/service/MenusServiceTest.java
@@ -0,0 +1,79 @@
+package com.nip.service;
+
+import com.nip.dao.MenusButtonDao;
+import com.nip.dao.MenusDao;
+import com.nip.dto.MenusButtonDto;
+import com.nip.entity.MenusButtonEntity;
+import com.nip.entity.MenusEntity;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import java.util.ArrayList;
+import java.util.List;
+
+import static org.junit.jupiter.api.Assertions.*;
+
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class MenusServiceTest {
+  @Inject MenusService service;
+  @Inject MenusDao menusDao;
+  @Inject MenusButtonDao menusButtonDao;
+
+  private MenusButtonDto menuWithOneButton(String title) {
+    MenusEntity menus = new MenusEntity();
+    menus.setTitle(title);
+    menus.setKey("k-" + title);
+    menus.setPath("/p/" + title);
+    menus.setName("n-" + title);
+    menus.setComponent("c");
+    menus.setSort(1);
+
+    MenusButtonEntity button = new MenusButtonEntity();
+    button.setTitle("btn");
+    button.setKey("btn-key");
+
+    MenusButtonDto dto = new MenusButtonDto();
+    dto.setMenus(menus);
+    dto.setPermissions(new ArrayList<>(List.of(button)));
+    return dto;
+  }
+
+  @Test
+  void addMenusWithNullPermissionsKeepsButtons() {
+    // 先建含 1 个按钮的菜单
+    MenusButtonDto created = service.addMenus(menuWithOneButton("m1"));
+    String menusId = created.getMenus().getId();
+    assertFalse(menusButtonDao.findAllByMenusId(menusId).isEmpty());
+
+    // 再用 permissions=null 编辑：不得静默删除已有按钮
+    MenusButtonDto edit = new MenusButtonDto();
+    MenusEntity in = new MenusEntity();
+    in.setId(menusId);
+    in.setTitle("m1");
+    edit.setMenus(in);
+    edit.setPermissions(null);
+
+    try {
+      service.addMenus(edit);
+    } catch (RuntimeException expectedAfterFix) {
+      // 修复后应在删除前抛出，拒绝编辑
+    }
+    assertFalse(menusButtonDao.findAllByMenusId(menusId).isEmpty(), "permissions=null 不得删除已有按钮");
+  }
+
+  @Test
+  void editMenusUpdatesMetadata() {
+    MenusButtonDto created = service.addMenus(menuWithOneButton("m2"));
+    String menusId = created.getMenus().getId();
+
+    MenusButtonDto edit = menuWithOneButton("m2-edit");
+    edit.getMenus().setId(menusId);
+    service.addMenus(edit);
+
+    assertEquals("m2-edit", menusDao.findById(menusId).getTitle(), "编辑 title 必须生效");
+  }
+}
