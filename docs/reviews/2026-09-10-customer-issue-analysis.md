# 客户报障 12 项问题分析

- **分析日期**：2026-09-10
- **问题来源**：客户使用反馈（12 条）
- **分析范围**：`backend/`（Quarkus 服务）、`bw-frontend/frontend/`（同一 Vue 前端的 **Web 部署与 Electron 壳两种运行方式**）、`bw-frontend/electron/`（桌面集成）。
- **分析方式**：6 路并行源码调查（调用链追踪 + file:line 取证），结论区分「确认事实」与「推断」
- **关联文档**：`docs/reviews/2026-09-08-joint-frontend-backend-review.md`。原《手键与电子键拍发、评分联合 Review》（2026-09-10）已合并入本文，见附录 A，原文件不再单独保留
- **修订记录**：2026-09-10 经 6 路并行独立核查（对照仓库现状 + git 历史 + 前端调用面逐条验证），修正：指纹漂移机制（1.1-2）、AudioWorklet 竞态文件归属与首因定性（2-3、10.1-4）、processor.js console 伪引证（10.2-2）、句号残留机制（7-2）、TOPIC_RESULT 房型归因（8.2-4）、NaN 触发链降级为推断（4-2）、发布语境（12）；删除不存在的引证，补充 token 可重放、preJob/postJob 入口区分等遗漏事实。同日产品决策：**单 token 互踢为设计行为不修复**，撤销 user_session 会话表改造（1.1-1、1.3、P3 批次）。
- **本轮复审基线**：`3360221`。源码审查与算术反例校验，不代表已在客户设备复现；仓库缺陷、客户根因推断和产品扩展分别记录。实施规格与计划见 [`../specs/2026-09-10-customer-issue-fix-spec.md`](../specs/2026-09-10-customer-issue-fix-spec.md)、[`../plans/2026-09-10-customer-issue-fix-plan.md`](../plans/2026-09-10-customer-issue-fix-plan.md)。
- **双模式补审**：基于文档提交 `6efc963` 补齐 Web 专项；两种模式均是正式交付对象。先前音频 processor 取证混淆 src 副本与 public 实际资源，现按 §0.1、§2、§10.2 更正，不能继续引用“渲染线程日志不存在”的旧结论。
- **实施状态提示**：T01交付链与T15题库/理论操作链已完成仓内实现验证，证据见计划T01.1/T15.1。T15另发现并修复ORM建库时的考试JSON容量映射缺失；current/base快照原有longtext不据此判为客户库缺陷。本文其余引证保留取证基线，正式发布与客户验收仍未关闭。

## 0. 总体结论

12 条问题可归并为 **6 个系统性根因簇**，不是 12 个孤立 bug：

| 根因簇 | 覆盖问题 | 侧 |
|---|---|---|
| C1 会话模型：单 token 互踢（**设计如此，不修复**）+ 凭证存储脆弱 + 授权码门闸 | 1 | 后端设计 + 前端 |
| C2 评分权威不完整：部分码速/用时/总分信任客户端，已有服务端计算与客户端口径混用 | 3、4、5、8；6 为输入损坏 | 契约（前后端共同） |
| C3 前端计时与音频节拍：`setInterval`/`setTimeout`/墙钟计时 + AudioWorklet 参数竞态 | 2、4、5、10 | 前端 |
| C4 摩尔斯采样链路：事件合并、阈值硬编码、映射表缺失、设置不生效 | 6、7、11 | 前端为主 |
| C5 WS 健壮性：裸 WebSocket 无心跳无重连 + 通知内容不一致/缺补偿 + 状态双写 | 8、9 | 前后端共同 |
| C6 交付缺口：题库导入修复未进本地已知 release tag，前端制品链未闭合 | 12 | 发布流程 |

**评分类问题（3/4/5/6/8）的共同风险**：部分评分输入信任客户端、提交状态缺少失败处理，且各域公式缺少明确对账。不能概括为“后端零重算”：GeneralKey 已从拍发数据计算、simulation 主要是对比展示；原始事件丢失也不能靠服务端重算恢复。鉴权失败可能来自互踢或凭证丢失，不是当前不存在的 token TTL 到期；具体客户触发链仍需现场记录。

### 0.1 Web / Electron 运行边界（补审）

两种模式共用业务组件/API，但启动、地址、存储、音频许可和硬件入口不同；“前端已验证”必须写明模式，不能只测壳。

| 维度 | Web 部署 | Electron 壳 | 当前证据 |
|---|---|---|---|
| 启动/地址 | 静态 dist；HTTP 分支直连18001，HTTPS 分支 `/data`、`/push`、`/file` 代理 | 打包读取 public/dist；IPC system.getConfig 注入地址，存在本机/局域网配置 | `bw-frontend/frontend/index.html:58-95`；`bw-frontend/electron/index.js:60-66` |
| 授权/设备码 | 同样经过 VerifyLicense；无硬件指纹时用随机设备码，授权仅浏览器IndexedDB副本 | 可采硬件设备码，授权另有机器级/用户级文件副本 | `bw-frontend/frontend/src/App.vue:1-16`；`bw-frontend/frontend/src/common/utils/machineCode.js:81-90`；`bw-frontend/frontend/src/common/utils/licenseStore.js:127-159,188-224` |
| 音频初始化 | 相关训练页等待用户点击遮罩启动 | mounted 时尝试初始化，仍异步等待worklet | `bw-frontend/frontend/src/components/common/NipPagePermission.vue:88-118` |
| 采集/后台 | Web Serial需浏览器能力、许可及安全上下文；页面可见性影响上游转发 | 有IPC选串口入口，但实际数据通道也须追踪，不由“有壳”推断协议 | `bw-frontend/frontend/src/common/utils/WebSerial.js:18-38`；`bw-frontend/frontend/src/common/ws/MessageWebSocket.js:89-108`；`bw-frontend/frontend/src/components/common/NipSerial.vue:49-95` |

补审确认的交付风险：
- `index.html:76-94` 的HTTPS Web分支仍生成HTTP uploadFileUrl/ueditorUrl/ocrUrl；实际执行该初始化代码得到 API=`https://…/data`、WS=`wss://…/push`，上传仍=`http://…:8000/api/file/upload`。这是地址风险证据，实际哪些客户功能被混合内容拦截须核对调用与浏览器Network，不能泛化为所有题库上传都走文件服务。
- `bw-frontend/frontend/src/common/utils/voice/MorseVoiceHighPerformance.js:274` 实际 addModule('processor.js')，对应 `bw-frontend/frontend/public/processor.js`；它与 src/common/utils/processor.js 的SHA256不同。T10必须修实际资源并验证Web静态dist/Electron安装包加载同一修复，不能只改src副本。
- `MessageWebSocket.js:98-100` 只在页面visible时转发已收到的串口数据，隐藏页可能在进评分队列前丢事件；FIFO修复不能覆盖这个上游丢弃。必须明确后台训练语义并验证两模式的最小化/切页路径。
- Web的token/设备标识/授权受浏览器profile与origin隔离；换域名/端口/HTTP→HTTPS或清站点数据不等于硬件变化。Electron文件副本恢复能力不能写成Web也具备。详见Spec §7.3的模式矩阵及Plan的T01/T05/T10/T16/T17。

