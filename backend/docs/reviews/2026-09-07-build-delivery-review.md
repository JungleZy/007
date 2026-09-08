# 结论：本片 P0 0 条 / P1 0 条 / P2 4 条 / P3 5 条；上一轮 P1-7 与汇总§3第9条四项断言经当前源码复核全部已修复。

| 项目 | 内容 |
|---|---|
| 审查范围 | `.github/workflows/build-quarkus-native.yml`、`src/main/docker/Dockerfile.jvm\|legacy-jar\|native\|native-micro`、`scripts/rehearse-migrations.sh`、`mvnw`/`mvnw.cmd`/`.mvn/wrapper/**`、`README.md`、`.gitignore`、`docs/guides/**`、`docs/specs/**`、`docs/plans/**`、`pom.xml`（仅 build 插件与 native profile）、及交叉取证 `src/main/resources/application.yml`、`docs/database/migrations/**`、`docs/database/rehearsal/2026-08-28/**` |
| 审查日期 | 2026-09-07 |
| 审查方式 | 静态阅读 + grep/glob 取证（未运行构建/测试/formatter/linter）|
| 计数 | P0 0 / P1 0 / P2 4 / P3 5（纯安全项列附录，不计数）|

非目标声明：Java 源码缺陷、pom 依赖内容（归 CommonInfra）不在本片计数内；下文凡涉及 `application.yml`/`reflection-config.json`/`src/main/resources/resources/` 的条目仅作「上一轮遗留核销」现状登记，归属分片已注明，不重复计入本片 P2/P3。

---

## 1. P0

无。

---

## 2. P1

无。

（构建当前为绿；交付链路各步骤本身可执行，未发现可稳定造成数据丢失/跨用户错乱/核心功能整体不可用/资源耗尽的缺陷。）

---

## 3. P2

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| BD-P2-01 | `.github/workflows/build-quarkus-native.yml:27`、`:72`（`restore-keys` `:29`、`:74`）| 任一 push/tag 事件触发 workflow，`test`+`build(ubuntu-22.04/x86_64)`+`build(ubuntu-24.04/arm64)` 三个 job 并发运行 | 三者缓存 key 均解析为 `Linux-maven-<pom-hash>`（`runner.os` 对两种 Linux 架构都是 `Linux`）。`actions/cache` 对同 key 的并发保存只有一个成功，其余打 warning 丢弃；且 x86_64 与 arm64 若下载了带平台 classifier 的依赖会污染同一份 `~/.m2/repository`。最终 runner 二进制在各 job 的 `target/` 现编现传、不走该缓存，故不会污染发布产物本体——影响限于缓存命中率与告警。上一轮 P2-23 建议加 `${{ matrix.arch }}` 未被采纳。 | `key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}`（两处逐字相同）；matrix 两条 ubuntu 条目 `runner: ubuntu-22.04` / `ubuntu-24.04-arm`（`:42-51`）。跨架构缓存的实际污染程度 **待运行验证**。 |
| BD-P2-02 | `pom.xml:7` + `.github/workflows/build-quarkus-native.yml:107-124`、`:135-155` | 打 `vX.Y.Z` tag 触发 release（现已可达）| `<version>1.0-SNAPSHOT</version>` 固定，native 打包产物为 `quarkus-template-1.0-SNAPSHOT-runner`，rename 步骤仅追加 `asset_suffix`，故 GitHub Release 里的二进制名恒为 `quarkus-template-1.0-SNAPSHOT-runner-linux-amd64` 等，与 tag `Release vX.Y.Z` 名义版本不符；下载者无法从文件名区分正式版与快照。项目记忆亦记录「v1.0.0 已发布 native 产物、pom 仍 1.0-SNAPSHOT 待同步」。版本号本身的修订归 CommonInfra，但「发布产物命名与 tag 一致性」属交付链路本片。 | pom 版本 `:7`；rename 逻辑 `mv "$f" "${f}-${{ matrix.asset_suffix }}"`（`:114`、`:118`）；release `tag_name: ${{ github.ref_name }}`（`:152`）。 |
| BD-P2-03 | `.github/workflows/build-quarkus-native.yml:34`、`:78`、`:82` vs `.mvn/wrapper/maven-wrapper.properties:19` | 每次 CI 运行 | 仓库随附 Maven Wrapper 并 pin `apache-maven-3.9.9`，但三处构建全部调用运行器环境自带的 `mvn`（未 `./mvnw`），构建工具版本随 GitHub runner 镜像漂移，与本地 `./mvnw` 及仓库文档命令 `JAVA_HOME=… ./mvnw -B clean verify` 不一致，破坏「wrapper 锁版本」的初衷（可维护性/可复现性）。 | 工作流命令 `run: mvn -B verify` / `run: mvn -B -Pnative …`；wrapper 配置 `distributionUrl=…/apache-maven-3.9.9-bin.zip`。行为差异是否致真实构建结果不同 **待运行验证**。 |
| BD-P2-04 | `.github/workflows/build-quarkus-native.yml:76-78`、`:80-82`（`-DskipTests`）+ release job `:135-155`；`pom.xml:203-220`（failsafe 已配 `native.image.path`）| 每次 native 构建；tag 发布 | 全部三条 native matrix 一律 `-DskipTests`，`native` profile 虽置 `skipITs=false`（`pom.xml:233`）但被命令行 `-DskipTests` 覆盖，failsafe 的 `integration-test`/`verify`（含 `native.image.path`）实际不执行。工作流注释明言「测试由 JVM `test` job 统一守门」，然而 JVM 测试无法覆盖 native-only 失败（反射未注册、资源未内联、序列化差异等）。结果：未经任何 native 功能冒烟的二进制被直接推入 GitHub Release。项目有 151 处 `@RegisterForReflection`，native 反射面广，风险真实存在。 | native 步骤 `mvn -B -Pnative -DskipTests … package`；无任何 `@QuarkusIntegrationTest`/native 冒烟步骤；release 直接取 `dist/**` 发布。native 二进制是否真的漏注册 **待运行验证**（需真实 native+运行）。 |

