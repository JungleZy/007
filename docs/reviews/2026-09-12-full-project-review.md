# 全项目评审（2026-09-12 基线）

- **评审日期**：2026-09-12
- **评审对象**：`backend/`（Quarkus 3.20.4 / Java 21，770 个主源文件、61 个 controller、80 个 service）+ `bw-frontend/`（Electron 壳）+ `bw-frontend/frontend/`（Vue，262 个 `.vue`、293 个 `.js`、28 个 api 模块）
- **代码基线**：`main` = `9f70c22`；本轮客户报障整改共 16 个提交（`4819227..9f70c22`），已推送 origin
- **验证基线**：`cd backend && ./mvnw -B clean verify` → **316 测试 / 74 suite，0 失败 0 错误 0 跳过**（2026-09-12，2 分 36 秒）；前端 `npm run build` 成功
- **评审方式**：8 路并行只读评审队（评分采集 / 数据与迁移 / 安全授权 / 并发与 WS / 跨栈契约 / 前端 / 交付形态 / 测试与文档），逐条要求根相对 `path:line` 取证；全部 P0/P1 由主评审独立复核，复核结论与纠正记录见 §9
- **本文定位**：**替代 `docs/reviews/2026-09-08-full-project-review.md` 成为当前唯一全项目评审入口**。2026-09-08 评审降为历史证据（其 216 测试基线等数字已过期）
- **关联文档**：客户报障分析 `docs/reviews/2026-09-10-customer-issue-analysis.md`；本轮规格 `docs/specs/2026-09-10-customer-issue-fix-spec.md`；本轮计划 `docs/plans/2026-09-10-customer-issue-fix-plan.md`（T17 现场交付仍未完成）

## 0. 总体结论

**本轮客户报障整改在其覆盖的六个训练域内是扎实的**：码率/用时已全部改为服务端从原始采集区间重算、`dash.max` 封顶修正、attempt 栅栏跨路径生效、结算走行锁 + 幂等 + 事务后通知、11 个迁移脚本双快照演练通过、WS 会话态实例字段残留为 0。§5 列出 80 项已核实正确的关键点，避免重复排查。

**但整改边界之外存在四类可直接触发的严重缺陷**，其中安全面最重：

| 严重度 | 数量 | 判据 |
|---|---|---|
| **P0** | 4 | 凭据泄露/匿名写入/成绩篡改/局域网任意文件读写，均已确认可触发 |
| **P1** | 11 | 成绩可被操纵、越权读写他人数据、交付即不可用 |
| P2 | 24 | 健壮性、可维护性、测试与文档一致性 |
| P3 | 9 | 整洁度 |

共 48 条。六个根因簇：

| 簇 | 覆盖 | 本质 |
|---|---|---|
| **R1 授权层缺失**（红线 6 未闭环） | SEC-01/02/03/05/06/07/08/09/10、SCORE-02 | `@RequireAdmin` 只覆盖 6 个 controller，其余写端点仅有类级 `@JWT`；大量端点按请求体 `userId` 定位记录 |
| **R2 凭据设计** | SEC-01/04/05/12 | token = AES/ECB(账号-**明文口令**-deviceId)，硬编码密钥、永不过期、可反解；一处泄露即全链失陷 |
| **R3 整改域边界漏一域** | SCORE-01/02/03、CONTRACT-01、FE-02 | 组训数据报/电传域 `generalTelexPat` 未纳入本轮迁移，仍信任客户端 `speed`/`validTime`，满分硬编码 100 |
| **R4 桌面交付形态** | DELIVERY-01/02/03/05/06 | 内嵌无鉴权文件服务 + 渲染进程安全特性全关 + 随包地址钉死开发机 + 桌面包不进 CI |
| R5 DAO 空返回与索引 | DATA-01/03、CONC-01 | 裸 `firstResult()` 55 处未收敛；电传倒计时扫描缺索引 |
| R6 测试与文档漂移 | TESTDOC-01…12 | `%test` 未禁调度器致评分门禁竞态；AGENTS.md 四处事实性断言已过期 |

**结论：不建议在 R1/R2/R4 处置前进行客户现场交付。** 本轮功能整改质量不构成阻塞项，阻塞项是授权与交付面。

---

## 1. P0（4 条，全部已独立复核）

### P0-01 训练同步端点回传他人 token + deviceId + 口令哈希（SEC-01）

```
GET /api/generalKeyPat/getTrainInfo?trainId=N     ← trainId 为自增 Integer，可枚举
GET /api/generalKeyPat/getTrainInfoBatch
```

- `backend/src/main/java/com/nip/service/general/GeneralKeyPatService.java:1062,1068`：`userDao.queryByIdIn(userIds)` 取出 `UserEntity` 后 `PojoUtils.convert(userEntities, UserSyncDto.class)` 按字段名整体拷贝。
- `backend/src/main/java/com/nip/dto/general/UserSyncDto.java:46,50,54`：该 DTO 含 `password`、`token`、`deviceId` 三个字段 —— 逐字段核实，确认被填充并随响应返回。
- `backend/src/main/java/com/nip/common/interceptor/JWTInterceptor.java:67-69`：全部鉴权即 `existsUserByTokenAndDeviceId(token, deviceId)`。

**触发前提**：任意最低权限已登录账号（学员即可）。**后果**：枚举 `trainId` 即可收割参训者与训练创建者的活动会话凭据；组训创建者通常是教员/管理员，因此本轮新增的 `@RequireAdmin`/`code:207` 门禁被完整绕过（可删用户、重置口令、改角色菜单）。叠加 P0-02 的 token 可反解，进一步得到对方**明文登录口令**。

**处置**：同步导出改用脱敏 DTO（仅 `id/userAccount/userName/userImg`），或 `PojoUtils.convert(..., "password","token","deviceId")` 显式忽略；同时对两个端点加参训归属或管理员判定。

### P0-02 4 个非 free 包 controller 无任何 `@JWT`：匿名可写评分规则并结算成绩（SEC-02）

