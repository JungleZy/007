# AGENTS.md

面向 AI 编码代理的工程指南。项目背景与上手见 [`README.md`](README.md)；本文只讲**命令、约定与红线**。

## 构建与测试

始终显式指定 JDK 21：

```bash
export JAVA_HOME=$HOME/.local/opt/jdk21
./mvnw -B clean verify     # 全量测试套件，提交前必跑；需 Docker（Testcontainers 起 MySQL）
./mvnw quarkus:dev         # 本地开发，热重载
./mvnw -B clean package    # fast-jar 产物 target/quarkus-app/
```

- 测试期无需本地 MySQL：`%test` 用 DevServices 拉起 `mysql:8.0`（库 `project006_test`，`drop-and-create`），但**必须有 Docker**。
- 只改一处时优先跑受影响的单测类，最后再 `verify` 全量；不要 `-DskipTests` 交付。

## 运行时关键事实（易踩）

- **端口 18001**，不是 8080。REST 前缀 **`/api`**（`common/MainApplication.java` 的 `@ApplicationPath`）。测试端口 18081。
- **响应恒为 HTTP 200**，业务状态在 JSON `code` 字段。禁止用 HTTP 状态码表达业务错误。
- **鉴权头**：`token` + `deviceId`（`common/constants/BaseConstants`）。类级 `@JWT` 拦截，`controller/free/**` 免鉴权。
- 生产库 schema 策略 `validate`：改实体/表结构必须同步 `docs/database/migrations/` 迁移脚本，否则 `%prod` 启动失败。

## 代码约定

- **响应信封**：一律经 `common/response/ResponseResult.success(...)` / `error(...)` 返回 `Response<T>`；不要手写 JSON 或直写 `HttpServerResponse`。
- **业务码**：新增码加到 `common/constants/ResponseCode`。鉴权码 **203/204/206** 是客户端契约，码值与文案**禁止改动**；不得再引入与现有码同码同义的重复项（历史上 CODE_200/202/500 已被清理）。
- **异常**：端点业务异常交给 `common/exception/*ExceptionMapper` 统一转 200 信封；不要在业务层吞异常后返回 `error()`（见下方红线）。
- **持久层**：DAO 继承 Panache 风格（`dao/`），实体在 `entity/`。物理命名走 `common/utils/CustomPhysicalNamingStrategy`（`application.yml` 已配）。
- **常量**：header 名、参数名、训练状态字符串统一取 `BaseConstants`，不要散落字面量。
- **分层**：`controller`（薄） → `service`（业务/事务） → `dao`。子域按包划分（`general/`、`simulation/`、`detector/`）。

## 红线（评审已确认的系统性缺陷，改动时务必规避）

1. **`@Transactional` 内 catch 吞异常 → 部分提交/数据丢失**。事务方法里捕获异常后若要中止，必须重抛或 `setRollbackOnly()`；不要「catch 后 `return error()`」让事务照常提交。
2. **MyISAM 表不可回滚**：`docs/database/project006.sql` 仍有 22 张 MyISAM 表。「先删后插」结算逻辑在这些表上中断即永久丢数据。改动结算路径前确认目标表已转 InnoDB（迁移 02）。
3. **WebSocket 端点是 `@ApplicationScoped` 单例**：实例字段跨连接共享，禁止把会话态存实例字段；用 `Session` 维度的容器。
4. `getUserByToken` 等在凭证过期时返回 `null`：下游调用点必须判空。

## 测试约定

- 遵循现有 Testcontainers + REST Assured 风格；测试必须自洽（不依赖本地库、可并行、全量安全）。
- 只为「真实可能失败的可观察契约」写测试；不要为「让改动有测试」而写断言实现细节的用例。临时验证用一次性脚本，别留进测试套件。

## 文档与权威来源

- 评审结论以 `docs/reviews/2026-09-07-full-project-review.md` 汇总为准（附 `*-review-audit.md` 独立审计）。
- 整改规格/计划在 `docs/specs/`、`docs/plans/`；迁移演练在 `docs/database/rehearsal/`。
- 若代码现状与文档/记忆冲突，以**仓库现状 + 运行验证**为准。

## 提交前检查

- [ ] `./mvnw -B clean verify` 全绿（含 Docker）。
- [ ] 改了导出符号 / 端点 / 实体，已用 `lsp references` 核对所有调用点与迁移脚本。
- [ ] 未新增 shim/别名/废弃路径；调用点已整体切换。
- [ ] 未触碰 203/204/206 契约；未在事务内吞异常。
