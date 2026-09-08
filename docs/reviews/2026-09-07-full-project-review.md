# 结论：去重后确认 P0 0 条；P1 34 / P2 139 / P3 53（分片原始合计 228 条，跨分片重复与改级共 5 处已处理）。上一轮 8 条确认 P0 全部修复且均有回归测试锁定；当前最高风险是「`@Transactional` 内 catch 吞异常导致部分提交」与「MyISAM 表 delete→重建的中断窗口」

| 项目 | 内容 |
|---|---|
| 审查对象 | `main` 分支 HEAD `b9b9f22` 全量源码（main 750 个源文件 / test 39 个）、`application.yml`、`pom.xml`、4 个 Dockerfile、GitHub Actions、SQL 快照与迁移脚本、`src/test` 全部测试 |
| 技术栈 | Quarkus 3.20.4、Java 21（Temurin 21.0.12.1）、Hibernate ORM/Panache、MySQL 8.0.26、Jakarta WebSocket；HTTP 18001，REST 前缀 `/api` |
| 审查日期 | 2026-09-07 |
| 审查方式 | 11 个并行只读评审代理分片静态取证（WS 并发／拍发评分服务／通用报底服务／理论考试与用户权限／推演与其余服务／API 层／持久层／基础设施与配置／构建交付／测试套件质量／上一轮整改核销），主代理负责去重、改级、运行验证与汇总 |
| 运行验证 | 主代理实测：`clean verify` 全绿（129 测试）、prod jar 两种 schema 策略启动、只读 HTTP 冒烟、WebSocket 三组双连接探针、活库表引擎与目标行核对（详见 §5） |
| 审查口径 | 内网部署；纯安全项（鉴权缺口、凭据、CORS、敏感字段、CVE）为已接受风险，列入附录不计入问题数；P0 仅保留可稳定造成永久数据损坏、跨用户状态错乱、核心功能整体不可用或资源耗尽的缺陷 |
| 上一轮基线 | `docs/reviews/2026-08-26-full-project-review.md`（338 条，确认 P0 8 条）与 `2026-08-26-review-audit.md`；整改记录 `2026-08-28-fix-spec-remediation.md` |
| 验证边界 | 未做 Native Image、Docker 镜像、ARM64 runner、WebSocket 压测与生产库写路径验证；未执行会破坏业务数据的 P1 触发请求（唯一例外见 §5 关于 `GET /api/test/start` 的说明） |
| 独立审计 | 本文已由 5 个独立审计代理逐条复核 93 项断言（确认 84 / 部分成立 9 / 误报 0），9 处表述已回改并标注「审计修正」；审计报告见 [review-audit](2026-09-07-review-audit.md) |

---

## 1. 分片索引与计数

| 分片 | 报告 | P0 | P1 | P2 | P3 | 小计 |
|---|---|---|---|---|---|---|
| WebSocket 并发 | [ws-concurrency-review](2026-09-07-ws-concurrency-review.md) | 0 | 0 | 7 | 3 | 10 |
| 拍发/评分服务 | [post-train-service-review](2026-09-07-post-train-service-review.md) | 0 | 10 | 34 | 6 | 50 |
| 通用报底/键控服务 | [general-pat-service-review](2026-09-07-general-pat-service-review.md) | 0 | 3 | 11 | 5 | 19 |
| 理论考试与用户权限 | [theory-user-service-review](2026-09-07-theory-user-service-review.md) | 0 | 2 | 8 | 3 | 13 |
| 推演/仿真与其余服务 | [simulation-misc-service-review](2026-09-07-simulation-misc-service-review.md) | 0 | 9 | 9 | 7 | 25 |
| API 层（controller/dto）| [controller-api-review](2026-09-07-controller-api-review.md) | 1 | 7 | 23 | 8 | 39 |
| 持久层（dao/entity/SQL）| [persistence-review](2026-09-07-persistence-review.md) | 0 | 2 | 21 | 2 | 25 |
| 基础设施与配置 | [common-infra-review](2026-09-07-common-infra-review.md) | 0 | 1 | 19 | 10 | 30 |
| 构建与交付 | [build-delivery-review](2026-09-07-build-delivery-review.md) | 0 | 0 | 4 | 5 | 9 |
| 测试套件质量 | [test-suite-review](2026-09-07-test-suite-review.md) | 0 | 0 | 4 | 4 | 8 |
| **分片原始合计** | | **1** | **34** | **140** | **53** | **228** |
| **本汇总去重/改级后** | | **0** | **34** | **139** | **53** | **226** |

