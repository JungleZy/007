# backend-rust 目录结构重构计划

> 状态：实施中，第 4 步代码与门禁已完成，第 5 步候选待集成。设计计数基线：`d1ccee6f`（2026-10-15）；执行起点：`5c1200dd`。当前进度与实际证据见 §9，不以历史计数代替验收。

规格与验收：[docs/specs/2026-10-15-backend-rust-structure-refactor-spec.md](../specs/2026-10-15-backend-rust-structure-refactor-spec.md)。本文负责文件搬迁、实施顺序与执行记录；SR01–SR08、V01–V05 的需求和验收口径以配套规格为准，调整时同步两份文档。

## 1. 目标与边界

把 `backend-rust/crates/app` 从“70 个平铺文件 + 巨型 `main.rs`”重排为“平台层 + 按功能开关划分的业务域”，使：

- 每个业务域自带路由、handler、SQL 与测试，增删一个域不必改 `main.rs`；
- 业务模块不再反向依赖二进制根（`super::AppState` / `crate::now_time_millis`）；
- 单文件规模回到可审阅范围（目标 ≤1500 行，测试文件除外）。

**不变量（每一步都必须成立）**：

| 不改 | 原因 |
|---|---|
| HTTP/WS 路由、请求/响应 JSON、业务码与文案 | 双栈对账基线；AGENTS 红线 5 |
| DB schema 与 SQL 语义 | 只搬运，不改写查询 |
| `RUST_PHASE*_ENABLED` 等环境变量名 | `phase9-contract-manifest.json`、`phase9-cutover-rehearsal.py`、`smoke.sh`、`platform-smoke.py` 与 `docs/reviews/rust-migration/*` 证据按名引用 |
| 二进制名 `rust-backend`、目录 `backend-rust/` | `.github/workflows/rust-backend.yml` 三平台产物路径 |
| 不换 ORM、不删 `persistence` crate | 既有决定；见 §7 待决项 |
| `scripts/` 目录与文件名 | 与迁移证据路径绑定 |
| 后台任务的启动条件、周期与退出清理 | general 训练恢复任务属于运行行为，不能只迁移 HTTP 路由而漏迁生命周期 |

本计划只做移动、拆分和必要接线、可见性调整，不夹带行为修复；错误类型与转信封逻辑原样保留，不引入统一错误 trait。搬运中发现的缺陷另行登记。

## 2. 现状问题（实测证据）

| # | 问题 | 证据 |
|---|---|---|
| P1 | 业务文件全平铺 | `crates/app/src/` 70 个 `.rs`，以 `post_*`/`telex*`/`theory*` 前缀模拟分组 |
| P2 | `main.rs` 职责过载 | 2808 行：`AppConfig`/`AppState`/`AppError`/`Envelope`、`authenticate`/`require_admin`/`token_digest`、用户/登录/菜单/角色/tools handler、`parse_*` 工具、`build_router`（388 处 `.route(`、22 个开关块）、`init_tracing`/`shutdown_signal`、general 训练恢复任务的启动与退出清理、180 行测试 |
| P3 | 业务模块依赖二进制根 | 如 `cable.rs:12 use super::{envelope_json, envelope_response, AppState}`；`crate::now_time_millis` 11 处引用 |
| P4 | 超大文件 | `general_train.rs` 4009（key 21 / telex 16 / ticker 12 个函数）、`ws.rs` 3198、`post_telegram_write.rs` 2276、`post_telegraph_write.rs` 1587、`exam.rs` 1549 |
| P5 | 拆分轴不一致 | 按读写拆（`post_telex.rs`+`post_telex_write.rs`）、按功能拆（`telex.rs`+`telex_capture.rs`+`telex_handle.rs`）、`#[path]` 嵌套（`telegram_write.rs:12` 挂 `telegram_lifecycle.rs`）并存 |
| P6 | 测试三种放法，含按迁移批次命名 | 11 处 `#[path = "*_writer_tests.rs"]`；`b8_writer_tests`/`prejob_writer_tests`/`radio_military_writer_tests` 挂在 `main.rs`；`post_ticker/` 目录只装一个测试文件 |
| P7 | 跨域共享逻辑寄居在业务文件 | 见 §4 |
| P8 | `persistence` crate 运行时未被使用 | `crates/app/Cargo.toml` 无该依赖，业务全部裸 `sqlx`；约 100 个 SeaORM 实体仅服务其自身 2 个测试 |
| P9 | 外部硬编码 `main.rs` | `scripts/local-dual-stack.sh:63` 扫描 `main.rs` 收集开关；`phase9-contract-manifest.json` sources 列 `main.rs`；`dual-stack-sweep.py:35` 注释 |
| P10 | 错误类型分散 | 11 个独立 `enum *Error`（`main.rs`、`comprehensive.rs`、`entering.rs`、`exam.rs` 等），平台启动错误与域业务错误并存；本轮仅原样搬迁，归一另行评估 |

