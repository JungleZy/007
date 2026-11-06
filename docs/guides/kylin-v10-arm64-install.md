# 银河麒麟 V10 桌面版 ARM64 整合包

每次打包先执行 [桌面打包与交付检查清单](desktop-packaging-checklist.md)。特别注意：必须重建应用目录以带入WebGL修复，deb必须显式包含父目录并通过实际dpkg解包；本页历史产物不自动随源码更新。

新构建环境须先恢复被Git忽略的 `bw-frontend/frontend/public/equipment/` 和 `bw-frontend/frontend/public/J210-742/`，核对入口及完整资源；Vite会将其复制到dist并随app.asar分发。最终包必须检查文件大小写、资源摘要和真实设备仿真页面加载，不能因目录不入Git就省略打包。

HJ风格3.1.2整合重打包、固定admin初始密码及本轮验证见 [HJ 3.1.2交付记录](2026-11-06-hj-desktop-release.md)。以下3.1.1版本的文件、摘要和补丁说明保留为故障追溯记录，历史打包输出已按用户要求清理。

目标由用户明确为 **银河麒麟桌面操作系统 V10 ARM64**，交付格式为 `.deb`。这不是 Windows ARM64 或麒麟高级服务器版 RPM 包。

## 运行组件与边界

- Electron 30.5.1 Linux ARM64。
- BellSoft Liberica Java21 21.0.12.1+1 Linux AArch64，随包提供，无需另外安装 Java。
- MySQL 8.0.46 Linux AArch64 服务端及客户端，随包提供，无需安装系统 MySQL 服务。
- Quarkus Java 后端使用标准 MySQL8 构建产物 `backend/target/quarkus-app`，不要误用 `quarkus-app-win32` 的MySQL5.7产物。
- 初始资料沿用当前业务资料种子，包含理论学习/题库、个人岗前、规则、报底及文章；源资料中的缺失分类/附件问题仍见 `docs/guides/initial-business-data.md`。
- 默认数据库端口6912、后端18001、文件服务8000。数据库只监听本机回环，Linux Unix socket 使用本实例 `config/mysql.sock`，不占用系统默认 `/tmp/mysql.sock`。
- 应用数据、凭据、上传文件、日志保存在当前用户的 Electron userData 下 `system/`。程序安装目录为 `/opt/nip-traffic-system-kylin-arm64`，更新和卸载不删除用户业务数据。

组件要求的最高 glibc 符号版本为2.28，因此 `.deb` 明确要求 `libc6 >= 2.28`。这只是运行库最低条件，不能由它推断所有麒麟V10补丁版本和CPU都已验收。

MySQL需要的OpenSSL、protobuf、libaio、libnuma等库随服务端分发，客户端需要的ncurses/tinfo也随工具分发；系统glibc/libstdc++以及GTK、NSS、音频、X11/图形库使用桌面系统自身组件。安装器声明其依赖，不替换系统全局共享库。原始文件下载源和摘要固定在 `bw-frontend/scripts/kylin-arm64-components.json`。

## 安装与启动

先确认机器架构和glibc：

```sh
uname -m
dpkg --print-architecture
getconf GNU_LIBC_VERSION
```

预期分别为 `aarch64`、`arm64` 和不低于2.28的glibc。可以双击 `.deb` 使用麒麟安装程序，也可在包所在目录执行：

```sh
sudo apt install "$(pwd)/nip-traffic-system-kylin-arm64_3.1.2-1_arm64.deb"
```

缺少声明的系统依赖时，需要从麒麟对应版本的软件源补齐；离线机器应预先准备这些系统依赖。应用内置Java和数据库并不表示可以在缺少图形桌面基础库的系统上运行。

安装后从应用菜单打开“多功能报务综合训练系统”，或使用普通桌面用户运行：

```sh
nip-traffic-system-kylin-arm64
```

**不要使用 sudo 启动业务应用。** 安装需要管理员权限，运行不需要；MySQL初始化到当前用户目录。当前源码首次初始化账号为 `admin`，默认密码为 `123456a`；已有实例保留原密码。历史安装包仍采用当时的初始化规则，需重新构建才会带入此修改。授权流程沿用项目原有逻辑，不会因新安装包绕过软件授权。

