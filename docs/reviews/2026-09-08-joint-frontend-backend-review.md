# 前后端联合评审总报告（joint review）

> 结论：**联合视角确认 41 条跨栈契约缺陷（J-P1 7 / J-P2 19 / J-P3 15，J-P0 0）**。最高风险不是任何单侧代码，而是**「后端按自己的单侧评审改，未对照前端调用面」造成的契约回归**：至少 4 处后端整改把原本前后端一致（或一致地坏）的契约改成了单侧正确、跨栈失效。第二大风险是**授权只存在于前端**——后端管理写端点只做 token 二值鉴权，任意已登录学员可删除任意用户、重置管理员密码、给自己加角色。
>
> ⚠️ **2026-09-08 勘误（见 §5.0）**：初版曾把「前端 GET 用 `data` 传参」判为 J-P1（26 个端点恒丢参）并据此派生「菜单/角色回显恒 500」，**两条均已撤回**——前端 28 个 api 模块走的是包装器 `frontend/src/common/http/axios.js:20-24`，它对 GET 走 `instance.get(url, { params: data })`，参数正常进查询串。同时更正：`IllegalArgumentException` 由 `ValidationExceptionMapper` 映射为 **HTTP 200 + 业务码 500 + 可读 message**，全仓只有 `GlobalExceptionMapper` 返 HTTP 500。

| 项目 | 内容 |
|---|---|
| 审查对象 | 单仓双工程 `HEAD 0d3bdea`：`backend/`（Quarkus 3.20.4 / Java 21，750 源文件）+ `frontend/`（Vue 3.5 + Vite，265 `.vue` / 282 `.js`） |
| 审查范围 | **只审跨栈契约面**：HTTP 端点、响应信封与业务码、鉴权/会话生命周期、WebSocket 协议、训练主业务流、文件与富文本传输、部署形态、数据表示。单侧内部缺陷不重报 |
| 审查日期 | 2026-09-08 |
| 审查方式 | 8 个只读评审子代理并行分片取证（各自一份分片报告），父代理负责去重、改级、独立复核与汇总；父代理另做 1 次浏览器运行期实证（§5.1） |
| 单侧基线 | 当前全项目评审 `docs/reviews/2026-09-08-full-project-review.md`；此前单侧报告和分片已移入 `docs/reviews/archive/`，仅作历史证据 |
| 定级口径 | J-P0 契约破裂已致功能整体不可用/跨用户串号/永久数据损坏；J-P1 特定路径静默失效、错误数据展示或落库、假成功；J-P2 契约脆弱（依赖巧合，一侧小改即炸）；J-P3 清洁度。内网部署，**纯安全缺口为已接受风险**，仅在同时造成功能性/数据性后果时正常计级 |
| 运行验证 | 未启动后端（分片纯静态取证）。父代理用真实 Chromium 实测了「XHR `GET` 请求体被浏览器丢弃」（结论成立，但**不适用**本项目：api 层包装器已把 GET 的 `data` 转成 `params`，见 §5.0 勘误）；其余运行期后果为代码路径推断 |

详细分片已归档至 `docs/reviews/archive/`，不再作为顶层阅读入口：
`2026-09-08-joint-http-contract.md` · `2026-09-08-joint-envelope-error.md` · `2026-09-08-joint-auth-session.md` · `2026-09-08-joint-websocket.md` ·
`2026-09-08-joint-training-flow.md` · `2026-09-08-joint-theory-file.md` · `2026-09-08-joint-deploy-config.md` · `2026-09-08-joint-datamodel.md`

---

## 1. 分片索引与计数