## 3. 目标结构

**划分原则：一个功能开关 = 一个域目录 = 一个路由入口。** 开关是部署、灰度和回滚的单位，`build_router` 也已按开关分块；按开关切能让第 2 步成为机械搬运。注意开关边界与“岗位/基础”命名不完全一致（`post_entering` 在 entering 开关下，`post_military`/`post_radiotelephone` 在 radiotelephone 开关下），以开关为准，不另加 `basic/`、`post/` 层级。

基线实跑前核查补充：`postTrainGlobalRule` 三条路由由 general、radiotelephone、entering 任一开关开启时挂载（原 `main.rs:815–831`），是上述原则的现有复合开关例外。业务实现仍归 general 域；总路由保留 OR 判断，单独合并其规则子模块入口一次，不收窄可达性、不重复注册。

```text
crates/app/src/
├── main.rs              # ≤60 行：#[tokio::main] + rust_backend::run().await
├── lib.rs               # 模块树根；唯一对 bin 的 pub async fn run()，保留启动错误日志与 exit(1)
├── runtime.rs           # init_tracing / 配置与状态装配 / serve / 恢复任务 JoinHandle / shutdown_signal
├── router.rs            # build_router(state)；if flags.x { api = api.merge(domain::x::routes(&state)) }
├── platform/
│   ├── config.rs        # AppConfig
│   ├── flags.rs         # 22 个 RUST_*_ENABLED 常量表（脚本扫描的唯一来源）
│   ├── state.rs         # AppState / build_state
│   ├── envelope.rs      # Envelope / envelope_json / envelope_response / openapi 兜底
│   ├── error.rs         # 原有 AppError；不归一域错误类型或转信封逻辑
│   ├── auth.rs          # authenticate / require_admin / lookup_user / AuthUser / token_digest
│   ├── extract.rs       # parse_bool/u32/u64/usize / header_text / parse_socket_addr
│   ├── clock.rs         # now_time_millis
│   ├── health.rs        # health / live / ready / refresh_database_readiness / probes
│   └── http.rs          # not_found / panic_response / request_id / CORS 层
├── shared/              # 跨域纯逻辑，不含 handler
│   ├── scoring.rs  statistical.rs  bean_conversion.rs  telex_capture.rs
│   └── …                # 第 5 步从业务域下沉的函数，见 §4
├── domain/
│   └── <域>/
│       ├── mod.rs       # pub(crate) fn routes(state: &AppState) -> Router<AppState>（含 admin 子路由）
│       ├── *.rs         # handler / service / SQL，按子资源分文件
│       └── tests/       # mod.rs + writer.rs 等；取代 #[path]
└── ws/
    ├── mod.rs           # routes(&state)（根路径及 /api 别名）、open/serve/dispatch
    ├── manager.rs       # 连接/房间/generation guard、lock_room_lifecycle
    ├── status.rs  classic.rs  union.rs  simulation.rs  general.rs
    ├── notify.rs        # 供业务域调用的 notify_*/close_*/send_classic* 出口
    └── tests/
```

### 3.1 文件归属表

