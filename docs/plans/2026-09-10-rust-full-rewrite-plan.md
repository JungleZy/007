# 全量 Rust 后端重写实施计划

## 1. 计划结论

本计划接受“最终全量 Rust 重写”的目标，但采用**分阶段建设、双栈验证、按域切换、最终 Java 下线**的实施方式，而不是一次性删除 Java 后端后从零开发。

最终状态：

- 后端业务代码全部由 Rust 提供；
- 现有 Electron/Vue 前端无需因语言切换而整体重写；
- 现有 REST 和 WebSocket wire contract 保持兼容，除非另有独立版本化变更；
- 现有 MySQL 数据和表结构先复用，避免语言迁移与数据迁移同时发生；
- Java/Quarkus 只作为迁移期间的行为基线和回滚服务，最终移除；
- Rust 服务具备独立构建、测试、观测、部署和回滚能力。

**关键决策：全量重写是终态，不是首个提交；每个阶段都必须可以运行、验证和回退。**

## 2. 当前基线与范围

### 2.1 代码范围

当前后端是 Quarkus 3.20.4 / Java 21 服务，包含：

- REST API，统一 `/api` 前缀；
- Jakarta WebSocket 训练和仿真通道；
- Hibernate ORM / Panache + MySQL 8；
- 自研 `token + deviceId` 会话校验和服务端管理员授权；
- 理论学习、题库、考试、手键、电传、报底、抄报、汉字录入、军语、设备、组网和仿真等业务域；
- 约 100 张业务表。controller、route、DAO、实体和 WebSocket 数量以 Phase 0 生成的 inventory 为准，不再引用已漂移的历史文件计数。

前端是 Electron 外壳 + Vue 页面，API 模块直接使用 `/api/...`，请求拦截器自动附加 `token` 和 `deviceId`。

### 2.2 当前行为基线

本计划修订时已完成的 Java/前端整改不能被 Rust 迁移重新解释为开放缺陷：

- `3e4dc05`：联合训练契约、失败状态、上传消费和无引用部门死子树整改；
- `5193463`：组训 WebSocket 空角色学员授权兼容；
- 当前后端 `JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify`：512 tests，0 failures / errors / skipped；
- 当前前端 `npm test`：39/39，通过生产构建；
- 2026-09-19 跨栈复核的 P1/P2 活跃契约项已按上述提交闭合；其余静态观察必须在 Phase 0 清单中标记为“迁移必须保持 / 独立整改 / 接受风险”，不能静默带入 Rust。

后续基线必须写入 `docs/reviews/rust-migration/2026-09-21-java-baseline.json`，并记录基线 commit、命令完整输出、数据库快照校验和前端构建产物校验；不得继续引用 228、240、442 等历史测试数字。

### 2.3 不在本计划中偷偷改变的内容

除非另立并批准 API/安全/数据库迁移规格，本计划不改变：

- REST 路径、HTTP 方法、请求字段、响应字段和业务码；
- HTTP 200 + `Response<T>` 信封语义；
- 203、204、206 的码值与文案；授权拒绝 207、业务终态 208；
- WebSocket 路径、握手方式、消息码、心跳和业务帧结构；
- 现有数据库表名、字段、主键、历史数据和迁移顺序；
- 训练评分、报文生成、finish、pause、resume 和房间生命周期的业务含义；
- Electron 外壳对后端可执行文件、端口和启动参数的既有要求。

如果希望借重写机会改协议、改数据库或改认证，必须先把它们拆成独立版本化变更；不能混在“语言替换”中。


## 3. 目标架构

### 3.1 仓库布局

迁移期间不覆盖 `backend/`，新增独立 Rust 工程：

```text
backend-rust/
├── Cargo.toml
├── Cargo.lock
├── crates/
│   ├── app/             # 进程入口、配置、路由装配、启动检查
│   ├── api/             # REST DTO、响应信封、路由 handler、OpenAPI
│   ├── auth/            # token/deviceId 会话认证、管理员授权；密码迁移另立阶段
│   ├── domain/          # 业务模型、状态机、领域错误、纯函数算法
│   ├── persistence/     # SeaORM entity、repository、事务和查询
│   ├── realtime/        # WebSocket、连接、房间、广播、心跳
│   ├── file-service/    # 上传、解析、导入、导出
│   └── observability/   # tracing、指标、请求 ID、脱敏规则
├── migration/           # 初期仅做 schema 校验与 rehearsal，不自动改生产库
├── tests/
│   ├── contract/        # Java/Rust 共享 REST fixture
│   ├── websocket/       # 协议与生命周期测试
│   └── database/        # MySQL 集成和回滚测试
└── deploy/              # Docker、systemd/Electron 运行包、CI 辅助文件
```

这是逻辑边界，不要求一开始拆成大量独立微服务。优先使用一个 Rust 二进制、一个 MySQL 连接池和清晰的 crate 边界，避免语言迁移同时变成微服务迁移。试点服务默认监听 **18002**，与 Java 生产兼容端口 **18001** 并行；只有域切换窗口才允许网关把该域路由到 18002，不能让 Rust 直接抢占 18001。

### 3.2 技术基线

