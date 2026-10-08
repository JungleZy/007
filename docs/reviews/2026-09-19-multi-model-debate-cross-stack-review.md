# 2026-09-19 多模型对抗辩论评审 —— 前后端一致性专项

> 方法：model-debate（三模型独立立论 → 交叉反驳 → 定点收口 → 独立裁判裁决）。
> 基线：HEAD `bb67ce0`，工作区干净。**全程只读静态取证**：未启动服务、未跑 `./mvnw verify`、未修改被评审代码。
> 本文为**结论文档**；与 [`2026-09-15-full-project-review.md`](2026-09-15-full-project-review.md) 的关系见 §7。

## 1. 参与方与流程

| 角色 | 模型 | 产出 |
|---|---|---|
| 辩手 A | `kimi/k3:high` | R1 立论 8.0/10 → R3 终局 8.0 |
| 辩手 B | `glm/glm-5.3:high` | R1 立论 7.5/10 → R2 7.3 → R3 终局 7.3 |
| 辩手 C | `gpt/gpt-6-astra:high` | R1 立论 7.2/10 → R2 7.7 → R3 终局 7.7 |
| 独立裁判 | `cc/claude-fable-5[1M]:high` | **裁决 7.9/10** |
| 主持人 | 会话模型 | 出题、控场、独立机器交叉核对（round 0） |

轮次：R1 三方**指令完全相同、互不可见**的独立立论；R2 全文互读后按 9 个分歧轴逐条反驳；R3 只收口残留的算术分歧与定级投票。共 9 次辩手调用 + 1 次裁判调用。

辩论原始记录（会话本地，非仓库资产）：
`~/.omp/agent/sessions/-workspace-007/2026-09-19T10-11-14-275Z_01a0b926-0ee3-709e-b724-8cd6e9021d0c/local/debate/`
—— `r1-*.md`、`r2-*.md`、`r3-*.md`、`full-record.md`（1545 行合并记录）、`verdict.md`（裁判裁决）、`moderator-xref.md`（主持人机器对账）。
其中 `r1-gpt-6-astra.md` 附录 A/B 是**前端 28 模块 331 个导出函数**与**后端 62 controller 359 个路由注解**的逐条清单，可作下一轮对账底底。

## 2. 裁决结论

**辩论 + 裁决阶段结论：前后端一致性健康度 7.9 / 10；P0 = 0，P1 = 0，P2 = 3，P3 = 8。**

**§9 四个盲区同日闭合后，实际计数为：P0 = 0，P1 = 1，P2 = 7（原 3 项 + 新增 4 项，其中 1 项由原 P3 升级），P3 新增 4 项（见 §10）。** 健康度相应下修到 **7.4 / 10**：新发现的 P1-01（联合演练「结束训练」恒失败，`sendFinish` 前端发 body / 后端读 query）与 P2-01（「平均报」开关被 Jackson 静默丢弃）都是**活跃契约面上的真实破裂**，直接推翻了辩论阶段「活跃面零破裂」这一支撑 7.9 的核心前提（−0.5）。裁判的 7.9 在其信息集内成立；本条是信息集扩大后的修正，不是对裁决的否定。

活跃契约面的其余部分（URL / HTTP 方法 / GET 键名 / 响应信封 / 鉴权头 / WebSocket 握手 / Electron IPC / keysim REST / 导出返回形态）经四方独立验证**零破裂**，本轮闭合未推翻。辩论阶段的扣分仍全部落在死代码、无消费者端点、消费端错误处理半收敛与契约元数据失真四类问题上。

扣分拆解（裁判给出，非三方平均 7.67）：

| 扣分项 | 分值 |
|---|---|
| 孤儿端点 36 个（≈10% 后端面，含 4 个 admin 用户信息端点与 4 个 admin 导入导出端点） | −0.6 |
| 消费端错误处理半收敛（7 处活跃假成功 + 15 处 `res.data`-gated + 约 30 文件从不判码；拦截器只 toast 不熔断） | −0.5 |
| `components/dept/` 死子树（3 文件 import 不存在的 `DeptApi.js`） | −0.3 |
| 死导出层（错域死导出 ×2、17 个零消费导出、同 URL 双导出） | −0.2 |
| 契约元数据失真（AGENTS.md 三组数字 + 「28 模块即完整契约清单」表述被证伪） | −0.2 |
| 卫生项（尾空格 URL、`listPge` 双侧 typo、`/socket/` 前缀、中文路径、数组插值 URL） | −0.2 |
| POST 请求体全量对账未完成的残余不确定性 | −0.1 |

## 3. 共识点（四方一致，标注证据强度）

