# 结论：8 份评审文档整体可信——357 条断言逐条复核，348 条确认（97.5%）、误报仅 2 条；但 full-project-review 的 14 条 P0 改级中 4 条理由站不住，#19/#20 应按 P0 对待

| 项目 | 内容 |
|---|---|
| 审计对象 | docs/reviews 下全部 8 份文档：2026-08-26 六份分片 + 2026-08-26 汇总（full-project-review）+ 2026-08-15 遗留报告 |
| 审计方式 | 8 个并行子代理：7 个按分片对照 `main` 源码逐条静态判定（以符号/方法名重定位，行号漂移不算误报），1 个实际运行构建与依赖树复现 |
| 审计日期 | 2026-08-26 |
| 判定口径 | 五分类：确认 / 部分成立（现象存在但触发条件或后果有出入）/ 误报 / 已失效 / 待运行验证；逐条给 file:line 证据，禁止抽样 |
| 测试维度 | `src/test` 不存在，仓库 0 测试（复核属实）；可复现的验证只有构建与依赖树，本次已实际执行（见 §5） |
| 边界 | 全程只读；未调用写库接口，未做 WebSocket 并发压测、Native/Docker/ARM64 验证 |

---

## 1. 总体判定统计

| 被审计文档 | 断言数 | 确认 | 部分成立 | 误报 | 已失效 | 待运行验证 |
|---|---|---|---|---|---|---|
| ws-concurrency-review（2P0+13P1+8P2） | 23 | 23 | 0 | 0 | 0 | 0 |
| service-core-review P0/P1（14P0+70P1+5压缩块） | 89 | 85 | 3 | 1 | 0 | 0 |
| service-core-review P2（P2-A + P2-02…96） | 96 | 94 | 1 | 1 | 0 | 0 |
| controller-api-review（1P0+8P1+19P2+6P3） | 34 | 33 | 1 | 0 | 0 | 0 |
| persistence-review（2P0+9P1+15P2+2P3） | 28 | 28 | 0 | 0 | 0 | 0 |
| common-build-review（7P1+24P2+4P3） | 35 | 34 | 1 | 0 | 0 | 0 |
| silent-failures-review（7P0+20P1+12P2） | 39 | 38 | 1 | 0 | 0 | 0 |
| 2026-08-15 遗留报告（1P1+12P2） | 13 | 13 | 0 | 0 | 0 | 0 |
| **合计** | **357** | **348** | **7** | **2** | **0** | **0** |

- 0 条"已失效"：评审后代码没有变化，所有确认缺陷至今都在。
- 2026-08-15 遗留 13/13 未修复，full-project-review 的"修复率 0%"属实。
- 分片间存在重复条目（silent-failures 与 service-core/ws/persistence/controller 大量交叉），本表按"文档断言"计数，不做跨文档去重；重复指认见 silent-failures 审计明细。

## 2. 误报（2 条）

| 条目 | 文档声称 | 实际 |
|---|---|---|
| service-core **P0-14**（TickerTapeTrainService.update）| "调用一次 update 接口即抹空 11 列" | `update()` 全仓无调用，是死代码；pause/finish 走 DAO 定向更新。full-project-review 改级 #7 已自行纠正此条，分片原文未改 |
| service-core **P2-25**（ComprehensiveService:287）| "credit 字符串转 BigDecimal 抛 NumberFormatException" | `TheoryKnowledgeEntity.credit` 是 `Double`（entity:103），`new BigDecimal(double)` 不可能抛 NFE，前提不成立 |

## 3. 部分成立（7 条，现象在、表述有出入）

| 条目 | 成立部分 | 出入点 |
|---|---|---|
| service-core P0-09（统计 clear→save(null)） | 新用户路径失败确认 | "老用户插空白垃圾行"过宽：NPE 随事务回滚（与 full-review 改级 #21 一致） |
| service-core P1-56（命名查询日期格式） | `substring(5)+valueOf` 脆弱假设存在 | 当前两条命名查询均返回 `%Y-%m`，无现故障；full-review 已剔除计数，处理正确 |
| service-core 压缩块 DeviceTypeService.delete | 删父/查子顺序颠倒 + 空 `in()` 属实 | 具体功能后果依赖运行时 |
| service-core P2-17（TickerTapeTrainService:216） | `Collections.swap(0,1)` 作用于无 ORDER BY 列表，Tab 串位属实 | "size<2 越界"不可达：:203-214 对 type 0/1/2 缺失回填，size 恒 ≥3 |
| controller-api P1-6（11 处未校验类型转换） | 11 处存在、全仓无 ExceptionMapper（grep 零命中）均属实 | 后果错：这 11 处都在 @JWT 拦截下，`JWTInterceptor.java:82-84` catch(Exception) 兜成 HTTP 200 + code 500 结构化响应，不是"打穿封装返回 Quarkus 默认 500 页" |
| common-build P2-17（死工具类清单 10 行） | 8 行属实 | SnowflakeIdKit 被 `WebSocketUnionService.java:230` 调用；ArraysSafeUtils 被 `GeneralKeyPatService.java:964-965`、`GeneralTickerPatService.java:621-622` 调用 |
| silent-failures P0-6（TheoryKnowledgeService） | 核心成立：:231 无条件先删课件，:259 NPE 被吞（:293 只打 getMessage），事务照常提交，课件确定丢失 | "测验也被删"对该 NPE 路径不精确：测验删除在 :276、位于 NPE 之后，单测验场景测验内容未删 |

