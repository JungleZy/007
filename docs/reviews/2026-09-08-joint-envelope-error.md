# 响应信封 / 业务码 / 错误语义（前后端联合评审分片）
- 日期：2026-09-08 / 范围：响应信封结构、业务码码表、错误语义（HTTP 状态 vs JSON `code`）跨栈契约 / 方法：只读取证（read/grep）
- 编号前缀：`EC`

## 0. 分片结论与计数

| 定级 | 条数 | 责任分布(FE/BE/双侧) |
|---|---|---|
| J-P0 | 0 | — |
| J-P1 | 1 | 双侧 1 |
| J-P2 | 1 | 双侧 1 |
| J-P3 | 1 | 双侧 1 |
| 合计 | 3 | 双侧 3 |

结论：本分片未发现 J-P0，但存在 1 条 J-P1（204 一码两义 → 认证端点业务「参数为空」把用户强制登出，稳定复现）。三条均为「联合视角」缺陷，责任均落在双侧协同（后端码语义 + 前端信封处理必须一起改才能根治）。

## 1. 契约清单（本分片覆盖面 + 覆盖率）

信封结构：后端恒 `Response<T>{code:int, data:T|null, message:String, description:String}`（`backend/.../common/response/Response.java:17-23`），成功 `code=200`，失败用其它码。前端共享 axios 拦截器把整包 `response.data` 作为 `res` 返回（`frontend/src/common/http/index.js:69`），调用方按 `res.code`/`res.data`/`res.message` 消费。

本分片核查的跨栈接口面：
1. 信封字段名与形态（`code/data/message/description`）—— 已核（两侧一致，见 §3）。
2. 全量业务码 200/202/203/204/205/206/500 的「后端产生点 → 前端处理点」映射 —— 已核（见 §4 矩阵）。
3. 未捕获异常最终形态（5 个 ExceptionMapper + GlobalExceptionMapper 兜底 + JWTInterceptor 拦截返回）—— 已核。
4. 前端调用方对 `res.code` 的判定习惯统计（判 vs 漏判比例）—— 已核（grep 计数 + 抽样，见 §2 缺陷 EC-J-P2-02）。

覆盖率说明：业务码码表 7 个码（`ResponseCode.java:10-21`）全部追踪到产生点与前端处理点。前端 176 个含 `.then(res` 的文件全部纳入判/漏判统计。**漏查/未证实**：token 过期时后端具体返回哪个码的确切路径（属 AuthSessionContract 面，见 §5，[INFERENCE]）；「返回 error 但事务已提交 → 重复/脏数据」的落库放大效应（属 TrainingFlow/DataModel 面，本分片仅在信封层证到「静默失败」，见 §5）；空集合 null vs [] 的逐端点数据形态（属 DataModelContract 面，本分片仅记录前端 `res.data.forEach` 无守卫的放大模式）。

## 2. 缺陷条目

### EC-J-P1-01 · 204 一码两义 → 认证端点业务「参数为空」把用户强制登出（稳定复现）
- **结论一句话**：后端把业务「参数为空」编码为 `204/NULL_ERROR`，而前端把**任何** 204 一律当鉴权失败弹「登录唯一凭证异常」并跳登录页；已登录用户在「修改密码」等表单留空一个字段即被强制登出。
- **前端证据**：`frontend/src/common/http/index.js:30-33`（`code===203||204` → `Modal.error` 标题「您的登录唯一凭证异常」→ `onOk` `location.href='#/login'`）；调用方 `frontend/src/components/personal/js/personal.js:172-178`（`changePassword(...).then(res => if(res.data)...else message.error(res.message)` —— 判的是 `res.data` 而非 `res.code`，且弹窗已由拦截器先触发）；表单 `frontend/src/components/personal/Personal.vue:34-56`（三个输入框无 `required`、`editPassword` 无客户端校验，可空提交）。
- **后端证据**：`backend/src/main/java/com/nip/common/constants/ResponseCode.java:12`（`NULL_ERROR(204,"请求参数为空")`）与 `:20`（`CODE_204(204,"设备标识不能为空")` —— **同码不同义**）；产生点 `backend/src/main/java/com/nip/controller/UserController.java:65-67`（`@JWT` 类 `:31`，`changePassword` 任一字段空 → `ResponseResult.error(ResponseCode.NULL_ERROR)`）；同类 `backend/src/main/java/com/nip/controller/PostTelegramTrainController.java:105-106`（`@JWT` 类 `:41`，`finish` 的 `dto==null` → 204）。
- **触发条件 → 后果**：已登录用户进入「个人中心 → 修改密码」，任一密码框留空点「修改密码」→ 后端 HTTP200 + `code:204` + `data:null` → 前端拦截器 `:30` 命中 → 弹「登录唯一凭证异常」→ 点确定跳 `#/login`，`token/deviceId` 虽未清但被踢回登录页。用户看到的是「凭证异常」而非「请填写完整」，且丢失当前操作。**稳定复现**（表单无任何客户端校验）。
- **责任归属**：`双侧协同`。根因是后端复用 204 表达业务「参数为空」+ 前端对 204 不区分语义。
- **最小修复**：后端把 `UserController.java:67`、`PostTelegramTrainController.java:106` 等业务「参数为空」改用 `PARAMS_ERROR(202)`（或新增专用业务码），令 204 仅保留给 `JWTInterceptor` 的鉴权语义（`deviceId 为空`）；前端 `index.js:30` 的 204→登出分支随之只覆盖真正的鉴权 204。两侧需同批改（后端先腾码，前端再收窄），否则任一单改都会漏码或误判。

