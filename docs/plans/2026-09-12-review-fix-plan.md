# 2026-09-12 评审整改实施计划（Plan）

> 对应 Spec：[`../specs/2026-09-12-review-fix-spec.md`](../specs/2026-09-12-review-fix-spec.md)（已按 5 路可行性评审修订，见其 §0）。权威依据：[`../reviews/2026-09-12-full-project-review.md`](../reviews/2026-09-12-full-project-review.md)。
>
> 本文是**已执行计划**：条目状态按 2026-09-12 收口时对**当前源码**的逐条核验回写（6 路并行只读核验 + 我自己的运行验证）。`[x]` = 有 path:line 或运行证据；`[~]` = 以偏离形态交付，理由就地写明；`[!]` = 核验时发现未达成、已在本轮补做。行号会漂移，以符号名现取。
>
> **B6（测试与文档）已完成**，见评审 §6.1（`f31861a..dfb43c3`）。本计划覆盖 B1、B2、B3、B4、B5、B7。

## 1. 交付策略

### 1.1 成功标准

- 4 条 P0 与 Spec §1.1 列出的 10 条 P1 全部关闭，每条有 Spec §11.3 的运行态证据。
- 后端 `./mvnw -B clean verify` 全绿且测试数 `>= 317`；前端 `npm run test >= 15`、`npm run build` 成功；迁移演练双快照通过。
- **每个 T 编号一个可独立回滚的提交**（同提交例外仅：跨栈两侧、重命名与引用、修复与其回归测试）。
- 未完成项只允许出现在 Spec §1.2 非目标或 §13 门禁里，且写明依据 —— 不得把「未做」记成「已修」。

### 1.2 执行波次

| 波 | 任务 | 并行度 | 前置 |
|---|---|---|---|
| **W0** | T3-0（授权拒绝原语 `ForbiddenException` + 迁移三个旧 helper） | 1 路（串行） | 无 —— **T1-1/T2-2/T2-3/T2-4/T4-1 的 207 验收都依赖它** |
| **W1** | T1-2、T1-3、T1-4、T5-1、T5-5、T7-1、T7-2、T7-6 | 8 路并行 | W0 |
| **W2** | T1-1、T2-1、T2-2、T2-4、T5-2、T5-4、T7-7、T7-9 | 8 路并行 | W1 |
| **W3** | T2-3、T3-1、T3-3、T3-4、T3-5、T3-6、T7-3、T7-5 | 8 路并行 | W2 |
| **W4** | T4-1（后端+前端同提交）、T4-2、T5-3、T7-8 | 4 路并行 | W3（`requireWritableTrain`、token 哈希夹具已就位） |
| **W5** | 全量验证 + 推送 + 回写评审文档 | 串行（我做） | W1–W4 |

**波内并行硬前提**：每个任务跳过所有验证命令（build/lint/test），我在波间做编译闸门、W5 做全量。

**为什么这样分波**（首版被评审推翻的点）：
- `ForbiddenException` 必须先行独立成波：五个任务的 207 验收都依赖它，否则各自造一套。
- T3-1（token 哈希）必须早于 T4-1 的测试编写：否则 W4 新写的测试夹具会在 T3-1 落地时集体红。
- T1-1 与 T3-4 都动 `GeneralKeyPatService`/`GeneralKeyPatController` → 拆到 W2/W3 不同波。
- T2-3 与 T7-5 都动 `GeneralKeyPatService`/`GeneralTickerPatService`（前者改 `delete`，后者改 `closingTrainIds`）→ 同波但**必须声明方法级独占**（见 §1.3）。
- T2-2 与 T3-6 都动 `application.yml`（cors 段 / `%prod` 段）→ 拆到 W2/W3。
- `generalTelexPat` 域的 `delete`/`updateTrainStatus` 归 **T4-1**，不归 T2-3（避免跨波双所有权）。

### 1.3 文件所有权（波内互斥）

