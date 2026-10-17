# generalKeyPat（电子键组训）Java ↔ Rust 对照

结论先行：Rust 能跑通主流程，但有 4 处会让真实前端出错或分数不同：`findAll` 缺 `userInfoList`（list.js:151 直接 `.forEach` 崩）、`patDetail` 缺 `pageAnalyzeVOS`（trainScore.js:80 `.map` 崩）、少页扣分与正确率算法和 Java 不一致、成绩通知（201）多推到组训房间 socket。另有一组 202↔208/207 码值偏差和一个跨训练死锁窗口（非 PK 加锁）。

## a. Java 流程

**表**（project006.sql + 迁移 2026-09-11-04）：`general_key_pat`（PK + `idx_general_key_pat_closing(status,end_time)`）、`general_key_pat_user`/`_page`/`_user_value`/`_user_value_resolver`/`_train_more` **都只有主键**，无 train_id/user_id 索引。

**状态机**（GeneralKeyPatService.java）：0 未开始 →(updateTrainStatus 1，:614-627，同时给 role=0 写 captureStartedAt)→ 1 进行中 →(status 2，:628-634)→ 3 收尾（60 s 宽限）→ 全员 isFinish=1 时 2。宽限过期由 `GeneralSettlementRecovery`（5 s，:414-419）→ `settleExpired`（:648）全员结算。学员 isFinish：0→startTrain 置 2（:1234）→finish 置 1；reset 递增 attempt（:1245）。

**角色**：建训人自动插 role=1 行（:200-204），学员 role=0。写权限 = 建训人 ∪ role=1 ∪ 管理员（TrainWriteAccess），读 = 成员 ∪ 管理/建训人（requireMember :1303）。

**REST**（`/api/generalKeyPat`，GeneralKeyPatController.java）及前端请求体：
| 端点 | 前端调用 | 请求体 |
|---|---|---|
| add | list.js:124 | `{trainType,title,messageType,isRandom(0/1),totalNumber,isAverage,ruleId,userId[],isCable,cableId,startPage}` |
| findAll | list.js:146 | `{page,rows:10}` |
| detail | teacher.js:73 / student.vue:238 | `{trainId}`（学员多带 `uid`，忽略） |
| updateTrainStatus | teacher.js:310 | `{trainId,status:1|2}` |
| getPage | handKeyTrain.js:104 / trainScore.js:119 | `{trainId,userId,pageNumber}` |
| startTrain (GET) | handKeyTrain.js:238 | `?trainId&attempt` |
| uploadResult | handKeyTrain.js:455-462 | `{trainId,pageNumber,pageValue:[{id,trainId,pageNumber,sort,key,value,time(JSON 文本)}],attempt,captureIntervals:[{startedMs,endedMs}]}` |
| finish / reset | handKeyTrain.js:489 / :569 | `{trainId,attempt}` |
| patDetail | trainScore.js:50 | `{trainId,userId}` |
| statistics | teacher.js:202 | `{trainId}` |
| delete (GET) | list.js:257 | `?trainId` |
| findPage/getPatValue/getTrainInfo[Batch]/importTrainInfo[Batch] | 无前端调用 | import 为 `@RequireAdmin` |
`GET /api/GeneralKeyPatTrain/getOneline/{trainId}`：路径参数被忽略，实际读 `@RestQuery trainId`（GeneralKeyPatTrainController.java:229）。

**WS** `/generalKeyPatTrain/{uid}/{trainId}`（WebSocketGeneralKeyPatService.java）：query token+deviceId 鉴权，非成员 → `{"code":-1,"data":"训练数据异常"}` 后关闭（:102-109）。房间 = 1 个 groupUser + joinUser 列表；新连接替换同 uid 旧连接，新 role=1 连接替换任何旧组训人（:123-136）。入房向对方角色推 `{"code":1,"sendName":"","receiveName":"","data":"{\"topic\":\"online\",\"id\":uid}"}`；离开推 `offline`（:226-245）。入站 topic：`begin/end` 仅 role=1，原样加 `id` 转发给全部学员；`ready/pat/finish` 仅学员，转发给组训人，`ready` 置状态 2（:185-207）。错误帧：`消息格式错误`/`未知主题`/`无权限操作`/`房间不存在`。心跳 `__nip_heartbeat_ping__`→`pong`。

**通知**：add 在事务内逐个学员 `WebSocketService.sendInfo` 推 `{"code":200,"map":{"type":"key","id",title}}`（:194，只走 `/websocket/{sid}`）；结算后 `GeneralPatResultNotifier` 在 AFTER_SUCCESS 给 role=1 推 `{"code":201,"map":{type,userId,trainId}}`（:380-386，同样只走经典 socket）。

