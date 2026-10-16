# backend-rust 目录结构重构规格（2026-10-15）

> 状态：实施中，第 1 步已完成，尚未完成整体验收。来源为实施计划的评审修订提交 `adf9a7eb`；代码事实与计数沿用计划记录的 `d1ccee6f` 设计基线，执行起点及实际证据见配套计划 §9，不作为既成验收结果。

配套执行文档：[docs/plans/2026-10-15-backend-rust-structure-refactor-plan.md](../plans/2026-10-15-backend-rust-structure-refactor-plan.md)。本规格定义必须满足的结构、行为边界与验收；计划负责文件归属、实施顺序和执行记录。SR01–SR08 为需求标识，V01–V05 为验证标识。两份文档出现冲突时，先同步规格与计划，不自行降低约束或门禁。

## 1. 目标与范围

将 `backend-rust/crates/app` 从平铺业务文件与巨型二进制根模块，整理为平台层、按功能开关划分的业务域、跨域共享逻辑和 WebSocket 模块。

本轮必须实现：

- 二进制只负责进入 Tokio 运行时并调用库入口；业务模块不再依赖二进制根。
- 路由按开关归属聚合，域内包含对应 handler、SQL 与测试；增加或删除业务域不需要改 `main.rs`。
- 后台恢复任务有明确的生命周期所有者，目录移动不改变运行行为。
- 超大文件按业务内聚边界拆分，移除迁移批次命名及 `#[path]` 装配方式。
- 共享逻辑有稳定归属，跨域直接依赖只保留明确例外。

允许改动的范围为 `backend-rust/crates/app` 的模块组织、必要接线、可见性，以及 SR04 指定的脚本与证据来源路径。只搬迁既有实现，不引入业务修复。

**不在本轮范围**：更换 ORM、删除或启用 `persistence` crate 承接业务 SQL、改 DB schema、重命名功能开关、统一域内 handlers/service/repo 三层、统一错误 trait、夹带 B8 或其他业务修复。此类改动必须另立任务，不与结构搬迁交错。

## 2. 不变约束

| 标识 | 必须保持的契约 |
|---|---|
| I01 HTTP/WS | 方法、路径及别名、请求参数与 JSON、响应形态、业务码、文案、HTTP 状态不变；WS 消息、接收者、关闭与会话替换语义不变 |
| I02 鉴权与授权 | REST token/deviceId 仍取请求头，WS 仍按既有握手规则认证；203/204/206 契约、207 授权拒绝、208 终态拒绝不变；`is_admin=0` 的管理员语义不变，不增删或错序装配鉴权/admin 中间件 |
| I03 数据与并发 | DB schema、SQL 语义、事务边界、锁与 generation guard、恢复/结算逻辑、通知时序不变；不通过吞异常、改变错误映射或写脏数据制造对账一致 |
| I04 配置 | 现有 22 个 `RUST_*_ENABLED` 名称及其集合、默认值、解析与非法值处理、路由归属不变；其他运行配置的语义也不变 |
| I05 交付路径 | 二进制名 `rust-backend`、工程根 `backend-rust/`、脚本目录与文件名、现有 CI 制品消费路径不变 |
| I06 生命周期 | 启动顺序、恢复任务启停条件、计时方式与退出清理不变；构建状态或 Router 不得额外启动后台任务 |

这些约束适用于每个中间提交，不只适用于最终目录。基线已有且已登记的行为差异继续按既有口径处理，不得趁重构修复，也不得新增未说明的差异。

## 3. 结构要求与验收

### SR01 lib/bin 边界与平台归属

