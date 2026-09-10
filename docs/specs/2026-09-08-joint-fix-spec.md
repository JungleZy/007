# 跨栈整改规格（Spec）

> **状态：可执行基线（2026-09-08）**。本文由当前代码、`docs/reviews/2026-09-08-full-project-review.md`（全项目权威入口）和 `docs/reviews/2026-09-08-joint-frontend-backend-review.md`（跨栈证据）整理。行号仅作定位，执行前以符号和当前源码复核。
>
> 既有后端整改的完成状态见 `docs/specs/archive/2026-09-07-fix-spec.md`、`docs/specs/archive/2026-09-08-deviation-fix-spec.md` 和 `docs/plans/archive/2026-09-07-fix-plan.md`。本文不把已确认修复项重新列为任务。

## 1. 目标与边界

### 1.1 目标

1. 关闭全项目评审已运行复现的 **P0**：匿名注册请求不得更新已有用户。
2. 恢复 P1 跨栈功能并建立服务端授权边界：管理 API 不能由普通登录用户调用；用户列表不能泄露会话凭据。
3. 逐条处置联合评审历史 41 条（原始统计：J-P1 7 / J-P2 19 / J-P3 15 / J-P0 0），并同时关闭全项目评审的 P0、敏感凭据暴露和交付缺口；撤回项不再伪装成已修复。
4. 保持既有契约红线：HTTP 业务错误仍为 HTTP 200 信封；203/204/206 的码值和文案不变；所有跨栈变更同步前端调用面。
5. 每个阶段都有可复现的后端测试、前端构建/静态门禁或浏览器/部署形态验收，不以“代码看起来合理”代替证据。
当前后端基线采用全项目评审 §9.1 的 `Tests run: 216, Failures: 0, Errors: 0, Skipped: 0`；整改收口必须保持全绿且测试数不少于 216，并为新增行为增加有价值的回归测试。

### 1.2 不在本 Spec 内重复实施

- 已在 2026-09-07/08 计划中落地的事务回滚、MyISAM 转 InnoDB、评分公共算法、分页基础守卫、WebSocket 单例状态、懒建并发唯一约束等。若新改动触碰这些路径，只做回归验证。
- 外部 Electron 主进程和仓外 nginx/Traefik 配置；仓内只定义交付接口和验证要求。
- 前端整体复制树重构、Pinia 迁移、依赖瘦身和测试框架建设；如需要另开 Spec。
- CORS 全开、WS 握手未鉴权、token 强度、明文自动登录等纯安全接受项，必须在终态表登记风险责任人；但 P0 匿名改数据、用户凭据泄露、匿名写删端点和管理越权属于数据/功能后果，不得用该豁免跳过。

### 1.3 当前代码事实（执行前置）

| 事实 | 当前证据 | 约束 |
|---|---|---|
| 匿名注册与管理更新共用 `UserService.addUser` | `backend/src/main/java/com/nip/controller/free/UserController.java:49-53`；`backend/src/main/java/com/nip/service/UserService.java:155-175,284-309` | `/signin` 必须强制新建语义；管理更新走受保护端点 |
| `UserEntity` 含 password/token/deviceId | `backend/src/main/java/com/nip/entity/UserEntity.java:40-76`；`backend/src/main/java/com/nip/controller/UserController.java:88-93` | 响应必须使用不含凭据的 DTO，不能直接返回实体 |
| `@JWT` 只校验 token/deviceId 存在性 | `backend/src/main/java/com/nip/common/interceptor/JWTInterceptor.java:52-69` | 角色授权必须另设服务端判定 |
| `RoleDao.findRoleByUserId` 无角色抛 `NoResultException` | `backend/src/main/java/com/nip/dao/RoleDao.java:14-18` | 授权逻辑将无角色视为无权限，不能逸出成 HTTP 500 |
| 业务 204 与鉴权 204 同码 | `backend/src/main/java/com/nip/common/constants/ResponseCode.java:10-21`；`backend/src/main/java/com/nip/controller/UserController.java:65-69` | 业务空参迁到 202；203/204/206 保持原值/文案 |
| GET 包装器已把 data 转成 params | `frontend/src/common/http/axios.js:11-24` | 不做全量 `data→params` 改写；只修真实键名、谓词、URL 或调用域 |
| 前端 lockfile 当前存在但被忽略 | `frontend/package-lock.json`；`frontend/.gitignore:8` | 解除忽略并确认 Git 跟踪，再让 CI 使用 `npm ci` |
| 尾空格“必然 404”已被全项目复核撤回 | `docs/reviews/2026-09-08-full-project-review.md` §8.2、§9.5；源码 `frontend/src/common/api/TestApi.js:58-63` | 不再按 P1 计数；清理可作为接触该文件时的低风险卫生改动 |

