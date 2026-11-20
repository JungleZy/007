# 麒麟 V10 SP1 龙芯一体化打包

更新日期：2026-11-19。每次打包先执行 [docs/guides/desktop-packaging-checklist.md](desktop-packaging-checklist.md)。
当前交付为 HJ、HJJ、LJ 三个 3.1.3-1 安装包，统一位于 `bw-frontend/out/releases/`；该目录最终只保留安装包。

目标为银河麒麟桌面 V10 SP1 2403（2024-05-06 发行镜像）、LoongArch64、glibc 2.28。
本流程采用旧 ABI（ELF flags `0x3`，加载器 `/lib64/ld.so.1`），deb 架构为 `loongarch64`。
新世界 `loong64` Debian 系统需另行准备运行时，不能共用本安装包。

## 组件来源

- Electron 30.5.1：龙芯开源社区 `ftp.loongnix.cn/electron/LoongArch/v30.5.1/`，对照发布方 SHA-256。
- Java 21.0.12+8：龙芯开源社区非 `glibc2.34` 版，首次取得时对照发布方 MD5，并记录 SHA-256；准备脚本按固定 SHA-256 校验后续输入。
- MySQL 8.0.21：Loongnix Server 8.4 RPM，提取文件后随应用部署；不在麒麟上安装 RPM、不注册系统 MySQL 服务。
- MySQL 依赖库包括 libevent、protobuf-lite、OpenSSL、libaio、libnuma、libedit、ncurses、libxcrypt、lz4。
  各 RPM 的地址与仓库元数据 SHA-256 固定于 `bw-frontend/scripts/loongarch-mysql-rpms.json`。

组件版本清单为 `bw-frontend/scripts/kylin-loongarch64-components.json`。
桌面应用版本来自 `bw-frontend/package.json`，前端版本来自 `bw-frontend/frontend/package.json`，二者及各自锁文件根版本须一致；
Java 后端版本来自 `backend/pom.xml`，按实际后端构建单独记录。本轮桌面/前端为 3.1.3，后端为 3.1.1。

## 配置位置与架构命名

| 配置 | 位置 / 取值 | 生效范围 |
| --- | --- | --- |
| Electron、Java、ABI、加载器与摘要 | `bw-frontend/scripts/kylin-loongarch64-components.json` | 龙芯固定运行组件，不能改用 ARM64 或新 ABI 组件 |
| MySQL 与共享库下载锁 | `bw-frontend/scripts/loongarch-mysql-rpms.json` | 固定 RPM 地址及 SHA-256，包含客户端和服务端依赖 |
| MySQL 通用准备状态 | `bw-frontend/scripts/mysql-tool-sources.json` | 龙芯条目指向专用流程；`prepare:mysql` 不替代龙芯组件准备 |
| 构建风格 | `NIP_INTERFACE_STYLE=HJ\|HJJ\|LJ` | `bw-frontend/frontend/vite.config.js` 编译常量，缺省为 HJJ |
| 龙芯单风格入口 | `npm run build-e-kylin-loongarch64 -- --style HJ` | 设置风格变量，编译实际界面并生成独立应用目录 |
| Electron 解压目录覆盖 | `NIP_LOONGARCH_ELECTRON_DIR` | 默认 `bw-frontend/.cache/kylin-loongarch64-components/electron`；覆盖目录仍须为 30.5.1 和目标 ABI |
| npm 命令与通用桌面配置 | `bw-frontend/package.json` | 不使用旧模板的 `electron/config/builder.json` |

`uname -m` / 麒麟 deb 架构使用 `loongarch64`；项目运行时资源目标使用 `linux-loong64`；ELF machine 为 258。
这些名字处在不同层，不能把目录里的 `linux-loong64` 改成 `linux-loongarch64`。
目录名本身不能区分新旧 ABI，最终仍要检查 flags `0x3`、加载器 `/lib64/ld.so.1`、glibc 上限及实际系统。

## 构建

