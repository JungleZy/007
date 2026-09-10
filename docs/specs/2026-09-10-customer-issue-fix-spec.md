# 客户报障整改规格（Spec）

- 日期：2026-09-10
- 状态：**规划基线；尚未实施**。确定性缺陷可按计划推进；§3 的产品/现场门禁未满足前，不得实施相应语义变更或宣布客户问题关闭。
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

门禁是执行任务的一部分，不是“以后再说”。T00 将决定及证据写回本节和计划；任何相关门禁未关闭，T17 不得将对应 R 项勾为完成。当前无客户设备、版本清单、教员签字样例，不能伪造批准。

| 门禁 | 必须取得的决定/证据 | 责任角色 | 阻塞范围 |
|---|---|---|---|
| G1 评分与计时 | 每个活跃训练域/模式的计数单位（字符/四字符组/WPM）、净用时是否含暂停/等待/最后间隔、空页/未完成/少多组、取整、满分与加扣上限、deadline 起点及迟到页处理；至少一份手工可对账样例；同时冻结 DTO 字段与存量策略 | 教研/产品确认业务；前后端负责人冻结接口 | T08、T09、T14；不阻塞 T02 的确定性错误修复 |
| G2 节拍与训练设置 | 低速模式是否固定符号速度+扩展间隔、5/7 间隔口径、码/分校准报文、划比变化语义、固定配置/自校准优先级、F2 组合键合法等待窗、目标机器与端到端延迟阈值 | 教研/硬件/前端 | T10 的语义调整、T07 的跨入口设置映射；不阻塞就绪竞态或比例往返修复 |
| G3 详情与对齐 | 客户需要“结束明细”还是“训练中草稿”；是否允许教员实时读取答案；漏/多组展示样例和对齐粒度，不将展示直接定义为数值扣分 | 客户代表/产品/训练负责人 | T13；不阻塞 T11/T12 的数据和通知修复 |
| G4 客户与交付环境 | 分别记录Web origin/协议/浏览器版本/反代与Electron壳/OS/本机或局域网配置；FE/BE及安装包hash、授权存储、串口能力/许可/采样率、实际schema/文件服务、测试发布窗口 | 部署/支持人员 | 两种模式现场归因与验收，尤其V01/V02/V06/V08/V10/V12；不阻塞仓内确定性修复 |

安全项 H4/H5 来自本轮分析附录，是独立可回滚任务；既有计划接受的“全域 WS 握手风险”不冒充本任务已关闭，也不隐式扩大授权重构到所有端点。

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

### 4.3 原始记录与权威结果

- G1 按模式冻结一张字段表：已有 DTO 字段→语义→单位→来源→必填性→验证→存储→消费者；缺失的开始/暂停/恢复/结束时序需跨栈同提交加入，不能仅删 speed 字段就声称后端能算用时。
- 逐页以训练、token owner、页号定位；记录单调事件序列及采集时序、服务端接收时刻、该次训练冻结的规则。客户端采集时序只可验证顺序/非负/区间一致性，不证明真实硬件时间或完全抗篡改。
- 计算时使用实际已保存的页/事件，不以客户端 speed/errorNumber/accuracy/totalScore 作为正式输入；前端删除这些最终输入依赖，实时显示可保留“预估”并在结算后替换。
- 不跨域强行除以 4。按 G1 的域单位归一后，显示码速、扣分码速、总用时与明细同源；重放同一保存记录与同一规则必须得到同一成绩。
- M4：移除 finish 里被 DTO 丢弃的 validTime/finishInfo；若某项真实有用，将其放入 G1 正式逐页原始记录契约，而非 finish 的无效冗余字段。
- M5：新建/懒建 GeneralKey 页都初始化相同空集合形态；存量 null 在停写迁移中规范化为相同语义，不永久保留双形态读取分支。
- 存量已完成成绩不无条件批量重算；没有原始时序的旧记录不能编造用时。切换时让旧进行中训练完成或明确终止，保留既有历史结果，新的规则/协议仅用于切换后的训练；具体停训窗口在 G1/G4 落实。

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

- 训练 DTO 中点/划/间隔/大间隔的毫秒边界与 UI 比例互为逆变换，不重复减 1 或反复 parseInt 截断；固定配置与自校准优先级在 G2 冻结。
- postJob 从本域训练配置加载，而非直接抄 preJob URL 或拿基础分级表替代；开始前配置可用，跨页/重进保持一致。
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
- 若 G3 确认为需要实时草稿：新增明确的草稿保存/页版本与读取协议，学员逐页保存触发通知，教员只读且须属于房间；草稿不得设置最终 userStatus、不得触发成绩。终提交按页版本原子冻结并区分草稿/最终结果；只删 UI status 判断不算实现。
- 若 G3 确认只需结束明细：记录该决定，不新增草稿能力；R09 仍须 V09 的结束详情验收，不能默默省略实时诉求。
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
- 模板保留稳定机器字段；中文说明和示例可放独立说明 sheet，第一数据 sheet 仍是现有 field 头，以避免只改中文头破坏 parseSpreadsheetRows。已有示例保留并清楚标识，默认 levelId 用当前选定题库，不用说明字符串冒充 ID；不能把说明行误导入。
- DOCX/XLSX 仍按当前前端能力解析后一次调用 saveBatch；后端 exportTemplate 仍为 JSON 列规格，不改为假字节流。仅 code===200 提示成功；坏行整批回滚并定位原因。
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

当前路径证据：`bw-frontend/frontend/index.html:58-95`、`bw-frontend/frontend/src/config/router/index.js:82-85`、`bw-frontend/frontend/src/components/common/NipPagePermission.vue:88-118`、`bw-frontend/frontend/src/common/utils/licenseStore.js:127-159,188-224`。HTTPS初始化仍含HTTP上传/OCR/编辑器地址，T01按本次活跃调用面修复或明确外部配置前置；题库saveBatch本身不能误判为依赖该文件上传URL。

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
