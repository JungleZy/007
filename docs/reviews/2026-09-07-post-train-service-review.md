# 结论：拍发/训练评分服务分片 —— P0 0 / P1 10 / P2 34 / P3 6（纯安全项见附录，不计数）

| 项目 | 内容 |
|---|---|
| 审查范围 | service 根目录评分/训练服务 20 文件：PostTelexPatTrainService、PostTelegramTrainService、PostTickerTapeTrainService、PostMilitaryTermTrainService、PostTelegraphKeyPatTrainService、PostRadiotelephoneService、PostEnteringExerciseService、PostEnteringExerciseWordStockService、PostTrainGlobalRuleService、TelegramTrainService、TickerTapeTrainService、TelexPatTrainService、TelegraphKeyPatTrainService、TelexPatTrainStatisticalService、TelegraphKeyPatSyntheticalService、GroupNetTrainService、MessageComparisonService、GradingRuleService、EnteringExerciseService、EnteringTelexPatService；辅以 service/detector(Line/Group/ErrorCode)、context/ComparisonContext、common/utils/ScoreMath、PostTelegramTrainController 端点暴露、common/utils/Page 与 dto/Page |
| 审查日期 | 2026-09-07 |
| 审查方式 | 静态阅读 + grep 取证（未运行构建/测试）|
| 计数 | P0 0 / P1 10 / P2 34 / P3 6 |

非目标：service/general、service/simulation、theory 系列；RadiotelephoneService/TelegraphKeyTrainStatisticalService/GeneralGroupNetRuleService/EnteringExerciseWordStockService 归 ServiceSimulation 分片（已确认无重叠）。detector 文件非显式目标，但 P1-01/03/04 评分正确性直接落在报文对比簇、无其他分片认领，故一并核销并计入。

## 1. P0
无。上一轮属本片的先删后写 P0（PostTelexPat 报底、PostTelegraphKeyPat parallelStream）已修复或降级；死循环、Assert 写反、状态枚举等 P0 已修复。残留的 delete→save 不可回滚窗口按内网口径计 P1（PT-P1-09/10，待验证引擎）。

