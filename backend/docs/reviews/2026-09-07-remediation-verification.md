# 结论：上一轮整改高风险子集真实落地率高（8/8 P0 已修复、fix-spec 声称任务 23/24 已落地），但 5 项审计确认缺陷被家族批次漏认领、代码原封未动（改级#7/#23、遗留 P2-03/P2-06/P2-07）

| 项目 | 内容 |
|---|---|
| 审查范围 | docs/reviews/2026-08-{15,26,28}-*.md、docs/specs/2026-08-26-fix-spec.md、docs/plans/2026-08-26-fix-plan.md，对照当前 HEAD(b9b9f22) 全量源码/配置/迁移脚本/workflow |
| 审查日期 | 2026-09-07 |
| 审查方式 | 静态阅读 + grep 取证 + .git/logs/HEAD 只读核对提交链（未运行构建/测试；本片只判定「声称修了的是否真修了」，不发现新缺陷）|
| 核销结论 | fix-spec 任务表 已落地23/部分1/未落地0/不一致0；8 条 P0 已修复8/未修复0；16改级(实列15) 已修复6/部分1/未修复2/待邻片验证6；13 遗留 已修复10/未修复3 |

> 编号前缀 `RV`（RemediationVerify）。上一轮文档行号可能漂移，一切以当前源码 file:line 为准。

---

## 0. 提交链核对（.git/logs/HEAD，只读）

任务给的整改范围 `5b37637..b9b9f22` 只对应**最后一次** `fix/spec-follow-up` 的 ort 合并；整改主体在更早的提交里：

1. `fix/2026-08-26-p0` 分支承载 fix-plan 批 0-7（reflog 可见 `fix(persist-5.*)`/`fix(ws-*)`/`fix(scoring-*)`/`fix(except-*)`/`fix(tail-*)`/`fix(final-*)` 共约 30 提交）→ FF 合入 main（`2036ad5→9a136b9`）。
2. 两条 CI 修复 → `5c7b0a6`。
3. `fix/spec-remediation`（8 提交，doc 载 `0cfcb51..a9ae85b`）FF 合入 → `46e6cab`。
4. `docs: 展开 fix spec 后续实施计划` → `5b37637`。
5. `fix/spec-follow-up`（Task 1-10）ort 合并 → `b9b9f22`(HEAD)。

结论：无法用 `git show --stat` 逐提交比对（本片只读工具集无 shell，git 写/checkout 本就被禁），且提交范围失真；因此**全部核销以当前工作树源码取证**，符合验收「每行必须有当前 file:line 证据」。

---

## 1. fix-spec 任务核销表（fix-plan 批 0-7 + follow-up Task 1-10 声称完成项）

