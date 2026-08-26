## commits
0ac58c5 docs(except): Phase 4 报告落盘
1935f9e fix(except-4): 全仓日志/空catch机械清理——29 处 log.error(getMessage) 改传异常对象；8 处空 catch 补日志或语义化；PostMilitaryTermTrain listPage 改直抛
50979e8 fix(except-2): getUserByToken 判空抛 UnauthorizedException（81 调用点核对；10 处 catch-all 前置重抛守卫；gradeCount 死码删除）
9cdf836 fix(except-1): 三个校验/兜底/未授权 ExceptionMapper + JWTInterceptor 收窄（proceed 移出 try），端点异常直达 Mapper

## stat
 .../sdd/2026-08-26-fix-plan/phase-4-report.md      |  64 +++++++++++
 .../java/com/nip/common/LifecycleApplication.java  |   3 +-
 .../common/exception/GlobalExceptionMapper.java    |  27 +++++
 .../exception/IllegalStateExceptionMapper.java     |  23 ++++
 .../exception/InvalidTitleExceptionMapper.java     |  24 ++++
 .../common/exception/UnauthorizedException.java    |  11 ++
 .../exception/UnauthorizedExceptionMapper.java     |  22 ++++
 .../exception/ValidationExceptionMapper.java       |  22 ++++
 .../com/nip/common/interceptor/JWTInterceptor.java |  20 ++--
 .../specification/SpecificationExecutor.java       |   4 +-
 src/main/java/com/nip/common/utils/PojoUtils.java  |   4 +-
 .../java/com/nip/common/utils/TickerPatUtils.java  |   5 +-
 .../com/nip/controller/free/ToolsController.java   |   5 +-
 .../nip/service/PostMilitaryTermTrainService.java  |  22 ++--
 .../service/PostTelegraphKeyPatTrainService.java   |   2 +-
 .../com/nip/service/PostTelexPatTrainService.java  |   2 +-
 .../java/com/nip/service/TelegramTrainService.java |  15 ++-
 src/main/java/com/nip/service/TelexPatService.java |  13 ++-
 .../java/com/nip/service/TelexPatTrainService.java |   7 +-
 .../java/com/nip/service/TestPaperService.java     |   2 +-
 .../nip/service/TheoryKnowledgeExamService.java    |   6 +-
 .../com/nip/service/TheoryKnowledgeService.java    |   4 -
 src/main/java/com/nip/service/UserService.java     |  13 ++-
 .../nip/service/UserTrainStatisticsService.java    |  17 ++-
 .../service/general/GeneralTelexPatService.java    |   3 +
 src/main/java/com/nip/ws/StartWebSocket.java       |   2 +-
 .../com/nip/ws/WebSocketGeneralKeyPatService.java  |   2 +-
 .../nip/ws/WebSocketGeneralTelexPatService.java    |   2 +-
 .../nip/ws/WebSocketGeneralTickerPatService.java   |   2 +-
 src/main/java/com/nip/ws/WebSocketService.java     |   4 +-
 .../com/nip/ws/WebSocketSimulationService.java     |   6 +-
 .../java/com/nip/ws/WebSocketUnionService.java     |   2 +-
 .../common/exception/ExceptionBoundaryTest.java    | 126 +++++++++++++++++++++
 src/test/java/com/nip/testsupport/Fixtures.java    |   5 +
 34 files changed, 426 insertions(+), 65 deletions(-)

## diff
diff --git a/.superpowers/sdd/2026-08-26-fix-plan/phase-4-report.md b/.superpowers/sdd/2026-08-26-fix-plan/phase-4-report.md
new file mode 100644
index 0000000..5cf0a34
--- /dev/null
+++ b/.superpowers/sdd/2026-08-26-fix-plan/phase-4-report.md
@@ -0,0 +1,64 @@
+# Phase 4 报告：异常边界与可观测性
+
+结论：Task 4.1-4.4 全部完成，三个提交，新增 7 个集成/单元测试 + 受影响旧测试共 28 个全绿，两条门禁 grep 零命中。
+
+## 提交
+
+| 提交 | 内容 |
+|---|---|
+| `9cdf836 fix(except-1)` | Task 4.1+4.3 同批：`common/exception/` 新建 GlobalExceptionMapper(Throwable→HTTP500+SYSTEM_ERROR)、ValidationExceptionMapper(IllegalArgumentException→200+CODE_500+原消息)、IllegalStateExceptionMapper、InvalidTitleExceptionMapper（同构）、UnauthorizedException+UnauthorizedExceptionMapper(→200+code203)；JWTInterceptor `context.proceed()` 移出 try，catch 收窄为拦截器自身逻辑，日志改 `log.error("jwt fail from {}.{}", 类, 方法, exception)` 带堆栈 |
+| `50979e8 fix(except-2)` | Task 4.2：`getUserByToken` 判 null 抛 UnauthorizedException；全仓 81 调用点逐一核对（31 文件），10 处 `try{}catch(Exception)→error()` 内的调用点前置 `catch(UnauthorizedException){throw e;}` 守卫（TelegramTrainService×4、TelexPatService×3、TelexPatTrainService×2、GeneralTelexPatService×1）；TheoryKnowledgeService.gradeCount 显式 null 分支死码删除 |
+| `1935f9e fix(except-4)` | Task 4.4：29 处 `log.error("…{}", e.getMessage())` → `log.error("…", e)`（含尾部 `：{}`/`:{}` 占位一并去除；PostTelegraphKeyPatTrainService:208 多参形态单独处理为 `log.error("details index:{},i:{}", index, i, e)`）；空 catch 8 处补齐；PostMilitaryTermTrainService.listPage 去掉 catch-返-emptyList 改直抛 |
+
+## Task 4.2 影响面核对（81 调用点）
+
+- grep 全仓 `getUserByToken` 命中 31 个文件 81 处（80 service + UserController:143），逐文件过目。
+- **10 处 catch(Exception)→error() 加守卫**：TelegramTrainService getAll/getFloorContentByFloorIdAsync×2/save、TelexPatService saveTelexPat/findById/deleteTexPatByToken、TelexPatTrainService saveTexPatTrain/findTexPatTrainByToken、GeneralTelexPatService findAll（该处原 catch 会把异常包成 RuntimeException(cause)，守卫防止 203 变 500）。
+- **TheoryKnowledgeService:531-533**（gradeCount 的 `if (userEntity == null) return error("Invalid token")`）：改抛后为死码，已删。
+- **其余 70 处**：无 catch 包裹，异常自然传播到 Mapper；含非 @JWT 的 DeviceService:41/:92——行为从 NPE(HTTP 500 裸响应) 变为 200+code203 信封，**计划内预期改进**，已有集成测试锁定。
+
+## Task 4.4 明细
+
+**日志（29 处，两条门禁的第 2 条）**：SpecificationExecutor×2、PojoUtils×2、PostTelexPatTrainService×1、TelegramTrainService×3、TelexPatService×3、TelexPatTrainService×1、TestPaperService×1、TheoryKnowledgeExamService×3、UserService×2（addUserRole/login）、StartWebSocket×1、WebSocketService×2（onError/send）、WebSocketSimulationService×3、WebSocketUnionService×1、WebSocketGeneral{KeyPat,TelexPat,TickerPat}×3、PostTelegraphKeyPatTrainService×1。逐行 diff 过目，无悬空 `{}` 残留（保留 `{}` 的行均有对应实参且异常对象在末位）。
+
+**空 catch（8 处）**：
+- LifecycleApplication:33 → `LOG.warn("banner 打印失败", e)`（纯装饰路径，log 即可）。
+- ToolsController:60 → `log.warn(..., e)`（补 @Slf4j）。
+- TickerPatUtils:293 → `log.warn("patKeys 非 JSON（index={}），按纯文本逐字符拆分", i, e)`（补 @Slf4j）。**不改重抛**：该 catch 是 Phase 1 有意保留的"纯文本 patKeys 协议容忍"（:295 注释），重抛会破坏既有协议行为；虽在 @Transactional 写路径内，但吞掉后走的是显式降级分支而非静默丢数据。
+- UserTrainStatisticsService ×5（补 @Slf4j）：parseTime 第一段 ISO 失败为正常回退（注释说明，不打日志防噪音），第二段失败 `log.warn` 后返回 null（修 P2-3 静默全时段）；sumElectronicKey/sumReceive×2 的时长解析失败逐条 `log.warn` 带原始值（修 P2-4）。均为只读统计路径，无 @Transactional 写路径归属，不需重抛。
+- PostMilitaryTermTrainService:424-427（原 :402-404 项）：删除 try/catch-返-emptyList，异常直抛到 Mapper（"登录失效"不再伪装成"暂无数据"）。
+
+**@Transactional 写路径核对**：对照 silent-failures 审计表逐条过——P0-4/5/6/7、P1-9/12/13/14 的写路径 catch 均已在 Phase 1/2 处理（本轮 grep 未再命中空 catch/吞写异常形态）；本轮 8 处空 catch 中唯一位于写路径的是 TickerPatUtils:293（处置见上）。Phase 1 已删的三个 catch 未回退。
+
+## 集成测试（ExceptionBoundaryTest，7 个用例）
+
+| 用例 | 断言 |
+|---|---|
+| 无 token 调 @JWT `/api/menus/getMenusAll` | HTTP 200 + code 203（拦截器自身校验保留） |
+| 过期 token 调 @JWT 同端点 | HTTP 200 + code 206（拦截器 exists 校验保留） |
+| 过期 token 调非 @JWT `/api/device/save` | HTTP 200 + code 203（UnauthorizedExceptionMapper，Task 4.2 新链路） |
+| 校验失败（permissions=null 的 addMenu） | HTTP 200 + code 500 + 原提示消息（ValidationExceptionMapper 接管，不再 SYSTEM_ERROR 兜底） |
+| 未知异常（menus=null → NPE 的 addMenu） | HTTP 500 + code 500 + "服务器错误"（GlobalExceptionMapper） |
+| getUserByToken 未知 token | 抛 UnauthorizedException（单元） |
+| findTexPatTrainByToken 未知 token | UnauthorizedException 穿透 catch-all 守卫重抛（单元） |
+
+说明：brief 写的"过期 token 调 @JWT 接口 → 203"在拦截器语义下实际是 **206**（203 = token 为空）——过期 token 到不了 getUserByToken，@JWT 路径由拦截器先拦。203 信封的 Mapper 链路在非 @JWT 的 DeviceController 路径上可达并已按此测试。测试基建：Fixtures.user 增加 deviceId 重载；拦截器 response.send 裸 JSON 无 Content-Type，测试显式 `RestAssured.defaultParser = Parser.JSON`。
+
+## 受影响旧测试
+
+无需修改任何旧测试断言：既有测试全部走 service 层直调（不经拦截器/Mapper），且均用已 seed 的有效 token。全量受影响集（11 类 28 用例）跑绿：ExceptionBoundaryTest、MenusServiceTest、TestPaperServiceTest、TheoryKnowledge{,Exam}ServiceTest、TelegramTrainServiceTest、TelexPatTrainStatisticalServiceTest、PostMilitaryTermTrainServiceTest、PostTelegraphKeyPatTrainServiceTest、TickerPatUtils{,Characterization}Test。
+
+## 门禁输出
+
+```
+$ grep -Prn 'catch\s*\([^)]+\)\s*\{\s*\}' src/main/java        # 零命中（含跨行形态复核，同样零命中）
+$ grep -rn 'getMessage())' src/main/java | grep log\.           # 零命中
+```
+
+日志格式抽查：全部替换行逐条 diff 过目，无悬空 `{}`。
+
+## Concerns
+
+- GlobalExceptionMapper 声明在 Throwable 上：RESTEasy Reactive 内置的 WebApplicationException Mapper 更具体，404/405 等默认语义不受影响（集成测试期间 404 行为正常，修路径前的 404 响应即为内置 Mapper 产出）。
+- 拦截器对空 token/206 的响应仍是 `response.send` 裸 JSON（无 Content-Type），前端现行契约如此，未动。
+- DeviceController 等非 @JWT 路径的 NPE→203 行为变化对前端是改进（可识别的重登信号），但若有客户端依赖旧 500 行为需前端知悉。
diff --git a/src/main/java/com/nip/common/LifecycleApplication.java b/src/main/java/com/nip/common/LifecycleApplication.java
index 6a982b4..5cac4f6 100644
--- a/src/main/java/com/nip/common/LifecycleApplication.java
+++ b/src/main/java/com/nip/common/LifecycleApplication.java
@@ -25,16 +25,17 @@ public class LifecycleApplication {
     try {
       InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("banner.txt");
       if (is != null) {
         String banner = new String(is.readAllBytes(), StandardCharsets.UTF_8);
         String version = ConfigProvider.getConfig().getOptionalValue("version", String.class).orElse("");
         banner = banner.replace("${version}", version);
         LOG.info("\n" + banner);
       }
-    } catch (Exception ignored) {
+    } catch (Exception e) {
+      LOG.warn("banner 打印失败", e);
     }
   }
 
   void onStop(@Observes ShutdownEvent event) {
     LOG.info("The Application Is Stopping...");
   }
 }