| 开关 | 目标目录 | 现文件 |
|---|---|---|
| `RUST_PHASE2_TOOLS_ENABLED` | `domain/tools/` | `main.rs` 中 `tools_get_now_time`、`tools_get_twelve_month`、`twelve_month_data` |
| `RUST_PHASE3_AUTH_ENABLED` | `domain/system/` | `main.rs` 中 user/login/menu/role handler（`user_login`、`user_signin`、`user_logout`、`change_password`、`verify_*`、`login_rejected`、`load_menu_tree`、`menu_*`、`role_get_all`、`user_get_by_token`、`valid_account`、`registration_birthday`）；`admin.rs`、`admin_writer_tests.rs`、`password.rs`、`user_training_stats.rs` |
| `RUST_PHASE4_CABLE_ENABLED` | `domain/cable/` | `cable.rs` |
| `RUST_PHASE4_DEVICE_ENABLED` | `domain/device/` | `device.rs` |
| `RUST_PHASE4_GRADING_ENABLED` | `domain/grading/` | `grading.rs` |
| `RUST_PHASE4_EQUIPMENT_ENABLED` | `domain/equipment/` | `equipment.rs`、`equipment_train.rs` |
| `RUST_PHASE5_THEORY_ENABLED` | `domain/theory/` | `theory.rs`、`theory_detail.rs`、`theory_file.rs`、`theory_question.rs`、`theory_test.rs`、`question_level.rs`、`test_paper.rs`、`exam.rs`、`exam_writer_tests.rs`、`comprehensive.rs`、`b8_writer_tests.rs` |
| `RUST_PHASE6_ENTERING_ENABLED` | `domain/entering/` | `entering.rs`、`entering_codebook.rs`、`input_writer_tests.rs`、`post_entering.rs` |
| `RUST_PHASE6_RADIOTELEPHONE_ENABLED` | `domain/radiotelephone/` | `radiotelephone.rs`、`radio_term.rs`、`military.rs`、`military_term_data.rs`、`post_military.rs`、`post_radiotelephone.rs`、`radio_military_writer_tests.rs`、`train_rule.rs` 的 `term_*` 部分 |
| `RUST_PHASE6_RECEIVE_KEYPOINTS_ENABLED` | `domain/receive_keypoints/` | `receive_keypoints.rs`、`keypoints.rs` |
| `RUST_PHASE6_TELEGRAM_ENABLED` | `domain/telegram/` | `telegram.rs`、`telegram_write.rs`、`telegram_lifecycle.rs` |
| `RUST_PHASE6_TELEGRAPH_KEY_ENABLED` | `domain/telegraph_key/` | `telegraph_key.rs`、`synthetical.rs` |
| `RUST_PHASE6_TELEX_ENABLED` | `domain/telex/` | `telex.rs`、`telex_handle.rs` |
| `RUST_PHASE6_TICKER_TAPE_ENABLED` | `domain/ticker_tape/` | `ticker_tape.rs` |
| `RUST_PHASE7_POST_TELEGRAM_ENABLED` | `domain/post_telegram/` | `post_telegram.rs`、`post_telegram_write.rs`、`post_telegram_writer_tests.rs` |
| `RUST_PHASE7_POST_TELEGRAPH_ENABLED` | `domain/post_telegraph/` | `post_telegraph.rs`、`post_telegraph_write.rs`、`post_telegraph_writer_tests.rs` |
| `RUST_PHASE7_POST_TELEX_ENABLED` | `domain/post_telex/` | `post_telex.rs`、`post_telex_write.rs`、`post_telex_writer_tests.rs` |
| `RUST_PHASE7_POST_TICKER_ENABLED` | `domain/post_ticker/` | `post_ticker.rs`、`post_ticker/receiver_writer_tests.rs`、`masthead.rs` |
| `RUST_PHASE7_GENERAL_ENABLED` | `domain/general_train/` | `general_train.rs`、`general_aux.rs`、`general_{key,telex,ticker}_writer_tests.rs`、`train_rule.rs` 的 `postTrainGlobalRule` 部分 |
| `RUST_PHASE7_GROUP_NET_ENABLED` | `domain/group_net/` | `group_net.rs`、`group_net_write.rs`、`general_group_net_rule.rs` |
| `RUST_PHASE7_SIMULATION_ENABLED` | `domain/simulation/` | `simulation.rs`、`simulation_writer_tests.rs` |
| `RUST_PHASE8_WS_ENABLED` | `ws/` | `ws.rs`、`ws_worker_tests.rs` |
| — | `shared/` | `scoring.rs`、`statistical.rs`、`bean_conversion.rs`、`telex_capture.rs` |
| — | `runtime.rs` | `main.rs` 的 `run`、`init_tracing`、`shutdown_signal` 与 general 训练恢复任务的生命周期管理；恢复业务函数仍属 general 域 |
| — | 按被测域拆分 | `prejob_writer_tests.rs`（调用 `synthetical`/`telegraph_key`→telegraph_key，`telegram_write`→telegram，`telex`→telex） |
| — | 各模块 `tests/` | `main.rs` 的 `mod tests` |

### 3.2 运行入口与可见性

- lib 与 bin 是两个 crate。bin 只调用库根的 `pub async fn run()`；将现有 `main` 的初始化、启动错误日志与 `exit(1)` 处理移入该入口，内部调用 `runtime::run() -> Result<(), AppError>`。不向 bin 暴露 `AppConfig`、`AppState`、`AppError` 或业务模块。
- 其余模块默认私有，确需库内跨模块访问时使用 `pub(crate)`；不通过 `pub mod domain`、`pub mod platform` 或兼容 re-export 扩大公开面。
- 域路由使用 `routes(state: &AppState) -> Router<AppState>`：保留 `from_fn_with_state(state.clone(), authenticate)` 与局部 body limit 对配置的读取；域内不提前绑定 Router 状态，最终仍由 `router::build_router(state: AppState) -> Router` 统一 `.with_state(state)`。
- `build_state` 与 `build_router` 作为库内构建入口供测试复用，不另设公开 `build_app(config)`；构建 Router 本身不得启动恢复任务。后台任务只由 `runtime::run` 随服务生命周期创建和清理。

## 4. 跨域依赖处理

