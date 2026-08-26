## commits
1fa3578 fix(p0-2): knowledgeTestContents 空列表归一

## diff
diff --git a/.superpowers/sdd/2026-08-26-fix-plan/task-1.2-report.md b/.superpowers/sdd/2026-08-26-fix-plan/task-1.2-report.md
index 146f01f..dcf9f25 100644
--- a/.superpowers/sdd/2026-08-26-fix-plan/task-1.2-report.md
+++ b/.superpowers/sdd/2026-08-26-fix-plan/task-1.2-report.md
@@ -31,10 +31,29 @@
 [INFO] BUILD SUCCESS
 ```
 
 命令：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=TheoryKnowledgeServiceTest`
 
 ## Concerns
 
 - 修复后校验异常在删除前抛出，事务由 JTA 对 RuntimeException 回滚；即使删除后异常（如版本1测验缺失）也会回滚，不再有"删了但没写"的中间态提交。
 - `findFirstByKnowledgeSwfIdAndVersions` 判空是防 NPE 的最小改动，未覆盖该分支的专项测试（brief 只要求"缺 swf 列表"断言）。
 - 空标题业务码从 CODE_500 变为 SYSTEM_ERROR（消息保留），Phase 4 ValidationExceptionMapper 恢复精确业务码——计划内契约微调。
