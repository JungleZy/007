# 客户报障整改规格（Spec）

- 日期：2026-09-10
- 状态：**R01–R12/H1–H5/M1–M5 的仓内实现与验证已完成（证据见计划 §5.4/§5.5 与 T00.2 台账）；客户发布与现场验收（G4）未完成**。§3 的 G1–G3 业务口径已由用户冻结；未满足 G4 前不得宣布客户问题关闭。
- 源码取证基线：`3360221`；行号仅定位，实施前重新核对当前符号和调用面。
- 分析依据：[`../reviews/2026-09-10-customer-issue-analysis.md`](../reviews/2026-09-10-customer-issue-analysis.md)。实施计划：[`../plans/2026-09-10-customer-issue-fix-plan.md`](../plans/2026-09-10-customer-issue-fix-plan.md)。
- 当前全项目入口仍为 [`../reviews/2026-09-08-full-project-review.md`](../reviews/2026-09-08-full-project-review.md)；本 Spec 是客户报障增量，不重做既有整改。

## 1. 目标与红线

1. 覆盖客户 12 项及分析附录 H1–H5/M1–M5，建立“现象→证据→修复→验收”的可追溯关系。
2. 正式成绩由后端保存的原始记录和冻结规则计算；前端预估不得覆盖权威结果。采集可信度与服务端计算正确性是两件事，不声称能恢复已丢事件或抵御全部客户端伪造。
3. **保留单账号单 token 互踢；不建 user_session 表。**随机 token、TTL、刷新、安全存储沿用 [`2026-09-09-password-session-migration-plan.md`](../plans/2026-09-09-password-session-migration-plan.md) 的既有门禁，不另造会话方案。
4. HTTP 业务结果沿用 `ResponseResult`/`Response<T>` 和现有 mapper。203 `token不能为空`、204 `设备标识不能为空`、206 `账号登录凭证异常` 原值原文不变；参数错误用现有 202，授权拒绝用现有 207。真实系统异常不通过业务层吞掉或伪装成成功。
5. 端点参数、返回形态、评分字段、上传能力变动必须同步 API 模块和全部活跃调用者，跨栈同提交；不用忽略旧字段、别名、双套评分长期并存代替切换。
6. 事务内失败回滚；通知仅在提交成功后发送；生产 schema validate 前置 migration。已有 MyISAM 快照风险不能视作所有目标表都已迁移，实施前查实际库引擎。
7. 本次文档交付不执行发布、生产数据清理、数据库迁移或业务代码修改。Rust/WASM 重写、全站状态重构、全局 WS 鉴权改造及无关死代码清理不在范围。

## 2. 全量追溯清单

`R01`–`R12` 对应原分析第 1–12 节；不是另起一套缺陷编号。附录编号沿用原文。

| 要求 | 范围与终态 | 实施任务 | 验收 |
|---|---|---|---|
| R01 | 掉线原因提示准确、设备标识稳定、授权状态和剩余运行时长可理解；互踢不修 | T00、T16 | V01 |
| R02 | 收报初始化/调速同一换算、低速模式语义明确、音频参数就绪与采样节拍正确 | T00、T10 | V02 |
| R03 | 各训练域单位/用时/扣分对账，确定性公式修复，显示和扣分同源 | T02、T08 | V03 |
| R04 | 数据报/电子键上传或结算失败可见、保留数据、可重试；坏规则不得出假成绩 | T03、T08 | V04 |
| R05 | 倒计时越界只触发一次，提交失败可重试；服务端截止恢复按已确认口径实施 | T03、T09 | V05 |
| R06 | 手键完整、有序、码值与时长一致；校准与抖动处理可验证 | T05、T07 | V06 |
| R07 | 句号/改错控制事件不残留 #、不误删正文、不破坏翻页 | T06 | V07 |
| R08 | simulation 报底唯一、答案一致、通知可恢复；展示对齐与综合组网数值评分分开 | T11、T12、T13、T14 | V08 |
| R09 | 结束后能查看每人已提交明细；实时草稿需求经 G3 决策后兑现或明确为不需要 | T00、T11、T12、T13 | V09 |
| R10 | 电子键跟随训练类型/速度，冷启动参数可靠，输入到音频延迟有实测边界 | T00、T10 | V10 |
| R11 | 训练毫秒与比例往返互逆；基础练习区间加载/校验完整，不混用两类配置 | T07 | V11 |
| R12 | 客户版本证据、Web静态发布/Electron安装包及配套BE、题库模板导入、理论测试操作闭环 | T00、T01、T15、T17 | V12 |
| H1 | 电子键多码帧逐项消费，包括重复相同码 | T05 | V06 |
| H2 | 手键码率不由 speedLog 平均值控制；重复页/reset 不污染结果 | T08 | V03、V04 |
| H3 | General 手键划线扣分上限使用 dash.max | T02 | V03 |
| H4 | 手键 upload/finish/reset 以 token owner 为主体并检查参训资格 | T04 | V13 |
| H5 | 电子键 finish 删除 body userId 依赖并检查参训资格 | T04 | V13 |
| M1 | 电子键失败释放提交锁，页未确认时不丢本地内容 | T03 | V04 |
| M2 | 手键码值/间隔/时长在同一事件快照中交付 | T05 | V06 |
| M3 | 在线查询失败不提交已结束状态；离线有记录的参训者不因内存名单缺失而漏结算 | T03、T08 | V04、V05 |
| M4 | 手键 finish 的无效 validTime/finishInfo 前端字段移除；所需原始时序走逐页正式契约 | T04、T08 | V03、V13 |
| M5 | GeneralKey 固定/懒生成页的 value 空值形态一致，不再产生新 null 页 | T08 | V03 |