| 分片 | 前缀 | J-P1 | J-P2 | J-P3 | 小计 | 责任分布 (FE/BE/双侧) |
|---|---|:--:|:--:|:--:|:--:|---|
| HTTP 端点契约对账 | `HC` | 2 | 1 | 3 | 6 | 5 / 0 / 1 |
| 响应信封与业务码 | `EC` | 1 | 1 | 1 | 3 | 0 / 0 / 3 |
| 鉴权与会话生命周期 | `AS` | 2 | 3 | 1 | 6 | 1 / 0 / 5 |
| WebSocket 协议 | `WS` | 0 | 3 | 2 | 5 | 1 / 1 / 3 |
| 训练/拍发/评分主流程 | `TF` | 0 | 6 | 2 | 8 | 2 / 1 / 5 |
| 理论考试/文件/富文本 | `TK` | 1 | 3 | 1 | 5 | 1 / 0 / 4 |
| 部署/配置/网络形态 | `DC` | 1 | 1 | 3 | 5 | 2 / 0 / 3 |
| 数据模型与表示 | `DM` | 1 | 2 | 3 | 6 | 1 / 1 / 4 |
| **分片原始合计** | | **8** | **20** | **16** | **44** | 13 / 3 / 28 |
| 去重（3 处，见 §1.1） | | −1 | −1 | −1 | **−3** | |
| 父代理新增 | | 0 | 0 | 0 | **0** | |
| **本汇总最终** | | **7** | **19** | **15** | **41** | 13 / 3 / 25 |

### 1.1 去重与改级

| 处理 | 条目 | 依据 |
|---|---|---|
| 合并计一条 | `EC-J-P1-01` ≡ `AS-J-P1-01`（204 一码两义 → 前端强制登出） | 同一根因同一证据；按 `EC-J-P1-01` 计入，`AS` 侧保留交叉引用 |
| 合并计一条 | `EC-J-P3-03`（205 死分支）与 `AS-J-P2-01`（205 死码 + 206 缺登录页抑制） | `AS` 版是超集（多出 206 分支不对称导致重登后可反复弹窗），按 J-P2 计一条 |
| 合并计一条 | `WS-J-P2-05`（WS 端点全线无 token）与 `AS-J-P3-01`（WS 握手身份可伪造） | 同一根因；纯身份伪造属已接受风险口径，按 `AS-J-P3-01` 计 J-P3 |
| **已撤回（不计数）** | `PA-J-P1-01`（GET 请求体被丢弃）、`PA-J-P1-02`（菜单/角色回显恒 500）、`PA-J-P3-01`（探针脚手架） | 前提被推翻：`frontend/src/common/http/axios.js:20-24` 的包装器对 GET 走 `params`，参数正常送达；详见 §5.0 勘误 |

---

## 2. 系统性主题（跨分片重复出现，按风险排序）

### 主题 1：单侧整改引入的跨栈回归 —— 本轮最重要的元结论

后端上一轮按 `2026-09-07-full-project-review.md` 修了 34 条 P1，**但没有一条对照前端调用面核对**。4 处「后端修对了、跨栈修断了」：

| 后端整改 | 整改前后 | 跨栈后果 | 条目 |
|---|---|---|---|
| `@RestQuery("roomgId")` → `@RestQuery(ROOM_ID)`（`BaseConstants.java:12` = `"roomId"`） | 前端 7 处一直传查询键 `roomgId` | 房间详情三路（router/report/recept）参数恒 null；router 路 `findByIdOptional(null).orElseThrow` → 信封 `code:500` | `HC-J-P1-01` ✔ |
| `uploadFileToNip` 重写为 `readDocumentContent`，只收 `txt/md/csv`（`TheoryKnowledgeClassifyService.java:43`） | 前端 `accept=".doc,.docx,.pptx"` 未改 | 「导入文档」按钮恒失败（`IllegalArgumentException` → HTTP 200 + 业务码 500 + data=null）+ 前端解引用 null | `TK-J-P1-01` ✔ |
| 新增 `saveBatch`（整批单事务）与 JSON 版 `exportTemplate` | 前端从未接线，仍逐行 fire-and-forget + 带外静态 docx | 后端原子性/校验能力闲置，端点成孤儿；前端 2 秒后无条件 `message.success` | `TK-J-P2-02/03` |
| `Page.getRows()` 钳制 `[1,200]`（`common/utils/Page.java:43-44`） | 前端 `rows: 999` 一次拉全量 | >200 条静默截断且无分页可翻 | `HC-J-P2-03` ✔ |

