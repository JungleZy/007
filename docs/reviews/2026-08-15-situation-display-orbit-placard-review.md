# 结论：按内网部署口径，安全项不计入问题；当前仍有 1 类 P1 数据完整性问题

## 当前项目代码审查报告

| 项目 | 内容 |
|---|---|
| 审查对象 | 当前 `main` 分支完整项目源码、运行配置、Dockerfile 与 GitHub Actions |
| 技术栈 | Quarkus 3.20.4、Java 21、Hibernate ORM/Panache、MySQL、Jakarta WebSocket |
| 审查日期 | 2026-08-26 |
| 部署口径 | 内网部署；认证授权、凭据、个人信息和匿名访问等安全项不作为本次问题 |
| 工作区状态 | 无未提交改动 |
| 当前结论 | 保留 1 类 P1 数据完整性问题和 11 类 P2 功能、并发、构建及部署问题 |
| 构建验证 | 未通过环境验证：当前工作站没有 Java，且 `mvnw` 没有执行权限 |

---

## 1. 审查口径调整

根据项目部署约束，本报告不再把安全性问题作为上线阻断项。

原报告中的以下内容已从问题清单移除：

| 原编号 | 原问题 | 当前处理 |
|---|---|---|
| P1-01 | 仓库包含账号、个人信息和会话数据 | 内网部署口径下忽略 |
| P1-02 | 匿名 `signin` 可更新现有用户 | 内网部署口径下忽略 |
| P1-03 | 缺少角色和资源授权 | 内网部署口径下忽略 |
| P1-04 | 用户查询返回密码、Token 和个人信息 | 内网部署口径下忽略 |
| P1-05 | Token 和密码算法安全性不足 | 内网部署口径下忽略 |
| P1-06 | REST 写接口没有认证 | 内网部署口径下忽略 |
| P1-07 | WebSocket 没有身份和消息权限校验 | 内网部署口径下忽略 |
| 原 P2-07 | 匿名训练统计查询 | 纯安全项，移除 |
| 原 P2-13 | 默认数据库账户 `root/root` | 纯安全项，移除 |

这些内容在本报告中视为项目已明确接受的部署风险，不要求本轮整改，也不计入剩余问题数量。

---

## 2. 执行摘要

排除安全项后，当前项目仍存在以下影响实际运行的缺陷：

1. 两条多页评分路径并行修改非线程安全集合，随后删除原始记录并写回可能残缺的结果，存在永久数据损坏风险。
2. WebSocket 端点使用应用级单例保存连接状态，多连接时会串 Session、错发消息或清理错误用户。
3. 房间成员列表和首次建房流程并非并发安全，并发连接会被覆盖或漏收广播。
4. “最后一次训练”实际查询最旧记录，新增训练会处理错误的历史状态。
5. 军语批量导入存在部分提交、空值异常和提前退出三个确定性缺陷。
6. 多处训练页查询、页码边界和跳页生成逻辑会返回或写入错误页面。
7. Release job 不会被 tag 触发，ARM64 构建参数错误，多架构产物还会同名覆盖。
8. Dockerfile 对外声明 8080，但应用实际监听 18001，按仓库说明启动的容器不可访问。
9. 项目没有 `src/test`，关键评分、事务、页码、并发和发布契约均无自动化回归保护。

建议优先处理数据损坏和事务问题，再处理 WebSocket 并发、业务查询和构建发布链路。

---

## 3. 审查范围与严重度

### 3.1 审查范围

- `src/main/java/com/nip/service`
- `src/main/java/com/nip/dao`
- `src/main/java/com/nip/ws`
- `src/main/java/com/nip/common`
- `src/main/resources/application.yml`
- `src/main/docker`
- `.github/workflows`
- `pom.xml`

### 3.2 本版审查重点

- 多线程评分和删除后重建流程
- 事务原子性与批量写入
- WebSocket 连接生命周期和并发状态
- 训练记录排序、页面关联和页码边界
- Native Image 构建、Release 发布和容器运行端口

### 3.3 严重度定义

| 严重度 | 定义 |
|---|---|
| P1 | 可造成持久化业务数据损坏或不可恢复的数据丢失 |
| P2 | 在现实输入、并发或标准部署流程下产生稳定错误、部分提交、错误结果或构建发布失败 |

---

## 4. P1 问题

### P1-01 多页评分并行修改共享状态，删除原始数据后可能写回残缺结果

> 原报告编号：P1-08。安全项移除后重新编号为 P1-01。

**位置：**

- `src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java:342-361`
- `src/main/java/com/nip/service/general/GeneralKeyPatService.java:641-661`

