# 007 舰船报务综合训练系统全项目 Review

- **评审日期**：2026-09-08
- **评审对象**：单仓双工程：`backend/` Quarkus 服务、`frontend/` Vue 桌面前端，以及 `docs/`、数据库、迁移和 CI 交付配置
- **评审基线**：当前工作树 HEAD `d380e5c`
- **评审结论**：后端构建与测试基线良好，但项目仍存在一条已运行验证的 P0 级匿名跨用户数据篡改问题，以及多条鉴权、敏感数据、前后端契约、实时通信和交付可复现性问题。不建议按“已收口”状态直接交付。
- **代码变更**：本次仅形成评审文档，未修改业务代码。

> 本文是全项目评审结果汇总。跨栈契约以 [`2026-09-08-joint-frontend-backend-review.md`](2026-09-08-joint-frontend-backend-review.md) 为主要参考；后端单侧历史问题需结合当前 HEAD 重新判断，不能直接复用旧报告的全部条目。

---

## 1. 项目概况

项目采用单仓双工程结构：

- 后端：Quarkus 3.20.4、Java 21、Hibernate ORM/Panache、MySQL、REST + WebSocket；
- 前端：Vue 3.5、Vite 4、Ant Design Vue，运行于外部 Electron 宿主；
- 后端 REST 前缀：`/api`；默认端口：`18001`；
- 测试使用 Quarkus DevServices 和 Testcontainers 启动 MySQL；
- 生产 profile 使用 Hibernate schema `validate`，数据库迁移顺序是生产启动前提；
- 前端当前没有提交 lockfile，也没有纳入现有 GitHub Actions 流程。

仓库已有后端单侧报告、前端单侧报告和前后端联合报告。本次复核重点是：

1. 现有报告中的问题在当前 HEAD 是否仍然成立；
2. 前后端接口、响应码和数据表示是否一致；
3. 认证、授权、会话、敏感字段和富文本处理是否形成有效安全边界；
4. 数据库迁移、CI、前端构建和部署形态是否闭合；
5. 能够运行验证的关键结论是否与源码推断一致。

---

## 2. 总体结论和优先级

| 优先级 | 数量/范围 | 结论 |
|---|---:|---|
| P0 | 1 | 匿名注册接口可修改任意已有用户身份字段，已在隔离数据库复现 |
| P1 | 多项 | 敏感凭据响应、管理接口无角色授权、房间详情契约断裂、204 语义冲突、文档导入契约冲突 |
| P2 | 多项 | 未鉴权写接口、WebSocket 生命周期、HTTPS URL、XSS、Axios 网络错误、构建不可复现等 |
| P3 | 多项 | 版本真源、死代码、状态管理重复、局部 UI 权限生命周期问题 |

当前最重要的判断：

- 后端 216 个测试全绿不能证明鉴权边界正确；
- 前端生产构建成功不能证明 Web 部署、HTTPS 反代和后端契约可用；
- 前一轮后端报告中的大量 P1 已被后续提交修复；
- 当前仍有新的、比历史“内网鉴权风险”更严重的匿名数据篡改问题。

---

## 3. P0：匿名注册接口可修改任意已有用户

### 3.1 证据

免鉴权接口：

```text
backend/src/main/java/com/nip/controller/free/UserController.java:49-53
```

```java
@POST
@Path("/signin")
public Response<Object> signin(UserEntity entity) {
  return userService.addUser(entity, true);
}
```

服务层根据客户端传入的 `id` 自动决定新增或修改：

```text
backend/src/main/java/com/nip/service/UserService.java:155-173
backend/src/main/java/com/nip/service/UserService.java:284-309
```

当请求中的 `id` 非空时：

1. 查询该 ID 对应的用户；
2. 进入 `handleExistingUser`；
3. 覆盖 `userName`、`userAccount`、`idCard`、`userSex`、`eday`、`bday`；
4. 没有 token、归属关系或管理员角色判断；
5. 已有用户分支没有再次检查 `userAccount` 唯一性。

### 3.2 运行复现

在隔离数据库中预置用户 `victim-007` 后，匿名调用：