---

## 4. P3

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| BD-P3-01 | `README.md:1` | 任何人克隆仓库查看根 README | 内容仅 3 字节 `111`，无构建命令、端口(18001)、鉴权(token+deviceId 头)、部署/迁移前置说明的根级入口文档。信息实际散落在 Dockerfile 注释与 `docs/`，但根 README 作为交付门面为占位符，构成上手/交付缺口（非功能阻断）。 | `read README.md` 返回单行 `111`。 |
| BD-P3-02 | `.gitignore:45` | 维护 .gitignore | `/hs_err_pid160488.log` 硬编码单个特定 PID 的 JVM 崩溃日志名，属一次性崩溃残留，应删除或泛化为 `hs_err_pid*.log`。 | `.gitignore` 第 45 行原文。 |
| BD-P3-03 | `scripts/rehearse-migrations.sh:38`、`:39`、`:43-47` | 在 2026-08-28 之后重跑演练脚本 | `OUTDIR` 硬编码 `docs/database/rehearsal/2026-08-28`：无论何时运行都写入该「日期目录」，会用当日结果覆盖 2026-08-28 的历史证据、造成产物日期与实际运行日期不符；且脚本依赖手工预生成并提交的 `entity-schema.tsv`（缺失即 `exit 3`），若实体自 2026-08-28 起漂移而未按 README 先重生成该 tsv，则演练是在**过期实体快照**上做 validate 差分，给出虚假的「差分为空」。可复现性/证据可信度隐患，非功能错误。 | `OUTDIR="$REPO_ROOT/docs/database/rehearsal/2026-08-28"`；`ENTITY_SCHEMA="$OUTDIR/entity-schema.tsv"`；缺失分支 `exit 3` 及提示。tsv 是否已随实体漂移而过期 **待运行验证**。 |
| BD-P3-04 | `.github/workflows/build-quarkus-native.yml:131-132` | 每次 upload-artifact | `path:` 第一行 `target/*-runner*` 已覆盖 `.exe`（含重命名后的 `-runner-windows-amd64.exe`），第二行 `target/*-runner.exe` 为死冗余（重命名后已无 `*-runner.exe` 精确匹配）。无功能影响，纯冗余，上一轮 P2-23 尾注亦提及。 | `path: \| target/*-runner* / target/*-runner.exe`。 |
| BD-P3-05 | `.gitignore:17` + 工作树 `.idea/`（compiler.xml/encodings.xml/jarRepositories.xml/misc.xml/vcs.xml/.gitignore）| 团队协作、IDE 生成配置 | `.gitignore` 第 17 行 `.idea` 已忽略，但工作树中 `.idea/` 仍存在 5 个 IDE 配置文件，说明其在忽略规则加入前已入库、`.gitignore` 对已跟踪文件不生效，IDE 个人配置随仓库分发产生无意义 diff。上一轮 P2-24；内网/单人维护场景影响轻微，降为 P3。 | glob（gitignore:false）列出 `.idea/{compiler,encodings,jarRepositories,misc,vcs}.xml` 与 `.idea/.gitignore` 在盘；`.gitignore:17` 为 `.idea`。「是否仍被 git 跟踪」**待运行验证**（需 `git ls-files .idea`）。 |

