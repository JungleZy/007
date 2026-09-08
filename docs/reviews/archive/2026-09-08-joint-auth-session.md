# 鉴权 / 会话生命周期（前后端联合评审分片）
- 日期：2026-09-08 / 范围：登录→请求→过期→重登→登出 全链路跨栈契约（token 形态/有效期/deviceId 绑定、@JWT 校验、失效码、登出、服务端授权、WS 鉴权面）/ 方法：只读取证
- 编号前缀：`AS`。本片相对路径：前端以 `frontend/` 为根，后端以 `backend/src/main/java/com/nip/` 为根（少数给全路径）。

## 0. 分片结论与计数

| 定级 | 条数 | 责任分布(FE/BE/双侧) |
|---|---|---|
| J-P0 | 0 | — |
| J-P1 | 2 | FE 0 / BE 0 / 双侧 2 |
| J-P2 | 3 | FE 1 / BE 0 / 双侧 2 |
| J-P3 | 1 | FE 0 / BE 0 / 双侧 1 |
| 合计 | 6 | FE 1 / BE 0 / 双侧 5 |

（安全纯缺口按 context `[已接受风险口径]` 处理；越权可改他人数据/强制登出/幽灵会话按功能性后果正常定级。）

---

## 1. 契约清单（本分片覆盖的跨栈接口面）

### 1.1 会话生命周期时序表（每步两侧行为 + file:line）

| 阶段 | 前端行为（file:line） | 后端行为（file:line） |
|---|---|---|
| 登录 | `onLogin` 取 FingerprintJS 指纹作 deviceId（`views/manage/login/useLogin.js:66-71`），POST `/api/user/login`；成功后把 token/deviceId/userInfo/userRole/userRouter 全写 localStorage（`useLogin.js:122-126`） | `free/UserController.login` 校验账号/密码/状态（`service/UserService.java:386-402`），token=`AES.encrypt(account+"-"+password+"-"+deviceId, UKDAI_AES_KEY)`（`UserService.java:404`），写回**单行** `user.token`/`deviceId`（`:405-409`）；返回 role+menus |
| 请求 | 请求拦截器从 localStorage 注入 `token`+`deviceId` 头（`common/http/index.js:15-18`） | `JWTInterceptor` 校验 token/deviceId 非空 + `existsUserByTokenAndDeviceId`（`common/interceptor/JWTInterceptor.java:52-69`）；业务层 `getUserByToken` 由 token 派生 userId（`UserService.java:517-523`） |
| 过期/失效 | 响应码 206→Modal「账号已被异地登录」；203/204→Modal「登录唯一凭证异常」；205→「已过期」；点确定 `location.href='#/login'`（`common/http/index.js:30-68`）；**不关闭 WS、不调 userOut** | token+deviceId 查无 → **206**（`JWTInterceptor.java:67-68`）；或服务层 `getUserByToken` 查无 → 抛 `UnauthorizedException`→**203**（`UserService.java:520` + `common/exception/UnauthorizedException.java:4-5`）；**无 token 有效期字段、无续期/刷新端点** |
| 重登 | 守卫仅看 token 是否**存在**（`config/router/guards.js:23,31`），进入 login 路径时清 localStorage（`guards.js:11-16`）；autoLoginInfo 明文回填账号密码（`useLogin.js:14-20`）；205/206 分支**无 login 页抑制** | login 再次 AES 出**相同** token（确定性），覆盖单行 |
| 登出 | 「退出登录」按钮跳 `#/login`，守卫清 localStorage（`guards.js:11-16`）；**从不 POST `/api/user/userOut`**（`common/api/UserApi.js:34-36` 定义但全仓零调用）；不关闭 WS 单例 | `userOut` 端点可失效 token（置 null：`controller/UserController.java:50-55`→`UserService.java:438-449`），但从未被前端触发 → DB token 仍有效 |

### 1.2 五个高危写端点的服务端授权核对表

