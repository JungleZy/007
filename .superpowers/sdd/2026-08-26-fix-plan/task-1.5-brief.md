### Task 1.5: 损坏 JSON 空化回写（P0#6，TickerPatUtils:300-314）

**Files:**
- Modify: `src/main/java/com/nip/common/utils/TickerPatUtils.java:300-314`
- Test: `src/test/java/com/nip/common/utils/TickerPatUtilsTest.java`

**现行缺陷**：`handleMessageBody`（public static，:270 起；PostTelegramTrainService 经 static import :57 在 :526/:848 调用——即同一方法）内 :303/:308/:313 三个 `catch (Exception ignore) {}` 把损坏的 patLogs/moresTime/moresValue 静默转 null，:316-318 再转空数组；外层 `saveContentValue`（:534-535）随后 delete 旧行 + save 空化结果。:286 的 patKeys catch 有逐字符回退（:288-296），属对"纯文本 patKeys"的协议容忍，**保留**。

- [ ] **Step 1: 失败测试**（纯单测，无容器；JSONUtils 用 Gson，损坏 JSON 抛 JsonSyntaxException——已核实可行）

```java
@Test
void corruptedPatLogsThrowsInsteadOfSilentEmpty() {
  PostTelegramTrainContentAddParam item = new PostTelegramTrainContentAddParam();
  item.setPatKeys("[\"a\",\"b\",\"c\",\"d\"]");
  item.setPatLogs("{corrupted-json");   // 损坏
  item.setMoresTime("[[1,2]]");
  item.setMoresValue("[[1,2]]");
  assertThrows(IllegalStateException.class,
      () -> TickerPatUtils.handleMessageBody(List.of(item)));
}
```

- [ ] **Step 3: 修复**——三个 catch 改抛：

```java
try {
  logs = JSONUtils.fromJson(item.getPatLogs(), new TypeToken<>() {});
} catch (Exception e) {
  throw new IllegalStateException("patLogs JSON 损坏，拒绝写入（index=" + i + ")", e);
}
// moresTime / moresValue 同构，字段名换掉
```

patKeys 回退分支（:288-296）加注释记录残留风险：`// 协议容忍：纯文本 patKeys 逐字符拆分；副作用：损坏的 JSON 数组文本也会被拆成含 [ " , 的垃圾按键——无协议标记无法区分，接受此残留`。
`saveContentValue` 已是 `@Transactional(rollbackOn = Exception.class)`（:508），异常冒出即回滚，无需改动外层。

- [ ] Step 4-5：绿 → `git commit -m "fix(p0-6): 损坏JSON解析失败改抛异常防空化回写"`

