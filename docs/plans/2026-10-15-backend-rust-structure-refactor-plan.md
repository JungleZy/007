# backend-rust 目录结构重构计划

> 状态：方案，未开工。基线：`d1ccee6f`（2026-10-15）。行号与计数均为该基线实测，执行时以仓库现状为准。

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

本计划只做移动、拆分和可见性调整，不夹带行为修复；搬运中发现的缺陷另行登记。

## 2. 现状问题（实测证据）

| # | 问题 | 证据 |
|---|---|---|
| P1 | 业务文件全平铺 | `crates/app/src/` 70 个 `.rs`，以 `post_*`/`telex*`/`theory*` 前缀模拟分组 |
| P2 | `main.rs` 职责过载 | 2808 行：`AppConfig`/`AppState`/`AppError`/`Envelope`、`authenticate`/`require_admin`/`token_digest`、用户/登录/菜单/角色/tools handler、`parse_*` 工具、`build_router`（388 处 `.route(`、22 个开关块）、`init_tracing`/`shutdown_signal`、180 行测试 |
| P3 | 业务模块依赖二进制根 | 如 `cable.rs:12 use super::{envelope_json, envelope_response, AppState}`；`crate::now_time_millis` 11 处引用 |
| P4 | 超大文件 | `general_train.rs` 4009（key 21 / telex 16 / ticker 12 个函数）、`ws.rs` 3198、`post_telegram_write.rs` 2276、`post_telegraph_write.rs` 1587、`exam.rs` 1549 |
| P5 | 拆分轴不一致 | 按读写拆（`post_telex.rs`+`post_telex_write.rs`）、按功能拆（`telex.rs`+`telex_capture.rs`+`telex_handle.rs`）、`#[path]` 嵌套（`telegram_write.rs:12` 挂 `telegram_lifecycle.rs`）并存 |
| P6 | 测试三种放法，含按迁移批次命名 | 11 处 `#[path = "*_writer_tests.rs"]`；`b8_writer_tests`/`prejob_writer_tests`/`radio_military_writer_tests` 挂在 `main.rs`；`post_ticker/` 目录只装一个测试文件 |
| P7 | 跨域共享逻辑寄居在业务文件 | 见 §4 |
| P8 | `persistence` crate 运行时未被使用 | `crates/app/Cargo.toml` 无该依赖，业务全部裸 `sqlx`；约 100 个 SeaORM 实体仅服务其自身 2 个测试 |
| P9 | 外部硬编码 `main.rs` | `scripts/local-dual-stack.sh:63` 扫描 `main.rs` 收集开关；`phase9-contract-manifest.json` sources 列 `main.rs`；`dual-stack-sweep.py:35` 注释 |
| P10 | 错误类型分散 | 11 个独立 `enum *Error`（`main.rs`、`comprehensive.rs`、`entering.rs`、`exam.rs` 等），各自转信封 |

## 3. 目标结构

**划分原则：一个功能开关 = 一个域目录 = 一个 `routes()`。** 开关是部署、灰度和回滚的单位，`build_router` 也已按开关分块；按开关切能让第 2 步成为机械搬运。注意开关边界与“岗位/基础”命名不完全一致（`post_entering` 在 entering 开关下，`post_military`/`post_radiotelephone` 在 radiotelephone 开关下），以开关为准，不另加 `basic/`、`post/` 层级。

