# 结论：持久层本轮实测 P0 0 / P1 2 / P2 21 / P3 3-项归并后为 2（详见下表），另核销上一轮 28 条 + 汇总§3-7

> 修正：本片计数为 P0 0 / P1 2 / P2 21 / P3 2。

| 项目 | 内容 |
|---|---|
| 审查范围 | `src/main/java/com/nip/dao/**`（101 文件）、`src/main/java/com/nip/entity/**`（103 文件）、`src/main/java/com/nip/common/repository/BaseRepository.java`、`src/main/java/com/nip/common/specification/**`、`backend/database/project006.sql`、`backend/database/migrations/2026-08-26-0{1,2}-*.sql`、`src/main/resources/application.yml`（Hibernate/数据源段）；旁证 `logs/info.log*`、`scripts/rehearse-migrations.sh`、`src/test/java/com/nip/rehearsal/EntitySchemaSnapshotRehearsal.java`、`src/test/java/com/nip/dao/*.java` |
| 审查日期 | 2026-09-07 |
| 审查方式 | 静态阅读 + grep 取证（未运行构建/测试）；schema 漂移为本次「实体注解 vs project006.sql」实测 |
| 计数 | P0 0 / P1 2 / P2 21 / P3 2 |

口径说明：纯安全项（明文口令/身份证/token、REGEXP 回溯）列入末尾附录不计数；相同反模式不同位置各计一条；服务层数据完整性项（上轮 P1-5/P2-14）属他分片，本片仅在核销表标注不计入本片计数。

---

## 1. P0

无。上一轮两条 P0（PS 关联 P0-1 军语 maxSort 拆箱、P0-2 findTwoPage 过滤字段+类型）均已修复，见第 5 节。

---

## 2. P1

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| PS-P1-01 | `dao/TickerTapeTrainDao.java:109-111` | 用户对某 type 已有历史训练记录（≥1 条），再次新建训练 | `lastTrain` 用 `Sort.by("createTime").ascending()+firstResult()` 取到该用户该 type 最早一条而非最后一条。`TickerTapeTrainService.java:73` 拿它判状态：状态=未开始则 `deleteById(:77)` 误删最早记录；状态=暂停则 `finish+finishStatistical(:79-81)` 误结算最早记录；`:226-228` 断点续训接口返回最早训练。可恢复但会错删/错算历史数据 | 本文件 `:110` 唯一用 `ascending()`；同族 `EnteringExerciseDao.java:56`=`ORDER BY createTime desc`、`TelegramTrainDao.java:56`=`order by createTime desc`、`TelegraphKeyPatSyntheticalService` 侧 `findLastTrain` 亦降序。方法名/注释「查询最后一次训练记录」与实现相反。上轮 P1-2 未修复 |
| PS-P1-02 | `backend/database/project006.sql`（22 张 `ENGINE = MyISAM`）+ `resources/application.yml:88` | 部署时未执行迁移 02（`generation:validate` 不校验存储引擎，MyISAM 可漏网），且删除/新建训练的 `@Transactional` 方法中途抛异常 | MyISAM 写入立即生效且忽略回滚。`rollbackOn=Exception.class` 给出虚假安全感：删训练时主记录删失败→报底/答卷已消失；新建时报底生成失败→残留无报底训练与参训人员。永久部分数据丢失（条件性） | 快照内 `general_key_pat`(:67)、`general_key_pat_page`(:82)、`general_key_pat_user_value`(:135)、`general_ticker_pat_train_page`(:193)、`t_post_telex_pat_train_page`(:28533/28545) 等 22 张 `ENGINE = MyISAM`；迁移 `2026-08-26-02-engine-innodb.sql:16-37` 列出同 22 张待转 InnoDB。**部分修复**：迁移 02 + 双快照演练已提供并验证（`2026-08-28-migration-rehearsal.md` MyISAM count=0），但 (a) 快照文件未回灌，(b) `application.yml:88` validate 不校验引擎，(c) `%dev`(:28)=none。上轮 P1-7 |

---

