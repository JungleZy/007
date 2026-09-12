# Rust 后端重构可行性评估

## 结论

**可以使用 Rust 重构本项目后端，但不建议当前直接进行一次性“大爆炸”替换。**

推荐采用“**契约不变、数据库复用、按业务边界渐进迁移**”的绞杀者路线：保留现有 Quarkus 服务作为稳定基线，先用 Rust 实现一个低耦合业务切片，经过 HTTP、数据库、鉴权、WebSocket、部署和回滚验收后，再逐个迁移业务域。

当前项目更适合 Rust 的原因是：后端已有清晰的 REST / WebSocket / 数据库边界，Rust 能提供内存安全、线程安全和较低运行时开销；当前项目不适合立即全量重写的原因是：业务面大、数据库表多、前端依赖现有 wire contract、实时训练状态复杂，迁移的主要难点不是 HTTP 框架，而是**保持既有行为完全一致**。

建议决策：

- **技术可行性：可行**；
- **一次性全量重写：不建议**；
- **增量迁移试点：建议**；
- **立即停止 Java 后端：不建议**，除非已经具备 Rust 主力开发、数据库迁移、协议回归和双运行环境的交付能力。

## 1. 调研范围与事实基线

本评估基于当前工作树的源码、工程说明、前后端联合评审、数据库迁移脚本和 Rust 官方生态文档。代码状态以源码为准，历史评审中的旧行号只作为背景。

### 1.1 现有后端规模和边界

| 维度 | 当前事实 | 对 Rust 迁移的含义 |
|---|---|---|
| 服务框架 | Quarkus 3.20.4 / Java 21 | 需要替换 CDI、JAX-RS、Hibernate/Panache、异常映射和配置体系 |
| HTTP | REST，统一 `/api` 前缀，响应主体为 `Response<T>` | 不能只迁移路径；必须逐字段保持 JSON、业务码、HTTP 语义 |
| 实时通道 | Jakarta WebSocket，多类训练和仿真端点 | 需要重建连接生命周期、房间状态、心跳、广播、关闭和并发控制 |
| 持久化 | MySQL 8，Hibernate ORM/Panache，约 100 张业务表 | 既有表和历史数据应作为兼容边界，不宜与语言迁移同时改模型 |
| Java 代码 | `backend/README.md` 记录约 744 个 main Java 文件、61 个 controller、73 个 service、103 个 entity、101 个 dao、26 个 WebSocket 类 | 全量翻译成本高；必须按业务域切分，不能按 Java 包逐文件机械翻译 |
| 测试 | Quarkus Testcontainers + REST Assured；仓库 README 记录后端 228 个测试全绿 | 现有测试可作为行为基线，但不能假设迁移后可直接复用；应增加跨实现契约测试 |
| 前端 | Electron 外壳 + Vue；API 模块直接使用 `/api/...`，请求头为 `token` + `deviceId` | Rust 服务必须先兼容现有前端，不应同步重做前端请求层 |
| 交付 | JVM、原生镜像、Docker、Linux/ARM64/Windows 构建矩阵 | Rust 可替换运行时，但必须重新证明各目标平台的启动、数据库、WebSocket 和文件能力 |

### 1.2 当前关键契约

1. **路径契约**：REST 根路径是 `/api`；WebSocket 路径不复用 `/api` 前缀，具体路径见 `backend/README.md` 的 WebSocket 表。
2. **响应契约**：业务成功和失败均通过 JSON 信封表达，通常仍为 HTTP 200：

   ```json
   {"code": 200, "data": {}, "message": "ok", "description": ""}
   ```

3. **鉴权契约**：当前实际是自研 `token + deviceId` 会话校验，不应在迁移时误当作标准 JWT。缺 token、缺 deviceId、凭证异常对应的 203/204/206 码值和文案属于客户端契约，不能在语言迁移中顺便改掉。
4. **授权契约**：认证不等于授权。管理端点需要服务端管理员判定，前端按钮权限不能作为安全边界。
5. **数据库契约**：生产使用 schema validate，实体、表和迁移必须保持一致；现有迁移还承担 MyISAM→InnoDB 的事务安全前置。
6. **训练契约**：评分、训练状态、完成/暂停/续训、房间生命周期和 WebSocket 消息码都是行为契约，不是普通 CRUD。
7. **文件契约**：当前上传能力存在纯文本边界，前端还涉及导入、导出和模板；Rust 迁移不能通过改变 MIME、字节流或错误码来“简化”实现。