| 能力 | 选择 | 约束 |
|---|---|---|
| Runtime | Tokio | 所有阻塞文件/压缩/CPU 密集任务必须显式隔离 |
| HTTP | Axum + Tower / tower-http | middleware 显式实现认证、授权、超时、CORS、追踪 |
| JSON | serde / serde_json | DTO 与数据库模型分离，禁止实体直接作为敏感响应 |
| ORM | SeaORM | 先生成实体再人工校正；生产禁止自动 schema sync |
| SQL | SeaORM query / raw SQL | 复杂查询必须有 SQL 和结果 fixture；同一服务只保留一个连接池 |
| WebSocket | Axum `extract::ws` | 连接态按连接保存，房间态由显式 manager 管理 |
| 错误 | `thiserror` + 统一 response mapper | 领域错误、认证错误、系统错误分层；系统故障不得伪装成成功 |
| 日志 | tracing | token、password、deviceId、完整请求体脱敏；request ID 全链路传递 |
| 会话 | SHA-256(token) 等值查找 + deviceId | REST 凭据只读 header；WS 因浏览器限制仍按既有 query 握手规则 |
| 授权 | 显式 policy / RequireAdmin | `is_admin=0` 才是管理员；必须查询“存在管理员角色” |
| 迁移 | 版本化 SQL / SeaORM migration | 初期只校验既有 schema；禁止启动自动 DDL，禁止 Rust/Java 同域双写 |
| 测试 | unit + MySQL integration + REST contract + WS lifecycle | 不以编译通过替代行为验收 |

### 3.3 Rust 服务内部分层

每个业务域保持统一调用方向：

```text
Router / Handler
    -> Application Service
        -> Domain model / state machine
            -> Repository / transaction
                -> MySQL
```

规则：

- handler 只负责提取参数、调用 application service、转换响应；
- service 负责授权后的业务流程和事务边界；
- domain 放评分、报文生成、状态转换等可测试逻辑；
- repository 负责查询和持久化，不向 handler 泄露 ORM model；
- DTO、domain model、persistence model 三者不默认复用；
- 一个 service 方法必须明确“读、写、事务、幂等和失败后状态”。

## 4. 迁移原则和不变量

### 4.1 API 不变量

每个端点迁移前建立以下清单：

- 方法、路径、前缀；
- query、header、form、JSON body 字段；
- 字段是否必填、默认值和空值语义；
- JSON 字段名、数字类型、时间格式、数组顺序；
- 成功响应和每类业务错误响应；
- HTTP status 与业务 `code`；
- 鉴权和管理员授权要求；
- 前端调用文件和真实消费点；
- 上传/导出大小、MIME、编码和字节流约束。

Rust 版本与 Java 版本对同一 fixture 的响应必须在契约允许范围内等价；不能只比较 HTTP 200。

### 4.2 数据库不变量

- MySQL schema 先保持不变；
- 迁移初期 Java 和 Rust 不同时写同一业务域；
- 同一个域始终只有一个写者；
- 所有写路径使用显式事务；
- 目标表全部为 InnoDB；
- 事务失败后检查受影响行、关联行和唯一约束状态；
- 删除、批量导入、finish、reset、房间结束必须有回滚和幂等测试；
- 生产迁移先备份、演练，再切换，禁止通过 ORM 启动自动改表。

### 4.3 会话和授权不变量

- REST `token + deviceId` 只从请求 header 读取；WebSocket 握手继续使用既有 query 凭据规则，不新增 REST query token 回退；
- 缺 token、缺 deviceId、凭证异常保持 203/204/206；授权拒绝统一 207；不可重试终态统一 208；未处理系统故障保持 HTTP500/code500；
- 管理员授权由服务端完成，不依赖前端 `v-per`；`is_admin=0` 才是管理员，必须判断“存在管理员角色”，不能取任意第一条；
- 普通用户、无角色、匿名调用管理端点必须保持目标数据不变；
- 登录、用户资料、用户目录和管理列表继续使用不同 DTO；
- 任何日志和错误追踪不得泄露凭据。

### 4.4 WebSocket 不变量

- 路径和消息码保持不变；REST 与 WS 的凭据传输边界不混用；
- 单连接、在线用户、房间成员和房间广播状态边界明确；
- 重复连接、断线、心跳超时、手动关闭、重连和房间销毁有确定顺序；
- 坏消息返回协议错误，不触发无关用户清理；
- 旧连接回调不能关闭或删除新连接状态；
- 所有共享状态访问有明确锁或 actor/channel 所有权；
- WebSocket 切换必须保留 Java 回滚入口，直至全量验收完成。

## 5. 阶段计划

### Phase 0：冻结基线和建立无流量 Rust 骨架

目标：把 Java 当前行为变成可重复的迁移基线，同时建立不接管流量的 Rust 空服务。Phase 0 的“冻结基线”必须先于任何 Rust 业务域实现；健康检查骨架可以并行建立，但不得接入前端或生产网关。

任务：

- 重新执行后端 `JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify`，记录测试总数和完整输出；
- 执行前端 `npm ci`、`npm test` 和 `npm run build`，记录完整输出；
- 固化当前 `/q/openapi`、REST 路由清单和前端真实 API 调用清单；
- 扫描 REST controller、WebSocket endpoint、上传/导出端点，并把实际消费者标到端点级；
- 从 `backend/database/project006.sql`、`project006-base.sql` 和全部 migration 生成 schema inventory；
- 记录所有表的引擎、主键、索引、关联、nullable、默认值和 migration 顺序；
- 建立匿名、普通用户、无角色用户、管理员和训练样本 fixture，但凭据只存测试 fixture，不进入日志或生产配置；
- 记录训练纯函数、评分和报文生成的特征样本；
- 记录 WebSocket 握手、身份覆盖、消息码、心跳、断线、重连、重复连接和关闭样本；
- 将证据写入 `docs/reviews/rust-migration/`：`2026-09-21-java-baseline.json`、`rest-endpoints.tsv`、`frontend-callers.tsv`、`ws-contracts.tsv`、`schema-inventory.tsv`、`fixture-manifest.json`；
- 建立 `backend-rust/` Cargo workspace 和独立 `rust-backend` CI job，但不接管流量、不修改现有 schema、不修改前端路由。

出口门禁：

- Java 后端、前端构建和数据库 rehearsal 有可重复证据；
- OpenAPI、前端调用清单、WS 协议清单和 schema inventory 已入库，且清单有生成命令、commit 和时间戳；
- 所有未决历史问题已标记为“迁移必须保持 / 独立整改 / 接受风险”；
- Rust 空服务默认监听 18002，可构建、启动、健康检查、readiness 检查和优雅退出；
- Java 仍占用 18001，前端和生产网关没有任何 Rust 流量。

