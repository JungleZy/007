## commits
a18849d test(base): Testcontainers MySQL 测试地基

## stat
 .../java/com/nip/entity/EquipmentDeviceEntity.java |  1 +
 src/main/resources/application.yml                 |  7 +++++
 src/test/java/com/nip/SmokeTest.java               | 21 +++++++++++++++
 .../java/com/nip/testsupport/MySqlResource.java    | 31 ++++++++++++++++++++++
 4 files changed, 60 insertions(+)

## diff
diff --git a/src/main/java/com/nip/entity/EquipmentDeviceEntity.java b/src/main/java/com/nip/entity/EquipmentDeviceEntity.java
index 1a8a5ba..01ca57a 100644
--- a/src/main/java/com/nip/entity/EquipmentDeviceEntity.java
+++ b/src/main/java/com/nip/entity/EquipmentDeviceEntity.java
@@ -33,20 +33,21 @@ public class EquipmentDeviceEntity {
   private String name;
 
   /**
    * 图片地址
    */
   private String image;
 
   /**
    * 表格字段
    */
+  @Column(name = "`option`")
   private String option;
 
   /**
    * 是否启用
    */
   private int isEnable;
 
 
   /**
    * 要点
diff --git a/src/main/resources/application.yml b/src/main/resources/application.yml
index d9f7aea..77fde78 100644
--- a/src/main/resources/application.yml
+++ b/src/main/resources/application.yml
@@ -50,10 +50,17 @@ quarkus:
         max-backup-index: 10
     console:
       level: INFO
       enable: true
       stderr: true
   native:
     resources:
       includes: resources/**
     march: compatibility
 #    additional-build-args: -H:ReflectionConfigurationFiles=reflection-config.json
+"%test":
+  quarkus:
+    hibernate-orm:
+      database:
+        generation: drop-and-create
+    http:
+      test-port: 18081
diff --git a/src/test/java/com/nip/SmokeTest.java b/src/test/java/com/nip/SmokeTest.java
new file mode 100644
index 0000000..b928486
--- /dev/null
+++ b/src/test/java/com/nip/SmokeTest.java
@@ -0,0 +1,21 @@
+package com.nip;
+
+import com.nip.dao.TestPaperDao;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import static org.junit.jupiter.api.Assertions.*;
+
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class SmokeTest {
+  @Inject TestPaperDao testPaperDao;
+
+  @Test
+  void schemaBoots() {
+    assertDoesNotThrow(() -> testPaperDao.count());
+  }
+}
diff --git a/src/test/java/com/nip/testsupport/MySqlResource.java b/src/test/java/com/nip/testsupport/MySqlResource.java
new file mode 100644
index 0000000..7e6e664
--- /dev/null
+++ b/src/test/java/com/nip/testsupport/MySqlResource.java
@@ -0,0 +1,31 @@
+package com.nip.testsupport;
+
+import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
+import org.testcontainers.containers.MySQLContainer;
+
+import java.util.Map;
+
+public class MySqlResource implements QuarkusTestResourceLifecycleManager {
+  static {
+    // Docker Engine 29+ 最低支持 API 1.44；Testcontainers 1.19.3 未配置时硬编码回退到 1.32，
+    // 会被 daemon 拒绝（"client version 1.32 is too old"）。升级 testcontainers 后可删。
+    System.setProperty("api.version", "1.44");
+  }
+
+  private static final MySQLContainer<?> DB = new MySQLContainer<>("mysql:8.0");
+
+  @Override
+  public Map<String, String> start() {
+    DB.start();
+    return Map.of(
+        "quarkus.datasource.jdbc.url",
+        DB.getJdbcUrl() + "?rewriteBatchedStatements=true&characterEncoding=utf8&allowPublicKeyRetrieval=true",
+        "quarkus.datasource.username", DB.getUsername(),
+        "quarkus.datasource.password", DB.getPassword());
+  }
+
+  @Override
+  public void stop() {
+    DB.stop();
+  }
+}
