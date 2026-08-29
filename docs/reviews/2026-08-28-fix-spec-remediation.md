# 2026-08-28 修复 Spec 整改验收报告

**结论：`fix/spec-remediation` 已完成本次约定的高风险修复子集，并在八个修复提交全部落地后通过全量验证；这不表示 2026-08-26 spec 所列 348 项确认缺陷已经全部完成。** 本报告固定本次范围、验收证据和仍需后续处理的已知工作。

## 本次范围

本次关闭的是按顺序约定的高风险子集：

- 三条残余 P0 路径；
- Union CAS 与空房间清理；
- Simulation/General 按 Session 精确管理的生命周期；
- 已确认的批 7 事务缺口；
- 定向处理的 `findById` 与“假成功”路径。

原 spec 中其余批 7/P2 家族不在本次完成声明内，详见“剩余已知工作”。

## 基线与修复提交

分支为 `fix/spec-remediation`，基于 main `5c7b0a6`。

| 提交 | 修复范围 |
|---|---|
| `0cfcb51` | 三条残余 P0 路径 |
| `1a7623b` | Union 精确到 Client 的清理 |
| `d490616` | Simulation/General 精确到 Session 的生命周期 |
| `79ce9ff` | 多步骤写操作的事务回滚边界 |
| `c631a77` | 实体缺失语义与军语删除顺序 |
| `a0ac3c6` | Union 空房间生命周期与确定性 remove 屏障 |
| `3a54ccd` | 闭合消息重连与删房竞态 |
| `a9ae85b` | 保留同用户重连的房间状态 |

## 八个提交后的全量验证

八个提交全部落地后重新执行：

```text
JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify
```

结果：

```text
Tests run: 112, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 01:58 min
Finished at: 2026-08-28T18:12:27+08:00
```

其他验收证据：

- `docker build -f src/main/docker/Dockerfile.jvm .`：成功；镜像 manifest list 为 `sha256:264d4dcd968b8f8b45c5a522a73fdc3c0f1a9e3c2238130871a079ba043564a3`。
- `docker run --rm ... rhysd/actionlint:latest`：退出码 0，无输出。
- 第一方 Java 静态门禁：空 catch 为 0；使用 `getMessage()` 的日志调用为 0；`@ServerEndpoint` 中按正则检查的每连接 `Session`/`UserModel` 字段为 0。
- `git diff --check main...HEAD`：退出码 0。
- Java LSP 诊断不可用，工具返回 `No language server found`；Java 诊断以实际编译和上述 `clean verify` 结果为准。

验收时间线：最早一次验证为 97/1（97 个通过、1 个失败），Union `onlineRooms` 泄漏促成 `a0ac3c6`；随后 98/0 是中间结果。最终审查又发现 4 项 Important，均由 `3a54ccd` 处理；定向残余审查发现的问题由 `a9ae85b` 处理。最终定向审查的 4 项 Important 与 1 项 residual 均为 `ADDRESSED/PASS`，八个提交后的 112/0 是权威结果；会话内产物未作为版本化证据引用。

## Definition of Done 状态

| 原 DoD | 本次状态 | 证据或边界 |
|---|---|---|
| 1. `clean verify` 全绿且测试数 ≥ 30 | 已满足 | 八个提交后共运行 112 个测试，失败、错误、跳过均为 0。 |
| 2. 空 catch、WebSocket 会话态实例字段、`log.*(...getMessage())` 静态门禁 | 已满足 | 三项第一方 Java 静态门禁均为 0。 |
| 3. 迁移脚本快照库演练及 prod `generation: validate` 启动 | 已满足 | 2026-08-28 由 `scripts/rehearse-migrations.sh` 在一次性 Docker 容器上对 current+base 双快照重跑迁移 01→02：全断言绿、validate 等价差分归零；prod jar 以 `%prod` 启动零 schema 校验错误（`started in 2.822s`）。演练还捕获并修复迁移 01 遗漏的 `general_ticker_pat_train_page.id` int→varchar 缺口。证据 `docs/reviews/2026-08-28-migration-rehearsal.md`。 |
| 4. 审计勘误与新发现批次映射 | 已修正文档映射 | 批次由 `1/2/3/7` 修正为 `1/2/3/6/7`，因为 Docker `EXPOSE` 属于批 6；该映射修正不表示所有原 P2 项已完成。 |

## 剩余已知工作

以下项目有证据支持，但被明确排除在本次按顺序执行的修复子集之外：

