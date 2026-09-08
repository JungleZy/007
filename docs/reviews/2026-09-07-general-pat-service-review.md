# 结论：通用报底/键控服务分片共 19 条问题 —— P0 0 / P1 3 / P2 11 / P3 5（纯安全项单列附录，不计入）

三个 `General*PatService` 之间存在大量复制粘贴漂移：同一算法（速率加减分、报底再生成的均匀标志、少页判定、在线人员获取、成绩统计）在三处各有一套互相矛盾的实现，其中 Ticker 的速率加减分与 Key/Telex 符号相反、Ticker 报底再生成时把 `isAverage` 判反、Telex 的 `findMessageBody` 直接 `return null`（接口恒返回空）。上一轮的先删后插数据丢失风险在本片已被 `RoomDeletionTransaction`（`@Transactional(rollbackOn=Exception.class)`）+ `RoomLifecycleLocks` 覆盖，未发现新的 P0。

| 项目 | 内容 |
|---|---|
| 审查范围 | `service/general/GeneralKeyPatService.java`(1008)、`GeneralTickerPatService.java`(1000)、`GeneralTelexPatService.java`(834)；`service/builder/MessageResultBuilder.java`；`service/detector/{Bunch,ErrorCode,Group,Line}Detector.java`；`service/enums/{DetectionResult,DetectionType,ProcessingState}.java`；`service/constants/MessageComparisonConstants.java` |
| 审查日期 | 2026-09-07 |
| 审查方式 | 静态阅读 + grep 取证（未运行构建/测试）|
| 计数 | P0 0 / P1 3 / P2 11 / P3 5 |

---

## 0. 三个 General*PatService 算法差异并列表（同一功能 → 三处实现 → 差异 → 判定）

| 功能 | GeneralKeyPatService | GeneralTickerPatService | GeneralTelexPatService | 差异与判定 |
|---|---|---|---|---|
| 报文条数上限 | `add`:171 `Math.min(messageNumber, TrainConstants.MAX_GENERATE_MESSAGE_COUNT)`（=200 常量）| `add`:160-164 硬编码 `if(>200)200` | `add`:132 硬编码 `groupNumber<200?groupNumber:200` | 值都是 200，行为一致；Key 用常量，另两处魔法数字。判定：Key 写法为佳，见 GP-P3-01 |
| 报底再生成时 isAverage 语义 | `getPage`→`generateAndSavePatKey`:263-267 `isAvg.equals(1)`（与 `add`:178 `param.getIsAverage().equals(1)` 一致，1=均匀）| `findMessageBody`:294-303 `entity.getIsAverage()==0`（**0=均匀，与自身 `add`:130/179 相反**）| 走 patType 分支（`add`:134-146），无独立再生成 | Ticker 再生成与其自身入库时相反：>200 组训练的第 3 页起用相反的均匀策略。判定：**Ticker 判反**，见 GP-P1-02 |
| 速率加减分 | `countScore`:814-823 `if(speed>base) score.add(r*diff); else score.subtract(l*diff)` | `saveTrainUserResult`:940-943 `wpm=base-speed; wpmScore=wpm>0?-(wpm*l):wpm*r; score+=wpmScore`（**高于基准时取 `wpm*r`，wpm<0 → 变成扣分**）| `countScore`:763-772 与 Key 同结构 `if/else` | Key/Telex 高于基准=加分，Ticker 高于基准=扣分，符号相反；且 Ticker 用 `SpeedDeduct`、Key 用 `PostKeyPatTrainRuleDto.Wpm`，两者 r/l 语义本就相反。判定：三者不可能同时对，**Ticker 与 Key/Telex 相反**，见 GP-P1-03（上一轮 P1-02 遗留）|
| 查询报底 findMessageBody | :292-299 按 `pageNumber+trainId` 查库返回 dto | :257-349 缺页时同步再生成并入库（`synchronized(this)`）| :341-343 **`return null`** | Telex 接口 `GeneralTelexPatController.findPage` 恒返回 `data:null`。判定：**Telex 未实现**，见 GP-P1-01 |
| 成绩/占比统计 | `statisticsScoreAndDotLineGapRate`:598-645 用 `PatTrainStatisticsBuilder` | :651-761 内联手写循环（含 calculateRate 守卫错、avg 时长恒 0）| :519-569 用 `PatTrainStatisticsBuilder`（多 `withStatusExtractor`）| Key/Telex 已重构为构建器，Ticker 未重构且带 bug。判定：**Ticker 漂移**，见 GP-P2-01/02/03 |
| 少页/缺页计算 | `countScore`:687-730 内联复杂块（按余数分档处理最后一页）| `calculateLackCount`:825-846 独立方法（末页 `i==size-1` 判定）| 依赖 `TelexPatUtils.handle` 的 `isLastPage` 形参，且该形参 :686 传 `pageNumber==pageNumbers.size()-1`（1-based 错位）| 三套完全不同实现；Telex 的 lastPage 形参在 `TelexPatUtils.handle` 中从未被使用，判定实际未生效。判定：**均待验证**，见 GP-P2-04 |
| 在线人员获取 | `findUserInfo`:840-844 `new GeneralKeyPatTrainController().getOneLine()` | `detail`:401-416 `for(;;)` 重试 + `new GeneralTickerPatTrainController().findUserInfo()` | `findUserInfo`:795-797 直接 `getOnline(trainId).getData()` | 三种写法；两处手动 `new` 控制器（仅访问静态 ROOM，不会 NPE）。判定：Telex 直调最干净，见 GP-P2-11 / GP-P3-05 |
| 删除级联（先删后插事务安全）| `delete`:234-254 `RoomLifecycleLocks`+`RoomDeletionTransaction.run` 内删 value/resolver/more/page/user/train | :237-255 同模式（无 resolver/more）| :410-428 同模式（无 resolver/more）| **三者一致**，`RoomDeletionTransaction` 为 `@Transactional(rollbackOn=Exception.class)`，异常整体回滚。判定：已整改，无问题 |

