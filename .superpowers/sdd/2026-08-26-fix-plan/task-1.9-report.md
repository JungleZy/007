# Task 1.9 Report: 同类型训练查重断言方向写反（改级#16）

**结论：已修复并提交 `d14e62aa85375f6e7946ec6fbffdea54ceed2b06`。红→绿完整，Assert 清扫确认全仓仅此一处写反。**

## 修复内容

`src/main/java/com/nip/service/EnteringTelexPatService.java:49`（save 创建路径，`param.getId()==null` 分支内）：

```diff
-      Assert.notNull(check, "您已存在相同类型的训练，不能再添加同类的训练！");
+      Assert.isNull(check, "您已存在相同类型的训练，不能再添加同类的训练！");
```

消息不变。`Assert.isNull(Object, String)` 重载确认存在（`com.nip.common.utils.Assert:55-59`，object!=null 时抛 IllegalArgumentException）。

## 红→绿证据

测试：`src/test/java/com/nip/service/EnteringTelexPatServiceTest.java`（@QuarkusTest + MySqlResource，Fixtures.user 建用户，token 走 `service.save(token, param)` 真实路径）。两用例：

1. `firstCreateOfTypeSucceeds` — 首次创建 type=0 训练应成功并返回持久化 id。
2. `secondCreateOfSameTypeIsRejected` — 同一用户同类型第二条创建应抛 IllegalArgumentException（消息核对）。

**红**（修复前，`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=EnteringTelexPatServiceTest`）：

```
[ERROR]   EnteringTelexPatServiceTest.firstCreateOfTypeSucceeds:42 » IllegalArgument 您已存在相同类型的训练，不能再添加同类的训练！
[ERROR]   EnteringTelexPatServiceTest.secondCreateOfSameTypeIsRejected:53 » IllegalArgument 您已存在相同类型的训练，不能再添加同类的训练！
[ERROR] Tests run: 2, Failures: 0, Errors: 2, Skipped: 0
```

两条都在**首次**创建时被写反的 notNull 拦下（check==null 抛"已存在"），与缺陷描述完全一致。

**绿**（修复后，同命令）：

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Step 4：Assert.notNull / Assert.isNull 全仓方向清扫

`grep -rn "Assert.notNull\|Assert.isNull" src/main/java` 逐点核对，共 4 类调用点：

| 位置 | 用法 | 方向判定 |
|---|---|---|
| `common/repository/BaseRepository.java:16,34` | 入参 entity/entities 非空守卫 | notNull 正确 |
| `common/specification/DefaultEntityInformation.java:15` | domainType 非空守卫 | notNull 正确 |
| `common/utils/StreamUtils.java`（:70,71,90,91,107-109,126,127,155,156,190,218,230）、`common/utils/StringUtils.java:414` | 流/字符集入参非空守卫（Spring 移植代码） | notNull 正确 |
| `service/EquipmentTrainService.java:57` | 请求参数 id 非空守卫（"请传入ID"） | notNull 正确 |
| `service/EnteringExerciseService.java:109` | `save = exerciseDao.save(entity)` 后断言结果存在（"未查询到该训练"） | notNull 方向正确（断言"必须存在"配"未查询到"消息，非反向）。附注：该检查放在 save 之后偏晚——findById 为 null 时 :106 会先 NPE——属检查位置问题而非方向写反，不在本任务范围，未改 |
| `service/EnteringTelexPatService.java:49` | 查重：check 存在即重复 | **唯一写反点，本任务已修为 isNull** |

结论：与评审判断一致，写反仅 :49 一处，无残留；其余各点均未改动。

## 提交

`d14e62aa85375f6e7946ec6fbffdea54ceed2b06` — `fix(p1-16): 同类型训练查重断言方向写反`（实现 + 测试同一提交，2 files changed）。
