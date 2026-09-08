# 结论：测试套件质量整体高，捍卫的绝大多数是可观测契约与 DB 真实状态；本片 P0 0 条 / P1 0 条 / P2 4 条 / P3 4 条。8 条已确认 P0 全部有回归测试锁定；未发现把错误行为固化成期望的测试。

| 项目 | 内容 |
|---|---|
| 审查范围 | `src/test/java/**`（39 文件：37 个 *Test 运行类贡献 129 测试 + 1 手动脚本 EntitySchemaSnapshotRehearsal + 1 支撑类 Fixtures）、`src/test/resources/scoring/**`（5 输入 + 4 expected 基线）、`pom.xml` 的 surefire/failsafe 配置与 `src/main/resources/application.yml` 的 `%test` DevServices |
| 审查日期 | 2026-09-07 |
| 审查方式 | 静态阅读 + grep 取证（未运行构建/测试）|
| 计数 | P0 0 / P1 0 / P2 4 / P3 4 |

口径提示：本片评的是“测试作为回归资产的质量”，不复评被测生产缺陷本身。P0 一栏专用于“测试把错误行为固化成期望”或“关键 P0 完全无覆盖”——本次两者皆无，故 P0 为 0。

---

## 1. P0

无。

对 P0 一栏两类触发条件的取证结论：
- 关键 P0 全覆盖：#1/#2/#3/#4/#6/#8/#9/#22 逐条有回归测试（见第 6 节映射表），且均断言 DB 真实状态或协议可观测行为，非断言 `@Transactional` 注解存在（PostTelegramFinishCorruptionTest 类注释 `src/test/java/com/nip/service/PostTelegramFinishCorruptionTest.java:21-25` 明确“断言校验数据库真实状态，而非注解本身”，:74-79 实测回滚后 status/score/resolver 三字段不变）。
- 未固化错误行为：重点核查 characterization 基线。`gap-two-groups.json:24-45` 中 `codeMinNumber=1` 而 `codeMaxNumber=0`、`groupMinNumber=1` 而 `groupMaxNumber=0`，初看像 min>max 矛盾；核对 `src/main/java/com/nip/common/utils/TickerPatUtils.java:608-683` 后确认 `*MinNumber/*MaxNumber/*PerfectNumber` 是三个互斥计数器（value<下限→Min++、value>上限→Max++、区间内→Perfect++），分别统计“过细/过粗/完美”事件数，min 与 max 计的是不同桶，`min>max` 无矛盾，无固化 bug 证据。其余三个 resolver 基线（`resolver-glued/normal-with-blank/question-marks.json`）与其输入用例（`resolver-case-*.json`）字段一一对应，属正常输出快照。

---

## 2. P1

无。

（说明：所有 129 个测试静态阅读未发现断言了错误期望值、会假绿放行真实回归、或锁定了与生产不符结论的测试。覆盖广度缺口与基础设施脆弱性归入 P2/P3，均非“测试本身产生错误/数据错误”。）

---