1. **【已验证】URL / HTTP 方法层零幽灵调用、零方法不匹配。** 前端 322 个唯一 `/api` URL ↔ 后端 358–359 个端点；三辩手独立机器对账 + 主持人 xref + 裁判抽查 GET 端点 `@RestQuery` 键名（`roomId/userId/id/deviceId/pageNumber`）全部对齐。3 个初筛「幽灵」均为 `${query}` 模板提取伪影。
2. **【已验证】孤儿端点 = 36**（清单见 §6）。三路印证：主持人 xref 40 候选 − 裁判逐条确认确有消费者的 4 条（`uploadFileToNip`、`getRoomUserList/{}`、两个 `startTrain`）= 36；抽 10 条在 `frontend/src + electron + keysim/src` 三处 grep 全部零消费。
3. **【已验证】`components/dept/` 死子树三事实**：`bw-frontend/frontend/src/common/api/DeptApi.js` 不存在；3 个 importer 真实存在（`components/dept/DeptTree/DeptTree.vue:20`、`components/dept/job/js/useJob.js:1`、`components/dept/member/js/useMember.js:1`）；后端 controller/service 零 `department` 命中；子树 0 外部引用。
4. **【已验证】AGENTS.md 计数已漂移**：`@RequireAdmin` 实为 **70 注解 / 29 controller**（文档写 45/17）；无 `@RequireAdmin` 的非 free controller 实为 **31 个**（文档写 43）；裸 `.firstResult()` 实为 **65 处 / 46 文件**（文档写 58/45）。62 个 controller、其中 free 2 个。
5. **【已验证】keysim 只支持 `http://` 是单测锁死的显式边界**，不是静默缺陷：`keysim/src/rest.rs:24` 显式报错，`:140` 断言 `split_base("https://x/api").is_err()`。
6. **【已验证】`electronKeyZuXun.js:57-69` 两个错域导出零消费**：三个消费文件（`handkeyZuXun/train/student/student.vue:243`、`.../student/js/trainScore.js:4`、`.../teacher/js/teacher.js:3`）全部 import 自 `handkeyZuXun.js`。
7. **【已验证】WS `Integer.parseInt(mesg.get("count"))` 有兜底**：`WebSocketSimulationService.java:439-441` 的 `catch(RuntimeException)` 覆盖 `messageHandleReport`，log + 发「消息格式错误」错误帧 + 保留连接；前端 finish 帧 `count` 恒为整数（`useBroadTeacher.js:411-415`）。
8. **【已验证】导出/下载面统一 JSON 信封**，无字节流/信封错配（`TheoryKnowledgeQuestionController.java:84-105`、`PostTelegramTrainController.java:84-89`）—— 三辩手均未覆盖此面，裁判补证，结论为干净。
9. **【已验证】拦截器非 200 只 toast 不熔断、原样 `return response.data`**（`bw-frontend/frontend/src/common/http/index.js:55-86`）；203/204/206 走登录跳转，207/208 走 `terminalCode.js` 终态文案。
10. **【推断】09-08 / 09-15 历史跨栈整改全部闭环**（`roomgId`、上传能力边界、`saveBatch`/`exportTemplate` 接线、`rows:999`、204 一码两义、错域调用、`userOut`、`start_time`）—— 三方独立复核一致，裁判仅抽验。
11. **【推断】AGENTS.md 五条红线当前收敛**：写路径事务全部重抛或 `setRollbackOnly()`；7 个 `@ServerEndpoint` 会话态全在 `static ConcurrentMap`、无违规实例字段；`project006.sql` 零 MyISAM；`findUserEntityByToken` 仅 `UserService.java:498,559` 两个调用点且都判空。

## 4. 分歧裁决

| 分歧 | 各方立场 | 裁决 |
|---|---|---|
| 孤儿端点数字 | k3 R1「约 24」/ glm 36 / astra 56→24→36 | **采信 36**（glm R1 起一贯正确）。astra R2 的「24 是共识」是证据不足下的**错误让步**——直接采纳 k3 已自撤的错误聚合数，且其「可复跑命令」`tools/contract_count.py` 在仓库中不存在（glm R3 指出，裁判 `test -f` 复核 MISSING）。 |
| success-before-check 数量 | 三方共识 21-22 处，3/3 投 P2 | **定级 P2 成立，但数字被裁判推翻。** 按「success 前是否有 `if(` 分流」拆分：**8 处无条件报成功（其中 1 处在 dept 死子树，活跃 7 处）** + **15 处以 `res.data` 真值分支**（失败走 error 分支，仅双 toast，不是假成功）。glm R1 点名的 3 个样例中 2 个（`personal.js:169`、`telex.js:147`）实为 data-gated。三方均未做此语义拆分。 |
| dept 死子树定级 | k3 P2 / glm R2 升 P2 / astra R2 补认 P2 | **P2 维持。** 一次路由接线即构建断裂 + 整批幽灵调用；不到 P1 因 0 外部引用、不进活跃 bundle。 |
| keysim http-only | astra P2→P3 / k3「非缺陷」/ glm P3 | **P3，astra 降级正当**（基于 glm 提供的单测新证据，非过早投降）。 |
| `TestApi.js:61` 尾空格 | astra P2→P3 / k3 P3（Bun WHATWG 实测）/ glm P3（并撤回「Node 环境 404」无实测断言） | **P3。** 空格真实存在（`cat -A` 验证），但该 URL 唯一执行路径是浏览器/renderer XHR，WHATWG 解析剥除尾空格。 |
| `electronKeyZuXun.js:57-69` 死导出 | astra P1→撤 / k3 R1 漏列后补 P3 / glm P3 | **P3。** 零消费者是硬事实；P1 口径要求存在活路径。 |
| REST/WS「生命周期分裂」 | astra P1→撤 / k3、glm 驳为 P3 | **P3。** `GeneralTickerPatService.updateStatus:647-679` 是唯一 DB 写口（带锁、带鉴权、带状态机），WS begin/end 仅转发，`teacher.js:291-305` 先判码后发帧——「REST=命令、WS=通知」是正确分层。残留仅 `/socket/` 前缀命名误导。 |
| AGENTS.md 数字漂移 | k3、astra P3 / glm P2 弱 | **P3（多数派），但附加 glm 方向的加重情节**：AGENTS.md「28 个 api 模块即完整契约清单」被 dept 死子树证伪（存在对第 29 个不存在模块的 import），这不是数字滞后而是**验证程序前提失效**，必须一并修。 |
| 总分 | k3 8.0 / astra 7.7 / glm 7.3 | **裁判独立给 7.9。** k3 偏高：未对契约清单前提被证伪计价。glm 偏低：其最大扣分桶建立在 21 处假成功上，实为 7 处。astra 数字居中但推导路径最差（56→24→36 两次不可复现）。 |