---

## 1. 报务训练系统经常掉线，需要验证码才能重登

### 1.1 掉线机制（按可能性排序）

1. **【确认】单账号单 token 互踢（设计如此，非缺陷，不修复）**。token 是 `AES(account-password-deviceId)` 确定性加密串，每次 login 覆盖写用户行：`UserService.java:452-454`（`user.setToken(token); user.setDeviceId(deviceId)`）。每个账号全库仅一条有效 (token, deviceId)。旧设备下一请求被 `JWTInterceptor.java:67` `existsUserByTokenAndDeviceId` 拒绝 → 206 → 前端 `http/index.js:33-47` 弹「登录唯一凭证异常」跳登录页。教室多终端共用账号场景必然频繁互踢——**产品决策：单点登录互踢即预期行为**（2026-09-10 确认），问题 1 的修复面只剩凭证存储脆弱性与授权码门闸（见 1.1-2、1.2）。
2. **【部分确认】deviceId 指纹漂移（机制已修正）**。前端 deviceId 取自 FingerprintJS visitorId（`bw-frontend/frontend/src/views/manage/login/useLogin.js:64-71`），仅登录时计算，后续请求用 localStorage 缓存（`bw-frontend/frontend/src/common/http/index.js:15-18`），所以指纹漂移本身不会导致当前会话掉线。存储清理可导致 203/204；其是否发生在客户设备上尚无证据。旁证：相同账号、明文密码、deviceId 再登录会生成相同 token；旧 token 在对应值重新写回用户行后可再次生效，**不是在被覆盖或退出后仍然有效**（`backend/src/main/java/com/nip/service/UserService.java:452-454,484-492`）。
3. **【确认，有前提】持有当前有效 token 的退出请求清空该账号会话**：`backend/src/main/java/com/nip/service/UserService.java:484-492` 查到当前 token 才清空 token/deviceId；已被新登录覆盖的旧 token 查不到用户，不会再踢掉新会话。
4. **【已排除】token 过期**：token 无 TTL、无续期机制，不被人顶/不退出则永久有效。
5. **【已排除】WS 断线导致登出**：ws 包不触碰 user.token；`WebSocketHeartbeat.java:10-16` 仅 ping/pong；前端 WS onclose 不触发登出。

### 1.2 「验证码」真实身份

仓内登录链路未见 captcha；**【推断，待客户截图/日志确认】**客户所述“验证码”可能是软件授权码（license）门闸。下列授权机制为源码事实，不能据此排除仓外网关或其它界面提示：

- 授权按**累计运行时长**到期：`VerifyLicense.js:16` DEFAULT_DAYS=30，前端每 10s 累加运行时长（:123-146），超限弹「授权的可用运行时长已用尽」拦回授权页（:109-119）。
- 授权码绑定设备码，硬件大幅变更后 `matchMachineCode` 不匹配（:232-238）。
- `bw-frontend/frontend/src/App.vue:1-16` 无条件包裹 VerifyLicense，**Web与Electron均有授权门闸**；只有硬件码及文件副本依赖Electron。

### 1.3 修复方向

- 单 token 互踢为设计行为，**不新增 user_session 表**。前端按 203/204/206 区分“缺少登录凭证 / 缺少设备标识 / 凭证不匹配”，206 只能提示“可能在其它终端登录，请重新登录”，不能断言互踢；后端业务码与文案保持不变。
- 稳定 deviceId 仅解决身份稳定性，不解决 localStorage 丢失或 token 重放。Electron 复用 `machineCode.js` 的既有硬件采集接口、Web 使用持久化随机标识；凭证读取失败不得悄悄生成新身份或清空授权。随机 token/TTL/安全存储沿用 `docs/plans/2026-09-09-password-session-migration-plan.md` 的独立门禁，不在本轮另建方案。
- 在剩余**累计可运行时长 ≤ 7×86400 秒**时预警，不写成自然日期“7 天后到期”；分别展示授权耗尽、设备不匹配与存储读取错误，不把所有门闸都引导为换发。

---

## 2. 个人岗位收报训练设置码速与实际码速偏差大

**纯前端问题**（后端不参与播报计时）。

1. **【确认】偏差系数方向自相矛盾**：`receiveTrain.js:39` 硬编码 `audioSpeedDeviation=1.18`；首次加载 `criterion = cri*1.18`（:253，理论速率偏慢约 15%），changeRate 反向除以 1.18（:620-623，理论速率偏快 18%），两处相差 1.18²≈1.39 倍。系数也暴露给用户输入（`receiveTrain.vue:148-151`）；源码不能证明最初引入原因。
2. **【确认常量；业务含义待定】低速分支点长按固定 35 计算**：`receiveTrain.js:240,622` 用 `isLowRate ? 35 : rate`。这证明点长分支不随设置 rate 改变，不能单凭此认定低速训练应取消固定符号速度；需确认其是否采用“固定符号速度+扩展间隔”的教学口径。
3. **【确认】AudioWorklet 参数竞态**：`MorseVoiceHighPerformance.js:427-432` updateParam 仅在 oscillator 已存在时下发；init 回推不含 criterion/ratio（:297-300）→ worklet 就绪前下发的参数**静默丢失**，停在 processor.js 默认 criterion=83ms。码/分分支靠 `setTimeout(1000)` 绕过、WPM 分支无保护且从不下发 changeRatio——两处均在 **`receiveTrain.js:251-267`**（非 MorseVoiceHighPerformance.js，此前引证文件张冠李戴）→ 上场训练的自定义划比残留进下一场，**跨训练串味**。worklet 为全局单例（`NipPagePermission.vue:105-109`）。
4. **【确认】码/分公式是经验平均值**：`cri=(400/rate×60000)/dots[type]`，dots 是「平均页点数」经验常量（`useMorse.js:202-207`，letter 4711/short 4755/long 6995/mix 5389），报文构成偏离平均即偏差；划比可调但标定按固定比例测得。
5. **【确认墙钟实现；偏差幅度待测】**实际资源 `bw-frontend/frontend/public/processor.js:170-199` 用 Date.now 判断边界，`:249-263` 定义 msToSamples 却不用于 datumSamples，仍按毫秒推进。128采样约2.7ms@48k，量化/调度抖动幅度须实测，不能把“偏慢5%~10%”当已确认。组间隔5单位是否改7仍需教学口径确认。旧src副本行号不再作为实际worklet的证据。
6. **【确认】WPM 模式「修改偏差」直接报错**：`receiveTrain.js:625` 引用从未定义的 `speedRate` → ReferenceError，码速永远停在初始值。

