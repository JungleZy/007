# B5 电子键 / 手键 Java-Rust 真实差异清单

结论：两份草稿在本地双栈上跑完（Java :18121 / Rust :18122，隔离库），修掉草稿自身 5 处错误后，剩余 63 个 FAIL 全是真实差异，去重后 **18 条 A（Rust 缺陷）+ 12 条 B（判断题）**。没有发现不稳定步骤。电传域（Telex）不在本轮范围。

| 草稿 | 请求数 | 一致 | FAIL（修后） | ACCEPTED（仅文案） | 连跑 |
|---|---|---|---|---|---|
| generalKeyPat | 155 | 117 | 36 | 2 | 3 次，FAIL 名单与差异内容一致 |
| generalTickerPat | 132 | 102 | 27 | 3 | 3 次，FAIL 名单与响应体大小一致 |

分类：A = Rust 明确偏离 Java 且无争议；B = 判断题（含交接文件已列的已知判断题）。同一差异在多个步骤重复出现只记一行。

## A：Rust 缺陷

| # | 域 | 旅程/步骤 | Java 对 Rust | Rust 位置 | 修法 |
|---|---|---|---|---|---|
| A1 | 键+手键 | key `student-list`、`admin-list-imported`；ticker `teacher-findAll`、`student-findAll`、`student-findAll-two-trainings` | findAll 每项 Java 有 `userInfoList`（成员列表）、`ruleContent=null`、带 `pageCount`/`contentValue` 键；Rust 返回原始行：无 `userInfoList`、`ruleContent` 为完整字符串、多 `upId`。手键另有：`codeSort`/`isRandom` Java 布尔、Rust 0/1；固定报 `messageNumber` Java=页行数(3)、Rust=100；Rust 多 `ruleScore`。前端 list.js 对 `userInfoList.forEach` 会抛错 | general_train.rs:524-539（`list` 直接返回 `rows(...)`） | 用 `detail_member`（:141）按成员装配 `userInfoList`，清空 `ruleContent`，补 `pageCount`/`contentValue`，手键转布尔并把固定报 `messageNumber` 换成页行数，去掉 `upId`/`ruleScore` |
| A2 | 键 | `student-pat-detail-running`、`student-pat-detail-final`、`teacher-pat-detail-student2`、`student-pat-detail-after-finish` | Java 有 `pageAnalyzeVOS`（如 `[{pageNumber:1,patNumber:48,totalTime:400}]`）、`patValue`、`userStatus` 键；Rust 无这三个，却多 `id`、`attempt`、`capturePages`、`captureStartedAt`、`createTime`、`statisticInfo`、`trainId`。`userName` Java 为 null、Rust 填值。cable 训练 `totalNumber` Java=12（页内组数）、Rust=100。`content[*]`：Java `id`/`trainId` 是字符串且无 `userId`，Rust 是数字且多 `userId`。`existPage` 受 B2 影响。trainScore.js `.map(pageAnalyzeVOS)` 会崩 | general_train.rs:1014-1052（`members` 直接拼原始成员行） | 改为输出 Java 的 VO 形态：补 `pageAnalyzeVOS`/`patValue`/`userStatus`，去掉多余原始列，cable 的 `totalNumber` 换页数，`content` 转字符串 id 且不带 `userId`，`userName` 置 null |
| A3 | 键 | `detail-final-teacher`（cable，`userInfoList[0].pageAnalyzeVOS`） | Java `[{pageNumber:1,patNumber:48,totalTime:400}]`，Rust 恒为 `[]` | general_train.rs:170、:197（`"pageAnalyzeVOS": json!([])`） | 按成员采集页计算 `pageNumber/patNumber/totalTime` |
| A4 | 键+手键 | key `student-finish`、`student-finish-replay`、`detail-final-teacher`、`export-teacher`、`student-pat-detail-final`；ticker `teacher-detail-after-finish` | 正确率：Java 先取比值两位再 ×100，如 "92.00"、"67.00"；Rust 保留百分数两位，"91.67"、"66.67"。落库值，影响所有读取该字段的接口（电传同写法，未跑） | general_train.rs:3523-3536（键）、:3736-3748（手键）、:3618-3629（电传同模式） | 改用仓库已有的 `scoring::accuracy_percent`（Java `ScoreMath.accuracy` 口径） |
| A5 | 手键 | `teacher-detail-after-finish` | 每页 `speedLog`：Java 0 位小数 HALF_UP "185"，Rust "184.62"。（总 `speed` 两栈一致） | general_train.rs:3694-3700（`score_text` 未取整） | 页速率按 0 位 HALF_UP 取整 |
| A6 | 键+手键 | key `teacher-room-after-result`、`teacher-room-after-result-2`、`student-room-socket-after-y`；ticker `teacher-ticker-no-result-frame`、`student-a-ticker-no-new-train-frame` | 结算 201（`{type,userId,trainId}`）与新训练 200（`{type,id,title}`）Java 只发经典 `/websocket/{sid}`；Rust 发给接收人**所有**会话，组训人的房间 socket 多收 1 帧 201，学员在别的房间 socket 多收 1 帧 200。前端 teacher.js / handKeyTrain.js 在房间 socket 上 `JSON.parse` 该帧会抛错。经典 socket 上两栈一致 | ws.rs:1130-1138（`notify_general_new_train`）、:1166-1174（`notify_general_result`）：`filter` 只按用户 id | 过滤 `connection.family == Family::Classic` |
| A7 | 手键 | `student-detail-teacher-uid-207`、`student-detail-self-uid`、`student-detail-b-self-uid` | detail 的 `uid` 参数：Java 学员传他人 uid → 207，Rust 200 并返回完整详情（授权缺口）；学员传自己 uid：Java `userInfoList` 只含本人并带 `existPageNumber`/`existNumber`（如 1/[1]），Rust 多返回组训人行（长度 2 对 1）且无这两个字段，学员 student.vue 续拍页(`existPageNumber+1`)失效 | general_train.rs:540-662（`detail` 不读 `uid`；`existPageNumber` 只在 Key 分支设置，约 :633-641） | 读 `uid`：非本人且非创建者/组训人 → 207；传 uid 时按 uid 过滤 `userInfoList` 并为手键计算 `existPageNumber/existNumber` |
| A8 | 手键 | `student-startTrain-unseeded` | 教员开训后学员 startTrain：Java 200；Rust 202「采集尚未开始」，随后 upload 全部 202——手键组训按前端真实路径走不通。原因是前端调的 socket updateTrainStatus 在 status=1 时只写 `status/start_time`，不写学员 `capture_started_at`（Java 在 :661-670 写）。草稿后续步骤用 seed_sql 补写时钟绕过 | general_aux.rs:296-300（`UPDATE general_ticker_pat SET status=1,start_time=NOW(6)`） | 同事务内给 role=0 成员写 `capture_started_at=NOW(6)`，并校验协议/冻结规则；或让该路由复用 `set_status`（general_train.rs:664） |
| A9 | 手键 | `add-cable-train-int-codesort`、`teacher-detail-before-start`、`student-finish`、`student-finish-again-idempotent` 等所有返回训练的步骤 | 前端选固定报时 `codeSort` 传整数 1：Java（Jackson）存 true，返回 `codeSort:true`；Rust 返回 false | general_train.rs:456-458（`body[field].as_bool() == Some(true)`） | 数字非 0 视为 true，字符串 "true"/"1" 同 Jackson 口径 |
| A10 | 手键 | `teacher-reads-student-page-resolver` | 结算后 `findPage` 的 `resolver`：Java 有 `{"resolverMessage":["EEEE","EEEE","EEET"],"moreGroups":[],...}`，Rust 为 null；前端 trainScore.js:144-145 `JSON.parse(null).moreGroups` 抛错 | general_train.rs:3640-3759（`settle_ticker` 无回写 resolver 的 UPDATE） | 结算逐页比对后把结果写回值表 `resolver` |
| A11 | 手键 | `teacher-statistics-b-tendency` | `userTendencyVO[*].lastScore`：训练 A 未结算，Java null；Rust 0（历史查询不过滤 `is_finish=1` 且按 `finish_time` 排序，空值再强制转 0） | general_train.rs:766（history 查询）、:770（null→0）。电子键 Java 本身即 0，两栈一致 | 手键历史查询加 `is_finish=1`、按 `create_time DESC`；`:770` 的 null→0 只对 Key 保留 |
| A12 | 手键 | `student-finish-in-grace-window`（固定报 `messageNumber=150`，电缆只有 1 页） | 收尾期内 finish：Java 200 正常结算（缺页计 50 组缺报，score=100，status→2）；Rust 202「固定报底页不存在」，学员永远无法结算，恢复任务每 5 秒重试同样失败。后续 `teacher-detail-after-grace-finish` 的 status/score/accuracy 等差异全由此引起。「缺 50 组」本身是 Java 的幻影计数，是否复制属 B，但「拒绝结算」是缺陷 | general_train.rs:3293-3350（`ensure_settlement_pages`）、:3788、:3899 | 固定报按真实页数结算，不因缺页拒绝；缺组口径待拍板 |
| A13 | 键 | `roster-teacher`（`/getOneline`） | 在线名单顺序：Java 学员按入房顺序、组训人固定放最后；Rust 按入房顺序，组训人先入房则排第一。`status` 随位置错位 | ws.rs:936-975（`general_users_for_key` 迭代 `general_rooms` 的 session 顺序） | 组训人移到末尾 |
| A14 | 键 | `find-page-teacher`、`find-page-2-generated-at-add` | `findPage` 每行：Rust 多 `trainId` | general_train.rs:271（`KEY_PAGE` 含 `trainId`）、:289-291（`page_fields`） | 非 personal 路径输出前去掉 `trainId` |
| A15 | 键 | `teacher-pat-value` | `getPatValue` 每行：Rust 多 `id`；其余字段（key/value/sort）排序后一致（草稿 mask 了 id） | general_train.rs:296-298（`value_fields`→`KEY_VALUE`） | 输出时去掉 `id` |
| A16 | 键 | `ws-blank-topic` | 只含空白的 topic：Java「消息格式错误」，Rust「未知主题」 | ws.rs:2351-2358（`general_message`：只对 `is_empty()` 报格式错误，`" "` 之类空白落到 :2359-2362 的「未知主题」） | `topic.trim().is_empty()` 才算格式错误 |
| A17 | 键 | `admin-list-imported`、`detail-imported` | 导入后训练 `isCable`：Java 取实体默认 0，Rust 为 NULL（导入字段表没有 `isCable`） | general_train.rs:269（`KEY_SYNC`）、:1363（`import_train`） | 导入时写 `is_cable=0`（或 `COALESCE`） |
| A18 | 键 | `statistics-teacher`（两条旅程） | `userTendencyVO[*].status`：Java null，Rust 为 `isFinish`（1） | general_train.rs:769 | 去掉该赋值 |

