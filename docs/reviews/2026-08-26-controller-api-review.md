# API 层（controller + dto）代码审查报告

**结论：共 34 个问题 —— P0 1 个、P1 8 个、P2 19 个、P3 6 个。** 另有 5 项纯安全问题按内网口径记入附录，不计入上述统计。

审查范围：`src/main/java/com/nip/controller/`（52 个文件，全部逐行读过）与 `src/main/java/com/nip/dto/`（75 个文件）。service 内部逻辑、dao 不在范围内，仅在需要作为证据链时引用。本机无 Java 环境，全部为静态阅读，未运行任何构建或测试。

> **〔勘误 2026-08-27〕** 上行"52 个文件"为口径错误：`controller/` 下实为 **63 个 Controller 类 / 64 个 .java 文件**（含 `test/Test.java` 非 controller 实验类），52 是标注 `@JWT` 的类数，疑将两者混淆。见 `2026-08-26-review-audit.md` §4 对照表。原文保留不改，以此标注为准。

最要命的三件事，按顺序读：

1. **`/test/start` 是一个无鉴权的 GET，直接 UPDATE 生产库里一条写死 ID 的训练记录**（P0-1）。Swagger UI 在生产是开启的，任何人点一下 "Try it out" 就把那条记录的状态、时间、成绩永久覆盖掉。
2. **全项目没有任何 `ExceptionMapper`**（P1-6）。控制器里有 11 处 `Integer.parseInt(map.get(...))` / `map.get(...).toString()` / 强制类型转换，参数缺失或类型不对时直接抛异常，Quarkus 兜底返回默认错误页，`Response{code,data,message}` 这层统一封装在错误路径上完全失效——前端拿到的不是约定的结构。
3. **分页参数没有任何校验，默认值本身就是坏的**（P1-5）。`Page.page` 默认 0，而所有消费方都写 `page.getPage() - 1`，于是不传 page 就是 `-1` → Hibernate/Panache 抛 `IllegalArgumentException` → 500；`rows` 传 0 会在 `SpecificationExecutor.findPage` 里除零；`rows` 没有上限，传 10000000 就是全表加载。

关于两个检查项的说明：

- **fastjson**：项目没有引入 fastjson。JSON 走 `quarkus-rest-jackson`（请求体反序列化）与 Gson（`common/utils/JSONUtils`）。controller 层唯一的手写 JSON 解析在 `TheoryKnowledgeController`，已并入 P1-6。`ws/` 包里用的 `JSONObject` 是 `org.jose4j.json.internal.json_simple.JSONObject`，不在本次范围。
- **文件上传/下载**：controller 层只有 3 个相关端点，其中 2 个的 service 实现被整体注释掉（P1-7、P1-8），1 个是空方法（P3-1）。**不存在**路径拼接、文件名处理、流未关闭的问题——因为压根没有落盘逻辑。真正的问题是这些端点在"假装成功"。

---

## P0

### P0-1 `/test/start` 无鉴权 GET，直接覆盖生产训练记录

**位置**：`src/main/java/com/nip/controller/test/TestController.java:28-45`

**现象**：

```java
@GET()
@Path("/start")
public Response<?> start(){
  trainDao.begin("02bfee8b-a01f-479f-a1a7-1d081734c952");
  trainDao.pause("02bfee8b-a01f-479f-a1a7-1d081734c952","10","1",1);
  trainDao.goOn("02bfee8b-a01f-479f-a1a7-1d081734c952");
  trainDao.finish("02bfee8b-a01f-479f-a1a7-1d081734c952","10","1",10);
```

`TestController` 类上**没有** `@JWT`（同目录外几乎所有 controller 都有），`JWTInterceptor` 不会拦截。四个 DAO 方法全部是 JPQL 批量更新（`src/main/java/com/nip/dao/TickerTapeTrainDao.java:31-64`）：

```java
update("startTime = now(),`status` = 1  where id = ?1", id);
update("`status` = 2,validTime = ?2 ,mark=?3 ,schedule=?4  where id = ?1 ", ...);
update("`status` = 3,validTime = ?2 ,mark=?3 ,endTime = now(),schedule=?4  where id = ?1", ...);
```

**影响**：训练记录 `02bfee8b-a01f-479f-a1a7-1d081734c952` 的 `status`、`startTime`、`endTime`、`validTime`、`mark`、`schedule` 被无条件覆盖成测试值（validTime=10、mark=1、schedule=10），原数据不可恢复、无审计。触发门槛极低：GET 请求，无需 token/deviceId；`src/main/resources/application.yml:36-38` 里 `quarkus.swagger-ui.enable: true`，Swagger UI 上一个按钮就能触发；浏览器预取、内网扫描器、监控探活都可能命中。另外三条 `System.out.println`（`TestController.java:42-44`）直接打到 stdout，绕过日志框架。

**建议修复方向**：删除整个 `controller/test` 包（同目录的 `Test.java:18-63` 是个带 `main` 的 GZIP 压缩实验类，也不该在 controller 包里）。若确需保留调试入口，用 `@IfBuildProfile("dev")` 限定 dev 环境并加 `@JWT`。

---

## P1

### P1-1 `getOneline/{trainId}` 路径模板与参数绑定不匹配，必然 NPE

**位置**：`src/main/java/com/nip/controller/general/GeneralKeyPatTrainController.java:28-32`

**现象**：

```java
@GET
@Path("/getOneline/{trainId}")
public Response<List<GeneralPatTrainUserDto>> getOneLine(@RestQuery(TRAIN_ID) Integer trainId) {
  GeneralPatTrainRoomUserDto trainRoomUser = WebSocketGeneralKeyPatService.ROOM.get(trainId);
```

路径声明了模板变量 `{trainId}`，但参数用的是 `@RestQuery`（查询串），不是 `@RestPath`。`@RestQuery` 是 Quarkus REST 明确识别的注解，绑定来源就是 query string，路径段里的值被完全忽略。

调用方按声明的路径请求 `/GeneralKeyPatTrain/getOneline/5`（不带 `?trainId=`）时 `trainId` 为 `null`，而 `ROOM` 是 `ConcurrentHashMap`（`src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java:37`），`ConcurrentHashMap.get(null)` 直接抛 `NullPointerException`——紧接着那行 `if (trainRoomUser == null)` 的空值保护永远走不到。

**影响**：「综合组训-电子键 获取在线人数」这个接口在按声明路径调用时 100% 返回 500。只有调用方写成 `/getOneline/任意值?trainId=5` 才碰巧能用。

**建议修复方向**：改成 `@RestPath Integer trainId`（或 `@PathParam("trainId")`），并在 `ROOM.get` 前加 `trainId == null` 判断返回参数错误。

