# 客户报障整改实施计划（Plan）

- 日期：2026-09-10
- 状态：**计划已编制，业务整改未开始**。下文 `[ ]` 均为待执行任务，不代表本次文档会话已修改/验证功能。
- 依据：[`../reviews/2026-09-10-customer-issue-analysis.md`](../reviews/2026-09-10-customer-issue-analysis.md)；规格：[`../specs/2026-09-10-customer-issue-fix-spec.md`](../specs/2026-09-10-customer-issue-fix-spec.md)。
- 源码取证基线：`3360221`；执行时记录实际提交。既有联合计划中已完成的事项只回归，不重新算本计划成果。

## 1. 执行约定

1. Spec 的 R01–R12/H1–H5/M1–M5、G1–G4、V01–V13 是本计划唯一需求、门禁和验收编号；T00–T17 是实施工作包。
2. 一个可独立回滚的修复一个 commit；工作包内多个独立修复分别提交。跨栈参数切换、迁移与依赖代码、修复与回归测试不得拆成不可用中间状态。提交格式 `fix(scope): 中文摘要` 等，不攒无关改动。
3. 接触导出符号前查 LSP references；当前未配置 LSP，不能伪造结果，使用 API 定义、导入、路由、内部调用全量搜索代替并记录。修改 API 必须检查 `bw-frontend/frontend/src/common/api/` 和实际消费者。
4. 不改 203/204/206 后端码文，不建 user_session，不重做既有密码/会话方案，不把 frontend 旧根路径误当当前工程。
5. 未通过 G1/G2/G3 的语义变更不先写代码猜需求；确定性 bug 可独立修。缺客户设备不妨碍仓内复现，但不能关闭客户验收。
6. 不以 count/length/源码文本断言代替行为。真实页面/音频/串口、数据库状态和评分结果是验收对象；永久测试仅保留防止真实回归的案例。
7. 并行分支只实现各自独占文件，期间不跑全量构建/测试；合并稳定后由集成者统一验证一次，独立任务交付前仍须完成受影响验证。

## 2. 依赖与文件所有权

### 2.1 推进顺序

| 波次 | 可并行工作 | 必须先满足 |
|---|---|---|
| A 基线与交付 | T00、T01 取证/打包链修复、T15 模板往返 | 先读取工作树，保护用户已有改动；客户发布必须等 G4 和验证 |
| B 确定性修复 | T02 公式、T05 有序事件、T11 网络数据、T16 凭证提示 | 不互改同一文件；G1/G2 未闭合的部分不猜测 |
| C 状态与设置 | T03 提交/结束、T04 身份契约、T06 控制符、T07 配置、T12 通知 | T02→T03→T04 共享后端；T05→T06→T07 共享输入；T11→T12 |
| D 权威与语义 | T08 原始评分、T09 截止恢复、T10 音频、T13 详情/对齐、T14 综合评分 | T08 需 G1、T03/T04/T05；T09 需 G1/T08；T10 语义需 G2；T13 需 G3/T11/T12；T14 需 G1 |
| E 综合交付 | T17 全矩阵、迁移与客户验收 | 所有实际适用的 G/V 完成，或明确记录阻塞且不得总体验收通过 |

波次不是把所有任务串行：例如 T10 的 ready 修复可在 B 开始，T07 的往返修复不依赖 G2 的产品决定；但共享文件最终由同一集成者按依赖合并。

### 2.2 共享文件单一所有权

