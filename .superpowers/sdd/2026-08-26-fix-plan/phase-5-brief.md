## Phase 5：持久层与 schema 对齐

- [ ] **Task 5.1**: `GeneralKeyPatPageDao:26-27`、`GeneralTelexPatPageDao:22-23` 的 `id = ?1` → `trainId = ?1`（正确参照 `GeneralKeyPatUserValueResolverDao:12-14`）；回归测试：两个 train 各两页数据，断言 findTwoPage 只返回目标 train 的页。
- [ ] **Task 5.2**: `TelegramTrainService:306` HQL 蛇形 `mores_value/morse_time` → 驼峰属性（:205 为正确参照）；persistence 分片 P2 清单的空 IN 判空、双引号字面量、聚合 ORDER BY 逐条修，各配命名查询集成测试（跑在测试容器上）。
- [ ] **Task 5.3**: 产出 `docs/database/migrations/2026-08-26-01-schema-sync.sql`：用批 0 容器 `drop-and-create` 后 `mysqldump --no-data` 导出实体 DDL，与 `docs/database/project006.sql` 差分，生成 5 张缺表 CREATE、`simulation_router_room.is_start_sign` ADD COLUMN、`general_key_pat_page.id` 类型对齐；与 `logs/info.log.1` 的 Hibernate validation 清单交叉核对无遗漏。
- [ ] **Task 5.4**: 产出 `02-engine-innodb.sql`：审计核实的 22 张 MyISAM 表 `ALTER TABLE ... ENGINE=InnoDB`；脚本头部注释注明"需全库备份+停服窗口"。
- [ ] **Task 5.5**: 在快照库演练 5.3/5.4 后，`application.yml` `%prod` 段加 `quarkus.hibernate-orm.database.generation: validate`。**顺序硬约束：迁移先行，validate 后置**，否则 prod 启动失败。

