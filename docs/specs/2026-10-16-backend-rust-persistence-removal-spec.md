# backend-rust 移除 persistence crate 与 ORM 表述修正规格（2026-10-16）

> 状态：§5 取舍已于 2026-10-16 确认，规格已经过一轮评审修订，未实施。代码基线 `22ba43d8`，其后至本规格提交只有文档变更。本规格承接结构重构计划 §7 待决项 P8（`persistence` crate 运行时未被使用），给出“删除”的决定、影响面与验收。

## 1. 背景与决定

2026-09-10 的可行性规格与重写计划定的持久层方案是“SeaORM 为主，复杂/关键 SQL 用 SQLx 或 raw SQL”。实际执行中：

- `d68fb9f6`（2026-09-21）建立 `backend-rust/crates/persistence`（包名 `rust-persistence`），从 schema 生成 105 个 SeaORM 实体文件；crate 全部 `.rs` 约 2,945 行，其中实体目录约 2,746 行；
- `crates/app` 从未依赖该 crate，全部业务以 SQLx 运行时字符串查询实现：`sqlx::query`/`query_as`/`query_scalar` 调用约 1,390 处（`grep -rhoE 'sqlx::query(_as|_scalar)?(::<[^(]*>)?\(' backend-rust/crates/app/src | wc -l`），sqlx 的 `query!` 系列编译期宏 0 处（`entering` 域的 `exercise_query!` 是自定义宏，不是 sqlx 宏）；
- 仓库无“放弃 SeaORM”的决策记录，结构重构计划将其称为“既有决定”但无出处。

**决定**：持久层确认为 SQLx 显式 SQL，不采用 ORM；删除 `persistence` crate，并修正现行文档中把 SeaORM 当作 Rust 方案的表述。理由：

1. 迁移以 Java 行为逐行对账为验收，显式 SQL 可逐句对照 Java HQL/原生查询，ORM 查询构造器会引入额外翻译层；
2. 遗留 schema 有 3 张无主键表无法生成实体（`docs/reviews/rust-migration/seaorm-entity-generation.json:24`），`is_admin=0` 为管理员等反直觉字段不宜被模型掩盖；
3. schema 由 Java 侧 `backend/database/migrations/` 管理、Rust 禁止自动 DDL，ORM 的迁移与模型同步能力无用；
4. 126 处 `FOR UPDATE` 与结算事务需要精确控制锁范围；
5. 约 1,390 处查询已通过双栈对账，换 ORM 等于全部重写。

## 2. 范围

本规格只覆盖两个独立交付单元：

| 标识 | 交付单元 | 提交类型 |
|---|---|---|
| PD01 | 删除 `persistence` crate 及其专属脚本 | `refactor(backend-rust)` |
| PD02 | 修正现行文档的 ORM 表述并登记方案变更 | `docs(backend-rust)` |

两者互不依赖，可分别回滚。先 PD01 后 PD02，使 PD02 能引用 PD01 的提交号。

**不在本轮范围**：引入 `query!` 编译期检查或 `.sqlx` 离线缓存、改写任何业务 SQL、补 Rust 侧 InnoDB 引擎自检（Rust 侧本来没有，与本议题无关）、改写历史证据 JSON。

**对结构重构约束的例外**：结构重构规格 I05 与计划 §1 把“`scripts/` 目录与文件名不变”列为不变量，理由是与迁移证据路径绑定。本规格删除 `backend-rust/scripts/phase2-schema-rehearsal.sh`，是对该约束的显式例外，理由见 §5。

## 3. 不变约束

| 标识 | 必须保持 |
|---|---|
| PI01 | `backend-rust/crates/app/src/**` 零改动；HTTP/WS 路由、JSON、业务码、文案、SQL、22 个开关全部不变。`crates/app/Cargo.toml` 默认不改，只在 PV02 发现运行期依赖特性差异、并经上报决定后，才允许补特性声明 |
| PI02 | 剩余依赖不升级、不新增：删除前后 `Cargo.lock` 的“包名 + 版本”集合只能减少 persistence 独占包。允许 Cargo 因版本消歧改写依赖名写法（例如 heck 只剩 0.5.0 一个版本后，`sqlx-macros-core` 依赖列表中的 `"heck 0.5.0"` 变为 `"heck"`） |
| PI03 | 二进制名 `rust-backend`、`backend-rust/` 根、CI 工作流与制品路径不变 |
| PI04 | 历史证据（`docs/reviews/rust-migration/*.json`）不改写，只在现行文档补说明 |

## 4. 需求与验收

### PD01 删除 persistence crate

改动清单（同一提交）：

