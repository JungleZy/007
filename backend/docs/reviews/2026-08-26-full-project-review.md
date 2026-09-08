# 结论：二次复核后共 338 条问题（仍含跨分片重复；确认 P0 去重后 8 条），上轮遗留 13 条问题 0 修复

| 项目 | 内容 |
|---|---|
| 审查对象 | `main` 分支完整源码、配置、Dockerfile、GitHub Actions、数据库 SQL 快照 |
| 技术栈 | Quarkus 3.20.4、Java 21、Hibernate ORM/Panache、MySQL、Jakarta WebSocket（端口 18001）|
| 审查日期 | 2026-08-26 |
| 审查方式 | 汇总 6 份当日静态分片报告，再由 3 个独立 reviewer 二次复核；补做 Java 21 JVM 构建、prod jar 启动和只读 HTTP/数据库冒烟 |
| 审查口径 | 内网部署；纯安全项（认证授权、凭据、匿名访问）为已接受风险，单列附录不计入问题数；P0 仅保留可稳定造成永久数据损坏、跨用户状态错乱、核心功能整体不可用或资源耗尽的缺陷 |
| 上轮遗留 | 2026-08-15 报告的 1×P1 + 12×P2 经静态核对均仍存在；未执行破坏性运行回归 |
| 测试现状 | `src/test` 不存在；`clean verify` 执行 0 个测试，没有仓库内自动化回归防护 |
| 验证边界 | 未调用破坏性写接口，未做 WebSocket 并发压测、Native Image、ARM64 runner 或 Docker 镜像验证；相应运行后果继续标为待验证 |

---

## 1. 二次复核统计与报告索引

| 分片 | 报告 | P0 | P1 | P2 | P3 | 小计 |
|---|---|---|---|---|---|---|
| WebSocket 并发 | [ws-concurrency-review](2026-08-26-ws-concurrency-review.md) | 2 | 13 | 8 | – | 23 |
| 业务服务层 | [service-core-review](2026-08-26-service-core-review.md) | 5 | 77 | 97 | – | 179 |
| API 层（controller/dto）| [controller-api-review](2026-08-26-controller-api-review.md) | 0 | 9 | 19 | 6 | 34 |
| 持久层（dao/entity/SQL）| [persistence-review](2026-08-26-persistence-review.md) | 0 | 11 | 15 | 2 | 28 |
| 基础设施与构建 | [common-build-review](2026-08-26-common-build-review.md) | 0 | 7 | 24 | 4 | 35 |
| 静默失败扫描 | [silent-failures-review](2026-08-26-silent-failures-review.md) | 4 | 23 | 12 | – | 39 |
| **合计（仍含跨分片重复）** | | **11** | **140** | **175** | **12** | **338** |

计数修正分两步：基础设施分片正文实际枚举 `7×P1 + 24×P2 + 4×P3 = 35`，其页首旧统计漏了 8 条 P2，原汇总又漏了 4 条 P3；二次复核另剔除 service-core P1-56——其所谓故障只会在未来单独修改 SQL 日期格式时出现，当前两个命名查询均明确返回 `%Y-%m`。原始 26 条 P0 中，TestPaper、TheoryKnowledge、Menus 三条分别在服务层与静默失败报告重复；改级后原始 P0 为 11 条，精确去重后为 8 条。相同反模式但不同源码位置不合并。

---

## 2. 二次复核确认的 P0（8 条，沿用原编号）

