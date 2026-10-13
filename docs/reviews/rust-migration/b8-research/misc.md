# B8 杂项端点调研（只读，未实跑）

结论：inventory 359 个端点里 52 个没有任何旅程路径命中（用路径模板匹配，`/GeneralKeyPatTrain/getOneline/{trainId}` 实际已被覆盖）。其中 9 个 `NO_STATIC_CONSUMER`；另有 4 个前端只导出从未调用（`saveTheoryKnowledgeTest`、`getTestBySwfId`、`tools/getTwelvemonth`、`user/verifyPassword`）。真有前端调用的是 39 个。读码发现一个必修项（`gradeCount` 是桩）、两个会让页面列直接空白的字段缺失、两处 Rust 比 Java 多删数据。旅程草稿 10 条/194 步覆盖 47 个，剩 5 个（两个 multipart、`importTrainInfoBatch`、openApi 中文路径、`verifyPassword` 文本体）harness 做不了。

## 1 未覆盖清单（按 class_path，括号为数量）

theoryKnowledgeQuestion(9)、theoryKnowledge(7)、theoryKnowledgeTest(6)、comprehensive(3)、equipmentTrain(3)、generalGroupNetRule(3)、postTrainGlobalRule(3)、deviceScoringRule(2)、groupNetTrain(2)、keyPoints(2)、masthead(2)、receiveKeyPoints(2)、tools(2)、各 1 个：theoryKnowledgeTestPaper、theoryKnowledgeExam、user、postEnteringExerciseWordStock、generalKeyPat、openApi/generalTickerPatTrain。

无前端调用（NO_STATIC_CONSUMER，9）：`deviceScoringRule/delete`、`postEnteringExerciseWordStock/view`、`theoryKnowledge/{getDutyBusiness,getChargeEquipment,uploadFileToNip}`、`theoryKnowledgeTest/{getTestContentByUserIdAndKnowledgeId,getTestContentById}`、`generalKeyPat/importTrainInfoBatch`、`openApi/generalTickerPatTrain/综合训练-手键拍发Api`。

前端调用点（file:line，均在 `bw-frontend/frontend/src`）：
- comprehensive：`common/api/StudyManage_classHours.js:23/31/39`，页面 `studyManage/analyze/js/{totalApi:35,useChart2:183,pointUseChart:47}`
- gradeCount：`StudyManage_classHours.js:14`，`studyManage/score/js/scoreUseChart.js:43,82`
- equipmentTrain：`common/api/equipment.js:31/38/45`；generalGroupNetRule：`equipment.js:52/59/65`
- groupNetTrain/deviceScoringRule：`common/api/TrainingDetails.js:28/36/43`
- keyPoints/receiveKeyPoints：`common/api/ExplainApi.js:4/11/19/33`；postTrainGlobalRule：`postWording.js:40/48/56`；masthead：`ReceiveApi.js:203/210`
- 理论各组：`TheoryKnowledgeApi.js:11/54`、`TestApi.js:3-24/59/156/163`、`TheoryQuestionBankApi.js:4-58`
- tools：`ToolsApi.js:4`（getNowTime 用于 `ShortcutMenu.vue:80`）

## 2 疑似差异（按严重度）