**教训（对后续整改的硬要求）**：改任何 `@RestQuery`/`@RestForm` 参数名、返回形态、错误语义、能力边界前，**必须 grep 前端调用面**；前端 28 个 `common/api/*.js` 就是完整的契约清单，成本极低。

### 主题 2：GET 传参「看起来危险、实际安全」，但键名/形态漂移是真缺陷

**已撤回的判断**（§5.0 勘误）：前端 `common/api/*.js` 有 37 处 `method:"get"` + `data`，初版据「浏览器丢弃 GET 请求体」判为恒丢参 —— 但这 28 个模块 import 的是包装器 `frontend/src/common/http/axios.js`，其 `:20-24` 对 GET 走 `instance.get(url, { params: data })`，参数正常进查询串。全仓两处直连 raw axios 的调用点（`knowledgeTabel.js:589`、`preJob/receive/explain/Index.vue:136`）分别是 POST 与已注释代码，**无一处真丢参**。

**仍然成立的真缺陷**（同一面上的键名/形态漂移，需逐条对账而非一刀切）：查询键名与后端 `@RestQuery` 名不一致（`HC-J-P1-01` roomgId）、URL 字面量带尾空格（`HC-J-P1-02`）、方法与后端不匹配（`HC-J-P3-04` GET vs POST）、指向不存在端点（`HC-J-P3-05/06`）。前端单侧评审的 3.3 LOW「GET 统一 `data`→`params`」维持 LOW（可读性/一致性，非功能缺陷）。

### 主题 3：授权只存在于前端

后端全仓无任何角色/权限判定（`grep @RolesAllowed|hasPermission` 零命中；`isAdmin` 仅在 `RoleService.java:61` 用于「默认角色互斥」业务逻辑，非授权）。而前端 `v-per` 是可篡改的 vuex 软门控、路由由 `localStorage.userRouter` 重建。前端单侧评审把这三条降级的前提是「**安全性完全依赖后端强校验**」——该前提被本轮**否证**。`GET /api/user/delete?userId=` 与 `GET /api/user/resetPassword?userId=`（`UserController.java:166-178`，仅类级 `@JWT`）任意已登录学员可直调。

### 主题 4：204/205/500 三码语义错位

- 后端 204 一码两义（`CODE_204 设备标识不能为空`=鉴权 / `NULL_ERROR 请求参数为空`=业务），而前端把所有 204 当鉴权失效强制登出 → 改密漏填字段 = 被踢回登录页。
- 前端有 205 分支（"凭证已过期"），后端从不发 205；真实失效发 206（"账号已被异地登录"）→ 过期被错标为异地登录，205 UX 永不可达。
- 后端把参数校验错误也编码成 `code:500`（`ValidationExceptionMapper` / `IllegalStateExceptionMapper` / `InvalidTitleExceptionMapper`）与真服务器错误同码 → 前端无法加「统一 toast 非 200」的集中分支（会把用户输入错误报成服务器错误）。**前端 176 个响应处理文件中 92 个不判 `res.code` 直取 `res.data`** → 非 200 时静默 no-op 或 `.forEach` 抛 TypeError。

### 主题 5：复制粘贴子树导致「错域调用」

前端单侧评审把 `*ZuXun` 四份复制列为结构债；联合视角看到它已经是**功能错域**：
- 「电传组训」整棵子树请求 `/api/generalKeyPat/*`（电子键域），电传成绩落进电子键库（`telexZuXun/train/student/js/datagramTrain.js:8-11` ✔）。
- 电子键「重新拍发」调 `/api/generalTickerPatTrain/reset`（手键域，`electronKeyZuXun.js:38-44` ✔），而 `GeneralKeyPatController` 无 reset 端点 → 本域未重置（假成功），并以同号 `trainId` 重置手键库中无关训练。

### 主题 6：同一计算/同一命名两套实现

- 评分：前端本地算 `errorNumber`/正确率/速率并上传，后端 `countScore` 又独立重算（分母口径不同）→ 训练中实时值与成绩页最终值必然漂移（`TF-J-P2-03`）。
- 命名：考试模块 DTO/VO 发 `snake_case`（`TheoryKnowledgeExamUserSelfVO.java:29` = `start_time` ✔），全站其余发 camelCase；前端逐端点硬编码记对，已发作一处（自测列表「开始时间」恒空 + 排序失效，`DM-J-P1-01`）。