主评审独立扫描 61 个 controller 的类级注解，确认 `controller/free/**` 之外**恰好 4 个**完全没有 `@JWT`：

| controller | `@Path` |
|---|---|
| `backend/src/main/java/com/nip/controller/PostTrainGlobalRuleController.java` | `/postTrainGlobalRule` |
| `backend/src/main/java/com/nip/controller/PostTickerTapeTrainSettingController.java` | `/postTickerTapeTrainSetting` |
| `backend/src/main/java/com/nip/controller/TelegraphKeyPatTrainSyntheticalController.java` | `/telegraphKeyPatTrainSynthetical` |
| `backend/src/main/java/com/nip/controller/CableFloorController.java` | `/cable/floor` |

**后果**：无需任何凭据即可改写全局训练规则与码速配置（影响全体学员成绩基准），并通过综合电子键端点用客户端数据结算训练成绩。

**处置**：四个 controller 加类级 `@JWT`，写端点按 R1 统一加授权；建议补一条架构测试断言「`controller/` 下非 `free/` 包的类必须带 `@JWT`」，防回归。

### P0-03 理论考试教师上分端点无角色校验（SEC-03）

- `backend/src/main/java/com/nip/controller/TheoryKnowledgeExamUserController.java:79-83`：`POST /theoryKnowledgeExamUser/teacherUploadScore` 直接接收 `Map<String,Object>` 的 `examId` + `list`。
- 主评审核实：该文件**全文无 `@RequireAdmin`**，仅有类级 `@JWT`。

**触发前提**：任意已登录学员。**后果**：把任意考生、任意场考试改成任意分数。

**处置**：加 `@RequireAdmin`（或教员角色判定），并校验被改考生属于该场考试。

### P0-04 桌面壳内嵌文件服务：无鉴权 + 目录穿越 + 任意路径写，绑定全网卡（DELIVERY-02）

> 评审队原判 P1，主评审**上调为 P0**：无需任何凭据、局域网可达、可写任意路径（等价远程代码执行前置）。

- `bw-frontend/electron/service/http/controllers/file.js:12`：`path.join(context.appPath, req.params['0'])` 后 `sendFile`，**无根目录约束** → `GET /api/file/getReadStream/../../../etc/passwd` 可读任意进程可读文件（`:19` 同样问题）。
- `bw-frontend/electron/service/http/controllers/file.js:54-73`：`currentPath` 取自 query 或 header，仅 `.replace(/^[\\\/]+|[\\\/]+$/, '')`（**无 `g` 标志、不剥内部 `..`**），随后作为 `formidable` 的 `uploadDir` → 可写到 `appPath` 之外。
- `bw-frontend/electron/service/http/node_server.js:34`：`app.listen(this.port)` **未指定 host** → 绑定 0.0.0.0；`:22` `Access-Control-Allow-Origin` 取自配置的 `*`（`bw-frontend/bin/nip.db` `_id:1`）；`:31` 全路由挂载，**无任何鉴权中间件**。

**处置**：解析后路径 `realpath` 归一并强制 `startsWith(appPath)`；`currentPath` 去除所有 `..` 段；`app.listen(port, '127.0.0.1')`；收紧 CORS。

---

## 2. P1（11 条，全部已独立复核）

### 2.1 凭据与授权

| ID | 问题 | 关键证据 |
|---|---|---|
| **SEC-04** | token = `AES/ECB(账号-明文口令-deviceId)`，密钥硬编码、**永不过期** | `backend/src/main/java/com/nip/service/UserService.java:452`；`backend/src/main/java/com/nip/common/utils/AESUtil.java:20,30` |
| **SEC-05** | `importTrainInfo(Batch)` 无授权，直接把客户端 `UserSyncDto`（含 token/deviceId/password/status）落库建号 | `backend/src/main/java/com/nip/controller/general/GeneralKeyPatController.java:152-173`；`GeneralKeyPatService.java:1073-1095` |
| **SEC-06** | 全部 **8** 个 WebSocket 端点握手零鉴权；仿真端点把无房间成员行的连接合成为「组训人员」 | `backend/src/main/java/com/nip/ws/WebSocketService.java:32-60`；`WebSocketSimulationService.java:70-72` |
| **SEC-07** | `@RequireAdmin` 仅覆盖 **6** 个 controller；评分规则与主数据写端点仍只有 `@JWT` | `backend/src/main/java/com/nip/controller/GradingRuleController.java:51-77`；`DeviceScoringRuleController.java:39-53` |
| **SEC-08** | `getAllUser` 的门禁被 5 个同类端点旁路，全量用户 PII（身份证号）对任意学员开放；`findAllUser` 存在 REGEXP 注入 | `backend/src/main/java/com/nip/controller/UserController.java:97-132`；`UserService.java:593-601`；`dao/UserDao.java:93-107` |
| **SEC-09** | 理论考试写端点按请求体 `userId` 定位记录：可改他人答卷、提前锁死他人考试；自测结算采信客户端分数 | `backend/src/main/java/com/nip/controller/TheoryKnowledgeExamController.java:102-119`；`service/TheoryKnowledgeExamService.java:178-204` |

`SEC-04` 说明：`PasswordHasher` 的 600k 轮 PBKDF2 因此完全失去意义 —— token 是口令明文的可逆密文，任何一次泄露（P0-01 的响应体、query string 形式的 token 进访问日志、数据库导出）都可 `AESUtil.decrypt` 还原 `账号-明文口令-deviceId`。**这是 R2 簇的根**，也是 P0-01 危害被放大的原因。

`@RequireAdmin` 现覆盖：`UserController`、`RoleController`、`MenusController`、`DeviceController`、`CableController`、`CableTypeController`。**未覆盖的写端点**包括 `/gradingRule/{save,update…Status,change…IsDefault,delete}`、`/deviceScoringRule/{save,delete}`、理论题库写端点、各训练域 `delete`/`updateStatus`。

### 2.2 组训数据报/电传域整体未迁移（R3）