### Phase 1：Rust 平台基础设施

目标：完成所有业务域共用的运行时能力，不迁移复杂训练逻辑，不切换生产流量。

任务：

- 配置加载：试点默认监听 18002；最终切换时才复用 Java 兼容端口 18001；数据库 URL、body limit、CORS、日志等级显式配置；
- `/api` 路由前缀和统一 `Response<T>` 信封；保留 `code/data/message/description` 字段和 null 语义；
- 错误分类与 mapper：参数错误、认证错误、授权错误、业务错误、系统错误；不把系统异常吞成成功；
- MySQL 连接池、超时、TLS、启动连接检查；连接失败只影响 readiness，不允许自动建表或自动改表；
- token/deviceId 认证 middleware：REST 只认 header，token 按 Java 的 SHA-256 摘要口径等值查询；
- RequireAdmin policy：`is_admin=0` 管理员语义与 Java 一致；
- request ID、结构化日志、敏感字段脱敏；
- health/readiness/liveness；
- graceful shutdown 和连接清理；
- Docker、Linux amd64/arm64 构建和启动 smoke test；
- OpenAPI 输出和契约 fixture 测试框架；
- 独立 CI 执行 `cargo test --locked`、`cargo build --locked` 和启动健康检查，不与 `keysim` job 混为同一工作目录。

出口门禁：

- 缺 token、缺 deviceId、无效凭证和无管理员权限的响应与 Java 基线一致：203/204/206/207；
- 业务终态保持 208，未处理系统故障保持 HTTP500/code500；
- 未捕获错误不会被吞成成功；
- HTTP 200 与业务码边界通过测试；
- Rust 服务不修改业务表、不执行生产 DDL；
- CI 能构建并启动 Rust 二进制；
- Java 仍为唯一生产写者，Rust 仍可立即停止且不会改变 Java 状态。

### Phase 2：数据库和通用基础域

目标：先完成所有业务域依赖的持久化基础，不迁移复杂训练流程。

任务：

- 从现有 MySQL schema 生成 SeaORM entity；
- 人工校正 UUID/string/integer、decimal、datetime、nullable 和命名映射；
- 建立 repository trait 和事务封装；
- 建立分页、排序、过滤、批量保存、删除和查询约定；
- 建立数据库 fixture、隔离数据库和 migration rehearsal；
- 实现工具时间、字典、固定报文类型、设备类型等基础只读能力；
- 对比 Java 与 Rust 的 SQL 结果和 JSON 结果。

出口门禁：

- 100 张左右业务表的实体/schema inventory 对账完成；
- 关键表查询、分页、排序、null 和 decimal 行为有 fixture；
- Rust 连接池、事务和回滚测试稳定；
- 未引入自动改表或隐式数据清理。

### Phase 3：用户、会话、角色和菜单域

目标：建立 Rust 版身份与管理边界，为所有后续域提供可靠 AuthContext。

任务：

- login、signin、userOut、current user、user directory；
- 用户资料、登录会话和管理响应 DTO 分离；
- token/deviceId 查询和失效；
- 密码哈希版本识别、旧格式渐进升级和新密码写入；
- role、user-role、menu、menu-button 查询；
- 管理端点授权矩阵；
- 匿名注册已有 ID、A token 携 B id、普通用户管理调用等回归；
- 前端登录、登出、自动清理和权限菜单回归。

出口门禁：

- 所有认证/授权 fixture 与 Java 等价；
- 用户 API 响应不含 password、token、deviceId，除明确登录会话字段；
- 普通用户、无角色、管理员四类权限测试全通过；
- 旧 token、登出后 token、deviceId 不匹配行为一致；
- Java 仍可作为该域回滚实现。

### Phase 4：基础资料、设备和评分规则域

目标：迁移低到中复杂度 CRUD 和配置类业务。

包含：

- tools；
- cable、cable type、cable floor；
- grading rule；
- device、device type、device description；
- equipment device、equipment train；
- 通用分页、目录和管理操作。

任务规则：

- 先迁只读接口，再迁写接口；
- 写接口逐个补管理员授权和事务；
- 实体响应改为契约 DTO，不因 Rust 迁移直接返回数据库 model；
- 前端 API 模块逐项指向 Rust 路由；
- 迁移期间同一配置域只保留一个写者。

出口门禁：

- 目录、保存、删除、分页和授权行为一致；
- 数据库写入和回滚有证据；
- 前端相关页面完成 Network 和页面消费回归；
- 路由切换可在不改前端代码的情况下回退 Java。

### Phase 5：理论知识、题库、考试和文件域

目标：迁移内容管理、考试和上传/导出能力。

包含：

- theory knowledge/classify；
- theory question bank、level、question；
- test paper、test、exam、exam user；
- fallible record、study record、comprehensive statistics；
- 纯文本上传、DOCX/XLSX 前端解析后的批量提交、模板列规格和导出。

重点：

- 先冻结每种文件能力的格式、大小、编码和错误码；
- Rust multipart 必须检查 body limit、单文件 limit 和空文件；
- 批量导入一次事务提交，失败时可观察且可回滚；
- 导出端点先确认是 JSON 列规格还是文件字节流，不能伪装 MIME；
- 富文本内容在统一净化边界前不得直接渲染；
- 考试计分和历史统计与 Java 做同数据集对账。

出口门禁：

- 文本、DOCX、XLSX 相关 fixture 和浏览器下载/上传回归通过；
- 批量导入部分失败不会留下半批数据；
- 导出文件可被现有前端和桌面端打开；
- 理论成绩、记录和统计逐字段对账。

### Phase 6：单用户训练域

目标：迁移不依赖多人房间的训练流程和训练结果。

包含：