**让步正当性总评**：astra R2 的 4 条降级（两条 P1、keysim、尾空格）全部基于对方新证据，裁判逐条复核后维持；唯一不正当的是孤儿数「24」的坐享式让步，R3 已自纠。glm 撤回「Node axios 404」正当。k3 两处改口（24→36、68→65）正当且干脆。

## 5. 缺陷清单

### P0 / P1：无

### P2（3 项）

1. **孤儿端点 36 条** —— 后端有、全前端（28 模块 + 所有 `.vue/.js` 的 `url:`/`:action` 字面量）+ keysim 零消费。清单见 §6。重点：`UserController.java:106,122,132,148`（4 个 admin 用户信息端点）、`GeneralKeyPatController.java:134,147,156,163`（导入导出，2 个带 `@RequireAdmin`）、`GeneralTickerPatTrainController.java:45,65`（含中文路径端点）、`SimulationWebSocketController.java:27`。
2. **7 处活跃「假成功」调用点** —— 不判 `res.code` 直接 `message.success` 并推进本地状态：
   - `bw-frontend/frontend/src/views/manage/unionJob/lineNotify/components/ListenIn.vue:286-303`（`changePin` 不判码即报「切换成功」并发 WS `change` 帧，主持人已读源码确认）
   - `bw-frontend/frontend/src/views/manage/preJob/ditto/militaryDeploy/Index.vue:264`（`addMilitarys`）、`:286`（`deleteMilitarys`）
   - `bw-frontend/frontend/src/views/manage/postJob/hanzi/articleManage/Index.vue:198`（`deleteArticleByID`）
   - `bw-frontend/frontend/src/views/manage/systemManage/basic/equipment/Index.vue:158`（`deleteEquipmentById`）、`:168`（`addEquipments`）、`:181`（`editEquipments`）
3. **`components/dept/` 死子树** —— `DeptTree.vue:20`、`job/js/useJob.js:1`、`member/js/useMember.js:1` import 不存在的 `common/api/DeptApi.js`；后端零 department 端点。

### P3（8 项）

- `AGENTS.md:35/52/54` 三组数字漂移 + `:53`「28 模块即完整契约清单」表述被证伪。
- `bw-frontend/frontend/src/common/api/TestApi.js:61` URL 尾随空格。
- `common/api/electronKeyZuXun.js:57-69` 两个错域死导出（打手键域 URL）。
- 17 个零消费 api 导出 + 同 URL 双导出（如 `UserApi.js:11/18` 的 `addSignin`/`userSignIn`）。
- `listPge` 双侧一致的拼写错误（`PostRadiotelephoneTrainController.java:72` ↔ 前端 `StructureApi.js`）——两侧任一「顺手改对」即改断，属红线 5 同型雷。
- `GeneralTickerSocketController.java:25` 的 `/socket/` REST 前缀命名误导。
- keysim 仅支持 `http://`（`keysim/src/rest.rs:24`）——显式边界，登记不扣产品分。
- `components/common/NipUEditor.vue:86` 对整个数组插值拼 URL；以及 15 处 `res.data`-gated success（双 toast 风格项）。

## 6. 孤儿端点清单（36 条）

