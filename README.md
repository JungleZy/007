# 007 — 舰船报务综合训练系统

单仓两工程：Quarkus 后端 + Vue 桌面前端。

| 目录 | 内容 | 详细文档 |
|---|---|---|
| [`backend/`](backend/) | Quarkus 3.20.4 / Java 21，REST + WebSocket 服务 | [`backend/README.md`](backend/README.md) |
| [`frontend/`](frontend/) | Vue 3.5 + Vite 4 桌面前端，运行于外部 Electron 外壳 | [`frontend/README.md`](frontend/README.md) |

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
cd frontend
npm install
npm run dev        # vite --host
npm run build      # 产物 dist/
```

> 仓库内无 lockfile（`frontend/.gitignore` 忽略 `package-lock.json`），
> `npm install` 每次重解析 `^` 区间，安装结果不可复现。
> 详见当前综合评审 [`docs/reviews/2026-09-08-full-project-review.md`](docs/reviews/2026-09-08-full-project-review.md)。

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
├── frontend/             # Vue 前端（src/、public/、vite.config.js）
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
| 后端测试基线 | **216 测试全绿**（`./mvnw -B clean verify`，需 Docker）|
| 后端单侧整改（2026-09-07 轮，34 条 P1）| 已完成，4 项偏离已于 2026-09-08 闭合（`docs/plans/2026-09-07-fix-plan.md`）|
| **跨栈整改（2026-09-08 联合评审）** | **待执行** —— 详见当前综合评审和 [`docs/specs/2026-09-08-joint-fix-spec.md`](docs/specs/2026-09-08-joint-fix-spec.md) |
| 已知未收口项 | 前端无 lockfile（构建不可复现）；三处版本号互不相关（`pom.xml` 1.1.0 / `package.json` 0.0.0 / `application.yml` 4.0.1）；CI 只构建后端 |

最高优先的两条（联合评审 §3）：后端管理写端点**零角色校验**（任意登录学员可删任意用户 / 重置管理员密码），
以及前端 7 处仍传 `roomgId` 而后端已改读 `roomId`（房间详情三路失效）。

---

## 文档索引

| 主题 | 路径 |
|---|---|
| **文档地图（先看这个）** | [`docs/README.md`](docs/README.md) |
| **当前全项目评审（唯一入口）** | [`docs/reviews/2026-09-08-full-project-review.md`](docs/reviews/2026-09-08-full-project-review.md) |
| 当前联合评审详细证据 | [`docs/reviews/2026-09-08-joint-frontend-backend-review.md`](docs/reviews/2026-09-08-joint-frontend-backend-review.md) |
| 跨栈修复 spec | [`docs/specs/2026-09-08-joint-fix-spec.md`](docs/specs/2026-09-08-joint-fix-spec.md) |
| 历史评审证据 | [`docs/reviews/archive/`](docs/reviews/archive/) |
| 后端整改规格 / 计划 | `docs/specs/`、`docs/plans/` |
| 数据库快照与迁移脚本 | `backend/database/`、`backend/database/migrations/` |
| 迁移演练证据 | `backend/database/rehearsal/` |