## 3. P2

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| PS-P2-01 | `common/specification/SpecificationExecutor.java:79-95`（`:87`） | 任一分页列表接口翻页 | 总数用 `entityManager.createQuery(...).getResultList().size()` 而非 `count(*)`，把满足条件的全部实体实例化进持久化上下文再取 size；且第二次 `specification.toPredicate(root,query,builder)` 复用同一 `CriteriaQuery/Root`。训练记录随时间线性增长→内存/延迟同步恶化，尾部 OOM 风险 | 调用方 `PostTelexPatTrainService`/`GeneralKeyPatService`/`GeneralTelexPatService`/`GeneralTickerPatService` 的分页。上轮 P1-6 未修复 |
| PS-P2-02 | `common/repository/BaseRepository.java:32-40` | 一次事务批量 `save(Iterable)` 上千条报底 | 逐条 `save`，无 `flush()/clear()`、无按 `statement-batch-size` 分批；每条 `save(:15-23)` 经 `ToolUtil.isIdFieldEmpty` 反射（无缓存）+ 非空走 `merge`（探测 SELECT 打断 JDBC 批处理）。持久化上下文无界增长、脏检查 O(N²) | `GeneralKeyPatService:220/200/268`、`SimulationRouterRoomService:116/133`、`GeneralTickerPatService:203/225` 等批量落库。上轮 P1-9 未修复 |
| PS-P2-03 | `common/utils/ToolUtil.java:84` | 前端提交 `{"id":""}` 类 payload 经 PojoUtils 转实体后走 `BaseRepository.save` | `return value==null||value==""` 用引用比较，非驻留空串（反序列化/`new String("")`）判为「有 ID」→走 `merge`→按 id=`''` 查无→当游离对象，与预期「新增」不一致 | `MenusService:105` 显式 `setId(null)` 规避，侧证此路径会被踩。上轮 P2-4 未修复 |
| PS-P2-04 | `common/specification/SpecificationExecutor.java:97-146`（`:105-107`、`:116`） | 一旦有人调用 `nativeQuery(...)` | `openSession()`(:105-107) 无 try-with-resources/close→每调泄漏一个 Session 及其 JDBC 连接；`if(executeQuery.getRow()==0)return;`(:116) 在 `executeQuery()` 后游标位于首行前，`getRow()` 必为 0→永远提前 return，`ret` 恒 null，`:140` 返回 null | 全项目 `.nativeQuery(` 零调用（潜伏）。catch(:136/:142) 已由 `getMessage()` 改为记全 `e`——**仅该子项修复**，泄漏与 getRow 逻辑未改。上轮 P2-3 |
| PS-P2-05 | `dao/general/key/GeneralKeyPatPageDao.java:11` | 若对该 DAO 调 `findById/deleteById` | 泛型 `BaseRepository<GeneralKeyPatPageEntity,Integer>`，但 `GeneralKeyPatPageEntity.java:20-22` `@Id` 为 `String`(UUID)。关闭 `findById/deleteById` 编译期类型检查；正是该根因催生了上轮 P0-2 的错误签名 | 实体 `:21` `@GeneratedValue(UUID) String id`；快照 `general_key_pat_page.id`=varchar(64)。上轮 P2-5(1/4) 未修复 |
| PS-P2-06 | `dao/general/ticker/GeneralTickerPatTrainPageDao.java:10` | 同上 | 泛型 `<...,Integer>`，`GeneralTickerPatTrainPageEntity.java:17-19` `@Id` 为 `String`(UUID) | 实体 `:18` `@GeneratedValue(UUID) String id`。上轮 P2-5(2/4) 未修复 |
| PS-P2-07 | `dao/EnteringExerciseWordStockDao.java:10` | 同上 | 泛型 `<...,String>`，`EnteringExerciseWordStockEntity.java:17-19` `@Id` 为 `Integer` | 实体 `:18-19` IDENTITY Integer id。上轮 P2-5(3/4) 未修复 |
| PS-P2-08 | `dao/PostTrainGlobalRuleDao.java:8` | 同上 | 泛型 `<...,String>`，`PostTrainGlobalRuleEntity.java:25-27` `@Id` 为 `Integer` | 实体 `:26-27` IDENTITY Integer id。上轮 P2-5(4/4) 未修复 |
| PS-P2-09 | `dao/RoleDao.java:14-18` | 用户在 `t_user_role` 无记录（已建号未派角色） | `where u.id=:id` 过滤掉未关联行，结果集空→`getSingleResult()` 抛 `NoResultException`。`UserService.java:391`（登录取菜单）与 `:94`（用户详情）调用，异常被宽 catch 吞成通用错误，登录失败且原因不可见 | 项目内 `PostTelexPatTrainPageValueDao:21-24` 用 `getResultList` 首元素返 null 的正确写法。上轮 P2-1 未修复 |
| PS-P2-10 | `dao/UserDao.java:24-35`（`:33`） | `getUsersByIds` 传非空 ID 集合 | `find("id REGEXP :ids", ...)` 拼 `a|b|c`：REGEXP 非 HQL 标准中缀操作符；即便可跑也是非锚定子串匹配（ID 互为子串误命中）、正则元字符改变语义、无法走主键索引退化全表扫描；空集合走 `findAll().list()` 全表 | 可达 `UserController:135`→`UserService:106`→`findAllUser`。应为 `find("id in ?1", ids)`。上轮 P2-2 未修复 |
| PS-P2-11 | `dao/general/key/GeneralKeyPatUserValueDao.java:20-23`（`:21`） | 若该方法被接线 | `group by page_number` 用的是命名策略转换后的**库列名**而非实体属性 `pageNumber`，HQL 解析报属性未知；且 `find` 返完整实体却单列 group by，MySQL `ONLY_FULL_GROUP_BY` 拒绝 | 潜伏：`GeneralTickerPatService:442/779` 调的是 ticker 的正确实现 `GeneralTickerPatTrainUserValueDao.java:32-39`（`select floorNumber ... group by floorNumber`）；本 KEY 方法 grep 无调用。上轮 P2-8 未修复 |
| PS-P2-12 | `dao/general/key/GeneralKeyPatUserDao.java:73-79` | `GeneralKeyPatService.java:987` 循环内逐用户调用 | `findLastTwoResult` 无 `LIMIT 2`/`setMaxResults`，为取前 2 分数把每学员全部历史科目成绩查出。班级人数×历史训练次数无谓传输 | 对称的同 DAO `findByFistTwoScore(:47-49)` 有 `LIMIT 2`；ticker 版 `GeneralTickerPatTrainUserDao` 亦带 limit。上轮 P2-9 未修复 |
| PS-P2-13 | `dao/TheoryKnowledgeSwfRecordDao.java:52-54` | 统计「已学课件数」 | `find("userId=?1").list().size()` 把整表该用户记录实例化再数，随时间单增且全进一级缓存 | 应 `count(...)`；项目内 `GeneralKeyPatUserValueDao:16-18` 已用 `count`。上轮 P2-10(1/2) 未修复 |
| PS-P2-14 | `dao/TheoryKnowledgeTestUserDao.java:102-104` | 统计「随堂测验次数」 | 同上 `find("userId=?1").list().size()` | 同 DAO `countSwfNum(:107)` 用命名查询，本方法仍全量拉取。上轮 P2-10(2/2) 未修复 |
| PS-P2-15 | `dao/general/ticker/GeneralTickerPatTrainDao.java:48-57` | 若 `countByUserTrainYearScore` 被接线 | `SELECT tu.score`（`GeneralTickerPatTrainUserEntity.score` 为 BigDecimal）声明结果类型 `String.class`，Hibernate 6 结果类型校验不兼容会抛异常；同 DAO `findByUserLastScore(:14-29)` 查同列用 `BigDecimal.class` | 潜伏：仅 `DemoService` 注释代码出现，无活跃调用点。上轮 P2-13 未修复 |
| PS-P2-16 | `dao/TheoryKnowledgeTestDao.java:83-88` | 查无匹配测验 | `.orElse(new TheoryKnowledgeTestEntity())` 返全空实体，调用方无法区分「查到空字段记录」与「没查到」；该空实体 id=null，若被传入 `BaseRepository.save`→`isIdFieldEmpty` 判空→`persist`→凭空插一条全空测验；且用 `.list().stream().findFirst()` 多查全部匹配行 | 同 DAO 其余方法返真实列表；`TheoryKnowledgeTestFallibleDao` 规矩返 `firstResult()`。上轮 P2-15 未修复 |
| PS-P2-17 | `entity/EnteringExerciseWordStockEntity.java:17-19` + `backend/database/project006.sql:20703-20707` | 给该表加写入路径 | 实体 `GenerationType.IDENTITY` 要求 INSERT 省略 id 由库自增回填，但表 `id int(0) NOT NULL` 无 AUTO_INCREMENT/DEFAULT：严格模式报 `Field 'id' doesn't have a default value`，非严格模式写 0 后主键冲突 | 表 `:20704` `id int(0) NOT NULL`；对比 `PostEnteringExerciseWordStockService:141` 有完整增改删。潜伏（当前只有 `findByType` 读）。上轮 P2-6 未修复 |
| PS-P2-18 | `backend/database/project006.sql` vs 实体注解；`application.yml:28`（%dev none） | 从 project006.sql 直接建库并以 %dev/默认（generation:none）或未跑迁移的 %prod 启动 | **实测漂移**：快照缺 5 表 `general_telex_pat`/`_page`/`_user`/`_user_value`（对应 `entity/simulation/telex/*`）、`t_masthead`（对应 `TMastheadEntity`）；缺 2 列 `simulation_router_room.is_start_sign`、`t_post_ticker_tape_train.is_start_sign`。none 下不报错、运行到对应 SQL 才炸：通用电传拍发功能族与 simulation 房间 3 条命名查询（`SimulationRouterRoomEntity.java:27/36/45` 显式 SELECT `is_start_sign`）不可用 | grep 快照：`general_telex_pat`/`t_masthead`/`is_start_sign` 均零命中；`SimulationRouterRoomEntity.java:123`/`PostTickerTapeTrainEntity.java:161` 声明 `isStartSign`。**部分修复**：迁移 01 补齐 + `%prod:88`=validate 兜底 + 双快照演练归零；但快照未回灌、%dev 无自动迁移。`logs/info.log.1`(2026-09-07 16:04) 实证 validate 对未迁移库 `missing table [general_telex_pat]` 启动失败。上轮 P1-8 / 汇总§3-7 |
| PS-P2-19 | `dao/TheoryKnowledgeSwfRecordDao.java:63-75`（`:64`） | `ComprehensiveService.java:89` 统计学习时长 | `entityManager.createQuery(HQL)` 中用 MySQL 私有函数 `TIME_TO_SEC`/`TIMEDIFF`（Hibernate 6 HQL 函数注册表默认无，项目无 FunctionContributor），且 `joinTime/exitTime` 为 String 列。若解析失败抛 SemanticException→综合统计接口异常（`Optional.ofNullable` 只包返回值不拦异常） | 旧注解 `nativeQuery=true`(:62) 表明本是原生 SQL，迁移改驼峰字段却仍用 createQuery；同表 `count_study_time` 命名原生查询(:93) 为正确对照。**待运行验证**：若函数确未注册则升级 P1。上轮 P1-4 未修复 |
| PS-P2-20 | `dao/DeviceDescriptionDao.java:19-22` | 设备类型下无设备时删除该类型 | `delete("deviceId in (?1)", deviceIdList)` 无空集合防护；`DeviceTypeService.java:72` 传可能为空的 `deviceIdList`。空 IN 渲染 `in ()` 在 MySQL 语法错误（最好情况静默恒假） | 同族 `MilitaryTermDataDao` 已加判空+回归测试，本处未推广。上轮 P2-7（可达分支之一）未修复 |
| PS-P2-21 | `dao/TheoryKnowledgeExamUserDao.java:165-172` | 用户当月无考试记录时统计成绩分布 | `where eu.id in (?1)` 无空集合防护；`TheoryKnowledgeService.java:523-524` 传 `examUsers.stream().map(getId).toList()`（可空）。空 IN 同 PS-P2-20 后果 | 上轮 P2-7（可达分支之二）未修复 |