| 文件/目录 | 串行所有权顺序 |
|---|---|
| `backend/src/main/java/com/nip/service/general/GeneralTickerPatService.java` | 后端评分集成者：T02 → T03 → T04 → T08 |
| `backend/src/main/java/com/nip/service/general/GeneralKeyPatService.java` | 同上：T03/T04 → T08，避免覆盖既有 reset |
| `backend/src/main/java/com/nip/service/PostTelexPatTrainService.java` | T02 → T03 → T08 → T09 |
| `bw-frontend/frontend/src/views/manage/organization/handkeyZuXun/train/student/` | 输入集成者：T05 → T06/T07 → T03/T04 的提交接线 → T08 |
| `bw-frontend/frontend/src/views/manage/organization/electronKeyZuXun/train/student/` | 输入集成者：T05 → T03/T04 → T08/T10 |
| `bw-frontend/frontend/src/common/utils/voice/MorseVoiceHighPerformance.js`、`bw-frontend/frontend/src/common/utils/processor.js`、`bw-frontend/frontend/src/common/utils/ElectronMorse.js` | 音频集成者独占 T10；与输入集成者冻结事件接口后接力 |
| `backend/src/main/java/com/nip/service/simulation/`、`backend/src/main/java/com/nip/ws/WebSocketSimulationService.java` | 网络集成者：T11 → T12 → T13 |
| `bw-frontend/frontend/src/components/BroadcastTeachTrain/` | 网络集成者：T12 → T13；不得同时改订阅和草稿处理 |
| `backend/database/migrations/`、`backend/scripts/rehearse-migrations.sh` | 迁移集成者为 T08/T09/T11 串行编号与演练，不让不同工作包覆盖同一迁移 |
| `bw-frontend/frontend/src/common/http/index.js` | T16 独占提示；T03 在调用方判 code，不重写全局返回/抛错协议 |

## 3. 工作包

### T00 取证与业务口径冻结（R01–R12）

- [ ] 记录执行提交、工作树、客户截图/日志、FE/Electron/BE 版本/hash、目标设备与部署 schema；核实授权界面和题库制品，不要求客户重复证明其已报告的故障现象。
- [ ] 按 Spec G1 列每个活跃训练域/模式的单位、时间轴、规则满分/加扣、空/少/多页样例及原始 DTO 字段表；冻结 reset 后旧请求隔离方案、规则快照和存量进行中训练切换窗口。
- [ ] 按 G2 取得低速/划比/5与7间隔/校准样文、配置优先级、F2 与目标机延迟阈值；按 G3 取得实时草稿与漏多组对齐决定。回填 Spec §3，不另起竞争规格。
- [ ] 建 V01–V13 证据登记，缺现场条件标 G4，不填“通过”。已确认互踢不修，不重新征求多设备方案。

**出口**：确定性 bug 与产品变更清单分离；G1/G2/G3 的业务字段有责任人确认，G4 有真实环境记录或明确缺项。缺项只阻塞相关任务，不阻塞 T02 等确定性修复。

### T01 双端制品与桌面打包闭环（R12，P0）

**文件**：`.github/workflows/build-quarkus-native.yml`、`bw-frontend/package.json`、`bw-frontend/frontend/vite.config.js`、`bw-frontend/electron/index.js`；本任务只修构建消费链，不顺带升级依赖。

- [ ] release 增 frontend 成功依赖，归档 dist 与 BE 制品；生成同源码 SHA 的版本/hash 清单，防仅后端成功就发布。
- [ ] 桌面打包前从本次 frontend/dist 更新 public/dist，拒绝旧资源残留；保持当前打包布局，检查安装包实际加载文件 hash。涉及脚本时使用仓内现有 npm script 入口。
- [ ] 选定提交必须同时包含 `1c40aae`/`9596c6c`；G4、测试、模板往返及真实安装 smoke 通过后才由发布负责人推送/tag/发布。当前文档交付不执行这些外部动作。

**验证**：V12 的制品部分；故意让 frontend 构建失败时 release 不产出正式版本；目标安装包使用新 dist，不以 Vite 开发页代替。

### T02 确定性评分公式修复（R03/H3，P1）

**文件**：`backend/src/main/java/com/nip/service/PostTelexPatTrainService.java`、`backend/src/main/java/com/nip/service/PostTelegramTrainService.java`、`backend/src/main/java/com/nip/service/general/GeneralTickerPatService.java` 及受影响评分测试。