### P1-2 `roomgId` 拼写错误，三个"查询房间详情"接口取不到房间号

**位置**：
- `src/main/java/com/nip/controller/simulation/SimulationReceptRoomController.java:55`
- `src/main/java/com/nip/controller/simulation/SimulationReportRoomController.java:56`
- `src/main/java/com/nip/controller/simulation/SimulationRouterRoomController.java:73`

**现象**：三处都写成 `@RestQuery("roomgId") Integer roomId`——多了一个 `g`。同一个类里其它方法用的都是常量 `@RestQuery(ROOM_ID)`，而 `BaseConstants.ROOM_ID = "roomId"`（`src/main/java/com/nip/common/constants/BaseConstants.java:12`）。例如 `SimulationReceptRoomController.java:55` 与 `:62` 是相邻两个方法，一个读 `roomgId`、一个读 `roomId`。

**影响**：前端按常规传 `?roomId=123`，这三个接口拿到 `roomId = null`，进入 service 后要么查不到房间返回空详情、要么在 `findById(null)` 上抛异常。属于典型的"接口静默返回空数据"，排查成本高。

**建议修复方向**：三处统一改为 `@RestQuery(ROOM_ID)`。字面量参数名在本层应当一律换成 `BaseConstants` 常量，避免再出现同类拼写漂移。

### P1-3 `Map<String, Boolean>` 拆箱成 `boolean`，缺参即 NPE

**位置**：`src/main/java/com/nip/controller/TheoryKnowledgeExamController.java:74-75`

**现象**：

```java
public Response<List<FindAllExamDto>> findAllTheoryKnowledgeExam(Map<String, Boolean> map) {
  return theoryKnowledgeExamService.findAllTheoryKnowledgeExam(map.get("state"));
}
```

service 侧签名是原始类型（`src/main/java/com/nip/service/TheoryKnowledgeExamService.java:99`）：

```java
public Response<List<FindAllExamDto>> findAllTheoryKnowledgeExam(boolean mark)
```

请求体里没有 `state` 字段时 `map.get("state")` 返回 `null`，自动拆箱抛 `NullPointerException`。因为没有全局 `ExceptionMapper`（见 P1-6），最终是 500 + 非约定响应体。

**建议修复方向**：controller 内先判空并返回 `ResponseResult.error(ResponseCode.NULL_ERROR)`，或把 service 参数改成 `Boolean` 并在内部定义缺省语义。

### P1-4 学员端考试列表：同样的拆箱 NPE，且内层错误响应被丢弃

**位置**：`src/main/java/com/nip/controller/TheoryKnowledgeExamUserController.java:46-63`

**现象**：两处 `map.get("state")`（`:54` 与 `:59`）传给 `findAllTheoryKnowledgeExamUser(String token, boolean type)`（`src/main/java/com/nip/service/TheoryKnowledgeExamUserService.java:38`），缺 `state` 时拆箱 NPE，与 P1-3 同因。

更隐蔽的第二个问题在同两行：

```java
exam = theoryKnowledgeExamUserService.findAllTheoryKnowledgeExamUser(token, map.get("state")).getData();
```

service 返回的是完整的 `Response<List<AllExamDto>>`，这里只取 `.getData()`，然后在 `:63` 用 `ResponseResult.success(ret)` 重新包装。如果内层返回的是错误响应，`getData()` 是 `null`，错误码和错误信息被彻底丢弃，前端看到的是 `code=200, exam=null`——查询失败被伪装成"查到了空列表"。

**建议修复方向**：先判断内层 `Response.getCode()`，非成功则原样透传；`state` 判空后再调用。

### P1-5 分页参数：默认值必然越界、`rows` 为 0 会除零、`rows` 无上限

**位置**：
- DTO：`src/main/java/com/nip/dto/Page.java:15-28`、`src/main/java/com/nip/common/utils/Page.java:13-26`（两个类字段完全相同，见 P2-14）
- 消费方：`GroupNetTrainController.java:54`、`PostTelexPatTrainController.java:57`、`PostTickerTapeTrainController.java:56`、`TickerTapeTrainController.java:56`、`general/GeneralKeyPatController.java:52`、`general/GeneralTelexPatController.java:50`、`general/GeneralTickerPatController.java:63`

**现象**：DTO 默认值是

```java
private int page = 0;
private int rows = 20;
```

而所有消费方一律按 1-based 处理，统一写 `page.getPage() - 1`：

```java
.page(page.getPage() - 1, page.getRows())                    // GroupNetTrainService.java:73
}, page.getPage() - 1, page.getRows());                      // PostTelexPatTrainService.java:163
io.quarkus.panache.common.Page.of(page.getPage() - 1, ...)   // TickerTapeTrainService.java:98
```

于是三种失败路径：

1. **请求体为 `{}` 或省略 `page` 字段** → `page = 0` → 传入 `-1`。`io.quarkus.panache.common.Page` 构造器要求 index >= 0，抛 `IllegalArgumentException`；走 `SpecificationExecutor.findPage`（`src/main/java/com/nip/common/specification/SpecificationExecutor.java:79`）的那条路则是 `:84` 的 `setFirstResult(-1 * rows)`，JPA 规定负数抛 `IllegalArgumentException`。两条路都是 500。
2. **`rows` 传 0** → `SpecificationExecutor.java:93` 的 `ret.setTotalPage((total + pageSize - 1) / pageSize)` 除零抛 `ArithmeticException`；Panache 那条路 `Page.of(index, 0)` 也会抛 "Page size must be >= 1"。
3. **`rows` 无上限** → 传 `1000000`，`SpecificationExecutor.java:85` 的 `setMaxResults(1000000)` 会把整表实体加载进持久化上下文。同一方法 `:87` 还有 `entityManager.createQuery(...).getResultList().size()` 这种把全表拉回来只为数个数的写法，大表下直接把堆打满。

**影响**：7 个分页接口在参数缺失/异常时全部 500 而不是 400；`rows` 可被任意放大，单请求即可造成 OOM。

**建议修复方向**：在 `Page` 里把默认值改成 `page = 1`（与 1-based 的消费方一致），并在入口做钳制：`page = Math.max(1, page)`、`rows = Math.min(Math.max(1, rows), 200)`。同时删除两个重复 `Page` 类中的一个。

### P1-6 没有全局 `ExceptionMapper`，11 处未校验的类型转换会打穿统一响应封装

**位置**（全项目 `ExceptionMapper|@Provider|ServerExceptionMapper` 零命中）：