### EC-J-P2-02 · 业务失败无集中处理 + 后端 500 双义 → 大量调用方漏判即静默 no-op / 未捕获 TypeError
- **结论一句话**：前端拦截器只集中处理 203/204/205/206，业务失败码（202、业务 500）原样透传；176 个含 `.then(res` 的文件里 92 个存在「取 `res.data` 却不判 `res.code`」的处理器，后端非 200 时静默无反馈或 `res.data.forEach` 抛未捕获 TypeError；且后端把**业务校验错**也编码为 `code:500`，与真实服务器 500 无法区分，前端无从写正确的集中默认分支。
- **前端证据**：`frontend/src/common/http/index.js:29-69`（拦截器仅 203/204/205/206 有分支，200/202/500 直接 `:69 return response.data`，无业务错误默认分支）。漏判抽样（`.then(res=>{` 下一行直取 `res.data` 且无 `res.code` 守卫）：`frontend/src/views/manage/basicTheory/study/basic/details/js/useDetails.js:79`（`res.data.knowledgeSwfs.forEach`）、`frontend/src/views/manage/fixedMessage/fixedMessageManage/js/index.js:204`（`res.data.forEach`）、`frontend/src/views/manage/equipment/equipmentList/Index.vue:89`（`euqipments.value = res.data`）、`frontend/src/components/cable/SelectCable.vue:108`（`pageData.value = res.data`）、`frontend/src/components/personal/js/personal.js:88`（`trainData.value = res.data`）、`frontend/src/views/manage/basicTheory/test/test/startGrade/js/startGrade.js:71`（`res.data.exam.state`）、`frontend/src/components/previewMessage/PreviewMessage.vue:108`（`res.data.messageBody`）。
- **后端证据**：业务校验错编码为 `code:500`（HTTP 200）：`backend/src/main/java/com/nip/common/exception/ValidationExceptionMapper.java:34-35`、`IllegalStateExceptionMapper.java:22-23`、`InvalidTitleExceptionMapper.java:24-25`（均 `Response.ok(ResponseResult.error(SYSTEM_ERROR, message,...))`）；真实未捕获异常则 HTTP 500 + `code:500`：`backend/src/main/java/com/nip/common/exception/GlobalExceptionMapper.java:22-25`（`Response.serverError()`）。业务 202 产生点示例：`backend/src/main/java/com/nip/controller/PostTelegramTrainController.java:108-109`。
- **触发条件 → 后果**：任一漏判处理器命中后端 202/业务 500（HTTP 200）→ `res.data` 为 null；若代码是 `res.data.forEach/map` 则抛未捕获 TypeError（整段处理静默中止、无 toast），若是 `x.value = res.data` 则赋 null（界面空白、无错误提示）。用户以为「什么也没发生」，可能重试。此外业务 500 与服务器 500 同码不同 HTTP 状态，前端即便加集中默认分支也无法用 `code` 区分「用户输入错」与「服务器炸了」。
- **责任归属**：`双侧协同`。
- **最小修复**：后端给业务校验错分配**独立业务码**（如统一 `202`），不再复用 `500/SYSTEM_ERROR`；前端在 `index.js` 拦截器加**一个集中的非 200 默认分支**（`res.code!==200 && ![203,204,206].includes(code)` → `message.error(res.message)`，带 opt-out），并对直取 `res.data.x` 的处理器补 `res.code===200` 守卫。后端不先腾码，前端集中分支会把用户校验错误显示成「服务器错误」。