| 任务 | 独占文件 / 方法 |
|---|---|
| T3-0 | `common/exception/ForbiddenException.java`(新)、对应 Mapper、`PostTelegramTrainService.owned`、`PostTelegraphKeyPatTrainService.owned`、`PostTelexPatTrainService.requireOwnedTrain` |
| T1-2 | 四个 controller + 新增架构测试 + 规则页 UI 守卫 |
| T1-3 | `controller/TheoryKnowledgeExamUserController.java`、`service/TheoryKnowledgeExamUserService.java` |
| T1-4 | `bw-frontend/electron/service/http/**` |
| T5-1 | `bw-frontend/bin/nip.db`、`bw-frontend/electron/core/index.js` |
| T5-5 | `bw-frontend/bin/config.json`、`views/demo/**` 及硬编码地址点 |
| T7-1 | 新迁移脚本 + `scripts/rehearse-migrations.sh`(MIGRATIONS 追加) |
| T7-2 | `service/general/GeneralSettlementRecovery.java` |
| T7-6 | `migrations/2026-09-11-03-post-telex-capture-clock.sql` + 新数据迁移脚本 |
| T1-1 | `dto/general/UserSyncDto.java`、`GeneralKeyPatService`(`getTrainInfo`/`getTrainInfoBatch`/新增 `exportable`)、`GeneralKeyPatController`(`:144-165`) |
| T2-1 | `controller/GradingRuleController.java`、`controller/DeviceScoringRuleController.java`、理论题库 controller |
| T2-2 | `controller/TheoryKnowledgeExamController.java`、`service/TheoryKnowledgeExamService.java`、`controller/free/UserController.java` + 对应前端 api |
| T2-4 | `controller/UserController.java`、`service/UserService.java`(`findAllUser` 路径)、`dao/UserDao.java:93-107`、`dto/UserProfile.java` |
| T5-2 | `bw-frontend/electron/index.js`、`bw-frontend/main.js`、新 preload、`frontend/src/common/utils/electronSerial.js`(删) |
| T5-4 | `bw-frontend/electron/serial/**`、`frontend/src/components/common/NipSerial.vue` |
| T7-7 | `components/BroadcastTeachTrain/js/useBroad{Student,Teacher}.js` |
| T7-9 | `examTrain.js`、`electronKeyZuXun/.../handKeyTrain.js`、`teacherBack.js`×4、死导入 |
| T2-3 | `common/*/requireWritableTrain` 新 helper + 各域 `delete`/`updateTrainStatus` **方法体**（不含 `closingTrainIds`） |
| T3-1 | `service/UserService.java`、`dao/UserDao.java`、`common/utils/AESUtil.java`、`testsupport/Fixtures.java` + 受影响测试类 |
| T3-3 | `common/interceptor/JWTInterceptor.java`、`RequireAdminInterceptor.java` |
| T3-4 | `GeneralKeyPatController.java`(`:152-173`)、`GeneralKeyPatService`(导入段)、`UserService.replaceUserIdAndSaveIfNotExist` |
| T3-5 | `ws/**`、`testsupport/WebSocketSessionProbe.java`、WS 测试类、`frontend/src/common/ws/SocketConnection.js` |
| T3-6 | `application.yml`(`%prod` 段)、`controller/free/ToolsController.java`(删)、新启动校验 |
| T7-3 | 11 个 service 的 `findUserEntityByToken` 调用点 |
| T7-5 | `GeneralKeyPatService.closingTrainIds` + `GeneralTickerPatService.closingTrainIds` **方法体** |
| T4-1 | `service/general/GeneralTelexPatService.java`、`controller/general/GeneralTelexPatController.java`、`dto/general/GeneralTelexPat*`、`dto/TelexPatValueTransferDto.java`、telex 实体与 DAO、新迁移、11 个前端消费文件 |
| T4-2 | `organization/{datagramZuXun,telexZuXun}/train/student/**` |
| T5-3 | `bw-frontend/scripts/artifact-manifest.cjs`、`.github/workflows/**` |
| T7-8 | `common/constants/ResponseCode.java`(新增码)、异常映射、`common/mixin/useConfirmedSubmission.js` |

> T2-3 与 T7-5 同波但方法级独占：两者改同一文件的不同方法，git 可自动合并；任务指令里必须写明「只改指定方法体，不得顺手格式化整文件」。

## 2. W0：授权拒绝原语

### T3-0 `ForbiddenException` + 迁移旧 helper

- [x] 新增 `common/exception/ForbiddenException.java` + Mapper，映射为 **HTTP 200 + `code:207`**（`ForbiddenExceptionMapper` → `ResponseResult.error(CODE_207)`）
- [~] 三个旧 helper 改抛 `ForbiddenException`；**旧方法名保留**：核验确认它们不是别名而是各域的「取实体 + 不存在→202」取数封装（`PostTelegramTrainService.owned` 还带 `lock` 开关），授权判定本身已统一委托 `TrainWriteAccess.requireTrainOwner`，删掉会让每个调用点重复一遍取数+加锁+404 映射。红线 6 的「不留第二套约定」由「授权口径单点」满足
- [x] 回归测试：`TrainOwnershipAuthorizationTest` 对电报/电子键/电传三域断言非属主得 **207**
- [x] 已有断言 202 的用例同提交改为 207（`b8cc186`）
- **提交**：`refactor(security): 统一授权拒绝为 207 并收敛属主判定原语`

## 3. W1

### T1-2 四个 controller 补类级 `@JWT`

- [x] 四类加 `@JWT`（`PostTrainGlobalRuleController:18`、`PostTickerTapeTrainSettingController`、`TelegraphKeyPatTrainSyntheticalController`、`CableFloorController:17`）
- [x] 规则类写端点加 `@RequireAdmin`（`PostTrainGlobalRuleController` 的 `addRule`/`deleteById`）
- [x] grep 前端调用面确认均在登录后调用（结果在 `bb69a0c` 正文）
- [~] 规则页 UI：走 spec 给的**第二条路径**——不恢复 `v-per`，而是让 207 有可见解释（`common/http/terminalCode.js:9` 的 207 文案 + 终态提示），因此 6 处注释掉的 `v-per` 保持原状
- [x] 架构测试：`ControllerJwtGuardArchitectureTest:48-57` 反射扫描非 `free/` 包断言全部带 `@JWT`
- [x] 回归：`ControllerAuthorizationGuardTest` 断言匿名 203、学员调规则写端点 207
- **提交**：`fix(security): 四个遗漏鉴权的控制器补类级 JWT 并加架构守卫`

### T1-3 上分端点授权

