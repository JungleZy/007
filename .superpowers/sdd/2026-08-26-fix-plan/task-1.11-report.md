# Task 1.11 报告：Phase 1 收尾门禁

结论：三项门禁全部通过。verify 全绿（23/23），三方法吞异常 catch 已清零，文档勘误与测试注释修正已提交。

## 1. 全量 verify

命令：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify`

结果：**BUILD SUCCESS**，总计 **Tests run: 23, Failures: 0, Errors: 0, Skipped: 0**（≥13 达标），总耗时 01:01 min（2026-08-27T03:24:26+08:00）。

分类明细（11 个测试类）：

| 测试类 | Tests | 耗时 |
|---|---|---|
| com.nip.SmokeTest | 1 | 32.32 s（含 Testcontainers MySQL 启动） |
| com.nip.service.EnteringTelexPatServiceTest | 2 | 0.344 s |
| com.nip.service.MenusServiceTest | 2 | 0.108 s |
| com.nip.service.MilitaryTermDataServiceTest | 3 | 0.106 s |
| com.nip.service.TestPaperServiceTest | 1 | 0.090 s |
| com.nip.service.TheoryKnowledgeExamServiceTest | 3 | 0.310 s |
| com.nip.service.TheoryKnowledgeServiceTest | 2 | 0.111 s |
| com.nip.ws.WebSocketSimulationTest | 1 | 0.514 s |
| com.nip.ws.WebSocketUnionTest | 3 | 8.916 s |
| com.nip.common.utils.TickerPatUtilsTest | 3 | 0.025 s |
| com.nip.service.PostMilitaryTermTrainServiceTest | 2 | 0.011 s |

## 2. 静态门禁 grep

命令与输出：

```
$ grep -n "catch (Exception" src/main/java/com/nip/service/TestPaperService.java src/main/java/com/nip/service/TheoryKnowledgeService.java src/main/java/com/nip/service/MenusService.java
src/main/java/com/nip/service/TestPaperService.java:137:    } catch (Exception e) {
src/main/java/com/nip/service/TestPaperService.java:252:    } catch (Exception e) {
src/main/java/com/nip/service/MenusService.java:224:    } catch (Exception e) {
```

判定：**通过**。本批三方法（saveTestPaper / saveTheoryKnowledge / addMenus）内均已无吞异常 catch；TheoryKnowledgeService 全文件零命中。剩余 3 处命中均在批外方法（列出即可，不在本批范围）：

- TestPaperService.java:137 → `findAllTestPaper()`（只读查询路径）
- TestPaperService.java:252 → `deleteTestPaper()`（Phase 后续批次范围）
- MenusService.java:224 → `handleMenusDto()`（只读 DTO 组装，对应 P2-34）

## 3. 文档勘误 + 注释修正

1. `docs/reviews/2026-08-26-service-core-review.md`：
   - P0-14 加"审计更正"标注：误报——`TickerTapeTrainService.update()` 全仓无调用方（本次已重新 grep 确认零命中），属死代码，不列入修复批次。
   - P2-25 加"审计更正"标注：误报——credit 为 `Double`（TheoryKnowledgeEntity.java:103，本次已重读源码确认），`BigDecimal(double)` 构造器不抛 NumberFormatException；并注明调用点行号已漂移至 :287。
2. `docs/reviews/2026-08-26-review-audit.md`：文末新增"§8 执行期实证更正"两条：
   - #19 实际失效模式为"创建即 StaleObjectStateException"（Hibernate 6 实测，Task 1.4），非静默跨场删除；P0 定级不变。
   - P1-43"自调用绕过 @Transactional"被证伪（Quarkus ArC 子类拦截，Task 1.10 红阶段实测 + 评审独立确认），§7 第 2 条相关推理链失效。
3. `src/test/java/com/nip/service/MilitaryTermDataServiceTest.java:17-22` 类注释重写为陈述实测机制（自调用 @Transactional 生效、整批回滚，测试防守回滚契约），修正评审 P3 指出的过时假设描述。

提交：`docs: 评审误报与执行期实证勘误`（哈希见提交记录）。
