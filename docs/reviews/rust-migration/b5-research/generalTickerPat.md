# generalTickerPat（手键组训）B5 调研

**结论**：Rust 版手键组训按前端真实路径走**跑不通**。开训接口不写采集时钟，所以学员 startTrain/upload 一律 202「采集尚未开始」。另外 findAll、结算 resolver、WS 通知寻址这 3 处会让前端页面抛错，detail 忽略 uid 会让续拍页丢失（总是回到第 1 页）。锁的问题是：成员表只有 PK，Rust 的定位锁和 UPDATE 都会扫全表，结果是不同训练之间互相串行。

## a. Java 流程

**表**（子表都只有 PK，见 project006.sql:183-232；唯一的二级索引是 `idx_general_ticker_pat_closing(status,end_time)`，见 migrations/2026-09-11-04:88）：
- `general_ticker_pat`：训练主表。
- `general_ticker_pat_train_user`：成员表。role 0 是学员，1 是组训人。另有 attempt、captureStartedAt、activeMillis 三列；isFinish 取值 null（未开始）/2（已开拍）/1（已结算）。
- `general_ticker_pat_train_page`：报底。
- `general_ticker_pat_train_user_value`：每页提交，字段有 messageBody、standard、finishInfo、captureIntervals、resolver。

**状态机**（训练 status，代码在 GeneralTickerPatService.java）：
- 0 未开始。
- updateStatus(1)：0→1。写 startTime，同时给所有 role 0 成员写 captureStartedAt（:661-670）。
- updateStatus(2)：1→3（收尾补交），写 endTime 和 validTime；如果已经没有未结算学员，直接变 2（:671-678）。
- 状态 3 下 finish：60 秒内走 captureBound；超过 60 秒则 settleClosing，给全体学员结算（:526-543）。
- GeneralSettlementRecovery 每 5 秒把超时的状态 3 训练结算掉（GeneralSettlementRecovery.java:61-66，Service:681-706）。

**授权口径**：
- **写口径**（delete、updateStatus）= 创建者 ∪ role 1 组训人 ∪ 管理员，拒绝返回 207（TrainWriteAccess）。
- **学员口径** `student()`（start、upload、finish、reset）：调用者不是学员时返回 **202**「未查询到该用户的学员参训记录」（:775-781）。
- **读本人/读他人** `readableMember`（findPage、detail、getByTrainIdAndUserId）：读别人的数据只放行创建者或组训人，**管理员不在内**；非成员读自己返回 **202**「未查询到参训记录」（:789-799）。
- **读口径** `requireReadable`（statistics、openApi）= 成员 ∪ 管理者，拒绝返回 207（:757-765）。

**REST 端点**（前端模块 common/api/handkeyZuXun.js）：

| 端点 | 前端请求体 | Java |
|---|---|---|
| POST /add | `{...formData,userId:[...]}`（list.js:122-125，表单在 list.js:89-98；选固定报时 SelectCable.vue:92,133 写 `messageNumber=页数*100`，codeSort 取电缆上的 **Integer**） | :144-284 |
| POST /findAll | `{page,rows:10}`（list.js:135） | :409-439：只按 createTime 降序；每条带 userInfoList，codeSort/isRandom 转布尔，固定报的 messageNumber 换成页行数 |
| POST /detail | `{id,uid}`（student.vue:285、trainScore.js:52）；教员端只传 `{id}`（teacher.js:75） | :441-518：传 uid 时过滤 userInfoList，并回填 existPageNumber/existNumber |
| POST /statistics | `{id}`（teacher.js:170） | :734-739 |
| POST /socket/…/updateTrainStatus | `{trainId,status}`（teacher.js:299） | :647-679，返回 "ok" |
| POST /findPage | `{id,userId,floorNumber}`（handKeyTrain.js:123） | :315-407：对训练行加 PESSIMISTIC_WRITE；该页无报底时现场生成 |
| POST /uploadResult | `{trainId,floorNumber,messageBody:[{id,moresKey,patKeys,moresValue,patLogs,moresTime}(值都是字符串)],attempt,captureIntervals:[{startedMs,endedMs}],standard:[{dot,line,codeGap,wordGap,groupGap,offSize}],finishInfo:"<json>"}`（handKeyTrain.js:639-645） | :566-619 |
| POST /finish、/reset | `{id,attempt}`（handKeyTrain.js:610、752） | :520-564；:713-726（reset 返回 "ok"） |
| GET /startTrain | `?trainId&attempt`（handKeyTrain.js:208） | :744-756，写 isFinish=2 |
| GET /delete | `?trainId`（list.js:248） | :290-313，同时关闭房间内的 socket |
| openApi `/综合训练-手键拍发Api`、`/findTrainInfo`；socket `/getByTrainIdAndUserId` | 前端不调用 | GeneralTickerPatTrainController.java:45,65；GeneralTickerSocketController.java:36 |