1. 批 0 的测试机制仍使用显式 `MySqlResource`，不是原 spec 描述的 DevServices；测试 profile 的 fallback 风险需单独解决。
2. 批 3 的 `TelegramTrainService.statisticalPage` 仍通过 `addFirst(getLast())` / `removeLast` 旋转已排序结果，尚未收敛为单一显式顺序。
3. 批 4 仍有其他既存的 TickerPatUtils JSON 解析失败后返回空值路径；本次只修复了特定的 `handleMessageBody patKeys` P0 路径。
4. 批 7 仍有更广泛的 N+1/重对象、死代码、动态及格线分布，以及其余文档清理工作；这些不属于本次执行子集。
5. `SnowflakeIdKit` 仍对系统时钟回拨敏感；Task 2 测试通过固定房间 ID 隔离了这一无关风险，没有修复它。
6. ~~迁移及生产 `validate` 的证据仍来自更早的 Phase 5 演练~~ → 已由 Task 9（2026-08-28）解决：`scripts/rehearse-migrations.sh` 在一次性 Docker 容器上对 current+base 双快照重新执行迁移 01→02，全断言绿、validate 等价差分归零、prod jar 以 `%prod` 零 schema 校验错误启动；证据见 `docs/reviews/2026-08-28-migration-rehearsal.md`，取代 Phase 5 演练。

## Task 8 增补（2026-08-29）

Task 8 处理了上述「剩余已知工作」第 4 项中的**死代码删除与文档清理**子集（不含仍需外部契约裁定的公共端点）：

- 删除零调用内部代码：`CharsetUtils`/`GZipUtil`/`PingYinUtil`/`PasswordUtil`/`MapTypeAdapter`/`NoEscapeStringSerializer` 六个工具类、`BunchDetector` 四个未调用方法（及随之失活的 `getLineNumber`）、`MessageResultBuilder.isConsistent`、`controller/test/Test.java`、`docs/guides/code.java`，以及两处被注释的 service 死实现。
- 从删除清单剔除并保留：`ArraysSafeUtils` 因被 `GeneralKeyPatService`/`GeneralTickerPatService` 活调用而保留。
- 校准审计文档：修正 `2026-08-26-common-build-review.md` 中「`SnowflakeIdKit` 全项目零调用」的错误结论——它运行时被 `WebSocketUnionService.java:294` 调用并有 `SnowflakeIdKitTest` 覆盖，予以保留。第 5 条中「Task 2 通过固定房间 ID 隔离了时钟回拨风险」仍成立，与本次勘误不冲突。
- 未纳入：`TheoryKnowledgeQuestionController.upLoadFile`/`exportTemplate`/`exportQuestionByLevelId` 三个端点与 `TickerTapeTrainService.update` 仍待外部契约决策，Task 8 不删除。
- 验证：`test-compile` 通过（BUILD SUCCESS，无未用 import 报错），`ExceptionBoundaryTest` 10/10 通过。

## Task 10 最终验收（2026-08-29）

结论：后续实施计划（Tasks 1-9）全部完成并通过整分支交叉审查；「剩余已知工作」6 项已全部落地为具体任务。公共端点删除仍待外部契约裁定，不在自动执行范围。

分支 `fix/spec-follow-up`（merge-base 5b37637 → HEAD 9dd3dd1）。

### 全量门禁（一次性、集成后）
- `clean verify`：**Tests run: 129, Failures: 0, Errors: 0, Skipped: 0，BUILD SUCCESS**（基线 112 → 129，+17 回归测试）。DevServices 单容器复用，全套约 2 分钟。
- 静态门禁 8 项全绿：一方 Java 空 catch=0；`log.*(getMessage())`=0；WS 端点单例会话态字段=0（`ws/model` DTO 的 per-user session 属合法领域状态）；TickerPatUtils catch→空表回退=0；`MySqlResource`/`@QuarkusTestResource` 引用=0；`git diff --check` 干净；`actionlint` 退出 0;`Dockerfile.jvm` 构建退出 0。
- 整分支交叉审查（FinalBranchReviewer）：spec + quality PASS，零 findings；重点核验 DevServices 无 prod 回退（SmokeTest 断言 jdbc url 含 `/project006_test`）、Task 2 异常经 `@Transactional(rollbackOn)` 真实回滚、Tasks 4/5/6 数字键域隔离不冲突、批量化保序、迁移01 与实体一致。

### 「剩余已知工作」逐项闭环
1. DevServices 测试机制 → Task 1（切换完成，25 个 `@QuarkusTest` 去除 `MySqlResource`；`%test` 用 project006_test）。
2. `statisticalPage` 顺序收敛 → Task 4（显式升序 [0,1,2]，删除旋转）。
3. TickerPatUtils 其余空值路径 → Task 2（损坏 JSON 改抛有界 `IllegalStateException`）。
4. 批 7 N+1/重对象/动态及格线/死代码/文档 → Tasks 5（动态分档）、6（TheoryKnowledge 批量）、7（跨文件 N+1）、8（死代码删除+文档校准）。
5. `SnowflakeIdKit` 时钟回拨 → Task 3（可注入逻辑时钟，单调不重不退）。
6. 迁移/生产 validate 证据 → Task 9（双快照 Docker 演练，diff 归零，prod jar validate 零错误）。

### 仍开放（非本次范围）
- 公共端点 `TheoryKnowledgeQuestionController.upLoadFile`/`exportTemplate`/`exportQuestionByLevelId` 与 `TickerTapeTrainService.update`：待外部契约裁定，未删除。
- 原审计 348 条未逐条重新验证；本次仅闭环上述家族与新发现项，不宣称全部 348 条完成。