- entering exercise / 汉字录入；
- entering telex / 五笔；
- military term；
- radiotelephone；
- 单用户 telegram、telegraph key、telex、ticker tape 训练；
- 训练设置、规则、开始、暂停、继续、完成、统计和历史记录。

实施顺序：

1. 把评分数学、速度、准确率、错误计数和报文生成抽成 Rust 纯函数；
2. 使用 Java characterization fixture 固化边界和历史行为；
3. 迁移训练创建与查询；
4. 迁移 begin/pause/goTo/finish；
5. 迁移统计和历史；
6. 再切前端训练页面。

出口门禁：

- 同一输入序列的评分、速度、准确率和状态转移等价；
- 重复 finish、越界分页、无效训练 ID、过期 token 和并发更新有回归；
- DB 结果和前端显示结果一致；
- 训练页面完成真实操作 smoke，而非只测接口返回。

### Phase 7：复杂报底、联合训练和仿真域

目标：迁移多表生成、结算和房间业务。

包含：

- post telegram；
- post telegraph key；
- post telex；
- post ticker tape；
- general key/telex/ticker；
- group net；
- simulation report/recept/router room/content。

这是数据库和并发风险最高的 REST 业务阶段。要求：

- 每个域先绘制状态机和表写入图；
- 每个事务列出成功写集、失败回滚集和幂等键；
- 结算类路径做并发 finish、重复提交和中断注入；
- 生成内容采用确定性 seed 或特征断言，不能只断言数量；
- 房间 CRUD 与 WebSocket 状态切换必须统一 owner；
- Java/Rust 不能同时写同一房间。

出口门禁：

- 结算、删除、重建、finish、reset、断点恢复和并发边界全部通过；
- 同一房间多用户行为与消息顺序符合协议 fixture；
- 生产快照执行迁移 rehearsal 后，Rust 启动和查询通过；
- 任一域仍可切回 Java 且不会重复结算或丢失房间状态。

### Phase 8：WebSocket 和实时状态全面切换

目标：完成所有实时端点 Rust 化。

端点范围以 Phase 0 清单为准，至少包括：

- `/websocket/{sid}`；
- `/websocketUnion/{sid}`；
- `/startWebsocket/{sid}`；
- `/status`；
- `/simulation/{id}/{roomId}`；
- `/generalKeyPatTrain/{uid}/{trainId}`；
- `/generalTelexPatTrain/{uid}/{trainId}`；
- `/generalTickerPat/{uid}/{trainId}/{role}`。

实现要求：

- `AppState` 只保存服务级资源；
- `ConnectionState` 保存连接级状态；
- `RoomManager` 对房间状态拥有明确的唯一写入路径；
- reader、writer、heartbeat、cleanup 任务之间使用 channel；
- 关闭流程幂等；
- 旧连接 generation 不能影响新连接；
- 坏消息、超时、网络断开、重复连接和广播失败有日志和协议响应；
- 前端 Electron/Web 两种 WebSocket 入口都验证。

出口门禁：

- 协议、心跳、重连、关闭、重复连接、房间成员和广播测试通过；
- 并发和断线注入测试通过；
- 浏览器/Electron 实际 smoke 通过；
- 监控能区分连接数、房间数、心跳超时和业务错误；
- Java WebSocket 可保留至最终切换窗口结束。

### Phase 9：全量双栈对账和切换

目标：证明所有活跃后端能力已经由 Rust 覆盖，并完成最终切换。

任务：

- 全量 REST 路径清单逐项标记 Rust handler、前端调用者、fixture 和回归证据；
- 全量 WebSocket 路径逐项标记协议、状态机和客户端证据；
- 对比 Java/Rust 的 OpenAPI、业务码、响应 fixture、数据库结果和日志字段；
- 运行完整前端构建和桌面端打包；
- 在 Linux amd64、Linux arm64、Windows/Electron 形态执行启动、登录、上传、训练、WebSocket 和退出 smoke；
- 进行同数据、同负载的性能和资源对比；
- 执行数据库备份、切换、回滚和再次切换演练；
- 关闭 Java 写入，观察稳定窗口，再停止 Java 流量；
- 保留 Java 可启动制品至 Rust 生产稳定和备份验证完成。

### Phase 10：Java 下线和仓库收口

目标：从交付链中移除 Quarkus，并让 Rust 成为唯一后端。

任务：

- 删除或归档 `backend/` Java 运行代码；
- 删除 Maven、Quarkus、JDK、Testcontainers Java 专属 CI；
- 更新根 README、后端说明、Docker、Electron 启动配置和部署手册；
- 将 Rust migration、schema rehearsal、运行手册设为唯一入口；
- 删除双栈路由和 Java 回滚配置；
- 清理不再使用的 DTO、旧 OpenAPI、旧 fixture 和旧部署产物；
- 全量静态搜索 Java endpoint、旧服务地址、旧端口和旧构建入口；
- 执行最终备份和恢复演练。

出口门禁：

- 仓库构建只依赖 Rust/前端需要的工具链；
- 无活跃请求仍指向 Java；
- 生产启动、升级、回滚、备份恢复和监控文档完整；
- Rust 全量测试、前端构建、桌面端打包和部署 smoke 全部通过。

## 6. 双栈迁移和路由策略

### 6.1 路由原则

迁移期间由反向代理或网关按**完整业务域**路由，不按单个请求随机分流。Rust 试点服务监听 18002，Java 继续监听 18001；前端不直接感知两个后端地址。

```text
/api/<Phase0确认的只读域>/**       -> Rust :18002（仅通过网关灰度）
/api/user/**                        -> Java :18001，直到 Phase 3 完成
/api/theoryKnowledge/**            -> Java :18001，直到 Phase 5 完成
/api/*training*/**                  -> Java :18001，直到对应训练阶段完成
/websocket*                         -> Java :18001，直到 Phase 8 完成
```

