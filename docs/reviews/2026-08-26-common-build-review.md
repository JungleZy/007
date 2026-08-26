# 基础设施与构建审查（common/ + pom.xml + application.yml + CI + native 配置）

**审查日期**：2026-08-26　**审查范围**：`src/main/java/com/nip/common/`、`pom.xml`、`src/main/resources/application.yml`、`banner.txt`、`reflection-config.json`、`.github/workflows/`、`.gitignore`、`src/main/resources/resources/`、`docs/guides/code.java`
**审查方式**：纯静态阅读（本机无 Java 环境，未执行任何构建/测试），CVE 经 NVD / GitHub Advisory 联网核对。

---

## 结论

**共 27 条问题：P0 = 0，P1 = 7，P2 = 16，P3 = 4。**另有 8 条纯安全项按内网口径列入附录，不计入上述总数。

最需要立刻处理的三件事：

1. **`TickerPatUtils.resolverMessage` 有 4 处独立缺陷**，其中「moresTime / moresValue 两列互换」走的是最常见的普通组分支，等于所有拍发训练的「电划耗时」和「点划表示」两列在落库时是反的。
2. **CI 的 ARM64 构建必然失败**（给 aarch64 的 native-image 传了 `-march=x86-64`），且 `release` job 永远不会被触发（workflow 根本不监听 tag push）——也就是说这条流水线目前只有 x86_64 Linux + Windows 两个 job 真正有效，发布环节是死的。
3. **`commons-collections 3.2.2` 被 3 个 common 工具类直接 import，但 pom.xml 里没有声明**，它是靠 `commons-beanutils` 传递进来的；而 `commons-beanutils` 本身只服务于一个从未被调用的方法。想删掉 beanutils 或升级它修 CVE-2025-48734，会直接导致 `Assert` / `ObjectUtils` / `StringUtils` 编译失败。

另需说明：任务书写的是「common/ 12 个文件」，实际 `com/nip/common/` 下有 70 余个 `.java`（仅 `utils/` 就有 34 个）。本报告覆盖了其中的基础设施类与通用工具类；`repository/BaseRepository`、`specification/*` 已与 `Persistence` agent 约定由其负责，本报告不重复。

---

## P1（严重缺陷：数据完整性 / 构建阻断）

### P1-1　`resolverMoresTime` / `resolverMoresValue` 误用 `List.add(int, E)` 插入语义，导致四个并行列表长度失配

**位置**：`src/main/java/com/nip/common/utils/TickerPatUtils.java:167-170`

**现象**：同一个 if 分支内，前两行用的是覆盖语义，后两行用的是插入语义：

```java
ret.set(ret.size() - 1, substring);                                              // 167  覆盖
resolverPatLogs.set(resolverPatLogs.size() - 1, JSONUtils.toJson(newPatLogs));   // 168  覆盖
resolverMoresTime.add(resolverMoresTime.size() - 1, JSONUtils.toJson(newTimes)); // 169  插入！
resolverMoresValue.add(resolverMoresValue.size() - 1, JSONUtils.toJson(newValues)); // 170 插入！
```

`List.add(int index, E element)` 是**插入**，不是替换。同一分支下 `ret` / `resolverPatLogs` 元素数不变，而 `resolverMoresTime` / `resolverMoresValue` 各多出 1 个元素。

**影响**：`PostTelegramTrainResolverVO` 里四个列表是按同一下标一一对应消费的（见 `TickerPatUtils.java:257-260` 的组装，以及下游按 index 取值的逻辑）。一旦命中该分支，后续所有组的 `moresTime` / `moresValue` 相对 `resolverMessage` / `resolverPatLogs` 整体错位一位，评分与回放数据全部对不上。此外若该分支在 `resolverMoresTime` 仍为空时命中，`size() - 1 == -1`，`add(-1, x)` 直接抛 `IndexOutOfBoundsException`。

**触发条件**：某一组 `patKey` 以 `?` 开头且 `i >= 1`（用户拍发过程中做了「改错重发」）。

**建议**：169、170 两行改为 `set(size - 1, ...)`，与 167、168 保持一致。

---

### P1-2　普通组分支把 `moresTime` 写进 `resolverMoresValue`、`moresValue` 写进 `resolverMoresTime`

**位置**：`src/main/java/com/nip/common/utils/TickerPatUtils.java:246-248`（另有 3 处同样写法：`138-139`、`205-206`、`239-240`）

**现象**：字段语义是 `moresTime` = 电划耗时、`moresValue` = 0 点 1 划表示。正确映射在 `112-113`、`200-201`、`233-234` 三处（`times` → `resolverMoresTime`，`values` → `resolverMoresValue`）。但下面 4 处全部写反：

```java
resolverPatLogs.add(contentAddParam.getPatLogs()  != null ? ... : "[]");   // 246
resolverMoresValue.add(contentAddParam.getMoresTime()  != null ? ... : "[]"); // 247  Time -> Value
resolverMoresTime.add(contentAddParam.getMoresValue() != null ? ... : "[]");  // 248  Value -> Time
```

**影响**：`244-248` 这一支是「普通组」路径——`patKey` 既不是 4 位倍数的粘连串、也不含 `?`，是最常走的分支。也就是说绝大多数训练记录落库时，`moresTime` 与 `moresValue` 两列内容是对调的。下游按耗时做点划判定、按 0/1 做码型还原都会拿到对方的数据。这是持久化的数据错误，历史数据无法通过重跑修复。

**建议**：247、248 两行互换，并在 `138-139`、`205-206`、`239-240` 做同样修正；建议把「往四个 resolver 列表追加一组」抽成一个私有方法，从结构上消除写反的可能。

---

### P1-3　`patKeys` 过滤后未同步过滤 `userContents`，索引整体错位

**位置**：`src/main/java/com/nip/common/utils/TickerPatUtils.java:51`（消费点 `:58-59`）

**现象**：

```java
patKeys = patKeys.stream().filter(StringUtils::isNotBlank).toList();   // 51  只过滤 patKeys
...
for (int i = 0; i < patKeys.size(); i++) {
  PostTelegramTrainContentAddParam contentAddParam = userContents.get(i);  // 59  仍按原索引取
```

`patKeys` 被压缩了，`userContents` 没有。第 54-56 行的 `while (userContents.size() < patKeys.size())` 只补长度，不修正对齐。

**影响**：只要靠前的位置出现一个空白组，其后**所有**组拿到的 `patLogs` / `moresTime` / `moresValue` 都来自前一组，全量错位。评分和回放全错。

**建议**：过滤时保持两个列表同步——先按下标筛出保留位置集合，再同时裁剪 `patKeys` 与 `userContents`；或者不过滤，在循环里对空白 `patKey` 直接 `continue`（走 `249-255` 的空串分支）。

---

### P1-4　`groupScore` 用赋值覆盖而非累加，且除数 `(2+1)` 与 4 位分组不符

**位置**：`src/main/java/com/nip/common/utils/TickerPatUtils.java:116`