### EC-J-P3-01 · 205 死分支：前端处理 205「登录已过期」，后端全仓零产生点
- **结论一句话**：前端为 `code:205`「您的登录唯一凭证已过期」写了完整弹窗分支，但后端码表无 205、全仓 grep 零命中，该分支永不触发；token 失效实际走 206「账号已被异地登录」，与「已过期」文案漂移。
- **前端证据**：`frontend/src/common/http/index.js:45-56`（`if(response.data.code===205){ Modal.error 标题「您的登录唯一凭证已过期」... }`）。
- **后端证据**：`grep '\b205\b|CODE_205'` 在 `backend/src/main/java` **零命中**；码表 `backend/src/main/java/com/nip/common/constants/ResponseCode.java:10-21` 无 205；鉴权码由 `JWTInterceptor.java:59/65/68` 只产 203/204/206；token 失效判定 `backend/src/main/java/com/nip/dao/UserDao.java:60-63`（`existsUserByTokenAndDeviceId` 记录不存在 → `:68` 抛 `CODE_206`）。
- **触发条件 → 后果**：205 分支不可达（死代码）；用户 token 失效/过期时收到 206 → 前端 `:57-68` 弹「账号已被异地登录」，与真实原因（凭证失效/过期）语义不符。功能不受阻，属清洁度 + 文案语义漂移。
- **责任归属**：`双侧协同`。
- **最小修复**：二选一——前端删除 `index.js:45-56` 的 205 分支（后端确无此码）；或后端在「过期」与「异地登录」两种失效路径分别产 205/206，前端文案随之区分。当前若只想清洁，删前端 205 分支即可。

## 3. 已核实为「一致/无问题」的关键契约
1. **信封字段名/形态一致**：后端 `Response.data`/`Response.message`（`Response.java:19/21`）↔ 前端 `res.data`/`res.message`（如 `frontend/src/components/dept/job/js/useJob.js:63 message.error(res.message)`）。`message` 键两侧同名，前端 toast 取键存在，无 `msg` 混用。
2. **200 成功语义一致**：`ResponseResult.success*`（`ResponseResult.java:12-22`）↔ 前端 122 个文件 `res.code===200`（grep）。
3. **203 鉴权一致**：`JWTInterceptor.java:59` / `UnauthorizedExceptionMapper.java:16-20`（HTTP200+code203）↔ `index.js:30` 登出流。
4. **204/206 鉴权路径一致**（对鉴权语义而言）：`JWTInterceptor.java:65/68` ↔ `index.js:30/57`。
5. **未捕获异常兜底一致**：`GlobalExceptionMapper.java:22-25`（HTTP 500）↔ 前端错误分支 `index.js:70-108`（`:87` status500 → `message.error('服务器内部错误!')` + reject）。
6. **内层错误码透传已修复**：`TheoryKnowledgeExamUserController.java:62-66` 现**显式透传**内层业务码（`if code!=SUCCESS return error(examResponse.getCode(),...)`），不再解包后重包 success。任务点名的「前端永远看不到失败」问题在当前源码**已不存在**（后端单侧评审已整改），本轮核实为无缺陷。

## 4. 业务码矩阵 + 与单侧评审的定级变化

### 4.1 业务码矩阵（码 → 后端产生点 → 前端处理 → 是否一致）

| code | 语义 | 后端产生点 (file:line) | 前端处理 (file:line) | 一致? |
|---|---|---|---|---|
| 200 | 成功 | `ResponseResult.java:12-22` success* | `index.js:69` 透传；调用方 `res.code===200`（122 文件） | ✅ 一致 |
| 202 | 请求参数错误 | `PostTelegramTrainController.java:108-109` 等 | **无集中分支**，`:69` 透传，漏判即静默 | ❌ 前端无 202 处理（EC-J-P2-02） |
| 203 | token 不能为空 | `JWTInterceptor.java:59`；`UnauthorizedExceptionMapper.java:18` | `index.js:30` 登出 modal | ✅ 一致（鉴权） |
| 204(a) | 请求参数为空(NULL_ERROR) | `UserController.java:67`；`PostTelegramTrainController.java:106`；`free/UserController.java:44` | `index.js:30` **登出 modal** | ❌ 业务错被当鉴权（EC-J-P1-01） |
| 204(b) | 设备标识不能为空(CODE_204) | `JWTInterceptor.java:65` | `index.js:30` 登出 modal | ✅ 一致（鉴权） |
| 205 | （前端）登录已过期 | **无产生点（grep 零命中）** | `index.js:45-56` modal | ❌ 死分支（EC-J-P3-01） |
| 206 | 账号登录凭证异常 | `JWTInterceptor.java:68`（`UserDao.java:60-63` 失配） | `index.js:57` modal「异地登录」 | ⚠️ 码一致、文案漂移（EC-J-P3-01 附注） |
| 500(业务) | 服务器错误(校验) | `ValidationExceptionMapper.java:35`；`IllegalStateExceptionMapper.java:23`；`InvalidTitleExceptionMapper.java:25`；`JWTInterceptor.java:75` | **无集中分支**，`:69` 透传（HTTP200） | ❌ 前端无业务 500 分支（EC-J-P2-02） |
| 500(异常) | 未捕获异常 | `GlobalExceptionMapper.java:22-25`（HTTP500） | `index.js:87` `message.error` + reject | ✅ 一致（走 HTTP error 分支） |