```http
POST /api/user/signin
Content-Type: application/json
```

请求体：

```json
{
  "id": "victim-007",
  "userName": "Overwritten",
  "userAccount": "attacker007",
  "idCard": "110105194901011234",
  "userSex": 1,
  "eday": "2099"
}
```

接口返回 HTTP 200 和 `code: 200`，数据库实际变为：

```text
victim-007 | Overwritten | attacker007 | ... | 1 | 2099
```

### 3.3 影响

攻击者只要获得有效用户 ID，就可以：

- 修改受害者登录账号，造成账号锁定或登录名冲突；
- 修改身份信息；
- 伪造用户资料；
- 破坏后续登录和管理数据。

### 3.4 修复建议

注册接口必须强制新用户语义：

- 拒绝非空 `id`；或在注册入口将 `id` 强制置为 `null`；
- 管理员编辑只能走鉴权后的管理接口；
- 将 `registerUser` 与 `updateUser` 拆成两个明确的服务方法；
- 新增数据库层 `user_account`、`id_card` 唯一约束；
- 增加匿名注册回归测试，确认带已有 ID 的请求不会修改任何已有行。

---

## 4. 后端鉴权和敏感数据

### 4.1 用户接口直接返回 password、token、deviceId

位置：

```text
backend/src/main/java/com/nip/controller/UserController.java:88-93
backend/src/main/java/com/nip/entity/UserEntity.java:61-66
```

`getAllUser` 返回原始 `UserEntity`，其中包含：

- `password`；
- `token`；
- `deviceId`。

鉴权逻辑通过 `token + deviceId` 判断会话是否有效：

```text
backend/src/main/java/com/nip/common/interceptor/JWTInterceptor.java:52-68
backend/src/main/java/com/nip/dao/UserDao.java:60-63
```

因此普通用户一旦能调用用户列表或详情接口，就可能取得其他用户的有效会话凭据并进行会话冒用。

类似风险还需要检查：

- `getAllUserByContent`；
- `getUserById`；
- `getUsersByToken`；
- 其他直接返回 `UserEntity` 的接口。

**建议**：禁止实体直接作为响应类型，使用不含密码和会话字段的响应 DTO；同时将敏感字段增加序列化层保护。

### 4.2 管理端点没有角色授权

当前 `JWTInterceptor` 主要验证 token 和 deviceId 是否存在，不负责管理角色授权。

管理接口包括：

```text
backend/src/main/java/com/nip/controller/UserController.java:43-93
backend/src/main/java/com/nip/controller/UserController.java:166-177
backend/src/main/java/com/nip/controller/RoleController.java:43-48
backend/src/main/java/com/nip/controller/MenusController.java:41-46
```

涉及新增/编辑用户、删除用户、重置密码、分配角色、新增角色、新增菜单和全量用户查询。

前端 `v-per` 只能控制按钮显示，不能构成服务端授权边界。

**建议**：增加统一的服务端管理员授权机制，并对每个管理写端点做角色判定。管理操作不应依赖客户端菜单或按钮权限。

### 4.3 多个写入/删除端点没有 `@JWT`

代表性位置：

```text
backend/src/main/java/com/nip/controller/CableController.java:52-64
backend/src/main/java/com/nip/controller/CableTypeController.java:35-52
backend/src/main/java/com/nip/controller/DeviceController.java:40-61
```

部分报文、设备、规则和训练状态接口没有服务端身份校验，可能允许匿名修改或删除数据。完整清单需按 controller 目录继续核对，并在修复时为每个写端点增加回归测试。

### 4.4 token/deviceId 允许通过 query 参数传递

```text
backend/src/main/java/com/nip/common/interceptor/JWTInterceptor.java:52-68
```

当请求头没有凭据时，拦截器会从 query 参数读取 token/deviceId。

这会使凭据进入：

- 访问日志；
- 代理日志；
- 浏览器历史；
- Referer；
- 监控和错误报告。

**建议**：只允许请求头传递凭据，并删除 query 参数兼容逻辑。

### 4.5 密码使用 MD5

相关调用：

