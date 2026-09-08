# 修复 Spec：闭合 2026-09-07 整改波次遗留的 4 项计划偏离（1 项判定为「计划文本滞后、代码更优」，1 项经业务拍板按方案 B 恢复功能）—— 已于 2026-09-08 全部闭合

> 依据：[2026-09-07-fix-plan.md](../plans/2026-09-07-fix-plan.md) 的执行结果节 —— 立项时 41 项 Task 已落地 37 / **偏离 4** / 未落地 0，偏离项分别是 Task 3.2、Task 3.4、Task 5.2、Task 6.3。**四项已于 2026-09-08 全部闭合**，fix-plan 的 Phase 表与合计行重算为 **41/41 已落地、0 偏离、0 未落地**（现落点：Task 3.2 `:412-457`、Task 3.4 `:475-526`、Task 5.2 `:647-668`、Task 6.3 `:740-760`）。
> 取证方式：4 个只读侦察分片分别核实一项，主代理复核关键事实（`git show` 取旧实现、活库查存量行、`/q/openapi` 取运行时 schema）。**立项基线**：HEAD `e319d41`（v1.1.0 已发布），`clean verify` 201 测试全绿；**收口后**：**216 测试全绿**（+15），见下方「验收状态（2026-09-08）」。

**Goal:** 4 项偏离全部有终态结论 —— 该修的修、该改记录的改记录、该由业务拍板的显式提出，不留「待评估」。收口后 `clean verify` 仍全绿，**得分数值与对外 JSON 契约不变**。

**Architecture:** 不把「偏离计划」等同于「有缺陷」。先判定每项偏离的性质，再决定动作：批 1 是真缺口（缺可执行断言）→ 用实现收口替代三份拷贝的测试；批 2 是计划文本滞后（代码严格更优）→ 只修记录并补上唯一未被锁定的点；批 3 的删除动作缺的是产品确认 → 提请业务拍板，**业务已定「功能需要，但不用 poi」**，故按方案 B 恢复三端点；批 4 的「待评估」现在评估完 → 真修并连带家族第二处。

**Tech Stack:** Quarkus 3.20.4 / Java 21 / Hibernate ORM Panache / MySQL 8.0.26。不引新依赖（批 3 曾把「打开 poi」列为方案 A，业务已拍板走**不用 poi** 的方案 B，故本 spec 全程零新依赖）。测试走 `@QuarkusTest` + DevServices `mysql:8.0`。

## 前提事实（执行者必读，均为本次实测）

| 事实 | 值 | 影响 |
|---|---|---|
| 三条速率路径的规则类型 | Ticker `SpeedDeduct` 四字段全 `Integer`；Key/Telex `Wpm.base` 是 `Integer`、**`r`/`l` 是 `BigDecimal`**（`PostKeyPatTrainRuleDto.java:46/51`） | 收口 helper 的 `r`/`l` 必须是 `BigDecimal`，int 化会截断小数系数 → 改分 |
| 三条路径的 `diff` 算法 | 都先把 speed 截成 int 再减（Key `:848` `speed.intValue() - base`） | helper 的 `base`/`speed` 用 int 即可，值不变 |
| Key/Telex 的负号 | `String minus = "-"`（`GeneralKeyPatService.java:790`、`GeneralTelexPatService.java:745`），ASCII 连字符 | 有符号 `BigDecimal.toString()` 的负值与 `minus + 正值` 逐字相同；正值仍需调用方补 `+` 前缀 |
| `ToolUtil` 三参 `calculateRate` | 已不存在（grep 0 命中）。旧实现见 `git show e319d41~5:src/main/java/com/nip/common/utils/ToolUtil.java` 的 `:96-98` | 计划原 `:471` 的 `Modify ToolUtil.java:96-98` 是失效引用 —— **已修正**（现 `docs/plans/2026-09-07-fix-plan.md:479` 改为「Delete `ToolUtil` 三参 `calculateRate`；口径由 `PatTrainStatisticsUtil.calculateRate(count,total)` 承接」）|
| 两张懒建表的存量 | `t_radiotelephone_train` 0 行 / 0 重复；`t_theory_knowledge_test_fallible` 0 行 / 0 重复 | 加唯一约束无需清理存量数据 |
| 两个懒建 DAO | `RadiotelephoneDao.findByUserIdAndType:23-25` 与 `TheoryKnowledgeTestFallibleDao.findByUserId:10-12` 都用 `firstResult()` | 主动容忍重复行，证明现设计未指望唯一性 |
| `%prod` schema 策略 | `generation: validate`，且硬约束「先执行迁移 01→02」 | 批 4 加唯一约束必须同步出迁移 SQL，否则生产启动被拦 |
| poi 依赖 | `io.quarkiverse.poi:quarkus-poi:2.1.1` 与 `quarkus-awt` 均被注掉（`pom.xml:75-83`），无解释性注释 | 批 3 方案 A 要打开它们，直接影响三平台 native 发布 |
| CI native 门禁 | tag 触发构建 linux-amd64 / linux-arm64 / windows-amd64 并对 native runner 冒烟 `/q/openapi` | poi-native 若失败会阻断整条 release |

## 验收状态（2026-09-08）

4 批全部执行完毕，`clean verify` 从 201 增长到 **216 测试全绿**（+15）。下表逐批对照本 spec 自己写的验收口径判定；数字全部引用主代理实测记录（唯一权威，未重跑），源码事实一律给当前工作树的 `file:line`。口径中未达成的项照实标注，不粉饰。

