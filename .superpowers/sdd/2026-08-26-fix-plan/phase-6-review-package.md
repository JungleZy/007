## commits
1fc4dab
c7abeb9
430be9a
32c318a
b29dd11


---
commit 1fc4dab12d1ea5ea95241c89197c008126f2e660
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 03:36:24 2026 +0800

    fix(delivery-6.1): workflow 监听 tag push、native 按架构拆 march、产物名嵌 runner.arch

diff --git a/.github/workflows/build-quarkus-native.yml b/.github/workflows/build-quarkus-native.yml
index 7e1a3da..b5b979d 100644
--- a/.github/workflows/build-quarkus-native.yml
+++ b/.github/workflows/build-quarkus-native.yml
@@ -1,28 +1,31 @@
 name: Build Quarkus Native
 
 on:
   push:
     branches: ['main', 'master']
+    tags: ['v*']
   pull_request:
 
 jobs:
   build:
     name: Build (${{ matrix.os }} / ${{ matrix.arch }})
     strategy:
       fail-fast: false
       matrix:
         include:
           - os: ubuntu-22.04
             arch: x86_64
             runner: ubuntu-22.04
+            native_march: x86-64
           - os: ubuntu-24.04
             arch: arm64
             runner: ubuntu-24.04-arm
+            native_march: armv8-a
           - os: windows-2022
             arch: x86_64
             runner: windows-2022
     runs-on: ${{ matrix.runner }}
     steps:
       - name: Checkout
         uses: actions/checkout@v4
 
@@ -42,24 +45,24 @@ jobs:
             ${{ runner.os }}-maven-
 
       - name: Build Native Binary
         if: matrix.os == 'windows-2022'
         run: mvn -B -Pnative package
 
       - name: Build Native Binary (Linux glibc 2.28 baseline)
         if: startsWith(matrix.os, 'ubuntu')
-        run: mvn -B -Pnative -Dquarkus.native.container-build=true -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21 -Dquarkus.native.march=compatibility -Dquarkus.native.additional-build-args=-march=x86-64 package
+        run: mvn -B -Pnative -Dquarkus.native.container-build=true -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21 -Dquarkus.native.march=${{ matrix.native_march }} package
 
       - name: Verify glibc baseline (Linux)
         if: startsWith(matrix.os, 'ubuntu')
         shell: bash
         run: |
           set -euo pipefail
