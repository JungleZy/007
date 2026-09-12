# 2026-09-12 P2/P3 收尾实施计划（Plan）

> 对应 Spec：[`../specs/2026-09-12-p2p3-closure-spec.md`](../specs/2026-09-12-p2p3-closure-spec.md)（已按 3 路并行评审修订，见其修订记录）。
> 权威依据：[`../reviews/2026-09-12-full-project-review.md`](../reviews/2026-09-12-full-project-review.md) §3/§4，已执行批次见其 §6.1/§6.2/§6.3。
>
> 本文是**已执行计划**（2026-09-12 收口）：`[x]` = 有 path:line 或运行证据；`[~]` = 以偏离形态交付、理由就地写明；`[!]` = 执行时与预期不符、已按 spec 口径处置。36 条复选框全部有终态（34 / 1 / 1）。
>
> **最终基线**：后端 `verify` **401 测试 / 94 suite 全绿**（改前 392 / 93）；前端 `npm run test` **24/24**（改前 19）+ `build` 成功；迁移演练双快照全绿。执行记录见评审 §6.4。

## 1. 交付策略

### 1.1 成功标准

- Spec §2–§5 的 4 个任务各有终态；判定为「误报/不可达/不修」的点写明依据，不伪装成已修。
- 后端 `./mvnw -B clean verify` 全绿且 `>= 392`；前端 `npm run test >= 23` 全绿（当前 19 = `test/*.test.mjs` 13 + `questionImport.test.mjs` 6）、`npm run build` 成功；迁移演练双快照通过。
- T-A 的三个真命中分属三个不相关缺陷，**各自一个可独立回滚的提交**；T-B/T-C/T-D 各一个提交。

### 1.2 执行波次

| 波 | 任务 | 并行度 | 前置 |
|---|---|---|---|
| **P0** | T-A1（`PostEnteringExerciseService`）、T-A2（`TheoryKnowledgeExamService`）、T-A3（`TelegramTrainService`）、T-B（实体非空）、T-C（WS 探针）、T-D（ElectronMorse） | **6 路并行** | 无 |
| **P1** | 全量验证 + 推送 + 回写评审 | 串行（我做） | P0 |

**为什么可以全并行**（首版按「同文件冲突」拆了两波，评审指出理由不成立）：六个任务的独占文件两两不相交 —— T-A 的三个点分别在三个不同 service，T-B 只动 `entity/PostTelegramTrainContentFloorValueEntity.java`，T-C 只动后端测试树，T-D 只动前端。`PostTelegramTrainService` 的 `:764` 经判定为不可达、本轮不改动，因此它与 T-B 的同域实体也不冲突。

**波内并行硬前提**：每个任务跳过所有验证命令（build/lint/test），我在波后做编译闸门与全量验证。

### 1.3 文件所有权（互斥）

| 任务 | 独占文件 |
|---|---|
| T-A1 | `service/PostEnteringExerciseService.java` + 其回归测试 |
| T-A2 | `service/TheoryKnowledgeExamService.java` + 其回归测试 |
| T-A3 | `service/TelegramTrainService.java` + 其回归测试 |
| T-B | `entity/PostTelegramTrainContentFloorValueEntity.java`（+ 如需重生成 `database/rehearsal/*/entity-schema.tsv`） |
| T-C | `testsupport/WebSocketSessionProbe.java`、`ws/WebSocketGeneralSessionLifecycleTest.java`、`ws/WebSocketDeleteOpenAtomicityTest.java`（仅加类注释） |
| T-D | `frontend/src/common/utils/ElectronMorse.js` + 新增控制器模块 + `frontend/test/morseController.test.mjs` |

## 2. P0：T-A1 词库缺失的立即解引用

- [x] `PostEnteringExerciseService.java:68,70` 改为 `defaultContentOf` 的 `Optional.map.orElseThrow`，照同方法 else 分支已有写法，未自造第二种风格（`654187a`）
- [x] 判定「查无即为错」→ 202，理由入正文：`content` 是学员要照着录入的正文，静默跳过会落下 content 为 null 的训练且 `finish` 只回写前端值 → 错误永不暴露（红线 1 同类）
- [x] 回归 `PostEnteringExerciseWordStockGuardTest` 3 例：HTTP 200 + code 202 + 文案含类型名与 type 码值 + 该用户零训练行；另一例正向保护。**突变实跑**：回退成裸解引用 → 3 例中 1 例变红
- **提交**：`fix(data): 词库缺失时给明确业务码而非空指针`

## 3. P0：T-A2 试卷行未判空

