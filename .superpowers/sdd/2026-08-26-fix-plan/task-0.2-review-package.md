## commits
16090a8 test(base): fixtures 与 ListUtils

## stat
 src/main/java/com/nip/common/utils/ListUtils.java | 12 ++++++++++++
 src/test/java/com/nip/testsupport/Fixtures.java   | 16 ++++++++++++++++
 2 files changed, 28 insertions(+)

## diff
diff --git a/src/main/java/com/nip/common/utils/ListUtils.java b/src/main/java/com/nip/common/utils/ListUtils.java
new file mode 100644
index 0000000..10b2289
--- /dev/null
+++ b/src/main/java/com/nip/common/utils/ListUtils.java
@@ -0,0 +1,12 @@
+package com.nip.common.utils;
+
+import java.util.ArrayList;
+import java.util.List;
+
+public final class ListUtils {
+  private ListUtils() {}
+
+  public static <T> List<T> nullToEmpty(List<T> list) {
+    return list == null ? new ArrayList<>() : list;
+  }
+}
diff --git a/src/test/java/com/nip/testsupport/Fixtures.java b/src/test/java/com/nip/testsupport/Fixtures.java
new file mode 100644
index 0000000..f800d81
--- /dev/null
+++ b/src/test/java/com/nip/testsupport/Fixtures.java
@@ -0,0 +1,16 @@
+package com.nip.testsupport;
+
+import com.nip.dao.UserDao;
+import com.nip.entity.UserEntity;
+
+public final class Fixtures {
+  private Fixtures() {}
+
+  public static UserEntity user(UserDao userDao, String token) {
+    UserEntity u = new UserEntity();
+    u.setUserName("tester");
+    u.setUserAccount("tester");
+    u.setToken(token);
+    return userDao.save(u); // save 自带事务独立提交
+  }
+}
