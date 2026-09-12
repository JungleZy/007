# 2026-09-12 P2/P3 收尾规格（Spec）

> **定位**：`docs/reviews/2026-09-12-full-project-review.md` 的 P2（24 条）+ P3（9 条）收尾。
> 权威依据：该评审 §3、§4，以及已执行批次的记录 §6.1（B6）、§6.2（B1–B5/B7）、§6.3（逐条核验与补做）。
>
> 行号仅作定位，执行前以符号和当前源码复核。
>
> **修订记录（2026-09-12，经 3 路并行评审）**：修正 §1.2 的证据链（原文把 T-B 的表当成三张 `int NULL` 表之一，且「读取路径均抛错」对 ticker 域为假）；§2 候选清单由静态扫描的 10 个「疑似」改为实测三分结论（4 真命中 / 3 已判空 / 3 上游不可达）；§4 修正「拒接帧统一走 `getBasicRemote`」（ticker 走 `getAsyncRemote`）、把 `WebSocketDeleteOpenAtomicityTest` 从迁移范围移出并写明理由；§6 把不可达的 `Proxy.newProxyInstance` 全仓门禁改为可达口径。

## 0. 先做的事：剔除已闭合项

P2/P3 的 33 条**不是 33 件待办**。评审 §6 的批次表把 `DATA-01…07、CONC-01、SEC-11、FE-01/03` 归入 B7，`SCORE-03、CONTRACT-01、FE-02` 归入 B4，`SEC-10` 归入 B2，`CONTRACT-02/03` 归入 B7/W4，`DELIVERY-04/05/06/07` 归入 B5，`TESTDOC-01…12` 归入 B6 —— 这些编号**本身就是 P2/P3**。

本 Spec 动笔前已对 33 条逐条核实当前源码，并由独立评审抽查 10 条复核，结论：**29 条已闭合，4 条仍开着**（其中 1 条的一半已被 T7-8 顺带闭合）。已闭合项不再重复列为任务。

| 状态 | 条目 |
|---|---|
| 已闭合（B4） | SCORE-03（`lockedTrain` 行锁 + `requireAttempt` + `settleMember` 事务后通知）、FE-02 |
| 已闭合（B2） | SEC-10（`controller/free/` 只剩 login/signin；三统计端点带类级 `@JWT` 且 `userId` 一律 token 推导） |
| 已闭合（B3） | SEC-11（`JWTInterceptor` 兜底回固定 `SYSTEM_ERROR`，细节仅入日志）、SEC-12 |
| 已闭合（B5） | DELIVERY-04/05/06/07 |
| 已闭合（B6） | TESTDOC-01（`%test` `scheduler.enabled: false`）、03、04、06、07、08、09、10、11、12 |
| 已闭合（B7） | CONC-01（`recover` 对扫描与结算分别 try/catch）、DATA-01（`idx_post_telex_due` + `findDueIds` 主键投影）、DATA-02（`MIGRATIONS` 已字典序）、DATA-06、DATA-07、FE-01、FE-03 |
| 已闭合（W4） | CONTRACT-03 |
| 已闭合（W2 + §6.3 补做） | CONTRACT-02（本轮补齐 `unionJob/` 漏改的 7 处） |
| 已闭合（§6.3 补做） | DATA-04（新增 `docs/guides/2026-09-12-release-runbook.md` 逐脚本还原） |
| **仍开着** | **DATA-03 剩余**、**DATA-05**、**TESTDOC-02 剩余**、**TESTDOC-05 剩余** |

## 1. 目标与边界

### 1.1 目标

1. **DATA-03 剩余**：修掉「DAO 查无返回 null 后被无判空解引用」的 **4 个实测真命中**；对已判空与上游不可达的点写明依据，不做无证据的批量改写。
2. **DATA-05**：消除 `%test`（`drop-and-create`）与 `%prod`（`validate`）在 `attempt` 列可空性上的 schema 分歧。
3. **TESTDOC-02 剩余**：消除 `WebSocketGeneralSessionLifecycleTest` 的私有 `Session` 代理，并把「拒接路径先发错误帧再关闭」变成可断言的契约。
4. **TESTDOC-05 剩余**：`ElectronMorse` 可测化并补纯逻辑回归。

### 1.2 不在本 Spec 内

- **55 个裸 `firstResult()` 的全量改写**：绝大多数是「查无返回 null 且调用方正确判空」的正常用法，改成抛异常会把正常空态变成故障。只修实测真命中。全仓分布：`dao/` **55 处 / 42 文件**（全部为代码），另 `entity/` 2 处在注释里、`service/UserService.java:314` 1 处**已正确判空并抛**（`assignDefaultRole:314-319`，可作为「查无即为错」的范本）。

