# 持久层审查报告（dao/ + entity/ + 持久层基础设施）

**结论：共发现 28 个问题 —— P0 2 个、P1 9 个、P2 15 个、P3 2 个。**

审查范围：`src/main/java/com/nip/dao/`（84 文件）、`src/main/java/com/nip/entity/`（85 文件，含 simulation 子包），以及 DAO 直接继承的持久层基础设施 `common/repository/BaseRepository.java`、`common/specification/SpecificationExecutor.java`（已与 CommonBuild 确认归属本报告）。表结构核对以 `docs/database/project006.sql`（当前 dump，36447 行）为准，`project006-base.sql` 为旧基线，两者主键类型已不同，**不能用 base 版本下结论**。

三条需要先说清楚的全局事实，它们决定了下面很多问题的严重程度：

1. `src/main/resources/application.yml:32` 配置 `generation: none`，Hibernate 启动时**不做 schema 校验**。实体与表不一致不会在启动时暴露，只会在跑到对应 SQL 时炸。
2. 项目**没有任何 JPA 关联映射**——全 85 个实体里 `@OneToMany/@ManyToOne/@JoinColumn/cascade/orphanRemoval` 零命中。所有外键都是裸标量列，级联删除必须手写。因此「删主记录不删子记录」不是配置疏漏，而是每个删除路径都要人工保证。
3. 当前 dump 里有 **22 张表是 MyISAM 引擎**（不支持事务），而它们全部在 `@Transactional` 方法里被写入。

---

## P0

### P0-1 `findByParentIdMaxSort` 返回 null，调用方直接拆箱 —— 军语 Excel 批量导入必崩

**位置**：`src/main/java/com/nip/dao/MilitaryTermDataDao.java:29-33`（DAO 侧），`src/main/java/com/nip/service/MilitaryTermDataService.java:219-220` 与 `:232-233`（崩溃点）

**现象**：
```java
// MilitaryTermDataDao.java:29-33
public Integer findByParentIdMaxSort(String parentId) {
  return entityManager.createQuery("select max(sort) from t_military_term_data where parentId =:parentId",
                                   Integer.class).setParameter("parentId", parentId).setMaxResults(1).getSingleResult();
}
```
`select max(...)` 在无匹配行时返回**一行 NULL**（不是抛 NoResultException），所以该方法会返回 `null`。

同一个 service 里两种写法并存，本身就是证据：
- `MilitaryTermDataService.java:123-124` 写对了：`entity.setSort(maxSort == null ? 1 : maxSort + 1);`
- `MilitaryTermDataService.java:219-220` 没判空：`Integer maxSort = ...findByParentIdMaxSort("0"); ...setSort(maxSort + 1)`
- `MilitaryTermDataService.java:232-233` 没判空：`Integer maxSort = ...findByParentIdMaxSort(entity.getId()); ...setSort(maxSort + 1)`

**影响**：
- 第 219 行：`t_military_term_data` 里没有 `parentId = '0'` 的行时（全新部署、或顶级分类被删空），`maxSort` 为 null，`maxSort + 1` 拆箱 NPE。**首次 Excel 导入军语必崩。**
- 第 232 行：父分类存在但当前没有子级时（子级被 `delete()` 删过，见 `MilitaryTermDataService.java:137-148` 允许删叶子节点），`max(sort)` 对空集返回 null，同样 NPE，整批导入回滚。

**建议修复方向**：在 DAO 内部收口，把 `findByParentIdMaxSort` 改成 `Optional<Integer>` 或直接返回 `resultList` 判空后给 0，不要把可空的装箱 Integer 泄漏给调用方；同时统一 `:123`、`:219`、`:232` 三处的取值逻辑。

---

### P0-2 `GeneralKeyPatPageDao.findTwoPage` 过滤字段用错 + 实参类型与实体主键类型不符

**位置**：`src/main/java/com/nip/dao/general/key/GeneralKeyPatPageDao.java:26-28`

**现象**：
```java
public List<GeneralKeyPatPageEntity> findTwoPage(Integer id) {
  return find("id = ?1 and (pageNumber = 1 or pageNumber =2)", Sort.by("pageNumber").ascending(), id).list();
}
```
两处都错：
1. **字段错**：调用方传的是训练 ID。`GeneralKeyPatService.java:833` 明确写 `trainPageDao.findTwoPage(param.getTrainId())`。但查询过滤的是 `id`（该页记录自己的主键），不是 `trainId`。同族的正确写法见 `PostTelegraphKeyPatTrainPageDao.java:34-36`：`find("trainId =?1 and (pageNumber = 1 or pageNumber = 2) order by pageNumber,sort", trainId)`。
2. **类型错**：`GeneralKeyPatPageEntity.java:20-22` 主键是 `@GeneratedValue(strategy = GenerationType.UUID) private String id;`（表侧 `docs/database/project006.sql:73` 起的 `general_key_pat_page.id` 为 `varchar(64)`，与实体一致）。而方法签名是 `Integer id`，把 Integer 绑到 String 类型的路径上。

**影响**：`GeneralKeyPatService.patDetail`（`GeneralKeyPatService.java:826-881`）**没有 try/catch**，异常直接抛到 controller。要么 Hibernate 在参数绑定阶段抛类型不匹配异常导致「通用手键拍发-拍发详情」接口 500；要么参数被强转成 `"123"` 这类字符串，与 UUID 主键永远匹配不上，返回空列表。后者会让 `GeneralKeyPatService.java:869`（学员未完成时 `v.setContent(PojoUtils.convert(twoPage, ...))`）**恒返回空报底内容**。无论哪条分支，这个功能 100% 不可用，不是边界情况。

**建议修复方向**：改成 `find("trainId = ?1 and (pageNumber = 1 or pageNumber = 2)", Sort.by("pageNumber").ascending().and("sort"), trainId)`，参数类型保持 `Integer`（`GeneralKeyPatPageEntity.trainId` 就是 Integer）。同时修正 `GeneralKeyPatPageDao` 的仓储泛型（见 P2-5）。

---

## P1

### P1-1 `GeneralTelexPatPageDao.findTwoPage` 同类字段错误

**位置**：`src/main/java/com/nip/dao/general/telex/GeneralTelexPatPageDao.java:22-24`