---

## 1. P0

无。

上一轮 service-core 的先删后插数据丢失（P0 组）在本片对应路径已具备事务保护：`delete()` 走 `RoomDeletionTransaction.run`（`ws/service/RoomDeletionTransaction.java:11` `@Transactional(rollbackOn=Exception.class)`）；`countScore`/`saveContentValue` 均为 `@Transactional` 且异常向外抛出（`finish` 的 catch 重抛 `RuntimeException`），JTA 会回滚已删数据，未发现稳定的永久数据丢失路径。

---

## 2. P1

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| GP-P1-01 | `service/general/GeneralTelexPatService.java:341-343` | 前端调 `GeneralTelexPatController.findPage`（`controller/general/GeneralTelexPatController.java:71-72` → `patTrainService.findMessageBody(param)`）| 「查询报底」接口恒返回 `{code:200,data:null}`，电传报底查询功能不可用（Key/Ticker 同名接口均有实现）| `findMessageBody(GeneralTelexPatPageParamDto)` 方法体只有 `return null;`；对照 `GeneralKeyPatService.java:292-299` 查库返回、`GeneralTickerPatService.java:257-349` 再生成返回 |
| GP-P1-02 | `service/general/GeneralTickerPatService.java:294-303` | 报文数 >200 的手键训练，翻到第 3 页起（`add` 只在 :157-210 生成前 200 条），由 `findMessageBody` 缺页再生成 | 再生成页用 `entity.getIsAverage()==0` 作为「均匀」判据，与入库时 `add`:130(`Boolean.TRUE.equals(param.getIsAverage())?1:0`)/:179(`param.getIsAverage()`) 相反 → 同一训练内前 2 页与后续页均匀策略相反，报底分布不一致 | 再生成三处均为 `entity.getIsAverage()==0`；`isRandom` 用 `==1` 则与入库一致，仅 `isAverage` 判反；对照 Key `generateAndSavePatKey`:263-267 用 `isAvg.equals(1)` |
| GP-P1-03 | `service/general/GeneralTickerPatService.java:940-943` | 任何手键训练结算，speed≠base | 速率项：高于基准时 `wpmScore=wpm*r`（wpm<0）→ 结果为负 → `score+=` 变成**扣分**，而 Key(:814-823)/Telex(:763-772) 高于基准为加分；三种拍发训练速率口径相反，横向成绩不可比且落库 | `int wpm=base-speed; int wpmScore=(wpm>0?-(wpm*l):wpm*r); score+=wpmScore;`；对照 Key `if(speed>base) score.add(r*diff)`。另：Ticker `rule.getWpm()` 返回 `dto/score/SpeedDeduct`，Key 返回 `PostKeyPatTrainRuleDto.Wpm`，两套 r/l 语义相反（上一轮附带发现），故「哪个对」待运行验证，但两者不可能同时正确 |