| 批 | 主题 | 本 spec 的验收口径（原文摘要）| 实测结果 | 证据 |
|---|---|---|---|---|
| 1 | Task 3.2 速率加减分收口到单一实现 | ①`clean verify` 全绿、测试数不减；②`src/main/java` 中速率加减分只剩 1 份实现，`getBase()` 在三个 General 服务里不再出现于速率分支；③**得分数值不变** —— 用临时对照脚本对同一组 `(base, r, l, speed)`（含小数系数、`speed` 等于/大于/小于 `base`、`diff` 为 0 的边界）比对改动前后三条路径输出，逐值相等，脚本不留仓 | **全部达成。** ①`Tests run: 216, Failures: 0`（测试数 +15，其中本批 `ScoringConsistencyTest` 3→8 例）；②速率公式（乘/减分支）在三个 General 服务里全部消失，只存在于 `ScoreMath.java:64-72`，`getBase()` 残留恰 3 处且只用于取值/比较（Key `:848`、Telex `:812`、Ticker `:994`）；③**126 组对照 `SCORE: ALL EQUAL` / `LABEL: ALL EQUAL`**（Ticker 54 组 + Key/Telex 72 组，旧实现逐字取自 `e319d41`；矩阵 `base ∈ {0,20,100}` × `speed ∈ {base-10,base-1,base,base+1,base+10,0}` × `(r,l) ∈ {(1,1),(2,3),(1.5,2.5),(0,0)}`，Ticker 的 `r`/`l` 是 `Integer` 故 `(1.5,2.5)` 组跳过），脚本已删。**对照脚本抓到一个真实回归**：第一版用 `speedScore.signum()` 决定标签，`(r,l)=(0,0)` 时整个丢掉 `speedScore` key（旧代码输出 `"+0"`/`"-0"`）→ **14/72 组 LABEL DIFF**，已改回按方向判断 | `ScoreMath.java:64-72` + javadoc `:51-63`；委托点 `GeneralTickerPatService.java:991-997`、`GeneralKeyPatService.java:849-851`、`GeneralTelexPatService.java:813-815`；标签方向仍用 `wpmBase` 的两处 `GeneralKeyPatService.java:848/854-858`、`GeneralTelexPatService.java:812/818-822`；测试 `ScoringConsistencyTest.java:13-23`（头注释已改写）、`:64/77/90/104/115`（新增 5 例）。RED 实证 A（int 截断 + 去 null 守卫）`Tests run: 8, Failures: 1, Errors: 1`；RED 实证 B（系数方向对调）同样变红；恢复后 `Tests run: 13, Failures: 0`（含既有 `ScoreMathTest` 5 例）|
| 2 | Task 3.4 不回退代码，修正记录并补服务级锁 | ①新用例经「**回退→变红→恢复→回绿**」实证非空转；②`grep -n "ToolUtil" docs/plans/2026-09-07-fix-plan.md` 不再指向已删除的方法 | **全部达成。** ①`TickerGapRateNumeratorTest` 2 例走公开入口 `GeneralTickerPatService.statistic(...)`（未用反射）；**RED 实证**：把 `GeneralTickerPatService.java:768` 首参改回 `groupGapMin` → `Tests run: 2, Failures: 2`，断言原文含「组间隔粗 = groupMaxNumber/groupTotal = 5/10 = 50%；拿到 0 说明分子不是 groupGapMax」，恢复后回绿、零残留。两例合起来让两行的分子身份各自唯一确定，任一行首参回退或两行对调都必红；②fix-plan 的 `Files` 行已改为「**Delete** `ToolUtil` 三参 `calculateRate`；口径由 `PatTrainStatisticsUtil.calculateRate(count,total)` 承接」，Task 3.4 状态改为「已落地（原判偏离不成立）」并附上等价性表；文件里余下的 `ToolUtil.java:96-98` 引用只在「事实（逐字，**修复前**状态）」代码块里，已显式标注为历史留档 | `docs/plans/2026-09-07-fix-plan.md:477`（状态）、`:479`（Files）、`:481`（历史留档标注）、`:496-522`（Step 3 + 等价性表 + 消费链纯读）；`src/test/java/com/nip/service/TickerGapRateNumeratorTest.java:89/105`；守卫 `PatTrainStatisticsUtil.java:71-74`；造数发现（`%test` 是 `drop-and-create`、String → `varchar(255)`、完整 20 字段 `statistic_info` JSON 撞 `MysqlDataTruncation`）已写进测试注释 |
| 3 | Task 5.2 上传导出功能恢复（方案 B） | ①`saveBatch` 正常批入库后可被 `findAllQuestionByLevelId` 查到；空集与缺必填字段各断言明确错误；**一行失败整批回滚**；②`exportTemplate` 列规格非空且字段名与 `TheoryKnowledgeQuestionDto` 逐一对应；③`uploadFileToNip`：纯文本返回 `type=2` 且 `wordContent` 等于文件内容、`.docx` 二进制被拒且文案明确、空文件被拒；④`clean verify` 全绿且 `TheoryKnowledgeUploadExportTest` 里的 404 断言全部替换为新契约断言 | **全部达成。业务决策：2026-09-08 用户拍板「功能需要，但不用 poi」（方案 B）**，三端点按仓内现成分工恢复（Excel 由前端解析、后端收 JSON 行 / 只给列规格）。①②③④ 逐条有断言：`batchImportPersistsRowsAndExportReadsThemBack`、`batchImportRejectsEmptyPayloadAndRowsMissingRequiredFields`（断言「第 2 行缺少题目」）、`batchImportRollsBackEveryRowWhenOneRowIsInvalid`（断言库里零行）、`templateColumnsMatchTheBatchImportContract`、`plainTextUploadReturnsItsContentAndOfficeFormatsAreRejected`（txt → `type=2` + 原文；`.docx` 魔数被拒含「仅支持纯文本文档」；空文件被拒含「文档内容为空」）；原先断言三条 404 的 `emptyShellUploadAndExportEndpointsAreGone` 已删除。**能力边界达成**：Office/二进制格式一律抛明确业务错误，**不静默返回空 VO**；`.docx`/`.pptx` 解析与「PPT 转图片」（`imgUrls`、`type=1`）明确不做；**不做持久化**（原 `updateFileToNip` 本就无落库目标）| 端点 `TheoryKnowledgeQuestionController.java:86-92`（`saveBatch`）、`:94-99`（`exportTemplate`）、`TheoryKnowledgeController.java:176-182`（`uploadFileToNip`，`@RestForm("file") FileUpload`）；服务 `TheoryKnowledgeQuestionService.java:185-218`（空集 `:187-189`、逐行指出第几行 `:194-206`、token 解析一次 `:190`、`@Transactional` `:185`）、`:227-241`（6 列规格）、`TheoryKnowledgeClassifyService.java:107-134`（边界 javadoc `:97-106`、格式拒绝 `:116-119`、空内容拒绝 `:126-128`）；新 VO `src/main/java/com/nip/dto/vo/TheoryKnowledgeQuestionTemplateColumnVO.java`；测试 `TheoryKnowledgeUploadExportTest.java:60/81/95/110/120`（+ 既有 `:146`）；prod jar 实测（1.1.0，18002，默认 `%prod`）三端点 bogus token → 200 + `code 206`，旧误导路径 `theoryKnowledgeQuestion/upLoadFile` → **404** |
| 4 | Task 6.3 把「待评估」评估完并修家族第二处 | ①并发回归测试：两线程同时对同一 `(userId, type)` 首调 `listPage`，断言表中只有 1 行、两个调用拿到同一行；家族第二处同理；②`clean verify` 全绿；③迁移演练双快照 PASSED（`table count`、`MyISAM=0`、实体表差分 0 字节三项断言全绿）；④`%prod` 默认 profile（`validate`）下 prod jar 能启动 | **全部达成。** ①`ReadPathLazyCreateConcurrencyTest` 4 例，确定性不靠抢跑概率 —— 两线程各自先用 `QuarkusTransaction.requiringNew()` 读一次固定 REPEATABLE READ 快照，都读完才在 `CyclicBarrier` 放行 → 双方必然都走懒建分支；`concurrentFinishAccumulatesOntoTheSameRow` 断言 1 行且 `totalCount=2` / `totalTime="60"`，直接锁住「计数割裂」这个真实危害。**RED 实证**：去掉两个实体的唯一约束 → 3 个用例 `expected: <1> but was: <2>`，已恢复、零残留；②`Tests run: 216, Failures: 0`；③双快照演练 **PASSED**，除口径三项外**另加两条唯一索引断言**（两侧各 2 条全 PASS）：表计数 105、MyISAM 0、`diff-current.txt`/`diff-base.txt` 均 0 字节，`timings.tsv` `current 182 2450 110` / `base 644 2431 165`；活库连跑迁移 03 三次，第三次 `mysql exit=0` 且索引列数仍为 3 → 幂等确证；④prod jar `quarkus-template 1.1.0 ... started in 2.506s`，**无** `SchemaManagementException` —— 实体新增的唯一约束被 `validate` 接受 | 唯一约束 `RadiotelephoneEntity.java:20-21`、`TheoryKnowledgeTestFallibleEntity.java:26-27`；支撑件 `IdempotentWrite.java:31-34`（`REQUIRES_NEW`）、`:42-52`（异常链）、`:12-21`（三条「为何不能原地重读」硬理由）；改造点 `RadiotelephoneService.java:41-52/54-61/63-87/89-102`、`ComprehensiveService.java:343/359-372/374-384`；空键校验 `RadiotelephoneService.java:71-77`、`ComprehensiveService.java:360-363`；测试 `ReadPathLazyCreateConcurrencyTest.java:58/81/100/122`；迁移 `backend/database/migrations/2026-09-08-01-unique-lazy-create.sql`；脚本 `scripts/rehearse-migrations.sh:62/64/162-164/186-188`；演练产物 `backend/database/rehearsal/2026-09-08/`；评审结论 `docs/reviews/2026-09-08-migration-rehearsal.md` |