### 4.2 与单侧评审的定级变化
- **前端评审 `2026-09-08-frontend-review.md:230-232`（MEDIUM，信封无业务错误分支/调用方漏判）→ 本轮 EC-J-P2-02（J-P2，归属 FE→双侧）**：联合视角发现后端把业务校验错也编码为 `code:500`（`ValidationExceptionMapper.java:35` 等）与真实 500 同码不同 HTTP 状态，故前端**无法单侧**写出正确的集中默认分支——原「FE 加默认分支」的修复必须升级为**双侧协同**（后端先腾码）。
- **前端评审把 204 归为「鉴权码」（`frontend-review.md:231/306/524`）→ 本轮 EC-J-P1-01（J-P1，新增/升级，归属双侧）**：单侧评审未察觉 204 亦是业务码（`NULL_ERROR`）且由认证端点产出，联合视角揭示其后果为**强制登出**，定级从「信封瑕疵 MEDIUM」升为 J-P1。
- **前端评审把 205 列为已处理鉴权码（`frontend-review.md:283/355`）→ 本轮 EC-J-P3-01**：后端 grep 零 205，确认为**死分支**（单侧未标）。
- **登录页 203/204 `return undefined`（`frontend-review.md:231/306/524` 已列 MEDIUM）**：本轮核实登录端点 `free/UserController.java:44` 确会返 204（参数空），但登录表单 `useLogin.js:55-62` 有客户端校验拦空、`:84` 有 catch 兜底，且 203 在免鉴权登录页不可达，**联合视角未改变其定级/归属**，故不另立编号（仅此处备案，避免重报）。

## 5. 未能验证的部分（缺什么前提）
1. **token 过期 vs 异地登录的确切后端路径**：`existsUserByTokenAndDeviceId`（`UserDao.java:60-63`）仅判 token+deviceId 是否存在，未见显式「过期」分支；过期是否等价于记录被覆盖/删除从而返 206，属 AuthSessionContract 面，未逐路径证实（EC-J-P3-01 的「文案漂移」为 [INFERENCE]）。
2. **「返回 error 但事务已提交 → 重复/脏数据」放大效应**：`PostTelegramTrainService.finish` 现 `:525 throw e`（重抛），且 `remediation-verification.md RV-15` 记异常边界整改已落地，本分片在信封层**只能证到「静默失败」**（EC-J-P2-02），无法在本面证到「已落库」的数据后果；是否仍有「写库后 return error 不回滚」的活路径，需 TrainingFlow/DataModelContract 复核。故 EC-J-P2-02 定级未按「数据损坏」升级。
3. **空集合 null vs []**：后端各端点集合返回 null 还是 [] 的逐端点形态属 DataModelContract 面；本分片仅记录前端 `res.data.forEach`（如 `useDetails.js:79`、`fixedMessageManage/index.js:204`）无守卫，会与 EC-J-P2-02 叠加放大（后端返 null 时抛 TypeError）。