**现象**：

```java
scoreVO.setGroupScore(patKey.length() / (2 + 1) * rule.getLarge().getL());
```

两个问题：
1. 这行在 `for (int i = 0; i < patKeys.size(); i++)` 循环体内，用 `set` 而不是 `get() + ...` 累加。同方法内 `173` 行的 `alterError` 用的是 `scoreVO.setAlterErrorScore(scoreVO.getAlterErrorScore() + ...)`，写法不一致。多组时前面累计的组间隔扣分被最后一组直接覆盖。
2. `(2 + 1)` 即 `/3`，而紧邻上方 `88` 行的拆分逻辑是 `patKey.length() / 4`（4 位一组）。同一个字符串按 4 位切分却按 3 除来算组数，两者对不上。

**影响**：组间隔扣分算错（多页/多组时严重偏小），最终成绩失真。

**建议**：改为 `scoreVO.setGroupScore(scoreVO.getGroupScore() + (patKey.length() / 4 - 1) * rule.getLarge().getL())`，除数与分组宽度请与业务确认后统一为常量。

---

### P1-5　`ToolUtil.isIdFieldEmpty` 用 `==` 比较字符串，空 ID 判定失效

**位置**：`src/main/java/com/nip/common/utils/ToolUtil.java:84`

**现象**：

```java
Object value = getterMethod.invoke(object);
return value == null || value == "";     // 引用比较，不是内容比较
```

只有当 `value` 恰好是 JVM 字符串常量池里的那个 `""` 实例时才成立。任何运行时产生的空串（JSON 反序列化、`substring`、`new String("")`、数据库读出）都不是同一引用，比较结果恒为 `false`。

**影响**：唯一调用方是 `BaseRepository:17`：

```java
if (ToolUtil.isIdFieldEmpty(entity)) { entityManager.persist(entity); } else { ... }
```

项目实体主键大量是 `String` 类型（`BaseRepository<XxxEntity, String>`）。当上层传入一个 ID 为运行时空串的新实体时，`isIdFieldEmpty` 返回 `false`，走 merge 而不是 persist——要么静默地什么都没插入，要么以空串为主键插入脏数据。这是数据完整性问题。

**建议**：改为 `return value == null || "".equals(value)`；更稳妥的是 `value == null || (value instanceof CharSequence cs && cs.isEmpty())`。

---

### P1-6　`ToolUtil.calculateRate` 的零值守卫检查了错误的参数

**位置**：`src/main/java/com/nip/common/utils/ToolUtil.java:96-97`

**现象**：

```java
public static BigDecimal calculateRate(int min, int max, int total) {
  return min == 0 ? BigDecimal.ZERO
       : new BigDecimal(max).divide(new BigDecimal(total), 10, RoundingMode.HALF_UP)...;
}
```

守卫判的是 `min`，除数却是 `total`。三个参数名（min / max / total）与实际语义（分子是 `max`）也对不上。

**影响**：真实触发点在 `src/main/java/com/nip/service/general/GeneralTickerPatService.java:717-718`：

```java
errorInfoVO.setGroupGapMin(calculateRate(groupGapMin, groupGapMin, groupTotal));  // 717
errorInfoVO.setGroupGapMax(calculateRate(groupGapMin, groupGapMax, groupTotal));  // 718
```

对比同一段的 `709-716` 行——那 8 行首参传的都是对应的 `xxxTotal`，只有 717、718 传的是 `groupGapMin`。因此：
- `groupTotal == 0` 且 `groupGapMin != 0` 时，`BigDecimal.divide` 抛 `ArithmeticException: Division by zero`，整个统计接口 500；
- `groupGapMin == 0` 而 `groupGapMax > 0` 时（第 718 行），守卫命中返回 `ZERO`，`groupGapMax` 比率被**静默算成 0%**，报表数据错误且无任何异常。

**建议**：`ToolUtil.calculateRate` 的守卫改为 `total == 0 || max == 0`，参数改名为 `(int unusedOrCount, int numerator, int total)` 并去掉冗余参数。注意 `PatTrainStatisticsUtil.java:71-74` 已有一个逻辑正确的两参版本 `calculateRate(count, total)`（守卫是 `total == 0 || count == 0`），建议直接删除 `ToolUtil` 的三参版本、全部收敛到那个实现。`GeneralTickerPatService:717-718` 的错参需一并修正。

---

### P1-7　CI 的 ARM64 job 必然失败；`release` job 永远不会被触发

**位置**：`.github/workflows/build-quarkus-native.yml:48-50`、`:3-6` 与 `:88`

**现象一（arm64 构建必失败）**：第 48-50 行的条件是 `startsWith(matrix.os, 'ubuntu')`，会同时命中 `ubuntu-22.04 / x86_64` 和 `ubuntu-24.04 / arm64` 两个 matrix 条目，而命令里硬编码了 x86 指令集：

```yaml
- name: Build Native Binary (Linux glibc 2.28 baseline)
  if: startsWith(matrix.os, 'ubuntu')
  run: mvn -B -Pnative ... -Dquarkus.native.march=compatibility -Dquarkus.native.additional-build-args=-march=x86-64 package
```

在 `ubuntu-24.04-arm` runner 上，native-image 的 `-march` 在 aarch64 平台只接受 `armv8-a` / `compatibility` / `native`，传入 `x86-64` 会直接报不支持的架构而中止。此外同一条命令既通过 `-Dquarkus.native.march=compatibility` 又通过 `additional-build-args` 传 `-march=x86-64`，两个 `-march` 互相冲突。

**现象二（release 是死 job）**：

```yaml
on:
  push:
    branches: ['main', 'master']    # 只监听分支 push
  pull_request:
...
  release:
    needs: [build]
    if: startsWith(github.ref, 'refs/tags/')   # 只在 tag ref 下运行
```

`push` 事件一旦声明了 `branches` 过滤器，tag push 就不会触发该 workflow。因此 `github.ref` 永远是 `refs/heads/...`，`release` job 的 `if` 恒为 false，`softprops/action-gh-release` 从未执行过。

**影响**：3 个 matrix job 里有 1 个恒红（`fail-fast: false` 让它不阻塞其他 job，所以很容易被长期忽略）；发布流程完全没有生效，打 tag 不会产出 GitHub Release。

**建议**：
- `additional-build-args` 按 `matrix.arch` 区分，x86_64 用 `-march=x86-64`，arm64 去掉该参数（或用 `-march=armv8-a`），并且不要与 `-Dquarkus.native.march` 同时传；
- `on:` 增加 `tags: ['v*']`（`push.branches` 与 `push.tags` 可以并列），或把 `release` 拆到独立的 `on: push: tags:` workflow。

---

## P2（功能瑕疵 / 性能 / 可维护性）

### P2-1　`commons-collections` 被直接使用但 pom.xml 未声明，靠 `commons-beanutils` 传递引入

**位置**：`src/main/java/com/nip/common/utils/Assert.java:4`、`ObjectUtils.java:4`、`StringUtils.java:4`；`pom.xml:22`