三路评审独立收敛到同一根因，主评审逐条复核确认：

| ID | 问题 | 证据 |
|---|---|---|
| **SCORE-01 / CONTRACT-01** | `countScore` 的码率分取自**客户端上报** `speed`；用时取自客户端 `validTime`；满分**硬编码 100**，忽略冻结规则满分 | `backend/src/main/java/com/nip/service/general/GeneralTelexPatService.java:405-418`（写 `speedLog`）、`:753`（`new BigDecimal(100)`）、`:814-820`（`avgSpeed` → `ScoreMath.wpmScore` → 计入总分）、`:828-836`（累加客户端 `validTime`）；DTO `backend/src/main/java/com/nip/dto/general/GeneralTelexPatPageSubmitDto.java:30-34` 无 `attempt`/`captureIntervals`/`protocolVersion` |
| **SCORE-02** | `finish` / `updateTrainStatus` / `delete` / `getPage` 无 token 主体，身份取自请求体 | `backend/src/main/java/com/nip/controller/general/GeneralTelexPatController.java:78,94,102,137`（对比同文件 `:43,50,86,129` 已带 `@RestHeader(TOKEN)`） |

**该域是活跃域**，主评审已核实前端两个学员页均在用：`bw-frontend/frontend/src/common/api/datagramZuXun.js:49,57`（`/api/generalTelexPat/uploadResult`、`/finish`）、`views/manage/organization/datagramZuXun/train/student/js/datagramTrain.js:35` 与 `telexZuXun/train/student/js/datagramTrain.js:32`（WS `/generalTelexPatTrain/...`），前端 `datagramTrain.js:179-181` 本地算出 `speed` 并上传。

**后果**：学员伪造一次 `uploadResult` 即可指定码率分；任意登录用户可结算他人成绩（`SCORE-02` + `SCORE-01` 组合）。**客户报障 #3（单位/用时/扣分）与 #8（组网评分）在该域仍未闭环**，`docs/specs/2026-09-10-customer-issue-fix-spec.md` 的 H2「码率不由客户端上报」不应标记为全域完成。

**处置二选一**：(a) 纳入同一采集契约（`begin`/`uploadResult` 带 `attempt`+`captureIntervals`+`protocolVersion`，服务端按 `CaptureTimeline` 重算，满分改用冻结规则满分，`finish`/`updateStatus` 加 token 主体与行锁）；(b) 若该域实际停用，则前端菜单与后端路由一并下线。**不能保持现状**。

### 2.3 桌面交付（R4）

| ID | 问题 | 证据 |
|---|---|---|
| **DELIVERY-01** | 随包 `bin/nip.db` 把桌面后端地址钉死在开发机 `10.10.0.117` | `git show HEAD:bw-frontend/bin/nip.db` 第 2 行 = `{"_id":2,"text":{"dataUrl":{"url":"10.10.0.117","port":"18001"},...}}`（主评审直接核实提交内容）；`bw-frontend/electron/core/index.js:29-46` 仅在 `_id:2` **缺失**时写 localhost 默认值，随包文件已含该行故默认值被跳过；`bw-frontend/package.json:33-35` `extraFiles: ["./bin"]` 原样随包 |
| **DELIVERY-03** | 渲染进程安全特性全部关闭 + 全局忽略证书错误 | `bw-frontend/electron/index.js:55-58`：`nodeIntegration: true`、`contextIsolation: false`、`webSecurity: false`；`bw-frontend/main.js:9`：`appendSwitch('--ignore-certificate-errors','true')` |

`DELIVERY-01` 后果：客户开箱后所有业务 API 发往开发机，本地 `localhost:18001` 后端被旁路 —— 该主机不可达则应用整体不可用；若恰好可达则数据发往错误主机。
`DELIVERY-03` 后果：系统内以富文本/`v-html` 渲染用户可编辑内容（如理论知识 `content`），任一注入点即等价宿主机任意代码执行；`--ignore-certificate-errors` 使 HTTPS 部署的 TLS 校验失效（可被 MITM），与 §8 记录的 G4「可信证书」门禁直接冲突。

---

## 3. P2（24 条）

> 以下为评审队取证结论，主评审对标注 ✔ 的条目做了独立复核；其余条目证据完整但未二次核实。

### 3.1 后端

| ID | 问题 | 证据 | 复核 |
|---|---|---|---|
| SCORE-03 | `GeneralTelexPat` 无行锁/无结束栅栏，且 WS 通知在事务提交前发出 → 双重结算与回滚幻影通知 | `service/general/GeneralTelexPatService.java:396-437,476-491` | ✔ |
| SEC-10 | `controller/free/` 下 3 个统计端点未鉴权且按请求体 `userId` 取数，叠加 `cors.origins: '*'` 可被任意网页匿名读取他人训练数据 | `controller/free/UserController.java:26-29,55-78`；`application.yml:18-22` | |
| SEC-11 | `JWTInterceptor` 兜底分支把原始异常消息回传客户端（鉴权前可达），可泄露 SQL/Hibernate 细节 | `common/interceptor/JWTInterceptor.java:70-76` | |
| CONC-01 | `GeneralSettlementRecovery.recover()` 未兜底 `closingTrainIds()` 扫描异常，而该方法被 `@Observes StartupEvent` 直接调用 → 瞬时 DB 错误升级为**启动中止**；兄弟类 `PostTelexPatTrainRecovery:31-45` 已正确兜底 | `service/general/GeneralSettlementRecovery.java:16-36` | |
| DATA-01 | 电传倒计时每 5 秒全表扫描 `t_post_telex_pat_train`，缺 `(status, deadline)` 索引；general 收尾路径已有 `(status,end_time)` 索引，两侧不对称 | `dao/PostTelexPatTrainDao.java:26-30`；`database/migrations/2026-09-11-03-post-telex-capture-clock.sql` 全程无 `CREATE INDEX` | |
| DATA-02 | 同日迁移前缀重复（两个 `-01`/`-03`/`-04`），且演练数组顺序 ≠ 文件名字典序 → 「演练通过」不等价于「运维按文件名顺序通过」 | `scripts/rehearse-migrations.sh:67-72` | |
| DATA-03 | 裸 `firstResult()` 返回 null 的红线 4 仍系统性残留：主评审实测 **55 处调用 / 42 个 DAO 文件**，含 `UserDao.findUserEntityByToken:75-81`（25 处调用、11 个 service） | `dao/UserDao.java:75-81` 等 | ✔ |