1. 删除 `backend-rust/crates/persistence/` 整个目录；
2. `backend-rust/Cargo.toml`：`members` 去掉 `crates/persistence`；`[workspace.dependencies]` 删除只被它使用的 `sea-orm`（`tokio`/`uuid`/`rust_decimal` app 仍在用，保留）；
3. `backend-rust/Cargo.lock`：执行 `cargo update --workspace --offline` 修剪（只更新 workspace 成员条目、删除不再被引用的包，不升级第三方依赖）。评审模拟结果为 289 → 265 个包，删除 24 个：aliasable、async-stream、async-stream-impl、async-trait、derive_more、derive_more-impl、heck 0.4.1、inherent、ordered-float、ouroboros、ouroboros_macro、proc-macro2-diagnostics、rust-persistence、rustc_version、sea-bae、sea-orm、sea-orm-macros、sea-query、sea-query-binder、semver、static_assertions、strum、unicode-xid、yansi。以实测为准，但必须满足 PI02；
4. 删除 `backend-rust/scripts/phase2-schema-rehearsal.sh`（唯一依赖 persistence 的脚本：默认输出到 `crates/persistence/src/entity` 并执行 `sea generate entity`；无其他脚本或 CI 调用它）；
5. `docs/plans/rust-migration-handoff.md:10`：保留原有“普通 Rust 79、串行隔离库 154”历史计数，在同一行追加“2026-10-16 删除 persistence 后：普通 77、串行隔离库 152（PD01 提交号）”。该文件其他行的 79 等计数属于历史记录，不改。

**重点风险：workspace 特性合并（feature unification）。** 在 workspace 根执行 `cargo build`/`cargo test`（CI 即如此，见 `.github/workflows/rust-backend.yml`）会构建全部成员，共享依赖的特性按成员合并；`sea-orm` 可能为 `sqlx`、`tokio`、`chrono`、`rust_decimal` 等额外打开特性。删除后这些特性可能不再对 app 生效：缺特性导致编译失败可被构建发现；若某特性改变运行期行为，则编译能过而行为变化。

已知事实：`backend-rust/scripts/local-dual-stack.sh:55` 用 `cargo build --locked -p rust-backend` 构建，不含 workspace 合并，所以历次双栈对账验证的是“app 单独解析”的特性集，而 CI 发布产物用的是合并后的特性集。评审模拟显示删除后 `--workspace` 的特性集与删除前 `-p rust-backend` 完全一致，即删除会让发布产物的特性集与双栈对账验证过的特性集重合。

**执行顺序与验收**：

在动任何文件之前，先在 `backend-rust/` 下保存基线（`$B` 为 scratch 目录，不入库）：

```bash
norm() { sed 's/ (\*)$//; s/ (proc-macro)$//' | sort -u; }
cargo tree --locked --workspace   -e normal,build,dev -f '{p}|{f}' --prefix none | norm > "$B/feat-ws-before.txt"
cargo tree --locked -p rust-backend -e normal,build,dev -f '{p}|{f}' --prefix none | norm > "$B/feat-app-before.txt"
cargo test --locked -- --list           2>/dev/null | grep ': test$' | sort > "$B/tests-before.txt"
cargo test --locked -- --list --ignored 2>/dev/null | grep ': test$' | sort > "$B/ignored-before.txt"
awk '/^name = /{n=$3} /^version = /{print n, $3}' Cargo.lock | sort > "$B/lock-before.txt"
```

然后完成改动清单 1–4，执行 `cargo update --workspace --offline`，再逐项验收：

| 标识 | 验证（`backend-rust/` 下执行，命令见表后） | 通过条件 |
|---|---|---|
| PV01 | 改动范围与 lock 集合 | 暂存区（提交前 `git diff --cached --stat`）只含改动清单 5 项涉及的路径；`lock-before` 与 `lock-after` 的差集只有被删除的包，没有新增或版本变化 |
| PV02 | 特性对比 | `feat-ws-after` 与 `feat-ws-before` 相比，除 persistence 独占包消失外，只允许编译期过程宏依赖（如 `syn`）的特性变化；同时 `feat-ws-after` 应与 `feat-app-before` 一致。出现运行期依赖的特性差异时，停止并上报，由决定是否在 `crates/app/Cargo.toml` 补声明（PI01） |
| PV03 | 格式、发布构建与 smoke | `cargo fmt --all -- --check`、`cargo build --locked --release && ./scripts/smoke.sh target/release/rust-backend` 全部通过 |
| PV04 | 测试与测试名集合 | `cargo test --locked` 与 `cargo test --locked -- --ignored --test-threads=1` 均 0 失败，计数 77 / 152；`tests-*`、`ignored-*` 前后 diff 只含 persistence 的 4 个测试：`repository_context_type_is_cloneable`、`page_size_is_bounded_and_offset_cannot_overflow`、`transaction_rolls_back_without_leaving_fixture_rows`、`read_only_fixture_preserves_pagination_null_and_decimal_shapes` |
| PV05 | Python 脚本测试 | `python3 -m unittest discover -s scripts -p 'test_*.py'` 全部通过 |
| PV06 | 双栈对账 | PV02 无运行期依赖差异时不重跑。若按 PV02 补了声明，须用 `scripts/local-dual-stack.sh` 重跑 `journeys` 与 `full`，0 FAIL / 0 OPEN |
| PV07 | 残留引用（**在仓库根执行**） | 命中为 0。文档中的引用由 PD02 处理，不在本项范围 |

