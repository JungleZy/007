# Fix Spec Follow-Up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 `docs/reviews/2026-08-28-fix-spec-remediation.md` 中仍有源码证据的后续工作，消除测试误连/毁库风险、评分静默降级、ID 时钟回拨、统计契约漂移、动态及格线错误、主要 N+1/无效分配和安全可删的死代码，并重新生成可复核的迁移演练证据。

**Architecture:** 先关闭测试数据库和错误处理两条安全边界，再处理独立的 ID/排序契约；随后在同一文件域内依次修动态成绩分档和理论知识查询批处理，最后清理跨文件分配/N+1 与零调用代码。迁移演练依赖测试地基切换，使用一次性 MySQL 8.0 容器和两个历史快照，不接触配置中的 `project006` 或真实生产地址。

**Tech Stack:** Quarkus 3.20.4、Java 21、Hibernate ORM Panache、MySQL 8.0、JUnit 5、Quarkus Test、Testcontainers、Docker。

## Global Constraints

- 所有 REST 端点保持 HTTP 200 + 业务码信封契约；异常 Mapper 语义不变。
- 不新增第三方依赖；只使用现有 Quarkus/JDK/Testcontainers/JUnit API。
- 每个行为修复必须 RED→GREEN；配置、脚本和纯删除也必须有可观察验收，不能用源码文本断言代替行为测试。
- 每个任务只修改列出的文件域；同一文件域任务串行执行。
- `JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify` 只在单任务 review 通过后和最终阶段运行；基线为 112 tests、0 failures/errors/skips。
- 事务写路径不得吞异常；损坏 JSON 必须抛出并由现有事务回滚。
- 禁止删除 `SnowflakeIdKit`：它在 `WebSocketUnionService.addRoom` 中有真实调用。
- 公共空壳端点 `upLoadFile`、`exportTemplate`、`exportQuestionByLevelId` 本计划不删除；仓库外调用方未知，先记录产品决策。

---

## Phase 0: Documentation Discovery and Locked Decisions

### Allowed APIs and copy sources

- Quarkus 3.20.3 官方文档（与项目 3.20.4 同一维护线）：显式 `quarkus.datasource.jdbc.url` 会选择外部数据库并阻止 datasource Dev Services；`quarkus.datasource.devservices.image-name` 可固定容器镜像。来源：`/quarkusio/quarkus/3.20.3` 的 `getting-started-dev-services.adoc`、`datasource.adoc`。
- Hibernate ORM schema 策略使用 `quarkus.hibernate-orm.database.generation=drop-and-create|none|validate`；物理命名策略继续使用 `quarkus.hibernate-orm.physical-naming-strategy`。来源：同版本 `hibernate-orm.adoc` 与项目 `application.yml:25-33`。
- 项目既有批量模式：`UserDao.queryByIdIn(Set<String>)`、`DeviceDescriptionDao.findByDeviceIdInOrderByCreateTimeAsc`、`DeviceDao.countGroupByDeviceTypeId`。
- 项目既有 JSON 错误模式：`TickerPatUtils.handleMessageBody` 对每个字段单独 catch，抛 `IllegalStateException(field + index, cause)`；错误原文最多回显 96 字符。
- 项目既有动态成绩阈值：`TheoryKnowledgeExamService.examineAnalyse` 使用 `(total - passMark) / 2 + passMark`，`RoundingMode.DOWN`。
- 项目既有测试替身：手写 DAO 子类/固定数据，不引入 Mockito。

### Locked decisions

1. **测试数据库：** 采用 spec 对齐的 DevServices clean cutover。默认 profile 不再持有 URL；`%dev` 和 `%prod` 显式保留当前本地 MySQL 连接；`%test` 固定 `mysql:8.0`、数据库名 `project006_test`。删除 `MySqlResource` 和 25 个重复注解。
2. **统计列表：** `TelegramTrainService.statisticalPage` 输出统一为显式升序 `[0,1,2]`，与三个兄弟服务一致。后端仓库没有 `[2,0,1]` 的客户端契约证据。
3. **Snowflake：** 保持 64 位布局、epoch、machine/data-center 位不变；采用可注入 `LongSupplier` 的单 JVM单调逻辑时钟。时钟回拨时不抛错、不生成重复/倒序 ID；序列耗尽时逻辑毫秒 `lastStamp + 1`，不无限等待物理时钟。
4. **成绩分布：** 保留响应 map 的历史 key `59/60/81` 作为类别标识，语义改为“不及格/及格/良好”，避免破坏客户端字段；每条成绩按其考试自己的 `passMark/total` 计算边界。
5. **死代码：** 仅删除当前源码和测试零引用的内部方法/类、实验控制器、注释块和无用注入。公共业务 REST 空壳保留并写入决策清单。
6. **迁移演练：** `project006.sql` 和 `project006-base.sql` 都跑；前者验证当前生产近似状态，后者验证 `general_key_pat_page.id int → varchar(64)` 真正转换。

### Anti-pattern guards

- 不得用 `%test.quarkus.datasource.jdbc.url: ""` 模拟“未设置”；DevServices 要求 URL 属性不存在。
- 不得同时保留显式 `MySqlResource` 和 DevServices。
- 不得在 JSON catch 中返回空集合或默认满分。
- 不得用 `Collections.swap`、sort 后 rotate 或 DB 自然顺序表达统计顺序。
- 不得通过 Mockito/PowerMock 修改 `System.currentTimeMillis()`。
- 不得把 Snowflake 回拨“修复”为无限 sleep/busy-spin 或允许重复 ID。
- 不得删除仓库外契约未知的公共端点。
- 不得对配置中的 `jdbc:mysql://localhost:3306/project006` 或 dump 头中的 `10.10.0.212:3306` 执行迁移。

---

## Phase 1: Safety Boundaries

### Task 1: Cut test database ownership over to Quarkus DevServices

**Files:**
- Modify: `src/main/resources/application.yml:16-74`
- Modify: `pom.xml:191-200`
- Modify: `src/test/java/com/nip/SmokeTest.java`
- Modify: 25 `@QuarkusTest` files listed in Appendix A
- Delete: `src/test/java/com/nip/testsupport/MySqlResource.java`

**Interfaces:**
- Consumes: Quarkus datasource DevServices configuration.
- Produces: `%test` effective JDBC URL pointing to database `project006_test`; no test annotation can redirect to the configured local schema.

- [ ] **Step 1: Add the failing datasource ownership assertion**

Add to `SmokeTest` before changing configuration:

```java
@Inject
org.eclipse.microprofile.config.Config config;

@Test
void testProfileUsesDedicatedDevServiceDatabase() {
  String jdbcUrl = config.getValue("quarkus.datasource.jdbc.url", String.class);
  assertTrue(jdbcUrl.contains("/project006_test"), jdbcUrl);
  assertFalse(jdbcUrl.contains("localhost:3306/project006"), jdbcUrl);
}
```