-          FILE=$(ls target/*-runner 2>/dev/null | head -n 1 || true)
+          FILE=$(find target -maxdepth 1 -name '*-runner' -type f | head -n 1 || true)
           if [ -z "$FILE" ]; then
             echo "No Linux native runner found in target/"
             exit 1
           fi
           MAX_VER=$(strings -a "$FILE" | grep -oE 'GLIBC_[0-9]+\.[0-9]+' | sed 's/GLIBC_//' | sort -V | tail -n 1 || true)
           if [ -z "${MAX_VER}" ]; then
             echo "No GLIBC version symbols found, assuming OK"
             exit 0
@@ -70,34 +73,33 @@ jobs:
             echo "glibc requirement too high: ${MAX_VER} (> ${REQ})"
             exit 1
           fi
           echo "glibc baseline OK: ${MAX_VER} <= ${REQ}"
 
       - name: Upload Native Artifact
         uses: actions/upload-artifact@v4
         with:
-          name: quarkus-native-${{ matrix.os }}-${{ matrix.arch }}
+          name: quarkus-native-${{ matrix.os }}-${{ runner.arch }}
           path: |
             target/*-runner*
             target/*-runner.exe
           if-no-files-found: error
 
   release:
     name: Publish Release
     needs: [build]
     runs-on: ubuntu-22.04
     if: startsWith(github.ref, 'refs/tags/')
     steps:
       - name: Download all build artifacts
         uses: actions/download-artifact@v4
         with:
           path: dist
-          merge-multiple: true
 
       - name: Create GitHub Release
-        uses: softprops/action-gh-release@v1
+        uses: softprops/action-gh-release@v2
         with:
           files: dist/**
           tag_name: ${{ github.ref_name }}
           name: Release ${{ github.ref_name }}
           draft: false
           prerelease: false


---
commit c7abeb9978c2d7ff12273088d0c71a7941391faa
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 03:38:38 2026 +0800

    fix(delivery-6.2): 四个 Dockerfile EXPOSE 8080→18001，注释示例同步

diff --git a/src/main/docker/Dockerfile.jvm b/src/main/docker/Dockerfile.jvm
index 511b47f..47ac264 100644
--- a/src/main/docker/Dockerfile.jvm
+++ b/src/main/docker/Dockerfile.jvm
@@ -6,26 +6,26 @@
 # ./mvnw package
 #
 # Then, build the image with:
 #
 # docker build -f src/main/docker/Dockerfile.jvm -t quarkus/quarkus-template-new-jvm .
 #
 # Then run the container using:
 #
-# docker run -i --rm -p 8080:8080 quarkus/quarkus-template-new-jvm
+# docker run -i --rm -p 18001:18001 quarkus/quarkus-template-new-jvm
 #
 # If you want to include the debug port into your docker image
-# you will have to expose the debug port (default 5005 being the default) like this :  EXPOSE 8080 5005.
+# you will have to expose the debug port (default 5005 being the default) like this :  EXPOSE 18001 5005.
 # Additionally you will have to set -e JAVA_DEBUG=true and -e JAVA_DEBUG_PORT=*:5005
 # when running the container
 #
 # Then run the container using :
 #
-# docker run -i --rm -p 8080:8080 quarkus/quarkus-template-new-jvm
+# docker run -i --rm -p 18001:18001 quarkus/quarkus-template-new-jvm
 #
 # This image uses the `run-java.sh` script to run the application.
 # This scripts computes the command line to execute your Java application, and
 # includes memory/GC tuning.
 # You can configure the behavior using the following environment properties:
 # - JAVA_OPTS: JVM options passed to the `java` command (example: "-verbose:class")
 # - JAVA_OPTS_APPEND: User specified Java options to be appended to generated options
 #   in JAVA_OPTS (example: "-Dsome.property=foo")
@@ -83,14 +83,14 @@ ENV LANGUAGE='en_US:en'
 
 
 # We make four distinct layers so if there are application changes the library layers can be re-used
 COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/
 COPY --chown=185 target/quarkus-app/*.jar /deployments/
 COPY --chown=185 target/quarkus-app/app/ /deployments/app/
 COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/
 
-EXPOSE 8080
+EXPOSE 18001
 USER 185
 ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
 ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"
 
 ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
diff --git a/src/main/docker/Dockerfile.legacy-jar b/src/main/docker/Dockerfile.legacy-jar
index 8073f2f..bab6927 100644
--- a/src/main/docker/Dockerfile.legacy-jar
+++ b/src/main/docker/Dockerfile.legacy-jar
@@ -6,26 +6,26 @@
 # ./mvnw package -Dquarkus.package.jar.type=legacy-jar
 #
 # Then, build the image with:
 #
 # docker build -f src/main/docker/Dockerfile.legacy-jar -t quarkus/quarkus-template-new-legacy-jar .
 #
 # Then run the container using:
 #
-# docker run -i --rm -p 8080:8080 quarkus/quarkus-template-new-legacy-jar
+# docker run -i --rm -p 18001:18001 quarkus/quarkus-template-new-legacy-jar
 #
 # If you want to include the debug port into your docker image
-# you will have to expose the debug port (default 5005 being the default) like this :  EXPOSE 8080 5005.
+# you will have to expose the debug port (default 5005 being the default) like this :  EXPOSE 18001 5005.
 # Additionally you will have to set -e JAVA_DEBUG=true and -e JAVA_DEBUG_PORT=*:5005
 # when running the container
 #
 # Then run the container using :
 #
-# docker run -i --rm -p 8080:8080 quarkus/quarkus-template-new-legacy-jar
+# docker run -i --rm -p 18001:18001 quarkus/quarkus-template-new-legacy-jar
 #
 # This image uses the `run-java.sh` script to run the application.
 # This scripts computes the command line to execute your Java application, and
 # includes memory/GC tuning.
 # You can configure the behavior using the following environment properties:
 # - JAVA_OPTS: JVM options passed to the `java` command (example: "-verbose:class")
 # - JAVA_OPTS_APPEND: User specified Java options to be appended to generated options
 #   in JAVA_OPTS (example: "-Dsome.property=foo")
@@ -80,14 +80,14 @@
 FROM registry.access.redhat.com/ubi8/openjdk-21:1.20
 
 ENV LANGUAGE='en_US:en'
 
 
 COPY target/lib/* /deployments/lib/
 COPY target/*-runner.jar /deployments/quarkus-run.jar
 
-EXPOSE 8080
+EXPOSE 18001
 USER 185
 ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
 ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"
 
 ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
\ No newline at end of file
diff --git a/src/main/docker/Dockerfile.native b/src/main/docker/Dockerfile.native
index b11b9d3..443e4be 100644
--- a/src/main/docker/Dockerfile.native
+++ b/src/main/docker/Dockerfile.native
@@ -6,22 +6,22 @@
 # ./mvnw package -Dnative
 #
 # Then, build the image with:
 #
 # docker build -f src/main/docker/Dockerfile.native -t quarkus/quarkus-template-new .
 #
 # Then run the container using:
 #
-# docker run -i --rm -p 8080:8080 quarkus/quarkus-template-new
+# docker run -i --rm -p 18001:18001 quarkus/quarkus-template-new
 #
 ###
 FROM registry.access.redhat.com/ubi8/ubi-minimal:8.10
 WORKDIR /work/
 RUN chown 1001 /work \
     && chmod "g+rwX" /work \
     && chown 1001:root /work
 COPY --chown=1001:root target/*-runner /work/application
 
-EXPOSE 8080
+EXPOSE 18001
 USER 1001
 
 ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]
\ No newline at end of file
diff --git a/src/main/docker/Dockerfile.native-micro b/src/main/docker/Dockerfile.native-micro
index 194a2bd..a6c4ea5 100644
--- a/src/main/docker/Dockerfile.native-micro
+++ b/src/main/docker/Dockerfile.native-micro
@@ -9,22 +9,22 @@
 # ./mvnw package -Dnative
 #
 # Then, build the image with:
 #
 # docker build -f src/main/docker/Dockerfile.native-micro -t quarkus/quarkus-template-new .
 #
 # Then run the container using:
 #
-# docker run -i --rm -p 8080:8080 quarkus/quarkus-template-new
+# docker run -i --rm -p 18001:18001 quarkus/quarkus-template-new
 #
 ###
 FROM quay.io/quarkus/quarkus-micro-image:2.0
 WORKDIR /work/
 RUN chown 1001 /work \
     && chmod "g+rwX" /work \
     && chown 1001:root /work
 COPY --chown=1001:root target/*-runner /work/application
 
-EXPOSE 8080
+EXPOSE 18001
 USER 1001
 
 ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]
\ No newline at end of file


---
commit 430be9a8b18dbb577ff85221618984ff95d167b9
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 03:40:20 2026 +0800

    fix(delivery-6.3): commons-collections 换 hutool、删 beanutils 死依赖与 PojoUtils.merge

diff --git a/pom.xml b/pom.xml
index f8b9c17..ef59072 100644
--- a/pom.xml
+++ b/pom.xml
@@ -14,17 +14,16 @@
     <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
     <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
     <quarkus.platform.version>3.20.4</quarkus.platform.version>
     <skipITs>true</skipITs>
     <surefire-plugin.version>3.5.0</surefire-plugin.version>
     <hutool.version>5.8.12</hutool.version>
     <commons-lang3.version>3.20.0</commons-lang3.version>
     <commons-codec.version>1.13</commons-codec.version>
-    <commons-beanutils.version>1.9.4</commons-beanutils.version>
     <fastjson.version>1.2.78</fastjson.version>
     <lombok.version>1.18.36</lombok.version>
   </properties>
 
   <dependencyManagement>
     <dependencies>
       <dependency>
         <groupId>${quarkus.platform.group-id}</groupId>
@@ -87,21 +86,16 @@
       <artifactId>commons-lang3</artifactId>
       <version>${commons-lang3.version}</version>
     </dependency>
     <dependency>
       <groupId>commons-codec</groupId>
       <artifactId>commons-codec</artifactId>
       <version>${commons-codec.version}</version>
     </dependency>
-    <dependency>
-      <groupId>commons-beanutils</groupId>
-      <artifactId>commons-beanutils</artifactId>
-      <version>${commons-beanutils.version}</version>
-    </dependency>
     <dependency>
       <groupId>cn.hutool</groupId>
       <artifactId>hutool-all</artifactId>
       <version>${hutool.version}</version>
     </dependency>
     <dependency>
       <groupId>com.google.code.gson</groupId>
       <artifactId>gson</artifactId>
diff --git a/src/main/java/com/nip/common/utils/Assert.java b/src/main/java/com/nip/common/utils/Assert.java
index 8fe5561..6aeec8a 100644
--- a/src/main/java/com/nip/common/utils/Assert.java
+++ b/src/main/java/com/nip/common/utils/Assert.java
@@ -1,15 +1,15 @@
 package com.nip.common.utils;
 
+import cn.hutool.core.collection.CollUtil;
+import cn.hutool.core.map.MapUtil;
 import jakarta.annotation.Nullable;
-import org.apache.commons.collections.CollectionUtils;
 
 import java.util.Collection;
-import java.util.Collections;
 import java.util.Map;
 import java.util.function.Supplier;
 
 public abstract class Assert {
   public Assert() {
   }
 
   public static void state(boolean expression, String message) {
@@ -198,23 +198,23 @@ public abstract class Assert {
    * @deprecated
    */
   @Deprecated
   public static void noNullElements(@Nullable Object[] array) {
     noNullElements(array, "[Assertion failed] - this array must not contain any null elements");
   }
 
   public static void notEmpty(@Nullable Collection<?> collection, String message) {
-    if (CollectionUtils.isEmpty(collection)) {
+    if (CollUtil.isEmpty(collection)) {
       throw new IllegalArgumentException(message);
     }
   }
 
   public static void notEmpty(@Nullable Collection<?> collection, Supplier<String> messageSupplier) {
-    if (CollectionUtils.isEmpty(collection)) {
+    if (CollUtil.isEmpty(collection)) {
       throw new IllegalArgumentException(nullSafeGet(messageSupplier));
     }
   }
 
   /**
    * @deprecated
    */
   @Deprecated
@@ -240,23 +240,23 @@ public abstract class Assert {
           throw new IllegalArgumentException(nullSafeGet(messageSupplier));
         }
       }
     }
 
   }
 
   public static void notEmpty(@Nullable Map<?, ?> map, String message) {
-    if (CollectionUtils.isEmpty(Collections.singleton(map))) {
+    if (MapUtil.isEmpty(map)) {
       throw new IllegalArgumentException(message);
     }
   }
 
   public static void notEmpty(@Nullable Map<?, ?> map, Supplier<String> messageSupplier) {
-    if (CollectionUtils.isEmpty(Collections.singleton(map))) {
+    if (MapUtil.isEmpty(map)) {
       throw new IllegalArgumentException(nullSafeGet(messageSupplier));
     }
   }
 
   /**
    * @deprecated
    */
   @Deprecated
diff --git a/src/main/java/com/nip/common/utils/ObjectUtils.java b/src/main/java/com/nip/common/utils/ObjectUtils.java
index 45e6736..8cd6b5a 100644
--- a/src/main/java/com/nip/common/utils/ObjectUtils.java
+++ b/src/main/java/com/nip/common/utils/ObjectUtils.java
@@ -1,12 +1,12 @@
 package com.nip.common.utils;
 
+import cn.hutool.core.collection.CollUtil;
 import jakarta.annotation.Nullable;
-import org.apache.commons.collections.CollectionUtils;
 
 import java.lang.reflect.Array;
 import java.util.*;
 
 public abstract class ObjectUtils {
 
   private static final int INITIAL_HASH = 7;
   private static final int MULTIPLIER = 31;
@@ -90,18 +90,18 @@ public abstract class ObjectUtils {
    * <p>If the given object is non-null and not one of the aforementioned
    * supported types, this method returns {@code false}.
    *
    * @param obj the object to check
    * @return {@code true} if the object is {@code null} or <em>empty</em>
    * @see Optional#isPresent()
    * @see ObjectUtils#isEmpty(Object[])
    * @see StringUtils#hasLength(CharSequence)
-   * @see CollectionUtils#isEmpty(Collection)
-   * @see CollectionUtils#isEmpty(Map)
+   * @see CollUtil#isEmpty(Collection)
+   * @see CollUtil#isEmpty(Map)
    * @since 4.2
    */
   public static boolean isEmpty(@Nullable Object obj) {
     if (obj == null) {
       return true;
     }
 
     if (obj instanceof Optional) {
diff --git a/src/main/java/com/nip/common/utils/PojoUtils.java b/src/main/java/com/nip/common/utils/PojoUtils.java
index 90ba62a..38fe9ea 100644
--- a/src/main/java/com/nip/common/utils/PojoUtils.java
+++ b/src/main/java/com/nip/common/utils/PojoUtils.java
@@ -2,17 +2,16 @@ package com.nip.common.utils;
 
 import cn.hutool.core.bean.BeanUtil;
 import cn.hutool.core.bean.copier.CopyOptions;
 import cn.hutool.core.collection.CollUtil;
 import com.nip.common.Page;
 import com.nip.common.PageInfo;
 import io.quarkus.hibernate.orm.panache.PanacheQuery;
 import lombok.extern.slf4j.Slf4j;
-import org.apache.commons.beanutils.BeanUtils;
 
 import java.lang.reflect.InvocationTargetException;
 import java.util.ArrayList;
 import java.util.List;
 import java.util.function.BiConsumer;
 
 /**
  * @author wushilin
@@ -135,17 +134,9 @@ public class PojoUtils {
     }
 
     return result;
   }
 
   private static int calculateSplitNum(int totalSize, int splitItemNnm) {
     return totalSize % splitItemNnm == 0 ? totalSize / splitItemNnm : totalSize / splitItemNnm + 1;
   }
-
-  public static <T> void merge(T user1, T user2) {
-    try {
-      BeanUtils.copyProperties(user1, user2);
-    } catch (Exception e) {
-      throw new IllegalArgumentException("Failed to merge users", e);
-    }
-  }
 }
diff --git a/src/main/java/com/nip/common/utils/StringUtils.java b/src/main/java/com/nip/common/utils/StringUtils.java
index 713026c..356e9d3 100644
--- a/src/main/java/com/nip/common/utils/StringUtils.java
+++ b/src/main/java/com/nip/common/utils/StringUtils.java
@@ -1,12 +1,12 @@
 package com.nip.common.utils;
 
+import cn.hutool.core.collection.CollUtil;
 import jakarta.annotation.Nullable;
-import org.apache.commons.collections.CollectionUtils;
 
 import java.io.ByteArrayOutputStream;
 import java.nio.charset.Charset;
 import java.util.*;
 
 public abstract class StringUtils {
   private static final String[] EMPTY_STRING_ARRAY = new String[0];
   private static final String FOLDER_SEPARATOR = "/";
@@ -511,17 +511,17 @@ public abstract class StringUtils {
     if ("GMT".equals(timeZone.getID()) && !timeZoneString.startsWith("GMT")) {
       throw new IllegalArgumentException("Invalid time zone specification '" + timeZoneString + "'");
     } else {
       return timeZone;
     }
   }
 
   public static String[] toStringArray(@Nullable Collection<String> collection) {
-    return !CollectionUtils.isEmpty(collection) ? (String[]) collection.toArray(EMPTY_STRING_ARRAY) : EMPTY_STRING_ARRAY;
+    return !CollUtil.isEmpty(collection) ? (String[]) collection.toArray(EMPTY_STRING_ARRAY) : EMPTY_STRING_ARRAY;
   }
 
   public static String[] toStringArray(@Nullable Enumeration<String> enumeration) {
     return enumeration != null ? toStringArray(Collections.list(enumeration)) : EMPTY_STRING_ARRAY;
   }
 
   public static String[] addStringToArray(@Nullable String[] array, String str) {
     if (ObjectUtils.isEmpty(array)) {
@@ -709,17 +709,17 @@ public abstract class StringUtils {
   }
 
   public static Set<String> commaDelimitedListToSet(@Nullable String str) {
     String[] tokens = commaDelimitedListToStringArray(str);
     return new LinkedHashSet(Arrays.asList(tokens));
   }
 
   public static String collectionToDelimitedString(@Nullable Collection<?> coll, String delim, String prefix, String suffix) {
-    if (CollectionUtils.isEmpty(coll)) {
+    if (CollUtil.isEmpty(coll)) {
       return "";
     } else {
       StringBuilder sb = new StringBuilder();
       Iterator<?> it = coll.iterator();
 
       while (it.hasNext()) {
         sb.append(prefix).append(it.next()).append(suffix);
         if (it.hasNext()) {


---
commit 32c318aa8edac266ba47efe2f444fbcc253e6a4d
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 03:40:43 2026 +0800

    fix(delivery-6.4): 评审文档勘误标注（common-build 27→35、controller-api 52→63）

diff --git a/docs/reviews/2026-08-26-common-build-review.md b/docs/reviews/2026-08-26-common-build-review.md
index a565b12..a9aa599 100644
--- a/docs/reviews/2026-08-26-common-build-review.md
+++ b/docs/reviews/2026-08-26-common-build-review.md
@@ -4,16 +4,18 @@
 **审查方式**：纯静态阅读（本机无 Java 环境，未执行任何构建/测试），CVE 经 NVD / GitHub Advisory 联网核对。
 
 ---
 
 ## 结论
 
 **共 27 条问题：P0 = 0，P1 = 7，P2 = 16，P3 = 4。**另有 8 条纯安全项按内网口径列入附录，不计入上述总数。
 
+> **〔勘误 2026-08-27〕** 上行"共 27 条"为计数错误：本文正文实际列出 **35 条**（P1 = 7、P2 = 24、P3 = 4）。见 `2026-08-26-review-audit.md` §4（"common-build 分片页首 '27 条'，正文实为 35 条"）。原文保留不改，以此标注为准。
+
 最需要立刻处理的三件事：
 
 1. **`TickerPatUtils.resolverMessage` 有 4 处独立缺陷**，其中「moresTime / moresValue 两列互换」走的是最常见的普通组分支，等于所有拍发训练的「电划耗时」和「点划表示」两列在落库时是反的。
 2. **CI 的 ARM64 构建必然失败**（给 aarch64 的 native-image 传了 `-march=x86-64`），且 `release` job 永远不会被触发（workflow 根本不监听 tag push）——也就是说这条流水线目前只有 x86_64 Linux + Windows 两个 job 真正有效，发布环节是死的。
 3. **`commons-collections 3.2.2` 被 3 个 common 工具类直接 import，但 pom.xml 里没有声明**，它是靠 `commons-beanutils` 传递进来的；而 `commons-beanutils` 本身只服务于一个从未被调用的方法。想删掉 beanutils 或升级它修 CVE-2025-48734，会直接导致 `Assert` / `ObjectUtils` / `StringUtils` 编译失败。
 
 另需说明：任务书写的是「common/ 12 个文件」，实际 `com/nip/common/` 下有 70 余个 `.java`（仅 `utils/` 就有 34 个）。本报告覆盖了其中的基础设施类与通用工具类；`repository/BaseRepository`、`specification/*` 已与 `Persistence` agent 约定由其负责，本报告不重复。
 
diff --git a/docs/reviews/2026-08-26-controller-api-review.md b/docs/reviews/2026-08-26-controller-api-review.md
index 24d007f..eb68541 100644
--- a/docs/reviews/2026-08-26-controller-api-review.md
+++ b/docs/reviews/2026-08-26-controller-api-review.md
@@ -1,14 +1,16 @@
 # API 层（controller + dto）代码审查报告
 
 **结论：共 34 个问题 —— P0 1 个、P1 8 个、P2 19 个、P3 6 个。** 另有 5 项纯安全问题按内网口径记入附录，不计入上述统计。
 
 审查范围：`src/main/java/com/nip/controller/`（52 个文件，全部逐行读过）与 `src/main/java/com/nip/dto/`（75 个文件）。service 内部逻辑、dao 不在范围内，仅在需要作为证据链时引用。本机无 Java 环境，全部为静态阅读，未运行任何构建或测试。
 
+> **〔勘误 2026-08-27〕** 上行"52 个文件"为口径错误：`controller/` 下实为 **63 个 Controller 类 / 64 个 .java 文件**（含 `test/Test.java` 非 controller 实验类），52 是标注 `@JWT` 的类数，疑将两者混淆。见 `2026-08-26-review-audit.md` §4 对照表。原文保留不改，以此标注为准。
+
 最要命的三件事，按顺序读：
 
 1. **`/test/start` 是一个无鉴权的 GET，直接 UPDATE 生产库里一条写死 ID 的训练记录**（P0-1）。Swagger UI 在生产是开启的，任何人点一下 "Try it out" 就把那条记录的状态、时间、成绩永久覆盖掉。
 2. **全项目没有任何 `ExceptionMapper`**（P1-6）。控制器里有 11 处 `Integer.parseInt(map.get(...))` / `map.get(...).toString()` / 强制类型转换，参数缺失或类型不对时直接抛异常，Quarkus 兜底返回默认错误页，`Response{code,data,message}` 这层统一封装在错误路径上完全失效——前端拿到的不是约定的结构。
 3. **分页参数没有任何校验，默认值本身就是坏的**（P1-5）。`Page.page` 默认 0，而所有消费方都写 `page.getPage() - 1`，于是不传 page 就是 `-1` → Hibernate/Panache 抛 `IllegalArgumentException` → 500；`rows` 传 0 会在 `SpecificationExecutor.findPage` 里除零；`rows` 没有上限，传 10000000 就是全表加载。
 
 关于两个检查项的说明：
 


---
commit b29dd1188616bbc8b5ba847a5ae60e1bc8a1805e
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 03:41:28 2026 +0800

    docs(delivery): Phase 6 报告落盘

diff --git a/.superpowers/sdd/2026-08-26-fix-plan/phase-6-report.md b/.superpowers/sdd/2026-08-26-fix-plan/phase-6-report.md
new file mode 100644
index 0000000..e694230
--- /dev/null
+++ b/.superpowers/sdd/2026-08-26-fix-plan/phase-6-report.md
@@ -0,0 +1,48 @@
+# Phase 6 报告：交付链路
+
+结论：4/4 子 Task 完成，各一次提交。actionlint 全绿、docker build 通过、test-compile 通过、依赖树确认 beanutils/commons-collections 已消失。
+
+## Task 6.1 — workflow（提交 1fc4dab）
+
+改动 `.github/workflows/build-quarkus-native.yml`：
+- `on.push` 增加 `tags: ['v*']`（release job 原先永不触发的死路径打通）。
+- Linux native 构建按架构拆分：matrix include 增加 `native_march` 键（x86_64→`x86-64`，arm64→`armv8-a`），构建命令改为 `-Dquarkus.native.march=${{ matrix.native_march }}`，删除与之冲突的 `-Dquarkus.native.additional-build-args=-march=x86-64`（原先 ARM64 job 必失败的根因）。
+- 产物名改为 `quarkus-native-${{ matrix.os }}-${{ runner.arch }}`。
+- 连带修正：release job 的 `download-artifact` 去掉 `merge-multiple: true`——两个 Linux 架构的二进制同名 `*-runner`，合并解包会互相覆盖，改为默认按 artifact 名分目录解包。
+- actionlint 报出的两处存量问题一并修（否则无法全绿退出）：SC2012（`ls`→`find`）、`softprops/action-gh-release@v1`→`@v2`（v1 runner 过旧）。
+
+**校验方式**：本机无 actionlint 二进制，按预案用 `docker run --rm -v $PWD:/repo -w /repo rhysd/actionlint:latest`。首轮报 2 处存量问题（见上），修复后复跑 **exit=0**。
+
+## Task 6.2 — Dockerfile EXPOSE（提交 c7abeb9）
+
+- brief 点名的 `Dockerfile.jvm:91`、`Dockerfile.native:24` EXPOSE 8080→18001。
+- 同目录 `Dockerfile.legacy-jar`、`Dockerfile.native-micro` 有完全相同缺陷（域内文件），一并修正；4 个文件注释里的 `docker run -p 8080:8080` 示例同步改 18001。应用端口 18001 已对 `application.yml:10` 核实。
+- **证据**：`flock /tmp/omp-mvn.lock -c "./mvnw -B package -DskipTests"` exit=0 产出 fast-jar；`docker build -f src/main/docker/Dockerfile.jvm .` exit=0；`docker inspect` ExposedPorts = `{"18001/tcp":{},"8080/tcp":{},"8443/tcp":{}}`——8080/8443 来自 ubi8/openjdk-21 基础镜像自身的 EXPOSE，下游 Dockerfile 无法移除，非缺陷。
+
+## Task 6.3 — BeanUtils 清理（提交 430be9a）
+
+- 全仓 grep 确认受影响面仅 4 个 Java 文件（`Assert`/`ObjectUtils`/`StringUtils` import commons-collections 3.x；`PojoUtils` import beanutils），远低于 10 文件阈值 → 走完整替代路线，未降级。
+- `Assert.java`：`CollectionUtils.isEmpty(Collection)`→hutool `CollUtil.isEmpty`；两个 Map 重载原文是 `CollectionUtils.isEmpty(Collections.singleton(map))`（singleton 恒非空 → 断言恒不触发的死逻辑），改为 `MapUtil.isEmpty(map)` 恢复语义。风险为零：`Assert.notEmpty(` 全仓无调用点。
+- `StringUtils.java`（2 处）→`CollUtil.isEmpty`；`ObjectUtils.java` 仅 javadoc `@see` 引用 → 改指向 `CollUtil`。
+- `PojoUtils.merge`（BeanUtils 唯一调用点，全仓零调用者）连同 import 删除；pom 删 `commons-beanutils` 依赖及 `commons-beanutils.version` 属性。未显式声明 commons-collections——三处使用已全部换掉，无需声明。
+- **证据**：`flock ... ./mvnw -B test-compile` exit=0；`dependency:tree -Dincludes='commons-beanutils:*,commons-collections:*'` 输出为空（CVE-2025-48734 暴露面消除）。
+
+## Task 6.4 — 文档勘误（提交 32c318a）
+
+- `2026-08-26-common-build-review.md` 页首"共 27 条"后加勘误 blockquote：正文实为 35 条（P1=7、P2=24、P3=4，依据 audit §4）。原文未改。
+- `2026-08-26-controller-api-review.md` scope"52 个文件"后加勘误 blockquote：实为 63 个 Controller 类 / 64 个 .java（52 是 @JWT 类数，依据 audit §4；64 经本机实测计数复核）。原文未改。
+
+## 提交清单
+
+| Task | 提交 | 内容 |
+|---|---|---|
+| 6.1 | 1fc4dab | workflow tags 触发 + 按架构 march + 产物名 arch + actionlint 全绿 |
+| 6.2 | c7abeb9 | 4 个 Dockerfile EXPOSE 18001 |
+| 6.3 | 430be9a | commons-collections→hutool、删 beanutils |
+| 6.4 | 32c318a | 两份评审文档勘误标注 |
+
+## Concerns
+
+1. `package -DskipTests` 与 `test-compile` 在兄弟 agent 并发改源码的工作区通过——通过时点的快照有效性以合流后全量 verify 为准。
+2. workflow 的 ARM64 job（`ubuntu-24.04-arm` + mandrel 容器构建）本地无法实跑，`-march=armv8-a` 取值依据评审文档所列 aarch64 合法值；需首次 tag push 实测。
+3. release job 下载布局改为按 artifact 名分目录（去 merge-multiple），`files: dist/**` 语义不变，但发布产物路径会多一层目录名——如有下游脚本依赖平铺布局需知悉。