- `backend-rust/crates/app/src/main.rs` 只保留 `#[tokio::main]` 入口及 `rust_backend::run().await`。
- `backend-rust/crates/app/src/lib.rs` 为模块树根，仅向 bin 公开 `pub async fn run()`。该入口承接现有主函数的 tracing 初始化、启动错误日志与 `exit(1)`，内部调用 `runtime::run() -> Result<(), AppError>`。
- `backend-rust/crates/app/src/runtime.rs` 承接配置/状态装配、监听、服务运行与退出管理。`AppConfig`、`AppState`、`AppError` 不成为 bin 所需的公开 API。
- 平台职责按计划 §3 分入 `backend-rust/crates/app/src/platform/`：config、flags、state、envelope、error、auth、extract、clock、health、http；业务 handler 不留在二进制根或借平台目录继续平铺。
- 模块默认私有，库内跨模块访问仅在需要时使用 `pub(crate)`；不通过公开整个 domain/platform 模块或兼容 re-export 扩大公开面。
- `build_state` 与 `router::build_router(state: AppState) -> Router` 是库内测试可复用的构建入口，不另设公开 `build_app(config)`。

**验收**：bin 与 lib 在现有包内可构建，现有测试从库内入口运行；生产二进制启动、配置失败和正常退出的行为不变；不残留依赖二进制根的业务模块或兼容入口。

### SR02 恢复任务生命周期

- 既有 general 训练恢复任务的唯一所有者为 `runtime::run`；业务恢复函数仍归 general 域，不搬入平台层。
- `RUST_PHASE7_GENERAL_ENABLED=false` 时不创建任务；为 true 时每次服务启动只创建一个任务，并由当前运行入口持有 `JoinHandle`。
- 原样保留 `interval(Duration::from_secs(5))`、首次立即 tick 与后续 tick/恢复调用顺序；不得改为先等 5 秒、延迟调度或增加第二个定时器。
- `axum::serve` 返回后，必须在传播服务错误之前调用已有任务的 `abort()`；正常退出与服务错误路径都保留清理。
- `build_state`、`build_router` 和各域 `routes` 不负责启动任务，测试构建 Router 不产生隐藏的恢复 worker。

**验收**：通过 V04 对照任务开/关、恢复状态变化、周期和退出清理；HTTP 可访问或 `JoinHandle` 存在本身不能证明恢复逻辑已运行。

### SR03 路由接口与装配边界

- 每个功能开关对应一个路由入口；业务域与 WS 的入口签名为 `pub(crate) fn routes(state: &AppState) -> Router<AppState>`。
- 同一开关在原 `build_router` 中出现的所有路由块必须完整归入同一入口，不能只搬第一个块。域内保留 admin 子路由与局部 body limit。
- 保留 `from_fn_with_state(state.clone(), authenticate)` 需要的状态及配置读取，不为凑无参签名改写中间件或引入全局状态。
- `backend-rust/crates/app/src/router.rs` 只负责开关判断、路由聚合和既有全局层；原 `nest`/`merge` 层级及 CORS、body limit、catch-panic、request_id、fallback 的装配顺序不变。
- 域内返回未最终绑定状态的 `Router<AppState>`；最外层仍由 `build_router` 统一 `.with_state(state)`。
- WS 的根路径与 `/api` 别名均属于 WS 开关，包括两种前缀的 `startWebsocket`；不得因为 Java 清单未收录就删除。
- 既有复合开关例外：`/api/postTrainGlobalRule/{findByType,addRule,deleteById}` 的挂载条件为 general、radiotelephone、entering 三个开关的 OR（基线 `main.rs:815–831`）。实现归 general 域，由总路由在该 OR 条件下合并规则子模块的 `routes(&state)` 一次；不得收窄为 general 独占，也不得在多个域中重复注册。

**验收**：V01、V02、V03 均通过；单开某个开关时只恢复其原有路由，其他受开关控制的路由保持基线行为；常驻路由不受域开关误控。

### SR04 开关定义与外部引用

- 开关名称集中于 `backend-rust/crates/app/src/platform/flags.rs`；`AppConfig::from_env` 与启动脚本消费同一份定义，不维护第二份复制的名称表。
- 迁移前后比较完整名称集合，不以“数量仍为 22”代替集合相等；定义必须继续能被现有脚本的字符串扫描方式识别。
- 抽取开关的同一个提交必须同步：
  - `backend-rust/scripts/local-dual-stack.sh`：扫描来源由 `main.rs` 改为 `platform/flags.rs`，仍实际开启全部既有开关。
  - `backend-rust/scripts/phase9-contract-manifest.json`：sources 增加 lib、runtime、flags、router 的完整仓库路径，保留仍存在的 main 来源。
  - `backend-rust/scripts/dual-stack-sweep.py`：更新涉及原扫描/实现位置的说明。