## 2. 缺陷清单与终态要求

联合评审原始统计为 41 条；其中编号在分片中已合并/撤回，不能把历史总数直接当当前待修数量。下表是当前代码基线的逐组处置表，`计划批次` 对应第 4 节和实施计划；每组最终必须登记完成、接受风险或另立 Spec。

| 编号 | 定级 | 现象/根因 | 计划批次 | 终态 |
|---|---|---|---:|---|
| P0-01 | P0 | `/api/user/signin` 接受客户端非空 id，进入 `handleExistingUser` 更新任意用户 | 0 | 匿名带已有 id 明确返回非成功且不改变任何已有行；无 id 正常注册仍成功 |
| BE-P1-01 | P1 | `getAllUser` 等路径直接返回 `UserEntity`，泄露 password/token/deviceId | 1 | 所有用户查询响应均为脱敏 DTO；OpenAPI 与实际响应不含三类字段 |
| AS-J-P1-02 | J-P1 | user/role 管理端点只有 `@JWT`，无角色授权 | 1 | 普通用户/无角色得到 207 且状态不变；管理员成功 |
| HC-J-P1-01 | J-P1 | 前端仍传 `roomgId`，后端读 `roomId` | 2 | 房间详情请求统一 `roomId` 且成功 |
| EC-J-P1-01 | J-P1 | 业务 NULL_ERROR=204 被前端当作凭证失效 | 3 | 业务空参=202；203/204/206 仅用于鉴权；不因校验错误登出 |
| TK-J-P1-01 | J-P1 | 上传 UI 接受 doc/docx/pptx，后端仅处理 txt/md/csv，空数组又被当真值 | 4 | 前端能力提示、后端能力、空值分支一致 |
| DC-J-P1-01 | J-P1 | HTTPS+反代时手工 `http://`/`ws://` 拼接产生畸形地址 | 5 | HTTP、SSE、上传、导出、WS 均使用协议感知入口 |
| DM-J-P1-01 | J-P1 | 自测列表读取 `startTime`，实际 JSON 为 `start_time` | 2 | 时间显示和排序使用真实 wire 字段 |
| EC-J-P2-02 | J-P2 | 业务错误无集中提示；前端大量消费点不判 code；后端校验异常与服务器错误同为业务 500 | 3 | 校验错误=202、集中提示可 opt-out、调用方不再假成功 |
| TF-J-P2-01/02 | J-P2 | 电传组训打电子键域；电子键 reset 打手键域 | 6 | 各训练域 API/WS/reset 与数据库域一致 |
| AS-J-P2-02 | J-P2 | 登出只清本地状态，不调用 userOut、不关 WS | 7 | 先失效服务端会话，再清本地并销毁 WS |
| AS-J-P2-01 | J-P2 | 前端 205 死分支，206 缺登录页抑制 | 3 | 认证失败处理幂等，返回信封不为 undefined |
| TK-J-P2-01/02 | J-P2 | `exportTemplate`、`saveBatch` 后端已存在但前端孤儿；导入逐行 fire-and-forget | 4 | 一次批量提交、失败可见、导出格式与后端列规格一致 |
| TK-J-P2-03 | J-P2 | 富文本多个 sink 原样渲染，后端无净化 | 8 | 渲染侧统一白名单净化；危险标签/属性/scheme 不执行 |
| DC-J-P2-01 | J-P2 | 上传大小在前端、后端、反代没有共同上限 | 5 | BE/FE 文档化同一上限；超限有可理解提示；反代要求标为外部前置 |
| AS-J-P2-03 | J-P2 | 确定性 token、客户端指纹、无有效期/刷新，自动登录需存明文密码 | 9 | 形成独立会话设计决策；若本轮不迁移，记录接受风险和后续前置，不伪称已修 |
| TF-J-P2-03/04/05/06 | J-P2 | 评分双实现、ticker 算法缺陷、finish 竞态、断点取本地缓存/错误 getItem | 10 | 与既有后端算法整改对账；只保留权威结果；重复 finish/续训边界有证据 |
| WS-J-P2-01/02/05 | J-P2 | WS 重连风暴、心跳/发送状态/清理不完整、握手身份与 HTTP 会话脱钩 | 7 | 功能性生命周期完成；纯握手安全项按接受风险或另立项 |
| WS-J-P3-03/04 | J-P3 | 死代码 teacherBack、无前端调用者的 `/status` 与 `/startWebsocket` | 10 | references 为零才删除，否则记录外部消费者 |
| BE-P2-01 | P2 | 多个写/删端点缺 `@JWT`（如 Cable、CableType、Device） | 1 | 穷举 controller 写端点；匿名请求不得改删数据，逐项记录需保留的外部/内部例外 |
| BE-P2-02 | P2 | token/deviceId 允许 query 传递，易进日志/历史 | 9 | 迁移所有调用到 header；兼容删除前完成客户端 grep 与部署观察 |
| DC-J-P3-01/02/03 | J-P3 | 版本号三分裂、CI 无前端、地址配置多真源 | 5 | 版本/CI/交付链有单一规则和可复现输出 |
| DM-J-P2/P3 | J-P2/P3 | 时区、数值 wire 类型、snake/camel、字典/时间形态分裂 | 8/10 | 逐字段给已处理或判定不修理由，禁止泛化改全站 |
| HC-J-P3-04/05/06 | J-P3 | 死导出方法、孤儿 API、谓词/端点不匹配 | 2 | 零活跃调用者后删除；删除前以 LSP references 证明 |
| BE-P3-01 | P3 | 生产 `/q/openapi` 可访问，暴露端点/DTO 信息 | 10 | 明确关闭、网络限制或接受人；实际部署验证，不以 Swagger UI 404 代替 |
| 其余单侧 P3 | P3 | CORS、Tauri/死依赖、状态重复等 | 10 | 记录接受/另开 Spec，不混入 P0/P1 交付门禁 |

