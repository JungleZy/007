# 007 — 舰船报务综合训练系统

单仓两工程：Quarkus 后端 + Electron 桌面端（内含 Vue 前端）。

| 目录 | 内容 | 详细文档 |
|---|---|---|
| [`backend/`](backend/) | Quarkus 3.20.4 / Java 21，REST + WebSocket 服务 | [`backend/README.md`](backend/README.md) |
| [`bw-frontend/`](bw-frontend/) | Electron 桌面外壳（主进程、本地 HTTP 服务、串口桥接、授权校验） | [`bw-frontend/README.md`](bw-frontend/README.md) |
| [`bw-frontend/frontend/`](bw-frontend/frontend/) | Vue 3.5 + Vite 4 前端页面工程 | [`bw-frontend/frontend/README.md`](bw-frontend/frontend/README.md) |

- 仓库：`JungleZy/007`；当前发布版本 `backend/pom.xml` = `1.1.0`
- 面向 AI 编码代理的命令、约定与红线：[`AGENTS.md`](AGENTS.md)（含**提交约定**：完成一个任务就提交，不攒批）

---

## 快速开始

### 后端

```bash
cd backend
export JAVA_HOME=$HOME/.local/opt/jdk21
./mvnw -B clean verify     # 全量测试套件，提交前必跑（需 Docker）
./mvnw quarkus:dev         # 开发模式，监听 18001，REST 前缀 /api
```

数据库准备、profile 与 schema 策略、API 与响应信封约定、业务码表、WebSocket 端点、
迁移执行顺序 —— 全在 [`backend/README.md`](backend/README.md)。

### 前端

```bash
cd bw-frontend/frontend
npm ci             # 已有 package-lock.json，安装结果可复现
npm run dev        # vite --host
npm run build      # 产物 dist/
```

Electron 外壳在 `bw-frontend/` 根（主进程入口 `main.js`）：

```bash
cd bw-frontend
npm ci
npm run dev-e      # 仅起 Electron；dev-f 仅起 Vite；build-e-w / build-e-l 打包
```
> 详见当前综合评审 [`docs/reviews/2026-09-12-full-project-review.md`](docs/reviews/2026-09-12-full-project-review.md)。

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
├── bw-frontend/          # Electron 桌面端
│   ├── electron/         #   主进程、controller、本地 HTTP 服务、串口桥接
│   ├── frontend/         #   Vue 前端（src/、public/、vite.config.js）
│   ├── bin/              #   随包资源（server/ 与 file/ 为产物，不入库）
│   └── main.js           #   Electron 入口
├── docs/                 # 全仓文档唯一位置（2026-09-08 收口）
│   ├── README.md         #   文档地图与路径约定
│   ├── reviews/          #   后端评审 + 前端评审 + 前后端联合评审
│   ├── specs/            #   整改规格
│   ├── plans/            #   整改实施计划
│   └── guides/           #   专题说明
├── .github/workflows/    # CI：后端测试守门 → 三平台原生构建 → tag 发版
├── AGENTS.md
└── README.md
```

文档只收**文字**：库快照、迁移脚本与演练证据是后端工程资产（被 `backend/scripts/rehearse-migrations.sh`
与 `%prod` 部署流程直接消费），留在 `backend/database/`，脚本仍以 `$SCRIPT_DIR/..`（= `backend/`）
一把定位快照、迁移脚本、证据目录与 `target/`。

`docs/` 下 2026-08/09 的历史后端文档，其代码引用（`src/...`、`scripts/...`）仍以 `backend/` 为根、
未逐篇改写；库路径已统一改写为仓库根形式 `backend/database/...`（详见 `docs/README.md` §3）。

CI 的 `run` 步骤统一 `working-directory: backend`；`upload-artifact` 的 `path`
必须写仓库根相对的 `backend/target/*-runner*` —— `working-directory` 不作用于
`uses` 动作的输入。

---

## 当前状态与在执行的整改

| 项 | 状态 |
|---|---|
| 后端测试基线 | **392 测试 / 93 suite 全绿**（`./mvnw -B clean verify`，需 Docker；2026-09-12）|
| 前端测试基线 | `npm run test` **19/19**、`npm run build` 成功 |
| 后端单侧整改（2026-09-07 轮，34 条 P1）| 已完成，4 项偏离已于 2026-09-08 闭合（`docs/plans/archive/2026-09-07-fix-plan.md`）|
| 跨栈整改（2026-09-08 联合评审）| 已闭环（`docs/plans/2026-09-08-joint-fix-plan.md`）|
| 客户报障 12 条整改（2026-09-10）| 六个训练域已落地；仅 T17 现场交付未完成（`docs/plans/2026-09-10-customer-issue-fix-plan.md`）|
| **全项目评审整改（2026-09-12，48 条）** | **已执行** —— 4 条 P0 + 11 条 P1 全部处置，B1–B5/B7 共 32 个提交；执行记录见 `docs/reviews/2026-09-12-full-project-review.md` §6.2 |
| 已知未收口项 | G4 可信证书链、真实训练房间的设备授权环境验收（均为外部前置）；P2/P3 长尾与 `docs/reviews/2026-09-12-full-project-review.md` §7 的「需产品确认」条目 |

本轮已落地的关键安全与契约边界：训练同步端点不再回传会话凭据；非 free controller 全部有类级 `@JWT`（架构测试守卫）；管理写端点有 `@RequireAdmin`，授权拒绝统一 `code:207`；业务终态用 `code:208`；token 为不透明随机串、只从请求头读、DB 存哈希；WebSocket 握手校验凭据并覆盖路径 `uid`；六个训练域 + 组训数据报域的码速/用时一律服务端从原始采集区间重算；桌面渲染进程启用 `contextIsolation` + preload 白名单，内嵌文件服务只监听 127.0.0.1 且路径约束在资源根内。

---

## 文档索引

| 主题 | 路径 |
|---|---|
| **文档地图（先看这个）** | [`docs/README.md`](docs/README.md) |
| **当前全项目评审（唯一入口）** | [`docs/reviews/2026-09-12-full-project-review.md`](docs/reviews/2026-09-12-full-project-review.md) |
| 跨栈契约详细证据（历史，仍有效） | [`docs/reviews/2026-09-08-joint-frontend-backend-review.md`](docs/reviews/2026-09-08-joint-frontend-backend-review.md) |
| 上一轮全项目评审（已被取代） | [`docs/reviews/2026-09-08-full-project-review.md`](docs/reviews/2026-09-08-full-project-review.md) |
| 跨栈修复 spec | [`docs/specs/2026-09-08-joint-fix-spec.md`](docs/specs/2026-09-08-joint-fix-spec.md) |
| 历史评审证据 | [`docs/reviews/archive/`](docs/reviews/archive/) |
| 后端整改规格 / 计划 | `docs/specs/`、`docs/plans/` |
| 数据库快照与迁移脚本 | `backend/database/`、`backend/database/migrations/` |
| 迁移演练证据 | `backend/database/rehearsal/` |