- 仅更新来源路径，不更名脚本、环境变量、二进制或历史证据文件，不重写旧证据声称其验证过新结构。

**验收**：名称集合与执行基线一致；实际启动记录中的有效开关与请求结果符合 V01；`local-dual-stack.sh` 的 journeys/full 均按 V05 执行，不能在少开开关的情况下宣称通过。

### SR05 域归属与测试搬迁

- 开关、目录、现文件的完整搬迁清单采用配套计划 §3.1；不得另加 basic/post 层改变划分轴。
- 保留既有特殊归属：`post_entering` 在 entering 域，`post_military`/`post_radiotelephone` 在 radiotelephone 域，`train_rule` 按 term 与 postTrainGlobalRule 语义分属 radiotelephone/general 域。
- 每个域的模块树自行装配 handler、SQL 与测试；测试进入对应域的 `tests/`，删除原 `#[path]` 装配与搬迁后空目录。
- `b8_writer_tests` 归 theory，`radio_military_writer_tests` 归 radiotelephone，`prejob_writer_tests` 按 telegraph_key、telegram、telex 三个被测域拆分；测试文件名去掉迁移批次前缀。
- 保存原测试到新模块的对应关系，测试数量、ignored 属性及覆盖的行为不变；不得漏搬用例，也不得用重复新用例补齐计数。

**验收**：模块树与清单对应；旧模块声明、旧路径与兼容转发全部清除；原测试能被发现并通过 V05，不为目录搬运新增源码文本或路径断言测试。

### SR06 超大文件拆分

| 对象 | 结构要求 |
|---|---|
| 二进制入口 | `backend-rust/crates/app/src/main.rs` 不超过 60 行 |
| 非测试 Rust 源文件 | 目标不超过 1500 行；不能把生产逻辑挪入测试文件规避目标 |
| general_train | 拆入 `backend-rust/crates/app/src/domain/general_train/` 的 key、telex、ticker、common 子模块；共用 SQL 字段与生命周期逻辑保留单一实现 |
| WS | 拆入 `backend-rust/crates/app/src/ws/` 的 manager、status、classic、union、simulation、general、notify 子模块及路由入口 |
| 其他超大文件 | post_telegram_write、post_telegraph_write、exam 等按生命周期、分页提交、结算、报底生成等内聚边界拆分 |

具体函数切点在计划第 4 步开始前形成清单并确认；这不是修改业务规则的授权。若目标无法在本轮边界内达成，必须先修订规格并获得明确确认，不得实施者自行豁免。

**验收**：规模目标达成；函数与 SQL 只调整归属、必要可见性和调用路径，锁、事务、恢复、结算及 WS 会话语义保持 I01–I06；通过既有行为测试与对账，不以行数达标代替行为验收。

### SR07 共享逻辑与跨域依赖

- `scoring`、`statistical`、`bean_conversion`、`telex_capture` 归入 `backend-rust/crates/app/src/shared/`，该层用于跨域纯逻辑，不含 handler。
- `post_telegram_write::generated_groups`、`telex_handle::Stats` 下沉共享层；`entering::request_integer` 下沉 `platform::extract`。
- 业务域对 WS 的调用统一经 `ws::notify` 出口，覆盖既有 send_classic、notify、close、general、simulation 相关调用；不改变调用时序或接收者。
- 允许保留的直接跨域依赖仅为计划 §4 所列 `post_ticker → ticker_tape::{SettingRequest, settings_connection}`。如果执行时确认可只下沉共享数据结构，须保持设置语义，不借机抽象整个持久层。
- 第 1–4 步可临时使用显式 `crate::domain::x::y` 引用保证机械搬运；第 5 步结束时除上述例外外清零业务域间直接引用，不留旧路径 shim。
- 只合并语义相同的工具函数；同名但签名、错误或业务含义不同的函数保持原有私有实现。