### 3.2 跨栈契约与前端

| ID | 问题 | 证据 | 复核 |
|---|---|---|---|
| CONTRACT-02 | `BroadcastTeachTrain` 读取后端不存在的 `res.msg`/`envelope.msg`（实际字段为 `message`，WS 文案在 `data`）→ 业务与 WS 拒因全部退化为通用兜底文案，削弱「失败可见可重试」 | `common/response/Response.java:17-23`；`ws/model/SimulationResponseModel.java:22-42`；前端 `components/BroadcastTeachTrain/js/useBroadStudent.js:45,147,241`（同仓 `postJob/telegram/handkey/js/telegram.js:375` 正确用 `res.message`） | |
| CONTRACT-03 | 提交 mixin 把终态业务码（202「训练已完成」、207「无权限」）当可重试，引导用户重试必然再失败的操作；两侧缺可机读的终态标记 | 前端 `common/mixin/useConfirmedSubmission.js:20-48`；后端 `exception/ValidationExceptionMapper.java:30-33` | |
| FE-02 | 组网数据报 `datagramZuXun` 学生页是 `telexZuXun` 兄弟的旧副本：恢复快照 `patPage`/`speed` 缺 `.value` 写成 `undefined`；`handlerSubmit` 无 `.catch` 且 `switchTelegram` 不 `await` → 网络失败静默翻页丢内容；`readyTrainPat` 对 null 快照解引用 TypeError；`onbeforeunload` 直接赋值且卸载不清除 | `views/manage/organization/datagramZuXun/train/student/student.vue:252,255,262-273`；同域 `js/datagramTrain.js:125-146,169-187,209-217`；对照 `telexZuXun/.../student.vue:249,252` | ✔ |

### 3.3 交付

| ID | 问题 | 证据 |
|---|---|---|
| DELIVERY-04 | release 不校验 git tag 与 `pom.version` 一致，产物名用 pom 版本 → 打 `v2.0.0` 而 pom 仍 `1.1.0` 会发布错名产物 | `bw-frontend/scripts/artifact-manifest.cjs:132-145`；`.github/workflows/build-quarkus-native.yml:194-198,248-256` |
| DELIVERY-05 | Electron 桌面安装包不进 CI：deb/nsis 无 manifest、无 SHA、无 commit 绑定，内含 `public/dist`、`bin/nip.db`、`extraFiles` 取自打包者本机状态（直接放大 DELIVERY-01） | `.github/workflows/build-quarkus-native.yml:42-92,207-213` |
| DELIVERY-06 | 串口选择器：目标端口缺失时 `callback(selectedPort.portId)` 抛 TypeError；`serial-port-added/removed` 监听每次选择都注册 → 泄漏；`getSerialPortList` 在用户不在 `dialout` 组时自动 `pkexec` 提权并 `chmod 666` | `bw-frontend/electron/serial/index.js:5-24`；`nativeSerialPort.js:60-64,90-101` |

### 3.4 测试与文档（R6）

| ID | 问题 | 证据 | 复核 |
|---|---|---|---|
| TESTDOC-01 | `%test` **未禁用调度器**：两个 5s 恢复任务在整个 `verify` 期间全库扫描并结算，范围不限于当前用例；`GeneralCaptureContractTest:532-534` 只能靠「过期与结算圈进同一父行锁」规避，`graceWindowFollowsRequestArrival…` 存在锁队列顺序决定结果的竞态 → **评分门禁本身非确定性** | `backend/src/main/resources/application.yml:78-89`（主评审确认全文无 `scheduler` 配置项） | ✔ |
| TESTDOC-02 | WS 全局 `static` 表被多个测试类无条件 `clear()`，`awaitEmpty` 断言全局表为空 → 套件依赖执行顺序、不可并行，与 `AGENTS.md:58`「可并行」约定直接冲突 | `ws/WebSocketUnionService.java:60-62`；`test/java/com/nip/ws/WebSocketUnionTest.java:251-254,324-330` | |
| TESTDOC-03 | `ScoringConsistencyTest.allThreeCallersAgreeOnRateSign` 名不副实：只调了 `GeneralTickerPatService` 与其委托的 `ScoreMath`，Key/Telex 两路从未执行，守的是委托这件实现事实 → 按测试约定应删除并改为三域真实结算后比对落库 `deductInfo` | `test/java/com/nip/service/ScoringConsistencyTest.java:109-128` | |
| TESTDOC-04 | `GeneralPatResultNotifier`（本轮 `6f16d54` 的 General 域落地件）**零测试**：提交才发、回滚不发、重复 finish 不重发、单接收方失败不阻断其余，四条契约均无用例 | `service/general/GeneralPatResultNotifier.java:23-29`；全树 test 目录 `Notifier` 仅命中 `SimulationResultNotifier` | |
| TESTDOC-05 | 前端测试面仅 1 文件 6 用例；本轮 4 项前端改动（毫秒往返、音频节拍、码值解释、提交/采集 mixin）零回归，而这些都是纯函数可测逻辑；`babel-jest` 为孤儿依赖 | `bw-frontend/frontend/package.json:4-12,81` | |
| TESTDOC-06 | `AGENTS.md:53` 红线 5 列举的 4 处「已改断」**当前全部已闭环**，红线描述与事实相反 | 主评审实测：`roomgId` 活跃调用 0（仅一处测试注释）；`rows:999` 0 处；`saveBatch`/`exportTemplate` 前端各 8 处已接线；`.docx` 改为前端 `mammoth` 本地解析（`views/manage/postJob/hanzi/articleManage/Index.vue:112,159`），与后端收窄到 `txt/md/csv`（`controller/TheoryKnowledgeController.java:178`）一致 | ✔ |
| TESTDOC-07 | `AGENTS.md:50` 红线 2 的 MyISAM 断言已过期 | 主评审实测：`project006.sql` = **105 张表全 InnoDB，0 张 MyISAM**（唯一命中是 `:36455,36458` 的说明注释）；22 张 MyISAM 只存在于迁移前 base 快照 `project006-base.sql`（78 InnoDB + 22 MyISAM） | ✔ |
| TESTDOC-08 | 测试基线数字三处不一致且全部过期：`AGENTS.md:29` = 216、`README.md:93` = 228、`docs/reviews/2026-09-08-full-project-review.md:45` = 216，实际 **316 / 74 suite** | 同左 | ✔ |
| TESTDOC-09 | `docs/README.md` 文档地图漏 5 份当前有效文档，并仍称 2026-09-08 评审为「当前唯一入口」 | `docs/README.md:5-10,16-24` | ✔ |
| TESTDOC-10 | 6 处悬空文档引用：`docs/README.md:66` 的 `../frontend/README.md`（前端已迁至 `bw-frontend/frontend/`）、`bw-frontend/frontend/README.md:13/14/16` 的 3 条 `../docs/...`、`application.yml:107` 与 `project006.sql` 指向已归档路径 | 同左 | |
| TESTDOC-11 | characterization 快照测试用 `Files.readString(Path.of("src/test/resources/..."))` 依赖工作目录（IDE/根目录执行即假失败），`SCORING_UPDATE=1` 可由环境变量回写源码树，且对 VO 全字段整体比对 | `test/java/com/nip/common/utils/TickerPatUtilsCharacterizationTest.java:36,91-111,127-143` | |

