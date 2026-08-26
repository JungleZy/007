# Phase 7 报告：长尾模式规则 + 执行期附加清单

分支 `fix/2026-08-26-p0`，11 个提交（fix(tail-1) … fix(tail-11)），全部家族完成；抽样/回归测试全绿（flock 串行 mvnw）。

## 家族 1 — findById 裸解引用（fix(tail-1) 6ad178e）
**修法**：`findByIdOptional(id).orElseThrow(() -> new IllegalArgumentException("未查询到…"))`。
**位置清单**：
- P2-30：TheoryKnowledgeQuestionService:69/92、TheoryKnowledgeClassifyService:58、GradingRuleService:90
- P2-49：DeviceTypeService:49、TickerTapeTrainStageSettingService:45、RadiotelephoneTermDataService:70、EquipmentDeviceService:40/49、PostEnteringExerciseWordStockService:141、CableService:54、PostRadiotelephoneService begin/finish/details
- P2-70：KeyPointsService.findById(int type) 更名 `getByType`（与 Receive/Entering 兄弟服务一致；controller 同步改，null 数据是合法"未配置"读语义）
- P2-71：TickerTapeTrainSettingService.getDotStandardRate `getFirst()` → `stream().findFirst().orElseGet(new)`（对齐 Post 版）
- 新发现：RoleService.getRoleById:91、TheoryKnowledgeExamService:355（考生被删→显式报错）
- 附加#3：EnteringExerciseService.finish（修复 convertOne(null)→persist 全空新行）；同文件 pause 同 bug 同修
**测试**：FindByIdBoundaryTest 3/3 绿——finish 不存在 id 报错且行数不变、getRoleById 报错、EquipmentDevice update 报错。

## 家族 2 — Integer 拆箱（fix(tail-2) 2670d5c）
**修法**：`Objects.equals`/前置判空。
**位置**：P2-24 ComprehensiveService:301；P2-31 GradingRuleService:63/71/78；P2-32 MilitaryTermDataService:115；P2-33 move（改 findByIdOptional+同级校验+排序判空）；P2-55/56 PostTelexPat save/getPage（isCable/type/patType/groupNumber 判空，isCable=0 必填显式 IAE）。
**测试**：IntegerUnboxBoundaryTest 3/3 绿——isDefault null 不 NPE、跨父移动被拒、telex 组数 null 显式报错。

## 家族 3 — nextInt(size-1)（fix(tail-3) b13e9cb）
逐点确认：PostRadiotelephoneService:54、RadiotelephoneTermDataService:47 均为单点取值，无 +1/成对索引 → 改 `nextInt(size())`，补空集/number 判空。GlobalMessageGeneratedUtil 与 PostMilitaryTermTrain 各站点已是 `nextInt(size())`，未动。编译绿。

## 家族 4 — ObjectMapper 单例 + N+1（fix(tail-4) 83ecfa9）
- MenusService:195、RoleService:105/117 → 构造器注入 Quarkus 单例 ObjectMapper。
- DeviceService.listPage → `findByDeviceIdInOrderByCreateTimeAsc` 一次 in 查询 + groupingBy；DeviceTypeService.findAll → DeviceDao.countGroupByDeviceTypeId 一次分组计数（JPQL 实体名 DeviceEntity 为默认名，已核实无 @Entity(name)）。
**测试**：MenusServiceTest 2/2 绿。

## 家族 5 — P2-A 补 @Transactional（fix(tail-5) 0853b5b）
按"入口注解是否存在"逐方法补（自调用问题不存在，ArC 子类拦截已确证）：
EquipmentDeviceService add/delete/update/saveKeyPoints；TickerTapeTrainService begin/pause/goOn/finish/saveBaseTrain；TelegraphKeyPatSyntheticalService begin/stop/goTo；GeneralGroupNetRule/PostEnteringExercise/PostRadiotelephone/TelexPatTrain 四处 delete；PostEnteringExerciseWordStockService.listPage（查询内回填 save）；ComprehensiveService.getUserOverallInfo（GET 写易错题缓存）。TickerTapeTrainServiceTest 绿。

## 家族 6 — 死代码 + P2-13 守卫接入（fix(tail-6) b3cf6c7）
**删除**（删前 grep 确认调用链）：
- DemoService + DemoController（唯一调用者即调试端点本身，P2-94 调试代码进生产）
- AsyncSavePostTelegramTrainService 整类（savePostTelegramTrain 零调用；select 无 @Asynchronous 实为同步）→ PostTelegramTrainService 假 Future/死异常处理内联为直接分页查询（P2-07）
- P2-84：TheoryKnowledgeQuestionService 三个 handler + 注释调用块（仅被注释代码引用）
**接入**（不删）：
- ComparisonContext.getCurrentSource → MessageComparisonService.getSourceMessage（顺带消除 P2-08 MAX_SIGN_VALUE=99 误当下标上限）
- isValidSourceIndex → BunchDetector.shouldSkipBunchDetection
- shouldSkipGroupDetection/getSafeString/hasMoreSources → GroupDetector 入口与 handle*（无界 get 全护）
- shouldSkipErrorCodeDetection → ErrorCodeDetector 入口（补空占位保持结果列表对齐，handleStandardLengthGroup 无界 get 不再可达）；getErrorCodeDescription 接入 debug 日志
- clearJsonCache → MessageResultBuilder.cachePut（满 1000 整体重置后写入，消除 P2-12 缓存冻结）
**测试**：PostTelegramTrainServiceTest + PostTelegramTrainScoreTest + TheoryKnowledgeExamServiceTest 全绿（评分行为无回归）。

