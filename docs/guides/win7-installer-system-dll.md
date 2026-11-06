# Win7 安装器 System.dll 崩溃处理

每次打包先执行 [桌面打包与交付检查清单](desktop-packaging-checklist.md)，再按本文构建compat2；最终归档必须核对本轮WebGL配置和运行依赖。

当前Win7完整交付版本为 [HJ 3.1.3](2026-11-06-win7-hj-3.1.3-release.md)，已合入串口和WebGL修复。本文下方3.1.1产物名称、摘要与现场记录均为历史依据。

自HJ 3.1.2起，`prepare:win7-32`同时准备官方CH340离线驱动，缺少下载缓存时从厂商获取并严格校验固定摘要及签名；也可单独执行 `node scripts/prepare-ch340.cjs`。准备后正常构建应用目录和compat2，安装器将启动厂商驱动安装窗口。后续产物名包含 `-HJ-`，本页下方3.1.1文件名和摘要为历史记录；新产物和驱动操作见 [HJ 3.1.2交付记录](2026-11-06-hj-desktop-release.md)。

**当前交付结论（2026-11-06）：用户已在此前报错的 Windows 7 64 位机器上确认 compat2“能正常安装使用”。后续为相同环境打包，使用本文的 compat2 流程，不再分发原 electron-builder 默认 NSIS 安装器。** 本次运行的是 ia32 应用及32位数据库/JVM，不是原 Electron30 x64 包。该反馈不外推为所有 Win7 版本、32位操作系统或全部外设已经验收。

后续功能反馈：用户报告WebGL不可用，已修复入口同时禁用GPU和软件渲染的问题，并提供已安装版本补丁。见 `docs/guides/win7-webgl.md`；重新打包时须先重新生成应用目录，旧compat2安装器不会自动带入此修复。

用户截图显示 `APPCRASH`，应用名为 `多功能报务综合训练系统-win7-3.1.1-ia32.exe`，故障模块为 `System.dll`。这是安装器进程的崩溃，不能由该截图推断 Electron、Java 或 MySQL 服务端已发生故障。

原安装器使用 electron-builder 的 NSIS 模板，在安装目录探测、互斥和进程检查中调用 `System::Call`。其中 `multiUser.nsh` 使用 `SHGetKnownFolderPath` 及指针解引用读取用户程序目录。没有 Win7 的完整崩溃事件/转储，尚不能确定具体调用和系统环境原因。

## 替代交付

提供两份不需要原安装器插件流程的产物：

- `bw-frontend/out/win7-ia32/多功能报务综合训练系统-win7-3.1.1-ia32-compat2.exe`
- `bw-frontend/out/win7-ia32/多功能报务综合训练系统-win7-3.1.1-ia32-unpacked.zip`

兼容安装器由 `build/win7-compatible-installer.nsi` 生成，仅使用 NSIS 内置指令，不使用 System/UAC/StdUtils/nsDialogs 插件。完整应用先压缩为7z数据，安装器通过随附32位7za程序解压，避免旧32位 NSIS 编译器直接处理大型未压缩目录时的内存映射限制。

安装器以当前用户权限运行，默认程序目录为 `%LOCALAPPDATA%/Programs/nip-traffic-system-win7`，桌面快捷方式名带 `Win7 x86`。不会默认启动应用；安装结束后由用户从新快捷方式启动。先退出旧程序，避免6912、18001或8000端口冲突。

ZIP版完整解压到可写目录，直接运行根目录的 `多功能报务综合训练系统.exe`；不得只取出单个EXE或在压缩软件预览窗口中运行。ZIP绕过所有安装器代码，适合区分安装器故障与应用本体运行问题。

两个版本携带同一套已校验的 Electron22 ia32、Java21 x86、MySQL5.7 Win32、后端和初始化资料。默认数据库端口6912。数据仍位于原 Electron 用户数据目录的 `system-win32-mysql57`，程序目录变化不会清空数据。初始管理员密码仅在首次初始化时显示。

兼容安装器的卸载仅按随包文件清单删除程序文件，目录使用非递归删除；不删除 `APPDATA` 业务数据，也不递归删除安装目录中的未知文件。

## 构建与验证

### 固定组件与构建机准备

