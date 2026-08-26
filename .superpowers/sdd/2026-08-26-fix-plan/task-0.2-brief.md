### Task 0.2: 通用 fixture 与 nullToEmpty 工具

**Files:**
- Create: `src/main/java/com/nip/common/utils/ListUtils.java`
- Create: `src/test/java/com/nip/testsupport/Fixtures.java`

**Interfaces:**
- Produces: `ListUtils.nullToEmpty(List<T>)`（批 1 多任务使用）；`Fixtures.user(UserDao, String token)` 返回已落库 UserEntity——**id 是 @GeneratedValue(UUID) 自动生成，测试一律用返回实体的 `getId()`，禁止硬编码 id**。

- [ ] **Step 1: 工具类**

```java
package com.nip.common.utils;

import java.util.ArrayList;
import java.util.List;

public final class ListUtils {
  private ListUtils() {}

  public static <T> List<T> nullToEmpty(List<T> list) {
    return list == null ? new ArrayList<>() : list;
  }
}
```

- [ ] **Step 2: Fixtures**（不加 @Transactional——static 方法上拦截器不绑定是 no-op；落库依赖 `BaseRepository.save` 自身的 @Transactional，已核实）

```java
package com.nip.testsupport;

import com.nip.dao.UserDao;
import com.nip.entity.UserEntity;

public final class Fixtures {
  private Fixtures() {}

  public static UserEntity user(UserDao userDao, String token) {
    UserEntity u = new UserEntity();
    u.setUserName("tester");
    u.setUserAccount("tester");
    u.setToken(token);
    return userDao.save(u); // save 自带事务独立提交
  }
}
```

- [ ] **Step 3: 编译** `$MVN test-compile`，通过后提交 `git commit -m "test(base): fixtures 与 ListUtils"`