**批 4 的行为面取舍（口径未要求，但必须记录）：** `finish`/`listPage` 的写入移进独立事务后，写入本身仍原子，但写完之后若 `PojoUtils.convertOne` 抛异常，增量**不再随外层 `@Transactional(rollbackOn = Exception.class)` 回滚**（`RadiotelephoneService.java:54-60`）。写后只剩一次纯内存 POJO 拷贝，判定可接受 —— 这是换取「撞唯一键可恢复」的必要代价。

### DoD 逐条结论

| # | 口径 | 判定 | 证据 |
|---|---|---|---|
| 1 | `clean verify` 全绿，测试数不减（起点 201）| **达成** | `exit=0`、`Tests run: 216, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`。净增 15 例 = `ScoringConsistencyTest` 3→8（+5）、`TickerGapRateNumeratorTest` 2 新建、`ReadPathLazyCreateConcurrencyTest` 4 新建、`TheoryKnowledgeUploadExportTest` 2→6（新增 5、删 1）|
| 2 | 批 1 收口后**得分数值逐值不变**，有对照证据 | **达成** | 126 组对照 `SCORE: ALL EQUAL` / `LABEL: ALL EQUAL`；对外契约未变：`deductMap` key 名（Ticker `wpmScore` int、Key/Telex `speedScore` 带符号 String）、`speedScore` 值格式、`speedNumber`、speed 上游取整（Ticker `HALF_DOWN` / Key/Telex `HALF_UP`）全部原样。对照过程还抓出并修掉一个自造回归（`signum()` 判标签 → `(r,l)=(0,0)` 丢 key，14/72 组 LABEL DIFF）|
| 3 | 批 2 的新用例经「回退→变红→恢复→回绿」实证非空转 | **达成** | `GeneralTickerPatService.java:768` 首参回退 → `Tests run: 2, Failures: 2`；恢复后回绿，零残留 |
| 4 | 批 4 的并发用例能复现双插（未加约束时变红）| **达成** | 去掉两个实体的唯一约束 → 3 个用例 `expected: <1> but was: <2>`；恢复后回绿，零残留 |
| 5 | 批 4 后迁移演练双快照 PASSED，且 prod jar 在默认 `%prod` 下启动成功 | **达成** | 双快照 PASSED（含两侧各 2 条 `unique index exists` 断言）、表计数 105 / MyISAM 0 / 差分 0 字节；迁移 03 活库连跑三次幂等；prod jar `1.1.0 ... started in 2.506s`，无 `SchemaManagementException` |
| 6 | 4 项偏离在 `docs/plans/2026-09-07-fix-plan.md` 里全部有终态结论，无一停留在「偏离计划」而无下文；批 3 若被业务确认为「不需要」，其理由须是产品结论而非技术借口 | **达成** | `grep -c '\*\*状态:\*\* 偏离计划' docs/plans/2026-09-07-fix-plan.md` → **0**（余下 3 处「偏离计划」字样均在「已落地（2026-09-08 闭合原『偏离计划』）」的状态行里，是闭合记录而非未决状态）；Phase 表与合计行重算为 **41/41 已落地、0 偏离、0 未落地**（`docs/plans/2026-09-07-fix-plan.md:25`（Phase 3）、`:27`（Phase 5）、`:28`（Phase 6）、`:30`（合计））。批 3 未被确认为「不需要」—— 业务拍板的是「**需要，但不用 poi**」，功能已恢复，故「技术借口」这一风险点自然消解 |

