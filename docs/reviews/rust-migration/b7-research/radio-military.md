# B7 报话/军语调研（只读，未实跑）

结论：5 个 Java controller 共 30 个端点，`dual-stack-journeys.json` 里一个都没有（0/30 覆盖；current-coverage 状态为 NO_CUTOVER 或 FLAGGED，只被未登录探针碰过）。读码发现 1 条可能让所有非空读取直接崩溃的缺陷（DECIMAL 解码）、3 条评分/时长差异、2 条会锁全表的非主键 `FOR UPDATE`。以上均未经双栈实跑；草稿 `radio-military-journeys.draft.json`（4 条旅程，120 步，已验证可解析）用来证实。路由受 `RUST_PHASE6_RADIOTELEPHONE_ENABLED` 控制（`main.rs:332`，`local-dual-stack.sh` 会全开）。

## 1 未覆盖端点与前端载荷
- postRadiotelephoneTrain（`common/api/postWording.js`）：add `{name,type,number,speed,disturb:[],trainType}`（`wording.js:63-70,139`，number 默认 200）；listPge（注意拼写，无体）；details/begin `{id}`（`wordTrain.js:121,198`）；finish `{id,content:JSON.stringify(data)}`，data 每项带 `answer:{key,value}`（`wordTrain.js:256-260`）；delete `GET ?trainId=`。findByType `{type}`（`termTrain.js:256`）；findByTypeAndRandom 前端无调用。
- radiotelephone（`TelegramApi.js:377-392`）：listPage/begin/pause/resume/finish，体 `{type}` 或 `{type,sessionId}`（`termTrain.js:66,339,361,532`）。`menu/Index.vue:63` 的 `getPreTermTrainTotal()` 无请求体，Java 此时 dto=null 返回全部类型；harness 对无体步骤固定发 `{}`，无法复现，Rust 的 `Option<Json>`（`radiotelephone.rs:114`）需另用 curl 验证。
- postRadiotelephoneTermData：findByType `{type}`；save/update/delete `{id?,type,key,value}`（`termDeploy/Index.vue:221-262`），三者 `@RequireAdmin`。
- postMilitaryTermTrain（`MilitaryTermApi.js:20-103`）：add `{name,types:[id],totalNumber}`（`militaryTerm/Index.vue:202`）；details/begin `{id}`；finish `{id,testPaperList:[{id,userAnswer}]}`（`militaryTrain.js:186`）；delete GET。前端只读 `res.code`，details 读 `id/testPaperList/duration/accuracy/status`（`militaryTrain.js:61-75`）。
- mtd：findAll 无体；save `{type,parentId,key,value,sort}`（`militaryDeploy/Index.vue:268`）；update `{id,key,value,parentId}`（无 sort，:334）；delete `{id}`；move `{sourceId,targetId}`（:174）；saveBatch `[{parentName,childName,content}]`（:143）；saveAll 无前端调用。

## 2 Java 流程
- 个人训练表 `t_post_radiotelephone_train` / `t_post_military_term_train`：status 0 未开始 / 1 进行 / 2 结束，只有属主可操作（`TrainWriteAccess`，非属主 207）。服务端自己算成绩，客户端传 score 等 -> 202。
- 报话 finish：校验题面一致、按 trainType 判对错、`accuracy=correct*100/n` 保留 2 位、score 保留 1 位（HALF_UP）；已结束重复提交相同答案 200，不同 -> 208。
- 军语 add：随机抽类型/题、`checkKeyword` 造干扰项（无线->有线、出口<->入口、数字区间加一等）、选项按 `hashCode` 排序成 A-D；`NIPException` 被 add 的 `catch(Exception)` 包成 RuntimeException（HTTP 500）。
- `t_radiotelephone_train`（岗前累计）：`uk(user_id,type)`；锁 t_user 主键行，begin 幂等，finish 以 `finalized_session_id` 做幂等；别人的 session -> 207，失效 -> 208。
- mtd：两层树，`save` 的重复判断拿 key 去比 `value` 列；`update` 用 VO 整体覆盖，VO.sort 默认 0。

