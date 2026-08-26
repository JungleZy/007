# Task 1.8 报告：P0#22 军语干扰项死循环 + nextInt 随机偏置

**结论：已修复并提交。红（死循环挂起 900s 被杀）→ 绿（2/2 通过，0.076s）。**

## 红阶段证据

新增 `src/test/java/com/nip/service/PostMilitaryTermTrainServiceTest.java`：
- 测试 1：4 条同类型、value 互异实体（value 刻意避开 checkKeyword 全部关键词/数字模式，使其无法合成干扰项），totalNumber=10，`assertTimeoutPreemptively(2s)` 调 `generateTestPaper`，断言每题选项 map size==4 且互异、correctAnswer 指向存在的键。
- 修复前运行：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=PostMilitaryTermTrainServiceTest` **挂起直至 900s 超时被杀**（surefire 只打出 `Running com.nip.service.PostMilitaryTermTrainServiceTest` 后无输出）。死循环确认：`nextInt(size-1)` 使末元素（第 4 条）永不可选，可达候选仅 3 个（含正确答案），最多凑 2 个干扰项，`while (flag <= 3)` 永不退出；且硬循环不响应中断，preemptive timeout 的工作线程也停不下来。
- 注：红=超时（符合计划预期），非断言失败。

## 修复（三点，均在 generateTestPaper）

1. 三处 `random.nextInt(size - 1)` → `random.nextInt(size)`（类型选择 :133、题目选择 :155、干扰项选择 :193；`size==1` 分支保留）。
2. 取到候选列表后校验去重 value 数：`distinct < 4` 抛 `IllegalArgumentException("类型 " + dataId + " 有效题目不足4条，无法生成干扰项")`（:142-149）。
3. while 循环加 100 次护栏：超限 `log.warn` 后从候选中顺序补足 options 至 4 个互异项并 break（:173-187）。

## 绿阶段证据

```
[INFO] Running com.nip.service.PostMilitaryTermTrainServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.076 s
[INFO] BUILD SUCCESS
```

测试 2（`generateTestPaperRejectsTypeWithFewerThanFourDistinctValues`）：3 条候选时抛 IllegalArgumentException（外层套 2s timeout 防回归挂起）。

## Concerns

- 行为变化：调用方 `add()` 在某类型有效题目 <4 条时现在会抛 IllegalArgumentException（经 JWTInterceptor 变为 HTTP 200 + SYSTEM_ERROR + 消息），此前是死循环挂死请求——按计划预期。
- 测试为纯单元测试（`new PostMilitaryTermTrainService(null,null,null,null)`，generateTestPaper 无 dao 调用），不走 @QuarkusTest，运行快且不依赖 MySqlResource。
- :195 原条件 `titleIndex != optionId || optionId == 0` 的怪逻辑（optionId==0 时允许重取正确答案走 checkKeyword 合成路径）未动——不在任务范围。