- [x] `teacherUploadScore` 加 `@RequireAdmin`（`TheoryKnowledgeExamUserController`）
- [x] 校验 `list` 中考生属于该 `examId`（`TheoryKnowledgeExamUserService:82-85`，外场考生整批拒绝）
- [x] 副作用写入提交正文（`e2af312`）+ 发布说明（`docs/guides/2026-09-12-release-runbook.md` §4.3）
- [x] 回归：`TheoryKnowledgeExamUploadScoreAuthorizationTest:42-96` 普通人员 207、跨考试考生整批回滚
- **提交**：`fix(security): 教师上分端点补管理员授权与考生归属校验`

### T1-4 桌面文件服务收紧

- [x] 根取**子进程 ctx** 的 `appPath`（`electron/service/http/index.js:8-9` 设 `<cwd>/bin/file`；`controllers/file.js:8-13` 注释写明「绝不可改用主进程 appPath」）
- [x] 读路径 `path.resolve` + `startsWith(ROOT+sep)` + 对存在文件 `realpathSync` 二次校验，越界 404（`controllers/file.js:43-56` `resolveWithinRoot`；404 在 `:77-78,:87-88`）
- [x] 写路径 `currentPath` 逐段剥 `..`/绝对段（`sanitizeCurrentPath:59-69`）；`mkdir` 失败返 500（`:137-143`）、`rename` 失败计入 failed 返 500（`:184-201`）
- [x] `node_server` 加 `host`（默认 `127.0.0.1`，`node_server.js:10,16`）并 `app.listen(port, host)`（`:38`）
- [x] **ACAO 不动**（`node_server.js:26` 原样），判断依据在 `index.js:31-32` 注释与 `07ef886` 正文
- [x] 运行证据（真实 `--dir` 产物）：`ss -ltnp` 显示 8000 仅 `127.0.0.1`；`/api/file/getFile/../../bin/nip.db` 与 `../../../../etc/passwd` 均 404；局域网地址连接被拒
- **提交**：`fix(desktop): 内嵌文件服务约束在资源根内并默认只监听本机`

### T5-1 发布态状态归一

- [x] `nip.db` 的 `_id:2` → localhost（`bin/nip.db:1` `localhost:18001` / `127.0.0.1:8000`）、`_id:3` 不预置（文件仅一行）
- [x] **不**纳入 CI 脏检查：`scripts/artifact-manifest.cjs:21` 只做存在性检查，`sourceDirty` 是全局 `git status --porcelain`，CI 构建期只写 gitignore 的 `bin/server/`
- [x] 证据：`git show HEAD:bw-frontend/bin/nip.db` 无业务 IP/COM 口；真实 `--dir` 冷启动 `window.httpUrl === 'http://localhost:18001'`
- **提交**：`fix(desktop): 随包配置归一为发布态默认`

### T5-5 死配置与硬编码地址

- [x] 删 `bin/config.json` 的 `server`/`serial` 字段，**保留文件本身**（当前内容 `{}`，避免 `loadConfig` 走 catch 致 `context.db` 变 `{}`）
- [x] 清理四处硬编码地址：`views/demo` 整目录删除（含两处模块顶层建 WS 的 demo 页）、`config.json` 的 `10.0.0.217`、`nip.db` 的 `10.10.0.117`
- [x] 证据：全仓 grep 三个 IP 仅命中 docs/（历史取证文本），源码零命中
- **提交**：`chore(desktop): 清理死配置与硬编码地址`

### T7-1 电传倒计时索引

- [x] 新增幂等索引迁移 `2026-09-12-02-post-telex-due-index.sql`（`information_schema` 判存 + PREPARE/EXECUTE）+ 接入 `rehearse-migrations.sh` 的 `MIGRATIONS`
- [x] 证据：演练脚本断言 `idx_post_telex_due(status,deadline)` 存在且形状精确（双快照均 PASS）
- **提交**：`perf(db): 电传倒计时扫描补支撑索引`

### T7-2 启动结算扫描兜底

- [x] 两处 `closingTrainIds()` 扫描加 try/catch（`GeneralSettlementRecovery` 的 recover 助手 `:40-45`，不外抛）
- [x] 测试：`GeneralSettlementRecoveryTest` 用 `assertDoesNotThrow` 断言扫描抛异常时 `recover()` 不外抛、且跨域不连坐
- **提交**：`fix(settlement): 启动结算恢复容忍瞬时扫描失败`

### T7-6 数据迁移拆分

- [x] 菜单 UPDATE 拆为独立脚本 `2026-09-12-03-menu-telex-component-path.sql`；**刻意不进 `MIGRATIONS`**（该演练比对 schema 与实体等价性，数据 `UPDATE` 无 schema 差分），但在部署清单里是必执行项
- [!] 迁移清单标注前端契约依赖：核验时只在 SQL 文件头（`:8-11`）标了，`backend/README.md` 的 14 脚本清单没标 → 本轮补（`92e528e`），并新增 `docs/guides/2026-09-12-release-runbook.md` 逐脚本还原步骤
- **提交**：`chore(db): 拆分菜单数据迁移与 schema DDL`

## 4. W2

### T1-1 训练同步端点不回传凭据