---

## 4. P3（9 条）

| ID | 问题 | 证据 |
|---|---|---|
| DATA-04 | 迁移无自动化回滚/down 脚本，恢复依赖人工备份还原（脚本 fail-closed + 幂等已降低风险，但缺 runbook） | `backend/database/migrations/` |
| DATA-05 | `t_post_telegram_train_floor_content_value.attempt` 迁移为 `NOT NULL DEFAULT 0`，实体却是可空 `Integer` 且无 `@Column(nullable=false)`/`@DynamicInsert` → DB 默认值形同虚设（当前被 attempt 栅栏拦下，故实际安全，但与兄弟实体口径不一） | `migrations/2026-09-11-04-personal-handkey-capture.sql:84`；`entity/PostTelegramTrainContentFloorValueEntity.java:16` |
| DATA-06 | general 收尾扫描每 5 秒把整实体（含 `longtext`）载入仅为取 id；telex 侧 `select id` 投影更省 | `service/general/GeneralKeyPatService.java:617-619`；`GeneralTickerPatService.java:673-675` |
| DATA-07 | 跨域菜单/路由数据 UPDATE 塞进电传倒计时 DDL 迁移，可追溯性差且与前端同版本强耦合 | `migrations/2026-09-11-03-post-telex-capture-clock.sql:100-111` |
| SEC-12 | `%prod` 内置 `root/root` 数据库口令（明文进仓库）；`/api/tools/system` 匿名返回主机名与内网 IP | `application.yml:90-101`；`controller/free/ToolsController.java:41-77` |
| FE-01 | 本轮迁移的电子键文件残留热路径 `console.log` 与死墙钟赋值（`startTime = Date.now()` 无读取者） | `views/manage/postJob/telegram/examTrain/js/examTrain.js:191,247,386`；`organization/electronKeyZuXun/train/student/js/handKeyTrain.js:209,239` |
| FE-03 | 死代码：`teacherBack.js` × 4（全仓无调用者）、`datagram/telex` 学生页未调用的 `useControl` 导入 | `views/manage/organization/*/train/teacher/js/teacherBack.js` |
| DELIVERY-07 | 三处互不一致的硬编码地址（`bin/config.json` `10.0.0.217` 为死配置、`nip.db` `10.10.0.117`、`DivDemo.vue` `10.10.0.210`），误导排障 | `bw-frontend/bin/config.json:1-4`；`electron/core/index.js:9-10` |
| TESTDOC-12 | 三处低价值断言：`SmokeTest.schemaBoots` 为裸 not-throw（`%test` 用 `drop-and-create`，无法捕捉 `%prod` 的 `validate` 漂移）、schema 导出器以「非空」为断言且被 CI 排除、过宽的 `rejected()` 判据 | `test/java/com/nip/SmokeTest.java:19-22`；`test/java/com/nip/rehearsal/EntitySchemaSnapshotRehearsal.java:20-22,72-73` |

---

## 5. 已核实正常（避免重复排查）

### 5.1 评分与采集（六域）

- **码率一律服务端重算**，客户端无 `speed` 入参：`PostTelexPatTrainService.java:341-351,589-621`、`PostTelegramTrainService.java:564-580`、`PostTelegraphKeyPatTrainService.java:427-498`、`general/GeneralTickerPatService.java:1148-1163`、`general/GeneralKeyPatService.java:896-905`。
- **`dash.max` 封顶修正到位**（`dot.max` 不再错用）：`PostTelegramTrainService.java:808,813`、`general/GeneralTickerPatService.java:1074,1079`。
- **四码组/分换算**仅一次最终舍入、零除返 0：`dto/score/TrainingRateUnit.java:8-19`。
- **`CaptureTimeline` 区间算术自洽**：拒绝乱序/重叠/0 长度/负值/越界，`requireNoOverlap` 先排序再复用同一校验：`common/utils/CaptureTimeline.java:12-50`。
- **attempt 栅栏覆盖 upload/finish/reset/start 全路径**，写路径独立再校验：`PostTelexPatTrainService.java:296-322,478-487`、`general/GeneralTickerPatService.java:516,564,705,734,775-778`。
- **严格数字解析**拒绝小数/字符串/科学计数/布尔/null：`common/utils/StrictIntegerDeserializer.java:13-23`。
- **结算兜底幂等 + 行锁 + 失败重抛回滚**：`general/GeneralTickerPatService.java:678-689`、`PostTelexPatTrainRecovery.java:30-46`。
- **综合组网服务端评分**：行锁 + 已评分要求答案一致 + 冻结规则逐格权重，未答格得 0 不借用其它格：`service/GroupNetTrainService.java:149-171`。
- **红线 1（事务内吞异常）在评分/采集域残留为 0**：`general/GeneralTickerPatService.java:1012-1017` 等处均重抛或放行业务异常。

