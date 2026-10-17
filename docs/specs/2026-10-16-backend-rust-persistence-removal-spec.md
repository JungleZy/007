# backend-rust 移除 persistence crate 与 ORM 表述修正规格（2026-10-16）

> 状态：§5 取舍已于 2026-10-16 确认，未实施。基线 `22ba43d8`。本规格承接结构重构计划 §7 待决项 P8（`persistence` crate 运行时未被使用），给出“删除”的决定、影响面与验收。

## 1. 背景与决定

2026-09-10 的可行性规格与重写计划定的持久层方案是“SeaORM 为主，复杂/关键 SQL 用 SQLx 或 raw SQL”。实际执行中：

- `d68fb9f6`（2026-09-21）建立 `backend-rust/crates/persistence`（包名 `rust-persistence`），从 schema 生成 105 个 SeaORM 实体文件，约 2,865 行；
- `crates/app` 从未依赖该 crate，全部业务以 SQLx 运行时字符串查询实现（`query()`/`query_as()` 1,382 处，`query!` 宏 0 处）；
- 仓库无“放弃 SeaORM”的决策记录，结构重构计划将其称为“既有决定”但无出处。

**决定**：持久层确认为 SQLx 显式 SQL，不采用 ORM；删除 `persistence` crate，并修正现行文档中把 SeaORM 当作 Rust 方案的表述。理由：

1. 迁移以 Java 行为逐行对账为验收，显式 SQL 可逐句对照 Java HQL/原生查询，ORM 查询构造器会引入额外翻译层；
2. 遗留 schema 有 3 张无主键表无法生成实体（`docs/reviews/rust-migration/seaorm-entity-generation.json:24`），`is_admin=0` 为管理员等反直觉字段不宜被模型掩盖；
3. schema 由 Java 侧 `backend/database/migrations/` 管理、Rust 禁止自动 DDL，ORM 的迁移与模型同步能力无用；
4. 126 处 `FOR UPDATE` 与结算事务需要精确控制锁范围；
5. 已有 860 条 SQL 通过双栈对账，换 ORM 等于全部重写。

## 2. 范围

本规格只覆盖两个独立交付单元：

| 标识 | 交付单元 | 提交类型 |
|---|---|---|
| PD01 | 删除 `persistence` crate 及其专属脚本 | `refactor(backend-rust)` |
| PD02 | 修正现行文档的 ORM 表述并登记方案变更 | `docs(backend-rust)` |

两者互不依赖，可分别回滚。建议先 PD01 后 PD02，使 PD02 能引用 PD01 的提交号。

**不在本轮范围**：引入 `query!` 编译期检查或 `.sqlx` 离线缓存、改写任何业务 SQL、补 Rust 侧 InnoDB 引擎自检（Rust 侧本来没有，与本议题无关）、改写历史证据 JSON。

## 3. 不变约束

| 标识 | 必须保持 |
|---|---|
| PI01 | `crates/app` 源码零改动；HTTP/WS 路由、JSON、业务码、文案、SQL、22 个开关全部不变 |
| PI02 | `crates/app` 依赖版本不变：`Cargo.lock` 只允许删除 persistence 独占包，不得升级或新增任何包 |
| PI03 | 二进制名 `rust-backend`、`backend-rust/` 根、CI 工作流与制品路径不变 |
| PI04 | 历史证据（`docs/reviews/rust-migration/*.json`）不改写，只在现行文档补说明 |

## 4. 需求与验收

### PD01 删除 persistence crate

改动清单（同一提交）：

1. 删除 `backend-rust/crates/persistence/` 整个目录；
2. `backend-rust/Cargo.toml`：`members` 去掉 `crates/persistence`；`[workspace.dependencies]` 删除只被它使用的 `sea-orm`（`tokio`/`uuid`/`rust_decimal` app 仍在用，保留）；
3. `backend-rust/Cargo.lock`：随 workspace 缩小重新生成，预计从 289 个包降到约 264 个（独占 25 个，含 sea-orm、sea-query、ouroboros、derive_more 等，以实际结果为准）；
4. 删除 `backend-rust/scripts/phase2-schema-rehearsal.sh`（唯一依赖 persistence 的脚本：默认输出到 `crates/persistence/src/entity` 并执行 `sea generate entity`；无其他脚本或 CI 调用它）；
5. `docs/plans/rust-migration-handoff.md:10` 的测试计数随实测更新（预期普通 79→77、串行隔离库 154→152）。

