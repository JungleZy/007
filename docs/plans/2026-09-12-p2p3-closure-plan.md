# 2026-09-12 P2/P3 收尾实施计划（Plan）

> 对应 Spec：[`../specs/2026-09-12-p2p3-closure-spec.md`](../specs/2026-09-12-p2p3-closure-spec.md)（已按 3 路并行评审修订，见其修订记录）。
> 权威依据：[`../reviews/2026-09-12-full-project-review.md`](../reviews/2026-09-12-full-project-review.md) §3/§4，已执行批次见其 §6.1/§6.2/§6.3。
>
> 本文是**执行中计划**：仅 `[x]` 且有 path:line 或运行证据的条目表示完成。

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

- [ ] `PostEnteringExerciseService.java:67,69`：`wordStockDao.findByType(...).getContent()` 改为查无即抛，**照同方法 `:72-73` 已有的 `orElseThrow` 写法**，不自造第二种风格
- [ ] 判定写入提交正文：词库是主数据，缺失属配置错误 → 参数类业务码（202），不是 500 堆栈
- [ ] 回归：构造缺该 type 的词库，断言响应码明确且不是 500
- **提交**：`fix(data): 词库缺失时给明确业务码而非空指针`

## 3. P0：T-A2 试卷行未判空

- [ ] `TheoryKnowledgeExamService.java:471`：`testPaperEntity` 在 `:474` 裸解引用（`:466` 只判了 `examEntity`）→ 判定并处置
- [ ] **不动** `:216`、`:303`：两处已在 `:218`/`:304` 判空（静态扫描 6 行窗口导致的误报），在正文列出依据
- [ ] 回归：试卷行缺失时断言可观察响应，不是 NPE
- **提交**：`fix(data): 理论考试试卷行缺失不再空指针`

## 4. P0：T-A3 缺页 NPE 被吞成业务失败

- [ ] `TelegramTrainService.java:153`：`:154/:156` 裸解引用 → 判定并处置
- [ ] **关键**：`:162` 的宽 catch 当前把 NPE 吞成 `error()` 信封（红线 1 同类，真因丢失）。判空之后必须让「缺页」有明确业务码，且不得再由宽 catch 兜住真因
- [ ] 回归：缺页时断言明确业务码，并断言日志/响应不再把 NPE 伪装成通用业务失败
- **提交**：`fix(data): 电报训练缺页给明确业务码并停止吞异常`

## 5. P0：T-B attempt schema 对齐

- [ ] `PostTelegramTrainContentFloorValueEntity.attempt` 加 `@Column(nullable = false)`
- [ ] 就地注释两点：① `%test` 从实体建表（`application.yml:88` `drop-and-create`），不声明就与 `%prod` 的 `NOT NULL` 分歧，漏设 attempt 的写路径会「测试过、生产炸」；② 无 `@DynamicInsert` 时 Hibernate 每次带全列，该列 `DEFAULT 0` 恒不生效
- [ ] 不加 `@DynamicInsert`、不动 DB、不动 Spec §1.2 的三张值行表
- [ ] 若 `%test` 重建为非空后有用例变红：修那条漏设 attempt 的写路径，**不要**改回可空（评审已核实现有写路径都 `setAttempt`，预期不红）
- [ ] 回归：`./mvnw -B clean verify` 全绿；演练双快照全绿；`entity-schema.tsv` 若有差异同步重生成并说明
- **提交**：`fix(data): 采集页行attempt在实体上声明非空`

## 6. P0：T-C WS 探针收敛与拒接帧可断言