```
POST /api/equipmentDevice/addKeyPoints                     EquipmentDeviceController.java:75
POST /api/equipmentDevice/listKeyPoints                    EquipmentDeviceController.java:84
GET  /api/deviceScoringRule/delete                         DeviceScoringRuleController.java:50
POST /api/tickerTapeTrain/saveBaseTrain                    TickerTapeTrainController.java:100
POST /api/user/getUsersByUserNameStartingWith              UserController.java:106
POST /api/user/getUserInfoAllByStatusDesc                  UserController.java:122
POST /api/user/getUserById                                 UserController.java:132
POST /api/user/getUsersByIds                               UserController.java:148
POST /api/enteringExercise/getWordStock                    EnteringExerciseController.java:89
POST /api/postEnteringExercise/getWordStock                PostEnteringExerciseController.java:80
POST /api/postEnteringExerciseWordStock/view               PostEnteringExerciseWordStockController.java:52
POST /api/postRadiotelephoneTrain/findByTypeAndRandom      PostRadiotelephoneTrainController.java:55
POST /api/postTelegramTrain/addContentValue                PostTelegramTrainController.java:129
POST /api/PostTelegraphKeyPatTrain/reset                   PostTelegraphKeyPatTrainController.java:100
POST /api/telegraphKeyPatTrainSynthetical/findAll          TelegraphKeyPatTrainSyntheticalController.java:43
GET  /api/simulation/socket/getAllRoomInfo                 simulation/SimulationWebSocketController.java:27
GET  /api/simulation/recept/getRoomDetail                  simulation/SimulationReceptRoomController.java:55
GET  /api/simulation/report/delete                         simulation/SimulationReportRoomController.java:64
POST /api/telexPat/findTexPatTrainByToken                  TexPatTrainController.java:53
POST /api/telexPat/deleteById                              TexPatTrainController.java:82
POST /api/theoryKnowledge/getDutyBusiness                  TheoryKnowledgeController.java:100
POST /api/theoryKnowledge/getChargeEquipment               TheoryKnowledgeController.java:106
POST /api/theoryKnowledgeTest/getTestContentByUserIdAndKnowledgeId  TheoryKnowledgeTestController.java:70
POST /api/theoryKnowledgeTest/getTestContentById           TheoryKnowledgeTestController.java:76
GET  /api/openApi/generalTickerPatTrain/综合训练-手键拍发Api   general/GeneralTickerPatTrainController.java:45
GET  /api/openApi/generalTickerPatTrain/findTrainInfo      general/GeneralTickerPatTrainController.java:65
GET  /api/GeneralKeyPatTrain/getOneline/{trainId}          general/GeneralKeyPatTrainController.java:33
GET  /api/generalTickerPatTrain/startTrain                 general/GeneralTickerPatController.java:112
GET  /api/socket/generalTickerPatTrain/getByTrainIdAndUserId  general/GeneralTickerSocketController.java:36
POST /api/generalTelexPat/findPage                         general/GeneralTelexPatController.java:71
GET  /api/generalTelexPat/getOnline                        general/GeneralTelexPatController.java:129
POST /api/generalKeyPat/findPage                           general/GeneralKeyPatController.java:59
POST /api/generalKeyPat/getPatValue                        general/GeneralKeyPatController.java:113
GET  /api/generalKeyPat/getTrainInfo                       general/GeneralKeyPatController.java:134
POST /api/generalKeyPat/importTrainInfo                    general/GeneralKeyPatController.java:147
GET  /api/generalKeyPat/getTrainInfoBatch                  general/GeneralKeyPatController.java:156
POST /api/generalKeyPat/importTrainInfoBatch               general/GeneralKeyPatController.java:163
```

> 注：上表 37 行中 `general/GeneralTickerPatController.java:112 startTrain` 由 keysim（`keysim/src/rest.rs:114`）与前端 `handkeyZuXun.js:52` 双重消费，**不计入 36**，保留在此仅为说明主持人 40 候选与最终 36 的差集；另三条被剔除的是 `theoryKnowledge/uploadFileToNip`（`equipmentIndex.vue:274` 的 a-upload `:action` 直连）、`simulation/router/getRoomUserList/{}`（`ListenIn.vue:481`）、`generalKeyPat/startTrain`（`electronKeyZuXun.js:73`、`keysim/src/rest.rs:123`）。

## 7. 与既有文档的关系

- 本文**不取代** [`2026-09-15-full-project-review.md`](2026-09-15-full-project-review.md)（全项目状态入口）；它是同一基线上的**前后端一致性专项复核**，方法为多模型对抗 + 独立裁判。
- 本文推翻/修正的既有陈述：
  - `AGENTS.md:35`「`@RequireAdmin` 45 个端点 / 17 个 controller」→ 实测 **70 注解 / 29 controller**。
  - `AGENTS.md:54`「43 个非 free controller 全文无 `@RequireAdmin`」→ 实测 **31 个**。
  - `AGENTS.md:52`「裸 `firstResult()` 58 处 / 45 个 DAO 文件」→ 实测 **65 处 / 46 文件**。
  - `AGENTS.md:53`「28 个模块即完整契约清单」→ **表述被证伪**：存在对第 29 个（不存在的）模块 `DeptApi.js` 的 import；正确口径是「28 模块 + 全 `src` 的 `/api` 字面量（含 a-upload `:action`）+ `keysim/src/rest.rs`」。
- [`2026-09-08-joint-frontend-backend-review.md`](2026-09-08-joint-frontend-backend-review.md) 记录的 4 处「后端单侧整改改断前端」与全部 J-P1，本轮三方独立复核**均为已闭环**。

## 8. 整改动作项（按优先级）

