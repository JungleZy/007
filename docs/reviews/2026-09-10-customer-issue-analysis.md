# 客户报障 12 项问题分析

- **分析日期**：2026-09-10
- **问题来源**：客户使用反馈（12 条）
- **分析范围**：`backend/`（Quarkus 服务）、`bw-frontend/frontend/`（Vue 前端，Electron 桌面壳）
- **分析方式**：6 路并行源码调查（调用链追踪 + file:line 取证），结论区分「确认事实」与「推断」
- **关联文档**：`docs/reviews/2026-09-08-joint-frontend-backend-review.md`。原《手键与电子键拍发、评分联合 Review》（2026-09-10）已合并入本文，见附录 A，原文件不再单独保留
- **修订记录**：2026-09-10 经 6 路并行独立核查（对照仓库现状 + git 历史 + 前端调用面逐条验证），修正：指纹漂移机制（1.1-2）、AudioWorklet 竞态文件归属与首因定性（2-3、10.1-4）、processor.js console 伪引证（10.2-2）、句号残留机制（7-2）、TOPIC_RESULT 房型归因（8.2-4）、NaN 触发链降级为推断（4-2）、发布语境（12）；删除不存在的引证，补充 token 可重放、preJob/postJob 入口区分等遗漏事实。同日产品决策：**单 token 互踢为设计行为不修复**，撤销 user_session 会话表改造（1.1-1、1.3、P3 批次）。

## 0. 总体结论

12 条问题可归并为 **6 个系统性根因簇**，不是 12 个孤立 bug：

| 根因簇 | 覆盖问题 | 侧 |
|---|---|---|
| C1 会话模型：单 token 互踢（**设计如此，不修复**）+ 凭证存储脆弱 + 授权码门闸 | 1 | 后端设计 + 前端 |
| C2 客户端信任：评分输入（码速/用时/总分）由客户端自报，后端零重算 | 2、3、4、5、8、10 | 契约（前后端共同） |
| C3 前端计时与音频节拍：`setInterval`/`setTimeout`/墙钟计时 + AudioWorklet 参数竞态 | 2、4、5、10 | 前端 |
| C4 摩尔斯采样链路：事件合并、阈值硬编码、映射表缺失、设置不生效 | 6、7、11 | 前端为主 |
| C5 WS 健壮性：裸 WebSocket 无心跳无重连 + 推送单点投递 + 状态双写 | 8、9 | 前后端共同 |
| C6 版本发布滞后：题库导入修复未进任何 release | 12 | 发布流程 |

**评分类问题（3/4/5/6/8）的共同本质**：系统把「客户端算好的数」当权威结果落库，服务端不重算、不校验、无兜底。客户端任何一种失败（断网、token 过期、NaN、计时漂移、事件丢失）都直接变成「评分错误/评不了分」。

---

## 1. 报务训练系统经常掉线，需要验证码才能重登

### 1.1 掉线机制（按可能性排序）

1. **【确认】单账号单 token 互踢（设计如此，非缺陷，不修复）**。token 是 `AES(account-password-deviceId)` 确定性加密串，每次 login 覆盖写用户行：`UserService.java:452-454`（`user.setToken(token); user.setDeviceId(deviceId)`）。每个账号全库仅一条有效 (token, deviceId)。旧设备下一请求被 `JWTInterceptor.java:67` `existsUserByTokenAndDeviceId` 拒绝 → 206 → 前端 `http/index.js:33-47` 弹「登录唯一凭证异常」跳登录页。教室多终端共用账号场景必然频繁互踢——**产品决策：单点登录互踢即预期行为**（2026-09-10 确认），问题 1 的修复面只剩凭证存储脆弱性与授权码门闸（见 1.1-2、1.2）。
2. **【部分确认】deviceId 指纹漂移（机制已修正）**。前端 deviceId 取自 FingerprintJS visitorId（`useLogin.js:64-71`），但**仅在登录时计算一次**，之后请求面使用 localStorage 缓存值（`http/index.js:15-18`），永不重算指纹 → 指纹漂移本身不会导致本机掉线，只在下次登录时覆盖 DB 行、踢掉同账号其他机器。存储清理导致的掉线实为 localStorage 丢失（203 缺 token），与指纹无关。（旁证【确认】：token 为 AES/ECB(账号-密码-deviceId) 确定性密文，同账号同密码同 deviceId 每次登录产出**相同 token**，旧 token 永久可重放——比互踢更值一提的安全事实。）
3. **【确认】任一终端退出登录清空全账号会话**：`UserService.java:484-489` 把 token/deviceId 置 null，同账号其他在线机器立即被踢。
4. **【已排除】token 过期**：token 无 TTL、无续期机制，不被人顶/不退出则永久有效。
5. **【已排除】WS 断线导致登出**：ws 包不触碰 user.token；`WebSocketHeartbeat.java:10-16` 仅 ping/pong；前端 WS onclose 不触发登出。

