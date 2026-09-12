# 2026-09-12 评审整改实施计划（Plan）

> 对应 Spec：[`../specs/2026-09-12-review-fix-spec.md`](../specs/2026-09-12-review-fix-spec.md)（已按 5 路可行性评审修订，见其 §0）。权威依据：[`../reviews/2026-09-12-full-project-review.md`](../reviews/2026-09-12-full-project-review.md)。
>
> 本文是**执行中计划**：仅 `[x]` 且有证据的条目表示完成。
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

- [ ] 新增 `common/exception/ForbiddenException.java` + Mapper，映射为 **HTTP 200 + `code:207`**（照 `RequireAdminInterceptor.java:42-45` 的既有形态）
- [ ] `PostTelegramTrainService.owned:948`、`PostTelegraphKeyPatTrainService.owned:659`、`PostTelexPatTrainService.requireOwnedTrain:647` 改抛 `ForbiddenException` 并**删除旧名**（红线 6）
- [ ] 回归测试：非属主操作这三域得 **207**（此前是 202）
- [ ] 已有断言 202 的用例同提交改为 207（若存在）
- **提交**：`refactor(security): 统一授权拒绝为 207 并收敛属主判定原语`

## 3. W1

### T1-2 四个 controller 补类级 `@JWT`

- [ ] 四类加 `@JWT`（`PostTrainGlobalRule`、`PostTickerTapeTrainSetting`、`TelegraphKeyPatTrainSynthetical`、`CableFloor`）
- [ ] 规则类写端点加 `@RequireAdmin`
- [ ] grep 前端调用面确认均在登录后调用（结果入正文）
- [ ] 规则页 UI：恢复被注释掉的 `v-per` 守卫（6 处）或给 207 可见解释
- [ ] 架构测试：`controller/` 非 `free/` 包全部带 `@JWT`
- [ ] 回归：无 token 得 203；学员调规则写端点得 207
- **提交**：`fix(security): 四个遗漏鉴权的控制器补类级 JWT 并加架构守卫`

### T1-3 上分端点授权

- [ ] `teacherUploadScore` 加 `@RequireAdmin`（库中无教员角色，唯一选项）
- [ ] 校验 `list` 中考生属于该 `examId`
- [ ] 副作用写入提交正文：需上分的教员须具系统管理员角色
- [ ] 回归：普通人员 207；跨考试考生被拒
- **提交**：`fix(security): 教师上分端点补管理员授权与考生归属校验`

### T1-4 桌面文件服务收紧

- [ ] 根取**子进程 ctx** 的 `appPath`（`<安装目录>/bin/file`），代码注释写明「不是主进程 appPath」
- [ ] 读路径 `path.resolve` + `startsWith(ROOT+sep)` + 对存在文件 `realpathSync` 二次校验，越界 404
- [ ] 写路径 `currentPath` 逐段剥 `..`/绝对段；`mkdir`/`rename` 失败不得返回成功
- [ ] `node_server` 构造参数加 `host`（默认 `127.0.0.1`）并 `app.listen(port, host)`
- [ ] **ACAO 不动**，正文写明判断依据
- [ ] 运行证据（在 `--dir` 产物上）：正常可读、穿越 404、`ss -ltnp` 仅本机
- **提交**：`fix(desktop): 内嵌文件服务约束在资源根内并默认只监听本机`

### T5-1 发布态状态归一

- [ ] `nip.db` 的 `_id:2` → localhost、`_id:3` → 不预置
- [ ] **不**把 `nip.db` 纳入 CI 脏检查（运行时可写，会误报并触发 `sourceDirty` 拒发）
- [ ] 证据：`git show HEAD:bw-frontend/bin/nip.db` 无业务 IP/COM 口；`--dir` 冷启动指向本机
- **提交**：`fix(desktop): 随包配置归一为发布态默认`

### T5-5 死配置与硬编码地址

- [ ] 删 `bin/config.json` 的 `server`/`serial` 字段，**保留文件本身**（缺失会让 `context.db` 变 `{}` 致 ipc 全挂）
- [ ] 清理四处硬编码地址（含两处模块顶层建 WebSocket 的 demo 页）
- [ ] 证据：全仓 grep 无 `10.0.0.217`/`10.10.0.210`/`10.10.0.117`
- **提交**：`chore(desktop): 清理死配置与硬编码地址`

