# 桌面整合软件打包与交付检查清单

更新日期：2026-11-06。适用于Windows x64、Win7 ia32兼容包、银河麒麟V10桌面ARM64整合包。每次发布从本文开始，再按对应平台指南执行。本文的检查项是发布要求，不表示已全部实现为自动构建门禁。

当前交付入口：Win7为 [HJ 3.1.3完整包](2026-11-06-win7-hj-3.1.3-release.md)，麒麟ARM64为 [HJ 3.1.2整合包](2026-11-06-hj-desktop-release.md)。源码当前应用版本3.1.3，不代表磁盘上的旧安装包自动升级。新克隆仓库后，必须先补齐本页列出的Git忽略资源，再构建。

## 1. 先确定目标和组件组合

| 目标 | Electron | Java / MySQL | 后端输出 | 平台指南 |
| --- | --- | --- | --- | --- |
| Windows x64 | 30.5.1 x64 | Java21 x64 / MySQL8.0.46 x64 | `backend/target/quarkus-app` | [整合部署](integrated-desktop-install.md) |
| Win7兼容版（应用为32位） | 22.3.27 ia32 | Java21 x86 / MySQL5.7.44 Win32 | `backend/target/quarkus-app-win32` | [compat2完整流程](win7-installer-system-dll.md) |
| 麒麟V10桌面ARM64 | 30.5.1 ARM64 | Java21 AArch64 / MySQL8.0.46 AArch64 | `backend/target/quarkus-app` | [麒麟完整流程](kylin-v10-arm64-install.md) |

- `aarch64`与Debian的`arm64`对应；不能把Linux ARM64当成Windows ARM64，也不能把麒麟桌面deb流程直接用于服务器RPM系统。
- Electron、Java、MySQL服务端/客户端、原生依赖及安装器辅助程序必须核对目标架构；不要只核对主EXE。
- 固定下载版本和摘要以 `bw-frontend/scripts/windows-ia32-components.json`、`bw-frontend/scripts/kylin-arm64-components.json` 等组件清单为准。升级组件后重新验证，不能沿用旧组合结论。
- Win7当前现场成功证据来自Win7 **64位操作系统运行ia32应用**；不代表Win7 32位操作系统、全部补丁或外设已验收。Electron30 x64包不能作为Win7兼容包交付。

## 2. 每次重新构建，避免旧产物混入

按以下顺序执行，不能只运行最后的安装器封装命令：

1. 记录源码提交、工作区未提交改动、目标系统/架构、组件版本及本次版本号。核对Git状态，保留用户工作。
2. 在 `backend/` 下显式设置JDK21，执行构建测试；Win7使用 `windows-ia32` profile。全量验证需要Docker。标准构建为 `./mvnw -B clean verify`，Win7为 `./mvnw -B -Pwindows-ia32 clean verify`；Windows PowerShell使用 `.\mvnw.cmd`。不能以跳过测试或历史测试计数交付为“全绿”。
3. 如果后端、迁移、初始化SQL或组件发生变化，将旧 `bw-frontend/bin/integrated/<目标>/` 移出打包资源目录并归档，再准备新资产。已有资产通过摘要校验，不代表它来自最新源码；准备脚本不会自动覆盖旧资产。只处理构建资产，不移动用户数据库。
4. 按下节补齐Git忽略的设备资源，并确认 `bw-frontend/frontend/src/config/runtime.js` 中 `window.interfaceStyle = 'HJ'`。在 `bw-frontend/` 下按平台指南准备运行组件，重新构建前端及Electron应用目录。检查构建日志，不能把旧 `out/*-unpacked` 当作本轮成功输出。
5. 从本轮应用目录生成最终安装器；记录最终文件大小、SHA-256和构建退出码。升级版本或Debian修订号，避免新旧文件同名混淆。当前deb脚本将应用版本附加 `-1`，后续仅修正封装时须明确递增修订号。
6. 从**最终安装器**提取或实际安装，核对安装后的文件，再运行验收。工作区源码正确不等于交付包正确。