## 3. P2

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| TS-P2-01 | `src/main/resources/application.yml:63-74`（`%test` profile）+ `pom.xml:132-143`（testcontainers/mysql）| 无 Docker 守护进程的环境执行 `./mvnw -B clean verify` | ~29 个 `@QuarkusTest` 类（含全部 service/dao/ws/exception/smoke）在 DevServices 启动 `mysql:8.0` 容器失败时全部 error，`verify` 整体失败，套件不可运行；仅 9 个纯单元类（无 `@QuarkusTest`）在隔离执行时仍绿。属既定 CI 设计（CI 有 Docker，主代理构建为绿），但本地/离线开发无 Docker-less 回退，且纯单元测试与 QuarkusTest 生命周期在同一 Surefire 执行中被连坐 | `%test` 仅配 `datasource.devservices.enabled: true`+`image-name: mysql:8.0`，未配 `jdbc.url`；`hibernate-orm.database.generation: drop-and-create` 依赖容器库；SmokeTest.java:24-28 断言 `jdbc.url` 含 `/project006_test`（DevServices 注入）|
| TS-P2-02 | `src/test/java/com/nip/ws/WebSocketDeleteOpenAtomicityTest.java:229,237-238` | 慢速/高负载机器上 `deleteCompleted.await(250, MILLISECONDS)` 因线程饥饿超时（与锁是否生效无关）| 负向断言 `assertFalse(deleteFinishedBeforeRegistration)` 会因超时恒成立而假绿——即使“delete 未在共享锁下等待注册”这一 P0#8/#9 邻接的删开原子性缺陷回归，该断言也可能通过；同用例的 `roomPresent==false` 与 `session 关闭` 仍能守住“最终态干净”，故为部分削弱而非完全失守。**待运行验证**：在受控负载下测量 250ms 窗口的假绿概率 | 该 250ms 窗口是唯一锁定“delete 必须等待 validation+注册”时序的断言；其它断言只验证终态 |
| TS-P2-03 | `src/test/java/com/nip/testsupport/Fixtures.java:13-20` | 任一用例调用 `Fixtures.user(...)` | `userAccount` 对所有用户硬编码为 `"tester"`（:16），`save` 独立提交（:19 注释“save 自带事务独立提交”）、无 `@AfterEach`/`@TestTransaction` 清理；`drop-and-create` 仅启动一次，全程累积大量 `userAccount="tester"` 的已提交行；隔离性仅靠各用例 token 唯一性（部分用固定 token 如 `"t-paper"/"telegram-order"`、部分用 UUID）人工纪律维持。当前无跨类 token 冲突，但任何新增用例复用既有固定 token，或生产查询走 `userAccount` 维度，即产生跨用例污染 | `Fixtures.java:15-16` `setUserName("tester")/setUserAccount("tester")` 恒定；EnteringTelexPatServiceTest.java:39/TestPaperServiceTest.java:41 等用固定 token |
| TS-P2-04 | `src/test/java/com/nip/ws/WebSocketUnionTest.java:228-230,250-253` | 该类多个用例操作 `WebSocketUnionService` 的全局静态表且**无 `@AfterEach`** | 仅 `soleRoomOwnerDisconnectRemovesRoom`/`concurrentChurnLeavesNoResidualState` 在方法起始 `clear()` 三张全局静态 Map，其余方法（如 `roomMessageReachesRoomMemberOnly`、`firstClientStillReceivesBroadcast`）既不清也不复原，用例间对单例 `webSocketClientSet/onlineUsers/onlineRooms` 存在隐式顺序耦合；某方法遗留房间条目可能改变后续方法的初始状态（现靠唯一 user id 规避）。对照同片 WebSocketDeleteOpenAtomicityTest/WebSocketUnionLifecycleTest 均有 `@AfterEach clear`，本类缺失属不一致 | WebSocketUnionTest 无 `@AfterEach`（全文仅方法内散布 `unionMap(...).clear()`）；WebSocketUnionLifecycleTest.java:33-38 与 WebSocketDeleteOpenAtomicityTest.java:68-74 均有 `@AfterEach` |

---

