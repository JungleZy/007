## commits
d14e62a fix(p1-16): 同类型训练查重断言方向写反

## stat
 .../com/nip/service/EnteringTelexPatService.java   |  2 +-
 .../nip/service/EnteringTelexPatServiceTest.java   | 60 ++++++++++++++++++++++
 2 files changed, 61 insertions(+), 1 deletion(-)

## diff
diff --git a/src/main/java/com/nip/service/EnteringTelexPatService.java b/src/main/java/com/nip/service/EnteringTelexPatService.java
index 001e9fc..29f41d0 100644
--- a/src/main/java/com/nip/service/EnteringTelexPatService.java
+++ b/src/main/java/com/nip/service/EnteringTelexPatService.java
@@ -39,21 +39,21 @@ public class EnteringTelexPatService {
     this.statisticalDao = statisticalDao;
   }
 
   @Transactional
   public EnteringTelexPatVO save(String token, EnteringTelexPatSaveParam param) {
     //从token中获取用户
     UserEntity userEntity = userDao.findUserEntityByToken(token);
     //如果用户id为空，则需要校验数据库中同一用户是否存在同一类型的记录
     if (Objects.isNull(param.getId())) {
       EnteringTelexPatEntity check = telexPatDao.findByCreateUserIdAndType(userEntity.getId(), param.getType());
-      Assert.notNull(check, "您已存在相同类型的训练，不能再添加同类的训练！");
+      Assert.isNull(check, "您已存在相同类型的训练，不能再添加同类的训练！");
     }
     //根据id和类型查询
     EnteringTelexPatEntity entity = telexPatDao.findByIdAndType(param.getId(), param.getType());
     if (entity == null) {
       entity = PojoUtils.convertOne(param, EnteringTelexPatEntity.class);
       entity.setCreateUserId(userEntity.getId());
     } else {
       entity.setTotalError(param.getTotalError());
       entity.setTotalNum(param.getTotalNum());
       entity.setTotalTime(param.getTotalTime());
diff --git a/src/test/java/com/nip/service/EnteringTelexPatServiceTest.java b/src/test/java/com/nip/service/EnteringTelexPatServiceTest.java
new file mode 100644
index 0000000..d0e152b
--- /dev/null
+++ b/src/test/java/com/nip/service/EnteringTelexPatServiceTest.java
@@ -0,0 +1,60 @@
+package com.nip.service;
+
+import com.nip.dao.UserDao;
+import com.nip.dto.vo.EnteringTelexPatVO;
+import com.nip.dto.vo.param.EnteringTelexPatSaveParam;
+import com.nip.entity.UserEntity;
+import com.nip.testsupport.Fixtures;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.Test;
+
+import static org.junit.jupiter.api.Assertions.*;
+
+/**
+ * 改级#16：save 创建路径查重断言方向写反（EnteringTelexPatService:49）。
+ * 修复前 Assert.notNull(check, ...)：首次创建（check==null）反而抛"已存在"；
+ * 真重复（check!=null）静默放行。
+ */
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class EnteringTelexPatServiceTest {
+  @Inject EnteringTelexPatService service;
+  @Inject UserDao userDao;
+
+  private static EnteringTelexPatSaveParam param(Integer type) {
+    EnteringTelexPatSaveParam p = new EnteringTelexPatSaveParam();
+    p.setType(type);
+    p.setTotalTime(60);
+    p.setTotalNum(120);
+    p.setTotalError(3);
+    p.setMessageName("口诀练习");
+    return p;
+  }
+
+  @Test
+  void firstCreateOfTypeSucceeds() {
+    String token = "telex-pat-first-create";
+    Fixtures.user(userDao, token);
+
+    EnteringTelexPatVO vo = service.save(token, param(0));
+
+    assertNotNull(vo, "首次创建同类型训练必须成功");
+    assertNotNull(vo.getId(), "创建成功必须返回持久化 id");
+  }
+
+  @Test
+  void secondCreateOfSameTypeIsRejected() {
+    String token = "telex-pat-duplicate";
+    UserEntity user = Fixtures.user(userDao, token);
+
+    service.save(token, param(1));
+
+    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
+        () -> service.save(token, param(1)), "同一用户同类型第二条创建必须被拒");
+    assertEquals("您已存在相同类型的训练，不能再添加同类的训练！", ex.getMessage());
+    assertNotNull(user.getId());
+  }
+}
