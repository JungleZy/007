# backend-rust/AGENTS.md

Java 后端（`backend/`）的 Rust 迁移，面向 AI 编码代理的命令、约定与红线。全仓通用规则（提交约定、跨栈契约、文档位置）见根 [`AGENTS.md`](../AGENTS.md)。**每次接手先读 [`docs/plans/rust-migration-handoff.md`](../docs/plans/rust-migration-handoff.md)**：它记录当前阶段、已拍板口径与下一步，本文只放稳定规则。

## 定位与边界

- 终态是全量替换 Java（[`docs/plans/2026-09-10-rust-full-rewrite-plan.md`](../docs/plans/2026-09-10-rust-full-rewrite-plan.md)）。Phase 1–9 已完成；**Phase 10（下线 Java）阻断，生产判定 NO-GO**。Java 仍是生产后端与回滚基线，`backend/` 及其 CI 不得删除或降级。
- 迁移提交在分支 `ci/rust-native-acceptance`。**未经用户明确批准，不合并 main、不打 tag、不发 release、不改生产流量、不停 Java。**
- 本地验证（Wine/QEMU、本机双栈）不能冒充 native CI、真实设备或客户现场验收；不拿历史计数充当本轮结果。

## 工程布局

- workspace：`crates/app`（包 `rust-backend`：lib `rust_backend` + bin `rust-backend`）；`crates/persistence`（SeaORM 实体，app 从未依赖，已有删除规格 [`docs/specs/2026-10-16-backend-rust-persistence-removal-spec.md`](../docs/specs/2026-10-16-backend-rust-persistence-removal-spec.md)，取舍已确认、未实施）。**持久层是 SQLx 显式 SQL，不引入 ORM。**
- `crates/app/src/`：`main.rs` 只调 `rust_backend::run()`；`runtime.rs` 负责装配、监听、恢复任务；`router.rs` 按开关组装路由；`platform/` 放配置、开关、状态、鉴权、信封、错误、参数解析、健康检查；`shared/` 放跨域纯逻辑；`ws/` 是 WebSocket；`domain/<域>/` 是 21 个业务域，一个功能开关对应一个域，各自带 `routes()`、handler、SQL 和 `tests/`。
- 结构规则来自 [`docs/specs/2026-10-15-backend-rust-structure-refactor-spec.md`](../docs/specs/2026-10-15-backend-rust-structure-refactor-spec.md)（SR01–SR08），新增代码必须遵守：
  - 库只对 bin 公开 `pub async fn run()`，其余默认私有，库内跨模块用 `pub(crate)`；不写 `pub mod`、兼容 re-export、`#[path]` 或 shim。
  - 新域在 `domain/<域>/mod.rs` 提供 `pub(crate) fn routes(state: &AppState) -> Router<AppState>`，由 `router.rs` 按开关 `merge`。
  - 业务域调 WS 只经 `ws::notify`。业务域之间不直接引用，唯一例外是 `post_ticker → ticker_tape::{SettingRequest, settings_connection}`；新的共享逻辑放 `shared/`。
  - 非测试文件不超过 1500 行。

## 构建与测试

在 `backend-rust/` 下执行：

```bash
cargo fmt --all -- --check
cargo test --locked
cargo build --locked --release && ./scripts/smoke.sh target/release/rust-backend
python3 -m unittest discover -s scripts -p 'test_*.py'
# 隔离库测试：需 Docker MySQL 中已建好的 rust_scratch_lockdemo
: "${RUST_PHASE2_DATABASE_URL:?须指向 .../rust_scratch_lockdemo}"
cargo test --locked -- --ignored --test-threads=1
# 双栈对账（提交前）
S=scripts/local-dual-stack.sh
$S build && $S up && $S journeys && $S full   # 两者 0 FAIL / 0 OPEN
$S down
```

- 改动迭代时只跑受影响的测试；提交前跑上面整套门禁。不要 `--skip` 或删测试凑绿。
- `#[ignore]` 测试共用一个隔离库，必须 `--test-threads=1`，URL 必须以 `/rust_scratch_lockdemo` 结尾（测试里有断言）；绝不指向共享库或生产库。
- `local-dual-stack.sh` 子命令：`setup`（新机器：Docker 容器 `mysql-project006`、dev 快照、全部迁移、隔离库）、`schema`、`build`、`up`/`down`（Java :18121、Rust :18122，开启全部开关）、`journeys [文件]`、`full`。依赖 Docker、`$HOME/.local/opt/jdk21` 和 cargo。容器停了先 `docker start mysql-project006`。
- CI（`.github/workflows/rust-backend.yml`，push main/master 与 PR 触发）：Linux x64 跑 fmt、测试、Python 单测、构建和 smoke；另有原生 Linux arm64、Windows GNU 构建，以及 Windows 桌面验收。该工作流不发布任何东西，tag 发版链仍打 Java。