**现象**：三个类都 `import org.apache.commons.collections.CollectionUtils;`（注意是 commons-collections **3.x** 的包名，不是 `collections4`）。`pom.xml` 的 `<dependencies>` 里没有 `commons-collections`。它来自 `commons-beanutils:1.9.4` 的 compile 依赖（已核对其 POM：`commons-collections:commons-collections:3.2.2` + `commons-logging:1.2`）。

**影响**：这是隐式依赖。而 `commons-beanutils` 在本项目中**只被一处使用**——`PojoUtils.java:146` 的 `merge(T, T)`，而 `merge` 全项目零调用（见 P2-9）。也就是说：为了修 `CVE-2025-48734`（commons-beanutils < 1.11.0，CVSS 8.8）而删除或升级 beanutils 时，`Assert` / `ObjectUtils` / `StringUtils` 会立刻编译失败，问题排查成本很高。

**建议**：显式声明 `commons-collections`（或更好：把这三处 `CollectionUtils.isEmpty/isNotEmpty` 换成已有的 `commons-lang3` / `hutool CollUtil`，彻底去掉 commons-collections 3.x 这个 2015 年的老库），然后删除 `commons-beanutils` 依赖及 `PojoUtils.merge`。

---

### P2-2　`fastjson.version` 属性存在但没有对应依赖，是误导性死配置

**位置**：`pom.xml:23`

**现象**：`<fastjson.version>1.2.78</fastjson.version>` 定义了，但 `<dependencies>` 中没有任何 `com.alibaba:fastjson`。全项目 `grep` 也没有任何 `com.alibaba.fastjson` 的 import。

**影响**：功能上无影响，但会让任何依赖扫描 / 人工安全审计误判本项目受 `CVE-2022-25845`（fastjson < 1.2.83，autoType 绕过导致远程反序列化 RCE，CVSS 8.1）影响。实际上本项目**不受该 CVE 影响**。

顺带说明：代码里被当作「fastjson」使用的其实是 `org.jose4j.json.internal.json_simple.JSONObject`（见 P2-3），两者毫无关系。

**建议**：删除 `pom.xml:23` 这一行。

---

### P2-3　WebSocket 全部消息序列化依赖 jose4j 的内部 shaded 包

**位置**：`src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java:22`、`WebSocketGeneralTelexPatService.java:22`、`WebSocketSimulationService.java:32`

**现象**：三个 WS 服务 `import org.jose4j.json.internal.json_simple.JSONObject;`。`org.jose4j` 是 `quarkus-smallrye-jwt` 的传递依赖，`.json.internal.` 这个包名明确表示是 jose4j 内部重打包的 json-simple 1.1，属于私有实现细节，不是公开 API，且 pom.xml 未声明 jose4j。

**影响**：
1. Quarkus 平台版本升级导致 jose4j 版本变化或调整 shading 时，这三个文件会直接编译失败或行为变化，且没有任何依赖声明能提示这层耦合；
2. json-simple 的 `toJSONString(Object)` 对非 `String`/`Number`/`Boolean`/`Map`/`List` 类型会走 `value.toString()` 且**不加引号**，产出非法 JSON。当前调用点传入的多为 `Map<String,String>`（如 `WebSocketGeneralKeyPatService.java:66-73`）不受影响，但 `WebSocketSimulationService.java:247-248` 往 `message` 里 `put(BODY, body)` 的 `body` 类型需要复核。〔INFERENCE：未逐一追踪所有 `body` 的运行时类型〕

**建议**：项目已经引入 `quarkus-rest-jackson` 和 `gson`（并有封装好的 `JSONUtils`），WS 序列化统一改用 `JSONUtils.toJson`，删除对 jose4j 内部包的引用。已同步告知负责 `ws/` 的 agent。

---

### P2-4　连接池 `max-size: 200` 大幅超过 MySQL 默认 `max_connections`

**位置**：`src/main/resources/application.yml:23-24`

**现象**：`max-size: 200`、`min-size: 20`（Quarkus/Agroal 默认分别是 50 和 0）。MySQL 8 服务端 `max_connections` 默认值是 151。

**影响**：一旦并发压力把池撑到 151 个以上，MySQL 会返回 `ER_CON_COUNT_ERROR: Too many connections`，表现为随机的连接获取失败而不是排队等待——即池的「限流」保护作用被架空了。同时 `min-size: 20` 意味着空闲期也长期占着 20 条连接。

**建议**：`max-size` 下调到与 MySQL 端 `max_connections` 协调的值（留出运维连接余量，例如服务端 200 / 应用端 100），或同步调大服务端 `max_connections` 并在部署文档中固化这对约束。

---

### P2-5　JDBC URL 里塞的是 HikariCP 参数，在 Agroal 下完全不生效

**位置**：`src/main/resources/application.yml:22`

**现象**：

```
jdbc:mysql://localhost:3306/project006?...&idleTimeout=60000&connectionTestQuery=SELECT 1
```

`idleTimeout` 和 `connectionTestQuery` 都是 **HikariCP / DBCP 的连接池参数名**，不是 MySQL Connector/J 的连接属性。而 Quarkus 用的池是 **Agroal**，不是 Hikari。Connector/J 会把这两个未知属性忽略掉（应用能正常启动即说明未被拒绝〔INFERENCE〕）。

**影响**：作者期望的「空闲连接 60 秒回收」和「借出前用 `SELECT 1` 校验」两个行为**一个都没有生效**。配合 P2-4 的 `min-size: 20`，长时间空闲后遇上 MySQL 的 `wait_timeout`（默认 8 小时）断链时，会出现「第一次请求必失败」的经典症状。

**建议**：从 URL 中删掉这两个参数，改用 Agroal 的正确配置键（已核对 Quarkus `DataSourceJdbcRuntimeConfig`）：

```yaml
quarkus:
  datasource:
    jdbc:
      idle-removal-interval: 60S
      validation-query-sql: SELECT 1
      background-validation-interval: 2M
```

---

### P2-6　日志 format 里 `%n` 位置错误，导致日志行粘连；`%l %F` 代价极高且信息重复

**位置**：`src/main/resources/application.yml:47`

**现象**：

```yaml
format: '%d{yyyy-MM-dd HH:mm:ss,SSS}  %h %N[%i] %-5p [%c{3.}] (%t) %s%e%n %l %F'
```

换行符 `%n` 在 `%l %F` **之前**，整个 pattern 末尾没有换行。

**影响**：
1. 每条日志渲染成「正文\n 位置信息 文件名」，而下一条日志紧接着 `%F` 之后开始 —— 日志文件里第 N 条的位置信息和第 N+1 条的时间戳会挤在同一行，破坏一切按行切分的日志采集/grep；
2. `%l`（调用位置 `类.方法(文件:行)`）和 `%F`（源文件名）都需要在每条日志上构造并遍历栈帧，是 JBoss LogManager 中开销最高的两个转换符，且 `%l` 的输出已经包含了 `%F` 的内容，`%F` 纯属重复；
3. 该 format 挂在 `quarkus.log.file` 下，`level: WARN`，虽然只对 WARN 以上生效，但异常高峰期正是最需要日志可读、也最扛不住栈帧开销的时候。