## 2. P1（10 条）
| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| PT-P1-01 | service/TelegramTrainService.java:264-310（catch 307-308） | save() 写入中抛非持久化异常（如 285-289 floorContents 为 null → .size() NPE） | @Transactional 默认只回滚逸出方法的异常；catch 吞掉后正常返回 error()，不重抛也不 setRollbackOnly → 提交。此前 274-278 已把上一次暂停训练置 status=3 并 finishStatistical 计入统计、284 新训练头已写、楼层写一半 → 全部提交，暂停态永久丢失、新训练残缺，前端仅见 error，零日志 | 对照 TelexPatTrainService.java:95 已用 transactionManager.setRollbackOnly()，此处未做；catch 无 log |
| PT-P1-02 | service/PostTelegramTrainService.java:830-844；controller/PostTelegramTrainController.java:150-155 | 任意请求 GET /api/postTelegramTrain/test | 方法体硬编码 trainId 46b6bfee-...，读其第1页 messageBody 经 handleMessageBody 重写后 saveAndFlush 覆盖真实数据；该 trainId 不存在时 837 valueEntity.getMessageBody() NPE→回滚 500 | 调试端点仍生产暴露、写死主键；待验证库中是否存在该 trainId |
| PT-P1-03 | service/PostTelegramTrainService.java:795-821（803） | addContentValue 追加 ≥2 页（POST /addContentValue 已暴露 controller:137-141） | floorNumber+=i 累加而非递增 → base+0,+1,+3,+6…：i=0 复用已有楼层号重叠，i=2 跳过 base+2 空洞；逐行 saveAndFlush | 方法已补 @Transactional(794) 修“无事务”半项，算术缺陷仍在 |
| PT-P1-04 | service/TelegraphKeyPatTrainService.java:114-130（128） | clear() 用户该 type 尚无统计记录（新用户/未完成过） | 124 findByUserIdAndType 返回 null，125-127 setTotalCount 有判空但 128 save(statisticalEntity) 在判断外 → save(null) 触发 BaseRepository Assert.notNull IllegalArgumentException → @Transactional 回滚，新用户清空不可用 | 上一轮 P0-09 改级 #21→P1，机制未变 |
| PT-P1-05 | service/PostTickerTapeTrainService.java:159-171（163-167） | reset(183 置 startTime=null) 后不 begin 直接 finish，或从未 begin | checkStatus(309-313) 只拦 FINISH/HAS_SCORE，放行 NOT_STARTED → 167 Duration.between(null,endTime) NPE → 回滚 500，finish 该状态不可用 | reset 183 setStartTime(null) |
| PT-P1-06 | service/PostTickerTapeTrainService.java:188-258（232） | uploadResult result 页数 > images 张数，或截图失败未传 images | param.getImages().get(i) IndexOutOfBounds/NPE → 回滚 500，成绩无法提交 | images/result 长度不校验、images 未判空 |
| PT-P1-07 | service/MessageComparisonService.java:205-212 | 拍发出现多组（源报文在后续若干组内重新对上），groupDetector 命中 | 多组成功后 return currentIndex 未加 skipCount，主循环 140 仅 incrementSourceIndex 一次、i++ 一次 → 多余组在后续迭代被再次当普通组比对，本页后半段对齐错位、错码/点划重复累加，错误分数落库 | 对照 239 错码路径 return currentIndex+skipCount 生效；GroupDetector 已 setMoreGroup(+skipCount) 但主循环未跳过 |
| PT-P1-08 | service/detector/LineDetector.java:182-253 | 拍发行数与报底不一致且位于行首(sourceIndex%10==0)，多行/少行检测成功 | handleMoreLineDetected/handleLessLineDetected 全程无 addCorrectMessage，当前 patKey 日志/点划/耗时丢失；多行分支 216-225 对后 9 组 checkDotLineGap 但主循环未跳过 → 这 9 组随后再统计一遍，点粗/点虚/间隔翻倍，resolver 缺组 | 对照 GroupDetector.handleLessGroupDetected 会写入当前组，两 detector 处理不一致 |
| PT-P1-09 | service/PostTelexPatTrainService.java:796-819（818-819） | type≠4 结算 finish→countScore；delete 与 saveAndFlush 间进程/连接中断 | deleteByTrainId(818) 已下发，若 post_telex_pat_train_page 为 MyISAM，saveAndFlush(819) 前中断则报底/回写值永久丢失不回滚 | finish 已加幂等守卫(230)，仅剩单次中断窗口；待验证表引擎 |
| PT-P1-10 | service/PostTelegraphKeyPatTrainService.java:333-482（371-372） | 电键结算 countScore；delete 与 saveAndFlush 间中断 | valueDao.deleteByTrainId(371) 已下发，若 post_telegraph_key_pat_train_page_value 为 MyISAM，saveAndFlush(372) 前中断则拍发记录永久丢失 | parallelStream 已改串行(357)，仅剩单次中断窗口；待验证表引擎 |

