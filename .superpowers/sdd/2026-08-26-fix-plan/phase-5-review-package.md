## commits
6635da5
2c1be29
a6e0c13
3c983a0
f823a33


---
commit 6635da59a56edd7f53164f3f2cc9e169b713088c
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 03:52:48 2026 +0800

    fix(persist-5.1): findTwoPage 误用页主键 id 过滤改为 trainId（P0-2/P1-1）

diff --git a/src/main/java/com/nip/dao/general/key/GeneralKeyPatPageDao.java b/src/main/java/com/nip/dao/general/key/GeneralKeyPatPageDao.java
index 19a14ff..f34b141 100644
--- a/src/main/java/com/nip/dao/general/key/GeneralKeyPatPageDao.java
+++ b/src/main/java/com/nip/dao/general/key/GeneralKeyPatPageDao.java
@@ -18,18 +18,19 @@ public class GeneralKeyPatPageDao extends BaseRepository<GeneralKeyPatPageEntity
     return find("trainId = ?1 and pageNumber = ?2", Sort.by("sort").ascending(), trainId, pageNumber).list();
   }
 
   public List<Integer> countPageNumber(Integer trainId) {
     return find("trainId = ?1 group by pageNumber", trainId).list()
         .stream().map(GeneralKeyPatPageEntity::getPageNumber).toList();
   }
 