**高**
1. `theoryKnowledge/gradeCount` Rust 是桩：`theory.rs:779-806` 忽略 type，恒返回 `{"up":{"all":n,"good":n},"down":{}}`；Java 按 passMark 分档/按月日分布（`TheoryKnowledgeService.java:537-690`）。前端 `scoreUseChart.js:82` 遍历 `down` 画图，现在三种图全空。类别：缺前端读取的字段。
2. `createUserName` 恒 null：`theory.rs:127,232`（getBasicTheory/getBasicTheoryOpen）、`theory_question.rs:147`（findAllQuestionByLevelId）；Java 联表 `t_user.user_name`（`TheoryKnowledgeEntity.java:26`、`TheoryKnowledgeQuestionService.java:140-150`）。前端有该列（`useList.js:34`、`knowledgeTabel.js:38`）。类别：缺字段。
3. 理论列表筛选语义：Java 内连 swf 且默认 `difficulty/specialty IN (全部分类id)`（`TheoryKnowledgeEntity.java:25-40`、`TheoryKnowledgeService.java:181-231`），无课件/无分类的课程不出现；Rust 左连、无筛选时不加条件（`theory.rs:69-128`）。另 `getBasicTheoryOpen` Java 固定 status=1，Rust 采用请求体 `status`（`theory.rs:166-180`），学员可请求 `[0]` 看到草稿课程。
4. 删除范围不同：`deleteThroyKnowledgeById` Java 只 `deleteById`（`TheoryKnowledgeService.java:389-392`），Rust 连删 5 张子表含学员答题和学时记录（`theory.rs:943-968`）；`deleteTheoryKnowledgeQuestionLevelById` Java 只删题库节点，Rust 同时删其下全部题目（`theory_question.rs:193`）。库内无外键。Rust 多删不可恢复数据，且改变 `comprehensive` 的 swfNum（Java 仍计孤儿答题）。
5. 非主键锁：这些表全是仅主键表（`project006.sql`，已逐表核对）。`DELETE ... WHERE knowledge_id=?`（`theory.rs:943-963`，5 条）、`WHERE level_id=?`（`theory_question.rs:193`）、`UPDATE t_theory_knowledge_test ... WHERE knowledge_swf_id=? AND id<>?`（`theory_test.rs:74`）、`masthead.rs:93` `WHERE train_id=? FOR UPDATE`（Java 同样加锁，`MastheadService` 第 3 次 find）。全表 next-key 锁，会挡住全体学员写答题。按既定锁方案：先锁父行主键，再按已读出 id 逐个主键删。

**中**
6. `examineAnalyse`：Java 返回全部错题（`TheoryKnowledgeExamService.java:~714` 无 limit），Rust `exam.rs:1396` `truncate(3)`；并列次序 Java 为 HashMap 迭代、Rust 为随机 HashMap，旅程用 4/3/2/1 互异次数避开。
7. `findAllQuestionByLevelId` 名称过滤：Java SQL LIKE（不区分大小写，`%`/`_` 是通配，`TheoryKnowledgeQuestionDao.java:22-31`），Rust 字面量区分大小写 `contains`（`theory_question.rs:134`）；且 Rust 全表读入后内存过滤。
8. 状态码：`deviceScoringRule/findAllByDeviceId` 重复规则 Java 抛 IAE→202（`DeviceScoringRuleService.java:80`），Rust 返回 500（`device.rs:241`）。`postTrainGlobalRule/findByType` 缺 type：Java 返回空数组，Rust 202；`deleteById` 缺 id：Java 空操作 200，Rust 202（`train_rule.rs` IdInput）。
9. `comprehensive`：`theoryTestAvgCredit` Java 是 double 转 BigDecimal（如 83.33333333333333），Rust 是 DECIMAL AVG（`comprehensive.rs:204`，4 位小数），取整类；`getTheoryTestYear.avgScores` Java GROUP BY 无 ORDER BY，Rust 按月份数值排序（`comprehensive.rs:261`）；`userEntity` Java 序列化整个实体，Rust 只投影安全字段（有意，Java 泄露凭证类字段，不应复制）。
10. `getTestContentById`：Java 无任何属主检查（任何登录用户可读他人答题），Rust 非属主非管理员返回 207（`theory_test.rs:249-251`）。有意加固，需登记。
11. `groupNetTrain` 与 `masthead`、`equipmentTrain`、`generalGroupNetRule`、`keyPoints`、`receiveKeyPoints`、`postTrainGlobalRule`：读码未见高风险差异。小项：`equipment_train.rs:23` 列表 `ORDER BY train_time` 升序，Java 降序（`EquipmentTrainDao.java:15`，train_time 两栈都不写，实际全 NULL）；`keypoints.rs`/`receive_keypoints.rs` 保存时 Rust 采用客户端 id，Java 对不存在的 id 另生成 UUID；`receiveKeyPoints/save` 缺 content Rust 202、Java 存 NULL；`generalGroupNetRule/save` 带不存在的 id Rust 走 `fetch_one` 报 500（`general_group_net_rule.rs:78`），Java 行为待旅程实测。
12. `postEnteringExerciseWordStock/view`：Java 是桩（返回空 DTO，`PostEnteringExerciseWordStockService.java:47`），Rust 实际解析 txt/md/csv（`post_entering.rs:714`）。能力边界超出 Java，无前端调用。

读接口带 `FOR UPDATE`：本范围内没有（`masthead.rs:93` 在写事务内）。