---

## 3. P2

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| GP-P2-01 | `service/general/GeneralTickerPatService.java:651-761` | 手键训练取统计 `statistic` | Ticker 未随 Key/Telex 重构为 `PatTrainStatisticsBuilder`，仍是内联手写循环，是本片多个统计 bug（GP-P2-02/03）的载体，且与另两个服务口径难以保持同步 | Key `:606-621`、Telex `:527-543` 均 `PatTrainStatisticsBuilder.create(...)`；Ticker 为逐字段手工累加循环 |
| GP-P2-02 | `service/general/GeneralTickerPatService.java:737` | 组间隔统计中 `groupGapMin==0` 且 `groupGapMax>0` | `calculateRate(groupGapMin, groupGapMax, groupTotal)` 首参（守卫变量）误用 `groupGapMin`，`ToolUtil.calculateRate`(common:96-97) 判 `min==0` 即返回 0 → groupGapMax 占比被静默算成 0%，统计错误 | 对照 :728-735 其余各项首参均传 `xxxTotal`；:736 `calculateRate(groupGapMin, groupGapMin, groupTotal)` 语义恰好等价故无害。除零不可达（groupGapMin 为 groupTotal 加数）。根因 `ToolUtil` 归 CommonInfra（CI-P1-01）|
| GP-P2-03 | `service/general/GeneralTickerPatService.java:782` | 每次手键训练结算 | `statisticsAllAvg(statisticsVO, 0,0,0,0,0)` 五个时长总量硬编码 0 → 点/划/码/词/组平均时长恒为 0，`statisticInfo` 中该组数据永远无意义 | 方法体 :975-999 用入参 `xxxTotalTime` 计算，但调用点全传 0 |
| GP-P2-04 | `service/general/GeneralTelexPatService.java:686` | 电传结算 `countScore` | `handle(..., pageNumber == pageNumbers.size()-1)`，`pageNumbers` 为 1-based 页号（3 页=[1,2,3]，size-1=2）→ 第 2 页被判为末页，真正末页第 3 页判 false；且 `TelexPatUtils.handle` 的 `isLastPage` 形参(common:26) 全程未使用 → 末页少页判定实际未实现 | grep `isLastPage` 仅出现在形参声明；与上一轮 P2-61（`PostTelexPatTrainService:791`+`TelexPatUtils:26`）同根，此为 general 包新增站点 |
| GP-P2-05 | `service/general/GeneralKeyPatService.java:210-211` | 电缆报底训练 `add`，`isCable=1` 且 `totalNumber<100`，或 `totalNumber/100 > cableFloor.size()` | `int totalPage=totalNumber/100; cableFloor.subList(0,totalPage)`：<100 时 totalPage=0 → 一页报底不生成；>楼层数时 `subList` 抛 `IndexOutOfBoundsException` → `add` 整体回滚，无法建训 | `cableFloorService.findCableFloor(...)` 返回长度未校验即 subList |
| GP-P2-06 | `service/general/GeneralTickerPatService.java:214-215` | 同上（`param.getMessageNumber()/100`）| 同 GP-P2-05 | `int totalPage=messageNumber/100; cableFloor.subList(0,totalPage)` |
| GP-P2-07 | `service/general/GeneralTelexPatService.java:155-156` | 同上（`groupNumber/100`）| 同 GP-P2-05 | `int totalPage=groupNumber/100; cableFloor.subList(0,totalPage)` |
| GP-P2-08 | `service/general/GeneralKeyPatService.java:746-754` | `countScore` 中 `patGroup==0` 而 `errorTotal≠0` | 正确率守卫判 `errorTotal!=0`（分子）而除数是 `patGroup`（分母）→ 该组合下 `divide` 抛 `ArithmeticException`；正常空提交时各计数均 0 故当前难触发，属守卫方向错误 | `int errorTotal=patGroup-error-bunchGroup-lack-more; if(errorTotal!=0) accuracy=errorTotal/patGroup*100`，`待运行验证`可达性 |
| GP-P2-09 | `service/general/GeneralTelexPatService.java:744-750` | 同 GP-P2-08（`errorTotal=patGroup-errorCode-muchLessCode`）| 同 GP-P2-08，守卫判分子不判分母 | `if(errorTotal!=0) accuracy=errorTotal/patGroup*100`，`待运行验证` |
| GP-P2-10 | `service/general/GeneralKeyPatService.java:734-738` | `countScore` 中 `pat!=0` 而 `patTime==0` | 速率计算 `pat/4 / (patTime/1000) *60` 守卫判 `getPat()!=0`，但除数 `patTime/1000` 在 patTime==0 时为 0 → `ArithmeticException` | `if(keyPatStatistics.getPat()!=0){ ... divide(new BigDecimal(patTime).divide(1000...)) }`，`待运行验证` |
| GP-P2-11 | `service/general/GeneralTickerPatService.java:401-416` | `detail` 查询训练详情，获取在线人员 | `for(int i=0;true;i++)` 循环靠「i==1 抛异常」终止，控制流晦涩且脆弱（依赖 `getData()` 异常/非空）；同时手动 `new GeneralTickerPatTrainController()` 绕过 CDI；与 Key/Telex 两套写法漂移 | 对照 Telex `:795-797` 直接 `getOnline(trainId).getData()`；控制器 `findUserInfo` 仅读静态 `PAT_ROOM`（controller:37-40）故 `new` 不 NPE |