---

## 5. 上一轮遗留核销

核销来源：`docs/reviews/2026-08-26-common-build-review.md`（分片）与 `docs/reviews/2026-08-26-full-project-review.md`（汇总）中属 CI/Docker/交付的条目。

| 上轮编号 | 上轮结论 | 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| P1-7 现象一 | ARM64 job 必失败：给 aarch64 传 `-march=x86-64`，且与 `-Dquarkus.native.march=compatibility` 双 `-march` 冲突 | **已修复** | matrix arm64 条目 `native_march: armv8-a`（wf:50）、x86_64 `native_march: x86-64`（wf:45）；Linux native 步骤仅传单一 `-Dquarkus.native.march=${{ matrix.native_march }}`（wf:82），已无 `additional-build-args=-march=...`，无双 `-march`。 |
| P1-7 现象二 | `release` 是死 job：`on.push` 只声明 `branches`，tag push 不触发 workflow，`if: refs/tags` 恒 false | **已修复** | `on.push.tags: ['v*']`（wf:6）与 `branches`（wf:5）并列；`release: needs:[build,test]`（wf:137）、`if: startsWith(github.ref,'refs/tags/')`（wf:141）。tag push 现可触发并进入 release。 |
| 汇总§3 第9条·断言1（release 不可达）| 因不监听 tag 而不可达 | **已修复** | 同上 wf:6 / :141。 |
| 汇总§3 第9条·断言2（ARM64 含 x86 `-march`）| 预期失败需真实 ARM runner 确认 | **已修复** | 同 P1-7 现象一；wf:50/:82。真实 ARM runner 上的构建成功仍 **待运行验证**（本片只读，未跑 CI）。 |
| 汇总§3 第9条·断言3（两 Linux 产物内部同名，平铺合并冲突）| release 恢复后两 Linux runner 文件同名冲突 | **已修复** | 三个 matrix 上传 artifact 名各异 `quarkus-native-${{ matrix.os }}-${{ runner.arch }}`（wf:129，解析为 ubuntu-22.04-X64 / ubuntu-24.04-ARM64 / windows-2022-X64）；二进制按 `asset_suffix` 重命名 linux-amd64/linux-arm64/windows-amd64（wf:107-124）；release 用 `download-artifact@v4`（wf:143-146）默认不 merge，各 artifact 落入 `dist/<name>/` 子目录，`files: dist/**`（wf:151）递归发布，双重去冲突。 |
| 汇总§3 第9条·断言4（4 个 Dockerfile 映射 8080，应用 18001）| Dockerfile 示例/EXPOSE 端口与应用不符 | **已修复** | 四个 Dockerfile 均 `EXPOSE 18001`（jvm:91 / legacy-jar:88 / native:24 / native-micro:27），`docker run` 示例均 `-p 18001:18001`；全仓 `8080` 仅出现在 jvm/legacy-jar 的 `HTTP(S)_PROXY` 注释示例（如 jvm:74-75），非端口映射。应用端口 `application.yml:10 port: 18001`。 |
| P2-23（缓存 key 跨架构冲突 + `*-runner.exe` 冗余）| 建议 key 加 `matrix.arch`、去冗余 glob | **未修复** | key 仍 `${{ runner.os }}-maven-...`（wf:27、:72），无 arch 维度 → 见 BD-P2-01；冗余 glob 仍在（wf:132）→ 见 BD-P3-04。 |
| P2-24（`.gitignore` 忽略 `.idea` 但已入库 6 文件）| 建议 `git rm -r --cached .idea` | **未修复** | `.idea/` 工作树仍有 5 个 xml + `.idea/.gitignore`；`.gitignore:17` 仍 `.idea` → 降级登记为 BD-P3-05。 |
| P2-8（`src/main/resources/resources/` 7.4MB 死资源 + `native.resources.includes` + prod swagger-ui 404）| 建议移入 META-INF/resources 或删除 | **未修复（现状登记，归属 CommonInfra/config）** | 目录仍在盘（glob 命中 `resources/docs/**` 大量 knife4j/mermaid 产物）；`application.yml:52-53 native.resources.includes: resources/**` 仍在；`swagger-ui.enable:true`（:33）但 prod `/q/swagger-ui` 404（迁移演练报告 §Prod jar 冒烟已实测）。本片不计入 P2/P3，交由 CommonInfra 处置。 |
| P2-9（`reflection-config.json` 死文件 + CI `additional-build-args` 覆盖 yml）| 建议删文件与注释 | **部分修复** | CI 侧冲突已消除：工作流不再传 `additional-build-args=-march`，`application.yml:55` 的 `# additional-build-args:` 仍是注释、不参与构建，故不存在「本地加载/CI 覆盖」的行为分裂；但 `src/main/resources/reflection-config.json` 与该注释行仍在（文件删除归 CommonInfra/config）。 |
| P3-3（native 下 `banner.txt` 取不到 / 流未关闭）| 建议 try-with-resources / 加入 includes | **现状登记（归属 CommonInfra）** | `application.yml:52-53` includes 仍仅 `resources/**` 不含 `banner.txt`，`banner.enabled:false`（:7）；Java 侧修复归 CommonInfra，本片不计数。 |