### 5.2 并发与 WebSocket

- **红线 3 残留面 = 0**：8 个端点的会话/房间态全部挂 `static ConcurrentHashMap`，无实例会话字段（`ws/WebSocketService.java:47` 等）。
- 三条结算路径（`finish`、教师结束、`@Scheduled` 兜底）统一 `PESSIMISTIC_WRITE` 行锁 + 锁内状态复检，**行锁为 DB 级，跨实例有效**：`general/GeneralTickerPatService.java:512-535,678-693`。
- `AFTER_SUCCESS` 观察者在提交后触发，`send` 内吞单点异常不断广播，通知仅作触发不携数据故无脏读：`service/simulation/SimulationResultNotifier.java:36-51`。
- 成员集合为 `CopyOnWriteArrayList`，房间生命周期用 `compute/computeIfPresent` 原子改 map，断连摘除不泄漏：`ws/service/simulation/SimulationRoomLifecycle.java:17-60`。
- `ReentrantLock` 条带全部 `try/finally` 释放：`ws/service/RoomLifecycleLocks.java:37-49`。
- 采用经典 `quarkus-websockets`（Undertow worker 线程），`onMessage` 内同步 DB 调用**不在事件循环上**：`backend/pom.xml:52-54`。
- **CONC-02 已排除**（评审队原列 P3 推断项）：主评审核实前端仿真结果提交已全量改走 REST（`common/api/UnionApi.js:47`），`topic:'result'` 仅作**接收**触发回读（`views/manage/unionJob/disturbCode/js/train.js:252`、`lineNotify/js/Issue.js:105`、`components/ListenIn.vue:334`），全仓无前端发送点 → WS 侧静默 `return` 不造成结果丢失。

### 5.3 数据与迁移

- 本轮全部新列在实体注解与迁移中列名/类型族一致，**`%prod` `validate` 不会因新列失败**：`migrations/2026-09-11-04-general-capture-clock.sql:56-68`。
- 两个 `full_score` 故意异型且各自匹配（telegram `int`↔`Integer`；telegraph_key `decimal(38,2)`↔`BigDecimal`）。
- 唯一键迁移 **fail-closed**：NULL 业务键与重复键均 `SIGNAL 45000`，不删不改不任选行：`migrations/2026-09-11-01-simulation-page-uniqueness.sql:70-95`。
- 迁移后两快照 **106 张表全部 InnoDB**，所有 `delete+insert` 结算路径落在 InnoDB 上：`database/rehearsal/2026-09-11-customer-integrated-v1/current-engine.tsv`。
- `rehearse-migrations.sh:61-73` 覆盖全部 11 个迁移脚本与 current+base 双快照，并对采集/JSON 迁移做重复执行幂等校验。

### 5.4 安全面已核实干净的部分

- `@RequireAdmin` 拦截器**自身实现正确**：token → `getUserByToken`（查无即 203）→ `existsAdminRoleByUserId` → 非管理员 HTTP 200 + `code:207`，未触碰 203/204/206：`common/interceptor/RequireAdminInterceptor.java:21-48`。
- **全仓零 fastjson 反序列化调用**：`com.alibaba` import 0 命中，`parseObject`/`ParserConfig`/`autoType` 0 命中，JSON 统一走 Gson。
- **无 SQL/JPQL 注入面**（唯一例外 `UserDao.findAllUser` 的 REGEXP 拼接，已列 SEC-08）。
- 文件上传有 10MiB 上限 + `txt/md/csv` 白名单，只读临时路径不拼接用户文件名，**无 `../` 穿越面**：`service/TheoryKnowledgeClassifyService.java:107-131`。
- 异常映射不泄露堆栈（`ValidationExceptionMapper.safeMessage` 剥类名并截断）；**日志未打印凭据**（带 token/password/idCard 变量的 `log.*` 0 命中）。
- 本轮新增电子键采集/结算/轮次路径身份**一律 token 推导**，未接受请求体 `userId`：`general/GeneralKeyPatService.java:467-469,532-533,576-577`；跨人读取有显式授权判定 `:1210-1220`；手键组训 WS 的 `role` 路径参数与库内真实 role 比对 `ws/WebSocketGeneralTickerPatService.java:72-81`。

### 5.5 跨栈契约

- **203/204/206 码值与文案两侧逐字冻结未改**：`common/constants/ResponseCode.java:18-20` ↔ 前端 `common/http/index.js:47-56`。
- 分页 `rows` 服务端钳制 `[1,200]`、`page` 归一 ≥1，前端均用 `rows:10`：`common/utils/Page.java:43-45`。
- 本轮新字段（`protocolVersion`/`attempt`/`captureIntervals`/`serverElapsedMs`/`receivedAt`/`rateUnit`/`pageAnalyzeVOS`）在五个已迁移域两侧拼写与嵌套一致。
- 历史孤儿端点 `saveBatch`/`exportTemplate`/`exportQuestionByLevelId` 已两侧接线，导出返回 JSON 由前端生成文件。
- 旧协议客户端被 attempt/protocolVersion 栅栏**拒绝而非静默降级**：`PostTelexPatTrainService.java:478-487`。

### 5.6 前端与交付

