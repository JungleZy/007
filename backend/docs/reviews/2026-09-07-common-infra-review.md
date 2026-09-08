# 结论：本片 P0 0 条 / P1 1 条 / P2 19 条 / P3 10 条（纯安全项见附录，不计入）

| 项目 | 内容 |
|---|---|
| 审查范围 | `com/nip/common/{annotation,constants,exception,interceptor,response,utils}`（除 repository/specification）、`common/{LifecycleApplication,MainApplication,Page,PageInfo}.java`、`src/main/resources/application.yml`、`reflection-config.json`、`banner.txt`、`pom.xml` |
| 审查日期 | 2026-09-07 |
| 审查方式 | 静态阅读 + grep 取证（未运行构建/测试）|
| 计数 | P0 0 / P1 1 / P2 19 / P3 10 |

> 与上一轮相比，common 层多条严重项已整改（详见 §5）：新增 6 个 `@Provider` ExceptionMapper（Global/IllegalState/InvalidTitle/Unauthorized/Validation/WebApplication；本文早期草稿误写 7，已按 `common/exception/` 实际类数订正）、JWTInterceptor 把 `context.proceed()` 移出 try 并向日志传入 Throwable、`SnowflakeIdKit` 处理时钟回拨、`TickerPatUtils` 全部静默 catch 改为重抛、彻底移除 commons-beanutils/commons-collections、删除 `PojoUtils.merge`。

---

## 1. P0

无。上一轮 common 内的 P0（`TickerPatUtils` 四处空 catch、`checkDotLineGap` 吞异常变满分、连坐清空）均已改为重抛（见 §5）。

---

## 2. P1

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| CI-P1-01 | `common/utils/ToolUtil.java:96-97` | `calculateRate(int min,int max,int total)` 守卫判 `min==0` 却用 `total` 作除数；被 `service/general/GeneralTickerPatService.java:736-737` 以 `calculateRate(groupGapMin, groupGapMin/groupGapMax, groupTotal)` 调用（首参传 groupGapMin 而非 groupTotal，与 728-735 行 pattern 不一致）；当 `groupGapMin==0 && groupGapMax>0`（无“组间隔虚”但有“组间隔粗”，常见） | 主后果：737 行守卫命中返回 `ZERO`，`groupGapMax` 比率被静默算成 0% → 报表数据错误且无异常。（除零 `groupTotal==0 && groupGapMin!=0` 经 ServiceGeneralPat 核实**当前不可达**：groupGapMin 是 groupTotal 的加数；对其余调用点如 742/747/752 传 `total=size()` 时理论仍是潜在除零风险） | 方法体 `return min == 0 ? BigDecimal.ZERO : new BigDecimal(max).divide(new BigDecimal(total),10,...)`；正确实现 `PatTrainStatisticsUtil.java:71-74` 守 `total==0||count==0`。调用点 `GeneralTickerPatService.java:737` 由 ServiceGeneralPat 记 GP-P2-02；方法根因在本片。上一轮 P1-6 未修复 |

> 说明：`calculateRate` 是 `PatTrainStatisticsUtil.calculateRate(count,total)`（守卫正确）的重复且有缺陷的实现。建议删除 `ToolUtil` 三参版，统一收敛到 `PatTrainStatisticsUtil`。

---