## 2. Rust 是否能覆盖当前技术需求

### 2.1 HTTP 和中间件

可行。候选组合为：

- Tokio：异步运行时；
- Axum：HTTP 路由、extractor、middleware、错误处理和共享状态；
- Tower / tower-http：超时、追踪、压缩、CORS、鉴权中间件；
- serde / serde_json：请求和响应 JSON；
- utoipa 或其他 OpenAPI 工具：接口描述和契约生成。

Axum 官方文档明确支持路由、请求提取、响应转换、middleware 和 `State` 共享状态。其设计与当前“controller → service → dao”分层可以对应，但 Rust 不会自动提供 CDI、注解拦截器或异常映射；这些要显式设计成 middleware、domain error 和统一 response converter。

### 2.2 MySQL、ORM 和事务

可行，但不能把 Hibernate/Panache 的自动行为假定为 Rust ORM 的自动行为。

候选方案：

- **SeaORM**：更接近当前实体/关系/分页/事务模型，支持从既有数据库生成实体，也支持关系和 raw SQL；适合迁移初期降低 CRUD 改造量。
- **SQLx**：更接近显式 SQL 和编译期查询检查，支持 Tokio、MySQL、连接池和事务；适合复杂查询、性能敏感路径和需要明确控制 SQL 的模块。

建议先以 **SeaORM + 必要的 SQLx/raw SQL** 为主，而不是全站强行选择一种模式。无论使用哪一种，都必须：

- 复用既有表、字段、主键和历史数据；
- 生产只执行版本化迁移，不启用自动 schema sync；
- 显式设置事务边界、隔离级别、超时和回滚语义；
- 对 `先删后插`、finish、reset、批量导入、房间结束等写路径做数据库级回归；
- 继续把 InnoDB 作为事务前置，不把 Rust 当成 MyISAM 风险的替代修复。

### 2.3 WebSocket 和实时训练

可行，但这是本项目最高风险迁移面之一。

Axum 提供 `WebSocketUpgrade`、`WebSocket`、`on_upgrade`，并可通过 `State` 把应用状态传入连接处理器。Tokio 生态也支持将 WebSocket 拆分为读写流并发处理。

Rust 版本应把当前 Java 单例中的全局容器显式建模为：

- `AppState`：数据库池、会话服务、房间管理器、配置；
- `RoomManager`：按房间 ID 管理成员、状态和广播；
- `ConnectionState`：单连接的用户、会话、心跳和关闭状态；
- channel / task：读消息、写消息、心跳、关闭清理；
- 明确的锁粒度和生命周期顺序。

不能只把 `@ServerEndpoint` 改成一个 Rust handler。当前 `WebSocketUnionService` 已经有在线连接、在线用户、房间广播、重复连接踢除和房间锁；这些必须通过协议测试和并发测试证明等价。

### 2.4 鉴权、授权和密码

可行，但建议把迁移视为安全边界重建，而不是简单复制拦截器。

Rust 中应建立：

1. Header extractor：读取 `token`、`deviceId`；
2. session middleware：查询有效会话并将 `AuthContext` 放入 request extensions；
3. admin middleware / policy：查询角色并拒绝无权限调用；
4. 统一业务错误转换：保持 203/204/206 和 HTTP 200 兼容语义；
5. 密码哈希版本识别和渐进迁移：兼容存量格式，但新密码不得继续使用不安全的旧格式；
6. 日志脱敏：禁止记录 token、password、deviceId 和完整敏感请求体。

迁移时必须避免新增 query 参数传 token 的兼容逻辑；应先让所有调用方稳定使用 header，再在切换阶段删除旧路径。

## 3. 主要收益与不能承诺的收益

### 3.1 可能收益

- 编译期内存安全和线程安全，降低部分并发状态错误；
- 显式错误类型和 `Result`，减少“异常被吞后继续提交”的风险；
- 无 GC 运行时，服务资源曲线更可预测；
- Tokio/Axum 的异步模型适合大量 WebSocket、I/O 和连接管理；
- 单一 Rust 二进制和容器镜像可简化部分运行时依赖。

### 3.2 不能直接承诺

