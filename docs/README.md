# 文档地图（docs/）

全仓文字文档统一存放在 `docs/`。评审资料采用“当前入口 + 历史归档”结构：

- `docs/reviews/2026-09-12-full-project-review.md`：**当前唯一的全项目评审入口**（2026-09-12 基线，48 条发现 / P0 4 条），覆盖前后端、数据库、交付形态、测试与文档一致性。**其 P0/P1 已全部处置**，执行记录见该文 §6.2。
- `docs/reviews/2026-09-10-customer-issue-analysis.md`：客户报障 12 条的根因分析与取证，是本轮整改的事实基础。
- `docs/reviews/2026-09-08-full-project-review.md`：上一轮全项目评审，**降为历史证据**（其 216 测试基线等数字已过期，勿作为当前状态依据）。
- `docs/reviews/2026-09-08-joint-frontend-backend-review.md`：跨栈契约的详细历史证据，供修改跨栈接口前查阅。
- `docs/reviews/archive/`：历史评审、分片、审计、整改核销和迁移演练记录，仅作为取证档案，不代表当前代码状态。
- `docs/specs/`：整改规格；`docs/specs/archive/`：已闭环的历史规格（2026-08-26、2026-09-07、2026-09-08 偏离闭合）。
- `docs/plans/`：整改计划和执行记录；`docs/plans/archive/`：已闭环的历史计划（2026-08-26、2026-08-28、2026-09-07 波次与 2026-09-09 已完成的决策/spike）。
- `docs/guides/`：专题说明与发布执行清单（含 [`2026-09-12-release-runbook.md`](guides/2026-09-12-release-runbook.md)：迁移顺序与逐脚本还原、会话失效通告、凭据注入前提、桌面包发布形态）。

库快照、迁移脚本和迁移演练产物不属于文字文档，继续保留在 `backend/database/`。

## 阅读顺序

| 目的 | 入口 |
|---|---|
| 了解当前全项目状态 | [`reviews/2026-09-12-full-project-review.md`](reviews/2026-09-12-full-project-review.md) |
| **发布这一批整改** | [`guides/2026-09-12-release-runbook.md`](guides/2026-09-12-release-runbook.md) |
| 了解客户报障根因 | [`reviews/2026-09-10-customer-issue-analysis.md`](reviews/2026-09-10-customer-issue-analysis.md) |
| 承接当前整改任务 | [`specs/2026-09-12-review-fix-spec.md`](specs/2026-09-12-review-fix-spec.md) + [`plans/2026-09-12-review-fix-plan.md`](plans/2026-09-12-review-fix-plan.md)（B1–B5/B7 已执行，见评审 §6.2） |
| 承接客户报障整改（T17 现场交付未完成） | [`specs/2026-09-10-customer-issue-fix-spec.md`](specs/2026-09-10-customer-issue-fix-spec.md) + [`plans/2026-09-10-customer-issue-fix-plan.md`](plans/2026-09-10-customer-issue-fix-plan.md) |
| 修改跨栈契约 | [`reviews/2026-09-12-full-project-review.md`](reviews/2026-09-12-full-project-review.md) §5.5 + [`reviews/2026-09-08-joint-frontend-backend-review.md`](reviews/2026-09-08-joint-frontend-backend-review.md) |
| 会话与口令协议迁移 | [`plans/2026-09-09-password-session-migration-plan.md`](plans/2026-09-09-password-session-migration-plan.md) |
| 前端 Worker/WASM 可行性 | [`plans/2026-09-09-frontend-wasm-feasibility-report.md`](plans/2026-09-09-frontend-wasm-feasibility-report.md) |
| 承接历史联合整改（已闭环部分） | [`specs/2026-09-08-joint-fix-spec.md`](specs/2026-09-08-joint-fix-spec.md) + [`plans/2026-09-08-joint-fix-plan.md`](plans/2026-09-08-joint-fix-plan.md) |
| 全量 Rust 后端重写计划 | [`plans/2026-09-10-rust-full-rewrite-plan.md`](plans/2026-09-10-rust-full-rewrite-plan.md) |
| Rust 后端重构可行性评估 | [`specs/2026-09-10-rust-backend-feasibility.md`](specs/2026-09-10-rust-backend-feasibility.md) |
| 查询历史分片证据 | [`reviews/archive/`](reviews/archive/) |
| 查询数据库迁移证据 | `backend/database/rehearsal/` + `reviews/archive/*migration-rehearsal.md` |

## 当前评审入口

### 全项目综合评审

[`2026-09-12-full-project-review.md`](reviews/2026-09-12-full-project-review.md) 是当前 HEAD（`0efbdf1`）的汇总文档，包含：

- 48 条发现（P0 4 / P1 11 / P2 24 / P3 9）与六个根因簇；
- 后端授权边界、凭据协议、评分采集、并发与结算结论；
- 组训数据报/电传域未纳入上一轮迁移的证据与二选一处置（已选「修复」）；
- 桌面交付形态（内嵌文件服务、渲染进程安全特性、随包地址、CI 缺口）；
- 测试与文档一致性（`%test` 调度器竞态、AGENTS.md 过期断言）；
- §5 的已核实正确点清单（避免重复排查）、§6 整改批次、**§6.1/§6.2 两份执行记录（B6 与 B1–B5/B7，含提交号与运行证据）**、§7 待运行验证事项。

整改后基线：后端 391 测试 / 93 suite 全绿、前端 19/19 + build 成功、迁移演练双快照全绿。

上一轮 [`2026-09-08-full-project-review.md`](reviews/2026-09-08-full-project-review.md) 仅作历史对照，其测试基线与 MyISAM 等数字已过期。

### 联合评审详细证据

[`2026-09-08-joint-frontend-backend-review.md`](reviews/2026-09-08-joint-frontend-backend-review.md) 保留跨栈契约的详细分析。其分片文件已经移入 `reviews/archive/`，不再作为顶层阅读入口；需要逐条证据时从归档目录查找对应文件。

## 归档规则

`reviews/archive/` 中的文件保留原始评审上下文和历史行号，统一表示历史快照，不作为当前缺陷清单。当前代码状态、运行验证结果和修复优先级以全项目综合评审为准；跨栈端点细节以联合评审为补充。

归档内容包括：

- 2026-08 历史全项目评审及分片；
- 2026-09-07 后端分片、审计和整改核销；
- 2026-09-08 前端单侧评审和联合评审分片；
- 2026-08-28、2026-09-07、2026-09-08 迁移演练文字记录；
- `specs/archive/` 与 `plans/archive/`：已闭环验收的历史整改规格与计划，仅作追溯；
- 历史整改验收记录。

## 路径约定

1. 新文档中的代码引用使用仓库根相对路径，例如 `backend/src/main/java/com/nip/common/MainApplication.java:20`、`bw-frontend/frontend/src/common/http/index.js:30`。
2. 历史归档文档保留其原始代码引用和行号，不为迁移归档而重写历史证据。
3. 迁移脚本和演练证据使用 `backend/database/...`；不要重新放回 `docs/`。
4. 具体代码现状与历史文档冲突时，以当前仓库源码和运行验证为准。

## 其他文档

- 后端工程说明：[`../backend/README.md`](../backend/README.md)
- 前端工程说明：[`../bw-frontend/frontend/README.md`](../bw-frontend/frontend/README.md)
- 桌面壳说明：[`../bw-frontend/README.md`](../bw-frontend/README.md)
- 工程约定：[`../AGENTS.md`](../AGENTS.md)
- 单仓总览：[`../README.md`](../README.md)
