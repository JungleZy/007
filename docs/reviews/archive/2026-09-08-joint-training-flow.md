# 训练/拍发/报底/评分主业务流（前后端联合评审分片）
- 日期：2026-09-08 / 范围：拍发/组训学员端全链路的跨栈契约（前缀 `TF`）/ 方法：只读取证
- 说明：本分片只报「把前后端放一起看才成立」的缺陷。纯后端算法缺陷（`docs/reviews/2026-09-07-*`）与纯前端缺陷（`docs/reviews/2026-09-08-frontend-review.md`）不重报，除非联合视角改变其定级/归属（见 §4）。
- 前端相对路径以 `frontend/` 为根，后端以 `backend/src/main/java/com/nip/` 为根。

## 0. 分片结论与计数
| 定级 | 条数 | 责任分布(FE/BE/双侧) |
|---|---|---|
| J-P0 | 0 | — |
| J-P1 | 0 | — |
| J-P2 | 6 | FE 1 / BE 1 / 双侧 4 |
| J-P3 | 2 | FE 1 / 双侧 1 |
| 合计 | 8 | FE 2 / BE 1 / 双侧 5 |

## 1. 契约清单（覆盖率）
覆盖的跨栈接口面：
- **链路①「电报拍发训练」postTelegram**（`common/api/TelegramApi.js`）：save / detail / findMessageBody(报底) / begin / saveContentValue(提交拍发) / finish / printBottomReport → `controller/PostTelegramTrainController` + `service/PostTelegramTrainService`。附带核对姊妹链 postTelex(`postTelexPatTrain/*`)、telegraphKey(`PostTelegraphKeyPatTrain/*`) 的 getPage 参数绑定。
- **链路②「组训学员端」*ZuXun**：datagram→`/api/generalTelexPat/*`（`GeneralTelexPatController`/`GeneralTelexPatService`）；handkey→`/api/generalTickerPatTrain/*`（`GeneralTickerPatController`）+`/api/socket/generalTickerPatTrain/updateTrainStatus`（`GeneralTickerSocketController`）；electronKey→`/api/generalKeyPat/*`（`GeneralKeyPatController`）；telex（子变体）。
- **VO 字段对账（3 个接口逐字段，见 §3）**：datagram `patDetail`→`GeneralTelexPatUserInfoVO`；datagram `statistics`→`GeneralTelexPatTrainStatisticVO`（含 `errorCollect`/`schoolReport`/`userTendencyVO`）；handkey `statistics`→`GeneralTickerPatTrainStatisticVO`（含 `errorInfoVO`）。另核对 detail VO（`GeneralTelexPatTrainVO`/`GeneralKeyPatTrainVO`/`GeneralTickerPatTrainVO`）与 electronKey `errorCollect`。
- **训练状态常量对账**：`PostTelegramTrainEnum`(0/1/2)、`PostTelexPatTrainStatusEnum`(0/1/2/3)、`TickerTapeTrainStatusEnum`。
- **结算/删除中断窗口 + 前端重试**、**localStorage vs 后端断点续训**、**前端重复实现后端计算**。

未覆盖/部分覆盖（见 §5）：postTelex/telegraphKey 的 deductInfo 键位与后端 `countScore` 的逐键对账（仅核对了端点形状，未逐键）；教员端 WS 消息路由；telex 子变体教员端仅取 scout 证据、未逐行复核。

## 2. 缺陷条目

### TF-J-P2-01　telex 组训学员端+教员端整体打到 electronKey(generalKeyPat) 域（错域）
- 结论一句话：「电传组训(telexZuXun)」整棵 UI 子树复制自 electronKey，所有 detail/getPage/uploadResult/finish/statistics/updateStatus 都请求 `/api/generalKeyPat/*`（电子键域），而非电传应属的 `generalTelexPat`(trainType=1)。
- 前端证据：`views/manage/organization/telexZuXun/train/student/student.vue:160`（`import {getElectronKeyZuXunDetails} from '.../electronKeyZuXun.js'`）、`telexZuXun/train/student/js/datagramTrain.js:8-11`（import `finishElectronKeyZuXun,getElectronKeyZuXunPageNumber,uploadElectronKeyZuXunPatResult`）、`:27`（WS `/generalKeyPatTrain/...`）；教员端 `telexZuXun/train/teacher/js/teacher.js:2-4`（import electronKey detail/statistics/updateStatus）[scout]。
- 后端证据：电传域为 `controller/general/GeneralTelexPatController.java:29`（`@Path("/generalTelexPat")`），电子键域为 `controller/general/GeneralKeyPatController.java:30`（`@Path("/generalKeyPat")`）；`GeneralTelexPatTrainVO.java:71-72` 表明电传本应是 generalTelexPat 的 `trainType=1`。
- 触发条件 → 后果：任何人使用「电传组训」→ 实际在电子键(generalKeyPat)库中建/控/结算训练；真正的电传(generalTelexPat)训练无法经此界面创建/查看，且电传与电子键成绩落入同一域相互污染。
- 责任归属：FE。
- 最小修复：telex 学员端/教员端改导入 `datagramZuXun.js`（generalTelexPat）并在建训时传 `trainType=1`；或若「电传组训」确非独立特性则删除该子树。

