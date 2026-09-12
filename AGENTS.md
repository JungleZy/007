# AGENTS.md

面向 AI 编码代理的工程指南。项目背景与上手见 [`README.md`](README.md)；本文只讲**命令、约定与红线**。

## 仓库布局与路径约定

单仓两工程：`backend/`（Quarkus 服务）+ `bw-frontend/`（Electron 桌面外壳，Vue 前端在 `bw-frontend/frontend/`）。**本文以后端为主**；「红线 5/6」与「提交约定」对两侧同时适用。

- 所有 Maven 命令在 **`backend/`** 下执行。
- 本文的 Java 路径相对 `backend/src/main/java/com/nip/`（如 `common/MainApplication.java`）。
- **全仓文档统一在仓库根 `docs/`**（2026-09-08 收口，`backend/docs/` 已不存在）：`docs/reviews/`（后端 + 前端 + 联合评审）、`docs/specs/`、`docs/plans/`、`docs/guides/`。文档路径一律相对仓库根写全（如 `docs/reviews/...`）；文档地图见 [`docs/README.md`](docs/README.md)。
- **库资产不在 `docs/`**：快照 `backend/database/project006[-base].sql`、迁移 `backend/database/migrations/`、演练证据 `backend/database/rehearsal/` 属后端工程资产（`backend/scripts/rehearse-migrations.sh` 以 `backend/` 为根消费）。
- 当前全项目评审见 `docs/reviews/2026-09-12-full-project-review.md`（**唯一入口**）。其 P0/P1（授权层缺失、凭证协议回传、组训数据报/电传域整体未迁移、桌面交付）在未处置前，是新增同类代码时的必读约束。跨栈契约的详细取证仍看 `docs/reviews/2026-09-08-joint-frontend-backend-review.md`，跨栈整改规格见 `docs/specs/2026-09-08-joint-fix-spec.md`；`docs/reviews/2026-09-08-full-project-review.md` 已降为历史证据。历史分片统一在 `docs/reviews/archive/`，不作为当前状态依据。

## 构建与测试

始终显式指定 JDK 21，并在 `backend/` 下执行：

```bash
cd backend
export JAVA_HOME=$HOME/.local/opt/jdk21
./mvnw -B clean verify     # 全量测试套件，提交前必跑；需 Docker（Testcontainers 起 MySQL）
./mvnw quarkus:dev         # 本地开发，热重载
./mvnw -B clean package    # fast-jar 产物 target/quarkus-app/
```

- 测试期无需本地 MySQL：`%test` 用 DevServices 拉起 `mysql:8.0`（库 `project006_test`，`drop-and-create`），但**必须有 Docker**。
- 只改一处时优先跑受影响的单测类，最后再 `verify` 全量；不要 `-DskipTests` 交付。
- 当前基线 **316 测试 / 74 suite 全绿**（`docs/plans/2026-09-10-customer-issue-fix-plan.md` T17、`docs/reviews/2026-09-12-full-project-review.md`）；新增测试只增不减。

## 运行时关键事实（易踩）

- **端口 18001**，不是 8080。REST 前缀 **`/api`**（`common/MainApplication.java` 的 `@ApplicationPath`）。测试端口 18081。
- **响应恒为 HTTP 200**，业务状态在 JSON `code` 字段。禁止用 HTTP 状态码表达业务错误。
- **鉴权头**：`token` + `deviceId`（`common/constants/BaseConstants`）。类级 `@JWT` 拦截，`controller/free/**` 免鉴权。
- 生产库 schema 策略 `validate`：改实体/表结构必须同步 `backend/database/migrations/` 迁移脚本，否则 `%prod` 启动失败。

## 代码约定

- **响应信封**：一律经 `common/response/ResponseResult.success(...)` / `error(...)` 返回 `Response<T>`；不要手写 JSON 或直写 `HttpServerResponse`。
- **业务码**：新增码加到 `common/constants/ResponseCode`。鉴权码 **203/204/206** 是客户端契约，码值与文案**禁止改动**；不得再引入与现有码同码同义的重复项（历史上 CODE_200/202/500 已被清理）。
- **异常**：端点业务异常交给 `common/exception/*ExceptionMapper` 统一转 200 信封；不要在业务层吞异常后返回 `error()`（见下方红线）。
- **持久层**：DAO 继承 Panache 风格（`dao/`），实体在 `entity/`。物理命名走 `common/utils/CustomPhysicalNamingStrategy`（`application.yml` 已配）。
- **常量**：header 名、参数名、训练状态字符串统一取 `BaseConstants`，不要散落字面量。
- **分层**：`controller`（薄） → `service`（业务/事务） → `dao`。子域按包划分（`general/`、`simulation/`、`detector/`）。

## 红线（评审已确认的系统性缺陷，改动时务必规避）

