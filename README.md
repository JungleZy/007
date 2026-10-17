# 007 — 舰船报务综合训练系统

单仓包含 Quarkus 后端、Electron 桌面端（内含 Vue 前端）、后端的 Rust 迁移工程，以及独立的 Rust `keysim` 拍发/串口模拟工具。

| 目录 | 内容 | 详细文档 |
|---|---|---|
| [`backend/`](backend/) | Quarkus 3.20.4 / Java 21，REST + WebSocket 服务；**当前生产后端** | [`backend/README.md`](backend/README.md) |
| [`backend-rust/`](backend-rust/) | Java 后端的 Rust（axum + SQLx）迁移；Phase 1–9 完成，Phase 10 下线 Java 仍阻断 | [`backend-rust/AGENTS.md`](backend-rust/AGENTS.md)、[迁移交接](docs/plans/rust-migration-handoff.md) |
| [`bw-frontend/`](bw-frontend/) | Electron 桌面外壳（主进程、本地 HTTP 服务、串口桥接、授权校验） | [`bw-frontend/README.md`](bw-frontend/README.md) |
| [`bw-frontend/frontend/`](bw-frontend/frontend/) | Vue 3.5 + Vite 4 前端页面工程 | [`bw-frontend/frontend/README.md`](bw-frontend/frontend/README.md) |
| [`keysim/`](keysim/) | 手键/电子键时间轴、虚拟串口、桥接与网页控制台 | [`CLI 命令入口`](keysim/src/main.rs) |

- 仓库：`JungleZy/007`；当前发布版本 `backend/pom.xml` = `3.1.1`
- 面向 AI 编码代理的命令、约定与红线：[`AGENTS.md`](AGENTS.md)（含**提交约定**：完成一个任务就提交，不攒批）；Rust 工程另见 [`backend-rust/AGENTS.md`](backend-rust/AGENTS.md)

---

## 快速开始

### 后端（Java）

```bash
cd backend
export JAVA_HOME=$HOME/.local/opt/jdk21
./mvnw -B clean verify     # 全量测试套件，提交前必跑（需 Docker）
./mvnw quarkus:dev         # 开发模式，监听 18001，REST 前缀 /api
```

数据库准备、profile 与 schema 策略、API 与响应信封约定、业务码表、WebSocket 端点、
迁移执行顺序 —— 全在 [`backend/README.md`](backend/README.md)。

### 后端（Rust 迁移）

```bash
cd backend-rust
cargo test --locked
cargo build --locked --release && ./scripts/smoke.sh target/release/rust-backend
```

默认监听 `127.0.0.1:18002`，22 个 `RUST_PHASE*_ENABLED` 功能开关默认全关。隔离库测试、
Java/Rust 双栈对账与红线见 [`backend-rust/AGENTS.md`](backend-rust/AGENTS.md)。

### 前端

```bash
cd bw-frontend/frontend
npm ci             # 已有 package-lock.json，安装结果可复现
npm run dev        # vite --host
npm run test       # 前端单测
npm run build      # 产物 dist/，并生成产物清单
```

Electron 外壳在 `bw-frontend/` 根（主进程入口 `main.js`）：

```bash
cd bw-frontend
npm ci
npm run dev-e      # 仅起 Electron；dev-f 仅起 Vite；build-e-w / build-e-l 打包
```

### keysim

```bash
cd keysim
cargo test --locked && cargo build --locked
```

---

## 目录结构

```
.
├── backend/              # Quarkus 服务（代码/脚本的内部相对引用以此为根）
│   ├── src/              #   main/java/com/nip/{common,controller,service,dao,entity,dto,ws}
│   ├── database/         #   DB 快照（project006[-base].sql）、migrations/、rehearsal/ 演练证据
│   ├── scripts/          #   rehearse-migrations.sh（双快照迁移演练）
│   ├── pom.xml           #   com.nip:quarkus-template
│   └── mvnw, mvnw.cmd    #   Maven Wrapper，无需预装 Maven
├── backend-rust/         # Rust 迁移后端（Cargo workspace）
│   ├── crates/app/       #   rust-backend：platform/ shared/ ws/ domain/（21 个业务域）
│   ├── crates/persistence/ # 未被使用的 SeaORM 实体，已规划删除
│   └── scripts/          #   smoke、双栈对账（local-dual-stack.sh、dual-stack-sweep*）
├── bw-frontend/          # Electron 桌面端
│   ├── electron/         #   主进程、controller、本地 HTTP 服务、串口桥接
│   ├── frontend/         #   Vue 前端（src/、public/、vite.config.js）
│   ├── scripts/          #   打包、产物清单、桌面验收与契约测试
│   ├── bin/              #   随包资源（server/ 与 file/ 为产物，不入库）
│   └── main.js           #   Electron 入口
├── keysim/               # 独立 Rust 拍发/串口模拟工具
├── docs/                 # 全仓文档唯一位置（2026-09-08 收口）
│   ├── README.md         #   文档地图与路径约定
│   ├── reviews/          #   评审；rust-migration/ 为 Rust 迁移证据
│   ├── specs/            #   规格
│   ├── plans/            #   实施计划与 Rust 迁移交接
│   └── guides/           #   专题说明
├── .github/workflows/    # CI，见下
├── AGENTS.md
└── README.md
```