**现象**：
```java
public List<GeneralTelexPatPageEntity> findTwoPage(String id) {
  return find("id = ?1 and (pageNumber = 1 or pageNumber =2)", Sort.by("pageNumber").ascending(), id).list();
}
```
`GeneralTelexPatPageEntity.java` 的 `id` 是 UUID 字符串主键、`trainId` 是训练 ID，两者都是 String，所以不会报类型错，但 `GeneralTelexPatService.java:279` 传进来的是 `param.getTrainId()`，用它去匹配页记录自己的主键，两个不同表的 UUID 永远不会相等。

**影响**：`GeneralTelexPatService.java:309`（学员 `isFinish != 1` 分支）用 `twoPage` 作为返回内容，结果**恒为空数组**。教员查看未完成学员的电传拍发详情时看不到任何报底。该方法外层有 try/catch（`:321-324`）但只是记日志后重新抛出，不改变结果为空的事实。

**建议修复方向**：同 P0-2，把 `id = ?1` 改成 `trainId = ?1`。

---

### P1-2 `TickerTapeTrainDao.lastTrain` 用升序，返回的是最早一次而不是最后一次训练

**位置**：`src/main/java/com/nip/dao/TickerTapeTrainDao.java:109-111`

**现象**：
```java
public TickerTapeTrainEntity lastTrain(String id, Integer type) {
  return find("userId = ?1 and type = ?2", Sort.by("createTime").ascending(), id, type).firstResult();
}
```
同一项目内所有「查最后一次训练」的 DAO 全部用降序：
- `TelegramTrainDao.java:55-57`：`order by createTime desc`
- `EnteringExerciseDao.java:55-57`：`ORDER BY createTime desc`
- `TelexPatTrainDao.java:39-41`：`order by createTime desc`
- `TelegraphKeyPatSyntheticalDao.java:34-36`：`order by createTime desc`

只有这一处是 `ascending()`，配合 `firstResult()` 取到的是该用户该类型**最早**的一条训练记录。

**影响**：两条业务路径都错：
1. `TickerTapeTrainService.java:73-80`：新建训练前查「上一次训练」，状态为「未开始」就 `deleteById`（`:77`），状态为「暂停」就置为「已完成」并做统计（`:79-80`）。实际操作的是这个用户历史上最早的那条记录 —— 会误删或误结算多年前的训练数据。
2. `TickerTapeTrainService.java:222`（对应接口 `TickerTapeTrainController.java:122`「查询最后一次训练状态」）返回最早一次训练，前端断点续训会恢复到错误的记录。

**建议修复方向**：改为 `Sort.by("createTime").descending()`。

---

### P1-3 `MilitaryTermDataDao.sortSubtract` 实现成了加法，与 `updateSort` 完全相同

**位置**：`src/main/java/com/nip/dao/MilitaryTermDataDao.java:35-43`

**现象**：
```java
@Transactional
public void updateSort(String parentId, Integer sort) {
  update("sort = sort + 1 where parentId = ?1 and sort>=?2", parentId, sort);   // :37
}

@Transactional
public void sortSubtract(String parentId, Integer sort) {
  update("sort = sort + 1 where parentId = ?1 and sort>=?2", parentId, sort);   // :42  ← 与上面一字不差
}
```
方法名是 subtract（减），SQL 写的是 `+ 1`。同类的 `upSwapDown`（`:51-53`）正确使用了 `sort = sort - 1`，说明作者知道减法怎么写。

**影响**：`MilitaryTermDataService.java:145` 在删除军语条目时调用它，本意是把后续兄弟节点的 sort 往前挪补上空位，实际是**再往后推一位**。每删一条，后续节点 sort 就凭空 +1，同时删除本身留下一个空洞。配合 `MilitaryTermDataService.java:123-124` 的 `sort = max(sort) + 1` 分配策略，反复增删会让 sort 值无界膨胀且空洞越来越多，`downSwapUp/upSwapDown` 依赖的 `sort >= / <=` 区间判断随之失真。数据一旦写坏无法自动恢复。

**建议修复方向**：`sortSubtract` 改为 `update("sort = sort - 1 where parentId = ?1 and sort > ?2", parentId, sort)`（注意删除场景应该是 `>` 而不是 `>=`，因为被删那条自己的 sort 不该参与）。另外确认 `updateSort` 是否还有调用方，当前全项目搜不到调用点，疑似死代码。

---

### P1-4 把 MySQL 原生函数写进了 HQL —— 学习时长统计必然失败

**位置**：`src/main/java/com/nip/dao/TheoryKnowledgeSwfRecordDao.java:49-60`

**现象**：
```java
//@Query(value = "select Round(sum(TIME_TO_SEC(TIMEDIFF(exit_time,join_time)))/3600,2) t from ... ",nativeQuery = true)   // :47
public BigDecimal countStudyTimeByUserId(String userId) {
  List<BigDecimal> resultList = entityManager.createQuery(          // :50 ← createQuery = HQL，不是原生 SQL
      "select IFNULL(Round(sum(TIME_TO_SEC(TIMEDIFF(exitTime,joinTime)))/3600,2),0) t "
    + "from t_theory_knowledge_swf_record where userId = ?1", BigDecimal.class)
```
第 47 行保留的旧注解 `nativeQuery = true` 明确说明这条 SQL 原本是原生查询，从 Spring Data 迁到 Quarkus 时字段名改成了驼峰（`exitTime`/`joinTime`），但 API 用的是 `createQuery`（HQL），**函数名没跟着改**。`TIME_TO_SEC` / `TIMEDIFF` 是 MySQL 专有函数，Hibernate 6 的 HQL 函数注册表里没有它们，项目也没有任何 `FunctionContributor` / `MetadataBuilderContributor`（全项目零命中）。

此外 `TheoryKnowledgeSwfRecordEntity.java:49-50` 的 `joinTime`/`exitTime` 都是 `String` 类型，用时间函数处理字符串本身也依赖 MySQL 的隐式转换。

**影响**：`ComprehensiveService.java:87` 调用它统计「学习时长」，走到就会抛 `SemanticException`（未知函数），综合统计页整块数据不可用。[INFERENCE] 具体异常类型未经运行验证，但「HQL 里用未注册的方言私有函数」这一点是确定的。同表的 `count_study_time` 命名查询（`TheoryKnowledgeSwfRecordEntity.java:21-28`）用 `@NamedNativeQuery` + snake_case 列名，是正确写法，可直接对照。