### 1.2 「验证码」真实身份

登录链路全程无验证码（后端无 captcha 代码）。客户所述「验证码」是 **Electron 端软件授权码（license）门闸**：

- 授权按**累计运行时长**到期：`VerifyLicense.js:16` DEFAULT_DAYS=30，前端每 10s 累加运行时长（:123-146），超限弹「授权的可用运行时长已用尽」拦回授权页（:109-119）。
- 授权码绑定设备码，硬件大幅变更后 `matchMachineCode` 不匹配（:232-238）。
- `App.vue:35` 以 VerifyLicense 包裹整个应用，非 authorized 时主界面不可达。

### 1.3 修复方向

- ~~会话表改造（user_session 一行一 (token, deviceId)、同设备重复登录不互踢）~~——**已撤销**：单 token 互踢为设计行为，不做会话表改造。保留：掉线提示区分「他处登录」与「凭证失效」（203/204/206 目前共用同一弹窗文案，客户无法区分，见 `http/index.js:33-47`）。
- deviceId 弃用 FingerprintJS，Electron 端复用 `machineCode.js` 硬件因子，浏览器端用持久化随机 UUID（目的：身份稳定与去重放；指纹漂移本身不掉线，见 1.1-2）。
- 授权到期前 7 天主界面提示剩余时长；向客户澄清「验证码=授权码」并走换发流程。

---

## 2. 个人岗位收报训练设置码速与实际码速偏差大

**纯前端问题**（后端不参与播报计时）。

1. **【确认】偏差系数方向自相矛盾**：`receiveTrain.js:39` 硬编码 `audioSpeedDeviation=1.18`；首次加载 `criterion = cri*1.18`（:253，偏慢约 15%），而「修改偏差」changeRate 里反向除以 1.18（:620-623，偏快约 15%），两处相差 1.18²≈1.39 倍。该系数还暴露给用户输入（`receiveTrain.vue:148-151`）——开发者明知公式不准用手搓系数兜底。
2. **【确认】低速率写死 35**：`receiveTrain.js:240,622` `isLowRate ? 35 : rate`，勾选低速率后公式与设置码速完全脱钩。
3. **【确认】AudioWorklet 参数竞态**：`MorseVoiceHighPerformance.js:427-432` updateParam 仅在 oscillator 已存在时下发；init 回推不含 criterion/ratio（:297-300）→ worklet 就绪前下发的参数**静默丢失**，停在 processor.js 默认 criterion=83ms。码/分分支靠 `setTimeout(1000)` 绕过、WPM 分支无保护且从不下发 changeRatio——两处均在 **`receiveTrain.js:251-267`**（非 MorseVoiceHighPerformance.js，此前引证文件张冠李戴）→ 上场训练的自定义划比残留进下一场，**跨训练串味**。worklet 为全局单例（`NipPagePermission.vue:105-109`）。
4. **【确认】码/分公式是经验平均值**：`cri=(400/rate×60000)/dots[type]`，dots 是「平均页点数」经验常量（`useMorse.js:202-207`，letter 4711/short 4755/long 6995/mix 5389），报文构成偏离平均即偏差；划比可调但标定按固定比例测得。
5. **【确认】计时系统性偏慢**：`processor.js:158-160,178` 用墙钟 `Date.now()` 判断符号边界，每符号最多滞后一个渲染量子（128 采样≈2.7ms@48k）逐符号累积；高码速（点长 20ms 量级）相当于偏慢 5%~10%。`msToSamples`（:236-239）定义后从未调用（datumSamples 名为采样数实为毫秒，整条计时链是墙钟）。组间隔按 5 单位（非标准 7 单位）偏快，进一步口径混乱。
6. **【确认】WPM 模式「修改偏差」直接报错**：`receiveTrain.js:625` 引用从未定义的 `speedRate` → ReferenceError，码速永远停在初始值。