| # | 动作 | 改动面 | 验证方式 |
|---|---|---|---|
| 1 | 删除 `components/dept/` 死子树（或补齐后端 + `DeptApi.js`，二选一，默认删） | `bw-frontend/frontend/src/components/dept/` 整目录 | 前端构建通过 + `grep -rn "components/dept" src` = 0 |
| 2 | 36 条孤儿端点分诊：逐条决定删除或登记进 api 模块；优先 `UserController` 4 条 admin 信息端点与 `GeneralKeyPatController` 4 条导入导出；中文路径端点 `GeneralTickerPatTrainController.java:45` 删除或改名 | 后端 controller + 可能的前端接线 | 重跑 FE/BE URL join，孤儿 = 0 或余项进白名单文档 |
| 3 | 修 7 处假成功点：`message.success`/状态推进前加 `if (res.code !== 200) return`（`ListenIn.vue:481` 已有先例可复用） | 见 §5 P2-2 清单 | 复跑分类扫描（`.then` 窗口内 success 前无 `if(`）裸点 = 0 |
| 4 | 修 AGENTS.md：`:35`→70/29、`:52`→65/46、`:54`→31；`:53` 把「28 模块即完整契约清单」改为三源口径 | `AGENTS.md` | 文中数字与 `grep -c` 实测一致 |
| 5 | 清死导出：`electronKeyZuXun.js:57-69` 两导出、17 个零消费导出、`userSignIn` 重复导出 | `bw-frontend/frontend/src/common/api/*.js` | 构建通过 + 每个删除符号全仓 grep = 0 |
| 6 | `TestApi.js:61` 去尾空格（顺带 grep 全仓带尾空格的 URL 字面量） | `TestApi.js` | `cat -A` 无尾空格；调用点手测删除功能 |
| 7 | （低优）`listPge` 双侧同步改名放**同一个 commit**；`/socket/` 前缀迁移或加注释 | 跨栈 | 双侧 grep 原词 = 0，新词两侧各命中 |

> 动作项 7 属跨栈契约改动，必须遵守 AGENTS.md 红线 5：同一 commit 改两侧，并把前端调用面 grep 结果贴进提交正文。

## 9. 置信度与未决风险

**仅有静态证据、需跑服务才能定性：**
- 7 处假成功点的实际 UX（双 toast 时序、207 时 WS change 帧是否被后端拒绝、本地状态污染可否恢复）。
- 尾空格 URL 在真实 Chromium XHR 的行为（Bun WHATWG 实测是代理证据）。
- 36 个孤儿端点的实际鉴权行为（静态见 `@JWT`/`@RequireAdmin`，未发请求验证 203/207 路径）。
- `String→Integer` query 宽松转换的失败形态。

**四方（含裁判）均未全量覆盖的盲区** —— **已于同日全部闭合，见 §10**：
1. POST 请求体 DTO 字段 ↔ 前端 payload 键名全量对账 → §10.1（已闭，查出 1 个 P1）
2. WebSocket 消息体字段级 schema → §10.2（已闭，查出 2 个 P2 + 2 个 P3）
3. Electron 文件服务（8000）↔ `NipUEditor` 返回形态契约 → §10.3（已闭，P3 升级为 P2 共性问题）
4. WS 单例静态容器并发正确性 → §10.4（已闭，未发现缺陷）

**裁判自评置信度**：孤儿 36、dept 三事实、四组计数、死导出零消费、7/15 假成功拆分 = 高（全部亲手复跑）；REST/WS 分层、历史整改闭环、红线复核 = 中（三方交叉一致但未逐行重验）；总分 7.9 = 权重判断，±0.3 内均可辩护。

---

## 10. 盲区闭合（2026-09-19 同日执行）

§9 列出的四个盲区已全部闭合。方法一律只读静态取证（未启动服务、未跑 `./mvnw verify`）。**新增 1 个 P1、4 个 P2、4 个 P3**，均不在辩论三方与裁判此前的清单里。

### 10.1 POST 请求体 DTO ↔ 前端 payload 全量对账（最大盲区，已闭）

**方法**：机器化双向 join。后端解析 62 个 controller 的全部 `@Path`+HTTP 注解与方法签名，按注解把参数分成 query/form/header/body 三类；body 类再解析 DTO 类字段（含 `extends` 继承链、`@JsonProperty` 改名、无修饰符的包级私有字段）；`Map` 绑定的端点改为从 controller/service 方法体提取 `map.get("字面量")` 与 `map.get(常量)`（常量表从 `common/constants/**` 解析，52 个）。前端侧**逐文件解析 `import {…} from '…Api.js'` 再按模块归属匹配调用点**（关键：28 个模块间有 18 个重名导出，如 `addTrain`/`findAll`/`deleteList`，按函数名全局匹配会串模块并产生整批假阳性），对象字面量实参做括号配对 + 顶层键提取，动态实参回溯 `ref()/reactive()` 初始化器。

**覆盖率**：

| 项 | 数 |
|---|---|
| 后端 POST 端点 | 310 |
| 其中带请求体参数 | 259（DTO 196 / Map·集合 61 / 标量 2） |
| Map 端点已解析出后端读键 | 53 / 61 |
| 前端 POST 调用点（已按 import 解析模块归属） | 425 |
| — 对象字面量实参（可逐键对账） | 265（含 3 处展开运算符） |
| — 动态实参 | 96（其中 60 经 ref/reactive 初始化器解析） |
| — 无实参 | 64 |
| 被前端调用到的带体端点 | 213 |
| 带体端点无任何前端调用（与 §6 孤儿清单重叠） | 46 |