## 3. P2

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| CI-P2-01 | `common/interceptor/JWTInterceptor.java:50-54` | 每个请求都手写 CORS 响应头，与 `application.yml:11-15` 的 `quarkus.http.cors` 重复且不一致 | 两组件争抢同名响应头，实际 `Access-Control-Allow-Methods: POST,OPTIONS,PUT,HEAD,DELETE`（缺 GET、PATCH），与配置 `OPTIONS,GET,POST,DELETE,PUT,PATCH` 冲突；行为取决于执行顺序 | 5 行 `response.putHeader(...)` 手写头。上一轮 P2-21 未修复 |
| CI-P2-02 | `common/interceptor/JWTInterceptor.java:55-57` | OPTIONS 预检进入拦截器 | 只 `setStatusCode(200)` 但**无 return**，继续向下做 token 校验；无 token 头的预检会落入 `:64-68` 发 CODE_203 错误体 | `if (OPTIONS) { response.setStatusCode(200); }` 后无 return。`待运行验证`是否有 OPTIONS 落到此分支 |
| CI-P2-03 | `common/interceptor/JWTInterceptor.java:67-68,76-77,82-83,85-88` | 鉴权失败或 token 校验自身抛异常 | (1) `response.send(...)` 已 end 响应后又 `return null`，`@AroundInvoke` 返回 null 使 JAX-RS 再走响应写出 → 双写可能抛 `IllegalStateException`；(2) catch `return ResponseResult.error(...)`（Response 类型），被拦截方法若返回 void（如 `TheoryKnowledgeQuestionController.exportTemplate`）→ 类型不兼容异常 | `response.send(...); return null;`（×3）、`:87`。`context.proceed()` 已移出 try（`:89`），影响面收窄至拦截器自身异常。上一轮 P2-19/P2-20。`待运行验证`双写是否产生错误日志 |
| CI-P2-04 | `common/utils/ToolUtil.java:84` | `isIdFieldEmpty` 中 `@Id` getter 返回运行期产生的空串（非 null、非常量池 `""`） | `value == ""` 引用比较恒 false → 判“ID 非空” → `BaseRepository.java:17` 走 `merge` 而非 `persist`，对空串主键瞬态实体可能插入脏数据 | `return value == null || value == "";`；唯一消费方 `BaseRepository.java:17`。常见 null 路径正确，条件受限。上一轮 P1-5 未修复。`待运行验证` merge 对空串主键的行为 |
| CI-P2-05 | `common/utils/TickerPatUtils.java:130` | 粘连组分支（`length>4 && %4==0 && !"?"`）且长度≥12 | 组间隔扣分按 `length/(2+1)`（/3）计组数，而拆分按 4 位一组（`:100-102`）。长度 8 时巧合相等，≥12 起发散 → 组间隔扣分偏大，落库成绩失真 | `scoreVO.setGroupScore(getGroupScore() + patKey.length()/(2+1)*rule.getLarge().getL())`。上一轮 P1-4 的累加覆盖已修，除数不自洽仍在。`待运行验证` /3 是否为业务预期 |
| CI-P2-06 | `common/utils/PojoUtils.java:70,81` | 目标类缺无参构造/构造器抛异常/native 缺反射注册 | `throw new NullPointerException()`（无 message/cause）语义误导，且映射到 GlobalExceptionMapper 的 HTTP 500 而非 200+code 信封 | 两处 catch 后裸抛 NPE。日志已改为传 `e`（栈保留，上一轮 P1-20/P2-12 日志部分已修）。建议改 `IllegalStateException(clazz, e)` |
| CI-P2-07 | `common/utils/PojoUtils.java:133` | `averageAssign` 返回的分片或源列表被结构性修改，或源为 Hibernate `PersistentBag` 且 session 关闭 | `result.add(source.subList(start,end))` 返回视图非副本 → 写回污染源、`ConcurrentModificationException` 或 `LazyInitializationException` | `result.add(source.subList(start, end));`。上一轮 P2-13 未修复 |
| CI-P2-08 | `common/utils/PojoUtils.java:32,42,61,74,85,97,109` | 任何调用方传入 `ignoreProperties` | 6 方法声明 `String... ignoreProperties` 但从未传给 `CopyOptions`（`:65/77` 仅 `setIgnoreError(true)`）→ 形参静默丢弃，敏感字段照拷不误且无报错 | 无 `.setIgnoreProperties(...)`。高危 API。上一轮 P2-11 未修复 |
| CI-P2-09 | `common/utils/ToolUtil.java:53-62` | `objToList` 收到非 `ArrayList` 的 `List`（`List.of`/`Arrays.asList`/`PersistentBag`） | `obj instanceof ArrayList<?>` 不匹配即 `return null`，null 传到 `UserController.java:83 → addUserRole(...)` | `if (obj instanceof ArrayList<?>){...} return null;`。上一轮 P2-16.1 未修复。建议 `instanceof List<?>` |
| CI-P2-10 | `common/utils/ToolUtil.java:43-51` | 原生查询结果列数不足 3 | `assembleData` 直接 `getFirst()[0..2]` 无长度校验 → AIOOBE | 调用方 `EnteringExerciseDao:52`、`TelegramTrainDao:33/43/52`、`TelegraphKeyPatSyntheticalDao:31`（列固定 3，潜在缺陷）。上一轮 P2-16.2 未修复 |
| CI-P2-11 | `common/utils/ToolUtil.java:110-117` | `calculateTS` 除数 `max+min+per` 之和为 0 | `BigDecimal.divide` 抛 `ArithmeticException` | 10 个调用点（`PostTelegramTrainService.java:919/923/927/931/936`、`GeneralTickerPatService.java:979/983/987/991/996`）当前外层 `if(...!=0)` 挡住，工具本身无契约保证。上一轮 P2-16.3 未修复 |
| CI-P2-12 | `common/utils/CustomPhysicalNamingStrategy.java:42` | 字段/表名含“字母+数字+大写”“连续大写缩写”，或 `@Table/@Column` 指定大写/引用标识符 | 正则 `([a-z]+)([A-Z]+)` 不处理数字/连续大写边界；`.toLowerCase()` 无条件且无 Locale，改写显式大写名与 `isQuoted()` 标识符 → 列名与库不符，运行时 `Unknown column` | `name.replaceAll("([a-z]+)([A-Z]+)","$1\\_$2").toLowerCase()`。上一轮 P2-10 未修复 |
| CI-P2-13 | `src/main/resources/application.yml:19` | 并发把 Agroal 池撑到 >151 | `max-size:200` 超 MySQL 8 默认 `max_connections=151` → `ER_CON_COUNT_ERROR`，随机连接获取失败，池限流被架空 | `jdbc: max-size:200 / min-size:20`。上一轮 P2-4 未修复 |
| CI-P2-14 | `application.yml:62,81` | 空闲后遇 MySQL `wait_timeout` 断链 | URL 里 `idleTimeout=60000&connectionTestQuery=SELECT 1` 是 HikariCP/DBCP 参数名，Agroal 下被 Connector/J 忽略 → 空闲回收/借出前校验均未生效，空闲后首次请求必失败 | dev+prod 两处 URL。上一轮 P2-5 未修复。应改 Agroal 键 `idle-removal-interval/validation-query-sql/background-validation-interval` |
| CI-P2-15 | `application.yml:43` | 文件日志输出 | `format:'...%s%e%n %l %F'` 中 `%n` 位于 `%l %F` 之前且末尾无换行 → 位置信息与下一条时间戳挤同一行，破坏按行采集；`%l`+`%F` 每条遍历栈帧、`%l` 已含 `%F`（重复且开销高） | `application.yml:43`。上一轮 P2-6 未修复 |
| CI-P2-16 | `src/main/resources/reflection-config.json:1-12` + `application.yml:55` | native 反射配置排查 | 仍是脚手架模板（`com.example.SourceObj`/`com.example.TargetObj`，本项目为 `com.nip.*`），加载参数被注释；纯误导，解注释还会因类不存在报错、且 CI 命令行 args 会整体覆盖 | 两个 `com.example.*` 条目；`application.yml:55` 注释行。上一轮 P2-9 未修复（实际靠 `@RegisterForReflection`）|
| CI-P2-17 | `pom.xml:22` | 依赖/安全扫描 | `<fastjson.version>1.2.78</fastjson.version>` 有属性但无 fastjson 依赖、无 import → 死配置，误判受 CVE-2022-25845 影响 | `pom.xml:22`。上一轮 P2-2 未修复 |
| CI-P2-18 | `application.yml:32-34` | 打包后访问 `/q/swagger-ui` | `swagger-ui.enable/theme` 仅控制“纳入构建”，生产暴露需构建期 `always-include=true`；缺该项 → prod 404（汇总 §4 已验证 prod jar 404），`theme` 落空 | `swagger-ui: enable:true / theme:newspaper`。上一轮 P2-7 未修复 |
| CI-P2-19 | `application.yml:52-53` | native 打包 | `native.resources.includes: resources/**` 把 `src/main/resources/resources/`（非 Quarkus 静态资源约定目录 `META-INF/resources/`）全量塞进 native → 死资源增大产物、且不含 `banner.txt` | `native: resources: includes: resources/**`。上一轮 P2-8（打包侧交 BuildDelivery，此处只记 yml 配置侧）|