**重点风险：workspace 特性合并（feature unification）。** `cargo build` 在 workspace 根构建全部成员，依赖特性按成员合并；`sea-orm` 可能为 `sqlx`、`tokio`、`chrono`、`rust_decimal` 等共享依赖额外打开特性。删除后这些特性可能不再对 app 生效：缺特性导致编译失败可被构建发现；若某特性改变运行期行为（如解码路径），则编译能过而行为变化。因此 PD01 必须对比删除前后 app 实际生效的依赖特性。

**验收**（在 `backend-rust/` 下）：

| 标识 | 验证 | 通过条件 |
|---|---|---|
| PV01 | `git diff --stat` 与 `git diff Cargo.lock` | 仅含上述 5 项文件；`Cargo.lock` 只有删除，无版本变更或新增 |
| PV02 | 删除前执行 `cargo tree --locked --workspace -e normal,build -f '{p}\|{f}' --prefix none`（workspace 合并后的特性，即现网二进制实际生效的特性），删除后再执行同一命令，按包逐行 diff 特性集合。注意不能用 `-p rust-backend` 做前后对比：`-p` 只解析 app 自身，看不到合并效应 | 除 persistence 独占包消失外，运行期依赖的特性集合不变。2026-10-16 预检（`-p rust-backend` 对 `--workspace`）只有编译期过程宏依赖 `syn` 2.x/3.x 的特性不同，`sqlx`、`sqlx-core`、`sqlx-mysql`、`tokio`、`chrono`、`rust_decimal`、`serde_json`、`uuid` 一致；实施时以实测为准。若出现运行期依赖的特性差异，app 的 `Cargo.toml` 需显式声明以保持原状，**并上报决定后再继续**（这是对 PI01 的唯一可能触碰） |
| PV03 | `cargo fmt --all -- --check`；`cargo build --locked --release && ./scripts/smoke.sh target/release/rust-backend` | 全部通过 |
| PV04 | `cargo test --locked` 与 `cargo test --locked -- --ignored --test-threads=1`（`RUST_PHASE2_DATABASE_URL` 指向隔离库 `rust_scratch_lockdemo`） | 0 失败；计数等于删除前减去 persistence 的 2 个普通 + 2 个 ignored（预期 77 / 152），测试名集合除这 4 个外不变 |
| PV05 | `python3 -m unittest discover -s scripts -p 'test_*.py'` | 全部通过 |
| PV06 | PV02 运行期依赖无差异时，不要求重跑双栈对账；有差异并按 PV02 补声明后，须重跑 `scripts/local-dual-stack.sh` 的 `journeys` 与 `full` | 0 FAIL / 0 OPEN |
| PV07 | `git grep -n -E 'crates/persistence\|rust-persistence\|sea-orm\|phase2-schema-rehearsal' -- ':!docs/reviews'` | 仅剩 PD02 中有意保留的历史说明 |

删除 `mysql_transaction` 与 `schema_readonly` 两个 ignored 测试不丢失独有覆盖：前者只测 SeaORM 包装的回滚，app 已有以触发器注入失败、断言整体回滚的真实 MySQL 测试（如 `domain/entering/tests/writer.rs`、`domain/post_telegram/tests/writer.rs`、`domain/post_telegraph/tests/writer.rs`、`domain/post_telex/tests/writer.rs`）；后者的 NULL/Decimal 读取形态由 app 的真实 sqlx 读写测试隐含覆盖。

提交正文写明：删除原因（引用本规格）、PV02 特性对比结论、各门禁结果与计数、“未改 API/DB/开关名与 app 源码”声明。