- [ ] PostTelex trainType!=4 以规则满分起评；用非 100 满分原始记录验证扣分与最终分。
- [ ] PostTelegram 高于 r 加、低于 l 扣，与 ScoreMath 对账；移除反向旧注释，不改业务规则字段含义。
- [ ] General 手键划线 cap 改为 dash.max；dot.max=1/dash.max=5/lineScore=7 最终成绩与扣分明细均正确。

**提交**：上面三项各自一个可回滚修复提交；每项带它保护的行为回归。**验证**：V03 中对应案例；既有工具函数测试不能代替 General 活跃结算。

### T03 提交失败恢复与结束原子性（R04/R05/M1/M3，P1）

**文件**：postJob 数据报 `telexTrain.js`/`trainScore.js` 活跃调用树、电子键组训 `handKeyTrain.js`、相应后端 service；以 API 模块 `TelegramApi.js`、`electronKeyZuXun.js` 搜索实际消费者。

- [ ] 先复现 code 非200、reject、响应丢失和连点；页成功后才推进，最后一页确认后才 finish；失败保留内容且 finally 释放锁。鉴权失败只引导登录，不自动跨用户重传。
- [ ] 修 pageTime 前页结束点、单调时钟计时、coun<=0 一次触发；合法时长在开始时冻结。这里不提前实现未定 G1 的服务端超时语义。
- [ ] upload/finish/reset 在相同训练/学员互斥，防并发重复结算、上传重开已结束训练；已有 finish 短路保留但补并发保护。
- [ ] M3 结束流程以 DB 已保存参训记录为依据；异常时回滚状态和分数，不 catch 后空列表继续；离线已上传学员仍须结算。

**验证**：V04、V05 客户端部分，尤其“服务器已提交但客户端未收到响应”的重试；对数据行/成绩不变与用户可继续操作断言。按数据报恢复、电子键锁、后端结束各自提交，契约相关修改保持同提交。

### T04 学员身份与内部结算切换（H4/H5/M4，P1）

**文件**：`backend/src/main/java/com/nip/controller/general/GeneralTickerPatController.java`、`backend/src/main/java/com/nip/controller/general/GeneralKeyPatController.java`、对应 DTO/service、`handkeyZuXun.js`/`electronKeyZuXun.js` 与调用页。

- [ ] 手键 upload/finish/reset 和电子键 finish 从 token 取得主体，校验参训关系与可写状态；两侧删除 userId/uid 身份参数，不保留“传来但忽略”的兼容字段。
- [ ] 将教员结束的授权与内部按用户结算路径分开，迁移 GeneralTickerPatService.updateStatus 的所有调用，不把其结束全员操作变成只结算教员本人。
- [ ] 移除手键 finish 中未被 DTO 接收的 validTime/finishInfo；G1 需要的时序由 T08 在正式逐页契约实现。

**验证**：V13；A/B 身份、非参训者、未授权教员不能改变目标数据；授权教员批量结束及正常学生继续有效。

### T05 手键/电子键有序事件（R06/H1/M2，P1）

**文件**：`bw-frontend/frontend/src/common/utils/WebSerial.js`，手键/电子键组训学生的 useControl/student/handKeyTrain，及复用生产者的活跃 preJob/postJob 入口。

- [ ] 原始码按顺序逐项消费，同一对象传递码值和时序；替换 ref 覆盖和值/时间多个共享 ref 拼装，连拍相同码也不丢。
- [ ] 点阈值使用当前有效校准，剔除≤10ms抖动；重复按下/缺抬起/分包粘包明确恢复，正常抬起必须解锁。
- [ ] 硬件无时间戳时只标记 JS 接收单调时间，记录精度边界；离页清订阅/队列，跨训练不残留。

**验证**：V06；真实串口帧与同 tick 注入分开记录。无硬件仅能证明事件消费，不关闭客户采样精度问题。

### T06 手键控制符与翻页（R07，P1）