### T7-1 电传倒计时索引

- [ ] 新增幂等索引迁移 + 接入 `MIGRATIONS`
- [ ] 证据：`EXPLAIN` 走索引
- **提交**：`perf(db): 电传倒计时扫描补支撑索引`

### T7-2 启动结算扫描兜底

- [ ] 两处 `closingTrainIds()` 扫描加 `try/catch`
- [ ] 测试：扫描抛异常时 `recover()` 不外抛
- **提交**：`fix(settlement): 启动结算恢复容忍瞬时扫描失败`

### T7-6 数据迁移拆分

- [ ] 菜单 UPDATE 从时钟 DDL 迁移中拆出为独立脚本 + 接入 `MIGRATIONS`
- [ ] 迁移清单标注其前端契约依赖
- **提交**：`chore(db): 拆分菜单数据迁移与 schema DDL`

## 4. W2

### T1-1 训练同步端点不回传凭据

- [ ] `UserSyncDto` 删 `password`/`token`/`deviceId`
- [ ] `getTrainInfo` 加 `@RestHeader(TOKEN)`；抽谓词 `exportable(trainId, actorId)`（创建者 ∪ role=1 ∪ 管理员）
- [ ] `getTrainInfoBatch` 用谓词 **filter**（学员得空列表，不抛）
- [ ] grep 前端是否消费三个字段（入正文）
- [ ] 回归：非授权 207；学员 batch 得空列表；响应 JSON 不含三键
- **提交**：`fix(security): 训练同步端点不再回传会话凭据并加导出归属判定`

### T2-1 规则与主数据写端点授权

- [ ] `GradingRuleController` 4 个 + `DeviceScoringRuleController` 2 个 + 题库写端点加 `@RequireAdmin`
- [ ] 读端点不加
- [ ] 回归：逐端点 学员 207 / 管理员成功；读端点学员可用
- **提交**：`fix(security): 评分规则与主数据写端点补管理员授权`

### T2-2 理论考试与统计端点身份收口

- [ ] `studentChangeExamState`、`studentSaveExamRealtimeContont` 改 token 推导 + 删 body `userId`
- [ ] `findExamUser` **保留 `userId`** + 授权判定（本人／该场 teacher/createUser／管理员）
- [ ] `finishSelfTesting` **服务端按试卷快照重算**（无退路）
- [ ] 3 个统计端点移出 free 包、加 `@JWT`、token 推导、**路径不变**
- [ ] `cors.origins` **不动**
- [ ] 前端对应 api 与调用点同提交
- [ ] 回归：A 改不了 B；任意 `score` 不影响入库；教员仍能阅卷；匿名统计得 203
- **提交**：`fix(security): 理论考试与训练统计端点按token收口身份`（跨栈同提交）

### T2-4 用户目录端点与注入

- [ ] 全量目录能力收敛到唯一入口 + `@RequireAdmin`
- [ ] 新增 `UserDirectoryEntry`（不含 `idCard`/`phone`）供非管理员路径
- [ ] `findAllUser` 的 `REGEXP` 拼接改参数化 `id in (?1)`，空列表返空集
- [ ] grep 前端是否消费 `idCard`/`phone`，同提交处置
- [ ] 回归：学员得 207 或得到脱敏结果；`REGEXP` 元字符不再影响匹配
- **提交**：`fix(security): 用户目录端点收敛授权并参数化查询`（跨栈同提交）

### T5-2 渲染进程安全特性

- [ ] 新增 preload，白名单 `{invoke, send, sendSync, on, once, off}`（**必含 `sendSync`**）
- [ ] `on`/`once` 包装为 `(_event, ...args) => listener(null, ...args)`（保住 `(event, data)` 签名且不泄露真 event）
- [ ] **不暴露 `removeListener`**；删零引用死代码 `common/utils/electronSerial.js`
- [ ] `contextIsolation: true`、`nodeIntegration: false`
- [ ] 删 `main.js:9` 的 `--ignore-certificate-errors`
- [ ] `webSecurity` 保持 `false` 并就地注释原因
- [ ] 运行证据：`--dir` 包冷启动，登录/训练/**发音**/串口/网络设置/许可全通；渲染进程 `typeof require === 'undefined'`
- **提交**：`fix(desktop): 渲染进程启用上下文隔离并移除证书校验开关`