---

## 4. P3

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| PS-P3-01 | `dao/TheoryKnowledgeSwfRecordDao.java:88-90` | 若被调用 | `countMonthStudyTimeAndSwfNum(...)` 签名完整却 `return null`（迁移未完成的死桩），参数名拼错 `yaer`；下次调用者直接 NPE 而非「未实现」提示 | 上方 `:83-87` 保留整段被注释原生 SQL。全项目无调用点。上轮 P3-1 未修复 |
| PS-P3-02 | `dao/general/ticker/GeneralTickerPatTrainPageDao.java:16-18` | 调用该查询 | `find("trainId=?1 order by floorNumber desc limit 1").firstResult()`：`firstResult()` 内部已 `setMaxResults(1)` 与 HQL `limit 1` 重复（Hibernate 6 行为不确定）；方法名 `SortDesc` 但 order by 无 `sort desc` | 同名 `PostTelegramTrainFloorContentDao:46-48` 无 limit。上轮 P3-2 未修复 |

---

## 5. 上一轮遗留核销（`docs/reviews/2026-08-26-persistence-review.md` 28 条 + 汇总§3-7）

| 上轮编号 | 上轮结论 | 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| P0-1 | maxSort=null 拆箱 NPE，首次 Excel 导入必崩 | **已修复** | `MilitaryTermDataService.java:118/230/245` 三处均 `maxSort==null?1:maxSort+1`；DAO `:35-39` 仍返 nullable Integer（残留气味，无崩溃） |
| P0-2 | findTwoPage 过滤字段错+Integer 绑 String 主键 | **已修复** | `GeneralKeyPatPageDao.java:26-29` 改为 `trainId=?1 and (pageNumber=1 or 2)`，参数 Integer；回归测试 `PatPageFindTwoPageDaoTest`。注：泛型 Integer 仍错，另记 PS-P2-05 |
| P1-1 | GeneralTelexPatPageDao.findTwoPage 字段错 | **已修复** | `GeneralTelexPatPageDao.java:22-25` 改为 `trainId=?1`；测试覆盖 |
| P1-2 | TickerTapeTrainDao.lastTrain 升序取最早 | **未修复** | `TickerTapeTrainDao.java:110` 仍 `ascending()` → PS-P1-01 |
| P1-3 | sortSubtract 实为加法 | **已修复** | `MilitaryTermDataDao.java:47-49` 改为 `sort=sort-1 ... and sort>?2` |
| P1-4 | HQL 里用 TIME_TO_SEC/TIMEDIFF | **未修复** | `TheoryKnowledgeSwfRecordDao.java:64` 仍 createQuery + 私有函数 → PS-P2-19（待运行验证） |
| P1-5 | deleteThroyKnowledgeById 只删主表留 5 子表孤儿 | **未修复（服务层，属他分片）** | `TheoryKnowledgeService.java:388-391` 仍 `knowledgeDao.deleteById(id)` 单表；非本片目标，不计入本片计数 |
| P1-6 | findPage 用 getResultList().size() | **未修复** | `SpecificationExecutor.java:87` → PS-P2-01 |
| P1-7 | 22 张 MyISAM 参与事务无原子性 | **部分修复** | 迁移 02 + 演练已备并绿；快照仍 MyISAM、validate 不校验引擎、%dev none → PS-P1-02 |
| P1-8 | 实体/schema 脱节，无迁移无启动校验 | **部分修复** | 迁移 01 + `%prod:88` validate + 双快照演练；快照未回灌、%dev none → PS-P2-18；`info.log.1` 今日仍实证 validate 缺表失败 |
| P1-9 | save(Iterable) 无分批/flush/clear | **未修复** | `BaseRepository.java:32-40` → PS-P2-02 |
| P2-1 | RoleDao.findRoleByUserId getSingleResult | **未修复** | `RoleDao.java:17` → PS-P2-09 |
| P2-2 | UserDao.findAllUser REGEXP | **未修复** | `UserDao.java:33` → PS-P2-10 |
| P2-3 | nativeQuery 泄漏 Session、永返 null、空 catch | **部分修复** | catch 已改记全 `e`(:136/:142)；Session 泄漏(:105-107)+getRow()==0(:116) 仍在 → PS-P2-04 |
| P2-4 | ToolUtil.isIdFieldEmpty value=="" | **未修复** | `ToolUtil.java:84` → PS-P2-03 |
| P2-5 | 4 处 DAO 泛型 ID 与实体不符 | **未修复（4/4）** | GeneralKeyPatPageDao:11、GeneralTickerPatTrainPageDao:10、EnteringExerciseWordStockDao:10、PostTrainGlobalRuleDao:8 → PS-P2-05..08 |
| P2-6 | EnteringExerciseWordStock IDENTITY 但表无 AUTO_INCREMENT | **未修复** | 实体 `:18` IDENTITY / 表 `:20704` int 无自增 → PS-P2-17 |
| P2-7 | in(?1) 空集合无防护（族） | **部分修复** | `MilitaryTermDataDao:21-32` 已加判空+测试；`DeviceDescriptionDao:20`、`TheoryKnowledgeExamUserDao.countExamPass:165` 仍无防护 → PS-P2-20/21；其余 TheoryKnowledgeQuestionDao/TestPaperDao 等无空调用点，潜伏未计 |
| P2-8 | GeneralKeyPatUserValueDao 用 page_number | **未修复** | `:21` 仍 `group by page_number`（潜伏，无调用）→ PS-P2-11 |
| P2-9 | findLastTwoResult 无 LIMIT | **未修复** | `GeneralKeyPatUserDao.java:73-79` → PS-P2-12 |
| P2-10 | 两处 .list().size() 计数 | **未修复（2/2）** | `TheoryKnowledgeSwfRecordDao:53`、`TheoryKnowledgeTestUserDao:103` → PS-P2-13/14 |
| P2-11 | clearByTranId 双引号包 HQL 字符串 | **已修复** | `PostTelegramTrainFloorContentDao.java:24` 改单引号 `'[]'`；测试 `PostTelegramTrainFloorContentDaoTest` |
| P2-12 | 聚合查询带非聚合 ORDER BY | **已修复** | `PostTelegramTrainFloorContentDao.java:62-66` 已删 `order by`；测试覆盖 |
| P2-13 | BigDecimal 列查进 String.class | **未修复** | `GeneralTickerPatTrainDao.java:48-53`（潜伏）→ PS-P2-15 |
| P2-14 | 删题库分级/分类留悬空引用 | **未修复（服务层，属他分片）** | `TheoryKnowledgeQuestionService.java:160-162`、`TheoryKnowledgeClassifyService.java:67-68` 仍单表删；非本片目标，不计入本片计数 |
| P2-15 | 查不到返空实体 | **未修复** | `TheoryKnowledgeTestDao.java:87` → PS-P2-16 |
| P3-1 | countMonthStudyTimeAndSwfNum return null | **未修复** | `TheoryKnowledgeSwfRecordDao.java:89` → PS-P3-01 |
| P3-2 | limit 1 与 firstResult() 重复 | **未修复** | `GeneralTickerPatTrainPageDao.java:17` → PS-P3-02 |
| 汇总§3-7 | info.log.1 报 5 张缺表 | **未修复（残留，同 P1-8）** | `logs/info.log.1`(2026-09-07 16:04) `SchemaManagementException: missing table [general_telex_pat]`（validate 遇首个缺表即停，与 5 表清单一致）→ PS-P2-18 |