| 项目 | 本次成功组合 |
| --- | --- |
| 客户系统 | 用户报告的 Windows 7 64 位；具体 SP1、补丁和硬件信息未提供 |
| 应用与客户端架构 | Windows ia32 / x86，安装器和解压程序也使用32位 |
| Electron | 22.3.27 ia32 |
| Java | BellSoft Liberica 21.0.12.1+1 x86 |
| MySQL 服务端与工具 | 5.7.44 Win32 |
| 后端 | JDK21构建，Maven profile `windows-ia32`，数据库构建版本5.7.0 |
| 组装工具 | electron-builder 22.14.13，用于生成解包目录 |
| 最终安装器 | NSIS 3.0.4.1 + `bw-frontend/build/win7-compatible-installer.nsi` |
| 解压程序 | `7zip-bin` 中的 `win/ia32/7za.exe`，构建时校验PE32 |

构建在 Windows 上进行，不要求在客户 Win7 机器上编译。准备 Node/npm、JDK21、curl、7-Zip及经校验的 NSIS 编译器。Electron/Java/MySQL 下载版本及摘要取 `bw-frontend/scripts/windows-ia32-components.json`，不要使用其他架构或自动更新到不同版本。

NSIS 3.0.4.1 原始包：`https://github.com/electron-userland/electron-builder-binaries/releases/download/nsis-3.0.4.1/nsis-3.0.4.1.7z`。本轮使用的官方 SHA-512（Base64）为 `VKMiizYdmNdJOWpRGz4trl4lD++BvYP2irAXpMilheUP0pc93iKlWAoP843Vlraj8YG19CVn0j+dCo/hURz9+Q==`。将其解压到稳定的构建工具目录；目录下应有 `Bin/makensis.exe`、`Include/` 和 `Stubs/`。不要依赖前一轮会话的临时缓存路径。

### 从源码到 compat2 的完整步骤

1. 在 `backend/` 下显式指定本机构建用 JDK21，运行后端构建与验证。例如 Windows PowerShell：

   ```powershell
   $env:JAVA_HOME = 'D:\build-tools\jdk21'
   .\mvnw.cmd -B -Pwindows-ia32 clean verify
   ```

   JDK 路径需换成本机实际路径；全量测试需要 Docker。成功后应产生 `backend/target/quarkus-app-win32/quarkus-run.jar`。若只是调试采用受影响测试构建，交付记录必须注明未完成全量验证，不能沿用旧测试结论。

2. 在 `bw-frontend/` 下安装锁定依赖、准备运行组件：

   ```powershell
   npm ci
   npm run prepare:win7-32
   ```

    若更新了后端、迁移或业务初始化资料，先将旧的 `bw-frontend/bin/integrated/windows-ia32/` **移出打包资源目录并归档**，再运行准备命令；准备脚本遇到已有目录只校验，不会自动覆盖成新后端。不要移动运行中的用户数据库目录。原始下载可用 `NIP_MYSQL_CACHE_DIR` 指向持久缓存，准备完成后打包不需客户联网。

   同时恢复 `bw-frontend/frontend/public/equipment/` 与 `bw-frontend/frontend/public/J210-742/` 的完整资源。两者已被Git忽略，干净克隆不会自带；必须按统一检查清单核对入口、loader引用和最终归档，不能只以Vite构建成功判断资源齐全。

3. 仅生成应用目录，避免再次使用有问题的默认安装器：

   ```powershell
   npm run build-e-win7-32 -- --dir
   ```

   输出为 `bw-frontend/out/win7-ia32/win-ia32-unpacked/`。该步骤会构建前端，并执行整合组件、工具架构、生产依赖清单及可执行情况下的包内依赖加载检查。不得从旧 `out` 目录直接取文件冒充本次产物；日志中的源码提交/dirty状态应与本次构建匹配。

4. 仍在 `bw-frontend/` 下封装 compat2：

   ```powershell
   $env:ELECTRON_BUILDER_NSIS_DIR = 'D:\build-tools\nsis-3.0.4.1'
   node scripts/build-win7-compatible-installer.cjs
   ```

   `ELECTRON_BUILDER_NSIS_DIR` 指向 NSIS 根目录，不是 `Bin` 目录。脚本生成文件名带 `-ia32-compat2.exe` 的安装器、`-ia32-unpacked.zip` 和 `win7-compat2-report.json`。必须分发 compat2 文件；不带 compat2 后缀的原安装器仅保留为历史证据。

5. 在 `bw-frontend/` 下执行安装脚本回归检查，再用独立安装目录实际验证：

```text
node --test electron/test/win7-installer.test.cjs electron/test/windows-serial-discovery.test.cjs electron/test/graphics.test.cjs electron/test/integrated-runtime.test.cjs electron/test/packaged-runtime.test.cjs
```