```text
crates/app/src/
├── main.rs              # ≤60 行：init_tracing、AppConfig::from_env、serve、shutdown_signal
├── lib.rs               # 模块树根；pub fn build_app(config) 供 main 与测试复用
├── router.rs            # 仅组装：if flags.x { api = api.merge(domain::x::routes()) }
├── platform/
│   ├── config.rs        # AppConfig
│   ├── flags.rs         # 22 个 RUST_*_ENABLED 常量表（脚本扫描的唯一来源）
│   ├── state.rs         # AppState
│   ├── envelope.rs      # Envelope / envelope_json / envelope_response / openapi 兜底
│   ├── error.rs         # AppError + 域错误转信封的统一 trait
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
│       ├── mod.rs       # pub(crate) fn routes() -> Router<AppState>（含 admin 子路由）
│       ├── *.rs         # handler / service / SQL，按子资源分文件
│       └── tests/       # mod.rs + writer.rs 等；取代 #[path]
└── ws/
    ├── mod.rs           # routes()、open/serve/dispatch
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
| — | 按被测域拆分 | `prejob_writer_tests.rs`（调用 `synthetical`/`telegraph_key`→telegraph_key，`telegram_write`→telegram，`telex`→telex） |
| — | 各模块 `tests/` | `main.rs` 的 `mod tests` |

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
| `train_rule.rs` 横跨 radiotelephone（`term_*`）与 general（`find_by_type`/`add_rule`/`delete_by_id`） | 按开关拆为两个文件，分属两域 |

第 1–4 步允许临时使用 `crate::domain::x::y` 跨域引用以保持纯移动；第 5 步清零业务域之间的直接引用（`ws/notify.rs` 与 §4 已列保留项除外）。

## 5. 分步实施

每步一个提交、可单独 revert；每步只移动/拆分，不改函数体（可见性与 `use` 路径除外）。同一时刻只有一个写代码的执行者。

### 第 1 步：lib/bin 拆分与 `platform/` 抽取

- 新建 `lib.rs`，`main.rs` 只保留启动；P2 列出的平台项按 §3 搬入 `platform/`。
- 22 个开关名集中到 `platform/flags.rs` 常量表，`AppConfig::from_env` 改为读表。
- 业务模块 `use super::{…}` 改为 `use crate::platform::…`；此时业务文件仍平铺在 `src/`。
- **同提交同步外部引用**：`scripts/local-dual-stack.sh:63` 扫描路径改为 `platform/flags.rs`；`phase9-contract-manifest.json` sources 增加 `flags.rs`/`router.rs`（`main.rs` 保留，内容已变）；`dual-stack-sweep.py:35` 注释。
- 校验：`grep -oE '"RUST_[A-Z0-9_]+_ENABLED"' platform/flags.rs | sort -u | wc -l` = 22，且集合与基线一致。

### 第 2 步：拆 `build_router`

- 每个开关块整体搬到对应域的 `routes()`（此时可先放在平铺文件里，如 `cable.rs` 内的 `pub(crate) fn routes()`）；`router.rs` 只剩组装与全局层（CORS、body limit、catch-panic、request_id、fallback）。
- 中间件顺序与 `nest`/`merge` 层级必须逐块保持，admin 子路由仍套 `require_admin`。
- **路由等价校验（一次性脚本，不入库）**：以全部开关打开、无 DB 构建 Router，对 `docs/reviews/rust-migration/rest-endpoints.tsv` 全部行与 `ws-contracts.tsv` 路径，用无凭据请求记录 `(method, path, HTTP 状态, 信封 code)`；拆分前后输出逐行 diff 必须为空；再对“全部开关关闭”跑一次同样 diff。

### 第 3 步：按域搬目录

- `git mv` 到 §3.1 的目标目录，补 `mod.rs`；删除全部 `#[path]`，测试移入各域 `tests/`。
- `b8_writer_tests` 并入 `domain/theory/tests/`，`radio_military_writer_tests` 并入 `domain/radiotelephone/tests/`，`prejob_writer_tests` 按被测域拆三份；文件名去掉批次前缀。
- 删除空的 `post_ticker/` 目录。

### 第 4 步：拆超大文件

- `general_train.rs` → `domain/general_train/{key,telex,ticker,common}.rs`（按现有 `key_`/`telex_`/`ticker_` 函数前缀，共用 SQL 字段表与生命周期进 `common.rs`）。
- `ws.rs` → `ws/{manager,status,classic,union,simulation,general,notify}.rs`。
- `post_telegram_write.rs`、`post_telegraph_write.rs`、`exam.rs` 等超过 1500 行的，按子资源（生命周期 / 分页提交 / 结算 / 报底生成）拆；具体切点在执行该步前列清单拍板。
- `train_rule.rs` 按 §4 拆入两个域。

### 第 5 步：收共享内核

- 按 §4 下沉共享函数，清零业务域之间的直接引用。
- 只合并语义相同的工具函数；同名不同义的保持私有（如 `post_telegraph_write.rs:158` 与 `synthetical.rs:221` 两个 `parse_array` 签名与错误类型不同，不合并）。
- 11 个域错误类型统一实现 `platform/error.rs` 的转信封 trait；**业务码、文案、HTTP 状态逐一不变**，以第 2 步脚本 + 全量对账确认。

## 6. 门禁（每步提交前）

```bash
cd backend-rust
cargo fmt --all -- --check
cargo build --locked --release && ./scripts/smoke.sh target/release/rust-backend   # 同 CI
cargo test --locked
# ignored 隔离库测试（需 docker start mysql-project006）
cargo test --locked -- --ignored
python3 -m unittest discover -s scripts -p 'test_*.py'
S=scripts/local-dual-stack.sh
$S build && $S up && $S journeys && $S full   # 两者 0 FAIL / 0 OPEN
$S down
```

附加：

- 测试数量与基线一致（只搬不删）；第 2 步路由 diff 为空。
- 提交信息：`refactor(backend-rust): …`，正文写本步范围、门禁结果，以及“未改 API/DB/开关名”声明。

## 7. 待决项

| 项 | 选项 | 建议 |
|---|---|---|
| `persistence` crate（P8） | A. 维持现状；B. app 依赖它，作为各域 SQL 的落点；C. 删除 | 本计划不处理；结构重构完成后单独评估 |
| 开关名（`RUST_PHASE*` → 按域命名） | 改名需同步脚本、manifest、证据文档 | 不改；目录已按域，开关名只作为外部标识 |
| 域内统一 `handlers/service/repo` 三层 | 现有代码 handler 与 SQL 交织 | 本计划只按子资源拆文件；分层另立计划，避免移动与改写混在同一提交 |

## 8. 风险

| 风险 | 缓解 |
|---|---|
| 第 2 步漏挂或错挂路由、中间件顺序变化 | 一次性路由 diff（开关全开/全关两次）+ `full` 对账 |
| 第 1 步漏改脚本，导致 `$S up` 少开开关、对账假绿 | 同提交改脚本，并校验开关计数 = 22 |
| 可见性放宽成 `pub` 扩大耦合 | 统一 `pub(crate)`，第 5 步清理跨域引用 |
| 大文件拆分时混入逻辑改动 | 每步只移动，评审按 `git diff -M --stat` 与移动检测确认 |
| 搬运与业务修复并行冲突 | 重构期间冻结 `backend-rust` 业务改动（B8 修复排在重构前或后，不交错） |
