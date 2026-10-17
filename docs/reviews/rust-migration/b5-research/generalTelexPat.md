# generalTelexPat（数据报/电传组训）Java ↔ Rust 对照

结论先行：Rust 能跑通开训、上传、结算（开训写采集时钟 `general_train.rs:686`，detail 不依赖 uid，`existPage` 已算），但有 4 处会让前端出错或看不到内容：`findAll` 无 `userInfoList`、`patDetail` 形态不对、结算不落逐组分析行、成绩通知推到组训房间 socket。另有若干电传独有的评分差异。下文 Java 行号=`GeneralTelexPatService.java`（S），Rust=`general_train.rs`（R）。

## Java 流程与前端载荷

表 `general_telex_pat[_user/_page/_user_value]`，子表只有 PK（project006.sql:36500-36545；仅主表有 `idx_general_telex_pat_closing`）。状态只有 0/1/2，**无 reset 端点**，2 之后 60 s 内仍收提交（S:1138），`GeneralSettlementRecovery` 补结算（S:578-606）。

| 端点 | 前端载荷 |
|---|---|
| add | telexZuXun list.js:108 `{title,isCable,cableId,startPage,totalNumber,ruleId,userId[],trainType:0,patType,type}` |
| findAll / detail | `{page,rows}` / `{trainId,userId}`（student.vue:190，userId 被忽略）；list.js:136、teacher.js:64 直接 `.userInfoList.forEach/.filter` |
| getPage / uploadResult | `{trainId,userId,pageNumber}` / `{trainId,pageNumber,patValue,protocolVersion:1,attempt,captureIntervals}`（datagramTrain.js:146,194） |
| startTrain / finish | GET `?trainId&attempt`（:97）/ `{trainId,attempt}`（:166） |
| patDetail | `{trainId,userId,pageNumber}`（trainScore.js:54），读 `content/pageAnalyzeVOS/pageCount/deductInfo` |
| updateTrainStatus | `{trainId,status:1|2}`（teacher.js:213）；WS `begin/end/ready/pat/finish` |

通知：add 逐学员 `sendInfo` 200（S:166，事务内）；结算后 201 给 role=1（`GeneralPatResultNotifier`），均只走经典 `/websocket`。

## 疑似 Rust 差异

| # | Java | Rust | 级别 | 同族 |
|---|---|---|---|---|
| 1 | findAll 带 `userInfoList{userId,userName,userImg,role}`，`ruleContent=null`（S:233-247） | 原始行，无 userInfoList（R:524-538）→ list.js:136 崩 | 高 | findAll |
| 2 | patDetail：`pageAnalyzeVOS/userStatus/existPageNumber/patValue`，cable 的 totalNumber=页行数，pageCount 仅 cable（S:345-390） | 原始成员行，多 id/attempt/trainId/captureStartedAt/statisticInfo，缺上述字段，pageCount 恒有（R:1014-1050）；trainScore.js:74 折线图为空 | 高 | patDetail |
| 3 | countScore 删旧分析行并写 sort≥0 行（S:891-892），getPage/getPatValue/patDetail.content 含逐组对照 | settle_telex 只 UPDATE（R:3554-3640），三处只剩 sort=-1 原始行，成绩页逐组对照为空 | 高 | resolver 未落库 |
| 4 | 通知仅经典 socket | 发给该用户全部会话（ws.rs:1106-1175），房间 socket 收到无 `data` 的 200/201 → teacher.js:100、datagramTrain.js:42 `JSON.parse(undefined)` 抛错 | 高 | 通知扇出 |
| 5 | accuracy=先 2 位比值再×100（S:946-951，11/12→92.00） | 直接百分比 .2f（R:3618-3624，91.67） | 中 | 舍入 |
| 6 | 页标检查按**单页** groupNumber≥100，无破折号→lessPage（TelexPatUtils:130,1129-1143） | 用**累计** pat_group（R:2787），无破折号时取全文首段数字→errorPage（R:2789） | 中 | 电传独有 |
| 7 | 结算不写 errorNumber（保持 0） | 写 error_code（R:3625） | 低 | 电传独有 |
| 8 | 改错扣分用可选的 `alterError`，UI 规则无此键 → NPE→500（S:940；个人电传 PostTelexPatTrainService:1207 回退 correctMistakes） | 缺键按 0（R:2862） | 中 | 电传独有 |
| 9 | 系数为 0 时仍输出 speedScore "+0"/"-0"（S:970-974） | 为 0 则省略（R:2874） | 低 | 电传独有 |
| 10 | 多余行 muchLessLine += size-rowNum（TelexPatUtils:438） | +多出行数（R:2784）；疑似少 1，未实证 | 低 | 电传独有 |
| 11 | finish 不生成页 | finish/recovery 先 `ensure_settlement_pages`（R:3788,3899,3293）：非 cable 补页（findPage 第 3 页 []→50 行，existPage [1,2]→[1,2,3]）；cable totalNumber%100≠0 时 202「固定报底页不存在」，恢复任务反复失败 | 中 | 结算补页 |
| 12 | 非学员 start/finish → 207（S:1037-1043） | locked_member 返回 202（R:3288） | 中 | 202/207 |
| 13 | patDetail/getPage/getPatValue 读自己不查成员：202 / 200 / 200 []（S:1089-1096） | require_member 先判 → 207（R:405,1536） | 低 | 202/207 |
| 14 | updateStatus 不校验（接受 5、重复开始、未开始就停；S:411-437） | 202/208（R:664-700） | 低 | 状态机（Rust 更严，入 registry） |
| 15 | detail 成员来自 native DTO，无 valid_time/speed_log → null；无 ORDER BY；`pageAnalyzeVOS` 按页算（S:314） | 填 validTime/duration/speedLog，`role DESC,id`，`pageAnalyzeVOS` 恒 []（R:182-195） | 低 | detail |
| 16 | findPage 无 trainId；getPatValue 仅 6 字段 | 多 trainId / id/attempt/captureIntervals/receivedAt（R:292,1596） | 低 | 字段多出 |
| 17 | getOnline 学员在前、组训人最后（S:758-768）；单组训人 | 按入房顺序，允许多组训人（ws.rs:960,320） | 低 | 在线名单 |
| 18 | finish 响应 speed/accuracy 为内存 BigDecimal（"185"/"0"） | 重载库值（"185.00"/"0.00"） | 低 | — |
| 19 | requireProtocol 抛 208（S:1099） | 202（R:1064,1812,3785）；缩短区间、add 文案不同 | 低 | 文案/码 |

