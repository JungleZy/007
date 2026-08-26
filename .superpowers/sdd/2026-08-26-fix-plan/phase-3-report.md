# Phase 3 报告：评分核心统一

**结论：Task 3.1–3.5 全部完成，5 个提交，验收套件 29 测试全绿。**

验收命令（实际类名）：
`flock /tmp/omp-mvn.lock -c "JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest='TickerPatUtils*,ScoreMath*,Post*Test,TickerTapeTrainServiceTest,TelexPatTrainStatisticalServiceTest'"`
→ `Tests run: 29, Failures: 0, Errors: 0` BUILD SUCCESS。

## 提交清单

| Task | 提交 | 内容 |
|---|---|---|
| 3.1 | `5fb76f1` | characterization 快照锁现行为 |
| 3.2 | `1fae6cf` | 确定性缺陷 + P1-01/02/06/20/21 |
| 3.3 | `1e498d3` | ScoreMath 新建与迁移（P2-68/15/51） |
| 3.4 | `409c512` | SpeedDeduct r/l 使用点 + 排序统一（P2-69/17） |
| 3.5 | `4d46edb` | finish 幂等（P1-08/09/10）+ speedLog upsert |

## Task 3.1（红/绿基线）

- 样本入 `src/test/resources/scoring/`：规则 JSON 逐字取自 `project006.sql` `t_grading_rule` type=0 真实行；patKeys 用 `t_cable_floor` 真实字码组（3729/7201/U3YU）。
- **数据事实：dump 内没有任何用户拍发数据**（`t_post_telegram_train_floor_content_value` 等表 0 条 INSERT），patLogs/moresTime/moresValue 按实体 schema 构造、锚定真实 patKeys 与规则。
- `TickerPatUtilsCharacterizationTest` 4 用例快照（`scoring/expected/`，`SCORING_UPDATE=1` 再生成）。初版快照将列对调/错位/覆盖/串号的病态输出全部锁进文件，即修复前的"红"基线。

## Task 3.2（快照 diff = 红→绿证据，见 1fae6cf 中 expected/*.json 变更）

- TickerPatUtils：`:51` patKeys 过滤与 userContents 同步配对；`:116` groupScore 赋值→累加（快照 2→4）；四处 moresTime/moresValue 列对调复原（快照列内容互换）；`:169-170` `add(size-1,·)`→`set`（快照列表长度 5→4 对齐）；旧 `:640` 词完美串号 `getCodePerfectNumber()+1`→`getWordPerfectNumber()+1`（快照 1→3）。
- P1-01 ErrorCodeDetector `:173/:194`：字间隔系数 large→middle（与封顶 middle.max 同段）。
- P1-02 `saveTrainResult`：速率高于加分(l)/低于扣分(r)，与其余五处方向一致；`PostTelegramTrainScoreTest` 断言 ±方向与系数。
- P1-06 `applyDeductions:709` 划封顶 `getDot().getMax()`→`getDash().getMax()`；测试用 dash.max=5≠dot.max=1 断言封顶取 5。两方法降为 package-private static 作测试缝（无行为变化）。
- P1-20 电传 errorNumber 改存真实错误数（错码+多少码），正确率用正确组数。
- P1-21 五三码规整与 convertCodeAll 同语义，抽 `normalizeAdjacentGroups` 静态缝；`PostTelexPatTrainScoreTest` 断言 `23456 789 → 2345 6789`（旧实现产出 `2345 7895`）。

## Task 3.3