**修复方向**：统一一套 wpm/码分→点长换算函数（含比例、间隔口径），删除手搓系数；低速率用真实 rate；init 完成后回推全量参数或就绪前排队；processor 改采样数计时；修 `speedRate` 未定义；每场训练显式重置单例参数。

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

**修复方向**：码速/正确率/用时一律后端按服务端数据用 `ScoreMath` 重算，前端只回传原始内容；修 pageTime 增量 bug；:855 改用 entity.getScore()；:816-818 改回 r加l扣。

---

## 4. 部分电脑个人岗位数据报训练评不了分

1. **【确认】提交链零失败兜底**：`telexTrain.js:268-283` finishPage 不看 `res.code`、无 `.catch`；:255-266 endTrain 不看 code，**无论成败都跳成绩页**。后端返回 500 时 axios 拦截器（`http/index.js:50-54`）只弹 toast 仍 resolve；203/204/206 走 :33-48 弹强制重登 Modal 后同样 resolve → 成绩页读到未结算数据（score 还是创建时的规则满分）。任何网络抖动/代理/杀毒拦截即触发 → 「部分电脑」。
2. **【确认】后端 finish 未守边界**：
   - 【确认无守卫/触发链为推断】`PostTelexPatTrainService.java:741` `Integer.parseInt(totalSpeed)` 无 null/格式守卫，收到 null/非数字 → NumberFormatException → 500；但当前前端 tick 先 duration++ 再算 speed（`telexTrain.js:85` 先于 :101），「duration=0→speed=NaN→null」窗口不存在，真实触发更可能来自旧页面/其他入口上送 null；
   - :890 `rule.getOther().getNonStandart()`、:919/:924 `rule.getWpm().getR()/getL()` 无 null 守卫 → 规则 JSON 缺字段即 NPE（规则配置差异决定哪些训练必炸）。
3. **【推断】旧运行时 API 缺失**：`telexTrain.js:98` 用 `String.prototype.replaceAll`（需 Chrome 85+），旧 Electron/Chromium 每秒 tick 抛 TypeError → 码速恒 0 → 巨额速率扣分。
4. **【推断】计时节流差异**：setInterval 被节流程度随机型/负载不同 → 同训练不同机器结果不同。

**修复方向**：前端 finishPage/endTrain 校验 `res.code==200` + `.catch`，失败停留并允许重试，上送前 `Number.isFinite` 兜底；后端 totalSpeed 容错解析、规则字段全部 Optional 兜底为 0；结算异常不留「进行中」死局。

---

## 5. 个人岗位数据报倒计时训练没法评分

**倒计时结束会触发结算链**（`telexTrain.js:84-91` tick → `coun===0` → endTest → finishPage → finish → 后端 countScore），但：

1. **【确认】触发条件是精确相等 `coun===0`**（:88），无 `<=0` 兜底：训练中才开倒计时开关或把时长改小到低于已用时长（`Index.vue:22-27` 可随时改），coun 从正直接跳负 → **永不触发**；时长允许 `:min=0`，duration=0 时 coun 从 -1 起步同样永不触发。
2. **【确认】触发后仍可能静默失败**：finishPage 无 `.catch`，网络层 reject 后 endTrain 永不执行；倒计时训练动辄几十分钟，token 失效（203）概率高——203 时拦截器弹强制重登 Modal 后**仍 resolve**（`http/index.js:33-54`），finish 链携带失败响应继续执行并跳成绩页（读到未结算数据）；仅网络错误（:95 reject）才走静默中断。服务端无任何倒计时兜底——客户端不发 finish 就永远不结算。
3. **【确认】长倒计时放大计时漂移**：倒计时与用时都用 setInterval 秒计数而非墙钟。

