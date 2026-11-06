# Windows 32 位整合安装包

本包面向 Windows 7 SP1 32 位的整套本机运行需求。实际采用 Electron 22.3.27 ia32、BellSoft Liberica Java 21.0.12.1+1 x86、MySQL 5.7.44 Win32；不是 32 位外壳连接 64 位服务。Windows x64 系统也可以运行该包，但独立 x64 构建仍保留原组件组合。

**2026-11-06 更新：用户确认 compat2 在此前报错的 Win7 64位机器上“能正常安装使用”。以后向相同环境交付，必须继续完成 [compat2 封装流程](win7-installer-system-dll.md)，不要分发默认 NSIS 安装器。该反馈针对Win7 64位系统运行ia32应用，尚不能替代Win7 32位操作系统的实测。**

## 运行与数据

- 内置数据库默认端口 6912，业务后端默认 18001，文件服务默认 8000。
- 首次启动自动建立数据库、应用迁移并创建 `admin`，当前源码默认密码固定为 `123456a`，在本机弹窗中显示。已有实例保留原密码；历史安装包需重新构建才能带入此修改。
- 数据保存在 Electron 用户数据目录的 `system-win32-mysql57/` 中。与原 x64 包的 `system/` 隔离；MySQL5.7 禁止直接打开 MySQL8 的物理数据文件。
- 使用配置时无需系统预装 Java/MySQL，不依赖客户机器的 PATH；随包 Java 与 MySQL 的 EXE/DLL 均校验为 x86 PE32。
- 已包含理论学习、理论题库、个人岗前资料、评分规则、固定报底、文章管理的仓库快照初始化数据，具体数量及源资料缺陷见 `docs/guides/initial-business-data.md`。不含原有用户、会话与成绩。
- 旧 MySQL8 的完整 SQL 备份不保证能直接导入 MySQL5.7（例如 0900 排序规则、表达式默认值等不兼容）；不能通过复制 datadir 迁移。业务资料补充 SQL 的显式列形式已独立处理。
- 保留现有软件授权流程；32位构建不会绕过授权。

## 实现要点

1. `build/windows-ia32.cjs` 固定 Electron22 和 ia32 目标，输出到独立 `out/win7-ia32/`。常规 x64 的 Electron30 配置不被覆盖。
2. Java 使用厂商真实发布的 x86 Java21。此前仅检查本机 Temurin x64 而判断需降级 Java 的信息不完整，本轮已通过真实 x86 JVM 启动纠正。业务 Java 源码仍以21编译。
3. Maven profile `windows-ia32` 在构建期指定 `quarkus.datasource.db-version=5.7.0`，输出 `target/quarkus-app-win32/`；不能仅设置运行期环境变量，因为 Hibernate 最低数据库版本在构建期固化。
4. 仅对32位初始化资产副本转换 `utf8mb4_0900_ai_ci` 为 `utf8mb4_general_ci`。`capture_pages` 的 LONGTEXT 表达式默认值改为无默认值的 NOT NULL 列，Java实体仍初始化为 `{}`。仓库 MySQL8 快照和迁移原文件保持原定义。
5. MySQL5.7 启动不使用 MySQL8 的 `mysqlx=0` / `--no-monitor`；Windows 以 `--log-syslog=0` 避免依赖系统服务/EventLog注册权限。
6. 备份接口通过 `nip.database.client-major=5` 不传 MySQL8 专属 `--column-statistics=0`。接口路径、鉴权头、响应信封及前端 `DatabaseApi.js` 调用形态保持一致。
7. 32位后端配置 `-Xms64m -Xmx512m -XX:MaxMetaspaceSize=192m`，避免 JVM 按高内存宿主机设定过大的32位地址空间；MySQL 缓冲池为128MiB。
8. 串口枚举使用 Windows PowerShell 的 `Get-WmiObject Win32_PnPEntity`，避免 Win7 默认环境没有 `Get-PnpDevice`。实际串口驱动与硬件仍须目标机器验收。
9. 文件服务子进程接收网络设置中的端口，主进程与子进程保持一致，独立测试无需占用正在运行的开发服务。

## 可复现构建

下载地址和 SHA-256 固定在 `bw-frontend/scripts/windows-ia32-components.json`。MySQL ZIP 另外核对官方 MD5，Java ZIP 核对厂商 API 的 SHA1；Electron 使用官方发布的 SHA256。版本号不会随下载自动漂移。

在 `backend/` 使用 JDK21：

```text
./mvnw.cmd -B -Pwindows-ia32 -Dtest=DatabaseTransferServiceTest package
```

这条命令只运行受影响测试，发布前全量验证要求仍需另行满足，不等于完整后端测试结果。