| 文件:行 | 代码 | 触发条件 |
|---|---|---|
| `TheoryKnowledgeController.java:74` | `Integer.parseInt(map.get(TYPE))` | `type` 缺失 → NPE；非数字 → `NumberFormatException` |
| `TheoryKnowledgeController.java:90` | 同上 | 同上（此方法连 `map.isEmpty()` 都没判，见 P2-18） |
| `TheoryKnowledgeController.java:145` | 同上 | 同上 |
| `TheoryKnowledgeController.java:151` | 同上 | 同上 |
| `TheoryKnowledgeExamController.java:93` | `Integer.parseInt(map.get(TYPE))` | 同上 |
| `TheoryKnowledgeExamController.java:106` | 同上 | 同上 |
| `UserController.java:84` | `data.get(USER_ID).toString()` | `userId` 缺失 → NPE |
| `GradingRuleController.java:62` | `data.get(ID).toString()` + `(Integer) data.get("status")` | `id` 缺失 → NPE；`status` 传字符串 → `ClassCastException` |
| `GradingRuleController.java:69` | `data.get(ID).toString()` | `id` 缺失 → NPE |
| `TelexPatController.java:39` | `(int) map.get("count")`、`(int) map.get("mistake")`、`(int) map.get(TYPE)` | 任一缺失 → 拆箱 NPE；传字符串 → `ClassCastException` |
| `TelegramTrainController.java:99` | `(String) map.get(TRAIN_ID)`、`(Integer) map.get(PAGE_NUMBER)` | 类型不符 → `ClassCastException` |

**现象**：这些异常没有任何地方接住。`JWTInterceptor` 的 `catch (Exception)`（`src/main/java/com/nip/common/interceptor/JWTInterceptor.java:82`）确实会捕获 `context.proceed()` 抛出的异常，但它返回的 `ResponseResult.error(...)` 对于声明返回 `Response<Boolean>` / `Response<Void>` 的方法是类型不兼容的对象；而且 Cable*、Device、PostTickerTapeTrainSetting、PostTrainGlobalRule、TelegraphKeyPatTrainSynthetical 这几个 controller 根本没有 `@JWT`（见附录 A-4），连这层都没有。

**影响**：整个错误路径上，前端拿到的不是 `{code, data, message, description}`，而是 Quarkus 默认的错误响应（HTTP 500 + 框架格式）。前端如果统一按 `res.code === 200` 判断，会在解析阶段就崩。这是"返回结构一致性"这一项最根本的破口——不是某个接口写错了，而是整层缺一个兜底。

**建议修复方向**：新增 `@Provider ExceptionMapper<Throwable>`（或 RESTEasy Reactive 的 `@ServerExceptionMapper`），统一把异常转成 `Response`：`IllegalArgumentException` → `PARAMS_ERROR`，其余 → `SYSTEM_ERROR` 并记录日志。同时把上表 11 处转换改为显式判空 + `ResponseCode.PARAMS_ERROR`，或直接换成强类型 DTO（项目里已有 75 个 DTO，这些 `Map<String, ?>` 入参是历史遗留）。

### P1-7 `uploadFileToNip` 丢弃上传的文件并返回成功

**位置**：`src/main/java/com/nip/controller/TheoryKnowledgeController.java:176-181`

**现象**：

```java
@POST
@Path("/uploadFileToNip")
@Operation(summary = "上传文件到NIP服务中")
public Response<TheoryKnowledgeDocumentContentVO> updateFileToNip(FileUpload file, HttpServerRequest request) {
  return ResponseResult.success(classifyService.updateFileToNip(file, request));
}
```

service 实现（`src/main/java/com/nip/service/TheoryKnowledgeClassifyService.java:88-93`）：

```java
public TheoryKnowledgeDocumentContentVO updateFileToNip(FileUpload dto, HttpServerRequest request)  {
  String token = request.getHeader(TOKEN);
  UserEntity userEntity = userService.getUserByToken(token);
  String id = userEntity.getId();
  return new TheoryKnowledgeDocumentContentVO();
}
```

文件参数从头到尾没被使用，`id` 取出来也没用，直接返回一个所有字段为 `null` 的空 VO，外层包成 `code=200, message="ok"`。

第二个缺陷：`FileUpload file` 参数**没有** `@RestForm` 注解。同项目里正确的写法在 `PostEnteringExerciseWordStockController.java:52`：`@RestForm("file") FileUpload file`。Quarkus REST 靠 `@RestForm` 识别 multipart 表单部分，缺注解时该参数按"请求体"处理，绑定结果与预期不符。

**影响**：调用方上传文件后收到 `code=200` 但内容为空，会误判为"上传成功但服务端解析不出内容"，实际是功能根本没实现。这是典型的假成功——比直接报错更难排查。

**建议修复方向**：要么补全实现（`@RestForm("file")` + 落盘/解析 + 返回真实 VO），要么删除端点。保留一个返回空对象的 `success` 是最差选项。

### P1-8 `exportTemplate` 返回 `void`，实现被整体注释，接口静默返回 204

**位置**：`src/main/java/com/nip/controller/TheoryKnowledgeQuestionController.java:86-91`

**现象**：

```java
@POST
@Path("/exportTemplate")
@Operation(summary = "导出模板")
public void exportTemplate(HttpServerResponse response) {
  theoryKnowledgeQuestionService.exportTemplate(response);
}
```

service 方法体从 `src/main/java/com/nip/service/TheoryKnowledgeQuestionService.java:254` 起全部是注释，没有任何可执行语句。

两个问题叠加：
1. 返回类型是 `void`，是**整个 controller 层唯一一个不返回 `Response<T>` 的端点**，破坏统一封装约定，客户端拿到 204 No Content。
2. 实现为空，"导出模板"这个功能不存在。

**影响**：前端点击"导出模板"没有任何反应也没有错误提示。

**建议修复方向**：删除该端点，或补全实现并改为返回 `jakarta.ws.rs.core.Response` 带 `Content-Disposition` 输出流。

---

## P2

### P2-1 `finish` 用 `catch (Exception)` 把所有异常压成"服务器错误"且不打日志

**位置**：`src/main/java/com/nip/controller/PostTelegramTrainController.java:104-126`

**现象**：`try` 块包着 `postTelegramTrainService.finish(dto)`，后接三个 catch：`IndexOutOfBoundsException` → `"数组越界错误"`、`IllegalArgumentException` → `e.getMessage()`、`Exception`（`:123-125`）→ `"服务器错误"`。最后这个 catch 会吞掉数据库异常、事务回滚异常、NPE，且**不打日志**——异常对象 `e` 完全未被使用。

**影响**：训练完成失败时服务端不留任何痕迹，只有前端一句"服务器错误"。而"完成训练"是会写成绩的操作，静默失败等于成绩丢失且无从追溯。