### 主题 7：Web + 反代形态整类失效

后端只在 18001 起明文 HTTP、无 TLS 配置；唯一能承载 HTTPS 的形态是「浏览器 + 反代」，而恰恰在该形态下前端所有自己拼 base 的点（6 处上传 `action`、1 处 SSE、1 处导出、4 处协同 WS）都会产出 `http://https://…` / `ws://https://…` 畸形 URL（`DC-J-P1-01`）。Electron 与 Web-http 直连因 `httpUrl` 不带 scheme 而侥幸可用。

---

## 3. 必修清单（Must-fix，按风险排序）

| # | 编号 | 结论 | 定级 | 修复侧 | 关键证据 |
|---|---|---|---|:--:|---|
| 1 | `AS-J-P1-02` | 后端管理写端点零授权 + 前端软门控 → 任意学员删任意用户 / 重置管理员密码 / 自我提权 | J-P1 | **BE** | `controller/UserController.java:166-178`、`:79-86`、`RoleController.java:43-47`；`config/directive/ButtonPermission.js:15-25`（`indexOf(value)===-1` 时置 `isExist=true`，即**查不到权限反而放行**）✔ |
| 2 | `HC-J-P1-01` | 前端查询键传 `roomgId`，后端已改读 `roomId` → 房间详情三路全失效，router 路返回信封 `code:500` | J-P1 | **FE** | FE 7 处：`Issue.js:149,290`、`ListenIn.vue:404,483`、`useBroadStudent.js:265`、`useBroadTeacher.js:288`、`useBroadcastTrain.js:21`；BE：`SimulationRouterRoomController.java:72`、`SimulationReportRoomController.java:56`、`SimulationReceptRoomController.java:55` ✔ |
| 3 | `EC-J-P1-01` | 后端 204 一码两义 + 前端把 204 一律当鉴权失效 → 改密漏填字段即被强制登出、未提交数据丢失 | J-P1 | 双侧 | `ResponseCode.java:12,20`、`UserController.java:65-67`、`PostTelegramTrainController.java:105-106`；`common/http/index.js:30-33`、`personal.js:172` ✔ |
| 4 | `TK-J-P1-01` | 「导入文档」：前端 `accept=".doc,.docx,.pptx"` × 后端只收 `txt/md/csv` 且抛 `IllegalArgumentException`（→ HTTP 200 + 业务码 500 + `data=null`）→ 按钮恒失败且前端解引用 null；成功路径 `imgUrls=[]` 又使 Word 内容分支永不执行 | J-P1 | 双侧 | `equipmentIndex.vue:204,246`、`study/basic/edit/Index.vue:168`；`TheoryKnowledgeClassifyService.java:43,110-127,132`、`ValidationExceptionMapper.java:31-37` ✔ |
| 5 | `HC-J-P1-02` | `deleteThroyKnowledgeById` URL 末尾多一个空格 → `%20` 真 404，删除静默失败（无 catch） | J-P1 | **FE** | `common/api/TestApi.js:58-63` ✔；BE `TheoryKnowledgeController.java:135-139` |
| 6 | `DM-J-P1-01` | 自测列表读 `d.startTime`，后端 VO 只有 `start_time` → 开始时间恒空 + 时间排序失效（`Number(undefined)=NaN`） | J-P1 | **FE** | `studentGradeList/Index.vue:32`；`TheoryKnowledgeExamUserSelfVO.java:28-29` ✔ |
| 7 | `DC-J-P1-01` | Web+反代(https) 形态下上传/SSE/导出/协同 WS 全部畸形失效（后端无 TLS，只能靠反代） | J-P1 | **FE**(+运维) | `index.html:80-88`；`study/basic/edit/Index.vue:198,230`、`knowledgeTabel.js:588`、`UnionWs.js:41`；`application.yml:8-15,32-33` |
| 8 | `EC-J-P2-02` | 业务失败无集中处理：92/176 个处理文件不判 `res.code`；后端把参数校验错误（`IllegalArgumentException`/`IllegalStateException`）与真服务器错误同编码为业务码 500 → 前端无法安全加集中分支 | J-P2 | 双侧 | `common/http/index.js:29-69`；`ValidationExceptionMapper.java:31-37`、`IllegalStateExceptionMapper.java:19-23`、`GlobalExceptionMapper.java:20-25` |
| 9 | `TF-J-P2-01/02` | 「电传组训」整树打电子键域；电子键「重新拍发」打手键域 reset | J-P2 | **FE**(+BE 补端点) | `telexZuXun/.../datagramTrain.js:8-11`、`electronKeyZuXun.js:38-44`、`GeneralTickerPatController.java:90-96`（`GeneralKeyPatController` 无 reset）✔ |
| 10 | `AS-J-P2-02` | 登出只清 localStorage，从不调 `POST /api/user/userOut`（端点存在、前端零调用），且不拆 WS → 服务端 token 仍有效 + 幽灵 WS 会话 | J-P2 | **FE** | `common/api/UserApi.js:34-36`（全仓唯一出现处）、`config/router/guards.js:10-19`；BE `UserController.java:50-54` ✔ |