- **H1 多码帧逐项消费**（不再用单 ref 当事件队列）：`common/utils/WebSerial.js:75-97`、`common/mixin/useTraffic.js:73-80`。
- **计时用 `performance.now` 单调锚点**，无墙钟算分：`common/mixin/useTrainingCapture.js:13,29,33,45`、`views/manage/postJob/datagram/js/usePageSubmission.js:40-43`。
- AudioWorklet 按样本计时、跨块保留 `sampleRemainder`、`isFinite`/负值校验、暂停恢复同一游标、无 `console`：`public/processor.js:89-96,105-122`。
- `SocketConnection` 心跳在业务 parse 前过滤 PING/PONG，退避重连，`close` 释放定时器：`common/ws/SocketConnection.js:68-107`。
- 无固定 `playSpeed=80`/`1.18` 双口径残留，速度跟随到位。
- 许可 **fail-closed**：任一副本读失败且无可用授权即抛错 → `storage_error`，不放行：`common/utils/licenseStore.js:191-215`；设备码漂移容差按槽位阈值（换硬盘/重装仍可通过）：`common/utils/machineCode.js:42-75`。
- HTTPS 页面强制 HTTPS/WSS 服务地址，错配抛错**不静默降级为混合内容**：`frontend/src/config/runtime.js:6-22`。
- 制品清单 SHA256 全量核对并拒绝路径穿越/符号链接：`scripts/artifact-manifest.cjs:105-118,149-186`；release job 权限最小化（仅 `contents: write`）。
- **IPC 面收敛**：无任何 `ipcMain` 通道接受渲染进程传入的路径/命令/SQL 直接执行（许可读写为固定目录，fingerprint/serial 用固定参数或枚举出的设备路径）：`electron/controller/license.js:29-50,141-192`。

---

## 6. 整改批次建议

按「可独立回滚 + 阻塞关系」排序，不含 §8 的现场门禁：

| 批次 | 内容 | 覆盖 | 阻塞关系 |
|---|---|---|---|
| **B1 止血（发布前必做）** | ① `UserSyncDto` 删除 `password/token/deviceId` 或 convert 忽略三字段；② 4 个 controller 补类级 `@JWT`；③ `teacherUploadScore` 加授权；④ 文件服务路径归一 + 绑定 127.0.0.1 + 去 CORS `*` | P0-01…04 | 无；应先于任何交付 |
| **B2 授权层收口** | `@RequireAdmin` 扩到评分规则/主数据/题库写端点；训练 `delete`/`updateStatus` 改属主或管理员判定（复用 `readableMember` 同构的 `writableTrain`）；理论考试与 free 统计端点改 token 推导身份并删除请求体 `userId`；补架构测试防回归 | SEC-03/07/08/09/10、SCORE-02 | 跨栈契约改动，需同步 `common/api/*.js`（红线 5） |
| **B3 凭据协议** | token 改 `SecureRandom` 不透明串（与口令解耦）+ DB 存哈希 + `issuedAt/expiresAt` + 移除 query-string 回退；WS 握手校验 token/deviceId 并以校验结果覆盖路径 `uid`；`%prod` 口令改环境变量注入并降权 | SEC-04/05/06/12 | 依赖 B2；需前端 `http/index.js` 同版本发布，接 `docs/plans/2026-09-09-password-session-migration-plan.md` |
| **B4 组训数据报域** | 纳入采集契约重算码率/用时 + 冻结满分 + 行锁 + 事务后通知 + token 主体；前端 `datagramZuXun` 同步 `telexZuXun` 修复。**或**整域下线 | SCORE-01/02/03、CONTRACT-01、FE-02 | 需先确认该域是否启用（§7） |
| **B5 桌面交付** | `bin/nip.db` 发布态归一为 localhost；`contextIsolation:true` + preload 白名单、恢复 `webSecurity`、去 `--ignore-certificate-errors`；桌面包纳入 CI 并出 manifest；release 断言 tag == `pom.version`；串口选择器三处修复 | DELIVERY-01/03/04/05/06/07 | B5 的证书项是 G4「可信证书」门禁前提 |
| **B6 测试与文档** ✅**已执行（2026-09-12）** | 见下方执行记录 | TESTDOC-01…12 | 已完成 |
| **B7 长尾** | 电传倒计时补索引；`GeneralSettlementRecovery` 扫描兜底；裸 `firstResult()` 分批收敛（优先鉴权与结算写路径）；迁移序号唯一化 + 回滚 runbook；死代码与热路径日志清理 | DATA-01…07、CONC-01、SEC-11、FE-01/03 | 无 |

### 6.1 B6 执行记录（2026-09-12，7 个提交 `f31861a..dfb43c3`）

| 发现 | 处置 | 提交 |
|---|---|---|
| TESTDOC-01 | `%test` 加 `quarkus.scheduler.enabled: false`，评分门禁不再受 5s 恢复调度器干扰（已核实无用例依赖定时器自动触发） | `f31861a` |
| TESTDOC-03、12 | 删除 `ScoringConsistencyTest.allThreeCallersAgreeOnRateSign` 与 `SmokeTest.schemaBoots`（均不能独立失败；算法契约已由同类其余用例覆盖）。**推翻本文原建议**：`calculateWpmScore`/`applyDeductions` 不收回 private —— `PostTelegramTrainScoreTest` 是其合法驱动方 | `c77600a` |
| TESTDOC-04 | 新增 `GeneralPatResultNotifierTest` 三条契约（提交才送达且事务体内为空、单接收方失败不连坐、离线收件人跳过）。突变检验：`AFTER_SUCCESS`→`IN_PROGRESS` 时该用例失败，证明可失败 | `7873645` |
| TESTDOC-02 | WS 九张进程级 static 表的清零与反射通道收敛到 `testsupport/WebSocketStateReset`，四个测试类去重，断言语义未变 | `5eda012` |
| TESTDOC-11、12 | 快照测试期望值改 classpath 只读、删除 `SCORING_UPDATE` 源码树回写；`EntitySchemaSnapshotRehearsal` 去伪断言并明确为手动导出入口（不纳入 CI） | `b1f5713` |
| TESTDOC-06…10 | AGENTS.md 红线 2/4/5、测试基线与「可并行」约定更正；评审入口改指本文；`backend/README.md` 规模数字重取；3+1 处悬空引用修正 | `94f48b4` |
| TESTDOC-05 | 新增 3 个纯函数模块的 9 条 `node:test` 用例（训练设置毫秒往返、摩尔斯时序换算、成绩对齐），删除孤儿 `babel-jest`；`ElectronMorse`/`useConfirmedSubmission` 因需重构生产代码才可测而**跳过并记录** | `dfb43c3` |