## B：判断题

交接文件已列的判断题对应：B1 = Java 缺页扣分重复累加，B2 = Rust 交卷补页，B3a-g = 202 对 208/207。B4-B6 是本轮新增的判断项。锁方案那一条草稿未覆盖。

| # | 域 | 旅程/步骤 | Java 对 Rust | Rust 位置 | 备注/建议 |
|---|---|---|---|---|---|
| B1 | 键 | `student2-finish-in-grace-no-pages`、`student-finish-no-pages`、`detail-final-teacher`、`statistics-teacher`、`export-teacher`、`detail-imported`（两条旅程） | 空交卷缺页扣分：12 组 cable：Java `lackLineNumber=1`/`lackLineScore="-1"`/score 85，Rust `0`/`"-0"`/86；250 组随机报：Java 49/`"-49"`/-199，Rust 24/`"-24"`/-174。Java 缺页循环与公式重复累加（GeneralKeyPatService.java:897-925） | general_train.rs:3498-3516 | 已知判断题：复制还是登记。注意 Rust 的 `"-0"` 字符串本身也与 Java 不同，无论如何应修成 `"0"` |
| B2 | 键 | `find-page-3-after-finish`、`student-pat-detail-after-finish`（`existPage`、`pageCount`） | 交卷后缺页：Java 不生成（`findPage` 第 3 页 0 行，`existPage=[1,2]`，`pageCount` null）；Rust 交卷时补生成（50 行，`existPage=[1,2,3]`，`pageCount=3`）。另有 totalNumber 很大时拒绝服务风险 | general_train.rs:3788、:3899 | 已知判断题，交接文件倾向按 Java |
| B3a | 键 | `student-finish-before-begin` | 训练未开始 finish：Java 202「训练不在进行中」，Rust 208「训练尚未开始或已经结束」 | general_train.rs:3824-3826 | 202 对 208 口径 |
| B3b | 键+手键 | key `student2-reset-in-grace`、`student-reset-after-end`；ticker `student-reset-after-stop`、`student-reset-while-closing` | 非进行中 reset：Java 202「训练不在进行中」，Rust 208 同文案 | general_train.rs:1808 | 同上 |
| B3c | 键+手键 | key `student-start-after-end`；ticker `student-startTrain-after-stop` | 训练结束后 startTrain：Java 202「训练已停止接收拍发记录」（captureBound 先于 isFinish 判断），Rust 208「已结算的训练需要先重置」（isFinish 先判，:1826；capture_bound :1855-1857 也是 208） | general_train.rs:1826、:1855-1857 | 同上，且检查顺序也不同 |
| B3d | 键 | `teacher-restart-ended` | 重开已结束训练：Java 202「训练不能重新开始」，Rust 208 同文案（手键走 aux 路径两栈都是 202） | general_train.rs:682 | 同上 |
| B3e | 键+手键 | key `outsider-reads-own-page`；ticker `outsider-page-read`、`outsider-detail` | 非成员读自己：Java 202「未查询到参训记录」，Rust 207「无权限」 | general_train.rs:387-404（`require_member`→`denied()`） | 同上；注意 Java 的 readableMember 不放行管理员，Rust 放行（草稿未覆盖） |
| B3f | 键 | `outsider-pat-value-self` | 非成员 getPatValue 读自己：Java 200 `data=[]`，Rust 207 | general_train.rs:405-420 | 同上 |
| B3g | 键+手键 | key `teacher-upload-rejected`、`teacher-finish-rejected`；ticker `outsider-upload`、`teacher-finish-as-student` | 非学员上传/结算：Java 202「未查询到该用户的学员参训记录」，Rust 207「无权限」 | general_train.rs:1063（upload）、:3777（finish） | 同上 |
| B4 | 手键 | `socket-getByTrainIdAndUserId` | `data.status`：Java null（4 参构造器），Rust 在线状态 1。前端无调用 | general_aux.rs:247 | 建议登记为刻意差异 |
| B5 | 手键 | `openapi-find-train-info-roster`（`/api/openApi/generalTickerPatTrain/findTrainInfo`） | 房间名单结构：Java 用 `GeneralTickerPatTrainUserDto` 转换，`userId` 恒 null、无 `id`/`status`、学员在前组训人在后；Rust 返回名单形态 `{id,userName,role,status,...}` 且顺序按入房。前端无调用。中文路由 `/综合训练-手键拍发Api` 两栈用 UTF-8/百分号编码/二次编码均 404，无差异 | ws.rs:936-990、general_aux.rs:148-166 | 建议登记，或按 Java DTO 形态输出 |
| B6 | 键 | `export-teacher`（两条旅程；草稿已 mask） | 导出 `pageDto[*].id`：Java cable 训练全为 null，随机报为从 UUID 转 Integer 的不稳定数值（如 1、213、25745）；Rust 导出原 UUID。是 Java 缺陷，Rust 不应照搬；草稿 mask 掉了该字段，所以不报 FAIL | general_train.rs:1215-1270 | 建议登记 |