> 表中 ✔ = 父代理独立复核过该证据（读源码/grep/浏览器实证），非仅采信子代理。

---

## 4. 与两侧单侧评审的定级变化

| 单侧结论 | 单侧定级 | 联合定级 | 变化理由 |
|---|---|---|---|
| 后端 `CA-P1-01/02/03` `@RestQuery("roomgId")` 拼写错误 | P1「前端传 roomId 得 null」 | `HC-J-P1-01` J-P1，**责任反转为 FE** | 前提被否证：前端实际传的查询键就是 `roomgId`。后端「修正拼写」把原本同名可用的契约改断 |
| 前端 3.3 LOW「GET 统一 `data`→`params`」 | LOW | **维持 LOW** | 初版曾升为 J-P1 并已撤回：包装器 `common/http/axios.js:20-24` 对 GET 走 `params`，参数正常送达；只剩可读性/一致性价值（见 §5.0） |
| 前端 3.1/3.4 HIGH「鉴权与权限门控全靠客户端信任」（降级前提：后端强校验） | HIGH（前端软门控） | `AS-J-P1-02` J-P1，**根因归 BE** | 后端零角色授权，前提不成立；越权可删用户/重置密码/提权，属数据性后果，不适用「纯安全已接受风险」豁免 |
| 前端 3.3 MEDIUM「无集中式业务码处理」 | MEDIUM（FE） | `EC-J-P2-02` J-P2，**双侧** | 后端把校验错误与服务器错误同编码 500，前端无法单侧修出正确的集中分支 |
| 前端 §2「复制粘贴主导的结构债」 | 结构债 | `TF-J-P2-01/02` J-P2 **功能错域** | 已经不是"改 N 份"的维护成本，而是电传成绩落进电子键库、reset 打到无关域 |
| 前端 3.1 HIGH#1「富文本 `v-html`/`document.write` 存储型 XSS」 | HIGH（FE 净化） | `TK-J-P2-04` 定责细化 | 后端全仓零净化（`grep sanitiz|jsoup|Whitelist` 零命中），确认最小修复落**前端渲染侧**（多 sink 收口点唯一），后端写入侧净化仅作纵深防御 |
| 后端 `CI-P2-03`「手写 CORS 与配置择一」 | P2 | **已解决** | `JWTInterceptor.java:45-51` 手写 CORS 已删，现单源 `application.yml:11-15`（`origins:'*'`、`headers:'*'`）；跨源形态预检可通过 |
| 后端 `CA-P1-04`（分页默认值/无上限） | P1 已修 | `HC-J-P2-03` 新增副作用 | `getRows()` 钳到 200 是正确修复，但与前端 `rows:999` 的「一次拉全」约定冲突 → 静默截断 |

---

## 5. 父代理独立复核、勘误与新增条目

### 5.0 勘误（2026-09-08，写 fix-spec 前的二次取证发现）

