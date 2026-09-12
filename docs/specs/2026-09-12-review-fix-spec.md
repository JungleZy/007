# 2026-09-12 评审整改规格（Spec）

> **状态：可执行基线（2026-09-12，经 5 路并行可行性评审修订）**。依据 [`../reviews/2026-09-12-full-project-review.md`](../reviews/2026-09-12-full-project-review.md)（当前唯一全项目评审入口，48 条发现）。
> 对应计划：[`../plans/2026-09-12-review-fix-plan.md`](../plans/2026-09-12-review-fix-plan.md)。
> **B6（测试与文档）已于 2026-09-12 执行完毕**（评审 §6.1，提交 `f31861a..dfb43c3`），本 Spec 不再重复其内容。
> 行号已按当前代码复核；执行前仍以符号与源码为准。

## 0. 修订记录（首版 → 当前）

首版经 5 路并行可行性评审（授权面／凭据面／组训电传域／桌面交付／文档一致性），以下结论**推翻了首版写法**，均已并入正文：

| 首版写法 | 问题 | 现写法 |
|---|---|---|
| SEC-08 挂在 B2 但无任务 | 静默丢项，使「11 条 P1 全关」不可达 | 新增 **T2-4** |
| `findExamUser` 改 token 推导 | 教员阅卷传的是**他人** `user_id`，改后教员只能读自己（且教员无 exam_user 行必报错）→ 打断阅卷 | 保留 `userId` + 授权判定 |
| T2-3「统一为创建者或管理员」 | **收窄**现有能力：`generalKeyPat`/`generalTickerPat` 已允许房间内 `role=1` 组训人改状态 | 口径统一为「创建者 ∪ role=1 组训人 ∪ 管理员」 |
| 复用既有 `readableMember`/`owned` 拿 207 | 既有 helper 抛 `IllegalArgumentException` → 映射为 **202**，拿不到 207 | 新增共享 `ForbiddenException` → 207，并迁移既有 helper |
| T1-1 删 `UserSyncDto.password` + T3-4 要求「password 走 hasher」 | 自相矛盾：请求体已无口令，验收无输入 | 导入建号 **不设口令**（`password` 留 NULL，需管理员 `resetPassword`） |
| T1-3「`@RequireAdmin` 或教员角色判定」 | 库里**不存在教员角色**：`t_role` 只有系统管理员(`is_admin=0`)与普通人员(`is_admin=1`) | 只有 `@RequireAdmin` 一个选项，并写明副作用 |
| T3-5「前端 `ws_connect` 是唯一改点」 | 7 处直连绕过该封装（首页通知／联合作业／广播教学／干扰码／线路通报），按字面改会全部握手被拒 | 注入点改为 `SocketConnection.connect()` |
| T3-5「无 `roomUser` 行一律拒绝」 | 路由房**建房人本就没有** `roomUser` 行，现有代码靠「合成 `userType=null`」让教员入房 → 按字面改会打断路由房组训 | 改为「无行**且**不是 `createUserId`」才拒绝 |
| T3-2 新增 `token_issued_at` 单字段 | 与 `2026-09-09-password-session-migration-plan.md` Phase 9 的三字段设计冲突，且其部署门禁未过 → 会导致第二次破坏性 `%prod` 迁移 | **移出本轮**（§1.2） |
| T5-2「preload 白名单 `invoke/send/on/once/removeListener` 即可」 | 漏 **5 处 `ipc.sendSync`**（网络设置页、串口页）；`on` 剥参数会让串口列表永远拿不到数据；`removeListener` 经 contextBridge 恒不匹配 | 白名单与 `on` 的包装写法写死（§8 T5-2） |
| T5-2「`webSecurity` 尝试恢复」 | `file://` + `audioWorklet.addModule()` 在 `webSecurity:true` 下必失败且**无降级**，摩尔斯发音整体不可用 | **移出本轮**（§1.2），需先做 `app://` 协议改造 |
| T1-4 根目录取 `context.appPath` | 文件服务跑在 **fork 子进程**，其 `appPath` = `<安装目录>/bin/file`，比主进程窄一层；按主进程 appPath 约束会把可读范围**扩大**到含 `bin/server`、`bin/nip.db`、`app.asar` | 根取子进程 ctx 的 `appPath` |
| T1-4 CORS 白名单含 `file://` | `file://` 永不作为 Origin 出现（不透明源序列化为 `null`）；ACAO 也不能是列表 | 本轮**不动 ACAO**，安全收益由「绑本机 + 路径约束」提供 |
| T7-4 重命名已执行的迁移脚本 | 无迁移账本表，运维按文件名记录「跑过哪些」；改名会让已执行脚本以新名重现 | **不改名**，只重排演练数组为字典序 + 文档说明 |
| T7-5 给 `attempt` 补 `@Column(nullable=false)`「与兄弟统一」 | 理由反了：同类页/层提交值实体的兄弟口径恰恰是**不带**该注解 | 删除该项，只保留投影优化 |

## 1. 目标与边界

### 1.1 目标

1. 关闭全部 **4 条 P0**：凭据回传、匿名写评分规则、无授权上分、桌面文件服务任意读写。
2. 关闭 **10 条 P1**：SEC-05/06/07/08/09、SCORE-01/02、CONTRACT-01、DELIVERY-01、SEC-04 的**可反解**部分。
3. 处置有明确落地路径的 P2/P3：SCORE-03、SEC-10/11/12、CONC-01、DATA-01/02/03/04/06/07、CONTRACT-02/03、FE-01/02/03、DELIVERY-04/05/06/07。
4. 保持既有契约红线（§2），不产生前后端不匹配或编译不过的中间提交。
5. 每项改动有**可观察验收证据**；不以「代码看起来合理」代替证据。

### 1.2 非目标（本轮明确不做，各带证据）

| 项 | 为什么不做 |
|---|---|
| **会话 TTL / 刷新 / 撤销**（SEC-04 的「永不过期」半边） | 权威设计在 `../plans/2026-09-09-password-session-migration-plan.md` Phase 9：三字段（`issued_at`+`expires_at`+`revoked_at`）+ 产品确认时长 + 部署确认迁移，**门禁未过**。本轮只加 `issued_at` 会导致将来第二次破坏性 `%prod` 迁移。SEC-04 的**可反解**半边由 T3-1 完整修复 |
| **`webSecurity: true`** | 打包页走 `loadFile` → `file://`；摩尔斯发音唯一实现用 `audioWorklet.addModule(new URL('processor.js', document.baseURI))`，模块脚本恒以 CORS 模式抓取，`file://` 不透明源拿不到 ACAO → `MorseVoiceHighPerformance.js:247-251` 直接置 `failure` 且**无降级路径**。要拿这条收益必须先做 `app://` privileged scheme 改造（`registerSchemesAsPrivileged` + `protocol.handle` + `loadURL`），是独立范围与独立风险 → 另立项 |
| **桌面 deb/nsis 进 CI** | 需要 `fpm`、把 native 后端二进制 staging 进 `bin/server/server`、以及 `productName` 为中文导致的资产名规范化问题。本轮只做 `--dir` 冒烟 + manifest（§8 T5-3） |
| 单 token 互踢 / `user_session` 表 / `resetPassword` 固定临时密码 / fastjson 版本 | 产品已确认接受 |
| G4 现场门禁 | 见 `../plans/2026-09-10-customer-issue-fix-plan.md` 剩余 5 条未勾项 |
| 前端整体重构 / Pinia / Rust 重写 | 无关范围 |

