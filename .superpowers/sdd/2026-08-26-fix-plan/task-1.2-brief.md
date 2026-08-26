### Task 1.2: 理论知识课件/测验丢失（P0#2，TheoryKnowledgeService:223-296）

**Files:**
- Modify: `src/main/java/com/nip/service/TheoryKnowledgeService.java:223-296`
- Test: `src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java`

同 1.1 模式，修复要点（相同 TDD 五步，测试断言"缺 swf 列表编辑后原课件仍在"）：
1. 方法开头校验：`knowledgesDto.getKnowledgeSwfs()` null → `throw new IllegalArgumentException("课件列表缺失")`；每个 swf 的 `s.getTest()` 用 `ListUtils.nullToEmpty` 归一。
2. :231 `knowledgeSwfDao.deleteAllByKnowledgeId(...)` 下移至输入校验全部通过之后（保持在 save 主记录之后即可，关键是校验前不删）。
3. :257 `findFirstByKnowledgeSwfIdAndVersions` 结果判空：null → `throw new IllegalStateException("版本1测验不存在: swfId=" + test.getKnowledgeSwfId())`。
4. :292-295 catch 整体删除。**契约微调（有意）**：`InvalidTitleException`（:227 空标题）原被兜成 200+CODE_500，删 catch 后经 JWTInterceptor 变 200+SYSTEM_ERROR、提示消息保留；Phase 4 的 ValidationExceptionMapper 恢复精确业务码。测试断言以库状态为准，不断言业务码。

- [ ] Steps 1-5（测试→红→修→绿→提交 `fix(p0-2)`）