`/api/tools/**` 只能作为候选示例，不能在 Phase 0 inventory 未确认前写死为路由；`/api/cable/**` 同理。具体路由以 Phase 0 生成的真实端点清单和域级单写者决议为准。不得把同一域的读请求切到 Rust、写请求留在 Java，却没有明确一致性策略；不得让 Rust 在没有网关回滚开关时直接占用 18001。

### 6.2 切换模式

每个域使用以下顺序：

1. Java baseline：记录输入、响应和数据库状态；
2. Rust shadow/read comparison：只读比较，不产生副作用；
3. Rust canary：低风险用户或测试房间使用 Rust；
4. Rust primary：Rust 成为唯一写者；
5. rollback window：保留 Java 入口但禁止并发写入；
6. domain complete：关闭 Java 该域流量。

不使用默认双写。双写只有在数据一致性方案、幂等键和补偿机制通过评审后才能启用。

### 6.3 回滚原则

- 只在域边界回滚，不在一个事务中混用两个服务；
- 回滚前停止 Rust 写入并确认连接、队列、事务已排空；
- 若 Rust 已写入数据，先按域设计恢复/补偿，再恢复 Java 写入；
- 所有回滚动作必须记录版本、时间、数据库快照和业务影响；
- WebSocket 回滚需关闭 Rust 连接、清理房间 owner，再恢复 Java endpoint，避免双连接广播。

## 7. 测试和验收体系

### 7.1 共享契约测试

建立独立 runner，对 Java 和 Rust 分别执行相同请求 fixture，比较：

- HTTP status；
- JSON schema；
- `code`、message、description；
- data 字段、null、数字、时间、数组顺序；
- 数据库变化；
- 日志中的 request ID 和错误分类。

允许差异必须显式写入 fixture 规则，不能在比较器中静默忽略。

### 7.2 数据库测试

至少覆盖：

- 并发唯一约束；
- 事务中途失败；
- 删除后插入失败；
- 批量导入部分失败；
- 重复 finish/reset；
- 房间结束和成员退出；
- 分页上限、空查询、null 和 decimal；
- schema 与实体对账；
- current/base 快照迁移 rehearsal。

### 7.3 WebSocket 测试

至少覆盖：

- 握手和身份；
- 正常消息流；
- 心跳和失活；
- 重连退避；
- 同用户重复连接；
- 多人加入、退出、踢除和广播；
- 坏 JSON、未知消息码、非法字段；
- 网络断开、服务关闭、房间删除；
- 旧回调影响新连接；
- 同一房间并发修改。

### 7.4 前端和桌面端验收

每个域切换后必须：

- 执行相关 API 模块静态调用面检查；
- 执行前端 build；
- 在真实前端页面验证 Network 和页面状态；
- 在 Electron 外壳验证 API、WS、上传、下载和退出；
- 对登录失效、业务错误和网络失败验证提示和清理行为。

## 8. 统一阶段出口门禁

任何业务域未通过以下门禁，不得标记完成：

### 静态门禁

- 端点和前端调用者清单无遗漏；
- Rust handler、service、repository、fixture 路径明确；
- 无同域双写；
- 无敏感字段泄露；
- 无 query token 新增；
- 无隐藏的 fallback、吞异常或空成功响应。

### 行为门禁

- REST 契约测试通过；
- 业务码和 HTTP 语义等价；
- 数据库状态和回滚行为等价；
- WebSocket 状态机和消息顺序等价；
- 前端页面和 Electron smoke 通过；
- 性能不低于 Java 基线的预设阈值，阈值在压测前冻结。

### 运维门禁

- 容器和目标平台构建成功；
- 启动、健康检查、优雅关闭和升级通过；
- 日志、指标、告警、request ID 和脱敏通过；
- 备份、恢复、迁移和回滚演练通过；
- 生产配置不包含 root/root 等硬编码凭据。

## 9. 交付拆分和提交约定

一个阶段不能作为一个超大提交交付。按以下提交粒度拆分：

1. Rust workspace 和 CI 骨架；
2. 基础配置、日志、错误、响应和健康检查；
3. 数据库实体和 schema 对账；
4. 认证/授权基础设施；
5. 每个业务域的 DTO；
6. 每个业务域的 repository；
7. 每个业务域的 service/handler；
8. 对应契约、数据库和浏览器测试；
9. 路由切换和回滚配置；
10. Java 域下线和文档收口。

每个独立任务一个 commit。跨栈契约、实现和回归测试必须同一提交。不得使用 `git add -A` 携带无关变更。

## 10. 关键风险与应对

| 风险 | 级别 | 应对 |
|---|---|---|
| 全量端点遗漏 | 高 | Phase 0 建立 endpoint inventory；最终逐项销账 |
| ORM 语义差异 | 高 | 生成实体后人工对账；共享 SQL/JSON/DB fixture |
| 训练算法漂移 | 高 | characterization tests、纯函数和同输入输出对账 |
| 事务边界漂移 | 高 | 显式事务、失败注入、DB 状态断言、InnoDB 门禁 |
| WebSocket 并发错误 | 极高 | 状态机、单 owner、channel、并发/断线注入测试 |
| 双栈数据冲突 | 极高 | 域级路由、单写者、不默认双写 |
| 前端未覆盖调用 | 高 | API inventory + grep/LSP/浏览器 Network + 页面验收 |
| 跨平台交付失败 | 中高 | Linux amd64/arm64/Windows/Electron 构建和实际 smoke |
| 团队 Rust 维护能力不足 | 高 | 先完成平台骨架和一条域链路，再扩展；统一规范和 code review |
| Java 下线过早 | 极高 | 最终 Phase 10 前保留可启动 Java 制品和回滚演练 |

## 11. 首批实际任务顺序

执行时按以下顺序开始，不跳过 Phase 0：