| # | 端点（file:line） | 有 @JWT | 校验角色/权限 | 前端门控 | 结论 |
|---|---|---|---|---|---|
| 1 | `GET /api/user/delete`（`controller/UserController.java:166-171`）| ✓ 类级(:31) | ✗（`UserService.delete:604-608` 无任何角色判定）| 菜单 userRouter(`guards.js:58-81`)+v-per(`config/directive/ButtonPermission.js:15-24`) 客户端软门控 | 任意登录用户删任意 userId → **越权改他人数据（J-P1）** |
| 2 | `GET /api/user/resetPassword`（`UserController.java:173-178`）| ✓ | ✗（`UserService.resetPassword:610-616` 重置为 `"123456"`）| 同上软门控 | 任意登录用户把他人（含管理员）密码重置为默认 → **账户接管（J-P1，最严重）** |
| 3 | `POST /api/user/addUserRole`（`UserController.java:79-86`）| ✓ | ✗（`UserService.addUserRole:348-372` 只重建关联，不判调用者角色）| 同上软门控 | 任意登录用户给自己/他人分配管理员角色 → **提权（J-P1）** |
| 4 | `POST /api/role/addRole`（`controller/RoleController.java:43-48`）| ✓ 类级(:31) | ✗（`RoleService.addRole` 无调用者角色判定）| 同上软门控 | 任意登录用户创建/编辑角色 → **提权链（J-P1）** |
| 5 | `POST /api/radiotelephone/finish`（结算，`controller/RadiotelephoneController.java:46-51`）| ✓ 类级(:26) | ✗ 无角色判定 | 页面门控 | **对照项**：userId 由 token 派生（`:49`→`getUserByToken`）→ 自限定，**不可越权改他人**；证明训练/结算写端点不受此越权面影响 |

补充对照：`POST /api/device/delete`（`controller/DeviceController.java:55-61`）类级**无 @JWT** → 完全无鉴权删除（纯安全缺口，`[已接受风险口径]`，已见 BE 单侧附录①，不重复计数）。

授权结论：全仓服务端授权 = **仅 @JWT 二值**（持有效 token 即放行）；`grep isAdmin|@RolesAllowed|hasPermission|checkPermission` 在 controller/service 层**零授权判定**（仅 `UserService.java:412` 用 isAdmin 决定登录返回哪套菜单、`RoleService.java:61` 用于默认角色业务，均非授权门）。前端软门控是 user/role 管理写端点的**唯一门槛**，直连 API 即绕过。

### 1.3 覆盖率说明

- 已查：登录（`free/UserController` + `UserService.login`）、token 形态/有效期/deviceId 绑定/多设备、`@JWT` 全实现、`getUserByToken` 33 处引用的过期行为、logout/失效端点、续期机制（不存在）；前端登录/守卫/拦截器/登出/自动登录/deviceId 来源；服务端授权抽样 5+1 端点；WS 握手鉴权面 4 个客户端。
- 未逐一：61 个 Controller 的 `@JWT` 覆盖清单（BE 单侧附录①已列，本片不重述），只抽样越权最敏感的 user/role/device 写端点。
- 交界：WS 连接的运行期行为（连接建立/角色伪造后果、重连风暴）属 `WebSocketContract` 分片，本片只记 WS **鉴权面**契约（是否携带/校验 token）。

---

## 2. 缺陷条目

### AS-J-P1-01 · 后端业务码 204(NULL_ERROR) 被前端拦截器判为鉴权失败 → 良性校验错误触发强制登出
- 结论一句话：`204` 一码两义（`设备标识缺失`=鉴权 / `请求参数为空`=业务），前端把**所有** 204 当鉴权失效弹窗并跳登录，于是任意返回 NULL_ERROR 的业务请求都会把已登录用户强制登出。
- 前端证据：`frontend/src/common/http/index.js:30`（`response.data.code === 203 || === 204` → destroyAll+「登录唯一凭证异常」Modal → `location.href='#/login'` :34-43）。
- 后端证据：`common/constants/ResponseCode.java:12,20`（`NULL_ERROR(204)` 与 `CODE_204(204)` 同码不同义）；业务侧发码点 `controller/UserController.java:67`（changePassword 缺字段→204）、`controller/PostTelegramTrainController.java:106`（dto 为空→204）。
- 触发条件 → 后果：用户在任一鉴权页触发一个 `NULL_ERROR`（如改密少填一项、训练保存传空体）→ 前端弹「登录凭证异常」→ 点确定跳 `#/login`，守卫清空全部会话态 → **无故被登出**，未提交的数据丢失。稳定可复现（特定路径）。
- 责任归属：**双侧协同（根因 BE）**。
- 最小修复：BE 把 `NULL_ERROR` 从 204 迁到一个非鉴权码（如 202/新码），使 203/204/206 纯为鉴权语义；前端相应只对纯鉴权码触发登出。二者需同步改（改码=契约变更）。