**建议**：改为 `'%d{yyyy-MM-dd HH:mm:ss,SSS} %h %N[%i] %-5p [%c{3.}] (%t) %s%e%n'`；确需定位信息时只保留 `%l` 且放到 `%n` 之前。

---

### P2-7　`quarkus.swagger-ui.enable` 在生产模式下不会暴露 Swagger UI

**位置**：`src/main/resources/application.yml:36-38`

**现象**：

```yaml
swagger-ui:
  enable: true
  theme: newspaper
```

Quarkus 官方文档明确：Swagger UI **默认只在 dev / test 模式可用**，要在生产（jar / native）中提供，必须设置构建期属性 `quarkus.swagger-ui.always-include=true`（且该属性运行时不可改）。`quarkus.swagger-ui.enable` 控制的是「是否把 Swagger UI 纳入构建」，不等于「在生产暴露」。

**影响**：打包部署后访问 `/q/swagger-ui` 得到 404，配置里的 `theme: newspaper` 也一并落空。作者的意图（内网环境暴露接口文档）没有实现。

**建议**：若确实要在内网生产环境提供接口文档，加上 `quarkus.swagger-ui.always-include: true`；若不需要，删掉这三行避免误解。

---

### P2-8　7.4 MB 静态资源放错目录：既不被 HTTP 提供、也无代码读取，却被显式打进 native 镜像

**位置**：`src/main/resources/resources/`（110 个已入库文件，7.4 MB）与 `src/main/resources/application.yml:55-57`

**现象**：Quarkus 提供 HTTP 静态资源的约定目录是 `src/main/resources/META-INF/resources/`。本项目该目录**不存在**，全部资源放在了 `src/main/resources/resources/`。有力证据是这批文件里的 `resources/index.html:322-324` —— 它是 Quarkus 脚手架自带的欢迎页，页面正文自己写着：

```html
<p>This page: <code>src/main/resources/META-INF/resources/index.html</code></p>
<p>Static assets: <code>src/main/resources/META-INF/resources/</code></p>
```

也就是说这批资源本来就该在 `META-INF/resources/` 下，是被整体挪错了位置。同时全项目 `grep` 确认没有任何代码用 `getResourceAsStream` / `getResource` 读取 `resources/` 下的内容（唯一的类路径资源读取是 `LifecycleApplication.java:26` 读 `banner.txt`）。

而 `application.yml:57` 又把它们全量塞进 native 镜像：

```yaml
native:
  resources:
    includes: resources/**
```

**影响**：
1. 这批文件（mermaid、ace editor、OAuth2 页面等 knife4j/OpenAPI 前端产物）**任何 URL 都访问不到**，如果部署时期望通过 `/docs` 打开接口文档，该功能是坏的；
2. 7.4 MB 死资源被无条件嵌入 native 二进制，白白增大产物体积和构建时间。

**建议**：确认这批资源是否还需要——需要就整体 `git mv` 到 `src/main/resources/META-INF/resources/`（此时 `quarkus.native.resources.includes` 也可以删掉，`META-INF/resources` 会被自动处理）；不需要就整个删除，同时删掉 `application.yml:55-57`。

---

### P2-9　`reflection-config.json` 是模板残留：类不存在，加载开关被注释，且即使解注释在 CI 下也不生效

**位置**：`src/main/resources/reflection-config.json:1-12`、`src/main/resources/application.yml:59`、`.github/workflows/build-quarkus-native.yml:50`

**现象**：

```json
[ { "name": "com.example.SourceObj", ... }, { "name": "com.example.TargetObj", ... } ]
```

`com.example.SourceObj` / `com.example.TargetObj` 在本项目中不存在（`com.nip.*` 才是本项目包名），是 Quarkus 脚手架示例的残留。加载它的构建参数被注释掉了：

```yaml
#    additional-build-args: -H:ReflectionConfigurationFiles=reflection-config.json
```

**影响**：
1. 当前状态下这个文件完全不参与构建，是纯误导——看到它的人会以为 native 反射配置已经在管控之中；
2. 如果有人为了补反射注册而解注释这一行，会遇到两个坑：`-H:ReflectionConfigurationFiles=` 后跟的是相对**构建工作目录**的文件系统路径（native-image 在 `target/` 下执行），不是类路径路径，找不到文件；而且即便路径对了，GraalVM 也会因为 `com.example.*` 无法解析而报错；
3. 更隐蔽的是：CI 在 `.github/workflows/build-quarkus-native.yml:50` 用命令行传了 `-Dquarkus.native.additional-build-args=-march=x86-64`，命令行属性优先级高于 `application.yml`，会**整个覆盖**掉 yml 里的 `additional-build-args`。也就是说即使解了注释，CI 构建里反射配置依然不会被加载，而本地构建会加载——本地和 CI 行为不一致。

**好消息**：目前 native 反射覆盖面并不靠这个文件——全项目有 151 个类标了 `@RegisterForReflection`，DTO 层基本覆盖到位。

**建议**：删除 `reflection-config.json` 和 `application.yml:59` 这行注释。确有需要注册的类，统一用 `@RegisterForReflection` 或 `quarkus.native.additional-build-args` 的 `--initialize-at-*` 系列，不要引入第二套机制。

---

### P2-10　`CustomPhysicalNamingStrategy` 的驼峰转换正则不处理数字边界与连续大写，并且无条件小写化会改写显式指定的标识符

**位置**：`src/main/java/com/nip/common/utils/CustomPhysicalNamingStrategy.java:37-48`（配置入口 `application.yml:33`）

**现象**：

```java
String name = identifier.getText();
String snakeName = name.replaceAll("([a-z]+)([A-Z]+)", "$1\\_$2").toLowerCase();
if (!snakeName.equals(name)) { return new Identifier(snakeName, identifier.isQuoted()); }
```

三个问题：

1. **数字边界不处理**：正则要求大写字母紧跟在小写字母后。`field1Name` 中 `1` 与 `N` 之间不匹配，结果是 `field1name`，而 Hibernate 社区惯例的 `CamelCaseToUnderscoresNamingStrategy` 会给出 `field1_name`。
2. **连续大写不处理**：`HTTPServer` 里没有「小写+大写」边界，结果是 `httpserver` 而不是 `http_server`；`userID` 能正确转成 `user_id`（因 `r` + `ID` 匹配），但 `IDCard` 转成 `idcard`。转换规则不自洽。
3. **无条件 `toLowerCase()`**：`toLowerCase()` 是在正则替换之后无条件执行的，因此哪怕实体上写了 `@Table(name = "SIM_ROOM")` 或 `@Column(name = "USER_ID")` 这种显式全大写名，也会被改写成 `sim_room` / `user_id`。在 Linux 上 MySQL 的 `lower_case_table_names` 默认为 0（表名大小写敏感），一旦库里真有大写表名，SQL 就会报 `Table doesn't exist`。同理，`identifier.isQuoted()` 为 true 的显式引用标识符本应原样保留，这里也被小写化了。
4. **`toLowerCase()` 未指定 Locale**：在 `tr_TR` 等 locale 下 `I` 会变成 `ı`（无点 i），`ID` → `ıd`。当前部署环境 locale 为 zh_CN 不会触发，但这是标准的 locale 敏感陷阱。