两条父代理新增条目**已撤回**，一处事实**已更正**。撤回原因不是「证据不足」，而是**前提被推翻**：

| 撤回/更正项 | 初版结论 | 推翻它的事实 | 现状 |
|---|---|---|---|
| `PA-J-P1-01` | 前端 26 个端点面把参数放 GET 请求体、浏览器丢弃 → 后端恒收 null | 28 个 api 模块 import 的是**包装器** `frontend/src/common/http/axios.js`（`grep -h '^import' common/api/*.js` → 28/28 命中 `../http/axios.js`），其 `:20-24` 对 GET 执行 `instance.get(url, { params: data })` → `data` 被序列化成查询串正常送达 | **撤回**。同时核实全仓仅 2 处直连 raw axios：`knowledgeTabel.js:589`（`method:'POST'`）与 `preJob/receive/explain/Index.vue:136`（已注释），**无一处真丢参** |
| `PA-J-P1-02` | 菜单/角色回显「GET 丢参 × `orElseThrow`」→ HTTP 500 | 参数不丢（同上），且 `IllegalArgumentException` 有专用 Mapper | **撤回** |
| 事实更正 | `orElseThrow(IllegalArgumentException)` 落 `GlobalExceptionMapper` → HTTP 500 | `ValidationExceptionMapper.java:21` 声明 `implements ExceptionMapper<IllegalArgumentException>`，`:31-37` 返回 **`Response.ok`（HTTP 200）+ 业务码 500 + `safeMessage`**；全仓只有 `GlobalExceptionMapper.java:20-25` 走 `Response.serverError()` | 受影响表述已在 §2 主题 1、§3 第 4 行改写。影响面：全仓约 **116 处** `orElseThrow(IAE)`（38 个 service）统一走这条路径 |

仍然成立的相关结论（不受勘误影响）：`HC-J-P1-01`（查询键 `roomgId` ≠ 后端 `roomId`，7 处）、`HC-J-P1-02`（URL 尾空格 → 404）、以及「前端大量消费点不判 `res.code` 直接用 `res.data.*`」——后者在**业务码 500 且 `data=null`** 时同样炸，只是原因是错误码语义而非丢参。

方法论教训（值得写进流程）：**父代理的「运行期实证」也必须实证到调用链末端**。这次 Chromium 探针测的是浏览器 XHR 语义（结论正确），却没验证「项目里的 axios 调用真的以那种形态发出」——中间隔了一层 4 行的包装器。凡是「共同前提」型断言，必须连同项目自己的适配层一起验证。

### 5.1 父代理复核的关键事实（逐条已核）

- `roomgId`：前端 7 处 ✔（grep 命中）；后端全仓零命中 ✔；`BaseConstants.ROOM_ID="roomId"` ✔。
- 后端零授权：`grep @RolesAllowed|hasPermission|SecurityIdentity|PermitAll` 仅命中 `RoleEntity.isAdmin` 字段与 `RoleService.java:61` 的默认角色互斥逻辑 ✔；`isAdmin` 的另一处消费是 `UserService.java:412` 的**菜单过滤**（`isAdmin==0` = 超管，给全部菜单），仍非端点授权。
- 管理写端点面（仅 3 个 `@JWT` 类）：`UserController` 的 `saveUser:43`、`changePassword:57`、`importUser:72`、`addUserRole:79`、`delete:166`、`resetPassword:173`；`RoleController.addRole:43`；`MenusController.addMenu:41` ✔。唯一自限定范式是 `UserController.userOut:50`（`@RestHeader(TOKEN)`）与 `UserService.getUserByToken:517-522`（查无即抛 `UnauthorizedException`）✔。
- `userOut` 端点存在、前端全仓仅 `UserApi.js:35` 定义、零调用 ✔。
- `TestApi.js:61` URL 末尾空格 ✔（`:raw` 读取确认）。
- `TEXT_SUFFIXES = Set.of("txt","md","csv")`（`TheoryKnowledgeClassifyService.java:43`）与前端 `accept=".doc,.docx,.pptx"` ✔。
- `TheoryKnowledgeExamUserSelfVO.start_time` ✔；`Page.getRows()` 钳制 `[1,200]` ✔；前端 `rows:999`（`telexZuXun/list/js/list.js:138`）✔。
- 错误码产生点穷举：业务语义的 `NULL_ERROR`(=204) 全仓 **3 处** —— `UserController.java:67`（改密）、`PostTelegramTrainController.java:106`（finish）在 `@JWT` 类下 → 会触发前端强制登出；`free/UserController.java:44`（登录）在登录页命中 `index.js:32` 的 `return undefined` 破坏信封 ✔。`PARAMS_ERROR`(202) 全仓仅 1 处（`PostTelegramTrainController.java:109`）✔。
- 错域调用：telexZuXun 6 个文件全部 import `electronKeyZuXun.js`/`handkeyZuXun.js`（key/ticker 域），WS 也连 `/generalKeyPatTrain`；`reset` 端点全仓只有 `GeneralTickerPatController.java:91`，Key/Telex 两域均无 ✔。
- WS 分片结论「后端单例已用静态 `ConcurrentMap` 收口、无共享可变实例字段」与后端单侧评审一致，父代理未发现反例（未运行验证）。