**建议修复方向**：至少 `log.error("finish train failed, id={}", dto.getId(), e)`；配合 P1-6 的全局 `ExceptionMapper` 后，这里的 catch-all 可以整块删掉。

### P2-2 `saveBaseTrain` 用无参 `error()` 丢弃失败原因

**位置**：`src/main/java/com/nip/controller/TickerTapeTrainController.java:98-106`

**现象**：`catch (Exception e) { return ResponseResult.error(); }`——异常对象未使用、未打日志，返回固定的 `SYSTEM_ERROR`。

**影响**：与 P2-1 同性质，"添加基础训练/科式训练"失败时无任何诊断信息。

**建议修复方向**：记录日志并保留原始信息。

### P2-3 `e.getMessage()` 可能为 null，导致 message 字段为 null

**位置**：
- `src/main/java/com/nip/controller/EnteringExerciseController.java:65-67`
- `src/main/java/com/nip/controller/EnteringTelexPatController.java:50-52`
- `src/main/java/com/nip/controller/EnteringTelexPatController.java:62-64`

**现象**：三处都是 `catch (Exception e) { return ResponseResult.error(e.getMessage()); }`。`NullPointerException` 等异常的 `getMessage()` 在很多情况下返回 `null`，此时响应体是 `{"code":500,"message":null}`。同时这种写法会把内部异常文本原样吐给客户端。

**影响**：前端拿到 `message: null` 无法展示；失败原因同样不落日志。

**建议修复方向**：`ResponseResult.error(Objects.requireNonNullElse(e.getMessage(), "操作失败"))`，并补 `log.error`。

### P2-4 catch 之后 `throw new RuntimeException(e)`，比不 catch 更糟

**位置**：`src/main/java/com/nip/controller/general/GeneralKeyPatController.java:63-72`

**现象**：

```java
public Response<GeneralKeyPatTrainVO> detail(@RequestBody GeneralKeyPatPageParamDto param) {
  try {
    return ResponseResult.success(patTrainService.detail(param));
  } catch (Exception e) {
    throw new RuntimeException(e);
  }
}
```

catch 唯一的作用是把异常再包一层抛出去。因为没有全局 `ExceptionMapper`（P1-6），结果仍是 500，但原始异常类型被 `RuntimeException` 这层包装淡化，日志里多一层无意义的栈。同一个类里的 `patDetail`（`:74-79`）、`getPage`、`statistic` 都没有这个 try-catch，说明是一处遗留。

**建议修复方向**：删除 try-catch。

### P2-5 20 个删除/写入操作使用 GET

**位置**（全部为改变服务端状态的操作）：

| 文件:行 | 端点 | 操作 |
|---|---|---|
| `UserController.java:167-172` | `GET /user/delete` | 删除用户 |
| `UserController.java:174-179` | `GET /user/resetPassword` | 重置密码 |
| `PostEnteringExerciseController.java:92-97` | `GET /postEnteringExercise/delete` | 删除训练 |
| `PostMilitaryTermTrainController.java:78-83` | `GET .../delete` | 删除训练 |
| `PostRadiotelephoneTrainController.java:98-103` | `GET .../delete` | 删除训练 |
| `PostTelegramTrainController.java:143-148` | `GET .../delete` | 删除训练 |
| `PostTelegraphKeyPatTrainController.java:96-101` | `GET .../delete` | 删除训练 |
| `PostTelexPatTrainController.java:97-102` | `GET .../delete` | 删除训练 |
| `PostTickerTapeTrainController.java:104-109` | `GET .../delete` | 删除训练 |
| `DeviceScoringRuleController.java:47-52` | `GET /deviceScoringRule/delete` | 删除规则 |
| `general/GeneralKeyPatController.java:170-176` | `GET .../startTrain` | 修改训练状态 |
| `general/GeneralKeyPatController.java:178-183` | `GET .../delete` | 删除训练 |
| `general/GeneralTelexPatController.java:126-132` | `GET .../startTrain` | 修改训练状态 |
| `general/GeneralTelexPatController.java:134-139` | `GET .../delete` | 删除训练 |
| `general/GeneralTickerPatController.java:105-111` | `GET .../startTrain` | 修改训练状态 |
| `general/GeneralTickerPatController.java:113-118` | `GET .../delete` | 删除训练 |
| `simulation/SimulationDisturdController.java:104-109` | `GET .../delete` | 删除训练 |
| `simulation/SimulationReceptRoomController.java:59-64` | `GET .../delete` | 删除训练 |
| `simulation/SimulationReportRoomController.java:61-66` | `GET .../delete` | 删除训练 |
| `simulation/SimulationRouterRoomController.java:100-105` | `GET .../delete` | 删除训练 |

**影响**：GET 按 RFC 7231 是安全方法，浏览器、代理、反向代理、预取器、爬虫都假定它无副作用。这些 URL 一旦出现在浏览器历史/书签/日志中被重放，就是一次真实的删除。同一项目里 `CableController.java:66`、`CableTypeController.java:49`、`EquipmentDeviceController.java:48`、`MilitaryTermDataController.java:55` 的删除用的是 POST，说明这是不一致而非有意设计。

**建议修复方向**：统一改为 `@DELETE`（或至少 `@POST`）。`JWTInterceptor.java:47` 的 `Access-Control-Allow-Methods` 已经包含 `DELETE`，改动无需调整 CORS。

### P2-6 `@RequestPass` 是一个没有任何人读取的死注解

**位置**：`src/main/java/com/nip/common/annotation/RequestPass.java:13`；使用点 `src/main/java/com/nip/controller/general/GeneralTickerSocketController.java:36` 与 `:44`

**现象**：注解的 javadoc 写的是"请求放行"，但全项目搜索 `RequestPass` 只有定义处和这两个使用处，`JWTInterceptor` 从头到尾没有检查过它。而 `GeneralTickerSocketController` 类上有 `@JWT`（`:22`），所以这两个标了"放行"的端点实际仍然要求 `token` + `deviceId` 双请求头。

**影响**：`/socket/generalTickerPatTrain/getByTrainIdAndUserId` 与 `.../updateTrainStatus` 从命名和注解看是给 WebSocket 侧内部回调用的，如果调用方（不带用户 token 的服务端组件）按"已放行"的预期发起请求，会收到 `CODE_203 token不能为空`。[INFERENCE] 放行意图来自注解 javadoc 与包名 `socket`，无其它文档佐证。

**建议修复方向**：二选一——在 `JWTInterceptor.execute` 里通过 `context.getMethod().isAnnotationPresent(RequestPass.class)` 提前 `return context.proceed()`；或确认不需要放行后删除注解类与两处使用。保留一个不生效的安全语义注解是最危险的状态。