**建议修复方向**：改用 `entityManager.createNativeQuery(...)` 并把字段名换回 `exit_time`/`join_time`，或者直接复用已有的 `count_study_time` 命名原生查询。

---

### P1-5 删除理论知识只删主表，5 张子表全部留下孤儿数据

**位置**：`src/main/java/com/nip/service/TheoryKnowledgeService.java:367-370`

**现象**：
```java
public Response<Void> deleteThroyKnowledgeById(String id) {
  knowledgeDao.deleteById(id);
  return ResponseResult.success();
}
```
`t_theory_knowledge` 的子表（全部以裸 `knowledgeId` 列关联，无 FK 无级联）：
- `TheoryKnowledgeSwfEntity.java:26` `knowledgeId` —— 课件
- `TheoryKnowledgeTestEntity.java:28` `knowledgeId` —— 随堂测验
- `TheoryKnowledgeTestContentEntity.java:27` `knowledgeId` —— 测验题目
- `TheoryKnowledgeTestUserEntity.java:44` `knowledgeId` —— 学员答卷
- `TheoryKnowledgeSwfRecordEntity.java:46` `knowledgeId` —— 学习记录

DAO 里现成的清理方法都存在且在别处被用过 —— `TheoryKnowledgeSwfDao.java:26-28 deleteAllByKnowledgeId` 在 `TheoryKnowledgeService.java:231`（更新课件时）被调用，`TheoryKnowledgeTestContentDao.java:62-64 deleteAllByKnowledgeId` 同理。说明清理动作是已知需求，删除路径上漏了。

**影响**：删一个知识点后，它的课件、测验、题目、学员答卷、学习记录全部残留且再也无法通过界面访问或清理。这些残留数据会继续被统计口径捞出来 —— 例如 `TheoryKnowledgeSwfRecordDao.java:38-40 countByUserId`（统计已学课件数）不带任何有效性过滤，学员的「已学课件数」会包含已删除知识点下的记录，越删越虚高。

**建议修复方向**：把 `deleteThroyKnowledgeById` 改成事务方法，按 `swfRecord → testUser → testContent → test → swf → knowledge` 顺序逐层清理。

---

### P1-6 分页查询用 `getResultList().size()` 统计总数 —— 每次分页都全表加载

**位置**：`src/main/java/com/nip/common/specification/SpecificationExecutor.java:79-95`

**现象**：
```java
public PageInfo<T> findPage(@Nullable Specification<T> specification, int currentPage, int pageSize) {
  ...
  List<T> resultList = entityManager.createQuery(specification.toPredicate(root, query, builder))
      .setFirstResult(currentPage * pageSize).setMaxResults(pageSize).getResultList();
  int total = entityManager.createQuery(specification.toPredicate(root, query, builder)).getResultList().size();  // :88
  ...
}
```
第 88 行没有用 `count(*)`，而是把**满足条件的全部实体查出来再取 size()**。第二次调用 `specification.toPredicate(root, query, builder)` 还复用了同一个 `CriteriaQuery`/`Root` 对象，语义依赖 Specification 实现是否幂等。

**影响**：4 个训练列表接口全部走这条路径 —— `PostTelexPatTrainService.java:156`、`GeneralKeyPatService.java:290`、`GeneralTelexPatService.java:179`、`GeneralTickerPatService.java:338`。每翻一页都会把该用户全部历史训练记录完整实例化进持久化上下文一次；训练记录随使用时间线性增长，内存与响应时间同步恶化，且这些实体全部进入一级缓存直到事务结束。

**建议修复方向**：单独构造一个 `CriteriaQuery<Long>` 用 `builder.count(root)` 取总数；同时为 count 查询用独立的 `Root`，避免两次 `toPredicate` 作用在同一个 query 对象上。

---

### P1-7 22 张 MyISAM 表参与 `@Transactional` 事务，删除/新建训练没有原子性

**位置**：`docs/database/project006.sql`（引擎声明）+ 各 service 的删除方法

**现象**：当前 dump 里以下表是 `ENGINE = MyISAM`（MyISAM 不支持事务）：
```
general_key_pat, general_key_pat_page, general_key_pat_train_more, general_key_pat_user,
general_key_pat_user_value, general_key_pat_user_value_resolver, general_ticker_pat,
general_ticker_pat_train_page, general_ticker_pat_train_user, general_ticker_pat_train_user_value,
hand_key_err_log, simulation_router_room_page, simulation_router_room_page_value,
t_post_telegram_train_content_value, t_post_telegraph_key_pat_train_more,
t_post_telegraph_key_pat_train_page, t_post_telegraph_key_pat_train_page_value,
t_post_telex_pat_train_page, t_post_telex_pat_train_page_value,
t_post_ticker_tape_train_page, t_post_ticker_tape_train_page_value, t_ticker_tape_train_stage_setting
```
（可核对 `docs/database/project006.sql:48` general_key_pat、`:73` general_key_pat_page、`:125` general_key_pat_user_value、`:319` simulation_router_room_page 等处的 `) ENGINE = MyISAM`。）

对应的事务方法：
- `GeneralKeyPatService.java:228-237` 标了 `@Transactional(rollbackOn = Exception.class)`，依次删 `general_key_pat_user_value`、`general_key_pat_user_value_resolver`、`general_key_pat_train_more`、`general_key_pat_page`、`general_key_pat_user`、`general_key_pat` —— 六张表全是 MyISAM。
- `PostTelexPatTrainService.java:305-309`：先删 MyISAM 的 `t_post_telex_pat_train_page` / `_page_value`，最后删 InnoDB 的 `t_post_telex_pat_train`（`docs/database/project006.sql:28492`，`ENGINE = InnoDB`）—— **同一事务里混用两种引擎**。
- `SimulationRouterRoomService` / `SimulationReceptRoomService` / `SimulationReportRoomService` 的 `delete`（如 `SimulationReportRoomService.java:199-206`）同样混用 MyISAM 的 page/page_value 与 InnoDB 的 `simulation_router_room`（`docs/database/project006.sql:283`）。
- 新建路径同理：`GeneralKeyPatService.java:123-226` 的 `add` 在同一事务里先存训练主记录、再存参训人员、最后批量生成报底页；`GeneralTickerPatService.java:209` 的 `cableFloor.subList(0, totalPage)` 在 `cableFloor.size() < totalPage` 时会抛 `IndexOutOfBoundsException`。