- **三张值行表的 `attempt` 由 `int NULL` 收紧为 `NOT NULL`**（`t_post_telex_pat_train_page_value`、`general_ticker_pat_train_user_value`、`general_telex_pat_user_value`）：需存量回填 + 三表迁移。**不收紧的依据不是「所有读取路径都抛错」（首版这句对 ticker 域为假）**，而是三个域各有一套机制保证旧轮次行不会被算进结算：

  | 域 | 值行 `attempt` | 旧轮次行为何进不了结算 | 依据 |
  |---|---|---|---|
  | 个人手键 telegram | `NOT NULL DEFAULT 0`（即 T-B 那张表，**不属本条三表**） | `finish` 逐页 `requireAttempt`，不匹配即抛 | `PostTelegramTrainService:574-576` |
  | 个人电传 post-telex | `int NULL` | 逐行 `requireAttempt` 抛错 | `PostTelexPatTrainService:446,604` |
  | 组训数据报 telex | `int NULL` | `deriveCapture` 逐行 `requireAttempt` 抛错；且本域**无 reset 端点**，成员 attempt 不递增 | `GeneralTelexPatService:1188`；`GeneralTelexPatController` 无 reset 路径 |
  | 组训手键 ticker | `int NULL` | **结算不校验 attempt**（`saveTrainUserResult:1175-1176`、`processPageComparisons:1046-1048` 均只按 `trainId+userId` 取行）；安全性来自 `reset` **物理删除**该成员全部值行 | `GeneralTickerPatService:726-727` |
  | 组训电子键 key | `int NULL` | 同 ticker：`reset` 递增 attempt 并删除值/解析/多组行 | `GeneralKeyPatService:1230-1234` |

  → **ticker/key 的正确性依赖「reset 一定删干净」这个隐式不变量**，而非显式校验。这是真实脆弱点（若将来给这两域加「保留原始行」，结算会静默把旧轮次算进去），已登记为 §7 的后续项；本轮不改，因为改它属于结算语义变更，需要与「保留原始行」的产品需求一起设计。

- 把 WS 生命周期单测改成依赖真实用户行的集成测试（见 §4 的分级说明）。
- 其余 4 处自造 `Session` 动态代理的测试类（见 §4 表格）与 4 处非 Session 代理（`HttpServerRequest`/`InvocationContext`）：见 §7 已知偏离。
- G4 现场交付门禁（`docs/plans/2026-09-10-customer-issue-fix-plan.md` 剩余 6 条）：需真实客户环境、可信证书与发布签收人，非本轮可动。

### 1.3 必须保持的契约

- 业务错误恒 HTTP 200 + JSON `code`；203/204/206 码值文案逐字不动；207/208 语义不动。
- 后端测试数只增不减（当前基线 **392 / 93 suite**）；前端不减（当前 **19 = `test/*.test.mjs` 13 + `basicTheory/test/questionBank/js/questionImport.test.mjs` 6**，两者都由 `npm run test` 的同一条命令跑）。
- `%prod` 的 `generation: validate` 必须继续通过（改实体即须对齐迁移与 `entity-schema.tsv`）。
- 测试套件串行执行，不得引入并行。
- 不新增 shim/别名/废弃路径；调用点整体切换。

## 2. T-A：DAO 空返回的真命中收敛（DATA-03 剩余）

- **扫描口径**：`dao/**` 的 39 个方法用 `firstResult()`（查无返回 null）。对 `service/`+`controller/` 做两类静态扫描（立即链式解引用；赋值后 6 行内解引用且窗口内无判空），共 10 个疑似点，**经逐点读码判定为**：

| 判定 | 点位 | 说明 |
|---|---|---|
| **真命中（本轮修）** | `PostEnteringExerciseService.java:67,69` | `wordStockDao.findByType(...).getContent()` 立即链式解引用；词库行缺失即 NPE。同方法 `:72-73` 已示范 `orElseThrow` 修法，照它改 |
| **真命中（本轮修）** | `TheoryKnowledgeExamService.java:471` | `:466` 只判了 `examEntity`，`testPaperEntity` 在 `:474` 裸解引用 |
| **真命中（本轮修，优先级最高）** | `TelegramTrainService.java:153` | `:154/:156` 裸解引用，且 NPE 被 `:162` 的 catch **吞成 `error()` 信封** → 红线 1 同类：调用方看到的是业务失败文案，真因丢失 |
| 已判空（误报，不改） | `TheoryKnowledgeExamService.java:216`（`:218` 判空）、`:303`（`:304` 判空）、`MilitaryTermDataService.java:168`（`:169` `ObjectUtil.isNotEmpty` 短路） | 扫描窗口取 6 行导致误报；保持原状 |
| 上游保证不可达（不改，写依据） | `GeneralTickerPatService.java:1158`（`:560` `student()` 已校验成员存在）、`GeneralKeyPatService.java:419`（detail 查询路径，非结算写路径）、`PostTelegramTrainService.java:764` | 不加防御式判空 |