### T5-4 串口链路容错

- [ ] `_id:3` 缺失、端口缺失、非 linux 分支三处崩溃/错值修正
- [ ] 监听移到顶层只注册一次
- [ ] 取消列举时自动 `pkexec`；新增显式 `grantAccess` 路由 + try/catch + `filter(Boolean)`
- [ ] `NipSerial.vue` 对 `accessible===false` 显示「需要授权」按钮 + `usermod -aG dialout` 需重登提示
- [ ] 运行证据：首次安装打开串口页不崩；不存在端口有提示；列举不弹提权；取消提权不致主进程退出（直调 handler）
- **提交**：`fix(desktop): 串口链路容错并改为显式授权`（跨栈同提交）

### T7-7 通播教学页拒因文案

- [ ] `useBroadStudent`/`useBroadTeacher` 的 `res.msg`→`res.message`、`envelope.msg`→`envelope.data`
- [ ] 运行证据：真实页面可见 202/207 拒因
- **提交**：`fix(frontend): 通播教学页显示后端真实拒因`

### T7-9 死代码与热路径日志

- [ ] 删 `console.log`：`examTrain.js:191,247,386`、`handKeyTrain.js:209`、`datagramZuXun/.../student.vue:249`、`telexZuXun/.../score.vue:311`
- [ ] 删死墙钟 `handKeyTrain.js:239`
- [ ] 删 `teacherBack.js` ×4、datagram/telex 学生页未调用的 `useControl` 导入
- [ ] 证据：全仓 grep 无残留；构建通过
- **提交**：`chore(frontend): 清理热路径日志与死代码`

## 5. W3

### T2-3 写权限口径统一

- [ ] 新增唯一 helper `requireWritableTrain`（创建者 ∪ role=1 组训人 ∪ 管理员，抛 `ForbiddenException`）
- [ ] **迁移并删除** T3-0 已改造的三个旧 helper 的调用面，统一到新 helper
- [ ] 各域 `delete`/`updateTrainStatus` 接入（属主字段名三种，显式传入，不反射）
- [ ] `generalTelexPat` 域**不在本任务**（归 T4-1）
- [ ] 回归：非授权删他人训练 207 且 DB 行仍在；**非创建者的 role=1 组训人仍能改状态**
- **提交**：`refactor(security): 训练写权限统一为创建者与组训人与管理员`

### T3-1 token 随机化与哈希存储

- [ ] 签发 `SecureRandom` 不透明串，明文只回 `LoginSessionDto.token`
- [ ] 存 SHA-256 hex；`existsUserByTokenAndDeviceId`/`findUserEntityByToken` 先哈希再查
- [ ] **四处写面**：`login`、`userOut`、`importUser`、`replaceUserIdAndSaveIfNotExist`（后两处改为服务端决定=NULL）
- [ ] `lsp references` 覆盖 `findUserEntityByToken` 24 处调用点
- [ ] **测试面**：`Fixtures` 改「入库写哈希、返回明文」唯一工厂；禁止用例自行 `setToken`（5 处）；修正把 `entity.getToken()` 当凭据的测试类（约 8 个）
- [ ] `AESUtil` 零调用则整类删除
- [ ] runbook 写明「存量会话全部失效，需全员重新登录」
- [ ] 回归：两次登录 token 不同；DB 值≠响应值；DB 哈希当 token 得 206；旧 AES token 得 206；`userOut` 后 206
- **提交**：`fix(security): token 改不透明随机串并按哈希校验`

### T3-3 拦截器收口

- [ ] 两个拦截器删 query 回退
- [ ] 兜底分支改固定 `SYSTEM_ERROR` 文案，细节只进日志
- [ ] 回归：仅 query token 得 203；鉴权期异常不泄 SQL
- **提交**：`fix(security): 鉴权只接受请求头并停止回传原始异常`