| # | 声称整改（来源批/任务）| 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| RV-01 | 批0/Task1 测试地基切 DevServices，25 个 @QuarkusTest 去 MySqlResource，%test 用 project006_test | 已落地 | `application.yml:66-72`(devservices enabled/image mysql:8.0/db-name project006_test/drop-and-create)；`SmokeTest.java:26-28`(断言 jdbc 含 /project006_test)；`MySqlResource.java` 已删除(glob 无) |
| RV-02 | 批1.1 P0#1 试卷编辑丢题：nullToEmpty 归一、删除后置、去 catch | 已落地 | `TestPaperService.java:63-67`(五题型 nullToEmpty)、`:70-73`(先组装后删)、`:60-90`(无 try/catch) |
| RV-03 | 批1.2 P0#2 课件/测验丢失：校验在前、删除在后、firstBy 判空 | 已落地 | `TheoryKnowledgeService.java:246-247`(swf 列表 null 抛)、`:256`(校验后才删)、`:280-281`(firstBy null 抛)、`:269/288/300`(nullToEmpty) |
| RV-04 | 批1.3 P0#3 按钮权限静默丢失 + 改级#14 自赋值 | 已落地 | `MenusService.java:98-100`(permissions null 抛)、`:108-110`(findById null 抛)、`:111-118`(改用入参 `in.getXxx()`，自赋值已除) |
| RV-05 | 批1.4 P0#4 编辑考试抹答卷：状态守卫 | 已落地 | `TheoryKnowledgeExamService.java:75-79`(count 已作答>0 抛 IllegalStateException 拒绝重建) |
| RV-06 | 批1.5 P0#6 损坏 JSON 空化回写：四 catch 改抛 | 已落地 | `TickerPatUtils.java:81/89/97`(corruptJson 抛)、`:325/331/337`(IllegalStateException)、`:545`、`:739-744`(helper)。注：`:301-308` patKeys 分支仅当 raw 以 `[`/`{` 开头才抛（非括号=合法标量，非损坏 JSON），列入待验证观察项 |
| RV-07 | 批1.6 P0#8 Union 单例串扰：session-keyed 状态 + setSendUser | 已落地 | `WebSocketUnionService.java:54`(Client record)、`:60-62`(static ConcurrentHashMap)、`:126-127`(setSendUser+setReceiveUser 各自正确) |
| RV-08 | 批1.7 P0#9 Simulation 断线错身份：session-keyed + routerRoom key 移除 | 已落地 | `WebSocketSimulationService.java:238-239`(reportRoom+routerRoom removeCurrent)、`:549-552`(get 判空)；`SimulationGlobal.java:14/18/22`(ConcurrentMap)；类内无 `private Session/UserModel`(grep 0) |
| RV-09 | 批1.8 P0#22 干扰项死循环：nextInt(size)、distinct≥4、循环上限 | 已落地 | `PostMilitaryTermTrainService.java:136/158/196`(nextInt(size()))、`:145-152`(distinct<4 抛)、`:178-186`(attempts>100 降级顺序补足+warn) |
| RV-10 | 批1.9 改级#19 快照复用源试卷 id：置 null 走 persist、按 examId 删 | 已落地 | `TheoryKnowledgeExamService.java:81-82`(按 examId 删)、`:86`(snap.setId(null)) |
| RV-11 | 批1.10 改级#20 快照 addAll(null)：两侧 nullToEmpty | 已落地 | `TheoryKnowledgeExamService.java:89-93`(写入 5 列 nullToEmpty)、`:334-348`(考核分析读出 5 列 nullToEmpty) |
| RV-12 | 批1.11 Assert 语义 + 军语导入事务边界(#18) | 已落地 | `Assert.java:75-78`(notNull：object==null 才抛，语义正确)；`MilitaryTermDataService.java:207-208`(saveBatch @Transactional)、`:217-218`(excelHanle @Transactional) |
| RV-13 | 批2 WS 家族：@OnError 补齐、COW、session-keyed | 已落地 | `WebSocketGeneralTickerPatService.java:227-230` / `WebSocketGeneralKeyPatService.java:194-197`(@OnError 带 Throwable)；`WebSocketGeneralTickerPatService.java:50-56`(RoomLifecycleLocks 加锁原子建房) |
| RV-14 | 批3 评分核心：ScoreMath 统一 + setWordPerfectNumber 串号修正 + statisticalPage 排序 | 已落地 | `ScoreMath.rate/accuracy` 被 `EnteringTelexPatService.java:79`、`PostTelegraphKeyPatTrainService.java:394/401`、`PostTelexPatTrainService.java:714/876`、`TelegraphKeyPatTrainService.java:84` 采用；`TickerPatUtils.java:665`(setWordPerfectNumber(getWordPerfectNumber()+1))；`TickerTapeTrainService.java:222`(按 type 排序，去 swap) |
| RV-15 | 批4 异常边界：Mapper + JWTInterceptor 收窄 + getUserByToken 抛 | 已落地 | `common/exception/` 存在 GlobalExceptionMapper/UnauthorizedException(+Mapper)/Validation/WebApplication/IllegalState/InvalidTitle 六类 Mapper；`JWTInterceptor.java:89`(proceed 移出 try)、`:86`(log 带 Throwable)；`UserService.java:483`(null 抛 UnauthorizedException)；全仓空 catch=0、log.*(getMessage())=0（grep 0 命中） |
| RV-16 | 批5 findTwoPage 主键→trainId | 已落地 | `GeneralKeyPatPageDao.java:26-28`、`GeneralTelexPatPageDao.java:22-24`（均 `trainId = ?1 and (pageNumber=1 or 2)`）。注：KeyPageDao 泛型仍 `<...,Integer>`(:11)，findTwoPage 功能已正确，泛型对齐属次要 |
| RV-17 | 批5 迁移01 schema-sync：5 缺表 + 2 is_start_sign + 主键类型 | 已落地 | `2026-08-26-01-schema-sync.sql:23/42/52/73/84`(5 表 CREATE)、`:93-94`(simulation_router_room + t_post_ticker_tape_train 补 is_start_sign)、`:106-107`(general_key_pat_page + general_ticker_pat_train_page id→varchar(64)，含演练补入项) |
| RV-18 | 批5 迁移02 22 张 MyISAM→InnoDB | 已落地 | `2026-08-26-02-engine-innodb.sql:16-37`(22 条 ALTER TABLE ... ENGINE=InnoDB，逐名核对) |
| RV-19 | 批5 %prod generation:validate | 已落地 | `application.yml:88`(validate，含 2026-08-28 演练注释 :85-87)；基线仍 `:28` none，prod 覆盖 |
| RV-20 | 批6 workflow：tags 触发 + 按架构拆分 + 产物防同名 | 已落地 | `build-quarkus-native.yml:6`(tags ['v*'])、`:45/50`(native_march x86-64/armv8-a)、`:107-124`(按 asset_suffix 重命名)、`:129`(artifact name 含 os+arch)、`:137-141`(release needs:[build,test]+if refs/tags) |
| RV-21 | 批6 EXPOSE 18001 | 已落地 | `Dockerfile.jvm:91`、`Dockerfile.legacy-jar:88`、`Dockerfile.native:24`、`Dockerfile.native-micro:27` 全为 18001；示例命令 `-p 18001:18001` |
| RV-22 | 批7 findById 裸解引用统一 orElseThrow；死代码删除(Task8)；缺 @Transactional 补注解 | 已落地 | `RoleService.java:94-95`、`TheoryKnowledgeQuestionService.java:62-63/90-91`、`MilitaryTermDataService.java:184-185`(orElseThrow)；`CharsetUtils/GZipUtil/PingYinUtil/PasswordUtil` 及 `controller/test/Test.java` 已删(glob 缺)，`ArraysSafeUtils` 按活调用保留 |
| RV-23 | follow-up Task3 SnowflakeIdKit 可注入时钟；Task4 statisticalPage 升序；Task5 动态分档；Task9 迁移演练 | 已落地 | `SnowflakeIdKit.java:81-84`(currentTimeMillis LongSupplier + lastStamp 防回拨)；`TickerTapeTrainService.java:222`；`TheoryKnowledgeExamService.java:325-326/571-575`(goodBoundary 动态)；迁移脚本+validate 配置齐备 |
| RV-24 | follow-up Task6/Task7 N+1 批量化（TheoryKnowledge 批量、Device/DeviceType 一次 in 查询）| 部分落地 | findById 守卫已落地(`TheoryKnowledgeQuestionService.java:62/90`)；Device/DeviceType 批量查询改写（reflog `fix(tail-4)` 声称）未逐行核实 → 待运行验证 |

**统计：已落地 23 / 部分落地 1 / 未落地 0 / 声称与实现不一致 0。**

> 说明：fix-spec 文档对自身声称的任务诚实且几乎全部兑现；`update()`/TestController/P2-03/06/07 从未被列入任何批次（见 §2b、§3、§4），故不进本表的「不一致」列，而以「审计确认但无批次认领」记入 §3 遗留表与 §4 矛盾清单。

---

## 2. 8 条确认 P0 核销表（full-project-review §2）

| 原编号 | 位置 | 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| P0-1 | TestPaperService | 已修复 | `TestPaperService.java:62-88`：五题型先 nullToEmpty 组装(63-67)→有 id 才删(70-73)→保存循环(83-88)，全程无吞异常 catch |
| P0-2 | TheoryKnowledgeService | 已修复 | `TheoryKnowledgeService.java:243-256`：标题/课件列表校验在前、`deleteAllByKnowledgeId` 后置(256)；`:280-281` 版本1测验 null 抛 |
| P0-3 | MenusService | 已修复 | `MenusService.java:98-100`(permissions null 抛)、`:120-126`(校验通过后才删按钮权限并重建) |
| P0-4 | TheoryKnowledgeExamService | 已修复 | `:75-79`：`count(examId 且已作答)`>0 抛异常拒绝重建，删除/重建被状态守卫拦住 |
| P0-6 | TickerPatUtils + PostTelegramTrainService | 已修复 | `TickerPatUtils.java:325/331/337/545/742` 损坏 JSON 抛 IllegalStateException，外层不再吞后回写；仅 patKeys `:301-308` 对非括号标量不抛（合法路径，非数据丢失），列 §5 |
| P0-8 | WebSocketUnionService | 已修复 | `:60-62` 连接态移入 static ConcurrentHashMap<sessionId,Client>，端点无共享 session/sUser 实例字段 |
| P0-9 | WebSocketSimulationService | 已修复 | 经 `SimulationRoomLifecycle` 按 session 精确增删(`:238-239` removeCurrent)，无单例共享 userModel |
| P0-22 | PostMilitaryTermTrainService | 已修复 | `:136/158/196` nextInt(size())、`:150-152` 候选<4 直接业务报错、`:178-186` 循环 100 次上限后降级，杜绝无界循环 |

**统计：已修复 8 / 未修复 0 / 部分 0。** 审计 §4.2 将 #19/#20 回调 P0，二者亦已修复（见 RV-10/RV-11），合计确认 P0 及回调项 10 项全数落地。

### 2b. 16 条改级核销（full-project-review §2.1，实列 15 行）

| 改级# | 原改级结论 | 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| #5 | 电传删前已转换；真风险=MyISAM 不可回滚窗口 | 部分(已缓解) | 迁移02 将 `t_post_telex_pat_train_page(_value)` 转 InnoDB(`:33-34`)，回滚窗口在迁移应用后消除；删前转换代码模式未变，待迁移在生产落地 |
| #7 | 危险 detached merge `update()` 全仓无调用（死码） | 未修复 | `TickerTapeTrainService.java:108-112` `update()` 仍在（BeanUtil.toBean 全字段 merge），memory 记为「延期待外部契约」，未删 |
| #10 | 多页 parallelStream 竞态 | 已修复 | `PostTelegraphKeyPatTrainService.java:357`、`GeneralKeyPatService.java:668` 改 `.stream()` 串行（注释 P1-1） |
| #11 | 非法 patLogs 少扣分 | 待邻片验证 | 评分深逻辑属 ServicePostTrain 片；本片未独立复算 |
| #12 | 三字段共用 try 连坐清空 | 待邻片验证 | 同上 |
| #13 | 在线查询失败漏结算但可重试 | 待邻片验证 | 同上 |
| #14 | 菜单元数据编辑自赋值失效 | 已修复 | `MenusService.java:111-118` 改用入参 `in.getXxx()` |
| #15 | 角色字段编辑不落库 | 待邻片验证 | RoleService 更新链属 ServiceTheoryUser/General 片域 |
| #16 | 有同类型记录时允许建重复 | 待邻片验证 | 邻片域 |
| #17 | findTwoPage 把 trainId 当主键 | 已修复 | `GeneralKeyPatPageDao.java:26-28`、`GeneralTelexPatPageDao.java:22-24` |
| #18 | 军语 Excel 导入 NPE（且被 P1-43 判不回滚）| 已修复 | `MilitaryTermDataService.java:207-213`(saveBatch 事务)、`:209-211`(空/格式校验)、`:238`(continue)、`:118/230/245`(maxSort null→1)；审计§8 已证 CDI 自调用在 ArC 下事务生效 |
| #19 | 快照复用源试卷 id | 已修复 | `TheoryKnowledgeExamService.java:81-82/86` |
| #20 | 快照 addAll(null) | 已修复 | `TheoryKnowledgeExamService.java:89-93/334-348` |
| #21 | 无统计记录 clear→save(null) | 待邻片验证 | 全仓未见裸 `save(null)`；具体路径属 ServicePostTrain 片域，本片不武断判「已修复」 |
| #23 | `GET /api/test/start` + 硬编码 UPDATE | 未修复 | `controller/test/TestController.java:28-45` 原封：GET /start 硬编码 UUID 连发 begin/pause/goOn/finish 四次 DAO 写 + `System.out.println`(42-44)；Task8 删的是同目录另一个 `Test.java`，漏此端点 |

**统计（15 行）：已修复 6 / 部分(缓解) 1 / 未修复 2 / 待邻片运行验证 6。**

---

## 3. 上一轮遗留 13 条核销表（2026-08-15 situation-display 报告，含 1×P1 + 12×P2；full-project-review §5 基线为 13/13 未修复）

| 遗留编号 | 上轮结论 | 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| P1-01 | 多页评分并行修改共享集合后先删后写 | 已修复 | `PostTelegraphKeyPatTrainService.java:357`、`GeneralKeyPatService.java:668` 串行流 |
| P2-01 | @ApplicationScoped WS 端点共享连接态 | 已修复 | `WebSocketUnionService.java:60-62`(static map)、Simulation 经 lifecycle；端点无 session 实例字段(grep 0) |
| P2-02 | 房间 Map value 与首次建房非并发安全 | 已修复 | `SimulationGlobal.java:14/18/22`(ConcurrentMap，单次 compute)、`WebSocketGeneralTickerPatService.java:50-56`(建房加锁)；reflog `ws-6/tail-9` COW 化 |
| P2-03 | 「最后一次训练」实际取最旧记录 | **未修复** | `TickerTapeTrainDao.java:109-110` `lastTrain` 仍 `Sort.by("createTime").ascending()`；调用方 `TickerTapeTrainService.java:73`、`TelegramTrainService.java:272` 均依赖此错误排序 |
| P2-04 | 军语批量导入部分提交/首次500/提前结束 | 已修复 | `MilitaryTermDataService.java:207-218`(事务)、`:238`(return→continue)、`:118/230/245`(maxSort null→1) |
| P2-05 | 前两页查询把 trainId 当页面主键 | 已修复 | `GeneralKeyPatPageDao.java:26-28`、`GeneralTelexPatPageDao.java:22-24` |
| P2-06 | 页码 0 被持久化为真实训练页 | **未修复** | `PostTelexPatTrainService.java:255` 仍 `if (totalPage < pageNumber || pageNumber < 0)`；0 通过校验→`:263-265` 空页触发 `generateContent(...,0,...)`，第 0 页仍会落库 |
| P2-07 | 跳页请求写入/返回错误页面 | **未修复** | `PostTelegramTrainService.java:415` `int floorNumber = floorContentEntity.getFloorNumber()`(上一页最后楼层)，`:416-427` 以 lastFloor+1 编号并保存，与请求页 `param.getFloorNumber()` 脱钩；请求第4页仅生成第3页并当作第4页返回 |
| P2-08 | 联合训练消息丢失发送者字段 | 已修复 | `WebSocketUnionService.java:126`(setSendUser 取 map.get("sendUser"))、`:127`(setReceiveUser 取 receiveUser) |
| P2-09 | Release job 不会被 tag 触发 | 已修复 | `build-quarkus-native.yml:6`(tags ['v*'])、`:141`(if refs/tags) |
| P2-10 | ARM64 构建传 x86 专用 -march | 已修复 | `build-quarkus-native.yml:47-51`(arm64→ubuntu-24.04-arm + native_march armv8-a)、`:82`(按 matrix.native_march) |
| P2-11 | 多架构发布产物同名覆盖 | 已修复 | `build-quarkus-native.yml:107-124`(按 asset_suffix 重命名)、`:129`(artifact name 含 os+arch) |
| P2-12 | Docker 端口 8080 与应用 18001 不一致 | 已修复 | 四 Dockerfile EXPOSE 18001（jvm:91/legacy:88/native:24/micro:27），示例 -p 18001:18001 |

**统计：已修复 10 / 未修复 3（P2-03、P2-06、P2-07）。** 修复率由上轮 0/13 提升到 10/13；未修复 3 项均为 situation-display 报告特有、且不在 fix-spec 任何批次（批3/5/7 均未认领 TickerTape 排序与 telex/telegram 页码生成）的功能缺陷。

---

## 4. 自相矛盾 / 声称与实现不一致清单

| 编号 | 矛盾描述 | 举证 |
|---|---|---|
| RV-X1 | Task8「死代码删除+文档清理」声称清掉 `controller/test/Test.java` 等零调用代码，但同目录真正危险的 `TestController`（活跃写端点、改级#23）被漏，仍在生产源码里；且它调用的 `lastTrain` 正是未修复的 P2-03 | `controller/test/TestController.java:28-45`（GET /start 硬编码 UUID 四次写库 + println）；fix-spec-remediation.md:83 删除清单仅列 `controller/test/Test.java` |
| RV-X2 | 整改给 TickerTapeTrain 的 begin/pause/goOn/finish 补了 @Transactional（reflog `fix(tail-5)`），却漏掉本类核心排序缺陷；「上一次训练」语义仍取最旧 | `TickerTapeTrainDao.java:110` ascending；遗留 P2-03 原始定位 `TickerTapeTrainDao:102-110` 未变 |
| RV-X3 | full-project-review §5 与审计均把 situation-display 13 遗留计入「348 确认缺陷」并作为 fix-spec 消化目标（fix-spec 依据行 :3），但 P2-06/P2-07 的页码生成旧实现在批 3/5/7 中无任何认领，代码逐字未改 | `PostTelexPatTrainService.java:255`、`PostTelegramTrainService.java:415`；fix-spec.md 批5 仅改 telex/telegram 的 HQL/命名查询，未触页码分支 |
| RV-X4 | fix-spec-remediation.md 声称基线提交 `0cfcb51..a9ae85b`（fix/spec-remediation），任务下发范围 `5b37637..b9b9f22` 仅是最后一次 follow-up 合并，二者不重叠；「整改提交为 5b37637..b9b9f22」不足以覆盖整改主体 | `.git/logs/HEAD`：`5c7b0a6→46e6cab`(FF fix/spec-remediation)、`5b37637→b9b9f22`(ort fix/spec-follow-up)。非阻断，仅提示范围失真——本报告已改以当前源码取证 |

> 说明：Task10 明确声明「不宣称全部 348 条完成」，故上述 RV-X1..X3 更准确的定性是**家族批次覆盖盲区**而非文档说谎——整改诚实但不完整；这些盲区恰落在"P2 功能正确性"而非"P0 数据丢失"，与整改优先级一致，但仍应在下一轮补齐。

---

## 5. 待运行验证清单

1. `TickerPatUtils.java:301-308` patKeys catch 仅对以 `[`/`{` 开头的 raw 抛异常，非括号标量被静默置空列表（`:339`）。需运行确认：现网 patKeys 是否存在合法非括号标量；若否，则此分支应与 :325 一致无条件抛。
2. follow-up Task6/Task7 的 N+1 批量化（DeviceService/DeviceTypeService 一次 in 查询、TheoryKnowledge 批量）——本片仅核到 findById 守卫，批量查询改写未逐行核实。
3. 改级 #11/#12/#13/#15/#16/#21 属评分/角色深逻辑，交 ServicePostTrain/ServiceTheoryUser/ServiceGeneralPat 片以运行取证；本片不武断判定。
4. `%prod generation:validate`（`application.yml:88`）对**未迁移**的现网 project006（仍 22 MyISAM + 5 缺表）会启动即失败——迁移 01→02 必须先于本配置部署（注释 :85-87 已声明顺序）。需在生产变更窗口验证迁移已应用。
5. 迁移 02 对 22 张 MyISAM 表 `ENGINE=InnoDB` 在真实存量数据上的耗时与锁窗口（快照演练 current 2552ms/base 2628ms，非生产数据量）。

---

## 附录：已接受安全风险（内网口径，不计入计数）

- `controller/test/TestController.java` 为匿名 GET 写端点——其**功能性数据破坏**后果已按改级#23 计入 §2b（未修复），此处不重复计安全项；其"无鉴权"属性沿用已接受风险。
- root/root、CORS 全开、token/口令强度、用户查询外泄敏感字段、生产暴露 `/q/openapi`、BeanUtils/hutool CVE 等——沿用上轮内网已接受风险口径，不计入本片核销计数。