| 依赖（使用方 → 被用方） | 处理 |
|---|---|
| 10 个域 → `statistical`；`general_train`/`post_telegram_write`/`post_telegraph_write`/`telegram_lifecycle` → `scoring` | 直接进 `shared/` |
| `general_train`/`post_telex`/`post_telex_write`/`telex_handle` → `telex_capture` | 进 `shared/telex_capture.rs` |
| `post_ticker`/`ticker_tape` → `bean_conversion` | 进 `shared/` |
| `telex` → `post_telegram_write::generated_groups` | 下沉 `shared/` |
| `general_train` → `telex_handle::Stats` | 下沉 `shared/telex_capture.rs`（或同级 `shared/telex_stats.rs`） |
| `ticker_tape` → `entering::request_integer` | 下沉 `platform/extract.rs` |
| `post_ticker` → `ticker_tape::{SettingRequest, settings_connection}` | 保留为显式跨域 `pub(crate)` 依赖（同属纸带设置语义）；若第 5 步发现仅为数据结构，下沉 `shared/` |
| `exam`/`general_train`/`simulation`/`telegram`/`telegram_write` → `ws::*`（`send_classic*`、`notify_*`、`close_*`、`general_*`、`simulation_*`） | 收口为 `ws/notify.rs` 的公开出口，业务域只依赖该文件 |
| `train_rule.rs` 横跨 radiotelephone（`term_*`）与 general（`find_by_type`/`add_rule`/`delete_by_id`） | 按语义拆为两个文件，分属两域；general 规则路由仍由 general/radiotelephone/entering 三开关 OR 挂载，不能按文件归属收窄开关 |

第 1–4 步允许临时使用 `crate::domain::x::y` 跨域引用以保持纯移动；第 5 步清零业务域之间的直接引用（`ws/notify.rs` 与 §4 已列保留项除外）。

## 5. 分步实施

每步一个提交，提交后均须可构建并通过门禁；后一步依赖前一步，**不承诺任意撤销中间步骤**。当前步失败可撤销当前步；整体回滚按已完成步骤的依赖逆序执行（全部完成时为 5 → 4 → 3 → 2 → 1），每次回滚后重跑对应门禁。

业务函数体与 SQL 逻辑保持不变；仅允许模块/调用路径、可见性及本计划明确的路由参数、启动入口接线调整。同一时刻只有一个写代码的执行者。

### 第 1 步：lib/bin 拆分与 `platform/` 抽取

- 开工前先按第 2 步的校验口径保存当前 Rust 路由、开关归属及请求结果基线，避免第 1 步已改行为后才采样。
- 新建 `lib.rs`、`runtime.rs`，按 §3.2 闭合 lib/bin 边界；`main.rs` 只保留 Tokio 入口与库调用，P2 列出的平台项按 §3 搬入 `platform/`。
- 将现有 `run` 的启动/退出顺序整体搬入 `runtime.rs`：保留 `RUST_PHASE7_GENERAL_ENABLED` 控制的唯一恢复任务、`interval(Duration::from_secs(5))` 的首次立即 tick 与后续周期，以及 `axum::serve` 返回后、传播服务错误前的 `JoinHandle::abort()`。不把任务启动藏进 `build_state`、`build_router` 或域 `routes`，不改变恢复函数业务逻辑。
- 22 个开关名集中到 `platform/flags.rs` 常量表，`AppConfig::from_env` 改为读表。
- 业务模块 `use super::{…}` 改为 `use crate::platform::…`；此时业务文件仍平铺在 `src/`。
- **同提交同步外部引用**：`scripts/local-dual-stack.sh:63` 扫描路径改为 `platform/flags.rs`；`phase9-contract-manifest.json` sources 增加 `lib.rs`、`runtime.rs`、`platform/flags.rs`、`router.rs` 的完整仓库路径（`main.rs` 保留，内容已变）；`dual-stack-sweep.py:35` 注释。
- 校验（在 `backend-rust/` 下）：`grep -oE '"RUST_[A-Z0-9_]+_ENABLED"' crates/app/src/platform/flags.rs | sort -u | wc -l` = 22，且集合与基线一致。

### 第 2 步：拆 `build_router`