| 原编号 | 位置 | 已确认触发与后果 |
|---|---|---|
| 1 | `service/TestPaperService.java:59-92` | 编辑先删除全部题目；任一未初始化题型列表在 `addAll(null)` 处 NPE，异常被吞后事务提交，题目永久丢失 |
| 2 | `service/TheoryKnowledgeService.java:223-296` | 保存主记录后先删课件，或先删某测验题目；缺失列表触发 NPE 后被吞，分别造成课件全丢或测验残缺 |
| 3 | `service/MenusService.java:101-115` | 先删按钮权限；请求缺 `permissions` 时 NPE 被吞，权限删除照常提交且无异常日志 |
| 4 | `service/TheoryKnowledgeExamService.java:65-93` | 编辑考试无状态保护地删除该考试全部考生行，再重建空答卷；既有答题内容和成绩稳定丢失 |
| 6 | `common/utils/TickerPatUtils.java:283-318`、`service/PostTelegramTrainService.java:508-535` | 四个空 catch 把损坏 JSON 变成形状合法的空数组，外层随后删旧行并写回空化结果，原有效点划轨迹永久丢失 |
| 8 | `ws/WebSocketUnionService.java:39-85` | `@ApplicationScoped` 单例端点共享 `session/sUser`；两连接即可把定向发送和房间身份操作错投到最后连接者 |
| 9 | `ws/WebSocketSimulationService.java:43-96,183-198` | REPORT/RECEPT 房间的 `onClose` 使用单例共享 `userModel`；学员断线可稳定按最后连接的教员身份暂停整房并落库 |
| 22 | `service/PostMilitaryTermTrainService.java:124-189` | 类型恰有 4 条且可达文本无法由 `checkKeyword` 生成唯一合成项时，循环无法凑满 3 个干扰项；单请求永久占用一个工作线程并持续耗 CPU，并发重复调用可耗尽工作线程 |

### 2.1 原 P0 改级

这些缺陷仍计入 338 条总数，但原报告把“条件型严重故障、可回滚失败、局部功能失败或死代码”混进了 P0。

| 原编号 | 改级 | 二次复核结论 |
|---|---|---|
| 5 | P1 | `PostTelexPatTrainService` 在删除前已完成转换；“稳定转换为空”证据不成立。真实风险是 MyISAM 表 delete→重建的不可回滚中断窗口 |
| 7 | P2 | 危险 detached merge 方法全仓无调用；实际 begin/pause/goOn/finish 均走 DAO 定向更新，原称“活跃路径抹空 11 列”是误报 |
| 10 | P1 | 多页时 `parallelStream` 确有裸 `ArrayList` 和共享 DTO 竞态，但只有并行正常返回且已静默丢写时才会删旧写残；单页无竞态 |
| 11 | P1 | 非法 `patLogs` 会少算部分时值扣分，但错误码仍计数并扣分，“损坏数据稳定得满分”不成立 |
| 12 | P1 | 三字段共用 try 的连坐清空只影响长粘连组和部分改错分支；普通组主路径继续复制原字符串 |
| 13 | P1 | 在线查询失败会漏掉本次全班自动结算，但接口可再次提交 `status=2`，学员也可单独 finish；“不可重试、成绩永久丢失”不成立 |
| 14 | P1 | 菜单元数据编辑因自赋值失效，但按钮权限编辑仍执行，不是整个菜单编辑接口完全不可用 |
| 15 | P1 | 角色字段编辑不落库；只有编辑当前默认普通角色才会清空默认配置，且管理员可修复，不是所有角色编辑都令用户永久无法登录 |
| 16 | P1 | 首次或不存在同类型记录时新增稳定失败；已有同类型记录时反而允许创建重复记录，“所有新建 100% 被拒”过宽 |
| 17 | P1 | 两个 `findTwoPage` 确实把 `trainId` 写成主键过滤，但详情仍返回其他字段；GeneralKey 的参数类型错误是抛异常还是空结果需定向运行验证 |
| 18 | P1 | 军语 Excel 导入在空顶级或空子级集合时 NPE；当前 SQL 快照已有种子数据，失败会回滚且不损坏数据 |
| 19 | P1（后果待验证） | 考试快照错误复用源试卷 id 已确认；同一事务 delete 后 merge 同 id 会回滚还是覆盖第一场，需定向 Hibernate 集成验证 |
| 20 | P1 | 只有考试快照题型列为 NULL/空串时 `addAll(null)`；正常空题型由 `TestPaperService` 初始化为 `[]`，并非绝大多数试卷必崩 |
| 21 | P1 | 无统计记录时 `clear()` 的 `save(null)` 会失败，但异常逃出事务且相关表为 InnoDB，之前修改回滚，不会插入空白垃圾行 |
| 23 | P1（后果待验证） | 生产源码确有 `GET /api/test/start` 和四次硬编码 UPDATE；当前 SQL 快照无目标行，实际库命中需核实。风险不依赖 Swagger UI |