- **修法口径**（真命中三点各自三选一并写明理由）：
  1. **查无即为错** → 抛业务异常（参数类 `IllegalArgumentException`→202、终态 `TerminalStateException`→208、鉴权 `UnauthorizedException`→203），**不要**返回 `error()` 信封绕过 mapper；
  2. **查无正常** → 显式判空并走既有空态分支，补注释说明为什么空是合法的；
  3. 不可达 → 只写守卫依据，不改代码。
- **禁止**：为「消除告警」在候选点批量插 `if (x == null) return;` —— 那把「数据缺失」变成静默无操作，正是红线 1 同类。
- **`TelegramTrainService:153` 附带要求**：`:162` 的宽 catch 若继续把 NPE 吞成 `error()`，即使判空也只是把一种静默变成另一种 —— 该处必须让「缺页」有明确业务码，且不得由宽 catch 兜住真因。
- **验收**：
  - 三个真命中点各有一条**会失败**的回归测试（先写会红的用例，再修）；
  - 误报与不可达点在提交正文里列出依据 `path:line`，不产生代码改动；
  - `PostEnteringExerciseService` 的词库缺失路径响应码明确，不再是 500 堆栈。

## 3. T-B：`attempt` 可空性 schema 对齐（DATA-05）

- **现状（三条前提均经评审复核）**：
  1. `t_post_telegram_train_floor_content_value.attempt` 迁移为 `int NOT NULL DEFAULT 0`（`migrations/2026-09-11-04-personal-handkey-capture.sql:84`）；
  2. 实体 `PostTelegramTrainContentFloorValueEntity:16` 是可空 `Integer`，无 `@Column(nullable = false)`；
  3. `%test` 是 `drop-and-create`（`application.yml:88`）、`%prod` 是 `validate`（`:125`）。
- **分歧的实证**：`database/rehearsal/2026-09-11-customer-integrated-v1/entity-schema.tsv:463` 实体派生为 `int YES`（可空），同目录 `current-schema.tsv:466` DB 侧为 `int NO 0`（非空）。演练比对的是 base↔current 两个 DB 快照、不校验「实体 ↔ DB 空性」，所以该分歧当前不触发红 —— 但它意味着**漏设 attempt 的写路径在测试里能过、在生产会炸**。
- **第二个后果**：该列的 `DEFAULT 0` 恒不生效 —— 实体没有 `@DynamicInsert`，Hibernate 每次 INSERT 都带全列，默认值永远走不到。
- **目标**：实体补 `@Column(nullable = false)` 并就地注释上面两点（尤其「DEFAULT 为何不生效」，避免后人误以为漏设 attempt 会被默认值兜住）。
- **不做**：不加 `@DynamicInsert`（改变全表 INSERT 形态，收益仅为让一个用不到的默认值生效）；不动 DB；不动 §1.2 的三张值行表。
- **已知无影响**：现有测试的所有写路径都 `setAttempt`，加 `nullable=false` 后 `%test` 重建为非空列**不会**让任何既有用例变红（评审已逐处核实）。若实际出现红，说明发现了一条漏设 attempt 的写路径 —— 那就修写路径，**不要**改回可空。
- **验收**：`./mvnw -B clean verify` 全绿；迁移演练双快照全绿；`entity-schema.tsv` 若有差异同步重新生成并说明。

## 4. T-C：WS 探针收敛与拒接帧可断言（TESTDOC-02 剩余）

### 4.1 现状：测试树里有 5 处自造 `Session` 代理