### TF-J-P2-02　electronKey「重新拍发」reset 打到 ticker 域（generalKeyPat 无 reset 端点）
- 结论一句话：electronKey 学员点「重新拍发」调 `resetHandKeyZuXunTrain`→`POST /api/generalTickerPatTrain/reset`（手键/ticker 域），而 electronKey 自身域 `generalKeyPat` 根本没有 reset 端点 → 本域训练未被重置（静默失效），且以 electronKey 的 trainId 去重置 ticker 库中同号训练。
- 前端证据：`common/api/electronKeyZuXun.js:38-44`（`resetHandKeyZuXunTrain`→`/api/generalTickerPatTrain/reset`）；`views/manage/organization/electronKeyZuXun/train/student/js/handKeyTrain.js:488-491`（`resetTrainInfo` 调之），可达路径 `:564-567`（status==1 时「重新拍发」→`resetTrainInfo()`）。
- 后端证据：`controller/general/GeneralTickerPatController.java:90-96`（`reset` 端点存在）；`controller/general/GeneralKeyPatController.java:33-186`（全表无 reset 端点）。
- 触发条件 → 后果：electronKey 学员训练中点「重新拍发」→ 本训练状态未在后端重置（假成功）；若 ticker 库存在同 Integer 主键的训练，则误重置该无关手键训练（跨训练写）。
- 责任归属：双侧协同。
- 最小修复：BE 为 `generalKeyPat` 增 `reset` 端点；FE 改调本域 reset（`electronKeyZuXun.js` 的 reset 指向 `/api/generalKeyPat/reset`）；或移除 electronKey 的「重新拍发」reset 路径。

### TF-J-P2-03　前端重复实现评分/正确率/速率，与后端各算一遍 → 双实现漂移
- 结论一句话：拍发链路前端本地重算 errorNumber/正确率/速率并随提交上传，后端 `countScore` 又独立重算并作为最终成绩 → 同一指标两套权威、公式不同，训练中「实时值」与成绩页「最终值」必然漂移。
- 前端证据：`views/manage/postJob/telegram/train/js/details.js:917`（errorNumber）、`:919`（`accuracy=((total-errorNumber)/total)`）、`:925`（`speed=1200/WPM`），并经 `savePostTelegramContent`（`details.js:948`，参数含 `errorNumber,speed,accuracy`）上传；组训 datagram 同理 `datagramTrain.js:177`（`speed=groups.length/(pageTime/60)`）。
- 后端证据：`service/general/GeneralTelexPatService.java:791-798`（后端正确率 `(patGroup-errorCode-muchLessCode)/patGroup`）、`:809-815`（后端速率 `ScoreMath.wpmScore`），与前端公式不同。
- 触发条件 → 后果：前端公式（分母 total、`1200/WPM`）≠后端公式（分母 patGroup、按规则系数）→ 学员训练中看到的正确率/速率与结算后成绩页展示的不一致；且上传的 accuracy/speed 语义与后端是否采用不明。
- 责任归属：双侧协同。
- 最小修复：确定单一权威（建议后端），前端只做展示、不再上传 accuracy/speed/errorNumber，或前端公式与后端 `ScoreMath` 对齐并注明为「预估」。

### TF-J-P2-04　handkey(ticker) 成绩/排名前端原样展示后端「反向速率分/0%组间隔/0均值」→ 放大后果
- 结论一句话：手键组训成绩由 ticker 服务算出（已知反向速率、组间隔占比恒 0%、各均值恒 0），前端不做任何复核直接展示并用于教员排名，放大了后端算法缺陷的用户可见后果。
- 前端证据：`views/manage/organization/handkeyZuXun/train/student/js/trainScore.js:73-88`（读 `userInfoList[].{speed,score,statisticInfo,...}` 直接显示）；教员端 `handkeyZuXun/train/teacher/js/teacher.js:172-183`（读 `errorInfoVO.{...}Min/Max` 直接作条形图）[scout]。
- 后端证据（原单侧编号，见 `2026-09-07-general-pat-service-review.md`）：`service/general/GeneralTickerPatService.java:940-943`(GP-P1-03 高于基准反被扣分)、`:737`(GP-P2-02 组间隔占比恒 0%)、`:782`(GP-P2-03 均值硬编码 0)。
- 触发条件 → 后果：任何手键组训结算 → 成绩页速率分方向反、组间隔占比 0%、点/划/码/词/组平均时长 0；教员 `schoolReport` 排名基于错误 `score` → 手键横向成绩与排名失真。
- 责任归属：BE（前端不需改，仅放大）。引用原编号 GP-P1-03 / GP-P2-02 / GP-P2-03。
- 最小修复：修后端 ticker `saveTrainUserResult`/`statisticsAllAvg`/`calculateRate`（与 Key/Telex 对齐）；前端无需改动。