---

## 4. P3

| 编号 | 位置(file:line) | 说明 | 证据 |
|---|---|---|---|
| CI-P3-01 | `common/utils/DateTimeUtil.java:80-82` | `currentTimeMillis()` 的 `Instant.now().atZone(UTC_PLUS_8).toInstant().toEpochMilli()` 是恒等变换，等价 `System.currentTimeMillis()`，误导读者 | grep `DateTimeUtil.currentTimeMillis` **0 处调用**。上一轮 P3-2 未修复 |
| CI-P3-02 | `common/utils/DateTimeUtil.java:10` | `SHANGHAI` 常量零使用；注释“同样表示东八区”对 1991 前历史日期不成立 | grep `SHANGHAI` **0 处外部引用** |
| CI-P3-03 | 见右列 | 零调用死代码（实测）：`NumUtil.java`（`NumUtil.` **0**）、`ArrayUtils.java`（`ArrayUtils.`/`new ArrayUtils` **0**）、`RatingJudgmentUtils.java`（**0**，唯一方法 `electronicKey(String,String)` 为空实现）、`exception/` 数据访问异常族 7 类（`DataAccessException/NonTransientDataAccessException/DataRetrievalFailureException/IncorrectResultSizeDataAccessException/EmptyResultDataAccessException/NestedRuntimeException/NestedExceptionUtils`，`throw new (Empty|Incorrect|DataRetrieval|DataAccess|NonTransient)...` **0 处抛/捕获**，仅内部 extends 链自引用） | 注：`NIPException` 非死代码（MilitaryTermDataService 等在用）；`SnowflakeIdKit`(1 调用 WebSocketUnionService:294)、`ArraysSafeUtils.getElement`/`ArraySafeGetUtils.get`(各有活调用) 均非死代码。上一轮 P2-17 部分修复但残留+新增 |
| CI-P3-04 | `common/utils/MD5Util.java:38-40`、`AESUtil.java:95-96` | 残留 `main`：`MD5Util.main` 打印 `encrypt("123456a")`；`AESUtil.main` 空体 | 上一轮 P3-1 部分修复（含凭据的 PasswordUtil 已删；这两处仍在）|
| CI-P3-05 | `common/LifecycleApplication.java:26` | banner `InputStream is` 从未 close/无 try-with-resources；`banner.txt` 未在 `native.resources.includes`，native 下 `getResourceAsStream` 返 null（被 `:27 if(is!=null)` 兜住，静默跳过）| 上一轮 P3-3 部分修复（catch 已 `warn(e)`，流仍未关）|
| CI-P3-06 | `common/Page.java` 与 `common/utils/Page.java` | 两个同名 `Page` 类并存：`common/Page.java`（分页 DTO，仅被 `PojoUtils.convertPage(Page<V>)` 重载引用，调用方待确认）；`common/utils/Page.java` 被众多 controller/service 引用。命名易混淆 | glob 两处 `Page.java`；`PojoUtils.java:6 import com.nip.common.Page` |
| CI-P3-07 | `pom.xml:75-83` | 整块注释的 `quarkus-awt`/`io.quarkiverse.poi:quarkus-poi` 依赖残留 | `pom.xml:75-83` |
| CI-P3-08 | `common/utils/ArraySafeGetUtils.java:35-39`、`ArraysSafeUtils.java:61-65` | 两类功能高度重叠（应合并）；`get(List,int,Consumer)` 重载直接 `list.size()`，`list==null` 抛 NPE，与同类带 null 检查的 `getElement` 风格不一致 | 上一轮 P3-4 未修复。`待运行验证` Consumer 重载是否有调用方 |
| CI-P3-09 | `common/utils/JSONUtils.java:39-44` | `fromJson(String, Type)` 为**非 static** 实例方法，与同类 static 重载不一致，需持有实例才能调用，易误用 | `public <T> T fromJson(String jsonStr, Type typeOfT)` 缺 `static` |
| CI-P3-10 | `pom.xml:7` | `<version>1.0-SNAPSHOT</version>` 未与已发布 native 制品 v1.0.0 同步（memory 记“pom 版本仍是 1.0-SNAPSHOT 未同步”——**核实成立**）| `pom.xml:7` |

