# 文档地图（docs/）

全仓**文档**的唯一位置。2026-09-08 起 `backend/docs/` 与 `frontend/docs/` 已收口到本目录，两处旧路径不再存在。

| 目录 | 内容 | 数量 |
|---|---|---|
| [`reviews/`](reviews/) | 评审报告：后端分片与汇总、前端汇总、前后端联合评审与其 8 份分片、迁移演练记录 | 36 |
| [`specs/`](specs/) | 整改规格（由评审结论派生，逐条可验收） | 3 |
| [`plans/`](plans/) | 整改实施计划（Task 级 checkbox，含执行结果与偏离记录） | 3 |
| [`guides/`](guides/) | 专题说明 | 1 |

> **不在本目录**：库快照 / 迁移脚本 / 迁移演练证据属于后端工程资产（被 `backend/scripts/rehearse-migrations.sh` 与 `%prod` 部署流程直接消费），放在
> [`../backend/database/`](../backend/database/)：`project006.sql`、`project006-base.sql`、`migrations/`、`rehearsal/`。

---

## 1. 先看哪一份

| 你要做的事 | 权威文档 |
|---|---|
| 改**跨栈契约**（端点参数名、返回形态、业务码、上传/解析能力边界） | [`reviews/2026-09-08-joint-frontend-backend-review.md`](reviews/2026-09-08-joint-frontend-backend-review.md) —— 必读；上一轮后端单侧整改已改断 5 处前端调用面 |
| 改**后端**业务/持久层/WS | [`reviews/2026-09-07-full-project-review.md`](reviews/2026-09-07-full-project-review.md)（汇总为最终结论）+ [`reviews/2026-09-07-review-audit.md`](reviews/2026-09-07-review-audit.md)（独立审计） |
| 改**前端** | [`reviews/2026-09-08-frontend-review.md`](reviews/2026-09-08-frontend-review.md)（注意：其 3.1/3.4 的「后端强校验」降级前提已被联合评审否证；3.3 的「GET 用 `data` 传参」经勘误**维持 LOW**，包装器已转 `params`）|
| 改**实体 / 表结构** | [`../backend/database/migrations/`](../backend/database/migrations/) + [`../backend/database/rehearsal/`](../backend/database/rehearsal/)（`%prod` 是 `generation: validate`，不同步迁移脚本即启动失败）；演练记录见下 §2.4 |
| 承接未完成整改 | 跨栈：[`specs/2026-09-08-joint-fix-spec.md`](specs/2026-09-08-joint-fix-spec.md)（8 批次，含依赖顺序与门禁）；后端单侧：[`specs/`](specs/) → [`plans/`](plans/)（plans 里带每个 Task 的落地/偏离状态） |

代理工程约定与红线：[`../AGENTS.md`](../AGENTS.md)；后端命令与部署：[`../backend/README.md`](../backend/README.md)；单仓总览：[`../README.md`](../README.md)。

---

## 2. reviews/ 分类

### 2.1 前后端联合评审（2026-09-08，跨栈契约唯一权威）

- 汇总：[`2026-09-08-joint-frontend-backend-review.md`](reviews/2026-09-08-joint-frontend-backend-review.md) —— J-P1 9 / J-P2 18 / J-P3 16
- 分片：`2026-09-08-joint-http-contract` · `-envelope-error` · `-auth-session` · `-websocket` · `-training-flow` · `-theory-file` · `-deploy-config` · `-datamodel`

### 2.2 后端评审

- **2026-09-07 轮（当前有效）**：汇总 `2026-09-07-full-project-review.md`；审计 `2026-09-07-review-audit.md`；整改核销 `2026-09-07-remediation-verification.md`；分片 10 份（`controller-api` / `post-train-service` / `general-pat-service` / `theory-user-service` / `simulation-misc-service` / `persistence` / `common-infra` / `ws-concurrency` / `build-delivery` / `test-suite`）。
- **2026-08-26 轮（历史基线）**：`2026-08-26-full-project-review.md` + 审计 + 6 份分片；整改记录 `2026-08-28-fix-spec-remediation.md`。
- 专题：`2026-08-15-situation-display-orbit-placard-review.md`。

### 2.3 前端评审

- **全项目综合评审（2026-09-08）**：[`2026-09-08-full-project-review.md`](reviews/2026-09-08-full-project-review.md) —— 当前 HEAD 的前后端、数据库、部署和验证结论；包含已验证的 P0、历史报告勘误与整改顺序。
- `2026-09-08-frontend-review.md`（HIGH 23 / MEDIUM 31 / LOW 17 / INFO 12；**代码尚未整改**）。

### 2.4 迁移演练记录

- `2026-08-28-migration-rehearsal.md` · `2026-09-07-migration-rehearsal.md` · `2026-09-08-migration-rehearsal.md`（证据目录在 `backend/database/rehearsal/`）。

---

## 3. 路径约定（重要）

1. **新增文档**：所有代码/文件引用一律**相对仓库根**写全，例如 `backend/src/main/java/com/nip/common/MainApplication.java:20`、`frontend/src/common/http/index.js:30`、`backend/database/project006.sql`。
2. **历史文档（2026-08/09 的后端评审、规格、计划）的代码引用未逐篇改写**：其内部的 `src/...`、`scripts/...`、`application.yml`、`pom.xml` 等相对路径仍以 **`backend/`** 为根。它们是带行号的取证快照，改写会破坏与提交历史的对应关系。
   - 例外：库相关路径已在本次收口中**统一改写为仓库根形式** `backend/database/...`（原先写作 `docs/database/...`），因为该目录真实位置变了，不改就是死链。
   - `docs/reviews/...`、`.github/workflows/...` 一直指仓库根，现在也与仓库根一致。
3. **前端评审文档**内部的相对路径以 **`frontend/`** 为根（`src/` 再省略，如 `common/http/index.js`），文首已声明。
4. **代码里的库路径**：`backend/src/main/java/com/nip/common/LifecycleApplication.java`、`backend/src/main/resources/application.yml` 与两个懒建实体的迁移提示统一写 `backend/database/...`（仓库根形式），避免在 `backend/` 下执行命令时误解析。
5. **脚本**：`backend/scripts/rehearse-migrations.sh` 只用 `PROJECT_ROOT`（= `backend/`）定位一切 —— 快照、迁移脚本、演练证据在 `PROJECT_ROOT/database/`，构建产物在 `PROJECT_ROOT/target/`，不再需要仓库根变量。