**影响**：MyISAM 表上的写入**立即生效且无法回滚**。任一步失败后，子表已删/已写的行永久留下：删除训练时主记录删失败 → 报底和答卷已消失但训练还在；新建训练时报底生成失败 → 数据库里留下一个没有任何报底的训练和一批参训人员记录。`rollbackOn = Exception.class` 给的是虚假的安全感。

**建议修复方向**：把这 22 张表统一 `ALTER TABLE ... ENGINE=InnoDB`（MyISAM 在 MySQL 8 已是历史遗留，且这些表都不需要 MyISAM 的任何特性）。在改造完成前，删除/新建路径不能依赖事务回滚，需要显式的补偿清理。

---

### P1-8 实体与仓库内 schema 文件严重脱节，且无迁移工具、无启动校验

**位置**：`src/main/resources/application.yml:32`、`docs/database/*.sql`、多个实体

**现象**：以当前 dump `docs/database/project006.sql` 为基准逐表比对，发现三类脱节：

1. **5 张实体声明的表在两份 dump 里都不存在**（已对 `project006.sql` 与 `project006-base.sql` 的全部 `CREATE TABLE` 做过集合差）：
   `general_telex_pat`、`general_telex_pat_page`、`general_telex_pat_user`、`general_telex_pat_user_value`（通用电传拍发整个功能族）、`t_masthead`。
2. **`simulation_router_room.is_start_sign` 列在两份 dump 里都不存在**（对 `docs/database/` 整目录 grep `is_start_sign` 零命中），但 `SimulationRouterRoomEntity.java:123` 声明了 `private Integer isStartSign = 1;`，且 3 条命名原生查询显式 SELECT 该列：`SimulationRouterRoomEntity.java:27`、`:36`、`:45`。表实际列见 `docs/database/project006.sql:283-296`，共 11 列，无此列。
3. **两份 dump 之间主键类型已经漂移**：`general_key_pat_page.id` 在 `project006-base.sql:74` 是 `int(0) AUTO_INCREMENT`，在 `project006.sql:73` 起是 `varchar(64)`；`general_ticker_pat_train_page.id` 同样从 int 变成 varchar(64)。

同时 `application.yml:32` 是 `generation: none`，项目里没有 Flyway / Liquibase（无 migration 目录、无相关依赖引用）。

**影响**：schema 与代码是两条独立演进的线，没有任何自动对账机制。如果线上库真如 dump 所示，则 5 张表对应的功能和 simulation 房间列表 3 个接口全部不可用；如果线上库其实有这些表和列，那么仓库里的 dump 就是失效文档，任何按它搭建的新环境都会立刻炸。**这两种情况都必须在合并前确认清楚，不能靠猜。**

**建议修复方向**：临时把 `generation` 改成 `validate` 启动一次，用 Hibernate 自己把差异全部打出来；然后引入 Flyway 把 schema 纳入版本管理，dump 文件降级为参考资料。

---

### P1-9 `BaseRepository.save(Iterable)` 无分批、无 flush/clear，且每条实体走一次反射

**位置**：`src/main/java/com/nip/common/repository/BaseRepository.java:33-39`，配合 `src/main/java/com/nip/common/utils/ToolUtil.java:71-94`

**现象**：
```java
// BaseRepository.java:33-39
@Transactional
public <S extends T> List<S> save(Iterable<S> entities) {
  Assert.notNull(entities, "Entities must not be null!");
  List<S> result = new ArrayList<>();
  for (S entity : entities) { result.add(save(entity)); }   // 逐条，无 flush/clear
  return result;
}
```
每次 `save(entity)` 都调 `ToolUtil.isIdFieldEmpty(entity)`（`BaseRepository.java:17`），而该方法每次都重新走 `clazz.getDeclaredFields()` 遍历 + `clazz.getMethod(getterName)` + `invoke`（`ToolUtil.java:78-83`），**没有任何缓存**。且走的是 `merge()` 而非 `persist()` 的路径会额外触发一次 SELECT。

批量调用点（都在一个事务里一次性提交上千条）：
- `GeneralKeyPatService.java:220`：固定报模式下 `pageEntities` 数量 = `totalNumber/100 × 每页组数`，`totalNumber` 由前端传入无上限。
- `GeneralKeyPatService.java:200`、`:268`、`SimulationRouterRoomService.java:116`/`:133`、`SimulationReportRoomService.java:111`、`GeneralTickerPatService.java:203`/`:225`、`GeneralTelexPatService.java:170` 同型。
- `GeneralKeyPatService.java:941-944` 一次连存 4 批（page/user/userValue/more）。

`application.yml:27` 虽然配了 `statement-batch-size: 100`，但由于每条实体都可能走 merge（先 SELECT 再 INSERT），JDBC 批处理会被 SELECT 打断而无法成批。

**影响**：持久化上下文无界增长，Hibernate 每次 flush 的脏检查是 O(N) 遍历，N 条实体累计 O(N²)；再叠加 N 次反射查找与 N 次 merge SELECT。生成一个万级报底的训练会出现明显卡顿甚至 OOM。

**建议修复方向**：`save(Iterable)` 内按 `statement-batch-size` 分批 `flush()` + `clear()`；`ToolUtil.isIdFieldEmpty` 的 `@Id` getter 用 `ConcurrentHashMap<Class<?>, Method>` 缓存；新增场景直接用 `persist()` 绕开 merge 的探测 SELECT。

---
## P2

### P2-1 `RoleDao.findRoleByUserId` 用 `getSingleResult()` —— 没有角色的用户无法登录且报错无意义

**位置**：`src/main/java/com/nip/dao/RoleDao.java:14-18`

**现象**：
```java
public RoleEntity findRoleByUserId(String userId) {
  return entityManager.createQuery(
    "select r FROM t_role as r LEFT JOIN t_user_role as ur on ur.roleId = r.id LEFT JOIN t_user as u on u.id = ur.userId "
      + "where u.id=:id", RoleEntity.class).setParameter(ID, userId).setMaxResults(1).getSingleResult();
}
```
虽然写了 LEFT JOIN，但 `where u.id=:id` 会把未关联的行全部过滤掉。用户在 `t_user_role` 里没有记录时结果集为空，`getSingleResult()` 抛 `NoResultException`。