- [x] `UserSyncDto` 删 `password`/`token`/`deviceId`（字段段已无三者，`:9` 注释写明刻意不含）
- [x] `getTrainInfo` 加 `@RestHeader(TOKEN)`（`GeneralKeyPatController:146-172`）；谓词 `exportable` 在 `GeneralKeyPatService:1088-1098`（创建者 ∪ role=1 ∪ 管理员，复用 `TrainWriteAccess.manages:58-65`）
- [x] `getTrainInfoBatch` 用谓词 **filter**（`GeneralKeyPatService:1176-1184`，学员得空列表不抛）
- [x] grep 前端是否消费三个字段（结果在 `fcbd798` 正文）
- [x] 回归：`GeneralKeyPatExportAuthorizationTest` 断言非授权 207（`:53-57`）、响应不含三键（`:80-84`）、学员 batch 空列表（`:107-121`）
- **提交**：`fix(security): 训练同步端点不再回传会话凭据并加导出归属判定`

### T2-1 规则与主数据写端点授权

- [x] `GradingRuleController` 4 个写端点（`:55,63,71,79`）+ `DeviceScoringRuleController` 2 个（`:43,52`）+ 题库写端点（`TheoryKnowledgeQuestionController:46,53,66,73,94`）加 `@RequireAdmin`
- [x] 读端点确认**未加**（`GradingRuleController:38,45`、`DeviceScoringRuleController:58-63`、题库读/导出）
- [x] 回归：`RuleAndQuestionBankAuthorizationTest:48-173` 逐端点断言 学员 207 / 读端点 200 / 管理员成功
- **提交**：`fix(security): 评分规则与主数据写端点补管理员授权`

### T2-2 理论考试与统计端点身份收口

- [x] `studentChangeExamState`、`saveUserRealTimeParam` 改 token 推导 + 删 body `userId`（`TheoryKnowledgeExamService:175-176,214-215`）
- [x] `findExamUser` **保留 `userId`** + 授权判定（`TheoryKnowledgeExamUserService:113-126`：本人／该场 teacher/createUser／管理员）
- [x] `finishSelfTesting` 服务端按试卷快照重算（`TheoryKnowledgeExamService:301-334,360`）
- [x] 3 个统计端点移出 free 包、加 `@JWT`、token 推导、**路径不变**（`UserTrainStatisticsController:34-76`；前端 `common/api/trainingStatistics.js:5,14,20` 路径字符串未变）
- [x] `cors.origins` **未动**（`application.yml:21` 仍为 `*`）
- [x] 前端对应 api 与调用点同提交（`995c669`）
- [x] 回归：`TheoryExamIdentityAuthorizationTest` 断言 A 改不了 B（`:55-85`）、任意 `score` 被重算（`:88-116`）、教员仍能阅卷（`:138-168`）、匿名统计 203（`:171-181`）
- **提交**：`fix(security): 理论考试与训练统计端点按token收口身份`（跨栈同提交）

### T2-4 用户目录端点与注入

- [x] 全量目录能力收敛到唯一入口 + `@RequireAdmin`（`UserController:100,124,134,142`）
- [~] 脱敏 DTO 以 **`UserSummary`** 交付（`dto/UserSummary.java:7`，record，不含 `idCard`/`phone`），非计划文本里的 `UserDirectoryEntry` —— 仅命名差异，契约与回归覆盖一致；非管理员路径走 `getUserDirectory`（`UserController:115`）
- [x] `findAllUser` 的 `REGEXP` 拼接改参数化（`UserDao.queryByIdIn:92-93` 的 `id in ?1`），空列表返空集；`REGEXP` 全仓零残留
- [x] grep 前端是否消费 `idCard`/`phone`，同提交处置（`caca347`）
- [x] 回归：`UserDirectoryAuthorizationTest:66-69` 断言脱敏结果 `idCard` 全为 null；`:111-134` 断言 `.*`/`a|b` 元字符不再影响匹配集
- **提交**：`fix(security): 用户目录端点收敛授权并参数化查询`（跨栈同提交）

### T5-2 渲染进程安全特性

- [x] 新增 `electron/preload.js`，白名单**恰好** `{invoke:58, send:59, sendSync:62, on:64, once:67, off:70}` 六个
- [x] `on`/`once` 经 `subscribe`（`:44-49`）包装为 `(_event, ...args) => listener(null, ...args)`
- [x] **不暴露 `removeListener`**（仅 `:75` 内部使用）；`common/utils/electronSerial.js` 已删（glob 零命中、无 import）
- [x] `contextIsolation: true`（`electron/index.js:57`）、`nodeIntegration: false`（`:56`）、preload 挂载（`:58`）
- [x] 删 `main.js` 的 `--ignore-certificate-errors`（全仓 grep `ignore-certificate` 零命中）
- [x] `webSecurity` 保持 `false` 并就地注释原因（`electron/index.js:59-65`，`file://` + AudioWorklet CORS）
- [x] 运行证据（真实 `--dir` 包 + CDP）：`require`/`process`/`module` 均 `undefined`、`window.electron.ipcRenderer` 只有 6 个白名单方法、`sendSync('controller.system.getConfig')` 正常回配置、`AudioWorklet.addModule` OK；真实登录进入 dashboard
- **提交**：`fix(desktop): 渲染进程启用上下文隔离并移除证书校验开关`

### T5-4 串口链路容错

