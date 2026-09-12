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
- 约 100 张业务表、约 744 个 Java main 文件、61 个 controller、73 个 service、103 个 entity、101 个 dao、26 个 WebSocket 类（以 `backend/README.md` 记录为基线）。

前端是 Electron 外壳 + Vue 页面，API 模块直接使用 `/api/...`，请求拦截器自动附加 `token` 和 `deviceId`。

### 2.2 基线数字先重新冻结

仓库文档中存在不同历史测试数字：根 README 记录 228 个测试，联合整改计划的最新出口记录 240 个测试。因此重写开始前必须重新执行一次：

```bash
cd backend
export JAVA_HOME=$HOME/.local/opt/jdk21
./mvnw -B clean verify
```

该结果、当前 commit、数据库快照校验和前端构建结果写入 Phase 0 证据文件。后续不能继续引用旧数字。

### 2.3 不在本计划中偷偷改变的内容

除非另立并批准 API/安全/数据库迁移规格，本计划不改变：

- REST 路径、HTTP 方法、请求字段、响应字段和业务码；
- HTTP 200 + `Response<T>` 信封语义；
- 203、204、206 的码值和文案；
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
│   ├── auth/            # 会话认证、管理员授权、密码迁移
│   ├── domain/          # 业务模型、状态机、领域错误、纯函数算法
│   ├── persistence/     # SeaORM entity、repository、事务和查询
│   ├── realtime/        # WebSocket、连接、房间、广播、心跳
│   ├── file-service/    # 上传、解析、导入、导出
│   └── observability/   # tracing、指标、请求 ID、脱敏规则
├── migration/           # Rust 侧版本化迁移；初期只做校验和新增变更
├── tests/
│   ├── contract/        # Java/Rust 共享 REST fixture
│   ├── websocket/       # 协议与生命周期测试
│   └── database/        # MySQL 集成和回滚测试
└── deploy/              # Docker、systemd/Electron 运行包、CI 辅助文件
```

这是逻辑边界，不要求一开始拆成大量独立微服务。优先使用一个 Rust 二进制、一个 MySQL 连接池和清晰的 crate 边界，避免语言迁移同时变成微服务迁移。

### 3.2 技术基线

| 能力 | 选择 | 约束 |
|---|---|---|
| Runtime | Tokio | 所有阻塞文件/压缩/CPU 密集任务必须显式隔离 |
| HTTP | Axum + Tower / tower-http | middleware 显式实现认证、授权、超时、CORS、追踪 |
| JSON | serde / serde_json | DTO 与数据库模型分离，禁止实体直接作为敏感响应 |
| ORM | SeaORM | 先生成实体再人工校正；生产禁止自动 schema sync |
| SQL | SeaORM query / raw SQL | 复杂查询必须有 SQL 和结果 fixture；不默认引入第二套连接池 |
| WebSocket | Axum `extract::ws` | 连接态按连接保存，房间态由显式 manager 管理 |
| 错误 | `thiserror` + 统一 response mapper | 领域错误、认证错误、系统错误分层 |
| 日志 | tracing | token、password、deviceId、完整请求体脱敏 |
| 密码 | 兼容现有版本化哈希并渐进升级 | 不新增旧 MD5 写入；存量迁移另有回归 |
| 迁移 | 版本化 SQL / SeaORM migration | 初期不重建既有 schema；任何 DDL 先 rehearsal |
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

- `token + deviceId` 仍从 header 读取；
- 缺 token、缺 deviceId、凭证异常保持 203/204/206；
- 管理员授权由服务端完成，不依赖前端 `v-per`；
- 多角色判断使用“存在管理员角色”，不能取任意第一条；
- 普通用户、无角色、匿名调用管理端点必须保持目标数据不变；
- 登录、用户资料、用户目录和管理列表继续使用不同 DTO；
- 任何日志和错误追踪不得泄露凭据。

### 4.4 WebSocket 不变量

- 路径和消息码保持不变；
- 单连接、在线用户、房间成员和房间广播状态边界明确；
- 重复连接、断线、心跳超时、手动关闭、重连和房间销毁有确定顺序；
- 坏消息返回协议错误，不触发无关用户清理；
- 旧连接回调不能关闭或删除新连接状态；
- 所有共享状态访问有明确锁或 actor/channel 所有权；
- WebSocket 切换必须保留 Java 回滚入口，直至全量验收完成。

## 5. 阶段计划

### Phase 0：冻结基线和建立重写分支

目标：在任何 Rust 代码开始前，把 Java 当前行为变成可重复的迁移基线。

任务：

- 重新执行后端 clean verify，记录测试总数和完整输出；
- 执行前端 `npm ci` 和 `npm run build`；
- 固化当前 OpenAPI 输出、路由清单和前端 API 调用清单；
- 扫描 REST controller、WebSocket endpoint、上传/导出端点；
- 从 `backend/database/project006.sql` 和 migration 生成 schema inventory；
- 记录所有表的引擎、主键、索引、关联、nullable 和默认值；
- 建立测试用户、普通用户、无角色用户、管理员和训练样本 fixture；
- 记录训练纯函数、评分和报文生成的特征样本；
- 记录 WebSocket 握手、心跳、消息码、关闭和重连样本；
- 建立 `backend-rust/` 初始工程和 CI 最小骨架，但不接管流量。

出口门禁：

- Java 后端、前端构建和数据库 rehearsal 有可重复证据；
- OpenAPI、前端调用清单、WS 协议清单和 schema inventory 入库；
- 所有未决历史问题已标记为“迁移必须保持 / 独立整改 / 接受风险”；
- Rust 空服务可构建、启动、健康检查和优雅退出。

### Phase 1：Rust 平台基础设施

目标：完成所有业务域共用的运行时能力。

任务：

- 配置加载：端口 18001、数据库 URL、body limit、CORS、日志等级；
- `/api` 路由前缀和统一 `Response<T>`；
- 错误分类与 mapper：参数错误、认证错误、授权错误、业务错误、系统错误；
- MySQL 连接池、超时、TLS、启动连接检查；
- token/deviceId 认证 middleware；
- RequireAdmin policy；
- request ID、结构化日志、敏感字段脱敏；
- health/readiness/liveness；
- graceful shutdown 和连接清理；
- Docker、Linux amd64/arm64 构建和启动 smoke test；
- OpenAPI 输出和契约 fixture 测试框架。

出口门禁：

- 缺 token、缺 deviceId、无效凭证和无管理员权限的响应与 Java 基线一致；
- 未捕获错误不会被吞成成功；
- HTTP 200 与业务码边界通过测试；
- Rust 服务不修改业务表；
- CI 能构建并启动 Rust 二进制。

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

迁移期间由反向代理或网关按**完整业务域**路由，不按单个请求随机分流：

```text
/api/tools/**                         -> Rust
/api/cable/**                         -> Rust 或 Java，单域单写者
/api/user/**                          -> Java，直到 Phase 3 完成
/api/theoryKnowledge/**               -> Java，直到 Phase 5 完成
/api/*training*/**                    -> Java，直到对应训练阶段完成
/websocket*                           -> Java，直到 Phase 8 完成
```

具体路由以 Phase 0 生成的真实端点清单为准。不得把同一域的读请求切到 Rust、写请求留在 Java，却没有明确一致性策略。

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
2. 生成 REST、WebSocket、前端 API、数据库 schema 四张清单；
3. 创建 `backend-rust/` Cargo workspace 和最小 CI；
4. 实现配置、日志、健康检查、统一响应和错误 mapper；
5. 建立 MySQL 连接池、SeaORM entity 生成和 schema 对账工具；
6. 实现 header 会话认证和管理员授权；
7. 选择 tools / 固定报文目录只读接口做第一条完整域链路；
8. 完成 Java/Rust 共享 fixture、路由切换、回滚和浏览器 smoke；
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

## 13. 关联文档

- 可行性评估：[`../specs/2026-09-10-rust-backend-feasibility.md`](../specs/2026-09-10-rust-backend-feasibility.md)
- 当前全项目评审：[`../reviews/2026-09-08-full-project-review.md`](../reviews/2026-09-08-full-project-review.md)
- 跨栈整改规格：[`../specs/2026-09-08-joint-fix-spec.md`](../specs/2026-09-08-joint-fix-spec.md)
- 当前联合整改计划：[`2026-09-08-joint-fix-plan.md`](2026-09-08-joint-fix-plan.md)
- 后端技术和运行说明：[`../../backend/README.md`](../../backend/README.md)
- 工程约束：[`../../AGENTS.md`](../../AGENTS.md)