---

## 3. 系统性根因

1. **事务内吞异常 + 先删后写**：确认 P0 #1-3 的共同根因。`catch` 正常返回后事务不会因业务 NPE 自动回滚；P0 #6 则是在工具层先吞解析异常，再把空化结果交给外层正常提交。
2. **WebSocket 单例保存连接状态**：P0 #8/#9 均由 `@ApplicationScoped` 端点把 `Session`/用户模型放在实例字段引起。`SimulationGlobal` 的裸 `ArrayList`、非原子 get→改→put 和成员泄漏是同一状态模型下的 P1。
3. **`UserService.getUserByToken` 返回 null**：过期 token 继续在大量调用点触发 NPE，但二次复核后的 P0 #1-3 不需要依赖该认证触发即可成立。
4. **异常可观测性不足**：全仓无 `ExceptionMapper`；controller 目录有 63 个 Controller，其中 52 个标 `@JWT`，这些类在 `JWTInterceptor` catch 分支丢失异常对象；多个 WS 端点无有效 `@OnError`，大量日志只写 `e.getMessage()`。
5. **显式错误响应仍用 HTTP 200**：运行验证确认缺 token 时 HTTP 200、响应体业务码 203。只有显式 `ResponseResult.error` 路径如此；未捕获异常仍可 500，void 端点可 204。依赖 HTTP 状态的客户端和网关无法识别这类失败。
6. **复制粘贴漂移**：`findTwoPage` 的字段错误已扩散两处；速率加减分、平均速率、页码校验和先删后插均存在多套互相矛盾实现。
7. **Schema 与实体脱节且生产启动不阻断**：同一环境的 Hibernate post-boot validation 日志 `logs/info.log.1` 已确认 5 张实体表缺失和大量列差异；prod jar 仍能启动并访问未受影响的表，说明 `generation: none` 不会替部署阻断这类漂移。22 张 MyISAM 表又使部分事务无法回滚。
8. **评分核心实现漂移**：TickerPatUtils 的列对调、列表错位、过滤不同步和 groupScore 覆盖均是确定 P1；原报告把部分条件型少扣分夸大成“稳定满分”。
9. **原生构建、发布和容器文档有确定缺陷，但并非“CI 全断”**：release job 因 workflow 不监听 tag 而不可达；ARM64 命令含 x86 专用 `-march`，预期失败但需真实 ARM runner 确认；release 恢复后两个 Linux 产物内部同名会在平铺合并时冲突。四个 Dockerfile 示例映射容器 8080，而应用监听 18001；`EXPOSE` 只是元数据，显式映射容器 18001 仍可用。

---

## 4. 构建与运行验证

| 验证项 | 实际结果 | 结论边界 |
|---|---|---|
| `JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify` | **通过**，27.858 秒；Java 21.0.12.1 编译 746 个源文件，Quarkus augmentation 成功 | 证明 JVM 编译与打包；`src/test` 不存在，单元测试 0 个，`skipITs=true` 使集成测试跳过 |
| prod jar 独立启动 | 使用 `-Dquarkus.http.port=18002`，2.294 秒启动，Quarkus profile=`prod` | 证明 JVM 生产包可启动；不证明 Native Image、Docker 或全部业务路径 |
| 只读数据库接口 | `POST /api/cable/type/find` 返回业务码 200 和 3 条实际数据 | 证明 HTTP→Controller→Hibernate→当前 MySQL 的基本读链路可用 |
| OpenAPI / Swagger | `/q/openapi` HTTP 200；`/q/swagger-ui` HTTP 404 | OpenAPI schema 在生产可用；原报告“Swagger UI 生产开启”是误报 |
| 错误响应 | 缺 token 请求返回 HTTP 200、响应体 `code=203` | 确认显式业务错误不使用 HTTP 错误状态，但不能外推为所有响应恒 200 |
| 关键依赖树 | `commons-beanutils:1.9.4 -> commons-collections:3.2.2` | 隐式依赖成立；但 BeanUtils 1.11.0 官方 POM 仍传递 3.2.2，单纯升级不会导致编译失败。只有删除/排除 BeanUtils 前未替换 import 或显式声明时才会失败 |
| Schema 运行日志 | `logs/info.log.1` 报 5 张缺表及大量列差异 | 持久层 P1-8 从静态条件判断升级为当前环境运行证据；prod 启动未因此失败 |