### AS-J-P1-02 · 前端软门控 + 后端零角色授权 → 任意登录用户越权管理用户/角色
- 结论一句话：后端对 user/role 管理写端点只做 `@JWT` 二值鉴权、无任何角色/权限判定，前端 v-per/菜单软门控是唯一门槛且可绕过 → 任意登录学员可删号、把他人（含管理员）密码重置为 `123456`、给自己分配管理员角色。
- 前端证据：`frontend/src/config/directive/ButtonPermission.js:15-24`（v-per 取 vuex permissions，客户端可篡改）、`frontend/src/config/router/guards.js:58-81`（路由由 localStorage `userRouter` 重建）。
- 后端证据：`controller/UserController.java:166-171`(delete)、`:173-178`(resetPassword→`UserService.java:614`)、`:79-86`(addUserRole)、`controller/RoleController.java:43-48`(addRole)——均类级 `@JWT` 但服务层无角色校验（`grep @RolesAllowed|isAdmin(授权用途)` 零命中）。
- 触发条件 → 后果：任一持有效 token 的用户（学员）直连这些端点（绕过 UI）即可越权改他人数据 / 提权 / 接管管理员账户。
- 责任归属：**双侧协同（根因 BE 缺服务端授权）**。前端软门控按负责人口径是「装饰」，不作安全边界。
- 最小修复：BE 在 user/role/menus 等管理写端点加服务端角色校验（如从 token→role 判 isAdmin，或引入 `@RolesAllowed`）；对 delete/resetPassword/addUserRole 增加「目标非自身需管理员」判定。前端软门控保持但明确非边界。

### AS-J-P2-01 · 前端 205 分支为死码 + 真实失效码 206 缺登录页抑制 → 重登后可重复弹窗、语义错标
- 结论一句话：前端处理 203/204/205/206 四码，但后端**从不发 205**（真实失效=206），205「已过期」弹窗是死码；且只有 203/204 分支有 login 页早返回抑制，206 分支没有，导致跳登录后 206 会再次弹窗，且 206 文案「异地登录」与真实「token 失效」语义不符。
- 前端证据：`frontend/src/common/http/index.js:30-33`（仅 203/204 有 `location.href.indexOf('login')>-1` 早返回）、`:45-56`（205 分支，永不触发）、`:57-68`（206 分支，无 login 页抑制）。
- 后端证据：`common/constants/ResponseCode.java:19-21`（无 205；失效码为 206）、`common/interceptor/JWTInterceptor.java:68`（查无 token+deviceId → 206）。
- 触发条件 → 后果：token 失效（如异地/清库）→ 206 弹窗→跳 login；此后任一仍在飞行或未拆除定时器/WS 触发的鉴权请求再拿 206，因 206 分支无 login 页抑制 → 在登录页**再次弹窗**（可反复）；205 分支永远显示不出，真实失效被错标为「异地登录」。
- 责任归属：**双侧协同（主 FE）**。
- 最小修复：FE 删 205 死分支，把 login 页早返回抑制统一套到 203/204/205/206 全部分支（或抽公共 handler 幂等化）；BE 若要区分「过期 vs 异地」再引入独立码并与 FE 同步。

