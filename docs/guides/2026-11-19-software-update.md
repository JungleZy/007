# 软件升级操作指南（2026-11-19）

规格：`docs/specs/2026-11-19-software-update-spec.md`；实施与验证记录：`docs/plans/2026-11-19-software-update-plan.md`。

**当前状态**：代码已实现。尚未用真实构建产物出过升级包，也未在任何目标平台实机演练；下面的流程是按实现写的操作步骤，首次执行时请按 §6 逐项核对。

## 1. 基本概念

- **完整安装包**：含 Electron、JRE、MySQL。新装机器、运行环境变更时使用。
- **升级包**（`.nipupd`）：只含外壳与前端（`app.asar`）、后端、数据库迁移。每个平台与风格的组合一个文件，例如 `nip-update-3.2.0-windows-ia32-HJ.nipupd`。
- **版本目录**：升级包解到 `<用户数据目录>/system/releases/<版本>/`，安装目录不被改动。32 位 Windows 的数据目录是 `system-win32-mysql57`。
- **运行时基线**：`bw-frontend/electron/shared/update-keys.js` 的 `RUNTIME_BASELINE`。升级包只能装到基线相同的机器上。

带升级功能的版本从 **4.0.0** 开始（2026-11-20 定）。存量机器（3.1.3 及更早）没有升级功能，必须先人工安装一次 4.0.0 或更高版本的完整安装包；`build/update-release.json` 的 `minFromVersion` 相应为 4.0.0。

## 2. 签名密钥

- 私钥放在仓库之外，出包时通过环境变量 `NIP_UPDATE_SIGNING_KEY` 指定路径。
- 公钥在 `bw-frontend/electron/shared/update-keys.js` 的 `PUBLIC_KEYS`，随完整安装包进入现场机器，**不随升级包更新**。
- 生成新密钥：`node scripts/update-signing-key.cjs <仓库外的私钥路径>`，把输出的公钥加入 `PUBLIC_KEYS`。
- 私钥丢失或泄露后，只能发新的完整安装包更换公钥。请离线备份。
- 轮换：新安装包的 `PUBLIC_KEYS` 同时保留新旧两把，待现场都装过后再移除旧的。

## 3. 发布一个版本

以下命令在 `bw-frontend/` 下执行。

1. 改版本号：`bw-frontend/package.json`、两个 `package-lock.json`、`frontend/package.json`、`backend/pom.xml`、`MainApplication.java` 的 OpenAPI 版本。用 `node scripts/check-product-version.cjs` 确认一致；打包前置步骤也会自动检查。版本号必须是三段数字。
2. 按各平台指南构建后端、准备运行组件、构建前端与应用目录（见 `docs/guides/desktop-packaging-checklist.md`）。风格由 `NIP_INTERFACE_STYLE` 指定。
3. 对每个平台与风格的组合出升级包：

   ```bash
   NIP_UPDATE_SIGNING_KEY=<私钥路径> node scripts/build-update-package.cjs \
     --target <windows-x64|windows-ia32|linux-arm64|linux-loong64> --style <HJ|HJJ|LJ> \
     --app-asar <本轮构建产物>/resources/app.asar
   ```

   产物在 `out/updates/`，旁边的 `.json` 是构建报告（字节数、sha256、提交）。脚本会核对：`app.asar` 的版本与风格、运行组件的平台与版本、已发布迁移未被改动，并用内置公钥把成品完整验一遍。
4. 发布完成后记录已发布的迁移：`node scripts/build-update-package.cjs --record-release`，并提交 `build/update-release.json`。

需要改 `build/update-release.json` 里 `minFromVersion` 的情况：新版本不再支持从某个旧版本直接升级时，把它提到仍受支持的最低版本。

### 必须发完整安装包并把基线加一的改动

- 更换 Electron、JRE 或 MySQL。
- 改动 `main.js`、`electron/bootstrap/`、`electron/shared/update-keys.js`（它们只从安装目录加载，升级包里的副本不生效）。
- 改动升级包格式或版本指针的读取方式，使旧引导读不懂。

## 4. 现场升级

### 单机