- [ ] `testsupport/WebSocketSessionProbe` 新增 `bound(id, userId)`：直接 `WebSocketHandshake.bind`，不需凭据与 DB 用户行；现有 `open`/`failing`/`anonymous` 三个走真实握手的工厂保持不变
- [ ] `WebSocketGeneralSessionLifecycleTest` 删除私有 `record SessionProbe` + `recordingSession` + `defaultValue`，改用 `bound(...)`
- [ ] 新增断言「拒接路径先发出错误帧、再关闭连接」，**按端点选对传输**：key（`sendErrMessage:326-334`）与 telex（`:281-284`）断 **basic** 帧、ticker（`:370-378`）断 **async** 帧
- [ ] `WebSocketDeleteOpenAtomicityTest` **保持原状**，只在类注释写明为何不用共享探针（它注入私有 `FixedHandshake:368-381` 而非 bind、探针暴露 `open()/close():383-387`、断言对象是 open/close 因果而非帧内容）
- [ ] 既有断言若因多出的 basic 帧失败：判断该帧是否本该发出，是 → 修断言并在正文写明此前为空断言；不是 → 单独修生产缺陷
- [ ] **禁止**把共享版 `getBasicRemote` 改回返回 null 来让测试变绿
- [ ] 突变检验：分别注释三个端点 `sendErrMessage` 的发送行，对应断言必须失败（三次结果写入正文）
- [ ] 证据：`WebSocketGeneralSessionLifecycleTest` 全文 `Proxy.newProxyInstance` = 0（**不追求全仓 0**：测试树另有 4 处 Session 代理 + 4 处非 Session 代理，见 Spec §7）
- **提交**：`test(ws): 会话探针收敛并断言拒接错误帧`

## 7. P0：T-D ElectronMorse 可测化

- [ ] 抽出纯工厂 `createMorseController({operation})`，承载 `messageType`/`timingType`/`rate`/`playing` 四个状态与类型决议链
- [ ] 暴露 `handleProcessEvent(event)` 承载 `finish`/`stopped`/`failure` 的 `playing` 复位（原先在 PubSub 回调里）
- [ ] `ElectronMorse()` 退化为薄壳：构造控制器 + `PubSub.subscribe` + `onUnmounted` 退订并 `clear()`；**唯一消费者 `useTraffic.js:85,99` 零改动，且必须保留内部 `onUnmounted` 自动退订**（消费者依赖它，不会自己退订）
- [ ] 初始化 `configure`（原 `:40`）保留在同一时机与同一参数
- [ ] 新增 `frontend/test/morseController.test.mjs`，用假 `operation` 记录调用序列，覆盖：
      ① `changeCriterion` 对非有限值/`<=0`/未变化的短路；② `changePattern` 的 1/2/其余映射；
      ③ `voiceCode` 类型决议与「仅 timingType 变化才重配置」；④ `playing` 状态机（`accepted` 才置真、`message` vs `addCode`、事件复位）
- [ ] 至少一条突变检验写入正文（例：把 `<= 0` 改成 `< 0` 后哪条用例变红）
- [ ] 证据：`ElectronMorse` 调用面 grep 与改前逐字相同；`npm run test >= 23` 全绿；`npm run build` 成功
- **提交**：`refactor(frontend): 摩尔斯控制器与Vue生命周期解耦并补回归`

## 8. P1：收口

- [ ] `cd backend && ./mvnw -B clean verify` 全绿且 `>= 392`
- [ ] `cd bw-frontend/frontend && npm run test && npm run build`
- [ ] `cd backend && ./scripts/rehearse-migrations.sh` 双快照通过
- [ ] Spec §6 的三条静态门禁逐条实测（均为改前不满足、改后满足）
- [ ] 推送；评审文档新增 §6.4 执行记录（含 T-A 的逐点判定结论与 §7 偏离项去向）
- [ ] `docs/README.md` 增加本 Spec/Plan 入口
- [ ] 若基线数字变化，同步 `AGENTS.md`、`README.md`、`docs/README.md`

## 9. 风险与回滚

| 风险 | 触发 | 处置 |
|---|---|---|
| T-A 把「查无正常」误判成「查无即为错」 | 抛异常后某个正常空态页面报错 | 三个真命中点各自单提交可 revert；回归测试同时覆盖「有数据」路径 |
| T-A3 判空后真因仍被 `:162` 宽 catch 吞掉 | 修了 NPE 但用户仍看到通用文案 | 该点验收要求断言「响应码明确」而非只断言不抛 NPE |
| T-C 切换探针后既有断言看到更多 basic 帧 | WS 测试失败 | 先判断帧是否本该发出；是则修断言并记录「此前为空断言」，不是则修生产 |
| T-C 对 ticker 断错传输通道 | 断言恒不命中却以为通过 | 三域各做一次突变检验，确认断言真能变红 |
| T-D 抽层改变首次发音时序或漏了退订 | 真实发音节拍漂移 / 订阅泄漏 | 初始 `configure` 时机与参数逐字保持；薄壳保留 `onUnmounted` 退订；`useTraffic.js` 零改动并 grep 留证 |