### P2-7 `findUserInfo` 缺 `trainId` 时 `ConcurrentHashMap.get(null)` 抛 NPE

**位置**：`src/main/java/com/nip/controller/general/GeneralTickerPatTrainController.java:34-40`

**现象**：

```java
public Response<List<GeneralPatTrainUserDto>> findUserInfo(@RestQuery(TRAIN_ID) Integer trainId) {
  GeneralTickerPatTrainRoomUserModel trainRoomUser = WebSocketGeneralTickerPatService.PAT_ROOM.get(trainId);
  if (trainRoomUser == null) {
    return ResponseResult.success(new ArrayList<>());
```

`PAT_ROOM` 是 `ConcurrentHashMap`（`src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java:30`），不允许 null key，`get(null)` 抛 NPE。代码里那个 `if (trainRoomUser == null)` 说明作者以为查不到会返回 null——对 `HashMap` 成立，对 `ConcurrentHashMap` 不成立。

**影响**：不传 `trainId` 时返回 500 而不是空列表。与 P1-1 是同一类错误，区别是 P1-1 必然触发、这里取决于调用方是否漏传。

**建议修复方向**：`if (trainId == null) return ResponseResult.success(new ArrayList<>());` 前置。

### P2-8 `/user/test` 调试端点残留

**位置**：`src/main/java/com/nip/controller/free/UserController.java:55-59`

**现象**：`return ResponseResult.success(userService.getUserById(""));` —— 传空字符串查用户。位于 `free` 包（无 `@JWT`），任何人可访问。

**影响**：返回值是 `UserEntity`，含 `password`/`token` 字段（见附录 A-1）。虽然按空 id 查大概率返回 null，但这是一个不该存在于生产的端点。

**建议修复方向**：删除。

### P2-9 `/postTelegramTrain/test` 调试端点，且 `@Operation` 描述完全错误

**位置**：`src/main/java/com/nip/controller/PostTelegramTrainController.java:149-155`

**现象**：

```java
@GET
@Path(value = "test")
@Operation(summary = "删除训练")
public Response<List<PostTelegramTrainContentAddParam>> test() {
  return ResponseResult.success(postTelegramTrainService.test());
}
```

方法名 `test`、路径 `test`、返回训练内容参数列表，但 Swagger 上显示"删除训练"（复制自上面的 `delete` 方法未改）。

**影响**：Swagger 文档误导；调试端点暴露在生产 API 面上。

**建议修复方向**：删除该端点及 service 里对应的 `test()`。

### P2-10 `/demo/test` 调试端点，无鉴权，向 stdout 打印查询结果

**位置**：`src/main/java/com/nip/controller/free/DemoController.java:17-22`

**现象**：`GET /demo/test` 调用 `DemoService.test()`，后者执行 13 次带硬编码 ID 的 DAO 查询并逐条 `System.out.println`（`src/main/java/com/nip/service/DemoService.java:80-105`）。只读，但无鉴权。

**影响**：每次请求打十几行 stdout（绕过 `quarkus.log` 配置的轮转与级别），并对数据库产生无意义负载。可被反复调用。

**建议修复方向**：删除 `free/DemoController` 与 `DemoService`。

### P2-11 `@RestQuery` 包装类型参数缺失时 null 直接下传 service

**位置**（示例，同类写法在全层普遍存在）：
- `src/main/java/com/nip/controller/DeviceScoringRuleController.java:50` — `@RestQuery(ID) Integer id` → `ruleService.deleteRule(id)`
- `src/main/java/com/nip/controller/DeviceScoringRuleController.java:58` — `@RestQuery(DEVICE_ID) Integer deviceId` → `ruleService.findAllByDeviceId(deviceId)`
- `src/main/java/com/nip/controller/GroupNetTrainController.java:62` — `@RestQuery(value = ID) Integer id` → `trainService.detail(id)`
- `src/main/java/com/nip/controller/simulation/SimulationRouterRoomController.java:94-97` — `roomId`/`pageNumber`/`userId` 三个都无校验

**现象**：`@RestQuery` 绑定包装类型时，参数缺失得到 `null`；参数存在但非数字（如 `?id=abc`）会在转换阶段失败，按 JAX-RS 规范对 query 参数返回 404——既不是 400，也不是统一封装。

**影响**：controller 层没有承担任何入参校验职责，全部下沉到 service/dao，最终表现为 500 或 404。以 `GroupNetTrainController.java:62` 为例，`trainService.detail(null)` 进入 `Optional.ofNullable(trainDao.findById(null))` 会在 Hibernate 层抛异常。

**建议修复方向**：对必填参数统一前置判空返回 `ResponseCode.PARAMS_ERROR`；或引入 Bean Validation（`quarkus-hibernate-validator`）配合 `@NotNull` 与一个 `ConstraintViolationException` mapper。

### P2-12 HTTP 状态码恒为 200，业务码只在响应体里

**位置**：`src/main/java/com/nip/common/response/ResponseResult.java:29-55`（全部 `error` 重载）；全部 controller 的返回语句

**现象**：`ResponseResult.error(...)` 返回的仍是一个普通 POJO `Response`，RESTEasy 序列化后 HTTP 状态码是 200。业务失败（500 服务器错误、202 参数错误）全部藏在响应体的 `code` 字段里。

**影响**：与 P1-6 叠加后出现最坏组合——**被捕获的业务错误返回 HTTP 200**，**未捕获的异常返回 HTTP 500 且响应体不是约定结构**。前端无法用统一策略处理错误：既不能只看 HTTP 状态码，也不能只看 `body.code`（因为 500 时 body 里根本没有 `code`）。这是"错误码语义一致性"上最需要拉齐的一点。

**建议修复方向**：定一个口径并全层贯彻。推荐保留 HTTP 200 + 体内业务码（改动最小），但必须配合 P1-6 的全局 `ExceptionMapper`，让异常路径也产出同样结构的 200 响应体。

### P2-13 `ResponseCode` 里 202/204/200 各有两套冲突语义

**位置**：`src/main/java/com/nip/common/constants/ResponseCode.java:10-19`

**现象**：

```java
SUCCESS(200, "ok", ""),
PARAMS_ERROR(202, "请求参数错误", ""),
NULL_ERROR(204, "请求参数为空", ""),
SYSTEM_ERROR(500, "服务器错误", ""),
CODE_200(200,"ok" , "" ),
CODE_202(202,"本次授权已过期" , "" ),
CODE_204(204,"设备标识不能为空" , "" ),
CODE_206(206,"账号登录凭证异常" , "" ),
CODE_500(500,"服务器错误" , "" );
```