**执行后基线**：后端 `./mvnw -B clean verify` → **317 测试 / 75 suite，0 失败 0 错误 0 跳过**；前端 `npm run test` 15/15、`npm run build` 成功。中间提交非坏态已验证（`7873645`、`5eda012` 独立 `test-compile` 通过）。

**B6 未做的两项**（需重构生产代码，另立项）：`ElectronMorse` 的纯状态机与 `useConfirmedSubmission` 的「单在途 + generation 取消」状态机需从 Vue 生命周期/`Modal`/`localStorage` 中剥离才能单测；四个 WS 测试类各自的私有 `SessionProbe` 未合并（共享版 `getBasicRemote()` 返回非 null，会把当前被 `catch` 吞掉的 NPE 变成真实记账，可能翻转既有断言）。

---

## 7. 需运行验证或需产品确认（本次只读约束下无法判定）

| 事项 | 需要什么 |
|---|---|
| **组训数据报/电传域是否启用**（决定 B4 走修复还是下线） | 产品/负责人确认；代码路由与前端两个学员页当前均为活 |
| `@RequireAdmin` 运行时是否真生效（CDI 拦截器 + `@Context` 注入边界） | 用非管理员 token 请求 `POST /api/user/getAllUser`，观察是否返回 `code:207` |
| `t_role.is_admin` 实际数据分布（是否有 `isAdmin=0` 角色被派给学员，使 207 形同虚设） | 查库 `select id,title,is_admin from t_role` 与 `t_user_role` 分布 |
| `getTrainInfo`/`importTrainInfo` 收紧后是否打断「离线库导入」 | 该功能的真实使用场景说明 + 前端调用面 grep |
| 部署形态单实例 vs 多实例 | 若多实例，`static` WS 房间态与进程内 `ReentrantLock` 不跨节点（结算仍靠 DB 行锁，安全） |
| `onClose/onError` 中 `this.quitRoom*` 自调用 `@Transactional` 是否真开启事务 | 触发一次断连，观察 `userStatus` 是否落库 |
| Hibernate 6.6 的 `validate` 是否容忍 `varchar`↔`longtext` | 对两快照跑一次真实 `%prod` `generation: validate` |
| `t_post_telex_pat_train.content` 为 `text`（64KB）是否够用 | 确认该列实际写入的最大 JSON 体量 |
| DELIVERY-02 的局域网实际可达性 | 目标机防火墙与网卡绑定实测（代码层已确认无 host 绑定 + 穿越逻辑） |
| `PostTelex` 结算后详情页「标准页-用户内容对齐」重映射对越界索引静默降级为空串 | 构造页/组数不齐的真实提交，回读详情页判断是否误显空白 |
| 其它训练类型（`TelegraphKeyPatSynthetical`、`PostMilitaryTerm`、`PostRadiotelephone` 等）是否活跃 | 若活跃则与 SCORE-01 同类风险，需纳入排查 |

## 8. 不作为缺陷上报的既定决策与已知门禁

**产品已确认接受**（本次评审未重复上报）：单 token 互踢为设计行为；不引入 `user_session` 表；业务错误恒 HTTP 200 + JSON `code`；203/204/206 码值文案冻结；`resetPassword` 固定临时密码；fastjson 1.2.78 版本风险（已核实全仓零反序列化调用，见 §5.4）。

**计划内已知未完成的现场门禁**（`docs/plans/2026-09-10-customer-issue-fix-plan.md` 剩余 5 条未勾项）：G4 客户环境记录、存量进行中训练的切换窗口、F2 与目标机按键到声音延迟阈值、Web 真实站点 + 可信证书发布、发布负责人签收。本文 §1–§4 的发现与这 5 条互不重叠。

## 9. 评审方法、纠正记录与可追溯性

**域划分**（8 路并行，互不重叠所有权）：评分采集 `ScoreCore`、数据与迁移 `DataIntegrity`、安全授权 `Security`、并发与 WS `Concurrency`、跨栈契约 `Contract`、前端 `Frontend`、交付形态 `Delivery`、测试与文档 `TestsDocs`。各域完整原始取证可经 `agent://<域名>` 追溯。

**主评审独立复核**：全部 4 条 P0 与 11 条 P1 逐条读源码确认；另复核 P2 中标 ✔ 的 8 条。复核过程纠正了评审队 4 处：

| 项 | 评审队结论 | 复核后 |
|---|---|---|
| WebSocket 端点数 | 7 个 | **8 个**（`@ServerEndpoint` 文件实测） |
| 裸 `firstResult()` 残留 | 约 57 处 / 44 文件 | **55 处 / 42 文件** |
| MyISAM 残留 | 「22 张只在 base 快照」（另一域称「106 表全 InnoDB」） | 当前快照 `project006.sql` **105 表全 InnoDB / 0 MyISAM**；base 快照 78 InnoDB + **22 MyISAM** |
| CONC-02 WS `TOPIC_RESULT` 静默 return | P3 推断项，疑前端仍发该帧致结果丢失 | **排除**：前端已全量改走 REST，无发送点（§5.2） |
| DELIVERY-02 文件服务 | P1 | **上调 P0**：无凭据、局域网可达、可任意路径写 |

**评审边界**：本次为静态取证评审，未运行应用、未连数据库、未执行构建或测试（`316/74` 基线取自本轮已完成的 `verify` 运行）。§7 列出的事项需运行态或产品输入才能闭合。