**修复方向**：在既有 Morse 入口统一单位/类型/比例/间隔换算，修初次与改速的系数方向、未定义 speedRate、参数 ready 回推和采样数计时；每场重置全量参数。低速模式、码/分校准报文、5/7 间隔与经验补偿的去留先按 Spec G2 确认，不直接拿真实 rate 替换 35 改变教学含义。

---

## 3. 个人岗位所有训练评分码速、时间、扣分不正确

**三侧共同：前端计时 + 后端公式 + 契约多头。**

1. **【确认】码速至少 6 种互不一致的公式**：
   - 前端数据报 `telexTrain.js:101` `speed=字符数/(duration/60)/4`；同目录收报 :86 `speed=字符数/(duration/60)`（**差 4 倍**）；
   - 前端每页 :271 按空格分组数/分；
   - 后端 trainType==4 `PostTelexPatTrainService.java:739` `ScoreMath.rate(组数, validTime*1000)`；trainType!=4 :915-916 用客户端 speedLog 求平均；（注：==4 分支 :739 算出的服务端 speed **只落库不参与扣分**——扣分比较用 :741 的客户端 totalSpeed，多头问题比「显示/扣分两数」更深一层）
   - 电子键 `GeneralKeyPatService.java:769` 拍发数/4/时间；手键 `PostTelegraphKeyPatTrainService.java:422` 不除 4。
   - **显示值与扣分依据不是同一个数**：数据报扣分用 avgSpeed（:917-926），成绩页展示的却是客户端另一公式算的 totalSpeed（`trainScore.js:50` → `PostTelexPatTrainService.java:245` 原样落库）。
2. **【确认】每页耗时状态 bug**：`telexTrain.js:269` `pageTime = pageTime===0 ? duration : duration - pageTime`——存的是增量而非结束时刻，**第 3 页起耗时全错**（100/160/200s 结束三页记成 100/60/140，实际 100/60/40）。
3. **【确认】用时双口径**：trainType!=4 分支总用时取 validTimeLog 逐页求和（:928-936），完全忽略 finish 入参 validTime；trainType==4 用入参（:738）。
4. **【确认】满分基准不一致**：trainType!=4 分支 :855 硬编码 `new BigDecimal(100)` 起评，规则满分≠100 时扣分基准即错；其余路径均用规则满分。
5. **【确认】速率加/扣系数颠倒**：全仓规范 r=加分系数、l=扣分系数（`ScoreMath.wpmScore` javadoc），但 `PostTelegramTrainService.java:816-818` 写成 l加r扣，规则 r≠l 时速率项金额算错。
6. **【确认】客户端计时失真**：所有用时来自前端 `setInterval` 秒计数（`telexTrain.js:84-85`），后台/卡顿机器 tick 节流 → 用时少计、码速虚高。

**修复方向**：先修 pageTime 增量、规则满分基准及 r加l扣；再按训练域冻结计数单位、净用时/暂停/空页规则，统一服务端权威评分和成绩展示。**原始内容不足以重算用时**：需原始事件/页时序、服务端接收时间与规则版本的可核验契约；客户端采集时间仍不是可信硬件时间，不能承诺完全防作弊。已有 `ScoreMath` 是公共计算工具，不代表所有域必须强行使用同一计数单位。

---

## 4. 部分电脑个人岗位数据报训练评不了分

1. **【确认失败分支缺失；客户触发条件待证】**`telexTrain.js:268-283` finishPage 不看 `res.code`、无 `.catch`；:255-266 endTrain 不看 code，业务失败也跳成绩页。后端业务 500 经拦截器提示后仍 resolve；203/204/206 同样返回失败信封（`bw-frontend/frontend/src/common/http/index.js:33-54`）；网络/HTTP 异常则 reject，缺少调用方恢复。可能读到未结算数据或停在中断状态，不能由源码断言客户由代理/杀毒软件触发。
2. **【确认】后端 finish 未守边界**：
   - 【确认无守卫/触发链为推断】`PostTelexPatTrainService.java:741` `Integer.parseInt(totalSpeed)` 无 null/格式守卫，收到 null/非数字 → NumberFormatException → 500；但当前前端 tick 先 duration++ 再算 speed（`telexTrain.js:85` 先于 :101），「duration=0→speed=NaN→null」窗口不存在，真实触发更可能来自旧页面/其他入口上送 null；
   - :890 `rule.getOther().getNonStandart()`、:919/:924 `rule.getWpm().getR()/getL()` 无 null 守卫 → 规则 JSON 缺字段即 NPE（规则配置差异决定哪些训练必炸）。
3. **【推断】旧运行时 API 缺失**：`telexTrain.js:98` 用 `String.prototype.replaceAll`（需 Chrome 85+），旧 Electron/Chromium 每秒 tick 抛 TypeError → 码速恒 0 → 巨额速率扣分。
4. **【推断】计时节流差异**：setInterval 被节流程度随机型/负载不同 → 同训练不同机器结果不同。

**修复方向**：finishPage/endTrain 明确判 `code===200`，网络异常与业务失败均保留未确认页、释放锁且允许重试；只有服务端确认结算成功才跳成绩页。非法数字拒绝并提示，不以 0 冒充有效成绩；规则必填项缺失返回可诊断错误并保持数据/状态可重试，仅业务明确允许缺省的可选项使用默认值。不要把所有 Optional 兜底 0，也不要在事务里吞异常提交“已结束”。

---

## 5. 个人岗位数据报倒计时训练没法评分

**倒计时结束会触发结算链**（`telexTrain.js:84-91` tick → `coun===0` → endTest → finishPage → finish → 后端 countScore），但：

1. **【确认】触发条件是精确相等 `coun===0`**（:88），无 `<=0` 兜底：训练中才开倒计时开关或把时长改小到低于已用时长（`Index.vue:22-27` 可随时改），coun 从正直接跳负 → **永不触发**；时长允许 `:min=0`，duration=0 时 coun 从 -1 起步同样永不触发。
2. **【确认】触发后仍可能失败**：finishPage 网络 reject 后 endTrain 不执行；203/204/206 的业务失败信封仍 resolve，可继续误走 finish/成绩页。当前 token 无 TTL，训练时间长不能推出“过期概率高”。服务端没有独立倒计时结束触发器，客户端未发 finish 时不能仅靠现有结算入口兜底。
3. **【确认】长倒计时放大计时漂移**：倒计时与用时都用 setInterval 秒计数而非墙钟。

**修复方向**：`coun<=0` 加防重入，以单调时钟差值而非 tick 数计算用时/剩余时间；开始时冻结合法时长，训练中禁改；对已加载的过期配置立即进入一次结束流程。失败保留页、释放锁并允许显式重试。服务端超时兜底须先明确真实开始/暂停与迟到页规则，再持久化 deadline 并由恢复扫描触发幂等结算；仅在“创建”时记录 startTime 或仅让 finish 幂等，都不能实现无人请求时自动结束。

---

## 6. 个人岗位手键训练无故连码、评分无故报错码

**前端采样链路为主。**