## 3. 实施门禁与决策责任

门禁依据本节已确认口径执行；用户已选择本轮完成全部仓内工作，真实客户部署/硬件验收与正式发布后续执行。业务选择见§3.1，技术字段表与迁移仍需落地，不把本会话决定伪装成客户现场已验收。

| 门禁 | 必须取得的决定/证据 | 责任角色 | 阻塞范围 |
|---|---|---|---|
| G1 评分与计时 | 分域单位、有效采集时长及60秒补交窗口已确认（§3.1）；继续冻结原始DTO、规则单位/版本、历史与进行中训练切换，并用真实场景对账 | 本会话用户确认业务；前后端负责人落实技术契约 | 技术前置T08/T09/T14；不再等待单位/计时选择 |
| G2 节拍与训练设置 | 固定35字符/分并拉长间隔；既有划比、组/页间隔与F2组合含义保留；完成校准样文、配置优先级与数字音频验证 | 本会话用户确认；实现者验证 | T07/T10落实；真实设备延迟转外部验收 |
| G3 详情与对齐 | 结束后的已提交明细与对齐展示；明确不新增实时草稿，不将对齐展示当数值评分 | 本会话用户已确认 | T13可实施，不再等待是否新增草稿的决定 |
| G4 客户与交付环境 | 本轮先完成全部仓内代码、隔离/自动验证和交付材料；真实部署/证书/安装器/硬件与正式发布由后续现场执行 | 用户/部署支持后续承接 | 不阻本轮仓内实现；外部矩阵保持未验收并移交 |

安全项 H4/H5 来自本轮分析附录，是独立可回滚任务；既有计划接受的“全域 WS 握手风险”不冒充本任务已关闭，也不隐式扩大授权重构到所有端点。

### 3.1 本会话已确认的执行口径（2026-09-10）

以下由用户通过两轮选项确认，不再按未定业务反复询问；技术字段表、迁移与验收仍须据此落实。

| 项目 | 确认结果 |
|---|---|
| 手键拍发（个人/组训） | 字符/分钟 |
| 电子键拍发（个人/组训） | 四码组/分钟 |
| 数据报/电传发报 | 字符/分钟 |
| 数据报/电传收报 | 字符/分钟；播放WPM模式独立 |
| 用时 | 有效采集时长，扣显式暂停，保留键间/组间停顿和思考时间；服务端保存/校验事件，不将传输等待计入有效用时，不声称客户端时间完全防作弊 |
| 低速模式 | 固定35字符/分的符号速度，通过拉长间隔降低平均速度；界面区分符号速度和平均速度 |
| 教员详情 | 结束后的已提交明细与对齐展示；本轮不新增训练中实时草稿 |
| 倒计时 | 截止时冻结新输入，保留60秒补交窗口，只接收截止前采集的数据；窗口结束按已收到内容结算缺页，之后不得暗改已结算成绩 |
| 本轮范围 | 完成全部仓内代码、自动验证、隔离验证与交付材料；真实客户部署、硬件验收及正式发布移交后续，不标为已通过 |
| postJob手键配置（2026-09-11补充） | 用户选择保留纯自校准：不新增持久化初值、固定模式或配置入口；保留现有初值及开始符号自校准，纠正原计划的不存在配置读取问题 |
| 教员手动结束组训（2026-09-11补充） | 用户选择“学员收到通知时停止”：General手键/电子键收到通知即停止新采集，先处理通知前已收到的队列，再在60秒收尾窗口内补交；通知延迟可使实际停止时刻不同。不把教员HTTP到达时刻硬套为采集截止，倒计时仍严格服从服务端deadline |

未另行改变的划比、组/页间隔比例、F2组合含义、扣分规则与舍入口径沿用既有配置；已完成历史成绩不自动重算。T08/T10必须同步单位标签、规则输入与计算，不能仅换分母；旧规则单位含混时应明确核对/重新保存，不凭矛盾代码盲乘除4。G1–G3的上述业务选择已冻结，T00剩余为可在仓内完成的字段/迁移设计，G4按外部验收移交。


## 4. 评分、提交和授权契约

### 4.1 活跃调用面

| 服务端路径/域 | 当前前端 API 入口 | 实施边界 |
|---|---|---|
| `/api/postTelexPatTrain/begin,finishPage,finish,detail` | `bw-frontend/frontend/src/common/api/TelegramApi.js` | 覆盖 postJob 数据报与该 API 的其它实际调用者，按 trainType 分别对账 |
| `/api/generalTickerPatTrain/uploadResult,finish,reset` | `bw-frontend/frontend/src/common/api/handkeyZuXun.js` | 手键学生端；教员结束经 socket controller 内部结算不能误用学生 token |
| `/api/generalKeyPat/uploadResult,finish,reset,patDetail,detail` | `bw-frontend/frontend/src/common/api/electronKeyZuXun.js` | 电子键学生/教员及回放；不重新实现已有 reset 域隔离 |
| `/api/groupNetTrain/submitAnswer,details` | `bw-frontend/frontend/src/common/api/TrainingDetails.js` | 综合组网，独立于 simulation 对比页 |