---

## 4. P3

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| GP-P3-01 | `GeneralTickerPatService.java:160-161`、`GeneralTelexPatService.java:132` | 建训报文条数上限 | 硬编码 `200`，Key 用 `TrainConstants.MAX_GENERATE_MESSAGE_COUNT`(=200)；值同、语义分散，改常量时漏改风险 | `TrainConstants.java:19` `MAX_GENERATE_MESSAGE_COUNT=200`；Key `add`:171 用常量 |
| GP-P3-02 | `GeneralKeyPatService.java:256-287` | `getPage` 缺页时 `generateAndSavePatKey` 生成报底 | 只 `setTime("[]")`(:278) 未 `setValue("[]")`，而 `add`:203 两者都设 → 按需生成页的 value 落库为 null，与首建页不一致 | 对照 `add`:199-203；`generateAndSavePatKey` 无 `setValue` |
| GP-P3-03 | `GeneralTickerPatService.java:281-289,386-465`、`GeneralTelexPatService.java:808-828` | 阅读维护 | 大段注释掉的旧实现/逐行 `log.info` 注释、`getTrainUserInfo` 冗长块注释，噪声 | Ticker :281-289 注释掉的「上一页最后值」逻辑、:386-465 多处 `// log.info`；Telex :808-828 段落式 javadoc |
| GP-P3-04 | `GeneralKeyPatService.java:864-886` vs `:378-406`；`GeneralTelexPatService.java:296-313` vs `:259-278` | 阅读维护 | `patDetail` 内联复制了 `generatePageAnalyze` 的整段每页统计循环（Key、Telex 各自复制一次）| 两文件内 `patDetail` 与 `generatePageAnalyze` 循环体逐行相同 |
| GP-P3-05 | `GeneralKeyPatService.java:841` | `findUserInfo` | 手动 `new GeneralKeyPatTrainController()` 绕过 CDI（仅读静态 ROOM 故可用），与 Telex 直调风格漂移 | `new GeneralKeyPatTrainController().getOneLine(trainId)`；对照 Telex `:795-797` |

---

## 5. 上一轮遗留核销

上一轮汇总报告与 `2026-08-26-service-core-review.md` 将 `service/general/` 列为**非目标**（`service-core-review.md:10`），故 general 三个服务本身无独立编号条目；下表核销与本片直接相关的引用/移交项及汇总报告 §3-6、附录改级中点名 general 的条目。