1. **【确认】Vue watcher 事件合并**：按下/弹起经单个 ref `handKeyValue` 传递，watcher 无 `flush:'sync'`；串口粘包时 `WebSerial.record()` 在同一同步循环连续回调多事件 → 同 tick 多次赋值被合并，只处理最后一个 → 间隔（-1）事件丢失、两个点压缩成一个 → 丢码/连码（`student.vue:356-360`、`WebSerial.js:111-138,171-179`）。
2. **【确认】点判定阈值硬编码 120ms**：`useControl.js:66-90` `diff<=120` 判点（原 `patStandard.dot*(1+initFloat/100)` 被注释掉）。开始符号自校准后 dot 基准可能 >120ms（新手点 130ms）→ 所有点被判为划 → 电码全错 → **评分无故报错码**。`diff<=10` 的抖动仍记为点并污染基准校准。
3. **【确认】连码判定依赖前端 setTimeout**：`handKeyTrain.js:275-299` wordTimer=codeGap×1.5（默认 120ms），每次按键 clearTimeout 重排；快速拍发时多字码合并查表 → 查无 → '#' 或错码。codeGap 基准又被丢间隔事件污染，下限钳 60ms（`patStandard.js:41-43`）。
4. **【确认】时间戳取自 JS 处理时刻**而非硬件事件时刻，串口缓冲/粘包延迟直接计入点划时长。
5. **【确认丢事件；永久错位结论撤回】**`bw-frontend/frontend/src/common/utils/WebSerial.js:111-137` 按下态再次收到按下帧会清空不回调，但随后合法抬起帧仍将 key_lock 复位。它丢失重复/异常转换，不能据此断言永久错位一帧；需用重复按下、缺抬起、分包/粘包序列验证恢复策略。
6. **后端对照**：后端不重建电码，逐字比对 patKeys 与报底（`MessageComparisonService.java:132-137`），前端产生的任何错字/'#' 都计入 errorNumber；且手键上传的 speed 原样参与最终结算（附录 A H2/H3：码率信客户端、划线扣分错用 dot.max）。

**修复方向**：改顺序消费的不可变事件（不以 ref 充当队列），同一事件携带码值、按下/抬起与间隔时间；恢复校准基准阈值，≤10ms 不入电码与校准；异常转换显式处理并恢复。现有串口路径只有 JS 接收时刻，改用单调时钟可避免墙钟跳变但**无法恢复缓冲前的硬件时间**；无硬件时间戳时保留测量精度限制，不伪造逐事件真实间隔。

---

## 7. 手键敲打改错符号和句号显示 # 号

1. **【确认】'#' 是映射表 miss 的兜底字符**：`handKeyTrain.js:549-550` / `details.js:695-696` else 分支 `codeInit[code]!=undefined ? codeInit[code] : '#'`。改错符 001100（当前组）/001011（前一组）不在 `useMorse.js:94-191` 四张映射表任何一张中。
2. **【部分确认】句号残留 '#'（机制已修正）**：句号点组逐组编译时先落 else 立即显示字符（短码表 '00'→'#'）；`details.js:641-661` 凑齐 3 组翻页后只 splice 掉 2 个 patKeys 与 2 个显示字符——但干净路径下第三组在 else 之前即命中翻页分支，实际残留 0，「必残留 1 个」不成立。真实残留路径是**检测失效**：连码粘连成 '0000'/'000000' 或组边界清 cacheKeyCode（details.js 无 '000000' 分支），整体粘连必落 else 产 '#' 且永不清除。组训路径（`handKeyTrain.js:504-512`）翻页分支**完全没有清除逻辑**（原句号展示代码被整段注释）——组训侧残留成立。
3. **【确认】改错特判脆弱**：仅当 code 完整等于 '001100' 或末两个拼接等于 'xx,001011' 才走改错分支；一旦连码粘连或被间隔拆开（'00'+'1100'）→ 特判失效 → '#'。
4. **【确认】拼写错误**：`handKeyTrain.js:516` `cacheKey.value.legnth`（undefined==0 恒 false）→ 改错后下标递减失效，错位产生更多 '#'。

**修复方向**：特殊符先识别为控制事件，不把翻页/改错码作为普通得分字符加入映射；按本次识别实际生成的临时显示项移除，**不能统一改成删除 3 项**（干净路径只写入了 2 项，会误删有效正文）。修 legnth，并覆盖完整码、分片、跨组边界与未知码；不能无边界地拼接历史输入来“修复”粘连。

---

## 8. 组网训练乱评分、反应慢，学员结束后教员席很久才反应

### 8.1 「乱评分」

simulation 链路**没有服务端数值评分**——「评分」是前端把学员填报与报底逐字标红对比：

1. **【确认】逐字索引对比无对齐**：`TrainResult.vue:31-32` 按数组索引逐字符比对，漏抄/多抄一组 → 其后全部错位标红；学员答案超长时 `undefined != v` 恒真全标红；学员可自行加页（`FillInResult.vue:34-36`）超出报底页数无标准可比。
2. **【确认机制/竞态推断】报底懒生成竞态**：建房只预生成 min(bwCount,200) 组；第 3 页起 findPage 懒生成，isRandom=1 用无种子 ThreadLocalRandom，两个端并发首拉同一未生成页 → 各自生成不同随机内容都入库 → 报底与播放内容错位 → 大面积误判。
3. **【确认】重复填报叠加污染**：`backend/src/main/java/com/nip/service/simulation/SimulationRouterRoomContentService.java:215-226` 每次插入整份答案不替换旧行；`backend/src/main/java/com/nip/dao/simulation/SimulationRouterRoomPageValueDao.java:10-11` 用无排序 firstResult，重复记录下可能读到旧值，**不保证一定是最旧值**；count 被重复行虚增。
4. **【旁证】综合组网学员端自报分**：`trainingDetails/Index.vue:85-98` 用 `parseFloat==parseFloat` 浮点等值比较自算分，`GroupNetTrainService.java:127-132` 服务端零校验原样入库；且把路由参数 range 当 deviceId 传（:64）可能按错规则打分。

### 8.2 「反应慢/教员席延迟」

教员席状态更新**纯靠 WS 推送、零轮询兜底**，且推送链多处断流（全部确认事实）：