常用桌面构建命令（工作目录均为 `bw-frontend/`，先完成平台资产准备）：

```text
# Windows x64
npm run build-e-w -- --x64

# Win7：只生成应用目录，再走compat2；NSIS环境变量见平台指南
npm run build-e-win7-32 -- --dir
node scripts/build-win7-compatible-installer.cjs

# 麒麟ARM64：必须先重新生成应用目录，再封装deb
npm run build-e-kylin-arm64
python scripts/build-kylin-deb.py
```

`build-kylin-deb.py`与compat2封装器不会重新编译应用。当前Win7封装器会逐字节比较包内 `main.js`、图形配置、初始化模块和两个串口模块与源码，不一致时中止；不要绕过该检查，应重新生成应用目录。该检查不代表所有前端资源或后端资产已经刷新。

版本更新须同步 `bw-frontend/package.json`、`bw-frontend/frontend/package.json` 及各自package-lock.json的根版本。当前NSIS版本由封装器传入的 `APP_VERSION` 派生（文件版本末尾补 `.0`，卸载注册项使用应用版本）；不再手工保留旧的3.1.1常量。仍需核对最终EXE版本、归档版本、文件名和报告一致。后端版本按实际后端发布情况记录。

### Git忽略的设备资源：打包前必须补齐

以下两个目录已在 `bw-frontend/.gitignore` 中忽略，仅保留在本机，不随Git提交或拉取：

| 本地源目录（仓库根相对路径） | 当前入口 |
| --- | --- |
| `bw-frontend/frontend/public/equipment/` | `121C/index.html`、`134A/index.html`、`173/index.html`、`FS/index.html`、`JS/index.html`、`KZ/index.html` |
| `bw-frontend/frontend/public/J210-742/` | `index.html`、`Build/`、`TemplateData/` |

换电脑、干净克隆、CI或重装环境时，须从维护的本地备份/资源交付件恢复完整目录；Git不会恢复这些文件。记录来源、资源版本及文件摘要，不只复制index.html，也不要用空目录或占位文件代替。清理时避免未经核对的 `git clean -fdx`，它会删除这些被忽略的本地资源以及组件缓存。

**Git忽略不等于不打包。** 当前Vite使用默认public目录，构建会把资源复制到 `bw-frontend/frontend/dist/equipment/`、`dist/J210-742/`；桌面准备脚本再复制到 `bw-frontend/public/dist/`，最终随 `app.asar/public/dist/` 分发。不要将这两个目录加入Electron排除规则。

在 `bw-frontend/` 下可先执行入口检查（PowerShell）：

```powershell
$entries = @('equipment/121C', 'equipment/134A', 'equipment/173', 'equipment/FS', 'equipment/JS', 'equipment/KZ', 'J210-742')
foreach ($entry in $entries) {
    $index = Join-Path 'frontend/public' "$entry/index.html"
    if (!(Test-Path -LiteralPath $index -PathType Leaf)) { throw "缺少设备资源入口：$index" }
}
```

此命令只检查入口，不证明资源完整。还必须核对各index/loader引用的真实文件（例如WASM、data、JS、压缩资源和模板资源）及大小写，按本次资源清单比较源目录、前端dist和最终安装后的归档文件摘要。在实际设备仿真页面确认iframe入口、资源加载及WebGL渲染正常，没有404或无限加载。现有构建manifest只记录构建时已有文件，不能发现构建前整个资源目录已缺失；尚无自动资源完整性门禁，缺失时应停止交付并补齐后重建。

## 3. 本次故障形成的必查项