- [ ] **Step 2: Run RED against the explicit resource**

Run:

```bash
JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B -Dtest=SmokeTest test
```

Expected: `testProfileUsesDedicatedDevServiceDatabase` fails because `MySqlResource` creates database `test`, not `project006_test`.

- [ ] **Step 3: Move concrete credentials out of the default profile**

Reshape `application.yml` so the default datasource keeps only shared type/pool settings. Add exact profile blocks:

```yaml
quarkus:
  datasource:
    db-kind: mysql
    jdbc:
      max-size: 200
      min-size: 20

"%dev":
  quarkus:
    datasource:
      username: root
      password: root
      jdbc:
        url: jdbc:mysql://localhost:3306/project006?rewriteBatchedStatements=true&characterEncoding=utf8&useSSL=true&allowPublicKeyRetrieval=true&idleTimeout=60000&connectionTestQuery=SELECT 1

"%test":
  quarkus:
    datasource:
      devservices:
        enabled: true
        image-name: mysql:8.0
        db-name: project006_test
    hibernate-orm:
      database:
        generation: drop-and-create
    http:
      test-port: 18081

"%prod":
  quarkus:
    datasource:
      username: root
      password: root
      jdbc:
        url: jdbc:mysql://localhost:3306/project006?rewriteBatchedStatements=true&characterEncoding=utf8&useSSL=true&allowPublicKeyRetrieval=true&idleTimeout=60000&connectionTestQuery=SELECT 1
    hibernate-orm:
      database:
        generation: validate
```

Keep `CustomPhysicalNamingStrategy` in the shared/default block.

- [ ] **Step 4: Preserve the Docker API compatibility workaround**

Move the current `System.setProperty("api.version", "1.44")` responsibility into Surefire:

```xml
<systemPropertyVariables>
  <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
  <maven.home>${maven.home}</maven.home>
  <api.version>1.44</api.version>
</systemPropertyVariables>
```

- [ ] **Step 5: Remove the explicit resource cleanly**

Delete `MySqlResource.java`. Remove these two imports and the annotation from every Appendix A class:

```java
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;

@QuarkusTestResource(MySqlResource.class)
```

Use an AST-aware rewrite; do not partially migrate the 25 classes. Keep Testcontainers dependencies because Task 9 uses them for migration rehearsal.

- [ ] **Step 6: Run GREEN and the full test foundation**

Run:

```bash
JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B -Dtest=SmokeTest test
JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify
```

Expected: DevServices log shows MySQL `mysql:8.0`; SmokeTest 2/2; full suite remains green. Capture the effective JDBC URL with credentials redacted.

- [ ] **Step 7: Static anti-pattern check and commit**

Verify no Java test references `MySqlResource` or `@QuarkusTestResource`, and the default YAML datasource contains no concrete JDBC URL. Commit:

```bash
git add pom.xml src/main/resources/application.yml src/test/java
git commit -m "test: 测试数据库切换为 DevServices"
```

### Task 2: Reject all remaining corrupt ticker JSON instead of awarding empty/full-score results

**Files:**
- Modify: `src/main/java/com/nip/common/utils/TickerPatUtils.java:67-93,514-542`
- Modify: `src/test/java/com/nip/common/utils/TickerPatUtilsTest.java`
- Modify: `src/test/java/com/nip/common/utils/TickerPatUtilsCharacterizationTest.java`
- Modify: `src/test/java/com/nip/service/PostTelegramTrainServiceTest.java`
- Create: `src/test/java/com/nip/service/PostTelegramFinishCorruptionTest.java`

**Interfaces:**
- Consumes: existing `IllegalStateExceptionMapper`, existing outer `@Transactional(rollbackOn=Exception.class)` finish methods.
- Produces: malformed JSON always propagates an indexed, bounded `IllegalStateException`; null/empty remains a legitimate absent value.

- [ ] **Step 1: Write RED utility tests for both fallback sites**

Add tests with exact behavior:

```java
@Test
void resolverMessageRejectsCorruptMoresTimeWithoutErasingParsedLogs() {
  PostTelegramTrainContentAddParam item = validContent("ABCD");
  item.setPatLogs("[[{\"key\":\"A\"}]]");
  item.setMoresTime("[broken");

  IllegalStateException error = assertThrows(IllegalStateException.class,
      () -> TickerPatUtils.resolverMessage(
          List.of("ABCD"), new PostTelegramTrainScoreVO(), rule(), List.of(item)));

  assertTrue(error.getMessage().contains("moresTime"));
  assertTrue(error.getMessage().contains("index=0"));
}

@Test
void checkDotLineGapRejectsCorruptPatLogs() {
  assertThrows(IllegalStateException.class, () -> TickerPatUtils.checkDotLineGap(
      "A", 0, "[broken", standards(), rule(), false,
      new PostTelegramTrainStatisticsVO(), new PostTelegramTrainScoreVO()));
}
```

Build `rule()/standards()/validContent()` by extracting the exact fixture construction already present in `TickerPatUtilsCharacterizationTest`; do not add mocks.

- [ ] **Step 2: Run RED**

Run:

```bash
JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B -Dtest=TickerPatUtilsTest,TickerPatUtilsCharacterizationTest test
```

Expected: both new methods fail because the current catches return empty lists.

- [ ] **Step 3: Split resolver parsing into per-field parse-or-throw blocks**

Copy the fixed `handleMessageBody` shape for `patLogs`, `moresTime`, and `moresValue`:

```java
try {
  if (contentAddParam.getPatLogs() != null) {
    patLogs = JSONUtils.fromJson(contentAddParam.getPatLogs(), new TypeToken<>() {});
  }
} catch (Exception e) {
  throw corruptJson("patLogs", i, contentAddParam.getPatLogs(), e);
}
```

Add one private helper in `TickerPatUtils`:

```java
private static IllegalStateException corruptJson(String field, int index, String raw, Exception cause) {
  String value = raw == null ? "null" : raw;
  String fragment = value.substring(0, Math.min(value.length(), 96));
  return new IllegalStateException(
      field + " JSON 损坏，拒绝写入（index=" + index + ", raw=" + fragment + ")", cause);
}
```

Use the same helper in `checkDotLineGap`; do not change the intentional plain-text `patKeys` branch.

- [ ] **Step 4: Add an outer transaction rollback regression**

Seed a train and an existing scored/floor row using `PostTelegramTrainServiceTest` fixtures. Put corrupt `patLogs` into the stored message body, call the real `finish`/score path, and assert:

```java
assertThrows(IllegalStateException.class, () -> service.finish(finishDto));
assertEquals(oldResolver, floorContentDao.findById(floorId).getResolver());
assertEquals(oldScore, trainDao.findById(trainId).getScore());
```