关键实现：`backend/src/main/java/com/nip/service/PostTelexPatTrainService.java:235-252,734-748,850-938`、`backend/src/main/java/com/nip/service/PostTelegramTrainService.java:803-823`、`backend/src/main/java/com/nip/service/general/GeneralTickerPatService.java:498-557,583-643,881-981`、`backend/src/main/java/com/nip/service/general/GeneralKeyPatService.java:276-306,429-494,766-786`。LSP 当前未配置；实施时先检查可用性，可用则查 references，否则 API/导入/路由/内部调用搜索并记录替代证据。

### 4.2 确定性修复

- 三页在累计 100/160/200 秒结束时，逐页用时必须为 100/60/40，而非 100/60/140；保存“上一页结束点”，不要保存增量再相减。
- trainType!=4 起评分使用训练规则满分，而非写死 100。
- 速率项按现有 `backend/src/main/java/com/nip/common/utils/ScoreMath.java:51-72`：高于 base 按 r 加，低于按 l 扣。r/l 的旧反向注释不能作为实现依据；该工具已明确允许 r/l 缺省为 0，其它必填规则不得类推全部默认 0。
- General 手键 dot.max=1、dash.max=5、lineScore=7 时划线扣 5，不是 1；最终 score 与 deductInfo 必须一起验证。
- General 电子键结算的 `deductInfo` 必须始终输出 `speedScore`（等于基准输出 `0`，正值补 `+`），与个人电子键同一口径；缺该键会让成绩页把码率扣分渲染成 `NaN`，违反“显示与扣分同源”。
- 逐页码率图表只消费后端已保存记录派生的统计：个人手键 `postTelegramTrain/detail` 返回 `pageAnalyzeVOS`（`pageNumber`/`patNumber`/`totalTime`，毫秒取自 `captureIntervals`），个人电子键 `details` 的统计行必须带 `pageNumber`，组训电子键 `patDetail` 必须回传父训练 `protocolVersion`。前端不得为画图新增逐页请求，也不得按客户端汇总值重算。
- 单位标签由 `protocolVersion`（新协议=1）与规则 `rateUnit` 决定，不再按旧 `wpm.type` 推断新成绩；`protocolVersion=0` 的历史成绩保持原数值并显式标注为历史口径。列表页与成绩页同源：用时统一按毫秒格式化（`activeMillis`，缺失时按秒换算），不得把秒直接交给毫秒格式化函数。

### 4.3 原始记录与权威结果

- G1 按模式冻结一张字段表：已有 DTO 字段→语义→单位→来源→必填性→验证→存储→消费者；缺失的开始/暂停/恢复/结束时序需跨栈同提交加入，不能仅删 speed 字段就声称后端能算用时。
- 逐页以训练、token owner、页号定位；记录单调事件序列及采集时序、服务端接收时刻、该次训练冻结的规则。客户端采集时序只可验证顺序/非负/区间一致性，不证明真实硬件时间或完全抗篡改。
- 计算时使用实际已保存的页/事件，不以客户端 speed/errorNumber/accuracy/totalScore 作为正式输入；前端删除这些最终输入依赖，实时显示可保留“预估”并在结算后替换。
- 不跨域强行除以 4。按 G1 的域单位归一后，显示码速、扣分码速、总用时与明细同源；重放同一保存记录与同一规则必须得到同一成绩。
- M4：移除 finish 里被 DTO 丢弃的 validTime/finishInfo；若某项真实有用，将其放入 G1 正式逐页原始记录契约，而非 finish 的无效冗余字段。
- M5：新建/懒建 GeneralKey 页都初始化相同空集合形态；存量 null 在停写迁移中规范化为相同语义，不永久保留双形态读取分支。
- 存量已完成成绩不无条件批量重算；没有原始时序的旧记录不能编造用时。切换时让旧进行中训练完成或明确终止，保留既有历史结果，新的规则/协议仅用于切换后的训练；具体停训窗口在 G1/G4 落实。

#### 4.3.1 仓内技术切换契约（2026-09-11）

| 字段 | 语义、校验与存储 |
|---|---|
| protocolVersion | 新训练为1，迁移存量为0；已完成历史只读原结果，缺时间轴的旧未完成训练拒绝按新算法开始/结算，须先完成或明确终止并新建，不编造采集时长 |
| attempt | 个人训练或组训学员的整型轮次，初值0；逐页写必传且须匹配；reset持父行锁递增并清除本轮原始/派生记录，拒绝旧在途页 |
| captureIntervals | 逐页原始采集区间数组`[{startedMs,endedMs}]`，毫秒整数，相对该轮服务端begin时间轴；同页可包含多次进入/暂停恢复区间，有序、非负、不重叠；暂停、提交等待关闭区间，思考及键/组间停顿仍在区间内 |
| 服务端采集锚点 | begin/恢复/回读返回serverElapsedMs，前端以收到响应时performance.now建立锚点，不用墙钟递增；刷新重新取锚点，不将不同页面的performance原点混算 |
| receivedAt | 服务端收到逐页请求的时刻，持久化供区间上界校验，不作为采集起止替身；不允许区间超过服务端已过时间或训练截止界限；不同已保存页的区间也不得重叠 |
| 正式结果 | 后端从保存的原始正文/事件计数、采集区间求有效用时，同一结果供speed/总用时/扣分/详情；删除客户端speed/errorNumber/accuracy/totalSpeed等汇总评分输入，不用客户端汇总字段兜底 |
| 规则单位 | 冻结规则JSON显式包含rateUnit：CHARACTERS_PER_MINUTE或FOUR_CHARACTER_GROUPS_PER_MINUTE；单位不明确的旧规则须核对并重新保存，不隐式乘除4。电子键四码组按实际拍发字符数折算，只有一次最终舍入；空白/已识别控制符不计，未知正文字符仍计数 |