### T3-4 训练导入授权与白名单

- [ ] 两个导入端点加 `@RequireAdmin`
- [ ] 只允许 `userAccount`/`userName`/`userImg`；`password`/`token`/`deviceId`/`status` 服务端决定（`password` 留 NULL）
- [ ] 顺带修 `replaceUserIdAndSaveIfNotExist` 的导入失效缺陷（正文说明表现）
- [ ] 回归：学员 207；管理员导入后 `password`/`token`/`deviceId` 均 NULL 且该账号无法直接登录
- **提交**：`fix(security): 训练导入端点补授权与用户字段白名单`

### T3-5 WebSocket 握手鉴权

- [ ] **6 个**带身份语义端点校验 query `token`+`deviceId` 并以校验身份覆盖路径 `uid`
- [ ] 删除零引用 `StartWebSocket`（整类）；`/status` 保持匿名
- [ ] 仿真：**无 `roomUser` 行且不是 `createUserId`** 才拒绝；复用 `SimulationRoomAccess` 的 teacher 判定；建房人合成成员保持 `userType=null`/`channel=-1`
- [ ] `role` 比对只保留在 `/generalTickerPat`
- [ ] 前端注入点 `common/ws/SocketConnection.js` 的 `connect()`（覆盖 7 处直连 + `PublicSocket` + 重连）；`MessageWebSocket` 排除
- [ ] **测试面**：`WebSocketSessionProbe` 加 `getRequestParameterMap`；WS 测试 URI（约 9 处）带凭据
- [ ] 回归：无凭据被关；A 凭据连 B 的 uid 按 A 注册；**路由房建房人仍可入房广播**
- [ ] 运行证据：真实训练页 WS 不回归
- **提交**：`fix(security): WebSocket 握手校验凭据并以校验身份覆盖路径参数`（跨栈同提交）

### T3-6 生产凭据外置与侦察面

- [x] `%prod` 改 `${DB_USER}`/`${DB_PASSWORD}`（**不写默认值**）；`%dev` 保留字面值
- [x] ~~新增 `%prod` 启动校验~~ **撤销**：JPA 引导早于 `StartupEvent` 观察者，守卫是死代码；前移到 SmallRye 配置拦截器会挡掉 `mvn package`。改为在 `%prod` 配置注释里写明必须注入的变量（见 spec §6 T3-6 目标 2 的撤销理由）
- [x] `/api/tools/system` **整端点删除**（前端零消费）
- [x] 回归（打包产物实测）：缺变量 → 启动失败、不监听 HTTP；注入 `DB_USER=root DB_PASSWORD=root` → `started in 3.188s`、`validate` 通过、`/q/openapi`=200、登录信封仍为 `200 + code:500`；该端点 404
- **提交**：`fix(security): 生产数据源凭据外置并删除系统侦察端点`

### T7-3 鉴权路径空返回收敛

- [ ] `findUserEntityByToken` 的 24 处调用点改走 `UserService.getUserByToken`
- [ ] 其余裸 `firstResult()` 登记后续批次（不盲改）
- [ ] 回归：抽样端点 token 失效返回 203 而非 500
- **提交**：`fix(auth): 鉴权路径不再裸解引用 DAO 空返回`

### T7-5 收尾扫描投影

- [ ] 两个 `closingTrainIds()` 改 `select id` 投影（只改方法体）
- [ ] 证据：扫描 SQL 不再 select 全实体
- **提交**：`perf(settlement): 收尾扫描改主键投影`

## 6. W4

### T4-1 组训数据报域纳入采集契约

