# B6 仿真调研（只读，未实跑）

结论：27 个 REST 端点只有 5 个有旅程（router addRoom/findRoom/getRoomUserList/changeChannel/delete），WS 只有握手探针；其余 22 个 REST + 全部房间 WS 话题无覆盖。读码发现 3 条会让前端直接出错/失效的 Rust 缺陷，另有若干判断题。所有差异均未经双栈实跑，草稿旅程 `simulation-journeys.draft.json`（4 条，共 126 步）用于证实。

## 1 未覆盖端点与前端载荷
- routerRoomContent（快报/干扰，`common/api/UnionApi.js`）：addRoomAndContent(:6)、findAlls(:14)、findById(:22)、addStudent(:30)、findTrainUser(:38)、uploadResult(:47)、saveSetting(:55)、delete(:79)。载荷：建房 `{roomName,bdType,bwType,bwCount,isRandom,content(JSON串),mainSignal,interferenceSignal,isCable,cableId,startPage}`（`disturbCode/js/disturbCode.js:66`）；其余多为 `{roomId:Number}`；上传 `{roomId,contentValue:[“[\"ABCD\"]”,...]}`（`useBroadStudent.js:237`）。
- report/recept（`broadcastTeachingApi.js:12-47`）：addRoom、findRoom、getRoomDetail、delete；载荷 `{roomName,sendUserList,receiveUserList,content:'',bdType,mainSignal,bwType,bwCount,isRandom,isCable,cableId,startPage,isStartSign}`（`broadcastTeaching.js:152`）。recept/getRoomDetail 与 report/delete 无前端调用。
- router 未覆盖：getRoomDetail、getRoomChannels、sendFinish、findPage（`useBroadStudent.js:62` 传 `roomId,userId,pageNumber`）。
- socket/getAllRoomInfo：调试端点，`@RequireAdmin`，无前端。
- WS `/simulation/{uid}/{roomId}`：所有话题均无覆盖。

## 2 Java 流程
- 房型 `room_type`：0 线路通报、1 快报/干扰、2 通报教学、3 收报组训；`stats` 0 未开始/1 进行/2 结束；用户表 `user_type` 0 发报(带训)/1 收报。
- 授权：非成员且非建房人/管理员 -> 207（`SimulationRoomAccess`）；改频道/删除/保存设置按组训位 -> 207；业务校验（房间不存在、非发报人、未开始、非学员上传）-> 202。
- WS 帧信封：`{code:1|-1,sendName,receiveName,data:<字符串>}`。握手失败发 code -1 后关闭。路由房话题 ready/play/begin/end/over/change；干扰房 0/1/2/3/begin/end/select/result；通报房 type 1/2/3/4/online/result。
- 状态变更：路由 begin 写 `start_time`，end 写 `total_time`；通报 1/3 写 `stats=1,play_status=1`；4 写 `stats=2,total_time=count`。
- 结果通知：uploadResult 提交后发 `{type:result,topic:result,roomId,userId}`，经 `sendMessage` 包装成信封，收件人 = 建房人 + 非路由房的发报位（`SimulationResultNotifier`）。

## 3 疑似差异
| # | 内容 | Java | Rust | 严重度 / B5 类别 |
|---|---|---|---|---|
| R1 | result 通知未包信封 | `SimulationResultNotifier.java` afterCommit 经 `sendMessage` 包信封 | `ws.rs:1231-1250` 直发裸 JSON；`useBroadStudent.js:149-152` 取 `envelope.data` 为空即报“训练通知格式无效”，通报教学学员/教员收不到刷新 | 高；字段缺失类 |
| R2 | 发送者回显缺失 | 路由 begin/end/over/change、干扰 0/1/2/3 对含发送者的全员广播（`WebSocketSimulationService.java:462-480,640-720`） | `sim_snapshot`（ws.rs:739）排除发送者，`simulation_router`(ws.rs:2816-2857)、`simulation_disturb`(ws.rs:2566-2660) 沿用 | 高；通知扇出类 |
| R3 | 路由 begin/end 不写时间 | begin 写 `start_time`；end 算 `total_time`（Java:680-715） | ws.rs:2816 只 `stats=1`，ws.rs:2835 只 `stats=2`；getRoomDetail 的 startTime 为空、totalTime 恒 NULL | 高；状态机 |
| R4 | 通报 1/3 不置 play_status | `updateStatsToGoing` 同时 `play_status=1` | ws.rs:2709 topic 1 仅 stats，ws.rs:2721 topic 3 仅 play_status | 中 |
| R5 | uploadResult 非学员/非成员 | 202“仅参训收报人员可提交答案”（Content Service） | simulation.rs:960-961 返回 207 | 中；按 B5 已拍板应 202 |
| R6 | 未知话题 | 干扰/路由静默忽略；通报回“消息格式错误” | ws.rs:2659 干扰、ws.rs:2858 路由也回 -1 帧 | 低；判断题 |
| R7 | findPage 无 userId | 传 null，value 恒空 | simulation.rs:995 默认当前用户，会返回本人答案 | 低（前端恒传 userId） |
| R8 | addRoom 校验 | isCable 缺失一律 202；roomName 不校验 | simulation.rs:536 非干扰房缺失 isCable 当 0；空 roomName 202 | 低 |
| R9 | select 无成员行 | 建房人无行则不广播也不改频道 | ws.rs:2625 仍广播、仍改会话频道 | 低 |
| R10 | 多余路由 | 无 `/router/savePage` | main.rs:862 + simulation.rs save_page；行为 202/208 另立 | 低；建议删除 |