共享Java原始类型为`CaptureInterval`，`CaptureTimeline`校验区间及跨页不重叠。各训练域保留既有错误统计/扣分逻辑，不合并为一套评分算法。DTO、实体、规则编辑、逐页采集、详情、迁移同批切换；服务端校验只限制可观察时间边界，不证明客户端/硬件时间不可伪造。

### 4.4 提交状态与幂等

- 前端状态最少区分待提交、提交中、页已确认、结算成功、可重试失败。finish 必须等待所有应提交页确认；只有结算 code===200 才跳成绩页。HTTP 成功不是业务成功。
- `finally` 释放锁；错误页保持内容/页号/时序和身份归属，不递增页号、不无条件跳转。鉴权失败不自动重复写请求、不把 A 用户的草稿发到 B 会话。
- 相同页请求重试不得追加 speedLog 或重复影响统计；提交成功但响应丢失后，客户端先回读服务端进度再恢复。不同内容重提只允许在未结束且仍有权限的训练中替换该页。
- 后端同训练/学员的 upload、finish、reset 串行化到数据库事务锁；已有 isFinish 短路只保证顺序重复调用，不证明并发安全。结束后禁止 upload 将 isFinish 重置为 0。
- reset 同时清理该次训练的页、派生评分和日志，保留报底；与旧在途请求隔离。G1 冻结训练轮次/请求版本约束，防 reset 后旧页重新落库。
- 教员结束训练以 DB 参训/已保存记录为依据，不以在线 socket 列表过滤应结算者；在线查询故障不得导致“已结束但无人结算”。失败回滚状态和分数，恢复后可重试；不能 catch 后返回成功。

### 4.5 身份与内部结算

- 学员写接口从 token 取得主体，验证属于该训练且角色/状态允许写入；前端和 DTO 同提交移除 userId/uid 身份选择字段。
- 教员查询/结束其它学员不能套用“学生接口忽略 userId”；使用已有教员入口检查管理该训练的权限，再调用明确的内部结算方法。`GeneralTickerPatService.updateStatus` 内部逐用户结算是必须迁移的调用者。
- A token 携带 B 身份、非参训用户、结束后重传必须不改变 B 的页/成绩/状态；正常参训学员与授权教员功能保持。

### 4.6 倒计时

- 客户端使用单调经过时间驱动剩余时间，tick 只刷新显示；开始前时长为正、开始后冻结，剩余值跨过 0 也只触发一次。暂停/睡眠/恢复按 G1，不凭实现方便改变含义。
- 服务端 deadline 从真实 begin 事件而非创建训练时起算；暂停/恢复同事务更新其权威状态。持久化 deadline 后由周期恢复扫描与启动恢复检查触发同一幂等结算路径，不能只存字段。
- 客户端离线后，服务端只能结算已收到的记录；未上传页如何处理、迟到页是否接受及截止宽限由 G1 固定。没有这些决定不得上线自动结束，更不得声称恢复离线期间未传数据。
- General教员手动结束按§3.1补充选择处理：status=3表示收尾补交，不是最终完成；窗口从服务端收到结束请求起算60秒，学员收到通知停止并串行确认已采集队列/最后页，服务端按有效采集区间校验，不以教员请求时刻整页拒绝正常延迟。窗口后按已收原始记录及缺页结算，全部成功才status=2；失败保持可重试收尾态。该人工控制协议不放宽PostTelex倒计时截止规则。

## 5. 事件、控制符、配置和音频

### 5.1 有序输入

- 影响入口：`bw-frontend/frontend/src/common/utils/WebSerial.js`；`bw-frontend/frontend/src/views/manage/organization/handkeyZuXun/train/student/` 与 `bw-frontend/frontend/src/views/manage/organization/electronKeyZuXun/train/student/`；postJob/preJob 复用手键事件的活跃训练页。
- 事件由生产者逐项同步调用消费者或有界 FIFO 顺序消费，码值、按下/抬起、间隔属于同一个不可变快照；不再把单个 ref 的最后值当事件流，也不以字符串/数字交替强迫 watcher 触发。
- 重复相同电子键码不可丢；串口分包/粘包、重复按下/缺抬起必须显式恢复。不将不存在的硬件时间戳伪造为逐事件真实发生时刻。
- 点阈值以有效校准/明确固定配置为准；≤10ms 抖动不参与编码和校准。离页销毁订阅/定时器，下一训练不得消费上一场残留。
- 上游 `bw-frontend/frontend/src/common/ws/MessageWebSocket.js:89-108` 也在范围：不能因visible判断丢弃已收到的原始帧。G1若规定后台继续训练，采集不得跟UI绘制暂停一起丢弃；若需暂停必须显式进入训练暂停协议。两模式均覆盖隐藏/最小化后恢复，不能只测试页面前台FIFO。

### 5.2 控制符

- 句号翻页、当前组改错、前组改错作为控制事件优先识别，保留现有字符表对普通码的含义。
- 临时显示记录与控制事件绑定，识别后仅撤销它产生的占位；干净三组路径只有两组先被渲染，不可固定删除三个字符。
- `legnth` 修为有效长度判断；覆盖从首组改错、跨组/翻页、连续控制符、完整码和分片码。真正未知码仍可显示 #，不得用隐藏所有 # 掩盖丢码。

### 5.3 两类配置分开

