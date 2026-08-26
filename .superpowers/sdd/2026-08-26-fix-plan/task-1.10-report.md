# Task 1.10 报告：军语 Excel 导入事务边界（改级#18 + P1-43）

## 结论
已修复并提交。但**"前半落库不回滚"缺陷在 Quarkus 下实际不存在**——红阶段回滚测试直接通过；真正红的只有入口空集合校验（P1-43）。

## 关键发现：#18 定位对 Quarkus 不成立
计划假设 `saveBatch:202`（无 @Transactional）自调用 `excelHanle:208`（@Transactional）时拦截器被绕过（Spring 代理式 AOP 的行为）。红阶段堆栈证明相反：Quarkus ArC 用**子类拦截**（`MilitaryTermDataService_Subclass.excelHanle` → `TransactionalInterceptorRequired`），自调用同样走拦截器，整批写入本来就在一个事务里、异常即整批回滚。excelHanle 覆盖全部写路径（saveBatch 后续 findAll 只读），故无法构造任何"前半残留"形态——依据 brief"不伪造红"条款，回滚测试以"落地即绿 + 本报告说明"交付，它仍防守回滚契约回归。

## 红→绿证据
`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=MilitaryTermDataServiceTest`

红（修复前，3 测试中 2 失败）：
```
[ERROR]   MilitaryTermDataServiceTest.emptyBatchIsRejected:59 空集合必须在入口被拒 ==> Expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown.
[ERROR]   MilitaryTermDataServiceTest.nullBatchIsRejected:66 null 集合必须在入口被拒 ==> Unexpected exception type thrown, expected: <java.lang.IllegalArgumentException> but was: <java.lang.NullPointerException>
[ERROR] Tests run: 3, Failures: 2, Errors: 0, Skipped: 0
```
（importRollsBackWholeBatchWhenARowFails 在修复前即通过，原因见上。）

绿（修复后）：
```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 改动
- `MilitaryTermDataService.saveBatch`（原 :202）：加 `@Transactional`（按计划显式化入口事务边界，虽经证实非必需，但消除对 excelHanle 注解位置的隐式依赖）；入口校验 `params == null || params.isEmpty()` → `throw new IllegalArgumentException("导入数据为空或格式不完整")`。
- 新增 `src/test/java/com/nip/service/MilitaryTermDataServiceTest.java`：3 测试——整批回滚（种子父类型+已有子项，批含 2 有效行 + 1 null 非法行，断言异常后新行零残留）、空集合拒绝、null 集合拒绝。
- **未动** `saveAll`（:47，开发用 JSON 端点）与 `excelHanle` 本体。

## 测试设计说明
- brief 要求校验"顶级集合与每个子级集合"，但 `MilitaryTermDto` 是扁平结构（parentName/childName/content，无嵌套子集合），入口校验即整表 list 的 null/empty。
- 种子数据必须含父类型下**一个已有子项**：否则新子项走 `excelHanle:233` 的 `maxSort + 1` 会因 `max(sort)=null` 拆箱 NPE；新父类型行会触发 :227 提前 return。两者都会阻止"多行成功后再异常"的构造。

## Concerns
1. `excelHanle:227` 提前 return（首个新建父类型后丢弃剩余所有行）是**未修的独立缺陷**，不在本任务范围，建议入 backlog。
2. 计划中其余引用"Quarkus 自调用绕过 @Transactional"的任务（若有）应复核——该机制假设已被本任务证伪。