1. 以管理员登录，进入「系统管理 → 数据备份」页面底部的「软件升级」。
2. 点「导入升级包」，选择与本机平台、风格一致的 `.nipupd` 文件。
3. 提示就绪后点「立即重启」。重启时如需更新数据库，会先自动备份到数据目录的 `backups/before-upgrade-*.sql`。

### 组网

1. 在作为服务端的机器上按单机流程升级并重启。请在无人训练时操作，重启期间客户端会断开。
2. 现场有其他平台或风格的客户端时，把它们的升级包也在服务端导入。这类包只留存不安装，供客户端下载。
3. 客户端启动后发现版本不一致会弹出提示，点「下载并升级」，完成后「立即重启」。
4. 服务端没有对应的包时，客户端可用 U 盘在提示里「导入升级包」。
5. 也可以点「继续使用」关闭提示，照常登录和训练；下次启动会再次提示。版本不一致时部分功能可能无法正常工作，建议尽快升级。

版本检查只在数据服务指向远端时进行，比较的是三段版本号是否完全相同；不同风格可以组网。保存网络设置时，本机与远端之间发生切换会自动重启软件。

### 版本不一致时的提示含义

| 提示 | 处理 |
|---|---|
| 本机版本低于服务端 | 下载并升级，或导入升级包 |
| 本机低于服务端，但服务端没有适用的包 | 在服务端导入本机平台与风格的包，或本机用 U 盘导入 |
| 服务端版本低于本机 | 先升级服务端 |
| 服务端是不支持版本检查的旧版本 | 服务端需先人工安装带升级功能的完整安装包 |

服务端连不上时不提示，表现与以前相同。

## 5. 失败与回退

| 情况 | 行为 |
|---|---|
| 升级包签名不对、被改动、平台或风格不符、版本不高于当前、基线不符 | 导入时拒绝并说明原因，当前版本不受影响 |
| 新版本首次启动失败，数据库还没开始更新 | 自动回到升级前的版本并重启。需重新导入升级包 |
| 新版本首次启动失败，数据库已开始更新 | 不自动回退。可退出后重试；或选「恢复备份并回退」，用升级前的备份覆盖数据库并回到旧版本 |
| 已在使用的版本文件损坏 | 启动时提示，可重新导入升级包，或暂以出厂版本启动 |
| 数据库比程序新（例如装回了旧的完整安装包） | 拒绝启动并提示，不改动数据库 |

注意：

- 首次启动失败若由环境原因引起（例如端口被占用），同样会触发自动回退，排除原因后需重新导入。
- 「恢复备份并回退」会丢弃升级之后写入的数据。
- 重装不低于已升级版本的完整安装包后，以安装包里的版本为准。
- 排查入口：数据目录下的 `logs/startup.log`（引导的记录带 `[bootstrap]`）、`releases/current.json`、`releases/last-failure.json`。

## 6. 首次实机执行时需核对的事项

实现完成时这些都没有在真实环境运行过：

- [ ] 各平台用真实构建产物出包成功，体积与耗时可接受。
- [ ] 带数据的机器导入升级包后重启：版本正确、数据保留、有新迁移时生成了备份。
- [ ] 注入启动失败，验证两种回退路径；「恢复备份并回退」后数据与升级前一致。
- [ ] Win7：用户名含中文时，后端能从数据目录下的版本目录启动。
- [ ] 麒麟、龙芯：普通用户无需提权即可完成升级。
- [ ] 两台机器组网：一新一旧双向各试一次；跨平台、跨风格各一组。
- [ ] 版本提示点「继续使用」后可正常登录和训练；提示里的下载、导入可用。
- [ ] 后端 `UpdateControllerTest` 与 `./mvnw -B clean verify` 通过。

## 7. 与旧补丁脚本的关系

`build-kylin-update-patch.cjs`、`build-win7-update-patch.cjs` 及 webgl、serial 专项补丁脚本针对的是已交付的旧版本。入口已拆为 `main.js`（引导）与 `electron/app-main.js`，其中只拷入 `main.js` 的 webgl 补丁脚本**不能再对当前源码使用**，否则会产出缺少引导模块的包。现场存量机器装完带升级功能的完整安装包后，这些脚本应移除。