- preJob训练DTO中点/划/间隔/大间隔的毫秒边界与UI比例互为逆变换，不重复减1或反复parseInt截断；既有校准行为不另行改义。
- postJob手键当前无持久化点划/间隔字段或固定模式，只有初值与开始符号自校准。2026-09-11用户明确保留该现状，不新增配置能力；原“加载本域训练配置”要求撤销，不再把不存在的getBasicSetting调用当缺陷。
- 基础练习已有 getBasicSetting 区间分级，修的是异步加载、空/坏区间和异常区间缺失。加载未完成/失败时不允许开始；显示可修复错误，不能首键 TypeError。
- `/api/telegramTrain/saveSetting` 是整份区间保存；验证数值有限、上下界合法、正区间及异常区间完整，再事务替换。错误时旧配置不变，禁止 '<undefined' 入库。
- 定位：`bw-frontend/frontend/src/views/manage/preJob/telegram/handkey/js/telegram.js:222-247,417-440`、`bw-frontend/frontend/src/views/manage/preJob/telegram/train/HandKeyTrain.vue:766-772`、`bw-frontend/frontend/src/views/manage/preJob/telegram/train/js/basicTrain.js:38-70,133-154`、`backend/src/main/java/com/nip/service/TelegramTrainService.java:373-381`。

### 5.4 播报节拍

- 在既有 Morse 工具入口收口换算，不新增一套互不兼容的播放器。输入明确单位、rate、报文类型、划比和间隔；输出点长/各段样本数。删除初次乘 1.18、改速除 1.18 的双口径及未定义 speedRate。
- 常规 WPM 的现有 1200/rate 点长规则、码/分经验 dots/type 与低速模式分开描述；G2 选定校准报文和间隔后才可删除经验补偿。不可对任意报文组成承诺相同误差。
- 就绪前保存**最新完整参数状态**，ready 后一次回推 criterion/ratio/frequency/volume 等；不需要无界排队每次参数变更。每次训练显式设置全量参数，避免单例残留。Web须等待用户手势初始化/恢复音频，Electron虽mounted尝试初始化仍须验证实际ready；未ready不允许开始有声训练并假装在播放。
- processor 按样本计时，跨渲染块保留余数；暂停/恢复/清空对应同一采样游标。每段取整误差 ≤1 sample，整篇校准报文误差 <2%；不是把 Date.now 换个变量名。
- postJob/组训电子键取消固定 playSpeed=80，跟随本场配置且按 type 选择标定；preJob 已有速度跟随，作为回归入口，不宣称同一缺陷。
- 去掉热路径调试输出和固定 1s/3s 延迟订阅，以实际 ready/订阅生命周期驱动；F2 窗口按 G2 的组合键协议优化，不先随意调成一个更小的常量。端到端延迟须目标机实测，不以数字采样正确代替硬件响应验收。
- 定位：`bw-frontend/frontend/src/common/utils/voice/MorseVoiceHighPerformance.js:274,297-304,427-432`、**实际静态资源 `bw-frontend/frontend/public/processor.js`**、`bw-frontend/frontend/src/common/utils/ElectronMorse.js`、`bw-frontend/frontend/src/views/manage/postJob/receive/train/js/receiveTrain.js:238-267,617-626`。src/common/utils/processor.js是不同副本，不能只改它；T10确认引用后收口单一运行源，Web dist与Electron安装包均校验实际加载的processor hash。

## 6. 组网数据、通知与详情

### 6.1 数据权威及唯一性

- `simulation_router_room_page` 一行是一个组，不是一页：唯一键 `(room_id,page_number,sort)`；`simulation_router_room_page_value` 一行是一人一页：唯一键 `(room_id,user_id,page_number)`。两表在 current 快照为 InnoDB，仍需实际部署核对。
- 保留有界懒生成；在事务中锁已有房间 DB 行、重查页、生成并落库完整页再返回。创建/读取/删房遵守相同锁顺序；仅 Java 锁不能覆盖多实例。不得未经容量限制全量预生成任意 bwCount。
- uploadResult 现为整份答案数组，事务内替换本用户整份页集合，包括删掉不再提交的尾页；归属从 token 验证，页码/超页按 G3 决定拒绝或显式展示，不制造标准报底。
- 重复请求不增加总页数；DB 查询不再依赖重复行下无排序 firstResult 的偶然返回；只有保存成功才更新已提交状态。
- 迁移前盘点空键和重复键：相同重复可备份后规范化；冲突答案/冲突报底没有可靠时间或训练来源时**停止迁移并人工裁决**，不能按 UUID 或任意 firstResult 选真值。上述唯一键各列在清理后设为 NOT NULL，入参同步拒绝空键，防 MySQL 多个 NULL 绕过唯一性；唯一约束、实体、DAO、演练同提交。

### 6.2 通知与恢复

- 复用 `bw-frontend/frontend/src/common/ws/SocketConnection.js`，各房型保留自己的消息封装；验证已有 `WebSocketHeartbeat.respond` 和 ping/pong，不让控制帧进入双重 JSON.parse。
- REST 事务提交后，服务端向房间内全体教员发同一身份完整的结果变更通知；前端收到后回读 REST 权威详情。不再客户端发 result 改 DB 状态，不发送未提交“完整快照”。
- 报务房当前第一位收到补 id 消息，余下收到原始缺 id 消息；不是其它人完全无广播。新协议不得保留这两个版本，也不因移除 break 再造成重复通知。
- 首次进入、断线重连和结果通知触发详情读取；教员可见页面且仍等待结果时，每 5 秒最多一个在途快照请求，隐藏/离页/结束且全部结果已齐时停止。重连保留现有退避，禁止叠加循环。
- 正常同局域网、两名教员、一名学员场景：从 REST 成功响应起，两端无刷新 2 秒内可见；刻意丢通知时 10 秒内经补偿可见。网络不可用时明确显示离线/待同步，不承诺不可达链路时限。
- DB 为持久状态，内存 Map 只代表在线连接；重启后从 DB 恢复成绩/提交状态，连接重建后恢复在线显示，不能把“在线”当“已结算”。