**触发条件：**

一次训练存在至少两页已提交数据，`parallelStream()` 把不同页面分配到多个工作线程。

**问题：**

两个评分流程都让多个线程同时执行：

- 向普通 `ArrayList` 调用 `addAll`；
- 修改同一个统计 DTO；
- 在并行工作线程中执行 DAO 查询。

处理结束后，代码会先删除原有 page value/resolver 数据，再保存共享集合中的计算结果：

```text
并行读取和计算
  → 多线程写共享 ArrayList/统计 DTO
  → 删除原始 page value/resolver
  → 保存可能缺页或统计错误的新结果
```

**实际影响：**

- 多页训练结果随机漏页或统计错误。
- PostTelegraph 路径可能永久丢失用户页数据。
- GeneralKey 路径可能同时覆盖 resolver 和 user value 数据。
- 原始记录已经删除，错误结果难以恢复。

**修复要求：**

1. 先改为普通顺序循环，保证 DAO 和累加器只在请求线程使用。
2. 如果确需并行，每页必须返回独立、不可变的计算结果，由主线程确定性归并。
3. 删除旧记录和批量保存必须在同一事务内完成。
4. 删除前校验结果页数、元素数量和训练 ID 是否完整。
5. 增加至少包含 2 页、10 页和并发完成请求的回归测试。

---

## 5. P2 问题

### P2-01 `@ApplicationScoped` WebSocket 共享连接实例状态

**位置：**

- `src/main/java/com/nip/ws/WebSocketUnionService.java:39-59`
- `src/main/java/com/nip/ws/StatusWebSocket.java:12-40`
- `src/main/java/com/nip/ws/WebSocketSimulationService.java:43-56`

Quarkus Classic WebSocket 的 CDI `@ApplicationScoped` 是应用级单例，但这些类把 `Session` 和当前用户保存在实例字段。第二个连接会覆盖第一个连接的实例状态。

**触发时序：**

1. 用户 A 建立连接，端点字段保存 A 的 Session。
2. 用户 B 建立连接，同一个应用级实例把字段覆盖为 B。
3. A 发送消息或断开连接时，代码读取到 B 的 Session 或用户状态。

**结果：**

- 消息发送给错误连接。
- 用户 A 断线时可能清理用户 B。
- 在线用户状态和房间成员状态不一致。
- 联合训练无法可靠支持多个并发连接。

**修复：**

端点保持无状态或使用每连接实例；共享注册表移入独立 `@ApplicationScoped` 服务，并以 `Session.getId()` 映射不可变连接记录。

---

### P2-02 房间 Map 的 value 和首次建房流程不是并发安全操作

**位置：**

- `src/main/java/com/nip/ws/service/simulation/SimulationGlobal.java:9-18`
- `src/main/java/com/nip/ws/WebSocketSimulationService.java:99-177`
- `src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java:40-49`
- `src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java:60-70`
- `src/main/resources/application.yml:39-40`

Map 使用了并发集合，但 value 是普通 `ArrayList`，首次建房采用非原子的 `get → create → put`。

**触发时序：**

1. 空房间中 A、B 同时连接。
2. 两个线程都读取到 null，并各自创建房间对象。
3. 两次 `put` 后只保留最后一个对象，另一个连接从房间注册表消失。

已有房间中，广播遍历与 add/remove 并发执行还可能产生 `ConcurrentModificationException` 或漏发。

**修复：**

- 使用 `computeIfAbsent` 原子创建房间。
- 成员按 Session/User 存入 `ConcurrentHashMap`，或让全部成员操作遵循同一个房间锁。
- 广播基于一致的成员快照执行。

---

### P2-03 “最后一次训练”实际返回最旧记录

**位置：**

- `src/main/java/com/nip/dao/TickerTapeTrainDao.java:102-110`
- `src/main/java/com/nip/service/TickerTapeTrainService.java:72-84`

DAO 按 `createTime ascending` 排序后取第一条。新增训练时，服务处理的是最旧记录，而不是最新暂停或未开始记录。

**触发条件：**

同一用户、同一类型已经存在至少两条历史训练，最新一条为未开始或暂停状态，再次创建训练。

**结果：**

- 最新暂停训练没有被完成和统计。
- 最新未开始训练没有被删除。
- 同一类型可能同时保留多条活动或未完成记录。
- “最后一次训练”接口返回错误记录。

**修复：**

改为 `createTime descending`，并增加稳定的第二排序键，例如主键或创建序号。

---

### P2-04 军语批量导入会部分提交、首次导入 500 或提前结束

**位置：**