```text
backend/src/main/java/com/nip/service/UserService.java:234
backend/src/main/java/com/nip/service/UserService.java:390
backend/src/main/java/com/nip/service/UserService.java:474
```

MD5 不适合密码存储，缺少 salt 和足够的计算成本。

**建议**：迁移到 Argon2id、bcrypt、scrypt 或 PBKDF2，并设计旧密码登录时渐进升级机制。

### 4.6 生产数据库凭据硬编码

```text
backend/src/main/resources/application.yml:58-69
backend/src/main/resources/application.yml:83-100
```

开发和生产配置均使用 `root/root`。建议使用 Secret 或环境变量，并为应用建立最小权限数据库账户，禁止应用使用 root。

---

## 5. 前后端接口契约

### 5.1 房间详情使用了不一致的查询键

前端仍然传：

```text
roomgId
```

位置：

```text
frontend/src/views/manage/unionJob/lineNotify/js/Issue.js:149,290
frontend/src/views/manage/unionJob/lineNotify/components/ListenIn.vue:404,483
frontend/src/components/BroadcastTeachTrain/js/useBroadStudent.js:265
frontend/src/components/BroadcastTeachTrain/js/useBroadTeacher.js:288
frontend/src/views/manage/unionJob/broadcastTeacheing/js/useBroadcastTrain.js:21
```

后端绑定：

```text
backend/src/main/java/com/nip/controller/simulation/SimulationRouterRoomController.java:69-74
```

使用的是 `ROOM_ID`，实际键为 `roomId`。

前端 GET 包装器本身是正确的：

```text
frontend/src/common/http/axios.js:20-24
```

因此这里的根因是**键名不一致**，不是 GET body 丢失。

**影响**：房间详情中的 ID 绑定为空，router 房间可能返回 `code:500`，其他消费点还可能继续读取空的 `res.data`。

### 5.2 204 同时表示鉴权失败和业务参数错误

后端定义：

```text
backend/src/main/java/com/nip/common/constants/ResponseCode.java:10-21
```

存在：

- 鉴权语义的 204：设备标识为空；
- 业务语义的 204：请求参数为空。

前端拦截器：

```text
frontend/src/common/http/index.js:29-44
```

将所有 204 当作鉴权失败并跳转登录。

例如修改密码字段为空时，普通业务错误会被误认为凭证失效，导致用户被登出且输入丢失。登录页分支还存在裸 `return`，会向调用方返回 `undefined`。

**建议**：将业务参数错误迁移到 202，保留鉴权码 203/204/206；前端统一返回响应信封，不返回 `undefined`。

### 5.3 文档导入格式与后端能力冲突

前端允许：

```text
frontend/src/views/manage/basicTheory/study/basic/edit/Index.vue:168
frontend/src/views/manage/equipment/equipmentIndex.vue:204,246
```

包括 `.doc`、`.docx`、`.pptx`。

后端只允许：

```text
backend/src/main/java/com/nip/service/TheoryKnowledgeClassifyService.java:100-133
```

包括 `txt`、`md`、`csv`。

Office 文件会被拒绝，前端错误路径还可能访问 `data.imgUrls`，但错误响应的 data 为 null。

**建议**：将前端 accept 改为 `.txt,.md,.csv`，并补空值保护；如果产品必须支持 Office，则实现真实的前端解析或后端解析能力，不能只改变提示文字。

### 5.4 自测开始时间字段不一致

后端使用 `start_time`，前端读取 `startTime`：

```text
frontend/src/views/manage/basicTheory/test/test/studentGradeList/Index.vue:31-33
backend/src/main/java/com/nip/dto/vo/TheoryKnowledgeExamUserSelfVO.java
```

结果是开始时间为空，按时间排序得到 `NaN`。

建议全站统一 camelCase wire format，短期至少修复该页面的读取和排序字段。

### 5.5 电传组训调用错误业务域

```text
frontend/src/views/manage/organization/telexZuXun/train/student/js/datagramTrain.js:8-11
```

电传组训调用了电子键和手键域 API，包括 finish、getPage、uploadResult、reset；WebSocket 也使用：