- 每个开关下的所有路由块整体搬到对应域的 `pub(crate) fn routes(state: &AppState) -> Router<AppState>`（此时可先放在平铺文件里，如 `cable.rs`）；`router.rs` 只剩组装与全局层（CORS、body limit、catch-panic、request_id、fallback）。
- 中间件顺序与 `nest`/`merge` 层级必须逐块保持，admin 子路由仍套 `require_admin`。
- **路由等价校验（一次性脚本，不入库）**：以下矩阵在第 1 步前采集基线，每步修改后重复执行；记录源码版本、实际开关配置、用例与差异结果，凭据脱敏。无凭据 diff 只证明部分路由可达性，不能替代 `journeys/full` 或证明管理员授权不变。
  - **清单**：以当前 Rust 实际注册的路由为主，记录方法、路径模板、具体参数样例、所属开关及鉴权/admin 要求；Java 的 `rest-endpoints.tsv` 与 `ws-contracts.tsv` 仅作补充核对。必须包含 `/health`、`/live`、`/ready`、`/q/openapi`、`/api/phase1/contract/*`、`/api/ws/status`，以及所有 WS 根路径和 `/api` 别名，特别是 `/startWebsocket/{sid}`、`/api/startWebsocket/{sid}`。
  - **开关矩阵**：全关、全开、22 个开关分别单独开启，共 24 组；每组显式设置全部开关，避免继承环境变量。无 DB、无凭据验证每条路由的出现/消失及错误方法响应，保证开关归属不变，不能只测全开/全关。
  - **鉴权矩阵**：全开、隔离库场景使用相同业务夹具和有效参数，覆盖无凭据、普通用户、管理员；逐条验证受保护路由的鉴权边界，管理员接口须覆盖普通用户拒绝与管理员合法调用。前后运行独立重置夹具，核对拒绝请求未写库；普通查询与管理员写接口同为 `200/203` 不算授权等价证据。
  - **WS**：对每条路径及别名执行真实 Upgrade，比较握手结果，并按连接类型用合法/非法凭据核对首帧及错误/关闭行为；需要房间/训练身份的用例使用隔离库夹具。普通 GET 可作补充，但不得用其 404 代替握手结果或判定未挂载。
  - **判定**：逐用例记录 `(开关配置, 身份, method, path, HTTP 状态, 信封 code)` 与鉴权错误文案，WS 另记帧及关闭结果；与基线差异必须为空，并核对预期路由/权限结果。漏跑、执行异常、两个非预期的 404/500 均不能算通过；业务响应内容与写入语义仍由现有 `journeys/full` 对账覆盖。

### 第 3 步：按域搬目录

- `git mv` 到 §3.1 的目标目录，补 `mod.rs`；删除全部 `#[path]`，测试移入各域 `tests/`。
- `b8_writer_tests` 并入 `domain/theory/tests/`，`radio_military_writer_tests` 并入 `domain/radiotelephone/tests/`，`prejob_writer_tests` 按被测域拆三份；文件名去掉批次前缀。
- 删除空的 `post_ticker/` 目录。

### 第 4 步：拆超大文件

- `general_train.rs` → `domain/general_train/{key,telex,ticker,common}.rs`（按现有 `key_`/`telex_`/`ticker_` 函数前缀，共用 SQL 字段表与生命周期进 `common.rs`）。
- `ws.rs` → `ws/{manager,status,classic,union,simulation,general,notify}.rs`。
- `post_telegram_write.rs`、`post_telegraph_write.rs`、`exam.rs` 等超过 1500 行的，按子资源（生命周期 / 分页提交 / 结算 / 报底生成）拆；具体切点在执行该步前列清单拍板。
- `train_rule.rs` 按 §4 拆入两个域。

第 4 步实施切点确认：

- general 的 `key`/`telex`/`ticker` 收对应前缀入口与对应结算；`common` 合并共享类型、SQL 字段、数据库和锁辅助，避免分散成多个薄模块。通用采集/分页读取归 `capture`，通用状态与完成归 `lifecycle`；生成、导入导出和恢复循环可按完整函数边界保留独立模块，所有生产文件须 ≤1500 行。
- WS 的 `status`、`classic`、`general` 按连接族独立归位，`manager` 保持管理器及锁的完整实现，`notify` 仅承接业务域出口。连接建立/serve/prepare 可放 `session`；通用 dispatch/响应构造保留路由入口模块或内聚辅助，不用转发 re-export。
- `train_rule` 的 term 写入函数及专属请求类型迁入 radiotelephone；global rule 保留 general 归属及三开关 OR。必要的域内请求/响应适配保持原语义，不引入统一错误 trait。
- `exam.rs` 在第 3 步迁出测试后为 1414 行，已满足规模目标；不为凑拆分数量改写其生产逻辑。


### 第 5 步：收共享内核

- 按 §4 下沉共享函数，清零业务域之间的直接引用。
- 只合并语义相同的工具函数；同名不同义的保持私有（如 `post_telegraph_write.rs:158` 与 `synthetical.rs:221` 两个 `parse_array` 签名与错误类型不同，不合并）。
- 错误类型及其转信封实现随所属模块原样搬迁；`platform/error.rs` 仅容纳原有 `AppError`，不引入统一 trait、不合并错误分支。**业务码、文案、HTTP 状态逐一不变**；错误体系归一移出本轮，见 §7。

## 6. 门禁（每步提交前）