**前提**：`application.yml` 未配置 Jackson，Quarkus 默认 `fail-on-unknown-properties=false` —— 前端多传的键**静默丢弃**，不报 400。因此「多传」= 静默失效而非协议错误。

**结论**：双向差异原始候选 35 处，逐条回源码复核后**只有 2 处是真缺陷**（见下），其余全部是假阳性（重名导出串模块、展开运算符供键、`ref` 初始化器与调用前就地补齐的变量不同、后端可选参数如 `floorNumber 不传为获取全部`）。

**新增 P1-01｜`/api/simulation/router/sendFinish` 前端发 body、后端读 query，联合演练「结束训练」恒失败**

- 前端：`bw-frontend/frontend/src/common/api/UserApi.js:130-136` 的 `apiSimulationRouterSendFinish(data)` 把 `data` 作为 POST body 发出（`common/http/axios.js:18-19`，POST 一律进 body）；两个调用点 `views/manage/unionJob/lineNotify/js/Issue.js:449-459` 与 `:509-511` 构造 `new FormData()` 并 `append('roomId', route.query.id)`。
- 后端：`backend/src/main/java/com/nip/controller/simulation/SimulationRouterRoomController.java:88-93` 绑定 `@RestQuery(ROOM_ID) Integer roomId` —— 只读 URI 查询串，multipart body 不会填充它。
- 后果链：`roomId = null` → `SimulationRouterRoomService.java:332` `roomUserDao.findByUserIdAndRoomId(userId, null)` 返回 null → `:333` `orElseThrow(IllegalArgumentException)`；`common/exception/` 下**没有** `IllegalArgumentExceptionMapper`，落到 `GlobalExceptionMapper` → HTTP 500 + code 500 → 前端 `res.code == 200` 为假，弹「结束训练失败，请重试」。
- 为什么此前没被发现：`backend/src/test` 全文 **0 处** `sendFinish`（零测试覆盖），且它是全仓唯一「FormData body ↔ `@RestQuery`」组合（其余 3 个 query 型 POST——`cable/delete`、`cable/type/delete`、`cable/floor` 系——都在 api 模块里用 URL 模板 `?id=${data}` 传参，正确）。
- 修法二选一（跨栈改动，必须同一 commit）：前端改 `url: `/api/simulation/router/sendFinish?roomId=${roomId}``；或后端改 `@RestForm(ROOM_ID)`。推荐前者，与同域 `cable/delete` 等既有写法一致。

**新增 P2-01｜联合演练建房的「平均报」开关被静默丢弃**

- 前端 `views/manage/unionJob/lineNotify/Index.vue:111` 有 `a-switch v-model:checked="trainData.isAverage"`，`js/index.js:163` 把 `isAverage` 放进 `apiSimulationRouterAddRoom` 的 payload；`js/index.js:159` 同时把 `bdType` 硬编码为 `1`。
- 后端 `SimulationRoomRouterAddParam` 无 `isAverage` 字段（字段集 = `roomName,isCable,cableId,startPage,sendUserList,receiveUserList,content,bdType,bwType,bwCount,isRandom`）→ 该键被 Jackson 静默丢弃。
- 对照：兄弟页 `unionJob/broadcastTeacheing/js/broadcastTeaching.js:156` 的做法是 `bdType: isAverage.value ? 1 : 2`，并把 `isAverage` 显式注释掉（`:165`）。即平均报在本域的载体是 `bdType`，lineNotify 漏做这层映射。
- 后果：用户拨动开关无任何效果，报文始终按 `bdType=1` 生成。UI 承诺与后端能力不一致。

**其余「多传/少传」全部为良性死键（P3-01 汇总）**：`generalKeyPat/detail` 发 `uid`（DTO 是 `userId`，且 `GeneralKeyPatService.detail:393-413` 全程不读 userId）、`theoryKnowledge/getByIdAndToken` 与 `theoryKnowledgeTest/getTestContentByUserIdAndKnowledgeSwfId` 在 body 里重复发 `token`（头里已有）、`PostTelegraphKeyPatTrain/add` 发恒为空串的 `content`、`postTelexPatTrain/findAll` 在 body 里重复发已在 URL query 的 `trainType`、`theoryKnowledge/getById|deleteThroyKnowledgeById` 与 `theoryKnowledgeTest/saveUserKnowledgeSwfTestContent` 发后端不读的 `type`、`telexPat/saveTelexPat` 发 `time/themistake/theSuccessCount`。

### 10.2 WebSocket 消息体字段级 schema（已闭）

**覆盖**：7/7 `@ServerEndpoint`，四向核对（前端发→后端读、后端发→前端读、topic/type 码值双侧、类型转换假设），对端为 3 类前端封装共 15 个收发点（`Ws.js`、`UnionWs.js`、6 个组训页、4 个 simulation 对端）。码值单点回溯 `BaseConstants`、`CodeConstants`、`UnionConstants`、`SimulationDisturdTopicEnum`。

**新增 P2-02｜组训 WS 错误帧结构与前端解析契约错位**