Use the actual `finish` DTO and DAO setup copied from the closest current finish test; the assertion must inspect database state, not annotations.

- [ ] **Step 5: Run GREEN and static gates**

Run focused utility and service tests. Verify `TickerPatUtils` contains no catch assigning `new ArrayList<>()` and no comment `JSON解析失败时使用空列表`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/nip/common/utils/TickerPatUtils.java \
  src/test/java/com/nip/common/utils/TickerPatUtilsTest.java \
  src/test/java/com/nip/common/utils/TickerPatUtilsCharacterizationTest.java \
  src/test/java/com/nip/service/PostTelegramTrainServiceTest.java \
  src/test/java/com/nip/service/PostTelegramFinishCorruptionTest.java
git commit -m "fix(score): 损坏拍发轨迹拒绝静默计分"
```

### Task 3: Make Snowflake IDs monotonic under wall-clock rollback

**Files:**
- Modify: `src/main/java/com/nip/common/utils/SnowflakeIdKit.java`
- Modify: `src/main/java/com/nip/ws/WebSocketUnionService.java:280-310`
- Create: `src/test/java/com/nip/common/utils/SnowflakeIdKitTest.java`

**Interfaces:**
- Consumes: JDK `LongSupplier`; current bit layout and singleton API.
- Produces: package-private constructor for deterministic tests; synchronized `nextId()` remains the production API.

- [ ] **Step 1: Add deterministic RED tests with a fresh generator**

Use a scripted supplier and a non-singleton package-private constructor:

```java
private static final long BASE = 1_480_166_466_631L; // START_STAMP + 1_000 ms

@Test
void rollbackUsesLogicalTimeWithoutDuplicateOrDescendingId() {
  ScriptedClock clock = new ScriptedClock(BASE, BASE - 1, BASE - 2, BASE + 1);
  SnowflakeIdKit ids = new SnowflakeIdKit(0, 0, clock);

  long first = ids.nextId();
  long second = ids.nextId();
  long third = ids.nextId();
  long recovered = ids.nextId();

  assertTrue(first < second && second < third && third < recovered);
  assertEquals(BASE, decodeMillis(first));
  assertEquals(BASE, decodeMillis(second));
  assertEquals(BASE, decodeMillis(third));
  assertEquals(BASE + 1, decodeMillis(recovered));
}

@Test
void frozenClockPastSequenceCapacityAdvancesLogicalMillisecond() {
  SnowflakeIdKit ids = new SnowflakeIdKit(0, 0, () -> BASE);
  long previous = ids.nextId();
  Set<Long> generated = new HashSet<>();
  generated.add(previous);
  for (int i = 0; i < 5_000; i++) {
    long next = ids.nextId();
    assertTrue(next > previous);
    assertTrue(generated.add(next));
    previous = next;
  }
}
```

Do not access the production constants from the test; they are private. Add this test-local decoder and keep the production constants private:

```java
private static long decodeMillis(long id) {
  return ((id >>> 22) & ((1L << 41) - 1)) + 1_480_166_465_631L;
}
```

Add a concurrent uniqueness test using a fixed thread pool and the fresh instance.

- [ ] **Step 2: Run RED**

Expected: constructor/time seam is absent; current rollback behavior throws.

- [ ] **Step 3: Add the package-private time source and logical-clock algorithm**

Production singleton continues to use wall time:

```java
private final LongSupplier currentTimeMillis;

SnowflakeIdKit(long dataCenterId, long machineId, LongSupplier currentTimeMillis) {
  if (dataCenterId > MAX_DATA_CENTER_NUM || dataCenterId < 0) {
    throw new IllegalArgumentException(
        "dataCenterId can't be greater than MAX_DATA_CENTER_NUM or less than 0");
  }
  if (machineId > MAX_MACHINE_NUM || machineId < 0) {
    throw new IllegalArgumentException(
        "machineId can't be greater than MAX_MACHINE_NUM or less than 0");
  }
  this.dataCenterId = dataCenterId;
  this.machineId = machineId;
  this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis);
}

private SnowflakeIdKit(long dataCenterId, long machineId) {
  this(dataCenterId, machineId, System::currentTimeMillis);
}
```

Replace rollback throw/busy-spin with:

```java
long wallStamp = currentTimeMillis.getAsLong();
long currStamp = Math.max(wallStamp, lastStamp);
if (currStamp == lastStamp) {
  sequence = (sequence + 1) & MAX_SEQUENCE;
  if (sequence == 0) {
    currStamp = lastStamp + 1;
  }
} else {
  sequence = 0L;
}
lastStamp = currStamp;
return (currStamp - START_STAMP) << TIME_STAMP_LEFT
    | dataCenterId << DATA_CENTER_LEFT
    | machineId << MACHINE_LEFT
    | sequence;
```

Do not change `START_STAMP`, shifts or masks.

- [ ] **Step 4: Make room creation failures observable**

In `WebSocketUnionService.addRoom`, retain `ADD_ROOM_FAIL` but add stack logging:

```java
} catch (Exception e) {
  log.error("联合训练创建房间失败", e);
  send(me.session(), new ResponseModel(UnionConstants.ADD_ROOM_FAIL.getCode()));
}
```

- [ ] **Step 5: Run GREEN and real ADD_ROOM regression**

Run `SnowflakeIdKitTest` and the Union room-message test that uses real ADD_ROOM. Assert 5,001 generated IDs are unique and strictly increasing.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/nip/common/utils/SnowflakeIdKit.java src/main/java/com/nip/ws/WebSocketUnionService.java src/test/java/com/nip/common/utils/SnowflakeIdKitTest.java
git commit -m "fix(id): 时钟回拨时保持雪花 ID 单调"
```

---

## Phase 2: Observable Contract Corrections

### Task 4: Converge Telegram statistical ordering to `[0,1,2]`

**Files:**
- Modify: `src/main/java/com/nip/service/TelegramTrainService.java:390-414`
- Modify: `src/test/java/com/nip/service/TelegramTrainServiceTest.java`
- Modify: `src/main/java/com/nip/dto/vo/TelegramTrainStatisticalVO.java:20-29` (javadoc only)

**Interfaces:**
- Produces: `/api/telegramTrain/statisticalPage` list ordered by `type` ascending; types remain exactly 0, 1, 2.

- [ ] **Step 1: Add RED fill-and-order behavior**

Copy the sibling pattern from `TickerTapeTrainServiceTest`:

```java
@Inject TelegramTrainStatisticalDao statisticalDao;
@Inject UserDao userDao;

@Test
void statisticalPageFillsMissingTypesAndSortsAscending() {
  UserEntity user = Fixtures.user(userDao, "telegram-order");
  for (int type : new int[]{2, 0}) {
    TelegramTrainStatisticalEntity entity = new TelegramTrainStatisticalEntity();
    entity.setUserId(user.getId());
    entity.setType(type);
    entity.setTotalCount(0);
    entity.setAvgSpeed(BigDecimal.ZERO);
    entity.setTotalTime("0");
    statisticalDao.save(entity);
  }

  List<TelegramTrainStatisticalVO> result = service.statisticalPage("telegram-order");

  assertEquals(3, result.size());
  assertEquals(List.of(0, 1, 2), result.stream()
      .map(TelegramTrainStatisticalVO::getType).toList());
}
```

