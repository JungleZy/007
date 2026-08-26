## Phase 4：异常边界与可观测性

**Files:** Create `common/exception/GlobalExceptionMapper.java`、`ValidationExceptionMapper.java`、`UnauthorizedException.java`、`UnauthorizedExceptionMapper.java`；Modify `JWTInterceptor.java:42-86`、`UserService.java:479-481`、全仓 log/catch（机械化）。

**前置事实（评审核实）**：JWTInterceptor 的 try 包住 `context.proceed()`（:44-85），@JWT 端点的一切异常现被兜成 200+SYSTEM_ERROR——**Mapper 在收窄拦截器之前收不到 @JWT 端点异常**。Task 4.1 与 4.3 必须同批落地。

- [ ] **Task 4.1**: 三个 Mapper：

```java
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionMapper.class);
  @Override
  public jakarta.ws.rs.core.Response toResponse(Throwable e) {
    log.error("unhandled", e);
    return jakarta.ws.rs.core.Response.serverError()
        .entity(ResponseResult.error(ResponseCode.SYSTEM_ERROR)).build();
  }
}

@Provider // 校验类异常保持 200+业务码+原提示消息（契约约束）
public class ValidationExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
  @Override
  public jakarta.ws.rs.core.Response toResponse(IllegalArgumentException e) {
    return jakarta.ws.rs.core.Response.ok(
        ResponseResult.error(ResponseCode.CODE_500, e.getMessage(), e.getMessage())).build();
  }
}
// IllegalStateException、InvalidTitleException 同构各一个（或共同基类一个），码值对照原 catch 返回值定

@Provider
public class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {
  @Override
  public jakarta.ws.rs.core.Response toResponse(UnauthorizedException e) {
    // 契约兼容：HTTP 200 + code 203（与 JWTInterceptor:61-64 现行为一致）
    return jakarta.ws.rs.core.Response.ok(
        ResponseResult.error(ResponseCode.CODE_203)).build();
  }
}
```

（`error` 有 7 个重载，`error(ResponseCode)`/`error(ResponseCode,String,String)` 均存在——已核实。）
- [ ] **Task 4.2**: `getUserByToken` 判 null 抛 `UnauthorizedException`。**影响面（评审实测）：81 个调用点**（80 个 service + UserController:143），不是"52 个 @JWT controller"：
  1. `lsp references` 列全 81 点逐一核对；
  2. **10 处**位于 `try{}catch(Exception){return error()}` 内（TelegramTrainService/TelexPatService 等）——在这些 catch 前加 `catch (UnauthorizedException e) { throw e; }` 或收窄原 catch，防止 203 被吞成通用错误；
  3. `TheoryKnowledgeService:526-529` 显式 null 分支变死码——删除；
  4. 非 @JWT 的 `DeviceController` → DeviceService:41/:92 路径 token 未经拦截器校验，改抛后行为从 NPE 变 203 信封——记录为预期改进。
  集成测试：过期 token 调任一 @JWT 接口，断言 HTTP 200 + code 203。
- [ ] **Task 4.3**: JWTInterceptor 收窄——`context.proceed()` 移出 try（拦截器只 catch 自身 token 解析/校验逻辑的异常），使端点异常到达 Mapper；catch 分支 `log.error("jwt fail from {}.{}", ..., exception)` 保留 Throwable。与 4.1 同批提交。
- [ ] **Task 4.4**: 机械化（ast_edit，**逐 pattern 分开跑并人工过 diff 后 resolve**）：
  1. `log.error("…{}", e.getMessage())` → `log.error("…", e)`——**必须同时去掉消息串尾部为 getMessage 服务的 `{}`/`：{}` 占位**（SLF4J 的 (String,Throwable) 重载不填充 {}，直接替换会产生字面 `{}` 脏日志）；先 grep 枚举全部形态再分批改。
  2. 空 catch 块（P2-43 三处 `catch(ignored){}` 等）补 `log.warn("<方法名上下文>", e)`；位于 @Transactional 写路径的改重抛（对照 silent-failures 审计表逐条核对写路径归属）。
  3. `PostMilitaryTermTrain:402-404` catch-返-emptyList 改重抛。
- [ ] 门禁：`grep -Prn 'catch\s*\([^)]+\)\s*\{\s*\}' src/main/java` 零命中；`grep -rn 'getMessage())' src/main/java | grep log\.` 零命中；日志格式抽查无悬空 `{}`。