构建另报告两个未展开的 javac 告警：`common/utils/StringUtils.java` 使用弃用 API，`common/specification/SpecificationExecutor.java` 有 unchecked/unsafe 操作。它们不影响本次构建结论，但应在后续清理时开启对应 `-Xlint` 精确定位。

未执行会修改业务数据的 P0/P1 触发请求，也未做 WebSocket 多连接竞态、MySQL 特定 HQL/SQL、Native Image、ARM64 GitHub runner 和 Docker 镜像验证。相关条目只能保留源码已确认的缺陷，并把具体运行后果标为待验证，不能用 JVM `clean verify` 或普通启动替代。

---

## 5. 上轮遗留问题核销

2026-08-15 报告实际编号为 1×P1 + 12×P2（旧表头写“11 条 P2”是计数错误）。二次静态核对显示 13/13 对应代码或配置仍存在，修复率 0%；这是静态核销，不代表 13 条均已做运行回归。新增确认：P2-05 的“把 trainId 当主键查”已从 `GeneralKeyPatPageDao` 扩散到 `GeneralTelexPatPageDao.java:22-24`。

---

## 6. 建议修复顺序

1. **先止血确认 P0 #1-4、#6**：新数据完整校验成功后再删除旧数据；删除吞异常 catch 或重抛，确保事务回滚；为五条路径各补失败回滚测试。
2. **修 WebSocket P0 #8/#9**：端点改为每连接实例，或把连接状态放入以 session 为 key 的 holder；随后处理 P1 #10 的两处 `parallelStream` 和房间裸集合。
3. **终止 P0 #22 的无界循环**：修正三个 `nextInt(size - 1)`，候选池先去重并校验至少 4 项，循环增加最大重试次数。
4. **统一评分实现**：先修 TickerPatUtils 的列对调、错位、过滤不同步和覆盖累加，再把速率/平均速率/正确率收敛到单一实现。
5. **补全异常边界**：增加 `ExceptionMapper`；token 查询失败抛明确异常；JWT/WS/业务日志保留 Throwable；显式错误使用正确 HTTP 状态。
6. **修交付链路**：增加 tag 触发，按架构拆分 native 参数和产物名；统一应用、Dockerfile 和示例端口；删除 BeanUtils 死调用前先替换对 commons-collections 的直接 import。

每批修复前先补该路径的回归测试；当前 `clean verify` 执行 0 个测试，任何重构都没有仓库内自动告警。

---

## 附录：已接受安全风险（内网口径，不计入问题数）

沿用上轮口径：匿名/无鉴权接口、root/root 数据库账户、CORS 全开、密码与 token 算法强度、用户查询返回敏感字段、生产暴露 OpenAPI schema、依赖 CVE（CVE-2025-48734 beanutils、CVE-2023-24163/CVE-2023-42278 hutool——经核对在本项目当前调用方式下均不可触发；fastjson 属性为死配置无实际依赖；commons-codec 1.13 无已知 CVE）。Swagger UI 在本次 prod jar 验证中返回 404，不再列为已暴露风险；破坏性测试端点按功能后果计入原 P0 #23，二次复核后改为 P1（实际库是否存在硬编码目标行待验证）。