## 3. P2（34 条）
| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| PT-P2-01 | PostTelegramTrainService.java:226 | 固定报 messageNumber<100(totalPage=0) 或 cableFloor 层数<totalPage | subList(0,0)→训练建成零报底静默；层数不足→IndexOutOfBounds 回滚 500 | 无 Math.min/size 校验 |
| PT-P2-02 | PostTelexPatTrainService.java:140-141 | 同上(groupNumber/100) | 同上 | 无 size 校验 |
| PT-P2-03 | PostTickerTapeTrainService.java:93-94 | 同上(totalNumber/100) | 同上 | 无 size 校验 |
| PT-P2-04 | PostTelegraphKeyPatTrainService.java:91-92 | 同上(totalNumber/100) | 同上 | 无 size 校验 |
| PT-P2-05 | PostTelexPatTrainService.java:255 | getPage 传 pageNumber=0 | totalPage<pageNumber||pageNumber<0 不拦 0 → 写入 pageNumber=0 垃圾行，countScore 页序错位 | 对照 PostTickerTape 269 <=0、PostTelegraphKeyPat 247 <1 |
| PT-P2-06 | PostTelexPatTrainService.java:244-266 | 同一(trainId,pageNumber)并发 GET | getPage 无 @Transactional，isEmpty→generateContent 各插一套，无唯一约束→该页两套 sort，countScore 错位 | 懒生成非原子 |
| PT-P2-07 | PostTickerTapeTrainService.java:260-298 | 同上 | findPage 无 @Transactional，重复生成报底 | 同上 |
| PT-P2-08 | PostTelegraphKeyPatTrainService.java:232-267 | 同上 | getPage 无 @Transactional，重复生成 | 同上 |
| PT-P2-09 | PostTelegramTrainService.java:507-509 | saveContentValue 提交某页 | messageNumber(总组数).compareTo(floorNumber(页码))>0 口径错几乎恒真→floorNow=页码+1 指向不存在页，detail 跳空页 | 组数与页码比较 |
| PT-P2-10 | PostTelegramTrainService.java:349-373 | 训练部分页已提交部分未提交 | resolver 仅 else 分支 add(363)、size()==2 break 仅 else(364)，messageBody/standards 两分支都 add→长度不一致，前端按下标错位 | 分支不对称 |
| PT-P2-11 | PostTelegramTrainService.java:400-415 | findMessageBody 该页需懒生成且训练无任何 floor content | 401 findByTrainIdOrderByFloorNumberDescSortDesc 返回 null，415 getFloorNumber() NPE 500 | 对照 printBottomReport 553 已判空 |
| PT-P2-12 | PostTelegramTrainService.java:541-583（571） | 多次打印报底或 messageNumber 下调后再打印 | 读接口 printBottomReport 无 @Transactional 却 580 generateMessage 写库；571 generateNumber 可为负 | 假异步死代码已删(P2-07)，写库+负数仍在 |
| PT-P2-13 | PostTelegramTrainService.java:488/600/776 | finish 缺字段 | 488 Long.valueOf(Integer null) NPE、600 dto.getFinishInfo().isEmpty() null NPE、776 new BigDecimal(getSpeed()) null/非数字 | 拆箱/解析未校验 |
| PT-P2-14 | MessageComparisonService.java:84-86 | 调用方传 null rule（ruleContent 解析失败） | rule=new PostTelegramTrainRule() 各子规则仍 null，detector 立即 getLarge().getL()/checkDotLineGap NPE，兜底只后移崩溃点 | 空对象未初始化子规则 |
| PT-P2-15 | MessageComparisonService.java:245-265 vs detector/LineDetector.java:244 | 用户少拍一整行且总组数相应减少 | calculateMissingLines 与 handleLessLineDetected 均向 scoreVO.moreOrLackLine 累加互不知情→少行扣分翻倍 | 两处重复计数 |
| PT-P2-16 | PostTelegramTrainService.java:766-768 vs PostTelexPatTrainService.java:747-749 | 任意结算 | 正确率均先 scale=2 除后×100（精度仅整百分比），电报 HALF_UP、电传 type4 HALF_DOWN 不一致，均未走 ScoreMath.accuracy | 舍入模式漂移 |
| PT-P2-17 | PostTelexPatTrainService.java:761-788（764-786） | type4 结算 parseCodeAll 与 convertText 长度不一致 | 三层 try/catch 越界→gGroup=空串，setValue(空串)并 save→用户拍发内容被空串覆盖落库 | 静默降级 |
| PT-P2-18 | PostTelexPatTrainService.java:1193-1197 | ADD/QTA 改错解析异常 | catch 后 rowList.add(group) 把字面量 ADD/QTA 当电码组混入，虚增错组 | 兜底加原串 |
| PT-P2-19 | PostTelexPatTrainService.java:1200-1234 | 标错页(1200-1214)/隔页(1216-1234) 改错解析异常 | 标错页 catch 把指令原串当电码组；隔页 catch 静默丢弃且 z 不推进、后续 token 当正文，双重扣分 | 两处 catch 无堆栈 |
| PT-P2-20 | PostMilitaryTermTrainService.java:495-496/516 | finish 提交含非本训练题目 id，或未 begin 直接 finish | 495 assert 生产失效→496 save(null) IllegalArgumentException 回滚 500；516 startTime null NPE | finish 无状态守卫 |
| PT-P2-21 | PostMilitaryTermTrainService.java:71 vs 98 | 用户不选类型或选中类型部分不足 4 条 | 66-71 convertOne 回调 setTypes(原始 dto.getTypes()) 早于 78-82/98 过滤→落库 types 为过滤前值，列表类型列显示错 | 落库早于过滤 |
| PT-P2-22 | PostMilitaryTermTrainService.java:158/165/170/246 | 军语条目 value 为 null | titleIndex 在未过滤列表随机(158)，dataEntity.getValue() 可能 null→165 options.add(null)、170/246 Pattern.matches(null) NPE→add 回滚 500 | distinct 已滤空但标题选取未滤 |
| PT-P2-23 | PostMilitaryTermTrainService.java:489/496/503/507/523 | 任意 finish | 489 判对时 setCorrectAnswer(userAnswer) 覆写库中正确答案（复盘失真）；496 与 523 每卷保存两次(2N merge)；503 accuracy scale2 / 507 score scale3 精度不一且先舍入后×100 | 多缺陷聚合 |
| PT-P2-24 | PostMilitaryTermTrainService.java:466-508 | dto.testPaperList 含重复题目 id | correctNum 按提交条目累加，分母 testPaperMap.size() 为库中题数→score 可>100，排名污染 | 分子分母口径无绑定 |
| PT-P2-25 | PostTickerTapeTrainService.java:228-233 | 同一 trainId 二次 uploadResult（无状态守卫） | new value 实体只插不删→同(trainId,pageNumber)多行，getById images 重复 | 对照 PostTelexPat 316/PostTelegraphKeyPat 311 先删后插 |
| PT-P2-26 | PostTelegraphKeyPatTrainService.java:340 | 未 begin 直接 finish | beginTime 仅 begin 赋值，340 getBeginTime().toEpochSecond NPE→catch(145)→RuntimeException 回滚 500 | finish 仅幂等守卫无 begin 守卫 |
| PT-P2-27 | PostTrainGlobalRuleService.java:41-44 | 两人同编辑评分规则一方已删 | update 影响 0 行不报错，43 find(id).singleResult() NoResultException→同批已处理规则一并回滚 | singleResult 无空保护 |
| PT-P2-28 | PostEnteringExerciseWordStockService.java:61 vs 69/74-80 | 编辑已有文章改长度；或正文含 @ | 更新分支用 ## 分隔且不重算 wordSize（字数停旧值），新增分支用 @ 分隔（含 @ 正文错拆行） | 两分支分隔符/逻辑漂移 |
| PT-P2-29 | TelexPatTrainService.java:81 + TelexPatTrainStatisticalService.java:75-76/124-148 | 上一条暂停训练自动完成，异步统计 | runAsync 与外层未提交事务并行，statistical 读到未提交/旧 status→漏统一次；异常仅存进被丢弃 future（零日志）；initStatistical 每次 new+saveAndFlush 从不查重 | fire-and-forget |
| PT-P2-30 | TelegramTrainService.java:82-92（92） | getById 该训练 floor 列表为空 | floorEntities.getFirst() NoSuchElementException 500，无 catch | 空集合 getFirst |
| PT-P2-31 | TelegramTrainService.java:174-224（177/211） | controlTelegramTrain 传不存在 train id，或 floorContents 为 null | 177 assert 生产失效→后续 NPE；211 floor.getFloorContents().size() NPE；无 catch→回滚 500 | assert 判空+未判空解引用 |
| PT-P2-32 | PostMilitaryTermTrainService.java:99 | 选中类型全部不足 4 条 | 守卫判 dataMap 而非过滤后 types，types 空时 136 nextInt(0) IllegalArgumentException（非友好 NIPException），回滚 | 守卫检查错变量 |
| PT-P2-33 | EnteringExerciseService.java:117/121 | goTo/pause | 缺 @Transactional（begin 有 rollbackOn），靠 BaseRepository 微事务；两同形状态切换方法一有一无 | 事务标注分裂 |
| PT-P2-34 | PostEnteringExerciseService.java:58-61 | 新建军语/通知文章训练但对应 word_stock 无种子 | wordStockDao.findByType(...) 返回 null，getContent() NPE→回滚 500 | 非 Optional 直接解引用 |