### TF-J-P2-05　结算重复触发：前端无提交守卫+axios 无超时+双 end 触发；后端 finish 无 isFinish 短路
- 结论一句话：学员「结束」按钮与教员 WS `end` 都会调 `handlerSubmit('end')`→`finish`，前端 isFinish 守卫在异步 uploadResult 回调后才置位（存在竞态窗口），且 axios 无超时；后端 datagram/handkey 的 finish 无「已完成即返回」短路（不同于 PostTelexPat）→ 可并发/重复结算。
- 前端证据：`common/http/index.js:7`（`// timeout` 被注释）；datagram `datagramTrain.js:196-197`（`endTrain`→`handlerSubmit('end')`，无守卫）+`:63-67`（WS `end`→`handlerSubmit('end')`，仅判 `patUser.isFinish!=1`，而该值在 `:205` finishTrainInfo 内才置 1，位于首个 uploadResult 回调之后）；`:209` `finishDatagramZuXun` 无守卫。
- 后端证据：`service/general/GeneralTelexPatService.java:467-478`（finish 无 isFinish 短路，`:474` 每次都 `countScore`，`:739-740` `deleteByTrainIdAndUserId`→`saveAndFlush`）；对照有短路的 `service/PostTelexPatTrainService.java:240-242`（`if FINISH return`）。
- 触发条件 → 后果：学员点结束的同时教员结束（或刷新重发）→ 两次 finish 并发 → 重复 `countScore`（对用户 value 先删后插）→ 重复结算/竞态；配合 MyISAM（后端单侧 PT-P1-09 待验证引擎）删-插窗口崩溃可丢失该用户报底。
- 责任归属：双侧协同。
- 最小修复：FE 加 `submitting` 标志立即置位（先于异步）并禁用按钮、恢复 axios timeout；BE datagram/handkey finish 补 `isFinish==1 直接返回` 短路（与 PostTelexPat 一致）。

### TF-J-P2-06　断点续训依赖客户端 localStorage、忽略后端权威进度；handkey/electronKey resume 因 getItem 误用而 null 解引用
- 结论一句话：「继续拍发」的续训位（页/组/时长）取自客户端 localStorage 而非后端权威的 `existNumber/existPageNumber`；datagram/telex 共用键 `'datagramZuXun'+trainId`（键名误导），而 handkey/electronKey 把状态写成了 `getItem`（从未 `setItem`）→ 续训读到 null 后解引用抛 TypeError。
- 前端证据：datagram 写 `student.vue:258,271`（`setItem('datagramZuXun'+trainId,...)`），读 `datagramTrain.js:225`；telex 复用同键 `telexZuXun/.../student.vue:257,270`（但其域是 generalKeyPat，见 TF-J-P2-01）；handkey `student.vue:341,353` 与 electronKey `student.vue:298` 均为 `JSON.parse(localStorage.getItem('handKeyZuXun'+trainId, JSON.stringify(obj)))`（getItem 非 setItem）→ 续训 `handKeyTrain.js:874`/`:549` `obj.patPage` 抛错。
- 后端证据：`dto/general/GeneralTelexPatUserInfoVO.java:64-69`（后端已按用户维护权威 `existPageNumber`/`existNumber`/`existPage`）——前端续训未使用。
- 触发条件 → 后果：handkey/electronKey 点「继续拍发」→ TypeError，续训不可用；datagram 续训以本地缓存为准，与后端权威进度可能不一致（后端权威被忽略）。
- 责任归属：双侧协同（FE 修 setItem/键名并改用后端 existNumber；BE 提供/沿用权威续训位）。
- 最小修复：FE 将 onUnmounted/onbeforeunload 改为 `setItem`，键名按真实域区分；续训位优先读后端 `existNumber/existPage`。

### TF-J-P3-01　patDetail VO 无 `title` 字段，成绩页读 `res.data.title` → 学员名空白
- 结论一句话：组训成绩页取学员名用 `res.data.title`，但 `patDetail` 返回的 `GeneralTelexPatUserInfoVO` 没有 `title`（只有 `userName`）→ `scoreData.name` 恒 undefined，成绩卡姓名空白。
- 前端证据：datagram `train/student/js/trainScore.js:47`（`scoreData.value.name = res.data.title`）、`score.vue:14`（`{{scoreData.name}}`）。
- 后端证据：`dto/general/GeneralTelexPatUserInfoVO.java:15-114`（无 `title`，`:19` 为 `userName`）；`GeneralTelexPatController.java:61-66`（`patDetail` 返回该 VO）。
- 触发条件 → 后果：进入组训成绩页 → HJ 皮肤下姓名标签空白（`electronKey` 同型复制，同样受影响）。
- 责任归属：FE。
- 最小修复：FE 改读 `res.data.userName`（或 BE 在 VO 增补 `title`）。