- [x] `_id:3` 缺失（`electron/serial/index.js:16-24` 返 `''`）、端口缺失（`:39-53` 弹提示 + `callback('')`）、非 linux 分支（`nativeSerialPort.js:89-91` 返对象不崩）三处修正
- [x] 监听移到顶层只注册一次（`electron/serial/index.js:55-73` `serialApiHandle`，`electron/index.js:86` 在 `createWindow` 末尾只调一次）
- [x] 列举时**不再**自动 `pkexec`（`nativeSerialPort.js:58-59`）；新增显式 `grantAccess` 路由（`controller/serialPort.js:33-48`）+ try/catch（`:35-44`）+ `filter(Boolean)`（`:36`、`nativeSerialPort.js:96`）
- [x] `NipSerial.vue:31-34` 对 `accessible===false` 显示「需要授权」+ 按钮；`:77` 提示「加入 dialout 组需重新登录才生效」
- [x] 运行证据：真实 `--dir` 包串口页打开不崩、列举不弹提权（`grantAccess` 才弹）
- **提交**：`fix(desktop): 串口链路容错并改为显式授权`（跨栈同提交）

### T7-7 通播教学页拒因文案

- [x] `useBroadStudent`/`useBroadTeacher` 的 `res.msg`→`res.message`、`envelope.msg`→`envelope.data`（两文件 grep `.msg` 零命中）
- [!] 「真实页面可见拒因」核验时发现**同类缺陷漏改 7 处**：`unionJob/` 的 `lineNotify`、`disturbCode`、`broadcastTeacheing` 仍读 `res.msg`/`data.msg`（恒 undefined，用户只看到兜底文案）→ 本轮补齐（`23cafc9`），spec §11.2 的「前端 `res.msg` = 0」门禁此前并未真正达成
- **提交**：`fix(frontend): 通播教学页显示后端真实拒因`

### T7-9 死代码与热路径日志

- [x] 删 `console.log`：`examTrain.js`、`handKeyTrain.js`、`datagramZuXun/.../student.vue`、`telexZuXun/.../score.vue` 目标点 grep 零命中
- [x] 删死墙钟（`handKeyTrain.js` 的 `Date.now()` 零命中）
- [x] 删 `teacherBack.js` ×4（glob 零命中）、datagram/telex 学生页未调用的 `useControl` 导入
- [x] 证据：全仓 grep 无残留；`npm run build` 通过
- **提交**：`chore(frontend): 清理热路径日志与死代码`

## 5. W3

### T2-3 写权限口径统一

- [x] 新增唯一 helper `TrainWriteAccess.requireWritableTrain:49-53`（创建者 ∪ role=1 组训人 ∪ 管理员，抛 `ForbiddenException`）
- [x] 三个旧 helper 的**授权判定**已全部委托到新原语（旧方法名按 T3-0 的理由保留为取数封装）
- [x] 各域 `delete`/`updateTrainStatus` 接入，属主字段名显式传入不反射（`TrainWriteAccess:23-24` javadoc）
- [x] `generalTelexPat` 域不在本任务（归 T4-1，见其 `requireWritableTrain`/`requireMember` 调用）
- [x] 回归：`WritableTrainAuthorizationTest:62-64` 非授权删他人训练 207 且 DB 行仍在；`:93-116` 非创建者的 role=1 组训人仍能改状态且真改
- **提交**：`refactor(security): 训练写权限统一为创建者与组训人与管理员`

### T3-1 token 随机化与哈希存储

- [x] 签发 `SecureRandom` 不透明串（`SessionToken.issue:29,42-44`），明文只回 `LoginSessionDto.token`（`UserService.login:465-475`）
- [x] 存 SHA-256 hex（`SessionToken.hash:53-58`）；`existsUserByTokenAndDeviceId:50-53` 与 `findUserEntityByToken:74-75` 均先哈希再查
- [x] 四处写面：`login:465-475`、`userOut:500-501`、`importUser:380-381`（置 NULL）、`replaceUserIdAndSaveIfNotExist:621-632`（白名单 + 服务端决定）
- [x] `lsp references` 覆盖 `findUserEntityByToken` 调用点（现全仓只剩 `UserService` 内 2 处且都判空）
- [~] 测试面：`Fixtures.user:33-45` 是「入库写哈希、返回明文」的正规工厂，但核验发现摘要口径在测试侧另有 7 份手写副本（4 处播种 + 3 处按 token 反查）→ 本轮补做单点收敛（`Fixtures.sessionToken`/`userIdByToken`，提交 `1033f7d`）。剩余两处直接引用 `SessionToken.hash` 的是**断言存储口径本身**的用例，必须保留
- [x] `AESUtil` 整类删除（文件不存在、生产零引用）
- [!] runbook 写明「存量会话全部失效」：核验时这句话只在 spec/plan 需求文里，仓库没有发布说明制品 → 本轮新增 `docs/guides/2026-09-12-release-runbook.md` §4.2（`92e528e`）
- [x] 回归：`OpaqueSessionTokenTest:57,60-61,73,83,96` 五条；另有运行实测：两次登录 token 不同、DB 64 位摘要 ≠ 响应 43 位明文
- **提交**：`fix(security): token 改不透明随机串并按哈希校验`

### T3-3 拦截器收口