| 已发生故障 | 根因或已确认事实 | 后续必须检查 |
| --- | --- | --- |
| NeDB模块找不到 | `node_modules/node_modules`重复依赖树导致归档依赖层级错误 | 打包前布局检查、包内生产依赖闭包检查；可执行的同架构环境运行包内Electron/NeDB持久化探测 |
| Win7安装器 `APPCRASH / System.dll` | 原NSIS插件路径在用户环境失败，compat2现场安装成功 | 使用compat2，保留无System/UAC/StdUtils/nsDialogs插件的流程及32位7za；实际执行最终安装器 |
| 麒麟 `config.json.dpkg-new：没有那个文件或目录` | data.tar只有文件，没有父目录条目 | 所有父目录显式写入归档，并排在子目录/文件之前；实际 `dpkg --unpack` 或目标机安装 |
| `Your browser does not support WebGL` | 旧入口同时禁用了GPU和软件渲染；重新封装旧应用目录又带回旧参数 | 检查最终app.asar入口与图形配置，保留软件渲染库；在实际功能页验证WebGL |
| APT `Pathname to install is not absolute` | 现场APT处理相对路径报内部错误 | 本地包使用带引号的绝对路径；依赖齐全时可使用 `dpkg -i`，保留原始输出 |
| Win7补丁 `SHA256Managed` 没有 `Dispose` 方法 | 旧版.NET的HashAlgorithm没有公开此方法，校验阶段失败 | 哈希清理使用兼容旧.NET的 `Clear()`，文件流用 `Close()`；修复工具也必须在目标PowerShell/.NET环境验收，不能仅验证业务EXE |
| 设备管理器有COM口，软件一直搜索不到 | 无超时的WMI枚举存在挂起风险，旧自动选择仅识别名称里的USB | Windows读取SERIALCOMM并限时；测试多个COM口和失效选择，必须按用户选择精确连接；设备管理器正常不等于软件枚举/连接已验收 |

### WebGL必须检查最终包

- `bw-frontend/main.js` 在应用就绪前调用 `configureGraphics(app)`。不要恢复 `disableHardwareAcceleration()`、`disable-gpu`、`disable-software-rasterizer` 这组旧配置。
- 当前 `bw-frontend/electron/core/graphics.js` 对Win7和Linux ARM64默认选择ANGLE/SwiftShader；`NIP_GRAPHICS_MODE=auto`可显式选择自动模式。其他平台默认行为按当前源码核对。
- 麒麟应用目录保留 `libEGL.so`、`libGLESv2.so`、`libvk_swiftshader.so` 及完整Electron分发资源。不能为了缩包随意删掉软件渲染组件。
- 从最终包的app.asar提取 `main.js`、`electron/core/graphics.js`，与本轮源码逐字节比较；包中缺少graphics模块或仍含旧禁用参数时不得交付。
- 在目标系统打开实际使用WebGL的功能页，检查页面无提示，WebGL上下文创建成功并可绘制/读回像素。启动窗口正常、配置单测通过或`--version`成功均不能替代此项。
- 自动绘制探测必须断言像素结果文件存在且内容正确；打包后的Electron可能忽略位置参数中的测试脚本并直接启动app.asar，退出码0并不说明测试执行过。需要隔离入口时保留最终包的EXE/DLL和配置，并明确记录测试入口与目标系统范围。
- 定向补丁只修复已安装实例，不会更新原deb/EXE或 `out/*-unpacked`。下次全量包必须从当前源码重新构建，不能让用户长期靠历史补丁补齐新安装。

### Debian目录、权限和实际解包

- `bw-frontend/scripts/build-kylin-deb.py` 的 `add_parent_directories` 必须保留。父目录条目为root属主、0755；ELF、启动器和维护脚本为0755；普通资源为0644。Windows文件权限不能直接作为Linux权限依据。
- `dpkg-deb --info`只检查元数据；APT模拟安装只检查依赖方案；普通tar提取可能自动创建父目录。这三项通过均不等于dpkg能安装。
- 先运行封装回归测试，再对最终包做真实解包。下面的Bash示例在Linux/WSL创建隔离临时根目录，不在宿主系统安装应用；用本轮包的绝对路径替换 `deb`：