### AS-J-P2-02 · 登出仅前端清 localStorage，不调用后端 userOut、不关闭 WS → 后端 token 仍有效 + 幽灵会话
- 结论一句话：登出=跳 `#/login` 由守卫清本地存储，从不 POST `/api/user/userOut`，也不关闭 WS 单例；后端 token 从未被失效（且确定性、无有效期）→ 服务端会话与 WS 连接成为幽灵。
- 前端证据：`frontend/src/config/router/guards.js:10-19`（进 login 仅清 localStorage）、`frontend/src/common/api/UserApi.js:34-36`（userOut 已定义但**全仓零调用**）。
- 后端证据：`controller/UserController.java:50-55` + `service/UserService.java:438-449`（userOut 存在且能置 token/deviceId 为 null）。
- 触发条件 → 后果：用户点退出后，DB 里的 token 仍有效直到下次登录覆盖；任何持有该 token（或 autoLoginInfo 明文凭据 `useLogin.js:14-20`）者可继续调用；未拆除的 WS 单例继续以旧身份运行（幽灵 WS 会话）。纯 token 有效性属 `[已接受风险口径]`；WS 未拆除+明文凭据保留属功能性/幽灵会话后果，正常计入。
- 责任归属：**FE**（后端能力齐备，缺的是前端调用与 WS 拆除）。
- 最小修复：FE 登出时先 `await userOut()` 再清本地、关闭全部 WS 单例、清 localforage autoLoginInfo；无需 BE 改动。

### AS-J-P2-03 · deviceId=客户端指纹 + 确定性 token + 无有效期/无刷新令牌 → 单会话不变量脆弱、明文存密码根因在 BE
- 结论一句话：deviceId 由前端 FingerprintJS 生成、非服务端签发；token=AES(account-password-deviceId,静态密钥) 为确定性、无有效期、无刷新端点；后端「单行覆盖」的单会话模型依赖 deviceId 唯一，在同镜像机器指纹碰撞时静默失效；因无刷新令牌，前端自动登录只能明文存密码。
- 前端证据：`frontend/src/views/manage/login/useLogin.js:66-71`（deviceId=`result.visitorId`）、`:122-123`（存 localStorage）、`:127-131`（autoLoginInfo 明文账号密码）。
- 后端证据：`service/UserService.java:404-406`（token=AES(确定性)+deviceId 绑定单行）；全类无 token 有效期字段、无 refresh 端点（`UserService`/`free/UserController` 无对应方法）。
- 触发条件 → 后果：①换设备→新指纹→覆盖单行→旧设备下次请求 206 被登出（预期）；②清缓存→重登→指纹稳定→**重算出同一 token**；③多开/同镜像机器→相同指纹→相同 deviceId+token→单会话驱逐（206）不触发，两端并存，单会话安全不变量失效；④无刷新令牌→前端自动登录只能落地明文密码。①③纯安全部分 `[已接受风险口径]`，但④的明文密码根因能力缺口归 BE。
- 责任归属：**双侧协同（能力缺口在 BE）**。
- 最小修复：BE 改为服务端签发随机会话 token（含有效期）+ 提供刷新端点，deviceId 作为绑定校验而非身份来源；FE 改存刷新令牌、不落地明文密码。

### AS-J-P3-01 · WS 握手不携带 token/deviceId，身份走客户端可伪造的 userId 路径参数，与 HTTP 会话完全解耦
- 结论一句话：前端 WS 连接 URL 只带 `userInfo.id` 路径参数、不带 token/deviceId，后端 WS 端点也不校验 token（见契约）→ WS 鉴权面与 HTTP token 完全脱钩：登出/失效不影响 WS，身份可伪造。
- 前端证据：`frontend/src/common/ws/Ws.js:18`（`${window.wsUrl}/websocket/${this.userInfo.id}`）、`frontend/src/views/manage/unionJob/js/UnionWs.js:41`（`.../websocketUnion/${this.userInfo.id}`）——无 token/deviceId。
- 后端证据：WS 端点不校验 token/deviceId（契约既定，如 `ws/WebSocketUnionService.java` 按路径参数取 userId；BE 单侧附录①亦述「8 个 WS 端点全部不校验 token/deviceId，路径参数可伪造身份含 role=1 冒充教员」）。
- 触发条件 → 后果：token 失效/登出后 WS 仍以旧 userId 运行；学员可用伪造 userId/role 路径参数连接。身份伪造属 `[已接受风险口径]`；「WS 不随会话失效而拆除」的幽灵连接后果与 AS-J-P2-02 呼应。
- 责任归属：**双侧协同**（鉴权面）；WS 运行期行为详见 `WebSocketContract` 分片。
- 最小修复：BE 在 `@OnOpen` 用握手参数校验 token+deviceId 后再建立会话；FE WS URL 带 token（或走鉴权子协议）。