## 3 疑似差异
| # | 内容 | Java | Rust | 严重度 / 类别 |
|---|---|---|---|---|
| D1 | DECIMAL 读成 String/f64 | 无此问题 | sqlx 0.8.6 的 `f64`/`String` 都不兼容 DECIMAL（`sqlx-mysql types/float.rs:10-13`, `str.rs:19-31`），`row.get` 会 panic。`post_radiotelephone.rs:237,246,249`（speed/accuracy/score 为 decimal(11,2)/(19,2)）、`post_military.rs:39,41`（accuracy decimal(11,2)、score decimal(19,2)，`project006.sql:27851`）。其他模块的同类字段是 varchar/double | 高；会让 add/列表/详情全 500 或断连。**先跑一次确认**，修法 `CAST(col AS CHAR)` 或读 `rust_decimal::Decimal` |
| D2 | 军语 accuracy/score 先舍入再乘 100 | `PostMilitaryTermTrainService.java:512-519`：`round(c/a,2)*100`、`round(c/n,3)*100`，2/3 -> 67.00 / 66.70 | `post_military.rs:690` `ROUND(c/a*100,2)` -> 66.67 / 66.67 | 高；评分取整类，评分口径按 Java |
| D3 | 报话 duration 向上取整 | `PostRadiotelephoneService.java:171` `(millis+999)/1000` | `post_radiotelephone.rs:616` `TIMESTAMPDIFF(SECOND)` 向下 | 中；两栈结算后的 duration 差 1 |
| D4 | 军语 add 失败口径 | 类型下无题 -> HTTP 500；全部类型 <4 条 -> 202 "bound must be positive"；distinct<4 -> 202 "类型 X 有效题目不足4条，无法生成干扰项"（`ExceptionBoundaryTest:171`） | `post_military.rs:353-360` 一律 202 "该类型条目少于4条…"；`make_paper`（:252）消息缺 "类型 X " 前缀；NULL key/value 行被丢弃（:230-240），空串 value 计为互异 | 中；202 vs 500 |
| D5 | 无干扰项造型 | `checkKeyword` 生成"无线->有线"等合成选项 | `make_paper`（:252-292）只从同类真实 value 里抽 | 中；功能差异，见判断题 |
| D6 | 非主键 `FOR UPDATE` 锁全表 | `findByActiveSessionId` 不加锁 | `radiotelephone.rs:88` `WHERE active_session_id=? FOR UPDATE`（t_radiotelephone_train 只有 pk 与 uk(user_id,type)），错误路径锁住所有用户行 | 高；锁 |
| D7 | 军语 papers/删除 | Java 同样 `trainId` 悲观锁 | `post_military.rs:554` `WHERE train_id=? FOR UPDATE`、`:752` `DELETE ... WHERE train_id=?`（paper 表只有 pk，`project006.sql:27864`）-> 全表 next-key，add 的插入也被挡 | 高；锁 |
| D8 | mtd 非主键 FOR UPDATE | Java 普通读 | `military_term_data.rs:255,402,411` 对 parent_id/key 加锁（表只有 pk） | 低；仅管理员写 |
| D9 | 读接口加锁 | `RadiotelephoneService.listPage` 也 `withLock` | `radiotelephone.rs:149` `ORDER BY type FOR UPDATE`；`post_radiotelephone.rs:262` `select_train(lock=false)` 无锁（正确） | 低 |
| D10 | content 序列化 | Gson 不输出 null；字段顺序保持 | `json_term`（`post_radiotelephone.rs:76`）输出 `"sort":null`，serde_json 无 preserve_order 键序按字母 | 低；旅程用 `parse_json`，null 差异只在 sort 为 NULL 时 |
| D11 | 202/208 的 description | IAE/Terminal 映射 description=message | `post_military.rs` 28 处、`radiotelephone.rs` 9 处传 `""` | 低；sweep 归为 TEXT_ONLY 接受 |
| D12 | Java begin/finish 返回的 types | `convertOne(entity->VO)`，String->List 经 Hutool，内容未知 | 返回类型名数组 | 低；前端不读，首跑看结果再定 |
| D13 | 报话 disturb 存储 | `["1","2"]` 同 | 缺 disturb 存 `''`，与 Java `toJson(null)=""` 一致 | 无差异 |
| D14 | term save/delete id 异常 | id 已带 -> Hibernate 500；delete 缺 id -> IAE 202 | `train_rule.rs:231-300` 统一 202/500 | 低 |