```bash
# PV01：提交前
git diff --cached --stat
awk '/^name = /{n=$3} /^version = /{print n, $3}' Cargo.lock | sort > "$B/lock-after.txt"
comm -13 "$B/lock-before.txt" "$B/lock-after.txt"   # 必须为空（无新增/无版本变化）
comm -23 "$B/lock-before.txt" "$B/lock-after.txt"   # 只能是 persistence 独占包

# PV02
cargo tree --locked --workspace -e normal,build,dev -f '{p}|{f}' --prefix none | norm > "$B/feat-ws-after.txt"
diff "$B/feat-ws-before.txt" "$B/feat-ws-after.txt"
diff "$B/feat-app-before.txt" "$B/feat-ws-after.txt"

# PV04：测试名
cargo test --locked -- --list           2>/dev/null | grep ': test$' | sort > "$B/tests-after.txt"
cargo test --locked -- --list --ignored 2>/dev/null | grep ': test$' | sort > "$B/ignored-after.txt"
diff "$B/tests-before.txt" "$B/tests-after.txt"; diff "$B/ignored-before.txt" "$B/ignored-after.txt"

# PV07：仓库根
git grep -n -i -E 'crates/persistence|rust-persistence|sea-orm|sea_orm|seaorm|phase2-schema-rehearsal' -- . ':!docs'
```

PV04 的 ignored 一轮需要隔离库 `rust_scratch_lockdemo`（按 `backend/database/project006.sql` 导入），`RUST_PHASE2_DATABASE_URL` 必须以 `/rust_scratch_lockdemo` 结尾（`domain/entering/tests/writer.rs:34` 有断言）。环境准备沿用 `docs/plans/rust-migration-handoff.md` 与 `scripts/local-dual-stack.sh setup`。

**删除的两个 ignored 测试的覆盖情况**：

- `mysql_transaction` 只测 persistence 自己封装的 `RepositoryContext::transaction` 回滚，app 不使用该封装。app 中以触发器注入失败、断言整体回滚的真实 MySQL 测试覆盖更强（`domain/entering/tests/writer.rs`、`domain/post_telegram/tests/writer.rs`、`domain/post_telegraph/tests/writer.rs`、`domain/post_telex/tests/writer.rs`）。这些测试都是 ignored，CI 的 `cargo test --locked` 不跑，只在门禁的隔离库一轮执行；删除前后这一点不变。
- `schema_readonly` 对 `t_grading_rule`、`general_ticker_pat_train_user` 做 `Option<i32>`/`Option<Decimal>`/`Option<String>` 解码。app 没有逐项等价的单元测试，这类 NULL/Decimal 读取形态由 app 实际查询路径与双栈对账承担。删除后不再有该专项测试，接受这一点，因为它验证的是 app 并不使用的 SeaORM 映射层。

提交正文写明：删除原因（引用本规格）、PV02 特性对比结论、PV01–PV05 结果与计数、对结构重构 I05 的例外、“未改 API/DB/开关名与 app 源码”声明。

### PD02 修正 ORM 表述

**应改**（行号为本规格提交时的位置，实施时以原文定位）：