### 6.3 详情与综合组网

- 默认修复已提交明细可见性：结束后首次进入/刷新/重连能查看每人准确页数、答案与同一份报底。
- G3已确认只做结束后的已提交明细，不增加实时草稿保存/推送协议；T13必须完成可见性与对齐，不得把“未增加新能力”误记为现有明细问题已修。
- 对齐按 G3 确认的组/字符粒度在有界单页实现，漏/多码不连带把后续全部标错，未知/超出报底单独显示；不改后端数值扣分。
- 综合组网 `GroupNetTrainService` 则按本域题目/答案/规则计算正式分数，移除客户端总分输入和浮点直接相等判定依据；range 与规则 deviceId 的对应由真实域字段确定，不能把路由字段直接改名当修好。

## 7. 凭证、授权与题库交付

### 7.1 R01

- 前端按 203/204/206 解释缺凭证/缺设备标识/凭证不匹配；206 只提示可能他处登录，不断言原因。后端码文不改，仍只弹一次登录提示。
- deviceId稳定化：Electron复用 `bw-frontend/frontend/src/common/utils/machineCode.js` 的硬件接口；Web为**浏览器profile+origin范围**的持久化标识，不是物理机器ID。有效会话不在请求途中换ID；读取失败显式报错不清记录、不重生标识。清站点数据、换profile或HTTP→HTTPS后的新origin可能需要重新登录/授权，不能承诺硬件式跨环境稳定。
- token 与授权存储分开：稳定 ID 不修复 token 丢失或重放。现有授权 storage_error 流程保留可恢复错误，不把它当未授权清库。
- 剩余累计运行时长 ≤604800 秒时预警，显示可运行小时而非自然日；授权耗尽仍走现有门闸。续发、设备不匹配和存储失败分别说明，禁止用“验证码”泛称所有登录失败。

### 7.2 R12

- 先核对客户实际 FE/Electron/BE 制品，不用本地 tag 缺提交推断客户版本。已知 `1c40aae`、`9596c6c` 必须同时被交付提交包含。
- release 必须依赖 frontend/test/build 成功，归档 FE dist 和 BE 制品；记录各自版本、同一源码 SHA、目标架构与校验和。tag/推送只是后续发布动作，本计划不授权当前直接发布。
- 两种交付分开：Web为本次frontend/dist→静态站点/代理，Electron为本次frontend/dist→public/dist→安装包；两者都与配套BE的SHA/hash清单对齐。核验实际站点资源及安装包资源，拒绝旧缓存/旧public/dist残留；不能以构建成功或仅后端release附件作为任一模式交付证明。
- 模板保留稳定机器字段；首张“题库模板”只预填当前二级节点levelId，题目字段留空；中文说明和示例放第二张“填写说明”，不导入示例。导入可空levelId但不能显式指向其它题库，目标在开始读文件时固定，不随稍后的页面选择变化。
- DOCX/XLSX 仍按当前前端能力解析后一次调用 saveBatch；后端 exportTemplate 仍为 JSON 列规格，不改为假字节流。仅 code===200 提示成功；坏行整批回滚并定位原因。
- T15已补选择答案整数/范围与实际Excel行号检查。DOCX填空导出以“答案（JSON）：”明确数组语法，普通“答案：”中的方括号仍是文本，中文分号可分隔手工多空答案；不靠首字符猜JSON并改变已有文字含义。
- T15实测新增的建考容量阻塞由 `backend/database/migrations/2026-09-10-01-theory-json-capacity.sql` 及实体映射修复：五个考试快照列表和学员content对齐快照已有longtext，旧ORM误建varchar(255)的库可迁移。单题表字段能力不在此迁移中扩张；实际回归和双快照演练见计划T15.1。
- “不会操作”交付具体操作步骤及真实界面走查：选择题库→导模板→填题/选项/答案→导入→确认题目；教员选题建卷→学员进入测试→作答交卷→查询成绩。操作员按实际入口完成，不把文件上传成功代替理论测试闭环。

### 7.3 双运行模式硬边界（两者均须交付）

“双端”表示FE/BE，**不等于已经覆盖Web/Electron两种前端运行模式**。共用Vue业务、HTTP/WS契约和评分算法，不复制两套业务实现；差异局限于现有运行环境/配置/硬件接口层。G4按以下矩阵记录，不把Web降为未承诺的可选入口。

