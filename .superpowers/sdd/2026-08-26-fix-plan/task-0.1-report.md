# Task 0.1 报告：Testcontainers MySQL 测试地基

**结论：DONE_WITH_CONCERNS。** SmokeTest 通过（Tests run: 1, Failures: 0, Errors: 0），但实施中发现并修复了两个 brief 未预料的问题，其中一个改动了 brief 清单外的第 4 个文件（EquipmentDeviceEntity.java，DDL 建表失败，Target 明确授权修复）。

## 改动文件（4 个）

1. `src/test/java/com/nip/testsupport/MySqlResource.java`（新建）——brief 代码 + 一个 static 块（见问题 1）
2. `src/main/resources/application.yml`（文件尾追加 `%test` 段，与 brief 逐字一致）
3. `src/test/java/com/nip/SmokeTest.java`（新建，与 brief 逐字一致）
4. `src/main/java/com/nip/entity/EquipmentDeviceEntity.java`（brief 清单外，+1 行，见问题 2）

## 问题 1：Docker 29 与 Testcontainers 1.19.3 API 版本不兼容（环境问题，已修复）

**现象**：首跑失败，Testcontainers 找不到可用 Docker 环境：

```
UnixSocketClientProviderStrategy: failed with exception BadRequestException
(Status 400: {"message":"client version 1.32 is too old.
Minimum supported API version is 1.44, please upgrade your client..."})
```

**根因**（反编译 testcontainers-1.19.3.jar 核实）：`DockerClientProviderStrategy` 在未显式配置 `api.version` 时硬编码回退 `RemoteApiVersion.VERSION_1_32`；本机 Docker Engine 29.1.3 最低支持 API 1.44，直接拒绝。shaded `DefaultDockerClientConfig` 会读系统属性 `api.version` 覆盖该回退。

**验证**：`$MVN test -Dtest=SmokeTest -Dapi.version=1.44` → 容器启动、测试通过（首跑拉镜像+建 schema 共 213s）。

**持久化方案**：在 `MySqlResource` 静态块中 `System.setProperty("api.version", "1.44")`（类加载先于 `DB.start()`，先于 Docker 客户端策略解析）。选它而不是 pom surefire `systemPropertyVariables`，因为：workaround 与 Docker 用法同处一个文件、带注释说明可删条件（升级 testcontainers ≥1.21.x 后），且不扩大 pom 改动。这是对 brief"逐字使用"代码的唯一偏离。

## 问题 2：drop-and-create 建表失败——`option` 保留字未转义（已修复）

**DDL 错误原文**（Hibernate schema 创建日志）：

```
java.sql.SQLSyntaxErrorException: You have an error in your SQL syntax; check the
manual that corresponds to your MySQL server version for the right syntax to use
near 'option varchar(255), primary key (id)) engine=InnoDB' at line 1
```

**定位**：grep 全部实体，`option` 字段出现两处——
- `PostMilitaryTermTrainTestPaperEntity.java:33` 已有 `@Column(name = "`option`")` 转义 ✅
- `EquipmentDeviceEntity.java:43` **未转义** ❌ ← 问题实体（brief 断言"保留字列已转义"在此实体上不成立）

**修复**：按仓库既有模式给 `EquipmentDeviceEntity.option` 加 `@Column(name = "`option`")`。生产 `generation: none` 且列物理名不变（本来就叫 option），运行时行为零影响。该错误此前不致命（Hibernate 仅记日志），但违反 Step 4"确认无建表失败"的要求，且会让 t_equipment_device 在测试库缺表。

## 命令与输出

```
$ JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=SmokeTest   # 验收命令原样
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 33.08 s -- in com.nip.SmokeTest
[INFO] BUILD SUCCESS
```

日志复查：`grep -E "SQLSyntaxErrorException|GenerationTarget|error in your SQL"` → 无匹配，全部实体建表成功。

## 自审

- [x] 验收命令原样通过（不带任何额外 -D flag）
- [x] DDL 日志无建表失败
- [x] 改动文件 = brief 3 文件 + EquipmentDeviceEntity（Target 授权的 DDL 修复）；工作区中 doc/ 删除与 mvnw 改动为任务外既有状态，未纳入提交
- [x] 未引新依赖、未跑格式化器
- [ ] 偏离点已在上文标注：MySqlResource static 块、第 4 个文件

## Concerns（供主代理评审）

1. **mysql:8.0 与生产版本未核对**——brief 注释要求"执行前与生产版本核对"，但顶层 yml 只有 localhost jdbc url，仓库内无生产 MySQL 版本记录，无从核对。按 brief 原文使用 mysql:8.0。
2. **api.version=1.44 是 workaround 不是修复**——正解是升级 testcontainers（≥1.21.x 已适配 Docker 29），但受"不引新依赖/不改 pom"约束未做。升级后删除 MySqlResource 的 static 块即可。
3. **EquipmentDeviceEntity 是清单外文件**——修复正确性高置信（既有同模式先例 + 测试验证），但严格说超出"只改 3 个文件"的自审要求，故状态为 DONE_WITH_CONCERNS 而非 DONE。
