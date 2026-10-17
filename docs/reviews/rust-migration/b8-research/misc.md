# B8 杂项端点调研与实跑

初始静态清单：inventory 359 个端点里 52 个没有任何旅程路径命中（`/GeneralKeyPatTrain/getOneline/{trainId}` 实际已被覆盖）。metadata 标记 9 个 `NO_STATIC_CONSUMER`，另有 4 个前端只导出未调用。后续核对动态上传 action 发现 `uploadFileToNip` 有真实消费者，因此实际有前端调用的是 **40 个**，不是初稿的 39 个。原草稿 10 条/194 步覆盖 47 个；剩 5 个特殊协议请求采用一次性 curl 验证。下文第 2–5 节保留首轮静态分析，第 6 节记录本轮运行证据。

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

## 6 本轮实跑（2026-10-13）

- 用户已确认：删除课程/题库节点不级联；课程列表完整对齐 Java 筛选；保留 Rust 属主保护和现有解析能力；无前端调用端点保持最小覆盖，特殊协议使用一次性 curl。
- 初始草稿运行：`backend-rust/scripts/local-dual-stack.sh journeys docs/reviews/rust-migration/b8-research/misc-journeys.draft.json`，194 请求，119 identical、18 ACCEPTED、57 FAIL。这是修复前证据，不是最终门禁结果。
- 草稿修正：要点页面首次确实发送 `id:""`（telegram/receive 的 `explain.js:10,19-28,62-67`），Java generated-id merge 返回 500。保留空 ID 缺陷探针，另以省略 ID 的有效后端请求覆盖创建/更新/读取；不能把省略 ID 夹具称为前端原样请求。题库删除后的导出改用管理员，另留学员 207 探针，避免权限拒绝掩盖数据保留验证。
- `difficulty:"[]"` 实测 Java 为 200 + 空数组，不是静态调研猜测的 SQL 错误；按已确认的完整筛选口径对齐空集语义。
- `comprehensive.errorTopic` 前端直接按数组下标编号展示（`bw-frontend/frontend/src/views/manage/basicTheory/studyManage/analyze/Index.vue:87-98,353-354`）。非并列排名不能 unordered；本夹具管理员前三题错误次数并列，只能登记确切并列排列差异。

### 五项特殊请求

对隔离库 `rust_parity_java_local` / `rust_parity_rust_local` 使用专用合成账号与真实 curl 请求，不扩展 JSON harness：

| 请求 | 已观察结果 |
|---|---|
| multipart `postEnteringExerciseWordStock/view`，`file=b8-note.txt`，内容 `甲乙\nAB\n` | Java 200 + 全 null DTO；Rust 200 + 原文、文件名、`wordSize=5`。保留用户已确认的有效预览能力差异。 |
| multipart `theoryKnowledge/uploadFileToNip`，同一文本文件 | 两栈 200，`type=2`、完整 `wordContent`、`imgUrls=[]` 一致。该接口并非能力超集；当前两栈都只支持文本。 |
| JSON 数组 `generalKeyPat/importTrainInfoBatch`，一个含用户/训练/成员的真实归档包 | 学员两栈 207；管理员两栈 200。SQL 核对导入账号、训练创建者、成员身份均正确重映射，`password/token/device_id` 为 NULL。不是空数组空操作验证。 |
| GET 中文 `openApi/generalTickerPatTrain/综合训练-手键拍发Api` | 常规 curl 自动百分号编码、显式百分号编码均为两栈 404。强制 `curl --request-target` 发送字面 UTF-8：Java HTTP 400，Rust 200 + 属主离线训练的空在线列表。邻近 `findTrainInfo` 两栈 200；记录既有编码边界，不宣称常规客户端可调用该中文端点。 |
| `user/verifyPassword`，`Content-Type: text/plain; charset=utf-8`，无 JSON 引号/尾换行 | 合成历史 MD5 账号，正确/错误明文两栈分别 200 + true/false。 |

`uploadFileToNip` 的真实调用：`bw-frontend/frontend/src/views/manage/basicTheory/study/basic/edit/Index.vue:171,199-237` 的 `a-upload` action，以及 `views/manage/equipment/equipmentIndex.vue:273-277`。它未被静态 API 导出扫描捕获，不能继续列为无前端调用。

### 两个横切项的运行证据

- 修复前：`POST /api/simulation/router/savePage` Java 404 / Rust 405；同路径非升级 GET 为 Java 404 / Rust 400。真实 REST `GET /api/theoryKnowledge/gradeCount` 两栈 405，必须保留。
- cable 固定相同三个主键时，`find` 和更新 `save` 两栈列表顺序完全一致，都是主键序；两栈分别生成新 UUID 时，实跑连续三次新增均出现不同相对位置。保留 Rust `ORDER BY id`；现有四处生成 ID 的 `unordered` 仅消除独立随机主键的相对顺序，不是忽略业务字段。此处没有 Rust 排序缺陷。

