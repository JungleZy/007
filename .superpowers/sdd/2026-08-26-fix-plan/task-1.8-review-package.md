## commits
d43eb4a fix(p0-22): 干扰项生成死循环与随机偏置

## stat
 .../nip/service/PostMilitaryTermTrainService.java  | 28 +++++++-
 .../service/PostMilitaryTermTrainServiceTest.java  | 84 ++++++++++++++++++++++
 2 files changed, 109 insertions(+), 3 deletions(-)

## diff
diff --git a/src/main/java/com/nip/service/PostMilitaryTermTrainService.java b/src/main/java/com/nip/service/PostMilitaryTermTrainService.java
index 12d542a..12dc282 100644
--- a/src/main/java/com/nip/service/PostMilitaryTermTrainService.java
+++ b/src/main/java/com/nip/service/PostMilitaryTermTrainService.java
@@ -123,59 +123,81 @@ public class PostMilitaryTermTrainService {
    */
   public void generateTestPaper(PostMilitaryTermTrainAddDto dto, PostMilitaryTermTrainEntity save,
                                 Map<String, List<MilitaryTermDataEntity>> dataMap,
                                 List<PostMilitaryTermTrainTestPaperEntity> testPaperEntityList) {
     //生成训练考题
     for (int i = 0; i < dto.getTotalNumber(); i++) {
       int dataIndex;
       if (dto.getTypes().size() == 1) {
         dataIndex = 0;
       } else {
-        dataIndex = random.nextInt(dto.getTypes().size() - 1);
+        dataIndex = random.nextInt(dto.getTypes().size());
       }
       String dataId = dto.getTypes().get(dataIndex);
       //获得该类型的所有考题
       List<MilitaryTermDataEntity> militaryTermDataEntities = dataMap.get(dataId);
       if (militaryTermDataEntities == null) {
         i--;
         continue;
       }
+      long distinct = militaryTermDataEntities.stream()
+          .map(MilitaryTermDataEntity::getValue)
+          .filter(ObjectUtil::isNotEmpty)
+          .distinct()
+          .count();
+      if (distinct < 4) {
+        throw new IllegalArgumentException("类型 " + dataId + " 有效题目不足4条，无法生成干扰项");
+      }
       //考试题目
       int titleIndex;
       if (militaryTermDataEntities.size() == 1) {
         titleIndex = 0;
       } else {
-        titleIndex = random.nextInt(militaryTermDataEntities.size() - 1);
+        titleIndex = random.nextInt(militaryTermDataEntities.size());
       }
       //正确答案
       MilitaryTermDataEntity dataEntity = militaryTermDataEntities.get(titleIndex);
       //存放选项
       List<String> options = new ArrayList<>();
       //放入正确答案
       options.add(dataEntity.getValue());
       //循环3次拿同类型的其它错误答案
       int flag = 1;
 
       //判断是否生成与正确答案类型的选项
       boolean keyword = checkKeyword(dataEntity.getValue(), options);
       if (keyword) {
         flag = 2;
       }
       //封装test_paper对象
       PostMilitaryTermTrainTestPaperEntity testPaperEntity = new PostMilitaryTermTrainTestPaperEntity();
+      int attempts = 0;
       while (flag <= 3) {
+        if (++attempts > 100) {
+          log.warn("干扰项随机生成超过100次未完成，降级为顺序补足，title={}", dataEntity.getKey());
+          for (MilitaryTermDataEntity entity : militaryTermDataEntities) {
+            String v = entity.getValue();
+            if (ObjectUtil.isNotEmpty(v) && !options.contains(v)) {
+              options.add(v);
+              if (options.size() >= 4) {
+                break;
+              }
+            }
+          }
+          break;
+        }
         int optionId;
         if (militaryTermDataEntities.size() == 1) {
           optionId = 0;
         } else {
           //随机其它选项
-          optionId = random.nextInt(militaryTermDataEntities.size() - 1);
+          optionId = random.nextInt(militaryTermDataEntities.size());
         }
         if (titleIndex != optionId || optionId == 0) {
           MilitaryTermDataEntity entity = militaryTermDataEntities.get(optionId);
           if (ObjectUtil.isNotEmpty(entity.getValue())) {
             String value = entity.getValue();
             if (options.stream().anyMatch(s -> s.equals(value))) {
               //放入options
               boolean b = checkKeyword(value, options);
               if (b) {
                 flag++;
diff --git a/src/test/java/com/nip/service/PostMilitaryTermTrainServiceTest.java b/src/test/java/com/nip/service/PostMilitaryTermTrainServiceTest.java
new file mode 100644
index 0000000..a7a119b
--- /dev/null
+++ b/src/test/java/com/nip/service/PostMilitaryTermTrainServiceTest.java
@@ -0,0 +1,84 @@
+package com.nip.service;
+
+import com.google.gson.reflect.TypeToken;
+import com.nip.common.utils.JSONUtils;
+import com.nip.dto.PostMilitaryTermTrainAddDto;
+import com.nip.entity.MilitaryTermDataEntity;
+import com.nip.entity.PostMilitaryTermTrainEntity;
+import com.nip.entity.PostMilitaryTermTrainTestPaperEntity;
+import org.junit.jupiter.api.Test;
+
+import java.time.Duration;
+import java.util.ArrayList;
+import java.util.HashSet;
+import java.util.LinkedHashMap;
+import java.util.List;
+import java.util.Map;
+
+import static org.junit.jupiter.api.Assertions.*;
+
+/**
+ * P0#22：generateTestPaper 纯内存装配，直接 new（dao 不参与该方法）。
+ */
+class PostMilitaryTermTrainServiceTest {
+
+  private final PostMilitaryTermTrainService service =
+      new PostMilitaryTermTrainService(null, null, null, null);
+
+  /**
+   * 4 条同类型、value 互异的候选。value 刻意不含数字/无线/出口/入口/干线/小时/
+   * 线状/面状/接收/发射/战术/战役/三个以上顿号——checkKeyword 全路径不命中，
+   * 无法合成干扰项。修复前 nextInt(size-1) 使末元素永不可选，随机路径最多凑出
+   * 2 个干扰项，while (flag <= 3) 永不退出 → 测试超时（红）。
+   */
+  private static List<MilitaryTermDataEntity> fourPlainCandidates() {
+    List<MilitaryTermDataEntity> list = new ArrayList<>();
+    String[][] rows = {
+        {"甲术语", "甲种密语内容说明"},
+        {"乙术语", "乙种密语内容说明"},
+        {"丙术语", "丙种密语内容说明"},
+        {"丁术语", "丁种密语内容说明"},
+    };
+    for (String[] row : rows) {
+      list.add(new MilitaryTermDataEntity().setKey(row[0]).setValue(row[1]));
+    }
+    return list;
+  }
+
+  @Test
+  void generateTestPaperTerminatesWithExactlyFourCandidates() {
+    Map<String, List<MilitaryTermDataEntity>> dataMap = Map.of("type1", fourPlainCandidates());
+    PostMilitaryTermTrainAddDto dto = new PostMilitaryTermTrainAddDto();
+    dto.setTypes(List.of("type1"));
+    dto.setTotalNumber(10);
+    List<PostMilitaryTermTrainTestPaperEntity> out = new ArrayList<>();
+
+    assertTimeoutPreemptively(Duration.ofSeconds(2),
+        () -> service.generateTestPaper(dto, new PostMilitaryTermTrainEntity(), dataMap, out));
+
+    assertEquals(10, out.size());
+    for (PostMilitaryTermTrainTestPaperEntity paper : out) {
+      Map<String, String> optionMap =
+          JSONUtils.fromJson(paper.getOption(), new TypeToken<LinkedHashMap<String, String>>() {});
+      assertEquals(4, optionMap.size(), "每题必须 4 个选项: " + paper.getOption());
+      assertEquals(4, new HashSet<>(optionMap.values()).size(),
+          "4 个选项必须互异: " + paper.getOption());
+      assertNotNull(paper.getCorrectAnswer());
+      assertTrue(optionMap.containsKey(paper.getCorrectAnswer()),
+          "correctAnswer 必须指向存在的选项: " + paper.getCorrectAnswer());
+    }
+  }
+
+  @Test
+  void generateTestPaperRejectsTypeWithFewerThanFourDistinctValues() {
+    List<MilitaryTermDataEntity> three = fourPlainCandidates().subList(0, 3);
+    Map<String, List<MilitaryTermDataEntity>> dataMap = Map.of("type1", new ArrayList<>(three));
+    PostMilitaryTermTrainAddDto dto = new PostMilitaryTermTrainAddDto();
+    dto.setTypes(List.of("type1"));
+    dto.setTotalNumber(1);
+
+    assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
+        assertThrows(IllegalArgumentException.class,
+            () -> service.generateTestPaper(dto, new PostMilitaryTermTrainEntity(), dataMap, new ArrayList<>())));
+  }
+}
