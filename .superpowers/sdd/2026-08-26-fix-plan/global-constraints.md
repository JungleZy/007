## Global Constraints

- 构建命令统一：`MVN="JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B"`；单测运行 `$MVN test -Dtest=<ClassName>`；批尾全量 `$MVN clean verify`。
- 事务铁律：`@Transactional` 内 catch 不得吞写路径异常（重抛或不 catch）；"先删后写"倒转为"先校验/构建新数据 → 删旧 → 写新"。事务全部在 service 层（controller 目录零 @Transactional，已核实）；JTA 默认对 RuntimeException 回滚。
- 异常响应链路（已核实，执行者必读）：@JWT 端点的异常会被 `JWTInterceptor:82-84` 的 catch(Exception) 兜住，返回 **HTTP 200 + SYSTEM_ERROR + 异常消息** 的结构化响应体（不是裸 500）；事务在 service 边界已先回滚。Phase 1 接受"校验失败业务码从 CODE_500 变为 SYSTEM_ERROR（消息保留）"的微调；Phase 4 收窄拦截器后由专用 Mapper 恢复精确业务码。
- 接口契约：HTTP 200 + 业务码信封保持；安全项（认证/root/CORS）不在范围。
- **JPQL 铁律**：全仓实体统一 `@Entity(name = "t_xxx")`，JPA 实体名 = 表名字符串；JPQL 里写 `from TestPaperEntity` 无法解析。查询一律走 DAO（Panache）或用 `@Entity(name)` 里的字符串。
- 不引新依赖、不顺手重构；只改任务列出的位置。
- 实体/DTO setter 名以源码为准（评审已核实本计划所有 setter 引用；新增引用前先读实体）。
- 每个 Task 一次提交，消息格式 `fix(<编号>): <一句话>`；测试与实现同一提交。