**本次范围外、仍未达成（照实记录）：** ①「每 Task 一次提交」—— v1.1.0 已推送，历史无法追溯重写，永久未达成；②分片文档批量标注「已在 fix-plan 批 N 处理」—— 14 份文档的独立工作量。两项均在下方「明确不做的事」表中列明。另：`%test` 的 `drop-and-create` 把长文本 String 映射成 `varchar(255)`，与活库 longtext 不一致（批 2 造数时撞到）；`validate` 容忍字符串族差异故生产无碍，但测试侧无法承载完整长 JSON —— 属新发现的测试基建限制，未修。


---

## 批 1：Task 3.2 —— 速率加减分收口到单一实现

**判定：偏离成立，但性质是「缺可执行断言」而非「实现不一致」。** 三条 in-scope 路径的公式当前已同口径（`speed > base` → `r × diff` 加分；`speed < base` → `l × diff` 扣分；`speed == base` → 0；三者均无 `getMax()` 封顶）。计划 Step 3 想用测试证明三份拷贝一致，本批改为**让它们只剩一份实现** —— 结构性保证强于三份拷贝各测一遍。

**Files:**
- Modify `src/main/java/com/nip/common/utils/ScoreMath.java`（现有 `rate():25-32`、`accuracy():41-49`，`final` 类 + 私有构造 `:10-16`，无 wpm）
- Modify `src/main/java/com/nip/service/general/GeneralTickerPatService.java`（`calculateWpmScore:988-996`，调用点 `:969-971`）
- Modify `src/main/java/com/nip/service/general/GeneralKeyPatService.java`（wpm 段 `:846-857`，位于私有 `countScore:685`，公开入口 `saveContentValue:429` 在 `:476` 调用）
- Modify `src/main/java/com/nip/service/general/GeneralTelexPatService.java`（wpm 段 `:809-820`，位于私有 `countScore:716`，公开入口 `saveContentValue:396` 在 `:473` 调用）
- Modify `src/test/java/com/nip/service/ScoringConsistencyTest.java`（头注释 `:12-15` 记的阻碍将失效，需改写；`:28-51` 现只断言 Ticker 一侧）

**内容：**

1. `ScoreMath` 新增唯一速率项实现：

   ```java
   public static BigDecimal wpmScore(int base, BigDecimal r, BigDecimal l, int speed)
   ```

   `speed > base` → `r × (speed − base)`；`speed < base` → `−(l × (base − speed))`；`speed == base` → `BigDecimal.ZERO`。返回值**带符号**（正数加分、负数扣分）。`r`/`l` 为 `null` 时按零系数处理，不抛。

2. Ticker：`calculateWpmScore(SpeedDeduct, int)` **保留方法与签名**，方法体改为委托 `ScoreMath.wpmScore(...)` 后 `.intValue()`。`SpeedDeduct` 四字段全 `Integer`，转 `BigDecimal` 再 `intValue()` 逐值相等 → 得分不变，`ScoringConsistencyTest` 现有 3 条断言（20 / 0 / −30）不需改。

3. Key/Telex：`:846-857`、`:809-820` 的 if/else-if 内联分支替换为一次 `ScoreMath.wpmScore(...)` 调用，再按各自现有方式累加（`score = score.add(结果)`，负值天然做减）。

   **不改** `deductInfo`/`deductMap` 的 key 名与值格式：Ticker 是 `wpmScore`（int），Key/Telex 是 `speedScore`（带符号 String，正值 `"+" + 值`、负值 `"-" + 值`）。这是对外 JSON 契约，前端与历史 `deductInfo` 依赖，改即 API 破坏。正值的 `+` 前缀仍由调用方拼。

   **不改** speed 的上游取整（Ticker `HALF_DOWN`、Key/Telex `HALF_UP`），它在 helper 之外。

