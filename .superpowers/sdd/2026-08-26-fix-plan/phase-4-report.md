# Phase 4 报告：异常边界与可观测性

结论：Task 4.1-4.4 全部完成，三个提交，新增 7 个集成/单元测试 + 受影响旧测试共 28 个全绿，两条门禁 grep 零命中。

## 提交

| 提交 | 内容 |
|---|---|
| `9cdf836 fix(except-1)` | Task 4.1+4.3 同批：`common/exception/` 新建 GlobalExceptionMapper(Throwable→HTTP500+SYSTEM_ERROR)、ValidationExceptionMapper(IllegalArgumentException→200+CODE_500+原消息)、IllegalStateExceptionMapper、InvalidTitleExceptionMapper（同构）、UnauthorizedException+UnauthorizedExceptionMapper(→200+code203)；JWTInterceptor `context.proceed()` 移出 try，catch 收窄为拦截器自身逻辑，日志改 `log.error("jwt fail from {}.{}", 类, 方法, exception)` 带堆栈 |
| `50979e8 fix(except-2)` | Task 4.2：`getUserByToken` 判 null 抛 UnauthorizedException；全仓 81 调用点逐一核对（31 文件），10 处 `try{}catch(Exception)→error()` 内的调用点前置 `catch(UnauthorizedException){throw e;}` 守卫（TelegramTrainService×4、TelexPatService×3、TelexPatTrainService×2、GeneralTelexPatService×1）；TheoryKnowledgeService.gradeCount 显式 null 分支死码删除 |
| `1935f9e fix(except-4)` | Task 4.4：29 处 `log.error("…{}", e.getMessage())` → `log.error("…", e)`（含尾部 `：{}`/`:{}` 占位一并去除；PostTelegraphKeyPatTrainService:208 多参形态单独处理为 `log.error("details index:{},i:{}", index, i, e)`）；空 catch 8 处补齐；PostMilitaryTermTrainService.listPage 去掉 catch-返-emptyList 改直抛 |

## Task 4.2 影响面核对（81 调用点）

- grep 全仓 `getUserByToken` 命中 31 个文件 81 处（80 service + UserController:143），逐文件过目。
- **10 处 catch(Exception)→error() 加守卫**：TelegramTrainService getAll/getFloorContentByFloorIdAsync×2/save、TelexPatService saveTelexPat/findById/deleteTexPatByToken、TelexPatTrainService saveTexPatTrain/findTexPatTrainByToken、GeneralTelexPatService findAll（该处原 catch 会把异常包成 RuntimeException(cause)，守卫防止 203 变 500）。
- **TheoryKnowledgeService:531-533**（gradeCount 的 `if (userEntity == null) return error("Invalid token")`）：改抛后为死码，已删。
- **其余 70 处**：无 catch 包裹，异常自然传播到 Mapper；含非 @JWT 的 DeviceService:41/:92——行为从 NPE(HTTP 500 裸响应) 变为 200+code203 信封，**计划内预期改进**，已有集成测试锁定。

## Task 4.4 明细

**日志（29 处，两条门禁的第 2 条）**：SpecificationExecutor×2、PojoUtils×2、PostTelexPatTrainService×1、TelegramTrainService×3、TelexPatService×3、TelexPatTrainService×1、TestPaperService×1、TheoryKnowledgeExamService×3、UserService×2（addUserRole/login）、StartWebSocket×1、WebSocketService×2（onError/send）、WebSocketSimulationService×3、WebSocketUnionService×1、WebSocketGeneral{KeyPat,TelexPat,TickerPat}×3、PostTelegraphKeyPatTrainService×1。逐行 diff 过目，无悬空 `{}` 残留（保留 `{}` 的行均有对应实参且异常对象在末位）。