- Rust 不会自动修复权限、业务码、SQL、数据模型或协议设计错误；
- Rust 不会自动提升本项目训练算法的正确性；
- 没有等价负载测试，不能宣称吞吐、延迟或内存一定优于 Quarkus；
- 没有协议和数据库回归，不能宣称可以无感替换 Java；
- 使用 ORM 并不会自动消除 N+1、锁竞争、错误分页或事务边界问题。

## 4. 方案比较

| 方案 | 兼容风险 | 交付风险 | 迁移速度 | 结论 |
|---|---:|---:|---:|---|
| 一次性全量改 Rust | 极高 | 极高 | 表面快、实际不可控 | 不采用 |
| Java 保留，Rust 新建独立服务，按域迁移 | 中 | 中 | 中 | **推荐** |
| Rust 作为 Java 内部库/FFI | 中高 | 高 | 慢 | 不作为主路线；边界复杂且不解决服务契约迁移 |
| 只重写 WebSocket，REST 保持 Java | 中 | 中高 | 中 | 可作为后续专项，不作为第一步；实时协议本身风险最高 |
| 只做 Rust read-only 查询旁路 | 低 | 低 | 快 | **适合作为试点第一阶段** |

推荐采用双服务路由：

```text
Electron/Vue
    |
    v
反向代理 / API Gateway
    |-- /api/tools、只读目录、试点域 --> Rust
    `-- 其他 REST、训练 WebSocket --> Java/Quarkus
                         |
                         `--> 共享 MySQL（迁移期间单写者原则）
```

迁移阶段应尽量避免同一张业务表由两个服务同时写入。若必须双写，必须先定义幂等键、写入顺序、冲突解决和补偿机制；否则优先采用“一个域一个写者”的路由策略。

## 5. 推荐迁移路线

### 阶段 0：先固定行为基线

交付物：

- 现有 OpenAPI 快照和前端真实调用清单；
- REST 请求/响应 fixture，覆盖成功、业务错误、认证错误、未捕获异常；
- WebSocket 握手、消息码、心跳、断线、重连、重复连接和关闭的协议样本；
- 数据库 schema、索引、存储引擎、迁移顺序和关键存量数据样本；
- 关键写路径的事务不变量和回滚测试。

门禁：现有 Java 端保持全绿，且不因 Rust 试点修改既有业务契约。

### 阶段 1：Rust 最小运行骨架

只实现以下能力，不迁移复杂训练逻辑：

- 配置、日志、健康检查、优雅关闭；
- `/api` 路由和统一 `Response<T>` 信封；
- `token + deviceId` header 认证；
- MySQL 连接池、超时、迁移检查；
- tracing、请求 ID、敏感字段脱敏；
- Docker、CI、Linux 目标平台启动 smoke test。

### 阶段 2：只读试点

优先选择低耦合只读域，例如工具接口、固定报文/类型目录的只读查询；不要一开始选择用户、训练结算、房间 WebSocket、批量导入或文件导出。

验收要求：

- 与 Java 对同一数据库返回的 JSON fixture 等价；
- 字段名、null、数字类型、时间格式、分页边界等逐字段一致；
- 前端真实调用成功，浏览器 Network 结果与 Java 基线一致；
- 失败时保持业务码和 HTTP 语义；
- 可以通过路由开关一键切回 Java。

### 阶段 3：单一写入域

在只读试点稳定后，选择一个边界清晰的 CRUD 域迁移。迁移顺序建议：

1. DTO 与校验；
2. repository/query；
3. service 事务；
4. controller / response；
5. REST 契约测试；
6. 真实前端回归；
7. 小流量切换和回滚。

迁移用户、角色、密码、训练结算前，必须先完成授权、凭据脱敏、密码格式和会话失效策略的独立设计；不能把这些安全高风险路径作为第一个 Rust 试点。

### 阶段 4：复杂训练和 WebSocket

仅在 REST 域有双实现契约测试后，再迁移训练域。顺序建议：

- 先迁移纯函数评分/报文生成，并用 characterization tests 固定 Java 输出；
- 再迁移单用户训练状态；
- 最后迁移联合训练、仿真房间和多连接 WebSocket；
- WebSocket 切换必须支持按路径或按租户/房间灰度，并保留 Java 回滚入口。

### 阶段 5：停写切换与 Java 下线

只有满足以下条件才考虑移除 Java：