```bash
cd backend-rust
cargo fmt --all -- --check
cargo build --locked --release && ./scripts/smoke.sh target/release/rust-backend   # 同 CI
cargo test --locked
# ignored 测试共用隔离库与故障注入夹具，需 Docker、已建好的 rust_scratch_lockdemo 与显式 URL
: "${RUST_PHASE2_DATABASE_URL:?须指向隔离库 rust_scratch_lockdemo}"
cargo test --locked -- --ignored --test-threads=1
python3 -m unittest discover -s scripts -p 'test_*.py'
S=scripts/local-dual-stack.sh
$S build && $S up && $S journeys && $S full   # 两者 0 FAIL / 0 OPEN
$S down
```

附加：

- 测试数量与基线一致（只搬不删）；每步通过第 2 步定义的路由/开关/身份/WS 校验矩阵，不能只检查匿名请求 diff。
- 第 1 步及任何触及 `runtime.rs`、general 恢复逻辑归属的步骤，使用隔离库运行场景核验恢复任务开/关、恢复状态变化及服务退出清理；对照首次 tick、周期和句柄归属，确认仅构建 Router 不启动任务。HTTP 上线不等于恢复任务已验证。
- 提交信息：`refactor(backend-rust): …`，正文写本步范围、依赖的前置提交、门禁结果，以及“未改 API/DB/开关名”声明。

## 7. 待决项

| 项 | 选项 | 建议 |
|---|---|---|
| `persistence` crate（P8） | A. 维持现状；B. app 依赖它，作为各域 SQL 的落点；C. 删除 | 本计划不处理；结构重构完成后单独评估 |
| 开关名（`RUST_PHASE*` → 按域命名） | 改名需同步脚本、manifest、证据文档 | 不改；目录已按域，开关名只作为外部标识 |
| 域内统一 `handlers/service/repo` 三层 | 现有代码 handler 与 SQL 交织 | 本计划只按子资源拆文件；分层另立计划，避免移动与改写混在同一提交 |
| 错误类型与转信封 trait（P10） | 保留现有实现；或另行设计归一 | 本轮原样搬迁；归一需单独评估错误映射与行为回归，不混入目录重构 |

## 8. 风险

| 风险 | 缓解 |
|---|---|
| 漏挂/错挂路由、挂错开关、中间件顺序变化 | 第 2 步的实际路由清单、24 组开关配置、身份矩阵及真实 WS 握手校验 + `journeys/full` 对账 |
| 第 1 步漏改脚本，导致 `$S up` 少开开关、对账假绿 | 同提交改脚本，并校验开关计数 = 22 |
| 可见性放宽成 `pub` 扩大耦合，或 lib/bin 边界无法编译 | 按 §3.2 仅公开库启动入口；内部默认私有，必要时 `pub(crate)`，第 5 步清理跨域引用 |
| 大文件拆分时混入逻辑改动 | 按 §5 限定接线调整范围，错误映射不归一；评审按 `git diff -M --stat` 与移动检测确认 |
| 搬运与业务修复并行冲突 | 重构期间冻结 `backend-rust` 业务改动（B8 修复排在重构前或后，不交错） |
| 搬启动代码时遗漏、重复启动或未清理恢复任务 | `runtime.rs` 唯一持有恢复任务句柄；按 §6 核验运行生命周期，禁止构建 Router 时启动任务 |
| 撤销中间步骤破坏后续路径/模块依赖 | 当前步失败回退当前步；整体按已完成步骤逆序回滚，不承诺任意单步 revert |

## 9. 执行记录（2026-10-16）

### 9.1 执行基线与校准

- 从干净提交 `5c1200dd244d30470ada5a94ed2e75834ce6e675` 开始；用该提交的独立归档源码及其编译期码表构建原始 release 二进制，补采行为基线，不把重构后代码当成基线。
- Rust AST 解析得到 **386 条实际注册路由、22 个开关、360 条带 authenticate 的路由**。原 `main.rs` 中 388 次 `.route(` 含两个测试内注册，不等于生产路由数；Java 的 359 条清单不足以代替 Rust 注册表。
- 已确认并保留 `postTrainGlobalRule` 的三开关 OR 条件，见 §3。无凭据请求的错误方法会先被既有鉴权中间件拒绝，不能一律断言 405；中文静态路径按原始 UTF-8 request-target 验证。
- 既有诊断缺陷单列：`/api/phase1/contract/admin` 只挂 `require_admin`、未挂 `authenticate`；原始与第 1 步二进制的匿名请求均为 HTTP 500/code 500。该行为原样保留，不作为业务管理员授权成功证据，本轮不夹带修复。
- 项目未配置 LSP；已实际查询状态。符号定位与机械搬迁使用精确搜索和 Rust AST，未声称完成语言服务器 references 验证。
- 隔离库测试须显式传 `RUST_PHASE2_DATABASE_URL` 并串行执行。缺少 URL 的首次命令与并行执行失败均不计通过；按 §6 补齐环境、改为串行后全部通过，不修改测试或业务逻辑。