4. 测试改造 `ScoringConsistencyTest`：
   - 锁单一实现 `ScoreMath.wpmScore` 的符号、方向、零点；
   - 加一例 `r=1.5, l=2.5` 证明小数系数未被截断（这是「不改分」底线的守门测试）；
   - 对三个调用方各断言一次「同一 `(base, r, l, speed)` 下速率项符号一致」，取代计划 Step 3 想要的跨类型断言；
   - 头注释 `:12-15` 记的「Key/Telex 内嵌私有 `countScore` 无法直接调用」阻碍随收口消失，改写为收口后的口径说明。

**验收：**
- `clean verify` 全绿，测试数不减。
- `src/main/java` 中速率加减分只剩 1 份实现：`grep -n "getBase()" src/main/java/com/nip/service/general/` 在三个 General 服务里不再出现于速率分支。
- **得分数值不变**：用临时对照脚本对同一组 `(base, r, l, speed)`（含 `r`/`l` 带小数、`speed` 等于/大于/小于 `base`、`diff` 为 0 的边界）比对改动前后三条路径的输出，逐值相等。脚本是验证手段，不留仓。

**风险：** 得分口径是业务底线。helper 若把 `r`/`l` 收成 int 会截断 Key/Telex 的小数系数 → 直接改分，**禁止**。

---

## 批 2：Task 3.4 —— 不回退代码，修正记录并补服务级锁

**判定：偏离不成立。当前实现严格优于计划字面，应修正记录而非回退代码。**

计划 Step 3 自己写的目标（`:490-496`）就是把守卫改成 `total == 0 || max == 0` 并让首参 `min` 变成**死参数**；「只改守卫、不删参数」是排期约束（删参数被排到 Phase 7 / Task 7.6），不是验收项。实际执行把两步合一：三参方法删除、13 处调用迁到 `PatTrainStatisticsUtil.calculateRate(count, total)`（守卫 `:72-74`）。

逐处核对结果（旧实现取自 `git show e319d41~5`，13 处旧调用逐行比对）：

| 输入 | 旧三参 `min==0 ? ZERO : max/total×100` | 新两参 `total==0\|\|count==0 ? ZERO : count/total×100` | 等价性 |
|---|---|---|---|
| `total>0, count>0` | `count/total×100` | 同 | 等价 |
| `count==0`（11 处首参=分子或=total 的正确站点） | 0 | 0 | 等价 |
| `total==0 && count==0` | ZERO | ZERO | 等价 |
| `total==0 && count>0` | `divide` 抛 `ArithmeticException` | ZERO | 新更安全（该输入不可达，`total` 含 `count`） |
| 旧 `:737` `calculateRate(groupGapMin, groupGapMax, groupTotal)` 且 `groupGapMin==0 && groupGapMax>0` | **返回 0%（既有 bug）** | `groupGapMax/groupTotal` 正确 | 新正确，即 Step 2 的修复意图 |

**13 处调用无一丢守卫或改语义**，唯一可达的行为变化就是 `:737` 的既定 bug 修复。消费去向已核实为纯读：`statisticsScoreAndDotLineGapRate` → `statistic():648-653` → `GeneralTickerPatController.statistic:100-103` 直接返回 VO，**不落库**。

**Files:**
- Modify `docs/plans/2026-09-07-fix-plan.md`（立项时的落点：`**状态:**` 行 `:469`、`Files` 行 `:471`、Step 3 `:486-487`；**已改完**，现落点 `:477`／`:479`／`:496-522`）
- Add 服务级回归用例（补计划 Step 1 要求的红点，类名与落点见下）

**内容：**

1. 记录修正：
   - `:471` 的 `Modify ToolUtil.java:96-98` 是失效引用（该方法已不存在）→ 改为「Delete `ToolUtil` 三参 `calculateRate`；口径由 `PatTrainStatisticsUtil.calculateRate(count,total):71-79` 承接」。**已改完**（现 `docs/plans/2026-09-07-fix-plan.md:479`）。
   - `:469` 与 `:486-487` 撤销「本步『只改守卫，不删参数』的约束因此未被遵守」的判语，改为「Step 3 与 Task 7.6 合并完成」，并附上表的等价性论证。Task 3.4 的状态从「偏离计划」改为「已落地」。**已改完**（状态行 `:477`、Step 3 与等价性表 `:496-522`；文件里余下的 `ToolUtil.java:96-98` 引用只在 `:481-488` 的「事实（逐字，**修复前**状态）」代码块里，已显式标注为历史留档）。
2. 补服务级回归测试：`groupGapMin=0, groupGapMax=5, groupTotal=10` 时断言 `GroupGapMax` 为 50%。
   现状是 util 级 `CalculateRateTest.java:16-35` 只锁两参契约（`total==0`、`count==0`、取整），**服务层的首参传递无锁** —— 若有人把该行首参改回 `groupGapMin`，CI 逮不到。这正是计划 Step 1 的红点，立项时工作树不存在。**已补齐**：`src/test/java/com/nip/service/TickerGapRateNumeratorTest.java:89/105` 两例走公开入口 `GeneralTickerPatService.statistic(...)`。

**验收：** 新用例在把对应行首参改回 `groupGapMin` 时变红、改回来变绿（按「回退→变红→恢复→回绿」实证）；`grep -n "ToolUtil" docs/plans/2026-09-07-fix-plan.md` 不再指向已删除的方法。

**风险：** 无。纯 `BigDecimal` 静态函数，不涉反射与序列化，`reflection-config.json` 无关，三平台 native 无影响。

---

## 批 3：Task 5.2 —— 上传导出功能恢复（业务已定：方案 B，不用 poi）

