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
| 后端测试基线 | **228 测试全绿**（`./mvnw -B clean verify`，需 Docker）|
| 后端单侧整改（2026-09-07 轮，34 条 P1）| 已完成，4 项偏离已于 2026-09-08 闭合（`docs/plans/2026-09-07-fix-plan.md`）|
| **跨栈整改（2026-09-08 联合评审）** | **进行中** —— P0 注册、P1 授权/脱敏、房间分页/错误码、导入与协议地址批次已落地；详见 `docs/plans/2026-09-08-joint-fix-plan.md` |
| 已知未收口项 | 生产数据重复清理、未鉴权设备类写端点、其余训练域/WS/富文本/部署回归仍待处理；真实反代与登录态浏览器验收需外部环境 |

已完成的首批高风险闭环：匿名注册不再接受客户端用户 ID 更新已有行；管理写端点已有服务端管理员判定；用户资料与会话凭据分离；业务参数错误使用 202，鉴权码 203/204/206 保持不变。

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