- [x] 两个拦截器删 query 回退（`JWTInterceptor:58-59`、`RequireAdminInterceptor:38` 只读请求头；`getParam`/`QueryParam` 在拦截器里零命中，仅剩一行说明注释）
- [x] 兜底分支改固定 `SYSTEM_ERROR` 文案、细节只进日志（`JWTInterceptor:74,78`）
- [x] 回归：`ControllerAuthorizationGuardTest` 断言仅 query token 得 203
- **提交**：`fix(security): 鉴权只接受请求头并停止回传原始异常`

### T3-4 训练导入授权与白名单

- [x] 两个导入端点加 `@RequireAdmin`（`GeneralKeyPatController.importTrainInfo:160`、`importTrainInfoBatch:176`）
- [x] 只允许 `userAccount`/`userName`/`userImg`；`password`/`token`/`deviceId`/`status` 服务端决定（`UserService.replaceUserIdAndSaveIfNotExist:621-632`）
- [x] 顺带修 `replaceUserIdAndSaveIfNotExist` 的导入失效缺陷（表现写在 `47f0741` 正文）
- [x] 回归：`GeneralKeyPatImportAuthorizationTest:70` 学员 207；`:86-88` 导入号 token/deviceId/password 均 NULL；`:124` 夹带密码无法直接登录；`:113` status 服务端决定；`:140-143` id 重映射
- **提交**：`fix(security): 训练导入端点补授权与用户字段白名单`

### T3-5 WebSocket 握手鉴权

- [x] **6 个**带身份语义端点在 `@OnOpen` 校验 query 凭据并以校验身份覆盖路径 `uid`（`WebSocketHandshake.authenticate:47-64`、`bind/authenticatedId:66-82`）
- [x] 删除零引用 `StartWebSocket`（glob 零命中）；`/status` 保持匿名（实测带/不带凭据均 OPEN）
- [x] 仿真：`WebSocketSimulationService:112` 无 `roomUser` 行且不是 `createUserId` 才拒绝；teacher 口径与 `SimulationRoomAccess:57-60` 对齐（注释交叉引用）；建房人合成成员保持 `userType=null`/`channel=-1`（`:123-127`）
- [x] `role` 比对只保留在 `/generalTickerPat`
- [x] 前端注入点 `common/ws/SocketConnection.js:11-24,39,50`（重连复用 `this.url`）；`MessageWebSocket.js:115` 是本地 echo，正确排除
- [x] 测试面：`WebSocketSessionProbe:56` 加 `getRequestParameterMap`；WS 测试 URI 带凭据
- [x] 回归：`WebSocketHandshakeAuthorizationTest` 无凭据被关（`:79-92`）、A 凭据连 B 按 A 注册（`:95-119`）、建房人合成成员入房广播（`:127-163`）、外人被拒（`:171-193`）
- [!] 运行证据：**核验发现门禁可被路径参数转换绕过** —— `@PathParam Integer` 转换失败时 `@OnOpen`/`@OnError` 都不被调用，未鉴权连接被无限保持（实测 `/generalKeyPatTrain/1/not-a-number` 不带凭据 OPEN-HELD）→ 本轮修复并补回归（`f9f97bd`）。修复后实测：6 个端点在「无凭据」与「非法路径」两种情况下一律 `CLOSED(1000)`；真实房间 happy path（新建手键组训 id=75）教员/学员各自带凭据均 OPEN、上线与 `ready` 帧互达；真实打包桌面登录后 shipped `SocketConnection` 注入凭据的 `/websocket/1` opened 且收到推送帧
- **提交**：`fix(security): WebSocket 握手校验凭据并以校验身份覆盖路径参数`（跨栈同提交）

### T3-6 生产凭据外置与侦察面

- [x] `%prod` 改 `${DB_USER}`/`${DB_PASSWORD}`（**不写默认值**）；`%dev` 保留字面值
- [x] ~~新增 `%prod` 启动校验~~ **撤销**：JPA 引导早于 `StartupEvent` 观察者，守卫是死代码；前移到 SmallRye 配置拦截器会挡掉 `mvn package`。改为在 `%prod` 配置注释里写明必须注入的变量（见 spec §6 T3-6 目标 2 的撤销理由）
- [x] `/api/tools/system` **整端点删除**（前端零消费）
- [x] 回归（打包产物实测）：缺变量 → 启动失败、不监听 HTTP；注入 `DB_USER=root DB_PASSWORD=root` → `started in 3.188s`、`validate` 通过、`/q/openapi`=200、登录信封仍为 `200 + code:500`；该端点 404
- **提交**：`fix(security): 生产数据源凭据外置并删除系统侦察端点`

### T7-3 鉴权路径空返回收敛

- [x] `findUserEntityByToken` 的调用点改走 `UserService.getUserByToken`（现全仓只剩 `UserService` 内 2 处且都判空）
- [x] 其余裸 `firstResult()` 登记后续批次（当前 58 处 / 45 文件，已写入 AGENTS.md 红线 4）
- [x] 回归：`TokenExpiredUnauthorizedTest:38-70` 五个 service 在 token 失效时抛 `UnauthorizedException`（→203）而非 NPE/500
- **提交**：`fix(auth): 鉴权路径不再裸解引用 DAO 空返回`

### T7-5 收尾扫描投影