### 修复范围与回归

- `theory.rs`：完整 `gradeCount` 三种图表、课程创建人、有效分类/课件过滤、开放课程固定发布状态、毫秒时间；移除超出 Java 的学习数据级联，课程改题仅按 Java 条件失效答题，关联写入改为主键操作。
- `theory_question.rs` / `theory_test.rs`：数据库 LIKE、创建人、删除节点保留题目、完整批量/测验返回形态；无启用测验返回空数组。测验写入锁现有课件主键，缺父课件返回 202，不创建孤儿测验。
- `exam.rs` / `test_paper.rs` / `device.rs`：全部错题及 Java 分档、缺试卷 202、重复评分规则 202。
- `masthead.rs` / `main.rs` / `ws.rs`：报头按训练主键串行、非主键查询不锁；未知 WS 形状路径的普通 GET/POST 404，真实 REST 错方法仍 405，真实升级仍可用。
- 新增 11 项 `b8_writer_tests` 隔离库行为/锁回归，覆盖列表/LIKE、分档与过滤、保留学习数据、改题失效、并发启用单一版本及并发报头唯一性。首次运行中两处夹具漏填 `swf_record.type`，修正后 11/11 通过；不是放宽断言。
- 平均分静态结论纠正：合成已完成考试分数 `100/80/70` 的 HTTP 实跑，Java 为 `83.3333`，推测性的 Rust DOUBLE 实现为 `83.33333333333333`。撤回该改动，保留原 `AVG(score)` DECIMAL 口径；不依据过时调研改坏兼容性。

### 逐处锁变异（全部失败于行为断言，随后恢复）

统一命令前缀（在 `backend-rust/`）：`RUST_PHASE2_DATABASE_URL=mysql://root:root@127.0.0.1:3306/rust_scratch_lockdemo cargo test --locked -p rust-backend <测试名> -- --ignored --test-threads=1`。

| 独立变异 | 捕获测试 | 失败证据 |
|---|---|---|
| 去掉测验父课件 `FOR UPDATE` | `simultaneous_activations_wait_for_courseware_and_keep_one_enabled` | 两个写者未等待课件主键 |
| 兄弟测验 UPDATE 恢复非主键 `knowledge_swf_id` 条件 | `activation_does_not_lock_other_courseware_tests_or_contents` | 无关测验/题目锁阻塞启用 |
| 测验题目 DELETE 恢复非主键 `knowledge_test_id` 条件 | 同上 | 无关测验/题目锁阻塞启用 |
| 去掉报头父训练 `FOR UPDATE` | `simultaneous_masthead_creates_wait_for_train_and_share_one_header` | 两个写者未等待训练主键 |
| 报头 `train_id` 查询恢复 `FOR UPDATE` | `masthead_save_does_not_lock_other_train_headers` | 无关报头锁阻塞本训练更新 |
| 课程替换课件 DELETE 恢复 `knowledge_id` 条件 | `course_edit_deletes_only_primary_keys_without_locking_other_courses` | 无关课程数据锁阻塞编辑 |
| 课程失效答题 DELETE 恢复 `knowledge_id/knowledge_swf_id` 条件 | 同上 | 无关课程数据锁阻塞编辑 |
| 课程题目 DELETE 恢复关联字段组合条件 | 同上 | 无关课程数据锁阻塞编辑 |

### 跨栈调用面

实际 grep `bw-frontend/frontend/src/common/api/*.js`：`StudyManage_classHours.js:16` gradeCount；`TheoryKnowledgeApi.js:6,13` 两种课程列表；`TheoryQuestionBankApi.js:19,33,46` 节点删除、批量保存、LIKE 查询；`TestApi.js:19,61,165` 启用测验、课程删除、成绩分析；`TrainingDetails.js:30` 评分规则；`ReceiveApi.js:205,212` 报头。

真实消费：`scoreUseChart.js` 读取 `up.all/up.good/down` 三种形态；课程 `useList.js` 展示作者与时间；题库 `knowledgeTabel.js` 展示作者并解析字符串 options；详情 `useDetails.js` 遍历启用测验数组；`testAnalyse.vue` 遍历全部 errorTop3。接口名、请求参数和响应信封不变，无需前端迁移；凭据字段不外泄、207 属主保护仍保留。

### 已执行验证