---

## 5. 上一轮遗留核销

### 5.1 `2026-08-26-common-build-review.md`（common/配置/pom；CI/Docker 交 BuildDelivery）

| 上轮编号 | 上轮结论 | 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| P1-1 | `resolverMoresTime/Value` 误用 `add(int,E)` | 已修复 | `TickerPatUtils.java:182-184` 四列均 `.set(size-1,...)` |
| P1-2 | 普通组分支 Time/Value 写反 | 已修复 | `:247-248`、`:260-262` 顺序正确 |
| P1-3 | `patKeys` 过滤未同步 `userContents` | 已修复 | `:53-65` `filteredKeys`/`filteredContents` 同步筛选 |
| P1-4 | `groupScore` 覆盖 + 除数 `(2+1)` | 部分修复 | 累加已修（`:130 getGroupScore()+`）；除数 `/(2+1)` 仍不自洽（CI-P2-05）|
| P1-5 | `isIdFieldEmpty` 用 `==` 比较空串 | 未修复 | `ToolUtil.java:84`（CI-P2-04）|
| P1-6 | `calculateRate` 守卫检查错参 + 调用点错参 | 未修复 | `ToolUtil.java:96-97`；`GeneralTickerPatService.java:737`（CI-P1-01 + GP-P2-02）|
| P1-7 | CI arm64/release | 交 BuildDelivery | — |
| P2-1 | commons-collections 靠 beanutils 传递 | 已修复 | `Assert/ObjectUtils/StringUtils` 改用 `cn.hutool.core.collection.CollUtil`；grep `commons.collections`/`commons-beanutils` **0 匹配** |
| P2-2 | `fastjson.version` 死属性 | 未修复 | `pom.xml:22`（CI-P2-17）|
| P2-3 | WS 用 jose4j 内部 JSON | 交 WsConcurrency | — |
| P2-4 | 连接池 max-size 200 | 未修复 | `application.yml:19`（CI-P2-13）|
| P2-5 | JDBC URL 塞 HikariCP 参数 | 未修复 | `application.yml:62/81`（CI-P2-14）|
| P2-6 | 日志 format `%n` 位置错 | 未修复 | `application.yml:43`（CI-P2-15）|
| P2-7 | swagger-ui 生产不暴露 | 未修复 | `application.yml:32-34`（CI-P2-18）|
| P2-8 | 静态资源放错目录 | 未修复（配置侧）| `application.yml:52-53`（CI-P2-19）|
| P2-9 | reflection-config 模板残留 | 未修复 | `reflection-config.json:1-12`、`application.yml:55`（CI-P2-16）|
| P2-10 | 命名策略正则 | 未修复 | `CustomPhysicalNamingStrategy.java:42`（CI-P2-12）|
| P2-11 | PojoUtils `ignoreProperties` 未传 | 未修复 | `PojoUtils.java:65/77`（CI-P2-08）|
| P2-12 | convertOne 吞成裸 NPE | 部分修复 | 日志已传 `e`（`:69/80`）；仍 `throw new NullPointerException()`（`:70/81`，CI-P2-06）|
| P2-13 | averageAssign 返回视图 | 未修复 | `PojoUtils.java:133`（CI-P2-07）|
| P2-14 | merge 参数顺序反 + beanutils | 已修复 | `merge` 已删、`commons-beanutils` 依赖已移除 |
| P2-15 | PasswordUtil.decryptPassword 越界 | 已修复 | `PasswordUtil` 已删除 |
| P2-16 | ToolUtil objToList/assembleData/calculateTS | 未修复 | `ToolUtil.java:55/46/110`（CI-P2-09/10/11）|
| P2-17 | 死代码清单 | 部分修复 | 上轮删除项生效；`SnowflakeIdKit`/`ArraysSafeUtils` 确有活调用；新增/残留见 CI-P3-03；calculateRate 重复实现仍在 |
| P2-18 | 每请求 new ObjectMapper | 已修复 | `JWTInterceptor.java:41-42 @Inject ObjectMapper`；`:67/76/82` 复用 |
| P2-19 | send 后 return null 双写 | 未修复 | `JWTInterceptor.java:67-68/76-77/82-83`（CI-P2-03）|
| P2-20 | 返回类型不兼容 + 日志丢异常 | 部分修复 | 日志已传 `exception`（`:86`）；catch 仍 `return ResponseResult.error(...)`（`:87`，CI-P2-03）|
| P2-21 | 手写 CORS 与配置冲突 | 未修复 | `JWTInterceptor.java:50-57`（CI-P2-01/02）|
| P2-22 | `docs/guides/code.java` 游离代码 | 未核销（超出本片源码范围，docs/ 非本片文件）| — |
| P2-23/24 | CI 缓存 key / .gitignore | 交 BuildDelivery | — |
| P3-1 | 工具类残留 main（含凭据）| 部分修复 | 含凭据的 PasswordUtil 已删；`MD5Util.java:38`、`AESUtil.java:95` 仍在（CI-P3-04）|
| P3-2 | DateTimeUtil 空操作 + SHANGHAI 未用 | 未修复 | `DateTimeUtil.java:81/10`（CI-P3-01/02）|
| P3-3 | banner 流未关 + native 取不到 | 部分修复 | catch 已 `warn(e)`；流仍未关（`:26`，CI-P3-05）|
| P3-4 | Safe get(...,Consumer) 缺 null 检查 | 未修复 | `ArraySafeGetUtils.java:35-39`、`ArraysSafeUtils.java:61-65`（CI-P3-08）|