## 4. full-project-review（汇总报告）二次复核质量

### 4.1 确认 P0（8 条）——全部站得住

分片审计对 8 条确认 P0（#1-4、#6、#8、#9、#22）逐条给出 file:line 佐证，无一推翻。关键机制均静态确证：`BaseRepository` id 空→persist/非空→merge（:16-22）、`JSONUtils.toJson(null)=""`/`fromJson("")=null`（:19-34）、`PojoUtils.convert(null)` 返空列表不抛（:34-39）。改级 #23 的 `GET /api/test/start` 端点也已核实存在且活跃（`TestController:33-41`，硬编码 trainId 连发四次 DAO 更新）。

### 4.2 14 条 P0 改级：10 条合理，4 条存疑

合理：#7、#10、#11、#12、#13、#14、#15、#16、#17、#21。

存疑 4 条：

| 改级 | 问题 |
|---|---|
| **#19（考试快照复用源试卷 id）→ 应回调 P0** | 改级理由"delete 后 merge 结果需运行验证"过保守：`TheoryKnowledgeExamService` :74 `deleteById(源试卷id)` 在 merge 之前**直接删掉第一场考试的快照**，跨考试数据丢失是确定性的，不需要 Hibernate 行为验证 |
| **#20（快照题型 addAll(null)）→ 应回调 P0** | 改级依赖"正常空题型由 TestPaperService 初始化为 []"，但考核分析路径读的是客户端 `TestPaperDto` 直写的快照：五个题型列表无初始值 → null → `toJson`="" → 读回 null → `addAll(null)` NPE，防护不覆盖该路径 |
| #18（军语 Excel 导入 NPE） | "失败会回滚"与同报告 P1-43 自相矛盾：saveBatch 无事务且 CDI 自调用绕过 @Transactional，失败**不**回滚 |
| #5（电传转换） | 结论方向（改 P1）可维持，但论据写错："稳定转换为空证据不成立"不准确——无输入 finish 时 `TelexPatUtils:331-339` 恒不 add，报底确实先被删 |

### 4.3 统计口径核对

| 声称 | 实测 | 判定 |
|---|---|---|
| 746 个编译源文件 | 746 个 .java（实测计数） | ✓ |
| 63 个 Controller、52 个 @JWT | 63 类/64 文件（含 test/Test.java 非 controller 实验类）、@JWT 52 | ✓（但 controller-api 分片自述 scope"52 个文件"是错的，疑把 @JWT 数当文件数） |
| MyISAM 22 张 | 22 张，清单逐名吻合（project006.sql 全 36447 行逐段核对引擎） | ✓ |
| 5 张实体表缺失 | general_telex_pat/_page/_user/_user_value + t_masthead，两份 dump 字母序缺口证实 | ✓（怀疑的第 6 张缺表已排除：TheoryKnowledgeQuestionLevelEntity 实映射 `t_theory_question_level`，dump 存在） |
| 遗留 13/13 未修复 | 13/13 确认仍在 | ✓ |
| P2-05 扩散到 GeneralTelexPatPageDao:22-24 | 扩散成立但只有一半：telex 侧确把 trainId 当 `id=?1` 过滤（调用方 GeneralTelexPatService:279 传 trainId），但泛型与主键均为 String，无 Integer/String 类型错配——静默错/空结果，不会绑定期抛异常 | 部分成立 |
| 分片索引表计数 | service-core 正文实为 14P0+70P1+5压缩块+96P2=185 条，索引记 179（5P0+77P1+97P2）；persistence 正文 2P0+9P1 vs 索引 0P0+11P1；silent-failures 正文 7P0+20P1 vs 索引 4P0+23P1。改级/去重可解释大部分，但 full-review"计数修正"一节只说明了基础设施分片，service-core 的 −6 与 P2 97↔96 差额未见记账说明 | 记账缺口 |
| common-build 分片页首"27 条" | 正文实为 35 条（full-review 已纠正，分片原文未改） | 分片自身错误 |

## 5. 运行复现（本次实际执行）

| 验证项 | 结果 |
|---|---|
| `JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify` | BUILD SUCCESS（18.4s）；`No tests to run.`——**0 测试**复现 |
| javac 告警 | deprecation 在 `StringUtils.java`、unchecked 在 `SpecificationExecutor.java`，与 full-review §4 一致 |
| `dependency:tree -Dincludes=commons-collections` | `commons-beanutils:1.9.4:compile → commons-collections:3.2.2:compile`，传递链属实 |