同一个数字码 202 既表示"请求参数错误"（业务层用，如 `PostTelegramTrainController.java:110`）又表示"本次授权已过期"；204 同理，既是"请求参数为空"（`UserController.java:68`、`free/UserController.java:44`、`PostTelegramTrainController.java:107`）又是"设备标识不能为空"（`JWTInterceptor.java:70-71`）。`SUCCESS` 与 `CODE_200`、`SYSTEM_ERROR` 与 `CODE_500` 也是同码重复定义。

**影响**：前端拿到 `code=202` 无法判断该弹"参数错误"还是该跳登录页。

**建议修复方向**：合并重复枚举项，给鉴权类错误单独分配码段（如 4xxx），业务参数错误另用一段。

### P2-14 两个字段完全相同的 `Page` 类并存

**位置**：`src/main/java/com/nip/dto/Page.java:15-28` 与 `src/main/java/com/nip/common/utils/Page.java:13-26`

**现象**：字段（`page`/`rows`/`desc`/`sortBy`）、默认值、注释、作者签名全部一致，唯一差别是 `dto.Page` 多了 `@RegisterForReflection`。使用上也是分裂的：`TickerTapeTrainController.java:7` 用 `dto.Page`，其余 6 个分页 controller 用 `common.utils.Page`。

另外 `common/Page.java:13`（泛型分页结果）与 `common/PageInfo.java:19`（另一个泛型分页结果）也是两个语义重叠的类，`PageInfo` 才是被 controller 返回类型实际使用的那个，`common/Page` 在 controller 层零使用。

**影响**：新增分页接口时无从判断该 import 哪个；两份默认值将来会漂移。

**建议修复方向**：保留 `common/utils/Page`（使用更广）并补 `@RegisterForReflection`，删掉 `dto/Page` 与未使用的 `common/Page`，统一 import。

### P2-15 `@Path("/telexPat")` 被两个 Controller 同时占用

**位置**：`src/main/java/com/nip/controller/TelexPatController.java:25` 与 `src/main/java/com/nip/controller/TexPatTrainController.java:33`

**现象**：两个类的根路径都是 `/telexPat`。子路径不重叠（`saveTelexPat`/`findTelexPatById`/`deleteTexPatByToken` vs `saveTexPatTrain`/`findTexPatTrainByToken`/`findTexPatTrainById`/`statisticalPage`/`lastPatTrain`/`deleteById`），所以运行时不冲突，但 Swagger 上两个 `@Tag`（"发报训练" / "岗前训练-发报训练-电传拍发"）指向同一路径前缀。类名 `TelexPat` 与 `TexPat` 只差一个字母，方法名 `findTelexPatById` 与 `findTexPatTrainById` 极易混淆。

**影响**：可维护性风险——将来任一侧新增子路径时可能撞车。Quarkus 新版本已有重复端点的构建期检测，届时会直接构建失败。

**建议修复方向**：给其中一个换根路径（如 `/telexPatTrain`），并统一类名拼写。

### P2-16 端点路径使用中文

**位置**：`src/main/java/com/nip/controller/general/GeneralTickerPatTrainController.java:35`

**现象**：`@Path("/综合训练-手键拍发Api")`，完整路径是 `/openApi/generalTickerPatTrain/综合训练-手键拍发Api`。

**影响**：URL 需要 percent-encoding，不同客户端/代理/日志系统的编码处理不一致；curl、Nginx 日志、抓包排查都会看到 `%E7%BB%BC%E5%90%88...` 这样的路径。同一个类里另一个端点 `/findTrainInfo`（`:53`）是正常英文命名。

**建议修复方向**：改为 `/findUserInfo`（与方法名一致），中文说明留在 `@Operation(summary=...)` 里。

### P2-17 引入了错误的 `PathParam` 注解

**位置**：`src/main/java/com/nip/controller/simulation/SimulationRouterRoomController.java:15` 与 `:58`

**现象**：

```java
import jakarta.websocket.server.PathParam;   // 应为 jakarta.ws.rs.PathParam
...
public Response<SimulationRouterRoomUserVO> getRoomUserList(@PathParam(ROOM_ID) Integer roomId) {
```

`jakarta.websocket.server.PathParam` 是 WebSocket 端点专用注解，Quarkus REST 不认识它。这是全项目 REST controller 里唯一一处该导入（其余 6 处都在 `ws/` 包的 `@ServerEndpoint` 类里，用法正确）。

**为什么现在还能工作**：Quarkus REST 的绑定规则是"路径参数注解可以省略"——无可识别注解的参数，若其名称与 URI 模板变量同名则按路径参数绑定。这里参数名 `roomId` 恰好等于 `{roomId}`，且 `pom.xml:180-182` 开启了 `-parameters`，所以运行时侥幸正确。注解里的 `ROOM_ID` 值被完全忽略。

**影响**：这是一处"靠巧合工作"的代码。一旦有人重命名参数（如改成 `id`）、或去掉 `-parameters` 编译参数，绑定会静默退化为"请求体参数"（GET 无 body → null），而注解看上去还在，排查会非常困难。

**建议修复方向**：`import jakarta.ws.rs.PathParam;`，或统一使用项目里更常见的 `@RestPath`。

### P2-18 `getBasicTheory` 与 `getBasicTheoryOpen` 的空入参处理不一致

**位置**：`src/main/java/com/nip/controller/TheoryKnowledgeController.java:57-91`

**现象**：`getBasicTheory`（`:58`）开头有 `if (map.isEmpty()) { return knowledgeService.getAll(0, null, null); }`（`:60-62`）兜底；紧邻的 `getBasicTheoryOpen`（`:78`）逻辑几乎完全相同（同样解析 `difficulty`/`specialty`/`type`），却**没有**这个兜底分支，直接走到 `:90` 的 `Integer.parseInt(map.get(TYPE))`。

同时 `getBasicTheory` 的兜底也不完整：`map` 非空但缺 `type` 时（例如只传了 `difficulty`），仍然会在 `:74` 的 `parseInt(null)` 上 NPE。

**影响**：两个语义几乎相同的接口，对同一种异常输入一个返回默认数据、一个返回 500。

**建议修复方向**：抽出公共的参数解析方法，统一按"缺 type 视为 0"或"缺 type 返回参数错误"处理。

### P2-19 注入了未使用的依赖

**位置**：
- `src/main/java/com/nip/controller/UserController.java:39-42` — 构造器接收 `UserTrainStatisticsService userTrainStatisticsService`，方法体里只赋值了 `userService`，该参数被直接丢弃；`:14` 的 import 因此也是多余的。
- `src/main/java/com/nip/controller/CableController.java:29-30`、`CableFloorController.java:22,24`、`CableTypeController.java:23,25` — 每个类都注入 `CableService` + `CableTypeService` + `CableFloorService` 三个，但各自只用其中一个（`CableController` 只用 `cableService`，`CableFloorController` 只用 `cableFloorService`，`CableTypeController` 只用 `cableTypeService`）。