**影响**：只要实体的字段/表名出现「字母+数字+大写」或连续大写缩写，Hibernate 生成的列名就和数据库实际列名对不上，运行时报 `Unknown column`。属于「加字段时才炸」的隐雷。

**建议**：优先改用 Hibernate 自带的 `org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy`（Quarkus 直接支持 `quarkus.hibernate-orm.physical-naming-strategy` 指向它）。若必须自定义，至少要：(a) 补上数字与连续大写的边界规则；(b) 对 `identifier.isQuoted()` 为 true 的标识符直接原样返回；(c) `toLowerCase(Locale.ROOT)`。〔与 Persistence agent 已约定该文件由本报告负责〕

---

### P2-11　`PojoUtils` 的 `ignoreProperties` 形参声明了却从未传给 `CopyOptions`

**位置**：`src/main/java/com/nip/common/utils/PojoUtils.java:33`、`43`、`62`、`75`、`98`、`110`

**现象**：6 个公开方法都带 `String... ignoreProperties` 形参，但真正执行拷贝的两处是：

```java
BeanUtil.copyProperties(v, t, CopyOptions.create().setIgnoreError(true));       // 66
BeanUtil.copyProperties(v, entity, CopyOptions.create().setIgnoreError(true));  // 78
```

`CopyOptions` 从头到尾没有调用 `setIgnoreProperties(ignoreProperties)`，形参被完全丢弃。

**影响**：当前无实际损害——`grep` 确认没有任何调用方传入了忽略字段。但这是个高危 API：任何人写 `PojoUtils.convertOne(dto, UserVO.class, "password")` 都会得到「参数被接受、编译通过、字段照拷不误」的静默结果，敏感字段泄漏或不该覆盖的字段被覆盖都不会有任何报错。

**建议**：要么补上 `.setIgnoreProperties(ignoreProperties)`，要么把这 6 个方法的该形参全部删掉。二选一，不要留着。

---

### P2-12　`PojoUtils.convertOne` 把反射异常吞成裸 `NullPointerException`

**位置**：`src/main/java/com/nip/common/utils/PojoUtils.java:69-72`、`80-83`

**现象**：

```java
} catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
  log.error("convertOne error:{}", e.getMessage());
  throw new NullPointerException();      // 无 message、无 cause
}
```

**影响**：目标类缺少无参构造、构造器抛异常、或 native 模式下缺少反射注册时，抛出的是一个**没有 message 也没有 cause 的 NPE**。日志里只有 `e.getMessage()`（对 `NoSuchMethodException` 就是一个类名，对 NPE 类原因常常是 `null`），栈信息全丢。这个方法在全项目被大量调用（含 `WebSocketService.java:115/122` 等 WS 路径），一旦出问题排查成本极高。

**建议**：`throw new IllegalStateException("convertOne failed: " + clazz.getName(), e);`，并把 `log.error` 的第二参改成异常对象本身以保留栈。

---

### P2-13　`PojoUtils.averageAssign` 返回的是原列表的视图而非独立子列表

**位置**：`src/main/java/com/nip/common/utils/PojoUtils.java:134`

**现象**：`result.add(source.subList(start, end));`。`List.subList` 返回的是**背靠原列表的视图**，不是副本。

**影响**：
1. 任何对返回子列表的结构性修改会直接写回 `source`；反之 `source` 发生结构性修改后，所有已返回的子列表全部失效，再访问抛 `ConcurrentModificationException`；
2. 若 `source` 是 Hibernate 的 `PersistentBag`/`PersistentList`，这些视图会持有对持久化集合的引用，session 关闭后遍历触发 `LazyInitializationException`。

方法名「averageAssign（平均分配）」和 javadoc 都暗示返回的是独立分片，语义不符。

**建议**：`result.add(new ArrayList<>(source.subList(start, end)));`

---

### P2-14　`PojoUtils` 同一个类里混用两套参数顺序**相反**的 `copyProperties`

**位置**：`src/main/java/com/nip/common/utils/PojoUtils.java:66/78` 与 `:146`

**现象**：
- hutool：`BeanUtil.copyProperties(Object source, Object target, CopyOptions)` —— **源在前**（66、78 行用法正确）；
- commons-beanutils：`BeanUtils.copyProperties(Object dest, Object orig)` —— **目标在前**（146 行 `merge(user1, user2)` 实际是把 user2 拷进 user1）。

两个语义完全相反的 API 出现在同一个 34 行的类里，方法名还叫 `merge(user1, user2)` 这种不体现方向的名字。

**影响**：`merge` 全项目零调用，当前无实际损害。但只要有人凭直觉按「源, 目标」调用它，数据就会朝反方向覆盖，且不会有任何异常。这也是 P2-1 里那条「只为一个死方法背了个 CVSS 8.8 的依赖」的根源。

**建议**：删除 `merge` 方法与 `commons-beanutils` 依赖（先按 P2-1 处理好 commons-collections）。

---

### P2-15　`PasswordUtil.decryptPassword` 对空密码/无分隔符的密文会数组越界

**位置**：`src/main/java/com/nip/common/utils/PasswordUtil.java:54`

**现象**：

```java
return linked.split("\\t")[1];
```

加密侧是 `account + "\t" + password`（第 30 行）。`String.split` 会丢弃尾部空串，所以：
- `password` 为空串时，明文是 `"account\t"`，`split` 返回长度为 1 的数组 → `[1]` 抛 `ArrayIndexOutOfBoundsException`；
- 密文被篡改或用错密钥而恰好解密出不含 `\t` 的内容时，同样越界；
- 反过来，若 `password` 本身含 `\t`，`split` 会切成 3 段以上，只返回第一个 `\t` 到第二个 `\t` 之间的片段，**密码被静默截断**，导致解出来的密码与原密码不一致。

**影响**：`PasswordUtil` 目前全项目**没有任何调用方**（登录走的是 `MD5Util` + `AESUtil`，见 `UserService.java:370/383`），所以是潜在缺陷而非现网故障。

**建议**：改为 `int i = linked.indexOf('\t'); if (i < 0) throw new IllegalArgumentException(...); return linked.substring(i + 1);`。或者直接删除这个未被使用的类（见 P2-17）。

---

### P2-16　`ToolUtil` 的三个工具方法边界处理有缺陷

**位置**：`src/main/java/com/nip/common/utils/ToolUtil.java:53-62`、`43-51`、`110-117`