-  public List<GeneralKeyPatPageEntity> findTwoPage(Integer id) {
-    return find("id = ?1 and (pageNumber = 1 or pageNumber =2)", Sort.by("pageNumber").ascending(), id).list();
+  public List<GeneralKeyPatPageEntity> findTwoPage(Integer trainId) {
+    return find("trainId = ?1 and (pageNumber = 1 or pageNumber = 2)",
+        Sort.by("pageNumber").ascending().and("sort"), trainId).list();
   }
 
   public List<GeneralKeyPatPageEntity> findByTrainId(Integer trainId) {
     return find("trainId = ?1", trainId).list();
   }
   public Integer findMaxPageNumber(Integer trainId) {
     return entityManager.createQuery(
         "select max(pageNumber) from general_key_pat_page where trainId =:trainId",
diff --git a/src/main/java/com/nip/dao/general/telex/GeneralTelexPatPageDao.java b/src/main/java/com/nip/dao/general/telex/GeneralTelexPatPageDao.java
index 4fcefdd..b56d057 100644
--- a/src/main/java/com/nip/dao/general/telex/GeneralTelexPatPageDao.java
+++ b/src/main/java/com/nip/dao/general/telex/GeneralTelexPatPageDao.java
@@ -14,15 +14,16 @@ public class GeneralTelexPatPageDao extends BaseRepository<GeneralTelexPatPageEn
         "select max(pageNumber) from general_telex_pat_page where trainId =:trainId",
         Integer.class
     ).setParameter("trainId", trainId).setMaxResults(1).getSingleResult();
   }
   public List<Integer> countPageNumber(String trainId) {
     return find("trainId = ?1 group by pageNumber order by pageNumber", trainId).list()
         .stream().map(GeneralTelexPatPageEntity::getPageNumber).toList();
   }
-  public List<GeneralTelexPatPageEntity> findTwoPage(String id) {
-    return find("id = ?1 and (pageNumber = 1 or pageNumber =2)", Sort.by("pageNumber").ascending(), id).list();
+  public List<GeneralTelexPatPageEntity> findTwoPage(String trainId) {
+    return find("trainId = ?1 and (pageNumber = 1 or pageNumber = 2)",
+        Sort.by("pageNumber").ascending().and("sort"), trainId).list();
   }
   public List<GeneralTelexPatPageEntity> findByTrainIdAndPageNumberOrderBySort(String trainId, Integer pageNumber) {
     return find("trainId = ?1 and pageNumber = ?2", Sort.by("sort").ascending(), trainId, pageNumber).list();
   }
 }
diff --git a/src/test/java/com/nip/dao/PatPageFindTwoPageDaoTest.java b/src/test/java/com/nip/dao/PatPageFindTwoPageDaoTest.java
new file mode 100644
index 0000000..e388231
--- /dev/null
+++ b/src/test/java/com/nip/dao/PatPageFindTwoPageDaoTest.java
@@ -0,0 +1,77 @@
+package com.nip.dao;
+
+import com.nip.dao.general.key.GeneralKeyPatPageDao;
+import com.nip.dao.general.telex.GeneralTelexPatPageDao;
+import com.nip.entity.simulation.key.GeneralKeyPatPageEntity;
+import com.nip.entity.simulation.telex.GeneralTelexPatPageEntity;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import java.util.List;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.assertTrue;
+
+/**
+ * P0-2 / P1-1（Task 5.1）：findTwoPage 曾误用页记录主键 id 过滤，
+ * 而调用方（GeneralKeyPatService:833 / GeneralTelexPatService:279）传的是 trainId，
+ * 导致"拍发详情"报底内容恒为空。
+ * 防守契约：findTwoPage(trainId) 只返回目标 train 的第 1、2 页，按页码升序。
+ */
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class PatPageFindTwoPageDaoTest {
+  @Inject GeneralKeyPatPageDao keyPageDao;
+  @Inject GeneralTelexPatPageDao telexPageDao;
+
+  private static GeneralKeyPatPageEntity keyPage(Integer trainId, Integer pageNumber) {
+    return new GeneralKeyPatPageEntity()
+        .setTrainId(trainId).setPageNumber(pageNumber).setSort(0)
+        .setKey("k" + pageNumber).setValue("v" + pageNumber);
+  }
+
+  private static GeneralTelexPatPageEntity telexPage(String trainId, Integer pageNumber) {
+    return new GeneralTelexPatPageEntity()
+        .setTrainId(trainId).setPageNumber(pageNumber).setSort(0)
+        .setKey("k" + pageNumber).setValue("v" + pageNumber);
+  }
+
+  @Test
+  void keyPatFindTwoPageReturnsOnlyTargetTrainPages() {
+    keyPageDao.save(keyPage(9101, 1));
+    keyPageDao.save(keyPage(9101, 2));
+    keyPageDao.save(keyPage(9101, 3)); // 第 3 页不属于"前两页"
+    keyPageDao.save(keyPage(9202, 1));
+    keyPageDao.save(keyPage(9202, 2));
+
+    List<GeneralKeyPatPageEntity> pages = keyPageDao.findTwoPage(9101);
+
+    assertEquals(2, pages.size(), "只应返回目标 train 的第 1、2 页");
+    assertTrue(pages.stream().allMatch(p -> p.getTrainId().equals(9101)),
+        "不得混入其他 train 的页");
+    assertEquals(List.of(1, 2),
+        pages.stream().map(GeneralKeyPatPageEntity::getPageNumber).toList(),
+        "页码必须为升序的 1、2");
+  }
+
+  @Test
+  void telexPatFindTwoPageReturnsOnlyTargetTrainPages() {
+    telexPageDao.save(telexPage("p51-train-a", 1));
+    telexPageDao.save(telexPage("p51-train-a", 2));
+    telexPageDao.save(telexPage("p51-train-a", 3));
+    telexPageDao.save(telexPage("p51-train-b", 1));
+    telexPageDao.save(telexPage("p51-train-b", 2));
+
+    List<GeneralTelexPatPageEntity> pages = telexPageDao.findTwoPage("p51-train-a");
+
+    assertEquals(2, pages.size(), "只应返回目标 train 的第 1、2 页");
+    assertTrue(pages.stream().allMatch(p -> "p51-train-a".equals(p.getTrainId())),
+        "不得混入其他 train 的页");
+    assertEquals(List.of(1, 2),
+        pages.stream().map(GeneralTelexPatPageEntity::getPageNumber).toList(),
+        "页码必须为升序的 1、2");
+  }
+}


---
commit 2c1be294f1a961161cc37846d12cb5d3c88de372
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 03:52:48 2026 +0800

    fix(persist-5.2): saveFloorContent HQL 蛇形列名改驼峰；空 IN 判空、双引号字面量、聚合 ORDER BY 修正

diff --git a/src/main/java/com/nip/dao/MilitaryTermDataDao.java b/src/main/java/com/nip/dao/MilitaryTermDataDao.java
index 208fa03..b23f9b7 100644
--- a/src/main/java/com/nip/dao/MilitaryTermDataDao.java
+++ b/src/main/java/com/nip/dao/MilitaryTermDataDao.java
@@ -14,20 +14,26 @@ public class MilitaryTermDataDao extends BaseRepository<MilitaryTermDataEntity,
     return find("parentId", parentId).list();
   }
 
   public List<MilitaryTermDataEntity> findAllByParentIdOrderBySort(String parentId) {
     return find("parentId = ?1 order by sort", parentId).list();
   }
 
   public List<MilitaryTermDataEntity> findAllByParentIdIn(List<String> ids) {
+    if (ids == null || ids.isEmpty()) {
+      return List.of();
+    }
     return find("parentId in (?1)", ids).list();
   }
 
   public List<MilitaryTermDataEntity> findAllByIdIn(List<String> ids) {
+    if (ids == null || ids.isEmpty()) {
+      return List.of();
+    }
     return find("id in (?1)", ids).list();
   }
 
   public Integer findByParentIdMaxSort(String parentId) {
     return entityManager.createQuery("select max(sort) from t_military_term_data where parentId =:parentId",
                                      Integer.class
     ).setParameter("parentId", parentId).setMaxResults(1).getSingleResult();
   }