第 11 份 [remediation-verification](2026-09-07-remediation-verification.md) 是整改核销专片，只判定「声称修了的是否真修了」，不产生新问题条目，不计入上表。

各分片自带 §5 上一轮核销表，逐行给出当前 file:line；本汇总不重算分片 §5 的逐行判定，跨轮核销以整改核销专片为准（§6）。

---

## 2. 去重与改级（5 处）

| 处理 | 条目 | 依据 |
|---|---|---|
| **P0 → P1** | `CA-P0-01` `GET /api/test/start`（`controller/test/TestController.java:18,28-46`）| 端点确实无鉴权且连发 4 次硬编码 UPDATE，但两条 P0 前提被运行验证否证：目标行 `02bfee8b-a01f-479f-a1a7-1d081734c952` 在当前库**不存在**（实测 `count(*)=0`，本次调用 0 行受影响）；`t_ticker_tape_train` 是 **InnoDB** 而非 MyISAM，不存在「不可回滚」。沿用上一轮改级 #23 的 P1 定级，后果按「目标行存在时无鉴权改写训练状态」表述 |
| **P1 → P2** | `SM-P1-05` `CableService.save:51-80` 编辑时 `floors` 为 null | 分片以「`cable_floor` 为 MyISAM，无回滚 → 永久丢失」定级 P1；实测活库 `t_cable`/`t_cable_floor`/`t_cable_type` **全为 InnoDB**，NPE 逸出后事务回滚，无永久丢失。缺陷降为「编辑接口对缺字段请求 500」的健壮性问题 |
| **合并计一条** | `CI-P1-01`（`common/utils/ToolUtil.java:96-97` 守卫判首参却以第三参为除数）与 `GP-P2-02`（缺陷调用点 `GeneralTickerPatService.java:737`）| 同一根因的方法体与调用点，按根因计入 P1，调用点不重复计数。注意影响面：三参版在该类共 13 处调用（:728-737、742、747、752），只有 :737 首参 ≠ 分子/分母因而出错 |
| **合并计一条** | `PT-P1-02`（`PostTelegramTrainService.java:830-844` 覆盖硬编码 trainId）与 `CA-P2-12`（同一 `GET /postTelegramTrain/test` 端点的暴露与 `@Operation` 描述错）| 同一端点的服务实现与端点暴露，按 P1 计一条 |
| **保留为独立条目** | `PT-P1-09`/`PT-P1-10`（两处 delete→saveAndFlush 窗口）与 `PS-P1-02`（MyISAM 表整体不可回滚）| 前两条是具体源码位置，后一条是 schema/部署层根因；沿用「相同反模式不同位置不合并」的口径 |

`PT-P1-09`/`PT-P1-10` 的「待验证表引擎」已由运行验证消除：`t_post_telex_pat_train_page`、`t_post_telex_pat_train_page_value`、`t_post_telegraph_key_pat_train_page`、`t_post_telegraph_key_pat_train_page_value` **实测均为 MyISAM**，两处中断窗口的永久丢失后果成立。

---

## 3. P1 全表（34 条）

事务与数据完整性（吞异常导致部分提交 / 不可回滚窗口）：

| 编号 | 位置 | 触发 → 后果 |
|---|---|---|
| PT-P1-01 | `service/TelegramTrainService.java:264-310`（catch 307-308）| 请求缺 `trainFloors` 等导致写入中 NPE → `catch(Exception){return error()}` 既不重抛也不 `setRollbackOnly`，事务提交：上一次暂停训练已被置 status=3 并计入统计、新训练头与半数楼层已写、且零日志 |
| SM-P1-01 | `service/CableService.java:82-91` | `deleteById` 抛 RuntimeException → catch 吞掉，楼层已删、报文头残留成空报文，前端因 `return false` 以为未删 |
| SM-P1-02 | `service/CableTypeService.java:47-59` | 三连删中途失败被吞 → 已删部分提交，类型/报文/楼层成孤儿 |
| SM-P1-03 | `service/TelexPatService.java:95-112` | 有 `t_telex_pat` 数据但无统计行 → :101 NPE 被吞，删除照常提交却返回 error 误导前端（重试恒复现）。审计修正：触发条件本身就是「无统计行」，因此不存在「统计陈旧」，真实后果是提交与返回码矛盾 |
| PT-P1-09 | `service/PostTelexPatTrainService.java:796-819` | `deleteByTrainId(818)` → `saveAndFlush(819)` 之间中断；两张 page/value 表实测 MyISAM → 报底与回写值永久丢失 |
| PT-P1-10 | `service/PostTelegraphKeyPatTrainService.java:333-482`（371-372）| 同上模式，`t_post_telegraph_key_pat_train_page_value` 实测 MyISAM → 拍发记录永久丢失 |
| PS-P1-02 | `docs/database/project006.sql` 22 张 `ENGINE=MyISAM` + `application.yml:88` | 部署未执行迁移 02 时，`rollbackOn=Exception` 给出虚假安全感：删训练主记录失败→报底/答卷已消失；新建报底失败→残留无报底训练 |