**修复方向**：`coun===0` 改 `coun<=0` 加防重入；改时长时若已超时立即 endTest；时长 min 改 1 且训练中禁改；长期方案：服务端创建训练时记 startTime+countdownSeconds，finish 幂等兜底。

---

## 6. 个人岗位手键训练无故连码、评分无故报错码

**前端采样链路为主。**

1. **【确认】Vue watcher 事件合并**：按下/弹起经单个 ref `handKeyValue` 传递，watcher 无 `flush:'sync'`；串口粘包时 `WebSerial.record()` 在同一同步循环连续回调多事件 → 同 tick 多次赋值被合并，只处理最后一个 → 间隔（-1）事件丢失、两个点压缩成一个 → 丢码/连码（`student.vue:356-360`、`WebSerial.js:111-138,171-179`）。
2. **【确认】点判定阈值硬编码 120ms**：`useControl.js:66-90` `diff<=120` 判点（原 `patStandard.dot*(1+initFloat/100)` 被注释掉）。开始符号自校准后 dot 基准可能 >120ms（新手点 130ms）→ 所有点被判为划 → 电码全错 → **评分无故报错码**。`diff<=10` 的抖动仍记为点并污染基准校准。
3. **【确认】连码判定依赖前端 setTimeout**：`handKeyTrain.js:275-299` wordTimer=codeGap×1.5（默认 120ms），每次按键 clearTimeout 重排；快速拍发时多字码合并查表 → 查无 → '#' 或错码。codeGap 基准又被丢间隔事件污染，下限钳 60ms（`patStandard.js:41-43`）。
4. **【确认】时间戳取自 JS 处理时刻**而非硬件事件时刻，串口缓冲/粘包延迟直接计入点划时长。
5. **【确认】key_lock 静默丢事件**：按下态再收按下帧直接清空不回调，且 key_lock 不复位（`WebSerial.js:111-137`），事件流永久错位一帧。
6. **后端对照**：后端不重建电码，逐字比对 patKeys 与报底（`MessageComparisonService.java:132-137`），前端产生的任何错字/'#' 都计入 errorNumber；且手键上传的 speed 原样参与最终结算（附录 A H2/H3：码率信客户端、划线扣分错用 dot.max）。

**修复方向**：改 FIFO 队列顺序消费（废弃 ref 当事件队列）；恢复校准基准点阈值、删 120 硬编码；≤10ms 抖动不入电码与校准；串口事件用逐事件打点；key_lock 冲突不静默丢弃；后端按 messageBody+点划时长重算码率。

---

## 7. 手键敲打改错符号和句号显示 # 号

1. **【确认】'#' 是映射表 miss 的兜底字符**：`handKeyTrain.js:549-550` / `details.js:695-696` else 分支 `codeInit[code]!=undefined ? codeInit[code] : '#'`。改错符 001100（当前组）/001011（前一组）不在 `useMorse.js:94-191` 四张映射表任何一张中。
2. **【部分确认】句号残留 '#'（机制已修正）**：句号点组逐组编译时先落 else 立即显示字符（短码表 '00'→'#'）；`details.js:641-661` 凑齐 3 组翻页后只 splice 掉 2 个 patKeys 与 2 个显示字符——但干净路径下第三组在 else 之前即命中翻页分支，实际残留 0，「必残留 1 个」不成立。真实残留路径是**检测失效**：连码粘连成 '0000'/'000000' 或组边界清 cacheKeyCode（details.js 无 '000000' 分支），整体粘连必落 else 产 '#' 且永不清除。组训路径（`handKeyTrain.js:504-512`）翻页分支**完全没有清除逻辑**（原句号展示代码被整段注释）——组训侧残留成立。
3. **【确认】改错特判脆弱**：仅当 code 完整等于 '001100' 或末两个拼接等于 'xx,001011' 才走改错分支；一旦连码粘连或被间隔拆开（'00'+'1100'）→ 特判失效 → '#'。
4. **【确认】拼写错误**：`handKeyTrain.js:516` `cacheKey.value.legnth`（undefined==0 恒 false）→ 改错后下标递减失效，错位产生更多 '#'。