**判定：部分成立。** 计划默认「实现优先」，执行方改为「删除优先」，理由写的是**技术不可行**（poi 被注掉）—— 而计划 Step 2 允许删除的前置条件是「**产品确认不需要**」。缺的是产品确认，不是技术论证。

但取证显示，删除这个**结论**被证据强力支持，反而是计划的「实现优先」默认值站不住：

| 证据 | 内容 |
|---|---|
| 零消费方 | 三个端点（`theoryKnowledgeQuestion/upLoadFile`、`/exportTemplate`、`theoryKnowledge/uploadFileToNip`）全仓无引用，前端 `src/main/resources/resources/index.html` 与 `docs/` 均无命中 |
| 作者原始声明 | `upLoadFile` 的 `@Operation` 原文写着「新框架不做上传功能」（原文见 `docs/reviews/2026-08-26-controller-api-review.md:204-220,540-544`） |
| 全项目既定约定 | 后端只回 JSON 行、Excel 文件由前端生成。范式就是保留下来的 `exportQuestionByLevelId`（`TheoryKnowledgeQuestionService.java:170-175`，直接返回实体列表，零 poi，javadoc 写明「后端只提供数据，由前端生成文件」）|
| 计划引作参照的实现本身是空壳 | `PostEnteringExerciseWordStockService.view():48-50` 只 `return new ...Dto()`；真正的入库在 `add():52-86`，把 DTO 的 content 字符串按换行 split 后 JSON 落库 —— **全仓没有任何真实 Excel 解析代码** |
| 被删的三个方法体 | `updateFileToNip` 取完 user 直接 `new` 全 null VO（`file` 形参从未使用且缺 `@RestForm`）；`upLoadFile` 只 `return success()`；`exportTemplate` service 体 100% 是注释、零可执行语句 |

**方案对照：**

| 方案 | 做法 | 工量 | native 风险 | 需业务拍板 |
|---|---|---|---|---|
| A | 打开 `quarkus-poi` + `quarkus-awt`，真实实现 Excel 上传解析与模板导出 | 高（三端点 + 解析/生成逻辑 + 反射注册 + 三平台 native 验证） | **高**。poi 重度依赖反射与资源加载，`quarkus-awt` 同时被注掉暗示曾有 native 问题；失败会阻断 linux-arm64 / windows-amd64 的 release。只读环境无法预先证明可行 | 是 |
| B | 不用 poi，按全项目约定实现：导出端点只回 JSON（前端生成文件）；上传端点解析 CSV/纯文本而非 Excel | 中 | 低 | 是（功能形态与产品预期不同） |
| C | **维持删除**，把理由从「技术不可行」换成「零消费方 + 全项目约定 + 作者原始声明」，并记入持久位置 | 低 | 无 | 是（只需一次「确认不需要」） |

**业务决策（2026-09-08，用户拍板）：走方案 B —— 功能需要，但不引 poi。**

方案 B 不是折中，它命中了**仓内已有的现成范式**：`MilitaryTermDataController.saveBatch:84-89`
的 `@Operation` 原文写着「批量保存军语密语-**代替之前文件导入**」，服务端
`MilitaryTermDataService.saveBatch:207-214` + `excelHanle:217-252` 收的是 `List<MilitaryTermDto>`（JSON 行），
**Excel 由前端解析**。同一分工在 `exportQuestionByLevelId` 的 `@Operation` 里也写着
「后端只提供数据由前端生成文件导出」。所以「不用 poi」是本仓早就定下的分工，不是本次的妥协。

**三个端点在方案 B 下的落地形态：**

| 原端点 | 新形态 | 依据 |
|---|---|---|
| `theoryKnowledgeQuestion/upLoadFile`（原签名**无任何入参**，只 `return success()`） | `POST /theoryKnowledgeQuestion/saveBatch`，收 `List<TheoryKnowledgeQuestionDto>` 批量入库 | 逐字照 `MilitaryTermDataService.saveBatch` 的先例，含空集校验文案「导入数据为空或格式不完整」 |
| `theoryKnowledgeQuestion/exportTemplate`（原 `void` + `HttpServerResponse`，service 体 100% 注释） | 返回 `Response<...>`，给出导入模板的**列规格 JSON**（字段名/中文标题/是否必填/示例），前端据此生成 .xlsx | 与 `exportQuestionByLevelId` 同一分工；同时消掉本层唯一的 `void`→204 破例 |
| `theoryKnowledge/uploadFileToNip`（原取完 user 直接 `return new TheoryKnowledgeDocumentContentVO()`，`file` 形参从未使用且缺 `@RestForm`） | 收 `@RestForm("file") FileUpload`，按 UTF-8 读**纯文本**并返回 `type=2` + `wordContent` | 契约保持 `TheoryKnowledgeDocumentContentVO`；`FileUpload` 的正确注解照 `PostEnteringExerciseWordStockController:52` |

**方案 B 的能力边界（必须写进代码 javadoc 与 API summary，不许含糊）：**
`uploadFileToNip` 只能处理纯文本（`.txt`/`.md`/`.csv`）。`.docx`/`.pptx` 的解析与
「PPT 转图片」（`TheoryKnowledgeDocumentContentVO.imgUrls`，`type=1`）**没有 poi 就做不到**，
遇到 Office/二进制格式必须抛明确业务错误，**禁止**静默返回空 VO —— 那正是本次要消灭的假成功。
需要 Word/PPT 时，由前端解析后走既有的课件内容保存路径（`TheoryKnowledgeSwfEntity.content`）。

**不做持久化**：原 `updateFileToNip` 就没有落库目标（`TheoryKnowledgeSwfEntity` 从未与该端点接线），
本次不发明存储设计，只补齐「读取并返回内容」这一原有契约。