1. 教员席仅 onMounted 拉一次详情（`useBroadTeacher.js:199-200`），WS 收不到推送就永远不更新。
2. **裸 WebSocket 无心跳无重连**：simulation 各页 onclose 只置 null、重连行被注释（`useBroadTeacher.js:111-115`、`useBroadStudent.js:115-118`、`train.js:229-232`）；`Issue.js`/`ListenIn.vue` 更彻底——全篇**没有 onclose 处理器**（原引 :65/:301 实为 `new WebSocket` 行）。带心跳重连的 `SocketConnection` 除联合训练（UnionWs.js）外还有全局通知（Ws.js/PublicSocket.js）在用，但 simulation 各页无一引入。断链后教员席永久失聪。
3. **【确认，有入口前提】结束态页面初始化不建连**：`bw-frontend/frontend/src/components/BroadcastTeachTrain/js/useBroadTeacher.js:99-115,199-200` 在 login 时 status==2 直接 return。已连接页面不会仅因状态变成 2 自动断开；问题发生于结束后进入/刷新页面，以及既有连接掉线后不能重连。
4. **【确认，通知内容不一致】**学员 REST 返回后发送 `{type:'result',existPage}`，不带 id（`bw-frontend/frontend/src/components/BroadcastTeachTrain/js/useBroadStudent.js:203-214`）。报务教学房 `backend/src/main/java/com/nip/ws/WebSocketSimulationService.java:535-550` 只向第一个 channel==0 成员发补齐 id 的 mesg，但 **:552-555 仍向其它连接广播原始 message**，所以不是“其余教员完全没收到消息”，而是收到缺 id 的通知；教员按 data.id 更新（`useBroadTeacher.js:124-129`），导致不可应用。干扰房 :466-472 向全体 userType==0 广播，须独立对账主题格式。通知仍依赖学员事后发 WS，sendMessage 失败仅记录日志。
5. 学员端收到结束广播后 `location.reload()`（`train.js:296-300`）整页重载。
6. **状态双写漂移**：房间/人员状态既写 DB 又存进程内存 `SimulationGlobal` 三个 static Map；getRoomDetail 在线状态读内存、其余读 DB；服务重启内存全空。

### 8.3 修复方向

- 复用 SocketConnection 的心跳/重连与生命周期，保留各房型编解码；结束态进入也连接。连接恢复、结果通知后拉取现有详情；教员页可见且仍等待结果时用有上限的轮询补偿丢通知，离页停止。后端入口已有 `WebSocketHeartbeat.respond`（`WebSocketSimulationService.java:358`），迁移时实际验证控制帧不进入业务 JSON 解析。
- uploadResult **事务提交后**向房间内全体教员通知已保存状态；选择轻量通知触发 REST 权威快照读取，避免在事务内广播未提交数据。不再由客户端 WS 改写已提交状态；报务房补齐身份的通知必须一致发给各教员，去除双版本重复投递。
- 幂等必须匹配当前“整份答案”接口：事务内替换该学员整份页集合，删除本次缺失的旧尾页，约束 `(room_id,user_id,page_number)`；只 upsert 会留下尾页。报底保留有界懒生成，锁定房间 DB 行、锁内重查并整页落库，约束 `(room_id,page_number,sort)`；仅进程锁无法覆盖多实例，不无上限全量预生成。
- 结果对齐属于展示语义调整；不把 simulation 新增数值计分与综合组网已有自报分混为一项。先明确漏/多组如何展示，再做有限页内对齐；综合组网自报分另以本域规则服务端计算。

---

## 9. 组网训练教员席无法查看每个人员训练详细情况

**不是端点缺失**——明细端点存在且前端已接线：`GET /api/simulation/router/findPage`（`SimulationRouterRoomController.java:91-98`），三个教员席查看入口均已接线；历史 roomgId/roomId 契约缺陷已修复。真实原因是可用性限制 + 数据污染：

1. **【确认】训练中禁止查看**：`useBroadTeacher.js:35` `status<2` 直接 return false；且只有 userStatus==1（已上传答案）的学员才有查看入口——**训练中教员看不到任何学员的实时进展**，体感即「无法查看详细情况」。
2. **【确认】详情数据被污染**：重复填报使无排序 firstResult 可能读到旧答案、existPageNumber 虚高；超出报底的页面应明确标记“超出报底”，不能伪造 '--' 作为评分基准。
3. **【推断，有前提】**仅缺失页才触发懒生成（`backend/src/main/java/com/nip/service/simulation/SimulationRouterRoomService.java:381-403`）；不能说每次查看都重新生成。并发生成与历史报底丢失是需要验证的异常场景。

**修复方向**：先恢复结束后可见的已提交详情与页集合一致性；“训练中实时查看”是新增能力，当前答案通常结束后提交，**只删 status 门闸没有实时数据**。必须先确认客户需要实时草稿还是结束成绩，再定义逐页草稿上报、授权、非最终状态与推送协议；未确认不擅自改变训练过程可见性，也不将该项记为已关闭。

---

## 10. 个人岗位电子键播报码速不正确、反应较慢

**纯前端问题**（后端只用拍发时间戳算考核速度，与播报无关）。

### 10.1 码速不正确

1. **【确认】报文类型取错标定常量**：`ElectronMorse.js:90-93` 固定用 `dots['short']=4755`，而长码报播报用 `numType='long'`（dots=6995）→ 点长放大 1.47 倍 → **长码报实际比设置慢约 32%**。
2. **【确认】不看 wpmTOmm 开关**，WPM 模式也按码/分公式算，与收报链路口径不一致。
3. **【确认】播报速度不跟随训练设置**：`examTrain.js:49` playSpeed 硬编码默认 80；本该跟随训练速度的 `changeCriterion(trainData.value.speed)` **被注释掉**（:147-148，组训 student.vue:316-317 同样被注释）。
4. **【确认】初始化竞态与模式有关**：postJob examTrain mounted 即changePlaySpeed，早于worklet就绪会丢参数。`NipPagePermission.vue:88-118` 在Electron mounted尝试初始化，而Web等待用户手势，因此**不能断言常规入口不触发或只在刷新时触发**。硬编码80与未跟随仍是独立直接原因；preJob已有跟随但仍需验证ready顺序。

### 10.2 反应慢

1. **【确认】F2 组合键 800ms 判定窗**：`examTrain.js:363-370` 每次按键后 setTimeout(800ms) 等待组合键，字码赋值与播报整体滞后最多 800ms——最大单点。
2. **【确认日志存在；卡顿贡献待测】**主线程 `MorseVoiceHighPerformance.js:348-352,361-362` 与 examTrain.js 有调试输出；**实际worklet `bw-frontend/frontend/public/processor.js:50-56` 的addCode分支确有console.log(this.morseCode)**。之前仅查src副本得到“不存在”是错误取证。public资源不能以Vite drop_console配置代替安装/部署产物核验；删除热路径日志后仍需实测卡顿改善。
3. **【确认】多级异步链路**：串口/WS → pinia $subscribe → watch → addCode → PubSub 微任务 → convert 全量转码 → postMessage → worklet；另有 `setTimeout(3000/1000)` 延迟订阅（`ElectronMorse.js:102-122`），初始化期按键无声或按默认参数发声。

**修复方向**：changeCriterion 按报文类型与模式换算，postJob/组训恢复训练速度跟随；参数等待 ready，删热路径调试输出。F2 先按 Spec G2 明确合法组合等待窗与目标机延迟阈值，再优化，不能只调小常量而破坏组合键。

---

## 11. 个人岗位手键单字拍发点划间隔设置不正确，无法基础训练