**修复方向**：句号/改错符加入映射或特殊符优先整码匹配；翻页按实际组数（3）清除占位字符；修 legnth 拼写；改错识别不应以「未被连码污染」为前提。

---

## 8. 组网训练乱评分、反应慢，学员结束后教员席很久才反应

### 8.1 「乱评分」

simulation 链路**没有服务端数值评分**——「评分」是前端把学员填报与报底逐字标红对比：

1. **【确认】逐字索引对比无对齐**：`TrainResult.vue:31-32` 按数组索引逐字符比对，漏抄/多抄一组 → 其后全部错位标红；学员答案超长时 `undefined != v` 恒真全标红；学员可自行加页（`FillInResult.vue:34-36`）超出报底页数无标准可比。
2. **【确认机制/竞态推断】报底懒生成竞态**：建房只预生成 min(bwCount,200) 组；第 3 页起 findPage 懒生成，isRandom=1 用无种子 ThreadLocalRandom，两个端并发首拉同一未生成页 → 各自生成不同随机内容都入库 → 报底与播放内容错位 → 大面积误判。
3. **【确认】重复填报叠加污染**：`SimulationRouterRoomContentService.java:215-226` uploadResult 插入前不删旧行；读取用 firstResult 取到最旧答案；count 被重复行虚增 → 分页与对比全乱。
4. **【旁证】综合组网学员端自报分**：`trainingDetails/Index.vue:85-98` 用 `parseFloat==parseFloat` 浮点等值比较自算分，`GroupNetTrainService.java:127-132` 服务端零校验原样入库；且把路由参数 range 当 deviceId 传（:64）可能按错规则打分。

### 8.2 「反应慢/教员席延迟」

教员席状态更新**纯靠 WS 推送、零轮询兜底**，且推送链多处断流（全部确认事实）：

1. 教员席仅 onMounted 拉一次详情（`useBroadTeacher.js:199-200`），WS 收不到推送就永远不更新。
2. **裸 WebSocket 无心跳无重连**：simulation 各页 onclose 只置 null、重连行被注释（`useBroadTeacher.js:111-115`、`useBroadStudent.js:115-118`、`train.js:229-232`）；`Issue.js`/`ListenIn.vue` 更彻底——全篇**没有 onclose 处理器**（原引 :65/:301 实为 `new WebSocket` 行）。带心跳重连的 `SocketConnection` 除联合训练（UnionWs.js）外还有全局通知（Ws.js/PublicSocket.js）在用，但 simulation 各页无一引入。断链后教员席永久失聪。
3. **结束后拒绝建连**：`useBroadTeacher.js:101-103` `status==2` 直接 return 不连 WS——学员的填报恰恰都在结束后才发 → 教员席在该阶段完全没有推送通道。
4. **通知串行依赖 REST 且推送不可靠（房型归因已修正）**：学员先等 REST 上传成功才发 WS 'result'/'over'；REST 慢/失败则教员端无感知（disturb 房的 TOPIC_RESULT 不写 DB userStatus，填报状态完全依赖 REST 先成功）。服务端单点投递缺陷位于**报务教学房**：`WebSocketSimulationService.java:535-551` messageHandleReport 的 TOPIC_RESULT 只发给内存列表第一个 channel==0 成员且 break；**组网干扰房**同主题（:466-472）是向全体 userType==0 广播、无 break——两房型实现不同，修复需分别核实。sendMessage 失败仅记日志无补偿（:682-690）。
5. 学员端收到结束广播后 `location.reload()`（`train.js:296-300`）整页重载。
6. **状态双写漂移**：房间/人员状态既写 DB 又存进程内存 `SimulationGlobal` 三个 static Map；getRoomDetail 在线状态读内存、其余读 DB；服务重启内存全空。

### 8.3 修复方向

- simulation 各页统一切到带心跳/重连的 SocketConnection；去掉 status==2 拒绝建连；或教员席加轮询兜底。
- 服务端 uploadResult 结算成功后**主动**向房间内全体教员推送完整快照，不再依赖学员端事后发 WS；TOPIC_RESULT 去掉 first+break。
- uploadResult 幂等（按 roomId+userId+pageNumber upsert + 唯一索引）；报底建房全量预生成或懒生成加房间级锁。
- 结果对比引入对齐算法（编辑距离/LCS）；数值评分一律服务端结算，禁止客户端自报分。

