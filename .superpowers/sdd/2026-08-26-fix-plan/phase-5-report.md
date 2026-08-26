# Phase 5 报告：持久层与 schema 对齐

**结论：Task 5.1–5.5 全部完成，5 个提交，8/8 测试绿，快照库演练通过后已启用 %prod validate。**

## 提交清单

| Task | 提交 | 内容 |
|---|---|---|
| 5.1 | `6635da5` | GeneralKeyPatPageDao/GeneralTelexPatPageDao 的 findTwoPage `id = ?1` → `trainId = ?1`（排序补 `.and("sort")`，对照 GeneralKeyPatUserValueResolverDao:12-14）+ 回归测试 |
| 5.2 | `2c1be29` | TelegramTrainService:306 `mores_value/morse_time` → `moresValue/moresTime`；MilitaryTermDataDao 两处 in(?1) 空/null 判空；PostTelegramTrainFloorContentDao clearByTranId 双引号→单引号、count 查询去 ORDER BY + 集成测试 |
| 5.3 | `a6e0c13` | `docs/database/migrations/2026-08-26-01-schema-sync.sql` |
| 5.4 | `3c983a0` | `docs/database/migrations/2026-08-26-02-engine-innodb.sql` |
| 5.5 | `f823a33` | application.yml 新增 `%prod` 段 `generation: validate`（未触碰 `%test` 与顶层） |

## Task 5.1 / 5.2 红→绿证据

红（修复前，`flock` 包裹 `mvnw test -Dtest=四个测试类`，Tests run: 8, Failures: 4）：
- `PatPageFindTwoPageDaoTest.keyPatFindTwoPage...`：expected 2 but was **0**（id 过滤匹配不到 trainId）
- `PatPageFindTwoPageDaoTest.telexPatFindTwoPage...`：expected 2 but was **0**
- `TelegramTrainServiceTest.saveFloorContent...` ×2：expected 200 but was **500**，根因栈：`SemanticException: Could not interpret path expression 'mores_value'`（TelegramTrainService.java:306）

绿（修复后同命令）：**Tests run: 8, Failures: 0, Errors: 0**。

⚠ 红阶段实证（与评审 [INFERENCE] 不符，如实记录）：P2-7 空 IN、P2-11 双引号字面量、P2-12 聚合 ORDER BY 三项在 Hibernate 6.x + MySQL 8 上**当前并不报错**（4 个对应测试修复前即绿）。三处仍按 brief 修正（防御式判空 / HQL 标准单引号 / 去无意义 ORDER BY），测试转为契约防守。

## Task 5.3 差分方法与交叉核对

1. `%test` 容器 drop-and-create 后用临时 `@QuarkusTest`（SchemaExportTest，用毕已删除，未入库）对全库 `SHOW CREATE TABLE` 导出实体 DDL（103 表）。
2. 与 `docs/database/project006.sql`（100 表）程序化逐表逐列差分：
   - 缺表 5：`general_telex_pat` / `_page` / `_user` / `_user_value`、`t_masthead`（与审计一致）
   - 缺列 2：`simulation_router_room.is_start_sign`、**`t_post_ticker_tape_train.is_start_sign`**（后者为 brief/审计未收录、Hibernate 日志实证的新增项，PostTickerTapeTrainEntity.java:161 声明）
   - 98 张共有表逐列类型族比对：**0 处不一致**——Hibernate 校验日志里所有 `modify column` 行均为长度/字符集/显示宽度噪音，validate（`Dialect#equivalentTypes`）不校验这些，无需迁移
   - `general_key_pat_page.id`：当前快照已是 varchar(64)（漂移发生在 base→current 之间），迁移中保留 `MODIFY varchar(64) NOT NULL` 以兜底仍在旧基线的环境（当前快照上为无害同型重建，演练已证）
3. Hibernate validation 清单交叉核对：实际内容在 `logs/info.log.2`（brief 写的 info.log.1 是轮转后的端口占用错误，不含校验信息）。日志中 5 条 `create table` + 2 条 `add column is_start_sign` 与差分结果完全一致，无遗漏。
4. 审计"新发现1"排除：`TheoryKnowledgeQuestionLevelEntity` 的 `@Entity(name)` 实为 `t_theory_question_level`（dump 已有），不存在第 6 张缺表。

## Task 5.4

22 张 MyISAM 表清单未采信文档，独立对 dump 全 100 表引擎重新枚举，实数 22、逐名与审计一致。脚本头部已注明"全库备份 + 停服窗口 + 顺序在 01 之后"。

## Task 5.5 演练日志摘要

一次性容器 `mysql:8.0`（p5-rehearsal，已销毁）：
1. 灌入 `project006.sql` → 100 表 / 22 MyISAM ✓
2. 应用 01-schema-sync.sql → exit 0；应用 02-engine-innodb.sql → exit 0
3. 事后核验：**105 表 / 0 MyISAM**；两处 `is_start_sign` 均存在且 DEFAULT 1；`general_key_pat_page.id` = varchar(64) ✓
4. **强化演练（超出 brief 要求）**：`mvnw package` 后以 prod profile + 环境变量 `QUARKUS_HIBERNATE_ORM_DATABASE_GENERATION=validate` 直连演练库启动打包应用——`started in 3.591s / Profile prod activated`，运行 90 秒窗口零 Schema-validation 错误，正常停机（对照：迁移前同款校验 2 秒内即报 `missing table [general_telex_pat]`，见 info.log.2）。据此才落 `%prod validate` 配置。

## Concerns

1. **迁移 01 的 `is_start_sign` 用 `DEFAULT 1` 回填存量行**（实体 Java 默认值为 1，命名查询直接 SELECT 该列）；若业务预期存量房间/训练应为"未开始拍发"以外的语义，需人工复核。
2. **迁移 02 对生产库是重建整表的锁表操作**，脚本头部已注明需全库备份 + 停服窗口；MyISAM 备份对在线写不安全，必须停写后备份。
3. **prod 部署顺序硬约束**：先 01 → 02 → 再发布含 `%prod validate` 的版本；旧库直接上新版会启动失败（这是 validate 的预期防护行为）。
4. dump 中存在实体未映射的 `t_union_room`、`seq` 两张表（validate 不管多余表，无影响，仅记录）。
5. 演练用的打包产物包含 Ph2Ws/Ph3Score 同工作区未提交改动，validate 结论只依赖实体注解，不受影响。