文档只收**文字**：库快照、迁移脚本与演练证据是后端工程资产（被 `backend/scripts/rehearse-migrations.sh`
与 `%prod` 部署流程直接消费），留在 `backend/database/`，脚本仍以 `$SCRIPT_DIR/..`（= `backend/`）
一把定位快照、迁移脚本、证据目录与 `target/`。Rust 迁移同样以这些迁移脚本为 schema 来源，不做自动 DDL。

`docs/` 下 2026-08/09 的历史后端文档，其代码引用（`src/...`、`scripts/...`）仍以 `backend/` 为根、
未逐篇改写；库路径已统一改写为仓库根形式 `backend/database/...`（详见 `docs/README.md` §3）。

### CI

| 工作流 | 触发 | 内容 |
|---|---|---|
| `build-quarkus-native.yml` | push main/master、`v*` tag、PR | 后端 JVM 测试 → 前端测试与构建、keysim 测试与构建 → 三平台 GraalVM native；`desktop` 与 `release` 仅在 tag 时运行，发版仍打 Java |
| `rust-backend.yml` | push main/master、PR | Rust fmt/测试/构建/smoke（Linux x64），原生 Linux arm64、Windows GNU 构建，Windows 桌面验收；不发布 |

各 job 的 `run` 步骤用各自的 `working-directory`（`backend`、`bw-frontend/frontend`、`keysim`、`backend-rust`）；
`upload-artifact` 的 `path` 必须写仓库根相对路径（如 `backend/target/*-runner*`）—— `working-directory` 不作用于
`uses` 动作的输入。

---

## 当前状态

状态与开放项以 [`docs/README.md`](docs/README.md) 顶部入口为准，本表只做摘要，不重复计数。

| 项 | 状态 |
|---|---|
| 全项目与核心训练复核（2026-09-15） | 19 个修复单元；代码、运行、迁移与 Actions 证据见 [`docs/reviews/2026-09-15-full-project-review.md`](docs/reviews/2026-09-15-full-project-review.md) |
| 前后端一致性专项复核（2026-09-19） | 评分 7.4/10，P0=0、P1=1、P2=7，见 [`docs/reviews/2026-09-19-multi-model-debate-cross-stack-review.md`](docs/reviews/2026-09-19-multi-model-debate-cross-stack-review.md) |
| Rust 迁移 | Phase 1–9 完成，双栈全量扫描 0 FAIL/OPEN；Phase 10（下线 Java）阻断，生产判定 NO-GO，见 [`docs/plans/rust-migration-handoff.md`](docs/plans/rust-migration-handoff.md) |
| 客户报障 12 条整改（2026-09-10） | 六个训练域已落地；仅 T17 现场交付未完成（`docs/plans/2026-09-10-customer-issue-fix-plan.md`） |
| 历史整改（09-07 后端单侧、09-08 跨栈、09-12 全项目） | 已闭环或降为历史执行基线，记录保留在各自评审与计划 |
| 外部验收前提 | 可信证书、桌面 native DB 凭据 provisioning、真实电键/USB/音频设备和客户现场签收；native CI 与实机验收分开记录 |

已落地的关键安全与契约边界：训练同步端点不再回传会话凭据；非 free controller 全部有类级 `@JWT`（架构测试守卫）；管理写端点有 `@RequireAdmin`，授权拒绝统一 `code:207`；业务终态用 `code:208`；token 为不透明随机串、只从请求头读、DB 存哈希；WebSocket 握手校验凭据并覆盖路径 `uid`；六个训练域 + 组训数据报域的码速/用时一律服务端从原始采集区间重算；桌面渲染进程启用 `contextIsolation` + preload 白名单，内嵌文件服务只监听 127.0.0.1 且路径约束在资源根内。

---

## 文档索引

| 主题 | 路径 |
|---|---|
| **文档地图（先看这个）** | [`docs/README.md`](docs/README.md) |
| 全项目与核心训练复核 | [`docs/reviews/2026-09-15-full-project-review.md`](docs/reviews/2026-09-15-full-project-review.md) |
| 前后端一致性专项复核 | [`docs/reviews/2026-09-19-multi-model-debate-cross-stack-review.md`](docs/reviews/2026-09-19-multi-model-debate-cross-stack-review.md) |
| 当前复核整改规格 / 计划 | [`docs/specs/2026-09-15-full-project-fix-spec.md`](docs/specs/2026-09-15-full-project-fix-spec.md)、[`docs/plans/2026-09-15-full-project-fix-plan.md`](docs/plans/2026-09-15-full-project-fix-plan.md) |
| Rust 迁移交接 / 全量重写计划 | [`docs/plans/rust-migration-handoff.md`](docs/plans/rust-migration-handoff.md)、[`docs/plans/2026-09-10-rust-full-rewrite-plan.md`](docs/plans/2026-09-10-rust-full-rewrite-plan.md) |
| Rust 迁移证据 | [`docs/reviews/rust-migration/`](docs/reviews/rust-migration/) |
| 发布手册 | [`docs/guides/2026-09-12-release-runbook.md`](docs/guides/2026-09-12-release-runbook.md) |
| 数据库快照与迁移脚本 | `backend/database/`、`backend/database/migrations/` |
| 迁移演练证据 | `backend/database/rehearsal/` |
