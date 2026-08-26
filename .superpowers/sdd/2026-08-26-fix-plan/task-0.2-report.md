# Task 0.2 报告：通用 fixture 与 nullToEmpty 工具

**状态：完成。**

## 提交
- 哈希：`16090a8`（分支 `fix/2026-08-26-p0`）
- 消息：`test(base): fixtures 与 ListUtils`
- 内容：仅 2 个新文件，无夹带
  - `src/main/java/com/nip/common/utils/ListUtils.java`（+12）
  - `src/test/java/com/nip/testsupport/Fixtures.java`（+16）

## 编译
`JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B test-compile` → **BUILD SUCCESS**（11.3s）。

## 核实
- `UserEntity` 用 Lombok `@Data`，`setUserName`/`setUserAccount`/`setToken` 均自动生成，与 brief 一致，无需改写。
- `UserDao extends BaseRepository`，`BaseRepository.save` 带 `@Transactional`（BaseRepository.java:14-15），返回落库实体，符合 Fixtures 依赖。
- 代码逐字采用 brief Step 1/Step 2，Fixtures 未加 `@Transactional`（按 brief 说明，static 方法拦截器 no-op）。
- 提交前工作区已有无关改动（doc/*.sql 与 doc/* 删除、mvnw 修改、未跟踪 .superpowers/）；用显式路径 `git add` 只暂存 2 个新文件，未纳入这些改动。

## concerns
- 提交消息用 brief Step 3 指定的 `test(base): ...`，与 global-constraints 的 `fix(<编号>): ...` 格式不同；按任务显式指令执行。如需统一格式，可 amend 为 `fix(0.2): fixtures 与 ListUtils`。
