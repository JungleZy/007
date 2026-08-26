# Phase 6 报告：交付链路

结论：4/4 子 Task 完成，各一次提交。actionlint 全绿、docker build 通过、test-compile 通过、依赖树确认 beanutils/commons-collections 已消失。

## Task 6.1 — workflow（提交 1fc4dab）

改动 `.github/workflows/build-quarkus-native.yml`：
- `on.push` 增加 `tags: ['v*']`（release job 原先永不触发的死路径打通）。
- Linux native 构建按架构拆分：matrix include 增加 `native_march` 键（x86_64→`x86-64`，arm64→`armv8-a`），构建命令改为 `-Dquarkus.native.march=${{ matrix.native_march }}`，删除与之冲突的 `-Dquarkus.native.additional-build-args=-march=x86-64`（原先 ARM64 job 必失败的根因）。
- 产物名改为 `quarkus-native-${{ matrix.os }}-${{ runner.arch }}`。
- 连带修正：release job 的 `download-artifact` 去掉 `merge-multiple: true`——两个 Linux 架构的二进制同名 `*-runner`，合并解包会互相覆盖，改为默认按 artifact 名分目录解包。
- actionlint 报出的两处存量问题一并修（否则无法全绿退出）：SC2012（`ls`→`find`）、`softprops/action-gh-release@v1`→`@v2`（v1 runner 过旧）。

**校验方式**：本机无 actionlint 二进制，按预案用 `docker run --rm -v $PWD:/repo -w /repo rhysd/actionlint:latest`。首轮报 2 处存量问题（见上），修复后复跑 **exit=0**。

## Task 6.2 — Dockerfile EXPOSE（提交 c7abeb9）

- brief 点名的 `Dockerfile.jvm:91`、`Dockerfile.native:24` EXPOSE 8080→18001。
- 同目录 `Dockerfile.legacy-jar`、`Dockerfile.native-micro` 有完全相同缺陷（域内文件），一并修正；4 个文件注释里的 `docker run -p 8080:8080` 示例同步改 18001。应用端口 18001 已对 `application.yml:10` 核实。
- **证据**：`flock /tmp/omp-mvn.lock -c "./mvnw -B package -DskipTests"` exit=0 产出 fast-jar；`docker build -f src/main/docker/Dockerfile.jvm .` exit=0；`docker inspect` ExposedPorts = `{"18001/tcp":{},"8080/tcp":{},"8443/tcp":{}}`——8080/8443 来自 ubi8/openjdk-21 基础镜像自身的 EXPOSE，下游 Dockerfile 无法移除，非缺陷。

## Task 6.3 — BeanUtils 清理（提交 430be9a）

- 全仓 grep 确认受影响面仅 4 个 Java 文件（`Assert`/`ObjectUtils`/`StringUtils` import commons-collections 3.x；`PojoUtils` import beanutils），远低于 10 文件阈值 → 走完整替代路线，未降级。
- `Assert.java`：`CollectionUtils.isEmpty(Collection)`→hutool `CollUtil.isEmpty`；两个 Map 重载原文是 `CollectionUtils.isEmpty(Collections.singleton(map))`（singleton 恒非空 → 断言恒不触发的死逻辑），改为 `MapUtil.isEmpty(map)` 恢复语义。风险为零：`Assert.notEmpty(` 全仓无调用点。
- `StringUtils.java`（2 处）→`CollUtil.isEmpty`；`ObjectUtils.java` 仅 javadoc `@see` 引用 → 改指向 `CollUtil`。
- `PojoUtils.merge`（BeanUtils 唯一调用点，全仓零调用者）连同 import 删除；pom 删 `commons-beanutils` 依赖及 `commons-beanutils.version` 属性。未显式声明 commons-collections——三处使用已全部换掉，无需声明。
- **证据**：`flock ... ./mvnw -B test-compile` exit=0；`dependency:tree -Dincludes='commons-beanutils:*,commons-collections:*'` 输出为空（CVE-2025-48734 暴露面消除）。

## Task 6.4 — 文档勘误（提交 32c318a）

- `2026-08-26-common-build-review.md` 页首"共 27 条"后加勘误 blockquote：正文实为 35 条（P1=7、P2=24、P3=4，依据 audit §4）。原文未改。
- `2026-08-26-controller-api-review.md` scope"52 个文件"后加勘误 blockquote：实为 63 个 Controller 类 / 64 个 .java（52 是 @JWT 类数，依据 audit §4；64 经本机实测计数复核）。原文未改。

## 提交清单

| Task | 提交 | 内容 |
|---|---|---|
| 6.1 | 1fc4dab | workflow tags 触发 + 按架构 march + 产物名 arch + actionlint 全绿 |
| 6.2 | c7abeb9 | 4 个 Dockerfile EXPOSE 18001 |
| 6.3 | 430be9a | commons-collections→hutool、删 beanutils |
| 6.4 | 32c318a | 两份评审文档勘误标注 |

## Concerns

1. `package -DskipTests` 与 `test-compile` 在兄弟 agent 并发改源码的工作区通过——通过时点的快照有效性以合流后全量 verify 为准。
2. workflow 的 ARM64 job（`ubuntu-24.04-arm` + mandrel 容器构建）本地无法实跑，`-march=armv8-a` 取值依据评审文档所列 aarch64 合法值；需首次 tag push 实测。
3. release job 下载布局改为按 artifact 名分目录（去 merge-multiple），`files: dist/**` 语义不变，但发布产物路径会多一层目录名——如有下游脚本依赖平铺布局需知悉。

## 修复轮 1（评审 Important，提交 e2008b9）

**问题**：gh-release 按文件 basename 命名资产，两个 Linux native 产物同名 `*-runner`，`files: dist/**` 上传时第二个 422→删旧重传，发布只剩一个架构（静默丢失）；此前去掉 merge-multiple 只解决下载目录、不解决资产命名。

**修复**：matrix 增加 `asset_suffix`（linux-amd64 / linux-arm64 / windows-amd64），各 build job 在 glibc 校验之后、upload-artifact 之前新增 "Rename binary with architecture suffix" 步骤（bash，Windows runner 走 Git Bash），`*-runner`→`*-runner-<suffix>`、`*-runner.exe`→`*-runner-<suffix>.exe`，无可重命名文件时 exit 1 快速失败。一处改动同时覆盖 build 产物与 release 资产两层，dist 下 basename 全局唯一。upload path `target/*-runner*` 无需改动即匹配重命名后文件。

**证据**：actionlint（docker rhysd/actionlint）exit=0。