### DAO 测试覆盖缺口（对照 `src/test/java/com/nip/dao/*.java` 三个测试）

现有 3 个 DAO 测试覆盖：`PatPageFindTwoPageDaoTest`（P0-2/P1-1，key+telex）、`MilitaryTermDataDaoTest`（P2-7 空/非空 IN，仅 MilitaryTermDataDao）、`PostTelegramTrainFloorContentDaoTest`（P2-11/P2-12）。缺口：
- **无** lastTrain 排序方向回归测试 → PS-P1-01 长期漏网（本可一测即中）。
- **无** `BaseRepository.save` persist/merge 语义测试（PS-P2-03/PS-P2-16 的插空行链路）。
- **无** `SpecificationExecutor.findPage` 计数正确性/内存测试（PS-P2-01）。
- **无** DAO 泛型 ID 类型一致性测试（PS-P2-05..08，编译期即可拦）。
- **无** `countStudyTimeByUserId` HQL 函数可执行性测试（PS-P2-19）。
- P2-7 防护测试仅覆盖 `MilitaryTermDataDao`，未覆盖 `DeviceDescriptionDao`/`countExamPass`（PS-P2-20/21）。

---

## 6. 待运行验证清单

1. **PS-P2-19**：Hibernate 6 MySQL 方言下 HQL 是否拒绝 `TIME_TO_SEC`/`TIMEDIFF`（是否抛 SemanticException）。若拒绝→`ComprehensiveService` 综合统计接口 500，升级 P1；`joinTime/exitTime` 为 String 亦依赖隐式转换。
2. **PS-P2-11**：`group by page_number`（库列名）在 HQL 的属性解析；以及 `find` 返完整实体 + 单列 group by 在 `ONLY_FULL_GROUP_BY` 下是否被拒。需先接线才可触发。
3. **PS-P2-04**：`nativeQuery` 的 Session/连接实际泄漏（需构造调用点压测）与 `getRow()==0` 恒真返 null。
4. **PS-P2-10**：Hibernate 6 HQL 是否接受 `REGEXP` 中缀操作符；非锚定子串误命中在真实 ID 上的表现。
5. **PS-P2-20/21**：空集合传入 `in (?1)` 渲染 `in ()` 的实际结果（MySQL 语法错 vs 静默恒假）。
6. **PS-P2-09**：无角色用户触发 `getSingleResult` 的 `NoResultException`（取决于是否存在未派角色用户）。
7. **PS-P2-15**：`SELECT tu.score`(BigDecimal) 声明 `String.class` 在 Hibernate 6 的结果类型校验行为。
8. **PS-P1-02**：目标环境是否已执行迁移 02（`information_schema.tables.engine` 全 InnoDB）；validate 不校验引擎，须运维带外确认。