启动失败时提供具体错误窗口、系统版本（`cat /etc/os-release`）、上述架构/glibc输出及 userData 的 `system/logs/startup.log`；不要发送 `database-instance.json`，其中包含数据库凭据。

## 本地交叉构建流程

本轮在Windows x64准备ARM64组件、构建前后端与应用目录，并使用WSL Debian工具检查最终包。无需把构建机的架构伪装为ARM64。

1. 在 `backend/` 下显式使用JDK21，构建标准MySQL8产物。发布前按工程约定跑全量验证；本轮实际仅跑受影响的 `DatabaseTransferServiceTest`（7个测试通过）及package构建，没有把它记成全量验证。
2. 下载并核对 `bw-frontend/scripts/kylin-arm64-components.json` 中Electron/JRE/MySQL及libaio/libnuma归档摘要；MySQL/JRE解压源分别放入 `.cache/kylin-arm64-components/mysql`、`java`，Electron放入 `electron`。libaio/libnuma的ARM64共享库放入MySQL源的 `lib/private`，对应copyright放入 `dependency-licenses`，不复制本机x64库。
3. 在 `bw-frontend/` 下执行：

   ```text
   node scripts/prepare-integrated-runtime.cjs .cache/kylin-arm64-components/mysql .cache/kylin-arm64-components/java linux-arm64
   npm run build-e-kylin-arm64
   python scripts/build-kylin-deb.py
   ```

   `python` 指可用的Python3解释器。组件目录已存在时，先归档旧构建资源后重新准备，脚本不会覆盖正在使用的运行资源；不得删除用户数据库。

4. 应用目录为 `bw-frontend/out/kylin-v10-arm64/linux-arm64-unpacked`，最终包与报告位于同级目录。

`build-kylin-deb.py` 直接生成标准Debian ar归档、control.tar.gz和data.tar.xz，对所有文件显式设置root归属及Linux权限，不依赖Windows NTFS的执行位。打包时检查所有本地ELF均为64位小端AArch64，遇到Windows PE/x64 ELF或高于glibc2.28的需求则失败。launcher和ELF设为0755，资源设为0644；维护脚本设为0755。没有把外架构执行失败忽略后标记为通过。

该包沿用现有Linux桌面启动的 `--no-sandbox` 选项，未增加setuid权限。软件启动后仍使用原有preload边界和服务端鉴权。

## 验证范围

- Electron应用目录交叉构建通过，111份生产依赖清单检查通过；x64构建机没有直接执行ARM64 Electron图形界面。
- Python封装测试覆盖AArch64/PE识别、执行权限、Debian ar格式、启动用户检查与卸载保留数据；相关Node初始化/文件服务/依赖检查也已运行。
- 在WSL的QEMU ARM64用户态仿真中，使用经校验的Ubuntu20.04 ARM64基础库环境，对最终应用目录里的 `mysqld --version`、`mysql --version`、`java -version` 执行成功。输出分别确认MySQL8.0.46 AArch64和Java21。这些是组件加载检查，不是麒麟整套运行证据，也未把QEMU或Ubuntu基础库混入交付包。
- 最终 `.deb` 已通过WSL `dpkg-deb --info` 读取，确认为Debian2.0包、arm64架构，执行脚本权限为0755，系统依赖声明可解析。
- 已遍历最终安装归档的1,720个文件，156个ELF全部为AArch64且具备执行权限；文件归属root，主程序、Java、MySQL、launcher与桌面入口齐全；包内app.asar摘要与已校验构建目录一致。
- 本轮3个Python封装测试、11个相关Node测试通过；后端7个受影响单测及标准MySQL8 package构建通过。没有将本次结果写成后端全量Docker测试通过。
- 尚未在用户银河麒麟V10 ARM64机器执行实际安装、图形界面、音频、串口和完整数据库恢复验收；不能以Windows x64/Win7验证替代。

## 初版交付记录（已发现解包缺陷，请使用下方修正版）