构建机需要 Node.js 20+、Python 3.11+、curl、bsdtar、JDK 21 和 Docker。
Windows 可使用 `py -3`，系统 `tar.exe` 为 bsdtar；Linux 需确保 `tar` 命令支持 `@archive` 转换 RPM。
系统只有 GNU tar 时，不能直接运行 RPM 提取步骤；应先准备 bsdtar 工具环境。Windows 上 `python` 可能是商店别名，先用 `py -3 --version` 确认解释器。
两层 npm 依赖须分别在 `bw-frontend/` 和 `bw-frontend/frontend/` 按锁文件准备。
先恢复 `bw-frontend/frontend/public/equipment/`、`bw-frontend/frontend/public/J210-742/` 等 Git 忽略的设备资源，
同时核对所需 `bw-frontend/bin/` 资源。构建会复制已有资源，但不能自动发现整个资源目录已缺失。
三风格会保留三套未压缩目录，每套约 2.7 GB；另需安装包、压缩暂存目录、前端输出和组件缓存空间。
先在 `backend/` 显式设置 JDK 21 并执行 `mvnw.cmd -B clean verify`（Linux 为 `./mvnw -B clean verify`）。
只有成功后，才使用 `backend/target/quarkus-app/` 制作运行时。

以下命令在 `bw-frontend/` 执行：

```powershell
py -3 scripts/prepare-loongarch-components.py
node scripts/bundle-mysql-tools.cjs .cache/kylin-loongarch64-components/mysql linux-loong64
node scripts/prepare-integrated-runtime.cjs .cache/kylin-loongarch64-components/mysql .cache/kylin-loongarch64-components/java linux-loong64
npm run build-e-kylin-loongarch64
py -3 scripts/build-kylin-deb.py --arch loongarch64
```

脚本拒绝覆盖已有组件、工具库、集成运行时和桌面输出目录；重建前先归档相应目录。
下载缓存在 `bw-frontend/.cache/`，每次使用都检查固定的 SHA-256。
后端源码、迁移、种子 SQL 或运行组件变化后，重新准备 `bw-frontend/bin/integrated/linux-loong64/`；
已有 manifest 校验通过仅说明文件未变，不保证包含刚构建的后端。只改变界面风格时，可复用本轮已经验证的相同后端与组件。
重新编译或封装前保留旧产物的来源和摘要，不用 `git clean -fdx` 清空本地资源，也不操作用户数据库。

当前 electron-builder 22 没有 `loong64` 架构枚举，因此龙芯入口直接装配供应商 Electron 与应用 ASAR，
沿用项目的前端构建、产物清单、生产依赖闭包、MySQL 工具和集成运行时校验。
生产依赖按 Node 的实际解析路径复制，遇到原生 `.node` 模块立即失败，避免夹带构建机二进制。

输出目录为 `bw-frontend/out/kylin-v10-loongarch64/`：

- `linux-loong64-unpacked/`：应用目录。
- `nip-traffic-system-kylin-loongarch64_<版本>-1_loongarch64.deb`：安装包。
- `kylin-loongarch64-report.json`：大小、SHA-256、ELF 数量、glibc 符号上限和验证范围。

## 校验及运行边界

```powershell
py -3 scripts/test-kylin-deb.py
node --test electron/test/packaged-runtime.test.cjs electron/test/integrated-runtime.test.cjs
py -3 scripts/verify-loongarch-mysql.py .cache/kylin-loongarch64-components/mysql
```

组件准备检查 MySQL 直接和间接共享库清单，deb 生成检查所有外部 ELF 的机器类型、旧 ABI flags 和 glibc 符号上限，
并在归档中写入 Linux 可执行权限。基础系统仍需提供 glibc、libstdc++、libgcc、zlib 和桌面图形/音频库。
这些检查不等于在目标机器完成动态链接或启动验收；本机 x86_64 后端测试也不替代龙芯设备测试。

在目标麒麟机器安装、启动：

```bash
sudo apt install ./nip-traffic-system-kylin-loongarch64_3.1.3-1_loongarch64.deb
nip-traffic-system-kylin-loongarch64
```

使用普通桌面用户启动。首次启动会初始化应用自己的数据库和基础资料。
目标机验收应包括首次初始化、重启、登录、数据库备份/恢复、串口电键和声音输出。
若安装器报告架构不匹配，先核对 `dpkg --print-architecture`，不要强制忽略架构检查。

## 2026-11-19 首个默认风格包的历史验证记录

- JDK 21 执行 `mvnw.cmd -B clean verify`：639 项测试，0 失败、0 错误、0 跳过，BUILD SUCCESS。
  Maven 原有配置跳过的 native Failsafe 阶段不计入该 JVM 测试结果。