- 所有前端活跃调用点已通过 Rust 契约测试和浏览器回归；
- 所有 WebSocket 路径有连接生命周期和并发证据；
- 数据库迁移、备份、回滚、运行手册已更新；
- Rust 版本在 Linux、ARM64、Windows/Electron 交付形态下完成实际 smoke test；
- 线上观测至少覆盖错误率、业务码分布、连接数、房间数、事务失败、数据库池耗尽和回滚；
- Java 服务已不再是唯一的回滚方案，且已有明确的切换窗口和恢复演练。

## 6. 风险清单与控制措施

| 风险 | 具体表现 | 控制措施 |
|---|---|---|
| API 语义漂移 | GET 参数、snake/camel、null、数字和时间格式变化 | OpenAPI + fixture + 前端真实调用；逐端点验收 |
| ORM 映射差异 | 默认值、级联、懒加载、排序、分页、关联查询不同 | 先生成实体，再逐查询对账；复杂查询保留显式 SQL |
| 事务差异 | Java 注解事务与 Rust 显式事务边界不一致 | 每个写用例定义 commit/rollback 不变量；只允许 InnoDB |
| 双写冲突 | Java 和 Rust 同时更新同一行 | 一个域一个写者；必要时幂等键和补偿，不默认双写 |
| WebSocket 并发 | 重连、踢出、广播、房间销毁次序改变 | 状态机、模型检查式测试、并发压力和断线注入 |
| 授权遗漏 | 只复制认证，遗漏管理员角色判断 | 端点授权矩阵；匿名、普通用户、无角色、管理员四类测试 |
| 错误处理 | Rust `Result` 被统一成错误 500，破坏业务码 | 领域错误枚举 → 统一信封映射；保留 203/204/206 |
| 文件能力 | 上传大小、MIME、编码、导出字节流不一致 | 真实文件 fixture、超限测试和浏览器下载校验 |
| 构建交付 | Rust 跨平台二进制、TLS、OpenSSL、Electron 嵌入差异 | CI 目标矩阵；各平台启动 smoke test；固定基础镜像 |
| 团队生产力 | Rust 学习成本导致缺陷修复速度下降 | 先试点；统一 lint/format/error/logging 规范；禁止机械翻译 |
| 误判性能 | 只看微基准，未覆盖数据库和 WebSocket | 迁移前后用同一业务场景压测，记录 p95、错误率和资源 |

## 7. 建议的 Rust 技术基线

这是迁移试点的建议基线，不是要求一次性引入所有组件：

| 能力 | 建议 |
|---|---|
| Runtime | Tokio |
| HTTP | Axum + Tower / tower-http |
| JSON | serde + serde_json |
| DB 起步 | SeaORM；复杂/关键 SQL 使用 SQLx 或受控 raw SQL |
| MySQL | 现有 MySQL 8；连接池、超时和 TLS 参数显式配置 |
| WebSocket | Axum `extract::ws`；必要时使用 tokio-tungstenite |
| 错误 | `thiserror` 定义领域错误，统一转换为现有响应信封 |
| 日志 | tracing + tracing-subscriber，字段脱敏 |
| 密码 | Argon2id 或 PBKDF2 等受支持的密码哈希；兼容存量格式并渐进升级 |
| OpenAPI | 选定一个可维护的 Rust OpenAPI 生成方案，先以 fixture/契约测试为权威 |
| 测试 | unit + integration + REST contract + WebSocket lifecycle + MySQL/Testcontainers |
| 迁移 | 版本化 SQL 或 SeaORM migration；生产禁止无审计自动 schema sync |
| 交付 | cargo lock、容器、多平台 CI、启动和回滚 smoke test |

## 8. Go / No-Go 门槛

### 可以进入 Rust 试点的条件

- 明确一个独立业务边界和唯一写者；
- 已冻结该域的前端调用面和 JSON 契约；
- 有可复现的 MySQL 测试环境；
- 有 Java 基线 fixture 和回滚路由；
- 至少一名能负责生产 Rust 的维护者；
- CI 能构建并启动 Rust 服务；
- 试点不修改 203/204/206，也不改变现有数据库 schema。

### 暂不应开始全量重构的条件

- 仍未完成当前 P0/P1 安全和跨栈整改；
- 没有 WebSocket 协议样本和生命周期测试；
- 无法确定前端真实调用点；
- 计划同时改语言、数据库 schema、API 形态和身份认证；
- 没有灰度、回滚和双服务观测方案；
- 主要目标只是“Rust 更快”，但没有业务负载基线。