**明确撤回：** `HC-J-P1-02`（`deleteThroyKnowledgeById` 尾空格“必然 404”）不是当前确定性 P1。源码仍可顺手去空格，但 DoD 不以“零命中 + 删除成功”宣称关闭一个已撤回缺陷；若实测真实客户端仍失败，必须重新提交独立证据和编号。

## 3. 不可破坏的契约与设计决策

1. **HTTP**：业务成功/失败仍由 `Response<T>` 信封表达；只有未捕获异常保持现有 HTTP 500 兜底。
2. **鉴权码**：203 `token不能为空`、204 `设备标识不能为空`、206 `账号登录凭证异常` 的码值和文案逐字不动。业务空参数使用 202。
3. **授权**：建议使用 `@RequireAdmin` CDI interceptor。不要只取多角色关联中的任意第一条记录；应以 token→user 后查询“是否存在 `isAdmin == 0` 的角色”。无角色或没有超管角色均拒绝，`NoResultException` 必须转成 207，不得 HTTP 500。
4. **自限定修改密码**：从 token 推导当前用户 id；必须删除“id 为空即参数错误”的前置要求，否则“忽略 body id”仍无法工作。前端多传旧 `userId` 可暂时兼容但服务端不得使用。
5. **脱敏**：禁止把 JPA 实体作为用户管理响应。列表/详情 DTO 不含 password、token、deviceId；登录/注册使用单独 DTO，登录 token/deviceId 仅作为明确的会话字段返回。
6. **上传**：本轮选择后端现有纯文本能力，不偷偷恢复 Office 支持；若产品必须支持 Office，另开能力 Spec，不能只改 accept。
7. **错误拦截**：前端响应拦截器对 203/204/206 做幂等认证处理，对其它 `code !== 200` 做默认提示并支持 opt-out；任何分支都返回 `response.data`，不能裸 `return`。
8. **计分**：后端结果是权威；前端值只能标为预估，不能上传一套与后端含义不同的最终值。
9. **迁移与部署**：任何实体/唯一索引/配置变更同步迁移、README、CI 或部署前置检查；生产 `generation: validate` 是硬约束。

### 3.1 已落地后端整改边界

以下项目在 `docs/plans/archive/2026-09-07-fix-plan.md` 的当前执行记录中已落地；本计划只做回归，不重复实现：事务异常回滚、MyISAM→InnoDB、`ScoreMath` 评分收口、分页 `[1,200]` 钳制、后端房间参数统一到 `ROOM_ID`、文档上传/`saveBatch`/`exportTemplate` 后端端点、WebSocket 单例状态收口、边界守卫和读路径懒建唯一约束。当前仍存在的 `roomgId` 是前端调用面回归，不是后端再次改名。

