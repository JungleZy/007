# 核心训练功能专项复核报告（2026-09-14）

## 1. 结论

本轮 agent team 对当前 `main`（`46cf2a6`）的手键拍发、电子键拍发、收报/报话训练及其 Electron/Vue/REST/WebSocket 闭环进行了源码、前端调用面和运行态复核。上一轮 `2026-09-12` 复核确认的修复仍然有效，但本轮发现一组未被上一轮覆盖的活跃路径缺陷。最高风险集中在综合组训读面和经典收报/报话训练的对象授权；这些端点都存在真实前端消费者或可由已登录客户端直接调用。

**结论：REQUEST CHANGES。当前不能宣称核心训练功能已闭环。**

## 2. 本轮范围与证据

- 后端：`GeneralTickerPat*`（综合组训手键）、`GeneralKeyPat*`（综合组训电子键）、`TickerTape*`/`PostTickerTape*`（收报）、`TelegramTrain`、`TelexPatTrain`、`GeneralTelexPat*`、个人/岗位电子键路径。
- 前端：`common/api/handkeyZuXun.js`、`electronKeyZuXun.js`、`ReceiveApi.js`、`TelegramApi.js` 及其训练页、`SocketConnection`/`PublicSocket`。
- 运行验证：使用 `backend/target/quarkus-app/quarkus-run.jar`，以 `java -Dquarkus.profile=dev -Dquarkus.http.port=18001 -jar ...` 启动，连接本地 `mysql-project006/project006`；`GET /q/openapi` 返回 HTTP 200；缺 token 的受保护 REST 端点返回 HTTP 200 + `code:203`；`admin/123456a` 登录成功（token 仅作本地临时凭据，不写入文档）。直接向两个 GET 训练启动接口发送 `{"trainId":999999,"attempt":0}` JSON body 返回 `{"code":202,"message":"训练ID不能为空"}`，显式 query 返回 `{"code":202,"message":"未查询到训练"}`。源码复核同时确认共享 `axios.js` 会把 GET 的 `data` 映射成 query，因此当前前端通常可工作；问题是两个模块依赖隐式转换，而数据报模块已显式拼 query，形成脆弱且未被契约测试锁定的分叉。该记录是 wire smoke，不代表真实训练成功闭环。
- 既有证据：`docs/reviews/2026-09-12-current-state-review.md`、`backend/src/test/java/com/nip/service/GeneralCaptureContractTest.java`、`backend/src/test/java/com/nip/ws/WebSocketHandshakeAuthorizationTest.java`。

## 3. 当前确认正确的部分

1. 综合组训手键/电子键的写入、采集时间轴、attempt 栅栏、重叠区间拒绝、服务端重算速率/成绩、行锁、幂等结算、结算通知（提交成功后发送）和 WebSocket 握手身份覆盖已有强回归；岗位手键和岗位电子键主流程的 owner 约束已落地。新建训练通知的事务时序仍是本轮开放缺陷。
2. 综合数据报 `GeneralTelexPatService` 的读写授权、服务端时钟、终态和恢复逻辑是可复用的参考实现。
3. `TickerTapeTrainSetting`、`PostTickerTapeTrainSetting`、`ReceiveKeyPoints` 写入已管理员保护；事务内明确 `setRollbackOnly()` 的路径未发现新的吞异常数据丢失问题。
4. 本轮未重新打开上一轮已闭合的 token、MyISAM、WS 单例会话态、203/204/206 文案和 207/208 信封契约。

## 4. 开放发现

### P1-H1：综合组训手键/电子键读面 IDOR

- `backend/src/main/java/com/nip/controller/general/GeneralTickerPatController.java:69-74,102-107` 仅有类级 `@JWT`，方法/服务未使用 token 推导 actor，缺成员判定；`GeneralTickerPatTrainController.java:34-67` 的单房间/全房间在线名单同样仅受类级 `@JWT`，无目标授权。
- `backend/src/main/java/com/nip/controller/general/GeneralKeyPatController.java:58-143` 的 `findPage/detail/patDetail/getPatValue/statistics/getScore` 与 `GeneralKeyPatTrainController.java:28-41` 的在线名单仅受类级 `@JWT`，方法/服务未使用 token 推导 actor，缺成员判定。
- 可枚举的 Integer `trainId` 或已泄露 id 可读取跨班训练。响应包含姓名、头像、成绩、扣分明细、速率、评分规则、拍发内容；电子键 `getScore` 还返回全局最近学员成绩。
- 对照：`GeneralTelexPatController` 所有读端点已调用 `requireMember/requireReadableTarget`。这是明确的授权分叉，不是产品公开读面的推断。
- 修复等级：P1。所有 train-scoped 读使用成员/创建者/组训人员/管理员判定；per-user 读使用目标用户判定；孤儿 `getScore`/全房间名单删除或改为明确 train-scope 授权。拒绝必须 `207`。

### P1-H2：岗位收报生命周期、上传和读面缺少对象授权

`PostTickerTapeTrainController.java:62-105` 仅有类级 `@JWT`，除 delete 外方法未传 token；`PostTickerTapeTrainService.java:149,174,180,211-290,292` 的 `getById/begin/finish/uploadResult/findPage/reset` 按 id 操作而缺 owner。跨用户可读取正确答案（`PostTickerTapeTrainPageVO.key`）、写结果/完成/重置；重复 `uploadResult` 可追加值行并重复结算，上传路径还缺终态检查。前端 `ReceiveApi.js` 正在消费这些接口。