- `bw-frontend/out/kylin-v10-arm64/nip-traffic-system-kylin-arm64_3.1.1_arm64.deb`
- 大小：1,770,328,086 字节（约1.77GB）；安装程序资源约3.16GB，另外需要数据库及用户文件空间。
- SHA-256：`35728c9b260db67e7b8386f1e9a877f8d7e7ee785363f3aab843eb90f7bfccdb`
- 检查报告：`bw-frontend/out/kylin-v10-arm64/kylin-arm64-report.json`。
- 定位为麒麟V10桌面ARM64的交叉构建测试包；是否适配客户实际补丁、CPU与桌面依赖，以目标机安装启动和业务测试为准。

## 客户安装器报“软件包操作异常”时

用户截图显示 Kylin 内核 `5.4.18-91-generic`、`aarch64`，图形安装器提示“软件包操作异常，无法安装或移除软件包”。`aarch64` 与 Debian 的 `arm64` 架构名对应，不应因拼写差异更换CPU架构。内核版本不能用来推断glibc版本或具体桌面发行版，尚不能仅凭截图认定缺少哪项依赖。

诊断工具：`bw-frontend/out/kylin-v10-arm64/麒麟安装诊断.zip`，源码为 `bw-frontend/support/kylin-install/kylin-install-check.sh`。将脚本放在安装包旁，在该目录执行：

```sh
bash ./kylin-install-check.sh ./nip-traffic-system-kylin-arm64_3.1.1_arm64.deb
```

提供生成的 `kylin-install-report.txt`。工具无需sudo，只收集系统/包架构、glibc与声明依赖版本、磁盘空间、dpkg审计、包摘要及 `apt-get --simulate --no-download install` 输出；不实际安装/删除/下载包，不读取数据库凭据。安装包约1.77GB，计算摘要需要等待片刻。

脚本已通过 `bash -n` 和本机WSL实际执行验证，正确报告摘要一致、包信息读取成功和模拟安装的具体依赖结果。WSL是x64系统，此处仅验证诊断工具，不能把模拟结果当作用户麒麟ARM64机器的故障原因。当前等待用户机器报告后定位，禁止靠 `--force-depends` 或手工替换系统glibc绕过检查。

### 用户诊断报告结果（2026-11-06）

已读取用户提供的 `bw-frontend/kylin-install-report.txt`：

- 银河麒麟桌面V10 SP1，发行标识2303；内核5.4.18-91-generic；系统与包管理架构分别为aarch64/arm64。
- glibc2.31满足2.28最低要求；所有声明依赖均已安装。
- `dpkg --audit` 未报告未完成的包操作；根分区可用65GB，空间足够。
- 安装包字节数与SHA-256均与交付产物一致；`dpkg-deb --info` 返回0。
- `apt-get --simulate --no-download install` 返回0，计划仅新增本软件包，不升级、不删除其他包。

以上排除了报告时刻的架构不匹配、已声明依赖未满足、文件传输损坏和磁盘不足；不等同于已完成解包、维护脚本及程序启动。图形安装器仍只提供通用异常，不能仅据模拟成功认定其具体内部故障原因。

下一步：关闭图形安装器，在 `.deb` 所在目录用Bash执行实际命令行安装并记录原始输出：

```sh
set -o pipefail
sudo apt-get --no-download install ./nip-traffic-system-kylin-arm64_3.1.1_arm64.deb 2>&1 | tee kylin-real-install.log
```

此命令为当时的诊断步骤，后续结果和修复见下节，不再使用初版包安装。

### 实际解包失败及修正（2026-11-06）

用户实际APT安装出现相对路径内部错误，随后 `dpkg -i` 明确报告无法创建 `/opt/nip-traffic-system-kylin-arm64/bin/config.json.dpkg-new`：父目录不存在。本地在WSL隔离根目录运行旧包的实际 `dpkg --unpack`，复现同一文件、同一错误。根因是Python封装器只向data.tar写入普通文件，遗漏全部父目录条目；元数据检查和APT模拟安装不会发现它，自动创建父目录的普通tar提取也不能替代dpkg验收。