### 5.2 实证脚手架清理

§5.0 的 Chromium 探针脚手架（`/tmp/joint-probe/server.mjs` + 一次性 `joint-probe` 进程）已停止并删除，未进入仓库。

---

## 6. 修复落地顺序建议

1. **BE 先补授权**（`AS-J-P1-02`）：8 个管理写端点（`UserController` 6 个 + `RoleController.addRole` + `MenusController.addMenu`）加服务端角色校验；`changePassword` 改为从 token 推导 userId（照 `userOut` 范式）。这是唯一「不修就没有安全边界」的一条，也是前端所有软门控降级的前提。
2. **FE 契约对账修点**（`HC-J-P1-01`/`HC-J-P1-02`/`DM-J-P1-01`/`HC-J-P2-03`）：`roomgId`→`roomId`（7 处）、删 URL 尾空格、`d.startTime`→`d.start_time`、`rows:999` 改真分页。全部集中在 `common/api/*.js` 与少数视图，风险低。
3. **双侧对齐错误码**（`EC-J-P1-01`/`EC-J-P2-02`/`AS-J-P2-01`）：BE 把业务「参数为空」迁出鉴权码段（`NULL_ERROR`→`PARAMS_ERROR`）、把参数校验错误与真服务器错误分码；FE 随后删 205 死分支、把登录页抑制统一到全部鉴权码、加「非 200 集中提示」默认分支。**顺序不可颠倒**（前端先加集中分支会把用户输入错误报成服务器错误）。
4. **修文档导入与题库导入分工**（`TK-J-P1-01`/`TK-J-P2-02/03`）：FE `accept` 对齐 `txt/md/csv`、`imgUrls` 判定改 `Array.isArray && length`；接线 `saveBatch` 取代逐行 fire-and-forget 假成功。
5. **FE 修错域调用**（`TF-J-P2-01/02`）：telexZuXun 6 文件改指 `datagramZuXun.js`（`generalTelexPat`）与 `/generalTelexPatTrain`；electronKey reset 改指本域（需 BE 补 `generalKeyPat/reset`）。
6. 其余 J-P2/J-P3 见分片报告，可与各自域的常规迭代合并。

> 逐条可执行的任务分解、验收口径与门禁见 [修复 Spec](../specs/2026-09-08-joint-fix-spec.md)。

---

## 7. 局限

- 全部分片为静态取证，未启动后端与前端。唯一运行期验证是 §5.0 的浏览器 XHR 探针，而它恰好证明了「实证也可能测错层次」。「信封 500」「静默截断」「重连风暴」等运行期后果均为代码路径推断。
- 后端 61 个 controller / 前端 28 个 api 模块做了全量对账；但**调用点**（265 `.vue` + 282 `.js`）为抽样，覆盖率见各分片 §1。
- 仓外组件（Electron 主进程外壳、nginx/反代配置）不可见，相关结论标 `[INFERENCE]`。
- 行号基于 `HEAD 0d3bdea` 快照；后续改动会偏移。