已核对一致：报话 begin/finish/details/delete 的属主 207、终态 208、状态 202、幂等重放；radiotelephone 的 begin/pause/resume/finish 状态机；mtd 的 findAll/move/delete/saveBatch/saveAll 以及 Java 缺陷复刻。

## 4 不应复制的 Java 缺陷
- mtd `save` 用 key 比 `value` 列判重（`MilitaryTermDataService.save`），`update` 空 value 与另一空 value 的节点冲突 -> HTTP 500（UI 里分类 value 都是 ''，第二个分类就不能改名）；`update` 用 VO 覆盖，sort 被重置为 0。Rust 已刻意复刻（`military_term_data.rs:200-203,293-300,332`）。
- 军语 delete/finish 对 paper 表用非主键悲观锁；radiotelephone `listPage` 读接口加写锁。
- `NIPException` 被 `catch(Exception)` 包装成 HTTP 500（业务拒绝不应是 500）。
- `ThreadLocalRandom.nextInt(0)` 的 "bound must be positive" 当作用户提示。
- `findByValue` 取 firstResult 无排序。

## 5 待拍板
1. D1：先实跑确认（一步 `add`），确认后改 `CAST(... AS CHAR)`。无选项，必须修。
2. D2：建议按 Java（先舍入再乘 100），登记已知 Java 缺陷，日后两栈一起改成直接 `c/a*100`；和 B5 缺页扣分口径一致。
3. D3：建议按 Java 向上取整（`CEIL(TIMESTAMPDIFF(MICROSECOND)/1e6)`），毫秒级时钟下 Java 的 start_time 因 datetime(0) 取整可能出现 0 或负（见 B6 回跳结论），Rust 已把负值拒绝，保留。
4. D4：建议保留 Rust 202，登记刻意差异（AGENTS：业务拒绝走 200 信封）；补上 "类型 X " 前缀以统一 distinct<4 的文案。
5. D5：建议移植 `checkKeyword`（至少无线/出口/数字区间三条），否则相同类型下题目质量不同；若不移植则登记刻意差异。
6. D6/D7/D9：按 B6 默认——先锁训练行主键，paper/foreign session 改普通读，删除按已读出的 id 逐个主键删；mtd 同理去掉 `FOR UPDATE`。
7. 无体 listPage：Rust 保持 `Option<Json>`；要求补一个 curl 级验证，不进旅程。

## 6 旅程草稿
- `b7-post-radiotelephone-train-lifecycle`（34 步）：固定 term id 9001-9003，类型 0 只有一条，随机抽取在两栈等价；覆盖 findByType/Random、add 校验、begin 幂等、客户端分数 202、成员 207、1/3 正确（accuracy 33.33、score 33.3）、重复提交 200/208、delete。
- `b7-radiotelephone-prejob-and-term-admin`（31 步）：带 1.1s sleep 的 begin/pause/resume/finish（有效秒数 2），他人 session 207、失效 208；postRadiotelephoneTermData 管理员 CRUD（学员 207）。sessionId 是生成值，已 mask，totalTime/totalCount 不 mask。
- `b7-mtd-military-term-data-admin`（27 步）：两层树 CRUD、move、saveBatch、saveAll；含两个复刻缺陷的 HTTP 500 步骤。
- `b7-post-military-train-lifecycle`（28 步）：训练与试卷由 seed 固定（Java add 随机，无法比较），2/3 正确暴露 D2；`pin-start-time` 把 start_time 设为 10 秒前使 duration 可精确比较（要求 JVM 与 MySQL 时区一致）；add-unknown-type 暴露 D4。
- 非确定因素：报话 `pin-start-time-10s` 之后 Java 为 11/Rust 为 10，这是 D3 的预期 FAIL；所有 duration 之外的分数、内容均未 mask。