```text
/generalKeyPatTrain
```

而电传后端端点是：

```text
/generalTelexPatTrain
```

**影响**：训练结果写入错误业务域，reset 可能操作另一个训练类型的数据。

**建议**：统一切换到 `datagramZuXun.js` 和 `generalTelexPat*` API；为电子键域增加本域 reset 端点，不得继续调用手键 reset。

---

## 6. 前端网络、会话和实时通信

### 6.1 HTTPS 反向代理下 URL 生成错误

多个文件使用：

```js
"http://" + window.httpUrl
"ws://" + window.httpUrl
```

例如：

```text
frontend/src/views/manage/unionJob/js/UnionWs.js:41
frontend/src/views/manage/unionJob/disturbCode/js/train.js:224
frontend/src/views/manage/basicTheory/study/basic/edit/Index.vue:198,230
frontend/src/views/manage/basicTheory/test/questionBank/js/knowledgeTabel.js:588
frontend/src/views/manage/equipment/equipmentIndex.vue:272
```

HTTPS 分支中 `window.httpUrl` 已经是 `https://...`，继续拼接后会形成：

```text
http://https://...
ws://https://...
```

会导致上传、SSE、导出和部分 WebSocket 在正式 HTTPS 反代形态下失败。

**建议**：集中提供协议感知的 `apiUrl()`、`wsUrl()`，所有自建 URL 统一走一个入口。

### 6.2 Axios 没有超时和传输层错误提示

```text
frontend/src/common/http/index.js:5-11
frontend/src/common/http/index.js:70-111
```

timeout 被注释，且无 `error.response` 时直接 reject。后端不可达、DNS、CORS、网络中断等场景可能长期 pending，用户没有提示。

**建议**：设置真实 timeout，并增加无 HTTP 响应时的网络错误提示。

### 6.3 WebSocket 重连风暴和资源生命周期不完整

```text
frontend/src/common/ws/Ws.js:67-81
frontend/src/common/ws/PublicSocket.js:32-48
frontend/src/common/ws/MessageWebSocket.js:81-106
```

问题包括：

- 固定间隔无限重连；
- 无指数退避、抖动和最大等待时间；
- 无心跳和半开连接检测；
- `send` 前部分路径不检查 `readyState`；
- singleton 退出后可能保留旧实例；
- 页面切换后 timer、socket 和回调清理不完整。

**建议**：抽共享连接生命周期工具，统一处理退避、心跳、关闭、销毁和发送状态检查。

### 6.4 仿真 WebSocket 未隔离坏消息异常

```text
backend/src/main/java/com/nip/ws/WebSocketSimulationService.java:355-364
```

客户端消息中的数值解析可能抛异常，异常继续进入 `@OnError` 和连接清理路径，导致正常训练参与者被移除。

相比之下，Union WebSocket 已有异常隔离。仿真 WebSocket 应保持一致：返回协议级错误帧，不因单条坏消息关闭正常连接。

### 6.5 会话材料放入 localStorage

```text
frontend/src/views/manage/login/useLogin.js:121-126
frontend/src/common/http/index.js:15-18
```

token、deviceId、用户角色和动态路由都在 localStorage 中保存。任何同源 XSS 都可以读取并重放会话。

建议 Web 部署使用安全 Cookie 或短期 token；Electron 使用系统安全存储，并减少渲染进程直接接触长期凭据。

### 6.6 自动登录明文保存密码

```text
frontend/src/views/manage/login/useLogin.js:127-134
```

自动登录会将用户名和密码写入 localforage/IndexedDB。应改为 refresh token 或 Electron 安全存储，禁止落地明文密码。

### 6.7 前端富文本没有统一净化

位置：

```text
frontend/src/views/manage/equipment/equipmentIndex.vue:23-24
frontend/src/views/manage/basicTheory/study/basic/details/js/useDetails.js:50-74
```

存在 `v-html` 和 `iframe.document.write(backendContent)`，未发现统一白名单净化。

**影响**：恶意课程或设备说明可构成存储型 XSS，并进一步读取 localStorage 会话材料。