- 首次全量运行发现 `PostTelexPatTrainServiceTest` 未适配已存在的 evidence 数组与延迟解析数字；
  修正测试解析类型后，该类 12 项测试及上述全量验证通过，业务实现未改动。
- Debian 打包测试 5 项通过；桌面依赖与集成运行时测试 9 项通过。
- Electron/Java 发布包中的 92 个 ELF 符合 LoongArch64 和 glibc 2.28 上限；MySQL 的 53 个 ELF 通过旧 ABI 与共享库清单检查。
- ASAR 生产依赖清单共 111 个；同一 ASAR 使用构建机 Windows Electron 执行模块加载和 NeDB 持久化探针，返回 `PACKAGED_RUNTIME_OK`。
  这项验证不代表 LoongArch Electron 已运行。
- 最终 `.deb` 为 1,428,101,504 字节，包含 195 个目标 ELF，最高 glibc 符号为 2.28。
  已从实际归档逐一比对 1,857 个应用文件的 SHA-256、ELF 可执行权限和最终包摘要。
  包 SHA-256 为 `93961a4f605ed01db1982dc2d1fe38aed069a5dd25bbcb62cb0c491fa97066b1`。
  Debian bookworm 容器内 `dpkg-deb --info -` 通过标准输入读取实际安装包，确认包格式、版本、架构和维护脚本有效。
- 日志保存在 `bw-frontend/.cache/loongarch-backend-final.log`、`bw-frontend/.cache/loongarch-build.log`；
  最终安装包的大小、SHA-256 和 ELF 数量以输出目录中的 `kylin-loongarch64-report.json` 为准。

本次改动文件：

```text
backend/src/test/java/com/nip/service/PostTelexPatTrainServiceTest.java
bw-frontend/package.json
bw-frontend/scripts/build-loongarch-desktop.cjs
bw-frontend/scripts/prepare-loongarch-components.py
bw-frontend/scripts/inspect-loongarch-runtime.py
bw-frontend/scripts/verify-loongarch-mysql.py
bw-frontend/scripts/kylin-loongarch64-components.json
bw-frontend/scripts/loongarch-mysql-rpms.json
bw-frontend/scripts/mysql-tool-sources.json
bw-frontend/scripts/build-kylin-deb.py
bw-frontend/scripts/test-kylin-deb.py
docs/guides/kylin-loongarch64-packaging.md
docs/guides/database-backup-import.md
```

建议将后端测试修正与龙芯打包支持拆成两个独立提交；构建下载物和输出安装包留在忽略目录，不提交到 Git。

## HJ、HJJ、LJ 三风格安装包

在 `bw-frontend/` 运行以下命令，一次构建三个独立风格的安装包：

```powershell
py -3 scripts/build-loongarch-styles.py
node scripts/verify-loongarch-styles.cjs
py -3 scripts/verify-loongarch-styles.py
```

该流程使用已经准备并校验过的 `bin/integrated/linux-loong64` 与 MySQL 工具，
串行构建前端、分别保存桌面目录，再并行压缩三个安装包。压缩级别为 1，文件内容不因压缩级别改变。
最终交付目录为 `bw-frontend/out/releases/`，文件名分别为：

```text
多功能报务综合训练系统-LoongArch64-HJ-3.1.3-1_loongarch64.deb
多功能报务综合训练系统-LoongArch64-HJJ-3.1.3-1_loongarch64.deb
多功能报务综合训练系统-LoongArch64-LJ-3.1.3-1_loongarch64.deb
```

**脚本当前会在构建阶段临时生成非安装包文件**：`LoongArch64-SHA256SUMS.txt`、三个
`kylin-loongarch64-<风格>-report.json`，归档核对后还会生成 `LoongArch64-verification.json`。
先运行上述两个验证脚本，再按下一节归档或清理；脚本本身尚未自动执行交付清理。
`verify-loongarch-styles.py` 依赖这三个报告及各自的未压缩目录，不能提前删除。
本轮已按用户要求删除交付目录中的六个非安装包文件（含安装说明），现在 `out/releases/` 只保留 12 个 `.deb` / `.exe` 安装包。
删除报告后不能直接重跑该 Python 校验；需要恢复与安装包摘要一致的报告和构建目录，不能将报告缺失误判为安装包损坏。