- 后端 `ws/WebSocketGeneralKeyPatService.java:326-333`（`WebSocketGeneralTelexPatService`、`WebSocketGeneralTickerPatService` 同名方法一致）的 `sendErrMessage` 发出 `code=-1` 且 **`data` 是纯字符串**（如「房间不存在」）。
- 前端 6 个组训页无条件执行 `JSON.parse(JSON.parse(e.data).data)`（`views/manage/organization/electronKeyZuXun/train/student/js/handKeyTrain.js:510`、同域 `teacher.js:121` 等），内层对纯串 `JSON.parse` 抛 `SyntaxError`，直接崩掉 `onmessage` 回调。
- 触发条件真实存在：连接到已被删除的训练房（`WebSocketGeneralKeyPatService.java:162` `ROOM.get` 为 null）即发该错误帧。
- 对照：simulation 域两个对端（`train.js:240-245`、`useBroadTeacher.js:124`）都先判 `code === -1` 再 parse —— 说明正确写法在仓库里已有先例，组训域漏做。

**新增 P2-03｜组训 WS 入站 topic 解析无兜底，坏帧撕连接**

- `WebSocketGeneralKeyPatService.java:169-171`、`WebSocketGeneralTelexPatService.java:153-155`、`WebSocketGeneralTickerPatService.java:192-194` 的 `fromJson(...)` + `msg.get(TOPIC).toString()` 无 try/catch：缺 `topic` 键 → NPE，非 JSON 文本 → `JsonParseException`，均冒泡到 `@OnError` 后 `onClose` 撕掉连接（`WebSocketGeneralTickerPatService.java:274-278`）。
- 另外 4 个端点都有兜底：`WebSocketService.java:109-128`、`WebSocketUnionService.java:134-155`、`WebSocketSimulationService.java:422-442`。已登记的 `WebSocketSimulationService.java:585` `parseInt` 经复核确在 `:439` 的 `catch(RuntimeException)` 保护内（与裁判共识 7 一致）。

**新增 P3-02｜Key/Telex 的 `finish` topic 路由错位**：学员发 `topic=finish`（`handKeyTrain.js:493`），但 `WebSocketGeneralKeyPatService.java:194-198` 与 `WebSocketGeneralTelexPatService.java:172-174` 没有 `finish` 分支，消息落入 `joinUser`（学员）广播，`groupUser`（教员）收不到；只有 `WebSocketGeneralTickerPatService.java:209-216` 正确。当前被 REST 侧 201 `GeneralPatResultNotifier` 通知补偿而未暴露。

**新增 P3-03｜`topic=over` 只有消费方没有生产方**：`BaseConstants.java:31` `TRAIN_OVER="over"`，后端 `WebSocketSimulationService.java:714-715` 有 handler、前端 `Issue.js:126` 有消费分支，但全 `unionJob` 子树无任何发送点。

**已验证为干净**：`/websocket` 的 2001/3001 帧键名与实体字段逐一命中；Union 30 个码值与前端 `UnionWsCode` 一一对应；组训成功帧「后端塞字符串 + 前端双重 parse」是**自洽**的（`WebSocketGeneralKeyPatService.java:296`）；三处 200 帧的 map 键 `type/id/title` 三端点一致；`/status` 无字段契约（仅日志 + pong）。

### 10.3 Electron 文件服务（8000）契约（已闭）

**覆盖**：服务实现为 Express 子进程（`bw-frontend/electron/service/http/index.js`，由 `service/index.js:8-12` 在 Electron 主进程 fork —— Web 模式不起此服务），绑 `127.0.0.1:8000`（`electron/shared/ports.js:11,14`），唯一路由前缀 `/api/file`，共 4 条路由（`service/http/routers/file.js:7-11`）：`GET /`、`GET /getFile/*`、`GET /getReadStream/*`（支持 Range）、`POST /upload`。

**P3-04 升级为 P2-04｜上传响应是数组，4 处消费点全部当字符串用**

- 服务端 `service/http/controllers/file.js:202` 恒返回 `{data: names}`，`names` 是**字符串数组**。
- 4 个消费点全部对整个数组做字符串强转：`common/mixin/useUpload.js:14` `` callback(`/${response.data}`) ``、`views/manage/equipment/equipmentIndex.js:169` `'/' + response.data`、`views/manage/systemManage/basic/equipment/Index.vue:124` 原样存数组、`components/common/NipUEditor.vue:86` `` `${window.fileUrl}/${json.data}` ``。
- 单元素数组 `toString()` 恰等于元素本身，所以**单文件侥幸正确、多文件必坏**（拼出逗号分隔的伪 URL）。此前登记的 P3（仅 `NipUEditor.vue:86`）实为四处共性问题；`NipUEditor` 自身 `multiple` 被注释且只取 `this.files[0]`（`:62,:65`），故当前不可触发，但另外三处的组件配置不受此保护。

**新增 P3-05｜上传无服务端扩展名白名单**：`service/http/controllers/file.js:144-150` 的 formidable 未设 filter，扩展名限制只在前端 `useUpload.js:22-30`（jpg/png < 4MB）。风险受限于服务只绑回环且不对外暴露。

**已验证为干净**：路径穿越有 `resolveWithinRoot`/`sanitizeCurrentPath` 防护（`controllers/file.js:43-69,178`）；`window.fileUrl`/`uploadFileUrl`/`audioUrl` 单源派生自 `frontend/src/config/runtime.js:70-72` 的 `fileBase`，与 8000 常量一致。

