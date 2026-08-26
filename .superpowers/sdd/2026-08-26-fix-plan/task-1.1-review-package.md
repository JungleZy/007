## commits
d0bf56c fix(p0-1): 试卷编辑先删后写+吞异常导致丢题

## stat
 .../java/com/nip/service/TestPaperService.java     | 59 ++++++++++------------
 .../java/com/nip/service/TestPaperServiceTest.java | 59 ++++++++++++++++++++++
 2 files changed, 87 insertions(+), 31 deletions(-)

## diff
diff --git a/src/main/java/com/nip/service/TestPaperService.java b/src/main/java/com/nip/service/TestPaperService.java
index e033d38..53755a8 100644
--- a/src/main/java/com/nip/service/TestPaperService.java
+++ b/src/main/java/com/nip/service/TestPaperService.java
@@ -1,16 +1,17 @@
 package com.nip.service;
 
 import cn.hutool.core.util.ObjectUtil;
 import com.nip.common.response.Response;
 import com.nip.common.response.ResponseResult;
 import com.nip.common.utils.PojoUtils;
+import com.nip.common.utils.ListUtils;
 import com.nip.dao.TestPaperDao;
 import com.nip.dao.TestPaperQuestionDao;
 import com.nip.dao.TheoryKnowledgeQuestionLevelDao;
 import com.nip.dto.TestPaperDto;
 import com.nip.dto.TestPaperQuestionDto;
 import com.nip.entity.TestPaperEntity;
 import com.nip.entity.TestPaperQuestionEntity;
 import com.nip.entity.TheoryKnowledgeQuestionLevelEntity;
 import com.nip.entity.UserEntity;
 import jakarta.enterprise.context.ApplicationScoped;
