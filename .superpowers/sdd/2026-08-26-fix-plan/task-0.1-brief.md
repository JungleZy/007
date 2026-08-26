### Task 0.1: Testcontainers MySQL 资源 + %test 配置

**Files:**
- Create: `src/test/java/com/nip/testsupport/MySqlResource.java`
- Modify: `src/main/resources/application.yml`（文件尾追加 `%test` 段）
- Create: `src/test/java/com/nip/SmokeTest.java`

**Interfaces:**
- Produces: `@QuarkusTestResource(MySqlResource.class)` —— 默认全局生效（restrictToAnnotatedClass=false），全部 @QuarkusTest 共享单容器单次启动；`%test` 下 Hibernate `drop-and-create` 实体建表（实体无 JPA 关联、保留字列已转义，评审判定低风险）。

- [ ] **Step 1: 写测试资源类**（顶层 yml 已配 jdbc url → DevServices 不会自启，必须 TestResource 显式覆盖；TestResource 返回值优先级最高，已核实）

```java
package com.nip.testsupport;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MySQLContainer;

import java.util.Map;

public class MySqlResource implements QuarkusTestResourceLifecycleManager {
  private static final MySQLContainer<?> DB = new MySQLContainer<>("mysql:8.0"); // 执行前与生产版本核对

  @Override
  public Map<String, String> start() {
    DB.start();
    return Map.of(
        "quarkus.datasource.jdbc.url",
        DB.getJdbcUrl() + "?rewriteBatchedStatements=true&characterEncoding=utf8&allowPublicKeyRetrieval=true",
        "quarkus.datasource.username", DB.getUsername(),
        "quarkus.datasource.password", DB.getPassword());
  }

  @Override
  public void stop() {
    DB.stop();
  }
}
```

- [ ] **Step 2: application.yml 追加 %test 段**（文件尾，与顶层 `version:` 同级）

```yaml
"%test":
  quarkus:
    hibernate-orm:
      database:
        generation: drop-and-create
    http:
      test-port: 18081
```

- [ ] **Step 3: 冒烟测试**（走 DAO，不写 JPQL——见 Global Constraints JPQL 铁律）

```java
package com.nip;

import com.nip.dao.TestPaperDao;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class SmokeTest {
  @Inject TestPaperDao testPaperDao;

  @Test
  void schemaBoots() {
    assertDoesNotThrow(() -> testPaperDao.count());
  }
}
```

- [ ] **Step 4: 运行** `$MVN test -Dtest=SmokeTest`，期望 `Tests run: 1, Failures: 0`（首跑拉镜像较慢属正常）。首跑核对 Hibernate DDL 日志确认无建表失败。
- [ ] **Step 5: 提交** `git add src/test src/main/resources/application.yml && git commit -m "test(base): Testcontainers MySQL 测试地基"`