**建议**：使用白名单净化器，禁止危险标签、属性和 URL scheme；增加 CSP；将 iframe 内容改为安全的受控渲染路径。

### 6.8 `v-per` 权限指令实际问题

```text
frontend/src/config/directive/ButtonPermission.js:1-25
```

需要纠正历史报告的一点：权限列表已加载时，缺少权限会删除元素，行为是拒绝，不是放行。

真实问题是：

- 权限为 null/undefined 时，元素被保留，权限尚未加载时 fail-open；
- 只有 `mounted`，权限后续变化不会重新计算；
- 它始终只能是 UI 门控，不能替代后端授权。

---

## 7. 数据库、部署和交付

### 7.1 数据库引擎迁移机制已改善，但仍是生产硬约束

迁移脚本：

```text
backend/database/migrations/2026-08-26-02-engine-innodb.sql
backend/database/migrations/2026-09-08-01-unique-lazy-create.sql
```

当前已经补充 MyISAM → InnoDB 和懒建唯一约束逻辑，历史报告中相应的部分问题已被修复。

生产配置：

```text
backend/src/main/resources/application.yml:95-101
```

使用 `generation: validate`，启动前必须按顺序执行迁移。建议继续保留部署脚本门禁，并在部署阶段明确检查：

- 表结构；
- 存储引擎；
- 唯一约束；
- 迁移版本；
- 快照和目标库一致性。

### 7.2 CI 只验证后端

```text
.github/workflows/build-quarkus-native.yml:15-18
.github/workflows/build-quarkus-native.yml:38-39
.github/workflows/build-quarkus-native.yml:178-183
```

当前 CI 执行 Maven 测试和原生构建，但不执行前端安装、构建或静态契约校验。

**影响**：CI 通过不代表完整产品可交付，也无法捕获前端 roomId、错误 URL、错误 API 域等问题。

**建议**：加入：

```bash
cd frontend
npm ci
npm run build
```

同时明确 `dist/` 到外部 Electron 宿主的交付链。

### 7.3 前端没有 lockfile

```text
frontend/.gitignore:8
```

忽略 `package-lock.json`，而依赖普遍使用 caret 范围。当前不存在前端 lockfile，因此不同时间安装可能产生不同依赖树。

**建议**：取消忽略、生成并提交 lockfile，CI 使用 `npm ci`。

### 7.4 版本号不一致

当前版本标记：

```text
backend/pom.xml:7                         1.1.0
frontend/package.json:2                   0.0.0
backend/src/main/resources/application.yml:1 4.0.1
```

建议使用 Git tag 或单一版本源，发布时同步前后端版本和启动 banner。

### 7.5 CORS 全开放

```text
backend/src/main/resources/application.yml:11-15
```

当前 origins、headers 全开放。虽然项目按内网部署接受该风险，但如果部署范围扩大，会增加跨源调用面。应按真实前端来源设置 allowlist。

### 7.6 生产 OpenAPI 可访问

当前验证：

```text
GET /q/openapi    → HTTP 200
GET /q/swagger-ui → HTTP 404
```

OpenAPI 暴露端点、参数和 DTO 信息。若生产不需要，应关闭或限制到内网/管理员网络。

---

## 8. 已修复或已纠正的历史结论

以下历史问题在当前 HEAD 已确认修复，不应直接按旧报告重复安排：

- 多个事务方法吞异常导致部分提交的问题；
- 多个评分算法漂移问题；
- `lastTrain` 排序方向问题；
- 多个调试写端点；
- 后端房间参数统一到 `ROOM_ID`；
- 文档上传后端假成功问题；
- 分页 page/rows 基础校验问题；
- WebSocket 共享实例状态问题；
- SQL 快照中的部分 MyISAM 问题；
- 读路径懒建的并发双插问题。

以下历史表述已确认错误或不应继续作为确定性缺陷：