**验收**：调用点整体切换，共享函数无重复实现；跨域引用与例外清单对应，WS 通知出口完整；既有评分、统计、设置与通知行为通过 V05。

### SR08 错误类型与响应映射

- `backend-rust/crates/app/src/platform/error.rs` 只接收原有 `AppError`；域错误及其转信封实现随所属模块原样搬迁。
- 不新增统一错误 trait，不合并错误分支，不把平台启动错误与业务错误混为一种响应类型。
- 业务码、文案、HTTP 状态及错误传播边界逐一保留；不得为了消除对账差异改变错误处理。

**验收**：错误映射变更仅涉及路径或必要可见性；V01–V03 的错误结果和 V05 的业务对账均保持基线。错误体系归一不属于本规格的完成条件。

## 4. 验证契约

### 4.1 基线与证据

在第 1 步修改代码前采集当前源码版本、完整开关集合、测试清单、实际 Rust 路由及请求结果。计划中的历史行号与计数仅作定位，不代替该采样。

路由清单以 Rust 实际注册为主，包含方法、路径模板、具体参数样例、所属开关、鉴权/admin 要求。`docs/reviews/rust-migration/rest-endpoints.tsv` 与 `docs/reviews/rust-migration/ws-contracts.tsv` 只作补充核对，不是 Rust 路由的完整清单。

至少覆盖常驻基础设施/诊断路由、全部业务路由，以及所有 WS 根路径与 `/api` 别名，包括 `/health`、`/live`、`/ready`、`/q/openapi`、`/api/phase1/contract/*`、`/api/ws/status` 和两种前缀的 `startWebsocket`。

一次性路由验证脚本不进入永久测试套件。执行记录须保留基线/目标版本、实际开关、用例、夹具重置身份、结果、命令退出码和差异；凭据与连接串脱敏，不把执行异常记为通过。

### V01 路由与开关等价

- 配置为全关、全开、22 个开关分别单独开启，共 24 组。每组显式设置全部开关，避免继承外部环境。
- 无 DB、无凭据验证每条路由的出现/消失及错误方法响应；同时验证常驻路由与域路由边界。
- 记录 `(配置, 用例, 身份, method, path, HTTP 状态, 信封 code)` 与鉴权错误文案；非信封响应明确标记 code 不适用，保留响应类型及实际结果。
- 前后差异必须为空，并符合该清单的预期结果；漏跑、两个非预期的 404/500 或解析失败均不得算通过。

此项只验证路由/开关及部分拒绝路径，不证明 handler 内容、管理员权限或写入语义等价。

### V02 鉴权与授权等价

- 全开配置、隔离库、相同业务夹具及有效参数，逐条覆盖受保护路由的无凭据、普通用户、管理员场景。
- 管理员接口必须验证普通用户按既有契约被拒绝，以及管理员合法调用；不以两类接口均返回 `200/203` 作为授权证据。
- 前后运行独立重置数据；拒绝请求的目标及非目标记录均不得写入。成功响应与写入语义结合 V05 对账。
- 公开路由保留公开性，既有授权边界不得因路由合并被扩大或收窄。
- 既有能力拒绝单列：`POST /api/mtd/saveAll` 的 Java/Rust 实现均对 String 再序列化，管理员请求始终得到特定 202，而非可成功导入的接口。该端点仍须覆盖匿名 203、普通用户 207、管理员到达原有 202 业务契约及拒绝无写入；不能为制造 code 200 成功路径改变业务行为，也不能把它描述为导入成功。

### V03 真实 WebSocket 路径与行为

- 每条路径及别名使用真实 Upgrade，记录握手状态；普通 GET 仅作补充，不能用其 404 判断未挂载。
- 按连接类型用合法/非法凭据验证首帧、错误及关闭行为；无主动首帧的连接使用既有心跳或合法消息观察响应，不假设所有端点都会主动发送首帧。
- 房间/训练连接使用隔离库身份及成员夹具；既有路径身份覆盖、角色检查、会话替换、陈旧连接清理和通知行为由真实场景与现有 WS 测试共同守住。

