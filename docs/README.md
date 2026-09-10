# 文档地图（docs/）

全仓文字文档统一存放在 `docs/`。评审资料采用“当前入口 + 历史归档”结构：

- `docs/reviews/2026-09-08-full-project-review.md`：**当前唯一的全项目评审入口**，覆盖前后端、数据库、部署和验证结果。
- `docs/reviews/2026-09-08-joint-frontend-backend-review.md`：当前联合评审的详细契约证据，供修改跨栈接口前查阅。
- `docs/reviews/archive/`：历史评审、分片、审计、整改核销和迁移演练记录，仅作为取证档案，不代表当前代码状态。
- `docs/specs/`：整改规格。
- `docs/plans/`：整改计划和执行记录。
- `docs/guides/`：专题说明。

库快照、迁移脚本和迁移演练产物不属于文字文档，继续保留在 `backend/database/`。

## 阅读顺序

| 目的 | 入口 |
|---|---|
| 了解当前全项目状态 | [`reviews/2026-09-08-full-project-review.md`](reviews/2026-09-08-full-project-review.md) |
| 修改跨栈契约 | [`reviews/2026-09-08-joint-frontend-backend-review.md`](reviews/2026-09-08-joint-frontend-backend-review.md) + 全项目评审 |
| 承接整改任务 | [`specs/2026-09-08-joint-fix-spec.md`](specs/2026-09-08-joint-fix-spec.md) + [`plans/2026-09-08-joint-fix-plan.md`](plans/2026-09-08-joint-fix-plan.md) |
| 查询历史分片证据 | [`reviews/archive/`](reviews/archive/) |
| 查询数据库迁移证据 | `backend/database/rehearsal/` + `reviews/archive/*migration-rehearsal.md` |

## 当前评审入口

### 全项目综合评审

[`2026-09-08-full-project-review.md`](reviews/2026-09-08-full-project-review.md) 是当前 HEAD 的汇总文档，包含：

- P0/P1/P2/P3 优先级问题；
- 后端鉴权、授权、事务、WebSocket 和敏感数据结论；
- 前端会话、XSS、网络、状态管理和构建结论；
- 前后端 API、业务码、文件上传和训练域契约；
- 数据库迁移、CI、版本和交付验证；
- 已修复的历史问题、历史报告勘误和未验证边界。

### 联合评审详细证据

[`2026-09-08-joint-frontend-backend-review.md`](reviews/2026-09-08-joint-frontend-backend-review.md) 保留跨栈契约的详细分析。其分片文件已经移入 `reviews/archive/`，不再作为顶层阅读入口；需要逐条证据时从归档目录查找对应文件。

## 归档规则

`reviews/archive/` 中的文件保留原始评审上下文和历史行号，统一表示历史快照，不作为当前缺陷清单。当前代码状态、运行验证结果和修复优先级以全项目综合评审为准；跨栈端点细节以联合评审为补充。

归档内容包括：

- 2026-08 历史全项目评审及分片；
- 2026-09-07 后端分片、审计和整改核销；
- 2026-09-08 前端单侧评审和联合评审分片；
- 2026-08-28、2026-09-07、2026-09-08 迁移演练文字记录；
- 历史整改验收记录。

## 路径约定

1. 新文档中的代码引用使用仓库根相对路径，例如 `backend/src/main/java/com/nip/common/MainApplication.java:20`、`bw-frontend/frontend/src/common/http/index.js:30`。
2. 历史归档文档保留其原始代码引用和行号，不为迁移归档而重写历史证据。
3. 迁移脚本和演练证据使用 `backend/database/...`；不要重新放回 `docs/`。
4. 具体代码现状与历史文档冲突时，以当前仓库源码和运行验证为准。

## 其他文档

- 后端工程说明：[`../backend/README.md`](../backend/README.md)
- 前端工程说明：[`../frontend/README.md`](../frontend/README.md)
- 工程约定：[`../AGENTS.md`](../AGENTS.md)
- 单仓总览：[`../README.md`](../README.md)
