## commits
5921151 fix(p0-6): 损坏JSON解析失败改抛异常防空化回写

## stat
 .../sdd/2026-08-26-fix-plan/task-1.5-report.md     | 24 ++++++++++++
 .../java/com/nip/common/utils/TickerPatUtils.java  | 10 +++--
 .../com/nip/common/utils/TickerPatUtilsTest.java   | 44 ++++++++++++++++++++++
 3 files changed, 75 insertions(+), 3 deletions(-)

## diff
diff --git a/.superpowers/sdd/2026-08-26-fix-plan/task-1.5-report.md b/.superpowers/sdd/2026-08-26-fix-plan/task-1.5-report.md
new file mode 100644
index 0000000..cb0623a
--- /dev/null
+++ b/.superpowers/sdd/2026-08-26-fix-plan/task-1.5-report.md
@@ -0,0 +1,24 @@
+# Task 1.5 报告：损坏 JSON 空化回写（P0#6）
+
+**结论：已修复。** 三个 `catch (Exception ignore) {}` 改为抛 `IllegalStateException`（带字段名/index/cause），损坏 JSON 不再被静默转 null → 空数组回写；`saveContentValue` 的 `@Transactional(rollbackOn = Exception.class)` 随异常回滚，杜绝“先删后写空化结果”。
+
+## 改动
+- `src/main/java/com/nip/common/utils/TickerPatUtils.java`
+  - `handleMessageBody`（public static，:270 起，签名/位置与 brief 一致）内三个 catch 改抛：
+    - patLogs（原 :303）→ `throw new IllegalStateException("patLogs JSON 损坏，拒绝写入（index=" + i + ")", e)`
+    - moresTime（原 :308）→ 同构，字段名 moresTime
+    - moresValue（原 :313）→ 同构，字段名 moresValue
+  - patKeys 回退分支（:288-296）新增残留风险注释（协议容忍逐字符拆分，损坏 JSON 文本会被拆成含 `[ " ,` 垃圾按键，无协议标记无法区分，接受此残留）。patKeys 的 catch 与逐字符回退**保留不动**。
+- `src/test/java/com/nip/common/utils/TickerPatUtilsTest.java`（新增，纯 JUnit 5，无 @QuarkusTest，无容器）
+  - `corruptedPatLogsThrowsInsteadOfSilentEmpty`
+  - `corruptedMoresTimeThrowsInsteadOfSilentEmpty`
+  - `corruptedMoresValueThrowsInsteadOfSilentEmpty`
+
+## 红 → 绿证据
+命令：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=TickerPatUtilsTest`
+
+- 红（改前）：`Tests run: 3, Failures: 3, Errors: 0` — 全部 `Expected java.lang.IllegalStateException to be thrown, but nothing was thrown.`
+- 绿（改后）：`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS
+
+## 残留风险
+patKeys 逐字符回退保留，损坏 JSON 文本仍会被拆成垃圾按键（已加注释记录）；本任务只收敛 patLogs/moresTime/moresValue 的空化回写路径，符合 brief 范围。
diff --git a/src/main/java/com/nip/common/utils/TickerPatUtils.java b/src/main/java/com/nip/common/utils/TickerPatUtils.java
index 18b3ab2..4006477 100644
--- a/src/main/java/com/nip/common/utils/TickerPatUtils.java
+++ b/src/main/java/com/nip/common/utils/TickerPatUtils.java
@@ -278,46 +278,50 @@ public class TickerPatUtils {
     List<List<List<Integer>>> timesLists = new ArrayList<>(n);
     List<List<List<Integer>>> valuesLists = new ArrayList<>(n);
     for (int i = 0; i < n; i++) {
       PostTelegramTrainContentAddParam item = messageBody.get(i);
       List<String> pk = null;
       try {
         pk = JSONUtils.fromJson(item.getPatKeys(), new TypeToken<List<String>>() {
         });
       } catch (Exception ignore) {
       }
+      // 协议容忍：纯文本 patKeys 逐字符拆分；副作用：损坏的 JSON 数组文本也会被拆成含 [ " , 的垃圾按键——无协议标记无法区分，接受此残留
       if (pk == null) {
         pk = new ArrayList<>();
         String raw = item.getPatKeys();
         if (raw != null) {
           for (int c = 0; c < raw.length(); c++) {
             pk.add(String.valueOf(raw.charAt(c)));
           }
         }
       }
       List<List<Map<String, Object>>> logs = null;
       List<List<Integer>> times = null;
       List<List<Integer>> values = null;
       try {
         logs = JSONUtils.fromJson(item.getPatLogs(), new TypeToken<>() {
         });
-      } catch (Exception ignore) {
+      } catch (Exception e) {
+        throw new IllegalStateException("patLogs JSON 损坏，拒绝写入（index=" + i + ")", e);
       }
       try {
         times = JSONUtils.fromJson(item.getMoresTime(), new TypeToken<>() {
         });
-      } catch (Exception ignore) {
+      } catch (Exception e) {
+        throw new IllegalStateException("moresTime JSON 损坏，拒绝写入（index=" + i + ")", e);
       }
       try {
         values = JSONUtils.fromJson(item.getMoresValue(), new TypeToken<>() {
         });
-      } catch (Exception ignore) {
+      } catch (Exception e) {
+        throw new IllegalStateException("moresValue JSON 损坏，拒绝写入（index=" + i + ")", e);
       }
       pkLists.add(pk != null ? pk : new ArrayList<>());
       logsLists.add(logs != null ? logs : new ArrayList<>());
       timesLists.add(times != null ? times : new ArrayList<>());
       valuesLists.add(values != null ? values : new ArrayList<>());
     }
     for (int i = 0; i < n; i++) {
       List<String> curPk = pkLists.get(i);
       int curLen = curPk != null ? curPk.size() : 0;
       if (curLen >= 4) {
diff --git a/src/test/java/com/nip/common/utils/TickerPatUtilsTest.java b/src/test/java/com/nip/common/utils/TickerPatUtilsTest.java
new file mode 100644
index 0000000..6378b67
--- /dev/null
+++ b/src/test/java/com/nip/common/utils/TickerPatUtilsTest.java
@@ -0,0 +1,44 @@
+package com.nip.common.utils;
+
+import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
+import org.junit.jupiter.api.Test;
+
+import java.util.List;
+
+import static org.junit.jupiter.api.Assertions.assertThrows;
+
+class TickerPatUtilsTest {
+
+  @Test
+  void corruptedPatLogsThrowsInsteadOfSilentEmpty() {
+    PostTelegramTrainContentAddParam item = new PostTelegramTrainContentAddParam();
+    item.setPatKeys("[\"a\",\"b\",\"c\",\"d\"]");
+    item.setPatLogs("{corrupted-json");
+    item.setMoresTime("[[1,2]]");
+    item.setMoresValue("[[1,2]]");
+    assertThrows(IllegalStateException.class,
+        () -> TickerPatUtils.handleMessageBody(List.of(item)));
+  }
+
+  @Test
+  void corruptedMoresTimeThrowsInsteadOfSilentEmpty() {
+    PostTelegramTrainContentAddParam item = new PostTelegramTrainContentAddParam();
+    item.setPatKeys("[\"a\",\"b\",\"c\",\"d\"]");
+    item.setPatLogs("[[]]");
+    item.setMoresTime("{corrupted-json");
+    item.setMoresValue("[[1,2]]");
+    assertThrows(IllegalStateException.class,
+        () -> TickerPatUtils.handleMessageBody(List.of(item)));
+  }
+
+  @Test
+  void corruptedMoresValueThrowsInsteadOfSilentEmpty() {
+    PostTelegramTrainContentAddParam item = new PostTelegramTrainContentAddParam();
+    item.setPatKeys("[\"a\",\"b\",\"c\",\"d\"]");
+    item.setPatLogs("[[]]");
+    item.setMoresTime("[[1,2]]");
+    item.setMoresValue("{corrupted-json");
+    assertThrows(IllegalStateException.class,
+        () -> TickerPatUtils.handleMessageBody(List.of(item)));
+  }
+}