1. **`objToList` 只识别 `ArrayList`，其他 `List` 实现一律返回 `null`**（53-62 行）：判断条件是 `obj instanceof ArrayList<?>`。`List.of(...)`、`Arrays.asList(...)`、Hibernate 的 `PersistentBag`、Jackson 在某些配置下产出的集合都不是 `ArrayList`，会走到 `return null`。调用方 `UserController.java:84` 直接把结果传给 `userService.addUserRole(...)`，一个 `null` 就会往下游传播。另外方法开头 `new ArrayList<>()` 分配了对象却在返回 null 的路径上被丢弃。建议改成 `instanceof List<?>` 并在不匹配时返回 `Collections.emptyList()`。
2. **`assembleData` 不校验数组长度**（43-51 行）：`resultList.getFirst()[0..2]`，若原生查询列数不足 3 会抛 `ArrayIndexOutOfBoundsException`。
3. **`calculateTS` 除零无守卫**（110-117 行）：除数是 `max + min + per`，三者和为 0 时 `BigDecimal.divide` 抛 `ArithmeticException`。目前 5 个调用点（`PostTelegramTrainService.java:927/931/935/939/944`）都在外层用 `if (... != 0)` 挡住了，但这个保护是**调用方的**，工具方法本身没有契约保证，新增调用点极易漏掉。

---

### P2-17　大量死代码：整文件注释、零调用工具类、重复实现

**位置**：见下表

| 文件 / 位置 | 状态 |
|---|---|
| `common/utils/CharsetUtils.java:1-59` | **整个类被注释掉**，第 1 行是 `package com.nip.common.utils;//package com.nip.common.utils;`，编译产物为空。两个调用点 `PostEnteringExerciseWordStockService.java:77`、`TheoryKnowledgeQuestionService.java:186` 也是注释 |
| `common/utils/SnowflakeIdKit.java` | 全项目零调用 |
| `common/utils/GZipUtil.java` | 全项目零调用；`main` 里 `109-134` 行还用 `new byte[in.available()]` + 单次 `read()` 读文件（`available()` 不保证等于文件大小，`read` 不保证读满） |
| `common/utils/PingYinUtil.java` | 全项目零调用，只有一个 `main` |
| `common/utils/PasswordUtil.java` | 全项目零调用（见 P2-15） |
| `common/utils/MapTypeAdapter.java:19` | `FACTORY` 从未注册到任何 `GsonBuilder`（`JSONUtils.java:13-16` 只注册了 `LocalDateTimeAdapter`） |
| `common/utils/NoEscapeStringSerializer.java` | 从未注册，零调用 |
| `common/utils/ArraysSafeUtils.java` | 与 `ArraySafeGetUtils.java` 功能重叠（都是「按下标安全取值」），前者零调用 |
| `common/utils/ToolUtil.java:96` | 与 `PatTrainStatisticsUtil.java:71` 的 `calculateRate` 重复实现，且是有缺陷的那个（见 P1-6） |
| `pom.xml:76-84` | `quarkus-awt` / `quarkus-poi` 依赖被整块注释 |

**影响**：可维护性。特别是 `CharsetUtils` 这种「文件在、类不在」的形态，IDE 里搜索不到符号但文件列表里有，非常容易误导。

**建议**：整体删除。

---

### P2-18　`JWTInterceptor` 三处每请求 `new ObjectMapper()`

**位置**：`src/main/java/com/nip/common/interceptor/JWTInterceptor.java:63`、`72`、`80`

**现象**：每个鉴权失败的请求都 `new ObjectMapper().writeValueAsString(mp)`。

**影响**：`ObjectMapper` 构造开销大（要初始化序列化器缓存、模块注册等），且被设计为线程安全的可复用单例。在鉴权失败集中出现时（例如前端 token 过期后的重试风暴）会成为额外 CPU 与 GC 压力。Quarkus 本身已经提供了可注入的 `ObjectMapper` bean。

**建议**：`@Inject ObjectMapper objectMapper;`，或者干脆用手工拼接的固定 JSON 字符串（只有 code/message 两个字段）。

---

### P2-19　`JWTInterceptor` 在直接写响应后又 `return null`，造成响应双写

**位置**：`src/main/java/com/nip/common/interceptor/JWTInterceptor.java:63-64`、`72-73`、`80-81`

**现象**：

```java
response.send(new ObjectMapper().writeValueAsString(mp));   // 直接把 Vert.x 响应写完并结束
return null;                                                 // 同时让被拦截方法「返回 null」
```

`HttpServerResponse.send(...)` 会写入并 **end** 响应。而 `@AroundInvoke` 返回 `null` 会让 JAX-RS 层认为资源方法返回了 null，继续走它自己的响应写出流程（对非 void 方法通常是 204 No Content）。

**影响**：同一个请求上出现两次响应写出，第二次会因响应已结束而抛 `IllegalStateException`（"Response has already been written" 一类），在日志里堆积异常噪声，也可能干扰连接复用。〔INFERENCE：具体异常类型取决于 RESTEasy Reactive 版本，但双写这一事实由代码本身可证〕

**建议**：不要在拦截器里手写 Vert.x 响应。改为抛出 `WebApplicationException`（携带构造好的 `jakarta.ws.rs.core.Response`），交给 JAX-RS 统一写出；或者把鉴权改成标准的 `ContainerRequestFilter` + `requestContext.abortWith(...)`。

---

### P2-20　`JWTInterceptor` 异常分支返回的类型与被拦截方法的返回类型不兼容

**位置**：`src/main/java/com/nip/common/interceptor/JWTInterceptor.java:82-85`

**现象**：

```java
} catch (Exception exception) {
  log.error(...);
  return ResponseResult.error(ResponseCode.SYSTEM_ERROR, exception.getMessage(), exception.getMessage());
}
```

`@AroundInvoke` 的返回值会被当作被拦截方法的返回值。这里无条件返回 `com.nip.common.response.Response`。

**影响**：`@JWT` 标注在 53 个 controller 类上（类级别绑定，覆盖类中所有方法）。其中 `TheoryKnowledgeQuestionController.java:89` 的 `exportTemplate(HttpServerResponse response)` 返回类型是 `void`；对 void 方法返回非 null 值，CDI 拦截器规范要求返回 null，容器会抛 `IllegalStateException`。其他任何返回类型不是 `Response` 的方法（如返回 `String`、`List<X>`）也会在赋值时 `ClassCastException`。

**影响面**：只在 catch 分支触发，即「拦截器自身抛异常时」，因此平时看不到；但那正是最需要拿到真实错误的时刻，结果被一个类型错误盖住。

**建议**：catch 里改为 `throw new WebApplicationException(...)`（配合 P2-19 的整改方向），不要用返回值传递错误。同时 `log.error(...)` 应把 `exception` 作为最后一个参数传入以保留栈——现在的写法（`log.error("...{}.{}\n", simpleName, methodName)`）把异常整个丢了。

---