未复现（超边界）：应用启动、HTTP/数据库冒烟、WS 并发、Native/Docker/ARM64——full-review 对这些已自标"待验证"，口径诚实。

## 6. 审计新发现（8 份文档均未收录）

评审遗漏的同类缺陷，建议纳入下轮修复清单：

**建议 P1：**
1. `common/utils/TickerPatUtils.java:640` — 词间隔完美数写成 `setWordPerfectNumber(getCodePerfectNumber()+1)`，复制粘贴取错字段，词/码完美统计静默串号（评分静默错误家族的漏网条目）。
2. `ws/WebSocketUnionService.java:110-111` — onMessage 连续两次 `setReceiveUser`（:111 覆盖 :110），`setSendUser` 从未调用，发送方字段静默丢弃。
3. `ws/WebSocketSimulationService.java:301-320`（quitRoomRouter）— 仅 `simulations.remove(removeObj)`，routerRoom 的 key 永不移除 → 条目泄漏（与已收录的 P1-4/5 同类，漏了 router 表）。
4. `ws/WebSocketUnionService.java` removeRoom:304-308 / roomMessage:401 / userExit:180 — 对 `webSocketClientSet.get(...)` 不判空即解引用，成员离线独立 NPE。
5. `ws/WebSocketService.java:185-189`（sendInfoAll）— 忽略 `sid` 入参对所有连接广播，潜在越界推送。

**建议 P2：**
6. `service/RoleService.java:91-94`（getRoleById）— findById 后不判空直接 `.getId()`；同类 :104 却判了，类内不一致。
7. `service/TheoryKnowledgeExamService.java:346-352` — `userDao.findById(userId)` 后 `.getUserAccount()` 无判空，考生被删即 NPE。
8. `service/MilitaryTermDataService.java:76-85`（getKey）— 取 map 首个非 null key，条目同含 key+value 时依赖遍历顺序，可能取错列（saveAll:64 依赖）。
9. `service/EquipmentDeviceService.java` — 事务外取托管实体→改→另开事务 saveAndFlush，污染同请求持久化上下文（P2-A 缺事务的副作用补记）。
10. `Dockerfile.jvm:91` / `Dockerfile.native:24` — `EXPOSE 8080` vs 应用实际端口 18001（full-review §3.9 提了示例映射，分片未收录 EXPOSE 本身）。

**文档记账类：** common-build P2-16 的 calculateTS 调用点漏计 5 处（`GeneralTickerPatService.java:946-963`）；P2-11 漏列 `PojoUtils.java:86` 的 convertPage 重载（同样丢弃 ignoreProperties）。

**已排除：** `t_theory_knowledge_question_level` 缺表怀疑不成立（实体映射 `t_theory_question_level`）；`application.yml:17-18` root/root 明文属已接受安全风险口径，不计数。

## 7. 对 full-review 修复顺序的修正

full-review §6 的六步顺序整体成立，两处调整：

1. **第一批止血扩容**：改级 #19（快照复用源试卷 id → deleteById 直删首场快照）和 #20（考核分析路径 addAll(null)）按 P0 处理，并入"P0 #1-4、#6"批次——两者都是确定性数据丢失/崩溃，不依赖运行验证。
2. **军语 Excel 导入（#18）按"不回滚"设防**：其降级依据被 P1-43（saveBatch 无事务 + CDI 自调用绕过 @Transactional）推翻，修复时须先补事务边界再谈导入健壮性。

其余不变：每批修复前先补该路径回归测试——当前 0 测试，任何重构无仓库内告警，这是全部 348 条确认缺陷共同的放大器。

---

## 附录：审计分工与产物

| 子代理 | 范围 | 明细产物 |
|---|---|---|
| WsAudit | ws-concurrency 23 条 | agent://WsAudit |
| SvcCoreP0P1 | service-core P0+P1+压缩块 89 条 + full-review 2.1 节 14 条改级评估 | agent://SvcCoreP0P1 |
| SvcCoreP2 | service-core P2 96 条（含全 96 行逐条判定表） | agent://SvcCoreP2 |
| CtrlApiAudit | controller-api 34 条 + Controller/@JWT 实测计数 | agent://CtrlApiAudit |
| PersistAudit | persistence 28 条 + MyISAM/缺表/SQL 快照全量核对 | agent://PersistAudit |
| SilentFailAudit | silent-failures 39 条 + 跨分片重复指认 | agent://SilentFailAudit |
| LegacyAudit | 2026-08-15 遗留 13 条 + 扩散声明核实 | agent://LegacyAudit |
| BuildVerify | common-build 35 条 + 构建/依赖树实际复现 | agent://BuildVerify |

确认条目的逐条 file:line 证据与原评审文档定位一致（行号漂移处已按符号重定位），未在本文重复；非"确认"条目已全部列于 §2/§3。