## 4. P3（6 条）
| 编号 | 位置(file:line) | 说明 |
|---|---|---|
| PT-P3-01 | TelegramTrainService.java:77-78；TelexPatTrainService.java:111-112/119-120 | 读接口 generic catch 返回 error() 零日志（登录失效已改抛 UnauthorizedException，仅剩可观测性缺失） |
| PT-P3-02 | GradingRuleService.java:127-128/146-147 | changeGradingRuleIsDefault/deleteGradingRule catch 零日志，把外键约束等原因压成通用 error |
| PT-P3-03 | PostTelegraphKeyPatTrainService.java:202-211 | details 循环内 catch 吞 convertOne 异常，前两页预览少显示一行 |
| PT-P3-04 | TelegraphKeyPatSyntheticalService.java:113/158 | finish 返回 merge 前 entity 而非 save；lastTrain convertOne(null) 产出全 null 假 VO |
| PT-P3-05 | PostEnteringExerciseWordStockService.java:48-50 | view() 空壳 stub 直接 return new DTO |
| PT-P3-06 | TelexPatTrainStatisticalService.java:72 | assert statistical!=null 生产失效（实际由 54 守卫保证非空，属误导性无效断言） |

## 5. 上一轮遗留核销
| 上轮编号 | 上轮结论 | 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| service-core P0-04 | PostTelexPat 结算删报底不回插 | 部分修复 | finish 幂等守卫(230)+@Transactional(rollbackOn 224)；转换在删除前完成(809-817)后 818-819 delete→save，残留 MyISAM 窗口=PT-P1-09 |
| service-core P0-05 | PostTelegraphKeyPat parallelStream 竞态 | 已修复（残留另计） | 357 已改串行；delete→save 窗口=PT-P1-10 |
| service-core P0-08 | EnteringTelexPat Assert 写反 | 已修复 | EnteringTelexPatService.java:50 Assert.isNull |
| service-core P0-09 | 电键 clear 新用户抛/老用户脏行 | 未修复 | TelegraphKeyPatTrainService.java:128 save 在 null 判断外=PT-P1-04 |
| service-core P0-13 / 汇总 P0#22 | 军语出题 while 死循环 | 已修复 | PostMilitaryTermTrainService.java:178 attempts>100 降级；150 distinct>=4；136/158/196 nextInt(size) |
| service-core P0-14 | 抄收 update 4 字段 merge 抹空 | 原结论误报确认 | TickerTapeTrainService.java:108-113 代码未改，仍无调用方（controller 未暴露 update） |
| 汇总 P0#6 | TickerPatUtils 空 catch+PostTelegram 先删后写空化 | 已修复（服务侧） | saveContentValue 529 handleMessageBody 在 537 delete 之前；503 @Transactional(rollbackOn)，TickerPatUtils 改抛 IllegalStateException（CommonInfra 侧） |
| service-core P1-02 | 电报速率系数用反+超速反扣 | 已修复 | PostTelegramTrainService.java:774-779 |
| service-core P1-03 | 多组检测成功不跳过多余组 | 未修复 | MessageComparisonService.java:207-212=PT-P1-07 |
| service-core P1-04 | 多行/少行成功当前组不写入+双统计 | 未修复 | detector/LineDetector.java:182-253=PT-P1-08 |
| service-core P1-05 | 追加报底 floorNumber+=i+无事务 | 部分修复 | 794 @Transactional 已加；803 floorNumber+=i 仍错=PT-P1-03 |
| service-core P1-06 | 划封顶误用点 max | 已修复 | PostTelegramTrainService.java:697 getDash().getMax() |
| service-core P1-07 | test() 写死 trainId 覆盖真实数据 | 未修复 | PostTelegramTrainService.java:830-844 + controller 150-155=PT-P1-02 |
| service-core P1-08 | 抄收状态枚举混用 | 已修复 | PostTickerTapeTrainService.java:309-313 统一 |
| service-core P1-09 | 电传 finish 幂等守卫被注释 | 已修复 | PostTelexPatTrainService.java:230 |
| service-core P1-10 | 电键 finish 无状态守卫 | 已修复 | PostTelegraphKeyPatTrainService.java:137 |
| service-core P1-11 | 抄收 uploadResult 只插不删 | 未修复 | PostTickerTapeTrainService.java:228-233=PT-P2-25 |
| service-core P1-12 | uploadResult images.get 越界/NPE | 未修复 | PostTickerTapeTrainService.java:232=PT-P1-06 |
| service-core P1-13 | 电传速率除零 | 已修复 | PostTelexPatTrainService.java:714 ScoreMath.rate |
| service-core P1-14 | 电键除零守卫检查错变量 | 已修复 | PostTelegraphKeyPatTrainService.java:394 ScoreMath.rate |
| service-core P1-15 | 三处懒生成报底无事务无唯一约束 | 未修复 | PostTelexPat 244/PostTickerTape 260/PostTelegraphKeyPat 232=PT-P2-06/07/08 |
| service-core P1-16 | 电传页码允许 0 | 未修复 | PostTelexPatTrainService.java:255=PT-P2-05 |
| service-core P1-17 | 四处 subList 未校验 size | 未修复 | PostTelegram 226/PostTelexPat 141/PostTickerTape 94/PostTelegraphKeyPat 92=PT-P2-01/02/03/04 |
| service-core P1-18 | 抄收 reset→finish NPE | 未修复 | PostTickerTapeTrainService.java:183/163-167=PT-P1-05 |
| service-core P1-19 | 电键 beginTime null NPE | 未修复 | PostTelegraphKeyPatTrainService.java:340=PT-P2-26 |
| service-core P1-20 | 电传 errorNumber 存正确组数 | 已修复 | PostTelexPatTrainService.java:872-874 |
| service-core P1-21 | 五三码规整两处不一致 | 已修复 | normalizeAdjacentGroups(333-348)，429 与 1022-1035 一致 |
| service-core P1-33 | 评分规则查不到 new 游离假成功 | 已修复 | GradingRuleService.java:55/108/116 orElseThrow |
| service-core P1-67 / sf P1-13 | TelegramTrain.save 全体 catch 部分提交 | 未修复 | TelegramTrainService.java:307-308=PT-P1-01 |
| service-core P1-68 | 电子键平均速率除零+1000 倍 | 已修复 | TelegraphKeyPatTrainService.java:84 ScoreMath.rate |
| service-core P1-69 | 五笔平均速率除零+单位相反 | 已修复 | EnteringTelexPatService.java:79 ScoreMath.rate(totalNum,totalTime*1000) |
| service-core P2-02 | findMessageBody 返回 null 解引用 | 未修复 | PostTelegramTrainService.java:415=PT-P2-11 |
| service-core P2-03 | 用组数与页码比较判下一页 | 未修复 | PostTelegramTrainService.java:507=PT-P2-09 |
| service-core P2-04 | 正确率精度/舍入不一致 | 未修复 | PostTelegram 766 vs PostTelexPat 747=PT-P2-16 |
| service-core P2-05 | detail 四列表长度不一致 | 未修复 | PostTelegramTrainService.java:349-373=PT-P2-10 |
| service-core P2-06 | finish 三处拆箱/解析未校验 | 未修复 | PostTelegramTrainService.java:488/600/776=PT-P2-13 |
| service-core P2-07 | AsyncSave 名不副实死代码 | 已修复 | printBottomReport 555 假 Future/死异常删除，改分页直查 |
| service-core P2-08 | MAX_SIGN_VALUE=99 当下标上限 | 已修复 | MessageComparisonService.java:164-166 走 ComparisonContext.getCurrentSource(130-141) |
| service-core P2-09 | rule 空值兜底无效 | 未修复 | MessageComparisonService.java:84-86=PT-P2-14 |
| service-core P2-10 | 页级少行与 LineDetector 重复计数 | 未修复 | MessageComparisonService.java:245-265=PT-P2-15 |
| service-core P2-11 | speedLog 无幂等 | 已修复 | PostTelegramTrainService.java:511-522 按 floorNumber upsert |
| service-core P2-13 | detector/context 死代码守卫未接入 | 大部分修复 | GroupDetector.shouldSkipGroupDetection(47)/hasMoreSources(122)/getSafeString 已接入；BunchDetector 冗余经 Task8 删除；部分 enum 仍冗余（detector 分片） |
| service-core P2-14 | printBottomReport 读接口写库+负数 | 未修复 | PostTelegramTrainService.java:541/571=PT-P2-12 |
| service-core P2-15 | 正确率守分子非分母 | 已修复 | PostTelexPat 876/PostTelegraphKeyPat 401 ScoreMath.accuracy |
| service-core P2-17 | 抄收 statisticalPage Collections.swap 串位 | 已修复 | TickerTapeTrainService.java:220-222 comparingInt(type) |
| service-core P2-20 | 话报 nextInt(size-1) 空集/漏末条 | 已修复 | PostRadiotelephoneService.java:51/54/59 |
| service-core P2-21 | 电传统计 size!=4 门控+不查重 | 未修复 | TelexPatTrainStatisticalService.java:95/124-148=PT-P2-29 |
| service-core P2-22 | 全局规则 bulk update 0 行+singleResult | 未修复 | PostTrainGlobalRuleService.java:41-44=PT-P2-27 |
| service-core P2-69 | 电传统计 statisticalPage 顺序依赖 DB | 已修复 | TelexPatTrainStatisticalService.java:113 显式排序 |
| service-core P2-91 | 军语精度/两次保存/判对改写答案 | 未修复 | PostMilitaryTermTrainService.java:489/496/503/507/523=PT-P2-23 |
| service-core P2-95 | 词库新增/更新分隔符不同+wordSize 不重算 | 未修复 | PostEnteringExerciseWordStockService.java:61/69/74-80=PT-P2-28 |
| service-core P2-96(片段) | getById 空楼层 getFirst；finish 返回 entity；convertOne(null) | 未修复 | TelegramTrainService.java:92=PT-P2-30；TelegraphKeyPatSyntheticalService.java:113/158=PT-P3-04 |
| service-core P2-A | 缺 @Transactional 清单 | 部分修复 | Synthetical begin/stop/goTo 已补(68/77/92)、addContentValue(794)、WordStock listPage(88) 已补；EnteringExercise goTo/pause 仍缺(117/121)=PT-P2-33；printBottomReport/findMessageBody 仍读接口写库 |
| service-core P1-35 | 军语守卫检查错变量 | 部分修复 | 99 仍判 dataMap，types 空→136 nextInt(0) IllegalArgumentException（非死循环，回滚）=PT-P2-32 |
| service-core P1-36 | nextInt(size-1) 漏末元素 | 已修复 | PostMilitaryTermTrainService.java:136/158/196 nextInt(size) |
| service-core P1-37 | 军语重复 id 使 score>100 | 未修复 | PostMilitaryTermTrainService.java:466-508=PT-P2-24 |
| service-core P1-38 | 军语 assert 失效 save(null) 回滚 | 未修复 | PostMilitaryTermTrainService.java:495-496=PT-P2-20 |
| service-core P1-39 | 军语未 begin 直接 finish NPE | 未修复 | PostMilitaryTermTrainService.java:516=PT-P2-20 |
| service-core P1-40 | 军语落库 types 为过滤前值 | 未修复 | PostMilitaryTermTrainService.java:71 vs 98=PT-P2-21 |
| service-core P1-41 | 军语 value 为 null 出题 NPE | 部分修复 | distinct 已滤空(145-149)、干扰项已判空(200)，标题 titleIndex 未滤、170/246 仍 NPE=PT-P2-22 |
| sf P1-5 / P1-66 | 电传统计异步 fire-and-forget+跨事务读未提交 | 部分修复 | saveTexPatTrain catch 95 setRollbackOnly 修部分提交；runAsync 81 仍读未提交+吞异常=PT-P2-29 |
| sf P1-14 | 电传已入库统计未更新诱导重复提交 | 已修复 | TelexPatTrainService.java:87-102 setRollbackOnly 回滚 |
| sf P1-15 | 报文比对入口静默返回空串 | 部分修复 | processPageComparisons 用 normalizeGroupString(892-903) 单组失败返空串；整体解析异常无 catch→finish(rollbackOn) 回滚不再静默提交；单组空串残留=次要 |
| sf P1-16 | 电传隔页改错静默丢弃 | 未修复 | PostTelexPatTrainService.java:1216-1234=PT-P2-19 |
| sf P1-17 | ADD/QTA 改错失败字面量当电码 | 未修复 | PostTelexPatTrainService.java:1193-1197=PT-P2-18 |
| sf P1-18 | 军语列表把登录失效伪装暂无数据 | 已修复 | PostMilitaryTermTrainService.java:419 getUserByToken 抛 Unauthorized，无吞异常 catch |
| sf P2-1 | 电传拍发内容三层降级空串覆盖 | 未修复 | PostTelexPatTrainService.java:764-786=PT-P2-17 |
| sf P2-2 | 标错页改错当电码组比对 | 未修复 | PostTelexPatTrainService.java:1200-1214=PT-P2-19 |
| sf P2-5 | 读接口零日志出错与无数据不可区分 | 部分修复 | 登录失效已改抛 Unauthorized；TelegramTrain 77/TelexPatTrain 111/119 generic catch 仍零日志=PT-P3-01 |
| sf P2-6 | 删除类接口零日志 | 未修复 | GradingRuleService.java:127/146=PT-P3-02 |
| sf P2-7 | 设为默认查不到伪装成功 | 已修复 | GradingRuleService.java:116 orElseThrow |
| sf P2-12 | 电键详情循环吞异常少显示一行 | 未修复 | PostTelegraphKeyPatTrainService.java:202-211=PT-P3-03 |
| 汇总改级 #5 | PostTelexPat 报底删写→P1(MyISAM 窗口) | 采纳 | =PT-P1-09（待验证引擎） |
| 汇总改级 #10 | 电键 parallelStream→P1 | 主体已修 | 357 串行；残留窗口=PT-P1-10 |
| 汇总改级 #11/#12 | TickerPatUtils patLogs/连坐清空→P1 | 非本片文件 | 根因 common/utils/TickerPatUtils（CommonInfra）；本片 PostTelegram 侧 saveContentValue rollbackOn+TickerPatUtils 改抛后不再静默提交 |
| 汇总改级 #13 | GeneralTickerPat 在线查询失败漏结算→P1 | 非本片文件 | 根因 service/general（ServiceGeneralPat 分片） |