- [x] 判「查无即为错」→ 202，照同文件 `finishSelfTesting` 对同一查询的既有判法对齐，不并立第二套约定（`afee8e6`）
- [x] 未动 `:216`（`:218` 判空）、`:303`（`:304` 判空）；另核实 `:127`/`:188` 结果直接塞进响应 map 不解引用。依据入正文
- [x] 回归 `analyseWithoutPaperSnapshotIsRejectedInsteadOfNPE`：走生产路径建考试后用独立事务只删快照行（库里唯一能出现该形态的方式）。**突变实跑**：注释掉守卫 → 8 例中 1 例变红（收到 NPE）
- **提交**：`fix(data): 理论考试试卷行缺失不再空指针`

## 4. P0：T-A3 缺页 NPE 被吞成业务失败

- [x] 判「查无即为错」→ 202（依据 `TerminalStateException` javadoc 的 202/208 分工：目标不存在、可修正后重试；训练未进终态）（`620bd53`）
- [x] 宽 catch **整体删除**（非缩小）：方法不在事务里、不写库、无资源要补偿；剩余异常面已收窄到都该往上抛。未知异常落 `GlobalExceptionMapper`（HTTP 500 + SYSTEM_ERROR），该边界已由 `ExceptionBoundaryTest.unknownExceptionOnJwtEndpointReturns500SystemErrorEnvelope` 钉成既有契约
- [x] 回归 2 例（走真实 REST 端点）：缺页 → HTTP 200 + 202 + 文案点名页号且不是通用「服务器错误」；有页 → 仍 200。**突变实跑**：守卫换回 `orElse(null)` → 5 例中 1 例变红
- **提交**：`fix(data): 电报训练缺页给明确业务码并停止吞异常`

## 5. P0：T-B attempt schema 对齐

- [x] 已加；**范围扩到同类分歧** `PostTelegramTrainEntity.protocolVersion`/`attempt`（同一波迁移 `:80-81` 同为 NOT NULL，实体同样未声明）（`75fedf4`）
- [x] 两点均已就地注释（含「DEFAULT 为何恒不生效」：无 `@DynamicInsert` 时 attempt 为 null 发的是 `attempt = NULL` 而非省略该列）
- [x] 未加 `@DynamicInsert`、未动 DB、未动三张值行表
- [!] **实际变红了一例**（评审预期不红）：`TrainOwnershipAuthorizationTest.nonOwnerGetsForbiddenOnPersonalHandkeyTrain` 的播种器只设 `createUser`/`name` → `not-null property references a null or transient value`。按口径**修写路径**（补 `setProtocolVersion(1)`/`setAttempt(0)`，`8ee831b`），未改回可空。这正是声明对齐要暴露的东西：改前该播种路径在 `%test` 能落库跑绿、同形态写入在 `%prod` 会失败。另核实其余 4 个播种点：3 个已设两列，`PostTelegramTrainScoreTest:63` 只构造 POJO 不落库、不受约束
- [x] `verify` 全绿（**401 / 94 suite**）；演练双快照全绿（两个 diff 文件为空）；`entity-schema.tsv` 已重生成并归档（`1fff2c8`）：三列由 `YES` 变 `NO`，与生产库 `int NOT NULL` 一致
- **提交**：`fix(data): 采集页行attempt在实体上声明非空`

## 6. P0：T-C WS 探针收敛与拒接帧可断言

- [x] 已加 `bound(id, userId)`（空 requestParameterMap + 直接 `bind`），并带 javadoc 写明与 `open(...)` 的分工；原三个工厂一行未动。顺带把出站帧按通道分账（`basicOutbound`/`asyncOutbound`），原 `outbound()` 保留为归并视图，两个既有消费者 API 不变
- [x] 私有代理三件（`record SessionProbe`/`recordingSession`/`defaultValue`）全删，三处改用 `bound(...)`，并清掉 7 个因此未使用的 import
- [~] 新增三条用例。**传输判定修正**：ticker 原走 `getAsyncRemote` 与 key/telex 的同步写不一致，而它三个拒接调用点都紧跟 `close(session)` —— 异步写只是入队、close 可能抢在刷出前，客户端看到无理由断连。已把 ticker 改为 `getBasicRemote`（`7145d0a`），三域因此**统一断 basic**（plan 原写「ticker 断 async」，那样会把偏差冻结成契约）
- [x] 保持原状，仅加类注释写明三条不迁理由
- [x] 既有三条 `outbound().isEmpty()` 未变红：它们走的是陈旧连接短路（`currentConnection` 返回 null 即 return），两侧本来无帧。但此前对 basic 通道是**盲的**（探针返 null → NPE → 被生产 catch 吞），迁移后才真正覆盖两个通道 —— 这不是「此前是空断言」，是覆盖面缺一半
- [x] 未改共享版 `getBasicRemote`
- [x] **突变实跑**：分别注释 key/telex/ticker 三个端点 `sendErrMessage` 的发送行，各让本类 13 例中 1 例变红
- [x] `WebSocketGeneralSessionLifecycleTest` 全文 `Proxy.newProxyInstance` = 0（实测）
- **提交**：`test(ws): 会话探针收敛并断言拒接错误帧`