评分与业务正确性：

| 编号 | 位置 | 触发 → 后果 |
|---|---|---|
| GP-P1-03 | `service/general/GeneralTickerPatService.java:939-943` | `wpm=base-speed`，`speed≥base` 时 `wpm*R ≤ 0` → 快于基准反而扣分；对照 `GeneralKeyPatService.java:814-818` 为加分，三种拍发训练速率口径相反且成绩落库 |
| GP-P1-02 | `service/general/GeneralTickerPatService.java:294-303` | 报文数 >200 翻页再生成时以 `getIsAverage()==0` 判「均匀」，与入库口径相反 → 同一训练前后页分布策略相反 |
| CI-P1-01 | `common/utils/ToolUtil.java:96-97`（缺陷调用点仅 `GeneralTickerPatService.java:737`）| 三参 `calculateRate(guard, numerator, denominator)` 的守卫判首参却以第三参为除数；:737 首参误传 `groupGapMin` 而分子是 `groupGapMax` → `groupGapMin==0 && groupGapMax>0` 时「组间隔粗」比率被短路成 0%。审计修正：该值经 `statisticsScoreAndDotLineGapRate` 组装 VO 直接返回（**报表展示错误，非落库**）；同方法在该类另有 12 处调用（:728-736、742、747、752）首参等于分子或分母，行为正确。正确实现见 `PatTrainStatisticsUtil.java:71-74` |
| PT-P1-07 | `service/MessageComparisonService.java:205-212` | 多组命中后 `return currentIndex` 未加 `skipCount` → 多余组被再次当普通组比对，本页后半段对齐错位、错码/点划重复累加，错误分数落库 |
| PT-P1-08 | `service/detector/LineDetector.java:182-253` | 多行/少行检测成功时不写 `addCorrectMessage`，当前 patKey 日志/点划/耗时丢失；多行分支对后 9 组重复统计 → 点粗/点虚/间隔翻倍 |
| PT-P1-03 | `service/PostTelegramTrainService.java:795-821`（803）| `floorNumber+=i` 累加而非递增 → 追加 ≥2 页时楼层号重叠并留空洞 |
| PS-P1-01 | `dao/TickerTapeTrainDao.java:109-111` | `lastTrain` 用 `createTime ascending` 取到**最早**一条（同族 `EnteringExerciseDao:56`/`TelegramTrainDao:56` 均为 desc）→ `TickerTapeTrainService:73/77/79/226` 误删或误结算最早记录、断点续训返回最早训练 |
| TU-P1-01 | `service/TheoryKnowledgeExamService.java:242-250` | 自测路径未 `setId(null)`，快照复用源试卷主键 → 第二次自测 merge 覆盖同一行，把前一场 examId 改写为后一场，前一场取卷/分析在 `:323,326` NPE（主路径 `:86` 已修，自测路径残留）|
| TU-P1-02 | `service/RoleService.java:63-65`（级联 :56-62）| 经 `/api/role/addRole` 编辑已有角色时标量字段（name/isAdmin/isDefault/备注）**永不落库**（`roleDao.save` 只在 id 为空时执行），仅重建了 role-menus 关联；级联效应是库中可能不再有 `isDefault=0` 角色，`UserService.assignDefaultRole:255-256` 有 null 守卫故不抛异常，而是**新建用户静默拿不到任何角色**。后续登录 NPE 属未验证的下游推断，管理员补一个默认角色即可恢复 |

功能不可用 / 假成功：