---

## 附录：已接受安全风险（内网口径，不计入问题计数）

1. **口令明文等值查询**：`dao/UserDao.java:50-52 findUserEntityByPassword`；`UserEntity.password` 为普通 varchar，无加盐哈希。
2. **身份证号明文存储且可检索**：`UserEntity.idCard`；`UserDao.java:46-48 findUserEntityByIdCard`、`:54-57 existsUserEntitiesByIdCardOrUserAccount` 均按明文匹配。
3. **token 明文入库并可反查用户**：`UserEntity.token`；`UserDao.java:78-80 findUserEntityByToken`、`:60-64 existsUserByTokenAndDeviceId`；token 由 `AESUtil.encrypt(account+"-"+password+"-"+deviceId)` 生成，可解密还原账号口令。
4. **`UserDao.java:33` REGEXP 参数拼接的安全侧面**：命名参数绑定不构成经典注入，但正则内容由调用方控制，恶意正则可致回溯爆炸（功能正确性已记 PS-P2-10）。
5. **MyISAM AUTO_INCREMENT 主键值可预测**：22 张 MyISAM 表主键为自增整数（如快照 `general_key_pat_page AUTO_INCREMENT=12425`），横向遍历风险；功能侧回滚缺陷已记 PS-P1-02。

---

### 附：本次取证方式
- 所有 file:line 均来自当前 HEAD（b9b9f22）源文件实读；schema 漂移为「实体注解 vs `backend/database/project006.sql`」本次实测（grep `general_telex_pat`/`t_masthead`/`is_start_sign` 零命中、`ENGINE = MyISAM` 命中 22+ 张、`id varchar(64)` 主键已对齐），未沿用旧文档数字。
- 标 `待运行验证` 者为「写法确定有问题、失败模式依赖 Hibernate 6/MySQL sql_mode 运行时行为」，本片不跑构建/测试。
- `logs/info.log`（空）、`info.log.2`（2026-08-26 端口占用，无关）；`info.log.1`（2026-09-07 16:04，validate 缺表 `general_telex_pat` 启动失败）为漂移现存的直接证据。