- [ ] **Step 2: Run RED**

Expected: current result is `[2,0,1]`.

- [ ] **Step 3: Delete only the rotation**

Final method tail:

```java
List<TelegramTrainStatisticalVO> convert =
    PojoUtils.convert(entities, TelegramTrainStatisticalVO.class);
convert.sort(Comparator.comparingInt(TelegramTrainStatisticalVO::getType));
return convert;
```

Update VO javadoc to the authoritative three types already present in its `@Schema` and entity.

- [ ] **Step 4: Run GREEN and endpoint test**

Run `TelegramTrainServiceTest`; do not change loop bound from 3 to 4.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/nip/service/TelegramTrainService.java src/main/java/com/nip/dto/vo/TelegramTrainStatisticalVO.java src/test/java/com/nip/service/TelegramTrainServiceTest.java
git commit -m "fix(statistics): 统一拍发统计类型顺序"
```

### Task 5: Make grade-distribution buckets relative to each exam paper

**Files:**
- Create: `src/main/java/com/nip/dto/sql/ExamScoreThresholdDto.java`
- Modify: `src/main/java/com/nip/dao/TheoryKnowledgeExamUserDao.java:156-179`
- Modify: `src/main/java/com/nip/service/TheoryKnowledgeService.java:553-576`
- Modify: `src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java`

**Interfaces:**
- Produces: `ExamScoreThresholdDto(Integer score, Integer passMark, Integer total)`.
- Preserves response category keys: `59` = below passMark, `60` = passMark through good-boundary minus one, `81` = at or above good-boundary.

- [ ] **Step 1: Add RED with passMark not equal to 60**

Seed two exams for one user: paper A `passMark=70,total=100` with scores 69/70/84/85; paper B `passMark=50,total=100` with scores 49/50/74/75. Call `gradeCount(token, year, month, 0)` and assert:

```java
assertEquals(2, details.get("59"));
assertEquals(4, details.get("60"));
assertEquals(2, details.get("81"));
assertEquals(6, result.get("good"));
```

The category counts are per-exam thresholds; `good` remains pass-count semantics.

- [ ] **Step 2: Run RED**

Expected: hardcoded 60/80 buckets misclassify at least paper A scores 69 and 84 and paper B score 75.

- [ ] **Step 3: Add the projection query**

```java
public record ExamScoreThresholdDto(Integer score, Integer passMark, Integer total) {}
```

DAO method:

```java
public List<ExamScoreThresholdDto> findScoreThresholds(List<String> examUserIds) {
  if (examUserIds == null || examUserIds.isEmpty()) {
    return List.of();
  }
  return entityManager.createQuery("""
      select new com.nip.dto.sql.ExamScoreThresholdDto(eu.score, tp.passMark, tp.total)
      from t_theory_knowledge_exam_user eu
      left join t_theory_knowledge_exam_test_paper tp on tp.examId = eu.examId
      where eu.id in (?1)
      """, ExamScoreThresholdDto.class)
      .setParameter(1, examUserIds)
      .getResultList();
}
```

- [ ] **Step 4: Replace the entire fixed-bucket loop while preserving response keys**

After loading `allByUserIdAndEndTimeLike`, replace the current `Map` declaration, score loop and return with the complete connection to the new DAO method:

```java
List<String> examUserIds = allByUserIdAndEndTimeLike.stream()
    .map(TheoryKnowledgeExamUserEntity::getId)
    .toList();
List<ExamScoreThresholdDto> rows =
    theoryKnowledgeExamUserDao.findScoreThresholds(examUserIds);
Map<String, Integer> map = new HashMap<>();
for (ExamScoreThresholdDto row : rows) {
  if (row.score() == null || row.passMark() == null || row.total() == null) {
    throw new IllegalStateException("考试成绩或试卷分数配置缺失");
  }
  int goodBoundary = BigDecimal.valueOf((long) row.total() - row.passMark())
      .divide(BigDecimal.valueOf(2), 0, RoundingMode.DOWN)
      .add(BigDecimal.valueOf(row.passMark()))
      .intValue();
  String key = row.score() < row.passMark() ? "59"
      : row.score() < goodBoundary ? "60" : "81";
  map.merge(key, 1, Integer::sum);
}
all = allByUserIdAndEndTimeLike.size();
good = countPass(allByUserIdAndEndTimeLike);
return buildResultMap(all, good, map);
```

Do not reintroduce a hardcoded `score >= 60` pass count; keep `countExamPass`.

- [ ] **Step 5: Run GREEN and commit**

Run `TheoryKnowledgeServiceTest`, then commit:

```bash
git add src/main/java/com/nip/dto/sql/ExamScoreThresholdDto.java src/main/java/com/nip/dao/TheoryKnowledgeExamUserDao.java src/main/java/com/nip/service/TheoryKnowledgeService.java src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java
git commit -m "fix(grade): 按各场试卷动态分档"
```

---

## Phase 3: Batch 7 Performance and Allocation Tail

### Task 6: Batch TheoryKnowledge detail/statistics queries

**Files:**
- Modify: `src/main/java/com/nip/dao/TheoryKnowledgeTestDao.java`
- Modify: `src/main/java/com/nip/dao/TheoryKnowledgeTestContentDao.java`
- Modify: `src/main/java/com/nip/dao/TheoryKnowledgeSwfRecordDao.java`
- Modify: `src/main/java/com/nip/service/TheoryKnowledgeService.java:81-151,415-501,589-684`
- Modify: `src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java`

**Interfaces:**
- Produces: batch finders `findAllByKnowledgeSwfIdInOrderByCreateTimeAsc(List<String>)`, `findAllByKnowledgeTestIdInOrderByCreateTimeAsc(List<String>)`, and `findAllByUserIdAndKnowledgeSwfIdIn(String,List<String>)`.
- Consumes existing `TheoryKnowledgeTestUserDao.findAllByUserIdAndKnowledgeSwfIdIn`.

- [ ] **Step 1: Add characterization and counting-DAO RED tests**

Build hand-written DAO subclasses that increment counters in the old single-ID methods. Seed 20 SWFs × 3 tests × 2 contents and assert the final DTO tree. Add the desired call-count contract:

```java
assertEquals(1, testDao.batchCalls);
assertEquals(1, contentDao.batchCalls);
assertEquals(1, testUserDao.batchCalls);
assertEquals(1, recordDao.batchCalls);
assertEquals(0, testDao.singleSwfCalls);
assertEquals(0, contentDao.singleTestCalls);
assertEquals(0, testUserDao.singleSwfCalls);
assertEquals(0, recordDao.singleSwfCalls);
```

For the `count` response, also assert one batched knowledge lookup, one batched SWF lookup and one batched user-test lookup, with zero loop-local `findById/findAllByKnowledgeId` calls. For month/day functions, assert the same output maps for records across January/December and month days 1/31.

- [ ] **Step 2: Run RED**

Expected: old nested callbacks invoke single-ID finders N times and counting assertions fail.

- [ ] **Step 3: Add batch DAO methods**

```java
public List<TheoryKnowledgeTestEntity> findAllByKnowledgeSwfIdInOrderByCreateTimeAsc(List<String> swfIds) {
  if (swfIds.isEmpty()) return List.of();
  return find("knowledgeSwfId in ?1", Sort.by("createTime").ascending(), swfIds).list();
}