| 上轮编号/出处 | 上轮结论 | 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| service-core P1-02（附：`GeneralTickerPatService:918-922` 逐字复制体，`GeneralKeyPatService:790-800`、`GeneralTelexPatService:735-745` 为正确参照）| 速率加减分：高于基准反被扣分，与其他实现相反 | **Ticker 未修复**（代码迁至 `saveTrainUserResult`）；**Key/Telex 保持正确参照** | Ticker 现 `:940-943` 仍 `wpm*r` 致高于基准扣分（=GP-P1-03）；Key `:814-823`、Telex `:763-772` 为 `if(speed>base)加/else 减` |
| service-core 附录 `ToolUtil.java:96` 调用点（点名 `GeneralTickerPatService:717-718`）| `calculateRate` 守卫护错变量，调用点受影响 | **部分修复**：多数调用点(:728-735)已正确传 `xxxTotal`；**:737 仍误传 `groupGapMin`** | GP-P2-02，行号随代码演进为 :737 |
| service-core P2-61（`PostTelexPatTrainService:791`+`TelexPatUtils:26`）同型缺陷 | 1-based 页号用 `size()-1` 判末页错位；`isLastPage` 形参未用 | **general 包同型未处理**：`GeneralTelexPatService:686` 新站点存在同一错位，`TelexPatUtils.handle` 形参仍未用 | GP-P2-04；grep 确认 `isLastPage` 仅在 common:26 形参声明处出现 |
| service-core §3-6「复制粘贴漂移」/汇总 §3-6、附录 C | `findTwoPage` 字段错误已扩散两处；速率/平均速率/页码校验/先删后插多套矛盾实现 | **`findTwoPage` 已修复并被测试锁定**；**速率/统计漂移仍在**（见 0 节表与 GP-P1-03、GP-P2-01~04）；**先删后插已被事务保护** | `GeneralKeyPatPageDao.java:26-28`、`GeneralTelexPatPageDao.java:22-24` 现按 `trainId` 过滤；`src/test/java/com/nip/dao/PatPageFindTwoPageDaoTest.java` 断言只返回目标 train 的第 1/2 页 |
| service-core P2-13（detector 边界守卫，含 BunchDetector 死方法删除）| detector 越界守卫应接入、死方法应删 | **已完成并复核**：`ErrorCodeDetector.shouldSkipErrorCodeDetection`(:75)、`GroupDetector.shouldSkipGroupDetection`(:47)、`BunchDetector.shouldSkipBunchDetection`(:148) 均已接入；BunchDetector 仅剩在用方法 | 见各 detector 源码；`MessageResultBuilder.cachePut`(:193) 亦接入 `clearJsonCache` 满则重置 |

---

## 6. 待运行验证清单

1. GP-P1-02：报文数 >200 的手键训练翻到第 3 页，核对再生成页与首 2 页的均匀分布是否一致（确认 `isAverage==0` 判反的实际观感）。
2. GP-P1-03：同一 `t_grading_rule.content` 分别喂给 Key 与 Ticker 结算，比较高于/低于基准时速率加减分的符号，确认哪套符合业务口径（受 `SpeedDeduct` 与 `PostKeyPatTrainRuleDto.Wpm` 的 r/l 反向语义影响）。
3. GP-P2-04：电传 3 页训练缺中间页结算，确认末页少页判定是否如预期（当前 `isLastPage` 未被 `handle` 使用，预计无差异，用于确认「事实未实现」）。
4. GP-P2-05/06/07：`isCable=1` 且 `totalNumber<100`（预期 0 报底页）与 `totalNumber/100 > 电缆楼层数`（预期 `IndexOutOfBoundsException`）两种建训，确认后果。
5. GP-P2-08/09/10：构造 `patGroup==0 且 lack/error>0`、`pat!=0 且 patTime==0` 的结算输入，确认正确率/速率的除零是否可实际触发（静态判定难以坐实可达性）。
6. 三个 `findAll` 的 `criteriaBuilder.in(...)` 在用户无任何训练（值集合为空）时的 SQL 行为（Key:307-311 / Ticker:356-360 / Telex:185-188），确认 Hibernate 6 是否渲染为恒假条件而非报错。

---

## 附录：已接受安全风险（不计入计数）

- `GeneralTickerPatService.detail`（:404）等处手动 `new` 控制器并跨层调用，属架构耦合而非安全问题，已在 GP-P2-11/GP-P3-05 记维护项，此处不重复。
- 本片三个服务的 REST 入口鉴权（`token`+`deviceId`）、匿名可达性、CORS 等由控制器分片与全局配置统一处置，按内网口径为已接受风险，本片无新增安全项。