diff --git a/src/main/java/com/nip/dao/PostTelegramTrainFloorContentDao.java b/src/main/java/com/nip/dao/PostTelegramTrainFloorContentDao.java
index 5f04a30..a7ae8cc 100644
--- a/src/main/java/com/nip/dao/PostTelegramTrainFloorContentDao.java
+++ b/src/main/java/com/nip/dao/PostTelegramTrainFloorContentDao.java
@@ -16,17 +16,17 @@ public class PostTelegramTrainFloorContentDao
 
   @Transactional
   public void updateById(String id, String value, String time, String keys) {
     update("moresValue = ?2,moresTime = ?3 ,patKeys = ?4 where id = ?1", id, value, time, keys);
   }
 
   @Transactional
   public void clearByTranId(String tranId) {
-    update("moresValue=\"[]\",moresTime=\"[]\",patKeys=\"[]\"  where trainId = ?1", tranId);
+    update("moresValue='[]',moresTime='[]',patKeys='[]' where trainId = ?1", tranId);
   }
 
   public List<PostTelegramTrainFloorContentEntity> findByFloorNumberAndTrainIdOrderBySort(Integer floor, String id) {
     return find("floorNumber = ?1 and trainId =?2 order by sort", floor, id).list();
   }
 
   public List<PostTelegramTrainFloorContentEntity> findByTrainIdOrderByFloorNumberSort(String tranId) {
     return find("trainId = ?1 order by floorNumber, sort", tranId).list();
@@ -57,17 +57,17 @@ public class PostTelegramTrainFloorContentDao
     query.setFirstResult(number);
     query.setMaxResults(pageSize);
     return query.getResultList();
   }
 
   public Integer findCountByTrainIdOrderByFloorNumberAscSortAsc(String id) {
     List<Long> resultList = entityManager.createQuery("select count(id) " +
             "from t_post_telegram_train_floor_content " +
-            "where trainId=?1 order by floorNumber , sort ", Long.class)
+            "where trainId=?1", Long.class)
         .setParameter(1, id).getResultList();
     if (resultList == null || resultList.isEmpty()) {
       return 0;
     } else {
       Long firstElement = resultList.getFirst();
       return firstElement != null ? firstElement.intValue() : 0;
     }
   }