### 5.2 `2026-08-26-silent-failures-review.md`（utils/interceptor + 汇总 §3.4）

| 上轮编号 | 上轮结论 | 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| P0-1 | TickerPatUtils 四处空 catch 覆盖记录 | 已修复 | `:80-98` 三次 `fromJson` 各独立 try 并 `throw corruptJson(...)`；`handleMessageBody :301-338` 抛 `IllegalStateException` 拒绝写入 |
| P0-2 | checkDotLineGap 解析失败变满分 | 已修复 | `:544-546 throw corruptJson("patLogs",...)` |
| P0-3 | 三字段共用一个 try 连坐清空 | 已修复 | `:75-98` 拆为三个独立 try 各自重抛 |
| P1-1 | JWTInterceptor 日志丢异常堆栈 | 已修复 | `:86 log.error("jwt fail from {}.{}",...,exception)`；且 `context.proceed()` 移出 try（`:89`），业务异常改走 ExceptionMapper |
| P1-20 | PojoUtils 反射异常换裸 NPE + 日志丢栈 | 部分修复 | 日志已 `log.error("convertOne error", e)`；仍裸抛 NPE（CI-P2-06）|
| 汇总 §3 第4条 | 全仓无 ExceptionMapper；JWTInterceptor 丢异常；日志只写 e.getMessage() | 已修复（common 部分）| 新增 7 个 `@Provider ExceptionMapper`（`common/exception/*Mapper.java`）；JWTInterceptor 已传 Throwable；common 内除 mapper 面向用户的 `getMessage()` 外，PojoUtils/ToolUtil/MD5Util 均保留完整 `e`/cause。（WS `@OnError` 属 WsConcurrency 分片）|