- `src/main/java/com/nip/service/MilitaryTermDataService.java:202-239`
- `src/main/java/com/nip/dao/MilitaryTermDataDao.java:29-32`

同一批量流程存在三个缺陷：

1. 外部 `saveBatch` 没有事务，同类调用 `@Transactional excelHanle()` 不经过 CDI 拦截器；后续失败时前面的记录已经提交。
2. 空分类的 `MAX(sort)` 返回 null，代码直接执行 `maxSort + 1`。
3. 创建新父类后使用 `return` 退出整个循环，后续 DTO 被静默忽略，但接口仍返回成功。

**修复：**

- 把事务放在对外 `saveBatch` 方法。
- 写入前一次性校验整个列表。
- `MAX(sort)` 为 null 时使用初始排序值。
- 把提前 `return` 改为继续处理下一项。
- 测试空库首次导入、新父类、多项中途失败和整批回滚。

---

### P2-05 前两页查询把训练 ID 当成页面主键

**位置：**

- `src/main/java/com/nip/dao/general/key/GeneralKeyPatPageDao.java:11-27`
- `src/main/java/com/nip/entity/simulation/key/GeneralKeyPatPageEntity.java:20-45`

查询条件使用页面 UUID 主键 `id = Integer trainId`，正确关联字段应为 `trainId`。DAO 的 Repository ID 泛型也与实体 String 主键不一致。

**结果：**

- 训练详情无法返回目标训练的第 1、2 页。
- Hibernate 可能因 String/Integer 参数类型不匹配而报错。

**修复：**

把条件改为 `trainId = ?1`，并将 Repository ID 泛型改为 String。

---

### P2-06 页码 0 被持久化为真实训练页

**位置：**

- `src/main/java/com/nip/service/PostTelexPatTrainService.java:228-250`
- `src/main/java/com/nip/service/PostTelexPatTrainService.java:1339-1405`

当前校验只拒绝 `pageNumber < 0`，因此 0 会继续进入查询和生成流程。数据库通常没有第 0 页，服务会生成内容并把 `pageNumber=0` 写入真实训练表。

**结果：**

- 第 0 页进入训练数据。
- 页数统计和评分逻辑受到污染。

**修复：**

在任何 DAO 查询或生成前统一验证：

```text
pageNumber != null && pageNumber >= 1 && pageNumber <= totalPage
```

---

### P2-07 跳页请求写入和返回错误页面

**位置：**

- `src/main/java/com/nip/service/PostTelegramTrainService.java:385-433`

创建训练时只预生成第 1、2 页。客户端首次直接请求第 4 页时，服务按请求页计算内容，却读取最后已生成页 2，并把新内容编号为第 3 页，然后作为“第 4 页”响应返回。

**结果：**

- 第一次跳页请求稳定返回错误页。
- 数据库写入的页号与请求页不一致。
- 重复请求会逐页追赶，行为依赖历史请求顺序。

**修复：**

生成内容必须使用请求页编号；如果业务要求连续生成，应明确补齐 `lastPage + 1` 到 requestedPage，并最终只查询和返回 requestedPage。

---

### P2-08 联合训练消息解析丢失发送者字段

**位置：**

- `src/main/java/com/nip/ws/WebSocketUnionService.java:103-112`

代码连续两次调用 `setReceiveUser()`：

```java
msg.setReceiveUser(map.get("sendUser"));
msg.setReceiveUser(map.get("receiveUser"));
```

`sendUser` 从未赋值。依赖发送者的房间消息、角色更新和席位检测会得到 null。

**结果：**

- 房间消息稳定空指针或广播错误用户。
- 用户角色更新和席位检测使用错误参数。

**修复：**

修正字段赋值，并增加消息模型反序列化测试及各 code 分支的行为测试。

---

### P2-09 Release job 永远不会被 tag 触发

**位置：**

- `.github/workflows/build-quarkus-native.yml:3-7`
- `.github/workflows/build-quarkus-native.yml:84-88`

`on.push` 只配置 `branches`，没有配置 `tags`。只定义 branch 过滤器时，tag push 不会运行该 workflow，因此 Release job 的 `refs/tags/*` 条件不可达。

**结果：**

推送发布标签不会创建 GitHub Release，也不会发布 Native Image 产物。

**修复：**

为发布标签增加明确的 `tags` 触发器，并保留分支构建触发器。

---

### P2-10 ARM64 构建传入 x86_64 专用机器参数

**位置：**

- `.github/workflows/build-quarkus-native.yml:18-20`
- `.github/workflows/build-quarkus-native.yml:48-50`