### 9.2 第 1 步：平台抽取（完成）

- `main.rs` 为 4 行入口；新增 lib/runtime/router/platform，system/tools 业务实现暂留平铺文件，待第 3 步迁入对应域。原 build_router 与恢复循环保持原逻辑，业务调用已切到平台明确路径，无根平台符号兼容 re-export。
- 同步 local-dual-stack 开关扫描、manifest 来源与 sweep 说明；开关集合仍为原 22 项。

| 已执行项目 | 本轮结果 |
|---|---|
| Cargo 格式检查 | 通过 |
| locked release 构建与 release smoke | 通过；health/live/ready、OpenAPI、203/204/206、request ID、/api=404、SIGTERM |
| 普通 Cargo 测试 | 79 通过，154 ignored；与执行前普通测试计数一致 |
| 串行 ignored 隔离库测试 | 154 通过 |
| Python 脚本测试 | 81 通过 |
| V01 与无 DB WS 握手矩阵 | 原始提交与第 1 步各 24 组、18,912 条观察，差异为 0 |
| V04 实际服务恢复 | 关闭开关不恢复；启用后首次立即恢复，后续约 5 秒恢复；退出后不再恢复，前后状态一致 |
| 稳定时钟隔离环境的 journeys | 2,454 请求，0 FAIL / 0 OPEN；169 项既有 ACCEPTED 差异 |
| 稳定时钟隔离环境的 full | 3,903 请求，0 FAIL / 0 OPEN；678 项既有 ACCEPTED 差异 |
| V02/V03 完整有身份对照 | 2,771 条流程观察（含 394 条 WS），1,080 条三身份路由观察，69 条普通用户管理操作拒绝且无写入；0 行为差异、0 漏覆盖 |
| 管理员业务契约 | 68 条接口成功调用；`POST /api/mtd/saveAll` 保持既有 202 能力拒绝，另以 3 种载荷 × 3 种身份验证，106 张表均无改动 |

首次双栈 journeys 在 `b5-telex-rule-and-status-edges/student-finish-E-rule-without-alterError` 失败，后续定位为宿主机 wall clock 在约 +9 秒/-9 秒间跳变：已记录的训练开始时间比随后 MySQL NOW 晚约 8 秒，两栈均按原规则拒绝采集。实测 CLOCK_MONOTONIC 连续。未更改宿主机 NTP、业务代码、测试夹具或差异 registry；采用临时 LD_PRELOAD 验证时钟（共享 UTC 锚点 + CLOCK_MONOTONIC）和新建的 MySQL 演练容器，确认 Java/Rust/MySQL 同一时钟后重跑，得到上表全量通过结果。临时 dual-stack 副本只重定向仓库根、容器、MySQL 端口和已有 --mysql-container 参数；仓库脚本未因时钟隔离再改动，请求集合不变。V02/V03 专项矩阵使用独立库，恢复任务探针另用独立库，避免互相重置或后台恢复干扰。

V02 临时校验器最初将已有 `missing-*` 负例误判为新增成功用例，并错误要求所有管理接口都存在 code 200 成功路径。完整观测已有 0 行为差异；按原用例重新核对四条 202/208 负例，并补验 `mtd/saveAll` 的真实能力边界后，独立契约审计通过。该接口原 Java/Rust 实现把 String 再序列化，始终返回 `Not a JSON Object:` 的 202，不能为重构验收虚构导入成功。原始报告与校验修正、补验结果均保留，不掩盖最初的校验器失败。

第 1 步的函数体/SQL/错误映射/中间件顺序另经独立只读复核：63 个业务模块仅路径改写，主程序抽取项仅移动、必要可见性和格式调整，无新增行为逻辑或兼容 re-export。

本轮原始验证产物暂存 `/tmp/007-structure-refactor-oj245o40/`；完成交付时归档可追溯证据并更新此记录。第 2 步候选仅在临时目录完成，不计为仓库已实施；第 2–5 步仍须依次集成、验证与提交。

### 9.3 第 2 步：按开关拆路由（完成）

- `router.rs` 仅保留常驻诊断、开关条件聚合与全局层；23 个路由工厂分别归对应平铺业务模块，`postTrainGlobalRule` 保留 general/radiotelephone/entering 三开关 OR。
- WS 根路径与 `/api` 别名静态展开，所有动态 GET 保留 `fallback(not_found)`；未引入 `format!` 路由构造或隐藏分配。域工厂不再重复判断开关，总路由负责唯一挂载。
- 原处理函数、SQL、错误映射、admin/auth layer 顺序、局部 body limit 和全局层顺序未改。第 1 步的 386 路由基线与第 2 步的 24 组、18,912 条观察逐项差异为 0。