public List<TheoryKnowledgeTestContentEntity> findAllByKnowledgeTestIdInOrderByCreateTimeAsc(List<String> testIds) {
  if (testIds.isEmpty()) return List.of();
  return find("knowledgeTestId in ?1", Sort.by("createTime").ascending(), testIds).list();
}

public List<TheoryKnowledgeSwfRecordEntity> findAllByUserIdAndKnowledgeSwfIdIn(String userId, List<String> swfIds) {
  if (swfIds.isEmpty()) return List.of();
  return find("userId = ?1 and knowledgeSwfId in ?2", userId, swfIds).list();
}
```

- [ ] **Step 4: Extract one shared tree assembler and batch the token metadata**

Create `private List<TheoryKnowledgeSwfVO> assembleSwfs(List<TheoryKnowledgeSwfEntity> swfs, String userId)`. Fetch all tests and contents once; when `userId != null`, also call the existing `theoryKnowledgeTestUserDao.findAllByUserIdAndKnowledgeSwfIdIn(userId, swfIds)` once and the new `knowledgeRecordDao.findAllByUserIdAndKnowledgeSwfIdIn(userId, swfIds)` once. Group every result by foreign key:

```java
Map<String, List<TheoryKnowledgeTestEntity>> testsBySwf = tests.stream()
    .collect(Collectors.groupingBy(TheoryKnowledgeTestEntity::getKnowledgeSwfId));
Map<String, List<TheoryKnowledgeTestContentEntity>> contentsByTest = contents.stream()
    .collect(Collectors.groupingBy(TheoryKnowledgeTestContentEntity::getKnowledgeTestId));
Map<String, TheoryKnowledgeTestUserEntity> answerBySwf = answers.stream()
    .collect(Collectors.toMap(TheoryKnowledgeTestUserEntity::getKnowledgeSwfId,
        Function.identity(), (first, ignored) -> first));
Map<String, List<TheoryKnowledgeSwfRecordEntity>> recordsBySwf = records.stream()
    .collect(Collectors.groupingBy(TheoryKnowledgeSwfRecordEntity::getKnowledgeSwfId));
```

For each SWF, derive `haveTest` from `testsBySwf.getOrDefault(id,List.of()).stream().anyMatch(t -> Objects.equals(t.getVersions(), 1))`, derive score from `answerBySwf`, and sum study duration from `recordsBySwf`. Build test/content VOs from the two grouped maps. Delete the three token-path single-ID calls and the duplicated nested callback from both public methods. Keep per-SWF/test order by using sorted DAO results.

- [ ] **Step 5: Remove loop-based month/day scans**

Replace the 1..12/1..31 nested loops in `check`, `examTimes`, and `scoreCount` with direct key extraction and `Map.merge/computeIfAbsent`:

```java
String key = month == null || month.isBlank()
    ? String.valueOf(Integer.parseInt(timestamp.substring(5, 7)))
    : String.valueOf(Integer.parseInt(timestamp.substring(8, 10)));
map.merge(key, duration, Long::sum);
```

- [ ] **Step 6: Simplify `count` with batched entities and no managed-entity mutation**

Use nested maps directly instead of concatenating/splitting `knowledgeId:swfId`. Before the output loop, collect all `knowledgeId` and `knowledgeSwfId` values, then execute exactly one `knowledgeDao.list("id in ?1", knowledgeIds)`, one `knowledgeSwfDao.list("id in ?1", swfIds)`, and one existing `theoryKnowledgeTestUserDao.findAllByUserIdAndKnowledgeSwfIdIn(userId, swfIds)`. Group the returned SWFs and user-test rows by `knowledgeId`; determine completion from those grouped counts instead of loop-local `findAllByKnowledgeIdOrderBySortAsc` and `findAllByUserIdAndKnowledgeId` calls. Preserve the current response without mutating a managed entity: create `TheoryKnowledgeEntity display = PojoUtils.convertOne(theoryKnowledgeEntity, TheoryKnowledgeEntity.class)`, set `display.setCredit(0.0)` only when the completion condition fails, and use `display` as the result-map key. Never call `setCredit` on the DAO-returned entity or call `findById` inside the loop.

- [ ] **Step 7: Run GREEN and commit**

```bash
JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B -Dtest=TheoryKnowledgeServiceTest test
git add src/main/java/com/nip/dao/TheoryKnowledgeTestDao.java src/main/java/com/nip/dao/TheoryKnowledgeTestContentDao.java src/main/java/com/nip/dao/TheoryKnowledgeSwfRecordDao.java src/main/java/com/nip/service/TheoryKnowledgeService.java src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java
git commit -m "perf(theory): 批量装配课件与统计数据"
```

### Task 7: Remove proven cross-file N+1 and hot allocations

**Files:**
- Modify: `src/main/java/com/nip/service/TheoryKnowledgeQuestionService.java:40-155`
- Modify: `src/main/java/com/nip/common/interceptor/JWTInterceptor.java:34-87`
- Modify: `src/main/java/com/nip/service/GroupNetTrainService.java:69-82`
- Modify: `src/main/java/com/nip/dao/DeviceTypeDao.java`
- Modify: `src/main/java/com/nip/service/MenusService.java:59-82,147-206`
- Modify: `src/main/java/com/nip/dao/RoleMenusDao.java`
- Modify: `src/main/java/com/nip/dao/MenusButtonDao.java`
- Create: `src/test/java/com/nip/service/TheoryKnowledgeQuestionServiceTest.java`
- Create: `src/test/java/com/nip/service/GroupNetTrainServiceTest.java`
- Modify: `src/test/java/com/nip/service/MenusServiceTest.java`
- Modify: `src/test/java/com/nip/common/exception/ExceptionBoundaryTest.java`

`TestPaperService.getTestPaper` 当前已经直接按题型 switch 写入 DTO 列表，不再在题目循环内构建 Map；P2-82 已由当前源码消除，本任务不得重新修改该文件。

**Interfaces:**
- Produces batch finder methods for device types, role-menu rows and buttons by menu-id set.
- Preserves current `PageInfo` page index/size/count semantics, menu ordering and JSON response shapes.

- [ ] **Step 1: Add behavior + query-count RED tests**

Use hand-written counting DAO subclasses. Assert identical DTO content and these bounds:

```java
assertEquals(1, deviceDao.batchCalls);
assertEquals(1, deviceTypeDao.batchCalls);
assertEquals(0, deviceDao.findByIdCalls);
assertEquals(0, deviceTypeDao.findByIdCalls);
```

For menus, build a three-level tree and assert parent closure/permissions with one bulk role-menu call and one bulk button call. For theory questions, seed two creators plus an unrelated user and assert only the two creator IDs are queried.

- [ ] **Step 2: Allocate TheoryKnowledgeQuestion entities only in insert branches**

Move `new TheoryKnowledgeQuestionEntity()` and `new TheoryKnowledgeQuestionLevelEntity()` plus setters inside `else` blocks; update branches mutate only fetched entities. Replace `userDao.findAll().list()` with:

```java
Set<String> userIds = allByIdIn.stream()
    .map(TheoryKnowledgeQuestionEntity::getCreateUserId)
    .filter(Objects::nonNull)
    .collect(Collectors.toSet());
