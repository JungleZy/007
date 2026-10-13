# Rust 迁移交接（每次接手先读本文件）

最后更新：2026-10-13，基线提交见 `git log -1`。本文件只记"现状与下一步"；历史证据在 `docs/reviews/rust-migration/` 与各提交正文，不要在这里追加长段落。

## 现状
- 接口层双栈对账：B4 用户/角色/菜单、WS 逐帧设施、B5 组训三域、B6 仿真、B7 报话/军语 **已完成**，全部旅程 2252 请求 / 全量扫描 3701 请求，0 FAIL/OPEN（cf404716 时点）。
- **B8 理论题库余项与杂项：调研完成，判断题待用户拍板（见下），未开始修。** 这是最后一批接口覆盖。
- 未做：单域真实切换与回滚演练、浏览器/Electron 实测、Windows/arm64 构建、生产切换。Phase10（下线 Java）仍阻断。
- 所有提交均未 push。

## 工作方式（用户要求，全局 ~/.claude/CLAUDE.md 第 5 条）
- 主会话（Opus）只调度与拍板；执行交给子代理：改代码/跑对账用 Sonnet，检索用 Haiku。子代理回报 ≤300 字。
- 共享双栈与对账库：同一时间只派一个写代码/跑对账的子代理；只读调研可并行。
- 子代理遇到判断题停下上报，由用户拍板；拍板结论写进本文件"已拍板口径"。
- 每批流程：只读调研（产出 `bN-research/*.md` 与旅程草稿）→ 用户拍板 → 一个 Sonnet 子代理：实跑草稿并修草稿 → 修 Rust → 旅程并入 `dual-stack-journeys.json` → 锁改动配隔离库回归 + 每处一次变异 → 门禁 → 两个提交（`fix(...)` 代码+回归；`test(rust-contract)` 旅程+registry）。
- 门禁：`cargo fmt --check`、`cargo test --locked`、相关 ignored 隔离库测试、python unittest、`$S journeys` 全部 0 FAIL/OPEN、一次 `$S full` 0 FAIL/OPEN、`$S down`。
- 子代理撞额度限额或会话结束会中断：工作区保留未提交改动，用 SendMessage 让它从断点继续（先 `docker start mysql-project006`）。子代理指令里写明"门禁一过就提交""不要设空闲计时器"。

## 环境与命令（WSL，本机已就绪）
```bash
S=backend-rust/scripts/local-dual-stack.sh
$S setup      # 仅新机器：MySQL 容器 + 快照 + 迁移 + 隔离库
$S build      # 改了 Java 或 Rust 后
docker start mysql-project006   # 容器停了时
$S schema && $S up
$S journeys [文件]   # 默认全部旅程；输出 FAIL/OPEN 摘要，exit 1 表示有未解决差异
$S full              # 提交前一次
$S down
RUST_PHASE2_DATABASE_URL=mysql://root:root@127.0.0.1:3306/rust_scratch_lockdemo \
  cargo test --locked -p rust-backend <模块>:: -- --ignored --test-threads=1
python3 -m unittest discover -s backend-rust/scripts -p 'test_*.py'
```
JDK：`~/.local/opt/jdk21`。git 作者已在仓库本地配置为 Jungle。旅程格式见 `dual-stack-sweep.py` 顶部；支持 `headers` 会话步骤、`ws` 步骤、`presence`、`parse_data`。harness 的 POST 一律带 JSON 体，无体请求需 curl 另验。

## 已拍板口径（后续批次默认沿用）
- 业务码：状态类错误按 Java 202（不用 208）；非成员/非学员按 Java；Java 的 500 一般不照搬，Rust 返回定义好的 202 并登记——例外：报文页组数非 10 倍数且末行错码时照 Java 500（用户 3b）。
- 计分/取整/时长：一律按 Java（ScoreMath 取整、向上取整时长等）；Java 计分缺陷照抄并登记为已知 Java 缺陷（如缺页重复扣分）。
- 锁：先锁父行/训练行主键、普通读出子行 id、按主键写；读接口不加 FOR UPDATE；不加索引、不改表结构。
- 刻意差异（已登记）：Rust 更严的状态机/权限、Java 双重编码或乱值且前端不读的字段、时钟回跳时 Rust 截 0、listPage 无体 Java 415/Rust 200、非成员读页不写库、规则缺 alterError 回退 correctMistakes。
- 数据：不复制会写脏数据的 Java 行为（非成员读页建页）；Java 有意保留数据的行为照做（如开始前断线删成员照 Java）。

## B8 理论题库余项与杂项（下一步）
调研：`docs/reviews/rust-migration/b8-research/misc.md`、旅程草稿 `misc-journeys.draft.json`（10 条 194 步，未实跑）。范围：52 个未覆盖端点，39 个有前端调用；草稿覆盖 47 个，另 5 个（2 个 multipart、importTrainInfoBatch、openApi 中文路径、verifyPassword 文本体）需 curl 一次性验证。

直接修（Rust 缺陷，按 Java）：
- `gradeCount` 是桩，恒返回空（theory.rs:779-806 对 TheoryKnowledgeService.java:537-690），成绩图全空——完整移植。
- `createUserName` 恒 null（theory.rs:127,232；theory_question.rs:147）——补联表。
- `examineAnalyse` 截断 3 条（exam.rs:1396）——返回全部。
- 题库名称搜索大小写敏感（theory_question.rs:134）——改为与 Java LIKE 一致。
- 重复评分规则 Rust 500（device.rs:241）——按 Java 202。
- 404/405：WS 通配路由只注册 GET（main.rs:1276-1284）导致 POST 未知子路径得 405；修法 `get(ws::open).fallback(not_found)` 加根 `.fallback`，不要用 `method_not_allowed_fallback`（会把真实路由的 405 改成 404）。
- cable 类型列表顺序：静态看两栈应一致，实跑确认。
- 非主键锁（theory.rs:943-963、theory_question.rs:193、theory_test.rs:74、masthead.rs:93）——按既定锁方案。

**待用户拍板（主会话建议，用户尚未确认）**：
1. 删理论课程时 Rust 连删 5 张子表、删题库节点连删其下题目，Java 只删本身 → 建议按 Java 不连删（被连删的可能是学习记录/成绩/被试卷引用的题，删错不可恢复）。
2. `getBasicTheoryOpen` Rust 采用请求体 status（学员可看草稿），Java 固定 1；Java 只显示有分类、有课件的课程 → 建议按 Java。
3. `getTestContentById` Rust 多一层 207 权限检查；`view`/`uploadFileToNip` Rust 能力是 Java 超集 → 建议保留 Rust，登记刻意差异。
4. 无前端调用的端点 → 建议不写旅程，只保留全量扫描最小覆盖（已有夹具的 4 个已放进草稿）。

## B8 之后
1. 遗留观察：`personal-ticker-tape` 偶发 FAIL 未复现。
2. 单域真实切换与回滚演练：选一个已对齐、低风险的域，在网关上 Java→Rust 切流量、写入、回滚、再切（`backend-rust/scripts/phase9-cutover-rehearsal.sh` 目前只验证 Rust 单独起停）。
3. 浏览器/Electron 实测（Rust 作为 Electron 子进程：登录、上传、训练、WS、退出）；Windows（mingw）与 arm64 交叉编译 CI。
4. 以上完成后再评估 Phase10 前置条件（`docs/reviews/rust-migration/phase10-preconditions.json`）。