| 编号 | 位置 | 触发 → 后果 |
|---|---|---|
| GP-P1-01 | `service/general/GeneralTelexPatService.java:341-343` | `findMessageBody` 方法体只有 `return null;` → 电传「查询报底」接口恒返回 `{code:200,data:null}`，功能不可用（Key/Ticker 均有实现）|
| CA-P1-06 | `TheoryKnowledgeController.java:176-181` + `TheoryKnowledgeClassifyService.java:89-93` | `uploadFileToNip` 不使用上传文件、缺 `@RestForm`，返回全 null VO 且 `code=200` → 假成功 |
| CA-P1-07 | `TheoryKnowledgeQuestionController.java:86-91` + `TheoryKnowledgeQuestionService.java:174-175` | `exportTemplate` 返回 void（全层唯一非 `Response<T>`，客户端收 204），service 为空方法体 → 功能不存在 |
| CA-P1-05 | `TheoryKnowledgeExamUserController.java:54,59` | `.getData()` 丢弃内层错误 Response 再 `success(ret)` 重包 → 失败伪装成 `code=200, exam=null`；`state` 拆箱 NPE |
| CA-P1-01 | `simulation/SimulationReceptRoomController.java:55` | `@RestQuery("roomgId")` 拼错 → 前端传 `roomId` 得 null，详情为空 |
| CA-P1-02 | `simulation/SimulationReportRoomController.java:56` | 同上拼写错误 |
| CA-P1-03 | `simulation/SimulationRouterRoomController.java:73` | 同上拼写错误。审计修正：与抄收/报告房不同，`SimulationRouterRoomService.java:257-258` 用 `findByIdOptional(null).orElseThrow(IllegalArgumentException)`，因此后果是显式错误信封（HTTP 200 + CODE_500）而非「详情为空」|
| CA-P1-04 | `dto/Page.java:19` + `common/utils/Page.java:17`（7 个消费方）| 默认 `page=0` 而消费方一律 `getPage()-1` → `setFirstResult(-1)` 抛 `IllegalArgumentException`；显式传 `rows=0` 时 `(total+pageSize-1)/pageSize` 除零；`rows` 无上限 → `setMaxResults(大数)` 整表载入 OOM。审计修正：`rows` 默认值是 20，不是 0，除零只在显式传 0 时发生 |
| CA-P0-01→P1 | `controller/test/TestController.java:18,28-46` | 无鉴权 `GET /api/test/start` 连发 begin/pause/goOn/finish 覆盖硬编码 trainId 的训练状态；当前库无目标行（实测 0 行受影响），生产库若存在该行即被静默改写；:42-44 三行 `System.out` 绕日志 |
| PT-P1-02 | `service/PostTelegramTrainService.java:830-844`（端点 `PostTelegramTrainController.java:150-155`）| **任意已登录用户**请求 `GET /postTelegramTrain/test` 即重写硬编码 trainId 第 1 页 `messageBody` 并 `saveAndFlush` 覆盖真实数据；该 trainId 不存在时 NPE 500。审计修正：该控制器有类级 `@JWT`（`:42`），不是无鉴权端点（无鉴权的只有 `GET /api/test/start`）|

边界与状态机（可恢复但功能失败）：

| 编号 | 位置 | 触发 → 后果 |
|---|---|---|
| PT-P1-04 | `service/TelegraphKeyPatTrainService.java:114-130`（128）| 新用户 `clear()`：`save(statisticalEntity)` 在判空之外 → `save(null)` 抛 `IllegalArgumentException` 回滚，清空功能对新用户不可用（上一轮改级 #21 机制未变）|
| PT-P1-05 | `service/PostTickerTapeTrainService.java:159-171`（`reset` 在 :173-185）| `reset` 置 `startTime=null` 后直接 finish：`checkStatus:307-314` 只拦 FINISH/HAS_SCORE、放行 NOT_STARTED → `Duration.between(null,…)` NPE 500 |
| PT-P1-06 | `service/PostTickerTapeTrainService.java:188-258`（232）| `uploadResult` 页数 > 截图数或未传 images → 下标越界/NPE，成绩无法提交 |
| SM-P1-04 | `service/RadiotelephoneService.java:53-61` | 未先 `listPage` 懒建记录即 finish → `:56` 查得 null，`:57` 直接对 null 解引用 NPE（非拆箱），`:58` 的 `Integer.parseInt` 另可抛 NFE；事务回滚，本次结算丢失 |
| SM-P1-06 | `service/simulation/SimulationRouterRoomService.java:125-127` | `bwCount/100 > 可用楼层数` → 越界建房失败；`bwCount<100` → `subList(0,0)` 空 → **房间建成但没有任何报底**，无日志 |
| SM-P1-07 | `service/simulation/SimulationRouterRoomContentService.java:106-109` | 同 SM-P1-06（干扰房）|
| SM-P1-08 | `service/simulation/SimulationReceptRoomService.java:104-107` | 同 SM-P1-06（抄收房）|
| SM-P1-09 | `service/simulation/SimulationReportRoomService.java:103-106` | 同 SM-P1-06（报告房）|