---

## 3. 已核实为「一致/无问题」的关键契约

1. 请求头注入与后端读取一致：前端 `common/http/index.js:15-18` 注入 `token`/`deviceId`，后端 `JWTInterceptor.java:52-53` 读同名 header（`BaseConstants`）。
2. 登录成功码一致：后端成功码 200，前端 `useLogin.js:73` 以 `res.code===200` 判成功。
3. 训练/结算写端点自限定安全：`radiotelephone/finish` 等 userId 一律由 token 派生（`RadiotelephoneController.java:49`→`getUserByToken`），不接受客户端 userId → 训练数据**不可跨用户越权改**（与 §2 的 user/role 管理端点形成对照）。
4. 后端失效能力齐备：`userOut`（`UserController.java:50-55`→`UserService.java:442-444`）确实能置 token/deviceId 为 null——缺的是前端调用（AS-J-P2-02）。
5. 守卫在 token 缺失时确实跳登录：`guards.js:23-24,33-34`（仅「存在性」判定，非「有效性」，这一点的弱点已归 FE 单侧，不重报）。

---

## 4. 与单侧评审的定级变化

- FE 单侧 §3.4 HIGH「v-per 纯客户端授权」+ §2/§3.1「客户端信任锚，成立前提=后端强校验」→ **联合核实后端对 user/role 管理写端点零角色授权**，该「成立前提」不成立，组合构成真实越权：定级维持/升为 **AS-J-P1-02（J-P1）**，归属由「FE 单侧」改为「双侧协同（根因 BE）」。
- BE 单侧「ResponseCode 204 一码两义」（清洁度/响应口径）+ FE 单侧 §3.3「203/204 登录页 return undefined」→ 联合视角下业务 204 触发**强制登出**：从两处 nit 升为 **AS-J-P1-01（J-P1 强制登出）**，双侧协同。
- FE 单侧 §2/§3.4「登出从不调用后端失效 token」→ 联合确认后端 `/api/user/userOut` 存在且有效：归属**明确为 FE 单方缺失**（非 BE 缺能力），即 **AS-J-P2-02**。
- FE 单侧 HIGH「自动登录明文存密码」→ 联合确认后端无刷新令牌/无 token 有效期，前端无法改存刷新令牌：根因能力缺口补注为 **BE**（并入 AS-J-P2-03，双侧协同）。

---

## 5. 未能验证的部分（明确前提）

- 循环弹窗 / 静默失败的实测回答（依据静态代码）：
  - **循环弹窗**：构造上不会无限堆叠——四个失效码处理器都先 `Modal.destroyAll()` 再 `Modal.error()`，并发多个失效响应最终收敛为一个弹窗（`http/index.js:34,46,58`）。但存在**可重复弹窗缺口**：只有 203/204 分支有 login 页早返回抑制（`:31-33`），205/206 无（`:45-68`），而真实失效码是 206（`JWTInterceptor.java:68`）→ 跳 login 后任一仍在飞行/未拆除的鉴权请求再拿 206 都会在登录页再次弹窗。**分支不对称可从代码直接证**；「持续请求源导致反复弹窗」依赖未拆除 WS/定时器的运行期行为，标 `[INFERENCE]`。
  - **静默失败**：拦截器只特判 203/204/205/206，其余码（202 `PARAMS_ERROR`、500 `SYSTEM_ERROR`）直接 `return response.data`（`:69`），多数调用方只判 `res.code===200`、否则静默 no-op（FE 单侧 §3.3 MEDIUM）；且四个失效码分支后仍继续 `return response.data`，调用方 `.then` 会再拿失效 payload 处理一次。