1. **【确认】保存-读取往返 off-by-one**（注意：本条两个文件均在 **preJob** 目录）：保存侧 `preJob/.../telegram.js:222-247` rateIntervalMaxMs=dot×4、bigIntervalMaxMs=dot×10；读取侧 `preJob/.../HandKeyTrain.vue:770-772` 反推比例时 interval/gap **多减了 1**（parseInt(÷−1)=3 和 9，而 line 不减）→ 任何输入触发 handlePatDeployData 即按错误比例重算（`useDetails.js:581-588`）→ 设置界面显示的比例本身就错，生效间隔与所设不符。
2. **【2026-09-11归因修正；用户已决定保留纯自校准】** postJob手键无持久化点划/间隔字段或固定模式；`PostTelegramTrainEntity`与创建页没有该设置，`train/js/useControl.js`提供80/240/80/240/400初值，`details.js`会按开始符号自校准，因此“阈值恒为硬编码、用户配置永不生效”不成立。用户明确不新增持久化初值/配置入口，维持现有自校准；preJob往返及基础区间问题仍须修复。
3. **【原归因撤回】基础训练配置已经参与分级**：`bw-frontend/frontend/src/views/manage/preJob/telegram/train/js/basicTrain.js:38-70,124-154` 从 getBasicSetting 读取区间，并按当前“点/划练习”页签给时长分级；`HandKeyBasicTrain.vue:148-155` 将按下时长交给它，不是按 useControl 的点/划码值选择分级。它是独立的基础区间配置，不是训练 DTO 中的四段毫秒设置；不能把两者直接替换为同一阈值表。
4. **【确认缺边界；客户触发待复现】**`basicTrain.js:140-142` 无 type===0 兜底区间时会对 undefined 取 value；异步配置未加载时也需禁用/排队输入。`preJob/telegram/handkey/js/telegram.js:417-440` 没有正区间时可生成 '<undefined'。修配置加载时序、空/坏区间和保存校验，而非重写已经存在的分级算法。

**修复方向**：preJob训练DTO的毫秒上下限与界面比例保存/读取互逆；postJob按用户补充决定保留纯自校准，不新增配置能力。基础练习继续使用getBasicSetting区间模型，加载失败或配置不完整时明确禁用开始并允许修复/重试。saveSetting在deleteAll前校验全部区间，失败保留旧配置；不得将任意损坏配置默认为满分或0。

---

## 12. 理论学习无法导入题库，理论测试系统不会操作

**【确认仓库发布缺口；客户版本根因待确认】**题库修复尚未进入本地已知 release tag，但不能由此认定客户必然使用旧版。

基线 `3360221` 核查：`git tag --contains 1c40aae`（后端）与 `git tag --contains 9596c6c`（前端）均为空，已知 tag 为 v1.0.0/v1.1.0；两个修复提交已在本地 `origin/main` 引用的历史中。未向远端刷新、未取得客户安装包 hash/版本与服务端版本，因此现场是否缺修复需要核对制品，不以 main 领先提交数作为发布依据。旧版缺陷如下：

1. **【确认】发布版后端导入端点是空壳**：v1.0.0 `TheoryKnowledgeQuestionController.java:80-83` upLoadFile 空实现直接返回成功；v1.1.0 连 upLoadFile 空壳都已删除，saveBatch/exportTemplate 路由不存在。当前 main 的导入走 saveBatch——upLoadFile 端点是**删除**而非实现，属契约变更，发布说明需注明。
2. **【确认仓外 URL 依赖；404 与因果关系为推断】**旧模板 URL 为 `/api/file/getFile/006/题库-模板.docx`，指向独立文件服务而非本仓后端；本仓无该路由不等于客户文件服务 404（见 `docs/reviews/archive/2026-09-08-joint-theory-file.md:119`）。需现场请求记录确认是否影响下载，不能直接将“不会操作”归因于此。
3. **【确认】旧前端 docx 解析脆弱 + 假成功**：按硬编码版式解析，稍有不符即报错中断；逐行 fire-and-forget 上传后 `setTimeout(2000)` **无条件提示成功**，全失败也提示成功。

**当前 main 状态**：已有 saveBatch 整批事务、逐行校验回滚与 code===200 成功判定；`backend/src/test/java/com/nip/controller/TheoryKnowledgeUploadExportTest.java` 有 6 个测试方法，覆盖导入/导出/文件边界，不等于本轮运行通过。模板已有示例行（`bw-frontend/frontend/src/views/manage/basicTheory/test/questionBank/js/knowledgeTabel.js:349-365`），但表头用 field 丢弃中文 title，options 仍为 JSON；levelId 示例是说明文字且非空，会优先覆盖当前题库兜底（同目录 `questionImport.js:86-91`）。必须同步导出与解析，不能只改中文表头或宣称从未有示例。

**修复方向**：
- **发布闭环**：先取得客户版本/制品，再从同时含两个修复的确定提交构建前后端。当前 `.github/workflows/build-quarkus-native.yml:42-60,197-220` 的前端 job 只构建、不上传产物，release 仅依赖 build/test，不依赖 frontend；打 tag 不能保证双端交付。需补前端成功门禁、制品归档及 Electron 打包消费证明。
- **桌面资产闭环**：`bw-frontend/frontend/vite.config.js:49-50` 输出 frontend/dist；`bw-frontend/electron/index.js:60-66` 打包态读取 public/dist；`bw-frontend/package.json:23-34` 排除 frontend。必须验证新 dist 进入实际安装包，而不是旧 public/dist。
- **Web资产闭环**：同一构建的frontend/dist部署到静态站点，核对入口/动态chunk/processor.js、实际HTTP或HTTPS地址及反代映射、文件服务、缓存更新；无需Electron安装包，但必须有Web发布/回滚清单与真实浏览器验收。
- 模板 UX：展示中文列说明并保持唯一 field 契约；修复 levelId 示例覆盖问题，自动绑定当前选中题库；保留已有示例并补选项/答案填法。按目标版本完成“选题库→导模板→填表→导入→查询”及 DOCX 导入；“理论测试不会操作”另验建卷/开考/交卷/查成绩实际流程，不以导入成功代替。

---

## 13. 优先级与修复批次建议