---

## 4. 系统性根因

1. **`@Transactional` 方法内 catch 吞异常 → 部分提交**：上一轮的 P0 家族（先校验后删、异常逸出触发回滚）已在理论考试、菜单权限、试卷、军语等主路径修好，但同一反模式在 `TelegramTrainService.save`、`CableService.delete`、`CableTypeService.delete`、`TelexPatService.deleteTexPatByToken` 仍然原样存在（PT-P1-01、SM-P1-01/02/03，对应上一轮 P1-63/64/65）。`TelexPatService.saveTelexPat:70-76` 已改用 `transactionManager.setRollbackOnly()`，说明修法已知但未推广。
2. **MyISAM 表把「删除+重建」变成不可回滚**：活库实测 22 张 MyISAM 表，主体是训练报底/拍发明细表（`general_*` 10 张、`simulation_router_room_page`/`_value`、`t_post_*_page(_value)`、`t_ticker_tape_train_stage_setting`），另有 3 张不落入该命名规律：`hand_key_err_log`（错误日志追加表，不参与 delete→重建）、`t_post_telegram_train_content_value`、`t_post_telegraph_key_pat_train_more`。关键点是评分结算 `delete→saveAndFlush` 的目标表（`t_post_telex_pat_train_page(_value)`、`t_post_telegraph_key_pat_train_page(_value)`）确实在这 22 张之内。迁移脚本 `docs/database/migrations/2026-08-26-02-engine-innodb.sql` 已提供，但 `generation: validate` 不校验存储引擎，部署漏执行不会被拦住。
3. **同一算法多套互相矛盾的实现**：速率加减分（Ticker 与 Key/Telex 符号相反）、比率计算（`ToolUtil.calculateRate` 三参错版 vs `PatTrainStatisticsUtil` 正确版）、`lastTrain` 排序（1 处 asc vs 2 处 desc）、分页类（`dto/Page` 与 `common/utils/Page` 双胞胎）。上一轮 §3 第 6 条「复制粘贴漂移」仍是首要维护风险。
4. **异常可观测性已建立但口径未收口**：新增 6 个 `@Provider` ExceptionMapper（Global/Validation/IllegalState/InvalidTitle/Unauthorized/WebApplication）、`JWTInterceptor` 的 `context.proceed()` 移出 try、`getUserByToken` 改抛 `UnauthorizedException`，上一轮「全仓无 ExceptionMapper + token 返回 null 传播 NPE」的系统性根因已消除。残余问题是三套响应口径并存（HTTP 200 业务码、`GlobalExceptionMapper` 的 HTTP 500 信封、`ValidationExceptionMapper` 的 200+CODE_500 且回显 `e.getMessage()`），以及 `ResponseCode` 同码多义。
5. **调试端点仍在生产路径**：`GET /api/test/start`、`GET /postTelegramTrain/test`、`POST /user/test` 三个调试端点都写死主键或返回含凭据的实体，且前两个会写库。整改批次只删掉了同目录的死类 `test/Test.java` 与 `free/DemoController`，漏掉真正危险的两个写端点。
6. **Schema 与实体仍有漂移，但已从静默变为硬失败**：`%prod` 改为 `generation: validate` 后，未迁移库启动即失败（本次实测 `missing table [general_telex_pat]`，2.42 秒 fail-fast）。这是正确方向，代价是部署顺序成为硬约束：必须先执行迁移 01→02。SQL 快照 `project006.sql` 仍缺 5 表 2 列，未回灌。
7. **WebSocket 层已系统性重构**：8 个端点仍是 `@ApplicationScoped` 单例，但连接态改为 per-connection holder + 静态 `ConcurrentMap` + `RoomLifecycleLocks` 条带锁，上一轮 2×P0 + 13×P1 基本清零（运行探针证实，见 §5）。残余是可空 `channel`/`userType`/`role` 的未判空解引用与 `onMessage` 缺异常隔离（WS-P2-01..07）。
8. **测试从 0 变成 129 且质量达标**：上一轮「`src/test` 不存在、零回归防护」已修复；8 条确认 P0 逐条有回归测试，且断言的是 DB 真实状态与协议可见行为，不是注解或实现细节。残余风险是套件强依赖 Docker（DevServices）、一处 250ms 负向时序断言可能假绿、`Fixtures` 播种数据不清理。