diff --git a/src/main/java/com/nip/common/exception/GlobalExceptionMapper.java b/src/main/java/com/nip/common/exception/GlobalExceptionMapper.java
new file mode 100644
index 0000000..33e2b51
--- /dev/null
+++ b/src/main/java/com/nip/common/exception/GlobalExceptionMapper.java
@@ -0,0 +1,27 @@
+package com.nip.common.exception;
+
+import com.nip.common.constants.ResponseCode;
+import com.nip.common.response.ResponseResult;
+import jakarta.ws.rs.core.MediaType;
+import jakarta.ws.rs.core.Response;
+import jakarta.ws.rs.ext.ExceptionMapper;
+import jakarta.ws.rs.ext.Provider;
+import org.slf4j.Logger;
+import org.slf4j.LoggerFactory;
+
+/**
+ * 兜底异常映射：未被专用 Mapper 接管的异常 → HTTP 500 + SYSTEM_ERROR 信封，日志保留完整堆栈。
+ */
+@Provider
+public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {
+  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionMapper.class);
+
+  @Override
+  public Response toResponse(Throwable e) {
+    log.error("unhandled", e);
+    return Response.serverError()
+        .type(MediaType.APPLICATION_JSON)
+        .entity(ResponseResult.error(ResponseCode.SYSTEM_ERROR))
+        .build();
+  }
+}
diff --git a/src/main/java/com/nip/common/exception/IllegalStateExceptionMapper.java b/src/main/java/com/nip/common/exception/IllegalStateExceptionMapper.java
new file mode 100644
index 0000000..4340cdf
--- /dev/null
+++ b/src/main/java/com/nip/common/exception/IllegalStateExceptionMapper.java
@@ -0,0 +1,23 @@
+package com.nip.common.exception;
+
+import com.nip.common.constants.ResponseCode;
+import com.nip.common.response.ResponseResult;
+import jakarta.ws.rs.core.MediaType;
+import jakarta.ws.rs.core.Response;
+import jakarta.ws.rs.ext.ExceptionMapper;
+import jakarta.ws.rs.ext.Provider;
+
+/**
+ * 状态校验类异常（Assert.state / 业务前置校验）：与 {@link ValidationExceptionMapper} 同构，
+ * HTTP 200 + CODE_500 + 原提示消息。
+ */
+@Provider
+public class IllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {
+  @Override
+  public Response toResponse(IllegalStateException e) {
+    return Response.ok(
+            ResponseResult.error(ResponseCode.CODE_500, e.getMessage(), e.getMessage()))
+        .type(MediaType.APPLICATION_JSON)
+        .build();
+  }
+}
diff --git a/src/main/java/com/nip/common/exception/InvalidTitleExceptionMapper.java b/src/main/java/com/nip/common/exception/InvalidTitleExceptionMapper.java
new file mode 100644
index 0000000..a6b3118
--- /dev/null
+++ b/src/main/java/com/nip/common/exception/InvalidTitleExceptionMapper.java
@@ -0,0 +1,24 @@
+package com.nip.common.exception;
+
+import com.nip.common.constants.ResponseCode;
+import com.nip.common.response.ResponseResult;
+import com.nip.service.TheoryKnowledgeService;
+import jakarta.ws.rs.core.MediaType;
+import jakarta.ws.rs.core.Response;
+import jakarta.ws.rs.ext.ExceptionMapper;
+import jakarta.ws.rs.ext.Provider;
+
+/**
+ * 理论知识标题校验异常：与 {@link ValidationExceptionMapper} 同构，HTTP 200 + CODE_500 + 原提示消息。
+ */
+@Provider
+public class InvalidTitleExceptionMapper
+    implements ExceptionMapper<TheoryKnowledgeService.InvalidTitleException> {
+  @Override
+  public Response toResponse(TheoryKnowledgeService.InvalidTitleException e) {
+    return Response.ok(
+            ResponseResult.error(ResponseCode.CODE_500, e.getMessage(), e.getMessage()))
+        .type(MediaType.APPLICATION_JSON)
+        .build();
+  }
+}
diff --git a/src/main/java/com/nip/common/exception/UnauthorizedException.java b/src/main/java/com/nip/common/exception/UnauthorizedException.java
new file mode 100644
index 0000000..012f83e
--- /dev/null
+++ b/src/main/java/com/nip/common/exception/UnauthorizedException.java
@@ -0,0 +1,11 @@
+package com.nip.common.exception;
+
+/**
+ * token 无效或已过期（getUserByToken 查无用户）。
+ * 由 {@link UnauthorizedExceptionMapper} 映射为 HTTP 200 + code 203 信封（与 JWTInterceptor 现行契约一致）。
+ */
+public class UnauthorizedException extends RuntimeException {
+  public UnauthorizedException(String message) {
+    super(message);
+  }
+}
diff --git a/src/main/java/com/nip/common/exception/UnauthorizedExceptionMapper.java b/src/main/java/com/nip/common/exception/UnauthorizedExceptionMapper.java
new file mode 100644
index 0000000..90f39de
--- /dev/null
+++ b/src/main/java/com/nip/common/exception/UnauthorizedExceptionMapper.java
@@ -0,0 +1,22 @@
+package com.nip.common.exception;
+
+import com.nip.common.constants.ResponseCode;
+import com.nip.common.response.ResponseResult;
+import jakarta.ws.rs.core.MediaType;
+import jakarta.ws.rs.core.Response;
+import jakarta.ws.rs.ext.ExceptionMapper;
+import jakarta.ws.rs.ext.Provider;
+
+/**
+ * 契约兼容：HTTP 200 + code 203（与 JWTInterceptor 对空 token 的现行为一致）。
+ */
+@Provider
+public class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {
+  @Override
+  public Response toResponse(UnauthorizedException e) {
+    return Response.ok(
+            ResponseResult.error(ResponseCode.CODE_203))
+        .type(MediaType.APPLICATION_JSON)
+        .build();
+  }
+}
diff --git a/src/main/java/com/nip/common/exception/ValidationExceptionMapper.java b/src/main/java/com/nip/common/exception/ValidationExceptionMapper.java
new file mode 100644
index 0000000..5bf81f1
--- /dev/null
+++ b/src/main/java/com/nip/common/exception/ValidationExceptionMapper.java
@@ -0,0 +1,22 @@
+package com.nip.common.exception;
+
+import com.nip.common.constants.ResponseCode;
+import com.nip.common.response.ResponseResult;
+import jakarta.ws.rs.core.MediaType;
+import jakarta.ws.rs.core.Response;
+import jakarta.ws.rs.ext.ExceptionMapper;
+import jakarta.ws.rs.ext.Provider;
+
+/**
+ * 校验类异常保持 HTTP 200 + 业务码 CODE_500 + 原提示消息（契约约束，Phase 4 收窄拦截器后恢复精确业务码）。
+ */
+@Provider
+public class ValidationExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
+  @Override
+  public Response toResponse(IllegalArgumentException e) {
+    return Response.ok(
+            ResponseResult.error(ResponseCode.CODE_500, e.getMessage(), e.getMessage()))
+        .type(MediaType.APPLICATION_JSON)
+        .build();
+  }
+}
diff --git a/src/main/java/com/nip/common/interceptor/JWTInterceptor.java b/src/main/java/com/nip/common/interceptor/JWTInterceptor.java
index f0d75c4..7094d91 100644
--- a/src/main/java/com/nip/common/interceptor/JWTInterceptor.java
+++ b/src/main/java/com/nip/common/interceptor/JWTInterceptor.java
@@ -35,17 +35,19 @@ public class JWTInterceptor {
   @Context
   HttpServerRequest request;
   @Context
   HttpServerResponse response;
   @Inject
   UserDao userDao;
 
   @AroundInvoke
-  Object execute(InvocationContext context) {
+  Object execute(InvocationContext context) throws Exception {
+    // 拦截器只兜自身 token 解析/校验逻辑的异常；context.proceed() 在 try 之外，
+    // 端点业务异常直达 common/exception 下的 ExceptionMapper（Phase 4 Task 4.3）
     try {
       Map<String, Object> mp = new HashMap<>();
       response.putHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));//HTTP 请求头获取源IP或域名，并配置到跨域源中
       response.putHeader("Access-Control-Allow-Methods", "POST,OPTIONS,PUT,HEAD,DELETE");
       response.putHeader("Access-Control-Max-Age", "3600000");
       response.putHeader("Access-Control-Allow-Credentials", "true");
       response.putHeader("Access-Control-Allow-Headers", "Authentication,Origin, X-Requested-With, Content-Type, Accept,token,deviceId");
       if (request.method().name().equals("OPTIONS")) {
@@ -67,21 +69,21 @@ public class JWTInterceptor {
         deviceId = request.getParam(DEVICE_ID);
       }
       if (StringUtils.isEmpty(deviceId)) {
         mp.put(CODE, ResponseCode.CODE_204.getCode());
         mp.put(MESSAGE, ResponseCode.CODE_204.getMessage());
         response.send(new ObjectMapper().writeValueAsString(mp));
         return null;
       }
-      if (userDao.existsUserByTokenAndDeviceId(token, deviceId)) {
-        return context.proceed();
+      if (!userDao.existsUserByTokenAndDeviceId(token, deviceId)) {
+        mp.put(CODE, ResponseCode.CODE_206.getCode());
+        mp.put(MESSAGE, ResponseCode.CODE_206.getMessage());
+        response.send(new ObjectMapper().writeValueAsString(mp));
+        return null;
       }
-      mp.put(CODE, ResponseCode.CODE_206.getCode());
-      mp.put(MESSAGE, ResponseCode.CODE_206.getMessage());
-      response.send(new ObjectMapper().writeValueAsString(mp));
-      return null;
     } catch (Exception exception) {
-      log.error("method error from {}.{}\n", context.getTarget().getClass().getSimpleName(), context.getMethod().getName());
-      return ResponseResult.error(ResponseCode.SYSTEM_ERROR,exception.getMessage(),exception.getMessage());
+      log.error("jwt fail from {}.{}", context.getTarget().getClass().getSimpleName(), context.getMethod().getName(), exception);
+      return ResponseResult.error(ResponseCode.SYSTEM_ERROR, exception.getMessage(), exception.getMessage());
     }
+    return context.proceed();
   }
 }
diff --git a/src/main/java/com/nip/common/specification/SpecificationExecutor.java b/src/main/java/com/nip/common/specification/SpecificationExecutor.java
index 8ad6e73..bd6dffa 100644
--- a/src/main/java/com/nip/common/specification/SpecificationExecutor.java
+++ b/src/main/java/com/nip/common/specification/SpecificationExecutor.java
@@ -128,23 +128,23 @@ public class SpecificationExecutor<T> {
               }
               List<ResultMappingHandler> handlers = ResultMappingHandlerFactory.getHandlers();
               ResultMappingHandler mappingHandler = handlers.stream()
                   .filter(handler -> handler.getHandlerType(ret.get()))
                   .findFirst()
                   .orElseThrow(() -> new RuntimeException("类型未指定"));
               mappingHandler.handler(executeQuery, columNames, retClass, ret.get());
             } catch (Exception e) {
-              log.error("sql execute exception:{}", e.getMessage());
+              log.error("sql execute exception", e);
             }
           });
 
       return ret.get();
     } catch (Exception e) {
-      log.error("sql execute exception:{}", e.getMessage());
+      log.error("sql execute exception", e);
     }
     throw new IllegalArgumentException("sql execute exception");
 
   }
 
   /**
    * 将字段转成驼峰
    *
diff --git a/src/main/java/com/nip/common/utils/PojoUtils.java b/src/main/java/com/nip/common/utils/PojoUtils.java
index 38fe9ea..4bfe95a 100644
--- a/src/main/java/com/nip/common/utils/PojoUtils.java
+++ b/src/main/java/com/nip/common/utils/PojoUtils.java
@@ -61,28 +61,28 @@ public class PojoUtils {
   public static <T, V> T convertOne(V v, Class<T> clazz, BiConsumer<V, T> func, String... ignoreProperties) {
     try {
 
       T t = clazz.getDeclaredConstructor().newInstance();
       BeanUtil.copyProperties(v, t, CopyOptions.create().setIgnoreError(true));
       func.accept(v, t);
       return t;
     } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
-      log.error("convertOne error:{}", e.getMessage());
+      log.error("convertOne error", e);
       throw new NullPointerException();
     }
   }
 
   public static <T, V> T convertOne(V v, Class<T> clazz, String... ignoreProperties) {
     try {
       T entity = clazz.getDeclaredConstructor().newInstance();
       BeanUtil.copyProperties(v, entity, CopyOptions.create().setIgnoreError(true));
       return entity;
     } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
-      log.error("convertOne error:{}", e.getMessage());
+      log.error("convertOne error", e);
       throw new NullPointerException();
     }
   }
 
   public static <T, V> PageInfo<T> convertPage(PanacheQuery<V> page, Class<T> clazz, BiConsumer<V, T> func, String... ignoreProperties) {
     List<V> content = page.list();
     List<T> convert = convert(content, clazz, func, ignoreProperties);
     PageInfo<T> ret = new PageInfo<>();
diff --git a/src/main/java/com/nip/common/utils/TickerPatUtils.java b/src/main/java/com/nip/common/utils/TickerPatUtils.java
index babc12d..bb25dcf 100644
--- a/src/main/java/com/nip/common/utils/TickerPatUtils.java
+++ b/src/main/java/com/nip/common/utils/TickerPatUtils.java
@@ -5,22 +5,24 @@ import com.nip.dto.PostTelegramTrainFinishInfoDto;
 import com.nip.dto.score.MessageDeduct;
 import com.nip.dto.score.PostTelegramTrainRule;
 import com.nip.dto.score.SpeedDeduct;
 import com.nip.dto.vo.PostTelegramTrainResolverVO;
 import com.nip.dto.vo.PostTelegramTrainScoreVO;
 import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
 import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
 import org.apache.commons.lang3.StringUtils;
+import lombok.extern.slf4j.Slf4j;
 
 import java.util.ArrayList;
 import java.util.List;
 import java.util.Map;
 import java.util.Objects;
 
+@Slf4j
 public class TickerPatUtils {
   /**
    * 解析用户的原始报文
    *
    * @return
    */
   public static PostTelegramTrainResolverVO resolverMessage(List<String> patKeys,
       PostTelegramTrainScoreVO scoreVO,
@@ -285,17 +287,18 @@ public class TickerPatUtils {
     List<List<List<Integer>>> timesLists = new ArrayList<>(n);
     List<List<List<Integer>>> valuesLists = new ArrayList<>(n);
     for (int i = 0; i < n; i++) {
       PostTelegramTrainContentAddParam item = messageBody.get(i);
       List<String> pk = null;
       try {
         pk = JSONUtils.fromJson(item.getPatKeys(), new TypeToken<List<String>>() {
         });
-      } catch (Exception ignore) {
+      } catch (Exception e) {
+        log.warn("handleMessageBody patKeys 非 JSON（index={}），按纯文本逐字符拆分", i, e);
       }
       // 协议容忍：纯文本 patKeys 逐字符拆分；副作用：损坏的 JSON 数组文本也会被拆成含 [ " , 的垃圾按键——无协议标记无法区分，接受此残留
       if (pk == null) {
         pk = new ArrayList<>();
         String raw = item.getPatKeys();
         if (raw != null) {
           for (int c = 0; c < raw.length(); c++) {
             pk.add(String.valueOf(raw.charAt(c)));
diff --git a/src/main/java/com/nip/controller/free/ToolsController.java b/src/main/java/com/nip/controller/free/ToolsController.java
index 24244a6..071378a 100644
--- a/src/main/java/com/nip/controller/free/ToolsController.java
+++ b/src/main/java/com/nip/controller/free/ToolsController.java
@@ -4,16 +4,17 @@ import cn.hutool.core.date.DateTime;
 import com.nip.common.response.Response;
 import com.nip.common.response.ResponseResult;
 import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.ws.rs.GET;
 import jakarta.ws.rs.Path;
 import org.eclipse.microprofile.openapi.annotations.Operation;
 import org.eclipse.microprofile.openapi.annotations.tags.Tag;
 import org.eclipse.microprofile.config.ConfigProvider;
+import lombok.extern.slf4j.Slf4j;
 
 import java.net.InetAddress;
 import java.net.UnknownHostException;
 import java.time.YearMonth;
 import java.util.ArrayList;
 import java.util.HashMap;
 import java.util.List;
 import java.util.Map;
@@ -23,16 +24,17 @@ import java.util.Map;
  *
  * @author < a href=" ">ZhangYang</ a>
  * @version v1.0.01
  * @date 2023-07-24 11:04
  */
 @Path("/tools")
 @ApplicationScoped
 @Tag(name = "工具库接口")
+@Slf4j
 public class ToolsController {
   @GET
   @Path("/getNowTime")
   @Operation(summary = "获取当前服务器时间")
   public Response<Long> getNowTime() {
     return ResponseResult.success(DateTime.now().getTime());
   }
 
@@ -52,17 +54,18 @@ public class ToolsController {
     long totalMemoryMB = totalMemory / (1024 * 1024);
     long freeMemoryMB = freeMemory / (1024 * 1024);
     String hostname = "unknown";
     String ip = "unknown";
     try {
       InetAddress localHost = InetAddress.getLocalHost();
       hostname = localHost.getHostName();
       ip = localHost.getHostAddress();
-    } catch (UnknownHostException ignored) {
+    } catch (UnknownHostException e) {
+      log.warn("getSystemAndVersionInfo 获取本机地址失败", e);
     }
     String version = ConfigProvider.getConfig().getOptionalValue("version", String.class).orElse(null);
     info.put("软件版本", version);
     info.put("操作系统", osName);
     info.put("系统架构", osArch);
     info.put("系统版本", osVersion);
     info.put("Java版本", javaVersion);
     info.put("Java厂商", javaVendor);
diff --git a/src/main/java/com/nip/service/PostMilitaryTermTrainService.java b/src/main/java/com/nip/service/PostMilitaryTermTrainService.java
index 12dc282..f2ba2e6 100644
--- a/src/main/java/com/nip/service/PostMilitaryTermTrainService.java
+++ b/src/main/java/com/nip/service/PostMilitaryTermTrainService.java
@@ -407,29 +407,25 @@ public class PostMilitaryTermTrainService {
     Matcher matcher = Pattern.compile("\\d+").matcher(str);
     if (matcher.find()) {
       return matcher.group();
     }
     return null;
   }
 
   public List<PostMilitaryTermTrainVO> listPage(String token) {
-    try {
-      UserEntity userEntity = userDao.findUserEntityByToken(token);
-      List<PostMilitaryTermTrainEntity> ret = termTrainDao.findByUserIdOrderByCreateTimeDesc(userEntity.getId());
-      return PojoUtils.convert(ret, PostMilitaryTermTrainVO.class, (e, v) -> v.setTypes(
-          dataDao.findAllByIdIn(JSONUtils.fromJson(e.getTypes(), new TypeToken<>() {
-              }))
-              .stream()
-              .map(MilitaryTermDataEntity::getKey)
-              .toList()));
-    } catch (Exception e) {
-      log.error("获取训练失败", e);
-      return Collections.emptyList();
-    }
+    // Phase 4 Task 4.4：不再 catch-返-emptyList 把"登录失效/脏数据"伪装成"暂无数据"，异常直达 ExceptionMapper
+    UserEntity userEntity = userDao.findUserEntityByToken(token);
+    List<PostMilitaryTermTrainEntity> ret = termTrainDao.findByUserIdOrderByCreateTimeDesc(userEntity.getId());
+    return PojoUtils.convert(ret, PostMilitaryTermTrainVO.class, (e, v) -> v.setTypes(
+        dataDao.findAllByIdIn(JSONUtils.fromJson(e.getTypes(), new TypeToken<>() {
+            }))
+            .stream()
+            .map(MilitaryTermDataEntity::getKey)
+            .toList()));
   }
 
   public PostMilitaryTermTrainVO details(PostMilitaryTermTrainVO vo) {
     PostMilitaryTermTrainEntity entity = termTrainDao.findById(vo.getId());
     //查询试卷内容
     List<PostMilitaryTermTrainTestPaperEntity> testPaperEntities = testPaperDao.findAllByTrainId(vo.getId());
 
     return PojoUtils.convertOne(entity, PostMilitaryTermTrainVO.class, (e, v) -> {
diff --git a/src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java b/src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java
index 48dfc66..ac7f8f3 100644
--- a/src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java
+++ b/src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java
@@ -200,17 +200,17 @@ public class PostTelegraphKeyPatTrainService {
               try {
                 if (index + i < twoPage.size()) {
                   if (null != twoPage.get(index + i)) {
                     twoPageValue.add(
                         PojoUtils.convertOne(twoPage.get(index + i), PostTelegraphKeyPatTrainPageValueEntity.class));
                   }
                 }
               } catch (Exception e) {
-                log.error("details：{},index:{},i:{}", e.getMessage(), index, i);
+                log.error("details index:{},i:{}", index, i, e);
               }
             }
           }
           v.setContent(PojoUtils.convert(twoPageValue, PostTelegraphKeyPatTrainPageMessageVO.class));
         } else {
           v.setContent(PojoUtils.convert(twoPage, PostTelegraphKeyPatTrainPageMessageVO.class));
         }
         v.setPageAnalyzeVOS(analyzeVOS);
diff --git a/src/main/java/com/nip/service/PostTelexPatTrainService.java b/src/main/java/com/nip/service/PostTelexPatTrainService.java
index 290131c..9f1269a 100644
--- a/src/main/java/com/nip/service/PostTelexPatTrainService.java
+++ b/src/main/java/com/nip/service/PostTelexPatTrainService.java
@@ -1173,17 +1173,17 @@ public class PostTelexPatTrainService {
                       rowList.add(index - 1, data);
                       z = z + 2;
                       errorNumber++;
                     }
                   }
                   // 判断是
                 } catch (Exception e) {
                   // 若出现异常则添加到集合中
-                  log.error("解析ADD或QTA 失败:{}", e.getMessage());
+                  log.error("解析ADD或QTA 失败", e);
                   rowList.add(group);
                 }
               }
               // 标错页修改 1234 .... 7890-2/1 表示将第二页修改成第一页
               else if (PATTERN_REG_6.matcher(group).matches()) {
                 // 7890-2/1
                 try {
                   String[] split = group.split("/");
diff --git a/src/main/java/com/nip/service/TelegramTrainService.java b/src/main/java/com/nip/service/TelegramTrainService.java
index b838245..733f313 100644
--- a/src/main/java/com/nip/service/TelegramTrainService.java
+++ b/src/main/java/com/nip/service/TelegramTrainService.java
@@ -1,12 +1,13 @@
 package com.nip.service;
 
 
 import cn.hutool.core.text.CharSequenceUtil;
+import com.nip.common.exception.UnauthorizedException;
 import com.nip.common.response.Response;
 import com.nip.common.response.ResponseResult;
 import com.nip.common.utils.JSONUtils;
 import com.nip.common.utils.PojoUtils;
 import com.nip.dao.*;
 import com.nip.dto.TelegramBaseTrainDto;
 import com.nip.dto.TelegramTrainDto;
 import com.nip.dto.TelegramTrainFloorDto;
@@ -66,16 +67,18 @@ public class TelegramTrainService {
 
   private final String[] dotLineArray = new String[]{"A", "B", "C", "D", "F", "G", "J", "K", "L", "N", "P", "Q", "R",
       "U", "V", "W", "X", "Y", "Z", "1", "2", "3", "4", "6", "7", "8", "9"};
 
   public Response<List<TelegramTrainEntity>> getAll(String token) {
     try {
       UserEntity userEntity = userService.getUserByToken(token);
       return ResponseResult.success(telegramTrainDao.findAllByCreateUserIdOrderByCreateTimeDesc(userEntity.getId()));
+    } catch (UnauthorizedException e) {
+      throw e;
     } catch (Exception e) {
       return ResponseResult.error();
     }
   }
 
   public Response<TelegramTrainDto> getById(String id) {
     TelegramTrainEntity trainEntity = Optional.ofNullable(telegramTrainDao.findById(id))
         .orElseThrow(() -> new IllegalArgumentException("未查询该训练！"));
@@ -110,44 +113,48 @@ public class TelegramTrainService {
 
   public Response<Map<String, List<TelegramTrainFloorContentEntity>>> getFloorContentByFloorId(List<String> ids) {
     try {
       Map<String, List<TelegramTrainFloorContentEntity>> list = new HashMap<>();
       List<TelegramTrainFloorContentEntity> byFloorIdIn = telegramTrainFloorContentDao.findByFloorIdIn(ids);
       handleMaps(list, byFloorIdIn);
       return ResponseResult.success(list);
     } catch (Exception e) {
-      log.error("getFloorContentByFloorId获取失败：{}", e.getMessage());
+      log.error("getFloorContentByFloorId获取失败", e);
       return ResponseResult.error();
     }
   }
 
   public Response<Void> getFloorContentByFloorIdAsync(String token, String id) {
     try {
       UserEntity userEntity = userService.getUserByToken(token);
       asyncTask(userEntity.getId(), id);
       return ResponseResult.success();
+    } catch (UnauthorizedException e) {
+      throw e;
     } catch (Exception e) {
-      log.error("getFloorContentByFloorIdAsync获取失败：{}", e.getMessage());
+      log.error("getFloorContentByFloorIdAsync获取失败", e);
       return ResponseResult.error();
     }
   }
 
   public Response<Void> getFloorContentByFloorIdAsync(String token, String id, Integer pageNumber) {
     try {
       UserEntity userEntity = userService.getUserByToken(token);
       TelegramTrainFloorEntity allByTrainIdAndPageNumber = telegramTrainFloorDao.findAllByTrainIdAndPageNumber(id, pageNumber);
       List<TelegramTrainFloorContentEntity> byFloorIdIn = telegramTrainFloorContentDao.findAllByFloorIdOrderBySort(allByTrainIdAndPageNumber.getId());
       Map<String, List<TelegramTrainFloorContentEntity>> list = new HashMap<>();
       list.put(allByTrainIdAndPageNumber.getId(), byFloorIdIn);
       WebSocketService.sendInfo(
           userEntity.getId(), new ResponseModel(FLOOR_CONTENT_DATA.getCode(), JSONUtils.toJson(list)));
       return ResponseResult.success();
+    } catch (UnauthorizedException e) {
+      throw e;
     } catch (Exception e) {
-      log.error("getFloorContentByFloorIdAsync获取失败：{}", e.getMessage());
+      log.error("getFloorContentByFloorIdAsync获取失败", e);
       return ResponseResult.error();
     }
   }
 
   public void asyncTask(String userId, String id) {
     List<TelegramTrainFloorEntity> all = telegramTrainFloorDao.findAllByTrainIdOrderBySort(id);
     List<String> ls = new ArrayList<>();
     for (int j = 0; j < all.size(); j++) {
@@ -290,16 +297,18 @@ public class TelegramTrainService {
           entity.setMoresValue(floorContents.get(j).getMoresValue());
           entity.setMoresTime(CharSequenceUtil.isEmpty(floorContents.get(j).getMoresTime())
               ? "[]"
               : floorContents.get(j).getMoresTime());
           telegramTrainFloorContentDao.save(entity);
         }
       }
       return ResponseResult.success(train);
+    } catch (UnauthorizedException e) {
+      throw e;
     } catch (Exception e) {
       return ResponseResult.error();
     }
   }
 
   @Transactional
   public Response<Void> saveFloorContent(Map<String, String> map) {
     try {
diff --git a/src/main/java/com/nip/service/TelexPatService.java b/src/main/java/com/nip/service/TelexPatService.java
index 1f64159..e2c3e96 100644
--- a/src/main/java/com/nip/service/TelexPatService.java
+++ b/src/main/java/com/nip/service/TelexPatService.java
@@ -1,10 +1,11 @@
 package com.nip.service;
 
+import com.nip.common.exception.UnauthorizedException;
 import com.nip.common.constants.TelexPatTrainStatisticalTypeEnum;
 import com.nip.common.response.Response;
 import com.nip.common.response.ResponseResult;
 import com.nip.common.utils.PojoUtils;
 import com.nip.dao.TelexPatDao;
 import com.nip.dao.TelexPatTrainStatisticalDao;
 import com.nip.entity.TelexPatEntity;
 import com.nip.entity.TelexPatTrainEntity;
@@ -54,42 +55,48 @@ public class TelexPatService {
       allByUserIdAndType.setCount(count);
       allByUserIdAndType.setMistake(mistake);
       allByUserIdAndType.setDuration(Long.valueOf(dur));
       TelexPatEntity save = telexPatDao.save(allByUserIdAndType);
       statisticalService.statistical(userEntity.getId(), WORD.getType(),
           PojoUtils.convertOne(save, TelexPatTrainEntity.class)
       );
       return ResponseResult.success(save);
+    } catch (UnauthorizedException e) {
+      throw e;
     } catch (Exception e) {
-      log.error("saveTelexPat：{}", e.getMessage());
+      log.error("saveTelexPat", e);
       return ResponseResult.error();
     }
   }
 
   public Response<TelexPatEntity> findById(String token, int type) {
     try {
       UserEntity userEntity = userService.getUserByToken(token);
       return ResponseResult.success(telexPatDao.findAllByUserIdAndType(userEntity.getId(), type));
+    } catch (UnauthorizedException e) {
+      throw e;
     } catch (Exception e) {
-      log.error("findById：{}", e.getMessage());
+      log.error("findById", e);
       return ResponseResult.error();
     }
   }
 
   @Transactional
   public Response<Void> deleteTexPatByToken(String token, Integer type) {
     try {
       UserEntity userEntity = userService.getUserByToken(token);
       telexPatDao.deleteByUserIdAndType(userEntity.getId(), type);
       //清除统计信息
       TelexPatTrainStatisticalEntity statisticalEntity = statisticalDao.findByUserIdAndType(userEntity.getId(), type);
       statisticalEntity.setTotalTime("0");
       statisticalEntity.setTotalCount(0);
       statisticalEntity.setAvgSpeed(BigDecimal.ZERO);
       statisticalDao.save(statisticalEntity);
       return ResponseResult.success();
+    } catch (UnauthorizedException e) {
+      throw e;
     } catch (Exception e) {
-      log.error("deleteTexPatByToken：{}", e.getMessage());
+      log.error("deleteTexPatByToken", e);
       return ResponseResult.error();
     }
   }
 }
\ No newline at end of file
diff --git a/src/main/java/com/nip/service/TelexPatTrainService.java b/src/main/java/com/nip/service/TelexPatTrainService.java
index 7cf1ed1..cf5025f 100644
--- a/src/main/java/com/nip/service/TelexPatTrainService.java
+++ b/src/main/java/com/nip/service/TelexPatTrainService.java
@@ -1,11 +1,12 @@
 package com.nip.service;
 
 
+import com.nip.common.exception.UnauthorizedException;
 import com.nip.common.constants.PostTelexPatTrainStatusEnum;
 import com.nip.common.response.Response;
 import com.nip.common.response.ResponseResult;
 import com.nip.common.utils.JSONUtils;
 import com.nip.common.utils.PojoUtils;
 import com.nip.dao.TelexPatTrainDao;
 import com.nip.dto.TelexPatTrainDto;
 import com.nip.dto.vo.TelexPatTrainVO;
@@ -76,26 +77,30 @@ public class TelexPatTrainService {
       }
 
       TelexPatTrainEntity save = telexPatTrainDao.save(telexPatTrainEntity);
       //如果是完成训练，
       if (dto.getStatus().compareTo(FINISH.getStatus()) == 0) {
         statisticalService.statistical(userEntity.getId(), dto.getType() + 1, save);
       }
       return ResponseResult.success(save);
+    } catch (UnauthorizedException e) {
+      throw e;
     } catch (Exception e) {
-      log.error("保存训练记录失败：{}", e.getMessage());
+      log.error("保存训练记录失败", e);
       return ResponseResult.error();
     }
   }
 
   public Response<List<TelexPatTrainEntity>> findTexPatTrainByToken(String token) {
     try {
       UserEntity userEntity = userService.getUserByToken(token);
       return ResponseResult.success(telexPatTrainDao.findAllByCreateUserId(userEntity.getId()));
+    } catch (UnauthorizedException e) {
+      throw e;
     } catch (Exception e) {
       return ResponseResult.error();
     }
   }
 
   public Response<TelexPatTrainEntity> findTexPatTrainById(String id) {
     try {
       return ResponseResult.success(telexPatTrainDao.findById(id));
diff --git a/src/main/java/com/nip/service/TestPaperService.java b/src/main/java/com/nip/service/TestPaperService.java
index 53755a8..8deb801 100644
--- a/src/main/java/com/nip/service/TestPaperService.java
+++ b/src/main/java/com/nip/service/TestPaperService.java
@@ -130,17 +130,17 @@ public class TestPaperService {
                 TestPaperQuestionDto.class);
             typeToListMap.get(type).add(testPaperQuestionDto);
           }
         }
         testPaperDtos.add(testPaperDto);
       }
       return ResponseResult.success(testPaperDtos);
     } catch (Exception e) {
-      log.error("findAllTestPaper error:{}", e.getMessage());
+      log.error("findAllTestPaper error", e);
       return ResponseResult.error();
     }
   }
 
   /**
    * 根据测试卷ID查找测试卷信息
    * 此方法使用了@Transactional注解，确保在查找测试卷时，操作是原子性的，可以维护数据一致性
    *
diff --git a/src/main/java/com/nip/service/TheoryKnowledgeExamService.java b/src/main/java/com/nip/service/TheoryKnowledgeExamService.java
index 09e400e..9777a61 100644
--- a/src/main/java/com/nip/service/TheoryKnowledgeExamService.java
+++ b/src/main/java/com/nip/service/TheoryKnowledgeExamService.java
@@ -151,17 +151,17 @@ public class TheoryKnowledgeExamService {
       TheoryKnowledgeExamEntity save = theoryKnowledgeExamDao.save(entity);
       Map<String, Object> map = new HashMap<>();
       map.put("exam", save);
       List<TheoryKnowledgeExamUserEntity> allByExamId = theoryKnowledgeExamUserDao.findAllByExamId(examId);
       allByExamId.forEach(a -> WebSocketService.sendInfo(a.getUserId(),
           new ResponseModel(CodeConstants.TEACHERCHANGEEXAMSTATE.getCode(), map)));
       return ResponseResult.success(entity);
     } catch (Exception e) {
-      log.error("teacherStartExam error:{}", e.getMessage());
+      log.error("teacherStartExam error", e);
       return ResponseResult.error();
     }
   }
 
   @Transactional
   public Response<Map<String, Object>> studentChangeExamState(String examId, String userId, int type, String content) {
     try {
       TheoryKnowledgeExamEntity entity = theoryKnowledgeExamDao.findById(examId);
@@ -189,17 +189,17 @@ public class TheoryKnowledgeExamService {
       }
       TheoryKnowledgeExamUserEntity save = theoryKnowledgeExamUserDao.save(allByExamIdAndUserId);
       data.put("exam", entity);
       data.put("student", save);
       WebSocketService.sendInfo(entity.getTeacher(),
           new ResponseModel(CodeConstants.STUDENTCHANGEEXAMSTATE.getCode(), data));
       return ResponseResult.success(data);
     } catch (Exception e) {
-      log.error("student change status error:{}", e.getMessage());
+      log.error("student change status error", e);
       return ResponseResult.error();
     }
   }
 
   @Transactional
   public Response<TheoryKnowledgeExamUserEntity> saveUserRealTimeParam(String examId, String userId, String content) {
     try {
       TheoryKnowledgeExamUserEntity allByExamIdAndUserId = theoryKnowledgeExamUserDao.findAllByExamIdAndUserId(examId,
@@ -210,17 +210,17 @@ public class TheoryKnowledgeExamService {
       allByExamIdAndUserId.setContent(content);
       TheoryKnowledgeExamUserEntity save = theoryKnowledgeExamUserDao.save(allByExamIdAndUserId);
       TheoryKnowledgeExamEntity entity = theoryKnowledgeExamDao.findById(examId);
       Map<String, Object> map = new HashMap<>();
       map.put("student", save);
       WebSocketService.sendInfo(entity.getTeacher(), new ResponseModel(CodeConstants.USERUPLOADCONTENT.getCode(), map));
       return ResponseResult.success(save);
     } catch (Exception e) {
-      log.error("saveUserRealTimeParam:{}", e.getMessage());
+      log.error("saveUserRealTimeParam", e);
       return ResponseResult.error();
     }
   }
 
   /**
    * 新增自测考试
    *
    * @param token
diff --git a/src/main/java/com/nip/service/TheoryKnowledgeService.java b/src/main/java/com/nip/service/TheoryKnowledgeService.java
index 99c9648..1215b60 100644
--- a/src/main/java/com/nip/service/TheoryKnowledgeService.java
+++ b/src/main/java/com/nip/service/TheoryKnowledgeService.java
@@ -523,20 +523,16 @@ public class TheoryKnowledgeService {
    * @param token 用户身份令牌，用于识别用户
    * @param year  统计的年份
    * @param month 统计的月份
    * @param type  统计类型：0-成绩分布，1-考试次数，其他-分数统计
    * @return 包含成绩统计信息的响应对象
    */
   public Response<Object> gradeCount(String token, String year, String month, int type) {
     UserEntity userEntity = userService.getUserByToken(token);
-    if (userEntity == null) {
-      return ResponseResult.error("Invalid token");
-    }
-
     return switch (type) {
       case 0 -> ResponseResult.success(gradeDistribution(userEntity.getId(), year, month));
       case 1 -> ResponseResult.success(examTimes(userEntity.getId(), year, month));
       default -> ResponseResult.success(scoreCount(userEntity.getId(), year, month));
     };
   }
 
   /**
diff --git a/src/main/java/com/nip/service/UserService.java b/src/main/java/com/nip/service/UserService.java
index a56a608..881866b 100644
--- a/src/main/java/com/nip/service/UserService.java
+++ b/src/main/java/com/nip/service/UserService.java
@@ -1,10 +1,11 @@
 package com.nip.service;
 
+import com.nip.common.exception.UnauthorizedException;
 import com.nip.common.constants.MessageConstants;
 import com.nip.common.constants.ResponseCode;
 import com.nip.common.response.Response;
 import com.nip.common.response.ResponseResult;
 import com.nip.common.utils.AESUtil;
 import com.nip.common.utils.MD5Util;
 import com.nip.common.utils.PojoUtils;
 import com.nip.common.utils.ToolUtil;
@@ -341,17 +342,17 @@ public class UserService {
       for (String roleId : roleIds) {
         UserRoleEntity userRoleEntity = new UserRoleEntity();
         userRoleEntity.setUserId(userId);
         userRoleEntity.setRoleId(roleId);
         userRoleDao.save(userRoleEntity);
       }
       return true;
     } catch (Exception e) {
-      log.error("addUserRole error:{}", e.getMessage());
+      log.error("addUserRole error", e);
       return false;
     }
   }
 
   /**
    * 用户登录方法
    *
    * @param userAccount 用户账号，用于查询用户信息
@@ -391,17 +392,17 @@ public class UserService {
       List<MenusDto> menusDtoList = role.getIsAdmin() == 0
           ? menusService.getMenusDtos()
           : menusService.getMenusDtosById(role.getId());
       userInfoDto.setUser(user);
       userInfoDto.setRole(role);
       userInfoDto.setMenus(menusDtoList);
       return ResponseResult.success(MessageConstants.LOGIN_SUCCESS, userInfoDto);
     } catch (Exception e) {
-      log.error("login error:{}", e.getMessage());
+      log.error("login error", e);
       return ResponseResult.error(ResponseCode.SYSTEM_ERROR.getCode(), MessageConstants.DATA_EXCEPTION);
     }
   }
 
   /**
    * 用户退出功能
    * 通过使用户实体与给定令牌关联的设备ID和令牌本身无效来实现用户退出
    *
@@ -469,20 +470,24 @@ public class UserService {
       return ResponseResult.error(MessageConstants.DATA_EXCEPTION);
     }
   }
 
   /**
    * 根据用户令牌获取用户实体
    *
    * @param token 用户令牌，用于唯一标识用户会话
-   * @return UserEntity 返回用户实体对象，如果找不到则返回null
+   * @return UserEntity 用户实体对象，查无用户（token 无效或已过期）时抛 UnauthorizedException（映射为 200+code203 信封）
    */
   public UserEntity getUserByToken(String token) {
-    return userDao.findUserEntityByToken(token);
+    UserEntity user = userDao.findUserEntityByToken(token);
+    if (user == null) {
+      throw new UnauthorizedException("token 无效或已过期");
+    }
+    return user;
   }
 
   /**
    * 获取所有教师用户信息
    * <p>
    * 教师在系统中的角色ID为"1"此方法通过调用UserDao的findAllByRoleId方法，
    * 并传入角色ID"1"来获取所有教师用户的详细信息
    *
diff --git a/src/main/java/com/nip/service/UserTrainStatisticsService.java b/src/main/java/com/nip/service/UserTrainStatisticsService.java
index 4e79da0..fa7be40 100644
--- a/src/main/java/com/nip/service/UserTrainStatisticsService.java
+++ b/src/main/java/com/nip/service/UserTrainStatisticsService.java
@@ -11,26 +11,28 @@ import com.nip.dto.vo.HandKeyRecentTrainVO;
 import com.nip.dto.vo.UserTrainDurationStatVO;
 import com.nip.entity.PostEnteringExerciseEntity;
 import com.nip.entity.PostTelexPatTrainEntity;
 import com.nip.entity.PostTickerTapeTrainEntity;
 import com.nip.entity.TickerTapeTrainEntity;
 import com.nip.entity.simulation.key.GeneralKeyPatUserEntity;
 import com.nip.entity.simulation.telex.GeneralTelexPatUserEntity;
 import com.nip.entity.simulation.ticker.GeneralTickerPatTrainUserEntity;
+import lombok.extern.slf4j.Slf4j;
 import jakarta.enterprise.context.ApplicationScoped;
 import jakarta.inject.Inject;
 
 import java.time.Duration;
 import java.time.LocalDateTime;
 import java.time.format.DateTimeFormatter;
 import java.time.format.DateTimeParseException;
 import java.util.List;
 import java.util.Objects;
 
+@Slf4j
 @ApplicationScoped
 public class UserTrainStatisticsService {
   @Inject
   GeneralTickerPatTrainUserDao tickerUserDao;
   @Inject
   GeneralKeyPatUserDao keyUserDao;
   @Inject
   TickerTapeTrainDao tickerDao;
@@ -70,22 +72,24 @@ public class UserTrainStatisticsService {
   }
 
   private LocalDateTime parseTime(String text) {
     if (text == null || text.isBlank())
       return null;
     try {
       return LocalDateTime.parse(text);
     } catch (DateTimeParseException ignored) {
+      // ISO 格式不匹配属正常回退路径，继续尝试 yyyy-MM-dd HH:mm:ss
     }
     try {
       return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
-    } catch (DateTimeParseException ignored) {
+    } catch (DateTimeParseException e) {
+      log.warn("parseTime 时间参数无法解析，忽略该时间过滤条件: {}", text, e);
+      return null;
     }
-    return null;
   }
 
   private int sumHandKey(String userId, LocalDateTime start, LocalDateTime end) {
     List<GeneralTickerPatTrainUserEntity> list = tickerUserDao.find("userId = ?1 and role = 0", userId).list();
     long total = 0;
     for (GeneralTickerPatTrainUserEntity e : list) {
       if (Objects.equals(e.getIsFinish(), 1) && e.getCreateTime() != null && e.getFinishTime() != null) {
         if (within(e.getFinishTime(), start, end)) {
@@ -99,40 +103,43 @@ public class UserTrainStatisticsService {
   private int sumElectronicKey(String userId, LocalDateTime start, LocalDateTime end) {
     List<GeneralKeyPatUserEntity> list = keyUserDao.find("userId = ?1 and role = 0 and isFinish = 1", userId).list();
     int total = 0;
     for (GeneralKeyPatUserEntity e : list) {
       try {
         if (within(e.getFinishTime(), start, end)) {
           total += Integer.parseInt(Objects.toString(e.getDuration(), "0"));
         }
-      } catch (Exception ignored) {
+      } catch (Exception ex) {
+        log.warn("sumElectronicKey 训练时长解析失败按0计入: duration={}", e.getDuration(), ex);
       }
     }
     return total;
   }
 
   private int sumReceive(String userId, LocalDateTime start, LocalDateTime end) {
     List<TickerTapeTrainEntity> baseList = tickerDao.find("userId = ?1 and status = 3", userId).list();
     int total = 0;
     for (TickerTapeTrainEntity e : baseList) {
       try {
         if (within(e.getEndTime(), start, end)) {
           total += Integer.parseInt(Objects.toString(e.getValidTime(), "0"));
         }
-      } catch (Exception ignored) {
+      } catch (Exception ex) {
+        log.warn("sumReceive 训练时长解析失败按0计入: validTime={}", e.getValidTime(), ex);
       }
     }
     List<PostTickerTapeTrainEntity> postList = postTickerDao.find("userId = ?1 and status >= 2", userId).list();
     for (PostTickerTapeTrainEntity e : postList) {
       try {
         if (within(e.getEndTime(), start, end)) {
           total += Integer.parseInt(Objects.toString(e.getValidTime(), "0"));
         }
-      } catch (Exception ignored) {
+      } catch (Exception ex) {
+        log.warn("sumReceive 训练时长解析失败按0计入: validTime={}", e.getValidTime(), ex);
       }
     }
     return total;
   }
 
   private int sumPostTelex(String userId, int trainType, LocalDateTime start, LocalDateTime end) {
     List<PostTelexPatTrainEntity> list = postTelexDao
         .find("createUser = ?1 and trainType = ?2 and status = 3", userId, trainType).list();
diff --git a/src/main/java/com/nip/service/general/GeneralTelexPatService.java b/src/main/java/com/nip/service/general/GeneralTelexPatService.java
index ccb0044..c5d1896 100644
--- a/src/main/java/com/nip/service/general/GeneralTelexPatService.java
+++ b/src/main/java/com/nip/service/general/GeneralTelexPatService.java
@@ -1,12 +1,13 @@
 package com.nip.service.general;
 
 import cn.hutool.core.text.CharSequenceUtil;
 import com.google.gson.reflect.TypeToken;
+import com.nip.common.exception.UnauthorizedException;
 import com.nip.common.PageInfo;
 import com.nip.common.constants.CodeConstants;
 import com.nip.common.constants.PostTelegramTrainEnum;
 import com.nip.common.response.Response;
 import com.nip.common.response.ResponseResult;
 import com.nip.common.utils.ArraySafeGetUtils;
 import com.nip.common.utils.JSONUtils;
 import com.nip.common.utils.Page;
@@ -194,16 +195,18 @@ public class GeneralTelexPatService {
           });
       PageInfo<GeneralTelexPatTrainVO> pageInfo = new PageInfo<>();
       pageInfo.setCurrentPage(all.getCurrentPage());
       pageInfo.setPageSize(all.getPageSize());
       pageInfo.setTotalPage(all.getTotalPage());
       pageInfo.setTotalNumber(all.getTotalNumber());
       pageInfo.setData(convert);
       return pageInfo;
+    } catch (UnauthorizedException e) {
+      throw e;
     } catch (Exception e) {
       log.error("查询训练列表失败", e);
       throw new RuntimeException(e);
     }
   }
 
   public GeneralTelexPatTrainVO detail(GeneralTelexPatPageParamDto param) {
     try {
diff --git a/src/main/java/com/nip/ws/StartWebSocket.java b/src/main/java/com/nip/ws/StartWebSocket.java
index e699fdd..b8b8853 100644
--- a/src/main/java/com/nip/ws/StartWebSocket.java
+++ b/src/main/java/com/nip/ws/StartWebSocket.java
@@ -51,12 +51,12 @@ public class StartWebSocket {
   }
 
   private static void send(Session session, ResponseModel message) {
     try {
       if (session.isOpen()) {
         session.getAsyncRemote().sendText(JSONUtils.toJson(message));
       }
     } catch (Exception e) {
-      log.error("StartWebSocket send:{}", e.getMessage());
+      log.error("StartWebSocket send", e);
     }
   }
 }
diff --git a/src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java b/src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java
index 5988167..f7c2617 100644
--- a/src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java
+++ b/src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java
@@ -183,12 +183,12 @@ public class WebSocketGeneralKeyPatService {
       log.error("WebSocketGeneralKeyPatService.sendErrMessage: 发送消息失败");
     }
   }
 
   private void close(Session session) {
     try {
       session.close();
     } catch (IOException e) {
-      log.error("关闭socket出错:{}", e.getMessage());
+      log.error("关闭socket出错", e);
     }
   }
 }
diff --git a/src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java b/src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java
index 9c13a21..3555596 100644
--- a/src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java
+++ b/src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java
@@ -175,12 +175,12 @@ public class WebSocketGeneralTelexPatService {
       log.error("WebSocketGeneralTelexPatService.sendErrMessage: 发送消息失败");
     }
   }
 
   private void close(Session session) {
     try {
       session.close();
     } catch (IOException e) {
-      log.error("关闭socket出错:{}", e.getMessage());
+      log.error("关闭socket出错", e);
     }
   }
 }
diff --git a/src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java b/src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java
index 35985a4..bebb2d4 100644
--- a/src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java
+++ b/src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java
@@ -176,17 +176,17 @@ public class WebSocketGeneralTickerPatService {
   @OnError
   public void onError(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId, Session session, Throwable t) {
     log.error("ws error, session={}", session.getId(), t);
     //复用 onClose 清理该 session 对应的房间状态，并关闭连接
     onClose(uid, trainId);
     try {
       session.close();
     } catch (IOException e) {
-      log.error("关闭socket出错:{}", e.getMessage());
+      log.error("关闭socket出错", e);
     }
   }
 
   /**
    * 广播发送统一入口：async remote 避免并发 basic 写抛 IllegalStateException；
    * 返回 false 表示连接已关闭或提交失败，调用方据此清理死会话
    */
   public static boolean sendMessage(Session session, String message, String sendName, String receiveName) {
diff --git a/src/main/java/com/nip/ws/WebSocketService.java b/src/main/java/com/nip/ws/WebSocketService.java
index ef494d6..052ab23 100644
--- a/src/main/java/com/nip/ws/WebSocketService.java
+++ b/src/main/java/com/nip/ws/WebSocketService.java
@@ -108,17 +108,17 @@ public class WebSocketService {
   }
 
   /**
    * @param session
    * @param error
    */
   @OnError
   public void onError(Session session, Throwable error) {
-    log.error("WebSocketService onError:{}", error.getMessage());
+    log.error("WebSocketService onError", error);
   }
 
   /**
    * 按 sid 定向发送
    */
   public static void sendInfo(@PathParam("sid") String sid, ResponseModel message) {
     Session session = CLIENTS.get(sid);
     if (session != null) {
@@ -145,12 +145,12 @@ public class WebSocketService {
    * IllegalStateException）；catch Exception，单个接收方失败不得中断调用方
    */
   private static void send(Session session, String message) {
     try {
       if (session.isOpen()) {
         session.getAsyncRemote().sendText(message);
       }
     } catch (Exception e) {
-      log.error("WebSocketService send:{}", e.getMessage());
+      log.error("WebSocketService send", e);
     }
   }
 }
diff --git a/src/main/java/com/nip/ws/WebSocketSimulationService.java b/src/main/java/com/nip/ws/WebSocketSimulationService.java
index f65b052..579e0c4 100644
--- a/src/main/java/com/nip/ws/WebSocketSimulationService.java
+++ b/src/main/java/com/nip/ws/WebSocketSimulationService.java
@@ -631,46 +631,46 @@ public class WebSocketSimulationService {
    * 确保紧随其后的 session.close() 前错误帧已发出
    */
   private void sendErrorMessage(Session session, String errorMsg, String sendName, String receiveName) {
     try {
       if (session.isOpen()) {
         session.getBasicRemote().sendText(JSONUtils.toJson(SimulationResponseModel.err(errorMsg, sendName, receiveName)));
       }
     } catch (Exception e) {
-      log.error("WebSocketSimulationService.sendErrorMessage:{}", e.getMessage());
+      log.error("WebSocketSimulationService.sendErrorMessage", e);
     }
   }
 
   /**
    * 广播发送统一入口：async remote（Undertow 内部排队，避免多线程并发 basic 写抛
    * IllegalStateException 打断整轮广播）；catch Exception，单个接收方失败不中断循环
    */
   public static void sendMessage(Session session, String message, String sendName, String receiveName) {
     try {
       if (session.isOpen()) {
         session.getAsyncRemote().sendText(JSONUtils.toJson(SimulationResponseModel.success(message, sendName, receiveName)));
       }
     } catch (Exception e) {
-      log.error("WebSocketSimulationService.sendMessage:{}", e.getMessage());
+      log.error("WebSocketSimulationService.sendMessage", e);
     }
   }
 
   /**
    * 踢出旧连接
    *
    * @param simulations 连接
    * @param userId      用户id
    */
   public void kickOutOld(List<WebSocketSimulationService> simulations, String userId) {
     //先关旧连接再整体移除：索引 for 内 remove 会因元素前移漏踢（P2-3）
     for (WebSocketSimulationService item : simulations) {
       if (Objects.equals(item.getUserModel().getId(), userId)) {
         try {
           item.getSession().close();
         } catch (IOException e) {
-          log.error("WebSocketSimulationService.kickOutOld:{}", e.getMessage());
+          log.error("WebSocketSimulationService.kickOutOld", e);
         }
       }
     }
     simulations.removeIf(item -> Objects.equals(item.getUserModel().getId(), userId));
   }
 }
diff --git a/src/main/java/com/nip/ws/WebSocketUnionService.java b/src/main/java/com/nip/ws/WebSocketUnionService.java
index 3e7c934..24518ac 100644
--- a/src/main/java/com/nip/ws/WebSocketUnionService.java
+++ b/src/main/java/com/nip/ws/WebSocketUnionService.java
@@ -524,12 +524,12 @@ public class WebSocketUnionService {
    */
   private static void send(Session session, ResponseModel message) {
     if (session == null) {
       return;
     }
     try {
       session.getAsyncRemote().sendText(JSONUtils.toJson(message));
     } catch (Exception e) {
-      log.error("WebSocketUnionService.send:{}", e.getMessage());
+      log.error("WebSocketUnionService.send", e);
     }
   }
 }
diff --git a/src/test/java/com/nip/common/exception/ExceptionBoundaryTest.java b/src/test/java/com/nip/common/exception/ExceptionBoundaryTest.java
new file mode 100644
index 0000000..f4b72f4
--- /dev/null
+++ b/src/test/java/com/nip/common/exception/ExceptionBoundaryTest.java
@@ -0,0 +1,126 @@
+package com.nip.common.exception;
+
+import com.nip.dao.UserDao;
+import com.nip.service.TelexPatTrainService;
+import com.nip.service.UserService;
+import com.nip.testsupport.Fixtures;
+import com.nip.testsupport.MySqlResource;
+import io.quarkus.test.common.QuarkusTestResource;
+import io.quarkus.test.junit.QuarkusTest;
+import jakarta.inject.Inject;
+import org.junit.jupiter.api.BeforeEach;
+import org.junit.jupiter.api.Test;
+import io.restassured.RestAssured;
+import io.restassured.parsing.Parser;
+
+import static io.restassured.RestAssured.given;
+import static org.hamcrest.Matchers.equalTo;
+import static org.hamcrest.Matchers.is;
+import static org.junit.jupiter.api.Assertions.assertThrows;
+
+/**
+ * Phase 4 异常边界集成测试：JWTInterceptor 收窄（Task 4.3）后，
+ * 端点异常由 common/exception 下的 ExceptionMapper 接管（Task 4.1）。
+ */
+@QuarkusTest
+@QuarkusTestResource(MySqlResource.class)
+class ExceptionBoundaryTest {
+  private static final String TOKEN = "boundary-token";
+  private static final String DEVICE = "boundary-device";
+
+  @Inject
+  UserDao userDao;
+  @Inject
+  UserService userService;
+  @Inject
+  TelexPatTrainService telexPatTrainService;
+
+  @BeforeEach
+  void seedUser() {
+    // 拦截器 response.send 的裸 JSON 未带 Content-Type，显式指定默认解析器
+    RestAssured.defaultParser = Parser.JSON;
+    if (userDao.findUserEntityByToken(TOKEN) == null) {
+      Fixtures.user(userDao, TOKEN, DEVICE);
+    }
+  }
+
+  @Test
+  void missingTokenOnJwtEndpointReturns203Envelope() {
+    // 拦截器自身校验保留：无 token → HTTP 200 + code 203
+    given()
+        .header("Origin", "http://localhost")
+        .header("deviceId", DEVICE)
+        .when().get("/api/menus/getMenusAll")
+        .then().statusCode(200)
+        .body("code", is(203));
+  }
+
+  @Test
+  void unknownTokenOnNonJwtEndpointReturns203Envelope() {
+    // Task 4.2：非 @JWT 的 DeviceController 路径，getUserByToken 抛 UnauthorizedException
+    // → UnauthorizedExceptionMapper：HTTP 200 + code 203（原为 NPE→500）
+    given()
+        .header("Origin", "http://localhost")
+        .header("token", "expired-token-nowhere")
+        .contentType("application/json")
+        .body("{}")
+        .when().post("/api/device/save")
+        .then().statusCode(200)
+        .body("code", is(203));
+  }
+
+  @Test
+  void getUserByTokenThrowsUnauthorizedForUnknownToken() {
+    assertThrows(UnauthorizedException.class, () -> userService.getUserByToken("expired-token-nowhere"));
+  }
+
+  @Test
+  void unauthorizedRethrownThroughLegacyCatchAll() {
+    // 4.2 步骤 2：位于 try/catch(Exception)→error() 内的调用点必须重抛 203，而不是被吞成通用错误
+    assertThrows(UnauthorizedException.class,
+        () -> telexPatTrainService.findTexPatTrainByToken("expired-token-nowhere"));
+  }
+
+  @Test
+  void expiredTokenOnJwtEndpointReturns206Envelope() {
+    // 库中不存在的（过期）token → 拦截器 HTTP 200 + code 206
+    given()
+        .header("Origin", "http://localhost")
+        .header("token", "expired-token-nowhere")
+        .header("deviceId", DEVICE)
+        .when().get("/api/menus/getMenusAll")
+        .then().statusCode(200)
+        .body("code", is(206));
+  }
+
+  @Test
+  void validationFailureOnJwtEndpointKeeps200WithOriginalMessage() {
+    // 校验失败（permissions=null 的 addMenu）→ ValidationExceptionMapper 接管：
+    // HTTP 200 + CODE_500 + 原提示消息（不再被拦截器兜成 SYSTEM_ERROR）
+    given()
+        .header("Origin", "http://localhost")
+        .header("token", TOKEN)
+        .header("deviceId", DEVICE)
+        .contentType("application/json")
+        .body("{\"menus\":{\"title\":\"px\"},\"permissions\":null}")
+        .when().post("/api/menus/addMenu")
+        .then().statusCode(200)
+        .body("code", is(500))
+        .body("message", equalTo("permissions 缺失，拒绝编辑菜单权限"));
+  }
+
+  @Test
+  void unknownExceptionOnJwtEndpointReturns500SystemErrorEnvelope() {
+    // 未知异常（menus=null → NPE）→ GlobalExceptionMapper：HTTP 500 + SYSTEM_ERROR 信封
+    given()
+        .header("Origin", "http://localhost")
+        .header("token", TOKEN)
+        .header("deviceId", DEVICE)
+        .contentType("application/json")
+        .body("{\"permissions\":[]}")
+        .when().post("/api/menus/addMenu")
+        .then().statusCode(500)
+        .body("code", is(500))
+        .body("message", equalTo("服务器错误"));
+  }
+}
diff --git a/src/test/java/com/nip/testsupport/Fixtures.java b/src/test/java/com/nip/testsupport/Fixtures.java
index f800d81..a9e56ac 100644
--- a/src/test/java/com/nip/testsupport/Fixtures.java
+++ b/src/test/java/com/nip/testsupport/Fixtures.java
@@ -2,15 +2,20 @@ package com.nip.testsupport;
 
 import com.nip.dao.UserDao;
 import com.nip.entity.UserEntity;
 
 public final class Fixtures {
   private Fixtures() {}
 
   public static UserEntity user(UserDao userDao, String token) {
+    return user(userDao, token, null);
+  }
+
+  public static UserEntity user(UserDao userDao, String token, String deviceId) {
     UserEntity u = new UserEntity();
     u.setUserName("tester");
     u.setUserAccount("tester");
     u.setToken(token);
+    u.setDeviceId(deviceId);
     return userDao.save(u); // save 自带事务独立提交
   }
 }