构建入口通过 `--style HJ|HJJ|LJ` 传入 `NIP_INTERFACE_STYLE`，由 Vite 编译到运行时。
不传时默认 HJJ。脚本核对实际编译的 `window.interfaceStyle`，并将风格写入包内前端清单及
`resources/interface-style.json`；后者绑定 ASAR 摘要，deb 打包时检查是否与指定风格一致。
风格选择或 ASAR 摘要不一致会阻断生成。重建前须归档相应风格目录及同名安装包。

三个安装包是同一应用的界面变体，沿用相同 Debian 包名和安装路径；目标机选择一种风格安装，
安装另一种会替换应用界面，不是三个并存应用。目标机首次初始化、串口和音频验收要求同上。

### 单独重建一种风格

以下命令在 `bw-frontend/` 执行，示例为 HJ；HJJ、LJ 同时替换命令中的风格值与目录名：

```powershell
npm run build-e-kylin-loongarch64 -- --style HJ
py -3 scripts/build-kylin-deb.py --arch loongarch64 --style HJ --source out/kylin-v10-loongarch64/HJ/linux-loong64-unpacked --output out/releases --compression 1
```

| 封装参数 | 当前实现 |
| --- | --- |
| `--arch loongarch64` | 必须显式传入；缺省为 `arm64` |
| `--style HJ\|HJJ\|LJ` | 核对包内风格标记与 ASAR 摘要；当前仅支持龙芯风格包 |
| `--source` | 风格包必须传对应的独立应用目录；Python 封装器不会根据 `--style` 自动选择它 |
| `--output out/releases` | 最终安装包和构建报告暂存位置；不提供时输出到架构构建目录 |
| `--compression 0..9` | CLI 默认 4，三风格批量流程用 1；不改变包内内容 |
| Debian 修订号 | `build(..., revision='1')` 默认 1，CLI 当前无 `--revision` 参数；调整修订号须同步封装调用和批量重名检查 |

先检查每条命令成功退出，再执行下一步。风格应用目录或同名安装包已存在时，归档本次目标后重建；
仅重命名旧包不会改变界面。封装器读取指定应用目录，不会重新编译前端或后端。
不要同时启动多个独立前端构建进程，它们共享 `frontend/dist/` 和 `public/dist/`。

### 交付清理与校验留档

最终目录 `bw-frontend/out/releases/` **只保留 `.deb`、`.exe` 安装包**。
验证成功后，可将本轮报告和摘要归档到 `bw-frontend/.cache/release-evidence/<批次>/`，
构建日志留在 `bw-frontend/.cache/loongarch-style-<风格>.log`，未压缩目录留在 `bw-frontend/out/kylin-v10-loongarch64/<风格>/`。
文字说明保存在本指南，不重新向交付目录放 README 或安装说明。按本轮明确文件名处理，不递归删除整个 `out/`。

未来构建完成后的归档示例（`bw-frontend/` 下执行；必须先通过上述验证）：

```powershell
$evidence = Join-Path '.cache/release-evidence' (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Path $evidence -ErrorAction Stop | Out-Null
$names = @(
    'kylin-loongarch64-HJ-report.json',
    'kylin-loongarch64-HJJ-report.json',
    'kylin-loongarch64-LJ-report.json',
    'LoongArch64-SHA256SUMS.txt',
    'LoongArch64-verification.json',
    'LoongArch64-安装说明.txt'
)
foreach ($name in $names) {
    $source = Join-Path 'out/releases' $name
    if (Test-Path -LiteralPath $source -PathType Leaf) {
        Move-Item -LiteralPath $source -Destination $evidence -ErrorAction Stop
    }
}
Get-ChildItem -LiteralPath 'out/releases' -Force |
    Where-Object { $_.PSIsContainer -or $_.Extension -notin @('.deb', '.exe') }
```

最后一条正常应无输出；若发现失败构建遗留的 `deb-stage-*`，先确认构建进程已退出，再处理该批暂存目录。
上述归档是后续发布步骤，不表示本轮已删除的报告仍存在于该归档目录。

### 本轮三风格摘要与安装

下表保留本轮已经核对的事实，替代被清理的旁挂报告作为文字查询入口；以后重建须重新计算，不能复用旧摘要。