---

## 5. 运行验证（主代理实测，2026-09-07）

| 验证项 | 实际结果 | 结论边界 |
|---|---|---|
| `JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify` | **BUILD SUCCESS**，02:07；main 编译 750 源文件、test 39；`Tests run: 129, Failures: 0, Errors: 0, Skipped: 0` | 证明 JVM 编译、打包与全部单元/集成测试通过；`skipITs` 使 failsafe 阶段跳过 |
| javac 告警 | 仍为 3 处未展开告警：`common/utils/StringUtils.java`（deprecated API）、`common/specification/SpecificationExecutor.java`（unchecked）、`src/test/java/com/nip/ws/WebSocketUnionTest.java`（unchecked）| 与上一轮相同，未新增 |
| 最慢测试 | `WebSocketUnionTest` 63.63s、`SmokeTest` 29.96s | 两者占套件绝大部分时长，属 CI 时间成本项 |
| prod jar 默认启动（`%prod` = `generation: validate`）| **失败，exit=1**，2.42s fail-fast：`SchemaManagementException: Schema-validation: missing table [general_telex_pat]` | 证实 `validate` 已把 schema 漂移变成硬启动失败；也证实本机 docker 库未执行迁移 01/02 |
| prod jar 覆盖 `generation=none` 启动 | `started in 2.155s`，profile=prod，18002 端口，features 含 hibernate-orm/websockets/smallrye-openapi | 证明生产包本身可启动 |
| `POST /api/cable/type/find` | HTTP 200，`code=200`，返回 3 条真实数据 | HTTP→Controller→Hibernate→MySQL 读链路可用 |
| 伪造 token 请求 `POST /api/user/userOut` | **HTTP 200**，`{"code":206,"message":"账号登录凭证异常"}` | 确认显式业务错误仍走 HTTP 200 + 体内码 |
| `GET /q/openapi` / `GET /q/swagger-ui` | 200 / **404** | OpenAPI schema 在 prod 可用；Swagger UI 未打包，「生产开启 Swagger UI」不成立 |
| `GET /api/test/start`（无鉴权）| HTTP 200，`data="bigDecimal"`；目标行 `02bfee8b-…` 实测 `count(*)=0`，本次调用 0 行受影响 | 端点确实活跃可执行；CA-P0-01 改级依据 |
| 活库表引擎（`information_schema`）| 100 张表 = InnoDB 78 + **MyISAM 22**；`t_post_telex_pat_train_page(_value)`、`t_post_telegraph_key_pat_train_page(_value)` 为 MyISAM；`t_cable`/`t_cable_floor`/`t_cable_type`/`t_ticker_tape_train`/`t_theory_knowledge_swf`/`t_theory_knowledge_exam_test_paper`/`t_theory_knowledge_exam_user` 为 InnoDB | 直接决定 PT-P1-09/10 成立、SM-P1-05 降级、理论考试分片「引擎待验证」项消除 |
| WS 探针 1：`ws://…/websocketUnion/{sid}`，A(sid=1)+B(sid=2)，仅 A 发 `{"code":0}` | A 收到 `code:10` 用户列表与 `code:11`；**B 收到 0 条** | 上一轮 P0 #8「定向发送错投到最后连接者」在运行期已修复 |
| WS 探针 2：C 以同一 sid=1 重连 | A 收 `{"code":1,"data":"关闭连接"}` 后 close(1000)；**B 未受影响**，收到 `code:2` USER_JOIN | 同 sid 重连只驱逐旧连接，不误伤其他连接 |
| WS 探针 3：不存在的 sid | 服务端 `ERROR … NullPointerException: Cannot invoke "UserEntity.getUserName()" because "userEntity" is null`（`WebSocketUnionService.java:78-82`），客户端收 0 条 | 未校验 `findUserEntityById` 返回 null；失败方向安全（连接不建立、无写入），但握手无业务错误帧 |