锁与事务（Rust）：
- 所有 simulation 表仅有主键（`project006.sql:283-355`），Rust 每个读端点都 `lock_room(... FOR UPDATE)`（simulation.rs:138），再对 `room_user`、`content`、`page`、`page_value` 做 `WHERE room_id=?` 非主键 `FOR UPDATE`（:142,:190,:1008,:1020），会升级为范围锁；getRoomDetail/getRoomUserList/getRoomChannels/findPage 是读接口，Java 只有 findPage 加锁。建议读接口去掉 FOR UPDATE，只写路径先锁房间主键行（已拍板的锁方案）。
- `t_cable_floor WHERE cable_id=? FOR UPDATE`（:560）：该表也只有主键，读固定报底时锁了共享数据；应改普通读。
- 删除顺序（page_value、page、user、content、room）与 Java 一致，先锁房间行，符合方案。
- WS 的 `UPDATE ... WHERE room_id AND user_id` 与关闭时 `DELETE room_user`（ws.rs:2932）直接走池、不加房间锁，属非主键写，范围小，登记即可。

## 4 不应复制的 Java 缺陷
- 学员在 stats=0 断线即被 `roomUserDao.remove` 删除成员行（Java:329；Rust ws.rs:2932 已复制）。刷新页面就丢参训资格。
- `addStudent` 任何登录用户可加入任意房间（含路由/通报房），无邀请校验。
- `findAlls` 返回全库快报房，不按成员过滤。
- 通报 `finish` 的 `Integer.parseInt(count)` 异常只回“消息格式错误”，但 `updateStatsToFinish` 已提交前不会部分写。
- findPage 读接口持房间行写锁（`lockRoom`）。
- 路由 getRoomDetail 的 totalTime 用 JVM 时间减库时间，时钟偏差会出负值（B5 已见 -3）；Rust 用库 `NOW()` 更稳，旅程中 mask。

## 5 待拍板
1. R2/R3/R4 按 Java 修（我的建议：全修，前端依赖回显与 startTime）。
2. R5 沿用 B5 结论改 202；R6 干扰/路由未知话题是否改为静默（建议保留 Rust 的错误帧，登记刻意差异）。
3. 是否保留 stats=0 断线删成员（建议不复制：只在 stats=0 且从未提交时也不删）。
4. 读接口去 FOR UPDATE、删除 `/router/savePage`。
5. R8：roomName 空是否继续 202（建议保留，登记）。

## 6 旅程草稿（注意事项）
- `b6-router-room-lifecycle`、`b6-disturb-content-flow`、`b6-report-recept-teaching`、`b6-validation-and-auth`。
- 夹具：`seed_sql` 增加 `b6-out` 外人；内容用 `isRandom=0,bdType=1,bwType=1` 的确定性报底；页 id 与 createTime/startTime/totalTime 已 mask；房间列表同秒创建的顺序未定，故每条旅程仅一个同类房间。
- R1-R4 在草稿中不 mask，预期首跑 FAIL。getRoomDetail 的 start/totalTime 被 mask，R3 需靠 `detail-ended` 之外的单测或人工核对。