| 文件 | 特征 | 本轮处置 |
|---|---|---|
| `ws/WebSocketGeneralSessionLifecycleTest` | 记 async 帧；`getBasicRemote` 落 `defaultValue` → **null**；经 `WebSocketHandshake.bind` 直接绑身份 | **迁移**：与共享版同为 bind 绑身份，共享版功能是它的超集 |
| `ws/WebSocketDeleteOpenAtomicityTest` | async/basic 均返回**不记账**代理；**不绑身份**，改注入私有 `FixedHandshake`（`:368-381`）；探针暴露 `open()/close()`（`:383-387`） | **不迁移**：身份注入方式与 API 形状都不同，迁移须改端点注入方式；它断言的是 open/close 因果而非帧内容，迁移收益为零 |
| `ws/WebSocketSimulationTest:290,500,509` | 另造 Session + Async | 不迁移（§7 已知偏离） |
| `ws/WebSocketUnionLifecycleTest:384,393` | 握手式，自建 token/deviceId 参数 Map | 不迁移（§7） |
| `ws/service/simulation/SimulationRoomLifecycleTest:75` | 纯单元式，不 bind 不握手，直接喂 service | 不迁移（§7；`bound(...)` 对它不是即插替换） |

### 4.2 真实覆盖缺口（本任务的价值点）

生产的拒接帧**分两种传输**，首版 spec 误作统一：

| 端点 | 拒接帧传输 | 私有探针下是否可见 |
|---|---|---|
| `WebSocketGeneralKeyPatService.sendErrMessage:326-334` | `getBasicRemote()` | **不可见** —— 探针返 null → NPE → 被 `catch (Exception) { log.error }` 吞掉 |
| `WebSocketGeneralTelexPatService.sendErrMessage:281-284` | `getBasicRemote()` | **不可见**（同上） |
| `WebSocketGeneralTickerPatService.sendErrMessage:370-378` | `getAsyncRemote()` | 可见（私有探针记 async 帧） |

→ 所以「拒接时先发错误帧再关闭」这条契约当前**在 key/telex 两域无人断言**，在 ticker 域可断言但未断言。

### 4.3 目标

1. 共享 `testsupport/WebSocketSessionProbe` 新增 `bound(String id, String userId)` 工厂：直接 `WebSocketHandshake.bind`，不需要凭据与真实用户行，保留单元级语义（现有 `open`/`failing`/`anonymous` 三个走真实握手的工厂不变）。
2. `WebSocketGeneralSessionLifecycleTest` 删除私有 `record SessionProbe` + `recordingSession` + `defaultValue`，改用 `bound(...)`。
3. 切换后 basic 帧开始记账 → **补断言「拒接路径先发出错误帧、再关闭连接」**，key/telex 断 basic、ticker 断 async（按 §4.2 选对路径）。
4. `WebSocketDeleteOpenAtomicityTest` 保持原状，在其类注释里写明为何不用共享探针（避免后人再花时间试）。

### 4.4 风险与处置

- 切换会让既有断言看到**更多**出站帧。某条断言因此失败时：先判断多出的帧是否本该发出 —— 是 → 修断言并在正文写明「此前是空断言」；不是 → 那是生产多发帧的缺陷，单独修。
- **禁止**把共享版的 `getBasicRemote` 改回返回 null 来让测试变绿。

### 4.5 验收

- WS 相关测试类全绿；
- `WebSocketGeneralSessionLifecycleTest` 全文不再出现 `Proxy.newProxyInstance`（可 grep 证伪）；
- 新增拒接帧断言通过突变检验：把对应端点 `sendErrMessage` 的发送行注释掉后该断言失败（三域各记一次结果）。

## 5. T-D：`ElectronMorse` 可测化（TESTDOC-05 剩余）

- **现状**：`bw-frontend/frontend/src/common/utils/ElectronMorse.js` 46 行，导出一个组合式函数，耦合三件不可测物：`onUnmounted`（Vue 生命周期）、`PubSub.subscribe`（全局总线）、`operationMorseVoice()`（音频侧效）。评审记为「需重构生产代码才可测」，B6 明确跳过并另立项。
- **唯一消费者**：`common/mixin/useTraffic.js:85` 解构四个方法，`:99` 自己也会调 `clear()`；**它依赖 `ElectronMorse` 内部的 `onUnmounted` 自动退订 `receiveProcessData`** —— 薄壳必须保留这一行为，否则消费者会漏订阅泄漏。
- **值得测的纯逻辑**（也是唯一容易出错的地方）：
  - `changeCriterion`：非有限值与 `<= 0` 直接返回；「速率与类型都未变」短路；
  - `changePattern`：`1→letter / 2→mix / 其余→short`；
  - `voiceCode`：类型决议链（`letter` 优先、`numType==='short'`、`mix`、否则 `long`）与「仅 `timingType` 变化才重配置」；
  - `playing` 状态机：`accepted` 才置真；`message` 与 `addCode` 的选择取决于 `playing`；收到 `finish`/`stopped`/`failure` 即复位。