- [x] 两个 `closingTrainIds()` 改 `select id` 投影（`GeneralKeyPatService:634-636`、`GeneralTickerPatService:687-689`，返回 `List<Integer>`）
- [x] 证据：JPQL 均为主键投影，不再加载含 `longtext` 的整实体
- **提交**：`perf(settlement): 收尾扫描改主键投影`

## 6. W4

### T4-1 组训数据报域纳入采集契约

- [x] **范本**：页内采集与重算照 `PostTelexPatTrainService`；数据分层与房间/role 维度照 general 域（未引入点划 `symbolMillis` 对账）
- [x] DTO 加 `protocolVersion`/`attempt`（`GeneralTelexPatPageSubmitDto:39,43` 用 `StrictIntegerDeserializer` 严格数字）/`captureIntervals`（`:47`），并删 `speed`/`validTime`（全类 48 行无二者）
- [x] `saveContentValue` 按 `CaptureTimeline` 校验并服务端重算逐页用时/码率（`GeneralTelexPatService:451`）
- [x] `countScore` 基准分改冻结规则满分（`:880` + `frozenFullScore:1215`，硬编码 100 已消失）
- [x] 原始行分层：删除改 `sort > -1`（`deleteRawBy:499`/`setSort(-1):504`）、`pageValueResult` 由 `handle` 构建不再 `addAll(userValue)`、原始行带 `attempt`/`capture_intervals`/`received_at` 原地保留
- [x] `finish`/`saveContentValue` 行锁（`lockedTrain:1042` `PESSIMISTIC_WRITE`）+ attempt 栅栏（`requireAttempt:1115`）+ `isFinish` 幂等（`:483,612`）
- [x] WS 通知改 `AFTER_SUCCESS`（`GeneralPatResultNotifier:27` `@Observes(during=TransactionPhase.AFTER_SUCCESS)`；`GeneralTelexPatService:575` 复用 `publish`）
- [x] controller 12 个端点全部加 `@RestHeader(TOKEN)`（`GeneralTelexPatController:43..148`），写口径入 `requireWritableTrain`/`lockedStudent`、读口径入 `requireMember`
- [x] 采集边界用成员行 `capture_started_at` → 训练 `end_time`（`captureBound:1148`，含宽限）；**未加时钟列**（迁移 `:7` 明确写明）
- [x] 收尾扫描与 `(status,end_time)` 索引对齐（`closingTrainIds:590` + 迁移 `:81` 建 `idx_general_telex_pat_closing`）
- [x] 新增幂等迁移 `2026-09-12-01-general-telex-capture.sql` + 接入 `MIGRATIONS`（`rehearse-migrations.sh:79`）+ 重新生成 `database/rehearsal/2026-09-12/entity-schema.tsv` + 双快照通过（diff 文件为空）
- [x] 前端消费面：实测 10 个 API 消费者 + `common/api/datagramZuXun.js` = 11 个文件，上传复用 `useTrainingCapture`，两侧同提交（`0c03279`）
- [x] 回归：`GeneralTelexCaptureContractTest` 6 个用例覆盖 spec §7 的 7 条验收（重算忽略客户端 `speed` 一条同时覆盖「提交体无 speed/validTime」与「码率由 captureIntervals 重算」）：`:125` 重算、`:144` 冻结满分、`:169` finish 后原始行仍在、`:199` 越界/乱序被拒且不覆盖已存页、`:221` 并发 finish 只结算一次且无幻影帧、`:278` 越权 207
- **提交**：`feat(pat): 组训数据报按服务端采集记录评分`（跨栈同提交）

### T4-2 前端 `datagramZuXun` 同步修复

- [x] `.value` 修正（两域 `student.vue` 的快照构造已收敛为单一 `saveTrainSnapshot()`）
- [x] `handlerSubmit` 加 `.catch`（datagram `:204` / telex `:207`）、`switchTelegram` 加 `await`（datagram `:140,154` / telex `:122,134`）
- [x] `readyTrainPat` 加 `if (saved)` 守卫与 `pageTime` 恢复（datagram `:250-252` / telex `:233`）
- [x] `onbeforeunload` 改 `addEventListener` + 卸载移除，**两域都改**（datagram `student.vue:263,266` / telex `:260,263`）
- [~] 未合并两域为共享实现；理由在提交 `9704267` 正文：两棵子树已实质漂移（datagram 版另有 `useMorse`/`keyCode`、`endTrain`、`resetTrainInfo` 与不同模板插槽），合并需同时统一 WS 处理与报文解析，超出本缺陷面；采集/上传线的去重由 T4-1 的 `useTrainingCapture` 承担
- [x] 运行证据：`npm run build` 通过；提交失败保留内容与恢复路径由 `test/terminalSubmission.test.mjs` 断言（Modal 为 error 且不清除已录入内容）
- **提交**：`fix(frontend): 组网数据报学生页同步提交失败与恢复修复`

### T5-3 发布校验