### TF-J-P3-02　训练状态 0/1/2 契约脆弱：后端 datagram 服务混用两套 FINISH 语义
- 结论一句话：前端全链硬编码 `status==0/1/2`（无共享枚举），后端 datagram 服务同时 import `PostTelegramTrainEnum`(FINISH=2) 与 `PostTelexPatTrainStatusEnum`(FINISH=3/PAUSE=2)；当前一致仅因 `updateStatus` 恰好用 `PostTelegramTrainEnum`，任一侧改动即错。
- 前端证据：`datagramZuXun/train/student/student.vue:10`、`datagramZuXun/list/Index.vue:46-49`（`record.status==2?'查看报底':==1?'继续训练':'开始训练'`）、教员 `teacher.js:305-314`（`updateTrainStatus({status:1|2})`）。
- 后端证据：`common/constants/PostTelegramTrainEnum.java:10-14`(0/1/2)；`common/constants/PostTelexPatTrainStatusEnum.java:10-16`(0/1/2/3, PAUSE=2, FINISH=3)；`service/general/GeneralTelexPatService.java:61`(import NOT_STARTED@PostTelexPat) 与 `:384,387`(updateStatus 用 PostTelegramTrainEnum.UNDERWAY/FINISH)。
- 触发条件 → 后果：若有人把 datagram 的 add/updateStatus 统一改用 `PostTelexPatTrainStatusEnum`（FINISH=3），前端 `status==2` 的「查看报底/禁删」判定全部失效（已完成训练显示为「开始训练」、可被删除）。
- 责任归属：双侧协同。
- 最小修复：后端每域固定一个状态枚举并暴露给前端（或 detail VO 带语义化状态），前端引用共享枚举替代字面量。

## 3. 已核实为「一致/无问题」的关键契约
1. **组训训练状态 0/1/2 前后端一致**：`GeneralTelexPatTrainVO.java:52`/`GeneralKeyPatTrainVO.java:52` Schema「0未开始 1进行中 2已完成」＝前端字面量；`GeneralTelexPatService.updateStatus:384/387` 用 `PostTelegramTrainEnum`(0/1/2)；建训 `add` 置 0。（脆弱点另见 TF-J-P3-02）
2. **datagram 每页提交 uploadResult 幂等**：`GeneralTelexPatService.java:438-440` 按 `(trainId,pageNumber,userId)` 先删后插 → 学员同页重复提交不产生重复行（重复结算风险仅在 finish，见 TF-J-P2-05）。
3. **datagram `getPage` GET 传参正常**：前端薄封装 `common/http/axios.js:20-24` 对 GET 将 `data`→`params`，故 `apiPostTelexPatTrainGetPage`/`apiPostTelegraphKeyPatTrainGetPage`（`method:'get'` 带 data）实际以查询串到达后端 `@RestQuery`（`PostTelexPatTrainController.java:82-86`、`PostTelegraphKeyPatTrainController.java:80-84`）——**非缺陷**（纠正 chain-scout 的「GET 丢 body」初判）。
4. **datagram `deductInfo` 逐键对账全部匹配**（成绩页扣分表）：`score.vue:61-106` 读 `speedScore/errorCode*/muchLessLine*/muchLessGroups*/muchLessCode*/lessReturnLine*/lessPage*/errorPage*/nonStandart*/correctMistakes*` ＝ `GeneralTelexPatService.java:751-789,819/821` 的 `deductMap` 键。仅 `speedScore` 在 `avgSpeed==base` 时不出键 → `undefined*1=NaN`（极端边界，见 §5）。
5. **datagram/handkey 统计 VO 逐字段一致**（见下表）。
6. **handkey `updateTrainStatus` 路由一致**：前端 `handkeyZuXun.js:31-37`→`/api/socket/generalTickerPatTrain/updateTrainStatus` ＝ 后端 `GeneralTickerSocketController.java:23,42`（`@RequestPass` 免鉴权按内网口径 [已接受风险口径]）。
7. **GP-P1-01（`GeneralTelexPatService.findMessageBody` 恒 null）前端不可达**：组训 datagram 报底走 `getPage`（`GeneralTelexPatController.java:98-103`），无任何前端调 `generalTelexPat/findPage` → 该后端缺陷无跨栈后果（grep 全前端仅 `generalTickerPatTrain/findPage` 与 `postTelegramTrain/findMessageBody` 有调用）。

**VO 字段对账表**（缺字段标 ❌）