- **目标**：抽出**不依赖 Vue 与全局总线**的纯工厂 `createMorseController({operation})`，返回同名四个方法 + `handleProcessEvent(event)`；`ElectronMorse()` 退化为「构造控制器 + `PubSub.subscribe` + `onUnmounted` 退订并 `clear()`」的薄壳。
- **约束**：
  - 对外 API 与行为不变：`ElectronMorse()` 仍返回 `{changeCriterion, clear, voiceCode, changePattern}`，`useTraffic.js` 零改动；
  - 初始化时那次 `configure`（`:40`）保留在同一时机与同一参数，否则首次发音时序会变；
  - 不引入新依赖、不改 `operationMorseVoice` 与 `MorseVoiceHighPerformance`。
- **验收**：新增 `bw-frontend/frontend/test/morseController.test.mjs`，用假 `operation` 记录调用序列，覆盖上述四类；`npm run test` 由 **19**（13 + 6）增至 **≥ 23** 且全绿；`npm run build` 成功。每条用例都要能被一处合理改错弄红，正文给出至少一条突变检验（例：把 `<= 0` 改成 `< 0` 后哪条变红）。

## 6. 验收门禁（DoD）

```bash
cd backend && export JAVA_HOME=$HOME/.local/opt/jdk21 && ./mvnw -B clean verify
cd ../bw-frontend/frontend && npm run test && npm run build
cd ../../backend && ./scripts/rehearse-migrations.sh
```

- 后端 `>= 392` 且全绿；前端 `>= 23` 且全绿；迁移演练双快照通过。
- 静态门禁（**均为改动前不满足、改动后满足，可证伪**）：
  - `PostTelegramTrainContentFloorValueEntity` 的 `attempt` 带 `@Column(nullable = false)`；
  - `WebSocketGeneralSessionLifecycleTest` 全文 `Proxy.newProxyInstance` 命中数 = 0（**不是**全仓 0：测试树另有 4 处 Session 代理与 4 处非 Session 代理，见 §7）；
  - `PostEnteringExerciseService`、`TheoryKnowledgeExamService`、`TelegramTrainService` 三个真命中点不再存在「取 DAO 结果后直接解引用」的形态。
- **不作为门禁**（改动前即满足，写出来只是提示）：`ElectronMorse` 调用面零改动 —— 用「改后 grep 结果与改前逐字相同」在正文留证，不当作门禁条目。
- 每个 T 编号一个可独立回滚的提交；T-A 的三个真命中分属三个不相关缺陷，**各自一个提交**（符合 AGENTS.md 的 revert 粒度判据）。
- **不得把「未做」记成「已修」**：T-A 的误报/不可达点必须写明依据；§1.2 的三表收紧与 §7 的偏离项必须留在文档里。

## 7. 已知偏离与后续项

| 事项 | 处置 |
|---|---|
| ticker/key 结算**不校验值行 attempt**，正确性依赖「reset 物理删除旧行」的隐式不变量 | **登记为后续项**：若给这两域加「保留原始行」（如 telex T4-1 所做），结算会静默把旧轮次算进去。收口方式是结算逐行 `requireAttempt`（与 telegram/post-telex/telex 同口径），属结算语义变更，需与产品需求一起设计 |
| 三张值行表 `attempt` 仍为 `int NULL`（与两张 `NOT NULL` 不一致） | 已知偏离，理由见 §1.2 表；需存量回填 + 三表迁移，另立项 |
| 55 个 `firstResult()` 中判定为「查无正常」的多数 | 保持现状，本 Spec 只收敛实测真命中 |
| 另 4 处自造 `Session` 代理（`WebSocketSimulationTest`、`WebSocketUnionLifecycleTest`、`SimulationRoomLifecycleTest`，及 `WebSocketDeleteOpenAtomicityTest`） | 已知偏离：三者的身份注入与断言对象各不相同，`bound(...)` 不是即插替换；合并须先统一端点的 handshake 注入方式，另立项 |
| 4 处非 `Session` 动态代理（`InterceptorCredentialSourceTest:114,134`、`ReadPathLazyCreateConcurrencyTest:156`、`SimulationPagePersistenceTest:249`、`SimulationRoomCreationGuardTest:80`） | 不在范围：造的是 `HttpServerRequest`/`InvocationContext`，无法折进 `WebSocketSessionProbe` |
| WS 生命周期测试仍是单元级（不走真实握手） | 有意保留：握手路径已由 `WebSocketHandshakeAuthorizationTest` 用真实连接覆盖，两级分工明确 |
| G4 现场交付 6 条 | 外部前置，见 `docs/plans/2026-09-10-customer-issue-fix-plan.md` |