封装器现先写全部父目录（root属主、0755权限，父目录先于子目录），再写入文件；Debian修订号提升为 `3.1.1-1`。新增回归测试按顺序创建目录和文件，禁止隐式创建父目录，4个封装测试通过。此修正版复用原ARM64应用目录，应用组件版本不变。

用户在修正版包所在目录安装：

```sh
sudo dpkg -i "$(pwd)/nip-traffic-system-kylin-arm64_3.1.1-1_arm64.deb"
```

前述用户环境已确认依赖齐全。可直接覆盖失败的初版安装，无需删除用户数据；成功后以普通桌面用户启动。目标机完整安装和启动结果仍待反馈。

修正版文件：`bw-frontend/out/kylin-v10-arm64/nip-traffic-system-kylin-arm64_3.1.1-1_arm64.deb`，1,770,344,218字节。

SHA-256：`53bc3ff8967570c5273f0b2c3896b141e6cbb62d7c2e61de9f23a168fc8fef95`。

修正版实际验证：在WSL的空白临时根目录运行 `dpkg --root=<临时目录> --force-architecture --unpack <修正版deb>` 返回0，原报错文件 `bin/config.json` 存在，启动器存在且具有执行权限。测试后删除隔离根目录。`--force-architecture` 仅用于x64构建机解包ARM64文件，用户ARM64机器安装不需要此参数。此检查没有执行ARM64程序或postinst，不能代表客户机完整安装/启动验收。

证据：`bw-frontend/out/kylin-v10-arm64/dpkg-unpack-original.json`（旧包同错误复现）与 `bw-frontend/out/kylin-v10-arm64/dpkg-unpack-fix1.json`（新包解包通过）。

### 实机安装成功与WebGL补丁（2026-11-06）

用户确认目录修正版“可以用了”，随后反馈 `Your browser does not support WebGL`。此反馈证明实机安装/启动已推进成功，不代表音频、串口、恢复等完整业务验收。

核对已交付ARM64归档后确认：旧入口仍包含 `disableHardwareAcceleration`、`disable-gpu` 和 `disable-software-rasterizer`。目录修正版复用了该应用归档，因此同时禁用了硬件和软件渲染。当前源码入口已移除禁用项；图形配置进一步调整为Linux ARM64默认ANGLE/SwiftShader，保留 `NIP_GRAPHICS_MODE=auto` 显式选择自动渲染。新版Windows/Linux x64默认行为不变。

为已安装的3.1.1-1提供定向补丁：

- `bw-frontend/out/kylin-v10-arm64/麒麟ARM64-WebGL修复补丁.zip`，281,807,637字节。
- SHA-256：`6adfea2aeb22e07cb04e886bcc1b361269f911c88471bf68eb5d49b85a1df4c4`。
- 构建脚本：`bw-frontend/scripts/build-kylin-webgl-patch.cjs`。
- 仅更改归档内 `main.js` 和 `electron/core/graphics.js`，其余文件逐字节比较通过；生产依赖闭包检查通过，随包软件渲染库存在。
- 安装器严格校验旧归档与补丁摘要，原子替换前备份 `resources/app.asar.before-webgl`，不改数据库。未知版本或损坏补丁会拒绝操作。

正常退出软件并等待数据库退出，完整解压补丁，在解压目录执行：

```sh
sudo bash ./repair-webgl.sh
```

出现 `WEBGL_PATCH_OK` 或 `WEBGL_PATCH_ALREADY_INSTALLED` 后，以普通用户运行 `nip-traffic-system-kylin-arm64`。若软件渲染仍失败，可尝试 `NIP_GRAPHICS_MODE=auto nip-traffic-system-kylin-arm64` 并提供终端输出。

验证：3个图形配置测试通过；补丁ZIP校验通过；WSL隔离目录实际安装、备份、重复执行、未知版本拒绝和损坏补丁拒绝均通过，证据为 `kylin-webgl-install-test.json`。尚未在麒麟ARM64实机确认WebGL实际渲染。当前3.1.1-1完整deb本身尚未集成此补丁，安装后需要执行补丁；后续完整构建须重新运行 `npm run build-e-kylin-arm64` 更新应用归档，再封装deb。