**影响**：`UserService.java:390` 在登录流程里调用它。异常被 `UserService.java:398-401` 的宽泛 catch 吞掉，返回统一的 `DATA_EXCEPTION`。结果是「用户已创建但还没分配角色」时登录失败，且错误信息完全看不出原因。另一处 `UserService.java:93` 同样调用。

**建议修复方向**：改成 `getResultList()` 后取首个元素返回 null（与项目里 `PostTelexPatTrainPageValueDao.java:21-24` 的写法一致），由调用方决定「无角色」的业务语义。

---

### P2-2 `UserDao.findAllUser` 用 `REGEXP` 拼接 ID 列表

**位置**：`src/main/java/com/nip/dao/UserDao.java:24-35`

**现象**：
```java
public List<UserEntity> findAllUser(List<String> ids) {
  if (ids.isEmpty()) { return findAll().list(); }
  else {
    StringBuilder t = new StringBuilder();
    ids.forEach(s -> { t.append(s).append("|"); });
    t.deleteCharAt(t.length() - 1);
    return find("id REGEXP :ids", Parameters.with("ids", t.toString())).list();   // :33
  }
}
```
三个问题叠在一起：
1. `REGEXP` 是 MySQL 的操作符，不是 HQL 标准语法。[INFERENCE] Hibernate 6 的 HQL 语法里没有 `REGEXP` 中缀操作符，解析阶段就会失败；未经运行验证。
2. 即使能跑，`REGEXP 'a|b'` 是**非锚定的子串匹配**，不是相等匹配。ID 里只要有一个是另一个的子串就会误命中。
3. ID 若包含正则元字符（`.`、`(`、`+` 等）会改变匹配语义。
4. 无法走主键索引，退化成全表扫描 + 逐行正则。

`ids` 为空时走 `findAll().list()` 全表加载，也是隐患。

**影响**：可达路径确定 —— `UserController.java:135-137 getUsersByIds` → `UserService.java:106-108 getUsers` → `findAllUser`。要么接口直接报错，要么返回多余的用户。

**建议修复方向**：换成 `find("id in ?1", ids).list()`；空集合直接返回 `Collections.emptyList()` 而不是全表。

---

### P2-3 `SpecificationExecutor.nativeQuery` 泄漏 Session，且永远返回 null

**位置**：`src/main/java/com/nip/common/specification/SpecificationExecutor.java:97-146`

**现象**：两个独立缺陷：
```java
SessionFactory sessionFactory = CDI.current().select(SessionFactory.class).get();
sessionFactory.openSession().doWork(connection -> {     // :102-104  打开的 Session 从不 close
  ...
  ResultSet executeQuery = ps.executeQuery();
  ...
  if (executeQuery.getRow() == 0) { return; }           // :116-118
```
1. `openSession()` 拿到的 Session 没有 try-with-resources 也没有 `close()`，每调一次泄漏一个 Session 和它持有的 JDBC 连接。
2. `ResultSet.getRow()` 返回**当前行号**，`executeQuery()` 之后游标在第一行之前，`getRow()` 必然是 0，所以这个判断永远成立、永远提前 return，`ret` 保持为 null，方法最终返回 null（第 138 行 `return ret.get()`）。判断意图显然是「结果集为空」，正确写法应该是先 `next()`。
3. `catch (Exception e) { log.error("sql execute exception:{}", e.getMessage()); }`（`:140-142`）只记 message 不记堆栈，异常被吞。

**影响**：当前全项目**没有任何调用点**（对 `.nativeQuery(` 全项目搜索零命中），所以是潜伏问题而非现网故障。一旦有人开始用它，会同时踩到「返回 null」和「连接池被耗尽」。

**建议修复方向**：整个方法删掉。项目已有 `@NamedNativeQuery` + `@SqlResultSetMapping` 和 `entityManager.createNativeQuery` 两条成熟路径，不需要再手写一套 JDBC + 反射映射。若确要保留，至少用 try-with-resources 管理 Session，把 `getRow() == 0` 改成基于 `next()` 的判断。

---

### P2-4 `ToolUtil.isIdFieldEmpty` 用 `==` 比较字符串

**位置**：`src/main/java/com/nip/common/utils/ToolUtil.java:84`

**现象**：
```java
Object value = getterMethod.invoke(object);
return value == null || value == "";     // :84  引用比较，不是值比较
```
`value == ""` 比较的是引用。只有编译期常量池里的 `""` 才会命中；从 HTTP 请求体反序列化出来的空字符串、`new String("")`、`substring` 产生的空串全都不相等。

**影响**：这是 `BaseRepository.save()`（`BaseRepository.java:15-23`）区分 `persist` 与 `merge` 的唯一依据。前端提交 `{"id": ""}` 这类 payload 经 `PojoUtils` 转成实体后，ID 是非驻留的空字符串 → 判定为「有 ID」→ 走 `merge()` → Hibernate 按 id=`''` 查不到行，把它当游离对象处理，行为与预期的「新增」不一致。`MenusService.java:105` 显式写了 `p.setId(null)` 来规避，侧面说明这条路径确实会被踩到。

**建议修复方向**：改成 `return value == null || (value instanceof String s && s.isEmpty());`。

---

### P2-5 4 处 DAO 泛型 ID 类型与实体 `@Id` 类型不一致

**位置**：
- `src/main/java/com/nip/dao/general/key/GeneralKeyPatPageDao.java:11` —— `BaseRepository<GeneralKeyPatPageEntity, Integer>`，但 `GeneralKeyPatPageEntity.java:20-22` 是 `String id`（UUID）
- `src/main/java/com/nip/dao/general/ticker/GeneralTickerPatTrainPageDao.java:10` —— `BaseRepository<GeneralTickerPatTrainPageEntity, Integer>`，但 `GeneralTickerPatTrainPageEntity.java:17-19` 是 `String id`（UUID）
- `src/main/java/com/nip/dao/EnteringExerciseWordStockDao.java:10` —— `BaseRepository<EnteringExerciseWordStockEntity, String>`，但 `EnteringExerciseWordStockEntity.java:17-19` 是 `Integer id`
- `src/main/java/com/nip/dao/PostTrainGlobalRuleDao.java:8` —— `BaseRepository<PostTrainGlobalRuleEntity, String>`，但实体是 `Integer id`