## 家族 7 — getKey / 提示变量用错 / 两套及格线（fix(tail-7) 93a59eb）
- getKey:76-85 删除，saveAll 改按明确列名 `parse.get("key")/get("value")` 取值，缺 key 列显式报错（后端开发导入端点）。
- P2-62：PostTelegraphKeyPat:244 提示改用 totalPage；P2-63：PostTickerTape reset 提示改"训练还未开始，无需重置"。
- P2-64：PostTelexPat 末页少行改按实际页号（==totalPage）+ 剩余组数折算行数（每行 10 组向上取整），替代 `totalPage % 100` 与 removeAll 残余判定。
- P2-77：getMonth → padTwoDigits（%02d，1..31 月/日通用，12 调用点 ast 批量改名）。
- P2-78：examTimes/scoreCount 硬编码 >=60 删除，统一 `countPass`→`countExamPass`（以各场试卷 passMark 为准）；gradeDistribution 同口径收敛。:559-565 分档（59/60-80/81+）是成绩分布展示口径，保留。
**测试**：Telex/Ticker/KeyPat/TheoryKnowledge/MilitaryTerm 选集全绿。

## 附加清单
- **#1 countScore 竞态（fix(tail-8) 20c3f55）**：PostTelegraphKeyPatTrainService:351、GeneralKeyPatService:644 页级 parallelStream 共享裸 ArrayList/统计 DTO → 改串行流（页序确定、无竞态）。
- **#2 ws 域（fix(tail-9) e20412e）**：
  - P1-13/P2-54：TLR 字段全改方法内 `ThreadLocalRandom.current()`——PostMilitaryTermTrain:58、PostRadiotelephone:46、PostTelexPat:61(static)、GeneralTelexPat:72(static)。
  - P2-8：REST 删房四站点（SimulationRecept/Report/RouterContent/Router）改 `WebSocketSimulationService.closeRoomSessions(map.remove(roomId), "房间已解散")`——向成员发 CLOSE 帧（NORMAL_CLOSURE+原因）并关闭 session。
  - GeneralPatTrainRoomUserDto.joinUser → CopyOnWriteArrayList。
  - **P2-4 全局锁粒度：跳过**（设计改造，超出机械化范围，待专项）。
  - ws 测试 WebSocketSimulationTest 1/1、WebSocketUnionTest 4/4 绿。
- **#4 listPage 203 语义（fix(tail-10) 86f2db7）**：PostMilitaryTermTrainService.listPage 改 userService.getUserByToken（登录失效统一 UnauthorizedException→203）；测试 new 构造参数同步。
- **#5 404 断言（fix(tail-10)）**：ExceptionBoundaryTest 新增 unknownPathKeeps404NotHijackedByGlobalMapper。**红测先证实缺陷**：GlobalExceptionMapper<Throwable> 确把 NotFoundException 兜成 500。按 Phase 4 专用 Mapper 模式**新增** WebApplicationExceptionMapper（直通 e.getResponse()，更具体 Mapper 优先），未动 GlobalExceptionMapper.java。8/8 绿。
- **#6 excelHanle（fix(tail-11) ce996ef）**：新建父类型分支 return→continue，剩余行继续处理；两处 maxSort 判空对齐 save 口径；回归测试导入"新父A 两行 + 新父B 一行"断言全部落库，4/4 绿。

## 文档勘误（随 tail-11 提交）
- P2-25 误报更正（此前已在 doc :1142 落盘）。
- 新增 P2-17 部分成立更正：越界分支因回填循环不可达；swap 串位属实、待排序方案决策。

## Concerns（需用户/后续决策）
1. **BunchDetector.detectBunchInRange / detectSameLineBunch / getColumnNumber / getLineDifference 未接入**：它们是检测变体而非守卫，接入会改变串组判分口径（同行/跨多行也计串组），无规格依据；删除又被 brief 明令禁止。原样保留，需拍板"扩大串组检测范围"与否。
2. **P2-17 swap 串位**：Tab 顺序应按 type 排序而非 swap(0,1)，属行为决策，未动。
3. **P2-31 flag 语义**（用户明确 isDefault=1 时可能被强设默认）：本轮只修拆箱；意图性逻辑待决策。
4. **P2-4 ws 全局锁粒度**：跳过，设计改造专项。
5. GeneralTickerPatTrainRoomUserModel（ws ticker 房间模型）joinUser 同为裸 ArrayList，任务仅点名 GeneralPatTrainRoomUserDto，未动。
