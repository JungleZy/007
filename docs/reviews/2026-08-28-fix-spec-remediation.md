# 2026-08-28 修复 Spec 整改验收报告

**结论：`fix/spec-remediation` 已完成本次约定的高风险修复子集，并在六个修复提交全部落地后通过全量验证；这不表示 2026-08-26 spec 所列 348 项确认缺陷已经全部完成。** 本报告固定本次范围、验收证据和仍需后续处理的已知工作。

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

## 六个提交后的全量验证

六个提交全部落地后重新执行：

```text
JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify
```

结果：

```text
Tests run: 98, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 02:00 min
Finished at: 2026-08-28T16:09:09+08:00
```

其他验收证据：

- `docker build -f src/main/docker/Dockerfile.jvm .`：成功；镜像 manifest list 为 `sha256:68e933f6d31ac1d46ceb8e553c88268386e9332f6187428c8c10ae6a63254f89`。
- `docker run --rm ... rhysd/actionlint:latest`：退出码 0，无输出。
- 第一方 Java 静态门禁：空 catch 为 0；使用 `getMessage()` 的日志调用为 0；`@ServerEndpoint` 中按正则检查的每连接 `Session`/`UserModel` 字段为 0。
- `git diff --check main...HEAD`：退出码 0。
- Java LSP 诊断不可用，工具返回 `No language server found`；Java 诊断以实际编译和上述 `clean verify` 结果为准。

此前一次最终验证为 97/1（97 个通过、1 个失败）：失败暴露 Union `onlineRooms` 泄漏，并直接促成 `a0ac3c6`。上面的修复后新鲜全量验证是权威结果；会话内产物未作为版本化证据引用。

## Definition of Done 状态

| 原 DoD | 本次状态 | 证据或边界 |
|---|---|---|
| 1. `clean verify` 全绿且测试数 ≥ 30 | 已满足 | 六个提交后共运行 98 个测试，失败、错误、跳过均为 0。 |
| 2. 空 catch、WebSocket 会话态实例字段、`log.*(...getMessage())` 静态门禁 | 已满足 | 三项第一方 Java 静态门禁均为 0。 |
| 3. 迁移脚本快照库演练及 prod `generation: validate` 启动 | 本次未重新验证 | 仍沿用更早的 Phase 5 演练证据；本次未重跑破坏性迁移，不能把它记作本次新证据。 |
| 4. 审计勘误与新发现批次映射 | 已修正文档映射 | 批次由 `1/2/3/7` 修正为 `1/2/3/6/7`，因为 Docker `EXPOSE` 属于批 6；该映射修正不表示所有原 P2 项已完成。 |

## 剩余已知工作

以下项目有证据支持，但被明确排除在本次按顺序执行的修复子集之外：

1. 批 0 的测试机制仍使用显式 `MySqlResource`，不是原 spec 描述的 DevServices；测试 profile 的 fallback 风险需单独解决。
2. 批 3 的 `TelegramTrainService.statisticalPage` 仍通过 `addFirst(getLast())` / `removeLast` 旋转已排序结果，尚未收敛为单一显式顺序。
3. 批 4 仍有其他既存的 TickerPatUtils JSON 解析失败后返回空值路径；本次只修复了特定的 `handleMessageBody patKeys` P0 路径。
4. 批 7 仍有更广泛的 N+1/重对象、死代码、动态及格线分布，以及其余文档清理工作；这些不属于本次执行子集。
5. `SnowflakeIdKit` 仍对系统时钟回拨敏感；Task 2 测试通过固定房间 ID 隔离了这一无关风险，没有修复它。
6. 迁移及生产 `validate` 的证据仍来自更早的 Phase 5 演练；本次没有重新执行破坏性迁移。