**现象**：`PanacheRepositoryBase<Entity, Id>` 的第二个泛型参数就是主键类型，`findById` / `deleteById` / `findByIdOptional` 的签名全靠它。声明错了就等于把这几个方法的编译期类型检查完全关掉。

**影响**：目前这 4 个 DAO 都恰好绕开了 `findById`/`deleteById`（`EnteringExerciseWordStockService` 只用 `findByType`，`PostTrainGlobalRuleService.java:70` 用 `delete("id", id)` 而不是 `deleteById`），所以还没炸。但 P0-2 已经因为同一个根因（`GeneralKeyPatPageDao` 泛型写成 Integer）写出了 `findTwoPage(Integer id)` 这种错误签名 —— 类型系统本该在编译期拦下它。

**建议修复方向**：四处泛型改成与实体 `@Id` 一致，然后编译一次把连带的调用点全部暴露出来。

---

### P2-6 `EnteringExerciseWordStockEntity` 声明 IDENTITY，但表主键没有 AUTO_INCREMENT

**位置**：`src/main/java/com/nip/entity/EnteringExerciseWordStockEntity.java:17-19`，表定义见 `docs/database/project006.sql:28525` 起

**现象**：
```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Integer id;
```
表侧：
```sql
CREATE TABLE `t_entering_exercise_word_stock`  (
  `id` int(0) NOT NULL,          -- 没有 AUTO_INCREMENT
  `type` int(0) NULL DEFAULT NULL ...,
  `content` longtext ...,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB ...
```
`GenerationType.IDENTITY` 要求 Hibernate 在 INSERT 时**省略 id 列**、由数据库自增回填。列既不是 AUTO_INCREMENT 也没有 DEFAULT，MySQL 严格模式下会报 `Field 'id' doesn't have a default value`；非严格模式下写入 0，第二条就主键冲突。

**影响**：当前 `EnteringExerciseWordStockService.java` 只有 `findByType` 一个读方法，没有写入路径，所以是潜伏问题。对比同类的 `PostEnteringExerciseWordStockService.java:141` 有完整的增改删。一旦给这张表加写入功能就会立刻失败。

**建议修复方向**：给表加 `AUTO_INCREMENT`，或把实体改成 `GenerationType.IDENTITY` 之外的显式赋值策略。同时修掉 P2-5 里该 DAO 的泛型。

---

### P2-7 `in (?1)` 传空集合无任何防护

**位置**：多处，代表性的有
- `src/main/java/com/nip/dao/DeviceDescriptionDao.java:20-22 deleteAllByDeviceIdIn`
- `src/main/java/com/nip/dao/TheoryKnowledgeExamUserDao.java:164-176 countExamPass`
- `src/main/java/com/nip/dao/MilitaryTermDataDao.java:21-27`、`TheoryKnowledgeQuestionDao.java:18-31`、`TestPaperDao.java:22-30`、`TheoryKnowledgeSwfDao.java:31-33`、`TelegramTrainFloorContentDao.java:27-29`、`TheoryKnowledgeTestUserDao.java:77-79`

**现象**：这些方法全部形如 `find("xxxId in (?1)", ids)`，没有一个检查 `ids.isEmpty()`。而 `UserDao.java:25` 恰恰写了 `if (ids.isEmpty()) { ... }` 的防护 —— 说明团队踩过这个坑，只是没有推广。

确实会传空集合进去的调用点：
- `DeviceTypeService.java:64-67`：设备类型下没有设备时，`deviceIdList` 为空列表，直接传给 `deleteAllByDeviceIdIn`。
- `TheoryKnowledgeService.java:570-573`：`examIds` 由 `allByUserIdAndEndTimeLike` 流式收集而来，用户当月没有考试记录时为空列表，直接传给 `countExamPass`。

**影响**：[INFERENCE] Hibernate 对空的 IN 参数列表会渲染出 `in ()`，MySQL 报语法错误；未经运行验证。最好的情况也只是静默变成恒假条件。前者会让「删除设备类型」在没有设备时失败，后者会让「成绩分布统计」在无考试月份失败。

**建议修复方向**：在 DAO 方法入口统一 `if (ids == null || ids.isEmpty()) return List.of();`（删除类的直接 return），不要指望每个调用方记得判空。

---

### P2-8 HQL 里写了数据库列名 `page_number` 而不是实体属性名

**位置**：`src/main/java/com/nip/dao/general/key/GeneralKeyPatUserValueDao.java:20-23`

**现象**：
```java
public List<Integer> countByTrainIdAndUserIdGroupByPageNumber(Integer trainId, String userId) {
  return find("trainId =?1 and userId=?2 group by page_number", trainId, userId).list()
      .stream().map(GeneralKeyPatUserValueEntity::getPageNumber).toList();
}
```
`GeneralKeyPatUserValueEntity.java` 里的属性名是 `pageNumber`，`page_number` 是经命名策略转换后的**数据库列名**，HQL 里不认识它。同族的正确写法在 `GeneralTickerPatTrainUserValueDao.java:32-40`：用 `entityManager.createQuery("select floorNumber ... group by floorNumber", Integer.class)`。

另外这里还有第二个问题：`find(...)` 返回的是完整实体，`group by` 单列却 select 全部列，在 MySQL `ONLY_FULL_GROUP_BY`（5.7+ 默认开启）下会被拒绝。

**影响**：当前**没有调用点**（`GeneralTickerPatService.java:425` 和 `:760` 调的是 ticker 那个正确实现），属于潜伏问题。一旦接线就会立刻抛属性解析异常。

**建议修复方向**：照抄 ticker 版本的写法，用 `select pageNumber ... group by pageNumber` 的投影查询。

---

### P2-9 `findLastTwoResult` 名字说取两条，实际没有 LIMIT

**位置**：`src/main/java/com/nip/dao/general/key/GeneralKeyPatUserDao.java:73-80`

**现象**：
```java
public List<BigDecimal> findLastTwoResult(String user) {
  return entityManager.createQuery("SELECT ifnull(pu.score,0) "
      + "FROM general_key_pat_user pu LEFT JOIN general_key_pat p on pu.trainId = p.id "
      + "where p.trainType = 1 and pu.userId = ?1 ORDER BY p.createTime desc", BigDecimal.class)
      .setParameter(1, user).getResultList();     // 没有 LIMIT 2，也没有 setMaxResults
}
```
完全对称的 ticker 版本 `GeneralTickerPatTrainUserDao.java:75-83` 就带了 `ORDER BY p.createTime DESC LIMIT 2`。