**文件**：手键组训 `handKeyTrain.js`、`bw-frontend/frontend/src/views/manage/postJob/telegram/train/js/details.js` 和其控制符共享定义；依赖 T05 的事件语义。

- [ ] 句号/当前组改错/前组改错优先识别，跟踪控制符产生的临时项，完成时只撤销这些项；修 legnth。
- [ ] 保留普通未知码 #，不得隐藏全部 # 或无边界拼接历史输入；干净三组不能固定删 3，完整粘连/分片按控制符边界处理。

**验证**：V07，断言实际正文、组/页位置及控制行为；不能只断言字符串里没有 #。

### T07 训练设置往返与基础分级边界（R11，P2）

**文件**：preJob/telegram/handkey 的 telegram.js、preJob/telegram/train 的 HandKeyTrain.vue/basicTrain.js、postJob 手键配置入口、`backend/src/main/java/com/nip/service/TelegramTrainService.java`。

- [ ] 修 interval/gap 读取多减1；保存→加载→不改再保存毫秒值不漂移。跨入口映射及固定/自校准优先级遵循 G2。
- [ ] postJob 加载本域训练配置，不以 getBasicSetting 区间冒充四段毫秒字段。
- [ ] 基础练习保留已存在的区间分级，异步未就绪/缺异常区间/坏 JSON/无正区间时禁用开始并可恢复；saveSetting 全量校验后才替换，坏配置不清空旧值。

**验证**：V11 和 V06 的校准边界；往返修复与基础配置校验分别提交。

### T08 原始记录驱动权威评分（R03/R04/H2/M3/M4/M5，P1，需 G1）

- [ ] G1 表必须先补全为可实施的字段/单位/时间轴/错误策略；按模式先形成保存记录→score/time/speed/deductInfo 可手工验算的案例，不直接强行把六种公式合成一种。
- [ ] 同提交迁移逐页 DTO、存储、计算、API 和成绩页；删除客户端汇总评分输入，显示和扣分均来自同一后端结果。用服务端接收时间校验边界，不冒充原始拍发时刻。
- [ ] 页替换不累加 speedLog；reset 清本轮派生状态并以 G1 的轮次约束拒绝旧在途页。规则冻结与存量切换同步 migration，不重算缺原始数据的历史成绩。
- [ ] GeneralKey 固定/懒生成 value 一致并规范化历史 null；清除本次切换过时字段/计算，不保留新旧双算法作为运行兜底。

**验证**：V03/V04；客户端 speed/总分篡改、重复/乱序页、reset 后迟到请求、缺规则、非100满分与三页计时。定向单测之后必须从真实提交接口到数据库/详情回读完整结算。

### T09 服务端倒计时恢复（R05，P1，需 G1/T08）

- [ ] 按真实 begin/暂停/恢复持久化截止状态，不以创建时间假定已开始。
- [ ] 加周期扫描和重启恢复入口，复用同一互斥幂等结算；按 G1 明确未传页、迟到页、截止宽限和失败重试，不丢弃客户端仍可补交的数据。
- [ ] schema、DAO、截止处理与回归同提交；失败回滚，不产生“已结束无结果”终态。

**验证**：V05 后端部分；停止浏览器不发 finish、并发手动 finish、服务重启、持久化/结算故障恢复。不能只断言字段存在或定时方法被调用。

### T10 音频码速与响应（R02/R10，P2）

**文件**：Spec §5.4 列出的音频工具，postJob/preJob 收报及电子键 examTrain/组训训练设置消费点。

- [ ] 就绪前缓存最新全量参数，ready 回推并每场重置；去掉 setTimeout 等初始化，保留错误可见性，不静默退回旧参数。
- [ ] 统一换算入口、修 speedRate、按 type/模式设置 criterion/ratio；postJob/组训恢复速度跟随，preJob 验证不回归。低速与5/7间隔遵循 G2。
- [ ] processor 改样本累计及余数，暂停/清空使用同一游标；删除热路径调试输出，按 G2 优化 F2，不任意缩窗。