1. **GET 使用 data 导致参数丢失**：项目包装器在 `frontend/src/common/http/axios.js:20-24` 已将其转换为 query params。
2. **删除 URL 尾空格必然导致 `%20` 和 404**：真实 Chromium 请求中 URL 被规范化为无尾空格路径，不能继续按必现 404 定级。
3. **`v-per` 找不到权限时放行**：实际权限列表存在时，缺少权限会删除元素；真实问题是权限未加载时 fail-open 和 mounted-only 非响应式。
4. **音频毫秒/采样数问题必然导致时序错误**：当前证据不足，不作为确定性结论。
5. **`node_modules.zip` 已入库，需要重写历史**：当前文件未被 Git 跟踪，不需要历史重写。

---

## 9. 验证记录

### 9.1 后端

执行：

```bash
cd backend
export JAVA_HOME=$HOME/.local/opt/jdk21
./mvnw -B clean verify
```

结果：

```text
BUILD SUCCESS
Tests run: 216
Failures: 0
Errors: 0
Skipped: 0
```

### 9.2 前端

执行：

```bash
cd frontend
npm install --ignore-scripts --no-audit --no-fund
npm run build
```

结果：

```text
built in 56.16s
```

构建成功，但仍有大 chunk，例如：

- `@babylonjs`：约 4.63 MB；
- `qq-wubi`：约 1.03 MB；
- `echarts`：约 807 KB；
- `@antv`：约 787 KB。

### 9.3 前端运行期

真实 Chromium 打开生产预览后：

- 页面成功加载；
- 无 page error；
- 正常进入登录/授权页面。

### 9.4 P0 运行复现

已在隔离数据库中预置用户，并通过匿名 `/api/user/signin` 请求成功修改目标用户身份字段，确认 P0-1 不是静态推断。

### 9.5 URL 尾空格复现

浏览器请求带尾空格的路径后，实际请求 URL 被规范化为无尾空格路径，撤回历史报告中的必然 404 结论。

### 9.6 未完成验证

本轮未执行：

- Native Image 完整业务回归；
- Docker 镜像启动和业务回归；
- ARM64 原生产物功能验证；
- WebSocket 压力测试；
- 生产数据库写路径验证；
- 全量浏览器业务流程回归；
- 外部 Electron 主进程源码审查。

---

## 10. 整改顺序建议

### 第一批：安全边界

1. 修复匿名 `/api/user/signin` 非空 ID 分支；
2. 禁止 API 返回 password、token、deviceId；
3. 给全部写入/删除接口补服务端鉴权；
4. 给管理接口补服务端角色授权；
5. 移除 query token/deviceId；
6. 替换生产 root/root 凭据；
7. 评估并限制生产 OpenAPI。

### 第二批：跨栈硬故障

1. 前端 `roomgId` 改为 `roomId`；
2. 统一 204/202 业务码语义；
3. 对齐文档上传文件类型和错误响应；
4. 修复电传组训错误 API 域；
5. 统一 HTTPS/WSS URL 生成；
6. 修复 `start_time` 读取。

### 第三批：稳定性和纵深防御

1. Axios timeout 和网络异常提示；
2. WebSocket 指数退避、心跳和 readyState 防护；
3. 仿真 WebSocket 异常隔离；
4. 完整销毁 WebSocket singleton；
5. 富文本净化和 CSP；
6. 自动登录改为 refresh token/安全存储；
7. 密码从 MD5 迁移到 Argon2id/bcrypt。

### 第四批：工程交付

1. 提交前端 lockfile；
2. CI 加入前端安装和构建；
3. 统一版本真源；
4. 明确前端 `dist/` 到 Electron 的交付链；
5. 收敛 CORS；
6. 清理 Tauri 残留和死依赖；
7. 收敛重复训练域和状态管理实现。

---

## 11. 最终建议

当前最先处理的不是样式、重复代码或包体积，而是：

1. 匿名跨用户修改漏洞；
2. 有效会话凭据泄露；
3. 无鉴权写入/删除接口；
4. 管理接口缺少服务端授权；
5. 房间详情和电传训练等跨栈功能断裂。

完成第一、第二批后，再进行完整浏览器业务回归和生产形态验证。后端测试全绿应继续保留，但不能作为 API 鉴权、跨栈契约和部署安全已经合格的替代证明。