---

## 9. 组网训练教员席无法查看每个人员训练详细情况

**不是端点缺失**——明细端点存在且前端已接线：`GET /api/simulation/router/findPage`（`SimulationRouterRoomController.java:91-98`），三个教员席查看入口均已接线；历史 roomgId/roomId 契约缺陷已修复。真实原因是可用性限制 + 数据污染：

1. **【确认】训练中禁止查看**：`useBroadTeacher.js:35` `status<2` 直接 return false；且只有 userStatus==1（已上传答案）的学员才有查看入口——**训练中教员看不到任何学员的实时进展**，体感即「无法查看详细情况」。
2. **【确认】详情数据被污染**：重复填报不删旧值 → firstResult 取到最旧答案、existPageNumber 虚高 → 教员看到过期/错页数据；学员自行加页超出报底页数时用 '--' 补齐无法对比。
3. **【推断】查看动作可能触发报底再生**：教员翻看未生成页时懒生成的随机报底与训练时播放的未必同一份，对比基准本身就错。

**修复方向**：开放训练中实时查看（学员逐页上报即推送给教员）；修问题 8 的幂等/懒生成缺陷后详情自然正确。

---

## 10. 个人岗位电子键播报码速不正确、反应较慢

**纯前端问题**（后端只用拍发时间戳算考核速度，与播报无关）。

### 10.1 码速不正确

1. **【确认】报文类型取错标定常量**：`ElectronMorse.js:90-93` 固定用 `dots['short']=4755`，而长码报播报用 `numType='long'`（dots=6995）→ 点长放大 1.47 倍 → **长码报实际比设置慢约 32%**。
2. **【确认】不看 wpmTOmm 开关**，WPM 模式也按码/分公式算，与收报链路口径不一致。
3. **【确认】播报速度不跟随训练设置**：`examTrain.js:49` playSpeed 硬编码默认 80；本该跟随训练速度的 `changeCriterion(trainData.value.speed)` **被注释掉**（:147-148，组训 student.vue:316-317 同样被注释）。
4. **【确认】初始化竞态（叠加因素，非首因）**：`examTrain.js:132` onMounted 立即 changePlaySpeed（硬编码 playSpeed=80），若早于 AudioWorklet 就绪则参数静默丢失，worklet 停在默认值或上一页面残值。但常规使用流（App 启动即由 NipPagePermission init worklet）下竞态不触发，只在刷新/直进训练页时叠加——**「设置不起作用」的直接原因是第 3 条的 playSpeed=80 硬编码 + :147-148 跟随被注释**，本条为加重项。另：preJob `examTrain.js:96` 的 changeCriterion 未被注释、无 playSpeed 硬编码，问题仅限 postJob 与组训链路。

### 10.2 反应慢

1. **【确认】F2 组合键 800ms 判定窗**：`examTrain.js:363-370` 每次按键后 setTimeout(800ms) 等待组合键，字码赋值与播报整体滞后最多 800ms——最大单点。
2. **【确认】音频热路径 console 输出**：`MorseVoiceHighPerformance.js:348-352,361-362` 每次播报 console.time/console.log 整个数组；`examTrain.js:369` 另有 `console.log(3333333333)` 调试残留——Electron 下大量 console 输出造成明显卡顿，按键越频繁越慢。（注：原引「processor.js:56 渲染线程 console.log」经复核不存在——该文件当前及入库历史均无此句，已删除该引证。）
3. **【确认】多级异步链路**：串口/WS → pinia $subscribe → watch → addCode → PubSub 微任务 → convert 全量转码 → postMessage → worklet；另有 `setTimeout(3000/1000)` 延迟订阅（`ElectronMorse.js:102-122`），初始化期按键无声或按默认参数发声。

**修复方向**：changeCriterion 按报文类型取 dots[type] 并尊重 wpmTOmm；恢复并修正跟随训练速度（取消注释+修公式）；参数下发等 worklet ready；F2 判窗缩短可配；删除热路径全部 console。