**验证**：V02/V10；数字音频采样测量 + 真实目标机回环/录音及按键延迟。参数竞态修复、采样时钟、各入口跟随为可独立回滚子提交；共享换算契约的生产/消费同提交。

### T11 simulation 报底与答案幂等（R08/R09，P2）

**文件**：`backend/src/main/java/com/nip/service/simulation/SimulationRouterRoomService.java`、`backend/src/main/java/com/nip/service/simulation/SimulationRouterRoomContentService.java`、对应 Page/PageValue DAO/entity、migrations/rehearsal。

- [ ] 清点两表 null/重复键；冲突内容无可靠裁决来源则停止迁移，备份待人工确认，禁止无条件取第一条/最大 UUID；清理后唯一键各列设 NOT NULL，输入与实体同步，防空键绕过约束。
- [ ] 有界懒生成锁房间 DB 行、锁内重查整页；唯一键是 room/page/sort。统一创建/懒生成/删除锁顺序，不仅套 JVM 锁。
- [ ] uploadResult 保持整份数组语义，按 token 归属在事务内替换该用户页集合，删旧尾页；唯一键 room/user/page。状态只随保存成功提交。
- [ ] 对应实体、迁移和脚本纳入双快照演练；脚本当前显式列迁移，新增 SQL 不会自动执行，必须同步接线与断言。

**验证**：V08/V09 数据部分；并发首读同页、重复完整提交、三页改两页、两个学员隔离、事务中断恢复。两种数据不变量分别提交，不让 schema 与消费者分离。

### T12 simulation 事务后通知与可恢复详情（R08/R09，P2）

- [ ] 各房型迁入现有 SocketConnection；心跳不进业务 JSON 解析，结束态进入也建连；卸载/手动关闭停止重连。
- [ ] REST 提交后发身份完整的轻量结果通知给房间全体教员，删除客户端后置 WS 写状态依赖。报务房同时删除补 id/原消息两路分歧，防重复。
- [ ] 首进、通知、重连均拉 REST 快照；5秒一个有界在途请求作等待结果时兜底，按可见性/结束结果齐全停止；不用内存在线名单冒充最终成绩。

**验证**：V08/V09；两教员均2秒内更新，丢通知10秒内补偿；服务重启、结束后刷新、连续进出无重复连接。以上网络时限只对 Spec 所列可达同局域网矩阵生效。

### T13 详情可见性与对齐（R08/R09，P2，需 G3）

- [ ] G3 确认漏多组展示样例后做有限页内对齐，额外页显式“超出报底”；不新增 simulation 数值评分。
- [ ] 若需要实时草稿，同提交实现逐页草稿保存/版本、授权读取、通知、教员展示、最终冻结，不复用提交状态伪装草稿；不需要则在 G3 记录客户确认的结束明细范围。
- [ ] 检查三类教员详情入口，不把只修 BroadcastTeachTrain 一处当全部完成。

**验证**：V09 和 V08 对齐部分；草稿不结算、不向无权者泄露，最终提交后显示状态/内容一致；已结束详情无论 G3 分支都必须验收。

### T14 综合组网服务端评分（R08，P2，需 G1）

**文件**：`backend/src/main/java/com/nip/service/GroupNetTrainService.java`、`backend/src/main/java/com/nip/controller/GroupNetTrainController.java`、`bw-frontend/frontend/src/common/api/TrainingDetails.js` 及其真实 trainingDetails 消费页。

- [ ] 以服务端题目、规则与答案计算分数，明确 range/deviceId 的真实对应，删除客户端正式总分与浮点直接相等作为权威依据。
- [ ] 参数与响应消费者同时切换，重复提交结果确定；不同训练类型按 G1 各自样例，不误用 simulation 字符对齐规则。

**验证**：V03/V08 综合组网部分；篡改自报分无效，同规则答案同分、规则归属正确，前端详情使用后端结果。