**结算** countScore（:850-1018）：逐采集页 `KeyPatUtils.handle` 对齐；缺页附加扣分（:897-925）；速率 = 字符×60000/(ms×4) HALF_UP；正确率 `ScoreMath.accuracy`（先算 2 位比值再×100，如 "92.00"）；`deductInfo` 22 个键。

## b. Rust 映射
- 路由：main.rs:899-921（多出 `/getOnline` :918，Java 无此端点）、:954、WS :1278/:1292。
- general_train.rs：add :446、list :524、detail :540、set_status :664、delete :702、statistic :741、members(patDetail) :1014、findPage→page_response(personal=false) :1522、upload :1053、export/import :1215-1475、getPage :1522、getPatValue :1596、frozen_rule :1748、start/reset `participant_action` :1790、capture_bound :1837、settle_key :3418、finish_participant :3761、recovery :3871-3966。
- general_aux.rs:121 `key_online`（照抄 Java 的 query 读取）。
- ws.rs：route_spec :1228、prepare/general_role :1449/:1611、register :304、disconnect :612、general_message :2336、notify_general_new_train :1106、notify_general_result :1143、close_general_room :1075。

## c. 疑似行为差异（journey 步骤名在括号内）
1. **findAll 返回原始行**：Rust 无 `userInfoList`、未清空 `ruleContent`、多 `upId`（general_train.rs:536）；Java 每项带成员列表（Service :372-379）。前端 list.js:151 会抛 TypeError（`student-list`）。
2. **patDetail 形态**：Rust 缺 `pageAnalyzeVOS`/`userStatus`/`patValue`，多出原始行字段（id、attempt、capturePages…），`userName` 被填充（Java 为 null），cable 训练 `totalNumber` 未改为页数（Java :1077-1080），非 cable `pageCount` 永远有值（:1034），`content` 的 id/trainId 是数字且带 userId（Java 为字符串 VO，:1066）（`student-pat-detail-*`）。trainScore.js:80 会崩。
3. **detail.pageAnalyzeVOS 恒为 []**（:170），Java 按页算 `{pageNumber,patNumber,totalTime}`（:431,:445-465）；成员排序 `role DESC,id`（:590），Java 无 ORDER BY（实际按主键）。
4. **缺页扣分**：Java 的缺页循环与公式**重复累加** lackLine（:897-925）；Rust 只算公式（:3500-3516）。12 组 cable 空交卷：Java lackLine=1/得分 85，Rust 0/86；250 组空交卷：Java 49/-199，Rust 24/-174（`student2-finish-in-grace-no-pages`、`student-finish-no-pages`、`statistics-teacher`）。
5. **正确率舍入**：Rust 保留百分数 2 位（:3531，11/12→"91.67"），Java 先比值 2 位再×100→"92.00"（ScoreMath.java:41-49，注释写明这是既定落库口径）。
6. **码值偏差**（Java IllegalStateException→202，Rust 用 208/207）：未开始 finish（Java :570 202 / Rust :3825 208）；收尾期或结束后 reset（Java :1242 202 / Rust :1808 208）；结束后 startTrain（Java captureBound :1336 先于 isFinish 判断→202 / Rust :1826 208）；重开已结束训练（Java :616 202 / Rust :682 208）；收尾期过后上传（Java :1336 202 / Rust :1857 208）；组训人 finish/upload（Java student() :1274 202 / Rust :3777、:1063 207）；非成员读自己页（Java readableMember :1300 202 / Rust require_member 207）；非成员 getPatValue 自己（Java :1311 200 [] / Rust 207）。
7. **结果通知扇出**：Rust 给接收人**所有** socket 推（ws.rs:1166-1174、:1129-1137），组训人房间 socket 会多收 201，学员在别的组训房间时也会多收 200；Java 只推 `/websocket`（`teacher-room-after-result`、`student-room-socket-after-y`）。
8. **结算时补页**：Rust finish/recovery 先 `ensure_settlement_pages` 把全部缺页生成入库（:3788、:3899）；Java 不生成。结果是 `findPage` 第 3 页 Java [] / Rust 50 行、`patDetail.existPage` [1,2] vs [1,2,3]（`find-page-3-after-finish`）。
9. **统计** `userTendencyVO[*].status`：Rust=isFinish（:769），Java 键域没设 statusExtractor → null。
10. **findPage/getPatValue 字段**：Rust 多 `trainId`（findPage）和 `id`（getPatValue）。
11. **导出/导入**：Java 页 id 类型是 Integer，UUID 转换失败 → 导出为 null，Rust 导出 UUID；Rust 导入不写 `is_cable` → NULL，Java 取实体默认 0（`detail-imported`）。
12. **在线名单顺序**：Java 学员按入房顺序、组训人放最后（Controller :233-234），Rust 按入房顺序，组训人先入房时排第一（`roster-teacher`）。
13. **WS**：空白 topic Java 报"消息格式错误"，Rust 报"未知主题"（ws.rs:2351-2364）；Rust 允许同一房间存在多个 role=1 连接（ws.rs:320-345），Java 只留一个组训人。
14. **文案差异**（只影响 TEXT_ONLY 类）：add 校验文案、页码文案、`训练状态不合法` 出现顺序。