已核实**不存在**的同族缺陷：开训写采集时钟（R:686）、detail 忽略 uid（Java 也忽略）、telex 上传/组训人 finish=207、`existPage`。

## 锁

电传子表全是 PK-only，以下均为全表扫描加锁：`locked_member … ORDER BY id LIMIT 1 FOR UPDATE`（R:3275-3287，start 1802 / finish 3777 / recovery 3913）；`UPDATE … WHERE train_id AND user_id`（R:1831、2103（每次上传+结算各一次）、3625）；开训 `UPDATE…JOIN`（R:686）；值表 `DELETE`（R:1116，Java 同）与删训三表（R:713）；recovery `FOR UPDATE` 扫描（R:3901）。Java 成员行按 PK 更新。getPage/findPage 对训练行 `FOR UPDATE`（R:1536，Java 不加锁）。

锁序：upload 为训练 → 值表 → 成员表；finish 为训练 → 成员表 → 补页插页表；delete 为值 → 页 → 成员。跨训练 delete(A) 与 finish(B) 可成环（A 持页表锁等成员表，B 持成员表锁插页被 A 间隙锁挡）。无池重入、无事务内 `let _ =`；WS 业务消息不碰库（ws.rs:2336）。

## Java 缺陷，Rust 不要照搬

updateStatus 无状态机、可写成 5；`alterError` NPE（Rust 应回退 correctMistakes，与个人电传一致）；getPage 对非成员本人放行并现场建页；add 在事务内推通知（S:166）；detail 成员 DTO 漏 valid_time/speed_log，学员回看时长为 0；patGroup 把换行计为一组（保留以对齐评分）；deductInfo 是 HashMap 无序（对比用 parse_json）。

## Journeys

见 `generalTelexPat-journeys.draft.json`，3 条（87/32/29 步）：主流程（双学员、WS、207/208、统计、删训）；两页 cable 结算 + 第二训练通知扇出 + 补页；规则/状态边界（零系数、150 组、UI 规则 NPE、状态机）。固定 cable 内容与区间 [0,400]/[450,700]，开训后 sleep 1.0–1.2 s；仅 mask 生成 id、`serverElapsedMs`、结束后的训练级 `validTime`；成员/趋势列表 unordered（Java 无 ORDER BY，成员 id 为随机 UUID）。