**影响**：`GeneralKeyPatService.java:962-966` 在 `for (String user : userList)` 循环里逐个调用，只取下标 0 和 1 两个元素。也就是说，为了拿 2 个分数，把每个学员的全部历史科目训练成绩都查了出来。班级人数 × 历史训练次数的无谓数据传输，随使用时间线性恶化。

**建议修复方向**：加上 `LIMIT 2`（与 ticker 版本对齐）或用 `.setMaxResults(2)`。

---

### P2-10 用 `.list().size()` 统计数量

**位置**：`src/main/java/com/nip/dao/TheoryKnowledgeSwfRecordDao.java:38-40`、`src/main/java/com/nip/dao/TheoryKnowledgeTestUserDao.java:88-90`

**现象**：
```java
public Integer countByUserId(String userId) {
  return find("userId = ?1", userId).list().size();     // 把所有行查出来再数
}
```
`PanacheQuery` 有现成的 `count()`，项目里 `GeneralKeyPatUserValueDao.java:16-18`、`SimulationRouterRoomUserDao.java:26-28`、`SimulationRouterRoomPageValueDao.java:21-23` 都正确用了 `count(...)`。

**影响**：`SELECT COUNT(*)` 被换成把整表该用户的全部记录实例化成实体再取 size。这两个方法统计的是「已学课件数」「随堂测验次数」，随使用时间单调增长，且返回的实体全部进入一级缓存。

**建议修复方向**：改成 `return Math.toIntExact(count("userId = ?1", userId));`。

---

### P2-11 HQL 更新语句里用双引号包字符串

**位置**：`src/main/java/com/nip/dao/PostTelegramTrainFloorContentDao.java:22-25`

**现象**：
```java
@Transactional
public void clearByTranId(String tranId) {
  update("moresValue=\"[]\",moresTime=\"[]\",patKeys=\"[]\"  where trainId = ?1", tranId);
}
```
HQL/JPQL 的字符串字面量用**单引号**，双引号按 SQL 标准是「引用标识符」。全项目只有这一处这么写，其余 update 语句（如 `EnteringExerciseDao.java:25`、`TickerTapeTrainDao.java:33`）都不涉及字符串常量。

**影响**：[INFERENCE] 若 Hibernate 6 按标识符解析，`[]` 不是合法标识符，语句解析失败；未经运行验证。该方法在 `PostTelegramTrainService.java:481`「清除 floor content 内容」的活跃路径上被调用，失败就意味着重置训练内容的功能不可用。若能解析成字面量则行为正确，但这个前提不该赌。

**建议修复方向**：改成单引号 `moresValue='[]',moresTime='[]',patKeys='[]'`，在 HQL 和 SQL 里都无歧义。

---

### P2-12 聚合查询里带了非聚合列的 ORDER BY

**位置**：`src/main/java/com/nip/dao/PostTelegramTrainFloorContentDao.java:62-70`

**现象**：
```java
public Integer findCountByTrainIdOrderByFloorNumberAscSortAsc(String id) {
  List<Long> resultList = entityManager.createQuery("select count(id) "
      + "from t_post_telegram_train_floor_content "
      + "where trainId=?1 order by floorNumber , sort ", Long.class)   // 聚合 + 非聚合列排序
      .setParameter(1, id).getResultList();
```
无 `group by` 的聚合查询只返回一行，`order by floorNumber, sort` 既无意义又违反 `ONLY_FULL_GROUP_BY`（MySQL 5.7+ 默认开启）。

**影响**：[INFERENCE] 在 `ONLY_FULL_GROUP_BY` 下 MySQL 报 `Expression #1 of ORDER BY clause is not in GROUP BY clause and contains nonaggregated column`；未经运行验证。该方法在 `PostTelegramTrainService.java:554` 驱动分页（`:555-560` 用它算 `totalPage1`），失败会让整个报文分页查询不可用。即便 sql_mode 放宽，这个 ORDER BY 也是纯粹的多余开销。

**建议修复方向**：删掉 `order by floorNumber , sort`。

---

### P2-13 把 BigDecimal 列查进 `String.class`

**位置**：`src/main/java/com/nip/dao/general/ticker/GeneralTickerPatTrainDao.java:48-56`

**现象**：
```java
public List<String> countByUserTrainYearScore(String id, String year) {
  return entityManager.createQuery("SELECT tu.score "
      + "from general_ticker_pat_train_user tu ... ", String.class)   // 结果类型声明为 String
```
`GeneralTickerPatTrainUserEntity.java:59` 的 `score` 是 `BigDecimal`。同一个 DAO 的 `findByUserLastScore`（`:14-28`）查同一个 `tu.score` 用的是 `BigDecimal.class`，前后不一致。

**影响**：Hibernate 6 会在创建查询时校验声明的结果类型与选择项类型是否兼容，`String` 与 `BigDecimal` 不兼容会直接抛异常。当前该方法只在 `DemoService.java:109` 的**注释掉的代码**里出现，没有活跃调用点，属于潜伏地雷。

**建议修复方向**：改成 `BigDecimal.class`，返回类型跟着改；或者删掉这个无人使用的方法。

---

### P2-14 删除理论知识题库/分类留下悬空引用

**位置**：`src/main/java/com/nip/service/TheoryKnowledgeQuestionService.java:153-162`、`src/main/java/com/nip/service/TheoryKnowledgeClassifyService.java:66-68`

**现象**：
```java
// TheoryKnowledgeQuestionService.java:153-156
public Response<List<TheoryKnowledgeQuestionLevelEntity>> deleteTheoryKnowledgeQuestionLevelById(String id) {
  theoryKnowledgeQuestionLevelDao.deleteById(id);       // 只删分级，不管引用它的题目和试卷
  ...
}
// TheoryKnowledgeClassifyService.java:66-68
public void remove(TheoryKnowledgeClassifyDto dto) {
  classifyDao.deleteById(dto.getId());                   // 只删分类，不管引用它的知识点
}
```
引用关系（全是裸标量列，无 FK）：
- `TheoryKnowledgeQuestionEntity.java:55 levelId`、`TestPaperEntity.java:32 levelId` → `t_theory_knowledge_question_level`
- `TheoryKnowledgeEntity.java` 的 `difficultyId` / `specialtyId` → `t_theory_knowledge_classify`