## 4. P3

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| TS-P3-01 | `src/test/java/com/nip/service/EnteringTelexPatServiceTest.java:58` | 恒成立 | 重言式填充断言：`assertNotNull(user.getId())`——`user` 由 `Fixtures.user` 返回的已持久化实体，id 必非空，与本用例真实契约（:55-57 第二次同类型创建抛 `IllegalArgumentException`）无关，纯噪声。处置：删除该行 | :51 `user=Fixtures.user(...)`；:55-57 才是契约断言 |
| TS-P3-02 | `src/test/java/com/nip/rehearsal/EntitySchemaSnapshotRehearsal.java:72-73` | 手动 `-Dtest=` 显式运行时 | 断言仅 `assertTrue(rows>0)` 与 `Files.exists(...)`，命中“裸非空/存在性”红线；但类名不以 `Test` 结尾被 Surefire 默认排除（:20-22 注释与命名一致），本质是迁移演练用的 schema 导出脚本而非回归测试，不进 129 计数、不进 `verify`。处置：接受现状，或迁到 `tools/`/`scripts` 以免与测试目录混淆。定位为清理项 | :30 `@QuarkusTest`、:31 `class EntitySchemaSnapshotRehearsal`（无 Test 后缀）；:37 `exportCanonicalSchema` 写 `target/migration-rehearsal/entity-schema.tsv` |
| TS-P3-03 | `src/test/java/com/nip/ws/WebSocketUnionTest.java:153-157,316-320`、`src/test/java/com/nip/ws/WebSocketUnionLifecycleTest.java:204-238` | 生产端重命名私有字段/方法 | 反射硬编码耦合到私有名 `webSocketClientSet/onlineUsers/onlineRooms/userDao/userExit`：字段/方法改名会使测试编译期或运行期断裂，即便外部行为不变。属白盒并发测试单例的必要代价，且用例同时断言了协议可观测行为（广播/在线列表），非纯实现耦合。处置：保留，或为这些静态表提供包级 `@VisibleForTesting` 访问器以收敛耦合面。定位为可维护性清理项 | WebSocketUnionTest.java:154 `getDeclaredMethod("userExit", ...)`、:317 `getDeclaredField(field)`；WebSocketUnionLifecycleTest.java:206/235 反射注入 `userDao`、读三张 map |
| TS-P3-04 | `src/test/java/com/nip/common/utils/TickerPatUtilsCharacterizationTest.java:83-103` | 生产评分逻辑发生**真实修复**后 | characterization 快照以“当前输出即正确”为 oracle，无独立正确性判据；`SCORING_UPDATE=1`（:86-93）一键重生成基线的工作流，使一次真实修复看起来像回归，若开发者未按 :31 注释“人工核对 diff”而直接重生成，可能把真实修复静默吸收。已核查现有 4 基线无固化 bug（见第 1 节）。处置：保留（这是 characterization 测试的固有权衡），但建议对 gap/统计基线补一条基于人工计算期望值的独立断言用例，形成交叉校验 | :86-94 `SCORING_UPDATE=1` 覆写基线并 return；:31 “重新生成快照…人工核对 diff” |

---

## 5. 逐文件判定表（39 文件）

判定列含义：契约=捍卫可观测契约/DB 真实状态/不变量（合格）；脚本=非回归测试；支撑=测试夹具。