构建工具检查不含 `System.dll`，NSIS 编译使用 `/WX` 将警告视为错误。实际验证包括安装成功、中文/空格路径、安装后的文件摘要一致、依赖加载、数据库/后端/文件服务启动、登录及正常退出；卸载检查必须确认用户数据和未知文件仍保留。每次记录新包的文件大小、SHA-256、源码版本、组件版本和测试环境，不能把下方3.1.1产物的摘要当作新版本摘要。

静默安装验证时，NSIS 的 `/D=目标目录` 必须位于命令行末尾；通过 Node 调用且目录含空格时使用 `windowsVerbatimArguments: true`，否则NSIS可能忽略目标目录并安装到默认位置。卸载验证的 `_?=目标目录` 同理。正常交互安装不受此测试命令传参问题影响。

版本升级时同步应用/前端package.json及各自锁文件根版本，后端版本按实际发布记录。当前封装器从应用package.json传入 `APP_VERSION`，NSIS用其生成 `VIProductVersion`、版本字符串和卸载项 `DisplayVersion`；不再沿用旧硬编码版本。检查最终EXE与app.asar的版本及产物文件名一致。

### 后续打包必须保留的行为

- 不重新引入 `System::Call`、UAC、StdUtils或nsDialogs插件路径来替代已成功的安装流程。用户不需要为本次安装器崩溃另装所谓“System.dll环境插件”。
- 大型程序资源先用7z压缩，16MiB字典；NSIS 使用 `SetCompress off` 嵌入压缩数据，由32位7za解压。不要恢复直接 `File /r` 压缩整套未压缩目录，本轮曾遇到32位编译器内存映射失败。
- 默认安装目录和新快捷方式保持清晰可辨；数据库端口6912、后端18001、文件服务8000，数据保留在 `system-win32-mysql57`。不要复制MySQL8物理目录到该版本。
- 安装器只复制资源并创建快捷方式，不在安装过程中初始化数据库或自动启动应用；首次启动流程由Electron管理。
- ZIP作为同版本备用交付保留，并做完整性测试。构建失败或最终校验失败时，旧的同名输出文件不能视作本次成功产物。

如果 ZIP 版也在启动时出现“停止工作”，应记录这次故障的应用名、故障模块、异常代码和系统版本/SP1状态；这将是不同于原安装器 `System.dll` 的下一层问题，不能继续按安装器故障处理。

## 本轮产物与实测结果

| 文件 | 大小（字节） | SHA-256 |
| --- | --- | --- |
| `多功能报务综合训练系统-win7-3.1.1-ia32-compat2.exe` | 1,286,438,925 | `f1b4b7befec91b91f57e20d9cee0c2e665fb694a483b55e645f8168433a8c776` |
| `多功能报务综合训练系统-win7-3.1.1-ia32-unpacked.zip` | 1,465,191,450 | `81868d3a0484ceb3de468d2819e84a55249740ae783cbe0aea38668ce3adb8f5` |

- 构建返回0，新安装器内部条目没有 `System.dll`；最终编译日志没有 NSIS 警告。
- 已执行真正的兼容安装EXE，在本机构建环境以静默模式安装到独立中文/空格目录，退出码0；不是只解压检查。
- 安装后的 app.asar 与已校验原产物一致，运行组件与MySQL工具摘要检查通过。
- 使用实际安装后的32位 Electron 执行生产依赖及NeDB持久化检查，通过。
- 在安装目录新增未知文件后执行卸载：app.asar 被删除、未知文件内容保留；测试结束清理自身临时目录与测试注册项。
- ZIP 执行7-Zip完整性测试通过。
- 2个安装脚本相关回归测试通过，覆盖不使用插件、命令引号、路径转义和非递归卸载清单。
- 上述自动化和隔离安装测试来自 Windows 构建机。后续用户反馈见下一节，二者证据范围分开记录。

## 用户现场反馈（2026-11-06）

- 环境来源：用户上一条报障明确为 **Windows 7 64 位**，运行本轮32位整合应用。
- 原始问题：默认安装器 `多功能报务综合训练系统-win7-3.1.1-ia32.exe` 出现 `APPCRASH / System.dll`。
- 使用方案：改用本文上方列出的 compat2 安装包，组件与旧32位应用保持一致，替换安装器实现。
- 用户原话：**“compat2这个能正常安装使用”**。
- 记录结论：compat2 已获得该用户 Win7 64位机器上安装与使用成功的反馈，作为后续相同环境的打包基线。
- 证据边界：用户未提供具体SP1/系统补丁、CPU、内存、驱动信息，也未逐项报告所有训练、音频、串口、升级和卸载验收。此反馈不等同于 Win7 32位操作系统已通过测试，也不推翻 `docs/guides/initial-business-data.md` 中已有的源资料缺项。