ARM64 matrix 执行统一 Ubuntu 构建步骤，并收到 `-march=x86-64`。命令还同时设置 `quarkus.native.march=compatibility` 和额外 `-march=x86-64`，配置互相冲突。

**结果：**

- ARM64 Native Image 无法正确产出。
- `needs: [build]` 会连带阻断 Release job。

**修复：**

按架构拆分构建步骤，只通过一个 `quarkus.native.march` 配置机器类型。

---

### P2-11 多架构发布产物会同名覆盖

**位置：**

- `.github/workflows/build-quarkus-native.yml:90-99`
- `pom.xml:5-7`

`download-artifact` 使用 `merge-multiple: true` 把不同架构产物平铺到同一目录，而 Maven 最终文件名不包含架构。两个 Linux runner 会相互覆盖。

**结果：**

Release 中缺失一个 Linux 架构，或把错误架构的二进制交付给目标机器。

**修复：**

保留各 artifact 子目录，或在上传前给 runner 添加 OS/架构后缀。

---

### P2-12 Docker 公开端口与应用监听端口不一致

**位置：**

- `src/main/resources/application.yml:8-10`
- `src/main/docker/Dockerfile.jvm:14,91-94`
- `src/main/docker/Dockerfile.legacy-jar:14,88-91`
- `src/main/docker/Dockerfile.native:14,24-27`
- `src/main/docker/Dockerfile.native-micro:17,27-30`

应用监听 18001，但四个 Dockerfile 都 `EXPOSE 8080`，示例命令也使用 `-p 8080:8080`。入口只覆盖 host，没有覆盖 port。

**结果：**

按仓库说明启动后，映射的容器 8080 端口没有服务监听。

**修复：**

统一应用配置、`EXPOSE`、容器映射和健康检查使用 18001，或统一把应用监听端口改为 8080。

---

## 6. 测试与验证状态

### 6.1 已执行检查

| 检查 | 结果 |
|---|---|
| Git 工作区 | 干净，无待审查的未提交 diff |
| 测试源码 | 不存在 `src/test` |
| `./mvnw -B test` | 失败：`mvnw` 没有执行权限 |
| `bash mvnw -B test` | 失败：环境没有正确的 `JAVA_HOME` |
| `java -version` | 失败：当前工作站不存在 `java` 命令 |

### 6.2 结论边界

本报告中的源码调用链和配置问题均已通过静态审查核实，但没有获得成功的编译、测试或运行时验证结果。因此：

- 不能声明当前项目可以编译或测试通过。
- 不能排除编译后才出现的其他 Quarkus、Hibernate 或 Native Image 问题。
- 当前 `controller/test` 是生产源码中的运行时测试端点，不是自动化测试。

---

## 7. 修复顺序

### 第一优先级：数据完整性

- 移除评分流程中的共享并行状态。
- 确保删除旧数据与保存新数据位于同一事务。
- 增加评分结果完整性校验和多页回归测试。

### 第二优先级：批量事务与查询正确性

- 修复军语批量导入事务、空值和提前退出。
- 修复最后训练排序、页面关联、页码边界和跳页生成。
- 为活动训练和训练页增加必要的唯一约束。

### 第三优先级：WebSocket 生命周期与并发

- 将连接状态与 application-scoped 服务分离。
- 使用原子房间创建和线程安全成员注册表。
- 断线清理按实际 Session 执行。
- 修复联合训练消息模型字段赋值。

### 第四优先级：构建与部署

- 修复 tag 触发、ARM64 参数和产物命名。
- 统一应用与容器端口。
- 配置 Java 21，并修复 `mvnw` 执行权限。

### 第五优先级：回归测试

- 增加多页评分、批量回滚、最后训练、页码边界和跳页测试。
- 增加双连接、并发建房、重连和断线清理测试。
- 增加 JVM 构建、Native Image 矩阵构建和容器连通性检查。

---

## 8. 最低上线门槛

以下条件全部满足前，不应判定功能和部署链路稳定：

- [ ] 多页评分不再并行修改共享集合。
- [ ] 删除原始评分数据前能够验证新结果完整。
- [ ] 批量导入具备整批提交或整批回滚语义。
- [ ] 最后训练、页面关联、页码边界和跳页行为正确。
- [ ] WebSocket 多连接之间不共享可变 Session/用户字段。
- [ ] 房间创建、加入、退出和广播具备一致的并发模型。
- [ ] Release 能被 tag 触发，ARM64 和 x86_64 均能产出独立文件。
- [ ] Docker 映射端口与应用实际监听端口一致。
- [ ] JVM 测试、Native Image 构建和容器连通性验证全部通过。
- [ ] 关键业务契约已有自动化回归测试。