| # | 文件 | 类型 | 捍卫对象 | 判定 | 关联 P |
|---|---|---|---|---|---|
| 1 | `SmokeTest.java` | @QuarkusTest | schema 引导 + 测试库隔离（`/project006_test`，不打生产库）| 契约（schemaBoots 为烟雾冒烟，可接受）| — |
| 2 | `common/exception/ExceptionBoundaryTest.java` | @QuarkusTest+RestAssured | JWT 拦截器/ExceptionMapper 的 HTTP 信封码（203/204/206/500/404）与原始错误消息 | 契约（消费者可见 HTTP 行为）| — |
| 3 | `common/utils/SnowflakeIdKitTest.java` | 纯单元 | 时钟回拨不产重复/降序、频率耗尽进位、并发唯一 | 契约（不变量）| — |
| 4 | `common/utils/TickerPatUtilsTest.java` | 纯单元 | 损坏 patKeys/patLogs/moresTime/moresValue 抛 `IllegalStateException` 且错误信息有界（<220、不回显超长输入）| 契约 | #6 |
| 5 | `common/utils/TickerPatUtilsCharacterizationTest.java` | 纯单元 | resolverMessage/checkDotLineGap 输出快照 + 损坏输入抛异常 | 契约（快照，见 TS-P3-04 权衡）| #6 |
| 6 | `common/utils/ScoreMathTest.java` | 纯单元 | rate/accuracy 的分母为零/负、HALF_UP、守分母不守分子 | 契约（边界）| P2-15/51 |
| 7 | `service/PostTelegramTrainScoreTest.java` | 纯单元 | 划扣分按 dash.max 封顶（非 dot.max）、速率 l/r 系数方向 | 契约 | P1-06/P1-02 |
| 8 | `service/PostTelexPatTrainScoreTest.java` | 纯单元 | 五三码/三五码规整移位语义 | 契约 | P1-21 |
| 9 | `service/PostMilitaryTermTrainServiceTest.java` | 纯单元 | generateTestPaper 2s 内终止 + 每题 4 个互异选项 + correctAnswer 有效 | 契约（终止不变量）| #22 |
| 10 | `service/EnteringTelexPatServiceTest.java` | @QuarkusTest | 首次创建成功 / 同类型二次创建被拒 | 契约（含 TS-P3-01 一处填充断言）| #16 |
| 11 | `service/FindByIdBoundaryTest.java` | @QuarkusTest | 缺失 id 显式抛错且不落新行（count 前后一致）| 契约 | Phase7 findById |
| 12 | `service/FindByIdResidualTest.java` | @QuarkusTest+RestAssured | 缺失评分规则/军语 显式抛错、不建空行、不影响他行；有效流仍工作 | 契约 | Phase7 findById |
| 13 | `service/GroupNetTrainServiceTest.java` | @QuarkusTest | listPage 装配设备名/类型名 + PageInfo 语义 + N+1 计数 | 契约（附带精确调用次数耦合，见下注）| N+1 |
| 14 | `service/IntegerUnboxBoundaryTest.java` | @QuarkusTest | null isDefault/groupNumber 不 NPE、跨父移动被拒 | 契约（针对具体拆箱 NPE）| P2-31/33/55/56 |
| 15 | `service/MenusServiceTest.java` | @QuarkusTest | permissions=null 不删按钮、编辑生效、祖先闭包+权限过滤 + N+1 计数 | 契约 | #3 |
| 16 | `service/MilitaryTermDataServiceTest.java` | @QuarkusTest | 批导入整批回滚、空/null 拒绝、新父行不丢后续行 | 契约（DB 真实状态）| #18 |
| 17 | `service/PostTelegramFinishCorruptionTest.java` | @QuarkusTest | finish 遇损坏 patLogs 抛异常且外层事务回滚（status/score/resolver 不变）| 契约（DB 真实状态，非注解）| #6 |
| 18 | `service/PostTelegramTrainServiceTest.java` | @QuarkusTest | speedLog 按 floorNumber upsert、损坏不覆盖旧页、后行失败回滚已 flush 追加 | 契约 | #6/#10 |
| 19 | `service/PostTelegraphKeyPatTrainServiceTest.java` | @QuarkusTest | 已完成训练 finish 不重算覆盖分数 | 契约 | P1-10(finish) |
| 20 | `service/PostTelexPatTrainServiceTest.java` | @QuarkusTest | 已完成训练 finish 幂等、不重结算 | 契约 | P1-09 |
| 21 | `service/PostTickerTapeTrainServiceTest.java` | @QuarkusTest | 已结束/已评分训练拦截二次 finish/begin | 契约（枚举一致性）| P1-08 |
| 22 | `service/TelegramTrainServiceTest.java` | @QuarkusTest | saveFloorContent HQL 属性名修正后更新成功、空 moresTime 落 []、统计补齐+排序 | 契约 | P2-52/HQL |
| 23 | `service/TelexPatTrainStatisticalServiceTest.java` | @QuarkusTest | 统计补齐 4 型+排序、统计失败时先写单字/训练回滚 | 契约（DB 真实状态）| P2-69/#5 |
| 24 | `service/TestPaperServiceTest.java` | @QuarkusTest | 缺题型列表编辑不静默删题（count>0）| 契约 | #1 |
| 25 | `service/TheoryKnowledgeExamServiceTest.java` | @QuarkusTest | 同试卷两考试各自快照、有作答考试编辑被拒且考生行 state/score/content 不变、缺题型不 NPE | 契约 | #4/#19 |
| 26 | `service/TheoryKnowledgeQuestionServiceTest.java` | @QuarkusTest | 创建人名按各自 id 批量解析、无关用户不查、不全表加载 + N+1 计数 | 契约 | N+1 |
| 27 | `service/TheoryKnowledgeServiceTest.java` | @QuarkusTest | 缺课件列表不删课件、成绩分档按卷阈值、批量装配树、学分完成语义、月/日边界 + N+1 计数 | 契约 | #2 |
| 28 | `service/TickerTapeTrainServiceTest.java` | @QuarkusTest | statisticalPage 按 type 升序（不依赖 DB 顺序）| 契约 | P2-17 |
| 29 | `dao/MilitaryTermDataDaoTest.java` | @QuarkusTest | 空/null IN 集合安全返回空、非空正常命中 | 契约 | P2-7 |
| 30 | `dao/PatPageFindTwoPageDaoTest.java` | @QuarkusTest | findTwoPage 只返回目标 trainId 的 1/2 页、不混其他 train | 契约 | #17 |
| 31 | `dao/PostTelegramTrainFloorContentDaoTest.java` | @QuarkusTest | clearByTranId 清为 [] 且不影响他 train、count 查询不触 ONLY_FULL_GROUP_BY | 契约 | P2-11/12 |
| 32 | `ws/WebSocketUnionTest.java` | @QuarkusTest+WS 客户端 | 广播可达性、同 sid 重连不驱逐新连接、房间消息只达成员、50 次并发进出无残留 | 契约（见 TS-P2-04/TS-P3-03）| #8 |
| 33 | `ws/WebSocketDeleteOpenAtomicityTest.java` | @QuarkusTest+反射桩 | 删除必须在共享锁下等待 validation+注册、成功删除不留 ghost 房/活会话、生产删除门面清行+清 map+关会话 | 契约（见 TS-P2-02）| #8/#9 邻接 |
| 34 | `ws/WebSocketSimulationTest.java` | @QuarkusTest+WS 客户端 | 学员断线不按教员身份暂停整房、过期回调不驱逐替换、无成员配置连接被服务端关闭、null 角色断连不暂停 | 契约（DB playStatus + 房间列表）| #9 |
| 35 | `ws/WebSocketUnionLifecycleTest.java` | @QuarkusTest+反射注入 | 替换连接等待旧清理完成、并发同 sid 恰一活会话、同 sid 替换保房籍不广播 USER_EXIT、独享成员退房删键、断连与 JOIN 交错不写入已脱离房 | 契约（见 TS-P3-03）| #8 |
| 36 | `ws/WebSocketGeneralSessionLifecycleTest.java` | 纯单元+JDK Proxy | key/telex/ticker 三族：过期 close/error 不移除替换、当前 error+close 幂等删空房、过期消息不改替换态/不通知教员、删除房快照关闭全部会话 | 契约（可观测房态/会话开闭）| #8 通用族 |
| 37 | `ws/service/simulation/SimulationRoomLifecycleTest.java` | 纯单元+JDK Proxy | 1000 轮并发 close/open 不孤立活 holder、当前移除删空键且重复回调无副作用 | 契约（并发不变量）| #9 底座 |
| 38 | `rehearsal/EntitySchemaSnapshotRehearsal.java` | 脚本（@QuarkusTest 但非 *Test，Surefire 排除）| information_schema 列元数据导出 TSV | 脚本（非回归测试，见 TS-P3-02）| — |
| 39 | `testsupport/Fixtures.java` | 支撑 | 播种 UserEntity | 支撑（见 TS-P2-03）| — |