### 3.2 共享文件串行所有权

| 文件 | 触及阶段 | 规则 |
|---|---|---|
| `frontend/src/views/manage/basicTheory/test/questionBank/js/knowledgeTabel.js` | 文件题库、地址配置 | 文件题库先改 `saveBatch`/模板，再由地址阶段接力；不得并行编辑 |
| `frontend/src/views/manage/basicTheory/study/basic/edit/Index.vue` | 文件题库、地址配置 | accept/响应分支先完成，地址/SSE 后接力 |
| `frontend/src/views/manage/equipment/equipmentIndex.vue` | 文件题库、地址配置、富文本 | 单一集成者按 文件→地址→净化 顺序落地 |
| `frontend/src/common/http/index.js` | 错误拦截器、timeout | 先完成错误码骨架，再补 timeout/网络提示 |
| `frontend/src/views/manage/organization/telexZuXun/list/js/list.js` | 分页、错域 | 分页与错域合并同一提交，避免覆盖 |

并行任务只允许修改各自独占文件；跨栈接口和回归测试必须同提交。

## 4. 批次与任务要求

### 批 0：P0 匿名注册止血

- 将 `/api/user/signin` 与管理编辑路径分离；注册入口明确拒绝非空 id（不采用清空 id 后悄悄创建新用户），服务层不得根据客户端 id 进入更新分支。
- 在已有 ID、已有账号、正常注册三种场景写 REST/DB 回归测试。匿名攻击请求必须 HTTP 200 业务失败或明确拒绝，目标行所有身份字段不变；注册成功响应也不得回传实体敏感字段。
- 账户/身份证唯一性先盘点存量和迁移可行性；不得未经数据清理直接加约束。

### 批 1：服务端授权与敏感数据

- 建 `@RequireAdmin` 和 mapper/业务码 207；给 8 个目标管理方法逐方法加保护。多角色授权必须查询是否存在 `isAdmin == 0`，不能依赖第一条角色。
- `changePassword` 从 token 推导 id，保留旧密码校验；A token 携 B id 不得修改 B。
- 枚举所有直接返回 `UserEntity` 的用户查询，改脱敏 DTO；为登录/注册定义单独会话响应，为选学员/教员的调用提供最小 `UserSummary`，不能简单把 `getAllUser` 限为管理员而破坏训练建训。
- 清点 `CableController`、`CableTypeController`、`DeviceController` 等无 `@JWT` 写/删端点：补鉴权并做匿名不变测试，或逐项记录外部消费者并路由到专门后端 Spec。

### 批 2：前端活跃契约对账

- `roomgId`→`roomId`；修 `start_time` 读取/排序；清理确认无调用者的死导出/死 import；分页不再传 `rows:999`。
- 每项修改先 grep API 定义和调用点，再浏览器 Network 验收。禁止全量改 GET data。

### 批 3：错误码/信封（严格 BE→FE）

- BE 先将 3 个 `NULL_ERROR` 业务产生点迁到 202，并将三个校验 Mapper 的业务码从 500 改 202；保持 safeMessage、HTTP 200 和 GlobalExceptionMapper 的真实 HTTP 500。
- FE 再收口认证处理、删除 205、统一登录页抑制、集中业务错误 toast 和假成功点；保留 `skipErrorToast` 的明确调用理由。
- 用测试覆盖：业务空参≠204、鉴权缺 deviceId=204、校验异常 HTTP200/code202、未预期异常 HTTP500/code500、响应永远不为 undefined。

### 批 4：文档/题库导入

- 前端 accept 对齐 txt/md/csv，空数组用 `Array.isArray && length`；错误响应先判 code/data。
- 题库导入解析后一次调用 `saveBatch`，按返回结果提示，删除定时器假成功；模板调用 JSON 列规格并用已有 xlsx 能力生成可打开文件。
- 若当前后端 `exportTemplate` 返回的是列规格而非字节流，前端不得使用 blob/docx 文件名伪装。

### 批 5：协议地址、体积、CI

- 建协议感知 `apiUrl`/`wsUrl` 入口，迁移上传/SSE/导出/协同 WS 的手工拼接；以 `window.httpUrl` 实际注入值为测试矩阵输入。
- 写定后端 body 上限、前端预检和真实 timeout；反代限额缺失时标 `[外部前置]`，不能声称仓库已完成。
- 解除 lockfile 忽略、CI 用 `npm ci && npm run build`，归档前端 dist 或明确外壳消费步骤；版本号只选一个真源并记录迁移规则。

