# Task 1.3 报告：菜单按钮权限静默丢失（P0#3）+ 元数据自赋值（改级#14）

**状态：DONE**

## 修改

- `src/main/java/com/nip/service/MenusService.java` addMenus：
  - 删除 try/catch(Exception)→return null 吞异常（先删后写在 @Transactional 内吞 NPE 导致按钮被删且事务提交）。
  - permissions==null → 抛 IllegalArgumentException（删除动作之前），拒绝编辑；非 null 空列表保持原"清空全部按钮"语义。
  - 编辑分支自赋值（`menus.setXxx(menus.getXxx())`）改为从入参 `in` 复制，元数据编辑真实生效。
  - 新建分支 `menusDao.save(entity.getMenus())` 保持原样（按 brief 已核实）。
- `src/main/java/com/nip/controller/MenusController.java:47-49`：方法不再返回 null，删除 `== null ? SYSTEM_ERROR : ...` 死三目，直接 `ResponseResult.success(...)`；顺带移除因此失效的 `ResponseCode` import。

## 测试（src/test/java/com/nip/service/MenusServiceTest.java）

1. `addMenusWithNullPermissionsKeepsButtons`：建含 1 按钮菜单 → permissions=null 编辑 → 断言按钮仍在。
2. `editMenusUpdatesMetadata`：建菜单 → 改 title 编辑 → 断言 `menusDao.findById(id).getTitle()` 已变。

## 红阶段证据（修复前，两用例各自失败于目标缺陷）

```
[ERROR]   MenusServiceTest.addMenusWithNullPermissionsKeepsButtons:65 permissions=null 不得删除已有按钮 ==> expected: <false> but was: <true>
[ERROR]   MenusServiceTest.editMenusUpdatesMetadata:77 编辑 title 必须生效 ==> expected: <m2-edit> but was: <m2>
[ERROR] Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
```

## 绿阶段证据（修复后）

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

命令：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=MenusServiceTest`

## Concerns

- 契约变化：permissions 缺失时接口从「HTTP 200 + SYSTEM_ERROR（原为 null 分支）」变为抛 IllegalArgumentException，由 JWTInterceptor 兜为 HTTP 200 + SYSTEM_ERROR + 消息——响应码不变，消息更明确，符合 global-constraints Phase 1 约定。
