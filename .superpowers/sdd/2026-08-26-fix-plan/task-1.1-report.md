# Task 1.1 报告 — 试卷编辑先删后写+吞异常导致丢题（P0#1）

**状态：DONE。红→绿一次通过，无偏差。**

## 变更

- 新增 `src/test/java/com/nip/service/TestPaperServiceTest.java`（按计划文档 Task 1.1 Step 1 原文）。
- 替换 `src/main/java/com/nip/service/TestPaperService.java` 的 `saveTestPaper` 方法体：
  - 五个题型列表先经 `ListUtils.nullToEmpty` 归一组装（归一在前）；
  - `deleteAllByTestPaperId` 下移到新列表组装完成之后（删除在后）；
  - `catch (Exception)` 整体删除——异常冒出 → service 边界事务回滚 → JWTInterceptor 兜成 HTTP 200 + SYSTEM_ERROR（符合 Global Constraints 事务铁律与异常响应链路）。
  - 新增 `import com.nip.common.utils.ListUtils`，其余 import/类结构未动。

备注：brief 文件 `.superpowers/sdd/2026-08-26-fix-plan/task-1.1-brief.md` 不存在，测试与修复代码取自 `docs/plans/2026-08-26-fix-plan.md` Task 1.1 全文（内容即任务描述所指的 brief）。

## 红阶段证据（修复前，2026-08-27 00:59）

```
2026-08-27 00:59:34,834 ERROR [com.nip.ser.TestPaperService] (main) 保存试卷失败：Cannot invoke "java.util.Collection.toArray()" because "c" is null
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 31.78 s <<< FAILURE! -- in com.nip.service.TestPaperServiceTest
[ERROR] com.nip.service.TestPaperServiceTest.updateWithNullTypeListKeepsExistingQuestions -- Time elapsed: 0.680 s <<< FAILURE!
org.opentest4j.AssertionFailedError: 原题目不得被静默删除 ==> expected: <true> but was: <false>
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

丢题机制原样复现：编辑请求缺 `shortAnswer` 列表 → `addAll(null)` NPE → 被 `catch (Exception)` 吞掉并正常返回 → 事务提交 → 先删的旧题目永久丢失（count==0）。

## 绿阶段证据（修复后，2026-08-27 01:00）

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 34.05 s -- in com.nip.service.TestPaperServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

命令均为 `JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=TestPaperServiceTest`。

## Concerns

- 无。DTO setter（setTopic/setType）与计划核实一致，编译一次通过；`questionDao.count("testPaperId", ...)` Panache API 可用。
- 修复后的行为：null 列表被归一为空列表，编辑不再抛异常（题目按新列表重建）；若未来出现其他运行时异常，会回滚整个事务，旧题目保留。