接口 A：datagram `patDetail` → `GeneralTelexPatUserInfoVO`（消费方 datagram `trainScore.js`/`score.vue`）
| 前端读取键(file:line) | 后端 VO 字段(file:line) | 一致 |
|---|---|---|
| `res.data.title` (trainScore.js:47) | 无（有 `userName` @VO:19） | ❌ |
| `totalNumber` (:48) | totalNumber @VO:83 | ✓ |
| `speed` (:49) | speed @VO:55 | ✓ |
| `accuracy` (:50) | accuracy @VO:50 | ✓ |
| `score` (:51) | score @VO:35 | ✓ |
| `duration` (:52) | duration @VO:80 | ✓ |
| `content` (:53) | content @VO:111 | ✓ |
| `deductInfo` (:58) | deductInfo(String) @VO:40 | ✓ |
| `ruleContent` (:59) | ruleContent @VO:89 | ✓ |
| `isCable` (:60) | isCable @VO:17 | ✓ |
| `pageCount` (:61) | pageCount @VO:84 | ✓ |

接口 B：datagram `statistics` → `GeneralTelexPatTrainStatisticVO`（消费方 datagram `teacher.js`）
| 前端读取键(file:line)[scout] | 后端字段(file:line) | 一致 |
|---|---|---|
| `schoolReport.{good,nice,belowStandard}` (202-204) | GeneralPatTrainSchoolReportVO:18-26 | ✓ |
| `errorCollect.{errorCode,muchLessGroups,muchLessLine,muchLessCode,lessReturnLine,lessPage,errorPage,nonStandart,correctMistakes}Number` (207-215) | GeneralTelexPatTrainErrorCollect:18-41 | ✓ |
| `userTendencyVO` (224) | GeneralTelexPatTrainStatisticVO:18 | ✓ |

接口 C：handkey `statistics` → `GeneralTickerPatTrainStatisticVO`（消费方 handkey `teacher.js`）
| 前端读取键(file:line)[scout] | 后端字段(file:line) | 一致 |
|---|---|---|
| `schoolReport` (167-169) | GeneralTickerPatTrainSchoolReportVO | ✓ |
| `errorInfoVO.{dot,line,codeGap,wordGap,groupGap}{Min,Max}` (172-183) | GeneralTickerPatTrainErrorInfoVO:18-45 | ✓ |
| `userTendencyVO` (185) | GeneralTickerPatTrainStatisticVO:23 | ✓ |

> **跨域字段名危害**：统计 VO 的错情字段在 datagram/electronKey 叫 `errorCollect`，在 handkey(ticker) 叫 `errorInfoVO`（`GeneralTelexPatTrainStatisticVO.java:21` vs `GeneralTickerPatTrainStatisticVO.java:20`）。各变体的 **live** `teacher.js` 恰好域-键匹配（datagram/electronKey 读 errorCollect、handkey 读 errorInfoVO），但复制来的 `teacherBack.js` 统一读 `errorInfoVO` 且都调 `generalTickerPatTrain/statistics`（见 §4）——对非 ticker 训练取到错训练/空数据（其 errorInfoVO 读多已注释规避，`schoolReport` 读仍生效）。

**调用时序表**

链路①：postTelegram 拍发训练（学员端，全部 POST `/api/postTelegramTrain/*`）
| # | 步骤 | 前端 file:line | 后端端点+service | 传参/返回关键字段 | 一致性 |
|---|---|---|---|---|---|
| 1 | 建训 | postJob/telegram/handkey/Index.vue:421 | /save → PostTelegramTrainService.save | formData / code | ✓ |
| 2 | 打开(详情) | train/HandKeyTrain.vue:315 | /detail | {id} / status,messageNumber,floorNow,messageBody | ✓ |
| 3 | 取报底(逐楼层) | train/js/details.js:148 | /findMessageBody | {id,floorNumber} / messageKey[].moresKey（无判空 details.js:154-158） | ✓端点 |
| 4 | 开始 | details.js:822 | /begin | {id} / startTime,status | ✓ |
| 5 | 提交拍发(每页) | details.js:948 | /saveContentValue | {trainId,floorNumber,messageBody,validTime,errorNumber,speed,accuracy,standard} | ⚠ 见 TF-J-P2-03 |
| 6 | 结算 | details.js:851 | /finish | {id,validTime,finishInfo,speed} / code→跳成绩 | ✓ |
| 7 | 成绩取详情 | train/js/trainScore.js:52 | /detail | deductInfo/statisticInfo/standards/resolver… | ✓ |
| 8 | 报底打印 | HandKeyTrain.vue:298-308 | /printBottomReport | 整块被注释=死代码 | — |