**空 catch（8 处）**：
- LifecycleApplication:33 → `LOG.warn("banner 打印失败", e)`（纯装饰路径，log 即可）。
- ToolsController:60 → `log.warn(..., e)`（补 @Slf4j）。
- TickerPatUtils:293 → `log.warn("patKeys 非 JSON（index={}），按纯文本逐字符拆分", i, e)`（补 @Slf4j）。**不改重抛**：该 catch 是 Phase 1 有意保留的"纯文本 patKeys 协议容忍"（:295 注释），重抛会破坏既有协议行为；虽在 @Transactional 写路径内，但吞掉后走的是显式降级分支而非静默丢数据。
- UserTrainStatisticsService ×5（补 @Slf4j）：parseTime 第一段 ISO 失败为正常回退（注释说明，不打日志防噪音），第二段失败 `log.warn` 后返回 null（修 P2-3 静默全时段）；sumElectronicKey/sumReceive×2 的时长解析失败逐条 `log.warn` 带原始值（修 P2-4）。均为只读统计路径，无 @Transactional 写路径归属，不需重抛。
- PostMilitaryTermTrainService:424-427（原 :402-404 项）：删除 try/catch-返-emptyList，异常直抛到 Mapper（"登录失效"不再伪装成"暂无数据"）。

**@Transactional 写路径核对**：对照 silent-failures 审计表逐条过——P0-4/5/6/7、P1-9/12/13/14 的写路径 catch 均已在 Phase 1/2 处理（本轮 grep 未再命中空 catch/吞写异常形态）；本轮 8 处空 catch 中唯一位于写路径的是 TickerPatUtils:293（处置见上）。Phase 1 已删的三个 catch 未回退。

## 集成测试（ExceptionBoundaryTest，7 个用例）

| 用例 | 断言 |
|---|---|
| 无 token 调 @JWT `/api/menus/getMenusAll` | HTTP 200 + code 203（拦截器自身校验保留） |
| 过期 token 调 @JWT 同端点 | HTTP 200 + code 206（拦截器 exists 校验保留） |
| 过期 token 调非 @JWT `/api/device/save` | HTTP 200 + code 203（UnauthorizedExceptionMapper，Task 4.2 新链路） |
| 校验失败（permissions=null 的 addMenu） | HTTP 200 + code 500 + 原提示消息（ValidationExceptionMapper 接管，不再 SYSTEM_ERROR 兜底） |
| 未知异常（menus=null → NPE 的 addMenu） | HTTP 500 + code 500 + "服务器错误"（GlobalExceptionMapper） |
| getUserByToken 未知 token | 抛 UnauthorizedException（单元） |
| findTexPatTrainByToken 未知 token | UnauthorizedException 穿透 catch-all 守卫重抛（单元） |

说明：brief 写的"过期 token 调 @JWT 接口 → 203"在拦截器语义下实际是 **206**（203 = token 为空）——过期 token 到不了 getUserByToken，@JWT 路径由拦截器先拦。203 信封的 Mapper 链路在非 @JWT 的 DeviceController 路径上可达并已按此测试。测试基建：Fixtures.user 增加 deviceId 重载；拦截器 response.send 裸 JSON 无 Content-Type，测试显式 `RestAssured.defaultParser = Parser.JSON`。

## 受影响旧测试

无需修改任何旧测试断言：既有测试全部走 service 层直调（不经拦截器/Mapper），且均用已 seed 的有效 token。全量受影响集（11 类 28 用例）跑绿：ExceptionBoundaryTest、MenusServiceTest、TestPaperServiceTest、TheoryKnowledge{,Exam}ServiceTest、TelegramTrainServiceTest、TelexPatTrainStatisticalServiceTest、PostMilitaryTermTrainServiceTest、PostTelegraphKeyPatTrainServiceTest、TickerPatUtils{,Characterization}Test。

## 门禁输出

```
$ grep -Prn 'catch\s*\([^)]+\)\s*\{\s*\}' src/main/java        # 零命中（含跨行形态复核，同样零命中）
$ grep -rn 'getMessage())' src/main/java | grep log\.           # 零命中
```

日志格式抽查：全部替换行逐条 diff 过目，无悬空 `{}`。

## Concerns

- GlobalExceptionMapper 声明在 Throwable 上：RESTEasy Reactive 内置的 WebApplicationException Mapper 更具体，404/405 等默认语义不受影响（集成测试期间 404 行为正常，修路径前的 404 响应即为内置 Mapper 产出）。
- 拦截器对空 token/206 的响应仍是 `response.send` 裸 JSON（无 Content-Type），前端现行契约如此，未动。
- DeviceController 等非 @JWT 路径的 NPE→203 行为变化对前端是改进（可识别的重登信号），但若有客户端依赖旧 500 行为需前端知悉。