### 批 6：训练域与结算边界

- telexZuXun 全部改为 datagram/generalTelexPat API/WS，修 localStorage 域键；为 generalKeyPat 增本域 reset，并测试不影响 ticker。
- 对照既有评分公共实现，删除/标注前端最终评分上传；finish 立即提交守卫与后端幂等短路；断点优先读取后端权威进度。

### 批 7：会话与 WebSocket 生命周期

- 登出顺序：调用 userOut（失败也执行本地清理并记录）、关闭/销毁全部 socket、清理 localforage，再跳登录。
OpenAPI、query token、匿名写删、死端点、重复导出、Tauri 残留等逐条使用 LSP references 和运行证据决定实施/保留/接受/另立 Spec；不得默认归入历史计划。
与 2026-09-07 计划重叠的已修复条目只做回归，不产生第二套实现；其余全项目评审新增项必须在终态表有去向。

### 批 8：富文本与数据表示

- 在 `v-html` 和 iframe/document.write 前建立唯一净化入口，白名单覆盖标签、属性、URL scheme；CSP/iframe 隔离作为部署要求。
- 时区、数值类型、snake/camel、字典按字段清单逐项决定；每项写“已处理/判定不修（理由）”，不进行无证据的全站重命名。

### 批 9：会话凭据与密码迁移设计

- 先输出 token/hash 版本、refresh/注销、存量迁移、失败回滚和 Electron/Web 存储边界设计；经产品/部署确认后再编码。
- 若本轮没有安全存储和兼容登录前提，不得把 MD5、明文自动登录或 WS 握手项标为已关闭，只标“设计完成/实施阻塞”。

### 批 10：长尾收口

- OpenAPI/CORS、死端点、重复导出、Tauri 残留等逐条使用 LSP references 和运行证据决定删除/保留。
- 与 2026-09-07 计划重叠的已修复条目只做回归，不产生第二套实现。

## 5. 验收门禁（DoD）

### 5.1 必须通过

```bash
# 后端
cd backend
export JAVA_HOME=$HOME/.local/opt/jdk21
./mvnw -B clean verify

# 前端
cd ../frontend
npm ci
npm run build
```

静态门禁使用仓库内置 grep/脚本等价实现，至少包括：

- `roomgId` 活跃调用为 0；
- 管理端点 `@RequireAdmin` 覆盖 8 个目标方法；
- `NULL_ERROR` 业务产生点为 0，203/204/206 定义逐字未变；
- telexZuXun 不引用 electronKey/handkey API，WS 不连 `generalKeyPatTrain`；
- 协议感知迁移后无 `'http://' + window.httpUrl` 等手工拼接；
- 导入不再逐行 fire-and-forget、模板死导出无活跃引用；
- 生产配置 Secret、CI 前端构建和 lockfile 状态可由文件证据复核。

### 5.2 行为验收

1. 匿名 `/signin` 带已有 id：响应为业务失败，数据库目标用户不变；正常注册创建新行。
2. 普通用户、无角色调用八个管理端点：code=207，目标数据不变；管理员调用成功。
3. A token 携 B id 改密码：只允许自限定语义，B 密码不变。
4. 修改密码漏字段：code=202，不弹认证失效、不跳登录；缺 deviceId 仍 code=204。
5. 浏览器验证房间 `?roomId=...`、文本导入、题库批量导入、xlsx 模板下载、telex 建训、登出旧 token 失效。
6. HTTP、HTTPS+反代、Electron 直连三种地址形态分别验证上传/SSE/WS；反代缺配置的结果单独标注。
7. WS 重连、重复关闭、坏消息、finish 双触发和断点续训均有可观察日志/协议/DB 证据。

### 5.3 交付记录

- `docs/plans/2026-09-08-joint-fix-plan.md` 每任务写状态、实际证据、偏离原因；未实施项不能写“完成”。
- 联合评审 §3 必修表逐行关联到本 Spec 批次；撤回的尾空格项保持撤回说明。
- 任何跨栈参数、返回形态、业务码变更必须在同一提交同步两侧，并在提交说明记录调用面 grep 结果。
- 每个独立任务可单独回滚；修复与回归测试同提交。历史无法满足“每 Task 一个 commit”的部分，不追溯伪造。