链路②：datagram 组训（学员端，POST `/api/generalTelexPat/*` 除注明）
| # | 步骤 | 前端 file:line | 后端端点+service file:line | 传参/返回关键字段 | 一致性 |
|---|---|---|---|---|---|
| 1 | 打开(详情) | student.vue:202 | /detail → GeneralTelexPatController.java:54-59 | {trainId,userId} / GeneralTelexPatTrainVO(title,status,userInfoList) | ✓ |
| 2 | 学员开始 | datagramTrain.js:107 | GET /startTrain → :126-132 | trainId,token | ✓ |
| 3 | 取报底(每页) | datagramTrain.js:152 | /getPage → :98-103 (getPage) | {trainId,pageNumber,userId} / messageVO | ✓ |
| 4 | 提交拍发(每页) | datagramTrain.js:185 | /uploadResult → :83-89 saveContentValue:397 | {trainId,pageNumber,patValue,validTime,speed} / code；幂等 :438-440 | ✓ |
| 5 | 结算 | datagramTrain.js:209 | /finish → :91-96 finish:467 | {trainId,userId} / List<UserInfoVO>；无幂等短路 | ⚠ 见 TF-J-P2-05 |
| 6 | 成绩取详情 | trainScore.js:40 | /patDetail → :61-66 | {trainId,userId,pageNumber} / UserInfoVO(无 title ❌) | ⚠ 见 TF-J-P3-01 |
| 7 | 成绩翻页 | trainScore.js:87,135 | /getPage → :98-103 | {pageNumber,userId,trainId} / messageVO | ✓ |
| 8 | 教员改状态 | teacher.js:305 | /updateTrainStatus → :75-81 updateStatus:379 | {trainId,status(1/2)} | ✓(脆弱 TF-J-P3-02) |

## 4. 与单侧评审的定级变化
- **前端评审 §2 / §3.2「telex/datagram student.vue 共用键 `'datagramZuXun'+trainId` → 数据碰撞（HIGH）」→ 联合定级下调为 J-P3（键名误导），但根因改判**：联合视角显示 telex 学员端整棵子树其实打 `generalKeyPat`（Integer 主键），datagram 打 `generalTelexPat`（String 主键，`GeneralTelexPatTrainVO.id` 为 String），两者 id 空间不同 → 该共用键在实践中**不发生**跨训练串号；真正的问题是「telex 全端错域」(TF-J-P2-01) 与「续训只信本地缓存/handkey·electronKey 续训崩溃」(TF-J-P2-06)。即：单侧的「同键碰撞」被高估，真正的联合缺陷在错域与续训实现。
- **前端评审 §2「复制粘贴主导/跨域污染（telexZuXun 导入 electronKey API）」（结构债）→ 联合定级上调为功能缺陷**：该跨域导入不只是维护性问题，而是让 telex 全流程与 electronKey「重新拍发」reset 真正打到错误后端域（TF-J-P2-01 / TF-J-P2-02），产生错域读写。
- **后端 GP-P1-03 / GP-P2-02 / GP-P2-03（手键 ticker 速率/比率/均值算法，单侧 P1/P2）→ 责任仍归 BE，但联合视角确认「前端零复核直接展示+排名」放大用户可见后果**（TF-J-P2-04），前端无需改。
- **后端 GP-P1-01（`GeneralTelexPatService.findMessageBody` 恒 null）→ 联合视角确认前端不可达、无跨栈后果**（§3-7），组训 datagram 报底走 `getPage`。

## 5. 未能验证的部分（缺前提）
1. **postTelex/telegraphKey 的 deductInfo 逐键对账**：仅核对了端点形状与 GET 传参，未把 `HandKeyTrainScore.vue`（chain1 成绩页）读的 ~14 组 deductInfo 键（wpmScore/dotMin*/line*/code(Gap)*/word(Gap)*/group(Gap)*/alterError*/errorWord*/quantoCode*/bunchGroup*/quantoGroup*/quantoRow*）与 `PostTelegramTrainService.countScore` 的 deductMap 逐键比对（缺后端 builder 逐行读取）。datagram 侧已逐键核对为一致（§3-4）。
2. **`speedScore` 边界 NaN**：`avgSpeed==base` 时后端不写 `speedScore` 键，前端 `score.vue:61` `deductInfo.speedScore*1` 得 NaN——需构造「平均速率恰等于基准」的结算才能坐实展示后果（静态判定为真，实际可达性未跑）。
3. **electronKey reset 误重置的实际后果**依赖 ticker 与 generalKeyPat 两表主键是否同号（均为自增 Integer，判定可能同号但未跑库确认）。
4. **telex 教员端错域**部分调用点行号取自 scout（`telexZuXun/train/teacher/js/teacher.js:129` 等），学员端已由本代理逐行复核；teacherBack 系列多为死/注释代码，未逐一复核其可达性。
5. 未跑构建/库；行号基于评审时快照。

## 附录 A：结构化缺陷条目（8 条，子代理原始输出）

### [J-P2] telex 组训学员端+教员端整体请求 electronKey(generalKeyPat) 错域

