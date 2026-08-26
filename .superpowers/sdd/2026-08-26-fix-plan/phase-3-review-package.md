## commits
5fb76f1
1fae6cf
1e498d3
409c512
4d46edb
70bb600


---
commit 5fb76f172cf64d1bcb5080393523ebf24a874fe3
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 03:57:14 2026 +0800

    fix(scoring-1): characterization 快照锁定 TickerPatUtils 现行为（真实 patKeys/规则样本）

diff --git a/src/test/java/com/nip/common/utils/TickerPatUtilsCharacterizationTest.java b/src/test/java/com/nip/common/utils/TickerPatUtilsCharacterizationTest.java
new file mode 100644
index 0000000..7951d87
--- /dev/null
+++ b/src/test/java/com/nip/common/utils/TickerPatUtilsCharacterizationTest.java
@@ -0,0 +1,133 @@
+package com.nip.common.utils;
+
+import com.google.gson.reflect.TypeToken;
+import com.nip.dto.PostTelegramTrainFinishInfoDto;
+import com.nip.dto.score.PostTelegramTrainRule;
+import com.nip.dto.vo.PostTelegramTrainResolverVO;
+import com.nip.dto.vo.PostTelegramTrainScoreVO;
+import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
+import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
+import org.junit.jupiter.api.Test;
+
+import java.io.IOException;
+import java.io.InputStream;
+import java.io.UncheckedIOException;
+import java.nio.charset.StandardCharsets;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.LinkedHashMap;
+import java.util.List;
+import java.util.Map;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.fail;
+
+/**
+ * Task 3.1：TickerPatUtils 评分核心的 characterization 快照。
+ * 输入样本在 src/test/resources/scoring/（patKeys 与规则取自 docs/database/project006.sql 真实数据），
+ * 期望输出在 src/test/resources/scoring/expected/。
+ * 重新生成快照：SCORING_UPDATE=1 运行本测试后人工核对 diff。
+ */
+class TickerPatUtilsCharacterizationTest {
+
+  private static final Path EXPECTED_DIR = Path.of("src/test/resources/scoring/expected");
+
+  static class ResolverCase {
+    List<String> patKeys;
+    List<PostTelegramTrainContentAddParam> userContents;
+  }
+
+  static class GapCall {
+    String patKey;
+    int i;
+    List<List<PostTelegramTrainFinishInfoDto.PatLogs>> patLogs;
+  }
+
+  static class GapCase {
+    List<PostTelegramTrainFinishInfoDto> standards;
+    List<GapCall> calls;
+  }
+
+  private static String resource(String name) {
+    try (InputStream in = TickerPatUtilsCharacterizationTest.class.getResourceAsStream("/scoring/" + name)) {
+      if (in == null) {
+        throw new IllegalStateException("缺少测试资源 scoring/" + name);
+      }
+      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
+    } catch (IOException e) {
+      throw new UncheckedIOException(e);
+    }
+  }
+
+  private static PostTelegramTrainRule rule() {
+    return TickerPatUtils.parseContent(resource("grading-rule-type0.json"));
+  }
+
+  private static void assertSnapshot(String name, Object actualPayload) {
+    String actual = JSONUtils.gson.newBuilder().setPrettyPrinting().create().toJson(actualPayload);
+    Path expectedFile = EXPECTED_DIR.resolve(name + ".json");
+    if ("1".equals(System.getenv("SCORING_UPDATE"))) {
+      try {
+        Files.createDirectories(EXPECTED_DIR);
+        Files.writeString(expectedFile, actual + "\n");
+      } catch (IOException e) {
+        throw new UncheckedIOException(e);
+      }
+      return;
+    }
+    if (!Files.exists(expectedFile)) {
+      fail("缺少快照 " + expectedFile + "，先用 SCORING_UPDATE=1 生成并人工核对");
+    }
+    try {
+      assertEquals(Files.readString(expectedFile).stripTrailing(), actual, name);
+    } catch (IOException e) {
+      throw new UncheckedIOException(e);
+    }
+  }
+
+  private static Map<String, Object> runResolver(String caseFile) {
+    ResolverCase c = JSONUtils.fromJson(resource(caseFile), new TypeToken<>() {
+    });
+    PostTelegramTrainScoreVO scoreVO = new PostTelegramTrainScoreVO();
+    PostTelegramTrainResolverVO vo = TickerPatUtils.resolverMessage(c.patKeys, scoreVO, rule(), c.userContents);
+    Map<String, Object> payload = new LinkedHashMap<>();
+    payload.put("resolverMessage", vo.getResolverMessage());
+    payload.put("resolverPatLogs", vo.getResolverPatLogs());
+    payload.put("resolverMoresTime", vo.getResolverMoresTime());
+    payload.put("resolverMoresValue", vo.getResolverMoresValue());
+    payload.put("scoreVO", scoreVO);
+    return payload;
+  }
+
+  @Test
+  void resolverMessageNormalGroupsWithBlank() {
+    assertSnapshot("resolver-normal-with-blank", runResolver("resolver-case-normal-with-blank.json"));
+  }
+
+  @Test
+  void resolverMessageGluedGroups() {
+    assertSnapshot("resolver-glued", runResolver("resolver-case-glued.json"));
+  }
+
+  @Test
+  void resolverMessageQuestionMarkCorrections() {
+    assertSnapshot("resolver-question-marks", runResolver("resolver-case-question-marks.json"));
+  }
+
+  @Test
+  void checkDotLineGapTwoGroups() {
+    GapCase c = JSONUtils.fromJson(resource("gap-case-two-groups.json"), new TypeToken<>() {
+    });
+    PostTelegramTrainScoreVO scoreVO = new PostTelegramTrainScoreVO();
+    PostTelegramTrainStatisticsVO statisticsVO = new PostTelegramTrainStatisticsVO();
+    PostTelegramTrainRule rule = rule();
+    for (GapCall call : c.calls) {
+      TickerPatUtils.checkDotLineGap(call.patKey, call.i, JSONUtils.toJson(call.patLogs),
+          c.standards, rule, false, statisticsVO, scoreVO);
+    }
+    Map<String, Object> payload = new LinkedHashMap<>();
+    payload.put("scoreVO", scoreVO);
+    payload.put("statisticsVO", statisticsVO);
+    assertSnapshot("gap-two-groups", payload);
+  }
+}
diff --git a/src/test/resources/scoring/expected/gap-two-groups.json b/src/test/resources/scoring/expected/gap-two-groups.json
new file mode 100644
index 0000000..060bfe1
--- /dev/null
+++ b/src/test/resources/scoring/expected/gap-two-groups.json
@@ -0,0 +1,46 @@
+{
+  "scoreVO": {
+    "lack": 0,
+    "correct": 0,
+    "errorNumber": 0,
+    "moreGroup": 0,
+    "lackGroup": 0,
+    "bunchGroup": 0,
+    "moreOrLackWord": 0,
+    "moreOrLackLine": 0,
+    "patTotalNum": 0,
+    "dotScore": 11,
+    "lineScore": 10,
+    "codeScore": 1,
+    "wordScore": 21,
+    "groupScore": 1,
+    "alterErrorScore": 0,
+    "dotTotalTime": 650,
+    "lineTotalTime": 1250,
+    "codeTotalTime": 50,
+    "wordTotalTime": 1201,
+    "groupTotalTime": 300
+  },
+  "statisticsVO": {
+    "dotMinNumber": 1,
+    "dotMaxNumber": 1,
+    "dotPerfectNumber": 4,
+    "lineMinNumber": 0,
+    "lineMaxNumber": 1,
+    "linePerfectNumber": 3,
+    "codeMinNumber": 1,
+    "codeMaxNumber": 0,
+    "codePerfectNumber": 0,
+    "wordMinNumber": 1,
+    "wordMaxNumber": 2,
+    "wordPerfectNumber": 1,
+    "groupMinNumber": 1,
+    "groupMaxNumber": 0,
+    "groupPerfectNumber": 0,
+    "dotAvg": 0,
+    "lineAvg": 0,
+    "codeAvg": 0,
+    "wordAvg": 0,
+    "groupAvg": 0
+  }
+}
diff --git a/src/test/resources/scoring/expected/resolver-glued.json b/src/test/resources/scoring/expected/resolver-glued.json
new file mode 100644
index 0000000..6cd4933
--- /dev/null
+++ b/src/test/resources/scoring/expected/resolver-glued.json
@@ -0,0 +1,48 @@
+{
+  "resolverMessage": [
+    "3729",
+    "7201",
+    "U3YU",
+    "U3YU"
+  ],
+  "resolverPatLogs": [
+    "[[{\"key\":0,\"value\":101}],[{\"key\":0,\"value\":102}],[{\"key\":0,\"value\":103}],[{\"key\":0,\"value\":104}]]",
+    "[[{\"key\":0,\"value\":105}],[{\"key\":0,\"value\":106}],[{\"key\":0,\"value\":107}],[{\"key\":0,\"value\":108}]]",
+    "[[{\"key\":1,\"value\":201}],[{\"key\":1,\"value\":202}],[{\"key\":1,\"value\":203}],[{\"key\":1,\"value\":204}]]",
+    "[[{\"key\":1,\"value\":205}],[{\"key\":1,\"value\":206}],[{\"key\":1,\"value\":207}],[{\"key\":1,\"value\":208}]]"
+  ],
+  "resolverMoresTime": [
+    "[\"[1]\",\"[2]\",\"[3]\",\"[4]\"]",
+    "[\"[5]\",\"[6]\",\"[7]\",\"[8]\"]",
+    "[\"[11]\",\"[12]\",\"[13]\",\"[14]\"]",
+    "[\"[15]\",\"[16]\",\"[17]\",\"[18]\"]"
+  ],
+  "resolverMoresValue": [
+    "[\"[0]\",\"[1]\",\"[0]\",\"[1]\"]",
+    "[\"[0]\",\"[1]\",\"[0]\",\"[1]\"]",
+    "[\"[1]\",\"[0]\",\"[1]\",\"[0]\"]",
+    "[\"[1]\",\"[0]\",\"[1]\",\"[0]\"]"
+  ],
+  "scoreVO": {
+    "lack": 0,
+    "correct": 0,
+    "errorNumber": 0,
+    "moreGroup": 0,
+    "lackGroup": 0,
+    "bunchGroup": 0,
+    "moreOrLackWord": 0,
+    "moreOrLackLine": 0,
+    "patTotalNum": 0,
+    "dotScore": 0,
+    "lineScore": 0,
+    "codeScore": 0,
+    "wordScore": 0,
+    "groupScore": 2,
+    "alterErrorScore": 0,
+    "dotTotalTime": 0,
+    "lineTotalTime": 0,
+    "codeTotalTime": 0,
+    "wordTotalTime": 0,
+    "groupTotalTime": 0
+  }
+}
diff --git a/src/test/resources/scoring/expected/resolver-normal-with-blank.json b/src/test/resources/scoring/expected/resolver-normal-with-blank.json
new file mode 100644
index 0000000..7c1591a
--- /dev/null
+++ b/src/test/resources/scoring/expected/resolver-normal-with-blank.json
@@ -0,0 +1,40 @@
+{
+  "resolverMessage": [
+    "3729",
+    "7201"
+  ],
+  "resolverPatLogs": [
+    "[[{\"key\":0,\"value\":100}]]",
+    "[]"
+  ],
+  "resolverMoresTime": [
+    "[[1,0]]",
+    "[]"
+  ],
+  "resolverMoresValue": [
+    "[[11,12]]",
+    "[]"
+  ],
+  "scoreVO": {
+    "lack": 0,
+    "correct": 0,
+    "errorNumber": 0,
+    "moreGroup": 0,
+    "lackGroup": 0,
+    "bunchGroup": 0,
+    "moreOrLackWord": 0,
+    "moreOrLackLine": 0,
+    "patTotalNum": 0,
+    "dotScore": 0,
+    "lineScore": 0,
+    "codeScore": 0,
+    "wordScore": 0,
+    "groupScore": 0,
+    "alterErrorScore": 0,
+    "dotTotalTime": 0,
+    "lineTotalTime": 0,
+    "codeTotalTime": 0,
+    "wordTotalTime": 0,
+    "groupTotalTime": 0
+  }
+}
diff --git a/src/test/resources/scoring/expected/resolver-question-marks.json b/src/test/resources/scoring/expected/resolver-question-marks.json
new file mode 100644
index 0000000..9a106de
--- /dev/null
+++ b/src/test/resources/scoring/expected/resolver-question-marks.json
@@ -0,0 +1,50 @@
+{
+  "resolverMessage": [
+    "3729",
+    "?",
+    "7201",
+    "201"
+  ],
+  "resolverPatLogs": [
+    "[[{\"key\":0,\"value\":100}]]",
+    "[[]]",
+    "[[{\"key\":1,\"value\":300}]]",
+    "[[{\"key\":0,\"value\":42}],[{\"key\":0,\"value\":43}],[{\"key\":0,\"value\":44}]]"
+  ],
+  "resolverMoresTime": [
+    "[[0]]",
+    "[[92]]",
+    "[[1]]",
+    "[\"[42]\",\"[43]\",\"[44]\"]",
+    "[[2]]"
+  ],
+  "resolverMoresValue": [
+    "[[10]]",
+    "[[91]]",
+    "[[20]]",
+    "[\"[5]\",\"[6]\",\"[7]\"]",
+    "[[30]]"
+  ],
+  "scoreVO": {
+    "lack": 0,
+    "correct": 0,
+    "errorNumber": 0,
+    "moreGroup": 0,
+    "lackGroup": 0,
+    "bunchGroup": 0,
+    "moreOrLackWord": 0,
+    "moreOrLackLine": 0,
+    "patTotalNum": 0,
+    "dotScore": 0,
+    "lineScore": 0,
+    "codeScore": 0,
+    "wordScore": 0,
+    "groupScore": 0,
+    "alterErrorScore": 3,
+    "dotTotalTime": 0,
+    "lineTotalTime": 0,
+    "codeTotalTime": 0,
+    "wordTotalTime": 0,
+    "groupTotalTime": 0
+  }
+}
diff --git a/src/test/resources/scoring/gap-case-two-groups.json b/src/test/resources/scoring/gap-case-two-groups.json
new file mode 100644
index 0000000..92d5209
--- /dev/null
+++ b/src/test/resources/scoring/gap-case-two-groups.json
@@ -0,0 +1,28 @@
+{
+  "comment": "checkDotLineGap 两次调用（i=0 组 3729、i=1 组 7201）。标准值/日志按 PatLogs schema 构造：key 0=点 1=划 2=间隔；规则容差来自真实 t_grading_rule type=0（dot±30 dash±50 little±40 middle±60 large±90）。覆盖点细/点粗/划粗/词间隔细·粗·完美/码间隔细/组间隔细。",
+  "standards": [
+    {"dot": 100, "line": 300, "codeGap": 100, "wordGap": 200, "groupGap": 400, "offSize": 0}
+  ],
+  "calls": [
+    {
+      "patKey": "3729",
+      "i": 0,
+      "patLogs": [
+        [{"key": 2, "value": 400}, {"key": 0, "value": 50}, {"key": 0, "value": 100}, {"key": 1, "value": 400}],
+        [{"key": 2, "value": 100}, {"key": 1, "value": 300}],
+        [{"key": 2, "value": 200}, {"key": 2, "value": 50}, {"key": 0, "value": 200}],
+        [{"key": 2, "value": 300}, {"key": 0, "value": 100}]
+      ]
+    },
+    {
+      "patKey": "7201",
+      "i": 1,
+      "patLogs": [
+        [{"key": 2, "value": 300}, {"key": 0, "value": 100}],
+        [{"key": 2, "value": 200}, {"key": 1, "value": 300}],
+        [{"key": 2, "value": 140}, {"key": 0, "value": 100}],
+        [{"key": 2, "value": 261}, {"key": 1, "value": 250}]
+      ]
+    }
+  ]
+}
diff --git a/src/test/resources/scoring/grading-rule-type0.json b/src/test/resources/scoring/grading-rule-type0.json
new file mode 100644
index 0000000..b639135
--- /dev/null
+++ b/src/test/resources/scoring/grading-rule-type0.json
@@ -0,0 +1 @@
+{"wpm":{"base":70,"r":1,"l":1,"type":false},"skew":51,"code":{"dot":{"base":30,"l":1,"r":10,"max":1},"dash":{"base":50,"l":1,"r":10,"max":1}},"gap":{"little":{"base":40,"l":1,"r":10,"max":1},"middle":{"base":60,"l":1,"r":10,"max":1},"large":{"base":90,"l":1,"r":10,"max":1}},"other":{"errorCode":{"l":1,"max":1},"quantoCode":{"l":1,"max":1},"quantoGroup":{"l":1,"max":1},"alterError":{"l":1,"max":1},"quantoRow":{"l":1,"max":1},"bunchGroup":{"l":1,"max":1}},"scale":{"dot":1,"dash":3,"little":1,"middle":3,"large":5}}
diff --git a/src/test/resources/scoring/resolver-case-glued.json b/src/test/resources/scoring/resolver-case-glued.json
new file mode 100644
index 0000000..41b293c
--- /dev/null
+++ b/src/test/resources/scoring/resolver-case-glued.json
@@ -0,0 +1,16 @@
+{
+  "comment": "两个 8 位粘连组（真实字码 3729/7201/U3YU 拼接），锁定 4 位拆分与 groupScore 的累计行为。",
+  "patKeys": ["37297201", "U3YUU3YU"],
+  "userContents": [
+    {
+      "patLogs": "[[{\"key\":0,\"value\":101}],[{\"key\":0,\"value\":102}],[{\"key\":0,\"value\":103}],[{\"key\":0,\"value\":104}],[{\"key\":0,\"value\":105}],[{\"key\":0,\"value\":106}],[{\"key\":0,\"value\":107}],[{\"key\":0,\"value\":108}]]",
+      "moresTime": "[[1],[2],[3],[4],[5],[6],[7],[8]]",
+      "moresValue": "[[0],[1],[0],[1],[0],[1],[0],[1]]"
+    },
+    {
+      "patLogs": "[[{\"key\":1,\"value\":201}],[{\"key\":1,\"value\":202}],[{\"key\":1,\"value\":203}],[{\"key\":1,\"value\":204}],[{\"key\":1,\"value\":205}],[{\"key\":1,\"value\":206}],[{\"key\":1,\"value\":207}],[{\"key\":1,\"value\":208}]]",
+      "moresTime": "[[11],[12],[13],[14],[15],[16],[17],[18]]",
+      "moresValue": "[[1],[0],[1],[0],[1],[0],[1],[0]]"
+    }
+  ]
+}
diff --git a/src/test/resources/scoring/resolver-case-normal-with-blank.json b/src/test/resources/scoring/resolver-case-normal-with-blank.json
new file mode 100644
index 0000000..69c3004
--- /dev/null
+++ b/src/test/resources/scoring/resolver-case-normal-with-blank.json
@@ -0,0 +1,9 @@
+{
+  "comment": "patKeys 取自 docs/database/project006.sql t_cable_floor 真实字码组（3729/7201）；中间夹一个空白组，锁定 patKeys 过滤后 userContents 的配对行为。库内无用户拍发数据，patLogs/moresTime/moresValue 为按实体 schema 构造的样本。",
+  "patKeys": ["3729", "", "7201"],
+  "userContents": [
+    {"patLogs": "[[{\"key\":0,\"value\":100}]]", "moresTime": "[[11,12]]", "moresValue": "[[1,0]]"},
+    {"patLogs": "[]", "moresTime": "[]", "moresValue": "[]"},
+    {"patLogs": "[[{\"key\":1,\"value\":300}]]", "moresTime": "[[21,22]]", "moresValue": "[[0,1]]"}
+  ]
+}
diff --git a/src/test/resources/scoring/resolver-case-question-marks.json b/src/test/resources/scoring/resolver-case-question-marks.json
new file mode 100644
index 0000000..740e056
--- /dev/null
+++ b/src/test/resources/scoring/resolver-case-question-marks.json
@@ -0,0 +1,11 @@
+{
+  "comment": "改错符号（?）三种形态：单独 ?、组尾 372?、组首 ?201，锁定改错分支的列写入与列表长度行为。",
+  "patKeys": ["3729", "?", "7201", "372?", "?201"],
+  "userContents": [
+    {"patLogs": "[[{\"key\":0,\"value\":100}]]", "moresTime": "[[10]]", "moresValue": "[[0]]"},
+    {"patLogs": "[[]]", "moresTime": "[[91]]", "moresValue": "[[92]]"},
+    {"patLogs": "[[{\"key\":1,\"value\":300}]]", "moresTime": "[[20]]", "moresValue": "[[1]]"},
+    {"patLogs": "[[]]", "moresTime": "[[30]]", "moresValue": "[[2]]"},
+    {"patLogs": "[[{\"key\":0,\"value\":41}],[{\"key\":0,\"value\":42}],[{\"key\":0,\"value\":43}],[{\"key\":0,\"value\":44}]]", "moresTime": "[[41],[42],[43],[44]]", "moresValue": "[[4],[5],[6],[7]]"}
+  ]
+}