| 维度 | Web部署要求 | Electron壳要求 | 任务/验收 |
|---|---|---|---|
| 资源与路由 | 发布frontend/dist；保持现有hash路由，验证登录/训练深链刷新、动态chunk、localforage脚本、processor.js及MIME；子路径部署须另验证所有绝对资源路径，不因base='./'就宣称支持 | 打包态file加载public/dist，不拿dev localhost页面代替安装包；验证同一批JS/worker/字体与外部文件访问 | T01/T10，V02/V10/V12 |
| 地址与代理 | 按实际HTTP/HTTPS配置注入window.serverConfig；HTTPS下API/WS/上传/文件均无活跃混合内容。验证/data→后端/api调用、/push→WS、/file→文件服务的前缀映射与Upgrade；跨域时校验Origin、token/deviceId预检与下载行为，不改成通配开放来掩盖失败 | 通过现有IPC system.getConfig取配置，验证本机/局域网地址；不能给file:拼file://API、也不能把/data等Web代理前缀强塞进桌面直连 | T01/T12，V04/V08/V12 |
| Web环境隔离 | 没有window.require/electron/IPC仍可启动、登录和进行支持的训练；不可调用机器文件接口 | 验证IPC真实可用及失败分支；不能用关闭浏览器安全校验的方法证明Web可用 | T01/T16，V01/V12 |
| 授权与存储 | App同样有VerifyLicense；授权只有IndexedDB，ID/token各自按浏览器存储范围生效；正常重载保留，清数据与存储错误分开解释；多标签页并发也受已有提交幂等约束 | 授权有IndexedDB+机器/用户文件副本，按既有优先级恢复；IPC/权限失败与真实未授权分开；登录token仍不因此成为安全文件存储 | T16，V01/V04 |
| 音频许可 | 支持AudioWorklet的安全上下文；用户点击开始/恢复后再播放，处理suspended/许可未满足，不以仅建AudioContext为ready | 验证壳内实际AudioContext及worklet就绪；初始化不需Web遮罩不代表可忽略异步参数或最小化恢复 | T10，V02/V05/V10 |
| 串口与输入 | 支持Web Serial的目标浏览器、安全上下文和用户选端口许可；拒绝/无设备/占用/拔插可理解，不自动反复弹选择框；不支持时只限制该能力而不伪称已连接 | 核对实际选串口IPC与数据接收路径，现有WebSerial/本地桥接不能只因isEE就认定已连通；不擅自给本地硬件协议加业务心跳 | T05，V06/V07/V11 |
| 更新与回滚 | 入口/运行配置缓存可更新，hash资源版本一致；旧打开标签页在切换窗口刷新/停止写旧协议，站点与BE成对回滚 | 安装包与BE兼容矩阵、旧壳阻断/升级和回滚；不只替换BE | T01/T17，V12 |

**运行矩阵**：
- **W-HTTPS**：远程可信HTTPS站点、目标支持浏览器、无Electron桥；执行全部适用V01–V13。Web Serial与AudioWorklet有能力/许可前置，不能用Electron的安全开关绕过。
- **W-HTTP**：现有普通远程HTTP部署分支必须验证页面/API/WS及能力限制提示；普通远程HTTP通常不是安全上下文，不承诺完整串口/AudioWorklet训练。需要完整能力的Web部署使用可信HTTPS；localhost开发的安全上下文例外不能替远程HTTP验收，也不能默默缩减客户所需能力。
- **E-PACK**：真实安装包；按G4实际支持的Windows/Linux与本机/局域网组合执行全部适用V01–V13，不以npm run dev-e连接Vite的结果代替。
- **混合使用**：同一后端/房间分别测试Web教员+Electron学员、Electron教员+Web学员；报底、提交、通知、成绩及断链恢复同一契约。另测同账号跨模式登录仍按单token互踢，不能为壳/Web各开一套会话规避既定行为。