未执行：Native Image 构建、Docker 镜像、ARM64 runner、WebSocket 并发压测、任何会修改业务数据的 P1 触发请求（`GET /api/test/start` 已确认零行影响后才调用）。

---

## 6. 上一轮核销（以 [remediation-verification](2026-09-07-remediation-verification.md) 为准）

| 核销对象 | 结果 |
|---|---|
| 2026-08-26 汇总确认的 8 条 P0（#1/#2/#3/#4/#6/#8/#9/#22）| **已修复 8 / 未修复 0**；审计回调的 #19/#20 亦已修复，合计 10 项全数落地。测试分片独立确认 8 条均有回归测试锁定，且断言 DB 真实状态或协议可见行为 |
| fix-spec 声称完成的整改（批 0-7 + Task 1-10，24 行）| 已落地 23 / 部分落地 1 / 未落地 0 / 声称与实现不一致 0 |
| 16 条 P0 改级项（实列 15 行）| 已修复 6 / 部分（缓解）1 / 未修复 2（改级 #7 死代码未删、#23 测试端点未删）/ 待邻片运行验证 6 |
| 2026-08-15 遗留 13 条 | **已修复 10 / 未修复 3**：P2-03（`TickerTapeTrainDao.lastTrain` 升序，本轮 PS-P1-01）、P2-06（`PostTelexPatTrainService:255` 仍 `pageNumber<0`）、P2-07（`PostTelegramTrainService:415` 仍 `floorNumber=lastFloor+1`）。修复率由 0/13 提升到 10/13 |
| 各分片自身的上一轮条目 | 逐份见分片 §5：WS 23 条基本清零（仅 `GeneralTickerPatService` 的 `synchronized(this)` 跨分片未修、2 条部分修复）；API 层 34 条为已修复 4 / 部分 6 / 未修复 24；持久层 §5 共 29 行为已修复 6 / 部分修复 4 / 其余未修复（含 2 条判归其他分片、1 条汇总条目）|

整改文档自相矛盾核查：`2026-08-28-fix-spec-remediation.md` 与 `2026-08-28-migration-rehearsal.md` 的验收记录与当前文件状态一致，未发现「声称全绿但代码是旧实现」的条目；唯一系统性偏差是**批次漏认领**——`GET /api/test/start`、`lastTrain` 排序、两处页码生成从未进入任何整改批次。

---

## 7. 建议修复顺序

1. **收口吞异常的事务方法**（PT-P1-01、SM-P1-01/02/03）：删除 catch 或重抛，或统一 `transactionManager.setRollbackOnly()`，照 `TelexPatService.saveTelexPat:70-76` 的既有修法推广；每条补失败回滚回归测试（现有 `PostTelegramFinishCorruptionTest` 是模板）。
2. **执行引擎迁移并加部署门禁**（PS-P1-02、PT-P1-09/10）：在目标库执行 `migrations/2026-08-26-02-engine-innodb.sql`，把 22 张 MyISAM 转 InnoDB；把「引擎检查」加入启动自检或部署脚本，因为 `generation: validate` 不校验引擎。同时把评分结算的 delete→重建改为「先算后写、同事务内替换」。
3. **删除两个写库调试端点**（CA-P0-01→P1、PT-P1-02）：直接删除 `controller/test/TestController.java` 与 `PostTelegramTrainService.test()`/`PostTelegramTrainController:150-155`。其中只有 `GET /api/test/start` 是无鉴权路径；`GET /postTelegramTrain/test` 受类级 `@JWT` 保护，任意已登录用户可触发。
4. **统一漂移实现**（GP-P1-03、GP-P1-02、CI-P1-01、PS-P1-01、CA-P1-04）：速率加减分收敛到单一实现并补跨训练类型的一致性测试；`ToolUtil.calculateRate` 三参版**不可直接删除**（该类 13 处调用中 12 处依赖其「守首参」语义正常工作），最小修正是改对 `GeneralTickerPatService:737` 的首参并把守卫语义统一为「分母为 0 或分子为 0 返回 0」，再逐步迁往 `PatTrainStatisticsUtil`；`lastTrain` 改 desc；两个 `Page` 合一并加 page/rows 钳制。
5. **修评分明细错位**（PT-P1-07、PT-P1-08）：`MessageComparisonService` 多组返回值加 `skipCount`，`LineDetector` 多行/少行分支补 `addCorrectMessage` 并跳过已统计组；用 `src/test/resources/scoring` 基线扩测。
6. **补边界守卫**（PT-P1-04/05/06、SM-P1-04、SM-P1-06..09、TU-P1-01/02、CA-P1-01..07）：`subList` 一律 `Math.min`、状态机补 NOT_STARTED 分支、自测快照 `setId(null)`、角色编辑走 merge、修 `roomgId` 拼写、上传/导出端点要么实现要么删除。
7. **收口响应与日志口径**（CA-P2-14/15、CI-P2-01/02/03）：三套错误响应口径统一，`ResponseCode` 消除同码多义，`JWTInterceptor` 的手写 CORS 与配置择一。