1. 重新冻结 Java 测试、前端构建和数据库迁移基线；
2. 生成 REST、WebSocket、前端 API、数据库 schema 四张清单并写入 Phase 0 证据目录；
3. 创建 `backend-rust/` Cargo workspace 和独立 `rust-backend` CI；
4. 实现配置、日志、健康检查、统一响应和错误 mapper，默认只监听 18002；
5. 建立 MySQL 连接池、SeaORM entity 生成和 schema 对账工具，禁止自动 DDL；
6. 实现 header 会话认证和管理员授权，锁定 203/204/206/207/208 语义；
7. 从 Phase 0 inventory 选择并冻结一条低耦合只读域，不预设 tools/cable 必然可迁移；
8. 完成 Java/Rust 共享 fixture、网关路由、回滚和浏览器 smoke；
9. 通过 Phase 2 出口门禁后再进入用户域和写入域；
10. 每个阶段结束立即更新本计划的证据和终态，不把未执行项写成完成。

## 12. 完成定义

只有全部条件成立，才称为“全量 Rust 重写完成”：

- Rust 已覆盖所有活跃 REST 和 WebSocket 端点；
- 前端和 Electron 没有活跃请求指向 Java；
- 所有端点、业务码、响应、数据库写集和错误边界有证据；
- 所有训练评分、状态机、房间和 WebSocket 生命周期通过回归；
- MySQL 数据已完成备份、迁移 rehearsal、升级和恢复演练；
- Linux amd64、Linux arm64、Windows/Electron 交付通过；
- CI、镜像、配置、日志、指标、告警和运行手册已切换到 Rust；
- Java 已停止写入、停止流量并从交付链移除；
- Rust 版本具备可独立回滚到上一个 Rust 版本的发布能力；
- 没有未登记的兼容性差异、静默 fallback 或未验证的“理论上可用”路径。

## 13. 当前执行检查点(2026-09-27)

本节只记录当前仓库证据,不替代第12节完成定义,也不将部分迁移宣称为全量完成。