---

## 11. 个人岗位手键单字拍发点划间隔设置不正确，无法基础训练

1. **【确认】保存-读取往返 off-by-one**（注意：本条两个文件均在 **preJob** 目录）：保存侧 `preJob/.../telegram.js:222-247` rateIntervalMaxMs=dot×4、bigIntervalMaxMs=dot×10；读取侧 `preJob/.../HandKeyTrain.vue:770-772` 反推比例时 interval/gap **多减了 1**（parseInt(÷−1)=3 和 9，而 line 不减）→ 任何输入触发 handlePatDeployData 即按错误比例重算（`useDetails.js:581-588`）→ 设置界面显示的比例本身就错，生效间隔与所设不符。
2. **【确认】个人岗位（postJob）手键训练完全不读取设置**：postJob `HandKeyTrain.vue` 全篇无 rateDotMaxMs/getSetting 调用，判定阈值恒为硬编码 dot=80/line=240/codeGap=80/wordGap=240/groupGap=400（`useControl.js:7-13`）→ **用户配置的间隔在该入口永不生效**。
3. **【确认】基础训练（HandKeyBasicTrain）链路断裂**：设置经 `/api/telegramTrain/saveSetting` 保存（后端 `TelegramTrainService.java:373-381` deleteAll+save 透传无校验），但训练页 getBasicSetting 只用于图表分级（`basicTrain.js:38-70,133-141`），点/划/间隔判定阈值硬编码从不应用设置。
4. **【确认+推断】基础训练首键即崩**：`basicTrain.js:140-142` 无 type===0 行时 `[0].value` 对 undefined 取属性抛 TypeError；设置行被全量 prune 或新库无记录时可达（推断，需 UI 复核）；`changeBasicValue` 还会生成 `'<undefined'` 入库。

**修复方向**：统一比例换算使保存/读取互逆，以库内四段毫秒值为权威；postJob 训练页接入与 preJob 相同的设置加载；基础训练判定阈值改读 getBasicSetting；basicTrain 加空兜底；后端 saveSetting 加合法性校验。

---

## 12. 理论学习无法导入题库，理论测试系统不会操作

**确定性根因：客户运行的是修复前发布版。**

题库导入修复（后端 `1c40aae`、前端 `9596c6c`，均 2026-09-08/09）**不含在任何 release tag 中**（`git tag --contains` 两者均为空，全仓仅 v1.0.0/v1.1.0 两 tag）。两个修复提交**已在 origin/main**；main 领先的 4 个未推送提交纯为 docs/chore——缺的是 release 而非推送。旧版三重缺陷：

1. **【确认】发布版后端导入端点是空壳**：v1.0.0 `TheoryKnowledgeQuestionController.java:80-83` upLoadFile 空实现直接返回成功；v1.1.0 连 upLoadFile 空壳都已删除，saveBatch/exportTemplate 路由不存在。当前 main 的导入走 saveBatch——upLoadFile 端点是**删除**而非实现，属契约变更，发布说明需注明。
2. **【确认死链 URL / 404 为推断】模板下载是死链**：旧前端模板指向 `/api/file/getFile/006/题库-模板.docx`，**本仓后端任何版本均无此路由**；但该 URL 指向仓外独立文件服务（index.html 的 fileUrl 与 httpUrl 分离），客户侧是否 404 取决于该服务部署，无法在本仓静态证实（同 `docs/reviews/archive/2026-09-08-joint-theory-file.md:119`）。「拿不到模板 → 不会操作」的因果链成立，部署侧验证后收口。
3. **【确认】旧前端 docx 解析脆弱 + 假成功**：按硬编码版式解析，稍有不符即报错中断；逐行 fire-and-forget 上传后 `setTimeout(2000)` **无条件提示成功**，全失败也提示成功。

**当前 main 状态**：导入链路已修复且前后端契约一致（saveBatch 整批事务 + 逐行校验整批回滚；模板由前端按后端列规格生成 xlsx；仅 code===200 才提示成功），有 6 例测试覆盖。残留易用性问题：模板表头是英文字段名（中文 title 被前端丢弃）、options 列要求手填 JSON、levelId 需手抄题库 ID。