---
commit 1fae6cfd3e0e9841b4fb7bb94bfa31545ec9dd31
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 04:00:39 2026 +0800

    fix(scoring-2): 修确定性缺陷——列对调/列表错位/过滤不同步/groupScore覆盖/词完美串号 + P1-01/02/06/20/21

diff --git a/src/main/java/com/nip/common/utils/TickerPatUtils.java b/src/main/java/com/nip/common/utils/TickerPatUtils.java
index 4006477..40cc130 100644
--- a/src/main/java/com/nip/common/utils/TickerPatUtils.java
+++ b/src/main/java/com/nip/common/utils/TickerPatUtils.java
@@ -43,22 +43,29 @@ public class TickerPatUtils {
       resolverVO.setResolverMoresValue(resolverMoresValue);
       return resolverVO;
     }
 
     if (userContents == null) {
       userContents = new ArrayList<>();
     }
 
-    patKeys = patKeys.stream().filter(StringUtils::isNotBlank).toList();
-
-    // 确保userContents大小足够
-    while (userContents.size() < patKeys.size()) {
-      userContents.add(new PostTelegramTrainContentAddParam());
+    // 过滤空白组时同步过滤对应的 userContents，保持 patKeys[i] 与 userContents[i] 配对（P3 Task 3.2：过滤不同步）
+    List<String> filteredKeys = new ArrayList<>();
+    List<PostTelegramTrainContentAddParam> filteredContents = new ArrayList<>();
+    for (int i = 0; i < patKeys.size(); i++) {
+      if (StringUtils.isNotBlank(patKeys.get(i))) {
+        filteredKeys.add(patKeys.get(i));
+        filteredContents.add(i < userContents.size()
+            ? userContents.get(i)
+            : new PostTelegramTrainContentAddParam());
+      }
     }
+    patKeys = filteredKeys;
+    userContents = filteredContents;
 
     for (int i = 0; i < patKeys.size(); i++) {
       PostTelegramTrainContentAddParam contentAddParam = userContents.get(i);
 
       // 安全解析JSON，添加空值检查
       List<List<Map<String, Object>>> patLogs = null;
       List<List<Integer>> moresTime = null;
       List<List<Integer>> moresValue = null;
@@ -108,17 +115,17 @@ public class TickerPatUtils {
             }
           }
           ret.add(substring);
           resolverPatLogs.add(JSONUtils.toJson(logs));
           resolverMoresTime.add(JSONUtils.toJson(times));
           resolverMoresValue.add(JSONUtils.toJson(values));
         }
 
-        scoreVO.setGroupScore(patKey.length() / (2 + 1) * rule.getLarge().getL());
+        scoreVO.setGroupScore(scoreVO.getGroupScore() + patKey.length() / (2 + 1) * rule.getLarge().getL());
 
       } else if (Objects.equals(patKey, "?")) {
         // 拿到上一组和下一组
         if (i - 1 > 0 && i + 1 < patKeys.size()) {
           String nextPatKey = patKeys.get(i + 1);
           PostTelegramTrainContentAddParam nextAddParam = userContents.get(i + 1);
           String nextPatLogs = nextAddParam.getPatLogs();
           String nextMoresTime = nextAddParam.getMoresTime();
@@ -130,18 +137,18 @@ public class TickerPatUtils {
           // 交换morestime
           resolverMoresTime.set(resolverMoresTime.size() - 1, nextMoresTime);
           // 交换moresValue
           resolverMoresValue.set(resolverMoresValue.size() - 1, nextMoresValue);
           i = i + 1;
         } else {
           ret.add(patKey);
           resolverPatLogs.add(contentAddParam.getPatLogs());
-          resolverMoresValue.add(contentAddParam.getMoresTime());
-          resolverMoresTime.add(contentAddParam.getMoresValue());
+          resolverMoresTime.add(contentAddParam.getMoresTime());
+          resolverMoresValue.add(contentAddParam.getMoresValue());
         }
       } else if (patKey.contains("?")) {
         int index = patKey.lastIndexOf("?") + 1;
         // 判断 ？在前还是在后
         if (patKey.startsWith("?") && i - 1 >= 0) {
           // 获取前一组
           String substring = patKey.substring(index);
           List<List<Map<String, Object>>> newPatLogs = new ArrayList<>();
@@ -161,18 +168,18 @@ public class TickerPatUtils {
             List<Integer> value = null;
             if (moresValue != null && index + z < moresValue.size()) {
               value = moresValue.get(index + z);
             }
             newValues.add(JSONUtils.toJson(value));
           }
           ret.set(ret.size() - 1, substring);
           resolverPatLogs.set(resolverPatLogs.size() - 1, JSONUtils.toJson(newPatLogs));
-          resolverMoresTime.add(resolverMoresTime.size() - 1, JSONUtils.toJson(newTimes));
-          resolverMoresValue.add(resolverMoresValue.size() - 1, JSONUtils.toJson(newValues));
+          resolverMoresTime.set(resolverMoresTime.size() - 1, JSONUtils.toJson(newTimes));
+          resolverMoresValue.set(resolverMoresValue.size() - 1, JSONUtils.toJson(newValues));
 
         } else if (patKey.endsWith("?") && patKeys.size() > i + 1) {
           scoreVO.setAlterErrorScore(scoreVO.getAlterErrorScore() + rule.getAlterError().getL());
           int first = patKey.indexOf("?");
           int last = patKey.lastIndexOf("?");
           String between = (first >= 0 && last > first + 1) ? patKey.substring(first + 1, last) : "";
           if (!between.isEmpty()) {
             List<List<Map<String, Object>>> newPatLogs = new ArrayList<>();
@@ -197,18 +204,18 @@ public class TickerPatUtils {
             }
             ret.add(between);
             resolverPatLogs.add(JSONUtils.toJson(newPatLogs));
             resolverMoresTime.add(JSONUtils.toJson(newTimes));
             resolverMoresValue.add(JSONUtils.toJson(newValues));
           } else {
             ret.add(patKey);
             resolverPatLogs.add(contentAddParam.getPatLogs());
-            resolverMoresValue.add(contentAddParam.getMoresTime());
-            resolverMoresTime.add(contentAddParam.getMoresValue());
+            resolverMoresTime.add(contentAddParam.getMoresTime());
+            resolverMoresValue.add(contentAddParam.getMoresValue());
           }
         } else {
           String substring = patKey.substring(index);
           List<List<Map<String, Object>>> newPatLogs = new ArrayList<>();
           List<String> newTimes = new ArrayList<>();
           List<String> newValues = new ArrayList<>();
           if (!substring.isEmpty()) {
             ret.add(substring);
@@ -231,26 +238,26 @@ public class TickerPatUtils {
             }
             resolverPatLogs.add(JSONUtils.toJson(newPatLogs));
             resolverMoresTime.add(JSONUtils.toJson(newTimes));
             resolverMoresValue.add(JSONUtils.toJson(newValues));
 
           } else {
             ret.add(patKey);
             resolverPatLogs.add(contentAddParam.getPatLogs());
-            resolverMoresValue.add(contentAddParam.getMoresTime());
-            resolverMoresTime.add(contentAddParam.getMoresValue());
+            resolverMoresTime.add(contentAddParam.getMoresTime());
+            resolverMoresValue.add(contentAddParam.getMoresValue());
           }
         }
         scoreVO.setAlterErrorScore(scoreVO.getAlterErrorScore() + rule.getAlterError().getL());
       } else if (!patKey.isEmpty()) {
         ret.add(patKey);
         resolverPatLogs.add(contentAddParam.getPatLogs() != null ? contentAddParam.getPatLogs() : "[]");
-        resolverMoresValue.add(contentAddParam.getMoresTime() != null ? contentAddParam.getMoresTime() : "[]");
-        resolverMoresTime.add(contentAddParam.getMoresValue() != null ? contentAddParam.getMoresValue() : "[]");
+        resolverMoresTime.add(contentAddParam.getMoresTime() != null ? contentAddParam.getMoresTime() : "[]");
+        resolverMoresValue.add(contentAddParam.getMoresValue() != null ? contentAddParam.getMoresValue() : "[]");
       } else {
         // 处理空字符串情况
         ret.add("");
         resolverPatLogs.add("[]");
         resolverMoresValue.add("[]");
         resolverMoresTime.add("[]");
       }
     }