### 10.4 WS 单例静态容器并发正确性（已闭，无缺陷）

**覆盖**：7 个端点类的全部 9 个可变 `static` 容器，逐个做 key 碰撞 / 复合操作原子性 / 泄漏 / 并发 `sendText` 四项判定。

**结论：不存在真实的 key 碰撞、结构性竞态或容器泄漏。** 证据：房间容器一律 `ConcurrentHashMap` + `compute`/`computeIfPresent` 单次原子重映射，空集合返回 `null` 移除父层 entry（无永久增长）；值内成员列表一律 `CopyOnWriteArrayList`（`dto/general/GeneralPatTrainRoomUserDto.java:21`、`ws/model/GeneralTickerPatTrainRoomUserModel.java:21`）；WS `@OnOpen` 与 REST `delete` 共用 `ws/service/RoomLifecycleLocks.java` 的 5 组 ×64 条带锁做跨面原子；清理路径以 `sameConnection` 会话身份守卫，旧连接的 `onClose/onError` 不会误删替代连接；出站广播统一 `getAsyncRemote()`（容器内部排队），`basicRemote` 仅用于单会话拒接/错误回执，故不存在同一 `Session` 被并发 basic 写。

**已有真并发测试**（这也是此前判断「运行期未验」偏保守的原因）：`backend/src/test/java/com/nip/ws/WebSocketDeleteOpenAtomicityTest.java`（在 open 校验中途暂停、delete 抢锁，断言 4 个房间族无残留键）、`ws/service/simulation/SimulationRoomLifecycleTest.java`（虚拟线程并发 remove+replace）、`ws/WebSocketUnionTest.java#concurrentChurnLeavesNoResidualState`（50 次并发进出后三表清零）。

**两条低危行为观察（非完整性缺陷）**：`WebSocketService.java:73-76` 位移旧连接只发 CLOSE 帧不强制 `session.close()`；`WebSocketUnionService.java:369-381,423-433` 的 `ur()`/`removeRoom()` 是 get-then-act 非原子（值本身是 COW 列表，最坏结果是多一次幂等清理）。

### 10.5 盲区闭合后的整改动作项（接 §8 编号）

| # | 动作 | 改动面 | 验证方式 |
|---|---|---|---|
| 8（P1） | 修 `sendFinish` 传参形态：前端改 URL query（推荐）或后端改 `@RestForm`。**跨栈同一 commit**，正文贴前端调用面 grep | `common/api/UserApi.js:130-136` ×或× `SimulationRouterRoomController.java:91` | 真实房间点「结束训练」返回 code 200 且发报人状态置 1；补一个后端集成测试（当前 0 覆盖） |
| 9（P2） | lineNotify 建房把 `isAverage` 映射成 `bdType`（对齐 `broadcastTeaching.js:156`），或后端 `SimulationRoomRouterAddParam` 增字段 | `unionJob/lineNotify/js/index.js:159,163` | 开关拨动后建出的房 `bdType` 随之变化 |
| 10（P2） | 组训 WS 错误帧：前端 6 个组训页收帧先判 `code === -1` 再 parse（照抄 `train.js:240-245`），或后端 `sendErrMessage` 把 `data` 也序列化成 JSON | 3 个 `ws/WebSocketGeneral*Service.java:326-333` ×或× 6 个组训页 | 连已删房不再崩 `onmessage`，页面显示拒因 |
| 11（P2） | 组训 WS 入站解析加 try/catch（照抄 `WebSocketSimulationService.java:422-442`） | `WebSocketGeneral{Key,Telex,Ticker}PatService` 的 `@OnMessage` | 发缺 `topic` 的帧连接不断、回错误帧 |
| 12（P2） | 上传响应消费统一取 `response.data[0]`（或服务端单文件时返回字符串） | `useUpload.js:14`、`equipmentIndex.js:169`、`systemManage/basic/equipment/Index.vue:124`、`NipUEditor.vue:86` | 多文件上传拼出的 URL 正确 |
| 13（P3） | Key/Telex 补 `finish` 分支路由到 `groupUser`（对齐 Ticker `:209-216`）；`topic=over` 决定补生产方或删消费分支；上传加服务端扩展名白名单 | 见各条 | 教员端直接收到 finish 帧；grep 无单侧码值 |

### 10.6 闭合后仍存的残余（诚实边界）

- POST 对账中 **36 处动态实参**（变量在调用前被就地改写、或来自跨文件组合）未能静态定键；本轮抽检 5 处全部为良性，但不等于全覆盖。
- Union WS 的 `scalarOrJson`（`WebSocketUnionService.java:174-182`）裸值/对象二分，静态只能核到两条路径，第三形态需运行期抓帧。
- simulation `disturb` 子协议的 `body` 为纯广播透传，后端不读的字段其契约由两个前端页自洽，未做前端页间同构核对。
- 本节全部结论为静态取证；P1-01 的「恒失败」是由绑定语义 + 异常链推导（JAX-RS `@QueryParam` 不接受 form/multipart body），未在运行实例上复现。
