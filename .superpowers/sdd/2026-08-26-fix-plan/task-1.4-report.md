# Task 1.4 Report — P0#4 + #19 + #20（TheoryKnowledgeExamService）

结论：DONE。三用例先红后绿，一次提交。

## 红（修复前，`mvnw test -Dtest=TheoryKnowledgeExamServiceTest`）

```
[ERROR] Failures:
[ERROR]   TheoryKnowledgeExamServiceTest.analyseWithMissingTypeListDoesNotNPE:109 Unexpected exception thrown: java.lang.NullPointerException: Cannot invoke "java.util.Collection.toArray()" because "c" is null
[ERROR]   TheoryKnowledgeExamServiceTest.editExamWithAnsweredUsersIsRejected:91 已有作答记录的考试编辑必须被拒绝 ==> Unexpected exception type thrown, expected: <java.lang.IllegalStateException> but was: <jakarta.persistence.OptimisticLockException>
[ERROR] Errors:
[ERROR]   TheoryKnowledgeExamServiceTest.twoExamsOnSamePaperKeepBothSnapshots:67 » OptimisticLock Row was updated or deleted by another transaction (or unsaved-value mapping was incorrect): [com.nip.entity.TheoryKnowledgeExamTestPaperEntity#src-paper-1]
[ERROR] Tests run: 3, Failures: 2, Errors: 1, Skipped: 0
```

- #20 用例红形态与预期一致：`fromJson("")=null` → `addAll(null)` NPE。
- #4 用例红：编辑未被业务守卫拒绝（抛的是基础设施异常 OptimisticLock，不是 IllegalStateException）。
- #19 用例红形态与 brief 预测不同（见 Concerns）。

## 绿（修复后，同一命令）

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 改动

- `saveTheoryKnowledgeExam`（原 :72-94 区段）：按 brief 替换——先状态守卫（`state <> 1 or score > 0` 存在即抛 `IllegalStateException`），再按 examId 删旧快照/考生（移除原 `deleteById(testPaper.getId())` 分支），`snap.setId(null)` 保证快照独立主键，五列表 `ListUtils.nullToEmpty` 归一后序列化。
- `examineAnalyse` :325-339：5 处 `fromJson` 结果包 `ListUtils.nullToEmpty` 再 `addAll`。
- 新增 `src/test/java/com/nip/service/TheoryKnowledgeExamServiceTest.java`（3 用例）。

## Concerns

1. **#19 红形态偏差**：brief 预测"第二场建立后第一场快照 count==0"（静默串删）。实测 Hibernate 6.x 对 `merge` 一个 id 已赋值但库中无行的实体直接抛 `StaleObjectStateException`——即凡 `testPaper.getId()` 非空且非现存快照 id 的保存，第一场创建就崩，根本走不到"第二场删第一场"。根因不变（convertOne 把输入 id 复制为快照主键 + 按输入 id 删除），生产表现是异常而非静默丢失。断言方向未改（仍断言两场各 count==1），修复后绿。
2. **编辑流原本整体不可用**：用例 2 显示带快照 id 的编辑请求在旧代码下 `deleteById(snapId)` + 同事务 `merge(snapId)` 必抛 OptimisticLock——即"编辑抹答卷"在当前 Hibernate 版本下实际表现为编辑直接 500；守卫 + setId(null) 后编辑语义恢复且受状态保护。
3. 守卫在 `theoryKnowledgeExamDao.save(entity)` 之后抛出，exam 主表的 merge 随事务回滚，无残留。