注（N+1 计数断言，涉及 #13/#15/#26/#27 四处 `Counting*Dao` 子类）：这些子类委托 `super`（真实实现）并计次，`singleXxxCalls==0`/`findAll==0` 断言捍卫的是“无逐行查询/无全表加载”这一**真实不变量**（资源耗尽后果），属合格；同用例还完整断言了输出 DTO 内容，故非纯实现细节断言。唯一可议是 `batchCalls==1` 的**精确**计数与具体批处理策略耦合（合法的批策略重构会误伤）。此为轻微耦合、非缺陷，按红线“断言实现细节”仅作提示，不单列 P 编号，建议将精确 `==1` 放宽、保留 `==0` 零单查询断言。

---

## 6. P0 → 回归测试映射表（8 条已确认 P0 + 主要 P1）

### 6.1 P0（全部“有回归测试锁定”）

| 原 P0 | 生产缺陷（2026-08-26 汇总）| 回归测试（file::method）| 锁定方式（断言消费者可见项）| 判定 |
|---|---|---|---|---|
| #1 | TestPaperService:59-92 编辑先删全部题目、`addAll(null)` NPE 吞后提交、题目永久丢失 | `TestPaperServiceTest::updateWithNullTypeListKeepsExistingQuestions` | `questionDao.count("testPaperId",id)>0`（DB 真实状态，容忍抛或不抛）| 有 |
| #2 | TheoryKnowledgeService:223-296 删课件/测验后缺列表 NPE 吞、课件全丢或测验残缺 | `TheoryKnowledgeServiceTest::editWithNullSwfListKeepsExistingSwfs` + `::saveWithNullTestContentsDoesNotNpe` | `knowledgeSwfDao.count("knowledgeId",id)>0` | 有 |
| #3 | MenusService:101-115 先删按钮权限、缺 permissions 时 NPE 吞照常提交 | `MenusServiceTest::addMenusWithNullPermissionsKeepsButtons` | `menusButtonDao.findAllByMenusId(id)` 非空 | 有 |
| #4 | TheoryKnowledgeExamService:65-93 编辑无状态保护删全部考生行重建空卷、答题与成绩丢失 | `TheoryKnowledgeExamServiceTest::editExamWithAnsweredUsersIsRejected` + `::editExamWithContentOnlyAnswerIsRejectedWithoutDeletingRows` | 考生行 `state/score/content` 不变、快照 `findById` 仍在、编辑被拒抛 `IllegalStateException` | 有 |
| #6 | TickerPatUtils:283-318 + PostTelegramTrainService:508-535 空 catch 把损坏 JSON 变空数组、外层删旧写空、点划轨迹永久丢失 | `TickerPatUtilsTest`(3 用例) + `TickerPatUtilsCharacterizationTest::...RejectsCorruptMoresTime.../checkDotLineGapRejectsCorruptPatLogs` + `PostTelegramFinishCorruptionTest::finishRollsBackWhenStoredPatLogsJsonIsCorrupt` + `PostTelegramTrainServiceTest::malformedJsonShapedPatKeysDoesNotOverwriteExistingPage` | 抛 `IllegalStateException` + DB 回滚（旧 resolver/messageBody/standard/status/score 不变）| 有 |
| #8 | WebSocketUnionService:39-85 `@ApplicationScoped` 单例共享 session/sUser、定向发送与房间身份错投 | `WebSocketUnionTest`(6 用例：广播可达/同 sid 重连/过期解析/房间路由/独享房主退出/并发无残留) + `WebSocketUnionLifecycleTest`(5) + `WebSocketGeneralSessionLifecycleTest`(通用 key/telex/ticker 族) | 广播可达性、在线用户列表含 id、替换会话为当前 holder、无 USER_EXIT 误广播、全局静态表清零 | 有 |
| #9 | WebSocketSimulationService:43-96,183-198 单例共享 userModel、学员断线按教员身份暂停整房并落库 | `WebSocketSimulationTest`(4 用例) + `SimulationRoomLifecycleTest`(2，1000 轮并发底座) | `roomDao.findById(roomId).getPlayStatus()==1`（DB）、教员仍在房间列表且连接 open、并发替换不孤立活 holder | 有 |
| #22 | PostMilitaryTermTrainService:124-189 干扰项无法凑满时无界循环、耗尽工作线程 | `PostMilitaryTermTrainServiceTest::generateTestPaperTerminatesWithExactlyFourCandidates` + `::generateTestPaperRejectsTypeWithFewerThanFourDistinctValues` | `assertTimeoutPreemptively(2s)` 终止不变量 + 每题 4 互异选项 + <4 互异值抛 `IllegalArgumentException` | 有 |

