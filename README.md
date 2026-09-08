# 007 — 舰船报务综合训练系统

单仓两工程：Quarkus 后端 + Vue 桌面前端。

| 目录 | 内容 | 详细文档 |
|---|---|---|
| [`backend/`](backend/) | Quarkus 3.20.4 / Java 21，REST + WebSocket 服务 | [`backend/README.md`](backend/README.md) |
| [`frontend/`](frontend/) | Vue 3.5 + Vite 4 桌面前端，运行于外部 Electron 外壳 | [`frontend/README.md`](frontend/README.md) |

- 仓库：`JungleZy/007`
- 面向 AI 编码代理的命令、约定与红线：[`AGENTS.md`](AGENTS.md)

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
> 见 [`frontend/docs/2026-09-08-frontend-review.md`](frontend/docs/2026-09-08-frontend-review.md) 的 HIGH 项。

---

## 目录结构

```
.
├── backend/              # Quarkus 服务（整棵子树自洽，所有内部相对引用以此为根）
│   ├── src/              #   main/java/com/nip/{common,controller,service,dao,entity,dto,ws}
│   ├── docs/             #   评审、整改规格与计划、DB 快照与迁移、演练证据
│   ├── scripts/          #   rehearse-migrations.sh（双快照迁移演练）
│   ├── pom.xml           #   com.nip:quarkus-template
│   └── mvnw, mvnw.cmd    #   Maven Wrapper，无需预装 Maven
├── frontend/             # Vue 前端
│   └── docs/             #   前端评审报告
├── .github/workflows/    # CI：后端测试守门 → 三平台原生构建 → tag 发版
├── AGENTS.md
└── README.md
```

后端子树平移时刻意保持了内部相对路径不变：`scripts/rehearse-migrations.sh` 仍按
`$SCRIPT_DIR/..` 定位工程根，后端文档里的 `src/main/java/...`、`docs/database/...`
引用一律相对 `backend/`，无需改写。

CI 的 `run` 步骤统一 `working-directory: backend`；`upload-artifact` 的 `path`
必须写仓库根相对的 `backend/target/*-runner*` —— `working-directory` 不作用于
`uses` 动作的输入。

---

## 文档索引

| 主题 | 路径 |
|---|---|
| 后端评审权威结论 | `backend/docs/reviews/2026-09-07-full-project-review.md` |
| 评审独立审计 | `backend/docs/reviews/2026-09-07-review-audit.md` |
| 后端整改规格 / 计划 | `backend/docs/specs/`、`backend/docs/plans/` |
| 数据库快照与迁移脚本 | `backend/docs/database/` |
| 迁移演练证据 | `backend/docs/database/rehearsal/` |
| 前端评审 | `frontend/docs/2026-09-08-frontend-review.md` |