## 3 两个横切项

**cable 类型列表顺序**：Java `CableTypeService.findAll`/`save` 返回 `cableTypeDao.findAll().list()`（`CableTypeService.java:29-30,44`），无 ORDER BY；Rust `cable.rs:114` `ORDER BY id`，`save_type` 也复用它。`t_cable_type` 只有主键（`project006.sql:20608`，varchar(64) general_ci），InnoDB 全表扫描即聚簇主键序，与 `ORDER BY id`（同排序规则）理论上相同；现有旅程为此给 `cable/type/save` 加了 `unordered`。静态上找不到真实顺序差异，需要实跑一次确认。建议：保留 Rust 的确定排序，实跑一致就去掉旅程里的 `unordered`；不一致则登记刻意差异。

**不存在子路径 Java 404 / Rust 405**：Rust 没有全局 fallback（`main.rs:1314-1324` 的根 Router 无 `.fallback`），axum 对未匹配路径默认 404。405 的来源是 WS 通配路由：`/api/simulation/{id}/{roomId}`、`/api/generalKeyPatTrain/{uid}/{trainId}`、`/api/generalTelexPatTrain/{uid}/{trainId}`、`/api/websocket/{sid}` 等只注册了 GET（`main.rs:1276-1284`），任何 POST 到 `/api/simulation/<x>/<y>` 的不存在 REST 路径都命中它并被判方法不允许（例如已删除的 `/router/savePage`）。全局修法：`Router::method_not_allowed_fallback` 在 axum 0.8.9 存在，但会同时把真实路由的方法不符也改成 404，而 Java 对真实路径错方法是 405，所以不能用。正确做法：给这些 WS 路由写 `get(ws::open).fallback(not_found)`（`MethodRouter::fallback` 只替换"同路径其它方法"），再在根 Router 加 `.fallback(not_found)` 统一 404。非升级的 GET 命中通配路由会得到 400/426 而 Java 为 404，同一处一并处理。

## 4 待拍板（含建议）

1. `gradeCount` 完整移植（高、前端图表为空）。建议直接修。
2. `createUserName` 补联表。建议直接修（三处）。
3. 删除范围（差异 4）：建议按 Java 不连删，登记为 Java 已知缺陷（孤儿数据），日后两栈一起改；与 B5 缺页扣分同口径。
4. 列表语义（差异 3）：建议 getBasicTheoryOpen 忽略请求体 status、固定 1（防草稿外泄）；无分类/无课件课程是否隐藏按 Java（内连 + IN 全分类）。空数组 `[]` Java 很可能 `IN ()` 报错，不复制，保留 Rust 的"空数组=不筛选"，由旅程第 11 步首跑确认。
5. 非主键锁（差异 5）：按 B6/B7 默认修，不加索引。
6. `getTestContentById` 207 与 `view`/`uploadFileToNip` 的 Rust 超集能力：建议保留 Rust 并登记刻意差异。
7. 并列与取整（差异 6、9）：`errorTop3` 建议按 Java 返回全部；`avgCredit` 取整建议按 Java 的 double 口径。
8. NO_STATIC_CONSUMER 默认处理：只保留最小扫描覆盖（现有 sweep 的 anon/学员/管理员/空体探针），不为它们单独写旅程；已搭好夹具、几乎零成本的 4 个（`getDutyBusiness`、`getChargeEquipment`、`getTestContentById`、`deviceScoringRule/delete`）已顺带放进草稿旅程。multipart/导入类（`view`、`uploadFileToNip`、`importTrainInfoBatch`）建议用 curl 级一次性验证，不进套件。

## 5 草稿旅程

`misc-journeys.draft.json`（10 条，194 步，已验证可解析）：装备训练、组网规则 CRUD、组网训练详情/提交/208 + 评分规则删除、要点讲解 + 全局规则、报头属主流程、理论目录（分类/无课件/草稿/删除/答题）、题库（层级/LIKE 通配/批量/删除）、试卷按编号、成绩分析（种子考试结果，固定 2026 日期）、tools。时钟：tools 的 `getNowTime` 仅此一项 mask，其余不 mask 分数或内容；成绩分析的考试行是输入夹具，不是预期结果。预期首跑会 FAIL 的步骤：gradeCount 6 步、createUserName、名称 LIKE、两处级联删除、`getTestContentById` 非属主、errorTop3。