Map<String, UserEntity> users = userDao.queryByIdIn(userIds).stream()
    .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
```

Set `createUserName` from `users.get(ques.getCreateUserId())`; do not load all users.

- [ ] **Step 3: Inject the singleton ObjectMapper into JWTInterceptor**

Add `@Inject ObjectMapper objectMapper;` and replace all three `new ObjectMapper().writeValueAsString(mp)` calls with `objectMapper.writeValueAsString(mp)`. Do not alter response/CORS logic in this task.

- [ ] **Step 4: Batch GroupNet device/type lookup while preserving PageInfo**

Read the page list once, collect device IDs/type IDs, use `list("id in ?1", ids)` batch methods, map by id, then convert. Construct the same metadata currently produced by `PojoUtils.convertPage`:

```java
List<GroupNetTrainEntity> entities = pageQuery.list();
Map<Integer, DeviceEntity> devices = deviceDao.list("id in ?1", deviceIds).stream()
    .collect(Collectors.toMap(DeviceEntity::getId, Function.identity()));
Map<Integer, DeviceTypeEntity> types = deviceTypeDao.findAllByIdIn(typeIds).stream()
    .collect(Collectors.toMap(DeviceTypeEntity::getId, Function.identity()));
List<GroupNetTrainListPageVO> data = PojoUtils.convert(entities,
    GroupNetTrainListPageVO.class, (entity, vo) -> {
      vo.setDeviceName(Optional.ofNullable(devices.get(entity.getDeviceId()))
          .map(DeviceEntity::getDeviceName).orElse(null));
      vo.setDeviceTypeName(Optional.ofNullable(types.get(entity.getDeviceType()))
          .map(DeviceTypeEntity::getTypeName).orElse(null));
    });
PageInfo<GroupNetTrainListPageVO> result = new PageInfo<>();
result.setData(data);
result.setPageSize(pageQuery.page().size);
result.setTotalNumber(pageQuery.count());
result.setCurrentPage(pageQuery.page().index);
result.setTotalPage(pageQuery.pageCount());
return result;
```

Add to `DeviceTypeDao`:

```java
public List<DeviceTypeEntity> findAllByIdIn(Set<Integer> ids) {
  return ids.isEmpty() ? List.of() : list("id in ?1", ids);
}
```

- [ ] **Step 5: Batch menu ancestors and permissions**

Fetch all menus once and build `Map<String,MenusEntity> byId`; replace recursive DAO calls with map traversal. Add:

```java
public List<RoleMenusEntity> findAllByRoleId(String roleId) {
  return list("roleId", roleId);
}

public List<MenusButtonEntity> findAllByMenusIdIn(Set<String> menuIds) {
  if (menuIds.isEmpty()) return List.of();
  return list("menusId in ?1", menuIds);
}
```

Group rows/buttons in memory and reuse them while building the tree. Preserve `Collections.sort(menusDtos)` and filter buttons by each role-menu row's decoded permission keys.

- [ ] **Step 6: Run focused GREEN and commit**

Run `TheoryKnowledgeQuestionServiceTest`, `MenusServiceTest`, `GroupNetTrainServiceTest`, and `ExceptionBoundaryTest`. Commit:

```bash
git add src/main/java/com/nip/service/TheoryKnowledgeQuestionService.java \
  src/main/java/com/nip/service/GroupNetTrainService.java \
  src/main/java/com/nip/service/MenusService.java \
  src/main/java/com/nip/dao/DeviceTypeDao.java \
  src/main/java/com/nip/dao/RoleMenusDao.java \
  src/main/java/com/nip/dao/MenusButtonDao.java \
  src/main/java/com/nip/common/interceptor/JWTInterceptor.java \
  src/test/java/com/nip/service/TheoryKnowledgeQuestionServiceTest.java \
  src/test/java/com/nip/service/MenusServiceTest.java \
  src/test/java/com/nip/service/GroupNetTrainServiceTest.java \
  src/test/java/com/nip/common/exception/ExceptionBoundaryTest.java
