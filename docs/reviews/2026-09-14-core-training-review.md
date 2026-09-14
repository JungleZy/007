# 核心训练功能专项复核报告（2026-09-14）

本轮 agent team 完成了手键拍发、电子键拍发、收报/报话、跨栈和非核心边界复核，并完成文档独立 review、代码修复及最终验证。核心训练整改已闭合；历史发现保留在 §4 作为取证，不再代表当前未修复状态。

**结论：仓内整改通过，外部验收边界仍按 §6 执行。**

最终证据：后端 `JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify` 为 442 tests、0 failures、0 errors、0 skipped；前端 `npm run test` 为 31/31，`npm run build` 成功；实体 schema snapshot 通过；current/base 双快照迁移 rehearsal 通过全部断言（106 张表、0 张 MyISAM、schema 差分为空）。

## 2. 本轮范围与证据

- 后端：`GeneralTickerPat*`（综合组训手键）、`GeneralKeyPat*`（综合组训电子键）、`TickerTape*`/`PostTickerTape*`（收报）、`TelegramTrain`、`TelexPatTrain`、`GeneralTelexPat*`、个人/岗位电子键路径。
- 前端：`common/api/handkeyZuXun.js`、`electronKeyZuXun.js`、`ReceiveApi.js`、`TelegramApi.js` 及其训练页、`SocketConnection`/`PublicSocket`。
- 运行验证：使用 `backend/target/quarkus-app/quarkus-run.jar`，以 `java -Dquarkus.profile=dev -Dquarkus.http.port=18001 -jar ...` 启动，连接本地 `mysql-project006/project006`；`GET /q/openapi` 返回 HTTP 200；缺 token 的受保护 REST 端点返回 HTTP 200 + `code:203`；`admin/123456a` 登录成功（token 仅作本地临时凭据，不写入文档）。直接向两个 GET 训练启动接口发送 `{"trainId":999999,"attempt":0}` JSON body 返回 `{"code":202,"message":"训练ID不能为空"}`，显式 query 返回 `{"code":202,"message":"未查询到训练"}`。源码复核同时确认共享 `axios.js` 会把 GET 的 `data` 映射成 query，因此当前前端通常可工作；问题是两个模块依赖隐式转换，而数据报模块已显式拼 query，形成脆弱且未被契约测试锁定的分叉。该记录是 wire smoke，不代表真实训练成功闭环。
- 既有证据：`docs/reviews/2026-09-12-current-state-review.md`、`backend/src/test/java/com/nip/service/GeneralCaptureContractTest.java`、`backend/src/test/java/com/nip/ws/WebSocketHandshakeAuthorizationTest.java`。

## 3. 当前确认正确的部分

1. 综合组训手键/电子键的写入、采集时间轴、attempt 栅栏、重叠区间拒绝、服务端重算速率/成绩、行锁、幂等结算、授权读面和提交成功后的通知均有回归覆盖；WebSocket 握手身份覆盖路径身份。
2. 岗位收报、经典收报、经典 Telegram、经典 Telex 的 owner 生命周期、终态语义、服务端活动时钟和客户端聚合字段拒信已落地；基础/科式收报使用 type 21/22 专用统计桶，暂停不计时。
3. 经典 Telegram 的修改报底按内容归属校验，结束按数据库完整原始内容重算；经典 Telex 的扩展由服务端按冻结类型生成，客户端不能改源内容；小数毫秒拍发事件按数值语义校验。
4. 设备主数据管理员门禁、设备训练/汉字录入属主判定、GET query 显式契约、桌面 activate/linkPort 成功判定均已同步；207/208 错误不会被相关页面伪装成成功。
5. `TickerTapeTrainSetting`、`PostTickerTapeTrainSetting`、`ReceiveKeyPoints` 写入仍受管理员保护；事务异常路径没有新增吞异常提交。
6. 本轮未重新打开上一轮已闭合的 token、MyISAM、WS 单例会话态、203/204/206 文案和 207/208 信封契约。

## 4. 历史发现及关闭证据

### P1：训练读写授权与 IDOR

综合手键/电子键、岗位收报、TickerTape、Telegram、Telex 以及设备/个人训练路径现在均从 token 推导 actor，再执行 owner、成员、组训人员或管理员判定；新增/既有 id 路径不会用客户端 id 接管他人记录。授权拒绝统一由现有 mapper 返回 207。相关边界回归包含 `TrainOwnershipAuthorizationTest`、`GeneralCaptureContractTest` 及管理员授权套件。

### P1：客户端结果和时钟伪造

综合域、岗位域和经典域均由服务端采集时间轴/活动时钟重算；Telegram/Telex 的原始报文和提交报底经过归属与结构校验。经典旧行保持 `protocol_version=0`，迁移 rehearsal 已断言 current/base 历史行不被重标为新采集协议；新建行由服务端初始化为协议 1。

### P1/P2：状态、会话和前端 wire

基础/科式收报创建服务端 session id，开始前完成音频准备，暂停/结束等待服务端响应并处理 207/208/网络错误；Koch 恢复查询先于创建新 session。手键/电子键 startTrain 使用显式 query；Telex 正常扩展沿用服务端冻结源。前端完整测试 31/31，生产构建成功。

### P3：迁移与非核心边界

三项经典时钟迁移已加入 rehearsal 字典序清单；双快照演练对 106 张表、引擎、实体 schema、历史协议标记和重复执行均通过。`saveBaseTrain` 吞异常、设备管理员门禁、设备训练/汉字录入 owner caller 和 Electron 两项错误已修复并通过对应构建/语法检查。

## 5. 当前验证边界

- 已执行：442/102 后端全量回归、31/31 前端测试、前端生产构建、实体 schema snapshot、current/base 双快照迁移 rehearsal、变更 JavaScript 语法检查。
- 未由本地证据覆盖：GitHub Actions 本次提交后的 runner 结果、Windows/ARM64 native、真实串口硬件、可信证书链、桌面 native 后端凭据注入、客户现场和发布负责人签收。
- 真实浏览器/fast-jar smoke 的历史证据仍见 `docs/reviews/2026-09-12-current-state-review.md`；本轮前端构建与后端 full verify 不替代上述外部验收。

## 6. 交付结论

仓内代码、迁移、测试和文档整改已完成，下一步仅剩独立提交、推送及观察 GitHub Actions；任何 runner 失败必须以实际日志为准修复，不提前宣称 CI 通过。