- 新建 `common/utils/ScoreMath`：`rate(count, totalTimeMillis)`（次/分钟，零除/零次数返 0，注释注明毫秒）与 `accuracy(correct, total)`（守分母，负分子按 0）。`ScoreMathTest` 5 用例。
- 迁移（旧内联实现全部删除，`grep multiply(new BigDecimal(60))` 确认本域零残留，仅剩 `service/general/**`——他分片域，未动）：
  - PostTelexPatTrainService 速率（顺带修 validTime=0/null 除零/NPE，即 P1-13 同点）与 type≠4 正确率（P2-15 守分子→守分母）；
  - PostTelegraphKeyPatTrainService 速率（顺带修 P1-14 守错变量：原判分子 pat、除 patTime）与正确率（P2-15）；
  - TelegraphKeyPatTrainService 两分支（P2-68：原一支 /1000 一支不除，**按"秒"统一**——多数实现语义 + EnteringTelexPat 注释"时长(秒)"佐证）；
  - EnteringTelexPatService（P2-68，除零返 0）。
- **保守决策**：P2-68 第三处 TelexPatTrainStatisticalService:134 是"各场速率的算术平均"（非 count/time 语义），已有空集保护且被评审标 ✓，强并入 rate() 会改变含义 → 保留原实现，仅在此说明。
- 数值口径说明：rate 单步除法 scale0 HALF_UP，替代旧"中间 scale10（Telex 处为 HALF_DOWN）再 setScale0"，恰好 .5 边界可能相差 1，属统一预期内。

## Task 3.4

- SpeedDeduct r/l 在 TickerPatUtils 词间隔使用点（旧 :633/:637）按字段语义修正：细(低于)→r、粗(高于)→l；ErrorCodeDetector 两处同步同口径（否则同一 wordScore 两个来源系数相反，重蹈 P1-01）。快照 wordScore 21→12。
- 排序统一为显式 sort(type)：删 TickerTapeTrainService:216 `Collections.swap(0,1)`（P2-17）；TelexPatTrainStatisticalService 补齐后排序直返（P2-69）。两个 @QuarkusTest 用逆序插入断言输出 0,1,2(,3)。
- **P2-69 第四写法 TelegramTrainService:400-402（sort 后旋转 2,0,1）在 Phase5 域**：已与 Ph5Schema 协调，其答复"保持现状"，故未统一，此处注明。

## Task 3.5

- P1-08：PostTickerTapeTrainService.checkStatus 统一 `PostTickerTapeTrainStatusEnum` 并同时拦 FINISH(2)/HAS_SCORE(3)，reset 同步换枚举（同值 0）；测试：finish 后重复 finish/begin 抛"训练已结束"，HAS_SCORE 拦 begin。
- P1-09：恢复电传 finish 守卫（FINISH=3 直接返 VO 不重算）；P1-10：电键 finish 加同款守卫（FINISH=2）。**一致化口径**：三服务均有有效前置检查；TickerTape 沿用其 checkStatus 抛异常式（void finish），Telex/KeyPat 返回现有实体 VO（幂等重放）。测试实体只带 status+score、无规则无页数据——守卫失效必走 countScore 抛异常，即红态。
- speedLog 按 floorNumber(1 起) upsert，空洞补 "0"，与 `deleteByTrainIdAndFloorNumber` 的按页覆盖语义对齐；测试断言同页重传 `["90","100"]` 而非追加三条。PostTelegramTrainEntity.speedLog 本仓内无计算消费方（仅重置/展示），补位安全。

## Concerns

1. **checkDotLineGap 内部 r/l 口径不一致（遗留）**：Task 3.4 只点名词间隔两处；同函数 dot/dash/little/large 分支仍是 细→l、粗→r（与词间隔修正后相反）。真实规则 l=1/r=10，方向影响扣分量级。评审文档未裁决这四处，брief 未列 → 按"只改任务列出位置"未动，建议后续统一裁决。
2. TelegraphKeyPatTrainService totalTime 单位取"秒"是裁决（原两分支互斥）；若前端实际传毫秒，avgSpeed 会偏大 60 倍——与旧 create 分支行为一致，非回归。
3. 收尾 `$MVN clean verify` 按全局约束留给合流后统一执行，本域未跑。
4. Task 3.5 幂等测试的"红"未在本分支实跑（需要 stash 反证）；红态依据为评审 P1-09/10 复现路径 + 测试设计上守卫失效必抛异常。