**Files:**
- Modify `src/main/java/com/nip/controller/TheoryKnowledgeQuestionController.java`（补 `saveBatch` 与 `exportTemplate`）
- Modify `src/main/java/com/nip/service/TheoryKnowledgeQuestionService.java`（现有方法见 `:58`/`:173`；批量入库照 `saveTheoryKnowledgeQuestion:57-83` 的字段搬运口径）
- Modify `src/main/java/com/nip/controller/TheoryKnowledgeController.java`（补 `uploadFileToNip`）
- Modify `src/main/java/com/nip/service/TheoryKnowledgeClassifyService.java`（承接文本读取）
- Add 模板列规格 VO（放 `dto/vo/`）
- Modify `src/test/java/com/nip/controller/TheoryKnowledgeUploadExportTest.java`（`:50-61` 现断言三条 404，需改为断言新契约；`:63-80` 的 `exportQuestionByLevelId` 断言保留）

**内容：**
1. `saveBatch`：空集/`null` 抛 `IllegalArgumentException("导入数据为空或格式不完整")`；逐行校验 `topic`/`type`/`levelId` 必填，缺字段抛明确错误并**指出是第几行**；`createUserId` 从 token 解析一次，不在循环里查库；`@Transactional` 整批回滚（照批 1 波次已定的铁律，异常一律不吞）。
2. `exportTemplate`：返回列规格，字段顺序与 `saveBatch` 接受的 DTO 一致 —— 模板与导入口径必须同源，否则前端按模板填的表导不进来。
3. `uploadFileToNip`：`@RestForm("file") FileUpload`；空文件、超限（沿用 `application.yml:47` 的 `max-file-size: 10M`）、非文本格式各有明确错误；读取用 UTF-8，失败不吞。

**验收：**
- `saveBatch` 回归测试：正常批入库后可被 `findAllQuestionByLevelId` 查到；空集与缺必填字段各断言明确错误；**一行失败整批回滚**（这是本仓 `@Transactional` 铁律的必测项）。
- `exportTemplate` 断言列规格非空且字段名与 `TheoryKnowledgeQuestionDto` 逐一对应（防模板与导入口径漂移）。
- `uploadFileToNip` 断言：纯文本上传返回 `type=2` 且 `wordContent` 等于文件内容；`.docx` 二进制被拒且错误文案明确；空文件被拒。
- `clean verify` 全绿；`TheoryKnowledgeUploadExportTest` 里的 404 断言全部替换为新契约断言。

**风险：** 不引依赖、不动 native 配置，三平台 native 无影响。唯一风险是能力边界被误解为「支持 Excel/Word 上传」—— 靠 API summary 与错误文案顶住。

---

## 批 4：Task 6.3 —— 把「待评估」评估完，并连带修家族第二处

**判定：偏离成立。** Step 3 要求的「`listPage` 是读接口带写副作用」标注在任何交付物中都不存在。本批不满足于补一个标注 —— 直接把这个「待评估项」评估完并修掉。

**评估结论：确有并发缺陷，但后果是计数割裂而非数据丢失。**

| 事实 | 证据 |
|---|---|
| `listPage` 在 `@Transactional` 里查不到就 `save` 懒建 | `RadiotelephoneService.java:36-53`（`:41` 查 null → `:42-47` new + save） |
| 懒建表无任何唯一约束 | `RadiotelephoneEntity.java:20-22` 只有 `@Id @GeneratedValue(UUID)`，无 `@Table`/`@UniqueConstraint`/`@Column(unique)`；活库 DDL 也只有 `PRIMARY KEY (id)` |
| DAO 主动容忍重复 | `RadiotelephoneDao.findByUserIdAndType:23-25` 用 `firstResult()` |
| DAO 写入始终提交 | `BaseRepository.save:14-23` 是方法级 `@Transactional`，id 空则 `persist` |
| 后果 | 两个并发首调各插一行 → 统计页重复显示该 type；后续 `finish` 只累加 `firstResult()` 命中的那一行，另一行成孤儿（计数割裂/欠计）。非数据丢失、非跨用户串号 |
| 家族第二处，且更严重 | `GET /comprehensive/getUserInfo`（`ComprehensiveController.java:39-44`，**GET**）→ `ComprehensiveService.getUserOverallInfo:66-96` → `countErrorSubject:299-349`，`:344` 在读路径 `save` 易错题缓存。同样无唯一约束，`TheoryKnowledgeTestFallibleDao.findByUserId:10-12` 同样 `firstResult()` |
| 仓内正确参照 | `EnteringTelexPatService.findByUserIdAndType:88-97` 查不到只返回 transient 默认 VO、不 save；写入集中在 `clear:100-115` |
| `listPage` 的 HTTP 方法 | `RadiotelephoneController.listPage:38-44` 是 `@POST`（不是 GET），危害小于家族第二处的 GET |

**方案：唯一约束 + 撞库重读（幂等懒建），两处一起做。** 不选「把懒建移出读路径」，因为那会改变对外行为（首次调用从「返回默认行」变成「返回空/报错」），`TrainBoundaryGuardTest.java:79-95` 锁的 `finish` 懒建语义也依赖它。

**Files:**
- Modify `src/main/java/com/nip/entity/RadiotelephoneEntity.java`（加 `(user_id, type)` 唯一约束）
- Modify `src/main/java/com/nip/entity/TheoryKnowledgeTestFallibleEntity.java`（加 `user_id` 唯一约束）
- Modify `src/main/java/com/nip/service/RadiotelephoneService.java`（`listPage:36-53` 懒建改幂等；`finish:55-72` 的同口径懒建一并处理）
- Modify `src/main/java/com/nip/service/ComprehensiveService.java`（`countErrorSubject:299-349` 的 `:344` 缓存写入改幂等）
- Add `backend/database/migrations/2026-09-08-01-unique-lazy-create.sql`
- Modify `backend/database/project006.sql`（回灌）
- Modify `docs/reviews/2026-09-07-full-project-review.md`（把「读接口带写副作用」的评估结论记入 `## 8` `:173` 或附录 `:189`，这才是 Step 3 要的落点）
- Add 并发回归用例