### 1.3 本 Spec 作出的决定

| 决定 | 选择 | 依据 |
|---|---|---|
| 组训数据报/电传域（`generalTelexPat`） | **修复** | 代码层活跃：消费面为 **11 个前端文件 / 12 个端点 / 5 处 WS 连接**（含 3 处教员页）。下线活跃功能需产品批准。**注**：菜单表 `t_menus` 无该域入口记录，「客户是否在用」仅有代码证据，无数据证据 —— 若产品确认停用，则改为整域下线（前后端同提交） |
| 授权拒绝语义 | 新增 `ForbiddenException` → **HTTP 200 + `code:207`**；既有 `readableMember`/`owned`/`requireOwnedTrain` 同提交迁移到它 | 既有 helper 抛 `IllegalArgumentException` → `ValidationExceptionMapper` → **202**；不迁移就会出现「同语义两套码」（违反红线） |
| 写权限口径（唯一） | **创建者 ∪ 该训练/房间内 `role=1` 组训人 ∪ 管理员** | 与 `GeneralKeyPatService:576-583`、`GeneralTickerPatService:636-639` 的既有实现一致，不收窄组训场景 |
| token 形态 | `SecureRandom` 不透明串 + 存储 **SHA-256 hex** | `AESUtil.decrypt` 全仓**零生产调用**（仅 `common/utils/AESUtil.java:85` 定义），服务端从不反解，仅等值查找 → 随机化零破坏面。`t_user.token` 为 `varchar(255)`，64 字符哈希**无需迁移** |
| WS 凭据注入点 | **`bw-frontend/frontend/src/common/ws/SocketConnection.js` 的 `connect()`**（在 `new WebSocket(this.url)` 前追加 query） | 7 处直连点 + `PublicSocket` + 重连（复用 `this.url`）一次覆盖；训练页确实不用改。`MessageWebSocket.js` 连的是**仓外硬件桥接**，明确不在范围 |
| 渲染进程安全特性 | preload + `contextIsolation:true` + `nodeIntegration:false` + 删 `--ignore-certificate-errors`；**`webSecurity` 保持 `false`** | 见 §1.2 |
| 桌面文件服务 | 路径约束（根 = **子进程 ctx 的 `appPath`**）+ 监听地址**可配置、默认 `127.0.0.1`` | UI「资源服务地址」明示支持填远端 IP，写死本机会单方面砍能力；可配置既默认安全又留逃生门 |

## 2. 不可变契约（红线）

1. 业务错误恒 **HTTP 200 + JSON `code`**。
2. **`203`/`204`/`206` 码值与文案逐字冻结**；授权拒绝统一 **`207`**（`ResponseCode.CODE_207`）。
3. 跨栈契约改动**两侧同一 commit**，提交正文贴 `bw-frontend/frontend/src/common/api/*.js`（28 个模块）的 grep 结果。
4. 事务内 catch 要中止必须重抛或 `setRollbackOnly()`。
5. 改实体/表结构必须同步 `backend/database/migrations/`（`%prod` 为 `validate`）。
6. 不留 shim／别名／废弃路径；调用点整体切换。
7. 测试只为「真实可能失败的可观察契约」而写。

## 3. 批次与依赖

| 批次 | 任务 | 覆盖发现 |
|---|---|---|
| **B1 止血** | T1-1…T1-4 | P0-01…04 |
| **B2 授权层** | T2-1…T2-4 | SEC-07/08/09/10、SCORE-02(非 B4 部分) |
| **B3 凭据与握手** | T3-1、T3-3、T3-4、T3-5、T3-6 | SEC-04(可反解)/05/06/11/12 |
| **B4 组训数据报域** | T4-1、T4-2 | SCORE-01/02/03、CONTRACT-01、FE-02 |
| **B5 桌面交付** | T5-1…T5-5 | DELIVERY-01/03(部分)/04/05/06/07 |
| **B7 长尾** | T7-1…T7-9 | DATA-01/02/03/04/06/07、CONC-01、CONTRACT-02/03、FE-01/03 |

## 4. B1 止血（P0）

### T1-1 训练同步端点不得回传凭据（P0-01 / SEC-01）

- **现状**：`service/general/GeneralKeyPatService.java:1062,1068` 用 `PojoUtils.convert(userEntities, UserSyncDto.class)` 整体拷贝 `UserEntity`；`dto/general/UserSyncDto.java:46,50,54` 含 `password`/`token`/`deviceId`。`controller/general/GeneralKeyPatController.java:144-151` 的 `getTrainInfo`（`trainId` 自增可枚举）**当前不接受 token 形参**。
- **目标**：
  1. `UserSyncDto` 删除 `password`/`token`/`deviceId`（DTO 不再具备承载凭据的能力）。
  2. `getTrainInfo` **新增 `@RestHeader(TOKEN)` 形参**；抽谓词 `boolean exportable(trainId, actorId)`（创建者 ∪ role=1 组训人 ∪ 管理员）。
  3. 单点 `getTrainInfo` 不满足即抛 `ForbiddenException` → 207；**`getTrainInfoBatch` 用该谓词 filter**（`:1113-1117` 按 `queryRelatedTrainId` 取全部 membership），学员得到**空列表而非 207**。
- **跨栈**：grep 前端是否消费 `users[].password/token/deviceId`，结果贴入提交正文。
- **验收**：非授权者调 `getTrainInfo` 得 207；学员调 `getTrainInfoBatch` 得空列表且不抛；创建者调用成功且响应 JSON **不含**三个键（断言键缺失）。

### T1-2 补齐缺失的类级 `@JWT`（P0-02 / SEC-02）

- **现状**（61 个 controller 实测，`free/` 之外恰 4 个无 `@JWT`）：

| 文件 | `@Path` | 实际风险 |
|---|---|---|
| `controller/PostTrainGlobalRuleController.java` | `/postTrainGlobalRule` | 匿名改全局训练规则 |
| `controller/PostTickerTapeTrainSettingController.java` | `/postTickerTapeTrainSetting` | 匿名改码速配置 |
| `controller/TelegraphKeyPatTrainSyntheticalController.java` | `/telegraphKeyPatTrainSynthetical` | 匿名用客户端数据结算成绩 |
| `controller/CableFloorController.java` | `/cable/floor` | **只有一个读端点**（`:27-32` `@POST /find`）→ 匿名读取报底报文 |

- **目标**：四类加类级 `@JWT`；规则类写端点同时加 `@RequireAdmin`（与 T2-1 同口径）。
- **跨栈**：先 grep 前端确认均在登录后调用；**规则页 UI 的 `v-per="'grad'"` 守卫有 6 处被注释掉**，按钮对普通用户无条件可见 → 加授权后必须同提交恢复守卫或给出 207 的可见解释，否则用户点了只得静默失败。
- **验收**：无 token 请求四类均得 203；学员调规则写端点得 207；管理员成功。**架构测试**：扫描 `controller/` 非 `free/` 包的类，断言全部带 `@JWT`。

### T1-3 理论考试上分端点授权（P0-03 / SEC-03）

- **现状**：`controller/TheoryKnowledgeExamUserController.java:79-83` 只有类级 `@JWT`。
- **目标**：加 `@RequireAdmin`（**唯一可实现选项** —— `t_role` 只有系统管理员 `is_admin=0` 与普通人员 `is_admin=1`，无教员角色）；校验 `list` 中每个考生属于该 `examId`。
- **副作用（必须写入提交正文与发布说明）**：需要上分的教员必须被赋予系统管理员角色，否则无法上分。
- **验收**：普通人员得 207；管理员对不属于该考试的考生上分被拒（202 + 明确文案）。

### T1-4 桌面文件服务收紧（P0-04 / DELIVERY-02）

- **现状**：
  - `bw-frontend/electron/service/http/controllers/file.js:12`（`getFile`）与 `:19`（`getReadStream`）`path.join(ctx.appPath, req.params[0])`，无根约束。
  - `:54-73` `fileUpload` 的 `currentPath` 仅 `.replace(/^[\\\/]+|[\\\/]+$/, '')`（无 `g`、不剥内部 `..`）→ 可写出根外。
  - `service/http/node_server.js:34` `app.listen(this.port)` 未指定 host → 绑 0.0.0.0；构造参数是 `service/http/index.js:26-34` **硬编码**的 `{http:{port:8000, allow_origin:'*'}}`（与 `bin/nip.db` 的 `_id:1` 无关，后者是死配置）。
- **目标**：
  1. `const ROOT = path.resolve(require('../core/node_core_ctx').appPath)` —— **这是 fork 子进程的 ctx，值为 `<安装目录>/bin/file`，不是 Electron 主进程的 appPath**（用主进程的会把范围扩大到含 `bin/server`、`bin/nip.db`、`app.asar`，等于把 P0 修成更大的 P0）。
  2. 读：`const p = path.resolve(ROOT, req.params[0]); if (p !== ROOT && !p.startsWith(ROOT + path.sep)) return res.sendStatus(404)`；对存在文件再 `fs.realpathSync` 二次校验（防符号链接逃逸）。
  3. 写：`currentPath` 逐段过滤丢弃 `..` 与绝对路径段，归一后同样强制在 `ROOT` 内；`mkdir`/`rename` 失败**不得**仍返回成功（当前异常被吞后仍 `res.json({data:names})`）。
  4. `node_server.js` 构造参数新增 `host`，默认 `'127.0.0.1'`，`app.listen(this.port, this.host)`。
  5. **ACAO 本轮不动**（保持 `*`）：`file://` 永不作为 Origin 出现（不透明源序列化为 `null`），ACAO 也不能是列表；当前 `webSecurity:false` 下浏览器不做 CORS 检查，收紧收益为零。安全收益来自 1–4。提交正文写明此判断。
- **验收**（在 **`--dir` 产物**上做 —— deb 装到 `/opt` 且 root 属主，普通用户写入必失败，该形态下上传验收不可达，须在计划中写死）：正常文件可读；`../../../etc/passwd` 得 404；带 `..` 的 `currentPath` 被拒或落在 ROOT 内；`ss -ltnp` 显示 8000 仅监听 127.0.0.1。

## 5. B2 授权层（P1）

### T2-1 评分规则与主数据写端点加 `@RequireAdmin`（SEC-07）

- **现状**：`@RequireAdmin` 仅覆盖 6 个 controller。无角色判定的写端点：`controller/GradingRuleController.java:51-77`（`saveGradingRule`/`updateGradingRuleStatus`/`changeGradingRuleIsDefault`/`deleteGradingRule`）、`controller/DeviceScoringRuleController.java:39-53`（`save`/`delete`）、理论题库写端点（执行时 grep 列全）。
- **目标**：逐个加 `@RequireAdmin`；**读端点不加**（加了会打断学员正常使用）。前端 UI 守卫同 T1-2 处置。
- **验收**：逐端点 学员 207 / 管理员成功；对应读端点学员仍可用。

### T2-2 理论考试与统计端点身份收口（SEC-09、SEC-10）

- **现状**（行号已复核）：
  - `service/TheoryKnowledgeExamService.java:161-194` `studentChangeExamState`、`:196-211` `saveUserRealTimeParam`（即 `studentSaveExamRealtimeContont`）：不校验 body `userId`。
  - `:277-300` `finishSelfTesting`：`:286` `examUserEntity.setScore(vo.getScore())` 直接采信客户端分数，且只按 `examId` 查行、不校验归属。
  - `controller/free/UserController.java:26-29,55-78`：3 个统计端点在**免鉴权包**且按 body `userId` 取数。
- **目标**：
  1. `studentChangeExamState`、`studentSaveExamRealtimeContont`：改 `@RestHeader(TOKEN)` 推导身份，**删除 body `userId`**（真·自操作端点）。
  2. `findExamUser`：**保留 `userId`** + 授权判定（`actor == userId`，或 actor 是该 `examId` 的 `teacher`/`createUserId`，或管理员），否则 207。**不得改成 token 推导** —— 教员阅卷传的正是他人 `user_id`（`views/manage/basicTheory/test/test/startTest/js/startTest.js:292-310`、`studentStartTest/js/startTest.js:249-269`）。
  3. `finishSelfTesting`：**服务端按试卷快照重算**（数据齐备：`t_theory_knowledge_exam_test_paper` 的 longtext 列存 `TestPaperQuestionDto` 序列化结果，含每题 `score`+`answer`，见 `TheoryKnowledgeExamService.java:241-245`）。**不设「无法重算」退路** —— 首版的退路措辞已删除。
  4. 3 个统计端点：**原路径不变**，仅移出 `controller/free/` 包并加 `@JWT` + token 推导身份（换路径会多改一轮前端）。
  5. `quarkus.http.cors.origins` 本轮**不动** —— 收敛需要与 `webSecurity`/`app://` 一并决定（§1.2），且 `Origin: null` 场景下 Quarkus CORSFilter 对白名单外来源**直接 403**（连业务码信封都拿不到），会整体打断桌面端。
- **跨栈**：删除 body `userId` 的两个端点 → 前端对应 api 模块与调用点同 commit（清单在执行时 grep 后贴入正文）。
- **验收**：A 的 token 改不了 B 的答卷/状态（DB 中 B 的行不变）；**提交任意 `score` 值不影响入库 score**；教员仍能读学员答卷；匿名请求统计端点得 203。

### T2-3 写权限口径统一（SEC-07 续、SCORE-02 非 B4 部分）

- **现状**：仓内已有**三个同语义 helper**（`PostTelegramTrainService.owned:948`、`PostTelegraphKeyPatTrainService.owned:659`、`PostTelexPatTrainService.requireOwnedTrain:647`，均只判创建者且抛 202），加上 `GeneralKeyPatService:576-583`、`GeneralTickerPatService:636-639` 的「创建者 ∪ role=1」判定。部分 `delete` 仍无判定（`GeneralKeyPatService:268`、`GeneralTickerPatService:273`）。
- **目标**：
  1. 定义**唯一** helper `requireWritableTrain(actorId, train)`，口径 = 创建者 ∪ 该训练/房间内 `role=1` 组训人 ∪ 管理员，拒绝时抛 `ForbiddenException`（207）。
  2. **迁移并删除**上述三个旧 helper（红线 6：不留第二套约定）。
  3. 各训练域 `delete`/`updateTrainStatus` 接入。属主字段名有三种（`createUser`/`createUserId`/`userId`），**不得靠字段名反射统一**，由各域显式传入。
  4. `generalTelexPat` 域由 **T4-1** 处理（避免跨波双所有权）。
- **验收**：非授权者删他人训练得 207 且 DB 行仍在；创建者成功；**非创建者的 role=1 组训人仍能改训练状态**（防收窄回归）。

### T2-4 用户目录端点与注入（SEC-08）

- **现状**：`controller/UserController.java:97-132`：`getAllUser` 有 `@RequireAdmin`，但 `getAllUserByContent`（不传条件即返全量）、`getUsersByUserNameStartingWith`、`getUserDirectory`、`getUserInfoAllByStatusDesc`、`getUserById` 无门禁，返回体含 `idCard`/`phone`/`email`（`dto/UserProfile.java:14-27`）；`service/UserService.java:593-601` → `dao/UserDao.java:93-107` `findAllUser` 把输入拼进 `REGEXP` 模式。
- **目标**：
  1. 「全量用户目录」能力收敛到唯一入口并加 `@RequireAdmin`；非管理员可达的 VO **移除 `idCard`/`phone`**（新增只含 `id`/`userName`/`userAccount`/`userImg` 的 `UserDirectoryEntry`）。
  2. `findAllUser` 的 `REGEXP` 拼接改参数化集合（`id in (?1)`），空列表显式返回空集。
- **跨栈**：VO 字段收窄是返回形态变更 → grep 前端是否消费 `idCard`/`phone`，同提交处置。
- **验收**：学员调用每个目录端点得 207 或得到不含 `idCard`/`phone` 的结果；构造含 `REGEXP` 元字符的输入不再影响匹配集。

## 6. B3 凭据与握手（P1）

### T3-1 token 改不透明随机串 + 存储哈希（SEC-04 可反解部分）

- **现状**：`service/UserService.java:452` `AESUtil.encrypt(userAccount + "-" + password + "-" + deviceId, AESUtil.UKDAI_AES_KEY)`；密钥明文常量 `common/utils/AESUtil.java:20`，`AES/ECB/PKCS5Padding`（`:30`）。
- **目标**：
  1. 签发 `SecureRandom` ≥32 字节（Base64URL 无填充），明文只回 `dto/LoginSessionDto` 的 `token`。
  2. `t_user.token` 存 **SHA-256 hex**（64 字符，`varchar(255)` 足够，无需迁移）。
  3. 查找先哈希：`UserDao.existsUserByTokenAndDeviceId:57`、`findUserEntityByToken:75`。
  4. **写面共 4 处**（首版只列了 2 处）：`UserService.login`、`userOut`、**`importUser`**、**`replaceUserIdAndSaveIfNotExist`** —— 后两处当前把客户端传来的 token/deviceId 原样落库，必须改为服务端决定（与 T3-4 同口径：一律 NULL）。
  5. **测试面（首版完全缺失，不做会在闸门集体红）**：
     - `backend/src/test/java/com/nip/testsupport/Fixtures.java` 的播种改为「入库写哈希、返回明文 token」的唯一工厂；
     - 禁止用例自行 `setToken(`（仓内共 5 处调用点）；
     - 逐个修正直接把 `entity.getToken()` 当凭据用的用例（约 8 个测试类，执行时以 `grep getToken()` 为准）。
  6. `AESUtil` 若零生产调用 → **整类删除**（红线 6）。
- **兼容性（必须写入提交正文与发布 runbook）**：存量 `t_user.token` 是旧 AES 密文，改哈希查找后**全部现存会话失效**，需全员重新登录一次。**不做双查兜底**（双查=保留旧凭据可用）。
- **导出读面**：综合组训离线导出会把用户行序列化给客户端 —— T1-1 删字段后不再含 token；此读面须在提交正文列出，以支撑「token 只回登录者本人」。
- **验收**：两次登录 token 不同；DB 值 ≠ 响应值；用 DB 哈希当 token 得 206；旧 AES 格式 token 得 206；`userOut` 后原 token 得 206。

### T3-3 移除 HTTP query token 回退与异常回传（SEC-04 续、SEC-11）

- **现状**：`common/interceptor/JWTInterceptor.java:55-63` header 缺失时回退 `request.getParam(TOKEN)`/`getParam(DEVICE_ID)`；`:73-76` 兜底分支把 `exception.getMessage()` 原样回传两个字段（鉴权前可达）。`RequireAdminInterceptor.java:37-39` 有同样回退。
- **目标**：两个拦截器只接受 header；兜底分支改固定 `ResponseCode.SYSTEM_ERROR` 文案，细节只进 `log.error`。
- **注意**：WS 侧仍用 query 传凭据（浏览器无法设置 WS 头，§1.3），两种传输约束不同，差异写入提交正文。
- **验收**：仅带 query token 的请求得 203；header 正常；鉴权期 DB 异常时响应不含 SQL/表名而日志有堆栈。

### T3-4 训练导入授权与字段白名单（SEC-05）

- **现状**：`controller/general/GeneralKeyPatController.java:152-173` 无授权；`service/UserService.java:616-630` `replaceUserIdAndSaveIfNotExist` 直接 `PojoUtils.convertOne(item, UserEntity.class)` 落库。
- **目标**：
  1. 两个端点加 `@RequireAdmin`。
  2. 只允许 `userAccount`/`userName`/`userImg` 落地；`token`/`deviceId`/`status`/`password` **一律服务端决定**：`password` **留 NULL**（`t_user.password` DDL 允许 NULL），账号需管理员 `resetPassword` 后才能登录。
  3. 顺带修 `replaceUserIdAndSaveIfNotExist` 现存的导入整体失效缺陷（执行时读代码确认具体表现并在正文说明）。
- **验收**：学员得 207；管理员导入后 DB 中该用户 `password`/`token`/`deviceId` 均为 NULL，且该账号无法直接登录。

### T3-5 WebSocket 握手鉴权（SEC-06）

- **范围**：**6 个带身份语义的端点**（首版写「8 个」）。`StartWebSocket`（`/startWebsocket/{sid}`）全仓零客户端零发送方 → **整类删除**（红线 6）；`/status` 无路径参数、无身份语义、只回 PONG → **保持匿名**（加门禁会打断心跳测试且无安全收益）。
- **目标**：
  1. 6 个端点 `@OnOpen` 从 `session.getRequestParameterMap()` 取 `token`+`deviceId`，经 `UserService.getUserByToken` 校验，失败即关闭连接。
  2. **以校验结果覆盖路径 `uid`**（路径参数只作路由）。
  3. 仿真端点：**「无 `roomUser` 行 **且** 不是 `room.getCreateUserId()`」才拒绝** —— 路由房建房人本就没有成员行（`SimulationRouterRoomService.java:162-188` 只为 send/receive 列表建行），现有代码靠合成 `userType=null` 让教员入房并广播（`WebSocketSimulationService.java:102-115`、`:570-573`）。照抄 `SimulationRoomAccess.java:32-35` 已有的 teacher 判定，**不发明第二套**；合成成员只对建房人保留且必须继续 `userType=null`/`channel=-1`。REPORT/RECEPT 分支（`:97-101`）已拒绝无行连接，本条只改 DISTURB/ROUTER。
  4. `role` 比对**只在** `/generalTickerPat/{uid}/{trainId}/{role}` 保持（`WebSocketGeneralTickerPatService.java:76-78`）—— 其余端点签名里没有 `role`，「推广到全部端点」不可实现（首版措辞已删）。
  5. **测试面**：`testsupport/WebSocketSessionProbe.java` 的 switch 加 `case "getRequestParameterMap"` 返回凭据；`WebSocketSimulationTest`/`WebSocketUnionTest` 的 URI（约 9 处）统一改带 `?token=…&deviceId=…`；`WebSocketHeartbeatTest` 因 `/status` 保持匿名而不用改。
- **跨栈**：注入点 = `bw-frontend/frontend/src/common/ws/SocketConnection.js` 的 `connect()`（`new WebSocket(this.url)` 之前追加 query，重连复用 `this.url` 自动生效）。**7 处直连点**（`Ws.js:11,15`、`UnionWs.js:15,17`、`useBroadTeacher.js:121`、`useBroadStudent.js:143`、`disturbCode/js/train.js:241`、`lineNotify/js/Issue.js:93`、`ListenIn.vue:322`）与 `PublicSocket.ws_connect` 一并覆盖。`MessageWebSocket.js` 明确排除。
- **验收**：不带凭据连接被关闭；A 的凭据连 B 的 `uid` 时按 A 注册（B 在线状态不受影响、A 收不到 B 的推送）；**路由房建房人仍能连入并广播**；真实训练页 WS 不回归。

### T3-6 生产配置凭据外置与侦察面（SEC-12）

- **现状**：`application.yml` 的 `%prod` 用 `root/root` 明文；`controller/free/ToolsController.java:41-77` `/api/tools/system` 匿名返回 OS/JVM/CPU/主机名/内网 IP。
- **目标**：
  1. `%prod` 的 `username`/`password` 改 `${DB_USER}`/`${DB_PASSWORD}`（**不得写 `${DB_USER:root}` 默认值 —— 那等于没修**）；`%dev` 保留字面 `root/root`。
  2. **必须加 `%prod` 启动校验**：Quarkus 对 `quarkus.datasource.username` 这类可选配置，未解析的 `${DB_USER}` **不抛错**而是当未配置，驱动回退到 OS 用户名去连库，进程照常启动 → 必须在 `@Observes StartupEvent` 里检查非空，缺失即抛带变量名的 `IllegalStateException`。
  3. `/api/tools/system` **整端点删除**（前端零消费，红线 6）。
- **验收**：`%prod` 缺变量时**启动即失败**且错误文案含 `DB_USER`/`DB_PASSWORD`；`/api/tools/system` 返回 404。

## 7. B4 组训数据报/电传域（P1）

### T4-1 纳入采集契约与冻结满分（SCORE-01/02/03、CONTRACT-01）

- **范本（首版选错，这是本任务最关键的一条）**：**页内采集与重算逻辑照 `PostTelexPatTrainService`**（同为「整页文本」模型：`patValue`+`pageNumber`+`attempt`+`captureIntervals`+`receivedAt`，见 `:417-455,589-619`）；**数据分层与多学员/房间/role 维度照 general 域**（成员行取代训练行做 attempt/capture 锚点，见 `GeneralTickerPatTrainUserEntity:52-55` 的 `attempt`+`captureStartedAt`+`activeMillis`）。
  **不得照抄 `GeneralTickerPatService`**：它的「页」是点划事件流（`patKeys` 序列 + `measurePage()` 的 `symbolMillis` 对账，`:590-594,811-846`），而电传的页是一个 `String patValue`，服务端只能数「组」，没有 symbol 时长可对账 —— 照搬那条断言恒为 0，无意义。
- **现状缺陷**：
  - `service/general/GeneralTelexPatService.java:405-418` 写客户端 `speed`；`:424-435` 写客户端 `validTime`；
  - `:753` `new BigDecimal(100)` 硬编码满分；`:814-820` 客户端 `avgSpeed` → `ScoreMath.wpmScore` → 计入总分；`:828-836` 累加客户端 `validTime`；
  - `finish`（`:470` `trainDao.findById`）无锁；`saveContentValue`（`:399-401` 只读成员行）的 TOCTOU 在「成员行 `isFinish` 读判定与写入之间」；
  - WS 通知在事务提交前发出；
  - DTO `dto/general/GeneralTelexPatPageSubmitDto.java:22-34` 无 `attempt`/`captureIntervals`/`protocolVersion`。
- **致命路径（首版完全未提）**：`countScore` 在同一事务里 `trainUserValueDao.deleteByTrainIdAndUserId`（`dao/general/telex/GeneralTelexPatUserValueDao.java:34-37`）删掉该用户**全部** value 行，再用 `dto/TelexPatValueTransferDto.java:8-15`（只含 `trainId/userId/pageNumber/key/value/sort`）重建 → **`finish` 跑过一次，原始采集时间轴永久丢失**，之后任何重算都会命中「已保存页缺少原始采集时间轴」。
  **处置**：照 `PostTelexPatTrainService` 的分层 —— 原始提交行（`sort=-1`，带 `attempt`/`capture_intervals`/`received_at`）**不参与** delete+reinsert：把删除改为 `delete("trainId=?1 and userId=?2 and sort>-1")`，`pageValueResult` 只装 `handle()` 产出的分析行（去掉 `:737` 的 `addAll(userValue)`），原始行原地保留。
- **目标**：
  1. DTO 加 `protocolVersion`/`attempt`/`captureIntervals`（`StrictIntegerDeserializer`/`StrictLongDeserializer`），**删除** `speed`/`validTime`；服务端按 `CaptureTimeline` 校验并重算逐页用时与码率。
  2. `countScore` 基准分改**冻结规则满分**。
  3. 原始行分层（见上）。
  4. `finish`/`saveContentValue` 行锁串行化 + attempt 栅栏 + `isFinish` 幂等。
  5. WS 结算通知改 `AFTER_SUCCESS`（复用 `GeneralPatResultNotifier`）。
  6. `finish`（`controller/general/GeneralTelexPatController.java:94`）、`updateTrainStatus`（`:78`）、`delete`（`:137`）、`getPage`（`:101`）加 `@RestHeader(TOKEN)` 与 `requireWritableTrain`/参训判定。同类缺失还有 `:57 detail`、`:64 patDetail`、`:71 findPage`、`:108 getPatValue`、`:115 statistic`、`:122 getOnline` —— 逐个按读/写性质处置。
  7. 采集边界用「成员行 `capture_started_at` 起 → 训练 `end_time`（或 now）止」，与 general key/ticker 的 `captureBound(train, member, receivedAt)` 同构。**不加时钟列**（`deadline`/`paused_at`/`pause_intervals`/`countdown_seconds`）—— 组训电传无倒计时与暂停语义（`GeneralTelexPatEntity` 无相关字段，状态只有三态），加了就是永远为 NULL 的死列。
  8. **收尾扫描对齐**：仿 `GeneralSettlementRecovery` + `closingTrainIds()` 给该域加兜底与 `(status,end_time)` 索引。
- **迁移**：`backend/database/migrations/2026-09-12-01-general-telex-capture.sql`，幂等、接入 `rehearse-migrations.sh` 的 `MIGRATIONS`、双快照通过。**列清单以实体最终形态为准**，至少含成员行的 `attempt`/`capture_started_at`/`active_millis`、value 行的 `attempt`/`capture_intervals`/`received_at`、训练行的 `protocol_version`/`full_score`（若采用冻结满分列）。
- **跨栈**：消费面为 **11 个前端文件 / 12 个端点 / 5 处 WS 连接**（含教员页与教员回放页）—— 执行时先产出完整清单再改，两侧同提交。上传改用 `common/mixin/useTrainingCapture.js`，不新写一套。
- **验收**：
  - 提交体不再有 `speed`/`validTime`，构造带该字段的请求被拒或忽略（断言入库 score 不受影响）；
  - 码率由 `captureIntervals` 重算（给定输入得确定 `speed`/`score`）；
  - 规则满分非 100 时结算基准跟随；
  - **`finish` 之后原始采集行仍在**（直接断言 `sort=-1` 行的 `capture_intervals` 非空）；
  - 跨页重叠/乱序区间被拒且不覆盖已保存页；
  - 并发两次 `finish` 只结算一次、教员端只收 1 帧；结算失败回滚无幻影通知；
  - 非授权者调 `finish`/`delete` 得 207。

### T4-2 前端 `datagramZuXun` 同步修复（FE-02）

- **现状**：`views/manage/organization/datagramZuXun/train/student/student.vue:252,255` 缺 `.value`；`:262-273` `onbeforeunload` 直接赋值且卸载不清除；`js/datagramTrain.js:125-146` `switchTelegram` 不 `await`、`:169-187` `handlerSubmit` 无 `.catch`、`:209-217` `readyTrainPat` 对 null 快照解引用。
- **目标**：移植兄弟 `telexZuXun` 已修的四项（`.value`、`.catch`、`await`、`if (saved)` + `pageTime` 恢复）；**`addEventListener` + 卸载移除这一项兄弟也没修**（`telexZuXun/.../student.vue:259-270` 仍是全局直赋值且 `onUnmounted` 不清除）→ 需**新写**，并同时修兄弟（两侧同提交）。优先合并两域为共享实现；不合并需在提交正文说明。
- **验收**：真实页面 —— 上传失败保留内容 + 可见提示、不静默翻页；刷新恢复页号与速度正确；离开训练页后无残留 `onbeforeunload`。

## 8. B5 桌面交付

### T5-1 发布态状态归一（DELIVERY-01）

- **现状**：`bw-frontend/bin/nip.db` 提交内容里 `_id:2` 为开发机 `10.10.0.117`；`_id:3` 为 `"COM3"`（Windows 串口名，而交付目标是 linux）；`_id:1` 是死配置（`node_server` 参数硬编码在 `service/http/index.js:26-34`）。`electron/core/index.js:29-46` 只在 `_id:2` 缺失时写默认值。
- **目标**：随包 `nip.db` 归一为发布态默认（`_id:2` → localhost、`_id:3` → 空/不预置）。**注意该文件运行时可写**（用户改网络设置即写回，开发态 appPath 就是仓库目录）→ **不得**把它纳入 CI 脏检查（会反复误报，且 `verifyRelease` 会以 `sourceDirty` 拒绝整个发布）。
- **验收**：`--dir` 包冷启动后 `window.httpUrl` 指向本机；`git show HEAD:bw-frontend/bin/nip.db` 不含真实业务 IP 与 COM 口。

### T5-2 渲染进程安全特性（DELIVERY-03 部分）

- **目标**：
  1. 新增 preload，`contextBridge` 暴露 `window.electron.ipcRenderer`，白名单 **`{invoke, send, sendSync, on, once, off}`** —— 必含 `sendSync`：生产有 5 处调用（`NetSetting.vue:188,205,249`、`NipSerial.vue:92`、`PreviewHJ.vue:591`），缺了网络设置页与串口连接直接 TypeError。`sendSync` 经 contextBridge 可行（三个 `ipcMain.on` 的返回值都是结构化克隆安全的）。
  2. `on`/`once` 必须写成 `(channel, listener) => ipcRenderer.on(channel, (_event, ...args) => listener(null, ...args))` —— 用 `null` 占位保住唯一活的消费点 `NipSerial.vue:57-62` 的 `(event, data)` 两参签名，且不把真 `IpcRendererEvent`（其 `sender` 就是完整 ipcRenderer）代理进主世界。
  3. **不暴露 `removeListener`**：渲染侧持有的 listener 与 preload 内注册的 wrapper 不是同一引用，`removeListener` 恒不匹配 —— 写进契约就是埋雷。当前无活调用点；唯一用 `removeAllListeners` 的 `common/utils/electronSerial.js` 是**零引用死代码**，同提交删除。
  4. `contextIsolation: true`、`nodeIntegration: false`。
  5. 删除 `bw-frontend/main.js:9` 的 `--ignore-certificate-errors`。
  6. **`webSecurity` 保持 `false`**，在 `electron/index.js` 就地写明原因（`file://` + AudioWorklet，见 §1.2）。
- **验收**：真实 `--dir` 包冷启动 —— 登录、训练页、**摩尔斯发音**、串口页、网络设置页、许可页全部可用；渲染进程 `typeof require === 'undefined'`；HTTPS 错误证书不再被静默接受。

### T5-3 发布校验（DELIVERY-04、05 部分）

- **现状**：`bw-frontend/scripts/artifact-manifest.cjs` 的 `verifyRelease` 在 **`:149-186`**（`:132-147` 是 `packageNative`），其 `:152-153` 对制品目录做**精确集合断言**（必须恰好等于 4 个目录），`:176-180` 已算出 `backendVersion`。`.github/workflows/build-quarkus-native.yml` 的 release job 在 `:211-260`，校验步骤在 `:245-250`，且已有 `checkout`（`:219-220`）。
- **目标**：
  1. release job 断言 tag 去 `refs/tags/v` 前缀后等于 `backend/pom.xml` 的 `version`，**并同时断言** `bw-frontend/frontend/package.json`（1.1.0）与 `bw-frontend/package.json`（**3.1.0**，deb 文件名用的是它）—— 三个版本号互不相干，只断言一个会出现「v1.1.0 的 tag 挂 3.1.0 的 deb」。
  2. 新增桌面 job：`needs: [build]`，下载 native 产物并 staging 为 `bin/server/server`（`chmod +x`），跑 `electron-builder --linux --dir`（**不出 deb**，见 §1.2），产出 manifest + SHA；`verifyRelease` 的 `expectedDirectories` 加 `desktop-linux` 并加对应 `verifyManifest` 分支（`component` 非 `frontend` 不会撞版本断言）。
  3. 桌面 job 只在 `startsWith(github.ref, 'refs/tags/')` 触发（PR/push 上跑桌面包代价过大：vite build + 下载 electron 运行时 + ~300MB 产物）。
- **验收**：本地构造 tag/pom 不一致场景被拒（需新写 Git fixture —— 仓内无可沿用的 `artifact-manifest.cjs` 测试手法，工作量计入）；workflow 静态检查通过；不触发真实发布。

### T5-4 串口链路容错（DELIVERY-06）

- **现状**（`bw-frontend/electron/serial/index.js`）：`:14-15` `_id:3` 从未写过时 `findOne.text` **先抛**（首次安装最常见场景，首版未覆盖）；`:5-24` 目标端口缺失时 `callback(selectedPort.portId)` TypeError；`:23` 非 linux 分支把**设备对象**当 portId 回传（Windows 同样坏）；`serial-port-added/removed` 每次注册 → 泄漏；`serial/nativeSerialPort.js:60-64,90-101` 列举串口时自动 `pkexec` 提权 + `chmod 666`，`grantAccess` 在 `exec` 的**异步回调**里同步调用且内部 `execSync` 失败（用户取消 pkexec / 无 polkit agent）会抛到无 catch 的回调栈 → **主进程 uncaughtException**。
- **重要事实**：`select-serial-port` 回调在桌面包里**永远不触发** —— 唯一调 `navigator.serial.requestPort()` 的 `WebSerial.js` 只被 `MessageWebSocket.connect()` 调用，而该调用被 `!ipcRenderer.isEE` 门住；桌面模式串口数据来自**仓外桥接程序** `ws://localhost:18765/echo`。因此「反复打开不累积监听」这条验收在桌面包上不可观察。
- **目标**：
  1. `_id:3` 缺失、端口缺失、非 linux 分支三处崩溃/错值修正；监听移到 `serialApiHandle` 顶层只注册一次。
  2. 取消列举串口时的自动 `pkexec`；新增显式 `controller.serialPort.grantAccess` 路由由**用户点击**触发，并给 `grantAccess` 加 try/catch（防主进程崩）+ `results.filter(Boolean)`。
  3. UI 信号：`_listLinuxPorts` 已为每口算了 `accessible`（`_checkPermission`）→ `NipSerial.vue` 对 `accessible===false` 显示「需要授权」+ 按钮调新路由，提示写明 `usermod -aG dialout` 需重新登录才生效。
- **验收**：首次安装（无 `_id:3`）打开串口页不崩溃；不存在的端口不崩溃且有提示；列举串口不弹提权框；点「需要授权」才弹；取消提权不导致主进程退出（直接调 handler 观察，不依赖不可达的 `select-serial-port`）。

### T5-5 死配置与硬编码地址清理（DELIVERY-07）

- **目标**：删 `bin/config.json` 的 `server`/`serial` 字段（`context.config` 全仓只被赋值从不读取）—— **但不得删除该文件**：`loadConfig` 把 `readFileSync(config.json)` 与 `Datastore.create` 放在同一个 try 里，文件缺失会走 catch 导致 `context.db` 永远是 `{}`，所有 ipc 处理器全挂。清理硬编码地址（`views/demo/chil/DivDemo.vue:10,13` 及另外三处，其中两处在**模块顶层**就建 WebSocket 且 `/demo` 路由已注册）。
- **验收**：全仓 grep 无 `10.0.0.217`/`10.10.0.210`/`10.10.0.117`；桌面包启动后 ipc 正常（证明 `config.json` 仍在）。

## 9. B7 长尾

| ID | 目标 | 验收 |
|---|---|---|
| **T7-1** DATA-01 | 新增幂等 `CREATE INDEX idx_post_telex_due ON t_post_telex_pat_train (status, deadline)` | `EXPLAIN` 显示 `findDueIds` 走该索引；演练双快照通过 |
| **T7-2** CONC-01 | `service/general/GeneralSettlementRecovery.java:16-36` 两处 `closingTrainIds()` 扫描加 `try/catch`（比照 `PostTelexPatTrainRecovery.java:31-45`） | 扫描抛异常时 `recover()` 不向外抛（测试直接调用断言） |
| **T7-3** DATA-03 | 本轮只做鉴权路径：`findUserEntityByToken` 的 24 处调用点改走 `UserService.getUserByToken`；其余裸 `firstResult()`（55 处 / 42 文件）登记后续 | 抽样端点在 token 失效时返回 203 而非 500 |
| **T7-4** DATA-02/04 | **不改文件名**（无迁移账本表，运维按文件名记录已执行脚本；改名会让已跑脚本以新名重现，并使 8 处已签收证据指向不存在的文件）。只把 `rehearse-migrations.sh:61-73` 的 `MIGRATIONS` 重排为字典序 + 在迁移清单文档写明同日重号的执行顺序；每个迁移补显式还原步骤 runbook | 演练数组顺序 = 字典序；runbook 覆盖全部脚本 |
| **T7-5** DATA-06 | general 收尾扫描改 `select id` 投影（`GeneralKeyPatService:617-619`、`GeneralTickerPatService:673-675`），不再每 5 秒加载含 `longtext` 的整实体。**删除首版的 `attempt` 注解项**（兄弟实体口径恰恰是不带该注解） | 扫描 SQL 不再 select 全实体 |
| **T7-6** DATA-07 | 把 `2026-09-11-03-post-telex-capture-clock.sql:100-111` 的跨域菜单 UPDATE 拆为独立数据迁移脚本 | 迁移清单标注其前端契约依赖 |
| **T7-7** CONTRACT-02 | `components/BroadcastTeachTrain/js/useBroadStudent.js:45,147,241` 与 `useBroadTeacher` 的 `res.msg`→`res.message`、`envelope.msg`→`envelope.data` | 真实页面可见后端 202/207 拒因文案 |
| **T7-8** CONTRACT-03 | 终态业务错误引入可机读标记（新增码或 error 分支细分，**不动 203/204/206**）；`common/mixin/useConfirmedSubmission.js:20-48` 对终态码提示不可重试并保留数据 | 训练已完成时提交显示不可重试；网络失败仍可重试 |
| **T7-9** FE-01/03 | 删热路径 `console.log`：`postJob/telegram/examTrain/js/examTrain.js:191,247,386`、`organization/electronKeyZuXun/.../handKeyTrain.js:209`、**`datagramZuXun/train/student/student.vue:249`**、**`telexZuXun/train/student/score.vue:311`**（首版漏后两处）；删死墙钟 `handKeyTrain.js:239`；删 `teacherBack.js` ×4、`common/utils/electronSerial.js`（零引用）、datagram/telex 学生页未调用的 `useControl` 导入 | 全仓 grep 无残留；前端构建通过；真实页面不回归 |

## 10. 数据库迁移要求

1. 幂等：先查 `information_schema` 再 DDL；重复执行 0 变更。
2. 唯一键/NOT NULL 变更 fail-closed：冲突 `SIGNAL 45000` 且保留数据。
3. 接入 `backend/scripts/rehearse-migrations.sh` 的 `MIGRATIONS`，对 `project006.sql`（105 表全 InnoDB）与 `project006-base.sql`（78 InnoDB + 22 MyISAM）**双快照演练**通过。
4. 实体注解与迁移列名/类型族/索引名逐列一致（`%prod` `validate`）。
5. **新增实体字段必须重新生成 `entity-schema.tsv`**（演练差分基准），否则演练不是通过而是 Missing 失败。
6. 迁移清单文档需同步脚本总数（本轮新增 T4-1、T7-1 各 1 个 + T7-6 拆 1 个）。

## 11. 验收门禁（DoD）

### 11.1 必须通过

```bash
cd backend && export JAVA_HOME=$HOME/.local/opt/jdk21 && ./mvnw -B clean verify
cd ../bw-frontend/frontend && npm run test && npm run build
cd ../../backend && ./scripts/rehearse-migrations.sh
```

- 后端当前基线 **317 测试 / 75 suite 全绿**；收口后 `>= 317 + 新增回归`，测试数只增不减（删除不合格用例须在提交正文论证「不丢覆盖」）。
- 前端 `npm run test` 当前 15/15，不减；`npm run build` 成功。

### 11.2 静态门禁

- `controller/` 非 `free/` 包的类带 `@JWT` = 100%（T1-2 架构测试保证）；
- `AESUtil` 生产调用 = 0（或正文列出保留原因）；
- 两个拦截器中 `getParam(TOKEN)` = 0；
- `UserSyncDto` 不含 `password`/`token`/`deviceId`；
- 非管理员可达 VO 不含 `idCard`/`phone`；
- `git show HEAD:bw-frontend/bin/nip.db` 不含真实业务 IP/COM 口；
- 前端 `res.msg`/`envelope.msg` = 0；
- `StartWebSocket`、`electronSerial.js`、`teacherBack.js` 全仓零残留。

### 11.3 运行态证据

| 批次 | 证据 |
|---|---|
| B1 | 越权请求实际响应码；文件服务穿越 404 + `ss -ltnp` 仅 127.0.0.1（在 `--dir` 产物上） |
| B2 | A 改 B 数据被拒且 DB 未变；任意 `score` 不影响入库；教员仍能阅卷 |
| B3 | 两次登录 token 不同、DB 存哈希、旧 token 失效；WS 冒充被拒且**路由房建房人仍可入房广播** |
| B4 | 四类结算断言 + `finish` 后原始采集行仍在 + 真实成绩页数值 |
| B5 | 真实 `--dir` 包冷启动（登录/训练/**发音**/串口/网络设置/许可全通）+ 渲染进程 `require` 不可见 |
| B7 | 真实页面拒因文案与不可重试提示；`EXPLAIN` 走索引 |