- 锚点：`frontend/src/views/manage/organization/telexZuXun/train/student/js/datagramTrain.js:8-11`（置信度 0.72）

「电传组训」(telexZuXun) 整棵 UI 复制自 electronKey，其 detail/getPage/uploadResult/finish/statistics/updateStatus 全部打 /api/generalKeyPat/*（电子键域）而非电传应属的 generalTelexPat(trainType=1)。前端: frontend/src/views/manage/organization/telexZuXun/train/student/js/datagramTrain.js:8-11 (import electronKey uploadResult/finish/getPage)、student.vue:160、教员 teacher/js/teacher.js:2-4[scout]。后端: 电传域 backend/.../controller/general/GeneralTelexPatController.java:29 (@Path /generalTelexPat) vs 电子键域 GeneralKeyPatController.java:30 (@Path /generalKeyPat)；GeneralTelexPatTrainVO.java:71-72 表明电传本应是 generalTelexPat 的 trainType=1。后果: 用「电传组训」建/控/结算的其实是电子键库训练，真正电传训练无法经此界面处理，两类成绩落入同一域相互污染。责任=FE。最小修复: telex 端改导入 datagramZuXun.js 并传 trainType=1，或删除该重复子树。联合定级: 单侧评审 §2 视其为结构债，联合视角上调为功能错域。

### [J-P2] electronKey「重新拍发」reset 打到 ticker 域（generalKeyPat 无 reset 端点）

- 锚点：`frontend/src/views/manage/organization/electronKeyZuXun/train/student/js/handKeyTrain.js:488-491`（置信度 0.8）

electronKey 学员点「重新拍发」调 resetHandKeyZuXunTrain→POST /api/generalTickerPatTrain/reset（手键/ticker 域），而 generalKeyPat 自身根本没有 reset 端点。前端: frontend/src/views/manage/organization/electronKeyZuXun/train/student/js/handKeyTrain.js:488-491 (resetTrainInfo)，可达自 :564-567（status==1 时「重新拍发」）；映射 common/api/electronKeyZuXun.js:38-44。后端: GeneralTickerPatController.java:90-96 有 reset；GeneralKeyPatController.java:33-186 全表无 reset。后果: 本域训练未被重置（静默失效），并以 electronKey 的 Integer trainId 去重置 ticker 库中同号训练（跨训练写）。责任=双侧协同。最小修复: BE 为 generalKeyPat 增 reset 端点且 FE 改指本域，或移除该 reset 路径。

### [J-P2] 前端重复实现评分/正确率/速率，与后端 countScore 双实现漂移

- 锚点：`frontend/src/views/manage/postJob/telegram/train/js/details.js:917-925`（置信度 0.68）

拍发链路前端本地重算并上传 errorNumber/正确率/速率，后端 countScore 又独立重算作为最终成绩，两套公式不同 → 训练中「实时值」与成绩页「最终值」必然漂移。前端: frontend/src/views/manage/postJob/telegram/train/js/details.js:917(errorNumber),:919(accuracy=(total-errorNumber)/total),:925(speed=1200/WPM)，经 savePostTelegramContent(:948) 上传；组训 datagram 同型 datagramTrain.js:177。后端: backend/.../service/general/GeneralTelexPatService.java:791-798(正确率=(patGroup-errorCode-muchLessCode)/patGroup),:809-815(速率 ScoreMath.wpmScore)。后果: 前端分母 total 与后端分母 patGroup 不同 → 展示不一致；上传值是否被后端采用不明。责任=双侧协同。最小修复: 定单一权威（建议后端），前端仅展示或与 ScoreMath 对齐并标注为预估。

### [J-P2] handkey(ticker) 成绩/排名前端原样展示后端反向速率分/0%组间隔/0均值

- 锚点：`backend/src/main/java/com/nip/service/general/GeneralTickerPatService.java:940-943`（置信度 0.7）

手键组训成绩由 ticker 服务算出（已知反向速率、组间隔占比恒 0%、各均值恒 0），前端零复核直接展示并用于教员排名，放大后端算法缺陷。前端: frontend/src/views/manage/organization/handkeyZuXun/train/student/js/trainScore.js:73-88(读 userInfoList[].{speed,score,statisticInfo})；教员 teacher/js/teacher.js:172-183[scout]。后端(原编号): backend/.../service/general/GeneralTickerPatService.java:940-943(GP-P1-03 高于基准反扣分),:737(GP-P2-02 组间隔占比 0%),:782(GP-P2-03 均值硬编码 0)。后果: 手键成绩速率分方向反、组间隔 0%、均值 0，schoolReport 排名失真。责任=BE(前端仅放大)。最小修复: 修 ticker 服务与 Key/Telex 对齐；前端无需改。

### [J-P2] 结算重复触发：FE 无守卫+axios 无超时+双 end；BE datagram/handkey finish 无 isFinish 短路

- 锚点：`frontend/src/views/manage/organization/datagramZuXun/train/student/js/datagramTrain.js:203-212`（置信度 0.72）

学员「结束」按钮与教员 WS end 都调 handlerSubmit('end')→finish，前端 isFinish 守卫在异步 uploadResult 回调后才置位（竞态），axios 又无超时；后端 datagram/handkey finish 无「已完成即返回」短路 → 可并发/重复结算。前端: frontend/src/views/manage/organization/datagramZuXun/train/student/js/datagramTrain.js:196-197(endTrain 无守卫)、:63-67(WS end)、:203-212(finishTrainInfo，isFinish 于 :205 才置位)；common/http/index.js:7(timeout 注释)。后端: backend/.../service/general/GeneralTelexPatService.java:467-478(finish 无短路,:739-740 先删后插)；对照有短路的 service/PostTelexPatTrainService.java:240-242。后果: 学员与教员同时结束/刷新重发 → 两次 finish 并发重复 countScore，配合 MyISAM 删-插窗口可丢报底。责任=双侧协同。最小修复: FE 加同步 submitting 守卫并恢复 timeout；BE finish 补 isFinish==1 短路。

### [J-P2] 断点续训只信客户端 localStorage 且 handkey/electronKey 续训因 getItem 误用而崩溃

- 锚点：`frontend/src/views/manage/organization/handkeyZuXun/train/student/student.vue:341-353`（置信度 0.72）

「继续拍发」的续训位取自 localStorage 而非后端权威 existNumber/existPageNumber；datagram/telex 共用键 'datagramZuXun'+trainId，而 handkey/electronKey 把状态写成 getItem（从未 setItem）→ 续训读 null 后解引用抛 TypeError。前端: datagram 写 student.vue:258,271 读 datagramTrain.js:225；handkey student.vue:341,353 与 electronKey student.vue:298 均为 JSON.parse(localStorage.getItem('handKeyZuXun'+trainId, JSON.stringify(obj)))，续训 handKeyTrain.js:874/:549 obj.patPage 抛错。后端: backend/.../dto/general/GeneralTelexPatUserInfoVO.java:64-69 已有权威 existPageNumber/existNumber/existPage 但前端续训未用。后果: handkey/electronKey「继续拍发」必崩，datagram 续训以本地缓存为准可能与后端权威不一致。责任=双侧协同。最小修复: FE 改 setItem+按域分键并优先读后端 existNumber。

### [J-P3] patDetail VO 无 title 字段，组训成绩页读 res.data.title → 学员名空白

- 锚点：`frontend/src/views/manage/organization/datagramZuXun/train/student/js/trainScore.js:44-52`（置信度 0.8）

组训成绩页取学员名用 res.data.title，但 patDetail 返回的 GeneralTelexPatUserInfoVO 没有 title（只有 userName）→ scoreData.name 恒 undefined，成绩卡姓名空白（electronKey 同型复制）。前端: frontend/src/views/manage/organization/datagramZuXun/train/student/js/trainScore.js:47(scoreData.value.name=res.data.title)、score.vue:14({{scoreData.name}})。后端: backend/.../dto/general/GeneralTelexPatUserInfoVO.java:15-114(无 title，:19 为 userName)；GeneralTelexPatController.java:61-66(patDetail 返回该 VO)。后果: HJ 皮肤下成绩卡姓名空白。责任=FE。最小修复: 前端改读 res.data.userName（或后端 VO 增补 title）。

### [J-P3] 训练状态 0/1/2 契约脆弱：后端 datagram 服务混用两套 FINISH 语义

- 锚点：`backend/src/main/java/com/nip/service/general/GeneralTelexPatService.java:383-388`（置信度 0.68）

前端全链硬编码 status==0/1/2（无共享枚举），后端 datagram 服务同时 import PostTelegramTrainEnum(FINISH=2) 与 PostTelexPatTrainStatusEnum(FINISH=3/PAUSE=2)；当前一致仅因 updateStatus 恰用 PostTelegramTrainEnum。前端: frontend/src/views/manage/organization/datagramZuXun/list/Index.vue:46-49(status==2?查看报底:==1?继续训练:开始训练)、student.vue:10。后端: backend/.../service/general/GeneralTelexPatService.java:383-388(updateStatus 用 PostTelegramTrainEnum.UNDERWAY/FINISH)、:61(import NOT_STARTED@PostTelexPat)；common/constants/PostTelexPatTrainStatusEnum.java:14-16(PAUSE=2,FINISH=3)。后果: 若把 datagram 改用 PostTelexPatTrainStatusEnum(FINISH=3)，前端 status==2 的查看报底/禁删判定全部失效。责任=双侧协同。最小修复: 每域固定单一状态枚举并暴露给前端，前端引用共享枚举替代字面量。