```bash
(
set -e
deb="/absolute/path/to/nip-traffic-system-kylin-arm64_3.1.2-1_arm64.deb"
root="$(mktemp -d /tmp/nip-deb-check.XXXXXX)"
sudo mkdir -p "$root/var/lib/dpkg"
sudo touch "$root/var/lib/dpkg/status"
sudo dpkg --root="$root" --force-architecture --unpack "$deb"
# 上一条必须返回0；失败时先保存输出，不能继续记为通过。
test -f "$root/opt/nip-traffic-system-kylin-arm64/bin/config.json"
test -x "$root/usr/bin/nip-traffic-system-kylin-arm64"
# 仅清理本次mktemp创建的测试根目录，root变量不可替换为系统/用户目录。
sudo rm -rf -- "$root"
)
```

`--force-architecture`仅用于x64构建机解包ARM64文件；目标ARM64机器不需要，也不要使用强制忽略依赖的选项。隔离解包不执行ARM64程序，也不代表postinst和桌面入口已验收；仍需目标机实际安装。

### Win7串口必须分层验收

- 驱动层：官方CH340安装程序、CAT及x86/x64驱动签名和摘要正确；现场设备管理器出现CH340对应COM号，设备状态正常。不要通过关闭签名验证绕过驱动错误。
- 枚举层：最终包中的 `electron/serial/nativeSerialPort.js` 使用SERIALCOMM，8秒超时，不恢复无超时的全量WMI查询。测试多个COM口、去重排序、无设备与查询超时，确认“搜索串口中”能够结束。
- 选择层：最终包中的 `electron/serial/index.js` 按用户COM号匹配。已选设备拔出后不得自动连接其他端口；多个USB串口时要求明确选择。其他串口可共存，不应建议用户卸载它们。
- 连接层：在软件选择CH340对应COM号，实际打开、收发、断开重连；设备管理器可见或软件枚举成功不代表收发已通过。原3.1.2串口补丁已收到客户“可行”反馈，3.1.3已合入同一修复；每次新包仍需核对最终代码和本次验证证据。
- 失败时收集注册表端口列表、应用枚举结果、设备PnP错误码及选中的COM号。3.1.2定向补丁附带 `check-serial.cmd` / `serial-report.json`；不能将其修复脚本强行用于3.1.3等未知版本。

## 4. 依赖、初始化资料和用户数据

- 麒麟依赖保留glibc最低要求2.28及桌面库声明。采集 `cat /etc/os-release`、`uname -m`、`dpkg --print-architecture`、`getconf GNU_LIBC_VERSION`；内核版本不能推断glibc。不要通过手工替换系统glibc解决兼容问题。
- 服务端与客户端工具都要带齐依赖和许可，manifest中平台和逐文件摘要必须匹配。x64/ia32/ARM64资产分别放置，禁止跨目标混装。
- Win7离线包需先运行 `node scripts/prepare-ch340.cjs` 准备经摘要和签名校验的官方CH340驱动；安装器核对驱动清单，交互安装后启动厂商安装程序，开始菜单保留重试入口。厂商Windows驱动包含多架构，不能按应用ia32位数裁掉x64驱动。静默安装需单独安装驱动；目标机用设备管理器和实际收发验收，不能以文件存在代替设备就绪。流程见 [HJ 3.1.2交付记录](2026-11-06-hj-desktop-release.md)。
- Java使用完整运行目录，Quarkus使用完整fast-jar目录；MySQL包含程序、依赖和基础资源。不能只复制单个jar或EXE。
- 初始化SQL只取白名单业务资料，不打入开发库用户、token、训练记录、数据库实例配置或真实口令。确认资料来源与附件真实存在；已知题库分类、章节关联和旧附件地址缺项见 [初始化资料说明](initial-business-data.md)，不得标成已补齐。
- 默认端口数据库6912、后端18001、文件服务8000。端口冲突不能擅自停止客户的其他服务。测试用独立实例，避免连接开发库或客户库。
- 用户数据在userData的 `system`，Win7 ia32为 `system-win32-mysql57`。升级、补丁和卸载保留用户数据；不能复制MySQL8物理目录给MySQL5.7。
- 新实例管理员为 `admin`，默认密码固定为 `123456a`；数据库内保存PBKDF2摘要。数据库root/应用连接口令仍随机生成。验收须确认默认账号可登录，且升级/重启不重置已有账号密码。此修改需要重新构建Electron应用目录才能进入安装包，不能从旧包的随机密码提示推断源码行为。
- 首次初始化、迁移、登录、备份恢复、正常关闭后再次启动均需验证；关闭数据库应等待进程退出，不能只检查端口关闭。
- Windows验证中文/空格路径、普通用户权限；Linux安装可sudo，业务应用必须以普通桌面用户启动。