---
### 附：判/漏判统计方法与命中数（工具 = 内置 grep，Rust 正则；范围 frontend/src，未整读 vendored 大文件）
- 成功门禁 `res\.code === 200` → **122 文件 / ~266 行**；`res\.code == 200` → 26 文件 / 45 行；无空格 `res\.code===200` → 8；`res\.code==200` → 1；反向早退 `res\.code\s*!=\s*200` → 4。
- 分母 `\.then\(res` → **176 文件**；`\.then\(\(res` → 5 文件。
- 漏判反模式（`.then(res=>{` 紧邻下一行直取 `res.data` 且无 `res.code` 守卫，范围 views+components）→ **92 文件**。即约半数含响应处理器的文件至少有 1 处未判 code 直取 data。
- 抽样漏判点（file:line）：`useDetails.js:79`、`fixedMessageManage/index.js:204`、`equipment/equipmentList/Index.vue:89`、`cable/SelectCable.vue:108`、`personal.js:88`、`startGrade.js:71`、`previewMessage/PreviewMessage.vue:108`。
- 抽样已判点（file:line）：`login/useLogin.js:73`、`gradingRule/DatagramGardRule.vue:226`、`systemManage/structure/js/useStructure.js:24`、`dashboard/NipTop.vue:119`、`postJob/receive/train/js/trainScore.js:69`（反向 `!=200` 早退）。

## 附录 A：结构化缺陷条目（3 条，子代理原始输出）

### [J-P1] Stop treating business 204/NULL_ERROR as auth failure to avoid forced logout

- 锚点：`frontend/src/common/http/index.js:30-33`（置信度 0.9）

204 is a code-with-two-meanings: backend emits it both for auth (JWTInterceptor.java:65 CODE_204 "设备标识不能为空") AND for the business error "参数为空" (ResponseCode.java:12 NULL_ERROR). Authenticated endpoints return NULL_ERROR on empty params: UserController.java:65-67 (@JWT class :31, changePassword) and PostTelegramTrainController.java:105-106 (@JWT class :41, finish). The frontend interceptor at frontend/src/common/http/index.js:30-33 treats every code 204 as "登录唯一凭证异常" and redirects to #/login. The change-password form (Personal.vue:34-56) and its caller (personal.js:172-178, which checks res.data not res.code) have no client validation, so a user who leaves any password field blank gets force-logged-out instead of a "参数为空" hint. Stably reproducible. Fix (双侧协同): backend switch these business empties to PARAMS_ERROR(202) / a dedicated code so 204 stays auth-only; frontend then only logs out on the genuine auth 204. Per the joint rubric this borders J-P0 (forced logout, stable repro); scoped here as J-P1 because it is single-user, path-specific and recoverable.

### [J-P2] Add a central non-200 business-error branch and disambiguate backend code 500

- 锚点：`frontend/src/common/http/index.js:60-69`（置信度 0.85）

The interceptor (frontend/src/common/http/index.js:29-69) only handles 203/204/205/206; codes 200/202 and business-500 fall through to :69 `return response.data` with no default error handling. 92 of 176 `.then(res` handler files consume res.data with no res.code gate (samples: useDetails.js:79 `res.data.knowledgeSwfs.forEach`, fixedMessageManage/index.js:204 `res.data.forEach`, equipment/equipmentList/Index.vue:89, cable/SelectCable.vue:108, personal.js:88, startGrade.js:71, previewMessage/PreviewMessage.vue:108), so a non-200 envelope (data=null) either silently assigns null (blank UI, no toast) or throws an uncaught TypeError on `.forEach/.map`. Jointly, the backend encodes business validation errors as code 500 at HTTP 200 (ValidationExceptionMapper.java:34-35, IllegalStateExceptionMapper.java:22-23, InvalidTitleExceptionMapper.java:24-25), indistinguishable from the real server 500 that GlobalExceptionMapper.java:22-25 returns at HTTP 500 — so a naive FE "toast all non-200" default would mislabel user input errors as server errors. This escalates FE review item (frontend-review.md:230-232, MEDIUM) from FE-only to 双侧: backend must give validation errors a distinct code (e.g. 202) before the frontend can add a correct central branch.

### [J-P3] Remove the dead 205 interceptor branch the backend never emits

- 锚点：`frontend/src/common/http/index.js:45-56`（置信度 0.93）

The frontend interceptor handles code 205 with a full "您的登录唯一凭证已过期" modal at frontend/src/common/http/index.js:45-56, but the backend never emits 205: ResponseCode.java:10-21 has no 205 and `grep '\b205\b|CODE_205'` over backend/src/main/java returns zero matches. Auth codes come only from JWTInterceptor.java:59/65/68 (203/204/206). Token invalidation/expiry is detected by UserDao.java:60-63 (existsUserByTokenAndDeviceId → false) which yields CODE_206 → the frontend shows "账号已被异地登录" (:57-68), so the intended "已过期" UX is unreachable and expiry is mislabeled. Fix (双侧协同, pick one): delete the frontend 205 branch (:45-56), or have the backend emit 205 on expiry vs 206 on concurrent login. No functional breakage today, so J-P3.