diff --git a/src/main/java/com/nip/service/TelegramTrainService.java b/src/main/java/com/nip/service/TelegramTrainService.java
index bacd408..b838245 100644
--- a/src/main/java/com/nip/service/TelegramTrainService.java
+++ b/src/main/java/com/nip/service/TelegramTrainService.java
@@ -298,17 +298,17 @@ public class TelegramTrainService {
     } catch (Exception e) {
       return ResponseResult.error();
     }
   }
 
   @Transactional
   public Response<Void> saveFloorContent(Map<String, String> map) {
     try {
-      telegramTrainFloorContentDao.update("mores_value=?1,morse_time=?2 where id = ?3", map.get("moresValue"),
+      telegramTrainFloorContentDao.update("moresValue=?1,moresTime=?2 where id = ?3", map.get("moresValue"),
           CharSequenceUtil.isEmpty(map.get("moresTime")) ? "[]" : map.get("moresTime"),
           map.get(ID)
       );
       return ResponseResult.success();
     } catch (Exception e) {
       log.error("saveFloorContent error", e);
       return ResponseResult.error();
     }
diff --git a/src/test/java/com/nip/dao/MilitaryTermDataDaoTest.java b/src/test/java/com/nip/dao/MilitaryTermDataDaoTest.java
new file mode 100644
index 0000000..ec30d4c
--- /dev/null
+++ b/src/test/java/com/nip/dao/MilitaryTermDataDaoTest.java
@@ -0,0 +1,47 @@
+package com.nip.dao;
+
+import com.nip.entity.MilitaryTermDataEntity;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import java.util.List;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.assertTrue;
+
+/**
+ * Task 5.2（persistence 分片 P2-7）：in (?1) 对空/null 集合无判空防护。
+ * 契约：空集合与 null 一律安全返回空列表，非空集合正常命中。
+ */
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class MilitaryTermDataDaoTest {
+  @Inject MilitaryTermDataDao dao;
+
+  @Test
+  void inQueriesReturnEmptyListForEmptyOrNullInput() {
+    assertTrue(dao.findAllByIdIn(List.of()).isEmpty(), "空 id 集合必须安全返回空列表");
+    assertTrue(dao.findAllByParentIdIn(List.of()).isEmpty(), "空 parentId 集合必须安全返回空列表");
+    assertTrue(dao.findAllByIdIn(null).isEmpty(), "null id 集合必须安全返回空列表");
+    assertTrue(dao.findAllByParentIdIn(null).isEmpty(), "null parentId 集合必须安全返回空列表");
+  }
+
+  @Test
+  void inQueriesStillMatchWithNonEmptyInput() {
+    MilitaryTermDataEntity a = dao.save(new MilitaryTermDataEntity()
+        .setParentId("p52-in-parent").setKey("p52-in-a").setValue("va").setSort(0));
+    dao.save(new MilitaryTermDataEntity()
+        .setParentId("p52-in-other").setKey("p52-in-b").setValue("vb").setSort(0));
+
+    List<MilitaryTermDataEntity> byId = dao.findAllByIdIn(List.of(a.getId()));
+    assertEquals(1, byId.size());
+    assertEquals(a.getId(), byId.getFirst().getId());
+
+    List<MilitaryTermDataEntity> byParent = dao.findAllByParentIdIn(List.of("p52-in-parent"));
+    assertEquals(1, byParent.size());
+    assertEquals("p52-in-a", byParent.getFirst().getKey());
+  }
+}
diff --git a/src/test/java/com/nip/dao/PostTelegramTrainFloorContentDaoTest.java b/src/test/java/com/nip/dao/PostTelegramTrainFloorContentDaoTest.java
new file mode 100644
index 0000000..421e3c3
--- /dev/null
+++ b/src/test/java/com/nip/dao/PostTelegramTrainFloorContentDaoTest.java
@@ -0,0 +1,70 @@
+package com.nip.dao;
+
+import com.nip.entity.PostTelegramTrainFloorContentEntity;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import java.util.List;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.assertFalse;
+
+/**
+ * Task 5.2（persistence 分片 P2-11 / P2-12）：
+ * - clearByTranId 曾用双引号包 HQL 字符串字面量（HQL 双引号是"带引号的标识符"，不是字符串）。
+ * - findCountByTrainIdOrderByFloorNumberAscSortAsc 曾在无 GROUP BY 的聚合查询上带非聚合 ORDER BY，
+ *   MySQL 8 默认 sql_mode=ONLY_FULL_GROUP_BY 直接拒绝。
+ */
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class PostTelegramTrainFloorContentDaoTest {
+  @Inject PostTelegramTrainFloorContentDao dao;
+
+  private static PostTelegramTrainFloorContentEntity content(String trainId, int floor, int sort) {
+    PostTelegramTrainFloorContentEntity e = new PostTelegramTrainFloorContentEntity();
+    e.setTrainId(trainId);
+    e.setFloorNumber(floor);
+    e.setSort(sort);
+    e.setMoresKey("key-" + floor + "-" + sort);
+    e.setMoresValue("[\"x\"]");
+    e.setMoresTime("[100]");
+    e.setPatKeys("[\"p\"]");
+    return e;
+  }
+
+  @Test
+  void clearByTranIdResetsContentToEmptyJsonArrays() {
+    dao.save(content("p52-clear", 1, 0));
+    dao.save(content("p52-clear", 1, 1));
+    dao.save(content("p52-keep", 1, 0)); // 其他 train 不受影响
+
+    dao.clearByTranId("p52-clear");
+
+    List<PostTelegramTrainFloorContentEntity> cleared =
+        dao.findByTrainIdOrderByFloorNumberSort("p52-clear");
+    assertEquals(2, cleared.size());
+    for (PostTelegramTrainFloorContentEntity e : cleared) {
+      assertEquals("[]", e.getMoresValue(), "moresValue 必须被清为 []");
+      assertEquals("[]", e.getMoresTime(), "moresTime 必须被清为 []");
+      assertEquals("[]", e.getPatKeys(), "patKeys 必须被清为 []");
+    }
+
+    List<PostTelegramTrainFloorContentEntity> kept =
+        dao.findByTrainIdOrderByFloorNumberSort("p52-keep");
+    assertEquals(1, kept.size());
+    assertFalse("[]".equals(kept.getFirst().getMoresValue()), "其他 train 的内容不得被清除");
+  }
+
+  @Test
+  void countByTrainIdReturnsRowCount() {
+    dao.save(content("p52-count", 1, 0));
+    dao.save(content("p52-count", 1, 1));
+    dao.save(content("p52-count", 2, 0));
+
+    assertEquals(3, dao.findCountByTrainIdOrderByFloorNumberAscSortAsc("p52-count"));
+    assertEquals(0, dao.findCountByTrainIdOrderByFloorNumberAscSortAsc("p52-count-none"));
+  }
+}
diff --git a/src/test/java/com/nip/service/TelegramTrainServiceTest.java b/src/test/java/com/nip/service/TelegramTrainServiceTest.java
new file mode 100644
index 0000000..8eb61ba
--- /dev/null
+++ b/src/test/java/com/nip/service/TelegramTrainServiceTest.java
@@ -0,0 +1,63 @@
+package com.nip.service;
+
+import com.nip.common.constants.ResponseCode;
+import com.nip.common.response.Response;
+import com.nip.dao.TelegramTrainFloorContentDao;
+import com.nip.entity.TelegramTrainFloorContentEntity;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import java.util.Map;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.assertNotNull;
+
+/**
+ * Task 5.2：saveFloorContent 的 HQL 曾写成蛇形列名 mores_value/morse_time
+ * （非实体属性名，且 morse 还是拼错的 mores），更新永远失败并被 catch 吞成 error 响应。
+ * 正确参照同类 controlTelegramTrain 内联更新（TelegramTrainService:205 的 moresValue）。
+ */
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class TelegramTrainServiceTest {
+  @Inject TelegramTrainService service;
+  @Inject TelegramTrainFloorContentDao contentDao;
+
+  @Test
+  void saveFloorContentUpdatesMoresValueAndTime() {
+    TelegramTrainFloorContentEntity e = new TelegramTrainFloorContentEntity();
+    e.setFloorId("p52-floor");
+    e.setSort(0);
+    e.setMoresKey("k");
+    e = contentDao.save(e);
+
+    Response<Void> resp = service.saveFloorContent(
+        Map.of("id", e.getId(), "moresValue", "[\"A\"]", "moresTime", "[123]"));
+
+    assertEquals(ResponseCode.SUCCESS.getCode(), resp.getCode(), "更新必须成功而不是被吞成 error");
+    TelegramTrainFloorContentEntity reloaded = contentDao.findById(e.getId());
+    assertNotNull(reloaded);
+    assertEquals("[\"A\"]", reloaded.getMoresValue(), "moresValue 必须已更新");
+    assertEquals("[123]", reloaded.getMoresTime(), "moresTime 必须已更新");
+  }
+
+  @Test
+  void saveFloorContentDefaultsEmptyMoresTimeToEmptyJsonArray() {
+    TelegramTrainFloorContentEntity e = new TelegramTrainFloorContentEntity();
+    e.setFloorId("p52-floor2");
+    e.setSort(0);
+    e.setMoresKey("k");
+    e.setMoresTime("[9]");
+    e = contentDao.save(e);
+
+    Response<Void> resp = service.saveFloorContent(
+        Map.of("id", e.getId(), "moresValue", "[\"B\"]", "moresTime", ""));
+
+    assertEquals(ResponseCode.SUCCESS.getCode(), resp.getCode());
+    TelegramTrainFloorContentEntity reloaded = contentDao.findById(e.getId());
+    assertEquals("[]", reloaded.getMoresTime(), "空 moresTime 必须落库为 []");
+  }
+}


---
commit a6e0c1350f94dfe46391d602d91b93caf92b748a
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 04:01:40 2026 +0800

    fix(persist-5.3): schema 同步迁移——5 张缺表 CREATE、2 处 is_start_sign 缺列、主键类型对齐（P1-8）

diff --git a/docs/database/migrations/2026-08-26-01-schema-sync.sql b/docs/database/migrations/2026-08-26-01-schema-sync.sql
new file mode 100644
index 0000000..59e9f66
--- /dev/null
+++ b/docs/database/migrations/2026-08-26-01-schema-sync.sql
@@ -0,0 +1,102 @@
+-- ============================================================================
+-- 迁移 01：schema 与实体对齐（P1-8）
+--
+-- 生成方式：%test 容器（mysql:8.0）drop-and-create 导出 Hibernate 实体 DDL
+-- （target/entity-ddl.sql），与 docs/database/project006.sql（当前快照，100 表）
+-- 逐表差分；并与 Hibernate 启动校验日志（logs/info.log.2，
+-- SchemaManagementIntegrator 输出）交叉核对：
+--   - 缺表 5 张：general_telex_pat / _page / _user / _user_value、t_masthead ✓
+--   - 缺列 2 处：simulation_router_room.is_start_sign、
+--     t_post_ticker_tape_train.is_start_sign（日志实证，评审 P1-8 仅列了前者）✓
+--   - 日志其余 "modify column" 均为长度/字符集/显示宽度噪音，同一类型族，
+--     Hibernate validate（Dialect#equivalentTypes）不校验长度与字符集，无需迁移。
+--     全部 98 张共有表逐列比对：0 处类型族不一致。
+--
+-- 执行顺序硬约束：本脚本 → 02-engine-innodb.sql → 才可启用
+-- %prod quarkus.hibernate-orm.database.generation=validate。
+-- 本脚本幂等：IF NOT EXISTS + 缺列判断由执行者保证只跑一次；重复执行
+-- CREATE 安全，重复 ADD COLUMN 会报 1060（可忽略）。
+-- ============================================================================
+
+-- ---- 1. 缺表（DDL 取自 Hibernate 实体导出，validate 的权威期望） ----
+
+CREATE TABLE IF NOT EXISTS `general_telex_pat` (
+  `is_cable` int DEFAULT NULL,
+  `pat_type` int DEFAULT NULL,
+  `status` int DEFAULT NULL,
+  `total_number` int DEFAULT NULL,
+  `train_type` int DEFAULT NULL,
+  `type` int DEFAULT NULL,
+  `create_time` datetime(6) DEFAULT NULL,
+  `end_time` datetime(6) DEFAULT NULL,
+  `start_time` datetime(6) DEFAULT NULL,
+  `valid_time` bigint DEFAULT NULL,
+  `create_user` varchar(255) DEFAULT NULL,
+  `id` varchar(255) NOT NULL,
+  `rule_content` varchar(255) DEFAULT NULL,
+  `rule_id` varchar(255) DEFAULT NULL,
+  `title` varchar(255) DEFAULT NULL,
+  PRIMARY KEY (`id`)
+) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
+
+CREATE TABLE IF NOT EXISTS `general_telex_pat_page` (
+  `page_number` int DEFAULT NULL,
+  `sort` int DEFAULT NULL,
+  `id` varchar(255) NOT NULL,
+  `key` varchar(255) DEFAULT NULL,
+  `train_id` varchar(255) DEFAULT NULL,
+  `value` varchar(255) DEFAULT NULL,
+  PRIMARY KEY (`id`)
+) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
+
+CREATE TABLE IF NOT EXISTS `general_telex_pat_user` (
+  `accuracy` decimal(38,2) DEFAULT NULL,
+  `error_number` int DEFAULT NULL,
+  `is_finish` int DEFAULT NULL,
+  `role` int DEFAULT NULL,
+  `score` decimal(38,2) DEFAULT NULL,
+  `speed` decimal(38,2) DEFAULT NULL,
+  `valid_time` int DEFAULT NULL,
+  `create_time` datetime(6) DEFAULT NULL,
+  `finish_time` datetime(6) DEFAULT NULL,
+  `deduct_info` varchar(255) DEFAULT NULL,
+  `duration` varchar(255) DEFAULT NULL,
+  `id` varchar(255) NOT NULL,
+  `speed_log` varchar(255) DEFAULT NULL,
+  `statistic_info` varchar(255) DEFAULT NULL,
+  `train_id` varchar(255) DEFAULT NULL,
+  `user_id` varchar(255) DEFAULT NULL,
+  `valid_time_log` varchar(255) DEFAULT NULL,
+  PRIMARY KEY (`id`)
+) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
+
+CREATE TABLE IF NOT EXISTS `general_telex_pat_user_value` (
+  `page_number` int DEFAULT NULL,
+  `sort` int DEFAULT NULL,
+  `id` varchar(255) NOT NULL,
+  `key` varchar(255) DEFAULT NULL,
+  `train_id` varchar(255) DEFAULT NULL,
+  `user_id` varchar(255) DEFAULT NULL,
+  `value` varchar(255) DEFAULT NULL,
+  PRIMARY KEY (`id`)
+) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
+
+CREATE TABLE IF NOT EXISTS `t_masthead` (
+  `content` varchar(255) DEFAULT NULL,
+  `id` varchar(255) NOT NULL,
+  `train_id` varchar(255) DEFAULT NULL,
+  PRIMARY KEY (`id`)
+) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
+
+-- ---- 2. 缺列（实体默认 isStartSign=1，存量行按业务默认回填为 1） ----
+
+ALTER TABLE `simulation_router_room` ADD COLUMN `is_start_sign` int NULL DEFAULT 1;
+ALTER TABLE `t_post_ticker_tape_train` ADD COLUMN `is_start_sign` int NULL DEFAULT 1;
+
+-- ---- 3. 主键类型对齐（P1-8 第 3 类） ----
+-- 旧基线（project006-base.sql:74）general_key_pat_page.id 为 int AUTO_INCREMENT，
+-- 实体为 UUID 字符串主键（GenerationType.UUID）。当前快照（project006.sql:74）
+-- 已是 varchar(64)，在该状态下本语句为无害的同型重建；仍停留在旧基线的环境
+-- 由本语句完成 int → varchar 对齐（存量数值主键转为其十进制字符串形式）。
+
+ALTER TABLE `general_key_pat_page` MODIFY COLUMN `id` varchar(64) NOT NULL;


---
commit 3c983a0794f9cf1eca0f7137cf6becdc062d74e4
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 04:01:40 2026 +0800

    fix(persist-5.4): 22 张 MyISAM 表转 InnoDB 迁移脚本（P1-7）

diff --git a/docs/database/migrations/2026-08-26-02-engine-innodb.sql b/docs/database/migrations/2026-08-26-02-engine-innodb.sql
new file mode 100644
index 0000000..17201a3
--- /dev/null
+++ b/docs/database/migrations/2026-08-26-02-engine-innodb.sql
@@ -0,0 +1,37 @@
+-- ============================================================================
+-- 迁移 02：MyISAM → InnoDB（P1-7）
+--
+-- ⚠ 执行前提（必须满足，缺一不可）：
+--   1. 全库备份（mysqldump --single-transaction 对 MyISAM 无效，需停写后备份）。
+--   2. 停服窗口执行：ALTER ENGINE 会重建整表并持锁，期间对应功能不可用；
+--      且应用在事务中混写 MyISAM/InnoDB，转换途中启动应用会产生不一致。
+--   3. 先执行 2026-08-26-01-schema-sync.sql，再执行本脚本，
+--      最后才可在 %prod 启用 quarkus.hibernate-orm.database.generation=validate。
+--
+-- 背景：以下 22 张表在 @Transactional 写路径中被删除/写入，但 MyISAM 不支持事务，
+-- 写入立即生效且无法回滚（评审 P1-7，22 张清单经逐表实测核实）。
+-- 本脚本幂等：对已是 InnoDB 的表执行仅触发一次无害重建。
+-- ============================================================================
+
+ALTER TABLE `general_key_pat` ENGINE = InnoDB;
+ALTER TABLE `general_key_pat_page` ENGINE = InnoDB;
+ALTER TABLE `general_key_pat_train_more` ENGINE = InnoDB;
+ALTER TABLE `general_key_pat_user` ENGINE = InnoDB;
+ALTER TABLE `general_key_pat_user_value` ENGINE = InnoDB;
+ALTER TABLE `general_key_pat_user_value_resolver` ENGINE = InnoDB;
+ALTER TABLE `general_ticker_pat` ENGINE = InnoDB;
+ALTER TABLE `general_ticker_pat_train_page` ENGINE = InnoDB;
+ALTER TABLE `general_ticker_pat_train_user` ENGINE = InnoDB;
+ALTER TABLE `general_ticker_pat_train_user_value` ENGINE = InnoDB;
+ALTER TABLE `hand_key_err_log` ENGINE = InnoDB;
+ALTER TABLE `simulation_router_room_page` ENGINE = InnoDB;
+ALTER TABLE `simulation_router_room_page_value` ENGINE = InnoDB;
+ALTER TABLE `t_post_telegram_train_content_value` ENGINE = InnoDB;
+ALTER TABLE `t_post_telegraph_key_pat_train_more` ENGINE = InnoDB;
+ALTER TABLE `t_post_telegraph_key_pat_train_page` ENGINE = InnoDB;
+ALTER TABLE `t_post_telegraph_key_pat_train_page_value` ENGINE = InnoDB;
+ALTER TABLE `t_post_telex_pat_train_page` ENGINE = InnoDB;
+ALTER TABLE `t_post_telex_pat_train_page_value` ENGINE = InnoDB;
+ALTER TABLE `t_post_ticker_tape_train_page` ENGINE = InnoDB;
+ALTER TABLE `t_post_ticker_tape_train_page_value` ENGINE = InnoDB;
+ALTER TABLE `t_ticker_tape_train_stage_setting` ENGINE = InnoDB;


---
commit f823a3333bca7f3dc44ef9d0edd283b21b992a11
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 04:01:40 2026 +0800

    fix(persist-5.5): %prod 启用 hibernate schema validate（迁移演练通过后置开关）

diff --git a/src/main/resources/application.yml b/src/main/resources/application.yml
index 77fde78..4eb1d2f 100644
--- a/src/main/resources/application.yml
+++ b/src/main/resources/application.yml
@@ -59,8 +59,16 @@ quarkus:
 #    additional-build-args: -H:ReflectionConfigurationFiles=reflection-config.json
 "%test":
   quarkus:
     hibernate-orm:
       database:
         generation: drop-and-create
     http:
       test-port: 18081
+"%prod":
+  quarkus:
+    hibernate-orm:
+      database:
+        # 顺序硬约束：必须先执行 docs/database/migrations/2026-08-26-01-schema-sync.sql
+        # 与 02-engine-innodb.sql，再部署本配置，否则启动即失败。
+        # 已于 2026-08-27 在 project006.sql 快照库演练通过（迁移 01+02 → prod 模式启动零校验错误）。
+        generation: validate