**WS** `/generalTickerPat/{uid}/{trainId}/{role}`（WebSocketGeneralTickerPatService.java）：
- 握手：凭 query 里的 token/deviceId 认证。路径上的 role 必须等于库里的 role，否则发「训练数据异常」后关闭（:104-113）。
- 帧格式：`{"code":1|-1,"sendName":"","receiveName":"","data":"<字符串>"}`。
- 服务端主动发：`online {topic,id}` 发给对侧（:124-156）；`offline` 在连接关闭时发（:247-269）。
- 入站主题：
  - begin、end：只有 role 1 可发，广播给全体学员。
  - ready（把状态置 2）、pat、finish（把状态置 3）：只有 role 0 可发，转发给组训人。
  - 转发前把 `id` 覆盖为认证身份（:207-232）。
  - 错误文案：消息格式错误 / 未知主题 / 无权限操作 / 房间不存在。
- 新的 role 1 连接会顶掉旧组训人（:139-143）。

**通知**：
- NOTIFICATION_NEW_TRAIN（200，`{type:"ticker",id,title}`）：提交后逐个学员发（:123-126,186）。
- NOTIFICATION_TRAIN_RESULT（201，`{type,userId,trainId}`）：每结算一名学员，发给 role 1 成员（:557-559，GeneralPatResultNotifier.java:27-33）。
- 两者**只发经典 `/websocket/{sid}` 会话**（WebSocketService.java:148-153）。

**计分** countScore（:1023-1049）：
- 逐页调用 MessageComparisonService，把结果回写到 `value.resolver`（:1074）。
- 缺报组数按 messageNumber 计（:1078-1095）。
- 各项扣分以 max 封顶（:1097-1157）。
- accuracy = round(correct/total, 2)×100，例如 "67.00"（:1166-1173）。
- speed 和每页的 speedLog 都按 0 位小数 HALF_UP 取整（:1186，ScoreMath.java:25-32）。
- 码率加减分（wpm）（:1192）。

## b. Rust 映射

| 端点 | 实现位置 |
|---|---|
| add | general_train.rs:951→446-523（通知在 520） |
| findAll | 958→524-539 |
| detail | 965→540-663 |
| findPage | 1200→page_response 1522-1595；main.rs:943 还多挂了 /getPage |
| uploadResult | 1207→1053-1142 |
| finish | 972→finish_participant 3761-3862→settle_ticker 3640-3759 |
| reset、startTrain | participant_action 1790-1836 |
| statistics | 741-793 |
| delete | 702-740 |
| socket updateTrainStatus | **general_aux.rs:254-327**（main.rs:961-965） |
| getByTrainIdAndUserId | general_aux.rs:193 |
| openApi | general_aux.rs:148,166 |
| 恢复任务 | 3871-3966（每 5 秒，main.rs:266） |
| set_status | general_train.rs:664-701，只挂在 Java 不存在的 `/api/generalTickerPatTrain/updateTrainStatus` 上（main.rs:946） |

WS 部分（ws.rs）：
- 路由：1242-1249。
- 握手准备：1488-1520；查角色 general_role：1611-1659。
- online/offline：register 304-419、disconnect 612-650。
- 消息分发 general_message：2336-2398。
- 通知：1106-1175。
- 删训关房：1075-1102。

## c. Rust 与 Java 的行为差异

按严重程度排序：
1. **[阻断] 开训不写采集时钟。** 前端走 general_aux::ticker_update_status，它的 status=1 分支只改 status 和 start_time（general_aux.rs:298），不写 role 0 成员的 `capture_started_at`；Java 会写（:667-670）。结果是 startTrain、upload 在 capture_bound 处返回 202「采集尚未开始」（general_train.rs:1856）。这条 aux 路径还有几个问题：
   - 不校验 protocol 和冻结规则。
   - 鉴权放在加锁之前；训练不存在时非管理员得 207，Java 是 202「未查询到训练」。
   - 未开始就停训，文案是「训练尚未开始」，Java 是「训练不在进行中」。
2. **[阻断] findAll 返回原始行**（general_train.rs:524-539）：
   - 缺 userInfoList、pageCount、contentValue；多出 ruleScore、upId。
   - codeSort/isRandom 是 0/1，不是布尔。
   - 固定报的 messageNumber 没换成页行数。
   - 前端 list.js:140 执行 `item.userInfoList.forEach` 会抛 TypeError。