+
+## 修复轮 1（评审 Important）
+
+结论：已修复，2 个测试全绿，单独提交。
+
+问题：`test.getKnowledgeTestContents()` 未归一——:283 取值后 :287 `forEach` 在 null 时 NPE；:271-272 `listEquals` 入参同险。
+
+改动：
+- `TheoryKnowledgeService.java` 两处取值点（listEquals 分支 + save 后的 content 循环）改为 `ListUtils.nullToEmpty(test.getKnowledgeTestContents())`。
+- `TheoryKnowledgeServiceTest` 新增 `saveWithNullTestContentsDoesNotNpe`：swf 带 1 个测验（id=null 走新增分支）、`knowledgeTestContents=null`，断言保存不抛 NPE 且课件按 knowledgeId 落库。
+
+测试输出：
+
+```
+[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
+[INFO] BUILD SUCCESS
+```
+
+命令：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=TheoryKnowledgeServiceTest`
diff --git a/src/main/java/com/nip/service/TheoryKnowledgeService.java b/src/main/java/com/nip/service/TheoryKnowledgeService.java
index 9bb0be7..99c9648 100644
--- a/src/main/java/com/nip/service/TheoryKnowledgeService.java
+++ b/src/main/java/com/nip/service/TheoryKnowledgeService.java
@@ -261,33 +261,33 @@ public class TheoryKnowledgeService {
             if (test.getVersions() == 1) {
               TheoryKnowledgeTestEntity firstByKnowledgeSwfIdAndVersions = theoryKnowledgeTestDao.findFirstByKnowledgeSwfIdAndVersions(test.getKnowledgeSwfId(), 1);
               if (firstByKnowledgeSwfIdAndVersions == null) {
                 throw new IllegalStateException("版本1测验不存在: swfId=" + test.getKnowledgeSwfId());
               }
               TheoryKnowledgeTestEntity testEntity = PojoUtils.convertOne(test, TheoryKnowledgeTestEntity.class, (t, e) -> e.setKnowledgeSwfId(saveSwfEntity.getId()));
               if (!firstByKnowledgeSwfIdAndVersions.getId().equals(testEntity.getId())) {
                 theoryKnowledgeTestUserDao.deleteByKnowledgeIdAndKnowledgeSwfId(test.getKnowledgeId(), test.getKnowledgeSwfId());
               } else {
                 List<TheoryKnowledgeTestContentEntity> allByKnowledgeTestId = theoryKnowledgeTestContentDao.findAllByKnowledgeTestId(testEntity.getId());
-                List<TheoryKnowledgeTestContentEntity> knowledgeTestContents = test.getKnowledgeTestContents();
+                List<TheoryKnowledgeTestContentEntity> knowledgeTestContents = ListUtils.nullToEmpty(test.getKnowledgeTestContents());
                 if (!listEquals(allByKnowledgeTestId, knowledgeTestContents)) {
                   theoryKnowledgeTestUserDao.deleteByKnowledgeIdAndKnowledgeSwfId(test.getKnowledgeId(), test.getKnowledgeSwfId());
                 }
               }
             }
           }
           TheoryKnowledgeTestEntity testEntity = PojoUtils.convertOne(test, TheoryKnowledgeTestEntity.class, (t, e) -> {
             e.setKnowledgeSwfId(saveSwfEntity.getId());
           });
           TheoryKnowledgeTestEntity saveTest = theoryKnowledgeTestDao.save(testEntity);
           //再拿到content
-          List<TheoryKnowledgeTestContentEntity> knowledgeTestContents = test.getKnowledgeTestContents();
+          List<TheoryKnowledgeTestContentEntity> knowledgeTestContents = ListUtils.nullToEmpty(test.getKnowledgeTestContents());
           theoryKnowledgeTestContentDao.deleteByKnowledgeIdAndCreateUserIdAndKnowledgeSwfIdAndKnowledgeTestId(
               knowledge.getId(), knowledge.getCreateUserId(), saveSwfEntity.getId(), saveTest.getId()
           );
           knowledgeTestContents.forEach(content -> {
             //新增记录设置默认值
             if (content.getId() == null) {
               content.setCreateUserId(knowledge.getCreateUserId());
               content.setKnowledgeId(knowledge.getId());
               content.setKnowledgeSwfId(saveSwfEntity.getId());
               content.setKnowledgeTestId(saveTest.getId());
diff --git a/src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java b/src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java
index 802d068..443315c 100644
--- a/src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java
+++ b/src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java
@@ -1,16 +1,17 @@
 package com.nip.service;
 
 import com.nip.dao.TheoryKnowledgeSwfDao;
 import com.nip.dao.UserDao;
 import com.nip.dto.TheoryKnowledgesDto;
 import com.nip.dto.vo.TheoryKnowledgeSwfVO;
+import com.nip.dto.vo.TheoryKnowledgeTestVO;
 import com.nip.entity.TheoryKnowledgeEntity;
 import com.nip.entity.UserEntity;
 import com.nip.testsupport.Fixtures;
 import com.nip.testsupport.MySqlResource;
 import io.quarkus.test.common.QuarkusTestResource;
 import io.quarkus.test.junit.QuarkusTest;
 import jakarta.inject.Inject;
 import org.junit.jupiter.api.Test;
 
 import java.util.List;
@@ -49,11 +50,25 @@ class TheoryKnowledgeServiceTest {
     edit.getKnowledge().setId(knowledgeId);
     edit.setKnowledgeSwfs(null); // 缺课件列表的编辑请求
 
     try {
       service.saveTheoryKnowledge(edit);
     } catch (RuntimeException expectedAfterFix) {
       // 修复后允许抛（课件列表缺失）；关键契约在下一行
     }
     assertTrue(knowledgeSwfDao.count("knowledgeId", knowledgeId) > 0, "原课件不得被静默删除");
   }
+
+  @Test
+  void saveWithNullTestContentsDoesNotNpe() {
+    UserEntity user = Fixtures.user(userDao, "t-theory-2");
+    TheoryKnowledgesDto create = knowledges("k2", user.getId());
+    TheoryKnowledgeTestVO test = new TheoryKnowledgeTestVO();
+    test.setTitle("测验1");
+    test.setVersions(1);
+    test.setKnowledgeTestContents(null); // 测验缺 content 列表
+    create.getKnowledgeSwfs().get(0).setTest(List.of(test));
+
+    String knowledgeId = service.saveTheoryKnowledge(create).getData().getId();
+    assertTrue(knowledgeSwfDao.count("knowledgeId", knowledgeId) > 0, "课件应落库且不抛 NPE");
+  }
 }