**修复方向**：
- **发布（根因）**：从当前 main 出新 release，前后端两个修复提交必须同时进交付物；发布前先推送 main。
- 模板 UX：用中文表头 + 示例行；levelId 由当前选中题库自动预填。
- 发布后用真实 docx/xlsx 走「导出模板→填表→导入」闭环验证。

---

## 13. 优先级与修复批次建议

| 批次 | 内容 | 覆盖问题 | 理由 |
|---|---|---|---|
| P0 发布 | 从当前 main 出新 release（两个修复提交已在 origin/main，缺的只是 tag/构建），前后端同步发布 | 12（直接解决），并缓解所有「客户跑的是旧版」类问题 | 不改代码即见效 |
| P1 评分可信 | 评分收口服务端重算；finishPage/endTrain 失败兜底；倒计时 `coun<=0`；parseInt/NPE 边界守卫；手键 dash.max 错用 dot.max；电子键提交锁释放 | 3、4、5（附录 A H2/H3/M1/M3） | 数据完整性，纯后端+少量前端 |
| P1 手键采样 | FIFO 事件队列（含电子键多码帧，附录 A H1）；恢复校准阈值；修 legnth；句号/改错符映射与清除 | 6、7 | 高频训练路径，改动集中在前端 |
| P2 码速口径 | 统一换算函数；修速度跟随注释；worklet 参数就绪前排队；删热路径 console | 2、10 | 需音频回归验证 |
| P2 组网 | WS 统一切 SocketConnection；服务端主动推送教员；uploadResult 幂等；报底全量预生成 | 8、9 | 涉及契约，需前后端同步 |
| P2 点划间隔 | 比例换算互逆；postJob 接入设置加载；基础训练阈值接设置 | 11 | 前端为主 |
| P1 越权收口 | 手键 upload/finish/reset 与电子键 finish 一律从 token 推导用户（附录 A H4/H5） | 非客户报障，评审确认 HIGH | 触及红线 6，随 P1 同步修 |
| P3 会话模型 | ~~user_session 会话表~~（互踢为设计行为，已撤销）；deviceId 稳定化；授权到期预警；203/204/206 掉线提示文案区分 | 1 | 契约变更大，单独排期 |

## 14. 验证要求（修复时执行）

- 评分类：新增/更新「原始拍发 → 最终 score」端到端结算测试（现有 `ScoringConsistencyTest` 只覆盖 `ScoreMath.wpmScore`）；篡改 speed/重复 finish/上传失败重试用例。
- 音频类：用采样时钟校验实际发声节拍（误差 <2%），覆盖 WPM 与码/分两种模式、四种报文类型。
- 组网类：双端并发首拉未生成页、教员断链重连、学员结束后教员无刷新可见。
- 发布前：`cd backend && ./mvnw -B clean verify` 全绿；跨栈契约改动核对前端调用面（红线 5）。

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

**H5 电子键 finish 仍信任 body userId**【HIGH】：上传接口已改为 token 推导（`GeneralKeyPatService.java:429-453`），但 finish 仍 `findByUserIdAndTrainId(dto.getUserId(), ...)`（`GeneralKeyPatController.java:99-104`、`GeneralKeyPatService.java:456-487`）。**修复**：finish 从 token 推导，删除/忽略 body userId。

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
10. 清理无活跃调用者的 `teacherBack.js` 等死代码（→ P3）。

在 1～5 完成并有端到端回归证据前，手键/电子键拍发与评分不能标记为已验收。

### A.7 已确认正常的部分（避免重复排查）

- 手键 API 均指向 `generalTickerPatTrain`；电子键 API 均指向 `generalKeyPat`；
- 活跃 `teacher.js` 三个训练域无跨域调用；电子键 reset 已用 `generalKeyPat/reset`；
- 手键/电子键 WS 路径分别对应同名后端 endpoint；
- 成绩页 score/deductInfo/accuracy 主要从后端详情读取；前端不直接提交最终 score 字段；
- `teacherBack.js` 等死代码有错误 import/串域引用，但无活跃调用者。
