## commits
bd551a5 fix(p0-4/19/20): 考试编辑守卫+快照独立主键+空列表归一

## stat
 .../nip/service/TheoryKnowledgeExamService.java    |  51 ++++++----
 .../service/TheoryKnowledgeExamServiceTest.java    | 111 +++++++++++++++++++++
 2 files changed, 141 insertions(+), 21 deletions(-)

## diff
diff --git a/src/main/java/com/nip/service/TheoryKnowledgeExamService.java b/src/main/java/com/nip/service/TheoryKnowledgeExamService.java
index c647623..09e400e 100644
--- a/src/main/java/com/nip/service/TheoryKnowledgeExamService.java
+++ b/src/main/java/com/nip/service/TheoryKnowledgeExamService.java
@@ -1,20 +1,21 @@
 package com.nip.service;
 
 import cn.hutool.core.date.DateUtil;
 import cn.hutool.core.util.ObjectUtil;
 import com.google.gson.reflect.TypeToken;
 import com.nip.common.constants.CodeConstants;
 import com.nip.common.response.Response;
 import com.nip.common.response.ResponseResult;
 import com.nip.common.utils.DateTimeUtil;
 import com.nip.common.utils.JSONUtils;
+import com.nip.common.utils.ListUtils;
 import com.nip.common.utils.PojoUtils;
 import com.nip.dao.*;
 import com.nip.dto.TestPaperDto;
 import com.nip.dto.TheoryKnowledgeExamDto;
 import com.nip.dto.TheoryKnowledgeQuestionCheckDto;
 import com.nip.dto.sql.FindAllExamByIdDto;
 import com.nip.dto.sql.FindAllExamDto;
 import com.nip.dto.sql.FindExamIdDto;
 import com.nip.dto.vo.TheoryKnowLedgeExamAnalyseVO;
 import com.nip.dto.vo.TheoryKnowLedgeExamUserVO;
@@ -63,33 +64,41 @@ public class TheoryKnowledgeExamService {
   }
 
   @Transactional
   public Response<Void> saveTheoryKnowledgeExam(String token, TheoryKnowledgeExamDto dto) {
     UserEntity userEntity = userService.getUserByToken(token);
     TheoryKnowledgeExamEntity entity = PojoUtils.convertOne(dto, TheoryKnowledgeExamEntity.class);
     entity.setCreateUserId(userEntity.getId());
     entity.setState(1);
     TheoryKnowledgeExamEntity save = theoryKnowledgeExamDao.save(entity);
     TestPaperDto testPaper = dto.getTestPaper();
-    if (!StringUtils.isEmpty(testPaper.getId())) {
-      theoryKnowledgeExamTestPaperDao.deleteById(testPaper.getId());
-      theoryKnowledgeExamUserDao.deleteAllByExamId(save.getId());
+    // #4 状态守卫：已有作答/进行中/已交卷的考生存在时禁止重建
+    long touched = theoryKnowledgeExamUserDao
+        .count("examId = ?1 and (state <> 1 or score > 0)", save.getId());
+    if (touched > 0) {
+      throw new IllegalStateException("考试已有作答记录，禁止编辑重建考生名单");
     }
-    TheoryKnowledgeExamTestPaperEntity theoryKnowledgeExamTestPaperEntity = PojoUtils.convertOne(testPaper,
+    // #19 只删本考试自己的旧快照，绝不按试卷 id 删
+    theoryKnowledgeExamTestPaperDao.delete("examId", save.getId());
+    theoryKnowledgeExamUserDao.deleteAllByExamId(save.getId());
+
+    TheoryKnowledgeExamTestPaperEntity snap = PojoUtils.convertOne(testPaper,
         TheoryKnowledgeExamTestPaperEntity.class);
-    theoryKnowledgeExamTestPaperEntity.setExamId(save.getId());
-    theoryKnowledgeExamTestPaperEntity.setSingleChoiceList(JSONUtils.toJson(testPaper.getSingleChoice()));
-    theoryKnowledgeExamTestPaperEntity.setMultipleChoiceList(JSONUtils.toJson(testPaper.getMultipleChoice()));
-    theoryKnowledgeExamTestPaperEntity.setJudgeList(JSONUtils.toJson(testPaper.getJudge()));
-    theoryKnowledgeExamTestPaperEntity.setCompletionList(JSONUtils.toJson(testPaper.getCompletion()));
-    theoryKnowledgeExamTestPaperEntity.setShortAnswer(JSONUtils.toJson(testPaper.getShortAnswer()));
-    theoryKnowledgeExamTestPaperDao.save(theoryKnowledgeExamTestPaperEntity);
+    snap.setId(null); // #19 快照永远新建，不复用源试卷主键
+    snap.setExamId(save.getId());
+    // #20 五列表 null 归一后再序列化
+    snap.setSingleChoiceList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getSingleChoice())));
+    snap.setMultipleChoiceList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getMultipleChoice())));
+    snap.setJudgeList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getJudge())));
+    snap.setCompletionList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getCompletion())));
+    snap.setShortAnswer(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getShortAnswer())));
+    theoryKnowledgeExamTestPaperDao.save(snap);
     dto.getStuId().forEach(stu -> {
       TheoryKnowledgeExamUserEntity theoryKnowledgeExamUserEntity = new TheoryKnowledgeExamUserEntity();
       theoryKnowledgeExamUserEntity.setUserId(stu);
       theoryKnowledgeExamUserEntity.setExamId(save.getId());
       theoryKnowledgeExamUserEntity.setState(1);
       theoryKnowledgeExamUserEntity.setScore(0);
       theoryKnowledgeExamUserEntity.setIsSelfTesting(1);
       theoryKnowledgeExamUserDao.save(theoryKnowledgeExamUserEntity);
     });
     return ResponseResult.success();
