# 结论：本片共 25 条问题 —— P0 0 条 / P1 9 条 / P2 9 条 / P3 7 条（纯安全项单列附录，不计入）

最需要先处理的是 **3 类“删除已提交、后续步骤失败但事务不回滚”**：`CableService.delete`、`CableTypeService.delete` 用 try/catch 吞掉 RuntimeException（@Transactional 见不到异常，仍提交半删），`TelexPatService.deleteTexPatByToken` 在统计记录缺失时 NPE 同样被吞——三条都是上一轮 P1-63/64/65 的原样残留。其次是 `CableService.save` 编辑路径“先删楼层再遇 null NPE”，在 MyISAM 无回滚前提下会永久丢失电缆拍发内容。

| 项目 | 内容 |
|---|---|
| 审查范围 | 见「覆盖文件清单」：service/simulation/**(5)、service/context/**(1)、service/event/**(1)、service 根目录 22 个 `*.java` |
| 审查日期 | 2026-09-07 |
| 审查方式 | 静态阅读 + grep 取证（未运行构建/测试）|
| 计数 | P0 0 / P1 9 / P2 9 / P3 7 |

## 覆盖文件清单（证明 service 根目录无遗漏）

经 hub 与 `ServicePostTrain`/`ServiceTheoryUser`/`ServiceGeneralPat` 三分片对齐：55 个 service 根文件 = 本片 22 + PostTrain 21 + TheoryUser 12，无缝无叠。

**本片覆盖：**
- `service/simulation/`：SimulationRouterRoomService(406)、SimulationRouterRoomContentService(272)、SimulationReceptRoomService(243)、SimulationReportRoomService(238)、SimulationRouterRoomUserService(空壳)
- `service/context/`：ComparisonContext
- `service/event/`：WebSocketEventService
- service 根（指派 10）：MilitaryTermDataService、RadiotelephoneTermDataService、DeviceService、DeviceTypeService、DeviceScoringRuleService、EquipmentDeviceService、CableService、TelexPatService、TickerTapeTrainSettingService、TickerTapeTrainStageSettingService
- service 根（缺口 12，其他分片确认不归他们）：KeyPointsService、ReceiveKeyPointsService、EnteringKeyPointsService、EnteringExerciseWordStockService、EquipmentTrainService、RadiotelephoneService、TelegraphKeyTrainStatisticalService、GeneralGroupNetRuleService、MastheadService、BaseService、CableFloorService、CableTypeService

**非本片（已确认归属）：** `GradingRuleService`→ServicePostTrain（其明确认领，本片不重复评）；所有 `Post*`、`TelegramTrainService`、`TickerTapeTrainService`、`TelexPatTrainService`、`TelegraphKeyPatTrainService`、`TelexPatTrainStatisticalService`、`TelegraphKeyPatSyntheticalService`、`GroupNetTrainService`、`MessageComparisonService`、`EnteringExerciseService`、`EnteringTelexPatService`→ServicePostTrain；`Theory*`、`TestPaperService`、`UserService`、`MenusService`、`RoleService`、`ComprehensiveService`、`UserTrainStatisticsService`→ServiceTheoryUser；`service/general/**`+builder/detector/enums/constants→ServiceGeneralPat。

## 1. P0

无。

（本片先删后插路径要么条件受限、要么异常逸出而非静默吞掉，未见“稳定且无条件的永久性全用户数据丢失/核心功能整体不可用”，故 P0 计 0。相关条目见 P1。）

## 2. P1 —— 9 条

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| SM-P1-01 | `service/CableService.java:82-91` `delete` | 删除报文时 `cableDao.deleteById(id)` 抛任意 RuntimeException（DB 异常/约束） | `cableFloorDao.deleteByCableId(id)` 已执行；catch 吞异常 `return false`，`@Transactional` 见不到异常→仍提交→楼层已删、报文头残留成空报文；前端因 return false 以为未删 | :85 先删楼层→:86 删头→:87-90 `catch(RuntimeException){log;return false}`。上轮 **P1-64** 原样未改 |
| SM-P1-02 | `service/CableTypeService.java:47-59` `delete` | 删除报文类型时任一 `deleteById`/`delete` 抛 RuntimeException | 三步删除(type/cable/cable_floor)中途失败被 catch 吞→已删部分提交、类型行/报文/楼层成孤儿不可回收；return false 误导前端 | :50-52 三连删+:54-56 catch 吞异常。上轮 **P1-63** 原样未改 |
| SM-P1-03 | `service/TelexPatService.java:95-112` `deleteTexPatByToken` | 用户对某 type 有 `t_telex_pat` 数据但无 `t_telex_pat_train_statistical` 行 | :98 `deleteByUserIdAndType` 已执行；:100 `findByUserIdAndType` 返回 null→:101 `statisticalEntity.setTotalTime("0")` NPE→:108 catch 吞、无 `setRollbackOnly`→删除提交、统计陈旧、返回 error 让前端以为失败（重试恒复现） | 对比同类 `saveTelexPat`(:70-76) 已用 `transactionManager.setRollbackOnly()` 整改，唯独 delete 未跟进。上轮 **P1-65** 未修复 |
| SM-P1-04 | `service/RadiotelephoneService.java:53-61` `finish` | 未先调 `listPage`（:39-47 才懒建记录）直接对某 type 调 finish | :56 `findByUserIdAndType` 返回 null→:57 `entity.getTotalCount()+1` 拆箱 NPE；另 :58 `Integer.parseInt(entity.getTotalTime())` 对非数字抛 NFE。`@Transactional(rollbackOn=Exception)` 回滚→500，该次结算丢失 | 同类 listPage 懒建、finish 却假定存在，上轮 P1-69 集群条目 :989 原样未改 |
| SM-P1-05 | `service/CableService.java:51-80` `save` | 编辑已有电缆(vo 带 id)时请求体 `floors` 为 null/缺失 | :58 `cableFloorDao.deleteByCableId` 先删全部楼层(cable_floor 为 MyISAM，语句即时生效)→:62 `floors.size()` NPE 逸出→楼层内容 moresKey 不再回插；MyISAM 无回滚→**永久丢失该电缆全部拍发内容** | :57 save→:58 删楼层→:59 `vo.getFloors()`→:62 循环 `floors.size()` 无判空。异常逸出(非静默)，损坏依赖引擎，见待验证 |
| SM-P1-06 | `service/simulation/SimulationRouterRoomService.java:125-127` `addRoom` | isCable=1 建房，且请求组数 `bwCount/100` 大于该电缆从 startPage 起可用楼层数；或 bwCount<100 | :126 `int totalPage=bwCount/100`→:127 `cableFloor.subList(0,totalPage)` 无 `Math.min(size)`：(a) totalPage>size→IndexOutOfBounds→建房失败回滚；(b) bwCount<100→totalPage=0→subList(0,0) 空→**房间建成但一组报底都没有**，用户进去空白无日志 | 与上轮 **P1-17** 同构，仿真侧 4 处独立复制(不合并各计一条) |
| SM-P1-07 | `service/simulation/SimulationRouterRoomContentService.java:106-109` `addRoomAndContent` | 同 SM-P1-06(干扰房 isCable=1 建房) | 同 SM-P1-06 | :108 `totalPage=bwCount/100`→:109 `subList(0,totalPage)` 无 size 校验 |
| SM-P1-08 | `service/simulation/SimulationReceptRoomService.java:104-107` `addRoom` | 同 SM-P1-06(抄收房 isCable=1 建房) | 同 SM-P1-06 | :106 `totalPage=bwCount/100`→:107 `subList(0,totalPage)` 无 size 校验 |
| SM-P1-09 | `service/simulation/SimulationReportRoomService.java:103-106` `addRoom` | 同 SM-P1-06(报告房 isCable=1 建房) | 同 SM-P1-06 | :104 `totalPage=bwCount/100`→:105 `subList(0,totalPage)` 无 size 校验 |

## 3. P2 —— 9 条

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| SM-P2-01 | `service/DeviceTypeService.java:66-73` `delete` | 删除“尚未添加过设备”的设备类型 | :67 先删父(type)→:69 后查子(`findByDeviceTypeId`)顺序颠倒；因 device 与 device_type 无 DB 级联(MyISAM)故子查询仍命中、功能侥幸正确但脆弱；:72 `deleteAllByDeviceIdIn(空 list)` 展开为 `deviceId in ()` | `DeviceDescriptionDao.java:20-22` `delete("deviceId in (?1)", list)`。上轮 **P2-49** 未修复；空 in 行为见待验证 |
| SM-P2-02 | `service/CableService.java:41-42` `findAll` | 传入非 null 但为空的 `scope` 列表 | :42 `cableDao.find("scope IN (?1)", scope)` 空集展开为 `scope in ()` | :35/:38/:41 分支仅判 `null==scope`，不判空集 |
| SM-P2-03 | `service/TickerTapeTrainSettingService.java:48-60` `addOrUpdate` | 请求体 `paramList` 为 null/缺失(或空数组) | :51 `deleteAll()` 清空全部共享抄收训练参数→:53 `PojoUtils.convert(null,...)` 返回空 List(`PojoUtils.java:33-39`)→:57 save 空→**静默清空全局参数、返回 success、无异常无告警** | 上轮 **P2-72** 未修复(异常路径本可回滚，但 null→空 List 无异常，deleteAll 已生效) |
| SM-P2-04 | `service/MilitaryTermDataService.java:49` `saveAll` | 调用 `POST /api/.../saveAll`(Controller :42-44) | `JSONUtils.gson.toJsonTree(data)` 对 String 入参产出 JsonPrimitive，`.getAsJsonObject()` 抛 IllegalStateException→该端点恒失败。为“后端开发用”工具端点，生产走 save/saveBatch，影响面小 | 应为 `JsonParser.parseString(data)`。见待验证 |
| SM-P2-05 | `service/MilitaryTermDataService.java:112` `save` | 新增军语查重 | 查重用 `findByValue(dto.getKey())`(`MilitaryTermDataDao` JPQL 为 `value=?1`)——用 key 比 value 列：真内容重复查不出(形同虚设)，而某条 value 恰等于新条目 key 时误报“内容重复” | 对比 :168 update 用 `findByValue(vo.getValue())`。上轮 **P2-92** 未修复 |
| SM-P2-06 | `service/TelegraphKeyTrainStatisticalService.java:44-61` `statisticalPage` | 新用户并发两次打开统计页 | GET 内 `@Transactional` 懒建 type 0/1/2 记录，无唯一约束→并发两请求都判缺失都插入→重复统计行 | :50-60 `collect.get(i)==null` 即 `statisticalDao.save`；与上轮 P2-21 同根 |
| SM-P2-07 | `service/simulation/SimulationRouterRoomService.java:227-243` `changeChannel` | 改信道号同时另一线程调 `getRoomUserList`(:188-218) | 就地改共享 `SimulationGlobal.routerRoom` 中 `SimulationUserModel.setChannel`，无同步；跨用户共享内存态被非原子改写，读侧可能见撕裂值 | SimulationGlobal 为 @ApplicationScoped 单例静态态(与 WsConcurrency 分片同源，建议协调) |
| SM-P2-08 | `service/EquipmentTrainService.java:55-59` `detail` | 传入不存在的 id | :58 `findByIdOptional(id).orElse(new EquipmentTrainEntity())`→返回字段全 null 的空 VO + HTTP 200，静默失败，前端无法区分“查无此项” | 对比同类多处 orElseThrow |
| SM-P2-09 | `service/simulation/SimulationRouterRoomService.java:115/118` addRoom；`SimulationRouterRoomContentService.java:137,146` addStudent | 建房/加人请求缺 `isCable`/`bwCount`，或 addStudent 传无效 roomId | :115 `param.getIsCable()==0`、:118 `bwCount.compareTo(200)` Integer 裸拆箱 NPE；addStudent :137 `findById` 可能 null→:146 `byId.getRoomType()` NPE | 四个仿真 addRoom 与 addStudent 均无入参判空 |

## 4. P3 —— 7 条

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| SM-P3-01 | `service/simulation/SimulationRouterRoomService.java:122-123` `addRoom` | isCable=0 建房 | :122 `generateMessageBody` 内部已通过回调 `pageDao::save`(:404) 保存并返回已保存列表(`SimulationMessageGenerator.java:83` `saver.apply(ret)`)，:123 又 `pageDao.save(...)` 一次；因 IDENTITY 主键在 persist 即赋值→第二次走 merge 不产重复行，仅冗余 merge，依赖“persist 即赋 id”较脆弱 | 对比 ContentService(:103-104) 用 `entities->entities` 恒等回调、只存一次 |
| SM-P3-02 | `service/KeyPointsService.java:24-30` / `ReceiveKeyPointsService.java:24-31` / `EnteringKeyPointsService.java:24-31` | — | KeyPointsService 用原生 `int type`、方法名 `getByType` 但 DAO 名 `findAllByType` 且返回单实体、命名互相矛盾；三读方法都直返 DAO 的 null 无默认值 | 上轮 **P2-70** 未修复(纯命名/风格) |
| SM-P3-03 | `service/simulation/SimulationRouterRoomService.java:163` `addRoom` | 建含收报人的房间 | 收报人用 `roomEntity.getId()`，发报人(:151)用 `room.getId()`；因 `room=save(roomEntity)` 为同一托管实例故 id 相等、当前无 bug，但两处不一致易误导 | :102 `room=routerRoomDao.save(roomEntity)` |
| SM-P3-04 | `service/BaseService.java:10-36` | — | 整个 @ApplicationScoped Bean 只剩注释掉的 `queryListBySql`、无有效成员——死 Bean，应删除 | 全文仅注释块 |
| SM-P3-05 | `service/simulation/SimulationRouterRoomUserService.java:5-8` | — | 空 @ApplicationScoped 类、无任何方法——僵尸桩，应删除 | 类体为空 |
| SM-P3-06 | `service/simulation/SimulationRouterRoomService.java:21,34`、`SimulationReceptRoomService.java:19,26` 等 | — | 重复 import(`SimulationSessionHolder` 引两次)及未使用 import(JSONUtils/TypeToken 等)，四个仿真服务普遍存在 | 逐文件 import 段 |
| SM-P3-07 | `service/TickerTapeTrainSettingService.java:67-71` `getDotStandardRate` | — | 方法名叫 rate 实际返回 dot 时长，与 `PostTickerTapeTrainSettingService` 的 `getDotStandard` 同义两名 | 上轮 **P2-71** 未修复(纯命名) |

## 5. 上一轮遗留核销

上轮属于本片文件的条目(取自 `2026-08-26-service-core-review.md` 及汇总 `2026-08-26-full-project-review.md` 改级表)逐条核销：

| 上轮编号 | 上轮结论 | 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| 汇总改级 #18(军语 Excel 导入 NPE) | 空顶级/空子级集合 NPE，降 P1 | **已修复** | `MilitaryTermDataService.saveBatch:209-211` 入口拒空/null；`excelHanle:228/230/243/245` maxSort `==null?1:+1`；:237-238 `return`→`continue`；`saveBatch:207` 自带 @Transactional。`MilitaryTermDataServiceTest` 3 用例锁定整批回滚+空/null 拒收+新父不丢后续行 |
| 汇总改级 #7(TickerTapeTrainService.update 抹空 11 列) | detached merge 全仓无调用，误报降 P2 | **非本片文件**(TickerTapeTrainService→ServicePostTrain)；结论沿用(死代码/无调用方) | — |
| P1-41(军语 value=null 传导下游 NPE) | excelHanle/saveAll 产出 value=null | **部分修复** | `saveAll:66-68` 增 key 非空校验；但 `excelHanle:225/233/248` 仍用 `dto.getContent()` 可为 null，下游 `PostMilitaryTermTrainService`(非本片)仍可 NPE |
| P1-43(saveBatch 自调用 excelHanle 事务绕过) | 半导入 | **已修复/原假设被推翻** | `saveBatch:207` @Transactional；`MilitaryTermDataServiceTest` 注释实测 ArC 子类拦截使自调用事务生效、整批回滚 |
| P1-44(excelHanle return 丢弃剩余行) | 首个新父类型后整批丢 | **已修复** | :237-238 改 `continue`；测试 `newParentRowDoesNotDropRemainingRows` 守护 |
| P1-45(maxSort 拆箱 NPE) | 空库首导 NPE | **已修复** | :228/:230/:243/:245 `maxSort==null?1:maxSort+1` |
| P1-63(CableTypeService.delete 吞异常留孤儿) | 半删孤儿 | **未修复** | `CableTypeService.java:47-59` try/catch 原样(见 SM-P1-02) |
| P1-64(CableService.delete 吞异常空报文) | 楼层删、头残留 | **未修复** | `CableService.java:82-91` try/catch 原样(见 SM-P1-01) |
| P1-65(TelexPatService clear 删除已提交、统计未清零) | 删提交+统计陈旧+返回 error | **未修复** | `TelexPatService.deleteTexPatByToken:95-112` 原样(saveTelexPat 已整改、delete 未跟进，见 SM-P1-03) |
| P1-69 集群 RadiotelephoneService.finish(:989) | entity null NPE/parse | **未修复** | `RadiotelephoneService.java:53-61`(见 SM-P1-04) |
| P2-16(TickerTapeTrainSettingService.getDotStandardRate 裸 getFirst) | NoSuchElementException | **已修复** | :67-71 改 `stream().findFirst().orElseGet(new)` |
| P2-20(RadiotelephoneTermData 随机 nextInt(size-1)) | 空/单元素抛、漏末条 | **已修复** | `RadiotelephoneTermDataService.java:42-60` 空集抛、number 判空、`nextInt(entityList.size())` 全长 + 注释「P2-20」 |
| P2-32(MilitaryTermData.save type 拆箱 NPE) | 缺 type NPE | **已修复** | :109 `Objects.equals(dto.getType(),0)` 空安全 |
| P2-33(MilitaryTermData.move 不校验同父/sort null) | 跨父/null NPE | **已修复** | :188 校验同父抛错、:191-193 校验 sort 非空 |
| P2-49(一批小服务查不到直接 NPE) | orElseThrow 缺失 | **已修复** | DeviceTypeService.save:50-51、RadiotelephoneTermData.update:77-78、EquipmentDeviceService:44-45/55-56、CableService.save:54-55、TickerTapeTrainStageSetting.add:45-46 均改 orElseThrow |
| P2-49 压缩块(DeviceTypeService.delete 先删父后查子+空 in) | 顺序反+空 in | **未修复** | `DeviceTypeService.java:66-73` 顺序未改(见 SM-P2-01)；因无级联侥幸不出错 |
| P2-70(三 KeyPoints 服务命名不一致) | 命名/签名矛盾 | **未修复** | 三文件原样(见 SM-P3-02，纯风格) |
| P2-71(TickerTapeTrainStageSetting 写方法裸 findById) | 更新分支 NPE | **已修复** | :45-46 orElseThrow；命名 rate 遗留(SM-P3-07) |
| P2-72(两 Setting deleteAll 静默清空) | null paramList 静默清空 | **未修复**(本片侧 TickerTapeTrainSettingService) | :48-60 原样(见 SM-P2-03) |
| P2-92(MilitaryTermData.save findByValue(getKey)) | 查重失效/误报 | **未修复** | :112 仍 `findByValue(dto.getKey())`(见 SM-P2-05) |
| 附录“deleteById 事务标注分裂/整类无 @Transactional”(EquipmentDeviceService/GeneralGroupNetRuleService) | 缺事务 | **已修复** | EquipmentDeviceService:26/32/42/53 均 @Transactional；GeneralGroupNetRuleService.deleteById:34 @Transactional |
| P2-54 ThreadLocalRandom(RadiotelephoneTermData 方法内 current) | 正样本 | **仍为正样本** | :52 方法内 `ThreadLocalRandom.current()` |
| 仿真房间删除“先删子后删父/并发” | 上轮跨用户/删除竞态 | **已整改(本轮确认)** | 4 个仿真 delete 用 `RoomLifecycleLocks.simulationRoom(roomId)`+`RoomDeletionTransaction.run{pageValue→page→roomUser→roomContent→room}`，子先父后、删除后锁外关会话；`SimulationRoomLifecycleTest` 覆盖 replace/removeCurrent 并发与幂等 |

## 6. 待运行验证清单

1. **表引擎口径冲突(影响 SM-P1-05 定级)**：上轮汇总 #21 称“相关表为 InnoDB”，而本轮背景事实为“22 张 MyISAM 表不可回滚”。SM-P1-01/02/03/04 因异常被 catch 吞、与引擎无关(恒提交)；但 SM-P1-05(CableService.save)异常逸出，是否永久丢失取决于 cable_floor 是否 MyISAM。请以 `backend/database/project006.sql` 实际 ENGINE 为准确认。
2. **gson toJsonTree(SM-P2-04)**：确认 `JSONUtils.gson.toJsonTree(<原始JSON字符串>).getAsJsonObject()` 是否抛 IllegalStateException 使 `/saveAll` 端点恒失败(静态判断高置信，未运行)。
3. **空 IN 展开(SM-P2-01/02)**：Hibernate ORM 6.6(Quarkus 3.20)对 `delete/find ... in (?1)` 传空 List 的展开策略(`1=0` 静默匹配 0 行 还是抛异常)，验证 `DeviceDescriptionDao.deleteAllByDeviceIdIn(空)` 与 `CableService.findAll` 空 scope。
4. **懒建统计并发重复(SM-P2-06)**：并发两次 `statisticalPage`/相关 statistical 初始化是否产生重复行(表无唯一约束)。
5. **SM-P1-06~09 空报底**：bwCount<100 时 totalPage=0→仿真房间建成但无报底，运行确认前端表现(空白)与是否有提示。

## 附录：已接受安全风险(不计入计数)

1. **无权限校验的全局写**：`DeviceService`/`DeviceTypeService`/`DeviceScoringRuleService`/`EquipmentDeviceService`/`CableService`/`CableTypeService`/`RadiotelephoneTermDataService`/`GeneralGroupNetRuleService` 的增删改均不校验调用者角色，任何登录用户可删设备类型/评分规则/电缆/词库/组网规则(内网已接受)。
2. **token 换用户不判空即 NPE 而非 401**：`RadiotelephoneService:36`、`EquipmentTrainService:30/50`、`TelegraphKeyTrainStatisticalService:46`、`DeviceService/DeviceTypeService` 经 UserService/userDao 取用户，过期 token 表现为 NPE 500。
3. **跨用户数据访问**：`RadiotelephoneService.finish`、`EquipmentTrainService.detail/listPage` 仅按 userId/id 定位、不校验归属；仿真 `changeChannel`/`sendFinish`/房间 delete 按 roomId 直接改，未校验操作者是否房主。
4. **仿真房间状态仅按 roomId 变更**：`SimulationRouterRoomService.changeChannel/sendFinish`、`SimulationRouterRoomContentService.saveSetting` 可改任意房间(内网已接受)。