3. **[高] detail 忽略 uid**（general_train.rs:546-660）：
   - 既不过滤 userInfoList，也不计算 existPageNumber/existNumber（Java 在 :470-490 计算）。前端 student.vue:298 用 existPageNumber+1 定续拍页，Rust 下拿不到值，续拍页错。
   - 学员带他人 uid：Java 207，Rust 200。
   - 非成员：Java 202，Rust 207（require_member :400-402）。
   - Rust 多出 `patValue` 字段（:179）。
4. **[高] 结算不回写 `resolver`**：settle_ticker 里没有这条 UPDATE（Java 在 :1074 回写）。结算后 findPage 返回的 resolver 是 null，前端 trainScore.js:144-145 执行 `JSON.parse(null).moreGroups` 抛错。
5. **[高] 通知寻址过宽**：notify_general_new_train/result（ws.rs:1106-1175）对该用户的**所有** WS 会话都发，没有按 family 过滤；Java 只发经典会话。组训 socket 收到 `{"code":201,"map":…}` 后，teacher.js:125 和 handKeyTrain.js:701 执行 `JSON.parse(undefined)` 抛错。
6. **[中] 两处取整口径不同**：
   - accuracy：Rust 直接按原始百分比保留两位（3736-3748），2/3 得 "66.67"；Java 得 "67.00"。Rust 仓库里已有 scoring::accuracy_percent，这里没用。
   - 每页 speedLog：Rust 用 score_text 不取整（3696-3700），得 "184.62"；Java 得 "185"。
7. **[中] 统计里的历史成绩**：Rust 查询没加 is_finish=1，排序按 finish_time（:766）；Java 有 isFinish=1 且按 createTime 排（GeneralTickerPatTrainUserDao.java:71-73）。所以 Rust 会把未完成旧训练的 0 分当成 lastScore。
8. **[中] codeSort 传整数**：前端选固定报时 codeSort 是 Integer。Java（Jackson）把 1 转成 true；Rust 用 `as_bool()`（:457），结果存成 0。
9. **[中] 固定报 messageNumber 非 100 整数倍**：ensure_settlement_pages（3293-3350）要求 ceil(n/100) 页都存在，固定报缺页时 finish 返回 202「固定报底页不存在」，恢复任务每 5 秒失败一次、一直重试；Java 正常结算，把缺页计为缺报组。
10. **208 与 202 的口径不一致**：Java 的 IllegalStateException 统一映射为 202。Rust 在以下位置用了 208：
    - reset 时训练不在进行中（1808）。
    - 「训练已停止接收拍发记录」（1857）。
    - finish 时状态不是 1 或 3（3825，文案「训练尚未开始或已经结束」）。
    - startTrain 的检查顺序也不同：Java 先 captureBound 后 isFinish（:750-754），Rust 先 isFinish（1826）。所以训练结束后再调 startTrain，Java 是 202「训练已停止接收拍发记录」，Rust 是 208「已结算的训练需要先重置」。
11. **身份拒绝码不一致**：
    - upload：非成员或组训人，Rust 207（1062-1063），Java 202。
    - finish：组训人 Rust 207（3777）；非成员时 Rust 文案「未查询到参训记录」（3290），与 Java 不同。
    - findPage：非成员 Rust 207，Java 202。
    - 管理员读他人：Java 207，Rust 放行（readable_target :417、general_aux.rs:216）。
12. **文案差异**：
    - 协议校验缺后半句「；历史成绩保持不变」（1807、3785，对应 Java :813）。
    - upload 缺页码时：Rust「页码不正确」，Java「页码、拍发记录和校准记录不能为空」（1059 / :576）。
    - 采集区间缩短时：Rust「不能覆盖已确认的原始采集区间」，Java「页内容已变化…」（1095 / CaptureTimeline.java:27）。
    - add 时 Rust 只在随机报下校验 type（460）。
13. **getByTrainIdAndUserId**：Java 的 status 是 null（4 参构造器）；Rust 返回在线状态（general_aux.rs:247）。
14. **路由面**：
    - Rust 多出 /getPage、/updateTrainStatus 和 `/api/generalTickerPat` 这条 WS 路径（main.rs:943,946,1281）。
    - 中文路由（main.rs:957）在 axum 0.8.9 下用原始的 percent-encoded 路径去匹配（path_router.rs:388），**疑似 404**；Java 的实际表现交给旅程来确认。