### P1-H3：拍发 `TelegramTrain` 生命周期、日志和结果全信任客户端

`TelegramTrainController.java:120-148,169-173` 仅有类级 `@JWT`；控制、`getTelegramTrainLog` 未传 token。`TelegramTrainService.java:193-257,394-396` 按 body 中 train id 定位，直接采信 errorNumber/accuracy/speed/sustainTime/totalKnockNumber 并覆盖报底；日志按传入 id 直接返回。`getById/getFloorContentByFloorId/saveFloorContent` 也缺 owner。已登录用户可结束他人训练、读取他人日志、伪造成绩并覆盖报底。

### P1-H4：经典电传 `TelexPatTrain` 现有 id 接管

`TelexPatTrainService.java:64-106` 对 DTO 非空 id 直接 merge，未先验证旧记录属主；`findTexPatTrainById:119` 也无 owner。`BaseRepository.save` 的 merge 使该路径可重写他人记录并把 `createUserId` 改成调用者。DTO 同时携带客户端成绩字段。

### P1-H5：经典收报 `TickerTapeTrain` 生命周期缺少对象授权、用时可伪造

`TickerTapeTrainController.java:60-97` 仅有类级 `@JWT`，`TickerTapeTrainService.java:110-151` 的读/开始/暂停/继续/结束缺 owner；结束直接保存客户端 `validTime/mark/schedule` 并更新训练属主统计。`getById` 可读他人报文。

### P1-H6：综合组训读面外的实际功能缺陷

1. 电子键 `TelegraphKeyPatSyntheticalService.save:99-108` 遇迁移前 `status=PAUSE, protocol_version=NULL` 的历史记录时返回 208，导致无法新建训练且无法处理旧记录；迁移 `2026-09-12-04-comprehensive-key-authority.sql` 未回填。
2. 电子键单字 `TelegraphKeyPatTrainService.save:64-70` 更新分支不判 owner 且采信 `totalTime/totalNum/totalError`。
3. 手键 `GeneralTickerPatService.statisticsScoreAndDotLineGapRate:919-925` 在 reset 后 `score=NULL` 时 NPE，外部表现为业务 500；`countScore:1029` 将所有实际点划/码/字/组用时传成 0，报表平均用时恒为 0。
4. 手键 add 在事务提交前推送 NEW_TRAIN，后续回滚会产生幽灵通知；`GeneralTickerSocketController.getByTrainIdAndUserId` 的 `@RequestPass` 无消费者且仍缺目标授权。

### P2-H7：全局配置写入遗漏管理员门禁

`TickerTapeTrainStageSettingController.add:33-38` 与 `TelegramTrainController.saveSetting:181-185` 可由任意已登录用户改全局配置；与已有两个受保护配置控制器不一致。应加 `@RequireAdmin`，非管理员 `207`。

### P2-H8：手键/电子键 GET 参数依赖隐式 wrapper 转换

`bw-frontend/frontend/src/common/api/handkeyZuXun.js:50-51` 与 `electronKeyZuXun.js:71-72` 对 GET `startTrain` 把 `{trainId,attempt}` 放在 `data`；后端 `GeneralTickerPatController`/`GeneralKeyPatController` 使用 `@RestQuery`。当前 `bw-frontend/frontend/src/common/http/axios.js:20-24` 会将 GET `data` 转成 `params`，所以源码 happy path 可工作；但 `datagramZuXun.js:77-88` 已采用 URL 显式 query，两个核心模块的隐式依赖容易被 wrapper 替换或直连调用破坏。直接 curl 发送 JSON body 的对照实验返回 `训练ID不能为空`，证明后端不接受 GET body。建议统一显式 query，并添加前端请求契约测试。

### P3：诊断/健壮性问题

- `TickerTapeTrainController.saveBaseTrain:102-109` 吞异常，隐藏根因；应交给 mapper。
- `TelexPatTrainStatisticalService.statistical:75-77` 对客户端数值直接 `Integer.valueOf`，坏输入变 500。
- 综合手键/电子键部分无用/脆弱 controller 实例化与空 bean 应在安全修复后清理，不得作为授权替代。

当前没有覆盖：综合手键/电子键读面外人 207、岗位收报所有读写 owner、TelegramTrain 跨人生命周期/日志读取、Telex 现有 id 接管、TickerTape owner、两个全局配置管理员门禁、电子键 legacy PAUSE 新建、单字电子键更新 owner、手键 reset 后统计、统计平均用时、GET query 统一契约。应以行为断言补齐，而非断言注解或实现文本。

## 6. 非目标与边界

本轮不改变 203/204/206 值文案，不把 HTTP 状态码改为业务状态，不引入兼容别名，不把前端软门控当作安全边界。真实硬件串口、Windows/ARM64 native、可信证书、客户现场仍属于外部验收；本轮功能缺陷均可在仓内修复验证。

先修 P1-H1～H5 和 P2-H7，再修 P1-H6、P2-H8 与 P3；每个独立任务单独提交，受影响后端测试和前端契约测试通过后再运行完整 `clean verify`、前端 test/build，推送后观察全部 GitHub Actions job。