- `[INFERENCE]` FingerprintJS visitorId 在同镜像/同硬件批量部署机器上的实际碰撞率——需实机比对，静态不可证；结论基于 FingerprintJS「浏览器/环境指纹」原理。
- `[INFERENCE]` 重登后是否真有「持续请求源」在登录页反复触发 206——取决于未拆除 WS/定时器的运行期行为。
- 只读评审未实跑 login/userOut/越权请求验证运行期返回码；后端单侧 §5 已实测「伪造 token→HTTP200+206」，可佐证 206 失效路径成立。
- WS 连接建立/角色伪造后果/重连风暴的运行期行为交由 `WebSocketContract` 分片，本片仅记 WS 鉴权面契约。

## 附录 A：结构化缺陷条目（6 条，子代理原始输出）

### [J-P1] 204(NULL_ERROR) 业务码被前端判为鉴权失败触发强制登出

- 锚点：`frontend/src/common/http/index.js:30-33`（置信度 0.85）

AS-J-P1-01。后端 204 一码两义:CODE_204(设备标识缺失,鉴权) 与 NULL_ERROR(请求参数为空,业务) 同码(ResponseCode.java:12,20)。业务发码点如 controller/UserController.java:67(改密缺字段)、PostTelegramTrainController.java:106(空 body) 返回 204。前端响应拦截器 frontend/src/common/http/index.js:30 把所有 204 与 203 一并当鉴权失效,destroyAll+弹「登录唯一凭证异常」并跳 #/login(:34-43),守卫清空全部会话态。触发:已登录用户在鉴权页触发任一 NULL_ERROR → 无故被强制登出、未提交数据丢失,稳定可复现。责任=双侧协同(根因 BE)。修复:BE 把 NULL_ERROR 迁出鉴权码段(如 202),FE 仅对纯鉴权码登出,需同步改。联合视角把 BE「204 一码两义」nit + FE「登录页 return undefined」nit 升级为 J-P1 强制登出。

### [J-P1] 前端软门控+后端零角色授权 → 任意登录用户越权管理用户/角色

- 锚点：`backend/src/main/java/com/nip/controller/UserController.java:166-178`（置信度 0.86）

AS-J-P1-02。后端对 user/role 管理写端点只做 @JWT 二值鉴权、无任何角色/权限判定(grep @RolesAllowed|isAdmin授权用途|hasPermission 零命中)。后端证据:controller/UserController.java:166-171(delete 任意 userId)、:173-178(resetPassword → UserService.java:614 重置为 "123456")、:79-86(addUserRole 自我提权)、controller/RoleController.java:43-48(addRole)。前端证据:config/directive/ButtonPermission.js:15-24(v-per 取 vuex permissions 可篡改)、config/router/guards.js:58-81(路由由 localStorage userRouter 重建)——均客户端软门控。触发:任一持有效 token 的学员直连 API(绕过 UI)即可越权改他人数据/提权/重置管理员密码接管账户。对照:radiotelephone/finish(RadiotelephoneController.java:49) userId 由 token 派生→自限定不可越权。责任=双侧协同(根因 BE 缺服务端授权)。修复:BE 在管理写端点加 token→role 角色校验及「非自身需管理员」判定。这推翻了 FE 单侧「后端强校验」的降级前提。

### [J-P2] 205 分支为死码 + 真实失效码 206 缺登录页抑制 → 重登后可重复弹窗

- 锚点：`frontend/src/common/http/index.js:57-66`（置信度 0.8）

AS-J-P2-01。前端处理 203/204/205/206 四码,但后端从不发 205(ResponseCode.java:19-21),真实失效=206(JWTInterceptor.java:68)。frontend/src/common/http/index.js 中仅 203/204 分支有 location.href.indexOf('login')>-1 早返回抑制(:31-33),205(:45-56 死码) 与 206(:57-68) 都没有。触发:token 失效→206 弹窗→跳 login;此后任一仍在飞行/未拆除定时器或 WS 触发的鉴权请求再拿 206 → 因 206 无 login 页抑制而在登录页再次弹窗(可反复);205「已过期」永远显示不出,真实失效被错标为「异地登录」。分支不对称可代码直证;「持续请求源导致反复弹窗」依赖未拆除 WS/定时器运行期行为,标 [INFERENCE]。责任=双侧协同(主 FE)。修复:FE 删 205 死分支,把 login 页早返回抑制统一到四码(或抽幂等 handler)。