## 6. 待运行验证清单
1. PT-P1-09/10：确认 post_telex_pat_train_page、post_telegraph_key_pat_train_page_value（及 post_ticker_tape_train_page_value）是否 MyISAM；若是，delete→saveAndFlush 中断即永久丢失，升 P0。
2. PT-P1-02：确认库中是否存在硬编码 trainId 46b6bfee-446e-4e71-8192-9616b7ba4ae8 对应数据（决定 test() 是覆盖真实数据还是 NPE 500）。
3. PT-P2-06/07/08：并发两次 GET 同页，验证是否产生双套报底（依赖 DB 无唯一索引）。
4. PT-P2-23：验证 finish 判对分支 setCorrectAnswer(userAnswer) 是否使复盘时正确答案不可恢复。
5. PT-P2-33/GroupNet：确认 controller 是否回填 Page.page（默认 0→page-1=-1 是否触发 Panache 负下标 500）。

## 附录：已接受安全风险（内网口径，不计入计数）
- token 换用户不判空：PostMilitaryTermTrainService.add:64、TickerTapeTrainService/PostTickerTapeTrainService/PostRadiotelephoneService/EnteringExerciseService 等大量 userDao.findUserEntityByToken(token) 直接解引用，token 失效表现为 NPE 500 而非 401（部分服务已改走 userService.getUserByToken 抛 UnauthorizedException=203）。
- 跨用户数据访问：TickerTapeTrainService.getById、PostEnteringExerciseService.getById、GroupNetTrainService.detail、PostMilitaryTermTrainService.details/delete、TelegraphKeyPatSyntheticalService.begin/stop/goTo/finish 等按 id 操作，不校验训练归属。
- 成绩由客户端提交：GroupNetTrainService.submitAnswer(131)、PostRadiotelephoneService.finish 直接落库 vo.getScore()，服务端不判分。
- 列表接口无字段脱敏：PostRadiotelephoneService.assembleData 返回全量训练字段。