**影响**：
- 删掉题目分级后，`TheoryKnowledgeQuestionDao.java:18-19 findAllByLevelIdIn` 和 `TestPaperDao.java:22-23 findAllByLevelIdIn` 再也捞不到这些题目和试卷 —— 数据还在库里，但界面上永久消失。
- 删掉知识分类后，`TheoryKnowledgeEntity.java:25-32` 的命名查询 `find_theory_knowledge_dto_all_sql` 在 `:30` 带 `k.difficulty_id in (:dids) and k.specialty_id in (:sids)` 过滤（`:33-40` 的 `find_theory_knowledge_dto_all_sql_open` 同理），引用了已删分类的知识点会从列表里**静默消失**。

**建议修复方向**：删除前先检查引用计数并拒绝（与 `MilitaryTermDataService.java:140-143`「存在子级不能删除」的做法一致），或者级联清理。

---

### P2-15 查不到时返回空实体而不是 null

**位置**：`src/main/java/com/nip/dao/TheoryKnowledgeTestDao.java:70-75`

**现象**：
```java
public TheoryKnowledgeTestEntity findFirstByKnowledgeSwfIdAndVersions(String knowledgeSwfId, Integer versions) {
  return find("knowledgeSwfId = ?1 and versions=?2", knowledgeSwfId, versions).list()
      .stream().findFirst()
      .orElse(new TheoryKnowledgeTestEntity());     // 查不到时造一个全空实体
}
```
同 DAO 其余方法（`:27-28`、`:38-39` 等）都返回真实列表，`TheoryKnowledgeTestFallibleDao.java:10-12` 等则规规矩矩返回 `firstResult()`（即 null）。

**影响**：调用方无法区分「查到一条各字段为空的记录」和「没查到」。这个空实体的 `id` 为 null，一旦被传给 `BaseRepository.save()`，`ToolUtil.isIdFieldEmpty` 判定为空 → 走 `persist()` → **在库里凭空插入一条全空的测验记录**。另外它还用了 `.list().stream().findFirst()` 而不是 `firstResult()`，多查了全部匹配行。

**建议修复方向**：改成 `find(...).firstResult()`，由调用方处理 null。

---

## P3

### P3-1 空实现方法直接 `return null`

**位置**：`src/main/java/com/nip/dao/TheoryKnowledgeSwfRecordDao.java:74-76`

**现象**：
```java
public List<Map<String, Object>> countMonthStudyTimeAndSwfNum(String userId, String yaer) {
  return null;
}
```
上方 `:68-73` 保留着一大段被注释掉的原生 SQL（带 `nativeQuery = true`），说明这是迁移时没有完成的方法。参数名还拼错成 `yaer`。

**影响**：全项目无调用点，当前无实际影响。但一个签名完整、返回 null 的公开方法，下次有人调用时会直接 NPE，而不会得到「未实现」的提示。

**建议修复方向**：删掉；需要时按 `count_study_time` 命名原生查询（`TheoryKnowledgeSwfRecordEntity.java:21-28`）的模式重新实现。

---

### P3-2 HQL `limit 1` 与 `firstResult()` 重复限制

**位置**：`src/main/java/com/nip/dao/general/ticker/GeneralTickerPatTrainPageDao.java:16-18`

**现象**：
```java
public GeneralTickerPatTrainPageEntity findByTrainIdOrderByFloorNumberDescSortDesc(Integer id) {
  return find("trainId = ?1 order by floorNumber desc limit 1", id).firstResult();
}
```
`firstResult()` 内部会调 `setMaxResults(1)`，与 HQL 里的 `limit 1` 重复。同名的 `PostTelegramTrainFloorContentDao.java:46-48` 就没写 limit。

**影响**：[INFERENCE] Hibernate 6 对「HQL 已有 limit 子句又调用 setMaxResults」的处理未经验证，可能抛异常也可能后者覆盖前者。功能上无差别，但属于不必要的不确定性。另外方法名说 `SortDesc` 但 order by 里没有 `sort desc`。

**建议修复方向**：去掉 HQL 里的 `limit 1`，补上 `, sort desc` 让排序与方法名一致。

---

## 已接受安全风险（按审查口径单独列出，不计入上述问题数）

内网部署口径下以下条目为已接受风险，仅作记录：

1. **密码明文按等值查询** —— `src/main/java/com/nip/dao/UserDao.java:50-52 findUserEntityByPassword(String password)` 直接用密码做等值查询，且 `UserEntity.java:64` 的 `password` 是普通 String 字段、`t_user.password` 为 varchar，无加盐哈希列。
2. **身份证号明文存储且可直接检索** —— `UserEntity.java:59-60 idCard`，`UserDao.java:46-48 findUserEntityByIdCard`、`:54-57 existsUserEntitiesByIdCardOrUserAccount` 均按明文匹配。
3. **token 明文入库并可反查用户** —— `UserEntity.java:66 token`、`UserDao.java:78-80 findUserEntityByToken`、`:60-64 existsUserByTokenAndDeviceId`。token 由 `AESUtil.encrypt(userAccount + "-" + password + "-" + deviceId, ...)` 生成（`UserService.java:383`），拿到 token 等于拿到可解密的账号密码串。
4. **`UserDao.java:33` 的 `REGEXP` 参数拼接** —— 见 P2-2。参数虽然走的是命名参数绑定（不构成经典 SQL 注入），但正则内容完全由调用方控制，恶意正则可造成回溯爆炸。功能正确性问题已在 P2-2 记入正式条目，此处仅记录其安全侧面。

---

## 附：本次核对的取证方式

- 所有 file:line 均来自对源文件的实际读取，未做推断。
- 标注 `[INFERENCE]` 的条目是「代码写法确定有问题、但具体失败模式依赖 Hibernate 6 / MySQL sql_mode 运行时行为」，本机无 Java 环境无法运行验证，已在各条目内明确标出。
- 表结构以 `docs/database/project006.sql`（36447 行，当前 dump）为准。`project006-base.sql` 是旧基线，两者在 `general_key_pat_page.id` / `general_ticker_pat_train_page.id` 上主键类型已经不同（int AUTO_INCREMENT → varchar(64)），**用 base 版本核对会得出错误结论**。
- 实体表名集合与两份 dump 的 `CREATE TABLE` 集合做过完整差集比对，结果见 P1-8。