- Phase 0-5 既有证据仍以 `docs/reviews/rust-migration/phase0-gate.json` 至 `phase5-*.json` 及 `2026-09-21-java-baseline.json` 为准。
- Phase 6 当前状态为部分完成: 个人训练、报话生命周期、收报生命周期、电子键、电传及军语已有 Rust feature-flag 切片;完整评分等价、每域认证浏览器烟测和生产切换未完成。门禁审计见 [`phase6-gate.json`](../reviews/rust-migration/phase6-gate.json)。
- 个人`enteringExercise`已纠正旧切片的空题面/仅状态更新：复用前后端同一码本，六类题面与Java内容字符串对账通过；按服务器时钟重建正误与速度，暂停不可改答，完成幂等，详情仅允许读取已完成的协议0历史且旧版写入拒绝。真实Java/Rust四并发结束、统计写入失败回滚、Unicode跨栈持久重放和数值绑定边界已验证；真实浏览器完成创建→开始→键入→暂停→继续→结束，页面与数据库均为2正确、0错误、100%、3组/分、31秒。当前隔离Rust回归23项通过，细节及旧验收记录纠偏见 [`phase6-entering-writes.json`](../reviews/rust-migration/phase6-entering-writes.json) 与 [`phase9-frontend-smoke.json`](../reviews/rust-migration/phase9-frontend-smoke.json)。此闭环不替代设备拍发、其他训练域或完整Electron验收。
- Phase 7 当前已有岗位手键、电传、电子键、收报、组网、仿真、综合组训、报话配置和报话/军语/汉字单用户训练的Rust feature-flag REST切片；本轮已把原53条Java-only REST行实现为Rust路由/handler，并将收报要点读写端点纳入实现，覆盖训练生命周期、分页/页面提交、报底生成/结算、配置写入和设置查询，但剩余114条partial-domain仍需逐项完成Java/Rust行为对账、并发/回滚注入和前端域验收。证据见 [`phase7-domain-status.json`](../reviews/rust-migration/phase7-domain-status.json)、[`phase7-readwrite-slices.json`](../reviews/rust-migration/phase7-readwrite-slices.json)、[`phase7-radiotelephone-term.json`](../reviews/rust-migration/phase7-radiotelephone-term.json) 和 [`phase7-8-rest-ws-expansion.json`](../reviews/rust-migration/phase7-8-rest-ws-expansion.json)。
- Phase 8 已将Rust WebSocket重构为带generation/session guard的统一连接管理器，覆盖status、经典、union、simulation和general key/telex/ticker路径，含查询认证、角色/房间门禁、心跳、坏消息、替换、陈旧关闭、广播和清理；实际状态WS升级/心跳及无效凭据错误帧已冒烟通过，但浏览器/Electron完整工作流、压力/断线注入和Java逐消息fixture对账未完成。证据见 [`phase8-websocket-connection.json`](../reviews/rust-migration/phase8-websocket-connection.json)、[`phase8-websocket-protocol.json`](../reviews/rust-migration/phase8-websocket-protocol.json) 与 [`phase7-8-rest-ws-expansion.json`](../reviews/rust-migration/phase7-8-rest-ws-expansion.json)。
- Phase 8本轮补验：真实双栈经典手键六页1000批次与1001完成帧、空训练/空页、替换连接及旧关闭隔离已验证；在线名单查询参数/可空角色、仿真教学成员门禁、创建者名称、握手拒绝身份和空角色断开不误暂停已修复并复验。内嵌JSON对象成员顺序、无序在线名单遍历顺序和Gson解析诊断文案的差异明确保留在 [`phase8-websocket-protocol.json`](../reviews/rust-migration/phase8-websocket-protocol.json)，不宣称字节等价；其余消息族、压力与完整客户端验收仍待完成。
- Phase 9前次扩展检查点已生成359条Java REST清单（静态标记0条Java-only、114条Rust partial-domain），记录Rust 16 passed/2 ignored、release构建与局部REST/WS smoke、前端39/39及构建、Java 512/512。该历史记录不替代以下本轮真实路由和业务对账，也不代表跨平台、完整桌面或生产网关切换完成。保留证据见 [`current-coverage.json`](../reviews/rust-migration/current-coverage.json)、[`phase9-build-gate.json`](../reviews/rust-migration/phase9-build-gate.json)、[`phase9-frontend-smoke.json`](../reviews/rust-migration/phase9-frontend-smoke.json) 和 [`phase7-8-rest-ws-expansion.json`](../reviews/rust-migration/phase7-8-rest-ws-expansion.json)。
- 本轮真实路由核验纠正了静态“0条Java-only即覆盖完整”的推断：补齐用户查询/训练统计、汉字字库与要点写入、理论分类、速率/阶段设置、综合电子键九条训练路由及设备/电传删除契约。355条受保护清单中354条能实际到达Rust认证中间件；中文openApi路径在双栈均返回404，不能算作已验收的业务端点。
- 共享fixture已在独立Java/Rust隔离库与18121/18122真实实例执行，49/49的HTTP状态和完整JSON一致。另以非零数据核验八类时长、时间范围、近期成绩、NULL故障、协议400/415；综合电子键验证权威题面/评分、207/208、暂停恢复、四请求并发幂等结束及统计写入失败时事务回滚。修复了分钟精度ISO时间过滤、空统计字段静默成功、207文案和速度字符串精度差异；逐例响应与明确的时钟/生成ID边界见 [`phase9-contract-fixture-result.json`](../reviews/rust-migration/phase9-contract-fixture-result.json)。这些证据不替代其余域的扩展契约、WS及真实客户端验收。
- 本轮Rust单元测试19项通过，原默认忽略的2项MySQL测试已在隔离库显式执行并通过；共享比较器4项回归通过，debug构建通过。release/三平台/完整桌面门禁不沿用该debug结果；Java下线前置条件仍未全部满足。
- 2026-09-28岗位手键对账补验：Rust补回`findMessageBody`缺页的事务内按请求页生成；固定报底104组只预置1组时，Java/Rust第二页均产生`0123/4567/8901/2345`四组，Rust双并发请求复用同一批ID，库内仍仅4行，结束后缺页均返回HTTP200+202且不写入。三类随机平均报文104组均由双栈真实接口生成，数码/混合码的字符频次相同，字码均为104个四字符不重复组，但Java随机补位分支的具体字母频次与Rust不同，不能声明随机报文字节级等价。原有29条手键/电子键生命周期调用的状态、评分和持久行数一致；`deductInfo`内JSON成员顺序不同（前端按对象解析），独立实例采集时钟导致手键码率分别为11817/11852，不把这两项误报为已消除的差异。Rust `cargo test --locked -p rust-backend` 23 passed，`cargo build --locked -p rust-backend`通过；并行请求、真实接口和双隔离库证明此单点修复，但其余训练域、WS与切换门禁仍未闭合。
- 2026-09-28岗位电子键并发轮次门禁：隔离MySQL事务先锁父训练并把attempt改为1，同时用旧attempt调用reset；Java返回208、库为attempt=1/status=1，修复前Rust错误返回200并改成attempt=2/status=0，父行加锁后Rust与Java同为208且无状态改写。delete也改为在删子表前锁父行，避免与逐页写入竞态。新Rust实例重复执行岗位手键/电子键29条生命周期请求，HTTP/业务码和评分、attempt、子行数全部一致；本证明不代替故障注入的全部删除路径。
- 2026-09-28岗位电传与收报：双隔离库各33条真实请求的语义化JSON对账为0差异；电传冻结规则下完成score=100/speed=240000，原Rust把trainType=1误走trainType=4扣分字段分支（修前仅finish及重试各45处JSON字段差异），已按Java分支纠正。收报100组夹具只交1组后score=1，两侧状态与缺报一致；非空题面上传响应的`codeMessageBody`为null、详情仍为1条数组，与Java实际消费者契约相同。组别/逐页乱序、异常SQL回滚及真实播放速度仍需扩展验证。
- 2026-09-28组三领域：Java/Rust各完成key 25、telex 27、ticker 25步的角色/轮次/开始/提交/结束/恢复；对Java终态后的65秒时钟平移进行了隔离夹具验证，电子键缺行负零扣分格式已对齐。首轮ticker边界夹具在开始3.2秒后提交3秒区间时Java返回202、Rust返回200，暴露了独立时钟边界，不以其推断评分等价；另用共同有效的1秒区间，在双栈均得activeMillis=1000、speed=240、accuracy=100.00、score=280。不同硬件拍发与大页评分仍需覆盖。
- 2026-09-28仿真REST：四种房型34例归一化响应无差异，失败建房两侧房间数均4→4；终态stats=2时信道修改/发送完成/学生入房/保存设置均200且写后状态相同，null答案页两侧返回202并保持结果行数1→1；负bwCount不会溢出分配，均建房但生成0页；固定电缆startPage=999回退首层1234，startPage=2使用次层5678。该证据尚不覆盖仿真WebSocket全部成员广播与真实设备。
- 2026-09-28联合训练WebSocket：真实Java/Rust双连接的大厅10/11、建房120/121、入房130/111/121、席位类型171、检测171/181、回执183和详情1的码序及用户type/status相同；Rust对非成员房聊与冒名席位回执各发-1且不改变房间状态，畸形状态帧同报“改变房间状态处理失败”。Java当前`WebSocketUnionService.roomMessage`按声明的sendUser转发而未检查房间成员，生产仍有越权投递风险；Rust加严是显式安全差异，不能宣称该异常路径字节等价。Rust工作区`cargo test --locked --workspace`为25 passed/2 ignored，`cargo fmt --all -- --check`与debug构建通过；这些不替代release/三平台、完整桌面、生产切换和Phase10下线门禁。
- 2026-10-09认证双栈检查点（最终代码/registry基线`02155549`）：清单内全部端点按anon/admin/student身份、empty/filled/probe-str/probe-num变体重新执行真实HTTP对账；真实前端payload脚本覆盖理论学习/同名课程/答案trim、分类增改、定时考试/自测、个人报务/收报/基础收报/电传/键入电传、设备目录/设备绑定、固定报底、仿真信道、评分规则、岗位收报报底和分页，新增删除后的旧编辑页保存与已有作答的正式考试重建拒绝。这里的“真实前端payload”不是浏览器或Electron烟测；名称、类别计数、命令、闭合发现与mutation证据见[`phase9-dual-stack-sweep.json`](../reviews/rust-migration/phase9-dual-stack-sweep.json)。
- 2026-10-09比较边界：`mask`、`unordered`、`parse_json`、`normalize_keys`按步骤显式opt-in，registry按endpoint/variant/category登记；`allowed_paths`默认为精确叶路径，只有显式`subtree_paths`授权后代。全部28条受限规则逐路径验证Java/Rust值、类型及存在方向，数组`[len]`还保留并校验每条额外行的完整声明库存，不以四项`first_data_diffs`预览或长度单独豁免。最终旅程421请求、401 identical、20 ACCEPTED、0 FAIL/0 OPEN，源`/tmp/y7_final_journeys_after02155549.json`；完整扫描1870请求、1319 identical、551 ACCEPTED、0 FAIL/0 OPEN，源`/tmp/y7_final_full_after02155549.json`；两次顺序执行均exit 0。ACCEPTED是注明原因并受谓词约束的差异，不是原始JSON完全相等。
- 2026-10-09复核Y1-Y6全部闭合，逐项提交：`aefde87`禁止陈旧供给id复活删除考试（202「未查询到考试」，生成id仍直接INSERT）；`dbdf43c`评分规则先解析数据库规范id再按PK排序/去重并更新，保留锁后复查；`68c5154`收紧registry谓词；`176b55d`GET_LOCK前标记PoolConnection close_on_drop并保留配额，提交后先drop再池化读取；`c64408f8`仅JSON四种空白构成的历史收报body在list/detail转null，非空非法JSON仍报错；`02155549`正式考试作答后的重建拒绝对齐Java参数码202。触及域继续采用user行→父PK→子PK顺序，不以非PK查询或多行写入扩大锁范围。
- 2026-10-09刻意不复制Java缺陷：电传NULL时长滚转、键入记录接管/改类型/NULL类型、自测空试卷、空设置列表破坏性删除、空body写垃圾行；新增陈旧考试编辑页由Java未处理StaleObjectStateException的HTTP500转换为Rust定义202且不写入，该旅程按具名JAVA_UNHANDLED_500登记。刻意放宽为自测相同答案重试忽略JSON属性顺序、理论type缺省为0。registry现共162条显式规则（`backend-rust/scripts/dual-stack-sweep-registry.json`）；原138条分类仍为JAVA_DEFECT_ACCEPT 14、MALFORMED_INPUT_ONLY 108、STATE_ARTEFACT 16，其余24条为具名行为差异，不误称全部规则仅有三个分类。
- 2026-10-09最终验收均在最后代码/registry提交`02155549`之后：`cargo fmt --all -- --check`、`cargo test --locked`与debug构建通过，53 passed/27 ignored；隔离库显式执行考试writer 6/6、评分规则7/7、收报历史caller 1/1（`artifact://950`）；Python harness 24/24通过（`python3 -m unittest discover -s backend-rust/scripts -p 'test_*.py'`）。六项新增/变更DB回归分别拒绝陈旧保存复活、规范id大小写回退、取消后会话复用、空白解析回退、208映射回退和生成父记录不写入的mutation；Python另拒绝区域内值/类型/存在方向/任意额外行变异。一次给生成id加missing-PK锁读的额外探针未被既有并发门控拒绝；该门控不是gap升级必死锁的确定性证明，最终代码没有该锁读，亦不以本轮回归宣称完整压力证明。两栈扫描本次均重新运行，不沿用旧artifact。
- 2026-10-09 KNOWN LIMITATIONS / OPEN RISKS：UUID仅按形状掩码，不验证Java↔Rust身份/引用对应；HashMap只覆盖链表桶顺序，未模拟tree-bin（容量>=64且同桶>=8个碰撞id时仍需独立回归）；WS连接循环inline await数据库锁，`innodb_lock_wait_timeout=50s`低于前端90s陈旧阈值但仍阻塞连接；未触及域仍约90条非PK `FOR UPDATE`/多行锁语句（评审观察约数，不是扫描计数）。TEXT_ONLY描述差异保留；设备UI deviceId被两栈共同忽略；没有持久进程内WS帧测试设施，18001-18003仅由一次性probe验证。临时`/tmp`artifact不是已提交的原始响应归档。未证明浏览器/Electron烟测、生产切换、多平台构建或客户现场验收；Phase 10仍阻断。
- Phase 10 仍被前置条件阻断;Java运行代码、CI入口和回滚配置不得删除。审计见 [`phase10-preconditions.json`](../reviews/rust-migration/phase10-preconditions.json)。

## 14. 关联文档

- 可行性评估：[`../specs/2026-09-10-rust-backend-feasibility.md`](../specs/2026-09-10-rust-backend-feasibility.md)
- 当前全项目与核心训练复核：[`../reviews/2026-09-15-full-project-review.md`](../reviews/2026-09-15-full-project-review.md)
- 最新跨栈一致性复核：[`../reviews/2026-09-19-multi-model-debate-cross-stack-review.md`](../reviews/2026-09-19-multi-model-debate-cross-stack-review.md)
- 跨栈整改规格：[`../specs/2026-09-08-joint-fix-spec.md`](../specs/2026-09-08-joint-fix-spec.md)
- 后端技术和运行说明：[`../../backend/README.md`](../../backend/README.md)
- 工程约束：[`../../AGENTS.md`](../../AGENTS.md)
