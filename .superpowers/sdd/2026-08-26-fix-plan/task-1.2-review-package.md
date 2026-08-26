## commits
9a26708 fix(p0-2): 理论知识保存先删课件+吞异常导致丢失

## stat
 .../sdd/2026-08-26-fix-plan/task-1.2-report.md     | 40 +++++++++++++++
 .../com/nip/service/TheoryKnowledgeService.java    | 42 ++++++++-------
 .../nip/service/TheoryKnowledgeServiceTest.java    | 59 ++++++++++++++++++++++
 3 files changed, 122 insertions(+), 19 deletions(-)

## diff
diff --git a/.superpowers/sdd/2026-08-26-fix-plan/task-1.2-report.md b/.superpowers/sdd/2026-08-26-fix-plan/task-1.2-report.md
new file mode 100644
index 0000000..146f01f
--- /dev/null
+++ b/.superpowers/sdd/2026-08-26-fix-plan/task-1.2-report.md
@@ -0,0 +1,40 @@
+# Task 1.2 报告：理论知识保存先删课件+吞异常导致丢失（P0#2）
+
+结论：已修复，红→绿完整，一次提交。
+
+## 改动
+
+`src/main/java/com/nip/service/TheoryKnowledgeService.java` saveTheoryKnowledge：
+1. 方法开头新增校验：`getKnowledgeSwfs()==null` → `IllegalArgumentException("课件列表缺失")`；每个 swf 的空标题校验（原 :236-237）前移到删除之前。
+2. `knowledgeSwfDao.deleteAllByKnowledgeId` 下移至全部输入校验通过、主记录 save 之后。
+3. `s.getTest()` 使用点改为 `ListUtils.nullToEmpty(s.getTest())`。
+4. `findFirstByKnowledgeSwfIdAndVersions` 结果判空：null → `IllegalStateException("版本1测验不存在: swfId=" + test.getKnowledgeSwfId())`（原直接 `.getId()` 会 NPE）。
+5. 整体 try/catch(Exception) 删除；异常冒出经 JWTInterceptor 兜为 200+SYSTEM_ERROR（契约微调有意，见 global-constraints）。
+
+`src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java`：新增，仿 TestPaperServiceTest。建含 1 个课件的知识点 → 用 `knowledgeSwfs=null` 的 DTO 编辑 → 断言按 knowledgeId 计数的课件行仍在。
+
+## 红阶段证据（修复前）
+
+```
+[ERROR]   TheoryKnowledgeServiceTest.editWithNullSwfListKeepsExistingSwfs:57 原课件不得被静默删除 ==> expected: <true> but was: <false>
+[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
+[INFO] BUILD FAILURE
+```
+
+失败机理：编辑请求先删旧课件（:231），随后 `getKnowledgeSwfs().forEach` NPE 被 catch 吞掉返回 CODE_500，事务正常提交 → 旧课件永久丢失。
+
+## 绿阶段证据（修复后）
+
+```
+[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 32.42 s -- in com.nip.service.TheoryKnowledgeServiceTest
+[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
+[INFO] BUILD SUCCESS
+```
+
+命令：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=TheoryKnowledgeServiceTest`
+
+## Concerns
+
+- 修复后校验异常在删除前抛出，事务由 JTA 对 RuntimeException 回滚；即使删除后异常（如版本1测验缺失）也会回滚，不再有"删了但没写"的中间态提交。
+- `findFirstByKnowledgeSwfIdAndVersions` 判空是防 NPE 的最小改动，未覆盖该分支的专项测试（brief 只要求"缺 swf 列表"断言）。
+- 空标题业务码从 CODE_500 变为 SYSTEM_ERROR（消息保留），Phase 4 ValidationExceptionMapper 恢复精确业务码——计划内契约微调。
diff --git a/src/main/java/com/nip/service/TheoryKnowledgeService.java b/src/main/java/com/nip/service/TheoryKnowledgeService.java
index 303222b..9bb0be7 100644
--- a/src/main/java/com/nip/service/TheoryKnowledgeService.java
+++ b/src/main/java/com/nip/service/TheoryKnowledgeService.java
@@ -3,20 +3,21 @@ package com.nip.service;
 import cn.hutool.core.date.DateUnit;
 import cn.hutool.core.date.DateUtil;
 import cn.hutool.core.util.ObjectUtil;
 import com.google.gson.reflect.TypeToken;
 import com.nip.common.constants.ResponseCode;
 import com.nip.common.constants.TheoryKnowledgeClassifyTypeEnum;
 import com.nip.common.response.Response;
 import com.nip.common.response.ResponseResult;
 import com.nip.common.utils.DateTimeUtil;
 import com.nip.common.utils.JSONUtils;
+import com.nip.common.utils.ListUtils;
 import com.nip.common.utils.PojoUtils;
 import com.nip.dao.*;
 import com.nip.dto.TheoryKnowledgeDto;
 import com.nip.dto.TheoryKnowledgesDto;
 import com.nip.dto.sql.FindTheoryKnowledgeDto;
 import com.nip.dto.vo.TheoryKnowledgeSwfVO;
 import com.nip.dto.vo.TheoryKnowledgeTestVO;
 import com.nip.entity.*;
 import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.inject.Inject;
@@ -215,53 +216,60 @@ public class TheoryKnowledgeService {
   }
 
   /**
    * 保存理论知识信息
    *
    * @param knowledgesDto 理论知识DTO对象，包含需要保存的知识信息
    * @return 返回保存后的理论知识实体对象
    */
   @Transactional
   public Response<TheoryKnowledgeEntity> saveTheoryKnowledge(TheoryKnowledgesDto knowledgesDto) {
-    try {
-      if (ObjectUtil.isEmpty(knowledgesDto.getKnowledge().getTitle())) {
-        throw new InvalidTitleException("标题不能是空!");
+    if (ObjectUtil.isEmpty(knowledgesDto.getKnowledge().getTitle())) {
+      throw new InvalidTitleException("标题不能是空!");
+    }
+    if (knowledgesDto.getKnowledgeSwfs() == null) {
+      throw new IllegalArgumentException("课件列表缺失");
+    }
+    knowledgesDto.getKnowledgeSwfs().forEach(s -> {
+      if (StringUtils.isEmpty(s.getTitle())) {
+        throw new IllegalArgumentException("标题不能是空!");
+      }
+    });
+    TheoryKnowledgeEntity knowledge = knowledgeDao.save(knowledgesDto.getKnowledge());
+    //输入校验全部通过后再删除之前的课件
+    knowledgeSwfDao.deleteAllByKnowledgeId(knowledgesDto.getKnowledge().getId());
+    knowledgesDto.getKnowledgeSwfs().forEach(s -> {
+      if (StringUtils.isEmpty(s.getId())) {
+        s.setKnowledgeId(knowledge.getId());
       }
-      TheoryKnowledgeEntity knowledge = knowledgeDao.save(knowledgesDto.getKnowledge());
-      //删除之前的课件
-      knowledgeSwfDao.deleteAllByKnowledgeId(knowledgesDto.getKnowledge().getId());
-      knowledgesDto.getKnowledgeSwfs().forEach(s -> {
-        if (StringUtils.isEmpty(s.getId())) {
-          s.setKnowledgeId(knowledge.getId());
-        }
-        if (StringUtils.isEmpty(s.getTitle())) {
-          throw new IllegalArgumentException("标题不能是空!");
-        }
         //如果id是null则需要设置默认值
         if (s.getId() == null) {
           s.setCreateUserId(knowledge.getCreateUserId());
         }
         //保存到数据库中
         TheoryKnowledgeSwfEntity swfEntity = PojoUtils.convertOne(s, TheoryKnowledgeSwfEntity.class);
         TheoryKnowledgeSwfEntity saveSwfEntity = knowledgeSwfDao.save(swfEntity);
         //拿到测验test
-        List<TheoryKnowledgeTestVO> testVOS = s.getTest();
+        List<TheoryKnowledgeTestVO> testVOS = ListUtils.nullToEmpty(s.getTest());
         testVOS.forEach(test -> {
           //当ID是null的时候，设置默认值
           if (test.getId() == null) {
             test.setCreateUserId(knowledge.getCreateUserId());
             test.setKnowledgeId(knowledge.getId());
             test.setKnowledgeSwfId(saveSwfEntity.getId());
             test.setCreateTime(System.currentTimeMillis() + "");
           } else {
             if (test.getVersions() == 1) {
               TheoryKnowledgeTestEntity firstByKnowledgeSwfIdAndVersions = theoryKnowledgeTestDao.findFirstByKnowledgeSwfIdAndVersions(test.getKnowledgeSwfId(), 1);
+              if (firstByKnowledgeSwfIdAndVersions == null) {
+                throw new IllegalStateException("版本1测验不存在: swfId=" + test.getKnowledgeSwfId());
+              }
               TheoryKnowledgeTestEntity testEntity = PojoUtils.convertOne(test, TheoryKnowledgeTestEntity.class, (t, e) -> e.setKnowledgeSwfId(saveSwfEntity.getId()));
               if (!firstByKnowledgeSwfIdAndVersions.getId().equals(testEntity.getId())) {
                 theoryKnowledgeTestUserDao.deleteByKnowledgeIdAndKnowledgeSwfId(test.getKnowledgeId(), test.getKnowledgeSwfId());
               } else {
                 List<TheoryKnowledgeTestContentEntity> allByKnowledgeTestId = theoryKnowledgeTestContentDao.findAllByKnowledgeTestId(testEntity.getId());
                 List<TheoryKnowledgeTestContentEntity> knowledgeTestContents = test.getKnowledgeTestContents();
                 if (!listEquals(allByKnowledgeTestId, knowledgeTestContents)) {
                   theoryKnowledgeTestUserDao.deleteByKnowledgeIdAndKnowledgeSwfId(test.getKnowledgeId(), test.getKnowledgeSwfId());
                 }
               }
@@ -281,25 +289,21 @@ public class TheoryKnowledgeService {
             if (content.getId() == null) {
               content.setCreateUserId(knowledge.getCreateUserId());
               content.setKnowledgeId(knowledge.getId());
               content.setKnowledgeSwfId(saveSwfEntity.getId());
               content.setKnowledgeTestId(saveTest.getId());
             }
             theoryKnowledgeTestContentDao.save(content);
           });
         });
       });
-      return ResponseResult.success(knowledge);
-    } catch (Exception e) {
-      log.error("保存失败:{}", e.getMessage());
-      return ResponseResult.error(ResponseCode.CODE_500.getMessage());
-    }
+    return ResponseResult.success(knowledge);
   }
 
   /**
    * 比较两个列表是否相等
    *
    * @param t1 第一个列表，泛型类型为T
    * @param t2 第二个列表，泛型类型为T
    * @return 如果两个列表相等返回true，否则返回false
    * <p>
    * 此方法主要解决列表内容的相等问题，包括对列表引用、大小和元素的比较
diff --git a/src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java b/src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java
new file mode 100644
index 0000000..802d068
--- /dev/null
+++ b/src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java
@@ -0,0 +1,59 @@
+package com.nip.service;
+
+import com.nip.dao.TheoryKnowledgeSwfDao;
+import com.nip.dao.UserDao;
+import com.nip.dto.TheoryKnowledgesDto;
+import com.nip.dto.vo.TheoryKnowledgeSwfVO;
+import com.nip.entity.TheoryKnowledgeEntity;
+import com.nip.entity.UserEntity;
+import com.nip.testsupport.Fixtures;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import java.util.List;
+
+import static org.junit.jupiter.api.Assertions.assertTrue;
+
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class TheoryKnowledgeServiceTest {
+  @Inject TheoryKnowledgeService service;
+  @Inject TheoryKnowledgeSwfDao knowledgeSwfDao;
+  @Inject UserDao userDao;
+
+  private TheoryKnowledgesDto knowledges(String title, String userId) {
+    TheoryKnowledgeEntity knowledge = new TheoryKnowledgeEntity();
+    knowledge.setTitle(title);
+    knowledge.setCreateUserId(userId);
+    TheoryKnowledgeSwfVO swf = new TheoryKnowledgeSwfVO();
+    swf.setTitle("章节1");
+    swf.setTest(List.of());
+    TheoryKnowledgesDto dto = new TheoryKnowledgesDto();
+    dto.setKnowledge(knowledge);
+    dto.setKnowledgeSwfs(List.of(swf));
+    return dto;
+  }
+
+  @Test
+  void editWithNullSwfListKeepsExistingSwfs() {
+    UserEntity user = Fixtures.user(userDao, "t-theory");
+    TheoryKnowledgesDto create = knowledges("k1", user.getId());
+    String knowledgeId = service.saveTheoryKnowledge(create).getData().getId();
+    long before = knowledgeSwfDao.count("knowledgeId", knowledgeId);
+    assertTrue(before > 0);
+
+    TheoryKnowledgesDto edit = knowledges("k1-edit", user.getId());
+    edit.getKnowledge().setId(knowledgeId);
+    edit.setKnowledgeSwfs(null); // 缺课件列表的编辑请求
+
+    try {
+      service.saveTheoryKnowledge(edit);
+    } catch (RuntimeException expectedAfterFix) {
+      // 修复后允许抛（课件列表缺失）；关键契约在下一行
+    }
+    assertTrue(knowledgeSwfDao.count("knowledgeId", knowledgeId) > 0, "原课件不得被静默删除");
+  }
+}