## 5. 验收命令与交付记录

在 `bw-frontend/` 下执行适用检查，测试数量以本次输出为准：

```text
node --test electron/test/graphics.test.cjs electron/test/packaged-runtime.test.cjs
python scripts/test-kylin-deb.py
node --test electron/test/win7-installer.test.cjs electron/test/windows-serial-discovery.test.cjs electron/test/integrated-runtime.test.cjs
```

第二条用于麒麟封装，第三条用于Win7。根据改动补充受影响测试及AGENTS.md要求的后端全量验证。以上检查不能替代最终安装器和目标机运行。

每次交付复制并填写以下清单，未执行项写明原因，不勾选为通过：

- [ ] 已记录源码提交/dirty状态、应用版本、安装包修订号和固定组件版本。
- [ ] `equipment`、`J210-742`已从明确来源恢复并核对完整资源，最终归档包含它们，设备仿真页面实际可加载。
- [ ] HJ配置及应用/前端/锁文件版本正确，最终安装器版本与报告一致。
- [ ] 后端与目标profile正确，相关测试及要求的全量验证通过并保存日志。
- [ ] 运行组件、SQL及迁移已刷新；本轮应用目录确实由本轮源码生成。
- [ ] 最终包内app.asar、运行组件摘要已核对；不存在重复依赖树和旧WebGL禁用参数。
- [ ] 安装器按目标格式/架构构建；deb父目录和权限检查、实际解包通过，或Windows实际安装通过。
- [ ] 干净目标环境实际安装、首次启动、初始化、登录、文件服务和正常停机通过。
- [ ] 目标机实际WebGL页面通过；音频、串口等硬件项分别记录实测结果。
- [ ] Win7 CH340驱动入口可用；多COM枚举、精确选口、失效选择和实际收发分别验证，未把驱动存在当作连接成功。
- [ ] 升级保留数据、备份恢复、重启、卸载保留数据已验证。
- [ ] 最终文件名、大小、SHA-256、安装说明与报告一致；没有把失败构建留下的旧文件当成新包。
- [ ] 已区分构建机检查、仿真、目标机检查与客户反馈；剩余限制已写入交付说明。

## 6. 当前产物状态，避免误用历史包

| 平台 | 当前完整包 | 已包含修复与验收边界 |
| --- | --- | --- |
| Win7 | HJ 3.1.3 compat2 | 串口、WebGL、CH340离线驱动、固定初始密码；本机实际覆盖升级、登录/重启、WebGL和卸载通过；客户已确认先前同一串口修复补丁可行，见 [3.1.3交付记录](2026-11-06-win7-hj-3.1.3-release.md) |
| 麒麟V10 ARM64 | HJ 3.1.2-1 deb | 父目录条目、WebGL配置和固定初始密码已合入；实际隔离解包及运行资产摘要通过，原生图形/音频/串口仍需现场验收，见 [3.1.2交付记录](2026-11-06-hj-desktop-release.md) |

Win7 3.1.2完整包需串口补丁，优先交付3.1.3；3.1.1及其修正版/补丁仅用于历史追溯，不再作为当前安装步骤。历史包不会随源码更新；新产物重新计算摘要，旧摘要不能替代本轮证据。清理仅针对明确的旧安装器、补丁与构建输出，保留源码、两套本地设备资源、组件来源缓存及用户数据。