@@ -51,52 +52,48 @@ public class TestPaperService {
    * 保存试卷信息
    *
    * @param token        用户令牌，用于验证用户身份
    * @param testPaperDto 试卷数据传输对象，包含试卷的相关信息
    * @return 返回保存结果，成功或失败
    *         <p>
    *         本方法首先根据传入的试卷DTO创建或更新试卷实体，然后根据试卷类型保存试卷题目
    */
   @Transactional
   public Response<Void> saveTestPaper(String token, TestPaperDto testPaperDto) {
-    try {
-      TestPaperEntity entity = new TestPaperEntity();
-      if (ObjectUtil.isNotEmpty(testPaperDto.getId())) {
-        entity.setId(testPaperDto.getId());
-        testPaperQuestionDao.deleteAllByTestPaperId(testPaperDto.getId());
-      }
-      entity.setName(testPaperDto.getName());
-      entity.setLevelId(testPaperDto.getLevelId());
-      entity.setTotal(testPaperDto.getTotal());
-      entity.setPassMark(testPaperDto.getPassMark());
-      UserEntity userEntity = userService.getUserByToken(token);
-      entity.setCreateUserId(userEntity.getId());
-      entity.setCreateUserName(userEntity.getUserName());
-      entity.setPassTheExamThan(testPaperDto.getPassTheExamThan());
-      TestPaperEntity save = testPaperDao.save(entity);
-      List<TestPaperQuestionDto> testPaperQuestionDtos = new ArrayList<>();
-      testPaperQuestionDtos.addAll(testPaperDto.getSingleChoice());
-      testPaperQuestionDtos.addAll(testPaperDto.getMultipleChoice());
-      testPaperQuestionDtos.addAll(testPaperDto.getJudge());
-      testPaperQuestionDtos.addAll(testPaperDto.getCompletion());
-      testPaperQuestionDtos.addAll(testPaperDto.getShortAnswer());
-      testPaperQuestionDtos.forEach(ques -> {
-        TestPaperQuestionEntity testPaperQuestionEntity1 = PojoUtils.convertOne(ques, TestPaperQuestionEntity.class);
-        testPaperQuestionEntity1.setId(null);
-        testPaperQuestionEntity1.setTestPaperId(save.getId());
-        testPaperQuestionDao.save(testPaperQuestionEntity1);
-      });
-      return ResponseResult.success();
-    } catch (Exception e) {
-      log.error("保存试卷失败：{}", e.getMessage());
-      return ResponseResult.error();
+    List<TestPaperQuestionDto> testPaperQuestionDtos = new ArrayList<>();
+    testPaperQuestionDtos.addAll(ListUtils.nullToEmpty(testPaperDto.getSingleChoice()));
+    testPaperQuestionDtos.addAll(ListUtils.nullToEmpty(testPaperDto.getMultipleChoice()));
+    testPaperQuestionDtos.addAll(ListUtils.nullToEmpty(testPaperDto.getJudge()));
+    testPaperQuestionDtos.addAll(ListUtils.nullToEmpty(testPaperDto.getCompletion()));
+    testPaperQuestionDtos.addAll(ListUtils.nullToEmpty(testPaperDto.getShortAnswer()));
+
+    TestPaperEntity entity = new TestPaperEntity();
+    if (ObjectUtil.isNotEmpty(testPaperDto.getId())) {
+      entity.setId(testPaperDto.getId());
+      testPaperQuestionDao.deleteAllByTestPaperId(testPaperDto.getId()); // 新列表组装完成后才删
     }
+    entity.setName(testPaperDto.getName());
+    entity.setLevelId(testPaperDto.getLevelId());
+    entity.setTotal(testPaperDto.getTotal());
+    entity.setPassMark(testPaperDto.getPassMark());
+    UserEntity userEntity = userService.getUserByToken(token);
+    entity.setCreateUserId(userEntity.getId());
+    entity.setCreateUserName(userEntity.getUserName());
+    entity.setPassTheExamThan(testPaperDto.getPassTheExamThan());
+    TestPaperEntity save = testPaperDao.save(entity);
+    testPaperQuestionDtos.forEach(ques -> {
+      TestPaperQuestionEntity q = PojoUtils.convertOne(ques, TestPaperQuestionEntity.class);
+      q.setId(null);
+      q.setTestPaperId(save.getId());
+      testPaperQuestionDao.save(q);
+    });
+    return ResponseResult.success();
   }
 
   /**
    * 获取所有试卷的信息
    *
    * 此方法从数据库中检索所有试卷实体，将它们转换为DTO（数据传输对象）格式，并包含每个试卷的所有相关问题
    * 问题根据类型被分类到不同的列表中，以便于后续处理和展示
    *
    * @return 包含TestPaperDto列表的响应对象，如果发生错误，返回错误响应
    */
diff --git a/src/test/java/com/nip/service/TestPaperServiceTest.java b/src/test/java/com/nip/service/TestPaperServiceTest.java
new file mode 100644
index 0000000..16f9781
--- /dev/null
+++ b/src/test/java/com/nip/service/TestPaperServiceTest.java
@@ -0,0 +1,59 @@
+package com.nip.service;
+
+import com.nip.dao.*;
+import com.nip.dto.TestPaperDto;
+import com.nip.dto.TestPaperQuestionDto;
+import com.nip.testsupport.*;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import java.util.List;
+
+import static org.junit.jupiter.api.Assertions.*;
+
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class TestPaperServiceTest {
+  @Inject TestPaperService service;
+  @Inject TestPaperQuestionDao questionDao;
+  @Inject UserDao userDao;
+
+  private TestPaperDto paper(String name) {
+    TestPaperDto dto = new TestPaperDto();
+    dto.setName(name);
+    dto.setTotal(100);
+    dto.setPassMark(60);
+    TestPaperQuestionDto q = new TestPaperQuestionDto();
+    q.setTopic("1+1=?");
+    q.setType(1); // 单选；null 会在 findAllTestPaper:130 拆箱 NPE
+    dto.setSingleChoice(List.of(q));
+    dto.setMultipleChoice(List.of());
+    dto.setJudge(List.of());
+    dto.setCompletion(List.of());
+    dto.setShortAnswer(List.of());
+    return dto;
+  }
+
+  @Test
+  void updateWithNullTypeListKeepsExistingQuestions() {
+    Fixtures.user(userDao, "t-paper");
+    TestPaperDto dto = paper("p1");
+    service.saveTestPaper("t-paper", dto);
+    String paperId = service.findAllTestPaper().getData().get(0).getId();
+    long before = questionDao.count("testPaperId", paperId);
+    assertTrue(before > 0);
+
+    TestPaperDto edit = paper("p1-edit");
+    edit.setId(paperId);
+    edit.setShortAnswer(null); // 缺一个题型列表
+
+    try {
+      service.saveTestPaper("t-paper", edit);
+    } catch (RuntimeException expectedAfterFix) {
+      // 修复后允许抛（null 归一后实际不抛）；关键契约在下一行
+    }
+    assertTrue(questionDao.count("testPaperId", paperId) > 0, "原题目不得被静默删除");
+  }
+}
