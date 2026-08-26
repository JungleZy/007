# Task 1.2 报告：理论知识保存先删课件+吞异常导致丢失（P0#2）

结论：已修复，红→绿完整，一次提交。

## 改动

`src/main/java/com/nip/service/TheoryKnowledgeService.java` saveTheoryKnowledge：
1. 方法开头新增校验：`getKnowledgeSwfs()==null` → `IllegalArgumentException("课件列表缺失")`；每个 swf 的空标题校验（原 :236-237）前移到删除之前。
2. `knowledgeSwfDao.deleteAllByKnowledgeId` 下移至全部输入校验通过、主记录 save 之后。
3. `s.getTest()` 使用点改为 `ListUtils.nullToEmpty(s.getTest())`。
4. `findFirstByKnowledgeSwfIdAndVersions` 结果判空：null → `IllegalStateException("版本1测验不存在: swfId=" + test.getKnowledgeSwfId())`（原直接 `.getId()` 会 NPE）。
5. 整体 try/catch(Exception) 删除；异常冒出经 JWTInterceptor 兜为 200+SYSTEM_ERROR（契约微调有意，见 global-constraints）。

`src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java`：新增，仿 TestPaperServiceTest。建含 1 个课件的知识点 → 用 `knowledgeSwfs=null` 的 DTO 编辑 → 断言按 knowledgeId 计数的课件行仍在。

## 红阶段证据（修复前）

```
[ERROR]   TheoryKnowledgeServiceTest.editWithNullSwfListKeepsExistingSwfs:57 原课件不得被静默删除 ==> expected: <true> but was: <false>
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

失败机理：编辑请求先删旧课件（:231），随后 `getKnowledgeSwfs().forEach` NPE 被 catch 吞掉返回 CODE_500，事务正常提交 → 旧课件永久丢失。

## 绿阶段证据（修复后）

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 32.42 s -- in com.nip.service.TheoryKnowledgeServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

命令：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=TheoryKnowledgeServiceTest`

## Concerns

- 修复后校验异常在删除前抛出，事务由 JTA 对 RuntimeException 回滚；即使删除后异常（如版本1测验缺失）也会回滚，不再有"删了但没写"的中间态提交。
- `findFirstByKnowledgeSwfIdAndVersions` 判空是防 NPE 的最小改动，未覆盖该分支的专项测试（brief 只要求"缺 swf 列表"断言）。
- 空标题业务码从 CODE_500 变为 SYSTEM_ERROR（消息保留），Phase 4 ValidationExceptionMapper 恢复精确业务码——计划内契约微调。