| 批次 | 内容 | 覆盖问题 | 理由 |
|---|---|---|---|
| P0 交付核查 | 核对客户版本；补前端成功门禁、双端制品与 Electron 资产闭环；验证后再发布 | 12 | 优先恢复交付；不是已证明的不改代码即见效 |
| P1 评分可信 | 确定性公式修复；提交失败可重试/倒计时防重入；规则边界拒绝脏数据；单独冻结原始计时与评分契约后服务端重算 | 3、4、5（附录 A H2/H3/M1/M3/M4/M5） | 跨栈数据完整性，不是纯后端小改 |
| P1 手键采样 | 有序事件（含电子键多码帧，附录 A H1/M2）；校准阈值；legnth；控制符优先识别与按实际占位清除 | 6、7 | 高频训练路径，改动集中在前端 |
| P2 码速口径 | 统一换算函数；修速度跟随注释；worklet 参数就绪前排队；删热路径 console | 2、10 | 需音频回归验证 |
| P2 组网 | 事务后统一结果通知 + REST 快照/重连补偿；整份答案幂等替换；报底锁内懒生成；实时草稿与对齐先确认语义 | 8、9 | 两种唯一键与迁移，跨栈同步 |
| P2 点划间隔 | preJob训练毫秒/比例互逆；基础区间加载与空配置守卫；postJob按2026-09-11用户决定保留纯自校准 | 11 | 两类设置不混用，不新增postJob配置能力 |
| P1 越权收口 | 手键 upload/finish/reset 与电子键 finish 一律从 token 推导用户（附录 A H4/H5） | 非客户报障，评审确认 HIGH | 触及红线 6，随 P1 同步修 |
| P3 凭证与授权提示 | 保留单 token 互踢；稳定 deviceId；授权剩余运行时长预警；前端按鉴权码解释；安全会话迁移引用既有计划 | 1 | 不改后端 203/204/206 码值文案，不以设备标识稳定化声称防重放 |

## 14. 验证要求（修复时执行）

- 评分类：新增/更新「原始拍发 → 最终 score」端到端结算测试（现有 `ScoringConsistencyTest` 只覆盖 `ScoreMath.wpmScore`）；篡改 speed/重复 finish/上传失败重试用例。
- 音频类：按冻结的点划/间隔口径计算期望采样数，数字输出段误差 ≤1 sample，完整校准报文时长误差 <2%；44.1/48kHz、WPM 与码/分、四种报文类型、冷启动/跨训练均覆盖。码/分经验常量不保证任意组成的报文都 <2%；真实设备端到端延迟单独实测。
- 组网类：双端并发首拉未生成页、教员断链重连、学员结束后教员无刷新可见。
- 发布前：`cd backend && ./mvnw -B clean verify` 全绿；跨栈契约改动核对前端调用面（红线 5）。
- 双模式：每组适用用例分别登记Web与Electron；Web再区分HTTPS功能验收与普通远程HTTP的能力限制验收。Web受浏览器许可约束、Electron受实际壳版本与硬件接线约束，不能相互替代证据。

## 15. 本轮文档复审记录

- **初审结论：REQUEST CHANGES，已在本文修订**。阻塞点包括：客户版本/授权界面未经现场证明、deviceId 与防重放混淆、评分/用时可信边界不完整、规则全默认 0、句号固定删 3、报务通知漏看后续广播、整份答案 upsert 遗留尾页、基础训练“未使用区间”误判、release 缺前端制品门禁。
- **修订后结论：可作为规划基线**。12 项及附录 H1–H5/M1–M5 均须在 spec/plan 有处置；产品口径与现场验收前置不能伪装成已确认事实或已完成修复。单 token 互踢仍不修。
- **证据方式**：直接读取关键调用链；LSP 返回未配置，使用 API 定义/调用点搜索；本地 tag/提交祖先核查；算术反例得到 pageTime `[100,60,140]`（期望 `[100,60,40]`）；调用真实 `parseSpreadsheetRows` 得到模板说明字符串覆盖 selected-bank-id。没有修改业务代码，没有运行客户设备或后端全套测试。
- **并行核查限制**：三路补充核查均因服务 503 未产出报告，未据其声称通过；上述结论由主评审直接取证。最终三文档交叉复审记录落在实施计划末节。

---

## 附录 A：手键/电子键联合评审归档

> 原 `docs/reviews/2026-09-10-handkey-electronic-key-joint-review.md` 全文要点合并于此（评审日期 2026-09-10，范围：手键 `generalTickerPat`、电子键 `generalKeyPat` 组训学生端/教员端及后端 REST/WS/落库/结算）。评审总体结论为 **REQUEST CHANGES / CRITICAL**：端点域名虽已对齐，但拍发与评分没有形成统一可验证的契约。以下编号沿用原评审（H=严重，M=中等）。

### A.1 活跃调用链

**手键**：

```text
硬件/报训数据
  → useControl.js:handleHandKeysData
  → student.vue:watch(handKeyValue)
  → handKeyTrain.js:handleReceiveKeyCode
  → WebSocket /generalTickerPat/{uid}/{trainId}/0
  → POST /api/generalTickerPatTrain/uploadResult
  → GeneralTickerPatService.saveContentValue
  → POST /api/generalTickerPatTrain/finish
  → GeneralTickerPatService.finish
  → MessageComparisonService
  → applyDeductions / saveTrainUserResult
  → detail/statistics 回读成绩
```

逐页上传字段：`userId / trainId / floorNumber / messageBody / standard / finishInfo / validTime / speed / errorNumber / accuracy`（DTO：`dto/vo/simulation/tickerPat/GeneralTickerPatTrainContentValueVO.java:17-60`）。

**电子键**：

```text
串口数据 data.d
  → useControl.js:handleElectronicKeysData
  → patKey ref
  → student.vue:watch(patKey)
  → handKeyTrain.js:handleReceiveKeyCode
  → WebSocket /generalKeyPatTrain/{uid}/{trainId}
  → POST /api/generalKeyPat/uploadResult
  → GeneralKeyPatService.saveContentValue
  → POST /api/generalKeyPat/finish
  → GeneralKeyPatService.finish
  → KeyPatUtils.handle
  → GeneralKeyPatService.countScore
  → patDetail/detail 回读成绩
```

电子键逐页上传由 token 推导用户，仅传 `trainId / pageNumber / pageValue`；但 `finish` 仍接受 body `userId`（见 H5）。

### A.2 严重问题（H1–H5）

**H1 电子键多码串口帧丢拍发码**【HIGH，对应问题 6 同一机制】：`electronKeyZuXun/train/student/js/useControl.js:13-22` 循环 `data.d.forEach(e => patKey.value = e.toString())`，学生页靠单个 `patKey` watcher（`student.vue:313-319`）消费；一次串口帧含多码时 Vue 合并更新只处理最后一个 → a、b 丢失 → 少码/错码/正确率与分数错误，后端无法恢复前端已丢事件。**修复**：逐项调用 `handleReceiveKeyCode` 或 FIFO 队列，禁止用单个 ref 当事件队列。

**H2 手键最终码率信任客户端上传值**【HIGH，对应问题 3】：前端算并上传 `speed`（`handkeyZuXun/.../handKeyTrain.js:127-131,765-775`），后端 `GeneralTickerPatService.java:523-557` 存入 speedLog，结算 :959-974 再取平均参与 `calculateWpmScore` → 客户端间接控制最终成绩的码率项；重复提交重复追加 speed；reset 只删拍发页不清结算字段与 speedLog。**修复**：后端按 messageBody + 点划时长/间隔重算码率，前端 speed 仅用于实时显示。