**内容：**

1. 两个实体加唯一约束。**两列都可空**，MySQL 唯一索引允许多个 NULL 行，所以代码侧必须同时保证懒建前 `userId`/`type` 非空（`userId` 来自 token、`type` 来自请求参数），为空则抛 `IllegalArgumentException`。
2. 懒建改幂等：插入撞唯一键时捕获约束冲突并重读（`findByUserIdAndType` / `findByUserId`），返回已存在的那一行。禁止吞异常后返回 null 或默认值。
3. 新迁移脚本，**必须幂等**：照 `backend/database/migrations/2026-08-26-01-schema-sync.sql:91-115` 的既有写法，用 `information_schema.statistics` 判存 + `PREPARE`/`EXECUTE`/`DEALLOCATE`，索引已存在时 `DO 0`。理由同上轮：演练脚本会对已迁移的 current 快照再跑一次。
4. 回灌 `backend/database/project006.sql` 并重跑 `scripts/rehearse-migrations.sh`（`REHEARSAL_OUT_NAME=2026-09-08`），产出新证据目录 + README，权威结论写进 `docs/reviews/`。
5. 把评估结论（并发双插、后果、已修方式、家族两处清单）记入 `full-project-review.md` 的持久位置 —— 代码里没有 `// 待评估`/`// TODO` 的惯例（全 `src` grep 0 命中），所以落点必须是 review 文档。

**验收：**
- 并发回归测试：两个线程同时对同一 `(userId, type)` 首调 `listPage`，断言表中只有 1 行、两个调用都拿到同一行；家族第二处同理。
- `clean verify` 全绿。
- 迁移演练双快照 PASSED（`table count`、`MyISAM=0`、实体表差分 0 字节三项断言全绿）。
- `%prod` 默认 profile（`validate`）下 prod jar 能启动 —— 实体加了约束，`validate` 会校验索引存在性，这是必测项。

**风险：** 加唯一约束是 schema 变更，必须走迁移 + 演练；漏了迁移会让生产启动被 `validate` 拦住。存量数据无阻碍（两张表当前均 0 行 / 0 重复）。

---

## 排期与依赖

| 批 | 可否并行 | 依赖 |
|---|---|---|
| 1（速率收口） | 可 | 无。文件域限 `ScoreMath` + 三个 General 服务 + `ScoringConsistencyTest` |
| 2（记录修正 + 服务级锁） | 可 | 无。文件域限 fix-plan + 一个新测试 |
| 4（懒建幂等 + 迁移） | 可 | 无。文件域限两个实体 + 两个服务 + 迁移/快照 |
| 3（上传导出） | 可 | ~~等业务确认~~ —— **业务已于 2026-09-08 拍板「功能需要，但不用 poi」，阻塞已解除**，按方案 B 恢复三端点，与其余三批并行执行，文件域限两个 controller + 两个 service + 一个新 VO + `TheoryKnowledgeUploadExportTest` |

批 1 与批 2 都碰 `docs/plans/2026-09-07-fix-plan.md`（批 1 要更新 Task 3.2 状态、批 2 要更新 Task 3.4 状态），**由主代理串行收口该文件**，不让两批同时改。

批 4 的迁移演练要独占 docker，必须由主代理串行执行，不能与其他批的构建并发。

## 明确不做的事（防误扩）

| 项 | 为什么不做 |
|---|---|
| `PostTelegramTrainService.java:813-820` 的**相反**速率约定（`l` 加 / `r` 扣，对齐字段 JavaDoc） | 统一它等于翻转历史成绩符号，是业务决策不是重构。另 3 处 `PostTelegraphKeyPatTrainService.java:491-503`、`PostTelexPatTrainService.java:740-756`、`:916-927` 与 in-scope 同向但同样不在本次收口范围 |
| `SpeedDeduct` / `Wpm` 的 `r`/`l` 字段 JavaDoc 与实际用法相反 | 改注释不改分，但两个 DTO 的注释互不一致、且与 `Post*` 系列的用法方向绑定，必须和上一项一起决策 |
| 统一 `deductMap` 的 key 名（`wpmScore` vs `speedScore`） | 对外 JSON 契约，前端与历史 `deductInfo` 依赖 |
| speed 上游取整口径分叉（`HALF_DOWN` vs `HALF_UP`） | 在速率公式之外，改动会改分 |
| 「每 Task 一次提交」 | v1.1.0 已推送，历史无法追溯重写 |
| 分片文档批量标注「已在 fix-plan 批 N 处理」 | 14 份文档的独立工作量，不属本次 4 项偏离 |

## DoD

1. `JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify` 全绿，测试数不减（起点 201）。
2. 批 1 收口后**得分数值逐值不变**，有对照证据。
3. 批 2 的新用例经「回退→变红→恢复→回绿」实证非空转。
4. 批 4 的并发用例能复现双插（未加约束时变红）。
5. 批 4 后迁移演练双快照 PASSED，且 prod jar 在默认 `%prod` 下启动成功。
6. 4 项偏离在 `docs/plans/2026-09-07-fix-plan.md` 里全部有终态结论，无一停留在「偏离计划」而无下文；批 3 若被业务确认为「不需要」，其理由是产品结论而非技术借口。