### T15 题库模板与理论测试操作（R12，P0/P2）

**文件**：`bw-frontend/frontend/src/views/manage/basicTheory/test/questionBank/js/knowledgeTabel.js`、同目录 `questionImport.js`；必要时同步 `backend/src/main/java/com/nip/service/TheoryKnowledgeQuestionService.java` 列说明，不改 saveBatch 返回能力。

- [ ] 修默认模板 levelId 说明字符串优先于当前选中题库的真实缺陷；中文说明与示例保持机器 field 契约，不让说明行被当题目。
- [ ] 明确 options/answer 的可填写格式与示例；导出→填写→解析→整批保存→回读均正确，失败不假成功、不部分入库。
- [ ] 在实际可用版本记录教员建卷/开考、学员作答/交卷、查询成绩的操作步骤和常见错误提示，交支持人员按步骤演示。用户指南更新随这一步实施，不在当前仅三文档交付中额外生成指南。

**验证**：V12；实际解析函数、后端导入回滚与真实授权后的界面闭环三类证据分开。当前已有模板示例与导入代码不重复实现。

### T16 设备身份与授权解释（R01，P3）

**文件**：`bw-frontend/frontend/src/views/manage/login/useLogin.js`、`bw-frontend/frontend/src/common/http/index.js`、`bw-frontend/frontend/src/common/utils/machineCode.js`、`bw-frontend/frontend/src/common/utils/VerifyLicense.js` 及实际提示组件。

- [ ] 按203/204/206解释前端提示，不改后端码文；206只说可能他处登录，不声称准确检测到互踢。
- [ ] 安装身份稳定化只在登录流程使用，不换正在使用的 deviceId；读取失败不清授权/自动生成新身份。复用既有 Electron 硬件接口，Web 持久化独立标识。
- [ ] 剩余累计可运行时长604800秒阈值预警；存储错误、设备不匹配与耗尽分别引导，保留现有授权自恢复机制。

**验证**：V01；安全 token/TTL/存储迁移仍引用既有计划的门禁，不以本任务宣布防重放完成。

### T17 综合验收与发布关闭（全部 R 项）

- [ ] 工作包稳定合入后统一运行 §4 全量门禁，记录实际测试数，不复用历史216/238等数字。
- [ ] 逐条 V01–V13 登记结果；G4 缺真实硬件/授权/部署时如实阻塞对应 R 项，不能拿 smoke 替代现场关闭。
- [ ] 12条及H1–H5/M1–M5逐项登记：修复提交、验证证据、客户结果、剩余限制；G3不需要草稿须有明确决定，不悄悄跳过。
- [ ] 本轮切换过时字段/路径/临时脚本清理；随功能更新操作说明、发布变更与迁移前置；不得顺便删除无关 teacherBack 历史代码。
- [ ] 发布负责人按已验收双端清单交付，记录安装/回滚检查结果；文档、代码、客户版本一致才将本计划改为完成。

## 4. 命令、迁移与回滚

### 4.1 执行时验证命令（本轮文档会话未执行）

所有 Maven 命令在 `backend/`，显式 Java21；需要 Docker。按当前实际类名运行受影响测试，新增有价值的回归纳入对应工作包。

```bash
export JAVA_HOME="$HOME/.local/opt/jdk21"
./mvnw -B -Dtest=PostTelexPatTrainScoreTest,PostTelegramTrainScoreTest,ScoringConsistencyTest test
./mvnw -B -Dtest=TheoryKnowledgeUploadExportTest test
./mvnw -B clean verify
```

前端在 `bw-frontend/frontend/`，沿用 CI 的安装/构建方式：

```bash
npm ci --ignore-scripts --no-audit --no-fund
npm run build
```

桌面在 `bw-frontend/` 的对应目标 OS 执行：

```bash
npm ci
npm run build-f
# T01 打包入口应已把本次 frontend/dist 纳入 public/dist；未闭环不得继续发布。
# Windows:
npm run build-e-w
# Linux:
npm run build-e-l
```