git commit -m "perf: 清理列表 N加一与重复对象分配"
```

---

## Phase 4: Safe Dead-Code and Documentation Reconciliation

### Task 8: Delete only zero-caller internals and reconcile tracking docs

**Files:**
- Delete after reference proof: `CharsetUtils.java`, `GZipUtil.java`, `PingYinUtil.java`, `PasswordUtil.java`, `MapTypeAdapter.java`, `NoEscapeStringSerializer.java`, `ArraysSafeUtils.java`
- Modify: `BunchDetector.java`, `MessageResultBuilder.java`
- Delete: `src/main/java/com/nip/controller/test/Test.java`
- Delete: `docs/guides/code.java`
- Modify: `UserController.java`, `CableController.java`, `CableFloorController.java`, `CableTypeController.java`
- Modify: `TheoryKnowledgeQuestionService.java` to remove commented implementations only
- Modify: `PostEnteringExerciseWordStockService.java` to remove commented shell only
- Modify review/status docs named below

**Interfaces:**
- Produces no runtime API changes.
- Explicitly preserves `SnowflakeIdKit` and all public business endpoints.

- [ ] **Step 1: Reconfirm zero references immediately before deletion**

For each candidate, use LSP references when a Java language server is available; otherwise use exact symbol searches over `src/main` and `src/test`. A class with any live caller is removed from this task, not force-deleted.

- [ ] **Step 2: Delete confirmed internal symbols**

Remove the seven utility files only when each remains zero-reference. Delete uncalled `BunchDetector.detectBunchInRange`, `detectSameLineBunch`, `getColumnNumber`, `getLineDifference`, and `MessageResultBuilder.isConsistent`.

Do **not** delete:

```text
SnowflakeIdKit
TheoryKnowledgeQuestionController.upLoadFile
TheoryKnowledgeQuestionController.exportTemplate
TheoryKnowledgeQuestionController.exportQuestionByLevelId
TickerTapeTrainService.update
```

The endpoint trio requires an external contract decision; `TickerTapeTrainService.update` remains an explicit accepted hold.

- [ ] **Step 3: Remove safe scaffolding**

Delete the experiment controller `controller/test/Test.java`, floating `docs/guides/code.java`, commented upload/export bodies and the commented `view` shell. Remove unused constructor parameters/fields/imports from `UserController` and the three Cable controllers without changing signatures of REST methods.

- [ ] **Step 4: Compile and run focused controller/service tests**

Run `test-compile` first, then controller-adjacent tests. Static gate: deleted class names have zero references; no unused import compile errors.

- [ ] **Step 5: Reconcile review documents**

Update:

```text
docs/reviews/2026-08-26-service-core-review.md
docs/reviews/2026-08-26-common-build-review.md
docs/reviews/2026-08-26-controller-api-review.md
docs/reviews/2026-08-28-fix-spec-remediation.md
```

Mark already-completed P2-07/P2-13/P2-77/pass-count half of P2-78/P2-84/P2-94 accurately; correct the stale claim that SnowflakeIdKit has zero callers; separate this task's deletions from the public endpoint decision list.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/nip/common/utils/CharsetUtils.java \
  src/main/java/com/nip/common/utils/GZipUtil.java \
  src/main/java/com/nip/common/utils/PingYinUtil.java \
  src/main/java/com/nip/common/utils/PasswordUtil.java \
  src/main/java/com/nip/common/utils/MapTypeAdapter.java \
  src/main/java/com/nip/common/utils/NoEscapeStringSerializer.java \
  src/main/java/com/nip/common/utils/ArraysSafeUtils.java \
  src/main/java/com/nip/service/detector/BunchDetector.java \
  src/main/java/com/nip/service/builder/MessageResultBuilder.java \
  src/main/java/com/nip/controller/test/Test.java \
  src/main/java/com/nip/controller/UserController.java \
  src/main/java/com/nip/controller/CableController.java \
  src/main/java/com/nip/controller/CableFloorController.java \
  src/main/java/com/nip/controller/CableTypeController.java \
  src/main/java/com/nip/service/TheoryKnowledgeQuestionService.java \
  src/main/java/com/nip/service/PostEnteringExerciseWordStockService.java \
  docs/guides/code.java \
  docs/reviews/2026-08-26-service-core-review.md \
  docs/reviews/2026-08-26-common-build-review.md \
  docs/reviews/2026-08-26-controller-api-review.md \
  docs/reviews/2026-08-28-fix-spec-remediation.md
git commit -m "refactor: 删除零调用内部代码并校准审计状态"
```

---

## Phase 5: Reproducible Migration Rehearsal

### Task 9: Automate dual-snapshot migration rehearsal on disposable MySQL

**Files:**
- Create: `scripts/rehearse-migrations.sh`
- Create: `src/test/java/com/nip/rehearsal/EntitySchemaSnapshotRehearsal.java` (name intentionally does not end in `Test`, so default Surefire excludes it)
- Create: `docs/database/rehearsal/2026-08-28/README.md`
- Create during execution, schema-only: `docs/database/rehearsal/2026-08-28/current-schema.tsv`, `base-schema.tsv`, `entity-schema.tsv`, `diff-current.txt`, `diff-base.txt`
- Create: `docs/reviews/2026-08-28-migration-rehearsal.md`
- Modify after success: `src/main/resources/application.yml:71-74`, `docs/reviews/2026-08-28-fix-spec-remediation.md`

**Interfaces:**
- Consumes: `project006.sql`, `project006-base.sql`, migrations 01 then 02, Docker, JDK 21.
- Produces: deterministic schema-only evidence; never accepts a host/JDBC URL argument.

- [ ] **Step 1: Add the manual entity-schema exporter**

Create a Quarkus test-class-shaped rehearsal that writes canonical table/column metadata to `target/migration-rehearsal/entity-schema.tsv`:

```java
@QuarkusTest
class EntitySchemaSnapshotRehearsal {
  @Inject DataSource dataSource;

  @Test
  void exportCanonicalSchema() throws Exception {
    Path output = Path.of("target/migration-rehearsal/entity-schema.tsv");
    Files.createDirectories(output.getParent());
    try (Connection c = dataSource.getConnection();
         PreparedStatement ps = c.prepareStatement("""
           select table_name, column_name, ordinal_position, data_type, is_nullable,
                  coalesce(column_default, '<NULL>')
           from information_schema.columns
           where table_schema = database()
           order by table_name, ordinal_position
           """);
         ResultSet rs = ps.executeQuery()) {
      // write tab-separated rows; never write data rows or credentials
    }
  }
}
```

Run only explicitly:

```bash
./mvnw -B -Dtest=EntitySchemaSnapshotRehearsal test
```

- [ ] **Step 2: Write a container-only rehearsal script with hard safety guards**

The script must:

```bash
set -euo pipefail
[[ $# -eq 0 ]] || { echo "This script accepts no database target" >&2; exit 2; }
IMAGE=mysql:8.0
ROOT_PASSWORD="rehearsal-$(date +%s)-$$"
```

For each snapshot, create a fresh uniquely named Docker container and volume, wait with `mysqladmin ping`, import only via `docker exec -i` into that named container, run migration 01 then 02, and trap cleanup. The script must never read `application.yml` datasource settings and must reject any environment variable named `DB_HOST`, `JDBC_URL`, or `QUARKUS_DATASOURCE_JDBC_URL`.

- [ ] **Step 3: Assert schema and timing for both snapshots**

For `project006.sql` and `project006-base.sql`, assert:

```text
post-migration table count = 105
MyISAM table count = 0
general_telex_pat/_page/_user/_user_value and t_masthead exist
simulation_router_room.is_start_sign default = 1
t_post_ticker_tape_train.is_start_sign default = 1
general_key_pat_page.id data_type = varchar
```

Record elapsed milliseconds separately for migrations 01 and 02. On base snapshot, additionally assert the pre-migration id type is integer and post-migration type varchar.

- [ ] **Step 4: Generate canonical schema-only diff artifacts**

Run the same `information_schema.columns` query against each migrated disposable DB, restrict comparison to entity table names exported by `EntitySchemaSnapshotRehearsal`, and write normalized TSV. `diff-current.txt` and `diff-base.txt` must be empty. Engine evidence is a separate TSV/query because entity DDL does not encode production engine expectations.