@@ -315,35 +324,35 @@ public class TheoryKnowledgeExamService {
 
     // 拿到本场考试的就及格比吧 计算良的区间 公式：(总分-及格分)/2+及格分
     Integer total = BigDecimal.valueOf((long) testPaperEntity.getTotal() - (long) testPaperEntity.getPassMark())
         .divide(new BigDecimal(2), 0, RoundingMode.DOWN)
         .add(new BigDecimal(testPaperEntity.getPassMark()))
         .intValue();
 
     List<TheoryKnowLedgeExamUserVO> previousUser = new ArrayList<>();
     List<TheoryKnowledgeQuestionEntity> questionEntities = new ArrayList<>();
 
-    questionEntities.addAll(JSONUtils
+    questionEntities.addAll(ListUtils.nullToEmpty(JSONUtils
         .fromJson(testPaperEntity.getSingleChoiceList(), new TypeToken<>() {
-        }));
-    questionEntities.addAll(JSONUtils
+        })));
+    questionEntities.addAll(ListUtils.nullToEmpty(JSONUtils
         .fromJson(testPaperEntity.getMultipleChoiceList(), new TypeToken<>() {
-        }));
-    questionEntities.addAll(JSONUtils
+        })));
+    questionEntities.addAll(ListUtils.nullToEmpty(JSONUtils
         .fromJson(testPaperEntity.getJudgeList(), new TypeToken<>() {
-        }));
-    questionEntities.addAll(JSONUtils
+        })));
+    questionEntities.addAll(ListUtils.nullToEmpty(JSONUtils
         .fromJson(testPaperEntity.getCompletionList(), new TypeToken<>() {
-        }));
-    questionEntities.addAll(JSONUtils
+        })));
+    questionEntities.addAll(ListUtils.nullToEmpty(JSONUtils
         .fromJson(testPaperEntity.getShortAnswer(), new TypeToken<>() {
-        }));
+        })));
 
     // 再依次比对找出记录题目错误的次数
     Map<String, TheoryKnowledgeQuestionErrorTopVO> errorTop3 = new HashMap<>();
 
     examUserEntityList.forEach(item -> {
       String userId = item.getUserId();
       UserEntity userEntity = userDao.findById(userId);
       String endTime = item.getEndTime();
       // 查询上一次考试成绩
       TheoryKnowledgeExamUserEntity previous = theoryKnowledgeExamUserDao.findByUserIdAndEndTimePrevious(userId,
diff --git a/src/test/java/com/nip/service/TheoryKnowledgeExamServiceTest.java b/src/test/java/com/nip/service/TheoryKnowledgeExamServiceTest.java
new file mode 100644
index 0000000..88476f3
--- /dev/null
+++ b/src/test/java/com/nip/service/TheoryKnowledgeExamServiceTest.java
@@ -0,0 +1,111 @@
+package com.nip.service;
+
+import com.nip.dao.TheoryKnowledgeExamDao;
+import com.nip.dao.TheoryKnowledgeExamTestPaperDao;
+import com.nip.dao.TheoryKnowledgeExamUserDao;
+import com.nip.dao.UserDao;
+import com.nip.dto.TestPaperDto;
+import com.nip.dto.TestPaperQuestionDto;
+import com.nip.dto.TheoryKnowledgeExamDto;
+import com.nip.entity.TheoryKnowledgeExamUserEntity;
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
+import static org.junit.jupiter.api.Assertions.*;
+
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class TheoryKnowledgeExamServiceTest {
+  @Inject TheoryKnowledgeExamService service;
+  @Inject TheoryKnowledgeExamDao examDao;
+  @Inject TheoryKnowledgeExamTestPaperDao examTestPaperDao;
+  @Inject TheoryKnowledgeExamUserDao examUserDao;
+  @Inject UserDao userDao;
+
+  /** 模拟前端建考试时携带的源试卷；sourcePaperId 为 null 表示新建（走 persist）。 */
+  private TestPaperDto paper(String sourcePaperId) {
+    TestPaperDto paper = new TestPaperDto();
+    paper.setId(sourcePaperId);
+    paper.setName("试卷");
+    paper.setTotal(100);
+    paper.setPassMark(60);
+    TestPaperQuestionDto q = new TestPaperQuestionDto();
+    q.setTopic("1+1=?");
+    q.setType(1);
+    q.setAnswer("2");
+    paper.setSingleChoice(List.of(q));
+    paper.setMultipleChoice(List.of());
+    paper.setJudge(List.of());
+    paper.setCompletion(List.of());
+    paper.setShortAnswer(List.of());
+    return paper;
+  }
+
+  private TheoryKnowledgeExamDto exam(String title, TestPaperDto paper, String stuId) {
+    TheoryKnowledgeExamDto dto = new TheoryKnowledgeExamDto();
+    dto.setTitle(title);
+    dto.setDuration("60");
+    dto.setTestPaper(paper);
+    dto.setStuId(List.of(stuId));
+    return dto;
+  }
+
+  private String examIdByTitle(String title) {
+    return examDao.find("title", title).firstResult().getId();
+  }
+
+  @Test
+  void twoExamsOnSamePaperKeepBothSnapshots() {
+    UserEntity user = Fixtures.user(userDao, "t-exam-1");
+    service.saveTheoryKnowledgeExam("t-exam-1", exam("exam-snap-a", paper("src-paper-1"), user.getId()));
+    service.saveTheoryKnowledgeExam("t-exam-1", exam("exam-snap-b", paper("src-paper-1"), user.getId()));
+
+    String examA = examIdByTitle("exam-snap-a");
+    String examB = examIdByTitle("exam-snap-b");
+    assertEquals(1, examTestPaperDao.count("examId", examA), "考试A的快照不得被同试卷的考试B抹掉");
+    assertEquals(1, examTestPaperDao.count("examId", examB), "考试B应有自己的快照");
+  }
+
+  @Test
+  void editExamWithAnsweredUsersIsRejected() {
+    UserEntity user = Fixtures.user(userDao, "t-exam-2");
+    service.saveTheoryKnowledgeExam("t-exam-2", exam("exam-guard", paper(null), user.getId()));
+    String examId = examIdByTitle("exam-guard");
+
+    TheoryKnowledgeExamUserEntity examUser = examUserDao.findAllByExamId(examId).get(0);
+    examUser.setState(2); // 学生考核中
+    examUser.setScore(55);
+    examUserDao.save(examUser);
+
+    // 前端编辑流：findTheoryKnowledgeExamById 返回快照作为 paper，编辑请求带快照 id
+    String snapshotId = examTestPaperDao.findAllByExamId(examId).getId();
+    TheoryKnowledgeExamDto edit = exam("exam-guard-edit", paper(snapshotId), user.getId());
+    edit.setId(examId);
+    assertThrows(IllegalStateException.class,
+        () -> service.saveTheoryKnowledgeExam("t-exam-2", edit),
+        "已有作答记录的考试编辑必须被拒绝");
+
+    TheoryKnowledgeExamUserEntity after = examUserDao.findById(examUser.getId());
+    assertNotNull(after, "已作答考生行不得被删除重建");
+    assertEquals(2, after.getState(), "state 不得被重置");
+    assertEquals(55, after.getScore(), "score 不得被清零");
+  }
+
+  @Test
+  void analyseWithMissingTypeListDoesNotNPE() {
+    UserEntity user = Fixtures.user(userDao, "t-exam-3");
+    TestPaperDto paper = paper(null);
+    paper.setShortAnswer(null); // 缺一个题型列表
+    service.saveTheoryKnowledgeExam("t-exam-3", exam("exam-analyse", paper, user.getId()));
+    String examId = examIdByTitle("exam-analyse");
+
+    assertDoesNotThrow(() -> service.examineAnalyse(examId));
+  }
+}