---

## 6. 待运行验证清单

1. BD-P2-01：真实 CI 并发下 `Linux-maven-<hash>` 缓存的实际保存冲突频率与跨架构依赖污染是否发生（需观察 actions 日志中的 cache warning 与 `~/.m2` classifier 构件）。
2. BD-P2-03：GitHub runner 自带 `mvn` 版本与 wrapper pin 的 3.9.9 是否产生可观察的构建差异。
3. BD-P2-04：对三平台 native 二进制做一次真实启动 + `/api/cable/type/find`、`/q/openapi`、无 token `code=203` 等冒烟，确认无 native-only 反射/资源缺失。
4. 汇总§3 断言2：在真实 `ubuntu-24.04-arm` runner 上确认 `armv8-a` native 构建 + glibc≤2.28 校验通过。
5. BD-P3-03：核对当前 HEAD 实体导出的 `entity-schema.tsv` 是否与 `docs/database/rehearsal/2026-08-28/entity-schema.tsv`（103 表，sha256 520f39f8…）一致，判断演练证据是否已过期。
6. BD-P3-05：`git ls-files .idea` 确认 5 个 IDE 配置文件是否仍被跟踪。

---

## 附录：已接受安全风险（不计入计数）

- **Dockerfile 均 `-Dquarkus.http.host=0.0.0.0`**（native:27 / native-micro:30 / jvm:93 / legacy-jar:90 ENV），容器内监听全网卡；内网部署已接受。
- **`application.yml:11-15` CORS 全开**（`origins:'*'`、`headers:'*'`、允许 6 种方法）——非本片新发现，安全分片/CommonInfra 已覆盖，仅登记与交付镜像一并分发。
- **数据源凭据 root/root 明文**入 `%dev`/`%prod`（`application.yml:59-60,78-79`）——随 jar/镜像分发；内网已接受，非本片计数。
- **`softprops/action-gh-release@v2` 需 `contents:write`**（wf:138-139）——发布权限属预期，登记备查。

---

### 备注：整改文档一致性（Change 项 3）

对 `docs/reviews/2026-08-28-fix-spec-remediation.md` 声称的交付相关整改逐条比对当前文件，未发现自相矛盾的验收记录：

1. 该文 §「八个提交后的全量验证」称 `docker build -f src/main/docker/Dockerfile.jvm .` 成功——当前 `Dockerfile.jvm` 结构完整（多层 COPY + run-java.sh 入口），与「可构建」一致。
2. §DoD 第 4 条称「Docker `EXPOSE` 属批 6、端口批次映射修正」——当前四个 Dockerfile 全部 `EXPOSE 18001`，与该修复叙述一致。
3. §剩余已知工作第 6 项与 §Task 10 第 6 条称迁移 01 经双快照演练补入 `general_ticker_pat_train_page.id` int→varchar——`docs/database/migrations/2026-08-26-01-schema-sync.sql:106-107` 确实同时含 `general_key_pat_page.id` 与 `general_ticker_pat_train_page.id` 的 `MODIFY COLUMN id varchar(64) NOT NULL`，与 `2026-08-28-migration-rehearsal.md:75-79` 的补丁描述及 `rehearsal/2026-08-28/diff-*.txt`（0 字节）互证，无矛盾。

`docs/guides/` 现仅存 `handleMessageBody.MD`（评分算法示例规格，Task 8 已删 `code.java`）、`docs/specs/2026-08-26-fix-spec.md`、`docs/plans/2026-08-26-fix-plan.md`；三者为历史规格/计划文档，与当前构建/交付配置无直接自相矛盾之处（其所述算法缺陷归对应 service 分片，非本片交付链路范围）。