- `backend/`，JDK 21：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify`，513 测试，0 失败/错误/跳过。
- `backend-rust/`：`cargo fmt --check`、`cargo test --locked`，79 通过、154 ignored；ignored 不计作已验证。
- 同目录，隔离库 `rust_scratch_lockdemo`：`cargo test --locked -p rust-backend b8_writer_tests:: -- --ignored --test-threads=1`，最终 11/11 通过；以上八处变异分别使对应测试失败，全部恢复后重跑 11/11 通过。
- 同隔离库，分别运行 `business_message_waiting_on_a_row_lock_does_not_stall_heartbeats` 与 `opening_a_key_room_waits_for_the_room_lifecycle_lock`，各 1/1 通过。前者证明行锁等待不阻塞心跳，后者证明开房等待生命周期锁。常规套件另覆盖真实 WS 升级及普通 HTTP 的 404/405 边界。
- `python3 -m unittest discover -s backend-rust/scripts -p 'test_*.py'`，68/68 通过；输出含一次未关闭 socket 的 `ResourceWarning`，没有测试失败，本批未改 harness Python 实现。
- `backend-rust/scripts/local-dual-stack.sh journeys`，合并 11 条 B8 旅程/202 步后，共 2454 请求：2285 identical、169 ACCEPTED、0 FAIL/OPEN；未为分数/正文增加 mask。随后平均分单独 HTTP 复验，两栈均返回 `83.3333`。
- 临时上传文件已移除；两处失败夹具遗留的合成账号及关联行按已选主键清理，未清空其他 scratch 数据。双栈扫描自身会重置其专用 parity 库。

### 首次全量扫描补充发现

3903 请求首轮有 5 个 FAIL：4 个同源于测验题目排序，1 个为旧 B5 导入列表漏掉时钟字段归一化。

- Java `TheoryKnowledgeTestContentDao:47-61` 按 `createTime` 升序，Rust 原读接口按 `sort`。同毫秒创建的题目恰好按随机主键换序时才暴露。已把测验列表、启用测验、课程详情三个读路径改为 Java 的创建时间顺序；保存响应仍保留输入顺序。B8 夹具在保存后把两题创建时间设为不同固定值、且刻意与 sort 反序，随后四个读取步骤严格比较题目、答案、sort 和 ID，不使用 unordered 掩盖排名。
- B5 `admin-list-imported` 实测 Java `validTime=-2` / Rust `0`；Java `GeneralKeyPatService:623-631` 用两次本地墙钟差，Rust `general_train.rs` 用 `GREATEST(0,TIMESTAMPDIFF(...))`，沿用已拍板的负时长截零口径。该旅程 export/detail 已归一化此时钟字段，补齐导入列表同一字段的 mask；分数、采集区间、正文和导入身份仍原样比较。

### 最终门禁

排序修正及确定性夹具合入后再次执行 `cargo fmt --check`、`cargo test --locked`（79 通过）、B8 ignored 隔离库回归（11 通过）、Rust build 与 Python unittest（68 通过）。重启实际 Java/Rust 服务后连续运行：

| 命令 | 请求 | identical | ACCEPTED | FAIL / OPEN |
|---|---:|---:|---:|---:|
| `$S journeys docs/reviews/rust-migration/b8-research/misc-journeys.draft.json` | 202 | 168 | 34 | 0 / 0 |
| `$S full`（含全部 2454 旅程请求） | 3903 | 3225 | 678 | 0 / 0 |

已执行 `$S down`。本批没有生产切流、浏览器/Electron 实测或 Windows/arm64 交付证明；Phase10 继续阻断，下一步仍是单域真实切换与回滚演练。

### 双栈进程生命周期纠正

收尾实际连接检查发现旧脚本 `$S down` 后 Java :18121 仍返回 200。`ss` / `ps` 确认是前一日遗留 PID 9671、命令指向本仓库 fast-jar；旧 `up` 把后台 `cd && java` 的中间 shell PID 写进 `java.pid`，停止时未命中 Java，后续启动也可能误把旧服务当成就绪。

`local-dual-stack.sh` 现将整个子 shell 放到后台，并以 `exec env … nohup java` 替换进程，确保 `$!` 就是 Java PID。`bash -n` 通过；实跑 `up` 后 `java.pid=418628` 与 :18121 监听进程完全一致；随后 `down`，curl 对 :18121/:18122 均连接拒绝（exit 7 / HTTP 000）。旧孤儿进程已按核实 PID 终止。最终对账再使用这一正确生命周期启动的本轮构建产物，不把旧 Java 的存活当作新构建的验证。

生命周期修复后，使用本轮 JDK21 `clean verify` fast-jar 与最终 Rust binary，完整执行 `$S up && $S journeys <B8草稿> && $S full && $S down`：B8 202 请求 / 168 identical / 34 ACCEPTED；全量 3903 请求 / 3225 identical / 678 ACCEPTED，均 0 FAIL/OPEN。结束后两端口再次连接拒绝（exit 7 / HTTP 000）。最终结论以这次全新进程运行结果为准，前述同计数结果不替代它。