| 风格 | 安装包字节数 | SHA-256 |
| --- | ---: | --- |
| HJ | 1,484,756,112 | `19e6c9b4fbfa36e6051290b5de10d2a12df6d90227d0bb14589333ab9bf92b11` |
| HJJ | 1,484,756,308 | `be505ef4345a8383737517338b5bdbca0c585b33e376732cdcb26dc4f0edfb6a` |
| LJ | 1,484,757,420 | `45dfe3bc4a13b42121cfa6e62cdc1ace345ee6e9f3565266803e169b7db599d1` |

在目标机安装包所在目录执行，使用带引号的绝对路径适配麒麟 APT：

```bash
sha256sum ./*LoongArch64*.deb
sudo apt install "$(pwd)/多功能报务综合训练系统-LoongArch64-HJ-3.1.3-1_loongarch64.deb"
nip-traffic-system-kylin-loongarch64
```

改用 HJJ 或 LJ 时替换安装包文件名；同版本切换风格用 `sudo apt install --reinstall "<安装包绝对路径>"`。
安装后仍用普通桌面用户运行。遇到架构、依赖错误，先保存系统信息和安装原始输出，不强制忽略架构或替换系统 glibc。

### 易错配置与验证边界

- `--linux --arm64` 会生成 ARM64 目标，不能用于龙芯；本机 electron-builder 22 也不能直接接收 `--loong64`。
- Windows Python 读取含中文产品名的 JSON、写报告使用 UTF-8；批量脚本为 Python 子进程设置 `PYTHONIOENCODING=utf-8`。
- ASAR 必须包含完整生产依赖和正确层级，不能夹带构建机 `.node` 模块；当前龙芯脚本遇到原生 Node 模块会中止。
- MySQL 的 `libedit`、`libcrypt` 等依赖也要核对；RPM 可能把库放在 `/usr/lib64` 或 `/lib64`。
  准备脚本处理这两种目录，并保留 `share` 与 RPM 基于 `basedir` 推导的 `share/mysql` 消息资源。
- `dpkg-deb --info` 证明元数据可解析，不能证明真实安装、动态链接或 GUI 已通过。
  本轮 Windows Electron 的 ASAR 探针只覆盖模块加载及 NeDB，不代表龙芯 Electron 图形界面已运行。
- WSL 或 Docker 宿主磁盘挂载报 I/O 错误时，先区分构建机访问故障与包损坏。本轮曾通过标准输入将包送入 Debian 容器完成元数据检查；
  不要用 Windows PowerShell 5 的文本管道传递 `.deb` 字节流。

本轮验证：前端既有 232 项测试通过；新增 4 项构建风格初始化测试通过；桌面依赖测试 3 项通过；
Debian 打包测试 6 项通过（含错标风格和 ASAR 被修改的拒绝场景）；修改的前端文件 lint 通过。
曾尝试运行旧发布流水线 `artifact-manifest.test.mjs`，其 Windows 环境缺少 `zip` 可执行文件，
5 项用例在创建夹具时失败；该发布脚本本轮未修改，三风格实际包另由上述校验命令检查。

三风格任务涉及的代码与测试文件：

```text
bw-frontend/frontend/src/config/runtime.js
bw-frontend/frontend/vite.config.js
bw-frontend/frontend/test/runtime-style.test.mjs
bw-frontend/scripts/build-loongarch-desktop.cjs
bw-frontend/scripts/build-loongarch-styles.py
bw-frontend/scripts/build-kylin-deb.py
bw-frontend/scripts/test-kylin-deb.py
bw-frontend/scripts/verify-loongarch-styles.cjs
bw-frontend/scripts/verify-loongarch-styles.py
```

三风格最终交付校验已通过：每包 1,858 个应用文件逐一核对，195 个旧 ABI ELF 的架构和执行权限一致；
包内编译风格与名称一致，三个 ASAR 的模块加载和 NeDB 探针通过，三个实际 `.deb` 均通过 `dpkg-deb --info -` 检查。
每包约 1.48 GB；构建时摘要和逐包结果曾记录在 `LoongArch64-SHA256SUMS.txt` 与 `LoongArch64-verification.json`，
随后已按交付目录仅保留安装包的要求删除。摘要保留在上表，验证日志为 `bw-frontend/.cache/loongarch-style-verification.log`。
本任务建议独立提交：`feat(packaging): 支持龙芯三风格安装包集中输出`。