### V04 恢复任务与服务生命周期

- 用隔离库及可恢复训练夹具运行实际服务，对照 general 开关关闭/开启时的数据状态变化。
- 核验首次 tick、后续周期、唯一任务归属，以及正常退出/服务错误路径的清理；重复构建 Router 不增加恢复任务。
- 行为结果与句柄/调用归属共同取证；只看 HTTP 上线、只看任务已创建或只检查目录结构都不算完成。

V04 在第 1 步，以及之后任何涉及运行入口或 general 恢复逻辑归属的步骤执行。

### V05 既有构建、测试与双栈门禁

每步提交前执行配套计划 §6 的完整命令与附加检查，覆盖：

- Cargo 格式检查、locked release 构建及 release 二进制 smoke。
- 普通 Cargo 测试与 ignored 隔离库测试；不得因环境未准备而跳过后宣称全量通过。
- Python 脚本测试。
- 双栈 build/up/journeys/full/down；journeys 与 full 均为 `0 FAIL / 0 OPEN`，保留结果文件及摘要，清理所启动的服务。

V01–V03 每步执行，不能以 V05 替代；V01 的匿名结果也不能替代 V02 或 V05。运行依赖、隔离库和命令输出按本次执行记录，不沿用历史绿灯、测试数量或旧 CI 结果。

## 5. 实施映射与回滚

| 计划步骤 | 主要需求 | 交付边界 |
|---|---|---|
| 第 1 步：lib/bin 与平台抽取 | SR01、SR02、SR04 | 库入口、运行生命周期、平台项与外部扫描来源同提交闭合 |
| 第 2 步：拆 build_router | SR03 | 路由入口、状态注入、中间件与开关归属完整迁移 |
| 第 3 步：按域搬目录 | SR05 | 文件、模块引用与测试按归属一起迁移，不留旧入口 |
| 第 4 步：拆超大文件 | SR06 | 按确认切点拆分，业务逻辑与锁/事务/任务语义不变 |
| 第 5 步：收共享内核 | SR07、SR08 | 共享函数及调用点整体切换，跨域引用收口，错误实现不归一 |

SR08 及全部不变约束贯穿所有步骤，不只在第 5 步检查。

- 同一时刻只有一个代码写入执行者；结构重构期间冻结交错的业务修复。
- 每步一个提交，提交后可构建并通过本步适用门禁；模块移动及其调用点、脚本/manifest 配套变更不可拆成失效的中间提交。
- 后一步依赖前一步。当前步失败可撤销当前步；整体按已完成步骤逆序回滚，全部完成时为 5 → 4 → 3 → 2 → 1。不承诺任意撤销中间一步。
- 每次回滚后重跑对应门禁；提交正文记录范围、前置提交、实际验证及“未改 API/DB/开关名”。

## 6. 完成定义

以下条件全部满足，才能把本轮标为完成：

- [ ] SR01–SR08 的结构与接口要求全部落地，I01–I06 在各中间提交和最终状态均成立。
- [ ] 原文件、模块声明、调用点与测试完成干净切换，无旧路径 shim、兼容别名、漏搬用例或未说明的跨域引用。
- [ ] 文件规模目标达成；具体切点、文件归属及例外与规格/计划一致。
- [ ] 开关名称集合、有效配置、外部扫描来源与 manifest 路径一致，实际启动未少开开关。
- [ ] 每步适用的 V01–V05 有本轮证据，差异和失败已闭合；未执行、阻断、失败均未记为通过。
- [ ] 独立提交与依赖回滚关系清楚，计划状态及验证记录反映实际完成情况。

上述完成条件尚未全部满足。当前第 1 步已完成代码抽取及适用门禁，第 2–5 步仍须完成；实际进度、失败诊断与验收结果在配套计划 §9 登记。