---

## 8. 待运行验证清单（汇总，已消除项标注）

- ~~`t_post_telex_pat_train_page(_value)`、`t_post_telegraph_key_pat_train_page(_value)` 引擎~~ → 已实测 MyISAM，PT-P1-09/10 成立。
- ~~`t_cable_floor` 引擎~~ → 已实测 InnoDB，SM-P1-05 降级 P2。
- ~~`t_theory_knowledge_swf`/`t_test_paper_question` 族引擎~~ → 实测 `t_theory_knowledge_swf`、`t_theory_knowledge_exam_test_paper`、`t_theory_knowledge_exam_user`、`t_theory_knowledge_test_paper_question` 均为 InnoDB，理论考试分片先删后写路径确可回滚。
- ~~`GET /api/test/start` 的硬编码 trainId 是否存在~~ → 当前库不存在（生产库仍需核实）。
- 仍待验证：`PostTelegramTrainService.test()` 的硬编码 trainId `46b6bfee-…` 在生产库是否存在。
- 仍待验证：仿真 `channel`/`role`/`userType` 列是否存在 NULL 值（决定 WS-P2-03..06 是否可触发）。
- 仍待验证：`WebSocketSimulationService` 内 `this.` 自调用 `@Transactional` 方法在 ArC 下是否生效（WS-P3-03）。
- 仍待验证：`Boolean isXxx` DTO 与实体的 Jackson 属性名是否错配（CA-P2-23）；`hh:mm:ss`/`yyy` 日期格式的实际输出（CA-P2-21/22、CA-P3-07）。
- 仍待验证：`ValidationExceptionMapper` 回显的 `e.getMessage()` 是否进入前端与日志（CA-P2-14）。
- 仍待验证：native 产物的功能冒烟（CI 矩阵一律 `-DskipTests`，reflection/资源类 native-only 缺陷 JVM 测试守不住，BD-P2-04）。
- 仍待验证：`WebSocketDeleteOpenAtomicityTest` 250ms 负向时序断言在受控负载下的假绿概率（TS-P2-02）。

---

## 附录：已接受安全风险（内网口径，不计入问题数）

沿用上一轮口径，本轮复核后的变化：

1. **鉴权缺口**：62 个 Controller 中 52 个标 `@JWT`；未标的 10 个里有 6 个含无鉴权写接口（Cable/CableType/Device/PostTickerTapeTrainSetting/PostTrainGlobalRule/TelegraphKeyPatTrainSynthetical）。`DeviceController:44,69` 与 `TelegraphKeyPatTrainSyntheticalController:51,94` 取 header token 却从不校验。8 个 WebSocket 端点全部不校验 token/deviceId，路径参数可伪造身份（含 `role=1` 冒充教员）。
2. **敏感字段外泄**：`UserEntity`（含 password/token/deviceId/idCard）直接作响应体，`UserController:91-92 getAllUser` 可导出全库凭据；`free/UserController:55-59` 的 `/user/test` 空串查询同样返回凭据。
3. **凭据与配置**：`application.yml` 中 `%dev`/`%prod` 均硬编码 root/root；CORS 全开（`origins: '*'` + `JWTInterceptor:50-54` 二次手写 CORS 回显 Origin 与 credentials）；`JWTInterceptor:62,71` 允许 token/deviceId 走 query 参数，放大 GET 写接口（CA-P2-09）的重放面。
4. **依赖 CVE**：`commons-beanutils`/`commons-collections` 已在整改中彻底移除；hutool 相关 CVE 在当前调用方式下不可触发；fastjson 仍是无实际依赖的死配置。
5. **生产暴露面**：`/q/openapi` 返回 200（schema 可读）；`/q/swagger-ui` 实测 404，不再列为暴露风险。