| 已执行项目 | 本轮结果 |
|---|---|
| `cargo fmt --all -- --check` | 通过 |
| `cargo test --locked` | 79 通过，154 ignored |
| locked release 构建与 smoke | 通过 |
| Python 脚本测试 | 81 通过 |
| 路由/WS 开关矩阵 | 24 组、386 路由、18,912 观察，0 差异 |
| V02/V03 完整有身份对照 | 2,771 流程观察、394 WS、1,080 三身份路由、69 管理拒绝无写入、0 差异、0 漏覆盖 |
| 稳定时钟 dual-stack journeys/full | journeys 2,454、full 3,903，均 0 FAIL/0 OPEN；既有 ACCEPTED 差异未改 registry |
| 受影响生命周期夹具 | 64 请求；配对时钟环境下 0 FAIL/0 OPEN，未修改仓库 fixture 规则 |

Step2 双栈首轮在宿主机时钟跳变下出现 4 项 military duration 差异；采用临时共享取样时钟适配器重跑，结果全为既有 ACCEPTED/0 FAIL。仓库 `dual-stack-journeys.json` 保留原始夹具与请求集合；时钟适配器、隔离容器和报告留在 `/tmp/007-structure-refactor-oj245o40/`。

第 3 步候选已在临时目录生成，未计入本提交，未改真实仓库；第 3–5 步仍须依次集成、验证与提交。

### 9.4 第 3 步：按域搬目录与测试（完成）

- `crates/app/src/` 从平铺模块迁为 21 个 domain、shared、ws、router/tests、platform/tests；根目录仅 `lib.rs`、`main.rs`、`router.rs`、`runtime.rs`。
- 162 个 Rust 文件；旧平铺模块清除；`#[path]`、`pub use`、`include!` 均为 0。路由工厂、所有调用路径和测试宿主均切到真实模块树，不留兼容 shim。
- 测试迁移保持 229 个测试、152 个 ignored；prejob 混合测试保留单一实现及 test_support；资源路径已按新深度更新并确认资源存在。
- `train_rule` 按当前计划暂留 general_train，三开关 OR 仍由总 router 控制；WS 业务入口仍保持原会话锁、generation guard 和通知调用路径，Step5 再收口跨域出口。

| 已执行项目 | 本轮结果 |
|---|---|
| Cargo fmt、普通 Cargo 测试 | 79 通过，154 ignored |
| 串行 ignored 隔离库测试 | 154 通过 |
| locked release 构建与 smoke | 通过且无生产 warning |
| Python 脚本测试 | 81 通过 |
| 386 路由/24 组开关/WS 观察 | 18,912 观察，0 差异 |
| V02/V03 完整有身份对照 | 2,771 流程、394 WS、1,080 三身份、69 管理拒绝无写入，0 差异/漏覆盖 |
| paired-clock dual-stack | journeys 2,454、full 3,903，0 FAIL/OPEN；差异均为既有 ACCEPTED |

第 4 步候选只在临时目录生成，未计入本提交；第 4–5 步仍须依次集成、验证与提交。

### 9.5 第 4 步：拆分超大文件（完成）

- 按已确认切点拆分 general_train、WS、post_telegram_write、post_telegraph_write；general 具备 common/capture/key/telex/ticker/lifecycle 等内聚模块，WS 具备 manager/status/classic/general/notify 等命名模块，term 规则已从 general 临时归属拆到 radiotelephone。
- 生产 Rust 文件最大 1,414 行（`domain/theory/exam.rs`）；没有将生产逻辑移入测试文件规避目标。锁、事务、恢复 worker、WS generation/close 顺序静态复核无差异。

| 已执行项目 | 本轮结果 |
|---|---|
| Cargo fmt、普通 Cargo 测试 | 79 通过，154 ignored |
| 串行 ignored 隔离库测试 | 154 通过 |
| locked release 构建与 smoke | 通过 |
| Python 脚本测试 | 81 通过 |
| 路由/开关/WS矩阵 | 18,912 观察，0 差异 |
| V02/V03 完整有身份对照 | 2,771 流程、394 WS、1,080 三身份、69 管理拒绝无写入，0 差异/漏覆盖 |
| paired-clock dual-stack | journeys 2,454、full 3,903，0 FAIL/OPEN；差异均为既有 ACCEPTED |
| Step4 静态语义复核 | 1,645 函数、282 命名类型/常量、67 impl 头、1,927 注释/属性关联、17,490 字符串多重集闭合；测试229/ignored152集合一致 |

第 5 步仍须收口共享内核、清除生产跨域直接引用并完成最终审计。

