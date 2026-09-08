# 后端服务（backend/ — quarkus-template）

基于 **Quarkus 3.20.4 / Java 21** 的海军报务（电报/键控/报底/推演）训练系统后端服务。提供 REST + WebSocket 双通道，覆盖手键/电子键拍发、抄报、理论考试、仿真推演等训练科目的下发、评分与统计。

- Maven 坐标：`com.nip:quarkus-template:1.1.0`
- 仓库：`JungleZy/007`
- 持久化：Hibernate ORM + Panache，MySQL 8.0.26（约 100 张业务表）
- 规模：main 约 744 个 Java 文件（61 controller / 73 service / 103 entity / 101 dao / 26 WebSocket 类），test 57 个测试类（Testcontainers 驱动）

---

## 技术栈

| 类别 | 选型 |
|---|---|
| 框架 | Quarkus 3.20.4（`quarkus-rest` + `quarkus-rest-jackson`） |
| 语言 / 运行时 | Java 21（Temurin 21.0.12.1），Maven Wrapper |
| ORM | Hibernate ORM、Hibernate ORM Panache |
| 数据库 | MySQL 8.0.26（JDBC `quarkus-jdbc-mysql`，连接池 Agroal） |
| 实时通道 | Jakarta WebSocket（`quarkus-websockets`） |
| 鉴权 | 自研 `@JWT` 拦截器 + SmallRye JWT |
| API 文档 | SmallRye OpenAPI（`/q/openapi`） |
| 工具库 | Hutool 5.8.12、commons-lang3、commons-codec、Gson、fastjson 1.2.78、Lombok |
| 测试 | JUnit5、REST Assured、Testcontainers（MySQL 8.0） |

---

## 快速开始

### 1. 环境要求

- JDK 21（本机路径示例：`$HOME/.local/opt/jdk21`）
- Docker（本地 MySQL 与测试期 Testcontainers）
- 无需预装 Maven，使用仓库自带的 `./mvnw`

### 2. 准备数据库（开发/生产用）

```bash
# 启动 MySQL 8.0.26 容器
docker run -d --name mysql-project006 \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=project006 \
  -p 3306:3306 mysql:8.0.26

# 导入库结构与数据
docker exec -i mysql-project006 mysql -uroot -proot project006 < database/project006.sql
```

> 数据源默认连接 `jdbc:mysql://localhost:3306/project006`，账号 `root/root`（见 `application.yml` 的 `%dev`/`%prod`）。

### 3. 开发模式运行

```bash
export JAVA_HOME=$HOME/.local/opt/jdk21
./mvnw quarkus:dev
```

- 服务监听 **18001** 端口（非 8080）；REST 统一前缀 **`/api`**。
- Swagger UI：`http://localhost:18001/q/swagger-ui`（dev 开启；prod 实测 404）。
- OpenAPI：`http://localhost:18001/q/openapi`。

### 4. 构建

```bash
export JAVA_HOME=$HOME/.local/opt/jdk21
./mvnw -B clean package                 # 生成 target/quarkus-app（fast-jar）
./mvnw -B clean package -Dnative        # GraalVM 原生镜像（需 native profile）
```

### 5. 测试

```bash
export JAVA_HOME=$HOME/.local/opt/jdk21
./mvnw -B clean verify                   # 全量测试套件（Testcontainers），提交前必跑
```

测试期不连本地 MySQL：`%test` profile 通过 Quarkus DevServices 拉起 `mysql:8.0` 容器（库名 `project006_test`，`generation=drop-and-create`），HTTP 测试端口 `18081`。因此运行测试**需要 Docker**。

---

## 配置说明

配置集中在 `src/main/resources/application.yml`，按 profile 分区：

| Profile | 数据库 | schema 策略 | 说明 |
|---|---|---|---|
| 默认 | — | `generation: none` | HTTP `0.0.0.0:18001`，CORS 全开，Agroal 池 `max-size=50 / min-size=20` |
| `%dev` | 本地 `project006` | none | `quarkus:dev` 使用；Agroal 空闲 1 分钟回收 + 2 分钟后台校验 |
| `%test` | DevServices `mysql:8.0` | `drop-and-create` | 测试端口 18081 |
| `%prod` | 本地 `project006` | **`validate`** | 启动即校验 schema，与实体不一致直接 fail-fast |

> **生产部署硬约束**：`%prod` 的 `generation=validate` 要求先执行迁移脚本
> `database/migrations/2026-08-26-01-schema-sync.sql` 与 `2026-08-26-02-engine-innodb.sql`，否则启动校验失败。

---

## API 约定

- **前缀**：`@ApplicationPath("/api")`（`common/MainApplication.java`），所有 REST 路径以 `/api` 开头。
- **方法**：以 `POST` 为主，参数多为 `Map<String,String>` 或实体 JSON。
- **鉴权**：类级 `@JWT` 拦截器（`common/interceptor/JWTInterceptor.java`）要求请求头（或同名 query 参数）携带 `token` + `deviceId`，并校验 `existsUserByTokenAndDeviceId`。`controller/free/**` 下的接口不拦截（如登录、注册）。
- **响应信封**：一律返回 **HTTP 200**，业务状态放在 JSON `code` 字段。统一封装类 `common/response/Response<T>`，工厂方法 `common/response/ResponseResult`（`success(...)` / `error(...)`）。