## 运行时关键事实

- 默认监听 `127.0.0.1:18002`（`RUST_BACKEND_ADDR`），Java 是 18001。业务路径与 Java 相同（`/api/...`）；`/health`、`/live`、`/ready` 在根路径。
- 配置：`RUST_BACKEND_DATABASE_URL`（缺省时无库启动，`/ready` 报 `process-only`）、`RUST_BACKEND_MAX_CONNECTIONS`、`RUST_BACKEND_DB_TIMEOUT_SECS`、`RUST_BACKEND_BODY_LIMIT_BYTES`、`RUST_BACKEND_CORS_ORIGIN`，见 `platform/config.rs`。
- **22 个功能开关** `RUST_PHASE<n>_<域>_ENABLED` 定义在 `platform/flags.rs`，默认全关，关闭时对应路由不挂载。`local-dual-stack.sh`、`phase9-contract-manifest.json` 和 CI 桌面验收脚本都从 `flags.rs` 读取，不要把扫描源改回 `main.rs`。
- `/api/postTrainGlobalRule/**` 在 general、radiotelephone、entering 三个开关任一开启时挂载，不能按文件归属收窄。
- `RUST_PHASE7_GENERAL_ENABLED=true` 时，启动即创建写库的 general 训练恢复任务：首次立即执行，之后每 5 秒一次。它的唯一所有者是 `runtime::run`，`axum::serve` 返回后先 `abort()` 再传播错误；`build_state`、`build_router` 和各域 `routes` 不得启动后台任务。全开 Rust 会立即写库，所以网关分流本身不能证明 Java 和 Rust 只有一方在写。
- WS 会话态在进程级全局 `ws/manager.rs` 的 `MANAGER` 里，只支持单实例；测试靠共享助手清零，不能并行。

## 红线

1. **与 Java 行为逐请求对齐**：方法、路径及别名、参数名、JSON 形态、业务码、文案、HTTP 状态不改。偏离 Java 只能是刻意差异，并且必须登记在 `scripts/dual-stack-sweep-registry.json`，不允许把差异藏在对账噪音里。
2. **信封与码值同 Java**：已定义的业务结果用 HTTP 200 信封，未处理故障用 HTTP 500 + code 500；203/204/206 是客户端契约，207 表示无权限，208 表示终态。状态类错误按 Java 返回 202。Java 的 500 一般不照搬，改返回定义好的 202 并登记；例外以 handoff「已拍板口径」为准。
3. **鉴权**：REST 的 `token` 与 `deviceId` 只读请求头，WS 在握手时读 query；不新增 query 回退。`t_role.is_admin = 0` 才是管理员（与根 AGENTS 红线 7 相同）。
4. **数据与 schema**：不改表结构、不加索引、禁止自动 DDL，schema 以 `backend/database/migrations/` 为准。写路径用显式事务；锁顺序是先锁父行或训练行主键，再按主键写子行；读接口不加 `FOR UPDATE`。不复制会写脏数据的 Java 行为；Java 的计分缺陷照抄，并登记为已知缺陷。
5. **对外标识不可改**：开关名、二进制名 `rust-backend`、目录 `backend-rust/`、`scripts/` 下的文件名，以及 CI 制品路径，都被脚本、manifest 和迁移证据按名引用。
6. **判断题停下上报**，不自行决定：行为口径、是否复制 Java 缺陷、方案取舍都属于判断题。用户拍板后写进 handoff「已拍板口径」。
7. **结构搬迁与业务修复不交错**：同一提交只做一类。改共享双栈或对账库时，同一时间只有一个执行者。

## 提交约定

沿用根 AGENTS「提交约定」，另加两条：

- 双栈批次拆两个提交：`fix(...)` 放代码和回归测试，`test(rust-contract)` 放旅程和 registry。
- 结构类提交用 `refactor(backend-rust): …`，正文写范围、前置提交、门禁结果，并声明「未改 API/DB/开关名」。