**H3 手键划线扣分错用点的最大扣分值**【HIGH，确定性评分错误，对应问题 3】：`GeneralTickerPatService.java:885-893` `calculateScore(rule.getDash().getMax(), scoreVO.getLineScore(), rule.getDot().getMax())`——超上限时返回第三参数，dot.max=1/dash.max=5/lineScore=7 时扣 1 而非 5。**修复**：第三参数改 `rule.getDash().getMax()`，补 dot.max≠dash.max 的 General 手键结算测试。（旁证：旧路径 `PostTelegramTrainService.java:736` 同一缺陷已修复为 dash.max，且 `PostTelegramTrainScoreTest.java:32-42` 用完全相同的数值场景（dot.max=1/dash.max=5/lineScore=7）锁住——General 活跃路径是上一轮整改的漏网，修复样板与测试样例现成，可直接移植。）

**H4 手键 uploadResult/finish/reset 使用请求体 userId**【HIGH，越权】：`GeneralTickerPatController.java:75-96` + `GeneralTickerPatService.java:498-557,640-643`，`@JWT` 只验 token 有效，用户 ID 来自 body/query → 已登录用户可覆盖他人拍发结果、注入 speedLog、触发他人提前结算、删他人数据。**修复**：学员自有接口一律从 token 推导用户；教员查他人走独立授权路径。

**H5 电子键 finish 仍信任 body userId**【HIGH】：上传接口已改为 token 推导（`GeneralKeyPatService.java:429-453`），但 finish 仍 `findByUserIdAndTrainId(dto.getUserId(), ...)`（`GeneralKeyPatController.java:99-104`、`GeneralKeyPatService.java:456-487`）。**修复**：finish 从 token 推导，同时验证训练归属/参与资格与可提交状态；前后端同提交删除 body userId，不保留忽略字段的假兼容。

### A.3 中等问题（M1–M5）

**M1 电子键逐页上传失败后提交锁永久卡死**【MEDIUM】：`electronKeyZuXun/.../handKeyTrain.js:434-459` `count` 计数锁，请求 reject 无 `.catch/.finally` → `count` 恒 1，后续翻页提交与 `handlerSubmit('end')` 静默返回 → 数据无法完成提交（对应问题 4 同类）。**修复**：Promise 锁 + finally 释放，失败保留当前页允许重试。

**M2 手键输入值与时间数组经多个共享 ref 拼装**【MEDIUM】：`handkeyZuXun/.../useControl.js:31-93`、`student.vue:356-360`，`diffTime/gapTime/handKeyValue` 分别更新、watcher 异步读取，存在值与时间数组错配风险。**修复**：改用不可变事件对象/事件队列 `{code, diffTime, gapTime, timestamp}`。

**M3 手键结束训练时在线人员查询失败吞掉结算**【HIGH 级影响】：`GeneralTickerPatService.java:600-632` 查询异常只记日志后用空列表继续 → 训练主记录置为已结束但不结算任何学员 → 「已结束但无成绩」且无法再次自动结算（对应问题 4/5 同类死局，亦触碰红线 1 事务吞异常）。**修复**：查询失败时阻止状态转换或进入可重试结算态。

**M4 手键 finish DTO 丢弃 validTime/finishInfo**【LOW/MEDIUM 契约漂移】：前端发送两字段（`handKeyTrain.js:676-681`），后端 `GeneralTickerPatTrainFinishVO.java:14-20` 无对应字段被静默丢弃。**修复**：二选一——删除前端无效字段，或纳入正式完成契约并明确幂等语义。

**M5 电子键懒生成页未初始化 value**【LOW】：`GeneralKeyPatService.java:276-306` 懒生成页缺 `value="[]"`（固定生成路径 :211-218 有）→ 不同路径数据结构不一致，影响详情回放。

### A.4 评分口径对账（三套公式并存，对应问题 3）

`ScoreMath.wpmScore` 实际仅被三条路径用于速率加减分（`common/utils/ScoreMath.java:64-72`。注意：`ScoreMath.rate`/`accuracy` 另有 EnteringTelexPatService、PostTelexPatTrainService、TelegraphKeyPatTrainService、PostTelegraphKeyPatTrainService 等调用方未入下表，公式 sprawl 比「三套」更大）：

| 路径 | 比对 | 正确率 | 码率 | 结算 |
|---|---|---|---|---|
| 手键 General | MessageComparisonService | correct / patTotalNum | 前端上传 speed 的后端平均 | GeneralTickerPatService.countScore |
| 电子键 General | KeyPatUtils.handle | (patGroup - error - bunchGroup - lack - more) / patGroup | pat / 4 / patTime × 60 | GeneralKeyPatService.countScore |
| 历史 PostTelegraph 电子键 | — | ScoreMath.accuracy | ScoreMath.rate | — |

三套正确率/码率公式无明确契约、无端到端测试证明差异是有意的——与问题 3 的「6 种码速公式」同根。

### A.5 测试缺口（并入第 14 节执行）

现有 `ScoringConsistencyTest` 只验证 `ScoreMath.wpmScore`；`PostTelegramTrainScoreTest` 覆盖的是旧路径，保护不了活跃 General 路径。缺：GeneralTicker/GeneralKey 完整结算、原始拍发→最终 score、手键/电子键同边界对账、speed 篡改、重复 finish、上传失败重试、多码串口帧、token owner 与 body userId 不一致、dash.max≠dot.max、结束时在线查询失败。前端无自动化覆盖上述任一行为。

### A.6 原评审修复优先级（并入第 13 节批次）

1. 电子键多码改队列/逐项消费（→ P1 手键采样批次）；
2. 修 dash.max 错用 dot.max（→ P1 评分可信批次）；
3. 手键 upload/finish/reset 从 token 推导用户（→ P1，安全项）；
4. 电子键 finish 从 token 推导用户（→ P1，安全项）；
5. 手键后端重算码率（→ P1 评分可信批次）；
6. 电子键提交失败释放锁并重试（→ P1）；
7. 结束训练结算失败不得提交已结束状态（→ P1）；
8. 补 General 端到端结算测试（→ 第 14 节）；
9. 明确正确率/码率/少多码组公式契约（→ P1 评分可信批次）；
10. `teacherBack.js` 等无活跃调用者的历史代码不作为本轮客户报障验收项；仅在本轮切换确实使代码过时时核对引用并清除，既有长尾治理仍由原计划承接。

在 1～5 完成并有端到端回归证据前，手键/电子键拍发与评分不能标记为已验收。

### A.7 已确认正常的部分（避免重复排查）

- 手键 API 均指向 `generalTickerPatTrain`；电子键 API 均指向 `generalKeyPat`；
- 活跃 `teacher.js` 三个训练域无跨域调用；电子键 reset 已用 `generalKeyPat/reset`；
- 手键/电子键 WS 路径分别对应同名后端 endpoint；
- 成绩页 score/deductInfo/accuracy 主要从后端详情读取；前端不直接提交最终 score 字段；
- `teacherBack.js` 等死代码有错误 import/串域引用，但无活跃调用者。