响应结构：

```json
{ "code": 200, "data": {}, "message": "ok", "description": "" }
```

业务码（`common/constants/ResponseCode`，鉴权码 203/204/206 是客户端契约，**禁止改动码值与文案**）：

| code | 含义 |
|---|---|
| 200 | 成功（`SUCCESS`） |
| 202 | 请求参数错误 |
| 203 | token 不能为空 |
| 204 | 请求参数为空 / 设备标识不能为空（语义由端点区分） |
| 206 | 账号登录凭证异常 |
| 500 | 服务器错误 |

登录（无需鉴权）：`POST /api/user/login`，body `{ "userAccount", "password", "deviceId" }`。

---

## WebSocket 端点

WebSocket 类位于 `com.nip.ws`，端点路径（相对根，非 `/api` 前缀）：

| 路径 | 用途 |
|---|---|
| `/websocket/{sid}` | 通用消息通道 |
| `/websocketUnion/{sid}` | 联合训练通道 |
| `/startWebsocket/{sid}` | 训练启动信令 |
| `/status` | 状态广播 |
| `/simulation/{id}/{roomId}` | 仿真推演房间 |
| `/generalKeyPatTrain/{uid}/{trainId}` | 通用键控拍发训练 |
| `/generalTelexPatTrain/{uid}/{trainId}` | 通用电传报底训练 |
| `/generalTickerPat/{uid}/{trainId}/{role}` | 通用抄报训练（按角色） |

---

## 打包与部署

- **JVM（fast-jar）**：`./mvnw package` → `target/quarkus-app/`，`java -jar target/quarkus-app/quarkus-run.jar`。
- **原生镜像**：`./mvnw package -Dnative`，产物 `target/*-runner`。
- **容器镜像**：`src/main/docker/` 提供 `Dockerfile.jvm`、`Dockerfile.legacy-jar`、`Dockerfile.native`、`Dockerfile.native-micro`。
- **CI**：`../.github/workflows/build-quarkus-native.yml` —— Linux runner 跑 Testcontainers 测试守门，随后在 linux-amd64 / linux-arm64 / windows-amd64 三个矩阵产出原生二进制，打 `v*` tag 时发布 Release。CI 的 `run` 步骤统一 `working-directory: backend`。

---

## 数据库与迁移

- 快照：`database/project006.sql`（当前）、`project006-base.sql`（基线）。
- 迁移脚本：`database/migrations/`（`01-schema-sync` 结构对齐 → `02-engine-innodb` 引擎转 InnoDB）。
- **存储引擎自检**：`common/LifecycleApplication` 在启动时扫描 `information_schema`，发现 MyISAM 表时——生产（`NORMAL`）抛异常阻断启动并提示执行迁移 02，dev/test 仅告警。原因：MyISAM 不支持事务，`@Transactional` 回滚在其上是空操作，结算类「先删后插」一旦中断即永久丢数据。
- 迁移演练记录见 `database/rehearsal/` 与 `../docs/reviews/*-migration-rehearsal.md`。

---

## 目录结构

```
src/main/java/com/nip/
├── common/            # 基础设施：MainApplication、拦截器、异常映射、response 信封、工具类、常量
│   ├── constants/     #   ResponseCode、BaseConstants（header/参数名）
│   ├── interceptor/   #   @JWT 鉴权拦截器
│   ├── response/      #   Response<T> / ResponseResult
│   ├── exception/     #   各类 ExceptionMapper（统一 200 信封）
│   └── utils/         #   Hutool 补充、雪花 ID、分页、评分数学等
├── controller/        # REST 端点（61 个），free/ 为免鉴权
├── service/           # 业务服务（73 个），general/ simulation/ detector/ 等子域
├── dao/               # Panache DAO（101 个）
├── entity/            # JPA 实体（103 个）
├── dto/               # vo/ sql/ general/ 传输对象
└── ws/                # WebSocket 端点与会话模型（26 个）
```

---

## 文档索引

- **当前全项目评审（唯一入口）**：[`../docs/reviews/2026-09-08-full-project-review.md`](../docs/reviews/2026-09-08-full-project-review.md)。
- **前后端联合评审详细证据**：[`../docs/reviews/2026-09-08-joint-frontend-backend-review.md`](../docs/reviews/2026-09-08-joint-frontend-backend-review.md)。
- 历史评审、分片、审计与迁移文字记录：`../docs/reviews/archive/`。
- 整改规格 / 计划：`../docs/specs/`、`../docs/plans/`。
- 迁移演练：`database/rehearsal/`。
- 全仓文档地图：[`../docs/README.md`](../docs/README.md)。