### P2-21　`JWTInterceptor` 手写 CORS 头与 `quarkus.http.cors` 配置冲突

**位置**：`src/main/java/com/nip/common/interceptor/JWTInterceptor.java:46-53` 与 `src/main/resources/application.yml:11-15`

**现象**：`application.yml` 已经开启了 Quarkus 内置 CORS 过滤器（`origins: '*'`、`methods: OPTIONS, GET, POST, DELETE, PUT, PATCH`）。拦截器又在每个请求上重新设置了一遍同名响应头，且内容不一致：

- `Access-Control-Allow-Methods: POST,OPTIONS,PUT,HEAD,DELETE` —— **缺 GET 和 PATCH**；
- `Access-Control-Allow-Origin` 被改成回显请求 Origin；请求无 `Origin` 头时（curl、服务端调用、WS 握手）`putHeader(name, null)` 在 Vert.x `HeadersMultiMap.set0` 中的语义是**移除该头**（已核对 Vert.x 4.5 源码，不会抛 NPE），行为与 CORS 过滤器再次不一致；
- `51-53` 行对 OPTIONS 请求只是 `setStatusCode(200)`，**没有 return**，会继续往下做 token 校验并 `response.send` 一个错误体。

**影响**：两个组件争抢同一批响应头，最终生效的取决于执行顺序，行为不可预期且难以复现调试。预检请求通常已被 Quarkus CORS 过滤器提前处理，所以拦截器里那套逻辑多数时候是死代码；真正落到实际响应上的 `Allow-Methods` 缺 GET，在少数浏览器场景下会造成困惑。

**建议**：删除拦截器里的全部 CORS 头设置（46-53 行），CORS 统一由 `quarkus.http.cors` 负责。

---

### P2-22　`docs/guides/code.java` 是未纳入版本控制的游离代码，且是生产逻辑的过期副本

**位置**：`docs/guides/code.java:1-201`

**现象**：
- 文件在 `docs/` 下，不在任何 source root，**不参与编译**；
- 无 `package` 声明，类名是小写的 `code`；
- `git ls-files docs/guides` 返回空 —— 该文件（连同同目录的 `handleMessageBody.MD`）**未被 git 跟踪**；
- 内容是 `TickerPatUtils.handleMessageBody`（`src/main/java/com/nip/common/utils/TickerPatUtils.java:270`）的一份拷贝。

**影响**：生产逻辑被复制到一个不编译、不入库、不会被任何工具检查的位置。它无法随 `TickerPatUtils` 一起演进，读到它的人可能误以为是当前实现；同时 `handleMessageBody.MD`（3000+ 行的用例说明）也无人维护。这属于典型的「知识在仓库外腐烂」。

**建议**：如果是设计文档的一部分，把代码片段内联进 `handleMessageBody.MD` 并注明「对应 `TickerPatUtils.java:270`，仅供说明，以源码为准」，同时把两个文件正式 `git add`；如果是临时草稿，直接删除。

---

### P2-23　CI 缓存 key 在两个 Linux 架构 job 之间冲突

**位置**：`.github/workflows/build-quarkus-native.yml:36-42`

**现象**：

```yaml
key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
```

`runner.os` 对 `ubuntu-22.04`（x86_64）和 `ubuntu-24.04-arm`（arm64）都是 `Linux`，两个并行 job 用完全相同的 key 保存缓存。

**影响**：`actions/cache` 对已存在的 key 保存会失败并打警告，后跑完的那个 job 的缓存被丢弃；两个架构共用同一份 `~/.m2` 缓存，虽然多数 Maven 构件是平台无关的，但带 native classifier 的构件会互相污染。

**建议**：`key: ${{ runner.os }}-${{ matrix.arch }}-maven-${{ hashFiles('**/pom.xml') }}`，`restore-keys` 同步加上 `${{ matrix.arch }}`。另外 `upload-artifact` 的 `path` 里 `target/*-runner*` 已经覆盖了 `target/*-runner.exe`，第二行冗余（`:80-81`）。

---

### P2-24　`.gitignore` 忽略了 `.idea`，但 `.idea/` 下已有 6 个文件入库

**位置**：`.gitignore:17`

**现象**：`.gitignore` 第 17 行有 `.idea`，但 `git ls-files .idea` 返回 6 个已跟踪文件（`compiler.xml`、`encodings.xml`、`jarRepositories.xml`、`misc.xml`、`vcs.xml`、`.gitignore`）。`.gitignore` 对已跟踪文件不生效。

**影响**：IDE 个人配置（含 `compiler.xml` 里的注解处理器路径、`jarRepositories.xml` 里的仓库地址）随仓库分发，不同开发者之间会反复产生无意义 diff 和冲突。

**建议**：`git rm -r --cached .idea` 后提交一次；若团队约定要共享部分 IDE 配置，则把 `.gitignore` 改成白名单形式（`.idea/*` + `!.idea/codeStyles/`）而不是留着当前这种自相矛盾的状态。

---

## P3（提示性）

### P3-1　多个工具类残留 `main` 方法，其中两个含真实凭据样本

**位置**：`AESUtil.java:95-96`（空 main）、`MD5Util.java:38-40`、`PasswordUtil.java:57-60`、`PingYinUtil.java:14`、`GZipUtil.java:109-134`

`PasswordUtil.java:58-59` 的 main 里写着 `encryptPassword("18623090141", "123456")` 和一串真实格式的密文——手机号 + 弱口令的组合，即使是内网也不该留在源码里。`AESUtil.java:95-96` 的 main 是空方法体，纯粹是删残了。建议全部删除。

### P3-2　`DateTimeUtil.currentTimeMillis()` 的时区转换是空操作

**位置**：`src/main/java/com/nip/common/utils/DateTimeUtil.java:80-82`

```java
return Instant.now().atZone(UTC_PLUS_8).toInstant().toEpochMilli();
```

`Instant` → `ZonedDateTime` → `Instant` 是恒等变换，等价于 `System.currentTimeMillis()`。时间戳本身没有时区概念，这里的 `atZone` 只会让读者误以为返回的是「东八区的毫秒数」。建议直接 `return System.currentTimeMillis();` 或删掉该方法。

同文件第 9-10 行同时定义了 `UTC_PLUS_8` 和 `SHANGHAI` 两个时区常量，`SHANGHAI` 零使用；注释「同样表示东八区」在处理 1991 年以前的历史日期时不成立（`Asia/Shanghai` 有历史夏令时）。

### P3-3　`LifecycleApplication` 的 banner 输入流未关闭，且 `banner.txt` 在 native 模式下取不到

**位置**：`src/main/java/com/nip/common/LifecycleApplication.java:26-34`

1. `InputStream is = ...getResourceAsStream("banner.txt")` 从未 `close()`，也没用 try-with-resources。启动时一次性泄漏一个流，影响可忽略但写法应修正；
2. `application.yml:55-57` 的 `quarkus.native.resources.includes` 只包含 `resources/**`，**不含 `banner.txt`**；同时 `quarkus.banner.enabled: false`（`application.yml:7`）意味着 Quarkus 的 BannerProcessor 也不会自动把 `banner.txt` 注册进 native 镜像。因此 native 产物里 `getResourceAsStream("banner.txt")` 返回 null，banner 被静默跳过（第 27 行的 `if (is != null)` 兜住了，不会报错）。JVM 模式正常。