**影响**：CDI 每次实例化都要解析注入未使用的 bean；阅读时误以为存在跨 service 调用。`UserController` 那处更糟——参数被静默丢弃，将来有人想用 `userTrainStatisticsService` 会拿到 NPE 而不是编译错误。

**建议修复方向**：删除未使用的构造器参数与字段。

---

## P3

### P3-1 `upLoadFile` 是一个返回成功的空方法

**位置**：`src/main/java/com/nip/controller/TheoryKnowledgeQuestionController.java:79-84`

`@Operation(summary = "上传题库-新框架不做上传功能")` + 方法体只有 `return ResponseResult.success();`。既然明确不做，端点应当删除而不是留一个假成功。

### P3-2 使用裸类型 / 通配符 `Response`

**位置**：`UserController.java:99`（裸 `Response`）、`GroupNetTrainController.java:70`、`general/GeneralKeyPatController.java:84`、`:92`、`general/GeneralTelexPatController.java:78`、`general/GeneralTickerSocketController.java:45`、`test/TestController.java:31`（`Response<?>`）

其余 200 余个端点都写了具体泛型。裸类型会让 OpenAPI schema 生成退化为 `object`，前端拿不到类型定义。建议补全实际类型（多数是 `Response<Void>`）。

### P3-3 `@Operation` 描述与实现不符

**位置**：
- `PostTelegramTrainController.java:151` — 路径 `test` 却标注"删除训练"（见 P2-9）
- `PostTelegramTrainController.java:95` — 路径 `stop` 标注"重置训练"，而 `TickerTapeTrainController` 里 `stop`/`reset` 是两个不同语义
- `general/GeneralKeyPatController.java:65` 与 `:76` — `detail` 与 `patDetail` 的 summary 都是"查询训练详情"
- `general/GeneralTelexPatController.java:56` 与 `:63` — 同上
- `general/GeneralTickerPatTrainController.java:54` — 标注"查询房间人员信息"，实际返回所有房间

Swagger 是这套 API 唯一的文档来源，描述错误等于文档错误。

### P3-4 未使用的 `HttpServerResponse` 参数，且直接返回实体

**位置**：`src/main/java/com/nip/controller/TheoryKnowledgeQuestionController.java:94-98`

```java
public Response<List<TheoryKnowledgeQuestionEntity>> exportQuestionByLevelId(HttpServerResponse response, Map<String, String> map) {
```

`response` 一路传到 `TheoryKnowledgeQuestionService.java:492` 也没被使用（该方法只做 `findAllByLevelId`）。这是从旧的"服务端写流导出"改成"返回数据由前端导出"后遗留的参数。同时该方法返回的是**实体**列表而非 DTO。建议删除参数并改用 VO。

### P3-5 `Test.java` 是一个带 `main` 的实验类，放在 controller 包

**位置**：`src/main/java/com/nip/controller/test/Test.java:18-63`

内容是 GZIP 压缩/解压的耗时实验，`main` 里三行 `System.out.println` 打时间差。它不是 controller，不该在 controller 包，也不该在 `src/main`。建议删除（若 GZIP 工具需要保留，项目里已有 `common/utils/GZipUtil.java`）。

### P3-6 路径与方法命名拼写问题

**位置**：
- `src/main/java/com/nip/controller/PostRadiotelephoneTrainController.java:70` — `@Path("/listPge")`，方法名却是 `listPage`。其余所有列表端点都是 `/listPage`，这一个是 `listPge`，前端必须为它单独记一个例外。
- `src/main/java/com/nip/controller/simulation/SimulationRouterRoomController.java:82-85` — `sendFinish` 用 `@POST` 但只有 `@RestQuery` 参数、无请求体，与同类"写操作走 body"的约定不一致。
- `src/main/java/com/nip/controller/TheoryKnowledgeController.java:111` 与 `:117` — 两个方法都叫 `getById`（重载），一个走 `/getById`、一个走 `/getByIdAndToken`，阅读时容易看错。

---

## 附录：已接受安全风险（内网部署口径，不计入 P0/P1/P2 统计）

按 `docs/reviews/2026-08-15-situation-display-orbit-placard-review.md` 第 1 节的口径，以下属于认证授权、凭据、匿名访问、个人信息一类的纯安全项，记录但不计入问题总数。

### A-1 `UserEntity` 被直接作为响应返回，含密码与登录 token

**位置**：`src/main/java/com/nip/entity/UserEntity.java:57-66`

```java
private String idCard;    // :58  身份证
private String password;  // :62
private String token;     // :64
private String deviceId;  // :66
```

**直接返回该实体的端点**：

| 文件:行 | 端点 | 返回 |
|---|---|---|
| `UserController.java:90-93` | `POST /user/getAllUser` | `List<UserEntity>` —— **一次性导出全部用户的密码与 token** |
| `UserController.java:119-122` | `POST /user/getUserById` | `UserEntity` |
| `UserController.java:133-136` | `POST /user/getUsersByIds` | `List<UserEntity>` |
| `UserController.java:105-108` | `POST /user/getUsersByUserNameStartingWith` | `List<UserEntity>` |
| `UserController.java:140-143` | `POST /user/getUsersByToken` | `UserEntity` |
| `UserController.java:74-77` | `POST /user/importUser` | `List<UserEntity>` |
| `free/UserController.java:56-59` | `POST /user/test`（**无鉴权**，见 P2-8） | `UserEntity` |

值得单独指出：`token` + `deviceId` 正是 `JWTInterceptor.java:75` 的 `existsUserByTokenAndDeviceId(token, deviceId)` 的全部校验材料。拿到其他人的这两个字段就等同于完整冒充该用户，`getAllUser` 一次调用即可拿到全库。这一条虽按口径归入附录，但在内网中仍是最值得优先处理的一项。

**建议方向**：新增 `UserVO`（去掉 `password`/`token`/`deviceId`/`idCard`）作为出参；或在 `UserEntity` 这四个字段上加 `@JsonIgnore`（注意登录流程若依赖返回 token，需单独用一个专门的登录响应 DTO 承载）。

### A-2 多个 DTO 内嵌 `UserEntity` / `RoleEntity`，把 A-1 的泄漏面扩大