Windows/Linux 是各自目标矩阵，不要求在同一主机依次执行；Electron 安装脚本需要正常下载运行时，不能照搬前端的 ignore-scripts。Node 版本与当前 CI（24）及目标打包依赖一起记录。

### 4.2 迁移演练

新增迁移必须接入 `backend/scripts/rehearse-migrations.sh` 显式列表，并扩展其 schema/唯一键断言。导出实体 schema 后在 `backend/` 运行：

```bash
export JAVA_HOME="$HOME/.local/opt/jdk21"
./mvnw -B -Dtest=EntitySchemaSnapshotRehearsal test
REHEARSAL_OUT_NAME=customer-issue-fix ./scripts/rehearse-migrations.sh
```

重跑换唯一证据目录名，不覆盖旧证据。脚本拒绝外部 DB_HOST/JDBC_URL，不能改成直接连生产；schema 演练不证明真实重复数据已裁决，T11 另需隔离库数据案例。

### 4.3 切换与回滚

- 上线前暂停创建/修改相关训练，按 G1 处理旧进行中训练；备份实际 DB，盘点重复/空键，执行前置 migration，再部署同清单 FE/BE。旧客户端不能继续写新协议。
- 唯一约束/清理前确认保留记录清单，冲突不自动选赢家。回滚代码不等于恢复被去重数据；涉及数据处理须同时有备份恢复演练和停写窗口。
- 回滚时恢复上一对兼容 FE/BE 及匹配 schema/数据；不得只退后端而让新 DTO 客户端继续写。已产生新协议数据时先停写导出恢复点，按演练方案处理，不盲目 drop 列/索引。
- 每个独立修复提交可回滚，但共享契约与其迁移/调用者必须整体回退；发布说明列明不兼容边界。

## 5. 证据登记与三文档复审

### 5.1 实施记录格式

执行每一任务后追加：`任务/需求编号 | commit | 环境 | 输入与实际结果 | 测试/日志/截图路径 | 验收状态 | 未满足门禁及责任角色`。制品记录 SHA/hash，避免只写版本号。

**目前实施证据为空；这是事实，不是已完成标记。** 当前可用的是分析文档 §15 的源码证据、算术反例和实际解析函数 smoke，仅用于证明文档修订依据。

### 5.2 本次三文档交叉复审

- 核对范围：分析的事实/推断、Spec 的全量追溯/边界、Plan 的覆盖/依赖/验证/回滚；文档通过不代表业务验收通过。
- **结论：PASS（规划基线）**。已完成三份文档逐节交叉复审；未发现剩余的规划阻塞项。这里不批准未冻结的业务语义：G1评分/计时、G2节拍/配置、G3实时草稿/对齐、G4现场交付仍是对应实施/验收门禁。
- **复审修正**：分析中低速35与F2缩窗的无条件建议改为G2先决；C5从“单点投递”改为“通知内容不一致”；Spec/Plan补唯一键各列NOT NULL，避免只加UNIQUE仍允许多个空键；确认整份答案删除旧尾页、事务后通知、两类配置隔离及前端制品门禁一致。
- **结构验证**：脚本核对22个追溯项（12报障+10附录）、18个任务、13组验收、4项门禁；8个相对文档链接与45个去重源码路径均存在，任务/验收引用无悬空，代码围栏成对，实施复选框全部待执行。
- **行为取证**：运行真实 parseSpreadsheetRows，模板的非空levelId说明文字覆盖选中题库（correctlyUsesSelectedBank=false）；算术反例确认三页错误用时100/60/140。它们证明修订依据，不代表问题已修复。
- **验证限制**：三路补充核查服务503失败，未取得独立子代理审查报告；最终复审由主评审直接完成。只改三份文档，未运行Java全量测试、前端构建、真实音频/串口/客户安装验收，也未发布或迁移数据库。