结论：8/8 P0 均“有回归测试锁定”，无“无覆盖”项。

### 6.2 主要 P1 / 改级项映射

| 原编号 | 缺陷要点 | 回归测试 | 判定 |
|---|---|---|---|
| #5→P1 | PostTelexPat 统计失败时先写数据需回滚 | `TelexPatTrainStatisticalServiceTest::saveTelexPatRollsBackWordWriteWhenStatisticalFails` + `::saveTexPatTrainRollsBackTrainWriteWhenStatisticalFails` | 有 |
| #10→P1 | 多页追加中后行失败需回滚已 flush 追加 | `PostTelegramTrainServiceTest::addContentValueRollsBackEarlierRowsWhenALaterRowFails` | 有（parallelStream 竞态本身未单独压测，见待验证）|
| #16→P1 | EnteringTelexPat 查重方向写反 | `EnteringTelexPatServiceTest`(2) | 有 |
| #17→P1 | findTwoPage 用主键过滤 trainId | `PatPageFindTwoPageDaoTest`(2, key+telex) | 有 |
| #18→P1 | 军语 Excel 导入 NPE/整批回滚/丢行 | `MilitaryTermDataServiceTest`(3) | 有 |
| #19→P1 | 考试快照错误复用源试卷 id | `TheoryKnowledgeExamServiceTest::twoExamsOnSamePaperKeepBothSnapshots` | 有 |
| P1-06/02 | 划扣封顶误用 dot.max / 速率系数用反 | `PostTelegramTrainScoreTest`(3) | 有 |
| P1-21 | 五三码规整语义 | `PostTelexPatTrainScoreTest`(3) | 有 |
| P1-08/09/10 | finish 幂等守卫 | `PostTickerTapeTrainServiceTest`/`PostTelexPatTrainServiceTest`/`PostTelegraphKeyPatTrainServiceTest` | 有 |
| P2-17/69 | 统计页排序/补齐 | `TickerTapeTrainServiceTest` / `TelexPatTrainStatisticalServiceTest` | 有 |
| P1-4/5(WS) | 房表残留/删开竞态 | `WebSocketUnionTest::concurrentChurnLeavesNoResidualState` + `WebSocketDeleteOpenAtomicityTest`(5) | 有 |
| Phase4 | ExceptionMapper/token 失败信封 | `ExceptionBoundaryTest`(10) | 有 |
| Phase7 | findById 家族/Integer 拆箱 | `FindByIdBoundaryTest`/`FindByIdResidualTest`/`IntegerUnboxBoundaryTest` | 有 |