**位置**：
- `src/main/java/com/nip/dto/UserInfoDto.java:18` — `private UserEntity user;`；由 `free/UserController.java:38-47` 的登录接口和 `UserController.java:126-129` 的 `/user/getUserAndRoleById` 返回
- `src/main/java/com/nip/dto/RoleInfoDto.java:21` — `private List<UserEntity> users;`；由 `RoleController.java:51-54` 的 `POST /role/getRoleAll` 与 `:58-61` 的 `GET /role/getRoleById` 返回，**按角色批量导出用户凭据**
- `src/main/java/com/nip/dto/vo/ComprehensiveVO.java:17` — `private UserEntity userEntity;`；由 `ComprehensiveController.java:39-43` 的 `GET /comprehensive/getUserOverallInfo` 返回
- `src/main/java/com/nip/dto/RoleMenusDto.java:17`、`SaveRoleDto.java:17` — 内嵌 `RoleEntity`

**建议方向**：与 A-1 一并整改。项目里已有 `dto/sql/FindUserByRoleIdDto.java:19` 这样只含 `id`/`userName`/`userAccount` 的安全投影，可直接复用。

### A-3 `free` 包下与受保护端点共用 `/user` 前缀，且以请求体里的 `userId` 取数

**位置**：`src/main/java/com/nip/controller/free/UserController.java:26-84`

`free.UserController`（`:26` `@Path("/user")`，无 `@JWT`）与 `com.nip.controller.UserController`（`:33` `@Path("/user")`，有 `@JWT`）共用同一路径前缀。前者其中三个端点直接读请求体里的 `userId`：

- `:62-69` — `POST /user/getUserTrainDurationStat`，`map.get(USER_ID)`
- `:73-76` — `POST /user/getRecentHandKeyTrains`，`map.get(USER_ID)`
- `:80-83` — `POST /user/getRecentElectronicKeyTrains`，`map.get(USER_ID)`

任何人可以任意指定 `userId` 查询他人的训练时长与成绩，没有任何越权校验。同前缀混布还带来一个可维护性问题：从路径 `/user/xxx` 完全看不出该端点是否需要鉴权。

**建议方向**：把真正需要匿名的端点（`/login`）收敛到独立前缀（如 `/auth`）；统计类端点改为从 token 推导 `userId`（项目里 `userService.getUserByToken(token)` 已是通用做法）。

### A-4 未启用 `@JWT` 的 Controller 清单

| 文件:行 | 路径前缀 | 说明 |
|---|---|---|
| `CableController.java:24` | `/cable` | 含 `POST /cable/save`、`POST /cable/delete` |
| `CableFloorController.java:18` | `/cable/floor` | 只读 |
| `CableTypeController.java:19` | `/cable/type` | 含 `POST /cable/type/save`、`/delete` |
| `DeviceController.java:29` | `/device` | 含 `save`、`delete`、`addDeviceDescription` |
| `PostTickerTapeTrainSettingController.java:24` | `/postTickerTapeTrainSetting` | 含 `addOrUpdate` |
| `PostTrainGlobalRuleController.java:18` | `/postTrainGlobalRule` | 含 `addRule`、`deleteById` |
| `TelegraphKeyPatTrainSyntheticalController.java:28` | `/telegraphKeyPatTrainSynthetical` | 含 `save`、`begin`、`finish` |
| `test/TestController.java:18` | `/test` | 见 P0-1 |
| `free/DemoController.java:11`、`free/ToolsController.java:28`、`free/UserController.java:26` | `/demo`、`/tools`、`/user` | 按包名是有意匿名 |

`DeviceController.java:43-45`（`save`）、`:67-70`（`addDeviceDescription`）与 `TelegraphKeyPatTrainSyntheticalController` 里的多个方法特别值得注意：它们从 header 取 `token` 并传给 service 用于确定"当前用户"，但因为类上没有 `@JWT`，这个 token 从未被验证过——伪造任意 token 字符串即可指定操作者身份。[INFERENCE] 是否有意为之无法从代码判断，但与同类 controller 的写法不一致。

### A-5 CORS 全开 + Swagger UI 生产启用

**位置**：`src/main/resources/application.yml:11-15`、`:36-38`

```yaml
cors:               # :11
  ~: true
  headers: '*'
  origins: '*'      # :14
  methods: OPTIONS, GET, POST, DELETE, PUT, PATCH
swagger-ui:         # :36
  enable: true      # :37
```

另有 `src/main/java/com/nip/common/interceptor/JWTInterceptor.java:46-50` 在拦截器里第二次手工设置 CORS 头，其中 `:46` 的 `Access-Control-Allow-Origin` 直接回显请求方的 `Origin` 头，同时 `:49` 设置 `Access-Control-Allow-Credentials: true`——这个组合等于对任意来源开放带凭据的跨域请求。两套 CORS 配置并存本身也是维护隐患。

Swagger UI 启用放大了 P0-1 的可触发性（任何能访问 18001 端口的人都能在 UI 上点按钮触发写操作）。

**建议方向**：`origins` 收敛到实际前端域名；删除 `JWTInterceptor` 里的手工 CORS 头（与 Quarkus 内建 CORS filter 重复）；生产 profile 关闭 Swagger UI。

---

## 附：本次未发现问题的检查项

- **fastjson 类型安全**：项目未依赖 fastjson，controller/dto 层无 `JSON.parseObject` / `JSONObject` 风格的动态解析。入参走 Jackson（`quarkus-rest-jackson`），`TheoryKnowledgeController` 里的手写解析用的是 Gson（`common/utils/JSONUtils.java:32-37`，已在 P1-6 覆盖）。
- **文件上传的路径拼接 / 文件名处理 / 流关闭**：3 个相关端点（`TheoryKnowledgeController.java:179`、`PostEnteringExerciseWordStockController.java:52`、`TheoryKnowledgeQuestionController.java:82`）的实现要么为空、要么被整体注释（`PostEnteringExerciseWordStockService.java:48-134` 整段是注释），不存在落盘或流操作，因此没有路径穿越或流泄漏。真正的缺陷是"假成功"，已记为 P1-7、P1-8、P3-1。
- **PUT/POST 混用**：全层没有使用 `@PUT`/`@PATCH`，所有写操作统一走 `@POST`（除 P2-5 列出的 20 个误用 `@GET` 的）。不存在 PUT/POST 语义混用。
- **上传大小限制**：`application.yml` 未配置 `quarkus.http.limits.max-body-size`，沿用 Quarkus 默认（10M）。由于没有实际的上传处理逻辑，暂不构成问题；补全上传功能时需一并显式配置。
- **路径冲突**：52 个 controller 的完整路径（类 `@Path` + 方法 `@Path`）逐一比对，无完全重复的端点。`/user` 与 `/telexPat` 两个前缀被两个类共用，但子路径不重叠，已分别记为 A-3 与 P2-15。