@@ -636,17 +643,17 @@ public class TickerPatUtils {
               // 细
               scoreVO.setWordScore(scoreVO.getWordScore() + (isDuct ? 0 : rule.getMiddle().getL()));
               statisticsVO.setWordMinNumber(statisticsVO.getWordMinNumber() + 1);
             } else if (value > wordGapMax) {
               // 粗
               scoreVO.setWordScore(scoreVO.getWordScore() + (isDuct ? 0 : rule.getMiddle().getR()));
               statisticsVO.setWordMaxNumber(statisticsVO.getWordMaxNumber() + 1);
             } else {
-              statisticsVO.setWordPerfectNumber(statisticsVO.getCodePerfectNumber() + 1);
+              statisticsVO.setWordPerfectNumber(statisticsVO.getWordPerfectNumber() + 1);
             }
             scoreVO.setWordTotalTime(scoreVO.getWordTotalTime() + value);
           }
           // 码间隔
           else {
             if (value < codeGapMin) {
               // 细
               scoreVO.setCodeScore(scoreVO.getCodeScore() + (isDuct ? 0 : rule.getLittle().getL()));
diff --git a/src/main/java/com/nip/service/PostTelegramTrainService.java b/src/main/java/com/nip/service/PostTelegramTrainService.java
index 6610c55..65c204e 100644
--- a/src/main/java/com/nip/service/PostTelegramTrainService.java
+++ b/src/main/java/com/nip/service/PostTelegramTrainService.java
@@ -692,26 +692,26 @@ public class PostTelegramTrainService {
       if (i != existPageNumber.size() - 1) {
         scoreVO.setLackGroup(scoreVO.getLackGroup() + 100);
       } else {
         scoreVO.setLackGroup(scoreVO.getLackGroup() + messageNumber - ((totalFloorNumber - 1) * 100));
       }
     }
   }
 
-  private int applyDeductions(Integer baseScore, PostTelegramTrainScoreVO scoreVO, PostTelegramTrainRule rule,
+  static int applyDeductions(Integer baseScore, PostTelegramTrainScoreVO scoreVO, PostTelegramTrainRule rule,
       Map<String, Integer> deductMap) {
     int score = baseScore;
 
     int dotScore = calculateScore(rule.getDot().getMax(), scoreVO.getDotScore(), rule.getDot().getMax());
     score -= dotScore;
     deductMap.put("dotMinScore", dotScore);
     deductMap.put("dotMinNumber", scoreVO.getDotScore());
 
-    int lineScore = calculateScore(rule.getDash().getMax(), scoreVO.getLineScore(), rule.getDot().getMax());
+    int lineScore = calculateScore(rule.getDash().getMax(), scoreVO.getLineScore(), rule.getDash().getMax());
     score -= lineScore;
     deductMap.put("lineScore", lineScore);
     deductMap.put("lineNumber", scoreVO.getLineScore());
 
     int codeScore = calculateScore(rule.getLittle().getMax(), scoreVO.getCodeScore(), rule.getLittle().getMax());
     score -= codeScore;
     deductMap.put("codeGapScore", codeScore);
     deductMap.put("codeNumber", scoreVO.getCodeScore());
@@ -761,36 +761,39 @@ public class PostTelegramTrainService {
         scoreVO.getBunchGroup() * rule.getBunchGroup().getL(), rule.getBunchGroup().getMax());
     score -= bunchGroup;
     deductMap.put("bunchGroup", bunchGroup);
     deductMap.put("bunchGroupNumber", scoreVO.getBunchGroup());
 
     return score;
   }
 
-  private void saveTrainResult(PostTelegramTrainEntity entity, PostTelegramTrainScoreVO scoreVO,
+  static void saveTrainResult(PostTelegramTrainEntity entity, PostTelegramTrainScoreVO scoreVO,
       int score, PostTelegramTrainStatisticsVO statisticsVO, Map<String, Integer> deductMap, PostTelegramTrainRule rule,
       PostTelegramTrainFinishDto dto) {
     entity.setErrorNumber(scoreVO.getErrorNumber());
     entity.setLack(scoreVO.getLackGroup());
 
     if (scoreVO.getCorrect() == 0) {
       entity.setAccuracy("0.00");
     } else {
       String accuracy = new BigDecimal(scoreVO.getCorrect())
           .divide(new BigDecimal(scoreVO.getPatTotalNum()), 2, RoundingMode.HALF_UP)
           .multiply(new BigDecimal(100)).toString();
       entity.setAccuracy(accuracy);
     }
 
     entity.setSpeed(dto.getSpeed());
 
+    // 速率加减分：高于基准加分（l=高于加分）、低于基准扣分（r=低于扣分），与 SpeedDeduct 字段语义及其余训练一致（P1-02）
     SpeedDeduct baseWpm = rule.getWpm();
-    int wpm = baseWpm.getBase() - new BigDecimal(entity.getSpeed()).intValue();
-    int wpmScore = (wpm > 0 ? -(wpm * baseWpm.getL()) : wpm * baseWpm.getR());
+    int speed = new BigDecimal(entity.getSpeed()).intValue();
+    int wpmScore = speed > baseWpm.getBase()
+        ? (speed - baseWpm.getBase()) * baseWpm.getL()
+        : -((baseWpm.getBase() - speed) * baseWpm.getR());
     deductMap.put("wpmScore", wpmScore);
     score += wpmScore;
 
     entity.setScore(String.valueOf(score));
     entity.setStatisticInfo(JSONUtils.toJson(statisticsVO));
     entity.setDeductInfo(JSONUtils.toJson(deductMap));
   }
 
diff --git a/src/main/java/com/nip/service/PostTelexPatTrainService.java b/src/main/java/com/nip/service/PostTelexPatTrainService.java
index 4a81df4..ac67120 100644
--- a/src/main/java/com/nip/service/PostTelexPatTrainService.java
+++ b/src/main/java/com/nip/service/PostTelexPatTrainService.java
@@ -303,16 +303,39 @@ public class PostTelexPatTrainService {
 
   @Transactional
   public Boolean delete(String trainId) {
     pageDao.delete("trainId", trainId);
     valueDao.delete("trainId", trainId);
     return postTelexPatTrainDao.deleteById(trainId);
   }
 
+  /**
+   * 规整相邻的不规组：三五码 234 56789 -> 2345 6789；五三码 23456 789 -> 2345 6789。
+   * 语义与 convertCodeAll 中的同名处理一致（P1-21）。
+   *
+   * @return 规整次数
+   */
+  static int normalizeAdjacentGroups(String[] groups) {
+    int count = 0;
+    for (int i = 0; i < groups.length; i++) {
+      if (groups[i].length() == 3 && groups.length > (i + 1) && groups[i + 1].length() == 5) {
+        String nextGroup = groups[i + 1];
+        groups[i] = groups[i] + nextGroup.charAt(0);
+        groups[i + 1] = nextGroup.substring(1);
+        count++;
+      } else if (groups[i].length() == 5 && groups.length > (i + 1) && groups[i + 1].length() == 3) {
+        groups[i + 1] = groups[i].charAt(groups[i].length() - 1) + groups[i + 1];
+        groups[i] = groups[i].substring(0, groups[i].length() - 1);
+        count++;
+      }
+    }
+    return count;
+  }
+
   /**
    * 计算得分
    *
    * @param param
    * @return
    */
   private PostTelexPatTrainEntity countScore(PostTelexPatTrainFinishParam param, PostTelexPatTrainEntity entity) {
     if (entity.getTrainType().compareTo(4) == 0) {
@@ -381,30 +404,18 @@ public class PostTelexPatTrainService {
           List<List<String>> pageList = new ArrayList<>();
           for (String s : row) {
             if (StringUtils.isBlank(s)) {
               pageList.add(new ArrayList<>());
               continue;
             }
             // 每组间用空格 分
             String[] groups = s.split(" ");
-            // 处理不规
-            for (int i = 0; i < groups.length; i++) {
-              if (groups[i].length() == 3 && groups.length > (i + 1) && groups[i + 1].length() == 5) {
-                String nextGroup = groups[i + 1];
-                groups[i] = groups[i] + nextGroup.charAt(0);
-                groups[i + 1] = nextGroup.substring(1);
-                nonStandartNumber += 1;
-              } else if (groups[i].length() == 5 && groups.length > (i + 1) && groups[i + 1].length() == 3) {
-                String nextGroup = groups[i + 1];
-                groups[i] = groups[i].substring(0, groups[i].length() - 1);
-                groups[i + 1] = nextGroup + groups[i].charAt(groups[i].length() - 1);
-                nonStandartNumber += 1;
-              }
-            }
+            // 处理不规（三五码/五三码 规整）
+            nonStandartNumber += normalizeAdjacentGroups(groups);
             pageList.add(Arrays.stream(groups).toList());
           }
           if (!pageList.isEmpty()) {
             parseCodeAll.add(pageList);
           }
         }
       }
 
@@ -842,21 +853,22 @@ public class PostTelexPatTrainService {
       BigDecimal alterError = rule.getOther().getAlterError();
       BigDecimal correctMistakesScore = new BigDecimal(ks.getCorrectMistakesNumber())
           .multiply(null == alterError ? rule.getOther().getCorrectMistakes() : alterError);
       deductMap.put("correctMistakesNumber", ks.getCorrectMistakesNumber());
       deductMap.put("correctMistakesScore", minus + correctMistakesScore);
 
       // 计算正确率 （拍发总个数- 错误个数 = 正确个数） / 总个数
       BigDecimal accuracy = new BigDecimal("0");
-      int errorTotal = ks.getPatGroup() - ks.getErrorCodeNumber() - ks.getMuchLessCodeNumber();
-      entity.setErrorNumber(errorTotal);
-      if (errorTotal != 0) {
+      // 正确组数 =（拍发总组数 - 错码组 - 多少码组）；errorNumber 落库真实错误计数（P1-20）
+      int correctTotal = ks.getPatGroup() - ks.getErrorCodeNumber() - ks.getMuchLessCodeNumber();
+      entity.setErrorNumber(ks.getErrorCodeNumber() + ks.getMuchLessCodeNumber());
+      if (correctTotal != 0) {
         // 计算正确率 （拍发总个数 - 错误个数- 多字- 少字)） /拍发总个数
-        accuracy = new BigDecimal(errorTotal).divide(
+        accuracy = new BigDecimal(correctTotal).divide(
             new BigDecimal(ks.getPatGroup()), 2, RoundingMode.HALF_UP).multiply(new BigDecimal(100));
       }
 
       score = score.subtract(errorCodeScore)
           .subtract(muchLessLineScore)
           .subtract(muchLessGroupsScore)
           .subtract(muchLessCodeScore)
           .subtract(lessReturnLineScore)
diff --git a/src/main/java/com/nip/service/detector/ErrorCodeDetector.java b/src/main/java/com/nip/service/detector/ErrorCodeDetector.java
index f1dc3e3..00f68e4 100644
--- a/src/main/java/com/nip/service/detector/ErrorCodeDetector.java
+++ b/src/main/java/com/nip/service/detector/ErrorCodeDetector.java
@@ -164,20 +164,20 @@ public class ErrorCodeDetector {
 
   /**
    * 处理字间隔过小
    */
   private void handleWordGapSmall(ComparisonContext context) {
     PostTelegramTrainScoreVO scoreVO = context.getScoreVO();
     PostTelegramTrainRule rule = context.getRule();
 
-    // 根据评分规则扣分
-    scoreVO.setWordScore(scoreVO.getWordScore() + rule.getLarge().getL());
+    // 字间隔 word ↔ rule.getMiddle()（组间隔才是 large）；上限在 applyDeductions 按 middle.max 裁剪
+    scoreVO.setWordScore(scoreVO.getWordScore() + rule.getMiddle().getL());
 
-    log.debug("检测到字间隔过小，扣分: {}", rule.getLarge().getL());
+    log.debug("检测到字间隔过小，扣分: {}", rule.getMiddle().getL());
   }
 
   /**
    * 处理字间隔过大
    */
   private void handleWordGapLarge(ComparisonContext context,
       int currentIndex,
       String patKey1,
@@ -185,18 +185,18 @@ public class ErrorCodeDetector {
       MessageResultBuilder resultBuilder) {
 
     PostTelegramTrainScoreVO scoreVO = context.getScoreVO();
     PostTelegramTrainRule rule = context.getRule();
     List<String> resolverCorrectPatLogs = context.getResolverVO().getResolverPatLogs();
     List<String> resolverCorrectMoresValue = context.getResolverVO().getResolverMoresValue();
     List<String> resolverCorrectMoresTime = context.getResolverVO().getResolverMoresTime();
 
-    // 根据评分规则扣分
-    scoreVO.setWordScore(scoreVO.getWordScore() + rule.getLarge().getR());
+    // 字间隔 word ↔ rule.getMiddle()（组间隔才是 large）
+    scoreVO.setWordScore(scoreVO.getWordScore() + rule.getMiddle().getR());
 
     // 合并两组数据
     String patLog1 = resolverCorrectPatLogs.get(currentIndex);
     String patLog2 = resolverCorrectPatLogs.get(currentIndex + 1);
     String moresValue1 = resolverCorrectMoresValue.get(currentIndex);
     String moresValue2 = resolverCorrectMoresValue.get(currentIndex + 1);
     String moresTime1 = resolverCorrectMoresTime.get(currentIndex);
     String moresTime2 = resolverCorrectMoresTime.get(currentIndex + 1);
diff --git a/src/test/java/com/nip/service/PostTelegramTrainScoreTest.java b/src/test/java/com/nip/service/PostTelegramTrainScoreTest.java
new file mode 100644
index 0000000..5e2e522
--- /dev/null
+++ b/src/test/java/com/nip/service/PostTelegramTrainScoreTest.java
@@ -0,0 +1,68 @@
+package com.nip.service;
+
+import com.nip.dto.PostTelegramTrainFinishDto;
+import com.nip.dto.score.PostTelegramTrainRule;
+import com.nip.dto.vo.PostTelegramTrainScoreVO;
+import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
+import com.nip.entity.PostTelegramTrainEntity;
+import org.junit.jupiter.api.Test;
+
+import java.util.HashMap;
+import java.util.Map;
+
+import static com.nip.common.utils.TickerPatUtils.parseContent;
+import static org.junit.jupiter.api.Assertions.assertEquals;
+
+/**
+ * Task 3.2：手键拍发评分的确定性缺陷。
+ * P1-06：划扣分封顶误用点的 max；P1-02：速率加减分系数用反且超速被扣分。
+ */
+class PostTelegramTrainScoreTest {
+
+  /** 基于 t_grading_rule type=0 真实结构，dash.max 调成与 dot.max 不同以暴露 P1-06。 */
+  private static final String RULE_JSON = """
+      {"wpm":{"base":70,"r":2,"l":1,"type":false},"skew":51,
+       "code":{"dot":{"base":30,"l":1,"r":10,"max":1},"dash":{"base":50,"l":1,"r":10,"max":5}},
+       "gap":{"little":{"base":40,"l":1,"r":10,"max":1},"middle":{"base":60,"l":1,"r":10,"max":1},
+              "large":{"base":90,"l":1,"r":10,"max":1}},
+       "other":{"errorCode":{"l":1,"max":1},"quantoCode":{"l":1,"max":1},"quantoGroup":{"l":1,"max":1},
+                "alterError":{"l":1,"max":1},"quantoRow":{"l":1,"max":1},"bunchGroup":{"l":1,"max":1}}}""";
+
+  @Test
+  void dashDeductionIsCappedByDashMaxNotDotMax() {
+    PostTelegramTrainRule rule = parseContent(RULE_JSON);
+    PostTelegramTrainScoreVO scoreVO = new PostTelegramTrainScoreVO();
+    scoreVO.setLineScore(7); // 超过 dash.max=5，封顶后应扣 5 而不是 dot.max=1
+    Map<String, Integer> deductMap = new HashMap<>();
+
+    int score = PostTelegramTrainService.applyDeductions(100, scoreVO, rule, deductMap);
+
+    assertEquals(5, deductMap.get("lineScore"), "划扣分封顶值必须取 dash.max");
+    assertEquals(95, score);
+  }
+
+  @Test
+  void speedAboveBaseAddsScoreWithLCoefficient() {
+    Map<String, Integer> deductMap = new HashMap<>();
+    saveResultWithSpeed("100", deductMap); // base=70，高于 30，l=1 → +30
+
+    assertEquals(30, deductMap.get("wpmScore"), "高于基准必须按 l 加分");
+  }
+
+  @Test
+  void speedBelowBaseDeductsWithRCoefficient() {
+    Map<String, Integer> deductMap = new HashMap<>();
+    saveResultWithSpeed("60", deductMap); // base=70，低于 10，r=2 → -20
+
+    assertEquals(-20, deductMap.get("wpmScore"), "低于基准必须按 r 扣分");
+  }
+
+  private static void saveResultWithSpeed(String speed, Map<String, Integer> deductMap) {
+    PostTelegramTrainRule rule = parseContent(RULE_JSON);
+    PostTelegramTrainEntity entity = new PostTelegramTrainEntity();
+    PostTelegramTrainFinishDto dto = new PostTelegramTrainFinishDto();
+    dto.setSpeed(speed);
+    PostTelegramTrainService.saveTrainResult(entity, new PostTelegramTrainScoreVO(), 100,
+        new PostTelegramTrainStatisticsVO(), deductMap, rule, dto);
+  }
+}
diff --git a/src/test/java/com/nip/service/PostTelexPatTrainScoreTest.java b/src/test/java/com/nip/service/PostTelexPatTrainScoreTest.java
new file mode 100644
index 0000000..05cb22f
--- /dev/null
+++ b/src/test/java/com/nip/service/PostTelexPatTrainScoreTest.java
@@ -0,0 +1,37 @@
+package com.nip.service;
+
+import org.junit.jupiter.api.Test;
+
+import static org.junit.jupiter.api.Assertions.assertArrayEquals;
+import static org.junit.jupiter.api.Assertions.assertEquals;
+
+/**
+ * Task 3.2 P1-21：countScore 的五三码规整必须与 convertCodeAll 同语义：
+ * "23456 789" -> "2345 6789"（旧实现产出 "2345 7895"，丢 6 多 5 顺序反）。
+ */
+class PostTelexPatTrainScoreTest {
+
+  @Test
+  void fiveThreeNormalizationMovesLastCharToNextGroupHead() {
+    String[] groups = {"23456", "789"};
+    int count = PostTelexPatTrainService.normalizeAdjacentGroups(groups);
+    assertArrayEquals(new String[]{"2345", "6789"}, groups);
+    assertEquals(1, count);
+  }
+
+  @Test
+  void threeFiveNormalizationMovesNextGroupHeadToTail() {
+    String[] groups = {"234", "56789"};
+    int count = PostTelexPatTrainService.normalizeAdjacentGroups(groups);
+    assertArrayEquals(new String[]{"2345", "6789"}, groups);
+    assertEquals(1, count);
+  }
+
+  @Test
+  void regularGroupsUntouched() {
+    String[] groups = {"2345", "6789"};
+    int count = PostTelexPatTrainService.normalizeAdjacentGroups(groups);
+    assertArrayEquals(new String[]{"2345", "6789"}, groups);
+    assertEquals(0, count);
+  }
+}
diff --git a/src/test/resources/scoring/expected/gap-two-groups.json b/src/test/resources/scoring/expected/gap-two-groups.json
index 060bfe1..5323203 100644
--- a/src/test/resources/scoring/expected/gap-two-groups.json
+++ b/src/test/resources/scoring/expected/gap-two-groups.json
@@ -28,17 +28,17 @@
     "lineMinNumber": 0,
     "lineMaxNumber": 1,
     "linePerfectNumber": 3,
     "codeMinNumber": 1,
     "codeMaxNumber": 0,
     "codePerfectNumber": 0,
     "wordMinNumber": 1,
     "wordMaxNumber": 2,
-    "wordPerfectNumber": 1,
+    "wordPerfectNumber": 3,
     "groupMinNumber": 1,
     "groupMaxNumber": 0,
     "groupPerfectNumber": 0,
     "dotAvg": 0,
     "lineAvg": 0,
     "codeAvg": 0,
     "wordAvg": 0,
     "groupAvg": 0
diff --git a/src/test/resources/scoring/expected/resolver-glued.json b/src/test/resources/scoring/expected/resolver-glued.json
index 6cd4933..0a5af9a 100644
--- a/src/test/resources/scoring/expected/resolver-glued.json
+++ b/src/test/resources/scoring/expected/resolver-glued.json
@@ -32,17 +32,17 @@
     "bunchGroup": 0,
     "moreOrLackWord": 0,
     "moreOrLackLine": 0,
     "patTotalNum": 0,
     "dotScore": 0,
     "lineScore": 0,
     "codeScore": 0,
     "wordScore": 0,
-    "groupScore": 2,
+    "groupScore": 4,
     "alterErrorScore": 0,
     "dotTotalTime": 0,
     "lineTotalTime": 0,
     "codeTotalTime": 0,
     "wordTotalTime": 0,
     "groupTotalTime": 0
   }
 }
diff --git a/src/test/resources/scoring/expected/resolver-normal-with-blank.json b/src/test/resources/scoring/expected/resolver-normal-with-blank.json
index 7c1591a..9033eae 100644
--- a/src/test/resources/scoring/expected/resolver-normal-with-blank.json
+++ b/src/test/resources/scoring/expected/resolver-normal-with-blank.json
@@ -1,24 +1,24 @@
 {
   "resolverMessage": [
     "3729",
     "7201"
   ],
   "resolverPatLogs": [
     "[[{\"key\":0,\"value\":100}]]",
-    "[]"
+    "[[{\"key\":1,\"value\":300}]]"
   ],
   "resolverMoresTime": [
-    "[[1,0]]",
-    "[]"
+    "[[11,12]]",
+    "[[21,22]]"
   ],
   "resolverMoresValue": [
-    "[[11,12]]",
-    "[]"
+    "[[1,0]]",
+    "[[0,1]]"
   ],
   "scoreVO": {
     "lack": 0,
     "correct": 0,
     "errorNumber": 0,
     "moreGroup": 0,
     "lackGroup": 0,
     "bunchGroup": 0,
diff --git a/src/test/resources/scoring/expected/resolver-question-marks.json b/src/test/resources/scoring/expected/resolver-question-marks.json
index 9a106de..e9c34f1 100644
--- a/src/test/resources/scoring/expected/resolver-question-marks.json
+++ b/src/test/resources/scoring/expected/resolver-question-marks.json
@@ -7,28 +7,26 @@
   ],
   "resolverPatLogs": [
     "[[{\"key\":0,\"value\":100}]]",
     "[[]]",
     "[[{\"key\":1,\"value\":300}]]",
     "[[{\"key\":0,\"value\":42}],[{\"key\":0,\"value\":43}],[{\"key\":0,\"value\":44}]]"
   ],
   "resolverMoresTime": [
-    "[[0]]",
-    "[[92]]",
-    "[[1]]",
-    "[\"[42]\",\"[43]\",\"[44]\"]",
-    "[[2]]"
-  ],
-  "resolverMoresValue": [
     "[[10]]",
     "[[91]]",
     "[[20]]",
-    "[\"[5]\",\"[6]\",\"[7]\"]",
-    "[[30]]"
+    "[\"[42]\",\"[43]\",\"[44]\"]"
+  ],
+  "resolverMoresValue": [
+    "[[0]]",
+    "[[92]]",
+    "[[1]]",
+    "[\"[5]\",\"[6]\",\"[7]\"]"
   ],
   "scoreVO": {
     "lack": 0,
     "correct": 0,
     "errorNumber": 0,
     "moreGroup": 0,
     "lackGroup": 0,
     "bunchGroup": 0,


---
commit 1e498d3975f93fabb22f28947cec84a93082c9ee
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 04:05:27 2026 +0800

    fix(scoring-3): 新建 ScoreMath.rate/accuracy，三套速率公式与守分子漂移全部收口（P2-68/15/51）

diff --git a/src/main/java/com/nip/common/utils/ScoreMath.java b/src/main/java/com/nip/common/utils/ScoreMath.java
new file mode 100644
index 0000000..f889ece
--- /dev/null
+++ b/src/main/java/com/nip/common/utils/ScoreMath.java
@@ -0,0 +1,50 @@
+package com.nip.common.utils;
+
+import java.math.BigDecimal;
+import java.math.RoundingMode;
+
+/**
+ * 评分公共数学口径（Phase3 Task 3.3，P2-68 / P2-15 / P2-51 统一收口）。
+ * 全仓速率/正确率计算一律走这里，禁止再内联除法。
+ */
+public final class ScoreMath {
+
+  private static final BigDecimal MILLIS_PER_MINUTE = BigDecimal.valueOf(60_000L);
+  private static final BigDecimal HUNDRED = new BigDecimal(100);
+
+  private ScoreMath() {
+  }
+
+  /**
+   * 速率 = count / totalTimeMillis 折算为「次/分钟」。
+   *
+   * @param count           次数（组数/码数/拍发次数）
+   * @param totalTimeMillis 总耗时，单位【毫秒】；秒要先乘 1000
+   * @return 每分钟次数，0 位小数 HALF_UP；count<=0 或 totalTimeMillis<=0 时返回 0（零除返 0）
+   */
+  public static BigDecimal rate(long count, long totalTimeMillis) {
+    if (count <= 0 || totalTimeMillis <= 0) {
+      return BigDecimal.ZERO;
+    }
+    return new BigDecimal(count)
+        .multiply(MILLIS_PER_MINUTE)
+        .divide(new BigDecimal(totalTimeMillis), 0, RoundingMode.HALF_UP);
+  }
+
+  /**
+   * 正确率百分比 = correct / total * 100。
+   *
+   * @param correct 正确个数（为负按 0 计，避免负正确率）
+   * @param total   总个数，守【分母】：total<=0 返回 0
+   * @return 百分比，中间除法 2 位小数 HALF_UP 再乘 100（与既有落库口径一致，如 33.00）
+   */
+  public static BigDecimal accuracy(long correct, long total) {
+    if (total <= 0) {
+      return BigDecimal.ZERO;
+    }
+    long safeCorrect = Math.max(correct, 0);
+    return new BigDecimal(safeCorrect)
+        .divide(new BigDecimal(total), 2, RoundingMode.HALF_UP)
+        .multiply(HUNDRED);
+  }
+}
diff --git a/src/main/java/com/nip/service/EnteringTelexPatService.java b/src/main/java/com/nip/service/EnteringTelexPatService.java
index 29f41d0..0af22ec 100644
--- a/src/main/java/com/nip/service/EnteringTelexPatService.java
+++ b/src/main/java/com/nip/service/EnteringTelexPatService.java
@@ -1,12 +1,13 @@
 package com.nip.service;
 
 import com.nip.common.utils.Assert;
 import com.nip.common.utils.PojoUtils;
+import com.nip.common.utils.ScoreMath;
 import com.nip.dao.EnteringStatisticalDao;
 import com.nip.dao.EnteringTelexPatDao;
 import com.nip.dao.UserDao;
 import com.nip.dto.vo.EnteringTelexPatVO;
 import com.nip.dto.vo.param.EnteringTelexPatQueryParam;
 import com.nip.dto.vo.param.EnteringTelexPatSaveParam;
 import com.nip.entity.EnteringStatisticalEntity;
 import com.nip.entity.EnteringTelexPatEntity;
@@ -69,20 +70,18 @@ public class EnteringTelexPatService {
             .setUserId(userEntity.getId())
             .setType(1)
             .setChildType(save.getType())
             .setAvgSpeed(new BigDecimal(0))
             .setTotalCount(0)
             .setTotalTime("0"));
     queryStatisticalEntity.setTotalTime(String.valueOf(save.getTotalTime()));
     queryStatisticalEntity.setTotalCount(queryStatisticalEntity.getTotalCount() + 1);
-    //计算平均速率=拍发总次数/时长(秒)*60
-    BigDecimal avgSpeed = new BigDecimal(save.getTotalNum()).divide(
-        new BigDecimal(save.getTotalTime()), 10, RoundingMode.HALF_UP).multiply(new BigDecimal(60)).setScale(0, RoundingMode.HALF_UP);
-    queryStatisticalEntity.setAvgSpeed(avgSpeed);
+    //计算平均速率=拍发总次数/时长(秒)折算次/分钟（ScoreMath 统一口径，时长为 0 返 0，P2-68）
+    queryStatisticalEntity.setAvgSpeed(ScoreMath.rate(save.getTotalNum(), save.getTotalTime() * 1000L));
     statisticalDao.save(queryStatisticalEntity);
     return PojoUtils.convertOne(save, EnteringTelexPatVO.class);
   }
 
   public EnteringTelexPatVO findByUserIdAndType(String token, Integer type) {
     UserEntity userEntity = userDao.findUserEntityByToken(token);
     EnteringTelexPatEntity entity = telexPatDao.findByCreateUserIdAndType(userEntity.getId(), type);
     return Optional.ofNullable(entity).map(e -> PojoUtils.convertOne(e, EnteringTelexPatVO.class))
diff --git a/src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java b/src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java
index 7e6079e..fc98903 100644
--- a/src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java
+++ b/src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java
@@ -1,15 +1,16 @@
 package com.nip.service;
 
 import com.google.gson.reflect.TypeToken;
 import com.nip.common.constants.PostTelegraphKeyPatTrainEnum;
 import com.nip.common.utils.GlobalMessageGeneratedUtil;
 import com.nip.common.utils.JSONUtils;
 import com.nip.common.utils.PojoUtils;
+import com.nip.common.utils.ScoreMath;
 import com.nip.dao.*;
 import com.nip.dto.*;
 import com.nip.dto.vo.*;
 import com.nip.entity.*;
 import io.quarkus.panache.common.Sort;
 import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.inject.Inject;
 import jakarta.transaction.Transactional;
@@ -374,35 +375,26 @@ public class PostTelegraphKeyPatTrainService {
       // 调整最后一页的缺失组数
       if (lastPageGroups > 0 && missingPages == 1) {
         missingGroups = lastPageGroups;
       }
       ks.setLackGroup(ks.getLackGroup() + missingGroups);
       ks.setLackLine(ks.getLackLine() + missingPages * 10);
     }
 
-    // 计算速率 拍发个数/训练时长*60
-    BigDecimal speed = new BigDecimal("0");
-    if (ks.getPat() != 0) {
-      speed = new BigDecimal(ks.getPat())
-          .divide(new BigDecimal(ks.getPatTime()).divide(new BigDecimal(1000), 10, RoundingMode.HALF_UP), 10,
-              RoundingMode.HALF_UP)
-          .multiply(new BigDecimal(60)).setScale(0, RoundingMode.HALF_UP);
-    }
+    // 计算速率 拍发个数/训练时长折算次/分钟（patTime 单位毫秒；守分母，ScoreMath 统一口径）
+    BigDecimal speed = ScoreMath.rate(ks.getPat(), ks.getPatTime());
 
     entity.setSpeed(String.valueOf(speed));
 
     // 错误个数
     entity.setErrorNumber(ks.getError());
-    BigDecimal accuracy = new BigDecimal("0");
-    if (ks.getPatGroup() - ks.getError() != 0) {
-      // 计算正确率 （拍发总个数 - 错误个数- 多字- 少字)） /拍发总个数
-      accuracy = new BigDecimal(ks.getPatGroup() - ks.getError() - ks.getBunchGroup()).divide(
-          new BigDecimal(ks.getPatGroup()), 2, RoundingMode.HALF_UP).multiply(new BigDecimal(100));
-    }
+    // 计算正确率 （拍发总个数 - 错误个数 - 串组） / 拍发总个数（守分母，ScoreMath 统一口径）
+    BigDecimal accuracy = ScoreMath.accuracy(
+        (long) ks.getPatGroup() - ks.getError() - ks.getBunchGroup(), ks.getPatGroup());
     entity.setAccuracy(accuracy.doubleValue());
 
     // 得到要扣的分
     String minus = "-";
     BigDecimal score = entity.getScore();
     BigDecimal errorScore = rule.getOther().getErrorCode().multiply(new BigDecimal(ks.getError()));
     deductInfo.put("errorNumber", ks.getError());
     deductInfo.put("errorScore", minus + errorScore);
diff --git a/src/main/java/com/nip/service/PostTelexPatTrainService.java b/src/main/java/com/nip/service/PostTelexPatTrainService.java
index ac67120..9c5d69b 100644
--- a/src/main/java/com/nip/service/PostTelexPatTrainService.java
+++ b/src/main/java/com/nip/service/PostTelexPatTrainService.java
@@ -2,16 +2,17 @@ package com.nip.service;
 
 import com.google.gson.reflect.TypeToken;
 import com.nip.common.PageInfo;
 import com.nip.common.constants.PostTelexPatTrainStatusEnum;
 import com.nip.common.utils.CheckUtils;
 import com.nip.common.utils.JSONUtils;
 import com.nip.common.utils.Page;
 import com.nip.common.utils.PojoUtils;
+import com.nip.common.utils.ScoreMath;
 import com.nip.common.utils.TelexPatUtils;
 import com.nip.dao.GradingRuleDao;
 import com.nip.dao.PostTelexPatTrainDao;
 import com.nip.dao.PostTelexPatTrainPageDao;
 import com.nip.dao.PostTelexPatTrainPageValueDao;
 import com.nip.dto.*;
 import com.nip.dto.vo.PostTelexPatTrainPageInfoVO;
 import com.nip.dto.vo.PostTelexPatTrainPageVO;
@@ -685,25 +686,23 @@ public class PostTelexPatTrainService {
       // 改错扣分 = 改错字数 *2
       BigDecimal updateScore = rule.getOther().getCorrectMistakes().multiply(new BigDecimal(change));
       score = score.subtract(updateScore);
 
       // 改错
       deductMap.put("updateErrorNumber", change + "");
       deductMap.put("updateErrorScore", updateScore.toString());
 
-      // 计算速率 组数 / 耗时 * 60
-      BigDecimal speed = parseCodeAll.stream()
+      // 计算速率 组数 / 耗时(秒) 折算次/分钟（ScoreMath 统一口径，validTime 为空/0 返 0）
+      long patGroupCount = parseCodeAll.stream()
           .flatMap(Collection::stream)
-          .map(List::size)
-          .map(BigDecimal::new)
-          .reduce(BigDecimal.ZERO, BigDecimal::add);
-      speed = speed.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
-          : speed.divide(new BigDecimal(param.getValidTime()), 10, RoundingMode.HALF_DOWN)
-              .multiply(new BigDecimal(60)).setScale(0, RoundingMode.HALF_UP);
+          .mapToLong(List::size)
+          .sum();
+      long validTimeMillis = param.getValidTime() == null ? 0L : param.getValidTime() * 1000L;
+      BigDecimal speed = ScoreMath.rate(patGroupCount, validTimeMillis);
       // 计算在结算速率是否高于规定速率
       int speedDiffer = rule.getWpm().getBase() - Integer.parseInt(param.getTotalSpeed());
       if (speedDiffer > 0) {
         // 乘法
         BigDecimal speedLow = rule.getWpm().getL().multiply(new BigDecimal(speedDiffer));
         // 减法
         score = score.subtract(speedLow);
         // 速率扣分
@@ -851,26 +850,21 @@ public class PostTelexPatTrainService {
       deductMap.put("nonStandartScore", minus + nonStandartScore);
 
       BigDecimal alterError = rule.getOther().getAlterError();
       BigDecimal correctMistakesScore = new BigDecimal(ks.getCorrectMistakesNumber())
           .multiply(null == alterError ? rule.getOther().getCorrectMistakes() : alterError);
       deductMap.put("correctMistakesNumber", ks.getCorrectMistakesNumber());
       deductMap.put("correctMistakesScore", minus + correctMistakesScore);
 
-      // 计算正确率 （拍发总个数- 错误个数 = 正确个数） / 总个数
-      BigDecimal accuracy = new BigDecimal("0");
       // 正确组数 =（拍发总组数 - 错码组 - 多少码组）；errorNumber 落库真实错误计数（P1-20）
       int correctTotal = ks.getPatGroup() - ks.getErrorCodeNumber() - ks.getMuchLessCodeNumber();
       entity.setErrorNumber(ks.getErrorCodeNumber() + ks.getMuchLessCodeNumber());
-      if (correctTotal != 0) {
-        // 计算正确率 （拍发总个数 - 错误个数- 多字- 少字)） /拍发总个数
-        accuracy = new BigDecimal(correctTotal).divide(
-            new BigDecimal(ks.getPatGroup()), 2, RoundingMode.HALF_UP).multiply(new BigDecimal(100));
-      }
+      // 计算正确率（守分母，ScoreMath 统一口径）
+      BigDecimal accuracy = ScoreMath.accuracy(correctTotal, ks.getPatGroup());
 
       score = score.subtract(errorCodeScore)
           .subtract(muchLessLineScore)
           .subtract(muchLessGroupsScore)
           .subtract(muchLessCodeScore)
           .subtract(lessReturnLineScore)
           .subtract(lessPageScore)
           .subtract(errorPageScore)
diff --git a/src/main/java/com/nip/service/TelegraphKeyPatTrainService.java b/src/main/java/com/nip/service/TelegraphKeyPatTrainService.java
index 3d3b1c2..33ba561 100644
--- a/src/main/java/com/nip/service/TelegraphKeyPatTrainService.java
+++ b/src/main/java/com/nip/service/TelegraphKeyPatTrainService.java
@@ -1,11 +1,12 @@
 package com.nip.service;
 
 import com.nip.common.utils.PojoUtils;
+import com.nip.common.utils.ScoreMath;
 import com.nip.dao.TelegraphKeyPatTrainDao;
 import com.nip.dao.TelegraphKeyTrainStatisticalDao;
 import com.nip.dto.TelegraphKeyPatTrainDto;
 import com.nip.dto.vo.TelegraphKeyPatTrainVO;
 import com.nip.entity.TelegraphKeyPatTrainEntity;
 import com.nip.entity.TelegraphKeyTrainStatisticalEntity;
 import com.nip.entity.UserEntity;
 import jakarta.enterprise.context.ApplicationScoped;
@@ -74,31 +75,28 @@ public class TelegraphKeyPatTrainService {
    * 此方法根据用户ID和训练类型来更新或创建电报训练的统计实体
    * 它计算平均速度，更新总训练次数和总训练时间
    *
    * @param dto        包含用户选择和训练信息的DTO
    * @param userEntity 用户实体，用于关联统计信息
    * @param entity     训练记录实体，从中提取统计数据
    */
   private void saveStsatistical(TelegraphKeyPatTrainDto dto, UserEntity userEntity, TelegraphKeyPatTrainEntity entity) {
+    // 平均速率 = 拍发总次数 / 时长(秒) 折算次/分钟（ScoreMath 统一口径；原两分支一处 /1000 一处不除，按“秒”统一，P2-68）
+    BigDecimal avgSpeed = ScoreMath.rate(entity.getTotalNum(), entity.getTotalTime() * 1000L);
     TelegraphKeyTrainStatisticalEntity statisticalEntity = statisticalDao.findByUserIdAndType(userEntity.getId(), dto.getType());
     statisticalEntity = Optional.ofNullable(statisticalEntity)
-        .map(temp -> temp.setAvgSpeed(new BigDecimal(entity.getTotalNum())
-                .divide(new BigDecimal(entity.getTotalTime()).divide(new BigDecimal(1000), 10, RoundingMode.HALF_UP), 10, RoundingMode.HALF_UP)
-                .multiply(new BigDecimal(60)).setScale(0, RoundingMode.HALF_UP))
+        .map(temp -> temp.setAvgSpeed(avgSpeed)
             .setTotalCount(temp.getTotalCount() + 1)
             .setTotalTime(String.valueOf(entity.getTotalTime()))
         )
         .orElse(new TelegraphKeyTrainStatisticalEntity()
             .setUserId(entity.getCreateUserId())
             .setType(entity.getType())
-            .setAvgSpeed(
-                new BigDecimal(entity.getTotalNum())
-                    .divide(new BigDecimal(entity.getTotalTime()), 10, RoundingMode.HALF_UP)
-                    .multiply(new BigDecimal(60)).setScale(0, RoundingMode.HALF_UP))
+            .setAvgSpeed(avgSpeed)
             .setTotalCount(1)
             .setTotalTime(String.valueOf(entity.getTotalTime())));
     statisticalDao.save(statisticalEntity);
   }
 
   @Transactional
   public TelegraphKeyPatTrainVO findByUserIdAndType(String token, Integer type) {
     UserEntity userEntity = userService.getUserByToken(token);
diff --git a/src/test/java/com/nip/common/utils/ScoreMathTest.java b/src/test/java/com/nip/common/utils/ScoreMathTest.java
new file mode 100644
index 0000000..4071108
--- /dev/null
+++ b/src/test/java/com/nip/common/utils/ScoreMathTest.java
@@ -0,0 +1,43 @@
+package com.nip.common.utils;
+
+import org.junit.jupiter.api.Test;
+
+import java.math.BigDecimal;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+
+class ScoreMathTest {
+
+  @Test
+  void ratePerMinute() {
+    // 120 次 / 60 秒 = 120 次/分钟
+    assertEquals(new BigDecimal(120), ScoreMath.rate(120, 60_000));
+    // 7 次 / 120 秒 = 3.5 → HALF_UP → 4
+    assertEquals(new BigDecimal(4), ScoreMath.rate(7, 120_000));
+  }
+
+  @Test
+  void rateZeroDenominatorReturnsZero() {
+    assertEquals(BigDecimal.ZERO, ScoreMath.rate(10, 0));
+    assertEquals(BigDecimal.ZERO, ScoreMath.rate(10, -5));
+  }
+
+  @Test
+  void rateZeroCountReturnsZero() {
+    assertEquals(BigDecimal.ZERO, ScoreMath.rate(0, 60_000));
+  }
+
+  @Test
+  void accuracyPercent() {
+    assertEquals(new BigDecimal("33.00"), ScoreMath.accuracy(1, 3));
+    assertEquals(new BigDecimal("100.00"), ScoreMath.accuracy(4, 4));
+  }
+
+  @Test
+  void accuracyGuardsDenominatorNotNumerator() {
+    // 分母为 0：返回 0，而不是 ArithmeticException（P2-15/51 守分子漂移）
+    assertEquals(BigDecimal.ZERO, ScoreMath.accuracy(5, 0));
+    // 分子为负：按 0 计，不出现负正确率
+    assertEquals(new BigDecimal("0.00"), ScoreMath.accuracy(-3, 10));
+  }
+}


---
commit 409c5122f96b6f551f7dd55ca75e33c2ac6f763a
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 04:08:22 2026 +0800

    fix(scoring-4): SpeedDeduct r/l 词间隔使用点修正 + statisticalPage 排序统一为显式 sort(type)

diff --git a/src/main/java/com/nip/common/utils/TickerPatUtils.java b/src/main/java/com/nip/common/utils/TickerPatUtils.java
index 40cc130..babc12d 100644
--- a/src/main/java/com/nip/common/utils/TickerPatUtils.java
+++ b/src/main/java/com/nip/common/utils/TickerPatUtils.java
@@ -636,21 +636,23 @@ public class TickerPatUtils {
               statisticsVO.setGroupPerfectNumber(statisticsVO.getGroupPerfectNumber() + 1);
             }
             scoreVO.setGroupTotalTime(scoreVO.getGroupTotalTime() + value);
           }
           // 词间隔
           else if (z != 0 && k == 0) {
             if (value < wordGapMin) {
               // 细
-              scoreVO.setWordScore(scoreVO.getWordScore() + (isDuct ? 0 : rule.getMiddle().getL()));
+              // 低于（细）扣 r=低于扣分（Task 3.4：SpeedDeduct r/l 语义，原用反）
+              scoreVO.setWordScore(scoreVO.getWordScore() + (isDuct ? 0 : rule.getMiddle().getR()));
               statisticsVO.setWordMinNumber(statisticsVO.getWordMinNumber() + 1);
             } else if (value > wordGapMax) {
               // 粗
-              scoreVO.setWordScore(scoreVO.getWordScore() + (isDuct ? 0 : rule.getMiddle().getR()));
+              // 高于（粗）扣 l（Task 3.4：SpeedDeduct r/l 语义，原用反）
+              scoreVO.setWordScore(scoreVO.getWordScore() + (isDuct ? 0 : rule.getMiddle().getL()));
               statisticsVO.setWordMaxNumber(statisticsVO.getWordMaxNumber() + 1);
             } else {
               statisticsVO.setWordPerfectNumber(statisticsVO.getWordPerfectNumber() + 1);
             }
             scoreVO.setWordTotalTime(scoreVO.getWordTotalTime() + value);
           }
           // 码间隔
           else {
diff --git a/src/main/java/com/nip/service/TelexPatTrainStatisticalService.java b/src/main/java/com/nip/service/TelexPatTrainStatisticalService.java
index 50dab00..0f368d3 100644
--- a/src/main/java/com/nip/service/TelexPatTrainStatisticalService.java
+++ b/src/main/java/com/nip/service/TelexPatTrainStatisticalService.java
@@ -10,16 +10,17 @@ import com.nip.entity.TelexPatTrainEntity;
 import com.nip.entity.TelexPatTrainStatisticalEntity;
 import com.nip.entity.UserEntity;
 import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.inject.Inject;
 import jakarta.transaction.Transactional;
 
 import java.math.BigDecimal;
 import java.math.RoundingMode;
+import java.util.Comparator;
 import java.util.List;
 import java.util.Map;
 import java.util.stream.Collectors;
 
 import static com.nip.common.constants.TelexPatTrainStatisticalTypeEnum.WORD;
 
 /**
  * @Author: wushilin
@@ -102,17 +103,20 @@ public class TelexPatTrainStatisticalService {
           entity.setTotalCount(0);
           entity.setAvgSpeed(BigDecimal.ZERO);
           entity.setTotalTime("0");
           statisticalDao.save(entity);
           entities.add(entity);
         }
       }
     }
-    return PojoUtils.convert(statisticalDao.findAllByUserId(userEntity.getId()), TelexPatTrainStatisticalVO.class);
+    // 显式按 type 排序（P2-69：原重新查库直返，顺序依赖 DB）
+    List<TelexPatTrainStatisticalVO> convert = PojoUtils.convert(entities, TelexPatTrainStatisticalVO.class);
+    convert.sort(Comparator.comparingInt(TelexPatTrainStatisticalVO::getType));
+    return convert;
   }
 
   /**
    * 初始化统计信息方法
    * 根据用户ID和类型统计训练信息的总时长、总次数和平均速率
    *
    * @param userId 用户ID，用于查询该用户的相关训练记录
    * @param type 类型，表示需要统计的训练类型
diff --git a/src/main/java/com/nip/service/TickerTapeTrainService.java b/src/main/java/com/nip/service/TickerTapeTrainService.java
index ef76675..a2506f9 100644
--- a/src/main/java/com/nip/service/TickerTapeTrainService.java
+++ b/src/main/java/com/nip/service/TickerTapeTrainService.java
@@ -23,17 +23,17 @@ import com.nip.entity.TickerTapeTrainStatisticalEntity;
 import com.nip.entity.UserEntity;
 import io.quarkus.hibernate.orm.panache.PanacheQuery;
 import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.inject.Inject;
 import jakarta.transaction.Transactional;
 
 import java.math.BigDecimal;
 import java.time.LocalDateTime;
-import java.util.Collections;
+import java.util.Comparator;
 import java.util.List;
 import java.util.Map;
 import java.util.Optional;
 import java.util.stream.Collectors;
 
 /**
  * @Author: wushilin
  * @Data: 2022-04-06 15:49
@@ -208,17 +208,18 @@ public class TickerTapeTrainService {
         entity.setTotalCount(0);
         entity.setAvgSpeed(new BigDecimal(0));
         entity.setTotalTime("0");
         statisticalDao.save(entity);
         entities.add(entity);
       }
     }
     List<TelexPatTrainStatisticalVO> convert = PojoUtils.convert(entities, TelexPatTrainStatisticalVO.class);
-    Collections.swap(convert, 0, 1);
+    // 显式按 type 排序（P2-17：原 Collections.swap(0,1) 依赖 DB 返回顺序，串位且 size<2 越界）
+    convert.sort(Comparator.comparingInt(TelexPatTrainStatisticalVO::getType));
     return convert;
   }
 
   public TickerTapeTrainVo lastTrain(String token, Integer type) {
     UserEntity userEntity = userDao.findUserEntityByToken(token);
     TickerTapeTrainEntity entity = tickerTapeTrainDao.lastTrain(userEntity.getId(), type);
     if (entity == null) {
       return new TickerTapeTrainVo();
diff --git a/src/main/java/com/nip/service/detector/ErrorCodeDetector.java b/src/main/java/com/nip/service/detector/ErrorCodeDetector.java
index 00f68e4..a4d6127 100644
--- a/src/main/java/com/nip/service/detector/ErrorCodeDetector.java
+++ b/src/main/java/com/nip/service/detector/ErrorCodeDetector.java
@@ -164,20 +164,20 @@ public class ErrorCodeDetector {
 
   /**
    * 处理字间隔过小
    */
   private void handleWordGapSmall(ComparisonContext context) {
     PostTelegramTrainScoreVO scoreVO = context.getScoreVO();
     PostTelegramTrainRule rule = context.getRule();
 
-    // 字间隔 word ↔ rule.getMiddle()（组间隔才是 large）；上限在 applyDeductions 按 middle.max 裁剪
-    scoreVO.setWordScore(scoreVO.getWordScore() + rule.getMiddle().getL());
+    // 字间隔 word ↔ rule.getMiddle()；过小=低于 → r=低于扣分（与 TickerPatUtils 词间隔口径一致，Task 3.4）
+    scoreVO.setWordScore(scoreVO.getWordScore() + rule.getMiddle().getR());
 
-    log.debug("检测到字间隔过小，扣分: {}", rule.getMiddle().getL());
+    log.debug("检测到字间隔过小，扣分: {}", rule.getMiddle().getR());
   }
 
   /**
    * 处理字间隔过大
    */
   private void handleWordGapLarge(ComparisonContext context,
       int currentIndex,
       String patKey1,
@@ -185,18 +185,18 @@ public class ErrorCodeDetector {
       MessageResultBuilder resultBuilder) {
 
     PostTelegramTrainScoreVO scoreVO = context.getScoreVO();
     PostTelegramTrainRule rule = context.getRule();
     List<String> resolverCorrectPatLogs = context.getResolverVO().getResolverPatLogs();
     List<String> resolverCorrectMoresValue = context.getResolverVO().getResolverMoresValue();
     List<String> resolverCorrectMoresTime = context.getResolverVO().getResolverMoresTime();
 
-    // 字间隔 word ↔ rule.getMiddle()（组间隔才是 large）
-    scoreVO.setWordScore(scoreVO.getWordScore() + rule.getMiddle().getR());
+    // 字间隔 word ↔ rule.getMiddle()；过大=高于 → l（与 TickerPatUtils 词间隔口径一致，Task 3.4）
+    scoreVO.setWordScore(scoreVO.getWordScore() + rule.getMiddle().getL());
 
     // 合并两组数据
     String patLog1 = resolverCorrectPatLogs.get(currentIndex);
     String patLog2 = resolverCorrectPatLogs.get(currentIndex + 1);
     String moresValue1 = resolverCorrectMoresValue.get(currentIndex);
     String moresValue2 = resolverCorrectMoresValue.get(currentIndex + 1);
     String moresTime1 = resolverCorrectMoresTime.get(currentIndex);
     String moresTime2 = resolverCorrectMoresTime.get(currentIndex + 1);
diff --git a/src/test/java/com/nip/service/TelexPatTrainStatisticalServiceTest.java b/src/test/java/com/nip/service/TelexPatTrainStatisticalServiceTest.java
new file mode 100644
index 0000000..158095e
--- /dev/null
+++ b/src/test/java/com/nip/service/TelexPatTrainStatisticalServiceTest.java
@@ -0,0 +1,51 @@
+package com.nip.service;
+
+import com.nip.dao.TelexPatTrainStatisticalDao;
+import com.nip.dao.UserDao;
+import com.nip.dto.vo.TelexPatTrainStatisticalVO;
+import com.nip.entity.TelexPatTrainStatisticalEntity;
+import com.nip.entity.UserEntity;
+import com.nip.testsupport.Fixtures;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import java.math.BigDecimal;
+import java.util.List;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+
+/**
+ * Task 3.4 P2-69：statisticalPage 原来补齐缺失类型后重新查库直返、完全不排序。
+ * 现在必须补齐 4 种类型并显式按 type 排序。
+ */
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class TelexPatTrainStatisticalServiceTest {
+  @Inject TelexPatTrainStatisticalService service;
+  @Inject TelexPatTrainStatisticalDao statisticalDao;
+  @Inject UserDao userDao;
+
+  @Test
+  void statisticalPageFillsMissingTypesAndSortsByType() {
+    UserEntity user = Fixtures.user(userDao, "p34-telex-sort");
+    // 只有 type 3 和 1，且逆序插入
+    for (int type : new int[]{3, 1}) {
+      TelexPatTrainStatisticalEntity e = new TelexPatTrainStatisticalEntity();
+      e.setUserId(user.getId());
+      e.setType(type);
+      e.setTotalCount(type * 10);
+      e.setAvgSpeed(BigDecimal.ZERO);
+      e.setTotalTime("0");
+      statisticalDao.save(e);
+    }
+
+    List<TelexPatTrainStatisticalVO> page = service.statisticalPage("p34-telex-sort");
+
+    assertEquals(4, page.size(), "缺失类型必须补齐到 4 种");
+    assertEquals(List.of(0, 1, 2, 3), page.stream().map(TelexPatTrainStatisticalVO::getType).toList(),
+        "必须按 type 升序返回");
+  }
+}
diff --git a/src/test/java/com/nip/service/TickerTapeTrainServiceTest.java b/src/test/java/com/nip/service/TickerTapeTrainServiceTest.java
new file mode 100644
index 0000000..06b3a4c
--- /dev/null
+++ b/src/test/java/com/nip/service/TickerTapeTrainServiceTest.java
@@ -0,0 +1,51 @@
+package com.nip.service;
+
+import com.nip.dao.TickerTapeTrainStatisticalDao;
+import com.nip.dao.UserDao;
+import com.nip.dto.vo.TelexPatTrainStatisticalVO;
+import com.nip.entity.TickerTapeTrainStatisticalEntity;
+import com.nip.entity.UserEntity;
+import com.nip.testsupport.Fixtures;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import java.math.BigDecimal;
+import java.util.List;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+
+/**
+ * Task 3.4 P2-17：statisticalPage 原来对未排序结果 Collections.swap(0,1)，
+ * DB 返回顺序不是 0,1,2 时三个 Tab 数据互串。现在必须显式按 type 排序。
+ */
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class TickerTapeTrainServiceTest {
+  @Inject TickerTapeTrainService service;
+  @Inject TickerTapeTrainStatisticalDao statisticalDao;
+  @Inject UserDao userDao;
+
+  @Test
+  void statisticalPageSortsByTypeRegardlessOfDbOrder() {
+    UserEntity user = Fixtures.user(userDao, "p34-tickertape-sort");
+    // 逆序插入，模拟 DB 返回顺序不是 0,1,2
+    for (int type : new int[]{2, 0, 1}) {
+      TickerTapeTrainStatisticalEntity e = new TickerTapeTrainStatisticalEntity();
+      e.setUserId(user.getId());
+      e.setType(type);
+      e.setTotalCount(type * 10);
+      e.setAvgSpeed(new BigDecimal(0));
+      e.setTotalTime("0");
+      statisticalDao.save(e);
+    }
+
+    List<TelexPatTrainStatisticalVO> page = service.statisticalPage("p34-tickertape-sort");
+
+    assertEquals(3, page.size());
+    assertEquals(List.of(0, 1, 2), page.stream().map(TelexPatTrainStatisticalVO::getType).toList(),
+        "必须按 type 升序，不依赖 DB 顺序");
+  }
+}
diff --git a/src/test/resources/scoring/expected/gap-two-groups.json b/src/test/resources/scoring/expected/gap-two-groups.json
index 5323203..82e9361 100644
--- a/src/test/resources/scoring/expected/gap-two-groups.json
+++ b/src/test/resources/scoring/expected/gap-two-groups.json
@@ -7,17 +7,17 @@
     "lackGroup": 0,
     "bunchGroup": 0,
     "moreOrLackWord": 0,
     "moreOrLackLine": 0,
     "patTotalNum": 0,
     "dotScore": 11,
     "lineScore": 10,
     "codeScore": 1,
-    "wordScore": 21,
+    "wordScore": 12,
     "groupScore": 1,
     "alterErrorScore": 0,
     "dotTotalTime": 650,
     "lineTotalTime": 1250,
     "codeTotalTime": 50,
     "wordTotalTime": 1201,
     "groupTotalTime": 300
   },


---
commit 4d46edb8fab6f759b09471becf5760b455900e26
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 04:12:18 2026 +0800

    fix(scoring-5): finish 幂等统一（P1-08/09/10）+ speedLog 按 floorNumber upsert

diff --git a/src/main/java/com/nip/service/PostTelegramTrainService.java b/src/main/java/com/nip/service/PostTelegramTrainService.java
index 65c204e..fd75713 100644
--- a/src/main/java/com/nip/service/PostTelegramTrainService.java
+++ b/src/main/java/com/nip/service/PostTelegramTrainService.java
@@ -507,21 +507,29 @@ public class PostTelegramTrainService {
 
   @Transactional(rollbackOn = Exception.class)
   public void saveContentValue(PostTelegramTrainContentValueDto dto) {
     PostTelegramTrainEntity trainEntity = Optional.ofNullable(postTelegramTrainDao.findById(dto.getTrainId()))
         .orElseThrow(() -> new IllegalArgumentException("未查询待该训练"));
     if (trainEntity.getMessageNumber().compareTo(dto.getFloorNumber()) > 0) {
       trainEntity.setFloorNow(dto.getFloorNumber() + 1);
     }
-    // 记录每页速率
+    // 记录每页速率：按 floorNumber upsert，与下方 deleteByTrainIdAndFloorNumber 的重传语义对齐（Task 3.5）
     List<String> speedLog = Optional.ofNullable(trainEntity.getSpeedLog())
         .map(speed -> JSONUtils.fromJson(speed, new TypeToken<List<String>>() {
         })).orElseGet(ArrayList::new);
-    speedLog.add(dto.getSpeed());
+    int speedIndex = dto.getFloorNumber() - 1; // floorNumber 从 1 开始
+    if (speedIndex >= 0) {
+      while (speedLog.size() <= speedIndex) {
+        speedLog.add("0");
+      }
+      speedLog.set(speedIndex, dto.getSpeed());
+    } else {
+      speedLog.add(dto.getSpeed());
+    }
     trainEntity.setSpeedLog(JSONUtils.toJson(speedLog));
     trainEntity.setErrorNumber(dto.getErrorNumber());
     trainEntity.setAccuracy(dto.getAccuracy());
 
     PostTelegramTrainContentFloorValueEntity valueEntity = PojoUtils.convertOne(
         dto, PostTelegramTrainContentFloorValueEntity.class, (d, e) -> {
           List<PostTelegramTrainContentAddParam> messageBody = handleMessageBody(d.getMessageBody());
           e.setMessageBody(JSONUtils.toJson(messageBody));
diff --git a/src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java b/src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java
index fc98903..48dfc66 100644
--- a/src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java
+++ b/src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java
@@ -128,16 +128,20 @@ public class PostTelegraphKeyPatTrainService {
     patTrainDao.save(entity);
   }
 
   @Transactional
   public PostTelegraphKeyPatTrainVO finish(PostTelegraphKeyPatTrainDto dto) {
     try {
       PostTelegraphKeyPatTrainEntity entity = Optional.ofNullable(patTrainDao.findById(dto.getId()))
           .orElseThrow(() -> new IllegalArgumentException(TRAINING_NOT_FOUND));
+      // P1-10：finish 幂等守卫——已完成的训练直接返回，不再重复结算（与 Telex/TickerTape 口径一致）
+      if (PostTelegraphKeyPatTrainEnum.FINISH.getStatus().equals(entity.getStatus())) {
+        return PojoUtils.convertOne(entity, PostTelegraphKeyPatTrainVO.class);
+      }
       // 分数
       PostTelegraphKeyPatTrainEntity save = countScore(entity, dto);
       return PojoUtils.convertOne(save, PostTelegraphKeyPatTrainVO.class);
     } catch (Exception e) {
       log.error("完成训练失败，训练ID: {}", dto.getId(), e);
       throw new RuntimeException(e);
     }
   }
diff --git a/src/main/java/com/nip/service/PostTelexPatTrainService.java b/src/main/java/com/nip/service/PostTelexPatTrainService.java
index 9c5d69b..290131c 100644
--- a/src/main/java/com/nip/service/PostTelexPatTrainService.java
+++ b/src/main/java/com/nip/service/PostTelexPatTrainService.java
@@ -208,20 +208,20 @@ public class PostTelexPatTrainService {
     }
   }
 
   @Transactional(rollbackOn = Exception.class)
   public PostTelexPatTrainVO finish(PostTelexPatTrainFinishParam param) {
     try {
       PostTelexPatTrainEntity entity = Optional.ofNullable(postTelexPatTrainDao.findById(param.getId()))
           .orElseThrow(() -> new IllegalArgumentException("未查询到训练信息"));
-      // 根据评分规则计算训练得分
-      // if (entity.getStatus().equals(3)) {
-      // return PojoUtils.convertOne(entity, PostTelexPatTrainVO.class);
-      // }
+      // P1-09：恢复 finish 幂等守卫——已完成的训练直接返回，不再重复结算
+      if (PostTelexPatTrainStatusEnum.FINISH.getStatus().equals(entity.getStatus())) {
+        return PojoUtils.convertOne(entity, PostTelexPatTrainVO.class);
+      }
       PostTelexPatTrainEntity postTelexPatTrainEntity = countScore(param, entity);
       postTelexPatTrainEntity.setTotalSpeed(param.getTotalSpeed());
       return PojoUtils.convertOne(postTelexPatTrainDao.save(postTelexPatTrainEntity), PostTelexPatTrainVO.class);
     } catch (Exception e) {
       log.error("完成训练失败，训练ID: {}", param.getId(), e);
       throw new RuntimeException(e);
     }
   }
diff --git a/src/main/java/com/nip/service/PostTickerTapeTrainService.java b/src/main/java/com/nip/service/PostTickerTapeTrainService.java
index 83750c6..8a16071 100644
--- a/src/main/java/com/nip/service/PostTickerTapeTrainService.java
+++ b/src/main/java/com/nip/service/PostTickerTapeTrainService.java
@@ -1,15 +1,14 @@
 package com.nip.service;
 
 import com.google.gson.reflect.TypeToken;
 import com.nip.common.PageInfo;
 import com.nip.common.constants.BaseConstants;
 import com.nip.common.constants.PostTickerTapeTrainStatusEnum;
-import com.nip.common.constants.TickerTapeTrainStatusEnum;
 import com.nip.common.utils.JSONUtils;
 import com.nip.common.utils.Page;
 import com.nip.common.utils.PojoUtils;
 import com.nip.dao.PostTickerTapeTrainDao;
 import com.nip.dao.PostTickerTapeTrainPageDao;
 import com.nip.dao.PostTickerTapeTrainPageValueDao;
 import com.nip.dto.vo.PostTickerTapeTrainPageVO;
 import com.nip.dto.vo.PostTickerTapeTrainPageValueVO;
@@ -172,17 +171,17 @@ public class PostTickerTapeTrainService {
   }
 
   @Transactional
   public void reset(String id) {
     PostTickerTapeTrainEntity entity = tickerTapeTrainDao.findById(id);
     Optional.ofNullable(entity)
         .orElseThrow(() -> new IllegalArgumentException(BaseConstants.TRAINING_NOT_FOUND));
     // 状态校验
-    if (entity.getStatus().compareTo(TickerTapeTrainStatusEnum.NOT_STARTED.getCode()) == 0) {
+    if (entity.getStatus().compareTo(NOT_STARTED.getCode()) == 0) {
       throw new IllegalArgumentException("训练状态不是未开始");
     }
     entity.setStatus(NOT_STARTED.getCode());
     entity.setStartTime(null);
     tickerTapeTrainDao.save(entity);
   }
 
   @Transactional
@@ -302,17 +301,19 @@ public class PostTickerTapeTrainService {
     PostTickerTapeTrainPageValueVO ret = new PostTickerTapeTrainPageValueVO();
     ret.setMessageBody(pageVo);
     ret.setValue(value);
     return ret;
   }
 
   private void checkStatus(String id) {
     PostTickerTapeTrainEntity entity = tickerTapeTrainDao.findById(id);
-    if (entity.getStatus().compareTo(TickerTapeTrainStatusEnum.FINISH.getCode()) == 0) {
+    // P1-08：统一用 PostTickerTapeTrainStatusEnum（finish 写 2）；已结束(2)/已评分(3) 均拦截
+    if (entity.getStatus().compareTo(PostTickerTapeTrainStatusEnum.FINISH.getCode()) == 0
+        || entity.getStatus().compareTo(HAS_SCORE.getCode()) == 0) {
       throw new IllegalArgumentException("训练已结束");
     }
   }
 
   @Transactional
   public boolean delete(String trainId) {
     valueDao.delete("trainId=?1", trainId);
     pageDao.delete("trainId=?1", trainId);
diff --git a/src/test/java/com/nip/service/PostTelegramTrainServiceTest.java b/src/test/java/com/nip/service/PostTelegramTrainServiceTest.java
new file mode 100644
index 0000000..2ca274d
--- /dev/null
+++ b/src/test/java/com/nip/service/PostTelegramTrainServiceTest.java
@@ -0,0 +1,55 @@
+package com.nip.service;
+
+import com.google.gson.reflect.TypeToken;
+import com.nip.common.utils.JSONUtils;
+import com.nip.dao.PostTelegramTrainDao;
+import com.nip.dto.PostTelegramTrainContentValueDto;
+import com.nip.entity.PostTelegramTrainEntity;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import java.util.ArrayList;
+import java.util.List;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+
+/**
+ * Task 3.5：saveContentValue 的 speedLog 原来无脑 append，同一页重传会追加重复速率，
+ * 与 :534 的 deleteByTrainIdAndFloorNumber（按页覆盖）语义不一致。现在按 floorNumber upsert。
+ */
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class PostTelegramTrainServiceTest {
+  @Inject PostTelegramTrainService service;
+  @Inject PostTelegramTrainDao trainDao;
+
+  private static PostTelegramTrainContentValueDto dto(String trainId, int floorNumber, String speed) {
+    PostTelegramTrainContentValueDto d = new PostTelegramTrainContentValueDto();
+    d.setTrainId(trainId);
+    d.setFloorNumber(floorNumber);
+    d.setSpeed(speed);
+    d.setErrorNumber(0);
+    d.setAccuracy("0.00");
+    d.setMessageBody(new ArrayList<>());
+    return d;
+  }
+
+  @Test
+  void speedLogUpsertsByFloorNumberInsteadOfAppending() {
+    PostTelegramTrainEntity e = new PostTelegramTrainEntity();
+    e.setMessageNumber(200);
+    e = trainDao.save(e);
+    String id = e.getId();
+
+    service.saveContentValue(dto(id, 1, "80"));
+    service.saveContentValue(dto(id, 1, "90")); // 同一页重传：覆盖而不是追加
+    service.saveContentValue(dto(id, 2, "100"));
+
+    List<String> speedLog = JSONUtils.fromJson(trainDao.findById(id).getSpeedLog(), new TypeToken<>() {
+    });
+    assertEquals(List.of("90", "100"), speedLog, "同页重传必须按 floorNumber 覆盖");
+  }
+}
diff --git a/src/test/java/com/nip/service/PostTelegraphKeyPatTrainServiceTest.java b/src/test/java/com/nip/service/PostTelegraphKeyPatTrainServiceTest.java
new file mode 100644
index 0000000..faa1d51
--- /dev/null
+++ b/src/test/java/com/nip/service/PostTelegraphKeyPatTrainServiceTest.java
@@ -0,0 +1,44 @@
+package com.nip.service;
+
+import com.nip.dao.PostTelegraphKeyPatTrainDao;
+import com.nip.dto.PostTelegraphKeyPatTrainDto;
+import com.nip.dto.vo.PostTelegraphKeyPatTrainVO;
+import com.nip.entity.PostTelegraphKeyPatTrainEntity;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import java.math.BigDecimal;
+
+import static com.nip.common.constants.PostTelegraphKeyPatTrainEnum.FINISH;
+import static org.junit.jupiter.api.Assertions.assertEquals;
+
+/**
+ * Task 3.5 P1-10：finish 原来没有任何状态守卫，重复 finish 每次 deleteByTrainId+重插、
+ * 时长按新 endTime 重算覆盖。已完成训练必须直接返回。
+ * （守卫失效时本用例会走 countScore：无规则内容直接抛异常。）
+ */
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class PostTelegraphKeyPatTrainServiceTest {
+  @Inject PostTelegraphKeyPatTrainService service;
+  @Inject PostTelegraphKeyPatTrainDao trainDao;
+
+  @Test
+  void finishOnFinishedTrainReturnsWithoutRecount() {
+    PostTelegraphKeyPatTrainEntity e = new PostTelegraphKeyPatTrainEntity();
+    e.setStatus(FINISH.getStatus());
+    e.setScore(new BigDecimal(77));
+    e = trainDao.save(e);
+
+    PostTelegraphKeyPatTrainDto dto = new PostTelegraphKeyPatTrainDto();
+    dto.setId(e.getId());
+
+    PostTelegraphKeyPatTrainVO vo = service.finish(dto);
+
+    assertEquals(0, new BigDecimal(77).compareTo(trainDao.findById(e.getId()).getScore()),
+        "已完成训练的分数不得被重复结算覆盖");
+  }
+}
diff --git a/src/test/java/com/nip/service/PostTelexPatTrainServiceTest.java b/src/test/java/com/nip/service/PostTelexPatTrainServiceTest.java
new file mode 100644
index 0000000..839fe58
--- /dev/null
+++ b/src/test/java/com/nip/service/PostTelexPatTrainServiceTest.java
@@ -0,0 +1,43 @@
+package com.nip.service;
+
+import com.nip.dao.PostTelexPatTrainDao;
+import com.nip.dto.vo.PostTelexPatTrainVO;
+import com.nip.dto.vo.param.PostTelexPatTrainFinishParam;
+import com.nip.entity.PostTelexPatTrainEntity;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import static com.nip.common.constants.PostTelexPatTrainStatusEnum.FINISH;
+import static org.junit.jupiter.api.Assertions.assertEquals;
+
+/**
+ * Task 3.5 P1-09：finish 的幂等守卫曾被注释掉，重复 finish 会把「报底+用户值」混合行
+ * 再解析一遍并全表删除重写。已完成训练必须直接返回，不重新结算。
+ * （守卫失效时本用例会走 countScore：无规则/无页数据直接抛异常。）
+ */
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class PostTelexPatTrainServiceTest {
+  @Inject PostTelexPatTrainService service;
+  @Inject PostTelexPatTrainDao trainDao;
+
+  @Test
+  void finishOnFinishedTrainReturnsWithoutRecount() {
+    PostTelexPatTrainEntity e = new PostTelexPatTrainEntity();
+    e.setStatus(FINISH.getStatus());
+    e.setTrainType(4);
+    e.setScore("88");
+    e = trainDao.save(e);
+
+    PostTelexPatTrainFinishParam param = new PostTelexPatTrainFinishParam();
+    param.setId(e.getId());
+
+    PostTelexPatTrainVO vo = service.finish(param);
+
+    assertEquals("88", vo.getScore(), "已完成训练的分数不得被重复结算覆盖");
+    assertEquals("88", trainDao.findById(e.getId()).getScore());
+  }
+}
diff --git a/src/test/java/com/nip/service/PostTickerTapeTrainServiceTest.java b/src/test/java/com/nip/service/PostTickerTapeTrainServiceTest.java
new file mode 100644
index 0000000..a653324
--- /dev/null
+++ b/src/test/java/com/nip/service/PostTickerTapeTrainServiceTest.java
@@ -0,0 +1,60 @@
+package com.nip.service;
+
+import com.nip.dao.PostTickerTapeTrainDao;
+import com.nip.dto.vo.param.PostTickerTapeTrainUpdateParam;
+import com.nip.entity.PostTickerTapeTrainEntity;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import java.time.LocalDateTime;
+
+import static com.nip.common.constants.PostTickerTapeTrainStatusEnum.FINISH;
+import static com.nip.common.constants.PostTickerTapeTrainStatusEnum.HAS_SCORE;
+import static com.nip.common.constants.PostTickerTapeTrainStatusEnum.UNDERWAY;
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.assertThrows;
+
+/**
+ * Task 3.5 P1-08：finish 写 PostTickerTapeTrainStatusEnum.FINISH(2)，
+ * 而 checkStatus 拦的是旧枚举 FINISH(3) —— 已结束的训练可被反复 begin/finish 覆盖时间。
+ * 现在 checkStatus 统一新枚举并同时拦 FINISH(2) 与 HAS_SCORE(3)。
+ */
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class PostTickerTapeTrainServiceTest {
+  @Inject PostTickerTapeTrainService service;
+  @Inject PostTickerTapeTrainDao trainDao;
+
+  private PostTickerTapeTrainEntity train(Integer status) {
+    PostTickerTapeTrainEntity e = new PostTickerTapeTrainEntity();
+    e.setStatus(status);
+    e.setStartTime(LocalDateTime.now().minusMinutes(5));
+    e.setValidTime("0");
+    return trainDao.save(e);
+  }
+
+  @Test
+  void finishedTrainRejectsSecondFinishAndBegin() {
+    PostTickerTapeTrainEntity e = train(UNDERWAY.getCode());
+    PostTickerTapeTrainUpdateParam param = new PostTickerTapeTrainUpdateParam();
+    param.setId(e.getId());
+
+    service.finish(param);
+    assertEquals(FINISH.getCode(), trainDao.findById(e.getId()).getStatus());
+
+    assertThrows(IllegalArgumentException.class, () -> service.finish(param),
+        "已结束训练重复 finish 必须被拦截");
+    assertThrows(IllegalArgumentException.class, () -> service.begin(e.getId()),
+        "已结束训练重复 begin 必须被拦截");
+  }
+
+  @Test
+  void scoredTrainRejectsBegin() {
+    PostTickerTapeTrainEntity e = train(HAS_SCORE.getCode());
+    assertThrows(IllegalArgumentException.class, () -> service.begin(e.getId()),
+        "已评分训练必须被拦截");
+  }
+}


---
commit 70bb6003835c3b71d47d2f1b6272b2aacba12e0d
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 04:14:48 2026 +0800

    docs(scoring): Phase 3 报告落盘

diff --git a/.superpowers/sdd/2026-08-26-fix-plan/phase-3-report.md b/.superpowers/sdd/2026-08-26-fix-plan/phase-3-report.md
new file mode 100644
index 0000000..30a2e3d
--- /dev/null
+++ b/.superpowers/sdd/2026-08-26-fix-plan/phase-3-report.md
@@ -0,0 +1,62 @@
+# Phase 3 报告：评分核心统一
+
+**结论：Task 3.1–3.5 全部完成，5 个提交，验收套件 29 测试全绿。**
+
+验收命令（实际类名）：
+`flock /tmp/omp-mvn.lock -c "JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest='TickerPatUtils*,ScoreMath*,Post*Test,TickerTapeTrainServiceTest,TelexPatTrainStatisticalServiceTest'"`
+→ `Tests run: 29, Failures: 0, Errors: 0` BUILD SUCCESS。
+
+## 提交清单
+
+| Task | 提交 | 内容 |
+|---|---|---|
+| 3.1 | `5fb76f1` | characterization 快照锁现行为 |
+| 3.2 | `1fae6cf` | 确定性缺陷 + P1-01/02/06/20/21 |
+| 3.3 | `1e498d3` | ScoreMath 新建与迁移（P2-68/15/51） |
+| 3.4 | `409c512` | SpeedDeduct r/l 使用点 + 排序统一（P2-69/17） |
+| 3.5 | `4d46edb` | finish 幂等（P1-08/09/10）+ speedLog upsert |
+
+## Task 3.1（红/绿基线）
+
+- 样本入 `src/test/resources/scoring/`：规则 JSON 逐字取自 `project006.sql` `t_grading_rule` type=0 真实行；patKeys 用 `t_cable_floor` 真实字码组（3729/7201/U3YU）。
+- **数据事实：dump 内没有任何用户拍发数据**（`t_post_telegram_train_floor_content_value` 等表 0 条 INSERT），patLogs/moresTime/moresValue 按实体 schema 构造、锚定真实 patKeys 与规则。
+- `TickerPatUtilsCharacterizationTest` 4 用例快照（`scoring/expected/`，`SCORING_UPDATE=1` 再生成）。初版快照将列对调/错位/覆盖/串号的病态输出全部锁进文件，即修复前的"红"基线。
+
+## Task 3.2（快照 diff = 红→绿证据，见 1fae6cf 中 expected/*.json 变更）
+
+- TickerPatUtils：`:51` patKeys 过滤与 userContents 同步配对；`:116` groupScore 赋值→累加（快照 2→4）；四处 moresTime/moresValue 列对调复原（快照列内容互换）；`:169-170` `add(size-1,·)`→`set`（快照列表长度 5→4 对齐）；旧 `:640` 词完美串号 `getCodePerfectNumber()+1`→`getWordPerfectNumber()+1`（快照 1→3）。
+- P1-01 ErrorCodeDetector `:173/:194`：字间隔系数 large→middle（与封顶 middle.max 同段）。
+- P1-02 `saveTrainResult`：速率高于加分(l)/低于扣分(r)，与其余五处方向一致；`PostTelegramTrainScoreTest` 断言 ±方向与系数。
+- P1-06 `applyDeductions:709` 划封顶 `getDot().getMax()`→`getDash().getMax()`；测试用 dash.max=5≠dot.max=1 断言封顶取 5。两方法降为 package-private static 作测试缝（无行为变化）。
+- P1-20 电传 errorNumber 改存真实错误数（错码+多少码），正确率用正确组数。
+- P1-21 五三码规整与 convertCodeAll 同语义，抽 `normalizeAdjacentGroups` 静态缝；`PostTelexPatTrainScoreTest` 断言 `23456 789 → 2345 6789`（旧实现产出 `2345 7895`）。
+
+## Task 3.3
+
+- 新建 `common/utils/ScoreMath`：`rate(count, totalTimeMillis)`（次/分钟，零除/零次数返 0，注释注明毫秒）与 `accuracy(correct, total)`（守分母，负分子按 0）。`ScoreMathTest` 5 用例。
+- 迁移（旧内联实现全部删除，`grep multiply(new BigDecimal(60))` 确认本域零残留，仅剩 `service/general/**`——他分片域，未动）：
+  - PostTelexPatTrainService 速率（顺带修 validTime=0/null 除零/NPE，即 P1-13 同点）与 type≠4 正确率（P2-15 守分子→守分母）；
+  - PostTelegraphKeyPatTrainService 速率（顺带修 P1-14 守错变量：原判分子 pat、除 patTime）与正确率（P2-15）；
+  - TelegraphKeyPatTrainService 两分支（P2-68：原一支 /1000 一支不除，**按"秒"统一**——多数实现语义 + EnteringTelexPat 注释"时长(秒)"佐证）；
+  - EnteringTelexPatService（P2-68，除零返 0）。
+- **保守决策**：P2-68 第三处 TelexPatTrainStatisticalService:134 是"各场速率的算术平均"（非 count/time 语义），已有空集保护且被评审标 ✓，强并入 rate() 会改变含义 → 保留原实现，仅在此说明。
+- 数值口径说明：rate 单步除法 scale0 HALF_UP，替代旧"中间 scale10（Telex 处为 HALF_DOWN）再 setScale0"，恰好 .5 边界可能相差 1，属统一预期内。
+
+## Task 3.4
+
+- SpeedDeduct r/l 在 TickerPatUtils 词间隔使用点（旧 :633/:637）按字段语义修正：细(低于)→r、粗(高于)→l；ErrorCodeDetector 两处同步同口径（否则同一 wordScore 两个来源系数相反，重蹈 P1-01）。快照 wordScore 21→12。
+- 排序统一为显式 sort(type)：删 TickerTapeTrainService:216 `Collections.swap(0,1)`（P2-17）；TelexPatTrainStatisticalService 补齐后排序直返（P2-69）。两个 @QuarkusTest 用逆序插入断言输出 0,1,2(,3)。
+- **P2-69 第四写法 TelegramTrainService:400-402（sort 后旋转 2,0,1）在 Phase5 域**：已与 Ph5Schema 协调，其答复"保持现状"，故未统一，此处注明。
+
+## Task 3.5
+
+- P1-08：PostTickerTapeTrainService.checkStatus 统一 `PostTickerTapeTrainStatusEnum` 并同时拦 FINISH(2)/HAS_SCORE(3)，reset 同步换枚举（同值 0）；测试：finish 后重复 finish/begin 抛"训练已结束"，HAS_SCORE 拦 begin。
+- P1-09：恢复电传 finish 守卫（FINISH=3 直接返 VO 不重算）；P1-10：电键 finish 加同款守卫（FINISH=2）。**一致化口径**：三服务均有有效前置检查；TickerTape 沿用其 checkStatus 抛异常式（void finish），Telex/KeyPat 返回现有实体 VO（幂等重放）。测试实体只带 status+score、无规则无页数据——守卫失效必走 countScore 抛异常，即红态。
+- speedLog 按 floorNumber(1 起) upsert，空洞补 "0"，与 `deleteByTrainIdAndFloorNumber` 的按页覆盖语义对齐；测试断言同页重传 `["90","100"]` 而非追加三条。PostTelegramTrainEntity.speedLog 本仓内无计算消费方（仅重置/展示），补位安全。
+
+## Concerns
+
+1. **checkDotLineGap 内部 r/l 口径不一致（遗留）**：Task 3.4 只点名词间隔两处；同函数 dot/dash/little/large 分支仍是 细→l、粗→r（与词间隔修正后相反）。真实规则 l=1/r=10，方向影响扣分量级。评审文档未裁决这四处，брief 未列 → 按"只改任务列出位置"未动，建议后续统一裁决。
+2. TelegraphKeyPatTrainService totalTime 单位取"秒"是裁决（原两分支互斥）；若前端实际传毫秒，avgSpeed 会偏大 60 倍——与旧 create 分支行为一致，非回归。
+3. 收尾 `$MVN clean verify` 按全局约束留给合流后统一执行，本域未跑。
+4. Task 3.5 幂等测试的"红"未在本分支实跑（需要 stash 反证）；红态依据为评审 P1-09/10 复现路径 + 测试设计上守卫失效必抛异常。