**建议**：用 try-with-resources；若希望 native 下也打印 banner，把 `banner.txt` 加进 `quarkus.native.resources.includes`。

### P3-4　`ArraySafeGetUtils` / `ArraysSafeUtils` 的 `get(list, index, consumer)` 缺 null 检查

**位置**：`ArraySafeGetUtils.java:35-39`、`ArraysSafeUtils.java:61-65`

两个类里的 `get(List, int, Consumer)` 都直接 `list.size()`，`list` 为 null 时抛 NPE。而 `ArraysSafeUtils.getElement`（23-31、44-52 行）是有 null 检查的，同一个类里两种风格不一致。类名叫「Safe」却在某些重载上不安全，容易被误信。建议统一补上 `list == null` 判断。

---

## 附录：已接受安全风险（内网部署口径，不计入上述问题数）

沿用 `docs/reviews/2026-08-15-situation-display-orbit-placard-review.md` 第 1 节的口径记录，不计入 P0/P1/P2。

### A-1　`commons-beanutils 1.9.4` —— **CVE-2025-48734**（CVSS 8.8，CWE-470 不安全反射）
`PropertyUtilsBean` 的默认 `BeanIntrospector` 允许通过 `declaredClass` 属性访问到类加载器，构成远程代码执行链的入口。修复版本为 **1.11.0**。
**本项目触发面**：`commons-beanutils` 只在 `PojoUtils.java:146` 的 `merge` 方法中使用，而 `merge` 全项目零调用，攻击者无法通过任何 HTTP/WS 入口把外部可控的属性名喂给 `BeanUtils.copyProperties`。**当前不可触发。**
**处置建议**：直接删除该依赖（需先按 P2-1 处理 commons-collections 的隐式引入）。

### A-2　`hutool-all 5.8.12` —— **CVE-2023-24163**（CVSS 9.8，SQL 注入）与 **CVE-2023-42278**（CVSS 7.5，缓冲区溢出 / DoS）
- CVE-2023-24163：`< 5.8.21`，通过 aviator 模板引擎执行任意代码。**本项目未使用 hutool 的 aviator/db 模块**，不可触发。
- CVE-2023-42278：`< 5.8.22`，`JSONUtil.parse()` 的缓冲区溢出。**本项目 JSON 解析全部走 Gson**（`common/utils/JSONUtils.java`），未使用 `hutool-json`，不可触发。
- hutool 实际使用面：`BeanUtil`、`CollUtil`、`CharSequenceUtil`、`ObjectUtil`、`DateUtil`、`Assert` 等纯工具方法。
**处置建议**：升级到 5.8.22+ 属于低成本的例行卫生动作，这些工具方法在 5.8.x 内没有破坏性变更。

### A-3　`fastjson 1.2.78` —— **CVE-2022-25845**（CVSS 8.1，autoType 绕过导致反序列化 RCE，修复版 1.2.83）
**本项目不受影响**：`pom.xml:23` 只有一个 `<fastjson.version>` 属性，`<dependencies>` 中没有 fastjson，代码里也没有任何 `com.alibaba.fastjson` 引用。该属性纯属误导（已在 P2-2 记录）。

### A-4　`commons-codec 1.13` —— 无已知 CVE
经核对，1.13 **本身就是** `sonatype-2012-0050`（CODEC-134，Base32 会把部分非法输入解码成任意值）的修复版本，`< 1.13` 才受影响。本项目仅在 `AESUtil.java:3` 使用其 `Base64`。**无需处置**，但 1.13 发布于 2019 年，可顺手升到 1.17+。

### A-5　`commons-collections 3.2.2`（隐式传递引入）
即经典反序列化 gadget chain 库。3.2.2 已加入 `InvokerTransformer` 等危险 functor 的反序列化开关保护，无未修复 CVE。本项目仅用其 `CollectionUtils.isEmpty/isNotEmpty`，且不做 Java 原生反序列化。风险可接受，但见 P2-1 的构建脆弱性问题。

### A-6　`quarkus.http.cors` 全开
`application.yml:11-15`：`origins: '*'`、`headers: '*'`。内网部署可接受。注意与 `JWTInterceptor` 手写 CORS 头的冲突已作为 P2-21 记录（那是功能一致性问题，不是安全问题）。

### A-7　数据库凭据与密钥硬编码
- `application.yml:17-18`：`username: root` / `password: root` 明文写在版本控制里，且用的是 MySQL 超级用户；
- `AESUtil.java:20`：`UKDAI_AES_KEY` 硬编码；
- `PasswordUtil.java:14`：`PASSWORD_AES_KEY` 硬编码。

### A-8　口令与令牌的密码学强度
- `UserService.java:224/314/370/438/446`：口令用 **无盐 MD5**（`MD5Util.java`），彩虹表可直接反查；`UserService.java:571` 还有把口令重置为 `"123456"` 的路径；
- `UserService.java:383`：登录令牌是 `AESUtil.encrypt(账号 + "-" + 明文口令 + "-" + 设备号, UKDAI_AES_KEY)`。这意味着**明文口令被可逆加密后作为 token 在网络上传输并落库**；
- `AESUtil.java:30`：使用 `AES/ECB/PKCS5Padding`。ECB 模式无 IV，相同明文块产生相同密文块，且整体是确定性加密——同账号同口令每次登录的 token 完全一致，无法区分重放。

内网口径下这些均记为已接受风险。若后续有外网暴露计划，A-7 / A-8 需要优先重做（口令改 bcrypt/Argon2，token 改项目已引入但未使用的 `quarkus-smallrye-jwt`）。

---

## 附：本次审查未发现问题的部分

以下文件通读后未发现符合 P0/P1/P2 标准的缺陷，记录以说明覆盖范围：
`common/response/Response.java`、`common/response/ResponseResult.java`、`common/PageInfo.java`、`common/MainApplication.java`、`common/utils/CheckUtils.java`、`common/utils/StreamUtils.java`、`common/utils/LocalDateTimeAdapter.java`、`common/interceptor/JWT.java`、`common/exception/*`、`src/main/resources/banner.txt`。

其中 `LocalDateTimeAdapter` 使用 `yyyy-MM-dd HH:mm:ss` 而 REST 层的 Jackson 默认走 ISO-8601，两者格式不同——但由于 Gson 只用于数据库 JSON 字段的读写、Jackson 只用于 HTTP 出入参，两条路径不交叉，暂不构成缺陷。若将来出现「Jackson 序列化的 JSON 交给 Gson 反序列化」的场景，`LocalDateTime.parse` 会因带 `T` 分隔符而抛 `DateTimeParseException`，届时需要统一格式。