### PD02 修正 ORM 表述

**应改（现行规格/计划）**：

| 文件:行 | 现表述 | 改为 |
|---|---|---|
| `docs/specs/2026-09-10-rust-backend-feasibility.md:72` | SeaORM 适合迁移初期 | 候选分析保留，句末注明“未采用，见文末修订” |
| 同上 `:75` | 建议以 SeaORM + 必要 SQLx 为主 | 实际采用 SQLx 显式 SQL，未使用 SeaORM |
| 同上 `:240` | 先生成实体，再逐查询对账 | 逐查询对照 Java SQL 结果 |
| 同上 `:260` | DB 起步：SeaORM | SQLx 显式 SQL |
| 同上 `:268` | 版本化 SQL 或 SeaORM migration | 版本化 SQL（沿用 `backend/database/migrations/`） |
| `docs/plans/2026-09-10-rust-full-rewrite-plan.md:75` | 目录图 `persistence/ # SeaORM entity…` | 删除该行，或整张目录图改指向结构重构后的实际布局 |
| 同上 `:96`、`:97`、`:103` | ORM/SQL/迁移选型为 SeaORM | 持久层为 SQLx 显式 SQL；保留“生产禁止自动 schema sync”与版本化 SQL |
| 同上 `:238`、`:624` | 阶段 2 生成 SeaORM entity | 注明“已生成但未接入 app，PD01 已删除” |
| 同上 `:606` | 风险“ORM 语义差异” | “SQL 语义差异：逐查询对账 + fixture” |
| `docs/plans/2026-10-15-backend-rust-structure-refactor-plan.md:40` | 实体“仅服务其自身 2 个测试” | 2 个普通 + 2 个 ignored，共 4 个 |
| 同上 §7（`:217`）与 `docs/specs/2026-10-15-backend-rust-structure-refactor-spec.md:21` | persistence 待决 / 不在本轮 | P8 标为“已决：删除”，链接本规格与 PD01 提交 |

两份 2026-09-10 文档各在文末加一段“2026-10-16 修订”：说明持久层实际方案、`persistence` 删除提交号，以及历史证据 `phase2-gate.json`、`seaorm-entity-generation.json`、`phase9-contract-fixture-result.json` 中的 `crates/persistence` 路径与 `-p rust-persistence` 命令已不可重放，需从 `d68fb9f6` 起的历史检出。

**不改**：

- `docs/specs/2026-09-10-rust-backend-feasibility.md:130`（ORM 不能自动消除 N+1）、`docs/plans/2026-09-10-rust-full-rewrite-plan.md:123`（不向 handler 泄露 ORM model）、`:154`（禁止 ORM 自动改表）：属通用原则，换成显式 SQL 仍成立；
- `docs/specs/2026-09-10-rust-backend-feasibility.md:327` 的 SeaORM 参考链接：历史参考；
- 所有指 Java 侧 Hibernate ORM 的表述；
- `docs/reviews/rust-migration/` 下的历史证据 JSON。

**验收**：`git diff` 只含上表文件；改后 `git grep -n -i seaorm -- docs ':!docs/reviews' ':!docs/*/archive'` 的命中只剩修订说明、上述“不改”项与候选分析；`docs/README.md` 文档地图收录本规格。

## 5. 已确认取舍（2026-10-16）

| 项 | 决定 | 依据 |
|---|---|---|
| `phase2-schema-rehearsal.sh` | 随 crate 删除（PD01 第 4 项） | 结构重构的“脚本文件名不变”约束只约束那一轮重构；该脚本只在历史证据 `phase2-gate.json` 里被叙述性提及，没有调用方或校验 |
| 三条通用 ORM 原则 | 不改（PD02“不改”清单） | 改成显式 SQL 后原则仍成立 |
| 105 个实体文件 | 不另行保留，需要时从 git 历史 `d68fb9f6` 找回 | 将来如需从 schema 生成类型做对账，可重新执行 `sea-orm-cli`，旧产物没有保留价值 |