## 12. 提交约定

1. **一个 T 编号 = 一个提交**。同提交例外仅三种：跨栈两侧、重命名与其引用更新、修复与其回归测试。**不得**把不相关的 T 号并进同一提交。
2. `type(scope): 中文摘要`；正文写「为什么 + 验证方式」；跨栈改动贴前端调用面 grep 结果。
3. 每提交前跑受影响单测类；推送前跑 §11.1 全量。
4. 中间提交不得是坏态；必要时用独立 worktree 检出验证。
5. 不 `--amend`/`rebase` 已推送提交；不 `git add -A`。

## 13. 待产品/部署确认项（不阻塞其余任务）

| 事项 | 影响 | 默认处置 |
|---|---|---|
| T3-1 使存量会话全部失效 | 发布即触发 | 写入 runbook，与发布窗口一并通告；**不做双查兜底** |
| 组训数据报/电传域是否仍在用 | T4-1 走修复 or 整域下线 | 默认修复（代码活跃）；`t_menus` 无该域入口，若产品确认停用则改为下线 |
| T1-3 要求上分教员具备系统管理员角色 | 运营流程 | 库中无教员角色，只能如此；写入发布说明 |
| `cors.origins` 收敛 / `webSecurity` / `app://` 协议 | 一组耦合决策 | 本轮全部不动（§1.2），另立项 |
| 桌面文件服务跨机访问 | T1-4 监听地址 | 默认 `127.0.0.1` 但**可配置**，保留 UI 的「资源服务地址」能力 |
| 会话 TTL 时长与刷新策略 | Phase 9 | 本轮不实现（§1.2） |