### [J-P2] 登出仅前端清 localStorage,不调 userOut/不关 WS → 后端 token 仍有效+幽灵会话

- 锚点：`frontend/src/config/router/guards.js:10-19`（置信度 0.9）

AS-J-P2-02。登出=跳 #/login 由守卫清本地存储(config/router/guards.js:10-19),从不 POST /api/user/userOut(common/api/UserApi.js:34-36 定义但全仓零调用),也不关闭 WS 单例。后端 userOut 端点存在且能置 token/deviceId 为 null(controller/UserController.java:50-55 → service/UserService.java:438-449)。触发→后果:登出后 DB token 仍有效直到下次登录覆盖;持该 token 或 autoLoginInfo 明文凭据(useLogin.js:14-20)者可继续调用;未拆除 WS 单例继续以旧身份运行(幽灵 WS)。纯 token 有效性属 [已接受风险口径];WS 未拆除+明文凭据保留属幽灵会话功能性后果,正常计入。责任=FE(后端能力齐备,缺的是前端调用与 WS 拆除)。联合确认 userOut 存在→FE 单侧「登出不调后端」归属明确为 FE(非 BE 缺能力)。修复:FE 登出先 await userOut() 再清本地、关全部 WS、清 autoLoginInfo。

### [J-P2] deviceId=客户端指纹+确定性 token+无有效期/无刷新令牌 → 单会话不变量脆弱

- 锚点：`backend/src/main/java/com/nip/service/UserService.java:404-409`（置信度 0.75）

AS-J-P2-03。deviceId 由前端 FingerprintJS 生成非服务端签发(useLogin.js:66-71,存 :122-123);token=AES(account-password-deviceId,静态密钥) 为确定性、无有效期、无刷新端点(UserService.java:404-406,全类无 token 有效期字段/无 refresh)。后果:①换设备→新指纹→覆盖单行→旧设备 206 登出(预期);②清缓存→重登→指纹稳定→重算出同一 token;③多开/同镜像机器→相同指纹→相同 deviceId+token→单会话驱逐(206)不触发,安全不变量失效;④无刷新令牌→前端自动登录只能落地明文密码。①③纯安全部分 [已接受风险口径],但④明文密码根因能力缺口归 BE。责任=双侧协同(能力缺口在 BE)。修复:BE 改服务端签发随机会话 token+有效期+刷新端点,deviceId 作绑定校验而非身份来源;FE 改存刷新令牌。FingerprintJS 碰撞率标 [INFERENCE]。

### [J-P3] WS 握手不带 token/deviceId,身份走可伪造 userId 路径参,与 HTTP 会话解耦

- 锚点：`frontend/src/common/ws/Ws.js:17-18`（置信度 0.82）

AS-J-P3-01(鉴权面,行为详情属 WebSocketContract 分片)。前端 WS 连接 URL 只带 userInfo.id 路径参不带 token/deviceId:frontend/src/common/ws/Ws.js:18(${window.wsUrl}/websocket/${this.userInfo.id})、frontend/src/views/manage/unionJob/js/UnionWs.js:41(.../websocketUnion/${this.userInfo.id})。后端 WS 端点也不校验 token(契约既定,BE 单侧附录①:8 个 WS 端点全不校验 token/deviceId,路径参可伪造身份含 role=1 冒充教员)。后果:token 失效/登出后 WS 仍以旧 userId 运行;学员可伪造 userId/role 路径参连接。身份伪造属 [已接受风险口径];「WS 不随会话失效而拆除」的幽灵连接后果与 AS-J-P2-02 呼应。责任=双侧协同。修复:BE @OnOpen 用握手参校验 token+deviceId 后再建会话;FE WS URL 带 token。
