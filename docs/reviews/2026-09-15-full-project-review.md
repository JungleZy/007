# 全项目与核心训练复核报告（2026-09-15）

## 1. 结论与证据口径

本轮以 `a39e226ea686e886648c8388a38d5acea54e9a9b` 为审查起点，采用六路领域审查、两路补充审查、隔离 MySQL + 生产 fast-jar + 真实浏览器验证。**发现新的功能缺陷；本报告初稿阶段尚未完成修复，不能将已有 CI 绿色等同于本轮验收通过。**

- 基线后端：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify`，442 tests，0 failures / errors / skipped。
- 基线前端：`npm run test` 31/31；`npm run build` 成功；发布契约 5/5。
- 起点 CI：GitHub Actions [34922593615](https://github.com/JungleZy/007/actions/runs/34922593615) 成功，对应 `a39e226`；核心整改提交 `479a1fa` 的 [34843913763](https://github.com/JungleZy/007/actions/runs/34843913763) 亦成功。它们不是本轮未来提交的验收证据。
- 隔离运行库：`project006_review_20260915`，从本地库复制，只对副本应用仓内已有迁移及测试写入；未修改原始训练库。生产 schema validation 通过后启动于 18001；前端生产预览 18002。
- LSP status 返回无可用 language server；引用审查使用当前源码搜索和前端实际消费者。
- 审查期间 `keysim/src/serial.rs`、`console.rs` 有外部并发编辑。曾观察到未闭合分隔符，但 `git show a39e226:keysim/src/serial.rs` 证实提交态完整闭合。**撤销“已提交模拟器无法编译”的判断；瞬时脏树失败不是 CI 漏检提交损坏的证据。外部改动不得归入本轮提交。**
- 隔离导出提交态 keysim 并提供兄弟工程源码后，`cargo test --locked`：79 passed；隔离复制首次因未及时链接兄弟工程导致 drift 文件读取失败，补齐环境后通过，不记为产品缺陷。

配套文档：[规格](../specs/2026-09-15-full-project-fix-spec.md)、[实施计划](../plans/2026-09-15-full-project-fix-plan.md)。本文行号指审查起点附近位置，修复后以符号和回归证据定位。

## 2. 审查覆盖

| 切片 | 覆盖 | 证据边界 |
|---|---|---|
| 手键 | TelegramTrain、PostTelegramTrain、GeneralTickerPat；采集、暂停恢复、分页、评分、组训 WS；对应 preJob/postJob/organization 页面 | 源码调用链 + REST 实测经典域 + 真实手键页面/音频激活 |
| 电子键 | TelegraphKeyPatTrain、TelegraphKeyPatSynthetical、PostTelegraphKeyPatTrain、GeneralKeyPat；examApi/electronKeyZuXun | 源码 + 综合训练真实 REST 生命周期 |
| 收报/数据报 | TickerTape、PostTickerTape、TelexPat、PostTelexPat、GeneralTelexPat；receive/datagram 页面 | 源码 + 基础收报真实 REST + 数据报真实页面 |
| 公共桌面/采集 | Electron main/preload/IPC/文件服务/串口；Morse/AudioWorklet、WebSerial、WS、确认式提交；keysim | 源码与已有测试内容核对；物理硬件与桌面实机尚无替代证据 |
| 其余业务 | 用户角色菜单、理论考核/学习、汉字、军语/报话、装备、组网、仿真、综合统计 | 领域审查与理论权限补充审查；不把类级 JWT 当作对象授权 |
| 数据/交付 | schema/迁移/rehearsal、Maven、前端/桌面构建、三平台 native CI、release manifest、文档入口 | 本地命令结果、已有 Actions、生产 validate；补充发现须继续核实 |

“覆盖”表示已核对相应代码及调用边界，不意味着每个端点、每种角色或每种实体组合均已动态执行。

## 3. 确认问题与修复单元

### R01 / P1：经典手键结算读到旧答案、首暂停时钟滞后，码率精度导致生产写入失败

- `backend/src/main/java/com/nip/service/TelegramTrainService.java`：`validateFloorContentsBelongToTrain` 先 `findById` 加载受管内容；`updateSubmittedContent` 使用 bulk HQL 更新；`recalculateFromPersistedRaw` 随后查回相同受管实例，仍可能读取旧 `moresValue`。同次提交评分滞后。
- `controlTelegramTrain` 暂停分支先重算、后 `closeActiveSegment`，首次暂停码率为 0；只在结束分支保存 `nowFloorId`，暂停重入丢失当前页定位。
- `recalculateFromRaw:327-328` 使用 double 除法再 `BigDecimal.valueOf(...).stripTrailingZeros()`，没有统一 `ScoreMath` 精度约束。生产 `t_telegram_train.speed` 为 `varchar(11)`，可被常见非整除速率撑满。
- **实际复现**：创建 A/B 两字符训练 → 开始 → 约 1.1s → 提交正确 Morse `[[0,1],[1,0,0,0]]` 并暂停。响应 `accuracy=0, speed=0, accumulatedActiveMillis=1132, nowFloorId=null`。继续约 1.1s 后结束，日志 SQL1406 `Data too long for column 'speed'`，实际 HTTP500，事务未完成。该问题由生产库验证揭示，Hibernate 测试建表的宽字符串列未覆盖容量边界。
- 修复：更新受管实体而非绕过一级缓存；先关闭活动段再计算；统一 `ScoreMath`；暂停与结束均验证并保存当前页。历史 protocol 0 保持只读，不得允许开始后再禁止结束。
- 文档审阅补证：公开`saveFloorContent`只判属主、未判protocol/终态且无同一训练锁，可在结束后改复盘原始答案；一并纳入R01，不能只保护control入口。

### R02 / P1：组训手键缺报组数与扣分数量不一致

- `GeneralTickerPatService.calculateLackCount:1078-1099` 对“最后一个缺页”无条件用 `messageNumber % 100`，同时给 `lackGroup` 每缺页加 100；与已修正的 `PostTelegramTrainService.calculateLackCount` 不同。
- 100 组全未交时显示缺报 0；150 组漏第 2 页时扣分按 100 组而非 50 组，未达到规则封顶时多扣分。
- 验收应同时断言返回缺报数和实际扣分，而非仅检查内部列表长度。

### R03 / P2：岗位手键缺前页时复盘分析错页

- `PostTelegramTrainService.detail:385-419` 未提交页只补 `messageBody/finishInfo/standards`，不补 `resolver`。
- `postJob/telegram/train/js/trainScore.js:74-89,217-229` 按页下标关联上述结果，前页未交而后页已交时分析错位。常规顺序提交可掩盖该问题。
- 修复须保留页号与缺页占位语义，不能把后页解析当作前页解析。

### R04 / P2：岗位电子键少码/多码仍算正确组

- `PostTelegraphKeyPatTrainService.countScore:510-512` 正确率扣除 error、bunchGroup，却未扣 lack、more；同源 `KeyPatUtils` 的组训路径 `GeneralKeyPatService.countScore:939-941` 扣除四项。
- 每组少拍一个码时，个人岗位可显示 100%，相同组训显示 0%。没有产品规范支持这处分叉。
- 统一正确组定义，并验证少码、多码、正确组与混合结果；不改变既有评分规则的扣分项或冻结规则。

### R05 / P1：个人数据报连贯训练开始缺少 Promise 返回

- `bw-frontend/frontend/src/views/manage/preJob/datagram/telexTrain/js/telexTrain.js:95-108` 的 `saveTest` 不 return；`:279-289` 的 `beginTrain` 调 `saveTest(1).then(...)`。
- 首键触发时会对 undefined 取 `.then`；服务端请求已发出，前端开始态和计时确认分支却未执行。兄弟 `preJob/telegram/telexTrain` 已采用返回成功布尔值的确认式流程。
- 真实菜单存在 `/preview/basicSkill/preJob/datagram/telexTrain`，已从数字连贯卡片进入真实训练页；不能将其与未配置菜单的组训 telexZuXun 混淆。

### R06 / P2：综合收报重入把秒当毫秒显示

- `preJob/receive/train/js/receiveTrain.js:230-254` 把服务端 `validTime` 直接交给毫秒拆分函数。
- `TickerTapeTrainService.seconds` 返回秒；岗位收报对应页面已有乘 1000 转换。暂停 120 秒后重开会显示近 0，而不是 00:02:00。
- 只修单位边界，不改服务端秒契约，不引入第二套计时来源。

### R07 / P2：数据报组训逐页分析 totalTime 恒零

- `GeneralTelexPatService.patDetail:336-397` 初始化 `totalTime=0`，遍历只累加 patNumber，最后原样返回 0；`generatePageAnalyze` 亦未填时间。
- 逐页码率消费者以 `patNumber/(totalTime/60000)` 计算，非空页出现 Infinity。即使当前本地菜单未配置该组训页面，REST 分析字段仍错误。
- 从持久化采集区间计算真实毫秒总量；缺失旧协议证据时不能伪造时长或用常量遮盖除零。

### R08 / P1：仿真线路频道与干扰设置缺对象授权

- `SimulationRouterRoomController.changeChannel` → `SimulationRouterRoomService.changeChannel:247-265` 只按请求体 roomId/userId 更新数据库与内存频道，无调用者授权。
- `getRoomChannels:319-325` 亦无房间读权限。
- `SimulationDisturdController.saveSetting` → `SimulationRouterRoomContentService.saveSetting:320-327` 直接覆写任意房间设置。
- 已有 `SimulationRoomAccess` 和训练属主/组训位判定可复用。必须核对 ListenIn 等真实使用者，允许合法控制者；外人和不可控制的成员返回 207，数据库及内存均不得改变。

### R09 / P2：个人汉字录入成绩和进度未持久化

- `EnteringExerciseService.add` 初始化指标为 0；`pause/finish:105-127` 仅改状态，不保存作答、不计算指标。
- `EnteringExerciseDao.finishStatistical` 对speed/duration聚合，结果长期为0。个人域实际是拼音/五笔码串练习，不能套用岗位版汉字/英文的type和判分单位。
- 文档审阅纠正：现有客户端仅记录pys[].trueOrfalse，不存实际按键；需冻结规范期望码串并保存真实value，以完成条目/组计数。复用权威时钟模式而非岗位判分模型，同步拼音/五笔/文章消费者、迁移与历史策略。

### R10 / P1：理论考试交卷/阅卷后可回退并覆盖答案

- `TheoryKnowledgeExamService.studentChangeExamState:192-226`、`saveUserRealTimeParam:232-247` 缺考试及考生终态守卫，允许再次 type2/type3 或任意默认状态回写。
- 普通学员写入口可改已评分答卷；补充发现`finishSelfTesting`还未检查自测标记/终态，普通单人考核可借该入口重写content、score和全场state4。不能仅保护两个普通学员入口。
- 采用服务器既有state3硬结束：保留合法type1离场保存、type2成员启动/重入，结束后208；前端等待启动确认，移除服务器未授权的31秒补交及虚假成功提示。教师阅卷、自测finish与所有学员写端点参加同锁序列。

### R11 / P1：理论详情暴露其他考生答卷，教师分析缺权限

- `findTheoryKnowledgeExamById:127-142` 返回全体 `FindExamIdDto`，其中有姓名、账号、content、score、起止时间。
- 此接口是学员 `startTest.js` 的合法入口，**禁止简单整端点加管理员门禁**。学员仅见自身合法信息，外人拒绝；创建人/监考人/管理员按既有口径查询。
- `examineAnalyse` 是教师分析消费者，但缺对应门禁；教师全场列表也须限定权限范围。
- 详情和type2入场都下发paper，必须统一响应投影。考中保留各题型空作答结构但不含标准答案/解析；填空不能直接删answer。自测创建者按考生处理，完成后复盘；教师阅卷完整答案保持。

### R12 / P2：Web 授权卡片裁切主按钮

- `components/common/VerifyLicense.vue:64` 固定 height 400px；右栏 padding + Web 设备标识长说明 + 输入和按钮超过高度，overflow:auto 把“开始授权”放到不可见折叠区。
- 1440×1000 真实截图已确认。使用内容自适应高度及小视口滚动约束，保持完整设备/存储说明；无需为视觉布局添加实现细节单测。

### R13 / P2：keysim 缺 CI 构建/行为门禁

- `.github/workflows/build-quarkus-native.yml` 不执行 `keysim` 的任何 Cargo 命令。
- 这是独立模拟工具与跨栈协议测试的覆盖缺口；**不是版本 1.0.0 与主产品 3.1.1 不一致的 bug**，没有将模拟器纳入产品 release 的既定契约。
- 新增独立 Linux Rust job，执行锁定依赖的现有测试和构建，不扩大发版资产范围，不碰外部正在编辑的模拟器源码。

### R14 / P2：部署迁移与最新审查入口漂移

- `backend/README.md:92-97` 清单止于 09-12-03，另处写共 11 个；release runbook 人读表同样漏后续 6 个。实际已有 20 个 SQL，其中 19 schema + 1 菜单数据迁移。
- 本地旧库 clone 首次 `%prod` 启动缺 `t_radiotelephone_train.active_millis`；应用已有 09-12-05 等迁移后启动成功。这是**本地未迁移状态 + 文档误导风险**，不是迁移脚本缺失。
- 更新根/后端 README、docs 地图、runbook 的权威链接、全量清单及 schema/数据迁移区别；避免把 09-12 的未观察 CI 陈述冒充当前事实。

### R15 / P2：试卷分层查询使用单例共享递归列表

- `TestPaperService` 为 `@ApplicationScoped`，实例字段 `ids` 被 `findAllLevel` 递归修改，`findTestPaperByLevelIdAndName` 仅在成功尾部重置。
- 学员自测、教师建考、题库均为真实消费者；并发查询不同层级或前次查询异常会污染后续范围。
- 使用请求局部集合并显式递归传递；结果互不混入，不引入共享锁串行化全部查询。

### R16 / P1：汉字录入要点写入口缺管理员门禁

- `EnteringKeyPointsController.save:37-42` 仅类级 JWT，`EnteringKeyPointsService.save` 保存全员共享主数据。
- `ExplainApi.saveHanziPoints` 及 `preJob/hanzi/explain/Index.vue` 为管理编辑消费者；同族手键、收报和设备要点写入口均有 `@RequireAdmin`。
- 补相同门禁，普通用户 207 且原内容不变，管理员仍可保存并由读取接口看到新内容。

### R17 / P1：通报教学学员可伪造教员生命周期 WS 消息

- `WebSocketSimulationService.messageHandleReport:536-596` 的 type 1/2/3/4（开始/暂停/继续/结束）只经在线成员校验，没有教员角色判定。
- `useBroadTeacher` 是这些控制消息的发送方；`useBroadStudent` 只合法发送 online 准备消息。现有 `SimulationRoomAccess.isOrganizer` 已把房间创建者及非线路房发报位视作控制者。
- 收报学员可发送 type4 并携带 count，调用 `updateStatsToFinish` 强制结束全场、写入时长并广播“考官结束”。该发现有角色枚举、真实教员/学员消费者及状态写入证据。
- 仅控制消息加控制者校验，学员 ready/online、业务作答保持可用；拒绝帧采用现有WS授权错误协议并且不广播/不改状态。线路和干扰房的开始/结束需按其实际发报角色另核对，不凭“非管理员”一刀切。

### R18 / P2：电子综合统计把秒当毫秒，单字统计码率分母再乘千倍

- **实测**：综合训练REST结束返回duration=2秒、accuracy=100、speed=106.48，随后真实patExam页面显示次数1/平均码率106.48，却显示训练时长00:00:00。
- `TelegraphKeyPatSyntheticalDao.finishStatistical` 直接SUM(duration)，新协议duration是秒；`preJob/telegram/exam/Index.vue:136-138` 按毫秒显示。同页基础/单字数据确实是毫秒，不能全局删前端除1000。
- `ExamBasicTrain.vue:293-297` 以performance.now差值上送毫秒；`TelegraphKeyPatTrainService.saveStsatistical:87` 又乘1000传给需要毫秒的ScoreMath.rate，造成存储平均码率偏低千倍。
- 历史证据：`d6dbbaa^` 的examTrain.js提交duration为界面秒数×1000，旧服务原样保存；新协议应聚合accumulated_active_millis，旧行保持原duration贡献，禁止过滤旧行或盲乘全部历史值。已有汇总也须正确重算，不只等待下一场训练。

## 4. 尚未升级为确定缺陷的观察

- cable 非整百数量与实际固定页数边界：需按当前 UI 与固定报底能力继续核对；不得直接收窄合法数量。
- 个人单字练习客户端累计统计、教案测验客户端分值：当前未发现其进入权威考核评分的消费证据，保留设计限制，不扩大为完整采集协议重写。
- `organization/telexZuXun` 教师缺结果订阅：本地 t_menus 无该模块或 datagramZuXun 组件，路由未证实，不声称当前教员 UI 已实际故障。
- 桌面异常杀进程后孤儿后端、共享串口单活假设、路由异步内部时序：未提供独立实际故障证据，本轮不增加自动重启/全局状态重构。
- 音频播放通用组件卸载定时器、无默认角色用户、题库低数量等补充边界：待补充审查与可观察消费者核实，确认后须补入规格并重新审阅相关文档。
- 文档审阅已澄清：已定义业务失败走HTTP200信封，未处理系统故障保持HTTP500/code500，JAX-RS协议异常保留原HTTP状态；GlobalExceptionMapper及ExceptionBoundaryTest已有明确契约。本轮不改mapper掩盖SQL1406，R14修正文档笼统表述。

## 5. 已运行的核心场景

| 场景 | 当前结果 |
|---|---|
| 生产 fast-jar + 完整已有迁移 | 启动通过；不是关闭 schema validation 的替代运行 |
| 真实浏览器授权、管理员登录、仪表盘 | 通过；原先工具点击超时已排除为产品登录挂死 |
| 手键页面创建与 AudioWorklet 用户激活 | 页面可达，音频激活遮罩解除；不等于听感或硬件时序验收 |
| 经典手键正确答案、暂停恢复、结束 | 复现 R01，未完成修复前不得记通过 |
| 电子综合 save/begin/stop/goTo/finish | 两段约 1.1s，结束 duration=2、accuracy=100；伪造客户端指标未生效；相同 finish 幂等 |
| 基础收报 baseSession/begin/pause/goOn/finish | 暂停后 validTime=1，结束=2；伪造 999999 未生效；重复结束 208 |
| 个人数据报数字/字母连贯页面 | 真实创建并打开；字母训练首键A由CDP main-world error监听捕获 `Uncaught TypeError: Cannot read properties of undefined (reading 'then')`，动态复现R05 |

## 6. 外部验收边界

真实电键的接触抖动、USB 枚举/重插、目标音频设备与听感、Windows/ARM64 桌面硬件、可信证书、生产凭据注入和客户现场签收不能由浏览器与模拟器替代。Linux 本地验证与三平台 native Actions 结果必须分别陈述；本轮不签发新产品版本、不发布 tag、不处理用户未授权的真实库数据。

## 7. 修复与最终复核记录

- 文档审阅：ReviewDocsPlan批准执行映射及验证计划；ReviewDocsContracts提出D1–D5（个人码串模型、理论合法流程/写入口/试卷投影、经典手键单内容旁路），Main修正后三份文档获复审批准；R18独立补审通过。
- 此处仅证明文档门禁已通过。逐项代码修复、回归、提交SHA及最终Actions结果尚待执行后回填。