---

## 6. 待运行验证清单

1. CI-P1-01：`GeneralTickerPatService:737` 在 `groupGapMin==0 && groupGapMax>0` 时 `groupGapMax` 比率是否落库为 0%（除零已由 ServiceGeneralPat 核实当前不可达）。
2. CI-P2-02：是否真有 OPTIONS 预检落到 `JWTInterceptor:55-57`（还是被 Quarkus CORS 过滤器提前处理）。
3. CI-P2-03：鉴权失败路径 `response.send(...)+return null` 是否产生响应双写 `IllegalStateException` 日志；catch 返回 `Response` 对 void 端点是否触发类型不兼容异常。
4. CI-P2-04：`isIdFieldEmpty` 误判后 `merge` 对空串主键瞬态实体的确切结果。
5. CI-P2-05：`groupScore` 除数 `/(2+1)` 是否为业务预期。
6. CI-P3-08：`get(List,int,Consumer)` 重载是否存在调用方（决定 null-NPE 可达性）。

---

## 7. 测试覆盖缺口（对照 `src/test/java/com/nip/common/**`）

现有 5 个测试：`exception/ExceptionBoundaryTest`、`utils/SnowflakeIdKitTest`、`utils/TickerPatUtilsTest`、`utils/TickerPatUtilsCharacterizationTest`、`utils/ScoreMathTest`。覆盖到位：SnowflakeIdKit（含时钟回拨）、TickerPatUtils（解析/评分特征化）、ScoreMath（守分母/分子）、ExceptionMapper 边界。