15. **落库内容**：Rust 存客户端原始 JSON（1136）；Java 存 Gson 规范化后的对象（:581-582）。messageBody 字符串的键顺序不同，需要 parse_json 才能对上，未知字段 Rust 会原样回显。
16. **其他**：
    - detail 的训练级 validTime：Rust 取库值，进行中时为 0；Java 在进行中实时计算（:502）。
    - 排序：detail 成员 Rust 按 `role DESC,id` 排，Java 无序；findAll Rust 多一个 `id DESC` 兜底。
    - WS：Rust 只替换同一用户的旧连接，Java 的新组训人会顶掉任何旧组训人；topic 只有空白时 Rust 回「未知主题」（ws.rs:2351），Java 回「消息格式错误」。

## d. 锁与事务

成员表、值表、页表都只有 PK。
- **locked_member**（3285）：`WHERE train_id=? AND user_id=? ORDER BY id LIMIT 1 FOR UPDATE`。在 RR 隔离级别下，它按 PK 顺序扫描，并给所有扫过的行加 next-key X 锁，也就是锁住了**所有更早训练**的成员行。start、reset、finish、恢复任务都会调用它。
- **以下语句会把 general_ticker_pat_train_user 全表锁到提交**：
  - settle_ticker 的 UPDATE（3743）。
  - participant_action 的 UPDATE（1817、1831）。
  - set_status 的 UPDATE…JOIN（686），以及 NOT EXISTS 子查询加的 S 锁（694）。
  - 恢复任务的 SELECT…FOR UPDATE（3901）。
  - Java 对应的写都是按 PK 更新实体（:669、:721、:1201），读也不加锁。
- **子表按 train_id 删除**（713-718、1116、1819）同样是全表扫描。Java 也一样（:302-304、ValueDao:43/48），是 Rust 照搬过来的老问题。
- **锁顺序**：各路径都是先锁训练行（PK），再锁子表，顺序一致，死锁风险低。问题在于子表锁跨了训练：不同房间的结算、重拍、开拍会互相串行，add 插入成员行时也会被 next-key 锁挡住。修法二选一：加 (train_id,user_id) 和 (train_id,floor_number) 索引的迁移；或者先查出 id，再 `WHERE id=?` 更新。
- **其他**：
  - page_response 在鉴权前就加 FOR UPDATE（1536，Java :317 同样如此）。
  - add 对 t_grading_rule 加了 FOR SHARE（464），Java 是普通读。
  - aux 先在事务外鉴权（272）、后加锁（281），存在 TOCTOU（检查与使用之间状态可能变化）。
- **没发现的问题**：
  - WS：消息在每个连接独立的 worker 上处理（ws.rs:1306），general_message 不碰数据库，不会在连接主循环里等锁。
  - 吞错：事务内没有 `let _ =`；ws.rs:2383/2397 的 `let _ =` 只是通道发送。
  - 连接池重入：所有通知都在提交之后（520、735、3857、3938），没有持有事务时再借池连接。

## e. Rust 不应照搬的 Java 缺陷

- 非成员读自己返回 202，而不是 207（:797）；readableMember 不放行管理员，requireReadable 却放行，两处口径矛盾（:762 对 :793）。
- findAll 排序没有兜底键（:419）。Rust 已经补了，保留。
- 子表没有 train_id 索引，还做全表扫描删除（:302、:617、:722）。
- 只读路径在鉴权前加写锁（findPage :317、statistics :736）。
- delete 用不加锁的读做鉴权，删除又在另一个事务里执行（:292-306）。
- 路由名把 Tag 文案误抄进了路径（Controller:45）。
- 固定报缺页按 messageNumber 计「幻影缺报」（:1081-1092）。Rust 应按真实页数计算，**而不是拒绝结算**；这一点需要先拍板口径。

## 旅程（generalTickerPat-journeys.json，3 条，132 步）

1. **主流程**：
   - 固定 3 组电缆，传整数 codeSort。
   - 经典 WS 和组训 WS 双连；涵盖越权 207、过期轮次 208。
   - 先不植入时钟调一次 startTrain，专门暴露 #1；之后用 seed_sql 把 capture_started_at 固定为 2026-10-09。
   - 上传固定区间 [0,3900]，覆盖 #6；结算后读 resolver（#4）、统计；教员组训 socket 收 201（#5）；最后删训关房。
2. **历史与重拍**：
   - 训练 A 的 create_time 固定为 2026-10-01。
   - 训练 B：reset 后旧轮次返回 208，之后重新植入时钟。
   - 统计 lastScore（#7）；学员在 A 的组训 socket 是否收到 B 的 200 通知（#5）。
3. **收尾窗口**：
   - 用 messageNumber=150 的固定报。
   - 未开始就停训（#1 文案）、区间缩短、停训后状态变 3、WS end、收尾期内 reset（#10）、60 秒宽限期内 finish（#9）。

**时钟处理**：只 mask serverElapsedMs、训练级 validTime 和训练自增 id；分数和内容不 mask；日期字符串由 sweep 统一归一化。