平台前置依据：[Web Serial requestPort的安全上下文/用户手势要求](https://developer.mozilla.org/en-US/docs/Web/API/Serial/requestPort)、[AudioWorklet安全上下文要求](https://developer.mozilla.org/en-US/docs/Web/API/AudioWorklet)。浏览器具体版本/外部代理证据由G4登记，不能由Vite build.target推导全部API支持。

取证基线的index.html内联地址现已由T01切到 `bw-frontend/frontend/public/runtime-config.js` → `bw-frontend/frontend/src/config/runtime.js` → `bw-frontend/frontend/src/entry.js`。Web的HTTP/HTTPS文件、上传、编辑器和OCR地址按协议/完整基础路径生成；桌面数据/文件地址仍取既有IPC配置。原桌面OCR的ws://localhost:13300与fetch调用冲突未冒充已修，真实侧车协议仍属G4外部前置。题库saveBatch与文件服务上传仍分开。

### 7.4 T01已落地的操作契约

- **Web配置来源**：构建前编辑public/runtime-config.js，部署时可只覆盖dist/runtime-config.js；不要在配置里放凭据。HTML入口和此配置文件应不缓存或强制重验证，hash静态资源可长缓存。部署覆盖需单独记录配置hash，不重写原build-manifest.json冒充构建原件。
- **地址解释**：httpUrl/wsUrl/fileUrl/ueditorUrl/ocrUrl接受裸主机或完整URL。裸主机的HTTP默认端口分别18001/18001/8000/8003/8080；HTTPS默认/data、/push、/file、/ueditor、/ocr前缀。完整URL保留指定协议、端口和路径；HTTPS页面配置HTTP/WS会显式停止启动。fileUrl填写文件服务基础地址，不重复带/api/file/getFile；读取/上传/图标由该基础地址统一派生。
- **MQTT两个用途分开**：mqttUrl仍是外部equipment://程序需要的裸主机；新增mqttWsUrl仅供两处浏览器Paho连接，填写完整WS/WSS URI。Web默认HTTP为ws://主机:8083/mqtt，HTTPS为当前origin对应wss://…/mqtt；部署必须提供真实broker/代理。Electron默认不猜broker，未配置时点击连接明确提示；可在打包前配置此可选URI，不修改外部设备程序传参语义。
- **启动顺序**：entry等待configureRuntime成功后才动态导入main；Electron复用既有getConfig invoke通道，不在启动时sendSync。配置读取/协议失败显示启动错误，业务模块不挂载；NetSetting保存端口与运行时同为整数1–65535。
- **清单与打包**：frontend npm run build成功后产生dist/build-manifest.json，含schemaVersion/component/version/sourceCommit/sourceDirty及完整排序的path/size/sha256清单。桌面beforePack总是先重建再核对、复制并复验，失败中止且不保留旧public/dist。桌面本地允许dirty开发产物但如实标记；CI发布必须clean且同SHA。
- **发布集合**：CI归档Web ZIP+前端sidecar及三个目标native二进制+各自sidecar，共8件；release依赖frontend/build/test，并在显式下载四个预期artifact目录后验证完整性、版本/架构、同SHA和非dirty。显式空SHA必须拒绝，不能降级本地校验。清单不是签名，也不替代CI信任边界。
- **桌面与正式发布边界**：现有build-e-w/build-e-l均自动执行beforePack；正式打包需安装依赖并准备目标模式所需的配套native与配置。Linux --dir真实包启动已作仓内验证，Windows/DEB安装、客户后端/授权/硬件和云CI未据此宣称通过；发布仍受G4/T15约束。


## 8. 验收矩阵

以下均为**待执行**；文档评审和算术smoke不等于这些用例通过。每项增加§7.3模式标签（W-HTTPS/W-HTTP/E-PACK）、origin或壳版本、能力与许可状态；同一BE行为测试可共享，但UI/网络/存储/音频/串口两模式分别留证。只有因平台确实不支持而明确限制的能力可标“不适用”，不能以一端成功代替另一端。每项记录提交、输入、预期/实际、环境及日志/截图位置。

| 验收 | 必须可观察的场景 |
|---|---|
| V01 | 203/204/206 保持原响应；互踢仍生效；有效/被替换 token 登出区别；重启 ID 稳定；授权剩余时长跨阈值、存储失败不清库 |
| V02 | 44.1/48kHz；WPM/码分×letter/short/long/mix；初次/改速/低速/冷启动/跨训练；采样边界 ≤1 sample、校准整篇 <2% |
| V03 | G1 每域样例原始记录→最终 score/deductInfo/speed/time；满分≠100、r≠l、dot.max≠dash.max；篡改客户端汇总不改变正式分；两种 GeneralKey 页形态同义 |
| V04 | 网络 reject、code202/500/203/204/206、响应丢失、双击、并发 finish/upload/reset；不丢页、不锁死、不假成功、不重复累计；在线查询失败回滚 |
| V05 | 0/负时长拒绝、剩余直接跨零、后台节流、暂停/恢复；过期一次触发；浏览器离线及服务重启后 deadline 扫描，按 G1 处置迟到/未传页 |
| V06 | 同 tick 多码、连续相同码、按下/抬起/间隔快照一致、分包/粘包、重复按下恢复；10ms 抖动、130ms 合法校准点；离页/重进无残留 |
| V07 | 干净三组句号、完整/分片控制码、首组/前组改错、连续翻页；正文保留、控制符不残留 #；未知码仍显式标识 |
| V08 | 两客户端/两实例并发首取同一未生成页hash一致；重复填报/三页改两页不留尾页；两教员通知一致、丢通知恢复；综合组网服务端评分；Web教员+Electron学员与反向组合都验证同房间提交/通知/详情 |
| V09 | 结束后新进入/刷新/重连都能看每个已提交学员；错误页/超页清晰；G3 若要求草稿则验证训练中更新、授权及不提前结算 |
| V10 | postJob/组训跟随速度且 preJob 不回归；F2 合法组合/普通输入；串口接收→入队→音频输出时间记录，达到 G2 确认阈值 |
| V11 | 配置保存/重进/再保存数值不漂移；postJob 对应配置生效；基础分级已存在且继续有效；空/坏/慢加载不崩溃，非法保存旧数据不变 |
| V12 | 同SHA的Web站点dist资源与Electron安装包资源分别核验，并与配套BE对齐；有效DOCX/XLSX闭环、无效行全回滚、默认题库绑定；两模式真实建卷/交卷/查成绩；W-HTTPS无活跃混合内容、hash路由刷新/缓存更新正常，W-HTTP能力限制明确，E-PACK实际IPC/配置可用 |
| V13 | A token/B 身份、非参训者、非管理教员均不能改他人记录；正常学生与授权教员结束全员正常；新 DTO/API 无旧身份参数 |

## 9. 验证与完成定义

- 现有单测优先复用 `PostTelexPatTrainScoreTest`、`PostTelegramTrainScoreTest`、`ScoringConsistencyTest`、`TheoryKnowledgeUploadExportTest` 等；General 活跃结算、并发和跨用户越权需要能失败的行为回归，不能用仅工具函数测试冒充端到端。
- 前端用现有环境跑真实页面/网络/音频/串口 smoke；仅为不确定且易回归的边界保留测试，不为此次整改另建测试框架。模拟帧不等于真实硬件采样验证。
- 后端 Java 21 + Docker，实施后受影响单测以及最终 `./mvnw -B clean verify`；前端按现有 lockfile 构建；涉及表结构同步双快照迁移演练。确切命令及打包门禁见计划。
- 一项完成 = 相应验收证据齐全 + 跨栈调用/迁移/操作说明同步 + 独立可回滚提交；不把编译通过、历史测试数量、规划批准、已有标记当本轮结果。
- 外部前置未达只能写“仓内实现完成，客户验收阻塞”，R 项不能全绿。12 项及附录全部有终态，禁止仅完成 P0/P1 后宣称本 Spec 已交付。