| 文件:行 | 现表述 | 改为 |
|---|---|---|
| `docs/specs/2026-09-10-rust-backend-feasibility.md:72` | SeaORM 适合迁移初期 | 候选分析保留，句末注明“未采用，见文末修订” |
| 同上 `:75` | 建议以 SeaORM + 必要 SQLx 为主 | 实际采用 SQLx 显式 SQL，未使用 SeaORM |
| 同上 `:240` | 先生成实体，再逐查询对账 | 逐查询对照 Java SQL 结果 |
| 同上 `:260` | DB 起步：SeaORM | SQLx 显式 SQL |
| 同上 `:268` | 版本化 SQL 或 SeaORM migration | 版本化 SQL（沿用 `backend/database/migrations/`） |
| `docs/plans/2026-09-10-rust-full-rewrite-plan.md:75` | 目录图 `persistence/ # SeaORM entity…` | 删除该行，并在目录图下加一句“实际布局见结构重构规格” |
| 同上 `:96`、`:97`、`:103` | ORM/SQL/迁移选型为 SeaORM | 持久层为 SQLx 显式 SQL；保留“生产禁止自动 schema sync”与版本化 SQL |
| 同上 `:238`、`:624` | 阶段 2 生成 SeaORM entity | 注明“已生成但未接入 app，PD01 已删除” |
| 同上 `:606` | 风险“ORM 语义差异” | “SQL 语义差异：逐查询对账 + fixture” |
| `docs/plans/2026-10-15-backend-rust-structure-refactor-plan.md:23` | 不换 ORM、不删 `persistence` crate，既有决定 | 不换 ORM 仍成立；persistence 已决删除，见本规格 |
| 同上 `:40` | 实体“仅服务其自身 2 个测试” | 2 个普通 + 2 个 ignored，共 4 个 |
| 同上 §7 `:217` | P8 待决 | 标为“已决：删除”，链接本规格与 PD01 提交 |
| `docs/specs/2026-10-15-backend-rust-structure-refactor-spec.md:21` | 不在本轮范围：删除或启用 persistence | 原文不改（那一轮确实不在范围），句末追加“后续见 2026-10-16 persistence 移除规格” |

两份 2026-09-10 文档各在文末加一节，标题固定为 `## 2026-10-16 修订：持久层方案`，内容为：持久层实际方案；`persistence` 删除提交号；以下历史证据中的路径与命令已不可在 HEAD 重放，需从 `d68fb9f6` 起的历史检出：

- `phase2-gate.json` 的 `crates/persistence` 路径，以及 `evidence` 数组中的 `backend-rust/scripts/phase2-schema-rehearsal.sh`；
- `seaorm-entity-generation.json` 的实体输出目录；
- `phase9-contract-fixture-result.json` 的 `cargo test --locked -p rust-persistence -- --ignored`。

**不改**：

- `docs/specs/2026-09-10-rust-backend-feasibility.md:130`（ORM 不能自动消除 N+1）、`docs/plans/2026-09-10-rust-full-rewrite-plan.md:123`（不向 handler 泄露 ORM model）、`:154`（禁止 ORM 自动改表）：属通用原则，换成显式 SQL 仍成立；
- `docs/specs/2026-09-10-rust-backend-feasibility.md:327` 的 SeaORM 参考链接：历史参考；
- 所有指 Java 侧 Hibernate ORM 的表述；
- `docs/reviews/rust-migration/` 下的历史证据 JSON。

**验收**（仓库根执行）：

```bash
git grep -n -i -E 'crates/persistence|rust-persistence|sea-orm|sea_orm|seaorm|phase2-schema-rehearsal' -- docs \
  ':!docs/reviews' ':!docs/*/archive/*' ':!docs/specs/2026-10-16-backend-rust-persistence-removal-spec.md'
```

每条命中必须属于以下之一，逐条列入提交正文：

1. 两份 2026-09-10 文档中 `## 2026-10-16 修订：持久层方案` 节内的行；
2. 可行性规格 `:72` 的候选分析行（已注明未采用）与 `:327` 的参考链接；
3. 结构重构计划 `:23`、`:40`、`:217` 修改后的行（作为决策记录保留 persistence/SeaORM 字样）；
4. `docs/README.md` 中本规格的文档地图条目（文件名含 `rust-persistence`）。

另外：`git diff --cached --stat` 只含上表文件、两份文档的修订节及 `docs/README.md`；`docs/README.md` 中本规格条目与本规格顶部状态改为“已实施”，并附 PD01、PD02 提交号。

## 5. 已确认取舍（2026-10-16）

| 项 | 决定 | 依据 |
|---|---|---|
| `phase2-schema-rehearsal.sh` | 随 crate 删除（PD01 第 4 项），作为对结构重构 I05 的显式例外（§2） | 该脚本列在历史证据 `phase2-gate.json` 的 `evidence` 数组中，删除后这条证据路径在 HEAD 悬空。没有任何脚本调用或校验它，而它所服务的 SeaORM 实体生成已不再使用。按 PI04 不改写 JSON，在 PD02 修订节中声明 |
| 三条通用 ORM 原则 | 不改（PD02“不改”清单） | 改成显式 SQL 后原则仍成立 |
| 105 个实体文件 | 不另行保留，需要时从 git 历史 `d68fb9f6` 找回 | 将来如需从 schema 生成类型做对账，可重新执行 `sea-orm-cli`，旧产物没有保留价值 |