覆盖缺口（无回归测试，据实记录，不计入 P 编号）：
- 已知延后功能：`TheoryKnowledgeQuestionController` 上传/导出、`TickerTapeTrainService.update` 端点（memory 标注为 deferred）无任何测试——属未实现功能而非回归缺口。
- Controller 层广度：62 个 controller 中仅经 `ExceptionBoundaryTest`/`FindByIdResidualTest` 的 RestAssured 切片间接触达少数端点；绝大多数 controller 无直接端点级测试，回归保护落在 service 层契约测试上。这是广度取舍，非本次 P0/P1 的锁定缺口。

---

## 7. 上一轮遗留核销

| 上轮编号/结论 | 上轮结论（2026-08-26）| 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| 汇总·测试现状 | “`src/test` 不存在；`clean verify` 执行 0 个测试，没有仓库内自动化回归防护”（`2026-08-26-full-project-review.md:11`）| 已修复 | `src/test/java` 现有 39 文件、37 个 *Test 运行类共 129 测试（全绿，主代理核实）；8 条 P0 全部有回归锁定（本文件第 6 节）|
| 汇总·建议 1 | “为 P0#1-4、#6 五条路径各补失败回滚测试”（`:109`）| 已修复 | TestPaperServiceTest / TheoryKnowledgeServiceTest / MenusServiceTest / TheoryKnowledgeExamServiceTest / PostTelegramFinishCorruptionTest 逐条断言 DB 真实状态回滚 |
| 汇总·建议 2 | “修 WebSocket P0#8/#9 后处理房间裸集合”（`:110`）| 已修复（测试侧）| WebSocketUnionTest/WebSocketSimulationTest/WebSocketUnionLifecycleTest/WebSocketGeneralSessionLifecycleTest/WebSocketDeleteOpenAtomicityTest/SimulationRoomLifecycleTest 覆盖单例改造后的每连接隔离与并发无残留 |
| 汇总·建议 3 | “终止 P0#22 无界循环”（`:111`）| 已修复（测试侧）| `PostMilitaryTermTrainServiceTest` 以 `assertTimeoutPreemptively` 锁定终止 |
| 各分片建议 | ws/service/persistence 分片均建议“补失败回滚/边界测试” | 已修复 | 对应 DAO/service 测试见第 5 节 #11/#12/#16/#23/#29/#30/#31 |

