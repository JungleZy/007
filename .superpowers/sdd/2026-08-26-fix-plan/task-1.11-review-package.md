## commits
1eab44f docs: 评审误报与执行期实证勘误

## diff
diff --git a/.superpowers/sdd/2026-08-26-fix-plan/task-1.11-report.md b/.superpowers/sdd/2026-08-26-fix-plan/task-1.11-report.md
new file mode 100644
index 0000000..f6e568a
--- /dev/null
+++ b/.superpowers/sdd/2026-08-26-fix-plan/task-1.11-report.md
@@ -0,0 +1,54 @@
+# Task 1.11 报告：Phase 1 收尾门禁
+
+结论：三项门禁全部通过。verify 全绿（23/23），三方法吞异常 catch 已清零，文档勘误与测试注释修正已提交。
+
+## 1. 全量 verify
+
+命令：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify`
+
+结果：**BUILD SUCCESS**，总计 **Tests run: 23, Failures: 0, Errors: 0, Skipped: 0**（≥13 达标），总耗时 01:01 min（2026-08-27T03:24:26+08:00）。
+
+分类明细（11 个测试类）：
+
+| 测试类 | Tests | 耗时 |
+|---|---|---|
+| com.nip.SmokeTest | 1 | 32.32 s（含 Testcontainers MySQL 启动） |
+| com.nip.service.EnteringTelexPatServiceTest | 2 | 0.344 s |
+| com.nip.service.MenusServiceTest | 2 | 0.108 s |
+| com.nip.service.MilitaryTermDataServiceTest | 3 | 0.106 s |
+| com.nip.service.TestPaperServiceTest | 1 | 0.090 s |
+| com.nip.service.TheoryKnowledgeExamServiceTest | 3 | 0.310 s |
+| com.nip.service.TheoryKnowledgeServiceTest | 2 | 0.111 s |
+| com.nip.ws.WebSocketSimulationTest | 1 | 0.514 s |
+| com.nip.ws.WebSocketUnionTest | 3 | 8.916 s |
+| com.nip.common.utils.TickerPatUtilsTest | 3 | 0.025 s |
+| com.nip.service.PostMilitaryTermTrainServiceTest | 2 | 0.011 s |
+
+## 2. 静态门禁 grep
+
+命令与输出：
+
+```
+$ grep -n "catch (Exception" src/main/java/com/nip/service/TestPaperService.java src/main/java/com/nip/service/TheoryKnowledgeService.java src/main/java/com/nip/service/MenusService.java
+src/main/java/com/nip/service/TestPaperService.java:137:    } catch (Exception e) {
+src/main/java/com/nip/service/TestPaperService.java:252:    } catch (Exception e) {
+src/main/java/com/nip/service/MenusService.java:224:    } catch (Exception e) {
+```
+
+判定：**通过**。本批三方法（saveTestPaper / saveTheoryKnowledge / addMenus）内均已无吞异常 catch；TheoryKnowledgeService 全文件零命中。剩余 3 处命中均在批外方法（列出即可，不在本批范围）：
+
+- TestPaperService.java:137 → `findAllTestPaper()`（只读查询路径）
+- TestPaperService.java:252 → `deleteTestPaper()`（Phase 后续批次范围）
+- MenusService.java:224 → `handleMenusDto()`（只读 DTO 组装，对应 P2-34）
+
+## 3. 文档勘误 + 注释修正
+
+1. `docs/reviews/2026-08-26-service-core-review.md`：
+   - P0-14 加"审计更正"标注：误报——`TickerTapeTrainService.update()` 全仓无调用方（本次已重新 grep 确认零命中），属死代码，不列入修复批次。
+   - P2-25 加"审计更正"标注：误报——credit 为 `Double`（TheoryKnowledgeEntity.java:103，本次已重读源码确认），`BigDecimal(double)` 构造器不抛 NumberFormatException；并注明调用点行号已漂移至 :287。
+2. `docs/reviews/2026-08-26-review-audit.md`：文末新增"§8 执行期实证更正"两条：
+   - #19 实际失效模式为"创建即 StaleObjectStateException"（Hibernate 6 实测，Task 1.4），非静默跨场删除；P0 定级不变。
+   - P1-43"自调用绕过 @Transactional"被证伪（Quarkus ArC 子类拦截，Task 1.10 红阶段实测 + 评审独立确认），§7 第 2 条相关推理链失效。
+3. `src/test/java/com/nip/service/MilitaryTermDataServiceTest.java:17-22` 类注释重写为陈述实测机制（自调用 @Transactional 生效、整批回滚，测试防守回滚契约），修正评审 P3 指出的过时假设描述。
+
+提交：`docs: 评审误报与执行期实证勘误`（哈希见提交记录）。
diff --git a/docs/reviews/2026-08-26-review-audit.md b/docs/reviews/2026-08-26-review-audit.md
index 713e779..385dc21 100644
--- a/docs/reviews/2026-08-26-review-audit.md
+++ b/docs/reviews/2026-08-26-review-audit.md
@@ -134,6 +134,13 @@ full-review §6 的六步顺序整体成立，两处调整：
 | PersistAudit | persistence 28 条 + MyISAM/缺表/SQL 快照全量核对 | agent://PersistAudit |
 | SilentFailAudit | silent-failures 39 条 + 跨分片重复指认 | agent://SilentFailAudit |
 | LegacyAudit | 2026-08-15 遗留 13 条 + 扩散声明核实 | agent://LegacyAudit |
 | BuildVerify | common-build 35 条 + 构建/依赖树实际复现 | agent://BuildVerify |
 
 确认条目的逐条 file:line 证据与原评审文档定位一致（行号漂移处已按符号重定位），未在本文重复；非"确认"条目已全部列于 §2/§3。
+
+---
+
+## 8. 执行期实证更正（2026-08-27，fix/2026-08-26-p0 分支落地时补记）
+
+1. **#19（快照复用源试卷 id）实际运行后果修正**：Hibernate 6 实测（Task 1.4 红阶段），复用已存在 id 调用 save/merge 创建快照时**创建即抛 `StaleObjectStateException`**（版本/存在性检查失败），并非本文所判"静默跨场删除首场快照"。缺陷确认成立、按 P0 修复不变，仅失效模式由"静默丢数据"更正为"确定性异常、快照功能不可用"。
+2. **P1-43（CDI 自调用绕过 @Transactional）被证伪**：Task 1.10 红阶段实测 + 评审独立确认，Quarkus ArC 采用**子类拦截**，`saveBatch` 自调用 `excelHanle` 时 `@Transactional` 依然生效，批中后行抛异常整批回滚。§7 第 2 条"其降级依据被 P1-43 推翻"的推理链随之失效——#18 的事务边界本就完好，导入健壮性问题独立成立。
diff --git a/docs/reviews/2026-08-26-service-core-review.md b/docs/reviews/2026-08-26-service-core-review.md
index 918a747..d916f9b 100644
--- a/docs/reviews/2026-08-26-service-core-review.md
+++ b/docs/reviews/2026-08-26-service-core-review.md
@@ -313,12 +313,14 @@
 **触发条件** 调用一次 update 接口（暂停/进度上报走的就是这个 Param 形状）。
 
 **影响** name/rate/type/**codeMessageBody（整份报文内容）**/codeShort/startTime/endTime/status/userId/isLowRate 全部写成 NULL。userId 变 null 后 listPage（:91-93 按 userId 过滤）再也查不到该记录；status 变 null 后 checkStatus（:231-236）必 NPE。报文内容不可恢复。
 
 **建议** 改为 findById 后逐字段 set（对照 `TelegraphKeyPatSyntheticalService.java:76-87`），或走 DAO 具名 update JPQL（`TickerTapeTrainDao` 已有 begin/pause/goOn/finish 四个具名 update，update() 是唯一漏网的）。
 
+> **审计更正（2026-08-27 执行期实证）**：本条为误报。全仓 grep 确认 `TickerTapeTrainService.update()` 无任何调用方（Controller 未暴露对应端点），属死代码；merge 抹空字段的机制描述正确但无触发路径。不列入修复批次。
+
 
 ---
 
 # P1 —— 70 条
 
 ## P1 组一：电报拍发评分与报文对比（MessageComparisonService + detector/，7 条）
@@ -1133,12 +1135,15 @@ correctNum.subtract(new BigDecimal(errorNumber)).divide(correctNum, 2, RoundingM
 | P2-31 | `GradingRuleService.java:63/71/78/85-87` | ① `entity.getIsDefault() == 0` Integer 拆箱，前端不传该字段即 NPE；② flag 语义是「该 type 下还没有默认规则」，用户明确提交 isDefault=1（非默认）时若库里恰好一条默认都没有 → :85 **违背用户意图**把它设成默认。影响：评分规则默认项被意外改动，直接影响后续所有训练的判分口径 |
 | P2-32 | `MilitaryTermDataService.java:115` | `dto.getType().compareTo(0) == 0` 拆箱 NPE。触发：新增军语时请求体缺 type |
 | P2-33 | `MilitaryTermDataService.java:186-199` `move` | 不校验 source/target 同父，:190 `source.getSort().compareTo(target.getSort())` 对 null 拆箱 NPE；:192/:195 的批量 update 用的都是 `source.getParentId()`。触发：拖拽时 targetId 属于另一个父节点 → 按 source 的父节点范围去平移 sort，但比较基准来自另一棵子树 |
 | P2-34 | `MenusService.java:200-202` | `om.readValue(firstByRoleIdAndMenuId.getPer(), ...)` —— Jackson 对 null 抛 IllegalArgumentException，被 :226 catch → 返回空 MenusDto。触发：某个角色-菜单关联的 per 列为空 → id/name 全 null 的空节点混进菜单树，前端渲染出空白项 |
 | P2-35 | `RoleService.java:71-72` | `map.get("menusId").toString()` / `map.get("per").toString()` 直接 toString。触发：前端提交的菜单 JSON 缺键 → NPE。该方法有 @Transactional 会回滚（:65 deleteAllByRoleId 在同一事务内），不丢数据但 500 且无可读提示 |
 
+
+> **审计更正（2026-08-27 执行期实证）**：P2-25 为误报。该 `.map(BigDecimal::new)`（现漂移至 :287）的上游是 `TheoryKnowledgeEntity::getCredit`，credit 字段类型为 `Double`（`TheoryKnowledgeEntity.java:103`），走 `BigDecimal(double)` 构造器，不存在字符串解析，不会抛 NumberFormatException。不列入修复批次。
+
 ## P2-D 静默失败 / 异常处理（12 条）
 
 | 编号 | 位置 | 现象与触发条件 |
 |---|---|---|
 | P2-36 | `TheoryKnowledgeQuestionService.java:254-283` | `exportTemplate(HttpServerResponse)` 方法体 100% 是注释，一行可执行代码都没有，而 `TheoryKnowledgeQuestionController.java:87-91` 照常暴露 `POST /exportTemplate`。客户端收到 200，模板下载是空的 |
 | P2-37 | `TheoryKnowledgeClassifyService.java:88-93` | `updateFileToNip(FileUpload dto, ...)` —— 参数 `dto`（上传的文件）**从未被引用**，:91 取出 userId 后再没用过，:92 返回空对象。Controller `TheoryKnowledgeController.java:178-181` 摘要写「上传文件到NIP服务中」并包 `ResponseResult.success(...)`。上传的文件直接进虚空 |
diff --git a/src/test/java/com/nip/service/MilitaryTermDataServiceTest.java b/src/test/java/com/nip/service/MilitaryTermDataServiceTest.java
index 2cf3ce6..329729b 100644
--- a/src/test/java/com/nip/service/MilitaryTermDataServiceTest.java
+++ b/src/test/java/com/nip/service/MilitaryTermDataServiceTest.java
@@ -12,15 +12,16 @@ import org.junit.jupiter.api.Test;
 import java.util.ArrayList;
 import java.util.List;
 
 import static org.junit.jupiter.api.Assertions.*;
 
 /**
- * 改级#18 + P1-43：Excel 导入入口 saveBatch（:202）无 @Transactional，
- * 自调用 excelHanle 绕过其 @Transactional；dao.save 各自独立提交，
- * 批中后行抛异常时前半已落库行不回滚。
+ * 改级#18 / P1-43 实测结论：Quarkus ArC 采用子类拦截，自调用 excelHanle 的
+ * @Transactional 依然生效——批中后行抛异常时整批回滚，前半行不落库
+ * （Task 1.10 红阶段实测推翻了"自调用绕过 @Transactional"的评审假设）。
+ * 本测试防守该回滚契约不回归。
  */
 @QuarkusTest
 @QuarkusTestResource(MySqlResource.class)
 class MilitaryTermDataServiceTest {
   @Inject MilitaryTermDataService service;
   @Inject MilitaryTermDataDao dao;