- [ ] **范本**：页内采集与重算照 `PostTelexPatTrainService`；数据分层与房间/role 维度照 general 域（**不照抄 GeneralTickerPat 的点划 symbolMillis 对账**）
- [ ] DTO 加 `protocolVersion`/`attempt`/`captureIntervals`（严格数字解析），删 `speed`/`validTime`
- [ ] `saveContentValue` 按 `CaptureTimeline` 校验并服务端重算逐页用时/码率
- [ ] `countScore` 基准分改冻结规则满分（去掉 `:753` 硬编码 100）
- [ ] **原始行分层**：删除改为 `sort > -1`，`pageValueResult` 不再 `addAll(userValue)`，原始行（带 `attempt`/`capture_intervals`/`received_at`）原地保留
- [ ] `finish`/`saveContentValue` 行锁 + attempt 栅栏 + `isFinish` 幂等
- [ ] WS 通知改 `AFTER_SUCCESS`（复用 `GeneralPatResultNotifier`）
- [ ] controller 端点加 `@RestHeader(TOKEN)` + `requireWritableTrain`/参训判定（`:78`/`:94`/`:101`/`:137` 及 `:57`/`:64`/`:71`/`:108`/`:115`/`:122` 按读写性质）
- [ ] 采集边界用成员行 `capture_started_at` → 训练 `end_time`；**不加时钟列**
- [ ] 收尾扫描与 `(status,end_time)` 索引对齐
- [ ] 新增 `2026-09-12-01-general-telex-capture.sql`（幂等）+ 接入 `MIGRATIONS` + **重新生成 `entity-schema.tsv`** + 双快照通过
- [ ] 前端 11 个消费文件：先产出完整清单，再改上传契约（复用 `useTrainingCapture`），两侧同提交
- [ ] 回归（七条，见 Spec §7 T4-1 验收，含「`finish` 后原始采集行仍在」）
- **提交**：`feat(pat): 组训数据报按服务端采集记录评分`（跨栈同提交）

### T4-2 前端 `datagramZuXun` 同步修复

- [ ] `.value` 修正（`student.vue:252,255`）
- [ ] `handlerSubmit` 加 `.catch`、`switchTelegram` 加 `await`
- [ ] `readyTrainPat` 加 `if (saved)` 守卫与 `pageTime` 恢复
- [ ] `onbeforeunload` 改 `addEventListener` + 卸载移除 —— **兄弟也没修，需新写并同时修兄弟**
- [ ] 优先合并两域为共享实现；不合并需正文说明
- [ ] 运行证据：上传失败保留内容 + 可见提示；刷新恢复正确；离页无残留监听
- **提交**：`fix(frontend): 组网数据报学生页同步提交失败与恢复修复`

### T5-3 发布校验

- [ ] release job 断言 tag 与**三个**版本号（`backend/pom.xml`、`frontend/package.json`、`bw-frontend/package.json`）一致
- [ ] 新增桌面 job：`needs: [build]`，staging native 为 `bin/server/server`，`electron-builder --linux --dir`（不出 deb），产 manifest + SHA
- [ ] `verifyRelease` 的 `expectedDirectories`（`:152-153`）加 `desktop-linux` + 对应 `verifyManifest` 分支
- [ ] 桌面 job 只在 tag 触发
- [ ] 证据：新写 Git fixture 验证 tag/版本不一致被拒；workflow 静态检查通过
- **提交**：`ci: 发布校验断言三方版本一致并纳入桌面产物`

### T7-8 终态业务码

- [ ] 后端引入终态可机读标记（新增码或 error 分支细分，**不动 203/204/206**）
- [ ] 前端 `useConfirmedSubmission` 对终态码提示不可重试并保留数据；网络/500 仍可重试
- [ ] 运行证据：训练已完成时提交显示不可重试
- **提交**：`feat(contract): 区分终态与瞬态业务错误并停止引导无效重试`（跨栈同提交）

## 7. W5：收口

- [ ] `cd backend && ./mvnw -B clean verify` 全绿且 `>= 317`
- [ ] `cd bw-frontend/frontend && npm run test && npm run build`
- [ ] `cd backend && ./scripts/rehearse-migrations.sh` 双快照通过
- [ ] Spec §11.2 静态门禁逐条实测
- [ ] Spec §11.3 运行态证据逐批留存
- [ ] 中间提交非坏态（worktree 抽检）
- [ ] 推送；评审文档新增 §6.2 执行记录
- [ ] `docs/README.md` 增加本 Spec/Plan 入口
- [ ] **修正 `AGENTS.md:29`**：仍写 316/74，实际 317/75（B6 的 `94f48b4` 写入了过期数字）

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