1. **`@Transactional` 内 catch 吞异常 → 部分提交/数据丢失**。事务方法里捕获异常后若要中止，必须重抛或 `setRollbackOnly()`；不要「catch 后 `return error()`」让事务照常提交。
2. **改结算路径前先确认目标表引擎**：MyISAM 不支持事务，`@Transactional` 回滚在其上是空操作，「先删后插」结算一旦中断即永久丢数据。当前快照 `backend/database/project006.sql` 已是 **105 张表全 InnoDB、0 张 MyISAM**（迁移 02 已回灌）；22 张 MyISAM 只存在于迁移前 base 快照 `backend/database/project006-base.sql`（78 InnoDB + 22 MyISAM，仅供迁移演练）。生产兜底是启动自检：`common/LifecycleApplication.checkStorageEngine` 扫 `information_schema`，`%prod` 发现 MyISAM 抛 `IllegalStateException` 阻断启动，dev/test 只告警。
3. **WebSocket 端点是 `@ApplicationScoped` 单例**：实例字段跨连接共享，禁止把会话态存实例字段；用 `Session` 维度的容器。
4. **token 查询有两条口径，别混用**：`UserService.getUserByToken` 查无即抛 `UnauthorizedException`（→ 200 + 203），可直接用；而裸 DAO `UserDao.findUserEntityByToken` 用 `firstResult()`，**查无返回 null**，当前 **24 处调用点、分布在 11 个 service**（`TickerTapeTrainService` 5 处、`EnteringExerciseService` 4 处、`EnteringTelexPatService` 3 处，`RadiotelephoneService`/`GeneralKeyPatService` 等其余 8 个各 1–2 处）直接 `userEntity.getId()` 解引用 → 凭证失效即 NPE。同类裸 `firstResult()` 全仓 **55 处 / 42 个 DAO 文件**。新代码走 `getUserByToken`，不要新增裸 DAO 解引用。行号会随改动漂移，用 `grep -rn findUserEntityByToken backend/src/main/java` 现取。
5. **跨栈契约不可单侧改**：改 `@RestQuery`/`@RestForm` 参数名、返回形态（`Response<T>`↔字节流↔void）、业务码语义、上传/解析能力边界前，必须 grep 前端 `bw-frontend/frontend/src/common/api/*.js`（28 个模块即完整契约清单）与实际调用点，并把结果贴进提交正文。历史上后端单侧整改曾一次改断 4 处跨栈契约（`roomgId` 改名、上传能力边界收窄、`saveBatch`/`exportTemplate` 成孤儿端点、`Page.getRows()` 钳制），取证见 `docs/reviews/2026-09-08-joint-frontend-backend-review.md`；这 4 处**均已闭环**，作为历史教训保留，红线规则本身继续有效。
6. **鉴权 ≠ 授权**：后端管理写端点（`user/role/menu` 的 delete/reset/addUserRole/addRole）目前只有类级 `@JWT`，无任何角色校验，前端 `v-per` 只是可篡改的软门控。新增管理类端点必须自己做服务端授权判定。

## 测试约定

- 遵循现有 Testcontainers + REST Assured 风格；测试必须自洽（不依赖本地库、全量安全）。
- **套件当前串行执行，禁止引入并行**（`backend/pom.xml` 的 surefire 未配置并行）：WS 会话态是进程级 `static` 表，多个测试类会直接清零它们（清零已收敛到 `backend/src/test/java/com/nip/testsupport/` 下的共享助手），并行会互相踩。
- 只为「真实可能失败的可观察契约」写测试；不要为「让改动有测试」而写断言实现细节的用例。临时验证用一次性脚本，别留进测试套件。

## 文档与权威来源

- 当前项目评审结论以 `docs/reviews/2026-09-12-full-project-review.md` 为唯一入口；其 P0/P1 在未处置前是新增同类代码的必读约束。
- `docs/reviews/2026-09-08-full-project-review.md` 已降为历史证据；**跨栈问题的详细取证仍以 `docs/reviews/2026-09-08-joint-frontend-backend-review.md` 为准**，汇总结论以当前全项目评审为准。更早的后端评审、审计和分片位于 `docs/reviews/archive/`，仅用于追溯。跨栈整改规格为 `docs/specs/2026-09-08-joint-fix-spec.md`，客户报障整改计划为 `docs/plans/2026-09-10-customer-issue-fix-plan.md`（T17 未完成）。
- 整改规格/计划在 `docs/specs/`、`docs/plans/`；迁移演练在 `backend/database/rehearsal/`；后端专题说明在 `docs/guides/`。
- 若代码现状与文档/记忆冲突，以**仓库现状 + 运行验证**为准。

## 提交约定

- **完成一个任务就提交，不要攒批**：一个 Task / 一条缺陷 / 一处可独立回滚的改动 = 一个 commit。禁止把多个不相关改动堆成一个大提交（历史上「每条 P1 一次提交」的口径就是因为攒批而永久未达成，见 `docs/specs/archive/2026-09-07-fix-spec.md` DoD 第 2 条）。
- 提交粒度判据：这个 commit 能不能被单独 revert 而不破坏其余功能？不能 → 拆小或合并到它真正依赖的那个 commit。
- 例外（必须同一 commit）：跨栈契约改动的两侧、重命名/移动与其引用更新、修复与其回归测试 —— 拆开会产生编译不过或链接悬空的中间提交。
- 每个 commit 交付前至少跑受影响的单测类；**推送前**跑一次 `./mvnw -B clean verify` 全绿。
- 信息格式沿用 `type(scope): 中文摘要`（`feat`/`fix`/`refactor`/`docs`/`test`/`chore`）。正文写「为什么」与验证方式；改了跨栈契约的，把 grep 前端调用面的结果贴进正文（红线 5）。
- 不要 `--amend` 或 `rebase` 已推送的提交；不要用 `git add -A` 顺手带入无关文件（提交前 `git status --short` 过一遍）。

## 提交前检查

- [ ] `cd backend && ./mvnw -B clean verify` 全绿（含 Docker）。
- [ ] 改了导出符号 / 端点 / 实体，已用 `lsp references` 核对所有调用点与迁移脚本。
- [ ] 未新增 shim/别名/废弃路径；调用点已整体切换。
- [ ] 未触碰 203/204/206 契约；未在事务内吞异常。
- [ ] 改了跨栈契约（参数名/返回形态/业务码/能力边界），已核对前端调用面并同步（见红线 5）。
- [ ] 本次改动是一个**独立可回滚**的任务单元（见「提交约定」），不是多任务攒批。