## d. 锁与事务
- **非主键扫描加锁**（子表只有 PK）：`locked_member … FOR UPDATE`（:3285，start/reset/finish）、`UPDATE general_key_pat_user … WHERE train_id AND user_id`（:1127 upload、:1817/:1831、:3538、:686 UPDATE…JOIN）、`DELETE … general_key_pat_user_value WHERE train_id…`（:1116、:1818）、recovery `FOR UPDATE`（:3901）、delete 5 张子表（:713-733）。在 RR 隔离级别下，这些语句会给**整表**加 next-key 锁：任一学员的 start/finish 都会挡住所有训练的同类操作，也会挡住 `add` 往成员表插行。Java 用 Hibernate 按 PK 更新成员行，只有批量 delete 会扫表。
- **锁序反转 → 跨训练死锁**：upload(A) 先锁值表再锁成员表（:1116→:1127）；reset(B) 先锁成员表再锁值表（:3285→:1818）。delete(C) 持页表锁、等成员表；finish(B) 持成员表锁、要补页插页表（:3788）。被 InnoDB 选为牺牲者的一方会映射成 HTTP 500（:97-100）。
- **读接口拿 X 锁**：findPage（:1536）、export（:1220；批量导出在单个事务里锁住全部训练），Java 都不加锁。
- **import** `SELECT … t_user WHERE user_account=? FOR UPDATE`（:1336）：t_user 只有 PK，会锁全表，挡住登录写 token。
- **补页放大**：finish/recovery 在持锁事务里按 `totalNumber/100` 生成全部缺页（逐行 INSERT），totalNumber 只校验 ≤ i32::MAX（:453），可被利用做 DoS。
- **删除/入房原子性**：Java 用 `RoomLifecycleLocks.generalKeyRoom` 让 openLocked 与 delete 互斥（Service :289-306，WS :84-90）。Rust 中 prepare 读角色（ws.rs:1449）与 register（:1279）不在同一临界区，delete 提交后才 `close_general_room`（:736）。中间插入的 open 会留下孤儿连接。
- 未发现事务内 `let _ =` 吞错、事务内重入连接池或 WS 主循环内联等锁：业务消息在独立 worker 中处理（ws.rs:1306-1316），通知都在 commit 之后发（:520、:3858）。

## e. Rust 不应照搬的 Java 缺陷
- add 在事务内、提交前推 200（:194）；随后 cable 楼层不足（:250-255）或 `title=null` 让 `Map.of` NPE（:195-198）回滚，学员仍收到不存在训练的通知。Rust 放在 commit 之后（:520），正确。
- userId 缺失 NPE→500（:184）；学员 score 为 null 时统计 NPE（PatTrainStatisticsBuilder.calculateScoreDistribution）。Rust 已防住。
- 缺页 lackLine 重复累加（:897-925）。Rust 没照搬，但这改变了分数，需要在 registry 里明确接受，或改成与 Java 一致，二选一。
- getOneline 路径参数无效（Controller :229）；导出 DTO 缺 `isCable/trainType`（GeneralKeyPatSyncDto），cable 训练导入后变成随机报。Rust 两处都照搬了，后者还把 is_cable 写成 NULL。
- WS onClose 不加 RoomLifecycleLocks（:226）。readableMember 没有管理员分支（:1291-1301），但 patDetail/getPatValue 允许管理员（:1309-1314），口径不一致。
- countScore 写入的 resolver 行没有任何读取方（patDetail :1053 只算不用）。Rust 不写，可接受。

## Journeys
见 `generalKeyPat-journeys.json`，共 3 条（91/37/27 步）：
- cable 双学员全流程 + WS 帧。
- 随机报 reset/attempt 拦截/空交卷/导出导入。
- WS 替换、离开重进与通知扇出。

只依赖墙钟的值：`serverElapsedMs`、`validTime` 做 mask；时间戳由 sweep 归一化。采集区间固定为 [0,400]/[0,300]，开训后 sleep 0.5-0.8 s，保证区间落在服务端已过时长之内。随机报内容只 mask `key`，分数取自固定内容或空交卷，不受随机内容影响。