- [x] release job 断言 tag 与**三个**版本号一致（`.github/workflows/build-quarkus-native.yml:342-348` 传 `RELEASE_REF`；`scripts/artifact-manifest.cjs:245-256`）
- [x] 新增桌面 job：`needs:[build]`（`workflow:223`）、staging native 为 `bin/server/server`（`:264`）、`--dir` 不出 deb（`:283`）、产 manifest + SHA（`:291`、`artifact-manifest.cjs:166-186`）
- [x] `verifyRelease` 的 `expectedDirectories` 加 `desktop-linux`（`artifact-manifest.cjs:223`）+ 对应 `verifyManifest` 分支（`:110,129-131,228`）
- [x] 桌面 job 只在 tag 触发（`workflow:221`）
- [x] 证据：`bw-frontend/scripts/test/artifact-manifest.test.mjs:173-193` tag/三版本不一致被拒、`:195` 非 `refs/tags/v` 被拒、`:201-207` 缺 `desktop-linux` 被拒；frontend job `:62` 跑该 fixture 测试
- **提交**：`ci: 发布校验断言三方版本一致并纳入桌面产物`

### T7-8 终态业务码

- [x] 后端引入终态可机读标记：`ResponseCode.CODE_208:26` + `TerminalStateException` + `TerminalStateExceptionMapper:24`（→ HTTP 200 + `code:208`）；203/204/206 码值文案未动（`:18-20`）
- [x] 前端 `common/http/terminalCode.js` 定义终态码表（207/208 终态，202/500 非终态，只认数字码，拒因优先后端文案）；`useConfirmedSubmission.js:53-67` 终态落定即清队列、停重试、保留快照并 `Modal.error`，非终态 500/网络仍 `Modal.confirm` 可重试
- [x] 运行证据：`test/terminalSubmission.test.mjs` 四条断言该分界（终态只认数字 code、拒因文案优先、终态不可重试且不清内容、500/网络仍可重试）
- **提交**：`feat(contract): 区分终态与瞬态业务错误并停止引导无效重试`（跨栈同提交）

## 7. W5：收口

- [x] `cd backend && ./mvnw -B clean verify` 全绿（§6.2 收口时 391 测试 / 93 suite；§6.3 补做后 **392 / 93**）
- [x] `cd bw-frontend/frontend && npm run test && npm run build`（19/19 + build 成功）
- [x] `cd backend && ./scripts/rehearse-migrations.sh` 双快照通过（首跑曾因 MySQL 容器就绪竞态失败一次，重跑全绿）
- [x] Spec §11.2 静态门禁逐条实测：`@JWT` 覆盖 100%、`AESUtil` 生产引用 0（类已删）、拦截器 `getParam` 0、`UserSyncDto` 不含三字段、非管理员 VO 无 `idCard`/`phone`、`nip.db` 无业务 IP/COM 口、`StartWebSocket`/`electronSerial.js`/`teacherBack.js` 零残留；**前端 `res.msg` = 0 当时未达成，本轮补齐后为 0**
- [x] Spec §11.3 运行态证据逐批留存（见评审 §6.2「执行后基线」与本文 T3-5/T1-4/T5-2 条目）
- [x] 中间提交非坏态（受影响单测类逐批跑过）
- [x] 推送；评审文档新增 §6.2 执行记录
- [x] `docs/README.md` 增加本 Spec/Plan 入口 + 发布 runbook 入口
- [x] 修正 `AGENTS.md` 测试基线（316/74 → 当前值）并同步 4 处已过期的红线断言

### 最终基线（2026-09-12 收口）

- 条目状态：**143 条复选框全部有终态** —— 134 条 `[x]`（有 path:line 或运行证据）、5 条 `[~]`（偏离形态交付，理由就地写明）、4 条 `[!]`（核验时发现未达成，已在本轮补做，提交 `23cafc9..f9f97bd`）。
- 后端 `./mvnw -B clean verify`：**392 测试 / 93 suite，0 失败 0 错误 0 跳过**。
- 前端 `npm run test` **19/19**、`npm run build` 成功。
- 迁移演练 `./scripts/rehearse-migrations.sh` 双快照全绿。
- 运行态证据（真实 `%prod` 产物 + 真实 `--dir` 桌面包 + 真实 WS 客户端）汇总见评审 §6.2「执行后基线」与 §6.3。
- 核验方法与逐条结论见评审 [`§6.3`](../reviews/2026-09-12-full-project-review.md)：不照提交信息勾选，只认当前源码与运行结果。

## 8. 风险与回滚

| 风险 | 触发 | 处置 |
|---|---|---|
| T3-1 使全员需重新登录 | 发布即触发 | runbook 通告；revert 该提交即恢复（旧 AES token 仍在 DB） |
| T3-0/T2-3 把 202 改成 207 | 前端可能按 202 分支处理属主拒绝 | grep 前端对 202 的分支；如有依赖则同提交处置 |
| T3-5 握手鉴权打断某个未发现的连接点 | 上线后某页 WS 不通 | 注入点集中在 `SocketConnection.connect()`，revert 单文件即恢复；上线前逐页真实验证 |
| T4-1 迁移与旧前端不兼容 | 前后端版本错配 | 两侧同提交 + 旧协议按 attempt/protocolVersion 栅栏**拒绝**（不静默降级） |
| T5-2 `contextIsolation` 打断某处 ipc | 真实包功能缺失 | 单提交可回滚；白名单已按 5 处 `sendSync` + 1 处 `on` 实测确定 |
| T1-4 绑本机砍掉跨机资源服务 | 用户填了远端「资源服务地址」 | 监听地址**可配置**，默认本机；正文写明改法 |
| 并行波次同文件冲突 | 所有权表被越界 | §1.3 方法级独占；越界任务由我串行接管 |