Never commit data-bearing SQL dumps, container credentials, tokens or logs containing seed rows.

- [ ] **Step 5: Boot the prod jar against the migrated current snapshot**

Build once, then launch the jar with `quarkus.profile=prod`, port 18002 and datasource environment overrides pointing only to the disposable container. The execution harness must use its managed process tool, wait for the Quarkus started banner, then exercise:

```text
POST /api/cable/type/find -> HTTP 200, body code 200
GET /q/openapi -> HTTP 200
GET /q/swagger-ui -> HTTP 404
GET an @JWT endpoint without token -> HTTP 200, body code 203
```

Stop the managed process and disposable container in all cases. Assert logs contain no schema-validation error.

- [ ] **Step 6: Write durable evidence and update status**

The report must include image digest, source snapshot checksums, migration durations, assertion outputs, empty diff checksums, prod startup time and smoke responses with sensitive values redacted. Update the application comment and remediation DoD row 3 only after every assertion passes.

- [ ] **Step 7: Commit**

```bash
git add scripts/rehearse-migrations.sh src/test/java/com/nip/rehearsal docs/database/rehearsal/2026-08-28 docs/reviews/2026-08-28-migration-rehearsal.md docs/reviews/2026-08-28-fix-spec-remediation.md src/main/resources/application.yml
git commit -m "test(migration): 固化双快照迁移演练证据"
```

---

## Phase 6: Final Verification and Status Closure

### Task 10: Run final gates and close only completed follow-up items

**Files:**
- Modify: `docs/reviews/2026-08-28-fix-spec-remediation.md`
- Modify: `docs/specs/2026-08-26-fix-spec.md` only if its status table now contradicts evidence

- [ ] **Step 1: Run the full suite on the final branch**

```bash
JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify
```

Expected: all existing 112 tests plus new tests pass with 0 failures/errors/skips.

- [ ] **Step 2: Run static gates**

Verify:

```text
empty first-party Java catch = 0
log call using getMessage() = 0
WebSocket endpoint per-connection instance Session/UserModel fields = 0
TickerPatUtils catch-to-empty fallback = 0
MySqlResource/@QuarkusTestResource references = 0
git diff --check = clean
actionlint = exit 0
Dockerfile.jvm build = success
```

- [ ] **Step 3: Review query and API contracts**

Run focused tests again for statistical order, dynamic passMark, TheoryKnowledge batch assembly, menu/group-net batch assembly and Snowflake monotonicity. Review JSON snapshots for unchanged keys/shapes.

- [ ] **Step 4: Perform final branch review**

Review the complete merge-base-to-HEAD diff. Block on any Important finding, especially DevServices fallback, transaction rollback, numeric category-key drift, query order drift or migration script targeting.

- [ ] **Step 5: Update durable status honestly**

Mark Tasks 1-9 complete only with fresh evidence. Keep the public endpoint removal decision explicit if still unresolved. Do not claim all original 348 items complete unless every remaining audit family has independently passed review.

- [ ] **Step 6: Commit final evidence**

```bash
git add docs/reviews/2026-08-28-fix-spec-remediation.md docs/specs/2026-08-26-fix-spec.md
git commit -m "docs: 更新 fix spec 后续工作验收状态"
```

---

## Dependency Graph

```text
Task 1 DevServices ──┬──> Task 2 JSON fallback ───────────┐
                     ├──> Task 4 statistical order ──────┤
                     ├──> Task 5 dynamic passMark ──> Task 6 TheoryKnowledge batching
                     ├──> Task 7 cross-file batching ────┤
                     └──> Task 9 migration rehearsal ────┤
Task 3 Snowflake ────────────────────────────────────────┤
Tasks 2-7 ──> Task 8 dead code/docs ────────────────────┤
Tasks 1-9 ────────────────────────────────────────> Task 10 final verification
```

Task 1 必须先执行：它会机械修改所有 `@QuarkusTest` 文件，先完成可避免后续测试文件合并冲突。Task 1 完成后，Tasks 2、3、4、7 可在独立 worktree 中并行；Task 5 和 Task 6 都修改 `TheoryKnowledgeService`，必须串行；Task 8 的文档更新必须在代码任务后；Task 9 依赖 Task 1 的 DevServices 数据库机制。

## Appendix A: Test classes losing `MySqlResource`

```text
src/test/java/com/nip/SmokeTest.java
src/test/java/com/nip/common/exception/ExceptionBoundaryTest.java
src/test/java/com/nip/dao/MilitaryTermDataDaoTest.java
src/test/java/com/nip/dao/PatPageFindTwoPageDaoTest.java
src/test/java/com/nip/dao/PostTelegramTrainFloorContentDaoTest.java
src/test/java/com/nip/service/EnteringTelexPatServiceTest.java
src/test/java/com/nip/service/FindByIdBoundaryTest.java
src/test/java/com/nip/service/FindByIdResidualTest.java
src/test/java/com/nip/service/IntegerUnboxBoundaryTest.java
src/test/java/com/nip/service/MenusServiceTest.java
src/test/java/com/nip/service/MilitaryTermDataServiceTest.java
src/test/java/com/nip/service/PostTelegramTrainServiceTest.java
src/test/java/com/nip/service/PostTelegraphKeyPatTrainServiceTest.java
src/test/java/com/nip/service/PostTelexPatTrainServiceTest.java
src/test/java/com/nip/service/PostTickerTapeTrainServiceTest.java
src/test/java/com/nip/service/TelegramTrainServiceTest.java
src/test/java/com/nip/service/TelexPatTrainStatisticalServiceTest.java
src/test/java/com/nip/service/TestPaperServiceTest.java
src/test/java/com/nip/service/TheoryKnowledgeExamServiceTest.java
src/test/java/com/nip/service/TheoryKnowledgeServiceTest.java
src/test/java/com/nip/service/TickerTapeTrainServiceTest.java
src/test/java/com/nip/ws/WebSocketDeleteOpenAtomicityTest.java
src/test/java/com/nip/ws/WebSocketSimulationTest.java
src/test/java/com/nip/ws/WebSocketUnionLifecycleTest.java
src/test/java/com/nip/ws/WebSocketUnionTest.java
```

## Self-Review Checklist

- Spec coverage: all six durable remaining-work items map to Tasks 1-9; Batch 7 is reconciled into dynamic passMark, batching/allocation, safe dead-code and docs tasks.
- No placeholders: every created/modified file, API signature, test name, command and commit boundary is explicit.
- Type consistency: `ExamScoreThresholdDto` fields and DAO constructor query match; batch finder collection types match entity IDs; Snowflake time supplier returns primitive long.
- Scope consistency: SnowflakeIdKit is preserved; public endpoint removal remains out of automatic execution; migration rehearsal never accepts a target URL.
- Verification consistency: per-task focused tests precede commits; full verification and durable status update happen once after integration.