## 7. P0：T-D ElectronMorse 可测化

- [x] 已抽出，四个状态与类型决议链逐字搬迁（`e505274`）
- [x] 已暴露；条件逐字不变（`event.status === 'finish'` 或 `['stopped','failure'].includes(event.type)`）
- [x] 薄壳 16 行，保留 `onUnmounted` 退订 + `clear()`；对外仍只返回原四个方法（`handleProcessEvent` 不外泄）；`useTraffic.js` 零改动（grep 留证：仅 `:3` import 与 `:85` 解构）
- [x] 参数逐字保留（含 `ratio: DEFAULT_RATIO`、`frequency: 1000`、`volume: 1`）。唯一可观测重排是薄壳里 `subscribe` 与 `configure` 的先后 —— `configure` 同步、不经 PubSub、此刻尚未起播，不可观测
- [x] 已新增 5 例，覆盖下列四类：
      ① `changeCriterion` 对非有限值/`<=0`/未变化的短路；② `changePattern` 的 1/2/其余映射；
      ③ `voiceCode` 类型决议与「仅 timingType 变化才重配置」；④ `playing` 状态机（`accepted` 才置真、`message` vs `addCode`、事件复位）
- [x] **突变（subagent 实跑 6 条）**：`accepted` 判断去掉 / `letter` 与 `short` 优先级互换 / 时值节流条件恒真 / `failure` 从复位集合删掉 / `mix` 映射改错 / `<= 0` 改成 `< 0` —— 各自让对应用例变红。一处已知不可检测：初始 `configure` 的 `ratio: DEFAULT_RATIO` 是恒等冗余写法，删掉不会红，按「参数逐字不变」保留
- [x] `ElectronMorse` 调用面与改前逐字相同；`npm run test` **24/24**（19 → 24）；`npm run build` 成功
- **提交**：`refactor(frontend): 摩尔斯控制器与Vue生命周期解耦并补回归`

## 8. P1：收口

- [x] `verify` 全绿：**401 测试 / 94 suite，0 失败 0 错误 0 跳过**（改前 392 / 93）
- [x] 前端 `npm run test` 24/24、`npm run build` 成功
- [x] 演练双快照全绿（`REHEARSAL PASSED`，两个 diff 文件为空）
- [x] 三条静态门禁实测：三列均带 `@Column(nullable = false)`；`WebSocketGeneralSessionLifecycleTest` 的 `Proxy.newProxyInstance` = 0；三个真命中点不再有裸解引用形态。另加一条：ticker 拒接已走 `getBasicRemote`
- [x] 已推送；评审文档新增 §6.4 执行记录
- [x] `docs/README.md` 已加本 Spec/Plan 入口
- [x] 基线数字已同步（后端 392 → 401，前端 19 → 24）

## 9. 风险与回滚

| 风险 | 触发 | 处置 |
|---|---|---|
| T-A 把「查无正常」误判成「查无即为错」 | 抛异常后某个正常空态页面报错 | 三个真命中点各自单提交可 revert；回归测试同时覆盖「有数据」路径 |
| T-A3 判空后真因仍被 `:162` 宽 catch 吞掉 | 修了 NPE 但用户仍看到通用文案 | 该点验收要求断言「响应码明确」而非只断言不抛 NPE |
| T-C 切换探针后既有断言看到更多 basic 帧 | WS 测试失败 | 先判断帧是否本该发出；是则修断言并记录「此前为空断言」，不是则修生产 |
| T-C 对 ticker 断错传输通道 | 断言恒不命中却以为通过 | 三域各做一次突变检验，确认断言真能变红 |
| T-D 抽层改变首次发音时序或漏了退订 | 真实发音节拍漂移 / 订阅泄漏 | 初始 `configure` 时机与参数逐字保持；薄壳保留 `onUnmounted` 退订；`useTraffic.js` 零改动并 grep 留证 |