## 9. 最终建议

1. **近期不做全量 Rust 重写。** 当前最优先事项仍是现有评审确认的安全、授权、跨栈契约、WebSocket 生命周期和部署闭环；语言迁移不能掩盖这些问题。
2. **可以立项 Rust 试点。** 试点应以只读、低耦合域开始，验证 HTTP 信封、鉴权、MySQL、测试、CI、日志和回滚，而不是直接碰训练结算或联合 WebSocket。
3. **将 API 契约和数据库 schema 当作迁移资产。** 先冻结并测试，再写 Rust；不要把 Java 类结构当作目标架构。
4. **将复杂训练逻辑按状态机和纯函数重建。** 评分和报文生成先做输出特征测试，WebSocket 后做；不要机械翻译 service 类。
5. **以可回滚、可观测、单域单写者作为上线标准。** 达不到这些条件时，保留 Quarkus 是更安全的工程决策。

因此，本项目的答案是：**能用 Rust 重构，但应当把 Rust 作为渐进式后端替代路线，而不是立即进行全量重写。**

## 10. 证据与参考资料

### 仓库证据

- [`backend/README.md`](../../backend/README.md)：后端技术栈、规模、REST/响应/WebSocket/数据库/构建边界。
- [`backend/pom.xml`](../../backend/pom.xml)：Quarkus REST、WebSocket、Hibernate ORM/Panache、MySQL、JWT、OpenAPI、Testcontainers 依赖。
- [`backend/src/main/java/com/nip/common/MainApplication.java`](../../backend/src/main/java/com/nip/common/MainApplication.java)：`/api` 应用前缀。
- [`backend/src/main/java/com/nip/common/response/Response.java`](../../backend/src/main/java/com/nip/common/response/Response.java)：响应信封字段。
- [`backend/src/main/java/com/nip/common/interceptor/JWTInterceptor.java`](../../backend/src/main/java/com/nip/common/interceptor/JWTInterceptor.java)：`token + deviceId`、203/204/206 兼容行为。
- [`backend/src/main/java/com/nip/ws/WebSocketUnionService.java`](../../backend/src/main/java/com/nip/ws/WebSocketUnionService.java)：连接、在线用户、房间状态和广播并发边界。
- [`backend/src/main/java/com/nip/service/PostTelexPatTrainService.java`](../../backend/src/main/java/com/nip/service/PostTelexPatTrainService.java)：复杂训练事务、生成、评分和多 DAO 协作示例。
- [`backend/database/migrations/2026-08-26-01-schema-sync.sql`](../../backend/database/migrations/2026-08-26-01-schema-sync.sql) 与 [`2026-08-26-02-engine-innodb.sql`](../../backend/database/migrations/2026-08-26-02-engine-innodb.sql)：schema 对齐和 InnoDB 事务前置。
- [`bw-frontend/frontend/src/common/http/axios.js`](../../bw-frontend/frontend/src/common/http/axios.js) 与 [`bw-frontend/frontend/src/common/http/index.js`](../../bw-frontend/frontend/src/common/http/index.js)：前端请求方法、header 和错误码消费。
- [`docs/reviews/2026-09-08-full-project-review.md`](../reviews/2026-09-08-full-project-review.md)：当前安全、契约、WebSocket 和交付风险基线。
- [`docs/specs/2026-09-08-joint-fix-spec.md`](../specs/2026-09-08-joint-fix-spec.md)：跨栈契约和验收约束。

### 官方生态资料

- [Rust](https://www.rust-lang.org/)：语言的安全、性能和工具链定位。
- [Axum](https://docs.rs/axum/latest/axum/)：路由、extractor、middleware、state 和响应模型。
- [Axum WebSocket](https://docs.rs/axum/latest/axum/extract/ws/)：升级、连接处理和并发读写。
- [SQLx](https://docs.rs/sqlx/latest/sqlx/)：异步 SQL、运行时、TLS、连接池和事务支持。
- [SQLx MySQL](https://docs.rs/sqlx/latest/sqlx/mysql/)：MySQL 驱动、连接池和事务类型。
- [SeaORM](https://docs.rs/sea-orm/latest/sea_orm/)：实体、关系、分页、事务和既有数据库生成能力。
- [Tokio Tungstenite](https://docs.rs/tokio-tungstenite/latest/tokio_tungstenite/)：Tokio WebSocket stream/sink 实现。