（B3a-g 为 202 对 208/207 判断题的 7 个具体点，B1/B2 为其他两条已知判断题，B4-B6 为新增判断项，合计 12 条。）

## ACCEPTED（仅文案，不再展开）

key `add-missing-total-rejected`（不能为空 对 不合法）、key `student-upload-page-out-of-range`、ticker `teacher-stop-before-start`（训练不在进行中 对 训练尚未开始）、ticker `student-upload-shrunk-intervals`、ticker `teacher-restart-after-stop`（文案相同，被判 TEXT_ONLY）。

## 草稿修改清单

1. key 旅程 cable `teacher-pat-value`：加 `mask: ["$.data[*].id"]`。该查询 Java 无 ORDER BY（GeneralKeyPatUserValueDao.java:12-14），草稿的 `unordered` 保留；Rust 多出的 `id` 是生成值，会让多重集合排序顺序错位、造成每行假差异。id 存在性差异仍会报告（A15）。
2. key 旅程 cable `export-teacher`：加 `mask: ["$.data.pageDto[*].id"]`。同上，Java 的 id 为 null 或不稳定数值，混进 `unordered` 的排序键会把其余字段的比对全部错位。副作用是遮住了 B6（已在上表登记，证据来自 mask 前的首次运行）。
3. key 旅程 generated `export-teacher`：同 2。
4. key 旅程 generated `student-page-1-submitted`：加 `mask: ["$.data.messageVO[*].key"]`。随机报内容每栈独立生成，key 必然不同；分数与提交值（value）仍逐项比对。
5. ticker 旅程 main-flow：`openapi-online-roster-encoded-path` 改名为 `openapi-find-train-info-roster`，路径改为 `/api/openApi/generalTickerPatTrain/findTrainInfo`。原中文路径两栈都 404（空响应体），这一步等于没比较；`findTrainInfo` 可达且暴露了 B5。
6. 保留的 `unordered` 依据：`detail.userInfoList`、`findAll.userInfoList`、`statistics.userTendencyVO` 的 Java 查询无 ORDER BY（`find_general_key_pat_user_dto` 命名查询，GeneralKeyPatUserEntity.java:26-31；`findByTrainIdAndRole` 无排序，GeneralKeyPatUserDao.java:25）；`getTrainInfo` 各数组同理。未新增其他 mask，分数、内容、状态码均未 mask。

## 未被草稿覆盖（调研笔记里的可疑项，本轮未验证）

管理员读他人数据（Java 207 / Rust 放行）、统计 `userStatus`、手键 WS 同用户/组训人替换、锁序与死锁、读接口持 X 锁、import 对 `t_user` 的 FOR UPDATE、补页 DoS（B2 的放大）、WS 删训/入房竞态。这些需要并发或专门旅程。