在 `bw-frontend/`：

```text
npm run prepare:win7-32
npm run build-e-win7-32 -- --dir
$env:ELECTRON_BUILDER_NSIS_DIR = 'D:\build-tools\nsis-3.0.4.1'
node scripts/build-win7-compatible-installer.cjs
```

`prepare:win7-32` 下载并校验32位组件，生成工具目录、整合运行组件目录及 `.cache/electron22-ia32`。可通过 `NIP_MYSQL_CACHE_DIR` 指向已有的原始归档缓存。组件目录已存在时只验证而不覆盖；更新后端/初始化资源时需先归档旧 `bin/integrated/windows-ia32` 再重建。

上述打包命令按 Windows PowerShell 执行，NSIS 路径换成本机经校验的目录。`--dir` 仅组装应用，最后一步才产生应交付的 compat2 安装包与备用 ZIP；完整构建、更新注意事项见 `docs/guides/win7-installer-system-dll.md`。

Windows x86 的客户端工具随包放在 `bin/file/tools/mysql/windows-ia32`。整合服务端/JRE/fast-jar 在 `bin/integrated/windows-ia32`。安装包只携带 ia32 目标目录。

## 已验证与限制

- x86 Electron 显示 `process.arch=ia32`，Java 显示 `OpenJDK 32-Bit Client VM`，MySQL 显示 `5.7.44 for Win32`。
- 当前 Windows 宿主机上，使用真实32位进程在独立端口和数据目录执行：首次初始化、24条迁移、所有业务资料表数量核对、管理员登录、SQL备份→删除测试数据→导入恢复、关闭并再次启动的数据保留，均通过。
- MySQL5.7专用 Maven profile 构建通过，7个 `DatabaseTransferServiceTest` 测试通过。开发进程占用日志导致轮转告警，不影响该次测试结果。全量 Docker 测试未在本轮完成。
- 最终21个相关 Node 测试通过，覆盖32位 PE和版本选择、MySQL5.7初始化转换、资料种子、6912端口迁移、文件服务端口、依赖闭包与打包目录等。
- 打包时按目标架构校验 PE，Windows x64 构建机也实际执行 ia32 Electron 的包内依赖加载与 NeDB 磁盘读写探测，不仅做目录检查。
- 构建机不是Windows7；上述自动验证是Windows上的32位进程验证。2026-11-06 用户补充确认 compat2 在其Win7 64位机器上安装使用正常；Win7 32位系统以及驱动、声音、训练设备逐项验收仍未提供。
- Electron22/MySQL5.7 是旧平台兼容组合，不能将其标记为当前受支持的新平台版本。

## 本轮安装产物

用户在 Win7 上报告原 NSIS 安装器 `System.dll` 崩溃，改用独立 `compat2` 安装器后确认安装使用正常。当前交付安装包和ZIP见 `docs/guides/win7-installer-system-dll.md`。以下原安装器信息仅保留为历史产物证据，不再作为相同环境的分发入口。

- 文件：`bw-frontend/out/win7-ia32/多功能报务综合训练系统-win7-3.1.1-ia32.exe`
- 大小：642,347,659 字节（约 642 MB）。
- SHA-256：`d436580b61cdf13b5ced57a474365fe902c2356ec5445c6890391ec21ab7ef6e`
- 安装器构建退出码0；采用可选安装目录的 NSIS 安装向导，默认保留用户数据。
- 核对最终安装器内提取出的 `app.asar` 与解包目录一致，关键启动/数据库/串口源码与本轮源码一致。
- 遍历解包目录158个 EXE/DLL，全部通过 x86 PE32 检查；仅包含 `windows-ia32` 工具及运行组件目录。
- 111份生产依赖清单检查通过，使用包内32位 Electron 实际加载依赖并执行 NeDB 磁盘写入、更新、重新读取通过。
- 在隔离的 Electron22 ia32 主进程中加载包内实际启动类（使用测试端口16912/18096/18097及独立用户数据目录），完成数据库/后端/文件服务启动并显示包内登录页面。renderer 验证 preload bridge、Vue app 根节点及 `navigator.serial` 存在，未记录页面加载错误；退出后测试端口全部释放。
- `bw-frontend/out/win7-ia32/win7-ia32-report.json` 记录安装器摘要、组件版本、文件架构校验和尚待实机验收的边界。

安装前退出旧版本软件及其错误弹窗，避免后端/文件服务端口占用。32位版创建新的独立数据库，首次管理员密码与原x64版不同。若需要保留旧版业务数据，应保留旧数据目录并按兼容迁移流程处理，不要复制 MySQL8 物理数据目录到32位版本。
