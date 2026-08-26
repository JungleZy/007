# Task 1.5 报告：损坏 JSON 空化回写（P0#6）

**结论：已修复。** 三个 `catch (Exception ignore) {}` 改为抛 `IllegalStateException`（带字段名/index/cause），损坏 JSON 不再被静默转 null → 空数组回写；`saveContentValue` 的 `@Transactional(rollbackOn = Exception.class)` 随异常回滚，杜绝“先删后写空化结果”。

## 改动
- `src/main/java/com/nip/common/utils/TickerPatUtils.java`
  - `handleMessageBody`（public static，:270 起，签名/位置与 brief 一致）内三个 catch 改抛：
    - patLogs（原 :303）→ `throw new IllegalStateException("patLogs JSON 损坏，拒绝写入（index=" + i + ")", e)`
    - moresTime（原 :308）→ 同构，字段名 moresTime
    - moresValue（原 :313）→ 同构，字段名 moresValue
  - patKeys 回退分支（:288-296）新增残留风险注释（协议容忍逐字符拆分，损坏 JSON 文本会被拆成含 `[ " ,` 垃圾按键，无协议标记无法区分，接受此残留）。patKeys 的 catch 与逐字符回退**保留不动**。
- `src/test/java/com/nip/common/utils/TickerPatUtilsTest.java`（新增，纯 JUnit 5，无 @QuarkusTest，无容器）
  - `corruptedPatLogsThrowsInsteadOfSilentEmpty`
  - `corruptedMoresTimeThrowsInsteadOfSilentEmpty`
  - `corruptedMoresValueThrowsInsteadOfSilentEmpty`

## 红 → 绿证据
命令：`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test -Dtest=TickerPatUtilsTest`

- 红（改前）：`Tests run: 3, Failures: 3, Errors: 0` — 全部 `Expected java.lang.IllegalStateException to be thrown, but nothing was thrown.`
- 绿（改后）：`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS

## 残留风险
patKeys 逐字符回退保留，损坏 JSON 文本仍会被拆成垃圾按键（已加注释记录）；本任务只收敛 patLogs/moresTime/moresValue 的空化回写路径，符合 brief 范围。