说明：2026-08-26 汇总的“测试”结论仅一条（零测试/零回归防护）及其派生建议；本片对该条及其全部派生建议逐条核销为“已修复”。2026-08-15 遗留的 1×P1+12×P2 与测试质量无关，不在本片范围。

---

## 8. 待运行验证清单

1. **TS-P2-02 时序假绿**：在受控 CPU 负载/慢速 runner 下重复运行 `WebSocketDeleteOpenAtomicityTest`，测量 `deleteCompleted.await(250ms)` 超时导致 `assertFalse(deleteFinishedBeforeRegistration)` 误通过的概率；若非零，改为对“注册完成事件”而非固定 250ms 窗口做同步。
2. **TS-P2-01 无 Docker 行为**：在无 Docker 守护进程环境静态判断已成立（DevServices 无法拉起 mys:8.0 容器），如需实测可运行单个纯单元类（如 `-Dtest=ScoreMathTest`）确认其独立绿、`verify` 整体红。
3. **#10 parallelStream 竞态**：`PostTelegramTrainServiceTest` 只覆盖串行追加回滚，未对多页 `parallelStream` 的裸 `ArrayList`/共享 DTO 竞态做并发压测；如需锁定 P1#10 的并发面需补并发用例（本片仅记录缺口，不新增测试）。
4. **TS-P3-04 快照 oracle**：确认 `scoring/expected/*.json` 各基线均经人工计算核对（当前静态核查未发现固化 bug，但无独立期望值交叉校验）。

---

## 附录：已接受安全风险（不计入计数）

- **测试库凭据**：`%test` DevServices 使用 testcontainers 默认 `mysql:8.0`（默认 root 口令、随机端口），仅测试期存在、随容器销毁；`test-port: 18081` 为测试 HTTP 端口。均为测试期临时资源，属已接受风险。
- **CORS/鉴权**：测试沿用生产 `cors.origins:'*'` 与 `token`/`deviceId` 头鉴权；`ExceptionBoundaryTest`/`FindByIdResidualTest` 用固定明文 token 播种——测试固件性质，不涉生产凭据强度。
- **正向项**：`SmokeTest::testProfileUsesDedicatedDevServiceDatabase` 主动断言测试连接串指向 `/project006_test` 且不含 `localhost:3306/project006`，防止测试误连并破坏 dev/prod 库——这是一条有价值的安全护栏，予以肯定。