缺口：
- `JWTInterceptor` 无测试——CORS 头一致性、OPTIONS 不 return、send+return null 双写、catch 对 void 端点（CI-P2-01/02/03）无回归保护。
- `ToolUtil` 无测试——`isIdFieldEmpty(=="")`、`calculateRate` 错参守卫、`objToList` 非 ArrayList 返 null、`calculateTS`/`assembleData` 边界（CI-P1-01/P2-04/09/10/11）。
- `PojoUtils` 无测试——`convertOne` 裸 NPE、`averageAssign` subList 视图、`ignoreProperties` 未生效（CI-P2-06/07/08）。
- `CustomPhysicalNamingStrategy` 无测试——数字/连续大写/引用标识符命名（CI-P2-12）。

---

## 附录：已接受安全风险（内网口径，不计入计数）

- A-1 `hutool-all 5.8.12` — CVE-2023-24163（aviator，未用）/CVE-2023-42278（hutool-json，本项目 JSON 走 Gson），当前不可触发；可顺手升 5.8.22+。`commons-beanutils` 已删除，CVE-2025-48734 随之消失。
- A-2 `fastjson 1.2.78` — 仅 pom 死属性无实际依赖，不受 CVE-2022-25845 影响（CI-P2-17）。
- A-3 `application.yml:11-15` CORS 全开（`origins:'*'`、`headers:'*'`），内网可接受；与 JWTInterceptor 手写 CORS 的冲突按功能项 CI-P2-01 记。
- A-4 凭据/密钥硬编码：`application.yml:59/60/78/79` root/root 明文超级用户；`AESUtil.java:20 UKDAI_AES_KEY` 硬编码。
- A-5 口令/令牌强度：`UserService` 无盐 MD5；登录 token 为 `AES/ECB/PKCS5Padding`（`AESUtil.java:30`，确定性、明文口令可逆入 token）。内网口径为已接受风险。
